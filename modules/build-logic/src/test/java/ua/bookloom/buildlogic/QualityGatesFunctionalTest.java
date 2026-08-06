package ua.bookloom.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.buildlogic.BuildFixture.qualityConfig;
import static ua.bookloom.buildlogic.BuildFixture.read;
import static ua.bookloom.buildlogic.BuildFixture.runner;
import static ua.bookloom.buildlogic.BuildFixture.settings;
import static ua.bookloom.buildlogic.BuildFixture.write;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves each configured quality tool actually fails <em>red</em> on a seeded canary violation.
 *
 * <p>This is the point of the suite, and it is not a formality. A tool that has only ever been observed green is
 * indistinguishable, from the outside, from a tool that is switched off — a typo'd config path, a ruleset that
 * silently failed to load, or a confidence threshold set so high nothing can ever reach it all produce exactly
 * the same build output as a healthy gate. Each test below seeds the specific defect its tool exists to catch
 * and asserts the build stops.
 *
 * <p>Covers task 2.10 of the {@code bootstrap-gradle-and-quality-toolchain} change.
 */
class QualityGatesFunctionalTest {

    // ===== Spotless =====================================================================================

    // Covers task 2.10: an unformatted source file must fail `spotlessCheck`, and `spotlessApply` must fix it.
    @Test
    void spotlessCheck_unformattedSource_failsAndSpotlessApplyRewritesTheFile(@TempDir Path projectDir)
            throws IOException {
        fixtureProject(projectDir);
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Ugly.java",
                """
                package fixture.alpha;
                public   final class Ugly {
                public Ugly(){}
                        public String value( ) { return    "v"; }
                }
                """);

        BuildResult failed = runner(projectDir, "spotlessCheck").buildAndFail();

        assertThat(failed.task(":alpha:spotlessJavaCheck").getOutcome()).isEqualTo(TaskOutcome.FAILED);
        assertThat(failed.getOutput()).contains("format violations");

        runner(projectDir, "spotlessApply").build();

