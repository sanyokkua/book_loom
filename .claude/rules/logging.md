# Logging

Scope: all modules (`**/src/main/java/**`). Stack: SLF4J 2 + Logback 1.5. Spec: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`. See also `error-envelope.md`, `offline-and-privacy.md`.

## MUST

- **MUST** obtain a logger via Lombok `@Slf4j` on every class — SLF4J API only, never a concrete Logback type in code. `@Slf4j` generates exactly `private static final Logger log = LoggerFactory.getLogger(X.class)`, so this is the same idiom, spelled once instead of hand-written per class. The only exemption is the bootstrap-path classes named in the ArchUnit rule below (`ua.bookloom.app.bootstrap`, `ua.bookloom.util.paths`): there, take a **local, non-static** `LoggerFactory.getLogger(...)` — never a class-init-time field, Lombok-generated or hand-written — and only after the log directory is published. — Rationale: one idiomatic, backend-agnostic pattern almost everywhere; the bootstrap path is the one place a class-init-time logger (of any origin) is actively harmful, see the `bootstrap-no-static-logger` bullet below and `java-coding-style.md`.
- **MUST** use **parameterized logging** (`log.info("job {} chunk {}", jobId, idx)`), never string concatenation in log calls. — Rationale: no formatting cost when the level is disabled; no accidental `toString` of large objects.
- **MUST** clear MDC in a `finally` block whenever it is set (e.g. per-job `jobId`), so context never leaks across threads/tasks. — Rationale: pooled/virtual threads reuse carriers; stale MDC misattributes logs.
- **MUST NOT** log secrets, tokens, `Authorization` headers, resolved credential values, full request/response bodies, or file contents at any level. — Rationale: privacy invariant; the app runs offline and must not persist secrets anywhere.
- **MUST** write to a **rolling file appender in the per-OS log directory** (platform-appropriate app-log location) with a human-readable pattern; console output is dev-only. — Rationale: diagnosable logs in the right place, size-bounded.
- **MUST** configure Logback **programmatically** at bootstrap and **publish the resolved log dir before the first `LoggerFactory.getLogger(...)` call** (paths-first startup order, DD-39/ADR-0015). No class on the pre-logging bootstrap path — the `:app` `Launcher`/lock acquisition and the `:util` paths resolver (`ua.bookloom.util.paths`) — may declare a static SLF4J `Logger` or touch `org.slf4j..` at class-init time; enforced by ArchUnit rule 8 `bootstrap-no-static-logger` (`02_MODULES_AND_LAYERING.md`). — Rationale: a static logger that fires before configuration pins Logback to a wrong/default path.
- **MUST NOT** use `System.out`/`System.err` (or `printStackTrace`) for application logging. — Rationale: everything goes through SLF4J so levels, format, and destination are controlled.
- **MUST** treat `LoggingBootstrap` (`ua.bookloom.app.bootstrap.LoggingBootstrap`), and **only** that class, as exempt from the "SLF4J API only" rule above: it is the one place Logback is configured programmatically (the bootstrap MUST two bullets up), so it alone may import concrete `ch.qos.logback.classic.*`/`ch.qos.logback.core.*` types. Every other class — including every other bootstrap-path class — stays on the SLF4J facade. — Rationale: the facade-purity rule needs exactly one, explicitly named exception, not a silent hole a reviewer has to rediscover.
- **MUST** pass a logged `Throwable` as the **last argument** to the SLF4J call, never interpolated into a `{}` placeholder: `log.error("Failed to save file: {}", filePath, e)`, not `log.error("Failed: {}", e.getMessage())` — the second form silently discards the stack trace. — Rationale: SLF4J only renders a full stack trace when the `Throwable` is the trailing varargs argument.
- **MUST** install a single `Thread.setDefaultUncaughtExceptionHandler` during `:app` startup that logs the thread name and throwable and marshals any user-facing error surfacing through `Platform.runLater` (see `javafx-ui.md`, `threading-concurrency.md`). Log a JavaFX `Task`/`Service` failure in its `setOnFailed`/`onFailed` handler, not by catching inside the task body. — Rationale: an uncaught exception on a background thread must not vanish silently, and a `Task` failure has one canonical place to observe it.
- **MUST NOT** log inside `layoutChildren()`, a `TableCell`/`TreeCell`-style `updateItem()` override, or a property listener on a frequently-changing value (window resize, scroll position, live progress). — Rationale: these run on every layout pass / every change and will flood the log file.

## SHOULD

- **SHOULD** log a given failure **once**, at the boundary that first builds the `AppError`, including the `cause`; downstream layers branch on `Result` without re-logging. — Rationale: one clean line per failure; see `error-envelope.md`.
- **SHOULD** choose levels deliberately: `error` for terminal `AppError`s, `warn` for retried/`FLAGGED`/degraded, `info` for lifecycle milestones, `debug` for chunk-level detail. — Rationale: usable logs at the default level.
- **SHOULD** use MDC keys (`jobId`, `projectId`, `chunk`) for correlation instead of interpolating them into every message. — Rationale: structured, filterable context.
- **SHOULD** guard an expensive log-argument computation with the level check even under parameterized logging — the placeholder alone doesn't save you if constructing the *argument* is expensive: `if (log.isDebugEnabled()) { log.debug("...", expensiveToString()); }`. — Rationale: parameterization avoids the format cost, not the argument-construction cost.
- **SHOULD** reserve `ERROR` for unexpected failures; an expected business outcome (user input validation, a user cancellation) is `WARN` or `INFO`, not `ERROR`. — Rationale: keeps `ERROR` a reliable signal for "something actually went wrong."
- **SHOULD NOT** log at `INFO` or above inside a tight loop — accumulate and emit one summary line after (`log.info("processed {} segments", count)`), not one line per iteration. — Rationale: per-iteration logging at chatty levels drowns the log and costs real I/O.
- **SHOULD** suppress third-party framework noise at `WARN` in the Logback config (`javafx`, `com.sun`, `jdk` logger names) — a concrete note for whoever writes the programmatic config in `LoggingBootstrap`. — Rationale: framework internals log more than a desktop app's log file needs to carry.

## Reject if

- A logger is created without the `private static final Logger log = LoggerFactory.getLogger(...)` idiom, or a Logback type appears in application code outside `LoggingBootstrap`.
- A non-bootstrap-path class hand-writes the `LoggerFactory.getLogger(...)` field boilerplate instead of `@Slf4j` (`java-coding-style.md`).
- A log statement concatenates strings instead of using `{}` placeholders.
- MDC is set without a `finally` clear.
- Any secret/token/header/body/file-content appears in a log message.
- `System.out`/`System.err`/`printStackTrace` is used for logging.
- A bootstrap-path class (`Launcher`, lock acquisition, `util.paths`) holds a static `Logger` or logs before the log dir is published (ArchUnit `bootstrap-no-static-logger`).
- The same failure is logged at multiple layers.
- A `Throwable` is interpolated into a `{}` placeholder instead of passed as the last argument.
- A concrete Logback type is imported outside `LoggingBootstrap`.
- A log call fires inside a JavaFX layout callback, cell `updateItem()`, or a high-frequency property listener.
- `ERROR` is used for an expected/validation outcome.
- A tight loop logs per-iteration at `INFO` or above instead of a single summary line.
