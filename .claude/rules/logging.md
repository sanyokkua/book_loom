# Logging

Scope: all modules (`**/src/main/java/**`). Stack: SLF4J 2 + Logback 1.5. Spec: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`. See also `error-envelope.md`, `offline-and-privacy.md`.

## MUST

- **MUST** obtain a logger as `private static final Logger log = LoggerFactory.getLogger(X.class)` — SLF4J API only, never a concrete Logback type in code. — Rationale: one idiomatic, backend-agnostic pattern.
- **MUST** use **parameterized logging** (`log.info("job {} chunk {}", jobId, idx)`), never string concatenation in log calls. — Rationale: no formatting cost when the level is disabled; no accidental `toString` of large objects.
- **MUST** clear MDC in a `finally` block whenever it is set (e.g. per-job `jobId`), so context never leaks across threads/tasks. — Rationale: pooled/virtual threads reuse carriers; stale MDC misattributes logs.
- **MUST NOT** log secrets, tokens, `Authorization` headers, resolved credential values, full request/response bodies, or file contents at any level. — Rationale: privacy invariant; the app runs offline and must not persist secrets anywhere.
- **MUST** write to a **rolling file appender in the per-OS log directory** (platform-appropriate app-log location) with a human-readable pattern; console output is dev-only. — Rationale: diagnosable logs in the right place, size-bounded.
- **MUST** configure Logback **programmatically** at bootstrap and **publish the resolved log dir before the first `LoggerFactory.getLogger(...)` call** (paths-first startup order, DD-39/ADR-0015). No class on the pre-logging bootstrap path — the `:app` `Launcher`/lock acquisition and the `:util` paths resolver (`ua.bookloom.util.paths`) — may declare a static SLF4J `Logger` or touch `org.slf4j..` at class-init time; enforced by ArchUnit rule 8 `bootstrap-no-static-logger` (`02_MODULES_AND_LAYERING.md`). — Rationale: a static logger that fires before configuration pins Logback to a wrong/default path.
- **MUST NOT** use `System.out`/`System.err` (or `printStackTrace`) for application logging. — Rationale: everything goes through SLF4J so levels, format, and destination are controlled.

## SHOULD

- **SHOULD** log a given failure **once**, at the boundary that first builds the `AppError`, including the `cause`; downstream layers branch on `Result` without re-logging. — Rationale: one clean line per failure; see `error-envelope.md`.
- **SHOULD** choose levels deliberately: `error` for terminal `AppError`s, `warn` for retried/`FLAGGED`/degraded, `info` for lifecycle milestones, `debug` for chunk-level detail. — Rationale: usable logs at the default level.
- **SHOULD** use MDC keys (`jobId`, `projectId`, `chunk`) for correlation instead of interpolating them into every message. — Rationale: structured, filterable context.

## Reject if

- A logger is created without the `private static final Logger log = LoggerFactory.getLogger(...)` idiom, or a Logback type appears in application code.
- A log statement concatenates strings instead of using `{}` placeholders.
- MDC is set without a `finally` clear.
- Any secret/token/header/body/file-content appears in a log message.
- `System.out`/`System.err`/`printStackTrace` is used for logging.
- A bootstrap-path class (`Launcher`, lock acquisition, `util.paths`) holds a static `Logger` or logs before the log dir is published (ArchUnit `bootstrap-no-static-logger`).
- The same failure is logged at multiple layers.
