**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `../implementation_plan/README.md`, `mockups/ui-mockup.html`

# Specification Index — BookLoom

This is the map of the **frozen specification**. Everything under `docs/specification/` is read-only during
implementation: it is the binding source of requirements, architecture, and decisions. Implementation happens as stories
under `../stories/` (see `../implementation_plan/README.md`). The UI source of truth is `mockups/ui-mockup.html`.

Requirement identifiers used throughout: `FR-<AREA>-NN` (functional), `NFR-<AREA>-NN` (non-functional), `DD-NN` (design
decision), `ADR-NNNN` (decision record, in `../adr/`), `EC-<AREA>-N` (edge case). Stories cite spec clauses as
`<file>#<anchor>`; the canonical anchor list is `00_Foundation/05_SPEC_INDEX.md`.

## 00 — Foundation

| File                                        | Contents                                                          |
|---------------------------------------------|-------------------------------------------------------------------|
| `00_Foundation/01_VISION_AND_SCOPE.md`      | Vision, value propositions, target users, in-scope / out-of-scope |
| `00_Foundation/02_GLOSSARY.md`              | Canonical terminology                                             |
| `00_Foundation/03_PERSONAS_AND_USECASES.md` | Personas and primary use cases                                    |
| `00_Foundation/04_DESIGN_DECISIONS.md`      | Lightweight decision log `DD-01…DD-50` (heavy ones → `../adr/`)   |
| `00_Foundation/05_SPEC_INDEX.md`            | Canonical citable clause anchors (`<file>#<anchor>`)              |
| `00_Foundation/06_IMPLEMENTATION_STAGES.md` | Staged delivery + forward-compatibility seams `F1…F9`             |

## 01 — Product (functional requirements)

| File                                        | Contents                                                                                           |
|---------------------------------------------|----------------------------------------------------------------------------------------------------|
| `01_Product/01_FUNCTIONAL_REQUIREMENTS.md`  | The gap-free FR catalog (all areas)                                                                |
| `01_Product/02_TRANSLATION_WORKFLOW.md`     | End-to-end user workflow; the three review modes; automatic-first                                  |
| `01_Product/03_DOCUMENT_FORMATS.md`         | EPUB / FB2 / Markdown / TXT requirements + edge cases                                              |
| `01_Product/04_LLM_PROVIDERS_AND_MODELS.md` | Providers, models, discovery, verification                                                         |
| `01_Product/05_TRANSLATION_ALGORITHM.md`    | The translation pipeline (phases A–E)                                                              |
| `01_Product/06_REVIEW_AND_EDITING.md`       | Segment state machine, flagged queue, side-by-side review                                          |
| `01_Product/07_SETTINGS.md`                 | Every setting, default, and validation range                                                       |
| `01_Product/08_UI_SCREENS_AND_STATES.md`    | Every screen / state / dialog / toast (binds to the mockup)                                        |
| `01_Product/09_THEMING.md`                  | Token model, palette, light + dark                                                                 |
| `01_Product/10_I18N_AND_ACCESSIBILITY.md`   | i18n and WCAG 2.1 AA                                                                               |
| `01_Product/11_NOTIFICATIONS_AND_ERRORS.md` | Toasts, banners, error dialogs, empty states                                                       |
| `01_Product/12_PROMPT_CATALOG.md`           | Normative per-phase LLM prompt catalog (system + user templates, variables, params, output shapes) |

## 02 — Architecture

| File                                              | Contents                                                                 |
|---------------------------------------------------|--------------------------------------------------------------------------|
| `02_Architecture/01_SYSTEM_ARCHITECTURE.md`       | Layered overview, module graph, core invariants                          |
| `02_Architecture/02_MODULES_AND_LAYERING.md`      | Modules, JPMS boundaries, ports, ArchUnit rules                          |
| `02_Architecture/03_DOCUMENT_MODEL.md`            | Skeleton + segment model, masking, round-trip                            |
| `02_Architecture/04_LLM_INTEGRATION.md`           | Provider architecture, inference, gate, retry, errors                    |
| `02_Architecture/05_PIPELINE_ENGINE.md`           | The translation engine internals                                         |
| `02_Architecture/06_DATA_MODEL_SQLITE.md`         | SQLite schema, Flyway, JDBI, KV settings                                 |
| `02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`    | JavaFX app structure, FXML, state mirror                                 |
| `02_Architecture/08_THREADING_CONCURRENCY.md`     | FX thread rules, Task/Service, virtual threads, cancellation             |
| `02_Architecture/09_ERROR_HANDLING.md`            | `Result`/`AppError` envelope, `ErrorCode`                                |
| `02_Architecture/10_DI_AND_LIFECYCLE.md`          | Guice, composition root, two-phase init, lifecycle                       |
| `02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md` | Per-OS data/log dir resolution, dev/prod separation, startup order, lock |

## 03 — Non-Functional

`03_NonFunctional/01_QUALITY_ATTRIBUTES.md` · `02_PERFORMANCE.md` · `03_PRIVACY_AND_OFFLINE.md` ·
`04_ACCESSIBILITY.md` · `05_RELIABILITY_AND_RESUME.md`

## 04 — Build & Release

`04_Build_and_Release/01_BUILD_AND_TOOLING.md` · `02_QUALITY_GATES.md` · `03_PACKAGING_JPACKAGE.md` · `04_CI_CD.md` ·
`05_ICON_AND_BRANDING.md` · `06_TESTING_STRATEGY.md`

## 05 — Dependencies

`05_Dependencies/01_DEPENDENCIES.md` · `02_DEPENDENCY_POLICY.md` · `03_LICENSING.md`

## Diagrams & mockups

- `diagrams/pipeline.mermaid` — the end-to-end pipeline.
- `diagrams/chunk-translate-loop.mermaid` — the per-chunk translate + QA + self-heal loop.
- `mockups/ui-mockup.html` — the binding UI source of truth (see `mockups/README.md`).

## Assets

- `assets/icon/` — the app icon: canonical source, the deterministic background-removal + per-OS derivation pipeline,
  and the generated `.icns` / `.ico` / `.png`. See `assets/icon/README.md` and
  `04_Build_and_Release/05_ICON_AND_BRANDING.md` (DD-29, ADR-0011).

## Process (in `../implementation_plan/`)

Module inventory, story format, traceability, ADR format, acceptance-criteria patterns, definition of done, roadmap, and
the phase files live in `../implementation_plan/`. ADRs live in `../adr/`. The generated traceability record is
`../traceability.yaml`.
