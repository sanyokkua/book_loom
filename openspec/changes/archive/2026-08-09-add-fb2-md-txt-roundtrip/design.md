# Design — add-fb2-md-txt-roundtrip

## Context

See `proposal.md` — Why. Settled elsewhere and not re-argued here: canonical-equal round trips and their three
per-format forms (**DD-43**, ADR-0003), export in the original format only (ADR-0004, DD-30), the skeleton/segment
model (**DD-07**), code and math as non-translatable blocks (**DD-49**), records-for-data (ADR-0014), and change 3's
`design.md` D1–D7. Three decisions this change *takes* are large enough to live outside it: **ADR-0025** (the
reassembly model), **ADR-0026** (DRM adjudication), **ADR-0027** (structural block recognition). This file cites
them; it does not restate their arguments.

What shapes the rest of the design is that **EPUB was the easy case for the model and the hard case for the
container, and the three formats arriving here invert that.** The container work is trivial or absent; the
difficulty is that each carries fidelity in a different place, and the seam F1 records were designed against a
format where none of those places existed.

| Fact | Where | Consequence |
|---|---|---|
| A segment's anchor addresses a *tree* | `:api`, change 3 D3 | TXT's skeleton is a byte buffer and Markdown adopts the same model — D2 |
| `Document` carries no charset and no BOM flag | `:api`, change 3 | EPUB never needed either; FB2 and TXT stake the round trip on both — D5 |
| `DocumentService` holds an `EpubReader`/`EpubWriter` field pair | `:document` | There is no dispatch to extend; `01_MODULE_INVENTORY.md#module-document` already names "format dispatch" as this class's job — D3 |
| Every non-enum top-level type in `..api..` must be a record | ArchUnit rule 7 `records-first` | The rule exempts interfaces (`ArchitectureRules.java`, `.areNotInterfaces()`), so a sealed interface with record implementations is legal — D2 |
| `:document` may depend only on `:api` and `:util` | ArchUnit rule 2 | Charset detection cannot borrow `:pipeline`'s ICU4J; the dependency is declared on `:document` — D5 |
| 53 books rely on XHTML named entities under a DOCTYPE | measured corpus | The parser must resolve `&nbsp;` **without fetching a DTD** — D4 |

## Goals / Non-Goals

**Goals:**

- Make all four formats populate **the same** seam F1 model, so masking, the pipeline and export need no format
  branch beyond the reader/writer pair.
- Fix the equality function per format **before** writing the parsers, so "canonical-equal" is a specific,
  defensible thing rather than whatever the test happens to compare.
- Make byte-exactness a property of the *mechanism* (splice from an untouched original) rather than of careful
  re-serialization, for the two formats where that is available.
- Close the gate's blind spot: add the one assertion that measures whether the importer produces anything at all.

**Non-Goals** (design-level; scope-level ones are `proposal.md`):

- **No general refactor of the EPUB path.** `EpubReader`/`EpubWriter` change exactly where ADR-0025, ADR-0026,
  ADR-0027, the `Document` shape and the zip fixes reach them. A "unify the four readers behind one abstraction"
  pass is a fifth thing to get right in a change that already has four.
- **No durable open-document state.** Each format's registry is in-memory, as `OpenEpubRegistry` is today.
- **No segments inside a Markdown raw-HTML block.** A CommonMark `HtmlBlock` is one literal node; addressing the
  prose inside it needs a second parser nested in the first. Deferred to change 5 (`CHANGE_BACKLOG.md`).
- **No dependency on the real-book corpus.** `.temporary_context/` is git-ignored and must stay unreferenced by any
  test, resource path or build step — see D8.

## Decisions

### D1 — Format is resolved by extension, confirmed by content, and never by trial parsing

`open(Path)` maps the file name's extension to a candidate `BookFormat` — `.epub`, `.fb2`, `.fb2.zip`, `.md` /
`.markdown`, `.txt` — then confirms it cheaply: a zip local-file-header magic (`PK\x03\x04`) for `.epub` and
`.fb2.zip`, an XML prolog with a `FictionBook` root for `.fb2`. Anything else — an unrecognised extension, a
`.txt.zip`, a *directory* whose name ends `.fb2` — is refused as `ErrorCode.validation` before a parser runs.

