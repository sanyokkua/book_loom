# Tasks

These rules apply to every task. The design decisions (D1–D14) and hooks (H1–H10) are in `design.md`. The behaviour
of every screen, dialog and state is in `docs/specification/mockups/README.md` and in this change's `specs/`.

- **Size.** This change is larger than the ~10-task norm (`AGENTS.md#how-work-is-planned`) because the owner asked for
  it as one change that finishes the UI.
  - Each numbered group is a mergeable unit. The repository is green between groups: the last task of every group runs
    `./gradlew clean build check spotlessCheck` and pastes its tail.
  - A task that changes an `:api` shape runs `./gradlew build` itself.
  - **∥ groups.** Groups 3 and 4 may run in parallel once group 2 is merged. They write no common file. Task 4.9 waits
    for group 3.
  - **Dependencies.** Group 5 needs groups 3 and 4; groups 6 and 7 need group 5.
  - **`:app` files.** Group 3 edits only `CoreModules`, `AppLifecycle` and `TranslateLauncher`. Group 4 edits only
    `app.bootstrap.ReviewModeResolver`. `IdSource`'s UUID binding goes in `PipelineModule` (4.7), and `RefusalMemory`
    is bound in `LlmModule` (4.3).
- **Git.**
  - Work on `feature/finalize-ui-and-persistence--<n.m>-<slug>` (for example `--3.2-sqlite-database`) and commit there.
  - Squash-merge one commit into `feature/finalize-ui-and-persistence`, then delete the task branch. A checked task is
    complete only when that merge has succeeded (`AGENTS.md#git`).
  - Never `--no-verify`, never force-push, never merge into `master`.
- **Tests first.**
  - Write the task's test, run it and see it **red**, then implement and see it green (`AGENTS.md#definition-of-done`).
  - Name tests `method_state_expected`. Put no `if`, `for` or `while` in a test body (use `@ParameterizedTest`). Use
    `@TempDir` for files. Write expected values out instead of recomputing them (`.claude/rules/testing.md`).
  - UI tests run on JavaFX 26's headless platform. "Wired" is shown behaviourally: drive the view-model and assert the
    node changed; fire the control and assert the recording fake was called.
  - Use WireMock at the HTTP seam for **both** dialects (`/api/*` and `/v1/*`) whenever a request changes. No Mockito.
  - Persistence is proven by the contract tests over **both** adapters, with SQLite on a temp file and the schema
    applied.
  - Fixtures are book-neutral (memory: no names or text from tested books).
- **Cadence.** Run `scripts/test-focused.sh` while coding, and `scripts/test-focused.sh --full-module` before each
  commit. Never run two gates at once. A run much longer than the last baseline is hung: kill it and diagnose.
- **Logging is part of done.**
  - INFO for lifecycle, DEBUG for every working method's parameters and every branch taken, TRACE for book text,
    prompts, replies and JSON documents, WARN for degraded, retried or refused outcomes, never a credential
    (`.claude/rules/logging.md`).
  - No log call inside a layout pass, a cell's `updateItem`, or a width or scroll listener.
- **Compare (every task marked "Compare:").** This is README "For AI agents":
  1. Serve the mockup with `python3 -m http.server 8799 --bind 127.0.0.1 --directory docs/specification/mockups`.
  2. Capture the named states at 960×640 and 1920×1080, in light and dark, with the built-in browser (`go(…)`,
     `setRun(…)`, `openDialog(…)`, `setTheme(…)`).
  3. Capture the app in the same state, size and theme (`./gradlew :app:run`, or `scripts/e2e-fixture.sh` for a run
     with the pseudo model; `screencapture`, per the memory "Driving the JavaFX app").
  4. List every difference and fix the app until only the accepted kinds remain (fonts, antialiasing, control chrome;
     sizes within about 4 px or 10 %).
  5. Store the pairs and the list under `.temporary_context/ui-evidence/<task>/`, which is git-ignored.

  Never port the mockup-only elements: the "Mockup reference" pages, the dashed demo bars, the blue requirement badges,
  the "Mockup: …" buttons, the ⋮ header glyph, and the fixture `D`.
- **Reuse first.** Each task names the existing class it extends. A new class is added only where the task says so.
- **Doc edits travel with the code.** Each group fixes the specification clauses its own behaviour changes. There is no
  documentation group at the end.

## 1. Decisions and rules

- [ ] 1.1 Write the offline exception for the first-launch look into the rules that state the offline invariant, so the
  one new outbound call is a documented exception and not a defect.
  - Files: `AGENTS.md` (the "Opening a book never needs the network…" non-negotiable), `.claude/rules/offline-and-privacy.md`
    (the first MUST and the first Reject-if: once, `127.0.0.1:11434` and `127.0.0.1:1234` only, a listing, silent,
    ADR-0044), `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md` (NFR-OFFLINE-02/03/04 and
    `#verification`), and DD-01 in `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`.
  - Also the same sentence in `01_VISION_AND_SCOPE.md` (V1, SC-IN-2, SC-CRIT-3), `01_QUALITY_ATTRIBUTES.md`
    (NFR-PRIV-01), `01_SYSTEM_ARCHITECTURE.md` (the invariant and the diagram label), `06_IMPLEMENTATION_STAGES.md`
    (F9), and FR-INFER-01 / FR-INFER-01a.
  - Mark ADR-0010 "Amended by ADR-0044".
  - Verify: `grep -rn "user-triggered" docs AGENTS.md .claude/rules` shows each hit naming the exception; ArchUnit
    `no-http-in-core-except-llm` is unchanged. → docs
