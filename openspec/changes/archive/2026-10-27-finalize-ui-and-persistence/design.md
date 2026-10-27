# Design: finalize-ui-and-persistence

## Context

See `proposal.md` for the motivation. The visual and interaction target is `docs/specification/mockups/README.md`. Its
"Code" column names the existing classes each element builds on, and its "For AI agents" section is the method for
building and checking every screen.

### What exists today

Paths are relative to `modules/<module>/src/main/java/ua/bookloom/<module>/`.

**`:ui`**
- **Dialogs and work.** `ModalHost` (scrim, focus trap, Esc, Tab wrap, one card), `dialog/ModalCard` (a `DialogPane`
  with no size variants) and `BusyHost` (a small busy card plus `#busy-show-calls`, which opens
  `control/ModelCallsWindow`, the only `new Stage` in `:ui`).
- **Shell controls.** `ActivityChip`, `FigureLabel`, `StepFooter.claim()` (mounts into `#shell-actions`), `ToastStack`
  (anchored bottom-centre) with `ToastAction`, `TwoColumns`, `PriorityRow`, `TaggedLog`, `StructureSegmentsPane`
  (virtualised), `GlossaryCells`, `SmoothScroll`, `Motion`.
- **Run state.** `RunState` (8 values), `RunNotice`, `RunInterventions`, `PauseNotice`, `ActivityTracker.Handle`,
  `CallFeed`, `ThroughputMeter`, `RunClock` (the time-left window, `WINDOW = 20`), `RunFigures` (a segment percentage).
- **Translating.** `LiveCallPanel` / `LiveCallView` (`SEGMENTS_HEIGHT = 220`) and `LiveCallState` (current and
  previous call only).
- **Settings.** `settings.fxml` has four tabs disabled; provider add, edit and delete are disabled.
- **Layout.** No `SplitPane`, no `TextFormatter`. `AppShellView.showContent` forces `minHeight = USE_PREF_SIZE` on
  every screen that is not itself a `ScrollPane` (`AppShellView.java:332`). `theme.css` has one base scrollbar rule and
  seven regional copies.

**`:api` / `:persistence`**
- Nine storage ports, each bound to an in-memory adapter over `InMemoryStore` (ADR-0034), proven by the
  `RepositoryContractTest` chain. The `LexiconRepository` is not in that chain yet.
- `Project(id, source, format, contentHash, brief)`. The id is the sha256 of the book's path
  (`ProjectServiceImpl:355`).
- `ProviderConfigs` and `ProviderConfig` live in `:api`, but `InMemoryProviderConfigs` (the two presets) is bound in
  `:llm`'s `LlmModule:38`.
- Settings ports do not exist. The theme is held in memory, the language comes from the OS, the review mode is a
  launch flag, and reasoning and sampling are environment variables.

**`:llm` / `:pipeline`**
- **One header point.** `HttpExchange.buildRequest` is the single place request headers are set.
- **Refused settings.** `CapabilityRejections` recognises a refused reasoning or structured-output setting on a 400
  only. `GatedChatModel`'s downgrade set lives for one call.
- **Batch replies.** `BatchReplyParser` parses a batch reply into items, but the items never reach `CallSnapshot`.
- **Context snapshots.** A batch-adopted segment stores its own `ContextSnapshot`, not the batch sections that were
  sent.
- **Step names.** `NamesPassImpl` names its stages only in a DEBUG line. `BriefSuggester`'s phases are visible only as
  call kinds. Export has `ExportProgress.Step`.
- **Requests.** `ChatRequests.build(PromptName, messages, OutputLimit, lowerTemperature)` is static, has 16 callers, and
  reads `SamplingMode.current()` (`BOOKLOOM_SAMPLING`) and `ReasoningPlan.current()` (`BOOKLOOM_REASONING`) itself
  (`ChatRequests.java:41,43`). The per-kind temperature is `PromptName.temperature`. Every run request passes
  `JobModelCalls.send` (`:157`), which sizes it to the window. `ContextBudget.windowFor(detected, override)` already
  takes an override, always `null` today (`TranslationJobImpl:292`, `BookSamples:78`, `NamesPassImpl:134`).
- **Checks.** `DialParameters.reviewPasses` counts reviewer calls per **chunk** (Fast 0, Balanced 1, Max 2; chunk cap
  8, 8, 2; Manual caps chunks at 1). Verbatim segments get no call.
- **Failures.** A throwing step becomes `ErrorCode.internal` (`RoutedCalls:296`). `PauseDecider` pauses on internal (3
  pauses, resumes by itself), timeout (2), outages (10) and validation (2, waits for the person); a segment's step that
  reaches its budget is flagged and the run goes on. The command line always runs Unattended and ignores the
  review-mode flag (`TranslateCommand:170`).
- **Review mode.** `ReviewModeResolver` (`BOOKLOOM_REVIEW_MODE` over `bookloom.review.mode`, default Unattended) runs in
  `Launcher:109` before the injector; `AppModule:54` binds the one `ReviewMode`, which `RunStarter:48`,
  `ReviewViewModel:88`, `ReviewPauseFollower:47`, `ReviewAvailability:24` and `TranslatingViewModel:200` inject.
- **Wiring.** `CoreModules` is shared by the window and the command line (`TranslateLauncher:109`). About nine `:llm`
  and `:pipeline` test injectors are built from `LlmModule` alone and rely on its `ProviderConfigs` binding.
  `ProviderConfigs` has `register`, `find` and `all`. The detailed log's switch is read by `TraceFileResolver`.
- **Titles.** `aux:fm:title` / `aux:fm:author` exist for Markdown only (`MarkdownAuxiliary.java:99`); TXT has no
  front matter.

### Constraints

- **Layering.** `:ui` sees `:pipeline`, `:api` and `:util`. Only `:llm` uses HTTP. Only `:persistence` uses SQL or
  JDBI. `:api` is framework-free (no Jackson).
- **Coding rules.** Records for data; `Result` / `AppError` at every port; single-flight inference; credentials as
  references; token-only theming; logging at every level; book-neutral fixtures; no mock of the boundary under test.

## Goals / Non-Goals

**Goals:**
- The window matches the mockup at 960×640 through 1920×1080, in light and dark, in `en` and `uk`.
- State and settings survive a restart, and a run continues after one.
- The smallest pipeline and provider changes that give the UI its data.

**Non-Goals:**
- No change to how the pipeline translates: prompts, gates, batching and the dial stay as they are.
- No schema migrations (owner, 2026-10-20; ADR-0043).
- No keychain.
- No new UI library: JavaFX 26, ControlsFX 11.2.5 and Ikonli Feather are enough, and RichTextFX is not added.
- No OS windows beyond the native file choosers.

