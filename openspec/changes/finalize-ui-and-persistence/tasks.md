# Tasks

These rules apply to every task. The design decisions (D1–D14) and hooks (K1–K10) are in `design.md`. The behaviour
of every screen, dialog and state is in this change's `specs/` (each task's **Satisfies** line names the requirement
headings) and, for layout and look, in `docs/specification/mockups/README.md`. Paths without a module prefix are under
`modules/<module>/src/main/java/ua/bookloom/<module>/`.

- **Size.** This change is larger than the ~10-task norm (`AGENTS.md#how-work-is-planned`) because the owner asked for
  it as one change that finishes the UI.
  - Each numbered group is a mergeable unit. The repository is green between groups: the last task of every group runs
    `./gradlew clean build check spotlessCheck` and pastes its tail.
  - A task that changes an `:api` shape runs `./gradlew build` itself.
  - **Order.** The groups run in order, 1 to 9, in the main checkout (worktrees made by the agent tool start from
    `master`, not from the feature branch). Each task may depend only on the repository and earlier tasks.
  - **`:app` files.** Group 3 edits `CoreModules`, `AppLifecycle` and `TranslateLauncher`; group 4 edits
    `app/cli/BookOpener` (4.7a), `AppModule` and `app.bootstrap.ReviewModeResolver` (4.8b). `IdSource`'s UUID binding
    goes in `PipelineModule` (2.1), and `RefusalMemory` is bound in `LlmModule` (4.3).
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
- **Doc edits travel with the code.** Each group fixes the specification clauses its own behaviour changes. Only the
  project's own documents (`AGENTS.md`, the module inventory, the requirement status list) wait for task 9.1.

## 1. Decisions and rules

- [x] 1.1 Write the offline exception for the first-launch look into the rules that state the offline invariant, so the
  one new outbound call is a documented exception and not a defect.
  - Files: `AGENTS.md` (the "Opening a book never needs the network…" non-negotiable), `.claude/rules/offline-and-privacy.md`
    (the first MUST and the first Reject-if: once, `127.0.0.1:11434` and `127.0.0.1:1234` only, a listing, silent,
    ADR-0044), `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md` (NFR-OFFLINE-02/03/04 and
    `#verification`), and DD-01 in `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`.
  - Also the same sentence in `01_VISION_AND_SCOPE.md` (V1, SC-IN-2, SC-CRIT-3), `01_QUALITY_ATTRIBUTES.md`
    (NFR-PRIV-01), `01_SYSTEM_ARCHITECTURE.md` (the invariant and the diagram label), `06_IMPLEMENTATION_STAGES.md`
    (F9), and FR-INFER-01 / FR-INFER-01a.
  - Also `AGENTS.md:114` and `openspec/config.yaml` (the offline invariant in `context` and the `apply` guidance
    line), and `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md:331` ("exception not yet written").
  - Mark ADR-0010 "Amended by ADR-0044".
  - Verify: `grep -rn "user-triggered" docs AGENTS.md .claude openspec/config.yaml .agents` shows each hit naming the
    exception; ArchUnit `no-http-in-core-except-llm` is unchanged. → docs
- [x] 1.2 Bring the storage rules to ADR-0043, so a later reader does not plan Flyway migrations or entity tables
  before the release.
  - `.claude/rules/persistence-sqlite.md`: the "Current status" section; the Flyway MUST becomes "from the first
    release; until then one schema file and no migrations"; JSON documents in a few tables replace "dedicated entity
    tables"; keychain → environment variable only.
  - `.claude/rules/testing.md` (lines 3, 5, 26, 44): the smoke test's "no database today", "Flyway applied" → "the
    schema applied", and the `ui.language` line.
  - `AGENTS.md:11` ("SQLite/JDBI/Flyway planned") and the Modules table line for `:persistence`.
  - `.claude/agents/tester.md:23`, `.agents/skills/testing-javafx/SKILL.md` (lines 44, 87, 204) and
    `.agents/skills/project-navigator/SKILL.md` (lines 38, 88).
  - `docs/adr/README.md:32`, `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md` (lines 154, 165, 238),
    `01_SYSTEM_ARCHITECTURE.md:93` (the Flyway label), `06_IMPLEMENTATION_STAGES.md:54`,
    `05_Dependencies/03_LICENSING.md:52`, `docs/implementation_plan/01_MODULE_INVENTORY.md` (lines 224, 401, 408) and
    the `add-local-storage` row of `CHANGE_BACKLOG.md` (line 581).
  - `openspec/config.yaml`: the stack line and every other Flyway or migration line (the persistence rule lines and the
    task rule "real temp SQLite + Flyway").
  - The specification clauses that state migrations as current:
    - FR-PERSIST-01/07/09/10/11 in `01_FUNCTIONAL_REQUIREMENTS.md#fr-persist` (FR-PERSIST-11's "refuse a newer
      schema" becomes the rename-aside rule; no `projects.provider_id` foreign key);
    - DD-20 in `04_DESIGN_DECISIONS.md`;
    - `10_DI_AND_LIFECYCLE.md` and `11_APP_ENVIRONMENT_AND_PATHS.md` ("run Flyway" → "create the schema");
    - `05_Dependencies/01_DEPENDENCIES.md` (Flyway later, jsr310 added) and `04_Build_and_Release/02_QUALITY_GATES.md`.
  - A one-line note in the proposal document's "Persistence" section: no Flyway before release (ADR-0043).
  - Mark ADR-0034 "Superseded for the window by ADR-0043" (and its lines 15 and 56), ADR-0009 "Amended by ADR-0043"
    and ADR-0036 "Superseded in part by ADR-0045".
  - Keychain mentions stay: a keychain reference is still allowed later; this change only does not build it.
  - Run `python3 scripts/sync-agent-files.py --apply` (it also refreshes the `.codex` mirrors).
  - Verify: the sync script reports no drift; `grep -rn "Flyway" AGENTS.md .claude .agents openspec/config.yaml docs`
    shows only "from the first release" wording; `openspec validate finalize-ui-and-persistence --strict` is still
    clean. → docs

## 2. `:api` shapes and their in-memory implementers

- [x] 2.1 Give a project a name, a fold key, a name origin and timestamps, and take its id from an injectable source,
  so one book can have several projects and names stay unique ignoring case in every script.
  - **Satisfies:** local-storage "Keep project names unique within one book, ignoring letter case"; app-shell "Allow one
    project per language pair of a book unless it is named"; app-shell "Refuse a project name already used by the same
    book"; local-storage "Accept a found book only when its content matches"; local-storage "Store a book once and reuse
    it by its content"
  - `Project` gains `name`, `nameKey` (`strip().toLowerCase(Locale.ROOT)`), `nameOrigin` (AUTO/USER), `createdAt` and
    `lastOpenedAt`.
  - `ProjectRepository` gains `all()` and `findByPair(contentHash, src, tgt)`.
  - Add `api.project.IdSource`. `TranslationJobImpl`'s one UUID (`jobId`, built in `TranslationEngineImpl:109`) moves
    behind it, and `PipelineModule` binds the UUID source in this task so the injector still builds.
  - `ProjectRepository`'s name finder is `findByNameKey(contentHash, key)`: a name is unique within its book (owner,
    2026-10-20). Add `relocateBook(contentHash, path)` for "Find the book…".
  - `InMemoryProjectRepository` follows.
  - Test: `ProjectTest` (`Земля` and `земля` give one key) and contract cases for the finders: two books may hold the
    same name; one book may not.
  - Verify: `./gradlew build`. → :api, :persistence, :pipeline
- [x] 2.2 Add the settings port, its typed key registry and its in-memory twin, so preferences have one owner that both
  adapters implement.
  - **Satisfies:** local-storage "Keep the application's work across a restart"; settings "Apply the other tabs at once
    and say when"
  - `api.persistence.SettingsRepository` (`get`, `put`, `all`) and `api.settings.AppSetting<T>` with every key in D12.
  - `persistence.memory.InMemorySettingsRepository`, bound in `PersistenceModule`; `api.settings` exported in
    `api/module-info.java`.
  - Test: `AppSettingTest` (defaults and parsing for each type) and a settings contract test over the in-memory
    adapter.
  - Verify: `./gradlew build`. → :api, :persistence
- [x] 2.3 Add the per-model settings port and its records with an in-memory twin, so temperature, context, answer
  length, the output field, refusals and the measured speed live per provider + model.
  - **Satisfies:** settings "Describe each model on the Models tab and keep its own settings"; settings "Choose how the
    temperature is sent for a model"; settings "Set a model's context window and answer length"; settings "List the
    settings a model refused and let the person send them again"; inference "Remember a refused setting for the provider
    and model"; settings "Store the speed the inference test measured on the model"
  - `api.persistence.ModelSettingsRepository` and `api.llm.ModelSettings`, `TemperatureChoice` (CHOOSE / VALUE / OMIT),
    `OutputLengthField`, `RefusedSetting`.
  - `persistence.memory.InMemoryModelSettingsRepository`, bound in `PersistenceModule`.
  - `ChatRequest` gains `@Nullable OutputLengthField outputField` (null = `max_tokens`, as today), so the request can
    carry the model's output-length field to the mapper (design K5); every constructor call site follows.
  - Test: the record tests and a contract test over the in-memory adapter.
  - Verify: `./gradlew build`. → :api, :persistence
