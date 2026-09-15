Every task is self-contained: its **Read** list names what it needs, so it can be planned and applied without any other
context. These rules apply to every task:

- **Git.** Work on `feature/add-translation-engine-and-cli--<task>` (for example `--1.1-api-contracts`), commit there,
  squash-merge one commit into `feature/add-translation-engine-and-cli`, and delete the task branch (`AGENTS.md#git`).
- **Tests first.** Write the task's tests, run them and watch them fail, then implement
  (`AGENTS.md#definition-of-done`). Name tests `method_state_expected`, put no `if`, `for` or `while` in a test body
  (use `@ParameterizedTest`), create files in `@TempDir`, and write expected values out instead of recomputing them
  (`.claude/rules/testing.md`).
- **Logging is part of done.** Write what the task's **Log** line lists, at the levels in design.md D8: INFO for
  lifecycle, DEBUG for every public method's parameters and every branch taken, TRACE for values and book text, WARN
  for flagged or degraded outcomes, ERROR once for an unexpected fault, and never a secret. Read the task's log output
  once at TRACE before calling it done (`.claude/rules/logging.md`).
- **Code rules.** `.claude/rules/java-coding-style.md`: records for data, `Objects.requireNonNull` at boundaries, a
  `@NullMarked` `package-info.java` with a purpose Javadoc for each new package, no `synchronized`, methods of at most
  30 lines and files of at most 400. `.claude/rules/error-envelope.md`: every port method returns a `Result`, behind a
  boundary `try/catch`, with a typed `ErrorCode`. Also `.claude/rules/architecture-layering.md` and
  `.claude/rules/offline-and-privacy.md`.
- **Finish** with `./gradlew spotlessApply`, then the task's **Done when** commands.
- **Sources.** Requirements are named in quotes and live in this change's `specs/<capability>/spec.md`. Decisions are
  in `design.md`; the two contract decisions are in `docs/adr/ADR-0033-bound-chat-model-and-pausable-translation-job.md`.

## 1. Contracts and document lifetime

- [x] 1.1 Add the `:api` contracts that the engine, the model and the command line share, the file suffixes on `BookFormat`, and copy-with methods on the document records. Every other module compiles against these types, so they come first, and no other module changes in this task. → `:api`
  - **Read:** design.md D1 and D2 (the code blocks, "Events and report types" and "Document-side changes");
    ADR-0033; `modules/api/src/main/java/module-info.java`;
    `modules/api/src/main/java/ua/bookloom/api/{Result,AppError,ErrorCode}.java`;
    `modules/api/src/main/java/ua/bookloom/api/document/{BookFormat,Segment,Unit,Document}.java`.
  - **Change:** the package `ua.bookloom.api.llm` with `ChatModel`, `ChatRequest`, `ChatMessage`, `ChatRole`,
    `ChatResponse`, `FinishReason`, `ChatModelFactory` and `ModelSelection`; the package `ua.bookloom.api.pipeline`
    with `TranslationEngine`, `TranslationRequest`, `TranslationJob`, `JobListener`, `Subscription`, `JobState`,
    `JobStage`, `PausePoint`, `PauseReason`, the sealed `JobEvent` and its records, `JobProgress`, `JobReport` and
    `FlaggedSegment`; both exported from `module-info.java`. `BookFormat` gains its suffixes (longest first),
    `ofFileName(String)` and `matchedSuffix(String)`. The records gain `Segment.withDecision(SegmentStatus, @Nullable
    String)`, `Unit.withSegments(List<Segment>)` and `Document.withUnits(List<Unit>)`.
  - **Test first:** `modules/api/src/test/java/ua/bookloom/api/pipeline/JobReportTest.java`: a non-terminal end,
    FAILED without an error, and a written file without COMPLETED are each rejected.
    `modules/api/src/test/java/ua/bookloom/api/document/BookFormatTest.java`: `B.FB2.ZIP` is FB2 with the suffix
    `.FB2.ZIP`, `Book.fb2` is FB2 with `.fb2`, `b.markdown` is MARKDOWN, and `b.pdf` has no format. The existing
    `modules/api/src/test/java/ua/bookloom/api/document/SegmentTest.java`: each copy-with method changes only its own
    components.
  - **Log:** none; `:api` holds contracts only and has no logger.
  - **Rules:** `api-is-framework-free` and `records-first` in `.claude/rules/architecture-layering.md`: an `..api..`
    package holds only records, interfaces and enums.
  - **Done when:** `./gradlew :api:build` and `./gradlew :app:archTest` are green.