## Decisions

### D1 — Two dialog layers (README G1)

- **The work layer:** `BusyHost` holds the progress dialogs of model work.
- **The question layer:** `ModalHost`, above the work layer, holds everything else: questions, confirmations, errors,
  the review desk, the provider tests and forms. A question opened during work appears above the progress dialog, which
  keeps its state underneath.

**Card sizes.** `ModalCard` gains `Size.DIALOG` (480 px), `Size.WIDE` (720 px) and `Size.XL`. XL is min(1180, 92 % of
the window) wide with a **fixed** min(820, 90 %) height, so its footer never moves. Every size has a fixed header and
footer and a `ScrollPane` body.

**Keys, in `ModalHost`'s key filter:**
- Enter runs the card's default button. Inside a `TextArea`, Ctrl/⌘+Enter does. A card that sets
  `ModalCard.setEnterNeedsShortcut(true)` (the review desk) runs it on Ctrl/⌘+Enter only; plain Enter is consumed and
  does nothing, so a stray Enter on a focused row never decides a segment.
- Esc runs the card's escape action. It closes a question card, hides long work (the names pass and the export's last
  check), and cancels short work. Only the export dialog's Cancel button asks "Cancel the export? No file is written."
  (owner, 2026-10-20).
- Only About closes on a click outside.

**Unrequested dialogs.** `showWhenFree(card)` queues a dialog the person did not ask for, such as the review desk at
the end of a run, until neither layer shows anything.

**Removed:** `ModelCallsWindow` and `#busy-show-calls`. "Retry with a note" becomes an inline panel in the desk, and
the desk's other questions (Undo my edit, typing that is not saved) are inline bars too, because `ModalHost` shows one
card at a time and a second card would replace the desk.

**Alternative rejected:** a separate OS window for call logs, which the UX research suggested. The owner overrode it
with decision 1.

### D2 — One progress dialog (G4, decisions 2, 3, 11)

**`control/ProgressDialog` and `state/ProgressDialogModel`.**
- The model holds:
  - grouped steps, each with a marker and a one-line result;
  - the stored input and output of each step;
  - the figures, the selection, the "follow current step" flag, the Simple/Detailed mode, and `hideable`.
- The selection is changed by code only while follow is on. Only a person's click or key turns follow off.
- It is fed by `CallFeed` / `ActivityTracker.Handle.calls` and by the step events (D4).
- Publications are coalesced to about 10 a second. Figures tick in `FigureLabel`, so a tick never re-lays out the
  dialog.
- Full prompts are kept only for the last 50 calls. Max's consistency list is a virtualised `ListView` of small row
  records whose detail is looked up by segment id.

