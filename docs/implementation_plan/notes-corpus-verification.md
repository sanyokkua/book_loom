# Corpus verification of the document round trip — consolidated findings

## Post-fix re-run — `fix-document-round-trip-corpus-defects`, 2026-08-11

Re-measured with the committed harness (`./gradlew :document:corpus`, `@Tag("corpus")`), which now replaces the
throwaway script this note originally described. 213 books; the 214th is the PDF, which refuses at format
resolution by extension and is not a book file.

| probe | before | after |
|---|---|---|
| open | 212 ok / 2 refused | **212 ok / 1 refused** (the PDF is no longer discovered; `Наймичка.epub` is genuinely corrupt) |
| **books yielding zero segments** | **12** | **0** |
| zero-edit identity | 201 pass / 10 fail / 1 write refused | **211 canonical-equal / 0 fail / 1 write refused** |
| fixed point (write twice) | not measured | **211 canonical-equal / 0 fail** |
| mutation marker-strip | 207 clean | **208 clean / 0 dirty** |
| idempotence | 209 of 212 | **211 of 211 written** |

**All twelve zero-segment books now produce real content and round-trip it.** Per-book, against the preserved
baseline: 959–9,066 segments each, every one canonical-equal on both the identity and the fixed-point probe. D6a's
trap — that these books passed identity *trivially* while empty and could newly fail once given content — did not
materialise.

**All ten baseline identity failures now pass**: the five Martin and two Rowling books (F12b), *Building
Microservices* (F12a), *Alice's Adventures in Wonderland* (F2/F12c) and `Chvarakoroliv.fb2` (F11).

The four remaining write refusals are all correct and all documented: three TXT books whose `windows-1251` charset
cannot represent the harness's `⟪T⟫` marker (`TxtWriter` refusing loudly is the behaviour this note already
confirmed as right), and `aliceDynamic.epub`, which has no `mimetype` entry and is recorded in `CHANGE_BACKLOG.md`.

**The report keys each book by its path relative to the corpus directory**, not by its bare file name, so a run
diffs row-for-row against a previous one — which is what D6a asks of it. Measured: **213 of 213 rows match the
preserved baseline's keys directly**, the only baseline row without a counterpart being the PDF, which is no longer
discovered. A bare file name would not do: the corpus is a tree and genuinely contains two different books sharing
one base name.

**Two honest artifacts of the harness, not product defects.** `rawBytesEqual` is false for 199 of 211 identity
writes and 34 of 211 fixed-point writes while `canonicalEqual` is true for all of them — a zip records a per-entry
timestamp and jsoup rewrites an XML prolog to a comment, both permitted under DD-43, which is exactly why the two
are recorded as separate fields. And the mutation probe's `tuplesMatchOpen` is false for every Markdown and TXT
book and true for every EPUB and FB2 one, because those two formats anchor by absolute byte span and the marker
lengthens the text ahead of every later segment; the load-bearing marker-strip check is clean for all 208.

