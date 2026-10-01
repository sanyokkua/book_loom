# Spec Delta

## Purpose

The book's names and terms: proposed by a scan, settled by the person on the Names & style screen, carried between
books as CSV, given to the model only where they occur, and — when locked — rendered exactly as entered, so that a
character keeps one spelling from the first chapter to the last.

## ADDED Requirements

### Requirement: Propose names by a deterministic scan when the glossary is empty

WHEN the Names & style screen opens for a book whose glossary is empty, or a run prepares a book whose glossary is
empty, the application SHALL propose, without any network call, every capitalised word and every run of two or three
capitalised words that occurs at least 3 times not at the start of a sentence, ordered by how often it occurs and then
by where it first occurs, each as an entry of type other, gender unknown, unlocked and with no target, under these
rules:

- a sentence starts after `.`, `!`, `?` or `…`, and also after a dash (`—`) or a quotation mark; a word that follows
  `Mr.`, `Mrs.`, `Ms.`, `Dr.`, `St.` or `Prof.` SHALL NOT count as the start of a sentence, and the title itself SHALL
  NOT be proposed;
- a capitalised word at the start of a sentence SHALL count, and SHALL start a run, WHEN the book writes it with a
  capital mid-sentence at least 2 times;
- an apostrophe or a hyphen between letters keeps one word (`Don’t`, `O'Brien`, `Al-Arish`), and a trailing possessive
  `’s` or `'s` is not part of the word;
- a word on the bundled stop-word list of the book's source language (English when the language has no list or is not
  known) SHALL NOT be proposed and SHALL NOT start or join a run;
- a single word that the book writes in lower case at least 20% of the times it occurs SHALL NOT be proposed;
- a single word that occurs on its own less than 40% of the times it occurs alone or inside a proposed longer run SHALL
  NOT be proposed, since it is part of that longer name;
- only running text SHALL be read — a paragraph, a verse line, a list item, a table cell, a footnote or a caption —
  never a heading, a title, metadata or alt text; within it, a line of at most 10 words whose every word holding a
  letter starts with a capital, apart from `a`, `an`, `and`, `as`, `at`, `by`, `for`, `from`, `in`, `of`, `on`, `or`,
  `the`, `to` and `with`, is a title line and SHALL NOT be read; neither SHALL a block or an inline run that declares
  a language other than the source language;
- a single word that is a spelled-out number or ordinal (the bundled list of the source language, English when it has
  none) or a language's name as the JDK writes it in the source language (`Latin`, `French`) SHALL NOT be proposed on
  its own, though it may open a longer run.

**Source:** FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#name-term-pre-scan` (offline fallback),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`.
In plain words: a name that recurs mid-sentence is almost certainly a proper noun, so a frequency count finds most of
a book's names instantly and offline; words at the start of a sentence are ignored because every sentence starts
with a capital, and the full stop after an abbreviated title does not end a sentence, so `Hale` in `Mr. Hale` still
counts. Proposals stay unlocked because a locked term cannot inflect. A term the person removed is never
proposed again (see "Keep the person's entries and removals when names are proposed"). A real book (Jonathan Stroud's
"The Amulet of Samarkand") showed what a bare count gets wrong: `—Well,` and `"Don’t` open speech without a full stop,
`Words`, `Seal` and `Shield` are common nouns given a capital, `Al-Arish` was cut into `Al` and `Arish`, and `Simon` was
proposed beside `Simon Lovelace`. Every real name measured there is written in lower case less than 20% of the time,
and every one of those common words at least 25% of the time, which is a test that works in any language with
capitals; the stop-word list catches the function words and interjections a book rarely writes in lower case.

#### Scenario: Common words, contractions and parts of longer names are not proposed

- **WHEN** the glossary is empty and the book has `He paused—Well, perhaps not.` 3 times and `well` 3 times in lower
  case, `"Don’t go."` 3 times and `don’t` 4 times, `the Words` 3 times and `words` twice, `the Seal` 3 times and `seal`
  twice, `a Shield` 3 times and `shield` once, `the road to Al-Arish` 3 times, `Simon Lovelace` 6 times mid-sentence,
  `Lovelace` alone 6 times and `Simon` alone 3 times
