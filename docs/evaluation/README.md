# Evaluation

How BookLoom measures translation quality and speed, why those measurements live outside the build, and where each
procedure and result is recorded.

## Purpose

Unit and end-to-end tests prove that the code does what it says. They cannot say whether a prompt, a check or a
pipeline change makes translations better, because that depends on a real model. Evaluations answer that question: they
run the production prompt builders and the real job against a local model and compare the result with a fixed
baseline. A prompt or pipeline change that claims a quality effect is decided by an eval, not by reading a few outputs.

## Tagged test sets outside `check`

The test convention plugin (`modules/build-logic/src/main/kotlin/bookloom.test-conventions.gradle.kts`) excludes five
tags from every ordinary `Test` task and registers one task per tag. None is wired into `check` or CI.

| Tag / task | What it runs | Why it is excluded |
|---|---|---|
| `promptEval` (`./gradlew :pipeline:promptEval`) | the eval suites below, against a real local model | needs a model, takes minutes to hours, results vary between runs |
| `liveLocal` (`./gradlew :llm:liveLocal :app:liveLocal`) | the real Ollama, LM Studio, llama-server and Unsloth servers; the engine detection matrix | needs running servers; checks the real wire, which WireMock covers in `check` |
| `visual` | pinned-environment snapshot diffs, when such tests exist | depends on a pinned display environment |
| `corpus` (`./gradlew :document:corpus`) | the round trip over the owner's book corpus | the corpus is local-only data |
| `soak` (`./gradlew :pipeline:soak`) | a whole generated book under injected faults on a scripted clock | slow; a night's run compressed into minutes |

