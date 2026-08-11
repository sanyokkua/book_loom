**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-08-03
**Cross-references:** `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md`, `docs/implementation_plan/README.md`,
`docs/implementation_plan/06_DEFINITION_OF_DONE.md`

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
|---------------|-------------------------------------|---------------------------------------------------------------------------|
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
    - **UI** — TestFX + Monocle, headless. CI (excluded from `pre-push`).
    - **Arch** — ArchUnit boundary assertions.
- A covering test carries `// Covers: FR-*` plus a one-line EARS restatement of the obligation it proves, immediately
  above the test method (ADR-0016 R5).

## adding-a-module-note {#adding-a-module-note}

**New modules and new package sub-paths are added to this file in the same change that introduces them.** A change
that creates a package must (a) add the row here and (b) name it in its proposal's Impact section. Updating the
inventory is an item in every change's final green-gate task group
(`docs/implementation_plan/06_DEFINITION_OF_DONE.md#per-change-checklist`), so the inventory update and the code land
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

**Six of the eight are still placeholders. Two are not**, as of the `bootstrap-app-launch-and-empty-window` change:

| Module         | Package                     | Placeholder type                                          |
|----------------|-----------------------------|-----------------------------------------------------------|
| `:api`         | `ua.bookloom.api`           | **No longer a placeholder** — see below                   |
| `:util`        | `ua.bookloom.util.paths`    | **No longer a placeholder** — see below                   |
| `:document`    | `ua.bookloom.document`      | `DocumentModule` (Guice `AbstractModule`)                 |
| `:llm`         | `ua.bookloom.llm`           | `LlmModule`                                               |
| `:pipeline`    | `ua.bookloom.pipeline`      | `PipelineModule`                                          |
| `:persistence` | `ua.bookloom.persistence`   | `PersistenceModule`                                       |
| `:ui`          | `ua.bookloom.ui`            | `UiModule`                                                |
| `:app`         | `ua.bookloom.app`           | `AppModule` (the composition root's own Guice module)     |

**What `bootstrap-app-launch-and-empty-window` filled in.** The application now starts: a window opens, a second
launch is refused, and a packaged image launches on the module path.

| Module  | Package                          | What it now holds                                                                                                                                                                                             |
|---------|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:api`  | `ua.bookloom.api`                | **Seam F2, complete**: `Result<T>`, `AppError`, the fifteen-constant `ErrorCode` (retryability derived on the enum), and `SafeDetails` — a record whose *component set* is the safe-details allowlist          |
| `:util` | `ua.bookloom.util.paths`         | `AppPaths` (record), `AppPathsResolver`, `AppEnvironment` (dev/prod), `OsFamily`. Resolution is a pure function over injected `getEnv`/`getProperty`; `prepare` does the I/O                                    |
| `:app`  | `ua.bookloom.app`                | `BookLoomApplication`, `AppModule` (real bindings), `AppLifecycle` (two-phase init, ordering **enforced**), `AppVersion`, `StartupContext`                                                                     |
| `:app`  | **`ua.bookloom.app.bootstrap`**  | **New package, and a constraint rather than a folder**: `Launcher`, `SingleInstanceLock`, `LoggingBootstrap`, `StartupFailureDialog`. ArchUnit rule 8 scopes to it, so membership *is* the pre-logging declaration |
| `:ui`   | `ua.bookloom.ui`                 | `Theme` (resolves the single stylesheet from inside the module, since JPMS encapsulates it), `AppShellView`, and `theme.css`                                                                                    |

Note the `Launcher` row in `#inventory` below still reads `:app/ua.bookloom.app`; it lives in
`ua.bookloom.app.bootstrap`, because the ArchUnit rule that forbids a static logger on the pre-logging path
identifies that path by **package**, not by class name.

**What `add-document-skeleton-and-epub-roundtrip` filled in.** The first change with a real, user-observable
capability: an EPUB survives being parsed apart and reassembled with nothing translated (`document-round-trip`,
EPUB only — FB2/Markdown/TXT extend it next).

| Module      | Package                            | What it now holds                                                                                                                                                                                                                                                                     |
|-------------|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `:api`      | `ua.bookloom.api.document`          | Seam F1, complete in shape: `BookFormat`, `SegmentKind`, `SegmentStatus`, `SkeletonAnchor`, `Segment`, `SkeletonHandle`, `Unit`, `Document` (records/enums only — `masked`/`placeholders`/`detectedSourceLang` ship empty, filled by later changes per design.md D2) and `DocumentPort` |
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
|-------------|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
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

Every other package row in `#inventory` is **still unwritten** — cite it freely as a target, but expect to create it.

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

**`arch-test` is a source set inside `:app`**, at `:app/src/archTest/java`, holding `ua.bookloom.archtest` (the eight
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

**The two dependency gates are outside `check`, deliberately, and both need the network.** `checkLicense` answers "may
we distribute this?" from POMs on disk; `dependencyCheckAggregate` answers "does this have a known CVE?" against the
NVD feed, whose answer changes without the dependency graph changing at all. Run them by name when a change touches
dependencies. Two facts worth carrying forward:

- **Transitive versions are raised by a `constraints` block in `bookloom.java-conventions`, never by declaring the
  dependency.** Guava is the worked example: Guice pins `guava:31.0.1-jre` transitively, and the catalog's
  `guava = "33.6.0-jre"` does nothing on its own — a catalog entry nothing references is dead. The constraint is what
  makes the pin bind, and it keeps Guava transitive-only rather than putting it on every module's compile classpath.
  Any catalog version added purely to raise a transitive needs a matching constraint, or it is decoration.
- **SCA false positives are suppressed by CPE, risk acceptances by CVE.** `config/owasp/suppressions.xml` keeps the
  two in separate sections with different rules — a mis-matched CPE produces an open-ended CVE list, so pinning
  today's list lets tomorrow's through, and it carries no `until` because a wrong identifier does not expire.
  `javafx-graphics` is the worked example: its bundled `com.sun.*` packages read as vendor evidence and match it
  against OpenJDK itself. The correct `oracle:javafx` CPE is left live so a genuine JavaFX CVE still fails the gate.

## inventory {#inventory}

### :api {#module-api}

Contracts only: ports, records/DTOs, enums, the `Result<T>` envelope, `AppError`, `ErrorCode`. Framework-free, FX-free,
imports no other internal module.

| Package path                       | Layer      | Responsibility                                                                                                         | Test target · tier                                      |
|------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `:api/ua.bookloom.api`             | Foundation | `Result<T>`, `AppError`, `ErrorCode`, safe-details allowlist                                                           | `:api/src/test/java/ua/bookloom/api` · Unit             |
| `:api/ua.bookloom.api.document`    | Foundation | `DocumentPort`, `Segment`, `Unit`, skeleton DTOs                                                                       | `:api/src/test/java/ua/bookloom/api/document` · Unit    |
| `:api/ua.bookloom.api.llm`         | Foundation | `Provider`, `ProviderProfile`, `ProviderFactory`, `ChatRequest`/`ChatResponse`                                         | `:api/src/test/java/ua/bookloom/api/llm` · Unit         |
| `:api/ua.bookloom.api.pipeline`    | Foundation | `TranslationEngine`, `JobHandle`, `QualityDial`, segment-status enum                                                   | `:api/src/test/java/ua/bookloom/api/pipeline` · Unit    |
| `:api/ua.bookloom.api.persistence` | Foundation | Repository ports: `ProjectRepository`, `SegmentRepository`, `GlossaryRepository`, `TmRepository`, `SettingsRepository` | `:api/src/test/java/ua/bookloom/api/persistence` · Unit |

### :util {#module-util}

Small stateless helpers. Depends only on `:api`.

| Package path                   | Layer      | Responsibility                                                                                                                                                                                                                                  | Test target · tier                                  |
|--------------------------------|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| `:util/ua.bookloom.util.text`  | Foundation | Text normalization, whitespace, placeholder helpers                                                                                                                                                                                             | `:util/src/test/java/ua/bookloom/util/text` · Unit  |
| `:util/ua.bookloom.util.paths` | Foundation | **App-paths resolver** — per-OS data/log/config dir resolution, dev/prod `-Dev` separation via `isDev`, first-run creation, and the process **single-instance lock-file path**. Runs pre-injector, before logging and SQLite (DD-39, ADR-0015). | `:util/src/test/java/ua/bookloom/util/paths` · Unit |
| `:util/ua.bookloom.util.io`    | Foundation | Atomic file writes, temp files, low-level IO helpers (not path resolution — see `util.paths`)                                                                                                                                                   | `:util/src/test/java/ua/bookloom/util/io` · Unit    |
| `:util/ua.bookloom.util.lang`  | Foundation | Language-code helpers, BCP-47 handling                                                                                                                                                                                                          | `:util/src/test/java/ua/bookloom/util/lang` · Unit  |
| `:util/ua.bookloom.util.hash`  | Foundation | Content/segment `source_hash` hashing                                                                                                                                                                                                           | `:util/src/test/java/ua/bookloom/util/hash` · Unit  |

### :document {#module-document}

Parse EPUB/FB2/MD/TXT into skeleton+segment, inline masking, unmask+validate, reassembly, repackaging, round-trip
fidelity. FX-free. Implements `DocumentPort`.

| Package path                            | Layer    | Responsibility                                                 | Test target · tier                                                         |
|-----------------------------------------|----------|----------------------------------------------------------------|----------------------------------------------------------------------------|
| `:document/ua.bookloom.document`        | Services | `DocumentService` (impl of `DocumentPort`), format dispatch    | `:document/src/test/java/ua/bookloom/document` · Integration               |
| `:document/ua.bookloom.document.model`  | Services | Skeleton, segment list, `Unit` structures (seam F1)            | `:document/src/test/java/ua/bookloom/document/model` · Unit                |
| `:document/ua.bookloom.document.epub`   | Services | EPUB 2/3 read/write, mimetype-first repackage                  | `:document/src/test/java/ua/bookloom/document/epub` · Integration (golden) |
| `:document/ua.bookloom.document.fb2`    | Services | FB2 / `.fb2.zip` read/write, encoding preservation             | `:document/src/test/java/ua/bookloom/document/fb2` · Integration (golden)  |
| `:document/ua.bookloom.document.md`     | Services | Markdown AST parse/render (CommonMark)                         | `:document/src/test/java/ua/bookloom/document/md` · Integration (golden)   |
| `:document/ua.bookloom.document.txt`    | Services | TXT paragraph parse/render                                     | `:document/src/test/java/ua/bookloom/document/txt` · Integration (golden)  |
| `:document/ua.bookloom.document.mask`   | Services | Inline masking `⟦gN⟧`, unmask, placeholder-multiset validation | `:document/src/test/java/ua/bookloom/document/mask` · Unit                 |
| `:document/ua.bookloom.document.detect` | Services | DRM detection, source-language detection (Lingua)              | `:document/src/test/java/ua/bookloom/document/detect` · Unit               |

### :llm {#module-llm}

Provider abstraction with two client implementations (Ollama-native + OpenAI-compatible), model discovery, inference,
response handling, `InferenceGate`, retry, HTTP→typed error mapping, three-stage + preflight verification. FX-free.

| Package path                     | Layer    | Responsibility                                                                                                                                                                                                                   | Test target · tier                                                                  |
|----------------------------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `:llm/ua.bookloom.llm`           | Services | `InferenceService`, module facade                                                                                                                                                                                                | `:llm/src/test/java/ua/bookloom/llm` · Integration (WireMock)                       |
| `:llm/ua.bookloom.llm.provider`  | Services | `Provider` port, `ProviderProfile` per-kind data, `ProviderFactory` (kind → client)                                                                                                                                              | `:llm/src/test/java/ua/bookloom/llm/provider` · Unit                                |
| `:llm/ua.bookloom.llm.client`    | Services | The two client impls: `OllamaClient` (native `/api/*`, `options`) + `OpenAiCompatibleClient` (`/v1/*`); shared response handling (structured-output request, reasoning/fence strip, tolerant parse, repair retry, text fallback) | `:llm/src/test/java/ua/bookloom/llm/client` · Integration (WireMock, both dialects) |
| `:llm/ua.bookloom.llm.discovery` | Services | Model discovery (`supportsModelDiscovery`) + first-class manual model-ID entry; translator/judge slots                                                                                                                           | `:llm/src/test/java/ua/bookloom/llm/discovery` · Integration (WireMock)             |
| `:llm/ua.bookloom.llm.verify`    | Services | Three-stage verification on draft config + preflight connection/model-availability check before any inference                                                                                                                    | `:llm/src/test/java/ua/bookloom/llm/verify` · Integration (WireMock)                |
| `:llm/ua.bookloom.llm.gate`      | Services | Single-flight `InferenceGate` (seam F4)                                                                                                                                                                                          | `:llm/src/test/java/ua/bookloom/llm/gate` · Unit                                    |
| `:llm/ua.bookloom.llm.retry`     | Services | Service-owned retry, `Retry-After`, fresh per-attempt timeout                                                                                                                                                                    | `:llm/src/test/java/ua/bookloom/llm/retry` · Unit                                   |
| `:llm/ua.bookloom.llm.error`     | Services | HTTP/transport → typed `AppError`/`ErrorCode` mapping                                                                                                                                                                            | `:llm/src/test/java/ua/bookloom/llm/error` · Unit                                   |
| `:llm/ua.bookloom.llm.dto`       | Services | Jackson JSON records for both dialects (opened to Jackson only; `@JsonInclude(NON_NULL)`, tolerant)                                                                                                                              | `:llm/src/test/java/ua/bookloom/llm/dto` · Unit                                     |

### :pipeline {#module-pipeline}

The translation engine: chunking, context assembly, tiered translate→QA→judge→self-heal loop, name/term dictionary,
context-aware TM, rolling summary, deferred-resolution + backward revision, prompt builder, quality dial. FX-free.
Implements `TranslationEngine`.

| Package path                              | Layer         | Responsibility                                                     | Test target · tier                                                            |
|-------------------------------------------|---------------|--------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `:pipeline/ua.bookloom.pipeline`          | Orchestration | `TranslationEngineImpl`, job lifecycle                             | `:pipeline/src/test/java/ua/bookloom/pipeline` · Integration                  |
| `:pipeline/ua.bookloom.pipeline.chunk`    | Orchestration | Paragraph-grouped chunking, sentence-split overflow (ICU4J)        | `:pipeline/src/test/java/ua/bookloom/pipeline/chunk` · Unit                   |
| `:pipeline/ua.bookloom.pipeline.context`  | Orchestration | Context-package assembler (seam F5), edge placement                | `:pipeline/src/test/java/ua/bookloom/pipeline/context` · Unit                 |
| `:pipeline/ua.bookloom.pipeline.prompt`   | Orchestration | Prompt building, JSON-array draft request/parse                    | `:pipeline/src/test/java/ua/bookloom/pipeline/prompt` · Unit                  |
| `:pipeline/ua.bookloom.pipeline.qa`       | Orchestration | Deterministic QA checks, confidence, policy-aware language check   | `:pipeline/src/test/java/ua/bookloom/pipeline/qa` · Unit                      |
| `:pipeline/ua.bookloom.pipeline.judge`    | Orchestration | LLM-as-judge scoring vs τ                                          | `:pipeline/src/test/java/ua/bookloom/pipeline/judge` · Integration (WireMock) |
| `:pipeline/ua.bookloom.pipeline.heal`     | Orchestration | Directed-fix + reflect→improve self-heal                           | `:pipeline/src/test/java/ua/bookloom/pipeline/heal` · Integration (WireMock)  |
| `:pipeline/ua.bookloom.pipeline.memory`   | Orchestration | Name/term dictionary, context-aware TM, rolling bilingual summary  | `:pipeline/src/test/java/ua/bookloom/pipeline/memory` · Unit                  |
| `:pipeline/ua.bookloom.pipeline.glossary` | Orchestration | Glossary pre-scan / term proposal                                  | `:pipeline/src/test/java/ua/bookloom/pipeline/glossary` · Unit                |
| `:pipeline/ua.bookloom.pipeline.revision` | Orchestration | Deferred-resolution register, backward revision, consistency sweep | `:pipeline/src/test/java/ua/bookloom/pipeline/revision` · Integration         |
| `:pipeline/ua.bookloom.pipeline.dial`     | Orchestration | Quality-dial → parameters mapping                                  | `:pipeline/src/test/java/ua/bookloom/pipeline/dial` · Unit                    |

### :persistence {#module-persistence}

SQLite (Flyway, JDBI DAOs), settings KV, project/segment/glossary/TM/summary stores, secret references, atomic writes,
single-instance lock. FX-free. Implements repository ports.

| Package path                                      | Layer    | Responsibility                                                                                                                                                                          | Test target · tier                                                                      |
|---------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `:persistence/ua.bookloom.persistence`            | Services | Module facade, DB bootstrap, connection/WAL config                                                                                                                                      | `:persistence/src/test/java/ua/bookloom/persistence` · Integration (temp DB)            |
| `:persistence/ua.bookloom.persistence.migration`  | Services | Flyway migrations, schema versioning                                                                                                                                                    | `:persistence/src/test/java/ua/bookloom/persistence/migration` · Integration (temp DB)  |
| `:persistence/ua.bookloom.persistence.dao`        | Services | JDBI DAOs implementing repository ports                                                                                                                                                 | `:persistence/src/test/java/ua/bookloom/persistence/dao` · Integration (temp DB)        |
| `:persistence/ua.bookloom.persistence.settings`   | Services | Typed `settings(key,value,type)` KV store (seam F7)                                                                                                                                     | `:persistence/src/test/java/ua/bookloom/persistence/settings` · Integration (temp DB)   |
| `:persistence/ua.bookloom.persistence.checkpoint` | Services | Per-chunk crash-safe checkpoints + resume reader (seam F6)                                                                                                                              | `:persistence/src/test/java/ua/bookloom/persistence/checkpoint` · Integration (temp DB) |
| `:persistence/ua.bookloom.persistence.secret`     | Services | Secret references (env-var name / OS keychain), never the value                                                                                                                         | `:persistence/src/test/java/ua/bookloom/persistence/secret` · Unit                      |
| `:persistence/ua.bookloom.persistence.lock`       | Services | **DB-connection guard only** (per-DB open/WAL discipline). The process **single-instance lock is NOT here** — it is owned pre-injector by `:app`/`:util.paths` (see `#lock-ownership`). | `:persistence/src/test/java/ua/bookloom/persistence/lock` · Integration                 |

### :ui {#module-ui}

JavaFX presentation: launcher glue, FXML views + controllers, viewmodels, observable state mirror,
screens/dialogs/notifications, theming, i18n. With `:app`, the only module that `requires javafx.*`.

| Package path                | Layer        | Responsibility                                                                                                                                   | Test target · tier                                      |
|-----------------------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `:ui/ua.bookloom.ui`        | Presentation | UI module facade, navigation host                                                                                                                | `:ui/src/test/java/ua/bookloom/ui` · UI (TestFX)        |
| `:ui/ua.bookloom.ui.view`   | Presentation | FXML controllers (opened to `javafx.fxml`, Guice)                                                                                                | `:ui/src/test/java/ua/bookloom/ui/view` · UI (TestFX)   |
| `:ui/ua.bookloom.ui.screen` | Presentation | Screen controllers: Projects, Import, Book Brief, Structure, Names & Style, Translating, Review, Export, Settings                                | `:ui/src/test/java/ua/bookloom/ui/screen` · UI (TestFX) |
| `:ui/ua.bookloom.ui.dialog` | Presentation | Dialogs: welcome, add/edit provider, glossary term, retry-with-note, confirm-delete, unsaved-changes, error-with-details, export-complete, about | `:ui/src/test/java/ua/bookloom/ui/dialog` · UI (TestFX) |
| `:ui/ua.bookloom.ui.state`  | Presentation | Observable state mirror (seam F8), viewmodels                                                                                                    | `:ui/src/test/java/ua/bookloom/ui/state` · Unit + UI    |
| `:ui/ua.bookloom.ui.theme`  | Presentation | Token-only theming, light/dark, accent                                                                                                           | `:ui/src/test/java/ua/bookloom/ui/theme` · UI (TestFX)  |
| `:ui/ua.bookloom.ui.notify` | Presentation | Toasts, banners, error dialog surface                                                                                                            | `:ui/src/test/java/ua/bookloom/ui/notify` · UI (TestFX) |
| `:ui/ua.bookloom.ui.i18n`   | Presentation | ResourceBundle wiring, language switching                                                                                                        | `:ui/src/test/java/ua/bookloom/ui/i18n` · Unit + UI     |

### :app {#module-app}

`Launcher` (does not extend `Application`), the `Application` subclass building the Guice injector, the single
composition root, two-phase init, single-instance lock wiring.

| Package path           | Layer        | Responsibility                                                                                                                                                                                                                                           | Test target · tier                                 |
|------------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------|
| `:app/ua.bookloom.app` | Presentation | `Application` subclass, Guice composition root, two-phase init, `AppVersion`, and the launcher→application startup handoff | `:app/src/test/java/ua/bookloom/app` · Integration |
| `:app/ua.bookloom.app.bootstrap` | Presentation | Everything that runs **before logging exists**: `Launcher` (does not extend `Application`), the **process single-instance lock** — acquired pre-injector (`FileChannel.tryLock` on the `:util.paths`-resolved lock file) before the DB opens (see `#lock-ownership`) — the programmatic Logback configuration, and the pre-logging failure dialog. ArchUnit `bootstrap-no-static-logger` scopes to this package, so putting a class here **is** the declaration that it runs pre-logging | `:app/src/test/java/ua/bookloom/app/bootstrap` · Integration |

## non-module-citable-targets {#non-module-citable-targets}

Some changes (notably the Stage A infrastructure work) touch **repository-level, non-Java build/tooling targets**
that are not Gradle subprojects or Java packages but must still be citable in a change's Impact section and task
pointers. These are the **only** allowed non-`:module/package` targets:

| Citable target | What it is                                                                                                                   | Typical citing work                                                                                      |
|----------------|------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
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

Stories about the single-instance lock cite `:app/ua.bookloom.app` and `:util/ua.bookloom.util.paths`, not
`:persistence/ua.bookloom.persistence.lock`.

## note-on-guice-modules {#note-on-guice-modules}

Each Gradle module owns exactly one Guice `Module` binding its ports to implementations; the single composition root in
`:app` installs them (see `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`). A story adding a binding cites
the owning module's package here; there is no separate DI module inventory. Under JPMS each service `module-info.java`
(`:document`/`:llm`/`:pipeline`/`:persistence`) therefore `requires com.google.guice` and
`opens <impl-pkg> to com.google.guice` so Guice can reflect on the constructor-injected implementation
(`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`).
