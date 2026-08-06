package ua.bookloom.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.buildlogic.BuildFixture.moduleDir;
import static ua.bookloom.buildlogic.BuildFixture.qualityConfig;
import static ua.bookloom.buildlogic.BuildFixture.read;
import static ua.bookloom.buildlogic.BuildFixture.runner;
import static ua.bookloom.buildlogic.BuildFixture.settings;
import static ua.bookloom.buildlogic.BuildFixture.write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves Gradle dependency locking is actually enforced, and that the JavaFX carve-out is exactly as wide as it
 * claims to be.
 *
 * <p>A lockfile is the easiest thing in a build to have and not use. Locking that is configured but inert looks
 * identical from the outside to locking that works: the lockfiles are committed, the build is green, and an
 * upstream re-publish still walks straight through. The first three tests seed the drift and assert the build
 * stops; the last two assert the committed lock state has the shape design D7 says it has.
 *
 * <p>Covers task 3.5 of the {@code bootstrap-gradle-and-quality-toolchain} change (tasks 3.1 and 3.2 are the
 * subject).
 */
class DependencyLockingFunctionalTest {

    /** The version the fixture locks to. Its neighbours on either side both exist, which the tests below need. */
    private static final String LOCKED = "3.27.6";

    private static final String HIGHER = "3.27.7";
    private static final String LOWER = "3.27.5";

    // ===== The lock is written =========================================================================

    // Covers task 3.5: `resolveAndLockAll --write-locks` must produce a lockfile pinning the declared version.
    @Test
    void resolveAndLockAll_withWriteLocks_writesLockfilePinningTheDeclaredVersion(@TempDir Path projectDir)
            throws IOException {
        fixtureProject(projectDir, LOCKED);

        runner(projectDir, "resolveAndLockAll", "--write-locks").build();

        assertThat(read(projectDir, "alpha/gradle.lockfile"))
                .contains("org.assertj:assertj-core:" + LOCKED)
                // The quality path is locked too, not just the compile classpath — that is the whole point of
                // resolving every resolvable configuration rather than only what a `build` happens to touch.
                .contains("annotationProcessor");
    }

    // Covers task 3.5: the task must refuse to run without `--write-locks`, rather than resolving everything,
    // writing nothing, and reporting success over lockfiles exactly as stale as they were.
    @Test
    void resolveAndLockAll_withoutWriteLocks_failsRatherThanSilentlyDoingNothing(@TempDir Path projectDir)
            throws IOException {
        fixtureProject(projectDir, LOCKED);

        BuildResult result = runner(projectDir, "resolveAndLockAll").buildAndFail();

        assertThat(result.getOutput()).contains("resolveAndLockAll must be run with --write-locks");
    }

    // ===== The lock is enforced ========================================================================

    // Covers task 3.5: resolving a version ABOVE the lock state must fail the build, naming dependency locking.
    // This is the case that matters — an upstream bump, a transitive creeping upward, or a catalog edit landed
    // without regenerating the locks.
    @Test
    void compileJava_versionAboveLockState_failsNamingDependencyLocking(@TempDir Path projectDir)
            throws IOException {
        fixtureProject(projectDir, LOCKED);
        runner(projectDir, "resolveAndLockAll", "--write-locks").build();

        // Exactly the mistake this gate exists to catch: the version moves, the lockfile does not.
        declareAssertJ(projectDir, HIGHER);

        BuildResult result = runner(projectDir, "compileJava").buildAndFail();

        // Assert on the reason Gradle attaches to the constraint, not on the surrounding prose: the wording of
        // the summary line varies with how many entries the configuration has, but this phrase is what actually
        // identifies locking — rather than some unrelated version conflict — as the thing that stopped the build.
        assertThat(result.getOutput())
                .contains("org.assertj:assertj-core:{strictly " + LOCKED + "}")
                .contains("Dependency version enforced by Dependency Locking");
    }