- [x] 1.2 Add `close(Document)` to `DocumentPort`, implemented in `DocumentService` through each format's reader, make `FormatResolver` read its suffixes from `BookFormat`, correct the port's Javadoc, and log every port call. Without a release every translated book stays in memory until the app quits (backlog D3), and without these lines nobody can see which book a job opened, wrote or released. → `:document`
  - **Read:** `specs/document-round-trip/spec.md` "Release an opened book"; design.md D6 and D8;
    `modules/api/src/main/java/ua/bookloom/api/document/DocumentPort.java`;
    `modules/document/src/main/java/ua/bookloom/document/{DocumentService,FormatResolver}.java`;
    `modules/document/src/main/java/ua/bookloom/document/model/OpenDocumentRegistry.java`, whose `close(String)`
    exists and has no caller; the readers
    `modules/document/src/main/java/ua/bookloom/document/{epub/EpubReader,fb2/Fb2Reader,md/MarkdownReader,txt/TxtReader}.java`;
    `modules/document/src/test/java/ua/bookloom/document/{DocumentServiceTest,DocumentServices,FormatDispatchTest}.java`.
  - **Change:** `DocumentPort.close(Document)` returning `Result<Boolean>`; `close(String documentId)` on each reader,
    over its registry; `DocumentService.close` switching on `document.format()` inside a boundary `try/catch`;
    `FormatResolver` matching `BookFormat`'s suffixes and keeping its content checks. In the `DocumentPort` Javadoc,
    replace "Both methods" with "Every method", name the DRM outcome of `open` as `ErrorCode.validation`, and replace
    the `unmask` sentence claiming `:pipeline` may not depend on `:document` with the real limit: JPMS exports and
    `ports-not-concretes`. Test logging: `testRuntimeOnly(libs.logback.classic)` in
    `modules/document/build.gradle.kts` and `modules/document/src/test/resources/logback-test.xml` as design.md D8
    describes, then `./gradlew resolveAndLockAll --write-locks`.
  - **Test first:** in `DocumentServiceTest`, following `write_documentIdNeverOpened_returnsInternalErrorWithCause`
    and `DocumentServices.newService()`: releasing an opened TXT book returns `true` and then `false`; releasing an
    opened EPUB, FB2 and Markdown book each returns `true`; writing a released Markdown book to `Book.uk.md` returns
    `ErrorCode.internal` and creates no file.
  - **Log:** DEBUG on entry to `open` (path and resolved format), `write` (document id, format, destination, target
    language, number of segments carrying a translation), `unmask` (format, segment id, token count) and `close`
    (document id, format), and DEBUG with each call's outcome, success or the error code; TRACE with `unmask`'s input
    and restored text. The existing WARN and ERROR lines stay where their errors are built.
  - **Rules:** `.claude/rules/document-roundtrip.md`.
  - **Done when:** `./gradlew :document:build` is green, including the golden round-trip tests, `FormatDispatchTest`
    and the 0.80 branch-coverage gate, and
    `BOOKLOOM_LOG_LEVEL=TRACE ./gradlew :document:test --tests 'ua.bookloom.document.DocumentServiceTest' --rerun`
    writes those lines to `modules/document/build/test-logs/test.log`.

## 2. Pseudo model

