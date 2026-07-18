---
phase: PHASE_13_PACKAGING_RELEASE
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#accessibility
  - docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization
  - docs/specification/03_NonFunctional/04_ACCESSIBILITY.md#requirements
  - docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#per-os-matrix
  - docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#approach
  - docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#unsigned-distribution
  - docs/specification/04_Build_and_Release/04_CI_CD.md#release-on-tags
  - docs/specification/04_Build_and_Release/05_ICON_AND_BRANDING.md#per-os-derivation
  - FR-UI-06
  - FR-UI-07
  - DD-24
  - DD-29
  - DD-34
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer, release management **Last
Updated:** 2026-07-18 **Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`,
`docs/specification/04_Build_and_Release/04_CI_CD.md`,
`docs/specification/04_Build_and_Release/05_ICON_AND_BRANDING.md`,
`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md`, `docs/specification/03_NonFunctional/04_ACCESSIBILITY.md`

# PHASE_13 — Packaging & Release

## Goal

Ship the application: per-OS native packages via script-driven jpackage on a GitHub Actions matrix — macOS `.app`
tar.gz + `.dmg` (Intel + Apple Silicon), **Windows portable app-image zip (no installer)**, Linux tar.gz + `.deb`
(x86_64 + ARM) — unsigned, with documented "open anyway" steps; the three-workflow CI (`ci`/`build`/`release`),
user-facing docs, the internationalization pass (ResourceBundles for all user text), and an advisory accessibility
review (WCAG 2.1 AA guidance — non-gating). Because jpackage cannot cross-compile, each installer is built on its own
OS.

## In scope

- Committed packaging scripts (`scripts/jpackage-common.sh` + `package-macos.sh` / `package-linux.sh` /
  `package-windows.bat`) over `./gradlew :app:collectDist`: macOS `.app` tar.gz + `.dmg` (x86_64 + aarch64), **Windows
  portable app-image zip only — no installer/WiX**, Linux tar.gz + `.deb` (x86_64 + aarch64, fakeroot with graceful
  skip); jlink-trimmed runtimes; Temurin JDK except Liberica Full (`jdk+fx`) on Windows/Linux-ARM; unsigned, documented
  Gatekeeper/SmartScreen "open anyway" steps (DD-24).
- The three-workflow CI per `04_Build_and_Release/04_CI_CD.md`: `ci.yml` (PR merge gate), `build.yml` (`main` snapshots,
  14-day artifacts), `release.yml` (tag → verify-tag-on-main + numeric-version extraction → quality → matrix → GitHub
  Release with auto pre-release + generated notes).
- Internationalization: all user-facing text via ResourceBundles in **English (default) + Ukrainian**, both bundles
  complete; OS-locale detection on first start; `ui.language` persisted in the DB; user-switchable in Settings →
  Appearance (apply-on-change; restart acceptable) — DD-34.
- Advisory accessibility review: WCAG 2.1 AA guidance (focus order, keyboard operability, contrast, target size) across
  all screens — best-effort, non-gating (`03_NonFunctional/04_ACCESSIBILITY.md` is Informative).
- Release docs: install/run instructions per OS, offline-usage note, "open anyway" steps.

## Out of scope

- Code signing / notarization (explicitly not done).
- New product features — this phase hardens, packages, and documents what exists.

## Dependencies

All prior phases (the full application must be built and green before packaging). Directly builds on PHASE_00 (jpackage
smoke, CI) and the full UI (PHASE_09–PHASE_11).

## Forward-compatibility

- **Consumes F9** — release artifacts preserve the offline invariant; the packaged app makes no network call other than
  user-triggered provider communication.
- No new seam; this phase finalizes distribution.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                                                                                                           | Target modules                                       | Cited spec clauses                                                                                                              |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| App icon pipeline: process the source master + derive per-OS `.icns`/`.ico`/`.png`; set the JavaFX `Stage` icon                                                                                                                          | `assets/icon/` (build), `:ui/ua.bookloom.ui`         | `04_Build_and_Release/05_ICON_AND_BRANDING.md#per-os-derivation`, DD-29, ADR-0011                                               |
| `:app:collectDist` Sync task + the committed packaging scripts (`jpackage-common.sh`, `package-macos.sh`, `package-linux.sh`, `package-windows.bat`) wiring per-OS `--icon`, app name **BookLoom**, prod build stamp, jlink trim options | `:app/ua.bookloom.app` (build), `tooling` (scripts/) | `04_Build_and_Release/03_PACKAGING_JPACKAGE.md#approach`, `05_ICON_AND_BRANDING.md#jpackage-integration`, DD-24, DD-29, DD-39   |
| Five-leg packaging matrix (`build.yml` + `release.yml`): macOS x86_64/aarch64, Windows x86_64 (Liberica Full), Linux x86_64/aarch64 — headless launch smoke per leg                                                                      | CI config (`ci`)                                     | `04_Build_and_Release/03_PACKAGING_JPACKAGE.md#per-os-matrix`, `04_Build_and_Release/04_CI_CD.md#packaging-matrix`              |
| `release.yml`: verify tag-on-main + version extraction (full vs numeric), 1-day staging artifacts, GitHub Release (auto pre-release, generated notes + open-anyway steps)                                                                | CI config (`ci`)                                     | `04_Build_and_Release/04_CI_CD.md#release-on-tags`, EC-REL-1, EC-REL-2                                                          |
| Release version wiring: `-PappVersion=<full>` on every Gradle step, `APP_VERSION=<numeric>` to the scripts; packaging smoke asserts the launched artifact logs the tag version                                                           | CI config (`ci`)                                     | `01_BUILD_AND_TOOLING.md#version-injection`, DD-50, EC-REL-7                                                                    |
| CI release pipeline: tag → matrix build → attach artifacts                                                                                                                                                                               | CI config                                            | `04_Build_and_Release/04_CI_CD.md#release-on-tags`                                                                              |
| Internationalization: English + Ukrainian ResourceBundles (parity), OS-locale first-start, `ui.language` persistence, in-app switch                                                                                                      | `:ui/ua.bookloom.ui.i18n`, all `:ui` screens         | FR-UI-06, DD-34, `01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`                                                 |
| Advisory accessibility review (WCAG 2.1 AA guidance: focus, keyboard, contrast, target size) — non-gating                                                                                                                                | all `:ui` screens, `:ui/ua.bookloom.ui.theme`        | FR-UI-07 (Should), `01_Product/10_I18N_AND_ACCESSIBILITY.md#accessibility`, `03_NonFunctional/04_ACCESSIBILITY.md#requirements` |
| Release docs: per-OS install/run, offline note, unsigned "open anyway" steps                                                                                                                                                             | `docs/` (user-facing)                                | DD-24, `04_Build_and_Release/03_PACKAGING_JPACKAGE.md#unsigned-distribution`                                                    |