A dialog of short work whose result needs a decision stays open after its work ends, offers the result action, and ends only on that action, Cancel or Esc (the brief suggestion's Apply).

**Continue in background** exists for long work only (the names pass, the export pass).
- `ActivityChip` gains an "open" action for hidden work.
- A `HiddenWork` property names the screen the work writes to. Its editable controls bind `disable` to it, under a
  read-only banner.
- Other model work is refused with a toast that offers "Show progress". When the work ends, a toast offers "Review the
  results".

**Parsed views (G5).**
- `control/PromptView` is one card per message role, with a `TitledPane` per `PromptSection`. Each slot has a renderer:
  bullets for the rule lists, a Term | Rendering | Note grid, a Source | Translation grid, and an Id | Segment | Text
  grid built from `<s id>` lines.
- `control/ReplyView` is an Id | Segment | Translation returned | Result grid built from the call's items (K1).
- Raw messages and the raw reply are collapsed blocks with Copy, in Detailed only.
- These two replace `PromptContextPane` and `ReplyPane`.

### D3 — Progress counts every step (G10, PR1–PR3)

**`state/ProgressLedger`** is a pure class with a clock seam. It counts planned, added and done steps per kind.
- **Time left** = Σ over kinds (steps left × the mean measured time per step of that kind over the whole operation,
  pauses excluded).
- **A kind not run yet** borrows the draft time scaled by its size (a check ≈ 0.6 × a draft).
- **When it shows:** after 5 steps or 3 %, rounded, as "about …", moving at most 10 % a minute.
- **The bar never moves back.** Added work holds the bar until the work catches up.
- **What it replaces:** the window in `RunClock` and the percentage in `RunFigures`. Segment counts stay in
  `RunFigures` for the text lines ("74 of 142 segments done"). `ThroughputMeter` stays.
- **Estimates before Start** come only from a measured speed in seconds per segment
  (`ModelSettings.measuredSecondsPerSegment`, with `measuredFrom`). Two writers set it: the Settings inference test
  (the inference stage's wall-clock seconds, rounded to 0.1 s and not below 0.1, taken as one segment, `measuredFrom` =
  TEST; only for a stored provider with a model) and the end of every run (the run's draft tally: the seconds of its
  timed draft steps, pauses excluded, divided by their number, one step being one drafted segment, `measuredFrom` = RUN; a run that timed no draft stores
  nothing, so a refused or early-cancelled run leaves the stored value alone). The last measurement wins, and the estimate names its source. Nothing else estimates.
  The inference test's seconds cover every call the stage made, including the plain-text fallback after a structured
  reply was refused, so a model that refuses the structured probe reads slower than its steady pace.
- **After a restart** the ledger is rebuilt from the stored segment decisions: decided segments count as done draft
  steps, the check steps of chunks whose segments are all decided count as done, the rest as planned; time left stays hidden until the new run has measured its own steps. The ledger itself
  is not stored.

**Alternative rejected:** a better smoothing of the 20-segment window. It still ignores checks, repairs and Max's
after-work, and still projects one stretch over the book.

### D4 — Step events and the hooks the UI needs

Each hook is the smallest change that gives the UI data the pipeline already has. None changes what is translated.

| Hook | Module | Change | Why the UI needs it |
|---|---|---|---|
| **K1** batch items | `:api`, `:pipeline` | An `api.pipeline.CallItem(id, segmentId, target, CallItemStatus, problems)` record, `CallSnapshot.items` (empty by default) and `CallSnapshot.withItems`, mirroring `withOutcome` (`CallSnapshot.java:240`). `CallItemStatus` mirrors `ItemStatus` (OK, MISSING, DUPLICATE, EXTRA, MERGED_SUSPECT). Right after `parser.parse` in `BatchDrafter` (`:164–177`) a new `ModelCalls.parsed(List<String> segmentIds, List<CallItem> items)` (default no-op) is called; `JobModelCalls` forwards it to `CallSnapshots`, which finds the call through its `draftedBy` map (the same lookup `noted` uses, `CallSnapshots.java:73–87, 182–184`) and publishes `withItems` | Rows show a batch's per-segment results; the reply grid maps each id to its segment (T1, T4, G5) |
| **K2** context sent | `:api`, `:pipeline` | `ContextSnapshot.sentSections @Nullable List<PromptSection>`, filled for batch-adopted segments from `BatchStage.adopt` (`prompts.sectionsFor(context, items)`). `RetryDraft` still replays from the snapshot's own fields | "What the model saw" shows what was really sent (T8) |
| **K3** steps | `:api`, `:pipeline` | `JobEvent` gains `StepsPlanned(kind, added, estimated)` and `StepChanged(kind, label, index, state, @Nullable itemRef, @Nullable StepResult result)`. Emitted per source (below). The export keeps `ExportProgress`, which gains the item data; `:ui` maps both into one model | Every progress dialog and the run's ledger (G4, G10) |
| **K4** provider form | `:api`, `:llm` | `ProviderConfig` + `displayName, auth (NONE / BEARER / API_KEY), apiKeyHeader, credentialRef, modelsPath, chatPath, headers, customModels`. Validation in the compact constructor and a pure `api.llm.ProviderConfigRules`. `HttpExchange.buildRequest` resolves `${env:NAME}` and adds auth and the headers. A missing variable is `ErrorCode.missingCredential` before any request. The draft overloads are `ProviderVerifier.verify(ProviderConfig, …)` and `ModelCatalog.listModels(ProviderConfig)`. `StageOutcome` + `VerificationExchange` (method, URL, bodies, status, first token, speed, structured), with the key masked | The Settings form, checks on an unsaved form, the test dialog (S2, S3, decision 7) |
| **K5** per-model | `:api`, `:llm`, `:pipeline` | `ModelSettings` is applied in `:llm` where the bound model sends (`GatedChatModel`), so every caller — run, brief, names pass, export check — gets it without touching the 16 `ChatRequests.build` callers. A `llm.provider.ModelSettingsCache` over `ModelSettingsRepository` (shared with `RefusalMemory`; it reads through the repository on every request, keeping the last settings seen only to log a change and to stand in when the store cannot be read, so a save by any writer is seen by the next request without a refresh hook) gives the current settings per provider + model; a save applies from the next request. Temperature `CHOOSE` keeps the request's per-kind value, `VALUE` replaces it, `OMIT` sends null (the DTOs already omit null); the answer cap lowers a `maxOutputTokens` the request already states and sends none where the request states none; it sets the new `ChatRequest.outputField` (`@Nullable OutputLengthField`, added in group 2), which `OpenAiRequestMapper` reads to write `max_completion_tokens` instead of `max_tokens` (never for Ollama); an OUTPUT_FIELD refusal (K6) flips the request's field, not the stored `outputField` (the refusal row is what holds it, so Send again reverts it). `ChatModelFactoryImpl` passes the cache into the `GatedChatModel` it builds (`ChatModelFactoryImpl:74`). These apply from the next request. **The context window is not touched in `:llm`:** a chunk is packed for its window, so the override is read in `:pipeline` at chunk entry by the three `ContextBudget.windowFor` call sites and reaches the request through `JobModelCalls.send`'s sizing; a save applies from the next chunk | The Models tab, applied to requests not yet sent (S6) |
| **K6** refused settings | `:llm` | `RejectedCapability` gains TEMPERATURE, TOP_P, TOP_K, MIN_P, REPEAT_PENALTY, SEED, OUTPUT_FIELD and NUM_CTX. `CapabilityRejections` matches HTTP 400 **or 422** whose body names a setting that **was sent**; only the setting's name is kept. The `GatedChatModel` repeat goes without the one refused setting, once per distinct setting per call, and is not an attempt. A Models-tab save (7.2) writes the whole `ModelSettings` record, so it carries `refused` through untouched (or re-reads the record just before saving) lest it drop a refusal recorded mid-run. `RefusalMemory` (bound in `LlmModule` over the `:api` port `ModelSettingsRepository`) strips remembered settings before the first attempt. `:llm` and `:pipeline` tests that build an injector from `LlmModule` alone install a small test module binding a fake `ModelSettingsRepository` and `ProviderConfigs` (`:llm` cannot see `:persistence`). One WARN line | FR-INFER-09e (`04_LLM_PROVIDERS_AND_MODELS.md`); "Settings this model refused" |
| **K7** projects | `:api`, `:pipeline` | `Project` + `name, nameKey, nameOrigin, createdAt, lastOpenedAt`; ids from an `api.project.IdSource` (a UUID in production, bound in `PipelineModule`; a counter in tests), which also gives `TranslationJobImpl` its one `jobId`. `ProjectService` becomes id-based in two steps: first `list, create, open` replace `importBook / continueBook / decidedSegments` with behaviour unchanged (callers `ImportViewModel:172`, `ImportGuard:122`, `app/cli/BookOpener:44` and about 42 `:pipeline` tests), then `rename, remove, translateInto, relocate` are added. `ProjectNames` checks names and `Project.automaticName(title, source, target)` is the one place the automatic name is built; a name is unique within its book. `makeSecondTranslation(projectId, brief, name)` replaces the brief and gives the typed name in one stored change (a blank name is a `validation` error, a taken name `NameInUse` with nothing stored), which is how "Same language pair" makes a second translation. `ProjectService` answers refusals as data in `Result.ok` (ADR-0039): `ProjectChange` (`Saved`, `PairInUse`, `NameInUse`), `ProjectOpening` (`Opened(book, interrupted)`, `BookFileMissing`, `BookFileChanged`) and `BookRelocation` (`Relocated`, `DifferentBook`). `Project.title` holds the book title (`books.title`); `create` reuses the book's automatic, target-less project instead of making a second one. `open` rewrites a stored RUNNING or PAUSED run as CANCELLED (counts and model kept, `endedAt` now) and reports `interrupted`. `translateInto` copies the segments as pending, the glossary without targets and the lexicon terms without renderings, never the memory, runs, summaries or deferrals, and stores no model (the first run takes `model.current`). `ProjectRepository.relocateBook(contentHash, path)` stores the path "Find the book…" accepted. `RunRecord.model` becomes a `ModelSelection`; a `lastExport` record is added. `ProjectSummary(project, decidedSegments, totalSegments, latestRun, lastExport)` carries what a Projects row shows; the quality of a row comes from `Project.brief().dial()`, because a `RunRecord` has no dial | Projects, project names, continue after a restart (PN, D11) |
| **K8** title, files | `:ui`, `:pipeline` | `ReviewDesk.originalTitle` (backed by `pipeline/project/OriginalTitles`) takes the original title in order: a non-blank `aux:title` segment on a non-Markdown book (step 1, the book's details; a Markdown book's metadata title *is* its front matter), then the front-matter title (`aux:fm:title`), then the first heading of a non-auxiliary unit (display text, first line only), then the file name without its format suffix (no segment); it returns the text, a reason (`TitleOrigin`) and the segment id. `BookTitleViewModel` and `FileNameTargets` use that segment id (a file-name title has no target and cannot be edited), and the card shows the reason line. `util/paths/ProjectFileLabel` and `DestinationPath` (shared with the command line, not `ExportPathRules`) add a named project's label after the language (`earth-gravity.uk.26b-max.md`) when another project of the book shares its language pair; the window finds it off the FX thread, so a rename made after the Export screen is open leaves the old label until the book is next opened | E1, README "Project names" |
| **K9** knobs | `:pipeline`, `:ui`, `:app` | **Reasoning and sampling:** `ChatRequests.build` takes a `RequestDefaults(reasoning, sampling)` argument; an injected `pipeline.prompt.RequestDefaultsSource` resolves each from its environment variable when set, else the stored `run.reasoning` / `run.sampling`, else today's default, and the 16 callers pass it. **Reasoning level:** `ChatRequest.reasoningEnabled` (a `Boolean`) becomes a `@Nullable ReasoningLevel` (OFF, LOW, ON; null = the provider's default); Ollama sends `think` false / `"low"` / true and the OpenAI-compatible client `reasoning_effort` `none` / `low` / `medium` (`OpenAiRequestMapper:29–32` today: none and medium). `ReasoningPlan` keeps its per-kind env syntax and gains `low`. **Review mode:** the startup instance binding of `ReviewMode` (`AppModule:54`) becomes an unscoped `@Provides ReviewMode` over `ReviewModeSource` (a flag that was set wins, a flag naming no mode is set and means Unattended; else the stored `run.reviewMode`; else Unattended). `RunStarter` and `pipeline/review/RetryDraft` (`:83`) inject `Provider<ReviewMode>` and read it at each start or retry; `ReviewViewModel`, `ReviewPauseFollower`, `ReviewAvailability` and `TranslatingViewModel` read the run's mode from `mirror.runMode()` (the singletons cannot reach `RunContext`, which lives in `TranslationRunner`), falling back to the provider when no run has published one; `RunStarter` reads the provider once per start. `UiModule:149`'s `requireBinding(ReviewMode.class)` and the tests' `ReviewModeTestModule` stay valid. The command line keeps ignoring the review-mode flag | The Generation and Automation tabs; the CLI and evaluations keep their env vars |
| **K10** first launch | `:ui` | `state/LocalModelProbe` runs once when `firstLaunch.probed` is absent (ADR-0044: window only; a fresh database is a first launch); "Try again" on Projects calls it again on request | First launch (decision 13) |