- [x] 2.1 Add the pseudo model and the chat-model factory to `:llm`, and bind the factory in `LlmModule`. The command line must get its model the way a configured provider will be selected later, and a book in capitals is easy to recognise while every real step around the model runs. → `:llm`
  - **Read:** `specs/inference/spec.md` (both requirements); design.md D1 and D8; ADR-0033;
    `modules/llm/src/main/java/module-info.java`; `modules/llm/src/main/java/ua/bookloom/llm/LlmModule.java`;
    `modules/llm/build.gradle.kts`.
  - **Change:** `ua.bookloom.llm.pseudo.PseudoChatModel`, in a package that is neither exported nor opened, with its
    `package-info.java`. It replies with the last user message upper-cased with `Locale.ROOT`, copies every `⟦g\d+⟧`
    token and every `&name;`, `&#N;` and `&#xH;` reference unchanged, and finishes `STOP`.
    `ua.bookloom.llm.ChatModelFactoryImpl` returns a `PseudoChatModel` for the provider id `pseudo`, and
    `ErrorCode.validation` for any other provider id or a blank model id. `LlmModule` binds
    `ChatModelFactory` to `ChatModelFactoryImpl`. Add `implementation(libs.slf4j.api)` and `requires org.slf4j;`, the
    test logging from design.md D8 (`testRuntimeOnly(libs.logback.classic)` and
    `src/test/resources/logback-test.xml`), then `./gradlew resolveAndLockAll --write-locks`.
  - **Test first:** in `modules/llm/src/test/java/ua/bookloom/llm/`, one test per scenario of
    `specs/inference/spec.md`: `pseudo`/`uppercase` answers `hello` with `HELLO`; `ollama`/`qwen3:8b` and a blank
    model id give `ErrorCode.validation`; `Tom ⟦g0⟧ran⟦g1⟧ &amp; hid&nbsp;&#160;&#xA0;⟦g12⟧` keeps its tokens and
    references; with the default locale `tr`, `title` still becomes `TITLE` (set and restore `Locale.setDefault` in
    `try`/`finally`, and mark the test `@ResourceLock(Resources.LOCALE)`); only the last user message is answered; an
    empty message gives an empty reply. Also: `Guice.createInjector(new LlmModule()).getInstance(ChatModelFactory.class)`
    resolves.
  - **Log:** DEBUG in `create` with the provider id, the model id and the result or error code; DEBUG in `chat` with
    the message count and the last user message's length; TRACE with the last user message and the reply.
  - **Rules:** `.claude/rules/llm-provider-integration.md` applies only in part: there is no HTTP client yet, so no
    WireMock test, no `liveLocal` case and no `InferenceGate`.
  - **Done when:** `./gradlew :llm:build` is green, which switches on the 0.80 branch-coverage gate for `:llm`, and
    the TRACE lines appear in `modules/llm/build/test-logs/test.log`.

## 3. Translation engine

