## ADDED Requirements

### Requirement: Show the same Book Brief rows for every target language

The Book Brief SHALL show the same cards, rows and choices whatever the source and target languages are, and SHALL show no row that exists for some target languages only. The apostrophe, the quotation marks, the form of address between strangers and in a family, and the spelling of a hard g SHALL follow the target language's own rules and SHALL NOT be asked.

**Source**: `research/review-digest.md#owner-decisions`, `design.md#d26--the-book-brief-has-no-language-specific-rows`. In plain words: the owner decided that the window is the same for every language. Five rows appeared only for Ukrainian and Russian targets; a person translating into German saw a different screen. The language's own rules already hold a tested default for each of the five, and a brief nobody touched has always used exactly those.

#### Scenario: Ukrainian and German show the same rows

- **WHEN** the Book Brief is shown for the target `uk`, and then for the target `de`
- **THEN** "How to translate" holds the same rows both times — names of people and places, text in other languages, footnotes, units and the close ↔ natural slider — and no row named Apostrophe, Quotation marks, Address between strangers, Address in a family or Hard g in foreign names

#### Scenario: The defaults still reach the book

- **WHEN** a book is translated into `uk` and the model writes `Він сказав: "Не пам'ятаю..."`
- **THEN** the stored translation is `Він сказав: «Не пам’ятаю…»`, with the Ukrainian apostrophe and quotation marks, although the brief holds no such choice

#### Scenario: A model suggestion proposes none of them

- **WHEN** "Fill in from the book" is used for a `uk` target
- **THEN** the dialog lists no decision about the apostrophe, the quotation marks, the address or the hard g

### Requirement: Offer the last used target language for a new book

WHEN a book is imported and a target language was used before, the application SHALL set the new brief's target language to the one last used, unless it equals the book's source language, in which case the target SHALL stay empty.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `research/review-digest.md#f16--defaults-work-against-the-goal`, `design.md#d21--defaults-instead-of-questions`. In plain words: the target language was the one decision the shortest path forced on the person for every book. Most people translate into one language, so the second book starts with it filled in; the very first book still asks once, when Start is pressed, in the dialog "Translate into which language?" of the `app-shell` capability, and that answer is the last used target from then on.

#### Scenario: The second book has its target filled in

- **WHEN** the last translation started into `uk` and an English book is imported
- **THEN** the new brief reads English → Ukrainian

#### Scenario: A book already in the last target stays empty

- **WHEN** the last target was `uk` and a Ukrainian book is imported
- **THEN** the new brief's target language is empty

#### Scenario: The first book has no target yet

- **WHEN** no translation was ever started and a book is imported
- **THEN** the new brief's target language is empty

### Requirement: Say that the quality is chosen by the model until the person chooses one

WHILE a brief's quality is marked "by the model", the Book Brief SHALL show under the quality control the line "Chosen for the AI model when the translation starts: Fast on a small model, Balanced otherwise.", and WHEN the person chooses a quality the mark SHALL be removed and the line no longer shown.

**Source**: `research/review-digest.md#f16--defaults-work-against-the-goal`, `design.md#d21--defaults-instead-of-questions`. In plain words: the app picks the quality from the model's size at the first start (the translation-pipeline capability's rule), so the screen must not pretend the person chose Balanced. The person's own choice always wins and is never changed by the app.

#### Scenario: A new brief says the quality is chosen by the model

- **WHEN** a book is imported while Settings › Automation says "By the AI model"
- **THEN** the Book Brief shows the line under the quality control

#### Scenario: The person's choice removes the mark

- **WHEN** the person chooses Max
- **THEN** the line is gone, and a run on a small model starts at Max

#### Scenario: After the first start the control shows what was chosen

- **WHEN** the first run started on `gemma4:e4b`
- **THEN** the control shows Fast and the line reads "Chosen for the AI model: Fast on a small model."

### Requirement: Fill the brief from the run's own suggestion

WHEN a run's Prepare stage has written a brief suggestion, the application SHALL have written it by the same rule that writes a suggestion applied from the Book Brief's own button, and the Book Brief of the open window SHALL show each field the suggestion filled with the "likely" mark and its evidence once the run has left its Prepare stage; a later edit on the Book Brief SHALL keep every field the run wrote that the edit does not change.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: the suggestion now also arrives without a button press. One rule writes it in both cases, so a field the model left empty keeps the person's value, an uncertain register is not applied, and every field the model filled carries the likely mark. The window holds its own copy of the brief, so it takes the stored brief when the run leaves Prepare and again when the run ends; without that, the first edit after the run would save the old copy over what the run wrote. The person who opens the Book Brief sees which values the model put there and why, and can change any of them for the paragraphs not translated yet.