**Where the step events come from:**
- **The run.** At chunk entry (`WorkList.enterChunk`), one draft step per segment of the chunk and `reviewPasses` check
  steps for the chunk (Fast 0, Balanced 1, Max 2). Then added steps:
  - a repair round (`RoundStarted`);
  - a batch re-ask;
  - a timeout resend (an attempt over 1);
  - the REVISE stage and the summaries on Max.

  A kept-as-is (verbatim) segment is a draft step done at once with no call. Example: a 12-segment unit with 2 verbatim
  segments under Balanced packs into 2 chunks (8 + 4): 12 draft steps (2 done at once) and 2 check steps.
- **The names pass.** `NamesPassImpl.step(name)` emits each of its 7 stages plus "write".
- **The brief.** `BriefSuggester` emits book-knowledge (or skipped), K samples (3 / 4 / 5 by dial) with each sample's
  value, agreement and quote, and synthesis.
- **The export.** `ExportCalls` reports each item's before, after, verdict and the paragraphs around it
  (`ExportProgress.Neighbours`) through `ExportProgress`; `ExportActivity` adds one `STAGE` group row per stage reached
  (uncounted while items are planned), which the dialog shows as Steps, the reread items and Finish. "Write the file
  now" is `ExportJob.writeNow()`: the stages read `ModelCalls.isStoppingEarly()` before each item, the call in flight
  is interrupted, every stored fix stays and the rest is `ConsistencyChecks.notReread`.

**Alternative rejected:** deriving steps in `:ui` from call kinds. The UI would duplicate the dial's plan and could not
know the planned review passes.

### D5 — Layout (L1–L10)

- **`control/LayoutClass`.** One width listener on `#shell-content` sets the pseudo-classes `compact` (< 1040),
  `regular` (1040–1439) and `wide` (≥ 1440), with 16 px hysteresis. It never logs. Regions still decide their own
  two-column switch from their own width (`TwoColumns`). Names & style switches at 1388 px of its own width, which is the wide class's 1440 px of content less the 52 px the shell pads a screen with.
- **`control/Overrun`.** It adds a tooltip to a `Labeled` only while its text is cut.
- **`#shell-banners`.** A `VBox` between the toolbar and the content `ScrollPane`, fed by `state/ShellBanners`, which
  is built from `RunStateView` (D8). Run banners show on every screen while they apply. The hidden-work banner is not one of them: it says "this screen", so Names & style shows it itself, as a local `Banner` bound to `HiddenWork.freezes`.
- **`fills-viewport`.** The style class opts a screen out of `minHeight = USE_PREF_SIZE` in `AppShellView.showContent`.
  Translating (live card min 460), Import (its browser `SplitPane` is at least 360 px high, see D15), Names & style and Settings each declare their minimum
  height, and below it the page scrolls.
- **Sizes.** The content is capped at 1320 px on form and list screens; prose at 680 px. The nav column is 236 px, or
  200 px under a 1200 px window.
- **Splits.** List | detail `SplitPane`s never stack: the live card, the review desk, the progress dialogs and Settings
  › Providers.

### D6 — Tables, grids and markers (G9, G11)

- **Editable tables.** `TermTables` moves to `CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN` with minimum widths (Term
  140, Kind 96, Translation 170, Gender 110, Locked 64, action 36) and keeps its fixed 46 px rows.
  - A changed row gets a `:changed` row pseudo-class (a left edge), which replaces the chip in the term cell
    (`ChangedMarks`).
  - The full text is in the tooltip.
