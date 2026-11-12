# ADR-0046 — Run a book in three stages, repair a segment once, and keep an edit only if no check got worse

**Status:** accepted (built across groups 10, 13 and 14 of `redesign-translation-pipeline`) **Date:** 2026-11-04
**Deciders:** owner (decisions D-12 to D-14 of the 2026-10-27 pipeline research)
**Supersedes:** ADR-0007 and ADR-0038 (group 14 accepted this decision fully)

## Context and problem statement

A segment that fails a check costs about eight model calls today: tiered repair rounds (DD-16), a directed fix, a
retry, and the reviewer's own round, with three different "no new blocker" variants deciding whether an edit may stay
(DD-45). Measured runs show the extra rounds seldom change the outcome, and some edits make a clean draft worse.
A heading's number is lost in the draft and never comes back at export, because nothing restores what the code could
restore without asking a model. Design D12 (checks, levels and code repairs before checks), D13 (one repair, the outcome
table, Fast's lost-marks retry) and D14 (the never-worse gate) set the shape this record fixes.

## Decision drivers

- Fewer calls per failing segment, on a local model where every call is single-flight and slow.
- A repair may never make a segment worse than the draft it started from.
- What code can restore (a heading number, a dialogue dash) is restored by code, with no model call.
- One rule, not three variants, for "may this edit stay".

## Considered options

- A: keep the tiered repair rounds and the directed fixes.
- B: three stages (Prepare, Translate, Proofread), one repair, and a never-worse gate.
- C: a model judge decides per segment whether an edit is better.

## Decision outcome

Chosen: B, because it cuts the calls of a failing segment to one and makes "never worse" a property of the code, not of a model's opinion.

- The run has three stages: Prepare (brief, names, windows), Translate (draft, code repairs, at most one repair), Proofread (the reviewer, until group 14 moves it).
- At Balanced and Max a failing draft gets one repair call; at Fast only a draft that lost its marks is drafted again once.
- The never-worse gate keeps an edit only if no check got worse than on the text it replaces; otherwise the earlier text stays.
- Export validates and writes with no model call; what code can restore is restored before the check.

### Consequences

- Positive: a failing segment costs at most one extra call; one rule for edits; the heading number survives export.
- Negative: a second repair that would have succeeded is no longer tried; the reserve trigger below watches for it.
- Neutral: the reviewer stays at Balanced until group 14, so a repaired draft is still read by it.

## Pros and cons of the options

### Option A - keep the tiered rounds

- Good: no change to a measured system.
- Bad: about eight calls per failing segment; three variants of one rule; edits that worsen a clean draft.

### Option B - one repair and the never-worse gate

- Good: bounded cost; deterministic acceptance; testable without a model.
- Bad: gives up later rounds that sometimes help.

### Option C - a model judge per segment

- Good: can weigh meaning.
- Bad: one more call per segment on a single-flight local model, and a judge that is wrong in the same way as the writer.
  Rejected.

## Falsifier

Paired grading of the changed paragraphs shows more worse than better ones. Reserve trigger: more than 1 % of
segments still need a second or third round to reach an accepted text; then a bounded second repair is reconsidered.

## Links

- Design decisions: DD-16, DD-45; `openspec/changes/redesign-translation-pipeline/design.md` D12-D14, D28
- Spec clauses: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`
- Stories: none