#### Scenario: The fields a run filled are marked

- **WHEN** a run's Prepare stage decided an informal register and a first-person narrator for a brief nobody had touched, and the run has reached its Translate stage
- **THEN** the Book Brief shows `Informal` and `First person`, each with the "likely" mark and its evidence line, read-only while the translation is at work

#### Scenario: A later edit keeps what the run wrote

- **WHEN** that run has ended and the person sets the audience to `Young adults` on the Book Brief
- **THEN** the stored brief holds the audience `Young adults`, the informal register and the first-person narrator, the two still marked likely

#### Scenario: A field the model left empty keeps the brief's value

- **WHEN** the run's suggestion decides the register and leaves the genre empty, and the brief held no genre
- **THEN** the brief still holds no genre and the genre carries no "likely" mark

### Requirement: Suggest the tone and style from verified readings, one decision for each field

WHEN the brief suggestion runs, from "✦ Fill in from the book" or in a run's Prepare stage, the application SHALL ask what the model knows of the book, read several samples with one `brief-suggestion` call each, ask one `brief-synthesis` call over the facts and the samples' verified readings, and decide each field once; a failed sample counts as no reading, and a stop leaves the brief unchanged.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: one sample can be an odd stretch of a book and a model's memory can be wrong, so a field is kept only when most samples or checked knowledge say it. The story shape is no longer asked: it only chose the default of the removed chapter-summaries switch.

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

#### Scenario: No field is left empty

- **WHEN** the windows give no clear answer for the audience
- **THEN** the audience is the synthesis's most probable value, for example `general`, shown as `general (likely)`

## MODIFIED Requirements

### Requirement: Carry every brief choice into the run

WHEN a run is started, the system SHALL build the instructions sent to the model from the brief as it then stands, once for the book, and SHALL send them with every translation request of the run, with a short reminder. WHILE an "Also translate" switch is off, the run SHALL NOT translate that kind of auxiliary text, and the exported book SHALL keep it as in the source language. WHILE a book stays open, its brief choices SHALL be kept when the person moves to another screen and back.

**Source**: `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`, `design.md#d26--the-book-brief-has-no-language-specific-rows`, `research/review-digest.md#owner-decisions`. In plain words: until now every control on this screen except the target language was drawn and ignored. A brief that does not reach the model is decoration, so this is the requirement that makes the screen mean something: what the person set is what the model is told, on every call, and a switch that says "do not translate the table of contents" leaves the table of contents alone.

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
- **THEN** the run is built from the source and target languages, genre, register, narrative voice / era, audience, the four policies, the faithful ↔ natural balance, and the quality dial; the apostrophe, the quotation marks, the form of address and a spelling convention come from the target language's own rules and are no brief choice

#### Scenario: The instructions are built once and a reminder goes with each call

- **WHEN** a run of three translation requests is started
- **THEN** the three requests carry the same instructions text, and each also carries a reminder of at most two lines

#### Scenario: A policy key reaches the model

- **WHEN** a run is started for a Ukrainian target
- **THEN** every translation request carries the Ukrainian rules for the apostrophe, the quotation marks and ти or ви from the target language's own rules, and the brief holds no such choice

### Requirement: Show the brief suggestion in a progress dialog that is not hideable

The brief suggestion SHALL run in a progress dialog whose Steps group lists what the model knows about the book (not listed when the title is the file name), reading sample 1 to K (K = 3, 4 or 5 for Fast, Balanced or Max, fewer when the story is too short for K windows), and deciding genre, readers and voice; Esc SHALL cancel it and it SHALL NOT be hideable.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: the suggestion is short work, so the person watches its steps and can stop it, but it is not pushed into the background. The last step no longer decides a story shape.

#### Scenario: The steps follow the dial and skip unknown knowledge

- **WHEN** the person presses "✦ Fill in from the book" on Balanced for a book whose title is only its file name
- **THEN** the dialog lists no "what the model knows" step, then Read sample 1 to Read sample 4 with their location ranges, then Decide genre, readers and voice

#### Scenario: A known title adds the knowledge step

- **WHEN** the person presses "✦ Fill in from the book" on Balanced for a book with a known title
- **THEN** the dialog lists what the model knows about the book, Read sample 1 to Read sample 4 and Decide genre, readers and voice (1 + 4 + 1 steps)

