# ADR-0023 — Make "the whole backend runs against a stub provider" an explicit milestone, and add five interstitial changes to reach it

**Status:** accepted **Date:** 2026-08-05
**Deciders:** product owner, architect
**Amends:** ADR-0017 (delivery order — the stage grouping is unchanged; five interstitial changes are added inside it)

## Context and problem statement

ADR-0017 grouped 28 changes into five stages and `docs/implementation_plan/CHANGE_BACKLOG.md` named them. The stage
grouping came from the frozen `00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery`, which was authored as a
**UI-first phase list**: PHASE_09 is *"UI shell — … Projects/Import/Book Brief/Structure"* and PHASE_10 is
*"Translate & review UI — … Export"*. ADR-0017 reordered the stages but did not re-examine whether each individual
change was purely a UI change. It is not.

Reviewing the backlog before authoring change 3 surfaced a mismatch between the **module map**
(`01_MODULE_INVENTORY.md`, ~40 packages, every one owned) and the **change map** (`CHANGE_BACKLOG.md`, 29 changes).
Five concerns are FX-free backend code in the module map and land inside changes labelled as screens in the change map:

| Concern | Module map says | Change map says |
|---|---|---|
| Source-language detection (Lingua), `FR-IMPORT-03` | `:document/ua.bookloom.document.detect` | change 19, Stage D (`book-import`) |
| Book Brief model + persistence, `FR-BRIEF-01..07` (31 ids) | `:persistence` `projects.brief_json` | change 19, Stage D (`book-brief`) |
| Import orchestration, `FR-IMPORT-01/04/05/08` | `:document` + `:persistence` | change 19, Stage D |
| Export orchestration + artifact validation, `FR-EXPORT-03/05/06` | `:document` + `:pipeline` | change 23, Stage D (`export`) |
| Job lifecycle (`TranslationEngineImpl`, `JobHandle`) | `:pipeline/ua.bookloom.pipeline` | no change owns it; absorbed into 12–13 by implication |

Four further gaps follow from the same cause:

1. **The `:api` contract floor is landed piecemeal** — each port arrives with the change that implements it
   (`DocumentPort` at 3, repository ports at 8, `Provider` at 10, `TranslationEngine` never explicitly). Stage B′ is
   declared parallel with Stage B, but a UI control library has no port to build against until Stage C is well under
   way. `TranslationEngine` appears in `02_MODULES_AND_LAYERING.md` and the module inventory but in no seam row and no
   backlog entry.
2. **Change 3's own deferrals point at nothing.** It ships the `METADATA_TITLE`/`METADATA_AUTHOR`/`FRONTMATTER_VALUE`/
   `ALT`/`NAV_LABEL` kinds in the enum and defers producing them, and defers `Document.detectedSourceLang`. No later
   change claims either. DD-47 requires both.
3. **The prompt layer is covered by dispersion.** `01_Product/12_PROMPT_CATALOG.md` specifies nine templates and an
   output contract; `:pipeline.prompt` is one package. `07_ROADMAP.md#execution-notes` assigns only *"13 draft; 16
   judge/repair/reflect"*, leaving brief-tone-setup, name-term pre-scan, rolling-summary and backward-revision
   scattered, and leaving the template registry, variable expansion and output contract unowned as a unit.
4. **Nothing is ever proven against a real model.** Every stage exit gate in `07_ROADMAP.md#stage-exit-gates` is
   WireMock or headless. DD-40 requires prompt evals against *"a real local model + embedding scorer"*; change 1 built
   the `promptEval` tag and the excluded Gradle task, but **no change builds the harness**, and no gate ever runs it.

The forcing question came from the product owner: *at what point does the whole pipeline exist and run — every stage
created, defined, logic in place, utilities in place — with only the model itself replaced by a stub?* Read against the
backlog, the answer today is "somewhere inside Stage D, if the screen changes happen to build it", which is not a
milestone anyone can plan against or verify.

## Decision drivers

- **The pipeline must be provable before it has a UI.** A backend defect found through a screen costs a UI change plus
  a backend change; found through a headless e2e test it costs one.
- **Stage B′ must be genuinely parallel.** `07_ROADMAP.md#stages` claims it is; without a contract floor it is not.
- **Seam discipline applies to every seam, not only F1.** `07_ROADMAP.md#forward-compatibility-seams` already requires
  a stage to establish its seams *before their consumers exist*. The `:api` port surface is a seam by that definition.
