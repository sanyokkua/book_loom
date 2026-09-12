---
name: project-navigator
description: >-
  Use when orienting in the BookLoom repo (`tranlator_app`) at the start of a
  task — finding which module owns a concern, where the specification, OpenSpec changes,
  ADRs, and reference phase plans live, the build/test commands, and the read order for a
  new task. Covers the module map, the docs layout, and `./gradlew` entry points.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Project Navigator

BookLoom is a local-first, offline desktop app (Java 25 + JavaFX 25, Gradle
Kotlin DSL, Guice DI, multi-module JPMS) that translates whole books (EPUB/FB2/MD/TXT) with
a locally-run LLM. Use this skill to find the right place fast and load the right context.

## When to use

- Starting any task and needing to know which module/doc owns the concern.
- Locating a spec clause, an OpenSpec change, an ADR, or a reference phase file.
- Recalling the build/test commands or the invariants that gate every change.

## When NOT to use

- Do NOT use this to author a change — `/opsx:propose` and the
  `openspec-change-authoring` skill own that.
- Do NOT rewrite `docs/specification/` wholesale — fix the clause the code outgrew, in the same change.

## Module map (dependencies point inward; only `:ui`/`:app` require javafx.*)

| Module | Owns | Package |
|---|---|---|
| `:api` | ports/interfaces, records/DTOs, enums, `Result<T>`, `AppError`, `ErrorCode` — the dependency floor, framework-free, FX-free | `ua.bookloom.api` |
| `:util` | text/io/lang/hash helpers | `ua.bookloom.util` |
| `:document` | EPUB/FB2/MD/TXT parse -> skeleton+segments, masking, reassembly, round-trip; impl `DocumentPort` | `ua.bookloom.document` |
| `:llm` | `Provider`+`ProviderProfile`+`ProviderFactory`, discovery, inference, `InferenceGate`, retry, HTTP->`AppError`, verification | `ua.bookloom.llm` |
| `:pipeline` | chunking, context assembly, translate->QA->judge->self-heal loop, name/term dict (seeded by the DD-46 glossary LLM pre-scan; deterministic fallback with `gender = unknown`), TM, rolling summary, backward revision; impl `TranslationEngine` | `ua.bookloom.pipeline` |
| `:persistence` | SQLite (Flyway, JDBI, WAL + per-connection pragmas, single-writer), settings KV, project/segment/glossary/TM stores, secret refs | `ua.bookloom.persistence` |
| `:ui` | JavaFX FXML views + controllers, viewmodels, state mirror, theming, i18n | `ua.bookloom.ui` |
| `:app` | `Launcher` (paths-first bootstrap + single-instance `bookloom.lock`, acquired pre-injector with `:util`; second launch = "already running" dialog + exit), `Application`, Guice composition root, two-phase init | `ua.bookloom.app` |

Allowed edges: `:app -> everything`; `:ui -> :pipeline :api :util (+javafx)`;
`:pipeline -> :document :llm :persistence :api :util`; `:document`/`:llm`/`:persistence ->
:api :util`; `:util -> :api`; `:api -> nothing internal`. Cross-module calls target `:api`
ports, never concrete `..Impl`/`..Dao`/`..Service` in another module.

## Docs layout

