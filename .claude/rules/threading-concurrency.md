# Threading & Concurrency

Scope: all modules; especially `:ui`, `:pipeline`, `:llm`. Spec: `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`. See also `javafx-ui.md`, `llm-provider-integration.md`.

## MUST

- **MUST** use the **FX Application Thread for UI only** — reading/writing the scene graph and control properties. No blocking, no I/O, no translation work on the FXAT. — Rationale: a blocked FXAT freezes the app.
- **MUST** run background work in a JavaFX `Task`/`Service` on an **injected daemon executor** (not `new Thread`, not the common pool). — Rationale: lifecycle-managed, cancellable, testable executors; daemon threads never block JVM exit.
- **MUST** bridge results back to the UI **only** through `Platform.runLater` (via the state mirror's `publish*`), and **batch** high-frequency updates instead of one call per segment. — Rationale: one safe seam onto the FXAT without flooding its queue.
- **MUST** use **virtual threads** for I/O fan-out (e.g. parallel provider verification, independent file reads), not for CPU-bound or gated inference work. — Rationale: cheap concurrency for blocking I/O.
- **MUST** route all model inference through the single-flight `InferenceGate` (`Semaphore(1)` `tryAcquire`), even when called from multiple virtual threads; a failed acquire is `busy`. — Rationale: the local model serves one request at a time.
- **MUST** support **per-job cancellation**: a job checks its cancel flag / `Task.isCancelled()` at chunk boundaries and stops promptly, releasing the gate. — Rationale: users can pause/stop a run cleanly.
- **MUST** persist **resume checkpoints** per chunk (atomic write) so a crash or pause resumes without re-translating accepted segments. — Rationale: crash-safe resume; see `persistence-sqlite.md`.
- **MUST NOT** use `synchronized` in new code; coordinate with immutability, confinement, `java.util.concurrent` types, or the gate. — Rationale: avoid coarse locks and deadlocks; see `java-coding-style.md`.

## SHOULD

- **SHOULD** keep shared mutable state behind the `@Singleton` state mirror (FXAT-confined) or concurrent collections, never ad-hoc locks. — Rationale: one confinement strategy per kind of state.
- **SHOULD** make executors and the gate injected singletons so tests can substitute deterministic ones. — Rationale: deterministic concurrency in tests.

## Reject if

- Long/blocking work or I/O runs on the FX Application Thread.
- Background code touches the scene graph without `Platform.runLater`/the state mirror.
- A raw `new Thread(...)` or the common ForkJoin pool is used instead of the injected executor / virtual threads.
- Inference bypasses the `InferenceGate`, or the gate is made blocking/multi-permit.
- A job cannot be cancelled at chunk boundaries, or cancellation leaks the gate permit.
- New code introduces `synchronized`.
