# BookLoom — Architecture and Current Capabilities

*Generated from the code as of 2026-09-26 (branch `feature/add-ui-translation-workspace`). Sections are
numbered and stable so a prompt or a review can say "per §4.3". When the code changes, change this file in the same
commit. `docs/DEVELOPMENT.md` says how to build and run; `AGENTS.md` is the operating manual.*

## 1. What BookLoom is, and where it honestly stands

BookLoom is a local-first, offline desktop application that translates whole books (EPUB, FB2, Markdown, TXT)
with a locally-run LLM (Ollama / LM Studio) or any OpenAI-compatible endpoint, preserving the book's structure, IDs,
images and fonts through a canonical-equal round trip.

**Today a book of any of the four formats goes through the whole pipeline from the desktop window or the command
line, translated by a deterministic offline pseudo model or by a real local provider.** In the window a person opens a
book, chooses a target language, a destination and whether to overwrite, verifies an Ollama or LM Studio provider,
chooses a model, starts, pauses, resumes and stops the run, watches its progress and shows the written file in the file
manager (§5, §6). `./gradlew -q :app:translate --args="'<book>' [--to <lang>] [--from <lang>] [--overwrite]
[--provider …] [--model …]"` does the same without a window (§5). `:llm` and `:pipeline` are real: a `ChatModel`/
`ChatModelFactory` contract bound once per job (ADR-0033, §3.4), the offline `pseudo` provider, gated and retried
Ollama-native and OpenAI-compatible clients, and a pausable `TranslationEngine`/`TranslationJob` that accepts, flags or
stops on each segment, aborts a model request in flight on Pause or Stop, and exports only after the written file
re-opens with the source's segment count (§3.5). `:ui` is real too: the shell, six screens, the state mirror, English
and Ukrainian bundles chosen by the operating system, and a light/dark theme. Only `:persistence` is empty, so nothing
is persisted — no resume across a restart, no remembered theme (§6). The next unit of work is persistence (§9).

| | |
|---|---|
| Production Java | 8 JPMS modules; seven carry real code, `:persistence` is one Guice module with no bindings. Line counts are not recorded here, so they cannot drift |
| Tests | JUnit 5 + AssertJ in every module (`:ui` under TestFX on the headless platform), WireMock at the provider HTTP seam, zero Mockito throughout; fixtures are real bytes in temp dirs. Run `./gradlew test` for the current count |
| Stack | Java 25, JavaFX 26, Gradle 9 (Kotlin DSL), Guice 7, jsoup + JDOM2 + commonmark + ICU4J, JUnit 5 + AssertJ |

## 2. Modules and layering

```
        :app  ──►  :ui                      presentation — the only modules that see JavaFX
          │
       :pipeline                            orchestration — TranslationEngine, the pausable job, checked export
       /   │    \
 :document :llm :persistence                services — implement :api ports; FX-free (:persistence still empty)
       \   │    /
        :util                               foundation
          │
        :api                                contracts only; framework-free
```

Edges point downward only. `settings.gradle.kts` maps `:api` … `:app` onto `modules/<name>` (ADR-0021).

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

Guice wiring: `AppModule` (paths, environment, startup context, a background pool and a virtual-thread IO executor),
`DocumentModule` (`DocumentPort` → `DocumentService`), `LlmModule` (`ChatModelFactory`, `ProviderVerifier`,
`ModelCatalog` and `ProviderConfigs`, resolving the offline `pseudo` provider and the in-memory `ollama`/`lmstudio`
presets) and `PipelineModule` (`TranslationEngine` → `TranslationEngineImpl`). `ua.bookloom.app.CoreModules` installs
all five and is what the desktop app and `./gradlew :app:translate` share, so the two entry points never wire
different graphs (§5); the desktop app adds `UiModule` on top. `PersistenceModule` still binds nothing.

## 3. Contracts (`:api`, package `ua.bookloom.api`)

### 3.1 Failure envelope

- `Result<T>(data, error)` — exactly one of the two is non-null (enforced in the constructor); `ok/err/isOk/isErr/map/flatMap`.
- `AppError(code, title, message, details, retryable, cause)` — the single failure type. `retryable` must equal
  `code.isRetryable()`; `details` can only be built through `SafeDetails`; `cause` is for logs, never shown.