- [x] 2.4 Extend the provider record with the fields of the GoText-style form, and move its validation and in-memory
  binding out of `:llm`, so either persistence module can own the providers without a duplicate Guice binding.
  - **Satisfies:** llm-provider "Describe a provider by kind, endpoint and timeouts"; llm-provider "Describe how a
    provider signs in"; llm-provider "Describe a provider's overrides, headers and custom models"; llm-provider "Offer
    the two local presets and accept registrations"; local-storage "Require a credential-like header to name an
    environment variable"; local-storage "Store a credential never, only the name of an environment variable"; settings
    "Describe a provider's connection in a form"
  - `ProviderConfig` + `displayName`, `auth`, `apiKeyHeader`, `credentialRef`, `modelsPath`, `chatPath`, `headers`,
    `customModels`.
  - A pure `api.llm.ProviderConfigRules` holds:
    - the `/v1` warning;
    - no chat override for Ollama;
    - a header whose name contains `authorization`, `key`, `token` or `secret` must have a `${env:NAME}` value.
  - `ProviderConfigs.register` becomes `save` (insert or replace; its one caller is `app/cli/ProviderSetup:55`) and
    `delete` is added.
  - `InMemoryProviderConfigs` (the two presets) moves to `persistence.memory`; its binding leaves `LlmModule:38` and
    goes to `PersistenceModule`.
  - `:llm` cannot see `:persistence`: add a test-source fake `ProviderConfigs` and `ModelSettingsRepository` in `:llm`
    (e.g. `llm/testing/FakeProviderConfigs`) plus a small test Guice module, and switch the nine `:llm` tests that use
    `InMemoryProviderConfigs` (`ProviderVerifierImplTest:46`, `ProviderVerifierGateTest:49`,
    `ProviderVerifierPolicyTest:44`, `ChatModelFactoryImplTest:64`, `ModelCatalogServiceTest:40`,
    `ModelCapabilityServiceTest:35`, `ModelCatalogLiveTest:27`, …) and every injector built from `LlmModule` alone
    (`ModelCatalogServiceTest:120`, `ChatModelFactoryImplTest:261`, `ProviderVerifierImplTest:320`, `HttpClientsTest:44`,
    `DtoSerializationTest:221`) to it. The fake serves `:llm` tests only (there are no test fixtures shared across
    modules).
  - `:pipeline` depends on `:persistence`: its injectors built from `LlmModule` alone (`WholeBookPipelineLiveTest:84`,
    `QualityCallsLiveTest:174`, `eval/PromptEvalTest:153`, `WireMockProvider:80`) add `new PersistenceModule()`, and
    their `.register(` calls (`WireMockProvider:83`, `WholeBookPipelineLiveTest:87`, `QualityCallsLiveTest:183`,
    `PromptEvalTest:161`) become `.save(`.
  - Test: `ProviderConfigRulesTest` (parameterised: `http://host:1234` warns, `X-Api-Key: sk-1` is refused,
    `X-Api-Key: ${env:LMS_KEY}` is accepted, `X-Monkey: abc` is refused because the check is a case-insensitive
    substring) and the moved `InMemoryProviderConfigsTest`.
  - Verify: `./gradlew build`. → :api, :llm, :persistence
- [x] 2.5 Add the parsed batch items to the call snapshot, and the batch context sent to the context snapshot, so the
  live rows, the reply grid and "What the model saw" can show what really happened.
  - **Satisfies:** review-queue "Show on "What the model saw" the context the model was actually sent";
    translation-pipeline "Send a batch reply's per-segment results to the rows"; translation-pipeline "Announce each
    batch item's result"
  - `api.pipeline.CallItem`, `CallItemStatus`, `CallSnapshot.items` (empty by default) and `CallSnapshot.withItems`.
  - `ContextSnapshot.sentSections` (`@Nullable`).
  - Every constructor call site follows.
  - Test: `CallSnapshotTest` and `ContextSnapshotTest`.
  - Verify: `./gradlew build`. → :api, :pipeline, :ui
- [x] 2.6 Add the two step events, the verification exchange and the typed run model, so the progress dialogs, the
  ledger, the test dialog and the AI-model questions on continue have their data.
  - **Satisfies:** translation-pipeline "Announce the planned steps and each step's state"; translation-pipeline "Plan a
    run's steps and count the time spent"; resume "Record whether a run is running, paused or ended"; resume "Ask when
    the stored AI model does not answer"; resume "Ask which model to use when a different one is chosen"; export "Show
    the tiles from the project before any export"; settings "Show storage and logs on one tab"; local-storage "Make a
    backup copy on request"; settings "Apply the app language after a restart and the review mode at the next run
    start"; settings "Show a check's details in a test dialog"
  - `JobEvent.StepsPlanned(kind, added, estimated)` and `JobEvent.StepChanged(kind, label, index, state, itemRef,
    result)` with `api.pipeline.StepResult`, plus no-op cases in every exhaustive switch.
  - `StageOutcome.exchange` (`@Nullable VerificationExchange`).
  - `RunRecord.model` becomes a `ModelSelection`.
  - `api.project.ExportRecord`, stored and read through `ProjectRepository.recordExport(projectId, record)` and
    `lastExport(projectId)`.
  - `api.persistence.StorageInfo` (`location()`, `sizeBytes()`, `backupTo(Path)`) for Settings › Storage & logs, with an
    in-memory twin that reports no file.
  - `api.AppRestart` (one method, `restart()`), with a no-op binding until task 7.3 gives `:app` the real one.
  - `ExportProgress` gains the item fields.
  - Test: `JobEventContractsTest`, `StageOutcomeTest`, `RunRecordTest`.
  - Verify: `./gradlew clean build check spotlessCheck` (end of group). → :api, :pipeline, :ui, :persistence

## 3. Stored state in SQLite

