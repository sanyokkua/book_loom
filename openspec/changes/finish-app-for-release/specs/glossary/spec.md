## ADDED Requirements

### Requirement: Take a character's gender only from the person or from evidence the code verifies

The application SHALL write a character's gender with the origin book only from windows a batched review cites in which the code itself reads the claimed pronoun for the name, or from five or more gendered past-tense verbs right after the name of which 90 % agree; SHALL keep a gender that rests on the readings' verified quotes alone as likely; and SHALL set no gender from a count of pronouns near the name, from a list of first names or from the way the book declines the name.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: a name row is followed almost always, so a wrong gender on it is repeated through the book; in the traced run a man was marked female because the code counted three "she" that belonged to a woman standing beside him, and the wrong value went into 13 requests. A count cannot tell whose pronoun it is, so it no longer decides, confirms or overrules anything, and a first name or a declined form is a guess about the language, not a fact of the book. What the code verified before it still verifies: the windows a review cites, and the verb that agrees with the name. The quotes of the single-name readings alone give a likely gender, as they did before: it is shown to the person, never sent in a request and never enforced. Everything else stays "not specified", which is the honest value.

#### Scenario: A neighbour's pronouns set nothing

- **WHEN** the book writes `she` in the sentence after `Corwin` three times and `he` once, each `she` being a woman who stands beside him, and no reading of `Corwin` quotes a pronoun
- **THEN** the row `Corwin` has gender "not specified", carries no `rule` chip, and no request lists a gender for `Corwin`

#### Scenario: A first name sets nothing

- **WHEN** an English book holds the character row `Mirabel` and a run prepares it with no model reading
- **THEN** `Mirabel` has gender "not specified"

#### Scenario: A declined form sets nothing

- **WHEN** the source language is `ru`, the row `Велемир` holds the form `Велемира`, and no verb and no cited window shows a gender
- **THEN** `Велемир` has gender "not specified"

#### Scenario: Two verified quotes give a likely gender

- **WHEN** two readings of `Corwin` quote `He nodded` and `He turned away`, the code finds each quote in the narration of the window that reading cites, and no review and no verb says more
- **THEN** `Corwin` reads male, likely, and no request lists a gender for `Corwin`

#### Scenario: The windows a review cites make the gender the book's

- **WHEN** a batched review answers `Corwin` a person, male, citing windows 1 and 3, and in both the code reads `he` outside speech as the pronoun of `Corwin`
- **THEN** `Corwin` reads male with the origin book, and the requests that name him list `male`

#### Scenario: Five agreeing verbs make the gender the book's

- **WHEN** the source language is `uk` and the narration writes `Орися сказала`, `Орися пішла`, `Орися сіла`, `Орися встала` and `Орися глянула`, with no masculine verb after the name
- **THEN** `Орися` reads female with the origin book

### Requirement: Propose the book's titles and forms of address as glossary rows

WHEN the deterministic scan runs and the book's running text uses a word of the source language's bundled title list at least 3 times, the application SHALL propose it as one glossary row of kind title, in lower case and with no gender, whose target is the rendering the language pair's title data gives, pinned with the origin rule, or empty when that data does not list the word.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `research/review-digest.md#consistency-numbers`, `design.md#d16--titles-are-glossary-rows`. In plain words: one whole book rendered "Mr" as «містер» 83 times and as «пан» 12 times, and the rows that should have held it steady were never sent: they sat in a separate list as locked rows, and a locked row is shown to the model only when its word is hidden behind a token, which it never was (0 of 14 requests). As ordinary pinned glossary rows they are sent with every request that holds the word, and a pinned word may still decline («пане», «пана»). The list of titles is a data file any source language may have; a language with no file proposes none.

#### Scenario: Titles with pair data get their rendering

- **WHEN** an English book writes `Mr.` 4 times and `sir` 3 times and the target is Ukrainian, whose pair data lists `mr>пан` and `sir>сер`
- **THEN** the glossary gains the rows `mr` → `пан` and `sir` → `сер`, each of kind title, pinned, with the origin rule and no gender

#### Scenario: A title without pair data has no target

- **WHEN** the book writes `Captain` 3 times and the pair data lists no rendering for `captain`
- **THEN** the glossary gains the row `captain` of kind title with no target

#### Scenario: A rare title is not proposed

- **WHEN** the book writes `Mrs` twice
- **THEN** no row `mrs` is proposed

#### Scenario: A title row reaches the request

- **WHEN** the row `mr` → `пан` is pinned and a window's source holds `Mr. Hale`
- **THEN** the request lists `mr → пан (title)` and sends `Mr.` as written, with no placeholder

#### Scenario: A removed title stays removed

- **WHEN** the person removed the row `sir` and the scan runs again
- **THEN** `sir` is not proposed

#### Scenario: A language without a title list proposes none

- **WHEN** the source language has no bundled title list
- **THEN** no title row is proposed

### Requirement: Pin a name's rendering once the book agrees on it

WHEN the Translate stage of a run ends, the application SHALL, with no model call, pin with the origin book the rendering of each character, place or title row whose target is suggested, WHERE the row's source is named in at least 5 decided body paragraphs and at least 80 % of those that hold any rendering of it hold one rendering or a form of it; that rendering SHALL become the row's target.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#consistency-numbers`, `design.md#d17--the-consistency-pass-agreement-pinning-and-the-sweep`. In plain words: almost every name of a real run stays "suggested", and nothing holds the book to a suggestion: one whole book spelled its main character a second way in 14 of 637 paragraphs. Rows with a right rendering are kept in 98–100 % of paragraphs, while the rows that really are undecided sit at 58 % and 75 %, so 80 % separates the two groups; 5 paragraphs keep a rare name from being pinned on one or two uses. Both numbers are starting values, confirmed on whole books before they are kept (`design.md#d17--the-consistency-pass-agreement-pinning-and-the-sweep`). The book's own majority wins over the unconfirmed suggestion because it is what the reader will see in most places.

#### Scenario: A book that agrees pins the row

- **WHEN** `Hale` → `Гейл` is suggested, 20 decided body paragraphs name `Hale`, 19 hold `Гейл` or a form such as `Гейла`, and 1 holds `Хейл`
- **THEN** the row reads `Гейл`, pinned, with the origin book

#### Scenario: The book's rendering wins over the suggestion

- **WHEN** `Hale` → `Хейл` is suggested and 19 of the 20 paragraphs that name `Hale` hold `Гейл`
- **THEN** the row reads `Гейл`, pinned, with the origin book

#### Scenario: A split book pins nothing

- **WHEN** 26 paragraphs name the place `Velk`, 15 hold `Велк` and 11 hold `Вельк`
- **THEN** the row stays suggested (58 % is below 80 %)

#### Scenario: A rare name is not pinned

- **WHEN** 4 decided paragraphs name `Tobin` and all 4 hold `Тобін`
- **THEN** the row stays suggested

#### Scenario: The person's rows are not touched

- **WHEN** `Milton` → `Мілтон` is pinned by the person and 9 of 10 paragraphs hold `Мільтон`
- **THEN** the row still reads `Мілтон`, pinned, with the origin person

### Requirement: Fill a frequent row that has no target from the book's own rendering

WHEN the Translate stage of a run ends and a one-word character, place or title row with no target is named in at least 10 decided body paragraphs, the application SHALL, with no model call, give the row the rendering most of those paragraphs use: pinned with the origin book WHERE the paragraphs agree on it by the rule of "Pin a name's rendering once the book agrees on it", and as a suggested target otherwise.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#consistency-numbers`, `design.md#d17--the-consistency-pass-agreement-pinning-and-the-sweep`. In plain words: a row with no target tells the model only the kind of the word, so each paragraph spells the name afresh: a place with an empty row came out two ways, 23 and 4 times in one run and 15 and 11 in another. A row the book uses ten times or more must not stay empty; the book's own most used spelling is the cheapest right answer, and where the book is split the spelling is stored only as a hint for the next call and the review. Ten uses is the review's figure and a starting value, confirmed on whole books in the same way as the agreement bar.

#### Scenario: An agreed spelling is pinned

- **WHEN** the row `Harrowgate` has no target, 27 decided paragraphs name it, 23 hold `Гарроуґейт` and 4 hold `Харроугейт`
- **THEN** the row reads `Гарроуґейт`, pinned, with the origin book (85 %)

#### Scenario: A split spelling is stored as a hint

- **WHEN** the row `Velk` has no target, 26 decided paragraphs name it, 15 hold `Велк` and 11 hold `Вельк`
- **THEN** the row reads `Велк`, suggested

#### Scenario: Competing renderings are counted by the paragraphs that hold them

- **WHEN** the row `Velk` has no target and the 26 decided paragraphs that name it hold `Велк` or a form such as `Велка` in 15 and `Вельк` or a form of it in 11
- **THEN** each rendering is counted once for every paragraph that holds a form of it, `Велк` leads with 15 of 26 (58 %, below the 80 % bar), and the row reads `Велк`, suggested, not pinned
- **AND** the rendering report lists the row as split with the counts 15 and 11

#### Scenario: A row under ten uses stays empty

- **WHEN** the row `Tobin` has no target and 9 decided paragraphs name it
- **THEN** the row still has no target

#### Scenario: A name of several words is reported, not filled

- **WHEN** the row `Marrow Lane` has no target and 12 decided paragraphs name it
- **THEN** the row still has no target and the rendering report lists it as "no rendering"

### Requirement: Keep no list of recurring common words

