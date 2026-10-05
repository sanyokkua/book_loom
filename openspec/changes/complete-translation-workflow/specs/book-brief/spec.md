# Spec Delta

## MODIFIED Requirements

### Requirement: Say so when no book is open

WHILE no book is open, the book-brief screen SHALL report that a book has not been opened yet and SHALL offer a
route to the import screen, and SHALL NOT present an empty brief.

The structure screen SHALL report the same state on the same condition.

**Source:** FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-01
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-structure`.
In plain words: the navigation is always live, so a person can open the brief or the structure before opening any
book — and both screens are built entirely out of one. An empty brief with greyed-out pickers looks broken; saying
"open a book first" and offering the way there is the same number of controls and tells the truth. The export
screen already has this state; these two are the other screens that need a book and lacked it.

#### Scenario: The brief with no book open

- **WHEN** the book-brief screen is opened and no book has been opened
- **THEN** it reports that no book is open and offers a route to the import screen
- **AND** no language, tone, policy, "Also translate" or quality control is presented

#### Scenario: The structure screen with no book open

- **WHEN** the structure screen is opened and no book has been opened
- **THEN** it reports that no book is open and offers a route to the import screen

#### Scenario: Opening a book leaves the state

- **WHEN** a book is opened and the book-brief screen is shown again
- **THEN** the brief is presented and the no-book-open report is gone

## ADDED Requirements

### Requirement: Choose the source and target languages

The book-brief screen SHALL let a person choose the target language and SHALL let a person choose or change the
source language.

WHEN a book is opened, the screen SHALL preselect as the source the language the book declares, normalized (for
example a declared `en-US` preselects English); where the import screen raised a language-mismatch warning, the
language the book's content documents declare; and where the book declares no language but more than half of its
content documents that declare one agree, that content language.

WHERE the book declares no language and its content documents give no majority, or declares a code the application
cannot name, the screen SHALL start with no source language chosen, SHALL say that the book does not declare
one, and SHALL keep `Continue` unavailable — so that no run can start — until a source language is chosen.

**Source:** FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-IMPORT-03
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, ADR-0037
(`docs/adr/ADR-0037-language-evidence-from-metadata-only.md`), `docs/next_features.md` §15 ("The source language
cannot be corrected").
In plain words: the application takes the source language from the book's metadata and never guesses it from the
text, so the metadata's answer is only a starting point. Real books declare the wrong language — one surveyed EPUB
declares `en-US` for Ukrainian text, and the locked selector made the model keep Ukrainian passages verbatim in a
Polish translation — so the person must be able to correct it. A book that declares nothing gets no invented
default: a run with the wrong source language is worse than a run that waits for one click.

#### Scenario: The declared language is preselected and can be changed

- **WHEN** a book declaring `en` is open
- **THEN** the source language shows `English`
- **AND** choosing `Ukrainian` as the source is accepted and kept

#### Scenario: A mis-declared book is corrected

- **WHEN** `Croatian_Lesson_Police.epub`, declaring `en-US`, is open and the person changes the source to
  `Ukrainian` and the target to `Polish`
- **THEN** the brief holds source `uk` and target `pl`

#### Scenario: A book that declares nothing waits for a source

- **WHEN** a plain-text book declaring no language is open and `uk` is chosen as the target
- **THEN** no source language is chosen, the screen says the book does not declare one, and `Continue` is
  unavailable
- **AND** after `English` is chosen as the source, `Continue` becomes available

#### Scenario: A declared language outside the list is preselected

- **WHEN** an EPUB declaring `la` is open
- **THEN** the source language shows `Latin`

#### Scenario: A book with no package language takes its chapters' language

- **WHEN** an EPUB whose package declares no language and whose content documents all declare `de` is open
- **THEN** the source language shows `German` and `Continue` is available once a target is chosen

#### Scenario: An unrecognized declaration also waits for a source

- **WHEN** a book declaring `xx-yy` is open
- **THEN** no source language is chosen and `Continue` is unavailable

### Requirement: Offer one searchable list of languages for source and target

The book-brief screen SHALL show the same 34 languages, as a convenience, in the lists for both the source and the
target: English and the other
23 official languages of the European Union (Bulgarian, Croatian, Czech, Danish, Dutch, Estonian, Finnish, French,
German, Greek, Hungarian, Irish, Italian, Latvian, Lithuanian, Maltese, Polish, Portuguese, Romanian, Slovak,
Slovenian, Spanish, Swedish), Chinese (Simplified), Chinese (Traditional), Ukrainian, Russian, Belarusian, Turkish,
Japanese, Norwegian Bokmål, Serbian and Korean.

WHEN a person types into either language field, the screen SHALL narrow the list to the languages whose display name
contains the typed text anywhere, ignoring letter case and accents.

WHEN the typed text, as a display name or a language tag, names a language the application recognizes that the list does
not hold, and the field loses focus, the screen SHALL accept it and show it by its display name. A language is
recognized when its tag is a well-formed BCP-47 tag whose language the application can name. IF the typed text names no
recognized language when the field loses focus, THEN the screen SHALL keep the previously chosen language, or none.

**Source:** FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`,
`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`.
In plain words: a list of 34 languages is too long to scroll every time, so typing a few letters finds the entry —
`bokmal` finds Norwegian Bokmål without the accent. Both fields share one list because any of these languages can be
either side of a translation. The list is only the common European languages, not a limit: a person translating Latin
types `Latin` or `la` and gets it, because the application can name it. Text that names no language is refused rather
than sent to the model as a language.

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