*Alternative considered:* sniff content only. **Cannot work.** Every valid TXT file is also valid Markdown; nothing
in the bytes separates them. The extension is not a hint, it is the only signal.

*Alternative considered:* try each reader and keep whichever succeeds. **Worse than it looks.** Markdown never fails
to parse, so a corrupt EPUB would fall through and import a "book" whose text is the base64 of a zip. A failure that
produces a plausible wrong answer beats no answer only in the wrong direction.

The corpus supplied both odd cases directly: a `.txt.zip` that contains images and `.url` shortcuts alongside the
text (and is not a format FR-IMPORT-01 lists), and a *folder* named `…royallib.com.fb2`.

### D2 — Two skeleton kinds, two anchor kinds — per ADR-0025

```
              SKELETON            ANCHOR                     REASSEMBLY
  EPUB        jsoup tree          NodeAnchor(path, run)      replace the run's inner content
  FB2         JDOM2 tree          NodeAnchor(path, run)      replace the run's inner content
  Markdown    byte buffer         ByteSpanAnchor(start,end)  copy original, splice the span
  TXT         byte buffer         ByteSpanAnchor(start,end)  copy original, splice the span
```

`SkeletonAnchor` becomes a `sealed interface` permitting the two records; consumers branch with an exhaustive
pattern-matching `switch`, so the compiler names every site if a fifth format arrives. `childIndex` is deleted and
`runIndex` added — the reasoning, including why that is not the same component returning, is ADR-0025.

**The invariant that keeps byte spans valid** is that reassembly copies from the *unmodified* original buffer into
fresh output in one ascending pass and never mutates the source. It is load-bearing and invisible: an "obvious"
refactor to a mutable builder, or writing spans out of order, breaks it only once two or more segments actually
carry target text — which no no-edit golden test exercises. It is therefore asserted directly by a two-write test
per buffer format.

### D3 — One reader/writer pair per format, dispatched by an exhaustive switch

`DocumentService` becomes what the inventory says it is — dispatch plus error classification — holding four
reader/writer pairs and switching on the resolved format (`open`) or on `Document.format()` (`write`, authoritative
because export is same-format-only). Each format package keeps its own package-private `@Singleton` open-document
registry, as `OpenEpubRegistry` does.

*Alternative considered:* a Guice `MapBinder<BookFormat, FormatHandler>`. **Rejected** — it turns "you added a
format and forgot to bind it" from a compile error into a `null` a user discovers. `BookFormat` is closed by
ADR-0004; an exhaustive switch over a closed enum makes the compiler the reviewer.

### D4 — The parser division extends as the specification divides it; two formats get no serializer at all

| Format | Parser | Role | Why |
|---|---|---|---|
| EPUB XHTML | **jsoup** | parse + serialize | Real-world XHTML is HTML-shaped. Critically, jsoup resolves named HTML entities **internally, with no DTD fetch** — and 53 corpus books use `&nbsp;` under an XHTML 1.1 DOCTYPE, so this is what keeps the offline invariant true. |
| EPUB OPF, FB2 | **JDOM2** (`SecureXml`) | parse + serialize | `03_DOCUMENT_MODEL.md#xml-round-trip-config` names JDOM2 for XML; change 3 D4 already chose it and named FB2 as the next consumer. External DTD/entity loading stays disabled. |
| Markdown | **commonmark-java** + GFM tables | **analysis only** | Used to locate each leaf block's byte span. Its Markdown *renderer* is never invoked — nothing is re-rendered. |
| TXT | **none** | — | A parser would be the bug. Segmentation is a blank-line scan over the buffer, recording spans. |

