# Developing BookLoom

*How to build, run, test, package and clean this repository. `AGENTS.md` is the operating manual for agents;
`docs/Architecture.md` describes what the code does and how to verify it.*

Sections: [1 Prerequisites](#prerequisites) · [2 Quick start](#first-build) · [3 Layout](#project-structure) ·
[4 Build configuration](#build-config) · [5 Run and debug](#running) · [6 IDE](#ide) · [7 Testing](#testing) ·
[8 Coverage](#coverage) · [9 Quality gate, hooks and CI](#quality-gate) · [10 Packaging](#packaging) ·
[11 Cleanup](#cleanup) · [12 Troubleshooting](#troubleshooting)

---

## 1. Prerequisites {#prerequisites}

**JDK 25, installed by you.** The build asks for a language version, not a vendor, and declares no toolchain
download repository, so Gradle cannot fetch one.

| OS | Install |
|---|---|
| macOS | `brew install --cask temurin@25`, or `sdk install java 25-tem` (SDKMAN) |
| Linux | `sdk install java 25-tem`, or your distribution's Temurin package |
| Windows | `winget install EclipseAdoptium.Temurin.25.JDK`, or <https://adoptium.net> |

```bash
./gradlew -q javaToolchains    # what Gradle can see — the check that counts; java -version does not
```

**Gradle** comes from the committed wrapper (9.6.1, pinned checksum). Always `./gradlew`; never a system `gradle`.
On Windows use `gradlew.bat` from `cmd`/PowerShell, or `./gradlew` from Git Bash.

**Windows also needs a POSIX shell and Python 3**: the git hooks run with `runner: sh`, the packaging and smoke
scripts are bash, and `scripts/sync-agent-files.py` is Python. Install Git Bash (bundled with Git for Windows) or
WSL. Line endings are handled by `.gitattributes`; do not change `core.autocrlf` to work around a formatting failure.

Optional:

| Tool | Needed for | Install |
|---|---|---|
| `lefthook` | git hooks (inactive until installed per clone) | `brew install lefthook` / `scoop install lefthook` |
| `gitleaks` | the pre-commit secret scan; the hook fails without it | `brew install gitleaks` / `scoop install gitleaks` |
| `fakeroot` | the Linux `.deb`; without it `package-linux.sh` warns and skips it | `apt install fakeroot` |
| Liberica 25 "Full" (`jdk+fx`) | packaging on **Windows** and **Linux aarch64** only — a plain JDK there lacks the JavaFX jmods `jpackage` needs | <https://bell-sw.com/pages/downloads/> |

There is no `gradle.properties`; one you add is not ignored by git. `org.gradle.parallel` and the build cache stay off on
purpose ([§7](#test-speed)).

---

## 2. Quick start {#first-build}

```bash
git clone <repo-url> && cd book_loom
./gradlew build          # compile, lint, test
./gradlew :app:run       # opens the BookLoom window (1024x700, minimum 960x640)
```

The full gate — what pre-push and CI run — is:

```bash
./gradlew clean build check spotlessCheck
```

About 4 minutes on the owner's machine (measured 2026-10-01: 4m 13s, 118 tasks; it was 8m 39s before the tests ran
in parallel forks without TestFX's fixed sleeps), of which about two are `:ui`'s ~6,900 TestFX tests. While coding, use the focused loop
instead ([§7](#test-speed)). Note your own baseline: a run materially longer than it is treated as hung.

---

## 3. Layout and modules {#project-structure}

```
AGENTS.md                  operating manual (agents)         docs/Architecture.md   what is built, how to verify it
docs/specification/        the spec — editable reference     docs/adr/              decisions (ADR-0001 …)
docs/implementation_plan/  CHANGE_BACKLOG.md (order of work), 01_MODULE_INVENTORY.md (as-built log), 07_ROADMAP.md,
                           notes-corpus-verification.md (235-book sweep results), 04_ADR_FORMAT.md
openspec/                  changes/<name>/ = a unit of work; specs/ = ledger of built behaviour; archive/ = history
modules/                   all code (see below)              scripts/               jpackage per OS, launch smoke, agent-file sync
config/                    checkstyle, spotbugs, licence allowlist
tooling/hooks/             helpers the git hooks call        .lefthook/pre-push/    the pre-push scripts
```

Gradle project names are `:api` … `:app` even though the directories sit under `modules/` (ADR-0021).

| Module | Directory | Status | Holds |
|---|---|---|---|
| `:api` | `modules/api` | real | `Result`, `AppError`, `ErrorCode`, `SafeDetails`, the document model (incl. book inspection), `DocumentPort`, the chat-model, translation-engine, project and storage-port contracts (`ua.bookloom.api.llm`, `ua.bookloom.api.pipeline`, `ua.bookloom.api.project`, `ua.bookloom.api.persistence`) |
| `:util` | `modules/util` | real | per-OS paths, dev/prod environment, hashing, the language catalogue (`util.lang`) |
| `:document` | `modules/document` | real | EPUB/FB2/Markdown/TXT parse → mask → unmask → write back → close; book inspection, sentence splitting, auxiliary text units; the placeholder gate checks the multiset and the order/nesting of paired placeholders (ADR-0040) |
| `:llm` | `modules/llm` | real | the `pseudo` model, the Ollama-native and OpenAI-compatible clients (retry, single-flight gate, three-stage verification), model discovery |
| `:pipeline` | `modules/pipeline` | real | the translation engine, the pausable job (pause/resume/cancel, which also abort a model request in flight), checked export; prompt templates, chunking, the stored-project service, deterministic QA checks, the judge and self-heal — built and tested but not yet wired into a run |
| `:persistence` | `modules/persistence` | real | in-memory adapters behind every `:api` storage port (ADR-0034), proven by `RepositoryContractTest`; SQLite + Flyway + JDBI planned |
| `:ui` | `modules/ui` | real | the shell, six screens (Import, Book Brief, Structure, Translating, Export, Settings), the state mirror and viewmodels, `en`/`uk` bundles, the theme — six of eight planned packages |
| `:app` | `modules/app` | real | launcher, logging bootstrap, single-instance lock, Guice root, the command-line translator, the `archTest` suite |
| `build-logic` | `modules/build-logic` | — | the five convention plugins; an **included build**, not a subproject |

Allowed dependency edges point downward only: `:app`/`:ui` → `:pipeline` → `:document`/`:llm`/`:persistence` →
`:util` → `:api`. Only `:ui` and `:app` may see JavaFX. ArchUnit enforces this ([§7](#testing)).

---

## 4. Build configuration {#build-config}

**Toolchain.** `bookloom.java-conventions` declares `languageVersion = 25`. The JVM Gradle runs on (from
`JAVA_HOME`/`PATH`) and the JDK that compiles BookLoom (auto-detected against 25) are chosen independently, so
`java -version` says nothing about whether the build works. With no JDK 25 detected, configuration succeeds and the
first `compileJava` fails ([§12](#troubleshooting)).

**Version catalog.** Every coordinate lives in `gradle/libs.versions.toml`, pinned exactly; no build script carries
an inline `group:artifact:version`. A catalog entry that nothing references does nothing — a transitive version is
raised only by a matching `constraints {}` block in `bookloom.java-conventions` (Guava is the worked example).
JavaFX is 26.0.2 (ADR-0019: the first release with a built-in headless platform).

**Dependency locking** (STRICT is opted into with `-PstrictLocks`; the included build has its own lock state):

```bash
./gradlew -PstrictLocks verifyLocks                          # the gate: resolve everything, write nothing
./gradlew -p modules/build-logic -PstrictLocks verifyLocks   # the included build
./gradlew resolveAndLockAll --write-locks                    # regenerate after a catalog change
./gradlew -p modules/build-logic resolveAndLockAll --write-locks
```

`:app` and `:ui` produce **no lockfile diff** by design: `bookloom.javafx-conventions` deactivates locking on their
compile/runtime classpaths because a lock entry cannot record which per-OS JavaFX classifier resolved. Verify a new
`:app` dependency in the catalog, not the lockfile.

**Convention plugins** (`modules/build-logic/src/main/kotlin/`):

| Plugin | Applied to | Sets |
|---|---|---|
| `bookloom.java-conventions` | all 8 | JDK 25 toolchain, `--release 25`, `-Werror` + `-Xlint:all` (minus a few carve-outs), Lombok as the only annotation processor, Error Prone + NullAway, Checkstyle, SpotBugs + FindSecBugs, dependency locking, version constraints, `resolveAndLockAll`/`verifyLocks` |
| `bookloom.spotless-conventions` | all 8 | Palantir Java Format, 120 columns |
| `bookloom.test-conventions` | all 8 | JUnit 5 + AssertJ + WireMock + TestFX on the test classpath, headless system properties, parallel test forks, `fastTest` (everything but `slow`), the four local-only tasks `liveLocal` `promptEval` `visual` `corpus` |
| `bookloom.coverage-conventions` | the six FX-free modules | JaCoCo branch coverage ≥ 0.80 ([§8](#coverage)) |
| `bookloom.javafx-conventions` | `:ui`, `:app` | JavaFX 26 with per-OS classifiers, `--enable-native-access` on test tasks, the lock carve-out |

The root build adds one thing: the licence gate (`checkLicense`, [§9](#quality-gate)).

---

## 5. Run and debug {#running}

```bash
./gradlew :app:run                # the BookLoom window: opens 1024x700, minimum 960x640
./gradlew :app:run --debug-jvm    # suspends before main, listening on 127.0.0.1:5005
```

`run` is a plain `JavaExec` with main class `ua.bookloom.app.bootstrap.Launcher`, launched on the **classpath**. A
green `:app:run` therefore says nothing about the JPMS graph; the packaged image is what proves that ([§10](#packaging)).

**Three warnings print on every run and none matters**: `Unsupported JavaFX configuration: classes were loaded from
'unnamed module'` (the classpath launch), `System::load has been called by … NativeLibLoader` (JEP 472; native access
is enabled for test tasks only), and `sun.misc.Unsafe … HiddenClassDefiner` (Guice internals) — the last of these is
switched off on `:app:translate` below, but not here.

**What the window can do.** Open a book of any of the four formats (EPUB, FB2, Markdown, TXT), choose a target
language, a destination and whether to overwrite, verify a provider (the Ollama and LM Studio presets; connection,
models, then a test inference), choose a model, then start, pause, resume, stop and start again. The dashboard shows
progress, the four counts the engine emits and a log; after 10 s without an answer it says "Waiting for the model…
m:ss". Pause and Stop abort the request in flight, and a stopped run is final and writes nothing. When a run
finishes, the Export screen has a button that shows the written file in the operating system's file manager.

**What it cannot do yet.** Persist anything across a restart (`:persistence` binds every repository port to an
in-memory adapter, ADR-0034, so a run cannot be resumed after a restart and the theme is not remembered), detect the
book's source language (the selector is read-only), switch language inside the app, or open the Projects, Names &
style and Review entries, which are greyed and have no screen. The chunking, deterministic QA, judge and self-heal
logic that exists in `:pipeline` is likewise not wired into this run yet — the job still accepts a segment once its
markup restores.

**Ukrainian interface.** The language follows the operating system: a Ukrainian locale shows `uk`, anything else
shows `en`, and there is no switch in the app. To see Ukrainian without changing the OS, run
`./gradlew :app:run -Duser.language=uk -Duser.country=UA`. The build script does nothing special for this: Gradle
copies `user.language`, `user.country`, `user.variant` and `file.encoding` from its own JVM into every forked JVM
(`JvmOptions` in `gradle-process-services`), so a `-D` on the Gradle command line reaches the launched application
and `OsLocaleProvider` sees `uk_UA` (the log records `OS locale uk_UA is Ukrainian, displaying uk`).

**The startup log line** tells you which environment and directories a run used:

```
INFO [main] ua.bookloom.app.bootstrap.Launcher - app started version=dev environment=DEV dataDir=… logDir=…
INFO [JavaFX-Launcher] … BookLoomApplication - injector built and two-phase init complete
INFO [JavaFX Application Thread] … BookLoomApplication - window shown, initialView=IMPORT
```

**Where the app writes** (`ua.bookloom.util.paths.AppPathsResolver`; `-Dev` is appended in the dev environment):

| OS | Data (holds `bookloom.lock`, later `bookloom.db`) | Logs (`bookloom.log` + rolled files) |
|---|---|---|
| macOS | `~/Library/Application Support/BookLoom[-Dev]` | `~/Library/Logs/BookLoom[-Dev]` |
| Linux | `$XDG_DATA_HOME` or `~/.local/share/bookloom[-dev]` | `$XDG_STATE_HOME` or `~/.local/state/bookloom[-dev]/logs` |
| Windows | `%LOCALAPPDATA%\BookLoom[-Dev]` | `%LOCALAPPDATA%\BookLoom[-Dev]\logs` |

**Environment** — first match wins: `BOOKLOOM_ENV` (`dev`/`prod`) → `-Dbookloom.env=prod` → jpackage's
`jpackage.app-path` property → **dev**. Packaged images are stamped `prod` by `scripts/jpackage-common.sh`. Never
set `prod` in a development run configuration: it points a debug session at the user's real data. `BOOKLOOM_DATA_DIR`,
when absolute, overrides the whole per-OS layout (logs go to its `logs/` child).

**Startup order** (`Launcher`): resolve environment → resolve paths → create directories → lock
`dataDir/bookloom.lock` → resolve the log level → configure Logback → publish `StartupContext` → `Application.launch`.
Nothing before the Logback step may log (the `bootstrap-no-static-logger` ArchUnit rule enforces it). Exit codes: **0**
= another instance already runs (a refusal, not a failure), **1** = startup failed.

**Log level.** `BOOKLOOM_LOG_LEVEL`, else the system property `bookloom.log.level` (`TRACE`/`DEBUG`/`INFO`/`WARN`/
`ERROR`, any letter case), else the default: `INFO` for an installed (`prod`) app, `DEBUG` for a development run.
Both the desktop app and the command line below use this same resolver. A value that names no level falls back to
that default and logs one `WARN` line naming the rejected value; every other logger stays at `WARN` regardless, so a
third-party library never drowns BookLoom's own lines. Book text, prompts and model replies are logged only at
`TRACE`, never at `DEBUG` or above (`.claude/rules/logging.md`).

**Where the log goes.** A development or packaged run writes the `bookloom.log` from the table above — on macOS
`~/Library/Logs/BookLoom-Dev/bookloom.log` for a dev run. A **test** run writes its own log instead:
`modules/<module>/build/test-logs/test-<worker>.log` — one file per test JVM, because a module's tests run in parallel
forks (`<worker>` is Gradle's test-worker id; the newest file is the last run) — configured today for `:app`,
`:document`, `:llm` and `:pipeline`
(`src/test/resources/logback-test.xml`; the other modules have none yet). Read one at `TRACE` with
`BOOKLOOM_LOG_LEVEL=TRACE ./gradlew :<module>:test --tests '<class>' --rerun`.

**A development run's `DEBUG` default is verbose**: about 37 lines per segment (one 5,000-segment book wrote roughly
50 MB of log at `TRACE`), so translate a large book with `BOOKLOOM_LOG_LEVEL=INFO` unless you are actually following
one segment through its log.

**The detailed diagnostic log — the one to share.** Beside `bookloom.log`, a run can write `bookloom-trace.log` in
the same folder (on macOS `~/Library/Logs/BookLoom-Dev/bookloom-trace.log` for a dev run,
`~/Library/Logs/BookLoom/bookloom-trace.log` installed): every BookLoom line down to `TRACE` — prompts, replies and
book text included — one line per event (a line break inside a message is written as ` ⏎ `), with the `job`/`segment`
MDC on each line, rolled at 20 MB into `bookloom-trace.1.log.gz` … `.4.log.gz` (about 100 MB at most). It is **on by
default in a development run** and off in an installed app, where `BOOKLOOM_TRACE_FILE=1` (or
`-Dbookloom.trace.file=true`; `0`/`false` switches a dev run off) turns it on for that launch — nothing is saved yet,
so the environment variable is the durable way. `bookloom.log`, the console and their level are unchanged; third-party
loggers stay at `WARN` in both files. Each file (and each rotated-in file) starts with a `#` session header: version,
OS, JVM, JavaFX, locale, log level, detailed-log state, and the session facts known so far — provider, kind, endpoint
host (never a URL's user part, path or query), model, dial, review mode, the brief's choices, the book's file name,
format and size; each change of those facts is also an INFO `session update …` line. A running job writes an INFO
`run summary periodic …` line at most once a minute (accepted/flagged/verbatim/pending, model calls, average and p95
call time, tokens per second, timeouts, current segment) and a `run summary final …` line at its end. **To share it**,
open **About** (title bar): it says whether the detailed log is on, names the folder, and offers *Open log folder*,
*Copy log path* and *Save diagnostic bundle…* — a ZIP of the `bookloom-trace*` files (or `bookloom.log` when the
detailed log is off) plus `session.json`, written where you choose and sent nowhere. The detailed log contains book
text: it stays on the machine unless the person shares it.

**Command line.** `./gradlew :app:translate` runs `ua.bookloom.app.bootstrap.TranslateLauncher`, which repeats
`Launcher`'s pre-injector steps — paths, single-instance lock, logging — without starting JavaFX, then runs
`ua.bookloom.app.cli.TranslateCommand`:

```bash
./gradlew -q :app:translate --args="'<book>' [--to <lang>] [--from <lang>] [--overwrite] \
  [--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] [--base-url <url>] [--timeout <seconds>]"
```

Without provider flags it translates `<book>` with the deterministic offline `pseudo` model — the text comes back
upper-cased — and writes `<name>.<to><suffix>` beside it; `--to` defaults to `uk`. `--provider ollama` and
`--provider lmstudio` use their local presets and require `--model`; `--provider openai-compatible` additionally
requires `--base-url`. `--timeout` overrides the per-request timeout in seconds. `-q` keeps Gradle's own build chatter
out of the way, so the command's preflight and completion lines are what you see; `--overwrite` allows replacing an
existing destination.

For LM Studio, an unknown requested model id can still yield a reply from the model currently loaded by the server.
BookLoom warns about that model mismatch but retains an otherwise usable reply; use the preflight output and the server's
loaded-model configuration to avoid a typo before a full run. The mismatch itself is recorded as a WARN log entry.

**Quote a book path that contains spaces inside `--args`**, with an inner pair of single quotes, or Gradle's own
tokenizer splits it into two arguments before the command ever sees one:

```bash
./gradlew -q :app:translate --args="'/path/with spaces/Book.epub' --to uk"
```

An unquoted path with spaces reproduces exactly this failure: it is parsed as two arguments and exits 2, printing the
reason first, then the usage line.

**Exit codes** are distinct from the desktop app's above, deliberately: **0** the book completed; **1** the book did
not make it — it could not be opened, the job failed or was cancelled, the destination already exists and
`--overwrite` was not given, or BookLoom is already running; **2** invalid arguments. The desktop app exits **0**
when another instance already runs, because a second window is a refusal, not a failure; the command line exits **1**
for the same case, because a script needs to know that nothing was written. Those are the codes `TranslateLauncher`
exits with. Through Gradle they collapse: the `translate` task fails on any non-zero code, so `./gradlew` itself exits
1 for both 1 and 2, and a script that must tell them apart reads the printed line (`Invalid command arguments: …`
starts a usage error).

**Leaving a run overnight.** A window run never hangs silently. A provider outage (`unreachable`, 5xx, 429), a
timeout or a step that throws pauses the run and it **recovers by itself**: it waits 15 s, 30 s, 1, 2, 5 and 10 min and
then every 10 min, probes the provider at each wake (connection and model list, no inference) and resumes when the
probe passes; the banner reads "Waiting for the provider · Next try in 4:12 · attempt 6 · down since 02:14", the title
bar and the connection chip say so, and every step is an activity-log line and an INFO line in the log. After 12 hours
down it stops waking and waits for you. Pause holds the run for you, Retry now / Skip segment act at once, Stop ends it.
A wrong key, a missing model or a rejected request (`validation`) are not fixed by waiting and always wait for you. An
unloaded model (LM Studio's `Model unloaded`, `modelUnavailable`) usually loads again on the next request: the run wakes
like an outage but only six times, then the banner says "The model is not available — load it in the provider at
<host> and press Resume". A model call outstanding 1.5 times its own timeout, or 20 minutes with no segment decided, is ended by the stall
watchdog and retried as a timeout (two pauses, then the segment is flagged and the run goes on). While a run is at
work — including such a wait — the computer is kept awake: on macOS `caffeinate -i -m -w <pid>`, on Linux
`systemd-inhibit` when installed, on Windows nothing (one WARN line; set the power plan yourself). It is a local
process, so the offline invariant is untouched. Nothing is saved to disk: a crash or power loss still loses the run.

**Measuring scroll smoothness.** The build sets no JavaFX rendering flags, and none should be added without numbers.
To measure, hand the flags to the launched JVM through `JAVA_TOOL_OPTIONS` (the `run` task forwards no `-D` of its
own) and scroll the long views — the Names & style glossary with a few hundred rows, and the Translating screen's log
and review list during a run:

```bash
JAVA_TOOL_OPTIONS="-Djavafx.pulseLogger=true -Dprism.verbose=true" ./gradlew :app:run
```

`prism.verbose` prints once which pipeline renders (`es2`/`mtl`/`d3d`, or `sw` — software, which is slow everywhere) and
whether vsync is on. The pulse logger prints every pulse slower than one frame (17 ms by default; change it with
`-Djavafx.pulseLogger.threshold=<ms>`) with where the time went — CSS, layout, rendering. Compare before and after a
change on the same book and window size; a run of long pulses while scrolling a list points at its cells.

---

## 6. IDE (IntelliJ IDEA) {#ide}

Open the repository root, let IDEA import the Gradle build with the wrapper, and create a **Gradle** run
configuration (not an Application one) with tasks `:app:run` — it runs the same `JavaExec` the CLI does. For
debugging, run `./gradlew :app:run --debug-jvm` and attach a **Remote JVM Debug** configuration to
`localhost:5005` (the socket binds to `127.0.0.1` only). An Application configuration may launch `:app` on the module
path; prefer the Gradle one.

If the Build panel reports `Could not resolve org.jetbrains.kotlin:kotlin-stdlib` on
`:build-logic:generateExternalPluginSpecBuilders` while `./gradlew` builds fine, IntelliJ is using a different,
locally-installed Gradle than the wrapper (Settings → Build, Execution, Deployment → Build Tools → Gradle →
Distribution) — switch it back to the Gradle Wrapper.

---

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

**The prompt eval** (`@Tag("promptEval")`, `PromptEvalTest`) sends 26 fixed English → Ukrainian cases through the
production prompt builders to a real Ollama model (`BOOKLOOM_EVAL_MODEL`, default `gemma4:e4b-mlx`; skipped when
`BOOKLOOM_EVAL_OLLAMA_URL` is unset) and measures each reply before any repair: it parses, its tokens are intact with
every pair around words (gate), it is in Cyrillic, it holds the case's marker (a glossary rendering, a correct drop cap,
a translated instruction), and the judge scores a good candidate ≥ 0.8 and a bad one ≤ 0.6. The table lands in
`modules/pipeline/build/reports/promptEval/<model>.txt`; the task fails below parse 95%, gate 90% or judge separation
80%. Calibration on 2026-10-02 (one sample per case, temperature as in production):

| Prompts | Model | parse | gate | judge separation | script | marker | injection |
|---|---|---|---|---|---|---|---|
| before (step 8g) | gemma4:e4b-mlx | 100% | 91% | 50% | 100% | 67% | 100% |
| after (step 9) | gemma4:e4b-mlx | 100% | 100% | 100% | 100% | 89% | 100% |
| after (step 9) | gemma4:e2b-mlx (floor) | 100% | 82% | 100% | 100% | 100% | 100% |

Before, the judge scored an untranslated English candidate 1.0, a pair was moved off its words and a locked name was
written out instead of its token; after, the one miss on e4b is the drop cap (`⟦g0⟧Т⟦g1⟧іч` — the source letter kept),
and e2b drops pairs, which the run's placeholder gate refuses and sends to repair.

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
`docs/implementation_plan/notes-corpus-verification.md`.

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

## 8. Coverage {#coverage}

```bash
./gradlew :document:jacocoTestReport               # HTML + XML report for one module
./gradlew :document:jacocoTestCoverageVerification # the gate for one module (also run by check)
```

Reports land at `modules/<module>/build/reports/jacoco/test/html/index.html` (and `jacocoTestReport.xml` beside it).
The gate is **branch coverage ≥ 0.80 per module**, applied to `:api`, `:util`, `:document`, `:llm`, `:pipeline`,
`:persistence`; `:ui` and `:app` are covered behaviourally and exempt. A module whose only sources are
`module-info.java`/`package-info.java` skips the check; a module with sources and no tests fails at 0%.

---

## 9. Quality gate, hooks and CI {#quality-gate}

`./gradlew check` runs Spotless (`spotlessCheck`), Checkstyle (`config/checkstyle/`, zero warnings allowed), Error
Prone + NullAway (as javac plugins; every package is `@NullMarked`), SpotBugs + FindSecBugs (`config/spotbugs/`),
ArchUnit (`:app:archTest`), the tests, and the JaCoCo verification. `./gradlew spotlessApply` fixes formatting;
`spotlessCheck` only reports it.

CI-only, because it needs the network: the licence gate.

```bash
./gradlew checkLicense     # every runtime dependency's licence must be in config/license/allowed-licenses.json
```

**Git hooks** (Lefthook; inactive until installed once per clone):

```bash
brew install lefthook gitleaks      # or scoop install … on Windows
lefthook install                    # writes .git/hooks
lefthook validate                   # optional: does lefthook.yml parse
```

| Stage | Runs |
|---|---|
| `pre-commit` | `spotlessApply` on staged Java (re-stages), `gitleaks` on the staged diff, a 4 MB file-size guard (`BOOKLOOM_MAX_FILE_SIZE_KB` overrides) |
| `commit-msg` | Conventional Commits check |
| `pre-push` | `.lefthook/pre-push/quality-gate.sh` = the full gate, and `agent-config-sync.sh` = `scripts/sync-agent-files.py --check` |

Pre-push runs **exactly** the CI quality command, so a green push implies a green CI quality job. Escape hatches
(`git push --no-verify`, `LEFTHOOK=0`) exist and are a decision, not a habit; agents may not use them.

**OpenSpec agent files** are the `openspec-*` skills in `.agents/skills/` and the `/opsx:*` commands in
`.claude/commands/opsx/`. The OpenSpec CLI generates them; never edit them by hand. Claude and Codex share one copy of
each skill: Codex reads `.agents/skills/` directly, and Claude reads it through the `.claude/skills` symlink. That copy
is the Codex rendering, whose references work in both tools.

```bash
OPENSPEC_TELEMETRY=0 openspec config profile core           # once per machine: propose, explore, apply, update, sync, archive
OPENSPEC_TELEMETRY=0 openspec update                        # after upgrading the CLI: npm install -g @fission-ai/openspec@latest
OPENSPEC_TELEMETRY=0 openspec init --tools claude,codex     # first set-up; codex comes last so its rendering is the one kept
```

**CI** (`.github/workflows/ci.yml`, on every push and pull request): a *quality* job (lock verification, the gate,
the agent-file sync check, the licence gate — about 4 minutes) and a five-leg *packaging* matrix (macOS x86_64 and
aarch64, Linux x86_64 and aarch64, Windows) that builds each image and, on POSIX, launch-smokes it — about 8 minutes
end to end. Test reports are uploaded only on failure.

---

## 10. Packaging {#packaging}

Stage the input first, on every OS:

```bash
./gradlew :app:collectDist    # -> modules/app/build/dist/libs/app.jar + the runtime classpath jars
```

The scripts require `modules/app/build/dist/libs/app.jar` to exist and drive the plain `jpackage` CLI with a
classpath launch (`--main-jar app.jar`), `--java-options "-Dbookloom.env=prod"` and a stripped jlink runtime.
`APP_VERSION` overrides the Gradle version (default `dev`; jpackage's own `--app-version` is coerced to a numeric
placeholder). There is no cross-compilation: each image is built on its own OS.

| OS | Command | Needs | Produces under `build/package/` |
|---|---|---|---|
| macOS | `./scripts/package-macos.sh` | a JDK 25 with `jpackage` | `BookLoom.app`, `BookLoom-<version>-macos-<arch>.tar.gz`, `.dmg` |
| Linux | `./scripts/package-linux.sh` | Liberica Full on aarch64; `fakeroot` for the `.deb` | `BookLoom/`, `…-linux-<arch>.tar.gz`, `.deb` |
| Windows | `scripts\package-windows.bat` | Liberica 25 Full (`jdk+fx`) | `BookLoom\BookLoom.exe` (app-image only, no zip) |

**Launch smoke** (POSIX only):

```bash
./scripts/launch-smoke.sh [path/to/image]     # SMOKE_TIMEOUT_SECONDS=90 by default
```

It launches the packaged image against a throwaway `BOOKLOOM_DATA_DIR` and asserts the three startup log lines from
[§5](#running) appear in `<dataDir>/logs/bookloom.log`. On Linux the packaged app starts the real GTK toolkit, so a
display is required — CI starts Xvfb before the smoke. The app icons `jpackage` reads are committed under
`docs/specification/assets/icon/dist/`; regenerate them from `appicon.png` with the scripts beside it.

---

## 11. Cleanup {#cleanup}

```bash
./gradlew clean                          # build/ at the root and modules/*/build (reports included)
./gradlew -p modules/build-logic clean   # the included build — root clean does not reach it
rm -rf .gradle .kotlin                   # Gradle's project cache; safe, costs one re-configure
```

Fresh app state means deleting the data and log directories from [§5](#running), e.g. on macOS
`~/Library/Application Support/BookLoom-Dev` and `~/Library/Logs/BookLoom-Dev`. The corpus report directory is
whatever `BOOKLOOM_CORPUS_REPORT_DIR` pointed at, or a `bookloom-corpus-report-*` temp directory.

---

## 12. Troubleshooting {#troubleshooting}

**`Cannot find a Java installation … matching {languageVersion=25}`** — no detectable JDK 25 and no
auto-provisioning. Install one ([§1](#prerequisites)) and confirm with `./gradlew -q javaToolchains`.

**`:app:run` appears to hang, then exits 0 after five minutes** — another instance holds `bookloom.lock` (often a
forgotten IDE launch). The second launch shows a "BookLoom is already running" window, waits up to 300 s for it to be
dismissed, then exits 0. Dismiss it, or find the first instance (`jps -l`) and its lock file in the data directory.

**A catalog pin changes nothing** — only a `constraints {}` entry in `bookloom.java-conventions` makes a version
bind. `grep -rn 'libs\.<alias>' --include='*.kts' .`; no hit means decoration.

**`:app` shows no lockfile diff after adding a dependency** — correct ([§4](#build-config)); check the catalog.

**A gate run takes materially longer than your baseline** — treat it as hung: kill it and diagnose. Never run two
gates at once; they contend for the same daemon and outputs.

**A UI test wants a real display** — a hand-rolled `Test` task that skipped `bookloom.test-conventions`. Let the
convention configure it; do not reach for Monocle.

**`jpackage is not on PATH`, or it fails on missing JavaFX jmods** — a JRE instead of a JDK, or a plain JDK on
Windows/Linux aarch64. Install Liberica 25 Full there ([§1](#prerequisites)); never copy jmods by hand.

**Packaging fails with `Specified icon file … does not exist`** — the committed icons are missing from your checkout;
regenerate them (`docs/specification/assets/icon/README.md`).

**`scripts/*.sh` or the hooks do not run on Windows** — run them from Git Bash or WSL, with Python 3 installed.
