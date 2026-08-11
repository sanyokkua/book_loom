# Developer Guide

*Living document — how to build, run, debug, test and package BookLoom on macOS, Linux and Windows. `AGENTS.md`
covers how work is planned and landed; `docs/specification/` is the frozen behaviour spec.*

---

## 1. Prerequisites {#prerequisites}

### JDK 25

Install a JDK 25 yourself — any vendor. The toolchain asks for a language version, not a vendor, and this build
declares no toolchain repository, so Gradle cannot download one ([§4](#build-config)).

| OS | Install |
|---|---|
| macOS | `brew install --cask temurin@25`, or `sdk install java 25-tem` (SDKMAN) |
| Linux | `sdk install java 25-tem` (SDKMAN), or your distribution's Adoptium/Temurin package |
| Windows | `winget install EclipseAdoptium.Temurin.25.JDK`, or the installer from <https://adoptium.net> |

```bash
java -version                  # what is on PATH
./gradlew -q javaToolchains    # what Gradle can actually see — the check that counts
```

### Gradle

Do not install Gradle. Gradle **9.6.1** comes from the committed wrapper, with a pinned `distributionSha256Sum` and
`validateDistributionUrl=true` in `gradle/wrapper/gradle-wrapper.properties`; a system `gradle` is a different,
unpinned distribution. Use `./gradlew` on macOS and Linux; on Windows use `gradlew.bat` from `cmd`/PowerShell, or
`./gradlew` from Git Bash.

### Windows also needs a POSIX shell and Python 3

Git hooks run with `runner: sh` (`lefthook.yml:98-103`); the packaging, smoke and coverage scripts are
`#!/usr/bin/env bash`; `scripts/sync-agent-files.py` needs Python 3 (`ci.yml:168`). Install **Git Bash** (bundled with
Git for Windows) or **WSL**, plus **Python 3**.

Line endings are already handled — `.gitattributes` normalises to LF and keeps CRLF for `*.bat`, `*.cmd` and
`gradlew.bat`, and `.editorconfig` matches. Do not change `core.autocrlf` to work around a formatting failure.

### Optional tools

| Tool | Needed for | macOS / Linux | Windows |
|---|---|---|---|
| `lefthook` | Git hooks (inactive until installed per clone) | `brew install lefthook` | `scoop install lefthook`, or the release binary from <https://github.com/evilmartians/lefthook/releases> |
| `gitleaks` | The pre-commit secret scan; hooks fail without it | `brew install gitleaks` | `scoop install gitleaks`, or the release binary from <https://github.com/gitleaks/gitleaks/releases> |
| `fakeroot` | The Linux `.deb`; without it `package-linux.sh` warns and skips it | `apt install fakeroot` | n/a |
| `NVD_API_KEY` | The OWASP SCA gate in CI; unset is fine locally | — | — |

### Liberica 25 "Full" for packaging

Packaging on **Windows** and on **Linux aarch64** requires Liberica 25 **"Full" (`jdk+fx`)** — download the *Full
JDK* from <https://bell-sw.com/pages/downloads/>. A plain JDK on those two platforms lacks the JavaFX jmods
`jpackage` links into the image, and `scripts/jpackage-common.sh:95-102` fails fast when they are missing.
Hand-copying jmods is never the fix (EC-REL-4). macOS and Linux x86_64 package with a plain JDK 25; `ci.yml:284-294`
uses `distribution: liberica`, `java-package: jdk+fx` on exactly the two legs that need it.

### There is no `gradle.properties`

The repository ships none, and one you add is not in `.gitignore`.

---

## 2. Quick start {#first-build}

```bash
git clone <repo-url> && cd book_loom
./gradlew build          # compile, lint, test
./gradlew :app:run       # opens a 1024x700 window titled "BookLoom"
```

On Windows use `gradlew.bat build` and `gradlew.bat :app:run`.

The full gate is `./gradlew clean build check spotlessCheck` — ~1–2 min with a warm daemon. Capture your own
baseline; the Definition of Done treats "materially longer than baseline" as evidence of a hang.

---

## 3. Project structure {#project-structure}

Eight code directories live under `modules/`; each project's Gradle path stays `:api` … `:app`, because
`settings.gradle.kts` repoints `projectDir` after `include(...)` (ADR-0021). Command lines are unaffected:
`./gradlew :app:run` is correct. Only `build-logic` takes a prefix, as `-p modules/build-logic`.

| Gradle project | Directory | JPMS module | State | Contents |
|---|---|---|---|---|
| `:api` | `modules/api` | `ua.bookloom.api` | real | `Result`, `AppError`, `ErrorCode`, port interfaces. The dependency floor. |
| `:util` | `modules/util` | `ua.bookloom.util` | real | `AppPathsResolver`, `AppEnvironment`, `OsFamily`, hashing. |
| `:document` | `modules/document` | `ua.bookloom.document` | real, **EPUB only** | Skeleton + segment model, masking, reassembly, EPUB repackaging. |
| `:llm` | `modules/llm` | `ua.bookloom.llm` | stub | A Guice module and `package-info`. |
| `:pipeline` | `modules/pipeline` | `ua.bookloom.pipeline` | stub | Same. |
| `:persistence` | `modules/persistence` | `ua.bookloom.persistence` | stub | Same. |
| `:ui` | `modules/ui` | `ua.bookloom.ui` | placeholder | `AppShellView`, `Theme`, `theme.css`. |
| `:app` | `modules/app` | `ua.bookloom.app` | real | `Launcher`, `BookLoomApplication`, the Guice composition root, the `archTest` source set. |
| `build-logic` | `modules/build-logic` | — | real | Five convention plugins with their own test suite. An **included build**, not a subproject. |

Allowed dependency edges:

```
:app  →  :ui :pipeline :document :llm :persistence :api :util
:ui   →  :pipeline :api :util
:pipeline → :document :llm :persistence :api :util
:document / :llm / :persistence → :api :util
:util →  :api
:api  →  (nothing internal)
```

Any other internal edge fails the `dependency-direction` ArchUnit rule. Only `:ui` and `:app` may see JavaFX — they
apply `bookloom.javafx-conventions`, the other six do not, and `fx-free-core` enforces it in bytecode.

---

## 4. How the build is configured {#build-config}

### Java toolchain

`bookloom.java-conventions.gradle.kts` declares `languageVersion = JavaLanguageVersion.of(25)`. Two JVMs are in
play and they are chosen independently:

| JVM | What it is | Chosen by |
|---|---|---|
| Launcher / Daemon JVM | the JVM Gradle itself runs on | `JAVA_HOME` and `PATH` |
| Toolchain JVM | the JDK that compiles and runs BookLoom | Gradle auto-detection against `languageVersion=25` |

Toolchain resolution ignores `PATH`, so `java -version` reporting 21 does not break the build and reporting 25 does
not guarantee it. Auto-provisioning is unavailable — no toolchain repository is declared anywhere — so auto-detection
must succeed. `Auto-download: Enabled` in `javaToolchains` output reflects the feature flag, not a configured
repository. When no JDK 25 is detected, configuration succeeds and the first `compileJava` task fails
([§10](#troubleshooting)).

### Version catalog {#dependencies}

Every coordinate lives in `gradle/libs.versions.toml`, referenced as `libs.<alias>` and pinned exactly; no build script
carries an inline `group:artifact:version`. Precompiled script plugins in `build-logic` read the catalog by name
(`catalog.findLibrary("guice").orElseThrow()`) because the generated `libs.*` accessors exist only in the main build.

JavaFX is **26.0.2**, ahead of the JDK: DD-02 chose JavaFX 25 and **ADR-0019 supersedes it**, because JavaFX 26 is the
first release with a built-in headless glass platform and `org.testfx:openjfx-monocle` has no build past 21.0.2.
SQLite, Flyway, JDBI, CommonMark, Jackson, Lingua, ICU4J, AtlantaFX, Ikonli and ControlsFX are specified but not yet in
the catalog; each arrives with the change that first uses it.

### Dependency locking

```bash
./gradlew -PstrictLocks verifyLocks                          # the gate: resolve everything, write nothing
./gradlew -p modules/build-logic -PstrictLocks verifyLocks    # the included build, separately
./gradlew resolveAndLockAll --write-locks                     # regenerate after a catalog bump
./gradlew -p modules/build-logic resolveAndLockAll --write-locks
```

Gradle has no command-line flag for the lock *mode*, so STRICT is opted into with `-PstrictLocks`.
`resolveAndLockAll` fails without `--write-locks`; `verifyLocks` fails with it. `:app` and `:ui` produce no lockfile
diff by design — `bookloom.javafx-conventions` calls `deactivateDependencyLocking()` on their compile/runtime
classpaths, because Gradle's lock state keys on `group:name:version` and cannot record which per-OS JavaFX
**classifier** resolved. Verify a new `:app` dependency's version in the catalog, not the lockfile.

### Convention plugins

| Plugin | Applied to | Provides |
|---|---|---|
| `bookloom.java-conventions` | all 8 | Java 25 toolchain, Error Prone + NullAway, Checkstyle, SpotBugs, dependency locking, version constraints |
| `bookloom.spotless-conventions` | all 8 | Palantir Java Format, 120 columns |
| `bookloom.test-conventions` | all 8 | JUnit 5 + AssertJ + Mockito, headless system properties, the `liveLocal`/`promptEval`/`visual` tasks |
| `bookloom.coverage-conventions` | 6 core modules | JaCoCo branch coverage ≥ 0.80 |
| `bookloom.javafx-conventions` | `:ui`, `:app` | JavaFX 26 with per-OS classifiers, `--enable-native-access`, lock carve-out |

---

## 5. Run the app {#running}

```bash
./gradlew :app:run                # 1024x700 window titled "BookLoom"
./gradlew :app:run --debug-jvm    # suspends before main, listening on 127.0.0.1:5005
```

On Windows use `gradlew.bat`. `run` is a hand-rolled `JavaExec`, not the `application` plugin (DD-24), with main
class `ua.bookloom.app.bootstrap.Launcher`. It launches on the **classpath, not the module path**, so a green
`:app:run` is not evidence the JPMS graph resolves ([§10](#troubleshooting)).

### Expected warnings on JDK 25

Three warnings print on every run and none indicates a problem:

- `Unsupported JavaFX configuration: classes were loaded from 'unnamed module …'` — the classpath launch, as designed.
- `java.lang.System::load has been called by com.sun.glass.utils.NativeLibLoader in an unnamed module` — JEP 472.
  `bookloom.javafx-conventions` adds `--enable-native-access=ALL-UNNAMED` to `Test` tasks only.
- `sun.misc.Unsafe::staticFieldBase has been called by com.google.inject.internal.aop.HiddenClassDefiner` — Guice
  internals.

### Where the app writes

Resolved by `ua.bookloom.util.paths.AppPathsResolver`; `[-Dev]` is appended in the dev environment.

| OS | Data | Logs |
|---|---|---|
| macOS | `~/Library/Application Support/BookLoom[-Dev]` | `~/Library/Logs/BookLoom[-Dev]` |
| Linux | `$XDG_DATA_HOME` or `~/.local/share/bookloom[-dev]` | `$XDG_STATE_HOME` or `~/.local/state/bookloom[-dev]/logs` |
| Windows | `%LOCALAPPDATA%\BookLoom[-Dev]` | `%LOCALAPPDATA%\BookLoom[-Dev]\logs` |

On Linux the logs sit under the **state** directory, not under the data directory. The data directory holds
`bookloom.lock`; the log directory holds `bookloom.log` plus rolled `bookloom.<date>.<n>.log` files.

### Environment

First match wins: `BOOKLOOM_ENV` (`dev`/`prod`, case-insensitive) → `-Dbookloom.env=prod` → jpackage's
`jpackage.app-path` property → **dev**. Packaged images get the stamp from `scripts/jpackage-common.sh`.

**Never set `-Dbookloom.env=prod` in a development run configuration** — it points a debug session at the user's real
book database. `BOOKLOOM_DATA_DIR`, when an absolute path, overrides the whole per-OS layout; logs land in a `logs/`
child of it.

### Startup sequence

From `Launcher`, in order:

1. Resolve `isDev` from env + system properties.
2. Resolve `dataDir` + `logDir` from `os.name`, home and env.
3. `createDirectories` — the first failure that can reach a user (EC-ENV-2).
4. `tryLock(dataDir/bookloom.lock)` — a second launch stops here (EC-ENV-3).
5. Configure Logback: publish the log dir, then start the appender.
6. Publish `StartupContext`, then `Application.launch`.

Nothing above step 5 may log: a logger that fires before configuration pins Logback to the wrong directory. `:util`
does not require SLF4J, and the `bootstrap-no-static-logger` ArchUnit rule forbids a static `Logger` on that path.
Exit codes: **0** = another instance is already running, **1** = startup failed.

---

## 6. IDE setup (IntelliJ IDEA) {#ide}

Open the repository root and let IDEA import the Gradle build, using the wrapper's distribution. Create a **Gradle**
run configuration, not an Application one:

| Field | Value |
|---|---|
| Type | Gradle |
| Name | `BookLoom (run)` |
| Gradle project | `bookloom` |
| Tasks | `:app:run` |
| Arguments | *(empty; `--debug-jvm` for the debug variant)* |
| Environment | leave `BOOKLOOM_ENV` unset |

A Gradle configuration runs the same `JavaExec` the CLI does and cannot drift from it.

### Debugging

Run `./gradlew :app:run --debug-jvm`. The JVM prints `Listening for transport dt_socket at address: 5005` and suspends
before `main`. Attach a **Remote JVM Debug** configuration:

| Field | Value |
|---|---|
| Debugger mode | Attach to remote JVM |
| Host / Port | `localhost` / `5005` |
| Transport | Socket |
| Use module classpath | the `:app` main source set of the imported Gradle project |

The socket binds to `127.0.0.1` only. Breakpoints set in the IDE bind once attached.

### Module-path caveat

`bookloom.java-conventions` sets `modularity.inferModulePath = true` and `:app` has a `module-info.java`, so an
Application run configuration is liable to launch `:app` on the module path, which currently fails:

```
java.lang.reflect.InaccessibleObjectException: Unable to make ua.bookloom.document.epub.OpenEpubRegistry() accessible:
module ua.bookloom.document does not "opens ua.bookloom.document.epub" to module com.google.guice
```

`modules/document/src/main/java/module-info.java` opens `ua.bookloom.document` to Guice but not that subpackage. Use
the Gradle configuration, or force a classpath launch.

---

## 7. Testing {#testing}

| Command | Runs |
|---|---|
| `./gradlew test` | everything except the three local-only tags |
| `./gradlew :document:test` | one module |
| `./gradlew :app:archTest` | the eight ArchUnit boundary rules |

`archTest` is a source set in `:app`, wired into `check`: `:app` is the only project whose classpath carries all eight
modules at once, which is what a rule like `dependency-direction` needs to see both ends of an edge. The rules are
`fx-free-core`, `dependency-direction`, `ports-not-concretes`, `no-http-in-core-except-llm`,
`no-sql-in-core-except-persistence`, `api-is-framework-free`, `records-first`, `bootstrap-no-static-logger`.

### Headless UI

Every `Test` task gets three system properties from `bookloom.test-conventions`:

```
-Dglass.platform=Headless    -Dprism.order=sw    -Djava.awt.headless=true
```

This is JavaFX 26's built-in headless platform (ADR-0019), not Monocle, and deliberately not `testfx.headless=true`,
which makes TestFX try to install Monocle itself. Wherever the frozen spec names Monocle, this is what runs.

### Local-only sets

```bash
./gradlew liveLocal     # real Ollama / LM Studio
./gradlew promptEval    # real model + embedding scorer
./gradlew visual        # pinned-environment snapshot diffs
```

Task name and tag name are identical. None is wired into `check`, and the exclusion is by tag in the task graph, so
the classes are never loaded during a gate run. `failOnNoDiscoveredTests = false` keeps these tasks green on a machine
with no local model.

### Conventions

Full rules in `.claude/rules/testing.md`:

- `// Covers: FR-*` plus a one-line EARS restatement above every covering test (ADR-0016 R5).
- `method_state_expected` naming.
- JaCoCo **branch** coverage ≥ 0.80 on `:api`, `:util`, `:document`, `:llm`, `:pipeline`, `:persistence`. `:ui` and
  `:app` do not apply `bookloom.coverage-conventions`; both are covered behaviourally.

---

## 8. Quality gate and git hooks {#quality-gate}

`./gradlew check` runs:

| Check | Configuration |
|---|---|
| Spotless | Palantir Java Format, 120 columns |
| Error Prone + NullAway | javac plugins; JSpecify `@NullMarked` per package |
| Checkstyle 13.9.0 | `config/checkstyle/`, `maxWarnings = 0` |
| SpotBugs + FindSecBugs | `config/spotbugs/exclude.xml` |
| ArchUnit | `:app:archTest`, the eight rules above |
| JaCoCo | branch ≥ 0.80 on the six core modules |

### Hooks

Lefthook manages the hooks and they are inactive until installed once per clone:

```bash
# macOS / Linux
brew install lefthook gitleaks

# Windows (Git Bash or PowerShell)
scoop install lefthook gitleaks

lefthook install     # writes .git/hooks
lefthook validate    # optional: does lefthook.yml parse
```

| Stage | Runs | Budget |
|---|---|---|
| `pre-commit` | `./gradlew spotlessApply` (re-stages), gitleaks on the staged diff, a 4 MB file-size guard | < 10 s, no tests |
| `commit-msg` | Conventional Commits validation | instant |
| `pre-push` | `./gradlew clean build check spotlessCheck` | the full gate |

Pre-push runs exactly the CI quality command. `AGENTS.md` forbids agents from using `--no-verify` or `LEFTHOOK=0`: if
a hook is wrong, fix the hook.

---

## 9. Packaging {#packaging}

Stage the input first, on every OS:

```bash
./gradlew :app:collectDist    # jar + runtime classpath -> modules/app/build/dist/libs
```

`collectDist` is a `Sync`, so a dropped dependency does not linger in the staging directory. It stages the eight module
jars plus the resolved runtime classpath, including this machine's classified JavaFX artifacts.

| OS | Command | Extra requirement | Produces (under `build/package/`) |
|---|---|---|---|
| macOS | `./scripts/package-macos.sh` | JDK 25 with `jpackage` | `BookLoom.app`, `BookLoom-<version>-macos-<arch>.tar.gz`, `.dmg` |
| Linux | `./scripts/package-linux.sh` | Liberica 25 Full on aarch64; `fakeroot` for the `.deb` | `BookLoom/`, `BookLoom-<version>-linux-<arch>.tar.gz`, `.deb` |
| Windows | `scripts\package-windows.bat` | Liberica 25 Full (`jdk+fx`) | `BookLoom\BookLoom.exe` — app-image only |

Gradle's involvement ends at `collectDist`. The scripts drive the plain `jpackage` CLI — no `org.beryx.jlink` or other
packaging plugin — with `--main-jar` + `--main-class`, a classpath launch, so the module-path problem in
[§6](#ide) does not reach the packaged app. There is no cross-compilation: each artifact is built on its own OS,
because the staged runtime classpath carries that machine's JavaFX natives.

### Launch smoke — POSIX only

```bash
./scripts/launch-smoke.sh     # macOS and Linux
```

It starts the packaged image on the module path against its own jlinked runtime and asserts it launched. The script is
bash-only (`uname`, `trap`, `kill -0`, `mktemp`) and its non-Darwin branch looks for `BookLoom/bin/BookLoom`, which a
Windows image does not contain. **There is no Windows launch smoke**, in the scripts or in CI (`ci.yml:330` skips it
with `if: runner.os != 'Windows'`).

Two known gaps, recorded rather than fixed here: Windows produces an app-image only, while
`03_PACKAGING_JPACKAGE.md:57` specifies a portable zip; and the CI packaging matrix has never executed, because CI
does not run until the app is feature-complete (`AGENTS.md`).

---

## 10. Troubleshooting {#troubleshooting}

### `Cannot find a Java installation on your machine … matching {languageVersion=25}`

**Cause:** no JDK 25 that Gradle can detect, and there is no auto-provisioning. The toolchain resolves lazily as a
task input property, so configuration succeeds and the first `compileJava` task fails.

**Fix:** install a JDK 25 ([§1](#prerequisites)) and confirm with `./gradlew -q javaToolchains`, not `java -version`.

### `:app:run` appears to hang, then exits after five minutes

**Cause:** another instance holds `dataDir/bookloom.lock` — often a forgotten IDE launch. The second launch shows a
JavaFX window titled "BookLoom is already running" and blocks for up to **300 s** waiting for it to be dismissed; if
that window is off-screen, the launch looks frozen. It then exits with status **0**, because a deliberate refusal is
not a failure — which is also why a wrapper script cannot detect this case by exit status.

**Fix:** dismiss the dialog, or find the first instance and its lock file.

```bash
jps -l                                                       # any OS: lists JVMs; look for the Launcher
ls -la "$HOME/Library/Application Support/BookLoom-Dev/"     # macOS
ls -la "${XDG_DATA_HOME:-$HOME/.local/share}/bookloom-dev/"  # Linux
dir "%LOCALAPPDATA%\BookLoom-Dev"                            # Windows (cmd)
```

### `InaccessibleObjectException` from the IDE, but `:app:run` works

**Cause:** the IDE launched `:app` on the module path. `ua.bookloom.document` does not `opens
ua.bookloom.document.epub` to Guice, so injecting `OpenEpubRegistry` is refused under JPMS.

**Fix:** use the Gradle run configuration ([§6](#ide)). `:app:run` and the packaged image both launch on the
classpath and are unaffected.

### `build-logic` canary tests report `UP-TO-DATE` across a "clean" gate

**Cause:** root `clean` does not reach the `modules/build-logic` included build. Even though `check` depends on
`:build-logic:test`, a repeat run can report it `UP-TO-DATE` and still print `BUILD SUCCESSFUL`. **Tell:** the last
line's ratio — `N actionable tasks: N executed` is a real run, `N actionable tasks: M executed, 12 up-to-date` means
the included build was skipped. Read the ratio, not the absolute number.

**Fix:** clean the included build first.

```bash
./gradlew :build-logic:clean              # by project path
./gradlew -p modules/build-logic clean    # the CI form: a separate build rooted there
```

### A catalog pin changes nothing

**Cause:** a `gradle/libs.versions.toml` entry only influences resolution through a matching `constraints {}` block in
`bookloom.java-conventions`. A pin with no constraint looks locked and is not — this is how Guava's CVE-2023-2976
shipped transitively once.

**Fix:** `grep -rn 'libs\.<alias>' --include='*.kts' .` — no hit means the pin is decoration.

### `:app` shows no lockfile diff after adding a dependency

**Cause:** the JavaFX classifier carve-out disables locking on `:app`/`:ui` compile/runtime classpaths
([§4](#build-config)). This is correct behaviour, not a failure.

**Fix:** verify the version in the catalog.

### A gate run takes materially longer than your baseline

**Cause:** treat it as hung. **Fix:** kill it and diagnose. Never run two full gates concurrently — they contend for
the same daemon and build outputs, and neither run's output is then evidence.

### A UI test wants a real display

**Cause:** a hand-rolled `Test` task that does not inherit the three headless properties from
`bookloom.test-conventions`. **Fix:** let the convention plugin configure the task; do not reach for Monocle or
`testfx.headless=true` ([§7](#testing)).

### `jpackage is not on PATH`, or jpackage fails on missing JavaFX jmods

**Cause:** a JRE rather than a JDK, or — on Windows and Linux aarch64 — a plain JDK with no JavaFX jmods. **Fix:**
install Liberica 25 "Full" (`jdk+fx`) on those two platforms ([§1](#prerequisites)). Copying jmods by hand is not a
fix (EC-REL-4).

### `scripts/*.sh` or the git hooks do not run on Windows

**Cause:** no POSIX shell — hooks are `runner: sh` and the scripts are bash. **Fix:** run them from Git Bash or WSL,
and install Python 3 for `scripts/sync-agent-files.py`.

---

## 11. Known spec drift {#spec-drift}

`docs/specification/**` is frozen and read-only during implementation (`.claude/rules/spec-authoring.md`). The
entries below are stale and superseded by ADRs. Trust the right-hand column.

| Stale location | Reality |
|---|---|
| `docs/specification/INDEX.md:7-8` — "Implementation happens as stories under `../stories/`" | Story format retired by **ADR-0016**. `docs/stories/` does not exist. Work happens as OpenSpec changes. |
| `04_Build_and_Release/01_BUILD_AND_TOOLING.md:133-141` — `trace` / `traceCheck` tasks | Retired by **ADR-0016**; cites a deleted `03_TRACEABILITY.md`. Coverage is the advisory `scripts/fr-coverage.sh`. |
| `04_Build_and_Release/01_BUILD_AND_TOOLING.md#project-layout` — modules at the repo root | Superseded by **ADR-0021**: the eight code directories live under `modules/`, Gradle project paths unchanged. |
| `04_Build_and_Release/03_PACKAGING_JPACKAGE.md` and `01_BUILD_AND_TOOLING.md` — `app/build/dist/libs/` (4 occurrences) | Superseded by **ADR-0021**: `modules/app/build/dist/libs/`. |
| `04_Build_and_Release/03_PACKAGING_JPACKAGE.md:57` — Windows portable zip | `scripts/package-windows.bat` produces an app-image only; no zip step exists. |
| `00_Foundation/04_DESIGN_DECISIONS.md:24` — DD-02 "Java 25 (LTS) + JavaFX 25 (LTS)" | Superseded by **ADR-0019**: JavaFX **26.0.2**. |
| `04_Build_and_Release/06_TESTING_STRATEGY.md` and `02_QUALITY_GATES.md` — "TestFX + Monocle" | Superseded by **ADR-0019**: JavaFX 26's built-in `glass.platform=Headless`. Monocle has no build past 21.0.2. |

A genuine gap in the frozen spec is a **new ADR** under `docs/adr/`, never a spec edit.
