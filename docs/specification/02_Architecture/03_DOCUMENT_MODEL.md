**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-09-27
**Cross-references:** `docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`, `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`

# Document Model

The document model realizes the `skeleton + segments` invariant (`01_SYSTEM_ARCHITECTURE.md#skeleton-and-segments`,
DD-07, DD-43). It is owned by `:document` and exposed through `DocumentPort`. This document fixes the data shape, the
masking/unmasking contract, reassembly and repackaging, the XML round-trip configuration requirements, and the golden
round-trip test.

## data-model {#data-model}

A parsed book is a `Document` composed of ordered `Unit`s; each `Unit` carries an immutable `skeleton` (its DOM/AST) and
contributes an ordered list of `Segment`s.

**What a `Segment` is (the "text node" definition).** A `Segment` is the translatable **inner content of a single
block-level element** (a paragraph, heading, list item, table cell, verse line, footnote body, caption, and so on).
Everything inside that block — all **inline descendant elements and their tails** — is masked to `⟦gN⟧` placeholders
(`#inline-masking`); the model sees clean block-level prose interleaved with placeholders. **Not every block yields a
segment**: `<pre>`/`<pre><code>` code listings (and block-level MathML `<math>`) are **non-translatable blocks**
(DD-49) — they produce no `Segment`, are excluded from chunking and the token budget, and are preserved verbatim by the
immutable skeleton alone. Admonition/sidebar blocks (`note`/`tip`/`warning`/`sidebar`), by contrast, are ordinary
translatable blocks. The skeleton keeps the block elements themselves and all structural/block-level whitespace;
`.text`/`.tail` preservation (`#xml-round-trip-config`) is therefore about **block-level / structural whitespace only**,
not about inline runs (those live inside the segment as masked spans).

```
Document
 ├─ id, format(EPUB|FB2|MD|TXT), detectedSourceLang, declaredLang, metadata(cover, title, author…)
 └─ Unit[] (spine order, plus one trailing auxiliary unit — see #metadata-unit)
      ├─ id, order, href/name, mediaType
      ├─ skeleton : Dom          // full tree: tags, attrs, images, fonts, IDs, comments, CDATA, entities, encoding
      └─ Segment[] (document order)
```

`Segment` (record in `:api`, persisted per `06_DATA_MODEL_SQLITE.md`):

| Field                 | Meaning                                                                                                                                                                                                                                      |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`                  | stable segment id (`{unitId}:{ordinal}`)                                                                                                                                                                                                     |
| `unit`                | owning unit id                                                                                                                                                                                                                               |
| `order`               | position within the unit (document order)                                                                                                                                                                                                    |
| `kind`                | body kinds `PARAGRAPH \| HEADING \| VERSE_LINE \| LIST_ITEM \| TABLE_CELL \| FOOTNOTE \| CAPTION \| TITLE`, plus **metadata-unit kinds** `METADATA_TITLE \| METADATA_AUTHOR \| METADATA_DESCRIPTION \| FRONTMATTER_VALUE \| ALT \| NAV_LABEL` (see `#metadata-unit`) |
| `sourceInner`         | the raw inner content of the block element, inline descendants still present                                                                                                                                                                 |
| `masked`              | `sourceInner` with inline descendants/inline code and math/locked terms/URLs replaced by `⟦gN⟧` placeholders — no numeral is ever masked (owner decision D-7)                                                                              |
| `placeholders`        | ordered map `⟦gN⟧ → original fragment` (inline run, atomic code/math span, locked term, or URL)                                                                                                                                             |
| `declaredLanguage`     | the `xml:lang`/`lang` of the segment's nearest ancestor-or-self element below the document root, or `null` when none is declared — a declaration only, never a comparison; the pipeline compares it against the brief's source language to decide the foreign-keep policy (`design.md` D11) |
| `sourceHash`          | **SHA-256 over the exact `sourceInner`** (pre-mask, **NFC-normalized**) — cache/TM key and per-segment change-detection                                                                                                                      |
| `prevKey` / `nextKey` | segment ids of document-order neighbours (context + TM neighbour match)                                                                                                                                                                      |
| `targetInner`         | translated inner content after unmask (null until translated)                                                                                                                                                                                |
| `status`              | `PENDING → ACCEPTED \| FLAGGED → REVISED` (state machine below)                                                                                                                                                                              |
| `confidence`          | QA/judge confidence in `[0,1]`                                                                                                                                                                                                               |

