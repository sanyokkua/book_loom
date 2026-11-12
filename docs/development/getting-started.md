# Getting started

*How to set up a machine, build and run BookLoom, supply a provider key, and package, clean and troubleshoot it.*

## Prerequisites {#prerequisites}

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
purpose ([focused loop](testing.md#test-speed)).

---

## Quick start {#first-build}

```bash
git clone <repo-url> && cd book_loom
./gradlew build          # compile, lint, test
./gradlew :app:run       # opens the BookLoom window (1024x700, minimum 960x640)
```

The full gate — what pre-push and CI run — is:

```bash
./gradlew clean build check spotlessCheck
```

About 20 minutes on the owner's machine (measured 2026-11-11 at the end of `redesign-translation-pipeline`: 17m 45s
to 20m 17s from clean; it was 4m 13s on 2026-10-01). While coding, use the focused loop
instead ([Test speed and the focused loop](testing.md#test-speed)). Note your own baseline: a run materially longer than it is treated as hung.

---

## Provider keys {#provider-keys}

Step-by-step commands for each operating system are in [provider-keys.md](provider-keys.md).

A provider that needs a key (LM Studio with a key, OpenAI, a company gateway) never holds the key in BookLoom. In
Settings › Providers the form's **Key variable** field takes the **name** of an environment variable (for example
`LMS_KEY`); only that name is saved (`ProviderConfig.credentialRef` in `:api`). The form refuses a key typed where the
name belongs, and a custom header whose value looks like a credential must be written as `${env:NAME}`. When a request
is sent, `RequestHeaders` in `:llm` resolves the name to its value and puts it in the sign-in header (`Authorization:
Bearer <key>`, or the header you name for the API-key type); an unset variable refuses the call with a
`missingCredential` error instead of sending it unsigned, and the value is never logged, stored or shown.

BookLoom's one lookup (`ua.bookloom.app.SystemEnvironmentVariables`) reads its own process environment first, then the
operating system's store of your user variables, and re-reads at call time (answers are cached for two seconds), so a
variable set while BookLoom runs is seen without a restart; the form shows the variable as "set" once it can be read.
An app opened from the Dock, the Start menu, a file manager or an IDE does **not** see `export` lines in `~/.zshrc` or
`~/.bashrc`, so set the variable where the app looks:

| OS | Where | Set it with |
|---|---|---|
| macOS | the login session (`launchctl getenv`) | `launchctl setenv LMS_KEY <key>`; to survive a reboot, a LaunchAgent that runs the same command at load |
| Windows | your user variables (the registry user and machine environment) | `setx LMS_KEY <key>`, or System Properties › Advanced › Environment Variables |
| Linux | `~/.config/environment.d/*.conf` or `/etc/environment` | a `LMS_KEY=<key>` line, then log in again |

`:app:run` and `:app:translate` use the same lookup, so a Gradle daemon started before you exported a key still finds
it through the OS store.

---

## Layout and modules {#project-structure}

```
AGENTS.md                  operating manual (agents)         docs/Architecture.md   what is built, how to verify it
docs/development/         this guide (getting started, running, testing, quality and CI)
docs/evaluation/          eval suites and the measured results of the prompt and pipeline rounds
docs/spec_baseline/specification/        the spec — editable reference     docs/spec_baseline/adr/              decisions (ADR-0001 …)
docs/spec_baseline/implementation_plan/  CHANGE_BACKLOG.md (order of work), 01_MODULE_INVENTORY.md (as-built log), 07_ROADMAP.md,
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
| `:llm` | `modules/llm` | real | the `pseudo` model, the Ollama-native and OpenAI-compatible clients (retry, single-flight gate, three-stage verification), detected server engines, per-model profiles, model discovery |
| `:pipeline` | `modules/pipeline` | real | the translation engine, the pausable job (pause/resume/cancel, which also abort a model request in flight), checked export; prompt templates, chunking, the stored-project service, deterministic QA checks, the one repair behind the never-worse gate, the Proofread stage, the context builder, translation memory and chapter summaries |
| `:persistence` | `modules/persistence` | real | in-memory adapters behind every `:api` storage port (ADR-0034; command line, tests) and SQLite + JDBI adapters for the window (`bookloom.db`, ADR-0043; Flyway only from the first release); `RepositoryContractTest` runs against both |
| `:ui` | `modules/ui` | real | the shell, the workflow screens (Import, Book Brief, Names & style, Translating, Export), Projects and Settings (Providers, Models, Generation, Appearance, Automation, Storage & logs), the state mirror and viewmodels, `en`/`uk` bundles, the theme |
| `:app` | `modules/app` | real | launcher, logging bootstrap, single-instance lock, Guice root, the command-line translator, the `archTest` suite |
| `build-logic` | `modules/build-logic` | — | the five convention plugins; an **included build**, not a subproject |

Allowed dependency edges point downward only: `:app`/`:ui` → `:pipeline` → `:document`/`:llm`/`:persistence` →
`:util` → `:api`. Only `:ui` and `:app` may see JavaFX. ArchUnit enforces this ([Testing](testing.md#testing)).

---

## Build configuration {#build-config}

**Toolchain.** `bookloom.java-conventions` declares `languageVersion = 25`. The JVM Gradle runs on (from
`JAVA_HOME`/`PATH`) and the JDK that compiles BookLoom (auto-detected against 25) are chosen independently, so
`java -version` says nothing about whether the build works. With no JDK 25 detected, configuration succeeds and the
first `compileJava` fails ([Troubleshooting](#troubleshooting)).

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
./gradlew --write-verification-metadata sha256 resolveAndLockAll --write-locks help   # regenerate gradle/verification-metadata.xml after a dependency change; the JavaFX jars of the other platforms (linux, linux-aarch64, win, mac) are added by hand from Maven Central, because only the host platform resolves
# The build-script classpath resolves differently per host (plugin-portal .module/.pom files a macOS cache never fetched), so after a
# regeneration also run it once on Linux against an EMPTY Gradle cache and keep the additions (same bytes from both repositories):
#   docker run --rm -v "$PWD":/src:ro -v <fresh-volume>:/root/.gradle -v "$PWD/.temporary_context/vout":/out bookloom-ci-ui bash -ec \
#     'rsync -a --exclude build --exclude .git --exclude .temporary_context /src/ /w/; cd /w; ./gradlew --no-daemon --write-verification-metadata sha256 help :ui:compileTestJava :app:compileTestJava; cp gradle/verification-metadata.xml /out/'
# then copy the file back; `scripts/ci-linux-ui.sh` with a fresh cache proves it.
```

`:app` and `:ui` produce **no lockfile diff** by design: `bookloom.javafx-conventions` deactivates locking on their
compile/runtime classpaths because a lock entry cannot record which per-OS JavaFX classifier resolved. Verify a new
`:app` dependency in the catalog, not the lockfile.

**Convention plugins** (`modules/build-logic/src/main/kotlin/`):

| Plugin | Applied to | Sets |
|---|---|---|
| `bookloom.java-conventions` | all 8 | JDK 25 toolchain, `--release 25`, `-Werror` + `-Xlint:all` (minus a few carve-outs), Lombok as the only annotation processor, Error Prone + NullAway, Checkstyle, SpotBugs + FindSecBugs, dependency locking, version constraints, `resolveAndLockAll`/`verifyLocks` |
| `bookloom.spotless-conventions` | all 8 | Palantir Java Format, 120 columns |
| `bookloom.test-conventions` | all 8 | JUnit 5 + AssertJ + WireMock + TestFX on the test classpath, headless system properties, parallel test forks, `fastTest` (everything but `slow`), the five local-only tasks `liveLocal` `promptEval` `visual` `corpus` `soak` |
| `bookloom.coverage-conventions` | the six FX-free modules | JaCoCo branch coverage ≥ 0.80 ([Coverage](quality-and-ci.md#coverage)) |
| `bookloom.javafx-conventions` | `:ui`, `:app` | JavaFX 26 with per-OS classifiers, `--enable-native-access` on test tasks, the lock carve-out |

The root build adds one thing: the licence gate (`checkLicense`, [Quality gate](quality-and-ci.md#quality-gate)).

---


## IDE (IntelliJ IDEA) {#ide}

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


## Packaging {#packaging}

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
asserts the three startup log lines from [Run and debug](running.md#running) and a fourth, `resolve-all passed: views=<n> ports=<m>`, appear
in `<dataDir>/logs/bookloom.log`. The property makes the image build its injector in Guice's production stage and, once
the window is shown, load every screen and resolve every `ua.bookloom.api` port; a `resolve-all FAILED` line names
what failed and ends the smoke at once. The image runs on the **module path**, where
`module-info` is enforced, so a missing `requires`, `exports` or `opens` stops the start (proved by removing `opens ua.bookloom.ui.screen`); the step also catches
a resource left out of the image, a Guice binding that cannot be built in the production stage, a screen whose FXML
or controller cannot load. (`ResolveAllTest` in `:app` runs the same step in the test JVM.) On Linux the packaged app starts the real GTK toolkit, so a
display is required — CI starts Xvfb before the smoke. The app icons `jpackage` reads are committed under
`docs/spec_baseline/specification/assets/icon/dist/`; regenerate them from `appicon.png` with the scripts beside it.

---

## Cleanup {#cleanup}

```bash
./gradlew clean                          # build/ at the root and modules/*/build (reports included)
./gradlew -p modules/build-logic clean   # the included build — root clean does not reach it
rm -rf .gradle .kotlin                   # Gradle's project cache; safe, costs one re-configure
```

Fresh app state means deleting the data and log directories from [Run and debug](running.md#running), e.g. on macOS
`~/Library/Application Support/BookLoom-Dev` and `~/Library/Logs/BookLoom-Dev`. The corpus report directory is
whatever `BOOKLOOM_CORPUS_REPORT_DIR` pointed at, or a `bookloom-corpus-report-*` temp directory.

---

## Troubleshooting {#troubleshooting}

**`Cannot find a Java installation … matching {languageVersion=25}`** — no detectable JDK 25 and no
auto-provisioning. Install one ([Prerequisites](#prerequisites)) and confirm with `./gradlew -q javaToolchains`.

**`:app:run` appears to hang, then exits 0 after five minutes** — another instance holds `bookloom.lock` (often a
forgotten IDE launch). The second launch shows a "BookLoom is already running" window, waits up to 300 s for it to be
dismissed, then exits 0. Dismiss it, or find the first instance (`jps -l`) and its lock file in the data directory.

**A catalog pin changes nothing** — only a `constraints {}` entry in `bookloom.java-conventions` makes a version
bind. `grep -rn 'libs\.<alias>' --include='*.kts' .`; no hit means decoration.

**`:app` shows no lockfile diff after adding a dependency** — correct ([Build configuration](#build-config)); check the catalog.

**A gate run takes materially longer than your baseline** — treat it as hung: kill it and diagnose. Never run two
gates at once; they contend for the same daemon and outputs.

**A UI test wants a real display** — a hand-rolled `Test` task that skipped `bookloom.test-conventions`. Let the
convention configure it; do not reach for Monocle.

**`jpackage is not on PATH`, or it fails on missing JavaFX jmods** — a JRE instead of a JDK, or a plain JDK on
Windows. Install Liberica 25 Full there ([Prerequisites](#prerequisites)); never copy jmods by hand.

**Packaging fails with `Specified icon file … does not exist`** — the committed icons are missing from your checkout;
regenerate them (`docs/spec_baseline/specification/assets/icon/README.md`).

**`scripts/*.sh` or the hooks do not run on Windows** — run them from Git Bash or WSL, with Python 3 installed.