### Requirement: Refuse a brief whose source and target are the same language

IF the chosen source language and the chosen target language are the same, THEN the book-brief screen SHALL show the
message `The source and target languages are the same.` beside the language fields and SHALL keep `Continue`
unavailable until they differ.

**Source:** FR-BRIEF-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`.
In plain words: now that the source is editable, a person can pick the same language on both sides; a run like that
would spend hours asking a model to translate a book into the language it is already in, and the quality checks
would flag every segment as untranslated. Saying so before the run is cheaper.

#### Scenario: The same language on both sides is refused

- **WHEN** the source is `English` and `English` is chosen as the target
- **THEN** the message `The source and target languages are the same.` is shown and `Continue` is unavailable

#### Scenario: Changing either side clears the refusal

- **WHEN** that brief's target is changed to `Ukrainian`
- **THEN** the message disappears and `Continue` becomes available

### Requirement: Capture the tone and style of the translation

The book-brief screen SHALL offer, in its Tone & style card, a genre field that suggests exactly these 40 predefined
genres and filters them as the person types, and also accepts free text: Literary fiction, Classic literature,
Historical fiction, Gothic novel, Romance, Historical romance, Mystery, Detective fiction, Crime fiction, Thriller,
Psychological thriller, Horror, Science fiction, Fantasy, Epic fantasy, Dystopian fiction, Adventure, Western, War
fiction, Humor, Satire, Young adult, Children's literature, Fairy tale, Short stories, Poetry, Drama, Memoir,
Biography, Autobiography, Essay, Travel writing, History, Philosophy, Religion and spirituality, Popular science,
Self-help, Business, True crime, Graphic novel.

The book-brief screen SHALL also offer, in the same card: a register choice of `Formal / literary`, `Neutral` or
`Casual`, defaulting to `Neutral`; a free-text narrative voice / era field; and a free-text audience field. The
genre, voice / era and audience fields SHALL start empty.

WHERE a predefined genre is chosen, the run SHALL receive its English name whatever the interface language; WHERE
free text is entered, the run SHALL receive that text as written.

**Source:** FR-BRIEF-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`.
In plain words: these fields tell the model what kind of book it is translating — a Gothic novel for adults reads
differently from a children's adventure — and that shapes every sentence. A list helps people name a genre quickly,
but books do not fit lists, so free text is allowed. Forty is a fixed count, not an estimate, because a fixed
catalogue is what a completeness test can check and "about forty" is not. The prompt is written in English, so a
chosen genre reaches it in English even when the interface shows it in Ukrainian.

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

### Requirement: Capture the translation policies

The book-brief screen SHALL offer, in its Translation policies card: a character-and-place-name policy of
`Translate`, `Transliterate` or `Keep original`, defaulting to `Transliterate`; a foreign-language-passage policy of
`Keep as-is`, `Translate` or `Translate + note`, defaulting to `Keep as-is`; a footnote policy of `Translate` or
`Keep`, defaulting to `Translate`; a unit policy of `Keep` or `Metric`, defaulting to `Keep`; and a faithful ↔
natural slider from 0 to 100, defaulting to 55, labelled `literal · balanced · free`.

