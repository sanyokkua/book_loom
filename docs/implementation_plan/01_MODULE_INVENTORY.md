**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-09-29
**Cross-references:** `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md`, `docs/Architecture.md`

# Module Inventory

This is the **canonical, authoritative list of Gradle/JPMS modules and their package sub-paths**. Every OpenSpec
change's proposal and `tasks.md` cite entries from this file; a citation that does not appear here is a review failure
(nothing checks it mechanically — ADR-0016). The module boundaries, allowed dependency edges, and ArchUnit rules that
govern this inventory are defined in `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`.

## how-to-cite-a-module {#how-to-cite-a-module}

Cite a module path as `:<module>/<package>` — the Gradle subproject name, a slash, and the fully-qualified package.
Examples: `:document/ua.bookloom.document.epub`, `:llm/ua.bookloom.llm.provider`, `:pipeline/ua.bookloom.pipeline.qa`,
`:persistence/ua.bookloom.persistence.dao`, `:ui/ua.bookloom.ui.screen`. A story that touches only a module root cites
`:<module>/ua.bookloom.<module>`.

**This convention is unaffected by where the directories physically sit.** The nine code directories moved under
`modules/` (ADR-0021) — the sources for `:document` are at `modules/document/src/main/java/…` — but a citation names
the **Gradle project**, not a filesystem path, and every Gradle project name is unchanged: `settings.gradle.kts`
repoints each `projectDir` explicitly rather than nesting the project paths, so `:document` is still `:document` and
not `:modules:document`. No citation anywhere in the corpus went stale.

## layers {#layers}

| Layer         | Modules                             | Rule                                                                      |
|---------------|--------------------------------------|-----------------------------------------------------------------------------|
| Foundation    | `:api`, `:util`                     | No internal deps except `:util → :api`. Framework-free. FX-free.          |
| Services      | `:document`, `:llm`, `:persistence` | Depend only on `:api`, `:util`. FX-free. Implement `:api` ports.          |
| Orchestration | `:pipeline`                         | Depends on `:document`, `:llm`, `:persistence`, `:api`, `:util`. FX-free. |
| Presentation  | `:ui`, `:app`                       | The only modules that may `requires javafx.*`.                            |

## test-conventions {#test-conventions}

- **Source layout:** production code in `src/main/java/<package-path>`, tests in `src/test/java/<package-path>`
  mirroring the production package. UI tests live in `:ui/src/test/java/...`.
- **ArchUnit** boundary tests live in the shared `arch-test` source set — physically `:app/src/archTest/java`, because
  `:app` is the only module whose compile classpath sees all eight modules at once. The `archTest` task is wired into
  `:app:check`, so the rules run in the whole-project gate, in `pre-push`, and in CI (see `#as-built-baseline`).
- **Test tiers:**
    - **Unit** — pure JVM, no I/O, Mockito/AssertJ. Runs in `pre-push`.
    - **Integration** — temp SQLite DB file or `:memory:`; WireMock for the LLM HTTP seam; golden-fixture round-trip for
      documents. CI + on demand.
    - **UI** — TestFX on JavaFX 26's built-in headless platform (ADR-0019). Runs in `check`, so in pre-push and CI.
    - **Arch** — ArchUnit boundary assertions.
- A covering test is named `method_state_expected`; a one-line comment says what it proves when the name is not
  enough. No requirement-id markers (ADR-0032).

## adding-a-module-note {#adding-a-module-note}

**New modules and new package sub-paths are added to this file in the same change that introduces them.** A change
that creates a package must (a) add the row here and (b) name it in its proposal's Impact section. Updating the
inventory is an item in every change's final green-gate task group, so the inventory update and the code land
together.

## as-built-baseline {#as-built-baseline}

**The `#inventory` tables below are the planned, forward-looking map — the citation targets later changes point at.
This section records what the `bootstrap-gradle-and-quality-toolchain` change actually created**, so a proposal can
tell an existing package from one it must still author.

**Eight subprojects exist and compile**, each a JPMS module named `ua.bookloom.<module>` with a hand-written
`module-info.java` declaring only the edges `#layers` allows: `:api :util :document :llm :pipeline :persistence :ui
:app`. A ninth requires an ADR. Only `:ui` and `:app` `requires javafx.*`.

Each carries **exactly one placeholder public type**, named from this inventory rather than invented, plus a
`@NullMarked` (JSpecify) `package-info.java` — enough to make the module non-empty so the toolchain has something to
compile, lint, and check.

**This section is a chronological log, one block per archived change.** Each block records what that change created,
so an earlier block is a dated snapshot, not a claim about today — read to the end for the current position. As of
`bootstrap-app-launch-and-empty-window`, six of the eight were still placeholders and two were not:

| Module         | Package                     | Placeholder type                                          |
|----------------|-----------------------------|-------------------------------------------------------------|
| `:api`         | `ua.bookloom.api`           | **No longer a placeholder** — see below                   |
| `:util`        | `ua.bookloom.util.paths`    | **No longer a placeholder** — see below                   |
| `:document`    | `ua.bookloom.document`      | `DocumentModule` (Guice `AbstractModule`)                 |
| `:llm`         | `ua.bookloom.llm`           | `LlmModule`                                                |
| `:pipeline`    | `ua.bookloom.pipeline`      | `PipelineModule`                                           |
| `:persistence` | `ua.bookloom.persistence`   | `PersistenceModule`                                        |
| `:ui`          | `ua.bookloom.ui`            | `UiModule`                                                  |
| `:app`         | `ua.bookloom.app`           | `AppModule` (the composition root's own Guice module)      |

**What `bootstrap-app-launch-and-empty-window` filled in.** The application now starts: a window opens, a second
launch is refused, and a packaged image launches on the module path.

| Module  | Package                          | What it now holds                                                                                                                                                                                             |
|---------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:api`  | `ua.bookloom.api`                | **Seam F2, complete**: `Result<T>`, `AppError`, the fifteen-constant `ErrorCode` (retryability derived on the enum), and `SafeDetails` — a record whose *component set* is the safe-details allowlist          |
| `:util` | `ua.bookloom.util.paths`         | `AppPaths` (record), `AppPathsResolver`, `AppEnvironment` (dev/prod), `OsFamily`. Resolution is a pure function over injected `getEnv`/`getProperty`; `prepare` does the I/O                                    |
| `:app`  | `ua.bookloom.app`                | `BookLoomApplication`, `AppModule` (real bindings), `AppLifecycle` (two-phase init, ordering **enforced**), `AppVersion`, `StartupContext`                                                                     |
| `:app`  | **`ua.bookloom.app.bootstrap`**  | **New package, and a constraint rather than a folder**: `Launcher`, `SingleInstanceLock`, `LoggingBootstrap`, `StartupFailureDialog`. ArchUnit rule 8 scopes to it, so membership *is* the pre-logging declaration |
| `:ui`   | `ua.bookloom.ui`                 | `Theme` (resolves the single stylesheet from inside the module, since JPMS encapsulates it), `AppShellView`, and `theme.css`                                                                                    |

`Launcher` lives in `ua.bookloom.app.bootstrap`, not `ua.bookloom.app`, because the ArchUnit rule that forbids a
static logger on the pre-logging path identifies that path by **package**, not by class name. `#inventory`'s
`### :app` section carries both rows and is correct as written.

**What `add-document-skeleton-and-epub-roundtrip` filled in.** The first change with a real, user-observable
capability: an EPUB survives being parsed apart and reassembled with nothing translated (`document-round-trip`,
EPUB only — FB2/Markdown/TXT extend it next).

| Module      | Package                            | What it now holds                                                                                                                                                                                                                                                                     |
|-------------|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:api`      | `ua.bookloom.api.document`          | Seam F1, complete in shape: `BookFormat`, `SegmentKind`, `SegmentStatus`, `SkeletonAnchor`, `Segment`, `SkeletonHandle`, `Unit`, `Document` (records/enums only — `masked`/`placeholders` shipped empty here and were filled in by change 5's masker; `detectedSourceLang` still ships empty, filled by a later change per design.md D2) and `DocumentPort` |
| `:util`     | `ua.bookloom.util.hash`             | `HashUtil` — SHA-256 over raw bytes (document content hash) and over NFC-normalized text (per-segment `sourceHash`)                                                                                                                                                                  |
| `:document` | `ua.bookloom.document`              | `DocumentService` (the `DocumentPort` impl and the module's port boundary — classifies every EPUB read/write failure into a typed `Result`/`AppError`), `DocumentModule` (now binds `DocumentPort` → `DocumentService`)                                                              |
| `:document` | `ua.bookloom.document.model`        | `BlockSegmentWalker` (format-agnostic segment emission, reused by the next change's FB2/Markdown/TXT importers) and `SkeletonAnchors` (index-path anchor computation and write-back resolution) — seam F1's implementation                                                          |
| `:document` | `ua.bookloom.document.epub`         | `EpubReader`/`EpubWriter` (container/OPF/spine/XHTML read via jsoup+JDOM2, DRM adjudicated first per design.md D7, `mimetype`-first/STORED repackaging), `OpenEpubRegistry` (in-memory parsed-tree registry keyed by document id, resolving each `SkeletonHandle` back to its real tree per design.md D1) |

Adds jsoup and JDOM2 (both allowlisted licenses) to `:document` only. The golden round-trip test and its
adversarial fixture (`modules/document/src/test/java/ua/bookloom/document/golden/`) are this change's own
acceptance gate, not part of the shipped module surface, so they aren't listed as a row above.

**What `add-fb2-md-txt-roundtrip` filled in.** The round trip now covers all four formats FR-IMPORT-01 promises,
and `ua.bookloom.document` finally performs the format dispatch its `#inventory` row has always claimed.

| Module      | Package                        | What it now holds                                                                                                                                                                                                                                                                        |
|-------------|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:api`      | `ua.bookloom.api.document`     | `SkeletonAnchor` is now a **sealed interface** over `NodeAnchor(nodePath, runIndex)` and `ByteSpanAnchor(start, end)` (ADR-0025); `Document` gains nullable `charset`/`hasBom`, unset for container formats                                                                             |
| `:document` | `ua.bookloom.document`         | `DocumentService` now **dispatches**: `FormatResolver` resolves the format from the extension and confirms it against the leading bytes, and both `open` and `write` switch exhaustively over `BookFormat`                                                                              |
| `:document` | `ua.bookloom.document.model`   | The shared structural walker (ADR-0027) over a `TreeNode` adapter with `JsoupTreeNode`/`Jdom2TreeNode` implementations, `BlockRuns`, `SegmentKinds`, run-scoped `SkeletonAnchors`, `BufferReassembler`, plus `ZipEntryReader`/`RawEntry`/`ZipEncryption`/`SecureXml` and the three shared exceptions, moved here so one bounded zip reader serves both container formats |
| `:document` | **`ua.bookloom.document.detect`** | **New package**: `CharsetLadder` — byte-order mark, then in-band declaration, then ICU detection, then UTF-8                                                                                                                                                                          |
| `:document` | **`ua.bookloom.document.fb2`**    | **New package**: `Fb2Reader`/`Fb2Writer`, `Fb2Encoding` (EC-FB2-2 adjudication over the document's prose), `Fb2Metadata`, `ParsedFb2`, `OpenFb2Registry`                                                                                                                              |
| `:document` | **`ua.bookloom.document.md`**     | **New package**: `MarkdownReader`/`MarkdownWriter`, `MarkdownWalker`, `MarkdownSpans`, `Frontmatter`, `ParsedMarkdown`, `OpenMarkdownRegistry`                                                                                                                                        |
| `:document` | **`ua.bookloom.document.txt`**    | **New package**: `TxtReader`/`TxtWriter`, `ParagraphScanner`, `OpenTxtRegistry`                                                                                                                                                                                                       |
| `:document` | `ua.bookloom.document.epub`    | `DrmAdjudicator` rewritten to decide from the encrypted resource (ADR-0026) with `FontMediaTypes` and a top-level `ManifestItem`; the writer preserves each entry's original compression method                                                                                        |

Adds commonmark-java, its GFM tables extension and ICU4J to `:document` only. ICU4J's licence is a **recorded
exception** (`config/license/allowed-licenses.json`), extended here with the `Unicode-3.0` spelling ICU now
publishes — the same permissive licence under its current SPDX name, which
`05_Dependencies/03_LICENSING.md` anticipates as a normalization alias rather than a new exception.

Test-side, `modules/document/src/test/java/ua/bookloom/document/fixture/` holds every fixture builder and the
`FixtureCatalog` whose declared expectations gate them; `golden/` holds the per-format comparisons, the text-coverage
measurement and the catalogue sweep. Neither is part of the shipped module surface, so neither is a row above.

**What `fix-document-round-trip-corpus-defects` filled in.** The first change driven by measurement rather than by a
backlog entry: a sweep over 214 real books wrote every one of them back out — 207 of 208 writable books survived a
full text-replacement round trip byte-exactly across 626,276 segments — and found eight defects the twelve
hand-authored fixtures could not. It added no package; it corrected behaviour inside the existing ones and made the
sweep repeatable.

| Module      | Package                      | What changed                                                                                                                                                                                                                                  |
|-------------|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:document` | `ua.bookloom.document.epub`  | `XhtmlParser` normalizes a self-closed `<script/>`/`<style/>`/`<noscript/>` before parsing (ADR-0028) — twelve of 194 EPUBs previously imported with **zero segments and no error**; `PreformattedLineFeedRestorer` restores a `<pre>`'s leading line feed |
| `:document` | `ua.bookloom.document.detect`| `CharsetLadder` gains `ForeignWordCoherence` so a Cyrillic single-byte encoding is no longer resolved as Western European, which decoded without error and produced mojibake                                                                  |
| `:document` | `ua.bookloom.document.fb2`   | A malformed translated fragment (a bare `&` or `<` in target text) is classified `ErrorCode.validation` rather than escaping as an unclassified throwable                                                                                     |
| `:document` | `ua.bookloom.document`       | `OpfParser` reads metadata from a legacy OEBPS-1.2 `<dc-metadata>` wrapper, which previously dropped title, author and language silently                                                                                                     |

The corpus sweep itself is now a committed, environment-gated test excluded from CI and `check`; its findings —
including the four it deliberately did **not** fix — are recorded in
`docs/implementation_plan/notes-corpus-verification.md` and carried as decision debt in
`CHANGE_BACKLOG.md#decision-debt`.

**What `add-inline-masking-and-placeholder-gate` filled in (archived 2026-09-11).** `ua.bookloom.document.mask`
(`Placeholders`, `MaskWriter`, `PlaceholderGate`, `Unmasker`, `MarkupEscape`, `FragmentRange`, `RestoredContent`),
`TreeMasker` in `.model`, `MarkdownMasker`/`MarkdownEscaper`/`MarkdownStructureCheck` in `.md`, `PlainTextMasker`,
and `DocumentPort.unmask`. Follow-ups on the same day: `OpenDocumentRegistry<T>` in `.model` replaces the four
per-format registries and adds `close(id)`; `EpubWriter` synthesizes a missing `mimetype` (ADR-0030); `Fb2Writer`
echoes the source's line-ending style. The readable map of all of this is `docs/Architecture.md`.

**What `add-translation-engine-and-cli` filled in (archived 2026-09-20).** The first
change to translate a book, end to end, with a chat model — a deterministic offline `pseudo` model standing in for
a real one — and to give `:llm` and `:pipeline` real production code.

| Module      | Package                    | What it now holds                                                                                                                                                                                                                                                     |
|-------------|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:api`      | `ua.bookloom.api.llm`      | **New, real**: `ChatModel`, `ChatRequest`, `ChatMessage`, `ChatRole`, `ChatResponse`, `FinishReason`, `ChatModelFactory`, `ModelSelection` — the engine-facing seam ADR-0033 fixes in front of the still-future `Provider`/`ProviderFactory`                            |
| `:api`      | `ua.bookloom.api.pipeline` | **New, real**: `TranslationEngine`, `TranslationRequest`, `TranslationJob`, `JobListener`, `Subscription`, `JobState`, `JobStage`, `PausePoint`, `PauseReason`, the sealed `JobEvent` and its records (`StageStarted`, `SegmentDecided`, `Paused`, `Resumed`, `Finished`), `JobProgress`, `JobReport`, `FlaggedSegment` |
| `:api`      | `ua.bookloom.api.document` | `BookFormat` gains its file suffixes (longest first), `ofFileName(String)` and `matchedSuffix(String)`; `Segment.withDecision`, `Unit.withSegments` and `Document.withUnits` copy-with methods; `DocumentPort` gains `close(Document)`                                  |
| `:document` | `ua.bookloom.document`     | `DocumentService.close`, switching on `document.format()` inside the same boundary `try/catch` as `open`/`write`/`unmask`, released through each reader's registry                                                                                                     |
| `:llm`      | `ua.bookloom.llm`          | `ChatModelFactoryImpl` — resolves the provider id `pseudo`, `ErrorCode.validation` for any other provider id or a blank model id                                                                                                                                       |
| `:llm`      | **`ua.bookloom.llm.pseudo`** | **New package, neither exported nor opened**: `PseudoChatModel` — upper-cases the last user message, keeps `⟦gN⟧` tokens and character references unchanged, finishes `STOP`                                                                                          |
| `:pipeline` | `ua.bookloom.pipeline`     | The public `TranslationEngineImpl` (request checks: language pattern, matching `BookFormat`, matching FB2 container, distinct destination); the package-private `TranslationJobImpl` (pause/resume/cancel at segment/section/stage/error boundaries), `SegmentTranslator` (prompt + reply decision) and `BookExporter` (reopen-validate-move) |
| `:app`      | `ua.bookloom.app`          | `CoreModules` — the Guice graph (`AppModule`, `DocumentModule`, `LlmModule`, `PersistenceModule`, `PipelineModule`) the desktop app and the command line share                                                                                                          |
| `:app`      | `ua.bookloom.app.bootstrap` | `TranslateLauncher` — repeats `Launcher`'s pre-injector steps (paths, single-instance lock, logging) without starting JavaFX; `LoggingLevelResolver`/`ResolvedLogLevel` — the `BOOKLOOM_LOG_LEVEL`/`bookloom.log.level` resolver `LoggingBootstrap` now uses            |
| `:app`      | **`ua.bookloom.app.cli`**  | **New package, opened to Guice, not exported**: `TranslateCommand` — parses `<book> [--to <lang>] [--from <lang>] [--overwrite]`, runs one job with `ModelSelection("pseudo", "uppercase")` and no pause points, and reports exit 0/1/2                                |

Also: the `translate` `JavaExec` task in `modules/app/build.gradle.kts`, with `workingDir = rootDir` and the system
property `guice_bytecode_gen_option=DISABLED` (Guice 7.0.0 otherwise reaches `sun.misc.Unsafe`, which JDK 25 reports
on the console of every run). No new third-party dependency: `:llm` and `:pipeline` add `slf4j-api`, and
`:document`/`:llm`/`:pipeline` add `logback-classic` to their test runtime only, both already in the version
catalog (ADR-0033, this change's design.md).

**What `add-real-llm-clients` filled in (archived 2026-09-24).** The offline `pseudo` path now has two real local
provider dialects behind the same chat-model boundary. The CLI can select a model and configured provider, perform its
preflight, and then run the established translation job; provider settings remain process-local and unauthenticated.

| Module      | Package                                  | What it now holds                                                                                                                                                                                                                                    |
|-------------|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:api`      | `ua.bookloom.api.llm`                    | Provider descriptions and lookup (`ProviderKind`, `ProviderConfig`, `ProviderConfigs`), model discovery (`ModelInfo`), structured reply settings (`ResponseFormat`), and three-stage verification contracts. `ChatRequest` now carries nullable temperature and response-format hints while preserving its message-only constructor. |
| `:llm`      | `ua.bookloom.llm` and its provider packages | The in-memory `ollama`/`lmstudio` presets, dialect factory, gated/retried bound chat model, native Ollama and OpenAI-compatible HTTP clients, Jackson wire records, typed HTTP mapping, reply sanitisation, and provider verification. |
| `:pipeline` | `ua.bookloom.pipeline.prompt`            | `DraftPromptBuilder`, `DraftSchema`, and `DraftReplyParser`, used by `SegmentTranslator` to request and read a one-segment structured draft reply.                                                                                                  |
| `:app`      | `ua.bookloom.app.cli`                    | Package-private `TranslateArguments` and the extended `TranslateCommand`: provider/model/base-URL/timeout arguments, run-scoped provider registration, preflight stage output, and the unchanged pseudo default.                              |

**What `add-ui-translation-workspace` filled in (archived 2026-09-26).** The window
now does the work: a book of each of the four formats can be opened, translated and written out from it. Six of the
eight planned `:ui` packages exist.

| Module      | Package                                  | What it now holds                                                                                                                                                                                                                                                                                  |
|-------------|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:ui`       | `ua.bookloom.ui`                         | The shell chrome built in code (`AppShellView`, `NavColumn`, `NavGroup`, `ModalHost`, `AboutDialog`), `Navigator`, `ViewNames`, `Theme`, `UiModule` and the `@BackgroundExecutor`/`@IoExecutor` qualifiers                                                                                          |
| `:ui`       | `ua.bookloom.ui.screen`                  | Import, Book Brief, Structure, Translating, Export and Settings (with its Appearance tab) — FXML plus a thin controller each                                                                                                                                                                       |
| `:ui`       | `ua.bookloom.ui.state`                   | `StateMirror`, `RunSession`, `TranslationRunner` and the viewmodels (import, book brief, translating, settings, model listing); `FileRevealer`/`PlatformFileRevealer`/`OsCommand` show the written file in the operating system's file manager                                                       |
| `:ui`       | `ua.bookloom.ui.i18n`                    | `Messages`, the typed `MessageKey` registry, `LocaleProvider`/`OsLocaleProvider` over the `messages_en` and `messages_uk` bundles. **No in-app language switch**: the operating system's locale chooses                                                                                          |
| `:ui`       | `ua.bookloom.ui.notify`                  | `ErrorDialog`/`ModalErrorPresenter` and the toast surface (`ToastStack`, `Toasts`)                                                                                                                                                                                                                 |
| `:ui`       | `ua.bookloom.ui.theme`                   | `ThemeController`, `ThemeMode` and the OS colour-scheme seam (`ColorSchemeProvider`)                                                                                                                                                                                                                |
| `:api`      | `ua.bookloom.api.llm`                    | `ModelCatalog`, the port a screen uses to list a provider's models without reaching the provider clients                                                                                                                                                                                           |
| `:llm`      | `ua.bookloom.llm`, `ua.bookloom.llm.http` | `ModelCatalogService` implements `ModelCatalog`; `HttpClients` pins the provider client to HTTP/1.1, because the default upgrade attempt on a plain `http://` connection stalled first requests until the timeout                                                                                    |
| `:pipeline` | `ua.bookloom.pipeline`                   | `CancellableChatModel` and the interrupt in `JobControl`: Pause and Stop abort a model request already in flight (the provider client turns the interrupt into `ErrorCode.cancelled`) and refuse to send another one. Export is deliberately not interruptible                                     |
| `:util`     | `ua.bookloom.util.paths`                 | `DestinationPath`, the one rule for naming the written book (`Kobzar.uk.fb2.zip`), shared by the command line and the Book Brief screen                                                                                                                                                            |
| `:app`      | `ua.bookloom.app`                        | `BookLoomApplication` installs `CoreModules` beside `UiModule`; `AppModule` binds the `@BackgroundExecutor` daemon pool and the build version; the window opens at 1024x700 with a 960x640 minimum                                                                                              |

Projects, Names & style and Review are inert (greyed) navigation entries with no screen. `ua.bookloom.ui.view` and
`ua.bookloom.ui.dialog` were not created — the reasons are in the `:ui` section below. Nothing is persisted: no resume
across a restart, no remembered theme or language.

**Where the code actually is, as of the eleventh archived change (`add-ui-translation-workspace`, archived
2026-09-26) plus `complete-translation-workflow` (in flight — its own `tasks.md` group 8 is still open).** All eight
modules carry real production code — `:api`, `:util`, `:document`, `:llm`, `:pipeline`, `:persistence`, `:ui` and
`:app`. `:persistence` binds every `:api` repository port to an in-memory adapter
(`ua.bookloom.persistence.memory`, ADR-0034), proven by the repository contract tests run against each adapter;
nothing survives a restart yet, and SQLite + Flyway + JDBI is still the planned adapter behind the same ports. `:ui`
is real: six of its eight planned packages exist, with FXML screens for import, book brief, structure, translating,
export and settings. Within `:document`, all four formats read **and** write, and **inline masking is now real**:
`BlockSegmentWalker`'s tree-format masking (`TreeMasker`, in `.model`), Markdown's masking (`MarkdownMasker`, in
`.md`) and `TxtReader` each populate a real `masked` form and an ordered `placeholders` map, and
`ua.bookloom.document.mask` holds the format-agnostic `⟦gN⟧` grammar, the mask-time invariant check, the
placeholder gate and the restore pass. `DocumentPort.unmask` runs that gate before restoring a translated
segment; a multiset mismatch, or a pair out of order or nesting (ADR-0040), is `ErrorCode.validation` with no repair
attempt. **`ua.bookloom.document.mask` is real
but `module-info.java` never names it in an `opens`/`exports` clause, and that is correct, not an oversight**: it
holds only static-utility classes and records, no injectable service, so Guice needs no reflective `opens` on it,
and it is consumed only from inside `:document`, so it needs no `exports` either. Within `:llm`, the chat-model
contract, real provider clients, inference gate, retry, and verification are now real. Within `:pipeline`, the draft
prompt and tolerant reply parser feed the translation engine, job and export, and `complete-translation-workflow`
has additionally built and tested prompt templates and a style sheet (`.prompt`), chunk packing and
oversized-segment splitting (`.chunk`), the quality-dial mapping (`.dial`), the stored-project service (`.project`),
the deterministic QA checks (`.qa`), the per-chunk judge (`.judge`) and self-heal (`.heal`), and the run over a
stored project (`.run`) now decides through them: each unit is packed into chunks, drafted, judged when the dial
enables the judge, and decided by the acceptance rule and the quality loop. A book of any of the four formats can
be translated end to end from `./gradlew :app:translate`, or through the window, with `pseudo` or a configured real
provider. SQLite is not yet built; protected spans, the context package, translation memory, the rolling summary and
deferrals exist but are not yet wired into a run.

Except for the packages recorded in the as-built entries above, every other package row in `#inventory` is **still
unwritten** — cite it freely as a target, but expect to create it. In `:ui` that means `ua.bookloom.ui.view` and
`ua.bookloom.ui.dialog` (six of eight `:ui` packages exist); in `:persistence`, the SQLite-facing packages —
`.migration`, `.dao`, `.settings`, `.checkpoint`, `.secret`, `.lock` — are all still unwritten, while `.memory` (the
in-memory adapters, ADR-0034) already exists outside this table.

**`build-logic` is an included build** (`includeBuild("modules/build-logic")` in `settings.gradle.kts`
`pluginManagement`),
not a subproject. It holds five precompiled convention plugins — `bookloom.java-conventions`,
`bookloom.spotless-conventions`, `bookloom.test-conventions`, `bookloom.javafx-conventions`, and the root-only
license/lock wiring — and its own JUnit + `GradleRunner` **functional test suite** under
`modules/build-logic/src/test/java/ua/bookloom/buildlogic/`, which seeds a deliberate violation per tool (unformatted source,
a NullAway finding, a Checkstyle finding, a FindSecBugs finding, a GPL coordinate, an out-of-lock version, a coverage
shortfall) and asserts each one fails the build. Being an included build has one practical consequence worth knowing:
**the root `clean` does not reach it**, so `:build-logic:test` can report `UP-TO-DATE` across an otherwise-clean gate
run. `./gradlew :build-logic:clean` first when the point of the run is to re-prove those canaries.

**`arch-test` is a source set inside `:app`**, at `:app/src/archTest/java`, holding `ua.bookloom.archtest` (the nine
rules, the rule suite, and the completeness meta-test) plus one deliberate **violation fixture per rule** under
`ua.bookloom.<module>.archfixture`. The fixtures are compiled but excluded from the production-class set the rules
analyse; `RuleViolationFixtureTest` asserts each rule catches its own fixture, so a rule that silently stopped
matching fails the gate. `:app:archTest` is a dependency of `:app:check`.

**The dependency-lock gate is `verifyLocks` + `-PstrictLocks`.** Gradle exposes no command-line flag for the lock
mode, so STRICT is opted into with the `strictLocks` Gradle property, and `verifyLocks` resolves every lockable
configuration without writing lock state. CI runs `./gradlew -PstrictLocks verifyLocks` (and the same for
`-p modules/build-logic`) as its **own step before** the quality gate, deliberately not folded into
`clean build check spotlessCheck` — the pre-push hook and the CI quality job must run that command byte-identically,
so the lock check stays beside it rather than inside it. `verifyLocks` refuses to run under `--write-locks`.

**The license gate is outside `check`, deliberately, because it needs the network.** `checkLicense` answers "may we
distribute this?" from POMs on disk; run it by name when a change touches dependencies. (The OWASP SCA gate that sat
beside it was dropped on 2026-09-11.) One fact worth carrying forward:

