# BookLoom documentation

BookLoom is a local-first, offline desktop app that translates whole books (EPUB, FB2, Markdown, TXT) with a locally
run LLM or any OpenAI-compatible endpoint. This folder holds the developer documentation. The operating manual for
contributors and agents is [`AGENTS.md`](../AGENTS.md) at the repository root; the project overview is the root
[`README.md`](../README.md).

## Start here

| You want to | Read |
|---|---|
| Install the tools and build for the first time | [development/getting-started.md](development/getting-started.md) |
| Run the window or the command line, find logs and the data folder | [development/running.md](development/running.md) |
| Find the right page for a task | [DEVELOPMENT.md](DEVELOPMENT.md) (a short index) |
| Find a folder, script, CI job or rule | [project/repository-map.md](project/repository-map.md) |
| See what BookLoom does not do well yet | [known-limits.md](known-limits.md) |

## Architecture

| Page | Covers |
|---|---|
| [architecture/overview.md](architecture/overview.md) | modules, layering, the runtime |
| [architecture/document-engine.md](architecture/document-engine.md) | parsing, masking, round trip, reassembly |
| [architecture/ui.md](architecture/ui.md) | the JavaFX window, state mirror, theme |
| [architecture/decisions.md](architecture/decisions.md) | the decisions that shaped the design |

## Pipeline and translation

| Page | Covers |
|---|---|
| [pipeline/translation-pipeline.md](pipeline/translation-pipeline.md) | Prepare, Translate, Proofread, the acceptance rule |
| [pipeline/context-glossary-memory.md](pipeline/context-glossary-memory.md) | context builder, glossary, translation memory, summaries |
| [pipeline/llm-providers.md](pipeline/llm-providers.md) | Ollama, LM Studio, OpenAI-compatible clients, model profiles |
| [pipeline/quality-gates.md](pipeline/quality-gates.md) | the deterministic checks and the never-worse gate |
| [development/provider-keys.md](development/provider-keys.md) | giving a provider a key through an environment variable |

## Development

| Page | Covers |
|---|---|
| [development/getting-started.md](development/getting-started.md) | prerequisites, first build, layout, build configuration, IDE, packaging, troubleshooting |
| [development/running.md](development/running.md) | window, command line, data folder, logs, the headless end-to-end proof |
| [development/testing.md](development/testing.md) | test tags, local-only sets, the focused loop, test speed |
| [development/quality-and-ci.md](development/quality-and-ci.md) | coverage, SonarQube, the gate, hooks, CI |
| [project/verification.md](project/verification.md) | how the project is verified, the Definition of Done, reading a gate failure |

## Evaluation

| Page | Covers |
|---|---|
| [evaluation/README.md](evaluation/README.md) | index of the evaluation material |
| [evaluation/eval-suites.md](evaluation/eval-suites.md) | how each prompt-eval suite is run and read |
| [evaluation/results-prompt-rounds.md](evaluation/results-prompt-rounds.md) | prompt rounds 15h to 15j |
| [evaluation/results-quality-rounds.md](evaluation/results-quality-rounds.md) | quality rounds 15d to 15f |
| [evaluation/results-redesign-arms.md](evaluation/results-redesign-arms.md) | the redesign arms |
| [evaluation/results-redesign-closing-run.md](evaluation/results-redesign-closing-run.md) | the closing run and the accepted limits |
| [evaluation/results-redesign-status.md](evaluation/results-redesign-status.md) | final audit, language data, live checks |

## Project and CI

| Page | Covers |
|---|---|
| [project/repository-map.md](project/repository-map.md) | annotated tree, modules, where things live, what is git-ignored |
| [project/scripts.md](project/scripts.md) | every script in `scripts/` and `tooling/hooks/` |
| [project/ci-and-hooks.md](project/ci-and-hooks.md) | the CI workflow, Lefthook stages, the gate, Sonar, licence check |
| [project/verification.md](project/verification.md) | test taxonomy, coverage, Definition of Done |
| [project/release-and-packaging.md](project/release-and-packaging.md) | jpackage scripts, outputs, launch smoke tests |
| [project/ai-agent-setup.md](project/ai-agent-setup.md) | AGENTS.md, `.claude`, `.agents`, `.codex`, OpenSpec |

## Spec baseline

[`spec_baseline/`](spec_baseline/) is the original specification, the architecture decision records and the
implementation plan the app was built from. It is kept as a baseline: it records what was intended and why, and it is
still edited when a clause is shown wrong by the code, but it is not a description of the current behaviour. The live
behavioural specifications are the capability specs under [`openspec/specs/`](../openspec/specs/), which grow as each
OpenSpec change is archived.

| Folder | Holds |
|---|---|
| [spec_baseline/specification/](spec_baseline/specification/) | requirements, architecture, non-functional rules, build and release, dependencies, mockup, diagrams. Start at [INDEX.md](spec_baseline/specification/INDEX.md) |
| [spec_baseline/adr/](spec_baseline/adr/) | architecture decision records, ADR-0001 to ADR-0047 (see its [README](spec_baseline/adr/README.md)) |
| [spec_baseline/implementation_plan/](spec_baseline/implementation_plan/) | [CHANGE_BACKLOG.md](spec_baseline/implementation_plan/CHANGE_BACKLOG.md), [01_MODULE_INVENTORY.md](spec_baseline/implementation_plan/01_MODULE_INVENTORY.md), roadmap |
| [../openspec/specs/](../openspec/specs/) | the live capability specs (17 capabilities) |
| [../openspec/changes/](../openspec/changes/) | the open change `redesign-translation-pipeline`, and `archive/` with the finished ones |