Each set is env-gated: without its variables (`BOOKLOOM_EVAL_OLLAMA_URL`, `BOOKLOOM_LIVE_*`, `BOOKLOOM_CORPUS_DIR`, …) a
test skips through a JUnit assumption. `promptEval` and `corpus` are never up to date, so Gradle always reruns them.
Details: [../development/testing.md](../development/testing.md#testing).

The eval code is test code in `modules/pipeline/src/test/java/ua/bookloom/pipeline/eval/`; its committed inputs are in
`modules/pipeline/src/test/resources/eval/` (invented text only).

## Scripts

| Script | Use |
|---|---|
| `scripts/probe.sh --case <id>` | the fastest loop: one case, one direct call through the production builders; prints the messages by section, request parameters, raw reply, parsed verdict and usage. `--dry` and `--provider pseudo` make no model call |
| `scripts/eval-matrix.sh` | runs `:pipeline:promptEval` per model and suite, one model resident and one Gradle run at a time, and prints a comparison table. `--quick` runs fast subsets; `--suite` selects a suite |
| `scripts/eval-models.txt` | the one list of models the matrix runs, `<provider>:<model> [context-length] [label]`; the first line (the e4b target) is the default |
| `scripts/eval-score.py` | compares stored runs: `list`, `diff`, `diff --paired` (sign test), `blind` (blind pair sheets), `calibrate` (judge against human labels), `baseline` (freeze a run as a committed baseline), `selftest` |

The suites most used for the pipeline are:

- `mini` — an invented mini book per pair (`mini-en`, `mini-ru` datasets), end to end through the real job, with hard
  and soft graders;
- `sequence` — a generated synthetic book of about 320 paragraphs through the real job; measures term consistency,
  name variants, narrator-gender slips, flags, calls and seconds per segment;
- `book` — one whole book file through the job (`--book-file`, `--out-dir`), for the owner's local books only.

Other suites (`realrun`, `batch`, `prescan`, `terms`, `setup`, `retry`, `replay`, `recall`, `stability`, `gold`,
`names`, `coref`, `article`) are listed in the header of `scripts/eval-matrix.sh` and described in
[eval-suites.md](eval-suites.md). The recommended loop is probe, then `--quick`, then `--suite mini`, then one full run
per suite on e4b, and the larger model once at the end of a change ([eval-suites.md](eval-suites.md#fast-loop)).
Script usage in general: [../project/scripts.md](../project/scripts.md).

## Baseline and statistics

- **Baseline commit `79ffede6`** is the pipeline before the redesign. Its runs were frozen with
  `eval-score.py baseline` into `modules/pipeline/src/test/resources/eval/baseline/` (mini, sequence and article suites,
  invented text). A redesign result is always a paired comparison against these files.
- **Repeats and seeds.** Runs use K repeats with recorded seeds (the closing run used K = 3, seeds 1000–1002). The range
  of the repeats' aggregates is the run's noise band.
- **Sign test.** `diff --paired` averages each case's repeats, counts the discordant cases (only B better against only A
  better) and gives the exact two-sided sign test. A real effect needs p < 0.05; a judge score difference needs a
  bootstrap interval that excludes 0 and exceeds the noise band. A soft grader may not fall below the baseline minus the
  larger of the noise band and the smallest detectable effect.
- **Wilson interval.** Rates read by hand (for example meaning errors in 100 random paragraphs) are reported with the
  Wilson 95 % interval; `calibrate` uses the Wilson lower bound to decide whether the judge agrees with human labels
  well enough.

## The judge is a screen, not a grade

The eval judge is a local `gemma4:26b`, asked pairwise with the order swapped; a win counts only when both orders
agree. Its calibration against the labels of two readers (the developer agent and a second reader) failed the Wilson
bounds, so it is used **only as a screen**: it
picks the paragraphs that differ, and those paragraphs are then read **blind** (`eval-score.py blind` writes shuffled
pairs and a separate key) by a person or agent, and that reading is the grade. A result read by one reader says so.

## Where data lives

- Raw outputs, run histories (`.temporary_context/eval-history/`, one folder per run), reports of owner books, and
  every real book's text stay in the git-ignored `.temporary_context/`, described by its own `README.md`. Pass absolute
  paths (`$PWD/.temporary_context/…`); never write eval data to the home folder.
- Committed: invented-text datasets (hashed, with `ownerData: false`), baselines, and the result tables in this folder.
  Real books, their text, outputs and reports are never committed, and no code, prompt or fixture holds names or text
  from a tested book.

## Reading a report's provenance

Every report JSON starts with a `provenance` object. Check it before trusting a comparison:

| Field | Tells you |
|---|---|
| `gitSha`, `gitDirty`, `pipelineCommit` | which code produced it; a dirty tree means uncommitted changes were in play |
| `baselineOf` | which baseline this run is meant to be compared with |
| `promptHash` | whether the prompts were the same as in another run |
| `model` | id, provider, quantisation, window, thinking, seed policy and seeds |
| `dial`, `brief`, `window`, `suite`, `label` | the run's settings |
| `dataset` (`id`, `version`, `rootHash`, `labelsHash`, `ownerData`) | which inputs and labels were graded; a changed hash means the inputs or labels changed, so a difference may not come from the pipeline |
| `judge`, `repeats`, `aggregates`, `noiseBand` | how it was judged, K and the noise band |

`eval-score.py` refuses to compare reports of different versions and refuses to freeze a baseline from a run without a
dataset, with owner data, or without `baselineOf`.

## Map of this folder

| File | What it holds |
|---|---|
| [eval-suites.md](eval-suites.md) | how each suite is run and read: procedures, environment variables, the fast loop |
| [results-quality-rounds.md](results-quality-rounds.md) | quality rounds 15d–15f before the redesign: where the time went, before/after tables, time to first token |
| [results-prompt-rounds.md](results-prompt-rounds.md) | prompt rounds 15h–15j: baselines, brief prediction, translation rounds, experiments not adopted |
| [results-redesign-arms.md](results-redesign-arms.md) | the redesign's measured arms (paired experiments that gated each design choice) and the `79ffede6` baselines |
| [results-redesign-status.md](results-redesign-status.md) | redesign group status: final audit, language data, live checks per engine |
| [results-redesign-closing-run.md](results-redesign-closing-run.md) | the closing run against `79ffede6`: fixed pass rules, results, verdicts and the owner-accepted known limits |

## See also

- [../pipeline/translation-pipeline.md](../pipeline/translation-pipeline.md)
- [../pipeline/quality-gates.md](../pipeline/quality-gates.md#known-limits)
- [../pipeline/llm-providers.md](../pipeline/llm-providers.md)
- [../development/testing.md](../development/testing.md)
- [../project/scripts.md](../project/scripts.md)
- [../architecture/overview.md](../architecture/overview.md)