| Location | Contents |
|---|---|
| `docs/specification/` | The spec (editable): `00_Foundation` (vision, glossary, personas, `04_DESIGN_DECISIONS.md` DD-01..DD-49, `05_SPEC_INDEX.md`, `06_IMPLEMENTATION_STAGES.md`), `01_Product` (incl. `12_PROMPT_CATALOG.md` — every model prompt, and `10_I18N_AND_ACCESSIBILITY.md`), `02_Architecture` (incl. `11_APP_ENVIRONMENT_AND_PATHS.md` — per-OS dirs, dev-vs-prod, startup order, `bookloom.lock`), `03_NonFunctional`, `04_Build_and_Release` (incl. `05_ICON_AND_BRANDING.md` and `06_TESTING_STRATEGY.md` — the full test taxonomy + CI-vs-local split), `05_Dependencies` |
| `docs/specification/mockups/ui-mockup.html` | Binding UI visual source of truth (P6) |
| `docs/specification/diagrams/` | Canonical `pipeline.mermaid`, `chunk-translate-loop.mermaid` |
| `docs/implementation_plan/` | `CHANGE_BACKLOG.md` (the ordered backlog), `01_MODULE_INVENTORY.md` (as-built log), `04_ADR_FORMAT.md`, `07_ROADMAP.md` (five stages), `notes-corpus-verification.md` (corpus evidence) |
| `docs/Architecture.md`, `docs/DEVELOPMENT.md` | What is built and how to verify it; how to build, run, test, package |
| `openspec/changes/<name>/` | THE UNIT OF WORK: `proposal.md`, `design.md`, `specs/<capability>/spec.md`, `tasks.md` |
| `openspec/specs/<capability>/` | The ledger of what is actually BUILT; starts empty, grown by `openspec archive` |
| `openspec/config.yaml` | Project context + the R1-R4 rules that steer artifact generation |
| `docs/adr/` | `ADR-NNNN-<slug>.md` (currently ADR-0001..ADR-0032; next free number 0033). ADR-0016 governs delivery tracking, ADR-0017 the delivery order — read both before planning work |

## Commands

- Build/format: `./gradlew build`, `./gradlew spotlessApply`, `./gradlew spotlessCheck`.
- Test: `./gradlew test` (full, incl. headless TestFX on JavaFX 26's built-in platform; the `liveLocal`/`promptEval`/`visual` tagged sets are local-only, excluded from CI/`check`).
- OpenSpec: `openspec list`, `openspec validate <change> --strict`, `openspec status --change <name>`, `openspec show <item>`.

None of the `./gradlew` tasks exist yet — they arrive with change 1,
`bootstrap-gradle-and-quality-toolchain`.

## Read order for a new task

1. `AGENTS.md` — invariants, how work is tracked, the `/opsx:*` loop. (`CLAUDE.md` just imports it.)
2. `docs/specification/00_Foundation/05_SPEC_INDEX.md` — resolve clause citations.
3. The change under `openspec/changes/` (or propose one) — proposal, requirements, tasks.
4. The cited spec clauses + `DD-NN` (`04_DESIGN_DECISIONS.md`) + any `docs/adr/ADR-*`.
5. `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md` — the owning module +
   ArchUnit rules.
6. The applicable `.claude/skills/*` and `.claude/rules/*`.

## Golden invariants (always true)

FX-free core (only `:ui`/`:app` touch JavaFX) · skeleton never semantically regenerated —
translate text nodes only; round-trip is structure-and-text-preserving canonical-equal, not
canonical-equal round trip (DD-43) · offline (the only outbound calls are user-triggered provider
communication — inference, model discovery, verification — to the configured provider) ·
credentials-as-reference (never store the secret) · single-flight `InferenceGate` ·
records-first · token-only theming · `Result`/`AppError` envelope everywhere · every
requirement has a covering test whose name and comment say what it proves ·
the whole-project clean gate (`./gradlew clean build check spotlessCheck` green, no
pre-existing-failure exemption).

## Reference index

- In-repo authorities: `AGENTS.md` (the single operating manual; `CLAUDE.md` imports it),
  `docs/specification/00_Foundation/05_SPEC_INDEX.md`,
  `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
  `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`.

## Mandatory validation checklist

- [ ] The concern is mapped to the correct owning module before editing.
- [ ] Clause citations resolved through `05_SPEC_INDEX.md`.
- [ ] The relevant OpenSpec change + `DD-NN`/ADR + skills/rules loaded before work.
- [ ] The golden invariants are not violated by the intended change.
- [ ] Build/test commands known before starting.

## Gotchas

- `docs/specification/` is editable — when the code legitimately differs, fix the clause in the same change.
- A cross-module call to a concrete impl (not the `:api` port) fails ArchUnit
  `ports-not-concretes`.
- New modules require updating the module inventory and the layering doc's ArchUnit setup.
- `openspec/specs/**` is written by `openspec archive` — never hand-edit it.
- The order of work is `07_ROADMAP.md` (five stages) + `CHANGE_BACKLOG.md` (ADR-0017).
- Story files, `docs/traceability.yaml`, the trace Gradle tasks, and `Proves:` markers are
  retired by ADR-0016 — never reintroduce them.