**Markdown segments leaf blocks only** — `Paragraph`, `Heading`, `TableCell`; never `ListItem`, `BulletList`,
`BlockQuote` or `TableBlock`. A container's source span can enclose a code block: the corpus's `00_INSTRUCTIONS.md`
has ordered list items holding a paragraph, then a 3-space-indented ` ```bash ` fence, then more prose. Replacing
the item's range would destroy the fence. The replacement range is additionally trimmed to the block's rendered text
so trailing whitespace survives — nine genuine hard line breaks (two trailing spaces) exist in the corpus, and six
of eight files do not end with a newline, both of which an AST re-render would silently change.

**Markdown frontmatter is split off before the parser sees it** — a leading `---`-delimited block is taken as raw
bytes and re-emitted verbatim ahead of the body. Simpler than the front-matter extension and stronger: verbatim is
unconditional. Because the split only ever examines the *first* line, a `---` appearing later — as a thematic break
(23 in the corpus) or inside a fenced block showing frontmatter as an example (2 files) — can never be mistaken for
it.

Those raw bytes are still **read** — a flat scan for the top-level `lang:` and `title:` keys populates
`Document.declaredLang` and the metadata map, exactly as FB2 reads `title-info` (FR-IMPORT-07, FR-IMPORT-03). Reading
and preserving are independent: the block is re-emitted byte-for-byte whatever the scan finds, and no key or value
becomes a segment. A **flat scan, not a YAML parser**, because the only two values needed are scalars at the top
level, and adding a YAML dependency to `:document` to read two strings would be the largest dependency in the module
serving the smallest purpose. A key that is absent, nested, or not a plain scalar simply yields nothing — absence is
representable, and inventing a value is the failure mode the requirement forbids.

**GFM tables is the only extension added.** Tables are block-level and appear in 7 of 8 corpus documents (36 tables,
325 rows), so they change segmentation. Strikethrough and autolink are inline: they sit *inside* a paragraph that is
already one segment, so adding them changes no block boundary — and the corpus contains zero of either.

### D5 — Charset resolves once, on a fixed ladder, and lands on `Document`

1. **BOM** — a BOM fixes the charset; its presence is recorded so it is re-emitted exactly as found.
2. **In-band declaration** — FB2's XML declaration; EPUB already does this per spine document.
3. **ICU charset detection** over the bytes.
4. **UTF-8** when detection is inconclusive.

`Document` gains `charset` and `hasBom`, both `@Nullable`. Container formats leave them null: an EPUB has no
document-level charset (each spine document declares its own) and `hasBom` has no referent there at all. Saying so
with null is honest, and matches how `declaredLang`/`detectedSourceLang` already behave.

**EC-FB2-2 — declaration contradicts the bytes — resolves toward refusal**, but the detection cannot be a decode
attempt. `windows-1252`, `iso-8859-1` and `koi8-r` accept **every** byte, so decoding never fails and the mojibake
is silent — which is exactly the case the frozen edge case describes ("Cyrillic text under an `iso-8859-1`
declaration"). The check therefore compares the declaration against ICU detection and refuses on a confident
disagreement. Refusal is chosen over honouring detection because a wrongly decoded book imports, segments and
translates without complaint, and the damage surfaces only in the finished file.

**TXT has no declaration to disagree with, so detection is the only signal — and it is unproven.** Against the three
real `windows-1251` files, `charset_normalizer` returned `cp1125` and `chardet` returned `Windows-1252` (confidence
0.096 / 0.098 / **0.637** — confidently wrong). Neither is ICU4J, which is a different and generally stronger
detector, and which could not be tested here. **The first task in the TXT group is a spike**: run ICU4J's
`CharsetDetector` against those three files and report charset and confidence. If ICU is right, "detect, record,
proceed" stands. If ICU is wrong too, the decision is retaken before the TXT reader is written rather than after.

### D6 — FB2's export encoding is decided over the actual serialized output

Serialize the reassembled tree, then attempt to encode it with the declared charset using a `CharsetEncoder` set to
`CodingErrorAction.REPORT`. Success → write it in the declared encoding with the original declaration intact.
Failure → write UTF-8 and rewrite the declaration.

Deciding on the real output rather than predicting from the target language is the point: a `windows-1251` book
translated into Ukrainian is fully representable until one em-dash or curly quote appears, and that character is
never the one anybody predicts.

Per `#round-trip-golden-requirement`, the encoding-switched fixture is excluded from the source-language golden and
asserted against a re-parsed canonical tree instead.

### D7 — Three canonical comparisons plus one coverage assertion

`EpubCanonicalAssert`'s Javadoc says it was written to be extended by this change. What is reused is its *shape* —
read, canonicalize per part, compare — not its function:

| Format | Comparison |
|---|---|
| **FB2** (`.fb2`) | Re-parsed and serialized through a fixed canonical XML writer, compared as strings — **plus** the declared encoding compared as a *value*, **plus** every `<binary>` element's text compared exactly. |
| **`.fb2.zip`** | The FB2 member as above; entry names and order as a list. |
| **Markdown** | Frontmatter byte-exact; the body re-parsed to a CommonMark AST and compared as a deterministic structural serialization. |
| **TXT** | **Exact bytes**, BOM and line endings included. |

Two of those carry an assertion plain canonicalization would miss, both deliberate. A canonical XML writer
normalizes the declaration away, so **EC-FB2-1 would be unprovable through it** — and the encoding must be compared
as a *value* (`utf-8`), not as declaration text, because one corpus file writes `<?xml version='1.0'
encoding='utf-8'?>` with single quotes while JDOM2 emits double quotes. A canonical writer is also free to re-wrap
long text content, which would silently rewrite a `<binary>` cover image, so that text is compared exactly.

**The Markdown AST comparison is a structural serialization, never `Node.equals`** — commonmark-java's nodes inherit
identity equality, so asserting equality on two ASTs compares two references and passes only when they are the same
object. A test written that way fails to fail.

**Every format's golden test additionally asserts text coverage** (ADR-0027): the proportion of the fixture's
visible text covered by emitted segments, excluding deliberately-excluded blocks. Every other document assertion
measures that nothing was *damaged*, which a book yielding zero segments satisfies perfectly. This is the only one
that measures that something *happened*.

### D8 — Fixtures are hand-authored and committed; the real corpus is local-only and unreferenced

Following change 3's D6 — never downloaded, because a copyrighted book committed to git is effectively permanent.
The survey did not change that; it changed what the fixtures must *contain*. Each is built to reproduce a shape the
corpus proved exists:

| Fixture | Reproduces |
|---|---|
| `div-paragraphs.epub` | `<div class="paragraph">` prose with zero `<p>` — the 42-book zero-segment case |
| `spacer-paragraphs.epub` | 1,393-style `<p>` that are all `<br/>` spacers with prose in divs — defeats "has `<p>` ⇒ fine" |
| `br-runs.epub` / `br-runs.fb2` | `<br/>`-delimited prose, text carried in tails — the run-splitting case |
| `inline-markup.epub` | Paragraphs wrapped `p > span > i` — the ADR-0025 write-back case, absent from every existing fixture |
| `non-font-encrypted.epub` | An `encryption.xml` naming a content document — the DRM refusal path, which the corpus has **no** example of |
| `font-obfuscated.epub` | `encryption.xml` naming fonts declared `application/x-font-otf`, algorithm `…enc#RC` |
| `stored-images.epub` | Deliberately STORED entries — the compression-method case |
| `primary.fb2` (`windows-1251`) | Declared non-UTF-8 encoding, `<binary>` cover, poem/stanza/`<v>`, a notes body, a table, CDATA, a comment, an entity, `<lang>` + `<src-lang>` |
| `encoding-switch.fb2` | Target text outside the declared charset (EC-FB2-1) |
| `primary.md` | Frontmatter, fenced block with info string, indented code, table with alignment, reference-link definition, raw-HTML block, nested lists, blockquote, hard line break, no trailing newline |
| `primary.txt` | BOM, CRLF, blank-line paragraphs, runs of 2+ blank lines, indentation, trailing whitespace |

**Five of these have no natural example** in a 194-book library and exist only because the survey proved the gap:
a non-font encrypted resource, a non-UTF-8 FB2, an FB2 notes body, an FB2 table, and FB2 CDATA/comments.

**Fixtures are Java builders writing real files into a `@TempDir`, never committed resources.** This is change 3's
mechanism (`EpubZipBuilder`, `PrimaryFixtureEpub`) and `modules/document/src/test/resources/` is deliberately never
created. A builder is reviewable where a committed `.epub` is an opaque blob, but the binding reason is that two of
these fixtures *are* byte sequences: a `windows-1251` FB2 and a BOM + CRLF + trailing-whitespace TXT. A committed
file of either kind is one editor save, one `core.autocrlf` setting or one Spotless run away from being silently
normalised into a fixture that no longer tests what it was written to test — and the failure mode is a *passing*
test. `getBytes(Charset.forName("windows-1251"))` and an explicit `"﻿…\r\n"` are immune by construction.