- **Transitive versions are raised by a `constraints` block in `bookloom.java-conventions`, never by declaring the
  dependency.** Guava is the worked example: Guice pins `guava:31.0.1-jre` transitively, and the catalog's
  `guava = "33.6.0-jre"` does nothing on its own — a catalog entry nothing references is dead. The constraint is what
  makes the pin bind, and it keeps Guava transitive-only rather than putting it on every module's compile classpath.
  Any catalog version added purely to raise a transitive needs a matching constraint, or it is decoration.

## inventory {#inventory}

### :api {#module-api}

Contracts only: ports, records/DTOs, enums, the `Result<T>` envelope, `AppError`, `ErrorCode`. Framework-free, FX-free,
imports no other internal module.

| Package path                       | Layer      | Responsibility                                                                                                         | Test target · tier                                      |
|------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| `:api/ua.bookloom.api`             | Foundation | `Result<T>`, `AppError`, `ErrorCode`, safe-details allowlist                                                           | `:api/src/test/java/ua/bookloom/api` · Unit             |
| `:api/ua.bookloom.api.document`    | Foundation | `DocumentPort`, `Segment` (now carrying `PlaceholderPair`s and line-break tokens, ADR-0040), `Unit`, skeleton DTOs, and, from `complete-translation-workflow`, book-inspection types (`BookInspector`, `BookInspection`, `BookProfile`, `BookStats`, `InspectionVerdict`, `LanguageEvidence`, `SentenceSplitter`) | `:api/src/test/java/ua/bookloom/api/document` · Unit    |
| `:api/ua.bookloom.api.llm`         | Foundation | **Real** (`add-translation-engine-and-cli`, `add-real-llm-clients`): `ChatModel`, `ChatMessage`, `ChatRole`, `ChatResponse`, `FinishReason`, `ChatModelFactory`, `ModelSelection`; `ChatRequest(messages, temperature, responseFormat)` retains its message-only constructor; `ProviderKind`, `ProviderConfig`, `ProviderConfigs`, `ModelInfo`, `ResponseFormat`, `ProviderVerifier`, `VerificationStage`, `StageStatus`, `VerificationPolicy`, `StageOutcome`, `VerificationReport`; and, from `add-ui-translation-workspace`, `ModelCatalog` | `:api/src/test/java/ua/bookloom/api/llm` · Unit         |
| `:api/ua.bookloom.api.pipeline`    | Foundation | **Real** (`add-translation-engine-and-cli`): `TranslationEngine`, `RunRequest`, `TranslationJob`, `JobListener`, `Subscription`, `JobState`, `JobStage`, `PausePoint`, `PauseReason`, the sealed `JobEvent` and its records, `JobProgress`, `JobReport`, `FlaggedSegment` — no separate `JobHandle`; `TranslationJob` is both the run and its own handle. `complete-translation-workflow` is adding `QualityDial`, `ReviewMode`, `CallKind`, `BookPlan`, `ChunkPosition` and the `ProjectService`/`ExportService`/`GlossaryService`/`ReviewDesk` service contracts (in flight, not yet wired into a run) | `:api/src/test/java/ua/bookloom/api/pipeline` · Unit    |
| `:api/ua.bookloom.api.persistence` | Foundation | **Real** (`complete-translation-workflow`): repository ports `ProjectRepository`, `SegmentRepository`, `GlossaryRepository`, `TmRepository`, `SummaryRepository`, `DeferralRepository`, `RunRepository`, `CheckpointPort` — each bound today to an in-memory adapter in `:persistence/ua.bookloom.persistence.memory` (ADR-0034) | `:api/src/test/java/ua/bookloom/api/persistence` · Unit |
| `:api/ua.bookloom.api.project`     | Foundation | **Real** (`complete-translation-workflow`): the stored-project shape — `Project`, `BookBrief`, `GlossaryEntry`, `TmEntry`, `RollingSummary`, `RunRecord`, `SegmentRecord`, `QaFinding`, `SegmentPath`, `Deferral`/`DeferralReason`, `ContextSnapshot`, and the brief's policy enums (`ForeignPassagePolicy`, `NamePolicy`, `FootnotePolicy`, `UnitPolicy`, `Gender`, `Register`, `TermType`, `Severity`) | `:api/src/test/java/ua/bookloom/api/project` · Unit |

