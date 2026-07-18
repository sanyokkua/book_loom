---
phase: PHASE_00_SCAFFOLD
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#build-system
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#tools
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#coverage-gate
  - docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#verification
  - docs/specification/04_Build_and_Release/04_CI_CD.md#quality-job
  - docs/specification/05_Dependencies/03_LICENSING.md#license-gate-tool
  - docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#dependency-direction
  - docs/specification/02_Architecture/09_ERROR_HANDLING.md#result-envelope
  - docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#composition-root
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#resolution-mechanism
  - docs/implementation_plan/03_TRACEABILITY.md#gradle-tasks
  - DD-03
  - DD-06
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`,
`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
`docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`, `docs/specification/04_Build_and_Release/04_CI_CD.md`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md`

# PHASE_00 — Scaffold

## Goal

Stand up the buildable, enforceable project skeleton: the eight-module Gradle/JPMS structure, the Guice composition
root, logging, git hooks, formatter and linters, ArchUnit boundary enforcement, CI, a jpackage smoke build, and the
traceability tooling. When this phase closes, an empty-but-correct application launches, `./gradlew build` and
`./gradlew check` are green, and the two cross-cutting seams (F2 Result/AppError, F9 offline) are in place and enforced.

## In scope

- Gradle (Kotlin DSL) multi-project with wrapper, `build-logic/` convention plugins, `gradle/libs.versions.toml` version
  catalog, committed dependency locking.
- The eight subprojects `:api :util :document :llm :pipeline :persistence :ui :app` as JPMS modules
  `ua.bookloom.<module>` with `module-info.java` and the allowed dependency edges only.
- `:api` foundation types: `Result<T>`, `AppError`, `ErrorCode`, safe-details allowlist, partial-result support (seam
  F2).
- **App-environment/paths resolver in `:util` (FIRST — before logging and persistence):** per-OS data + log dir
  resolution with injected env/property seams, dev/prod `-Dev` separation via the `isDev` signal, first-run directory
  creation, and the startup order (resolve → create → lock → logging → SQLite). Per
  `02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md` (DD-39, ADR-0015).
- `:app` `Launcher` + `Application` subclass building the Guice injector, single composition root, two-phase init,
  single-instance lock (`FileChannel.tryLock` on the resolved data dir).
- SLF4J + Logback rolling file to the **resolved** per-OS log dir (published before the first logger); parameterized
  logging; MDC cleared in `finally`.
- Spotless + Palantir Java Format (120-col); Lombok as the **sole annotation processor**, running ahead of the Error
  Prone **javac plugin** (not "before it in the processor path" — Error Prone is a compiler plugin; wired via
  `net.ltgt.errorprone` + `-XDcompilePolicy=simple`, with NullAway configured for Lombok-generated code; services only,
  DD-05/ADR-0014, `04_Build_and_Release/01_BUILD_AND_TOOLING.md#error-prone-lombok`); Error Prone + NullAway (JSpecify
  `@NullMarked`), Checkstyle, SpotBugs + FindSecBugs; ArchUnit rules in a shared `arch-test` source set.
- Test conventions: JUnit 5 + AssertJ + Mockito + WireMock + TestFX/Monocle; a `liveLocal`-tagged test set wired but
  excluded from `check`/CI (`04_Build_and_Release/06_TESTING_STRATEGY.md`).
- Lefthook hooks: pre-commit (`spotlessApply`, gitleaks, file-size guard), commit-msg (Conventional Commits), pre-push
  (unit tests excl. UI).
- CI (GitHub Actions) running `check`, ArchUnit, per-module branch coverage (production-code modules only), the license
  gate (`checkLicense`, distinct from OWASP SCA), and SCA; a packaging smoke — `./gradlew :app:collectDist` + the per-OS
  script building an app-image that is launched headlessly (Xvfb/Monocle) and must actually start (DD-24).
