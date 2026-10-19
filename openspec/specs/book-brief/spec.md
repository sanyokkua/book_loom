# book-brief Specification

## Purpose
The instruction a person gives before a run: which language the book is translated into, what the book says its
own language is, and — visible but not yet answerable — the tone, the policies, the balance and the quality dial
that the engine will one day read.

## Requirements

### Requirement: Say so when no book is open on the book brief

WHILE no book is open, the book-brief screen SHALL report that a book has not been opened yet and SHALL offer a
route to the import screen, and SHALL NOT present an empty brief.

**Source**: FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`. In plain words: the navigation is always live, so a person can open the brief before opening any book — and the screen is built entirely out of one. An empty brief with greyed-out pickers looks broken; saying "open a book first" and offering the way there is the same number of controls and tells the truth. The export screen already has this state; the brief is another screen that needs a book and lacked it. (The structure is no screen of its own since the import screen shows it, so it needs no such state.)

#### Scenario: The brief with no book open

- **WHEN** the book-brief screen is opened and no book has been opened
- **THEN** it reports that no book is open and offers a route to the import screen
- **AND** no language, tone, policy, "Also translate" or quality control is presented

#### Scenario: Opening a book leaves the state

- **WHEN** a book is opened and the book-brief screen is shown again
- **THEN** the brief is presented and the no-book-open report is gone

### Requirement: Choose the source and target languages

The book-brief screen SHALL let a person choose the target language and choose or change the source language, and WHEN a book is opened SHALL preselect as the source the language the book declares, normalized. WHERE it declares none, or a code the application cannot name, and its content documents give no majority, the screen SHALL start with the source language **Auto**, SHALL say that the model will identify it, and SHALL keep `Continue` available once a target language is chosen (ADR-0042).

**Source**: FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-IMPORT-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, ADR-0037 (`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), `docs/next_features.md` §15 ("The source language cannot be corrected"). In plain words: the application takes the source language from the book's metadata and never guesses it from the text (it does not guess; the model identifies the language when none is chosen), so the metadata's answer is only a starting point. Real books declare the wrong language — one surveyed EPUB declares `en-US` for Ukrainian text, and the locked selector made the model keep Ukrainian passages verbatim in a Polish translation — so the person must be able to correct it. A book that declares nothing gets no invented default: it is Auto, and a run with a wrong chosen source language is worse than a run that asks the model.

#### Scenario: The declared language is preselected and can be changed

- **WHEN** a book declaring `en` is open
- **THEN** the source language shows `English`
- **AND** choosing `Ukrainian` as the source is accepted and kept

#### Scenario: A mis-declared book is corrected

- **WHEN** `Croatian_Lesson_Police.epub`, declaring `en-US`, is open and the person changes the source to
  `Ukrainian` and the target to `Polish`
- **THEN** the brief holds source `uk` and target `pl`

#### Scenario: A book that declares nothing is Auto

- **WHEN** a plain-text book declaring no language is open and `uk` is chosen as the target
- **THEN** no source language is chosen (Auto), the screen says the model will identify it, and `Continue` is
  available
- **AND** after `English` is chosen as the source, `Continue` stays available

#### Scenario: A declared language outside the list is preselected

- **WHEN** an EPUB declaring `la` is open
- **THEN** the source language shows `Latin`

#### Scenario: A book with no package language takes its chapters' language

- **WHEN** an EPUB whose package declares no language and whose content documents all declare `de` is open
- **THEN** the source language shows `German` and `Continue` is available once a target is chosen

#### Scenario: An unrecognized declaration is also Auto

- **WHEN** a book declaring `xx-yy` is open and a target is chosen
- **THEN** no source language is chosen and `Continue` is available

#### Scenario: A declared regional code is normalized

- **WHEN** a book declaring `en-US` is open
- **THEN** the source language shows `English`

#### Scenario: A language-mismatch warning preselects the content documents' language

