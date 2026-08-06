package ua.bookloom.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.buildlogic.BuildFixture.qualityConfig;
import static ua.bookloom.buildlogic.BuildFixture.read;
import static ua.bookloom.buildlogic.BuildFixture.repoRoot;
import static ua.bookloom.buildlogic.BuildFixture.runner;
import static ua.bookloom.buildlogic.BuildFixture.settings;
import static ua.bookloom.buildlogic.BuildFixture.write;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the license gate rejects a banned license, accepts a permissive one, and ignores build tooling — using
 * the committed {@code config/license/allowed-licenses.json}, not a copy of it.
 *
 * <p>The canary artifacts are generated into a local file repository inside each fixture rather than named as
 * real coordinates. That keeps the test hermetic (no upstream can re-license out from under it) and keeps a
 * copyleft artifact out of the project's own resolution entirely. The gate reads a license string out of a POM,
 * so a license string is the only part that has to be real.
 *
 * <p>The first two tests together are what make the third meaningful: the same GPL artifact fails on
 * {@code implementation} and passes on {@code checkstyle}, which isolates <em>configuration scoping</em> — not an
 * allowlist entry — as the mechanism implementing {@code 03_LICENSING.md#build-tool-exception}.
 *
 * <p>Covers task 3.5 of the {@code bootstrap-gradle-and-quality-toolchain} change (tasks 3.3 and 3.4 are the
 * subject).
 */
class LicenseGateFunctionalTest {

    private static final String GPL = "canary:gpl-canary:1.0";
    private static final String PERMISSIVE = "canary:permissive-canary:1.0";

    // Covers task 3.5: a runtime dependency carrying a banned license must fail `checkLicense`, and the failure
    // must NAME the offending coordinate — a gate that fails without saying what tripped it sends the reader to
    // bisect a dependency graph by hand.
    @Test
    void checkLicense_gplDependencyOnRuntimeClasspath_failsNamingTheCoordinate(@TempDir Path projectDir)
            throws IOException {
        licenseFixture(projectDir, "implementation(\"" + GPL + "\")");

        BuildResult result = runner(projectDir, "checkLicense").buildAndFail();

        assertThat(result.getOutput())
                .contains("Some libraries do not declare at least one allowed license")
                .contains("canary:gpl-canary:1.0")
                .contains("GNU GENERAL PUBLIC LICENSE, Version 3");
    }

    // Covers task 3.5: the positive control. Without it, the test above proves only that `checkLicense` can fail
    // — which a broken allowlist path, an unreadable policy file, or a normalizer that matches nothing would also
    // achieve, while rejecting every dependency in the project.
    @Test
    void checkLicense_apacheDependencyOnRuntimeClasspath_passes(@TempDir Path projectDir) throws IOException {
        licenseFixture(projectDir, "implementation(\"" + PERMISSIVE + "\")");

        runner(projectDir, "checkLicense").build();

        assertThat(read(projectDir, "build/reports/dependency-license/THIRD-PARTY-NOTICES"))
                .contains("permissive-canary");
    }

    // Covers task 3.5 / 03_LICENSING.md#build-tool-exception: the SAME banned artifact on a build-tool
    // configuration must pass.
    //
    // This is the clause that lets the allowlist stay strict. Checkstyle and SpotBugs are LGPL-2.1 — a license
    // this project bans outright — and they pass the gate not because an exception was recorded for them but
    // because they live on `checkstyle`/`spotbugs`, reach no user, and are never resolved by a gate scoped to
    // `runtimeClasspath`. If that scoping were ever widened, this test goes red while the allowlist file still
    // looks perfectly correct.
    @Test
    void checkLicense_gplArtifactOnBuildToolConfiguration_passesBecauseItIsNotShipped(@TempDir Path projectDir)
            throws IOException {
        licenseFixture(projectDir, "\"checkstyle\"(\"" + GPL + "\")");

        runner(projectDir, "checkLicense").build();

        // Present in the build, absent from the shipped inventory — which is the whole distinction.
        assertThat(read(projectDir, "build/reports/dependency-license/THIRD-PARTY-NOTICES"))
                .doesNotContain("gpl-canary");
    }

    // ===== Fixture scaffolding =========================================================================

    /**
     * A fixture applying the real {@code bookloom.java-conventions} (which brings the LGPL Checkstyle and SpotBugs
     * configurations) plus the license-report plugin, wired to the committed allowlist.
     */
    private static void licenseFixture(Path projectDir, String dependencyDeclaration) throws IOException {
        Path canaryRepo = projectDir.resolve("canary-repo");
        publishCanary(canaryRepo, "gpl-canary", "GNU General Public License, version 3", "gpl-3.0");
        publishCanary(canaryRepo, "permissive-canary", "Apache License, Version 2.0", "LICENSE-2.0");

        settings(
                projectDir,
                "",
                "        maven { url = uri(\"%s\") }".formatted(canaryRepo.toString().replace('\\', '/')));
        qualityConfig(projectDir);

        write(
                projectDir,
                "build.gradle.kts",
                """
                import com.github.jk1.license.filter.LicenseBundleNormalizer
                import com.github.jk1.license.render.ReportRenderer
                import com.github.jk1.license.render.TextReportRenderer

                plugins {
                    id("bookloom.java-conventions")
                    alias(libs.plugins.license.report)
                }

                dependencies {
                    %s
                }

                licenseReport {
                    projects = arrayOf(project)
                    configurations = arrayOf("runtimeClasspath")
                    allowedLicensesFile = file("%s")
                    filters = arrayOf(LicenseBundleNormalizer())
                    // Explicitly `Array<ReportRenderer>`: the plugin appends its own JsonReportRenderer at
                    // execution time, and an inferred `Array<TextReportRenderer>` throws on that store.
                    renderers = arrayOf<ReportRenderer>(TextReportRenderer("THIRD-PARTY-NOTICES"))
                }
                """
                        .formatted(
                                dependencyDeclaration,
                                repoRoot()
                                        .resolve("config/license/allowed-licenses.json")
                                        .toString()
                                        .replace('\\', '/')));
    }

    /** Writes a minimal Maven-layout module whose POM declares exactly one license. */
    private static void publishCanary(Path repoDir, String artifact, String licenseName, String urlSlug)
            throws IOException {
        Path moduleDir = repoDir.resolve("canary").resolve(artifact).resolve("1.0");
        Files.createDirectories(moduleDir);

        Files.writeString(
                moduleDir.resolve(artifact + "-1.0.pom"),
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>canary</groupId>
                  <artifactId>%s</artifactId>
                  <version>1.0</version>
                  <name>%s</name>
                  <licenses>
                    <license>
                      <name>%s</name>
                      <url>https://example.invalid/%s.txt</url>
                    </license>
                  </licenses>
                </project>
                """
                        .formatted(artifact, artifact, licenseName, urlSlug));

        // The plugin opens the artifact to read its manifest, so the jar has to be a real archive rather than an
        // empty file. It carries no classes: nothing ever compiles or runs against these.
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (OutputStream out = Files.newOutputStream(moduleDir.resolve(artifact + "-1.0.jar"));
                JarOutputStream jar = new JarOutputStream(out, manifest)) {
            jar.flush();
        }
    }
}