- **THEN** `Al-Arish`, `Simon Lovelace` and `Lovelace` are proposed
- **AND** `Well`, `Don’t`, `Words`, `Seal`, `Shield`, `Al`, `Arish` and `Simon` are not

#### Scenario: A name that opens sentences is counted there too

- **WHEN** the book has `They visited Sholto Pinn at his shop.` 4 times and `Sholto Pinn bowed low.` 3 times
- **THEN** `Sholto Pinn` is proposed with a count of 7, and neither `Sholto` nor `Pinn` is proposed on its own

#### Scenario: The stop words are the book's language's

- **WHEN** the book's source language is `uk` and it has `Він сказав Так тихо.` 3 times
- **THEN** `Так` is not proposed
- **AND** with the source language `en` the same text proposes `Так`

#### Scenario: Recurring mid-sentence names are proposed

- **WHEN** the glossary is empty and the book has `Hale` 5 times mid-sentence, `Baker Street` 3 times mid-sentence
  and `Moreau` twice
- **THEN** `Hale` and then `Baker Street` are proposed with type other, gender unknown, unlocked and no target
- **AND** `Moreau` is not proposed

#### Scenario: Sentence-initial capitals are ignored

- **WHEN** `The` begins 40 sentences and never occurs capitalised mid-sentence
- **THEN** `The` is not proposed

#### Scenario: A name after an honorific is not sentence-initial

- **WHEN** the glossary is empty and the book has `Mr. Hale` 3 times mid-text
- **THEN** `Hale` is proposed and `Mr` is never proposed

#### Scenario: The scan needs no provider

- **WHEN** no provider is reachable and Names & style opens with an empty glossary
- **THEN** the proposals appear and no network call is made

#### Scenario: A non-empty glossary is not rescanned

- **WHEN** the glossary already holds the person's entry `Hale` → `Гейл` and a run prepares
- **THEN** preparation runs no scan and leaves the glossary holding exactly that entry

### Requirement: Run the model scan only when the person asks for it

WHEN the person presses the model scan button on Names & style, the application SHALL send the model every capitalised
word and every run of two or three capitalised words that occurs at least once not at the start of a sentence, read and
filtered by the deterministic scan's rules (running text only, no title line, no foreign block or run, no stop word, no
number or language name alone, no word mostly written in lower case), each with the first sentence that holds it, 40
candidates per call; SHALL read each proposed type as person → character, place → place, org → other, term → term and
anything else → other, and each gender other than female, male or neuter as unknown; SHALL then, in the same action,
send every proposal the glossary does not hold and the person has not removed to the verdict step of "Review the
glossary with the model when the person asks" — the same prompt, evidence and batches — and keep only a proposal it
calls a name or a term, taking the verdict's type and gender where the proposal's were other and unknown; and SHALL
write the kept proposals into the glossary only after every call has answered, matching terms by their glossary
key (see "Compare glossary terms by one key"), keeping any existing entry unchanged and adding each new term unlocked
with no target. IF any call fails, THEN it SHALL write nothing, keep the existing entries and show the provider's error
in place. Each call SHALL be announced, timed and attempt-counted as a run's model calls are, the screen SHALL show
which request it is waiting on, and WHEN the person presses Stop the request in flight SHALL be interrupted and
nothing written. The application SHALL NOT run the model scan on its own, when a run starts included.

**Source:** FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#name-term-pre-scan`, DD-46
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`),
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`.
In plain words: the model finds names a frequency count misses, and it can guess type and gender, but it costs time
and uses the network, so it only runs on request — the reference's automatic scan at run start is not built. Each name
travels with one sentence of context, so a small model can tell a person from a place, and in batches small enough for
a local model's context. The model's vocabulary is mapped onto the table's; a failure half-way must not leave half a
merge or wipe what the person already has. On the fixture book a small model kept 17 of 28 candidates, among them
chapter-title words (`Gravity Formula`, `Long Night`), spelled-out numbers (`Six`, `Seven`) and language names
(`Latin`, `French`), and every one became a row: candidates now come from running text only, and every proposal goes
through the same verdict the review gives, which sees all the book's uses of a term rather than one sentence, before
anything is written.

