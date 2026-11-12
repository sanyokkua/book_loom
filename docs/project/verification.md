# Verification

How BookLoom is checked, what counts as done, and how to read a red gate. The rules are in
[`AGENTS.md`](../../AGENTS.md) and `.claude/rules/testing.md`; the commands and tags are in
[development/testing.md](../development/testing.md). The CI side is in [ci-and-hooks.md](ci-and-hooks.md).

## Test taxonomy

All tests use JUnit 5 and AssertJ, with real collaborators or hand-written fakes. Mockito is on the classpath and
unused. Test names follow `method_state_expected`. A test method has no `if`, `for` or `while`; use parameterised
tests. A test proves our code, not Ollama or LM Studio.

| Tier | What it proves | Where | In `check` and CI |
|---|---|---|---|
| Unit | pure logic of a class | `modules/*/src/test` | yes |
| Persistence contract | one abstract `RepositoryContractTest` runs against the in-memory adapters and the SQLite adapters (real temp file, schema applied) | `modules/persistence/src/test/java/ua/bookloom/persistence/{contract,memory,sqlite}/` | yes |
| Provider tests at the HTTP seam | request shaping, response parsing, retry, `Retry-After`, HTTP to `ErrorCode` mapping, for both the OpenAI-compatible (`/v1/*`) and the Ollama-native (`/api/*`) dialect, using WireMock; engine detection runs on recorded reply bodies in `modules/llm/src/test/resources/ua/bookloom/llm/engine/` including look-alike servers | `modules/llm/src/test`, plus WireMock-based tests in `:pipeline` | yes |
| Document golden round trip | a no-op reassembly equals the source, compared canonically (re-parsed or canonical XML; EPUB entry order, `mimetype` first and stored; TXT exact bytes), per format; unmask and the placeholder hard gate | `modules/document/src/test/java/ua/bookloom/document/golden/` and the unmask tests beside it | yes |
| Pipeline end to end | a small whole book through the engine against a stub or WireMock provider, accepted and flagged outcomes, same-format export, restart and continue | `modules/pipeline/src/test` (`WholeBookPipelineEndToEndTest`, `TranslationEngineEndToEndTest`, `RestartContinueE2eTest`, `OneRepairE2eTest`), `modules/app/src/test` (`AllCallsEndToEndTest`) | yes; the heavy ones are tagged `slow` |
| UI widget and screen/state | control state and every enumerated screen state, run with TestFX on JavaFX 26's built-in headless platform (`glass.platform=Headless`, ADR-0019). "Wired" is checked behaviourally: drive the view model and assert the node, fire the control and assert the command through a recording fake | `modules/ui/src/test` | yes |
| UI matches mockup | structural and token assertions per screen, a screen and state coverage checklist | `ScreenConformanceTest`, `ScreenConformanceCoverageTest` and the `*ConformanceCases` classes in `modules/ui/src/test/java/ua/bookloom/ui/` | yes |
| Layout fit | text fit measured with the platform font | `LayoutFitTest` and the `*FitTest` classes; run on Ubuntu with `scripts/ci-linux-ui.sh` | yes (fails on Ubuntu first) |
| i18n | English and Ukrainian bundles have identical keys, valid ICU patterns, Ukrainian plural forms, every registered key defined, first-launch locale choice, persisted language switch | `modules/ui/src/test/java/ua/bookloom/ui/i18n/` | yes |
| Architecture (ArchUnit) | module edges, FX-free core, ports not concretes, no HTTP outside `:llm`, no SQL outside `:persistence`, `:api` framework-free, records first, no static logger on the bootstrap path, no inline style in `:ui`, export makes no model call | `modules/app/src/archTest/`, task `:app:archTest` | yes (`check` depends on it) |
| Smoke | the app boots (injector and two-phase init against a temp SQLite file) | `modules/app/src/test/java/ua/bookloom/app/AppBootSmokeTest.java` | yes |
| Packaged-image smoke | the jpackage image starts on the module path | `scripts/launch-smoke.sh` | in the CI packaging job, not in `check` |
| `slow` | heavy end-to-end classes (38 classes carry the tag today) | everywhere | in `test`, `check` and CI; left out of `fastTest` |

### Tagged local-only sets

Each has a Gradle task with the same name as its tag. None is part of `check` or CI, none counts towards coverage,
and a task with no matching test is green. Counts of classes carrying the tag come from `grep` on the current tree.

