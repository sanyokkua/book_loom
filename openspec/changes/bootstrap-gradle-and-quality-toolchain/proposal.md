# bootstrap-gradle-and-quality-toolchain

## Why

The repository contains ~40 specification files, 17 ADRs, and no code — there is no `settings.gradle.kts`, no
`build.gradle.kts`, no `src/`. Nothing can be written, compiled, formatted, linted, or tested until a build exists.

This is change 1 of Stage A (ADR-0017): the first half of the infrastructure that everything else stands on. It creates
the buildable skeleton and the mechanical gate, so that from the very first line of production code onward, every later
change compiles as a JPMS module under the full analysis pipeline and is held to
`./gradlew clean build check spotlessCheck` — the whole-project green gate that is an implicit requirement of every
change (`06_DEFINITION_OF_DONE.md#per-change-checklist`).

Doing the gate now rather than later matters because these checks are **retroactive**: ArchUnit rules, NullAway, and
records-first are cheap to satisfy on an empty tree and expensive to retrofit onto thousands of existing lines.

## What Changes

- **A Gradle (Kotlin DSL) multi-project** with the committed wrapper, `settings.gradle.kts` including the eight
  subprojects plus a `build-logic` included build.
- **Eight JPMS modules** — `:api :util :document :llm :pipeline :persistence :ui :app`, base package
  `ua.bookloom.<module>` — each with a `module-info.java` declaring only the allowed dependency edges from
  `02_MODULES_AND_LAYERING.md#dependency-direction`, and a placeholder public type so it compiles.
- **A version catalog** (`gradle/libs.versions.toml`) as the single source of every dependency coordinate, plus Gradle
  **dependency locking** with committed lockfiles.
- **Convention plugins in `build-logic`**: `bookloom.java-conventions` (JDK 25 toolchain, JPMS, Lombok as the sole
  annotation processor, Error Prone + NullAway with JSpecify `@NullMarked`, Checkstyle, SpotBugs + FindSecBugs),
  `bookloom.spotless-conventions` (Palantir Java Format, 120 columns), `bookloom.javafx-conventions` (applied to `:ui`
  and `:app` only), and `bookloom.test-conventions`.
- **The ArchUnit boundary suite** — a shared `arch-test` source set with all eight rules (`fx-free-core`,
  `dependency-direction`, `ports-not-concretes`, `no-http-in-core-except-llm`, `no-sql-in-core-except-persistence`,
  `api-is-framework-free`, `records-first`, `bootstrap-no-static-logger`) wired into `check`.
- **Test conventions and tag wiring** — JUnit 5 + AssertJ + Mockito 5 + WireMock + TestFX/Monocle; the default `Test`
  task excludes the `liveLocal`, `promptEval`, and `visual` tags, which get their own registered tasks outside `check`;
  the JaCoCo per-module branch-coverage gate.
- **Lefthook git hooks** — pre-commit (Spotless on staged files, gitleaks, file-size guard), commit-msg (Conventional
  Commits), pre-push (the project-wide clean gate).
- **The CI quality workflow** (`.github/workflows/ci.yml`) — the PR merge gate, running the clean gate headlessly under
  Xvfb, plus the license-report gate and the OWASP dependency-check SCA gate.
- **`.editorconfig` and `.gitattributes`** pinning LF line endings and whitespace so Spotless and Git agree.

**BREAKING:** none — there is nothing to break.

## Capabilities

**New Capabilities:** none.
**Modified Capabilities:** none.

This change sets `skip_specs: true` in `.openspec.yaml`. It produces no user-observable behaviour: nothing in it is a
thing a reader of the book-translation product can do, observe, or depend on. A build file, a lint rule, and an ArchUnit
constraint are all *how the system is built*, not *what the system does*. Inventing requirements such as "the system
SHALL fail the build on unformatted code" would put developer-tooling mechanics into a behaviour ledger whose job is to
answer "what does BookLoom do for its user" — and `openspec/specs/` is exactly that ledger (ADR-0016). The verification
that would otherwise live in a spec lives in `tasks.md` instead, where each task states its own check.

## Impact

- **Modules:** creates all eight (`:api :util :document :llm :pipeline :persistence :ui :app`) as empty compiling JPMS
  modules, plus the `build-logic` included build and the shared `arch-test` source set. No production logic in any of
  them.
- **Files added:** `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/**.lockfile`,
  `gradlew`/`gradlew.bat`/`gradle/wrapper/**`, `build-logic/**`, `<module>/build.gradle.kts` ×8,
  `<module>/src/main/java/module-info.java` ×8, `lefthook.yml`, `.github/workflows/ci.yml`, `.editorconfig`,
  `.gitattributes`, `config/checkstyle/**`, `config/spotbugs/**`, `config/license/allowed-licenses.json`.
- **Dependencies introduced:** build-time and test-time only — Palantir Java Format, Error Prone, NullAway, JSpecify,
  Lombok, Checkstyle, SpotBugs + FindSecBugs, ArchUnit, JUnit 5, AssertJ, Mockito 5, WireMock, TestFX + Monocle, JaCoCo,
  `com.github.jk1.dependency-license-report`, OWASP dependency-check, and the OpenJFX Gradle plugin. Every one must be
  Apache-2.0 / MIT / BSD or a recorded exception (EPL-1.0 for Logback, ICU, JDOM) —
  `05_Dependencies/03_LICENSING.md#license-gate-tool`.
- **Runtime behaviour:** none. `./gradlew :app:run` does not yet open a window — that is change 2
  (`bootstrap-app-launch-and-empty-window`).
- **Downstream:** every subsequent change depends on this one. Nothing in Stage B, B′, C, D, or E can start until it is
  archived.
- **ADRs:** ADR-0001 (Java 25 + JavaFX 25), ADR-0002 (Gradle Kotlin DSL), ADR-0014 (Lombok hybrid), ADR-0016 (this
  delivery process), ADR-0017 (this change is Stage A, change 1).
