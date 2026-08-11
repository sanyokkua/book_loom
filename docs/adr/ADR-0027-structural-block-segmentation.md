# ADR-0027 — Recognise a translatable block structurally, not by a tag whitelist

**Status:** accepted **Date:** 2026-08-09
**Deciders:** architect
**Supersedes:** none

## Context and problem statement

`02_Architecture/03_DOCUMENT_MODEL.md#data-model` defines a `Segment` as "the translatable **inner content of a
single block-level element** (a paragraph, heading, list item, table cell, verse line, footnote body, caption, and
so on)". The list is illustrative and ends in "and so on"; the specification never says how an importer decides
whether a given element *is* a block-level element.

`add-document-skeleton-and-epub-roundtrip` resolved that silence with a tag whitelist. `BlockSegmentWalker.classify`
recognises `p`, `h1`–`h6` and `li`; anything else is descended into.

A survey of 194 real EPUBs — 6,957 content documents, 107.9 million visible characters — shows what that costs:

| Measurement | Value |
|---|---|
| Corpus text inside `p`/`h1`–`h6`/`li` | **73.74%** |
| Corpus text sitting *directly* inside a `div` | **25.35%** (167,326 such elements) |
| Books that would import at **under 1%** text coverage | **41** |
| Books that would import essentially empty | **42 of 194 (21.6%)** |

The cause is a single dominant pattern: Calibre's FB2→EPUB conversion emits `<div class="paragraph">` as the
paragraph container. Whole author sets in an ordinary library are affected — every Stephen King, Dan Brown,
Прозоров, Asimov and Глуховский title in the surveyed collection, plus Tolkien's *Hobbit*, Gaiman, Adams and Percy
Jackson. The worst cases:

| Book | coverage | `<p>` | `<div>` | visible text |
|---|---|---|---|---|
| `kotliarevskyy…eneida1052.epub` | 0.01% | **0** | 4 | 465,500 |
| `Стівен Кінг — Воно` | 0.44% | 84 | 12,968 | 2,589,099 |
| `Ден Браун — Янголи і демони` | 0.08% | 7 | 6,291 | 871,992 |
| `Александр Прозоров — 1. Темный лорд` | 0.05% | **2** | 3,651 | 648,048 |

**No fidelity test can detect any of this.** A book that yields zero segments round-trips perfectly: the skeleton is
untouched, every byte and every canonical form matches, and the golden round-trip test — the gate the whole document
capability is judged by — reports green while the product does nothing at all.

A naive hardening does not help either. *Аґата Крісті — 9. Вбивство у Східному Експресі* contains 1,393 `<p>`
elements of which **every one is an empty `<br/>` spacer**, with all prose in `div`s. A sanity check of "does this
book have paragraphs?" passes it.

## Decision drivers

- **A book that imports with no text is the worst possible failure**: silent, invisible to every existing gate, and
  discovered by the user only after configuring and starting a translation run.
- **The rule must not depend on the producing tool's tag taste.** Calibre chose `div`; the next converter will
  choose something else, and a whitelist fails the same way again, just as silently.
- **Do not double-count.** `div` is also the most common pure wrapper in the corpus (187,297 across 191 books), so
  "treat `div` as a block" naively would emit a segment for a wrapper and again for its contents.
- **Keep the deliberate exclusions intact.** `<pre>`/`<pre><code>` listings and block-level MathML must continue to
  yield no segments (DD-49).

## Considered options

- **Option A — Status quo.** Whitelist `p`, `h1`–`h6`, `li`.
- **Option B — Extend the whitelist** with `div`, `td`, `th`, `dd`, `dt`, `caption`, `figcaption`, `blockquote`.
- **Option C — Structural rule:** an element is segment-bearing when it owns direct non-whitespace text, and the
  segment is emitted at the **innermost** such element.
- **Option D — Keep the whitelist and add a zero-segment guard** that refuses a book yielding no segments.

## Decision outcome

Chosen: **Option C.** An element is segment-bearing when it **owns direct non-whitespace text content** — text that
is its own child node, not text belonging to a descendant — and the segment is emitted at the **innermost** element
satisfying that test, so a wrapper that contains only other elements is descended into rather than segmented.

Measured effect: corpus text coverage rises from **73.74% to 99.99%**, and the worst individual book moves from
0.01% to 97.9% — the residue there being `<pre>`, excluded on purpose.

