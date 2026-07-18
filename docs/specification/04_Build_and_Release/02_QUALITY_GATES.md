**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md`,
`docs/specification/04_Build_and_Release/04_CI_CD.md`, `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md`,
`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/05_Dependencies/02_DEPENDENCY_POLICY.md`

# Quality Gates

Quality is enforced by tooling at three stages — local hooks (fast), CI (thorough), and code review — so defects are
caught as early as cheaply possible. This document fixes each tool, what it checks, and where a violation fails the
build.

## tools {#tools}

| Tool                                                          | Checks                                                                                                                                                                   | Config                                         |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------|
| **Spotless + Palantir Java Format**                           | formatting, import order, 120-col                                                                                                                                        | `spotlessApply` in hook, `spotlessCheck` in CI |
| **Error Prone + NullAway**                                    | correctness bug patterns; null-safety with JSpecify `@NullMarked` per package                                                                                            | compile-time; SECURITY/CORRECTNESS fail CI     |
| **Checkstyle**                                                | style/structure rules not covered by the formatter                                                                                                                       | CI (+ optional local)                          |
| **SpotBugs + FindSecBugs**                                    | bug patterns + security anti-patterns                                                                                                                                    | CI                                             |
| **ArchUnit**                                                  | module boundaries, FX-free core, ports-not-concretes, cycles                                                                                                             | `archTest`, CI + fast subset in pre-push       |
| **JaCoCo**                                                    | coverage — **exact branch threshold, applied per-module, only to modules with production code** (see `#coverage-gate`)                                                   | CI report + threshold                          |
| **License gate** (`com.github.jk1.dependency-license-report`) | every resolved runtime/bundled artifact's license is on the allowlist (`05_Dependencies/03_LICENSING.md#license-gate-tool`) — distinct from OWASP dependency-check (SCA) | CI (`checkLicense`)                            |
| **OWASP dependency-check**                                    | known-vulnerability SCA over the resolved graph                                                                                                                          | CI                                             |
| **gitleaks**                                                  | committed secrets                                                                                                                                                        | pre-commit + CI                                |
| **PIT** (optional)                                            | mutation testing on `document`/`pipeline`                                                                                                                                | opt-in                                         |

PMD is skipped (its coverage overlaps Error Prone/Checkstyle/SpotBugs).

## test-gate {#test-gate}

The full test taxonomy is normative in `06_TESTING_STRATEGY.md`; this gate fixes which types must be green to merge. The
following all run in **CI** (the merge gate) and a failure fails the job:

| Test type                     | Proves                                                                                                                                                                        | Tools                                        |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| Unit                          | class-level domain/service logic                                                                                                                                              | JUnit 5 + AssertJ + Mockito 5                |
| Persistence integration       | DAO + DDL over a real temp DB, Flyway applied                                                                                                                                 | temp SQLite / `:memory:` (no Testcontainers) |
| Provider integration          | request shape + response handling (`<think>`-strip, tolerant parse, repair, text fallback) for **both** the OpenAI-compatible (`/v1/*`) and Ollama-native (`/api/*`) dialects | WireMock (the only network seam)             |
| Document round-trip golden    | structure-and-text-preserving (canonical-equal) re-emit per format, original format only — canonicalized output compared to canonicalized source, not raw bytes (DD-43)       | JUnit 5 over fixtures                        |
| Pipeline e2e                  | a small whole book through the engine; accepted/flagged outcomes; same-format export                                                                                          | stub/WireMock provider                       |
| UI widget                     | control state + viewmodel binding                                                                                                                                             | TestFX + Monocle (headless)                  |
| UI screen/state               | each enumerated screen state/dialog                                                                                                                                           | TestFX + Monocle (headless)                  |
| UI-matches-mockup conformance | screens/controls/states/palette tokens present + wired vs the mockup                                                                                                          | TestFX + Monocle (headless)                  |
| i18n                          | `messages_en` + `messages_uk` complete (no missing keys), OS-locale first-start, `ui.language` DB switch                                                                      | JUnit 5 over bundles                         |
| Smoke                         | app boots (injector + two-phase init); jpackage image launches headlessly (mechanism + must-launch-or-fail per `#jpackage-smoke`)                                             | JUnit 5 (boot) + packaging matrix            |

**Local-only — NOT a CI gate:** the **`liveLocal`** set runs manually against a **real local Ollama and LM Studio** to
confirm real prompt/request/response structures + sanitization for both clients. It is **env-gated** (skipped when no
local endpoint is configured) and **excluded from `check` and CI** via a JUnit tag / separate source set
(`06_TESTING_STRATEGY.md#live-local`), so it never blocks a merge and never counts toward coverage or traceability. Add
a `liveLocal` case per provider-related feature.

## null-safety {#null-safety}

Each package is `@NullMarked` (JSpecify); NullAway then treats every unannotated reference as non-null and flags
nullable misuse at compile time. `@Nullable` is explicit where null is legal (e.g. `Result.data`/`Result.error`,
optional record fields). NullAway violations are CORRECTNESS and fail CI.

## coverage-gate {#coverage-gate}

JaCoCo enforces an **exact branch-coverage percentage** (not an approximate "~80%"): the threshold is **80% branch
coverage**, applied **per-module** via `jacocoTestCoverageVerification`. Two rules keep the whole-project-green
Definition of Done satisfiable during parallel and early phases:

- The threshold is applied **only to modules that contain production code** (`src/main/java` classes). An **empty or
  stub module** — one with no production classes yet, common while phases are built out in parallel — is **excluded**
  from the coverage rule (its verification rule has no classes to measure and cannot fail), so the clean-gate is
  satisfiable even before a module has code.
- `:ui`/`:app` JavaFX presentation code is excluded from the branch threshold as before (headless TestFX covers
  behaviour, not a branch percentage).

Because the gate is exact and per-module, a module either has production code and meets 80% branch, or has none and is
excluded — there is no global average that a code-complete module can hide behind, and no early module is forced to
fabricate coverage it cannot yet have.

## jpackage-smoke {#jpackage-smoke}

The jpackage-image smoke (`03_PACKAGING_JPACKAGE.md#verification`, `04_CI_CD.md#packaging-matrix`) does not merely build
the image — it **launches it headlessly and asserts the app actually started**:

- **Linux (`ubuntu-latest`):** run the packaged launcher under **Xvfb** (virtual framebuffer); the JavaFX Stage is
  created against the virtual display.
- **macOS / Windows and the in-JVM image smoke:** run the boot path with the **JavaFX Monocle** headless platform
  (`-Dglass.platform=Monocle -Dmonocle.platform=Headless`), the same mechanism the headless TestFX suite uses.

The smoke asserts a positive startup signal (injector built, two-phase init completed, primary Stage shown) within a
bounded timeout. **"Did not actually launch" is a hard failure, never a skip:** if no startup signal is observed the
smoke **fails the job** — it must not be reported as skipped/inconclusive. Absence of a display is handled by
Xvfb/Monocle, so there is no legitimate "no display, therefore skipped" path.

## lefthook-stages {#lefthook-stages}

Git hooks via **Lefthook**, budgeted so committing stays fast:

- **pre-commit (< 10 s):** `spotlessApply` with `stage_fixed` (auto-format and re-stage), `gitleaks`, a file-size guard.
  Fast, local, auto-fixing.
- **commit-msg:** Conventional Commits validation.
- **pre-push (< 60 s):** unit tests excluding UI/TestFX, plus the fast ArchUnit subset.

Heavy checks (full lint, TestFX headless, coverage, SCA) run in **CI only** to keep local loops fast.

## ci-gates {#ci-gates}

The CI quality job runs `spotlessCheck`, Error Prone + NullAway, Checkstyle, SpotBugs + FindSecBugs, all ArchUnit tests,
the full test suite — every type in `#test-gate` (unit, persistence integration, provider integration via WireMock for
both dialects, document golden round-trip, pipeline e2e, and the headless TestFX/Monocle UI
widget/screen-state/conformance, i18n, and boot-smoke tests) — the JaCoCo per-module branch threshold
(`#coverage-gate`), the license gate (`checkLicense`, `05_Dependencies/03_LICENSING.md#license-gate-tool`), OWASP
dependency-check, and `traceCheck`. The jpackage-image smoke runs in the packaging matrix and must launch-or-fail
(`#jpackage-smoke`). The `liveLocal` set is **excluded** (env-gated, local-only). See `04_CI_CD.md`.

## what-fails-where {#what-fails-where}

| Violation                                                                 | pre-commit         | pre-push       | CI                                                        |
|---------------------------------------------------------------------------|--------------------|----------------|-----------------------------------------------------------|
| Unformatted code                                                          | auto-fixed         | —              | fails (`spotlessCheck`)                                   |
| Committed secret                                                          | fails (gitleaks)   | —              | fails                                                     |
| Non-conventional commit msg                                               | fails (commit-msg) | —              | —                                                         |
| Failing unit test                                                         | —                  | fails          | fails                                                     |
| Failing persistence / provider / golden / pipeline-e2e test               | —                  | —              | fails                                                     |
| Failing UI/TestFX test (widget / screen-state / conformance)              | —                  | —              | fails                                                     |
| Missing i18n key (`messages_en`/`messages_uk`) or failed locale selection | —                  | —              | fails                                                     |
| Failing boot smoke; jpackage-image smoke (incl. "did not launch")         | —                  | —              | fails (quality job / packaging matrix)                    |
| ArchUnit boundary violation                                               | —                  | fails (subset) | fails (full)                                              |
| Error Prone / NullAway (SECURITY/CORRECTNESS)                             | —                  | —              | fails                                                     |
| SpotBugs/FindSecBugs high                                                 | —                  | —              | fails                                                     |
| Coverage below per-module branch threshold (production-code modules only) | —                  | —              | fails (`#coverage-gate`)                                  |
| Banned license (not on allowlist)                                         | —                  | —              | fails (`checkLicense`, `05_Dependencies/03_LICENSING.md`) |
| Vulnerable dep above severity threshold                                   | —                  | —              | fails (OWASP dependency-check, `02_DEPENDENCY_POLICY.md`) |
| Traceability orphan / stale record                                        | —                  | —              | fails (`traceCheck`)                                      |
| `liveLocal` provider test                                                 | —                  | —              | not run (local-only, env-gated)                           |

The Definition of Done (per story) requires the full CI-equivalent set green plus `traceCheck` with zero orphans before
merge. For a provider-related story, the DoD additionally expects a `liveLocal` case to have been added and exercised
locally against a real Ollama/LM Studio, even though it does not run in CI.