### :util {#module-util}

Small stateless helpers. Depends only on `:api`.

| Package path                   | Layer      | Responsibility                                                                                                                                                                                                                                  | Test target · tier                                  |
|----------------------------------|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| `:util/ua.bookloom.util.text`  | Foundation | Text normalization, whitespace, placeholder helpers                                                                                                                                                                                             | `:util/src/test/java/ua/bookloom/util/text` · Unit  |
| `:util/ua.bookloom.util.paths` | Foundation | **App-paths resolver** — per-OS data/log/config dir resolution, dev/prod `-Dev` separation via `isDev`, first-run creation, and the process **single-instance lock-file path**. Runs pre-injector, before logging and SQLite (DD-39, ADR-0015). Also `DestinationPath` (`add-ui-translation-workspace`), the destination-naming rule shared by the CLI and the Book Brief screen. | `:util/src/test/java/ua/bookloom/util/paths` · Unit |
| `:util/ua.bookloom.util.io`    | Foundation | Atomic file writes, temp files, low-level IO helpers (not path resolution — see `util.paths`)                                                                                                                                                   | `:util/src/test/java/ua/bookloom/util/io` · Unit    |
| `:util/ua.bookloom.util.lang`  | Foundation | **Real** (`complete-translation-workflow`, task 1.1): the 34-language catalogue — `Language`, `Script` (with its characters-per-token figure), `Languages` (`all()`, `byTag`) — and `LanguageTags.normalize` (case-folds, reads `_` as `-`, maps the retired `ua`→`uk`, splits Chinese into Simplified/Traditional, drops other region tags) | `:util/src/test/java/ua/bookloom/util/lang` · Unit  |
| `:util/ua.bookloom.util.hash`  | Foundation | Content/segment `source_hash` hashing                                                                                                                                                                                                           | `:util/src/test/java/ua/bookloom/util/hash` · Unit  |

