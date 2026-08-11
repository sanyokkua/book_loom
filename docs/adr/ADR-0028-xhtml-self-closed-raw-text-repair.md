# ADR-0028 — jsoup remains the EPUB XHTML parser, qualified by one pre-parse normalization

**Status:** accepted **Date:** 2026-08-11
**Deciders:** architect
**Supersedes:** none

## Context and problem statement

`add-fb2-md-txt-roundtrip`'s `design.md` D4 chose jsoup for EPUB content documents and gave one decisive reason:
jsoup resolves named HTML entities with **no DTD fetch**, and 53 of 194 surveyed books use `&nbsp;` under an
XHTML 1.1 DOCTYPE. An XML parser cannot resolve those without loading a DTD, and loading one over the network
would break the offline invariant (`.claude/rules/offline-and-privacy.md`).

A verification sweep over 214 real books
(`docs/implementation_plan/notes-corpus-verification.md`) then found that the chosen parser silently destroys a
class of real book. **Twelve of 194 EPUBs import with zero segments, no error, and nothing for the user to
translate.**

The cause is a single tag. `<script src="js/book.js"/>` is valid XHTML and is **unrepresentable in HTML**, where
a `<script>` element ends only at a literal `</script>`. jsoup's HTML tokeniser enters script-data state on the
start tag and leaves it only on that literal end tag, so everything after it — including the whole `<body>` —
is consumed as script text.

Measured on the pinned jsoup 1.23.1, `body.children()` for a document whose `<head>` carries the tag:

| in `<head>` | body children |
|---|---|
| baseline | 2 |
| `<script src="…"/>` | **0** |
| `<style/>` | **0** |
| `<noscript/>` | **0** |
| `<title/>` | 2 — unaffected |
| `<textarea/>` | 3 — unaffected |

All twelve share one converter fingerprint (Apple Pages/iBooks: `<script src="js/book.js"/>` + `EPB-UUID` +
`Body_onLoad`). A thirteenth shape, `<a id="…" data-type="indexterm"/>`, is the same root cause on a non-void
element: jsoup ignores the self-close, the anchor stays open, absorbs the following heading, and its `id` appears
four times in the output against once in the source.

**No fidelity test can see any of this.** A book that yields zero segments round-trips perfectly — the skeleton
is untouched, every canonical form matches, and the golden round-trip test reports green.

## Decision drivers

- **The entity-resolution argument that chose jsoup is still true**, and nothing may make the app fetch a DTD.
- **A silent zero-segment import is the worst failure mode available** — invisible to every existing gate.
- **The parse path must not depend on document content.** A book that silently moves between two parsers with
  different DOM shapes and different entity handling is a defect generator, not a fix.
- **Do not repair what is not broken.** 21 surveyed books carry a self-closed `<title/>` and are healthy.

## Considered options

- **Option A — Parse as XML, fall back to jsoup on failure.**
- **Option B — Replace jsoup with an XML parser outright.**
- **Option C — Keep jsoup, normalize self-closed elements in the byte stream before parsing.**
- **Option D — Accept the defect and refuse a book that yields zero segments.**

## Decision outcome

Chosen: **Option C.** jsoup remains the EPUB XHTML parser, exactly as `design.md` D4 decided and for exactly the
reason it gave. `XhtmlParser` now performs **one normalization before handing a content document to jsoup**:
an element written in XML self-closing form whose HTML content model is not empty is rewritten to an explicit
open/close pair. A **void** element (`img`, `br`, `hr`, `meta`, …) is left self-closed, because the output must
remain well-formed XHTML.

Two consequences are recorded here because they are not obvious from the decision:

- **The comparator normalizes both sides identically.** `EpubCanonicalAssert` re-parses source and output with
  jsoup, so without the same normalization the source side is still swallowed and the two never compare equal.
  A comparator taught to ignore a difference can no longer catch it, so `GoldenComparisonMetaTest` carries a case
  proving it still fails on a genuinely dropped element.
- **The write becomes a fixed point.** `Tag.SeenSelfClose` is a flag on the shared per-document `Tag` object, not
  on an element, so one `<p/>` anywhere in a document made **every** empty `<p>` serialize as `<p />` — which the
  next read does not treat as closed. Removing self-closing non-void forms before the tokeniser ever sees them
  removes the cause; clearing the flag on non-void tags after parsing guarantees it independently.