The application SHALL keep no list of recurring common words beside the glossary and SHALL make no model call that judges whether a common word of the book is a term.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `research/review-digest.md#f13--about-40--of-the-run-time-buys-nothing-in-the-exported-book`, `research/review-digest.md#time-breakdown-of-the-traced-run`, `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: the list of "recurring terms" was filled with ordinary words (about 30 of 37 rows in the traced run), its rows changed nothing a reader could see, two rows were overwritten with wrong values during the run, and the model calls that judged those words cost 204 seconds, a tenth of the run, 58 of 60 of them answering nothing. A word the person wants held steady is added to the glossary by hand as a row of kind term, where it is sent and checked like any other row.

#### Scenario: A common word is neither listed nor asked about

- **WHEN** a book writes `Warden` with a capital mid-sentence 3 times and `warden` in lower case 9 times, and a run prepares it
- **THEN** no row and no list entry for `warden` is created, and no model call asks about `warden`

#### Scenario: Names & style shows one table

- **WHEN** Names & style opens for a project
- **THEN** it shows the glossary table and no Recurring terms card

#### Scenario: A repeated word is added by hand

- **WHEN** the person adds `warden` → `наглядач` with the kind term
- **THEN** the glossary holds the row, pinned, and each request whose window holds `warden` lists it

### Requirement: Treat every spelling of one person as one person for gender

The application SHALL group the glossary entries that name one person into a cluster, computed from the entries alone
each time it is needed (no stored field, and no book index is needed), and SHALL treat the cluster as one person for
gender: a review SHALL end with one gender for the cluster, and a gender the person chooses for one member SHALL be
written to the others, a gender the person set never being overwritten.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: a book calls one woman `Zephra` and `Zephra Quillon`; one verified quote for each spelling is too little to suggest a gender for either, two together suggest it for both, and a review ends with one gender for all the spellings, so the model is never left to give them two. A gender the person sets for one spelling is theirs for all of them, but `Mrs Venn` is not joined to `Arlo Venn`: she may be his wife.

#### Scenario: A short name and a full name are one person

- **WHEN** the glossary holds `Arlo`, `Venn` and `Arlo Venn`, all characters
- **THEN** each of the three has the other two in its cluster
- **AND** with `Mira Venn` added `Venn` joins neither, `Arlo` still joins `Arlo Venn` and `Mira` would join `Mira Venn`

#### Scenario: Only the first or the last word, only a character or an untyped entry

- **WHEN** the glossary holds `Dane` and `Arlo Dane Venn`, or `Venn` of type place and `Arlo Venn`
- **THEN** the entries are alone

#### Scenario: A gendered honorific joins only a name of its gender

- **WHEN** the glossary holds `Mrs Venn` and `Arlo Venn` (male, or of no gender and not a listed first name)
- **THEN** `Mrs Venn` is alone
- **AND** with `Mira Venn` (female) she joins it, `Mr Venn` joins `Arlo Venn` but not `Mira Venn`, and `Dr Venn` joins either

#### Scenario: The person's gender spreads and is never overwritten

- **WHEN** the person sets `Arlo` male and `Arlo Venn` is a suggested female
- **THEN** `Arlo Venn` becomes male as the person's, and a CSV row that sets a gender does the same
- **AND** WHEN `Arlo` is male and `Arlo Venn` female, both set by the person, they stay as they are

#### Scenario: Clearing a gender does not spread

- **WHEN** the person clears `Arlo`'s gender
- **THEN** `Arlo Venn` keeps its gender

#### Scenario: The anchor and the members of a cluster

- **WHEN** the glossary holds a multi-word entry of type character that does not open with a title (`Arlo Venn`)
- **THEN** it is an anchor, and an alias is never an anchor or a member
- **AND** a one-word entry of type character or untyped joins an anchor WHEN its word is the anchor's first or last word
  (never a middle word) and exactly one anchor holds it; a place, a term or a title never joins, and a surname two
  anchors share joins neither

#### Scenario: An honorific form joins the one anchor with that surname

- **WHEN** an honorific form (a title of the book's language from the bundled list, with or without a full stop, and a
  surname: `Mr Venn`, `Dr Venn`) names a surname
- **THEN** it joins the one anchor with that surname, and does not count as an anchor itself
- **AND** WHEN the title shows a gender (the language file's `femaleTitles` and `maleTitles`: `Mrs`, `Miss`, `Ms`, `Mr`)
  it joins only WHEN the anchor's gender (the entry's, else the first-name list's for its first word) is the same, so an
  unknown or another gender keeps `Mrs Venn` a person of her own

#### Scenario: A review ends with one gender for each cluster

- **WHEN** a review ends
- **THEN** the cluster gets the person's gender WHEN any member holds one the person set (the other members take it as
  the person's, and two members with different person-set genders are all left alone); else the verified one, else the
  suggested one, written to the members of a lower rank, with members that disagree at the top rank left alone
- **AND** a gender the person set is never overwritten

#### Scenario: A gender the person chooses is written to the other members

- **WHEN** the person chooses a gender for an entry (the table, the Add dialog, a CSV row) and the entry now holds it as
  the person's after a change (confirming a suggested or verified gender included)
- **THEN** the other members get it as the person's
- **AND** clearing a gender, editing a target or any other field does not change the other members

#### Scenario: The character sheet lists a member under its anchor

- **WHEN** the anchor has a gender and a member has none or the same
- **THEN** the sheet lists the member under its anchor, and on a line of its own WHEN the genders differ

#### Scenario: The verified quotes of every spelling are pooled

- **WHEN** one reading of `Zephra` quotes `She laughed` and one reading of `Zephra Quillon` quotes `She stood up`, and the code finds each quote in the narration of its window
- **THEN** the two quotes are pooled, and both entries read female, likely
- **AND** one quote alone would have suggested no gender for either

### Requirement: Show the glossary as the one editable table of Names & style

The Names & style screen SHALL show the glossary as its one table, with the columns Term, Kind, Forms, Translation (an empty one shows the placeholder "No target"), State (suggested, pinned or locked), Gender and a remove cross, with an origin chip (person, book, rule or likely) on Kind and Translation and (person, book or likely) on Gender, under the purpose line "Names of people and places — written the same way every time. Set the gender so names take the right grammatical form."

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`, `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: each value says where it came from, so the person can tell a fact of the book from a guess; this is where the person settles spellings before translating, if they want to. A gender is the person's, the book's or a likely value (the readings' verified quotes alone, or the model's answer for the bare name); no gender comes from a rule any more. The table explains its purpose, a changed row is marked on its edge so the term is never cut, an empty table says what to do, and the glossary is the only table of the screen.

#### Scenario: A scanned entry appears unlocked

- **WHEN** the scan proposed `Hale`
- **THEN** its row shows Term `Hale`, Kind `other`, an empty Forms cell, an empty editable Translation with the placeholder "No target", an empty State and Gender "—"

#### Scenario: Editing a target and locking it

- **WHEN** the person types `Гейл` into the Translation of `Hale` and sets its State to locked
- **THEN** the glossary entry `Hale` reads target `Гейл`, locked, with the origin person

#### Scenario: An empty glossary still offers the start

- **WHEN** the project's glossary holds no entry
- **THEN** the table reads "No names yet. Scan the book for them, or add one." with the buttons Scan the book and + Add
- **AND** Start translation is still enabled

#### Scenario: A changed row is marked

- **WHEN** the last automatic run changed the row `Earth`
- **THEN** the row shows a mark on its left edge, its tooltip reads "Changed by the last automatic run", and the Term cell reads `Earth` in full with no chip

#### Scenario: Start translation during a paused run shows it

- **WHEN** the translation of `Earth Gravity · EN → UK` is paused at 55 % and the person looks at the toolbar of Names & style
- **THEN** it offers Go to Translating, pressing it shows the paused translation offering Resume, and no new translation begins

#### Scenario: The screen with no book open

- **WHEN** no project is open
- **THEN** the Names & style step is locked in the navigation with the hint "Steps unlock when a project is open", so the screen cannot be reached and no glossary table, scan button or Start translation is shown

#### Scenario: Start translation is pressed while no start is offered

- **WHEN** the project's translation is running, paused, stopped, failed, waiting for the AI model or ready to continue, or it finished
- **THEN** the toolbar offers Go to Translating instead of Start translation, and nothing starts
- **AND** Translating offers Resume for a paused translation and Continue translation for a stopped, failed or ready-to-continue one

#### Scenario: The Type column and the remove column

- **WHEN** the table shows a row
- **THEN** its Kind is one of character, place, organisation, product, material, term, title or other, and the Translation is editable
- **AND** the last column has no header text and holds the row's remove cross

#### Scenario: The Forms column and the Unsure mark

- **WHEN** the entry `Зотов` joins the forms `Зотова` and `Зотову`, and two of its mentions also fit another entry
- **THEN** its Forms cell reads `Зотова, Зотову` with the tooltip "joins 2 forms: Зотова, Зотову" (a narrow column may cut the list; the tooltip keeps every form), and the row shows an Unsure mark whose tooltip reads "Unsure: some mentions fit two names"

#### Scenario: A meaning chip

- **WHEN** the entry `Brightwater` was sounded out and its meaning `bright water` was kept as a note
- **THEN** its row shows a meaning chip, and hovering it shows `bright water`

#### Scenario: A target's state and origin are shown

- **WHEN** `Hale` → `Гейл` was suggested by the model and then accepted
- **THEN** the row shows State pinned and the origin chip `person` beside the Translation, because accepting a suggestion is the person's decision: the chip says who decided the spelling and the state says how firmly it holds
- **AND** before the acceptance the same row shows the slanted `Гейл` with the label `likely` and State suggested

#### Scenario: The Gender column

- **WHEN** the table shows a row's gender
- **THEN** it reads female, male or neuter for a character, followed by its origin (`male · book`, `female · person` or `male · likely`), "not specified" for a character with no value yet, "not specified — no evidence in the book" for one the names pass read and found no evidence for, and "—" for any other kind; it never reads "unknown" and never shows a `rule` chip
- **AND** a likely value below a confidence of 0.7 reads `likely, low confidence`

#### Scenario: The glossary fills the screen

- **WHEN** the content is 1500 px wide, and then 1200 px wide
- **THEN** the glossary card takes the full width at both sizes, with the table filling the available height
- **AND** a card too narrow for its toolbar folds Import and Export into a three-dot menu

#### Scenario: The banner and the Start translation button

- **WHEN** a project is open
- **THEN** the screen shows the purpose line "Decide how names are translated. Optional — skip it and BookLoom prepares the names when the translation starts." and the toolbar's Start translation button

### Requirement: Review the glossary with the model on request

WHEN the person presses Review with model, the application SHALL decide by code each open entry whose past-tense verbs alone decide its gender, send every other entry not pinned or locked for a verdict, then ask the model for the most probable gender and kind of each entry still lacking one from the person or the book, storing them as likely. It SHALL change the glossary only after every call has answered; IF any call fails or Stop is pressed, THEN it SHALL change nothing.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: a frequency count cannot tell a sentence-opening interjection from a name in every case, and it never knows a type or a gender, so the model is asked to clean and classify the list. It sees how often the book uses each word in lower case too, which is the clearest sign of a common word. A row the person locked or gave a target is theirs and is never sent. A gender the model states is a fact of the book only when the windows it cites show it; the code's own count of pronouns is no longer shown to the model and no longer decides or overrules, because the count takes a neighbour's pronoun for the name's. The model is never allowed to answer "unknown" for the bare name: it gives its most probable value, and the table marks it likely so nobody mistakes it for a fact of the book.

#### Scenario: The review removes a common word and classifies a name

- **WHEN** the glossary holds `Well` and `Hale` (other, unknown), `Moreau` (character, unknown), `Milton` → `Мілтон`
  locked and `Baker Street` → `Бейкер-стріт`, and the model answers `Well` not a name, `Hale` a name (person, male),
  `Moreau` a name (place, female), and `Milton` and `Baker Street` not a name
- **THEN** `Well` is removed and remembered as removed, `Hale` reads character, male (likely), `Moreau` reads
  character, female (likely) — no window shows either by a pronoun — and `Milton` and `Baker Street` are unchanged
- **AND** only `Well`, `Hale` and `Moreau` were sent, and the line reads `Model review done: 1 row removed, 2 rows
  updated, 0 targets suggested.` when the suggestion call suggests nothing

#### Scenario: A term locked while the model thinks is left alone

- **WHEN** the person locks `Well` with the target `Ну` while the review's call is out, and the model judges `Well`
  not a name
- **THEN** `Well` → `Ну` stays, locked

#### Scenario: A failed or stopped review changes nothing

- **WHEN** the glossary holds 41 open entries and the second call fails as unreachable, or the person
  presses Stop while a call is out
- **THEN** the glossary holds the same 41 entries as before

#### Scenario: The verdict is asked under a strict schema

- **WHEN** the model is asked about an entry
- **THEN** it answers a verdict — name, term or not a name — with a type (person, place, org, term, title or other), a
  gender (male, female, neuter or unknown) and the numbers of the windows it relied on

#### Scenario: Verdicts are written against each entry as it then stands

- **WHEN** every call has answered
- **THEN** the application removes through the glossary (so the removal is remembered) each entry judged not a name
  whose type is still other and gender still unknown
- **AND** it sets the type of an entry still of type other to the model's guess

#### Scenario: Suggested targets, locked entries and the summary line

- **WHEN** the verdicts are written
- **THEN** the suggested targets that "Suggest target renderings with the model" asks for are written after the
  verdicts, in the same action
- **AND** a locked entry, a pinned one and one whose target the person chose are not changed, and the glossary is shown with the line
  `Model review done: N rows removed, M rows updated, K targets suggested.`

#### Scenario: The calls are announced, stoppable and sent only on the button

- **WHEN** the review runs
- **THEN** its calls are announced, timed, attempt-counted and stoppable as the model scan's are
- **AND** they are not sent unless the person presses the button

#### Scenario: Every open character ends with a value

- **WHEN** the book gives the character `Ivar` no verified quote and no verb ending, and the model's answer for the bare name is male with confidence 0.8
- **THEN** `Ivar` reads male, likely, and the table does not read "unknown"
- **AND** an answer with confidence 0.55 reads `likely, low confidence`

#### Scenario: A gender stands only on windows that show it

- **WHEN** the book reads `Kestrel nodded. "He never pays," said Pell.` and the model answers `Kestrel` a person, male,
  citing that window
- **THEN** `Kestrel` reads male (likely): the `he` is inside the speech, so no window shows the gender
- **AND** had the model answered female citing the windows `Kestrel stood. She left.` and `Kestrel smiled. She sat down.`, `Kestrel` would read female, from the book

#### Scenario: A count of pronouns decides nothing

- **WHEN** an open entry `Wren` is followed by `she` in five narration sentences of the book and by no `he`
- **THEN** `Wren` is sent to the model like every other open entry, and no gender is written from the count

#### Scenario: A count of pronouns overrules nothing

- **WHEN** the model answers `Corwin` a person, male, citing two windows whose narration holds `he` for him, and elsewhere the book writes `she` after `Corwin` three times
- **THEN** `Corwin` reads male, from the book

#### Scenario: The calls are cut by a budget and carry windows

- **WHEN** the other unlocked entries are sent
- **THEN** the calls are cut by a budget of about 5,000 input tokens and at most 40 entries, each entry with how many
  times the book uses it in any case and numbered windows, and with no count of pronouns
- **AND** the windows are four uses (six above 30 uses, two for a place), the first and one from each equal stretch of
  the rest, each the sentence with one before and one after

#### Scenario: A gender is verified only by the windows it cites

- **WHEN** the model gives a gender for a person still of gender unknown
- **THEN** it is set as the book's gender when most of the distinct windows it cites (strictly more than half, and at least two
  unless fewer were shown) hold, outside speech, a pronoun of that gender that the code gives the name
- **AND** otherwise it is set as likely

#### Scenario: A value of higher origin is never replaced

- **WHEN** `Mara Quill` is female from the book, `Wren` is female as the person set it, and the model answers male for both
- **THEN** `Mara Quill` and `Wren` are unchanged

### Requirement: Decide a read name from its verified readings by code

The review and the names pass SHALL share one path: the open entries the quality dial's names plan reads (Fast: a
likely character written five times or more; Balanced and Max: any entry written three times or more; at most 80) SHALL
be read in context, one term per call, and the code — not the model — SHALL decide each from its readings, counting
for a gender only the readings' verified quotes, which give a likely gender, and the past-tense verb evidence, by the
rules of the scenarios below.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: the model reads, the code decides. A small model's pronoun answers were wrong one time in ten or more, so the readings' quotes, even when the code finds each in the narration of its window, give only a likely gender, as before: it is shown to the person, and no request carries it and no check enforces it. The gender is the book's when five verbs after the name agree. The code's own count of pronouns near the name decides nothing any more, because it takes a neighbour's pronoun for the name's (a man was marked female on a count of three to one). Where nothing is found the entry stays without a gender and the model's guess for the bare name is shown as likely.

#### Scenario: The bartender is read, the common word removed

- **WHEN** a book writes `Mark` with a capital mid-sentence five times, followed by `he`, and `a mark on the wall`
  twice, and the dial is Fast
- **THEN** the Scan button adds `Mark`, the review reads it as a person's name and it stays, male
- **AND** in a book where `Mark the spot` opens three sentences and `mark` is written in lower case two times in five,
  three readings of `Mark` as an ordinary word quoting `Mark the spot` remove the entry

#### Scenario: One misread window removes nothing

- **WHEN** one window reads `Mark` as an ordinary word quoting `Mark the spot` and another quotes a phrase its own window
  does not hold
- **THEN** `Mark` is not removed

#### Scenario: A name's kind is the kind most of its name windows give

- **WHEN** a name has two windows read as a name (one window of one at Fast)
- **THEN** it takes the kind most of its name windows give: a person is a character, a place a place, an organisation
  or a thing a term
- **AND** a title majority of two windows or more makes a title; anything else changes nothing

#### Scenario: Only a person carries a gender

- **WHEN** an entry is a place, an organisation, a thing or a title
- **THEN** it carries no gender
- **AND** a gender the person set is never decided again

#### Scenario: An entry is removed only on strong evidence

- **WHEN** an entry has two ordinary-word uses whose quote stands in the window it answers, no window read as a name, a
  lower-case share of 0.2 or more in the scanned running text, and nobody typed it or gave it a gender
- **THEN** it is removed

#### Scenario: At Max a split reading gets one summary call

- **WHEN** at Max a term is read in two or more calls (or mentioned 30 times or more) and the calls read it differently
- **THEN** one `term-summary` call is made, and its reading replaces the windows' majority only when its quote stands in
  a window shown and it is one of the calls' own readings

#### Scenario: The Scan button keeps a dual-use capitalised word for the reading

- **WHEN** the book writes a capitalised word with a capital mid-sentence three times or more, and in lower case a
  fifth of the time or more
- **THEN** the Scan button keeps it for the reading and adds it only where the dial's plan reads it
- **AND** the run's own scans drop it as before

#### Scenario: Verified quotes alone give a likely gender

- **WHEN** at Balanced the untyped `Brann` is written six times and its one reading call reads every window as a
  person's name, quoting `He nodded` from the narration of each
- **THEN** `Brann` reads character, male, likely (the readings' votes alone are the model's judgement), and no batched
  review call is sent for it

#### Scenario: Two verified quotes at three in four are the bar

- **WHEN** the verified quotes of every spelling of a person are pooled
- **THEN** a likely gender is written when two or more quotes are verified and 75 % or more of them show one gender
  (three or more at 80 % when the source language is Auto), never a gender from the book
- **AND** fewer quotes or a smaller share write no gender from the readings

#### Scenario: A quote from speech is no vote

- **WHEN** a reading quotes `He never pays` and its window holds those words only inside quotation marks
- **THEN** the quote is not verified and counts for no gender

#### Scenario: A count of pronouns never decides or overrules

- **WHEN** the readings of `Brann` quote `he` from the narration of six windows, and elsewhere the book writes `she` in the sentence after `Brann` four times
- **THEN** `Brann` reads male, likely, and no value is written by rule or from the count

#### Scenario: An honorific, a title or a common noun is not a character

- **WHEN** the windows call a term a person but it is an honorific or a title of the source language (the bundled title
  list, or a run of capitalised words ending in a word of the language file's `officeTitles`: `Mrs`, `Duchess`, `Prime
  Minister`)
- **THEN** it is a title entry with no gender
- **AND** WHEN it is a common noun — a stop word or a never-alone word of the source language, or a word the book
  writes in lower case a fifth of the time or more — it stays unsure and carries no gender

#### Scenario: A person with no evidence stays unspecified

- **WHEN** the book gives a person no evidence — no verified quote, no cited window and no verb ending
- **THEN** the code decides nothing and the entry goes to the model for its most probable value, which is stored as likely
- **AND** until the model has answered (or if it never does) the Names & style gender cell reads "not specified — no evidence in the book" (Ukrainian: "не вказано — у книзі немає свідчень") instead of "unknown", the column's tooltip explaining it; any later gender and the person's own edit clear that wording

#### Scenario: Past-tense verbs decide by themselves

- **WHEN** the source language is `ru` and an open entry `Ольга` is followed in narration by past forms ending in `-ла` five times and by none ending in `-л`
- **THEN** `Ольга` reads character, female, from the book

### Requirement: Suggest target renderings with the model and refuse only what the code can prove

WHEN the model review or the model scan has its verdicts, the application SHALL, in the same action, send the open
entries (not pinned or locked) to a separate suggestion call, at most 20 per call, asking under a strict schema and the
Brief's name policy for the dictionary form of each target, keep only suggestions that pass the checks below and write
them as suggested targets of origin likely after every call has answered; IF a call fails or Stop is pressed, THEN it
SHALL write nothing.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d8--names-are-not-refused-for-their-letters-or-rewritten-from-other-names`. In plain words: a glossary of names with no targets leaves every draft to spell each name afresh, so the model proposes one spelling per name, following the policy the person chose. A suggestion is only a hint until it is accepted or the book agrees on it, and the model never undoes the person's choice. The code refuses a suggestion only for what it can prove: a copy of the source, a lost word, a letter the target language does not have, a case form, a changed identifier. It no longer refuses a rendering because its first letters do not match the way the name sounds: that rule threw away the model's translated renderings of a place and of a speaking nickname and left both sounded out, and those two rows reached 28 of 385 paragraphs. It also no longer copies one name's word into another name's rendering.