#### Scenario: Esc cancels the suggestion

- **WHEN** the person presses Esc in the dialog before it has ended
- **THEN** the suggestion is cancelled and the brief is unchanged

### Requirement: Show what the suggestion decides with its agreement and verified quote

The dialog's "What it decides" group SHALL list four rows, genre, register, who tells the story, and who reads it with the voice, each with its value so far and its agreement ("3 of 4 samples agree"); selecting one SHALL show each sample's answer with its verified quote, and a value without support SHALL be shown as likely.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: the person can see why each field has its value and read the sentence of the book that supports it.

#### Scenario: A field shows its agreement and quote

- **WHEN** on Balanced three of four samples answer `formal` for the register, each with a quote found in its sample
- **THEN** "What it decides" shows Register `Formal` with "3 of 4 samples agree", and selecting it shows each sample's answer with its quote

#### Scenario: The story shape shows its two quotes

- **WHEN** the "What it decides" group is shown after the last step
- **THEN** it lists four rows and no row for a story shape

#### Scenario: A value without support is shown as likely

- **WHEN** the genre has no cited window and no accepted knowledge
- **THEN** its row shows the value with `(likely)` and no agreement count

### Requirement: Suggest the narrator from the source

WHEN a book is opened, the application SHALL read its narration chapter by chapter, outside quotes and dialogue, and keep a narrator hint (its person and the chapters judged), leaving the gender to "Read the narrator's gender from the source in Prepare". WHILE the hint is `FIRST`, or `MIXED` with a quarter or more of its chapters first person, and the gender is not chosen and the narrator is not third person, the Tone & style card SHALL show a notice and preselect `First person`.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d21--defaults-instead-of-questions`. In plain words: a first-person book told by a man and translated with no narrator gender came out with feminine verbs (`я була`), because nothing told the model or the checks who "I" is. The source shows that the book says "I", and where its language has gendered past forms Prepare also reads the gender from them, but where it does not the application notices the first and shows a note; it asks no question at Start, because "not stated" is a complete answer, and a gender that is only likely is held to no verb form.

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

### Requirement: Check the narrator's pending quotes again after the names

`SetupAssistant.recheckNarrator(projectId)` SHALL make no model call and write nothing, and for a first-person narrator of unknown gender SHALL return the narrator with that gender and the origin `VERIFIED` when a name in a pending quote is a glossary character of confirmed gender; otherwise it SHALL return it unchanged. WHEN the names step of a run's Prepare stage ends, or a names pass started from Names & style ends, the application SHALL run the re-check and, when it verified a gender, store it.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`, `design.md#d21--defaults-instead-of-questions`. In plain words: the model may point at "Hold on tight, Brindle" before anybody knows Brindle is a woman; once the names pass or the person has said so, the same quote proves the narrator's gender, and nobody is asked what the book already says. The re-check now runs when the names are ready, inside the run, and nobody is asked anything.

#### Scenario: A pending vocative is verified by the names pass

- **WHEN** the brief's narrator is first person with the pending quote `Hold on tight, Brindle`, the glossary now holds
  `Brindle` as a female character the person set, and the names step of the run's Prepare stage ends
- **THEN** the narrator is stored as female with the origin `VERIFIED` before the first draft call is sent

#### Scenario: A suggestion is no proof

- **WHEN** the glossary holds `Brindle` as female only suggested
- **THEN** the re-check returns the narrator unchanged and the narrator's gender stays not stated

#### Scenario: A name the narrator says to someone else is no proof

- **WHEN** the pending quote is `Mind the step, Brindle` and the book has `"Mind the step, Brindle," I said`
- **THEN** the re-check returns the narrator unchanged

#### Scenario: Confirmed means the person's or verified

- **WHEN** a pending quote's name is a glossary character whose gender is the person's or verified
- **THEN** the gender counts as confirmed, and one that is only a suggestion does not

#### Scenario: A verified narrator keeps the proving quotes

- **WHEN** the re-check verifies a gender
- **THEN** the returned narrator keeps the proving quotes
- **AND** a quote the narrator says to someone else verifies nothing

#### Scenario: Other narrators are returned as they are

- **WHEN** the narrator's gender is set, inferred or suggested, or the narrator is third person
- **THEN** the re-check returns the narrator as it is

#### Scenario: An unknown project answers a validation error

- **WHEN** the project is not stored or its book is not open
- **THEN** the re-check answers a `validation` error

#### Scenario: A failed or empty re-check asks as before