The element's **tag name no longer decides whether it is a segment; it decides only the segment's kind**:
`h1`–`h6` → `HEADING`, `li` → `LIST_ITEM`, `td`/`th` → `TABLE_CELL`, FB2 `v` → `VERSE_LINE`, FB2 `subtitle`/`title`
→ `HEADING`, everything else → `PARAGRAPH`.

Three rules ride alongside it, each measured against the same corpus:

- **No text, no segment.** A block whose content is empty after markup is stripped yields nothing — 18,886 such
  blocks (3.64%) across 163 of 194 books, plus 1,950 image-only `<p>` in a single FB2.
- **Exclusions are unchanged and take precedence**: `<pre>` (and its subtree), block-level `<math>`, Markdown
  fenced and indented code blocks, and Markdown raw-HTML blocks yield no segments however much text they own.
- **Markdown segments leaf blocks only** — `Paragraph`, `Heading`, `TableCell`, never `ListItem`, `BulletList`,
  `BlockQuote` or `TableBlock` — because a container's source span can enclose a code block, and replacing the
  container's byte range would destroy it.

**A text-coverage assertion becomes part of the gate.** Each format's golden test asserts that emitted segments
cover at least a stated proportion of the fixture's visible text, excluding deliberately-excluded blocks. This is
the only assertion in the suite that measures whether the importer *does* anything; every other document assertion
measures that nothing was damaged, which a zero-segment import satisfies trivially.

### Consequences

Positive:

- 42 measured books stop importing empty; corpus coverage becomes 99.99%.
- The rule is **smaller** than the whitelist it replaces — one structural test instead of a tag list — and cannot be
  defeated by a converter choosing an unanticipated tag.
- The coverage assertion closes a blind spot in the gate itself, not merely in one parser.
- The same rule serves all four formats, so `document.model`'s walker stays format-agnostic (seam F1).

Negative:

- **A silent behaviour change for EPUB**, which is already shipped and archived. Books that previously produced few
  or no segments now produce many. Nothing regresses — no previously-emitted segment disappears — but segment ids
  and counts change for any book containing text-bearing non-whitelisted elements.
- The innermost-element rule needs care with mixed content: an element owning *both* direct text and block-level
  children is rare but real, and the walker must not emit a segment that overlaps a descendant's.
- A coverage threshold is a number someone must choose and maintain per fixture; set too low it proves nothing, set
  too high it fails on a fixture with legitimately excluded content.

Neutral:

- The `<br/>`-run segmentation decided in ADR-0025 composes with this rule but is independent of it: 13,281 of the
  corpus's 24,347 `<br/>` elements sit *inside* the `div` wrappers this ADR makes visible, so `<br/>` splitting only
  becomes reachable once structural recognition lands.
- Verification against a real-book corpus is a **local, manual** step. Those books are copyrighted and never enter
  the repository, so no test, fixture or build step may reference them; the committed fixtures are hand-authored to
  reproduce the shapes the corpus revealed.

## Pros and cons of the options

### Option A — Tag whitelist (status quo)

Good: explicit and easy to read. Bad: measurably empties 21.6% of a real library, invisibly, with the gate reporting
green.

### Option B — Extended whitelist

Good: reaches roughly the same coverage on this corpus with a change a reviewer can eyeball. Bad: it is a list to
maintain, and it is silent by construction the next time a producer picks a tag that is not on it — which is exactly
how the current defect arose. It also does not answer the wrapper-`div` double-counting question, which still needs
the innermost-element rule.

### Option C — Structural rule (chosen)

Good: 99.99% coverage; smaller than the rule it replaces; producer-independent; one rule for four formats. Bad:
requires the innermost-element refinement to avoid double-counting wrappers, and it changes shipped EPUB behaviour.

### Option D — Whitelist plus zero-segment guard

Good: smallest possible change; closes the invisible-failure hole. Bad: it converts 42 working books into 42 refused
books rather than translating them, and it does not even catch the near-miss cases — *Стівен Кінг — Воно* yields 84
segments out of 12,968 paragraphs, so the guard passes while 99.56% of the book goes untranslated.

## Links

- Design decisions: DD-07 (skeleton + segment model), DD-49 (code and math as non-translatable blocks)
- Spec clauses (silence resolved, unedited): `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model`,
  `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc` (FR-DOC-01),
  `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content` (the exclusions)
- Related: ADR-0025 (reassembly model, including `<br/>` runs)
- Consumed by: `openspec/changes/add-fb2-md-txt-roundtrip/`
- Rules: `.claude/rules/document-roundtrip.md`, `.claude/rules/testing.md`
