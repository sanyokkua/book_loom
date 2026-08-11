**Status:** Final **Owner:** architect **Audience:** anyone picking up the next unit of work **Last Updated:**
2026-08-11 **Cross-references:** `docs/adr/ADR-0017-infrastructure-first-delivery-order.md`,
`docs/adr/ADR-0023-backend-complete-milestone-and-backlog-interstitials.md`,
`docs/adr/ADR-0016-openspec-delivery-tracking.md`, `docs/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md`,
`docs/adr/ADR-0030-synthesize-a-missing-epub-mimetype-entry-on-write.md`, `docs/implementation_plan/07_ROADMAP.md`,
`docs/implementation_plan/notes-corpus-verification.md`,
`docs/implementation_plan/README.md#end-to-end-flow`, `openspec/config.yaml`

# Change Backlog

The ordered list of OpenSpec changes that build BookLoom, grouped into the five stages of ADR-0017.

> **This is names and order only — deliberately unplanned.** No entry below has a proposal, specs, or tasks yet, and
> none should be written ahead of time. Each change gets its real artifacts from `/opsx:propose` **when its turn
> comes**, against the codebase as it actually is at that moment rather than as it was imagined months earlier. The
> "covers" column names the material a proposal should draw on; it is not a scope contract.

## where-this-stands {#where-this-stands}

**Six changes are authored and archived** (`openspec/changes/archive/`). **Stage A is complete and Stage B is two of
four**, with change 5 the next eligible entry.

| Archived | Change | What it shipped |
|---|---|---|
| 2026-08-03 | 1 · `bootstrap-gradle-and-quality-toolchain` | Gradle multi-module build, eight JPMS modules, the quality toolchain, Lefthook, CI |
| 2026-08-04 | — · `restructure-module-layout` | The nine code directories relocated under `modules/` (ADR-0021) |
| 2026-08-05 | 2 · `bootstrap-app-launch-and-empty-window` | The app launches: paths, logging, single-instance lock, Guice root, a themed window, packaging |
| 2026-08-07 | 3 · `add-document-skeleton-and-epub-roundtrip` | Seam F1 and the EPUB round trip — `document-round-trip` created |
| 2026-08-09 | 4 · `add-fb2-md-txt-roundtrip` | FB2, Markdown and TXT round trips; ADR-0025/0026/0027 |
| 2026-08-11 | — · `fix-document-round-trip-corpus-defects` | Eight defects found by a 214-book write-back sweep; ADR-0028; the sweep made repeatable |

**Honest completion figures**, so nobody reads the table above as more progress than it is:

| Metric | Value |
|---|---|
| Capabilities with a built-behaviour spec | **1 of 16** (`document-round-trip`) |
| `FR-*` ids with a covering `// Covers:` test | **~36 of 136** |
| Modules carrying real production logic | **4 of 8** (`:api`, `:util`, `:document`, `:app`) |
| Stages complete | **A only**, of A · B · B′ · C · D · E |

**One thing no entry below said out loud: inline masking does not exist.** `BlockSegmentWalker`, `MarkdownWalker` and
`TxtReader` each emit every segment with `masked == sourceInner` and an empty placeholder map — `BlockSegmentWalker`'s
own Javadoc concedes it. There is no `⟦gN⟧` code, no unmask and no tag-multiset gate anywhere in `modules/`;
`Segment.masked` and `Segment.placeholders` are contract-only fields. That is what change 5 is for, and it is why no
chunk can be validated until change 5 lands.

Everything below the archived rows remains unplanned.

## how-to-use-this {#how-to-use-this}

1. Take the lowest-numbered change whose stage dependencies are satisfied (see `07_ROADMAP.md#stages` — A before
   everything; B and B′ both after A; C after B; D after B′ and C; E after D). **Stage dependency governs
   eligibility, not the change number** — and interstitials carry no number, so read the stage tables, not the
   numbers alone. In practice delivery is **strictly serial, one change in flight**, even where ADR-0017 permits
   parallelism (see Stage B′).
2. Run `/opsx:propose` for it. Read the generated artifacts critically — `openspec/config.yaml` steers them toward the
   R1–R6 standard, it does not guarantee them.
