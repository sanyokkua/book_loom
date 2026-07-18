---
id: STORY-013
title: Packaging input — collectDist, jpackage scripts, and packaged-app launch smoke
status: ready
spec_clauses:
  - docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#approach
  - docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#verification
  - docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#jpackage-smoke
  - docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-24-jpackage-unsigned
  - docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-39-app-environment-and-paths
modules:
  - :app/ua.bookloom.app
  - tooling
  - ci
acceptance_criteria:
  - STORY-013-AC-1
  - STORY-013-AC-2
  - STORY-013-AC-3
  - STORY-013-AC-4
  - STORY-013-AC-5
edge_cases:
  - EC-REL-4
  - EC-REL-5
depends_on:
  - STORY-007
  - STORY-008
  - STORY-012
adrs:
  - ADR-0011
  - ADR-0015
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: L
---

# STORY-013 — Packaging input, jpackage scripts, and launch smoke

## Goal

Produce the packaging input chain — `:app:collectDist` runtime-jar collection, the per-OS jpackage scripts, and a
headless launch smoke — so that from PHASE_00 onward every platform can build a runnable, correctly-stamped BookLoom app
image from one command.

## In scope

- `:app:collectDist` Gradle task (a `Sync`) gathering the application jar plus the full runtime classpath into
  `app/build/dist/libs/` — the single input directory the jpackage scripts consume
  (`03_PACKAGING_JPACKAGE.md#approach`).
- `scripts/jpackage-common.sh` — shared argument assembly: app name **BookLoom**, icon from the spec asset pipeline
  (ADR-0011), main jar/class, jlink options (`--strip-debug --no-header-files --no-man-pages --compress zip-6`), and the
  production stamp
  `--java-options "-Dbookloom.env=prod"` (DD-39).
- `scripts/package-macos.sh` (app-image → `.app` → tar.gz + `.dmg`),
  `scripts/package-linux.sh` (app-image → tar.gz, `.deb` when `fakeroot` is available — graceful skip otherwise,
  EC-REL-4), `scripts/package-windows.bat` (portable **app-image zip only** — no MSI/WiX, DD-24).