#### Scenario: Transliterated names get Cyrillic targets

- **WHEN** the policy is Transliterate, the target is Ukrainian and the review keeps `Hale` (character, male) and
  `Moreau` (character, female), and the model suggests `Гейл` and `Моро`
- **THEN** `Hale` reads `Гейл` and `Moreau` reads `Моро`, each suggested, with the origin likely and the label `likely`,
  and the line reads `Model review done: 1 row removed, 2 rows updated, 2 targets suggested.`

#### Scenario: A copied name is no transliteration

- **WHEN** the policy is Transliterate and the model answers `Hale` for `Hale`, and `Hale` again to the second ask
- **THEN** `Hale` keeps no target, while for English to German the same answer is kept

#### Scenario: An identifier is kept as written

- **WHEN** the model answers `ZX-81` for `ZX-81`, English to Ukrainian
- **THEN** it is kept with no further call

#### Scenario: A person's spellings agree

- **WHEN** `Arlo Venn` reads `Арло Венн` and the model suggests `Вен` for `Venn`
- **THEN** `Venn` reads `Венн`, and the line sent for `Venn` said `Arlo Venn → Арло Венн, so Venn → Венн`

#### Scenario: A brand follows the name policy and a material is translated

- **WHEN** the reading calls `Velmora` a brand, the policy is Transliterate and the model suggests `Velmora` and then
  `Вельмора`
