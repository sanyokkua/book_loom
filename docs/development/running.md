# Running and debugging

*How to run the app and the command line, where they write, how logging works, and the headless end-to-end proof.*

## Run and debug {#running}

```bash
./gradlew :app:run                # the BookLoom window: opens 1024x700, minimum 960x640
./gradlew :app:run --debug-jvm    # suspends before main, listening on 127.0.0.1:5005
```

`run` is a plain `JavaExec` with main class `ua.bookloom.app.bootstrap.Launcher`, launched on the **classpath**. A
green `:app:run` therefore says nothing about the JPMS graph; the packaged image is what proves that ([Packaging](getting-started.md#packaging)).

**Three warnings print on every run and none matters**: `Unsupported JavaFX configuration: classes were loaded from
'unnamed module'` (the classpath launch), `System::load has been called by … NativeLibLoader` (JEP 472; native access
is enabled for test tasks only), and `sun.misc.Unsafe … HiddenClassDefiner` (Guice internals) — the last of these is
switched off on `:app:translate` below, but not here.

**Provider keys reach `:app:run` too.** A Gradle daemon started before you exported a key never sees it, and an IDE or a
Dock launch never sees a shell's `export`. BookLoom's one lookup (`ua.bookloom.app.SystemEnvironmentVariables`) reads
the process environment, then the user's OS store — `launchctl getenv` on macOS, the registry user and machine
environment on Windows, `environment.d` and `/etc/environment` on Linux — at call time, so `launchctl setenv NAME
value` works without restarting the daemon or the window. How to set a key per OS: [Provider keys](getting-started.md#provider-keys).

**What the window can do.** The whole workflow, in five numbered steps (Import, Book Brief, Names & style, Translating, Export), plus Projects and Settings. *Import*: open a book of any of the
four formats (EPUB, FB2, Markdown, TXT); the card reports its format, declared language, cover and chapter count, refuses
a DRM-protected or unsupported file, and shows the book's structure tree and its round-trip check. *Book Brief*: source
and target language (any language the JDK can name, or Auto for the source), tone and style, the name, foreign-passage,
footnote and unit policies, who narrates, the quality dial (Fast, Balanced, Max), the "Also translate" switches and a
file name the model can suggest. *Names & style*: the glossary and the recurring terms, each with search, Scan, Review
with model, Translate and CSV import and export, or one "Prepare names automatically" pass with Undo all. *Translating*:
start, pause, resume, stop; a live panel with every model call, the activity log, the provider-error banner with Retry
now and Skip segment, and the review panel — accept, edit, revert, leave for later, apply a proposal, retry with a note — inside
the same screen. *Export*: Save to, the side files (glossary CSV, bilingual HTML, quality report),
and the export-complete dialog (export makes no model call) with a button that shows the written file in the file manager; a stopped or partial run
can be exported, and what it did not translate stays in the source. Verify a provider on Settings (Providers: connection,
models, then a test inference, each measured), choose a model, and start. Pause and Stop abort the request in flight.
The window translates one book at a time: opening another book while a translation is running or paused asks first and stops it; the finished segments are saved and the run continues from Projects. The Projects screen lists, opens, continues, renames and removes the stored projects, and the app keeps its settings, providers, books and projects across restarts (below).

**The review mode** is chosen in Settings › Automation ("When to stop and ask you": `Unattended`, `Assisted` or `Manual`; stored as `run.reviewMode`, applied to the next run start). A launch flag overrides the stored choice for that launch: `BOOKLOOM_REVIEW_MODE`, else the system property `bookloom.review.mode` (for `:app:run`, hand it to the launched JVM with `JAVA_TOOL_OPTIONS="-Dbookloom.review.mode=manual"`; Settings then names the flag that decides). It takes `unattended` (also `auto`; the default — nothing pauses), `assisted` (also `semi-manual` — the run pauses on a flagged segment and on an error) or `manual` (pauses after every segment as well). A value that names no mode counts as set, means `unattended` and is named once in the log. The command line always runs unattended and ignores the stored choice. The mode is shown on the Translating screen's ready card and in the session facts of the detailed log.

**Persistence.** The window keeps its settings, providers, books, projects and every decided segment in SQLite (`bookloom.db` in the data folder, WAL; ADR-0043), so a run can be resumed after a restart and the theme, provider, model and brief are remembered. A database whose schema or stored record shapes changed is renamed `bookloom.db.old-<time>` and a fresh one created (there is no migration before the first release). The command line and most tests use the in-memory adapters instead and never create `bookloom.db` (ADR-0034).

**Settings.** Six tabs: Providers, Models, Generation, Appearance (theme and interface language), Automation and Storage & logs.

**Interface language.** The first launch follows the operating system: a Ukrainian locale shows `uk`, anything else `en`. Settings › Appearance has a language chooser (`en` or `uk`, stored as `ui.language`) and a Restart now button, because the choice applies after a restart. To see Ukrainian without changing the OS or the setting, run
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

| OS | Data (holds `bookloom.lock` and `bookloom.db`) | Logs (`bookloom.log` + rolled files) |
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

**A development run's `TRACE` default is verbose**: about 37 lines per segment (one 5,000-segment book wrote roughly
50 MB of log), so translate a large book with `BOOKLOOM_LOG_LEVEL=INFO` unless you are actually following
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
model and the validated Accept all before translating and prints one `names: …` line ending in the accepted and
rejected counts (`… 34 accepted, 3 rejected`): a suggestion that fails the target language's checks stays a hint. The run pauses on an error and **recovers from
an outage by itself** with the window's schedule and probe (below) for up to `--max-outage` (default `12h`; `90m`,
`1h30m`, `45s` or ISO `PT2H`), printing a line when an outage begins; a pause only a person could end — a wrong key, a
missing model, a refused request, an outage past the limit — stops the run with `Stopping the run: <reason>`. A run that
did not complete, Ctrl+C included, **still exports what it translated** (the rest in the source) and prints
`Partial: <path> (accepted=…, flagged=…, pending=…) — the run ended <state>: <reason>`; `--no-partial` writes nothing
instead. `--report <file>` writes a JSON summary: provider, model, dial and names, phase timings and model calls, how
the run ended and why, every flagged segment with its reason and finding kinds, each outage, the names review counts and
what the export wrote, source fallbacks included. `modelCallsByKind` in it holds, per call kind (draft, repair, proofread …), the attempts and failures, prompt and completion tokens, the tokens the server says its cache served, and the
elapsed, prompt-evaluation and generation seconds — counts and times only, never book text, each figure summed from
what the calls reported (the call ledger) and left out when no call reported it; the final `run summary` log line
carries the same totals as `byKind[…]`, and the names pass, the brief suggestion and the export each end with one
`prep summary byKind[…] stage=… project=…` line in the same format. `--stop-after <n>` ends the run once `n` segments are decided
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
process, so the offline invariant is untouched. Every decided segment is stored as the run goes, so a crash or power loss costs at most the last in-flight chunk and the run continues from Projects (SQLite with `synchronous=NORMAL` is not power-loss-proof).

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
