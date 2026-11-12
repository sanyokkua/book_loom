# Repository map

Where things live. Paths are relative to the repository root.

## Top-level tree

```text
AGENTS.md              operating manual for every agent and contributor (single source of truth)
CLAUDE.md              imports AGENTS.md, holds no rules of its own
README.md              project overview and local setup
LICENSE                MIT
build.gradle.kts       thin root build: Sonar properties, version, licence-report task (no allprojects block)
settings.gradle.kts    the eight subprojects, repointed to modules/<name>; build-logic included build
gradle/                version catalog, Sonar coverage script, dependency verification, wrapper
gradle.lockfile, settings-gradle.lockfile   dependency lock state (also one per module)
lombok.config          Lombok settings
lefthook.yml           git hooks (see ci-and-hooks.md)
.lefthook/pre-push/    the pre-push gate scripts
tooling/hooks/         helpers the hooks call (commit-message check, file-size guard)
.github/workflows/     ci.yml, the only workflow
config/                checkstyle/, spotbugs/, license/ (allowed-licenses.json)
scripts/               build, packaging, eval, fixture and agent-config scripts (see scripts.md)
modules/               all the code (ADR-0021): eight application modules plus build-logic
docs/                  developer documentation (this folder)
openspec/              OpenSpec config, live capability specs, open and archived changes
.claude/               Claude Code config: rules/, agents/, commands/opsx/, skills (symlink)
.agents/skills/        the skills, one copy, read by Codex directly and by Claude through the symlink
.codex/agents/         generated Codex mirrors of the .claude/agents files
.agentsync.json        options for scripts/sync-agent-files.py
.editorconfig, .gitattributes, .gitignore
```

