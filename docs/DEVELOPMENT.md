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

There is no `gradle.properties`; one you add is not ignored by git.

---

## 2. Quick start {#first-build}

```bash
git clone <repo-url> && cd book_loom
./gradlew build          # compile, lint, test
./gradlew :app:run       # opens a 1024x700 window titled "BookLoom"
```

The full gate — what pre-push and CI run — is:

```bash
./gradlew clean build check spotlessCheck
```

About one minute with a warm daemon. Note your own baseline: a run materially longer than it is treated as hung.

---

## 3. Layout and modules {#project-structure}

```
AGENTS.md                  operating manual (agents)         docs/Architecture.md   what is built, how to verify it
docs/specification/        the spec — editable reference     docs/adr/              decisions (ADR-0001 …)
docs/implementation_plan/  CHANGE_BACKLOG.md (order of work), 01_MODULE_INVENTORY.md (as-built log), 07_ROADMAP.md,
                           notes-corpus-verification.md (216-book sweep results), 04_ADR_FORMAT.md
openspec/                  changes/<name>/ = a unit of work; specs/ = ledger of built behaviour; archive/ = history
modules/                   all code (see below)              scripts/               jpackage per OS, launch smoke, agent-file sync
config/                    checkstyle, spotbugs, licence allowlist
tooling/hooks/             helpers the git hooks call        .lefthook/pre-push/    the pre-push scripts
```

Gradle project names are `:api` … `:app` even though the directories sit under `modules/` (ADR-0021).

