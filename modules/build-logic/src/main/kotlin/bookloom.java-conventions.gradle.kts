// Java toolchain and JPMS conventions shared by all eight subprojects.
//
// Pinning the toolchain means a contributor with JDK 21 installed still builds against 25 — Gradle downloads it —
// so "works on my machine" cannot diverge on language level
// (docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#build-system, ADR-0001).
//
// Also owns the compiler-facing quality stack per design decision D2: Lombok as the sole annotation processor,
// Error Prone + NullAway as javac plugins, Checkstyle and SpotBugs + FindSecBugs bound into `check`.

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.LockMode

plugins {
    java
    id("net.ltgt.errorprone")
    checkstyle
    id("com.github.spotbugs")
}

// Precompiled script plugins do not get the generated `libs.*` accessors — those exist only in the main build's
// scripts. Reading the catalog by name keeps every version pinned in exactly one file all the same.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = catalog.findLibrary(alias).orElseThrow()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }

    // Every subproject carries a `module-info.java`; inferring the module path is what makes a forbidden
    // `requires` fail at COMPILE time, before ArchUnit runs.
    modularity.inferModulePath = true
}

// Checkstyle covers the naming and structural conventions Spotless does not: Spotless FORMATS, it does not
// JUDGE. The ruleset lives once at the repository root so all eight modules are held to one standard
// (02_QUALITY_GATES.md#tools).
checkstyle {
    toolVersion = catalog.findVersion("checkstyle").orElseThrow().requiredVersion
    configDirectory = rootProject.layout.projectDirectory.dir("config/checkstyle")

    // A findings count that is allowed to be non-zero is a gate that never fails.
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    // Checkstyle's parser throws on a module declaration, so `module-info.java` must be excluded from the
    // task's SOURCE rather than merely suppressed — a `SuppressionFilter` filters violations, and a file that
    // fails to parse never produces one. Nothing is lost: the module graph is enforced by javac itself and by
    // the `dependency-direction` ArchUnit rule, neither of which is a style concern.
    exclude("module-info.java")
}

// SpotBugs analyses bytecode, so it sees what the source-level tools cannot. The FindSecBugs plugin is the
// automated backstop for the credentials-as-reference invariant: it flags a hard-coded secret or an unsafe
// credential path that review can miss (02_QUALITY_GATES.md#tools, DD-11).
spotbugs {
    toolVersion = catalog.findVersion("spotbugs").orElseThrow().requiredVersion

    // MAX effort with a HIGH confidence floor: analyse thoroughly, then report only findings SpotBugs is
    // confident about. The reverse — cheap analysis reported loosely — is how a security tool earns the
    // reputation that gets it switched off.
    effort = Effort.MAX
    reportLevel = Confidence.HIGH

    ignoreFailures = false

    // Exclusions live in a file rather than inline, so an exemption is reviewable next to its reason.
    excludeFilter = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}