3. `openspec validate <change> --strict`, then `/opsx:apply`, then the Definition of Done, then `/opsx:archive`.
4. At each stage boundary, run `bash scripts/fr-coverage.sh`. Advisory only.

Capability names must come from the 16-name map in `openspec/config.yaml` so the `FR-*` join key holds exactly. **NEW**
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
| 5 | `add-inline-masking-and-placeholder-gate` | `document-round-trip` MOD | ▶ **next** |
| — | `settle-writer-policy-and-document-lifetime` | `document-round-trip` MOD | proposed — `#decision-debt` |
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

**Change 5 additionally owns `EC-MD-2`'s second half** — translating the text nodes inside a Markdown raw-HTML
block. Change 4 preserves such a block verbatim and emits no segment for it, which is the half that can be honoured
without nesting a second parser inside the first; change 5 introduces exactly that mechanism for inline spans, so the
remaining half belongs there rather than being rediscovered later.

### decision-debt {#decision-debt}

**`fix-document-round-trip-corpus-defects` deliberately left five findings unfixed.** Each is real, each is evidenced
in `docs/implementation_plan/notes-corpus-verification.md`, and each is listed separately below rather than as one
paragraph so it can be found on its own; leaving them unrecorded is how they get rediscovered from scratch. What this
section adds is an **owner for every one** — previously they were named and assigned to nobody.

| # | Finding | Status | Owner |
|---|---|---|---|
| D1 | The four writers disagree on target text the resolved charset cannot represent | **Settled** — transcode the document to UTF-8 (**ADR-0029**) | `settle-writer-policy-and-document-lifetime` |
| D2 | The reader accepts EPUBs the writer can never export | **Settled** — synthesize the `mimetype` entry on write (**ADR-0030**) | `settle-writer-policy-and-document-lifetime` |
| D3 | No `close()` or eviction seam on the four `Open*Registry` singletons | Open — needs an `:api` change | `settle-writer-policy-and-document-lifetime` |
| D4 | `EpubWriter` mutates the registry-held tree in place | Open — fix, or document as a contract | `settle-writer-policy-and-document-lifetime` |
| D5 | One 26,306-character segment; 15 books over 5,000 | Open **by design** — the safety net belongs to the chunker | change 12 · `add-chunking-and-context-assembly` |

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
**Settled by ADR-0030: synthesize the entry on write.** OCF fixes it completely — name `mimetype`, content
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

**Why D1–D4 are one change and not four.** They are the same question wearing four faces — *what is `:document`'s
contract with `:pipeline`?* — and all four get cheaper the earlier they land and markedly more expensive once
`:pipeline` has real callers. D3 in particular is an `:api` change, and `DocumentPort` today has exactly two methods
and one caller. Hence `settle-writer-policy-and-document-lifetime`, below.

**`settle-writer-policy-and-document-lifetime` runs after change 5.** It **covers:** D1's uniform transcode-to-UTF-8
across all four writers with a scenario per format, and the golden carve-out ADR-0029 names; D2's synthesized
`mimetype` entry and the narrow EPUB-golden carve-out ADR-0030 names; D3's document-lifetime seam on `DocumentPort`
and eviction on the four registries; and D4's copy-on-write so a re-write of one document id starts from the parsed
tree rather than the mutated one. It takes **no number** for the same reason every other interstitial does.

**`fix-document-round-trip-corpus-defects` narrows how FB2 *classifies* a malformed translated fragment (a bare `&`
or `<` in target text) to `ErrorCode.validation` instead of `internal`, but does not change what happens to the
character itself.** Deciding how such a literal should be *escaped* or *masked* is change 5's, per its own Non-Goals;
the two must not be conflated later — one fixes error classification at the write boundary, the other fixes what the
model is allowed to hand back in the first place.

