package ua.bookloom.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.buildlogic.BuildFixture.qualityConfig;
import static ua.bookloom.buildlogic.BuildFixture.runner;
import static ua.bookloom.buildlogic.BuildFixture.settings;
import static ua.bookloom.buildlogic.BuildFixture.write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the local-only tag partition of {@code bookloom.test-conventions} is real (design D5).
 *
 * <p>What makes this worth a functional test rather than a code review: a broken tag partition is <em>invisible in
 * a green build</em>. If {@code excludeTags} silently stopped applying, {@code check} would start running live
 * tests and nobody would notice until a CI runner with no Ollama timed out. If {@code includeTags} were shadowed
 * by the shared exclusions — which is exactly what happens when both actions land on the same
 * {@code JUnitPlatformOptions} — {@code ./gradlew liveLocal} would report success while running nothing at all.
 * Both failure modes look identical to health from the outside, so each is asserted here by the SET of test
 * classes that actually executed.
 *
 * <p>Covers tasks 5.2, 5.3 and 5.5 of the {@code bootstrap-gradle-and-quality-toolchain} change.
 */
class TestConventionsFunctionalTest {

    private static final String PLAIN = "fixture.alpha.PlainTest";
    private static final String LIVE_LOCAL = "fixture.alpha.LiveLocalTest";
    private static final String PROMPT_EVAL = "fixture.alpha.PromptEvalTest";
    private static final String VISUAL = "fixture.alpha.VisualTest";

    // Covers task 5.2: `check` runs the untagged tests and NONE of the tagged ones, and the three tagged tasks
    // are not on its task graph at all — not merely up-to-date, not skipped: absent.
    @Test
    void check_taggedTests_neverExecuteAndTheirTasksAreNotOnTheGraph(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);

        BuildResult result = runner(projectDir, "check").build();

        assertThat(result.task(":alpha:test").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(executedTestClasses(projectDir, "test")).containsExactly(PLAIN);

        // The clearest statement of the invariant: a task Gradle never scheduled cannot run a live model.
        assertThat(result.task(":alpha:liveLocal")).isNull();
        assertThat(result.task(":alpha:promptEval")).isNull();
        assertThat(result.task(":alpha:visual")).isNull();
    }

    // Covers task 5.2/5.3: each tagged task runs exactly its own tag — no leakage in either direction. The
    // `includeTags`-vs-`excludeTags` collision this rules out would leave every one of them running zero tests.
    @Test
    void taggedTasks_eachRunOnlyTheirOwnTag(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);

        runner(projectDir, "liveLocal").build();
        assertThat(executedTestClasses(projectDir, "liveLocal")).containsExactly(LIVE_LOCAL);

        runner(projectDir, "promptEval").build();
        assertThat(executedTestClasses(projectDir, "promptEval")).containsExactly(PROMPT_EVAL);