- [x] 3.1 Add the `:pipeline` test scaffolding and `SegmentTranslator`, which builds the prompt, restores the segment's whitespace and makes every reply decision of design.md D4 through the real `DocumentPort.unmask`. Every accept, flag and stop decision of a job is made here, so a wrong row corrupts or loses a paragraph. → `:pipeline`
  - **Read:** `specs/translation-pipeline/spec.md` "Send each pending segment to the model in document order",
    "Accept a translation whose markup restores", "Flag a segment the model could not translate, and continue" and
    "Stop the job on any other failure"; design.md D2, D4 and D8; `modules/pipeline/build.gradle.kts`;
    `modules/pipeline/src/main/java/module-info.java`; the `:document` test builders whose shape to copy, because tests
    cannot import them: `modules/document/src/test/java/ua/bookloom/document/epub/EpubZipBuilder.java` and
    `modules/document/src/test/java/ua/bookloom/document/fixture/{Fb2Fixtures,MarkdownFixtures,TxtFixtures}.java`.
  - **Change:** test helpers in `modules/pipeline/src/test/java/ua/bookloom/pipeline/`: `TestBooks`, which writes
    into a `@TempDir` an EPUB with given spine documents, paragraphs and `dc:language`, an FB2 and a zipped FB2 with a
    `title-info` `lang`, a Markdown book and a TXT book; `ScriptedChatModel`, which answers queued
    `Result<ChatResponse>` values or throws, and records every request. Tests use the real `DocumentPort` from
    `Guice.createInjector(new DocumentModule())`. Main code: the package-private `SegmentTranslator` in
    `ua.bookloom.pipeline`. Build: `implementation(libs.slf4j.api)`, `requires org.slf4j;`, the test logging from
    design.md D8, then `./gradlew resolveAndLockAll --write-locks`.
  - **Test first:** the exact system and user messages with a source language, without one, and with a requested
    `de` that beats a declared `en`. A segment of two spaces, `Hello world` and a line feed, answered with a line
    feed, `HELLO WORLD` and two spaces, becomes two spaces, `HELLO WORLD` and a line feed. A `@ParameterizedTest`
    table for the Markdown segment `He opened the ⟦g0⟧old⟦g1⟧ door.`: `STOP` with both tokens is ACCEPTED as
    `HE OPENED THE *OLD* DOOR.`; a missing `⟦g1⟧` is FLAGGED `validation`; `""`, and two spaces with a line feed, are
    FLAGGED `emptyCompletion`; `LENGTH` and `OTHER` are FLAGGED `validation`; the errors `validation`,
    `emptyCompletion` and `contextWindow` are FLAGGED with that code; `cancelled` stops as cancelled; `unreachable`,
    `auth` and a thrown exception (as `internal`) stop as failed; an `unmask` returning `internal`, through a
    delegating `DocumentPort`, stops as failed.
  - **Log:** DEBUG per segment with its id, the reply kind, the finish, the decision and its error code; WARN for a
    FLAGGED segment with its id, code and the expected and observed tokens; ERROR once, with the cause, for a model
    call that throws; TRACE with the system and user messages, the raw reply, the trimmed text and the restored text.
  - **Done when:** `./gradlew :pipeline:build` is green, and
    `BOOKLOOM_LOG_LEVEL=TRACE ./gradlew :pipeline:test --tests '*SegmentTranslatorTest' --rerun` shows the decision,
    warning and message lines in `modules/pipeline/build/test-logs/test.log`.
- [x] 3.2 Add `BookExporter`, which re-opens the source, applies the job's decisions, writes a hidden temporary file, re-opens it to compare segment counts, and only then moves it onto the destination. Writers change the opened tree, so this is what makes a retried export safe and keeps a broken book from replacing a good one. → `:pipeline`
  - **Read:** `specs/export/spec.md` (both requirements); design.md D5 and D8;
    `docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#lock-and-atomic`; `open`, `write` and `close`
    in `modules/api/src/main/java/ua/bookloom/api/document/DocumentPort.java`.
  - **Change:** the package-private `BookExporter`. It re-opens the source and compares `contentHash` with the one
    read at the start (`ErrorCode.validation` when they differ); applies the decisions with `Segment.withDecision`,
    `Unit.withSegments` and `Document.withUnits`; writes `.<destination file name>` beside the destination; re-opens
    that file and compares segment counts (`ErrorCode.validation` when they differ); closes both books; checks a
    cancellation flag the job supplies just before the move; moves without replacing, or, when overwrite is on, with
    `ATOMIC_MOVE` and `REPLACE_EXISTING`, falling back to `REPLACE_EXISTING` on `AtomicMoveNotSupportedException`;
    and deletes the temporary file on every failure.
  - **Test first:** one test per scenario of `specs/export/spec.md`: an EPUB paragraph
    `<p>Tom &amp; <i>Jerry</i> ran.</p>` with an ACCEPTED decision restored by `unmask` from
    `TOM & ⟦g0⟧JERRY⟦g1⟧ RAN.` is written as `<p>TOM &amp; <i>JERRY</i> RAN.</p>` with `dc:language` `uk`; an FB2
    `<lang>en</lang>` becomes `<lang>uk</lang>`; a FLAGGED Markdown paragraph keeps `He opened the *old* door.`; a
    delegating `DocumentPort` that reports one segment fewer for the temporary file leaves an existing `Book.uk.md`
    unchanged and no temporary file; a destination folder deleted before the write leaves nothing behind; changed
    source bytes give `ErrorCode.validation`; overwrite on replaces `Book.uk.md`; overwrite off never does.
  - **Log:** DEBUG for each step with its values: source path and document id, hash match, accepted and flagged
    counts applied, temporary path, write result, both segment counts, move mode, temporary file deleted. WARN for a
    changed source, a count mismatch and an atomic-move fallback.
  - **Done when:** `./gradlew :pipeline:build` is green, and the step lines appear at DEBUG in
    `modules/pipeline/build/test-logs/test.log`.