- **Read-only grids.** These are `GridPane`s with the `read-only-grid` style class and a small factory, not a new
  component.
- **State markers.** `control/StateMarker` is a `Label` with an Ikonli Feather icon and a word. Its six style classes
  (`waiting`, `working`, `done`, `attention`, `failed`, `kept`) map onto the existing status token roles, so no token
  is added.
- **Links and selection.** Links are underlined in `-color-info-fg`. The selected segment of a `SegmentedButton` gets
  a 2 px `-color-text-strong` bar.
- **Scrollbars.** One 12 px rule with a `muted-2` thumb on a `surface-2` track; the nav column uses `nav-fg-muted` on
  `nav-bg-2`. The eight regional copies in `theme.css` are aligned to it.
- **Tests.** `ContrastTest` checks the thumb at 3:1, the links at 4.5:1 and the selected bar.

### D7 — The protected editor (decision 12)

**`control/ProtectedMarks`** is a `UnaryOperator<TextFormatter.Change>` filter on the review `TextArea`.
- It widens a deletion, replacement or cut that touches part of a `⟦n⟧` mark to the whole mark.
- On paste, it keeps only marks missing from the editor and drops duplicates.
- It lets through a whole-text load (`setText`) unchanged.

**Mark notation.** The stored masked text uses `⟦gN⟧`. The editor shows each as `⟦N⟧` with the same number
(`⟦g0⟧` → `⟦0⟧`). Loading the editor maps `⟦gN⟧` → `⟦N⟧`, and saving maps it back before the placeholder gate runs.
Detail panes keep their chips.

**`state/MarkStatus`** compares the editor's marks with the source's: missing, doubled, or complete. The main button
binds to "complete" and the strip below the editor shows what is wrong. Undo and redo skip the filter, which is safe
because every change it recorded already covered whole marks.

**Alternative rejected:** RichTextFX chips. It is a new library and a heavier control than the review editor needs.

### D8 — The run-state table (README "Run states", theme A)

**`state/RunStateView`** is one record per README row (16 rows). It is derived from:
- `RunState`;
- the recovery details (`RunNotice`, `RunInterventions`, `PauseNotice`);
- whether stored progress exists;
- the to-review count.

Each row gives the title-bar text key, the AI-model chip's meaning, the toolbar actions, the banner, the live line and
the current-call row marker. The title bar, the toolbar run actions (moved out of `TranslatingView`), `ShellBanners`,
the nav footer and the live card all read it. It is a pure function of its inputs with no actions of its own, so it
does not grow into a coordinator; each reader keeps its own commands. `RunStateMappingTest` walks all 16 rows.

### D9 — Persistence (ADR-0043)

**Libraries.** `org.xerial:sqlite-jdbc`, `org.jdbi:jdbi3-core` (fluent API) and `jackson-datatype-jsr310` in
`:persistence`; `jackson-databind` is already in the catalog. No Flyway before the first release.

**`persistence.sqlite.SqliteDatabase`.**
- **Connections.** `SQLiteDataSource` with a `SQLiteConfig` applies `journal_mode=WAL`, `foreign_keys=ON`,
  `synchronous=NORMAL` and `busy_timeout=5000` on every connection it opens. There is no pool library.
- **Schema.** At `AppLifecycle` phase 2, `db/schema.sql` runs with `CREATE … IF NOT EXISTS` and sets `PRAGMA
  user_version = N`.
- **A file that does not fit.** A different `user_version`, or a file that fails to open or fails `quick_check`, is
  renamed `bookloom.db.old-<yyyyMMdd-HHmmss>` and a fresh database is created. That is one WARN line, with no dialog.

**`WriteExecutor`.** A single-thread executor runs every write as one JDBI transaction. Reads use their own
connections. Port methods block on the write future and return `Result`; they are never called on the FX thread (the
`:ui` callers already use the background runner; the FX-thread check below enforces it).

**Schema (`db/schema.sql`):**

| Table | Key and columns | Holds |
|---|---|---|
| `settings` | `key` PK, `value`, `type` | preferences (D12) |
| `providers` | `id` PK, `json` | `ProviderConfig` |
| `model_settings` | PK (`provider_id`, `model_id`), `json` | `ModelSettings`, refusals and the measured speed included |
| `books` | `content_hash` PK, `source_path`, `format`, `title`, `json` | the book, stored once |
| `projects` | `id` PK, `content_hash` → books, `name`, `name_key`, `name_origin`, `source_lang`, `target_lang`, `created_at`, `last_opened_at`, `brief_json`; UNIQUE (`content_hash`, `name_key`); index (`content_hash`, `source_lang`, `target_lang`) | the project; a name is unique within its book (owner, 2026-10-20) |
| `project_docs` | PK (`project_id`, `kind`), `json` | glossary (with tombstones), lexicon (with removals), summary, latest run, lastExport |
| `segments` | PK (`project_id`, `segment_id`), `ord`, `unit_id`, `status`, `json`; index (`project_id`, `status`) | `SegmentRecord` |
| `tm_entries` | PK (`project_id`, `source_hash`, `context_key`), `json` | `TmEntry` |
| `deferrals` | PK (`project_id`, `segment_id`, `reason`, `waiting_on`), `json` | `Deferral` |

- **Cascade.** Every child table has `FOREIGN KEY (project_id) REFERENCES projects ON DELETE CASCADE`.
- **The name key.** `name_key` is `strip().toLowerCase(Locale.ROOT)` computed in Java, because SQLite's `lower()` folds
  only ASCII and Cyrillic must fold too.

