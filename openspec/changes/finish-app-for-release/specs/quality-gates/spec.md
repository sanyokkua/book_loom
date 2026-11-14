## ADDED Requirements

### Requirement: Replace another spelling of a pinned name by code after the Translate stage

WHEN the Translate stage of a run ends and a decided body paragraph whose source names a pinned character, place or title row holds another spelling of that row's target, the application SHALL, with no model call, replace that word by the same case form of the pinned target and store the result only when it passes the placeholder gates.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#consistency-numbers`, `design.md#d17--the-consistency-pass-agreement-pinning-and-the-sweep`. In plain words: where a row has a right target the model keeps it in 98–100 % of paragraphs, and what is left is a handful of slips nobody fixes. Another spelling is the same word once doubled letters are collapsed and the target language's voiced and voiceless pairs are folded, or a form of the rendering the book did not agree on: 14 paragraphs with a second spelling of the main character in one book. The whole-book check that finds them only reported them. Code can put them right, because the fix is one word for another with the ending kept, and it needs no model. A paragraph the person edited is never overwritten; the fix waits on it as a proposal.

#### Scenario: A second spelling of the main character is replaced

- **WHEN** `Brenz` → `Бренз` is pinned, 623 decided paragraphs hold `Бренз` or a form of it, and 14 hold `Бренс` or `Бренса`
- **THEN** `Бренс` is counted as a second spelling of 14 paragraphs against 623, not as a form of `Бренз`; the 14 paragraphs read `Бренз` and `Бренза` in those places, 14 paragraphs are counted as fixed, and no model call was made

#### Scenario: The minority rendering of a row the book pinned is replaced

- **WHEN** the book agreed on `Гейл` for `Hale` in 19 of 20 paragraphs and one paragraph reads `Хейла не було вдома.`
- **THEN** that paragraph reads `Гейла не було вдома.`

#### Scenario: A different name is not a spelling

- **WHEN** `Brenz` → `Бренз` is pinned and a paragraph whose source names `Brenz` also holds the word `Брена`
- **THEN** `Брена` is unchanged

#### Scenario: A person's edit is not overwritten

- **WHEN** the person edited the paragraph `ch04 · p12`, which holds `Бренс`
- **THEN** its text is unchanged and the fixed text waits on it as a proposal

#### Scenario: A fix the gates refuse is not stored

- **WHEN** replacing `Бренс` in `ch07 · p03` would leave the paragraph failing the placeholder gate
- **THEN** the paragraph is unchanged and is counted as not fixed in the rendering report

#### Scenario: A paragraph whose source does not name the row is not read

- **WHEN** a paragraph holds the word `Бренс` and its source does not hold `Brenz`
- **THEN** the paragraph is unchanged

### Requirement: Hold one dialogue style per book

WHEN the Translate stage of a run ends, the application SHALL count the decided body paragraphs that open speech with a dialogue dash and those that write speech in the target language's quotation marks, take the style of the larger count as the book's — the target language's own default on a tie or when fewer than 10 paragraphs hold speech — and rewrite, with no model call, each paragraph of the other style that has one of the shapes of the scenarios below.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#consistency-numbers`, `design.md#d18--one-dialogue-style-per-book`. In plain words: one whole book came out with 897 dash paragraphs, 529 quoted ones and 161 hybrids, both styles inside 27 of its 39 chapters, because each draft kept whatever the model chose. A reader notices that at once, and it is pure punctuation, so code can settle it: the style the book already uses most wins, and the paragraphs of the other style are rewritten where the rewrite is certain. The language's default (a dash where the language file sets `dialogue-dash`, quotation marks elsewhere) only breaks a tie.

#### Scenario: The larger count is the book's style

- **WHEN** a Ukrainian translation holds 897 paragraphs that open speech with a dash and 529 that write it in «…»
- **THEN** the book's style is the dash, and the quoted paragraphs of the listed shapes are rewritten

#### Scenario: A tie takes the language's default

