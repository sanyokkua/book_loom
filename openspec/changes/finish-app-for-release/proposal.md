# Proposal

## Why

A review of the built app (`research/review-digest.md`) showed that a book can end "Finished" with paragraphs still in
the source language, that code rules in Prepare create wrong name rows which every later call then follows, that about
40 % of a run's time changes nothing in the book, and that the window's shortest path skips the model's brief and
names work. The owner also listed UI defects, and the project has no release pipeline and carries a stale
specification tree. This change finishes the app for its first release: the default path becomes "drop a book, a
couple of clicks, an almost autonomous translation", and the repository becomes releasable.

## What Changes

Only measured or observed improvements are included (`research/review-digest.md#owner-decisions`).

Translation run (`:pipeline`, `:api`, `:llm`, `:persistence`, `:app`, `:ui`):
- **Finished means translated.** A cut draft is retried once; a paragraph with no target gets one more
  single-paragraph draft at the end of Translate, built only if a replay of the recorded cases recovers at least
  half of them; the Finished state and one Export warning name the paragraphs export would still write in the source
  language, counted by the code that decides what export writes.
- **Prepare stops creating wrong rows.** The name refusal by first letters and the shared-word swap are removed; a
  gender is no longer produced from a pronoun tally, a declension guess or a first-name list (a gender the person
  states or the code verifies, and the "likely" ones, behave as before); the list of recurring common words and the model calls that judge those
  words are removed. **BREAKING** for stored glossary and lexicon data.
- **A lean run.** Proofread reads only paragraphs with a mark a reader would see and answers with edits; chapter
  summaries are removed whole — the calls, their store, the `sum` log kind, the Book Brief "Chapter summaries" switch
  and "Story shape" (**BREAKING** for the stored brief); the "recently present" line, recurring-term rows and the
  dialogue reminder leave the translate request; a runaway reply is recovered instead of losing its last item.
- **Consistency by code.** After Translate, code pins a name the book agrees on and fixes minority spellings, fills a
  frequent row that stayed empty, holds one dialogue style per book, treats titles and honorifics as glossary rows,
  and builds a whole-book rendering report, counted again from the stored texts whenever the Export screen reads it
  and printed by the command line. No model call.
- **An autonomous default path.** The model's brief suggestion and the names pass run inside the run's Prepare stage,
  in the window and on the command line, each stored in one all-or-nothing write, and the run translates with the
  brief as stored after them; the Start questions are replaced by "not stated" defaults; the target
  language is pre-filled from the last one used; Start is offered on Import; small models start at Fast; gemma4:26b
  is the recommended model.
- **Three drafts, then merge, at Max — test first.** Built only for paragraphs marked risky, on a large model, and
  kept only if an in-app test passes the gate stated in `design.md` (at least 60 marked paragraphs read blind, a
  mean gain of at least +0.3 with twice as many better as worse, at most 15 % more whole-run time); otherwise removed
  in the same change.
- **An evaluation that sees quality.** A fixed real-book blind panel with a plain-ask control arm and the whole-book
  rendering report as the names metric; eval code for removed stages is deleted.

Window (`:ui`):
- The "Cannot start yet" banner no longer appears on Import, Projects, Settings and Export. A click on Start with no
  target language asks for it in a small dialog (not the existing "Translate into another language" dialog), and the
  Translating step is reachable when only the target language is missing.
- "About this book" opens with the book.
- The language and genre boxes behave as a standard editable combo box (click selects the text, typing filters, a
  mouse click and Enter both choose); the provider form's model entry is rebuilt on the same control.
- The five language-specific rows of "How to translate" (apostrophe, quotation marks, address between strangers,
  address in a family, hard g) are removed; the window is the same for every language and the language files keep the
  defaults. **BREAKING** for the stored brief.
- A model is chosen in one place, the provider form. Settings › Models loses its cross-provider chooser and edits the
  settings of the current provider's model; the model facts move under the provider's model picker.
- Names & style has one table: the Recurring terms card is removed.
- Polish defects found by the screenshot audit (`research/ui-audit-shell-dialogs.md`, `research/ui-audit-workflow.md`,
  `research/ui-audit-settings.md`) are fixed without restyling.