dependencies {
    // A CONSTRAINT, not a declared dependency. Guice 7.0.0 pins Guava 31.0.1-jre transitively; BookLoom does not
    // use Guava itself and must not gain it as a first-class compile dependency just to raise that version. A
    // constraint says only "if Guava resolves at all, it must be at least this" and leaves it transitive-only
    // (02_DEPENDENCY_POLICY.md#narrowest-scope).
    //
    // Two reasons the floor matters, and the second is why this is not optional:
    //   * 31.0.1-jre calls a terminally-deprecated `sun.misc.Unsafe` method and warns on JDK 25.
    //   * 31.0.1-jre carries CVE-2023-2976 (CVSS 7.1) — `FileBackedOutputStream` creates files in the shared
    //     default temp directory, readable by other local users. Fixed in 32.0.1.
    //
    // Declared in the shared convention rather than per module because all six Guice consumers inherit it here,
    // and a constraint that covers five of six modules is not a floor.
    constraints {
        "implementation"(lib("guava")) {
            because(
                "Guice 7.0.0 pins Guava 31.0.1-jre, which carries CVE-2023-2976 (CVSS 7.1, fixed in 32.0.1) " +
                    "and calls a terminally-deprecated sun.misc.Unsafe method that warns on JDK 25",
            )
        }
    }

    "spotbugsPlugins"(lib("findsecbugs"))

    // Lombok is the SOLE entry on `annotationProcessor`. Error Prone and NullAway are javac *plugins*, wired
    // below through the `errorprone` configuration — there is no processor-ordering problem to solve, because
    // there is no second processor (01_BUILD_AND_TOOLING.md#error-prone-lombok, design D3).
    "compileOnly"(lib("lombok"))
    "annotationProcessor"(lib("lombok"))
    "testCompileOnly"(lib("lombok"))
    "testAnnotationProcessor"(lib("lombok"))

    // JSpecify supplies `@NullMarked`/`@Nullable`. Compile-only: the annotations are CLASS-retention markers
    // NullAway reads at compile time and nothing reflects on at runtime.
    "compileOnly"(lib("jspecify"))
    "testCompileOnly"(lib("jspecify"))

    "errorprone"(lib("errorprone-core"))
    "errorprone"(lib("nullaway"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25

    options.errorprone {
        // NullAway violations are CORRECTNESS findings and fail the build
        // (02_QUALITY_GATES.md#null-safety).
        check("NullAway", CheckSeverity.ERROR)

        // NullAway analyses ONLY packages that opted in with a JSpecify `@NullMarked` package-info. An
        // unmarked package is silently unanalysed — the marker is what turns the tool on, which is why task
        // 2.6 seeds one in every package this change creates.
        option("NullAway:OnlyNullMarked", "true")

        // Lombok desugars before the Error Prone plugin walks the tree, so NullAway sees generated members
        // rather than annotations. `lombok.addNullAnnotations` (root `lombok.config`) makes those members
        // carry the right nullability; this is the documented fallback for any emission it still trips on.
        option("NullAway:HandleTestAssertionLibraries", "true")

        disableWarningsInGeneratedCode = true
    }

    options.compilerArgs.addAll(
        listOf(
            // Error Prone requires the simple compile policy so its javac plugin sees one flat compilation
            // per file. Not optional — without it Error Prone refuses to run
            // (01_BUILD_AND_TOOLING.md#error-prone-lombok).
            "-XDcompilePolicy=simple",
            // Warnings are errors: a lint finding that is merely printed is a lint finding nobody reads.
            "-Xlint:all",
            // Annotation-processor discovery notes are noise; Lombok is wired explicitly.
            "-Xlint:-processing",
            // Guice ships as an automatic module. Depending on one is a deliberate, reviewed choice here,
            // not an accident, so the advisory warning would only ever be suppressed noise.
            "-Xlint:-requires-automatic",
            "-Xlint:-requires-transitive-automatic",
            // Each module exports a public Guice `Module` extending `AbstractModule`, so `:app` can install it.
            // javac warns that the supertype is not re-exported. The fix it implies — `requires transitive
            // com.google.guice` — is the wrong one: it would put Guice on every downstream module's compile
            // classpath, exactly the transitive leak 02_DEPENDENCY_POLICY.md#narrowest-scope forbids. This is a
            // closed-world application, not a published library: `:app` declares its own `requires`.
            "-Xlint:-exports",
            // Same reasoning, and it is load-bearing for the Lombok hybrid: this lint inspects the source AST
            // before Lombok's generated constructor exists, so it fires on EVERY `@RequiredArgsConstructor`
            // service in an exported package (verified — a Lombok canary in `:document` fails `-Werror` on it).
            // The lint guards published-library API stability against an accidental default constructor; this
            // is a closed-world application with no external consumers, and DD-05/ADR-0014 mandate exactly the
            // Lombok usage it flags.
            "-Xlint:-missing-explicit-ctor",
            "-Werror",
        ),
    )
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

// Dependency locking. A locked graph means an upstream republishing a version cannot silently change a build,
// and a version bump becomes a reviewable diff in `gradle/*.lockfile` instead of an invisible resolution change
// (01_BUILD_AND_TOOLING.md#dependency-locking).
//
// `lockAllConfigurations()` registers a `configureEach` hook, so it also covers configurations created later by
// another plugin — which is what puts the quality path (`errorprone`, `checkstyle`, `spotbugs`,
// `spotbugsPlugins`, `annotationProcessor`) under lock alongside the compile and runtime classpaths.
// `bookloom.javafx-conventions` deactivates it again on the two configurations JavaFX contaminates; see D7 there.
dependencyLocking {
    lockAllConfigurations()

    // STRICT lock mode, opted into with `-PstrictLocks` (the CI merge gate passes it; see
    // `.github/workflows/ci.yml`, task 7.4). Gradle exposes NO command-line flag for the lock mode — `--write-locks`
    // and `--update-locks` are the only locking flags — so the switch has to live in the build script and be driven
    // by a property.
    //
    // What STRICT adds over DEFAULT is narrow but exactly the hole worth closing. In DEFAULT mode a configuration
    // that has lock state and whose resolution no longer matches it already fails, so a bumped catalog version is
    // caught either way. What DEFAULT does NOT catch is a configuration with NO lock state at all: it resolves
    // freely and silently. That is the realistic regression here — a new module, a new tool configuration, or a
    // `--write-locks` run that missed a configuration leaves an unlocked classpath that looks locked because the
    // other seven modules are. STRICT turns that into a failure.
    //
    // Opt-in rather than always-on because locally the unlocked-configuration state is a legitimate intermediate:
    // you add a module, then run `resolveAndLockAll --write-locks`. Making that ordering a hard error would only
    // teach contributors to reach for `--write-locks` reflexively, which is how an unreviewed lock diff lands.
    if (providers.gradleProperty("strictLocks").isPresent) {
        lockMode = LockMode.STRICT
    }
}

// Regenerating lock state after a catalog bump: `./gradlew resolveAndLockAll --write-locks`.
//
// A plain `./gradlew build --write-locks` only records the configurations that build happens to resolve, which
// silently leaves the quality-path configurations out of the lock state and makes the next contributor's diff
// depend on which task they ran. Resolving every resolvable configuration explicitly is the canonical Gradle
// recipe for a complete, deterministic lockfile.
tasks.register("resolveAndLockAll") {
    notCompatibleWithConfigurationCache("Resolves configurations at execution time")

    doFirst {
        // Without `--write-locks` this task would resolve everything and write nothing, reporting success while
        // leaving the lockfiles exactly as stale as they were.
        require(gradle.startParameter.isWriteDependencyLocks) {
            "resolveAndLockAll must be run with --write-locks"
        }
    }

    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}

// The read-only counterpart of `resolveAndLockAll`: same full resolution, writes nothing. This is the CI lock gate
// (task 7.4), run as `./gradlew -PstrictLocks verifyLocks` BEFORE the quality gate so a lock-state problem fails in
// seconds rather than after a compile and a full test run.
//
// It has to be its own task rather than a flag on the gate command, because D8 requires the pre-push hook and the CI
// quality job to run the byte-identical `./gradlew clean build check spotlessCheck`. Bolting `-PstrictLocks` onto
// that command in CI only would break the identity that makes "a green push implies a green CI quality job" true.
// CI may add gates; it may not run a different version of the shared one.
tasks.register("verifyLocks") {
    description = "Resolves every lockable configuration without writing lock state. Use with -PstrictLocks."
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    notCompatibleWithConfigurationCache("Resolves configurations at execution time")

    doFirst {
        // `--write-locks` would make this task rewrite the very state it is supposed to be checking and then pass,
        // which is the one way it could report a green lock gate on a stale lockfile.
        require(!gradle.startParameter.isWriteDependencyLocks) {
            "verifyLocks must NOT be run with --write-locks — it verifies lock state, it does not produce it. " +
                "Use resolveAndLockAll --write-locks to regenerate."
        }
    }

    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
