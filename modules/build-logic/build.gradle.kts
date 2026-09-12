import org.gradle.api.artifacts.dsl.LockMode

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
