**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-09-15
**Cross-references:** `docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md`, `docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`

# Modules and Layering

Eight Gradle subprojects, each a JPMS module named `ua.bookloom.<module>` with base package `ua.bookloom.<module>`.
Dependencies point inward. This document is the authoritative per-module contract: responsibility, `requires`/`exports`/
`opens`, allowed dependencies, ports, and the ArchUnit rules that enforce the boundaries.

## dependency-direction {#dependency-direction}

Allowed edges (a → b means "a may depend on b"):

```
:app        → :ui :pipeline :document :llm :persistence :api :util
:ui         → :pipeline :api :util          (+ javafx.*)
:pipeline   → :document :llm :persistence :api :util
:document   → :api :util
:llm        → :api :util
:persistence→ :api :util
:util       → :api
:api        → (nothing internal)
```

No other internal edge is permitted. `:ui` never calls `:document`/`:llm`/`:persistence` directly — it goes through
`:pipeline` and `:api` ports. There are no cycles.

## module-contracts {#module-contracts}

### api {#module-api}

**Responsibility:** the dependency floor. Ports (interfaces implemented elsewhere), records/DTOs, enums, the `Result<T>`
envelope, `AppError`, `ErrorCode`. Framework-free, JavaFX-free, imports no other internal module.

```
module ua.bookloom.api {
    requires static org.jspecify;        // annotations only
    exports ua.bookloom.api;            // Result, AppError, ErrorCode
    exports ua.bookloom.api.document;   // DocumentPort, Segment, Unit records
    exports ua.bookloom.api.llm;        // built: ChatModel, ChatRequest/Response, ChatModelFactory, ModelSelection — Provider/ProviderProfile follow with the real clients (04_LLM_INTEGRATION.md#chat-contracts)
    exports ua.bookloom.api.pipeline;   // built: TranslationEngine, TranslationJob, JobEvent, JobProgress, JobReport — QualityDial follows with the quality dial
    exports ua.bookloom.api.persistence;// repositories: ProjectRepo, SegmentRepo, ...
}
```

**Ports declared here, implemented elsewhere:** `DocumentPort` (→ `:document`), `ChatModelFactory` (→ `:llm`; today
`ChatModelFactoryImpl` resolves only the built-in offline `pseudo` provider — `Provider`/`ProviderFactory` follow with
the real clients, `04_LLM_INTEGRATION.md#provider-architecture`), `TranslationEngine` (→ `:pipeline`), the repository
interfaces (→ `:persistence`).

### util {#module-util}

**Responsibility:** small stateless helpers — text normalization, IO, language codes, hashing (segment `source_hash`).
Depends only on `:api`.

```
module ua.bookloom.util {
    requires ua.bookloom.api;
    exports ua.bookloom.util.text;
    exports ua.bookloom.util.io;
    exports ua.bookloom.util.lang;
    exports ua.bookloom.util.hash;
}
```

### document {#module-document}

**Responsibility:** parse EPUB/FB2/MD/TXT into the skeleton+segment model, inline masking, unmask+validate, reassembly,
repackaging, round-trip fidelity. FX-free. Implements `DocumentPort`.

```
module ua.bookloom.document {
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires org.jdom2;            // FB2/EPUB XML round-trip
    requires org.jsoup;           // XHTML bodies
    requires org.commonmark;      // Markdown AST
    requires java.xml;
    requires com.google.guice;    // this module owns a Guice Module (constructor injection)
    // java.util.zip is in java.base
    exports ua.bookloom.document;         // DocumentService (impl of DocumentPort)
    provides ua.bookloom.api.document.DocumentPort with ua.bookloom.document.DocumentService;
    opens ua.bookloom.document to com.google.guice; // Guice reflects on the impl for injection
    // internal packages epub/fb2/md/txt/mask NOT exported
}
```

### llm {#module-llm}

**Responsibility:** provider abstraction (`Provider` + `ProviderProfile` + `ProviderFactory`), model discovery,
inference (`ChatRequest`/`ChatResponse`), `InferenceGate`, retry, HTTP→typed `AppError` mapping, three-stage
verification. FX-free.

**Built so far** (`add-translation-engine-and-cli`, ADR-0033): the engine-facing `ChatModel`/`ChatModelFactory`
contract lives in `:api.llm`; `ua.bookloom.llm.ChatModelFactoryImpl` resolves the provider id `pseudo` to
`ua.bookloom.llm.pseudo.PseudoChatModel` — a deterministic, offline model that upper-cases the last user message and
finishes `STOP` — and any other provider id, or a blank model id, to `ErrorCode.validation`. `ua.bookloom.llm.pseudo`
is deliberately **neither exported nor opened**, so JPMS (not just `ports-not-concretes`) stops `:pipeline` from
naming the concrete class. Everything below this point — the `Provider` port, real clients, discovery, the gate,
retry, HTTP mapping and verification — is not built yet.

