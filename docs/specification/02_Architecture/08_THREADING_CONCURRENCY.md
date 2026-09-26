**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-09-15
**Cross-references:** `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
`docs/specification/03_NonFunctional/05_RELIABILITY_AND_RESUME.md`

# Threading and Concurrency

The application has one UI thread and a pool of worker threads. This document fixes the rules that keep the FX
Application Thread responsive, bridge worker results back safely, and reconcile I/O fan-out with the single-flight
inference gate.

## fx-thread-rules {#fx-thread-rules}

- The **FX Application Thread (FXAT)** does layout, rendering, and event handling only. No blocking call (HTTP, SQLite,
  file IO, parsing, inference) ever runs on it.
- Scene-graph nodes are mutated **only** on the FXAT.
- Any handler that would block hands off to a worker immediately and returns.

## long-work-off-thread {#long-work-off-thread}

- Long-running work runs as `javafx.concurrent.Task` / `Service` submitted to an **injected daemon `ExecutorService`**
  (constructed in the DI root, `10_DI_AND_LIFECYCLE.md`), never `new Thread`. Daemon threads so shutdown is not blocked.
- A `Task` reports progress/value/exception through its observable properties; the controller binds progress UI to them.
- Import, parse, the full translation run, export, and provider verification are all Tasks.

## platform-runlater-bridging {#runlater-bridging}

- Worker → UI updates cross via the observable state mirror's `publish*` methods, which wrap `Platform.runLater`
  (`07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`).
- Updates are **batched**: high-frequency events (per-segment accepts) are coalesced (e.g. drained on a short cadence)
  into a single `runLater` so the FXAT is not flooded — one enqueue per batch, not per segment.
- Workers never touch nodes directly; the mirror is the only bridge.

## virtual-threads-io {#virtual-threads}

- I/O fan-out (reading many EPUB entries, hashing segments, disk checkpoints) uses **virtual threads**
  (`Executors.newVirtualThreadPerTaskExecutor()`), which suit high-count blocking IO.
- Virtual threads are used for IO parallelism only — **never for concurrent model generation**.

## db-write-confinement {#db-write-confinement}

- SQLite is single-writer. All **write transactions** are confined to a **single-writer executor** (a serialized
  handle / one write-owning connection) so however many worker or virtual threads run, at most one writer touches the
  database at a time (`06_DATA_MODEL_SQLITE.md#storage-conventions`).
- **Readers may run concurrently** on other pooled connections; only writes serialize.
- Every pooled connection re-applies the full PRAGMA set (`foreign_keys`, `busy_timeout`, `synchronous`, WAL) via the
  connection-init hook, so `busy_timeout` turns any residual lock contention into a bounded wait rather than an
  immediate `SQLITE_BUSY`.

## inference-gate-interplay {#gate-interplay}

- All inference funnels through `InferenceGate` (`Semaphore(1)`), so however many worker/virtual threads exist, exactly
  one `chat` call is in flight (`04_LLM_INTEGRATION.md#inference-gate`).
- The pipeline may prepare the *next* chunk's context concurrently while a generation is in flight, but the actual
  `chat` call blocks on the gate.
- Interactive "retry now" uses `tryRun` (`tryAcquire`) so it fails fast with `ErrorCode.busy` rather than deadlocking
  behind the batch.

## cancellation {#cancellation}

- Each translation run is its own handle: `TranslationJob` (`:api`, `ua.bookloom.api.pipeline`, built by
  `add-translation-engine-and-cli`) combines the runnable and the control surface — `pause()`, `resume()`, `cancel()`
  and `pauseAt(Set<PausePoint>)` are callable from any thread, while `run()` executes synchronously on the caller's
  thread (ADR-0033). There is no separate `JobHandle`.
- Outside a model call, cancellation is cooperative and is checked at **safe boundaries**: before each segment's model call, after
  each segment is decided (where an enabled `PausePoint` — `AFTER_SEGMENT`, `AFTER_SECTION`, `BETWEEN_STAGES` — or a
  requested pause is also honored at the same point), before export begins, and once more just before export's final
  move. Inside export the only pause point is `ON_ERROR`; a pause requested while exporting is ignored until the next
  boundary. A `cancel()` raised before `run()` is called makes `run()` return at once without opening the book.
- A pause or a cancel raised while a model call is in flight **aborts that call at once** rather than waiting for the
  provider: the job thread is interrupted only while it is inside a model call, the provider client answers
  `ErrorCode.cancelled`, and no further request — no retry, no structural or placeholder repair — is sent. On a pause the
  interrupted segment has no decision and is translated again on resume; on a stop the run ends Cancelled and writes
  nothing; a reply that had already arrived before the click is decided normally. Neither the wait while paused nor the
  export is ever interrupted. The request timeout stays the upper bound only for a call nobody cancels.
- **Cancelling ends the run with a Cancelled report, not a thrown or propagated `ErrorCode.cancelled`.** `run()`
  still returns `Result.ok(JobReport)`, with `JobReport.end()` equal to `JobState.CANCELLED`; `JobReport.error()` is
  populated only when `end()` is `FAILED` (`09_ERROR_HANDLING.md#partial-results`), so a caller tells cancellation
  from failure apart by state, never by inspecting an error code. `ErrorCode.cancelled` still appears **internally** —
  a model or `unmask` failure classified `cancelled` is translated into the same Cancelled report — but it never
  reaches the job's caller as a failed `Result`.
- Segments already `ACCEPTED`/`FLAGGED` before the cancel point are still counted in the report the job returns.
  Surviving a restart is not built yet: `:persistence` stays empty in `add-translation-engine-and-cli`, so nothing is
  checkpointed to disk — see `resume-checkpoints` below for the intended, not-yet-built behaviour.

## resume-checkpoints {#resume-checkpoints}

- Because each accepted segment is an atomic checkpoint (`06_DATA_MODEL_SQLITE.md#resume-support`), pause/cancel/crash
  all resume from the **first `PENDING` segment**; `ACCEPTED`/`REVISED` are terminal, and **`FLAGGED` is
  terminal-for-run** (surfaced in the review queue, excluded from auto-resume).
- Resume-on-launch reconstructs job state from `segments.status` and continues; no in-memory-only progress is trusted
  (`05_RELIABILITY_AND_RESUME.md`, `EC-RESUME-*`).

## no-synchronized {#no-synchronized}

- New code MUST NOT use the `synchronized` keyword (it pins virtual threads). Use `java.util.concurrent` primitives —
  `Semaphore`, `ReentrantLock`, `ConcurrentHashMap`, `AtomicReference`, `BlockingQueue`, `CompletableFuture`.
- Shared mutable state is confined (the state mirror is FXAT-confined; the gate is a `Semaphore`; DAOs are
  transaction-scoped). This is checked in review and, where expressible, by an ArchUnit/SpotBugs rule.
