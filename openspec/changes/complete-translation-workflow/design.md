# Design

This design is the one place that fixes mechanisms, class names and numbers; the capability specs state the observable
behaviour, and every number the change invents is listed once in [Tuning constants](#tuning-constants).

## Context

See `proposal.md` — Why. The facts on `master` (c47f72f) the decisions below depend on:

- `:pipeline` — `TranslationJobImpl` (386 lines) opens a snapshot and walks every PENDING segment of every unit through
  `SegmentTranslator` (331 lines): one segment per call, strict `{"target":"…"}`, one structural and one placeholder
  repair, prompts as Java text blocks in `prompt/DraftPromptBuilder`. A blank `target` is already a structural failure
  (`DraftReplyParser:41`); blank reply content is already `emptyCompletion` (`SegmentTranslator:139`).
  `SegmentTranslator.decideModelError` flags a model-call `validation`, `emptyCompletion` or `contextWindow`; every
  other code reaches `JobControl.failureBoundary` (`JobControl`, 370 lines), which already pauses with `ON_ERROR` when
  that point is enabled — for any code, `internal` included — and redoes the step on resume
  (`TranslationJobRecoveryTest`). `JobProgressTracker` queues every PENDING segment and reports `units().size()` as the
  section count (`:47`, `:236–251`); `BookExporter:184–189` re-opens the written book and compares the full segment
  count. `TranslationRequest` appears in 28 files (11 main, 17 test).
- `:llm` — `GatedChatModel` behind one blocking `InferenceGate` and `RetryPolicy`; on a capability downgrade it rebuilds
  `ChatRequest` with the four-argument constructor (`GatedChatModel:122,130`). The clients' DTOs drop every usage field.
  The per-request timeout is `ProviderConfig.requestTimeout` (default `DEFAULT_REQUEST_TIMEOUT`, 3 minutes; the command
  line's `--timeout`). `PseudoChatModel` upper-cases the `<Text>` block of the last user message (or the whole message),
  keeping `⟦gN⟧` tokens and character references.
- `:document` — four readers/writers, masking to `⟦gN⟧` with a multiset gate (`mask/PlaceholderGate`), DRM
  adjudication. The parser classes (`OpfParser`, `ParsedEpub`, `ParsedOpf`, `ManifestItem`, `OpenEpubRegistry`,
  `ContainerReader`, `DrmAdjudicator`, `FontMediaTypes`, `XhtmlParser`; `ParsedFb2`, `Fb2Metadata`; `Frontmatter`,
  `ParsedMarkdown`; `FormatResolver`) are package-private. `ManifestItem(href, mediaType)` drops the manifest id and
  `properties`; nothing records the package `version`, `<meta name="cover">`, the guide or `spine@toc`. `TreeNode` has
  no attribute API. `Unit` requires an href, a media type and a skeleton handle; `Fb2Writer.bodyFor` (`:80–100`) and
  `EpubWriter.treeFor` (`:84–106`) need a parsed body or tree for every unit. `DocumentService.write` (`:273`) never
  unmasks: the pipeline stores the restored markup `unmask` returns, and `SkeletonAnchors` (`writeBlock` →
  `replaceChildren`, around `:127`) re-parses it into the node; `unmask` escapes text as markup for EPUB/FB2 and
  always runs `MarkdownEscaper` and `MarkdownStructureCheck` for Markdown (`:198–205`, `:276–283`).
  `Segment.prevKey`/`nextKey` are positional ids `unitId:order` (`BlockSegmentWalker:195–196`); `Segment.sourceHash`
  is `HashUtil.sha256OfNfcText` over the unmasked source. ICU4J is on `:document`'s module path; `:pipeline` has no
  ICU.
- `:ui` — FXML frames with code-built content; `StateMirror` fed by `RunSession` (391 lines) on 100 ms ticks;
  `ui/state/LogKind` with seven kinds; `SettingsViewModel` (399 lines); inert Names & style and Review entries; four
  disabled brief cards.
- `:persistence` — empty. `:app` — `CoreModules`, shared by the window and the command line.

Constraints that shape every decision: ADR-0022 (fifteen error codes), ADR-0033 (bound chat model; a job pauses in
place), ArchUnit `ports-not-concretes` and `fx-free-core`, the offline invariant, `records-first`, ADR-0034 … ADR-0041,
and the owner's decisions recorded in the proposal's assumptions.

## Goals / Non-Goals

**Goals:**

- Every decision a run makes is stored through a port, so review, export, retry and resume read one source of truth
  and SQLite can replace the adapters without touching callers.
- Every prompt the catalogue defines exists as a data template, carries the brief's style sheet, and returns the
  single-segment shape small local models follow.
- The UI binds only to the state mirror and to `:api` ports; no screen reaches a concrete pipeline class.

**Non-Goals:** (beyond the proposal's list)

- No streaming; throughput is derived from finished calls.
- No new `ErrorCode`; no change to `DocumentPort.open`'s refusal codes.
- No shared WireMock harness beyond the pipeline's existing `WireMockProvider`.
- No numeral masking and no automatic model pre-scan at run start (owner decision; the reference clauses are edited in
  task group 0).

## Decisions

### D1 — Packages and class sizes

Anchored to `docs/implementation_plan/01_MODULE_INVENTORY.md#inventory`; rows marked *planned* there become real.

| Module | Package | Holds |
|---|---|---|
| `:api` | `ua.bookloom.api.project` (new) | `Project`, `BookBrief` and its enums, `AlsoTranslate`, `SegmentRecord`, `SegmentCounts`, `SegmentPath`, `QaFinding`, `ContextSnapshot`, `SegmentLocator`, `GlossaryEntry`, `TmEntry`, `RollingSummary`, `Deferral`, `RunRecord`, `ChunkCommit` |
| `:api` | `ua.bookloom.api.persistence` (new) | `ProjectRepository`, `SegmentRepository`, `GlossaryRepository`, `TmRepository`, `SummaryRepository`, `DeferralRepository`, `RunRepository`, `CheckpointPort` |
| `:api` | `ua.bookloom.api.document` | + `BookInspector`, `BookInspection`, `InspectionVerdict`, `LanguageEvidence`, `BookProfile`, `StructureNode`, `BookStats`, `CoverImage`, `SentenceSplitter`, `AttributeAnchor`, `PlaceholderPair`, `Unit.AUXILIARY_ID` |
| `:api` | `ua.bookloom.api.pipeline` | + `ProjectService`, `GlossaryService`, `ReviewDesk` (with `ReviewFilter`, `SegmentView`, `ReviewCounts`), `ExportService`, `ExportJob`, `ExportRequest`, `ExportReport`, `RunRequest`, `ReviewMode`, `QualityDial`, `CallKind`, `ChunkPosition`, new `JobEvent`s |
| `:api` | `ua.bookloom.api.llm` | + `TokenUsage`; `ChatResponse.usage`; `ChatRequest.contextWindow`, `expectedOutputTokens`, `withoutResponseFormat()`, `withoutReasoning()`; `StageOutcome.elapsed/count` |
| `:util` | `ua.bookloom.util.lang` (new) | `Script`, `Language`, `Languages`, `LanguageTags.normalize` |
| `:persistence` | `ua.bookloom.persistence.memory` (new) | `InMemory*Repository`, `InMemoryCheckpoint` |
| `:document` | `.inspect` (new) | `BookInspectorService` (the one public `BookInspector` façade), `FormatInspection` (interface), word count |
| `:document` | `.epub`, `.fb2`, `.md`, `.txt` | + `EpubInspection`, `Fb2Inspection`, `MarkdownInspection`, `TxtInspection` (D11); each format's auxiliary slot table (D12); fidelity fixes (D14) |
| `:document` | `.split` (new) | `IcuSentenceSplitter` |
| `:pipeline` | `.prompt` | `PromptTemplates`, `PromptName`, `*.prompt` resources, `StyleSheet`, per-call builders |
| `:pipeline` | `.chunk`, `.context`, `.qa`, `.judge`, `.heal`, `.memory`, `.glossary`, `.revision`, `.dial` | as the inventory plans them (splitting moves to `:document`, D4) |
| `:pipeline` | `.run` (new) | `ChunkRunner`, `PrepStage`, `WorkList`, `PendingCommit`, `OutcomeRecords`, `JobModelCalls`, `RunRecorder`, `PauseDecider` |
| `:pipeline` | root package | + `WholeWord`, `Tokens` beside `DisplayText` (D10) |
| `:pipeline` | `.project`, `.review`, `.export` (new) | `OpenProjects` + `ProjectServiceImpl`; `ReviewDeskImpl` and its parts; `ExportServiceImpl`, `ExportJobImpl`, `SegmentVerification`, side-file writers, `RoundTripCheck` |
| `:ui` | `ua.bookloom.ui.state` | + `CurrentProject`, `ImportGuard`, `RunStarter`, `RunContext`, `ImportStates`, `ExportViewModel`, `LanguageNames`, `Genre`, `LiveChunkState`, `ThroughputMeter`, `RunClock`, `ActivityLogFeed`, `ProviderTestRunner` |
| `:ui` | `ua.bookloom.ui.control` (new) | `SearchableCombo`, `RunStatusBar`, `StepFooter`, `Banner`, `StatTile`, `LiveChunkPanel`, `ComparePanes`, `TaggedLog` |
| `:ui` | `ua.bookloom.ui.dialog` (new) | Replace run, Add term, Retry with note, Export complete |
| `:ui` | `ua.bookloom.ui.screen` | + Names & style; rebuilt Import, Brief, Structure, Translating (with review), Export, Providers |

Every new `:api`/`:util` package is exported. Every new package holding a class Guice constructs gets its `opens … to
com.google.guice` line (`:ui` packages also `to javafx.fxml`) in the task that creates it — `persistence.memory`,
`document.inspect`, `document.split`, `pipeline.run`, `pipeline.project`, `pipeline.review`, `pipeline.export`,
`pipeline.glossary`. No `opens` is added for `:pipeline`'s prompt resources: a module reads its own resources.

Classes at or near the ~400-line limit (measured at the group-8 checkpoint) and the extractions fixed here; the task
that first grows a class performs its extraction before adding to it:

| Class today | Keeps | Extracted |
|---|---|---|
| `TranslationJobImpl` (397) | lifecycle, stage order, event emission | `run.PrepStage` (style sheet, scan), `run.ChunkRunner` (one chunk's lifecycle, D4a), `run.WorkList` (records joined with the document's segments; replaces `JobProgressTracker`'s document walk), `run.PendingCommit` (the decided-since-last-commit buffer → `ChunkCommit`), `run.OutcomeRecords` (`SegmentOutcome` → `SegmentRecord`), `run.JobModelCalls` (the production `ModelCalls`), `run.RunRecorder` (`RunRecord` state writes only) |
| `JobControl` (370) | pause, resume and cancel requests; model-call interruption | `run.PauseDecider` (the failure routing of D3 and the review-mode pause points) |
| `SegmentTranslator` (361) | the draft step only: draft, structural repair, placeholder repair (D3 precedence) | acceptance and repair to `heal.AcceptanceRule`/`heal.QualityLoop` (D8); the draft-step prompt names to `prompt.DraftStep`; restore to `heal.GateFunction` |
| `heal.SegmentHealer` (391) | the rounds of one segment | `heal.RoundEvaluator` (evaluating a round's reply through the gate and the checks) |
| `BookExporter` (370) | writing and the atomic move | `export.SegmentVerification` (the per-segment re-open check, D13) |
| `OpenAiCompatibleClient` (396) | the HTTP exchange | `openai.OpenAiRequestMapper` (request DTO shaping, the output cap) |
| `RunSession` (366) | event dispatch to the mirror | `ui.state.LiveChunkState` (live rows), `ui.state.ThroughputMeter` (tokens per second), `ui.state.RunClock` (time left, elapsed time), `ui.state.ActivityLogFeed` (event → tagged entry with locator) |
| `StateMirror` (264, would pass 400) | the one `@Singleton` publish seam | section objects `live()` and `review()` (D15) |
| `TranslatingViewModel` (351) | screen state | `ui.state.RunStarter` (prepare, refusals, `RunContext`) |
| `ImportViewModel` (313) | screen state | `ui.state.ImportStates` (verdict → state and card, pure) |
| `BookBriefViewModel` (298, would pass 400) | the brief | `ui.state.ExportViewModel` (the destination members) |
| `SettingsViewModel` (399) | tab state | `ui.state.ProviderTestRunner` (the three test actions and measured badges) |
| `ReviewDeskImpl` (new) | the port | `review.SegmentActions` (status machine), `review.RetryDraft` (reuses the draft step, QA and the judge), `review.ReviewQueries`, `review.ReviewCounting` (shared with `ExportJobImpl`) |

`MessageKey` (422) stays one enum: it is the typed registry of catalogue keys, a flat list with no behaviour.
Test support is consolidated where it is first touched: one `TestDocuments` helper (today three copies), a
`UiTestInjector` builder instead of telescoping overloads, `ScreenConformanceTest` split into cases and preparations,
and a thread-safe `ScriptedChatModel` that can block and answer by call kind.

### D2 — Storage ports and in-memory adapters (ADR-0034)

- `SegmentRecord(projectId, segmentId, unitId, ord, kind, status, @Nullable machineTarget, @Nullable
  maskedMachineTarget, @Nullable userTarget, @Nullable maskedUserTarget, confidence, @Nullable judgeScore,
  List<QaFinding> findings, SegmentPath path, int repairRounds, boolean reviewed, @Nullable ContextSnapshot context)`.
  The effective target is `userTarget` when present, else `machineTarget`. `machineTarget` is the last target that
  passed every hard gate; a user edit never overwrites it. The **masked** forms are the text after protected-span
  restore and before `DocumentPort.unmask` — locked renderings and kept foreign runs substituted back, the document's
  own `⟦gN⟧` tokens still in place — so the review editor shows names, and `unmask` alone restores an edit. `reviewed`
  becomes true once a person accepted, saved, reverted, applied a proposal or retried successfully. A segment flagged
  at once (D3 rules 1–3) stores its reason as one more finding — `QaFinding(kind = the error code's name, HIGH, the
  error's message, raisedBy = "reply")` — so `SegmentRecord` needs no reason field and the review panel, the report
  and `SegmentView` show it like any other finding (`run.OutcomeRecords` builds records from `heal.SegmentOutcome`).
- `SegmentPath` = `DRAFT | TM_REUSE | REPAIRED | USER | SOURCE_KEPT`. `SOURCE_KEPT` is never stored; review views report
  it for a record kept as source by choice (below).
- `ContextSnapshot(precedingTargets, List<SnapshotTerm> glossary, List<SnapshotTmHit> tmHits, @Nullable summary,
  styleSheet)` stores the **texts** the draft saw — each injected entry as (term, target, type, gender, locked), each
  memory hit as (kind, source, target), the summary text and the style-sheet text — so Retry replays exactly with no
  repository lookup. The context assembler runs for every segment, a memory reuse included, so every decided record
  carries one.
- `Deferral(id, projectId, segmentId, reason, @Nullable waitingOn, @Nullable replacedRendering, @Nullable proposal,
  @Nullable maskedProposal)` — a revision proposal in both forms, so applying it stores both targets. `waitingOn`
  holds the glossary term a TERM or GENDER deferral waits on, and the judge's text for a JUDGE one. The adapters key a
  deferral by `(segmentId, reason, waitingOn)` — not `(segmentId, reason)` — so two changed terms, or two characters
  of unknown gender, in one segment are two deferrals.
- `RunRecord(runId, projectId, startedAt, @Nullable endedAt, JobState state, accepted, flagged)`. `state` is the run's
  current state, written by `run.RunRecorder`: `RUNNING` at start, `PAUSED` and `RUNNING` again on every pause and
  resume, then the terminal `COMPLETED`, `CANCELLED` or `FAILED` with `endedAt`. `RunRepository.save` upserts by run id;
  `latest(projectId)` is what review reads (D9).
- `SegmentLocator.of(kind, unitOrdinal, segmentOrdinal)` is the one display rule. A body segment reads `ch<n> · p<mm>`:
  `n` its unit's 1-based position among body units, `mm` its 1-based position in its unit, zero-padded to two digits
  (`ch5 · p12`, `ch1 · p03`). `unitOrdinal` 0 is the auxiliary unit, whose segments read by kind: `title`, `author ·
  <kk>`, `description · <kk>`, `nav · <kk>` (NCX labels included), `page title · <kk>`, `alt · <kk>`, `frontmatter ·
  <kk>`, `kk` the 1-based position among the auxiliary segments of that kind. Ports, events and tests address segments
  by id; people read locators.
- **Kept as source by choice.** A record of the auxiliary unit whose kind the brief's "Also translate" switches exclude
  is neither pending nor translated, judged by the brief at the time of the read, whatever its stored status.
  `AlsoTranslate.keptKinds()` answers the excluded kinds (an auxiliary `TITLE` follows the navigation switch); body
  records never consult it. The reads the job and the screens use take that set: `firstPending(projectId,
  keptAuxiliaryKinds)` skips such records; `countsByStatus(projectId, keptAuxiliaryKinds) → SegmentCounts(pending,
  accepted, revised, flagged, sourceKept)` counts them only as `sourceKept`; `flagged(projectId, keptAuxiliaryKinds)`
  omits them. Progress, time left, `ReviewCounts.pending` and the partial-export statement exclude them;
  `ReviewCounts.sourceKept` and `ExportReport.sourceKept` report them apart (D13). Export writes them as source.
- **Commits.** `CheckpointPort.commit(ChunkCommit)` applies one set of segment records with the TM entries, deferrals
  and glossary additions that belong to them, all or nothing. A commit holds the decided segments of a chunk: the
  decided prefix is committed before every pause and at stop or end, and the rest at the chunk's end, so one chunk may
  take several commits. A commit holds **only the segments decided since the previous commit** and never re-writes a
  record the run already committed (`run.PendingCommit`): the in-memory checkpoint replaces whole records, so re-sending
  a committed record would erase an edit the person saved during a pause. Undecided drafts are never committed. A glossary addition whose term already exists, or was
  removed by the person in this session, is skipped and the existing entry kept — a commit never fails for it; an
  unknown segment id is the only refusal (`validation`). `GlossaryRepository.remove` remembers the removed term for
  that purpose; the person adding it again (Add term, CSV import) clears the memory.
- `ua.bookloom.persistence.memory` implements the ports with `ConcurrentHashMap` and `compute`; the checkpoint takes the
  store's write lock so a commit is never half-visible. A shared abstract `RepositoryContractTest` is written against
  the ports, so the SQLite change reuses it.
- The opened `Document` is not stored; `pipeline.project.OpenProjects` holds it per project id and re-opens the source
  fresh for export and the round-trip check (a written book mutates the registry-held tree — backlog D4).

### D3 — Service ports, the reshaped job and failure routing (ADR-0035)

- `ProjectService`: `importBook(Path) → ImportedBook(projectId?, BookInspection, BookProfile?, BookBrief?)` (the stored default brief,
  source preselected, so no screen re-derives the preselection), `updateBrief`,
  `plan(projectId) → BookPlan(chunks per unit, oversized segment ids)`, `roundTrip(projectId) →
  RoundTripReport(structurePreserved, idsPreserved, missingIds, sourceSegments, copySegments)` (body segment counts of
  the source and the written copy), `close(projectId)`.
- `TranslationEngine.newJob(RunRequest(projectId, ReviewMode), ChatModel)`. `TranslationRequest` is deleted; its
  destination and overwrite move to `ExportRequest`, its languages to the brief.
- `JobStage` = `PREP | TRANSLATE | REVISE`. `PREP` builds the style sheet and, when the glossary is empty, runs the
  deterministic scan over the whole book. `REVISE` runs only on Max. `JobReport` loses `written` and the "completed
  implies written" invariant; `ExportReport` is separate (D13). Its counts are the project's counts when the run
  ends (`countsByStatus`), not only this run's decisions, so a run resumed after a stop reports the whole book. A
  segment flagged after its repair rounds has no error of its own; `FlaggedSegment` and `SegmentDecided.reason` report
  it as `validation`.
- A new job starts at `firstPending(projectId, keptAuxiliaryKinds)`; that is how a stopped run resumes within the
  session. FLAGGED stays terminal for the run (FR-RESUME-01).
- Pause points: the window runs every mode with its own points plus `ON_ERROR` (Unattended gets `{ON_ERROR}`); the
  command line runs with `pauseAt(Set.of())`, Unattended, and the brief's default dial (Balanced).
- **Failure routing** of an error a model call answers during a run — a pure `run.PauseDecider.route(ErrorCode)`,
  landed on today's per-segment path **before** the chunk runner, because a chunk's judge call names no segment and
  must already pause correctly when chunks arrive:

  | Code | Effect on the segment | Effect on the run |
  |---|---|---|
  | `unreachable`, `timeout`, `auth`, `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable`, `missingCredential`, and `validation` answered by the call (a provider `400` such as LM Studio's `Model unloaded`) | stays PENDING | pauses `ON_ERROR` with the error and redoes the interrupted call on resume; Failed where `ON_ERROR` is not enabled (the command line) |
  | `contextWindow`, `emptyCompletion` | FLAGGED at once, no repair | goes on |
  | `cancelled` | stays PENDING | Cancelled |
  | `internal`, a thrown call, a restore failing with anything but `validation`, and any code not listed | stays PENDING | Failed with the blocking dialog; never pauses, whatever the pause points |

  A run's model call cannot answer `busy` (the gate blocks rather than refusing) or `discoveryFailed` (a run makes no
  discovery call); if either ever arrived it would be treated as `internal`. A `validation` refusing a start, before
  any model call, stays a refusal shown in place. Against today's code the change is: a model-call `validation` moves
  from flag to pause, `internal` stops pausing, and the window always enables `ON_ERROR`; the pause-and-redo mechanism
  itself exists. This closes backlog D19.
- **Reply precedence** for a draft (the same order for the reply of each of its two repairs):
  1. The call answered an error → the routing table.
  2. The reply content is blank → `emptyCompletion`, FLAGGED at once.
  3. The finish is not a normal stop (a length cut) → `validation` "Incomplete model response", FLAGGED at once.
  4. The reply is not exactly one non-blank string `target` (`DraftReplyParser`) → one structural repair; a second
     structural failure → FLAGGED at once (`validation`).
  5. A protected-span token is missing or repeated (D10), or the document gate refuses the restore (multiset, pair
     order and nesting, the pair rules of D14) → one placeholder repair naming the expected token sequence; still
     failing → self-heal with a `markup` finding (a directed fix carrying the expected tokens), FLAGGED only after N
     rounds (D8).
  6. The restored target is empty after removing tokens and whitespace while the source is not, or starts with a
     refusal phrase → the refusal hard gate fails → self-heal.
  7. Otherwise the target goes to the checks and the acceptance rule (D8).

  The structural and the placeholder repair are each used at most once per draft and never count toward N. Rules 1–3
  apply to every self-heal call too; a self-heal reply failing rule 4 or 5 fails that round, and the loop moves to the
  next. A FLAGGED segment keeps as machine target the last target that passed every hard gate, or none.
- `JobControl.exitModelCall` clears the interrupt only when `interruptModelCall` fired (next_features §15).

### D4 — Chunks, budget and oversized segments (ADR-0038)

- `chunk.TokenEstimator`: `ceil(chars / K(script) × 1.15)`, K from the language's `util.lang.Script` — Latin 4.0,
  Cyrillic 3.0, Greek 3.5, Han, Japanese and Hangul 1.5, unknown 3.0 (`05_TRANSLATION_ALGORITHM.md#token-budget`).
  Source text uses the source language's K; the output allowance uses the target's.
- Output allowance of one segment: `ceil(chars(source display text) × hi / K(target) × 1.15)`, `hi` the upper bound of
  the pair's length band (D8, unwidened). It is also the request's `expectedOutputTokens` (D6).
- `chunkBudget = min(8192 − reservedHeadroom, 1200)`; 8192 is the constant effective context sent as Ollama `num_ctx`
  (D6); the headroom sums what is known when a unit is packed — the style sheet, the current summary, the injected
  terms and the output allowance of a full chunk (`TokenBudget.fullChunkAllowance`, 2,880 tokens for en→uk). The
  preceding window (at most 3 targets) and memory hits are not known then and count as zero; the 8192-token context
  leaves room for them.
- `chunk.ChunkPacker` packs consecutive pending segments of one unit (records kept as source excluded) until the budget
  or the dial's segment cap (Fast 8, Balanced 4, Max 2; Manual review forces 1). A unit boundary always closes a chunk
  and soft-resets the preceding window.
- **Splitting** (`split.IcuSentenceSplitter`, behind the `:api` `SentenceSplitter` port): a segment whose masked text
  alone exceeds the budget is split at sentence boundaries.
  1. Replace every `⟦gN⟧` token with U+FFFC (OBJECT REPLACEMENT CHARACTER), keeping an offset map.
  2. Run `BreakIterator.getSentenceInstance(ULocale.forLanguageTag(sourceLanguage))`; map each boundary back.
  3. Move each boundary forward past any closing or atomic tokens that immediately follow it, and past the whitespace
     after them; an opening token stays after the boundary.
  4. Drop a boundary at which a pair is still open (depth over the segment's recorded pairs; a protected-span token is
     atomic).
  5. The pieces concatenate exactly to the input.

  Why: on raw masked text ICU rule SB8 reads `⟦` as Close and the following `g` as Lower, "sentence continues", so
  `⟦g0⟧He left. She stayed.⟦g1⟧ Night fell.` loses the boundary after `⟦g1⟧ ` and `He left. ⟦g0⟧She⟦g1⟧ stayed.`
  loses the one before `⟦g0⟧`; a letter stand-in does the same; a Format-class stand-in (U+2060) attaches to the
  preceding space and puts the boundary after an opening token, where the pair is open. With U+FFFC the spike gives
  `⟦g0⟧He left. She stayed.⟦g1⟧ ` · `Night fell.`, `He left. ` · `⟦g0⟧She⟦g1⟧ stayed. ` · `Night fell.`, and
  `He left.` · `⟦g0⟧ She stayed.⟦g1⟧ ` · `Night fell.`; Ukrainian `Він пішов. Вона лишилася.` splits as before.
- The splitter sits in `:document`, although `01_MODULE_INVENTORY.md` planned it in `pipeline.chunk`: ICU4J, the
  token grammar and the pair records live there and `:pipeline` has no ICU; the port costs one `:api` interface. The
  inventory row is corrected at the gate.
- Each piece is drafted with the segment's context; the joined target is gated as one segment. A segment that cannot
  be split without separating a pair is drafted alone with the preceding window and TM hits dropped, and one WARN line
  names it (FR-ALGO-C2b). This closes backlog D5.

### D4a — Chunk lifecycle (ADR-0033, ADR-0034, ADR-0038)

`run.ChunkRunner` takes one chunk through three phases, always in document order. It stays small by delegating:
`run.WorkList` (the project's records joined with the opened document's segments, units and sections, kept kinds),
`run.PendingCommit` (the segments decided since the last commit, with their TM entries, deferrals and glossary
additions, built into one `ChunkCommit`), `run.OutcomeRecords` (`heal.SegmentOutcome` → `SegmentRecord`) and
`run.JobModelCalls` (the production `prompt.ModelCalls`: call kind, events, elapsed time, usage). The judge on or off
is a boolean branch, not a strategy type.

1. **Draft phase.** For each segment: assemble its context package (D10); a context-matched memory reuse is checked at
   once (hard gates, checks, τ — D8) and, when it passes, skips the draft and the judge and is decided in its turn in
   phase 3; otherwise the segment is drafted through the draft step (D3). The preceding targets of each draft are the
   effective targets of the earlier segments — same-chunk drafts and reuses included.
2. **Judge** (when the dial enables it): one call over the pairs that passed their hard gates and were not reused,
   labelled `s1…sk` in document order; no call when none qualify.
3. **Decisions**, one segment at a time: the acceptance rule, then self-heal rounds for a failing segment (D8), then
   ACCEPTED or FLAGGED. Each decision may pause.

When the judge is off (Fast), phases 1 and 3 run per segment instead — each segment is drafted and decided before the
next is drafted — because nothing needs the whole chunk first; the preceding targets are then decided targets.

Pause, stop and resume:

- A pause — requested, for review, or on error — holds the job in place (ADR-0033) with the chunk's undecided drafts in
  memory. The decided prefix since the last commit is committed before the job waits.
- On resume the paused segment's record is re-read, so a REVISED target feeds every segment drafted after the resume;
  drafts already made in the chunk are kept, not redrafted. Glossary edits made during the pause apply from the next
  chunk, which re-reads the glossary.
- A model call interrupted by a pause — a draft, a repair or the judge — is redone on resume. For a segment in its
  self-heal rounds the rounds already answered are kept in memory (`heal.Resumption`, held by `SegmentHealer` per
  segment), so the resume continues at the call that failed — the round's repair call, or only its re-judge when the
  repair had answered. This replaces the earlier "restart from round 1" (owner decision of 2026-09-29), which on the
  Bartimaeus hand test paid for the same directed fix after every resume (tasks 15b). The same holds when a self-heal
  call answers an error that pauses the run (D3). A step that pauses the run twice is flagged on its third failure
  (`run.RoutedCalls.PAUSES_BEFORE_FLAGGING`), and a paused run can skip the failing step
  (`TranslationJob.skipSegment`), so no step can hold a run in a pause loop.
- A stop commits the decided prefix and drops the undecided drafts; those segments stay PENDING and the next run drafts
  them again from `firstPending`.
- Consequences the specs state: with the judge on (Balanced, Max), a review pause on `s2` of a four-segment chunk comes
  after `s3` and `s4` were drafted and judged, so their draft calls precede the pause and an edit made during it feeds
  the next chunk, not `s3`/`s4`; with the judge off (Fast) or one segment per chunk (Manual review), no call for the
  next segment precedes the pause and an edit feeds its draft.

### D5 — Events, live view and throughput

New or extended `JobEvent`s (the sealed list grows; every consumer switch is exhaustive):

- `SegmentStarted(segmentId, locator, displaySource, ChunkPosition(section, sections, chunk, chunks))`
- `SegmentDrafted(segmentId, displayTarget, confidence)`
- `SegmentDecided(…)` + `@Nullable SegmentDetail(judgeScore, path, findingKinds)`
- `ModelCallStarted(segmentId?, CallKind)` — `DRAFT, STRUCTURAL_REPAIR, PLACEHOLDER_REPAIR, JUDGE, DIRECTED_FIX,
  REFLECT, IMPROVE, POLISH, PRESCAN, SUMMARY, REVISION`
- `ModelCallFinished(segmentId?, CallKind, Duration elapsed, @Nullable TokenUsage usage, int outputChars, boolean
  usageEstimated)`
- `MemoryUpdated(MemoryKind{GLOSSARY, SUMMARY, TM}, String label)` — GLOSSARY when a scan (PREP or a unit end) adds at
  least one entry, labelled `+n`; TM on each memory reuse, labelled with the segment's locator; SUMMARY on each refresh,
  labelled with the new version. Nothing else emits it.
- `JobProgress` keeps `section, sections` and adds only `chunk, chunks, autoAccepted, repairedAccepted`. `section` is the
  1-based position of the current unit among **body** units and `sections` their count; the auxiliary unit is never
  counted, and while it runs `section = sections`. `chunk/chunks` count within the current unit (ADR-0038). `pending`
  excludes records kept as source.
- `Paused` + `@Nullable String segmentId`

Display text is the masked text with its tokens removed and whitespace collapsed; it may travel in events, never in a
log line above TRACE. `RunSession` turns events into mirror state on its 100 ms tick:

- **Live panel** (`LiveChunkState`): row 1 is the most recently decided segment (source, target, judge badge, path
  badge); row 2 is the most recently started segment not yet decided (source; "waiting for the model…" until its
  `SegmentDrafted`, then its draft, marked "awaiting judge" while the judge is on). A `SegmentDecided` for the row-2
  segment moves it to row 1 and empties row 2 until the next `SegmentStarted`.
- **Tokens per second** (`ThroughputMeter`): completion tokens ÷ generation time over the last 20 `DRAFT` calls
  finished, from the usage `ModelCallFinished` carries; when the provider reported none the pipeline has already
  estimated completion tokens with `TokenEstimator` from `outputChars` and set `usageEstimated` (shown with a leading
  `~`), so `:ui` never estimates.
- **Time left** (`RunClock`): the plain average of wall seconds per decided segment over the last 20 timed segments ×
  remaining pending segments (group 15b step 6: the EWMA α 0.2 swung from minutes to hours on one slow segment); hidden
  until 5 segments are decided in this run. **Elapsed** excludes paused time.
- **Log** (`ActivityLogFeed`): the existing seven `LogKind`s gain their tags — `ACCEPTED` `ok`, `REPAIRED` `fix`,
  `GLOSSARY_APPLIED` `mem`, `SUMMARY_UPDATED` `sum`, `RETRIED` `retry`, `SEGMENT_ERROR` `err`, `MILESTONE` `info`
  (FR-NOTIF-6a) — rendered monospace with the tag first. `ok` an accepted segment; `fix` each repair call (directed fix,
  reflect, improve, polish); `retry` a structural or placeholder repair and the resume after an error pause; `mem` the
  glossary or memory applied or updated; `sum` the summary refreshed; `err` a flagged segment; `info` run milestones.
  An entry names a segment by its locator (`ch7 · p42`), never its id; `RunSession` keeps the id → locator map from
  `SegmentStarted`.

### D6 — Token usage, context size, timeouts and the pseudo model

- `TokenUsage(@Nullable Integer prompt, @Nullable Integer completion, @Nullable Duration generation)`; `ChatResponse`
  gains a nullable `usage`, keeping the two-argument constructor.
- Ollama: `prompt_eval_count`, `eval_count`, `eval_duration` (nanoseconds). OpenAI-compatible: `usage.prompt_tokens`,
  `usage.completion_tokens`, wall time as the duration. Absent fields stay null.
- `ChatRequest` gains nullable `contextWindow` (→ Ollama `options.num_ctx`; the OpenAI-compatible client omits it),
  `expectedOutputTokens` and `maxOutputTokens`, keeping its one-, three- and four-argument constructors, and the copy methods
  `withoutResponseFormat()` and `withoutReasoning()`, which keep every other field. `GatedChatModel:122,130` use them, so
  a capability downgrade no longer drops the context size, the expected output or the cap. The engine sends 8192.
- **Output cap** (owner decision, 2026-09-29). A call that states an expected output also states a cap,
  `chunk.TokenEstimator.outputCap(allowance, tokens) = max(64, ⌈1.5 × allowance⌉ + 16 + 6 × tokens)` — the margin
  covers the `{"target":…}` wrapper and each `⟦gN⟧` the reply must return, which the display-text allowance does not
  count. `ChatRequests.build` carries both through `OutputLimit(expected, cap)`. Ollama receives it as
  `options.num_predict` (`OllamaChatRequest.Options(temperature, numCtx, numPredict)`), an OpenAI-compatible server as
  `max_tokens` (mapped by an `openai.OpenAiRequestMapper` extracted from the 396-line `OpenAiCompatibleClient`); a
  request with no cap sends neither. Judge, reflect, pre-scan, summary and the verifier state no expected output and
  therefore no cap. A reply cut at the cap finishes with `length` and is flagged at once (D3 rule 3) instead of running
  into the timeout: at the checkpoint `qwen2.5:1.5b` looped on one paragraph for three 3-minute timeouts. `max_tokens`
  counts reasoning tokens on a thinking model; reasoning is already sent low/off.
- **Timeout** of a chat call: `effective = max(configured, min(600 s, expectedOutputTokens × 0.5 s))`. The configured
  request timeout (default 3 minutes, or the command line's `--timeout`) is the floor and is always honoured; long
  outputs get up to 600 s; a request stating no expected output uses the configured timeout. Draft, directed fix,
  improve, polish and revision state their output allowance (D4), and a structural or placeholder repair states the
  allowance of the draft it repairs; judge, reflect, pre-scan, summary and the verifier state none. (For a configured timeout of 600 s or less this equals `min(600 s, max(configured, expected × 0.5 s))`.)
- `StageOutcome` gains `@Nullable Duration elapsed` and `@Nullable Integer count` (models listed) for the Providers
  badges.
- `PseudoChatModel` answers by `ResponseFormat` name: draft, directed fix, improve, polish, revision → the upper-cased
  `<Text>` block; judge → `{"score":1.0,"verdict":"accept","findings":[],"deferrals":[]}`; reflect → `{"issues":[]}`;
  pre-scan → the capitalised words; summary → an empty summary. It reports no usage, which exercises the estimate path.
  Its upper-cased echo is flagged in every mode (D8).

### D7 — Prompts as data and the style sheet

- Templates are `modules/pipeline/src/main/resources/ua/bookloom/pipeline/prompt/<name>.system.prompt` and
  `<name>.user.prompt` with `{{slot}}` placeholders and `{{#slot}}…{{/slot}}` for an optional block omitted when empty.
  `PromptTemplates` loads them once, checks every slot against the call's declared slots, and fails injector creation
  on a mismatch. Names: `draft`, `structural-repair`, `placeholder-repair`, `judge`, `directed-fix`, `reflect`,
  `improve`, `polish`, `prescan`, `summary`, `revision`. Each `PromptName` constant carries its temperature
  ([Tuning constants](#tuning-constants)); reasoning is off on every call.
- Before migrating, a golden test pins today's `DraftPromptBuilder` messages byte for byte; the template must
  reproduce them when the brief is the default and no memory exists.
- Every generation call (draft, directed fix, improve, polish, revision) returns exactly `{"target":"…"}` for one
  segment; the catalogue's multi-segment `segments[]`/`revisions[]` shapes are edited to that. Judge, reflect, pre-scan
  and summary keep their catalogue shapes, read tolerantly (absent arrays empty, unknown fields ignored; an unparseable
  judge reply accepts nothing).
- Every repair template (directed fix, reflect, improve, polish, revision) carries exactly one `<Text>` block holding
  only the masked text to rewrite: the rejected target, or the masked source when the finding is a refusal or an empty
  target. The source appears under a `[Source]` label outside it.
- `StyleSheet.from(BookBrief)` is deterministic: each enum constant carries a prompt phrase from
  `style-phrases.properties` (English, because prompts are English); genre, voice/era and audience are inserted
  verbatim; the faithful↔natural value maps to five bands; policies expand to rules; the foreign-passage rule follows
  `12_PROMPT_CATALOG.md#prompt-construction`. The footnote policy acts on segments inside FB2 notes bodies and EPUB
  `epub:type` footnote/endnote elements; "Translate + note" asks for an inline parenthetical gloss, because the skeleton
  cannot gain elements. The unit policy "Metric" asks the model to convert measurements in prose.

### D8 — Quality gates (ADR-0038)

All checks read **display texts**: the masked source and the masked reply with every token — document and protected —
removed and whitespace collapsed. So locked terms and kept foreign runs never count toward script, echo or length.

- **Hard gates** (fail → not acceptable; excluded from confidence; raise a `high` finding):
  - placeholder integrity — multiset, pair order and nesting (ADR-0040), and the pair rules of D14 — checked in
    `DocumentService.unmask` from pairing recorded at mask time (finding `markup`);
  - protected spans — each locked-term and kept-foreign-run token exactly once — checked in `:pipeline` before
    `unmask` (finding `glossary` for a locked term, `markup` for a kept foreign run);
  - refusal — the target display text is empty while the source's is not, or it starts with a refusal phrase of
    English, the source language or the target language (finding `meaning`). Phrases (`qa.RefusalPhrases`) are
    anchored prefixes compared case-insensitively (`Locale.ROOT`) against the trimmed display text, with `’` read as
    `'`, each matching only when the end of the text or a non-letter, non-digit follows it, and skipped when the
    source's display text itself starts with a phrase; every phrase names the task or the model, so a character's own
    apology is never caught:
    - `en`: `I'm sorry, but I can't translate`, `I am sorry, but I cannot translate`, `I cannot translate`,
      `I can't translate`, `I am unable to translate`, `I'm unable to translate`, `As an AI`, `As a language model`,
      `Here is the translation`, `Here's the translation`, `Translation:`, `Sure, here is the translation`;
    - `uk`: `Вибачте, я не можу перекласти`, `Я не можу перекласти`, `Не можу перекласти`, `Як мовна модель`,
      `Як ШІ`, `Ось переклад`, `Переклад:`.
- **Soft checks** (margin 0–1; a failed check has margin 0.0, a skipped one 1.0):

  | Check | Fails when | Margin when passing | Skipped when |
  |---|---|---|---|
  | target script | share of the target's letters in the target language's script < 0.60 | `clamp((share − 0.60) / 0.20)` | source display text < 20 code points; the two languages share a script; the target has no letters |
  | untranslated echo | similarity ≥ 0.90 | `clamp((0.90 − similarity) / 0.10)` | — |
  | repetition | a 3-gram repeated ≥ 3 times consecutively | 1.0 with no repeated run, 0.5 for a run of 2 | — |
  | length ratio | `len(target)/len(source)` outside the pair's band | `clamp(distance to the nearer bound / (0.10 × band width))` | — |
  | glossary compliance | a locked term in the segment is not rendered as entered | fraction of in-segment locked terms rendered | no locked term in the segment |

  - Echo similarity = `1 − Levenshtein / max(length)` over the NFC-normalized, `toLowerCase(Locale.ROOT)` display
    texts (`qa.TextSimilarity`, reused by the TM).
  - The script share excludes protected spans (already removed) and, under the "keep original" name policy, every
    whole-word occurrence of a glossary term; the same removal applies to both texts before the echo similarity and the
    echo floor, so a names-only line kept by policy is not an echo. It uses `util.lang.Script`; `document/detect/ForeignWordCoherence` keeps its own
    and no third copy is written. Latin-script Serbian output fails the check (Serbian is catalogued as Cyrillic).
  - Length bands (`05_PIPELINE_ENGINE.md#qa-thresholds`): Latin→Cyrillic `[0.7, 1.8]`, same script `[0.6, 1.7]`,
    Latin→CJK `[0.2, 1.0]`, CJK→Latin `[1.0, 5.0]`, otherwise `[0.5, 2.5]`; for a source under 25 characters the lower
    bound × 0.5 and the upper × 2.
  - Glossary compliance is 1.0 whenever the protected-span gate passes (locked terms are masked); it stays in the blend
    because the reference weights include it.
- **Failed outright.** A failed soft check blocks acceptance and raises a `medium` finding — `language` (script, echo),
  `fluency` (repetition), `omission` (length), `glossary` — which sends the segment to a directed fix. Exception, the
  **echo floor**: when the source display text has fewer than 20 code points, a failed echo only contributes its 0.0
  margin and records a `low` finding; it does not block.
- `confidence = 0.30·glossary + 0.25·length + 0.20·script + 0.15·echo + 0.10·repetition` (`#confidence`), summed in
  that fixed order — glossary, length, script, echo, repetition — and compared with τ with a tolerance of 1e-9
  (`confidence ≥ τ − 1e-9`), so a floating-point sum a hair below τ still meets it: `YES, SIR.` at 0.85 is accepted in
  Manual (τ 0.85).
- **Foreign keep:** under the Keep policy a segment is marked foreign when its block's declared language (recorded on
  the segment, D11) differs from the brief's source language after `LanguageTags.normalize`, or its dominant script
  differs from the source language's; a marked segment skips echo and script. Kept foreign runs inside a segment are
  protected spans (D10).
- **Accept** (`heal.AcceptanceRule`): hard gates pass ∧ no soft check failed outright ∧ confidence ≥ τ − 1e-9 ∧ (judge
  off ∨ (score ≥ τ_judge ∧ no medium or high judge finding on the segment)). τ comes from `ReviewMode`; τ_judge = τ;
  the verdict string never decides; an unreadable judge reply accepts nothing.
- **Memory reuse exception:** a context-matched TM reuse is accepted on hard gates ∧ no failed check ∧ confidence ≥ τ,
  without the judge, and is never sent to the chunk's judge; a reuse that fails is discarded and the segment drafted
  in its place (path `TM_REUSE` only when accepted).
- **Judge input:** only pairs that passed their hard gates and were not reused, masked source and masked candidate,
  labelled `s1…sk`.
- **Self-heal** (`heal.QualityLoop`), up to N rounds (the dial's repair budget), per failing segment:
  - a **directed fix** when the segment has a concrete finding — a failed hard gate (a `markup` failure carries the
    expected token sequence), a check failed outright, or a medium or high judge finding;
  - otherwise **nothing**: reflect → improve → polish and the near-miss polish window were removed in 15d.7 (the
    acceptance rule no longer reaches them and no measurement showed they help); a segment with no finding to name is
    flagged without a call. The path carries a **best candidate** (`heal.BestCandidate`: fewest failed hard gates, then
    fewest blockers); a step replaces it only when `heal.RoundProgress` finds fewer blockers, otherwise the step is
    discarded and the path stops; the judge is gone (15d.6), so a repaired target is decided by the checks alone;
  - after N rounds the segment is FLAGGED with every finding; accepted after ≥ 1 round, its path is `REPAIRED`.
- **Reference fixtures:** en→uk `He opened the old door.` (23 code points) → the pseudo model's `HE OPENED THE OLD
  DOOR.` fails echo and script outright, is repaired N times with the same answer and is FLAGGED in every mode;
  `Yes, sir.` → `YES, SIR.` has confidence exactly 0.85 (echo below the floor, script skipped) and is accepted in
  every mode; `It was a dark night.` is exactly 20 code points, so its echo blocks.
- **Main-finding badge** in the review list: the finding with the highest severity, ties broken `glossary` (name) >
  `language` (wrong lang?) > `omission` (omission) > anything else (low score).
- `dial.DialParameters`: preceding targets 1/2/3, repair budget 1/2/3, judge off/on/on, backward revision off/off/on,
  LLM summary at unit end off/off/on, chunk cap 8/4/2.

### D9 — Review modes and the review desk (ADR-0036)

- `ReviewMode` constants `UNATTENDED(0.60)`, `ASSISTED(0.75)`, `MANUAL(0.85)` carry τ as a field; their pause points are
  none / `{ON_FLAGGED, ON_ERROR}` / `{AFTER_SEGMENT, ON_FLAGGED, ON_ERROR}`, and the window adds `ON_ERROR` to
  Unattended (D3). `PausePoint.ON_FLAGGED` and `PauseReason.ON_FLAGGED` are added.
- `ua.bookloom.app.bootstrap.ReviewModeResolver` mirrors `LoggingLevelResolver`: env `BOOKLOOM_REVIEW_MODE`, then property
  `bookloom.review.mode`; accepted values (any case) `unattended|auto`, `assisted|semi-manual`, `manual`; else
  `UNATTENDED` and one WARN. The value is part of `StartupContext` and bound as an instance; the command line ignores it
  and logs that it did.
- `ReviewDesk` (`pipeline.review.ReviewDeskImpl`) acts on `SegmentRepository`. Every action sets `reviewed` except
  `skip` and a failing retry:
  - `accept` — FLAGGED → ACCEPTED with the machine target; ACCEPTED → ACCEPTED (a confirmation, Manual's "Accept &
    continue"); on any other status it answers `validation` and changes nothing, and so it does on a segment with no
    machine target ("There is no machine translation to accept — edit it or retry."). A PENDING segment is never
    accepted by a person: a paused run's segment is already decided when the pause happens (D4a).
  - `saveEdit(maskedText)` — FLAGGED, ACCEPTED or REVISED → REVISED. The text is the masked edit as the editor shows it;
    `DocumentPort.unmask` restores it, and both forms are stored (`userTarget`, `maskedUserTarget`). A failed restore
    is a `validation` result shown in place, the record unchanged.
  - `revert` — REVISED → ACCEPTED, both user forms cleared.
  - `retry(note?, lowerTemperature)` — on FLAGGED or ACCEPTED only. It replays the stored `ContextSnapshot` (locked
    terms masked from the snapshot's locked entries), runs the draft step at 0.2, or 0.1 when a lower temperature is
    asked, then the gates, checks and — when the brief's dial enables the judge — a one-pair judge, and applies the
    acceptance rule with the session's τ; no self-heal rounds. It goes through the raw bound model and the same gate. A
    pass → ACCEPTED with the new machine target (path `DRAFT`); a failure on FLAGGED stays FLAGGED with the new
    findings; a failure on ACCEPTED keeps the previous target and status and reports the new findings. The snapshot is
    kept, so every retry replays what the first draft saw.
  - `skip` — no change; the queue moves on.
  - `acceptProposal` — a backward-revision proposal becomes the user target in both forms; status REVISED.
  No action returns a segment to PENDING.
- **Retry is allowed whenever no run of the project is RUNNING** — paused, stopped, completed, failed, or no run —
  read from `RunRepository.latest(projectId).state`; a RUNNING run refuses it with `busy` and no model call. A paused
  run holds no permit, so the retry never queues behind it; while a retry is in flight the UI disables Resume and the
  other review actions.
- `ReviewDesk` queries: `queue(projectId, ReviewFilter{ALL_FLAGGED, NAMES, OMISSIONS, FOREIGN_KEPT, ALL_SEGMENTS})` in
  document order (records kept as source appear only under `ALL_SEGMENTS`, with path `SOURCE_KEPT`); `segment(projectId,
  segmentId) → SegmentView(segmentId, locator, kind, status, maskedSource, displaySource, maskedMachineTarget,
  userTarget, maskedUserTarget, findings, judgeScore, path, reviewed, context, proposal)`; `counts(projectId) →
  ReviewCounts(total, autoAccepted, repairedAccepted, flagged, reviewed, pending, sourceKept, flaggedWithoutTarget)`. `NAMES` = a `glossary`
  finding; `OMISSIONS` = an `omission` finding; `FOREIGN_KEPT` = a segment marked foreign or holding a kept foreign run.
- The review editor shows `maskedUserTarget` when present, else `maskedMachineTarget`, else the masked source when the
  segment has neither, with its `⟦gN⟧` tokens visible, so an edit that deletes or reorders one is refused on Save with
  the reason. "Editing disables Accept until Save edit or Revert" replaces FR-REVIEW-09's dirty-Accept rule.

### D10 — Consistency stack

- **Shared text rules** (`:pipeline` root package, beside `DisplayText`): `WholeWord` is the one whole-word matcher,
  `(?<![\p{L}\p{N}])…(?![\p{L}\p{N}])` with cached patterns (never `\b`, ASCII-only since JDK 19), taken out of
  `qa.NameRemoval`; matching is case-sensitive everywhere (protected spans, injection, summary, deferrals) except the
  glossary's duplicate checks, which ignore case. `Tokens` is the one `⟦gN⟧` pattern. `prompt.JsonReplies` is the one
  tolerant reply parser. `prompt.DraftStep` lists the three draft-step calls, so a new `PromptName` (pre-scan,
  summary, revision) never edits `SegmentTranslator`.
- **Glossary scan** (`glossary.FrequencyScan`): capitalised tokens and 2–3-word capitalised runs not at sentence start,
  ≥ 3 occurrences, ranked by count then first occurrence, type `other`, gender `unknown`, unlocked, no target. It reads
  the masked text with every `⟦gN⟧` token as a word boundary (`DisplayText` would join `Hale⟦g3⟧Street` into one
  word). A word after `Mr.`, `Mrs.`, `Ms.`, `Dr.`, `St.` or `Prof.` is not sentence-initial (English only; the list is
  a tuning constant). `propose` answers candidate terms with counts; `newTerms` builds the entries with ids from
  `glossary.GlossaryIds.of(projectId, term)` (`projectId:` + the NFC-lower-cased term) after reading the glossary
  once. A script without capital letters (Chinese, Japanese) gets no proposals. It runs
  in `PREP` over the whole book when the glossary is empty, and when Names & style opens on an empty glossary. No scan,
  deterministic or model, ever proposes a term the person removed in this session (D2).
- **Names grown during the run** (FR-ALGO-C9): at the end of each body unit `run.ChunkRunner` re-runs `FrequencyScan`
  over the source of every segment decided so far and hands the candidates the glossary does not hold to
  `run.PendingCommit` for that unit's last commit (`RunRecorder` writes only the run's state) — unlocked, no target,
  type `other`, gender `unknown` — then emits
  `MemoryUpdated(GLOSSARY, "+n")` when any was added. The commit skips a term that exists or was removed (D2). After a
  whole-book `PREP` scan the re-scan finds nothing new by construction; it grows the glossary when the person started
  from their own list (CSV import, pre-scan, entries typed in). The re-scan reads every decided segment each time —
  quadratic over a book, accepted at this size.
- **Model pre-scan** (`glossary.PreScan`), from its button only: candidates are capitalised tokens and runs occurring at
  least once not sentence-initially, each with the first sentence holding it, 40 per call at 0.2; replies read
  tolerantly; types mapped person → `character`, place → `place`, org → `other`, term → `term`, other → `other`;
  genders `female`, `male`, `neuter` as given, anything else `unknown`. Merged only after every batch succeeded,
  deduplicated case-insensitively, an existing entry kept unchanged, a term the person removed in this session never
  added back (the same removal memory as the deterministic scan, D2), new entries unlocked with no target; a failed
  batch returns its `AppError` and writes nothing. A proposal that is not among its batch's candidates is dropped — the
  catalogue's "do not invent", and the pseudo model echoes every capitalised word of the prompt. It never runs at run
  start.
- **Gate result.** `heal.GateFunction` takes the segment's masked input and answers a typed `heal.GateResult` —
  `Restored(maskedForm, restored)`, `GateFailed(QaFinding)` or `StepError(AppError)` — so a caller knows which gate
  failed and gets the D2 masked form; `SegmentTranslator` and the self-heal rounds (`heal.RoundEvaluator`, extracted
  from the 391-line `SegmentHealer`) restore through this one path, and a round whose reply fails a gate fails with
  that finding.
- **Protected spans** (`memory.ProtectedSpans`), masked before drafting and restored before `DocumentPort.unmask`, so
  the document gate never sees them (`ProtectedMask(maskedText, List<ProtectedSpan(token, restored, CheckName)>,
  presentLocked)`):
  - **kept foreign runs** first — under the Keep foreign-passage policy only, a placeholder pair whose element carries
    its own `lang`/`xml:lang` (recorded at masking, D11) that differs from the run's source language after
    `LanguageTags.normalize` (open to any recognized language, D11) collapses, with its inner text, into one atomic
    token and is restored verbatim;
  - **locked terms** next — whole-word, case-sensitive matches of an entry that is locked **and has a non-empty
    target** (locking requires a target: the glossary table, the Add term dialog and CSV import refuse a locked entry
    without one), never inside a longer word or a token (EC-INLINE-3), replaced by a token and restored as the entry's
    target. Overlapping terms match longest first (`Baker Street` before `Baker`), then in document order, never
    overlapping.
  Tokens are numbered from one above the segment's highest token, in document order. One hard gate covers both: each
  protected token exactly once.
- **Injection:** entries whose term occurs whole-word in any segment of the chunk, with target, type and gender; an
  unlocked entry as guidance, an entry without a target by type and gender only, a masked locked term as its token
  with its rendering, type and gender (`⟦g5⟧ → Гейл, character, male`), so the model can make words agree with it.
- **TM** (`memory.TranslationMemory`): key `(sourceHash, contextKey)`, `sourceHash` = `Segment.sourceHash` (NFC,
  unmasked, FR-ALGO-A3) and `contextKey = hash(prevSourceHash ⊕ nextSourceHash)` over the neighbours' source hashes
  read from the unit, `⟦BOS⟧`/`⟦EOS⟧` at a unit's edges (`05_PIPELINE_ENGINE.md#context-aware-tm`). An ACCEPTED
  segment is recorded at its commit with its masked target (a review edit does not update the memory). Exact match →
  hint; context match → reuse (D8's exception); fuzzy → suggestion when similarity ≥ 0.85 over NFC case-folded source
  text, taken from `TmRepository.candidates` in the length band `[ceil(0.85 × len), floor(len / 0.85)]`, never
  duplicating a hint.
- **Rolling summary** (`memory.RollingSummaryKeeper`): deterministic text = the glossary entries whose term occurs in
  the decided source text so far (term → target, type, gender), then the latest heading texts; condensed by dropping
  the oldest headings first, then the entries with the fewest occurrences, until it estimates ≤ 300 tokens. Refreshed
  after every 20 ACCEPTED segments and at every unit end; the counter resets at each refresh, not per unit. On Max the
  unit-end refresh is one `summary` model call and the every-20 refresh stays deterministic; the chapter text it sends
  is capped per side, its middle dropped (Tuning constants). An unreadable summary
  reply — an empty `summary.target` included — keeps the previous version with one WARN; a model-call error routes by
  D3. `RollingSummary` fields: `source` the deterministic text, `target` the model's summary (Max, unit end) — carried unchanged through the deterministic every-20 refreshes that follow, so a later refresh never erases it — or empty when none was written (the record's field is non-null),
  `version` the latest plus one (assigned by the keeper), `lastSummarizedKey` the last decided segment id,
  `tokensSince` the ACCEPTED segments since the last refresh. The keeper's counter and memory live in the job and are
  seeded from the decided records when a job starts, so a resume in the session continues them. The draft prompt
  injects `target` when present, else `source`.
- **Context package** (`context.ContextPackageAssembler`, seam F5): system frame + style sheet at the top, then summary,
  injected terms, TM hits, preceding targets, and the masked source last. The glossary is read once per chunk. Each
  draft records its `ContextSnapshot`. Preceding targets are the **display text of the D2 masked form** — no `*`,
  `**` or tokens — because a model shown a restored `*перший*` imitates the markup (checkpoint `liveLocal` run). The
  assembler takes the summary as a `@Nullable String` and never reads a repository; `DraftPromptBuilder`'s own
  languages, style sheet and policy fold into `prompt.CallFrame`.
- **Self-heal calls get no preceding targets** (a decision at the re-plan): directed fix, reflect, improve and polish
  keep omitting the catalogue's optional preceding-target text — the round's findings and the draft carry what a repair
  needs, and the repair prompts stay short for small models. Revisit if the hand run shows repairs drifting in style.
- **The placeholder repair names the broken rule**: when the document gate refused the restore, the draft step's one
  placeholder repair carries the gate's reason (`GateFailed`'s finding, e.g. an emptied pair) beside the required
  token sequence — at the checkpoint a model repeated `Другий ⟦g0⟧⟦g1⟧ …` because the repair did not say what was
  wrong.
- **Deferrals and backward revision** (`revision`): judge `deferrals[]` (the chunk's and every re-judge's, read
  through `heal.ChunkDecider.deferrals()`) — **recorded only**: nothing in this change resolves them, the report and
  the panel show them; the unknown-gender heuristic (a segment containing a character entry whose gender is
  `unknown`); a TERM deferral when the person changes the target of an entry that is locked after the change and had
  a non-empty previous target, on each decided segment whose effective target holds the previous target whole-word
  (`SegmentRecord` holds no source text). `DeferralReason.NAME_UNRESOLVED` stays in the data model and is never
  produced. The sweep substitutes locked renderings deterministically in segments
  containing a swept term and re-renders gender deferrals through the `revision` template; machine REVISED results
  replace machine targets; user-REVISED segments get a proposal the person applies from the review panel.
- **Sweep limit:** the deterministic sweep replaces a rendering only when the glossary held a previous target for that
  term — recorded as the TERM deferral's `replacedRendering`. A rendering the model chose for a term that had no target
  is not known to the sweep; only the judge or a person catches it.
- **Known limit — a term renamed twice:** a proposal waiting on a person-edited segment is kept and built on by later
  passes, so if the same locked term is renamed again before the person applies it (`Хейл`→`Гейл`→`Гаїл`), the proposal
  keeps `Гейл`: the second rename records no deferral, because the person's text never held `Гейл`. Owner's decision:
  accepted as it is; the person ignores the proposal or edits by hand.
- **A flagged segment keeps its flag:** backward revision replaces the target of a FLAGGED segment but moves it to
  REVISED only when it carries at least one finding and every finding is one the revision fixes (a `glossary` finding, when
  the revision swapped a name); a FLAGGED segment with no finding, or with any other finding, stays FLAGGED with them, so a name swap never hides a real problem.

### D11 — Book inspection and profile (ADR-0037, ADR-0039)

- `inspect.BookInspectorService` is the one public `BookInspector`. Each format package holds its inspector
  (`epub.EpubInspection`, `fb2.Fb2Inspection`, `md.MarkdownInspection`, `txt.TxtInspection`, each implementing
  `inspect.FormatInspection`), so `OpfParser`, `ParsedEpub`, `ParsedFb2`, `Frontmatter` and the rest stay
  package-private; only the inspector classes become public inside the (non-exported) format package.
  `FormatResolver` moves from the module's root package into the non-exported `document.model` package as a public class,
  so the façade can resolve the format; nothing outside `:document` can reach it.
- `inspect(Path) → BookInspection(verdict, format?, formatVersion?, detectedType, encryptionScheme?, LanguageEvidence)`.
  The façade resolves the type (`FormatResolver`, then magic bytes: `%PDF` → PDF, a zip with `[Content_Types].xml` →
  DOCX, `BOOKMOBI` → MOBI, else unknown) and asks the format's inspector. `DRM_PROTECTED` reuses `DrmAdjudicator` and
  names the scheme from `encryption.xml` (for example "Adobe ADEPT"); a `.fb2.zip` whose entry has the zip
  encryption flag set is `DRM_PROTECTED` with the scheme `ZIP encryption`. A refusing verdict is a normal answer;
  opening still fails with `validation`.
- Parser additions: `ManifestItem(id, href, mediaType, properties)`; `ParsedOpf` records the package `version`, the
  `<meta name="cover">` content, the guide references and `spine@toc`; the `.fb2.zip` reader records the entry's
  encryption flag. The nav/NCX parser the EPUB inspector gains (task 4.4) is the one task 6.3's reader and writer
  reuse.
- `LanguageEvidence(declaredRaw?, declared?, contentMajority?, verdict)` with verdict `MATCH | MISMATCH | UNRECOGNIZED |
  ABSENT`; content majority reads `xml:lang`/`lang` on each EPUB content document's root.
- **Declared block language:** each segment records the `xml:lang`/`lang` of its nearest ancestor-or-self element below
  the document root (`Segment.declaredLanguage`, null when none) — a declaration, never a comparison; the pipeline
  compares it with the brief's source language (D8). Each placeholder pair records its own element's declared language
  (`PlaceholderPair(open, close, @Nullable language)`, D10). `TreeNode` gains attribute **read** (task 4.1); attribute
  write comes with the auxiliary units (task 6.1).
- `profile(Document) → BookProfile(title?, author?, CoverImage?, List<StructureNode>, BookStats, resourceIds)`. Title
  and author are read under the existing `MetadataKey.TITLE`/`AUTHOR` (`api.document.MetadataKey`, reused — no new type). Cover: EPUB 3 `properties="cover-image"`, else EPUB 2
  `<meta name="cover">` resolved as a manifest id **or** an href, else the guide's cover document's first image; FB2
  `coverpage/image` binary; Markdown/TXT none.
- **Structure** (every body segment is counted in exactly one node, so node counts add up to the body segment count;
  "chapters" = top-level nodes):
  - EPUB: nav entries (or the NCX when there is no nav) mapped onto spine units; an entry into a unit already counted
    has a null count; a spine unit no entry names is a top-level node at its spine position, titled by its first
    heading segment, else its href's file name.
  - FB2: section titles; content of a body outside any section becomes an untitled leading node; the notes body is a
    top-level node titled by its own `<title>`, else its `name` attribute.
  - Markdown: ATX/setext headings; text before the first heading is an untitled leading node. TXT: one node.
  - An untitled node has the empty title, which the Structure screen shows as the localized "Untitled".
- Statistics: segments and words from the body segments (words by ICU's word `BreakIterator` for the source language,
  counting pieces holding a letter or digit), images, fonts (manifest font media types via `FontMediaTypes`), code
  blocks, verse lines, footnotes, tables and the formatting kinds present.
- `LanguageTags.normalize` (`util.lang`) handles `ua`→`uk`, case, `_`, region stripping, `zh`/`zh-CN`/`zh-SG`→`zh-Hans`,
  `zh-TW`/`zh-HK`/`zh-MO`→`zh-Hant`. **Languages are open** (owner decision, 2026-09-29): a tag outside the catalogue
  normalizes to its lower-cased primary subtag when it is well-formed (`Locale.Builder.setLanguageTag`) and the JDK
  names its language in English (`getDisplayLanguage(Locale.ENGLISH)` differs from the code) — a **recognized**
  language (`la` → `la`, Latin); only a tag the application cannot name (`xx-yy`) normalizes to empty and reads
  `UNRECOGNIZED`. Prompts already name any language through the JDK (`Latin (la)`). A check that needs a script
  (`ScriptCheck`, the dominant-script branch of foreign marking, `TokenEstimator`'s K) uses the catalogue's `Script` and
  falls back — the script check is skipped, K is 3.0 — for an uncatalogued language. `Languages` is the convenience list
  the screens show first; it holds 34 languages: English and the other 23 official EU languages,
  Chinese (Simplified, Traditional), Ukrainian, Russian, Belarusian, Turkish, Japanese, Norwegian Bokmål, Serbian and
  Korean.

### D12 — Auxiliary units (ADR-0041)

- **Identity.** Every opened book has exactly one auxiliary unit, the **last** in `Document.units()` (body positions
  and orders are unchanged): id `aux` (`Unit.AUXILIARY_ID`); `href` = the package document path (EPUB), the FB2 file
  name, the Markdown file name, the TXT file name; media type `application/x-bookloom-auxiliary`; its skeleton handle
  points at the format's auxiliary slot table in the reader's registry. TXT's auxiliary unit is empty.
- **Slots** (segment ids are per slot, never a global counter, so a new slot never renumbers another):

  | Slot | Kind | Segment id | Anchor root | Written as |
  |---|---|---|---|---|
  | EPUB first `dc:title` | `METADATA_TITLE` | `aux:title` | OPF | text |
  | EPUB each `dc:creator` | `METADATA_AUTHOR` | `aux:creator:<i>` | OPF | text |
  | EPUB each `dc:description` | `METADATA_DESCRIPTION` | `aux:description:<i>` | OPF | text |
  | nav document link content (nav not in the spine) | `NAV_LABEL` | `aux:nav:<entry path>` (`1.3.2`) | nav tree | markup |
  | NCX `navLabel/text` whose source differs from every nav label | `NAV_LABEL` | `aux:ncx:<navPoint id>` | NCX tree | text |
  | XHTML `head/title` (non-empty) | `TITLE` | `aux:head-title:<unit href>` | that document's head | text |
  | `img@alt` (non-empty) | `ALT` | `aux:alt:<unit href>:<node path>` | that document's body | attribute |
  | FB2 `title-info/book-title` | `METADATA_TITLE` | `aux:title` | FB2 description | text |
  | FB2 each `title-info/author` | `METADATA_AUTHOR` | `aux:creator:<i>` | FB2 description | name parts |
  | FB2 `title-info/annotation` paragraphs | `METADATA_DESCRIPTION` | `aux:description:<i>` | FB2 description | markup |
  | Markdown frontmatter text value | `FRONTMATTER_VALUE` | `aux:fm:<key>` | frontmatter byte spans | YAML scalar |
  | Markdown image alt | `ALT` | `aux:alt:<file name>:img<k>` | body | alt span |

  - An NCX label whose source text equals a nav label's is not a separate segment; it is written from that nav
    segment's target, so a reviewer's edit reaches both. A nav document that is also in the spine yields no
    `NAV_LABEL` (its links are body segments). Page titles follow the navigation switch.
  - FB2 author: one segment per author built from `first-name`, `middle-name`, `last-name` and `nickname` only (never
    `id`, `email`, `home-page`): source `<first-name>Leo</first-name> <last-name>Tolstoy</last-name>`, masked as one
    pair per part (`⟦g0⟧Leo⟦g1⟧ ⟦g2⟧Tolstoy⟦g3⟧`); the writer puts each pair's restored inner text into the part it
    came from. `src-title-info` and `history` are untouched.
  - `SegmentKind.METADATA_DESCRIPTION` is added in task 1.2 with the other `:api` records; its class Javadoc loses the
    "does not grow" note, and DD-47 is amended to list descriptions under the metadata switch.
- **Frontmatter** (owner decision): a value is a segment only when all hold — the key is not `lang` or `language`; it
  is a single-line scalar on a top-level `key: value` line (not indented, not a list item, not a flow collection
  `[`/`{`, not a block scalar `|`/`>`, not an anchor, alias or tag `&`/`*`/`!`); unquoted, it is not YAML-typed (null
  `~`/`null`/empty, boolean `true/false/yes/no/on/off/y/n` in any case, a number including hex, octal, `.inf`, `.nan`,
  an ISO date or date-time); it is not a URL or e-mail (`scheme://`, `www.`, `mailto:`, `…@….…`); after removing
  surrounding quotes and a trailing `# comment` (unquoted values only) it holds a letter. A quoted value is text when it
  holds a letter and is not a URL. `Frontmatter` records each value's byte span. Write-back keeps the original quotes
  (`"` escaped as `\"` inside double quotes, `'` as `''` inside single quotes); an unquoted target is wrapped in double
  quotes when it contains `: ` or ` #`, starts with a YAML indicator (`- ? : , [ ] { } # & * ! | > ' " % @` or a
  backtick), or would itself read as a typed value (`1984`, `yes`, `null`). On export an existing top-level `lang`
  value is replaced by the target tag in its original quote style; a `lang` key is never added; `language` is left as
  it is.
- **Restore by kind.** `ALT` and `FRONTMATTER_VALUE` targets are restored to plain text: no markup escaping, no
  Markdown escaping, no Markdown structure check. Every other auxiliary kind is restored as body text is; a text slot
  takes the restored markup's character data through the tree's text setter (which escapes on output), a markup slot is
  re-parsed into its element as body runs are.
- **ALT write-back** (the enclosing run is the body segment whose markup holds the image):
  - the enclosing run has a target → the writer finds the image's exact source fragment inside that run's restored
    `targetInner` (matched by the image's full source markup; when the same markup occurs twice in one run, the k-th
    occurrence matches the k-th image) and substitutes the same fragment with the translated alt, attribute-encoded;
    the attribute is not also written in the tree, which the run's re-parse would overwrite;
  - the run has no target (pending, or flagged without a machine target) or the image is in no run → the writer sets
    the attribute on the tree as plain text; jsoup escapes it;
  - Markdown does the same on the image's alt span, escaping `\`, `[` and `]`.
- **Writers.** All four readers and writers learn the auxiliary unit in one task (6.2): a writer resolves each
  auxiliary segment through the slot table and skips a slot with no target; `Fb2Writer.bodyFor`, `EpubWriter.treeFor`
  and the TXT writer skip the auxiliary unit. The nav document and the NCX are carved out of "out-of-spine resources
  are verbatim" and re-serialized by D14's rules.
- **Interim rule, until the run applies the "Also translate" switches:** the job's pending work list and section
  count, and the `:ui` `StructureListing:32` and `ImportViewModel:243–252` counts, skip the auxiliary unit. It is never
  removed from the `Document`, because the exporter compares full segment counts. The contract migration carries the
  skip into the repository reads (`run.WorkList`); the switches then replace it with "kept as source by choice" (D2).

### D13 — Export job (ADR-0035)

- `ExportService.newExport(ExportRequest(projectId, destination, overwrite, Set<SideFile>, consistencyPass),
  @Nullable ChatModel)`; the model is needed only when the consistency pass re-renders gender deferrals. The proposed
  destination name comes from `util.paths.DestinationPath`, the command line's rule.
- Steps: optional consistency pass (D10) → fresh open → apply effective targets (PENDING records and records kept as
  source keep their source; FLAGGED writes its machine target, or its source when it has none) → write to a temporary
  file → re-open → verify the **body** segments only: their count, then per segment the placeholder tokens **in count
  and order** and each token's fragment compared after removing an image's alt value (XML/XHTML: the `alt` attribute's
  value; Markdown: the text between `![` and `](`), so a translated alt never fails it, even inside a PENDING paragraph
  written as source (next_features §14) → atomic move → side files → `ExportReport`.
- The auxiliary unit is not re-verified: its write-back is covered by the per-format auxiliary goldens (D16), and an
  auxiliary slot can legitimately disappear on re-open — an NCX label translated to the same text as its nav label
  merges with it; a frontmatter value written as `"1984"` holds no letter. `verifiedSegments` counts body segments.
- `ExportReport(destination, written, pending, sourceKept, flaggedWritten, autoAccepted, reviewed, sideFiles,
  verifiedSegments)`: `written` segments carry a target, `pending` are the segments of translated kinds written as source
  because they have no target — PENDING records and FLAGGED records with no machine target (a partial export) —,
  `flaggedWritten` the FLAGGED records written with their machine target, `sourceKept` the records kept as source by
  choice. `ReviewCounts.pending` stays PENDING records only: the review panel counts work left, the report counts what
  the file holds.
- The run's source language reaches the writer through an additive overload `DocumentPort.write(document, destination,
  @Nullable sourceLanguage, targetLanguage)`; the three-argument method delegates with null, meaning the package's
  declared language (D14 §1).
- One success surface: the export-complete dialog; no toast.
- Side files beside the book: `<name>.glossary.csv` (RFC-4180, columns term, target, type, gender, locked — the import
  format), `<name>.bilingual.html` (one table row per segment, source | target, self-contained CSS), `<name>.report.md`
  (counts, flagged list by locator with findings, consistency notes).
- The writer moves first, unchanged, from `TranslationEngineImpl`/`BookExporter` into `pipeline.export` behind a minimal
  `ExportServiceImpl` over the records, so the job migration never carries the old export stage; the finished
  `ExportJobImpl` then adds the refusals, side files and per-segment verification (`export.SegmentVerification`,
  extracted so `BookExporter`, 370 lines, stays under the limit). The autoAccepted, reviewed and pending counts come
  from one static `review.ReviewCounting` that `ReviewDeskImpl.counts` also uses — one rule, two callers.
- Command line: destination preflight (exists without `--overwrite` → the current refusal), translate job, export job,
  one report line; exit codes unchanged. A shutdown hook cancels the running job and waits up to 5 s so the export
  deletes its temporary file (§13). `guice_bytecode_gen_option=DISABLED` is set for `:app:run` and the jpackage
  launcher too (§13).

### D14 — `:document` fidelity fixes (next_features §1–§7, §9, §10)

- §1 `EpubWriter` rewrites `dcterms:language`, `<package xml:lang>`, and `xml:lang`/`lang` on each XHTML `html` and
  `body` (the nav document included) whose value equals the passed source language after normalization, or the
  package's declared language when none is passed; an appended `dc:language` gets its prefix; a nested `dc-metadata`
  is reached. Limit: a `<div lang>` or any deeper element keeps its value.
- §2 the OPF outputter uses LF and writes the declaration and encoding the source had (recorded in `ParsedOpf`). The
  re-serialized nav document and NCX follow the same line-end and declaration rule.
- §3 attribute values keep `\n`, `\r`, `\t` as character references by a serialization post-pass, because jsoup 1.23
  parses `&#10;` to a line feed and writes it raw: on the serialization copy only, replace them inside attribute values
  with U+E00A, U+E00D, U+E009; serialize; replace each sentinel in the output with `&#10;`, `&#13;`, `&#9;`. A document
  that already contains one of those code points skips the post-pass, with one DEBUG line. The canonical comparator
  normalizes attribute values.
- §4 the XHTML prolog (declaration and DOCTYPE) is kept verbatim and written ahead of the serialized document; the nav
  document follows it.
- §5 `ParsedFb2` records a byte-order mark and `Fb2Writer` re-emits it.
- §6 (ADR-0029, owner decision) a TXT or Markdown export first encodes in the charset resolved at import with a
  reporting encoder; if any character is unrepresentable, the **whole** document is written as UTF-8, a byte-order mark
  is written exactly when the source had one, and the registry's recorded charset becomes UTF-8; one INFO line names the
  document and both charsets. Markdown never writes `?` again. EPUB keeps jsoup's lossless numeric references; FB2
  keeps its existing switch. The no-edit golden is unchanged; an encoding-switched fixture is asserted against a
  re-parsed canonical form (the EC-FB2-1 carve-out).
- §7 a test pins jsoup writing a numeric reference; ADR-0029 and the backlog's D1 text are corrected.
- §9 pairs (ADR-0040) and two more rules checked by the placeholder gate, each failing as `validation`: a pair whose
  source content holds text (a non-whitespace character outside tokens) must hold text in the target; a line-break
  token's innermost enclosing pair must be the same in source and target. Masking records the pairs, each pair's
  declared language and the line-break tokens on the segment.
- §10 `commonmark-ext-autolink` makes a bare URL an atomic masked token.
- D18: every reader/writer method this change touches gains DEBUG parameter lines and TRACE text lines.

### D15 — UI

- **Current project.** `ui.state.CurrentProject` (`@Singleton`) holds a read-only property of `OpenedBook(projectId,
  source, BookInspection, @Nullable BookProfile, BookBrief)` — the source path feeds the export proposal (D13), the brief
  is the one `ImportedBook` carries. `ImportViewModel` sets it once `ProjectService.importBook` succeeds and clears it on Cancel and on closing the book (`ProjectService.close`); Brief, Structure, Names & style,
  Translating and Export read it and show their no-book state when it is empty (Names & style included).
- **One translation per app run** (owner decision, 2026-09-29). Choosing a book to import while the current run is
  RUNNING, PAUSED (a provider error included) or STOPPED opens `ui.dialog.ReplaceRunDialog` (the `ModalHost` card
  pattern of `AboutDialog`): "Discard and import" cancels a running job and waits for it off the FX thread, closes the
  project and imports; "Keep translation" leaves everything as it was. After COMPLETED or FAILED the import runs without
  asking and the run state returns to idle. `ui.state.ImportGuard` decides; the dialog package is created here and
  reused by the later dialogs. Switching projects will reuse the guard when Projects exist.
- `SearchableCombo<T>`: an editable `ComboBox` over a `FilteredList` filtered by the editor text (case- and
  accent-insensitive, matches anywhere in the display name), in three modes: STRICT keeps the previous value when the
  text matches nothing; FREE_TEXT keeps what was typed (Genre); PARSED hands unmatched text to a parser
  (`Function<String, Optional<T>>`) — the language lists, where `ui.state.LanguageNames.parse` accepts a catalogue name,
  the ICU display name of any recognized language in the interface language or English, or a recognized tag
  (`Latin`, `латинська`, `la` → `la`); anything else keeps the previous value and marks the field.
  ControlsFX `SearchableComboBox` is not used — it cannot take free text and is unproven in the packaged image.
- **Genres:** `ui.state.Genre`, 40 constants carrying the English name sent to the prompt and a message key shown in
  both catalogues: Literary fiction, Classic literature, Historical fiction, Gothic novel, Romance, Historical romance,
  Mystery, Detective fiction, Crime fiction, Thriller, Psychological thriller, Horror, Science fiction, Fantasy, Epic
  fantasy, Dystopian fiction, Adventure, Western, War fiction, Humor, Satire, Young adult, Children's literature, Fairy
  tale, Short stories, Poetry, Drama, Memoir, Biography, Autobiography, Essay, Travel writing, History, Philosophy,
  Religion and spirituality, Popular science, Self-help, Business, True crime, Graphic novel. Free text goes to the
  prompt as typed.
- **Title bar.** `RunStatusBar` sits in `AppShellView`'s title bar between the product name and the theme toggle,
  hidden (not merely empty) with no run; otherwise file name · state text (`Progress 78%`, `Paused at 78%`, `Stopped at
  78%`, `Provider error`, `Finished`, `Failed at 78%`) · elapsed / time left · Pause while running, Resume while paused,
  stopped or in a provider error, nothing when finished or failed.
- **Start and resume.** *Start* creates a new job at the first pending segment and is offered only by Names & style's
  "Start translation" and the Translating screen's own start control, and only when the project has no run, its last
  run COMPLETED with pending segments left (an "Also translate" switch turned on afterwards), or its last run FAILED.
  *Resume* continues: from Paused or Provider error it resumes the same job; from Stopped it creates a new job at the
  first pending segment. While a run is RUNNING, PAUSED, STOPPED or in the provider-error state, Names & style's "Start
  translation" starts nothing and navigates to Translating, its label unchanged, and Translating offers Resume, never
  Start; a COMPLETED run with nothing pending offers no start. `ui.state.Controls.of(state, preparing, pendingRemain)`
  holds this table. The title bar's control does exactly what the screen's control of the same name does.
- **Translating states:**

  | State | Shows | Offers |
  |---|---|---|
  | idle (no run yet for this project) | the ready card: book, model, mode, dial, pending count | the screen's Start (a missing model or brief field is named in place) |
  | running | progress line, four tiles, live panel, log | Pause, Stop, Review flagged (n) |
  | paused | the paused banner | Resume, Stop, Review flagged (n) |
  | stopped | the stopped banner | Resume, Review flagged (n) |
  | provider error | auto-paused, naming the code | Retry now (= resume), Open provider settings, Stay paused |
  | completed | the outcome: accepted, repaired, flagged, kept as source | Continue to Export, Review flagged (n); the screen's Start only while pending segments remain |
  | failed | the blocking error dialog, then the outcome so far | the screen's Start (a new start at the first pending segment) |

- **Texts replacing the mockup's persistence promises** (English; Ukrainian in the catalogue):
  - subtitle: `A stopped run resumes where it left off — until the application closes.`
  - paused banner: `Paused. Progress is kept until the application closes. Resume any time — it continues at chunk
    41/66.`
  - stopped banner: `Run stopped. Progress is kept until the application closes. Resume any time — it re-enters at the
    first pending segment; flagged segments wait in the review panel.`
  - start toast: `Translation started` / `Keep the application open — progress is kept only until it closes.`
  - failed dialog: `Decided segments are kept until the application closes.` in place of "Your progress is saved —
    nothing was lost."
- **Sidebar footer:** `Ollama · gemma4:26b` — the chosen provider's display name and model id — from the current
  selection.
- **Deviations from the mockup**, each stated in `app-shell` "Match the reference rendering": the separate Review step
  and its navigation entry (review is a panel inside Translating — owner decision); the toolbar's Pause and `Review (3)`
  (run control is in the title bar, review on the screen); the title bar's always-shown Resume (the status bar appears
  only with a run); "Back to projects" (Projects is not built); the sidebar footer's readiness dot and the brief's
  `ready` model badge (they need verification state the application does not keep); the import card's `valid` badge
  (the book is checked by opening it, nothing validates it); the import "Source language override" dropdown (the source
  language is chosen on the Book Brief, ADR-0037); the `primes system prompt` and `Slower · Max-quality` notes (notes
  for the mockup's reader; the consistency pass runs on any dial from Export); per-node formatting pills on the
  structure tree (formatting is reported for the book in the statistics card); toasts no requirement names; and the
  preview-state switchers, the design-reference group and "EPUBCheck passed" already listed there.
- **State mirror sections.** `StateMirror` stays the one `@Singleton` but grows by section objects — `live()` (live
  rows, throughput, kept-as-source count, flagged queue) and `review()` (provider error, review-pause segment, retry in
  flight) — sharing its `Platform.runLater` publish seam, each field added by the task that produces it. `RunSession`
  keeps the dispatch; `LiveChunkState`, `ThroughputMeter`, `RunClock` and `ActivityLogFeed` hold the logic.
  `TranslatingViewModel` hands starting and refusals to `RunStarter` (with one `RunContext(projectId, dial, fileName,
  selection)`), `ImportViewModel` its verdict mapping to a pure `ImportStates`, and the brief's destination members move
  to a new `ExportViewModel` that the Export screen builds on. Shared controls `StepFooter`, `Banner` and `StatTile`
  replace the hand-built footers, banners and tiles.
- **Interim export.** Until the Export view is rebuilt, a completed window run still writes the book through
  `ExportService` (the bridge the job migration adds); the Export view's rebuild deletes it, and from then on only the
  Export screen writes the book.
- Navigation: `NAMES_STYLE` gets a screen; `REVIEW` is removed from `ViewNames` and from
  `07_UI_ARCHITECTURE_JAVAFX.md#navigation` (a task-group-0 edit); completed steps carry a done mark; six numbered
  workflow steps; group headings uppercase and letter-spaced.
- Screens follow the mockup (the capability specs enumerate the parts); the Brief's segmented controls wrap instead of
  truncating Ukrainian labels (§15); Translating hosts a two-pane layout (live panel | log) above an expandable review
  panel (flagged list with filter chips | compare panes and actions).
- Settings: the model chooser stays on the Providers card; the Appearance tab stays available; the other tabs stay
  unavailable.
- Windows reveal: an Explorer exit code 1 counts as success (§15).
- Names & style's banner reads "Skip this and the app builds names on the fly as it translates." — true once names grow
  during the run (D10); proposals start unlocked.

### D16 — Testing

| Tier | What it proves | Where |
|---|---|---|
| Unit | estimator, packer, splitter (both token-adjacent boundary cases), QA checks with margins and the echo floor, acceptance rule, dial, style sheet, protected spans, TM keys from neighbour hashes, summary condensation and triggers, tag normalization | `:util`, `:document`, `:pipeline` |
| Repository contract + concurrency | every port; a commit is never half-visible; `RunRecord.state` transitions; a commit skips an existing or removed glossary term | `:persistence` |
| Provider seam (WireMock, both dialects) | usage mapping, `num_ctx` only on Ollama, verifier measurements, the timeout floor rule (`--timeout 30` included), a downgrade keeping `contextWindow`/`expectedOutputTokens` | `:llm` |
| Golden round trip per format | zero-edit canonical equality with auxiliary units; edited auxiliary slots change only their slots; aux ids stable when a slot is added; §1–§6 fixes; an encoding-switched TXT/Markdown fixture | `:document` |
| Round-trip check | a book whose re-open loses a segment id reports `idsPreserved=false` with the missing id; a structure change reports `structurePreserved=false` | `:pipeline` |
| Prompt golden | the migrated draft template reproduces today's messages | `:pipeline` |
| Pipeline e2e (WireMock) | prep → translate → judge → directed fix → flag → Assisted pause → review edit → resume → export with side files; a provider 503 auto-pauses and resumes | `:pipeline` |
| Chunk lifecycle | a Balanced mid-chunk review pause: prefix committed, later drafts kept, an edit feeding the next chunk; a judge call interrupted by a pause redone; a stop dropping undecided drafts | `:pipeline` |
| Retry vs run state | retry refused `busy` while RUNNING; allowed while PAUSED and after a stop | `:pipeline` |
| UI widget / screen-state / conformance | every enumerated state of Import, Brief, Structure, Names & style, Translating (the seven states, review included), Export, Providers; title bar; 944×600 | `:ui` (TestFX headless) |
| i18n | EN/UK parity, ICU validity, UK plurals for every new counted message | `:ui` |
| App e2e | `TranslationWorkspaceEndToEndTest` per review mode against the pseudo model | `:app` |
| liveLocal | usage present from a real Ollama and LM Studio | `:llm` |
| Packaged image | every new `opens`/`exports` resolves | `scripts/` |

Tests are written first and seen red (`AGENTS.md#definition-of-done`); no Mockito; WireMock only at the HTTP seam. A
fixture that asserts acceptance passes every soft check at full margin under the echo floor: for en→uk a Cyrillic reply
with a length ratio within 0.81–1.69 — for a 33-character source, `Маяк стояв на прибережній скелі.` (32 characters,
ratio 0.97), not `Маяк стояв на скелі.` (20 characters, ratio 0.61, below the band).

## Tuning constants

Every number this change fixes. "Reference" values come from the cited clause; "chosen here" values are this change's
starting points, each in one place.

| Constant | Value | Source | Lives in |
|---|---|---|---|
| τ per review mode | Unattended 0.60, Assisted 0.75, Manual 0.85 | chosen here (ADR-0036; the reference gives none) | `api.pipeline.ReviewMode` |
| τ_judge | = τ | reference `05_PIPELINE_ENGINE.md#quality-dial` | `heal.AcceptanceRule` |
| Ollama `num_ctx` / effective context | 8192 | chosen here (the reference default 32768 in `07_SETTINGS.md#generation-tab` is edited in task group 0) | `chunk.TokenBudget` |
| chunk budget | `min(8192 − headroom, 1200)` | reference formula `05_TRANSLATION_ALGORITHM.md#token-budget`; cap 1200 from `07_SETTINGS.md#generation-tab` | `chunk.TokenBudget` |
| segments per chunk | Fast 8, Balanced 4, Max 2; Manual review 1 | chosen here (ADR-0038) | `dial.DialParameters`, `chunk.ChunkPacker` |
| repair rounds N | 1 / 2 / 3 | reference `05_PIPELINE_ENGINE.md#quality-dial` | `dial.DialParameters` |
| preceding targets | 1 / 2 / 3 | reference `05_PIPELINE_ENGINE.md#quality-dial` | `dial.DialParameters` |
| judge / revision / LLM summary by dial | off-on-on / off-off-on / off-off-on | reference `05_PIPELINE_ENGINE.md#quality-dial`; LLM summary on Max only chosen here | `dial.DialParameters` |
| confidence weights | glossary 0.30, length 0.25, script 0.20, echo 0.15, repetition 0.10 | reference `05_PIPELINE_ENGINE.md#confidence` | `qa.CheckName` |
| confidence comparison | summed in the order glossary, length, script, echo, repetition; accepted when `confidence ≥ τ − 1e-9` | chosen here (a floating-point sum of exactly τ must meet it) | `qa.Confidence` (sum), `heal.AcceptanceRule` (comparison) |
| echo fail | similarity ≥ 0.90; margin window 0.10 | reference `05_PIPELINE_ENGINE.md#qa-thresholds`; window chosen here | `qa.EchoCheck` |
| echo floor | 20 code points of source display text | chosen here (owner-approved default) | `qa.EchoCheck` |
| script check floor | source ≥ 20 characters | reference `05_PIPELINE_ENGINE.md#qa-thresholds` | `qa.ScriptCheck` |
| script share | fail < 0.60; margin window 0.20 | chosen here (0.60 re-purposed from the reference's detector confidence) | `qa.ScriptCheck` |
| repetition | m = 3, k = 3; margin 0.5 at a run of 2 | reference `05_PIPELINE_ENGINE.md#qa-thresholds`; 0.5 chosen here | `qa.RepetitionCheck` |
| length bands | per pair class; short < 25 chars widened ×0.5 / ×2; margin window 0.10 × band width | reference `05_PIPELINE_ENGINE.md#qa-thresholds`; window chosen here | `qa.LengthBand` (read by the length check and the output allowance) |
| fuzzy TM suggestion | similarity ≥ 0.85 | chosen here | `memory.TranslationMemory` |
| summary refresh | every 20 ACCEPTED segments and at unit end | reference `05_PIPELINE_ENGINE.md#rolling-summary` (K = 20) | `memory.RollingSummaryKeeper` |
| summary size | ≤ 300 estimated tokens | chosen here | `memory.RollingSummaryKeeper` |
| summary call's chapter text | each side — the unit's accepted source, its accepted targets — ≤ 2048 estimated tokens (a quarter of the 8192 context, so both take half and the rest is left for the instructions, the previous summary and the reply); a longer side keeps its beginning and end around a `[…]` line | chosen here | `memory.ChapterText` |
| deterministic scan | ≥ 3 occurrences | chosen here | `glossary.FrequencyScan` |
| pre-scan batch | 40 candidates per call | chosen here | `glossary.PreScan` |
| temperature: draft | 0.2 | reference `12_PROMPT_CATALOG.md#draft-translation` | `prompt.PromptName` |
| temperature: judge | 0.1 | chosen within the reference's 0.0–0.2 | `prompt.PromptName` |
| temperature: directed fix | 0.2 (no caller asks for a lower one: a review retry runs no self-heal rounds, D9) | reference ~0.2 | `prompt.PromptName` |
| temperature: reflect, improve (removed in 15d.7) | 0.35 | reference ≤ ~0.4 (`05_PIPELINE_ENGINE.md#generation-parameters`); the catalogue's reflect "~0.2" (`12_PROMPT_CATALOG.md#reflect-critique`) is edited in task group 0 | `prompt.PromptName` |
| temperature: pre-scan, summary, revision (polish removed in 15d.7) | 0.2 | reference ~0.2 in `12_PROMPT_CATALOG.md` | `prompt.PromptName` |
| temperature: retried draft | 0.2, or 0.1 when asked | chosen here | `prompt.PromptName` |
| chat timeout | `max(configured, min(600 s, expectedOutputTokens × 0.5 s))`; configured default 3 min | chosen here; default from `ProviderConfig.DEFAULT_REQUEST_TIMEOUT` | `:llm` request timeout rule |
| output allowance | `ceil(chars(source) × band hi / K(target) × 1.15)` | chosen here | `chunk.TokenEstimator` |
| output cap | `max(64, ⌈1.5 × allowance⌉ + 16 + 6 × placeholder tokens)`, never below 128 for a translation call (group 15b step 3: a one-word source under 64 came back empty); none where no expected output is stated | chosen here (owner decision, 2026-09-29) | `chunk.TokenEstimator`, `prompt.OutputLimit` |
| sentence-start exceptions of the name scan | `Mr.`, `Mrs.`, `Ms.`, `Dr.`, `St.`, `Prof.` | chosen here | `glossary.FrequencyScan` |
| time left | average of the last 20 timed segments; shown after 5 decisions | chosen here | `ui.state.RunClock` |
| tokens per second | last 20 `DRAFT` calls | chosen here | `ui.state.ThroughputMeter` |
| token estimator | K per script (Latin 4.0, Cyrillic 3.0, Greek 3.5, Han/Japanese/Hangul 1.5, unknown 3.0), × 1.15 | reference `05_TRANSLATION_ALGORITHM.md#token-budget` | `util.lang.Script`, `chunk.TokenEstimator` |
| live tick | 100 ms | existing | `ui.state.RunSession` |
| command-line shutdown wait | 5 s | chosen here | `app.cli` |

## Risks / Trade-offs

- [Scale — ~110 tasks in one change] → groups are ordered by dependency and each merges separately into the feature
  branch; `./gradlew build :app:archTest` runs after every group. Groups 9–16 were re-planned against the code groups
  0–8 built; each task names the refactoring it needs first.
- [Balanced adds judge and repair calls, so runs get slower on small models] → the judge is per chunk (ADR-0038);
  Fast turns it off; the time-left figure makes the cost visible.
- [A failed soft check now blocks acceptance, so more segments are flagged] → the echo floor keeps short lines
  (names, numerals, "OK") out; names kept by policy are removed before script and echo; confidence still decides only
  close calls.
- [τ values are guesses] → they live on `ReviewMode` only; the hand run records flag rates for tuning.
- [`num_ctx` 8192 raises Ollama's memory use and may reload the model] → a constant in one place, logged at INFO when a
  run starts; the configured timeout stays the floor of every call; OpenAI-compatible servers are unaffected.
- [Locked terms cannot inflect] → proposals start unlocked; injected entries carry gender for agreement.
- [Script check misses same-script wrong-language output, and Latin-script Serbian fails it] → stated in the
  specification (ADR-0037); the judge's `language` finding covers part of it.
- [Alt-text translation widens the "only text nodes change" invariant] → closed list of slots and a second golden per
  format (ADR-0041).
- [Only `html`/`body` language attributes are rewritten] → a chapter-wide `<div lang>` keeps the source language;
  stated as a limit.
- [A UTF-8 fallback export declares an encoding the source did not] → accepted by ADR-0029; one INFO line names it.
- [Events now carry book text] → only in memory; a test asserts no DEBUG-or-higher log line contains segment text.
- [Retry while a run is active could queue behind the gate] → refused with `busy` while the run record is RUNNING (D9).
- [Repeated exports mutate the registry-held tree] → export and the round-trip check always open the source fresh (D2).
- [The output cap formula is a guess; judge, reflect and summary stay uncapped] → one constant; the hand run records
  how often replies are cut at the cap.
- [Languages outside the catalogue get weaker checks — no script check, the default K 3.0 and the widest length band;
  Latin-script Serbian still reads as Cyrillic] → stated as limits; recognition relies on the JDK's CLDR names.
- [Discarding a running run on import may take seconds] → the dialog shows it is stopping until the job ends.
- [Translating screen at 944×600] → the live/log row and the review panel scroll inside their cards; the minimum-size
  test covers Translating with the review panel open.

## Migration Plan

Nothing is persisted, so there is no data migration. Inside the repository: `TranslationRequest` (28 files — 11 main,
17 test — across `:api`, `:pipeline`, `:ui`, `:app`) moves to `RunRequest` + `ExportRequest` in the contract migration, after the
writer has moved unchanged into `pipeline.export`; the existing pipeline tests are rewritten against the new job before
the old exporter path is deleted, and the tests pinned to log strings or internal classes (`DiagnosticsSegmentTranslatorTest`,
`JobProgressTrackerTest`) are rewritten rather than migrated;
the command line's observable behaviour is pinned by its existing tests, re-baselined only where the stricter
acceptance rule changes the pseudo model's counts. Rollback is reverting the feature branch.
