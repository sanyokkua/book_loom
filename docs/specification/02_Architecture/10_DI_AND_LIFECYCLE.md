**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`

# Dependency Injection and Lifecycle

Google Guice 7 wires the application, constructor-injection only. Each Gradle module contributes one Guice module
binding its ports to its implementations; `:app` is the single composition root. This document fixes the module layout,
the composition root, two-phase init, eager singletons, the single-instance lock, and graceful shutdown.

## guice-modules {#guice-modules}

One `AbstractModule` per Gradle module, co-located with the implementation it binds:

| Guice module                         | Binds (port → impl)                                                  |
|--------------------------------------|----------------------------------------------------------------------|
| `DocumentModule` (`:document`)       | `DocumentPort → DocumentService`                                     |
| `LlmModule` (`:llm`)                 | `ProviderFactory → ProviderFactoryImpl`, `InferenceGate` (singleton) |
| `PersistenceModule` (`:persistence`) | repository ports → JDBI DAOs, `Jdbi`, `DataSource`                   |
| `PipelineModule` (`:pipeline`)       | `TranslationEngine → TranslationEngineImpl`, assemblers, QA          |
| `UiModule` (`:ui`)                   | viewmodels, `Navigator`, state mirror, controllers via factory       |
| `AppModule` (`:app`)                 | `ExecutorService` (daemon), virtual-thread executor, config, wiring  |

Rules: constructor injection only (`@Inject` on the constructor); `@Singleton` for stateless services and shared
infrastructure (gate, executors, mirror, DAOs); no field/setter injection; no static holders.

## composition-root {#composition-root}

`:app` is the **only** place that assembles the full injector:

```java
Injector injector = Guice.createInjector(
    new AppModule(dataDir, executor),
    new PersistenceModule(),
    new LlmModule(),
    new DocumentModule(),
    new PipelineModule(),
    new UiModule());
```

No other module constructs an injector. `FXMLLoader.setControllerFactory(injector::getInstance)`
(`07_UI_ARCHITECTURE_JAVAFX.md#bootstrap`) makes controllers Guice-managed. Because ports live in `:api`, the graph
stays acyclic and every binding targets an interface.

## two-phase-init {#two-phase-init}

Startup separates **construction** from **resource opening** so the object graph exists before any IO:

- **Phase 0 — resolve environment & paths.** Before the injector is built, resolve `isDev`, the per-OS data/log
  directories, and create them; acquire the single-instance lock; publish the log dir and start logging. This is the
  foundation the rest depends on (`11_APP_ENVIRONMENT_AND_PATHS.md#startup-order`, DD-39) — logging needs the log dir
  and SQLite needs the data dir, so it runs first.
- **Phase 1 — construct services.** Create the injector; instantiate singletons (logging, settings, gate, executors,
  engine, viewmodels). No DB is open yet; repositories hold an unopened handle. This is pure and fast.
- **Phase 2 — open resources + backfill.** Open the SQLite DataSource, run Flyway migrations, then **backfill** the
  repositories with the live `Jdbi`/connection. Load persisted settings and the `current_provider` **new-project
  default** (it seeds a freshly-created project's binding and is never consulted at resume,
  `06_DATA_MODEL_SQLITE.md#app_state`). Only after Phase 2 is the app ready to accept work.

Splitting the phases keeps DI wiring side-effect-free (safe to construct in tests without a DB) and gives a single,
ordered place where migrations run before any DAO is used. **Migrations run to completion inside Phase 2, before any
`JobHandle` exists** — there is no in-flight run to interrupt, so `stop()` can never race or cancel a migration
(see [graceful-shutdown](#graceful-shutdown)). A failed migration rolls back completely and surfaces as a typed startup
error (`06_DATA_MODEL_SQLITE.md#corruption-and-recovery`).

## eager-singletons {#eager-singletons}

Logging and the settings **service** are bound as **eager singletons** (`asEagerSingleton()` / `Stage.PRODUCTION`) so
their bindings exist first: logging must exist before anything can log, and the settings service must be constructed
before the first scene is assembled. "Eager" here means only that the *binding/service object* exists in Phase 1 — it
does **not** mean persisted values are readable then. The settings **repository is not backfilled until Phase 2**, so
**theme and language reads are valid only post-Phase-2**; any read before then has no DB behind it. Consequently the
**first scene is built after Phase 2**, and if no `ui.language` row exists yet it falls back to the **OS locale**
(DD-34, via the injectable `Locale` provider). The gate, executors, and state mirror are ordinary singletons created on
first use during Phase 1.

## logging-bootstrap-order {#logging-bootstrap-order}

Logback is configured **programmatically**, not from a static `logback.xml` file appender, so the resolved per-OS **log
directory is applied before the first logger is created** (`11_APP_ENVIRONMENT_AND_PATHS.md#startup-order`, step 5). A
static file appender declared in XML would bind to a default path at class-load time, before the log dir is known.
Because a class-level `static final Logger` field would trigger `LoggerFactory` initialization before the programmatic
configuration runs, an **ArchUnit rule forbids static `Logger` fields in the bootstrap package** (the launcher/path/lock
classes on the pre-configuration path); those classes obtain a logger only after step 5.

## single-instance-lock {#single-instance-lock}

Acquiring the single-instance lock is a **pre-injector `:app`/`:util` concern**, done by the `Launcher` after resolving
paths and **before building the injector** — not a `:persistence` DAO. The launcher takes an exclusive lock via
`FileChannel.tryLock()` on **`bookloom.lock`** in the resolved per-OS data dir
(`11_APP_ENVIRONMENT_AND_PATHS.md#lock-and-atomic`). If the lock is already held, the launcher **shows a "BookLoom is
already running" dialog and exits** — it does **not** signal, focus, or raise the first window, and there is **no IPC**
(`05_RELIABILITY_AND_RESUME.md#single-instance`, `11_APP_ENVIRONMENT_AND_PATHS.md#edge-cases`). The SQLite database and
job state are thus never touched by two processes. Because dev builds resolve to a `-Dev` data dir, a dev and a
production instance hold distinct locks and can run at once.

## graceful-shutdown {#graceful-shutdown}

`Application.stop()` (FX lifecycle) runs the shutdown sequence with **bounded await budgets** so shutdown never hangs:

1. **Cancel on shutdown** — signal every active `JobHandle` to cancel; the engine stops at the next chunk boundary and
   abandons any in-flight HTTP request (`08_THREADING_CONCURRENCY.md#cancellation`). Worst-case time to reach the
   boundary is the HTTP read-timeout — cancellation is bounded, not instant.
2. **Flush pending checkpoints** (the last atomic write), **checkpoint the WAL, and close the `Jdbi`/DataSource
   cleanly** — with a checkpoint-flush budget of **≤ N s** (N chosen so a normal flush always completes; if it is
   exceeded the close proceeds and WAL replay covers the remainder on next open).
3. Shut down the daemon and virtual-thread executors (`shutdownNow`, then `awaitTermination` with a budget of **≤ M s**;
   on expiry, stop waiting and let the daemon threads die with the JVM).
4. Release the single-instance lock.

`stop()` can only cancel work that has a `JobHandle`; **migrations complete during Phase 2 before any `JobHandle`
exists**, so `stop()` never interrupts a migration (`two-phase-init`). Because accepted segments are already durable, an
abrupt kill is also safe — graceful shutdown just avoids losing the last in-flight chunk and leaves WAL in a clean
state; on OS crash / power loss at most the last in-flight commit is lost, never earlier accepted work
(`06_DATA_MODEL_SQLITE.md#storage-conventions`, ADR-0009).
