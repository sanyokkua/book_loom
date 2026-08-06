package ua.bookloom.buildlogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.gradle.testkit.runner.GradleRunner;

/**
 * Builds throwaway Gradle projects for the convention-plugin functional tests.
 *
 * <p>Every fixture is generated fresh inside a JUnit {@code @TempDir} and built with its own, self-contained
 * {@code settings.gradle.kts}. No test ever points {@link GradleRunner} at the real repository root:
 * {@code build-logic} is an included build of that root, so doing so would recurse.
 *
 * <p>A fixture is nonetheless wired to the <em>real</em> shared configuration — the committed version catalog,
 * the committed Checkstyle ruleset, the committed SpotBugs filter, the committed {@code lombok.config}. A canary
 * proven against a copy of the config would prove nothing about the config that actually ships.
 */
final class BuildFixture {

    /** The repository root, published by the {@code test} task in {@code build-logic/build.gradle.kts}. */
    private static final Path REPO_ROOT =
            Path.of(System.getProperty("bookloom.repoRoot", "..")).toAbsolutePath().normalize();

    private BuildFixture() {}

    /** The repository root, so a test can assert against files this change actually committed. */
    static Path repoRoot() {
        return REPO_ROOT;
    }

    /**
     * The directory of one code module, all nine of which live under {@code modules/} (ADR-0021).
     *
     * <p>Naming the layout here rather than at each call site means the next layout change edits one line, and it
     * lets the lockfile assertions speak about <em>a module</em> rather than about <em>a path</em>.
     *
     * @param module the Gradle project name without its leading colon, or {@code build-logic}
     */
    static Path moduleDir(String module) {
        return REPO_ROOT.resolve("modules").resolve(module);
    }

    /**
     * Writes a {@code settings.gradle.kts} that resolves the real {@code libs} version catalog.
     *
     * <p>The convention plugins read every pinned version out of that catalog by name, so a fixture without it
     * fails at configuration time rather than at the assertion the test is actually making.
     */
    static void settings(Path projectDir, String includes) throws IOException {
        settings(projectDir, includes, "");
    }

    /**
     * As {@link #settings(Path, String)}, plus extra repository declarations.
     *
     * <p>Used by the license-gate tests, which resolve a canary artifact from a local file repository generated
     * inside the fixture. Publishing the canary locally rather than naming a real GPL coordinate keeps the test
     * hermetic and keeps a copyleft artifact out of the project's resolution entirely — the gate is proven
     * against a license string, and a license string is all that needs to be real.
     *
     * @param extraRepositories Kotlin DSL fragment inserted into the {@code repositories { }} block
     */
    static void settings(Path projectDir, String includes, String extraRepositories) throws IOException {
        String catalogPath = REPO_ROOT.resolve("gradle/libs.versions.toml").toString().replace('\\', '/');
        write(
                projectDir,
                "settings.gradle.kts",
                """
                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                %s
                    }
                    versionCatalogs {
                        create("libs") {
                            from(files("%s"))
                        }
                    }
                }

                rootProject.name = "fixture"
                include(%s)
                """
                        .formatted(extraRepositories, catalogPath, includes));
    }

    /**
     * Copies the committed Checkstyle ruleset, SpotBugs exclusion filter and {@code lombok.config} into the
     * fixture, at the same repository-relative paths the convention plugins resolve them from.
     */
    static void qualityConfig(Path projectDir) throws IOException {
        copy(projectDir, "config/checkstyle/checkstyle.xml");
        copy(projectDir, "config/checkstyle/suppressions.xml");
        copy(projectDir, "config/spotbugs/exclude.xml");
        copy(projectDir, "lombok.config");
    }

    /** Writes one fixture file, creating its parent directories first. */
    static void write(Path projectDir, String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Reads one fixture file back, for assertions about what a task rewrote. */
    static String read(Path projectDir, String relativePath) throws IOException {
        return Files.readString(projectDir.resolve(relativePath));
    }

    /**
     * A runner for the fixture.
     *
     * <p>Gradle TestKit otherwise gives each fixture a private Gradle user home, and therefore an empty module
     * cache — every test would re-download the entire quality stack. {@code -g} points the fixture at the outer
     * build's Gradle user home so it reuses artifacts already resolved.
     *
     * <p>These builds are deliberately NOT {@code --offline}: dependency resolution is build-time, not
     * application behaviour, so it does not touch the offline invariant, and a cold cache must still be able to
     * resolve rather than fail with a misleading "no cached version" error.
     */
    static GradleRunner runner(Path projectDir, String... arguments) {
        String[] all = new String[arguments.length + 2];
        System.arraycopy(arguments, 0, all, 0, arguments.length);
        all[arguments.length] = "-g";
        all[arguments.length + 1] =
                System.getProperty("bookloom.gradleUserHome", System.getProperty("user.home") + "/.gradle");
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(all);
    }

    private static void copy(Path projectDir, String relativePath) throws IOException {
        Path target = projectDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(REPO_ROOT.resolve(relativePath), target, StandardCopyOption.REPLACE_EXISTING);
    }
}