### :document {#module-document}

Parse EPUB/FB2/MD/TXT into skeleton+segment, inline masking, unmask+validate, reassembly, repackaging, round-trip
fidelity. FX-free. Implements `DocumentPort`.

| Package path                            | Layer    | Responsibility                                                 | Test target · tier                                                         |
|-------------------------------------------|----------|--------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `:document/ua.bookloom.document`        | Services | `DocumentService` (impl of `DocumentPort`), format dispatch, `close` released through each reader's registry (`add-translation-engine-and-cli`) | `:document/src/test/java/ua/bookloom/document` · Integration               |
| `:document/ua.bookloom.document.model`  | Services | Skeleton, segment list, `Unit` structures (seam F1)            | `:document/src/test/java/ua/bookloom/document/model` · Unit                |
| `:document/ua.bookloom.document.epub`   | Services | EPUB 2/3 read/write, mimetype-first repackage                  | `:document/src/test/java/ua/bookloom/document/epub` · Integration (golden) |
| `:document/ua.bookloom.document.fb2`    | Services | FB2 / `.fb2.zip` read/write, encoding preservation             | `:document/src/test/java/ua/bookloom/document/fb2` · Integration (golden)  |
| `:document/ua.bookloom.document.md`     | Services | Markdown AST parse/render (CommonMark)                         | `:document/src/test/java/ua/bookloom/document/md` · Integration (golden)   |
| `:document/ua.bookloom.document.txt`    | Services | TXT paragraph parse/render                                     | `:document/src/test/java/ua/bookloom/document/txt` · Integration (golden)  |
| `:document/ua.bookloom.document.mask`   | Services | Inline masking `⟦gN⟧`, unmask, placeholder-multiset **and** pair-order/nesting validation (ADR-0040) | `:document/src/test/java/ua/bookloom/document/mask` · Unit                 |
| `:document/ua.bookloom.document.detect` | Services | Charset detection (`CharsetLadder`, `ForeignWordCoherence`) — no source-language detector lives here; DRM is adjudicated in `.epub` (`DrmAdjudicator`) | `:document/src/test/java/ua/bookloom/document/detect` · Unit               |
| `:document/ua.bookloom.document.inspect` | Services | **Real** (`complete-translation-workflow`): `BookInspectorService` (impl of `BookInspector`), `BodySegments`, `FormatInspection`, `FormattingClassifier`, `LanguageEvidenceReader`, `WordCounter` | `:document/src/test/java/ua/bookloom/document/inspect` · Unit |
| `:document/ua.bookloom.document.split`  | Services | **Real** (`complete-translation-workflow`, task 5.9): `IcuSentenceSplitter` (impl of `SentenceSplitter`) — splits an oversized masked segment at ICU sentence boundaries without ever cutting a placeholder pair | `:document/src/test/java/ua/bookloom/document/split` · Unit |

**`ua.bookloom.document.mask` is real, not planned** (`add-inline-masking-and-placeholder-gate`, change 5): it holds
the format-agnostic `⟦gN⟧` grammar (`Placeholders`), the mask accumulator (`MaskWriter`), the masked-content record,
the mask-time invariant check, the placeholder-multiset gate (`PlaceholderGate`), the restore pass (`Unmasker`) and
the markup escaper (`MarkupEscape`). Two siblings implement the per-format masking rather than living inside `.mask`
itself — `TreeMasker` in `.model` (EPUB and FB2, which share the `TreeNode` adapter `BlockSegmentWalker` already
walks) and `MarkdownMasker`/`MarkdownEscaper`/`MarkdownStructureCheck` in `.md` (Markdown, beside `MarkdownWalker`)
— a deliberate deviation from the change's own design.md D10: a masker in `.mask` walking `TreeNode` would make
`.model` and `.mask` mutually dependent and force the one format-agnostic package to depend on jsoup, JDOM2 and
commonmark all at once. Plain-text masking (`TxtReader`'s target has no inline markup to protect beyond a literal
`⟦`/`⟧`) is handled by a small `PlainTextMasker` that does live in `.mask`, since it needs none of those parsers.

### :llm {#module-llm}