#### Scenario: The verdict step drops what the proposal stage kept wrongly

- **WHEN** the model scan runs on the fixture book `earth-gravity.md`, the model proposes every candidate, and the
  verdict step calls only the names a name
- **THEN** the glossary gains exactly `Eleanor Vance`, `Vance`, `Nell`, `Tomas`, `Reyes`, `Harrow Vale`,
  `Meridian Survey Institute` and `Amulet`

#### Scenario: Title words, numbers and languages are never candidates

- **WHEN** the fixture book is scanned in any of its four formats
- **THEN** no candidate is `Gravity Formula`, `Harrow Vale Expedition`, `Six`, `Seven`, `Long Night`, `Songs`,
  `Falling Things`, `Earth Gravity`, `Practical Introduction`, `Latin`, `French` or `Plumb Line`

#### Scenario: Model proposals merge without overwriting

- **WHEN** the glossary holds `Hale` → `Гейл` (character, male, locked) and the model proposes `hale` (other) and
  `Milton` (place)
- **THEN** the glossary holds `Hale` → `Гейл` unchanged and a new unlocked entry `Milton` of type place with no target

#### Scenario: Candidates travel in batches with their sentence

- **WHEN** the book holds 95 candidate names and the person presses the model scan button
- **THEN** 3 model calls are made, holding 40, 40 and 15 candidates, each candidate with the first sentence that holds
  it

#### Scenario: Proposed types and genders are mapped

- **WHEN** the model answers
  `{"terms":[{"term":"Hale","type":"person","gender":"male"},{"term":"East India Company","type":"org","gender":"unknown"},{"term":"Milton","type":"place","gender":"n/a"}]}`
- **THEN** the glossary gains `Hale` (character, male), `East India Company` (other, unknown) and `Milton` (place,
  unknown), each unlocked with no target

#### Scenario: A failed batch writes nothing

- **WHEN** the glossary holds the scanned entries `Hale` and `Baker Street`, and the second of three calls fails with
  `ErrorCode.unreachable`
- **THEN** both entries remain, nothing the first call proposed is added, and the screen shows the provider-unreachable
  message in place

#### Scenario: Nothing is sent until the button is pressed

- **WHEN** Names & style opens and the person never presses the model scan button
- **THEN** no model call is made for the glossary

#### Scenario: A run never starts the model scan

- **WHEN** a run prepares a book whose glossary is empty
- **THEN** the deterministic scan proposes the names and no model scan call is made

### Requirement: Show the glossary as an editable table on Names & style

The Names & style screen SHALL show the glossary as a table with the columns Source term, Type (character, place,
term, title or other), Target (editable), Gender (female, male, neuter or unknown) and a Locked switch, an info banner
reading `Skip this and the app builds names on the fly as it translates.`, and a large primary Start translation
button; WHILE no book is open, it SHALL instead report that no book is open with a route to the import screen, and SHALL
show no table, no scan and no Start translation.

WHEN Start translation is pressed while no start is offered — the project's run is running, paused, stopped or in the
provider-error state, or it completed with nothing pending — the application SHALL start nothing and SHALL show the
translating screen, and the button SHALL keep its label `Start translation`.

