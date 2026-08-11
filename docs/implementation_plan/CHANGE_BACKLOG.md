**Status:** Final **Owner:** architect **Audience:** anyone picking up the next unit of work **Last Updated:**
2026-08-05 **Cross-references:** `docs/adr/ADR-0017-infrastructure-first-delivery-order.md`,
`docs/adr/ADR-0023-backend-complete-milestone-and-backlog-interstitials.md`,
`docs/adr/ADR-0016-openspec-delivery-tracking.md`, `docs/implementation_plan/07_ROADMAP.md`,
`docs/implementation_plan/README.md#end-to-end-flow`, `openspec/config.yaml`

# Change Backlog

The ordered list of OpenSpec changes that build BookLoom, grouped into the five stages of ADR-0017.

> **This is names and order only — deliberately unplanned.** No entry below has a proposal, specs, or tasks yet, and
> none should be written ahead of time. Each change gets its real artifacts from `/opsx:propose` **when its turn
> comes**, against the codebase as it actually is at that moment rather than as it was imagined months earlier. The
> "covers" column names the material a proposal should draw on; it is not a scope contract.

**Four changes are authored and archived.** Stage A is complete — `bootstrap-gradle-and-quality-toolchain`,
`restructure-module-layout`, `bootstrap-app-launch-and-empty-window` — and Stage B has started with change 3,
`add-document-skeleton-and-epub-roundtrip`. See `openspec/changes/archive/`. Everything below that remains unplanned.

## how-to-use-this {#how-to-use-this}

1. Take the lowest-numbered change whose stage dependencies are satisfied (see
   `07_ROADMAP.md#stages` — A before everything; B and B′ in parallel after A; C after B; D after B′ and C; E after D).
2. Run `/opsx:propose` for it. Read the generated artifacts critically — `openspec/config.yaml` steers them toward the
   R1–R6 standard, it does not guarantee them.
3. `openspec validate <change> --strict`, then `/opsx:apply`, then the Definition of Done, then `/opsx:archive`.
4. At each stage boundary, run `bash scripts/fr-coverage.sh`. Advisory only.

Capability names must come from the 16-name map in `openspec/config.yaml` so the `FR-*` join key holds exactly. **NEW**
means the change creates `openspec/specs/<capability>/`; **MOD** means it adds to or changes an existing one; check
`openspec/specs/` before deciding which.

---

## Stage A — Infrastructure

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

**One honest consequence: there are now 34 changes in 28 numbered slots.** ADR-0017's "28 changes" counts the planned
*delivery sequence*, which the interstitial changes do not join — so a reader who counts rows and gets 34 has counted
correctly. Rule 1 of `#how-to-use-this` already requires filtering (change 1 is archived, so the lowest-numbered
eligible change is already not literally 1); entries whose position is stated outright fit that.

**Six interstitials, one reason.** `restructure-module-layout` (ADR-0021) is joined by five more added by
**ADR-0023**, which found that five FX-free backend concerns — language detection, the Book Brief model, import
orchestration, export orchestration, and job lifecycle — were parked inside changes 19 and 23, whose names say
*screens*. All six take **no number** for the same reason: change numbers are cited in ADR-0017, ADR-0018, ADR-0019,
in `07_ROADMAP.md`'s F1–F9 seam table, and in the **archived** change 1's `design.md`, and accepted ADRs and archived
changes are immutable. Every number below is exactly what it was.

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

Depends on Stage A. The goal is narrow and demonstrable: **open an existing file → parse → reassemble → save it back**,
canonical-equal.

| # | Change | Capability |
|---|---|---|
| 3 | `add-document-skeleton-and-epub-roundtrip` | `document-round-trip` **NEW** |
| 4 | `add-fb2-md-txt-roundtrip` | `document-round-trip` MOD |
| 5 | `add-inline-masking-and-placeholder-gate` | `document-round-trip` MOD |
| — | `add-metadata-units-and-language-detection` | `document-round-trip` MOD · `book-import` **NEW** |

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

**`fix-document-round-trip-corpus-defects` narrows how FB2 *classifies* a malformed translated fragment (a bare
`&` or `<` in target text) to `ErrorCode.validation` instead of `internal`, but does not change what happens to
the character itself.** Deciding how such a literal should be *escaped* or *masked* is change 5's, per its own
Non-Goals; the two must not be conflated later — one fixes error classification at the write boundary, the other
fixes what the model is allowed to hand back in the first place.

**`fix-document-round-trip-corpus-defects` deliberately left four findings unfixed, each real and each evidenced in
`docs/implementation_plan/notes-corpus-verification.md`.** They are listed separately below rather than as one
paragraph so each can be found on its own; leaving them unrecorded is how they get rediscovered from scratch.