**`persistence.sqlite.JsonCodec`.** A Jackson mapper with the jsr310 module and mix-ins for the sealed types, so `:api`
carries no annotation. Jackson calls `setAccessible` on public members by default, which fails on a package `:api`
exports but does not open; the mapper therefore disables `MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS` and
`OVERRIDE_PUBLIC_ACCESS_MODIFIERS`, so `:api`'s `module-info` stays as it is. `:persistence` adds `requires
com.fasterxml.jackson.databind`. A parameterised round-trip test, run on the module path, covers every stored record,
nested ones included.

**Adapters.** The `Sqlite*` adapters cover project, segment, glossary, TM, summary, deferral, run, lexicon, settings,
model settings and provider configs.
- `SqliteCheckpoint` writes the segments, TM entries, deferrals, glossary additions and the run record in one write
  transaction. This is ADR-0034's commit rule, unchanged.

**Modules.**
- `PersistenceModule` splits into `InMemoryPersistenceModule` and `SqlitePersistenceModule`. Each binds every storage
  port, including `ProviderConfigs`, whose binding leaves `LlmModule`. `InMemoryProviderConfigs` moves to
  `persistence.memory` with the presets.
- `CoreModules` takes the persistence module as a constructor argument: the window passes `SqlitePersistenceModule`,
  `TranslateLauncher` (the CLI) passes `InMemoryPersistenceModule`.
- Until the split (task group 3), `PersistenceModule` binds `ProviderConfigs` and the new settings and model-settings
  ports to their in-memory adapters. `ProviderConfigs.register` is renamed `save` (insert or replace; the one caller is
  `app/cli/ProviderSetup:55`) and `delete` is added.
- The in-memory `ProviderConfigs` holds the two presets (the command line's providers). The SQLite one starts empty:
  the window lists what the first-launch look found and what the person added (D13).

**One document, one writer.** The glossary and the lexicon are each one JSON document per project, and Names & style
stays editable while translating. So every change to them, a checkpoint's glossary additions included, is a
read-modify-write inside one `WriteExecutor` transaction. A person's edit and a run's addition never overwrite each
other, and a contract case proves it.

**Storage info.** `api.persistence.StorageInfo` (`location`, `sizeBytes`, `backupTo` = `VACUUM INTO ?` run on the single writer thread
outside a transaction, because SQLite refuses `VACUUM` inside one) serves Settings › Storage & logs. A destination that already exists is refused with `validation` and never replaced. The SQLite adapter's `location` and `sizeBytes` come with the other SQLite
adapters; `backupTo` comes with the Storage tab. `ProjectRepository.recordExport` / `lastExport` keep the `ExportRecord` in `project_docs`.

**The FX-thread check.** `SqliteDatabase` checks the calling thread's name at every read and write entry. On "JavaFX
Application Thread" it writes one WARN line with the stack (once per call site); with the system property
`bookloom.storage.failOnFxThread=true`, which every test task sets, it throws instead. It imports nothing from JavaFX,
so `:persistence` stays FX-free.

**Errors.** A storage failure is `ErrorCode.internal`, with the safe details `storage <operation>`. `ErrorCode` stays
at fifteen codes (ADR-0039).

**Tests.** The contract chain plus `LexiconRepository` and the three new ports, run against both adapters. SQLite runs
on a `@TempDir` file with the schema applied, and no Testcontainers.

**Alternatives rejected:**
- Typed entity tables plus Flyway now (ADR-0009 as written): a mapper and a migration for every field while the records
  still change.
- A single key/value table: uniqueness and lookups would move into Java.

### D10 — Projects and the book (PN, decision 9)

- **Ids.** Ids come from `IdSource` (a UUID in production, a counter in tests). A project no longer derives from its
  path, so one book can have several projects.
- **The book row** is shared by `content_hash`. It holds the book's metadata and file path, never its bytes: the
  file is re-read at open. It goes when no project uses it. `books.source_path` is the one path. `Project.source` is
  read from it, and "Find the book…" updates it.
- **`open(projectId)`.**
  - It re-reads the book from `books.source_path` into `OpenProjects` (the parsed-document cache stays) and checks the
    hash.
  - When the file is gone or changed, it returns a typed result that the UI turns into "Find the book…", which accepts
    a file only when its hash matches.
- **`ProjectNames`.**
  - It builds `<title> · SRC → TGT`. The title is the metadata title before ":", or the file name. Under Auto the
    source code is left out until known, and before a target is chosen the name has no arrow
    (`Title · EN`, or only the title while no source is known).
  - It follows the languages while the name is AUTO.
  - It reports a used pair (same book, same languages) or a duplicate name as typed results, never as an exception.
  - A named project returns to its automatic name only when no other project of the book has the pair.
- **`translateInto(projectId, target, name)`.** It creates a project on the same book and copies the brief, the
  Glossary and the Recurring terms (term, kind, gender, locked) with every target emptied. It never copies the
  translation memory (the source document's open question 5, recorded default). The new project uses the current AI
  model (`model.current`); there is no "start from" choice.
- **Names are unique within a book.** Two books with the same title and pair both keep `Earth Gravity · EN → UK`;
  inside one book a duplicate name, compared by `nameKey`, is `NameInUse`.
- **"Find the book…"** accepts a file only when its hash matches, then calls `relocateBook`.
- **`remove(projectId)`.** It deletes the project's rows. It never touches the book file, the exported files or the
  book's other projects.
- **The "Used" count** for recurring terms comes from the book index's postings for the term.

### D11 — Continue after a restart (resume)

- **What is stored.** The run record and the segment decisions are stored with every checkpoint.
- **What opening shows.** A project whose stored run reads RUNNING or PAUSED (a clean quit or a crash) is treated as
  interrupted when it opens. It shows **Ready to continue** and never continues by itself.
- **How it continues.** Continue translation starts at the first pending segment through the existing resume path
  (`RunStarter`).
- **The AI-model questions.** `state/ContinueGuard` runs inside `TranslatingViewModel.begin`, after the missing-input
  check and before preparing (a paused resume skips it). It reads `RunRecord.model` of the latest run through
  `ProjectService.list()` on every confirm and compares it with the chosen model. Order: "A different model is chosen"
  first, then the answer test of the model to use ("not answering" = `ModelCatalog.listModels(providerId)` failed or
  lacks the model; an empty list is answering). "Try again" there re-lists only that model; it never runs
  `LocalModelProbe`. Escape is Not now / cancel.
- **Reopen last project.** A listener on `CurrentProject` (`LastProjectRecorder`) writes `app.lastProject` whenever a project opens, from the Projects screen or from Import. At launch, with
  `automation.reopenLastProject` on and the project still stored, the window opens it; otherwise, or when it is gone,
  the window opens Projects.

### D12 — Settings storage

- **Port.** `api.persistence.SettingsRepository` has `get(key)`, `put(key, value)` and `all()`. `api.settings.AppSetting<T>`
  is the typed registry of keys, types and defaults. The new package `api.settings` is exported in `:api`'s
  `module-info`. Until the persistence split, `PersistenceModule` binds the in-memory adapter.
- **Keys:**
  - `ui.theme`, `ui.language`, `ui.technicalDetails`;
  - `run.reviewMode`, `run.defaultDial`, `run.reasoning`, `run.sampling`;
  - `automation.openReviewDesk`, `automation.lastCheck`, `automation.keepAwake`, `automation.reopenLastProject`;
  - `model.current`, `app.lastProject`, `firstLaunch.probed`, `help.sleepLineClosed.<projectId>`;
- **The detailed-log switch is not a database key.** Logging is configured before the injector and before the database
  opens, so the switch is a marker file `detailed-log.on` in the data folder. `TraceFileResolver` checks it with `java.nio`
  after the paths are published, and `BOOKLOOM_TRACE_FILE` still overrides it. Order: the environment variable, then the
  `bookloom.trace.file` property, then the marker (which can only switch the log on), then the environment default
  (a development run is on). A rejected value is kept for the log and resolution falls through. Only `DetailedLogSwitch`
  creates or deletes the marker.
- **Automation.** `AutomationSettings` stores the Automation choices at once, in the background; after a review mode is
  stored it calls `api.settings.ReviewModeChoice.stored(mode)` (implemented by `ReviewModeSource`) so the next run's
  read sees it, and `launchOverride()` names the variable or property that decided the launch. The keep-awake choice
  lives in `ui.state.KeepAwakeGate`, which `StoredPreferences.load()` seeds; `AwakeHolders` start the hold only while
  the gate is on and release it at once when it turns off. The default quality is read by `ProjectServiceImpl` when a
  new project is first stored.
- **When writes happen.** Providers and Models write on Save (the pinned footer, `LeaveGuard`'s dirty check). The other
  tabs write at once, through the background runner.
- **App language.** `ui.language` applies at the next launch: `OsLocaleProvider` reads it before the OS locale. `Messages`
  decides its locale lazily on its first use (the eager review-desk launcher builds it before phase two), and
  `StoredPreferences.load()` runs in `init()` right after phase two, off the FX thread, seeding `StoredLanguage` and
  `ThemeController` (the stored theme is applied at attach without a write). A seed that arrives after the decision is
  logged and applies from the next launch. `AppLanguage` holds the Appearance choice, stores it in the background and
  says whether it differs from the language on screen.
- **Generation.** `GenerationSettings` reads and stores `run.reasoning` and `run.sampling` in the background; the
  pipeline's `RequestDefaultsSource` still reads them once per job, so a running run keeps its settings. The environment
  variable or property that overrides a choice is named through `api.settings.GenerationOverrides` (implemented by
  `RequestDefaultsSource`). The quality rows come through `api.pipeline.DialProfiles` (implemented over `DialParameters`)
  and the time limits from `api.llm.CallLimits`, which `RequestTimeouts` and `RetryPolicy` also read, so `:ui` never
  imports `pipeline.*` and displays what the client enforces.
- **The current AI model.** `model.current` (a `ModelSelection`) is what a new run and a new project use. The
  first-launch look sets it when it finds one model, the person sets it with "Use this one" (Providers) or the
  title-bar model chip, and a run stores the selection it started with in `RunRecord.model`.
- **Restart now.** `:ui` calls an `api.AppRestart` port that `:app` implements; it answers `Result<Boolean>` (true:
  a new copy was started; false: this binding cannot restart, as in the command line). `DesktopAppRestart` builds the
  command from `ProcessHandle.current().info()` (`RestartCommand`; no command, or `java` without arguments, is an
  error and nothing else), records it in `PendingRestart` (carried by `StartupContext`) and asks the toolkit to exit.
  It never starts the child itself: `Launcher.main` starts it, output discarded, only after `Application.launch`
  returned (so `stop()` closed the database) and the single-instance lock was closed, so the new copy never meets
  files the old one still holds. The sequence is: build command, exit, shutdown closes the database, lock released,
  child spawned. A child that cannot start is logged; the window is gone either way. A second ask while one is
  pending is ignored (and the button is disabled). `CallLimits` is an interface of constants because the
  `records-first` rule allows only records, interfaces and enums in `:api`. The window asks `ExitGuard.requestClose` first, so work under way meets the quit question, and the
  choice is stored before the restart is asked.
- **The review mode is read per run.** `RunStarter` reads `run.reviewMode` at each start; a launch flag or environment
  variable resolved by `ReviewModeResolver` overrides it.

### D13 — Providers and per-model settings

- **Provider configs.** `SqliteProviderConfigs` implements `ProviderConfigs` (`save`, `delete`, `find`, `all`). The
  window's providers list starts empty, then holds what the first-launch look found plus anything the person adds; the
  two presets stay the command line's providers. The Add form's Kind pre-fills the preset address (`localhost`), while
  the look uses `127.0.0.1`.
- **Per-model settings.** `api.llm.ModelSettings(providerId, modelId, TemperatureChoice choice, @Nullable Double value,
  @Nullable Integer contextWindow, @Nullable Integer maxOutput, OutputLengthField field, List<RefusedSetting> refused,
  @Nullable Double measuredSecondsPerSegment, @Nullable MeasuredFrom measuredFrom)` (`measuredFrom` is null exactly when
  the speed is). `RefusedSetting` is `(name, at, insteadDid)`. `VerificationExchange` also carries the prompt and
  completion token counts the server reported, which the test dialog's Tokens row shows. "Send again" removes a
  refusal.
- **The key.** It is never stored: `credentialRef` is a variable name, and a header value that looks like a credential
  must be `${env:NAME}`. The header and key values are resolved in `HttpExchange` just before the request and never
  logged.
- **What the test dialog's figures mean.** "First token" is the server's prompt-evaluation time (Ollama reports it; an
  OpenAI-compatible server shows a dash), not a streamed time to first token, because chat is read whole. "Status" is
  the HTTP status carried in a failed stage's safe details; "No answer" is shown only when nothing came back, and a
  dash when a failure carries no status.

- **What the Models tab asks the program (task 7.2).** `ModelCapabilities.report(providerId, modelId)` returns
  `ModelReport(@Nullable contextLength, @Nullable loadedContext)`. `ModelCapabilityService` fills the first from
  `client.contextLength` and the second from `ProviderClient.loadedContextLength`, which only the Ollama-native client
  overrides: it reads `GET /api/ps` and takes the `context_length` of the entry whose `name` or `model` is the id. Any
  failure or an absent entry is "unknown", never an error; an OpenAI-compatible server is sent no `/api/ps`, and the
  run path (`contextLength`) is unchanged. Known caveat: an LM Studio "can read" figure may in fact be the loaded
  figure, because that server reports one number; the tab words it as what the program said.
- **Structured answers are per session.** `state/StructuredAnswers` (a singleton confined to the FX thread) keeps, per
  provider and model, whether the last inference test of this session got a structured answer;
  `ProviderTestRunner` writes it only for an inference test of a stored provider with a model whose inference stage
  passed with an exchange. It is not stored: a model not tested since launch reads "not checked yet".
- **Save and Send again are updates.** `ModelForm` saves with `ModelSettingsRepository.update(provider, model, cur ->
  draft.apply(cur))` (`ModelSettings.withChoices` keeps the ids, the refusals and the measured speed), so a refusal the
  chat layer records while the tab is open is never overwritten; Send again is `update(..., s ->
  s.withoutRefusal(name))`, acts at once and leaves the draft, the baseline and the dirty mark alone.
- **The window's unsaved question.** `DirtySource.subject()` returns an `UnsavedSubject(title, text, name)` so each form
  asks in its own words ("Save the changes to Ollama?", "Save the settings of gemma4:e4b · Ollama?").
  `SettingsDirtySource` replaces `ProviderFormDirtySource`: dirty when either form is, discards each dirty form, saves
  the provider form and then the model form, and stops at the first failure. `screen/SettingsTabGuard` asks for a tab
  change (the form of the tab being left decides) and for a change of model, once for each move.
- **The roster.** `state/ModelRoster` lists the models of every provider (listed ids, then the provider's extra
  models, then models with stored settings), one listing per provider on the I/O executor, on the first visit to the
  tab and on Refresh; a reading that is no longer the latest is dropped.

### D14 — First-launch look (ADR-0044)

`state/LocalModelProbe`, on a background thread after the window shows:
1. It runs `ModelCatalog.listModels(ProviderConfig)` against `http://127.0.0.1:11434` (Ollama) and
   `http://127.0.0.1:1234/v1` (OpenAI-compatible), with a 1 s connect timeout. It runs no inference and does not wait
   for the `InferenceGate`.