- **Change numbers are immutable.** They are cited in ADR-0017, ADR-0018, ADR-0019, in `07_ROADMAP.md`'s F1–F9 table,
  and in the archived change 1's `design.md`. Renumbering is not available (ADR-0021, design D5).
- **`openspec/specs/` must stay a ledger of product behaviour.** A change with no user-observable behaviour sets
  `skip_specs: true` rather than inventing a requirement (`.claude/rules/spec-authoring.md`).

## Considered options

- **Option A — Leave the backlog; fix the gaps at `/opsx:propose` time.**
- **Option B — Add five unnumbered interstitial changes, and name the stub-provider whole-book run as a milestone.**
- **Option C — Renumber the backlog into a backend-first sequence.**
- **Option D — Move the LLM module (10–11) after the pipeline, so the pipeline is built only against a stub.**

## Decision outcome

Chosen: **Option B**, because it repairs the mismatch without touching a single existing change number, and because
each inserted change has a single coherent claim that a green gate can judge.

Five interstitial changes are added. Each is **unnumbered**, exactly as `restructure-module-layout` is (ADR-0021,
design D5), and each states the numbered changes it sits between. The five stages, their dependencies, and all 28
existing numbers are unchanged.

| Interstitial | Position | Claim | Specs |
|---|---|---|---|
| `add-api-contract-floor-and-stubs` | after 2, parallel with 3 | Every `:api` port and domain record that change 3 does not own exists, plus in-memory stubs consumable from any module's tests | `skip_specs` |
| `add-metadata-units-and-language-detection` | after 5, closes Stage B | Metadata-unit segments (DD-47) are produced, nav/NCX labels are segments, and `detectedSourceLang` is populated by Lingua | `document-round-trip` MOD · `book-import` NEW |
| `add-prompt-catalog-and-output-contract` | after 12, before 13 | The nine templates, the variable-expansion mechanism, the JSON output contract, and the `promptEval` harness exist as one owned layer | `translation-pipeline` MOD |
| `add-project-lifecycle-and-orchestration` | after 16, closes Stage C | Import, project/job lifecycle, Book Brief persistence and export orchestration exist as FX-free services | `book-import` MOD · `book-brief` NEW · `export` NEW · `translation-pipeline` MOD |
| `add-stub-provider-whole-book-e2e` | after the above, Stage C exit | A small whole book imports, runs the complete pipeline against a canned-response stub `Provider`, resumes after an interrupt, and exports canonical-equal | `skip_specs` |

### The milestone

**Stage C's exit gate is restated** as the green gate of `add-stub-provider-whole-book-e2e`:

> A small book is imported from disk, parsed to skeleton + segments, chunked, context-assembled, drafted, QA-gated,
> judged, self-healed, consistency-passed, checkpointed, interrupted, resumed, and exported to a canonical-equal
> artifact — with the `Provider` port bound to a stub returning canned responses. Nothing in the pipeline is absent or
> faked except the model.

This is the point at which the backend is complete. Stage D wires screens to services that already work.

### Where the stub is legal, and where it is not

`.claude/rules/testing.md` holds two clauses that must both survive:

- The LLM **MUST** be exercised at the **WireMock HTTP seam** — *"Reject if: the LLM is tested by mocking the
  `Provider`/client instead of the WireMock HTTP seam."* This governs `:llm` and is unchanged: changes 10–11 prove both
  dialects against fake HTTP.
- **Pipeline e2e** is defined as *"a small whole book through the engine against a **stub/WireMock provider**"*. This
  governs `:pipeline` and is what the milestone uses.

The split is therefore already permitted by the rule, and no rule changes. What changes is that the stub `Provider`
becomes a **named deliverable** rather than an assumption made inside a test.

### Consequences

- **Positive:** the backend has a verifiable completion point, and it precedes every screen. Stage B′ becomes parallel
  in fact and not only on paper. `TranslationEngine` gains an owner. The `promptEval` harness gains an owner. Changes
  19, 21 and 23 shrink to what their names say — screens.
- **Positive:** change 3 is untouched and does not grow. Its single-claim, single-gate shape is what makes a
  round-trip regression unambiguous, and widening it would have cost that.
