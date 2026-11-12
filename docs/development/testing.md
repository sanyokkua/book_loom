# Testing

*Test tags and local-only sets, conventions, the soak run, test speed and the focused loop.*

## 7. Testing {#testing}

| Command | Runs |
|---|---|
| `./gradlew test` | every test except the four local-only tags |
| `./gradlew :document:test` | one module |
| `./gradlew :document:test --tests 'ua.bookloom.document.mask.*'` | one package or class (pattern) |
| `./gradlew :document:test --tests '*Fb2WriterTest.write_lineFeedSource_keepsBareLineFeeds'` | one method |
| `./gradlew :app:archTest` | the nine ArchUnit rules (also part of `check`) |
| `./gradlew :ui:fastTest` | one module's tests without the `slow`-tagged end-to-end classes (not part of `check`) |
| `scripts/test-focused.sh` | the inner loop: format and test what the working tree changed ([below](#test-speed)) |
| `python3 scripts/slowest-tests.py [module...]` | where the last run's test time went, from the XML reports |
| `python3 -I scripts/model-knowledge-check.py <research-data-dir>` | the bundled quant and language knowledge against the research CSVs (no network; exit 1 with the differing rows) |

**Headless UI.** Every `Test` task gets `-Dglass.platform=Headless -Dprism.order=sw -Djava.awt.headless=true` from
`bookloom.test-conventions`: JavaFX 26's built-in headless platform, not Monocle. No display server is needed.

**Parallel forks.** A module's `test` and `fastTest` spread their classes over `min(4, cores / 2)` JVMs
(`-Pbookloom.forks=N` overrides it; `1` is the old serial run). A test therefore shares no file, port or log with
another class outside its own `@TempDir`: WireMock takes a dynamic port, and the test log is one file per fork. `liveLocal`, `promptEval`, `visual`, `corpus` and `archTest` keep one JVM.

**`slow` and `fastTest`.** `@Tag("slow")` marks the heavy end-to-end classes — a whole book through the engine
(`WholeBookPipelineEndToEndTest`, `TranslationEngineEndToEndTest`), the three-mode workspace run
(`TranslationWorkspaceEndToEndTest`) — and is the place for any class that grows past about 30 s in one fork. `test`,
`check`, pre-push and CI run them; `fastTest` leaves them out.

**Local-only sets** — tag and task share a name; none is part of `check` or CI; a task with no matching test is
green (`failOnNoDiscoveredTests = false`):

```bash
BOOKLOOM_CORPUS_DIR=/path/to/books ./gradlew :document:corpus      # the 235-book sweep (never up-to-date)
BOOKLOOM_CORPUS_REPORT_DIR=/path ...                                 # optional: where the JSONL/TSV report lands
BOOKLOOM_LIVE_OLLAMA_URL=http://localhost:11434 \
  BOOKLOOM_LIVE_OLLAMA_MODEL=<model-id> ./gradlew :llm:liveLocal :app:liveLocal
BOOKLOOM_LIVE_LMSTUDIO_URL=http://localhost:1234/v1 \
  BOOKLOOM_LIVE_LMSTUDIO_MODEL=<model-id> ./gradlew :llm:liveLocal :app:liveLocal
./gradlew :pipeline:soak                                              # a night's run under faults, ~2.5 min
BOOKLOOM_EVAL_OLLAMA_URL=http://localhost:11434 \
  BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx ./gradlew :pipeline:promptEval    # prompts on a real model (never up-to-date)
./gradlew visual                                                      # local-only set when its tests exist
```


**The soak run** (`@Tag("soak")`, `WholeBookSoakTest`) replays an overnight run in about a minute per book: a generated
Markdown and TXT book of 3,700 paragraphs (`SoakBooks`: chapter headings, recurring names, emphasis and links,
number- and symbol-only paragraphs, a few huge paragraphs) goes through the real `TranslationJobImpl`, export and
re-open, over `FaultyModel` — the pseudo model at the model port behind a seeded fault injector (timeouts, a call that
hangs until the stall watchdog ends it, 5xx bursts, an unreachable server, one 25-minute outage, an unloaded model,
empty replies, dropped/doubled/garbled `⟦gN⟧` tokens and stray brackets, refusals, judge junk, a hang on a re-judge, a
step that throws). The clock is scripted, so every recovery wait and watchdog ceiling passes at once. It asserts the run
completes with no segment pending, every fault class shows up as its pause, stall or flag, the export re-opens with every
segment, the retained heap stays under 400 MB and grows by less than 16 KB per segment, the live threads return to the
baseline +2, the run takes under 5 minutes and no ERROR line or stack trace appears that no injected fault explains; the
fixture book in all four formats then passes `scripts/validate-translated-book.py --lang none` (FB2's `<code>` is prose
by design D11 and is translated, which the validator accepts for FB2). The task logs at INFO; each run's numbers are one
`soak …` INFO line in `modules/pipeline/build/test-logs/`. Last run: 3,774 segments a book, ~65 s each, about 5,100
model calls, 2 h 35 min of scripted time, retained heap 21 → 32 MB (about 2.5 KB a segment, the in-memory store), threads
9 → 9. The same harness runs the four fixture formats in the gate as `FixtureBookSoakTest` (`slow`, under a second each),
and `TranslatingScreenSoakTest` (`slow`) feeds 3,700 decisions and 20,000 log lines through the real run session and
translating screen (worst FX-thread latency 16 ms, heap growth 1 MB).

The `liveLocal` provider suites use the four `BOOKLOOM_LIVE_OLLAMA_URL`, `BOOKLOOM_LIVE_OLLAMA_MODEL`,
`BOOKLOOM_LIVE_LMSTUDIO_URL`, and `BOOKLOOM_LIVE_LMSTUDIO_MODEL` variables; URL absence skips that provider's cases.
They are excluded from `check` and CI. Without `BOOKLOOM_CORPUS_DIR` the corpus test skips via a JUnit assumption; a
set-but-missing directory is a hard failure. The last recorded corpus run is in
`docs/spec_baseline/implementation_plan/notes-corpus-verification.md`.

**Conventions** (full rules in `.claude/rules/testing.md`):

- A test is named `method_state_expected`; when the name is not enough, one plain-language comment line above it
  says what it proves. There are no requirement-id markers and no coverage script (ADR-0032) — the test is the evidence.
- Write the acceptance test first and see it fail before the implementation exists.
- Mock only I/O and non-determinism, never the boundary the test exists to prove. The repository has no Mockito;
  document tests run on real bytes in `@TempDir`, and the LLM seam will be WireMock at the HTTP level.
- No `if`/`for`/`while` in a test body — use `@ParameterizedTest`. Never recompute the expected value with the
  production algorithm.
- A `:ui` test that shows a stage extends `FxTestBase`, not TestFX's `ApplicationTest`: the same lifecycle, but it
  waits for the FX thread and two real pulses instead of sleeping ([below](#test-speed)).


### Test speed and the focused loop {#test-speed}

The cadence: **while coding**, `scripts/test-focused.sh`; **once before each commit**, the changed module's full
`check` (`scripts/test-focused.sh --full-module`); **at the end of a feature or step group**, the whole gate
`./gradlew clean build check spotlessCheck`. Pre-push and CI run the whole gate on every push, unchanged.

`scripts/test-focused.sh [options] [module...]` reads `git diff --name-only HEAD` plus untracked files, formats the
changed modules (`spotlessApply`), runs in each changed module the test classes the diff points at (a changed
`*Test.java`, or `<Name>*Test` beside a changed `<Name>.java`; the module's `fastTest` when some change maps to no
test), and `fastTest` in every module that depends on a changed one (`api`, `util` → `document`, `llm`, `persistence`
→ `pipeline` → `ui` → `app`). A change under `modules/build-logic`, `gradle/` or a root build script counts as every
module. It prints the failed tests and the last 40 lines; the whole output is in `build/test-focused.log`.

| Option | Effect |
|---|---|
| `module...` | test these modules instead of the ones the diff names |
| `--tests <pattern>` | only this Gradle test pattern in the changed modules (repeatable) |
| `--fast` | `fastTest` for the changed modules instead of the classes the diff points at |
| `--full-module` | `check` (every test including `slow`, plus lint) for the changed modules |
| `--no-dependents` | skip the dependent modules |
| `--base <ref>` | diff against `<ref>` instead of `HEAD` |
| `--dry-run` | print the Gradle command only |
| `--lines <n>` | lines of output shown at the end (default 40) |

Measured on the owner's machine (Apple Silicon, 10 cores), 2026-10-01:

| What | Before | After |
|---|---|---|
| `./gradlew :ui:test` (6,870 tests, 146 classes) | 13 m 36 s — one JVM, 813 s of test time | 1 m 53 s – 2 m 3 s — 4 forks, ~346 s of test time, 111 s wall (three runs) |
| `./gradlew test --rerun` (every module) | — | 3 m 14 s before the `interact` change (three runs in a row); `:ui` is now ~35 s less |
| `./gradlew clean build check spotlessCheck` | 8 m 39 s (2026-09-28) | 4 m 13 s, 118 tasks |
| `./gradlew :pipeline:fastTest` / `:document:fastTest` / `:llm:fastTest` `--rerun` | — | 14 s / 5 s / 15 s |
| `scripts/test-focused.sh` for a `:ui`-only change | — | 11 s when the diff points at its tests (`ReviewViewModel*Test`); 1 m 54 s when it falls back to `:ui:fastTest` (a changed message bundle) |

Where the `:ui` time went: TestFX's `ApplicationTest` ends each of its four setup and cleanup steps with
`waitForFxEvents()` — five round trips to the FX thread with a 10 ms sleep after each — about 0.25 s of sleeping per
test, profiled with JFR at 74 s of the 88 s `ScreenConformanceTest` took; `FxRobot.interact` sleeps the same way after
every action, half of what remained in the screen suites. `FxTestBase` keeps the lifecycle and `interact` and waits for
the FX thread, the events it queued and two real pulses instead; `interactAtTestFxPace` keeps TestFX's own timing for
the one test that measures a hand's wheel speed (`SmoothScrollTest`). The 222 direct `waitForFxEvents()` calls in
test bodies still sleep; replacing them is the next lever if `:ui` grows again. The conformance suites also reuse a shown screen
across consecutive cases that read it without changing it. Slowest classes before → after (one fork's time):

| Class | Before | After |
|---|---|---|
| `ScreenConformanceTest` (210) | 98.2 s | 24.3 s |
| `ContrastTest` (108) | 47.8 s | 18.1 s |
| `TranslatingViewModelRoutingTest` (55) | 29.9 s | 17.5 s |
| `ReviewPanelScreenTest` (21) | 23.6 s | 15.1 s |
| `TranslatingScreenTest` (39) | 20.9 s | 9.0 s |
| `ExportScreenTest` (23) | 18.7 s | 10.4 s |
| `SmoothScrollTest` (8) | 15.4 s | 13.8 s |

Isolation under forks: an "unreachable provider" test points at `ClosedPorts.endpoint(...)` (port 1, below every
ephemeral range) instead of binding and releasing a port, which another fork's WireMock could take in between; the
test log is one file per fork. Still open: two `:llm` WireMock tests (`GatedChatModelTest`,
`OpenAiCompatibleClientProbeTest`) each failed once in about fifteen full runs because a reply came from something
other than their own server — the same load-only symptom `replan-notes.md` recorded before the forks; rerun the class.

Two switches stay off on purpose. `org.gradle.parallel` (modules side by side) was tried: the gate then failed
intermittently with a `NoClassDefFoundError` out of palantir-java-format in some modules' `spotlessJavaCheck` — Spotless
shares cached formatter class loaders across modules and closes them from `clean` — and the module-level overlap saved
seconds, not minutes, next to the forks. The build cache would let `clean build check` load test results from the cache
instead of running the tests, so the `clean` in the gate would stop meaning "run everything again".

`SmoothScrollTest` stays slow on purpose: it measures a real wheel glide over animation time. A run that finds a class
taking much longer than these is a regression worth `python3 scripts/slowest-tests.py` before anything else.

---