- **WHEN** the re-check fails or verifies nothing
- **THEN** the narrator's gender stays not stated, the run goes on, and no question is asked

#### Scenario: A book Start would not ask about is not re-checked

- **WHEN** the narrator is third person or already has a gender
- **THEN** the re-check changes nothing

### Requirement: Judge the narration when the run starts when the import could not

WHEN Start translation is pressed and the open book's narration was not judged when it was opened (it declares no language and its source was chosen afterwards), the application SHALL first judge it from the text with no model call and no write — in the brief's source language, or under Auto in every language whose first-person pronoun it knows, never showing one — and SHALL use that judgement for the run's brief step and its narrator line; it SHALL ask no question.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d21--defaults-instead-of-questions`. In plain words: a first-person Markdown or text book was judged only by the run, after the Start question, so the run's own reading of the narrator came too late for its first calls; the judgement is now made when the run starts, and no question follows.

#### Scenario: A first-person Markdown book is asked about

- **WHEN** `Harbour.md`, told as "I" with `Anna` named 9 times and no gender in the glossary, is opened, its source set to English and its target to Ukrainian, and Start translation is pressed
- **THEN** the run starts with no question, the narration is judged first person before the brief step, and neither the narrator nor `Anna` is given a gender nobody stated

#### Scenario: A first-person Markdown book with an Auto source is asked about

- **WHEN** `Harbour.md`, told as "I" and declaring no language, is opened in the window over its SQLite store, its source left at `Find out from the text` and its target set to Ukrainian, and Start translation is pressed
- **THEN** the narration reads as first person with the English pronoun `I`, and the run starts with no question

### Requirement: Keep the narrator's most probable gender as likely and verify it only by evidence the code checks

WHEN the model's style suggestion answers the narrator's gender, the application SHALL set the gender from the book, shown as `Male, from the book` or `Female, from the book` with the quote, when the code verifies the quote it cites, and otherwise keep the answer as a likely gender, shown as `Male (likely)` or `Female (likely)`; a gender the person, the book's count or the run's count set is never replaced, and a third-person narrator gets none.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d21--defaults-instead-of-questions`. In plain words: the model was free to say "male" from nothing, and its answer was then thrown away for a book whose narrator was already set to first person. Now the model always gives its best answer. When it points at a line the code can check — a name it can look up or a word like "ma'am" — the line is the book's own evidence, so the gender is the book's; with nothing checkable it stays likely, so nobody takes it for a fact. No check holds the text to a likely gender: the translation-pipeline capability's "Tell the model who narrates and who is present" gives it to every call as `likely male` or `likely female`, which orders no verb form. The person's choice and the book's own forms outrank it. Nothing about the likely gender changes here; only the question at Start is gone, so the likely value is no longer offered there for confirmation.

#### Scenario: A vocative of a glossary person genders the narrator

- **WHEN** the glossary holds the character `Tobin` as male, the narrator is first person with no gender, and the model quotes `"Keep up, Tobin," Mara called back to me.` from the sample with the name `Tobin`
- **THEN** the narrator's gender is male and shown as `Male, from the book` with the quote

#### Scenario: A gender with nothing the code can check is still kept as likely

- **WHEN** the model answers `female` with a quote that holds neither a known name nor a word of address
- **THEN** the narrator's gender is female, shown as `Female (likely)` with no quote
- **AND** Start translation asks nothing; the person may change the gender under "Who narrates?" on the Book Brief

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

### Requirement: Read the narrator's gender from the source in Prepare

WHERE the source language's file lists a first-person pronoun and gendered past forms, Prepare SHALL count the narrator's first-person past forms in narration, outside quotes and dialogue, and SHALL store the narrator's gender as verified when at least 3 forms are counted and 80 % or more agree, showing the count.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d21--defaults-instead-of-questions`. In plain words: in Russian and Ukrainian the narrator's own verb says who "I" is (`я пришла`), so a book told in the first person usually has its narrator's gender without anybody being asked. The count is the book's own evidence, so it outranks the model's guess; a language with no gendered past forms, such as English, falls through to the later steps.

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

### Requirement: Write nothing to the brief until the person applies the suggestion

WHEN the brief suggestion is run from "✦ Fill in from the book", the application SHALL write nothing to the brief until the person presses "Apply to the brief", which SHALL be offered when all steps have ended, and Cancel SHALL leave the brief unchanged. A suggestion made by a run's Prepare stage SHALL be written by the run with no "Apply", as the `translation-pipeline` capability's "Suggest the brief inside the run's Prepare stage when nobody has filled it" states.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: in the dialog a model's suggestion is only a proposal; the brief changes when the person says so. The one exception is the run's own suggestion on a brief nobody has touched: there the press of Start translation is the person's consent, every field the run writes is marked likely and stays editable, and a brief the person filled is never suggested over.