Local, untracked folders you will see in a working copy: `build/`, `.gradle/`, `.idea/`, `.sonar-reports/`,
`.temporary_context/` (see [What is git-ignored](#what-is-git-ignored)).

## Modules

Eight Gradle subprojects, each also a JPMS module `ua.bookloom.<name>`, in `modules/<name>/`. The Gradle project paths
stay `:api` to `:app`. Edges point downward only. Java file counts were computed with `find` on the current tree
(main and test source sets of each module; `:app` also has an `archTest` source set not counted here).

| Module | Holds | Main / test files | Look here first |
|---|---|---|---|
| `:api` | contracts: `Result`, `AppError`, `ErrorCode`, document model, ports, records | 287 / 87 | `modules/api/src/main/java/ua/bookloom/api/` |
| `:util` | paths, hashing, the language catalogue | 32 / 17 | `ua/bookloom/util/` (`paths` is on the pre-logging bootstrap path) |
| `:document` | parse, mask, unmask, reassemble for EPUB, FB2, Markdown, TXT; golden round-trip tests | 137 / 160 | `ua/bookloom/document/`, tests in `.../document/golden/` |
| `:llm` | chat-model contract, Ollama-native and OpenAI-compatible clients, pseudo model, engine detection, model profiles, bundled knowledge CSVs | 204 / 158 | `ua/bookloom/llm/`, recorded engine bodies in `src/test/resources/ua/bookloom/llm/engine/` |
| `:persistence` | in-memory adapters and SQLite (JDBI) adapters behind the `:api` storage ports | 46 / 30 | `.../persistence/memory/`, `.../persistence/sqlite/`, contract tests in `.../persistence/contract/` |
| `:pipeline` | translation engine: prompts, context builder, chunking, checks, repair, Proofread, export, the job | 672 / 881 | `ua/bookloom/pipeline/`, eval code under `.../pipeline/eval/` (test sources) |
| `:ui` | JavaFX views, state mirror, theme, English and Ukrainian bundles | 696 / 675 | `ua/bookloom/ui/`, conformance tests in `src/test/java/ua/bookloom/ui/` (`ScreenConformanceTest`) |
| `:app` | launcher, boot, logging bootstrap, Guice composition root, window, command line, `archTest` suite | 56 / 73 | `ua/bookloom/app/bootstrap/` (`Launcher`, `TranslateLauncher`), `src/archTest/` |
| `build-logic` | Gradle convention plugins (an included build with its own lock state) | n/a | `modules/build-logic/src/main/kotlin/` |

Convention plugins in `build-logic`: `bookloom.java-conventions` (Java 25 toolchain, Error Prone and NullAway,
Checkstyle, SpotBugs and FindSecBugs, dependency locking, `verifyLocks`), `bookloom.javafx-conventions`,
`bookloom.spotless-conventions` (Palantir Java Format), `bookloom.test-conventions` (JUnit 5, WireMock, headless
JavaFX, `fastTest`, the local-only tagged tasks) and `bookloom.coverage-conventions` (JaCoCo, 0.80 branch minimum).
Module dependency graph and the allowed edges: [`AGENTS.md`](../../AGENTS.md#modules) and
[`openspec/config.yaml`](../../openspec/config.yaml).

## Where things live

| What | Where |
|---|---|
| Operating manual, Definition of Done, git protocol | [`AGENTS.md`](../../AGENTS.md) |
| Live behavioural specs (17 capabilities) | `openspec/specs/<capability>/spec.md` |
| Latest archived change | `openspec/changes/archive/2026-11-12-redesign-translation-pipeline/` (proposal, design, tasks, specs, owner checklist) |
| Finished changes | `openspec/changes/archive/<date>-<name>/` |
| Original specification (editable baseline) | [`docs/spec_baseline/specification/`](../spec_baseline/specification/) |
| Architecture decision records | [`docs/spec_baseline/adr/`](../spec_baseline/adr/) (ADR-0001 to ADR-0047) |
| Change backlog, module inventory, roadmap | [`docs/spec_baseline/implementation_plan/`](../spec_baseline/implementation_plan/) |
| Coding and design rules for agents, scoped by path | `.claude/rules/*.md` (14 files) |
| Skills | `.agents/skills/` (14 folders); `.claude/skills` is a symlink to it |
| Sub-agent definitions | `.claude/agents/*.md`; Codex mirrors in `.codex/agents/*.toml` |
| OpenSpec slash commands | `.claude/commands/opsx/` |
| Scripts | `scripts/`, `tooling/hooks/` ([scripts.md](scripts.md)) |
| Static analysis config | `config/checkstyle/`, `config/spotbugs/exclude.xml` |
| Licence allowlist | `config/license/allowed-licenses.json` |
| Dependency versions | `gradle/libs.versions.toml` (exact pins; locks in `gradle.lockfile` files) |
| Fixture book (four formats, generated) | `modules/app/src/test/resources/fixtures/earth-gravity/` |
| Eval cases and prompts | in `:pipeline` resources and test resources; run with `scripts/eval-matrix.sh` ([evaluation](../evaluation/eval-suites.md)) |
| App icons per OS | `docs/spec_baseline/specification/assets/icon/dist/{macos,linux,windows}/` |
| UI mockup (visual reference) | `docs/spec_baseline/specification/mockups/ui-mockup.html` |
| Packaging output | `build/package/` (generated) |

## What is git-ignored

From `.gitignore`:

| Pattern | Why |
|---|---|
| `build/`, `out/`, `bin/`, `dist/`, `.gradle/`, `.kotlin/` | build output (the icon `dist/` folder under the spec assets is re-included) |
| `.temporary_context/` | the owner's local-only data: sample books, the corpus, eval inputs, outputs and history. Never committed, never kept in the home folder. It has its own `README.md` describing the kept folders |
| `.sonar-reports/`, `.scannerwork/` | local SonarQube output |
| `.worktrees/`, `.serena/` | local agent worktrees and tooling state |
| `.idea/`, `*.iml`, `.vscode/`, `.settings/`, `.classpath`, `.project` | IDE files |
| `*.log`, `logs/`, `hs_err_pid*`, `replay_pid*`, `__pycache__/` | logs, crash dumps, Python caches |
| `*.dmg`, `*.msi`, `*.deb`, `*.rpm`, `*.pkg`, `jpackage/` | packaged artifacts |

Tests and docs never depend on `.temporary_context/`: the corpus sweep (`BOOKLOOM_CORPUS_DIR`) and the prompt evals
read it only when you point them at it, and skip otherwise.