Repository (no spec-level behaviour except the window icon):
- CI/CD: a branch push builds only; a pull request runs the full verification in parallel jobs; a push to `master` is
  skipped when it came from a merged pull request and built when direct; a semver tag on `master` builds and publishes
  one portable archive per OS and architecture with no test suite and one launch check per POSIX archive, no signing
  and no installers; the Windows archive, which is not produced today, is added, and the `.dmg` and `.deb` outputs
  are removed from the packaging scripts.
- `docs/spec_baseline/` is deleted: decisions and still-valid specification text are folded into `docs/` and
  `openspec/specs/`, the icons move into the build and the window gets its icon, the mockup moves beside the test that
  reads it and stops being the binding visual reference, and every reference is rewritten. README, screenshots, agent
  files and the remaining docs are refreshed; unused scripts, stale suppressions and stale statements are removed.
- Every analyser, the local Sonar scan included, is at zero open issues at the end.

Stored record shapes change (brief, glossary, lexicon, chapter summaries, project, run record, model settings), so
the database is set aside and started fresh, as before every release (ADR-0043).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `translation-pipeline`: what a run does in Prepare (model brief suggestion and names pass inside the run), what a
  translate request carries, how a cut or runaway reply is handled, the last draft for a paragraph with no target,
  the consistency pass after Translate, the quality chosen by the model's size, the command line's Prepare steps, the
  three-draft merge at Max.
- `quality-gates`: the proofread keep rule, the last draft kept as a machine target, the checks behind the
  consistency-by-code pass (name spellings, one dialogue style, the rendering report and its freshness), typography
  from the language file alone. No gender check is removed or changed.
- `glossary`: removal of the name refusal, the shared-word swap, the tallied gender, the common-word recurring terms
  and the Start questions; pinning a name the book agrees on; titles and honorifics as rows; a frequent row may not
  stay empty; Names & style as one table.
- `export`: one warning before export for every paragraph export would write in the source language; what export
  writes for a flagged paragraph; the rendering report on the Export screen and in the command-line report.
- `book-brief`: the five language-specific options, the chapter-summaries switch and the story shape are removed;
  narrator and quality defaults replace the Start questions; the last used target language; the run writes its own
  suggestion without Apply.
- `book-import`: "About this book" is open when a book is opened (the fold and the opening report); Start
  translation on the Import toolbar.
- `app-shell`: where the cannot-start notice appears (the banner rule), Start with no target language, the shared
  choice box's behaviour, the Finished state's wording, the window icon, polish rules that are observable behaviour.
- `settings`: the Models tab without a chooser, the model facts under the provider's model picker, the recommended
  model, the default quality by the model's size.
- `inference`: the same requests for every model, the merge temperatures, the pseudo model's proofread reply.
- `resume`: a continued run repeats an unfinished Prepare step and continues the last drafts.
- `local-storage`: the stored shapes that change and the fresh database.
- `review-queue`: the context a segment was sent, and a retry's context, hold no chapter summary and no
  recurring-term rendering.
- `theming`: one look for folds, segmented controls, banner marks, and readable edges in both themes.

## Impact

- Modules: `:api` (brief, glossary, project, run and model-settings records), `:pipeline` (run stages, context,
  proofread, glossary, export report, evals), `:ui` (Import, Book Brief, Names & style, Translating, Export, Settings,
  shell, theme), `:app` (command line, window icon, packaging inputs), `:persistence` (the checkpoint port's names
  commit, two removed document kinds, the stored-shape fingerprint; the schema file does not change), `:llm` (the
  pseudo model's proofread reply and the readers of removed call kinds).
- No new dependency.
- Build and release: `.github/workflows/`, `settings.gradle.kts`, `modules/build-logic`, `scripts/package-*.{sh,bat}`,
  `scripts/jpackage-common.sh`, `scripts/launch-smoke.sh`, `lefthook.yml`, and the new scripts `scripts/ci-verify.sh`,
  `scripts/release-version.sh`, `scripts/check-docs.py`.
- Documents: `docs/`, `README.md`, `AGENTS.md`, `.claude/rules/`, `.agents/skills/`, `openspec/config.yaml`, the
  `Source` lines of `openspec/specs/`.
- Owner actions outside the repository (listed in `design.md`): squash-only merges and a ruleset for `master` and
  `v*` tags.