- **THEN** the copy is refused and asked once more, and the entry reads `Вельмора`, not locked
- **AND** under Keep original it reads `Velmora`, locked, with no call, and a term the reading does not call a brand
  (an alloy, a drug) is asked about, translated and never locked

#### Scenario: Keep original needs no call for a name

- **WHEN** the policy is Keep original and the open entries are `Hale` (character), `Milton` (place) and `Amulet`
  (term)
- **THEN** `Hale` and `Milton` are suggested as written with no call, and only `Amulet` is sent

#### Scenario: The person's target is never overwritten

- **WHEN** the person types `Хейл` into `Hale` while the suggestion call is out, or `Moreau` already reads `Мору` typed
  by the person, and the model suggests `Гейл` and `Моро`
- **THEN** `Hale` reads `Хейл` and `Moreau` reads `Мору`, neither suggested

#### Scenario: A second review refreshes a suggestion

- **WHEN** `Hale` reads `Хейл` suggested and the next review's suggestion is `Гейл`
- **THEN** `Hale` reads `Гейл`, still suggested

#### Scenario: A failed suggestion call writes nothing

- **WHEN** the verdicts answered and the suggestion call fails with a timeout
- **THEN** the glossary holds the same entries as before the review, verdicts included

#### Scenario: The call carries the entry's evidence and a strict schema

- **WHEN** the entries are sent to a suggestion call
- **THEN** each carries its lemma (the nominative), its type, its gender when known, `speaking name` and `brand` when the term reading says so, what
  the glossary already renders of its person (`Arlo Venn → Арло Венн, so Venn → Венн`, for a spelling of a person whose
  full name has a rendering) and up to two sentences of the book that hold it (each cut at 120 characters)
- **AND** the reply is `{"suggestions":[{"term","target","meaning"}]}`, at most 20 items, a target and a meaning at most
  80 characters each, `""` meaning no suggestion or no note; the reply carries no gender

#### Scenario: The system message states the name policy

- **WHEN** the suggestion call is built
- **THEN** its system message states the Book Brief's name policy: Transliterate (the default for every language pair)
  asks for the name written letter by letter in the target language's script by that language's modern practice,
  following any convention the target language declares (such as г for a hard g in Ukrainian); Translate (shown as
  "translate or adapt names") asks for the target language's own form of a name that has one; Keep original asks for
  nothing
- **AND** a real place or person with an established name in the target language keeps it under every policy
- **AND** a nickname that is a plain word and a domain term are translated by meaning, while a proper name that only
  has a meaning is sounded out and its meaning is kept as a note

#### Scenario: Under Keep original a name is not asked about

- **WHEN** the policy is Keep original
- **THEN** no call is made for a name-like entry, and its source spelling is suggested as its target
- **AND** an entry is name-like when it is a character or a place, a brand by its reading, or a title or other entry
  that the readings verified as a proper name or that is a phrase of capitalised words (`Harbour Guild`); one rule
  decides it for the keep, the lock and the rendering checks
- **AND** any other entry of type term (a common noun, such as a material, a substance, a drug or a unit) is still asked
  about, to be translated by meaning, and is never kept or locked as a name (15j.J1)

#### Scenario: A suggestion is kept only when it is well formed

- **WHEN** a suggestion comes back
- **THEN** it is kept only for a term of its batch, on one line, without a placeholder token, at most 80 characters
- **AND** under Transliterate when the source and target scripts differ it has no letter left in the source's script once
  a Latin look-alike inside a Cyrillic word (`Вeнс`) is put back as its Cyrillic twin

#### Scenario: A rendering that leaves out a capitalised word is refused

- **WHEN** a name-like entry, and whose reading does not call it a
  speaking name (a brand follows the name policy exactly as a person's name does, 15j.J1), is rendered under
  Transliterate or Translate alike
- **THEN** a rendering that leaves out a capitalised word of the name is refused (a hyphenated word counts as its parts,
  and a translated common word that stands in lower case, such as `місто` for `City`, takes the place of one)
- **AND** `Varno City` → `Варно` is refused, `Варно Сіті` and `місто Варно` are kept (15j.J4)

#### Scenario: A last word with a non-nominative ending is refused

- **WHEN** the policy is Transliterate and a rendering's last word is the source's own transliteration plus an ending in
  the language file's `nonNominativeEndings` (endings no nominative has: `-ом`, `-ем`, `-ові`, `-ами` and the like; `-а`,
  `-у` and `-і` are left out, for `Анна`, `Павло` and `Джессі` are dictionary forms), the letter before the ending being
  one the `translit` table gives for the name's last letter and the stem no shorter than the source word, and the last
  letter of the name is not one the source language's `openNameFinals` lists
- **THEN** it is refused: `Valdor` → `Валдором` is refused for `Валдор`
- **AND** a target language whose file lists no such endings refuses nothing for an ending
- **AND** `Ann` → `Анна`, `Jess` → `Джессі`, `Sarah` → `Сара`, `Tom` → `Том`, `Lviv` → `Львів` and `Thamesor` → `Темзор`
  are kept

#### Scenario: Under every policy an identifier, a forbidden letter and a copy are refused

- **WHEN** a rendering changes an identifier (`KX-7`), holds a letter in the language file's `forbiddenLetters` (`Сын`,
  15j.J10), or equals the term ignoring case and outer space while the source and target scripts differ
- **THEN** it is refused, under every policy
- **AND** unless the entry is an identifier (one token holding a letter and a digit) or kept as written, which is kept
  even though its letters are in the source's script; the term-mapping check applies the same identity rule and does not
  count a rendering equal to the term across scripts as a learned rendering

#### Scenario: Refused renderings are asked once more

- **WHEN** renderings are refused by these rules
- **THEN** one further call per batch carries only the refused names and names each refused rendering and why, about the
  renderings refused by these rules and about no other refusal
- **AND** a rendering refused a second time leaves its entry without a target, and a failed further call returns its error

#### Scenario: The suggestions are written against each entry as it then stands

- **WHEN** every call has answered
- **THEN** a suggestion is written into an entry that is neither pinned nor locked and whose target is empty or was suggested, as a suggested target of origin likely,
  and no gender comes from the suggestion call (a person's gender is the review's)
- **AND** a target the person typed, imported, accepted or locked is not overwritten, the writes record no deferral, and
  while the calls run the screen shows `Suggesting renderings B/N` with the batch under way

#### Scenario: The lemma is asked for, not a form

- **WHEN** the entry `Велемир` joins the forms `Велемира` and `Велемиром`, two attested sentences use them, and the model answers `Велемиром`
- **THEN** the call carried the lemma `Велемир` and the sentences, the answer is refused for its non-nominative ending and asked once more

#### Scenario: A stress-marked reply is stored without the marks

- **WHEN** the model answers `Зо́тов` for `Zotov` and the target language's file asks for stress marks to be removed
- **THEN** the suggestion is stored as `Зотов`

#### Scenario: A pair's invented example is sent when it has one

- **WHEN** the language pair's file sets an example for the chosen name policy, and then when it sets none
- **THEN** the call carries that example in the first case, and in the second case is zero-shot with the schema only

#### Scenario: A rendering is not refused for its first letters

- **WHEN** the policy is Transliterate, the target is Ukrainian and the model answers `Шип` for the nickname `Thorn` and `Кам’яний міст` for the place `Stone Bridge`
- **THEN** both are kept as suggested targets, and no further call is made for them

#### Scenario: A suggested name that loses a word or holds a foreign letter is asked again

- **WHEN** the model suggests `Варно` for `Varno City`, `Валдором` for `Valdor` or `Сын` for `Son`
- **THEN** each is refused, named with its reason in one further call, and an entry refused twice has no target

#### Scenario: The renderings of one person agree

- **WHEN** one person is written under several spellings
- **THEN** a spelling takes the word the full name's rendering has in its place, the full name's rendering being the one
  the glossary holds or else the one asked in the same batch, and a full name asked about takes the words of a held
  rendering of one of its spellings

#### Scenario: Two names that share a word keep their own renderings

- **WHEN** the model suggests `Міст Марроу` for the place `Marrow Bridge` and `Брідж-роу` for the place `Bridge Row`
- **THEN** each row keeps the rendering the model gave, and no word of one rendering is written into the other

#### Scenario: A name the model adapts is kept under every policy that asks the model

- **WHEN** the source is Russian, the target Ukrainian, the entry is `Алексей` and the model answers `Олексій`
- **THEN** the answer is kept as a suggestion under Translate and under Transliterate alike

### Requirement: Keep the person's entries and removals whenever names are proposed

WHEN a scan — the deterministic scan on preparing a run, on opening Names & style or at the end of a chapter, or the
model scan — finds a term the glossary already holds by its glossary key, the application SHALL keep the existing entry
unchanged; and the application SHALL NOT let any scan, deterministic or model, propose a term the person removed in this
session, until the person adds that term again through Add term or a CSV import.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: the glossary grows while the book translates — the run prepares it at its start and adds the names it meets at the end of each chapter — and none of that growth may undo the person's work: a spelling they settled stays, and a false name they deleted does not come back at the next chapter. Adding a found name never costs the chapter's stored decisions either. The model scan honours a removal the same way.

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

### Requirement: Prepare names in one pass

