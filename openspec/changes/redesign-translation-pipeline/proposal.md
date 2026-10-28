# Proposal: redesign the translation pipeline

## Why

Measured runs on real books (English→Ukrainian and Russian→Ukrainian, on small and mid-size local models) show defects
that tuning cannot fix:

- Each kind of model call builds its context its own way (about fifteen places), so the model sees different facts
  about the same book from call to call. Repairs see no names at all.
- The story memory is one rolling summary, refreshed only at Max.
- Extra passes cost hours without better accuracy. The per-chunk reviewer took about 30 % of model time, and 270 of its
  349 edits did not match the text they were meant to fix. Max took about twice as long with no gain. The export pass
  repeats checks.
- When source and target share an alphabet (Russian→Ukrainian), untranslated lines slip through: the checks forgive
  any word that also appears in the source.
- The glossary holds one entry per written form, so an inflected form becomes a new "name" (381 false name swaps in
  one run). A model's guess about a gender becomes an enforced fact and spreads one mistake over dozens of paragraphs.
- Every model and server is treated alike, although they differ in how thinking is switched off, in templates, in
  structured output, in quant quality and in how they report errors. One misread error paused a run for 43 minutes.

A completed research round (owner-local) and the owner's decisions show that a fixed context recipe, accepted text
that is protected from changes that make it worse, hints instead of enforced guesses, and handling each model and
server by its known facts fix these at the root. This change completes the earlier proposal
`docs/proposals/2026-10-16-translation-pipeline-redesign.md`. Its three open questions are now answered: meaningful
names are sounded out with the meaning kept as a note; the eval judge is the local `gemma4:26b`; the reviewer at
Balanced is off unless a paired test proves it finds mistakes nothing else finds.

## What Changes

**A run becomes three stages: Prepare, Translate, Proofread.**
- **Prepare builds a book map.**
  - The glossary holds one entry per base form, with its inflected forms.
  - Each value records where it came from: the person, the book, a rule, or the model's likely guess.
  - Each target is suggested, pinned or locked.
  - When the model is unsure it still gives its most probable value, marked "likely": a hint that is never enforced.
  - Names are transliterated by default for every language pair (г for a hard g where the target language declares
    it). They are adapted to the target language's own forms only when the person chooses that, and then by the model.
  - Brief Suggest also proposes "one story or separate stories", which sets a per-book chapter-summaries switch.
- **Translate uses one context builder for every model call.** A call sees:
  - the previous accepted pairs, the current piece and the next source;
  - only the book-map items that occur in that window;
  - the writing rules, chosen once per book, plus a one-to-three-line reminder;
  - examples for the book's own language pair;
  - two-to-three-sentence summaries of the earlier chapters, newest kept when room runs out.

  Drafts go out as one flat JSON batch format, 4 segments at a time on small models. A failing segment gets one
  repair, then a mark for the proofread. **BREAKING:** the directed-fix rounds and the soft-fix call are gone.
- **Generic checks for any language pair:** target alphabet, untranslated function words (computed from per-language
  files that every language has), formatting marks, numbers, negation and dialogue. Before any check counts, code
  repairs stress marks, dialogue dashes, heading numbers and quotes. **BREAKING:** the Ukrainian file's list of
  Russian words (`foreignWords`) and its check are removed. No language file holds another language's words.
- **Proofread runs inside the run, before export.** It edits only what is wrong, behind a code "never worse" gate, and
  resolves the open deferrals at every dial.
- **The dial gets a plain meaning, the same for every model:**
  - **Fast:** draft plus free checks that mark problems; no fixing calls; one retry when formatting marks are lost.
  - **Balanced:** also the one repair and a proofread of the marked windows.
  - **Max:** Balanced plus a re-read of the whole book.
- **Removed** (**BREAKING**):
  - the per-chunk reviewer at Balanced and Max (unless its paired test wins);
  - Max's backward revision;
  - the export "last check" pass (export now validates and writes with no model call);
  - the book-wide rolling summary.

