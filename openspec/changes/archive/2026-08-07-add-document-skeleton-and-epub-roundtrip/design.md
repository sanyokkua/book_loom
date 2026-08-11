# Design — add-document-skeleton-and-epub-roundtrip

## Context

See `proposal.md` — Why. The decisions this change *consumes* are settled and are not re-argued here: canonical-equal
rather than byte-equal round trips (**DD-43**, ADR-0003), export in the original format only (ADR-0004, DD-30), the
skeleton/segment model itself (**DD-07**), code and math as non-translatable blocks (**DD-49**), and records-for-data
(ADR-0014, DD-05).

What shapes this design is that **the model has to be right before it has a second consumer, and it is about to get
four.** Seam F1 is consumed by masking, the pipeline, and export; `07_ROADMAP.md#forward-compatibility-seams` forbids
those later stages reshaping it. Anything provisional in the record shapes here becomes twenty-plus changes of churn.

Three facts about the tree constrain the shape:

| Fact | Where | Consequence |
|---|---|---|
| `:api` may not import a parser library | `ArchitectureRules` rule 6 `api-is-framework-free` | The document model in `:api` is parser-agnostic. No jsoup `Element`, no JDOM `Document` may appear in a record component — the skeleton crosses the boundary behind an opaque handle. |
| Every non-enum top-level type in `..api..` must be a record | `ArchitectureRules` rule 7 `records-first` | Learned the hard way in change 2, where a mutable builder was rejected. `Document`, `Unit`, `Segment` and the anchor are records; `DocumentPort` is an interface, which the rule exempts. |
| `:document` may depend only on `:api` and `:util` | `ArchitectureRules` rule 2 `dependency-direction` | Parsing cannot reach persistence for a hash, or the pipeline for a segment id. Both are computed here. |

## Goals / Non-Goals

**Goals:**

- Land seam F1 **complete in shape**, so masking and the pipeline extend it by filling fields rather than by changing
  the records.
- Make "the book survives" a **test**, not an intention — the golden round trip is the acceptance gate.
- Keep the skeleton genuinely immutable after parse, enforced by construction rather than by discipline.

**Non-Goals:**

- **No masking.** `⟦gN⟧` placeholders, locked terms, URLs and protected numerals are change 5. The fields exist; they
  stay empty. See D2.
- **No FB2, Markdown or TXT.** Change 4. The XML parser configuration this change establishes is what FB2 reuses.
- **No nav/NCX label translation.** FR-DOC-EPUB-8 and the `NAV_LABEL` metadata-unit kind belong to
  `add-metadata-units-and-language-detection` — the unnumbered interstitial that runs after change 5 and closes
  Stage B (ADR-0023) — which carves the nav document and the NCX out of the "out-of-spine = verbatim" rule so their
  ToC labels become segments. Nav and NCX are carried verbatim here, as ordinary out-of-spine resources.
- **No language detection, no UI, no persistence.** `Document.detectedSourceLang` is populated by
  `add-metadata-units-and-language-detection` (Lingua, in `:document/ua.bookloom.document.detect`), not here. The
  document content hash is computed and carried; nothing stores it yet.
- **No translation.** Every segment's `targetInner` is null throughout, which is exactly what makes the golden round
  trip a *no-edit* round trip.

## Decisions

### D1 — The skeleton crosses the `:api` boundary as an opaque handle

`api-is-framework-free` forbids a parser type in `:api`, and the rule is right: a jsoup `Element` in a record
component would put jsoup on the compile classpath of all eight modules, and `:ui` would be able to reach into a
book's DOM.

So `Unit` carries a `SkeletonHandle` — an opaque identity that `:document` can resolve back to its own parsed tree and
that nobody else can do anything with. The tree itself never leaves `:document`.

*Alternative considered:* model the skeleton structurally in `:api` (a parser-agnostic node tree). Rejected — it is a
second document model that must be kept faithful to the first, and every fidelity bug becomes a bug in the
translation between them. The spec's own wording is `skeleton : Dom`, a thing the document module owns.

*Alternative considered:* let `Unit` carry the serialized source text and re-parse on write. Rejected — re-parsing is
the regeneration FR-DOC-03 forbids, and it would make write-back cost O(document) per segment.

### D2 — `masked` and `placeholders` ship in the shape, empty

`Segment` carries all the fields `03_DOCUMENT_MODEL.md#data-model` lists, including the two this change cannot
populate. `masked` is initialized **equal to `sourceInner`** and `placeholders` to an empty map — the honest
representation of "no masking has been applied", and one that lets a downstream consumer read `masked` unconditionally
rather than branching on whether masking has run yet.

The alternative — omit them now, add them in change 5 — would change the record's shape after four changes had already
compiled against it. That is the churn seam F1 exists to prevent, and it is the same reasoning that put all fifteen
`ErrorCode` constants in change 2 rather than adding them as producers appeared.

**Consequence to accept:** for three changes, `masked == sourceInner` is trivially true and a reader may wonder why
both exist. The field comments say so explicitly.

### D3 — Anchors are a root-to-node index path plus a child index

`SkeletonAnchor(List<Integer> nodePath, int childIndex)` — the path from the unit's root to the block element, then
the index of the text node within it. The spec permits an element id instead, and this design deliberately does not
use one: **EC-EPUB-4 requires that duplicate ids be preserved**, so ids are not unique and cannot be an addressing
scheme. A book with two `id="note1"` elements would resolve both segments to the same node.

Index paths are stable under the only mutation this system performs — replacing a text node's content — because that
changes no node's position among its siblings. They are *not* stable under structural edits, which is precisely why
FR-DOC-03 forbids those.