    // Covers task 3.5: a version BELOW the lock state resolves UP to the locked version and the build passes.
    //
    // This is not a gap, it is what Gradle's lock is: a `strictly` constraint. Documenting it with a test matters
    // because it decides how every other lock test must be written — a canary that downgrades proves nothing,
    // since it goes green while the lock is doing exactly its job.
    @Test
    void compileJava_versionBelowLockState_resolvesUpToTheLockedVersion(@TempDir Path projectDir)
            throws IOException {
        fixtureProject(projectDir, LOCKED);
        runner(projectDir, "resolveAndLockAll", "--write-locks").build();

        declareAssertJ(projectDir, LOWER);

        // `:alpha:` qualified — an unqualified `dependencies` runs against the fixture's root project, which has
        // no `compileClasspath` at all.
        BuildResult result = runner(projectDir, ":alpha:dependencies", "--configuration", "compileClasspath")
                .build();

        assertThat(result.getOutput()).contains(LOWER + " -> " + LOCKED);
    }

    // ===== The committed lock state has the shape D7 describes =========================================

    // Covers task 3.5: every module this change created must carry committed lock state, so "locking is on" is a
    // fact about the repository rather than a fact about whoever last ran the build.
    @Test
    void committedLockState_coversEveryModuleAndBuildLogic() {
        List<String> locked = List.of(
                "api", "util", "document", "llm", "pipeline", "persistence", "ui", "app", "build-logic");

        for (String module : locked) {
            assertThat(moduleDir(module).resolve("gradle.lockfile"))
                    .as("committed lock state for :%s", module)
                    .isRegularFile();
        }
    }

    // Covers task 3.5 / design D7: no JavaFX module may appear in any committed lockfile, and `:ui`/`:app` must
    // lock their quality path while leaving the four JavaFX-contaminated classpaths unlocked.
    //
    // The reason is not that JavaFX resolution is unstable — it is that Gradle's lock state carries no classifier,
    // so a `org.openjfx:javafx-graphics:26.0.2` entry would pin nothing about WHICH of the four per-OS artifacts
    // is used, while reading as though it did. `01_BUILD_AND_TOOLING.md#dependency-locking` forbids asserting
    // that claim. An entry appearing here means someone locked a promise the build cannot keep.
    @Test
    void committedLockState_excludesJavaFxClasspathsButKeepsTheQualityPathLocked() throws IOException {
        for (String module : List.of("ui", "app")) {
            String lockfile = Files.readString(moduleDir(module).resolve("gradle.lockfile"));

            assertThat(lockfile)
                    .as("JavaFX must not be locked in :%s — the lock cannot express the per-OS classifier", module)
                    .doesNotContain("org.openjfx:")
                    .as("the four JavaFX-contaminated configurations must be excluded from locking in :%s", module)
                    .doesNotContain("compileClasspath")
                    .doesNotContain("runtimeClasspath")
                    .as("everything JavaFX does NOT reach must still be locked in :%s", module)
                    .contains("annotationProcessor")
                    .contains("checkstyle")
                    .contains("spotbugs");
        }

        // The carve-out is scoped to `:ui`/`:app`. A core module has no JavaFX and must lock its full classpath —
        // which is also what keeps Guice and its subtree version-pinned despite the carve-out above.
        String core = Files.readString(moduleDir("document").resolve("gradle.lockfile"));
        assertThat(core).contains("runtimeClasspath").contains("com.google.inject:guice:");
    }

    // ===== Fixture scaffolding =========================================================================

    /**
     * A single-module fixture applying the real {@code bookloom.java-conventions}, so these tests exercise the
     * shipped locking configuration rather than a re-declaration of it.
     */
    private static void fixtureProject(Path projectDir, String assertJVersion) throws IOException {
        settings(projectDir, "\":alpha\"");
        qualityConfig(projectDir);
        declareAssertJ(projectDir, assertJVersion);

        // `compileJava` is NO-SOURCE without this, and a task that does not run resolves no configuration and
        // therefore never consults the lock state.
        write(
                projectDir,
                "alpha/src/main/java/fixture/alpha/Anchor.java",
                """
                package fixture.alpha;

                public final class Anchor {
                    public Anchor() {}
                }
                """);
    }

    private static void declareAssertJ(Path projectDir, String version) throws IOException {
        write(
                projectDir,
                "alpha/build.gradle.kts",
                """
                plugins {
                    id("bookloom.java-conventions")
                }

                dependencies {
                    implementation("org.assertj:assertj-core:%s")
                }
                """
                        .formatted(version));
    }
}
