// JavaFX conventions — applied to :ui and :app ONLY.
//
// This is a separate plugin rather than a conditional inside `bookloom.java-conventions` on purpose. It makes the
// FX-free core structural instead of conventional: a core module cannot drift into a JavaFX dependency, because
// acquiring one would mean deliberately applying a plugin it has no reason to apply
// (docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#javafx-plugin, DD-06).

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.openjfx.javafxplugin")
}

// Precompiled script plugins do not get the generated `libs.*` accessors — those exist only in the main build's
// scripts. Reading the catalog by name keeps the version pinned in exactly one file all the same.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

javafx {
    version = catalog.findVersion("javafx").orElseThrow().requiredVersion

    // Only the three modules the spec names. `javafx.media` and `javafx.web` stay out: they are large, pull
    // native code we never call, and would bloat the jpackage image.
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

// --- Native access ------------------------------------------------------------------------------------------------
//
// JavaFX loads its native glass/prism libraries through `System.load`, which JEP 472 made a restricted method. On
// JDK 25 that prints a four-line warning per test JVM; on a future JDK it will be an error outright ("Restricted
// methods will be blocked in a future release unless native access is enabled"). Granting it explicitly here — on
// the two modules that actually carry JavaFX, and nowhere else — keeps the headless test output readable and means
// the UI test tier does not break on a toolchain bump.
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// --- Dependency locking carve-out (design D7) -----------------------------------------------------------------
//
// `bookloom.java-conventions` turns locking on for every configuration. The four below are turned back OFF here,
// in the plugin that causes the problem, so the exemption sits next to its reason.
//
// The reason, verified rather than assumed: `org.openjfx.javafxplugin` declares the platform artifact with a
// CLASSIFIER — this machine resolves `org.openjfx:javafx-graphics:26.0.2` and downloads
// `javafx-graphics-26.0.2-mac-aarch64.jar`. Gradle's lock state keys on module identity (`group:name:version`)
// and carries no classifier, so a committed lock entry for JavaFX would read `org.openjfx:javafx-graphics:26.0.2`
// and pin NOTHING about which of the four per-OS artifacts (`win`/`mac`/`mac-aarch64`/`linux`) is actually used —
// that is still decided by the plugin's platform detection at resolution time. Recording it would look like a
// reproducibility guarantee while providing none, which `01_BUILD_AND_TOOLING.md#dependency-locking` explicitly
// forbids asserting. The alternative the spec also permits — one lockfile per OS, each written on that OS —
// needs three machines to regenerate a lock and would block a contributor on a platform nobody has handy.
//
// What is given up: the platform-neutral libraries on these four classpaths (Guice and its Guava/jakarta.inject
// subtree) are not recorded in `:ui`/`:app` lock state. Their versions are still pinned — the SAME coordinates are
// locked on `:document`, `:llm`, `:pipeline` and `:persistence`, whose classpaths carry no JavaFX — so an upstream
// republish still fails the build; it fails there rather than here. What is retained: `:ui`/`:app` keep locked
// `annotationProcessor`, `errorprone`, `checkstyle`, `spotbugs` and `spotbugsPlugins` configurations, none of
// which JavaFX reaches.
//
// The JavaFX version itself is pinned exactly in `gradle/libs.versions.toml` (no range, no `+`), which is what
// the spec means by "rely on the exact catalog pin for those artifacts".
val javafxContaminated =
    listOf(
        // `javafx { }` declares into `implementation`; these are the four resolvable configurations that
        // extend it, directly or through `testImplementation`.
        "compileClasspath",
        "runtimeClasspath",
        "testCompileClasspath",
        "testRuntimeClasspath",
    )

javafxContaminated.forEach { name ->
    configurations.named(name) {
        resolutionStrategy.deactivateDependencyLocking()
    }
}
