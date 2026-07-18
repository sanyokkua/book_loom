**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`

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
    busy              // InferenceGate tryAcquire failed (single-flight)
}
```

**Mapping notes for the discovery/model codes** (referenced by `04_LLM_INTEGRATION.md`):

- **`discoveryFailed`** — a model-list or capability-discovery call (`/api/tags`, `/v1/models`, `/api/show`,
  structured-output probe) failed for a reason other than auth/unreachable — e.g. a malformed or unparseable listing
  response. It is **retryable** and distinct from `modelNotFound` (which is a specific model being absent) and from
  `modelUnavailable` (a *bound* model missing at run time). When discovery fails, the provider falls back to manual
  model-ID entry / the manual effective-context field (`06_DATA_MODEL_SQLITE.md#providers`, DD-44).
- **`modelUnavailable`** — a model the project is **bound** to (`translator_model`/`judge_model`) is not offered by the
  provider at run or resume time. This drives the resume **"provider unavailable → prompt"** path
  (`06_DATA_MODEL_SQLITE.md#resume-support`, `05_RELIABILITY_AND_RESUME.md`, DD-31); it is not silently substituted.

## safe-details-allowlist {#safe-details-allowlist}

`AppError.details` is populated only from an **allowlist** of safe fields — HTTP status, endpoint host (not full URL
with tokens), model name, timeout value, attempt count, QA finding names. It **never** contains: credentials, resolved
secret values, `Authorization` headers, full request/response bodies, or file contents. A helper builds `details` from
typed inputs, not from string-concatenating arbitrary exception messages, so secrets cannot leak into the UI or logs
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`).

## boundary-discipline {#boundary-discipline}

- Every public port method has a **boundary `try/catch`**: any escaping `Throwable` is caught and wrapped as
  `Result.err(AppError(internal, …, cause))`. Unhandled exceptions never cross a module edge.
- Known failures are converted to their specific `ErrorCode` at the point they are recognized (HTTP mapping in `:llm`,
  SQL errors in `:persistence`, parse errors in `:document`, QA hard-gate in `:pipeline` → `validation`).
- Logging happens once, at the boundary that first constructs the `AppError`, with the `cause`; downstream layers do not
  re-log.

## partial-results {#partial-results}

Long operations return **partial results** rather than all-or-nothing. A translation run that hits an unrecoverable
provider error mid-book returns the accepted segments so far plus the terminal `AppError`; export remains available for
what completed. A chunk that fails after `N` repair tries is `FLAGGED`, not fatal — the run continues. The engine's
return shape carries `{completed, flagged, error?}` so the UI can show "X of Y done, Z flagged, stopped because …".

## ui-surfacing {#ui-surfacing}

- A failed `Result` reaching `:ui` produces: (1) a **toast** (`err`) with `title`, and (2) for actionable/hard failures
  an **error dialog** with `message` and an **expandable technical details** panel bound to `AppError.details` (never
  `cause` raw).
- `retryable` errors offer a Retry action; `cancelled` is shown as an info state, not an error; `busy` is shown as a
  soft "model in use, try again" toast.
- Errors are marshaled to the FXAT through the state mirror's `publishToast`/`publishError`
  (`07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`), so the pipeline reports failures without touching the scene graph.
