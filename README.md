# BookLoom

A **local-first, offline desktop application** that translates whole books — EPUB, FB2, Markdown, TXT — using a
general-purpose LLM you run locally (Ollama or LM Studio) or any OpenAI-compatible endpoint. It keeps the document's
structure, images, fonts and IDs intact — a structure-and-text-preserving (canonical-equal) round trip; exact bytes may
differ on re-serialization — translating only the text, and runs automatically end-to-end: typically about 99% of a book
completes with no human interaction, and only the chunks it isn't sure about are flagged for optional side-by-side
review. Nothing leaves your machine.

- **Formats:** EPUB, FB2 (incl. `.fb2.zip`), Markdown, TXT.
- **Private:** fully offline, no account, no telemetry; the only network traffic is your own requests to your own model
  server (inference, model discovery, verification).
- **Faithful:** structure-preserving round-trip — the translated book opens exactly like the original.
- **Consistent:** a name/term glossary, translation memory, rolling summary, deterministic quality checks and an
  LLM-as-judge keep names, tone and formatting steady across a whole book.
- **Cross-platform:** Java 25 + JavaFX 25; unsigned native packages for macOS (`.app`/`.dmg`), Windows (portable zip —
  no installer), and Linux (tar.gz/`.deb`).
- **MIT licensed.**

## Repository layout

```
AGENTS.md                     Operating manual — the single source of truth (read first)
CLAUDE.md                     Pointer to AGENTS.md; holds no rules of its own
.claude/                      Claude Code config: rules, skills, agents, slash commands
docs/
  specification/              FROZEN spec: requirements, architecture, decisions, mockup, diagrams
    INDEX.md                    Start here for the spec map
    00_Foundation/ … 05_Dependencies/
    mockups/ui-mockup.html      Binding UI source of truth
    diagrams/                   Pipeline diagrams (mermaid)
    assets/icon/                App icon: source + background-removal/derivation pipeline + per-OS .icns/.ico/.png
  implementation_plan/        How to work with the spec: the operating manual, module inventory,
                              ADR + scenario-pattern formats, definition of done, the five-stage
                              roadmap, CHANGE_BACKLOG.md, and the phase files (reference material)
  adr/                        Architecture Decision Records (ADR-0001 … )
openspec/
  config.yaml                 Project context + the authoring rules that steer generated artifacts
  changes/<name>/             THE UNIT OF WORK: proposal.md, design.md, specs/, tasks.md
  specs/<capability>/         The ledger of what is actually BUILT — starts empty, grows on archive
scripts/fr-coverage.sh        Advisory grep: frozen FR-* ids no shipped requirement claims yet
```

## How the build is driven

The specification is frozen; work happens as **OpenSpec changes**, one at a time, planned before any code is written
(ADR-0016):

1. Pick the next entry from `docs/implementation_plan/CHANGE_BACKLOG.md`, respecting its stage
   (`docs/implementation_plan/07_ROADMAP.md`).
2. `/opsx:propose` → generates `proposal.md`, the delta `specs/`, `design.md` where warranted, and `tasks.md`.
3. `openspec validate <change> --strict` → then `/opsx:apply`; `coder` + `tester` implement and land covering tests
   marked `// Covers: FR-*` with a one-line EARS restatement.
4. A change is archivable only when the Definition of Done (`docs/implementation_plan/06_DEFINITION_OF_DONE.md`) is
   satisfied — including `./gradlew clean build check spotlessCheck` green project-wide. Then `/opsx:archive` folds its
   requirements into `openspec/specs/`.

Every artifact is written to be readable without opening another file: requirements in EARS with a plain-words
`Source:` gloss, scenarios with concrete values, tasks as full sentences. See `AGENTS.md` for the module map,
invariants, and command list, and ADR-0016 for the authoring standard.

## Status

**Specification finalized (v1.0, 2026-07-18); delivery migrated to OpenSpec (2026-08-02).** The specification and
implementation-plan documents are `Status: Final`; the 50-entry decision log, 17 accepted ADRs, the binding mockup, and
the AI-agent configuration are reconciled and cross-verified.

**No source code exists yet.** Delivery runs as 28 OpenSpec changes across five stages — infrastructure → document
round-trip core ∥ UI component library → engine → composition → release (ADR-0017). Exactly one change is authored and
ready: `bootstrap-gradle-and-quality-toolchain`, which stands up the Gradle multi-module build, the eight JPMS modules,
and the full mechanical quality gate.
