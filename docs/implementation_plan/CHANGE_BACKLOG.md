**Status:** Final **Owner:** architect **Audience:** anyone picking up the next unit of work **Last Updated:**
2026-09-22 **Cross-references:** `docs/adr/ADR-0017-infrastructure-first-delivery-order.md`,
`docs/adr/ADR-0023-backend-complete-milestone-and-backlog-interstitials.md`,
`docs/adr/ADR-0016-openspec-delivery-tracking.md`, `docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`,
`docs/adr/ADR-0030-synthesize-a-missing-epub-mimetype-entry-on-write.md`, `docs/implementation_plan/07_ROADMAP.md`,
`docs/implementation_plan/notes-corpus-verification.md`,
`openspec/config.yaml`

# Change Backlog

The ordered list of OpenSpec changes that build BookLoom, grouped into the five stages of ADR-0017.

> **This is names and order only — deliberately unplanned.** No entry below has a proposal, specs, or tasks yet, and
> none should be written ahead of time. Each change gets its real artifacts from `/opsx:propose` **when its turn
> comes**, against the codebase as it actually is at that moment rather than as it was imagined months earlier. The
> "covers" column names the material a proposal should draw on; it is not a scope contract.

## where-this-stands {#where-this-stands}

**Eight changes are authored and archived** (`openspec/changes/archive/`). **Stage A is complete and Stage B is three
of four archived**, change 5 — `add-inline-masking-and-placeholder-gate` — included (2026-09-11).
`add-translation-engine-and-cli` is archived: it delivered the walking skeleton
the owner named as the next unit of work — a book of any of the four formats through the whole pipeline from the
command line with a deterministic pseudo model (`docs/Architecture.md` §9) — making `:llm` and `:pipeline` real. The
interstitial `settle-writer-policy-and-document-lifetime` shrank on 2026-09-11 when D2 and D11 were fixed directly.
`add-real-llm-clients` is in progress and makes the existing LLM seam real for Ollama-native and OpenAI-compatible
endpoints.

| Archived | Change | What it shipped |
|---|---|---|
| 2026-08-03 | 1 · `bootstrap-gradle-and-quality-toolchain` | Gradle multi-module build, eight JPMS modules, the quality toolchain, Lefthook, CI |
| 2026-08-04 | — · `restructure-module-layout` | The nine code directories relocated under `modules/` (ADR-0021) |
| 2026-08-05 | 2 · `bootstrap-app-launch-and-empty-window` | The app launches: paths, logging, single-instance lock, Guice root, a themed window, packaging |
| 2026-08-07 | 3 · `add-document-skeleton-and-epub-roundtrip` | Seam F1 and the EPUB round trip — `document-round-trip` created |
| 2026-08-09 | 4 · `add-fb2-md-txt-roundtrip` | FB2, Markdown and TXT round trips; ADR-0025/0026/0027 |
| 2026-08-11 | — · `fix-document-round-trip-corpus-defects` | Eight defects found by a 214-book write-back sweep; ADR-0028; the sweep made repeatable |
| 2026-09-11 | 5 · `add-inline-masking-and-placeholder-gate` | `⟦gN⟧` masking for all four formats, `DocumentPort.unmask` behind the placeholder-multiset gate, Markdown escape + structure check; ADR-0031 |

**Honest completion figures**, so nobody reads the table above as more progress than it is:

| Metric | Value |
|---|---|
| Capabilities with a built-behaviour spec | The archived `add-translation-engine-and-cli` change records the shipped `inference`, `translation-pipeline`, `resume` and `export` behaviour; the main ledger is updated by OpenSpec archive |
| Tests | **616** at the 2026-09-11 archive, plus the suites shipped by the archived `add-translation-engine-and-cli` change across `:llm`, `:pipeline` and `:app` — a test's name and one-line comment say what it proves; requirement-id markers were retired on 2026-09-11 (ADR-0032) |
| Modules carrying real production logic | **6 of 8** (`:api`, `:util`, `:document`, `:llm`, `:pipeline`, `:app`) — `add-translation-engine-and-cli` made the last two real |
| Stages complete | **A only**, of A · B · B′ · C · D · E |

**Inline masking now exists — the previous edition of this section said it did not.**
`add-inline-masking-and-placeholder-gate` (change 5) added `ua.bookloom.document.mask`: the `⟦gN⟧` token grammar
(`Placeholders`), the mask accumulator (`MaskWriter`), the masked-content record, the mask-time invariant check, the
placeholder-multiset gate (`PlaceholderGate`), the restore pass (`Unmasker`), the markup escaper (`MarkupEscape`)
and the plain-text masker. `TreeMasker` (tree-format masking) lives beside `BlockSegmentWalker` in `.model`, and the
Markdown masker/escaper/structure check (`MarkdownMasker`, `MarkdownEscaper`, `MarkdownStructureCheck`) live beside
`MarkdownWalker` in `.md` — a deliberate deviation from the change's own design.md D10, because a masker in `.mask`
walking `TreeNode` would make `.model` and `.mask` mutually dependent and force the one format-agnostic package to
depend on jsoup, JDOM2 and commonmark at once. Every segment in all four formats now carries a real `masked` form
and an ordered token→fragment map; `DocumentPort` gained `unmask(BookFormat, Segment, String)`, which compares the
placeholder multiset as a hard gate before restoring anything and fails as `ErrorCode.validation`, with no repair
attempt, on any mismatch (`FR-DOC-05`). Measured against the 213-book corpus: 300,253 placeholders across 787,781
segments (0.38 per segment, 11.5% of segments carrying any at all, worst single segment 1,015), and every openable
book restores every segment from its own masked form. What the gate does **not** close is recorded below as
decision debt D7–D9 — a swapped or concatenated placeholder pair still satisfies the multiset, and the flat
token→fragment map carries no pairing information; both stay open by design until
`add-chunking-and-context-assembly` (change 12). D10 was in that list and is not any more: a **second** independent
audit of change 5, run with clean context after its first audit's fixes were green, measured that a pipe in a
translated cell collapses a four-cell row into one paragraph, and change 5 now refuses it. That audit is also why
this section should be read with the eight further defects it found in mind — three of which the *first* audit's
own fixes had introduced.

Everything below the archived rows remains unplanned.

## how-to-use-this {#how-to-use-this}

1. Take the lowest-numbered change whose stage dependencies are satisfied (see `07_ROADMAP.md#stages` — A before
   everything; B and B′ both after A; C after B; D after B′ and C; E after D). **Stage dependency governs
   eligibility, not the change number** — and interstitials carry no number, so read the stage tables, not the
   numbers alone. In practice delivery is **strictly serial, one change in flight**, even where ADR-0017 permits
   parallelism (see Stage B′).
2. Run `/opsx:propose` for it. Read the generated artifacts critically — `openspec/config.yaml` steers them toward the
   R1–R4 standard, it does not guarantee them.
3. `openspec validate <change> --strict`, then `/opsx:apply`, then the gate and the app run by hand, then
   `/opsx:archive`.

Capability names must come from the 16-name map in `openspec/config.yaml`. **NEW**
means the change creates `openspec/specs/<capability>/`; **MOD** means it adds to or changes an existing one; check
`openspec/specs/` before deciding which.