**Status when this note was written.** Verification complete; **no fix had been made**. Every finding below was
evidence for a follow-up OpenSpec change against capability `document-round-trip`, not work already done. The
measurement harness was a throwaway (per change 4's own task 12.8 precedent) and had been deleted; `git status` was
clean and the gate was green. **Everything below is preserved as written**, because it is the record of what was
measured before any fix — the section above records what changed. `fix-document-round-trip-corpus-defects` has
since taken findings F1–F6, F11, F12a, F12b and F12c, and recorded F7–F10 in `CHANGE_BACKLOG.md`.

**Provenance.** Branch `feature/add-fb2-md-txt-roundtrip`, against 214 real books (194 EPUB, 8 MD,
7 FB2, 4 TXT, 1 PDF), 314 MB, git-ignored at `.temporary_context/Book_Examples/`.

**The run itself is preserved** at `.temporary_context/corpus-verification-baseline/` — the per-book
JSONL, a summary table, and the analysis script. Git-ignored, so it does not travel to another clone
and no third-party book titles enter the repository; where it is absent, the aggregate counts below
are the anchor. This is the diff target for the re-run that proves the fixes. Real books never
enter the repo (design.md D8), so every finding below carries a **minimal hand-authorable repro**
to be reproduced synthetically into a `@TempDir` fixture.

**The standing fixture rule applies to all of these.** Whichever change takes a defect must add a
synthetic fixture carrying that shape to `fixture/FixtureCatalog.java`. The coverage assertion
catches nothing the fixtures do not contain — which is exactly how a 73.74 %-coverage
implementation once looked correct.

## What was actually proven

The change's own prior corpus run only ever called `DocumentPort.open`. **It never wrote a single
book back out.** This run closes that: every book was opened, written back with zero edits,
reopened, then written again with **every segment's text replaced** and reopened once more.

Headline: **207 of 208 books that could be written survived a full text-replacement round trip
with byte-exact marker-strip equality** — the translated text came back through real inline
markup, unchanged, on 626,276 segments. The core claim of change 4 holds on real data.

| Probe | Result |
|---|---|
| T0 open | 212 ok / 2 refused (both refusals correct) |
| T1 zero-edit identity | 201 pass / 10 fail / 1 write refused |
| T2 M1 mutation (all books) | 208 written, 207 marker-strip clean |
| T4 idempotence (FR-IMPORT-08 on real books) | 209 ok / 3 not |
| T3 coverage | EPUB median 1.000, FB2 ≥0.996, TXT ≥0.901, MD ≥0.853 |
| T5 container caps | none fired; 5–12× headroom |

Baseline gate before any change: `110 actionable tasks: 110 executed`, `BUILD SUCCESSFUL in 1m 32s`.

---

## Findings, classified

Classification per the plan: **(a)** violates a change-4 requirement → fix on this branch;
**(b)** explicitly deferred to a later change → record only; **(c)** covered by no requirement →
needs an ADR or a backlog entry.

### (a) F1 — Self-closed `<script/>` collapses the whole book. 12 books, zero segments.

Strongest finding here; four independent routes agree on the same 12 books (corpus byte scan,
standalone jsoup repro, the sweep's `segmentCount: 0` + `coverage: 0.0000`, and a blind lens).

`XhtmlParser.java:45` parses with jsoup's HTML tree builder. `<script>` is a raw-text element:
the tokeniser enters script-data state on the start tag and leaves it only on a literal
`</script>`. The XML self-closing form never produces one, so the rest of the document — the
entire `<body>` — is consumed as script text. `body.children()` is 0, the walker has nothing to
walk, and the book opens "successfully" with nothing to translate and no diagnostic.

**Measured on the pinned jsoup 1.23.1** — and this corrects the mechanism the plan assumed:

| in `<head>` | `body.children()` |
|---|---|
| baseline | 2 |
| `<script/>` | **0** |
| `<style/>` | **0** |
| `<noscript/>` | **0** |
| `<title/>` | 2 — unaffected |
| `<textarea/>` | 3 — unaffected |

The predicate is **not** "HTML raw-text element". The plan proposed fixing
`script, style, textarea, title`; measurement says `title`/`textarea` are unaffected and
`noscript` — which is affected — was missing. 21 corpus books carry a self-closed `<title/>` and
are perfectly healthy, which the plan's stated mechanism would have predicted broken.

Also measured: a book with one self-closed **and** one paired `<script>` parses correctly, because
the later `</script>` terminates the swallow. The defect is not uniform even within one
converter's output.

All 12 share the fingerprint `<script src="js/book.js"/>` + `EPB-UUID` + `Body_onLoad` (Apple
Pages/iBooks): Conan Doyle 1–9, both Alice volumes, Švejk.

**Requirement violated:** FR-DOC-01 / ADR-0027 (a segment is emitted for every block owning direct
non-whitespace text — here no block is ever seen), and requirement 35 (coverage).

**On the two candidate fixes.** design.md D4 chose jsoup partly because it resolves named HTML
entities with no DTD fetch, and 53 corpus books use `&nbsp;` under an XHTML 1.1 DOCTYPE — which
looks fatal to the XML-first option. Measured, it is not: the affected books use only `&amp;`, a
predefined XML entity needing no DTD. So **both options remain live** and the choice belongs in
the follow-up change's `design.md` + ADR-0028, not in this note.

**Comparator consequence (unchanged from the plan, and confirmed).** Either fix leaves the
skeleton holding `<script …></script>` where the source held `<script …/>`. Permitted by DD-43,
but `EpubCanonicalAssert` re-parses **both** sides with jsoup, so the source side is still
swallowed and the two will not compare equal. The comparator must apply the same normalization to
both sides, and `GoldenComparisonMetaTest` must gain a case proving it still catches a real
difference afterwards — otherwise the fix is "verified" by a blinded comparator.

**Fixture owed:** an EPUB whose `<head>` holds `<script src="…"/>` with real prose in `<body>`.
Add a `<style/>` and `<noscript/>` case too, since those are proven broken and absent from the
corpus.

### (a) F2 — A zero-edit write deletes a newline from inside `<pre>`. 2 books.

HTML's parser rule drops one U+000A after a `<pre>` start tag; the serialization spec's
counterpart says to prepend one back when the first child text begins with U+000A. **jsoup 1.23.1
does the parse half and not the serialize half**, so the pair is not a round trip.

Measured on `Alices Adventures in Wonderland.epub`, `chapter06.xhtml`:

| step | first text node of `pre.poem` |
|---|---|
| source | `<pre class="poem">` + `\n\n        “Speak…` (two newlines) |
| `parse(source)` | `"\n        “Speak roughly "` |
| serialize → re-parse | `"        “Speak roughly t"` |
| serialize → re-parse | stable |

Converges after two cycles, having permanently deleted a line from inside a `<pre>`.

**Exact blast-radius rule:** one leading newline is safe (both sides strip it, canonical equality
holds); **two or more** is lossy. Corpus: 5 EPUBs contain a `<pre>`, **2** have one with 2+
leading newlines (`aliceDynamic.epub`, `Alices Adventures in Wonderland.epub`).

**Requirement violated:** FR-DOC-09/DD-43 (identity round trip must be canonical-equal), DD-49
(block code "preserved via the skeleton only"), ADR-0025 (write-back replaces only a run's inner
content — here a zero-edit write mutates a `<pre>` nobody translated).

**Fix shape:** prepend U+000A on serialization when a `pre`/`textarea`/`listing` element's first
child text begins with one — exactly what the HTML serialization spec mandates.

**No comparator change needed.** `EpubCanonicalAssert` caught this correctly; it is doing its job.
Fix the serializer, not the comparator.

**Fixture trap:** a fixture with only **one** leading newline passes whether or not the bug is
fixed. It must have two.

### (a) F3 — FB2 write throws `internal` on a bare `&` or `<` in translated text. 7 of 7 FB2 books.

Two lenses traced the same path independently. `SkeletonAnchors.writeBack` parses `targetInner`
**as markup**; for FB2 that goes through `Jdom2TreeNode.parseFragment` → `SecureXml`, a **strict**
XML parser (`Jdom2TreeNode.java:88-109`). A bare `&` or `<` is not well-formed, so `JDOMException`
→ `IllegalArgumentException("Translated content is not well-formed markup")`. That is not a
`CorruptContainerException`, so `DocumentService.write` misses its `validation` branch and the
generic `catch (Throwable)` classifies it **`internal`** (`DocumentService.java:93-94`).

EPUB is unaffected because `JsoupTreeNode` uses the lenient `Jsoup.parseBodyFragment`. The
asymmetry is the proof: 7/7 FB2 fail M3, 0/7 fail M1 or M2, 0 EPUB fail.

**Two separable questions — keep them apart:**

1. **The classification is wrong regardless, and that part is squarely (a).** `error-envelope.md`:
   "MUST classify every known failure to a specific `ErrorCode` at the point it is recognized."
   Malformed inline markup in translated content is a *known, nameable* failure — it belongs in
   `validation`, not the unexpected-throwable net. Fix here.
2. **Whether a bare `&` should be *accepted*** is arguably change 5's masking territory. Real
   translated prose does contain "Tom & Jerry" and "R&D", so something must handle it eventually —
   but the escaping policy is a design decision, not a bug fix. Record for change 5.

### (a) F4 — Three Russian TXT files resolve as `windows-1252` when they are `windows-1251`.

Silent mojibake, no exception — the dangerous failure mode:

```
windows-1252: 'Ñïàñèáî, ÷òî ñêà÷àëè êíèãó â áåñïëàòíîé ýëåêòðîííîé áèáëèîòåêå Royallib.com'
windows-1251: 'Спасибо, что скачали книгу в бесплатной электронной библиотеке Royallib.com'
```

ICU whole-file detection is swamped by a large ASCII/French majority.

**Blast radius, honestly bounded:** exactly 195 bytes in the 0xC0–0xFF range per file, confined to
the Royallib attribution header and footer. The French novel body is 7-bit ASCII with accents as
HTML numeric entities (`&#224;`), and the remaining non-ASCII bytes are smart punctuation that
occupies the same code points in both code pages. **The book's prose is unaffected; the
site-boilerplate at head and tail is corrupted** — and that boilerplate is still segmented and
would still be translated and exported.

Small, but it is a real charset-ladder defect and it is exactly the "decodes without error, wrong
answer" case the ladder exists to prevent.

### (a) F5 — `TextCoverage` cannot measure two of the four formats correctly.

This one matters more than its size, because it is the **canary for requirement 35**.

Both confirmed by reading the source:

```java
case MARKDOWN, TXT -> sourceInner;                      // numerator keeps raw **bold**/`code`
case TXT -> readText(source, StandardCharsets.UTF_8);   // denominator ignores resolved charset
```

- **TXT:** hardcoded UTF-8 decode of the denominator while the numerator is correctly decoded with
  the ladder-resolved charset. On one real book that is 22,298 U+FFFD replacement characters — a
  fabricated ~10% coverage loss with production behaving perfectly.
- **Markdown:** EPUB and FB2 numerators are parsed to strip markup; MD/TXT are not, so
  `` `code` `` in a segment never matches the AST's clean `code` in the denominator. Estimated
  ~15-point understatement on markup-dense files.

**Why this is (a), not a test-tidiness nit.** Declared floors are `MARKDOWN 0.90`, `FB2 0.95`,
`EPUB 0.99`, `TXT 0.99`. Corpus Markdown lands 0.853–0.956 — straddling its own floor. The MD
floor was therefore calibrated against a metric that structurally understates, so the gate cannot
distinguish "markup-boundary noise" from "segmentation lost 10% of the prose." A real Markdown
regression passes.

design.md D8 warned about exactly this shape in a different guise — *"the failure mode is a
passing test."* D8 worried about fixture bytes being normalised; this is the measurement itself
being miscalibrated. Requirement 35 asks the goldens to *prove* coverage; a metric that cannot
measure two formats does not prove it.

### (a) F6 — `OpfParser` silently drops title, author and language for a legacy `<dc-metadata>` OPF.

`OpfParser.readTexts` (`OpfParser.java:107-116`) reads only **direct** children of `<metadata>`.
`aliceDynamic.epub` nests them one level deeper in an OEBPS-1.2-era `<dc-metadata>` wrapper —
verified by reading the OPF: `<dc:title>Alice's Adventures in Wonderland</dc:title>`,
`<dc:creator>Lewis Carroll</dc:creator>`, `<dc:language …>en-GB</dc:language>` all present and all
reported null. Requirement 34 obliges populating metadata from each format's own source.

### (c) F7 — The reader accepts EPUBs the writer can never export.

`grep -c -i mimetype`: **`EpubReader` 0, `EpubWriter` 9.** The reader imposes no mimetype
requirement at all; the writer requires one because DD-43 mandates mimetype-first-and-STORED on
output. `aliceDynamic.epub` has 68 entries and none named `mimetype`, so it opens with 13 units
and 836 segments and then refuses every write — including a zero-edit one — with
`ErrorCode.validation`.

The refusal itself is *correct*: the writer cannot fabricate a conformant container. The defect is
the **asymmetry** — the app accepts a book for translation that it can never deliver, and the user
discovers this only after translating. No requirement covers reader/writer acceptance parity;
this needs a decision (fail fast at open, or synthesize a mimetype on write).

### (c) F8 — No `close()` or eviction anywhere in `:document`.

Two lenses converged from different directions. All four `Open*Registry` classes are `@Singleton`
over a bare `ConcurrentHashMap` with no `remove`/`evict`/`close`, and `DocumentPort` exposes only
`open` and `write`. Per open EPUB the registry retains every zip entry's **inflated** bytes plus a
full jsoup DOM per spine document, for the process lifetime.

Inert today (`:pipeline` and `:persistence` are stubs), but the sharper framing is that **every
completed translation job will leak its book**, not merely multi-book browsing. Cheapest to add
the seam before `:pipeline` has callers. No requirement covers document lifetime.

### (c) F9 — `EpubWriter` mutates the registry-held tree in place.

`EpubWriter.writeSegmentsBack:78-88` mutates `parsed.spineTreesByHandleId()` — the live tree in the
registry, not a copy — so a second `write()` on one document id works from the already-mutated
tree. Latent in production; no concurrent-open/write test exists either way. **It was not latent
for this effort: it invalidated the first harness design.** Advisory note for `:pipeline`'s retry
path.

### (c) F10 — Oversized segments are a real downstream risk, correctly out of scope here.

One segment of 26,306 characters (Pullman); 15 books over 5,000. Verified as single genuine
unbroken `<p>` elements — splitting them would violate the skeleton invariant, so `:document` is
behaving correctly. The spec already anticipates this (`02_GLOSSARY.md`: "oversized single
paragraphs are sentence-split only on overflow", FR-ALGO-02/DD-44) but `:pipeline` is a stub, so
the safety net does not exist. Against the settings' 512-token minimum `num_ctx`, that one segment
overflows by 15–20×. Backlog item for the chunker, with this corpus as the proof case.

### (b) Deferred — record only, do not fix

- `detectedSourceLang` null everywhere — explicitly out of scope (`add-metadata-units-and-language-detection`).
- No `METADATA_TITLE`/`METADATA_AUTHOR`/`FRONTMATTER_VALUE` segments — explicitly deferred.
- No masking; `masked == sourceInner`, `placeholders` empty — explicitly deferred to change 5.
- `SegmentKinds` classifies by tag name only, so EPUB `<p class="v">` verse (Кобзар: 1,876
  segments, all `PARAGRAPH`) is not `VERSE_LINE`. This is exactly what **ADR-0027 mandates** — a
  design limit, not a violation. Text is still segmented and translates correctly.
- `Цисінь Лю` declares three `dc:language` values (`en-US`, `ru-RU`, `uk-UA`); the reader takes the
  first, as its Javadoc promises, while the body is Ukrainian. Source-data problem; relevant to
  language detection, not to this change.

### Correct behaviour, explicitly confirmed (worth as much as the defects)

- **Both open refusals are right.** The PDF refuses at format resolution by extension — the right
  reason, not an accident downstream. `Наймичка.epub` is genuinely corrupt: its OPF spine declares
  18 chapters `ch3..ch20` but the archive ships **only the 9 odd-numbered pages**.
- **All 30 `encryption.xml` EPUBs open, and all are genuinely font-only** — 99 `CipherReference`s
  across two algorithm URIs, every one resolving to a font media type. Matches ADR-0026's own
  cited survey exactly. No non-font encrypted resource anywhere.
- **TXT export refusal on unrepresentable characters is correct** — `TxtWriter` fails loudly rather
  than silently switching encoding, exactly as documented. The 3 windows-1252 books refusing M1/M2
  is conformant behaviour, not a defect.
- **Container caps are well set** — 5× headroom on entry count, 11.3× on uncompressed bytes, 12.3×
  on compression ratio, versus real corpus maxima. None fired.
- **Zero empty segments and zero duplicate ids across 626,276 segments** — verified from raw data,
  then shown *structurally* guaranteed by three separate blank-guards plus id construction.
- **No quadratic behaviour** in the walker. The two slow outliers (15s, 10s) are
  linear-with-a-large-constant on documents with ~9,000–19,000 direct children under one element —
  the shape `BlockRuns.java`'s own Javadoc already cites by name.
- **T4 idempotence holds on 209 of 212 real books**, extending FR-IMPORT-08 from fixtures to real
  data. The 3 exceptions are books already failing T1.
- **Performance is comfortable**: median 384ms for ~8 open/write operations per book; single open
  median 20ms, max 320ms.

---

### NOT a production defect — F11: the FB2 identity contract is self-contradictory for a real shape

`Chvarakoroliv.fb2` was the only FB2 failing T1. A hypothesis that this was run-recomputation
drift across its ~11,947 `<br/>` elements — which would have been serious content corruption —
was **REFUTED** by direct diagnosis.

The canonical diff is a single localized insertion at index 290, 24 characters, no cascade:

```
EXPECTED                                    ACTUAL
      <from>                                      <from>
        Конвертовано сайтом javalibre.com.ua        Конвертовано сайтом javalibre.com.ua
  <body>                                        <lang>
                                                  uk
                                              <body>
```

Canonical lengths 2,042,239 vs 2,042,263; the remaining ~2M characters are identical once that
one block is skipped. `SkeletonAnchors`/`BlockRuns`/`Jdom2TreeNode` are provably uninvolved — a
zero-edit write never reaches them, because no segment carries a `targetInner`.

**Mechanism.** `Fb2Writer.setTargetLanguage:96-108` runs on every write. When `title-info` has no
`<lang>`, the `existing == null` branch adds one. `Chvarakoroliv.fb2` is the only FB2 of seven
declaring no language, so `declaredLang` is null, the harness falls back to `"uk"`, and a node
appears that was never in the source.

**Adjudication — the writer is correct.** Requirement 22 obliges exactly this: "Replace/add
`<lang>` in title-info". The real finding is a **contradiction between two requirements**, visible
only on a shape no fixture contains:

- Requirement 22: always stamp the target language on export.
- Requirement 37: a zero-edit FB2 reassembly must be canonical-XML-equal.

For a source declaring no `<lang>`, both cannot hold. `primary.fb2` carries both `<lang>` and
`<src-lang>` (design.md D8's table), so the identity golden has only ever exercised the
replace-branch, where it is a genuine no-op. The add-branch has never been round-tripped.

**Therefore: fix the test's contract and the fixture set, not `Fb2Writer`.** The FB2 identity
golden must state that the language declaration is stamped, so "identity" means "identical except
the target-language declaration".

**Fixture owed:** an FB2 whose `title-info` omits `<lang>`. No scale needed — a single-paragraph
book reproduces it:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
  <description><title-info>
      <genre>prose</genre><book-title>Sample</book-title>
  </title-info></description>
  <body><section><p>Hello world.</p></section></body>
</FictionBook>
```

This is the standing fixture rule doing its job: real books found a shape the hand-authored
fixtures do not contain, and the gap was invisible until they did.

### F12 — The 10 T1 failures are THREE mechanisms, not one

A fidelity lens reported all of these as one cluster ("self-closed `<p/>`/`<a/>`"). Adversarial
refutation on the real book bytes split it into three, and found a 9th EPUB the lens had omitted
from its own list. All `firstDiffIndex` values reproduced exactly against the sweep.

**(a) F12a — `Building Microservices`: genuine, single-pass, content-corrupting.** Real defect.
Its `afterword01.html` genuinely contains `<a data-primary="…" data-type="indexterm"
id="idm45112632171480"/>`. jsoup ignores the self-close on a non-void element, so the anchor stays
open and absorbs `</p></div></section>`, several blank lines, and the whole next
`<section><div><h1>Moving to Microservices</h1>`. **The id appears 4× in the output against 1× in
the source, and an `<h1>` ends up inside an anchor.** Confirmed single-pass — production's own
write shows it with no comparator involved. Same root cause as F1 (self-close ignored on non-void
elements), different element and different blast radius.

The related `t2.M1.diffCount=3302 / 5398` and `t4.diffCount=3307` are real node-path cascades
(`node:[0,0,3,1,0,0]` → `node:[0,0,3,1,1,0]`), but they are evidenced on `preface01.html`, a
*different* entry from the one T1 flagged — that file has 6 self-closed indexterm anchors. One
inserted sibling shifts every later index in the unit.

**(a) F12b — 5 Martin + 2 Rowling books: the production write is not idempotent.** The
originating lens's attribution was **wrong** — the source contains no self-closed `<p/>`/`<a/>`
near the failure at all. The real trigger is `<p>` illegally wrapping `<div>`:

```html
<p class="p1"><div class="image"><img class="z1" src="images/i_003.jpg"/></div></p>
```

Legal XML, invalid HTML5. jsoup auto-closes the `<p>` before the `<div>`, and the stray `</p>`
becomes a phantom empty paragraph.

The refuter concluded this was "manufactured by a redundant second parse inside the test's own
comparator" and recommended comparing against a single-parse source. **That conclusion is wrong
and the recommendation would have hidden a real defect.** Measured using only production's
parse+serialize, with no comparator in the picture:

```
out1: ...<p /><div>x</div><p />\n</div><p>\n</p>...
out2: ...<p /><div>x</div><p>\n</p></div><p>\n</p>...
write idempotent (out1 == out2)?  false      out2 == out3?  true
```

**The write is not a fixed point.** Writing a book, then writing the result again, produces
different bytes; it stabilises only on the second pass. The comparator is doing its job. Same
family as F2: jsoup's parse and serialize are not inverse for empty non-void elements.

Severity is low — `body().text()` is identical, no content is lost — but ADR-0025 and
`document-roundtrip.md` require that the skeleton is never regenerated, and a zero-edit write that
changes structure violates that.

**The bigger prize, which the refutation undersold: a blind spot in the method itself.**
`<p><div>x</div></p>` un-nests to `<p /><div>x</div><p />` on the **first** parse — a real
structural change against the source. It appears identically on both sides of the comparator, so
it cancels and the comparator cannot see it. A re-parse-based canonical comparison (DD-43) is
structurally incapable of detecting any change the parser applies uniformly to both sides. That is
a limit of the verification method, worth recording as a known gap rather than a defect to fix.

**(a) F12c — `Alices Adventures in Wonderland`: the `<pre>` newline (F2).** Unrelated to
self-closing tags; the originating lens omitted it from its own list of failures. Independently
reproduced here at `firstDiffIndex 8445`.

## Nothing remains open

Every T1/T2/T4 failure in the sweep now has a mechanism, and every mechanism has either a minimal
hand-authorable repro or an explicit statement of what could not be determined.

## If you run a corpus sweep again, read this first

Five measurement errors were caught during this run, each of which would have produced a
confident wrong conclusion. They are cheap to repeat, so they are recorded here rather than lost.

1. **A Gradle run can report `BUILD SUCCESSFUL` having executed nothing.** Environment variables
   are not task inputs, so flipping `BOOKLOOM_CORPUS_DIR` left `:document:test` `up-to-date`.
   Use `--rerun-tasks`, and verify by the **executed/up-to-date split** and by the report file's
   existence — never by the exit code. This is the same shape as the `build-logic` canary trap
   AGENTS.md already documents, in a second place.
2. **The test JVM's working directory is the module, not the repo root.** A relative
   `BOOKLOOM_CORPUS_DIR` throws `NoSuchFileException`. Pass an absolute path.
3. **Never let a probe change the output file name.** For Markdown, TXT and FB2 the unit href *is*
   the file name and segment ids derive from it, so writing to `t2-M1.md` renames every segment and
   makes id lookups miss wholesale. It reported `stripMismatchCount` of 11947/11947 on FB2 and
   758/758 on Markdown — total failure on books whose segmentation is perfectly stable. Give each
   probe its own **directory** and keep the original name. The tell was an asymmetry: EPUB (ids from
   internal spine hrefs) looked believable while three other formats looked catastrophic.
4. **Diff the canonical forms, not the raw bytes.** jsoup rewrites an XML prolog to a comment, so a
   raw-byte diff reports `firstDiffIndex: 1` on nearly every EPUB — a permitted DD-43 difference
   that buries the real one. Record `rawBytesEqual` and `canonicalEqual` separately.
   `Fb2CanonicalAssert.xmlOf` returns **raw** XML; `canonicalize` is a different method.
5. **`EpubWriter` mutates the registry-held tree in place**, so a probe must never reuse another
   probe's registry entry — open a fresh `DocumentService` **and** a fresh document per write probe,
   or each mutation compounds the last.

## Instrument errors caught (full detail)

Five, three of them mine. Every one would have produced a confident, wrong conclusion; each was
caught by checking a result against something the instrument did not produce. The worst was a
filename-derived segment-id collision that reported `stripMismatchCount` of 11947/11947 on FB2 and
758/758 on Markdown — a total, unanimous failure on books whose segmentation is in fact perfectly
stable. The tell was an **asymmetry**: EPUB looked believable while three other formats looked
catastrophic.
