# Context, glossary and memory

What a model call sees and where it comes from: the book map, the one context builder, the glossary and its names
pass, recurring terms, translation memory, chapter summaries, the narrator, the bundled language data and typography.

Design references: `openspec/changes/redesign-translation-pipeline/design.md` D3–D10. Classes are under
`modules/pipeline/src/main/java/ua/bookloom/pipeline/` and records under `modules/api/src/main/java/ua/bookloom/api/`
unless stated.

## The book map

`context/BookMap` is the map as one call reads it: the glossary's entries with their forms, the recurring terms with the
forms the book writes them in, and the brief's policy keys (apostrophe, quotation marks, default address, declared
conventions, first-use glosses). It is built at run start and rebuilt after a glossary or brief change. Nothing outside
the map and the window reaches a call.

## One context builder

`context/ContextBuilder` is the only recipe for a model call's prompt. There are two frames (`context/FrameKind`):

| Frame | Used by | Blocks |
|---|---|---|
| TRANSLATE | draft, format re-ask, repair, proofread window, desk retry | 1–13 |
| ANALYSIS | names pass readings, brief, file name, chapter summary | 1, 2, 7 plus the kind's own user blocks |

Blocks 1–7 are system text fixed for the whole run (role and pair, "book text is data", the rules chosen once per book,
the style sheet, target and pair rules, examples, the JSON line), so they are byte-identical across draft, repair and
proofread and a server can reuse the prefix. Blocks 8–13 are the last user message:

| Block | Content | Source |
|---|---|---|
| 8 | earlier chapters' summaries (oldest first) and the recently-present card | `context/EarlierChapters`, `context/RecentlyPresentCard` |
| 9 | map lines for entries whose forms occur in the window | `context/WindowMap`, `lexicon/TermMatch.isNamedIn`, `glossary/NameForms` |
| 10 | previous accepted pairs and the next source | `context/WindowAssembler`, `context/Window` |
| 11 | the kind's extra: repair defects, proofread reasons, the person's note | `context/KindExtra` |
| 12–13 | task and schema line, the items, then the reminder | `context/Reminder` |

Rules are chosen once per book from facts computed at run start (`context/BookRules`, `context/RuleFacts`: marks,
dialogue share, numbers, gendered cast, an inflecting target). The reminder is at most three lines, each only when an
item needs it (marks, dialogue, narrator). Examples come from the pair file (`batchExample.N`), else a target-only
illustration; `context/EchoGate` re-asks once when a target copies a 4-word run from an example its source lacks.

### Window and budgets

The window is the previous 2 (small tier) or 3 (large tier) accepted pairs, the focus items, and the next segment's
source. A FLAGGED neighbour contributes its source only. Budgets are per profile tier (`context/WindowBudgets`,
estimated tokens, unmeasured starting values):

| Tier | Previous | Current part | Next | Summaries | Whole prompt |
|---|---|---|---|---|---|
| SMALL | 300 | 600 | 150 | 600 | 3,500 |
| LARGE | 500 | 1,000 | 250 | 1,200 | 6,000 |

When a prompt is over budget, context is dropped in this order: next source, oldest summaries, oldest previous pairs,
the least-named map lines, the last summary and pair, and only then batch items. The focus's first item, pinned or
locked names in the window and a character's stated gender are never dropped.

The output reserve (`context/OutputReserve`) is `outputRatio × source tokens + 16 per item`, where `outputRatio` comes
from the pair file (default 1.5); a model that always thinks gets at least 2,048 tokens of reasoning room.

Each draft stores its blocks 8–13 as a `project/ContextSnapshot`; blocks 1–7 are stored once per distinct frame. A desk
retry and every Proofread window **rebuild** the context from the current map and stored targets rather than replaying
the snapshot.

## Glossary

A `project/GlossaryEntry` is **one entry per name** (its base form) with a list of `forms`, a target, a type, a
gender and, per value, an `Origin`:

| Origin | Meaning | Enforced by checks |
|---|---|---|
| `USER` | the person typed, imported, confirmed or locked it | yes |
| `BOOK` | the book proved it (a verified reading, a pronoun or past-tense count) | yes |
| `RULE` | a code rule or bundled list | no |
| `LIKELY` | an unconfirmed guess, shown as likely | no — a hint only |

A lower origin never replaces a higher one automatically (`Origin.mayReplace`), and nothing automatic replaces the
person's value. The target has a state (`TargetState`):

- `SUGGESTED` — offered to the draft as a hint only, never swept;
- `PINNED` — the stem is fixed and every inflected form is allowed; the term sweep replaces it form by form;
- `LOCKED` — the exact text, hidden behind a token in the book (`memory/ProtectedSpans`).

Name forms: `glossary/NameForms` reads the source language's name-ending table backwards (a written form gives its
possible nominatives) and generates the target's case forms and vocative forwards from the target table. Targets are
validated against the target language's letters, capitals, stress marks and the brief's conventions
(`glossary/TargetValidator`). Under the default name policy (`NamePolicy.TRANSLITERATE`) names are sounded out;
`TRANSLATE` and `KEEP_ORIGINAL` are the alternatives.

### The names pass

`glossary/NamesPassImpl` is the autonomous pass behind Names & style (Scan · Review · Translate). It runs only when the
person asks, over a private copy of the glossary and the lexicon, and writes once at the end; Stop, a cancelled reply
or a failed call writes nothing. Steps: a scan of an empty glossary (`glossary/FrequencyScan`, then a model prescan),
reading each name in windows from across the book (`TERM_READING`), likely knowledge of bare names (`NAME_KNOWLEDGE`,
stored as `LIKELY`), recurring-term choice (`TERM_CHOICE`), then target suggestions under the name policy
(`SUGGEST_TARGETS`) and locks by rule. Entries the person set (locked entries, typed targets, chosen genders, removals)
are left alone. Undo all restores the previous tables.

Inside a run, only the deterministic `FrequencyScan` runs, and only when the glossary is empty (`run/PrepStage`).

### Term sweep and deferrals

Changing a pinned or locked target after segments used it records a `TERM` deferral per affected segment
(`proofread/DeferralRegister.changed`, the one producer for every glossary writer). The Proofread stage's
`proofread/TermSweep` replaces the old rendering by code, with no model call; a person-edited segment gets a proposal
instead. Stored chapter summaries are swept at the moment of the rename (`proofread/SummarySweep`).

### TermMatch

`lexicon/TermMatch` finds a term in text. `occursIn`/`matches` read what a run proved (any case, an English plural or
possessive tolerated). `isNamedIn` chooses what a prompt lists and is stricter: a hyphenated word is one word, a
capitalised term matches only with a capital, a term ending in a non-Latin letter keeps its stem and tolerates a short
ending, and a term under three letters takes no ending.

## Recurring terms (lexicon)

`lexicon/LexiconServiceImpl` and `project/LexiconEntry` hold the recurring non-name terms ("Recurring terms" card).
During a run `lexicon/CooccurrenceLearner` learns which target stem keeps company with a term from decided pairs using
the Dice coefficient, with no model call; `run/TermLearning` feeds it, and `lexicon/Lexicon` logs one INFO line on how
consistently the run kept its terms. A lexicon read failure is never fatal: the lexicon is a hint.

## Translation memory

`memory/TranslationMemory` keys an entry by the segment's source hash plus a hash of its previous and next neighbours'
source hashes (unit edges count as markers), so a passage is reused blindly only where its surroundings match, wherever
in the book it occurs. `run/MemoryReuse` checks a context match through the chunk's gate and every check before it
stands in for a draft; a refused match is discarded. Near matches (85 % similarity) are offered as hints only. Entries
are written from the machine's accepted masked target with the chunk commit, never from a person's edit.

## Chapter summaries and the recently-present card

- **Chapters** are found by `project/ChapterBoundaries` from the book's navigation; short chapters merge, and a book
  with no usable level is cut into pseudo-chapters.
