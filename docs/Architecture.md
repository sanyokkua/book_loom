# BookLoom — Architecture and Current Capabilities

*Regenerated from the code at the final commit of `complete-translation-workflow` (2026-10-17). Sections are numbered
and stable so a prompt or a review can say "per §4.3". When the code changes, change this file in the same commit.
`docs/DEVELOPMENT.md` says how to build and run; `AGENTS.md` is the operating manual and holds the dense "where it
stands" summary; the groups of work that were not planned up front (15a–15j) are written down in
`openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md`.*

## 1. What BookLoom is, and where it honestly stands

BookLoom is a local-first, offline desktop application that translates whole books (EPUB, FB2, Markdown, TXT)
with a locally-run LLM (Ollama / LM Studio) or any OpenAI-compatible endpoint, preserving the book's structure, IDs,
images and fonts through a canonical-equal round trip.

**Today the whole workflow runs in the window and from the command line.** In the window a person opens a book (the
import card reports its format, declared language, cover and structure), describes it in the Book Brief (languages —
any language the JDK can name, not only the 34 offered — tone, name/foreign-passage/footnote/unit policies, quality
dial, who narrates, the "Also translate" switches), settles names and recurring terms on Names & style, starts the run
on Translating, where the review panel lives (accept, edit, revert, skip, apply a proposal, retry with a note), and
writes the book from Export, which is its own action and not a job stage. A run is a pausable job over a **stored
project**: it packs each unit into chunks, drafts, reviews and decides every segment through the acceptance rule and the
quality loop, reuses translation memory, keeps a rolling summary and the glossary, pauses as the review mode says
(`BOOKLOOM_REVIEW_MODE`, §5), recovers from a provider outage by itself, and on Max revises backwards after the last
segment. Importing another book while a run can continue asks first (one translation per application run). The model
is the offline `pseudo` provider, an Ollama-native endpoint or an OpenAI-compatible one; every call is capped in output
(`ChatRequest.maxOutputTokens`) and bounded by a timeout scaled to the expected output.
`./gradlew -q :app:translate --args="'<book>' …"` runs the same pipeline headlessly and is the proof tool (§5).

**Nothing is saved across a restart.** `:persistence` binds every `:api` repository port to an in-memory adapter
(ADR-0034, §2), so there is no resume after a restart, no remembered setting and no Projects screen; SQLite behind the
same ports is the next piece of work after the pipeline redesign (§9).

| | |
|---|---|
| Production Java | 8 JPMS modules, all carrying real code; `:persistence` binds in-memory adapters (ADR-0034), SQLite still to come. Line counts are not recorded here, so they cannot drift |
| Tests | JUnit 5 + AssertJ in every module (`:ui` under TestFX on the headless platform), WireMock at the provider HTTP seam, zero Mockito throughout; fixtures are real bytes in temp dirs. Run `./gradlew test` for the current count |
| Stack | Java 25, JavaFX 26, Gradle 9 (Kotlin DSL), Guice 7, jsoup + JDOM2 + commonmark (+ autolink) + ICU4J, Jackson, JUnit 5 + AssertJ |

## 2. Modules and layering

```
        :app  ──►  :ui                      presentation — the only modules that see JavaFX
          │
       :pipeline                            orchestration — engine, run, quality stack, memory, export, review, setup
       /   │    \
 :document :llm :persistence                services — implement :api ports; FX-free (:persistence: in-memory adapters, ADR-0034)
       \   │    /
        :util                               foundation
          │
        :api                                contracts only; framework-free
```

Edges point downward only. `settings.gradle.kts` maps `:api` … `:app` onto `modules/<name>` (ADR-0021). The package
map of every module is `docs/implementation_plan/01_MODULE_INVENTORY.md`.

**Nine ArchUnit rules** (`modules/app/src/archTest/java/ua/bookloom/archtest/ArchitectureRules.java`, run by
`BoundaryRulesTest`; `RuleViolationFixtureTest` proves each one fires on a deliberate violation):

| Rule | Guarantees |
|---|---|
| `FX_FREE_CORE` | no `javafx.*` outside `:ui`/`:app` |
| `DEPENDENCY_DIRECTION` | module edges follow the graph above |
| `PORTS_NOT_CONCRETES` | consumers depend on `:api` ports, never on another module's implementation |
| `NO_HTTP_IN_CORE_EXCEPT_LLM` | `java.net.http` only in `:llm` — opening a book never needs the network, enforced |
| `NO_SQL_IN_CORE_EXCEPT_PERSISTENCE` | JDBC/SQL only in `:persistence` |
| `API_IS_FRAMEWORK_FREE` | `:api` depends on no Guice, Lombok or JavaFX |
| `RECORDS_FIRST` | immutable data carriers are records |
| `BOOTSTRAP_NO_STATIC_LOGGER` | nothing on the pre-logging boot path holds a static logger |
| `NO_INLINE_STYLE_IN_UI` | no `:ui` class calls `setStyle` on any JavaFX type (node, tooltip, menu, tab); styling is `theme.css` tokens and style classes |

