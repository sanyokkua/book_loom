// :app — Launcher, the Application subclass, and the single Guice composition root.
//
// The only module that depends on every other one: assembly happens in exactly one place. That is also why the
// shared `arch-test` source set is hosted here — see below.

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.javafx-conventions")
    id("bookloom.test-conventions")
    // No `bookloom.coverage-conventions` — same reason as `:ui`. `:app` is the composition root and the JavaFX
    // `Application` subclass; it is proven by the boot smoke test and the jpackage-image launch check, not by a
    // branch percentage (02_QUALITY_GATES.md#coverage-gate).
}

dependencies {
    implementation(project(":api"))
    implementation(project(":util"))
    implementation(project(":document"))
    implementation(project(":llm"))
    implementation(project(":persistence"))
    implementation(project(":pipeline"))
    implementation(project(":ui"))
    implementation(libs.guice)

    // The SLF4J facade, used by every class in `:app` that is allowed to log at all.
    implementation(libs.slf4j.api)

    // The binding, and the only place in the project it appears. Logback's concrete types (`LoggerContext`,
    // `RollingFileAppender`) are needed because the appender is configured PROGRAMMATICALLY — a `logback.xml`
    // file appender binds to a default directory at class-load time, before the resolved log dir is known, and
    // the resulting misdirection is silent. That carve-out is confined to one class in
    // `ua.bookloom.app.bootstrap`; everywhere else, including the rest of `:app`, logs through the facade.
    implementation(libs.logback.classic)
}

// --- `./gradlew :app:run` ---------------------------------------------------------------------------------------
//
// A plain JavaExec rather than the `application` plugin: the plugin also installs `distZip`/`installDist` start
// scripts, which are a second, unused distribution mechanism next to the jpackage scripts that are the real one
// (DD-24). One packaging story is the point.
//
// It runs on the CLASSPATH, not the module path. That is deliberate and worth stating, because it is also this
// task's limitation: a classpath launch cannot detect a missing `requires` or an unopened package, so a green
// `:app:run` is NOT evidence the JPMS graph resolves. The packaged image is what proves that (task 7.6), which is
// why the launch smoke exists at all.
val runApp =
    tasks.register<JavaExec>("run") {
        description = "Launches BookLoom from the build output."
        group = ApplicationPlugin.APPLICATION_GROUP

        mainClass = "ua.bookloom.app.bootstrap.Launcher"
        classpath = sourceSets.main.get().runtimeClasspath

        // No BOOKLOOM_ENV and no -Dbookloom.env=prod: an un-stamped run resolves the `-Dev` folder, which is the
        // safety property. A developer running this must not be able to touch their own production database.
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
    }

// --- The build-generated version resource (DD-50) ---------------------------------------------------------------
//
// A classpath RESOURCE rather than the jar manifest's `Implementation-Version`, and that is the load-bearing part:
// the manifest value is null whenever the code runs from class directories rather than from a jar — which is how
// `./gradlew :app:run`, every test, and every IDE launch execute. A version that is only readable in a packaged
// artifact cannot be asserted by the boot smoke, so the one mechanism that would prove it works is the one that
// could never run (01_BUILD_AND_TOOLING.md#version-injection).
//
// The value comes from the root build's `version = providers.gradleProperty("appVersion").orElse("dev")`, read via
// `rootProject` and NOT via `project.version`. Gradle does not propagate a version to subprojects: `:app`'s own
// `project.version` is the literal string "unspecified" unless something sets it, so reading it here would ship an
// artifact reporting `unspecified` while the root build knew the real answer. Naming the root keeps one source of
// truth rather than re-deriving the property and its `dev` fallback in a second place.
//
// Read at CONFIGURATION time into a plain String: reaching for a Project inside a task action would break the
// configuration cache, and the value is fixed for the whole build anyway.
val resolvedAppVersion = rootProject.version.toString()

val generateVersionResource =
    tasks.register("generateVersionResource") {
        description = "Writes ua/bookloom/app/version.properties from the appVersion Gradle property."

        val outputDir = layout.buildDirectory.dir("generated/version")

        // Declared as a task INPUT so up-to-date checking is driven by the version itself. Without this the task
        // would be UP-TO-DATE after a version bump and the artifact would carry the previous release's string —
        // silently, and in exactly the artifact whose whole purpose is to say which release it is.
        inputs.property("version", resolvedAppVersion)
        outputs.dir(outputDir)

        doLast {
            val file = outputDir.get().asFile.resolve("ua/bookloom/app/version.properties")
            file.parentFile.mkdirs()
            file.writeText("version=$resolvedAppVersion\n")
        }
    }

// Registering the task as a resource source wires the `processResources` dependency automatically, so there is no
// hand-written `dependsOn` to fall out of step.
sourceSets.main {
    resources.srcDir(generateVersionResource)
}

