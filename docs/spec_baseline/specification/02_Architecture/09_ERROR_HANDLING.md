**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-09-27
**Cross-references:** `docs/spec_baseline/specification/02_Architecture/04_LLM_INTEGRATION.md`,
`docs/spec_baseline/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
`docs/spec_baseline/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`

# Error Handling

The application uses one error model end to end: a `Result<T>` envelope carrying either data or a single typed
`AppError`. No checked-exception propagation crosses module boundaries; every boundary converts failure into a `Result`.
This document fixes the envelope, the error type, the `ErrorCode` enum, the safe-details allowlist, boundary discipline,
partial results, and how the UI surfaces errors.

## result-envelope {#result-envelope}

```java
public record Result<T>(@Nullable T data, @Nullable AppError error) {
    public boolean isOk()  { return error == null; }
    public boolean isErr() { return error != null; }
    static <T> Result<T> ok(T data);
    static <T> Result<T> err(AppError e);
    <R> Result<R> map(Function<T,R> f);       // short-circuits on error
    <R> Result<R> flatMap(Function<T,Result<R>> f);
}
```

Exactly one of `data`/`error` is non-null. Ports in `:api` return `Result<T>`; callers branch on `isOk()`.

## app-error {#app-error}

```java
public record AppError(
    ErrorCode code,
    String    title,        // short, user-facing
    String    message,      // one-sentence user-facing explanation
    @Nullable String details,   // safe technical detail (allowlist only) for the expandable panel
    boolean   retryable,
    @Nullable Throwable cause   // internal only; never serialized/logged with secrets
) {}
```

One `AppError` type for the whole app. `retryable` is set from the code (see retry,
`04_LLM_INTEGRATION.md#service-owned-retry`). `cause` is for logs/diagnosis and is never shown raw to the user nor
allowed to carry secrets.

## error-code {#error-code}

```java
public enum ErrorCode {
    auth,             // 401/403
    timeout,          // read/connect timeout           (retryable)
    rateLimited,      // 429, honor Retry-After          (retryable)
    unreachable,      // connect refused / no route      (retryable)
    modelNotFound,    // unknown/missing model
    discoveryFailed,  // model/capability discovery call failed (retryable)
    modelUnavailable, // a required bound model is not available at run/resume time
    upstream,         // 5xx from endpoint               (retryable)
    missingCredential,// credential reference resolves to nothing
    contextWindow,    // prompt exceeds model context
    emptyCompletion,  // 200 but no usable content
    cancelled,        // user cancelled the job
    validation,       // input/config invalid; QA hard-gate failures
    internal,         // unexpected/uncaught -> wrapped at a boundary
    busy              // a review-desk action was asked while the project's run is RUNNING
}
```

**Mapping notes for the discovery/model codes** (referenced by `04_LLM_INTEGRATION.md`):

- **`discoveryFailed`** — a model-list or capability-discovery call (`/api/tags`, `/v1/models`, `/api/show`,
  structured-output probe) failed for a reason other than auth/unreachable — e.g. a malformed or unparseable listing
  response. It is **retryable** and distinct from `modelNotFound` (which is a specific model being absent) and from
  `modelUnavailable` (a *bound* model missing at run time). When discovery fails, the provider falls back to manual
  model-ID entry / the manual effective-context field (`06_DATA_MODEL_SQLITE.md#providers`, DD-44).
- **`modelUnavailable`** — a model the project is **bound** to (`translator_model`) is not offered by the
  provider at run or resume time. This drives the resume **"provider unavailable → prompt"** path
  (`06_DATA_MODEL_SQLITE.md#resume-support`, `05_RELIABILITY_AND_RESUME.md`, DD-31); it is not silently substituted.
- **A DRM-protected or otherwise unsupported import is an inspection verdict carried as data, not a distinct
  `ErrorCode`.** Inspecting a book (`ADR-0039`) answers with a `BookInspection` whose `verdict` names the refusal
  reason (for example `DRM_PROTECTED`, naming the scheme; `DAMAGED` for a file whose content is not the format its
  name promises; `UNSUPPORTED` for any other type) as ordinary data; the import adds `NO_TEXT`, after opening, for a
  book whose body holds no word, and stores no project for it — a refusing verdict is a normal, successful
  answer from the inspector, never a thrown failure. The `ErrorCode` enum stays at its **fifteen constants** (`ADR-0022`):
  actually **opening** a book whose inspection refused it still fails with `validation`, the same code any other
  invalid input uses; inspection only moves *how the reason is discovered and displayed* earlier and off the exception
  path, not the code that failure carries.