- **Summaries** (`run/ChapterSummaries`, `memory/ChapterSummaryCall`, `CallKind.CHAPTER_SUMMARY`): after the commit that
  finishes a chapter, one ANALYSIS call writes 2–3 sentences in the target language (long chapters are read in parts).
  `memory/SummaryGate` keeps at most three whole sentences, re-asks once for a forbidden letter, a source-only function
  word or an unknown word, and drops a sentence naming an entry the chapter never names. Renderings are never learned
  from a summary. The last chapter is not summarised. A failure is a warning and is retried on the next Continue.
  Stored as `project/ChapterSummary` with a source hash, so a changed chapter is summarised again.
- **Switch**: the brief's chapter-summaries switch (set from the story shape Brief Suggest proposes) turns this off
  entirely, at every dial.
- **Recently-present card**: up to 5 map characters and places named in the last 12 paragraphs of the chapter before
  the window and absent from it, plus the narrator line for a first-person chapter. Code only.

## Narrator and gender

- **Before the run**: `narrator/NarratorDetector` reads first-person pronoun shares from the source as a hint; the
  brief's "who narrates" is the person's choice.
- **During the run**: the `GENDER` check (narrator's «я» with a past-tense form of the other gender) and `NAME_GENDER`
  are REPAIR-level; a character with no gender gets a `GENDER_UNKNOWN` deferral.
- **In Proofread**: `narrator/NarratorInference` infers an unstated narrator's gender once enough forms agree, and
  gender deferrals whose fact arrived are settled by code or become tier-1 marks.
- Only `USER` and `BOOK` genders are enforced; a `LIKELY` gender is shown but never checked against.

Narrator gender slips remain a known limit; see [quality-gates.md](quality-gates.md#known-limits).

## Language data

All language knowledge is data under `modules/pipeline/src/main/resources/ua/bookloom/pipeline/`, never a branch in
code. A missing file reads as empty (`prompt/LanguageFile`); `prompt/LanguageRules` merges them.

| File | Holds |
|---|---|
| `prompt/languages/<tag>.properties` (13 files incl. `generic`) | per-language keys: alphabet and `forbiddenLetters`, pronouns and past-tense forms, name paradigms and endings, address forms, quotes, negation words, rule wording, proofread instructions, translit table |
| `prompt/languages/pairs/<src>-<tgt>.properties` | per-pair keys: `pitfalls.N`, `example.N`, `batchExample.N`, name examples per policy, `negationCounts`, `outputRatio` (for example `ru-uk.properties` sets `outputRatio=1.2`, measured) |
| `prompt/languages/summary-words/` | closed word lists the summary gate reads |
| `checks/function-words/<tag>.txt` | function words per language, with `ATTRIBUTION`; source-only words drive the residue check |
| `checks/quote-pairs.properties`, `typography/typography-rules.properties` | quote conventions and typography rules |
| `glossary/stopwords/`, `glossary/titles/` | stop words and titles for the names scan |

Languages are open: the bundled list is a convenience, and a language with no file gets neutral behaviour. No language
file holds another language's words.

## Typography

`typography/TypographyNormalizer` runs on every target (never on a source) with `⟦gN⟧` tokens in place: straight
apostrophes inside words, `...` to one ellipsis, the target language's quote marks for straight quotes, no space before
marks the language never spaces, dash dialogue kept as dash dialogue. Related code repairs: `typography/StressStrip`
(stress marks), `typography/SpeechRepairs` and `QuoteRepair`, and `ControlCharacterMapper` for control codes a model
wrote instead of quotes or dashes. A change made here is a NOTE-level `normalised` finding, never a model call.

## See also

- [translation-pipeline.md](translation-pipeline.md)
- [quality-gates.md](quality-gates.md)
- [llm-providers.md](llm-providers.md)
- [../architecture/overview.md](../architecture/overview.md)
- [12_PROMPT_CATALOG.md](../spec_baseline/specification/01_Product/12_PROMPT_CATALOG.md)