- `SafeDetails` — the privacy allowlist as a type: eight `with…` methods (HTTP status, endpoint **host** only, model
  name, timeout, attempt count, QA finding names, expected placeholders in full, observed placeholders capped at
  64 tokens × 16 chars). There is no way to put an exception message or a body into it.
- `ErrorCode` — 15 constants; **bold = retryable**: `auth`, **`timeout`**, **`rateLimited`**, **`unreachable`**,
  `modelNotFound`, **`discoveryFailed`**, `modelUnavailable`, **`upstream`**, `missingCredential`, `contextWindow`,
  `emptyCompletion`, `cancelled`, `validation`, `internal`, `busy` (a second running instance; not retryable).

**Boundary rule:** no exception crosses a port. Every public port method catches `Throwable` and returns `Result.err`.

### 3.2 Document model (`ua.bookloom.api.document`)

| Type | Meaning |
|---|---|
| `Document` | `id, format, declaredLang?, detectedSourceLang? (never populated yet), charset?, hasBom?, contentHash, metadata, units` |
| `Unit` | one content file / body: `id, order, href, mediaType, skeleton (SkeletonHandle), segments` |
| `Segment` | one translatable block: `id ({unit}:{ordinal}), kind, sourceInner, masked, placeholders (ordered token→fragment), sourceHash, anchor, targetInner?, status, confidence`. Today always `PENDING`, `targetInner == null` until a translation job decides it |
| `SegmentKind` | 13 values; only `PARAGRAPH`, `HEADING`, `LIST_ITEM`, `TABLE_CELL` are produced today |
| `SkeletonAnchor` | sealed: `NodeAnchor(nodePath, runIndex)` for tree formats (index path, not id — ids can repeat), `ByteSpanAnchor(start, end)` for buffer formats |
| `SkeletonHandle(opaqueId)` | names a parsed tree only `:document` can resolve, so parsers never leak onto the shared classpath |
| `BookFormat` | `EPUB, FB2, MARKDOWN, TXT`; export is same-format only |

