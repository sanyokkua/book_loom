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
AGENTS.md                     Operating manual for agents (read first)
docs/Architecture.md          What is built, what is not, and how to verify each claim
CLAUDE.md                     Pointer to AGENTS.md; holds no rules of its own
.claude/                      Claude Code config: rules, skills, agents, slash commands
docs/
  DEVELOPMENT.md              Human developer guide: prerequisites, build, run, debug, IDE, troubleshooting
  specification/              The spec: requirements, architecture, decisions, mockup, diagrams (editable)
    INDEX.md                    Start here for the spec map
    00_Foundation/ … 05_Dependencies/
    mockups/ui-mockup.html      Binding UI source of truth
    diagrams/                   Pipeline diagrams (mermaid)
    assets/icon/                App icon: source + background-removal/derivation pipeline + per-OS .icns/.ico/.png
  implementation_plan/        CHANGE_BACKLOG.md (order of work), 01_MODULE_INVENTORY.md (as-built log),
                              07_ROADMAP.md, notes-corpus-verification.md, 04_ADR_FORMAT.md
  adr/                        Architecture Decision Records (ADR-0001 … )
openspec/
  config.yaml                 Project context + the authoring rules that steer generated artifacts
  changes/<name>/             THE UNIT OF WORK: proposal.md, design.md, specs/, tasks.md
  specs/<capability>/         The ledger of what is actually BUILT — starts empty, grows on archive
modules/                      ALL the code lives here (ADR-0021) — the root stays prose + build config
  api/                        Contracts: interfaces, records/DTOs, Result/AppError — the dependency floor
  util/                       Per-OS paths, shared helpers
  document/                   EPUB/FB2/Markdown/TXT parsing, masking, reassembly
  llm/                        (planned) provider port + Ollama-native and OpenAI-compatible clients — empty today
  pipeline/                   (planned) translation engine: chunking, QA, judge, repair — empty today
  persistence/                (planned) SQLite + Flyway + JDBI — empty today
  ui/                         JavaFX theming and an empty app shell (only ui/ and app/ see JavaFX)
  app/                        Launcher, Application, Guice composition root, the arch-test suite
  build-logic/                Gradle convention plugins (an included build)
                              Gradle project names are UNCHANGED by the move: still :api … :app, still
                              ./gradlew :app:run. Only `-p modules/build-logic` gained a prefix.
lefthook.yml                  Git hook stages (see "Git hooks" below)
.lefthook/pre-push/           The pre-push gate script
tooling/hooks/                Helper scripts the hooks call: file-size guard, commit-message check
```

## How work is planned

`AGENTS.md` is the operating manual. In short: a change is one short brief (an OpenSpec proposal under `openspec/`
when the owner wants one), an acceptance test seen red before the implementation, a green gate, and the change
exercised in the running app. The specification is a reference that is edited when the code legitimately differs from
it; `docs/adr/` records the decisions that are costly to reverse.

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
green push implies a green CI quality job for the same tree. CI adds one gate (the license report); it never
runs a weaker variant of a shared one.

Every stage can be bypassed, and doing so is a legitimate decision rather than a trick to rediscover:

```bash
git commit --no-verify   # skip pre-commit + commit-msg
git push --no-verify     # skip pre-push; CI becomes the gate instead
LEFTHOOK=0 git <cmd>     # skip every lefthook hook for one command
```

## Status

**Document engine shipped; translation not yet.** `:api`, `:util`, `:document` and `:app` carry real code: an EPUB,
FB2, Markdown or TXT book is parsed into a skeleton plus segments, every segment is masked to `⟦gN⟧` placeholders,
and a translated segment is restored behind a placeholder-multiset hard gate — verified canonical-equal on a 216-book
local corpus. `./gradlew :app:run` opens a themed, empty window. `:llm`, `:pipeline` and `:persistence` are Guice
stubs and `:ui` is a placeholder; no book has been translated end to end yet. The next unit of work is the walking
skeleton: one EPUB through one local model to a translated EPUB, from the UI.