Each **placeholder pair** (an open/close token bounding inline markup, `⟦g1⟧…⟦g2⟧`) additionally records the declared
language of the element it came from, alongside its pairing and nesting recorded at mask time (`design.md` D10/D11) —
this is what lets a kept foreign run be recognized and restored verbatim even though it sits inside a source-language
segment.

The skeleton holds a **placeholder anchor** for each segment so the target can be written back to exactly the node the
source came from. The anchor is a **stable node path/id plus child index** into the skeleton tree (for example an
element id, or a root-to-node index path, with the child index of the target text node) — **not** a byte offset and
**not** an inserted sentinel node. Anchors are computed at parse time and never mutate the skeleton, which is immutable
after parse.

A second, document-scoped **content hash** (SHA-256 over the whole imported source) is kept separately for **resume /
change-detection** at the document level (feeds `FR-IMPORT-08` and `05_RELIABILITY_AND_RESUME.md`); it is distinct from
the per-segment `sourceHash`.

### metadata-unit {#metadata-unit}

Every opened book has exactly one synthetic **auxiliary unit**, always produced whatever the Book-Brief "Also
translate" switches say (`design.md` D12): the **last** unit in `Document.units()` — body unit order and positions are
unchanged — carrying an ordered list of segments whose `kind` is one of `METADATA_TITLE`, `METADATA_AUTHOR`,
`METADATA_DESCRIPTION`, `FRONTMATTER_VALUE`, `ALT`, or `NAV_LABEL` (DD-47). Each is a normal `Segment` (same masking,
hashing, status machine, and placeholder-anchor mechanism), with a **per-slot segment id** (never a single running
counter, so adding one slot kind never renumbers another), and its anchor points at a **non-body node**:

| Kind                                  | Anchored to                                                                                 | Governed by (translated when on) |
|---------------------------------------|-----------------------------------------------------------------------------------------------|-----------------------------------|
| `METADATA_TITLE` / `METADATA_AUTHOR`  | OPF `dc:title` / `dc:creator` (EPUB) or `title-info/book-title` / `title-info/author` (FB2)   | "book metadata title/author" toggle |
| `METADATA_DESCRIPTION`                | OPF `dc:description` (EPUB) or `title-info/annotation` (FB2)                                  | "book metadata title/author" toggle (the metadata switch) |
| `FRONTMATTER_VALUE`                   | a Markdown frontmatter value node that is a single-line, untyped text scalar (never a key, never `lang`/`language`) | "frontmatter values" toggle |
| `ALT`                                 | an image `alt` (or caption) attribute, anchored by the element's path plus the attribute name (ADR-0041) | "image alt-text" toggle |
| `NAV_LABEL`                           | EPUB 3 nav-document link text or EPUB 2 NCX `navLabel/text`                                   | "ToC/navigation labels" toggle |
| `TITLE` (auxiliary)                   | an XHTML document's `<head><title>`                                                            | "ToC/navigation labels" toggle (the navigation switch) |

Because `NAV_LABEL` segments come from the nav-document / NCX, those resources are **carved out of the "out-of-spine
resources are verbatim" rule** (`01_Product/03_DOCUMENT_FORMATS.md#epub`, EC-EPUB-3): their labels are translatable
while their structure, `playOrder`, and href targets are preserved. An EPUB 3 book that also ships a **legacy NCX**
produces `NAV_LABEL` segments for **both** resources, and identical labels must render identically in both (DD-47,
`01_Product/03_DOCUMENT_FORMATS.md#epub` FR-DOC-EPUB-8). When a switch is off, the pipeline **skips** producing a
segment of the governed kind and exports the corresponding text **as source**, unchanged. The Book-Brief "Also
translate" toggle group that drives these switches is defined in `01_Product/01_FUNCTIONAL_REQUIREMENTS.md`
(FR-BRIEF-09, FR-DOC-11).