**Source:** FR-BRIEF-03, FR-BRIEF-04, FR-BRIEF-05, FR-BRIEF-06, FR-BRIEF-07
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), DD-26
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`.
In plain words: these are the decisions a human translator makes once per book — do the names stay English, get
spelled in the target alphabet, or get translated; does a Latin epigraph stay Latin; are footnotes translated; do
miles become kilometres; how close to the original wording should the translation stay. The defaults are the safe
choices for most fiction: names transliterated, foreign passages kept foreign, footnotes translated, units kept,
and a balance just past the middle towards natural.

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

### Requirement: Choose which auxiliary text is also translated

The book-brief screen SHALL offer, in its Also translate card, four switches: `ToC / navigation labels` (on by
default), `Image alt-text` (on by default), `Book metadata (title/author)` (on by default) and `Frontmatter values`
(off by default).

The `ToC / navigation labels` switch SHALL also govern each page's own title, wherever the format carries one, so
that switching it off leaves both the navigation labels and every page title in the source language. The
`Book metadata (title/author)` switch SHALL also govern the book's description, where the format carries one, so
that switching it off leaves the description in the source language along with the title and author.

**Source:** FR-BRIEF-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-11
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), DD-47
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`.
In plain words: besides the chapters, a book carries text a reader still sees — the table of contents, image
descriptions read aloud by screen readers, the title on the library shelf. Each is translated by default except
Markdown frontmatter values, which often hold copyright and edition notes that should stay as written. A page's own
title reads like a navigation label — it is the same kind of short wayfinding text — and a description reads like the
title and author it sits beside, so each rides along with the switch it resembles instead of the mockup growing two
more toggles it does not draw (see design).

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

### Requirement: Choose the quality dial and show what it turns on

The book-brief screen SHALL offer, in its Quality vs speed card, a dial of `Fast`, `Balanced` or `Max`, defaulting
to `Balanced`, and SHALL show beneath it a one-line hint naming what the chosen position turns on:

- `Fast: chunks of up to 8 segments · AI reviewer off · backward-consistency pass off`
- `Balanced: chunks of up to 8 segments · AI reviewer on · backward-consistency pass off`
- `Max: chunks of up to 2 segments · AI reviewer on · backward-consistency pass on`

The card SHALL show a model row naming the provider and the model the run will use, with a `change` link that opens
the Settings screen; that row SHALL NOT carry a readiness badge.

The screen SHALL NOT offer a review-mode choice; the review mode is decided when the application is launched.

**Source:** FR-BRIEF-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), DD-45
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-45-acceptance-model`),
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, ADR-0036
(`docs/adr/ADR-0036-review-modes-pause-the-run.md`).
In plain words: the dial trades speed for care, and a person can only make that trade knowingly if the screen says
what each position costs — a reviewer pass per chunk (two on Max), a whole-book consistency pass. The model row is shown because the
model is the other half of the quality question, and the link keeps the one place it is changed in Settings, so this
row cannot drift from that choice. It carries no readiness badge because that would mean keeping a verification
result between checks, which this build does not do; `app-shell`'s "Match the reference rendering" states this as a
deviation from the reference rendering, alongside the sidebar's own readiness dot. The review mode — when the run
stops for the person — is a preference about how they like to work, not a property of the book, so it does not
belong here.

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

### Requirement: Carry every brief choice into the run

WHEN a run is started, the system SHALL build the instructions sent to the model from the brief as it then stands —
the source and target languages, genre, register, narrative voice / era, audience, the four policies, the faithful ↔
natural balance and the quality dial — and SHALL send those instructions with every translation request of the run.

WHILE an "Also translate" switch is off, the run SHALL NOT translate the auxiliary text of that kind, and the
exported book SHALL keep that text exactly as it was in the source language.

WHILE a book stays open, the brief's choices SHALL be kept for it when the person moves to another screen and back.

**Source:** FR-BRIEF-01..09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-11
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, DD-47
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`).
In plain words: until now every control on this screen except the target language was drawn and ignored. A brief
that does not reach the model is decoration, so this is the requirement that makes the screen mean something: what
the person set is what the model is told, on every call, and a switch that says "do not translate the table of
contents" leaves the table of contents alone.

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

### Requirement: Keep the destination off the brief

The book-brief screen SHALL NOT offer a destination path, a Browse action or an overwrite switch; where the
translated book is written SHALL be chosen on the Export screen.