- **WHEN** the import screen raised a language-mismatch warning for the open book
- **THEN** the source language is the language the book's content documents declare

#### Scenario: A majority of the content documents decides when the book declares nothing

- **WHEN** the book declares no language but more than half of its content documents that declare one agree
- **THEN** the source language is that content language

#### Scenario: Auto is the first entry of the source list

- **WHEN** the source language is Auto
- **THEN** it is shown as the first entry of the source list, "Auto — the model identifies it", no source language is chosen, and the code uses generic rules, assuming no language

### Requirement: Offer one searchable list of languages for source and target

The book-brief screen SHALL show the same 34 languages in the source and target lists and, WHEN a person types into either field, narrow the list to those whose display name contains the typed text anywhere, ignoring case and accents. WHEN the field loses focus holding text that names a recognized language the list lacks, as a display name or tag, the screen SHALL accept it and show its display name; otherwise it SHALL keep the previous language, or none.

**Source**: FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`. In plain words: a list of 34 languages is too long to scroll every time, so typing a few letters finds the entry — `bokmal` finds Norwegian Bokmål without the accent. Both fields share one list because any of these languages can be either side of a translation. The list is only the common European languages, not a limit: a person translating Latin types `Latin` or `la` and gets it, because the application can name it. Text that names no language is refused rather than sent to the model as a language.

#### Scenario: Both lists hold the same 34 languages

- **WHEN** the source list and the target list are opened
- **THEN** each holds exactly 34 entries, the same in both, including `Norwegian Bokmål`, `Chinese (Traditional)`
  and `Irish`

#### Scenario: Typing narrows the list

- **WHEN** `ukr` is typed into the target field
- **THEN** the list shows only `Ukrainian`

#### Scenario: A match anywhere in the name counts

- **WHEN** `chin` is typed into the source field
- **THEN** the list shows `Chinese (Simplified)` and `Chinese (Traditional)`

#### Scenario: Accents and case are ignored

- **WHEN** `BOKMAL` is typed into the target field
- **THEN** the list shows `Norwegian Bokmål`

#### Scenario: A language outside the list is accepted by name

- **WHEN** `Latin` is typed into the target field and focus leaves it
- **THEN** the target is `la` and the field shows `Latin`

#### Scenario: A language outside the list is accepted by tag

- **WHEN** `la` is typed into the source field and focus leaves it
- **THEN** the source is `la` and the field shows `Latin`

#### Scenario: Unmatched text is not accepted as a language

- **WHEN** the target is `Polish` and `Elvish` is typed into the target field and focus leaves it
- **THEN** the target is still `Polish`

#### Scenario: The 34 languages are these

- **WHEN** the source list or the target list is read
- **THEN** it holds English and the other 23 official languages of the European Union (Bulgarian, Croatian, Czech, Danish, Dutch, Estonian, Finnish, French, German, Greek, Hungarian, Irish, Italian, Latvian, Lithuanian, Maltese, Polish, Portuguese, Romanian, Slovak, Slovenian, Spanish, Swedish), Chinese (Simplified), Chinese (Traditional), Ukrainian, Russian, Belarusian, Turkish, Japanese, Norwegian Bokmål, Serbian and Korean

#### Scenario: A language is recognized by a well-formed tag

- **WHEN** the typed text is a well-formed BCP-47 tag whose language the application can name, and focus leaves the field
- **THEN** the language is recognized and accepted

### Requirement: Refuse a brief whose source and target are the same language

IF the chosen source language and the chosen target language are the same, THEN the book-brief screen SHALL show the
message `The source and target languages are the same.` beside the language fields and SHALL keep `Continue`
unavailable until they differ.

**Source**: FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`. In plain words: now that the source is editable, a person can pick the same language on both sides; a run like that would spend hours asking a model to translate a book into the language it is already in, and the quality checks would flag every segment as untranslated. Saying so before the run is cheaper.

#### Scenario: The same language on both sides is refused

