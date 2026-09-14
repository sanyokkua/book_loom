## Why

BookLoom opens, masks and writes back books in four formats, but it has never translated one: `:llm` and `:pipeline`
hold only empty Guice modules. This change runs one real book end to end, with a deterministic pseudo model standing
in for the LLM. It also fixes the contracts the real LLM clients and the translation screen will build on, including
pausing a running job, and makes every run explainable from its log. The spec asks for both an automatic end-to-end
run and explicit pause/resume (`FR-ALGO-01`, `FR-RESUME-03`, `01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`,
`#fr-resume`).

## What Changes

- **Chat model contract.** The engine sends messages to a chat model and gets text plus a finish reason back. It never
  names a provider or a model. A factory creates the chat model from a provider id and a model id, the shape provider
  settings will fill later. A per-call setting, such as a temperature the user changes at a pause, will be an optional
  request field, so the contract does not change. The idea comes from go_text's provider and factory, not its exact
  contract.
- **Pseudo model.** A built-in provider `pseudo` replies with the last user message in capitals and leaves `⟦gN⟧`
  tokens and character references unchanged. It is deterministic and offline.
- **Translation job.** The job translates every pending segment in document order:
  - it accepts a translation whose markup restores;
  - it flags a segment the model could not translate, an empty reply included, and continues;
  - it stops on any other error.

  It sends progress events and returns a report, also when it stops early.
- **Pause, resume, cancel.** A job pauses on request, or at the pause points the caller enables: after each segment,
  after each section, between stages, and on error. On error, resume retries the failed step. With no pause points the
  job runs fully automatically.
- **Checked export.** The book is written in its own format with the target language. The written file is re-opened
  and its segment count compared with the source before it replaces the destination, and a translation is never
  written over its own source.
- **Releasing an opened book** (`DocumentPort.close`), so a finished job does not keep its book in memory.
- **Command line.** `./gradlew :app:translate --args="<book> [--to <lang>] [--from <lang>] [--overwrite]"` translates
  one book with the pseudo model and writes `<name>.<to>.<ext>` beside it.
- **Diagnostic log.** Every run logs its lifecycle at INFO, every check and decision at DEBUG, and prompts, replies and
  restored text at TRACE. `BOOKLOOM_LOG_LEVEL` picks the level for the desktop app and the command line, and
  credentials are never logged.

## Capabilities

### New Capabilities

- `translation-pipeline`: translating an opened book segment by segment through a chat model. Covers the prompt, the
  accept/flag/stop decisions, progress events, the job report, the one-book command line that starts a job, and the
  diagnostic log of a run with its level switch.
- `resume`: pausing, resuming and cancelling a running job, on request or at enabled pause points, without losing or
  repeating decided segments. It works within one run; nothing survives a restart yet.
- `inference`: how a caller gets a chat model by provider id and model id, and what the pseudo model replies.
- `export`: writing the translated book in its source format, and replacing the destination only after the written
  file re-opens with the source's segment count.

### Modified Capabilities

- `document-round-trip`: adds releasing an opened book.

## Impact

- **Modules:**
  - `:api`: new packages `ua.bookloom.api.llm` and `ua.bookloom.api.pipeline`; `DocumentPort.close`; file suffixes
    on `BookFormat`; copy-with methods on `Segment`, `Unit` and `Document`.
  - `:document`: closing an opened book; DEBUG and TRACE logging at `DocumentService`.
  - `:llm`: the model factory, and the pseudo model in the non-exported package `ua.bookloom.llm.pseudo`.
  - `:pipeline`: the engine, the job and the export.
  - `:app`: the CLI launcher and command, the Guice modules shared with the desktop app, the log-level switch in
    `LoggingBootstrap`, and the `translate` task.
- **Dependencies:** no new third-party library. `:llm` and `:pipeline` add `slf4j-api` (MIT); `:document`, `:llm` and
  `:pipeline` add `logback-classic` (EPL-1.0, a recorded exception) to their test runtime only. Both are already in
  the version catalog, and the three lockfiles are regenerated.
- **Decision record:** ADR-0033 (`docs/adr/ADR-0033-bound-chat-model-and-pausable-translation-job.md`) records the
  decisions that are costly to reverse: the engine gets a chat model already bound to a provider and model, with
  per-call settings as optional request fields, and a translation runs as a job that halts in place when paused.
- **Unchanged:** the desktop window and persistence. No network connection is added.
- **Docs:**
  - the spec clauses this change outgrows: `02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`,
    `02_Architecture/02_MODULES_AND_LAYERING.md#module-api`, `#module-llm`, `#module-pipeline`,
    `02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#chat-contracts`,
    `02_Architecture/09_ERROR_HANDLING.md#partial-results`, `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`
    and `02_Architecture/10_DI_AND_LIFECYCLE.md#guice-modules`;
  - `docs/Architecture.md`, `docs/DEVELOPMENT.md`, `AGENTS.md`, `docs/implementation_plan/CHANGE_BACKLOG.md` and
    `docs/implementation_plan/01_MODULE_INVENTORY.md`.
