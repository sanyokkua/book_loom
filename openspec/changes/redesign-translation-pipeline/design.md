# Design: redesign the translation pipeline

## Context

`proposal.md` says why. The delta specs under `specs/` say what each capability must do; this file says how, with the
concrete types, packages, Gradle modules and build groups. Paths are relative to
`modules/<module>/src/main/java/ua/bookloom/<module>/` unless a full path is given. The numbers the research measured
are quoted as evidence; every number it did not measure is a named constant or a data value here, never a requirement.

### What exists today

**Context building (`:pipeline`).** About 25 classes build a prompt's context in six selectors and about fifteen render
sites, each its own way:
- `context/` — `ContextPackage`, `ContextPackageAssembler` (with `replay`), `ContextInputs`, `ContextBudget`,
  `DynamicFit`, `ContextSection`, `InjectedTerms`, `InjectedCharacters`, `InjectedLexicon`, `InjectedTerm`,
  `LexiconFilter`;
- `run/` — `ChunkContext`, `ChunkBudget`, `BatchFit` (`capPairs`, `capNext`, `lean`), `RelevantPairs`,
  `PrecedingTargets`, `PromptRequests` (`prepared`, `batchRequest`), `RunWindow`;
- `prompt/` — `DraftContext`, `DraftPromptBuilder` (token and no-token system variants), `CallFrame`, `StyleSheet`,
  `LanguageRules` (reads `BOOKLOOM_EVAL_RULES`), `LanguageFile`, `PromptTemplates`, `PromptSections`;
- `batch/` — `BatchContext`, `BatchContexts`, `BatchPromptBuilder`, `KeyTermAsks`, `BatchDrafter` (`MAX_SIZE = 16`);
- `reviewer/BookReviewerSheets`, `revision/NeighbourPrompt`, `revision/BookContext`.
Repairs (`heal/DirectedFix`) see no glossary. `prompt/ChatRequests` stamps `chunk/TokenBudget.EFFECTIVE_CONTEXT`
(16,384) on every request; run calls are resized by `run/JobModelCalls`, preparation and export calls are not.
`setup/BookFacts` is a record of what the book says about itself (title, author, year, publisher, description).

**Quality loop.** `heal/` holds the draft decision: `SegmentHealer` (reviewer answer, then 1–3 directed-fix rounds
through `RoundRunner`, `RoundEvaluator`, `RoundOutcome`, `RoundStep`, `RoundProgress`, `Resumption`), `SoftFix` with
`SoftFindings`, `QualityLoop` (the reviewer pass), `ReviewResolver`, `ReviewJudge`, `EditRegression`,
`GuessedGenders`. `reviewer/` (23 files) is the per-chunk reviewer with code-verified edits (`EditApplier`,
`EditVerifier`, `EditGuards`). `dial/DialParameters` maps the dial to `precedingTargets`, `repairRounds` (Fast 1),
`reviewPasses` (0/1/2), `backwardRevision`, `llmSummary` (Max only) and `chunkCap` (8/8/2; Manual 1).

**Revision and export.** `revision/` (23 files) is Max's backward revision and the export "last check":
`ConsistencyPass`, `NeighbourRevision`, `GenderRevision`, `RetryPass`, `RevisionCall`, `PassChecks`, `RevisionGuards`,
`ParagraphFingerprint`, plus the parts that survive: `TermSweep` (no model call), `RevisionWriter`, `RevisionBase`,
`UnchangedReply`, `DeferralRegister`, `GenderWaits`. `api: pipeline/JobStage` is `PREP, TRANSLATE, REVISE`. Export
creates a model when `ExportRequest.consistencyPass` is on (`AppSetting.LAST_CHECK`).

**Story memory.** `memory/RollingSummaryKeeper` and `api: project/RollingSummary` (one per project);
`memory/SummaryModelCall` already writes one chapter's summary at Max; `ui: state/SceneSummary` shows it.

**Glossary.** `api: project/GlossaryEntry` is one entry per written form (`aliases`, a `locked` boolean,
`TargetOrigin {PERSON, SUGGESTED}`, `GenderOrigin {PERSON, VERIFIED, SUGGESTED}`); `glossary/LockRule` refuses
`locked` where names inflect; `glossary/PronounGender` reads pronouns after exact mentions; `run/NarratorStage` counts
first-person past forms on decided targets after the run. Stress marks are never stripped.

**Language data.** `pipeline/src/main/resources/ua/bookloom/pipeline/prompt/languages/` holds 13 `<lang>.properties`
files (cs de en es fr generic hr pl pt ru sk sl uk) and 16 flat `pairs/<src>-<tgt>.properties`. `uk.properties` has
`foreignWords` (12 Russian words, read by `prompt/ForeignWords` and `checks/ForeignWordCheck`) and `forbiddenLetters`;
`ru.properties` has neither. The key `functionWords` in uk and ru means mood and polarity particles, read by
`heal/ReviewResolver` and `context/LexiconFilter`. `glossary/` resources hold stop words (de en es fr pl uk), titles
(en) and `given-names-{en,ru,uk}.txt`.

**Providers and models (`:llm`).** Two transports, `api: llm/ProviderKind {OLLAMA, OPENAI_COMPATIBLE}`, each one client
through `provider/ProviderClientFactory`. `GatedChatModel` applies model settings, retries transport errors, lowers the
cap to 3/4 after a timeout and holds the single permit of `gate/InferenceGate` (`run` only). `http/HttpErrorMapper`
maps by status: every 5xx is `upstream`; a 400/422 is `validation` unless the overflow pattern matches, and that
pattern needs "context" before the verb. `client/openai/OpenAiRequestMapper` builds the OpenAI body;
`client/ollama/OllamaReasoning` adds room for gpt-oss by name; `provider/ReasoningHeadroom` adds a flat 1,024;
`response/ReplySanitizer` strips reasoning tags; `provider/CapabilityRejections` misses "does not support thinking".
`capability/ContextLengthReader` and `ContextLengthProbe` read `/api/show` for the context length only.
`prompt/ReasoningPlan` (`BOOKLOOM_REASONING`) and `prompt/SamplingMode` (`BOOKLOOM_SAMPLING`) are global switches;
`AppSetting.REASONING` (`run.reasoning`) and `AppSetting.SAMPLING` (`run.sampling`) store the Generation tab's choice.
`ui: state/ModelAdvice` holds name rules for the pickers. `api: FailureReason` is an enum of reason tokens carried in
`AppError.details` through `SafeDetails`. `pipeline: CallKindTotals` adds call costs by kind and writes 0 for a
missing figure.

**Storage.** `persistence: sqlite/StoredShapes.ROOTS` (Project, SegmentRecord, GlossaryDocument, LexiconDocument,
RollingSummary, Deferral, RunRecord, TmEntry, ExportRecord, ProviderConfig, ModelSettings) is fingerprinted, enum
constants included; a changed fingerprint renames the file aside (ADR-0043).

**Evals.** `scripts/eval-matrix.sh`, `scripts/probe.sh`, `scripts/eval-score.py`, the `eval/` test package
(`MiniRunner`, `MiniScorer`, `SequenceEvalTest`, `BookRunner`, `EvalProvenance`, `JudgementRunner`, …) and
`PromptRequestsEquivalenceTest`. No judge, no dataset hash, a fixed noise band, en→uk only.

### Constraints

- **Layering.** `:api` (records, ports, no logging, no framework) ← `:util` ← services `:document`, `:llm`,
  `:persistence` ← `:pipeline` ← `:ui` ← `:app`. Only `:llm` holds HTTP code; only `:persistence` holds SQL; only
  `:ui`/`:app` see JavaFX (ArchUnit `fx-free-core`, `no-http-in-core-except-llm`).
- **Envelope.** Every port returns `Result<T>`; every failure is one `AppError` with an existing `ErrorCode` (fifteen,
  unchanged); details only from the safe allowlist; a failure is logged once where it is classified.
- **Code style.** Records for data; Lombok only on services; `@RequiredArgsConstructor(onConstructor_ = {@Inject})`;
  sealed types with exhaustive switches; no `synchronized`; named constants; a `package-info.java` with a purpose
  Javadoc in every new package; logging at every level per `.claude/rules/logging.md` (book text at TRACE only).
- **Inference.** Single-flight through the one `InferenceGate`; credentials are env-var references.
- **Offline.** ADR-0044 unchanged: no background call; detection and fact reads run only on a person's action.
- **Storage.** No migration before the first release (ADR-0043): a stored-shape change renames the database aside.
- **Book-neutral.** No names or text from real books in code, tests, prompts or fixtures; invented text only.
- **Generic for any pair.** Per-language data only through a file kind or key every language may have.

## Goals / Non-Goals

**Goals:**
- One context recipe (`ContextBuilder`) for every model call, so every call sees the same facts about the book.
- A run of three stages; a dial that means the same for every model; accepted text protected by one code gate.
- A book map keyed by base form, with origins and target states, where a model's guess is a hint, never a fact.
- Generic checks for any pair, with code repairs before any check counts.
- Each model and server handled from known facts: engine traits, a model profile, an error catalogue by meaning.
- An eval harness that decides every gated choice by paired, seeded, hashed runs.

**Non-Goals:**
- No word dictionary, no language-specific word lists, no equivalents list of names.
- No migration code; no carrying data across a reset.
- No new transport kind (Anthropic, Gemini), no OpenRouter routing controls, no translation-tuned models.
- No free-text per-model instruction field; no second proofreading model.
- No time targets: speed is measured and reported.
- No document-layer or command-line work beyond what the pipeline needs.

## Decisions

### D1 — Delivery: one change, one task group per step, arms gate steps

**Shape.** One OpenSpec change with 17 task groups, one per build step. This is the owner's deliberate exception to
the rule "a change is small, at most about ten tasks" (`AGENTS.md#how-work-is-planned`); the rule holds per group
instead (about ten tasks each). A group is the "task" of the AGENTS.md git rule.

| # | Group (branch suffix) | Needs | Builds | DB reset |
|---|---|---|---|---|
| 1 | `1-eval-harness-v2` | today's code | manifests, mini-en/mini-ru, seeded repeats, report v2, judge, baselines | no |
| 2 | `2-batch-format-decision` | 1 | arm 2; one batch format; batch cap = start size; `ReviewDesk.skip` gone | no |
| 3 | `3-provider-error-catalogue` | 1 | error rows by meaning, reason tokens, `CallLedger`, null timings | no |
| 4 | `4-language-data` | 1 | function-word files, ru data, rule keys, the `functionWords` split, replay | no |
| 5 | `5-provider-engines` | 3 | `ServerEngine`, detection, `EngineTraits`, LM Studio route, canaries | yes |
| 6 | `6-model-profiles` | 5, arm 2 | name reading, `ModelProfile`, reasoning, sampling, shaping, window; arms 3a/3b | yes |
| 7 | `7-lemma-book-map` | 4, 6 | lemma entries, origins, states, `NameForms`, validator, likely knowledge; arms 4a/4b | yes |
| 8 | `8-openrouter-provider` | 6 (may run beside 7, merged after it) | OpenRouter essentials | no |
| 9 | `9-model-advice` | 6, 8 | quant and language advice, base families; arm 8 | no |
| 10 | `10-stage-b-checks-one-repair` | 4, 6, 7, arm 3b | checks and levels, NORM, `NeverWorse`, the one repair | yes |
| 11 | `11-context-builder` | 10, 7, arm 2 loser gone, arms 4a/4b | `ContextBuilder`, the recently-present card; arms 5a/5c | yes |
| 12 | `12-chapter-summaries` | 11 | chapter summaries, the switch; arm 5b (after the summaries), arm 6 | yes |
| 13 | `13-stage-c-proofread` | 10, 7, 11, 12, arms 5a/5b/5c | the Proofread stage, deferrals at every dial; arm 7c | yes |
| 14 | `14-remove-old-passes` | 13 | arms 7a/7b first (they gate the removals); then the backward revision, the export pass and the reviewer removed; export with no model call; ADR status | yes |
| 15 | `15-models-tab-and-timings` | 5, 6, 8, 9, 14 | the rest of the Models tab, timings per engine, the Providers busy sentence, name filter, the RAM-tier hint | no |
| 16 | `16-redesign-closing-run` | 12, 14, 15 | closing measurements against `79ffede6` | no |
| 17 | `17-gate-and-hand-run` | 16 | full gate, hand check, AGENTS.md, module inventory | no |

**Branches.** Each group is built on `feature/redesign-translation-pipeline--<n>-<step>` (the suffix above), cut from
`feature/redesign-translation-pipeline` after the groups it needs are merged, and squash-merged back when its gate is
green. Nothing touches `master`. Parallel work is allowed only where files do not overlap: groups 2, 3 and 4 after 1;
group 4 beside 5 (no shared files); group 8 beside 7, merged after 7 and rebased where both only add bundle keys.
The pure check classes of group 10 that read no map data (`TerminalMarkCheck`, `NegationCount`, `SourceVariants`,
`LeadingDash`, `DialogueDash`, `LeadingNumber`) may start beside 6 and 7, merged only with group 10.

**Done per group.** The group's acceptance test is seen red, then green; `scripts/test-focused.sh` while coding; the
changed module's `check` before each commit; the whole gate `./gradlew clean build check spotlessCheck` at the group's
end with its tail pasted; a hand check in the app for visible behaviour; `scripts/ci-linux-ui.sh` before pushing a
changed screen or word; a push after each group. A gate red twice for the same cause goes to the owner.

**Arms.** An arm is a paired eval of a default against one alternative (D24). It runs on the group's branch, e4b
(`gemma4:e4b`) first and once on 26b (`gemma4:26b`) at the group's end, before the loser is deleted. Both variants exist
only while the arm runs, selected through the test-only `PromptVariants` binding (D24), never through an environment
variable or a setting. After the arm: the loser's code is deleted in the same group; if the alternative won, a named
task edits the delta requirement and this file's line; the result is written into D24's table.

### D2 — The run's three stages and the quality dial

- **Stages.** `api: pipeline/JobStage` becomes `PREP, TRANSLATE, PROOFREAD`: `REVISE` is renamed in group 13, not
  added beside. `StepKind.REVISION` and `CallKind.REVISION` become `PROOFREAD` (13); until group 14 deletes the export
  pass, its calls carry the `PROOFREAD` label too. Prepare inside a run is `run/PrepStage`, code only and short: the
  style sheet from the brief, and the code name scan with lemma grouping (D3) when the glossary is empty. Brief Suggest
  (`setup/BriefSuggester`) and "Prepare names automatically" (`glossary/NamesPassImpl`) stay person-triggered, outside
  the run, and Continue never resumes them. Translate is the chunk loop, ended by the final audit (D15); Proofread is
  `proofread/ProofreadStage` (D15). A run is COMPLETED only after Proofread; Continue resumes whichever stage has work
  (the resume delta), Prepare only when the run stopped before Prepare finished.
- **`run/StageRunner`.** Group 13 replaces the call to `revise` with `ProofreadStage.run` at every dial; the
  backward-revision classes stay compiled only for the export last check until group 14 deletes them, so two
  proofreading paths never run in one run.
- **Dial.** `api: pipeline/DialProfile` gains `repair(): RepairMode {MARKS_ONLY, ONE}` (group 10) and
  `proofreadCoverage(): ProofreadCoverage {NONE, MARKED_AND_DOUBTFUL, MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH}`
  (group 13). `dial/DialParameters` loses `repairRounds` and `chunkCap` (10), `precedingTargets` (11, the window's
  previous count comes from the profile tier), `llmSummary` (12) and `reviewPasses`, `backwardRevision` (14); the
  `DialProfile` interface loses the same method in the same group, and `ui: screen/GenerationQualityTable` stops reading
  it there. The dial never reads the model profile (owner decision D-17: Max means the same for every model).

| Dial | Translate | Proofread |
|---|---|---|
| Fast | draft + free checks that mark; no fixing call; one re-draft only for lost formatting marks; the final audit at the end | code only, no model call: term sweep, deferrals checked by code, the mark list sent to the review desk |
| Balanced | + the one repair; the final audit at the end | tiers 1 and 2 (marked and doubtful windows) |
| Max | as Balanced | tiers 1 and 2, then a second round over every story paragraph |

- **Visible.** The title bar and the progress dialog show `Prepare · Translate · Proofread`;
  `ui: screen/GenerationQualityTable` reads `DialProfile` and gains the "Extra model calls" row; the Start estimate
  counts Proofread steps from Start through `ProgressLedger` (D15).
- **Why.** Max's backward revision and the export pass cost hours with no measured gain; one stage inside the run,
  bounded by the never-worse gate, edits only what is wrong. *Alternative rejected:* keep a Max-only backward revision
  (owner decisions D-17, D-18).