## safe-details-allowlist {#safe-details-allowlist}

`AppError.details` is populated only from an **allowlist** of safe fields — HTTP status, endpoint host (not full URL
with tokens), model name, timeout value, attempt count, QA finding names, a failure reason, a folder's own name and,
for a prompt that did not fit, the two typed token counts the provider stated (`promptTokens=696, windowTokens=512`,
integers read from the body, never its sentence). The **failure reason** is a fixed token (`FailureReason`:
`folder-missing`, `access-denied`, `read-only`, `disk-full`, `not-utf8`, `csv-header`, `wrong-path`,
`markdown-structure`, and the provider reasons of `#failure-reasons`: `model-not-pulled`, `thinking-unsupported`,
`think-level-invalid`, `server-busy`, `option-type`, `host-forbidden`, `cloud-disabled`, `cloud-signin`,
`cloud-unreachable`, `runner-crashed`, `out-of-memory`, `repeat-limit`, `context-overflow`, `terminated`,
`no-model-loaded`, `model-unloaded`, `grammar-too-large`, `schema-invalid`, `structured-unavailable`,
`engine-unreachable`, `template-rejected`, `model-loading`, `schema-not-applied`, `provider-unavailable`, `credits`,
`blocked`, `no-endpoint-for-parameters`, `unclassified`), rendered first as
`reason=<token>`, for a failure whose code
alone cannot say what to fix: the services word failures in English, and the window words a failure in another display
language by its reason when it has advice of its own, else by its code. Every HTTP failure of a provider carries a
reason: the token of the catalogue row its body matched, or `unclassified` when none did; the provider's own sentence
reaches the log at TRACE only. A write that fails for a missing folder, a refused permission, a read-only place or a full
disk is `validation` with that reason and the folder's last name only (never its path). It **never** contains: credentials, resolved
secret values, `Authorization` headers, full request/response bodies, or file contents. A helper builds `details` from
typed inputs, not from string-concatenating arbitrary exception messages, so secrets cannot leak into the UI or logs
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`).

### failure-reasons {#failure-reasons}

A provider failure's reason is the token of the catalogue row its reply matched (`04_LLM_INTEGRATION.md#http-error-mapping`,
Table A, which also gives each row's words and status). The token says what to do; the code says how the envelope
treats it.

| Token | Usual code | Retry rule | Who is asked |
|---|---|---|---|
| `context-overflow` | `contextWindow` | NONE | nobody: the segment is flagged and the book goes on |
| `repeat-limit` | `upstream` | NEW_SEED once | nobody: the segment is flagged after the resend |
| `terminated` | `upstream` | SAME once, after a state check | nobody: a second one waits as an outage |
| `runner-crashed` | `upstream` | SAME once | nobody: the run waits as for an outage |
| `engine-unreachable` | `upstream` | SAME once | nobody: the run waits as for an outage |
| `server-busy` | `upstream` | BACKOFF | nobody: the run waits as for an outage |
| `cloud-unreachable` | `upstream` | BACKOFF | nobody: the run waits as for an outage |
| `no-model-loaded` | `modelUnavailable` | AUTO_WAIT | nobody for six wakes, then the person |
| `model-unloaded` | `modelUnavailable` | AUTO_WAIT | nobody for six wakes, then the person |
| `model-loading` | `modelUnavailable` | AUTO_WAIT | nobody: the run waits until the server is ready |
| `out-of-memory` | `modelUnavailable` | NONE | the person |
| `model-not-pulled` | `modelNotFound` | NONE | the person |
| `host-forbidden` | `auth` | NONE | the person |
| `cloud-disabled` | `auth` | NONE | the person |
| `cloud-signin` | `auth` | NONE | the person |
| `thinking-unsupported` | `validation` | NONE | nobody when the control can be dropped and the request sent again; else the person |
| `think-level-invalid` | `validation` | NONE | as `thinking-unsupported` |
| `option-type` | `validation` | NONE | the person; a BookLoom fault, logged at ERROR once per model |
| `grammar-too-large` | `validation` | NONE | the person |
| `schema-invalid` | `validation` | NONE | the person |
| `structured-unavailable` | `validation` | NONE | the person |
| `template-rejected` | `validation` | NONE | the person |
| `schema-not-applied` | `validation` | NONE | the person |
| `wrong-path` | `validation` | no row: the code's own (not retried) | the person |
| `unclassified` | the status's code (Table B) | the code's own | as the code decides |