## Consequences

### Measured outcome

Verified against the 194-EPUB local corpus with production's own normalization and serialization, no comparator
involved:

| measurement | before | after |
|---|---|---|
| books importing with zero segments | **12** | **0** |
| books carrying a self-closed known non-void element | 37 | 37 (repaired) |
| books whose zero-edit write is not a fixed point | **9** | **0** |
| content documents checked for the fixed point | — | 6,975 |

The nine non-fixed-point books resolve in two disjoint steps: the self-close repair and the `SeenSelfClose` clear
take them to one, and that last book — `Alices Adventures in Wonderland.epub` — is the `<pre>` leading-line-feed
defect, a different mechanism with its own fix. No book is accounted for by both, and none is left over.

**The blast radius is far wider than the defect.** `<title/>` is a known non-void tag appearing self-closed 1,192
times across the corpus, so it is rewritten in 21 books that were never broken. That is safe — `<title/>` was
measured to parse correctly either way, and `<title></title>` is canonical-equal under DD-43 — but 37 books' output
bytes change to fix 3 books' structure, and a reader comparing bytes should expect that.

Positive:

- Twelve measured books stop importing empty, and one stops duplicating an index anchor's `id` four times.
- Every book that works today stays on exactly the path it is on today — the normalization is independent of
  document content and of which parser would have been chosen.
- The entity-resolution argument for jsoup survives intact; no DTD is ever fetched.
- The change is one function, testable in isolation.

Negative:

- **Byte manipulation before parsing is inherently risky.** It is confined to self-closing form, and fixtures
  assert that a document already using the paired form is unaffected and that the literal text `<script/>` inside
  a `<pre><code>` listing is not rewritten — that literal must be escaped to be well-formed XHTML, which is
  precisely what keeps it invisible to the rewrite.
- A source holding `<script …/>` now yields a skeleton holding `<script …></script>`. Permitted under DD-43
  (canonical-equal, not byte-equal), but it is a real difference in the output bytes.

Neutral:

- The repaired tag set is the **measured** set, not the HTML raw-text category. `title` and `textarea` are
  excluded because they demonstrably parse correctly; repairing them would be inert but would misstate the
  mechanism to the next reader.

## Pros and cons of the options

### Option A — XML first, jsoup on failure

Good: the obvious objection — that 53 books need `&nbsp;` resolved without a DTD — turns out **not** to apply to
the affected books, which use only `&amp;`, a predefined XML entity. So it would work. Bad: it changes the parser
for all 194 EPUBs to fix 12; it makes the parse path depend on document content, so a book can silently move
between two parsers with different DOM shapes; and the fallback path itself needs gating and testing as
thoroughly as the primary.

### Option B — Replace jsoup outright

Good: one parser, no repair. Bad: it breaks the offline invariant for the 53 books that need a DTD to resolve
`&nbsp;`, which is the reason D4 chose jsoup in the first place.

### Option C — Pre-parse normalization (chosen)

Good: one function; content-independent; leaves every working book untouched; preserves D4's argument. Bad:
byte manipulation before parsing, mitigated by confining it to self-closing form and by two negative fixtures.

### Option D — Refuse a zero-segment book

Good: the invisible failure becomes visible. Bad: it converts twelve translatable books into twelve refused ones
and fixes nothing; and it does not address the `<a/>` shape, which yields plenty of segments while corrupting
the document.

## Links

- Revises: `openspec/changes/archive/2026-08-09-add-fb2-md-txt-roundtrip/design.md` D4 (qualified, not reversed)
- Design decisions: DD-43 (canonical-equal round trip), DD-49 (code and math preserved via the skeleton only)
- Related: ADR-0025 (reassembly replaces a run's inner content), ADR-0027 (structural block segmentation)
- Evidence: `docs/implementation_plan/notes-corpus-verification.md` — findings F1, F12a, F12b
- Consumed by: `openspec/changes/fix-document-round-trip-corpus-defects/`
- Rules: `.claude/rules/document-roundtrip.md`, `.claude/rules/offline-and-privacy.md`
