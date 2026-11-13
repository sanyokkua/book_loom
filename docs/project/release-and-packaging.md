# Release and packaging

BookLoom is packaged with `jpackage`, driven by scripts rather than a Gradle plugin. This page says only what the
scripts and the CI file do. See also [development/getting-started.md](../development/getting-started.md) (section
Packaging) and [scripts.md](scripts.md#packaging).

## Pipeline

1. `./gradlew :app:collectDist` stages `app.jar` and the full runtime classpath, including the platform's JavaFX jars,
   into `modules/app/build/dist/libs/`.
2. A per-OS script runs `jpackage` against that folder and writes to `build/package/`.
3. `scripts/launch-smoke.sh` starts the result and checks it really launched.

jpackage cannot cross-compile: build each artifact on its own OS.

## Scripts and outputs

| OS | Script | JDK | Output in `build/package/` |
|---|---|---|---|
| macOS | `scripts/package-macos.sh` | a JDK 25 with `jpackage`; plain Temurin, not one that bundles JavaFX | `BookLoom.app`, `BookLoom-<version>-macos-<arch>.tar.gz`, a `.dmg` |
| Linux | `scripts/package-linux.sh` | the same | `BookLoom/` (app-image), `BookLoom-<version>-linux-<arch>.tar.gz`, a `.deb` only when `fakeroot` is installed (otherwise a warning and no `.deb`) |
| Windows | `scripts\package-windows.bat` | Liberica 25 "Full" (`jdk+fx`) | `BookLoom\BookLoom.exe` app-image folder only. The script does not zip it and builds no `.msi` or `.exe` installer |

The macOS app is tarred, not zipped, to keep the launcher's execute bit.

### Shared settings (`scripts/jpackage-common.sh`, macOS and Linux)

| Setting | Value |
|---|---|
| Main class | `ua.bookloom.app.bootstrap.Launcher`, module `ua.bookloom.app` |
| Launch mode | module path: every jar stays in the image's `app/mods`, so `module-info.java` is enforced and a missing `requires`, `exports` or `opens` fails at start |
| Runtime | built by `jlink` with the listed JDK modules only (`java.base`, `java.desktop`, `java.net.http`, `java.sql` and others, `--strip-debug`, `--compress zip-6`) into `build/runtime`; jpackage cannot link it because five dependencies are automatic modules |
| JVM options | `-Dbookloom.env=prod`, `-Dguice_bytecode_gen_option=DISABLED`, `--add-modules=ALL-MODULE-PATH`, `--enable-native-access=javafx.graphics` |
| Version | `APP_VERSION` from the environment, else the Gradle `version` (the `appVersion` property, default `dev`), else `dev`. jpackage needs a numeric `X.Y.Z`, so a tag contributes its numeric part and an un-tagged build uses `1.0.0` as OS metadata; the artifact name carries the full string. The version the app reports comes from a generated `version.properties` and stays `dev` for un-tagged builds |
| Icons | `docs/spec_baseline/specification/assets/icon/dist/{macos,linux,windows}/` |

The `-Dbookloom.env=prod` stamp matters: without it the image resolves the development data folder and appears to work
against the wrong data. The launch smoke checks for it.

The Windows `.bat` mirrors the shared settings by hand (its header says the two must be changed together) but differs
in one way: it passes `--input` and `--main-jar app.jar` with jlink options, so the Windows image runs on the
classpath with the JDK's own JavaFX jmods, not on the module path.

Signing: none of the scripts signs or notarizes anything. The artifacts are unsigned. No release or signing workflow
exists in the repository.

## Launch smoke tests

| Script | What it proves |
|---|---|
| `scripts/launch-smoke.sh [image]` | starts the image headless (`-Dglass.platform=Headless`, `-Dbookloom.smoke.resolveAll=true`, also passed through `JAVA_TOOL_OPTIONS` on macOS) with a temporary `BOOKLOOM_DATA_DIR`, waits up to `SMOKE_TIMEOUT_SECONDS` (default 90) for four log lines (`app started version=`, `injector built and two-phase init complete`, `window shown, initialView=`, `resolve-all passed`), fails at once on `resolve-all FAILED`, and requires `environment=PROD` in the startup line. Failing to launch is a hard failure, never a skip |
| `scripts/launch-smoke-readonly-db.sh [image]` | with `bookloom.db` made read-only the image shows the startup-failure message instead of dying silently |

"Resolve all" builds the injector in Guice's production stage, loads every screen and resolves every port, which
catches a binding or a resource that only fails in the packaged image. Both scripts find the launcher at
`build/package/BookLoom.app/Contents/MacOS/BookLoom` on macOS and `build/package/BookLoom/bin/BookLoom` elsewhere.
Neither has a Windows variant; CI skips the smoke on Windows.

## What CI packages

The `packaging` job in `.github/workflows/ci.yml` (after the quality job, on every push to a non-master branch and on
pull requests to `master`) builds five legs: macOS aarch64 (`macos-15`), macOS x86_64 (`macos-15-intel`), Linux x86_64
(`ubuntu-24.04`), Linux aarch64 (`ubuntu-24.04-arm`) and Windows x86_64 (`windows-2025`, Liberica `jdk+fx`). Each runs
`:app:collectDist`, its script, and on macOS and Linux the launch smoke (Linux under Xvfb), then uploads
`build/package/*.tar.gz`, `*.dmg` and `*.deb` as `bookloom-<target>-<run id>` for 14 days. The Windows folder matches
none of those patterns, so that leg uploads nothing (the step warns). Details: [ci-and-hooks.md](ci-and-hooks.md).

There is no tag-triggered release job and no GitHub Release step. `ci.yml` comments refer to `build.yml` and
`release.yml`, which do not exist.

## Building locally

```bash
./gradlew :app:collectDist
./scripts/package-macos.sh        # or package-linux.sh, or scripts\package-windows.bat
./scripts/launch-smoke.sh         # macOS and Linux
APP_VERSION=1.2.3 ./scripts/package-macos.sh   # set the version in the artifact name
```

Windows needs Git Bash or WSL for the other scripts and Python 3 for the sync check
([development/getting-started.md](../development/getting-started.md)).
