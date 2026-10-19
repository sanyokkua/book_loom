# ADR-0040 — A paired placeholder must come back in order and properly nested; an atomic placeholder may move

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner (plan approval)

## Context and problem statement

The placeholder gate compares only the multiset of `⟦gN⟧` tokens (FR-DOC-05); the shipped requirement even says
reordered tokens pass. A model that answers `⟦g0⟧old⟦g1⟧` with `⟦g1⟧OLD⟦g0⟧` therefore passes, and the restored
markup is broken: EPUB's lenient parse repairs it silently, FB2 refuses it at write, and since export re-opens the
written book the whole export can fail at the very end (`docs/next_features.md` §9, backlog D7/D8).
`.claude/rules/document-roundtrip.md` says reordered placeholders must fail; the shipped specification says they must
pass. One has to change.

## Decision

- The gate keeps comparing the multiset, and additionally requires every **paired** token (an opening and its closing
  partner, as recorded when the segment was masked) to appear opening-before-closing and properly nested with the
  other pairs.
- An **atomic** token (an image, a line break, a code span, a locked term) may move, because moving it is a legitimate
  word-order change in the target language — with one limit: a **line-break** token must keep its innermost enclosing
  pair (inside the same pair, or outside every pair, as in the source), because moving it across a pair boundary
  changes how many runs the restored markup has.
- A pair whose source content holds text (a non-whitespace character outside tokens) must still hold text in the
  target, so `⟦g0⟧old⟦g1⟧` answered with `⟦g0⟧⟦g1⟧OLD` fails.
- A violation fails as `ErrorCode.validation` for that one segment, which the pipeline repairs or flags; export never
  meets it.

## Considered options

- **Strict order for every token.** Rejected: word order legitimately differs between languages.
- **Multiset only, as shipped.** Rejected: it lets broken markup through to the export.

## Consequences

- **Positive:** one bad segment is flagged instead of a whole export failing.
- **Negative:** masking must record which tokens pair up; the flat token map gains that information.
- **Neutral:** the shipped requirement's "reordered tokens pass" scenario and the rule text are both edited.

## What would falsify this decision

Legitimate translations are flagged because a language needs to move an inline-formatted phrase in a way that crosses
another pair.

## Amended in this change (2026-09-27)

The readiness review of `complete-translation-workflow` found two replies the order rule alone still passes: an emptied
pair, which drops the formatted words out of their formatting, and a line break moved across a pair boundary, which
changes the run count and fails the whole export when the written book is re-opened. Both rules are added so that the
backlog's D7 can be marked closed.