`BookFormat` also carries its own file suffixes, longest first (`.fb2.zip` before `.fb2`), `ofFileName(String)`
(returning an `Optional<BookFormat>`) and `matchedSuffix(String)` (keeping the file name's own letter case) —
`FormatResolver`, `TranslationEngineImpl` and the command line all use these to recognise a book and to name its
translated sibling. `Segment.withDecision(SegmentStatus, targetInner)`, `Unit.withSegments(List<Segment>)` and
`Document.withUnits(List<Unit>)` are copy-with methods added by `add-translation-engine-and-cli` so `:pipeline` never
rebuilds a fourteen-component `Segment` by hand (design.md D2).

### 3.3 `DocumentPort` — the engine's whole surface

```java
Result<Document> open(Path source);
Result<Path>     write(Document document, Path destination, String targetLanguage);
Result<Boolean>  close(Document document);
Result<String>   unmask(BookFormat format, Segment segment, String translatedMasked);
```

`open` parses and masks; `write` reassembles what was opened; `close` releases the parsed state kept in the
in-memory registry keyed by `Document.id` — added by `add-translation-engine-and-cli` (backlog D3, resolved) so a
translated book does not stay in memory for the rest of the process's life; `unmask` validates a translated segment
against the placeholder gate and restores the markup. A gate or structure failure is `ErrorCode.validation`; anything
unexpected is `internal`.

### 3.4 The chat-model contract (`ua.bookloom.api.llm`)

Added by `add-translation-engine-and-cli` (ADR-0033), so the engine, the command line and the future screen share one
seam to a model without depending on which provider answers:

```java
interface ChatModel { Result<ChatResponse> chat(ChatRequest request); }
record ChatRequest(List<ChatMessage> messages) {}
record ChatMessage(ChatRole role, String content) {}               // ChatRole: SYSTEM, USER, ASSISTANT
record ChatResponse(String content, FinishReason finishReason) {}  // FinishReason: STOP, LENGTH, OTHER
interface ChatModelFactory { Result<ChatModel> create(ModelSelection selection); }
record ModelSelection(String providerId, String modelId) {}
```

The engine never names a provider or a model itself: it is handed a `ChatModel` already bound to one, and keeps that
same instance for the whole job, so `ChatRequest` carries no model field. `ua.bookloom.llm.ChatModelFactoryImpl`
resolves the provider id `pseudo` to the offline `PseudoChatModel` (`ua.bookloom.llm.pseudo`, neither exported nor
opened) — it upper-cases the last user message with `Locale.ROOT`, copies every `⟦gN⟧` token and character reference
unchanged, and finishes `STOP`; any other provider id, or a blank model id, is `ErrorCode.validation`. The
Ollama-native and OpenAI-compatible clients (`add-real-llm-clients`) bind behind the same `ChatModelFactory`, so
`:pipeline` did not change; `ChatRequest` gained optional temperature and response-format hints.

### 3.5 The translation engine contract (`ua.bookloom.api.pipeline`)

Also added by `add-translation-engine-and-cli` (ADR-0033):

```java
interface TranslationEngine { Result<TranslationJob> newJob(TranslationRequest request, ChatModel model); }
record TranslationRequest(Path source, Path destination, String targetLanguage,
        @Nullable String sourceLanguage, boolean overwrite) {}
interface TranslationJob {
    Result<JobReport> run();                 // synchronous, on the caller's thread
    void pause(); void resume(); void cancel();
    void pauseAt(Set<PausePoint> points);    // AFTER_SEGMENT, AFTER_SECTION, BETWEEN_STAGES, ON_ERROR
    JobState state();                        // NEW, RUNNING, PAUSED, COMPLETED, CANCELLED, FAILED
    Subscription subscribe(JobListener listener);
}
```

A `TranslationJob` is both a run and its own handle: `run()` executes synchronously and returns a `JobReport` even
when the job ends cancelled or failed; `pause()`/`resume()`/`cancel()`/`pauseAt(...)` are safe from any thread and
never wait for the job. `JobReport(format, end, segments, accepted, flagged, flaggedSegments, written, error)` is
always terminal: `written` is set only for `COMPLETED`, `error` only for `FAILED` — a cancelled job returns a report
with neither, not an `ErrorCode.cancelled` failure. `:pipeline` implements this with the public
`TranslationEngineImpl` (the request checks: language pattern, matching `BookFormat`, matching FB2 container, a
destination that is not the source) and the package-private `TranslationJobImpl`, `SegmentTranslator` and
`BookExporter` — the only package `:pipeline` opens to Guice.

## 4. The document engine (`:document`)

### 4.1 Data flow

```
open:    file ─► FormatResolver (extension + magic bytes) ─► <Format>Reader
              ─► segments: BlockSegmentWalker (EPUB/FB2 trees) | MarkdownWalker | ParagraphScanner (TXT)
              ─► anchors: NodeAnchor | ByteSpanAnchor
              ─► masking: TreeMasker | MarkdownMasker | PlainTextMasker ─► MaskWriter (invariant check)
              ─► Document (every Segment carries masked + placeholders); parsed state ─► Open<Format>Registry

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
| **EPUB** | every zip entry captured in stream order (limits: 10,000 entries, 256 MiB, ratio 200; IBM437 legacy names); `container.xml` → OPF (manifest, spine, `dc:language/title/creator`) → XHTML via jsoup. A block is a segment when it **owns direct text** (ADR-0027); `<br>` splits it into runs | `mimetype` first + STORED (synthesized when the source had none, ADR-0030); every other entry in original order with its **own** compression method; OPF mutated in place for `dc:language`; ids, namespaces, CDATA, comments, entities; the LF jsoup drops after `<pre>` is restored | any `encryption.xml` target that is not a manifest-declared font (ADR-0026); zip-level encryption; no OPF `<metadata>`; a spine with no present file. A spine item whose file is absent is **skipped** (one warning line) — one real book lists twenty pages and ships nine | `pre`, `code`, `math`, `svg`, `listing` content; blocks with no direct text |
| **FB2** (`.fb2`, `.fb2.zip`) | one XML document via `SecureXml` (entities expanded from the book's header or a bundled 252-name list; a DOCTYPE's external subset is answered from that list, never fetched; an externally-valued entity expands to nothing); declared encoding cross-checked by ICU over prose only; `title-info` lang/title/author; same walker over JDOM2 | container echoed (bare→bare, zip→zip, same member); encoding name echoed as spelled; the source's line-ending style (LF or CRLF); comments, CDATA, entity spelling, prefixes; **ADR-0029**: if a target char is unrepresentable in the declared charset the whole file is written UTF-8 with the declaration rewritten | encrypted zip member; declared encoding contradicting the content | `math`, `svg`, `pre`, `listing` (FB2 `<code>` is prose style and **is** entered) |
| **Markdown** | commonmark parse for **analysis only**; the skeleton is the original byte buffer; leading `---` frontmatter split off; one segment per translatable leaf block | original bytes copied, only translated spans replaced (single ascending pass); emphasis markers, bullets, fences, trailing newline untouched; **no** language metadata written (none exists) | — | fenced/indented code, raw-HTML blocks, link-reference definitions, frontmatter |
| **TXT** | no parser: blank-line boundaries scanned over bytes; one `PARAGRAPH` per block | byte-exact: encoding, BOM, line endings | an export whose target text the source encoding cannot represent (no declaration to update) | — |

### 4.3 Masking

- **Token**: `⟦gN⟧` (U+27E6, `g`, digits, U+27E7); map key is the bare `gN`. Numbering is dense from 0 in
  first-appearance order. `MaskWriter.build()` throws if a token repeats or the token set and the map disagree.
- **Paired vs atomic** (`TreeMasker`): an element with translatable children becomes an open token + masked children +
  close token; an empty element, CDATA, comment or processing instruction becomes one atomic token holding the
  exact source markup (entity references no longer exist in the tree: they are expanded on read). Never entered (always atomic): `math`, `svg`, `pre`, `listing` in both dialects,
  and `code` in XHTML only.
- A literal `⟦` or `⟧` in prose is itself masked to an atomic token, so the gate can never be fooled by source text.
- Markdown masks by the source ranges of its inline constructs (`MarkdownSpans`); TXT has nothing to mask.
- Masking never touches `sourceInner` or `sourceHash`; masking identical bytes yields identical tokens.

### 4.4 The placeholder gate

`PlaceholderGate.compare` treats the tokens as a multiset: any token missing, duplicated or invented in the
translation is `ErrorCode.validation`, nothing is restored, and the translation is returned unaltered. `SafeDetails`
carries the expected tokens in full and the observed ones bounded. Order is deliberately not checked (a swapped or
concatenated pair passes — debt D7/D8, owned by the future chunker).

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
| DRM refusal, corrupt container, malformed fragment, gate mismatch, structure mismatch, unwritable XML character, unrepresentable TXT | `validation` |
| document id not open (registry miss), any other `Throwable` | `internal` |

Details never contain paths, bodies or exception messages (§3.1).

## 5. Runtime (`:app`, `:util`, `:ui`)

**Boot** (`Launcher.main`): `AppPathsResolver.resolve()` → `prepare()` (create dirs, warn on a network filesystem)
→ `SingleInstanceLock` (`FileChannel.tryLock` on `dataDir/bookloom.lock`) → resolve the log level
(`ua.bookloom.app.bootstrap.LoggingLevelResolver`, below) → `LoggingBootstrap` (programmatic Logback:
`bookloom.log`, 10 MB files, 14 days, 200 MB cap; console only in dev) → startup log line → `Application.launch`.
`BookLoomApplication.init` builds the Guice injector from `ua.bookloom.app.CoreModules` (`AppModule`,
`DocumentModule`, `LlmModule`, `PersistenceModule`, `PipelineModule`) plus `UiModule`, and runs `AppLifecycle` phase
one and two (phase two is empty by design; the order is enforced). `start` shows a 1024×700 `Scene` (minimum
960×640) over `AppShellView` with `theme.css`.

- **Second launch**: `ErrorCode.busy`, a "BookLoom is already running" window, exit **0**.
- **Startup failure**: `StartupFailureDialog` on a bare `Platform.startup`, waits ≤ 300 s, exit **1**.
- **Environment**: `BOOKLOOM_ENV` → `-Dbookloom.env` → jpackage property → dev; data/log directories per OS with a
  `-Dev` suffix in dev; `BOOKLOOM_DATA_DIR` overrides.
- **`AppVersion`** reads `version.properties` generated from the git tag, or `dev`.
- **`:ui`** (`add-ui-translation-workspace`) is six packages: `ua.bookloom.ui` (`AppShellView` chrome built in code,
  `Navigator`, `ViewNames`, `Theme`), `.screen` (Import, Book Brief, Structure, Translating, Export, Settings — FXML
  plus a thin controller), `.state` (`StateMirror`, `RunSession`, `TranslationRunner`, the viewmodels, the file
  revealer), `.i18n` (`en`/`uk` bundles, typed `MessageKey`, a locale chosen by the OS — no in-app switch), `.notify`
  (error dialog, toasts) and `.theme` (44 role tokens in light and dark, one stylesheet). Projects, Names & style and
  Review are inert navigation entries with no screen. The window opens 1024×700, minimum 960×640, and its content
  scrolls when it does not fit.

**Log level.** `ua.bookloom.app.bootstrap.LoggingLevelResolver` is a pure function over injected `getEnv`/
`getProperty` (the same shape as `AppEnvironment.resolve`): `BOOKLOOM_LOG_LEVEL`, else the system property
`bookloom.log.level` (either in any letter case), else `INFO` for an installed app and `DEBUG` for a development run;
a value that names no level falls back to that same default and is named in one `WARN` line.
`LoggingBootstrap.configure` sets the resolved level on the `ua.bookloom` loggers and `WARN` on everything else, adds
`%X{job}` to the log pattern, and writes one `INFO` line naming the level and where it came from. Book text, prompts
and model replies are logged at `TRACE` only — never at `DEBUG` or above.

**Command line.** `ua.bookloom.app.bootstrap.TranslateLauncher` repeats `Launcher`'s pre-injector steps — paths,
single-instance lock, logging — without starting JavaFX, then builds its injector from `CoreModules` alone (no
`UiModule`) and runs `ua.bookloom.app.cli.TranslateCommand`:

```
./gradlew -q :app:translate --args="'<book>' [--to <lang>] [--from <lang>] [--overwrite]"
```

`TranslateCommand` gets its model with `ModelSelection("pseudo", "uppercase")` and enables no pause points, so the
job runs start to finish; it writes `<name>.<to><suffix>` beside the source (`<to>` defaults to `uk`), prints one
line — the completed report, or the stopped report's/error's title and message — and returns 0 when the job
completed, 1 when it did not (the book could not be opened, the job failed or was cancelled, the destination already
exists without `--overwrite`, or BookLoom is already running), or 2 for invalid arguments, printing the reason before
the usage line. Quoting a book path with spaces, and the log volume a run produces, are in
`docs/DEVELOPMENT.md#running`.

## 6. What it can and cannot do

**Can, through `DocumentPort` and its tests:** open any of the four formats, including an EPUB whose spine lists
files that are not there; refuse DRM and corrupt containers with a typed error; produce ordered, anchored, masked
segments; restore a translated masked segment safely or refuse it; write the book back canonical-equal (byte-exact
for TXT); release an opened book (`close`).

**Can, through `TranslationEngine` and the command line:** translate a whole book of any of the four formats end to
end with the offline `pseudo` model — accept a segment whose markup restores, flag one the model could not translate
(an error, an empty or truncated reply), and stop the job on any other failure; pause, resume and cancel a running
job at segment, section, stage and error boundaries; export only after the written file re-opens with the source's
segment count, never silently overwriting a source or an existing destination.

**Can, as an app:** start once, refuse a second instance, log to the right per-OS directory at a level
`BOOKLOOM_LOG_LEVEL` controls, package into a native image on macOS, Windows and Linux; and, through the window, open
a book of any of the four formats, choose a target language, destination and overwrite, verify a provider (Ollama or
LM Studio preset), choose a model, start / pause / resume / stop / start again, watch progress and the activity log,
and show the written file in the file manager. Pause and Stop abort the request in flight; a stopped run is final and
writes nothing.

**Cannot yet:** persist anything (no resume across a restart, no remembered theme), detect the source language (the
selector is read-only), switch the interface language in the app (the OS chooses `en` or `uk`), open the Projects,
Names & style or Review entries, or chunk long segments. `:persistence` still holds one empty Guice module.

**Text the walker never reaches** — everything outside a content document's `<body>` or an FB2 `<body>`; after a
translation these stay in the source language until the metadata-units change lands. Counted on the owner's 216-book
corpus: NCX table-of-contents labels (11,997 across all 197 EPUBs), EPUB 3 nav documents (20 books), each content
document's `<head><title>` (7,450), OPF `dc:title` (197) and `dc:description` (106), FB2 `annotation` (6 books,
24 paragraphs) and `book-title` (9), `img@alt` (1). Everything that *is* inside a body is covered by the structural
rule — the same census found no text-owning body element the walker does not handle.