---

## Stage A — Infrastructure ✅ COMPLETE

> **Goal: the app builds, launches, and opens a window.** You can run `./gradlew :app:run` and a themed BookLoom
> window appears. The app finds its own folders on Windows, macOS and Linux, writes a log file, refuses to start
> twice, and can be packaged into something distributable. Every quality check — formatting, linting, architecture
> rules, coverage, licences — runs and passes.
>
> **Still missing:** the window is empty. The app cannot open a book, or do anything at all.

Nothing else starts until **all three** are archived. All are pure infrastructure with no user-observable behaviour, so
all set `skip_specs: true`.

| # | Change | Capability | Exit gate |
|---|---|---|---|
| 1 | `bootstrap-gradle-and-quality-toolchain` | *(skip_specs)* | `./gradlew clean build check spotlessCheck` green on eight empty modules |
| — | `restructure-module-layout` | *(skip_specs)* | `./gradlew clean build check spotlessCheck` green from the new layout |
| 2 | `bootstrap-app-launch-and-empty-window` | *(skip_specs)* | `./gradlew :app:run` opens a blank themed JavaFX window; `:app:collectDist` stages a distributable |

**`restructure-module-layout` runs after change 1 and before change 2**, and takes **no number** deliberately
(ADR-0021, design D5). It relocates the nine code directories under `modules/` and repairs the paths that pointed at
their old locations; it adds no behaviour, no source file, and no dependency. Numbering it would mean renumbering
changes 2–28, and change numbers are cited in `07_ROADMAP.md`'s F1–F9 seam table, in **ADR-0017, ADR-0018, and
ADR-0019**, and in the **archived** change 1's `design.md` — accepted ADRs are immutable records and an archived
change is never reopened, so a renumber could only either violate those rules or leave the corpus permanently
self-contradicting. Every number below is therefore exactly what it was, and no prose cross-reference moved.

**One honest consequence: there are now 36 changes in 28 numbered slots.** ADR-0017's "28 changes" counts the planned
*delivery sequence*, which the interstitial changes do not join — so a reader who counts rows and gets 36 has counted
correctly. Rule 1 of `#how-to-use-this` already requires filtering (change 1 is archived, so the lowest-numbered
eligible change is already not literally 1); entries whose position is stated outright fit that.

**Eight interstitials, one reason.** `restructure-module-layout` (ADR-0021) is joined by five added by **ADR-0023**,
which found that five FX-free backend concerns — language detection, the Book Brief model, import orchestration,
export orchestration, and job lifecycle — were parked inside changes 19 and 23, whose names say *screens*. Two more
came from measurement rather than planning: `fix-document-round-trip-corpus-defects` (archived) and
`settle-writer-policy-and-document-lifetime` (proposed, `#decision-debt`), both produced by the corpus sweep. All
eight take **no number** for the same reason: change numbers are cited in ADR-0017, ADR-0018, ADR-0019, in
`07_ROADMAP.md`'s F1–F9 seam table, and in the **archived** change 1's `design.md`, and accepted ADRs and archived
changes are immutable. Every number below is exactly what it was.

**Interstitials are expected, not exceptional.** Two of the eight did not exist when this file was written; they were
created because building the thing taught us something the plan could not have known. The stage tables below are a
best current guess at order and intent, not a contract — a stage may gain, merge, split or reorder entries as the code
teaches us more, exactly as Stage B gained two and Stage C gained five.

**Change 1 — authored.** See `openspec/changes/bootstrap-gradle-and-quality-toolchain/`. Covers the Gradle multi-module
build and eight JPMS modules, the version catalog and dependency locking, the `build-logic` convention plugins
(Spotless/Palantir, Lombok + Error Prone/NullAway, Checkstyle, SpotBugs+FindSecBugs), the eight-rule ArchUnit boundary
suite, test conventions with the `liveLocal`/`promptEval`/`visual` tag exclusions and the coverage gate, Lefthook, the
CI quality workflow with the license and SCA gates, and `.editorconfig`/`.gitattributes`.

**Change 2 — covers:** the app-paths resolver (per-OS data/log dirs, dev/prod `-Dev` separation, DD-39/ADR-0015); the
programmatic Logback bootstrap publishing the log dir before the first `LoggerFactory.getLogger` call; the
build-generated version resource (DD-50); the `Result`/`AppError`/`ErrorCode` envelope with the safe-details allowlist
(**seam F2**); the `Launcher` + Guice composition root + single-instance `bookloom.lock`; the packaging scripts and
launch smoke; and a minimal JavaFX `Application` with a Scene-level token stylesheet. It absorbs the former STORY-002,
003, 006, 007, 008, and 013.

---

## Stage B — Document round-trip core

> **Goal: BookLoom can take a book apart and put it back together without damaging it.** Hand it an EPUB, FB2,
> Markdown or TXT file and it works out the format, reads the book in reading order, splits it into the individual
> pieces of text a translator would work on, and hides all the inline formatting — bold, italics, links, footnote
> markers — behind safe placeholders so a model can never mangle them. It works out what language the book is
> *actually* in, rather than trusting what the file claims. Then it writes the book back out, and the result opens
> identically: same images, fonts, chapter ids, table of contents, cross-references. It refuses DRM-protected and
> corrupt files cleanly instead of importing half a book.
>
> **Still missing:** nothing is translated — the text goes back out unchanged. There is still no UI and no model.
> This stage is proven by tests and a 213-book corpus sweep, not by clicking.

Depends on Stage A. The unit of work is narrow and demonstrable: **open an existing file → parse → reassemble → save
it back**, canonical-equal. **Two of four are archived**; changes 3 and 4 shipped the round trip for all four formats.

| # | Change | Capability | State |
|---|---|---|---|
| 3 | `add-document-skeleton-and-epub-roundtrip` | `document-round-trip` **NEW** | ✅ archived |
| 4 | `add-fb2-md-txt-roundtrip` | `document-round-trip` MOD | ✅ archived |
| — | `fix-document-round-trip-corpus-defects` | `document-round-trip` MOD | ✅ archived |
| 5 | `add-inline-masking-and-placeholder-gate` | `document-round-trip` MOD | ✅ archived 2026-09-11 |
| — | `settle-writer-policy-and-document-lifetime` | `document-round-trip` MOD | proposed — `#decision-debt` |
| — | `translate-markdown-raw-html-blocks` | `document-round-trip` MOD | unplanned — recorded below |
| — | `add-metadata-units-and-language-detection` | `document-round-trip` MOD · `book-import` **NEW** | closes Stage B |

Change 3 establishes **seam F1** (skeleton + ordered segment list; text nodes the only mutable slots). Change 5
establishes the placeholder-multiset hard gate that no confidence score or judge verdict can override.

**Change 4 additionally corrects three things change 3 shipped**, because it is the change that reopens that code:
how a translatable block is recognised (ADR-0027 — a tag whitelist left 42 of 194 surveyed books importing
essentially empty), how a translation is written back (ADR-0025 — writing into a single text node corrupts any block
containing inline markup), and how DRM is adjudicated (ADR-0026 — 16 of those 194 books are refused although none
carries content encryption). Change 4 also revises seam F1's anchor: `SkeletonAnchor` becomes a sealed interface over
`NodeAnchor(nodePath, runIndex)` and `ByteSpanAnchor(start, end)`, since Markdown and TXT reassemble by splicing the
original byte buffer rather than by mutating a tree.

