---
id: STORY-006
title: Configure programmatic Logback logging into the resolved log directory
status: ready
spec_clauses:
  - docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md#logging-bootstrap-order
  - docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#startup-order
  - docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#secrets-never-stored
  - DD-23
  - DD-39
modules:
  - :app/ua.bookloom.app
  - :util/ua.bookloom.util.io
acceptance_criteria:
  - STORY-006-AC-1
  - STORY-006-AC-2
  - STORY-006-AC-3
  - STORY-006-AC-4
edge_cases: [ ]
depends_on:
  - STORY-001
  - STORY-003
adrs: [ ]
phase: PHASE_00_SCAFFOLD
owner: coder
estimate: M
---

# STORY-006 — Configure programmatic Logback logging into the resolved log directory

## Goal

Stand up diagnostics correctly ordered: SLF4J + Logback configured programmatically (no static XML file appender) so the
log directory resolved by STORY-003 is applied before the first logger is created, with a time+size rolling file policy,
parameterized logging, MDC cleared in `finally`, and a hard rule that secrets never reach a log line.

## In scope

- Programmatic Logback setup consuming `AppPaths.logDir()`; rolling file appender (time+size policy, bounded
  history/total-size caps).
- The bootstrap discipline: no class on the pre-configuration path declares a static `Logger` (enforced by the STORY-005
  rule); loggers obtained after configuration.
- MDC conventions and a redaction helper for anything secret-adjacent.

## Out of scope

- Log-level settings UI (PHASE_11); per-run task logs (later phases).

## Spec inputs

- `10_DI_AND_LIFECYCLE.md#logging-bootstrap-order` — programmatic config; log dir published before first `getLogger`.
- `11_APP_ENVIRONMENT_AND_PATHS.md#startup-order` — logging is step 5, after paths/lock.
- `03_PRIVACY_AND_OFFLINE.md#secrets-never-stored` — never log secrets or book text.
- DD-23, DD-39.

## Design constraints

- Dev builds write under the `-Dev` log dir; prod under the real one (DD-39) — the choice comes entirely from
  `AppPaths`.
- Rolling policy bounded (per-file and total caps) so logs cannot grow unbounded.
- Parameterized logging only (`log.info("… {}", v)`); MDC cleared in `finally`.

## Acceptance criteria

### STORY-006-AC-1

Given a resolved log dir, when logging initializes and a line is written, then the rolling file exists under exactly
that directory (and under the `-Dev` sibling when `isDev` is true). (P1)

### STORY-006-AC-2

Logback is configured programmatically: no `logback.xml` file appender exists on the classpath, and the file appender's
location is decided at runtime from `AppPaths`. (P3)

### STORY-006-AC-3

Given a value registered as secret (credential reference resolution result), when it is passed through the redaction
helper into a log call, then the written line contains the redaction marker and never the raw value. (P5)

### STORY-006-AC-4

The rolling policy enforces its per-file size and total-size caps: writing beyond the per-file cap rolls to a new
indexed file and old files are pruned at the configured history bound. (P1)

## Test plan

- STORY-006-AC-1 → integration · app/src/test/java/ua/bookloom/app/logging/LoggingBootstrapTest.java ·
  writesUnderResolvedDirIncludingDev · `Proves: STORY-006-AC-1`
- STORY-006-AC-2 → unit · app/src/test/java/ua/bookloom/app/logging/ProgrammaticConfigTest.java ·
  noStaticXmlFileAppender · `Proves: STORY-006-AC-2`
- STORY-006-AC-3 → unit · app/src/test/java/ua/bookloom/app/logging/RedactionTest.java · secretNeverWrittenRaw ·
  `Proves: STORY-006-AC-3`
- STORY-006-AC-4 → integration · app/src/test/java/ua/bookloom/app/logging/RollingPolicyTest.java ·
  rollsAndPrunesAtCaps · `Proves: STORY-006-AC-4`

## Definition of done

Per `docs/implementation_plan/06_DEFINITION_OF_DONE.md`, including the implicit whole-project clean gate.
