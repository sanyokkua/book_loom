# ADR-0022 — Reuse `ErrorCode.busy` for the single-instance lock rather than adding a sixteenth code

**Status:** accepted **Date:** 2026-08-05
**Deciders:** architect

## Context and problem statement

`09_ERROR_HANDLING.md#error-code` fixes `ErrorCode` at **fifteen constants**, and that fixity is the point: seam F2
(`Result`/`AppError`/`ErrorCode`) is consumed by every remaining change, so a constant set that grows whenever a new
producer appears is a moving target for twenty-six changes compiled against it. ADR-0017 puts the envelope in the
first infrastructure change for exactly this reason.

The spec annotates one of those constants as:

```java
busy              // InferenceGate tryAcquire failed (single-flight)
```

The `bootstrap-app-launch-and-empty-window` change then introduced a second situation with the same shape. A second
application launch fails `FileChannel.tryLock()` on `dataDir/bookloom.lock` and must tell the user "another copy is
already open" (`10_DI_AND_LIFECYCLE.md#single-instance-lock`, EC-ENV-3). That failure crosses a boundary as an
`AppError`, so it needs a code — and the frozen list offers no obvious slot, because the spec's note describes `busy`
in terms of the inference gate specifically rather than in terms of exclusion generally.

The decision is whether the single-instance lock reuses `busy`, or the enum grows.

## Decision drivers

- **Seam F2's whole value is that it does not move.** A sixteenth constant is a change to a contract twenty-six
  changes depend on, taken to describe one dialog.
- **The two situations are behaviourally identical.** Both are "an exclusive resource is held by someone else";
  both are non-retryable in the strict sense (`retryable=false`) because the caller should try again *later*, not
  automatically; both surface as "try again" rather than as a defect.
- **No other existing constant fits.** `validation` means input or configuration is invalid, and nothing here is
  invalid. `unreachable`, `timeout` and `upstream` are transport failures. `internal` is the wrapper for an escaped
  throwable. Forcing one of those would be a worse distortion than widening `busy`.
- **The constant's *name* is already general.** It is `busy`, not `gateHeld` — the narrowness lives in a trailing
  comment, not in the contract's vocabulary.

## Considered options

- **Option A — reuse `busy`, widening its documentation** to state exclusion generally with the inference gate as its
  defining case.
- **Option B — add a sixteenth constant** (`alreadyRunning` or similar).
- **Option C — reuse a different existing constant**, most plausibly `validation`.

## Decision outcome

Chosen: **Option A**, because the two situations differ only in *which* exclusive resource is held, and that is the
kind of variation a typed code is supposed to abstract over. Callers branch on the code to decide what to do; both
cases want the same thing done. `ErrorCode.busy`'s Javadoc now reads "an exclusive resource is already held, so the
request cannot proceed now", names the inference gate as its defining case, names the process single-instance lock as
the other, and states that it is deliberately non-retryable in both.

This is recorded as an ADR rather than left as a code comment because it is the one judgement call in that change a
reviewer can legitimately challenge: the frozen spec's note really does describe `busy` narrowly, and reading the
spec alone would make this look like a defect. `spec-authoring.md` requires that a genuine deviation or gap becomes a
new ADR, never a spec edit — this is the ADR.

### Consequences

- Positive: seam F2 stays at fifteen constants, so every change already compiled against it is unaffected.
- Positive: the concept the enum now carries ("something exclusive is held") is more useful than the one it carried
  ("the inference gate is held"), and generalizes to any later exclusion without further widening.
- Negative: the frozen spec's inline comment `// InferenceGate tryAcquire failed (single-flight)` is now narrower
  than the constant's real meaning. Anyone reading `09_ERROR_HANDLING.md#error-code` alone will see the narrow
  version; this ADR is the correction, and the constant's own Javadoc carries it in the place code is read.
- Neutral: `retryable=false` was already correct for the gate and is correct for the lock, so no retry behaviour
  changes.

## Pros and cons of the options

### Option A — reuse `busy`, widened

- Good: the contract does not move; behaviour is identical; the name already generalizes.
- Good: `AppError.retryable` is derived from the code, so both cases automatically agree on retry policy.
- Bad: diverges from a frozen inline comment, which is why this ADR exists.

### Option B — a sixteenth constant

- Good: literal fidelity to the spec's narrow reading of `busy`.
- Bad: changes a contract twenty-six changes compile against, in the change whose entire purpose was to fix it —
  and establishes that the list grows whenever a producer appears, which is the precedent seam F2 exists to prevent.
- Bad: callers would have to branch on two codes to render one message.

### Option C — reuse `validation`

- Good: no new constant, no widening.
- Bad: actively wrong. Nothing about a second launch is invalid; the user did nothing incorrect. `validation` drives
  "fix your input" treatment in the UI, which is the wrong instruction here.

## Links

- Design decisions: DD-14 (typed error envelope), DD-39 (paths-first startup, the lock's position in it)
- Spec clauses: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`,
  `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#single-instance-lock`,
  `docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#edge-cases` (EC-ENV-3)
- Related ADRs: ADR-0017 (infrastructure first, which is why the envelope lands before its producers)
- Changes: `bootstrap-app-launch-and-empty-window`
