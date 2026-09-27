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
by where it first occurs, each as an entry of type other, gender unknown, unlocked and with no target.

**Source:** FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#name-term-pre-scan` (offline fallback),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`.
In plain words: a name that recurs mid-sentence is almost certainly a proper noun, so a frequency count finds most of
a book's names instantly and offline; words at the start of a sentence are ignored because every sentence starts
with a capital. Proposals stay unlocked because a locked term cannot inflect. A term the person removed is never
proposed again (see "Keep the person's entries and removals when names are proposed").

#### Scenario: Recurring mid-sentence names are proposed

- **WHEN** the glossary is empty and the book has `Hale` 5 times mid-sentence, `Baker Street` 3 times mid-sentence
  and `Moreau` twice
- **THEN** `Hale` and then `Baker Street` are proposed with type other, gender unknown, unlocked and no target
- **AND** `Moreau` is not proposed

#### Scenario: Sentence-initial capitals are ignored

- **WHEN** `The` begins 40 sentences and never occurs capitalised mid-sentence
- **THEN** `The` is not proposed

#### Scenario: The scan needs no provider

- **WHEN** no provider is reachable and Names & style opens with an empty glossary
- **THEN** the proposals appear and no network call is made

#### Scenario: A non-empty glossary is not rescanned

- **WHEN** the glossary already holds the person's entry `Hale` → `Гейл` and a run prepares
- **THEN** preparation runs no scan and leaves the glossary holding exactly that entry

### Requirement: Run the model scan only when the person asks for it

WHEN the person presses the model scan button on Names & style, the application SHALL send the model every capitalised
word and every run of two or three capitalised words that occurs at least once not at the start of a sentence, each
with the first sentence that holds it, 40 candidates per call; SHALL read each proposed type as person → character,
place → place, org → other, term → term and anything else → other, and each gender other than female, male or neuter
as unknown; and SHALL merge the proposals into the glossary only after every call has answered, matching terms
case-insensitively, keeping any existing entry unchanged and adding each new term unlocked with no target. IF any call
fails, THEN it SHALL write nothing, keep the existing entries and show the provider's error in place. The application
SHALL NOT run the model scan on its own, when a run starts included.

**Source:** FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#name-term-pre-scan`, DD-46
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`),
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`.
In plain words: the model finds names a frequency count misses, and it can guess type and gender, but it costs time
and uses the network, so it only runs on request — the reference's automatic scan at run start is not built. Each name
travels with one sentence of context, so a small model can tell a person from a place, and in batches small enough for
a local model's context. The model's vocabulary is mapped onto the table's; a failure half-way must not leave half a
merge or wipe what the person already has.

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
application SHALL add that entry, and IF the source term matches an existing entry ignoring case, THEN it SHALL refuse
the addition and say so in the dialog.

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
model scan — finds a term the glossary already holds, ignoring case, the application SHALL keep the existing entry
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