"The person" means the run pauses and waits for them; a step that keeps failing is flagged after its pauses, so the
book goes on. Eight of these tokens have words of their own in the window (`context-overflow`, `repeat-limit`,
`out-of-memory`, `model-not-pulled`, `host-forbidden`, `cloud-disabled`, `cloud-signin`, `terminated`); every other
token is worded by its code (`01_Product/11_NOTIFICATIONS_AND_ERRORS.md#error-code-categories`).

**Log lines.** Every WARN line about a retried, given-up or refused call, or a flagged step, carries `reason=<token>`
next to `code=`. The token is read from the typed details with `FailureReason.tokenIn(details)` — never from an
exception message or a reply body — and is `none` when the details name no reason (a timeout, a failure built before
the catalogue). For example: `gave up provider chat code=validation reason=grammar-too-large kind=TERM_READING`.

## boundary-discipline {#boundary-discipline}

- Every public port method has a **boundary `try/catch`**: any escaping `Exception` is caught (an `Error` is never swallowed; only `LinkageError` where class loading can fail) and wrapped as
  `Result.err(AppError(internal, …, cause))`. Unhandled exceptions never cross a module edge.
- Known failures are converted to their specific `ErrorCode` at the point they are recognized (HTTP mapping in `:llm`,
  SQL errors in `:persistence`, parse errors in `:document`, QA hard-gate in `:pipeline` → `validation`).
- A refused setting is not an error: an HTTP 400 or 422 that names an optional setting the request sent
  (`temperature`, `top_p`, `max_tokens`, `num_ctx`, ...) is repeated without it and remembered (FR-INFER-09e), and never
  surfaces. Only one that names nothing sent is `validation`; 422 maps like 400.
- Logging happens once, at the boundary that first constructs the `AppError`, with the `cause`; downstream layers do not
  re-log.

## partial-results {#partial-results}

Long operations return **partial results** rather than all-or-nothing. A translation run that hits an unrecoverable
provider error mid-book returns the accepted segments so far plus the terminal `AppError`; export remains available for
what completed. A segment the model could not translate — the reply itself carries an error, or comes back empty or
truncated — is `FLAGGED`, not fatal; the run continues to the next segment (`ua.bookloom.pipeline.SegmentTranslator`,
built by `add-translation-engine-and-cli`).

The engine's return shape is the built `JobReport` (`ua.bookloom.api.pipeline`):
`JobReport(format, end, segments, accepted, flagged, flaggedSegments, written, error)`. `end` is always a **terminal**
`JobState` (`COMPLETED`/`CANCELLED`/`FAILED`); `written` is set only when `end` is `COMPLETED`; `error` is set only
when `end` is `FAILED` — one value lets the UI show "X of Y done, Z flagged, stopped because …" without inspecting a
separate `Result`. A **cancelled** run is not itself an error: `TranslationJob.run()` still returns
`Result.ok(JobReport)`, with `end` equal to `CANCELLED` and no `error`
(`08_THREADING_CONCURRENCY.md#cancellation`) — a genuine boundary failure (the source book will not open, or the
destination exists without overwrite) is instead returned as `Result.err(AppError)` directly, before any report is
built.

Export makes no model call (group 14 of `redesign-translation-pipeline`), so there is no "partial pass" to end early:
a cancelled export writes no file, and the report carries the stored Proofread summary (`ProofreadSummary`).

## ui-surfacing {#ui-surfacing}

- A failed `Result` reaching `:ui` produces: (1) a **toast** (`err`) with `title`, and (2) for actionable/hard failures
  an **error dialog** with `message` and an **expandable technical details** panel bound to `AppError.details` (never
  `cause` raw). `title` and `message` are written in English where the failure is recognised; a display language other
  than English shows the catalogue's words for the `ErrorCode` instead (`ua.bookloom.ui.i18n.ErrorWords`), and
  `details` stay as given.
- `retryable` errors offer a Retry action; `cancelled` is shown as an info state, not an error; `busy` is shown as a
  soft "model in use, try again" toast. A review-desk retry answers `busy` whenever the project's own run record
  (`06_DATA_MODEL_SQLITE.md`) reads `RUNNING` — read once, compared, and refused with no model call; the gate that
  serializes inference itself never refuses a caller, it only ever waits its turn.
- Errors are marshaled to the FXAT through the state mirror's `publishToast`/`publishError`
  (`07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`), so the pipeline reports failures without touching the scene graph.