**Each model and server is handled by its known facts.**
- **A model profile per model:** thinking switch, samplers, window, output cap, message shaping, quirks and batch
  size. It is resolved from engine traits, then server metadata, then the model's name (read from a name, link,
  command or file), then built-in rows, then the person's override on Settings › Models.
  **BREAKING:** Thinking and Sampling leave Settings › Generation.
- **A detected server engine:** Ollama (native API), LM Studio, llama-server, Unsloth, OpenRouter or a generic
  OpenAI-compatible server. It is detected on Save, Test and Detect again, with a "Treat this server as…" override.
- **Errors are classified by meaning**, each with a reason the run log names. An out-of-room reply flags the segment
  instead of pausing the run.
- **Advice only, never blocking:**
  - a quant-quality chip;
  - a line when the model's maker does not list the book's languages;
  - a name filter in every model picker;
  - Ollama cloud models shown with a neutral "cloud" label.
- **Privacy wording:** "private when used with local models", never "always local". The offline rule (ADR-0044) is
  unchanged.

**An eval harness v2 decides the gated choices.**
- It has hashed datasets of invented text (English and Russian mini books).
- A local `gemma4:26b` judge grades paired before/after runs with a sign test.
- Real books are checked only on the owner's machine.

**How the change is delivered.** One change, with one task group per build step, in dependency order. Each group has
an acceptance test seen red first, the full gate and, for visible behaviour, a hand check in the app. The research
planned these steps as separate changes; the owner chose one change. This is a deliberate exception to the project
rule that a change holds at most about ten tasks and a one-page proposal (`AGENTS.md#how-work-is-planned`); the
capability list below is the change's one map of seventeen groups, so it runs past a page. The task rule holds per group instead,
and each group has its own branch. The database is set aside up to eight times along the way, so projects, providers
and settings must be entered again after each of those groups.

## Capabilities

### New Capabilities
None. Every requirement lands in an existing capability.

### Modified Capabilities
- `translation-pipeline`:
  - the three stages and what each dial does in them;
  - one context builder and the window rule;
  - writing rules per book plus a reminder;
  - chapter summaries and a code-built recently-present card in place of the rolling and scene summaries;
  - one flat batch format, sized from the model profile;
  - one repair then a mark;
  - the Proofread stage inside the run;
  - the per-kind call ledger and run-log lines.

  Removed: the backward revision at Max, the rolling summary, adaptive batch growth.
- `quality-gates`:
  - check levels (block, repair, note);
  - code repairs before checks (stress marks, dashes, heading numbers, quotes);
  - untranslated function-word and foreign-script checks for any pair;
  - number, negation, terminal-mark and form-of-address parity;
  - the never-worse gate.

  Removed: the reviewer, its edit verification, the directed-fix rounds, the soft-fix call and the listed-foreign-word
  check.
- `glossary`:
  - one entry per base form with its forms;
  - origin (person, book, rule, likely) and target state (suggested, pinned, locked);
  - scan grouping by word forms;
  - likely model knowledge that is never enforced;
  - target suggestion under the name policy with transliteration by default;
  - validated Accept all;
  - stem groups for recurring terms.
- `book-brief`:
  - quote, apostrophe and form-of-address keys;
  - the target language's declared conventions (such as г for a hard g);
  - the story shape and the chapter-summaries switch;
  - the narrator's likely gender read from the source in Prepare.
- `llm-provider`:
  - detected server engines and per-engine request shapes;
  - LM Studio's own chat route and id check;
  - the error catalogue by meaning;
  - OpenRouter's correctness essentials;
  - chat-only model lists with a cloud label;
  - reading model names;
  - model profiles and how they are resolved.
- `inference`:
  - the reasoning setting per call kind from the profile, with an explicit off value per engine;
  - sampling from the profile;
  - one context size for every call;
  - thinking text removed from replies;
  - time to first token and prompt-reading time per engine, where a missing figure is shown as missing, not zero;
  - a refused setting remembered per model and call kind.
