# ADR-0025 — Reassemble by replacing a segment run's inner content, not a single text node

**Status:** accepted **Date:** 2026-08-09
**Deciders:** architect
**Supersedes:** none

## Context and problem statement

Three clauses of the frozen specification describe how a translation is written back into a parsed book. All three
are normative, and they cannot all be satisfied:

| Clause | Says |
|---|---|
| FR-DOC-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`) | "never regenerate it; **only text nodes change**" |
| `02_Architecture/03_DOCUMENT_MODEL.md#reassembly` | "`targetInner` is written back into the **exact text node** the segment was parsed from" |
| `02_Architecture/03_DOCUMENT_MODEL.md#data-model` | `sourceInner` is "the **raw inner content** of the block element, **inline descendants still present**"; `targetInner` is "translated **inner content** after unmask" |

A text node cannot contain an element. Once masking (change 5) restores inline fragments on unmask, `targetInner`
holds inner *content* — `Привіт <em>світ</em>.` — and there is no text node that can receive it. The first two
clauses describe a mechanism the third clause's data shape makes impossible.

`add-document-skeleton-and-epub-roundtrip` implemented the text-node reading. `SkeletonAnchors.writeBack` resolves
an anchor to the block's first non-blank `TextNode` and calls `TextNode.text(targetInner)`, which escapes on output.
For `<p>Hello <em>world</em>.</p>` a written-back translation produces:

```
<p>Привіт &lt;em&gt;світ&lt;/em&gt;.<em>world</em>.</p>
   └──── markup escaped into text ────┘└─ original run left in place ─┘
```

Two defects at once: the restored markup is escaped into literal text, and the original inline children are never
removed. **No test in the repository detects this.** The primary EPUB fixture's paragraphs are all plain text, no
test anywhere constructs a segment containing inline markup, and the golden round trip performs zero edits — so
`writeBack` is never exercised against the shape it exists for.

A survey of 194 real EPUBs measured the blast radius: **23.4% of all block elements corpus-wide (121,711 of
519,369) contain inline markup**, median 8.4% per book, and **eight books exceed 99%** — one wraps 7,159 of 7,160
paragraphs as `<p><span><i>…</i></span></p>`. Those eight books would be corrupted in their entirety.

A second force arrived from the same corpus. Two formats do not have a tree skeleton at all: TXT's skeleton is the
original byte buffer (FR-DOC-TXT-3), and Markdown adopts the same model (see `#considered-options`, Option D
rationale, and the change's `design.md` D4). "Write into a node" has no meaning for either.

A third force is `<br/>`-delimited prose. One EPUB in the corpus (`kotliarevskyy…eneida1052.epub`) carries 465,500
characters inside four `<div>` elements separated by 9,290 `<br/>`, and one FB2 (`Chvarakoroliv.fb2`) holds
1,850,851 characters as the *tails* of 11,944 `<br/>` inside two `<p>`. Treating such a block as one segment
produces a segment larger than any model context; treating each inter-`<br/>` run as a segment means several
segments share one block, so a node path alone no longer identifies a segment.

## Decision drivers

- **The write-back mechanism must work for the shape it exists for.** Inline markup inside a translatable block is
  the normal case, not an edge case.
- **One reassembly model, expressible for both skeleton kinds** — trees (EPUB, FB2) and byte buffers (Markdown,
  TXT) — so masking, the pipeline and export stay format-agnostic (seam F1,
  `07_ROADMAP.md#forward-compatibility-seams`).
- **Preserve the intent of FR-DOC-03.** "Never regenerate the skeleton" is the invariant that matters; "only text
  nodes change" is the mechanism that was written to express it, and the mechanism is what fails.
- **Resolve it before change 5, not during it.** Masking is the first consumer that populates `targetInner` for
  real; discovering this there means redesigning the anchor after four changes have compiled against it.

## Considered options

- **Option A — Status quo.** Keep writing into a single text node.
- **Option B — Replace the block element's entire inner content.**
- **Option C — Replace the inner content of a segment's *run* within its block** (a run being the maximal sequence
  of child nodes between `<br/>` boundaries; a block with no `<br/>` has exactly one run).
- **Option D — Rebuild the block from a re-parsed `targetInner`.**

## Decision outcome

Chosen: **Option C**, expressed per skeleton kind.

**Tree skeletons (EPUB, FB2).** A segment addresses a **run** within a block: `NodeAnchor(List<Integer> nodePath,
int runIndex)`, where `nodePath` locates the block from the unit root and `runIndex` selects the inter-`<br/>` run.
`runIndex` is `0` for every block that contains no `<br/>`, which is the overwhelming majority. Reassembly replaces
the child nodes of that run with the nodes parsed from `targetInner`, and touches nothing else: no `<br/>` element
moves, no other run changes, no attribute, comment or sibling is affected.

