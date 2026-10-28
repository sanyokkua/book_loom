## ADDED Requirements

### Requirement: Keep the narrator's most probable gender as likely and verify it only by evidence the code checks

WHEN the model's style suggestion answers the narrator's gender, the application SHALL set the gender from the book, shown as `Male, from the book` or `Female, from the book` with the quote, when the code verifies the quote it cites, and otherwise keep the answer as a likely gender, shown as `Male (likely)` or `Female (likely)`; a gender the person, the book's count or the run's count set is never replaced, and a third-person narrator gets none.

**Source**: FR-BRIEF-02; tasks 15h.G2, 15i.B4; the owner's decision that the model always gives its most probable value, marked likely, and that the person's value and verified book evidence win over it, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: the model was free to say "male" from nothing, and its answer was then thrown away for a book whose narrator was already set to first person. Now the model always gives its best answer. When it points at a line the code can check — a name it can look up or a word like "ma'am" — the line is the book's own evidence, so the gender is the book's; with nothing checkable it stays likely, so nobody takes it for a fact. No check holds the text to a likely gender: the translation-pipeline capability's "Tell the model who narrates and who is present" gives it to every call as `likely male` or `likely female`, which orders no verb form. The person's choice and the book's own forms outrank it.

#### Scenario: A vocative of a glossary person genders the narrator

- **WHEN** the glossary holds the character `Tobin` as male, the narrator is first person with no gender, and the model quotes `"Keep up, Tobin," Mara called back to me.` from the sample with the name `Tobin`
- **THEN** the narrator's gender is male and shown as `Male, from the book` with the quote

#### Scenario: A gender with nothing the code can check is still kept as likely

- **WHEN** the model answers `female` with a quote that holds neither a known name nor a word of address
- **THEN** the narrator's gender is female, shown as `Female (likely)` with no quote
- **AND** Start translation asks who narrates with `Female` already chosen

#### Scenario: A name the narrator says to someone else is no vote

- **WHEN** a window holds `"Mind the step, Lucy," I said` and the model answers `female` with the name `Lucy`
- **THEN** that window gives no gender vote

#### Scenario: A name in the quote proves the gender whatever the model said

- **WHEN** the quote calls the narrator by a name, or the narrator gives it (`"Keep up, Tobin," Mara called back to me.`), the name the model gives stands in the quote, and it is a glossary character of known gender (the whole term or one word of it) or a name of the source language's first-name list
- **THEN** that name's gender is the narrator's, from the book, whatever the model said

#### Scenario: Words of address of one gender prove it when no name does

- **WHEN** the quote has no name but holds words of the source language file's `addressTerms` table (`term>male` / `term>female`, such as `sir`, `ma'am`, `пане`, `дівчино`) of one gender only
- **THEN** that gender is the narrator's, from the book

#### Scenario: The narrator's own introduction is a vote

- **WHEN** a window holds `"I'm Lucy," I said`
- **THEN** the name `Lucy` is a vote for the narrator's gender, while a name the attribution beside quoted speech shows the narrator saying to someone else is not

#### Scenario: One window proves a gender unless another proves the other

- **WHEN** one window proves a gender and no other window proves the other gender
- **THEN** the gender is kept as the book's and shown with its quote and the number of proving windows, not as uncertain

#### Scenario: The likely mark explains itself

- **WHEN** the narrator's gender is shown as `Male (likely)` or `Female (likely)`
- **THEN** a hover explanation is shown, in English and Ukrainian

#### Scenario: A gender nobody would replace is not replaced

- **WHEN** the person set the narrator's gender to male and the model answers female
- **THEN** the narrator stays male as the person's choice

### Requirement: State a time estimate on the brief as measured or as an estimate

The Quality and speed card SHALL show the time as measured, "about … , measured on this model", when this model has measured seconds for every kind of call the plan makes; otherwise as a range marked "estimate" and where it was measured, scaled from this model's measured seconds per drafted segment; and no time when nothing was ever measured for the model. Tokens per second alone SHALL give no time, and the card SHALL NOT show a relative cost.

**Source**: `docs/specification/mockups/README.md#step-2--book-brief--b1b3-i`, `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: a made-up number for hours of work misleads, so a time appears only when the model was measured on this computer. When every kind of call the chosen quality makes (draft, repair, proofread) has its own measured seconds, the time is the planned calls of each kind times those seconds; when only a draft speed is known, from the model test or an earlier run, the built-in per-call figures are scaled by it and shown as a range that says it is an estimate and where the speed came from. A claim of how much dearer Max is than Fast was a guess too and is gone. This is the one place the estimate before Start is specified; the speeds it reads are stored by the settings capability's "Store the speed the inference test measured on the model".

#### Scenario: Stored seconds per call kind give a measured estimate

- **WHEN** the chosen model has stored seconds for each call kind and the book's plan at Balanced adds up to 8,460 seconds
- **THEN** the card shows "For this book on this computer: about 2 h 21 min, measured on this model"

#### Scenario: Measured per kind