**Source:** FR-EXPORT-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-export`, ADR-0035
(`docs/adr/ADR-0035-export-is-a-separate-action.md`).
In plain words: a run no longer writes the book — exporting is its own action that can happen at any time, even
halfway through — so asking where to save before the translation even starts asks the question at the wrong moment.

#### Scenario: The brief has no destination card

- **WHEN** the brief is shown for an open book
- **THEN** no `Where to save` card, path field, `Browse…` button or overwrite switch is present

### Requirement: Wrap segmented choices rather than truncating them

WHERE a label of a segmented choice on the brief does not fit its segment, the screen SHALL wrap the label onto a
further line or widen the segment, and SHALL NOT cut the label short with an ellipsis.

**Source:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`,
`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#internationalization`, `docs/next_features.md` §15
("Book Brief segmented buttons truncate Ukrainian labels at 1024 px").
In plain words: Ukrainian words are longer than English ones, and the hand test found the name policy reading
"Транслітерув…" and "Залишати оригі…" in a 1024-pixel window — a choice whose label cannot be read cannot be chosen
with confidence.

#### Scenario: Ukrainian policy labels are readable at 1024 px

- **WHEN** the interface is Ukrainian and the window is 1024 pixels wide
- **THEN** the name-policy labels `Транслітерувати` and `Залишати оригінал` are shown in full, with no ellipsis

### Requirement: Say when the target language has no tested translation rules

The Book Brief SHALL show, under the target language box, a note stating that the language's translation rules are not
tested yet and that the general rules are used, with a hover explanation, in the interface language, for any target whose
language-rules file is not marked `status=tested` or does not exist, and SHALL show no note for a tested language or while
no target is chosen. The note SHALL never block continuing.

**Source:** `tasks.md` 15d.5b. In plain words: a language written for with care translates better than one with only
general advice, and a person should know which one they picked.

#### Scenario: A language without tested rules

- **WHEN** the target is German and German's rules are untested
- **THEN** the note is shown and Continue stays available

#### Scenario: A tested language

- **WHEN** the target is Ukrainian
- **THEN** no note is shown

### Requirement: Capture who narrates the book

The book-brief screen SHALL offer, in its Tone & style card, a narrator choice of `Not stated`, `First person` or `Third
person`, defaulting to `Not stated`, and a narrator-gender choice of `Not stated`, `Male` or `Female`, defaulting to
`Not stated` and available only while the narrator is in the first person. Each control SHALL explain itself on hover.
The brief SHALL hold the choice as `BookBrief.narrator` (the person and the gender); a brief made before the narrator
existed SHALL hold the unspecified narrator and read as it did, and every other change of the brief SHALL keep the
narrator it holds.

**Source:** FR-BRIEF-02, ADR-0038; tasks 15d.10.
In plain words: a first-person narrator's past-tense verbs show their gender in Ukrainian and many other languages
(`я зачинив` / `я зачинила`), and the source gives no hint of it, so the person says it once and every call and check
uses it.

#### Scenario: A new book starts with no narrator stated

- **WHEN** a book is opened and the brief is shown
- **THEN** the narrator choice is `Not stated` and the gender choice is not available

#### Scenario: A first-person female narrator reaches the brief

- **WHEN** the person chooses `First person` and then `Female`
- **THEN** the brief holds a first-person female narrator and the gender choice is available

#### Scenario: Another change keeps the narrator

- **WHEN** a first-person male narrator is set and the register, the genre and the dial are then changed
- **THEN** the brief still holds the first-person male narrator

## REMOVED Requirements

### Requirement: Choose the target language and show the source the book declares

**Reason**: The source language is no longer shown read-only. ADR-0037 makes the declared language only a starting point: the source is now preselected from the normalized declaration and editable, and a book that declares nothing starts with no source and cannot continue until one is chosen. The requirement's clause "without letting it be edited" and its scenario "The source language is shown but not editable" are now false, and "A book that declares nothing still gets a target" no longer describes a brief that can proceed.

**Migration**: Replaced by *Choose the source and target languages*, together with *Offer one searchable list of languages for source and target* and *Refuse a brief whose source and target are the same language*.

### Requirement: Show the rest of the brief and make none of it available

**Reason**: Every choice on the brief is now live and read by the run: tone and style, the four policies, the
faithful ↔ natural balance, the "Also translate" switches and the quality dial all shape the instructions sent to
the model, and the model row is shown with a link to Settings. The requirement's clauses — that these controls are
unavailable, that no run reads them, and that the model row is not repeated — are all now false.

**Migration**: Replaced by *Capture the tone and style of the translation*, *Capture the translation policies*,
*Choose which auxiliary text is also translated*, *Choose the quality dial and show what it turns on* and *Carry
every brief choice into the run*.