**`add-metadata-units-and-language-detection` runs last in Stage B and closes it** (ADR-0023) — after change 5 and
after `settle-writer-policy-and-document-lifetime`. It exists because
changes 3 and 4 each defer something that no later change claimed. It **covers:** producing the metadata-unit segments
whose kinds change 3 ships in the enum but never emits — `METADATA_TITLE`, `METADATA_AUTHOR`, `FRONTMATTER_VALUE`,
`ALT`, `NAV_LABEL` (DD-47, `02_Architecture/03_DOCUMENT_MODEL.md`); carving the EPUB nav document and NCX out of the
"out-of-spine = verbatim" rule so their ToC labels become segments (FR-DOC-EPUB-8); and **source-language detection
with Lingua** in `:document/ua.bookloom.document.detect`, populating `Document.detectedSourceLang` and surfacing the
declared-vs-detected mismatch state (FR-IMPORT-03, EC-LANG-*). Detection lands in Stage B rather than Stage D because
the deterministic QA gate's target-language check needs Lingua at change 14
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
change 3 does not own — `Project`, `BookBrief`, `QualityDial`, `ReviewMode`, `JobHandle`, `TranslationEngine`
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
| 9 | `add-resume-checkpoints` | `resume` **NEW** |
| 10 | `add-llm-provider-abstraction` | `llm-provider` **NEW** |
| 11 | `add-inference-gate-and-response-contract` | `inference` **NEW** |
| 12 | `add-chunking-and-context-assembly` | `translation-pipeline` **NEW** |
| — | `add-prompt-catalog-and-output-contract` | `translation-pipeline` MOD |
| 13 | `add-translation-draft-loop` | `translation-pipeline` MOD |
| 14 | `add-deterministic-qa-gate` | `quality-gates` **NEW** |
| 15 | `add-consistency-stack` | `translation-pipeline` MOD · `glossary` **NEW** |
| 16 | `add-judge-and-self-heal` | `quality-gates` MOD |
| — | `add-project-lifecycle-and-orchestration` | `book-import` MOD · `book-brief` **NEW** · `export` **NEW** · `translation-pipeline` MOD |
| — | `add-stub-provider-whole-book-e2e` | *(skip_specs)* |

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

**`add-project-lifecycle-and-orchestration` runs after change 16** (ADR-0023). It is the FX-free layer between the
engine and the screens, previously parked inside changes 19 and 23. It **covers:** the import service (open → detect →
hash → persist project, units and segments; FR-IMPORT-01/04/05/08); the `Project` and **Book Brief** model and its
`projects.brief_json` persistence (FR-BRIEF-01..07 — 31 requirements, the second-largest FR area); the job lifecycle in
`:pipeline/ua.bookloom.pipeline` (`TranslationEngineImpl`, `JobHandle`, start/pause/resume/cancel); and the export
service — apply accepted targets, `DocumentPort.write`, **validate the artifact is well-formed before finalizing**
(FR-EXPORT-03), side exports (FR-EXPORT-05) and the final consistency-pass toggle (FR-EXPORT-06). Changes 19, 21 and 23
shrink to the screens their names describe.

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
| `resume`               | fr-resume                      | change 9      |
| `llm-provider`         | fr-prov, fr-model              | change 10     |
| `inference`            | fr-infer                       | change 11     |
| `translation-pipeline` | fr-algo                        | change 12     |
| `quality-gates`        | fr-qa                          | change 14     |
| `glossary`             | fr-gloss                       | change 15     |
| `localization`         | fr-ui (FR-UI-06/08) + fr-i18n  | change 18     |
| `book-import`          | fr-import                      | `add-metadata-units-and-language-detection` (Stage B) |
| `book-brief`           | fr-brief                       | `add-project-lifecycle-and-orchestration` (Stage C)   |
| `review-queue`         | fr-review                      | change 22     |
| `export`               | fr-export                      | `add-project-lifecycle-and-orchestration` (Stage C)   |
| `settings`             | fr-settings                    | change 24     |
| `notifications`        | fr-notif                       | change 25     |

Introducing a capability outside this list requires an ADR first — the map exists so `scripts/fr-coverage.sh` means
something.

**ADR-0018 amendments.** `FR-THEME-01..10` (`01_Product/09_THEMING.md`) belongs to `theming`, and `FR-I18N-01..09`
(`01_Product/10_I18N_AND_ACCESSIBILITY.md`) belongs to `localization` — 19 requirements the original sixteen-area map
left unowned. `FR-A11Y-*` and `NFR-A11Y-*` (17 ids) are deliberately **unowned and excluded from coverage**: the
specification declares accessibility advisory and never a merge gate, so it is a review item on `:ui` changes rather
than an implementation obligation. Cite ids in the canonical zero-padded form (`FR-THEME-01`, not `FR-THEME-1`).
