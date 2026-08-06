import org.gradle.api.artifacts.dsl.LockMode
import java.time.Duration

// Hosts the precompiled convention plugins (`src/main/kotlin/bookloom.*-conventions.gradle.kts`).
//
// Shared configuration lives here rather than in a root `allprojects { }` block: a convention plugin is
// configuration-cache friendly and can be tested, an `allprojects` block is neither
// (docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#convention-plugins).

plugins {
    `kotlin-dsl`
}

dependencies {
    // Third-party Gradle plugins are consumed here as *dependencies* so the precompiled script plugins
    // in `src/main/kotlin` can `apply` them by id. Coordinates come from the shared catalog — never inline
    // strings.
    implementation(libs.plugin.javafx)
    implementation(libs.plugin.spotless)
    implementation(libs.plugin.errorprone)
    implementation(libs.plugin.spotbugs)

    // Functional tests drive the precompiled convention plugins through Gradle TestKit against a throwaway
    // fixture project generated under a JUnit `@TempDir` — never against the real repository root, which
    // would recurse (`build-logic` is an included build). Coordinates come from the shared catalog, same as
    // `implementation` above.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

// `build-logic` is a separate included build with its own resolution, so it needs its own lock state — the main
// build's lockfiles say nothing about the Gradle plugins that produce the main build's configuration. Without
// this, the versions of Spotless, Error Prone, SpotBugs and the JavaFX plugin — the tools that decide whether
// the whole quality gate even runs — would be the only unpinned part of the graph
// (01_BUILD_AND_TOOLING.md#dependency-locking).
dependencyLocking {
    lockAllConfigurations()

    // Same `-PstrictLocks` opt-in the eight subprojects get from `bookloom.java-conventions`; repeated here for the
    // same reason the block above is — an included build shares no build script with them. The CI lock gate runs
    // `./gradlew -p modules/build-logic -PstrictLocks verifyLocks` as well, because an unlocked plugin classpath
    // here would leave the tools that decide whether the quality gate runs at all outside the lock state.
    if (providers.gradleProperty("strictLocks").isPresent) {
        lockMode = LockMode.STRICT
    }
}

// Same recipe as `bookloom.java-conventions` registers for the eight subprojects; repeated here because an
// included build shares no build script with them. Regenerate with:
//   ./gradlew -p modules/build-logic resolveAndLockAll --write-locks
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Resolves configurations at execution time")

    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "resolveAndLockAll must be run with --write-locks"
        }
    }

    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}

// The read-only counterpart, mirroring the one `bookloom.java-conventions` registers for the eight subprojects.
// CI runs `./gradlew -p modules/build-logic -PstrictLocks verifyLocks`.
tasks.register("verifyLocks") {
    description = "Resolves every lockable configuration without writing lock state. Use with -PstrictLocks."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    notCompatibleWithConfigurationCache("Resolves configurations at execution time")

    doFirst {
        require(!gradle.startParameter.isWriteDependencyLocks) {
            "verifyLocks must NOT be run with --write-locks — it verifies lock state, it does not produce it. " +
                "Use resolveAndLockAll --write-locks to regenerate."
        }
    }

    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}

gradlePlugin {
    // Registers `src/test` as a plugin-under-test source set: Gradle writes the `plugin-under-test-metadata`
    // properties file so `GradleRunner.withPluginClasspath()` can resolve `bookloom.java-conventions` inside
    // the fixture build without publishing it anywhere.
    testSourceSets.add(sourceSets["test"])
}

tasks.test {
    useJUnitPlatform()

    // Fixture builds must resolve the SAME version catalog and the SAME Checkstyle / SpotBugs rulesets the real
    // build uses — a canary proven against a copy of the config proves nothing about the config that ships.
    // `rootDir` here is `modules/build-logic/` (ADR-0021), so the repository root is TWO hops up: the first
    // parent is `modules/`, the second is the root. One hop resolves to `modules/` — a directory that exists,
    // so nothing throws; `BuildFixture` would simply normalize a valid path to the wrong place and the damage
    // would surface far away as "cannot find `checkstyle.xml`" or "cannot find `allowed-licenses.json`".
    systemProperty("bookloom.repoRoot", rootDir.parentFile.parentFile.absolutePath)

    // Fixture builds run through Gradle TestKit, which otherwise hands each one a private Gradle user home and
    // therefore an EMPTY module cache — every fixture would re-download the whole quality stack (Error Prone,
    // NullAway, Checkstyle, SpotBugs, FindSecBugs, Palantir, Lombok, JSpecify). Pointing them at this build's
    // Gradle user home reuses the artifacts the outer build already resolved.
    systemProperty("bookloom.gradleUserHome", gradle.gradleUserHomeDir.absolutePath)

    // Each fixture build forks a Gradle daemon running a real JDK 25 toolchain and a full quality stack.
    // Ten minutes is a generous ceiling that still fails rather than hanging CI forever.
    timeout = Duration.ofMinutes(10)
}
