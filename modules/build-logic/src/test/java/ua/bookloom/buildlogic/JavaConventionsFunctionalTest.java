package ua.bookloom.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.buildlogic.BuildFixture.qualityConfig;
import static ua.bookloom.buildlogic.BuildFixture.runner;
import static ua.bookloom.buildlogic.BuildFixture.settings;
import static ua.bookloom.buildlogic.BuildFixture.write;

import java.io.IOException;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Functional tests proving the {@code bookloom.java-conventions} precompiled script plugin, applied to a
 * throwaway fixture project via Gradle TestKit, produces a genuinely modular (JPMS) compile under JDK 25 with
 * {@code -Werror} — not merely a build that happens to stay green.
 *
 * <p>Each fixture is generated fresh inside a JUnit {@code @TempDir} and built with its own, self-contained
 * {@code settings.gradle.kts}. None of these tests ever points {@link GradleRunner} at the real repository
 * root: {@code build-logic} is an included build of the root, so doing so would recurse.
 */
class JavaConventionsFunctionalTest {

    // Covers task 1.8: `./gradlew build` must compile a real, multi-module JPMS graph — this is the floor
    // everything else in the toolchain stands on.
    @Test
    void build_multiModuleJpmsFixture_compilesGreen(@TempDir Path projectDir) throws IOException {
        settings(projectDir, "\":alpha\", \":beta\"");
        qualityConfig(projectDir);
        write(projectDir, "alpha/build.gradle.kts", """
            plugins {
                id("bookloom.java-conventions")
            }
            """);
        write(projectDir, "alpha/src/main/java/module-info.java", """
            module fixture.alpha {
                exports fixture.alpha;
            }
            """);
        write(projectDir, "alpha/src/main/java/fixture/alpha/Alpha.java", """
            package fixture.alpha;

            public final class Alpha {
                public Alpha() {
                }

                public String greet() {
                    return "alpha";
                }
            }
            """);
        write(projectDir, "beta/build.gradle.kts", """
            plugins {
                id("bookloom.java-conventions")
            }

            dependencies {
                implementation(project(":alpha"))
            }
            """);
        write(projectDir, "beta/src/main/java/module-info.java", """
            module fixture.beta {
                requires fixture.alpha;
            }
            """);
        write(projectDir, "beta/src/main/java/fixture/beta/Beta.java", """
            package fixture.beta;

            import fixture.alpha.Alpha;

            public final class Beta {
                public String run() {
                    return new Alpha().greet();
                }
            }
            """);

        BuildResult result = runner(projectDir, "build").build();

        BuildTask compileBeta = result.task(":beta:compileJava");
        assertThat(compileBeta).isNotNull();
        assertThat(compileBeta.getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(projectDir.resolve("beta/build/classes/java/main/module-info.class")).exists();
    }

    // Covers task 1.8: a `requires` naming a module with no declared Gradle dependency must fail compilation —
    // proving the JPMS enforcement is real, not merely asserted.
    @Test
    void build_forbiddenModuleEdge_failsCompilation(@TempDir Path projectDir) throws IOException {
        settings(projectDir, "\":beta\"");
        qualityConfig(projectDir);
        write(projectDir, "beta/build.gradle.kts", """
            plugins {
                id("bookloom.java-conventions")
            }
            """);
        write(projectDir, "beta/src/main/java/module-info.java", """
            module fixture.beta {
                requires fixture.gamma;
            }
            """);

        BuildResult result = runner(projectDir, "build").buildAndFail();

        assertThat(result.getOutput()).contains("module not found");
    }

    // Covers task 1.8: a lint finding under `-Xlint:all` must fail the build via `-Werror`, not merely print
    // a warning nobody reads. Verified by experiment (see the change's coder report) that a deprecated-API
    // call reliably triggers `-Xlint:deprecation` on JDK 25 with this plugin's compiler-arg set.
    @Test
    void build_deprecatedApiUsage_failsUnderWerror(@TempDir Path projectDir) throws IOException {
        settings(projectDir, "\":warn\"");
        qualityConfig(projectDir);
        write(projectDir, "warn/build.gradle.kts", """
            plugins {
                id("bookloom.java-conventions")
            }
            """);
        write(projectDir, "warn/src/main/java/fixture/warn/Legacy.java", """
            package fixture.warn;

            public final class Legacy {
                @Deprecated
                public void old() {
                }
            }
            """);
        write(projectDir, "warn/src/main/java/fixture/warn/Caller.java", """
            package fixture.warn;

            public final class Caller {
                public void run() {
                    new Legacy().old();
                }
            }
            """);

        BuildResult result = runner(projectDir, "build").buildAndFail();

        assertThat(result.getOutput()).contains("warnings found and -Werror specified");
    }

}