#### Scenario: Nothing is written until Apply

- **WHEN** all steps have ended and the person closes the dialog with Esc before pressing "Apply to the brief"
- **THEN** the suggestion is cancelled and the brief is unchanged

#### Scenario: A failed or stopped suggestion offers no Apply

- **WHEN** the model cannot answer or the person stops the suggestion
- **THEN** the dialog closes, no "Apply to the brief" is offered, the brief is unchanged and the card says "The suggestion was stopped. The brief is unchanged." after a stop

#### Scenario: The language proposal and the evidence are kept only on Apply

- **WHEN** the person closes the dialog without pressing "Apply to the brief"
- **THEN** no evidence line and no proposed source language is kept, and both appear only after Apply

#### Scenario: Apply writes the decided fields

- **WHEN** all steps have ended and the person presses "Apply to the brief"
- **THEN** the decided genre, register, narrator and readers are written to the brief and the dialog closes

#### Scenario: A run writes its suggestion without Apply

- **WHEN** Start translation is pressed on a book whose brief nobody has touched, and the run's Prepare stage decides an informal register
- **THEN** the brief holds the informal register, marked likely, and no "Apply to the brief" was offered or pressed

### Requirement: Choose the quality dial and show what it turns on

The book-brief screen SHALL offer, in its Quality vs speed card, a dial of `Fast`, `Balanced` or `Max`, set as Settings › Automation's "Quality new books start at" says and shown at `Balanced` while the quality is still to be chosen by the AI model, with a one-line hint naming what the position does about fixing, proofreading and extra calls and the line "The same for every model." (WHERE the merge at Max is built, the line SHALL read "The same for every model, except that Max merges marked paragraphs only on a large model." and the Max hint SHALL name the merge), and a model row naming the provider and the model, with a `change` link that opens Settings and no readiness badge. It SHALL NOT offer a review-mode choice; that is decided when the application is launched.

**Source**: `research/review-digest.md#f16--defaults-work-against-the-goal`, `design.md#d21--defaults-instead-of-questions`, `design.md#d22--the-merge-at-max-is-an-experiment-first`. In plain words: the dial trades speed for care, and a person can only make that trade knowingly if the screen says what each position costs — how many fixing calls a piece gets, which passages are re-read, and how many extra calls that costs. The line under the hint says whether a position means the same on every model: it does, with the one exception the line names where the merge at Max is built. The model row is shown because the model is the other half of the quality question, and the link keeps the one place it is changed in Settings, so this row cannot drift from that choice. It carries no readiness badge because that would mean keeping a verification result between checks, which this build does not do; `app-shell`'s "Match the reference rendering" states this as a deviation from the reference rendering, alongside the sidebar's own readiness dot. The review mode — when the run stops for the person — is a preference about how they like to work, not a property of the book, so it does not belong here. The quality of a new book is no longer always Balanced: it follows the model's size at the first start unless the person chose one. The merge at Max is an experiment that is built only when it passes its gate (`design.md#d22--the-merge-at-max-is-an-experiment-first`); when it is built it is the one mechanic that depends on the model's size, so the line says so and the hint for Max names it, and when it is not built both read as before.

#### Scenario: Balanced is the default and says what it does

- **WHEN** a book is opened on a fresh installation and the brief is shown
- **THEN** the dial is at `Balanced`, the line of "Say that the quality is chosen by the model until the person chooses one" is shown under it, and the hint reads
  `Balanced: fixing calls — one repair per piece · proofread — marked and doubtful windows · extra calls — about one call per marked passage`
- **AND** the line below reads `The same for every model.` where the merge at Max is not built, and `The same for every model, except that Max merges marked paragraphs only on a large model.` where it is built

#### Scenario: The hint follows the dial

- **WHEN** the dial is moved to `Max` and the merge at Max is not built
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

#### Scenario: Max names the merge where the merge is built

- **WHEN** the merge at Max is built, the configured model is `gemma4:26b` (a large model) and the dial is moved to `Max`
- **THEN** the hint reads `Max: fixing calls — one repair per piece · proofread — marked paragraphs drafted twice more and merged, doubtful windows, then the whole book re-read · extra calls — about three calls per marked passage, plus one call per 4 paragraphs of the whole book`
- **AND** the line below reads `The same for every model, except that Max merges marked paragraphs only on a large model.`