*Alternative considered:* a byte offset into the serialized source. Rejected outright by
`03_DOCUMENT_MODEL.md#data-model`, and rightly: the first write-back of a longer translation invalidates every later
offset.

### D4 — Two parsers, divided the way the specification divides them

**jsoup for XHTML content bodies; JDOM2 for XML** (`META-INF/container.xml`, the OPF, and — in the next change — FB2).
This is not a preference: `03_DOCUMENT_MODEL.md#xml-round-trip-config` names JDOM2/dom4j for XML and says explicitly
"jsoup handles XHTML *bodies* only (inside EPUB items)".

The division is real rather than bureaucratic. XHTML in the wild is HTML-shaped — unclosed tags, HTML entities,
implied elements — and an XML parser rejects it. The OPF, by contrast, is strict XML whose comments, CDATA, entity
spelling and encoding declaration all have to survive, and jsoup's XML mode does not preserve them faithfully enough
for a canonical-equal round trip.

jsoup output settings are pinned to `prettyPrint(false)` and the source charset, per the same clause: pretty-printing
normalizes whitespace, and whitespace between block elements is significant in verse.

*Alternative considered:* jsoup alone, in XML mode, for everything. Rejected — it is one dependency instead of two,
but it trades a dependency for a fidelity risk in exactly the file (the OPF) whose corruption invalidates the whole
book.

### D5 — The golden comparison is defined here, once, and reused by three later changes

"Canonical-equal" has to be a specific function or the test proves nothing. For EPUB it is, per entry:

| Entry kind | Comparison |
|---|---|
| `mimetype` | present, **first**, **STORED**, content exactly `application/epub+zip` |
| XHTML, OPF, NCX, nav, CSS | decompressed, re-parsed, serialized with a fixed canonical writer, compared as strings |
| Images, fonts, other binaries | decompressed **bytes**, compared exactly |
| All entries | **order** compared as a list |

Re-parsing before comparing is what makes it *canonical* rather than textual: it absorbs entity spelling, attribute
quoting and insignificant whitespace, which DD-43 explicitly permits to differ, while still catching a lost element, a
rewritten id or a dropped comment.

Compression level is deliberately **not** compared. A re-zip at a different level changes every byte of a DEFLATED
entry and harms no reader.

This harness is written to be format-agnostic where it can be, because change 4 needs the same shape for FB2
(canonical-XML equal), Markdown (re-parse-equal AST) and TXT (**exact bytes** — the one format where byte equality is
the right assertion, since export splices target spans into the original buffer).

### D6 — One hand-authored fixture, built to trip every trap

The fixture EPUB is **authored for this repository**, not downloaded. A real book cannot be committed — copyright
aside, a large binary in git is its own problem — and a fixture that only exercises the happy path would pass while
the round trip was broken.

It is built to contain, deliberately: a `mimetype` entry first and stored; two `dc:language` entries (so D-language
replacement can be shown to touch only the first); **duplicate element ids** (EC-EPUB-4); a `<pre><code>` listing
(DD-49); an out-of-spine stylesheet (EC-EPUB-3); an embedded font (FR-DOC-EPUB-4); an XML comment between block
elements; a spine whose declared order differs from zip entry order; and an entity reference that a re-serializer
might respell.

Additional single-purpose fixtures cover the refusal paths — a content-encrypted `encryption.xml`, a
font-obfuscation-only one, a missing OPF, and a file that is not a zip at all.

### D7 — DRM inspection happens before anything is parsed

`META-INF/encryption.xml` is read and adjudicated **first**, before the OPF, before the spine, before any content
document. EC-EPUB-1 requires "no partial import", and the only way to guarantee that is to decide before any work
that could partially succeed.

Adjudication is an **allowlist**, not a denylist: the two known IDPF font-obfuscation URIs are permitted and
everything else — including an algorithm nobody has seen before — refuses the book. A denylist would silently import
whatever encryption scheme ships next.

## Risks / Trade-offs

- **The golden test passes while the round trip is broken, because the fixture is too easy** → The whole change is
  gated on one test, and a fixture without duplicate ids, without a stored `mimetype` to lose, and without an entity
  to respell would pass against a parser that got all three wrong. Mitigated by D6 making the fixture adversarial by
  construction, and by asserting the traps individually as well as through the round trip — so a fixture that stops
  covering one of them fails a named test rather than silently weakening the gate.
- **Canonicalization is written loosely enough to absorb a real defect** → The comparison exists to ignore
  differences that do not matter, and every widening of it narrows what the test can catch. A canonical writer that
  normalized attribute *order*, for instance, would hide a parser that reordered attributes. Mitigated by D5 fixing
  the comparison per entry kind, and by comparing binaries as raw decompressed bytes where no normalization is
  defensible.
- **jsoup silently repairs the XHTML it parses** → It is an HTML parser; repairing malformed markup is its purpose,
  and a repaired document is a *changed* document. This is the most likely source of a canonical-equal failure that
  looks like a test bug and is not. Mitigated by pinning output settings and by having the fixture contain markup that
  a repairing parser would be tempted to normalize.
- **The `masked == sourceInner` initialization is mistaken for real masking** → A later change could read `masked`,
  find plausible content, and conclude masking had run. Mitigated by `placeholders` being empty and by the field
  comment, but the real fix arrives in change 5 when the field is populated for the first time.
- **The fixture's licence is not checked** → A downloaded EPUB committed as a test resource would put someone else's
  copyrighted text in the repository permanently, and git makes that hard to undo. Mitigated by D6: the fixture is
  authored here, and the task text says so rather than leaving it to judgement.
