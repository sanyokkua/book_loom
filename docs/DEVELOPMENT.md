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
| Liberica 25 "Full" (`jdk+fx`) | packaging on **Windows** only — a plain JDK there lacks the JavaFX jmods `jpackage` needs; never on macOS or Linux, whose module-path image refuses a JavaFX-linked `java.base` | <https://bell-sw.com/pages/downloads/> |

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
| `:persistence` | `modules/persistence` | real | in-memory adapters behind every `:api` storage port (ADR-0034; command line, tests) and, built by `finalize-ui-and-persistence`, SQLite + JDBI adapters for the window (ADR-0043; Flyway only from the first release); `RepositoryContractTest` runs against both |
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

**Provider keys reach `:app:run` too.** A Gradle daemon started before you exported a key never sees it, and an IDE or a
Dock launch never sees a shell's `export`. BookLoom's one lookup (`ua.bookloom.app.SystemEnvironmentVariables`) reads
the process environment, then the user's OS store — `launchctl getenv` on macOS, the registry user and machine
environment on Windows, `environment.d` and `/etc/environment` on Linux — at call time, so `launchctl setenv NAME
value` works without restarting the daemon or the window. How to set a key per OS: the README's
[Provider keys](../README.md#provider-keys).

**What the window can do.** The whole workflow, in five numbered steps and Settings. *Import*: open a book of any of the
four formats (EPUB, FB2, Markdown, TXT); the card reports its format, declared language, cover and chapter count, refuses
a DRM-protected or unsupported file, and shows the book's structure tree and its round-trip check. *Book Brief*: source
and target language (any language the JDK can name, or Auto for the source), tone and style, the name, foreign-passage,
footnote and unit policies, who narrates, the quality dial (Fast, Balanced, Max), the "Also translate" switches and a
file name the model can suggest. *Names & style*: the glossary and the recurring terms, each with search, Scan, Review
with model, Translate and CSV import and export, or one "Prepare names automatically" pass with Undo all. *Translating*:
start, pause, resume, stop; a live panel with every model call, the activity log, the provider-error banner with Retry
now and Skip segment, and the review panel — accept, edit, revert, skip, apply a proposal, retry with a note — inside
the same screen. *Export*: Save to, the side files (glossary CSV, bilingual HTML, quality report), a consistency pass,
and the export-complete dialog with a button that shows the written file in the file manager; a stopped or partial run
can be exported, and what it did not translate stays in the source. Verify a provider on Settings (Providers: connection,
models, then a test inference, each measured), choose a model, and start. Pause and Stop abort the request in flight.
Only one translation exists per application run: importing a book while a run can continue asks whether to discard it.

**The review mode is a launch flag**, because nothing is saved yet. `BOOKLOOM_REVIEW_MODE`, else the system property
`bookloom.review.mode` (for `:app:run`, hand it to the launched JVM with `JAVA_TOOL_OPTIONS="-Dbookloom.review.mode=manual"`),
takes `unattended` (also `auto`; the default — nothing pauses), `assisted` (also `semi-manual` — the run pauses on a
flagged segment and on an error) or `manual` (pauses after every segment as well). A value that names no mode falls back
to `unattended` and is named once in the log. The command line always runs unattended. The mode is shown on the
Translating screen's ready card and in the session facts of the detailed log.

**What it cannot do yet.** Persist anything across a restart (`:persistence` binds every repository port to an
in-memory adapter, ADR-0034, so a run cannot be resumed after a restart and the theme, provider, model and brief are not
remembered; ADR-0043 moves the window to SQLite), switch the interface language inside the app, open the Projects entry (greyed, no screen), or use the
Settings tabs Models, Generation, Automation and Storage.

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
`ERROR`, any letter case), else the default: `INFO` for an installed (`prod`) app, `TRACE` for a development run.
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
`-Dbookloom.trace.file=true`; `0`/`false` switches a dev run off) turns it on for that launch; the durable way is the
Settings › Storage & logs switch, which writes the marker file `detailed-log.on` in the data folder and applies from the
next launch (the variable or property still wins over it, and a development run writes the log whatever the switch says). `bookloom.log`, the console and their level are unchanged; third-party
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
3.6). Beside it, **`bookloom-trace-evidence.log`** (100 MB, rolled into `bookloom-trace-evidence.1.log.gz` … `.4.log.gz`;
a 9-hour run wrote about 90 MB of evidence, so the cap holds a whole book's flags and drops the oldest content first)
keeps the full `TRACE` of every segment that ended **flagged** or went through **repair**, for the whole run: each
segment's lines are held in memory (at most 64 segments of 400 lines and 8 MB in all) until the pipeline's decision line carries the
`EVIDENCE_KEEP` marker, then written with the segment's later lines; a segment accepted at once is forgotten. It is on and
off with the detailed log, and the diagnostic bundle includes it. To read why `part0044.html:16` was flagged, grep that
segment key in the evidence file first.

**The run log.** The ordinary log at `DEBUG` also loses a long run's start (a 9-hour run kept its last 8 hours).
**`bookloom-trace-run-<yyyyMMdd-HHmmss>.log`** (the launch time) is written on every launch, detailed log or not. It holds
the BookLoom lines at `INFO` and above for that launch — the names pass, the brief, run summaries, flags, failures — plus
the `WARN`/`ERROR` lines of other libraries, and (with the detailed log on) the same `#` session header. It is never rolled and never sees `DEBUG` or `TRACE`, so it holds no book text and stays small; the last
10 launches' run logs are kept (the oldest name is deleted at the next launch; a failed delete is one `WARN` line), and the diagnostic bundle includes them. Read it first to see
how a run went, then the evidence file for why one segment was flagged.

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
upper-cased — and writes `<name>.<to><suffix>` beside it; `--to` defaults to `uk`. Without `--from` the source language is the one the import preselected (the book's declared language, else Auto); `--from auto` asks the model to identify it even for a book that declares one. `--provider ollama` and
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

Scroll events reach the FX thread a couple of hundred times a second on a trackpad, so the scroll code logs one DEBUG line a
second (`scroll: N events, X px in, Y px out, max gap Z ms, D target detached`) and nothing per event. `D` counts the
events of a gesture whose node was replaced mid-gesture (a rebuilt row, a swapped panel): JavaFX keeps firing them at
the gone node, and `GestureRescue` hands them to the nearest scroll pane or list still on screen. To capture the raw events for a bug
report (every event's `deltaX`, `deltaY`, inertia and units at TRACE, a line each), launch with
`JAVA_TOOL_OPTIONS="-Dbookloom.log.scroll=true"` and the trace log on; expect the log itself to cost frame time then. A
trackpad's vertical swipe carries a pixel or two of sideways noise; the larger of the two parts decides the direction,
and only the vertical part is applied (`SmoothScrollReplayTest` replays such a gesture against hand-driven pulses).
What a list cannot take of a frame's pixels goes on to the pane around it, and a list that moved zero is passed over
in that direction until it moves (`GestureRescueTest`).

To measure a trackpad hand run (15h.D2's targets), launch with `BOOKLOOM_LOG_LEVEL=DEBUG`, swipe for ten seconds on
Translating during a run and on Names & style, and read the `scroll:` lines of `ua.bookloom.ui.control.ScrollStats`:
`N events` per one-second line is the event rate (target 150 or more while the fingers move), `max gap` the longest
pause between events (target under 40 ms), and a gesture is cut when its lines stop while the fingers still move — with
`D target detached` above zero, the rescue caught a gesture that would have been cut.

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

### Quality round 2: after the Phase 2 fixes (15e.5–15e.12) {#15e-after2}

Same eval, models and window as the "before" table above; each cell is before → **after**. The fixes: keep the usable
draft and repair quotes instead of exporting English (15e.5), leaked protocol text (15e.6), reviewer truncation (15e.7),
compact-line length (15e.8), name-missing precision (15e.9), honest counts (15e.10), target-alphabet check and key-term
noise (15e.11), lexicon precision (15e.12).

| metric | e4b unset | e4b set | 26b unset | 26b set |
|---|---:|---:|---:|---:|
| Flagged of 328 | 19 → **17** | 15 → **12** | 12 → **7** | 18 → **4** |
| Flagged with no stored target | 2 → **0** | 2 → **0** | 7 → **0** | 11 → **0** |
| Hard-gate failures at round 0 | 12 → **8** | 16 → **8** | 36 → **5** | 31 → **4** |
| Narrator gender slips | 1 → **0** | 0 → **0** | 16 → **8** | 0 → **0** |
| Dominant rendering share | 0.87 → **0.89** | 0.87 → **0.87** | 0.92 → **0.90** | 0.92 → **0.90** |
| Distinct renderings per term | 1.8 → **2.1** | 1.8 → **1.9** | 1.6 → **1.6** | 1.5 → **1.6** |
| Name spelling variants | 3 → **3** | 2 → **3** | 2 → **2** | 1 → **2** |
| Batch fallback rate | 0.01 → **0.02** | 0.01 → **0.01** | 0.07 → **0.07** | 0.05 → **0.09** |
| Calls per segment | 0.35 → **0.35** | 0.36 → **0.33** | 0.62 → **0.44** | 0.48 → **0.54** |
| Seconds per segment | 3.8 → **3.7** | 4.0 → **3.6** | 6.3 → **5.0** | 5.6 → **5.2** |

Reading it: segments flagged without a stored target (the 23 English paragraphs of the 6-hour run) are 0 in every run;
first-round hard-gate failures fall by 33–86 %; leaked protocol text and truncated reviewer replies are 0. Gender slips
with the narrator unset are the open problem on 26b (8; 0 when the brief names the narrator), and recurring terms still
split (dominant share 0.89–0.91, 1.6–2.1 renderings per term): the next task, prompting for consistency (15e.13/15e.14).

| realrun kind / call | e4b | 26b |
|---|---|---|
| `short-line/draft` | 100 % (6/6) | 100 % (6/6) |
| `short-line/review` | 33 % (2/6) | 50 % (3/6) |
| `russian-letters/draft` | 100 % (3/3) | 100 % (3/3) |
| `russian-letters/review` | 40 % (2/5) | 80 % (4/5) |
| `invented-word/review` | 0 % (0/3) | 100 % (3/3) |
| `batch-terms/batch` | 100 % (1/1) | 100 % (1/1) |
| `reviewer-long/review-batch` | 92 % (11/12) | 92 % (11/12) |
| `repair/repair-placeholder` | 50 % (2/4) | 100 % (4/4) |

### Quality round 3: prompting for consistency (15e.13) {#15e-after3}

Same eval, window and dial as the tables above. Each lever was one change, one e4b sequence run (narrator unset, 20 min);
the e4b realrun is deterministic (the same 17 groups, reviewer batches ±1 case) so one run decides it. The sequence eval
is not: three identical runs of the baseline gave 4.3 / 4.6 / 5.2 % flagged, 3 / 6 / 8 first-round hard-gate failures,
88.2 / 88.1 / 89.4 % dominant share and 1.9 / 1.9 / 2.1 renderings per term, so a move counts only beyond about 1.5
points of dominant share, 0.2 renderings per term, 2 points of flagged and 3 hard-gate failures.

| round | lever | e4b unset: flagged / hard0 / renderings per term / name variants / dominant / fallbacks | decision |
|---|---|---|---|
| 0 | baseline (twice, today's code) | 4.3, 4.6 % / 3, 6 / 1.90, 1.90 / 3, 3 / 88.2, 88.1 % / 1.8, 1.5 % | |
| 1 | the first rendering stays (`retained`) and a name with no target learns its spelling | 3.0 % / 4 / 1.80 / 2 / 87.9 % / 2.1 % | kept; `Ms → коли` (a function word) learned, so round 3 |
| 2 | a rendering must not be overtaken by an unrelated word; a name's competitors count every case | 3.0 % / 0 / 1.60 / 1 / 90.2 % / 2.4 % | kept |
| 3 | stop words of the target language are never a rendering | 5.8 % / 8 / 1.70 / 2 / 90.6 % / 1.5 % | kept (noise in flagged and hard0; `коли` gone) |
| 4 | term blocks (glossary, suggested, established) moved next to the text, "exactly as shown" | 2.7 % / 4 / 1.60 / 1 / 89.5 % / 0.6 % | **rejected** after the 26b check below |
| 5 | a title is read with its capitals and a name never takes a word that renders a term (`Lovelace → Міс`) | 5.5 % / 2 / 1.90 / 1 / 87.0 % / 0.6 % (with round 4) | kept (the wrong suggestion is gone; dominant share is noise on `magician`, 30/30) |
| 6 | reviewer: no noun swap for gender, a clean agreement example instead of the idiom | realrun unchanged on every group | rejected |
| final | rounds 1-3 and 5 | 5.5 % / 4 / 1.70 / 2 / 90.6 % / 2.7 % | |

What moved. Names: `Bartimaeus` has one spelling in 6 of 7 runs where the baseline split it 25/13 and 24/21 (the learned
spelling is stored as a suggestion and the model follows it in 50-56 of 56 segments); name variants fall from 3 to 1-2 on e4b and
from 2 to 0-1 on 26b. Terms the model renders one way first (`master`, `Mr`, `Mrs`, `Ms`, `sir`) stay at 100 % — before,
`sir` was 77-100 % and `master` 98 % because the hint came and went. The learner no longer learns `коли` for `Ms` (rounds
1-2 did; round 3 stopped it), and a title before a recurring surname is never that name's spelling (`Lovelace → Міс`, found in round 4).
What did not move: terms the model splits from the first occurrence (`magician` маг/чарівник 47-64 %, `pentacle`
пентакл/пентаграма 52-67 %) never reach three matching segments before the rival does, so there is nothing to keep; the
only ways left are the model's own `terms` report (e4b does not give it) or choosing a winner with no evidence, which
would also pick a sense of a word with two meanings.

Rejected: moving the term blocks next to the text (round 4). On e4b it cut batch fallbacks from 1.5-2.4 % to 0.6 % in
three runs; on 26b, one run each, it raised them from 9.7 % to 17.5 % (50 batches with a missing id against 26), calls per
segment 0.51 to 0.77 and seconds per segment 5.7 to 6.9. 26b is the model of the owner's real run, so the layout stays
as it was.

| 26b unset | before (15e-after2) | after |
|---|---:|---:|
| Flagged of 328 | 7 | 6 (1.8 %) |
| Hard-gate failures at round 0 | 5 | 9 |
| Narrator gender slips | 8 | 10 (random, 0-16 between identical runs; 0 only when the brief names the narrator; 26b with the narrator detected, i.e. the Start question answered, was not measured) |
| Dominant share / renderings per term | 0.90 / 1.6 | 0.91 / 1.7 |
| Name spelling variants | 2 | 0 |
| Batch fallback rate | 0.07 | 0.10 |
| Calls / seconds per segment | 0.44 / 5.0 | 0.51 / 5.7 |

e4b with the narrator detected (final code): flagged 3.4 %, gender slips 0, dominant share 90.8 %, 1.8 renderings per term, 3 names with a stray spelling (`Bartimaeus` 54 of 56 in one, `Whitlock`, `Harrowgate`), fallbacks 1.8 %, 3.56 s per segment. Not measured in this round (time): e4b with the narrator set, 26b with the narrator detected. The realrun table is the same
as above on e4b and 26b: the prompt levers did not touch the reviewer's weak groups. Two of them are corpus artefacts:
the `short-line` "good" lines are compact translations that leave words out, so a reviewer that adds them is right, and
`russian-letters`/`mixed-script` good twins draw `gender` edits that swap a noun (`Стара брама → Старий замок`), which
a model of e4b's size makes whatever the prompt says.

### Quality round 2: final numbers {#15e-final}

Same eval, models and window as the tables above (Ollama native, Balanced, 328-paragraph sequence fixture). Each cell is
the "before" table → after Phase 2 → final (after prompting rounds, the narrator question and the review fixes, commit
2b852b0). The sequence eval has run-to-run noise (about ±1.5 points of dominant share, ±3 first-round hard-gate failures,
flagged 12–20 on e4b between identical runs), so read single cells as ranges.

| metric (sequence) | e4b, narrator unset | 26b, narrator unset | 26b, narrator detected (Start answer male) |
|---|---:|---:|---:|
| Flagged of 328 | 19 → 17 → 20 | 12 → 7 → — | — → — → **4** |
| Flagged with no stored target (English in the book) | 2 → 0 → **0** | 7 → 0 → — | 0 (after Phase 2 set mode) → **0** |
| Hard-gate failures at round 0 | 12 → 8 → **0** | 36 → 5 → — | — → — → **2** |
| Narrator gender slips | 1 → 0 → 0 | 16 → 8 → — | — → — → **0** |
| Dominant rendering share | 0.87 → 0.89 → 0.87 | 0.92 → 0.90 → — | **0.92** |
| Name spelling variants | 3 → 3 → **1** | 2 → 2 → — | **0** |
| Leaked protocol text / truncated reviewer replies | 0 / 0 | 0 / 0 | 0 / 1 (re-asked, nothing flagged) |
| Calls / seconds per segment | 0.35 / 3.8 → 0.34 / 3.75 | 0.62 / 6.3 → 0.44 / 5.0 | 0.54 / 5.7 |

Real-run corpus (final, no regression against the "after Phase 2" cells): quotes/draft 100 % e4b, 89 % 26b;
russian-letters/draft 100 % both (the target-alphabet check fixes them in the run); leaked 0 %, too short 0 %,
truncated 0, stability 100 % / 98.5 %.

What is still open, measured: `magician` and `pentacle` stay split (50–65 % one rendering) because the model splits them
from the first occurrence; the reviewer edits clean short lines (short-line/review 33 % e4b, 50 % 26b), misses invented
words on e4b (0 %), and 26b batches still fall back on missing ids (10 %). Speed work (a larger window and
re-asking only missing ids, task 15e.15) is not started.

### Quality round 3: the Burning Chrome review (15f) {#15f-results}

The owner's 26b run of *Burning Chrome* (Balanced, 21 min, 229 segments) showed a dropped last sentence, a lost vocative name, 10 false "suspicious" audits of 11 and a consistency pass that changed nothing. After 15f.A0–A9, the same book on `gemma4:e4b-mlx` (Ollama, 16k window), measured by `build/…/run1` and `run2` of one session:

| | 26b, before (owner) | e4b, Balanced | e4b, Max |
|---|---|---|---|
| time | 21 min | 14 min 53 s | 27 min 29 s |
| accepted / flagged | 228 / 1 | 228 / 1 | 225 / 4 |
| suspicious (audit) | 11 (10 false) | 1 | – |
| "We were partners." (ch4 p14) | missing | present | – |
| vocative *Finn* (ch4 p26) | missing | present | – |
| paragraphs ending `.»` (wrong attribution) | 22 of 217 | 1 of 206 | – |
| consistency pass, paragraphs corrected | 0 (byte-identical export) | – | 35 of 91 checked |

The models differ, so the time column compares nothing about speed; it shows the new run costs no more than the old one. What the Max run's 35 corrections were, read from the TRACE log: narrator gender (шукала, знайшла, щасливий), a wrong ending (дивитеся → дивишся), a lower-case sentence start, a doubled full stop after a closing quote — and one doubtful swap (база даних → вежа даних). Not measured: the 26b with the new checks, the Russian→Ukrainian book, and the speed effect of the 16k window alone (15e.15 asks for a first-300 A/B).

### Time to first token {#time-to-first-token}

The time before a call's first token (the provider's prompt evaluation, "prefill") is read from the provider's own
timings: Ollama's `prompt_eval_duration`, LM Studio's `stats.time_to_first_token`. A provider that sends neither leaves
the figure unknown (`-`), never zero. Read it in the final `run summary final ... byKind[...]` line of the log, per call
kind: `ttftMs:` is the mean over the calls that reported it and `prefill:` is its share of those calls' wall time. The
unattended command line's JSON report carries the same as `meanFirstTokenSeconds` and `prefillShare` under
`modelCallsByKind`.

The 10 % rule (15h.C5) was met: the owner's 26b Max run (2026-10-16) spent 41–60 % of each kind's call time in
prefill. `cached:` is the prompt tokens the provider served from its cache: Ollama's `prompt_eval_cached_count` (read
since 2026-10-17; before, the run summary said `cached:0` while Ollama's own server log showed 57 % of that run's prompt
tokens and 49 % of its export pass cached), LM Studio's `usage.prompt_tokens_details.cached_tokens`. On Ollama's MLX
runner `prompt_eval_count` is the whole prompt and `prompt_eval_duration` only its uncached part.

The reorder was tried and reverted (2026-10-17). Moving the draft and batch system prompts' token rules (present only
when a segment has `⟦gN⟧` tokens) to their end let two no-token drafts share the system prompt on `gemma4:e4b-mlx`
(478 of 579 tokens cached, prefill 1,091 → 263 ms; a repeat of the same case is served whole either way, 806 of 810),
but the first call after a with-token draft reused nothing, because Ollama's MLX runner caches a branch point only from
the second call that shares it — and the sequence suite lost token fidelity: batch items that dropped a `⟦gN⟧` token
rose from 4 to 16 and batch fallbacks from 15 to 29 against the same tree without the reorder. On e4b the rules work
better before the style sheet, so they stay there; prompt layout for the cache moves to `redesign-translation-pipeline`,
whose one context builder decides the order of every call's blocks.

### 15i baselines: the 15h prompt round and the book-neutral re-baseline {#15i-baselines}

All rounds 2026-10-12/13 on Ollama `gemma4:e4b-mlx` and `gemma4:26b-mlx`, one run per arm, Balanced, the suites of
`scripts/eval-matrix.sh`; 1-case moves are within noise.

**15h.C1–C4 (one combined round, before e8247cf0 → after 28e34e45).** realrun (172 shared cases): e4b 135 → 138, 26b
144 → 148. Default set: e4b script −1, injection −1 of 2; 26b gate −1, script −1, marker +1. Sequence (328
paragraphs): draft prompt tokens −9 % (e4b) and −10 % (26b), calls per segment 0.372 → 0.363 and 0.643 → 0.607, 26b
directed fixes 47 → 37. Kept: the target moved +3/+4 and no key metric lost 2+. The four changes were measured
together, so their effects are not separable.

**15i.E0 re-baseline (28e34e45 → cceac266: invented names in the prompts' examples, and E1–E4's test material).**
realrun on the shared cases: e4b 138 → 136, 26b 148 → 146 (slang, vocative/review and one placeholder repair −1 each).
Sequence: e4b name variants 3 → 1, seconds per segment 3.86 → 4.17; 26b calls per segment 0.607 → 0.457, flagged 7 → 9.
These are the baselines every later 15i prompt change is measured against; the drop is the cost of examples that no
longer resemble the books the suites were built from.

**15i.E1 names baseline (25cd1699)** is the "base15i" row of the names gate (`#15i-names-gate`).

### 15i brief prediction {#15i-brief}

The 15i.B4 gate round (2026-10-13, Ollama, `gemma4:e4b-mlx` and `gemma4:26b-mlx`, 16k window, server sampling) ran
`scripts/eval-matrix.sh --suite setup|stability|gold` with `BOOKLOOM_EVAL_BOOK=all`: **setup** is the 15 committed
setup cases (8 invented title/author pairs, 4 public-domain classics, a book with no metadata and two plain cases);
**books** is the 9 committed judgement books (stability suite, seed 1 of 3) plus the owner's four gold books ("owner
book 1–4", `BOOKLOOM_CORPUS_DIR`, Fast dial as every judgement round before). A cell is cases right of cases scored.
The baseline is 15h.G1's two samples of three windows (`25cd1699`); "after B4" is the merged 15i.B2–B4 code
(`166f44f9`) before this round.

| model | arm | setup genre | setup register | setup narrator | knowledge: invented unknown | knowledge: classics known | books genre | books register | books narrator | books narrator gender | genre of the six |
|---|---|---|---|---|---|---|---|---|---|---|---|
| e4b | baseline | 1/15 | 9/15 | 13/15 | 11/11 | 4/4 | 0/13 | 12/13 | 11/13 | 8/13 | 0/6 |
| e4b | after B4 | 3/15 | 11/15 | 14/15 | 11/11 | 4/4 | 1/13 | 7/13 | 11/13 | 9/13 | 1/6 |
| e4b | round 1 | 5/15 | 10/15 | 15/15 | 11/11 | 4/4 | 1/13 | 6/13 | 13/13 | 9/13 | 1/6 |
| e4b | round 2 | 7/15 | 10/15 | 15/15 | 11/11 | 4/4 | 3/13 | 13/13 | 13/13 | 9/13 | 2/6 |
| 26b | baseline | 1/15 | 9/15 | 15/15 | 11/11 | 4/4 | 0/13 | 12/13 | 11/13 | 9/13 | 0/6 |
| 26b | after B4 | 7/15 | 7/15 | 14/15 | 11/11 | 4/4 | 0/13 | 13/13 | 11/13 | 9/13 | 0/6 |
| 26b | round 1 | 8/15 | 11/15 | 15/15 | 11/11 | 4/4 | 6/13 | 8/13 | 13/13 | 9/13 | 4/6 |
| 26b | round 2 | 8/15 | 11/15 | 15/15 | 11/11 | 4/4 | 6/13 | 12/13 | 13/13 | 9/13 | 4/6 |

"Knowledge: invented unknown" counts the 8 invented books and the three other cases that must stay unknown; the 15i.B2
gate (unknown on 7 of 8 invented books, the 4 classics known) holds on both models in every arm. "Genre of the six" is
the gate's set: the four owner books and the two judgement books whose gold genre is itself one of the 40 names
(`female-narrator-address`, a gothic novel, and `straight-quoted-speech`, an adventure); the other seven judgement
books are short invented vignettes whose gold ("rural drama", "pastoral drama", "historical drama") no listed genre
name says, so they are scored but not part of the gate.

What each round changed, and why (from the TRACE logs of the earlier round):

- **Round 1.** (a) Knowledge title: a store's title carries the series ("… (Series Volume 1) (Series Trilogy)") or an
  edition word (", Complete"); 26b echoed the bare title and the answer was refused as `title-mismatch` on owner books
  1 and 4. A title of two words or more that is the whole-word start of the other now matches. (b) Narrator: the
  detector calls a book `MIXED` when any judged chapter is first person, and the brief read every mixed book as first
  person; a third-person novel with a first-person preface (1 of 40 chapters) or a few characters' written accounts (5
  of 24) became `FIRST`. A mixed book is now first person only when at least a quarter of its judged chapters are (first set at a half, which a fixture of 3 chapters in 8 failed)
  (`NarratorHint.judgedChapters`). (c) Genre and audience: 26b left their `evidence` empty ("empty for a neutral value"),
  so every window's genre and audience were refused and the synthesis had nothing to decide from; the prompt now says
  a genre or an audience is never neutral and needs the passage's own words. Genre on 26b 0 → 6 of 13; but register on
  26b fell 13 → 8, because the windows now quoted characters' speech ("Close the gate…", "…ma'am?") as `casual`.
- **Round 2.** Register is the narration's: the prompt says so and names what makes narration formal or casual, and
  `BriefReplies` keeps a register reading only when its quote stands outside the characters' speech
  (`BookSample.holdsInNarration`). e4b had called plain narration "formal" on seven books (books register 6 → 13); 26b
  books register 8 → 12. The genre and audience evidence may not be the title or the author (e4b quoted the metadata's
  title, which the code refused): e4b genre 1 → 3 of 13 books, 5 → 7 setup cases.

No third round was needed: against the baseline no field lost a case on either model, and every target moved by 2+.

Time per suggestion (first knowledge call to the decided brief, from the logs, 26b, owner books 1–4): Fast 37–51 s
(mean 42 s); **Balanced (`BOOKLOOM_EVAL_DIAL=BALANCED`, 4 windows of 2.5k tokens) 59–73 s (mean 64 s)**, under the 90 s
gate; the Balanced run also kept genre, register and narrator right on all four owner books. The narrator's gender stays
unknown on owner books 1 and 2, whose gold is male: no window found a line where someone names or addresses the
narrator that the code could verify, which is the intended refusal, not a miss to tune away.

### 15i translation rounds: T2, T3 and T6 {#15i-translation-rounds}

Rounds on `gemma4:e4b-mlx` and `gemma4:26b-mlx` (Ollama, server sampling, Balanced), one run per suite, realrun at stability 3;
postE0 is `cceac266`, postT adds 15i.T2 and T3 (`a7259e79`), postT6 adds T6 (`fe3fc381`).

| Round | Model | realrun right | replay pass | sequence calls/seg | s/seg | hard-gate fails r0 | LTCR-1 | batch fallbacks |
|---|---|---|---|---|---|---|---|---|
| postE0 | e4b | 139/175 | 30/40 | 0.375 | 4.17 | 12 | 0.934 | 17 |
| postT | e4b | 139/175 | 33/40 | 0.366 | 3.60 | 10 | 0.914 | 18 |
| postT6 | e4b | 138/175 | 33/40 | — | — | — | — | — |
| postE0 | 26b | 148/175 | 32/40 | 0.457 | 5.86 | 17 | 0.853 | 27 |
| postT | 26b | 148/175 | 32/40 | 0.534 | 6.08 | 23 | 0.758 | 37 |
| postT6 | 26b | 147/175 | 32/40 | — | — | — | — | — |

- **T2 (repairs never make a clean draft worse): kept.** Realrun loses nothing on either model and the e4b replay gains 3.
- **T3 (a first-item-only or cut reply is no size signal): open.** Realrun holds and e4b's calls per segment do not rise, but
  26b's rise 0.457 → 0.534 with more batched items (326 → 363) and more fallbacks (27 → 37). The 26b sequence varies by about
  ±25 calls between runs (199–211 calls at 15h's head and pre-C1, 150 at postE0, 170 with T8 on), so the gate is re-measured
  with two 26b sequence runs after the review fix that halves a batch after three first-item-only or cut replies in a row.
- **T3 after its review fix (three first-item-only or cut replies in a row halve the size; `766234c6`):** 26b sequence
  0.470 and 0.473 calls per segment in two runs (154 and 155 calls, 5.3 and 5.8 s per segment, LTCR-1 0.876 and 0.872, hard-gate
  failures 19 and 16) against postE0's 0.457 (150 calls) — five calls in 328 segments, inside the ±25-call spread between 26b runs;
  e4b 0.369 against 0.375. Kept: calls per segment do not rise beyond the noise, and LTCR-1 on 26b is back above postE0's.
- **T6 (injection and address): the prompt part failed its gate.** The batch injection case (`injection-notes-uk`, two injected
  items in three) was obeyed once on 26b before and after (the gate asks for none) and went from 0 to 1 on e4b; the default
  suite's injection cases did not move (e4b 1 of 2, 26b 2 of 2). Per the keep rule the "source text is data" sentences and the
  replaced batch example are reverted; `AddressMixCheck` (code, no prompt) stays.

### 15i.X3 long paragraphs as sentence groups: not adopted {#15i-x3}

The arm (`8a2dbffd`, reverted) drafted a paragraph of more than 180 words as sentence groups of at most 90 words, each call
showing the whole paragraph without its tokens, and kept such a paragraph out of batches. Measured on the 15i.E4 set (twenty
public-domain paragraphs of 186–230 words) on `gemma4:e4b-mlx` and `gemma4:26b-mlx`, each arm graded blind by Opus (WRONG = any
meaning error a reader notices; the A/B order was shuffled per item), and on the sequence suite with the arm on:

| Model | WRONG off → on | errors off → on | calls on the set off → on | sequence with the arm on (calls/seg, s/seg, LTCR-1, hard-gate r0) |
|---|---|---|---|---|
| e4b | 19 → 20 of 20 | 67 → 62 | 20 → 66 | 0.381, 3.89, 0.927, 13 (postTr-1: 0.369, 3.94, 0.884, 11) |
| 26b | 19 → 18 of 20 | 28 → 32 | 20 → 66 | 0.430, 5.32, 0.873, 17 (postTr-1/2: 0.470/0.473, 5.27/5.82, 0.876/0.872, 19/16) |

WRONG did not fall by two on either model (the gate), errors moved in opposite directions, and the set cost 3.3 times the calls,
so the code was removed. The grader's strict verdicts mark nearly every long paragraph WRONG on both models; 26b makes about half
of e4b's errors. Grades and the blind key: `.temporary_context/eval-artifacts/eval_2026-10-12/x3grade/` (owner-local).

### 15i.X2 thinking and notes per call kind: all off {#15i-x2}

Screened on `gemma4:e4b-mlx` only (the owner's rule: an arm that does not improve e4b gets no 26b run), every arm against a
baseline of the same tree (`55f4e223`), one run per suite. A call kind turns on only if its target moves by 2+ with its time up
by at most 30 %.

| Arm | realrun right | sequence s/seg | LTCR-1 | flagged | hard-gate r0 | coref right / wrong / abstain | Verdict |
|---|---|---|---|---|---|---|---|
| base | 142/179 | 3.99 | 0.908 | 9 | 10 | 8 / 2 / 21 | — |
| draft batch thinks | 142/179 | 8.82 (+121 %) | 0.886 | 10 | 14 | — | off: no gain, twice the time |
| batch notes | 143/179 | 4.20 (+5 %) | 0.893 | 10 | 10 | — | off: +1, the rule asks +2 |
| reviewer thinks | — | 14.01 (+251 %) | 0.934 | 109 | 12 | — | off: the reviewer runs out of time and 100 more segments are flagged |
| term reading thinks | — | — | — | — | — | 7 / 2 / 22 | off: one right vote fewer |

`brief-synthesis` with thinking on was not run (stopped on the owner's instruction once the other arms had failed); it stays off
with the rest. `ReasoningPlan` keeps every call kind off and the notes switch stays off; both remain as eval switches.

### 15i narrator's gender, in order {#15i-narrator}

The narrator's gender is never taken from a model's word alone (15i.T5, which also closes 15f.A4). In order: (1) the brief's
verified evidence (15i.B4: a name or a word of address the code checks); a quote that calls the narrator by a name nobody knows
yet is kept on `Narrator.evidence` as a *pending* quote, with the gender unknown; (2) `SetupAssistant.recheckNarrator`
(`NarratorRecheck`, no model, no write) checks the pending quotes against the glossary as Names & style left it, with the same
filter as the brief (a name the narrator says to someone else proves nothing), and a glossary character of confirmed gender
(the person's or verified, not a suggestion) makes the narrator that gender, `VERIFIED`; (3) Start runs the re-check only when
it is about to ask "Who narrates?" and stores a verified result through the Book Brief, a failed re-check asks as before; (4)
still unknown when translation ends (`NarratorStage`, after the last chunk, before backward revision): the targets of the
segments whose source narration holds «I» are read with the target language's `firstPersonPast` pattern (`uk.properties`, one
regex with the named groups `female` and `male`) outside quotes and dialogue; with at least 20 such segments, 8 forms and 80 % of
them (inclusive) agreeing the narrator is stored as first person with the origin `INFERRED`, logged at INFO (forms, agreeing,
gender), and each segment holding a form of the other gender gets a `GENDER_UNKNOWN` deferral waiting on
`DeferralRegister.NARRATOR`, which `GenderRevision` re-renders against the narrator at Max (the stored narrator is read when
the pass starts). A merely `SUGGESTED` gender is replaced by an inferred one, since the decided targets are stronger evidence
than an unverified guess; a person-set, verified, inferred or third-person narrator never is. The style sheet states a
person-set, verified or inferred gender and orders its form; a suggested one reads "likely male/female" and orders none.
The count runs once, after translation: a gender found during a run reaches the checks and the drafts of the *next* run
(the run's call frame is built once), and the window's brief is refreshed by the next Start, whose re-check returns the stored
narrator.

### 15i.M1 dial budgets measured {#15i-m1}

The names pass (the `names` suite, `scripts/eval-matrix.sh`) on `gemma4:e4b-mlx` at tree 41642a45, 2026-10-15, against the
minutes each dial promises. The owner rule screens on e4b only.

| Book | Fast (target 5–10 min) | Balanced (15–25) | Max (30–60) |
|---|---|---|---|
| owner novel | 10.4 min, 136 calls | 23.1 min, 257 calls | about 54 min (from the log; the run hit the eval's 90-minute limit later) |
| Tom Sawyer (public domain, about 70k words) | 6.9 min, 111 calls | 19.3 min, 250 calls | not reached |
| DDIA (technical) | 26.2 min, 202 calls | 40.5 min, 392 calls | over 31 min, cut by the time limit |

The novels fit Fast and Balanced at about 4.6 seconds a call (3.7 to 5.4); the technical book misses both because it has
more candidate terms and its calls take 6 to 8 seconds. `NamesPlan` now holds a call budget per dial, taken from the
table: a pass of 120 calls at Fast, 260 at Balanced and 520 at Max (6 to 10, 16 to 25 and 31 to 60 minutes at the pace
constants `NamesPlan` carries). The names reading may use 60 % of it (`readCallCap`: 72, 156 and 312 calls) and reads the
most mentioned terms first, so a book with many candidates is read for fewer of them; the rest of the budget is left to the
batched review, recurring terms and renderings, whose share was not measured by kind and is an assumption to check with the
26b round. The old cap of 80 terms is gone: it was a cap on terms, not on calls, and Max reads two calls a term.

Open finding for N7, not fixed here: the recurring terms kept were fewer at Balanced than at Fast (owner novel 3 of 23
against 11 of 23, Tom Sawyer 1 of 26 against 10 of 26).

Pending owner: the 26b closing round of these measurements and 15h.F2.

### 15j results: closing the change on e4b, then 26b once {#15j-results}

Measured 2026-10-17/18 on the final tree against the `0a587148` baseline (the tree before 15j), e4b first and 26b once,
with the fast loop (`#testing`, "The fast loop"); the numbers below are the record (the per-run reports were not kept).

| Suite (e4b) | Baseline | Final | Note |
|---|---|---|---|
| mini (28 segments, 50 expectations) | 46/50 | 46/50 | 1 min 51 s; misses: an obeyed injection, two names, one place |
| default | parse 98 %, gate 100 % | parse 100 %, gate 97 % | script, marker, injection, reviewer separation unchanged |
| realrun | — | identical on every column | |
| sequence, narrator unset | flag 1.8 %, LTCR-1 89.5 % | flag 2.7 %, LTCR-1 90.1 % | first-round blocking findings 4 → 13, all the alphabet check (15j.J5), so calls rose 51 → 119 |
| coref, setup | — | identical | |
| names, 3 owner books, Balanced | terms kept 3/23, 0/13, 0/21; lost 4, 3, 2 | 9/23, 7/13, 2/21; lost 2, 3, 2 | 0 wrong verified genders, junk unchanged |
| batch 4 / 8 (json) | — | tokens kept 96 % / 83 % | small sample; the default stays 8 |

On 26b once: mini as on e4b; realrun with no truncation or leak; sequence LTCR-1 78.7 %; names terms kept 11/23, 5/13,
2/21 (level with the earlier 26b runs), 0 wrong verified genders; batch 4 / 8 both keep every token.

The owner's book on e4b (Burning Chrome through `--suite book`, every decision the model's): Balanced 26.6 min, Max 48.2
min; 11 Latin words left in the story text at Balanced and 10 at Max, against 35 in the pre-15i e4b run and 35 in the
Oct 15 26b run; no word with a Russian letter (the Oct 15 run accepted one); brands and materials Cyrillic. Blind Opus
grading, 50 paragraphs each: against the pre-15i e4b run 15 WRONG and 68 errors against 16 and 80, preferred 20 to 17;
Max against Balanced 13 WRONG and 58 errors against 15 and 71, preferred 28 to 15 — about a fifth fewer errors at 1.8
times the time. Two things the run shows that this change does not fix: the predicted brief left the narrator's gender
unknown at Balanced, and two characters' genders stayed unknown, so 123 segments wait on a gender (15j.J3's Confirm
helps where a gender is suggested); both belong to the redesign's model-knowledge step.

Tried and reverted: the token rules at the end of the draft and batch system prompts (`#time-to-first-token`).

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
exactly as in a run; the window is the context length the provider reports for the model, limited to 16,384 tokens as a run limits it (16,384 when it reports none), and `BOOKLOOM_EVAL_WINDOW` overrides it; the chosen window is logged at INFO (`Eval window …`) and in the report line. The brief the requests carry is the defaults unless `BOOKLOOM_EVAL_PRESET` (`burning-chrome`: formal literary register, names translated, Balanced, cyberpunk genre — one model's frozen answer, kept so earlier reports can be measured again), `BOOKLOOM_EVAL_GOLD` (the owner's gold file for the book, [judgement evals](#judgement-evals): its brief's register, names, genre, voice, audience and narrator — prefer it for a new measurement), `BOOKLOOM_EVAL_BRIEF` (a JSON file with `register`, `names`, `genre`, `voiceEra`, `audience`, `dial`, `balance`) or `BOOKLOOM_EVAL_REGISTER` / `_NAMES` (`translate`, `transliterate`, `keep`) / `_GENRE` / `_DIAL` say otherwise The equality of the
two paths is held by `PromptRequestsEquivalenceTest`. Not covered by this path yet: the name prescan, the glossary
review and the rolling summary call, which build their own frame (deferred from 15e.2 to 15e.13, which tunes those
prompts; `SummaryModelCall` is package-private).

### The fast loop {#fast-loop}

Whole suites cost hours, so a prompt change is iterated in minutes and confirmed once. The loop, narrowest first:

1. **Probe** one case: `scripts/probe.sh --case <id>` sends it through the production builders (`ProbeTest` keeps it
   honest) and prints each message split by section, the request parameters (`num_ctx`, max output, temperature, seed,
   format, think), the raw reply, the parsed result with the verdict the suite scores, and per call the usage
   (`prompt`, `completion`, `promptEvalMs`, `generationMs`, `cachedPrompt`, `wallMs`). `--list [prompt|defects|realrun]`
   names the ids, `--repeat N` sends it N times, `--dry` prints the request and calls nothing, `--provider pseudo` is the
   offline model of the tests, `--model`/`--url`/`--window` default to `gemma4:e4b-mlx`, the provider's local address and
   16,384. Editing a `.prompt` file or an eval JSON needs no build: the probe reads the source resource directories first.
2. **Quick**: `scripts/eval-matrix.sh --quick` (`BOOKLOOM_EVAL_QUICK=1`) runs ten prompt cases (`QuickEval.PROMPT_CASES`,
   no defects corpus), the realrun cases marked `"quick": true`, one invented book for `names`, and `mini` in place of
   `sequence`. `--book-file <path>` runs one owner book of the names and gold suites.
3. **Mini**: `scripts/eval-matrix.sh --suite mini` (the invented 28-block book, end to end).
4. **One full run per suite** to confirm, on e4b (the default model; `--all-models` restores the list).
5. **26b once**, at the end of a change.

Keep the model and its prompt cache warm: start Ollama with `OLLAMA_KEEP_ALIVE=1h` in the *server's* environment (not
the client's), and keep the window constant (16,384) so a GGUF model is never reloaded. Every Gradle run and every model
call takes `scripts/gradle-lock.sh` (one at a time across agents; `BOOKLOOM_GRADLE_LOCK_DIR` moves the lock off the
owner's default path), so never wrap `probe.sh` or `eval-matrix.sh` in it.

Reading `cachedPrompt`: it is filled from Ollama's `prompt_eval_cached_count`. On Ollama's MLX runner `prompt_eval_count`
is the whole prompt and `prompt_eval_duration` only the part that was not cached, so `promptEvalMs` falls on a repeated
prompt while `prompt` does not; the share of the prompt served from the cache is `cachedPrompt / prompt`. A first probe
after a model load shows `cachedPrompt` of 0 or `-`; send the same case again (`--repeat 2`) to see the shared system
block reused.

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
generic words `great`, `hall` and `old` that `KeyTermScan` proposes (15e.11). They are
reported beside the rates (`+Nk` in the matrix, `known` in the table), never inside one, and the offline test fails when one
starts passing, so the fixing task drops the flag in the same change.

*Cases for the translation fixes (15i.E4).* They are red before 15i.T1–T7 and say so: `replies.json` holds the kept brand,
magazine title and identifier (`kept-run`; the first two known failures of 15i.T4) and the ти/Ви mix in one speech
(`address-mix`; a known failure of 15i.T6) as replies the draft judge must keep or send to a fix; `text.json` holds the
same kept runs as `kept-run` cases decided by the blocking Latin-run gate (`LATIN_RUN`); `injection.json` is a batch of a
plain sentence and two instructions to the translator, measured only by the model run (`injection/batch`, the detail
reads `obeyed=N`; offline tests prove the patterns). The cases that need a whole job, a consistency pass or a call of
their own (a stray `\u001e` at the start of a directed fix, a fix that wraps tokens in a tokenless segment, a gender that
is only suggested, a redraft that mends a mixed-script word and drops one dash, `data → data`) are tests wrapped in
`KnownRed.until("15i.Tn", id, () -> …)`: the assertions are the behaviour the task delivers, they must still fail while
the task is open, and the wrapper's message tells the fixing task to drop it. `eval/long-paragraphs.json` holds twenty
public-domain paragraphs of more than 180 words (title, author, chapter, Project Gutenberg number) for 15i.X3. The
sequence report adds LTCR-1 (DelTA, ICLR 2025), as implemented in `SequenceMeasure`: for each fixture term and name, the decided
segments with a target are read in order, and a segment counts only if its source names the term and its target carries at least one
listed rendering or spelling. The first such segment fixes the term's rendering (the first listed one when it carries several); each
later such segment counts once, per segment and not per occurrence, as kept when its target carries that rendering. A later segment
whose target carries no listed rendering (echoed, untranslated, flagged without a target, or an unlisted form) is skipped and counts for
neither side. The ratio is the kept count over the later segments, summed over all terms and names. It is -1, printed as `n/a`, when no term has a
later segment, because a run that repeated nothing is not 0 % consistent.

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
| names with tempting spellings (Zerubin, Edwin, Pellbury, Ashdown, Whitlock, Harrowgate, Quill), the stammered `Z-Zerubin`, the hyphenated `Stoke-on-Marsh`, generic capitalised places (`Great Hall`, `Old Bailey`) | in narration and in speech |
| `[n]` footnote markers and footnote lines, `*emphasis*` / `**bold**`, Latin kept foreign, numbers and dates, three verses | 16 markers, 8 footnote lines, 14 Latin paragraphs |

`manifest.json` lists the chapters and their narrator, the glossary the run starts with (Edwin locked, Pellbury unlocked, Ashdown
and Zerubin with no target) and, per term and name, the renderings and spellings that are counted (`master`'s list leaves out
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
| `nameVariants` | per name, the spellings seen (`зерубін` and `зеробін`); the sum of those beyond the first |
| `genderSlips` | `GenderChecks` («я» + a past-tense word of the wrong gender) over the first-person chapters |
| `english` | targets still in the source language: `LanguageIdentityCheck` finding or a plain echo of the source |
| `flaggedNoTarget` | flagged segments with no stored target, which an export writes as the source (the 23-English-paragraphs bug, fixed by 15e.5) |
| `hardGateRound0` and `kinds` | segments whose first evaluation failed a hard gate (a blocking quote or script finding, a placeholder), read from the run's `Evaluated ... round=0 ... hardGatesPass=false` line; `kinds` counts the findings of their first repair round (`Round choice ... round=1`). The real run: 73 of 3,783 |
| `leakedProtocol` | final targets holding protocol text (`ProtocolLeak`: a `terms`/`items` key and colon, a brace holding an `id` key, a code fence; the real run: 4) |
| `reviewerTruncated` | reviewer replies the provider cut off at the output cap (`finish=LENGTH`), counted by a recording wrapper around the model |
| `termClaimedWrong` and `claimed` | lexicon terms whose learned rendering another term also claimed (`mr` and `mrs` both learning «пані»), with the shared renderings |
| `quote` / `ascii` / `mixed` | `TextChecks` blocking unbalanced-quote and mixed-script findings in the final targets (a soft "English quote marks" note is not counted); `ascii` counts targets that still hold a straight `"` (an eval-side count, the quote check does not read the kind) |
| `flag%`, `fallbk%` | flagged share; batch items that fell back to their own draft over batched items (reasons are `status/problems`, read from the run's own warning) |
| `calls`, `sec/seg`, `edits+`, `edits-` | finished model calls and wall seconds per segment; reviewer edits applied and refused |
| `learned`, `coverage`, `lexicon/t` | renderings the lexicon learned by co-occurrence, the share of those terms' occurrences carrying it (the `--report` lexicon section), the lexicon's own distinct renderings per term |

Offline (`check`, no model, under 20 s): `SequenceFixtureTest` (the committed book equals the generator's output; paragraph, word and density
counts; term and name counts; the narrator modes), `SequenceEvalOfflineTest` (the same code through the real job over `SequenceScriptedModel`,
which drifts `master`, `Mr` and `Zerubin` for the second half of the book, says «я була» once, echoes one paragraph in English and appends one
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
sends batches of consecutive draft cases through the JSON batch protocol. Without `--batch-sizes` the size is the job's: the first
batch has `BatchDrafter.DEFAULT_INITIAL_SIZE` cases and each reply goes to a production `BatchDrafter.record`, which halves the
size on a lost numbering, an omission or a merge (not on a reply that answered only the first item or was cut at the length cap, 15i.T3) and grows it after a clean streak (the table then has a row per size reached);
`--batch-sizes` fixes the old sweep. The draft cases include two invented long paragraphs (over 250 and over 400 characters,
with numbers that must survive). It prints, per model and size: `idValid` (items
answered under their id exactly once), `tokGate` (also kept their tokens in order), `omit`, `merge` and output tokens per
item. It measures only; the batch size is chosen by reading the table (the tagged-block protocol it once compared was
dropped after the first A/B, see `12_PROMPT_CATALOG.md#batch-draft`). A run's own draft calls per segment are
`modelCallsByKind.DRAFT.attempts` over `run.segments` in the report JSON (a batch call and a single-segment fallback each count one).

**Batch format arms (15i.X1, an experiment).** Three ways to send and read a batch sit behind one switch until the
measurement picks one and the other two are removed. `BOOKLOOM_BATCH_FORMAT` (else the property `bookloom.batch.format`) names
`json` (the default: numbered items, a JSON reply under the schema), `markers` (one passage whose items start at `⟦s1⟧ … ⟦s8⟧`,
a plain-text reply with the same markers split by code, no response format) or `dual` (the whole passage once as context, then the
numbered items, the same JSON reply). In a run it is read when the job starts; in the evals use
`scripts/eval-matrix.sh --batch-format json|markers|dual` (or `BOOKLOOM_EVAL_BATCH_FORMAT` for a direct `./gradlew :pipeline:promptEval`,
which the build passes on as the property) so the realrun, sequence, batch and replay suites send and read what that arm's job does; every
report's provenance says `batch_format=…`. Run all three arms on the same models and suites, one gradle at a time, and compare omissions
and missing ids, control-code and quote defects, the placeholder gate, WRONG on a fixed 60-segment sample, seconds and calls per segment;
adopt `markers` or `dual` only if WRONG falls by 2 or more or seconds per segment fall by 10 % with no key metric losing 2 or more (task
15i.X1). The result and the decision are recorded here when the arms are measured.

**15i.X1 batch format (measured 2026-10-13/14, tree 690ce998, relevant pairs off).** Suites through `scripts/eval-matrix.sh
--batch-format <arm>` (realrun `--stability 3`, sequence `--narrator unset`, replay of the Oct 10 trace, fast mode, 40 cases);
the 300-segment run is `:app:translate` on a public-domain novel (EPUB, en→uk, Balanced, names transliterated,
`--stop-after 300`). realrun counts only the draft, batch and repair groups (the reviewer groups do not depend on the format).

| model | arm | realrun draft | sequence flagged | seq. fallbacks | seq. calls/seg | seq. s/seg | LTCR-1 | replay pass | 300: flagged | 300: missing ids | 300: calls/seg | 300: s/seg |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| e4b | json | 67/73 | 9 | 17 (5.2 %) | 0.38 | 4.11 | 92.9 % | 78 % | 3 | 5 (one cut reply, re-asked) | 0.30 | 1.60 |
| e4b | markers | 66/73 | 7 | 10 (3.0 %) | 0.36 | 3.60 | 92.4 % | 83 % | 4 | 0 | 0.30 | 1.32 |
| e4b | dual | 65/73 | 10 | 19 (5.8 %) | 0.39 | 3.81 | 92.7 % | 78 % | 3 | 0 | 0.31 | 1.46 |
| 26b | json | 66/73 | 9 | 11 (3.1 %) | 0.44 | 5.05 | 74.7 % | 80 % | 4 | 5 (one cut reply, re-asked) | 0.32 | 1.96 |
| 26b | markers | 67/73 | 9 | 5 (1.5 %) | 0.38 | 4.97 | 85.7 % | 78 % | 5 | 0 (one merge suspect) | 0.35 | 2.30 |
| 26b | dual | not run | not run | not run | not run | not run | not run | not run | 5 | 0 | 0.32 | 2.24 |

In every arm and model: no leaked protocol, no control-code residue, no ASCII quotes and no unbalanced « » in the export,
no placeholder-gate failure in the export (one `TOKENS` item problem per markers run, repaired before acceptance), realrun
truncated 0. The `exit=1` of every 300-segment run is Gradle reporting the command's exit code 3, a partial export
by design under `--stop-after`; all six runs decided 300 segments and wrote their book.

**Decision: json stays the default; the decision is pending the owner.** Speed alone cannot adopt an arm: markers is
18 % faster on e4b in the 300-segment run (12 % in the sequence suite) but 17 % slower on 26b (sequence 2 % faster), so the
models disagree and the rule keeps json; dual is never 10 % faster on both. The Opus-graded WRONG on the fixed 60-segment
sample and the 26b dual suites were not run (the eval queue's budget ran out), so a quality gain of 2+ WRONG cannot be
ruled in or out. The three arms stay in the code behind `BOOKLOOM_BATCH_FORMAT` (default `json`) until that grading is done.
Rerun what is missing: `scripts/eval-matrix.sh --models ollama:gemma4:26b-mlx --batch-format dual --suite realrun --stability 3`
(then `--suite sequence --narrator unset` and the replay suite), and grade the six exports with the sample rule: `<p>` blocks
of 8+ source words translated in all six books, 60 picked evenly by position, letters shuffled per segment.

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

**Coref probe (15i.E2).** 31 invented windows of two to five paragraphs (`src/test/resources/eval/coref/windows.json`), each
naming one target and listing every third-person pronoun with its gold referent (the target, another character, a place's
people, nothing), in six shapes: another character in the next sentence, a speaker's pronoun in quotes, a place's "her", a
first-person narrator, a pronoun with no referent, and plain. `CorefScorer` scores a `CorefReader` per window: wrong if any
vote names a pronoun that is not the target's (or fits the target's and another's), right if it has a vote and none is wrong,
else abstention; a quote not in the window is dropped. The gate is wrong votes at most 5 % of the windows that voted
(`CorefReport.wrongVoteRate`), per model: above it that model's window votes only break ties. The fixture and scorer are
tested in the normal gate. `scripts/eval-matrix.sh --suite coref` (15i.N4, `CorefEvalTest`) reads every window with the
production term reading (`TermReadingCorefReader`: one `term-reading` call per window, the target marked where it first
stands) and scores only the votes `TermReadingReplies` keeps; the report (`<model>-coref.json`) and the matrix table give
per model the windows right, the wrong votes, the abstentions and the wrong-vote rate.

**Stage suites (plan A4).** The model-call stages no other suite reaches are measured through their production classes on
small invented fixtures (`src/test/resources/eval/stages/*.json`, English and Russian to Ukrainian, no book text), selected
with `BOOKLOOM_EVAL_SUITE` and run like the others (`BOOKLOOM_EVAL_URL` required, `@Tag("promptEval")`, never in `check`):

| Suite | Production class | What a case must end with |
|---|---|---|
| `prescan` | `PreScan` (PRESCAN, then the verdict and suggestion calls) | a woman's one-word name kept as a female character, a place kept, a weekday and a language name left out |
| `terms` | `TermChoice` (TERM_CHOICE) and `TermReview` (REVIEW_TERMS) | everyday words (keep, black, screen) dropped, a jargon term kept; held names kept, a weekday removed |
| `setup` | `SetupAssistantImpl` (FILE_NAME; BOOK_KNOWLEDGE, BRIEF_SUGGESTION per window, BRIEF_SYNTHESIS) | a file name in the target script with its author part in it; the narrator the text is told in, the narrator's gender, the register and a genre of the case's class (`genreClass`, any one of whose words the genre holds) |
| `consistency` | `ConsistencyPass` (`RetryPass`, `RetryDraft`, `NeighbourRevision`, `RevisionCall`) | a planted mixed-script word, a left-untranslated sentence, a trailing `"}` and a dropped vocative repaired |
| `retry` | `RetryDraft.retry` (the review desk's Retry) | the same four planted defects repaired by one retry each |
| `replay` | the draft parsers and judges (`ReplyJudge`, the batch parser), over calls read from a real trace log | see "Replay from a trace log" below |

`consistency` and `retry` first run the real batched job over a small book with a model that answers the case file's planted
(defective) drafts, so the stored records, statuses and context snapshots are what a run leaves; only then is the real model
asked. Scoring is deterministic: each case's pass/fail, and per row the calls, failed calls, repeated identical requests and
answers the class read and refused (`ConsistencyChecks.refused`, undecided term batches). Reports are
`build/reports/promptEval/<model>-<suite>.json` and `.txt` (`{"suite":"prescan",...}`), and
`scripts/eval-matrix.sh --suite prescan|terms|setup|consistency|retry` prints one row per model. The offline proof is
`StageEvalRunnerTest` (a fake model answering like a good one, and a silent one), which also compares the recorded request with
the production prompt for the stage.

*Setup cases for the new prediction (15i.E3).* `eval/stages/setup.json` also holds eight invented title and author pairs
(`invented-01…08`), four public-domain classics (`classic-*`, published before 1929, with the year in the FB2 `<date>`) and a
book with no metadata (`setup-no-metadata`). A case carries `expectKnowledge` (`unknown` for an invented or title-less book,
`known` for a classic), `year`, `publisher` and `classic`; the knowledge answer is scored from 15i.B2 on, until then
`SetupScore` reads `knowledge.* n/a`. The per-field score (genre by the gold class, register, narrator, narrator's gender,
knowledge split into invented and classic) is `SetupScore`, proven offline by `SetupScoreTest` over `SetupScriptedModel`.

**Defect-class corpus (A5).** `eval/realrun/text.json` (`RunCase`; an optional `sourceLanguage` makes a case ru to uk, an optional
`marker` is a regex a model's draft must match where no production check decides) holds invented sentences for the classes
the October 8 run showed: a female character with a consonant-ending name whose verbs the draft makes masculine
(`female-noun-name`), slang read literally (`slang`), numbers that must not change, ASCII-quoted dialogue that must become
« » (`ascii-dialogue`), venue names declined twice (`venue-name`), a vocative at the start, middle and end
(`vocative-position`), reply closers after a draft (`closer-residue`, check `RESIDUE`) and a Latin tail on a Cyrillic word
(`mixed-script-tail`). `eval/realrun/replies.json` (`ReplyCase`) holds synthetic whole replies read by the draft reply parser
and judged by `ReplyJudge`; `DefectClassCorpusTest` holds each to what production does today. A defect production cannot see yet
(a masculine verb after a female name, a changed number) is a `knownFailure` naming the plan task that adds the check, and the
test fails once production gets it right. The control-character quote glitch (U+001C..U+001F where « » belong) and closers
after the JSON object are refused today; what they are expected to come to is the named constants
`ReplyExpectations.CONTROL_CHARS` and `.TRAILING_CLOSERS`, flipped to `ACCEPTED` when the mapping (plan B2) and the tail cut
(B3) land. No text of the corpus is copied from a book.

**Replay from a trace log (A6).** `BOOKLOOM_EVAL_SUITE=replay BOOKLOOM_EVAL_REPLAY_LOG=~/Library/Logs/BookLoom-Dev/bookloom-trace.log
./gradlew :pipeline:promptEval` (with `BOOKLOOM_EVAL_URL`, `_PROVIDER`, `_MODEL` as for the other suites; `scripts/eval-matrix.sh
--suite replay` passes the variables on) sends again the model calls a real run logged. `LogReplayCorpus` pairs each `Model call
going out` line with the next `HTTP request body` and `HTTP response body` lines of its thread and reads the `Decided segmentId=`
lines for how each segment ended; the log may be `.gz`. `BOOKLOOM_EVAL_REPLAY_MODE=raw` sends every logged request body as it
went out (a model, quant or server setting compared on the very prompts of the run); `fast` (the default) takes a stratified
selection of about `BOOKLOOM_EVAL_REPLAY_TARGET` (40) draft calls, by short/long (over 250 characters), single/batch and
flagged/repaired/accepted, always including every call that drafted a segment that ended flagged, and rebuilds each request with
the current prompts through `EvalProject` (a prompt change compared on the very paragraphs of the run; the glossary and context
are not in a log, so the rebuilt request carries none). `BOOKLOOM_EVAL_REPLAY_MAX_BYTES` reads only the first bytes of a log and
`_LIMIT` sends at most that many calls. A draft reply is scored by `DraftReplyParser` and `ReplyJudge`, a batch reply by the batch
parser and the judge on each item, any other call kind on its structure only; each row shows the logged reply's verdict beside the
new one. The calls stay in memory; the report (same files, provenance and history as the other stage suites, label = the mode)
holds counts and verdict words, never log text. `LogReplayCorpusTest` and `ReplayRunnerTest` prove the parser, the selection and
the runner on `eval/replay/sample-trace.txt`, an invented log in the real line format; `LogReplayRealLogTest` parses a real log
when `BOOKLOOM_EVAL_REPLAY_LOG` is set, without sending anything.

<a id="recall-suite"></a>**Detector recall over an exported book (15h.E2).** How many of the defects a person finds in a finished
book the code's own detectors catch, and how often they fire on a sound paragraph. No model is involved:

```bash
BOOKLOOM_EVAL_SUITE=recall BOOKLOOM_RECALL_DIR=$PWD/.temporary_context/eval-artifacts/recall ./gradlew :pipeline:promptEval --tests '*RecallEvalTest*'
BOOKLOOM_RECALL_DIR=$PWD/.temporary_context/eval-artifacts/recall scripts/eval-matrix.sh --suite recall     # the same, then the table
```

`BOOKLOOM_RECALL_DIR` must lie in the git-ignored `.temporary_context/` or outside the repository (the test refuses any
other path inside the checkout, since `segments.jsonl` holds book text), and must be absolute — Gradle runs the test in
the module directory; unset, the test is
skipped. The directory is one book (it holds `book.json`) or holds one sub-directory per book. A book directory holds:

| File | Who writes it | What it holds |
|---|---|---|
| `book.json` | the owner | `{"source": "Burning Chrome.epub", "exported": "Burning Chrome (uk).epub", "glossary": "glossary.csv", "sourceLanguage": "en", "targetLanguage": "uk", "narrator": {"person": "FIRST", "gender": "MALE"}}` — `source` and `exported` are required (paths relative to the directory); the glossary is the export's CSV side file; the languages default to `en` and `uk`, the narrator to unstated |
| `segments.jsonl` | the suite, on every run | one line per aligned segment: `{"id", "hash", "source", "target", "fired"}` — the display texts and the detectors that fired; **book text**, which is why the directory stays outside the repository. This is what a reading (a person, or an Opus reading the person spot-checks) labels |
| `gold.jsonl` | the reading | one line per segment that was read, no book text: `{"hash": "<64 hex>", "classes": ["dropped-sentence"], "expect": ["sentence-count", "audit:sentence-count"]}` |

*Gold line format.* `hash` is SHA-256, lower-case hex, of the NFC form of the source's display text, the unit separator U+001F and
the exported text's display text (`RecallGold.hash`; display text = `DisplayText.of`, tokens out, whitespace runs as one space,
trimmed), copied from `segments.jsonl`. `classes` are the defect classes the reading found, free words such as `dropped-sentence`,
`untranslated`, `wrong-gender`, `name-lost`, `wrong-meaning`; an empty list marks a segment read and found **clean** (needed for
precision). `expect` names the detectors that should have caught it, spelled as the report spells them: the run check's
`raisedBy` (`sentence-count`, `language-identity`, `quote-balance`, `number`, `vocative`, `name-swap`, `gender`, `length`, `echo`,
… — `qa.CheckName`) or the final audit's `audit:<check>`; leave it empty when no deterministic detector can see the defect. A
segment no line names is unread and counts nowhere. A line whose hash matches no segment (a new export of other text) is counted as
`staleGold`; a malformed or repeated line fails the run with its line number.

*What runs.* Both books are opened through `DocumentPort` and paired by segment id (`ExportedBooks.recordsOf`; a single-file book's
unit id is its file name, so an export under another name is paired unit by unit). Each pair goes through the run's own
evaluation, `QaEvaluator.evaluate` (refusal gate, every deterministic text check, the soft checks) with the glossary's pairs and the
segment's character sheet, then `FinalAudit.scan` reads the whole book as accepted, unreviewed records. The word validator is the
offline one, so `unknown-word` never fires.

*Report* (`build/reports/promptEval/recall.{txt,json}` and the history; counts and names only). Per detector: `fired` (read
segments it fired on), `tp` (of those, defective), precision = tp / fired, `expected` (segments whose gold expects it) and recall
= caught / expected. Per defect class: recall = the share of its segments **any** detector fired on; precision = of the read
segments one of the class's expected detectors fired on, the share of that class. Overall: `recall` (defective segments any
detector fired on) and `falseAlarm` (clean segments any detector fired on). A rate with an empty population prints `-` (`null` in
the JSON). `RecallSuiteTest` (in `check`) proves all of it on `eval/recall/mini/`, an invented eight-paragraph English → Ukrainian
book exported under another name. The gold for the three Oct 9 books is produced from the Opus readings and spot-checked by the owner (pending); it stays
in the owner's directory.

<a id="judgement-evals"></a>**The model's judgement of the book: stability and gold (15h.E3).** Most people accept the Book Brief and
the glossary's genders as suggested, so two suites measure them, both through the production `SetupAssistantImpl.suggestBrief`
and `TermReview.review` (`JudgementRunner`: the book imported with nothing stated, the gold's main characters held as a scan
leaves them — untyped, gender unknown — then the review with the code's pronoun and first-name seeding). Scored per field:
the brief's genre (by the gold's genre class), register, narrator and narrator gender, and each main character's type and
gender; voice and audience are free phrases and are not scored. *Agreement* is the share of a field's answers across seeds
that are the most common one (target 0.8 or more); *accuracy* is the share that match the gold.

```bash
scripts/eval-matrix.sh --suite stability                    # every model in scripts/eval-models.txt, seeds 1,2,3
BOOKLOOM_EVAL_BOOK=all BOOKLOOM_EVAL_SEEDS=1,2,3,4,5 scripts/eval-matrix.sh --suite stability --models "ollama:gemma4:e4b-mlx"
BOOKLOOM_CORPUS_DIR=/abs/books scripts/eval-matrix.sh --suite gold   # the owner's gold set, one seed
BOOKLOOM_EVAL_SUITE=stability BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx \
  ./gradlew :pipeline:promptEval --tests '*JudgementEvalTest*'    # one model by hand
```

`stability` reads one committed fixture book (`BOOKLOOM_EVAL_BOOK`: an id below or `all`; default `front-matter-converter`) once
per seed (`BOOKLOOM_EVAL_SEEDS`, comma-separated; each call is sent with that sampling seed). `gold` reads every
`<name>.gold.json` directly under `BOOKLOOM_CORPUS_DIR` (absolute path) and opens the book each names; it is skipped when the
directory is unset. Both read a book at the Fast dial unless `BOOKLOOM_EVAL_DIAL` names another (the 15i.B4 gate times
the suggestion at `BALANCED`). Both are `promptEval`-tagged (`JudgementEvalTest`), never in `check`. Reports:
`build/reports/promptEval/<model>-stability.{txt,json}` (or `-gold`) and the history; the matrix table prints each model's
lowest agreement and mean accuracy, then every `book:field` per model as agreement/accuracy and the agreement of all models'
answers pooled.

*Gold file format* (one per book, written by the owner after reading it; no book text):

```json
{
  "book": "Glass Harbour.epub",
  "sourceLanguage": "en",
  "targetLanguage": "uk",
  "brief": {
    "genre": "cyberpunk short stories",
    "genreClass": ["cyberpunk", "science fiction"],
    "register": "NEUTRAL",
    "voiceEra": "hard-boiled, 1980s street slang",
    "audience": "adult readers of science fiction",
    "names": "TRANSLITERATE",
    "narrator": "FIRST",
    "narratorGender": "MALE"
  },
  "characters": [
    {"term": "Lena", "type": "CHARACTER", "gender": "FEMALE"},
    {"term": "Danny Vosk", "type": "CHARACTER", "gender": "MALE"}
  ]
}
```

`book` is relative to the gold file's directory. `register` is `FORMAL_LITERARY`, `NEUTRAL` or `CASUAL`; `narrator` is `FIRST`,
`THIRD` or `UNSPECIFIED` (a book whose first-person narration alternates with third-person chapters is `FIRST` when at least a quarter of its judged chapters are first person, else `THIRD`: the book-brief spec's mixed-book rule, 15i.B4); `narratorGender` and a character's `gender` are `MALE`,
`FEMALE`, `NEUTER` or `UNKNOWN` (the book never shows it); `type` is `CHARACTER`, `PLACE`, `TERM`, `TITLE` or `OTHER`; `names`
is `TRANSLATE`, `TRANSLITERATE` or `KEEP_ORIGINAL`. `genreClass` holds lower-case words, any one of which a right genre contains
(empty: the genre is not scored; a committed gold's `genre` is one of the forty names the synthesis answers with, and its class never holds a generic word such as `fiction` alone, so every book can be scored); `genre`, `voiceEra`, `audience` and `names` are optional and only used when the same file is
given to `BOOKLOOM_EVAL_GOLD`, which sizes every other suite's requests for that book's real brief. List each main character
under the term the book uses most. Three to five books make the set.

*Committed cases* (`src/test/resources/eval/judgement/*.json`, invented text): `gender-late-or-object` (a "She" only two
sentences after the name; a man shown only by his/him), `straight-quoted-speech` (another man's "He" inside straight-quoted
speech next to a woman's name), `vocative-name` (a woman named mostly as a vocative before "he said"), `female-narrator-address`
(a first-person woman shown only by "ma'am" and her name), `front-matter-converter` (title page, copyright, twelve blurbs of
praise, dedication, contents and a converter's page before the story), `alternating-narrators`, and for the new prediction
(15i.E3) `letter-in-third-person` (a first-person letter inside a third-person novel: the quote misleads, the narrator detector
is right), `first-person-vocative-name` (a woman narrator named only by others' vocatives: the code verifies her gender only once
the name is a glossary character, `expect.verifiedGender` is `UNKNOWN` until then, 15i.T5) and `quoted-woman-not-the-narrator`
(a man's own quoted "Mind the step, Lucy" is a woman's name, not his; "sir" shows his gender). Each holds its chapters, its
gold, what the code must conclude with no model (`expect`: the narrator detector's person, the pronoun reader's gender per
name, where the brief's first sample starts and what no sample may hold) and the quotes a good model gives for the narrator's
gender. `JudgementBooksTest` (in `check`) holds the code to `expect` and runs each book through the runner with a fake model that
answers as the gold says (every field right); `JudgementSuitesTest` proves the agreement and accuracy scoring, the seed handling
and the gold directory. The praise page found a defect: twelve blurbs ("A quiet triumph." The Northern Review) counted as a
story chapter, so the brief read reviewers first; a story paragraph now needs two full sentences closed by their mark.

<a id="names-suite"></a>**The names suite: Names & style end to end against gold (15i.E1).** `NamesRunner` runs a book the way the
window does, through the production `GlossaryService` and `LexiconService` over the imported project's own stores (`SequenceJobs.names`):
glossary scan, name prescan, review, translate, then lexicon scan, model scan, review and suggest. It then scores the final glossary
and lexicon against the book's gold (`NamesScore`): the gender of every character with five or more mentions (accuracy, and the
*wrong but verified* count, a wrong gender not marked suggested), the type of every gold character, whether the written forms of one
person (one `cluster`) hold one gender, the distinct spellings per cluster, the junk a scan kept, the real names it lost, the
recurring terms kept and dropped, and the calls and minutes. A mention is a whole-word, case-sensitive occurrence of the gold term.

```bash
scripts/eval-matrix.sh --suite names                                    # the nine committed books, every model
BOOKLOOM_EVAL_BOOK=misattributed-she scripts/eval-matrix.sh --suite names --models "ollama:gemma4:e4b-mlx"
BOOKLOOM_CORPUS_DIR=/abs/books scripts/eval-matrix.sh --suite names     # the committed books and the owner's gold set
```

`BOOKLOOM_EVAL_BOOK` is a committed id or `all` (the default); with `BOOKLOOM_CORPUS_DIR` set every `<name>.gold.json` directly under
it runs too, its mentions counted in the book's story text. The report is `build/reports/promptEval/<model>-names.{txt,json}` and the
history; the matrix prints, per model and book, gender % over the eligible characters, wrong-but-verified, type %, the share of
person clusters with one gender, spellings per cluster (1 is ideal), junk kept, names lost, recurring terms right, calls and minutes.
It measures and asserts no floor. The books are read at `BOOKLOOM_EVAL_DIAL` (Balanced by default; before 15i's names gate the
suite read every book at Fast whatever its report said). Gender is scored over gold persons only (type `CHARACTER`, five or more
mentions): a place, a thing or a title carries no gender. A cluster's spellings are counted word by word: each source word of a
member whose rendering has as many words is paired with the rendering's word in its place, and the cluster counts the most
renderings one source word got. Each book's JSON also carries a `diagnostics` object (`NamesDiagnostics`) with the cases behind
the numbers: each wrong gender with its origin, the wrong types, the lost names, each cluster's renderings, the junk kept, the
recurring terms per gold decision, the other lexicon terms and the whole final glossary.

<a id="mini-suite"></a>**The mini suite: one small invented book that holds every defect seen so far (15j.J15).** `eval/mini/book.json` is a
28-block invented book (a first-person male narrator, a harbour town, no line from any real book) with one block per defect the owner's
runs showed: Ukrainian dialogue punctuation, an inflected title and an inflected multi-word term, a brand, a material and a drug, a
woman and a man whose pronouns prove their gender and a name with none, a multi-word place and a place whose first letter a model
mis-transliterates, a word that tempts a Russian letter, an injected instruction, a long paragraph, a ти/Ви mix, a footnote aside and a
code that must stay Latin. `MiniBook` writes it as an EPUB 3 for each run (nothing binary is committed) and `eval/mini/expectations.json`
says what each block must come to: a check of one segment or of a group (`NO_LATIN_EXCEPT`, `NO_RUSSIAN_LETTERS`, `MATCHES`, `ABSENT`,
`NOT_FLAGGED`, `NO_FINDING` by the finding's `raisedBy` name, `NARRATOR_MALE`, `MIN_RATIO`, `ADDRESS_SAME`), the two checks every block
must pass, and what the glossary holds after the names pass (type, gender and whether it is settled, locked, target in Cyrillic, target
words, junk absent). Each expectation carries the defect it guards. `MiniRunner` runs it as the app runs a whole book: the autonomous
names pass (`NamesPass`), the batched translation job, then the export's consistency pass, over one imported project; `MiniScorer`
scores the result with production checks (`GenderChecks`) and plain patterns.

```bash
scripts/eval-matrix.sh --suite mini --models "ollama:gemma4:e4b-mlx"                       # Transliterate, the default
scripts/eval-matrix.sh --suite mini --models "ollama:gemma4:e4b-mlx" --names keep_original # the brand expectations flip
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_SUITE=mini ./gradlew :pipeline:promptEval   # one model, by hand
```

The book has 28 segments; a run covers the names pass, the translation and one consistency pass at Balanced. Its runtime on e4b is measured by the orchestrator, not stated here. `--names` (env
`BOOKLOOM_EVAL_NAMES`) is the brief's name policy: under Transliterate and Translate a brand must come out in Cyrillic and unlocked, under Keep
original it must keep its Latin spelling and be locked; the material and the drug are translated under every policy. The report is
`build/reports/promptEval/<model>-mini-<POLICY>.{txt,json}` and the history; the `.txt` lists each expectation not met with what was found,
and the matrix prints the expectations met, the segments flagged, the calls and seconds, the missed ids and the met/total per defect.
It measures and asserts no floor. In `check`, `MiniEvalOfflineTest` runs the whole flow on the pseudo model (which upper-cases its source,
so only the expectations an echo can meet are met), and `MiniBookTest` holds the book to its expectation file.

**The whole-book run (15j.J16).** `BookRunner` takes any book file and decides what a typical user decides in the window: the predicted
Book Brief accepted (name policy and the rest at the defaults), the names pass, the Start narrator re-check, the translation at the dial,
then the export written twice, with the consistency pass off and on, each with the glossary CSV and the report. Select the class, since
`PromptEvalTest` also runs whenever `BOOKLOOM_EVAL_URL` is set:

```bash
BOOKLOOM_EVAL_URL=http://localhost:11434 BOOKLOOM_EVAL_PROVIDER=ollama BOOKLOOM_EVAL_MODEL=gemma4:e4b-mlx BOOKLOOM_EVAL_DIAL=BALANCED \
BOOKLOOM_EVAL_BOOK_FILE=/path/book.epub BOOKLOOM_EVAL_OUT_DIR=$PWD/.temporary_context/eval-artifacts/runs/book1 BOOKLOOM_EVAL_SOURCE=en BOOKLOOM_EVAL_TARGET=uk \
  ./gradlew :pipeline:promptEval --tests '*BookRunEvalTest'
```

`BOOKLOOM_EVAL_OUT_DIR` is required and must lie outside the checkout (the test refuses otherwise: the exports hold book text). It receives
`<name>.no_consistency_pass.<ext>`, `<name>.with_consistency_pass.<ext>`, their `.glossary.csv` and `.report.md`, and `book-run.json`: calls per stage,
minutes, flagged segments, characters by gender (verified, suggested, none), and per export the Latin words left, words with a letter the
target does not use, segments waiting on a gender, and the segments each detector fires on. `BookRunnerOfflineTest` proves it in `check` on the pseudo model.

The names gold extends the gold file above with these keys (the older code ignores them): `characters[].cluster` (the id shared by
every written form of one person, such as "Dax" and "Dax Orlo"; absent for a name of its own), `junk[]` (terms a scan must not leave
in the glossary: a heading, a nationality, a distress call, half of a brand), `keep[]` (real names besides the characters that must
not be lost: a place, a brand, a person called Mark) and `recurringTerms[]` (`{"term", "keep"}`: the lower-case common words a person
keeps in the lexicon, or `keep: false` for the ones a scan wrongly lists). Junk is matched against a glossary term with the same
letter case, so `KESSEL OPTIK` is junk while `Kessel Optik` is the camera.

*Committed books* (`src/test/resources/eval/names/*.json`, invented text; each has its gold, the answers a scripted model gives where
they differ from the gold, and `knownFailure`/`fixedBy` when today's Scan, Review and Translate end wrong): `misattributed-she`
(a man followed five times by another person's "she"; clean since 15i.N1), `short-and-full-form`, `city-her`, `stray-he`,
`bartender-mark` (a man called Mark beside "a mark on the wall"; clean since 15i.N5), `brand-and-camera` (`Orrin & Vale` and
`KESSEL OPTIK`/`Kessel Optik`/`Kessels`; red until 15i.N3), `groups-nationalities-converter`, `technical-headings`, `mayday`.
`NamesRunnerTest` (in `check`) runs each through the runner with a scripted model: a book not marked a known failure must end clean,
a marked one must still end wrong, so the test fails the day a task fixes it and the flag is dropped. `NamesScoreTest` holds every
scoring rule to numbers written out by hand.

*How Review decides a read name* (15i.N5): Review and the names pass share one path. A strong pronoun case
(`PronounGender.Evidence.isStrong`) of a person is decided by the code with no call; of the other open entries, those the
dial's `NamesPlan` reads go through `term-reading` and `ReadingVerdict`, and the rest through the batched `review-terms`
call. `ReadingVerdict` turns the readings into a change by rule: a name needs two windows read as `name` (one window of one
at Fast) and takes the kind most name windows give (person → character, place → place, organisation or thing → term); a
title majority makes a title, which like a place, an organisation or a thing carries no gender. The gender pools the
verified votes of the whole cluster (`ReadingVotes`), each window and quote counted once. The votes only break ties,
because the coref round (15i.N4, 2026-10-13) found 10 % wrong votes on 26b and 20 % on e4b against a 5 % gate: the code's
pronoun tally decides wherever it is clear (three or more at 60 %), written verified only where the cluster's verified votes
agree with it and suggested otherwise, and with no clear tally votes at the thresholds below give only a suggestion, as do two
or more pieces of evidence, votes and pronouns together, all one gender's; no tiebreak call is made. The tally reads a name's own
uses (15i.N names gate): a use that ends a longer name (`Corin Vale` for `Vale`; a capitalised word opening the sentence
counts only when the book also writes it with a capital inside a sentence) is not read, a use after a gendered form of address
(`Mrs.`, `Miss`, `Aunt`, `Widow`; language file `femaleTitles`/`maleTitles`) counts that form instead of a pronoun unless the
surname is written after both genders' forms (a family name), and a name that opens with one has its gender. A mode in
which a model's votes decide, with a `term-tiebreak` call against a dominant tally, returns with the first model that passes
the coref gate; until then it is not built (removed as dead code in the 15i review). Removal needs two `word` uses whose quote stands in its own window, no `name`
use, a lower-case share of 0.2 or more in the scan text and an entry nobody typed or gave a gender. At Max a term read in
two or more calls (or mentioned 30 times) whose calls read it differently gets one `term-summary` call; its reading replaces
the windows' majority only when its quote stands in a window shown and it is one of the calls' readings. The Scan button
keeps for the reading a common word the book writes with a capital mid-sentence three times or more (`Mark`), and adds it
only when the dial's plan reads it; the run's own scans still drop it.

*The names pass* (15i.N8): `NamesPass` (`NamesPassImpl`, `ua.bookloom.pipeline.glossary`) runs the steps the Names & style
buttons run, in order, on private copies of both tables (`StagedGlossary`, `StagedLexicon`: the glossary's and the
lexicon's own rules over a copy, with the stored repositories asked only about removals made earlier): the deterministic
scan when the glossary is empty (`GlossaryServiceImpl.scan`), the names step (`TermReview.decide`: the verdicts of a
review without its suggestion step), the recurring terms (`LexiconServiceImpl` scan, review of the held terms, model
scan, so a term a review drops is not read again), the renderings (`NamesTranslation` for the glossary, with the readings
of the names step; `LexiconService.suggest` for the terms) and the lock (`LockRule`: a brand, an identifier or a name kept
as written is locked with its own spelling; a name read as a name, not a speaking one, with a checked rendering, is locked
where the target language does not decline names, `namesInflect=false` in its language file). The stored tables are
written once, at the end, through `remove`, `update` and `add` (`put` for the lexicon), so a Stop, a cancelled reply or a
failed call writes nothing; Stop is the calling thread's interrupt, which `StoppableModel` turns into a `cancelled` answer
before the next call goes out. `estimate` runs the scan on a copy and then `NamesPlan.of` over the open names: calls and
minutes (`NamesPlan.estimatedMinutes`: the dial's time budget over the 80-term plan, 3.75–7.5 s per call at Fast,
11.25–18.75 s at Balanced, 11.25–22.5 s at Max, rounded up), an upper bound because a character the pronouns decide is
counted. The lexicon remembers a removed term for the session as the glossary does (`LexiconRepository.wasRemoved`: set by
`remove`, cleared by `put` and `record`), which the text scan and the model scan honour. The context window of the pass's
own calls is the detected one (`ContextBudget.windowFor`); the recurring-term calls of the lexicon service use the default
window, as the buttons do. Owner-book target: 26b at Balanced within 25 minutes with no wrong verified gender and every gold
name present (run by the orchestrator).

*Book-neutral sources* (15i.E0): no name, quote or passage of a book under test may sit in `modules/*/src` — the committed
cases above, the tests and the prompts use invented material of the same shape. `BookNeutralSourcesTest` (`:app`) enforces it
from the owner's own list, which is never committed because it would name the books: it reads
`$BOOKLOOM_CORPUS_DIR/neutral-denylist.txt` (one case-sensitive word or phrase per line, `#` comments and blank lines skipped)
and fails with every `path:line: token` that holds one as a whole word in a text file (java, kt, properties, prompt, txt, json,
md, fxml, css, xml, csv). It is skipped by a JUnit assumption when the variable is unset or the file is absent, so CI and a
checkout without the corpus stay green.

<a id="15i-names-gate"></a>**15i names gate (2026-10-13/14).** Three rounds of the names suite at Balanced on the owner's four books ("owner
book 1–4": three novels and a short story) and the committed fixtures, 26b and e4b
(`gemma4:26b-mlx`, `gemma4:e4b-mlx`, Ollama). Before the rounds the measurement itself was fixed: the suite had read every
book at Fast while its report said Balanced; it now reads at the reported dial, scores gender over gold persons only, counts
spellings word by word and writes the cases behind each number (`diagnostics`). The owner-local gold was corrected where the
book proves it wrong (four rules, each entry with its quote in `GOLD-CORRECTIONS.md` beside the gold): a title form the scan
cannot make (`Mr.`, `Mrs.`, `Miss` + surname: the glossary holds the surname, the title carries the gender), a name the book
never writes alone, a place given a gender, and a surname whose lone uses are one titled woman's. Every number below is
rescored against that corrected gold (persons with five or more mentions), r0 included. Round r0 is the code before the gate
(1202c719); r1 adds the agreement rule, the title-aware tally, the ten-lone-uses scan rule and the seeded-type rule; r2 adds the
one-sided weak evidence, the first-name diminutives and the model scan's batch pre-filter; r3 replaces that pre-filter with
"read the 30 most frequent, narrow the rest", adds the shared-word agreement and the tolerant reader's raw tab. (e4b r1 already
ran with r2's pre-filter and diminutives; its terms column moved with them.)

| Round | Model | Gender, persons ≥5 (books 1 / 2 / 3 / 4) | Wrong verified | Type | Lost | Spellings (max) | Terms kept (1 / 2 / 3 / 4) | Term junk kept | Minutes (1 / 2 / 3 / 4) |
|---|---|---|---|---|---|---|---|---|---|
| r0 | 26b | 39/50 / 6/8 / 32/42 / 32/40 | 4 | 149/188 | 24 | 1 | 3/23 / 5/13 / 0/21 / 4/26 | 4 | 28.8 / 11.0 / 18.0 / 22.9 |
| r0 | e4b | 40/50 / 5/8 / 30/42 / 31/40 | 4 | 144/188 | 24 | 2 | 2/23 / 1/13 / 0/21 / 0/26 | 0 | 24.7 / 9.7 / 16.3 / 20.4 |
| r1 | 26b | 40/50 / 5/8 / 36/42 / 36/40 | 0 | 160/188 | 19 | 2 | 3/23 / 5/13 / 0/21 / 3/26 | 4 | 28.2 / 10.7 / 16.3 / 22.4 |
| r1 | e4b | 41/50 / 5/8 / 37/42 / 37/40 | 0 | 155/188 | 19 | 2 | 3/23 / 1/13 / 0/21 / 1/26 | 0 | 24.3 / 5.7 / 15.9 / 18.7 |
| r2 | 26b | 42/50 / 6/8 / 38/42 / 38/40 | 0 | 160/188 | 19 | 2 | 10/23 / 5/13 / 2/21 / 4/26 | 0 | 21.7 / 5.7 / 11.8 / 16.9 |
| r2 | e4b | 42/50 / 5/8 / 38/42 / 37/40 | 0 | 155/188 | 19 | 2 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 23.6 / 5.7 / 15.5 / 18.7 |
| r3 | 26b | 42/50 / 7/8 / 38/42 / 38/40 | 0 | 161/188 | 19 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.0 / 6.9 / 12.1 / 16.9 |
| r3 | e4b | 42/50 / 5/8 / 38/42 / 37/40 | 0 | 155/188 | 19 | 1 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 22.0 / 6.3 / 14.6 / 18.1 |

What moved, by case: the agreement rule took every wrong verified gender away (a demon written "it" with a neighbour's "he"
four times; a title and a family surname); the title-aware tally gave the bare surname of a married couple its husband's gender
instead of the wife's (429 uses, 195 after `Mr.`/`Mrs.`) and gave `Widow …`, `Mr. …` and family surnames their gender; the
ten-lone-uses rule brought back five first names and surnames (one written alone 34 times), all with the right gender; the
seeded-type rule turned seven places, spells and objects a "he" had made characters back into places and terms (type
+7 on owner book 1); the weak evidence and the diminutives gave seven minor characters a suggested gender (and two plural group words of a junk fixture one too); the batch
pre-filter, reading the 300 most frequent words, took owner book 1's recurring terms from 3 to 11 kept and cut 4–7 minutes per
book on 26b; the tolerant reader recovered a heroine whose reading reply held a tab; the shared-word rule made the spellings
1 (a person written with two first names, two anchors, had two renderings of the surname).

Gates after r3. N1 met (wrong verified 4 → 0 on 26b, 4 → 0 on e4b). N5 not met: gender 84 / 88 / 90 / 95 % on 26b (95 %
wanted), 84 / 63 / 90 / 93 % on e4b (90 % wanted); no wrong verified gender and the bartender kept, but the junk fixtures still
keep their junk (a plural group, a nationality and a converter's name read as persons; `KESSEL`'s plural); time within the
budget (owner book 1, the longest, 24 min on 26b). N6 met on 26b (spellings 1) and e4b has no literal name. N7 not met: on 26b
correct keep/drop decisions rose from 34 to 46 and "Mayday" is kept, but three terms the per-term reading kept are now dropped
by the batch choice (two on owner book 4, one on owner book 2); on e4b +1 only. N3's triage loses no gold name on the owner
books (every lost name is lost by the longer-name rule or a connector phrase, not by triage). What remains: persons the book
gives no pronoun or title (a narrator in the first person, pet names, minor surnames) stay unknown, since the reading's
pronoun answer is "none" and no other evidence exists; names written alone only two or three times are dropped as part of
their full names; the junk fixtures; reading more of the pool word by word (`READ_FIRST` 120 would keep the three dropped
terms at about 4 more minutes per book).

*Review fixes folded in after the rounds (2026-10-14, not re-measured; each tested):* a rule's lock (`GlossaryEntry.withRuleLock`)
keeps a suggested gender suggested — only the person's lock confirms it; a clear tally is verified only where verified votes agree
and none opposes it; a cluster's calls that show one window twice count its quote once; the cluster's verified gender upgrades a
member suggested the same gender; only a rendering the person chose binds a cluster's other spellings; the DECIDE vote mode, the
tiebreak call and its prompt were removed as unreachable; the names pass's estimate (`NamesPassEstimate`) counts every call it
sends (names reading, batched review, Max summaries, recurring terms, renderings and their re-asks), its store
(`NamesPassStore`) records the term deferrals a locked rendering change leaves on decided segments and puts both tables back when
a write fails half-way, and a promotion of a term the glossary already holds only takes it out of the lexicon. Of these, only
the agreement and pooling rules can move the gate numbers (verified to suggested only, so wrong-verified can only fall).

*Second iteration (2026-10-14), rounds r4–r7.* The same suite, the same corrected gold. The rows below are the reports'
own counts (`.temporary_context/eval-artifacts/eval_2026-10-12/raw/Ngate2-r*`): the suite writes 39 eligible persons for owner book 4
where the hand-rescored r0–r3 rows above say 40, so compare r4 with r3's own report (`raw/Ngate-r3`), not with the row.
r4 is HEAD after the review fixes; "rf120" is r4 with `LexiconReading`'s Balanced read-first at 120 instead of 30 (15i.N7);
r5 adds the scan rules below; r6 fixes what r5 broke (a couple's shared surname, the plural rule, a place's spelling,
two fragments); r7 lets a mention be a joined name of up to eight words and keeps the runs of an "of" name.

| Round | Model | Gender, persons ≥5 (books 1 / 2 / 3 / 4) | Wrong verified | Type | Lost | Spellings (max) | Terms kept (1 / 2 / 3 / 4) | Term junk kept | Minutes (1 / 2 / 3 / 4) | Fixture junk |
|---|---|---|---|---|---|---|---|---|---|---|
| r3 (own report) | 26b | 42/50 / 7/8 / 38/42 / 37/39 | 0 | 161/188 | 19 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.0 / 6.9 / 12.1 / 16.9 | 6 |
| r4 | 26b | 42/50 / 6/8 / 37/42 / 37/39 | 0 | 160/188 | 19 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 23.3 / 7.4 / 13.1 / 18.2 | 6 |
| r4 | e4b | 42/50 / 5/8 / 38/42 / 36/39 | 0 | 155/188 | 19 | 1 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 23.9 / 6.9 / 16.2 / 19.0 | 6 |
| rf120 | 26b | 42/50 / 6/8 / 37/42 / 37/39 | 0 | 160/188 | 19 | 1 | 11/23 / 5/13 / 3/21 / 5/26 | 4 | 29.9 / 10.9 / 18.8 / 23.6 | 6 |
| rf120 | e4b | 42/50 / 5/8 / 38/42 / 36/39 | 0 | 155/188 | 19 | 1 | 3/23 / 1/13 / 0/21 / 1/26 | 0 | 28.1 / 9.6 / 19.4 / 23.7 | 6 |
| r5 | 26b | 44/50 / 7/8 / 38/42 / 39/39 | 1 | 168/188 | 12 | 2 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.2 / 7.4 / 13.5 / 18.4 | 0 |
| r5 | e4b | 44/50 / 5/8 / 39/42 / 38/39 | 1 | 162/188 | 12 | 2 | 4/23 / 0/13 / 0/21 / 1/26 | 0 | 25.2 / 6.9 / 16.3 / 19.7 | 0 |
| r6 | 26b | 44/50 / 7/8 / 39/42 / 39/39 | 0 | 168/188 | 10 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 25.4 / 7.6 / 13.6 / 18.4 | 0 |
| r6 | e4b | 44/50 / 5/8 / 39/42 / 38/39 | 0 | 162/188 | 10 | 1 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 25.3 / 6.7 / 17.4 / 20.2 | 0 |
| r7 | 26b | 44/50 / 6/8 / 38/42 / 39/39 | 0 | 168/188 | 9 | 1 | 11/23 / 5/13 / 2/21 / 4/26 | 2 | 24.2 / 8.0 / 13.8 / 19.2 | 0 |
| r7 | e4b | 44/50 / 5/8 / 39/42 / 38/39 | 0 | 163/188 | 9 | 2 | 3/23 / 0/13 / 0/21 / 1/26 | 0 | 24.3 / 7.3 / 16.8 / 20.7 | 0 |

r4 against r3: nothing lost two or more. Two persons changed on 26b, both model noise: one heroine's reading reply could
not be read this time (gender and type unknown), and one minor character got no vote. e4b is r3 to the person.

**N7, read-first at 120 (rf120): not adopted.** On 26b it kept three more terms of craft (a painting, a cave, a widow)
and lost one (doctor), but kept two more ordinary words (monitor, school): the correct keep/drop decisions are level.
On e4b one more term is kept. Every book took 3.5–6.6 minutes longer and the longest owner book took 29.9 minutes on
26b, over Balanced's 25. The rule asked for 2+ more correct decisions on both models within the budget; neither holds,
so `READ_FIRST` stays at 30, and no second value was tried (120 was the only one the r3 notes pointed at, and it did
not move the count).

**The scan rules of r5–r7 (15i.N5 junk, 15i.N8 lost names), all code, no prompt change.**
- A capitalised word the book writes after a determiner in nine of ten mid-sentence uses (language file
  `determiners`) is not a name when it also stands between an indefinite article and a noun ("a Velarian porter": an
  adjective, `indefiniteArticles`) or when the subject pronoun after it is plural in two uses or more and more often
  than a gendered one ("the Guildsmen … They": a group, `pluralPronouns`) (`NounUses`). A word of a longer candidate is
  that name's short form and stays ("an Institute notebook" beside a longer institute's name). On the owner books the
  rule touches only words no gold lists (a river and a watch brand used as adjectives, "English").
- A plural-shaped word written after a determiner in half its uses or more ("the Velarians", "every Kessels") is the
  plural of a word: it folds into that word's candidate, or is left out when the word only stands inside a longer
  candidate (`PluralNouns`). The singular must be a candidate or a word of one, so a newspaper called "The …s" stays.
- A name written only in sections with no paragraph of two full sentences, when the book has such paragraphs, is a
  credit (`ProseSections`, `StoryMeasure.hasProse`; the triage, `CandidateTriage`). Sections are cut at units and
  headings; a story-chapter measure (20 sentences) dropped real names on the fixture book, whose sub-sections are short.
- A word inside a longer name is a name of its own when the book writes it truly alone (mid-sentence, no capital beside
  it) twice (`FrequencyScan.ALONE_MIN_STANDALONE`); the hygiene's fragment rule keeps a fragment written alone twice.
  This brought back the surnames and first names written alone two to five times beside their full names.
- Two runs joined by "of", with at most one determiner after it (`nameJoiners`), are one candidate ("Tower of Arlen",
  "Keeper of the Grey Gate"); neither run becomes its fragment, and a mention may be up to eight words long so such a
  name has windows (`MentionFinder`).
- A surname that a form of address of the other gender also carries ("Lord Venn" beside "Mira Venn") joins no full name:
  r5 had given a husband's surname his wife's gender, verified (`NameClusters`).
- One word of a place's or other name keeps one rendering across names of that group, up to the first lower-case word
  (`ClusterAgreement`; a place's first word had two renderings beside the place's full name in r5; a place named after a person
  keeps its own).

**Gates after r7.** N5's junk fixtures: all junk gone on both models in r5, r6 and r7 (the plural group word, both
nationality words, the converter's name and the brand's plural; the scan alone now proposes exactly the gold names of
those two books), the bartender kept, no wrong verified gender (r5's one, a couple's shared surname, is fixed in r6).
N5's gender: 88 / 88 / 93 / 100 % on 26b in r6, 88 / 75 / 90 / 100 % in r7, where two persons fell back to unknown by
model noise seen in r4 too (a reading reply that could not be read, a reading with no vote); e4b 88 / 63 / 93 / 97 %.
N6: spellings 1 on 26b; e4b's 2 in r7 is a joined place name the model rendered as a phrase. N8: lost names 19 → 9;
what is left: a first name written truly alone once (its other lone uses follow a capital or carry a possessive), a
place whose first word is a stop word ("Other …"), a shop written with a possessive inside it, a firm joined by "and",
two nicknames and a place written two or three times, a pet name written alone twice that the model pre-scan did not
propose, a place written with a lower-case word ("… garden"). Time: the longest owner book takes 24.2 minutes on 26b
in r7 (25.4 in r6, 24.0 in r3), inside Balanced's 25 with no margin: the names brought back are read too. Recurring
terms: unchanged by these rules.

**N5's gender gate on 26b is not met as written, and cannot be by evidence.** Every 26b miss in r6 is one of: a person
the book gives no evidence for (the code's pronoun tally is 0/0 and the reading gave no vote: four minor men of owner
book 1 who are only spoken to or of in speech, the first-person narrator of owner book 2, a woman and a man of owner
book 3 written in quoted testimony, and a couple's shared surname, unknown on purpose), a demon the gold calls neuter
whom the book's narration calls "he" four times, and a name still lost (written truly alone once). Leaving out the 8
persons with no evidence in the book, gender on 26b in r6 is 44/46 (96 %), 7/7, 39/39 and 39/39: every book at or over
95 %. On e4b the same count is 44/46, 5/7, 39/39, 38/39: owner book 2 misses two persons with evidence (one typed a
term, one with a vote against its tally). Proposed task text for N5's gate (owner decision): "characters with five or
more mentions *that the book gives a pronoun, a gendered form of address or a verified reading vote for* 95 % right on
26b, 90 % on e4b; the persons with none are listed by the suite and stay unknown (asked at Start)", with the suite
reporting that denominator. Open, not a code rule: an unreadable term-reading reply is not asked again, which r4 and
r7 show costs a person now and then.

*Decided 2026-10-15, rescored (15i.N5).* The owner took the proposed gate text: the gender gate counts the persons the book
gives a pronoun, a gendered form of address or a verified vote for; the others stay not specified, are marked
`GlossaryEntry.noGenderEvidence` (the names pass sets it on a person with a 0/0 tally and no vote; any later gender or the
person's edit clears it), read "not specified — no evidence in the book" in the Names & style gender cell, and the suite's
report and JSON give gender over evidence persons (`genderOverEvidence`, `noEvidence`) beside the old denominator. The r6 and
r7 reports predate the mark, so they are rescored without a model run: a person who got an entry of the right type and an
unknown gender is read as "no evidence" (an upper bound on that group: a person with a vote against its tally is in it).
Pooled over all fourteen books: r6 26b 147/149 (98.7 %, was 93.6 %), r6 e4b 144/147 (98.0 %, was 91.7 %), r7 26b 145/148 (98.0 %,
was 92.4 %), r7 e4b 144/147 (98.0 %, was 91.7 %). By owner book (1 / 2 / 3 / 4): r6 26b 44/46, 7/7, 39/39, 39/39; r7 26b
44/46, 6/7, 38/38, 39/39; e4b (r6 and r7) 44/46, 5/6, 39/39, 38/38. The pooled gate (95 % on 26b, 90 % on e4b) is met; read per
book it is met on 26b in r6 and missed in r7 and on e4b by owner book 2 alone (8 persons: one reading reply that could not be
read, one person typed a term), so N5 stays open until the owner says whether the gate is pooled or per book.

**Scoreboard: comparing prompt rounds (A7).** Every eval report's JSON starts with a `provenance` object (`EvalProvenance`:
ISO timestamp, short git SHA and dirty flag, a 12-hex hash of `modules/pipeline/src/main/resources/ua/bookloom/pipeline/prompt/**`,
model, `BOOKLOOM_EVAL_LABEL`, provider, window, dial (`BOOKLOOM_EVAL_DIAL`, Balanced when unset; the judgement suites record the dial they ran at, Fast when unset), brief preset and overrides, suite). Besides `build/reports/promptEval`
(overwritten by the next run) each report is kept as `report.txt` and `report.json` in
`.temporary_context/eval-history/<yyyyMMdd-HHmmss>-<suite>-<model[-label]>/` (git-ignored; `BOOKLOOM_EVAL_HISTORY_DIR`
moves it, `off` disables it; a name that exists gets `-2`). Only what the report holds is written. The JSON carries, where the
suite has the data: pass rates, defect counts (mixed script, source-language leftover, protocol or closer residue, control
characters, missing vocative, quote findings, gender slips, name variants), `wastedCallRate` (failed or repeated calls per call),
calls per segment or case and, for the sequence suite, tokens and seconds per call kind (`costByKind`).

```bash
BOOKLOOM_EVAL_LABEL=round7 BOOKLOOM_EVAL_SUITE=terms ./gradlew :pipeline:promptEval   # a run, kept in .temporary_context/eval-history
scripts/eval-score.py list                                                          # the kept runs
scripts/eval-score.py diff previous:terms:e4b latest:terms:e4b                      # or two directories or globs
scripts/eval-score.py diff A B --noise 3                                            # noise band, default 5
```

`diff` prints what changed first (SHA, prompt hash, model, window, brief), then each metric as A, B, delta and `+` (better),
`-` (worse), `~` (inside the noise band), `=` (same), `?` (no direction known). The band is 5 percentage points for rates and
5 % of A for counts and costs, because the documented run-to-run noise is prose, not machine-readable (about 1.5 points of
dominant share and 2 points of flagged in the sequence eval, see 15e.13). The fast loop for a prompt round: edit the prompt,
run the offline `StageEvalRunnerTest` and `scripts/test-focused.sh`, then one `:pipeline:promptEval` run with a label, then `diff`.
`python3 scripts/eval-score.py selftest` and `python3 -m unittest discover -s scripts/tests` check the script.

**Prompt round 6 — draft rules rewrite (plan 16a, Phase C): reverted, no gain.** Control = HEAD `bd629759` (prompt hash `95c7d4ac6dc2`),
candidate = the rewritten DRAFT/BATCH-DRAFT rules + end checklist, token rules moved into the conditional `[Immutable tokens]` block,
the style default dropped when a register line follows, and uk/ru quotes, dialogue and venue-name lines. LM Studio `google/gemma-4-e4b`,
`burning-chrome` preset, window 16384, three runs per arm (`.temporary_context/eval-history/*r6-control*`, `*r6-cand*`, `*r6-split*`). Run-to-run spread of
the control was zero on every group (the seed is fixed per call), so a range proves less than it looks: one case flipping is one sample.
Moved for the better: `ascii-dialogue/draft` 33 % → 67 % (0.67..1.0), replay-fast pass 90 % (90..92.5) → 95 %, `injection-hello` fixed, default
`falsePositive` 17.6 % → 5.9 %. Did not move: `venue-name/draft` 0/2 (the model now writes «Синього якоря», still the genitive), `slang/draft`
60 % (0.4..0.6), `numbers/draft` 67 %, batch `tooShort`. Regressed: `quotes/draft` 100 % → 89 % (`quotes-unclosed-2` unparsed on an all-ASCII source),
default `gate` 94.3 % → 91.4 % (`link-pair` put its token pair around nothing, `names-no-token` left "Vance" in Latin). The split without the token
move kept the ascii gain (100 %) but lost the replay gain and `quotes/draft` fell to 78 %. The candidate was also not shorter: the token rules
moved into the user message (+100 words) and the uk language lines grew by 88 words. The decision rule (two targets improved and no key metric
worse than the control spread) was not met, so the edit was reverted; the idea worth retrying is the quotes line alone with a worked ASCII-quote example
(round 8).

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

### Thinking per call and the notes arm (15i.X2) {#x2-arms}

Published studies find that thinking does not help translation itself, so every call asks the model not to think, as it
always did. Two switches let the eval measure where that is wrong; neither changes a run that does not set them.

| Switch | Meaning | Eval wiring |
|---|---|---|
| `BOOKLOOM_REASONING="low"` or `"DRAFT_BATCH_JSON=on,TERM_READING=low"` (else `-Dbookloom.reasoning`) | a bare `off`, `low` or `on` sets the level of every call that is not named; `NAME=on\|low\|off` sets one `PromptName` call (names case-insensitive, `-` read as `_`, the last entry for a name wins, a malformed or unknown entry is ignored with one WARN; the environment wins over the property). **Set, it replaces the stored Generation setting entirely; unset, the stored setting (`run.reasoning`, Off when nothing is stored) applies to every call.** `BOOKLOOM_SAMPLING` (`server`\|`tuned`) works the same way against `run.sampling` | `BOOKLOOM_EVAL_REASONING`, `scripts/eval-matrix.sh --reasoning "<spec>"` |
| `BOOKLOOM_BATCH_NOTES=on\|off` (else `-Dbookloom.batch.notes`, default off) | the batch draft writes a one-line `notes` string before each `target` (hard phrases and idioms); the code reads it only to log it at TRACE and never validates or stores it | `BOOKLOOM_EVAL_BATCH_NOTES`, `scripts/eval-matrix.sh --batch-notes on` |

A call that thinks sends Ollama `think:true` (`"low"` at Low) and LM Studio `reasoning_effort:"medium"` (`"low"` at Low), and its output cap grows by 1,024
tokens for the reasoning (a gpt-oss model gets that room once, thinking or not); the Ollama runaway line guard is sized
from that grown cap and does not count the thinking lines. The report's provenance records `reasoning` and `batchNotes`.

```bash
scripts/eval-matrix.sh --suite realrun --reasoning "REVIEWER=on" --models "ollama:gemma4:e4b-mlx ollama:gemma4:26b-mlx"
scripts/eval-matrix.sh --suite batch --batch-notes on --models "ollama:gemma4:e4b-mlx"
scripts/eval-matrix.sh --suite names --reasoning "TERM_READING=on,TERM_SUMMARY=on,BRIEF_SYNTHESIS=on"
```

Compare each arm with the same suite without the switch: a call kind turns on only where its target moves by 2+ with its
time up by at most 30 %. The measured table goes here when the round is run. The live request shape of both dialects is
checked by `chat_thinkingOn_isAcceptedAndStillAnswers` in `OllamaLiveTest` and `LmStudioLiveTest` (`./gradlew liveLocal`).

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

The fit tests measure text with the platform's font, so they can pass on macOS and fail on the Ubuntu runner. Run them
there before pushing a UI change (needs a Docker daemon, e.g. `colima start --cpu 2 --memory 4`; the first run builds a
small image and compiles, later runs reuse both):

```bash
scripts/ci-linux-ui.sh     # the fit tests on Ubuntu; add --tests '*SomeTest' for another :ui selection
```

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
module-path launch (`--module-path` / `--module ua.bookloom.app/…Launcher`, see `03_PACKAGING_JPACKAGE.md#module-path`),
`--java-options "-Dbookloom.env=prod"` and a stripped jlink runtime of the JDK modules (built by the script: automatic modules
cannot be jlinked, the jars stay on the module path). The Windows `.bat` still launches on the classpath.
`APP_VERSION` overrides the Gradle version (default `dev`; jpackage's own `--app-version` is coerced to a numeric
placeholder). There is no cross-compilation: each image is built on its own OS.

| OS | Command | Needs | Produces under `build/package/` |
|---|---|---|---|
| macOS | `./scripts/package-macos.sh` | a JDK 25 with `jpackage` | `BookLoom.app`, `BookLoom-<version>-macos-<arch>.tar.gz`, `.dmg` |
| Linux | `./scripts/package-linux.sh` | a plain JDK 25 (Temurin) with `jpackage`; `fakeroot` for the `.deb` | `BookLoom/`, `…-linux-<arch>.tar.gz`, `.deb` |
| Windows | `scripts\package-windows.bat` | Liberica 25 Full (`jdk+fx`) | `BookLoom\BookLoom.exe` (app-image only, no zip) |

**Launch smoke** (POSIX only):

```bash
./scripts/launch-smoke.sh [path/to/image]     # SMOKE_TIMEOUT_SECONDS=90 by default
```

It launches the packaged image against a throwaway `BOOKLOOM_DATA_DIR` with `-J-Dbookloom.smoke.resolveAll=true` and
asserts the three startup log lines from [§5](#running) and a fourth, `resolve-all passed: views=<n> ports=<m>`, appear
in `<dataDir>/logs/bookloom.log`. The property makes the image build its injector in Guice's production stage and, once
the window is shown, load every screen and resolve every `ua.bookloom.api` port; a `resolve-all FAILED` line names
what failed and ends the smoke at once. The image runs on the **module path**, where
`module-info` is enforced, so a missing `requires`, `exports` or `opens` stops the start (proved by removing `opens ua.bookloom.ui.screen`); the step also catches
a resource left out of the image, a Guice binding that cannot be built in the production stage, a screen whose FXML
or controller cannot load. (`ResolveAllTest` in `:app` runs the same step in the test JVM.) On Linux the packaged app starts the real GTK toolkit, so a
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
Windows. Install Liberica 25 Full there ([§1](#prerequisites)); never copy jmods by hand.

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