**Change 4 measured a fourth shipped-EPUB defect it deliberately did not fix.** Running the built importer over a
216-book local corpus (change 4's task 12.8, a manual step that gates nothing) found **12 of 194 EPUBs still
importing with zero segments** after structural block recognition — a different cause from the 42 ADR-0027 fixed.
Those books self-close their script tag (`<script src="js/book.js"/>`), which is valid XHTML and unrepresentable in
HTML: `<script>` is a raw-text element, so jsoup's HTML parser — chosen by change 3's design.md D4 precisely
because real-world XHTML is HTML-shaped — cannot honour the XML self-closing form and swallows the entire rest of
the document as script data. Confirmed against a minimal reproduction: the body has zero children with the tag
present and two with it removed. Change 4 left it alone because no requirement of that change covers it and
revising D4's parser handling deserves its own fixture, its own requirement and its own ADR note; the evidence and
the reproduction are in `openspec/changes/archive/*-add-fb2-md-txt-roundtrip/notes-corpus-run.md`. **Whichever
change takes it must add a fixture carrying that shape** — change 4's text-coverage assertion is what catches this
class of failure, and it catches nothing the fixtures do not contain.

**This section previously assigned change 5 `EC-MD-2`'s second half** — translating the text nodes inside a
Markdown raw-HTML block — on the premise that "change 5 introduces exactly that mechanism" for inline spans. **That
premise is false, and change 5's own proposal found it false while building the thing.** `BlockSegmentWalker` emits
a `NodeAnchor` and a re-serialized `sourceInner`; Markdown reassembles by splicing bytes at a `ByteSpanAnchor`.
Nothing change 5 builds carries over: honouring the second half needs a second walker that understands raw-HTML
content rather than a tree node, a character-to-byte offset bridge back onto Markdown's byte-span anchoring, pinned
output settings for the nested jsoup parse so re-serialization is deterministic, a rewrite of the two requirements
change 4 already shipped for raw-HTML preservation, and a recalibrated text-coverage floor for every Markdown
fixture that carries a raw-HTML block. That is a change of its own, not a corner of change 5. Until it lands, the
shipped requirement *Preserve embedded raw HTML in Markdown without segmenting it* stays true and untouched.

**`translate-markdown-raw-html-blocks` runs after `settle-writer-policy-and-document-lifetime`.** It **covers:**
exactly the second half of `EC-MD-2` named above — a second walker, the offset bridge, the pinned nested-parse
settings, the requirement rewrite and the fixture-floor recalibration this section just described. It takes **no
number** for the same reason every other interstitial does.

### decision-debt {#decision-debt}

**`fix-document-round-trip-corpus-defects` deliberately left five findings unfixed — D1 through D5.** Each is real,
each is evidenced in `docs/implementation_plan/notes-corpus-verification.md`, and each is listed separately below
rather than as one paragraph so it can be found on its own; leaving them unrecorded is how they get rediscovered from
scratch. **D6 is not one of those five** — it surfaced later, while building `add-inline-masking-and-placeholder-gate`
(change 5), and is recorded here for the same reason rather than folded into the earlier change's count. **D7
through D10 surfaced later still**, while writing change 5's design and non-goals. What this section adds is an
**owner for every one** — previously D1–D5 were named and assigned to nobody, and each finding since is assigned the
moment it is found.

| # | Finding | Status | Owner |
|---|---|---|---|
| D1 | The four writers disagree on target text the resolved charset cannot represent | **Settled** — transcode the document to UTF-8 (**ADR-0029**) | `settle-writer-policy-and-document-lifetime` |
| D2 | The reader accepts EPUBs the writer can never export | **Settled** — synthesize the `mimetype` entry on write (**ADR-0030**) | `settle-writer-policy-and-document-lifetime` |
| D3 | No `close()` or eviction seam on the four `Open*Registry` singletons | **Resolved** in `add-translation-engine-and-cli` — `DocumentPort.close(Document)` releases an opened book through its reader's registry | `add-translation-engine-and-cli` |
| D4 | `EpubWriter` mutates the registry-held tree in place | Open — fix, or document as a contract | `settle-writer-policy-and-document-lifetime` |
| D5 | One 26,306-character segment; 15 books over 5,000 | Open **by design** — the safety net belongs to the chunker | change 12 · `add-chunking-and-context-assembly` |
| D6 | An FB2 entity reference is emitted twice: the skipped reference plus the characters it expands to | Open — the one-line fix is a no-op; expanding entities changes how the offline invariant is enforced | `settle-writer-policy-and-document-lifetime` |
| D7 | A model that returns a masked pair's tokens swapped or concatenated satisfies the placeholder-multiset gate and restores to broken or empty markup | Open **by design** — `FR-DOC-05` specifies a multiset comparison, order-insensitive by definition | change 12 · `add-chunking-and-context-assembly` |
| D8 | The flat token→fragment map does not say which two tokens are partners in a masked pair | Open **by design** — the chunker's "never split a pair" obligation needs pairing information this change does not expose | change 12 · `add-chunking-and-context-assembly` |
| D9 | A Markdown shortcut reference link (`[t]`) whose translated label matches no definition silently becomes literal text on the next parse | Open — reachable only once translation runs | Unowned — needs a change |
| D10 | A `\|` written into a translated Markdown table cell splits its row on the next parse | **Resolved** in change 5's round-2 audit — an unescaped pipe the source did not have is now `validation` | change 5 |
| D11 | `Fb2Writer` re-serializes every line feed in an FB2 document as `\r\n` | Open — the adapter half is fixed; the document-write half is a writer-policy decision | `settle-writer-policy-and-document-lifetime` |
| D12 | `SafeDetails.withPlaceholderMultiset` bypasses the length cap, and a model controls how many tokens it renders | **Resolved** in change 5's round-2 audit — the model-derived side is bounded by count and per-token length, overflow stated | change 5 |
| D13 | `RestoredContent.fragmentRanges` is a `List<int[]>`: the "defensively copies" Javadoc is untrue of the arrays, and `equals`/`hashCode` compare by identity | Open — safety currently rests on one undocumented private copy | next `:document` change |
| D14 | `DocumentService.unmask` routes on a ternary where an exhaustive `switch` is the mechanism two Javadocs promise | **Resolved** in change 5's round-2 audit — replaced by an exhaustive `switch` over all four `BookFormat` constants | change 5 |
| D15 | Markdown emits segments whose masked form is exactly one token — 46 code-only table cells corpus-wide | Open — wasted model calls, not corruption | `translate-markdown-raw-html-blocks` |
| D16 | The Markdown structure check is per-segment, so document-level block structure is unguarded | Open — measured, one book re-opened with 28 segments where it had 31 | `add-chunking-and-context-assembly` (change 12) |
| D17 | Declared language codes arrive unnormalized (`ua`, `EN`, `en-US`) | Open — found by the 2026-09-12 `Books_Examples` sweep; owned by `add-metadata-units-and-language-detection` |
| D18 | `:document` logs only at its port boundary: its readers, writers and maskers write no DEBUG or TRACE lines | Open — left out of `add-translation-engine-and-cli` by design (its design.md, Non-Goals) | Unowned — the next `:document` change |

**The review of `add-translation-engine-and-cli` found more gaps** — EPUB language metadata, serialization details, the
command line and what the translation screen will need. They are recorded, each with a reproduction, in
`docs/next_features.md`; the ones already owned in this table (D1, D7, D8) are cross-referenced there, not repeated.

**D1 — the four writers disagree on unrepresentable target text.** Found by the corpus verification, which sets every
segment's target to a marker plus its own source text and so exercises exactly this. Measured:

| format | behaviour on unrepresentable target text |
|---|---|
| TXT | **refuses** — `CorruptContainerException` → `ErrorCode.validation` (`TxtWriter.encode`) |
| FB2 | **silently switches the whole document to UTF-8** and re-declares the encoding (`Fb2Writer.serialize`) |
| Markdown | **silently substitutes `?`** and returns success |
| EPUB | **silently substitutes `?`** — `tree.outerHtml().getBytes(charset)` replaces unmappable characters |

Four formats, three policies, two of them silent. So this was never "`MarkdownWriter` is missing a guard" — adding
`TxtWriter`'s guard there would have cemented one of three competing behaviours with no requirement to justify it, and
left EPUB corrupting silently regardless. It is latent only while `:pipeline` is a stub; it stops being latent the
moment the first run exports a book whose source charset cannot hold the target language, which for a `windows-1252`
source translated into Ukrainian is **every single segment**. **Settled by ADR-0029: transcode the whole output
document to UTF-8 and re-declare the encoding**, uniformly across all four formats, generalising the rule
FR-DOC-FB2-3 already mandates for FB2. The ADR tabulates the four frozen-spec clauses it deviates from.

**D2 — the reader accepts EPUBs the writer can never export.** `EpubReader` imposes no `mimetype` requirement;
`EpubWriter` requires one, because DD-43 mandates mimetype-first-and-STORED on output. `aliceDynamic.epub` has 68
entries and none named `mimetype`, so it opens with 13 units and 836 segments and then refuses every write —
including a zero-edit one — with `ErrorCode.validation`. The refusal is *correct*; the defect is the **asymmetry**,
because the app accepts a book for translation it can never deliver and the user finds out only after translating.
**Settled by ADR-0030: synthesize the entry on write.** **Implemented 2026-09-11** — `EpubWriter.mimetypeEntry` synthesizes it; `EpubWriterTest` covers a source with no `mimetype`. OCF fixes it completely — name `mimetype`, content
`application/epub+zip`, first position, STORED — so nothing is invented, and the export is strictly more conformant
than the source. Costs one narrow carve-out in the EPUB golden's entry-by-entry comparison.

**D3 — there is no `close()` or eviction seam on the open-document registries.** All four `Open*Registry` classes are
`@Singleton` over a bare `ConcurrentHashMap` with no `remove`/`evict`/`close`, and `DocumentPort` exposes only `open`
and `write`. Per open EPUB the registry retains every zip entry's *inflated* bytes plus a full jsoup DOM per spine
document, for the process lifetime — so **every completed translation job will leak its book**, not merely multi-book
browsing. Inert while `:pipeline` and `:persistence` are stubs, and cheapest to add before `:pipeline` has callers.
No requirement covers document lifetime.

**D4 — `EpubWriter` mutates the registry-held tree in place.** `writeSegmentsBack` mutates
`parsed.spineTreesByHandleId()`, the live tree in the registry rather than a copy, so a second `write()` on one
document id works from the already-mutated tree. Latent in production today and no concurrent-open/write test exists
either way — but it was *not* latent for the corpus verification, where it invalidated the first harness design and
forced every write probe to open a fresh service and a fresh document. Advisory for `:pipeline`'s retry path.

**D5 — one 26,306-character segment, and 15 books over 5,000.** Verified as single genuine unbroken `<p>` elements, so
`:document` is behaving correctly and splitting them would violate the skeleton invariant. The spec already
anticipates it (`02_GLOSSARY.md`: "oversized single paragraphs are sentence-split only on overflow", FR-ALGO-02/DD-44)
but `:pipeline` is a stub, so the safety net does not exist. Against the settings' 512-token minimum `num_ctx`, that
one segment overflows by 15–20×. **Stays with the chunker at change 12**, with this corpus as its proof case — moving
it earlier would put a chunking decision inside a document change.

**D6 — an FB2 entity reference is emitted twice.** **Resolved 2026-09-12** — entities are expanded on read (book header or the bundled standard list, never fetched); `Fb2EntityExpansionTest` covers the internal, custom, external-DTD and `file:` cases. Found while building change 5. With entity expansion left off (as
`SecureXml` requires for the offline invariant), the XML parser reports both the skipped reference and the characters
it expands to, so `<p>A&nbsp;B</p>` round-trips to `<p>A&nbsp;&#xa0;B</p>` — one non-breaking space in, two out, on
every write. Two things make it non-trivial rather than a one-line fix. First, the obvious fix is a **no-op**:
`setExpandEntities(b)` writes the same `external-general-entities` feature key the next line of `SecureXml` sets to
`false`, so calling it changes nothing the parser actually does. Second, the configuration that does expand entities
requires dropping that flag in favour of an `EntityResolver`, which changes how the offline invariant is enforced and
additionally drops the document's internal subset. Latent only because no fixture in the repo declares an entity
subset; the corpus carries 10,377 references, so every one of them would double on the next write once a fixture
does.

**D7 — a model can reorder a masked pair and still pass the gate.** The placeholder-multiset gate compares counts,
not positions: a model that returns `⟦g1⟧old⟦g0⟧` for a source that masked as `⟦g0⟧old⟦g1⟧` has the same multiset
and restores as `</em>old<em>`, and a model that concatenates the two tokens instead of separating them (`⟦g0⟧⟦g1⟧`
where the source held text between them) also passes and restores to an empty `<em></em>`, deleting the words.
Neither is a bug in the gate — the frozen spec's placeholder-multiset requirement (`FR-DOC-05`) is order-insensitive
by definition — it is a hole the gate does not close. Open **by design**: closing it needs the chunker's own
machinery, so it stays with `add-chunking-and-context-assembly` (change 12).

**What that entry understated, measured against the corpus during change 5's review: the two tree formats behave
differently, and the more common one is the unsafe one.** `Jsoup.parseBodyFragment` silently repairs a malformed
fragment, while `Jdom2TreeNode.parseFragment` refuses it — so the *identical* model output is applied on EPUB and
rejected on FB2 with `ErrorCode.validation`. Swapping only the first two tokens of every segment, the write succeeds
on every EPUB tried and fails on every FB2 one; with the token sequence reversed outright, one EPUB re-opened with
**4,733 segments where it had 5,096**, having reported success. So the failure mode on 193 of the 213 corpus books is
not "restores to broken markup a reader would notice" but "silently drops content and reports success". Change 12
owns the fix; whoever takes it should know the EPUB write path currently has no structural guard of its own behind
the placeholder gate.

**D8 — the flat token→fragment map does not say which two tokens are partners.** `add-chunking-and-context-assembly`'s
own obligation — never split a masked opening/closing pair across a chunk boundary (`DD-19`,
`02_Architecture/05_PIPELINE_ENGINE.md`) — needs to know which token closes which, and `Segment.placeholders` is a
flat map from token to fragment with no pairing information; for Markdown, both fragments of an emphasis pair are the
literal `*`, so pairing cannot even be inferred from the fragment text. Open **by design**: exposing pair membership
is the job of the change that needs it, not this one.

**D9 — a Markdown shortcut reference link can silently become literal text.** `[t][r]` and `[t][]` mask as a pair and
restore correctly, but a *shortcut* reference `[t]` masks to `[` and `]`, and if the translated label no longer
matches any definition, the restored text parses as plain text rather than as a link on its next parse. The
structure check does not catch it, because both the source and the restored target are parsed standalone, and in
that context neither one's shortcut reference resolves either — so the construct-type multiset agrees on both sides.
Masking neither causes nor worsens this; translating the label text is what makes it reachable. Unowned — needs a
change.

**D10 — a `|` written into a translated Markdown table cell splits its row. RESOLVED in change 5's round-2 audit.**
A pipe character delimits table columns; if a translated cell's text contains one the source cell did not, the next
parse reads it as an extra column boundary instead of as cell content. Both gates passed regardless: the
placeholder-multiset gate has nothing to say about table syntax, and the structure check's construct-type multiset
does not change, because a cell's text parses the same way with or without the pipe when read standalone. It was
recorded here as unowned on the reasoning that translation is what makes it reachable — but two independent
reviewers rediscovered it from scratch in change 5's second audit, one of them measuring the consequence: a
four-cell row collapsing into a single paragraph, four translatable cells becoming one. Change 5 fixes it as one
half of a containment test it needed anyway for headings and cells (a line terminator ends the enclosing block for
either kind; an unescaped pipe opens a new column in a cell), so an unescaped pipe the source did not have is now
`ErrorCode.validation` and a pipe the model correctly escaped as `\|` still succeeds. **The lesson worth keeping:
a defect whose consequence is unmeasured is systematically under-prioritised.** This one was filed as a syntax
curiosity and is in fact table destruction.

**D11 — `Fb2Writer` re-serializes every line feed as `\r\n`.** **Resolved 2026-09-11** — the writer now echoes the source's line-ending style (`ParsedFb2.crlfLineEndings`); `Fb2WriterTest` covers an LF and a CRLF source. Found while building
`add-inline-masking-and-placeholder-gate`. `Format.getRawFormat()` does not mean "emit the bytes as they were
read": measured on JDOM2 2.0.6.1 its line separator defaults to `\r\n`, so a text node holding one line feed
re-serializes with two characters where the source had one, and `Fb2Writer` serializes the whole document with it.
No test catches it, because `Fb2CanonicalAssert.canonicalize` whitespace-collapses text before comparing — the same
blind spot change 5's own design.md Risks section already names for masking whitespace defects. **The adapter half
is fixed in change 5**, which pins `LineSeparator.NL` in `Jdom2TreeNode.markup()`, because that call feeds a
segment's `sourceInner`, the `sourceHash` taken over it, and every atomic protected span's mapped fragment — all
three of which change 5 makes load-bearing and one of which its own requirement calls the *exact* source fragment.
**The document-write half is deliberately left here**, because changing the bytes of every exported FB2 file is a
writer-policy decision of exactly the kind D1 and D2 already are, and it belongs with them rather than inside a
masking change. It is not a correctness defect in the XML sense — a parser normalizes `\r\n` to `\n` on the way
back in — which is precisely why it has gone unnoticed and why it needs a deliberate decision rather than a
drive-by fix.

**D12–D16 came from a four-reviewer audit of change 5, run with clean context after its own gate was green.** That
audit found four behaviour defects, all fixed in change 5 itself — a link label wrapped across a line making a book
unopenable, a numbered heading that could never be translated, a hard line break that could never be neutralised,
and a stray backslash written into a book. D12–D16 are what it found and change 5 deliberately did **not** fix,
recorded here so none of them is rediscovered from scratch.

**D17 — declared language codes arrive unnormalized.** Found by the 2026-09-12 `Books_Examples` sweep (216 books):
one EPUB declares `ua` (not a valid code; the book is Ukrainian, `uk`), one FB2 declares `EN`, two EPUBs `en-US`.
Nothing normalizes these yet; the language-handling change owns case-folding, region-tag handling and the `ua`→`uk`
alias. Recorded, not fixed. The same sweep fixed one product defect (a spine item whose file is absent is now skipped,
not refused) and two harness artefacts — see `notes-corpus-verification.md`.

**D12's first half is RESOLVED in change 5's round-2 audit; its second half stands.** `SafeDetails` deliberately
lifted its 120-character cap so a forty-placeholder segment's report is not truncated — but the *observed* multiset
comes from the model, so its cardinality is unbounded, and `AppError.details` is rendered into a dialog and written
to a log file. The bound that was lifted is the character cap; the volume bound was never replaced. Measured in the
second audit, one degenerate token — the grammar `⟦g(\d+)⟧` has an unbounded digit run — rendered a 200 KB
`details` and a 200 KB log line. Change 5 now bounds the observed side by count and per-token length, states the
overflow rather than silently dropping, and logs the bounded rendering; the expected side keeps its cap lift. The
second half stands: `RestoredContent`
promises in its Javadoc that it "defensively copies" its ranges, which `List.copyOf` does for the list and not for
the `int[]` elements inside it; nothing corrupts today only because `MarkdownEscaper` rebuilds every array before
mutating it, in an undocumented private helper that reads as redundant. A reader tidying that helper away
reintroduces silent range drift with no compile error and no failing test. Both belong to whoever next opens
`:document` rather than to change 12: `unmask` returns a `String`, so `RestoredContent` never crosses the module
edge and a `:pipeline` change would never open the file.

**D17–D18 came from change 5's *second* audit**, run with clean context after the first audit's fixes were green.
That audit found eight book-corrupting defects — three of them created by the first audit's own fixes, which is the
finding that matters most about how this codebase is reviewed. All eight are fixed in change 5. D17 and D18 are
what it found and change 5 deliberately did **not** fix.

**D17 — a Markdown link label ending in a span-less child derives the wrong closing delimiter.**
`MarkdownEscaper.pairedGroup` takes a paired construct's closing position as its last *spanned* child's end. For an
ordinary link that is exactly the `]`. When a soft or hard line break trails the label — `[foo  \n](u)`, a label
wrapped across a line — the last spanned child is the text before the break, whose end is the line feed at index 6,
while the `]` sits at 7. Measured, the old code inserted a backslash before the line feed, manufacturing a hard
line break that a later round then deleted; it reached a plausible-looking output by luck. Change 5's escapability
guard now refuses that position, so the construct survives to be reported as `ErrorCode.validation` — an honest
failure rather than a corruption, but the derivation is still wrong and such a link can no longer be accepted at
all. Whoever next opens the escaper should derive a link's closing delimiter from the destination's start rather
than from the label's last spanned child.

**D18 — an FB2 block whose only content is a CDATA section is counted as translated but never translated.**
`TreeMasker` masks a CDATA section atomically, for escaping fidelity rather than untranslatability (ADR-0031 D2).
So `<p><![CDATA[a < b]]></p>` produces a segment whose masked form is nothing but `⟦g0⟧`: the prose inside the
CDATA is never handed to a model, while the FB2 text-coverage metric counts those characters as reached. The
coverage floor therefore over-reports on any FB2 book using CDATA for prose. Found while narrowing change 5's
"a run with nothing translatable is not a segment" rule — that rule is scoped to named-atomic elements precisely so
it does *not* silently drop these blocks, which is the right call for a masking change and leaves the metric wrong.
Belongs with whichever change next owns the coverage metric.

**Two test-strength findings that are not decision debt but should not be lost either.** `FixtureSweepTest`'s
`sourceHash` assertion recomputes the expected value with production's own `HashUtil` call, which the
anti-tautology rule in `testing.md` forbids; and `PrimaryFixtureEpub`, the richest EPUB fixture in the tree, is not
registered in `FixtureCatalog`, so it never reaches the mask-then-restore identity sweep — against that
catalogue's own stated rule that registering a fixture there is what gates it. Both belong to whichever change next
opens `:document`'s test tree.

**Why D1–D4 are one change and not four.** They are the same question wearing four faces — *what is `:document`'s
contract with `:pipeline`?* — and all four get cheaper the earlier they land and markedly more expensive once
`:pipeline` has real callers. D3 in particular is an `:api` change, and `DocumentPort` today has exactly two methods
and one caller. Hence `settle-writer-policy-and-document-lifetime`, below.

**`settle-writer-policy-and-document-lifetime` runs after change 5.** It **covers:** D1's uniform transcode-to-UTF-8
across all four writers with a scenario per format, and the golden carve-out ADR-0029 names; D2's synthesized
`mimetype` entry and the narrow EPUB-golden carve-out ADR-0030 names; D3's document-lifetime seam on `DocumentPort`
and eviction on the four registries; D4's copy-on-write so a re-write of one document id starts from the parsed
tree rather than the mutated one — **which change 5's round-2 audit gave sharper teeth**: now that a block's runs
are split once per write rather than per segment, an ordinary second write is idempotent, but a second write of a
translation that *reordered* a run-splitting element would mis-aim exactly as the defect that fix closed did,
because the first write leaves the block split differently than the parse did; and **D6's doubled entity reference**, which lands here because expanding an
entity means substituting an `EntityResolver` for the `external-general-entities` flag — a change to how the
offline invariant is enforced, and so a writer-policy decision of exactly the kind D1 and D2 already are. It takes
**no number** for the same reason every other interstitial does.

**`fix-document-round-trip-corpus-defects` narrows how FB2 *classifies* a malformed translated fragment (a bare `&`
or `<` in target text) to `ErrorCode.validation` instead of `internal`, but does not change what happens to the
character itself.** Deciding how such a literal should be *escaped* or *masked* is change 5's, per its own Non-Goals;
the two must not be conflated later — one fixes error classification at the write boundary, the other fixes what the
model is allowed to hand back in the first place.

**`add-metadata-units-and-language-detection` runs last in Stage B and closes it** (ADR-0023) — after change 5 and
after `settle-writer-policy-and-document-lifetime`. It exists because
changes 3 and 4 each defer something that no later change claimed. It **covers:** producing the metadata-unit segments
whose kinds change 3 ships in the enum but never emits — `METADATA_TITLE`, `METADATA_AUTHOR`, `FRONTMATTER_VALUE`,
`ALT`, `NAV_LABEL` (DD-47, `02_Architecture/03_DOCUMENT_MODEL.md`) — measured on the owner's 216-book corpus on 2026-09-12
this is 11,997 NCX labels across all 197 EPUBs, 20 EPUB 3 nav documents, 7,450 page `<title>`s, 197 `dc:title`s,
106 `dc:description`s, 6 FB2 annotations and one `img@alt`; carving the EPUB nav document and NCX out of the
"out-of-spine = verbatim" rule so their ToC labels become segments (FR-DOC-EPUB-8); and **source-language detection
with Lingua** in `:document/ua.bookloom.document.detect`, populating `Document.detectedSourceLang` and surfacing the
declared-vs-detected mismatch state (FR-IMPORT-03, EC-LANG-*). It also inherits two `FR-DOC-04` masking categories
change 5 left to it: masking a detected **foreign-language inline run** as a protected keep-as-is placeholder so the
surrounding prose still translates (`EC-FOREIGN-3`), which needs the same Lingua detection this change already
builds; and masking the **metadata-unit segments** themselves — they mask exactly like any other segment's run once
they exist, and this is the change that makes them exist. Both land in `document-round-trip` riding along with this
change's own `document-round-trip` MOD row, whichever change schedules them. Detection lands in Stage B rather than
Stage D because the deterministic QA gate's target-language check needs Lingua at change 14
(`02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`) — leaving it in a screen change would mean building it twice.

---

## Stage B′ — Contract floor + UI component library

> **Goal: the app looks like BookLoom, and every part of the engine has an agreed shape.** The app can show a
> component-library screen displaying every control the finished product will use — buttons, tables, tree views,
> toggles, sliders, tabs, dialogs, toasts — all styled in the real colour palette and switching correctly between
> light and dark. Separately, every job the backend will eventually do exists as a named, agreed interface with a
> fake stand-in behind it, so screens can be built and tested against something that answers before the real engine
> exists.
>
> **Still missing:** none of it is connected to real work. Clicking things produces nothing. There are no application
> screens yet — only a catalogue of parts.

Depends only on Stage A. Reusable controls built against the mockup's own **"Component library"** screen —
widget-level, composed into no application screen yet. Screen composition is Stage D.

**ADR-0017 permits Stage B′ to run in parallel with Stage B, and in practice it does not.** Delivery is **strictly
serial in backlog order, one change in flight at a time**: Stage B completes in full before B′ starts. The parallelism
ADR-0017 allows is a statement about dependencies, not a schedule — nothing here forbids taking it up later if the
work is ever split across people.

| # | Change | Capability |
|---|---|---|
| — | `add-api-contract-floor-and-stubs` | *(skip_specs)* |
| 6 | `add-theming-token-system` | `theming` **NEW** |
| 7 | `add-ui-component-library` | `app-shell` **NEW** *(or `skip_specs` — decide at propose time)* |

**`add-api-contract-floor-and-stubs` runs after change 2 and heads Stage B′**, in parallel with change 3 (ADR-0023).
Without it Stage B′ is parallel only on paper: a control library has no port to build against until Stage C is well
under way, because today every port arrives with the change that implements it. It **covers** every `:api` contract
change 3 does not own — `Project`, `BookBrief`, `QualityDial`, `ReviewMode`, `TranslationEngine`
(`02_Architecture/02_MODULES_AND_LAYERING.md:42`, which no backlog entry previously claimed), the repository ports
(`06_DATA_MODEL_SQLITE.md#tables`), and `Provider`/`ProviderProfile`/`ProviderFactory`/`ChatRequest`/`ChatResponse`
(`04_LLM_INTEGRATION.md`) — plus **in-memory stub implementations** shipped as `:api` test fixtures so any module's
tests can run against a working graph. It sets `skip_specs: true`: contracts alone have no user-observable behaviour.
Where a port genuinely cannot be settled from the frozen spec, the proposal must **omit it and say which and why**
rather than guess — a wrong port here is the one real cost of doing this early (ADR-0023, Consequences).

Change 7's capability question is genuinely open: a library of controls composed into no screen may have no
user-observable behaviour to specify, in which case `skip_specs: true` is honest and inventing requirements is not.
Decide when proposing, not now.

---

## Stage C — Engine internals

> **Goal: BookLoom can translate an entire book, start to finish, on its own.** This is the stage where the app stops
> being scaffolding and becomes a translator. By the end it can: remember projects, settings and progress in its own
> local database; talk to a real Ollama or LM Studio server, list the models installed there, let you type a model
> name by hand, and check the connection works before starting; pack paragraphs into chunks that fit the model's
> context window and send each one with the surrounding context it needs to stay coherent; build a glossary of
> character and place names up front and hold them consistent across the whole book; check every translation
> automatically for missing formatting, wrong language, untranslated text, repetition loops and dropped content,
> score it, and ask a second model to judge the borderline ones; repair what fails, and flag what it cannot repair;
> save after every single chunk, so killing the app mid-book loses nothing and it picks up exactly where it stopped;
> and finally write the finished translation back into the original file format.
>
> The stage closes with one deliberate proof: a small book goes all the way through — imported, chunked, translated,
> checked, judged, repaired, interrupted, resumed, exported — with canned answers standing in for the model. Nothing
> in the pipeline is faked except the model itself.
>
> **Still missing:** there is no way for a person to drive any of this. It is all headless, invoked by tests.

Depends on Stage B. Storage (8–9) and the provider stack (10–11) are independent of each other; both precede the
pipeline (12+). **Stage C is where the backend becomes complete** — see `#backend-complete-milestone` below. It is
also the largest stage by some distance: **twelve changes, and all three stub modules become real in it.**

| # | Change | Capability |
|---|---|---|
| 8 | `add-local-storage` | `local-storage` **NEW** |
| 9 | `add-resume-checkpoints` | `resume` MOD |
| 10 | `add-llm-provider-abstraction` | `llm-provider` **NEW** — consumed in reduced form by `add-real-llm-clients`; persistence and provider editing remain |
| 11 | `add-inference-gate-and-response-contract` | `inference` MOD — consumed in reduced form by `add-real-llm-clients`; repair and interactive gate behavior remain |
| 12 | `add-chunking-and-context-assembly` | `translation-pipeline` MOD |
| — | `add-prompt-catalog-and-output-contract` | `translation-pipeline` MOD |
| 13 | `add-translation-draft-loop` | `translation-pipeline` MOD |
| 14 | `add-deterministic-qa-gate` | `quality-gates` **NEW** |
| 15 | `add-consistency-stack` | `translation-pipeline` MOD · `glossary` **NEW** |
| 16 | `add-judge-and-self-heal` | `quality-gates` MOD |
| — | `add-project-lifecycle-and-orchestration` | `book-import` MOD · `book-brief` **NEW** · `export` MOD · `translation-pipeline` MOD |
| — | `add-stub-provider-whole-book-e2e` | *(skip_specs)* |

**`add-translation-engine-and-cli` ran ahead of this stage's planned order**, mirroring how Stage A/B's interstitials
run outside the numbered sequence, and is why four of the rows above read MOD rather than the NEW the original plan
expected. Built directly against the code as it stood, it created `inference`, `translation-pipeline`, `resume` and
`export` itself: a `ChatModel`/`ChatModelFactory` contract bound once per job plus the offline `pseudo` provider
(`:llm`), and a pausable `TranslationEngine`/`TranslationJob` that translates a book segment by segment and exports
it only after the written file re-opens with the source's segment count (`:pipeline`) — driven today from
`./gradlew :app:translate` (ADR-0033). It takes no number of its own, the same way every other interstitial does not.

Seams established here: **F6/F7** (checkpoints, settings KV) by 8–9; **F3** (provider abstraction) by 10; **F4**
(single-flight gate) by 11; **F5** (context-package assembler) by 12.

**`add-prompt-catalog-and-output-contract` runs after change 12 and before change 13** (ADR-0023). The prompt layer was
previously covered only by dispersion — `07_ROADMAP.md#execution-notes` assigned *"13 draft; 16 judge/repair/reflect"*
and left brief-tone-setup, name-term pre-scan, rolling-summary and backward-revision scattered across 15, 19 and 26,
with the registry itself unowned. It **covers** `:pipeline/ua.bookloom.pipeline.prompt` as one layer: the nine
templates of `01_Product/12_PROMPT_CATALOG.md`, the variable-expansion mechanism, the JSON
`#output-contract`, and the **`promptEval` harness** (production prompt builder + real local model + embedding scorer,
env-gated and excluded from `check`) that DD-40 requires and that change 1 created only the Gradle task for. It comes
*after* 12 because a prompt builder consumes the context package (seam F5).

**Change 15 additionally owns four inline-masking categories `add-inline-masking-and-placeholder-gate` (change 5)
left unowned** — categories the frozen mask-protected-spans-before-translation requirement covers (`FR-DOC-04`,
`01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`) that change 5 does not implement. It **covers:** masking a
**locked glossary term**, which needs a populated glossary that exists only once this change ships, together with
the rule that a locked term embedded inside a longer word is masked only for its matched span, never the whole word
(`EC-INLINE-3`); **standalone and typographic numerals**, deferred because the frozen spec never draws the line
between a standalone/typographic numeral and a prose one, and this is the first change with a Book Brief to read a
unit policy from; and **bare URLs in running prose**, deferred because recognising one is lexical pattern-matching
(trailing punctuation, parenthesised URLs, internationalised domains) rather than the structural walk change 5
performs — a *linked* URL is already protected there, since an `<a href>`'s attributes ride inside its opening token
and a Markdown autolink masks atomically. Each lands in `document-round-trip` whichever change schedules it, this
one included: change 15's own capability row above is `translation-pipeline` MOD · `glossary` **NEW**, and the
masking work rides along as a `document-round-trip` MOD.

**`add-project-lifecycle-and-orchestration` runs after change 16** (ADR-0023). It is the FX-free layer between the
engine and the screens, previously parked inside changes 19 and 23. **Its job-lifecycle and export-validation scope
shrank once `add-translation-engine-and-cli` shipped ahead of it**: `TranslationEngine`/`TranslationJob`
(`:pipeline/ua.bookloom.pipeline` — the public `TranslationEngineImpl` and the package-private `TranslationJobImpl`,
not `JobHandle`) with start/pause/resume/cancel, and `BookExporter`'s reopen-and-compare-segment-count check before
publishing (FR-EXPORT-03), are both real already. What remains here: the import service (open → detect → hash →
persist project, units and segments; FR-IMPORT-01/04/05/08); the `Project` and **Book Brief** model and its
`projects.brief_json` persistence (FR-BRIEF-01..07 — 31 requirements, the second-largest FR area); binding a
persisted project and its settings to a real `TranslationJob` run; and the rest of the export service — side exports
(FR-EXPORT-05) and the final consistency-pass toggle (FR-EXPORT-06). Changes 19, 21 and 23 shrink to the screens
their names describe.

### backend-complete-milestone {#backend-complete-milestone}

**`add-stub-provider-whole-book-e2e` closes Stage C** and is the milestone the whole backend is judged by (ADR-0023).
Its green gate is one claim:

> A small book is imported from disk, parsed to skeleton + segments, chunked, context-assembled, drafted, QA-gated,
> judged, self-healed, consistency-passed, checkpointed, **interrupted, resumed**, and exported to a canonical-equal
> artifact — with the `Provider` port bound to a **stub returning canned responses**. Nothing in the pipeline is absent
> or faked except the model itself.

It **covers:** the canned-response stub `Provider` as a named fixture rather than an assumption inside a test; the
whole-book e2e asserting accepted/flagged counts and resume-after-interrupt; and the `liveLocal` suite exercising both
real clients against a real local Ollama and LM Studio. It sets `skip_specs: true` — it adds verification, not
behaviour.

**The stub is legal in exactly one place.** `.claude/rules/testing.md` **rejects** testing the LLM by mocking the
`Provider` class — `:llm` is proved at the **WireMock HTTP seam** by changes 10–11, and that is unchanged. The same
rule separately defines pipeline e2e as *"a small whole book through the engine against a stub/WireMock provider"*.
`:llm` meets fake HTTP; `:pipeline` meets a stub `Provider`. Both clauses hold, and no rule changes.

---

## Stage D — Composition

> **Goal: a person can actually use BookLoom.** Every screen from the mockup exists and works. You open the app, see
> your projects, drag a book in, and get a card confirming what was detected — with a clear warning if the book's
> declared language disagrees with its real one. You fill in a brief: source and target language, genre, tone, how
> faithful versus how natural, what to do with foreign passages and names, and how hard to try (Fast / Balanced /
> Max). You look over the chapter structure it found, and edit the proposed glossary — locking any name you want
> translated one exact way. You press start and watch it run: progress, counts of accepted, repaired and flagged
> passages, the current source and translation side by side, speed and time remaining, a live activity log. You can
> pause, resume, or stop, and stopping is safe rather than an error. When it finishes you review whatever it flagged,
> side by side, and accept, edit, retry, retry-with-a-note, or skip each one. Then you export. All of it in English
> or Ukrainian, chosen automatically from your OS language on first launch. Settings let you add providers, pick
> models, tune generation, and switch theme.
>
> **Still missing:** there is no installer — it runs from the build. Whole-book consistency polishing and the
> complete Ukrainian translation of the interface come last.

Depends on Stage B′ **and** Stage C. Screens assembled from Stage B′ widgets and wired to the Stage C engine. After
ADR-0023 the backend these screens call already exists and already runs, so a Stage D change adds a surface and a
binding — never a service.

| # | Change | Capability |
|---|---|---|
| 17 | `add-app-shell-and-navigation` | `app-shell` MOD |
| 18 | `add-localization-infrastructure` | `localization` **NEW** |
| 19 | `add-import-and-brief-screens` | `book-import` MOD · `book-brief` MOD |
| 20 | `add-structure-and-glossary-screens` | `glossary` MOD |
| 21 | `add-translating-dashboard` | `app-shell` MOD |
| 22 | `add-review-queue` | `review-queue` **NEW** |
| 23 | `add-export-flow` | `export` MOD |
| 24 | `add-settings-and-provider-ui` | `settings` **NEW** · `llm-provider` MOD |
| 25 | `add-notifications-and-error-surfacing` | `notifications` **NEW** |

Change 17 establishes **seam F8** (the observable state mirror). Change 18 lands **before** the screens deliberately, so
every screen is built against bundle keys from the start rather than retrofitted (`07_ROADMAP.md#execution-notes`).

---

## Stage E — Whole-book quality & release

> **Goal: BookLoom is something you can hand to another person.** The app can make a final pass back over a finished
> book to fix things that only became clear later — a character's name that settled into a different form halfway
> through, a term translated one way in chapter 2 and another way in chapter 20 — without ever overwriting a passage
> you edited yourself. And a tagged release produces real, downloadable applications for macOS, Windows and Linux
> that launch on a clean machine with no Java installed. The Ukrainian interface is complete, with every string
> translated and correct plural forms.
>
> **Still missing, by design rather than oversight:** the apps are unsigned, so each OS needs a manual "open anyway"
> step; there is no Windows installer, only a portable zip.

| # | Change | Capability |
|---|---|---|
| 26 | `add-backward-revision-sweep` | `translation-pipeline` MOD |
| 27 | `add-packaging-and-release` | *(skip_specs)* |
| 28 | `complete-ukrainian-localization` | `localization` MOD |

---

## capability-map {#capability-map}

The 16 capabilities, mapped from the frozen FR areas so the `FR-*` join key holds exactly. This table is duplicated in
`openspec/config.yaml`, which is what actually steers generation; keep them in sync.

| Capability             | FR area                        | Introduced by |
|------------------------|--------------------------------|---------------|
| `document-round-trip`  | fr-doc                         | change 3      |
| `theming`              | fr-ui (FR-UI-05) + fr-theme    | change 6      |
| `app-shell`            | fr-ui (FR-UI-01..04, FR-UI-09) | change 7 or 17|
| `local-storage`        | fr-persist                     | change 8      |
| `resume`               | fr-resume                      | `add-translation-engine-and-cli` |
| `llm-provider`         | fr-prov, fr-model              | change 10     |
| `inference`            | fr-infer                       | `add-translation-engine-and-cli` |
| `translation-pipeline` | fr-algo                        | `add-translation-engine-and-cli` |
| `quality-gates`        | fr-qa                          | change 14     |
| `glossary`             | fr-gloss                       | change 15     |
| `localization`         | fr-ui (FR-UI-06/08) + fr-i18n  | change 18     |
| `book-import`          | fr-import                      | `add-metadata-units-and-language-detection` (Stage B) |
| `book-brief`           | fr-brief                       | `add-project-lifecycle-and-orchestration` (Stage C)   |
| `review-queue`         | fr-review                      | change 22     |
| `export`               | fr-export                      | `add-translation-engine-and-cli` |
| `settings`             | fr-settings                    | change 24     |
| `notifications`        | fr-notif                       | change 25     |

Introducing a capability outside this list requires an ADR first — the map keeps capability names stable.

**ADR-0018 amendments.** `FR-THEME-01..10` (`01_Product/09_THEMING.md`) belongs to `theming`, and `FR-I18N-01..09`
(`01_Product/10_I18N_AND_ACCESSIBILITY.md`) belongs to `localization` — 19 requirements the original sixteen-area map
left unowned. `FR-A11Y-*` and `NFR-A11Y-*` (17 ids) are deliberately **unowned and excluded from coverage**: the
specification declares accessibility advisory and never a merge gate, so it is a review item on `:ui` changes rather
than an implementation obligation. Cite ids in the canonical zero-padded form (`FR-THEME-01`, not `FR-THEME-1`).