2. Found with models: it saves every program that answered, and Ollama becomes current when both answer. It
   sets `model.current` when there is one model or asks which one when
   there are several.
3. It sets `firstLaunch.probed` whatever the result. The flag lives in the database, so a fresh database (another
   schema version, renamed aside) runs the look once more (ADR-0044).
4. Projects shows the result; "Try again" runs steps 1–3 again because the person asked. Stored providers use the
   default timeouts (the 1 s connect timeout is for the look alone), and the look sends one request per address
   (the retry lives in the verifier, not in the listing).
5. A program that answers with zero models counts as not found.

Tests: `LocalModelProbeTest` fakes the catalog and asserts the exact addresses, one call each and no second look; a
WireMock test of `ModelCatalog.listModels(ProviderConfig)` in both dialects asserts the server receives only `GET
/api/tags` or `GET /v1/models`.

### D15 — Import page (task 6.2)

- **One verdict line.** `state/ImportVerdicts.of(Messages, ImportState)` words one `ImportVerdict(kind, text)` per
  state without a scene, and `screen/ImportVerdictView` draws it as a `Banner` (role by kind: ok, warn, err, info) with
  the controls the kind offers. Reading is indeterminate with the text "Reading…", because the parsers and
  `ProjectService.create(Path)` report no chapter count while they work; the line "chapter n of N" is not built.
