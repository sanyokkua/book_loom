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
CLAUDE.md                     Operating manual (read first)
.claude/                      Claude Code config: rules, skills, agents, slash commands
docs/
  specification/              FROZEN spec: requirements, architecture, decisions, mockup, diagrams
    INDEX.md                    Start here for the spec map
    00_Foundation/ … 05_Dependencies/
    mockups/ui-mockup.html      Binding UI source of truth
    diagrams/                   Pipeline diagrams (mermaid)
    assets/icon/                App icon: source + background-removal/derivation pipeline + per-OS .icns/.ico/.png
  implementation_plan/        How to work with the spec: module inventory, story/ADR/traceability
                              formats, definition of done, roadmap, and the phase backlogs
  stories/                    Implementation stories (one per session) — created during the build
  adr/                        Architecture Decision Records (ADR-0001 … )
  traceability.yaml           Generated spec→story→AC→test→module record
```

## How the build is driven

The specification is frozen; work happens as **stories**, one per session, planned before any code is written:

1. Pick a phase from `docs/implementation_plan/07_ROADMAP.md`.
2. `/plan-phase-stories-creation <PHASE>` → review → approve; the `architect` writes the story files.
3. For each story: `/plan-user-story-implementation <STORY>` → review → approve; `coder` + `tester` implement, land
   proving tests, and run `./gradlew trace && ./gradlew traceCheck`.
4. A story is `done` only when the Definition of Done (`docs/implementation_plan/06_DEFINITION_OF_DONE.md`) is
   satisfied.

See `CLAUDE.md` for the module map, invariants, and command list.

## Status

**Specification finalized (v1.0, 2026-07-18).** All 66 specification and implementation-plan documents are
`Status: Final`; the 50-entry decision log, 15 accepted ADRs, the binding mockup, and the AI-agent configuration are
reconciled and cross-verified. Source code is added phase by phase per the roadmap, starting with
`/plan-phase-stories-creation PHASE_00_SCAFFOLD`.