- `settings`:
  - Settings › Models shows each model's identity, quant, language and thinking/sampling overrides;
  - Generation keeps only app-wide items;
  - Providers shows the detected engine, the override and the optional advanced checks;
  - a name filter in every model picker;
  - Automation loses the last-check default;
  - the Providers screen says why a model check is refused while a run translates (the existing refusal, reworded).
- `review-queue`:
  - the desk shows proofread verdicts and their before text, with Undo proofread;
  - Try again rebuilds the context from the current glossary and settings;
  - deferrals arrive as desk items at Fast.

  Removed: backward-revision proposals.
- `export`: validate and write, with no model call and banners for open work. Removed: the last-check pass and its
  dialogs.
- `app-shell`: the progress dialog, background-work rules and Esc handling lose the export last check; the dialog
  and the title bar show the Proofread stage.
- `local-storage`: every stored-shape change sets the old database aside. This covers engine and probe verdicts, model
  overrides, lemma entries, policy keys, segment snapshots, chapter summaries, proofread verdicts and the export record.
  Global Thinking, Sampling and the last-check setting are no longer stored.
- `resume`:
  - Continue resumes whichever stage has work;
  - a proofread window resumes by fingerprint;
  - missing chapter summaries are written first;
  - the repair-round resumption is removed.

## Impact

- **Modules:** `:api` (new records: model identity and profile, server engine, origin, target state, chapter summary,
  proofread verdict; changed records: glossary, lexicon, brief, provider, model settings, segment). `:llm` (engine
  detection and traits, error catalogue, name reading, profile resolution, quant and language advisors, knowledge
  files). `:pipeline` (book map, context builder, checks and the one repair, never-worse gate, Proofread stage,
  chapter summaries, removals; prompts; eval harness v2 in its tests). `:persistence` (stored shapes, chapter
  summaries, proofread verdicts). `:ui` (Names & style, Book Brief, Translating, review desk, Export, Settings ›
  Providers, Models, Generation and Automation, model pickers). `:util` (stress-mark stripping shared by every module). `:app` (bindings).
- **Dependencies:** no new runtime library. The per-language function-word files are copied from one open source
  (stopwords-iso, MIT, or Lucene's analyzer stop sets, Apache-2.0). The licence and attribution are recorded in the
  language-data group before any file is copied.
- **Stored data:** no migration before the first release (ADR-0043). Each group that changes a stored shape changes
  the shape fingerprint, so the next launch renames the old database to `bookloom.db.old-<time>` and starts a fresh
  one. Projects, providers and settings are lost each time. The owner accepted this.
  - The groups that do it: provider engines, model profiles, lemma book map, stage-B checks, context builder, chapter
    summaries, proofread, remove old passes.
- **Decisions recorded:**
  - A new ADR (three stages, one repair, the never-worse gate, no model call at export) supersedes ADR-0007 (the
    tiered judge and self-heal pipeline) and ADR-0038 (judge a chunk, with batch caps of 8, 8 and 2).
  - A new ADR (server engines and per-model profiles) amends ADR-0005 (two provider kinds only), ADR-0013 (reasoning
    set low or off for every call) and ADR-0033 (provider-neutral call settings).
- **Words:** every new word is added in English and Ukrainian under the existing localization requirements (complete bundles, Ukrainian plural forms); no localization requirement changes.
- **Specification:** the contradicted clauses of `docs/specification/**`, the pipeline diagrams, the mockup README and
  `.claude/rules/{llm-provider-integration,offline-and-privacy}.md` are fixed by the group that changes each behaviour.
- **Out of scope:**
  - the document layer (for example FB2 inline code);
  - the command line's folder mode;
  - Anthropic and Gemini providers;
  - OpenRouter routing, data-policy and free-tier controls;
  - translation-tuned models;
  - a free-text per-model instruction field.