- **Words.** `state/WordCount.text` is the one owner of the count's wording (exact below 1,000, else "about" and the
  nearest hundred); the verdict, the fold's summary and the chapters row all use it.
- **Fold.** `screen/AboutBookFold` is a closed `TitledPane` whose graphic is the one-line summary; its body keeps the
  details and facts under their old `import-row-*` and `import-facts-card` ids, and shows "No cover" in place of a
  blank box.
- **Browser.** `screen/StructureTextBrowser` is one `SplitPane` (`import-browser`) of the chapters, the segments and the
  text, with minimum widths 160 / 240 / 220 and a minimum height of 360 px. Below a 760 px content width the text is
  re-parented under the segments in an inner vertical split; the three panes are fields, so the pick, the search and
  the text survive the change. The page is a `ScrollPane` with `fitToHeight`, so it scrolls only below the sum of its
  minimums and needs no scroll bar on a large window.
- **The guard.** While a project is open `ImportGuard` asks "Open another book?" first, whatever the run state. "Open
  as a new project" stops a RUNNING run and waits for it (other states are left as they are) and never removes the
  open project; "Replace and delete this translation" ends any run that has not ended, then
  `ImportViewModel.openReplacing` removes the project and creates the book. With no project open, or after "Open as a
  new project", the file's SHA-256 is read off the FX thread and matched with the stored projects' content hashes: a
  match is `ImportState.AlreadyInProjects` and creates and removes nothing. `ImportViewModel.open` never removes a
  project, and its `cancel()` during a read sets the state idle at once, removes the project the read creates when it
  finishes, and ignores its answer.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| JDBI is an automatic module and brings `geantyref`; sqlite-jdbc loads a native library in the jpackage image | A module-path boot test in `:persistence`, the lock and licence checks for the transitive jars, and the packaged-image smoke (`resolve-all passed`) |
| Jackson and records across JPMS modules; sealed types | Mix-ins plus a round-trip test of every stored record; public records only |
| `:api` record changes ripple into many constructors and exhaustive switches | Each group-2 task runs `./gradlew build`; `JobEventContractsTest` |
| Ids no longer derive from the path, so a moved file is not found | The hash check plus "Find the book…" |
| The first-launch exception widens the offline rule | ADR-0044's limits (two addresses, once, a listing), an egress test, and the rule written into `AGENTS.md` and `.claude/rules/offline-and-privacy.md` |
| A schema change discards a developer's database before release | Accepted by the owner (ADR-0043); the old file is renamed, never deleted |
| The TestFX suite grows | `fastTest` / `slow` tags as today; `scripts/test-focused.sh` while coding |
| Ukrainian labels are about 26 % longer | `LayoutFitTest` in `en` and `uk` at 960×640 and 1920×1080 for the title bar, toolbar, dialog footers and segmented controls |

## Migration Plan

- **No data migration.** No user has stored data yet. The window's first launch after this change creates the database.
- **Rollback.** Revert the branch. The old in-memory adapters are still in `src/main`.

## Open Questions

None that change the specs or the tasks. One item is recorded for the owner's hand check: the sentence "After sleep,
translating goes on by itself" ships only after one real laptop sleep on e4b (task 9.4).

The Recurring terms copy of "Translate into another language" (task 4.7b): `LexiconEntry` has no kind and no lock, so
the spec's "warden (title, locked)" cannot be copied as written; the copy keeps the term only. If the owner wants the
kind and the lock kept, `LexiconEntry` gains them first (a stored-record change).