WHEN the application prepares names in one pass, it SHALL run these steps in order on a private copy of the glossary: scan the book for names and titles when the glossary is empty, have the model check the candidates and remove what is not a name, ask the model's most probable gender and kind for the rest, ask a rendering for each name without one, and check the suggestions against the target language's rules. It SHALL write the stored glossary once, after its last step.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d7--the-recurring-term-list-is-removed-whole`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: one action — the run's Prepare stage, or the button on Names & style — gets from a book to a prepared glossary. The code finds the candidates; the model then filters out the junk: the most-written candidates are read in context one call each, every other one is sent for a verdict in batches of at most 40 (fewer when 5,000 input tokens are reached first), and a candidate the model judges not a name is removed, so no rendering is asked for a sentence-opening word or a heading. This check is part of the pass wherever the pass runs, the run included; only the model scan that looks for more names stays behind its button. The pass must also be whole: an answer that arrives half-way, or a Stop, must not leave half of a result behind; what the person fixed by hand stays fixed. The pass no longer reads recurring common words: those calls were a tenth of the traced run and changed nothing. Its renderings stay suggestions unless a lock rule makes one final; a suggestion becomes pinned when the person accepts it or when the book agrees on it after the Translate stage.

#### Scenario: A brand under Keep original is locked, a name in a language that declines names is not

- **WHEN** the policy is Keep original, the target is Ukrainian and the model reads `Zorbo` as a brand and `Brann` as a
  person, with the renderings `Zorbo` and `Бранн`
- **THEN** `Zorbo` is locked with the target `Zorbo` and `Brann` is pinned with `Бранн`
- **AND** with a German target a name read as a name and not a speaking one is locked as well, but never a common noun

#### Scenario: A name is locked with the rendering it was given

- **WHEN** a name is an identifier or kept as written by the name policy (the rendering is its own spelling)
- **THEN** it is locked with the rendering it was given
- **AND** WHEN the pass read it, decided it is a name and not a speaking one, and the target language does not decline
  names and the rendering passed the checks, it is locked as well; any other rendering that passed the checks is pinned

#### Scenario: The estimate of calls and minutes

- **WHEN** the application estimates the names step before the first model call, from the dial's plan over the glossary as it will stand after its scan, and nothing is measured for the chosen model
- **THEN** a call takes 3.0–4.9 seconds at Fast, 3.5–5.7 at Balanced and 3.5–6.9 at Max (`NamesPlan`, measured on the reference model), and the minutes are rounded up at both ends
- **AND** with seconds stored for the model, the estimate is the calls times those seconds, as "Estimate the names pass from the seconds measured on this model" says

#### Scenario: The model name scan stays behind its gate

- **WHEN** the pass runs over an empty glossary
- **THEN** the deterministic scan runs, and the model name scan stays behind its gate

#### Scenario: A suggestion the checks refuse is listed, not accepted

- **WHEN** the pass has the suggestion `Валдором` for `Valdor` and the target language's file lists `-ом` as a non-nominative ending
- **THEN** `Valdor` stays suggested, and the Results view lists it with the reason "not the dictionary form"

#### Scenario: Forms are grouped before the names are decided

- **WHEN** the pass scans a Russian book with `Велемир` and `Велемиром` and the glossary is empty
- **THEN** the names step decides one entry `Велемир`, not two

#### Scenario: A pass fills the glossary and writes it at the end

- **WHEN** the glossary holds the untyped names `Brann`, `Harrowgate` and `Overture`, and the model reads `Brann` as a
  man, quoting `He nodded` from the narration of two windows, and `Overture` as not a name
- **THEN** every model call goes out against the glossary as it was, and afterwards `Brann` is a male character from the
  book with a suggested rendering, `Harrowgate` has a suggested rendering, and `Overture` is gone

#### Scenario: A stopped pass changes nothing

- **WHEN** the person presses Stop during the fourth model call
- **THEN** no fifth call is sent, the pass answers `cancelled`, and the glossary has not changed

#### Scenario: A failed call changes nothing

- **WHEN** the call that asks for renderings fails with `unreachable`
- **THEN** the pass answers `unreachable` and the glossary is as it was

#### Scenario: Nothing is written when the pass stops or fails

- **WHEN** the person stops the pass (the calling thread is interrupted, after which no further model call is sent), a
  call is cancelled, or a call fails
- **THEN** nothing is written and the pass answers with that error

#### Scenario: What the person fixed stays fixed

- **WHEN** the pass runs over a locked or pinned entry, a target the person typed, a gender the person chose, or a name
  the person removed
- **THEN** it changes none of them and adds none back

#### Scenario: The pass makes no call about common words

- **WHEN** the pass runs at Balanced over a book that writes `Warden` 3 times and `warden` 9 times
- **THEN** no call of the pass asks whether `warden` is a term

#### Scenario: The model removes what is not a name before the names are used

- **WHEN** the pass runs at Balanced over an empty glossary, the code scan proposes 240 candidates, the names plan reads the 80 most-written ones in context, and `Perhaps` and `Overhead` are two of the 160 others
- **THEN** the names step sends one reading call for each of the 80 and sends the 160 others for a verdict in calls of at most 40 candidates each — 4 calls when each batch fits the 5,000-token input budget — and `Perhaps` and `Overhead`, which the model judges not a name, are gone from the glossary before the first rendering is asked

### Requirement: Apply glossary edits from the next chunk and keep the glossary stored

WHEN the person edits the glossary while a run is paused or translating, the application SHALL apply the edit from the first chunk that starts after it, SHALL NOT change segments already decided except through the Proofread stage, and SHALL keep the glossary stored with the project across a restart; a person's edit and a name the run adds SHALL never overwrite each other.

**Source**: `design.md#d7--the-recurring-term-list-is-removed-whole`, `design.md#d9--stored-shapes-that-change`. In plain words: a person who spots a wrong spelling mid-run can fix it, and the rest of the book follows; earlier segments are corrected by the Proofread stage's code sweep or on the review desk, not silently. A chunk reads the glossary once, when it starts. Each change is written to the stored entry in one step, so the run adding a name at the end of a chapter and the person typing a target at the same moment both survive. The glossary is stored with the project (ADR-0043).

#### Scenario: A pause-time lock affects later chunks only

- **WHEN** the run is paused after chunk 12 and the person locks `Hale` → `Гейл`
- **THEN** chunk 13 onwards masks `Hale`, and the targets of chunks 1–12 are unchanged by the edit itself
- **AND** at Fast, which re-reads nothing, they stay unchanged in the written book

#### Scenario: A pause inside a chunk applies from the next chunk

- **WHEN** a Fast Assisted run pauses on the flagged `ch07.xhtml:41` inside the chunk `ch07.xhtml:40`–`ch07.xhtml:47`, and the person locks `Hale` → `Гейл`
- **THEN** `ch07.xhtml:42`–`ch07.xhtml:47` are drafted with `Hale` sent as text
- **AND** the chunk starting at `ch07.xhtml:48` sends `Hale` as a placeholder

#### Scenario: An edit while translating applies from the next chunk

- **WHEN** chunk 9 is being drafted and the person types `Тарн` as the target of `Tarn`
- **THEN** chunk 9 is finished without that target and chunk 10 is the first to receive `Tarn` → `Тарн`

#### Scenario: The glossary survives a restart

- **WHEN** the person adds `Mira Holt` → `Міра Голт`, closes the application and opens the same project again
- **THEN** the glossary contains `Mira Holt` → `Міра Голт`

#### Scenario: A run's addition and a person's edit do not overwrite each other

- **WHEN** the run adds the name `Velk` at the end of chapter 3 while the person types `Тарн` as the target of `Tarn`
- **THEN** the stored glossary holds both `Velk` and `Tarn` → `Тарн`

### Requirement: Offer the names pass in a progress dialog

The Names & style screen SHALL offer "Prepare names automatically" in a card above the glossary table that says what it does and how long it takes for the brief's quality dial ("About 2 min, measured on this model. You can undo it all afterwards." or, with nothing measured, "About 1–2 min, estimate."), and a press SHALL run the names pass on the chosen model in the progress dialog, whose Cancel ("Cancel writes nothing.") stops the pass and leaves the glossary as it was.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d7--the-recurring-term-list-is-removed-whole`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: one button gives the person the same preparation a run makes by itself at its start, and gives every change back one by one or all together. Hiding the dialog with Continue in background or Esc, and the activity chip, are the `app-shell` capability's progress-dialog rules.

#### Scenario: The estimate comes before the first call

- **WHEN** five open names are each mentioned at least three times at the Balanced dial, and the chosen model has stored 15 seconds per reading call, 15 per name-knowledge call and 30 per suggestion call
- **THEN** the estimate is 5 reading calls, 1 name-knowledge call and 1 suggestion call, 120 seconds in all, and the card reads "About 2 min, measured on this model."

#### Scenario: The estimate line

- **WHEN** the screen opens, or a change of the dial has been saved
- **THEN** the card states the estimate again ("About 2 min, measured on this model. You can undo it all afterwards.")
- **AND** the estimate is left out when it cannot be made

#### Scenario: The button is off while a model action runs

- **WHEN** any model action runs, or no model is chosen
- **THEN** the button is off while a model action runs, and with no model chosen a press reports that in the screen's banner and calls nothing

#### Scenario: The press fills the glossary and Undo all gives it back

- **WHEN** the person presses "Prepare names automatically" and the pass adds `Mira Holt` and drops `Hale`
- **THEN** the Results view shows "1 added · 1 removed" in its subtitle and lists the two changes in the Changes tab
- **AND** Undo all removes `Mira Holt` and puts `Hale` back

#### Scenario: Cancel in the progress dialog changes nothing

- **WHEN** the person presses Cancel in the progress dialog while the pass waits for the model
- **THEN** the dialog says "Stopped. Nothing was changed." and the glossary is as it was

#### Scenario: The person's entries are left alone

- **WHEN** the glossary holds a locked `Gatewarden`, a `Marrow Lane` with the person's target and a `Wren` whose female gender the person chose
- **THEN** after the pass those entries are unchanged and `Wren` is still female

#### Scenario: A removal is remembered

- **WHEN** the person removed the name `Gantry` and the model would keep it
- **THEN** the pass does not add `Gantry` back

#### Scenario: The pass runs in the progress dialog

- **WHEN** the pass runs
- **THEN** the progress dialog is shown on the work layer, the computer is kept awake, and a stopped or failed pass leaves the glossary as it was

#### Scenario: Changed rows are marked

- **WHEN** the pass succeeds and the person presses Show changed rows
- **THEN** every changed row shows the left-edge mark and the changed filter is on

### Requirement: Keep the meaning of a name made of ordinary words as a note

WHEN a name is made of ordinary words but is used as a proper name, the application SHALL ask the model to sound it out under the Transliterate policy and keep its meaning as a note on the entry, SHALL keep the rendering the model gives for a nickname that is a plain word, translated, and SHALL ask the person no question about either.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d8--names-are-not-refused-for-their-letters-or-rewritten-from-other-names`, `design.md#d21--defaults-instead-of-questions`. In plain words: a surname such as `Brightwater` is a name first; a translator sounds it out and remembers what it means, and a nickname that is a plain word is translated. The model decides which of the two a name is, and the code no longer refuses its translated rendering. The person is no longer asked about such names before a run; whoever wants another rendering types it into the Translation cell.

#### Scenario: A surname is sounded out and its meaning kept

- **WHEN** the policy is Transliterate and `Brightwater` is a surname
- **THEN** its suggested target is the sound `Брайтвотер`, and the entry holds the note `bright water`

#### Scenario: A plain-word nickname is translated

- **WHEN** the book calls a character `the Fox` as a nickname
- **THEN** the suggestion is a translation, and no meaning note is made

#### Scenario: The person is not asked