## 7. Known limits and open debt

Code-visible today:

- `Document.detectedSourceLang` is never populated; every reader passes `null`.
- Markdown: raw-HTML blocks, code blocks and link-reference definitions are never translated; a shortcut reference
  link `[text]` becomes literal text (D9); the escaper stops after 20 rounds (D15-adjacent); the structure check is
  per segment, so a construct spanning segments is not caught (D16).
- FB2: a non-breaking space inside translatable text is now a plain character the model sees; a translation that
  replaces it with an ordinary space is accepted (the QA gate could count them later if it ever matters).
- Long segments (one of 26,306 chars; 15 books over 5,000) and placeholder-dense ones (248 tokens in one segment of a
  technical book) are passed through whole; splitting is the chunker's job (D5).
- The gate is order-insensitive (D7) and the flat token map carries no pairing information (D8) — both by design
  until chunking exists.
- `EpubWriter` mutates the registry-held tree in place on write (D4).

Resolved on 2026-09-11/12: D2 (missing `mimetype` synthesized on write), D11 (FB2 line endings echoed), D13
(`FragmentRange` record), D6 (FB2 entities expanded once; `Fb2EntityExpansionTest`). Resolved by
`add-translation-engine-and-cli` (archived 2026-09-20): D3 — `DocumentPort.close(Document)` now exists
and is called, by `:pipeline`'s job after it snapshots the source and by `BookExporter` after each write attempt, so
a translated book no longer stays open in memory for the rest of the process's life. The full list with history:
`CHANGE_BACKLOG.md#decision-debt`.

