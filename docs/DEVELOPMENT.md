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

**The evidence log.** The detailed log rolls at 20 MB, so a long run keeps only its last hours (an 8.3-hour run kept
3.6). Beside it, **`bookloom-trace-evidence.log`** (10 MB, rolled into `bookloom-trace-evidence.1.log.gz` … `.3.log.gz`)
keeps the full `TRACE` of every segment that ended **flagged** or went through **repair**, for the whole run: each
segment's lines are held in memory (at most 64 segments of 400 lines and 8 MB in all) until the pipeline's decision line carries the
`EVIDENCE_KEEP` marker, then written with the segment's later lines; a segment accepted at once is forgotten. It is on and
off with the detailed log, and the diagnostic bundle includes it. To read why `part0044.html:16` was flagged, grep that
segment key in the evidence file first.

**Command line.** `./gradlew :app:translate` runs `ua.bookloom.app.bootstrap.TranslateLauncher`, which repeats
`Launcher`'s pre-injector steps — paths, single-instance lock, logging — without starting JavaFX, then runs
`ua.bookloom.app.cli.TranslateCommand`:

```bash
./gradlew -q :app:translate --args="'<book>' [--to <lang>] [--from <lang>] [--overwrite] \
  [--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] [--base-url <url>] [--timeout <seconds>] \
  [--quality fast|balanced|max] [--names translate|transliterate|keep] [--review-names] \
  [--max-outage <duration>] [--report <file>] [--stop-after <segments>] [--no-partial]"
```

Without provider flags it translates `<book>` with the deterministic offline `pseudo` model — the text comes back
upper-cased — and writes `<name>.<to><suffix>` beside it; `--to` defaults to `uk`. `--provider ollama` and
`--provider lmstudio` use their local presets and require `--model`; `--provider openai-compatible` additionally
requires `--base-url`. `--timeout` overrides the per-request timeout in seconds. `-q` keeps Gradle's own build chatter
out of the way, so the command's preflight and completion lines are what you see; `--overwrite` allows replacing an
existing destination.

It is the headless proof tool, so it runs a book the way a window run does. `--quality` and `--names` are saved on the
brief (defaults Balanced and Transliterate; `keep` is Keep original). `--review-names` runs the names scan, Review with
model and Accept all before translating and prints one `names: …` line. The run pauses on an error and **recovers from
an outage by itself** with the window's schedule and probe (below) for up to `--max-outage` (default `12h`; `90m`,
`1h30m`, `45s` or ISO `PT2H`), printing a line when an outage begins; a pause only a person could end — a wrong key, a
missing model, a refused request, an outage past the limit — stops the run with `Stopping the run: <reason>`. A run that
did not complete, Ctrl+C included, **still exports what it translated** (the rest in the source) and prints
`Partial: <path> (accepted=…, flagged=…, pending=…) — the run ended <state>: <reason>`; `--no-partial` writes nothing
instead. `--report <file>` writes a JSON summary: provider, model, dial and names, phase timings and model calls, how
the run ended and why, every flagged segment with its reason and finding kinds, each outage, the names review counts and
what the export wrote, source fallbacks included. `modelCallsByKind` in it holds, per call kind (draft, judge, directed
fix …), the attempts and failures, prompt and completion tokens, the tokens the server says its cache served, and the
elapsed, prompt-evaluation and generation seconds — counts and times only, never book text; the final `run summary`
log line carries the same totals as `byKind[…]`. `--stop-after <n>` ends the run once `n` segments are decided
(the partial export is written as for any stopped run), so a measurement can use part of a book.

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

**Exit codes** are distinct from the desktop app's above, deliberately: **0** the book completed and was written;
**3** the run did not complete (failed, stopped or interrupted) and what it translated was written; **1** nothing was
written — the book could not be opened, the run did not complete under `--no-partial`, the export failed, the
destination already exists and `--overwrite` was not given, or BookLoom is already running; **2** invalid arguments. The desktop app exits **0**
when another instance already runs, because a second window is a refusal, not a failure; the command line exits **1**
for the same case, because a script needs to know that nothing was written. Those are the codes `TranslateLauncher`
exits with. Through Gradle they collapse: the `translate` task fails on any non-zero code, so `./gradlew` itself exits
1 for 1, 2 and 3, and a script that must tell them apart reads the printed line (`Invalid command arguments: …`
starts a usage error, `Partial: …` a partial export).