- **WHEN** the glossary holds `Brightwater` with the note `bright water` and Start translation is pressed
- **THEN** the translation starts with no question about `Brightwater`

### Requirement: List the six steps of the names pass in its progress dialog

The progress dialog of the names pass SHALL list six steps in order — Find candidates in the book, Check which candidates are names, Ask what the model knows about the names, Suggest translations for names, Check the suggestions, Write the results — each with the number of requests it sent and a done mark once ended, and a figures line that counts those six steps and times them by the requests.

**Source**: `research/review-digest.md#f13--about-40--of-the-run-time-buys-nothing-in-the-exported-book`, `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: the person sees which part of the pass is running and how much is left. The second step is named for what it does: the model checks the candidates the code found and what is not a name is removed (it read "Decide the names"; in Ukrainian it now reads «Перевірити, які кандидати є іменами»). The four steps about repeated words are gone with the list they filled. The check step sends no request and accepts nothing: the suggestions stay suggested until the person accepts them or the book agrees on them.

#### Scenario: The steps are listed with counts

- **WHEN** the pass is at its fourth step with 25 requests answered
- **THEN** the list shows six rows, the fourth reads "Suggest translations for names" with "25 requests so far", and the steps after it say "Not started"

#### Scenario: The count reads by the step's state

- **WHEN** a step is running with 25 requests, has ended with 6, ended with none, or has not begun
- **THEN** its row reads "25 requests so far", "6 requests", "no request" or "Not started"
- **AND** no count reads "of N", because no step knows its total up front

#### Scenario: The figures count the six steps

- **WHEN** the pass has ended three steps, one of them with 2 requests and two with none, and the results open after it
- **THEN** the figures line reads "50 % · 3 of about 6 steps", the three ended rows carry the done mark, and the results header counts 6 steps

## MODIFIED Requirements

### Requirement: Propose names by a deterministic scan when the glossary is empty

WHEN the Names & style screen opens for a book whose glossary is empty, or a run prepares such a book, the
application SHALL propose, without any network call, every capitalised word and every run of two or three capitalised
words that occurs at least 3 times not at the start of a sentence, counting the forms of one name together, ordered by
how often it occurs and then by where it first occurs, each with type other, gender unknown and no target, by the rules
of the scenarios below.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d16--titles-are-glossary-rows`. In plain words: a name that recurs mid-sentence is almost certainly a proper noun, so a frequency count finds most of a book's names instantly and offline; words at the start of a sentence are ignored because every sentence starts with a capital, and the full stop after an abbreviated title does not end a sentence, so `Hale` in `Mr. Hale` still counts. A proposal has no target yet, so it is neither pinned nor locked, and the forms of one name are one proposal. A term the person removed is never proposed again. A word the book also writes in lower case a fifth of the time or more is a common word, which is a test that works in any language with capitals; the stop-word list catches the function words a book rarely writes in lower case. A title such as `Mr` is never a name; it is proposed as a title row by its own requirement.

#### Scenario: Common words, contractions and parts of longer names are not proposed

- **WHEN** the glossary is empty and the book has `He paused—Well, perhaps not.` 3 times and `well` 3 times in lower
  case, `"Don’t go."` 3 times and `don’t` 4 times, `the Words` 3 times and `words` twice, `the Seal` 3 times and `seal`
  twice, `a Shield` 3 times and `shield` once, `the road to Al-Varesh` 3 times, `Corin Ashby` 6 times mid-sentence,
  `Ashby` alone 6 times and `Corin` alone 3 times
- **THEN** `Al-Varesh`, `Corin Ashby` and `Ashby` are proposed
- **AND** `Well`, `Don’t`, `Words`, `Seal`, `Shield`, `Al`, `Varesh` and `Corin` are not

#### Scenario: A name that opens sentences is counted there too

- **WHEN** the book has `They visited Odric Fenn at his shop.` 4 times and `Odric Fenn bowed low.` 3 times
- **THEN** `Odric Fenn` is proposed with a count of 7, and neither `Odric` nor `Fenn` is proposed on its own

#### Scenario: The stop words are the book's language's

- **WHEN** the book's source language is `uk` and it has `Він сказав Так тихо.` 3 times
- **THEN** `Так` is not proposed
- **AND** with the source language `en` the same text proposes `Так`

#### Scenario: Recurring mid-sentence names are proposed

- **WHEN** the glossary is empty and the book has `Hale` 5 times mid-sentence, `Baker Street` 3 times mid-sentence
  and `Moreau` twice
- **THEN** `Hale` and then `Baker Street` are proposed with type other, gender unknown and no target
- **AND** `Moreau` is not proposed

#### Scenario: Sentence-initial capitals are ignored

- **WHEN** `The` begins 40 sentences and never occurs capitalised mid-sentence
- **THEN** `The` is not proposed

#### Scenario: A name after an honorific is not sentence-initial

- **WHEN** the glossary is empty and the book has `Mr. Hale` 3 times mid-text
- **THEN** `Hale` is proposed as a name and `Mr` is never proposed as a name

#### Scenario: The scan needs no provider

- **WHEN** no provider is reachable and Names & style opens with an empty glossary
- **THEN** the proposals appear and no network call is made

#### Scenario: A non-empty glossary is not rescanned

- **WHEN** the glossary already holds the person's entry `Hale` → `Гейл` and a run prepares
- **THEN** preparation runs no scan and leaves the glossary holding exactly that entry

#### Scenario: A sentence starts after a stop mark, a dash or a quotation mark

- **WHEN** the scan reads where sentences start
- **THEN** a sentence starts after `.`, `!`, `?` or `…`, and also after a dash (`—`) or a quotation mark
- **AND** a word that follows `Mr.`, `Mrs.`, `Ms.`, `Dr.`, `St.` or `Prof.` does not count as the start of a sentence,
  and the title itself is not proposed as a name

#### Scenario: A capitalised sentence start counts when the book capitalises it elsewhere

- **WHEN** a capitalised word starts a sentence and the book writes it with a capital mid-sentence at least 2 times
- **THEN** that start counts, and it starts a run

#### Scenario: An apostrophe or a hyphen between letters keeps one word

- **WHEN** the book writes `Don’t`, `O'Brien` or `Al-Varesh`, or a name with a trailing possessive `’s` or `'s`
- **THEN** each of the first three is one word, and the possessive is not part of the word

#### Scenario: Stop words, lower-case words and parts of longer names are not proposed

- **WHEN** a word is on the bundled stop-word list of the book's source language (English when the language has no list
  or is not known), or a single word is written in lower case at least 20% of the times it occurs, or a single word
  occurs on its own less than 40% of the times it occurs alone or inside a proposed longer run
- **THEN** it is not proposed, since it is part of that longer name in the last case
- **AND** a stop word neither starts nor joins a run

#### Scenario: Only running text is read

- **WHEN** the book holds a paragraph, a verse line, a list item, a table cell, a footnote and a caption, and also a
  heading, a title, metadata and alt text
- **THEN** only the first six are read
- **AND** a line of at most 10 words whose every word holding a letter starts with a capital, apart from `a`, `an`,
  `and`, `as`, `at`, `by`, `for`, `from`, `in`, `of`, `on`, `or`, `the`, `to` and `with`, is a title line and is not
  read, and neither is a block or an inline run that declares a language other than the source language

#### Scenario: A firm joined by "and" is one candidate

- **WHEN** a run holds the language's firm connector (`and`) and a firm tail word of the language file's `firmTails`
  list (`Son`, `Sons`, `Brothers`, `Co`, `Company`) and is written that way at least 3 times
- **THEN** it is proposed as one candidate, whatever share of its first word's uses it is
- **AND** a language with no such list proposes none

#### Scenario: A stop word opens a run only under four conditions

- **WHEN** the run `Other Shore` is written at least 3 times with the stop word capitalised away from a sentence start,
  the stop word is not one of the language file's `articles`, the run holds at least half of that stop word's such
  capitals, and the book also writes the stop word in lower case at least 20% of the times
- **THEN** the stop word opens the run and `Other Shore` is proposed
- **AND** a word the book writes only with a capital is an honorific such as `Herr` or `Don`, which never opens a name

#### Scenario: A number or a language name is not proposed alone

- **WHEN** a single word is a spelled-out number or ordinal (the bundled list of the source language, English when it
  has none) or a language's name as the JDK writes it in the source language (`Latin`, `French`)
- **THEN** it is not proposed on its own, though it may open a longer run

#### Scenario: A firm joined by "and" is one candidate (15i.N8)

- **WHEN** the book writes `Pell and Sons` 4 times and `Pell` alone 6 times
- **THEN** `Pell and Sons` is proposed
- **AND** with `Pell and Bea` in its place it is not (`Bea` is no firm tail), and with a language that lists no firm tails
  it is not

#### Scenario: A stop word written as part of a name opens it (15i.N8)

- **WHEN** the book writes `Other Shore` 3 times mid-sentence and `other` in lower case 6 times
- **THEN** `Other Shore` is proposed
- **AND** with the run written twice, or with the capitalised `Other` mostly outside the run, or with `other` never
  written in lower case, it is not

#### Scenario: Inflected forms of one name count together

- **WHEN** the source language is `ru` and the book has `Велемир` 2 times and `Велемиром` once mid-sentence
- **THEN** one entry `Велемир` is proposed, with the form `Велемиром` and a count of 3
- **AND** `Велемир` written twice and nothing else would not reach the floor of 3

#### Scenario: A language with no name-ending table counts written forms

- **WHEN** the source language is `en` and the book has `Wren` 3 times and `Wrens` 3 times mid-sentence
- **THEN** `Wren` and `Wrens` are proposed as two entries, each with its own count

### Requirement: Give the model only the terms that occur in the chunk

WHEN a call is built, the application SHALL include only the glossary entries whose lemma or any attested form occurs
as a whole word in the window's source text, each with its type, its target and the gender when the person or the book
gave it, and never a likely gender. An entry whose target the model suggested and nobody accepted SHALL be listed
apart, and neither it nor a pinned entry SHALL be hidden behind a placeholder.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: a small model's context is precious, so a two-hundred-entry glossary is trimmed to the names in play; type and gender let the model agree verbs and adjectives with a character in languages that inflect for them. Only a gender the person stated or the book proves is sent, because the model follows what the row says: a guessed gender is stored and shown to the person but never put in a request. A locked name is hidden behind a placeholder, so the model is told what that placeholder will read as.

#### Scenario: Only present terms are given

- **WHEN** the glossary holds `Hale`, `Milton` and `Baker Street` and the window mentions only `Milton`
- **THEN** the prompt lists `Milton` → `Мілтон` (place) and neither `Hale` nor `Baker Street`

#### Scenario: A locked name is listed by its placeholder

- **WHEN** `Hale` → `Гейл` (character, male) is locked and the segment `Hale opened the door.` is sent as
  `⟦g0⟧ opened the door.`
- **THEN** the prompt lists `⟦g0⟧ → Гейл, character, male`