## Phase exit checklist

- [ ] The per-OS icons derive from the single `assets/icon/appicon.png` master (no hand-forked file); each installer
  carries the correct icon (`.icns`/`.ico`/`.png`) and the running window shows it.
- [ ] The packaging scripts produce every matrix artifact — macOS `.app` tar.gz + `.dmg` (both arches), Windows portable
  zip (**no installer**), Linux tar.gz + `.deb` (both arches) — each on its matching runner, each passing the headless
  launch smoke.
- [ ] All artifacts are unsigned; the release notes/docs give the Gatekeeper/SmartScreen "open anyway" steps (incl. the
  tar.gz quarantine note).
- [ ] `release.yml` verifies the tag is on `main`, extracts full + numeric versions, builds the matrix, and publishes
  the GitHub Release with auto pre-release detection; a red gate publishes nothing (EC-REL-1/2/5).
- [ ] Every released artifact reports the tag version in About, the startup log, and OS package metadata (asserted by
  the packaging smoke, EC-REL-7); a local build reports `dev`.
- [ ] All user-facing text is externalized to ResourceBundles; both English and Ukrainian bundles are complete and in
  parity; first-start OS-locale detection and the in-app language switch (persisted as `ui.language`) work.
- [ ] The advisory accessibility review is performed and its findings recorded (non-gating; fixes at the team's
  discretion).
- [ ] The packaged app runs fully offline and makes no network call other than user-triggered provider communication
  (inference, model discovery, verification) (F9 verified).
- [ ] Release docs published in-repo; `./gradlew test`, `./gradlew :ui:test`, and `./gradlew traceCheck` green across
  the project.
