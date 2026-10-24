**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`, `:api`), QA, UX **Last Updated:**
2026-10-19 **Cross-references:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`,
`docs/specification/mockups/README.md` (G1, G2, G4, L3, Run states)

# Notifications and Errors

This document specifies toasts, banners, the activity log, the error dialog, the typed error surface, and empty states,
realizing `FR-NOTIF-*`. It builds on the uniform `Result{data,error}` envelope and the typed `AppError`/`ErrorCode` with
a safe-details allowlist (DD-14), defined in `:api`. The UI behaviour here follows `mockups/README.md`; where a clause
says **planned**, it is built by the next change (UI finalization + SQLite persistence,
`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`) and is not in the app today.

**No OS/desktop notifications.** This is an owner decision, not a v1 gap. All surfacing is in-app (toasts, banners,
dialogs, the activity log, the title bar's activity chip). There is no operating-system notification channel, no
"desktop notification on finish" feature, and no Settings switch for one (`mockups/README.md`, Settings › Automation).
Run completion is surfaced in-app: the Translating screen's pinned banner and, when segments need review, the review
desk (`08_UI_SCREENS_AND_STATES.md#screen-translating`).

## toasts {#toasts}

Toasts are transient, native token-styled JavaFX nodes in four types (FR-NOTIF-01):

| Type | Token               | Fires when                                                                  |
|------|---------------------|------------------------------------------------------------------------------|
| ok   | success (sage)      | An action completes successfully (e.g. provider verified, glossary imported). |
| info | info (slate-blue)   | A neutral status update (e.g. a project renamed after a language change).   |
| warn | warning (ochre)     | A non-blocking concern (e.g. language mismatch confirmed, segment left flagged on export).  |
| err  | danger (terracotta) | A recoverable failure that does not warrant a modal (e.g. a single retry failed).           |

Placement and behaviour (`mockups/README.md`, G2):

- Toasts stack in the **bottom-right** corner of the content area, 16 px from the edges and 360 px wide (never over the
  title bar, the toolbar or the navigation), the newest lowest, at most four at once; each fades and slides in and out
  over about 160 ms (`notify/ToastStack`, `#shell-toast-host`).
- A success or info toast leaves after 5 s; a warn or err toast, and any toast with an action, after 12 s. The timer
  pauses while the pointer is over the toast.
- Every toast has a ✕ button (with a hover explanation); an identical message raised again folds into the toast
  showing and counts it (×2, ×3).
- A toast may carry **one action** button (`ToastAction`), for example "Review the results" when hidden work finishes or
  "Show progress" when other model work is refused while a long job runs in the background (G4). A click on the body of
  a toast with an action no longer dismisses it.
- **While a dialog or visible work is open, its feedback is a line in its own footer, not a toast**, so nothing lands on
  its buttons (G2). Toasts belong to the screens, not to dialogs: `Toasts.raise` hands the message to the open
  dialog's footer line (`ModalCard.feedback`, found through `DialogFeedback`) when a dialog of the modal host or the
  progress dialog of visible work is showing, and `Toasts.feedback` says a dialog's own result there and says nothing
  when no dialog is open.
- **The list of occasions is closed** (`ToastOccasion`): a book opens; a translation starts ("Translation started" /
  "Progress is saved as you go.") or finishes ("Translation finished", warning "3 segments need your review" when some
  do); a project's automatic name changes; a retry is refused; a model list cannot be read outside a dialog; a chosen book cannot be compared with the stored projects (it opens anyway); hidden
  work finishes ("Review the results"); other model work is refused while work is hidden ("Show progress"). A provider
  test, a locked step, an accepted review segment, a copied log path and a saved diagnostic bundle report in their own
  place and raise none.

Toasts must not be the sole channel for critical information (FR-A11Y-8). An export's success is reported by its own
dialog alone (`08_UI_SCREENS_AND_STATES.md#dialog-export-complete`), not an `ok` toast. Nothing pops up by itself when
hidden work ends: the activity chip goes and a toast with an action appears.

## banners {#banners}

Banners are persistent, in-context strips in three severities (FR-NOTIF-02). A banner persists until its condition is
resolved or dismissed and offers a relevant action (Try again now, Open settings, Pause, Go to Translating).

| Severity | Token   | Example condition                                                      | Where                                   |
|----------|---------|-------------------------------------------------------------------------|------------------------------------------|
| info     | info    | Welcome back — resume offered; translation finished; hidden work running | Pinned slot (run banners); Translating   |
| warn     | warning | Language mismatch (EC-LANG-1); the model is taking longer than usual     | Import; pinned slot                      |
| err      | danger  | DRM-blocked import (EC-DRM-1); AI model unreachable, missing or failed   | Import (DRM-blocked); pinned slot        |

### The pinned banner slot {#banner-slot}

**Built (task 5.8)** (`mockups/README.md`, L3): a pinned banner slot (`#shell-banners`, `BannerSlot` in `AppShellView`)
sits between the toolbar and the scrolling content. It holds the **run banner** (`RunBanner`, `#translating-banner`,
read from the run-state row) and the hidden-work banner. The run banner shows on **every** screen while it applies and,
off Translating, offers "Go to Translating", so a problem is never hidden by leaving the Translating screen; a finished
run's banner shows on Translating only.

The run banners and their exact wording come from the Run states table (`mockups/README.md`, Run states). The wording
below is the user-facing text; the program name ("Ollama") is the configured provider's.

| Run state | Banner |
|---|---|
| Ready to continue | "Welcome back — 74 of 142 segments are done." |
| Cannot start yet | "Cannot start yet — no AI model is chosen." [Open settings] |
| Waiting for the AI model | "BookLoom cannot reach Ollama — Is the Ollama app open? BookLoom tries again by itself in 0:12." [Try again now] [Skip these 4 segments] [Open settings] |
| Paused after an error | "Paused. Ollama could not be reached. Open Ollama, then press Resume." [Try again now] [Skip these 4 segments] [Open settings] |
| Stopped trying (12 h) | "Ollama has not answered for 12 hours — BookLoom stopped trying by itself." [Try again now] [Skip these 4 segments] [Open settings] |
| Model missing | "The AI model gemma4:e4b is not available — check that it is installed." [Choose another model…] |
| Stuck call | "The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00. You do not need to do anything." |
| Finished, to review | "Translation finished. 6 segments need your review. You can export now and review later." [Review 6 segments] (Translating only) |
| Finished, nothing to review | "Translation finished. Nothing needs your review." |
| Failed | "The translation stopped because of an error. Finished segments are saved." [Show details] |

The other states (Ready, Translating, Pausing, Paused, Stopping, Stopped) have no banner. Counts are ICU plurals, and the
retry verb is always "Try again now" (never "Retry" or "Send again"). Every pause on an error offers the three actions
the notifications spec names ("Report a paused or failed run in the run's own banner"); the README draws fewer for
Paused after an error and Stopped trying, and the table above follows the spec. A failed run opens **no dialog by
itself**: Show details opens the failure's dialog with its details folded. A start refused in place (`validation`)
reads "The run was refused" with its message. `RunStateMappingTest` checks this table.

Other banners are in-screen and appear with their screen (`mockups/README.md`, Changes while translating): Names &
style ("Changes apply to the next segments translated."), Book Brief read-only ("Pause the translation to change the
brief…" [Pause]), Export while translating or partial, and the hidden-work banner ("Names is running in the background
— this screen is read-only until it ends"). A progress dialog's own verdict (for example a provider test) is a banner
inside that dialog.

## activity-log {#activity-log}

The Translating screen hosts an **activity log** — an append-only, in-run event feed distinct from toasts/banners
(`08_UI_SCREENS_AND_STATES.md#screen-translating`). It has a fixed event vocabulary, each event carrying a status token
and a localized message:

| Event   | Meaning                                            | Token               |
|---------|------------------------------------------------------|---------------------|
| `ok`    | A segment/chunk was accepted                       | success (sage)      |
| `fix`   | A directed repair round ran (QA re-entry)          | info (slate-blue)   |
| `mem`   | Translation-memory / glossary applied              | info                |
| `sum`   | A rolling-summary/context update occurred          | info                |
| `retry` | An inference retry (rate-limit/timeout) fired      | warning (ochre)     |
| `err`   | A recoverable error surfaced for a segment         | danger (terracotta) |
| `info`  | A neutral run milestone (pass started, checkpoint) | info                |

Event codes and rule codes are technical: the main view shows them as plain words, and the codes appear only with
**Show technical details** on (`mockups/README.md`, G12).

| ID          | Requirement                                                                                                                                                                                                      |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-6a | Activity-log entries use only the vocabulary above (`ok/fix/mem/sum/retry/err/info`), each mapped to its status token so it is distinguishable by more than colour (FR-A11Y-6).                                  |
| FR-NOTIF-6b | The log retains the **last N entries** (a bounded ring; older entries are dropped) so it never grows without bound during a long run.                                                                            |
| FR-NOTIF-6c | Every entry is **bundle-keyed and localized** — the log stores an event key plus arguments and renders through the `ResourceBundle`/ICU layer (`10_I18N_AND_ACCESSIBILITY.md`), never a pre-concatenated string. |

## error-dialog {#error-dialog}

An error dialog is a card on the upper dialog layer (`ModalHost`, `mockups/README.md`, G1), so a failure raised while
model work is open appears **above** the progress dialog, which keeps its state underneath. It follows the "simple
first, details on request" rule (G12): the message is in plain words, and the technical part is folded away.

| ID          | Requirement                                                                                                                               |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-3a | Blocking failures are shown in an error dialog with a plain-language summary message (what happened and what to do) and an expandable technical-details section. |
| FR-NOTIF-3b | The expandable section ("Details for support") shows only safe details from the `AppError` allowlist — never secrets or raw internal traces beyond the allowlist. The `cause` is never read. |
| FR-NOTIF-3c | The dialog offers actionable choices where possible (Try again now for a retryable error, open settings, dismiss) and a **Copy details** button for support. |

Status: the error card (`notify/ErrorDialog`) is built with a message, a details toggle, Retry and Dismiss. **Planned:**
the plain-message wording, the "Details for support" label, **Copy details**, and the "Try again now" verb. The dialog
is one of the "Other dialogs" in `mockups/README.md` ("Error with details").

P6 reference: `08_UI_SCREENS_AND_STATES.md#dialog-error-with-details` (unreadable file and other hard failures).

## typed-error-surface {#typed-error-surface}

| ID          | Requirement                                                                                                                           |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-4a | Every user-visible failure derives from a typed `AppError` carrying an `ErrorCode`.                                                   |
| FR-NOTIF-4b | The user-facing message and expandable details are populated only from the safe-details allowlist (DD-14).                            |
| FR-NOTIF-4c | Error presentation is chosen by severity and blocking-ness: toast (transient, bottom-right), banner (persistent; the pinned slot for run failures), or dialog (blocking, on the upper layer). |
| FR-NOTIF-4d | Partial results are surfaced with their accompanying error so the user sees what succeeded and what did not ("Finished segments are saved"). |

## error-code-categories {#error-code-categories}

Representative `ErrorCode` categories the surface must handle (non-exhaustive; the canonical enum in `:api` is `auth`,
`timeout`, `rateLimited`, `unreachable`, `modelNotFound`, `discoveryFailed`, `modelUnavailable`, `upstream`,
`missingCredential`, `contextWindow`, `emptyCompletion`, `cancelled`, `validation`, `internal`, `busy`):

| Category    | Examples                                                                 | Typical surface            |
|-------------|----------------------------------------------------------------------------|-----------------------------|
| Import      | unsupported-format, corrupt-file, drm-protected                          | Import banner / error dialog |
| Provider    | `unreachable`, `discoveryFailed`, `modelNotFound`, `modelUnavailable`, `auth`, `missingCredential` | run banner (pinned slot) during a run; error dialog or verdict banner in Settings tests |
| Inference   | `rateLimited` (retryable), `timeout` (retryable), `upstream` (retryable), `contextWindow`, `emptyCompletion` | retried by the run, then a run banner; a single failure may be a toast |
| Document    | placeholder-mismatch, reassembly-failed, export-validation-failed        | error dialog               |
| Persistence | write-failed, single-instance-locked (`busy`)                            | error dialog               |

Retryability comes from the code (`ErrorCode.isRetryable()`), and a retryable failure offers "Try again now". The
unreachable-provider case is recovered by the run itself: it waits and retries, and shows the "Waiting for the AI model"
banner with a countdown, until it gives up after 12 hours (Run states table). A failure that stops the run entirely
leaves the run in the `FAILED` state with the Failed banner and a "Show details" action that opens the error dialog.

Before any inference, connection + model availability are verified (FR-INFER-08); a failure surfaces as a
Provider-category error (banner/dialog). On resuming a project, the provider/model-binding prompts are confirmation
dialogs — "AI model not answering" (Try again · Use another AI model… · Not now) and "A different model is chosen" (Use
the earlier model · Continue with the new one) (`mockups/README.md`, Other dialogs; DD-31). **Planned** — they arrive
with persistence.

**Cancellation is not an error.** A user-initiated stop/cancel of a run
(`08_UI_SCREENS_AND_STATES.md#screen-translating`) resolves to a `cancelled` outcome, **not** an `AppError`: it produces
no error dialog and no `err` toast. It is surfaced as a neutral state (the run enters the terminal `stopped` state, with
no banner), so an intentional stop is never presented as a failure.

## empty-states {#empty-states}

| ID          | Requirement                                                                                                                      |
|-------------|------------------------------------------------------------------------------------------------------------------------------------|
| FR-NOTIF-5a | Every list/collection screen shows an empty state when it has no data (FR-NOTIF-05).                             |
| FR-NOTIF-5b | Empty states are: Projects (the first-launch card "Translate a book on your own computer" with the AI-model line), the review desk ("Nothing needs your review"), Recurring terms ("No recurring terms yet…"), Providers (no providers configured). |
| FR-NOTIF-5c | Each empty state offers the primary next action (e.g. + New translation, add a provider, Try again).                            |

The Projects screen is **planned** (`mockups/README.md`, Projects); the review desk and Recurring terms states are built.

P6 reference: match the mockup's Notifications section, banner anatomy, and each empty state.
