# ADR-0008 — Serialize inference through a single-flight gate with service-owned retry

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The application drives a locally hosted or self-hosted model over HTTP. Local backends typically run on a single GPU (or
CPU) and serve one generation at a time; issuing concurrent requests to them degrades throughput, inflates latency, and
can trigger out-of-memory or model-reload failures. Meanwhile the pipeline is naturally concurrent — chunk drafting,
judge scoring, and repair could all want the model at once, and diagnostic actions (connection/model/inference
verification in the provider dialog) can fire while a real translation run is in progress.

Separately, requests fail transiently: rate limits, model-load stalls, and timeouts are expected against local runtimes.
The system must decide where concurrency is bounded and where retry lives. This ADR fixes both.

## Decision drivers

- **Protect the single-GPU backend.** The model should receive one request at a time regardless of how many callers want
  it.
- **One choke point shared by everyone.** Real runs and diagnostics must contend on the same gate, or diagnostics could
  collide with a live run.
- **Transient failures should not surface to the user.** Retry belongs where the typed error information is, honouring
  server back-pressure.
- **Fresh timeout per attempt.** A retry must get its own full timeout, not the remainder of the first.
- **Predictable, testable behaviour.** Concurrency and retry must be deterministic enough to test against a mocked HTTP
  seam.
- **Keep back-pressure visible.** The UI must be able to reflect that work is queued behind the gate.

## Considered options

- **Option A — Unbounded concurrency.** Let callers hit the provider in parallel; rely on the backend to cope.
- **Option B — A work queue with N workers.** A bounded pool (potentially N > 1) drains a request queue.
- **Option C — Single-flight `InferenceGate` (a `Semaphore(1)`)** shared by real runs and diagnostics, with retry owned
  by the provider service.

## Decision outcome

Chosen: **Option C — a single-flight `InferenceGate` implemented as a `Semaphore(1)`**, shared by every inference caller
including diagnostics. Exactly one inference request is in flight at any moment; all other callers wait on the gate.
Because local single-backend runtimes serve one generation at a time, this matches the hardware reality and eliminates a
whole class of concurrent-load failures, while the rest of the system (I/O fan-out, document work) can still use virtual
threads freely — the gate bounds only the model call.

Retry is **service-owned**: the provider service, which holds the typed error information, decides whether a failure is
retryable. Retries key on typed retryable errors (rate limit, model-load, transient network/timeout), honour a
`Retry-After` header when present, and use a **fresh timeout per attempt**. Non-retryable failures fail fast as typed
`AppError`s. Diagnostics share the same gate and the same retry path, so a verification action cannot collide with a
live translation run and behaves identically to a real call.

### Consequences

Positive:

- The model is never hit concurrently; single-GPU backends run at their best and avoid concurrent-load OOM/reload
  failures.
- One gate shared by runs and diagnostics removes the run-versus-verification collision entirely.
- Transient failures are absorbed by service-owned retry with proper back-pressure handling; users see stable behaviour.
- Each retry gets a full fresh timeout, so a slow first attempt does not doom the retry.
- The gate is a natural place to expose queue depth / back-pressure to the UI.

Negative:

- The gate is the system's throughput ceiling: total translation speed is bounded by one-request-at-a-time, so a remote
  endpoint that *could* serve parallel requests is under-utilized.
- A single stuck request blocks all others until its (fresh-per-attempt) timeout fires.

Neutral:

- Concurrency elsewhere (I/O fan-out, parsing) stays free; only the model call is serialized.
- If a future remote-only deployment warrants parallelism, the gate's permit count is the single well-defined place to
  revisit — but the default remains single-flight for local safety.

## Pros and cons of the options

### Option A — Unbounded concurrency

Pros:

- Simplest to write; no coordination.
- Maximizes utilization of a backend that can actually serve parallel requests.

Cons:

- Destroys throughput and stability on single-GPU local backends; causes OOM and model-reload failures.
- Diagnostics can collide with live runs.
- Behaviour depends entirely on the backend's tolerance, which is exactly the wrong thing to assume for a local-first
  tool.

### Option B — Work queue with N workers

Pros:

- Bounded concurrency with a tunable degree of parallelism.
- A queue gives a clean place to observe and manage back-pressure.

Cons:

- For the default single-backend case the correct N is 1, so the extra pool machinery buys nothing over a single-flight
  gate.
- With N > 1 it reintroduces the concurrent-load hazard on local runtimes.
- More moving parts (pool lifecycle, fairness) than the problem needs.

### Option C — Single-flight gate + service-owned retry (chosen)

Pros:

- Exactly matches single-backend hardware; no concurrent-load failures.
- One shared choke point for runs and diagnostics; no collisions.
- Retry lives where the typed error data is, with `Retry-After` and fresh-per-attempt timeouts.
- Simple, deterministic, and testable against a mocked HTTP seam.

Cons:

- Caps total throughput at one request at a time, under-utilizing a parallel-capable remote endpoint.
- A single stuck call blocks the rest until its timeout.

## Links

- Design decisions: DD-12 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-12-inference-gate`), DD-13
  (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-13-service-owned-retry`)
- Spec clauses: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
  `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
  `docs/specification/02_Architecture/09_ERROR_HANDLING.md`
- Stories: none yet