- **WHEN** the source is `English` and `English` is chosen as the target
- **THEN** the message `The source and target languages are the same.` is shown and `Continue` is unavailable

#### Scenario: Changing either side clears the refusal

- **WHEN** that brief's target is changed to `Ukrainian`
- **THEN** the message disappears and `Continue` becomes available

### Requirement: Capture the tone and style of the translation

The book-brief screen SHALL offer, in its Tone & style card, a genre field that suggests exactly 40 predefined genres, filters them as typed and accepts free text; a register choice of `Formal / literary`, `Neutral` or `Casual`, defaulting to `Neutral`; and free-text narrative voice / era and audience fields. WHERE a predefined genre is chosen, the run SHALL receive its English name whatever the interface language; WHERE free text is entered, the run SHALL receive that text as written.

**Source**: FR-BRIEF-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`. In plain words: these fields tell the model what kind of book it is translating — a Gothic novel for adults reads differently from a children's adventure — and that shapes every sentence. A list helps people name a genre quickly, but books do not fit lists, so free text is allowed. Forty is a fixed count, not an estimate, because a fixed catalogue is what a completeness test can check and "about forty" is not. The prompt is written in English, so a chosen genre reaches it in English even when the interface shows it in Ukrainian.

#### Scenario: A new book starts with the defaults

- **WHEN** a book is opened and the brief is shown
- **THEN** the register is `Neutral` and the genre, voice / era and audience fields are empty

#### Scenario: The genre field offers exactly forty entries

- **WHEN** the genre field's suggestions are read with nothing typed
- **THEN** there are exactly 40 of them, including `Literary fiction`, `Gothic novel`, `Graphic novel` and `True
  crime`

#### Scenario: Typing filters the genre suggestions

- **WHEN** `gothic` is typed into the genre field
- **THEN** the suggestions include `Gothic novel` and exclude `Science fiction`

#### Scenario: A free-text genre is kept as written

- **WHEN** `Cosy mystery set in 1920s Kyiv` is typed into the genre field and no suggestion is chosen
- **THEN** the brief holds the genre `Cosy mystery set in 1920s Kyiv`

#### Scenario: A predefined genre reaches the run in English

- **WHEN** the interface is Ukrainian and the person chooses the suggestion shown as `Готичний роман`
- **THEN** the run receives the genre `Gothic novel`

#### Scenario: The forty predefined genres are these

- **WHEN** the genre field's suggestions are read
- **THEN** they are Literary fiction, Classic literature, Historical fiction, Gothic novel, Romance, Historical romance, Mystery, Detective fiction, Crime fiction, Thriller, Psychological thriller, Horror, Science fiction, Fantasy, Epic fantasy, Dystopian fiction, Adventure, Western, War fiction, Humor, Satire, Young adult, Children's literature, Fairy tale, Short stories, Poetry, Drama, Memoir, Biography, Autobiography, Essay, Travel writing, History, Philosophy, Religion and spirituality, Popular science, Self-help, Business, True crime and Graphic novel

### Requirement: Capture the translation policies

The book-brief screen SHALL offer, in its Translation policies card, a character-and-place-name policy, a foreign-language-passage policy, a footnote policy, a unit policy and a faithful ↔ natural slider from 0 to 100 labelled `literal · balanced · free`, each with the choices and the default the scenarios name.

**Source**: FR-BRIEF-03, FR-BRIEF-04, FR-BRIEF-05, FR-BRIEF-06, FR-BRIEF-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), DD-26 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`. In plain words: these are the decisions a human translator makes once per book — do the names stay English, get spelled in the target alphabet, or get translated; does a Latin epigraph stay Latin; are footnotes translated; do miles become kilometres; how close to the original wording should the translation stay. The defaults are the safe choices for most fiction: names transliterated, foreign passages kept foreign, footnotes translated, units kept, and a balance just past the middle towards natural.

#### Scenario: A new book starts with the policy defaults