// --- `:app:collectDist` — the input jpackage consumes (DD-24) ---------------------------------------------------
//
// A plain `Sync`, and Gradle's ONLY involvement in packaging. No `org.beryx.jlink` or other packaging plugin: the
// packaging surface is tiny and platform-specific, jpackage flags map 1:1 to what the scripts write, and there is no
// plugin-version drift stacked on top of JDK churn (03_PACKAGING_JPACKAGE.md#approach).
//
// `Sync` rather than `Copy` on purpose. Copy leaves deleted files behind, so a renamed or dropped dependency would
// linger in the staging directory and be baked into the image — a stale jar that no build output explains.
//
// The destination is `modules/app/build/dist/libs/`. The frozen spec says `app/build/dist/libs/` in four places and
// **ADR-0021 supersedes all four** — the layout move that put the nine code directories under `modules/` was
// sequenced immediately before this change precisely so this path is written correctly the first time.
val collectDist =
    tasks.register<Sync>("collectDist") {
        description = "Stages the application jar and its full runtime classpath for jpackage."
        group = "distribution"

        // The runtime classpath carries the per-OS JavaFX platform jars the openjfx plugin resolved, so the staged
        // set is already correct for the machine doing the packaging — which is also why jpackage cannot
        // cross-compile and every artifact is built on its own OS.
        from(tasks.named("jar"))
        from(configurations.named("runtimeClasspath"))

        into(layout.buildDirectory.dir("dist/libs"))
    }

// --- The shared `arch-test` source set (design D4) --------------------------------------------------------------
//
// The eight boundary rules of `02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules` live HERE, once, and
// nowhere else.
//
// Why `:app` and not a ninth subproject: ArchUnit reasons over compiled bytecode reachable on a classpath, and
// `:app` is the only project whose classpath carries all eight modules at once. `dependency-direction` is
// meaningless from inside a single module — a rule that cannot see both ends of an edge cannot judge it — and
// per-module copies of the other seven would drift apart the first time one was edited without the others.
// `settings.gradle.kts` fixes the module set at eight; this is a source set, not a ninth module.
val archTest: SourceSet by sourceSets.creating

// The rules must see everything `:app` sees (the seven module jars, Guice, JavaFX), plus `:app`'s own classes.
configurations["archTestImplementation"].extendsFrom(configurations["implementation"])
configurations["archTestRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])

// …and the whole shared test stack, rather than a hand-copied subset of it. `bookloom.test-conventions` puts
// JUnit 5 (through the BOM), AssertJ, Mockito, WireMock, TestFX and the platform launcher on `test*`; extending
// from those four configurations means `archTest` tracks that decision automatically. The alternative — repeating
// the coordinates here — is a second list that drifts the first time the shared one changes, and the drift is
// invisible until a rule test fails to compile.
//
// `bookloom.java-conventions` reaches `archTest` the same way: JSpecify (`@NullMarked`, so NullAway analyses
// these sources too) and Lombok arrive on `testCompileOnly`/`testAnnotationProcessor`.
listOf("Implementation", "CompileOnly", "AnnotationProcessor", "RuntimeOnly").forEach { kind ->
    configurations["archTest$kind"].extendsFrom(configurations["test$kind"])
}

dependencies {
    // `:app`'s own production classes. The seven other modules arrive through `implementation` above; this adds
    // the eighth, which is on no configuration.
    "archTestImplementation"(sourceSets.main.get().output)

    // What remains here is exactly what is specific to the arch suite and to nothing else in the project.

    // The rule engine itself.
    "archTestImplementation"(libs.archunit.junit5)

    // Compiled against by the `bootstrap-no-static-logger` fixtures only: a static `org.slf4j.Logger` field and
    // a static initializer that calls `LoggerFactory`. Both need the real type to exist.
    "archTestImplementation"(libs.slf4j.api)

    // Compiled against by the records-first NEGATIVE control: a `@RequiredArgsConstructor` + `@Slf4j` service
    // that the rule must NOT flag, because DD-05/ADR-0014 make Lombok-on-services the intended hybrid. Declared
    // explicitly, next to the fixture that needs it, even though `testCompileOnly` above already supplies it.
    "archTestCompileOnly"(libs.lombok)
    "archTestAnnotationProcessor"(libs.lombok)
}

// --- Dependency-locking carve-out ------------------------------------------------------------------------------
//
// The same problem, and the same answer, as design decision D7 in `bookloom.javafx-conventions`: these two
// configurations extend `implementation`, so they inherit the per-OS classified JavaFX artifacts. Gradle's lock
// state keys on `group:name:version` and carries no classifier, so a lock entry here would pin nothing about
// which of the four platform artifacts is actually used while looking like it did — a reproducibility claim the
// build cannot honour. The versions that matter are pinned exactly in `gradle/libs.versions.toml`, and the same
// platform-neutral coordinates remain locked on the six modules JavaFX never reaches.
listOf("archTestCompileClasspath", "archTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
        resolutionStrategy.deactivateDependencyLocking()
    }
}

val archTestTask =
    tasks.register<Test>("archTest") {
        description = "Runs the ArchUnit boundary suite against the whole eight-module graph."
        group = LifecycleBasePlugin.VERIFICATION_GROUP

        testClassesDirs = archTest.output.classesDirs
        classpath = archTest.runtimeClasspath

        // No `useJUnitPlatform()` here. `bookloom.test-conventions` configures every `Test` task in the project
        // — this one included — with the platform AND with `excludeTags("liveLocal", "promptEval", "visual")`.
        // Re-declaring the framework locally would fork a second configuration path for the one task that most
        // needs to be subject to the shared one: a Test task outside the shared exclusion is precisely the hole
        // design D5 exists to close. The suite carries no tags, so all of its rules still run.

        // Ordered after the unit tests purely for readable output; it has no dependency on them.
        shouldRunAfter(tasks.named("test"))
    }

// A boundary rule that is not on the `check` graph is documentation, not a gate.
tasks.named("check") {
    dependsOn(archTestTask)
}