- **WHEN** `gemma4:e4b` stored 3.0 seconds per draft call and 6.0 seconds per proofread call, and Balanced plans 300 draft calls and 20 proofread calls and no other kind
- **THEN** the card shows "For this book on this computer: about 17 min, measured on this model"

#### Scenario: A speed per segment gives a labelled estimate

- **WHEN** the model test measured 3.0 seconds per segment, no seconds per call kind are stored, the book has 3,000 segments and the dial is at Fast
- **THEN** the card shows a range that holds 2 h 30 min, such as "For this book on this computer: about 2–3 h, estimate from the model test", worked out from the built-in figures scaled by that speed

#### Scenario: A kind never measured makes it an estimate

- **WHEN** an earlier Fast run stored 3.0 seconds per draft call, no proofread call was ever measured, and the dial is at Balanced
- **THEN** the card shows a range marked "estimate from an earlier translation", not "measured on this model"

#### Scenario: Tokens per second alone give no estimate

- **WHEN** the title bar showed 9.4 tok/s during a test, but no seconds per segment were measured for the chosen model
- **THEN** the card shows no time and no relative cost

#### Scenario: Nothing measured shows no time

- **WHEN** no inference test and no earlier run exist for the chosen model
- **THEN** the card shows no estimate line and does not say Balanced costs about 1.5 times Fast

### Requirement: Offer the apostrophe, quotation-mark and default-address choices the target language sets

WHERE the target language's file sets them, the Translation policies card SHALL offer the apostrophe character, the quotation-mark style and the default address for strangers and for family, each with the file's default, and SHALL show no row for a choice the file does not set.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: typography and form of address differ by language, and a model drifts between them from page to page; the person fixes them once per book. The choices come from the target language's own file, so an English target shows no address row. Title of address and foreign-word treatment are out of scope.

#### Scenario: Ukrainian defaults

- **WHEN** the target is `uk` and a new brief is shown
- **THEN** the apostrophe is `’` (U+2019), the quotation marks are `«»` with `„“` inside, and the default address is `Ви` for strangers and `ти` for family

#### Scenario: A choice the file does not set has no row

- **WHEN** the target language's file sets no address words
- **THEN** the card shows no default-address row, and no address line reaches the instructions

#### Scenario: A change is kept

- **WHEN** the person sets the apostrophe to `'`
- **THEN** the brief holds `'`

### Requirement: Offer a choice for each convention the target language declares

WHERE the target language's file declares a convention with its choices and a default, the Translation policies card SHALL show one row for it, store the person's answer with the brief, and show no extra row when the file declares none.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: some conventions exist in only some languages, such as whether a hard g in a foreign name is written г or ґ in Ukrainian; the language file declares the choice and its default, and the screen needs no code per language. One name may still be spelled otherwise in the glossary.

#### Scenario: Ukrainian declares the hard g

- **WHEN** the target is `uk`
- **THEN** the card shows the row "Hard g in foreign names" with `г` chosen and `ґ` offered

#### Scenario: A language that declares nothing shows nothing

- **WHEN** the target language's file declares no convention
- **THEN** the card shows no convention row

#### Scenario: The answer is stored

- **WHEN** the person chooses `ґ`
- **THEN** the brief holds the answer `ґ` for the hard g, and the glossary's checks follow it

### Requirement: Offer a per-book switch for chapter summaries

The Book Brief SHALL offer a Chapter summaries switch for the book, suggested on when the analysis found one continuing story or when no analysis ran and suggested off when it found separate stories, that the person can force on or off in the Brief and in the preparation panel before Start, and that no later suggestion changes once forced.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a novel needs the story so far; a collection of independent tales is better without it. The analysis suggests, the person decides, and the switch is the same at every dial. Like every Brief field it is read-only while a translation is at work and editable while the run is paused or stopped; a change applies from the next Continue.

#### Scenario: No analysis means on

- **WHEN** no brief suggestion has been run
- **THEN** Chapter summaries is on

#### Scenario: The story shape suggests the switch

- **WHEN** the suggestion finds one story, and then in another book finds separate stories
- **THEN** the switch is suggested on in the first book and off in the second

#### Scenario: A forced value is not changed by a later suggestion

- **WHEN** the person turns the switch off and then presses "✦ Fill in from the book", which finds one story
- **THEN** the switch stays off

#### Scenario: The same switch sits in the preparation panel

- **WHEN** the person turns the switch off in the preparation panel before Start
- **THEN** the Book Brief shows it off

#### Scenario: Read-only while translating, editable while paused

- **WHEN** a translation is running, and then it is paused
- **THEN** the switch is read-only while it runs and editable once paused, and a change applies from the next Continue

### Requirement: Say how many finished chapters will be summarised when the switch is turned on

WHEN the person turns Chapter summaries on while chapters are already finished, the Book Brief SHALL show how many finished chapters will be summarised before the next passage.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: turning summaries on at chapter 13 costs one model call for each earlier chapter before the run goes on, and the person should know that before they confirm.

#### Scenario: Twelve finished chapters