- [x] 3.3 Add `TranslationJobImpl`: the job's states and boundaries, the four pause points with one pause per boundary, pause-point changes, pause and cancel before `run()`, cancellation including an interrupt while paused, ordered events on the job's thread, and the job's lifecycle logging with its MDC. A screen will drive pause, resume and cancel from another thread, so a wrong wait, or a lock held while an event is sent, hangs the app. → `:pipeline`
  - **Read:** `specs/resume/spec.md` (every requirement); `specs/translation-pipeline/spec.md` "Stop the job on any
    other failure" and "Report progress and the outcome"; design.md D2, D3 and D8; ADR-0033;
    `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`.
  - **Change:** the package-private `TranslationJobImpl`, using `SegmentTranslator` and `BookExporter`: a
    `ReentrantLock` with a `Condition` and no `synchronized`; PAUSED set before `Paused` is sent;
    `while (paused && !cancelRequested) await()`; control methods that never wait for the job; events sent on the
    job's thread outside the lock; a subscriber that throws logged once and removed; package-private helpers wherever
    the class would pass 400 lines or a method 30.
  - **Test first:** one test per scenario of `specs/resume/spec.md` and of "Report progress and the outcome", with
    `TestBooks` (EPUBs whose spine documents hold 2 and 1, and 0 and 2 segments, for the after-section scenarios) and
    `ScriptedChatModel`. For "The export fails once", a subscriber deletes the destination folder on the export
    `StageStarted`, and the test creates it again before resuming. Also a `resume()` from a second thread, and a
    `resume()` inside a `Paused` callback that completes within `assertTimeoutPreemptively`.
  - **Log:** MDC `job` for the whole run and `segment` while a segment is decided, both removed in `finally`; INFO at
    the start (format, source, destination, languages, pause points, segment and section counts) and the end (end
    state, counts, written file); DEBUG for every state transition, boundary check, pause-point hit, pause-point
    change, subscription and event sent; WARN for a pause on error, a job ending Failed and a subscriber that threw;
    ERROR once, with the cause, for an unexpected exception caught at `run()`'s boundary.
  - **Rules:** `.claude/rules/threading-concurrency.md`.
  - **Done when:** `./gradlew :pipeline:build` is green, and a TRACE run of the pause tests shows the state transitions
    in order in `modules/pipeline/build/test-logs/test.log`.
- [x] 3.4 Add `TranslationEngineImpl` with its request checks, bind it in `PipelineModule`, and prove the whole job end to end on generated books in every format. This joins the parts into the one entry point the command line and the future screen call. → `:pipeline`
  - **Read:** `specs/translation-pipeline/spec.md` "Refuse a job that cannot start"; `specs/export/spec.md`;
    design.md D2 ("Checks") and D8; `modules/pipeline/src/main/java/ua/bookloom/pipeline/PipelineModule.java`;
    `modules/document/src/main/java/ua/bookloom/document/DocumentModule.java`.
  - **Change:** the public `TranslationEngineImpl`, with `@RequiredArgsConstructor(onConstructor_ = {@Inject})`
    injecting `DocumentPort`, whose `newJob` applies design.md D2's checks (the language pattern, the same
    `BookFormat`, a destination that is not the source) and creates a `TranslationJobImpl`; `PipelineModule` binds
    `TranslationEngine` to `TranslationEngineImpl`.
  - **Test first:** end-to-end tests on
    `Guice.createInjector(new DocumentModule(), new LlmModule(), new PipelineModule())` with the `pseudo`/`uppercase`
    model from `ChatModelFactory`, on `TestBooks`: an EPUB (`<i>` kept, `dc:language` `uk`), an FB2 and a zipped FB2
    (`lang` `uk`), a Markdown book with emphasis and a TXT book, each ending Completed and re-opening with the
    source's segment count. Refusals, each `ErrorCode.validation` with no model call: the target language `../x`, a
    destination of another format, a destination that is the source with overwrite on, an existing destination with
    overwrite off, and a second `run()`. The stop scenarios of "Stop the job on any other failure" with
    `ScriptedChatModel`.
  - **Log:** DEBUG in `newJob` with the request (source, destination, languages, overwrite) and each check's result;
    WARN for a refused request, naming the check that refused it.
  - **Done when:** `./gradlew :pipeline:build` and `./gradlew :app:archTest` are green, and one end-to-end test run at
    TRACE shows a segment's messages, reply and decision in `modules/pipeline/build/test-logs/test.log`.