        runner(projectDir, "visual").build();
        assertThat(executedTestClasses(projectDir, "visual")).containsExactly(VISUAL);
    }

    // Covers task 5.3: a tagged task on a module that carries none of that tag is a green no-op, not a failure.
    // This is the state of six of the eight real modules, and of ALL of them on a machine with no local server —
    // `./gradlew liveLocal` on a fresh checkout must be green-with-skips, so `failOnNoDiscoveredTests` cannot be
    // left at its default here.
    @Test
    void taggedTask_moduleWithNoTestCarryingThatTag_succeedsWithoutRunningAnything(@TempDir Path projectDir)
            throws IOException {
        settings(projectDir, "\":alpha\"");
        qualityConfig(projectDir);
        conventionModule(projectDir);
        write(projectDir, "alpha/src/test/java/fixture/alpha/PlainTest.java", testClass("PlainTest", null));

        BuildResult result = runner(projectDir, "liveLocal").build();

        assertThat(result.task(":alpha:liveLocal").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(executedTestClasses(projectDir, "liveLocal")).isEmpty();
    }

    // Covers task 5.2: a Test task a module registers ITSELF is subject to the same exclusions. `:app` registers
    // exactly such a task (`archTest`, the ArchUnit boundary suite), and a second Test task outside the shared
    // exclusion would be a hole straight through design D5 — which is why the convention configures
    // `tasks.withType<Test>` rather than `tasks.test`.
    @Test
    void customRegisteredTestTask_inheritsTheSharedTagExclusions(@TempDir Path projectDir) throws IOException {
        fixtureProject(projectDir);
        write(
                projectDir,
                "alpha/build.gradle.kts",
                """
                plugins {
                    id("bookloom.java-conventions")
                    id("bookloom.test-conventions")
                }

                // Shaped like `:app`'s `archTest`: a second Test task over an existing source set, registering no
                // JUnit Platform configuration of its own.
                tasks.register<Test>("extraTest") {
                    testClassesDirs = sourceSets["test"].output.classesDirs
                    classpath = sourceSets["test"].runtimeClasspath
                }
                """);

        BuildResult result = runner(projectDir, "extraTest").build();

        assertThat(result.task(":alpha:extraTest").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(executedTestClasses(projectDir, "extraTest")).containsExactly(PLAIN);
    }

    // ===== Fixture scaffolding ==========================================================================

    /** A one-module fixture with one untagged test and one test per local-only tag. */
    private static void fixtureProject(Path projectDir) throws IOException {
        settings(projectDir, "\":alpha\"");
        qualityConfig(projectDir);
        conventionModule(projectDir);

        write(projectDir, "alpha/src/test/java/fixture/alpha/PlainTest.java", testClass("PlainTest", null));
        write(
                projectDir,
                "alpha/src/test/java/fixture/alpha/LiveLocalTest.java",
                testClass("LiveLocalTest", "liveLocal"));
        write(
                projectDir,
                "alpha/src/test/java/fixture/alpha/PromptEvalTest.java",
                testClass("PromptEvalTest", "promptEval"));
        write(projectDir, "alpha/src/test/java/fixture/alpha/VisualTest.java", testClass("VisualTest", "visual"));
    }

    private static void conventionModule(Path projectDir) throws IOException {
        write(
                projectDir,
                "alpha/build.gradle.kts",
                """
                plugins {
                    id("bookloom.java-conventions")
                    id("bookloom.test-conventions")
                }
                """);
    }

    /**
     * A minimal always-passing test class, optionally carrying one tag.
     *
     * <p>The bodies assert nothing interesting on purpose: what is under test here is which classes the JUnit
     * Platform was asked to load, not what they do once loaded.
     */
    private static String testClass(String name, String tag) {
        String annotation = tag == null ? "" : "@org.junit.jupiter.api.Tag(\"%s\")%n".formatted(tag);
        return """
               package fixture.alpha;

               import org.junit.jupiter.api.Test;

               %sclass %s {

                   @Test
                   void run_always_passes() {
                       org.junit.jupiter.api.Assertions.assertTrue(true);
                   }
               }
               """
                .formatted(annotation, name);
    }

    /**
     * The set of test classes a given Test task actually executed, read from its JUnit XML result files.
     *
     * <p>Reading the results rather than parsing build output matters: Gradle prints nothing at all for a test
     * that was filtered out, so "absent from the log" and "never configured to run" are the same observation.
     * A result file exists if and only if the class was loaded and executed.
     */
    private static Set<String> executedTestClasses(Path projectDir, String taskName) throws IOException {
        Path resultsDir = projectDir.resolve("alpha/build/test-results/" + taskName);
        if (!Files.isDirectory(resultsDir)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(resultsDir)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(fileName -> fileName.startsWith("TEST-") && fileName.endsWith(".xml"))
                    .map(fileName -> fileName.substring("TEST-".length(), fileName.length() - ".xml".length()))
                    .collect(Collectors.toSet());
        }
    }
}