| Tag and task | Needs | Use | Classes |
|---|---|---|---|
| `liveLocal` | real Ollama, LM Studio, llama-server or Unsloth endpoints; each test reads its URL from `BOOKLOOM_LIVE_*_URL` and skips when unset | the engine detection matrix and real request and response shapes (a wrong engine there falsifies ADR-0047) | 9 |
| `promptEval` | a served local model | prompt evaluation; see [evaluation/eval-suites.md](../evaluation/eval-suites.md); never up to date | 15 |
| `visual` | a pinned rendering environment | snapshot diffs | 0 (the task exists, no class carries the tag) |
| `corpus` | `BOOKLOOM_CORPUS_DIR` pointing at the owner's books; unset skips, set-but-missing fails; never up to date | `./gradlew :document:corpus`, the round-trip sweep over the local corpus (235 books at the last recorded sweep) | 2 |
| `soak` | nothing external | `./gradlew :pipeline:soak`, a whole generated book under injected faults on a scripted clock | 1 |

### Test infrastructure

Set in `bookloom.test-conventions`: `test` and `fastTest` fork over `min(4, cores / 2)` JVMs (`-Pbookloom.forks=N`
overrides); headless JavaFX with the software renderer (`prism.order=sw`); `bookloom.reduceMotion=true`; storage access
on the FX thread throws in tests (`bookloom.storage.failOnFxThread`). A test shares no file, port or log with another
class outside its own `@TempDir`.

## Coverage

JaCoCo branch coverage must be at least 0.80 per module for `:api`, `:util`, `:document`, `:llm`, `:pipeline` and
`:persistence`; `jacocoTestCoverageVerification` is wired into `check`. `:ui` and `:app` do not apply the coverage
plugin and are covered behaviourally. Reports: `modules/<m>/build/reports/jacoco/test/html/index.html`. Local-only
tags are excluded.

## Definition of Done

From [`AGENTS.md`](../../AGENTS.md#definition-of-done):

1. The gate is green **and** the change was exercised in the running app, or by the one test that reproduces the
   user-visible behaviour. Evidence is the tail of the gate output, not "it passed".
2. Cadence: while coding run `scripts/test-focused.sh`; once before each commit run `scripts/test-focused.sh --full-module`;
   at the end of a feature or step group run `./gradlew clean build check spotlessCheck`. Pre-push and CI run the full
   gate on every push.
3. A red check anywhere is fixed before the work is called done.
4. After the gate at the end of a change group, `./gradlew sonar -PsonarLocal` must show 0 open issues and the quality
   gate OK (opt-in, needs the local server).
5. A run much longer than the last baseline is hung: kill it and diagnose. Never run two gates at once.
6. Before pushing a change to a screen, dialog or UI word: `scripts/ci-linux-ui.sh`.

A change is small: a brief, an acceptance test seen red before the implementation, then green, then the app run by
hand. See [ai-agent-setup.md](ai-agent-setup.md#openspec-workflow).

## Headless proof from the command line

`scripts/e2e-fixture.sh <epub|fb2|md|txt|all>` translates the generated fixture book through
`./gradlew :app:translate` exactly as a window run would (brief, names review, outage recovery), then runs
`scripts/validate-translated-book.py` on each output and prints one summary line per format. It needs a local model
already served and makes real model calls. It is the proof tool for a change that touches the pipeline end to end when
no window is available. Details: [development/running.md](../development/running.md). The fixtures are regenerated
with `scripts/make-fixtures.py`, and `validate-translated-book.py --self-test` proves the committed fixtures match it.

## Reading a gate failure

Run the failing module on its own first: `./gradlew :<module>:check`, or `scripts/test-focused.sh --tests '<Class>'`.

| Symptom | Where to look |
|---|---|
| Spotless failure | `./gradlew spotlessApply`, then re-run |
| Checkstyle, SpotBugs | `modules/<m>/build/reports/checkstyle/` and `.../spotbugs/` (HTML and XML) |
| Compile error from Error Prone, NullAway or `-Xlint ... -Werror` | the javac output names the check; every package is `@NullMarked` |
| Test failure | `modules/<m>/build/reports/tests/test/index.html` and `build/test-results/`; the console shows the full stack trace for failures only. In CI the same folders are uploaded as `test-reports-<run id>` on failure |
| Branch coverage below 0.80 | `./gradlew :<m>:jacocoTestReport`, open the module HTML report |
| ArchUnit failure | `./gradlew :app:archTest`; the message names the rule and the class; rules live in `ArchitectureRules.java` |
| UI fit test fails only on CI | `scripts/ci-linux-ui.sh`; fix with `Wrapped.wholeLines`, `USE_PREF_SIZE` or `FlowPane` |
| Lock error | `verifyLocks` failed: regenerate with `./gradlew resolveAndLockAll --write-locks`; for `modules/build-logic` use `./gradlew -p modules/build-logic resolveAndLockAll --write-locks` |
| A pin in `gradle/libs.versions.toml` seems to do nothing | a pin that no `constraints {}` block or `libs.` accessor references has no effect |
| Slow or hung run | `python3 scripts/slowest-tests.py` for where the time went |
| Same cause red twice | stop and report it (the project rule), do not retry blindly |
| Licence check failure (CI) | `build/reports/dependency-license/` holds the report; the allowlist is `config/license/allowed-licenses.json` |