- `APP_VERSION` consumption: scripts pass the numeric version to `jpackage --app-version` and the full version to the
  Gradle build via `-PappVersion` (STORY-007's injection).
- **Launch smoke** (`02_QUALITY_GATES.md#jpackage-smoke`): start the packaged binary headlessly, assert it reaches the
  post-Phase-2 ready state, logs `app started version=<v>`, resolves the **prod** (non `-Dev`) data dir, and exits
  cleanly on termination — with a bounded timeout so a hang fails red (EC-REL-5).
- Script portability checks: bash scripts run on macOS bash 3.2 and Linux; the `.bat` script needs only cmd.exe + the
  JDK on PATH.

## Out of scope

- `release.yml` / `build.yml` workflow authoring and the 5-leg release matrix — PHASE_13 refines them from
  `04_CI_CD.md`; this story makes the scripts those workflows will call.
- Liberica-vs-Temurin runner toolchain selection (a workflow concern, `04_CI_CD.md#packaging-matrix`).
- Code signing / notarization — explicitly out of scope for the project (unsigned distribution,
  `03_PACKAGING_JPACKAGE.md#unsigned-distribution`).
- Icon *design* — the icon asset and its conversion pipeline exist (ADR-0011); this story only consumes the generated
  `.icns`/`.ico`/`.png`.

## Spec inputs

- `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#approach` — scripts-driven plain jpackage CLI over
  `collectDist` output; no Gradle packaging plugin.
- `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#verification` — what each produced artifact must be
  verified to do, including the launch smoke.
- `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#jpackage-smoke` — the smoke's normative assertions
  (start, version log line, prod data dir, clean exit).
- `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-24-jpackage-unsigned` — per-OS artifact matrix; Windows
  portable-zip-only decision.
- `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-39-app-environment-and-paths` — the
  `-Dbookloom.env=prod` stamp and dev/prod dir separation the smoke asserts.

## Design constraints

- Scripts are the **single source of packaging truth** — CI calls them unchanged; a developer runs the same script
  locally and gets the same artifact layout.
- `collectDist` must be wired so `./gradlew :app:collectDist -PappVersion=<v>` is sufficient input for any script —
  scripts perform **no Gradle logic** themselves beyond invoking that task.
- The launch smoke must not depend on a display server (headless JavaFX via Monocle or platform-equivalent flags) and
  must not touch the developer's real prod data dir — it points
  `BOOKLOOM_DATA_DIR` at a temp dir (DD-39 override) while still asserting prod-mode resolution logic.
- No network access during packaging or smoke.
- Windows artifact is a **zip of the app-image only** (EC-REL: no installer tech available).

## Acceptance criteria

### STORY-013-AC-1

**Given** a built tree, **when** `./gradlew :app:collectDist -PappVersion=1.2.3` runs, **then** `app/build/dist/libs/`
contains exactly the app jar and its runtime classpath (no test, provided, or duplicate jars), and re-running the task
is up-to-date/idempotent (`Sync`
semantics).

### STORY-013-AC-2

**Given** `collectDist` output and `APP_VERSION=1.2.3` on the current OS, **when** the platform script runs (macOS:
`.app` + tar.gz + dmg; Linux: tar.gz [+ deb when fakeroot present]; Windows: app-image zip), **then** each artifact is
produced under the documented output path with the app name **BookLoom**, the platform icon, and `--app-version 1.2.3`
applied.

### STORY-013-AC-3

**Given** a produced app image with `BOOKLOOM_DATA_DIR` pointed at a temp dir, **when** the launch smoke starts it
headlessly, **then** within the bounded timeout it observes the single `app started version=1.2.3` log line, confirms
prod-mode path resolution (no `-Dev` suffix in the resolved logical dir), and the process exits 0 on termination — a
missing line or a hang past the timeout fails the smoke red (EC-REL-5).

### STORY-013-AC-4

**Given** a Linux environment **without** `fakeroot`, **when** `package-linux.sh` runs, **then** the tar.gz artifact is
still produced, the `.deb` step is skipped with an explicit warning line, and the script exits 0 (EC-REL-4).

### STORY-013-AC-5

**Given** no `-PappVersion` / `APP_VERSION` is supplied, **when** `collectDist` and a platform script run, **then** the
build stamps the `dev` version (STORY-007 default), jpackage receives the documented placeholder numeric version, and
the smoke asserts `app started version=dev` — packaging without a tag never fails, and never fabricates a
release-looking version.

## Test plan

| Tier           | Test                                                                                                          | Proves                           |
|----------------|---------------------------------------------------------------------------------------------------------------|----------------------------------|
| gradle test    | `app` build script test: `CollectDistTaskTest.collects_runtime_classpath_only`                                | Proves: STORY-013-AC-1           |
| scripted check | `tooling/packaging-test/run_platform_script.sh` on the current OS; asserts artifact set, name, version        | Proves: STORY-013-AC-2           |
| scripted check | `tooling/packaging-test/launch_smoke.sh` — temp `BOOKLOOM_DATA_DIR`, timeout, log-line grep, exit-code assert | Proves: STORY-013-AC-3, EC-REL-5 |
| scripted check | `tooling/packaging-test/no_fakeroot_skip.sh` — PATH-masked fakeroot; assert skip + exit 0                     | Proves: STORY-013-AC-4, EC-REL-4 |
| scripted check | `tooling/packaging-test/dev_version_default.sh` — no version env; assert `version=dev` smoke line             | Proves: STORY-013-AC-5           |

The scripted checks run per-OS; each OS leg exercises its own script (full 3-OS coverage arrives with the release
workflow in PHASE_13 — locally, the current OS's leg must pass).

## Definition of done

- All acceptance criteria demonstrably pass on the development OS; script logic for the other OSes reviewed against the
  spec's artifact matrix.
- `collectDist`, all three scripts, and the smoke harness are committed; outputs land under documented paths only.
- The implicit clean-gate AC holds: `./gradlew clean build check spotlessCheck` passes project-wide with zero warnings
  treated as errors — no "pre-existing issue" exemptions.
- Traceability: tests and scripted checks carry `Proves:` markers; `./gradlew traceCheck` passes; EC-REL-4 and EC-REL-5
  are linked from the edge-case front-matter to their proving checks.