Provider abstraction with two client implementations (Ollama-native + OpenAI-compatible), model discovery, inference,
response handling, `InferenceGate`, retry, HTTP→typed error mapping, three-stage + preflight verification. FX-free.

| Package path                     | Layer    | Responsibility                                                                                                                                                                                                                                   | Test target · tier                                                                  |
|------------------------------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `:llm/ua.bookloom.llm`           | Services | **Real** (`add-real-llm-clients`; `ModelCatalogService` from `add-ui-translation-workspace`): `LlmModule`, `ChatModelFactoryImpl`, `InMemoryProviderConfigs`, and `GatedChatModel` bind `pseudo` or a registered real provider to one model id, with retry and the shared inference gate | `:llm/src/test/java/ua/bookloom/llm` · Unit + Integration (WireMock) |
| `:llm/ua.bookloom.llm.pseudo`    | Services | **Real, not planned** (`add-translation-engine-and-cli`): `PseudoChatModel` — deterministic, offline, upper-cases the last user message, keeps `⟦gN⟧` tokens and character references unchanged, finishes `STOP`. Neither exported nor opened — see below | `:llm/src/test/java/ua/bookloom/llm` (`ChatModelFactoryImplTest`) · Unit            |
| `:llm/ua.bookloom.llm.provider`  | Services | Internal `ProviderClient`, `ProviderClientFactory`, and `ProviderCallResult`: one per-dialect contract, client construction by `ProviderKind`, and retry-delay advice carried with the public result | `:llm/src/test/java/ua/bookloom/llm/provider` · Unit                                |
| `:llm/ua.bookloom.llm.client.ollama` | Services | `OllamaClient`: native `/api/version`, `/api/tags`, and `/api/chat` dialect with optional temperature and JSON schema | `:llm/src/test/java/ua/bookloom/llm/client/ollama` · Integration (WireMock) + local-only `OllamaLiveTest` |
| `:llm/ua.bookloom.llm.client.openai` | Services | `OpenAiCompatibleClient`: `/models` and `/chat/completions` dialect with optional temperature and strict JSON-schema response format | `:llm/src/test/java/ua/bookloom/llm/client/openai` · Integration (WireMock) + local-only `LmStudioLiveTest` |
| `:llm/ua.bookloom.llm.http`      | Services | `HttpExchange`, `HttpClients`, `HttpErrorMapper`, and `HttpReply`: JSON transport, fresh request timeouts, reusable connect-timeout clients pinned to HTTP/1.1, and HTTP/transport to typed-error mapping | `:llm/src/test/java/ua/bookloom/llm/http` · Unit + Integration (WireMock) |
| `:llm/ua.bookloom.llm.dto`       | Services | Internal Jackson records for both dialects, opened only to Jackson; nullable request fields are omitted and replies tolerate unknown fields | `:llm/src/test/java/ua/bookloom/llm/dto` · Unit |
| `:llm/ua.bookloom.llm.response`  | Services | `ReplySanitizer`: reasoning-tag removal, outer code-fence unwrapping, and trimming before a reply reaches the engine | `:llm/src/test/java/ua/bookloom/llm/response` · Unit |
| `:llm/ua.bookloom.llm.retry`     | Services | `RetryPolicy`: typed retry decisions, bounded backoff, valid `Retry-After`, and a fresh timeout on every attempt | `:llm/src/test/java/ua/bookloom/llm/retry` · Unit |
| `:llm/ua.bookloom.llm.gate`      | Services | `InferenceGate`: fair single-flight admission around provider operations, released before retry sleep | `:llm/src/test/java/ua/bookloom/llm/gate` · Unit |
| `:llm/ua.bookloom.llm.verify`    | Services | `ProviderVerifierImpl`: ordered connection, models, and optional inference stages for command-line preflight | `:llm/src/test/java/ua/bookloom/llm/verify` · Integration (WireMock) |

**`ua.bookloom.llm.pseudo` is real, and its non-membership in `module-info.java`'s `exports`/`opens` is deliberate**,
the same pattern `ua.bookloom.document.mask` uses above: `PseudoChatModel` is a plain, no-collaborator class, so
Guice needs no reflective `opens` on it, and `ChatModelFactoryImpl` — in the exported base package — is the only
thing that constructs one. JPMS itself, not just the `ports-not-concretes` ArchUnit rule, is what stops `:pipeline`
from naming the concrete class; callers use `ChatModel`/`ChatModelFactory` in `:api.llm` instead.

### :pipeline {#module-pipeline}

The translation engine: chunking, context assembly, tiered translate→QA→judge→self-heal loop, name/term dictionary,
context-aware TM, rolling summary, deferred-resolution + backward revision, prompt builder, quality dial. FX-free.
Implements `TranslationEngine`.