### D3 — The book map: lemma entries, origin, target state

- **Records (`:api` `project/`, group 7).** `GlossaryEntry(id, projectId, term, forms, lemmaInferred, target,
  targetState, targetOrigin, type, placeKind, typeOrigin, gender, genderOrigin, genderSeedTried, noGenderEvidence,
  meaning)`: one canonical constructor and at most one convenience factory (today five telescoping constructors).
  New enums `Origin {USER, BOOK, RULE, LIKELY}`, `TargetState {SUGGESTED, PINNED, LOCKED}`, `PlaceKind {CITY, RIVER,
  COUNTRY, REGION}`; `TermType` gains `ORGANISATION, PRODUCT, MATERIAL`; `TargetOrigin` and `GenderOrigin` are deleted.
  `LexiconEntry` gains `forms`. `Narrator` keeps its own origin enum. Code that read the old shapes maps
  `locked → LOCKED`, `PERSON → USER`, `VERIFIED → BOOK`, `SUGGESTED → LIKELY`; stored data is reset (D26).
- **Storage.** The glossary stays one JSON document per project. `GlossaryDocument.findByKey`, `putAll`, `findByTerm`
  and `SqliteCheckpoint.applyGlossaryAdditions` become form-aware through one index built on load. The id stays
  `projectId:key(lemma)`.
- **Scan.** `glossary/FrequencyScan` and `CandidateTriage` read the stop words (`glossary/StopWords`: the source's
  function-word file plus the scan-only extras of `stopwords/<src>.txt`, D7) and `titles/<src>.txt`, then
  `NameForms.candidates` (D4) groups forms whose candidate sets meet. A lemma is accepted when its nominative is
  attested or two forms agree; the 3-use floor counts the group. Two competing lemmas are resolved per mention by clause
  evidence (verb ending after, preposition before, address term); a mention with none is "unsure" (matched to both, no
  gender vote). A competition that leaves the target unchanged collapses to the frequent class, `lemmaInferred`.
- **Origins.** Precedence USER > BOOK > RULE > LIKELY for automatic updates: a value from a lower origin never
  replaces a higher one by itself; the person (USER) can always edit any value. Only USER and BOOK
  genders are read by the gender findings, the gender-ending detectors and `NeverWorse`. `given-names-<lang>.txt` is a
  RULE source: one file kind any language may have, holding that language's own words; its absence means no RULE
  value. This is not a "language-specific word list" in the sense of owner decision D-16.
- **Gender evidence.** `glossary/PronounGender.Evidence` sums pronoun votes and source past-tense votes from the new
  `glossary/PastTenseReader` (patterns from the source file's `pastFemale`, `pastMale`, `pastMaleIrregular`,
  `shortAdjFemale/Male`; one reader for every language). Thresholds unchanged (3 votes at 80 %, strong 5 at 90 %).
- **Target state.** `LockRule` decides at acceptance: PINNED for an inflecting target (the stem is fixed, every
  generated form is allowed), LOCKED for a non-inflecting target or a kept identifier (exact text, token-substituted).
- **Recurring terms.** New `lexicon/FormGroups`: key = form minus its longest `nounEndings` ending, stem ≥
  `MIN_STEM_LETTERS` (4); representative from `BaseForms.of`; the 6-use floor (`MIN_GROUP_USES`) counts the group;
  `TermMatch.matches/occursIn` work on the group (replaces the surface-form count of `NameTally`).
- **Estimates.** `ModelSettings.secondsPerCall: Map<CallKind, MeasuredSpeed(seconds, MeasuredFrom from)>` replaces
  `measuredSecondsPerSegment` and `measuredFrom` (one stored speed, not two). It is written at the end of every names
  pass and run from the call ledger (D18); the Settings inference test writes the `DRAFT` entry with source `TEST`.
  `NamesEstimate` multiplies calls per kind by it, falling back to today's constants labelled "estimate"; the live
  `ProgressLedger` takes over during the pass. The Book Brief's time estimate (book-brief delta) is shown as measured
  when this model has a figure for every kind of step the run will take; otherwise as a range marked "estimate"
  computed from its `DRAFT` figure; with no figure at all, no time is shown.
- **Why.** One entry per written form made 381 false name swaps in one run. *Alternatives rejected:* keep aliases per
  surface form (the defect); a word dictionary (owner decision D-1).

### D4 — Name forms, stress marks and target validation

- **`StressMarks`** (`:util` `text/`, beside `GlossaryKeys`; pure JDK, no logging; group 7): NFD → drop U+0301 and
  U+0300 → NFC; U+00B4 only right after a Cyrillic vowel; U+00AD removed. Callers apply it when the target file sets
  `stripStressMarks=true` and log the count at DEBUG: the glossary and lexicon services on every inflow (model reply,
  CSV, edit, learned rendering; group 7), `typography/TypographyNormalizer` as its first step (10), and export output
  (10; translated segments only, a source-language fallback is written as it is). A guard test loads every stored
  target of such a language and finds no U+0301/U+0300.
- **`glossary/NameForms`** (new, pure, group 7): `candidates(form, lang)` inverts the source file's `nameParadigms`
  rows (`ending|gender: oblique endings`); `forms(target, gender, lang)` generates the target's case forms and vocative
  from the target file's `nameParadigms` and `nameAlternations` (`о/е → і` in a closed syllable, fleeting `е`). A
  language with no paradigm gets the identity table (source) or the target itself (target). Readers: the scan (D3),
  the `name-spelling` check (D12), `NeverWorse` veto 4 (D14), `TermSweep` for PINNED entries (D15), `WindowMap` (D8).
- **`glossary/TargetValidator`** (the extended `RenderingChecks`, one class): target letters (`forbiddenLetters`; no
  Latin unless `IdentityRule` keeps it), no stress marks, capitalisation as the source, nominative (not an oblique
  ending), word count, declared conventions (the Brief's choice; the letters one convention's values name fold when
  matching, read from the target file's `conventions` key — for uk, `hardG`'s values `г|ґ`; no code names a letter).
  It runs when a suggestion arrives and behind the new port method
  `GlossaryService.acceptSuggested(projectId) → Result<AcceptReport>` (new `:api` record: `accepted`, `rejected` with
  reasons). The Names & style Accept all calls it once; rejected rows go to
  `ui: control/ProgressDialog`'s Results view with Undo all.
- **Review flags** (computed, never stored, kept out of Accept all): an ending the target file's
  `nonNominativeEndings` or paradigm marks oblique, and a word count unlike the term's. A target file without these
  keys gives no flag.
- **Why.** The checks must accept every correct inflection of a pinned name and refuse a respelling.
  *Alternatives rejected:* a ru→uk equivalents list or `NameTransfer` letter rules (owner decision D-15: pair-specific
  code); NFC-first stripping (misses the grave forms).

### D5 — Likely model knowledge and target suggestions under the name policy

- **Name knowledge (group 7).** A new names-pass stage after the readings: `PromptName.NAME_KNOWLEDGE`,
  `CallKind.NAME_KNOWLEDGE`, templates `name-knowledge.{system,user}.prompt`, batches of `NAME_KNOWLEDGE_BATCH` (40)
  bare names, no windows. Schema `{"items":[{"term","gender","category","confidence"}]}` with no "unknown" gender: the
  model gives its most probable value (owner decision D-11). The new `glossary/NameKnowledgeReplies` keeps an item only
  if it echoes the term; every kept answer enters as LIKELY with its confidence; below `LOW_CONFIDENCE` (0.7) it is
  shown "likely, low confidence"; a missing item is asked once more in the next batch.
- **Never enforced.** LIKELY and RULE values are shown, fill the Start question's pre-selection
  (`UnknownGenders`, lowest confidence first) and feed nothing that checks. RULE against LIKELY keeps the RULE value
  and shows the disagreement ("rule: male · model: female").
- **In prompts.** Default: the map line shows the name only for a LIKELY gender (H-B). Arm 4b (D24) may adopt the hint
  line "Name — male (likely)" (H-A); arm 4a may set the profile quirk `NO_KNOWLEDGE_HINT` for a model class whose bare
  answers are confidently wrong, so no likely value of that class reaches any prompt. Both forms exist only during
  group 7's arms.
- **Target suggestions.** `suggest-targets.*` is rewritten: the lemma's nominative, batches of `SUGGEST_BATCH` (20),
  the Brief's `NamePolicy` stated (`TRANSLITERATE` default, `TRANSLATE` shown as "translate or adapt names",
  `KEEP_ORIGINAL`), the pair file's `nameExample.<policy>` when present, every reply through `TargetValidator`. The
  target origin is LIKELY until accepted.
- **Meaning-bearing names.** `GlossaryEntry.meaning` holds the meaning as a note; `term-reading` gains an
  `explainsName` field that cites a window where the book explains or puns on the name; only then is the person asked
  (transcribe / translate / transcribe and gloss at first use). Never decided mid-run.
- **Narrator.** `brief-synthesis` returns its most probable narrator, stored as `Narrator.GenderOrigin.SUGGESTED` and
  shown "likely". `PastTenseReader` counts the source's first-person past forms in narration (outside quotes) in
  Prepare; over the bar it is `VERIFIED`. `NarratorStage` stays for sources with no gendered past (`INFERRED`).
- **Why.** A guess enforced as a fact spread one mistake over dozens of paragraphs. *Alternatives rejected:* an
  "unknown" answer (owner decision D-11); a confidence floor that drops answers (leaves fields empty).

### D6 — Brief policy keys, declared conventions and story shape

- **Record (`:api` `project/BookBrief`).** Group 7 adds `apostrophe` (String, one character), `quoteStyle: QuoteStyle`
  (new `:api` record: the opening and closing marks of the outer and the inner quote), `defaultAddress:
  Address(strangers, family)` with `AddressForm {INFORMAL, FORMAL}`, `conventions: Map<String, String>`, and `likely:
  Set<BriefField>` (the fields the analysis filled that the person has not confirmed; an edit removes the field from
  the set). Group 12 adds `storyShape: @Nullable StoryShape {ONE_STORY, SEPARATE_STORIES}` (null = no analysis ran)
  and `chapterSummaries: @Nullable Boolean` (null = follow the story shape; true or false = forced by the person).
- **Defaults come from the target file.** A row appears on the Book Brief only when the target file declares it: the
  address row needs `informalAddress` and `formalAddress`; the apostrophe and quote rows offer the target's own
  characters; `conventions=` declares choices with values and default (uk: `hardG` = `г` by default, or `ґ`; owner
  decision D-14). An English target shows no extra rows.
- **Readers.** `prompt/StyleSheet.from(brief)` writes the keys and the narrator line (7); NORM and the checks read
  quote style, apostrophe and address from group 10, which also adds `run/BriefKeyRecheck`: a changed key re-normalises
  and re-checks by code the stored segments it affects, on the writer thread, and marks what now fails.
- **Story shape.** `brief-synthesis` answers `storyShape` with one quote from each of two sample windows (likely);
  one story suggests the switch on, separate stories off; with no analysis the switch is on. The switch follows
  `BriefLock` (editable whenever no translation is at work); a change applies from the next Continue.
- **Out of scope.** Title of address and foreign-word treatment in the Brief.
- **Why.** Conventions belong to a language, not to code. *Alternative rejected:* a uk-only `HardG` enum (against the
  standing "generic for any pair" principle).

### D7 — Language data: function-word files and rule keys for every language

- **Group 4 ships data and loaders only** (resources in `:pipeline`; no stored shape changes).
- **Function words.** New file kind `pipeline/src/main/resources/ua/bookloom/pipeline/checks/function-words/<lang>.txt`
  for every catalogue language one open source covers (at least en uk ru de es fr pl cs sk hr sl pt), trimmed to
  function words, with an `ATTRIBUTION` file. The first task records the licence check (stopwords-iso, MIT, or
  Lucene's analyzer stop sets, Apache-2.0) before any file is copied. Words both languages of a pair use (uk `просто`,
  `тебе`, `через`, `того`, `себе`, `перед`) go into the target's own file, because they are target words.
- **`checks/FunctionWords`** (new): loads a file into an immutable set; `sourceOnly(src, tgt)` = the source's set minus
  the target's, built once per pair at run start; a missing file gives an empty set, so the check is off for that pair
  (one DEBUG line). No curated pair list (`sourceOnlyWords`, `russisms`, `weakSourceWords`) is built.
- **One function-word list per language.** The name scan's stop words do not get a second copy:
  `glossary/StopWords` reads the language's function-word file plus the scan-only extras that stay in
  `glossary/stopwords/<lang>.txt` (interjections, the `alone/` spelled numbers); the words now in the function-word file
  are deleted from those files, and no `glossary/stopwords/ru.txt` is written (the ru scan needs only the function-word
  file and its titles). A function word that `given-names-<lang>.txt` also lists (en `May`, `Will`) is not a stop word,
  so it is left to the lower-case-share rule as today.
- **Split.** The existing key `functionWords` (negations and mood particles in one list, uk
  `би б не ні ніколи ніде ніхто нічого ж же та`) is split in every file into `negationWords` (the negations) and
  `moodWords` (the rest); `prompt/LanguageRules`, `heal/ReviewResolver` and `context/LexiconFilter` read their union
  where they read `functionWords` today, and the negation count (D12) reads `negationWords`. No word sits in two keys;
  "function words" now means only the new file kind.
- **New keys** (any language may set them): `nameParadigms`, `nameAlternations`, `nounEndings`, `pastFemale`,
  `pastMale`, `pastMaleIrregular`, `shortAdjFemale`, `shortAdjMale`, `firstPersonPronouns`, `clippedVocative`,
  `addressPositions`, `femSurnameMap`, `stripStressMarks`, `conventions`, `nonNominativeEndings`,
  `readingExample` (and `negationWords`, `moodWords` from the split); ru gains `forbiddenLetters=іїєґ`,
  `informalAddress`, `formalAddress`, `honorifics`. New name-scan files `glossary/titles/ru.txt`,
  `glossary/titles/uk.txt`. Pair files (flat
  `pairs/<src>-<tgt>.properties`) may set `nameExample.<policy>`, the pair examples, `outputRatio` and
  `negationCounts`; en-uk and ru-uk get them first (the eval pairs). Later groups add `proofreadInstruction` and
  `proofreadRules` (13) and `dialogue-dash` in `typography-rules.properties` (10).
- **Kept.** `given-names-<lang>.txt`; the pair files' prompt `pitfalls` (prompt guidance that no check reads); the
  refusal phrase lists (they detect the model's own refusal, not book residue). **Removed** in group 10: `foreignWords`
  in `uk.properties`, `prompt/ForeignWords`, `checks/ForeignWordCheck` (owner decision D-27).
- **Tests.** `LanguageFilesTest` loads every file and every key's shape; for every pair whose two languages have files,
  the computed source-only set holds no word of the target's file; the homograph test (ru→uk set never holds
  `просто`/`тебе`/`через`) is the group's acceptance test. Paradigm round trips live with `NameForms` in group 7.
- **Replay.** A local replay of the computed ru→uk set on the owner's real ru→uk book (never committed) measures its
  precision against hand-tiered residue; the result is recorded in the group and fixes the residue level (D12).
- **Why.** Owner decision D-16: no language-specific word lists; residue is caught by the target alphabet, computed
  function words and the proofread. *Alternative rejected:* the 174-word curated ru list (precision 1.0, recall
  16–18 %, but pair-specific).

### D8 — One context builder: block plan, window and budgets