- **WHEN** a book is opened and the brief is shown
- **THEN** the name policy is `Transliterate`, the foreign-passage policy is `Keep as-is`, the footnote policy is
  `Translate`, the unit policy is `Keep`, and the slider stands at `55`

#### Scenario: A policy change is kept

- **WHEN** the name policy is set to `Keep original` and the slider is moved to `20`
- **THEN** the brief holds the name policy `Keep original` and the balance `20`

#### Scenario: The slider stays within its range

- **WHEN** the slider is dragged past its left end
- **THEN** the balance is `0`, not a negative value

#### Scenario: Each policy offers these choices

- **WHEN** the Translation policies card is shown
- **THEN** the name policy offers `Translate`, `Transliterate` and `Keep original`, the foreign-passage policy `Keep as-is`, `Translate` and `Translate + note`, the footnote policy `Translate` and `Keep`, and the unit policy `Keep` and `Metric`

### Requirement: Choose which auxiliary text is also translated

The book-brief screen SHALL offer, in its Also translate card, four switches: `ToC / navigation labels`, `Image alt-text` and `Book metadata (title/author)`, each on by default, and `Frontmatter values`, off by default. The ToC switch SHALL also govern each page's own title, and the metadata switch the book's description, wherever the format carries one.

**Source**: FR-BRIEF-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-11 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`. In plain words: besides the chapters, a book carries text a reader still sees — the table of contents, image descriptions read aloud by screen readers, the title on the library shelf. Each is translated by default except Markdown frontmatter values, which often hold copyright and edition notes that should stay as written. A page's own title reads like a navigation label — it is the same kind of short wayfinding text — and a description reads like the title and author it sits beside, so each rides along with the switch it resembles instead of the mockup growing two more toggles it does not draw (see design).

#### Scenario: A new book starts with three switches on

- **WHEN** a book is opened and the brief is shown
- **THEN** `ToC / navigation labels`, `Image alt-text` and `Book metadata (title/author)` are on, and
  `Frontmatter values` is off

#### Scenario: Switching off navigation labels also keeps page titles untranslated

- **WHEN** a book whose pages carry their own titles is translated with `ToC / navigation labels` switched off, then
  exported
- **THEN** no page title is sent for translation
- **AND** every exported page title is unchanged from the source

#### Scenario: Switching on book metadata also translates the description

- **WHEN** a book with the description `A gripping tale of survival.` is translated with
  `Book metadata (title/author)` on
- **THEN** the description `A gripping tale of survival.` is sent for translation

#### Scenario: Switching off book metadata leaves the description in the source language

- **WHEN** a book with a description is translated with `Book metadata (title/author)` switched off
- **THEN** the description stays in the source language along with the title and author

### Requirement: Choose the quality dial and show what it turns on

The book-brief screen SHALL offer, in its Quality vs speed card, a dial of `Fast`, `Balanced` or `Max`, defaulting to `Balanced`, with a one-line hint naming what the chosen position turns on, and a model row naming the provider and the model the run will use, with a `change` link that opens the Settings screen and no readiness badge. The screen SHALL NOT offer a review-mode choice; the review mode is decided when the application is launched.

**Source**: FR-BRIEF-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), DD-45 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-45-acceptance-model`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, ADR-0036 (`docs/adr/ADR-0036-review-modes-pause-the-run.md`). In plain words: the dial trades speed for care, and a person can only make that trade knowingly if the screen says what each position costs — a reviewer pass per chunk (two on Max), a whole-book consistency pass. The model row is shown because the model is the other half of the quality question, and the link keeps the one place it is changed in Settings, so this row cannot drift from that choice. It carries no readiness badge because that would mean keeping a verification result between checks, which this build does not do; `app-shell`'s "Match the reference rendering" states this as a deviation from the reference rendering, alongside the sidebar's own readiness dot. The review mode — when the run stops for the person — is a preference about how they like to work, not a property of the book, so it does not belong here.