## inline-masking {#inline-masking}

Before a segment is sent to the model, inline structure is removed so the model sees clean prose and cannot corrupt
markup:

- The following are replaced with opaque placeholders `⟦g0⟧`, `⟦g1⟧`, … in first-appearance order: **inline descendant
  elements and their tails** (`<em>`, `<a href>`, `<sup>`, `<span>`, verse `<v>` inners, footnote refs); **inline code**
  (`<code>`, Markdown code spans) and **inline MathML `<math>`**, each masked as a single **atomic** placeholder whose
  content is kept exactly (DD-49); and locked glossary terms and URLs. Index-term/cross-reference anchors are masked as
  a **paired-markup placeholder group** (`⟦gN⟧…⟦gM⟧` around the link text): the anchor's ids and href target live in the
  masked fragments and are restored unchanged, while the **visible link text between the pair stays translatable**
  (DD-49). Inline code and math, by contrast, are **atomic** — a single placeholder swallows the whole span, so nothing
  inside is ever exposed to the model.
- **Protected spans** — locked glossary terms that carry a target, and, under the Keep foreign-passage policy, kept
  foreign runs (an inline element whose own declared language differs from the run's source language) — are masked
  **after** the document's own inline-markup/code/math/URL tokens above, numbered from one above the segment's highest
  token, and are **restored before** `DocumentPort.unmask` runs, so the document's own placeholder gate never has to
  reason about them (`design.md` D10). A kept foreign run is known **only from the element's own markup** — never
  detected from unmarked text.
- **No numeral is masked** — every numeral, standalone or in running prose, stays in the segment text so it can
  inflect and localize (owner decision D-7). Placeholder-multiset validation (`#unmask-and-validate`) covers only the
  categories that ARE masked; there is no separate number-preservation QA check.
- The mapping is stored in `Segment.placeholders`. Only inline content is masked; block structure lives in the skeleton
  and is never sent.

**Placeholder token scheme (normative):**

- **Grammar.** A placeholder token matches the regex `⟦g\d+⟧` (U+27E6 MATHEMATICAL LEFT WHITE SQUARE BRACKET, `g`, one
  or more ASCII digits, U+27E7). During unmask, tokens are matched **longest-first** by index width so that `⟦g1⟧` is
  never mis-matched as a prefix of `⟦g12⟧`.
- **Escape rule.** If the source `sourceInner` already contains the bracket character `⟦`, each pre-existing occurrence
  is escaped before masking (so it cannot be read as a placeholder token) and un-escaped on unmask; the round trip
  restores the original character exactly. Atomic protected spans (inline code/math, DD-49) are captured into the
  placeholder map **before** token scanning, so bracket characters *inside* them need no escaping and can never be
  misread as placeholder tokens (EC-CODE-2 in `01_Product/03_DOCUMENT_FORMATS.md#code-edge-cases`).
- **Uniqueness validation (mask time).** Placeholder indices are assigned densely from `0` in first-appearance order; at
  mask time the engine asserts every emitted `⟦gN⟧` token is **unique** within the segment and that the `placeholders`
  map is a bijection over the tokens actually present in `masked`. A violation is a parser-side invariant failure, not a
  model error.

## unmask-and-validate {#unmask-and-validate}

After the model returns `targetInner`:

1. **Unmask** — substitute each `⟦gN⟧` back to its original fragment.
2. **Tag-multiset hard gate** — the multiset of placeholders present in the model output MUST equal the multiset in
   `masked`. A missing, duplicated, or invented placeholder is a **hard failure**: the segment cannot be accepted and is
   routed to self-heal (`05_PIPELINE_ENGINE.md#tiered-loop`). This is a deterministic pre-condition, not a quality
   heuristic — it runs before any QA scoring.
3. **Pair order, nesting, and the two pair rules (ADR-0040 as amended).** Beyond the multiset, a paired placeholder
   group (`⟦gN⟧…⟦gM⟧`) must keep its recorded order and nesting relative to every other pair, and two further rules
   checked from the pairing recorded at mask time each fail as a hard gate: a pair whose source content held text (a
   non-whitespace character outside any token) must hold text in the target too, and a line-break token's innermost
   enclosing pair must be the same pair in source and target. A target whose words all sit inside pairs while the
   source left text outside them fails too: a tree reader makes the outermost element owning text its block, so the
   written paragraph would re-open as a segment of that inline element, its markup gone (a drop cap's `<span>` wrapped
   round a whole paragraph). A `⟦` or `⟧` glyph outside every whole token (a split `⟦g1 ⟧`, a misspelt `⟦G1⟧`, a
   bracket left over from a moved token) fails first, and a pair wrapping at least 12 visible characters of a source
   with text outside it must wrap between a third and three times its source share of the target's visible text (the
   bold of a three-word name moved onto one word of a long sentence fails). A violation of any of these — like the multiset itself — cannot be accepted and is routed
   to self-heal.