- [x] 3.1 Add the SQLite, JDBI and Jackson time libraries to `:persistence`, with exact catalog versions and locks, so
  the adapter code compiles on the module path.
  - **Satisfies:** enabling work for 3.2, 3.3, 3.4 and 3.5
  - `gradle/libs.versions.toml`: `sqlite-jdbc`, `jdbi3-core`, `jackson-datatype-jsr310` (Jackson 2.20.2's line),
    versions at least two weeks old.
  - `modules/persistence/build.gradle.kts` and `module-info.java` `requires`.
  - Run `./gradlew resolveAndLockAll --write-locks` (JDBI brings `geantyref`; check its lock entry and licence too).
  - The test is red first: `SqliteBootTest` opens a `@TempDir` database on the module path, which also loads the
    native library.
  - Verify: `./gradlew -PstrictLocks verifyLocks` and `./gradlew checkLicense` (all Apache-2.0). → :persistence
- [x] 3.2 Build the database opener: per-connection pragmas, the one schema file, a single writer, and the pre-release
  rule that puts a mismatching file aside. Without these, a crash or a second connection can lose decisions or run
  without foreign keys.
  - **Satisfies:** local-storage "Set aside a database that cannot be used and start a fresh one"; local-storage "Keep
    the database in the per-user data folder under a single running instance"; local-storage "Never freeze the window on
    storage work"; local-storage "Keep project names unique within one book, ignoring letter case"
  - `persistence.sqlite.SqliteDatabase`, `WriteExecutor`, and `src/main/resources/db/schema.sql` (D9 tables, cascades,
    `UNIQUE(content_hash, name_key)`, `user_version`).
  - The thread check (D9): a read or write on "JavaFX Application Thread" writes one WARN line with the stack, and
    throws when `bookloom.storage.failOnFxThread=true`; set that property for every test task in
    `bookloom.java-conventions.gradle.kts`, next to `BOOKLOOM_REDUCE_MOTION`.
  - Tests:
    - `SqliteDatabaseTest.open_twoConnections_bothHaveForeignKeysAndBusyTimeout`;
    - `open_freshFile_createsEveryTable`;
    - `open_otherSchemaVersion_renamesFileAsideAndCreatesFresh` (the old file is named `bookloom.db.old-<time>`);
    - `open_unreadableFile_renamesAside`;
    - `deleteProject_cascadesToSegments`;
    - `write_onFxNamedThread_throwsWhenPropertySet` (a plain thread named "JavaFX Application Thread").
  - Storage failures are `ErrorCode.internal` with the details `storage <operation>`.
  - Log: INFO open and schema, WARN rename-aside.
  - Edit `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md` to the D9 schema and the pre-release rule, and fix
    its ADR link to `ADR-0009-persistence-sqlite.md`. → :persistence, docs
- [x] 3.3 Add the JSON codec with mix-ins for the sealed types, so every stored record reads back equal while `:api`
  stays free of annotations.
  - **Satisfies:** local-storage "Keep the application's work across a restart"
  - `persistence.sqlite.JsonCodec` with `MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS` and
    `OVERRIDE_PUBLIC_ACCESS_MODIFIERS` off, so `:api` needs no `opens` (D9); `:persistence` `requires
    com.fasterxml.jackson.databind`.
  - Test, run on the module path: `JsonCodecTest.roundTrip_storedRecord_readsBackEqual`, parameterised over one fixture of each stored record
    (`SegmentRecord` with findings and `ContextSnapshot.sentSections`, `GlossaryEntry` with aliases, `LexiconEntry`,
    `RollingSummary`, `Deferral`, `RunRecord`, `TmEntry`, `BookBrief`, `ProviderConfig`, `ModelSettings`,
    `ExportRecord`). → :persistence
- [ ] 3.4 Implement the existing storage ports over SQLite, and add the lexicon to the contract chain, so the window's
  translation state survives a restart with the same behaviour the in-memory adapters prove.
  - **Satisfies:** local-storage "Keep the application's work across a restart"; local-storage "Write a chunk's
    decisions together"; local-storage "Remove a project's saved work and nothing else"; local-storage "Delete the
    stored book with its last project"; local-storage "Store a book once and reuse it by its content"; local-storage
    "Accept a found book only when its content matches"; local-storage "Keep project names unique within one book,
    ignoring letter case"; glossary "Apply glossary edits from the next chunk and keep the glossary across a restart";
    glossary "Keep a stored lexicon of recurring terms and show it on Names & style"
  - `Sqlite{Project,Segment,Glossary,Tm,Summary,Deferral,Run,Lexicon}Repository`.
  - `SqliteCheckpoint`: segments, TM, deferrals, glossary additions and the run record in one write transaction.
  - `LexiconRepository` joins `RepositoryContractTest`'s chain. `SqliteRepositoryContractTest` runs the whole chain on
    a temp file.
  - New contract cases:
    - a Cyrillic name-case clash inside one book is refused, and the same name in another book is accepted;
    - a checkpoint that fails part-way leaves no segment written;
    - removing a book's last project removes its stored book row, and removing one of two keeps it;
    - the same bytes under another file name resolve to the same book;
    - `relocateBook` changes the stored path;
    - a person's glossary edit and a checkpoint's glossary addition made one after the other both survive.
  - Log: DEBUG per operation with ids and counts; TRACE for documents. → :persistence
- [ ] 3.5 Implement the settings, per-model settings and provider ports over SQLite, so preferences, providers and
  refusals survive a restart and a key never lands in the file.
  - **Satisfies:** local-storage "Keep the application's work across a restart"; local-storage "Store a credential
    never, only the name of an environment variable"; local-storage "Require a credential-like header to name an
    environment variable"; llm-provider "Keep registrations across a restart in the window only"; inference "Remember a
    refused setting for the provider and model"
  - `SqliteSettingsRepository`, `SqliteModelSettingsRepository`, `SqliteProviderConfigs` (starts empty; the presets
    stay in the in-memory adapter for the command line, D13), and `SqliteStorageInfo` (`location`, `sizeBytes`;
    `backupTo` returns a not-yet-available error until 7.4).
  - Contract tests over both adapters, including "a stored provider row contains `${env:LMS_KEY}` and never a key
    value", and "a fresh SQLite provider list is empty". → :persistence
- [ ] 3.6 Split the persistence module into in-memory and SQLite variants and open the database at boot, so the window
  stores its state while the command line still keeps nothing.
  - **Satisfies:** local-storage "Keep nothing on disk from the command line"; local-storage "Keep the database in the
    per-user data folder under a single running instance"; local-storage "Keep the application's work across a restart";
    llm-provider "Keep registrations across a restart in the window only"; llm-provider "Offer the two local presets and
    accept registrations"
  - `InMemoryPersistenceModule` and `SqlitePersistenceModule`, each binding every storage port, `ProviderConfigs`
    included.
  - `CoreModules` takes the persistence module as a constructor argument (it is shared with `TranslateLauncher:109`):
    the window passes `SqlitePersistenceModule`, `TranslateLauncher` passes `InMemoryPersistenceModule`.
  - `AppLifecycle` phase 2 opens the database at `AppPaths.databaseFile` (add it to `AppPaths` if absent).
  - Tests: `PersistenceModuleTest` for both variants, and an `:app` smoke `RestartSmokeTest` with a temp
    `BOOKLOOM_DATA_DIR`: boot, save a project through `ProjectRepository`, boot again, `all()` lists it; a CLI injector
    over the same folder writes no `bookloom.db`.
  - Verify: `./gradlew clean build check spotlessCheck`, and `./gradlew :app:run` creates `bookloom.db` in the dev
    data folder. → :persistence, :app

## 4. The provider and pipeline hooks the UI needs

- [ ] 4.1 Send the form's sign-in, headers and endpoint overrides, and check the key before any request, so a provider
  configured in Settings works and a missing key says which variable is missing.
  - **Satisfies:** llm-provider "Add the provider's headers and its key to every request"; llm-provider "Refuse a call
    whose key is missing before sending anything"; llm-provider "Never show the key in a log line or in the test
    dialog"; llm-provider "Use a provider's endpoint overrides"; llm-provider "Verify a provider description that is not
    saved"; llm-provider "Fall back to a plain probe when structured output is not confirmed"; llm-provider "Discover
    the models a provider offers"; llm-provider "Probe a provider's reachability without listing or inference";
    llm-provider "Verify a provider and a model in three stages"; llm-provider "Shape an Ollama-native chat request";
    llm-provider "Shape an OpenAI-compatible chat request"
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
  - **Satisfies:** inference "Send each call kind at its own temperature"; inference "Apply a model's own settings to
    the segments not translated yet"; llm-provider "Cap a chat call's output where the request states a cap";
    llm-provider "Shape an Ollama-native chat request"; llm-provider "Shape an OpenAI-compatible chat request"; settings
    "Choose how the temperature is sent for a model"; settings "Set a model's context window and answer length"
  - Design K5: the settings are applied in `:llm` where the bound model sends (`GatedChatModel`), through a
    `llm.provider.ModelSettingsCache` over `ModelSettingsRepository` refreshed on every save, so the 16
    `ChatRequests.build` callers stay as they are. CHOOSE keeps the request's per-kind temperature, VALUE replaces it,
    OMIT sends null; the answer cap lowers `maxOutputTokens`; the model's field sets `ChatRequest.outputField` (2.3).
    These apply from the next request. `ChatModelFactoryImpl:74` passes the cache into the `GatedChatModel` it builds.
  - `OpenAiRequestMapper` writes `max_completion_tokens` instead of `max_tokens` when `outputField` says so, never for
    Ollama.
  - The context window is not changed in `:llm` (a chunk is packed for its window): the three
    `ContextBudget.windowFor(detected, override)` call sites (`TranslationJobImpl:292`, `setup/BookSamples:78`,
    `glossary/NamesPassImpl:134`) read the stored override at chunk entry, and the request carries it through
    `JobModelCalls.send`'s sizing; a save applies from the next chunk.
  - Tests: `GatedChatModel` settings cases (parameterised over CHOOSE / VALUE 0.7 / OMIT), `ContextBudgetTest` with an
    override, WireMock body assertions in both dialects (Ollama `options.temperature` absent under OMIT and
    `options.num_ctx` 8192 under an 8192 override; OpenAI `max_completion_tokens: 512`); a temperature save between two
    calls changes the second request only; a context save during chunk 7 reaches chunk 8 and not the rest of chunk 7.
  - Edit FR-MODEL-03/07 in `01_FUNCTIONAL_REQUIREMENTS.md#fr-model` and DD-36/44 (temperature, context window).
    → :llm, :pipeline, docs
- [ ] 4.3 Recognise any refused optional setting, drop it, repeat once and remember the refusal, so a model that
  refuses `temperature` or `max_tokens` still translates and is not asked again.
  - **Satisfies:** inference "Recognise a refused setting only when the request sent it"; inference "Keep everything
    else when a setting is dropped, and do not count the repeat as an attempt"; inference "Remember a refused setting
    for the provider and model"; inference "Say a refusal once, quietly"; inference "Request provider controls without
    trusting them as guarantees"; llm-provider "Retry a retryable failure a bounded number of times"; llm-provider
    "Classify a reply that says no model is loaded as modelUnavailable"; settings "List the settings a model refused and
    let the person send them again"
  - `RejectedCapability` gains TEMPERATURE, TOP_P, TOP_K, MIN_P, REPEAT_PENALTY, SEED, OUTPUT_FIELD and NUM_CTX.
  - `CapabilityRejections` matches 400 or 422 bodies that name a sent setting.
  - `GatedChatModel` repeats without counting an attempt.
  - `llm.provider.RefusalMemory` (a singleton over `ModelSettingsRepository`, sharing 4.2's cache, bound in
    `LlmModule`) strips remembered settings before the first attempt and records `RefusedSetting(name, at,
    insteadDid)`. The test module from 2.4 binds the fake `ModelSettingsRepository` for injectors built from
    `LlmModule` alone.
  - Log: one WARN per refusal.
  - Tests:
    - a new `CapabilityRejectionsTest`, parameterised over bodies: `"Unsupported parameter: 'temperature'"` → TEMPERATURE;
      `"max_tokens is not supported, use max_completion_tokens"` → OUTPUT_FIELD;
      `"temperature is invalid"` when temperature was not sent → none;
    - WireMock in both dialects: refuse → repeat → the second request lacks it → the third request (a new call) lacks
      it from the start; a 400 naming nothing sent stays `ErrorCode.validation`.
  - FR-INFER-09e already exists in `04_LLM_PROVIDERS_AND_MODELS.md:155`: widen it to every optional setting and 422,
    add a pointer from `01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`, and edit `09_ERROR_HANDLING.md` (a refused setting is
    not an error). → :llm, docs
- [ ] 4.4 Put a batch reply's parsed items on its call snapshot, so each live row shows its own translation or why it
  fell back.
  - **Satisfies:** translation-pipeline "Send a batch reply's per-segment results to the rows"; translation-pipeline
    "Announce each batch item's result"
  - Design K1: `ModelCalls.parsed(List<String> segmentIds, items)` (a default no-op) is called in `BatchDrafter` right
    after `parser.parse` (`BatchDrafter.java:164–177`). `JobModelCalls` forwards it to `CallSnapshots`, which finds the
    call through its `draftedBy` map (as `noted` does, `CallSnapshots.java:73–87, 182–184`) and publishes
    `call.withItems(items)`.
  - Items carry book text, so they are logged at TRACE only.
  - Test: `JobModelCallSnapshotsTest.parsed_fourItemBatchOneMissing_snapshotCarriesFourItemsOneMissing`. → :pipeline
- [ ] 4.5 Store, with each batch-adopted segment, the sections its batch actually sent, so the review desk's "What the
  model saw" is the real context.
  - **Satisfies:** review-queue "Show on "What the model saw" the context the model was actually sent"
  - `ChunkBatches` keeps segment → sections filled in `BatchStage.adopt`; `ChunkRunner.drafted` stores
    `context.snapshot().withSent(sections)`.
  - Tests: `BatchDraftingJobTest` asserts `sentSections` on a batch-adopted record and null on a single draft, and a
    `RetryDraft` test shows the replay text is unchanged. → :pipeline
- [ ] 4.6 Announce planned and changing steps from the run, the names pass and the brief suggestion, and the export
  items through `ExportProgress`, so every progress bar counts all the work of its operation.
  - **Satisfies:** translation-pipeline "Plan a run's steps and count the time spent"; translation-pipeline "Announce
    the planned steps and each step's state"; translation-pipeline "Map the quality dial to the run's mechanics";
    app-shell "Show progress as one percentage of steps that never moves back"; glossary "List the eight steps of the
    names pass in its progress dialog"; book-brief "Show the brief suggestion in a progress dialog that is not
    hideable"; export "Show the last check as a progress dialog the person can leave or end"; export "Show each reread
    item next to its neighbours with a before and an after"
  - **The run:** at chunk entry, one draft step per segment of the chunk (a verbatim segment is done at once with no
    call) and `reviewPasses` check steps per chunk (`DialParameters`: Fast 0, Balanced 1, Max 2); added steps at
    repair rounds, re-asks and timeout resends, and REVISE and summaries on Max.
  - **The names pass:** `NamesPassImpl.step` covers its 7 stages + write.
  - **The brief:** `BriefSuggester` covers book-knowledge (or skipped), K samples (3 / 4 / 5) with each one's
    value/agreement/quote, and synthesis.
  - **The export:** `ExportCalls` gives before/after/verdict per reread item.
  - Tests: per source, on a 12-segment one-unit book built with `pipeline/src/test/java/ua/bookloom/pipeline/TestBooks`
    (2 of the segments verbatim, short enough that the token budget does not bind): FAST 12 drafts (2 done at once), 0
    checks, 2 chunks (8 + 4); BALANCED 12 drafts + 2 checks; MAX 12 drafts + up to 12 checks over 6 chunks (2 per chunk)
    plus REVISE and one summary per unit. Write the totals the first run shows into the test only after checking them
    by hand against `ChunkPacker`; a repair round adds 1 step.
  - Edit `05_PIPELINE_ENGINE.md` (batches at `#tiered-loop`, the step announcements, `#generation-parameters`,
    `#rolling-summary`) and FR-ALGO-02/09 to the batched drafts the code already makes. → :pipeline, docs
- [ ] 4.7a Move `ProjectService` to project ids with behaviour unchanged, so the later project features have an id
  surface and the build stays green.
  - **Satisfies:** enabling work for 4.7b and 6.1a
  - Design K7, first step: `list`, `create` and `open(projectId)` replace `importBook(Path)`, `continueBook(Path)` and
    `decidedSegments(Path)`. `create` returns the new project's id; ids come from `IdSource` (2.1).
  - Move every caller in the same task: `ui/state/ImportViewModel.java:172`, `ui/state/ImportGuard:122`,
    `app/cli/BookOpener.java:44`, `ScriptedProjectService` (a `:ui` test fake), and the ~42 `:pipeline` tests that call
    the removed methods (`grep -rln "importBook\|continueBook\|decidedSegments" modules/*/src/test`).
  - The window still opens the just-imported project; nothing visible changes.
  - Verify: `./gradlew build`. → :api, :pipeline, :ui, :app
- [ ] 4.7b Add names, rename, remove, "translate into another language" and the missing-file paths to projects, so the
  Projects screen can keep several translations of one book.
  - **Satisfies:** app-shell "Name every project automatically from its book and languages"; app-shell "Let an automatic
    name follow the languages and a typed name stay"; app-shell "Allow one project per language pair of a book unless it
    is named"; app-shell "Refuse a project name already used by the same book"; app-shell "Rename and remove a project";
    app-shell "Translate a book into another language without importing it again"; app-shell "Find a project's book when
    its file is missing or changed"; app-shell "Open a project at its step and never continue it by itself";
    local-storage "Remove a project's saved work and nothing else"; local-storage "Delete the stored book with its last
    project"; local-storage "Accept a found book only when its content matches"; local-storage "Keep project names
    unique within one book, ignoring letter case"; glossary "Show the recurring terms with their purpose, use count and
    empty state"; resume "Treat a run stored as running or paused as interrupted"
  - `pipeline.project.ProjectNames`: the automatic name `<title> · SRC → TGT`, following the languages while AUTO, a
    pair already used in the same book (`PairInUse`) and a duplicate name in the same book (`NameInUse`) as typed results.
    A name is unique within its book only (owner, 2026-10-20).
  - `rename`, `remove` (keeps the book file, the exported files and the book's other projects; removes the book row with
    the last project), `translateInto(projectId, target, name)` (copies the brief, the Glossary and the Recurring terms
    with targets emptied, never the TM; uses `model.current`), and `relocate(projectId, path)` ("Find the book…": only a
    file whose hash matches, then `ProjectRepository.relocateBook`).
  - `open`: re-reads the book and checks its hash; a stored RUNNING or PAUSED run counts as interrupted; a missing or
    changed file returns `BookFileMissing` / `BookFileChanged`.
  - The recurring terms' "Used" count comes from the book index's postings.
  - Tests: extend `ProjectService*Test`s; add `ProjectNamesTest`, parameterised:
    - `Earth Gravity` en→uk → `Earth Gravity · EN → UK`;
    - Auto source → `Earth Gravity · → UK`;
    - no target → `Earth Gravity · EN → …`;
    - a used pair in the same book → `PairInUse`;
    - `Earth Gravity · 26b` twice in one book → `NameInUse`;
    - the same automatic name in two books → both accepted;
    - `Земля` vs `ЗЕМЛЯ` in one book → `NameInUse`;
    - rename back to automatic refused while the pair is used.
  - Verify: `./gradlew build`. → :api, :pipeline
- [ ] 4.8a Read the reasoning level and the sampling mode from the stored settings while their environment variables
  still override them, and send Off, Low or On to both kinds of server.
  - **Satisfies:** inference "Carry the reasoning level that the Generation setting chooses"; llm-provider "Turn
    reasoning off for an OpenAI-compatible call, and retry a reply that spent its cap reasoning"; llm-provider "Shape an
    Ollama-native chat request"
  - Design K9: `ChatRequests.build` takes a `RequestDefaults(reasoning, sampling)` argument from an injected
    `pipeline.prompt.RequestDefaultsSource` (environment variable when set, else `run.reasoning` / `run.sampling`, else
    today's default); its 16 callers pass it (`grep -rn "ChatRequests.build" modules/pipeline`).
  - `ChatRequest.reasoningEnabled` becomes `@Nullable ReasoningLevel` (OFF, LOW, ON; null = the provider's default);
    the Ollama request sends `think` false / `"low"` / true and `OpenAiRequestMapper` sends `reasoning_effort` none /
    low / medium (`OpenAiRequestMapper:29–32`); `ReasoningPlan` accepts `low`.
  - Tests: `RequestDefaultsSource` (env `BOOKLOOM_REASONING=low` wins over stored Off; unset → stored; nothing →
    Off); WireMock body assertions in both dialects for Off, Low and On.
  - Verify: `./gradlew build`. → :api, :llm, :pipeline
- [ ] 4.8b Read the review mode at each run start from the stored setting, while an explicitly set environment variable
  or property still wins, so a change in Settings › Automation reaches the next run.
  - **Satisfies:** translation-pipeline "Read the review mode at the start of each run"; settings "Apply the app
    language after a restart and the review mode at the next run start"; settings "Name the review modes with one set
    of words"; resume "Pause for review as the review mode says"
  - Design K9: `AppModule:54`'s instance binding becomes a non-singleton `Provider<ReviewMode>` (the resolver's mode
    when its source is the environment or the property, else stored `run.reviewMode`, else Unattended).
    `ui/state/RunStarter` and `pipeline/review/RetryDraft` (`:83`) inject `Provider<ReviewMode>` and read it per start
    or retry; `ReviewViewModel:88`, `ReviewPauseFollower:47`, `ReviewAvailability:24` and `TranslatingViewModel:200`
    read `RunContext.reviewMode()`. `UiModule:149` and `ReviewModeTestModule` stay valid. The command line keeps
    ignoring the flag.
  - Tests: env set wins; env unset → stored; nothing → Unattended; a stored change applies to the next start and a
    run in progress keeps its mode; a retry reads the current mode.
  - Verify: `./gradlew build`. → :pipeline, :ui, :app
- [ ] 4.8c Give every format a title by a fixed order, and give a named project's exports their own file names.
  - **Satisfies:** export "Take the original title in a fixed order and say where it came from"; export "Name the
    proposed file as the command line names its output"
  - Design K8: `BookTitleViewModel` and `FileNameTargets` take the metadata title, then Markdown front matter
    (`aux:fm:title`, `document/md/MarkdownAuxiliary.java:99`), then the first Markdown heading, then the file name
    without its extension; each step gives a reason line.
  - `ExportPathRules` appends the named project's suffix.
  - Tests: a Markdown front-matter title, a Markdown first heading, a TXT file `earth-gravity.txt` → "earth-gravity";
    two projects of one book and pair → `earth-gravity.uk.md` and `earth-gravity.uk.26b-max.md`.
  - Edit FR-REVIEW-01 and FR-EXPORT-04 in `01_FUNCTIONAL_REQUIREMENTS.md`. → :pipeline, :ui, docs
- [ ] 4.9 Prove a translation continues after a restart over the real SQLite store, so "Ready to continue" rests on a
  test and not on inspection. Depends on group 3.
  - **Satisfies:** resume "Reopen an unfinished project as ready to continue"; resume "Never continue a translation by
    itself"; resume "Continue at the first segment not yet decided"; resume "Treat a run stored as running or paused as
    interrupted"; resume "Recompute the progress of a reopened project from its stored decisions"; resume "Resume a
    stopped run with a new run"; local-storage "Keep the application's work across a restart"; app-shell "Continue an
    existing translation and never call it a start"; resume "Flag a step after its budget of pauses"; resume "Offer to
    skip the step a pause is stuck on"; resume "Pause instead of failing when an error stops the job"; resume "Recover
    from a provider error by itself"; translation-pipeline "End the run Failed on an error no pause can fix"
  - Test: `RestartContinueE2eTest` (pipeline e2e, temp database, pseudo model):
    - run a 12-segment fixture, stop after 5 decisions, drop the injector, build a new one on the same file, open the
      project, and see 5 decided, 7 pending; continue to 12 decided with the same accepted and flagged counts;
    - a second case abandons the run mid-chunk without a stop, and the reopened project still continues at the first
      pending segment.
  - Edit FR-RESUME-02/05 and `05_RELIABILITY_AND_RESUME.md` (NFR-REL-03/04).
  - The recovery rules these deltas restate are already built (`run/PauseDecider`, `run/RoutedCalls`): keep their
    tests green and add the cases the new wording names — a step that throws pauses and resumes by itself 3 times
    and is flagged on the 4th; a `validation` error waits for the person twice and is then flagged; Skip segment on a
    stuck pause flags it and goes on; an outage recovers by itself up to 10 pauses; `busy` ends the run Failed.
  - Verify: `./gradlew clean build check spotlessCheck`. → :pipeline, :persistence, docs

## 5. The window's shared infrastructure

- [ ] 5.1 Give dialog cards their three sizes and give the dialog host its Enter, Esc and queue rules, so every dialog
  fits the window, its footer stays put, and a dialog nobody asked for waits its turn.
  - **Satisfies:** app-shell "Show every dialog inside the window, on one of two layers"; app-shell "Give every dialog
    one of three sizes with a fixed header and footer"; app-shell "Answer Enter, Escape and Tab the same way in every
    dialog"; app-shell "Hold a dialog nobody asked for until the window is free"
  - `ModalCard.Size` (DIALOG 480, WIDE 720, XL min(1180, 92 %) × fixed min(820, 90 %)) with a fixed header and footer
    and a `ScrollPane` body.
  - `ModalHost`: Enter → the default button (Ctrl/⌘+Enter in a `TextArea`), Esc → the card's escape action,
    `showWhenFree`, and only About closes on an outside click.
  - Tests:
    - `ModalCardSizeTest` (at 960×640 the XL card is 883×576 and its footer y is unchanged after the body grows);
    - `ModalHostKeyQueueTest` (Enter runs the default; Esc on a hideable progress card hides it and on a question card
      closes it; a queued card opens only after the open one closes).
  - Edit FR-UI-10 (new) in `01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`. → :ui, docs
- [ ] 5.2 Replace the raw prompt and reply panes with parsed views, so prompts show as System and User sections and a
  reply as one row per segment.
  - **Satisfies:** app-shell "Offer Simple and Detailed views of a step"; app-shell "Show all model work in one progress
    dialog"; translation-pipeline "Offer Simple and Detailed views in a list and detail split"
  - `control/PromptView` and `control/ReplyView` replace `PromptContextPane` and `ReplyPane`; raw text sits in
    collapsed blocks with Copy.
  - Tests:
    - `PromptViewTest` (parameterised per slot: `styleSheet` → bullets, `glossary` → a 3-column grid, `items` with
      `<s id="2">` → an Id | Segment | Text row);
    - `ReplyViewTest` (4 items, one MISSING, gives 4 rows with "missing").
  - Rewrite the `PromptContextPane` and `ContextSection` tests. → :ui
- [ ] 5.3 Turn the busy layer into the work layer that holds the progress dialog, and remove the separate OS window, so
  all model work shows its steps, figures and parsed input and output in the window.
  - **Satisfies:** app-shell "Report an action that has not answered yet"; app-shell "Show every dialog inside the
    window, on one of two layers"; app-shell "Show all model work in one progress dialog"; app-shell "Offer Simple and
    Detailed views of a step"; app-shell "Follow the current step until a person chooses another"; app-shell "Let long
    work continue in the background"; app-shell "Ask before leaving a screen while long model work runs"
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
  - Verify: `grep -rn "new Stage(" modules/ui/src/main` has no hit (the startup-failure stage lives in `:app`). → :ui
- [ ] 5.4 Replace the 20-segment time-left window with a ledger of all steps, so "52 % in 2 h" reads "about 2 h left"
  and checks and repairs count.
  - **Satisfies:** app-shell "Show progress as one percentage of steps that never moves back"; app-shell "Show the time
    left only from measured time per kind of step"; app-shell "Show the share of work in the bar and the count of
    segments in the text"; app-shell "Estimate before Start only from a measured speed"; resume "Recompute the progress
    of a reopened project from its stored decisions"
  - `state/ProgressLedger` (D3) is fed by `StepsPlanned` / `StepChanged` and `ExportProgress`. It replaces
    `RunClock.timeLeft` and `RunFigures.percent`.
  - Tests: `ProgressLedgerTest`:
    - 3 tasks × 3 items = 9 steps;
    - 52 % done in 2 h of uniform steps → "about 2 h";
    - adding 10 steps never lowers the bar;
    - hidden for the first 4 steps;
    - a jump over 10 % a minute is capped;
    - a 30 min pause is excluded;
    - rebuilt after a restart from 74 of 142 decided segments: the bar starts there and time left is hidden until the
      new run has finished 5 steps;
    - an estimate before Start appears only when `measuredSecondsPerSegment` is stored.
  - Rewrite `RunClockTimingTest` and `RunFiguresTest`. → :ui
- [ ] 5.5 Move toasts to the bottom-right, route dialog feedback to the dialog's footer, and make scrollbars, links and
  the selected-segment bar visible, so nothing covers buttons and every state reads at the required contrast.
  - **Satisfies:** notifications "Stack transient messages in the bottom-right corner and time them by severity";
    notifications "Report a dialog's own feedback in its footer"; notifications "Carry four severities of transient
    message, coloured by the status roles"; theming "Underline every link and colour it for reading"; theming "Mark the
    selected choice of a segmented control with a bar"; theming "Keep a scrollbar's thumb distinguishable from its
    track"; app-shell "Show scrollbars whenever content overflows"; notifications "Raise a transient message only on the
    occasions the application names"
  - `ToastStack`:
    - `BOTTOM_RIGHT`, 16 px inset, 360 px wide, at most 4, newest at the bottom;
    - 5 s for success and info, 12 s for warning, error or a toast with an action;
    - ✕ on each;
    - while a dialog or visible work is open, the message goes to its footer line.
  - `theme.css`: one 12 px scrollbar rule (thumb `muted-2` on track `surface-2`, nav `nav-fg-muted` on `nav-bg-2`) and
    the seven regional copies aligned; links underlined in `-color-info-fg`; the 2 px `-color-text-strong` selected
    bar.
  - Tests: rewrite the toast tests (the fifth toast drops the oldest; a toast during an open dialog becomes a footer
    line); `ContrastTest` gains the thumb (≥ 3:1), links (≥ 4.5:1 on every surface, both themes) and the selected bar.
  - A test lists the toast occasions the notifications spec names and asserts each raises its toast, and that the
    provider test raises none (its feedback stays in its dialog).
  - Edit FR-NOTIF-01, `09_THEMING.md` (palette scrollbars, a new FR-THEME-13 for the scrollbar, link and selected-bar
    rules; FR-THEME-11 and -12 are taken) and `11_NOTIFICATIONS_AND_ERRORS.md` if a line still says bottom-centre.
    → :ui, docs
- [ ] 5.6 Add width classes, the overrun tooltip, the pinned banner slot, the fill-the-window opt-out and the content
  caps, so every screen lays out by its own width and fits from 960×640 to 1920×1080.
  - **Satisfies:** app-shell "Fit the layout to the width of the content area"; app-shell "Keep list and detail side by
    side and stack cards where they do not fit"; app-shell "Let four screens fill the window's height"; app-shell "Cut
    one-line text with a tooltip only where it is cut"; app-shell "Pin the run's banners above the content on every
    screen"; app-shell "Keep every part of the window reachable at its smallest size"; app-shell "Present one window
    divided into a fixed set of regions"; app-shell "Match the reference rendering"
  - `control/LayoutClass` (pseudo-classes, 16 px hysteresis, no logging); `control/Overrun`.
  - `#shell-banners` with `state/ShellBanners`; the `fills-viewport` style class read by `AppShellView.showContent`.
  - The 1320 px and 680 px caps; the nav at 236/200 px.
  - Tests:
    - `LayoutClassTest` (1039 → compact, 1040 → regular, back to 1030 stays regular, 1023 → compact);
    - a new `LayoutFitTest` grown from `TitleBarFitTest` (960×640 and 1920×1080 × `en` and `uk`: no clipped toolbar or
      title-bar item, no dialog past the window);
    - the banner cases in `ConformanceCases` (used by `ScreenConformanceTest`) moved to `#shell-banners`.
  - **Compare:** the empty shell at both sizes.
  - Edit FR-UI-11/12 (new). → :ui, docs
- [ ] 5.7 Add state markers with one colour map, read-only grids, and resizable term tables with a changed-row edge, so
  a long term is never cut by a chip and every state reads as an icon plus a word.
  - **Satisfies:** theming "Show every state in one of six colours, with an icon and a word"; glossary "Let every
    glossary column be resized"; app-shell "Use the control each drawn widget stands for"
  - `control/StateMarker` (six meanings); the `read-only-grid` style class with a small factory.
  - `TermTables` → `CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN` with minimum widths, keeping the 46 px rows; the
    `:changed` row edge replaces `ChangedMarks`' chip.
  - Tests: `StateMarkerTest` (each meaning has its style class and Feather icon); rewrite `TermTableCard*` (resizing
    Term keeps the Translation column ≥ 170 px; a changed row has the `changed` pseudo-class and its term cell has no
    chip).
  - Edit FR-THEME-10 in `09_THEMING.md` to the six meanings. → :ui, docs
- [ ] 5.8 Drive the title bar, the toolbar run actions, the banners, the nav footer and the step locks from one
  run-state table, so all 16 states look the same everywhere and the steps stay locked until a project is open.
  - **Satisfies:** app-shell "Show each of the sixteen run states the same way everywhere"; app-shell "Show the run in
    the title bar while one exists"; app-shell "Close each workflow step with a Back and a forward action"; app-shell
    "Show the chosen provider and model in the navigation footer"; app-shell "Disable model actions while a translation
    is at work"; app-shell "Pin the run's banners above the content on every screen"; app-shell "Let title-bar items
    give way by priority"; app-shell "Call a model call stuck after a minute and show one clock"; app-shell "Move
    through the five workflow steps and start one run at a time"; translation-pipeline "Show a stuck call with one
    clock"; resume "Pause, stop and resume a run from the screen"; resume "Show the provider-error state with its three
    actions"; notifications "Tell a person the AI model cannot be reached while waiting for it by itself"; notifications
    "Report a paused or failed run in the run's own banner"; notifications "Choose a failure's surface from its code";
    app-shell "Match the reference rendering"
  - `state/RunStateView` (D8).
  - The title bar shows the project name, the state text and the AI-model chip.
  - The run actions move from `TranslatingView` into the toolbar through `StepFooter.claim()`. The Stop dialog says
    "74 of 142 segments are done and saved. The 4 being translated now are translated next time. For a short break,
    use Pause."
  - `LeaveGuard` gains a dirty-form check.
  - The nav footer follows the chip's source ("No AI model found").
  - Tests: `RunStateMappingTest` (parameterised over the 16 README rows, with title text, chip meaning, toolbar ids and
    banner key); rewrite `StepAvailabilityTest` for the run-state rules. The no-project lock lands in 6.1, so the app
    keeps a way into Import until Projects exists.
  - **Compare:** the shell in Ready, Translating, Paused, Waiting for the AI model and Finished. → :ui
- [ ] 5.9 Add "Show technical details" as one property every Simple/Detailed view and Advanced fold reads, so plain
  words are the default and the technical lines appear in one place.
  - **Satisfies:** app-shell "Hide technical words unless Show technical details is on"; app-shell "Show technical
    details of a failure only on request"; app-shell "Offer Simple and Detailed views of a step"; settings "Show a
    provider's connection details and open them when they matter"
  - `state/TechnicalDetails` bound to `ui.technicalDetails`.
  - Test: `TechnicalDetailsTest` (on → a new progress dialog opens in Detailed and the Settings folds open).
  - Edit FR-UI-13/14 (new) in `01_FUNCTIONAL_REQUIREMENTS.md#fr-ui` (`07_SETTINGS.md` already states G12).
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, docs

## 6. Screens, in workflow order

- [ ] 6.1a Build the Projects screen and lock the steps until a project is open, so the way into the workflow is a list
  of translations grouped by book.
  - **Satisfies:** app-shell "Lock the five workflow steps until a project is open"; app-shell "Group the navigation and
    number the workflow"; app-shell "Show the Projects screen as a list grouped by book"; app-shell "Show what a project
    row says and offers"; app-shell "Open a project at its step and never continue it by itself"; app-shell "Open the
    last project or Projects at launch"; app-shell "Show a first-launch state on Projects when none exist"; app-shell
    "Start a new translation from the toolbar"; app-shell "Continue an existing translation and never call it a start";
    book-import "Open the import screen empty from New translation"; app-shell "Match the reference rendering"
  - `screen/ProjectsView` and `state/ProjectsViewModel` over `ProjectService` (4.7a/4.7b):
    - search, the All / In progress / Finished filter, and "No project matches 'xyz'. [Clear search]";
    - book headers with ＋ Translate into another language (its dialog comes in 6.1c);
    - project rows with a status chip, Continue/Open and ⋯ (Rename…, Show book file, Remove project…);
    - "+ New translation" opens Import;
    - the empty state with How it works and the AI-model line (its data comes in 6.1b; until then it reads the
      current provider list).
  - `ViewNames.PROJECTS` gets its resource; `StepAvailability` locks steps 1–5 without an open project ("Steps unlock
    when a project is open").
  - Opening a project writes `app.lastProject`; at launch, with `automation.reopenLastProject` on and the project still
    stored, the window opens it, otherwise Projects (design D11).
  - Tests: screen states (list, empty, search with no hits); a recording fake per row action; reopen-last-project on and
    off and with the project removed; fix `NavigatorTest`, `ViewNamesTest`, `ScreenConformanceCoverageTest` and
    `StepAvailabilityTest` (no project → steps 1–5 locked; Projects and Settings open).
  - **Compare:** `go('projects')` with projects and the empty state.
  - Edit the FR-PERSIST-01 project lines and add FR-UI-15 (Projects) in `01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`.
    → :ui, docs
- [ ] 6.1b Add the first-launch look and the AI-model questions on continue, so the first screen says whether
  translation can start and a continued run never silently uses another model.
  - **Satisfies:** llm-provider "Look once for a local AI program at first launch"; llm-provider "Store what the
    first-launch look found"; llm-provider "Look again when the person presses Try again"; app-shell "Look again when
    the person presses Try again"; llm-provider "Discover the models a provider offers"; resume "Ask when the stored AI
    model does not answer"; resume "Ask which model to use when a different one is chosen"; app-shell "Match the
    reference rendering"
  - `state/LocalModelProbe` (design D14, K10): window only, once when `firstLaunch.probed` is absent; "Try again" runs
    it on request; a program with zero models counts as not found; saves what answered and sets `model.current` for one
    model, asks which one for several.
  - The continue dialogs: "AI model not answering" and "A different model is chosen" compare `RunRecord.model` with
    `model.current` (design D11).
  - Tests: `LocalModelProbeTest` (exactly `http://127.0.0.1:11434` and `http://127.0.0.1:1234/v1`, one call each, no
    second look; Try again calls again; zero models → "No AI model found"); a WireMock test of
    `ModelCatalog.listModels(ProviderConfig)` in both dialects receiving only `GET /api/tags` / `GET /v1/models`; the
    two continue dialogs with a stored model `gemma4:e4b` against current `qwen3:8b`.
  - **Compare:** the empty state with and without a model, and both continue dialogs. → :ui, :llm
- [ ] 6.1c Add the project dialogs, so projects can be named, renamed, copied into another language, removed and
  relinked to a moved book file.
  - **Satisfies:** app-shell "Allow one project per language pair of a book unless it is named"; app-shell "Let an
    automatic name follow the languages and a typed name stay"; app-shell "Refuse a project name already used by the
    same book"; app-shell "Rename and remove a project"; app-shell "Translate a book into another language without
    importing it again"; app-shell "Find a project's book when its file is missing or changed"; book-brief "Open Same
    language pair when the chosen pair is already used"; local-storage "Accept a found book only when its content
    matches"; app-shell "Match the reference rendering"
  - Same language pair; Rename (chips, a uniqueness error within the book after the first edit, "Use the automatic
    name again"); Translate into another language; Remove project; Find the book… (only a file with the same content).
  - Tests: each dialog's buttons call the recording fake with the expected arguments; Rename shows the error for a name
    used in the same book and accepts one used in another book; Find the book refuses a file with different content.
  - **Compare:** each dialog. → :ui
- [ ] 6.2 Rebuild Import around the one-line verdict, the centred drop zone, the "About this book" fold and the
  three-pane text browser, so the page fits one screen and the book's text is browsed in one widget.
  - **Satisfies:** app-shell "Report an action that has not answered yet"; book-import "Say in one line what the import
    found"; book-import "Round the word count only from a thousand up"; book-import "Compare only the file's own
    language marks"; book-import "Show the book's details in a closed About this book fold"; book-import "Count chapters
    as the structure does"; book-import "Browse the book's text in three panes"; book-import "Ask before another book is
    opened while a project is open"; book-import "Open a book and report what was found"; book-import "Warn when an
    EPUB's own language declarations disagree"; book-import "Warn when a book declares a language the application does
    not recognize"; book-import "Block a DRM-protected book and name its encryption"; book-import "Name the encryption
    scheme of a blocked book"; book-import "Refuse a file of an unsupported type and name what it is"; app-shell "Ask
    before an import while a project is open"; app-shell "Match the reference rendering"
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
  - **Satisfies:** book-brief "Make the brief read-only while a translation is going on"; book-brief "Lock the languages
    once translating has started"; book-brief "Show the project name on the brief and let the person rename it";
    book-brief "Open Same language pair when the chosen pair is already used"; book-brief "State a time estimate on the
    brief only when it is measured"; book-brief "Show the brief suggestion in a progress dialog that is not hideable";
    book-brief "Show what the suggestion decides with its agreement and verified quote"; book-brief "Write nothing to
    the brief until the person applies the suggestion"; book-brief "Suggest the tone and style from verified readings,
    one decision per field"; app-shell "Match the reference rendering"
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
  - **Satisfies:** glossary "Keep both term tables editable while a translation runs"; glossary "Show the recurring
    terms with their purpose, use count and empty state"; glossary "List the eight steps of the names pass in its
    progress dialog"; glossary "Show the names pass's results with Undo all"; glossary "Run the Scan, Review and
    Translate actions in the same dialog with two steps"; glossary "Offer the one-pass preparation in a progress
    dialog"; glossary "Show the glossary as an editable table on Names & style"; glossary "Keep a stored lexicon of
    recurring terms and show it on Names & style"; app-shell "Disable model actions while a translation is at work";
    app-shell "Make the screen that hidden work writes to read-only"; app-shell "Let long work continue in the
    background"; app-shell "Keep list and detail side by side and stack cards where they do not fit"; app-shell "Match
    the reference rendering"
  - `NamesStyleView` switches at 1440 px.
  - `RecurringTermsCard` gains the Used column.
  - The model actions are disabled while translating ("Pause the translation first — BookLoom sends one model request
    at a time").
  - `AutoNamesBar` opens `ProgressDialog` with the 8 steps; `ChangeResultsDialog` becomes its Results view (Changes /
    Steps tabs, Undo all, Show changed rows).
  - Scan / Review / Translate run the 2-step variant.
  - Tests: rewrite `ChangeResultsDialogTest`; at 1280×800 the cards stack; while the pass runs (hidden too) Start
    translation waits with "Wait until the names pass ends"; a hidden pass disables `+ Add`, Scan,
    Review, Translate, every Translation input and the Locked switches (README "Names & style": read-only under hidden work).
  - **Compare:** at 1280 and 1920, the dialog, Results, hidden. → :ui
- [ ] 6.5 Rebuild Translating as a summary strip and a full-height live card, so the person sees time left, every
  segment of the call in flight with its own marker, and the scene summary without scrolling.
  - **Satisfies:** translation-pipeline "Always show the scene summary under the live detail"; translation-pipeline
    "Explain sleep and keep-awake in a closable help line"; translation-pipeline "Offer Simple and Detailed views in a
    list and detail split"; translation-pipeline "Follow the newly translated segment until the person picks another";
    translation-pipeline "Show a stuck call with one clock"; translation-pipeline "Show the segment just decided and the
    one in progress in the live panel"; translation-pipeline "Show the Translating screen's idle, completed and failed
    states"; translation-pipeline "Derive each segment count from the engine's progress snapshot"; translation-pipeline
    "Start a translation and report its progress"; translation-pipeline "Send a batch reply's per-segment results to the
    rows"; translation-pipeline "Tag every activity-log entry with one of seven kinds"; app-shell "Show the live card's
    Live and Activity log tabs"; app-shell "Let four screens fill the window's height"; app-shell "Show the share of
    work in the bar and the count of segments in the text"; app-shell "Match the reference rendering";
    translation-pipeline "Announce each model call as it starts"; translation-pipeline "Tell the person when the model
    is slow to answer"; translation-pipeline "Refuse to start a run with a missing target language or model"
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
  - Kept behaviour shown in the new card, with tests: each model call is announced as it starts and appears as the
    current call; after 60 s with no answer the live line says the model is taking longer than usual with one clock;
    Start with no target language or no model is refused and names the missing item.
  - **Compare:** every run state from the Run states page, in Simple and Detailed. → :ui
- [ ] 6.6a Move review into the extra-large dialog that opens by itself, with row markers, one main button, the More
  menu and an inline "Try again with a note", so the person reviews with room and no dialog replaces another.
  - **Satisfies:** review-queue "Open the review desk as a large dialog"; review-queue "Open the review desk by itself
    when a translation finishes"; review-queue "Filter the desk by what needs attention"; review-queue "Mark every desk
    row with its state"; review-queue "Keep "later" as a mark of this application run only"; review-queue "Offer the
    less common actions under More"; review-queue "Move through the desk with keyboard shortcuts"; review-queue "Show on
    "What the model saw" the context the model was actually sent"; review-queue "Turn Accept into Save my edit while the
    translation holds unsaved changes"; review-queue "Show a finding's kind and the words it quotes where they stand";
    review-queue "Badge a flagged segment by its worst finding"; review-queue "Compare source and target side by side
    with the segment's findings and context"; review-queue "Open the compare on the best text the segment has";
    review-queue "Accept, save an edit, undo it, and never discard an edit silently"; review-queue "Retry a segment with
    the context it first saw"; review-queue "Allow a retry whenever no run of the project is running"; review-queue
    "Locate a segment by chapter and paragraph"; review-queue "Show an empty state when nothing needs review";
    review-queue "Show a readable view of the source and the target under the panes"; quality-gates "Record each
    segment's findings for review and repair"; app-shell "Hold a dialog nobody asked for until the window is free";
    app-shell "Match the reference rendering"; review-queue "Make the target read-only while a run translates";
    review-queue "Open the segment a review pause names"; review-queue "Browse every segment after an Unattended run";
    review-queue "Apply backward-revision proposals for edited segments only on acceptance"; review-queue "Refresh the
    flagged count when a run stops writing"; review-queue "Refuse a desk action on a segment whose status it does not
    start from"; review-queue "Mark a segment reviewed only by the desk actions that review it"; review-queue "List in
    Worth a second look the accepted segments the final audit doubts"; review-queue "Explain Worth a second look on
    hover where its count is shown"
  - `ReviewPanel`, `ReviewListPane`, `ReviewComparePane`, `FindingRows`, `ReadableView` and `ui/control/ContextSection`
    move into an XL `ModalCard`.
  - It opens from Review (n), from the strip, and by itself through `showWhenFree` when a run finishes with segments to
    review and `automation.openReviewDesk` is on.
  - "✓ Accept & next" turns into "✓ Save my edit & next" once the text is edited.
  - More ▾: Leave for later, Try again, Try again with a note… as an inline panel, Undo my edit (disabled when
    unedited). A retry while the project's own run is running is still refused with `ErrorCode.busy`.
  - Ctrl/⌘+Enter runs the main button; Ctrl+Alt+↑/↓ move between rows.
  - "What the model saw" reads `sentSections`; one label per finding; the duplicate label goes.
  - Delete `RetryWithNoteDialog`.
  - Tests: rewrite the `ReviewPanel*` screen tests; the desk opens by itself after a COMPLETED run with 6 flagged, not
    with 0, and not while another dialog is open; Ctrl/⌘+Enter and Ctrl+Alt+↓ each tested; "What the model saw" shows
    the batch's sent sections for a batch-adopted segment.
  - Edit FR-REVIEW-03/05/09.
  - The desk keeps the review rules the inline panel had; its rewritten tests cover each: the target is read-only
    while a run translates; a review pause opens the desk on the segment it names; "All segments" lists every
    segment after an Unattended run; a backward-revision proposal applies only on acceptance; the to-review count is
    re-read when the run state changes; an action on a segment in the wrong status is refused with
    `ErrorCode.validation`; only Accept, Save my edit and Leave for later mark a segment reviewed; "Worth a second
    look" lists the accepted segments the final audit doubts and explains itself on hover (EN and UK keys).
  - **Compare:** `openDialog('review')` at 960 and 1920. → :ui, docs
- [ ] 6.6b Protect the formatting marks in the review editor, so the person can never save an edit that breaks a mark.
  - **Satisfies:** review-queue "Keep formatting marks whole in the translation editor"; review-queue "Show stored
    formatting marks in the editor by their number"; review-queue "Refuse a saved edit whose formatting marks are
    missing or doubled"; review-queue "Name the formatting marks the editor lacks and hold the main button";
    review-queue "Turn Accept into Save my edit while the translation holds unsaved changes"; app-shell "Match the
    reference rendering"
  - `control/ProtectedMarks` (a `TextFormatter` filter) and `state/MarkStatus` (design D7); the editor shows `⟦gN⟧` as
    `⟦N⟧` and maps back before the placeholder check; the main button binds to "complete"; the strip under the editor
    says what is missing or doubled.
  - Tests: `ProtectedMarksTest`, parameterised:
    - backspace inside `⟦1⟧` removes all of it;
    - pasting `⟦1⟧⟦2⟧` when `⟦2⟧` is present inserts only `⟦1⟧`;
    - `setText` passes unchanged;
    - undo after a whole-mark delete restores it;
    - the main button is off while `⟦1⟧` is missing;
    - `⟦g0⟧ text ⟦g1⟧` loads as `⟦0⟧ text ⟦1⟧` and saves back unchanged.
  - At 960×640 the translation editor and the mark strip are visible (README "Review desk").
  - **Compare:** the desk edited, and with a missing mark. → :ui
- [ ] 6.7 Finish Export: the toolbar action and its reasons, partial export, tiles from the project, the title fallback,
  asking before a replace, and the file-name and last-check progress dialogs. The screen is then never empty, and
  never overwrites a file without asking.
  - **Satisfies:** export "Show the tiles from the project before any export"; export "Show the book's title and author
    with a reason when there is none"; export "Take the original title in a fixed order and say where it came from";
    export "Suggest a file name in a short progress dialog"; export "Show the last check as a progress dialog the person
    can leave or end"; export "Show each reread item next to its neighbours with a before and an after"; export "Let the
    person write the file early or cancel the last check with a question first"; export "Announce a partial export in a
    banner"; export "Replace an existing file only when the person allows it"; export "Choose where to save on the
    export screen"; export "Export at any time except while a run is translating"; export "Run the final consistency
    pass before writing when it is switched on"; app-shell "Match the reference rendering"; export "Check the written
    book before it replaces the destination"; export "Run the whole-book backward revision before writing the book"
  - `ExportView`:
    - ⬇ Export book is in the toolbar, disabled while translating and with nothing translated, each with its reason;
    - the partial banners; the last check is off and disabled for a partial export;
    - a finished export records an `ExportRecord` through `ProjectRepository.recordExport`;
    - tiles from `SegmentRepository.countsByStatus` and `ExportRecord` (`ExportResult.reset()` no longer blanks
      them);
    - `ExportTitleCard` with the `aux:fm:*` fallback and a reason line;
    - the Replace switch is removed and a confirm asks before replacing.
  - "✦ Suggest a name" opens the short dialog; the last check opens the long dialog with Write the file now, the
    Cancel question above it ("Cancel the export? No file is written."), and Export complete. Esc hides the long
    dialog (continue in background; owner, 2026-10-20).
  - Tests:
    - the tiles show "74 of 142" before any export;
    - an existing destination asks first and Cancel writes nothing;
    - a partial export has the last check disabled;
    - an item's Before | After grid reads "Unchanged — kept as it was";
    - Esc on the last-check dialog hides it and the activity chip reopens it; Cancel asks first; Write the file now
      writes the file with the items checked so far;
    - the tiles after a restart come from the stored `ExportRecord`;
    - rewrite the export screen tests.
  - Edit FR-EXPORT-04/06.
  - **Compare:** ready, paused, finished, and each dialog.
  - Kept behaviour, tests stay green and move to the new screen: the written book is re-read and checked before it
    replaces the destination (a failed check leaves the destination untouched); on Max the backward revision runs
    before the file is written and leaves segments the person edited alone.
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, docs

## 7. Settings

- [ ] 7.1a Build Settings › Providers' list, form and saving: the list | form split, the summary card, the Connection
  details fold around the full form, Delete, and the pinned save footer with the leave guard. A person can then add and
  keep a provider without leaving the window.
  - **Satisfies:** settings "Add, edit and delete providers"; settings "Describe a provider's connection in a form";
    settings "Choose how a provider signs in without entering a key"; settings "Let the endpoints be overridden where
    the server needs it"; settings "Take a provider's custom headers and models"; settings "Show a provider's connection
    details and open them when they matter"; settings "Save Providers and Models through a pinned footer and guard
    leaving"; settings "Describe each provider in its row and its detail card"; settings "Choose a model from the list,
    or name one the list does not hold"; local-storage "Require a credential-like header to name an environment
    variable"; app-shell "Ask before leaving a screen while long model work runs"
  - `SettingsController` / `SettingsViewModel`:
    - the list at 240/200 px (empty until the first-launch look or the person adds one); the form column capped at
      860 px;
    - the form: name, kind (pre-fills the preset address), sign-in + key variable "set / not set", address with the
      `/v1` warning, the two endpoint overrides (inference disabled for Ollama with its reason), timeouts, model, custom
      headers, custom models (`ProviderConfigRules` from 2.4);
    - Delete… confirms;
    - "● Unsaved changes · Cancel · Save", and leaving asks Keep editing · Discard · Save (`LeaveGuard`).
  - `settings.fxml` enables Add, Edit and Delete.
  - Tests: recording-fake saves and deletes; the leave guard on navigation; a header `X-Api-Key: sk-1` is refused in
    the form; at 960×640 Save is visible without scrolling (README "Settings"); rewrite the existing Settings screen
    tests for the new layout.
  - Edit FR-SETTINGS-01 and the saving rule (new). → :ui, docs
- [ ] 7.1b Add the provider checks, the test dialog and "Use this one", so a person can test an unsaved provider, see
  what was sent and received, and switch providers safely.
  - **Satisfies:** settings "Check a provider in three stages and report each one"; settings "Show a check's details in
    a test dialog"; settings "Use one provider and ask first while the open project has stored decisions"; settings
    "Store the speed the inference test measured on the model"; llm-provider "Verify a provider description that is not
    saved"; llm-provider "Never show the key in a log line or in the test dialog"; app-shell "Match the reference
    rendering"
  - Checks through the draft verifier (`ProviderVerifier.verify(ProviderConfig, …)`, 4.1); the test dialog (verdict,
    request line with Copy as curl with the key hidden, Body | Metrics | Response, switching between the three tests,
    Close); its feedback stays in its own footer, never a toast.
  - A passing inference test stores the measured seconds per segment on the model (`ModelSettings`), the source of the
    estimates before Start.
  - Use this one sets `model.current`, and asks "Use LM Studio for the rest of this book?" while the open project has
    stored decisions.
  - Tests: the test dialog fills from `VerificationExchange` with the key shown as `••••`; a passing inference test
    stores `measuredSecondsPerSegment`; Use this one asks only when decisions exist.
  - **Compare:** each Providers state and the test dialog. → :ui
- [ ] 7.2 Build Settings › Models, so each model keeps its own temperature, context window, answer length and output
  field, and shows the settings it refused, with Send again.
  - **Satisfies:** settings "Describe each model on the Models tab and keep its own settings"; settings "Report what the
    program says about a model"; settings "Choose how the temperature is sent for a model"; settings "Set a model's
    context window and answer length"; settings "List the settings a model refused and let the person send them again";
    settings "Save Providers and Models through a pinned footer and guard leaving"; inference "Remember a refused
    setting for the provider and model"; app-shell "Match the reference rendering"
  - The model list across providers; "Found out from the program" from `ModelCapabilities`; the three-way temperature
    with a slider for My value; Use context window (with `num_ctx` in grey under technical details); Limit answer
    length; the Advanced output field (disabled for Ollama); "Settings this model refused" with Send again or "Nothing
    refused"; the pinned footer.
  - Tests: Save writes `ModelSettings` with OMIT; Send again removes a `RefusedSetting`.
  - **Compare:** `setSet('models')`. → :ui
- [ ] 7.3 Build Generation and Appearance with only the knobs the code has, so nothing on them is fictional.
  - **Satisfies:** settings "Show how quality, thinking and sampling are set on Generation"; settings "Choose the theme,
    the language and technical details on Appearance"; settings "Apply the other tabs at once and say when"; settings
    "Apply the app language after a restart and the review mode at the next run start"; localization "Choose the
    language from the operating system on first launch"; theming "Follow the operating system's colour scheme on first
    display"; theming "Let a person change the theme in two places"; inference "Carry the reasoning level that the
    Generation setting chooses"; app-shell "Match the reference rendering"
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
  - `:app` implements `api.AppRestart` (2.6) and binds it in place of the no-op.
  - Edit FR-SETTINGS-03/04, FR-UI-06 and DD-34.
  - **Compare:** both tabs. → :ui, :api, :app, docs
- [ ] 7.4 Build Automation and Storage & logs, so the review mode, defaults, keep-awake, reopen-last-project and the
  database and log places are visible and changeable.
  - **Satisfies:** settings "Choose when to stop and ask on Automation"; settings "Name the review modes with one set of
    words"; settings "Show storage and logs on one tab"; settings "Apply the other tabs at once and say when"; settings
    "Apply the app language after a restart and the review mode at the next run start"; settings "Release the keep-awake
    hold at once when the switch is turned off"; local-storage "Make a backup copy on request"; app-shell "Keep the
    computer awake while model work runs"; app-shell "Open the last project or Projects at launch"; app-shell "Estimate
    before Start only from a measured speed"; review-queue "Open the review desk by itself when a translation finishes";
    export "Run the final consistency pass before writing when it is switched on"; resume "Pause for review as the
    review mode says"; app-shell "Match the reference rendering"; settings "Show all six settings areas, every one
    enabled"
  - **Automation:** "When to stop and ask you", default quality, open the desk when a translation finishes, the
    last-check default, keep the computer awake, reopen the last project.
  - **Storage & logs:**
    - the database folder and size, Open folder, and Make a backup… (implement `SqliteStorageInfo.backupTo`, stubbed
      in 3.5: a `VACUUM INTO` through the write executor);
    - the log folder, 10 launches kept, the detailed-log switch, Open log folder, Save a diagnostic bundle…;
    - short paths with the full path in a tooltip.
  - The end of every run stores the measured seconds per segment on the run's model (design D3).
  - Tests: each Automation switch writes its key; changing "When to stop and ask you" makes the next started run use
    it; keep-awake off releases the hold at once; the backup file opens as a database with the same project count;
    the end of a run stores `measuredSecondsPerSegment`;
    the detailed-log switch writes the marker file `TraceFileResolver` reads at the next launch (`BOOKLOOM_TRACE_FILE`
    still overrides), and says "after BookLoom restarts".
  - Edit FR-SETTINGS-05/06 and NFR-PRIV-04.
  - **Compare:** both tabs.
  - A test asserts all six tabs are enabled and the subtitle reads "Your books stay on this computer. BookLoom sends
    your text only to the AI model you choose here."
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, :persistence, docs

## 8. Words, in English and Ukrainian

- [ ] 8.1 Apply the README Words table to both bundles, so the main screens say "segment", "to review", "Try again
  now", "Skip these 4 segments" and "AI model", and keep chunk, call, tokens and attempts for Detailed views.
  - **Satisfies:** app-shell "Use the words of the vocabulary on the main screens"; app-shell "Hide technical words
    unless Show technical details is on"
  - `messages_en.properties` and `messages_uk.properties`, with `MessageKey` entries added or removed together.
  - The segment tooltip on every segment count.
  - Ukrainian written for the EN source, with ICU plurals one/few/many/other and no gendered past tense for "you".
  - Test: the bundle parity and ICU validity tests stay green; a grep test asserts no `flagged`, `Retry now` or
    `chunk` remains in a main-screen key's EN value.
  - Edit `10_I18N_AND_ACCESSIBILITY.md` only if a line now disagrees. → :ui
- [ ] 8.2 Run every fit check in Ukrainian across the screens and dialogs, so the longer labels fit at 960×640.
  - **Satisfies:** app-shell "Keep every part of the window reachable at its smallest size"; app-shell "Fit the layout
    to the width of the content area"; app-shell "Let title-bar items give way by priority"
  - `LayoutFitTest` covers every screen and every dialog size × `uk`.
  - Fix any overflow with the README's shorter labels.
  - Edit `06_TESTING_STRATEGY.md`: the 16 states, the new tests, the first-launch look's tests (`LocalModelProbeTest`
    and the WireMock model-list test from 6.1b); Monocle and the retired `Proves:` / `traceCheck` lines removed.
  - Verify: `./gradlew clean build check spotlessCheck`. → :ui, docs

## 9. Gate and hand run

- [ ] 9.1 Bring the project's own documents up to date, so the next reader finds what was built.
  - `AGENTS.md` "Where it stands": Projects, the full Settings, SQLite storage, continue after restart, the first-launch
    look.
  - The `AGENTS.md` Modules table (`:persistence`: SQLite + JDBI; Flyway from the first release).
  - `docs/implementation_plan/CHANGE_BACKLOG.md`: mark this change done (its order is already recorded).
  - `docs/implementation_plan/01_MODULE_INVENTORY.md`: the new packages `persistence.sqlite`, `ui` controls and state.
  - `IMPLEMENTED_REQUIREMENTS.md`: FR-THEME-4, FR-PROV-01, FR-EXPORT-04, FR-REVIEW-05, and the FR-PERSIST,
    FR-SETTINGS, FR-RESUME and FR-UI-06 lines this change builds.
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