#### Scenario: Balanced is the default and says what it does

- **WHEN** a book is opened and the brief is shown
- **THEN** the dial is at `Balanced` and the hint reads
  `Balanced: chunks of up to 8 segments · AI reviewer on · backward-consistency pass off`

#### Scenario: The hint follows the dial

- **WHEN** the dial is moved to `Max`
- **THEN** the hint reads `Max: chunks of up to 2 segments · AI reviewer on · backward-consistency pass on`

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
- **THEN** the hint reads `Fast: chunks of up to 8 segments · AI reviewer off · backward-consistency pass off`

### Requirement: Carry every brief choice into the run

WHEN a run is started, the system SHALL build the instructions sent to the model from the brief as it then stands and SHALL send them with every translation request of the run. WHILE an "Also translate" switch is off, the run SHALL NOT translate the auxiliary text of that kind, and the exported book SHALL keep it exactly as it was in the source language. WHILE a book stays open, the brief's choices SHALL be kept for it when the person moves to another screen and back.

**Source**: FR-BRIEF-01..09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-11 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, DD-47 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`). In plain words: until now every control on this screen except the target language was drawn and ignored. A brief that does not reach the model is decoration, so this is the requirement that makes the screen mean something: what the person set is what the model is told, on every call, and a switch that says "do not translate the table of contents" leaves the table of contents alone.

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
- **THEN** the instructions are built from the source and target languages, genre, register, narrative voice / era, audience, the four policies, the faithful ↔ natural balance and the quality dial

### Requirement: Keep the destination off the brief

The book-brief screen SHALL NOT offer a destination path, a Browse action or an overwrite switch; where the
translated book is written SHALL be chosen on the Export screen.

**Source**: FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, ADR-0035 (`docs/adr/ADR-0035-export-is-a-separate-action.md`). In plain words: a run no longer writes the book — exporting is its own action that can happen at any time, even halfway through — so asking where to save before the translation even starts asks the question at the wrong moment.

#### Scenario: The brief has no destination card

- **WHEN** the brief is shown for an open book
- **THEN** no `Where to save` card, path field, `Browse…` button or overwrite switch is present

### Requirement: Wrap segmented choices rather than truncating them

WHERE a label of a segmented choice on the brief does not fit its segment, the screen SHALL wrap the label onto a
further line or widen the segment, and SHALL NOT cut the label short with an ellipsis.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`, `docs/next_features.md` §15 ("Book Brief segmented buttons truncate Ukrainian labels at 1024 px"). In plain words: Ukrainian words are longer than English ones, and the hand test found the name policy reading "Транслітерув…" and "Залишати оригі…" in a 1024-pixel window — a choice whose label cannot be read cannot be chosen with confidence.

#### Scenario: Ukrainian policy labels are readable at 1024 px

- **WHEN** the interface is Ukrainian and the window is 1024 pixels wide
- **THEN** the name-policy labels `Транслітерувати` and `Залишати оригінал` are shown in full, with no ellipsis

### Requirement: Say when the target language has no tested translation rules

The Book Brief SHALL show, under the target language box, a note stating that the language's translation rules are not
tested yet and that the general rules are used, with a hover explanation, in the interface language, for any target whose
language-rules file is not marked `status=tested` or does not exist, and SHALL show no note for a tested language or while
no target is chosen. The note SHALL never block continuing.

**Source**: `additional-work.md` 15d.5b. In plain words: a language written for with care translates better than one with only general advice, and a person should know which one they picked.

#### Scenario: A language without tested rules

- **WHEN** the target is German and German's rules are untested
- **THEN** the note is shown and Continue stays available

#### Scenario: A tested language

- **WHEN** the target is Ukrainian
- **THEN** no note is shown

### Requirement: Capture who narrates the book