- **Version injection plumbing (DD-50):** root `version` from `-PappVersion` (default `dev`), the
  `generateVersionResource` task writing the `version.properties` classpath resource in `:app`, the `AppVersion` reader,
  and the single startup log line — so the About dialog (later phases) and the packaging smoke read one injected value.
- `./gradlew trace` and `./gradlew traceCheck` tasks per `docs/implementation_plan/03_TRACEABILITY.md`.

## Out of scope

- Any document parsing, persistence schema, provider, pipeline, or UI screen logic (later phases).
- Real single-instance lock semantics beyond a wiring stub (hardened in PHASE_04).
- The full five-leg release packaging matrix and GitHub Releases (PHASE_13) — this phase only proves a single-OS
  app-image smoke.

## Dependencies

None. This is the first phase.

## Forward-compatibility

- **Establishes F2** — the `Result{data,error}` envelope, typed `AppError`/`ErrorCode`, and safe-details allowlist in
  `:api`, used by the first service onward.
- **Establishes F9** — the offline invariant as a structural guarantee: ArchUnit `no-http-in-core-except-llm` and
  WireMock-isolation conventions exist from the start.
- Creates the module boundaries every later seam (F1, F3–F8) will attach to.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                                                                                                                       | Target modules                                      | Cited spec clauses                                                                                                                                             |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Gradle Kotlin-DSL multi-project + wrapper + `build-logic` convention plugins + version catalog + dependency locking                                                                                                                                  | `:app/ua.bookloom.app` (settings/build)             | `04_Build_and_Release/01_BUILD_AND_TOOLING.md#build-and-tooling`, DD-03                                                                                        |
| Define the eight JPMS modules with `module-info.java` and the allowed dependency edges only                                                                                                                                                          | all modules                                         | `02_Architecture/02_MODULES_AND_LAYERING.md#dependency-direction`, DD-06                                                                                       |
| Implement `Result<T>`, `AppError`, `ErrorCode`, safe-details allowlist, partial results in `:api`                                                                                                                                                    | `:api/ua.bookloom.api`                              | `02_Architecture/09_ERROR_HANDLING.md#result-envelope`, DD-14, FR-INFER-07, FR-NOTIF-04                                                                        |
| **App-paths resolver** (per-OS data/log dirs, injected env/property seams, dev/prod `-Dev` `isDev` signal, first-run creation) — precedes logging + persistence                                                                                      | `:util/ua.bookloom.util.paths`                      | `02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#resolution-mechanism`, DD-39, ADR-0015, FR-PERSIST-03, FR-PERSIST-06                                          |
| `Launcher` + `Application` + Guice composition root + two-phase init (resolve paths → create dirs → lock → logging → SQLite)                                                                                                                         | `:app/ua.bookloom.app`                              | `02_Architecture/10_DI_AND_LIFECYCLE.md#composition-root`, `11_APP_ENVIRONMENT_AND_PATHS.md#startup-order`, DD-04, DD-21, DD-39                                |
| SLF4J + Logback rolling file to the resolved per-OS log dir (published before first logger); MDC discipline; never log secrets                                                                                                                       | `:app/ua.bookloom.app`, `:util/ua.bookloom.util.io` | DD-23, DD-39, `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#privacy`                                                                                             |
| Spotless + Palantir; Lombok (sole APT, ahead of the Error Prone javac plugin; `net.ltgt.errorprone` + `-XDcompilePolicy=simple`; NullAway over Lombok-generated code); Error Prone + NullAway; Checkstyle; SpotBugs + FindSecBugs wired into `check` | `build-logic`                                       | DD-25, DD-05, ADR-0014, `04_Build_and_Release/01_BUILD_AND_TOOLING.md#error-prone-lombok`, `04_Build_and_Release/02_QUALITY_GATES.md#quality-gates`            |
| ArchUnit rules in `arch-test` source set (fx-free-core, dependency-direction, ports-not-concretes, no-http-except-llm, no-sql-except-persistence, api-framework-free, records-first)                                                                 | `arch-test` across modules                          | `02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules`, DD-06                                                                                             |
| Lefthook pre-commit / commit-msg / pre-push hooks                                                                                                                                                                                                    | `tooling`                                           | DD-25, `04_Build_and_Release/02_QUALITY_GATES.md#git-hooks`                                                                                                    |
| GitHub Actions CI: `check`, ArchUnit, coverage, license gate (`checkLicense`, distinct from OWASP SCA), SCA, jpackage launch-or-fail smoke                                                                                                           | `ci`                                                | `04_Build_and_Release/04_CI_CD.md#quality-job`, `05_Dependencies/03_LICENSING.md#license-gate-tool`, `04_Build_and_Release/02_QUALITY_GATES.md#jpackage-smoke` |
| Packaging smoke: `:app:collectDist` + the committed per-OS script build an app-image on the current OS, launched headlessly (Xvfb/Monocle) and asserted to actually start                                                                            | `:app/ua.bookloom.app`, `tooling`, `ci`             | `04_Build_and_Release/03_PACKAGING_JPACKAGE.md#approach`, `#verification`, DD-24                                                                               |
| Version injection: `-PappVersion` property (default `dev`) + `generateVersionResource` (into `processResources`, declared inputs) + `AppVersion` reader + startup log line                                                                           | `:app/ua.bookloom.app`, `build-logic`               | `04_Build_and_Release/01_BUILD_AND_TOOLING.md#version-injection`, DD-50, FR-UI-09                                                                              |
| `./gradlew trace` (JavaParser over test sources → `Proves:`→FQN; parsed FR-ID index; canonical `(story,AC,test)` fingerprint) + `./gradlew traceCheck` (validate; phase-exit orphan check)                                                           | `build-logic`                                       | `docs/implementation_plan/03_TRACEABILITY.md#gradle-tasks`                                                                                                     |
| ArchUnit + WireMock-isolation test proving the only outbound network call is **user-triggered provider communication (inference, model discovery, verification)** — no background/unsolicited traffic (offline invariant seed)                       | `arch-test`, `:llm/ua.bookloom.llm`                 | DD-01, FR-INFER-01, `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#offline`                                                                                       |

