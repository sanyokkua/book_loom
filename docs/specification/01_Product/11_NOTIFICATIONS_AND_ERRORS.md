**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`, `:api`), QA, UX **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`

# Notifications and Errors

This document specifies toasts, banners, the activity log, the error dialog, the typed error surface, and empty states,
realizing `FR-NOTIF-*`. It builds on the uniform `Result{data,error}` envelope and the typed `AppError`/`ErrorCode` with
a safe-details allowlist (DD-14), defined in `:api`.

**No OS/desktop notifications in v1.** All surfacing is in-app (toasts, banners, dialogs, the activity log). There is no
operating-system notification channel and, specifically, **no "desktop notification on finish"** feature — run
completion is surfaced in-app on the Translating dashboard (`08_UI_SCREENS_AND_STATES.md#screen-translating`).

## toasts {#toasts}

Toasts are transient (ControlsFX `Notifications`) in four types (FR-NOTIF-01):

| Type | Token               | Fires when                                                                                  |
|------|---------------------|---------------------------------------------------------------------------------------------|
| ok   | success (sage)      | An action completes successfully (e.g. provider verified, export saved, glossary imported). |
| info | info (slate-blue)   | A neutral status update (e.g. resume available, pass started).                              |
| warn | warning (ochre)     | A non-blocking concern (e.g. language mismatch confirmed, segment left flagged on export).  |
| err  | danger (terracotta) | A recoverable failure that does not warrant a modal (e.g. a single retry failed).           |

Toasts must not be the sole channel for critical information (FR-A11Y-8).

## banners {#banners}

Banners are persistent, in-context strips in three severities (FR-NOTIF-02):

| Severity | Token   | Example condition                                     | Screen                                             |
|----------|---------|-------------------------------------------------------|----------------------------------------------------|
| info     | info    | Resume offered from a checkpoint                      | Projects / Translating                             |
| warn     | warning | Declared vs detected language mismatch (EC-LANG-1)    | Import (language-mismatch)                         |
| err      | danger  | DRM-blocked import (EC-DRM-1); provider-error mid-run | Import (DRM-blocked); Translating (provider-error) |

A banner persists until its condition is resolved or dismissed and offers a relevant action (retry, open settings,
confirm).

## activity-log {#activity-log}

The Translating dashboard hosts an **activity log** — an append-only, in-run event feed distinct from toasts/banners
(`08_UI_SCREENS_AND_STATES.md#screen-translating`). It has a fixed event vocabulary, each event carrying a status token
and a localized message:

| Event   | Meaning                                            | Token               |
|---------|----------------------------------------------------|---------------------|
| `ok`    | A segment/chunk was accepted                       | success (sage)      |
| `fix`   | A directed repair round ran (QA re-entry)          | info (slate-blue)   |
| `mem`   | Translation-memory / glossary applied              | info                |
| `sum`   | A rolling-summary/context update occurred          | info                |
| `retry` | An inference retry (rate-limit/timeout) fired      | warning (ochre)     |
| `err`   | A recoverable error surfaced for a segment         | danger (terracotta) |
| `info`  | A neutral run milestone (pass started, checkpoint) | info                |

| ID          | Requirement                                                                                                                                                                                                      |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-6a | Activity-log entries use only the vocabulary above (`ok/fix/mem/sum/retry/err/info`), each mapped to its status token so it is distinguishable by more than colour (FR-A11Y-6).                                  |
| FR-NOTIF-6b | The log retains the **last N entries** (a bounded ring; older entries are dropped) so it never grows without bound during a long run.                                                                            |
| FR-NOTIF-6c | Every entry is **bundle-keyed and localized** — the log stores an event key plus arguments and renders through the `ResourceBundle`/ICU layer (`10_I18N_AND_ACCESSIBILITY.md`), never a pre-concatenated string. |

## error-dialog {#error-dialog}

| ID          | Requirement                                                                                                                               |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-3a | Blocking failures are shown in an error dialog (`Alert` error) with a summary message and an expandable technical-details section.        |
| FR-NOTIF-3b | The expandable section shows only safe details from the `AppError` allowlist — never secrets or raw internal traces beyond the allowlist. |
| FR-NOTIF-3c | The dialog offers actionable choices where possible (retry, open settings, dismiss).                                                      |

P6 reference: `08_UI_SCREENS_AND_STATES.md#dialog-error-with-details` (Translation failed).

## typed-error-surface {#typed-error-surface}

| ID          | Requirement                                                                                                                           |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-4a | Every user-visible failure derives from a typed `AppError` carrying an `ErrorCode`.                                                   |
| FR-NOTIF-4b | The user-facing message and expandable details are populated only from the safe-details allowlist (DD-14).                            |
| FR-NOTIF-4c | Error presentation is chosen by severity and blocking-ness: toast (transient), banner (persistent, in-context), or dialog (blocking). |
| FR-NOTIF-4d | Partial results are surfaced with their accompanying error so the user sees what succeeded and what did not.                          |

## error-code-categories {#error-code-categories}

Representative `ErrorCode` categories the surface must handle (non-exhaustive; canonical enum in `:api`):

| Category    | Examples                                                                 | Typical surface            |
|-------------|--------------------------------------------------------------------------|----------------------------|
| Import      | unsupported-format, corrupt-file, drm-protected                          | banner / dialog            |
| Provider    | connection-failed, discovery-failed, inference-failed, model-unavailable | banner / dialog / toast    |
| Inference   | rate-limited (retryable), timeout (retryable), non-retryable-http        | retried, then toast/banner |
| Document    | placeholder-mismatch, reassembly-failed, export-validation-failed        | dialog                     |
| Persistence | write-failed, single-instance-locked                                     | dialog                     |

Before any inference, connection + model availability are verified (FR-INFER-08); a failure surfaces as a
Provider-category error (banner/dialog). On resuming a project, the provider/model-binding prompts
(`08_UI_SCREENS_AND_STATES.md#dialog-provider-binding`) are confirmation dialogs — bound provider/model unavailable →
confirm fallback; settings differ from last-used → apply vs continue (DD-31).

**Cancellation is not an error.** A user-initiated stop/cancel of a run
(`08_UI_SCREENS_AND_STATES.md#screen-translating`) resolves to a `cancelled` outcome, **not** an `AppError`: it produces
no error dialog and no `err` toast. It is surfaced as a neutral `info` state (the run enters the resumable `stopped`
state), so an intentional stop is never presented as a failure.

## empty-states {#empty-states}

| ID          | Requirement                                                                                                      |
|-------------|------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-5a | Every list/collection screen shows an empty state when it has no data (FR-NOTIF-05).                             |
| FR-NOTIF-5b | Empty states are: Projects ("No projects yet"), Review ("Nothing flagged"), Providers (no providers configured). |
| FR-NOTIF-5c | Each empty state offers the primary next action (e.g. import a book, add a provider).                            |

P6 reference: match the mockup's Notifications section, banner anatomy, and each empty state.