**Guice wiring.** `ua.bookloom.app.CoreModules` installs `AppModule` (paths, environment, `StartupContext`, the
`ReviewMode` resolved at launch, a background pool and a virtual-thread IO executor), `DocumentModule` (`DocumentPort`,
`BookInspector`, `SentenceSplitter`), `LlmModule` (`ChatModelFactory`, `ProviderVerifier`, `ModelCatalog`,
`ProviderConfigs` with the offline `pseudo` provider and the in-memory `ollama`/`lmstudio` presets),
`CoreModules` takes the storage module as an argument: `InMemoryPersistenceModule` (every repository port to its in-memory adapter, singletons over one `InMemoryStore`; the command line) or `SqlitePersistenceModule` (the same ports to the SQLite adapters over one `SqliteDatabase`; the window) and
`PipelineModule` (`TranslationEngine`, `ProjectService`, `ExportService`, `GlossaryService`, `LexiconService`,
`NamesPass`, `ReviewDesk`, `SetupAssistant`, the prompt templates and the bundled language rules). The desktop app adds
`UiModule` and `DesktopModule` (the operating system's keep-awake); `./gradlew :app:translate` installs `CoreModules`
alone, so the two entry points never wire different graphs (§5). The packaged smoke can swap the step locks for open
ones (`OpenStepsModule`) so its resolve-all step can load every screen with no book open.

## 3. Contracts (`:api`)

### 3.1 Failure envelope (`ua.bookloom.api`)

- `Result<T>(data, error)` — exactly one of the two is non-null (enforced in the constructor); `ok/err/isOk/isErr/map/flatMap`.
- `AppError(code, title, message, details, retryable, cause)` — the single failure type. `retryable` must equal
  `code.isRetryable()`; `details` can only be built through `SafeDetails`; `cause` is for logs, never shown.
- `SafeDetails` — the privacy allowlist as a type: `with…` methods for HTTP status, endpoint **host** only, model
  name, timeout, attempt count, QA finding names, expected placeholders in full and observed placeholders capped at
  64 tokens × 16 chars. There is no way to put an exception message or a body into it.
- `ErrorCode` — 15 constants; **bold = retryable**: `auth`, **`timeout`**, **`rateLimited`**, **`unreachable`**,
  `modelNotFound`, **`discoveryFailed`**, `modelUnavailable`, **`upstream`**, `missingCredential`, `contextWindow`,
  `emptyCompletion`, `cancelled`, `validation`, `internal`, `busy` (a second running instance, or a retry refused while
  a run is RUNNING; not retryable). This change added none (ADR-0022, ADR-0039).

**Boundary rule:** no exception crosses a port. Every public port method catches `Throwable` and returns `Result.err`.

### 3.2 Document model (`ua.bookloom.api.document`)

| Type | Meaning |
|---|---|
| `Document` | `id, format, declaredLang?, detectedSourceLang? (never populated — language evidence is read by inspection), charset?, hasBom?, contentHash, metadata, units` |
| `Unit` | one content file / body, or the book's one auxiliary unit: `id, order, href, mediaType, skeleton (SkeletonHandle), segments`. `UnitRole` says front matter, body or back matter |
| `Segment` | one translatable block: `id ({unit}:{ordinal}), unit, order, kind, sourceInner, masked, placeholders (ordered token→fragment), sourceHash, prevKey, nextKey (neighbours' hashes, for context-aware memory), anchor, targetInner?, status, confidence, declaredLanguage?, pairs, lineBreakTokens` |
| `PlaceholderPair` | the open and close token of one masked pair, recorded at mask time (ADR-0040) |
| `SegmentKind` | 13 values: the body kinds plus the auxiliary kinds (`METADATA_TITLE`, `METADATA_AUTHOR`, `METADATA_DESCRIPTION`, `FRONTMATTER_VALUE`, `ALT`) |
| `SkeletonAnchor` | sealed: `NodeAnchor(nodePath, runIndex)` for tree formats (index path, not id — ids can repeat), `ByteSpanAnchor(start, end)` for buffer formats, `AttributeAnchor(nodePath, attribute)` for an attribute value such as image alt text (ADR-0041) |
| `SkeletonHandle(opaqueId)` | names a parsed tree only `:document` can resolve, so parsers never leak onto the shared classpath |
| `BookFormat` | `EPUB, FB2, MARKDOWN, TXT`; export is same-format only; carries its suffixes (longest first), `ofFileName` and `matchedSuffix` |
| `BookInspector`, `BookInspection`, `BookProfile`, `BookStats`, `StructureNode`, `LanguageEvidence`, `InspectionVerdict` | what a book is before any translation: readable / DRM-protected / unsupported, the format and version, title, author, cover, declared language and its evidence, the structure tree (every body segment counted in one node) and statistics |
| `SentenceSplitter` | the port behind oversized-segment splitting; implemented in `:document` |

`Segment.withDecision`, `Unit.withSegments` and `Document.withUnits` are copy-with methods so `:pipeline` never
rebuilds a wide record by hand.

### 3.3 `DocumentPort` and `BookInspector`

```java
Result<Document> open(Path source);
Result<Path>     write(Document document, Path destination, String targetLanguage);
Result<Boolean>  close(Document document);
Result<String>   unmask(BookFormat format, Segment segment, String translatedMasked);
Result<String>   repairPlaceholders(Segment segment, String translatedMasked, PlaceholderRepair mode);

Result<BookInspection> inspect(Path source);          // BookInspector
Result<BookProfile>    profile(Document document);
```

`open` parses and masks; `write` reassembles what was opened (an overload also takes the run's source language, for the package's language rewrite);
`close` releases the parsed state kept in the in-memory registry keyed by `Document.id`; `unmask` validates a
translated segment against the placeholder gate and restores the markup; `repairPlaceholders` is the code-side repair
of a reply whose tokens are wrong. A gate or structure failure is `ErrorCode.validation`; anything unexpected is
`internal`. `inspect` opens a file only far enough to report it (the import card), `profile` adds the structure tree and
statistics for an opened document.

### 3.4 The chat-model contract (`ua.bookloom.api.llm`)

```java
interface ChatModel { Result<ChatResponse> chat(ChatRequest request); }
record ChatRequest(messages, temperature, responseFormat, contextSize, maxOutputTokens, sampling, …) {}
record ChatResponse(String content, FinishReason finishReason, TokenUsage usage) {}   // usage nullable
interface ChatModelFactory { Result<ChatModel> create(ModelSelection selection); }
record ModelSelection(String providerId, String modelId) {}
```

The engine never names a provider or a model itself: it is handed a `ChatModel` already bound to one (ADR-0033), and
keeps that same instance for the whole job. `ChatModelFactoryImpl` resolves `pseudo` to the offline `PseudoChatModel`
(upper-cases the last user message, keeps every `⟦gN⟧` token, answers every catalogue response format with a
well-formed reply) and any registered `ProviderConfig` to a gated, retried client: the Ollama-native client reads
token usage and sends `num_ctx` and `num_predict`; the OpenAI-compatible client reads `usage` and sends `max_tokens`
and never a context field. `ProviderVerifier` runs the three-stage check (connection, models, inference) with measured
durations; `ModelCatalog` lists a provider's models for a screen; `ModelCapabilities`/`ContextLength` carry the window
the provider reports (read in `llm.capability`). Every provider call goes through one `InferenceGate`.

### 3.5 The engine contract (`ua.bookloom.api.pipeline`)

```java
interface TranslationEngine { Result<TranslationJob> newJob(RunRequest request, ChatModel model); }
record RunRequest(String projectId, ReviewMode mode, @Nullable Integer detectedContext, @Nullable String modelName) {}
interface TranslationJob {
    Result<JobReport> run();                 // synchronous, on the caller's thread
    void pause(); void resume(); void cancel(); void skipSegment();
    void pauseAt(Set<PausePoint> points);    // AFTER_SEGMENT, AFTER_SECTION, BETWEEN_STAGES, ON_ERROR, ON_FLAGGED
    void recoverWith(ProviderProbe probe, Duration maxOutage);   // outage recovery (default 12 h)
    JobState state();                        // NEW, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED
    Subscription subscribe(JobListener listener);
}
```

A `TranslationJob` is both a run and its own handle. It runs over a **stored project**, from its first pending segment,
in the stages `PREP` (the style sheet, a deterministic names scan of an empty glossary), `TRANSLATE` and, on Max,
`REVISE`; there is **no export stage**. `ReviewMode` (`UNATTENDED`, `ASSISTED`, `MANUAL`) carries its pause points and
the confidence threshold. `JobReport` is always terminal and counts the accepted, flagged and pending segments from the
project. The job announces its work as sealed `JobEvent`s (segment started/drafted/decided, round and batch started,
context assembled, memory updated, model call started/finished with a live `CallSnapshot`, paused, resumed, recovery
waiting, finished).

Export is a separate action: `ExportService.newExport(ExportRequest, model)` returns an `ExportJob` whose `run()`
writes the book (destination checks, a temporary file, re-open, per-segment verification of the markup, atomic move),
the chosen side files (glossary CSV, bilingual HTML, quality report) and, on request, the consistency pass; it
writes what the project has decided, leaving the rest in the source, so a stopped run can still be exported.

### 3.6 Project, storage and service ports

- `ua.bookloom.api.project` (records): `Project`, `BookBrief` (languages, genre, register, policies, quality dial,
  `AlsoTranslate`, `Narrator`), `GlossaryEntry`, `LexiconEntry`, `TmEntry`, `RollingSummary`, `RunRecord`,
  `SegmentRecord` (the stored decision, its `QaFinding`s and its `ContextSnapshot`), `SegmentPath`/`SegmentLocator`,
  `Deferral`/`DeferralReason`, `ChunkCommit`.
- `ua.bookloom.api.persistence` (ports): `ProjectRepository`, `SegmentRepository`, `GlossaryRepository`,
  `LexiconRepository`, `TmRepository`, `SummaryRepository`, `DeferralRepository`, `RunRepository` and
  `CheckpointPort.commit(ChunkCommit)`, which makes a chunk's records, memory entries, deferrals and new names visible
  all together or not at all. `RepositoryContractTest` proves each port against its in-memory adapter.
- Service ports in `ua.bookloom.api.pipeline`: `ProjectService` (list, create, open, brief update, rename, remove,
  translate into another language, relocate the book file, plan, segment previews, round-trip check), `GlossaryService` and `LexiconService` (tables, scans, model review, CSV),
  `NamesPass` (the autonomous names pass with Undo), `SetupAssistant` (suggested brief, file name, narrator recheck),
  `ReviewDesk` (the review actions and queries), `ExportService`.

## 4. The document engine (`:document`)

### 4.1 Data flow

```
inspect: file ─► FormatInspection (per format) ─► BookInspection (verdict, language evidence, cover, structure tree)

open:    file ─► FormatResolver (extension + magic bytes) ─► <Format>Reader
              ─► segments: BlockSegmentWalker (EPUB/FB2 trees) | MarkdownWalker | ParagraphScanner (TXT)
              ─► anchors: NodeAnchor | ByteSpanAnchor
              ─► masking: TreeMasker | MarkdownMasker | PlainTextMasker ─► MaskWriter (invariant check)
              ─► auxiliary unit (always the last unit: nav/NCX/alt, FB2 title-info, frontmatter values)
              ─► Document (every Segment carries masked + placeholders + pairs); parsed state ─► Open<Format>Registry

split:   IcuSentenceSplitter(masked segment) ─► pieces that concatenate back, never cutting a placeholder pair

unmask:  PlaceholderGate.compare(segment.masked, translated) ─► Unmasker.restore (one regex pass; EPUB/FB2 escape
         & < > between tokens) ─► EPUB/FB2: XML-writability check | MARKDOWN: MarkdownEscaper + MarkdownStructureCheck
         ─► restored inner content

write:   Document ─► <Format>Writer (registry lookup by id) ─► target text written into the skeleton's text slots only
         ─► container re-emitted (mimetype-first EPUB | same FB2 container | byte-spliced MD/TXT)
```

Inside `:document` the readers and writers throw typed exceptions (`CorruptContainerException`,
`DrmRefusedException`, `MalformedFragmentException`, `DocumentNotOpenException`, …); `DocumentService` is the single
place that converts them to `Result`/`AppError` (§4.7).

### 4.2 Per-format behaviour

| | Parsed | Preserved on write | Refused | Yields no segment |
|---|---|---|---|---|
| **EPUB** | every zip entry captured in stream order (limits: 10,000 entries, 256 MiB, ratio 200; IBM437 legacy names); `container.xml` → OPF (manifest, spine, `dc:language/title/creator`) → XHTML via jsoup. A block is a segment when it **owns direct text** (ADR-0027); `<br>` splits it into runs. The navigation document's link texts, the NCX labels and every image's `alt` value are auxiliary segments (§4.9) | `mimetype` first + STORED (synthesized when the source had none, ADR-0030); every other entry in original order with its **own** compression method; the target language written everywhere a reading system looks (`dc:language`, the OPF `dcterms:language` meta, `<package xml:lang>`, `xml:lang`/`lang` on each content document); the OPF written with LF line ends and an XML declaration only when the source had one; each content document's prolog (declaration, DOCTYPE) kept verbatim; a line feed, carriage return or tab inside an attribute value kept as a character reference; ids, namespaces, CDATA, comments, entities; the LF jsoup drops after `<pre>` is restored | any `encryption.xml` target that is not a manifest-declared font (ADR-0026); zip-level encryption; no OPF `<metadata>`; a spine with no present file. A spine item whose file is absent is **skipped** (one warning line) — one real book lists twenty pages and ships nine | `pre`, `code`, `math`, `svg`, `listing` content; blocks with no direct text |
| **FB2** (`.fb2`, `.fb2.zip`) | one XML document via `SecureXml` (entities expanded from the book's header or a bundled 252-name list; a DOCTYPE's external subset is answered from that list, never fetched; an externally-valued entity expands to nothing); declared encoding cross-checked by ICU over prose only; `title-info` lang/title/author; same walker over JDOM2; the byte-order mark is recorded. `book-title`, authors and annotation paragraphs are auxiliary segments (§4.9) | container echoed (bare→bare, zip→zip, same member); encoding name echoed as spelled; the source's line-ending style (LF or CRLF); comments, CDATA, entity spelling, prefixes; a byte-order mark is written back exactly when the source had one; **ADR-0029**: if a target char is unrepresentable in the declared charset the whole file is written UTF-8 with the declaration rewritten | encrypted zip member; declared encoding contradicting the content | `math`, `svg`, `pre`, `listing` (FB2 `<code>` is prose style and **is** entered) |
| **Markdown** | commonmark parse for **analysis only**; the skeleton is the original byte buffer; leading `---` frontmatter split off; one segment per translatable leaf block; a bare web address in prose is masked as an atomic token (commonmark autolink); each text value of the frontmatter, and each image's alternative text, is an auxiliary segment (§4.9) | original bytes copied, only translated spans replaced (single ascending pass); emphasis markers, bullets, fences, trailing newline untouched; an existing top-level frontmatter `lang` value is replaced by the target tag in its own quotes and a `lang` key is never added (D-2); **ADR-0029**: a translation the source encoding cannot hold is written whole as UTF-8, with a byte-order mark exactly when the source had one (D-4) | — | fenced/indented code, raw-HTML blocks, link-reference definitions, frontmatter keys and any value YAML reads as another type (number, boolean, null, date, URL, list, map) |
| **TXT** | no parser: blank-line boundaries scanned over bytes; one `PARAGRAPH` per block | byte-exact: encoding, BOM, line endings; a translation the source encoding cannot hold is written whole as UTF-8 (D-4), with a BOM exactly when the source had one | — | — |

### 4.3 Masking

- **Token**: `⟦gN⟧` (U+27E6, `g`, digits, U+27E7); map key is the bare `gN`. Numbering is dense from 0 in
  first-appearance order. `MaskWriter.build()` throws if a token repeats or the token set and the map disagree.
- **Paired vs atomic** (`TreeMasker`): an element with translatable children becomes an open token + masked children +
  close token; an empty element, CDATA, comment or processing instruction becomes one atomic token holding the
  exact source markup (entity references no longer exist in the tree: they are expanded on read). Never entered (always atomic): `math`, `svg`, `pre`, `listing` in both dialects,
  and `code` in XHTML only.
- A literal `⟦` or `⟧` in prose is itself masked to an atomic token, so the gate can never be fooled by source text.
- Each pair records the `lang`/`xml:lang` its inline element declares, so `:pipeline` can keep a foreign run verbatim under the Keep policy (D-6).
- Markdown masks by the source ranges of its inline constructs (`MarkdownSpans`); TXT has nothing to mask.
- Masking never touches `sourceInner` or `sourceHash`; masking identical bytes yields identical tokens.

### 4.4 The placeholder gate

`PlaceholderGate.compare` checks the tokens as a multiset — any token missing, duplicated or invented is
`ErrorCode.validation` — and then the pairing recorded at mask time (ADR-0040 as amended, `mask/GateRule`): every
pair opens before it closes and nests with the others, a pair that held text still holds text, and a line-break token
keeps its innermost pair; atomic tokens stay free to move. On any failure nothing is restored and the translation is
returned unaltered. `SafeDetails` carries the expected tokens in full and the observed ones bounded.

### 4.5 Markdown restore: escaping and the structure check

After substitution, `MarkdownEscaper` escapes constructs the model introduced: per construct type, only when the
restored text has more of that type than the source and at least one delimiter lies outside every restored
fragment; hard line breaks are removed rather than escaped; at most 20 rounds. `MarkdownStructureCheck` then parses
source and result standalone and compares construct-type multisets (with a containment rule for headings and table
cells: no extra line terminators, no extra unescaped `|`). A mismatch is `validation`.

### 4.6 Charset detection (`detect`)

`CharsetLadder`: BOM → in-band declaration → ICU detection (minimum confidence 10; pure ASCII short-circuits to
UTF-8) → UTF-8. `ForeignWordCoherence` re-ranks only when the winner is a single-byte charset, scoring foreign-word
runs — ICU's whole-file pass never proposes `windows-1251` for a Western book with a short Cyrillic passage.

### 4.7 Error mapping

| Cause | `ErrorCode` |
|---|---|
| DRM refusal, corrupt container, malformed fragment, gate mismatch, structure mismatch, unwritable XML character | `validation` |
| document id not open (registry miss), any other `Throwable` | `internal` |

Details never contain paths, bodies or exception messages (§3.1).

### 4.8 Inspection and splitting

`BookInspectorService` (`document.inspect`) is the façade behind `BookInspector`: four per-format inspections answer
readable, DRM-protected (with the scheme named) or unsupported, read the language evidence the metadata gives (ADR-0037:
metadata only, never a text guess), extract the cover by the first rule that applies, build the structure tree from the
book's own navigation so every body segment is counted in exactly one node, and compute the statistics. It never needs
the network. `IcuSentenceSplitter` (`document.split`) divides a masked segment at the source language's sentence
boundaries; `:pipeline` holds no ICU, it receives the port.

### 4.9 Auxiliary text

Every opened book of all four formats ends with one auxiliary unit (id `aux`, ADR-0041), always produced whatever the
Book Brief says; the brief's "Also translate" switches decide only whether the run translates it. It holds the EPUB
navigation document's link texts and NCX labels that differ from them, image `alt` values, FB2 `book-title`, authors
(one pair around each name part) and annotation paragraphs, and Markdown frontmatter text values (D-2). Segments here
are addressed by `AttributeAnchor` or by the node of the metadata they come from, and are written back into their own
slots; a zero-edit round trip is still canonical-equal.

## 5. Runtime (`:app`, `:util`, `:ui`)

**Boot** (`Launcher.main`): `AppPathsResolver.resolve()` → `prepare()` (create dirs, warn on a network filesystem)
→ `SingleInstanceLock` (`FileChannel.tryLock` on `dataDir/bookloom.lock`) → resolve the log level
(`LoggingLevelResolver`) and the review mode (`ReviewModeResolver`) → `LoggingBootstrap` (programmatic Logback, below)
→ startup log line → `Application.launch`. `BookLoomApplication.init` builds the Guice injector from
`ua.bookloom.app.CoreModules` plus `UiModule` and `DesktopModule`, and runs `AppLifecycle` phase one and two (phase two
is empty by design; the order is enforced). `start` shows a 1024×700 `Scene` (minimum 960×640) over `AppShellView` with
`theme.css`.

- **Second launch**: `ErrorCode.busy`, a "BookLoom is already running" window, exit **0**.
- **Startup failure**: `StartupFailureDialog` on a bare `Platform.startup`, waits ≤ 300 s, exit **1**.
- **Environment**: `BOOKLOOM_ENV` → `-Dbookloom.env` → jpackage property → dev; data/log directories per OS with a
  `-Dev` suffix in dev; `BOOKLOOM_DATA_DIR` overrides.
- **`AppVersion`** reads `version.properties` generated from the git tag, or `dev`.
- **Review mode.** `BOOKLOOM_REVIEW_MODE`, else `-Dbookloom.review.mode`: `unattended` (alias `auto`, the default),
  `assisted` (`semi-manual`) or `manual`. Settings are not saved, so this launch flag is how the careful modes are
  chosen; a value that names no mode falls back to Unattended and is named once in the log. The mode travels in
  `StartupContext`, is bound as `ReviewMode`, and is shown in the Translating screen's ready card and the run status.
- **`:ui`** is eight packages: `ua.bookloom.ui` (the shell chrome built in code — `AppShellView`, `NavColumn`,
  `Navigator`, `ViewNames`, the title-bar run status, `KeepAwake`, `SessionInfo`, `DiagnosticLog`), `.screen` (Import,
  Book Brief, Names & style, Translating, Export and Settings — FXML plus a thin controller; the structure tree lives
  on Import, review lives inside Translating), `.control` (the shared controls: searchable choice box, step footer,
  banner, stat tile, the two-column layout, the term-table card, smooth scrolling), `.dialog` (the confirmation and
  input cards: open-another-book, leave, narrator, character genders, retry with a note, export complete, change results),
  `.state` (`StateMirror`, `RunSession`, `TranslationRunner`, `RunStarter`, `CurrentProject` and the view models),
  `.i18n` (`en`/`uk` bundles, typed `MessageKey`, a locale chosen by the OS — no in-app switch), `.notify` (error
  dialog, toasts) and `.theme` (token-only light and dark). **Projects** is the one inert navigation entry. Six workflow
  steps are numbered with a provider and model footer; the content scrolls when the window is small, and the title bar
  gives way by priority at 960 px.
- **One translation per application run.** Importing a book while a run can continue (running, pausing, paused,
  stopping, stopped) asks whether to discard the current run or keep it; re-importing a book that already holds decisions asks
  to continue the earlier translation or start over.
- **Resolve-all smoke.** With `-Dbookloom.smoke.resolveAll=true` the injector is built in Guice's production stage and
  `ResolveAll` loads every available screen and resolves every explicit `ua.bookloom.api` binding after the window is
  shown, logging `resolve-all passed: views=<n> ports=<m>` or `resolve-all FAILED: …` (§8).

**Logging.** `LoggingBootstrap` configures `bookloom.log` (10 MB files, 14 days, 200 MB cap; console only in dev) at
the level `BOOKLOOM_LOG_LEVEL` / `bookloom.log.level` resolves to (default `INFO` installed, `TRACE` in dev; every
other logger `WARN`), adds `%X{job}`, and writes one `INFO` line naming the level and its source. Beside it:
`bookloom-trace.log` (the detailed log, on in dev, `BOOKLOOM_TRACE_FILE=1` installed), `bookloom-trace-evidence.log`
(the full TRACE of every flagged or repaired segment, 100 MB × 4 archives) and, always on, one
`bookloom-trace-run-<launch time>.log` per launch (INFO and above, last 10 kept). Book text, prompts and model replies
are logged at `TRACE` only. The details and how to share them are `docs/DEVELOPMENT.md#running`.

**Command line.** `TranslateLauncher` repeats `Launcher`'s pre-injector steps — paths, single-instance lock, logging —
without starting JavaFX, then builds its injector from `CoreModules` alone and runs `TranslateCommand`:

```
./gradlew -q :app:translate --args="'<book>' [--to <lang>] [--from <lang>] [--overwrite] [--provider …] [--model …]
  [--quality fast|balanced|max] [--names translate|transliterate|keep] [--review-names] [--max-outage <d>]
  [--report <file>] [--stop-after <n>] [--no-partial]"
```

It checks the destination first (an existing file without `--overwrite` stops it before any model call), runs the
provider preflight, imports the book and its brief, translates unattended with the window's outage recovery, then
exports. A run that did not complete, Ctrl+C included, still exports what it translated unless `--no-partial`. Exit
codes: **0** completed and written, **3** the run did not complete and the partial book was written, **1** nothing was
written, **2** invalid arguments. `scripts/e2e-fixture.sh` drives it as the headless proof. Quoting a book path with
spaces and the log volume are in `docs/DEVELOPMENT.md#running`.

## 6. What it can and cannot do

**Can, through `DocumentPort`/`BookInspector` and their tests:** open any of the four formats (an EPUB whose spine
lists files that are not there included), report what a book is before translating it, refuse DRM and corrupt
containers with a typed error, produce ordered, anchored, masked segments plus the auxiliary unit, restore a
translated masked segment safely or refuse it (multiset and pair order), split an oversized segment at sentence
boundaries, write the book back canonical-equal (byte-exact for TXT), and release an opened book.

**Can, through the engine, the export service and the command line:** translate a whole book of any format end to end
over a stored project with the pseudo model or a real local provider — drafts go out in token-budgeted JSON batches,
deterministic checks and a typography pass run before any model reads a draft, a per-chunk reviewer fixes in place and
the code verifies every edit, the acceptance rule and quality loop repair or flag an echo, and protected spans, the
context package, translation memory, the rolling summary, the glossary and recurring terms keep the book consistent;
pause on a flagged segment or after each segment as the review mode says; recover from an outage by itself; on Max,
revise backwards; export a stopped or partial run, with the chosen side files and an optional consistency pass, never
overwriting a source or an existing destination without being told.

**Can, as an app:** start once, refuse a second instance, log to the right per-OS directory, keep the machine awake
during a run, package into a native image on macOS, Windows and Linux; and, through the window, do everything above
with the workflow screens in §5, in English or Ukrainian (chosen by the OS), in a light or dark token-only theme that
holds WCAG AA.

**Cannot yet:** persist anything across a restart (no resume, no remembered theme, provider or brief — SQLite is the
next adapter), open the Projects entry, switch the interface language in the app, translate through a cloud provider's
parallel lanes (local inference is single-flight), or use the Settings tabs Models, Generation, Automation and Storage.

**Text outside the body** — the navigation, metadata and alt text the auxiliary unit carries (§4.9, ADR-0041) is
translated when the brief's "Also translate" switches ask for it; a switched-off kind stays in the source language.
Counted on the owner's corpus at 216 books, the text that lives outside a body is: NCX table-of-contents labels
(11,997 across all 197 EPUBs), EPUB 3 nav documents (20 books), each content document's `<head><title>` (7,450), OPF
`dc:title` (197) and `dc:description` (106), FB2 `annotation` (6 books, 24 paragraphs) and `book-title` (9), `img@alt`
(1). Everything that *is* inside a body is covered by the structural rule — the same census found no text-owning body
element the walker does not handle.

## 7. Known limits and open debt

Code-visible today:

- `Document.detectedSourceLang` is never populated; the source language comes from the metadata evidence (ADR-0037) or,
  when a book declares none, from the Auto source language (ADR-0042), in which a prompt never names a language nobody
  chose.
- Markdown: raw-HTML blocks, code blocks and link-reference definitions are never translated; a shortcut reference
  link `[text]` becomes literal text (D9); the escaper stops after 20 rounds; the structure check is per segment, so a
  construct spanning segments is not caught (D16).
- FB2: a non-breaking space inside translatable text is a plain character the model sees; a translation that replaces
  it with an ordinary space is accepted.
- Quality: terms the model splits from the first occurrence stay split, the name classes the gold evals still lose
  are written down with their reasons (15i.N8), and the speed goal of about 1.6 s per segment on a 4B model is not met
  (about 3.0 s; generation is the floor). The measurements are in `docs/DEVELOPMENT.md`.
- `EpubWriter` mutates the registry-held tree in place on write (D4).
- `:document` logs DEBUG/TRACE in its readers, walkers and gate but not yet in every writer, parser and masker (D18,
  `CHANGE_BACKLOG.md#decision-debt`).
- The translation pipeline is being redesigned as the next change, `redesign-translation-pipeline`
  (`docs/proposals/2026-10-16-translation-pipeline-redesign.md`, with its "Moved here" list).

Resolved: D2, D3, D5, D6, D7, D8, D11, D13, D17 and D19 (see `CHANGE_BACKLOG.md#decision-debt`); the EPUB, OPF, FB2
byte-order-mark, TXT-refusal and Markdown-URL gaps that the review of `add-translation-engine-and-cli` recorded
(`docs/next_features.md`).

## 8. How to verify what is claimed

| Claim | Proof |
|---|---|
| Each format round-trips canonical-equal with zero edits | `golden/EpubGoldenRoundTripTest`, `golden/EpubGoldenTrapsTest`, `golden/FormatGoldenRoundTripTest` (FB2/MD/TXT), `md/MarkdownRoundTripTest`, `txt/TxtRoundTripTest`, `golden/FixtureSweepTest`, `golden/AllFormatsDeterminismTest`; comparators `EpubCanonicalAssert`, `Fb2CanonicalAssert`, `MarkdownAstAssert`, themselves mutation-tested by `golden/GoldenComparisonMetaTest` |
| Mask then restore is the identity on every fixture | `golden/MaskThenRestoreIdentitySweepTest`, `golden/MaskThenRestoreHazardFixturesTest`, `UnmaskRestoreTest`, `UnmaskFb2DocumentTest` |
| The gate refuses missing/duplicated/invented tokens | `UnmaskPlaceholderGateTest`, `mask/PlaceholdersTest` |
| Markdown escape + structure check | `MarkdownEscapeRestoreTest`, `MarkdownStructureRestoreTest` |
| Unwritable XML characters are refused | `UnwritableCharacterRestoreTest` |
| DRM and container limits | `epub/DrmAdjudicatorTest`, `golden/EpubRefusalFixturesTest`, `model/ZipEntryReaderLimitsTest` |
| Charset detection | `detect/CharsetLadderTest`, `fb2/Fb2ReaderTest` |
| Missing `mimetype` synthesized; FB2 line endings kept | `epub/EpubMimetypeSynthesisTest`, `fb2/Fb2WriterTest` |
| FB2 entities expanded once, external DTD served offline, `file:` entity never read | `fb2/Fb2EntityExpansionTest` |
| The app boots, locks, logs, resolves every screen and port | `app/AppBootSmokeTest`, `app/bootstrap/SingleInstanceLockTest`, `app/bootstrap/LoggingBootstrapTest`, `document/DocumentModuleTest` (Guice wiring) |
| Layering | `archtest/BoundaryRulesTest` + `RuleViolationFixtureTest` |

All test paths are under `modules/<module>/src/test/java/ua/bookloom/<module>/`. Run one with
`./gradlew :document:test --tests 'ua.bookloom.document.golden.FormatGoldenRoundTripTest'`.

**The corpus sweep** (`golden/CorpusVerificationTest`, tag `corpus`):
`BOOKLOOM_CORPUS_DIR=/path ./gradlew :document:corpus`. Last recorded run (2026-09-28, the owner's
`Books_Examples` set, now 235 books): 235/235 opened, 235/235 canonical-equal on the zero-edit identity and
fixed-point probes, 235/235 mutation, marker-strip and idempotence, 235/235 mask/unmask; 826,298 segments and 322,208
placeholders. The earlier 216-book run (2026-09-12, 197 EPUB, 9 FB2, 9 TXT, 1 Markdown, 367 MB) recorded text coverage per book (words of visible body text reached by segments) min
0.9808, median 1.0, the two lowest being a word-boundary counting artefact on span-built paragraphs; 2 min 39 s. Details and the defects it found: `docs/implementation_plan/notes-corpus-verification.md`.

**The running app**: `./gradlew :app:run` prints `app started`, `injector built and two-phase init complete`,
`window shown, initialView=…`; `./scripts/launch-smoke.sh` asserts the same three lines against the packaged image and a
fourth, `resolve-all passed: views=<n> ports=<m>` (`ResolveAllTest` proves the step against the same composition root).
The image runs on the classpath (`--main-jar`), so a missing `requires`, `exports` or `opens` is not visible there; the
fourth line catches what is: a missing resource, a Guice failure, a screen that cannot load.

**The pipeline and the window**: `pipeline/` has the unit tests per package and the whole-book end-to-end test at the
HTTP seam (WireMock, both dialects); `ui/` has the TestFX screen, state and mockup-conformance tests; `scripts/e2e-fixture.sh`
is the headless proof against a real local model.

## 9. What comes next

The workflow this change set out to build runs end to end in the window and from the command line (§1, §5, §6). The
change was archived on 2026-10-19 after the owner's hand checks (task 16.5) passed. Next comes
the pipeline redesign as its own change, `redesign-translation-pipeline`
(`docs/proposals/2026-10-16-translation-pipeline-redesign.md`), and after it the SQLite adapter behind `:persistence`'s
ports, which is what finally lets a run, the settings and the Projects screen survive a restart. The order beyond that
is `docs/implementation_plan/CHANGE_BACKLOG.md`.