```
module ua.bookloom.llm {
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires java.net.http;                 // JDK HttpClient — required since DD-01, unused until a real client lands
    requires org.slf4j;                     // ChatModelFactoryImpl and PseudoChatModel both log
    requires com.google.guice;              // this module owns a Guice Module (constructor injection)
    exports ua.bookloom.llm;               // ChatModelFactoryImpl
    opens ua.bookloom.llm to com.google.guice; // Guice reflects on the impl for injection
    // ua.bookloom.llm.pseudo is neither exported nor opened (see above)
}
```

Jackson, the `dto` package and `com.fasterxml.jackson.datatype.jsr310` arrive with the real HTTP clients
(`04_LLM_INTEGRATION.md#client-construction`); there is no wire format to serialize yet.

### pipeline {#module-pipeline}

**Responsibility:** the translation engine — chunk packing, context assembly, the tiered draft→QA→judge→self-heal loop,
name/term dictionary, context-aware TM, rolling summary, deferred-resolution + backward revision, prompt builder,
quality dial. FX-free. Implements `TranslationEngine`.

**Built so far** (`add-translation-engine-and-cli`, ADR-0033): the public `TranslationEngineImpl` (request checks —
language pattern, matching `BookFormat`, matching FB2 container, a destination that is not the source — before
creating a job); the package-private `TranslationJobImpl` (pause/resume/cancel at the segment, section, stage and
error boundaries, `08_THREADING_CONCURRENCY.md#cancellation`), `SegmentTranslator` (prompt building and the
accept/flag/stop decision) and `BookExporter` (reopen-validate-move). Chunking, context assembly, QA, the judge,
dictionaries, TM, the rolling summary and revision are not built yet.

```
module ua.bookloom.pipeline {
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires ua.bookloom.document;
    requires ua.bookloom.llm;
    requires ua.bookloom.persistence;
    requires org.slf4j;                // TranslationEngineImpl, TranslationJobImpl, SegmentTranslator, BookExporter log
    requires com.google.guice;         // this module owns a Guice Module (constructor injection)
    exports ua.bookloom.pipeline;     // TranslationEngineImpl only — the job, translator and exporter stay package-private
    opens ua.bookloom.pipeline to com.google.guice; // Guice reflects on TranslationEngineImpl for injection
}
```

`com.ibm.icu` (sentence-split overflow) and `com.github.pemistahl.lingua` (language detection) arrive with chunking
(change 12); there is no `provides ua.bookloom.api.pipeline.TranslationEngine with ...` clause yet — Guice, not
`ServiceLoader`, resolves the binding today.

### persistence {#module-persistence}

**Responsibility:** SQLite (Flyway migrations, JDBI DAOs), settings KV, project/segment/glossary/TM/summary stores,
secret references (env-var name / OS keychain), atomic writes, and the **DB-connection guard** (the per-DB open/WAL
discipline). FX-free. Implements the repository ports. The **process-level single-instance lock is NOT owned here** — it
is acquired pre-injector in `:app`/`:util` before the database opens (`10_DI_AND_LIFECYCLE.md#single-instance-lock`,
`11_APP_ENVIRONMENT_AND_PATHS.md`); this module only guards its own database connection.

```
module ua.bookloom.persistence {
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires org.xerial.sqlitejdbc;
    requires org.flywaydb.core;
    requires org.jdbi.v3.core;
    requires java.sql;
    requires com.google.guice;    // this module owns a Guice Module (constructor injection)
    exports ua.bookloom.persistence;
    provides ua.bookloom.api.persistence.ProjectRepository with ua.bookloom.persistence.ProjectDao;
    // ... one provides per repository port
    opens ua.bookloom.persistence to com.google.guice; // Guice reflects on the impl for injection
}
```

### ui {#module-ui}

**Responsibility:** JavaFX presentation — launcher glue, FXML views + controllers, viewmodels, observable state mirror,
screens/dialogs/notifications, theming, i18n. With `:app`, the only module that `requires javafx.*`.

