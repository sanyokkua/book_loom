# Error Envelope

Scope: every module — the shared failure model. Types live in `:api` (`ua.bookloom.api`). Spec: `docs/specification/02_Architecture/09_ERROR_HANDLING.md`, `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`.

## MUST

- **MUST** return `Result<T>(@Nullable T data, @Nullable AppError error)` from every `:api` port method; exactly one of `data`/`error` is non-null. Callers branch on `isOk()`/`isErr()`; `map`/`flatMap` short-circuit on error. — Rationale: one uniform success/failure shape across all boundaries.
- **MUST** use the single `AppError(ErrorCode code, String title, String message, @Nullable String details, boolean retryable, @Nullable Throwable cause)` record for every failure — no per-module error types. — Rationale: one error model end to end; the UI renders it uniformly.
- **MUST** classify every known failure to a specific `ErrorCode` at the point it is recognized (HTTP in `:llm`, SQL in `:persistence`, parse in `:document`, QA hard-gate in `:pipeline`→`validation`). `retryable` is derived from the code. — Rationale: callers act on typed codes, not string matching.
- **MUST** give every public port method a **boundary `try/catch`**: any escaping `Throwable` is caught and wrapped as `Result.err(AppError(internal, …, cause))`. No exception crosses a module edge. — Rationale: modules communicate via the envelope, never via propagated throwables.
- **MUST** populate `AppError.details` only from the **safe-details allowlist** (HTTP status, endpoint host without token, model name, timeout value, attempt count, QA finding names), built from typed inputs — never by concatenating arbitrary exception messages. — Rationale: prevents secrets/credentials/bodies leaking into UI or logs.
- **MUST NOT** put credentials, resolved secrets, `Authorization` headers, full request/response bodies, or file contents in `details`; `cause` is internal-only, never shown raw to the user. — Rationale: privacy invariant; see `logging.md`, `offline-and-privacy.md`.
- **MUST** log a failure once — at the boundary that first constructs the `AppError`, with the `cause` — and not re-log it downstream. — Rationale: one clean log line per failure, no duplication.

## SHOULD

- **SHOULD** return **partial results** from long operations rather than all-or-nothing: a run carries `{completed, flagged, error?}` so a mid-book provider failure still yields accepted segments and keeps export available. — Rationale: a book run is never wasted by one failure.
- **SHOULD** map `ErrorCode` to UI treatment consistently — `err` toast + expandable-details dialog for hard failures, Retry action for `retryable`, info state for `cancelled`, soft toast for `busy`. — Rationale: predictable, code-driven surfacing.

## Reject if

- A port method throws or returns something other than `Result<T>`.
- A new error type is introduced instead of reusing `AppError`/`ErrorCode`.
- `AppError.details` is built by string-concatenating an exception message, or contains a secret/header/body/file content.
- A `Throwable` escapes a public port method without being wrapped into `Result.err`.
- The same failure is logged at multiple layers, or logged with the secret-bearing `cause`.
- A long operation aborts all-or-nothing instead of returning completed + flagged + terminal error.