**Source:** FR-GLOSS-02, FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `#empty-states`,
`docs/specification/mockups/ui-mockup.html` (Names & style).
In plain words: this is where the person settles spellings before the run starts — optional in automatic mode, which the
banner says — and the run can be started from here without going back. The banner is true: the run adds the names it
meets at the end of each chapter (the translation-pipeline capability's "Propose new names at the end of each body
unit"). Like the brief, the structure and the export screens, this screen is built out of an open book, so with none
open it says so instead of showing an empty table. When a start is offered is app-shell's "Move through the workflow by
its available steps"; while a run can be resumed the button leads to it rather than beginning a second run, and it keeps
its name so the screen does not change under the person's hand.

#### Scenario: A scanned entry appears unlocked

- **WHEN** the scan proposed `Hale`
- **THEN** its row shows Source term `Hale`, Type `other`, an empty editable Target, Gender `unknown` and the Locked
  switch off

#### Scenario: Editing a target and locking it

- **WHEN** the person types `Гейл` into the Target of `Hale` and turns the Locked switch on
- **THEN** the glossary entry `Hale` reads target `Гейл`, locked

#### Scenario: An empty glossary still offers the start

- **WHEN** the book produced no proposals
- **THEN** the table is empty, the banner is shown and Start translation is enabled

#### Scenario: The screen with no book open

- **WHEN** Names & style is opened and no book has been opened
- **THEN** it reports that no book is open and offers a route to the import screen
- **AND** no glossary table, no scan button and no Start translation is shown

#### Scenario: Start translation during a paused run shows it

- **WHEN** the run over `Frankenstein.epub` is paused at 55% and the person presses Start translation on Names & style
- **THEN** the translating screen shows the paused run offering Resume, and no new run begins
- **AND** the button on Names & style still reads `Start translation`

### Requirement: Review the glossary with the model when the person asks

WHEN the person presses Review with model on Names & style, the application SHALL send the chosen model every
unlocked entry with no target, 40 per call, each with how many times the book uses it in any case and up to two
sentences that hold it, and SHALL ask for each a verdict — name, term or not a name — with a type (person, place, org,
term, title or other) and a gender (male, female, neuter or unknown) under a strict schema. Only after every call has
answered, and against each entry as it then stands, the application SHALL remove through the glossary (so the removal is
remembered) each entry judged not a name whose type is still other and gender still unknown, and SHALL set the type of
an entry still of type other and the gender of an entry still of gender unknown to the model's guess; it SHALL NOT
change a locked entry or one with a target, and SHALL then show the glossary as it stands and the line
`Model review done: N rows removed, M rows updated.` IF any call fails or the person presses Stop, THEN it SHALL
change nothing. The calls SHALL be announced, timed, attempt-counted and stoppable as the model scan's are, and SHALL
NOT be sent unless the person presses the button.

**Source:** FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-review`, DD-46
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`).
In plain words: a frequency count cannot tell a sentence-opening interjection from a name in every case, and it never
knows a type or a gender, so the model is asked to clean and classify the list. It sees how often the book uses each
word in lower case too, which is the clearest sign of a common word. A set type means someone — the person or an earlier
model scan — already judged the entry a name, so only an untouched row is removed; and a row the person locked or gave
a target is theirs and is never sent.

#### Scenario: The review removes a common word and classifies a name

- **WHEN** the glossary holds `Well` and `Hale` (other, unknown), `Moreau` (character, unknown), `Milton` → `Мілтон`
  locked and `Baker Street` → `Бейкер-стріт`, and the model answers `Well` not a name, `Hale` a name (person, male),
  `Moreau` a name (place, female), and `Milton` and `Baker Street` not a name
- **THEN** `Well` is removed and remembered as removed, `Hale` reads character, male, `Moreau` reads character, female,
  and `Milton` and `Baker Street` are unchanged
- **AND** only `Well`, `Hale` and `Moreau` were sent, and the line reads `Model review done: 1 row removed, 2 rows
  updated.`

#### Scenario: A term locked while the model thinks is left alone

- **WHEN** the person locks `Well` with the target `Ну` while the review's call is out, and the model judges `Well`
  not a name
- **THEN** `Well` → `Ну` stays, locked

#### Scenario: A failed or stopped review changes nothing

- **WHEN** the glossary holds 41 open entries and the second call fails with `ErrorCode.unreachable`, or the person
  presses Stop while a call is out
- **THEN** the glossary holds the same 41 entries as before

### Requirement: Compare glossary terms by one key

The application SHALL compare glossary terms — for a duplicate in the repository, the Add term dialog and a CSV import,
for a held or removed term in every scan, and for an entry's id — by one key: the term in Unicode NFC, with leading and
trailing punctuation and a trailing possessive `’s` or `'s` removed, inner spaces collapsed to one, and the case folded.

**Source:** FR-GLOSS-02, FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`).
In plain words: before, each way in compared differently, so `Lovelace’s` could be held beside `Lovelace` and a removed
name could come back in another spelling.

#### Scenario: Spellings of one term share a key

- **WHEN** the glossary holds `Hale` and `Hale's`, `Hale’s`, ` HALE ` or `"Hale,"` is added
- **THEN** the addition is refused as a duplicate, and a removed `Hale` is still removed when `Hale’s` is proposed