## 4. Command line

- [x] 4.1 Share one set of Guice modules between the desktop app and the command line, and add the log-level switch and the job id in the log pattern to `LoggingBootstrap`. The desktop app and the command line must never wire different graphs, and DEBUG and TRACE lines are useless while the level is fixed at INFO. → `:app`
  - **Read:** `specs/translation-pipeline/spec.md` "Choose the log level for a run"; design.md D7 and D8;
    `modules/app/src/main/java/ua/bookloom/app/{BookLoomApplication,AppModule,StartupContext,AppLifecycle}.java`;
    `modules/app/src/main/java/ua/bookloom/app/bootstrap/{Launcher,LoggingBootstrap}.java`;
    `modules/util/src/main/java/ua/bookloom/util/paths/AppEnvironment.java`, the model for a pure resolver over
    `getEnv` and `getProperty`; `modules/app/src/test/java/ua/bookloom/app/AppBootSmokeTest.java`;
    `modules/app/src/test/java/ua/bookloom/app/bootstrap/LoggingBootstrapTest.java`, which shows how to read the log
    file and reset the logging context.
  - **Change:** `ua.bookloom.app.CoreModules` with `AppModule`, `DocumentModule`, `LlmModule`, `PersistenceModule`
    and `PipelineModule`, and `BookLoomApplication.init` building from it plus `UiModule`. In
    `ua.bookloom.app.bootstrap`, a pure level resolver: `BOOKLOOM_LOG_LEVEL`, then `bookloom.log.level`, in any letter
    case, else `INFO` for `PROD` and `DEBUG` for `DEV`. `LoggingBootstrap.configure` takes the level, sets it on the
    `ua.bookloom` logger and `WARN` on the root, and adds `%X{job}` to the pattern. After configuring, it writes one
    INFO line naming the level and its source, and one WARN line naming a rejected value. `Launcher` passes the
    resolved level.
  - **Test first:** `AppBootSmokeTest.injector_afterBoot_suppliesTheApplicationScopedBindings` also resolves
    `TranslationEngine` and `ChatModelFactory`. In `LoggingBootstrapTest`: `trace` writes a TRACE line from a
    `ua.bookloom` logger; `LOUD` falls back to the default and writes a WARN line holding `LOUD`; the `DEV` default
    writes DEBUG and not TRACE; an INFO line from a logger outside `ua.bookloom` is not written. The resolver's
    precedence as a `@ParameterizedTest`.
  - **Log:** the INFO configuration line and the WARN rejected-value line above.
  - **Rules:** `.claude/rules/logging.md`: only `LoggingBootstrap` imports Logback types, and nothing on the bootstrap
    path holds a static logger.
  - **Done when:** `./gradlew :app:build` is green, and `./gradlew :app:run` still opens the window and writes the
    configuration line to the dev log folder (on macOS `~/Library/Logs/BookLoom-Dev/bookloom.log`).