- **Types (`pipeline.context`, group 11).** `ContextBuilder` (service) with
  `Result<PromptPlan> plan(CallKind kind, Focus focus, BookMap map, RunFrame frame, ModelProfile profile)`;
  records `PromptPlan(List<Block> blocks, int promptTokens, int maxOutputTokens, int contextSize)`,
  `Block(String slot, Role role, Stability stability, String text)` with `Role {SYSTEM, USER}` and
  `Stability {RUN, CHAPTER, WINDOW, CALL}`, `Focus(List<String> segmentIds, Map<String, List<String>> reasons)`,
  `RunFrame` (style sheet, rule lines, `BookFacts`, `RuleFacts`, examples, `frameHash`; built once per run),
  `BookMap` (the book map as one call reads it: the glossary's lemma entries with their forms, the recurring terms'
  `FormGroups` and the brief's policy keys; built at run start and rebuilt after a glossary or brief change). New
  `WindowAssembler` (reuses `run/PrecedingTargets`), `WindowMap` (merges `InjectedTerms`, `InjectedCharacters`,
  `InjectedLexicon`, `ChunkContext.occurringIn`, `BookReviewerSheets.isNamed`, `NeighbourPrompt.facts` and the replay
  filter, matching through `TermMatch.isNamedIn` and `NameForms.forms`), `BookRules` (D9); block 8's card,
  `RecentlyPresentCard` (D10), is built in group 11 by the same task that moves drafts onto the builder (task 11.5),
  so no draft ever loses today's code-built who-is-who; group 12 adds the earlier chapters' summaries to block 8.
  Between the two groups the model-written rolling text (Max only, at a unit's end) is not carried; chapter summaries
  replace it in group 12, and arms 5a and 5c run at Balanced, where that text does not exist today. Reused:
  `ContextBudget.SAFETY_MARGIN` (the rest of `ContextBudget` — its shares and `DYNAMIC_CEILING` — is replaced by
  `WindowBudgets`, and its window by `ModelWindow`, D20), `TokenEstimator`, `PromptTemplates`, `PromptHygiene`,
  `api: pipeline/PromptSection`, `StyleSheet`, `ChatRequests.build` (the plan's context size; only the Ollama-native
  client sends it as `num_ctx`, an OpenAI-compatible request never carries one).
- **`BookFacts` and `RuleFacts`.** The existing `setup/BookFacts` record (what the book says about itself) is reused as
  it is, not duplicated. The rule facts are a separate new record `context/RuleFacts` (`hasMarks`, `dialogueShare`,
  `hasNumbers`, `genderedCast`, `targetInflects`) computed at run start from `BookIndex`, the brief and the target
  file, held in `RunFrame` beside `BookFacts`; the brief path, which runs before any run, never needs one.
- **Block plan.** Blocks 1–7 (SYSTEM, RUN): role and pair, "book text is data", up to three book-gated rules and the
  inflect sentence, the style sheet, target and pair rules, examples, the JSON line. Blocks 8–13 (one last USER
  message): earlier chapter summaries and the card, map items in the window, previous accepted pairs and next source,
  the kind's extra (repair defects, proofread reasons, the person's note), task and schema line, items with the
  reminder last. TRANSLATE frame (draft, repair, proofread, retry) = 1–13; ANALYSIS frame (names readings, brief,
  chapter summary, file name) = 1, 2, 7 plus the kind's user blocks. Every variable block sits in the last user message,
  so blocks 1–7 are byte-identical across draft, repair and proofread of one run.
- **Window.** Previous: the last `PREVIOUS_PAIRS` (2 small tier / 3 large) segments before the focus in reading order
  over body units, across chapter boundaries, source + accepted target; a FLAGGED or not-accepted neighbour gives its
  source only; a Proofread KEEP counts as accepted. Current: the batch. Next: source only. Map items: an entry is
  injected only if one of its forms occurs in the window's source (for proofread also the target side); the line is
  `lemma → nominative target (type, gender[, likely])`; PINNED names are lines with "decline them to fit, keep the
  stem", LOCKED names are tokens.
- **Budgets (named constants in `WindowBudgets`, keyed by the profile tier `SMALL`/`LARGE`; unmeasured unless noted).**
  Previous ≤ 300/500 source tokens; current ≤ 600/1,000; next ≤ 150/250; earlier summaries ≤ 600/1,200; whole prompt ≤
  3,500/6,000. Drop order when over: next → oldest summaries down to the newest → previous 3 → 1 → map items with the
  fewest occurrences → the last summary. Never dropped: the current part, pinned or locked names in the window, a
  speaker's gender in the window. Estimator: 3.3 characters per token for Cyrillic, 4.1 for Latin (measured), ×1.15,
  the language passed for every message, plus the profile's `templateOverhead`; measured calibration is logged, not
  applied.
- **Output reserve.** `maxOutputTokens = outputRatio·S + OUTPUT_PER_ITEM·n` (`outputRatio` from the pair file, default
  `DEFAULT_OUTPUT_RATIO` 1.5, measured on en→uk; `OUTPUT_PER_ITEM` 16); for a profile whose reasoning is ALWAYS or
  UNSWITCHABLE, `max(REASONING_RESERVE_MIN 2048, 4·S + 35·n)`; the hard cap ≈ 3.6·S stays as a runaway guard; ANALYSIS
  kinds keep their own caps (chapter summary 200).
- **Snapshot.** `ContextSnapshot(frameHash, sections, chapterSummaryKeys)` holds only blocks 8–13 that went out; blocks
  1–7 are stored once per project for each distinct frame, as a project document `promptFrame:<hash>` (new `DocKind`,
  read through `persistence: sqlite/ProjectDocs`). Retry and Proofread rebuild from the current map and stored targets,
  never replay (owner decision D-19); `review/RetryDraft` and `SnapshotTerms` stop reading the stored glossary; the
  desk's "What the model saw" shows the rebuilt context for a retry.
- **Moves.** Draft, batch and repair (`DraftRequests`, `PromptRequests`, `DraftPromptBuilder`, `BatchPromptBuilder`)
  and the ANALYSIS kinds (`SetupAssistantImpl`, `GlossaryModelScans`, file name) go through the builder; a single
  draft is a batch of one (`DraftSchema`, the strict `DraftReplyParser` and `draft.*` merge into `translate.*`);
  `structural-repair.user` becomes `format-reask.user`. `PromptRequestsEquivalenceTest` covers every kind.
- **Why.** Fifteen render sites drifted apart; repairs saw no names. *Alternatives rejected:* shared helpers under
  the per-kind assemblers (keeps the drift); the whole language file in every call (noise and tokens).

### D9 — Rules per book, the reminder and pair examples

- **`context/BookRules`** chooses rule lines once per run from `RuleFacts`: placeholder rule iff `hasMarks`;
  dialogue and address iff `dialogueShare ≥ DIALOGUE_SHARE_MIN` (0.05, unmeasured); numbers iff `hasNumbers`; gender
  agreement iff `genderedCast` (a stated narrator gender or a gendered character, and a target whose file sets gendered
  past forms); inflect-glossary iff `targetInflects`. Wording comes from the target language file, never code.
- **Reminder.** One to three lines last in block 13: tokens named only when an item carries tokens; the dash only when
  an item is dialogue.
- **Examples.** Lookup: pair file examples → a target-only illustration from the target file → none; the six fixed
  en→uk template examples are removed; the profile quirk `EXAMPLES_OFF` drops block 6. Preparation prompts use the
  source file's `readingExample` (group 7). **Echo gate:** an item whose target shares a word 4-gram
  (`ECHO_NGRAM` 4) with an example target that its source lacks gets one re-ask, then is flagged.
- **Arms (group 11; 5b at the end of group 12).** All three are measured with the recently-present card in block 8,
  at Balanced. 5b (key-term asks) runs after the chapter summaries reach block 8, because their map lines bear on term
  consistency, its graded outcome. 5a: builder layout vs today's rule wording and order inside blocks 3–5. 5c: rules per book vs
  per window (rule lines chosen from the facts of the window's items, placed in the user tail so the system part stays
  fixed). The default is per book; a tie keeps per book (owner decisions D-9, D-28). If per window wins, a group-11
  task moves the selection into the user tail and edits the translation-pipeline requirement.
- **Why per book.** LM Studio (MLX) and llama-server reuse a run-constant prefix; Ollama MLX reuses nothing either
  way, so no speed gain is claimed on Ollama; per book stays there only by the tie rule.

### D10 — Chapter summaries and the recently-present card

- **Chapters (`project/ChapterBoundaries`, new, group 12).** From `StructureNode` over `UnitRole.BODY` units: the
  chapter level is the shallowest depth with at least two nodes (a single top node is looked through); a chapter is one
  node with all descendants; body text before the first node joins the first chapter; an EPUB spine file with no
  navigation entry continues the chapter before it; a chapter under `CHAPTER_MIN_TOKENS` (300) joins the next; a book
  with no such level is cut into pseudo-chapters of `PSEUDO_CHAPTER_TOKENS` (3,000) at paragraph ends. Key = `unitId`
  plus the first segment's position. `project/UnitChunks` ends a batch at a chapter boundary.
- **Call.** `memory/SummaryModelCall` grows into `memory/ChapterSummaryCall`: `PromptName.CHAPTER_SUMMARY`,
  `CallKind.CHAPTER_SUMMARY` (replaces `SUMMARY`), `chapter-summary.{system,user}.prompt` rewritten from `summary.*`,
  ANALYSIS frame; the finished chapter's source in, 2–3 sentences in the target language out, with the chapter's map
  lines (lemma → target, most frequent first). Chapters over `CHAPTER_PART_TOKENS` (2,500/4,500) are read in parts,
  each part given the previous part's summary. Reply `{"summary"}`, cap 200 tokens. Code gate: at most three whole
  sentences; a sentence with a forbidden letter or a source-only function word gets one re-ask, then is dropped; a
  sentence naming a map entry absent from the chapter is dropped. Renderings never come from a summary.
- **When.** Right after the chunk commit that finishes a chapter (every story segment decided), before the next draft,
  single-flight. Continue first writes every missing summary of a finished chapter (or one whose `sourceHash`
  changed). A failed call leaves the chapter without one (WARN) and is tried once more on the next Continue.
- **Store.** `api: project/ChapterSummary(projectId, chapterKey, unitId, firstSegment, segmentCount, text, sourceHash)`
  replaces `RollingSummary`; the port `api: persistence/SummaryRepository` is reshaped to save and list chapter
  summaries per project, with in-memory and SQLite adapters and contract tests. One transaction per summary. A renamed
  pinned or locked target is swapped into stored summaries by the term sweep's rule (whole word for LOCKED, generated
  forms for PINNED; D15). Summary text is logged at TRACE only.
- **Use.** Block 8 carries every earlier finished chapter's summary, oldest first, within its budget (newest kept);
  Proofread windows and desk retries get the summaries of chapters before the window's chapter; the snapshot stores
  keys only.
- **Card (group 11, D8).** `context/RecentlyPresentCard` (code, every dial, no model call): up to `CARD_ENTRIES` (5) entries seen in the last
  `CARD_PARAGRAPHS` (12) paragraphs of the current chapter before the window and absent from it, most recent first, plus
  the narrator line for a first-person chapter. Replaces `RollingSummaryKeeper`'s code who-is-who. Until group 12's
  chapter rule (`ChapterBoundaries`), the card's chapter is the body unit the focus sits in, the rolling summary's own reach.
- **Switch.** `BookBrief.chapterSummaries` (D6); off means no call and no summaries in block 8; the same at every dial.
- **Visible.** The Translating screen lists each summary as a row; `ui: state/LogKind.SUMMARY` (tag `sum`) now means a
  chapter summary was kept, and group 13 adds `PROOFREAD` (tag `proof`), so the activity log's seven kinds become
  eight. The card is not shown on the Translating screen: it is part of a call's prompt and is read in that call's
  detail (All calls…) and on the desk's "What the model saw". `ui: state/SceneSummary` and its line under the live
  detail are deleted in group 12 (the spec removes "Always show the scene summary under the live detail").
- **Removed.** Group 11: the rolling summary's place in the draft prompt (the card takes the code who-is-who, in the same task). Exception to the rule above: Max's model-written unit-end text has no replacement until group 12's chapter summaries; no arm runs at Max in between, so no measurement is biased.
  Group 12: `RollingSummaryKeeper`, the Max unit-end `SUMMARY` call, `summary.*`, `DialParameters.llmSummary`.
- **Why.** Owner decision D-10. *Alternatives rejected:* a per-chunk scene summary (about 380 calls per book);
  source-language summaries (they put source words next to the items and miss the target spellings).

### D11 — Batch format and batch size

- **Format (group 2).** Arm 2 grades JSON against markers on 60 segments. JSON is the expected winner and the default.
  The loser and the dual format are deleted with their code (`BatchFormat` and `BOOKLOOM_BATCH_FORMAT`, `BatchMarkers`,
  `MarkerReplies`, `draft-batch-markers.*`, `draft-batch-dual.*`, `BatchExamples.renderMarked/renderDual`, the
  `PromptName` constants and slots) and the off switches (`BatchNotesSwitch`, `BatchSchema.SCHEMA_WITH_NOTES`,
  `RelevantPairsSwitch`, `RelevantPairs`, `BatchRelevance`). The parser stays tolerant: unknown fields are ignored,
  `BatchEntries` salvages per id. If markers win, markers become the one format and the requirement, which is worded
  "each reply item is matched to its segment by id", still holds.
- **`terms`.** The batch `terms` field and `batch/KeyTermAsks` stay in production until arm 5b, which runs at the end
  of group 12, after the chapter summaries reach block 8 (main-thread decision 5). Group 11 builds the batch prompt with
  `terms` unchanged; arm 5b makes the flat schema `{"items":[{"id","target"}]}` the default and keeps `terms` only as
  its variant; the loser is deleted in that task. The delta states the flat schema as the default.
- **Size.** `chunk/BatchSizeController` keeps halving on a wrong id, omission or merge and after three stalls, and
  grows by one after `CLEAN_STREAK_TO_GROW` (3) clean batches, never above its start size (main-thread decision 6).
  Group 2 sets the ceiling to the start (`DEFAULT_INITIAL_SIZE` 8 until then) and deletes `MAX_SIZE` 16. Group 10 makes
  the profile's `batchSize` the start and deletes `DialParameters.chunkCap` (8/8/2), `MANUAL_CHUNK_CAP` and the
  2,000-token chunk cap; the window's current-part budget (D8) is the only token limit; a batch also ends at a chapter
  boundary (12).
- **Missing ids.** Two or more missing → one format re-ask for those ids (not counted as the repair); an id never
  returned gets a first draft as a batch of one.
- **Why.** The ceiling of 16 was dead in effect and growth back to 8 dropped marks on small models (96 % of items keep
  every mark at 4 against 83 % at 8). *Alternative:* 8 on small models, adopted only by arm 3b (D24).

### D12 — Checks, levels and code repairs before checks

- **Levels.** New `qa/CheckLevel {BLOCK, REPAIR, NOTE}`. No second table: the existing `qa/CheckName` (every check,
  the evaluator's and the text checks' alike, which `qa/TextCheckGates` already maps from `FindingKind`) replaces its
  `boolean hardGate` with `CheckLevel level`, the level of the check's findings at HIGH severity. A check whose findings
  differ in level (numbers, names and gender) raises the lower ones at MEDIUM (REPAIR) or LOW (NOTE). One static reader,
  `qa/CheckLevels.of(QaFinding)`, turns a stored finding into its level; `BlockingFindings`, `AcceptanceRule`,
  `heal/SoftFindings` (its `FIXABLE` set goes), the outcome table (D13) and `NeverWorse` read only it. NORM is a tally,
  not a level.
