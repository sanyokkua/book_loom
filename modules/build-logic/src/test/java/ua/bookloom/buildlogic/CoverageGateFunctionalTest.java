package ua.bookloom.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.buildlogic.BuildFixture.qualityConfig;
import static ua.bookloom.buildlogic.BuildFixture.runner;
import static ua.bookloom.buildlogic.BuildFixture.settings;
import static ua.bookloom.buildlogic.BuildFixture.write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the JaCoCo branch gate of {@code bookloom.coverage-conventions} both bites and exempts (design D6).
 *
 * <p>The two halves are equally load-bearing and pull in opposite directions. A gate that never fails is
 * indistinguishable from no gate — and this one is unusually easy to render inert, because JaCoCo evaluates a
 * ratio over zero branches as undefined and passes it, so a mis-scoped rule looks exactly like a satisfied one. A
 * gate that fails on a module with no code yet would, conversely, make the whole-project clean gate unsatisfiable
 * at exactly the moment this project is in — eight modules, one placeholder type each
 * ({@code docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#coverage-gate}).
 *
 * <p>The exemption is asserted as {@code SKIPPED}, not as "the build passed". That distinction is the whole point:
 * a vacuous pass and a deliberate exemption produce the same green build, and only the task outcome tells them
 * apart. The empty fixture below has a test that really runs — so JaCoCo execution data really exists — and is
 * exempted anyway, because the exemption is keyed on production sources rather than on what JaCoCo happened to
 * find.
 *
 * <p>Covers tasks 5.4 and 5.5 of the {@code bootstrap-gradle-and-quality-toolchain} change.
 */
class CoverageGateFunctionalTest {

    // Covers task 5.4/5.5: a module with real production code whose tests leave branch coverage below 80% fails
    // `check`. Without this, every other coverage assertion in the project is unfalsifiable.
    @Test
    void check_productionCodeBelowBranchThreshold_failsTheCoverageGate(@TempDir Path projectDir) throws IOException {
        fixtureModule(projectDir, "alpha");
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Classifier.java",
                """
                package fixture.alpha;

                public final class Classifier {

                    public Classifier() {}

                    public String classify(int value) {
                        if (value < 0) {
                            return "negative";
                        }
                        if (value == 0) {
                            return "zero";
                        }
                        if (value < 10) {
                            return "small";
                        }
                        return "large";
                    }
                }
                """);
        // Six branches; this exercises one of them. 1/6 = 17%, comfortably under the threshold.
        write(
                projectDir,
                "alpha/src/test/java/fixture/alpha/ClassifierTest.java",
                """
                package fixture.alpha;

                import static org.assertj.core.api.Assertions.assertThat;

                import org.junit.jupiter.api.Test;

                class ClassifierTest {

                    @Test
                    void classify_negativeValue_returnsNegative() {
                        assertThat(new Classifier().classify(-1)).isEqualTo("negative");
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").buildAndFail();

        assertThat(result.task(":alpha:jacocoTestCoverageVerification").getOutcome())
                .isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput())
                .contains("Rule violated for bundle alpha")
                .contains("branches covered ratio is")
                .contains("expected minimum is 0.80");
    }

    // Covers task 5.4/5.5: the same gate PASSES when the branches are covered — so the failure above is the
    // threshold speaking, not the rule being broken.
    @Test
    void check_productionCodeAboveBranchThreshold_passesTheCoverageGate(@TempDir Path projectDir) throws IOException {
        fixtureModule(projectDir, "alpha");
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Classifier.java",
                """
                package fixture.alpha;

                public final class Classifier {

                    public Classifier() {}

                    public String classify(int value) {
                        if (value < 0) {
                            return "negative";
                        }
                        return "non-negative";
                    }
                }
                """);
        write(
                projectDir,
                "alpha/src/test/java/fixture/alpha/ClassifierTest.java",
                """
                package fixture.alpha;

                import static org.assertj.core.api.Assertions.assertThat;

                import org.junit.jupiter.api.Test;

                class ClassifierTest {

                    @Test
                    void classify_negativeValue_returnsNegative() {
                        assertThat(new Classifier().classify(-1)).isEqualTo("negative");
                    }

                    @Test
                    void classify_zero_returnsNonNegative() {
                        assertThat(new Classifier().classify(0)).isEqualTo("non-negative");
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").build();

        // SUCCESS, not SKIPPED: the module has production sources, so the gate was live and satisfied.
        assertThat(result.task(":alpha:jacocoTestCoverageVerification").getOutcome())
                .isEqualTo(TaskOutcome.SUCCESS);
    }

    // Covers task 5.4: a module with branchy production code and NO test source set at all fails the gate. This
    // is the case Gradle's own JaCoCo wiring lets through — it skips the verification whenever no execution data
    // file exists, which is precisely the state of a module nobody has tested — so `bookloom.coverage-conventions`
    // replaces that predicate. Untested code is what a coverage gate is for; it must fail, not disappear.
    @Test
    void check_productionCodeWithNoTestsAtAll_failsTheCoverageGate(@TempDir Path projectDir) throws IOException {
        fixtureModule(projectDir, "alpha");
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Untested.java",
                """
                package fixture.alpha;

                public final class Untested {

                    public Untested() {}

                    public String classify(int value) {
                        if (value < 0) {
                            return "negative";
                        }
                        return "non-negative";
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").buildAndFail();

        assertThat(result.task(":alpha:test").getOutcome()).isEqualTo(TaskOutcome.NO_SOURCE);
        assertThat(result.task(":alpha:jacocoTestCoverageVerification").getOutcome())
                .isEqualTo(TaskOutcome.FAILED);
        assertThat(result.getOutput()).contains("Rule violated for bundle alpha");
    }

    // Covers task 5.4/5.5: a module with no production sources is EXEMPT by explicit configuration. The module
    // here runs a real test — so execution data exists and JaCoCo has something to read — and the verification
    // task is skipped anyway, which is what distinguishes the configured exemption from the vacuous pass JaCoCo
    // would give a zero-branch bundle.
    @Test
    void check_moduleWithNoProductionSources_skipsTheCoverageGateEntirely(@TempDir Path projectDir)
            throws IOException {
        fixtureModule(projectDir, "alpha");
        write(
                projectDir,
                "alpha/src/test/java/fixture/alpha/PlaceholderTest.java",
                """
                package fixture.alpha;

                import static org.assertj.core.api.Assertions.assertThat;

                import org.junit.jupiter.api.Test;

                class PlaceholderTest {

                    @Test
                    void module_withNoProductionCode_stillRunsItsOwnTests() {
                        assertThat("scaffolding").isNotBlank();
                    }
                }
                """);

        BuildResult result = runner(projectDir, "check").build();

        assertThat(result.task(":alpha:test").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.exists(projectDir.resolve("alpha/build/jacoco/test.exec")))
                .as("the exemption must hold even though JaCoCo execution data exists")
                .isTrue();
        assertThat(result.task(":alpha:jacocoTestCoverageVerification").getOutcome())
                .isEqualTo(TaskOutcome.SKIPPED);
    }

    // Covers task 5.4: `module-info.java` and `package-info.java` are declarations, not production code — a
    // module holding only those is still exempt. This is the shape every one of the eight real modules would have
    // had before its placeholder type existed, and the shape any new module starts in.
    //
    // The module declaration deliberately exports nothing: javac rejects `exports fixture.alpha` when the package
    // holds only a `package-info.java`, with "package is empty or does not exist". Which is the same judgement
    // this gate makes, arrived at independently by the compiler.
    @Test
    void check_moduleWithOnlyModuleAndPackageDeclarations_isStillExempt(@TempDir Path projectDir) throws IOException {
        fixtureModule(projectDir, "alpha");
        write(
                projectDir,
                "alpha/src/main/java/module-info.java",
                """
                module fixture.alpha {
                    requires static org.jspecify;
                }
                """);
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/package-info.java",
                """
                @NullMarked
                package fixture.alpha;

                import org.jspecify.annotations.NullMarked;
                """);

        BuildResult result = runner(projectDir, "check").build();

        assertThat(result.task(":alpha:jacocoTestCoverageVerification").getOutcome())
                .isEqualTo(TaskOutcome.SKIPPED);
    }

    // ===== Fixture scaffolding ==========================================================================

    /** A one-module fixture with the coverage gate applied against the real convention plugin. */
    private static void fixtureModule(Path projectDir, String module) throws IOException {
        settings(projectDir, "\":%s\"".formatted(module));
        qualityConfig(projectDir);
        write(
                projectDir,
                module + "/build.gradle.kts",
                """
                plugins {
                    id("bookloom.java-conventions")
                    id("bookloom.test-conventions")
                    id("bookloom.coverage-conventions")
                }
                """);
    }
}