**The headless end-to-end proof** — `scripts/e2e-fixture.sh <epub|fb2|md|txt|all> [--provider ollama|lmstudio]
[--model <id>] [--quality fast|balanced|max] [--names …] [--no-review-names] [--out <dir>] [-- <more flags>]` —
translates the earth-gravity fixture (`modules/app/src/test/resources/fixtures/earth-gravity/`) through this command
with a real local model, the way a window run would (`--names transliterate --review-names` by default), then checks the
written book with `scripts/validate-translated-book.py --lang uk` and prints one line per format: time, exit code,
segment counts (accepted, flagged and its share, auto-accepted, repaired, kept verbatim, pending, source fallbacks,
outages) and the validator's result with each FAIL line under it. Per format it keeps the book, the `--report` JSON,
the console and the validator output under `build/e2e/<fmt>/`. The model must already be served (`ollama serve`, or LM
Studio's server; Ollama loads it on the first call, 30–60 s); the script starts and stops nothing.

```bash
scripts/e2e-fixture.sh all                                        # gemma4:e4b-mlx on Ollama, Balanced
scripts/e2e-fixture.sh epub --provider lmstudio                   # google/gemma-4-e4b via LM Studio's /v1
scripts/e2e-fixture.sh epub --model gemma4:12b-mlx                # a larger model for comparison
```

**Headless proof results** (earth-gravity fixture, en → uk, gemma4:e4b-mlx on Ollama unless named; *before* = the
harness runs of 2026-10-02 04:53–05:10, *after* = `scripts/e2e-fixture.sh`, Balanced, Transliterate, `--review-names`):

| Run | Format | Time | Segments | Accepted | Flagged | Validator |
|---|---|---|---|---|---|---|
| before (Fast, Translate) | TXT | 177 s | 92 | 91 | 1 | FAIL — 3 lists/stanzas merged, ch6-p2 left English, names |
| before (Fast, Translate) | MD | 237 s | 140 | 137 | 3 | FAIL — stanzas 1–2 and ch6-p2 left English, names |
| before (Fast, Translate) | FB2 | 193 s | 147 | 144 | 3 | FAIL — ch6-p2 left English, names |
| before (Balanced) | EPUB | 275 s | 163 | 159 | 4 | FAIL — ch6-p2 left English, names |
| before, OpenAI-compatible `/v1` | EPUB | — | 163 | — | 114 (`emptyCompletion`) | — |
| after | TXT | 245 s | 92 | 92 | 0 | PASS |
| after | MD | 284 s | 140 | 140 | 0 | PASS |
| after | FB2 | 294 s | 147 | 147 | 0 | PASS |
| after | EPUB | 304 s | 163 | 163 | 0 | PASS |
| after, LM Studio `google/gemma-4-e4b` (`/v1`) | EPUB | 644 s | 163 | 159 | 4 | PASS |
| after, gemma4:12b-mlx | EPUB | 1321 s | 163 | 162 | 1 | FAIL — one `Венс` for the accepted `Ванс` (a model slip) |

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

### Where the time goes {#where-the-time-goes}

Measured by task 15d.0 (2026-10-04): the first 300 decided segments of Bartimaeus 1 (EPUB, en→uk) through the command
line — `--provider ollama --quality balanced --names transliterate --stop-after 300`, Ollama native endpoint, one
inference at a time — with the provider's own counts and timings from `modelCallsByKind` in the `--report` JSON.
Prompt evaluation and generation are the server's `prompt_eval_duration` and `eval_duration`; their shares of a call's
wall time do not add to 100 % because the rest is load, queueing and the client. Ollama reports no cached-token
figure (it counts only the tokens it evaluated), so the cache saving is not measured here; the prompt-evaluation
throughput below is what the cache would have to beat.

| Model | Call kind | Calls | Input tokens | Output tokens | Wall (share of model time) | Prompt-eval share | Generation share |
|---|---|---:|---:|---:|---:|---:|---:|
| `gemma4:e4b-mlx` | draft | 296 | 301,148 | 19,117 | 756 s (81 %) | 24 % | 74 % |
| | judge | 83 | 117,542 | 1,753 | 168 s (18 %) | 59 % | 39 % |
| | directed fix | 8 | 6,061 | 211 | 12 s (1 %) | 35 % | 62 % |
| `gemma4:26b-mlx` | draft | 296 | 303,970 | 19,978 | 1,138 s (56 %) | 34 % | 65 % |
| | judge | 135 | 179,781 | 9,060 | 602 s (30 %) | 44 % | 54 % |
| | directed fix | 56 | 50,901 | 6,119 | 285 s (14 %) | 29 % | 70 % |
| | repair, reflect, improve | 3 | 2,350 | 146 | 10 s (0 %) | — | — |

Whole run: e4b 387 calls, 936 s of model time, **3.1 s per segment**, 1.29 calls per segment, 296 accepted and 4 flagged
of 300; 26b 490 calls, 2,035 s, **6.8 s per segment**, 1.63 calls per segment, 280 accepted and 20 flagged. A draft call
sends about 1,020 tokens and returns about 65, so a quarter to a third of its time is evaluating a prompt that is mostly
the same instructions again; a judge call sends about 1,400 tokens for about 20–70 back, so on e4b more than half of
a judge call is prompt evaluation. The 26b judge and fix calls fire far more often (135 and 56 against 83 and 8), which,
with the larger model's slower generation, makes up the extra 3.7 s a segment over e4b.

**Segment lengths** (`scripts/segment-histogram.py <book>`, words per block; a block approximates a segment, so counts
differ by a few from the app's own):

| Book | Blocks | Words | Mean | Median | Blocks at 1–3 / 4–10 / 11–30 / over 30 words | Words at 1–3 / 4–10 / 11–30 / over 30 |
|---|---:|---:|---:|---:|---|---|
| Bartimaeus 1 | 3,579 | 127,228 | 35.5 | 25 | 9.6 / 17.6 / 29.3 / 43.4 % | 0.5 / 3.5 / 15.9 / 80.1 % |
| Harry Potter 1 | 2,937 | 80,745 | 27.5 | 19 | 5.4 / 22.9 / 39.7 / 31.9 % | 0.4 / 5.7 / 27.3 / 66.6 % |

Reading both together: 27 % of Bartimaeus blocks have at most 10 words yet carry about 4 % of its words, so one call per
segment spends most of its calls on very little text, and the 1,020-token prompt is mostly fixed instructions. That is what
batching (15d.8) and a lean reviewer (15d.6) have to win back; generation of the translation itself is the floor.

### Group 15d: before and after {#15d-results}

Measured 2026-10-05 on Bartimaeus 1 (EPUB, en→uk, Ollama native endpoint, **Balanced**, Unattended, names transliterate,
one inference at a time) with the `--report` JSON; "before" is the 15d.0 measurement on the same first 300 segments and,
for the whole book, the 8 h 18 min LM Studio `google/gemma-4-26b-a4b-qat` run of 2026-10-03. "Seconds per segment" is the
model time (`modelSeconds`) divided by the segments decided.

| Run | Calls / segment | Seconds / segment | Flagged | Notes |
|---|---:|---:|---:|---|
| e4b, first 300, before (15d.0) | 1.29 | 3.1 | 4 of 300 | judge + fix chain, one draft call per segment |
| e4b, first 300, batches of 4 | 0.60 | 3.45 | 5 of 300 | draft 0.30 / segment, reviewer 80 calls |
| e4b, first 300, batches of 8 (now) | **0.34** | **3.02** | 4 of 300 | draft 91 → 51 calls, reviewer 80 → 42 calls |
| 26b, first 300, before (15d.0) | 1.63 | 6.8 | 20 of 300 | |
| 26b, first 300, batches of 4 | 0.69 | 4.48 | 5 of 300 | |
| 26b, first 300, batches of 8 (now) | **0.50** | **5.14** | 3 of 300 | draft calls dominate (1,180 s) |
| e4b, whole book (3,783), now | **0.36** | **3.44** | 92 (2.4 %) | 3 h 40 min against 8 h 18 min; 0 timeouts; 584 repaired, 72 verbatim; audit lists 5 suspicious |
| 26b (LM Studio), whole book, 2026-10-03 | not measured | 7.9 | 170 (4.5 %) | the 8-hour baseline |

Against the targets of task 15d.14: calls per segment ≤ 0.4 **met** (0.34 on the first 300, 0.36 over the book); the whole
book takes 44 % of the baseline's time **(about half, met)**, but the first-300 goal of about 1.6 s (e4b) and 3.4 s (26b)
per segment is **not met** (3.0 s and 5.1 s): generating the translation is the floor (about 20,000 output tokens for 300
segments at about 38 tokens a second, 2.0 s a segment), and the reviewer adds about 0.9 s. Flagged ≤ 2 % is **just missed**
over the book (2.4 %; 4.5 % before). Unbalanced quote pairs: none left in the audit's list. False flags as a share of flags
and name consistency ≥ 99 % were **not measured**. Recurring terms: `gemma4:e4b-mlx` never returns the optional `terms` field (0 renderings recorded over the book), so renderings are
now also learned from the decided pairs by co-occurrence (second e4b whole-book run, 2026-10-06: 3 h 31 min, 0.347 calls per
segment, 70 flagged = 1.85 %): 12 terms learned, 90.3 % of their occurrences carry the learned rendering (`master` 99 %,
`pentacle` 94 %), against the 95 % target; the generic words the key-term scan also proposes pull the aggregate down.

Where e4b's 13,017 s of model time went over the book: draft 8,648 s (674 calls, 1.00 M prompt tokens, 290 k output),
reviewer 3,718 s (484 calls, 1.03 M prompt, 106 k output), directed fix 612 s (198 calls), placeholder repair 39 s.

### Quality round 2: the "before" table (15e.4) {#15e-before}

Measured 2026-10-06/07 on the commit that holds 15e.1–15e.3, Ollama native endpoint, one inference at a time, through the
production request factory (`PromptRequests`, window 8192, Balanced). These are the numbers every 15e task has to move.
Thresholds per model class stay in `eval/thresholds.json`; the new suites assert no floor yet, a task that fixes a class
raises the floor in its own change.

**Reviewer corpus (15d.1 + 15e.2 production path, stability ×3).** e4b: false negatives 0 %, false positives 0 %, stability 100 %, token breaks 0; 26b: false negatives 0 %, false positives 5.9 %, stability 97 %, token breaks 0.

**Batch protocol (size 4 / 8 / 12 / 16: id validity / token gate / too short).** e4b: 100 % / 96 % / 0 %; 100 % / 100 % / 4 %; 100 % / 100 % / 4 %; 100 % / 100 % / 0 %. 26b: 100 % / 100 % / 0 %; 100 % / 100 % / 0 %; 100 % / 100 % / 0 %; 100 % / 100 % / 0 %.

**Real-run corpus (`--suite realrun`, pass rate per kind and call; `+Nk` = known failures reported beside the rate).**

| kind / call | e4b | 26b |
|---|---|---|
| `quotes/draft` | 100% (9/9) | 89% (8/9) |
| `quotes/review` | 90% (9/10) | 80% (8/10) |
| `mixed-script/draft` | 100% (6/6) | 100% (6/6) |
| `mixed-script/review` | 67% (4/6) | 83% (5/6) |
| `invented-word/review` | 0% (0/3) | 100% (3/3) |
| `russian-letters/review` | 0% (0/5) | 20% (1/5) |
| `narrator/draft` | 100% (5/5) | 100% (5/5) |
| `narrator/review` | 83% (5/6) | 83% (5/6) |
| `short-line/draft` | 100% (0/0) +6k | 100% (0/0) +6k |
| `short-line/review` | 100% (0/0) +6k | 100% (0/0) +6k |
| `reviewer-batch/review-batch` | 100% (19/19) | 95% (18/19) |
| `reviewer-long/review-batch` | 92% (11/12) | 92% (11/12) |
| `batch-terms/batch` | 100% (1/1) | 100% (1/1) |
| `batch-context/batch` | 100% (1/1) | 100% (1/1) |
| `repair/repair-placeholder` | 50% (2/4) | 100% (4/4) |
| `repair/repair-structural` | 100% (2/2) | 100% (2/2) |

**Sequence eval (`--suite sequence`, 328 paragraphs, narrator unset | set).**

| metric | e4b unset | e4b set | 26b unset | 26b set |
|---|---:|---:|---:|---:|
| Flagged of 328 | 19 | 15 | 12 | 18 |
| Flagged with no stored target (exported as source) | 2 | 2 | 7 | 11 |
| Hard-gate failures at round 0 | 12 | 16 | 36 | 31 |
| Narrator gender slips (first-person chapters) | 1 | 0 | 16 | 0 |
| English leftovers | 0 | 0 | 0 | 0 |
| Blocking quote failures | 0 | 0 | 0 | 0 |
| Leaked protocol text | 0 | 0 | 0 | 0 |
| Truncated reviewer replies | 0 | 0 | 0 | 0 |
| Wrongly shared learned renderings | 0 | 0 | 0 | 0 |
| Dominant rendering share (mean over terms) | 0.87 | 0.87 | 0.92 | 0.92 |
| Distinct renderings per term (mean) | 1.80 | 1.80 | 1.60 | 1.50 |
| Name spelling variants (sum over names) | 3 | 2 | 2 | 1 |
| Batch fallback rate | 0.01 | 0.01 | 0.07 | 0.05 |
| Calls per segment | 0.35 | 0.36 | 0.62 | 0.48 |
| Seconds per segment | 3.8 | 4.0 | 6.3 | 5.6 |

Reading it: the sequence eval reproduces the run's gate and consistency problems (flagged segments exported without a
target, round-0 hard-gate failures, magician/pentacle/sir/boy splits, name spelling variants) and the narrator-gender
slips that appear only on 26b with the narrator unset (16 against 0 with it set). It does not reproduce leaked protocol
text or truncated reviewer replies at this size; the realrun corpus carries those shapes.

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

**The prompt eval** (`@Tag("promptEval")`, `PromptEvalTest`) sends 26 fixed English → Ukrainian cases, and one batch of
the fixture book's names given suggested targets (four rows, one per name), through the production prompt builders to a
real Ollama model (`BOOKLOOM_EVAL_MODEL`, default `gemma4:e4b-mlx`; `BOOKLOOM_EVAL_ONLY=<case-name prefix>`, such as
`suggest`, runs only those cases; skipped when `BOOKLOOM_EVAL_OLLAMA_URL` is unset) and measures each reply before any
repair: it parses, its tokens are intact with every pair around words (gate), it is in Cyrillic, it holds the case's
marker (a glossary rendering, a correct drop cap, a translated instruction, a name's uninflected dictionary form), and
the reviewer leaves a good candidate alone and asks for a change to a bad one (an edit whose quote is in the text, or a rewrite; an edit that quotes nothing is a harmless hallucination). The table lands in
`modules/pipeline/build/reports/promptEval/<model>.txt`; the task fails below parse 95%, gate 90% or review separation
80%.

*How the eval builds a request (15e.1).* An eval sends what the app sends. Each case becomes an `EvalProject`
(test side): its glossary entries and recurring terms go into the in-memory stores, its segments form one unit with the
earlier pairs, summary and narrator the case states (`EvalContext`; a case that states none gets none), and the request
is built by the run's own `PromptRequests` (`ua.bookloom.pipeline.run`) — the same code the job calls for a chunk's
context, a batch's fit and previous pairs, the `terms` request, the draft and its two repairs, and the reviewer's
term pairs and character sheet. A locked name is written out in the case text and hidden behind the run's token by
the real mask. Every model call goes through `JobModelCalls`, so the request is sized to the window and the reply cap
exactly as in a run; `BOOKLOOM_EVAL_WINDOW` sets that window (default: the app's, 8192 tokens). The equality of the
two paths is held by `PromptRequestsEquivalenceTest`. Not covered by this path yet: the name prescan, the glossary
review and the rolling summary call, which build their own frame (deferred from 15e.2 to 15e.13, which tunes those
prompts; `SummaryModelCall` is package-private).

**Real-run corpus (15e.2).** The defect classes of the 6 h 17 min GUI run (Bartimaeus 1, en → uk, 26b) as synthetic cases
under `modules/pipeline/src/test/resources/eval/realrun/` (no book text; failure shapes only), run as their own suite:

```bash
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_STABILITY=3 \
  BOOKLOOM_EVAL_SUITE=realrun ./gradlew :pipeline:promptEval          # e4b (BOOKLOOM_EVAL_ONLY=<case-id prefix> narrows it)
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:26b-mlx BOOKLOOM_EVAL_STABILITY=3 \
  BOOKLOOM_EVAL_SUITE=realrun ./gradlew :pipeline:promptEval          # the 26b class
scripts/eval-matrix.sh --suite realrun --models "ollama:gemma4:e4b-mlx ollama:gemma4:26b-mlx" --stability 3   # both, one table
```

| File | Kind (report group) | What it holds |
|---|---|---|
| `text.json` | `quotes` (10), `mixed-script` (6), `invented-word` (3), `russian-letters` (5), `narrator` (6), `short-line` (6) | one source with a clean and a defective Ukrainian candidate (`good`, `bad`), the production check that decides it (`QUOTES`, `SCRIPT`, `GENDER`, `LENGTH`, `NONE`), a glossary and the context (earlier pairs, summary, lexicon, narrator) |
| `batch.json` | `batch-terms`, `batch-context` | a batch whose lexicon makes it ask for `terms`, with scripted replies of the shapes the run produced: correct, the `terms` JSON leaked into a target (`«terms»: {…}}, {`), a code fence in a target, a far-too-short target |
| `reviewer.json` | `reviewer-batch` (6, 5 and 8 pairs with glossary, lexicon, characters and the narrator), `reviewer-long` (12 long paragraphs) | one defect among clean pairs; each pair labelled |
| `repair.json` | `repair-placeholder`, `repair-structural` | a Markdown source the real parser masks, the reply a run refuses and two scripted answers for the production document gate |
| `scan.json` | offline only | the key-term and name scans on synthetic lines (titles, `Great Hall`, `Old Bailey`) |

Every request of a model run is the run's own (draft, batch with its `terms` request, reviewer over a whole chunk with
the term pairs and character sheet, repairs). Each label of a deterministic case is held to the production code by plain
offline tests (`RealRunCorpusTest`, `RealRunRunnerTest`; no model, part of `check`): the text checks, `GenderChecks`, the
draft evaluation's length check, `BatchReplyParser` with `ItemValidator`, the real document gate, `KeyTermScan`.
`deterministic: false` marks a case only the reviewer can judge (the Russian-only letters `ы ъ э ё` until the alphabet
check of 15e.11; invented words, since no dictionary is bundled; an ASCII `"` inside « »; a gender slip behind `нічого не`),
so it counts for the reviewer only.

*Known failures.* `knownFailure: true` marks a case production gets wrong today, with `fixedBy` naming the task: the
41-character and five other compact lines that `LengthCheck` refuses (15e.8), the leaked `terms` and code-fence replies the
batch parser accepts (15e.6), the generic words `great`, `hall` and `old` that `KeyTermScan` proposes (15e.11). They are
reported beside the rates (`+Nk` in the matrix, `known` in the table), never inside one, and the offline test fails when one
starts passing, so the fixing task drops the flag in the same change.

*Report.* Rows are grouped by kind and call (`quotes/draft`, `quotes/review`, `reviewer-long/review-batch`, …); a rate is
the share of counted cases that came out as labelled. Extra metrics: `truncated` (reviewer calls whose reply stopped with
`finish=LENGTH`), `tooShort` and `leaked` (share of batch items whose target is far shorter than the source, or carries the
`terms` object, an `id` entry or a fence), and `stability` (reviewer rows whose `BOOKLOOM_EVAL_STABILITY` repeats agree).
`tooShort` and `leaked` also appear in the batch A/B (`--suite batch`), and every case-set report now ends with the share
of corpus cases right per kind (`byKind` in its JSON, a second table in the matrix). No threshold is set yet: 15e.4 records
the "before" numbers and the thresholds per model class from this suite.

**Sequence eval (15e.3).** Every case above stands alone; the defects of the 6 h run (a title drifting between renderings, a
name in two spellings, a narrator's gender slipping, English paragraphs exported as source) only show over many segments
with the context accumulating. The sequence suite runs a synthetic book through the *real batched job* — preparation, token-budgeted
batches, the reviewer the dial enables, the repair path, the lexicon and its learner, the rolling summary — against a real
model, unattended, then reads what the run decided and measures it with production checks only.

```bash
# e4b, the real run's brief (no narrator), then the same book with a first-person male narrator in the brief
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_SUITE=sequence \
  BOOKLOOM_EVAL_NARRATOR=unset ./gradlew :pipeline:promptEval
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_SUITE=sequence \
  BOOKLOOM_EVAL_NARRATOR=set ./gradlew :pipeline:promptEval
# 26b: the same two commands with BOOKLOOM_EVAL_MODEL=gemma4:26b-mlx (BOOKLOOM_EVAL_DIAL=FAST|BALANCED|MAX, default BALANCED; BOOKLOOM_EVAL_WINDOW as for the other suites)
scripts/eval-matrix.sh --suite sequence --narrator both --models "ollama:gemma4:e4b-mlx ollama:gemma4:26b-mlx"   # all four runs, one table
```

*Runtime.* About 15-25 minutes per narrator mode on e4b and 40-70 minutes on a 26B class model (about 330 segments, Balanced), so
the matrix timeout for this suite is 5400 s per run instead of 1500 s; `MODEL_TIMEOUT=<seconds>` overrides it. LM Studio:
`BOOKLOOM_EVAL_PROVIDER=lmstudio` with its `/v1` URL, or `--models "lmstudio:<id>"`.

*Fixture* (`modules/pipeline/src/test/resources/eval/sequence/`, no text from a real book). The 58-paragraph book of the first cut was
too easy: a 6 h run of 3,783 segments had 1.7 % of its segments fail a quote or script gate in the first round, 28 narrator
gender slips, 186 batch fallbacks in 722 batches and drifting terms, none of which 58 paragraphs can reproduce at those rates.
`book.md` is now *generated* by `SequenceBookGenerator` (test source, fixed seed, JDK only) from the original sentences and slots of
`SequenceBookLibrary` and `SequenceTopic`, and committed; `SequenceFixtureTest` checks that regenerating gives the same bytes.
After changing a pool, rewrite it with
`java -cp modules/pipeline/build/classes/java/test ua.bookloom.pipeline.eval.SequenceBookGenerator modules/pipeline/src/test/resources/eval/sequence/book.md`
(after `./gradlew :pipeline:testClasses`) and re-pin the counts of `SequenceEvalOfflineTest`. The book has 8 chapters, 328 paragraphs
(40 per chapter with the footnote line, 8 headings besides) and about 13,200 words; chapters 2, 5 and 7 are narrated in the first
person by a male spirit in the past tense, the other five in the third person. Densities of the committed file, pinned by the test:

| Feature | In the book |
|---|---|
| speech paragraphs (straight `"`, curly `“ ”`, nested `' '` / `‘ ’`, em-dash `—`, mixed) | about 145 (44 %); 16 mix straight and curly marks |
| long paragraphs of 80-140 words / short lines of 1-8 words | 46 (14 %) / 69 (21 %), 9 of them 40-60 characters like "And, a split second later, the explosion." |
| `Mr` / `Mrs` / `Ms` with surnames | 71 / 64 / 66 paragraphs |
| `master` / `imp` / `magician` / `boy` | 59 / 66 / 63 / 68 |
| `sir` / `pentacle` / `circle` | 27 / 29 / 32 |
| names with tempting spellings (Bartimaeus, Nathaniel, Underwood, Lovelace, Whitlock, Harrowgate, Quill), the stammered `B-Bartimaeus`, the hyphenated `Stoke-on-Marsh`, generic capitalised places (`Great Hall`, `Old Bailey`) | in narration and in speech |
| `[n]` footnote markers and footnote lines, `*emphasis*` / `**bold**`, Latin kept foreign, numbers and dates, three verses | 16 markers, 8 footnote lines, 14 Latin paragraphs |

`manifest.json` lists the chapters and their narrator, the glossary the run starts with (Nathaniel locked, Underwood unlocked, Lovelace
and Bartimaeus with no target) and, per term and name, the renderings and spellings that are counted (`master`'s list leaves out
`пан`, which a `Mr` in the same paragraph would otherwise claim). Three paragraphs are fixed anchors for the scripted model of the offline test.

*Narrator modes* (`BOOKLOOM_EVAL_NARRATOR`, reports `<model>-sequence-<mode>.{txt,json}`, the JSON field `narrator`). `unset`, the default,
writes a brief with **no narrator**, which is what the real run had: the style sheet carries no first-person rule and the Ukrainian
gender check is silent, so the male narrator's «я була / могла» slips through. `set` writes a first-person male narrator into the brief
(rule in the style sheet, check on, one directed fix per slip). `genderSlips` counts slips in the three first-person chapters in both modes,
so the difference between the two rows is what the narrator field buys.

*Metrics* (one table per model and mode):

| Metric | How it is read |
|---|---|
| `renderings/term`, `dominantShare` | per fixture term, the known renderings (inflected, as regular expressions) found in the targets of the paragraphs that name the term; the mean count of distinct ones over the terms rendered at all (1.00 is ideal) and, per term and as a mean, the commonest rendering's share (100 % is ideal; the real run's `master` was 143 майстер to 7 господар) |
| `nameVariants` | per name, the spellings seen (`бартімей` and `бартімеус`); the sum of those beyond the first |
| `genderSlips` | `GenderChecks` («я» + a past-tense word of the wrong gender) over the first-person chapters |
| `english` | targets still in the source language: `LanguageIdentityCheck` finding or a plain echo of the source |
| `flaggedNoTarget` | flagged segments with no stored target, which an export writes as the source (the 23-English-paragraphs bug, fixed by 15e.5) |
| `hardGateRound0` and `kinds` | segments whose first evaluation failed a hard gate (a blocking quote or script finding, a placeholder), read from the run's `Evaluated ... round=0 ... hardGatesPass=false` line; `kinds` counts the findings of their first repair round (`Round choice ... round=1`). The real run: 73 of 3,783 |
| `leakedProtocol` | final targets holding `"terms"`, `«terms»`, a code fence or a `{"id"` object (the real run: 4) |
| `reviewerTruncated` | reviewer replies the provider cut off at the output cap (`finish=LENGTH`), counted by a recording wrapper around the model |
| `termClaimedWrong` and `claimed` | lexicon terms whose learned rendering another term also claimed (`mr` and `mrs` both learning «пані»), with the shared renderings |
| `quote` / `ascii` / `mixed` | `TextChecks` blocking unbalanced-quote and mixed-script findings in the final targets (a soft "English quote marks" note is not counted); `ascii` counts targets that still hold a straight `"` (an eval-side count, the quote check does not read the kind) |
| `flag%`, `fallbk%` | flagged share; batch items that fell back to their own draft over batched items (reasons are `status/problems`, read from the run's own warning) |
| `calls`, `sec/seg`, `edits+`, `edits-` | finished model calls and wall seconds per segment; reviewer edits applied and refused |
| `learned`, `coverage`, `lexicon/t` | renderings the lexicon learned by co-occurrence, the share of those terms' occurrences carrying it (the `--report` lexicon section), the lexicon's own distinct renderings per term |

Offline (`check`, no model, under 20 s): `SequenceFixtureTest` (the committed book equals the generator's output; paragraph, word and density
counts; term and name counts; the narrator modes), `SequenceEvalOfflineTest` (the same code through the real job over `SequenceScriptedModel`,
which drifts `master`, `Mr` and `Bartimaeus` for the second half of the book, says «я була» once, echoes one paragraph in English and appends one
stray quote, and over hand-written runs for each defect class and the new metrics). No floor is asserted: 15e.4 records the "before" table
and the thresholds per model class from this suite.

Calibration on 2026-10-02 (one sample per case, temperature as in production):

| Prompts | Model | parse | gate | review separation | script | marker | injection |
|---|---|---|---|---|---|---|---|
| before (step 8g) | gemma4:e4b-mlx | 100% | 91% | 50% | 100% | 67% | 100% |
| after (step 9) | gemma4:e4b-mlx | 100% | 100% | 100% | 100% | 89% | 100% |
| after (step 9) | gemma4:e2b-mlx (floor) | 100% | 82% | 100% | 100% | 100% | 100% |
| after (step 10, with the 4 suggest rows) | gemma4:e4b-mlx | 100% | 100% | 100% | 100% | 92% | 100% |

**Model list.** `scripts/eval-models.txt` is the one list of models `scripts/eval-matrix.sh` runs (refreshed 2026-10-04 to what
`ollama ls` and `lms ls` show: gemma-4 e4b in every quant first, then e2b, 12b, 26b, gpt-oss, the two qwen3.8-27b builds, muse-glimmer
and the rest). `qwen2.5:1.5b` is kept only as a stub for API checks, never for translation. 

**Batch A/B (15d.8).** `scripts/eval-matrix.sh --suite batch [--batch-sizes 4,8,12,16] --models "ollama:<id> lmstudio:<id>"`
sends batches of consecutive draft cases through the JSON batch protocol and prints, per model and size: `idValid` (items
answered under their id exactly once), `tokGate` (also kept their tokens in order), `omit`, `merge` and output tokens per
item. It measures only; the batch size is chosen by reading the table (the tagged-block protocol it once compared was
dropped after the first A/B, see `12_PROMPT_CATALOG.md#batch-draft`). A run's own draft calls per segment are
`modelCallsByKind.DRAFT.attempts` over `run.segments` in the report JSON (a batch call and a single-segment fallback each count one).

**Reviewer eval (15d.6).** The same suite measures the reviewer that replaced the judge: each corpus case is sent through
`ReviewerCall` and its edits go through the production `EditApplier`. The table's `catch` is the share of defective
candidates the reviewer asked to change (thresholds: at least 90% on the e4b class, 95% on the 26b class), `falseAlarmEdits`
the share of clean ones it would have changed (at most 10%), `stability` the share of cases whose repeats ask for the same
change, and `tokenBreaks` the applied edits that changed a placeholder token (must be 0). One model, five repeats:

```bash
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_STABILITY=5 \
  BOOKLOOM_EVAL_ONLY=corpus ./gradlew :pipeline:promptEval          # e4b: the 19 corpus cases only
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:26b-mlx BOOKLOOM_EVAL_STABILITY=5 \
  BOOKLOOM_EVAL_ONLY=corpus ./gradlew :pipeline:promptEval          # the 26b class
scripts/eval-matrix.sh --models "ollama:gemma4:e4b-mlx ollama:gemma4:26b-mlx" --stability 5 --only corpus   # both, one table
```

Leave `BOOKLOOM_EVAL_ONLY` out to run the draft, fix and `review-*` pair cases as well; `--langs all` runs each language's
mini-corpus. Reports land in `modules/pipeline/build/reports/promptEval/<model>.txt` and `.json`.

**Corpus eval (15d.1).** `scripts/eval-matrix.sh [--models "ollama:<id> lmstudio:<id>"] [--stability N] [--only corpus]` runs the
prompt eval plus 19 labelled reviewer cases (`src/test/resources/eval/defects.json`: garbled word, mixed script, unbalanced
« », English left in, idiom, gender slip, lexical drift, omission, meaning, short lines) over Ollama and LM Studio and prints
one table. `falseNegative` is the share of defective candidates the reviewer left alone (one minus the defect catch rate), `falsePositive` the share of clean ones
it would have changed (false-alarm edits), `tokenBreaks` the applied edits that changed a placeholder token (always 0), `stability` the share of cases whose `BOOKLOOM_EVAL_STABILITY` repeats agree. Thresholds per model class are in
`eval/thresholds.json`. Env: `BOOKLOOM_EVAL_URL`, `BOOKLOOM_EVAL_PROVIDER=lmstudio`. 2026-10-03, one sample, stability 1:

| Model | judge FN before → after | FP before → after | other |
|---|---|---|---|
| gemma4:e4b-mlx | 38% → 25% | 0% → 0% | all draft rates 100% except marker 94% |
| gemma4:e4b-mxfp8 | 38% (not re-run) | 0% | |
| google/gemma-4-e4b (LM Studio) | 50% → 25% | 0% → 0% | gate 96% |
| gemma4:12b-mxfp8 | 12% → 0% | 0% → 9% | |
| gemma4:12b-mlx | 25% (not re-run) | 0% | |
| gemma4:26b-mlx | 12% (not re-run) | 0% | judge separation 75% |
| google/gemma-4-26b-a4b-qat (LM Studio) | 0% (not re-run) | 0% | every rate 100% |
| gemma4:e2b-mlx (floor) | 25% → 38% | 18% → 18% | gate 84%, judge separation 75% |
| qwen3.8:27b-mlx | 25% (not re-run) | 0% | |
| qwen/qwen3-vl-4b (LM Studio, no longer installed) | 88% (not re-run) | 0% | judge accepts nearly everything |
| muse-glimmer:30b-nvfp4-dflash | 12% (not re-run) | 9% | script 48%, gate 84%, injection 50% |
| gpt-oss:20b (Ollama) | n/a | n/a | parse 6%: `emptyCompletion` — Ollama's `think:false` is ignored by gpt-oss, the cap is spent on reasoning (needs an `:llm` fix) |
| qwen/qwen3-4b-2507 (LM Studio, no longer installed) | n/a | n/a | run hung over 49 minutes and was killed |

**Draft (translation) eval, 2026-10-03.** The 26 draft/fix cases plus six new ones (balanced « », he/she agreement, idiom,
short line) run for every model. gpt-oss:20b was failing on Ollama's native endpoint (`think:false` is ignored, the cap
went to reasoning, `emptyCompletion`): the client now asks a gpt-oss model for `think:"low"` and adds 1024 tokens of
reasoning headroom to its cap (`OllamaClient.thinkControl`, `withReasoningHeadroom`). The draft user prompt ends with a
reminder that the target is the target language, never a copy of the source and never "...".

| Model | parse | gate | script | marker | injection | note |
|---|---|---|---|---|---|---|
| gpt-oss:20b | 6% → 95% → 97% | 8% → 94% → 97% | 7% → 91% → 97% | 6% → 82% → 86% | 100% | fix, then prompt |
| muse-glimmer:30b | 100% | 84% → 87% | 54% → 74% | 68% → 77% | 50% | still copies source on some cases |
| gemma4:e4b-mlx | 100% | 100% | 100% | 96% | 100% | unchanged |
| gemma4:12b-mxfp8 | 100% | 100% | 100% | 100% | 100% | unchanged |
| gemma4:26b-mlx | 100% | 100% | 100% | 86% → 96% | 100% | judge separation 50% |
| gemma4:e2b-mlx (floor) | 97% | 87% | 97% | 73% | 100% | |
| google/gemma-4-e4b (LM Studio) | 100% | 97% → 94% | 100% → 97% | 91% → 86% | 100% → 50% | one sample; possible noise |

**New LM Studio models, 2026-10-04** (current prompts, one sample): `google/gemma-4-e2b` parse 97%, gate 94%, script 97%, marker 86%,
judge false-negative 38% / false-positive 9% (an e2b floor like the Ollama build). `qwen/qwen3.8-27b` parse, gate and script 100%, marker 86%,
judge false-negative 25% / false-positive 0%. One LM Studio model that was tried and excluded (weak Ukrainian drafts, parse 83%, obeys instructions hidden in the book text) also stalled on the
judge call: with the judge's JSON-schema `response_format` (with `maxItems`/`maxLength` limits) LM Studio never answered (100 s, model stuck in
`PROCESSINGPROMPT`), while the same prompt without a schema answered in 5 s and the simple draft schema worked. Keep the reviewer schema of
task 15d.6 flat and free of `maxItems`/`maxLength`, and consider dropping `response_format` for a model after a structured call timed out.

The other models (12b-mlx, e4b-mxfp8, qwen3.8:27b, gemma-4-26b-a4b-qat, qwen3-vl-4b) were measured before the reminder
and not re-run. Marker rates also moved because two over-strict markers were loosened. qwen3-4b-2507 is excluded from
the default matrix (it hung); `MODEL_TIMEOUT` (default 1500 s) now stops any model that stalls.

"After" is the judge prompt with a defect checklist (garbled word, mixed script, unbalanced quotes, name drift, gender
agreement, omission). The misses that remain on the 4B class — mixed script, unbalanced quotes, drift — are the
deterministic checks of 15d.2.

Before, the judge scored an untranslated English candidate 1.0, a pair was moved off its words and a locked name was
written out instead of its token; after, the one miss on e4b is the drop cap (`⟦g0⟧Т⟦g1⟧іч` — the source letter kept),
and e2b drops pairs, which the run's placeholder gate refuses and sends to repair. The suggestion rows read `Елеонора Венс`, `Гарроу Вейл`,
`Інститут Меридіанського зондування` and `Амулет`; the first prompt came back with `Вeнс` (a Latin `e`, now put back as
Cyrillic) and every place and thing as `neuter` (a suggested gender is now kept for characters only).

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

## Final audit (15d.12) {#final-audit}

After a run completes, the app checks every accepted segment again with the cheap deterministic checks and lists the
doubtful ones as "suspicious" (outcome card, the review panel's filter chip, `<name>.report.md`, and the `audit` object
of the command's `--report` JSON; the command prints one `Audit:` line when something is listed). To audit a book that was
already exported, with no model and no project, run the local-only tool against the source and the exported book:

```bash
BOOKLOOM_AUDIT_SOURCE=<source book> BOOKLOOM_AUDIT_EXPORT=<exported book> [BOOKLOOM_AUDIT_GLOSSARY=<glossary.csv>] \
  [BOOKLOOM_AUDIT_FROM=en] [BOOKLOOM_AUDIT_TO=uk] ./gradlew :pipeline:corpus --tests '*ExportedBookAuditTool*' -i
```

It prints `AUDIT <segment id> <locator> [checks]` lines, and `AUDIT-NAME` lines naming the glossary words a segment
lost. It treats every written segment as accepted, so segments the run had flagged are listed too.