#### Scenario: An entry with no target gives type and gender only

- **WHEN** the glossary holds `Moreau` (character, male, no target) and the window mentions `Moreau`
- **THEN** the prompt lists `Moreau` with `character` and `male` and no rendering

#### Scenario: A suggested rendering is a hint

- **WHEN** `Hale` → `Гейл` (character, male) is suggested and `Milton` → `Мілтон` is the person's, and the window mentions
  both
- **THEN** the prompt lists `Milton → Мілтон (place)` under the glossary heading and `Hale → Гейл (character,
  male)` under the suggested-renderings heading, and `Hale` is sent as written

#### Scenario: An empty glossary adds nothing

- **WHEN** the glossary is empty
- **THEN** the prompt carries no glossary terms

#### Scenario: Each entry is listed by what it holds

- **WHEN** a window names a pinned entry with the person's target, an entry with no target and a locked entry
- **THEN** the first is listed with its target rendering as guidance, the second by its type and gender alone, and the
  locked one — which the model sees only as a placeholder — as that placeholder with its rendering, type and gender

#### Scenario: The suggested renderings have their own heading

- **WHEN** a window names an entry whose target the model suggested and nobody confirmed
- **THEN** it is listed under `[Suggested renderings — not confirmed by the person]` with the line `Use each rendering
  unless it is clearly wrong; inflect it as the sentence needs.`
- **AND** it is never hidden behind a placeholder

#### Scenario: A form of a name selects its entry

- **WHEN** the glossary holds `Велемир` → `Велемир` (pinned, character, male) with the form `Велемира`, and the window's source holds only `Велемира`
- **THEN** the prompt lists `Велемир → Велемир (character, male)`

#### Scenario: A pinned name is a line, not a placeholder

- **WHEN** `Зотов` → `Зотов` is pinned and the window holds `Зотова`
- **THEN** the prompt lists the entry as a line and the source text `Зотова` is sent as written, with no placeholder

#### Scenario: A likely gender is not sent

- **WHEN** `Ivar` → `Івар` is a character whose gender is male, likely, and the window mentions `Ivar`
- **THEN** the prompt lists `Ivar → Івар (character)` with no gender
- **AND** a gender from the person or the book is listed

### Requirement: Keep the glossary editable while a translation runs

WHILE a translation of the project is at work (running, pausing, stopping, waiting on a stuck call, or waiting for the model), the Names & style screen SHALL keep the glossary editable under the banner "Translating is going on. Changes apply to the next segments translated." WHILE the Prepare stage of the run is running its names step, the screen SHALL be read-only instead, as the `app-shell` capability's "Make the screen that hidden work writes to read-only" says.

**Source**: `design.md#d20--prepare-steps-inside-the-run`, `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: hand edits stay possible because the next group of segments reads the glossary again. Which model actions are disabled while a translation runs, and the read-only screen while the names pass runs hidden, are the `app-shell` capability's rules.

#### Scenario: Typing a translation stays possible

- **WHEN** the translation of `Earth Gravity · EN → UK` is running and the person types `Велк` as the translation of `Velk`
- **THEN** the entry is stored, and the banner says the change applies to the next segments translated

#### Scenario: The names step of the run is the exception

- **WHEN** the run of `Earth Gravity · EN → UK` is in its Prepare stage and its names step is writing renderings
- **THEN** the glossary cannot be edited until the step ends, and it is editable again while the paragraphs are translated

### Requirement: Show the names pass's results with Undo all

WHEN the pass finishes, its dialog SHALL become a Results view with the subtitle "5 added · 0 removed · 1 changed · 49 steps · 1 min 48 s", the tabs Changes (Entry, Change, Before, After, grouped; a Why column when some row has a reason, and a Revert on each row) and Steps (the six steps with their request counts), and the actions Undo all, Show changed rows and Done.

**Source**: `design.md#d7--the-recurring-term-list-is-removed-whole`. In plain words: the person sees every change the pass made and can give all of them back; the pass has six steps now that it reads no recurring words.

#### Scenario: Results list the changes

- **WHEN** the pass adds `Mira Holt` and `lantern` and drops `Hale`
- **THEN** the Results view shows "2 added · 1 removed" in its subtitle and lists the three changes in the Changes tab

#### Scenario: A reverted row reads as put back

- **WHEN** the person presses Revert on the row that added `lantern`
- **THEN** the row's chip reads "reverted", its After shows the value it has again, its words are muted, and Revert gives way to "Reverted"

### Requirement: Record where each glossary value came from

The application SHALL record the origin of an entry's gender, kind and target — person, book, rule or likely — SHALL let the person edit any value at any time, SHALL let an automatic value replace a stored one only when its origin is the same or higher in the order book, rule, likely and the stored one is not the person's, and SHALL let only the person's and the book's genders be enforced by checks.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: a fact of the book is never confused with a hint: a likely value is shown to the person and may help them, but no request carries it and no check holds the text to it. A gender is never given the origin rule any more; the rule origin remains for a kind or a target that a rule of the code or a data file set, such as a title's rendering.

#### Scenario: A lower origin never replaces a higher one

- **WHEN** `Mara Quill` is female as the person set it and the model's answer is male
- **THEN** `Mara Quill` stays female with the origin person

#### Scenario: The person changes their own value

- **WHEN** `Mara Quill` is female as the person set it and the person changes her to male
- **THEN** `Mara Quill` is male with the origin person

#### Scenario: A value of the same origin is refreshed

- **WHEN** `Hale` → `Хейл` is a likely suggestion and the next review suggests `Гейл`
- **THEN** `Hale` reads `Гейл`, likely

#### Scenario: A higher origin replaces a lower one

- **WHEN** `Wren` is female, likely, and later a batched review cites two windows whose narration shows `she` for her
- **THEN** `Wren` is female with the origin book

#### Scenario: Only the person's and the book's genders are enforced

- **WHEN** `Ivar` is male, likely, and `Mara Quill` is female from the book, and a translated sentence gives each of them a verb of the other gender
- **THEN** the gender check raises a finding for `Mara Quill` and none for `Ivar`

### Requirement: Ask the model for the most probable gender and kind of each bare name

WHEN the names pass or a model review finds characters with no gender from the person or the book, or entries of type other, the application SHALL ask the model, in batches of up to 40 bare names with no book text, for the most probable gender and kind of each, and SHALL store every answer that repeats its name as a likely value with its confidence.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: a model knows that `Ivar` is almost always a man before it has read a page. Asking for the bare name costs about one call per 40 names, and the answer is always a value, never "unknown"; it is marked likely so nobody takes it for a fact, it is shown to the person only, and the book or the person overrides it.

#### Scenario: Every answer is a value

- **WHEN** the batch holds `Ivar`, `Mira Holt` and `Danube`, and the model answers `Ivar` male (0.8), `Mira Holt` female (0.9) and `Danube` river
- **THEN** `Ivar` reads male and `Mira Holt` female, both likely, and `Danube` is a place of kind river, likely
- **AND** the prompt offers male, female and neuter and no "unknown"

#### Scenario: A low confidence is shown

- **WHEN** the model answers `Sasha` female with confidence 0.55
- **THEN** `Sasha` reads `female · likely, low confidence`

#### Scenario: Batches of 40

- **WHEN** 95 names are open
- **THEN** three calls are made, with 40, 40 and 15 names

#### Scenario: A reply that does not repeat its name is asked again

- **WHEN** the model's answer for `Tobin` names `Tobias`, and then for `Tobin` in the next batch answers correctly
- **THEN** the first answer is dropped, `Tobin` is asked once more, and the second answer is stored
- **AND** a name the model never answers stays without a value

#### Scenario: Higher origins are not replaced

- **WHEN** the person set `Wren` female, the book decided `Mara Quill` female, and the model answers male for both
- **THEN** `Wren` and `Mara Quill` are unchanged

### Requirement: Render forms of address and common words by data and by the book, not by sound

WHEN targets are suggested for the glossary, the application SHALL render a title row whose term the language pair's `titleRenderings` lists (`mr`, `mrs`, `ms`, `miss`, `sir`, `dr` and so on) by that data, without asking the model, pinned with the rule as its origin, with a capital first letter where the term has one.

**Source**: `research/review-digest.md#f14--consistency-is-followed-but-not-enforced-or-checked-over-the-whole-book`, `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d16--titles-are-glossary-rows`. In plain words: a small model sounds `ms` out as `міз` and writes `Mr` in Latin letters, so the pair's file states the few certain words once. The row is pinned, not locked: a locked row is hidden behind a placeholder, which cannot decline («пане», «пана») and in the traced run was never even shown to the model (0 of 14 requests).

#### Scenario: A title of the pair's data is not asked

- **WHEN** the pair is English to Ukrainian and the title rows `ms` and `Mr` have no target
- **THEN** they get `пані` and `Пан` with no model call, pinned with the rule as origin

### Requirement: Read past-tense verbs after a name as gender evidence

WHERE the source language's file lists past-tense endings that show gender, the application SHALL count the first past-tense verb after a mention of a name, in the same clause, outside speech and with no other name between, as gender evidence for that name, and SHALL write the gender with the origin book when five or more such verbs are counted and 90 % or more of them show one gender.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: in Russian and Ukrainian the verb itself says who acted (`сказала`, `сказал`), so the grammar of the sentence, not a guess about whose pronoun follows, shows a woman's gender. The verb must follow the name in the same clause with no other name between, and five agreeing verbs are needed, so one odd sentence decides nothing. The endings come from the source language's file, so any language with gendered past forms gains by data alone.

#### Scenario: Another name between stops the evidence

- **WHEN** the book writes `Ольга и Велемир ушёл`
- **THEN** `ушёл` is a vote for `Велемир` only

#### Scenario: Speech and plural forms do not count

- **WHEN** the book writes `«Ольга пришла», сказал он` and `Ольга и Велемир пришли`
- **THEN** neither verb is evidence for `Ольга`

#### Scenario: A language with no such data gives no verb evidence

- **WHEN** the source language is `en`
- **THEN** only the pronouns are counted

#### Scenario: A genitive does not take the verb

- **WHEN** the source language is `ru` and the book writes `Ключ Велемира лежал на столе.`
- **THEN** `лежал` is no vote for `Велемир`: a verb counts only after a mention written as the entry's term, since an oblique form such as `Велемира` is not the subject of the clause

#### Scenario: A particle between the name and the verb is allowed

- **WHEN** the book writes `Велемир тихо ушёл.`
- **THEN** `ушёл` is one vote for male, the words the source file lists in `pastSkipWords` being passed over, while a comma, a word that is no past form (`Ольга, наверное, ушла.`) or a word on the file's `pastNotVerbs` list (`стол`) ends the search

#### Scenario: A feminine verb counts for the name before it

- **WHEN** the source language is `ru` and the book writes `Ольга пришла`, `Ольга сказала`, `Ольга ушла`, `Ольга села` and `Ольга встала`
- **THEN** `Ольга` reads female with the origin book
- **AND** with only the first three sentences no gender is written from the verbs