- **Negative:** the `:api` contract floor is designed before most of it is implemented, which is how ports come out
  wrong. Mitigated by `docs/specification/` being frozen and already specifying these shapes
  (`02_MODULES_AND_LAYERING.md#module-api`, `05_PIPELINE_ENGINE.md`, `06_DATA_MODEL_SQLITE.md#projects`) — the floor
  transcribes settled contracts rather than inventing them. Where a port genuinely cannot be settled, it is omitted
  from the floor and left to its implementing change, and the floor's proposal must say which and why.
- **Negative:** there are now **34 changes in 28 numbered slots**. A reader who counts rows and gets 34 has counted
  correctly; `CHANGE_BACKLOG.md#how-to-use-this` already requires filtering rather than reading numbers as positions.
- **Neutral:** two capability introductions move. `book-import` is introduced by `add-metadata-units-and-language-detection`
  (Stage B) rather than change 19, and `export` by `add-project-lifecycle-and-orchestration` (Stage C) rather than
  change 23; both become MOD at their old positions. `book-brief` moves from change 19 to the orchestration change.
  The sixteen-name capability map is unchanged — no capability is added, so no further ADR is required
  (`.claude/rules/spec-authoring.md`).
- **Neutral:** Option D was rejected but its concern is honoured. `:llm` stays at 10–11, before the pipeline, so the
  pipeline is written against a port whose real implementation already exists and whose HTTP semantics are already
  proven. The stub is a test binding, never a substitute for building `:llm`.

## Pros and cons of the options

### Option A — Leave the backlog; fix gaps at propose time

- Good: zero planning cost now; every change is authored against the codebase as it actually is, which is the
  backlog's stated philosophy.
- Bad: it is the current plan, and it is what produced these gaps. "It will be caught at propose time" is how change
  19 becomes a single change covering two capabilities, 50 requirements and a language detector.
- Bad: it leaves Stage C's exit gate unachievable as written — a whole-book run needs a `Project` and a `BookBrief`
  that Stage D introduces — so either the gate is quietly not met or changes 12–13 invent the types, which is the
  "later stages reshape earlier code" the seam table forbids.

### Option B — Five unnumbered interstitials plus an explicit milestone *(chosen)*

- Good: no number moves, so no citation in an accepted ADR or an archived change breaks.
- Good: each insertion has one claim and one gate; none is a grab-bag.
- Bad: the backlog grows by five entries before any of them is authored, in a document that says entries are
  "deliberately unplanned". Accepted: this ADR adds names and positions only, which is exactly what the backlog holds.

### Option C — Renumber into a backend-first sequence

- Good: the cleanest resulting document.
- Bad: forbidden. ADR-0017/0018/0019 and archived change 1 cite numbers; accepted ADRs are immutable and an archived
  change is never reopened, so a renumber leaves the corpus permanently self-contradicting (ADR-0021, design D5).

### Option D — Move `:llm` after the pipeline

- Good: maximally honours "build the pipeline against a stub".
- Bad: the pipeline would never meet real HTTP semantics — tolerant parsing, `<think>` stripping, repair retry,
  `Retry-After`, structured-output downgrade — until Stage D, where each is a cross-stage bug rather than a one-change
  fix. The response-handling contract (ADR-0013) is precisely the kind of thing a stub cannot exercise.

## Links

- Design decisions: DD-07 (skeleton/segment), DD-40 (local prompt evals), DD-43 (canonical-equal round trip),
  DD-45 (review-mode dial owns τ), DD-47 (metadata units)
- Amends: `docs/adr/ADR-0017-infrastructure-first-delivery-order.md`
- Related: `docs/adr/ADR-0013-response-handling-contract.md`, `docs/adr/ADR-0018-requirement-id-canonical-form.md`,
  `docs/adr/ADR-0021-modules-under-a-single-parent-directory.md`
- Spec clauses: `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md#staged-delivery`,
  `docs/specification/01_Product/12_PROMPT_CATALOG.md`, `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
  `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`, `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md`
- Plan documents updated: `docs/implementation_plan/CHANGE_BACKLOG.md`, `docs/implementation_plan/07_ROADMAP.md`
- OpenSpec changes: none authored yet; `add-document-skeleton-and-epub-roundtrip` is unaffected by this ADR