### Requirement: Sort and search the glossary table

The Names & style table SHALL sort by Source term, Type, Gender or Locked when the person chooses that column's header,
breaking every tie by the source term, so that storing a change to any other column — a typed target above all — never
moves its row; and it SHALL offer a search field that shows only the rows whose source term or target contains the
typed text, ignoring case, and that the Escape key clears.

**Source:** FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`.
In plain words: a real book yields a hundred rows or more, in the order the scan found them; a person settling them
needs to find one name and group the rows by kind. The glossary keeps no count of mentions, so there is no count column.

#### Scenario: Sorting by type keeps one type's rows in term order

- **WHEN** the table holds `Zeta` (place), `Alpha` (character), `Mid` and `Beta` (other) and the person sorts by Type
- **THEN** the rows read `Alpha`, `Zeta`, `Beta`, `Mid`, and storing a target for `Beta` leaves that order

#### Scenario: Searching by a target

- **WHEN** `Mid` has the target `Мід` and the person types `мід` into the search field
- **THEN** only `Mid` is shown, and pressing Escape clears the field and shows every row again

### Requirement: Lock only an entry that has a target

IF a change would leave a glossary entry locked with an empty target — turning the Locked switch on in the table,
emptying a locked entry's target, confirming the Add term dialog with the lock on and no target, or importing a CSV
row that is locked with no target — THEN the application SHALL refuse that change with the message
`A locked term needs a target.` and SHALL leave the glossary as it was.

**Source:** FR-GLOSS-03, FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `#dialog-add-glossary-term`.
In plain words: a locked term is replaced by its target wherever it occurs; with no target there is nothing to put
back, so the name would vanish from the book. Every way into the glossary refuses such an entry rather than one of
them letting it through.

#### Scenario: Locking a term with no target is refused in the table

- **WHEN** the row `Hale` has an empty Target and the person turns its Locked switch on
- **THEN** the switch stays off, `A locked term needs a target.` is shown, and `Hale` stays unlocked

#### Scenario: Emptying a locked term's target is refused

- **WHEN** `Hale` → `Гейл` is locked and the person clears its Target
- **THEN** the target stays `Гейл` and `A locked term needs a target.` is shown

#### Scenario: The Add term dialog refuses a locked term with no target

- **WHEN** the person confirms Add term with `Justine`, no target, character, female and the lock on
- **THEN** the dialog stays open with `A locked term needs a target.` and nothing is added

#### Scenario: A CSV row locked with no target is refused

- **WHEN** an imported file has the header and the line 2 `Hale,,character,male,true`
- **THEN** line 2 is reported as refused because a locked term needs a target, and no `Hale` entry is added

### Requirement: Add a term through a dialog that refuses duplicates

