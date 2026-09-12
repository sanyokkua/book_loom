# BookLoom — Architecture and Current Capabilities

*Generated from the code as of 2026-09-11 (branch `feature/add-inline-masking-and-placeholder-gate`). Sections are
numbered and stable so a prompt or a review can say "per §4.3". When the code changes, change this file in the same
commit. `docs/DEVELOPMENT.md` says how to build and run; `AGENTS.md` is the operating manual.*

## 1. What BookLoom is, and where it honestly stands

BookLoom is a local-first, offline desktop application that will translate whole books (EPUB, FB2, Markdown, TXT)
with a locally-run LLM (Ollama / LM Studio) or any OpenAI-compatible endpoint, preserving the book's structure, IDs,
images and fonts through a canonical-equal round trip.

**Today the document engine is complete and nothing is translated.** A book of any of the four formats is parsed,
its text is masked to placeholders, a translated segment can be restored behind a hard gate, and the book is written
back canonical-equal — verified on 213 real books. The application launches to an empty themed window. There is no
LLM client, no pipeline, no database and no screen beyond that window (§6). The next unit of work is the walking
skeleton: one EPUB through one local model to a translated EPUB, started from the UI (§9).

| | |
|---|---|
| Production Java | ≈10,800 lines in 8 JPMS modules (`:document` 7,700; `:api` 1,120; `:app` 1,000; `:util` 620; the rest ≈50 each) |
| Tests | 616 methods; zero Mockito; fixtures are real bytes in temp dirs |
| Stack | Java 25, JavaFX 26, Gradle 9 (Kotlin DSL), Guice 7, jsoup + JDOM2 + commonmark + ICU4J, JUnit 5 + AssertJ |

## 2. Modules and layering

```
        :app  ──►  :ui                      presentation — the only modules that see JavaFX
          │
       :pipeline                            orchestration (empty)
       /   │    \
 :document :llm :persistence                services — implement :api ports; FX-free
       \   │    /
        :util                               foundation
          │
        :api                                contracts only; framework-free
```

Edges point downward only. `settings.gradle.kts` maps `:api` … `:app` onto `modules/<name>` (ADR-0021).

**Eight ArchUnit rules** (`modules/app/src/archTest/java/ua/bookloom/archtest/ArchitectureRules.java`, run by
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

Guice wiring: `AppModule` (paths, environment, startup context, a background pool and a virtual-thread IO executor)
plus `DocumentModule` (`DocumentPort` → `DocumentService`). `LlmModule`, `PipelineModule`, `PersistenceModule`,
`UiModule` bind nothing.

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
| `Segment` | one translatable block: `id ({unit}:{ordinal}), kind, sourceInner, masked, placeholders (ordered token→fragment), sourceHash, anchor, targetInner?, status, confidence`. Today always `PENDING`, `targetInner == null` |
| `SegmentKind` | 13 values; only `PARAGRAPH`, `HEADING`, `LIST_ITEM`, `TABLE_CELL` are produced today |
| `SkeletonAnchor` | sealed: `NodeAnchor(nodePath, runIndex)` for tree formats (index path, not id — ids can repeat), `ByteSpanAnchor(start, end)` for buffer formats |
| `SkeletonHandle(opaqueId)` | names a parsed tree only `:document` can resolve, so parsers never leak onto the shared classpath |
| `BookFormat` | `EPUB, FB2, MARKDOWN, TXT`; export is same-format only |

### 3.3 `DocumentPort` — the engine's whole surface

```java
Result<Document> open(Path source);
Result<Path>     write(Document document, Path destination, String targetLanguage);
Result<String>   unmask(BookFormat format, Segment segment, String translatedMasked);
```

`open` parses and masks; `write` reassembles what was opened (the parsed state lives in an in-memory registry keyed
by `Document.id`, so a document does not survive a restart); `unmask` validates a translated segment against the
placeholder gate and restores the markup. A gate or structure failure is `ErrorCode.validation`; anything unexpected
is `internal`.

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
→ `SingleInstanceLock` (`FileChannel.tryLock` on `dataDir/bookloom.lock`) → `LoggingBootstrap` (programmatic Logback:
`bookloom.log`, 10 MB files, 14 days, 200 MB cap; console only in dev) → startup log line → `Application.launch`.
`BookLoomApplication.init` builds the Guice injector from the six modules and runs `AppLifecycle` phase one and two
(phase two is empty by design; the order is enforced). `start` shows a 1024×700 `Scene` over `AppShellView` with
`theme.css`.

- **Second launch**: `ErrorCode.busy`, a "BookLoom is already running" window, exit **0**.
- **Startup failure**: `StartupFailureDialog` on a bare `Platform.startup`, waits ≤ 300 s, exit **1**.
- **Environment**: `BOOKLOOM_ENV` → `-Dbookloom.env` → jpackage property → dev; data/log directories per OS with a
  `-Dev` suffix in dev; `BOOKLOOM_DATA_DIR` overrides.
- **`AppVersion`** reads `version.properties` generated from the git tag, or `dev`.
- **`:ui`** is `Theme` (resolves the single stylesheet: four brand anchors, five role tokens) and a `StackPane`
  holding one `Label("BookLoom")`.

## 6. What it can and cannot do

**Can, through `DocumentPort` and its tests:** open any of the four formats, including an EPUB whose spine lists
files that are not there; refuse DRM and corrupt containers with a typed error; produce ordered, anchored, masked segments; restore a translated masked segment safely or refuse it;
write the book back canonical-equal (byte-exact for TXT). **Can, as an app:** start once, refuse a second instance,
log to the right per-OS directory, package into a native image on macOS, Windows and Linux.

**Cannot yet:** call any LLM, translate anything, persist anything, resume, show any screen with content, detect the
source language, or chunk long segments. `:llm`, `:pipeline`, `:persistence` contain one empty Guice module each.

**Text the walker never reaches** — everything outside a content document's `<body>` or an FB2 `<body>`; after a
translation these stay in the source language until the metadata-units change lands. Counted on the owner's 216-book
corpus: NCX table-of-contents labels (11,997 across all 197 EPUBs), EPUB 3 nav documents (20 books), each content
document's `<head><title>` (7,450), OPF `dc:title` (197) and `dc:description` (106), FB2 `annotation` (6 books,
24 paragraphs) and `book-title` (9), `img@alt` (1). Everything that *is* inside a body is covered by the structural
rule — the same census found no text-owning body element the walker does not handle.

## 7. Known limits and open debt

Code-visible today:

- `Document.detectedSourceLang` is never populated; every reader passes `null`.
- Parsed state is held in memory per open document; `OpenDocumentRegistry.close(id)` exists and nothing calls it yet.
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
(`FragmentRange` record), D3 (`close` on the registry), D6 (FB2 entities expanded once; `Fb2EntityExpansionTest`). The full list with history: `CHANGE_BACKLOG.md#decision-debt`.

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
`primary stage shown`; `./scripts/launch-smoke.sh` asserts the same three lines against the packaged image.

## 9. What comes next

The walking skeleton, as the owner's next unit of work: a minimal `:llm` chat client (Ollama-native and
OpenAI-compatible, tested at the HTTP seam with WireMock for both dialects), a minimal `:pipeline` loop (open →
per segment: prompt with the masked text → `unmask` → keep source and flag on a gate failure → write), and one `:ui`
screen (file, endpoint, model, target language, Translate, progress). Done means a real small EPUB translated with a
local Ollama from the app and opened in a reader. Persistence, chunking, QA, judge, glossary, theming and
localization follow, one small change each; the order is `docs/implementation_plan/CHANGE_BACKLOG.md`.