**One fixture per shape is not enough, because the shipped defects were interactions.** Every fixture in the table
above isolates a single pathology, and each of those pathologies passed in isolation while real books were corrupted:
`<br/>` splitting is unreachable until structural recognition lands (13,281 of 24,347 corpus `<br/>` sit inside the
very `div` wrappers ADR-0027 makes visible), and write-back only corrupts once a run and inline markup coincide. So
three **combination** fixtures stack them the way a book does, and every fixture — single or combined — is registered
in one `FixtureCatalog` carrying its *declared* expectations: segment count, kinds, coverage floor, comparison mode.
A single parameterized sweep drives the catalogue end to end through `DocumentPort` on a real file. The catalogue's
expectations are declared rather than derived for the anti-tautology reason: a catalogue that asked the walker how
many segments it produced would have agreed with the 73.74%-coverage walker too. The per-format named goldens stay
alongside it — the sweep tells you *which fixture* broke, the goldens tell you *which obligation*.

**The corpus itself never enters the build.** `.temporary_context/` is git-ignored; no test, fixture path, resource
directory or Gradle task may reference it. Verifying the built code against it is a **local, manual, optional** step
in the final task group, phrased so that skipping it changes nothing — a clean checkout and CI must build and pass
identically without it.

### D9 — Blocks are recognised structurally, and split on `<br/>` — per ADR-0027 and ADR-0025

An element is segment-bearing when it owns direct non-whitespace text; the segment is emitted at the **innermost**
such element. The tag name decides only the kind (`h1`–`h6` → `HEADING`, `li` → `LIST_ITEM`, `td`/`th` →
`TABLE_CELL`, FB2 `v` → `VERSE_LINE`, FB2 `subtitle`/`title` → `HEADING`, otherwise `PARAGRAPH`). A block with no
text after markup stripping yields nothing. Exclusions (`<pre>` and its subtree, block `<math>`, Markdown code and
raw-HTML blocks) take precedence over the text test.

Within a segment-bearing block, content is split on `<br/>` into runs, one segment per run. In FB2 the run text
lives in the **tails** of the `<br/>` elements, not in the block's `.text` — one corpus file holds 12 characters of
`.text` and 1,850,851 characters across 11,944 tails, so an implementation reading `element.text` alone would
extract 25 characters from a 3.2 MB book.

### D10 — What a unit is, per format

The frozen spec defines a unit only for EPUB ("spine order"). For the rest:

| Format | Units | `id` | `href` | `mediaType` |
|---|---|---|---|---|
| EPUB | one per spine document | the href (unchanged) | the href | from the manifest |
| FB2 | one per `<body>`, in document order | `{href}#{order}` | the source file name | `application/x-fictionbook+xml` |
| `.fb2.zip` | as FB2 | `{member}#{order}` | the member name | as FB2 |
| Markdown | one | the file name | the file name | `text/markdown` |
| TXT | one | the file name | the file name | `text/plain` |

FB2 unit ids carry the `#{order}` suffix because a book's two bodies share one href, while `Unit.id` seeds every
segment id (`{unitId}:{ordinal}`) and `segments.id` is a SQLite primary key. The suffix is positional rather than
taken from `body/@name`, because the attribute is optional and the corpus shows it absent far more often than
present.

### D11 — DRM adjudication moves after OPF parsing, and stays before anything else

ADR-0026 requires the manifest, so adjudication can no longer be the very first thing the reader does. Change 3's
D7 put it first specifically to make "no partial import" achievable. The guarantee is preserved differently: the OPF
is parsed into memory, adjudication runs **before any content document is read and before any `Document`, `Unit` or
`Segment` is constructed**, and a refusal returns before anything reaches a caller. Parsing an OPF is not a partial
import — no unit and no segment exists yet.

Cipher-reference URIs resolve against the **container root**; manifest hrefs against the **OPF directory**. These
are two separate, named resolutions, not one shared helper, because 102 of 194 corpus books have a non-root OPF and
using the wrong base mis-resolves silently.

### D12 — Zip entries keep their original compression method, and reading is bounded

`RawEntry` already records `entry.getMethod()`; the writer ignores it and DEFLATEs everything. 26 corpus books
deliberately STORE 2,578 already-compressed entries — one stores 1,950 — and DD-43 licenses recompression of
*previously-DEFLATED* entries, not the conversion of STORED into DEFLATED. The writer uses the recorded method.