- **WHEN** 12 chapters are finished and the person turns the switch on
- **THEN** the brief says "12 finished chapters will be summarised before the next passage"

#### Scenario: Nothing finished, nothing said

- **WHEN** no chapter is finished and the person turns the switch on
- **THEN** no such line is shown

#### Scenario: Turned off, nothing said

- **WHEN** the person turns the switch off
- **THEN** no such line is shown

### Requirement: Show the story shape the analysis found, with the switch it suggests

WHEN the brief suggestion has decided the story shape, the Book Brief SHALL show it as `One story` or `Separate stories`, marked likely until the person confirms or changes it, beside a line saying what the chapter-summaries switch is suggested to be, and SHALL show no story shape when no analysis ran.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#brief-synthesis`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: the shape is the reason for the suggestion, so the person sees both and can disagree with either.

#### Scenario: One story

- **WHEN** the suggestion decided one story
- **THEN** the brief shows `One story (likely)` and the line "Suggested: on — the chapters tell one story (likely)"

#### Scenario: Separate stories

- **WHEN** the suggestion decided separate stories
- **THEN** the brief shows `Separate stories (likely)` and the line "Suggested: off — separate stories (likely)"

#### Scenario: No analysis

- **WHEN** no brief suggestion has been run
- **THEN** no story shape and no suggestion line are shown

#### Scenario: The person changes the shape

- **WHEN** the person changes `One story (likely)` to `Separate stories`
- **THEN** the mark `(likely)` is gone

### Requirement: Mark every field the analysis filled as likely until the person decides

WHEN the brief suggestion fills a field of the Book Brief, the Book Brief SHALL show `(likely)` beside it until the person confirms or changes it, and a likely value SHALL feed the instructions like any other brief value.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#brief-synthesis`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: a guess that looks like a decision gets trusted; the mark keeps it a guess until the person has looked at it, yet the translation can already use it.

#### Scenario: An applied suggestion carries marks

- **WHEN** the person applies a suggestion that filled the register `Casual` and the genre `Mystery`
- **THEN** the brief shows `Casual (likely)` and `Mystery (likely)`

#### Scenario: Confirming or changing removes the mark

- **WHEN** the person confirms `Casual` or changes `Mystery` to `Thriller`
- **THEN** neither carries the mark

#### Scenario: A person's own choice has no mark

- **WHEN** the person set the audience `adult`
- **THEN** it shows no mark

#### Scenario: A likely value feeds the instructions

- **WHEN** a run is started with `Casual (likely)`
- **THEN** its requests instruct the model to use a casual register

### Requirement: Ask about a meaning-bearing name in Prepare, once, and never during a run

WHEN a reading of a name cites a window where the book explains or plays on its meaning, Prepare SHALL ask the person once, before translating, whether to transcribe the name, translate it, or transcribe it and gloss it at first use, and SHALL ask nothing about a name the book does not play on, and nothing during a run.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: a translator sounds a name out by default and asks only when the story depends on what it means; a question in the middle of a long run would stall it, and the answer would come too late for the chapters already done.

#### Scenario: The book explains a name

- **WHEN** a reading of `Brightwater` cites the window `They called the village Brightwater for the bright water of its stream.`
- **THEN** Prepare asks whether to transcribe, translate, or transcribe and gloss it at first use

#### Scenario: A name the book does not play on is not asked

- **WHEN** `Brightwater` is only a surname in every window
- **THEN** no question is asked and it is sounded out with its meaning kept as a note

#### Scenario: Back leaves the default

- **WHEN** the person closes the question without answering
- **THEN** the name is transcribed and its meaning kept as a note

#### Scenario: Never during a run

- **WHEN** a run is at chapter 12 and a new name is found there
- **THEN** no question is asked and the name is handled by the default

### Requirement: Read the narrator's gender from the source in Prepare

WHERE the source language's file lists a first-person pronoun and gendered past forms, Prepare SHALL count the narrator's first-person past forms in narration, outside quotes and dialogue, and SHALL store the narrator's gender as verified when at least 3 forms are counted and 80 % or more agree, showing the count.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: in Russian and Ukrainian the narrator's own verb says who "I" is (`я пришла`), so a book told in the first person rarely needs the question at Start. The count is the book's own evidence, so it outranks the model's guess; a language with no gendered past forms, such as English, falls through to the later steps.

#### Scenario: The count settles the gender

- **WHEN** the source language is `ru` and the narration holds 43 first-person past forms, 41 of them feminine
- **THEN** the narrator's gender is female, verified, and the Brief shows "Narrator: female (from the book, 41 of 43 forms)"

#### Scenario: A particle between the pronoun and the verb is allowed

- **WHEN** the narration says `я тоже не спал`
- **THEN** it counts as one masculine form

#### Scenario: Speech does not count

- **WHEN** the only `я пришла` of a chapter stands inside a character's quoted speech
- **THEN** it is not counted

#### Scenario: Too few or too mixed forms settle nothing

- **WHEN** the narration holds 3 forms and 2 of them are feminine
- **THEN** the gender is not stored as verified, and the model's likely answer, if any, stays shown as likely