**Deferred finding — the reader accepts EPUBs the writer can never export.** `EpubReader` imposes no `mimetype`
requirement; `EpubWriter` requires one, because DD-43 mandates mimetype-first-and-STORED on output.
`aliceDynamic.epub` has 68 entries and none named `mimetype`, so it opens with 13 units and 866 segments and then
refuses every write — including a zero-edit one — with `ErrorCode.validation`. The refusal is *correct*: the writer
cannot fabricate a conformant container. The defect is the **asymmetry** — the app accepts a book for translation it
can never deliver, and the user finds out only after translating. No requirement covers reader/writer acceptance
parity, so this needs a product decision: fail fast at open, or synthesize a container entry on write.

**Deferred finding — there is no `close()` or eviction seam on the open-document registries.** All four
`Open*Registry` classes are `@Singleton` over a bare `ConcurrentHashMap` with no `remove`/`evict`/`close`, and
`DocumentPort` exposes only `open` and `write`. Per open EPUB the registry retains every zip entry's *inflated*
bytes plus a full jsoup DOM per spine document, for the process lifetime — so **every completed translation job will
leak its book**, not merely multi-book browsing. Inert while `:pipeline` and `:persistence` are stubs, and cheapest
to add before `:pipeline` has callers. No requirement covers document lifetime.

**Deferred finding — `EpubWriter` mutates the registry-held tree in place.** `writeSegmentsBack` mutates
`parsed.spineTreesByHandleId()`, the live tree in the registry rather than a copy, so a second `write()` on one
document id works from the already-mutated tree. Latent in production today and no concurrent-open/write test exists
either way — but it was *not* latent for the corpus verification, where it invalidated the first harness design and
forced every write probe to open a fresh service and a fresh document. Advisory for `:pipeline`'s retry path.

**Deferred finding — one 26,306-character segment, and 15 books over 5,000.** Verified as single genuine unbroken
`<p>` elements, so `:document` is behaving correctly and splitting them would violate the skeleton invariant. The
spec already anticipates it (`02_GLOSSARY.md`: "oversized single paragraphs are sentence-split only on overflow",
FR-ALGO-02/DD-44) but `:pipeline` is a stub, so the safety net does not exist. Against the settings' 512-token
minimum `num_ctx`, that one segment overflows by 15–20×. Belongs to the chunker, with this corpus as its proof case.

**New finding from the same change's own harness — the four writers disagree on what to do with target text the
resolved charset cannot represent, and nobody has decided which is right.** Found by the corpus verification, which
sets every segment's target to a marker plus its own source text and so exercises exactly this. Measured:

| format | behaviour on unrepresentable target text |
|---|---|
| TXT | **refuses** — `CorruptContainerException` → `ErrorCode.validation` (`TxtWriter.encode`) |
| FB2 | **silently switches the whole document to UTF-8** and re-declares the encoding (`Fb2Writer.serialize`) |
| Markdown | **silently substitutes `?`** and returns success |
| EPUB | **silently substitutes `?`** — `tree.outerHtml().getBytes(charset)` replaces unmappable characters |

Four formats, three policies, two of them silent. The corpus note records the TXT refusal as *correct* behaviour;
against the other three that judgement has never been made. So this is not "`MarkdownWriter` is missing a guard" —
adding `TxtWriter`'s guard there would cement one of three competing behaviours without a requirement to justify it,
and leave EPUB corrupting silently regardless.

`fix-document-round-trip-corpus-defects` deliberately left it alone for the same reason it left the reader/writer
acceptance asymmetry alone: it needs a **product decision** — refuse, transcode the document to UTF-8, or mask the
character — applied uniformly, then one requirement with a scenario per format. It is latent today because
`:pipeline` is a stub and nothing but a test yet calls `write` with target text; it stops being latent the moment
the first translation run exports a book whose source charset cannot hold the target language, which for a
`windows-1252` source translated into Ukrainian is every single segment.

**`add-metadata-units-and-language-detection` runs after change 5 and closes Stage B** (ADR-0023). It exists because
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

**Runs in parallel with Stage B**; depends only on Stage A. Reusable controls built against the mockup's own
**"Component library"** screen — widget-level, composed into no application screen yet. Screen composition is Stage D.

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

Depends on Stage B. Storage (8–9) and the provider stack (10–11) are independent of each other; both precede the
pipeline (12+). **Stage C is where the backend becomes complete** — see `#backend-complete-milestone` below.

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