#### Scenario: Max on a small model proofreads without a merge

- **WHEN** the merge at Max is built, the configured model is `gemma4:e4b` (a small model) and the dial is moved to `Max`
- **THEN** the hint reads `Max: fixing calls — one repair per piece · proofread — marked and doubtful windows, then the whole book re-read · extra calls — about one call per marked passage, plus one call per 4 paragraphs of the whole book`
- **AND** the line below reads `The same for every model, except that Max merges marked paragraphs only on a large model.`

#### Scenario: Nothing changes where the merge is not built

- **WHEN** the merge at Max is not built and the dial is moved to `Max` with any model
- **THEN** the hint is the one of "The hint follows the dial" and the line below reads `The same for every model.`

## REMOVED Requirements

### Requirement: Offer the apostrophe, quotation-mark and default-address choices the target language sets

**Reason**: The owner decided that the window is the same for every language (`research/review-digest.md#owner-decisions`); these rows existed only where the target language's file set them.

**Migration**: The target language's own rules decide the three, as they always did for a brief that stored no choice. A stored brief that held a choice loses it: the database is set aside and started fresh by this change (ADR-0043), so nothing is converted.

### Requirement: Offer a choice for each convention the target language declares

**Reason**: The same decision: the hard-g row was shown for Ukrainian targets only.

**Migration**: The target language's file keeps the two letters and its default. The glossary no longer refuses a suggested name for its hard g; the two letters still count as one letter when the forms of a name are compared. A single name is spelled as the person wants in the glossary.

### Requirement: Re-check the decided segments when a policy key changes

**Reason**: No policy key is left on the Book Brief, so nothing can change that would call for the re-check, and its banner can never be shown.

**Migration**: None. The typography pass still runs on every draft with the target language's rules.

### Requirement: Offer a per-book switch for chapter summaries

**Reason**: Chapter summaries are no longer written. They took 6.8 % of the traced run (27 calls, 137 s), their block was 11.6 % of the draft prompt tokens, and no effect on the translated text could be measured (`research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`). A switch for a thing that does nothing is removed.

**Migration**: None for the person. The Book Brief shows no "Chapter summaries" switch; the stored brief loses the `chapterSummaries` value, and the database is set aside and started fresh by this change (ADR-0043).

### Requirement: Say how many finished chapters will be summarised when the switch is turned on

**Reason**: The switch it describes is removed ("Offer a per-book switch for chapter summaries").

**Migration**: None. No chapter is summarised.

### Requirement: Show the story shape the analysis found, with the switch it suggests

**Reason**: The story shape ("one story" or "separate stories") had one use: it chose the default of the chapter-summaries switch. With the switch removed it decides nothing, so the field, its two options and its place in the brief suggestion are removed.

**Migration**: None for the person. The stored brief loses the `storyShape` value; the brief suggestion no longer asks the model for it and its progress dialog lists one decision fewer.

### Requirement: Ask who narrates once at Start translation

**Reason**: The default path asks no question at Start: a forced male-or-female answer about the narrator was a coin flip that went into every request (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`). A narrator whose gender is not stated, or only likely, is held to no gender.

**Migration**: The person sets the narrator's person and gender in the "Who narrates?" field of the Book Brief, before or during the run.

### Requirement: Ask about a meaning-bearing name in Prepare, once, and never during a run

**Reason**: The Start questions are removed, and the code no longer refuses the model's translated rendering of a speaking name, which is what the question worked around (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`).

**Migration**: The person types the rendering they want into the Translation cell on Names & style; the name's meaning stays a note on the entry (the `glossary` capability).

### Requirement: Suggest the tone and style from verified readings, one decision per field

**Reason**: Restated as "Suggest the tone and style from verified readings, one decision for each field": the old requirement held scenarios about behaviour this change removes, and a changed requirement may not drop a scenario.

**Migration**: None. The behaviour that stays is stated in full under the new heading; the story-shape decision is gone with the chapter summaries.

## RENAMED Requirements

- FROM: `### Requirement: Suggest the narrator from the source and ask once at Start translation`
- TO: `### Requirement: Suggest the narrator from the source`
- FROM: `### Requirement: Check the narrator's pending quotes again after the names, before asking who narrates`
- TO: `### Requirement: Check the narrator's pending quotes again after the names`
- FROM: `### Requirement: Judge the narration before the Start question when the import could not`
- TO: `### Requirement: Judge the narration when the run starts when the import could not`