| Package path                              | Layer         | Responsibility                                                     | Test target · tier                                                            |
|----------------------------------------------|---------------|------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `:pipeline/ua.bookloom.pipeline`          | Orchestration | **Real** (`add-translation-engine-and-cli`): the public `TranslationEngineImpl` (creates the job); the package-private `TranslationJobImpl` (the pausable run over a stored project: `PREP`, then `TRANSLATE` through the chunk runner) and `JobBoundaries` (its answer at every pause and stop boundary); the public `SegmentTranslator`, reduced to the draft step (a draft and its two repairs, read by design D3's reply precedence into a `heal.DraftOutcome`), with `PieceDrafter` and `DraftOutcomes`. Protected spans, the context package, memory reuse, the summary and deferrals are built and tested but not yet wired into a run | `:pipeline/src/test/java/ua/bookloom/pipeline` · Integration                  |
| `:pipeline/ua.bookloom.pipeline.chunk`    | Orchestration | **Real** (`complete-translation-workflow`): `Chunk`, `ChunkPacker` (paragraph-grouped packing against the token budget), `TokenBudget`, `TokenEstimator`, `OversizedSplit` (splits one oversized segment into sentence-aligned pieces via the `:document`-implemented `SentenceSplitter`; the sentence-boundary logic itself lives in `document.split`, not here) | `:pipeline/src/test/java/ua/bookloom/pipeline/chunk` · Unit                   |
| `:pipeline/ua.bookloom.pipeline.context`  | Orchestration | **Real** (`complete-translation-workflow`): `ContextPackageAssembler` (fills the draft prompt in the required order), `ContextInputs`, `ContextPackage`, `InjectedTerms`/`InjectedTerm` | `:pipeline/src/test/java/ua/bookloom/pipeline/context` · Unit                 |
| `:pipeline/ua.bookloom.pipeline.project`  | Orchestration | **Real** (`complete-translation-workflow`): `ProjectServiceImpl` (impl of `ProjectService`), `OpenProjects` (the open-project registry), `RoundTripCheck`, `SegmentLocators` (each segment's `ch5 · p12` / `nav · 01` locator, built once per opened book) | `:pipeline/src/test/java/ua/bookloom/pipeline/project` · Unit |
| `:pipeline/ua.bookloom.pipeline.run`      | Orchestration | **Real** (`complete-translation-workflow`): what a run over a stored project reads and writes — `RunStores`, `RunStart` (the refusals before any model call), `WorkList`/`WorkItem` (the PENDING records still to decide, from the first pending on), `OutcomeRecords` (a quality-loop outcome as the stored record, and a flag's reason read back), `RunReports` (the report, counted from the project), `PrepStage` (the style sheet and the deterministic name scan of an empty glossary), `ChunkRunner` with `RunSteps`/`RunSettings`/`RunSinks` (each unit packed into chunks; draft, one judge call and one-at-a-time decisions, or draft-and-decide per segment with the judge off; drafts kept across a pause, dropped by a stop), `ChunkContext` (the glossary a chunk reads once: each segment's protected spans, the span gate and its context package), `ChunkDrafts`, `MemoryReuse` (a context-matched memory target checked at once in place of the draft; each accepted segment's memory entry), `PrecedingTargets`, `RunBoundaries`/`RunEnd` (the job's pause and stop answers), `RoutedCalls`/`Step` (a call made until it answers, each error routed to a pause, a stop or a failure), `DecisionFollowUp` (after each decision its deferrals and the summary's count; at a unit's end the summary refresh and, for a body unit, the names found so far committed at once), `MemoryEvents` (the glossary and summary announcements), `PendingCommit` (with the deferrals and glossary additions of each commit), `JobModelCalls`, `RunRecorder`, `PauseDecider` | `:pipeline/src/test/java/ua/bookloom/pipeline/run` · Unit |
| `:pipeline/ua.bookloom.pipeline.prompt`   | Orchestration | **Real** (`add-real-llm-clients`; extended by `complete-translation-workflow`): `DraftPromptBuilder`, `DraftSchema`/`JudgeSchema`/`ReflectSchema`, tolerant `DraftReplyParser`, `PromptTemplates`, `PromptName`, `StyleSheet`/`StylePhrases`, `ModelCalls` (the one seam every model call of a run goes through), `ChatRequests`, `CallFrame` | `:pipeline/src/test/java/ua/bookloom/pipeline/prompt` · Unit                  |
| `:pipeline/ua.bookloom.pipeline.qa`       | Orchestration | **Real** (`complete-translation-workflow`): the five deterministic soft checks (`ScriptCheck`, `EchoCheck`, `RepetitionCheck`, `LengthCheck`, `GlossaryCheck`) run by `SoftChecks`, the refusal hard gate (`RefusalGate`, `RefusalPhrases`), `Confidence.blend`, and `QaEvaluator` | `:pipeline/src/test/java/ua/bookloom/pipeline/qa` · Unit                      |
| `:pipeline/ua.bookloom.pipeline.judge`    | Orchestration | **Real** (`complete-translation-workflow`): `JudgeCall`, `JudgeReplyParser` → `JudgeVerdict`/`JudgeFinding`/`JudgeDeferral` — the per-chunk LLM-as-judge scoring vs τ | `:pipeline/src/test/java/ua/bookloom/pipeline/judge` · Integration (WireMock) |
| `:pipeline/ua.bookloom.pipeline.heal`     | Orchestration | **Real** (`complete-translation-workflow`): `DirectedFix`, `ReflectImprove`, `Polish`, `Borderline`, `RepairReply`/`RepairReplies` — directed fix and reflect → improve → polish self-heal; `ReuseCheck` (a memory reuse accepted without the judge); the acceptance rule and the per-chunk quality loop are this group's last, still-open task | `:pipeline/src/test/java/ua/bookloom/pipeline/heal` · Integration (WireMock)  |
| `:pipeline/ua.bookloom.pipeline.memory`   | Orchestration | **Real** (`complete-translation-workflow`): `ProtectedSpans` (locked names and kept foreign runs hidden behind tokens, restored through a hard gate; a stored target's spans checked before reuse), `TranslationMemory` (context-aware reuse, hints, suggestions), `RollingSummaryKeeper` with `ChapterText` (the chapter the Max summary call is sent, its middle dropped past the cap) | `:pipeline/src/test/java/ua/bookloom/pipeline/memory` · Unit                  |
| `:pipeline/ua.bookloom.pipeline.glossary` | Orchestration | **Real** (`complete-translation-workflow`): `FrequencyScan` (the deterministic name scan), `PreScan` (the model scan, run only on request), `GlossaryIds`, `GlossaryServiceImpl` (impl of `GlossaryService`: the table, the dialog and CSV import all refuse a duplicate and a locked term with no target; the removal memory; term-change deferrals) and `GlossaryCsv` (the RFC 4180 reader and writer, reused by the export's side file) — not yet bound in a Guice module | `:pipeline/src/test/java/ua/bookloom/pipeline/glossary` · Unit                |
| `:pipeline/ua.bookloom.pipeline.revision` | Orchestration | **Real** (`complete-translation-workflow`): `DeferralRegister` (which deferrals a term change leaves behind); planned: backward revision, consistency sweep | `:pipeline/src/test/java/ua/bookloom/pipeline/revision` · Integration         |
| `:pipeline/ua.bookloom.pipeline.review`   | Orchestration | **Real** (`complete-translation-workflow`): the review desk's parts — `SegmentActions` (Accept, Save edit, Revert, Skip and Apply proposal, each checked against the status machine), `ReviewQueries` (the queue behind each filter chip and one segment's view), `ReviewCounting` (the one counts rule the desk and the export report share); planned: `RetryDraft` and `ReviewDeskImpl` | `:pipeline/src/test/java/ua/bookloom/pipeline/review` · Unit                    |
| `:pipeline/ua.bookloom.pipeline.dial`     | Orchestration | **Real** (`complete-translation-workflow`): `DialParameters` — the quality-dial → parameters mapping | `:pipeline/src/test/java/ua/bookloom/pipeline/dial` · Unit                    |

### :persistence {#module-persistence}

SQLite (Flyway, JDBI DAOs) — planned; in-memory adapters (ADR-0034) — today. Settings KV, project/segment/glossary/
TM/summary stores, secret references, atomic writes, single-instance lock. FX-free. Implements repository ports.

| Package path                                      | Layer    | Responsibility                                                                                                                                                                          | Test target · tier                                                                      |
|------------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `:persistence/ua.bookloom.persistence`            | Services | **Today**: `PersistenceModule` — binds every `:api` repository port to its in-memory adapter (ADR-0034). **Planned**: DB bootstrap, connection/WAL config once the SQLite adapter replaces these bindings | `:persistence/src/test/java/ua/bookloom/persistence` · Integration (temp DB)            |
| `:persistence/ua.bookloom.persistence.memory`     | Services | **Real** (`complete-translation-workflow`, ADR-0034): `InMemoryStore` (the shared singleton) and the eight in-memory repository adapters — `InMemoryProjectRepository`, `InMemorySegmentRepository`, `InMemoryGlossaryRepository`, `InMemoryTmRepository`, `InMemorySummaryRepository`, `InMemoryDeferralRepository`, `InMemoryRunRepository`, `InMemoryCheckpoint` — proven by `RepositoryContractTest`, run against each adapter | `:persistence/src/test/java/ua/bookloom/persistence/memory` · Integration |
| `:persistence/ua.bookloom.persistence.migration`  | Services | **Planned**: Flyway migrations, schema versioning                                                                                                                                                    | `:persistence/src/test/java/ua/bookloom/persistence/migration` · Integration (temp DB)  |
| `:persistence/ua.bookloom.persistence.dao`        | Services | **Planned**: JDBI DAOs implementing repository ports, replacing the in-memory adapters above without changing the ports                                                                                                                                                 | `:persistence/src/test/java/ua/bookloom/persistence/dao` · Integration (temp DB)        |
| `:persistence/ua.bookloom.persistence.settings`   | Services | **Planned**: typed `settings(key,value,type)` KV store (seam F7)                                                                                                                                     | `:persistence/src/test/java/ua/bookloom/persistence/settings` · Integration (temp DB)   |
| `:persistence/ua.bookloom.persistence.checkpoint` | Services | **Planned**: per-chunk crash-safe checkpoints + resume reader (seam F6) — `InMemoryCheckpoint` above is the in-memory `CheckpointPort` adapter that precedes it                                                                                                                              | `:persistence/src/test/java/ua/bookloom/persistence/checkpoint` · Integration (temp DB) |
| `:persistence/ua.bookloom.persistence.secret`     | Services | **Planned**: secret references (env-var name / OS keychain), never the value                                                                                                                         | `:persistence/src/test/java/ua/bookloom/persistence/secret` · Unit                      |
| `:persistence/ua.bookloom.persistence.lock`       | Services | **Planned — DB-connection guard only** (per-DB open/WAL discipline). The process **single-instance lock is NOT here** — it is owned pre-injector by `:app`/`:util.paths` (see `#lock-ownership`). | `:persistence/src/test/java/ua/bookloom/persistence/lock` · Integration                 |

### :ui {#module-ui}

JavaFX presentation: launcher glue, FXML views + controllers, viewmodels, observable state mirror,
screens/dialogs/notifications, theming, i18n. With `:app`, the only module that `requires javafx.*`.

| Package path                | Layer        | Responsibility                                                                                                                                   | Test target · tier                                      |
|--------------------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `:ui/ua.bookloom.ui`        | Presentation | **Real** (`add-ui-translation-workspace`): shell chrome (`AppShellView`, `NavColumn`, `ModalHost`, `AboutDialog`), `Navigator`, `ViewNames`, `Theme`, the `@BackgroundExecutor`/`@IoExecutor` qualifiers | `:ui/src/test/java/ua/bookloom/ui` · UI (TestFX)        |
| `:ui/ua.bookloom.ui.view`   | Presentation | **Planned, not created**: nothing generic to put in it — the controller factory is three lines in `UiModule`, and a package holding one helper is structure for its own sake (that change's design.md D1). Created by the change that first needs it | `:ui/src/test/java/ua/bookloom/ui/view` · UI (TestFX)   |
| `:ui/ua.bookloom.ui.screen` | Presentation | **Real**: Import, Book Brief, Structure, Translating, Export and Settings (Appearance tab). Projects, Names & Style and Review are inert navigation entries with no screen | `:ui/src/test/java/ua/bookloom/ui/screen` · UI (TestFX) |
| `:ui/ua.bookloom.ui.dialog` | Presentation | **Planned, not created**: the change surfaced exactly two dialogs, About (in `ui`) and error-with-details (in `ui.notify`); the other seven (welcome, add/edit provider, glossary term, retry-with-note, confirm-delete, unsaved-changes, export-complete) arrive with the features that raise them (design.md D1) | `:ui/src/test/java/ua/bookloom/ui/dialog` · UI (TestFX) |
| `:ui/ua.bookloom.ui.state`  | Presentation | **Real**: `StateMirror` (seam F8), `RunSession`, `TranslationRunner`, the viewmodels, `FileRevealer`                                              | `:ui/src/test/java/ua/bookloom/ui/state` · Unit + UI    |
| `:ui/ua.bookloom.ui.theme`  | Presentation | **Real**: token-only theming, light/dark, the OS colour-scheme seam and the toggle; accent stays fixed                                           | `:ui/src/test/java/ua/bookloom/ui/theme` · UI (TestFX)  |
| `:ui/ua.bookloom.ui.notify` | Presentation | **Real**: `ErrorPresenter` interface + `@Singleton ModalErrorPresenter` (error-with-details dialog), `Toasts` interface + `@Singleton ToastStack` (native token-styled nodes) | `:ui/src/test/java/ua/bookloom/ui/notify` · UI (TestFX) |
| `:ui/ua.bookloom.ui.i18n`   | Presentation | **Real**: `en` + `uk` bundles, the typed `MessageKey` registry, `LocaleProvider` (chosen by the OS locale). **No language switch in this build** — the in-app switch and the persisted `ui.language` wait for the SQLite adapter | `:ui/src/test/java/ua/bookloom/ui/i18n` · Unit + UI     |

### :app {#module-app}

`Launcher` (does not extend `Application`), the `Application` subclass building the Guice injector, the single
composition root, two-phase init, single-instance lock wiring.

| Package path           | Layer        | Responsibility                                                                                                                                                                                                                                           | Test target · tier                                 |
|--------------------------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `:app/ua.bookloom.app` | Presentation | `Application` subclass, Guice composition root, two-phase init, `AppVersion`, the launcher→application startup handoff, and **`CoreModules`** (`add-translation-engine-and-cli`) — the Guice graph (`AppModule`, `DocumentModule`, `LlmModule`, `PersistenceModule`, `PipelineModule`) `BookLoomApplication` and `TranslateLauncher` share, so the desktop app and the command line never wire different graphs | `:app/src/test/java/ua/bookloom/app` · Integration |
| `:app/ua.bookloom.app.bootstrap` | Presentation | Everything that runs **before logging exists**: `Launcher` (does not extend `Application`), **`TranslateLauncher`** (`add-translation-engine-and-cli`; repeats `Launcher`'s pre-injector steps without starting JavaFX, for `./gradlew :app:translate`), the **process single-instance lock** — acquired pre-injector (`FileChannel.tryLock` on the `:util.paths`-resolved lock file) before the DB opens (see `#lock-ownership`) — the programmatic Logback configuration (`LoggingBootstrap`, plus the pure `LoggingLevelResolver`/`ResolvedLogLevel` the `BOOKLOOM_LOG_LEVEL`/`bookloom.log.level` switch uses), and the pre-logging failure dialog. ArchUnit `bootstrap-no-static-logger` scopes to this package, so putting a class here **is** the declaration that it holds no static logger | `:app/src/test/java/ua/bookloom/app/bootstrap` · Integration |
| `:app/ua.bookloom.app.cli` | Presentation | **Real, opened to Guice and not exported**: `TranslateArguments` parses `<book> [--to <lang>] [--from <lang>] [--overwrite] [--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] [--base-url <url>] [--timeout <seconds>]`; `TranslateCommand` registers real providers for one run, prints preflight stages, preserves the pseudo default, and reports exit 0/1/2 | `:app/src/test/java/ua/bookloom/app/cli` · Integration + local-only `TranslateCommandLiveTest` |

## non-module-citable-targets {#non-module-citable-targets}

Some changes (notably the Stage A infrastructure work) touch **repository-level, non-Java build/tooling targets**
that are not Gradle subprojects or Java packages but must still be citable in a change's Impact section and task
pointers. These are the **only** allowed non-`:module/package` targets:

| Citable target | What it is                                                                                                                                   | Typical citing work                                                                                      |
|----------------|--------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `build-logic`  | The **included build** holding the precompiled convention plugins (`bookloom.*-conventions.gradle.kts`) and their `GradleRunner` functional tests | Toolchain/JPMS/lint/format/test conventions, tag, lock and coverage-gate wiring        |
| `arch-test`    | The shared ArchUnit source set — physically `:app/src/archTest/java` — hosting the boundary rules and their violation fixtures | ArchUnit boundary rules (`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules`) |
| `ci`           | The GitHub Actions workflow definitions (quality job, packaging matrix, release)                                             | CI pipeline, coverage/license/SCA gates, jpackage smoke                                                  |
| `tooling`      | Repo-level tooling not compiled into a module: Lefthook hooks, gitleaks config, license policy file, OWASP suppressions (`config/owasp/suppressions.xml`), icon-generation scripts | Git hooks, license allowlist file, SCA suppressions, `assets/icon/*.py`                     |

A change may cite these verbatim (e.g. `→ build-logic`, `→ arch-test`). They carry no `ua.bookloom.*` package path
because they contain no application module code.

## lock-ownership {#lock-ownership}

There are two distinct locks, owned in different layers:

- **Process single-instance lock** — owned **pre-injector** by `:app` (acquisition) using the lock-file path resolved by
  `:util/ua.bookloom.util.paths`. It is taken **before** the Guice injector is built and **before** the database opens
  (startup order: resolve paths → create dirs → **acquire single-instance lock** → logging → SQLite). A second launch
  that finds the lock held shows the "already running" dialog and exits (it does not raise the first window). This lock
  is **not** a persistence concern.
- **DB-connection guard** — owned by `:persistence/ua.bookloom.persistence.lock`, guarding the SQLite connection/WAL
  discipline only.

A change touching the single-instance lock cites `:app/ua.bookloom.app.bootstrap` and `:util/ua.bookloom.util.paths`,
not `:persistence/ua.bookloom.persistence.lock`.

## note-on-guice-modules {#note-on-guice-modules}

Each Gradle module owns exactly one Guice `Module` binding its ports to implementations; the single composition root in
`:app` installs them (see `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`). A story adding a binding cites
the owning module's package here; there is no separate DI module inventory. Under JPMS each service `module-info.java`
(`:document`/`:llm`/`:pipeline`/`:persistence`) therefore `requires com.google.guice` and
`opens <impl-pkg> to com.google.guice` so Guice can reflect on the constructor-injected implementation
(`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`).