## Phase exit checklist

- [ ] All eight modules compile; `module-info.java` declares only the allowed edges; `./gradlew build` green.
- [ ] `Result`/`AppError`/`ErrorCode` exist in `:api` with a safe-details allowlist and are unit-tested (F2
  established).
- [ ] The app launches an empty shell via the Guice composition root and exits cleanly.
- [ ] The app-paths resolver returns the correct data/log dirs for Windows/macOS/Linux (unit-tested via the env/property
  seams) and a dev build resolves to the `-Dev` sibling; resolution runs before logging and SQLite.
- [ ] Logging writes to the resolved per-OS log dir; no secret is ever logged (test asserts redaction path).
- [ ] `./gradlew check` runs Spotless, Checkstyle, Error Prone + NullAway, SpotBugs + FindSecBugs, and all seven
  ArchUnit rules green.
- [ ] Lefthook hooks installed and firing (pre-commit < 10s, pre-push < 60s).
- [ ] CI pipeline green on a clean checkout; the license gate (`checkLicense`,
  `com.github.jk1.dependency-license-report`, distinct from OWASP SCA) passes against the allowlist (Apache-2.0/MIT/BSD
  plus the recorded EPL-1.0/ICU/JDOM exceptions; `05_Dependencies/03_LICENSING.md`).
- [ ] The packaging smoke (`:app:collectDist` + script) produces a launchable app-image on at least one OS.
- [ ] `./gradlew trace` and `./gradlew traceCheck` run and pass on the seed stories.
- [ ] An ArchUnit/WireMock test enforces the offline invariant (F9 established).
- [ ] Module inventory (`docs/implementation_plan/01_MODULE_INVENTORY.md`) reflects every package created.
