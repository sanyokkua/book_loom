# ADR-0007 — Adopt an automatic-first, tiered quality pipeline with a single trust dial

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The core promise of the product is that a whole book translates automatically, end to end, with essentially no human
interaction — roughly ninety-nine percent of chunks complete on their own — while keeping names, tone, and formatting
consistent across the book. Only the small minority of chunks the machine cannot clear should be surfaced for optional
human review.

Delivering that promise requires deciding how a chunk gets from raw source to an accepted translation: how many model
calls, in what arrangement, with what quality checks, what repair strategy when a check fails, and what consistency
machinery keeps a long book coherent. Too little structure yields inconsistent, low-quality output that forces heavy
manual review; too much structure burns inference on every chunk and slows the whole book down. This ADR fixes the
pipeline shape.

## Decision drivers

- **Hands-off completion.** The common case must finish without a human; review is opt-in, not required.
- **Quality where it matters, cost where it does not.** Extra inference should be spent only on chunks that fail a
  check, not on the passing majority.
- **Book-level consistency.** Names, terminology, tone, and formatting must stay consistent across chapters, not just
  within a chunk.
- **Deterministic gates before subjective ones.** Cheap, deterministic checks should catch hard failures before any
  model-as-judge scoring.
- **A single, understandable control.** Users should tune the speed/quality trade-off with one dial, not a panel of
  expert knobs.
- **Bounded work per chunk.** Repair must have a hard attempt budget so a pathological chunk cannot stall the book.

## Considered options

- **Option A — Single-pass translation.** One model call per chunk; accept the output as-is.
- **Option B — Full multi-agent orchestration.** Multiple cooperating agents (planner, translator, critic, editor,
  consistency manager) run on every chunk.
- **Option C — Tiered hybrid, automatic-first.** A draft call, then a deterministic QA gate, then an optional
  LLM-as-judge score, then tiered self-heal that fires only on failure (directed fix for concrete findings,
  reflect→improve otherwise), then accept or flag; plus a consistency stack (preceding-target context, context-aware
  translation memory, name/term dictionary, rolling summary) and an optional whole-book backward-revision pass.

## Decision outcome

Chosen: **Option C — the tiered, automatic-first hybrid.** Each chunk is drafted once, then passes a deterministic QA
gate (tag-multiset integrity as a hard gate, target-language check, untranslated-echo, refusal, repetition-loop,
omission-by-length-ratio, glossary compliance, number/named-entity preservation, and a confidence score). If the
deterministic gate passes and the judge score meets the trust threshold τ, the chunk is accepted. Only on failure does
self-heal fire, and it is tiered: when QA produced concrete findings, a single directed-fix call injects those findings;
otherwise a reflect→improve pair (plus an optional monolingual polish) runs. Repair loops back through QA up to a
bounded budget N; a chunk still failing is flagged (~1%) for optional review.

Consistency is handled by a dedicated stack rather than by re-reading the whole book each time: a capped preceding-
*target* window (the main consistency lever), a context-aware translation memory (auto-reuse only when neighbours also
match; plain exact match is a hint, fuzzy is a suggestion), a name/term dictionary carrying gender for target-language
agreement and optional hard-locking via placeholder substitution, and a rolling bilingual summary per chapter. An
optional whole-book backward-revision pass resolves deferred items with full-book facts and runs a global
term-consistency sweep.

A single "quality vs speed" dial (Fast / Balanced / Max) sets chunk size, the number of preceding-target blocks, the
threshold τ, the repair budget N, whether the judge runs, and whether backward revision runs. Review is opt-in via a
single trust-threshold dial and three modes (Unattended / Assisted / Manual).

### Consequences

Positive:

- The passing majority costs one draft call plus cheap deterministic checks; expensive repair is spent only where a
  check fails.
- Book-level consistency comes from a targeted, mostly deterministic stack rather than from re-reading the whole book
  per chunk.
- Deterministic gates catch hard failures (lost tags, wrong language, echoes, omissions) before any subjective scoring.
- One dial gives users a comprehensible speed/quality trade-off; review is genuinely optional.
- A bounded repair budget guarantees the book always makes forward progress.

Negative:

- The pipeline has many coordinated parts (gate, judge, two repair tiers, consistency stack, backward revision); it is
  more to build, test, and reason about than a single pass.
- The judge and repair tiers add latency and cost on the minority of chunks that need them.
- Tuning τ, N, and the dial mappings requires empirical calibration.

Neutral:

- Neural quality-estimation is deferred as a documented future sidecar (see
  `docs/adr/ADR-0008-inference-concurrency-gate.md` is a peer concern; QE deferral is recorded in the design decisions
  log).
- Consistency uses a name/term dictionary, context-aware translation memory (exact / context / fuzzy via deterministic
  string similarity), and a rolling bilingual summary — no embeddings, no vector store, no RAG stage (DD-18).
- The canonical flow is diagrammed at `docs/specification/diagrams/pipeline.mermaid` and
  `docs/specification/diagrams/chunk-translate-loop.mermaid`.

## Pros and cons of the options

### Option A — Single-pass

Pros:

- Simplest and cheapest per chunk; one call, no orchestration.
- Fast to build and easy to reason about.

Cons:

- No quality gate: lost tags, wrong-language output, refusals, and omissions ship silently.
- No consistency machinery, so names and terminology drift across a long book.
- Pushes almost all quality assurance onto the human, defeating the automatic-first promise.

### Option B — Full multi-agent on every chunk

Pros:

- Highest ceiling on per-chunk quality when every chunk gets planner/critic/editor attention.
- Rich, explicit reasoning steps.

Cons:

- Spends many model calls on every chunk, including the ~99% that a single draft already gets right — with a
  single-flight local model this is prohibitively slow for a whole book.
- Far more moving parts and failure modes; hard to keep deterministic and testable.
- Overkill relative to the measured pass rate; cost scales with book length, not with difficulty.

### Option C — Tiered hybrid, automatic-first (chosen)

Pros:

- Cost scales with difficulty: cheap for passing chunks, more effort only on failures.
- Deterministic-first gating catches hard failures reliably and cheaply.
- A dedicated consistency stack keeps a long book coherent without re-reading it.
- One dial and opt-in review deliver the hands-off promise with an understandable control.
- Bounded repair guarantees forward progress.

Cons:

- The most machinery of the three to build and calibrate.
- Judge and repair add latency/cost on the minority path; τ, N, and dial mappings need tuning.

## Links

- Design decisions: DD-15 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-15-automatic-first`), DD-16
  (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-16-tiered-self-heal`), DD-17
  (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-17-deterministic-qa-plus-judge`), DD-18
  (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-18-consistency-stack`), DD-19
  (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-19-paragraph-chunking`)
- Spec clauses: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`,
  `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`, `docs/specification/01_Product/06_REVIEW_AND_EDITING.md`,
  `docs/specification/diagrams/pipeline.mermaid`, `docs/specification/diagrams/chunk-translate-loop.mermaid`
- Stories: none yet
