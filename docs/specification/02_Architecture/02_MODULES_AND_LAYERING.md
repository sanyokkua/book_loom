**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
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
    exports ua.bookloom.api.llm;        // Provider, ChatRequest/Response, ProviderProfile
    exports ua.bookloom.api.pipeline;   // TranslationEngine, JobHandle, QualityDial
    exports ua.bookloom.api.persistence;// repositories: ProjectRepo, SegmentRepo, ...
}
```

**Ports declared here, implemented elsewhere:** `DocumentPort` (→ `:document`), `Provider`/`ProviderFactory` (→ `:llm`),
`TranslationEngine` (→ `:pipeline`), the repository interfaces (→ `:persistence`).

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

```
module ua.bookloom.llm {
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires java.net.http;                 // JDK HttpClient
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.google.guice;              // this module owns a Guice Module (constructor injection)
    exports ua.bookloom.llm;               // InferenceService, ProviderFactory
    opens ua.bookloom.llm.dto to com.fasterxml.jackson.databind; // JSON records
    opens ua.bookloom.llm to com.google.guice; // Guice reflects on the impl for injection
}
```

### pipeline {#module-pipeline}

**Responsibility:** the translation engine — chunk packing, context assembly, the tiered draft→QA→judge→self-heal loop,
name/term dictionary, context-aware TM, rolling summary, deferred-resolution + backward revision, prompt builder,
quality dial. FX-free. Implements `TranslationEngine`.

```
module ua.bookloom.pipeline {
    requires ua.bookloom.api;
    requires ua.bookloom.util;
    requires ua.bookloom.document;   // for reassembly hand-off (via port at api where possible)
    requires ua.bookloom.llm;
    requires ua.bookloom.persistence;
    requires com.ibm.icu;             // ICU4J BreakIterator (sentence split on overflow)
    requires com.github.pemistahl.lingua; // language detection
    requires com.google.guice;        // this module owns a Guice Module (constructor injection)
    exports ua.bookloom.pipeline;    // TranslationEngineImpl
    provides ua.bookloom.api.pipeline.TranslationEngine with ua.bookloom.pipeline.TranslationEngineImpl;
    opens ua.bookloom.pipeline to com.google.guice; // Guice reflects on the impl for injection
}
```

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
    requires com.google.guice;
    requires com.ibm.icu;             // ICU4J MessageFormat for i18n plurals/gender (DD-48)
    requires org.controlsfx.controls; // Notifications, ToggleSwitch, SegmentedButton
    requires org.kordamp.ikonli.javafx;
    requires atlantafx.base;
    opens ua.bookloom.ui.view to javafx.fxml, com.google.guice; // FXML controller injection
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

A failing ArchUnit test fails the build; the boundary is not a guideline.