WHEN the person confirms the Add term dialog with a source term, a target, a type, a gender and the lock switch, the
application SHALL add that entry, and IF the source term has the glossary key of an existing entry (see "Compare
glossary terms by one key"), THEN it SHALL refuse the addition and say so in the dialog.

**Source:** FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-add-glossary-term`.
In plain words: a name the scan missed can be added by hand; two entries for the same name would contradict each other,
so the second is refused rather than silently merged. A locked term also needs a target (see "Lock only an entry that
has a target").

#### Scenario: A new term is added

- **WHEN** the person adds `Justine` → `Жустіна`, character, female, locked
- **THEN** the table shows `Justine` with those values

#### Scenario: A duplicate differing only in case is refused

- **WHEN** `Justine` exists and the person adds `justine` → `Юстина`
- **THEN** the dialog stays open with a message that `justine` is already in the glossary and nothing is added

#### Scenario: A possessive of a held term is refused

- **WHEN** `Justine` exists and the person adds `Justine’s`
- **THEN** the dialog says `Justine’s` is already in the glossary and nothing is added

### Requirement: Import and export the glossary as CSV

The application SHALL export the glossary as UTF-8 CSV with the header `term,target,type,gender,locked` and quoting as
RFC 4180 defines it, and WHEN a CSV is imported it SHALL update an existing entry matched by source term, add the
others, and report each malformed or refused row by its line number while importing the rest.

**Source:** FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`.
In plain words: a series keeps its names across books, so a glossary must travel as a plain file any spreadsheet can
open; one bad line must not throw away a hundred good ones. A row that would lock a term with no target is refused
like a malformed one (see "Lock only an entry that has a target").

#### Scenario: A term with a comma is quoted

- **WHEN** the glossary holds `Hale, Margaret` → `Гейл, Маргарет`, character, female, locked
- **THEN** the exported line is `"Hale, Margaret","Гейл, Маргарет",character,female,true`

#### Scenario: A malformed row is reported and skipped

- **WHEN** an imported file has the header, a valid line 2 `Hale,Гейл,character,male,true`, a line 3 `Milton,Мілтон`
  with two fields, and a valid line 4
- **THEN** lines 2 and 4 are imported and line 3 is reported as malformed

#### Scenario: Import updates an existing term

- **WHEN** the glossary holds `Hale` → `Хейл` and an imported line reads `Hale,Гейл,character,male,true`
- **THEN** the glossary holds one `Hale` entry with target `Гейл`, locked

### Requirement: Keep the person's entries and removals when names are proposed

WHEN a scan — the deterministic scan on preparing a run, on opening Names & style or at the end of a chapter, or the
model scan — finds a term the glossary already holds by its glossary key, the application SHALL keep the existing entry
unchanged; and the application SHALL NOT let any scan, deterministic or model, propose a term the person removed in this
session, until the person adds that term again through Add term or a CSV import.

**Source:** FR-GLOSS-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), FR-ALGO-C9
(`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, ADR-0034.
In plain words: the glossary grows while the book translates — at the end of each chapter the run adds the names it has
met (the translation-pipeline capability's "Propose new names at the end of each body unit") — and none of that growth
may undo the person's work: a spelling they settled stays, and a false name they deleted does not come back at the next
chapter. Adding a found name never costs the chapter's stored decisions either. The model scan, pressed after a removal,
honours it the same way: a name the model proposes again is still a name the person deleted (see "Run the model scan
only when the person asks for it").

#### Scenario: A chapter-end scan leaves the person's entry alone

- **WHEN** the person added `Hale` → `Гейл` (character, male, locked) during a pause, and at the end of `ch03.xhtml` the
  scan finds `Hale` 6 times mid-sentence
- **THEN** the entry still reads `Hale` → `Гейл`, character, male, locked, and no second `Hale` entry exists
- **AND** the decided segments of `ch03.xhtml` are stored

#### Scenario: A removed term is not proposed again

- **WHEN** the person removed the proposal `Chapter`, and the scan at the end of `ch05.xhtml` finds `Chapter` 7 times
  mid-sentence
- **THEN** `Chapter` is not added to the glossary

#### Scenario: The model scan does not bring a removal back

- **WHEN** the person removed the proposal `Chapter`, presses the model scan button, and the model proposes `Chapter`
  (term) and `Milton` (place)
- **THEN** the glossary gains an unlocked entry `Milton` of type place with no target
- **AND** `Chapter` is not added to the glossary

#### Scenario: Reopening Names & style does not bring removals back

- **WHEN** the person removed every proposed entry, leaving the glossary empty, and opens Names & style again
- **THEN** the scan runs and proposes none of the removed terms

#### Scenario: The person can add a removed term back

- **WHEN** the person removed `Moreau` and then imports a CSV holding the line `Moreau,Моро,character,male,false`
- **THEN** the glossary holds `Moreau` → `Моро`, character, male, unlocked

### Requirement: Render a locked term exactly as entered

WHILE a glossary entry is locked, the application SHALL hide each whole-word occurrence of its source term behind a
placeholder before the segment reaches the model and SHALL restore it with the entry's target rendering, never
matching the term inside a longer word or inside another placeholder.

**Source:** FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), EC-INLINE-3
(`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`.
In plain words: the model never sees a locked name, so it cannot misspell it; matching whole words only keeps `Hale`
from being substituted inside `Whale`. A model that drops the placeholder fails the protected-span hard gate (see the
quality-gates capability's "Treat each protected span's placeholder as a hard gate").

#### Scenario: A locked name is restored exactly

- **WHEN** `Hale` → `Гейл` is locked and the segment is `Hale opened the door.`
- **THEN** the model receives the name as a placeholder and the accepted target contains `Гейл`

#### Scenario: A longer word is left alone

- **WHEN** `Hale` is locked and the segment is `A whale and a Whale-boat.`
- **THEN** no placeholder is inserted for `Hale`

#### Scenario: An unlocked entry is not masked

- **WHEN** `Milton` → `Мілтон` is unlocked and the segment is `They left Milton.`
- **THEN** the model receives `Milton` as text

### Requirement: Give the model only the terms that occur in the chunk

WHEN a chunk is sent to the model, the application SHALL include only the glossary entries whose source term occurs as
a whole word in that chunk, each with its type and gender: an unlocked entry with its target rendering as guidance, an
entry with no target by its type and gender alone, and a locked entry — which the model sees only as a placeholder —
as that placeholder with its rendering, type and gender.

**Source:** FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`.
In plain words: a small model's context is precious, so a two-hundred-entry glossary is trimmed to the names in play;
type and gender let the model agree verbs and adjectives with a character in languages that inflect for them. A
locked name is hidden behind a placeholder, so the model is told what that placeholder will read as — otherwise it could
not make `відчинив` or `відчинила` agree with it.

#### Scenario: Only present terms are given

- **WHEN** the glossary holds `Hale`, `Milton` and `Baker Street` and the chunk mentions only `Milton`
- **THEN** the prompt lists `Milton` → `Мілтон` (place, unknown) and neither `Hale` nor `Baker Street`

#### Scenario: A locked name is listed by its placeholder

- **WHEN** `Hale` → `Гейл` (character, male) is locked and the segment `Hale opened the door.` is sent as
  `⟦g0⟧ opened the door.`
- **THEN** the prompt lists `⟦g0⟧ → Гейл, character, male`

#### Scenario: An entry with no target gives type and gender only

- **WHEN** the glossary holds `Moreau` (character, male, no target) and the chunk mentions `Moreau`
- **THEN** the prompt lists `Moreau` with `character` and `male` and no rendering

#### Scenario: An empty glossary adds nothing

- **WHEN** the glossary is empty
- **THEN** the prompt carries no glossary terms

### Requirement: Apply glossary edits made during a pause from the next chunk

WHEN the person edits the glossary while a run is paused, the application SHALL apply the edit from the first chunk that
starts after the run resumes and SHALL NOT change segments already decided, except through backward revision on the Max
dial or the export's final consistency pass; the glossary SHALL NOT survive closing the application.

**Source:** FR-GLOSS-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), ADR-0034,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`.
In plain words: a person who spots a wrong spelling mid-run can fix it and resume, and the rest of the book follows;
earlier segments are corrected by the consistency pass or review, not silently. A chunk reads the glossary once, when it
starts, so the rest of a chunk paused in the middle keeps the glossary it began with. Nothing is saved to disk yet.

#### Scenario: A pause-time lock affects later chunks only

- **WHEN** the run is paused after chunk 12 and the person locks `Hale` → `Гейл`
- **THEN** chunk 13 onwards masks `Hale`, and the targets of chunks 1–12 are unchanged by the edit itself
- **AND** on a Balanced run with the export's consistency pass off, they stay unchanged in the written book

#### Scenario: A pause inside a chunk applies from the next chunk

- **WHEN** a Fast Assisted run pauses on the flagged `ch07.xhtml:41` inside the chunk `ch07.xhtml:40`–`ch07.xhtml:47`,
  and the person locks `Hale` → `Гейл`
- **THEN** `ch07.xhtml:42`–`ch07.xhtml:47` are drafted with `Hale` sent as text
- **AND** the chunk starting at `ch07.xhtml:48` sends `Hale` as a placeholder

#### Scenario: Closing the application forgets the glossary

- **WHEN** the person added `Justine` → `Жустіна`, closes the application and reopens the same book
- **THEN** the glossary does not contain `Justine`
