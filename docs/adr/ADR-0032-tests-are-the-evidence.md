# ADR-0032 — Tests are the evidence; no requirement-id tracking

**Status:** accepted **Date:** 2026-09-11 **Deciders:** owner **Supersedes:** ADR-0018; ADR-0016 R5 and R6

## Context and problem statement

ADR-0016 replaced generated traceability with a lighter mechanism: every covering test carried a
`// Covers: FR-*` marker, and `scripts/fr-coverage.sh` grepped those ids against the specification. By
September 2026 the repository held 465 markers, a 16-checkbox Definition of Done, and a conformance-review
agent — and its last five commits were prose reconciliation: renaming one concept required edits in eight
places, and each pass found what the previous one had missed. The owner's audit of six projects found the same
pattern wherever a gate measured document consistency instead of a running program: the documents stay
mutually consistent and the software stops moving.

## Decision

- A test is the proof of a behaviour. Its name (`method_state_expected`) and, when needed, one plain-language
  comment line say what it proves. Nothing else joins a test to a requirement.
- No requirement-id markers in tests, no script that counts ids, no checkbox ledger treated as evidence, and no
  review pass whose job is to reconcile documents with each other.
- An acceptance test is written first and observed red before the implementation; "done" means that test is
  green and the behaviour was exercised in the running app.
- `docs/specification/` is a reference, not a frozen catalogue: a clause the code legitimately outgrew is
  edited in the same change. An ADR records a decision that is costly to reverse.

## Consequences

- Positive: a change touches code, tests and at most one document; nothing has to be kept consistent by hand.
- Negative: "which requirement does this test prove?" is answered by reading the test, not by grepping an id.
  Accepted — the sentence above the test is the readable answer, and the id was never mechanically enforced.
- Neutral: OpenSpec proposals still cite the spec by file and anchor in their `Source:` blocks; that pointer
  helps a reader, it is not a tracking key.

## What would falsify this decision

A shipped behaviour regresses and no test name or comment says what it was meant to prove — the marker's
absence made the regression harder to find than the id would have.
