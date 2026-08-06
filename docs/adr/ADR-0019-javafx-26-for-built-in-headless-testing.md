# ADR-0019 — Adopt JavaFX 26 for its built-in headless platform

**Status:** accepted **Date:** 2026-08-02
**Deciders:** project owner
**Supersedes:** the JavaFX 25 pin in ADR-0001 (the Java 25 language/toolchain decision there stands unchanged)

## Context and problem statement

The frozen specification requires two things that cannot both be satisfied.

1. **JavaFX 25.** DD-02 and ADR-0001 pin JavaFX 25 (LTS), restated in `01_BUILD_AND_TOOLING.md#javafx-plugin`,
   `05_Dependencies/01_DEPENDENCIES.md#runtime-libraries` and `03_PACKAGING_JPACKAGE.md`.
2. **TestFX + Monocle for every headless UI test.** `06_TESTING_STRATEGY.md` names "TestFX + Monocle (headless)" for
   UI widget, UI screen/state and UI-matches-mockup conformance tests; `02_QUALITY_GATES.md#ci-gates` repeats it; and
   `02_QUALITY_GATES.md` line 84 additionally requires `-Dglass.platform=Monocle -Dmonocle.platform=Headless` for the
   **macOS and Windows packaging smoke**, where no virtual framebuffer is available.

`org.testfx:openjfx-monocle` has no release for JavaFX 22 or later. Its published versions on Maven Central end at
**21.0.2**; the upstream repository maintains only `jdk-8`, `jdk-11`, `jdk-17` and `jdk-21` branches. Monocle works by
patching `javafx.graphics` internals, so a JavaFX 21-era artifact against a JavaFX 25 runtime is a version mismatch,
not a supported configuration — running it is not a matter of accepting a warning.

This was discovered while implementing change `bootstrap-gradle-and-quality-toolchain`, whose tasks 5.1 and 5.5
require Monocle to be wired into the test conventions and proven with a canary test. The conflict is between two
frozen clauses, so per ADR-0016 the remedy is this ADR, not an edit to `docs/specification/**`.

## Decision drivers

- **The packaging smoke has no Xvfb escape.** On Linux a virtual framebuffer substitutes for Monocle, but
  `02_QUALITY_GATES.md` requires a *headless in-JVM* launch on macOS and Windows, where Xvfb does not exist. Any
  option that only fixes CI leaves change 27 with the same problem unsolved.
- **A UI-conformance suite must run against the version that ships.** The whole purpose of the
  UI-matches-mockup tests is visual fidelity, so testing on a different JavaFX version than the distributable
  undermines the gate it exists to be.
- **`./gradlew check` must be runnable locally**, including on a headless Linux workstation, without a display server.
- Keeping the LTS pin has real value — but only if the resulting configuration is actually testable.

## Considered options

- **A — Xvfb as the primary path, drop Monocle.**
- **B — Move the whole project to JavaFX 26.**
- **C — Compile and ship JavaFX 25, run tests on a JavaFX 26 test runtime.**

## Decision outcome

Chosen: **B — move the whole project to JavaFX 26** (`org.openjfx:javafx-*:26.0.2`), because it is the only option
that resolves both the TestFX problem and the macOS/Windows packaging-smoke problem with a single change, and the
only one under which tests exercise the same JavaFX version the user installs.

JavaFX 26 ships a first-class headless glass platform inside `javafx.graphics` on every platform, selected with
`-Dglass.platform=Headless`. No third-party artifact is involved, so the failure mode that produced this ADR —
an unmaintained shim lagging the toolkit — cannot recur.

### Consequences

- Positive: headless UI tests need no external dependency on any OS; the macOS/Windows packaging smoke required by
  `02_QUALITY_GATES.md` has a supported mechanism; tests and the distributable run identical JavaFX.
- Positive: `org.testfx:openjfx-monocle` never enters the dependency graph, so it cannot appear in the license
  report or the SCA scan.
- Negative: **JavaFX 26 is not an LTS release.** DD-02 chose 25 precisely for LTS support, and this trades that for
  a supported test story. Upgrades will follow the JavaFX release train rather than the LTS train.
- Negative: every frozen-spec mention of "JavaFX 25" and of Monocle is now superseded by this ADR and must be read
  through it. The spec files themselves stay unedited.
- Neutral: `-Dglass.platform=Monocle -Dmonocle.platform=Headless` becomes `-Dglass.platform=Headless` wherever the
  spec specifies it. Java remains 25 — ADR-0001's language and toolchain decision is untouched.
- Neutral: Xvfb stays in the CI job as `04_CI_CD.md#quality-job` step 2 describes it. It is now redundant for the
  TestFX suites rather than load-bearing, and is retained for the Linux packaging-smoke leg.

## Pros and cons of the options

### Option A — Xvfb primary, drop Monocle

- Good: keeps the JavaFX 25 LTS pin; matches what `04_CI_CD.md` step 2 already does, where Xvfb is primary and
  Monocle is described only as a "fallback where a display is not needed".
- Bad: leaves the macOS/Windows packaging smoke with no headless mechanism at all — Xvfb is Linux-only — so change 27
  would have to reopen this decision.
- Bad: a headless Linux workstation cannot run `./gradlew check` without installing and starting Xvfb.

### Option B — Move the whole project to JavaFX 26 (chosen)

- Good: one decision fixes headless testing, the packaging smoke, and local `check` on every OS.
- Good: no third-party headless shim to go stale again.
- Bad: gives up LTS, which was a deliberate DD-02 choice, not an accident.

### Option C — JavaFX 25 main, JavaFX 26 test runtime

- Good: preserves the LTS pin for the artifact users actually install.
- Bad: the UI-matches-mockup conformance suite would assert rendering against a JavaFX version that is never shipped —
  a fidelity gap in exactly the suite whose purpose is fidelity.
- Bad: does not help the packaging smoke, which launches the real distributable.

## Links

- Design decisions: DD-02 (Java 25 + JavaFX 25 — JavaFX half superseded here), DD-06 (FX-free core, unaffected)
- Superseded ADR: `docs/adr/ADR-0001-language-and-ui-toolkit.md` (JavaFX version only)
- Spec clauses read through this ADR: `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md`,
  `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`,
  `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`,
  `docs/specification/04_Build_and_Release/01_BUILD_AND_TOOLING.md#javafx-plugin`,
  `docs/specification/05_Dependencies/01_DEPENDENCIES.md#runtime-libraries`
- Changes: `bootstrap-gradle-and-quality-toolchain` (tasks 5.1, 5.5, 7.2), and change 27 (packaging) downstream