#### Scenario: A language with no gendered past forms is not counted

- **WHEN** the source language is `en`
- **THEN** no count is made and the later steps decide

### Requirement: Re-check the decided segments when a policy key changes

WHEN the person changes the apostrophe, the quotation marks, the default address or a declared convention after segments were decided, the application SHALL re-check those segments against the new choice and SHALL show a banner naming how many segments it flagged.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: a changed key can make earlier text wrong without any new call, so the app looks again for free and tells the person how many pieces now need a look.

#### Scenario: A changed apostrophe flags the old text

- **WHEN** 40 segments are decided and 12 of them use the apostrophe `’`, and the person sets the apostrophe to `'`
- **THEN** a banner says 12 segments were flagged

#### Scenario: Nothing decided, nothing re-checked

- **WHEN** no segment is decided yet and the person changes the quotation marks
- **THEN** no banner is shown

## MODIFIED Requirements

### Requirement: Capture the translation policies

The book-brief screen SHALL offer, in its Translation policies card, a character-and-place-name policy, a foreign-language-passage policy, a footnote policy, a unit policy and a faithful ↔ natural slider from 0 to 100 labelled `literal · balanced · free`, each with the choices and the default the scenarios name.

**Source**: FR-BRIEF-03, FR-BRIEF-04, FR-BRIEF-05, FR-BRIEF-06, FR-BRIEF-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), DD-26 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: these are the decisions a human translator makes once per book — do the names stay English, get spelled in the target alphabet, or get translated; does a Latin epigraph stay Latin; are footnotes translated; do miles become kilometres; how close to the original wording should the translation stay. The name policy `Translate or adapt` lets the model use the target language's own form of a name that has one, and only when the person chooses it; names are sounded out by default for every language pair. The defaults are the safe choices for most fiction: names transliterated, foreign passages kept foreign, footnotes translated, units kept, and a balance just past the middle towards natural.

#### Scenario: A new book starts with the policy defaults

- **WHEN** a book is opened and the brief is shown
- **THEN** the name policy is `Transliterate` whatever the language pair, the foreign-passage policy is `Keep as-is`, the footnote policy is
  `Translate`, the unit policy is `Keep`, and the slider stands at `55`

#### Scenario: A policy change is kept

- **WHEN** the name policy is set to `Keep original` and the slider is moved to `20`
- **THEN** the brief holds the name policy `Keep original` and the balance `20`

#### Scenario: The slider stays within its range

- **WHEN** the slider is dragged past its left end
- **THEN** the balance is `0`, not a negative value

#### Scenario: Each policy offers these choices

- **WHEN** the Translation policies card is shown
- **THEN** the name policy offers `Translate or adapt`, `Transliterate` and `Keep original`, the foreign-passage policy `Keep as-is`, `Translate` and `Translate + note`, the footnote policy `Translate` and `Keep`, and the unit policy `Keep` and `Metric`

#### Scenario: Transliterate is the default for every pair

- **WHEN** a book is opened for `en` → `de`, and another for `ru` → `uk`
- **THEN** both briefs hold the name policy `Transliterate`

#### Scenario: What Translate or adapt means is said on hover

- **WHEN** the person hovers `Translate or adapt`
- **THEN** the explanation says the model may use the target language's own form of a name that has one, and that nothing is adapted under the other two choices

### Requirement: Choose the quality dial and show what it turns on

The book-brief screen SHALL offer, in its Quality vs speed card, a dial of `Fast`, `Balanced` or `Max`, defaulting to `Balanced`, with a one-line hint naming what the position does about fixing, proofreading and extra calls and the line "The same for every model.", and a model row naming the provider and the model, with a `change` link that opens Settings and no readiness badge. It SHALL NOT offer a review-mode choice; that is decided when the application is launched.

**Source**: FR-BRIEF-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), DD-45 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-45-acceptance-model`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, ADR-0036 (`docs/adr/ADR-0036-review-modes-pause-the-run.md`), `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: the dial trades speed for care, and a person can only make that trade knowingly if the screen says what each position costs — how many fixing calls a piece gets, which passages are re-read, and how many extra calls that costs; the same for every model, so the person never has to wonder whether the dial means less on a small model. The model row is shown because the model is the other half of the quality question, and the link keeps the one place it is changed in Settings, so this row cannot drift from that choice. It carries no readiness badge because that would mean keeping a verification result between checks, which this build does not do; `app-shell`'s "Match the reference rendering" states this as a deviation from the reference rendering, alongside the sidebar's own readiness dot. The review mode — when the run stops for the person — is a preference about how they like to work, not a property of the book, so it does not belong here.

#### Scenario: Balanced is the default and says what it does

- **WHEN** a book is opened and the brief is shown
- **THEN** the dial is at `Balanced` and the hint reads
  `Balanced: fixing calls — one repair per piece · proofread — marked and doubtful windows · extra calls — about one call per marked passage`
- **AND** the line below reads `The same for every model.`

#### Scenario: The hint follows the dial

