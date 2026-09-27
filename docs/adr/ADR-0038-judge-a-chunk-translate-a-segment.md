# ADR-0038 — Judge a chunk of segments together; generate every target one segment at a time

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner (plan approval) **Amends:** FR-ALGO-C13

## Context and problem statement

The shipped draft contract sends exactly one source segment per call and accepts exactly `{"target":"…"}` (FR-ALGO-C4,
`01_Product/12_PROMPT_CATALOG.md#draft-translation`), because small local models follow that shape reliably and fail
at id-keyed arrays. The specification also asks that QA and the judge score **per chunk**, and that a directed fix
**re-render the whole chunk** (FR-ALGO-C13, `02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`) — which needs a
multi-segment reply and contradicts C4. Judging every segment on its own doubles the model calls on Balanced.

## Decision

- A **chunk** is a run of consecutive segments of one unit, packed to the token budget and capped by the quality dial:
  at most 8 segments on Fast, 4 on Balanced, 2 on Max; Manual review mode forces 1.
- **Generation stays per segment**: every draft, directed fix, reflect/improve and polish call returns one
  `{"target":"…"}` for one segment. The chunk's other targets may be shown read-only as context.
- **The judge runs once per chunk**, seeing the chunk's source/target pairs labelled `s1…sk` (local labels, not
  segment ids), and returns the catalogue's score, verdict, findings and deferrals. Only pairs that passed their hard
  gates and were not reused from the translation memory are sent to it.
- A segment is accepted when its hard gates pass, **no deterministic soft check failed outright**, its confidence
  reaches τ, and either the judge is off or the chunk's score reaches τ_judge with no medium or high finding against
  that segment. A failed soft check raises a medium finding and sends the segment to repair; the untranslated-echo
  check counts as failed outright only when the source has at least 20 code points of display text. Only the segments
  that fail are repaired.
- **A context-matched memory reuse is not judged**: it is accepted on its hard gates, no failed check and confidence
  ≥ τ, and is never part of the chunk's judge call; a reuse that fails is drafted instead.
- **Repairs are re-judged singly**: when the judge is on, a segment whose repaired target passes its hard gates, has no
  failed soft check and reaches τ is judged again on its own — a one-pair judge call labelled `s1` — and its accept test
  uses that score, not the chunk's first one. A repair short of any of these goes to the next round without a judge
  call.
- The screen's "chunk k/n" is the chunk index within the current chapter.

## Considered options

- **Judge per segment.** Rejected: roughly twice the judge calls, and the judge loses cross-sentence context.
- **Re-render the whole chunk on a fix, as specified.** Rejected: needs multi-segment output, which small models break.

## Consequences

- **Positive:** fewer calls; the output contract stays the one proven in real runs.
- **Negative:** one weak segment can lower the chunk score for its neighbours; the per-segment finding rule limits
  that, and a low chunk score with no finding routes each segment through reflect → improve.
- **Neutral:** FR-ALGO-C13 and the per-chunk phrasing in the prompt catalogue are edited.

## What would falsify this decision

Real runs show chunk scores dominated by one segment so often that accepted neighbours are routinely repaired for
nothing, or the judge cannot keep the `s1…sk` labels straight.

## Amended in this change (2026-09-27)

The owner decided during the readiness review of `complete-translation-workflow` that a deterministic check failing
outright blocks acceptance, so confidence decides only close calls: with the τ values chosen, an echoed source, a
wrong-script reply, an omission or a decode loop would otherwise be accepted without a judge. The echo floor of 20 code
points keeps short names, numerals and "OK" from being flagged en masse. The memory-reuse exception and the judge's
input were implicit in `05_PIPELINE_ENGINE.md#context-aware-tm` and are now stated. The design's D8 holds the margins,
refusal phrases and echo metric.
