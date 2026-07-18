**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md`, `docs/implementation_plan/02_STORY_FORMAT.md`,
`docs/implementation_plan/03_TRACEABILITY.md`

# Module Inventory

This is the **canonical, authoritative list of Gradle/JPMS modules and their package sub-paths**. Every story's
`modules:` front-matter cites entries from this file, and `./gradlew traceCheck` fails when a story cites a module path
that does not appear here (see `docs/implementation_plan/03_TRACEABILITY.md`). The module boundaries, allowed dependency
edges, and ArchUnit rules that govern this inventory are defined in
`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`.

## how-to-cite-a-module {#how-to-cite-a-module}

Cite a module path as `:<module>/<package>` — the Gradle subproject name, a slash, and the fully-qualified package.
Examples: `:document/ua.bookloom.document.epub`, `:llm/ua.bookloom.llm.provider`, `:pipeline/ua.bookloom.pipeline.qa`,
`:persistence/ua.bookloom.persistence.dao`, `:ui/ua.bookloom.ui.screen`. A story that touches only a module root cites
`:<module>/ua.bookloom.<module>`.

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
- **ArchUnit** boundary tests live in a shared `arch-test` source set and run in CI and the fast `pre-push` subset.
- **Test tiers:**
    - **Unit** — pure JVM, no I/O, Mockito/AssertJ. Runs in `pre-push`.
    - **Integration** — temp SQLite DB file or `:memory:`; WireMock for the LLM HTTP seam; golden-fixture round-trip for
      documents. CI + on demand.
    - **UI** — TestFX + Monocle, headless. CI (excluded from `pre-push`).
    - **Arch** — ArchUnit boundary assertions.
- A proving test's first line declares `// Proves: STORY-NNN-AC-N` (see `docs/implementation_plan/03_TRACEABILITY.md`).

## adding-a-module-note {#adding-a-module-note}

**New modules and new package sub-paths are added to this file in the same story that introduces them.** A story that
creates a package must (a) add the row here and (b) list it in that story's `modules:`. `traceCheck` rejects a story
citing a module path absent from this inventory, so the inventory update and the code land together.

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
| `:app/ua.bookloom.app` | Presentation | `Launcher`, `Application` subclass, Guice composition root, two-phase init, and the **process single-instance lock** — acquired pre-injector (`FileChannel.tryLock` on the `:util.paths`-resolved lock file) before the DB opens (see `#lock-ownership`) | `:app/src/test/java/ua/bookloom/app` · Integration |

## non-module-citable-targets {#non-module-citable-targets}

Some stories (notably PHASE_00 scaffold work) touch **repository-level, non-Java build/tooling targets** that are not
Gradle subprojects or Java packages but must still be citable in a story's `modules:` front-matter so `traceCheck`
accepts them. These are the **only** allowed non-`:module/package` targets:

| Citable target | What it is                                                                                                                   | Typical citing work                                                                                      |
|----------------|------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `build-logic`  | The included build holding the precompiled convention plugins (`bookloom.*-conventions.gradle.kts`)                          | Toolchain/JPMS/lint/format/test conventions, the `trace`/`traceCheck` task wiring                        |
| `arch-test`    | The shared ArchUnit source set that hosts the boundary tests across modules                                                  | ArchUnit boundary rules (`docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules`) |
| `ci`           | The GitHub Actions workflow definitions (quality job, packaging matrix, release)                                             | CI pipeline, coverage/license/SCA gates, jpackage smoke                                                  |
| `tooling`      | Repo-level tooling not compiled into a module: Lefthook hooks, gitleaks config, license policy file, icon-generation scripts | Git hooks, license allowlist file, `assets/icon/*.py`                                                    |

A story may cite these verbatim (e.g. `modules: [build-logic, arch-test]`); `traceCheck` treats them as present. They
carry no `ua.bookloom.*` package path because they contain no application module code.

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