- **WHEN** a Ukrainian translation holds 40 dash paragraphs and 40 quoted ones
- **THEN** the book's style is the dash, since the Ukrainian file sets `dialogue-dash`
- **AND** an English translation with 6 speech paragraphs in all keeps quotation marks

#### Scenario: A paragraph that is one speech

- **WHEN** the book's style is the dash and a paragraph reads `«Де ключ?»`
- **THEN** it reads `— Де ключ?`
- **AND** WHEN the book's style is quotation marks, `— Де ключ?` reads `«Де ключ?»`

#### Scenario: A speech followed by its attribution

- **WHEN** the book's style is the dash and a paragraph reads `«Залишайся», — сказав він.`
- **THEN** it reads `— Залишайся, — сказав він.`
- **AND** WHEN the book's style is quotation marks, `— Залишайся, — сказав він.` reads `«Залишайся», — сказав він.`

#### Scenario: A speech split by its attribution

- **WHEN** the book's style is the dash and a paragraph reads `«Іди, — сказала вона. — Уже пізно».`
- **THEN** it reads `— Іди, — сказала вона. — Уже пізно.`

#### Scenario: A hybrid of both styles

- **WHEN** a paragraph reads `«— Де ключ?»`
- **THEN** it reads `— Де ключ?` in a dash book and `«Де ключ?»` in a quotation-mark book

#### Scenario: Any other shape is left and counted

- **WHEN** the book's style is the dash and a paragraph reads `Він озирнувся й тихо спитав: «Де ключ?» Ніхто не відповів.`
- **THEN** the paragraph is unchanged and is counted in the rendering report as speech of the other style

### Requirement: Change only punctuation when the dialogue style is unified

IF a dialogue-style rewrite would add, remove or change any letter, digit or placeholder token, would leave the paragraph with a quote-balance finding it did not have, or the person edited the paragraph, THEN the application SHALL keep the paragraph as it was.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `design.md#d18--one-dialogue-style-per-book`. In plain words: the step exists to settle dashes and quotation marks, never wording. The code proves that by comparing the letters, digits and tokens before and after, in order; anything else is left for a person. That keeps the promise the review found true of the code after the model: it changes punctuation and case only.

#### Scenario: The words are the same before and after

- **WHEN** `«Він сказав „ні“ і пішов», — мовила вона.` is rewritten to `— Він сказав „ні“ і пішов, — мовила вона.`
- **THEN** the letters, digits and tokens of both texts are equal in order, and the rewrite is stored

#### Scenario: A rewrite that would unbalance the quotes is dropped

- **WHEN** a rewrite of `«Іди, — сказав він, — „додому“.` would leave an opening mark with no closing one that the paragraph did not have before
- **THEN** the paragraph is unchanged

#### Scenario: A person's edit is never rewritten

- **WHEN** the person edited `ch02 · p08`, which reads `«Де ключ?»` in a dash book
- **THEN** it still reads `«Де ключ?»`

### Requirement: Build a whole-book rendering report by code

