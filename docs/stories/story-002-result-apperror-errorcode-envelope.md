---
id: STORY-002
title: Implement the Result/AppError/ErrorCode envelope in :api
status: ready
spec_clauses:
  - docs/specification/02_Architecture/09_ERROR_HANDLING.md#result-envelope
  - docs/specification/02_Architecture/09_ERROR_HANDLING.md#app-error
  - docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code
  - docs/specification/02_Architecture/09_ERROR_HANDLING.md#safe-details-allowlist
  - docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results
  - DD-14
modules:
  - :api/ua.bookloom.api
acceptance_criteria:
  - STORY-002-AC-1
  - STORY-002-AC-2
  - STORY-002-AC-3
  - STORY-002-AC-4
edge_cases: [ ]
depends_on:
  - STORY-001
adrs: [ ]
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: M
---

# STORY-002 — Implement the Result/AppError/ErrorCode envelope in :api

## Goal

Establish seam F2: the uniform `Result<T>{data,error}` envelope with one typed
`AppError{code,title,message,details,retryable,cause}` and the `ErrorCode` enum (including `discoveryFailed` and
`modelUnavailable`), plus the safe-details allowlist and partial-result support, so every later fallible operation
crosses boundaries the same way from the first service onward.

## In scope

- `Result<T>` record with `ok`/`err`/partial factory methods and combinators needed by callers (`map`, `isOk`,
  `orElseThrow`-free accessors).
- `AppError` record + `ErrorCode` enum seeded with the catalogued categories
  (import/provider/inference/document/persistence) including `discoveryFailed`, `modelUnavailable`, `cancelled`.
- The safe-details allowlist mechanism: `details` accepts only allowlisted keys; secrets can never be attached.
- Partial results: a `Result` carrying both `data` and `error`.

## Out of scope

- Any concrete error mapping (HTTP→code lands with `:llm` in PHASE_05; SQLite mapping in PHASE_04).

## Spec inputs

- `09_ERROR_HANDLING.md#result-envelope` / `#app-error` / `#error-code` — the exact shapes and fields.
- `09_ERROR_HANDLING.md#safe-details-allowlist` — allowlisted detail keys; no secrets.
- `09_ERROR_HANDLING.md#partial-results` — both-data-and-error semantics.
- DD-14 — the envelope decision.

## Design constraints

- All carriers are Java records in `:api` (DD-05 — records for data; no Lombok on carriers); `:api` stays framework-free
  (no Guice/Jackson/JavaFX imports).
- `Optional` as return type only; `requireNonNull` at public boundaries (java-coding-style rule).
- No checked exceptions in the API surface; causes preserved inside `AppError.cause`.

## Acceptance criteria

### STORY-002-AC-1

Calling `Result.err(appError)` yields a `Result` whose `error.code` is the given `ErrorCode`, whose `data` is absent,
and whose type is a record. (P3)

### STORY-002-AC-2

Constructing an `AppError` with a non-allowlisted `details` key is rejected (or the key is dropped, per the spec's
allowlist behaviour) — a secret-bearing key can never be carried in `details`. (P5)

### STORY-002-AC-3

A partial `Result` carries both `data` and `error` simultaneously and reports itself as partial to callers. (P3)

### STORY-002-AC-4

The `ErrorCode` enum contains every category the error-handling spec enumerates, including `discoveryFailed`,
`modelUnavailable`, and `cancelled`, and `:api` compiles with no framework imports. (P3)

## Test plan

- STORY-002-AC-1 → unit · api/src/test/java/ua/bookloom/api/ResultTest.java · errCarriesTypedCode ·
  `Proves: STORY-002-AC-1`
- STORY-002-AC-2 → unit · api/src/test/java/ua/bookloom/api/AppErrorSafeDetailsTest.java ·
  rejectsNonAllowlistedDetailKey · `Proves: STORY-002-AC-2`
- STORY-002-AC-3 → unit · api/src/test/java/ua/bookloom/api/ResultTest.java · partialCarriesDataAndError ·
  `Proves: STORY-002-AC-3`
- STORY-002-AC-4 → architecture · arch-test/src/test/java/ua/bookloom/arch/ApiFrameworkFreeTest.java ·
  errorCodeCatalogCompleteAndApiFrameworkFree · `Proves: STORY-002-AC-4`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate. Seam F2 exists
and is exercised by tests.
