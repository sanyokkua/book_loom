# Scripts

Every script in `scripts/` and `tooling/hooks/`, grouped by job. Run them from the repository root. The shell scripts
are bash (the hooks are POSIX `sh`); on Windows use Git Bash or WSL. Python scripts use only the standard library
(Python 3.9 or newer).

| Group | Scripts |
|---|---|
| [Inner loop and tests](#inner-loop-and-tests) | `test-focused.sh`, `slowest-tests.py`, `ci-linux-ui.sh` |
| [The gate and hooks](#the-gate-and-hooks) | `.lefthook/pre-push/*.sh`, `tooling/hooks/*.sh` |
| [Packaging](#packaging) | `jpackage-common.sh`, `package-macos.sh`, `package-linux.sh`, `package-windows.bat`, `launch-smoke.sh`, `launch-smoke-readonly-db.sh` |
| [Evaluation](#evaluation) | `eval-matrix.sh`, `eval-models.txt`, `probe.sh`, `eval-score.py`, `gradle-lock.sh`, `model-knowledge-check.py`, `segment-histogram.py` |
| [Fixtures and validation](#fixtures-and-validation) | `make-fixtures.py`, `validate-translated-book.py`, `e2e-fixture.sh`, `md-to-books.py` |
| [Agent config](#agent-config) | `sync-agent-files.py` |
| [Script tests](#script-tests) | `scripts/tests/` |

## Inner loop and tests

| Script | Purpose and usage | Needs | Output |
|---|---|---|---|
| `scripts/test-focused.sh` | Formats and tests only what the working tree changed (diff against `HEAD` plus untracked files): `spotlessApply` per changed module, the test classes the diff points at (or `fastTest` when none can be pointed at), then `fastTest` of every dependent module. A change under `modules/build-logic`, `gradle/` or a root `*.gradle.kts` counts as every module. `scripts/test-focused.sh [options] [module...]`; options `--tests <pattern>`, `--fast`, `--full-module` (runs `check` for the changed modules), `--no-dependents`, `--base <ref>`, `--dry-run`, `--lines <n>`. | JDK 25, git | tail of the Gradle output; full log in `build/test-focused.log`; exit status is Gradle's |
| `scripts/slowest-tests.py` | Reads the JUnit XML Gradle already wrote under `modules/<m>/build/test-results/<task>/` and prints per-module totals, the slowest classes and the slowest methods. `python3 scripts/slowest-tests.py [module...] [--task fastTest] [--top N]` | a previous test run | text on stdout |
| `scripts/ci-linux-ui.sh` | Runs the `:ui` tests that depend on the host font (default `*FitTest` and `ProjectsScreenTest`) inside an Ubuntu container, as the CI runner would. `scripts/ci-linux-ui.sh [--tests '*SomeTest']` | a running Docker daemon (Colima works) | Gradle output; build output stays in the Docker volume `bookloom-ci-work`, Gradle cache in `bookloom-ci-gradle` |

When to run: `test-focused.sh` while coding; `--full-module` once before each commit; `ci-linux-ui.sh` before pushing a
change to a screen, a dialog or a UI word. See [verification.md](verification.md).

## The gate and hooks

These are called by Lefthook, not by hand. Details in [ci-and-hooks.md](ci-and-hooks.md).

| Script | Purpose | Runs at |
|---|---|---|
| `.lefthook/pre-push/quality-gate.sh` | `exec ./gradlew clean build check spotlessCheck`, character for character the CI quality command | pre-push |
| `.lefthook/pre-push/agent-config-sync.sh` | `exec python3 scripts/sync-agent-files.py --check` | pre-push |
| `tooling/hooks/conventional-commit.sh <msg-file>` | rejects a commit message whose first line is not `type(scope)!: description`; allowed types `build chore ci docs feat fix perf refactor revert style test`; merge, revert, fixup, squash and amend headers pass | commit-msg |
| `tooling/hooks/file-size-guard.sh <files...>` | rejects staged files over 4096 KB; override per commit with `BOOKLOOM_MAX_FILE_SIZE_KB` | pre-commit |

## Packaging

Details in [release-and-packaging.md](release-and-packaging.md). All of them need `./gradlew :app:collectDist` first.

| Script | Purpose and usage | Output |
|---|---|---|
| `scripts/jpackage-common.sh` | Shared settings, sourced by the macOS and Linux scripts (not run on its own): app name, main class, input and output folders, version resolution (`APP_VERSION`, else the Gradle version, else `dev`), the jlink runtime, the shared jpackage arguments | none |
| `scripts/package-macos.sh` | app-image, a tarball of the `.app`, then a `.dmg`. `./scripts/package-macos.sh` | `build/package/` |
| `scripts/package-linux.sh` | app-image, a tarball, and a `.deb` when `fakeroot` is installed (otherwise a warning and no `.deb`). `./scripts/package-linux.sh` | `build/package/` |
| `scripts/package-windows.bat` | portable app-image only. `scripts\package-windows.bat` | `build\package\BookLoom\BookLoom.exe` |
| `scripts/launch-smoke.sh [image]` | starts the packaged image headless in a temporary data folder and waits for four log lines; checks the environment is `PROD`. Timeout `SMOKE_TIMEOUT_SECONDS` (default 90) | exit status; log excerpt on failure |
| `scripts/launch-smoke-readonly-db.sh [image]` | starts the image once, makes `bookloom.db` read-only, starts it again and expects the startup-failure message line, not a window | exit status |

## Evaluation

Prompt and pipeline evaluation against a real local model. None of this runs in CI or `check`. How to read the results:
[evaluation/eval-suites.md](../evaluation/eval-suites.md).

| Script | Purpose and usage | Needs |
|---|---|---|
| `scripts/eval-matrix.sh` | Runs the `:pipeline` `promptEval` over the first model in `scripts/eval-models.txt`, or `--all-models`, or `--models "<provider>:<model> ..."`, one model resident and one Gradle at a time, then prints a comparison table from `build/reports/promptEval/*.json`. Options include `--suite <batch\|realrun\|sequence\|prescan\|terms\|setup\|retry\|replay\|recall\|stability\|gold\|names\|coref\|mini\|article\|book>`, `--quick`, `--only`, `--table-only`, `--book-file`, `--out-dir`, `--langs`, `--repeats`, `--pair`. Run `head -60 scripts/eval-matrix.sh` for the full list | Ollama or LM Studio serving the models (`lms` for LM Studio loads), `scripts/gradle-lock.sh` |
| `scripts/eval-models.txt` | The default model list for the matrix, one per line: `<provider>:<model> [context-length or -] [label]` | read by `eval-matrix.sh` |
| `scripts/probe.sh` | The fast loop: one eval case, one direct call through the production prompt builders. `scripts/probe.sh --case <id> [--repeat N] [--dry] [--provider ollama\|lmstudio\|pseudo] [--url U] [--model M] [--window N]`, or `--list [prompt\|defects\|realrun]` | builds `:pipeline` test classes once; a model for real calls |
| `scripts/eval-score.py` | Compares eval runs kept in `.temporary_context/eval-history/`: `list`, `diff A B`, `diff --paired`, `calibrate`, `show`, `blind`, `baseline`, `selftest` | eval history folder |
| `scripts/gradle-lock.sh <command...>` | Queues Gradle-using commands so only one runs at a time across worktrees and eval batches. Not re-entrant: never wrap `eval-matrix.sh` or `probe.sh` in it, they take it themselves. The lock folder defaults to the owner's machine path `/private/tmp/claude-501`; set `BOOKLOOM_GRADLE_LOCK_DIR` elsewhere | bash |
| `scripts/model-knowledge-check.py <research-data-dir>` | Compares the bundled quant and declared-language CSVs in `modules/llm/src/main/resources/ua/bookloom/llm/knowledge/` with the research data they came from. Exit 0 agree, 1 differences, 2 usage error | the research CSVs |
| `scripts/segment-histogram.py <book>...` | Read-only histogram of words per block for EPUB, FB2, Markdown or TXT books | none |

## Fixtures and validation

| Script | Purpose and usage | Output |
|---|---|---|
| `scripts/make-fixtures.py [--out DIR]` | Generates the "Earth Gravity" fixture book in TXT, Markdown, FB2 and EPUB, with images, a dummy font and `manifest.json`, from one source in the script. Deterministic: two runs give identical files | default `modules/app/src/test/resources/fixtures/earth-gravity/` |
| `scripts/validate-translated-book.py <txt\|md\|fb2\|epub> <source_fixture> <translated_file> [--lang uk]` or `--self-test` | Checks a translated export against the fixture: structure (container, block inventory, inline markup, images and fonts byte-identical, no leftover placeholders) and language (target-script letters, glossary names rendered one way, declared language). `--self-test` also proves the committed fixtures have not drifted from `make-fixtures.py`. Exit 0 PASS, 1 FAIL, 2 usage | one line per check and PASS or FAIL |
| `scripts/e2e-fixture.sh <epub\|fb2\|md\|txt\|all> [options]` | The headless end-to-end proof: translates the fixture through the command line (`:app:translate`) with a served local model, then runs the validator. Options `--provider ollama\|lmstudio`, `--model`, `--quality fast\|balanced\|max`, `--names translate\|transliterate\|keep`, `--no-review-names`, `--out <dir>`, `-- <args>` passed to the translate command. Exit 0 when every format validates | `<out>/<fmt>/earth-gravity.uk.<fmt>`, `report.json`, `console.txt`, `validator.txt`; default `build/e2e` |
| `scripts/md-to-books.py SRC.md --lang uk --title T --author A --out-dir DIR [--formats epub,fb2] [--date D]` | Converts one GitHub-flavoured Markdown article to an EPUB 3 and an FB2 file, reproducibly, with no network | `DIR/<stem>.epub`, `DIR/<stem>.fb2` |

## Agent config

| Script | Purpose and usage |
|---|---|
| `scripts/sync-agent-files.py` | Keeps every agent harness reading the same bytes. `python3 scripts/sync-agent-files.py` reports only; `--apply` creates links and regenerates the Codex mirrors; `--check` exits 1 on drift (pre-push and CI); `--migrate` is a one-time move of `.claude/skills` to `.agents/skills`; `--root` overrides the repo root. Options come from `.agentsync.json`. See [ai-agent-setup.md](ai-agent-setup.md) |

## Script tests

`scripts/tests/` holds Python `unittest` modules for four of the scripts: `test_eval_score.py`, `test_md_to_books.py`,
`test_segment_histogram.py`, `test_sync_agent_files.py`. They are not run by Gradle, the gate or CI. Run them by hand,
for example `python3 -m unittest discover -s scripts/tests -p 'test_*.py'`.