The book-brief screen SHALL offer, in its Tone & style card, a narrator choice of `Not stated`, `First person` or `Third person` and a narrator-gender choice of `Not stated`, `Male` or `Female`, both defaulting to `Not stated`, the gender available only while the narrator is in the first person; each control SHALL explain itself on hover. The brief SHALL hold the choice as `BookBrief.narrator` (the person and the gender), and every change of the brief SHALL keep the narrator it holds.

**Source**: FR-BRIEF-02, ADR-0038; tasks 15d.10. In plain words: a first-person narrator's past-tense verbs show their gender in Ukrainian and many other languages (`я зачинив` / `я зачинила`), and the source gives no hint of it, so the person says it once and every call and check uses it.

#### Scenario: A new book starts with no narrator stated

- **WHEN** a book is opened and the brief is shown
- **THEN** the narrator choice is `Not stated` and the gender choice is not available

#### Scenario: A first-person female narrator reaches the brief

- **WHEN** the person chooses `First person` and then `Female`
- **THEN** the brief holds a first-person female narrator and the gender choice is available

#### Scenario: Another change keeps the narrator

- **WHEN** a first-person male narrator is set and the register, the genre and the dial are then changed
- **THEN** the brief still holds the first-person male narrator

#### Scenario: A brief made before the narrator existed reads as it did

- **WHEN** a brief made before the narrator existed is read
- **THEN** it holds the unspecified narrator

### Requirement: Suggest the narrator from the source and ask once at Start translation

WHEN a book is opened, the application SHALL read its narration chapter by chapter, outside quotes and dialogue, and keep a `NarratorHint` (the person `FIRST`, `THIRD` or `MIXED`, and the chapters judged); it SHALL never set the narrator's gender. WHILE the hint is `FIRST`, or `MIXED` with at least a quarter of its judged chapters first person, and the gender is not chosen and the narrator is not third person, the Tone & style card SHALL show a notice and preselect `First person`.

**Source**: FR-BRIEF-02, ADR-0038; tasks 15e.14. In plain words: a first-person book told by a man and translated with no narrator gender came out with feminine verbs (`я була`), because nothing told the model or the checks who "I" is. The source shows that the book says "I" but not the gender, so the application notices the first, shows a note, and asks the second question once, at the moment it matters, without ever blocking the start.

#### Scenario: A first-person book shows the notice and preselects the person

- **WHEN** a book whose narration says "I" is opened and its brief is shown
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

### Requirement: Ask who narrates once at Start translation

WHEN Start translation is pressed (on Names & style or on Translating) under the notice's conditions and the question has not been answered for the book in this session, the application SHALL ask once `Who narrates?` with `Male`, `Female`, `Not stated — start anyway` and `Back`. A gender SHALL be written to the brief as a first-person narrator of that gender and the run SHALL start; `Not stated` SHALL start as before; `Back` or Escape SHALL start nothing and change nothing.

**Source**: FR-BRIEF-02, ADR-0038; tasks 15e.14. In plain words: a first-person book told by a man and translated with no narrator gender came out with feminine verbs (`я була`), because nothing told the model or the checks who "I" is. The source shows that the book says "I" but not the gender, so the application notices the first, shows a note, and asks the second question once, at the moment it matters, without ever blocking the start.

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

### Requirement: Suggest the narrator's gender only from evidence the code verifies

WHEN the model's style suggestion answers the narrator's gender, the application SHALL keep it only when its quote is found in the sample the model read and shows the gender in words the code can check; any other answer SHALL be the unknown gender. A first-person narrator whose gender is unknown SHALL take the kept gender, shown as `Male (suggested)` or `Female (suggested)` until the person changes it; a gender the person set is never replaced and a third-person narrator gets none.

**Source**: FR-BRIEF-02; tasks 15h.G2, 15i.B4. In plain words: the model was free to say "male" from nothing, and its answer was then thrown away for a book whose narrator was already set to first person. Now the model has to point at a line the code can check — a name it can look up or a word like "ma'am" — and the gender it proves fills only a gap, marked as a suggestion.