- [ ] 1.2 Bring the storage rules to ADR-0043, so a later reader does not plan Flyway migrations or entity tables
  before the release.
  - `.claude/rules/persistence-sqlite.md`: the "Current status" section; the Flyway MUST becomes "from the first
    release; until then one schema file and no migrations"; JSON documents in a few tables replace "dedicated entity
    tables"; keychain → environment variable only.
  - `.claude/rules/testing.md`: the smoke test's "no database today", "Flyway applied" → "the schema applied", and the
    `ui.language` line.
  - `openspec/config.yaml`: the stack line and every other Flyway or migration line (the persistence rule lines and the
    task rule "real temp SQLite + Flyway").
  - The specification clauses that state migrations as current:
    - FR-PERSIST-01/07/09/10/11 in `01_FUNCTIONAL_REQUIREMENTS.md#fr-persist` (FR-PERSIST-11's "refuse a newer
      schema" becomes the rename-aside rule; no `projects.provider_id` foreign key);
    - DD-20 in `04_DESIGN_DECISIONS.md`;
    - `10_DI_AND_LIFECYCLE.md` and `11_APP_ENVIRONMENT_AND_PATHS.md` ("run Flyway" → "create the schema");
    - `05_Dependencies/01_DEPENDENCIES.md` (Flyway later, jsr310 added) and `04_Build_and_Release/02_QUALITY_GATES.md`.
  - A one-line note in the proposal document's "Persistence" section: no Flyway before release (ADR-0043).
  - Mark ADR-0034 "Superseded for the window by ADR-0043", ADR-0009 "Amended by ADR-0043" and ADR-0036 "Superseded in
    part by ADR-0045".
  - Run `python3 scripts/sync-agent-files.py --apply`.
  - Verify: the sync script reports no drift, and `openspec validate finalize-ui-and-persistence --strict` is still
    clean. → docs

## 2. `:api` shapes and their in-memory implementers

- [ ] 2.1 Give a project a name, a fold key, a name origin and timestamps, and take its id from an injectable source,
  so one book can have several projects and names stay unique ignoring case in every script.
  - `Project` gains `name`, `nameKey` (`strip().toLowerCase(Locale.ROOT)`), `nameOrigin` (AUTO/USER), `createdAt` and
    `lastOpenedAt`.
  - `ProjectRepository` gains `all()`, `findByPair(contentHash, src, tgt)` and `findByNameKey(key)`.
  - Add `api.project.IdSource`. `TranslationJobImpl`'s UUIDs move behind it.
  - `InMemoryProjectRepository` follows.
  - Test: `ProjectTest` (`Земля` and `земля` give one key) and the contract test for the three finders.
  - Verify: `./gradlew build`. → :api, :persistence, :pipeline
- [ ] 2.2 Add the settings port, its typed key registry and its in-memory twin, so preferences have one owner that both
  adapters implement.
  - `api.persistence.SettingsRepository` (`get`, `put`, `all`) and `api.settings.AppSetting<T>` with every key in D12.
  - `persistence.memory.InMemorySettingsRepository`.
  - Test: `AppSettingTest` (defaults and parsing for each type) and a settings contract test over the in-memory
    adapter.
  - Verify: `./gradlew build`. → :api, :persistence
- [ ] 2.3 Add the per-model settings port and its records with an in-memory twin, so temperature, context, answer
  length, the output field, refusals and the measured speed live per provider + model.
  - `api.persistence.ModelSettingsRepository` and `api.llm.ModelSettings`, `TemperatureChoice` (CHOOSE / VALUE / OMIT),
    `OutputLengthField`, `RefusedSetting`.
  - `persistence.memory.InMemoryModelSettingsRepository`.
  - Test: the record tests and a contract test over the in-memory adapter.
  - Verify: `./gradlew build`. → :api, :persistence
- [ ] 2.4 Extend the provider record with the fields of the GoText-style form, and move its validation and in-memory
  binding out of `:llm`, so either persistence module can own the providers without a duplicate Guice binding.
  - `ProviderConfig` + `displayName`, `auth`, `apiKeyHeader`, `credentialRef`, `modelsPath`, `chatPath`, `headers`,
    `customModels`.
  - A pure `api.llm.ProviderConfigRules` holds:
    - the `/v1` warning;
    - no chat override for Ollama;
    - a header whose name contains `authorization`, `key`, `token` or `secret` must have a `${env:NAME}` value.
  - `ProviderConfigs` gains `save` and `delete`.
  - `InMemoryProviderConfigs` (the two presets) moves to `persistence.memory`, and its binding leaves
    `LlmModule:38`.
  - Test: `ProviderConfigRulesTest` (parameterised: `http://host:1234` warns, `X-Api-Key: sk-1` is refused,
    `X-Api-Key: ${env:LMS_KEY}` is accepted) and the moved `InMemoryProviderConfigsTest`.
  - Verify: `./gradlew build`. → :api, :llm, :persistence
- [ ] 2.5 Add the parsed batch items to the call snapshot, and the batch context sent to the context snapshot, so the
  live rows, the reply grid and "What the model saw" can show what really happened.
  - `api.pipeline.CallItem`, `CallItemStatus`, `CallSnapshot.items` (empty by default).
  - `ContextSnapshot.sentSections` (`@Nullable`).
  - Every constructor call site follows.
  - Test: `CallSnapshotTest` and `ContextSnapshotTest`.
  - Verify: `./gradlew build`. → :api, :pipeline, :ui
- [ ] 2.6 Add the two step events, the verification exchange and the typed run model, so the progress dialogs, the
  ledger, the test dialog and the AI-model questions on continue have their data.
  - `JobEvent.StepsPlanned(kind, added, estimated)` and `JobEvent.StepChanged(kind, label, index, state, itemRef,
    result)` with `api.pipeline.StepResult`, plus no-op cases in every exhaustive switch.
  - `StageOutcome.exchange` (`@Nullable VerificationExchange`).
  - `RunRecord.model` becomes a `ModelSelection`.
  - `api.project.ExportRecord`, stored and read through `ProjectRepository.recordExport(projectId, record)` and
    `lastExport(projectId)`.
  - `api.persistence.StorageInfo` (`location()`, `sizeBytes()`, `backupTo(Path)`) for Settings › Storage & logs, with an
    in-memory twin that reports no file.
  - `ExportProgress` gains the item fields.
  - Test: `JobEventContractsTest`, `StageOutcomeTest`, `RunRecordTest`.
  - Verify: `./gradlew clean build check spotlessCheck` (end of group). → :api, :pipeline, :ui, :persistence

## 3. ∥ Stored state in SQLite

- [ ] 3.1 Add the SQLite, JDBI and Jackson time libraries to `:persistence`, with exact catalog versions and locks, so
  the adapter code compiles on the module path.
  - `gradle/libs.versions.toml`: `sqlite-jdbc`, `jdbi3-core`, `jackson-datatype-jsr310` (Jackson 2.20.2's line),
    versions at least two weeks old.
  - `modules/persistence/build.gradle.kts` and `module-info.java` `requires`.
  - Run `./gradlew resolveAndLockAll --write-locks`.
  - The test is red first: `SqliteBootTest` opens a `@TempDir` database on the module path, which also loads the
    native library.
  - Verify: `./gradlew -PstrictLocks verifyLocks`, and the licence gate passes (all Apache-2.0). → :persistence
- [ ] 3.2 Build the database opener: per-connection pragmas, the one schema file, a single writer, and the pre-release
  rule that puts a mismatching file aside. Without these, a crash or a second connection can lose decisions or run
  without foreign keys.
  - `persistence.sqlite.SqliteDatabase`, `WriteExecutor`, and `src/main/resources/db/schema.sql` (D9 tables, cascades,
    `user_version`).
  - Tests:
    - `SqliteDatabaseTest.open_twoConnections_bothHaveForeignKeysAndBusyTimeout`;
    - `open_freshFile_createsEveryTable`;
    - `open_otherSchemaVersion_renamesFileAsideAndCreatesFresh` (the old file is named `bookloom.db.old-<time>`);
    - `open_unreadableFile_renamesAside`;
    - `deleteProject_cascadesToSegments`.
  - Storage failures are `ErrorCode.internal` with the details `storage <operation>`.
  - Log: INFO open and schema, WARN rename-aside.
  - Edit `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md` to the D9 schema and the pre-release rule, and fix
    its ADR link to `ADR-0009-persistence-sqlite.md`. → :persistence, docs
- [ ] 3.3 Add the JSON codec with mix-ins for the sealed types, so every stored record reads back equal while `:api`
  stays free of annotations.
  - `persistence.sqlite.JsonCodec`.
  - Test: `JsonCodecTest.roundTrip_storedRecord_readsBackEqual`, parameterised over one fixture of each stored record
    (`SegmentRecord` with findings and `ContextSnapshot.sentSections`, `GlossaryEntry` with aliases, `LexiconEntry`,
    `RollingSummary`, `Deferral`, `RunRecord`, `TmEntry`, `BookBrief`, `ProviderConfig`, `ModelSettings`,
    `ExportRecord`). → :persistence
- [ ] 3.4 Implement the existing storage ports over SQLite, and add the lexicon to the contract chain, so the window's
  translation state survives a restart with the same behaviour the in-memory adapters prove.
  - `Sqlite{Project,Segment,Glossary,Tm,Summary,Deferral,Run,Lexicon}Repository`.
  - `SqliteCheckpoint`: segments, TM, deferrals, glossary additions and the run record in one write transaction.
  - `LexiconRepository` joins `RepositoryContractTest`'s chain. `SqliteRepositoryContractTest` runs the whole chain on
    a temp file.
  - New contract cases: a Cyrillic name-case clash is refused; a checkpoint that fails part-way leaves no segment
    written.
  - Log: DEBUG per operation with ids and counts; TRACE for documents. → :persistence
- [ ] 3.5 Implement the settings, per-model settings and provider ports over SQLite, so preferences, providers and
  refusals survive a restart and a key never lands in the file.
  - `SqliteSettingsRepository`, `SqliteModelSettingsRepository`, `SqliteProviderConfigs`.
  - Contract tests over both adapters, including "a stored provider row contains `${env:LMS_KEY}` and never a key
    value". → :persistence
- [ ] 3.6 Split the persistence module into in-memory and SQLite variants and open the database at boot, so the window
  stores its state while the command line still keeps nothing.
  - `InMemoryPersistenceModule` and `SqlitePersistenceModule`, each binding every storage port, `ProviderConfigs`
    included.
  - `CoreModules` (the window) installs SQLite. `TranslateLauncher` installs in-memory.
  - `AppLifecycle` phase 2 opens the database at `AppPaths.databaseFile`.
  - Add a test helper `NotOnFxThread` that the SQLite adapters call in tests.
  - Tests: `PersistenceModuleTest` for both variants, and an `:app` smoke `RestartSmokeTest` with a temp
    `BOOKLOOM_DATA_DIR`: boot, create a project, boot again, `all()` lists it.
  - Verify: `./gradlew clean build check spotlessCheck`, and `./gradlew :app:run` creates `bookloom.db` in the dev
    data folder. → :persistence, :app

## 4. ∥ The provider and pipeline hooks the UI needs

- [ ] 4.1 Send the form's sign-in, headers and endpoint overrides, and check the key before any request, so a provider
  configured in Settings works and a missing key says which variable is missing.
  - `HttpExchange.buildRequest` resolves `${env:NAME}` and adds `Authorization: Bearer …` or the API-key header plus
    the custom headers.
  - Both clients use `modelsPath` / `chatPath`.
  - A missing variable gives `ErrorCode.missingCredential`, naming the variable and never a value, before any request.
  - Add `ProviderVerifier.verify(ProviderConfig, model, policy)` and `ModelCatalog.listModels(ProviderConfig)` for an
    unsaved form. `VerificationExchange` is filled with the key masked.
  - Tests: WireMock for Ollama (`/api/tags` at an overridden path) and OpenAI-compatible (`/v1/chat/completions` with
    a Bearer header and an `X-Org` header), a missing variable, and a log assertion that the key value never appears.
  - Edit FR-PROV-03/05/06 in `01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`, `04_LLM_PROVIDERS_AND_MODELS.md`
    (`#provider-kinds-and-presets`, FR-PROV-05a/b/c, `#three-stage-verification`, and the collapsed table at
    `#response-handling`), and `02_Architecture/04_LLM_INTEGRATION.md` (`#provider-config`,
    `#credentials-as-reference`). → :llm, docs
- [ ] 4.2 Apply the per-model settings to every request, so Let BookLoom choose keeps the tuned per-kind temperature,
  My value sends one value, and Don't send leaves `temperature` out. The context window, answer length and output
  field follow too.
  - `ChatRequests` and `JobModelCalls.send` read `ModelSettings` per chunk (cached).
  - `ContextBudget.windowFor(detected, override)` gets the override; `OutputLimit` caps the answer.
  - `OpenAiRequestMapper` writes `max_completion_tokens` when chosen, never for Ollama.
  - Tests: `ChatRequestsTest` (parameterised over CHOOSE / VALUE 0.7 / OMIT), `ContextBudgetTest`, and WireMock body
    assertions in both dialects (Ollama `options.temperature` absent under OMIT, OpenAI `max_completion_tokens: 512`).
  - Edit FR-MODEL-03/07 + 09–11 and DD-36/44 (temperature, context window). → :pipeline, :llm, docs
- [ ] 4.3 Recognise any refused optional setting, drop it, repeat once and remember the refusal, so a model that
  refuses `temperature` or `max_tokens` still translates and is not asked again.
  - `RejectedCapability` gains TEMPERATURE, TOP_P, TOP_K, MIN_P, REPEAT_PENALTY, SEED, OUTPUT_FIELD and NUM_CTX.
  - `CapabilityRejections` matches 400 or 422 bodies that name a sent setting.
  - `GatedChatModel` repeats without counting an attempt.
  - `llm.provider.RefusalMemory` (a singleton over `ModelSettingsRepository`) strips remembered settings before the
    first attempt and records `RefusedSetting(name, at, insteadDid)`.
  - Log: one WARN per refusal.
  - Tests:
    - a new `CapabilityRejectionsTest`, parameterised over bodies: `"Unsupported parameter: 'temperature'"` → TEMPERATURE;
      `"max_tokens is not supported, use max_completion_tokens"` → OUTPUT_FIELD;
      `"temperature is invalid"` when temperature was not sent → none;
    - WireMock in both dialects: refuse → repeat → the second request lacks it → the third request (a new call) lacks
      it from the start; a 400 naming nothing sent stays `ErrorCode.validation`.
  - Edit FR-INFER-09e into `01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`, and `09_ERROR_HANDLING.md` (a refused setting is
    not an error). → :llm, docs
- [ ] 4.4 Put a batch reply's parsed items on its call snapshot, so each live row shows its own translation or why it
  fell back.
  - `ModelCalls.parsed(callRef, items)` (a default no-op) is called in `BatchDrafter` right after
    `BatchReplyParser.parse`; `CallSnapshots.answered` attaches the items; `JobModelCalls` publishes again.
  - Items carry book text, so they are logged at TRACE only.
  - Test: `JobModelCallSnapshotsTest.parsed_fourItemBatchOneMissing_snapshotCarriesFourItemsOneMissing`. → :pipeline
- [ ] 4.5 Store, with each batch-adopted segment, the sections its batch actually sent, so the review desk's "What the
  model saw" is the real context.
  - `ChunkBatches` keeps segment → sections filled in `BatchStage.adopt`; `ChunkRunner.drafted` stores
    `context.snapshot().withSent(sections)`.
  - Tests: `BatchDraftingJobTest` asserts `sentSections` on a batch-adopted record and null on a single draft, and a
    `RetryDraft` test shows the replay text is unchanged. → :pipeline
- [ ] 4.6 Announce planned and changing steps from the run, the names pass and the brief suggestion, and the export
  items through `ExportProgress`, so every progress bar counts all the work of its operation.
  - **The run:** drafts + `reviewPasses` checks per translated segment at chunk entry, added steps at repair rounds,
    re-asks and timeout resends, and REVISE and summaries on Max.
  - **The names pass:** `NamesPassImpl.step` covers its 7 stages + write.
  - **The brief:** `BriefSuggester` covers book-knowledge (or skipped), K samples (3 / 4 / 5) with each one's
    value/agreement/quote, and synthesis.
  - **The export:** `ExportCalls` gives before/after/verdict per reread item.
  - Tests: per source, with hard-coded totals for a 12-segment fixture book (FAST: 12 planned; BALANCED: 12 drafts + 10
    checks for 10 translated + 2 kept; MAX adds REVISE and one summary per unit), plus a repair that adds 1 step.
  - Edit `05_PIPELINE_ENGINE.md` (batches at `#tiered-loop`, the step announcements, `#generation-parameters`,
    `#rolling-summary`) and FR-ALGO-02/09 to the batched drafts the code already makes. → :pipeline, docs
- [ ] 4.7 Make projects id-based, with names, open-by-id, rename, remove and "translate into another language", so the
  Projects screen can keep several translations of one book.
  - `ProjectService` drops `importBook(Path)` / `continueBook(Path)` / `decidedSegments(Path)` for `list`, `create`,
    `open`, `rename`, `remove` and `translateInto`.
  - `pipeline.project.ProjectNames`: the automatic name, following the languages, a pair conflict and a duplicate name
    as typed results.
  - `open`:
    - re-reads the book and checks its hash;
    - a stored RUNNING or PAUSED run counts as interrupted;
    - a missing or changed file returns `BookFileMissing` / `BookFileChanged`.
  - `translateInto` copies the brief, the Glossary and the Recurring terms with targets emptied, never the TM.
  - `remove` keeps the book file and the other projects.
  - The recurring terms' "Used" count comes from the book index's postings.
  - The existing `:ui` callers (`ImportController`, `ImportGuard`, `CurrentProject`, `ScriptedProjectService`) move to
    the id surface in the same task, opening the just-imported project, so the build stays green.
  - Tests: rewrite the `ProjectServiceImpl*Test`s; add `ProjectNamesTest`, parameterised:
    - `Earth Gravity` en→uk → `Earth Gravity · EN → UK`;
    - Auto source → `Earth Gravity · → UK`;
    - no target → `Earth Gravity · EN → …`;
    - a used pair → `PairInUse`;
    - `Earth Gravity · 26b` twice → `NameInUse`;
    - `Земля` vs `ЗЕМЛЯ` → `NameInUse`;
    - rename back to automatic refused while the pair is used.
  - Verify: `./gradlew build`. → :api, :pipeline, :ui
- [ ] 4.8 Read the reasoning, sampling and review mode from the stored settings while the environment variables and
  flags still override them; give Markdown and TXT a title; and give a named project's exports their own file names.
  - `ReasoningPlan`, `SamplingMode` and `ReviewModeResolver` take the stored value under the override.
  - `FileNameTargets` falls back to `aux:fm:title` / `aux:fm:author`.
  - `ExportPathRules` appends the named project's suffix.
  - Tests:
    - the resolver tests (env set wins; env unset → stored; nothing → default);
    - a Markdown front-matter title case;
    - two projects of one book and pair → `earth-gravity.uk.md` and `earth-gravity.uk.26b-max.md`.
  - Edit FR-REVIEW-01 and FR-EXPORT-04 in `01_FUNCTIONAL_REQUIREMENTS.md`. → :pipeline, :app, :ui
- [ ] 4.9 Prove a translation continues after a restart over the real SQLite store, so "Ready to continue" rests on a
  test and not on inspection. Depends on group 3.
  - Test: `RestartContinueE2eTest` (pipeline e2e, temp database, pseudo model):
    - run a 12-segment fixture, stop after 5 decisions, drop the injector, build a new one on the same file, open the
      project, and see 5 decided, 7 pending; continue to 12 decided with the same accepted and flagged counts;
    - a second case abandons the run mid-chunk without a stop, and the reopened project still continues at the first
      pending segment.
  - Edit FR-RESUME-02/05 and `05_RELIABILITY_AND_RESUME.md` (NFR-REL-03/04).
  - Verify: `./gradlew clean build check spotlessCheck`. → :pipeline, :persistence, docs

## 5. The window's shared infrastructure

- [ ] 5.1 Give dialog cards their three sizes and give the dialog host its Enter, Esc and queue rules, so every dialog
  fits the window, its footer stays put, and a dialog nobody asked for waits its turn.
  - `ModalCard.Size` (DIALOG 480, WIDE 720, XL min(1180, 92 %) × fixed min(820, 90 %)) with a fixed header and footer
    and a `ScrollPane` body.
  - `ModalHost`: Enter → the default button (Ctrl/⌘+Enter in a `TextArea`), Esc → the card's escape action,
    `showWhenFree`, and only About closes on an outside click.
  - The Stop dialog says "74 of 142 segments are done and saved. The 4 being translated now are translated next time.
    For a short break, use Pause."
  - Tests:
    - `ModalCardSizeTest` (at 960×640 the XL card is 883×576 and its footer y is unchanged after the body grows);
    - `ModalHostKeyQueueTest` (Enter runs the default; a queued card opens only after the open one closes).
  - Edit FR-UI-10 (new) in `01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`. → :ui, docs
- [ ] 5.2 Replace the raw prompt and reply panes with parsed views, so prompts show as System and User sections and a
  reply as one row per segment.
  - `control/PromptView` and `control/ReplyView` replace `PromptContextPane` and `ReplyPane`; raw text sits in
    collapsed blocks with Copy.
  - Tests:
    - `PromptViewTest` (parameterised per slot: `styleSheet` → bullets, `glossary` → a 3-column grid, `items` with
      `<s id="2">` → an Id | Segment | Text row);
    - `ReplyViewTest` (4 items, one MISSING, gives 4 rows with "missing").
  - Rewrite the `PromptContextPane` and `ContextSection` tests. → :ui
- [ ] 5.3 Turn the busy layer into the work layer that holds the progress dialog, and remove the separate OS window, so
  all model work shows its steps, figures and parsed input and output in the window.
  - `control/ProgressDialog` and `state/ProgressDialogModel` (D2) on `BusyHost`:
    - follow is turned off only by a person;
    - Continue in background for hideable work, through `ActivityChip` "open" and `HiddenWork`;
    - other model work is refused with a "Show progress" toast;
    - the "Model work is running" leave dialog (Stay · Continue in background) goes through `LeaveDialog` /
      `LeaveGuard`.
  - Delete `control/ModelCallsWindow`, `#busy-show-calls`, `ModelCallsWindowTest` and `BusyHostCallsWindowTest`. Cut
    `BusyHostTest` to what remains.
  - Tests: `ProgressDialogTest`:
    - a code selection keeps follow on, a click turns it off;
    - Esc hides hideable work and cancels short work;
    - hiding long work sets `HiddenWork` to the screen it names, and the activity chip shows "open" (the Names
      screen's own bindings are tested in 6.4).
  - Verify: `grep -rn "new Stage" modules/ui/src/main` shows only the startup-failure stage. → :ui
- [ ] 5.4 Replace the 20-segment time-left window with a ledger of all steps, so "52 % in 2 h" reads "about 2 h left"
  and checks and repairs count.
  - `state/ProgressLedger` (D3) is fed by `StepsPlanned` / `StepChanged` and `ExportProgress`. It replaces
    `RunClock.timeLeft` and `RunFigures.percent`.
  - Tests: `ProgressLedgerTest`:
    - 3 tasks × 3 items = 9 steps;
    - 52 % done in 2 h of uniform steps → "about 2 h";
    - adding 10 steps never lowers the bar;
    - hidden for the first 4 steps;
    - a jump over 10 % a minute is capped;
    - a 30 min pause is excluded.
  - Rewrite `RunClockTimingTest` and `RunFiguresTest`. → :ui
- [ ] 5.5 Move toasts to the bottom-right, route dialog feedback to the dialog's footer, and make scrollbars, links and
  the selected-segment bar visible, so nothing covers buttons and every state reads at the required contrast.
  - `ToastStack`:
    - `BOTTOM_RIGHT`, 16 px inset, 360 px wide, at most 4, newest at the bottom;
    - 5 s for success and info, 12 s for warning, error or a toast with an action;
    - ✕ on each;
    - while a dialog or visible work is open, the message goes to its footer line.
  - `theme.css`: one 12 px scrollbar rule (thumb `muted-2` on track `surface-2`, nav `nav-fg-muted` on `nav-bg-2`) and
    the eight regional copies aligned; links underlined in `-color-info-fg`; the 2 px `-color-text-strong` selected
    bar.
  - Tests: rewrite the toast tests (the fifth toast drops the oldest; a toast during an open dialog becomes a footer
    line); `ContrastTest` gains the thumb (≥ 3:1), links (≥ 4.5:1 on every surface, both themes) and the selected bar.
  - Edit FR-NOTIF-01, `09_THEMING.md` (palette scrollbars, FR-THEME-11 new) and `11_NOTIFICATIONS_AND_ERRORS.md` if a
    line still says bottom-centre. → :ui, docs
- [ ] 5.6 Add width classes, the overrun tooltip, the pinned banner slot, the fill-the-window opt-out and the content
  caps, so every screen lays out by its own width and fits from 960×640 to 1920×1080.
  - `control/LayoutClass` (pseudo-classes, 16 px hysteresis, no logging); `control/Overrun`.
  - `#shell-banners` with `state/ShellBanners`; the `fills-viewport` style class read by `AppShellView.showContent`.
  - The 1320 px and 680 px caps; the nav at 236/200 px.
  - Tests:
    - `LayoutClassTest` (1039 → compact, 1040 → regular, back to 1030 stays regular, 1023 → compact);
    - a new `LayoutFitTest` grown from `TitleBarFitTest` (960×640 and 1920×1080 × `en` and `uk`: no clipped toolbar or
      title-bar item, no dialog past the window);
    - `ScreenConformanceTest` banner cases moved to `#shell-banners`.
  - **Compare:** the empty shell at both sizes.
  - Edit FR-UI-11/12 (new). → :ui, docs
- [ ] 5.7 Add state markers with one colour map, read-only grids, and resizable term tables with a changed-row edge, so
  a long term is never cut by a chip and every state reads as an icon plus a word.
  - `control/StateMarker` (six meanings); the `read-only-grid` style class with a small factory.
  - `TermTables` → `CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN` with minimum widths, keeping the 46 px rows; the
    `:changed` row edge replaces `ChangedMarks`' chip.
  - Tests: `StateMarkerTest` (each meaning has its style class and Feather icon); rewrite `TermTableCard*` (resizing
    Term keeps the Translation column ≥ 170 px; a changed row has the `changed` pseudo-class and its term cell has no
    chip).
  - Edit FR-THEME-10 in `09_THEMING.md` to the six meanings. → :ui, docs
- [ ] 5.8 Drive the title bar, the toolbar run actions, the banners, the nav footer and the step locks from one
  run-state table, so all 16 states look the same everywhere and the steps stay locked until a project is open.
  - `state/RunStateView` (D8).
  - The title bar shows the project name, the state text and the AI-model chip.
  - The run actions move from `TranslatingView` into the toolbar through `StepFooter.claim()`.
  - `LeaveGuard` gains a dirty-form check.
  - The nav footer follows the chip's source ("No AI model found").
  - Tests: `RunStateMappingTest` (parameterised over the 16 README rows, with title text, chip meaning, toolbar ids and
    banner key); rewrite `StepAvailabilityTest` for the run-state rules. The no-project lock lands in 6.1, so the app
    keeps a way into Import until Projects exists.
  - **Compare:** the shell in Ready, Translating, Paused, Waiting for the AI model and Finished. → :ui
- [ ] 5.9 Add "Show technical details" as one property every Simple/Detailed view and Advanced fold reads, so plain
  words are the default and the technical lines appear in one place.
  - `state/TechnicalDetails` bound to `ui.technicalDetails`.
  - Test: `TechnicalDetailsTest` (on → a new progress dialog opens in Detailed and the Settings folds open).
  - Edit FR-UI-13/14 (new) and `07_SETTINGS.md` line 138 to README G12.
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, docs

## 6. Screens, in workflow order

- [ ] 6.1 Build the Projects screen, its dialogs and the first-launch look, so the way into the workflow is a list of
  translations grouped by book, or a first-launch page that says whether translation can start.
  - `screen/ProjectsView` and `state/ProjectsViewModel` over `ProjectService`:
    - search, the All / In progress / Finished filter, and "No project matches 'xyz'. [Clear search]";
    - book headers with ＋ Translate into another language;
    - project rows with a status chip, Continue/Open and ⋯ (Rename…, Show book file, Remove project…);
    - the empty state with How it works and the AI-model line.
  - `state/LocalModelProbe` (D14).
  - Dialogs: Same language pair; Rename (chips, a uniqueness error after the first edit, "Use the automatic name
    again"); Translate into another language; Remove project; Find the book…; the continue dialogs (AI model not
    answering / A different model is chosen).
  - `ViewNames.PROJECTS` gets its resource, and `StepAvailability` now locks steps 1–5 without an open project ("Steps
    unlock when a project is open").
  - Tests:
    - screen states (list, empty + found, empty + not found, search with no hits);
    - a recording fake per action;
    - `LocalModelProbeTest`: exactly `http://127.0.0.1:11434` and `http://127.0.0.1:1234/v1`, called once, never on a
      second launch;
    - fix `NavigatorTest`, `ViewNamesTest`, `ScreenConformanceCoverageTest`, and `StepAvailabilityTest` (no project →
      steps 1–5 locked; Projects and Settings open).
  - **Compare:** `go('projects')` with projects, the empty state with and without a model, and each dialog.
  - Edit the FR-PERSIST-01 project lines and FR-UI (Projects). → :ui, docs
- [ ] 6.2 Rebuild Import around the one-line verdict, the centred drop zone, the "About this book" fold and the
  three-pane text browser, so the page fits one screen and the book's text is browsed in one widget.
  - `ImportBookView` / `ImportCardView`:
    - one verdict line per `ImportState` (Two language marks worded per I5);
    - "No cover";
    - one `SplitPane` of `StructureBrowser` + `StructureSegmentsPane` + the Text pane (minimum widths 160 / 240 / 220,
      Text below in compact) with search, the kind filter and "Nothing matches 'xyz'.";
    - "Open another book?" with "Open as a new project" as primary;
    - "Already in projects".
  - Tests: rewrite the Structure/Import layout tests (at 960×640 the browser is ≥ 360 px high and the page needs no
    scroll at 1920×1080); verdict cases per state.
  - **Compare:** each verdict, and the browser at both sizes. → :ui
- [ ] 6.3 Make the Book Brief read-only while translating, lock its languages once started, add the project-name line,
  and move "Fill in from the book" into the short progress dialog, so the brief cannot change under a running
  translation and its suggestion shows each sample's evidence.
  - `BriefView` and its cards bind to `RunStateView`.
  - The languages lock after the first decision.
  - `BriefLanguagesCard` gains the name line with Rename…; a used pair opens Same language pair.
  - "✦ Fill in from the book" opens `ProgressDialog` (steps and "What it decides", "Nothing is written to the brief
    until you apply it.", Apply to the brief).
  - The estimate line shows only when measured.
  - Tests: read-only while RUNNING; languages locked after one decision; the dialog's steps for BALANCED (1 + 4 + 1);
    Apply writes through `BriefSaver`.
  - **Compare:** idle, translating, and the dialog in Simple and Detailed. → :ui
- [ ] 6.4 Give Names & style its purpose lines, empty states, "Used" column, wide-only side-by-side layout, read-only
  state under hidden work, and the names pass in the long progress dialog with Results, so the tables explain
  themselves and the pass shows exact progress.
  - `NamesStyleView` switches at 1440 px.
  - `RecurringTermsCard` gains the Used column.
  - The model actions are disabled while translating ("Pause the translation first — BookLoom sends one model request
    at a time").
  - `AutoNamesBar` opens `ProgressDialog` with the 8 steps; `ChangeResultsDialog` becomes its Results view (Changes /
    Steps tabs, Undo all, Show changed rows).
  - Scan / Review / Translate run the 2-step variant.
  - Tests: rewrite `ChangeResultsDialogTest`; at 1280×800 the cards stack; while the pass runs (hidden too) Start
    translation waits with "Wait until the names pass ends"; a hidden pass disables `+ Add`, Scan,
    Review, Translate, every Translation input and the Locked switches (README N-5).
  - **Compare:** at 1280 and 1920, the dialog, Results, hidden. → :ui
- [ ] 6.5 Rebuild Translating as a summary strip and a full-height live card, so the person sees time left, every
  segment of the call in flight with its own marker, and the scene summary without scrolling.
  - A new `screen/TranslatingSummaryStrip` keeps `TranslatingView` under 400 lines.
  - `LiveCallPanel`:
    - Live / Activity log tabs;
    - a Simple/Detailed list | detail `SplitPane` that never stacks;
    - per-row markers from the call state + `SegmentOutcomeNote` + `SegmentDrafted` + `CallSnapshot.items`;
    - `SEGMENTS_HEIGHT` removed.
  - `LiveCallState` keeps the chunk's rows and the last 50 calls.
  - Show each segment when translated, Follow again, the Scene summary line, and the help line (closable once per
    project, with a keep-awake-off variant).
  - All calls… opens the progress dialog.
  - Tests:
    - per-item rows (a 4-item batch with 1 MISSING → 3 "translated", 1 "translated again alone");
    - auto-show selects the first new row unless one was clicked;
    - the live card is ≥ 460 px high at 960×640;
    - the scene summary line says "Updated every 20 segments." under BALANCED;
    - rewrite `LiveCallPanel*Test`.
  - **Compare:** every run state from the Run states page, in Simple and Detailed. → :ui
- [ ] 6.6 Move review into the extra-large dialog, with row markers, protected formatting marks, one main button and
  an inline "Try again with a note", so the person can review with room and never breaks a formatting mark.
  - `ReviewPanel`, `ReviewListPane`, `ReviewComparePane`, `FindingRows`, `ReadableView` and `ContextSection` move into
    an XL `ModalCard`.
  - It opens from Review (n), from the strip, and by itself through `showWhenFree` when a run finishes with flags (the
    `automation.openReviewDesk` setting).
  - `control/ProtectedMarks` and `state/MarkStatus` (D7).
  - "✓ Accept & next" turns into "✓ Save my edit & next".
  - More ▾: Leave for later, Try again, Try again with a note… as an inline panel, Undo my edit (disabled when
    unedited).
  - Ctrl/⌘+Enter and Ctrl+Alt+↑/↓.
  - "What the model saw" reads `sentSections`; one label per finding; the duplicate label goes.
  - Delete `RetryWithNoteDialog`.
  - Tests: `ProtectedMarksTest`, parameterised:
    - backspace inside `⟦1⟧` removes all of it;
    - pasting `⟦1⟧⟦2⟧` when `⟦2⟧` is present inserts only `⟦1⟧`;
    - `setText` passes unchanged;
    - undo after a whole-mark delete restores it;
    - the main button is off while `⟦1⟧` is missing.
  - Rewrite the `ReviewPanel*` screen tests. The desk opens by itself after a COMPLETED run with 6 flagged, and not
    with 0. At 960×640 the translation editor and the mark strip are visible (README N-4).
  - Edit FR-REVIEW-03/05/09.
  - **Compare:** `openDialog('review')` at 960 and 1920, edited, and with a missing mark. → :ui, docs
- [ ] 6.7 Finish Export: the toolbar action and its reasons, partial export, tiles from the project, the title fallback,
  asking before a replace, and the file-name and last-check progress dialogs. The screen is then never empty, and
  never overwrites a file without asking.
  - `ExportView`:
    - ⬇ Export book is in the toolbar, disabled while translating and with nothing translated, each with its reason;
    - the partial banners; the last check is off and disabled for a partial export;
    - tiles from `SegmentRepository.countsByStatus` and `ExportRecord` (`ExportResult.reset()` no longer blanks
      them);
    - `ExportTitleCard` with the `aux:fm:*` fallback and a reason line;
    - the Replace switch is removed and a confirm asks before replacing.
  - "✦ Suggest a name" opens the short dialog; the last check opens the long dialog with Write the file now, the
    Cancel question above it, and Export complete.
  - Tests:
    - the tiles show "74 of 142" before any export;
    - an existing destination asks first and Cancel writes nothing;
    - a partial export has the last check disabled;
    - an item's Before | After grid reads "Unchanged — kept as it was";
    - rewrite the export screen tests.
  - Edit FR-EXPORT-04/06.
  - **Compare:** ready, paused, finished, and each dialog.
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, docs

## 7. Settings

- [ ] 7.1 Build Settings › Providers: the list | form split, the summary card, the Connection details fold around the
  full form, plain checks, the test dialog, Delete, Use this one, and the pinned save footer. A person can then add,
  test and keep a provider without leaving the window.
  - `SettingsController` / `SettingsViewModel`:
    - the list at 240/200 px; the form column capped at 860 px;
    - the form: name, kind, sign-in + key variable "set / not set", address with the `/v1` warning, the two endpoint
      overrides (inference disabled for Ollama with its reason), timeouts, model, custom headers, custom models;
    - checks through the draft verifier; the test dialog (verdict, request line with Copy as curl with the key
      hidden, Body | Metrics | Response, switching between the three tests, Close);
    - Delete… confirms; Use this one asks "Use LM Studio for the rest of this book?" while a translation exists;
    - "● Unsaved changes · Cancel · Save", and leaving asks Keep editing · Discard · Save.
  - `settings.fxml` enables Add, Edit and Delete.
  - Tests:
    - recording-fake saves;
    - the leave guard on navigation;
    - the test dialog fills from `VerificationExchange` with the key shown as `••••`;
    - at 960×640 Save is visible without scrolling (R4-S1).
  - Edit FR-SETTINGS-01 and the saving rule (new).
  - **Compare:** each Providers state and the test dialog. → :ui, docs
- [ ] 7.2 Build Settings › Models, so each model keeps its own temperature, context window, answer length and output
  field, and shows the settings it refused, with Send again.
  - The model list across providers; "Found out from the program" from `ModelCapabilities`; the three-way temperature
    with a slider for My value; Use context window (with `num_ctx` in grey under technical details); Limit answer
    length; the Advanced output field (disabled for Ollama); "Settings this model refused" with Send again or "Nothing
    refused"; the pinned footer.
  - Tests: Save writes `ModelSettings` with OMIT; Send again removes a `RefusedSetting`.
  - **Compare:** `setSet('models')`. → :ui
- [ ] 7.3 Build Generation and Appearance with only the knobs the code has, so nothing on them is fictional.
  - **Generation:** the read-only `DialParameters` grid; Thinking Off / Low / On; Sampling; the read-only time limits.
  - **Appearance:**
    - the stored theme;
    - the app language with "Applies after BookLoom restarts." and Restart now (releases the lock, starts the same
      command, exits);
    - Show technical details;
    - the Accent row removed.
  - `OsLocaleProvider` reads `ui.language` first.
  - Tests: the theme survives a new injector over the same store; `RestartCommandTest` builds the same command and
    arguments; the i18n first-start test also covers a stored `uk`.
  - Edit FR-SETTINGS-03/04, FR-UI-06 and DD-34.
  - **Compare:** both tabs. → :ui, :app, docs
- [ ] 7.4 Build Automation and Storage & logs, so the review mode, defaults, keep-awake, reopen-last-project and the
  database and log places are visible and changeable.
  - **Automation:** "When to stop and ask you", default quality, open the desk when a translation finishes, the
    last-check default, keep the computer awake, reopen the last project.
  - **Storage & logs:**
    - the database folder and size, Open folder, and Make a backup… (`StorageInfo.backupTo`: a `VACUUM INTO` through the write executor);
    - the log folder, 10 launches kept, the detailed-log switch, Open log folder, Save a diagnostic bundle…;
    - short paths with the full path in a tooltip.
  - Tests: each Automation switch writes its key; the backup file opens as a database with the same project count;
    the detailed-log switch writes the marker file `LoggingBootstrap` reads at the next launch (`BOOKLOOM_TRACE_FILE`
    still overrides), and says "after BookLoom restarts".
  - Edit FR-SETTINGS-05/06 and NFR-PRIV-04.
  - **Compare:** both tabs.
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, :persistence, docs

## 8. Words, in English and Ukrainian

- [ ] 8.1 Apply the README Words table to both bundles, so the main screens say "segment", "to review", "Try again
  now", "Skip these 4 segments" and "AI model", and keep chunk, call, tokens and attempts for Detailed views.
  - `messages_en.properties` and `messages_uk.properties`, with `MessageKey` entries added or removed together.
  - The segment tooltip on every segment count.
  - Ukrainian written for the EN source, with ICU plurals one/few/many/other and no gendered past tense for "you".
  - Test: the bundle parity and ICU validity tests stay green; a grep test asserts no `flagged`, `Retry now` or
    `chunk` remains in a main-screen key's EN value.
  - Edit `10_I18N_AND_ACCESSIBILITY.md` only if a line now disagrees. → :ui
- [ ] 8.2 Run every fit check in Ukrainian across the screens and dialogs, so the longer labels fit at 960×640.
  - `LayoutFitTest` covers every screen and every dialog size × `uk`.
  - Fix any overflow with the README's shorter labels.
  - Edit `06_TESTING_STRATEGY.md`: the 16 states, the new tests, the egress test for the first-launch look; Monocle
    and the retired `Proves:` / `traceCheck` lines removed.
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, docs

## 9. Gate and hand run

- [ ] 9.1 Bring the project's own documents up to date, so the next reader finds what was built.
  - `AGENTS.md` "Where it stands": Projects, the full Settings, SQLite storage, continue after restart, the first-launch
    look.
  - The `AGENTS.md` Modules table (`:persistence`: SQLite + JDBI; Flyway from the first release).
  - `docs/implementation_plan/CHANGE_BACKLOG.md`: this change done, `redesign-translation-pipeline` next.
  - `docs/implementation_plan/01_MODULE_INVENTORY.md`: the new packages `persistence.sqlite`, `ui` controls and state.
  - `IMPLEMENTED_REQUIREMENTS.md`: FR-THEME-4, FR-PROV-01, FR-EXPORT-04, FR-REVIEW-05.
  - `docs/specification/00_Foundation/05_SPEC_INDEX.md`: the new anchors.
  - Verify: every link resolves (`grep` the cited anchors). → docs
- [ ] 9.2 Run the whole gate from clean, so the change is proven across every module with no exemption.
  - `./gradlew clean build check spotlessCheck` green; paste its tail.
  - `./gradlew -PstrictLocks verifyLocks`.
  - The packaged-image launch smoke shows `resolve-all passed`. → all
- [ ] 9.3 Exercise the change in the running app by hand, so the Definition of Done's second half holds.
  - `./gradlew :app:run` with a fresh `BOOKLOOM_DATA_DIR`:
    1. first launch with Ollama running (found ✓), and again with a fresh folder and Ollama closed ("No AI model
       found");
    2. a project from an EPUB and a Markdown book in `.temporary_context/Books_Examples/`;
    3. the brief filled in from the book;
    4. the names pass hidden in the background, then Results;
    5. translate with the pseudo model; quit mid-run; relaunch → "Ready to continue" → Continue;
    6. finish → the desk opens by itself; accept one and edit one with a mark;
    7. export partial and full; Translate into another language;
    8. Settings: add a provider with a header `${env:…}`, test it, set Don't send on a model, delete the provider.
  - Then the final **Compare** of every screen and dialog against the mockup at 960×640 and 1920×1080, light and dark,
    with the differences listed in `.temporary_context/ui-evidence/final/`. → all
- [ ] 9.4 Hand the owner the checks only the owner can do.
  - One real laptop sleep during an e4b translation: the help-line sentence "after sleep, translating goes on by
    itself" ships only if it holds, otherwise it is reworded.
  - A review of the evidence folder.
  - Record the result in this task's line before checking it. → owner

## Workflow follow-up

- Archive the change with `/opsx:archive` after the owner's review and merge of `feature/finalize-ui-and-persistence`.
- Then propose `redesign-translation-pipeline` against the code as it is at that point.
