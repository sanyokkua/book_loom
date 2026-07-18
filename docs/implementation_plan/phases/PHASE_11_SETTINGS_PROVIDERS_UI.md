---
phase: PHASE_11_SETTINGS_PROVIDERS_UI
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification
  - docs/specification/01_Product/07_SETTINGS.md#appearance-tab
  - docs/specification/01_Product/07_SETTINGS.md#automation-tab
  - docs/specification/01_Product/07_SETTINGS.md#generation-tab
  - docs/specification/01_Product/07_SETTINGS.md#models-tab
  - docs/specification/01_Product/07_SETTINGS.md#providers-tab
  - docs/specification/01_Product/07_SETTINGS.md#settings-tabs
  - docs/specification/01_Product/07_SETTINGS.md#storage-and-logs-tab
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-add-edit-provider
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-provider-binding
  - docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-settings
  - docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#error-dialog
  - docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#visual-validation
  - FR-MODEL-01
  - FR-MODEL-02
  - FR-MODEL-03
  - FR-MODEL-04
  - FR-NOTIF-01
  - FR-PROV-01
  - FR-PROV-03
  - FR-PROV-04
  - FR-PROV-05
  - FR-PROV-06
  - FR-PROV-07
  - FR-PROV-09
  - FR-PROV-10
  - FR-SETTINGS-02
  - FR-SETTINGS-03
  - FR-SETTINGS-04
  - FR-SETTINGS-05
  - FR-SETTINGS-06
  - FR-SETTINGS-07
  - FR-UI-04
  - FR-UI-06
  - DD-11
  - DD-14
  - DD-28
  - DD-31
  - DD-34
  - DD-36
  - DD-38
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`, `docs/specification/01_Product/07_SETTINGS.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md`,
`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`, `docs/specification/mockups/ui-mockup.html`

# PHASE_11 — Settings & Providers UI

## Goal

Complete the application surface: the Settings screen with its six tabs (Providers / Models / Generation / Appearance /
Automation / Storage & logs), the add/edit-provider dialog with the connection/models/inference test trio, and the
notification surface (toasts, banners, error dialog with expandable technical details, empty states). Settings persist
through the typed KV store (F7); the provider dialog drives the PHASE_05 verification and discovery through the provider
abstraction (F3).

## In scope

- Settings tabs: Providers, Models, Generation (num_ctx/temp/chunk-budget/preceding-blocks with validated ranges),
  Appearance (theme/accent/language/density), Automation (review-mode + quality-dial defaults), Storage & logs (data/log
  locations, log level).
- Add/edit-provider dialog capturing endpoint/auth/kind per profile, credential-as-reference entry, and the Test
  connection / Test models / Test inference trio surfacing independent per-stage results.
- Notification surface: toast types ok/info/warn/err; info/warn/err banners; error dialog with expandable technical
  details surfaced via the safe-details allowlist; empty states for every list screen.
- Setting validation: reject out-of-range input.

## Out of scope

- New provider/verification logic (built in PHASE_05) — this phase wires the dialog to it.
- Backward revision (PHASE_12), packaging (PHASE_13).

## Dependencies

PHASE_05 (provider abstraction, verification, discovery, secret references), PHASE_09 (UI shell, state mirror, theming,
settings KV consumption).

## Forward-compatibility

- **Consumes F3** — the provider dialog treats kinds as data via the provider profiles.
- **Consumes F7** — every setting reads/writes the typed KV store.
- **Consumes F8** — tabs and dialogs bind to the state mirror.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                        | Target modules                                                                     | Cited spec clauses                                                                                                   |
|-------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Settings shell + tabs scaffolding                                                                                                                     | `:ui/ua.bookloom.ui.screen`                                                        | FR-SETTINGS-02, `01_Product/08_UI_SCREENS_AND_STATES.md#screen-settings`, `01_Product/07_SETTINGS.md#settings-tabs`  |
| Providers tab: add/edit/select/delete, one current                                                                                                    | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.dialog`                           | FR-PROV-01, FR-PROV-04, `01_Product/07_SETTINGS.md#providers-tab`                                                    |
| Add/edit-provider dialog (endpoint/auth/kind per profile, credential-as-reference, manual model-ID entry field)                                       | `:ui/ua.bookloom.ui.dialog`                                                        | FR-PROV-03, FR-PROV-05, FR-MODEL-02, `01_Product/08_UI_SCREENS_AND_STATES.md#dialog-add-edit-provider`, DD-11, DD-38 |
| Test connection / models / inference trio with independent per-stage results                                                                          | `:ui/ua.bookloom.ui.dialog`, `:ui/ua.bookloom.ui.notify`                           | FR-PROV-06, FR-PROV-07, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification`                         |
| Resume-time provider/model prompts: bound provider/model unavailable → confirm fallback; settings differ from project's last-used → apply vs continue | `:ui/ua.bookloom.ui.dialog`                                                        | FR-PROV-09, FR-PROV-10, DD-31, `01_Product/08_UI_SCREENS_AND_STATES.md#dialog-provider-binding`                      |
| Models tab: discovery + first-class manual model-ID entry, translator/judge slots (no embedding), remembered model                                    | `:ui/ua.bookloom.ui.screen`                                                        | FR-MODEL-01, FR-MODEL-02, FR-MODEL-03, FR-MODEL-04, DD-38, `01_Product/07_SETTINGS.md#models-tab`                    |
| Generation tab: num_ctx/temp/chunk-budget/preceding-blocks with validated ranges (temp default 0.2)                                                   | `:ui/ua.bookloom.ui.screen`                                                        | FR-SETTINGS-03, FR-SETTINGS-07, DD-36, `01_Product/07_SETTINGS.md#generation-tab`                                    |
| Appearance tab: theme/accent/language (English/Ukrainian)/density                                                                                     | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.theme`, `:ui/ua.bookloom.ui.i18n` | FR-SETTINGS-04, FR-UI-06, DD-34, `01_Product/07_SETTINGS.md#appearance-tab`                                          |
| Automation tab: review-mode + quality-dial defaults                                                                                                   | `:ui/ua.bookloom.ui.screen`                                                        | FR-SETTINGS-05, `01_Product/07_SETTINGS.md#automation-tab`                                                           |
| Storage & logs tab: data/log locations, log level                                                                                                     | `:ui/ua.bookloom.ui.screen`                                                        | FR-SETTINGS-06, `01_Product/07_SETTINGS.md#storage-and-logs-tab`                                                     |
| Notification surface: toasts, banners, error dialog with expandable details (safe-details allowlist), empty states                                    | `:ui/ua.bookloom.ui.notify`, `:ui/ua.bookloom.ui.dialog`                           | FR-NOTIF-01..05, `01_Product/11_NOTIFICATIONS_AND_ERRORS.md#error-dialog`, DD-14                                     |
| Match Settings tabs, provider dialog, toasts/banners to the mockup (P6)                                                                               | `:ui/ua.bookloom.ui.screen`, `:ui/ua.bookloom.ui.dialog`                           | FR-UI-04, DD-28, `docs/specification/mockups/ui-mockup.html`                                                         |

## Phase exit checklist

- [ ] All six Settings tabs exist and persist through the typed KV store.
- [ ] The add/edit-provider dialog captures endpoint/auth/kind per profile and stores credentials only as a reference.
- [ ] The Test connection/models/inference trio runs against the draft config and reports each stage independently.
- [ ] Generation and all validated settings reject out-of-range input.
- [ ] Appearance changes theme/accent/density; language switches between English and Ukrainian, persisted as
  `ui.language` and applied on change (restart acceptable).
- [ ] Manual model-ID entry is available in the provider/Models UI even when discovery is absent; the resume-time
  provider/model prompts (unavailable → confirm fallback; settings-differ → apply vs continue) are wired to the binding.
- [ ] Toasts (ok/info/warn/err), banners, the error dialog with expandable technical details, and empty states are
  present; only allowlisted safe details surface.
- [ ] Each surface matches the mockup (P6) in both themes (TestFX/Monocle).
- [ ] Visual validation performed per `04_Build_and_Release/06_TESTING_STRATEGY.md#visual-validation` (token/geometry
  assertions; pinned snapshot diff and/or vision-model review for the settings/provider surfaces).
- [ ] `./gradlew :ui:test` and `./gradlew traceCheck` green; module inventory updated.