#### Scenario: A vocative of a glossary person genders the narrator

- **WHEN** the glossary holds the character `Jack` as male, the narrator is first person with no gender, and the model
  quotes `Hang on to your ass, Jack` from the sample with the name `Jack`
- **THEN** the narrator's gender is male and shown as `Male (suggested)` with the quote

#### Scenario: A gender with nothing the code can check stays unknown

- **WHEN** the model answers `female` with a quote that holds neither a known name nor a word of address
- **THEN** the narrator's gender stays unknown and Start translation asks who narrates

#### Scenario: A name the narrator says to someone else is no vote

- **WHEN** a window holds `"Mind the step, Lucy," I said` and the model answers `female` with the name `Lucy`
- **THEN** that window gives no gender vote

#### Scenario: A name in the quote proves the gender whatever the model said

- **WHEN** the quote calls the narrator by a name, or the narrator gives it ("Hang on to your ass, Jack"), the name the model gives stands in the quote, and it is a glossary character of known gender (the whole term or one word of it) or a name of the source language's first-name list
- **THEN** that name's gender is the narrator's, whatever the model said

#### Scenario: Words of address of one gender prove it when no name does

- **WHEN** the quote has no name but holds words of the source language file's `addressTerms` table (`term>male` / `term>female`, such as `sir`, `ma'am`, `пане`, `дівчино`) of one gender only
- **THEN** that gender is the narrator's

#### Scenario: The narrator's own introduction is a vote

- **WHEN** a window holds `"I'm Lucy," I said`
- **THEN** the name `Lucy` is a vote for the narrator's gender, while a name the attribution beside quoted speech shows the narrator saying to someone else is not

#### Scenario: One window proves a gender unless another proves the other

- **WHEN** one window proves a gender and no other window proves the other gender
- **THEN** the gender is kept and shown with its quote and the number of proving windows, not as uncertain

#### Scenario: The suggested mark explains itself

- **WHEN** the narrator's gender is shown as `Male (suggested)` or `Female (suggested)`
- **THEN** a hover explanation is shown, in English and Ukrainian

#### Scenario: No kept gender leaves the question to Start

- **WHEN** no gender is kept for a first-person narrator
- **THEN** the gender stays unknown and the Start question asks as before

### Requirement: Keep the proving quotes and the origin of the narrator's gender

The brief's narrator SHALL keep the proving quotes and the gender origin `VERIFIED` for a gender the code verified, so a later step can check the quotes again. A quote that calls the narrator by a name nobody knows yet SHALL be kept on the narrator as a pending quote, with the gender still unknown.

**Source**: FR-BRIEF-02; tasks 15h.G2, 15i.B4. In plain words: the model was free to say "male" from nothing, and its answer was then thrown away for a book whose narrator was already set to first person. Now the model has to point at a line the code can check — a name it can look up or a word like "ma'am" — and the gender it proves fills only a gap, marked as a suggestion.

#### Scenario: The origins of a gender

- **WHEN** the narrator's gender is set by the person, known with no proof, counted from the narrator's words in the run, or unknown
- **THEN** its origin is `PERSON`, `SUGGESTED`, `INFERRED` or `NONE` respectively

#### Scenario: A quote naming an unknown person is pending

- **WHEN** a quote is in the sample, the name stands in it, the narrator does not say it to someone else, and nobody knows the name yet
- **THEN** it proves nothing now and is kept on the narrator as a pending quote with the gender unknown

### Requirement: Check the narrator's pending quotes again after the names, before asking who narrates

`SetupAssistant.recheckNarrator(projectId)` SHALL make no model call and write nothing, and for a first-person narrator of unknown gender SHALL return the narrator with that gender and the origin `VERIFIED` when a name in a pending quote is a glossary character of confirmed gender; otherwise it SHALL return it unchanged. WHEN Start translation is about to ask "Who narrates?", it SHALL run the re-check first and, when it verified a gender, store it and start without asking.

**Source**: FR-BRIEF-02; tasks 15i.T5. In plain words: the model may point at "Hold on tight, Brindle" before anybody knows Brindle is a woman; once the names pass or the person has said so, the same quote proves the narrator's gender, and nobody is asked what the book already says.

#### Scenario: A pending vocative is verified by the names pass

- **WHEN** the brief's narrator is first person with the pending quote `Hold on tight, Brindle`, the glossary now holds
  `Brindle` as a female character the person set, and Start translation is pressed
- **THEN** the narrator is stored as female with the origin `VERIFIED` and the run starts without the question

#### Scenario: A suggestion is no proof

- **WHEN** the glossary holds `Brindle` as female only suggested
- **THEN** the re-check returns the narrator unchanged and Start translation asks who narrates

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
- **THEN** Start translation asks "Who narrates?" as before

#### Scenario: A book Start would not ask about is not re-checked

- **WHEN** Start translation would not ask who narrates
- **THEN** no re-check runs

### Requirement: Suggest the tone and style from verified readings, one decision per field

WHEN the person asks the model to suggest the tone and style, the application SHALL ask what the model knows of the book, read several windows of the story with one `brief-suggestion` call each, ask one `brief-synthesis` call over the book's facts, the accepted knowledge and the windows' verified readings, and decide each field once. A failed window call SHALL count as a window with no reading, and a stop by the person during any call SHALL stop the suggestion and leave the brief unchanged.

**Source**: FR-BRIEF-02; tasks 15h.G1, 15i.B2–B4; `docs/specification/01_Product/12_PROMPT_CATALOG.md#brief-synthesis`. In plain words: one window can be an odd stretch of a book, and a model's memory of a famous title can be wrong, so a field is kept only when most of the book's passages say it, or when the model's checked knowledge of the book says the same as the passages; otherwise the field stays for the person to choose.