5. **A separate deterministic repair, never inside the gate.** The gate only reports. A caller may ask the port to
   repair a refused target without a model — drop an invented or repeated token, put a missing one back at the
   boundary of the same kind nearest its scaled source position, or re-place every token by that rule, after rejoining
   a split or misspelt token and dropping any remaining stray bracket glyph — and must still
   pass the repaired text through the gate; the pipeline records the repair as a low `markup` finding.
4. **Atomic tokens may move.** Placeholder ordering is not otherwise required to match source order (translation may
   reorder), but every placeholder must appear exactly as many times as in the source, and an atomic protected-span
   token (inline code, math, a locked term, a kept foreign run) carries no internal structure for the model to disturb
   — only its position in the output, never its content, may change.

## reassembly {#reassembly}

For an accepted/revised segment, `targetInner` is written back into the exact text node the segment was parsed from,
using the skeleton's placeholder anchor. The skeleton is otherwise untouched — no re-serialization of unchanged nodes,
no reflow, no ID regeneration. Because only text nodes change, structure/images/fonts/IDs are preserved by construction.
The one closed exception is an **attribute value**: an `ALT` segment's restored text is written into its attribute
(never re-parsed as markup), so an attribute value round-trips through plain-text substitution and the writer's own
attribute-value encoding, not through the block-text reassembly path (`design.md` D12).

## repackaging {#repackaging}

Export re-emits the book in its **original format only** — EPUB→EPUB, FB2→FB2, MD→MD, TXT→TXT (DD-30, and the export
clause of ADR-0004). The output is rebuilt from the immutable skeleton, so it is **structure-and-text-preserving (
canonical-equal)** to the imported container apart from translated text nodes and the intentional language-metadata
update (DD-43, ADR-0003) — structure, IDs, images, fonts, and encoding are preserved, while a faithful re-serializer may
normalize entities, attribute quoting, insignificant whitespace, or ZIP recompression. Encoding is preserved **except**
under ADR-0029: a TXT or Markdown export whose resolved charset cannot hold a target character is instead written
whole as UTF-8, with the source's BOM presence kept — the one other intentional deviation alongside the
language-metadata update. The Markdown frontmatter `lang` value, where present, is part of that intentional
language-metadata update, not an accidental change: an existing top-level `lang` value is replaced by the target
language tag in its original quote style on export, `lang` is never added where absent, and `language` is left as
written. There is **no target-format choice** on the export path, and **converting between document formats is out of
scope** (it is lossy and cannot honour the skeleton invariant); `:document` never emits a format other than the one it
parsed.

Per format, `:document` re-emits the container:

- **EPUB** — rewrite only the spine XHTML text nodes, keep all resources; the `mimetype` entry is written **first and
  STORED (uncompressed)**, remaining entries DEFLATED; entry order preserved. Set the target language by **replacing the
  first `<dc:language>`** (adding one if none is present), leaving any additional entries untouched. Translate
  nav-document / NCX ToC labels when the toggle is on (`#metadata-unit`), preserving their structure and href targets.
- **FB2** — write the XML **keeping the original declared encoding when every target character is representable in it**;
  otherwise switch to **UTF-8 and rewrite the XML declaration** (`01_Product/03_DOCUMENT_FORMATS.md#fb2`, EC-FB2-1). If
  `.fb2.zip`, re-zip. Set the target language by **replacing the first `<lang>` in `title-info`** (adding one if none);
  **leave `src-title-info`'s `<lang>` untouched**.