        // The file is rewritten in place, and the rewritten file now passes the same check that just rejected it.
        String formatted = read(projectDir, "alpha/src/main/java/fixture/alpha/Ugly.java");
        assertThat(formatted).contains("public final class Ugly {").doesNotContain("public   final");
        assertThat(runner(projectDir, "spotlessCheck")
                        .build()
                        .task(":alpha:spotlessJavaCheck")
                        .getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    // ===== NullAway =====================================================================================

    // Covers task 2.10: a NullAway-detectable null defect inside a `@NullMarked` package must fail `check`.
    @Test
    void check_nullDereferenceInNullMarkedPackage_failsWithNullAway(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);
        nullMarkedPackageInfo(projectDir);
        write(
                projectDir,
                "alpha/src/main/java/module-info.java",
                """
                module fixture.alpha {
                    requires static org.jspecify;

                    exports fixture.alpha;
                }
                """);
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Deref.java",
                """
                package fixture.alpha;

                import org.jspecify.annotations.Nullable;

                public final class Deref {
                    public Deref() {}

                    public int length(@Nullable String value) {
                        return value.length();
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").buildAndFail();

        assertThat(result.getOutput()).contains("[NullAway] dereferenced expression 'value' is @Nullable");
    }

    // Covers task 2.10: NullAway must NOT fire on an unmarked package — the `@NullMarked` marker is what turns
    // the analysis on, so a package without one is silently unanalysed and this proves the opt-in is real.
    //
    // Scoped to `compileJava` rather than `check` deliberately: SpotBugs independently flags this same
    // dereference, so running the full graph would prove nothing about which tool spoke.
    @Test
    void compileJava_sameNullDefectWithoutNullMarked_passes(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Deref.java",
                """
                package fixture.alpha;

                import org.jspecify.annotations.Nullable;

                public final class Deref {
                    public Deref() {}

                    public int length(@Nullable String value) {
                        return value.length();
                    }
                }
                """);

        BuildResult result = runner(projectDir, "compileJava").build();

        assertThat(result.task(":alpha:compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).doesNotContain("[NullAway]");
    }

    // ===== Lombok x NullAway ============================================================================

    // Covers task 2.10: a Lombok `@RequiredArgsConstructor` service in a `@NullMarked` package must compile
    // CLEAN. Lombok is the sole annotation processor and desugars ahead of the Error Prone javac plugin, so
    // NullAway analyses the generated constructor rather than the annotation (design D3, DD-05, ADR-0014).
    @Test
    void check_lombokServiceInNullMarkedModule_compilesClean(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);
        nullMarkedPackageInfo(projectDir);
        write(
                projectDir,
                "alpha/src/main/java/module-info.java",
                """
                module fixture.alpha {
                    requires static org.jspecify;
                    requires static lombok;

                    exports fixture.alpha;
                }
                """);
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Service.java",
                """
                package fixture.alpha;

                import lombok.RequiredArgsConstructor;

                @RequiredArgsConstructor
                public final class Service {
                    private final String name;

                    public String name() {
                        return name;
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").build();

        assertThat(result.task(":alpha:compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).doesNotContain("[NullAway]");
        // The generated constructor is real, not merely un-flagged.
        assertThat(projectDir.resolve("alpha/build/classes/java/main/fixture/alpha/Service.class"))
                .exists();
    }

    // ===== Checkstyle ===================================================================================

    // Covers task 2.10: a seeded Checkstyle naming violation must fail `check`, using the committed ruleset.
    @Test
    void check_misnamedField_failsCheckstyle(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Naming.java",
                """
                package fixture.alpha;

                public final class Naming {
                    private int Badly_Named_Field;

                    public Naming() {}

                    public int value() {
                        return Badly_Named_Field;
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").buildAndFail();

        assertThat(result.task(":alpha:checkstyleMain").getOutcome()).isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput()).contains("[MemberName]");
    }

    // ===== SpotBugs + FindSecBugs =======================================================================

    // Covers task 2.10: a hard-coded credential must fail `check` via FindSecBugs — the automated backstop for
    // the credentials-as-reference invariant, which persists an env-var or keychain REFERENCE, never a secret
    // (DD-11).
    @Test
    void check_hardCodedCredential_failsFindSecBugs(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);
        write(
                projectDir,
                "alpha/src/main/java/module-info.java",
                """
                module fixture.alpha {
                    requires java.sql;

                    exports fixture.alpha;
                }
                """);
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Creds.java",
                """
                package fixture.alpha;

                import java.sql.Connection;
                import java.sql.DriverManager;
                import java.sql.SQLException;

                public final class Creds {
                    public Creds() {}

                    public Connection connect(String url) throws SQLException {
                        return DriverManager.getConnection(url, "admin", "hunter2");
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").buildAndFail();

        assertThat(result.task(":alpha:spotbugsMain").getOutcome()).isEqualTo(TaskOutcome.FAILED);
        assertThat(read(projectDir, "alpha/build/reports/spotbugs/main.xml")).contains("HARD_CODE_PASSWORD");
    }

    // ===== Fixture scaffolding ==========================================================================

    /** A single-module fixture applying the full conventions stack against the real shared configuration. */
    private static void fixtureProject(Path projectDir) throws IOException {
        settings(projectDir, "\":alpha\"");
        qualityConfig(projectDir);
        write(
                projectDir,
                "alpha/build.gradle.kts",
                """
                plugins {
                    id("bookloom.java-conventions")
                    id("bookloom.spotless-conventions")
                }
                """);
    }

    private static void nullMarkedPackageInfo(Path projectDir) throws IOException {
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/package-info.java",
                """
                @NullMarked
                package fixture.alpha;

                import org.jspecify.annotations.NullMarked;
                """);
    }
}