Reading gains explicit caps on entry count, total uncompressed size and per-entry compression ratio, plus a Cp437
retry when UTF-8 entry-name decoding throws. The Cp437 case is **not** evidenced by the current EPUB corpus (no
entry name in 12,513 contains a non-ASCII byte) but was measured directly on real Russian `.zip` archives, where
`ZipInputStream` with the default charset throws `ZipException: invalid LOC header (bad entry name)` and a Cp437
retry reads every entry. It is a cheap robustness fix for `.fb2.zip`, not a corpus-proven requirement, and is
labelled as such.

**Both fixes therefore have to live where `.fb2.zip` can reach them.** `ZipEntryReader` and `RawEntry` are today
package-private in `ua.bookloom.document.epub`, and the FB2 reader may not import a non-exported package of another
format. They move to `ua.bookloom.document.model` — the package that already holds what all four formats share —
before the caps and the Cp437 retry are added, so one bounded reader serves both container formats. Leaving them in
`document.epub` would mean either a second, silently unbounded zip reader for FB2, or a robustness fix justified by
FB2 that FB2 cannot use; the move is what makes the requirement's "every zip container" clause true rather than
aspirational.

### D13 — Parse determinism is load-bearing because anchors are not persisted

`06_DATA_MODEL_SQLITE.md#tables` has no anchor column: on resume, anchors are recomputed by re-parsing the source.
That makes "the same bytes yield the same unit ids, segment ids and anchors" an invariant the resume path depends
on and that nothing currently states. It becomes a requirement here, with a covering test, because this change is
the one that multiplies the number of parsers it must hold for.

## Risks / Trade-offs

- **The structural block rule over-segments mixed content** → an element owning both direct text and block-level
  children is rare but real; a naive walk could emit a segment that overlaps a descendant's. Mitigated by the
  innermost-element rule and by the coverage assertion, which detects under-segmentation but not over-segmentation —
  so a dedicated test asserts that no two segments' extents overlap.
- **The coverage threshold is a number someone must maintain** → set too low it proves nothing; set too high it
  fails on a fixture with legitimately excluded content. Mitigated by stating the threshold per fixture with the
  excluded-block budget written next to it.
- **The Markdown round trip passes on the AST while the file is visibly mangled** → AST equality is deliberately
  blind to what a renderer normalizes. Patch-in-place makes that largely moot (nothing is re-rendered), but the
  blindness remains in the *assertion*. Mitigated by named trap tests — fence info string, reference-link
  definitions, table alignment, hard line breaks, absent trailing newline.
- **commonmark-java's `TableCell` source spans may not exist** → the corpus's most common structure is tables (325
  rows), and cell spans are the one unverified dependency of the whole Markdown design. Mitigated by making span
  verification the **first** Markdown task; the fallback is locating cells by unescaped-pipe scanning within the
  row's span (safe here: zero `\|` escapes, zero pipes inside table code spans).
- **TXT byte spans silently drift** → byte-exactness depends entirely on splicing from the original buffer in one
  ascending pass. Broken only when two or more segments carry target text, which no no-edit golden exercises.
  Mitigated by the two-write test named in D2.
- **ICU4J may detect no better than the Python detectors** → then TXT imports mojibake invisibly. Mitigated by
  making the spike the first TXT task, so the decision is retaken before anything is built on it.
- **The `Document` and `SkeletonAnchor` shape changes ripple** → both are `:api` records with changed canonical
  constructors. Contained today because every construction site is inside `:document` and its tests, and this is the
  last change for which that is true — Stage B′'s contract floor and Stage C's persistence both build against them.
- **ICU4J is large for one function** → roughly an order of magnitude bigger than every other library here, added
  for charset detection alone. Accepted: the frozen spec mandates ICU charset detection on the import path, it is
  already licence-cleared and already destined for `:pipeline` and `:ui`, and hand-rolled charset detection works on
  fixtures and fails on real books.
- **Three ADRs in one change is a lot of architectural motion** → each records a deviation the corpus forced, and
  each is small and single-purpose. The alternative — folding them into `design.md` — would silently re-specify
  frozen clauses, which `.claude/rules/spec-authoring.md` rejects outright.
