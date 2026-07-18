**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`,
`docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`,
`docs/specification/05_Dependencies/02_DEPENDENCY_POLICY.md`,
`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`

# Build and Tooling

The build is Gradle with the Kotlin DSL, a committed wrapper, a version catalog, and `build-logic` convention plugins.
JDK 25 is pinned via a toolchain. This document fixes the build layout and the task surface every other document
assumes.

## build-system {#build-system}

| Decision                 | Value                                                                                                                                                                                                                                                                 |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Build tool               | Gradle, **Kotlin DSL** (`*.gradle.kts`)                                                                                                                                                                                                                               |
| Wrapper                  | committed (`gradlew`, `gradle/wrapper/`); pinned Gradle version + checksum                                                                                                                                                                                            |
| Toolchain                | Java **25** via `java.toolchain.languageVersion = 25` (contributors need no manual JDK)                                                                                                                                                                               |
| Module system            | JPMS — every subproject has `module-info.java` (`02_Architecture/02_MODULES_AND_LAYERING.md`)                                                                                                                                                                         |
| Data carriers            | records for carriers (no `@Data`/`@Value`); Lombok on services (`@RequiredArgsConstructor`/`@Slf4j`/`@Builder`) — DD-05, ADR-0014                                                                                                                                     |
| Lombok                   | Lombok is the **sole annotation processor**, and it runs **ahead of the Error Prone javac plugin** (not "before it in the annotation-processor path" — Error Prone is a compiler plugin, not a processor). See `#error-prone-lombok`. Spotless (Palantir) compatible. |
| Static analysis compiler | Error Prone + NullAway wired via the **`net.ltgt.errorprone` Gradle plugin** with `-XDcompilePolicy=simple`; NullAway configured for Lombok-generated code. See `#error-prone-lombok`.                                                                                |

## project-layout {#project-layout}

```
tranlator_app/
├─ settings.gradle.kts           // includes :api :util :document :llm :pipeline :persistence :ui :app + build-logic
├─ gradle/libs.versions.toml     // version catalog (single source of dependency versions)
├─ gradle.lockfile / *.lockfile  // committed dependency locks
├─ build-logic/                  // convention plugins (included build)
│   └─ src/main/kotlin/
│       ├─ bookloom.java-conventions.gradle.kts     // toolchain, JPMS, Lombok (sole APT), net.ltgt.errorprone + NullAway, Checkstyle, SpotBugs
│       ├─ bookloom.spotless-conventions.gradle.kts // Palantir format
│       ├─ bookloom.test-conventions.gradle.kts     // JUnit5, AssertJ, Mockito, JaCoCo
│       └─ bookloom.javafx-conventions.gradle.kts   // openjfx plugin, module opens (ui/app only)
├─ api/ util/ document/ llm/ pipeline/ persistence/ ui/ app/
└─ docs/
```

## version-catalog {#version-catalog}

All dependency coordinates and versions live in `gradle/libs.versions.toml` (`[versions]`, `[libraries]`, `[bundles]`,
`[plugins]`). No subproject hard-codes a version string; each references `libs.<alias>`. Exact pins and the policy are
in `05_Dependencies/02_DEPENDENCY_POLICY.md`.

## convention-plugins {#convention-plugins}

`build-logic` holds the shared configuration as precompiled convention plugins so each subproject's `build.gradle.kts`
is a few lines (apply the conventions + declare its dependencies). Cross-cutting concerns — toolchain, JPMS args, Error
Prone + NullAway, Checkstyle, SpotBugs, Spotless, test setup — are defined once. The JavaFX convention is applied
**only** to `:ui` and `:app`.

## error-prone-lombok {#error-prone-lombok}

Error Prone and NullAway are not annotation processors — Error Prone is a **javac plugin**, so the earlier
"annotation-processor ordering" framing is corrected here. The `bookloom.java-conventions` plugin wires the compiler
pipeline as:

- **Error Prone / NullAway via the `net.ltgt.errorprone` Gradle plugin.** It adds Error Prone as a javac plugin and
  NullAway as an Error Prone check, and sets **`-XDcompilePolicy=simple`** — the compile policy Error Prone requires so
  its plugin sees a single flat compilation over each file.
- **Lombok is the sole annotation processor.** It is the only entry on `annotationProcessor`, and it desugars its
  annotations (`@RequiredArgsConstructor`/`@Slf4j`/`@Builder`) **before** the Error Prone javac plugin inspects the
  tree, so Error Prone/NullAway analyze the generated members, not the Lombok annotations. There is no second processor
  to order against Lombok.