WHEN the consistency pass has stored its rewrites, the application SHALL build, with no model call, a rendering report counted from the stored texts as they then stand: for each glossary row named in at least 3 decided body paragraphs, that number and each rendering with its count; the row's state — split while more than one rendering is found, agreed when exactly one is, without a rendering when none is; the dash and quotation-mark speech counts; and the paragraphs the pass fixed and left.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#consistency-numbers`, `design.md#d19--the-rendering-report`. In plain words: a consistently wrong name scored 100 % on the old consistency figure, which was counted over the recurring-term list and never over the names. The report is the table the review built by hand: for each name, how the book actually wrote it. It is what tells the person which names to look at, it is the names metric of the evaluation, and it costs no model call. It is always a count of the texts as stored at that moment, never the count the pass took before it changed them, so a spelling the pass corrected is not reported as a second spelling. A row's state in the report is not the rule that pins a name: a name is pinned when one rendering holds 80 % of at least 5 paragraphs (the `glossary` capability's "Pin a name's rendering once the book agrees on it"), but the report calls a row agreed only when the book writes it one way all through — inflected forms of one rendering count as one — so a single paragraph with the other spelling keeps the row among the split ones, where the person sees it.

#### Scenario: An agreed row, a split row and a row without a rendering

- **WHEN** `Hale` is named in 20 paragraphs (19 `Гейл`, 1 `Хейл` fixed by the pass), `Velk` in 26 (15 `Велк`, 11 `Вельк`) and `Marrow Lane` in 12 with no target
- **THEN** the report lists `Hale` · 20 · `Гейл` 20 · agreed, `Velk` · 26 · `Велк` 15, `Вельк` 11 · split, and `Marrow Lane` · 12 · no rendering

#### Scenario: A rare row is left out

- **WHEN** `Tobin` is named in 2 decided body paragraphs
- **THEN** the report holds no line for `Tobin`

#### Scenario: The dialogue counts and the fixes are reported

- **WHEN** the pass left 1,380 dash paragraphs and 46 quoted ones it left as they were, replaced a spelling in 14 paragraphs and left 1 unfixed
- **THEN** the report reads dash 1,380, quotation marks 46, spellings fixed in 14 paragraphs and not fixed in 1

#### Scenario: The report needs no model

- **WHEN** the report is built while no provider is reachable
- **THEN** it is built, and no network call is made

#### Scenario: A split row the pass corrected reads agreed

- **WHEN** `Hale` is named in 20 paragraphs, the pass counted 19 `Гейл` and 1 `Хейл` before its sweep, and the sweep replaced the one `Хейл`
- **THEN** the report lists `Hale` · 20 · `Гейл` 20 · agreed, and holds no `Хейл`

#### Scenario: A minority spelling the sweep could not replace keeps the row split

- **WHEN** `Hale` is named in 20 paragraphs with 19 `Гейл` and 1 `Хейл`, `Hale` was pinned as `Гейл`, and the document gate refused the replacement in that one paragraph
- **THEN** the report lists `Hale` · 20 · `Гейл` 19, `Хейл` 1 · split, and counts 1 paragraph not fixed

#### Scenario: Inflected forms of one rendering are one rendering

- **WHEN** `Hale` is named in 20 paragraphs that read `Гейл` 12 times, `Гейла` 5 times and `Гейлу` 3 times
- **THEN** the report lists `Hale` · 20 · `Гейл` 20 · agreed

#### Scenario: Two spellings that differ by one voiced letter stay two renderings

- **WHEN** `Brenz` → `Бренз` is pinned, 636 paragraphs hold `Бренз` or a form of it after the sweep, and the document gate refused the replacement of `Бренса` in `ch07 · p03`
- **THEN** the report lists `Brenz` · 637 · `Бренз` 636, `Бренс` 1 · split and counts 1 paragraph not fixed, although the two spellings differ only by the voiced and unvoiced letter that the search for near spellings treats as one

#### Scenario: A row left split keeps its counts

- **WHEN** `Velk` is named in 26 paragraphs with 15 `Велк` and 11 `Вельк`, so nothing was pinned or replaced
- **THEN** the report lists `Velk` · 26 · `Велк` 15, `Вельк` 11 · split

### Requirement: Keep the rendering report true to the stored texts

The application SHALL build the rendering report again, by the same count, WHEN the Proofread stage of a run has stored its last change — that report SHALL be the one stored with the run — and each time the Export screen reads the open work of a project, from the texts as they are stored at that moment.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `design.md#d19--the-rendering-report`. In plain words: the Proofread stage and the person both change texts after the consistency pass, so a report taken once would describe a book that no longer exists. The run keeps the count taken after its last change, which the command line prints and the evaluation reads; the Export screen never shows that stored copy and counts again whenever it is read — when a project opens, a run ends, a review-desk action is stored or a book is exported.

#### Scenario: The report stored with the run follows the Proofread stage

- **WHEN** the consistency pass left `Velk` at 15 `Велк` and 11 `Вельк`, and the Proofread stage then stored an edit that turned one `Вельк` into `Велк`
- **THEN** the report stored with the run lists `Velk` · 26 · `Велк` 16, `Вельк` 10 · split

#### Scenario: A person's edit back to the minority spelling shows the row split again

- **WHEN** the stored report lists `Hale` · 20 · `Гейл` 20 · agreed, and the person then edits `ch03 · p05` on the review desk so that it reads `Хейл`
- **THEN** the Export screen's report lists `Hale` · 20 · `Гейл` 19, `Хейл` 1 · split, although 19 of 20 would still pin the name

### Requirement: Keep the last draft of a segment with no translation when it is a translation

WHERE the last draft is built, WHEN the last draft of a segment (the `translation-pipeline` capability's "Draft once more a segment that ended with no translation") is not accepted, the application SHALL keep it as the FLAGGED segment's machine target when it restores through the placeholder gate and fails none of the gates `language-identity`, `refusal`, `locked-term`, `kept-run`, `protocol-leak`, `echo`, `script` and `repetition`; otherwise the segment SHALL keep no machine target.

**Source**: `research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`, `research/review-digest.md#f3b--a-paragraph-with-no-accepted-target-is-exported-in-the-source-language`, `design.md#d4--the-last-draft-one-more-single-draft-at-the-end-of-translate`, `design.md#d5--one-number-for-no-translation-shown-before-export`. In plain words: the last draft is built only if a replay of the recorded cases shows it recovers at least half of them (the first design anchor); where it is not built, a FLAGGED segment with no translation keeps none and is counted and named before export (the second anchor). Most paragraphs a finished novel leaves in the source language (24, 5 and 1 in the three measured books) are real translations that a check refused, most often the sentence count. A reader is better served by such a translation, flagged for review, than by a paragraph in the other language. A text that is not a translation — a leftover in the source language, a refusal, a loop, broken locked names — is still never written as one.

#### Scenario: A last draft that lost a sentence is kept and flagged

- **WHEN** the last draft of `She closed the window. Then she sat down.` is `Вона зачинила вікно і сіла.`, which fails only the `sentence-count` check
- **THEN** the segment stays FLAGGED with the `sentence-count` finding and its machine target is `Вона зачинила вікно і сіла.`

#### Scenario: A last draft in the source language is not kept

- **WHEN** the last draft of `She closed the window.` is `She closed the window.`
- **THEN** the segment stays FLAGGED and keeps no machine target

#### Scenario: A last draft with broken formatting marks is not kept

- **WHEN** the last draft of `He opened the ⟦g0⟧old⟦g1⟧ door.` holds no mark and code cannot put the marks back
- **THEN** the segment keeps no machine target and its rejected target is that reply

#### Scenario: A first draft is still held to the old rule

- **WHEN** a first draft fails `sentence-count`, its one repair fails it too, and the run has not yet reached the end of the Translate stage
- **THEN** the segment is FLAGGED with no machine target until its last draft is made

## MODIFIED Requirements

### Requirement: Keep a usable draft as the machine target when a text check still blocks

A segment that ends FLAGGED SHALL keep as its machine target the last candidate that restored through the placeholder gate and whose failed hard gates are only `quote-balance`, `script-purity` or `alphabet`, or, WHERE the last draft is built, the last draft kept by "Keep the last draft of a segment with no translation when it is a translation"; a paragraph in the wrong language SHALL keep none. What export writes for a FLAGGED segment is stated by the `export` capability's "Write a flagged segment in its source when no usable translation exists".

**Source**: `research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`, `design.md#d4--the-last-draft-one-more-single-draft-at-the-end-of-translate`. In plain words: a usable paragraph with one stray quote mark is exported as the model wrote it and flagged for a look. Whether a flagged segment holds a machine target is decided once, when the segment is decided. Export writes that machine target when there is one, and else the model's last refused reply when that reply is fit to print, and only then the source — the `export` requirement is the one statement of that order, and the number the export screen announces before writing is counted by the same code.

#### Scenario: A quote the repair cannot clear keeps the draft

- **WHEN** a draft with two unclosed « ends FLAGGED
- **THEN** its machine target is the draft, and the export writes it, not the source

#### Scenario: A draft written in another alphabet keeps the draft

- **WHEN** a Ukrainian draft that holds the Russian letter `ы` in one word ends FLAGGED with only the `alphabet` gate failed
- **THEN** its machine target is the draft

### Requirement: Keep a candidate text only when its score is not worse and its mark is cleared

WHEN no veto applies, the application SHALL keep a candidate only when its score (BLOCK findings weigh most, then REPAIR findings, then notes and audit doubts) is not higher than the current text's and one of these holds: the marking finding is cleared, the score is strictly lower, the paragraph holds no mark and the candidate changes no more words than the rewrite cap allows, or — WHERE the equal-score rule is kept — the paragraph is marked and the candidate changes no more words than the rewrite cap allows; otherwise the current text and its marks stay.

**Source**: `research/review-digest.md#f2--almost-nothing-improves-a-poor-first-draft`, `design.md#d11--a-proofread-reply-is-a-list-of-edits-each-judged-alone`. In plain words: the checks see only a small part of what a reader grades wrong (recall 0.13), so a correct small fix often leaves the code score equal. The "no gain" rule refused 53 of 60 proofread changes in the traced run, and 3 of the 5 compared by hand were real fixes (a meaning error, a word left in the source language, a narrator's gender). A proofread change is now an edit of a few words, so a small change that breaks no veto and makes no score worse is kept for a marked paragraph too, as it already was for an unmarked one in Max's whole-book round. The equal-score rule for a marked paragraph is kept only if, read blind on the panel, the kept equal-score edits judged better outnumber those judged worse; a tie, or no such edit, removes it and a marked paragraph is then held to "cleared or lower" (the design anchor).

#### Scenario: A candidate that clears the marked finding is kept

- **WHEN** the current text holds one REPAIR finding and the candidate holds none and passes every veto
- **THEN** the candidate is kept

#### Scenario: A candidate that swaps a blocking finding for a soft one is kept

- **WHEN** the current text holds a BLOCK `alphabet` finding on `кобылу`, the candidate writes `кобилу`, clearing it, but holds a REPAIR `gender` finding, and no counted check's finding count rises
- **THEN** the candidate is kept because its score is lower

#### Scenario: A candidate with an equal score that clears nothing is not kept

- **WHEN** the current text and the candidate both hold the marked REPAIR finding and the candidate changes 14 of the paragraph's 20 words
- **THEN** the current text stays with its mark

#### Scenario: A small rewrite of an unmarked paragraph in the whole-book re-read is kept at an equal score

- **WHEN** the paragraph holds no mark, the candidate changes 2 of its 20 words and the score is equal
- **THEN** the candidate is kept

#### Scenario: A large rewrite of an unmarked paragraph is not kept at an equal score

- **WHEN** the paragraph holds no mark, the candidate changes 14 of its 20 words and the score is equal
- **THEN** the current text stays

#### Scenario: A small edit of a marked paragraph is kept at an equal score

- **WHEN** the equal-score rule is kept and the paragraph `Він взял ключ і вийшов геть.` is marked by an audit doubt, the edit `взял` → `взяв` changes 1 of its 6 words, the doubt stays and the score is equal
- **THEN** the candidate is kept

### Requirement: Compare the last mark of a paragraph with its source's

WHEN the class of a target paragraph's last mark (`?`, `!`, `.` or `…`, with quotes and closing brackets ignored) differs from its source's, the application SHALL raise a REPAIR-level `terminal-mark` finding; an ellipsis against a full stop counts as a difference, and an ending whose last run of marks holds an ellipsis — `…`, `...`, or either of them followed by a further full stop — SHALL be read as an ellipsis.

**Source**: `research/review-digest.md#f2--almost-nothing-improves-a-poor-first-draft`. In plain words: most of the times a model drops the end of a paragraph, it also drops its question mark; comparing the last mark finds those omissions cheaply. A source that ends in four dots was read as a full stop, so a correct translation ending in an ellipsis was sent to a repair: 4 of the 8 repairs of the traced run answered this defect, which does not exist.

#### Scenario: A lost question mark is a finding

- **WHEN** the source is `Where is the key?` and the draft is `Де ключ.`
- **THEN** a REPAIR `terminal-mark` finding is raised

#### Scenario: Quotes around the mark are ignored

- **WHEN** the source is `“Where is the key?”` and the draft is `«Де ключ?»`
- **THEN** no `terminal-mark` finding is raised

#### Scenario: An ellipsis against a full stop is a difference

- **WHEN** the source is `He waited…` and the draft is `Він чекав.`
- **THEN** a REPAIR `terminal-mark` finding is raised

#### Scenario: A four-dot ending is an ellipsis

- **WHEN** the source is `He waited….` and the draft is `Він чекав…`
- **THEN** no `terminal-mark` finding is raised

### Requirement: Normalise apostrophes, ellipses and spacing

The pass SHALL turn a straight apostrophe between two letters into the target language's apostrophe (`’` for Ukrainian), three dots into `…`, and remove a space before `,` `.` `;` `!` `?` or `…` (French keeps the space before `?` `!` `;`); a language with no line in the quote tables gets these fixes only.

**Source**: `design.md#d26--the-book-brief-has-no-language-specific-rows`, `research/review-digest.md#owner-decisions`. In plain words: code writes the apostrophe of the target language, and the typographic ellipsis, that models almost never do.

#### Scenario: The person chose the straight apostrophe

- **WHEN** the Ukrainian draft is `Не пам'ятаю.` and the project's stored brief is read
- **THEN** the stored target is `Не пам’ятаю.`, and the brief holds no apostrophe choice that asks for `'`

#### Scenario: Apostrophe, ellipsis and quotes are normalised

- **WHEN** the Ukrainian draft is `Він сказав: "Не пам'ятаю..."`
- **THEN** the stored target is `Він сказав: «Не пам’ятаю…»`

### Requirement: Write quote marks in the target language's convention

For a target language that has a line in the quote table, the pass SHALL turn a straight `"` into the primary pair of the language's own convention at the top level and its nested pair inside it (`«…„…“…»` in Ukrainian), SHALL keep a dash dialogue a dash dialogue, and SHALL leave a quote whose side cannot be told, a stray closing mark and an inch sign as they are.

**Source**: `design.md#d26--the-book-brief-has-no-language-specific-rows`, `research/review-digest.md#owner-decisions`. In plain words: the model writes `"Іди геть"`, and code writes `«Іди геть»` where the side of every mark is certain and touches nothing it would have to guess.

#### Scenario: Straight quotes become the language's pair

- **WHEN** the Ukrainian draft is `Він сказав: "Іди геть".`
- **THEN** the stored target is `Він сказав: «Іди геть».`

#### Scenario: The person chose another pair

- **WHEN** the Ukrainian draft is `Він сказав: "Іди геть".` and the project's stored brief is read
- **THEN** the stored target is `Він сказав: «Іди геть».`, and the brief holds no quotation-mark choice that asks for `„…“`

#### Scenario: A dash dialogue stays a dash dialogue

- **WHEN** the Ukrainian draft is `— Іди геть, — сказав він.`
- **THEN** the stored target is unchanged

### Requirement: Catch a lost glossary name

While a segment is drafted, the deterministic text checks SHALL raise a `name-missing` finding for an
unlocked glossary name (a capitalised term with a rendering) that the source names as a whole word and no word of the
target holds by the stem rule, at REPAIR level, reporting its rendering; two entries one vowel or one doubled letter apart are
one name.

**Source**: `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: the Oct 9 runs lost a place name; this check catches it. A lost name is a repair-level finding at draft time, fixable by the one repair. A copied Latin phrase is the separate requirement "Block a copied Latin phrase". The stem rule is the one the name checks already use; the recurring-term list it was named after is removed.

#### Scenario: A lost place name earns a fix

- **WHEN** the glossary maps `Zurich` to `Цюріх`, the source says "They reached Zurich at dawn." and the draft says
  "Вони дісталися до міста на світанку."
- **THEN** the draft carries a `name-missing` finding at REPAIR level and, at Balanced and Max, earns the one repair