- [x] 4.2 Add `bootstrap.TranslateLauncher`, `cli.TranslateCommand` and the Gradle `translate` task, logging every argument, decision and exit code. This is how a real book gets translated, and explained, before any screen exists. → `:app`
  - **Read:** `specs/translation-pipeline/spec.md` "Translate one book from the command line", "Report command-line
    failures with exit codes" and "Write a diagnostic log of every run"; design.md D7 and D8;
    `modules/app/src/main/java/ua/bookloom/app/bootstrap/{Launcher,SingleInstanceLock}.java`;
    `modules/app/src/test/java/ua/bookloom/app/bootstrap/SingleInstanceLockTest.java`, which shows how a test holds
    the lock; `modules/util/src/main/java/ua/bookloom/util/paths/AppPathsResolver.java`, where `BOOKLOOM_DATA_DIR`
    sets the data folder and logs go to its `logs/`; `modules/app/src/main/java/module-info.java`; the `run` task in
    `modules/app/build.gradle.kts`.
  - **Change:** `TranslateLauncher`, whose `main` calls
    `System.exit(run(args, System::getenv, System::getProperty, System.out))` and whose `run` follows design.md D7's
    five steps with no static logger. `TranslateCommand` in the new package `ua.bookloom.app.cli`, with its
    `package-info.java`, opened to `com.google.guice` and not exported: it parses
    `<book> [--to <lang>] [--from <lang>] [--overwrite]`, returns 2 for invalid arguments, names the output
    `<name>.<to><suffix>` beside the book, runs the job with `ModelSelection("pseudo", "uppercase")` and no pause
    points, prints one report line or the error's title and message, and returns 0 or 1. The `translate` `JavaExec`
    task in `modules/app/build.gradle.kts`, set up like `run`, with `workingDir = rootDir`.
  - **Test first:** `TranslateCommandTest` in `@TempDir`: `Book.md` holding `He opened the *old* door.` gives
    `Book.uk.md` holding `HE OPENED THE *OLD* DOOR.`, exactly one printed line and exit 0; `Book.fb2.zip` gives
    `Book.uk.fb2.zip`; an existing `Book.uk.md` without `--overwrite` gives exit 1 and stays unchanged; a `Book.epub`
    that is not a ZIP gives exit 1 with the error's title and message; `--bogus`, a folder, a missing path,
    `Book.pdf` and `--to ../x` each give exit 2 and no file. `TranslateLauncherTest` with a temporary
    `BOOKLOOM_DATA_DIR`, resetting the logging context after each test: a held lock gives exit 1 without translating;
    with `BOOKLOOM_LOG_LEVEL=TRACE`, `logs/bookloom.log` holds the INFO start and end lines, a DEBUG line with
    `Book.md:0` and `ACCEPTED`, and TRACE lines holding `He opened the ⟦g0⟧old⟦g1⟧ door.` and
    `HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.`; with `INFO` it holds no DEBUG line and no `He opened`.
  - **Log:** INFO for the command's arguments, resolved paths, log level and exit code; DEBUG for the parsed options,
    the output path and the report; WARN for every exit-2 reason.
  - **Rules:** `bootstrap-no-static-logger` in `.claude/rules/architecture-layering.md`.
  - **Done when:** `./gradlew :app:build` is green, and
    `BOOKLOOM_LOG_LEVEL=TRACE ./gradlew -q :app:translate --args="<a small .md book>"` writes the translated book
    beside it and its log lines to the dev log folder.

## 5. Documentation