```
module ua.bookloom.ui {
    requires ua.bookloom.api;
    requires ua.bookloom.pipeline;   // dispatches jobs to the engine port
    requires ua.bookloom.util;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires com.google.guice;
    requires com.ibm.icu;             // ICU4J MessageFormat for i18n plurals/gender (DD-48)
    requires org.controlsfx.controls; // ToggleSwitch, SegmentedButton (no AtlantaFX: see 07_UI_ARCHITECTURE_JAVAFX.md#theming)
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;
    opens ua.bookloom.ui to javafx.fxml, com.google.guice; // FXML controller injection; likewise ui.i18n,
                                                           // ui.notify, ui.screen, ui.state and ui.theme
    exports ua.bookloom.ui;
}
```

### app {#module-app}

**Responsibility:** `Launcher` (does not extend `Application`), the `Application` subclass that builds the Guice
injector, the single composition root, two-phase init, single-instance lock. See `10_DI_AND_LIFECYCLE.md`.

```
module ua.bookloom.app {
    requires ua.bookloom.ui;
    requires ua.bookloom.pipeline;
    requires ua.bookloom.document;
    requires ua.bookloom.llm;
    requires ua.bookloom.persistence;
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires javafx.graphics;
    requires com.google.guice;
    opens ua.bookloom.app to com.google.guice;
}
```

## ports-not-concretes {#ports-not-concretes}

Cross-module calls target interfaces in `:api`, never concrete classes in another implementation module. `:ui` holds a
`TranslationEngine`, not `TranslationEngineImpl`; `:pipeline` holds repository interfaces, not `ProjectDao`. Guice binds
each port to its implementation in the owning module's Guice module (see `10_DI_AND_LIFECYCLE.md`). This keeps the
dependency graph pointing at `:api` and makes every collaborator mockable.

## archunit-rules {#archunit-rules}

Enforced as JUnit tests in a shared `arch-test` source set, run in CI (and `pre-push` for the fast subset):

1. **fx-free-core** — no class in `:api`/`:util`/`:document`/`:llm`/`:pipeline`/`:persistence` depends on `javafx..`.
2. **dependency-direction** — `layeredArchitecture()` with layers Foundation (`api`,`util`) ← Services (`document`,
   `llm`,`persistence`) ← Orchestration (`pipeline`) ← Presentation (`ui`,`app`); each layer may only be accessed by the
   layers above it. No back-edges, no cycles (`slices().should().beFreeOfCycles()`).
3. **ports-not-concretes** — no class in `:ui` or `:pipeline` depends on a concrete `..Impl`/`..Dao`/`..Service` class
   residing in a different module; they may depend only on `ua.bookloom.api..` interfaces.
4. **no-http-in-core-except-llm** — only `:llm` may depend on `java.net.http..`.
5. **no-sql-in-core-except-persistence** — only `:persistence` may depend on `java.sql..`/`org.jdbi..`/`org.flywaydb..`.
6. **api-is-framework-free** — no class in `:api` depends on Guice, Jackson, JavaFX, JDBI, or any parser library. This
   rule scopes only to `:api`: the **service impl modules** (`:document`/`:llm`/`:pipeline`/`:persistence`) now
   legitimately `requires com.google.guice` and `opens <impl-pkg> to com.google.guice` because each owns a Guice
   `Module` and its constructor-injected implementation — that is the intended boundary. Guice may appear in the impl
   and `:ui`/`:app` modules; it must never appear in `:api`. `:api` ports and DTOs carry no `@Inject`/Guice annotations
   (constructor injection binds ports to impls in the owning module, not in `:api`).
7. **records-first** — DTO packages (`..dto`, `..api..`) contain records only (`should().beRecords()`), excluding
   declared exceptions.
8. **bootstrap-no-static-logger** — classes on the pre-logging bootstrap path (the `:app` `Launcher`
   /single-instance-lock acquisition and the `:util` paths resolver, `ua.bookloom.util.paths`) declare no static SLF4J
   `Logger` fields and do not touch `org.slf4j..` at class-init time: they run **before** logging is configured
   (paths-first startup order, DD-39, `11_APP_ENVIRONMENT_AND_PATHS.md`), so a static logger there would freeze an
   unconfigured logging context.
9. **no-inline-style-in-ui** — no class in `:ui` calls (or takes a method reference to) `setStyle` on any `javafx..` type —
   nodes, tooltips, context menus, menu items, tabs. Styling comes from the single `theme.css` token sheet and style
   classes (`getStyleClass()` stays allowed), so a theme swap reaches every element (`FR-THEME-5`,
   `.claude/rules/theming-tokens.md`).

A failing ArchUnit test fails the build; the boundary is not a guideline.