- **NullAway over Lombok-generated code.** NullAway is told about Lombok either by setting **
  `lombok.addNullAnnotations`** (in `lombok.config`) so generated members carry the right nullability annotations,
  **or** by treating Lombok-generated code as excluded/handled (NullAway's generated-code handling) so
  builder/constructor code Lombok emits does not raise false `@Nullable`/non-null violations. `@NullMarked` (JSpecify)
  remains per-package (`02_QUALITY_GATES.md#null-safety`).

## javafx-plugin {#javafx-plugin}

`org.openjfx.javafxplugin` (variant-aware) declares the JavaFX 25 modules (`controls`, `fxml`, `graphics`) for `:ui`/
`:app` and resolves the correct per-OS artifacts. Packaging uses **no Gradle plugin**: `./gradlew :app:collectDist` (a
plain `Sync` task) stages the app jar + `runtimeClasspath` into `app/build/dist/libs/`, and the committed
`scripts/package-<os>` scripts drive the `jpackage` CLI from there — see `03_PACKAGING_JPACKAGE.md#approach` (DD-24).

## version-injection {#version-injection}

The **git tag is the single source of truth for the app version** — there is no hand-maintained version constant
anywhere (no VERSION file, no hard-coded string in code). Java has no link-time injection like Go's `-ldflags -X`, so
the equivalent is a **build-generated classpath resource** (DD-50):

- **Gradle property, default `dev`.** The root build sets
  `version = providers.gradleProperty("appVersion").orElse("dev")`. Local builds, IDE runs, and CI snapshot builds pass
  nothing and get **`dev`**; only the release pipeline passes `-PappVersion=<full-version>` derived from the tag.
- **Generated resource.** A `generateVersionResource` task in `:app` (wired into `processResources`, with the version
  declared as a task input so up-to-date checks work) writes `ua/bookloom/app/version.properties` containing
  `version=<value>`. Because the value rides the classpath, it is present identically in `gradlew run`, tests, and the
  jpackaged image — unlike the jar-manifest `Implementation-Version`, which is null when running from classes.
- **One reader, two surfaces.** A tiny `AppVersion` reader in `:app` loads the resource (falling back to `dev`) and
  surfaces it in exactly **two places**: the About dialog (`01_Product/08_UI_SCREENS_AND_STATES.md#dialog-about`) and a
  **single startup log line** (`app started version=<v>`); nothing else formats or stores a second copy.
- **OS package metadata.** The packaging scripts pass the **numeric** version to `jpackage --app-version` (`APP_VERSION`
  env — jpackage rejects pre-release suffixes), which stamps the macOS `Info.plist`, Windows file properties, and `.deb`
  version — the analogue of go_text's `wails.json` patch. The **full** version (including a `-rc1`-style suffix) goes
  into the resource/About and the artifact filenames.
- **`dev` proves non-release.** Any build without the injection reports `dev` in About and the startup log — deliberate:
  seeing `dev` proves the artifact did not come from the release pipeline (and the dev/prod path separation of DD-39 is
  decided independently by the build stamp, not by this version string).
- **Verified at release.** The packaging-leg launch smoke asserts the startup log reports the tag version — a mismatch
  fails the leg (EC-REL-7, `03_PACKAGING_JPACKAGE.md#release-edge-cases`).

## dependency-locking {#dependency-locking}

Gradle dependency locking is enabled and lockfiles are committed, so resolution is reproducible across machines and CI.
Lock updates are deliberate (`--write-locks`) and reviewed; Renovate proposes catalog bumps
(`05_Dependencies/02_DEPENDENCY_POLICY.md`).

**Per-platform lock state for JavaFX.** JavaFX artifacts are **classified per OS** (`win`/`mac`/`mac-aarch64`/`linux`
variants resolved by the `org.openjfx.javafxplugin`), so a **single committed lockfile is not reproducible across
platforms** — each OS resolves a different classified artifact. The build therefore uses **per-platform lock state**:
either commit **platform-specific lockfiles** (one per resolved OS, each written on/for that OS) **or exclude the JavaFX
variant-classified configurations from locking** and rely on the exact catalog pin for those artifacts. The
single-committed-lock reproducibility claim applies only to the platform-neutral configurations; it MUST NOT be asserted
over the platform-classified JavaFX configs.

## task-surface {#task-surface}

`./gradlew` tasks the workflow relies on:

| Task                   | Purpose                                                                                                                                                                                                                                                                                                                                                                                                             |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `build`                | compile + test + all verification for touched code                                                                                                                                                                                                                                                                                                                                                                  |
| `test`                 | unit tests (UI/TestFX excluded from the fast set)                                                                                                                                                                                                                                                                                                                                                                   |
| `spotlessApply`        | apply Palantir formatting (pre-commit hook)                                                                                                                                                                                                                                                                                                                                                                         |
| `spotlessCheck`        | verify formatting (CI)                                                                                                                                                                                                                                                                                                                                                                                              |
| `check`                | run all verification tasks (lint, ArchUnit, SpotBugs, tests, coverage)                                                                                                                                                                                                                                                                                                                                              |
| `archTest`             | ArchUnit boundary tests                                                                                                                                                                                                                                                                                                                                                                                             |
| `trace`                | regenerate `docs/traceability.yaml` — a **JavaParser** pass over the test source sets maps each `// Proves:` marker to its enclosing test-method FQN, cross-referenced against a **parsed FR-ID index** built from the spec requirement tables and the module inventory; writes a **canonical fingerprint** over the sorted `(story, AC, test)` tuples (`docs/implementation_plan/03_TRACEABILITY.md#gradle-tasks`) |
| `traceCheck`           | validate traceability (per-story link checks, **phase-exit** orphan-clause check, fresh fingerprint) — see `docs/implementation_plan/03_TRACEABILITY.md#check-table`                                                                                                                                                                                                                                                |
| `:app:collectDist`     | stage the app jar + runtime classpath into `app/build/dist/libs/` — the input for the packaging scripts (`03_PACKAGING_JPACKAGE.md#approach`)                                                                                                                                                                                                                                                                       |
| `scripts/package-<os>` | (not Gradle) drive `jpackage` per OS: `.app`+`.dmg` / portable zip / tar.gz+`.deb`                                                                                                                                                                                                                                                                                                                                  |

`trace`/`traceCheck` are provided as Gradle tasks; until the scaffold story implements them they are maintained by
review against the traceability schema. The generator (JavaParser over test sources, the parsed FR-ID index, and the
canonical `(story,AC,test)` fingerprint) and the check semantics (including the phase-exit orphan-clause rule and the
phase→clause manifest) are normative in `docs/implementation_plan/03_TRACEABILITY.md`.