### Requirement: Register a gender-changed deferral when an enforced gender arrives

WHEN an entry's gender from the person or the book appears or changes after segments naming it were decided, the application SHALL register a gender-changed deferral for each of those segments, except for an entry that had no gender at all, whose waiting deferrals stay, and SHALL register none for a likely value.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: a likely gender was never checked, so when the person or the book later confirms it, even to the same value, the text decided meanwhile may disagree and must be looked at. This capability records the deferral; resolving it is the translation-pipeline capability's rule for the Proofread stage.

#### Scenario: A confirmed likely value defers the segments

- **WHEN** `Ivar` was male, likely, 4 segments naming him were decided, and the person sets male
- **THEN** 4 gender-changed deferrals are registered, one on each of those segments

#### Scenario: A changed enforced value defers the segments

- **WHEN** `Mara Quill` was female from the book, 2 segments naming her were decided, and the person sets male
- **THEN** 2 gender-changed deferrals are registered

#### Scenario: An entry with no gender keeps its waiting deferrals

- **WHEN** `Tobin` had no gender, 3 decided segments naming him carry the deferrals that wait on his gender, and the book later decides male
- **THEN** the 3 waiting deferrals stay, now with their fact arrived, so each of the 3 segments is looked at, and no gender-changed deferral is added beside them

#### Scenario: A gender the person sets after the run reaches every segment that names the entry

- **WHEN** `Sam` had no gender during the run, two decided segments name `Sam` with waiting deferrals, and after the run the person sets female
- **THEN** the two waiting deferrals have their fact and are settled by the Proofread stage the next Continue runs, and no second deferral is recorded on either segment

#### Scenario: A likely gender confirmed by the book defers the segments too

- **WHEN** `Corin Ashby`'s gender was likely male, three decided segments name him, and the windows a batched review cites then prove male
- **THEN** one gender-changed deferral is registered on each of the three segments, although the value did not change

#### Scenario: A likely or rule value registers nothing

- **WHEN** the model's likely value for `Ivar` arrives after segments naming him were decided
- **THEN** no deferral is registered

### Requirement: Log the names pass as counts and never as names

WHEN a names pass ends, the application SHALL write one INFO line to the run log with the numbers of entries, forms, and genders by origin (book, likely), the entries without a gender and the rejected suggestions, and SHALL put no name, rendering or book text in it.

**Source**: `research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `design.md#d6--one-gender-rule-the-person-or-evidence-the-code-verifies`. In plain words: the always-on run log must say what the pass did so a bad result can be traced, but it is a file the person may share, so it holds counts and choices only. No gender has the origin rule any more, so the line has no such count.

#### Scenario: No name appears

- **WHEN** the pass handled `Ivar`, `Велемир` and `Mara Quill`
- **THEN** none of those names, no target and no sentence of the book is in the run log

#### Scenario: The counts are written

- **WHEN** a pass ends with 37 entries, 58 forms, 12 genders from the book, 6 likely, 19 entries with no gender and 3 rejected suggestions
- **THEN** the run log holds `names pass entries=37 forms=58 book=12 likely=6 noGender=19 rejected=3`

### Requirement: Check a suggested target against the target language's rules

WHEN a target is suggested or Accept all is pressed, the application SHALL reject the target, with a reason, if it holds a letter the target language's file forbids, has no capital letter where the term has one, or has a different number of words than the term (a hyphenated word counting as its parts on both sides). It SHALL NOT reject a target for the letter it writes where the target language declares two spellings of one sound.

**Source**: `design.md#d26--the-book-brief-has-no-language-specific-rows`, `research/review-digest.md#owner-decisions`. In plain words: a suggestion that a small model writes with a Russian letter in a Ukrainian name, or a lost word reads right and matches nothing, so the code refuses it before it can be accepted and says why. A translated common word may stand in lower case (`місто` for `City`) and the later part of a hyphenated name may too (`Бейкер-стріт`), so only a target with no capital at all for a capitalised term is refused for its capitals. Every rule reads a key the target language's file may set; a language that sets none is not judged by it. The Book Brief no longer stores a hard-g choice, so no suggestion is refused for it; the two letters still count as one when forms are compared.

#### Scenario: A forbidden letter is rejected

- **WHEN** the target is Ukrainian and the suggestion for `Sinn` is `Сын`, whose `ы` the file forbids
- **THEN** it is rejected with the reason "letter not in the target alphabet"

#### Scenario: A lost word is rejected

- **WHEN** the suggestion for `Varno City` is `Варно`
- **THEN** it is rejected with the reason "different number of words", while `Варно Сіті` passes

#### Scenario: A capital is kept

- **WHEN** the suggestion for `Hale` is `гейл`
- **THEN** it is rejected with the reason "capitalisation differs"

#### Scenario: A hyphenated name and a translated common word pass

- **WHEN** the suggestion for `Baker Street` is `Бейкер-стріт` and the suggestion for `Varno City` is `місто Варно`
- **THEN** both pass: each has two words counted by their parts and a capital letter

#### Scenario: A declared convention is followed

- **WHEN** the target is Ukrainian and the suggestion for `Garcia` is `Ґарсія`, and another book's suggestion is `Гарсія`
- **THEN** neither is rejected for its hard g

#### Scenario: Alternatives of a convention are one name when forms are compared

- **WHEN** `Гарсія` is pinned and a draft writes `Ґарсії`
- **THEN** the draft's word is read as a form of the same name, not as a different spelling

## REMOVED Requirements

### Requirement: One person, one gender

**Reason**: Restated as "Treat every spelling of one person as one person for gender": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Treat every spelling of one person as one person for gender" in this capability; what is unchanged was carried over word for word.

### Requirement: Show the glossary as an editable table on Names & style

**Reason**: Restated as "Show the glossary as the one editable table of Names & style": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Show the glossary as the one editable table of Names & style" in this capability; what is unchanged was carried over word for word.

### Requirement: Review the glossary with the model when the person asks

**Reason**: Restated as "Review the glossary with the model on request": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Review the glossary with the model on request" in this capability; what is unchanged was carried over word for word.

### Requirement: Decide a read name from its readings by code

**Reason**: Restated as "Decide a read name from its verified readings by code": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Decide a read name from its verified readings by code" in this capability; what is unchanged was carried over word for word.

### Requirement: Suggest target renderings with the model

**Reason**: Restated as "Suggest target renderings with the model and refuse only what the code can prove": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Suggest target renderings with the model and refuse only what the code can prove" in this capability; what is unchanged was carried over word for word.

### Requirement: Keep the person's entries and removals when names are proposed

**Reason**: Restated as "Keep the person's entries and removals whenever names are proposed": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Keep the person's entries and removals whenever names are proposed" in this capability; what is unchanged was carried over word for word.

### Requirement: Prepare names and recurring terms in one pass

**Reason**: Restated as "Prepare names in one pass": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Prepare names in one pass" in this capability; what is unchanged was carried over word for word.

### Requirement: Apply glossary edits from the next chunk and keep the glossary across a restart

**Reason**: Restated as "Apply glossary edits from the next chunk and keep the glossary stored": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Apply glossary edits from the next chunk and keep the glossary stored" in this capability; what is unchanged was carried over word for word.

### Requirement: Offer the one-pass preparation in a progress dialog

**Reason**: Restated as "Offer the names pass in a progress dialog": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Offer the names pass in a progress dialog" in this capability; what is unchanged was carried over word for word.

### Requirement: Keep a meaning-bearing name's meaning as a note

**Reason**: Restated as "Keep the meaning of a name made of ordinary words as a note": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "Keep the meaning of a name made of ordinary words as a note" in this capability; what is unchanged was carried over word for word.

### Requirement: List the steps of the names pass in its progress dialog

**Reason**: Restated as "List the six steps of the names pass in its progress dialog": the old text held scenarios about behaviour this change removes (a count of pronouns, the recurring-term list, the letter rule or the Start questions), and a changed requirement may not drop a scenario.

**Migration**: See "List the six steps of the names pass in its progress dialog" in this capability; what is unchanged was carried over word for word.

### Requirement: Read recurring terms in context with the model

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Exchange the recurring terms as a CSV file

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Show the recurring terms with their purpose, use count and empty state

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Keep a stored lexicon of recurring terms and show it on Names & style

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Pass a recurring term held by the title list as a title

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Reuse the glossary's rendering of a term under the same lower-case key

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Group recurring terms by their stem

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Keep one established rendering for each recurring term

**Reason**: The recurring-term list is removed (see the added requirement "Keep no list of recurring common words"): its rows were mostly ordinary words, they changed nothing a reader could see, and the calls that judged them cost a tenth of the traced run (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`, `#time-breakdown-of-the-traced-run`).

**Migration**: A word the person wants held steady is added to the glossary as a row of kind term; titles and forms of address are glossary rows of kind title. Stored recurring terms are not carried over: the database is set aside and started fresh (ADR-0043).

### Requirement: Learn a recurring term's rendering from the decided segments

**Reason**: The recurring-term list it learned for is removed, and two of its learned rows were overwritten with wrong values during the traced run. Its one part that concerned names — a one-word name with no target takes the spelling the book used — is replaced by "Fill a frequent row that has no target from the book's own rendering" and "Pin a name's rendering once the book agrees on it".

**Migration**: No action: a frequent name row is filled after the Translate stage by the two added requirements.

### Requirement: Suggest a character's gender from a short list of first names

**Reason**: A gender guessed from a first name was stored with the origin rule and sent to the model as a fact; the review found rule genders wrong and followed (`research/review-digest.md#f5--wrong-facts-from-prepare-go-into-every-later-call`). An enforced gender now comes only from the person or from evidence the code verifies.

**Migration**: A character stays "not specified", or likely on the readings' verified quotes, until a review's cited windows, the verbs or the person give a gender; the bundled first-name lists stay only as data for joining an honorific form to a full name.

### Requirement: Suggest a name's gender from how the book declines it

**Reason**: A declension guess is a rule of the language, not a fact of the book, and it was stored with the origin rule that requests carried. No gender is taken from a guess any more.

**Migration**: A character stays "not specified", or likely on the readings' verified quotes, until a review's cited windows, the verbs or the person give a gender.

### Requirement: Ask at Start for the genders nobody has settled

**Reason**: The default path asks no question at Start (`research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`); a gender that is only likely is never sent and never enforced, so leaving it unanswered harms nothing.

**Migration**: The person sets a gender in the Gender column of Names & style at any time, before or during a run.

### Requirement: A gender answered at Start becomes the person's

**Reason**: The Start question it describes is removed.

**Migration**: A gender chosen in the Gender column of Names & style becomes the person's, as "Record where each glossary value came from" says.

## RENAMED Requirements

- FROM: `### Requirement: Keep both term tables editable while a translation runs`
- TO: `### Requirement: Keep the glossary editable while a translation runs`
