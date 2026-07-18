---
phase: PHASE_09_UI_SHELL
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-import
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-projects
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure
  - docs/specification/01_Product/09_THEMING.md#token-model
  - docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization
  - docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#mvvm
  - docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#fx-thread-rules
  - docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#visual-validation
  - FR-BRIEF-01
  - FR-IMPORT-02
  - FR-IMPORT-03
  - FR-IMPORT-04
  - FR-IMPORT-06
  - FR-NOTIF-05
  - FR-UI-01
  - FR-UI-02
  - FR-UI-03
  - FR-UI-04
  - FR-UI-05
  - FR-UI-06
  - DD-21
  - DD-22
  - DD-28
  - DD-34
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`, `docs/specification/01_Product/09_THEMING.md`,
`docs/specification/mockups/ui-mockup.html`

# PHASE_09 — UI Shell

## Goal

Stand up the JavaFX application shell: the launcher/app wiring, token-only theming (one role set, light + dark value
blocks, following OS `prefers-color-scheme`), navigation, the observable state mirror (seam F8), and the first four
screens — Projects (+empty), Import (with its detected-ok / language-mismatch / DRM-blocked / unsupported states), Book
Brief, and Structure. All long work runs off the FX thread; screens bind to the state mirror, not to services. The
mockup is the visual acceptance reference (P6).

## In scope

- FXML views + Guice-injected controllers; navigation host; Ikonli icons; ControlsFX/AtlantaFX controls per the control
  mapping.
- Token-only theming on `.root`; one set of token roles with light + dark value blocks; fixed Cognac accent; OS
  `prefers-color-scheme` (no density setting in v1).
- **i18n infrastructure established here (not deferred):** all screen text via `ResourceBundle` keys, the English
  bundle, OS-locale detection on first start, and the `ui.language` KV persistence — so every screen in this and later
  phases uses keys, never hardcoded strings. The Ukrainian bundle is completed to parity in PHASE_13 (DD-34).
- The observable state mirror updated off the FX thread via `Platform.runLater` (seam F8); viewmodels.
- Screens: Projects (list + empty state), Import (detected-ok / language-mismatch / DRM-blocked / unsupported states,
  drag-drop + file picker, detected-file card), Book Brief (languages, genre/register/voice, faithful↔natural slider,
  policies, quality dial), Structure (chapter tree, segment counts).
- Long-running work dispatched off the FX thread via Task/Service; virtual threads for I/O fan-out.

## Out of scope

- Translating dashboard, Review queue, Glossary editor, Export screens (PHASE_10).
- Settings tabs and the provider dialog + test trio (PHASE_11).
- Any change to core engine logic — the UI consumes existing ports (`DocumentPort`, `TranslationEngine`).

## Dependencies

PHASE_00 (app/Guice shell), PHASE_01 (document import for Import/Structure), PHASE_04 (settings KV / projects
persistence).

## Forward-compatibility

- **Establishes F8** — the observable state mirror decoupled from domain services, updated off the FX thread; consumed
  by all UI screens (PHASE_09–PHASE_11).
- Establishes the theming token model and TestFX/Monocle harness reused by PHASE_10 and PHASE_11.
- Consumes F7 (settings KV) for appearance/automation defaults.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                               | Target modules                                                          | Cited spec clauses                                                                                                                  |
|------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| JavaFX app shell, FXML + Guice controller factory, navigation host                                                           | `:ui/ua.bookloom.ui`, `:ui/ua.bookloom.ui.view`, `:app/ua.bookloom.app` | FR-UI-01, `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#mvvm`, DD-21                                                                |
| Token-only theming (light/dark value blocks, fixed accent, OS prefers-color-scheme)                                          | `:ui/ua.bookloom.ui.theme`                                              | FR-UI-05, `01_Product/09_THEMING.md#token-model`, DD-22                                                                             |
| i18n infrastructure: ResourceBundle wiring for all text, English bundle, OS-locale first-start, `ui.language` KV persistence | `:ui/ua.bookloom.ui.i18n`, `:ui/ua.bookloom.ui.screen`                  | FR-UI-06, DD-34, `01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`                                                     |
| Observable state mirror updated off the FX thread (`Platform.runLater`)                                                      | `:ui/ua.bookloom.ui.state`                                              | FR-UI-02, `02_Architecture/08_THREADING_CONCURRENCY.md#fx-thread-rules`, DD-21 (seam F8)                                            |
| Projects screen + empty state                                                                                                | `:ui/ua.bookloom.ui.screen`                                             | FR-UI-01, FR-NOTIF-05, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-projects`                                                     |
| Import screen with detected-ok / language-mismatch / DRM-blocked / unsupported states + detected-file card + drag-drop       | `:ui/ua.bookloom.ui.screen`                                             | FR-IMPORT-02, FR-IMPORT-03, FR-IMPORT-04, FR-IMPORT-06, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-import`, EC-DRM-*, EC-LANG-* |
| Book Brief screen (languages, genre/register/voice, faithful↔natural slider, policies, quality dial)                         | `:ui/ua.bookloom.ui.screen`                                             | FR-BRIEF-01..08, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`                                                         |
| Structure screen (chapter TreeView, segment counts)                                                                          | `:ui/ua.bookloom.ui.screen`                                             | FR-UI-03, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`                                                                 |
| Match each screen/state to the mockup (P6) in light + dark                                                                   | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.theme`                 | FR-UI-04, DD-28, `docs/specification/mockups/ui-mockup.html`                                                                        |

## Phase exit checklist

- [ ] The app shell launches with working navigation; controllers are Guice-injected.
- [ ] Theming is token-only on `.root`; light and dark render from the two value blocks of one role set and follow OS
  preference (theming harness in place).
- [ ] The observable state mirror is updated off the FX thread; screens bind to state, not services (F8 established).
- [ ] Projects, Import (all four states), Book Brief, and Structure screens exist and behave per spec.
- [ ] Import surfaces language-mismatch and DRM-blocked states; unsupported/corrupt rejected with a clear reason.
- [ ] Each screen/state matches the mockup (P6) in both themes, asserted via TestFX/Monocle (token/geometry
  conformance).
- [ ] All screen text resolves through ResourceBundle keys (no hardcoded UI strings); English bundle complete;
  first-start OS-locale selection and `ui.language` persistence work.
- [ ] Visual validation performed for the shell screens per
  `04_Build_and_Release/06_TESTING_STRATEGY.md#visual-validation` (token/geometry assertions; snapshot/vision review as
  needed).
- [ ] No long work on the FX thread; I/O fan-out uses virtual threads.
- [ ] `./gradlew :ui:test` (TestFX headless) and `./gradlew traceCheck` green; module inventory updated.