**Buffer skeletons (Markdown, TXT).** A segment addresses a byte span: `ByteSpanAnchor(int startInclusive, int
endExclusive)` into the original buffer. Reassembly copies the original buffer to fresh output and substitutes only
the spans of segments carrying target text, in one ascending pass, never mutating the source buffer.

`SkeletonAnchor` therefore becomes a sealed interface permitting those two records. The `childIndex` component of
the current `SkeletonAnchor` — "index of the first non-blank text node" — is **deleted**; it was the source of the
defect and has no counterpart in this model.

FR-DOC-03's invariant is preserved in the sense that matters: the skeleton is never regenerated, no block is added,
removed or reordered, and every structure the segment did not capture is carried through untouched. What changes is
the *unit* of replacement — a run's inner content rather than one text node — because that is the unit `sourceInner`
and `targetInner` have always described.

### Consequences

Positive:

- Write-back works for blocks containing inline markup — 23.4% of all blocks in the measured corpus, and effectively
  all of eight books.
- One model covers both skeleton kinds; masking, the pipeline and export need no format branch.
- `<br/>`-delimited prose becomes translatable at a sane granularity, so two real books in the corpus stop
  producing one enormous segment (or none at all).
- Buffer skeletons make a no-edit round trip byte-identical **by construction** rather than by careful
  re-serialization: for TXT this is the specified outcome (FR-DOC-TXT-3), and for Markdown it removes the whole
  class of renderer-normalization defects.

Negative:

- **This is a documented deviation from the literal wording of FR-DOC-03 and
  `03_DOCUMENT_MODEL.md#reassembly`.** The frozen specification is not edited; every requirement in
  `openspec/specs/document-round-trip/` that relies on this cites this ADR.
- `SkeletonAnchor` changes from a record to a sealed interface, and `NodeAnchor` changes shape. Every construction
  site is a compile break. All of them are inside `:document` and its tests today, and this is the last change for
  which that is true.
- A run is a concept a reader must learn. For every block without `<br/>` it is invisible (`runIndex == 0`), which
  is both the mitigation and the risk: it is easy to forget the component exists.

Neutral:

- Anchors are not persisted (`06_DATA_MODEL_SQLITE.md#tables` has no anchor column), so this changes no schema and
  needs no migration. It does make *parse determinism* load-bearing on the resume path — the same bytes must yield
  the same anchors after a restart — which the change states as its own requirement.

## Pros and cons of the options

### Option A — Status quo (single text node)

Good: no change; matches the two frozen clauses' literal wording. Bad: cannot express the third clause's data
shape at all; silently corrupts every block containing inline markup; the corruption is invisible to every existing
test and to the golden round trip by construction.

### Option B — Replace the block's entire inner content

Good: simple, matches `sourceInner`'s definition exactly, no second anchor component. Bad: a `<br/>`-delimited block
is one segment, so a 465,500-character `<div>` and a 1,850,851-character `<p>` become single segments — beyond any
model context, and the resulting write-back would replace thousands of `<br/>` elements the skeleton is supposed to
preserve.

### Option C — Replace a run's inner content (chosen)

Good: correct for ordinary blocks (one run) and for `<br/>`-delimited prose (many runs) with the same rule; the
`<br/>` elements themselves stay in the skeleton and never move. Bad: reintroduces a second anchor component so soon
after deleting `childIndex`, which invites the assumption that nothing was learned — the distinction is that
`childIndex` addressed *a node* and `runIndex` addresses *a segment's extent*.

### Option D — Rebuild the block from a re-parsed `targetInner`

Good: conceptually uniform — parse the translated content and swap the subtree. Bad: re-parsing translated content
and rebuilding the tree is precisely the regeneration FR-DOC-03 forbids, and it discards anything the round trip
depends on that a fragment parse cannot reproduce (entity spelling, comments, namespace prefixes). Rejected on the
same grounds `add-document-skeleton-and-epub-roundtrip`'s design.md D1 rejected re-parsing on write.

## Links

- Design decisions: DD-07 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`), DD-43 (canonical-equal round
  trip), DD-49 (code and math as non-translatable blocks)
- Spec clauses (deviated from, unedited): `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`
  (FR-DOC-03), `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#reassembly`,
  `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model`,
  `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#txt` (FR-DOC-TXT-3)
- Prior art: ADR-0003 (skeleton/segment model), `openspec/changes/archive/2026-08-07-add-document-skeleton-and-epub-roundtrip/design.md` D1–D3
- Consumed by: `openspec/changes/add-fb2-md-txt-roundtrip/`
- Rules: `.claude/rules/document-roundtrip.md`