#### Scenario: Three of four windows decide the register

- **WHEN** three of four windows answer `casual` with quotes found in them and the fourth answers `formal`
- **THEN** the suggested register is `Casual`, shown as `from 3 of 4 passages`

#### Scenario: A split register stays neutral and uncertain

- **WHEN** two windows answer `casual` and two answer `formal`
- **THEN** the suggested register is `Neutral` and shown as uncertain

#### Scenario: One window alone does not decide the genre

- **WHEN** the synthesis names `Mystery` citing one of four windows and the model does not know the book
- **THEN** the genre stays empty and is shown as uncertain

#### Scenario: A cited window that read another genre is no support

- **WHEN** the synthesis names `Thriller` citing two windows that read `fantasy` and `romance`
- **THEN** the genre stays empty and is shown as uncertain

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

- **WHEN** the suggestion is made on Fast, Balanced or Max
- **THEN** 3, 4 or 5 single windows of the story are read, fewer for a short story

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

### Requirement: Keep a suggested field only when its basis holds

The register SHALL be kept by a strict majority of the windows read, and the narrator's person SHALL be the narrator detector's when it answers, else a strict majority of the windows. The genre, audience and voice / era SHALL come from the synthesis, and SHALL stand only when the basis holds; any other field SHALL stay neutral or empty and be shown as uncertain. WHILE the source language is Auto, the language a strict majority of the windows name SHALL be a proposal only.

**Source**: FR-BRIEF-02; tasks 15h.G1, 15i.B2–B4; `docs/specification/01_Product/12_PROMPT_CATALOG.md#brief-synthesis`. In plain words: one window can be an odd stretch of a book, and a model's memory of a famous title can be wrong, so a field is kept only when most of the book's passages say it, or when the model's checked knowledge of the book says the same as the passages; otherwise the field stays for the person to choose.

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
- **THEN** it names its basis before its value and says what it stands on (`from 3 of 4 passages`, `from what the model knows of this book`, the detector's count)