- **WHEN** the dial is moved to `Max`
- **THEN** the hint reads `Max: fixing calls — one repair per piece · proofread — marked and doubtful windows, then the whole book re-read · extra calls — about one call per marked passage, plus one call per 4 paragraphs of the whole book`

#### Scenario: The model row names the configured model and links to Settings

- **WHEN** the configured provider is Ollama with the model `gemma4:26b` and the brief is shown
- **THEN** the model row reads `gemma4:26b · Ollama` followed by a `change` link
- **AND** pressing `change` opens the Settings screen
- **AND** the row carries no readiness badge

#### Scenario: No review-mode control is on the brief

- **WHEN** the brief is shown
- **THEN** no control offers `Automatic`, `Assisted` or `Manual`

#### Scenario: The hint for Fast

- **WHEN** the dial is moved to `Fast`
- **THEN** the hint reads `Fast: fixing calls — none, marks for review (lost formatting marks retried once) · proofread — none · extra calls — no extra calls`

### Requirement: Carry every brief choice into the run

WHEN a run is started, the system SHALL build the instructions sent to the model from the brief as it then stands, once for the book, and SHALL send them with every translation request of the run, with a short reminder. WHILE an "Also translate" switch is off, the run SHALL NOT translate that kind of auxiliary text, and the exported book SHALL keep it as in the source language. WHILE a book stays open, its brief choices SHALL be kept when the person moves to another screen and back.

**Source**: FR-BRIEF-01..09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-11 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, DD-47 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: until now every control on this screen except the target language was drawn and ignored. A brief that does not reach the model is decoration, so this is the requirement that makes the screen mean something: what the person set is what the model is told, on every call, and a switch that says "do not translate the table of contents" leaves the table of contents alone.

#### Scenario: Register and name policy reach the model

- **WHEN** a run is started from a brief with register `Formal / literary` and name policy `Keep original`
- **THEN** every translation request of that run instructs the model to use a formal, literary register and to keep
  character and place names in their original spelling

#### Scenario: A switched-off kind stays in the source language

- **WHEN** a book with an image whose alt text is `A ship at sea` is translated with `Image alt-text` switched off
  and then exported
- **THEN** no translation request carries `A ship at sea`
- **AND** the exported image's alt text is still `A ship at sea`

#### Scenario: A switched-on kind is translated

- **WHEN** the same book is translated with `Book metadata (title/author)` on
- **THEN** the book's title is sent for translation and the exported book declares the translated title

#### Scenario: Choices survive a trip to another screen

- **WHEN** the register is set to `Casual`, the person opens Structure and then returns to the brief
- **THEN** the register is still `Casual`

#### Scenario: Every brief choice is in the instructions

- **WHEN** a run is started
- **THEN** the run is built from the source and target languages, genre, register, narrative voice / era, audience, the four policies, the faithful ↔ natural balance, the quality dial, the policy keys (apostrophe, quotation marks, default address), the declared conventions and the chapter-summaries switch

#### Scenario: The instructions are built once and a reminder goes with each call

- **WHEN** a run of three translation requests is started
- **THEN** the three requests carry the same instructions text, and each also carries a reminder of at most three lines

#### Scenario: A policy key reaches the model

- **WHEN** a run is started for a Ukrainian target with the apostrophe `’`, the quotation marks `«»` and strangers addressed as `Ви`
- **THEN** every translation request instructs the model to use those three

### Requirement: Suggest the narrator from the source and ask once at Start translation

WHEN a book is opened, the application SHALL read its narration chapter by chapter, outside quotes and dialogue, and keep a narrator hint (its person and the chapters judged), leaving the gender to "Read the narrator's gender from the source in Prepare". WHILE the hint is `FIRST`, or `MIXED` with a quarter or more of its chapters first person, and the gender is not chosen and the narrator is not third person, the Tone & style card SHALL show a notice and preselect `First person`.

**Source**: FR-BRIEF-02, ADR-0038; tasks 15e.14, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: a first-person book told by a man and translated with no narrator gender came out with feminine verbs (`я була`), because nothing told the model or the checks who "I" is. The source shows that the book says "I", and where its language has gendered past forms Prepare also reads the gender from them, but where it does not so the application notices the first, shows a note, and asks the second question once, at the moment it matters, without ever blocking the start.

#### Scenario: A first-person book shows the notice and preselects the person

- **WHEN** a book whose narration says "I", in a source language with no gendered past forms, is opened and its brief is shown
- **THEN** the notice is shown, the narrator choice is `First person`, the gender choice is `Not stated` and the brief's
  gender is unchanged

#### Scenario: A third-person book with "I" only in quotes shows nothing

- **WHEN** the only "I" of the book is inside quotation marks and the brief is shown
- **THEN** no notice is shown and the narrator is `Not stated`

#### Scenario: A chapter is first person by its pronouns

- **WHEN** a chapter has at least six narration sentences and at least a tenth of its narration sentences contain the source language's `firstPersonPronouns` (`I`, `я`, `ich`, `je`)
- **THEN** the chapter is first person, the hint also holds the share of such sentences and the first-person chapters, and the book is `FIRST` when every judged chapter is, `THIRD` when none is and `MIXED` otherwise