- **Markdown** — re-emit from the CommonMark AST with source-faithful rendering (fenced blocks, link refs, tables
  intact); the round trip is asserted by **re-parse-equal AST**, not raw bytes. The resolved encoding and BOM presence
  are kept unless a target character is unrepresentable in it, in which case the whole document is written as UTF-8
  (ADR-0029).
- **TXT** — the skeleton is the **original byte buffer plus a list of segment byte-span slots**; export **splices** each
  target span back into its slot and leaves every other byte untouched, so a no-edit round trip is **byte-exact**
  (original line endings and charset preserved by construction), again subject to the same UTF-8 fallback when a target
  character cannot be represented in the resolved charset (ADR-0029).

## xml-round-trip-config {#xml-round-trip-config}

The XML reader/writer (JDOM2 or dom4j) MUST be configured to preserve:

- **Comments** and **processing instructions** (kept in the tree, never dropped).
- **CDATA** sections (kept as CDATA, not collapsed to text).
- **Entities / character references** — declared encoding preserved; do not eagerly expand or re-escape beyond what the
  source used.
- **XML declaration and encoding** — the exact declared encoding is echoed on output.
- **`.text` and `.tail` semantics (block-level only)** — leading/trailing whitespace **around and between
  block-level/structural elements** is preserved exactly (significant for verse and pre-formatted blocks). Inline runs
  are not handled here: they live inside a segment as masked `⟦gN⟧` spans (`#inline-masking`), so their whitespace
  round-trips through the placeholder mapping, not through `.text`/`.tail`.
- **Namespace prefixes** and attribute order as parsed, where the writer allows.

jsoup handles XHTML *bodies* only (inside EPUB items) with output settings pinned to `prettyPrint(false)` and the source
charset, so whitespace is not normalized.

## segment-status-machine {#segment-status-machine}

```
        translate + pass QA/judge
PENDING ─────────────────────────► ACCEPTED
   │                                   │
   │ fail after N repair tries         │ backward-revision re-render
   ▼                                   ▼
FLAGGED ───(manual/assisted edit)──► REVISED
```

`ACCEPTED` and `REVISED` are terminal-exportable; `FLAGGED` is exportable too (source or best-draft carried through) —
export is allowed at any time. Transitions are persisted atomically (`06_DATA_MODEL_SQLITE.md`).

## golden-round-trip-test {#golden-round-trip-test}

A release-gate test per format: parse a fixture book → reassemble **in the same format** with **zero segment edits** →
assert the output is **canonical-equal** to the input (DD-43, ADR-0003) — canonicalized/re-parse equality, not raw
bytes, because a faithful re-serializer may normalize entities, attribute quoting, or insignificant whitespace. Only
text nodes may change; the skeleton is never semantically regenerated. The comparison is per format:

- **EPUB** — entry-by-entry: text entries (OPF, XHTML, nav, NCX, CSS) by **decompressed canonical content**; **entry
  order preserved**; `mimetype` **first and STORED**; unchanged binary entries (images, fonts) by **decompressed bytes**
  (recompression differences allowed).
- **Markdown** — **re-parse-equal AST** (re-parsing the output yields a CommonMark AST equal to the source AST).
- **TXT** — **exact** (byte-for-byte), since export splices target spans into the original byte buffer.
- **FB2** — **canonical-XML equal** over the parsed tree; a fixture that legitimately switches encoding to UTF-8
  (EC-FB2-1) is **excluded from the source-language golden** and asserted against a re-parsed canonical tree instead.

Any structural, encoding-declaration, ID, or entry-order drift fails the build. Because export is same-format-only (see
`#repackaging`), this in-format round trip is the whole export guarantee — no cross-format conversion is produced or
tested. This test proves the skeleton path is faithful before any translation is layered on. Fixtures cover the edge
cases `EC-EPUB-*`, `EC-FB2-*`, `EC-MD-*`, `EC-VERSE-*`, `EC-INLINE-*`, `EC-CODE-*` (code/math/technical markup, DD-49),
`EC-IMG-*`, `EC-FONT-*`.
