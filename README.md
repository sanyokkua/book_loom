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
- **Cross-platform:** Java 25 + JavaFX 26; unsigned native packages for macOS (`.app`/`.dmg`), Windows (portable zip —
  no installer), and Linux (tar.gz/`.deb`).
- **MIT licensed.**

## Repository layout

```
AGENTS.md                     Operating manual — the single source of truth (read first)
CLAUDE.md                     Pointer to AGENTS.md; holds no rules of its own
.claude/                      Claude Code config: rules, skills, agents, slash commands
docs/
  DEVELOPMENT.md              Human developer guide: prerequisites, build, run, debug, IDE, troubleshooting
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
modules/                      ALL the code lives here (ADR-0021) — the root stays prose + build config
  api/                        Contracts: interfaces, records/DTOs, Result/AppError — the dependency floor
  util/                       Per-OS paths, shared helpers
  document/                   EPUB/FB2/Markdown/TXT parsing, masking, reassembly
  llm/                        Provider port + Ollama-native and OpenAI-compatible clients
  pipeline/                   Translation engine: chunking, QA, judge, repair
  persistence/                SQLite + Flyway + JDBI; repository port implementations
  ui/                         JavaFX views, controllers, theming (only ui/ and app/ see JavaFX)
  app/                        Launcher, Application, Guice composition root, the arch-test suite
  build-logic/                Gradle convention plugins (an included build, with its own test suite)
                              Gradle project names are UNCHANGED by the move: still :api … :app, still
                              ./gradlew :app:run. Only `-p modules/build-logic` gained a prefix.
scripts/fr-coverage.sh        Advisory grep: frozen FR-* ids no shipped requirement claims yet
lefthook.yml                  Git hook stages (see "Git hooks" below)
.lefthook/pre-push/           The pre-push gate script
tooling/
  hooks/                      Helper scripts the hooks call: file-size guard, commit-message check
  hooks-test/                 Scripted checks that the hooks do what they claim (run by hand, not by Gradle)
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

## Local setup

**JDK 25 is required and you must install it yourself.** There is no toolchain auto-provisioning: the build declares
no toolchain download repository, so a machine without a Java 25 installation fails at configuration time with
`Cannot find a Java installation on your machine ... Toolchain download repositories have not been configured.`
Gradle itself arrives through the committed wrapper — always invoke `./gradlew`, never a system `gradle`.

```bash
./gradlew build                        # compile + spotlessCheck + lint + test
./gradlew clean build check spotlessCheck   # the full gate — what pre-push and CI both run
./gradlew :app:run                     # launch the app (1024x700 window)
```

**New here? Read [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).** It covers prerequisites, the module map, running and
debugging the app, the IDE run configuration, the test tiers, packaging, and the traps that cost the most time.

### Git hooks

Hooks are managed by [Lefthook](https://lefthook.dev) and are **not active until you install them once per
clone**. They also need `gitleaks` on the `PATH` for the secret scan:

```bash
brew install lefthook gitleaks   # macOS; see the tool docs for Linux/Windows
lefthook install                 # writes .git/hooks — run once after cloning
lefthook validate                # optional: check lefthook.yml parses
```

What each stage does, and why (`docs/specification/04_Build_and_Release/02_QUALITY_GATES.md#lefthook-stages`):

| Stage | Runs | Budget |
|---|---|---|
| `pre-commit` | Spotless on staged Java (auto-fixes and re-stages), gitleaks on the staged diff, a 4 MB file-size guard | < 10 s, no tests |
| `commit-msg` | Conventional Commits validation | instant |
| `pre-push` | `./gradlew clean build check spotlessCheck` | slow — the full gate |

Pre-push runs **exactly** the command the CI quality job runs, deliberately — no faster hook-only subset — so a
green push implies a green CI quality job for the same tree. CI adds gates (license report, OWASP SCA); it never
runs a weaker variant of a shared one.

Every stage can be bypassed, and doing so is a legitimate decision rather than a trick to rediscover:

```bash
git commit --no-verify   # skip pre-commit + commit-msg
git push --no-verify     # skip pre-push; CI becomes the gate instead
LEFTHOOK=0 git <cmd>     # skip every lefthook hook for one command
```

## Status

**Specification finalized (v1.0, 2026-07-18); delivery migrated to OpenSpec (2026-08-02).** The specification and
implementation-plan documents are `Status: Final`; the 50-entry decision log, 24 accepted ADRs, the binding mockup, and
the AI-agent configuration are reconciled and cross-verified.

**Stage A is complete and the EPUB round-trip has shipped.** Roughly 4,300 lines of production Java across 75 files
live under `modules/`, and `./gradlew :app:run` opens a real window. Four OpenSpec changes are archived:
`bootstrap-gradle-and-quality-toolchain`, `restructure-module-layout`, `bootstrap-app-launch-and-empty-window`, and
`add-document-skeleton-and-epub-roundtrip`. `:api`, `:util`, `:document` (EPUB only) and `:app` carry real code;
`:llm`, `:pipeline` and `:persistence` are Guice-module stubs, and `:ui` is an app-shell placeholder plus `theme.css`.
Delivery runs as 28 OpenSpec changes across five stages — infrastructure → document round-trip core ∥ UI component
library → engine → composition → release (ADR-0017).