| Module | Directory | Status | Holds |
|---|---|---|---|
| `:api` | `modules/api` | real | `Result`, `AppError`, `ErrorCode`, `SafeDetails`, the document model, `DocumentPort`, the chat-model and translation-engine contracts (`ua.bookloom.api.llm`, `ua.bookloom.api.pipeline`) |
| `:util` | `modules/util` | real | per-OS paths, dev/prod environment, hashing |
| `:document` | `modules/document` | real | EPUB/FB2/Markdown/TXT parse → mask → unmask → write back → close |
| `:llm` | `modules/llm` | real | the `ChatModel`/`ChatModelFactory` contract, and the offline deterministic `pseudo` model |
| `:pipeline` | `modules/pipeline` | real | the translation engine, the pausable job (pause/resume/cancel), checked export |
| `:persistence` | `modules/persistence` | empty | one Guice module with no bindings |
| `:ui` | `modules/ui` | placeholder | `Theme`, `theme.css`, a `StackPane` with one label |
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
| `bookloom.test-conventions` | all 8 | JUnit 5 + AssertJ + WireMock + TestFX on the test classpath, headless system properties, the four local-only tasks `liveLocal` `promptEval` `visual` `corpus` |
| `bookloom.coverage-conventions` | the six FX-free modules | JaCoCo branch coverage ≥ 0.80 ([§8](#coverage)) |
| `bookloom.javafx-conventions` | `:ui`, `:app` | JavaFX 26 with per-OS classifiers, `--enable-native-access` on test tasks, the lock carve-out |

The root build adds one thing: the licence gate (`checkLicense`, [§9](#quality-gate)).

---

## 5. Run and debug {#running}

```bash
./gradlew :app:run                # 1024x700 window titled "BookLoom"
./gradlew :app:run --debug-jvm    # suspends before main, listening on 127.0.0.1:5005
```

`run` is a plain `JavaExec` with main class `ua.bookloom.app.bootstrap.Launcher`, launched on the **classpath**. A
green `:app:run` therefore says nothing about the JPMS graph; the packaged image is what proves that ([§10](#packaging)).

**Three warnings print on every run and none matters**: `Unsupported JavaFX configuration: classes were loaded from
'unnamed module'` (the classpath launch), `System::load has been called by … NativeLibLoader` (JEP 472; native access
is enabled for test tasks only), and `sun.misc.Unsafe … HiddenClassDefiner` (Guice internals) — the last of these is
switched off on `:app:translate` below, but not here.

**The startup log line** tells you which environment and directories a run used:

```
INFO [main] ua.bookloom.app.bootstrap.Launcher - app started version=dev environment=DEV dataDir=… logDir=…
INFO [JavaFX-Launcher] … BookLoomApplication - injector built and two-phase init complete
INFO [JavaFX Application Thread] … BookLoomApplication - primary stage shown
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
`modules/<module>/build/test-logs/test.log`, configured today for `:document`, `:llm` and `:pipeline`
(`src/test/resources/logback-test.xml`; the other modules have none yet). Read one at `TRACE` with
`BOOKLOOM_LOG_LEVEL=TRACE ./gradlew :<module>:test --tests '<class>' --rerun`.

**A development run's `DEBUG` default is verbose**: about 37 lines per segment (one 5,000-segment book wrote roughly
50 MB of log at `TRACE`), so translate a large book with `BOOKLOOM_LOG_LEVEL=INFO` unless you are actually following
one segment through its log.

**Command line.** `./gradlew :app:translate` runs `ua.bookloom.app.bootstrap.TranslateLauncher`, which repeats
`Launcher`'s pre-injector steps — paths, single-instance lock, logging — without starting JavaFX, then runs
`ua.bookloom.app.cli.TranslateCommand`:

```bash
./gradlew -q :app:translate --args="'<book>' [--to <lang>] [--from <lang>] [--overwrite]"
```

It translates `<book>` with the deterministic offline `pseudo` model — the text comes back upper-cased — and writes
`<name>.<to><suffix>` beside it; `--to` defaults to `uk`. `-q` keeps Gradle's own build chatter out of the way, so
the command's one-line report is what you see; `--overwrite` allows replacing an existing destination.

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

---

## 6. IDE (IntelliJ IDEA) {#ide}

Open the repository root, let IDEA import the Gradle build with the wrapper, and create a **Gradle** run
configuration (not an Application one) with tasks `:app:run` — it runs the same `JavaExec` the CLI does. For
debugging, run `./gradlew :app:run --debug-jvm` and attach a **Remote JVM Debug** configuration to
`localhost:5005` (the socket binds to `127.0.0.1` only). An Application configuration may launch `:app` on the module
path; prefer the Gradle one.

---

## 7. Testing {#testing}

| Command | Runs |
|---|---|
| `./gradlew test` | every test except the four local-only tags |
| `./gradlew :document:test` | one module |
| `./gradlew :document:test --tests 'ua.bookloom.document.mask.*'` | one package or class (pattern) |
| `./gradlew :document:test --tests '*Fb2WriterTest.write_lineFeedSource_keepsBareLineFeeds'` | one method |
| `./gradlew :app:archTest` | the eight ArchUnit rules (also part of `check`) |

**Headless UI.** Every `Test` task gets `-Dglass.platform=Headless -Dprism.order=sw -Djava.awt.headless=true` from
`bookloom.test-conventions`: JavaFX 26's built-in headless platform, not Monocle. No display server is needed.

**Local-only sets** — tag and task share a name; none is part of `check` or CI; a task with no matching test is
green (`failOnNoDiscoveredTests = false`):

```bash
BOOKLOOM_CORPUS_DIR=/path/to/books ./gradlew :document:corpus      # the 216-book sweep (never up-to-date)
BOOKLOOM_CORPUS_REPORT_DIR=/path ...                                 # optional: where the JSONL/TSV report lands
./gradlew liveLocal   promptEval   visual                            # reserved: no tests carry these tags yet
```

`corpus` is the only tag with tests behind it today. Without `BOOKLOOM_CORPUS_DIR` the corpus test skips via a JUnit
assumption; a set-but-missing directory is a hard failure. The last recorded run is in
`docs/implementation_plan/notes-corpus-verification.md`.

**Conventions** (full rules in `.claude/rules/testing.md`):

- A test is named `method_state_expected`; when the name is not enough, one plain-language comment line above it
  says what it proves. There are no requirement-id markers and no coverage script (ADR-0032) — the test is the evidence.
- Write the acceptance test first and see it fail before the implementation exists.
- Mock only I/O and non-determinism, never the boundary the test exists to prove. The repository has no Mockito;
  document tests run on real bytes in `@TempDir`, and the LLM seam will be WireMock at the HTTP level.
- No `if`/`for`/`while` in a test body — use `@ParameterizedTest`. Never recompute the expected value with the
  production algorithm.

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