#### Scenario: A language that drops its subject gets no hint

- **WHEN** the source language lists no `firstPersonPronouns`
- **THEN** no hint is made

#### Scenario: The notice is worded and explained in both languages

- **WHEN** the notice is shown
- **THEN** it reads `The book is narrated in the first person — choose the narrator's gender.` with a hover explanation, in English and Ukrainian

#### Scenario: A mixed book with few first-person chapters is third person

- **WHEN** the hint is `MIXED` with fewer than a quarter of its judged chapters first person, such as a preface or a letter in a third-person novel
- **THEN** no notice is shown, nothing is preselected and no question is asked

#### Scenario: The hint is read once, at import

- **WHEN** another source language is chosen in the brief after import, or the preselected language has no pronoun data (`UNRECOGNIZED`)
- **THEN** the notice is not recomputed in the first case and none is shown in the second
- **AND** the hint is read again in the run's preparation with the brief's language (known limitation of the 2026-10-07 review: the screens would need a port that re-reads the book's text, which is not worth it while the person can choose the narrator by hand)

#### Scenario: A source with gendered past forms may settle the gender

- **WHEN** a Russian first-person book is opened and Prepare has read the narrator as female from the book
- **THEN** the narrator choice is `First person`, the gender choice is `Female` and no notice is shown

### Requirement: Ask who narrates once at Start translation

WHEN Start translation is pressed under the notice's conditions and the question was not answered for the book in this session, the application SHALL ask once `Who narrates?` with `Male`, `Female`, `Not stated — start anyway` and `Back`, a likely answer already chosen. It SHALL write a chosen gender to the brief as a first-person narrator and start the run; Enter on a likely answer starts with it kept likely, not confirmed; `Back` or Escape starts nothing and changes nothing.

**Source**: FR-BRIEF-02, ADR-0038; tasks 15e.14, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: a first-person book told by a man and translated with no narrator gender came out with feminine verbs (`я була`), because nothing told the model or the checks who "I" is. The source shows that the book says "I" but not the gender, so the application notices the first, shows a note, and asks the second question once, at the moment it matters, without ever blocking the start.

#### Scenario: The answer reaches the brief and the run

- **WHEN** Start translation is pressed on a first-person book with no narrator gender and `Male` is chosen
- **THEN** the brief holds a first-person male narrator, the run starts and every call's style sheet carries the male
  narrator line

#### Scenario: Going back starts nothing

- **WHEN** the question is shown and Escape is pressed
- **THEN** no run starts, the brief is unchanged and the next Start asks again

#### Scenario: Any answer but Back is remembered

- **WHEN** the question was answered with `Male` or `Not stated — start anyway`
- **THEN** the question is not asked again for the book

#### Scenario: Enter keeps the likely answer

- **WHEN** the brief holds a first-person narrator, female, likely, and Start translation is pressed and Enter is pressed on the chosen `Female (likely)`
- **THEN** the run starts and the narrator stays female, likely, not confirmed

#### Scenario: Choosing the button confirms it

- **WHEN** the same question is answered by pressing the `Female` button
- **THEN** the brief holds a first-person female narrator as the person's choice, and the question is not asked again for the book

### Requirement: Suggest the tone and style from verified readings, one decision per field

WHEN the person presses "✦ Fill in from the book", the application SHALL ask what the model knows of the book, read several samples with one `brief-suggestion` call each, ask one `brief-synthesis` call over the facts and the samples' verified readings, and decide each field once, answering also the story shape from one quote in each of two samples; a failed sample counts as no reading, and a stop leaves the brief unchanged.

**Source**: FR-BRIEF-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`); tasks 15h.G1, 15i.B2–B4 (`openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md`); `docs/specification/01_Product/12_PROMPT_CATALOG.md#brief-synthesis`; docs/specification/mockups/README.md#dialog--fill-in-the-brief-from-the-book-g4-short--b1b3, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: one sample can be an odd stretch of a book and a model's memory can be wrong, so a field is kept only when most samples or checked knowledge say it.

#### Scenario: Three of four windows decide the register

- **WHEN** three of four windows answer `casual` with quotes found in them and the fourth answers `formal`
- **THEN** the suggested register is `Casual`, shown as "3 of 4 samples agree"

#### Scenario: A split register stays neutral and uncertain

- **WHEN** two windows answer `casual` and two answer `formal`
- **THEN** the suggested register is the synthesis's most probable value, `Neutral`, shown as `Neutral (likely)` and not as agreed

#### Scenario: One window alone does not decide the genre

- **WHEN** the synthesis names `Mystery` citing one of four windows and the model does not know the book
- **THEN** the genre is `Mystery`, shown as `Mystery (likely)` and not as agreed

#### Scenario: A cited window that read another genre is no support

- **WHEN** the synthesis names `Thriller` citing two windows that read `fantasy` and `romance`
- **THEN** the genre is `Thriller`, shown as `Thriller (likely)` with no basis named

#### Scenario: One failed window does not fail the suggestion

- **WHEN** one of four window calls fails and the other three answer
- **THEN** the suggestion is made from the three, the failed window counting against every majority

#### Scenario: A stop during the synthesis changes nothing

- **WHEN** the person stops the suggestion while the synthesis call is running
- **THEN** the suggestion ends as cancelled and the brief is unchanged

#### Scenario: Knowledge that agrees keeps the audience

- **WHEN** the model's accepted knowledge says `adult readers` and the synthesis names the audience `adult` on that
  knowledge
- **THEN** the audience is `adult`, shown as from what the model knows of this book

#### Scenario: The detector's person outweighs the windows

- **WHEN** the detector reads the book as third person and every window answers `first`
- **THEN** the suggested narrator is third person

#### Scenario: Under Auto the windows' language is only proposed

- **WHEN** the source language is Auto and three of four windows say the passage is French
- **THEN** the suggestion proposes French and the brief's source language stays Auto

#### Scenario: Knowledge is accepted only by its echo check

- **WHEN** the model answers what it knows of the book
- **THEN** the answer is accepted only when the title (also by its part before `;` or `:`) and the author (in either name order) match the metadata, and the year is no later than five after a metadata year, because that year is often an edition's

#### Scenario: The number of windows follows the dial

- **WHEN** the suggestion is made on Fast, Balanced or Max for a story long enough
- **THEN** 3, 4 or 5 single windows of the story are read, of at most 1,500, 2,500 or 4,000 tokens each

#### Scenario: A short story gets fewer windows

- **WHEN** the suggestion is made on Balanced for a story of 6,000 tokens
- **THEN** 2 windows are read, because 6,000 tokens hold only two windows of 2,500 tokens; a book with any story text gets at least 1

#### Scenario: An answer is kept only with a quote found in its window

- **WHEN** a window's answer is read
- **THEN** it is kept only with a quote found in its window: a register's quote in the window's narration, outside the characters' speech, and a genre or an audience never without a quote

#### Scenario: No synthesis for an unknown book that no window read

- **WHEN** the book is unknown to the model and no window read genre, audience or voice
- **THEN** no `brief-synthesis` call is made, because nothing could stand

#### Scenario: The suggestion fails only when stopped or when every window fails

- **WHEN** the person stopped it, or every window call failed
- **THEN** the suggestion fails, whereas a failed knowledge question or synthesis only leaves its fields uncertain

#### Scenario: A stop during the knowledge question changes nothing

- **WHEN** the person stops the suggestion while the knowledge question is running
- **THEN** the suggestion ends as cancelled and the brief is unchanged

#### Scenario: The story shape is decided from two windows

- **WHEN** the synthesis answers one story, with a quote from sample 1 (`Mara reached the pass at dusk, as she had sworn in chapter one.`) and a quote from sample 3 (`Three days after the pass, Mara still carried the key.`)
- **THEN** the story shape is `One story`, shown as `(likely)`, with both quotes

#### Scenario: A collection is separate stories

- **WHEN** the synthesis answers separate stories, with a quote from sample 1 that opens one tale and a quote from sample 2 that opens another with new characters
- **THEN** the story shape is `Separate stories`, shown as `(likely)`

#### Scenario: No field is left empty

- **WHEN** the windows give no clear answer for the audience
- **THEN** the audience is the synthesis's most probable value, for example `general`, shown as `general (likely)`

### Requirement: Mark a suggested field likely unless its basis holds

The register SHALL be kept by a strict majority of the windows read, and the narrator's person SHALL be the detector's when it answers, else a strict majority of the windows. The genre, audience and voice / era SHALL stand unmarked only when the synthesis's basis holds; any other field SHALL take the model's most probable value, shown as `(likely)` until the person confirms or changes it. WHILE the source language is Auto, the language most windows name SHALL be a proposal only.

**Source**: FR-BRIEF-02; tasks 15h.G1, 15i.B2–B4; `docs/specification/01_Product/12_PROMPT_CATALOG.md#brief-synthesis`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: one window can be an odd stretch of a book, and a model's memory of a famous title can be wrong, so a field is shown without a mark only when most of the book's passages say it, or when the model's checked knowledge of the book says the same as the passages; otherwise the field shows the model's most probable value marked likely, and the person confirms or changes it. No field is left empty.

#### Scenario: A window with no verified answer counts against every register

- **WHEN** a window has no verified answer, or its call failed
- **THEN** it counts against every value of the register majority

#### Scenario: A mixed book is first person when a quarter of its chapters are

- **WHEN** the detector reads a mixed book with at least a quarter of its judged chapters told as "I" (alternating narrators, or three chapters of eight)
- **THEN** the narrator is first person, and otherwise third person (a preface, a letter or a character's account inside a third-person novel)

#### Scenario: The genre, audience and voice come from the synthesis

- **WHEN** the synthesis answers
- **THEN** the genre is one of the 40 listed names, the audience is `adult`, `young adult`, `children`, `general` or `academic`, and the voice / era has at most 12 words

#### Scenario: A field stands on one of three bases

- **WHEN** the synthesis cites support for the genre, the audience or the voice / era
- **THEN** it stands only on two cited windows that each had a verified reading of the field, or on accepted knowledge whose own field agrees with the value, or on one cited window when that knowledge agrees or the one window holds every line of the story (a story of fifteen hundred tokens read in a window of a thousand is not that)

#### Scenario: A cited window counts once and only for what it read

- **WHEN** a window is cited for a value
- **THEN** it counts once, and only when it read the field and its reading says the same as the value (a window that read `fantasy` does not support `Thriller`)

#### Scenario: Each field says what it stands on

- **WHEN** a field is kept
- **THEN** it names its basis before its value and says what it stands on (`from 3 of 4 passages`, `from what the model knows of this book`, the detector's count), and a likely field says `the model's best guess`

#### Scenario: No majority gives a likely register

- **WHEN** no register has a strict majority of the windows
- **THEN** the register is the synthesis's most probable value, shown as `(likely)`

#### Scenario: A field with no basis is a likely value

- **WHEN** the synthesis names the voice / era `Victorian` and cites no window and no accepted knowledge
- **THEN** the voice / era is `Victorian`, shown as `Victorian (likely)`, and a field that stands on a basis carries no mark

### Requirement: Show the brief suggestion in a progress dialog that is not hideable

The brief suggestion SHALL run in a progress dialog whose Steps group lists what the model knows about the book (not listed when the title is the file name), reading sample 1 to K (K = 3, 4 or 5 for Fast, Balanced or Max, fewer when the story is too short for K windows), and deciding genre, readers, voice and story shape; Esc SHALL cancel it and it SHALL NOT be hideable.

**Source**: docs/specification/mockups/README.md#dialog--fill-in-the-brief-from-the-book-g4-short--b1b3, docs/specification/mockups/README.md#g4--the-progress-dialog-one-pattern-for-model-work-e1, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (B1–B3), `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: the suggestion is short work, so the person watches its steps and can stop it, but it is not pushed into the background.

#### Scenario: The steps follow the dial and skip unknown knowledge

- **WHEN** the person presses "✦ Fill in from the book" on Balanced for a book whose title is only its file name
- **THEN** the dialog lists no "what the model knows" step, then Read sample 1 to Read sample 4 with their location ranges, then Decide genre, readers, voice and story shape

#### Scenario: A known title adds the knowledge step

- **WHEN** the person presses "✦ Fill in from the book" on Balanced for a book with a known title
- **THEN** the dialog lists what the model knows about the book, Read sample 1 to Read sample 4 and Decide genre, readers, voice and story shape (1 + 4 + 1 steps)

#### Scenario: Esc cancels the suggestion

- **WHEN** the person presses Esc in the dialog before it has ended
- **THEN** the suggestion is cancelled and the brief is unchanged

### Requirement: Show what the suggestion decides with its agreement and verified quote

The dialog's "What it decides" group SHALL list five rows, genre, register, who tells the story, who reads it with the voice, and the story shape, each with its value so far and its agreement ("3 of 4 samples agree"); selecting one SHALL show each sample's answer with its verified quote, and a value without support SHALL be shown as likely.

**Source**: docs/specification/mockups/README.md#dialog--fill-in-the-brief-from-the-book-g4-short--b1b3, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (B2, B3), `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: the person can see why each field has its value and read the sentence of the book that supports it.

#### Scenario: A field shows its agreement and quote

- **WHEN** on Balanced three of four samples answer `formal` for the register, each with a quote found in its sample
- **THEN** "What it decides" shows Register `Formal` with "3 of 4 samples agree", and selecting it shows each sample's answer with its quote

#### Scenario: The story shape shows its two quotes

- **WHEN** the story shape is `One story` from sample 1 and sample 3
- **THEN** the row reads `One story` with "2 of 2 samples agree", and selecting it shows both quotes

#### Scenario: A value without support is shown as likely

- **WHEN** the genre has no cited window and no accepted knowledge
- **THEN** its row shows the value with `(likely)` and no agreement count

## REMOVED Requirements

### Requirement: Suggest the narrator's gender only from evidence the code verifies

**Reason**: The model's most probable narrator gender is now always kept, shown as likely, instead of being thrown away when the code cannot verify it; the verifying rules stay and decide when a quote makes the gender the book's.

**Migration**: Replaced by "Keep the narrator's most probable gender as likely and verify it only by evidence the code checks", which keeps every verifying rule.

### Requirement: State a time estimate on the brief only when it is measured

**Reason**: The card now also shows a labelled estimate range scaled from a measured draft speed, and no longer shows a relative cost ("Balanced costs about 1.5 times Fast") when nothing is measured; the old scenarios said the opposite.

**Migration**: Replaced by "State a time estimate on the brief as measured or as an estimate", which also takes over the app-shell capability's removed "Estimate before Start only from a measured speed".

## RENAMED Requirements

- FROM: `### Requirement: Keep a suggested field only when its basis holds`
- TO: `### Requirement: Mark a suggested field likely unless its basis holds`