**`add-translation-engine-and-cli`'s own review found further gaps**, mostly `:document` behaviour a real
translation now exposes for the first time — an untranslated table of contents, an XHTML file that still declares
`xml:lang="en"`, a command line with no Ctrl+C handling — each recorded with a reproduction in
`docs/next_features.md`.

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
| The app boots, locks, logs | `app/AppBootSmokeTest`, `app/bootstrap/SingleInstanceLockTest`, `app/bootstrap/LoggingBootstrapTest`, `document/DocumentModuleTest` (Guice wiring) |
| Layering | `archtest/BoundaryRulesTest` + `RuleViolationFixtureTest` |

All test paths are under `modules/<module>/src/test/java/ua/bookloom/<module>/`. Run one with
`./gradlew :document:test --tests 'ua.bookloom.document.golden.FormatGoldenRoundTripTest'`.

**The corpus sweep** (`golden/CorpusVerificationTest`, tag `corpus`):
`BOOKLOOM_CORPUS_DIR=/path ./gradlew :document:corpus`. Last recorded run (2026-09-12, the owner's 216-book
`Books_Examples` set: 197 EPUB, 9 FB2, 9 TXT, 1 Markdown, 367 MB): 216/216 opened, 216/216 canonical-equal on the
zero-edit identity and fixed-point probes, 216/216 mutation and idempotence, 216/216 mask-probe pass; 725,542
segments and 272,817 placeholders; text coverage per book (words of visible body text reached by segments) min
0.9808, median 1.0, the two lowest being a word-boundary counting artefact on span-built paragraphs; 2 min 39 s. Details and the defects it found: `docs/implementation_plan/notes-corpus-verification.md`.

**The running app**: `./gradlew :app:run` prints `app started`, `injector built and two-phase init complete`,
`window shown, initialView=…`; `./scripts/launch-smoke.sh` asserts the same three lines against the packaged image.

## 9. What comes next

The walking skeleton is done and so is the face on it: a book of any of the four formats goes through the whole
pipeline from the command line and from the window, with the offline `pseudo` model or a real local provider (§1, §5,
§6). Next: `:persistence` — projects, settings and per-chunk checkpoints, which is what makes resume across a restart,
a remembered theme and a language switch possible. Chunking, source-language detection, QA, judge and glossary follow,
one small change each; the order is `docs/implementation_plan/CHANGE_BACKLOG.md`.