- **NORM (group 10, `typography/`).** In `TypographyNormalizer` and `SpeechRepairs`, before any check: `StressMarks`
  first; new `LeadingDash` (source starts with a dash, target with a letter → prepend `— `; target starts with a quote
  → NOTE); new `DialogueDash` after `CommaBeforeDash` (line-start dash forms to `— `, glued dashes split, never inside a
  word or between digits); new `LeadingNumber` (a source's leading number or Roman numeral before `. ) :` restored);
  quotes and apostrophes from the Brief keys. `LeadingDash` and `DialogueDash` run only when the target sets
  `dialogue-dash=true` in `typography-rules.properties` (uk, ru).
- **Checks.**

| Check | Change | Level | Class |
|---|---|---|---|
| Target alphabet | the source-presence exemption only when source and target scripts differ | BLOCK | `qa/AlphabetCheck` |
| Function-word residue | hit count of the computed source-only set in the target (whole word, lower case, no source exemption; a one-letter word only before a lower-case word); the finding names the words; its own finding (`function-word-residue`), not the wrong-language-paragraph finding, whose "keep no machine target" rule stays as it is | REPAIR (NOTE if the replay's precision < 0.9) | `checks/LanguageIdentityCheck` raises it under its own new `CheckName.FUNCTION_WORD_RESIDUE`; the wrong-language-paragraph finding (`LANGUAGE_IDENTITY`, BLOCK) is unchanged: under 60 % target-script letters, or, when the scripts match, a source-only function-word share ≥ 30 % (`MAX_SOURCE_STOP_WORD_RATE`, now counted on the computed set) |
| Foreign script | a target word wholly in a script that is neither the target's nor Latin nor common, absent from the source; CJK and full-width punctuation | BLOCK | `checks/ScriptPurityCheck` (reports `script-purity`) |
| Numbers | leading number replaced → BLOCK; a missing 2-digit number → REPAIR; an extra number → NOTE; spelled-out numbers not compared | BLOCK / REPAIR / NOTE | `checks/NumberCheck` |
| Terminal mark | the paragraph's last mark class differs from the source's | REPAIR | new `checks/TerminalMarkCheck` (`terminal-mark`) |
| Address parity | source speech with only informal forms → no formal forms in the target, and the reverse; off without the keys | REPAIR | `checks/AddressMixCheck` source branch (`address-parity`) |
| Names and gender | `name-spelling` for a PINNED name whose target form is none of `NameForms.forms`; gender findings read USER/BOOK only | BLOCK / REPAIR | existing, reading `TargetState`, `Origin` |
| Negation | same-family pair with `negationWords` on both sides: source − target ≥ 2 | NOTE (audit doubt) | `audit/FinalAudit` + new pure `checks/NegationCount` |
| Same source, different targets | identical normalised sources (≥ 6 words) with two or more targets | NOTE (audit) | new `audit/SourceVariants` beside `NameVariants` |
| Listed foreign words | removed with `foreignWords` | — | `ForeignWordCheck`, `ForeignWords` deleted |

- **Residue level.** REPAIR is the shipped default (main-thread decision): any hit earns the one repair and leaves a
  proofread mark if it stays. The research's extra BLOCK tier for three or more hits is not adopted; a paragraph of at
  least 12 words left in the source language is blocked by the unchanged wrong-language-paragraph finding (the 60 %
  script share, or for a same-script pair the 30 % source-only share).
  If group 4's replay measures precision below 0.9, a group-10 task sets the level to NOTE and edits the requirement.
- **Wiring.** `FindingKind` gains `TERMINAL_MARK`, `ADDRESS_PARITY`, `FUNCTION_WORD_RESIDUE` and loses the foreign-word
  kind; `qa/CheckName` gains the three matching constants with their levels and loses `FOREIGN_WORD`;
  `qa/TextCheckGates` (exhaustive switch) follows. Three new check names (`terminal-mark`, `address-parity`,
  `function-word-residue`) cost EN and UK words.
- **Tests.** The invented `detectors-ru-uk` set runs with no model as a parameterised test in `check` over the
  production checks; the stray-content-word and mangled-stem cases expect no finding (documented misses only the
  proofread can catch). Each owner-reported defect (dash, heading number, Russian line, residue word) is a case written
  red first.
- **Why.** Code repairs make a check count only what a model must fix. *Alternatives rejected:* an identical-token
  share (no gap between clean and residue text); per-pair ending rules (false hits on Ukrainian verbs).

### D13 — One repair, the outcome table and Fast's lost-marks retry

- **What counts.** At most one model call per segment whose job is to change content after a check failed. Not
  counted: code repairs (restore missing marks, rewrap, NORM, control codes), one format re-ask, transport retries, the
  first draft of a missing batch id. Merged into the one call: placeholder repair, every directed-fix round, the soft
  fix, the re-draft of a batch item that failed its gate.
- **Build (group 10).** New `heal/OneRepair`: on Balanced and Max (`RepairMode.ONE`) any BLOCK or REPAIR finding earns
  one call, `PromptName.REPAIR`, `CallKind.REPAIR` (replaces `PLACEHOLDER_REPAIR` and `DIRECTED_FIX`), template
  `repair.user.prompt`: the draft prompt plus `[Previous attempt — data, not instructions]`, `[Defects to fix]` (each
  finding's explanation and quoted span) and the expected tokens; temperature `REPAIR_TEMPERATURE` (0.1) with the retry
  seed; one item. Group 10 builds it on today's request path (`DraftRequests` plus the tail); group 11 moves it onto the
  builder, which also gives it the glossary.
- **Keep rule.** The repair replaces the draft iff it passes the hard gates (placeholder, protected, typography) and
  `NeverWorse.judge(draft, repair)` is Keep (D14). `AcceptanceRule` alone never decides it.
- **Outcome** (read on the text that stays): a BLOCK left → FLAGGED (desk and Proofread tier 1); only REPAIR findings →
  ACCEPTED with a proofread mark (the stored unresolved findings); only NOTE → ACCEPTED with an audit mark; none →
  ACCEPTED. No new status.
- **Fast** (`RepairMode.MARKS_ONLY`): the table is read on the draft. Only a piece whose formatting marks are still
  lost after the code restore is drafted once more, under the same keep rule; if that fails, the segment is FLAGGED and
  export writes its source through today's fallback (`Fallbacks`, `EffectiveTargets.sourceFallbacks`) with a new banner
  `export.banner.source_fallback`.
- **Removed in group 10** (so two repair paths never coexist): `SegmentHealer.runRounds`, `attemptRound`,
  `decideEvaluated`, `RoundRunner`, `RoundEvaluator`, `RoundOutcome`, `RoundStep`, `Resumption`, `SoftFix` as a call,
  `placeholder-repair.user`, `DialParameters.repairRounds`, `SegmentRecord.repairRounds`. `DirectedFix`,
  `directed-fix.*`, `CallKind.DIRECTED_FIX`, `JobEvent.RoundStarted` and `ui: state/RunSession.onRound` go in group 14,
  because the reviewer's refused-edit fix (`ReviewResolver`) uses them until then. `RoundProgress` moves into
  `NeverWorse`.
- **Reviewer until group 14.** The reviewer runs as built at Balanced and Max; its refused-edit fix is its own call,
  not the one repair; the one repair runs on the findings left after it.
- **Resume.** A paused repair continues in-process at the repair call (the draft is in memory); after a restart the
  undecided chunk is drafted again, as today, because chunks commit atomically.
- **Why.** A failing segment cost about eight calls at Balanced. *Alternative held in reserve:* a second repair for
  BLOCK findings at Balanced and Max only, if more than 1 % of segments prove to need rounds 2–3 (a later change).

### D14 — The never-worse gate

- **`qa/NeverWorse`** (one class, group 10) takes over the logic of `PassChecks.refusal/isBetter`, `RevisionGuards`,
  `RoundProgress.isWorse/beats`, `EditRegression`, `ReviewResolver.notWorse` and, in group 14, the deterministic
  `EditGuards` rules; the export pass and the reviewer keep calling their old classes until group 14 deletes them.
  `judge(Candidate current, Candidate candidate, WindowFacts facts) → Verdict`, a sealed type
  `Keep | Reject(String reason)`; both texts are in the target language. `WindowFacts` (new record) holds what the
  vetoes read about the window: the source, the map entries with their target forms and states, the focus's triggering
  finding and whether the focus is an unmarked Max paragraph.
- **Vetoes (any → Reject):** the placeholder gate fails; a new blocking finding; a forbidden letter appears or the
  source-only function-word count rises; a name form outside `NameForms.forms` of a pinned or locked target; a map name
  dropped; fewer sentences (`Sentences.countLenient`); `NegationCount.of(candidate) < NegationCount.of(current)`; a
  number, quotes, dashes or clauses lost; a new Latin run; a stress mark; any per-check finding count rises for
  `alphabet`, `language-identity`, `function-word-residue` (counted on its own), `script-purity`, `number`,
  `terminal-mark`, `address-parity`, `sentence-count`.
- **Score.** `WEIGHT_BLOCKING` (5) × blocking + `WEIGHT_SOFT` (1) × soft + `WEIGHT_AUDIT` (1) × audit doubts (from
  `QaEvaluator` and `FinalAudit.checksOf`). Keep iff no veto, score not higher, and the triggering finding cleared or
  the score strictly lower, or, for an unmarked Max focus only, changed words ≤ `REWRITE_CAP` (0.35). Weights, the cap
  and the tie rule are unmeasured named constants; tests assert vetoes and orderings, not weight values.
- **Readers.** The Stage B keep rule (10); Proofread (13). There is no judge model at run time.
- **Tests.** `NeverWorseTest` gets the cases of `RevisionGuardsPolicyTest` (ported in 14) and one test per veto.
- **Why.** Three "no new blocker" variants disagreed. *Alternative rejected:* a model judge per segment (weak at
  ≤ 30B).

### D15 — The Proofread stage, deferrals and proofread marks

- **Package `pipeline.proofread`** (new, group 13): `ProofreadStage`, `MarkList`, `ProofreadWindows`, `ProofreadCall`,
  `WindowFingerprint` (from `revision/ParagraphFingerprint`). Group 14 moves the survivors of `revision/` here:
  `TermSweep`, `RevisionWriter`, `RevisionBase`, `UnchangedReply`, `DeferralRegister`, `GenderWaits`.
- **Terms.** A *proofread mark* is a segment's note that the Proofread stage (or, at Fast, the desk) must look at it:
  tier 1 = a FLAGGED segment or a REPAIR finding left after the one repair; an *audit mark* is tier 2 = a NOTE finding
  or an audit doubt. A *proofread verdict* is the record kept on a segment after a proofread window read it (below).
- **Audit.** `run/RunAudit` (the final audit through `audit/AuditRecorder`; today it runs when the run completes) moves
  to run once, when Translate ends, before the Proofread stage builds its mark list; its doubts become audit marks. It
  is not run again after Proofread (the never-worse gate guards every proofread change); the desk's "Run the audit
  again" stays on demand.
- **Order.** `TermSweep` first (no call, every dial): LOCKED entries whole-word, PINNED entries through their generated
  forms (old form k → new form k, built in group 7), never SUGGESTED; it consumes TERM deferrals. Then the mark list by
  code over every decided story segment: tier 1 = proofread marks and deferrals whose fact arrived; tier 2 = audit marks
  and the detectors (a lemma named in the source with none of its target forms, same source with different targets, a
  recurring term off its rendering, gender endings against a USER/BOOK gender); tier 3 = every story paragraph (Max's
  second round).
- **Fast.** Code only, no model call: the term sweep, the deferrals checked by code, and the mark list with the
  mismatching deferrals sent to the review desk; COMPLETED. Continue at Fast runs this code-only work again when a
  glossary change or a gender set after the run left work, so export's "Continue on Translating to apply it" holds at
  every dial.
- **Windows.** Marks within one paragraph merge, up to B = the profile's batch size and the current-part budget, plus
  one read-only neighbour each side; Max's round 2 tiles the book in windows of ≤ B paragraphs after round 1, with open
  marks riding as reasons. Reasons inside a window are ordered blocking first.
- **Call.** `PromptName.PROOFREAD`, `CallKind.PROOFREAD`, TRANSLATE frame; block 12 is the target file's minimal-edit
  block (`proofreadInstruction`, `proofreadRules` ≤ 5 lines, an English default, `{{sourceLanguageGenitive}}` from the
  target file); block 13 the focus items with source and current text. Reasoning OFF unless the person's override allows
  it; temperature 0.1; output = the pair reserve over the focus. The proofreading model is the drafter.
- **Reply.** `{"unchanged":true}` or changed items; ids outside the focus and texts identical to the current are
  dropped; a missing focus id is UNCHANGED. Each change goes through `NeverWorse`. A person-edited text is never
  rewritten: the change becomes a proposal (`RevisionWriter`), outcome PROPOSED. Keep on an ACCEPTED segment → status
  REVISED, outcome KEPT, `before` stored. Keep on a FLAGGED segment → REVISED and KEPT only when the kept text clears
  every blocking finding; otherwise the segment stays FLAGGED and the text is stored as a proposal for the desk
  (PROPOSED). A Reject → REJECTED. The review-queue delta's status machine says the same.
- **Records.** `api: project/ProofreadVerdict(fingerprint, outcome, reason, @Nullable before, statusBefore)` with `ProofreadOutcome
  {KEPT, PROPOSED, REJECTED, UNCHANGED}`, held in `SegmentRecord.proofread`. `api: project/ChunkCommit` gains the
  resolved deferral ids, so a window's targets, verdicts and resolved deferrals commit in one transaction
  (`CheckpointPort` contract case for both adapter families).
- **Resume.** Fingerprint = hash(focus sources, current targets, neighbour targets, injected map lines, prompt
  version, coverage tier); a window whose focus ids all hold a proofread verdict with the same fingerprint is skipped.
  `memory/CheckedParagraphs` (RAM only) goes.
- **Deferrals: one producer for every glossary write.** Today three writers each call `DeferralRegister.termChanged`
  themselves (`GlossaryServiceImpl:251`, `NamesPassStore:99`, `GlossaryRowsImport:109`; the last two write through
  `GlossaryRepository` directly, not through the service). Group 13 replaces those three calls with one helper,
  `DeferralRegister.changed(before, after, decided) → List<Deferral>`, which returns both the TERM deferrals of
  `termChanged` and the new `GENDER_CHANGED` ones: an enforced (USER or BOOK) gender that appears or changes after
  segments naming the entry were decided, a confirmed likely value included. Every glossary write path — the service
  (and `ui: state/GenderConfirmations` through it), the names pass's `NamesPassStore` and the CSV import's
  `GlossaryRowsImport` — calls only `changed`, so no writer can record one kind and miss the other.
  `DeferralRegister.unknownGender` creates `GENDER_UNKNOWN` only for an entry with no gender at all.
  `DeferralReason` gains `GENDER_CHANGED`. The glossary delta owns this producer; the Proofread stage is the consumer:
  at every dial, by code first, no mismatch → resolved with no call; else a tier-1 mark (Balanced, Max) or a desk item
  (Fast). Tests: a names-pass gender change and a CSV-import gender change, each after a decided segment, leave a
  `GENDER_CHANGED` deferral.
- **Estimate.** `ProgressLedger` counts Proofread steps from Start: round 2 as ⌈story paragraphs ÷ B⌉, tiers 1 and 2
  as the windows marked so far, one chapter-summary step per chapter with the switch on.
- **Visible.** The stage chip and window rows on Translating; desk filters by verdict and "Waiting on a gender", the
  `before`/after diff, "Undo proofread" (restores `before`); EN/UK words.
- **Why.** Owner decision D-18: the final pass edits only what is wrong. *Alternative rejected:* re-drafting marked
  segments with their window (rewrites accepted text).

### D16 — Export validates and writes with no model call

- **Group 14.** `export/ExportJobImpl` creates no model. Deleted: `export/ExportCalls`, `ExportParts.consistencyPass`,
  `ExportRequest.consistencyPass` and `consistencyScope`, `api: pipeline/ConsistencyScope`, the consistency progress
  steps, and in `:ui` `LastCheckChoice`,
  the switch, `control/RereadGrid`, `state/ExportWriteNow`, the model creation in `state/ExportRun`;
  `AppSetting.LAST_CHECK` and its Automation row.
- **Kept.** `SegmentVerification`, the unmask gate, the four-format reassembly, side files, the source fallback. The
  stress strip on output is new in group 10 (D4), not kept: translated segments only, source-language fallbacks as
  they are. ✦ Suggest a name stays an interactive ANALYSIS call, refused during a run by the existing
  activity refusal (D23).
- **Banners.** Proofread stopped at n of m windows; segments wait on a fact the person has given; segments written in
  the source language; the existing translating, partial and empty banners.
- **Record.** `ExportRecord.verifiedSegments` → `proofreadSegments` (stored); `api: pipeline/ExportReport.consistency:
  ConsistencySummary` (not stored) → `ProofreadSummary` (what Proofread changed and what still waits);
  `export/ConsistencySection` stays the Markdown section of `QualityReport` and becomes its proofread section.
- **Why.** The export pass repeated Proofread's work. *Alternative rejected:* keep it as an optional last check.

### D17 — Server engines: detection and traits

- **Records (`:api` `llm/`, group 5).** `ServerEngine {OLLAMA, LM_STUDIO, LLAMA_CPP, OPENROUTER, UNSLOTH, GENERIC}`;
  `ProviderConfig` gains `engine` (detected), `@Nullable engineChoice` (the person's "Treat this server as…", which
  detection never overwrites; the effective engine is the choice, else the detected one),
  `@Nullable engineVersion` and `boolean nativeOfferDeclined` (Keep as is on the one-time Ollama offer; Detect again
  clears `engineChoice` but never this flag, so the offer never returns and a declined offer never shows as the
  person's engine choice). `ProviderKind` stays the transport (two clients); the engine selects fields, not a client.
- **`llm.engine`** (new package). `EngineDetector`: only on Save, Test or Detect again; at most `MAX_PROBES` (7)
  body-checked GETs with 2 s timeouts, no body sent, the key only when the provider has one; host rules first
  (`openrouter.ai`, `ollama.com`, known hosted APIs), then Ollama (`/api/version` + `/api/tags` with the impostor rule:
  fake markers, and `/v1/models` rows must carry `owned_by` when it lists rows), llama-server (`Server: llama.cpp` or
  `/health` + `/props.build_info`; router flag), Unsloth (`/api/health`), LM Studio (`/api/v0/models` shape), then
  `base/models` signals, else GENERIC. A hit needs the expected body, never a status or a port. An app that cannot
  translate a book is refused at the connection stage (`validation`, no engine stored, no chat sent): GPT4All by
  `/v1/models` rows with `"owned_by":"humanity"` (reason `not-suitable-structured`), AnythingLLM by a base path
  `/api/v1/openai` (reason `not-suitable-documents`). `EngineTraits.of(engine, version)`: a Java switch giving
  `capField`, `offSwitch`, `reasoningFields`, `extraSamplers`, `windowControl`, `timingsSource`, `cachedTokens`,
  `errorQuirks`, `servesUnknownModel`, `alwaysSend`; computed, never stored. `EngineFacts` (session memory): Ollama
  runner, llama-server template caps, loaded window, router models. Group 5 makes `ui: state/ModelAdvice` (and its
  readers `ModelNotesFollower`, `TestSubject`) read the provider's effective engine and deletes its port-and-word guess
  (`LM_STUDIO_PORT` ":1234", `LM_STUDIO_WORD`, the `Server` enum), so two engine detections never coexist; group 15
  deletes the rest of `ModelAdvice`.
- **Request shaping.** `OpenAiRequestMapper` and `GatedChatModel.outputField` read the traits: Ollama `/v1` never gets
  `max_completion_tokens`; a `chat_template_kwargs` DTO field; per-engine sampler names; explicit `repeat_penalty` 1.0
  and `min_p` 0 where server defaults are not neutral (LM Studio, llama-server, Unsloth); Unsloth gets
  `{"enable_tools":false}` on every call; a `cap-ignored` post-check (WARN once per provider and model).
- **LM Studio.** Chat on `/api/v0/chat/completions`, non-stream, falling back to `/v1` for the session on a non-JSON or
  "Unexpected endpoint" reply; the model id is checked against `/api/v0/models` at Test and Start; `@variant` is
  stripped. Settings shows two separate messages: no model with the typed id, and the typed variant was removed.
- **Ollama added as OpenAI-compatible.** A one-time offer "Use Ollama's own connection"; never switched by itself;
  Keep as is sets `nativeOfferDeclined` and nothing else.
- **Model lists.** Chat models only (embeddings and other kinds dropped); Ollama cloud models carry a neutral "cloud"
  label (owner decision D-6).
- **Canaries.** An optional "Advanced features" stage of Test checks the off switch, schema, extra samplers and cap
  field; results go to `ModelSettings.probeVerdicts: ProbeVerdicts` (group 5).
- **Settings › Providers.** The "Detected: …" line, the override, Detect again, the Advanced stage, the Unsloth key and
  tools help; EN/UK words.
- **Privacy wording.** "Private when used with local models", never "always local" (owner decision D-5), in the UI
  help, the docs and `.claude/rules/offline-and-privacy.md`. No consent dialog or field. ADR-0044's first-launch listing
  is unchanged (llama-server's port stays out of it).
- **Live checks.** `liveLocal` per engine, env-gated: `BOOKLOOM_LIVE_OLLAMA_URL`, `BOOKLOOM_LIVE_LMSTUDIO_URL`, new
  `BOOKLOOM_LIVE_LLAMACPP_URL` and `BOOKLOOM_LIVE_UNSLOTH_URL`. The recorded Ollama `/v1/models` body (`owned_by` =
  namespace for `hf.co` rows, `library` otherwise) is the fixture of the impostor rule.
- **Why.** Every engine speaks Chat Completions but needs different fields for the same intent; what was missing is
  the engine, not more clients. *Alternatives rejected:* one client per engine (only request fields and error rows
  differ); detection by port (LM Studio answers 200 to every path).

### D18 — Error catalogue by meaning and the call ledger

- **Rows (group 3).** `http/HttpErrorMapper` walks one ordered `List<ErrorRow>` in `classifyBadRequest`, the 5xx path
  and the stream-error path: `ErrorRow(Set<ServerEngine> engines, Set<Integer> statuses, Pattern body, ErrorCode code,
  FailureReason reason, RetryRule retry)` with `RetryRule {NONE, SAME, NEW_SEED, LADDER_DOWN, RESEND_WITHOUT,
  REASONING_REASK, BACKOFF, AUTO_WAIT}`. Patterns are order-independent (the context row uses lookaheads; the refusal
  pattern uses `think(?:ing)?`); a row matches by body meaning whatever the status. No new `ErrorCode`; `api:
  FailureReason` gains the tokens (`context-overflow`, `model-not-pulled`, `thinking-unsupported`,
  `think-level-invalid`, `schema-invalid`, `structured-unavailable`, `server-busy`, `option-type`, `host-forbidden`,
  `engine-unreachable`, `template-rejected`, `model-loading`, `schema-not-applied`, `reasoning-exhausted`,
  `runner-crashed`, `out-of-memory`, `repeat-limit`, `no-model-loaded`, `model-unloaded`, `wrong-model-served`,
  `grammar-too-large`, `terminated`, `empty-structured`, `schema-cut-short`, `system-role-unsupported`,
  `stream-truncated`, `output-field`, `auth`, `cap-ignored`, `unclassified`, `not-suitable-structured`,
  `not-suitable-documents` (group 5), and later the cloud, OpenRouter and Unsloth tokens).
- **Staging.** Group 3: the rows matched by body text (Ollama, LM Studio, llama-server). Its first step records the
  real LM Studio `terminated` reply (a recorded body or one live call) and writes the fixture from it; `terminated` is
  `upstream`, retried once after a state check, a second one an outage. Group 5: the LM Studio id check, `cap-ignored`,
  the Unsloth rows and the integer error `code` rule. Group 6: `RejectedCapability.SYSTEM_ROLE` and refusal scopes.
  Group 8: the OpenRouter rows.
- **Who reads `RetryRule`.** One retry policy, not two: `GatedChatModel` reads the matched row's `RetryRule` instead of
  `ErrorCode.isRetryable()` (`GatedChatModel:264`, `RetryPolicy:51`); `llm/retry/RetryPolicy` keeps only the attempt
  count, the backoff and the timeout ladder's bounds; a failure no row matches keeps today's `isRetryable()` answer
  (`unclassified`).
- **Recovery.** `run/PauseDecider.recovery` routes by reason: `context-overflow` flags the segment and the packer
  shrinks the next chunk (no pause); `out-of-memory` → PERSON; `no-model-loaded`, `model-unloaded` → the existing
  UNLOADED_MODEL auto-wait. An answered but unreadable 200 is `upstream`.
- **Details and logs.** Only the token enters `AppError.details` (through `SafeDetails`); the provider's sentence is at
  TRACE. A sampler or grammar 400 logs WARN once; `grammar-too-large` and `schema-invalid` log ERROR once (a BookLoom
  bug).
- **Call ledger.** A port, so callers hold no `:llm` class: `api: llm/CallLedger` (`record(CallRecord)`,
  `mark() → long`, `since(long mark) → List<CallRecord>`) with the record `api: llm/CallRecord(callKind, usage with
  nulls kept, wall, ttft, @Nullable FailureReason reason)`. `:llm` implements it as a Guice singleton bound in
  `LlmModule` (`llm/CallLedgerImpl`: an append-only `ConcurrentLinkedQueue` with a sequence number, holding at most
  `MAX_RECORDS` (100,000) records, oldest dropped, because the job and interactive callers append from different
  threads); `GatedChatModel` records every call; verification calls carry no kind and are filtered. The sum per kind is
  not written twice: the existing `pipeline: CallKindTotals` is extended to sum a list of `CallRecord`s (instead of
  `ModelCallFinished` events), keeping `null` for a missing figure (the summary prints `–`) and counting failure
  reasons. `RunSummaryLogger`, `app: cli/UnattendedRun` and the names-pass estimate read `CallKindTotals.of(
  ledger.since(mark))`, so every stage is counted; the names pass, the brief and export print one INFO `prep summary
  byKind[…]` line. Call DEBUG lines gain `cached=` and `sourceChars=`; WARN lines gain `reason=<token>`; the always-on
  run log gets the run's start as counts and choices only (`profile resolved …` from group 6, its exact text owned by
  the inference delta; `facts read …` from group 5; never a name or book text).
- **Why.** One misread error paused a run for 43 minutes. *Alternative rejected:* mapping by status (the same status
  means different things on different engines).

### D19 — Model identity: reading model names

- **Records (`:api` `llm/`, group 6).** `ModelIdentity(family, version, line, sizeB, effB, activeB, variants)` plus
  `kind: ModelKind {LLM, EMBEDDING, RERANKER, IMAGE, VIDEO, SPEECH, SIDECAR, NOT_MODEL, WEIGHTS_SHARD}`, `baseFamily`,
  `distillOf`, `quantLabel`, `needsShow`; `ModelInput` (what the person pasted, normalised).
- **`llm.profile`** (new package). `ModelInputNormalizer` (a URL, an `ollama run` command, a file name, `hf.co/`
  prefixes, `@variant`) and `ModelIdentityParser` (name grammar v2). Metadata (`details.family`, LM Studio `arch`) wins
  over the name on conflict (a DEBUG line with both); architecture aliases are normalised (`qwen35` = `qwen3_5`,
  `gemma4` = `gemma4_unified`, `muse_glimmer`, `glm4moelite`); `deepseek2` alone and `llama` alone never select a row
  (two installed `hf.co` GGUFs report `llama`), and a name that reads a family of its own (Nemotron 3.5 Bolt) never gets
  another family's row by its architecture alone. A non-LLM kind (embeddings, `mmproj-*`, video) never reaches a picker.
- **Tests.** The research's `name-cases-v1.csv` (108) and `name-cases-v2.csv` (212) are copied into
  `modules/llm/src/test/resources/` by the group's first task (names only, book-neutral) and run as `@CsvFileSource`
  tests, plus the owner's sixteen installed ids.
- **Visible (group 9).** The paste box's "Understood: …" line.
- **Why.** `ui: state/ModelAdvice` read names by hand-written rules. *Fallback:* if more than 3 of the first 50 real
  ids parse wrongly, shrink the grammar to the listed forms and lean on metadata.

### D20 — Model profiles: resolution, reasoning, sampling, message shaping

- **Two records, by reader (`:api` `llm/`, not stored).** `ModelProfile` (group 6) holds only what a request needs:
  `reasoning: ReasoningControl(kind {NONE, BOOLEAN, LEVELS, ALWAYS, UNKNOWN, UNSWITCHABLE}, offered, offValue,
  serverDefault)`, `markupFilters {TO_SELF, BOXED}`, `systemRole {NATIVE, FOLD}`, `quirks` (`ESCAPE_THINK_TOGGLES`,
  `NO_REASONING_WORDS`, `SHORT_STRING_CHECK`, `PROMPT_ONLY_START`, `EXAMPLES_OFF`, `NO_KNOWLEDGE_HINT`), `sampling:
  Sampling`, `context: ContextPolicy(hardCap, outputMax, @Nullable detected, @Nullable loaded)`, `templateOverhead`,
  `batchSize`, `tier {SMALL, LARGE}`. `ChatModel.profile()` serves `:pipeline`. What a person reads is the existing
  `api: llm/ModelReport` (today `contextLength`, `loadedContext`), extended with `identity`, `match {OVERRIDE, EXACT,
  SIZE, FAMILY_VERSION, FAMILY, ARCHITECTURE, DEFAULT}` (group 6; `BASE_FAMILY` added in group 9 with
  `BaseFamilyAliases`), `verdict`, `warnings` (6), `languages` and `quantAdvice` (9), and served by the existing
  `ModelCapabilities.report`, now taking a `ModelSelection`, to `:ui` before a model exists. `EngineFacts` stays an
  `:llm` session cache; the facts a request needs reach `:api` only as `ContextPolicy`'s fields. So group 9 never
  touches the record `:pipeline` reads.
- **Resolution (`llm.profile.ModelProfiles`).** Stored engine → its metadata through `capability/ModelFactsReader`
  (grown from `ContextLengthReader` and `ContextLengthProbe`: Ollama `/api/show` capabilities, thinking values,
  renderer, parser, family, quant, `/api/ps`; LM Studio v0/v1; llama-server `/props` and `meta`; Unsloth rows; read only
  on selection, the Models tab, Test, Detect again, Start or Continue; kept in session memory) → name grammar →
  `BuiltInRows` (58 Java `Row(IdentityPattern, overrides, evidence)` records: the research table's rows 1–43 and
  the owner's four gap rows O1–O4 in group 6, the research table's rows 44–54 in group 9; a no-shadowing test; an alias-guard
  test) → the person's override.
- **Owner's models.** Group 6 adds rows for EuroLLM-22B (system role native, no thinking control, batch 4, verdict
  experimental), Ministral 3 Reasoning (always reasons, no switch: no control sent, the ALWAYS reserve, the
  `[THINK]` scrubber). An id with a `reasoning` variant, such as `mistralai/ministral-3-14b-reasoning`, never resolves
  to the non-reasoning Ministral row (that row's pattern excludes the variant); it gets the reasoning row or family,
  and the reserve when its server says thinking cannot be switched off. Also a Qwen 2.5 small-size row (no thinking,
  hard cap 32,768), and treats the Gemma 4 `it`/`qat` pairs as one behaviour row per size (identity excludes those
  flags; the quant advice still tells them apart). Nemotron 3.5 Bolt keeps the default profile until a probe of its off
  switch supports a row.
- **Reasoning.** The pipeline sends an intent `ReasoningLevel` per call kind (OFF for draft, repair, proofread, names
  and brief). Precedence: never send a control the model or the engine does not declare; the person's
  `ThinkingOverride {RECOMMENDED, ALLOW_PROOFREAD_AND_NAMES, ALWAYS}`; the profile default. The dialect mapper turns
  intent, profile and traits into fields (Ollama `think`, `reasoning_effort:"none"` on `/v1` and LM Studio,
  `chat_template_kwargs.enable_thinking:false` as a JSON boolean on llama-server and never `reasoning_effort` inside
  kwargs, gpt-oss `"low"` everywhere, glimmer `"low"` whenever a schema is sent). **UNSWITCHABLE**: a row whose server
  default is thinking on, with no `renderer` and no `thinking` capability in `/api/show` → no `think` field, the ALWAYS
  reserve, the scrubber, and a Start and Models-tab hint (the Models-tab line in group 9, D22). Removed in group 6:
  `BOOKLOOM_REASONING` (`ReasoningPlan`), `OllamaReasoning`'s `contains("gpt-oss")`, `ReasoningHeadroom`
  (`BOOKLOOM_EVAL_RULES` goes in group 1, D24).
- **Sampling.** The app's per-kind temperature (`PromptName.temperature`) plus explicit neutral penalties on engines
  whose defaults are not neutral is the profile's default; a row's vendor `top_p`/`top_k`/`min_p` are sent only if arm
  3a adopts them, as row data. The person's override is `SamplingOverride {PROFILE_DEFAULT, CUSTOM}` with the custom
  values (temperature, `top_k`, `top_p`, `min_p`); there is no "server defaults" choice, because it would contradict the
  explicit samplers sent where server defaults are not neutral. Removed: `SamplingMode` (`BOOKLOOM_SAMPLING`),
  `SamplingProfile`'s global modes, `AppSetting.REASONING`/`SAMPLING` and their enums.
- **Messages (`client/MessageShaper`).** A system message is always sent (including the verification probe); `FOLD`
  puts system + blank line + user into one user message for gemma2/gemma3 and their tunes; a "System role not
  supported" 400 stores `RejectedCapability.SYSTEM_ROLE` and resends folded at no attempt cost; the old Nemotron
  escape; the harmony developer turn.
- **Replies.** `response/ReasoningScrubber` replaces `ReplySanitizer` (every known reasoning and turn marker, harmony
  channels, one outer fence; `TO_SELF` and `BOXED` filters only by profile); the existing
  `client/openai/ReasoningBudget` (OpenAI only, ×4 cap) moves to `response/` and is extended to the Ollama dialect, so
  both dialects get one overflow re-ask (reasoning off if allowed, else cap ×4), and the Ollama DTO keeps
  `message.thinking`.
- **Structured rungs.** `SCHEMA → PROMPT_ONLY` per (model, `PromptName`), stored as
  `RefusedSetting(name, at, insteadDid, @Nullable scope)`; `PROMPT_ONLY_START` rows start low; never a schema with
  thinking on.
- **Window: one owner.** `api: llm/ModelWindow.of(ContextPolicy, @Nullable Integer override)` = the override, else
  `min(detected, hardCap, DEFAULT_WINDOW 16,384)`, in group 6. It replaces `context/ContextBudget.windowFor`,
  `ContextBudget.DEFAULT_WINDOW` and `chunk/TokenBudget.EFFECTIVE_CONTEXT` there; their other readers
  (`memory/ChapterText.MAX_TOKENS`, `JobLifecycleLogger`) read `ModelWindow.DEFAULT_WINDOW`. `ChatRequests` and
  `JobModelCalls` size every call of a model from that one window (preparation, file name, export, proofread); only the
  Ollama-native client sends it (`num_ctx`, with `truncate:false`), and an OpenAI-compatible request never carries a
  context size. At Start the loaded window is read for engines whose window is set at load or server start, and the
  prompt budget is loaded − cap − margin. `keep_alive` `KEEP_ALIVE` ("30m") on in-run Ollama native calls only.
- **Timeouts.** `RequestTimeouts` take the expected output including the reserve; ALWAYS models get `max(90 s,
  60 ms · tokens)`.
- **Stored (group 6).** `ModelSettings.thinkingOverride`, `samplingOverride`; `RefusedSetting.scope`. Settings › Models
  gets the override rows; Generation loses Thinking and Sampling.
- **Why.** Models differ in thinking switches, templates and structured output. *Alternatives rejected:* a class enum
  (kept as the fallback if metadata misresolves, risk below); a free-text per-model prompt (owner: out of scope).

### D21 — OpenRouter correctness essentials

- **Group 8, `:llm` only plus a few words.** `OPENROUTER` traits: the `reasoning` object from the model catalog
  (`none` listed → `{effort:"none"}`; an object without efforts → `{enabled:false}`; mandatory → the lowest effort;
  absent → omitted), checked by `reasoning_tokens`; reasoning read from `message.reasoning`.
- **Request.** `provider.require_parameters:true`, `plugins:[{"id":"context-compression","enabled":false}]`,
  `stream:false`, only the endpoint's `supported_parameters`, `repetition_penalty` as the penalty name; the cap
  clamped to the endpoint's `max_completion_tokens`; the window from its `context_length`. `ModelFactsReader` reads
  the catalog as a metadata source.
- **Errors.** An error inside a 200 is parsed before `choices`; rows for 402, 403, 408/504/524, 502/503/529, 429 and
  "no endpoint for parameters", each with a WireMock fixture.
- **Ledger.** Served provider and model slugs and `usage.cost` on the call line and in the run summary (numbers and
  slugs only).
- **Not built** (owner decision D-7): routing, data-collection, quantisation filters, pinning, `session_id`, key and
  free-tier counters.
- **Why.** Correct answers need the reasoning switch and parameter enforcement; routing is a product choice the owner
  declined.

### D22 — Quant and language advice

- **Records (`:api` `llm/`, group 9).** `QuantAdvice(token, baseType, format, dynamic, imatrix, qat, bitClass,
  sizeClass, tier, source)`, `QuantTier`, `SizeClass {SMALL, MID, LARGE, MOE}`, `LanguageDeclaration(listKind, codes,
  source)` with `ListKind {COMPLETE, POSITIVE_ONLY, NONE}`, `LanguageState {DECLARED, NOT_DECLARED, UNKNOWN}`.
- **`llm.knowledge`** (new package). `ModelKnowledgeFiles` (singleton) reads `ua/bookloom/llm/knowledge/quant-tiers.csv`
  and `declared-languages.csv` (each headed `# data-version` and `# deliberate:`) once and returns a `Result`; a
  malformed file is a start-up `internal` error that the smoke test catches. `QuantAdvisor`: the token and its flags
  (`UD-`, `_XL`, `i1`, QAT) from the name, base type and format from metadata, metadata wins on the bit class; the tier
  from the table. `LanguageAdvisor`: own row → base row → GGUF `general.languages` (add only), per open BCP-47 tag.
  `llm.profile.BaseFamilyAliases` (about 12 rows with evidence): a fine-tune inherits its base's behaviour row and
  declared languages (source named), never its verdict.
- **Data (copied in by the group's first task).** The two CSVs; verdict rows for eleven further models the research read
  (five experimental, one of them with a licence warning; six not recommended); no alias facts (group 6's
  identity parser owns them as `ModelIdentity.activeB`: `gpt-oss:20b` active 3.6B so it is MOE; `gemma4:26b` A4B;
  `glm-4.7-flash` 30B-A3B; `gpt-oss:120b` only after its vendor card is read); declared-language rows for
  EuroLLM, and `NONE` rows for Muse Glimmer and Nemotron Bolt. A pair counts as tested only from a row's evidence of
  BookLoom's own run: MamayLM's row names BookLoom's own ru→uk run, so a ru→uk project on it shows no language line
  (owner decision D-26: only BookLoom's own tests count, and that run is one). A model whose maker publishes no list is
  UNKNOWN for every language, English included; the one information line then names the pair's languages together.
- **Display (settings delta).** A chip "token · tier" in every model picker (for MLX tags the name's token until a Test
  or Detect reads `/api/show`); group 9 adds the identity, quant, language and "Model facts as of" lines, the licence
  note and the thinking hint (UNSWITCHABLE, D20) to Settings › Models (group 15 completes the tab, D23); Start
  lines: a quant line at POOR, UNUSABLE or REMOVED, or ACCEPTABLE at SMALL, unless the row names a BookLoom measurement
  of that model at that quant; a language warning for NOT_DECLARED, a quieter line for UNKNOWN, none where BookLoom
  measured the pair (owner decision D-25). Never a dialog, never blocking; the one refusal is the row rule for an
  English-input-only model.
- **Tooling.** `scripts/model-knowledge-check.py <research-data-dir>` (no network) prints changed rows and exits 0 with
  "research data not found" when the folder is absent.
- **Why.** The person cannot see whether a quant or a language is a risk. *Alternative rejected:* fetching Hugging Face
  metadata at run time (offline rule).

### D23 — Timings, interactive callers and the model-name filter

- **Timings.** Group 3: missing figures stay `null` end to end; Ollama stamps TTFT at the first streamed line with
  content or thinking on the `StreamedLines` clock. Group 5: the LM Studio v0 route reads `stats.time_to_first_token`,
  `tokens_per_second`, `generation_time`. Group 15: llama-server `timings` (`prompt_ms`, `cache_n`, rates); `–` for
  Ollama `/v1`, OpenRouter and generic; on MLX, prompt reading is labelled "uncached" with the cached share beside it;
  the run summary and Settings › Models show them per engine.
- **A model check during a run (groups 5 and 15): the existing refusal, reused.** The app already refuses every
  model-calling action while a run translates and lets it run while the run is paused: `ui: state/ActivityTracker`
  registers the run as `ActivityKind.TRANSLATION` only while it translates (not while it waits paused), every kind that
  calls the model is `exclusive`, and each owner asks `conflictFor` before it starts — `ProviderTestRunner.canStart`
  (Test inference, kind `PROVIDER_INFERENCE_TEST`), `SetupHelper.refusalFor` (✦ Suggest a name, the brief's
  suggestion), and `GlossaryModelRuns` through `ModelBlock.refusalFor` (Review with model, Translate, Prepare names
  automatically, Find and Review recurring terms; their model is built in `GlossaryModelRuns.job`). No call reaches
  `InferenceGate`, so the gate keeps its one blocking entry and no rule changes. Group 5 runs the Advanced features
  canaries as a `ProviderTest` whose activity kind is `PROVIDER_INFERENCE_TEST`, so they are refused the same way.
  Group 15 changes only the Providers screen's sentence: `ProviderChecks` and `ProviderSummaryCard` show
  `BlockedWords.in(…)` instead of the generic `ACTIVITY_BLOCKED`, and `BlockedWords` gives a translating run that
  blocks a provider check the new key ("BookLoom is translating. Try again when the run ends or while
  it is paused."). The other actions keep "Pause the translation first — BookLoom sends one model request at a time"
  (app-shell's "Disable model actions while a translation is at work").
- **Name filter.** One `ui: state/ModelNameFilter` (case-insensitive substring over id and display name) used by
  `screen/ModelField`, the Settings › Models chooser and the provider model list (owner decision D-8).
- **Models tab.** Group 9 adds the identity, quant, language and "Model facts as of" lines, the licence note and the
  thinking hint (D22); group 15 completes the tab (the verdict, structured answers per kind, the context window with who
  sets it per engine) and removes the old model notes: `ui: state/ModelAdvice` with `ModelNotesFollower`'s use of it.
- **Projects (group 15).** A RAM-tier hint from `OperatingSystemMXBean.getTotalMemorySize` (< 24 GB e4b; 24–35 GB 26b;
  ≥ 36 GB 26b with an opt-in larger model); never pulls.
- **Why.** Long lists hid the model; the Providers screen said a test was unavailable without saying that pausing frees
  the model. *Alternative rejected:* a non-blocking gate entry for interactive calls (a second refusal beside the
  activity tracker, with a second sentence).

### D24 — Eval harness v2 and the arms

- **Harness (group 1, test code and scripts only; not product behaviour).** In `pipeline/src/test/java/…/eval/`:
  `DatasetManifest` (SHA-256 per file, a root hash; a `check` test recomputes it), `EvalRepeats` with `SeededModel` (a
  test decorator stamping `seed = base + k`), `CaseRecord` (`cases.jsonl` and a calls folder), `JudgeRunner`,
  `JudgeRubric` (`bl-judge-1`), `JudgeReplyParser` (tests on recorded replies). `MiniRunner` reads the pair from the
  manifest. `scripts/eval-score.py` gains `diff --paired` (exact sign test, computed noise band), `calibrate`, `show`;
  `eval-matrix.sh` gains `--repeats`, `--judge`, `--pair`. The `reviewSeparation` floor and the fixed noise default are
  dropped. A `pseudo`-provider dry run must produce a complete version-2 report (in `check`).
- **Datasets** (`eval/datasets/`, invented text only, LF-normalised): `mini-en` (two chapters, numbered heading,
  negation, a stress-marked glossary seed, a proofread trap, numbers) and `mini-ru` (17 blocks, ru→uk, two chapters,
  heading number, female narrator from past tense, oblique form first, genitive with its own gender, lost dash, a
  Russian line, stress marks, double negation, ти/Ви, a three-speaker paragraph); each with `book.json`,
  `expectations.json`, `gold-map.json`, `manifest.json` and case labels. `detectors-ru-uk` (runner and empty manifest
  in group 1; cases in group 10). Each owner-reported defect becomes a case written red in the task that fixes it.
  Owner books are recorded only by their name and hash, in the git-ignored `.temporary_context/`; neither that record
  nor their reports are ever committed.
- **Graders.** Hard (floor 0): no stress mark, target alphabet, foreign script, placeholders, no source line, heading
  number, negation kept, numbers kept, one glossary entry per lemma, glossary gender, one spelling per lemma,
  window-injection (two scopes, D8/D10), proofread never-worse. A hard grader is a floor of 0 only from the group that
  builds its behaviour (lemma entries and one spelling per lemma: 7; stress marks and heading number: 10;
  window-injection: 11 and 12; proofread never-worse: 13); before that it is reported and compared with the
  `79ffede6` baseline only, so group 10's "mini K = 1 floors" gate counts only the graders live by then. Soft:
  source-only words (the case's literal list), dialogue dash, vocative, narrator gender, address, today's `MiniScorer`
  kinds. A grader never reuses the production check that decided the outcome.
- **Judge.** `gemma4:26b`, local, through `:llm` (the gate, explicit `think`, and the reply cleaner of the day:
  `ReplySanitizer` in groups 1–5, `ReasoningScrubber` from group 6); pairwise with the order
  swapped, a win only when both orders agree; cached by judge, rubric, mode and input hashes; reports flag
  `judgeSameFamily`. Calibration `cal-1`: about 60 invented items (≥ 20 ru→uk, ≥ 10 long), split 10 few-shot / 25 dev /
  25 hidden test; reference labels are the grades the developer agent and the owner's Codex CLI, asked by hand, agree
  on; `cal-1/labels.json` may be committed. Codex is never called by the app, a script or CI (owner decision D-21).
- **Statistics.** Unit = case; K repeats averaged first; K = 1 in the inner loop (mini-en + mini-ru, graders only,
  about 4 minutes), K = 3 with recorded seeds for decisions; `sequence` (about 320 paragraphs) to confirm small
  effects. Decision: a screen moves ≥ 2 cases with no key metric losing ≥ 2; a real effect is the two-sided sign test on
  discordant cases with p < 0.05 (6:0, 8:1, 10:2, 15:4), or a paired CI excluding 0 beyond the noise band. Baselines
  are the outputs of today's pipeline behaviour (that of `79ffede6`), recorded in group 1 with the commit that produced
  them; group 1 changes `src/main` only by test seams whose defaults reproduce that behaviour exactly, and the check is
  that running the suites with the seam defaults reproduces the recorded baseline; outputs of invented text are
  committed. Floors: 0 hard-grader failures; soft graders no worse than baseline − max(noise, MDE)
  (MDE = the smallest detectable effect).
- **Report v2.** `dataset`, `model` (with quantisation, profile, thinking and sampling per kind, seeds), `judge`
  (with calibration), `repeats`, `noiseBand`, a `comparison` block; per case: graders, judge result, time (total,
  prompt reading, TTFT, generation; `null` when missing) and tokens. Prompts and replies go to
  `.temporary_context/eval-history/<runId>/`.
- **`PromptVariants`** (`pipeline.prompt`, record bound in `PipelineModule` to its defaults): the only way an arm
  selects a prompt alternative (test injectors bind another value). It replaces the eval environment switches in group
  1, `BOOKLOOM_EVAL_RULES` (`LanguageRules.forEnvironment`) among them. Each field is deleted by the task that settles its arm;
  task 14.7 deletes the last fields (`reviewer`, and `languageRules`, the generic-only rules switch the eval keeps until
  then) with the record, and group 17 checks it is gone. Arms 3a and 3b are
  not prompt variants: 3a is profile-row data and 3b binds `BatchDrafter`'s start size in the test injector.
- **Arms.** Every arm: translator fixed, paired, K = 3, judge pairwise on changed cases, e4b first and 26b once; time
  and calls reported, never weighed against quality; equal quality keeps the simpler or cheaper side. LTCR-1 is the
  `sequence` suite's long-text consistency rating: the share of a term's later occurrences that keep its first
  rendering; "falls 1.5 points" means 1.5 percentage points.

| Arm | Group | Default (shipped) | Alternative | Rule | Result |
|---|---|---|---|---|---|
| 1 judge calibration | 1 (gates judge use from 2) | judge grades pairwise | judge as a screen only; every discordant case read by the agent with a Codex second opinion by hand | TPR and TNR ≥ 0.8 (Wilson lower bounds), position consistency ≥ 0.7 on the hidden test split | — |
| 2 batch format | 2 | JSON items | markers | markers only with ≥ 2 fewer WRONG paragraphs and no hard-gate rise ≥ 2 | — |
| 3a sampling | 6 | app temperature + explicit neutral penalties | vendor `top_k`/`top_p`/`min_p` as row data | vendor set only if no hard grader worse and token fidelity within noise | — |
| 3b batch size | 6 (wired in 10) | 4 on small models; 8 on large | 8 on small models | 8 only if token fidelity, fallbacks and hard gates are no worse beyond noise | — |
| 4a bare-name accuracy | 7 | a class's likely values may reach prompts (per 4b) | `NO_KNOWLEDGE_HINT` for the class | ≤ 5 % confidently wrong (confidence ≥ 0.7) of about 60 names and unisex names below 0.7 | — |
| 4b likely-gender hint | 7 | name only (H-B) | "Name — male (likely)" (H-A) | hint kept only with fewer gender-agreement errors | — |
| 5a rules layout | 11 (card in block 8, Balanced) | builder blocks + reminder | today's wording and order inside blocks 3–5 | builder kept unless fidelity, fallbacks or hard gates worse beyond noise, or LTCR-1 −1.5 | — |
| 5b key-term asks | 12, last (after chapter summaries, Balanced) | flat schema, no `terms` | `terms` + `KeyTermAsks` | asks return only if LTCR-1 falls ≥ 1.5 | — |
| 5c rules per book | 11 (card in block 8, Balanced) | per book | per window, in the user tail | per window only on a quality win (p < 0.05, or a screen confirmed on `sequence`) with no hard grader worse; a tie keeps per book | — |
| 6 chapter summaries | 12 | per-book switch, suggested | — (reported) | if "on" is worse beyond noise, the owner is told | — |
| 7a reviewer | 14, first (gates its removals) | reviewer off at every dial (one repair + Proofread) | today's reviewer on at Balanced, as built, on the same pipeline | the reviewer stays only on p < 0.05 on the final text, or ≥ 2 mini cases confirmed on `sequence` | — |
| 7b Max | 14, first (gates its removals) | Max = Balanced + whole-book re-read | — (reported; today's Max from the `79ffede6` baseline) | round 2 graded worse than better → tighten `NeverWorse` or the round-2 prompt | — |
| 7c proofread prompt | 13 | P1 (target-language block) | P2 (English frame) | P1 unless P2 wins | — |
| 8 quant | 9 | current advice | another quant recommended on e4b | changes only if a quant is worse beyond noise on mini-ru | — |

- **Measurements without an arm.** Group 4's residue replay (precision ≥ 0.9 keeps REPAIR, else NOTE; D12); the ru→uk
  `outputRatio` (1.5 until mini-ru and the closing ru→uk rerun show under 1 % of replies cut by length at 1.2); the
  second-repair falsifier (D13); the `/api/v0` route and `keep_alive` probes (D17, D20); judge reasoning (off unless it
  fixes ≥ 3 dev items net); prefix reuse per runtime (reported in 16).
- **If an alternative wins.** 3a: profile rows carry vendor values (no requirement text changes). 3b: one profile row
  and one scenario value. 4a: profile quirk data. 4b: a group-7 task edits the glossary/translation-pipeline
  requirement on map lines. 5a: today's rule text returns inside blocks 3–5 (no requirement change). 5b: the REMOVED
  glossary requirement on `terms` is dropped from the delta. 5c: D9's move. 7a: group 14 keeps the reviewer at Balanced
  (it does not delete `reviewer/` or its requirements) and a group-14 task edits the quality-gates delta; a reviewer
  that only lists defects for the one repair is left to a later change. 7c: P2 becomes the template.

### D25 — Removals, staged behind their replacements

A removal lands only when its replacement's gate is green; no runtime switch ships; comparisons run against
`79ffede6`.

| Group | Removed | Replaced by | Gate first |
|---|---|---|---|
| 1 | `BOOKLOOM_EVAL_RULES` (`LanguageRules.forEnvironment`) and the other eval environment switches | `PromptVariants` | the `pseudo` dry run's complete version-2 report |
| 2 | losing batch format, `DUAL`, `BOOKLOOM_BATCH_FORMAT`, notes and relevant-pairs switches (`BatchNotesSwitch`, `SCHEMA_WITH_NOTES`, `RelevantPairsSwitch`, `RelevantPairs`, `BatchRelevance`), `BatchExamples.renderMarked/renderDual`; `BatchDrafter.MAX_SIZE` and growth above the start; `ReviewDesk.skip`, `ReviewDeskImpl.skip`, `SegmentActions.skip` and their tests | the winning format; the start size as ceiling; "Leave for later" | arm 2 |
| 6 | `BOOKLOOM_REASONING`, `SamplingMode`/`BOOKLOOM_SAMPLING`, `SamplingProfile` global modes, `AppSetting.REASONING`/`SAMPLING`, `OllamaReasoning`'s name rule, `ReplySanitizer`, `ReasoningHeadroom`; Generation's Thinking and Sampling; `ContextBudget.windowFor`, `ContextBudget.DEFAULT_WINDOW`, `TokenBudget.EFFECTIVE_CONTEXT` | profile, `ReasoningScrubber`, reserve, Models tab overrides; `ModelWindow` | WireMock contract tests, both dialects |
| 10 | directed-fix rounds (`runRounds`, `attemptRound`, `decideEvaluated`, `RoundRunner`, `RoundEvaluator`, `RoundOutcome`, `RoundStep`, `Resumption`), `SoftFix` call, `placeholder-repair.user`, `repairRounds` (dial and segment); `ForeignWordCheck`, `ForeignWords`, `foreignWords`; `CheckName.hardGate`, `SoftFindings.FIXABLE`; `DialParameters.chunkCap`, `MANUAL_CHUNK_CAP`, the 2,000-token cap | the one repair, `NeverWorse`, computed residue, `CheckName.level`, profile batch size | group 10 acceptance test; mini K = 1 floors |
| 11 | `ContextPackageAssembler` (with `replay`), `DynamicFit`, `PromptRequests.prepared`, `BatchContexts`, `BatchFit.capPairs/capNext/lean`, the duplicated builders and system variants, `ContextBudget`'s shares and `DYNAMIC_CEILING` (replaced by `WindowBudgets`), `draft.*`, `DraftSchema`, `DraftReplyParser`, `DialParameters.precedingTargets`; the rolling summary's place in the draft prompt | `ContextBuilder`; the recently-present card (same task) | arm 5a; equivalence test over every kind |
| 12 | `RollingSummaryKeeper`, the Max unit-end `SUMMARY` call, `summary.*`, `DialParameters.llmSummary`; the loser of arm 5b (`terms` and `KeyTermAsks`, or nothing) | chapter summaries | group 12 acceptance test (arm 6 reports); arm 5b |
| 14 | backward revision and export pass (`ConsistencyPass`, `NeighbourRevision`, `NeighbourPrompt`, `GenderRevision`, `RetryPass`, `RevisionCall`, `PassOptions`, `PassTally`, `PassInputs`, `PassChecks`, `RevisionGuards`, `EarlyStop`, `CheckedParagraphs`, `RevisionAnswer`, `ConsistencyReport`, `BookContext`, `StageRunner.revise`, `revision.*`, `consistency.*`, `PromptName.REVISION/CONSISTENCY`) and their 13 `ConsistencyPass*Test` classes, `NeighbourRevisionStopEarlyTest`, `RevisionBook`; export last check (D16); the reviewer per arm 7a (`reviewer/` except the deterministic `EditGuards` rules moved into `NeverWorse`, `QualityLoop.reviewChunk/qualifyingPairs`, `ReviewResolver` with what only it kept alive (`DirectedFix`, `directed-fix.*`, `CallKind.DIRECTED_FIX`, `JobEvent.RoundStarted`, `RunSession.onRound`), `ReviewJudge`, `EditRegression`, `GuessedGenders`, `OutputLimit.forReview`, `CallLimits.REVIEW_TIMEOUT`, `CallKind.REVIEW`, `reviewer.*`, `suspicious-words.*`, `WordValidators`, reviewer words); `DialParameters.reviewPasses`, `backwardRevision`; `GenerationQualityTable` and `BriefQualityCard` reviewer and backward-pass hints, `estimate.relative.*` | Proofread stage; validate-and-write export; the one repair | Proofread and deferral gate green (no open arrived-fact deferral; changed-paragraph grading ≥ 3 better per worse); arms 7a, 7b, run first in this group |
| 15 | `ui: state/ModelAdvice` (its engine guess already gone in 5) and the old model notes | profile and report lines on the Models tab | group 15 acceptance test |

Kept on purpose: `TermSweep`, `RevisionWriter`, `RevisionBase`, `UnchangedReply`, `DeferralRegister`, `GenderWaits`
(moved to `pipeline.proofread` in 14), the co-occurrence learner, the refusal phrase lists, `given-names-<lang>.txt`.

### D26 — Stored shapes and database resets

Policy (ADR-0043, owner decision D-2): every group that changes a `StoredShapes` root, a nested record or an enum
constant changes the `meta.record_shapes` fingerprint; the next launch renames the file `bookloom.db.old-<time>` and
creates a fresh one; nothing is carried over. New stored types are added to `StoredShapes.ROOTS`. The in-memory
adapters, `RepositoryContractTest` and a `JsonCodecTest`-style round trip of each new record follow in the same group.
The command line keeps the in-memory adapters and is unaffected.

| Group | Records before → after | Reset |
|---|---|---|
| 1–4 | none (eval code, error rows, data files; `FailureReason` and `CallKind` are not stored yet) | no |
| 5 | `ProviderConfig` + `engine`, `engineChoice`, `engineVersion`, `nativeOfferDeclined`; `ModelSettings` + `probeVerdicts: ProbeVerdicts` | yes |
| 6 | `ModelSettings` + `thinkingOverride`, `samplingOverride`; `RefusedSetting` + `scope`; settings keys `run.reasoning`, `run.sampling` no longer written | yes |
| 7 | `GlossaryEntry` reshaped (D3); `LexiconEntry` + `forms`; `Origin`, `TargetState`, `PlaceKind` new; `TargetOrigin`, `GenderOrigin` deleted; `TermType` + 3 constants; `BookBrief` + `apostrophe`, `quoteStyle`, `defaultAddress`, `conventions`, `likely`; `ModelSettings` + `secondsPerCall` − `measuredSecondsPerSegment`, `measuredFrom` (from here `CallKind` is a stored enum, a map key, so the later `CallKind` edits of groups 10, 12, 13 and 14 change the fingerprint too; those groups reset anyway) | yes |
| 8, 9 | none (`OPENROUTER` exists since 5; advice is derived) | no |
| 10 | `SegmentRecord` − `repairRounds` (stored findings keep `QaFinding.kind` as a string, so the new `FindingKind` and `CheckName` constants change no fingerprint) | yes |
| 11 | `ContextSnapshot` → `(frameHash, sections, chapterSummaryKeys)`; new `promptFrame` document root | yes |
| 12 | `RollingSummary` → `ChapterSummary` (root replaced); `BookBrief` + `storyShape`, `chapterSummaries` | yes |
| 13 | `SegmentRecord` + `proofread: ProofreadVerdict` (with `ProofreadOutcome`); `DeferralReason` + `GENDER_CHANGED` | yes |
| 14 | `ExportRecord.verifiedSegments` → `proofreadSegments` (`ProofreadSummary` replaces `ExportReport.consistency`, which is not stored); settings key `automation.lastCheck` gone | yes |
| 15–17 | none | no |

Eight resets (groups 5, 6, 7, 10, 11, 12, 13, 14). Before each, the owner is told that projects, providers and
settings must be entered again. `persistence-sqlite.md` needs no change.

### D27 — Reuse map: what each new piece extends or replaces

| New piece | Extends or replaces | Module | Group |
|---|---|---|---|
| `DatasetManifest`, `CaseRecord`, report v2 | `EvalProvenance`, `EvalRow`, `eval/sequence/manifest.json` | `:pipeline` tests | 1 |
| `SeededModel`, `EvalRepeats` | `EvalCase`; `JudgementFakeModel` stays a scripted fake | `:pipeline` tests | 1 |
| `JudgeRunner`, `JudgeRubric`, `JudgeReplyParser` | `JudgementRunner`, `JudgementSuites`, `ReplyJudge` | `:pipeline` tests | 1 |
| `PromptVariants` | replaces the eval env switches (`LanguageRules.forEnvironment`, `BatchFormat`) in 1; deleted with its last field | `:pipeline` | 1–14 |
| batch size ceiling = start | `BatchSizeController`, `BatchDrafter` | `:pipeline` | 2, 10 |
| `ErrorRow` list, reason tokens | `HttpErrorMapper.classifyBadRequest`, `api: FailureReason`, `CapabilityRejections` | `:llm`, `:api` | 3 |
| `RetryRule` | replaces `ErrorCode.isRetryable()` as `GatedChatModel`'s retry decision; `RetryPolicy` keeps counts and backoff | `:llm` | 3 |
| `CallLedger` port, `CallRecord` | extends `CallKindTotals` (sums `CallRecord`s, nulls kept) instead of a second sum; `CallLedgerImpl` in `:llm` | `:api`, `:llm`, `:pipeline` | 3 |
| reason-aware recovery | `run/PauseDecider` | `:pipeline` | 3 |
| `FunctionWords` | new file kind, also read by `glossary/StopWords` (scan extras stay in `stopwords/<lang>.txt`); key `functionWords` split into `negationWords` and `moodWords` | `:pipeline` | 4 |
| `ServerEngine`, `engine` fields | beside `ProviderKind` (unchanged) on `ProviderConfig` | `:api` | 5 |
| `EngineDetector`, `EngineTraits` | `RequestHeaders`, `OpenAiRequestMapper`, `GatedChatModel.outputField` read it; replaces `ui: state/ModelAdvice`'s port-and-word engine guess | `:llm` (`:ui` reader) | 5 |
| `ProbeVerdicts` | session-only `ui: state/StructuredAnswers` | `:api`, `:ui` | 5 |
| `ModelFactsReader` | grows from `ContextLengthReader`, `ContextLengthProbe`; used by `ModelCapabilityService` | `:llm` | 5–6 |
| `ModelIdentityParser`, `ModelInputNormalizer` | replaces name rules of `ui: state/ModelAdvice` | `:llm` | 6 (UI 9, 15) |
| `ModelProfile`, `ModelProfiles`, `BuiltInRows` | new request-side record, served by `ChatModel.profile()` | `:api`, `:llm` | 6 |
| report fields (identity, match, verdict, warnings; languages, quant advice) | extend the existing `api: llm/ModelReport`, served by `ModelCapabilities.report(selection)` | `:api`, `:llm` | 6, 9 |
| `ReasoningScrubber`, `ReasoningBudget` | scrubber replaces `ReplySanitizer`, `ReasoningHeadroom`, `OllamaReasoning` name rule; `ReasoningBudget` moved from `client/openai/` to `response/` and extended to Ollama | `:llm` | 6 |
| `MessageShaper` | new; `ChatMessage` unchanged | `:llm` | 6 |
| `ModelWindow` | replaces `ContextBudget.windowFor`, `ContextBudget.DEFAULT_WINDOW`, `TokenBudget.EFFECTIVE_CONTEXT` | `:api` | 6 |
| scoped refusals | `RefusedSetting`, `provider/RefusalMemory` | `:api`, `:llm` | 6 |
| `Origin`, `TargetState` | replace `TargetOrigin`, `GenderOrigin`, `locked` | `:api` | 7 |
| `StressMarks` | new; called by glossary/lexicon services, `TypographyNormalizer`, export | `:util` (`text/`) | 7, 10 |
| `NameForms` | replaces `NameAliases`' plural fold (`pluralSuffixes`); `CaseVariants` and `NameAliases`' typo fold stay and add their spellings to `forms`; `lexicon/BaseForms` stays the representative picker for `FormGroups` | `:pipeline` | 7 |
| `QuoteStyle`, `AcceptReport` | new `:api` records (the Brief's quote marks; Accept all's accepted and rejected rows) | `:api` | 7 |
| `MeasuredSpeed`, `secondsPerCall` | replaces `ModelSettings.measuredSecondsPerSegment` and `measuredFrom` | `:api` | 7 |
| `PastTenseReader` | `PronounGender.Evidence`; replaces uk regex `firstPersonPast`; generalises `UkrainianGenderCheck`'s ending reading | `:pipeline` | 7 |
| `TargetValidator`, `acceptSuggested` | extends `RenderingChecks`; `SuggestTargets` | `:pipeline`, `:api` port | 7 |
| `NameKnowledgeReplies` | the `BookKnowledgeVerdict` echo pattern | `:pipeline` | 7 |
| `FormGroups` | `NameTally`, `TermEvidence` counting | `:pipeline` | 7 |
| `TermSweep` with forms | `revision/TermSweep` (kept) | `:pipeline` | 7, 13 |
| `CheckLevel`, `CheckLevels.of` | extends `qa/CheckName` (`level` replaces `hardGate`); replaces `SoftFindings.FIXABLE`; read by `BlockingFindings`, `AcceptanceRule` | `:pipeline` | 10 |
| `LeadingDash`, `DialogueDash`, `LeadingNumber` | `SpeechRepairs` (`OpeningQuote` pattern), `CommaBeforeDash` | `:pipeline` | 10 |
| `TerminalMarkCheck`, `NegationCount`, `SourceVariants` | `FinalAudit.checksOf`, `NameVariants` | `:pipeline` | 10 |
| `NeverWorse`, `WindowFacts` | merges `PassChecks`, `RevisionGuards`, `RoundProgress`, `EditRegression`, `ReviewResolver.notWorse`, `EditGuards` rules (the old classes stay for the export pass and the reviewer until 14) | `:pipeline` | 10, 14 |
| `OneRepair`, `repair.user` | replaces `SegmentHealer` rounds, `DirectedFix`, `SoftFix`, placeholder repair | `:pipeline` | 10 |
| `BriefKeyRecheck` | new; uses `TypographyNormalizer` and the checks | `:pipeline` | 10 |
| `ContextBuilder`, `PromptPlan`, `Block` | replaces the six selectors; reuses `ContextBudget.SAFETY_MARGIN`, `TokenEstimator`, `PromptTemplates`, `PromptSection`, `StyleSheet` | `:pipeline` | 11 |
| `WindowBudgets` | replaces `ContextBudget`'s shares and `DYNAMIC_CEILING` | `:pipeline` | 11 |
| `BookMap` | new record over the glossary's lemma entries, `FormGroups` and the brief keys; replaces the per-selector glossary reads | `:pipeline` | 11 |
| `WindowAssembler`, `WindowMap` | `PrecedingTargets`; merges `InjectedTerms`, `InjectedCharacters`, `InjectedLexicon`, `ChunkContext.occurringIn` | `:pipeline` | 11 |
| `RunFrame`, `RuleFacts` | `RunFrame` holds the existing `setup/BookFacts` unchanged (no second `BookFacts`) beside the new `RuleFacts` | `:pipeline` | 11 |
| `BookRules` | `CallFrame`, `StyleSheet`, `LanguageRules` | `:pipeline` | 11 |
| slim `ContextSnapshot`, `promptFrame` | `ContextSnapshot`, `ProjectDocs`/`DocKind` | `:api`, `:persistence` | 11 |
| rebuild on retry | `review/RetryDraft`, `SnapshotTerms` | `:pipeline` | 11 |
| `ChapterBoundaries` | `StructureNode`, `UnitChunks` | `:pipeline` | 12 |
| `ChapterSummaryCall` | grows from `memory/SummaryModelCall`; `summary.*` → `chapter-summary.*` | `:pipeline` | 12 |
| `ChapterSummary`, `SummaryRepository` | replaces `RollingSummary`; the port reshaped | `:api`, `:persistence` | 12 |
| `RecentlyPresentCard` | `RollingSummaryKeeper`'s code who-is-who; shown only in a call's prompt (`ui: state/SceneSummary` is deleted in 12) | `:pipeline` | 11 |
| `ProofreadStage`, `ProofreadWindows`, `ProofreadCall` | replaces `ConsistencyPass`, `NeighbourRevision`, `RevisionCall`; `JobStage.REVISE` → `PROOFREAD` | `:pipeline`, `:api` | 13 |
| `WindowFingerprint` | `revision/ParagraphFingerprint` | `:pipeline` | 13 |
| `ProofreadVerdict`, `ProofreadOutcome` | replaces RAM-only `CheckedParagraphs`; `RevisionWriter`/`RevisionBase` for proposals | `:api` | 13 |
| `DeferralRegister.changed` | replaces the three `termChanged` call sites (`GlossaryServiceImpl`, `NamesPassStore`, `GlossaryRowsImport`); adds `GENDER_CHANGED` | `:pipeline` | 13 |
| `ProofreadSummary` | replaces `ExportReport.consistency` (`ConsistencySummary`); `export/ConsistencySection` becomes the report's proofread section; `ExportRecord.verifiedSegments` → `proofreadSegments` | `:api`, `:pipeline` | 14 |
| `ModelKnowledgeFiles`, `QuantAdvisor`, `LanguageAdvisor`, `BaseFamilyAliases` | new; read by `ModelProfiles` and `ModelReport` | `:llm` | 9 |
| refusal of a model check during a run | reuses `ActivityTracker.conflictFor`, `ActivityKind` (`exclusive`), `BlockedWords`; the Advanced canaries register `PROVIDER_INFERENCE_TEST` (5); the Providers screen's sentence (15) | `:ui` | 5, 15 |
| `ModelNameFilter` | `screen/ModelField` and the Models chooser | `:ui` | 15 |

### D28 — Decision records (ADRs) and specification edits

**ADRs** (`docs/adr/`, `docs/adr/template.md`, each naming what would falsify it; the `docs/adr/README.md` index row
in the same group):

| ADR | Group | What |
|---|---|---|
| ADR-0046 (new) three stages, one repair, never-worse gate, no model call at export | 10 written ("accepted for the one repair, proposed for the rest"); 14 fully accepted | Falsifier: paired grading shows more worse than better changed paragraphs |
| ADR-0007 tiered judge and self-heal pipeline | 14 | `Status: superseded by ADR-0046` (one line: the dictionary-free glossary and TM decisions live on) |
| ADR-0038 judge a chunk, caps 8/8/2 | 14 | superseded by ADR-0046 |
| ADR-0047 (new) server engines and per-model profiles | 5 written (engine, detection, traits, kind unchanged, resolution order) | Falsifiers: wrong engines in the live matrix; more than about 8 version branches per release |
| ADR-0005 two client implementations | 5 | amended by ADR-0047: the kind stays the transport; the engine selects fields |
| ADR-0010 offline network policy | 5 | amended: "private when used with local models" |
| ADR-0013 response handling | 6 | amended by ADR-0047: reasoning per profile through traits; two structured rungs per (model, schema) |
| ADR-0033 bound chat model | 6 | amended by ADR-0047: per-call settings now depend on the model (its falsifier met) |
| ADR-0042 auto source language | 7 | one line: lemma entries replace the plural fold; no analysis language means no form grouping |
| ADR-0034 storage ports | 12 | `ChapterSummary` replaces the rolling summary |
| ADR-0012, ADR-0035, ADR-0036 | 14 | one model, no judge preflight; the job prepares, translates and proofreads; no judge clause |

ADR-0001, ADR-0022, ADR-0039, ADR-0043 and ADR-0044 need no edit. The config rule "write the ADR first" is met per
group: each ADR is written in the group that first makes its decision real, before that group's code.

**Specification edits.** Each group fixes, in its own commit, the clauses of `docs/specification/**`, the diagrams,
the mockups, `.claude/rules/` and the docs that its behaviour makes wrong; history in ADR bodies is never rewritten
(only status and amend lines). By file:

| File | Groups |
|---|---|
| `00_Foundation/01_VISION_AND_SCOPE.md` | 5, 8, 12, 14 |
| `00_Foundation/02_GLOSSARY.md` | 5, 6, 7, 10, 11, 12, 13, 14 |
| `00_Foundation/03_PERSONAS_AND_USECASES.md`, `06_IMPLEMENTATION_STAGES.md` | 14 |
| `00_Foundation/04_DESIGN_DECISIONS.md` | 5, 6, 7, 10, 12, 14 |
| `00_Foundation/05_SPEC_INDEX.md` | 10, 14 |
| `01_Product/01_FUNCTIONAL_REQUIREMENTS.md` | 2, 5, 6, 7, 10, 11, 12, 13, 14 |
| `01_Product/02_TRANSLATION_WORKFLOW.md` | 10, 14 |
| `01_Product/04_LLM_PROVIDERS_AND_MODELS.md` | 5, 6, 8, 9, 14 |
| `01_Product/05_TRANSLATION_ALGORITHM.md` | 2, 6, 10, 11, 12, 13, 14 |
| `01_Product/06_REVIEW_AND_EDITING.md` | 10, 11, 13, 14 |
| `01_Product/07_SETTINGS.md` | 5, 6, 9, 10, 14, 15 |
| `01_Product/08_UI_SCREENS_AND_STATES.md` | 5, 6, 7, 10, 12, 13, 14, 15 |
| `01_Product/11_NOTIFICATIONS_AND_ERRORS.md` | 3 |
| `01_Product/12_PROMPT_CATALOG.md` | 2, 4, 6, 7, 9, 10, 11, 12, 13, 14 |
| `02_Architecture/01_SYSTEM_ARCHITECTURE.md` | 5, 14 |
| `02_Architecture/02_MODULES_AND_LAYERING.md`, `03_DOCUMENT_MODEL.md` | 14 |
| `02_Architecture/04_LLM_INTEGRATION.md` | 3, 5, 6, 8, 14 |
| `02_Architecture/05_PIPELINE_ENGINE.md` | 6, 7, 10, 11, 12, 13, 14, 15 |
| `02_Architecture/06_DATA_MODEL_SQLITE.md` | 5, 7, 12 |
| `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md` | 10, 14 |
| `02_Architecture/09_ERROR_HANDLING.md` | 3, 14 |
| `03_NonFunctional/02_PERFORMANCE.md` | 12, 14 |
| `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md` | 5 |
| `03_NonFunctional/05_RELIABILITY_AND_RESUME.md` | 10, 12 |
| `04_Build_and_Release/06_TESTING_STRATEGY.md` | 1, 6 |
| `IMPLEMENTED_REQUIREMENTS.md` | 6, 10, 12, 14, 17 |
| `diagrams/pipeline.mermaid` / `chunk-translate-loop.mermaid` | 7, 13 / 10, 11 |
| `mockups/README.md`, `mockups/ui-mockup.html` (and the mockup-conformance tests) | 5, 6, 12, 14 and every group that changes a screen |
| `.claude/rules/llm-provider-integration.md` | 3, 5, 6, 14 |
| `.claude/rules/offline-and-privacy.md`, `.claude/rules/testing.md` | 5 |
| `docs/Architecture.md` | 12, 14 |
| `AGENTS.md` | 14 (module table row `:pipeline`), 17 ("Where it stands") |
| `docs/implementation_plan/01_MODULE_INVENTORY.md` | 17 (new packages `llm.engine`, `llm.profile`, `llm.knowledge`, `pipeline.proofread`) |

Not edited: the fifteen-error-code clauses, `persistence-sqlite.md`, `01_QUALITY_ATTRIBUTES.md`, the stop-word and
title-list sentences. Group 14 finds the remaining stale wording by a grep for judge, reviewer, backward revision,
rolling summary and last check across `docs/specification`, `.claude/rules` and `docs/next_features.md` §11–12,
instead of by the research's unlocated stale-doc ids. A delta cannot edit a capability's `## Purpose`, and OpenSpec
refuses a MODIFIED block that drops an original scenario name: the stale Purpose paragraphs of
`openspec/specs/{quality-gates,translation-pipeline,export}/spec.md` and the kept reviewer-era scenario titles are
fixed after archive, by `tasks.md`'s `## Workflow follow-up`, not in group 14. For the same reason a few renamed
requirements are written as an ADDED block with the new heading beside a REMOVED block of the old one (app-shell's
Enter, Escape and Tab keys; settings' Automation and Generation tabs and "Report what the program says"; book-brief's
estimate and narrator): each pair restates current behaviour under a new name with its edits, not new behaviour.

## Risks / Trade-offs

- [Lemma grouping merges two people or misses forms] → paradigm data work; clause evidence per mention. Falsifier: group
  precision < 0.95 or recall < 0.80 on the ru gold set; false swaps and losses on a scan replay fall by < 80 %.
- [Validators too strict] → demote that validator to a flag. Falsifier: more than half of rejected rows later accepted
  unchanged.
- [PINNED misflags Ukrainian alternations] → extend the uk paradigm, never unpin. Falsifier: > 1 false `name-spelling`
  per 200 uk segments.
- [Always-filled likely values are taken as facts] → confidence beside every likely value; Start lists low confidence
  first. Falsifier: > 1 in 10 likely genders confirmed unchanged later found wrong.
- [The builder's layout costs token fidelity (as the 15h.C5 reorder did)] → arm 5a; today's wording inside blocks 3–5.
  Falsifier: fidelity worse beyond noise or LTCR-1 −1.5.
- [Rules per book miss a rule a window needs] → that fact gated off (all rules). Falsifier: a rule-class failure that
  "all rules" avoids on a non-fiction case.
- [Chapter summaries mislead the draft] → the code gate; the person's switch. Falsifier: arm 6 "on" worse beyond noise;
  the gate drops > 20 % of sentences on the mini books.
- [The chapter rule fails on real structure] → the rule already joins continuation files and small chapters; a later
  change would read chapters from the navigation depth with most entries. Falsifier: on the owner's books more than 1
  in 5 chapters under 300 or over 15,000 source tokens.
- [One repair loses quality the rounds bought] → a second repair for BLOCK at Balanced and Max (later change).
  Falsifier: > 1 % of segments accepted only in rounds 2–3 with better text.
- [Same-script content-word residue stays at Balanced] → dial help says Max re-reads every paragraph; the owner may
  choose another generic signal. Falsifier: the closing ru→uk rerun at Balanced finds > 1 % of 100 random paragraphs
  with a source-language word.
- [The computed function-word set flags valid target words] → the hit becomes NOTE; the target file is completed.
  Falsifier: replay precision < 0.9, or > 1 false hit per 200 clean paragraphs on a later book.
- [Removing the source exemption flags deliberate source-language quotes] → kept-run marking in Prepare. Falsifier:
  false alphabet or residue findings on marked speech.
- [`terminal-mark` or `address-parity` noisy on en→uk] → demote to NOTE. Falsifier: precision < 0.7 or > 2 % of
  segments on a clean en→uk replay.
- [`LeadingDash` turns a dashed list into dialogue] → restrict to a letter or quote after the dash. Falsifier: > 1 wrong
  restore in 50.
- [Proofread makes text worse] → tighten `NeverWorse` before shipping. Falsifier: changed-paragraph grading < 3 better
  per worse, or any change a veto should have caught.
- [Reviewer removal costs accuracy on a small model] → arm 7a at the start of group 14; if the reviewer wins, it stays
  at Balanced and a later change may design a reviewer that only lists defects.
- [The never-worse weights are guesses] → tests assert vetoes; weights are named constants. Falsifier: arm 7b or the
  closing run grades round-2 changes worse than better.
- [Engine detection misfires] → two-signal body checks; the person's override. Falsifier: > 1 wrong engine in the live
  detection matrix, or an owner report of a wrong "Detected" line.
- [Engine versions drift] → `engineVersion` stored and logged. Falsifier: > 8 version branches in `EngineTraits` per
  release cycle; `cap-ignored` or `unclassified` lines after an update.
- [One engine needs its own reply pipeline] → a dedicated client for that engine. Falsifier: a second engine whose
  replies need another parse path.
- [Profiles resolved wrongly from metadata] → `liveLocal` contract runs; a class enum as fallback. Falsifier: > 5 rows
  repeating one bundle.
- [The name grammar is overfitted] → shrink to the listed forms. Falsifier: > 3 of the first 50 real ids wrong.
- [UNSWITCHABLE misjudged] → keep the path only for older servers, or send `think:false` and learn from the 400.
  Falsifier: probe P-A15-1 (a pulled `hf.co` Qwen3.5 GGUF, asked to the owner first) shows a renderer.
- [`UNSLOTH` is a needless engine, or impostors slip through] → collapse into GENERIC with a help line. Falsifier: no
  tool runs without the flag and a canary finds the off switch; > 1 impostor detected as Ollama.
- [The quant grid or the language line misleads] → one CSV edit; reword to "the maker says / BookLoom measured".
  Falsifier: the Ukrainian quant ladder orders two tiers wrongly; a DECLARED model fails while an UNKNOWN one passes,
  for two models.
- [Knowledge files go stale] → updated in the change that re-measures a model and once per release. Falsifier: a fact
  needs a change between releases more than twice.
- [Error rows miss real servers] → revisit rows. Falsifier: > 3 `unclassified` per 1,000 calls.
- [`keep_alive` 30m evicts the cache or reloads] → 10m or none (one constant).
- [The judge is not trustworthy] → screen only, manual reading, a local QE sidecar trial. Falsifier: calibration below
  the bars.
- [Mini books too noisy] → decide on `sequence`; mini only as a regression gate. Falsifier: K = 3 A/A noise wider than
  0.3 judge meaning.
- [New words break Linux fit tests] → `scripts/ci-linux-ui.sh` before each screen push; wrap, min-width, `FlowPane`.
- [Eight resets cause friction] → the owner is told before each; narrowing the fingerprint is the owner's call.
- [One change with 17 groups is long-lived] → each group merges green into the feature branch; nothing waits on an
  unmerged group.

## Migration Plan

- **No data migration** (ADR-0043). Groups 5, 6, 7, 10, 11, 12, 13 and 14 each change the stored-shape fingerprint; the
  next launch renames the old file to `bookloom.db.old-<time>` (never deleted) and starts fresh. Projects, providers,
  settings and per-model settings are entered again; nothing is carried over. The owner is told before each of those
  groups is merged.
- **Order.** The groups merge in the order of D1; a group never depends on an unmerged one.
- **Rollback.** Revert the group's squash commit on `feature/redesign-translation-pipeline`; a reverted
  shape-changing group sets the newer database aside the same way on the next launch.
- **Eval baselines.** Recorded in group 1 as the outputs of today's behaviour (that of `79ffede6`), with the commit that
  produced them; group 1's only `src/main` change is a test seam whose defaults keep that behaviour; every later
  comparison uses them.

## Open Questions

Only measurements whose every outcome is already planned, by data or by a named task (arms 4b, 5b, 5c and 7a edit a
requirement if their alternative wins); none needs a new decision.

- **Function-word residue precision** (group 4's local replay): ≥ 0.9 keeps the shipped REPAIR level; below, the
  group-10 task already planned sets NOTE (D12).
- **Arm outcomes** (D24): each arm's alternative and the task that applies it are named; the result column is filled
  when the arm runs.
- **ru→uk output ratio**: 1.5 until mini-ru and the closing rerun measure it; 1.2 becomes a pair-file value if under
  1 % of replies are cut by length.
- **Unsloth probes** (port, headers, whether its tools run by default, which off switch works): they set trait-row data
  and a scenario; the requirement "turn tools off where an engine runs them by default" holds either way.
- **P-A15-1** (`UNSWITCHABLE` on current Ollama): it decides whether the path is common or rare; the behaviour is
  written by facts, not by server version.
- **Data still to read**: the Qwen3 (119) and TranslateGemma (55) language lists stay UNKNOWN until read; the
  Ukrainian quant ladder may re-cut the CSV grid; Nemotron 3.5 Bolt's row waits for its off-switch probe.
- **Prefix reuse per runtime** and speed rows (seconds per call kind, calls per segment, whole-book time): reported in
  group 16, with no target.