- [ ] 5.1 Update the specification clauses this change outgrows and the project documents that describe the code. Stale docs send the next reader down the wrong path. → docs
  - **Read:** design.md; ADR-0033; the code as built.
  - **Change, specification:**
    - `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`: `TranslationJob` instead of
      `JobHandle`, segment boundaries, pause points, and a cancel that ends the job with a Cancelled report instead
      of `ErrorCode.cancelled`.
    - `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#module-api`, `#module-llm` and
      `#module-pipeline`: the two new `:api` packages, and the `:llm` and `:pipeline` module declarations as built.
    - `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-architecture` and `#chat-contracts`: the
      engine-facing `ChatModel` in front of the future `Provider`, and a `ChatRequest` without a model name whose
      per-call hints are nullable components.
    - `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`: the job report.
    - `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`: the engine's `JobProgress`, and
      how the screen's snapshot will be built from it.
    - `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#guice-modules`: `ChatModelFactory` in `LlmModule`,
      `TranslationEngine` in `PipelineModule`, and `CoreModules` shared with the command line.
  - **Change, project documents:**
    - `docs/Architecture.md`: §1 and §2 (the modules that are now real), §3 (the new contracts, and `close` in §3.3),
      §5 (the command line and the log-level switch), §6, §7 and §9.
    - `docs/DEVELOPMENT.md`: §3 (`:llm` and `:pipeline` are no longer empty) and §5 (`./gradlew -q :app:translate`,
      its exit codes beside the desktop app's, `BOOKLOOM_LOG_LEVEL`, the app log folder and
      `modules/<module>/build/test-logs/test.log`).
    - `AGENTS.md`: the where-it-stands paragraph.
    - `docs/implementation_plan/CHANGE_BACKLOG.md`: `#where-this-stands`; the Stage C rows for `inference`,
      `translation-pipeline`, `resume` and `export` change from NEW to MOD, noting that the job lifecycle and
      FR-EXPORT-03 were delivered here; `#decision-debt` D3 resolved; a new entry for logging inside `:document`'s
      readers, writers and maskers.
  - **Done when:** every edited citation resolves to a heading or `{#anchor}` in its file, and
    `OPENSPEC_TELEMETRY=0 openspec validate add-translation-engine-and-cli --strict` is clean.

## 6. Gate

- [ ] 6.1 Update `docs/implementation_plan/01_MODULE_INVENTORY.md` with the new packages in `:api` and `:app` and the new classes in `:llm` and `:pipeline`, so the module map matches the code. → docs
  - **Read:** `docs/implementation_plan/01_MODULE_INVENTORY.md` `#module-api`, `#module-llm`, `#module-pipeline`,
    `#module-app` and its as-built status section.
  - **Change:** `#module-api`: `ua.bookloom.api.llm` and `ua.bookloom.api.pipeline` as real, keeping
    `Provider`/`ProviderFactory` under one owner instead of listing them twice; `#module-llm`: `ChatModelFactoryImpl`
    and `ua.bookloom.llm.pseudo`; `#module-pipeline`: `TranslationEngineImpl`, `TranslationJobImpl`,
    `SegmentTranslator` and `BookExporter`; `#module-app`: `CoreModules`, `bootstrap.TranslateLauncher` and
    `ua.bookloom.app.cli`; and the as-built status.
  - **Done when:** every row names a package or class that exists.
- [ ] 6.2 Run `./gradlew clean build check spotlessCheck` and `./gradlew -PstrictLocks verifyLocks` and get both green across the whole project, with no pre-existing-failure exemption, including the ArchUnit rules and the 0.80 branch-coverage gate that now applies to `:llm` and `:pipeline`. A change is not done while any check anywhere is red. → all modules
  - **Done when:** both commands pass, and the gate's tail is pasted as evidence.
- [ ] 6.3 Translate one real EPUB, FB2, Markdown and TXT book from `.temporary_context/Books_Examples` with `./gradlew -q :app:translate --args="<book>"`, open each result in a reader, and follow one segment through the log of a TRACE run. A result that opens correctly, and a log that explains every flag and failure, are the evidence the feature works; a ticked checkbox is not. → `:app`
  - **Do:** run one of the books with `BOOKLOOM_LOG_LEVEL=TRACE`, and follow one segment from its prompt to its
    decision in `bookloom.log` in the dev log folder (on macOS `~/Library/Logs/BookLoom-Dev/`).
  - **Expect:** the text in capitals, with formatting, images and structure intact. A TXT book in a legacy encoding
    can fail when upper-casing leaves its charset (`µ` becomes `Μ` and `ÿ` becomes `Ÿ`, neither in ISO-8859-1);
    record the cause of every failure and flag from the log.
