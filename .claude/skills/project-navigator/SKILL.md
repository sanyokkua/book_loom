---
name: project-navigator
description: >-
  Use when orienting in the BookLoom repo (`tranlator_app`) at the start of a
  task — finding which module owns a concern, where the specification, stories, ADRs,
  traceability, and phase plans live, the build/test/trace commands, and the read order for
  a new task. Covers the module map, the docs layout, and `./gradlew` entry points.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# Project Navigator

BookLoom is a local-first, offline desktop app (Java 25 + JavaFX 25, Gradle
Kotlin DSL, Guice DI, multi-module JPMS) that translates whole books (EPUB/FB2/MD/TXT) with
a locally-run LLM. Use this skill to find the right place fast and load the right context.

## When to use

- Starting any task and needing to know which module/doc owns the concern.
- Locating a spec clause, story, ADR, phase file, or the traceability record.
- Recalling the build/test/trace commands or the invariants that gate every change.

## When NOT to use

- Do NOT use this to plan a phase (`/plan-phase-stories-creation`) or a story
  (`/plan-user-story-implementation`) — those commands own planning.
- Do NOT edit `docs/specification/` — it is frozen.

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
| `docs/specification/` | FROZEN spec: `00_Foundation` (vision, glossary, personas, `04_DESIGN_DECISIONS.md` DD-01..DD-49, `05_SPEC_INDEX.md`, `06_IMPLEMENTATION_STAGES.md`), `01_Product` (incl. `12_PROMPT_CATALOG.md` — every model prompt, and `10_I18N_AND_ACCESSIBILITY.md`), `02_Architecture` (incl. `11_APP_ENVIRONMENT_AND_PATHS.md` — per-OS dirs, dev-vs-prod, startup order, `bookloom.lock`), `03_NonFunctional`, `04_Build_and_Release` (incl. `05_ICON_AND_BRANDING.md` and `06_TESTING_STRATEGY.md` — the full test taxonomy + CI-vs-local split), `05_Dependencies` |
| `docs/specification/mockups/ui-mockup.html` | Binding UI visual source of truth (P6) |
| `docs/specification/diagrams/` | Canonical `pipeline.mermaid`, `chunk-translate-loop.mermaid` |
| `docs/implementation_plan/` | Process formats (story/AC/traceability/DoD/module-inventory/ADR formats), `07_ROADMAP.md`, `phases/PHASE_NN_*.md` |
| `docs/stories/` | `story-NNN-<slug>.md` + `README.md` index |
| `docs/adr/` | `ADR-NNNN-<slug>.md` (currently ADR-0001..ADR-0015; next free number 0016) |
| `docs/traceability.yaml` | GENERATED chain: clause -> story -> AC -> test -> module |

## Commands

- Build/format: `./gradlew build`, `./gradlew spotlessApply`, `./gradlew spotlessCheck`.
- Test: `./gradlew test` (full, incl. headless TestFX/Monocle; the `liveLocal`/`promptEval`/`visual` tagged sets are local-only, excluded from CI/`check`).
- Traceability: `./gradlew trace` (regenerate `docs/traceability.yaml`),
  `./gradlew traceCheck` (validate; zero orphans; fresh record).

## Read order for a new task

1. `CLAUDE.md` — invariants, how work is tracked, the two slash commands.
2. `docs/specification/00_Foundation/05_SPEC_INDEX.md` — resolve clause citations.
3. The story under `docs/stories/` (or plan one) — goal, ACs, spec inputs, constraints.
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
records-first · token-only theming · `Result`/`AppError` envelope everywhere · every AC has
a `Proves:` test and `traceCheck` passes with zero orphans · the whole-project clean gate
(`./gradlew clean build check spotlessCheck` green, no pre-existing-failure exemption).

## Reference index

- In-repo authorities: `CLAUDE.md`,
  `docs/specification/00_Foundation/05_SPEC_INDEX.md`,
  `docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md`,
  `docs/specification/00_Foundation/06_IMPLEMENTATION_STAGES.md`.

## Mandatory validation checklist

- [ ] The concern is mapped to the correct owning module before editing.
- [ ] Clause citations resolved through `05_SPEC_INDEX.md`.
- [ ] The relevant story + `DD-NN`/ADR + skills/rules loaded before work.
- [ ] The golden invariants are not violated by the intended change.
- [ ] Build/test/trace commands known before starting.

## Gotchas

- `docs/specification/` is FROZEN — never edit it to fit code; raise a gap instead.
- A cross-module call to a concrete impl (not the `:api` port) fails ArchUnit
  `ports-not-concretes`.
- New modules require updating the module inventory and the layering doc's ArchUnit setup.
- The traceability.yaml is generated — never hand-edit it.
