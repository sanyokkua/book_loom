# Spec Delta

## Purpose

The book's names and terms: proposed by a scan, settled by the person on the Names & style screen, carried between
books as CSV, given to the model only where they occur, and — when locked — rendered exactly as entered, so that a
character keeps one spelling from the first chapter to the last.

## ADDED Requirements

### Requirement: Propose names by a deterministic scan when the glossary is empty

WHEN the Names & style screen opens for a book whose glossary is empty, or a run prepares such a book, the
application SHALL propose, without any network call, every capitalised word and every run of two or three capitalised
words that occurs at least 3 times not at the start of a sentence, ordered by how often it occurs and then by where it
first occurs, each as an entry of type other, gender unknown, unlocked and with no target, by the rules of the
scenarios below.

**Source**: FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#name-term-pre-scan` (offline fallback), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`. In plain words: a name that recurs mid-sentence is almost certainly a proper noun, so a frequency count finds most of a book's names instantly and offline; words at the start of a sentence are ignored because every sentence starts with a capital, and the full stop after an abbreviated title does not end a sentence, so `Hale` in `Mr. Hale` still counts. Proposals stay unlocked because a locked term cannot inflect. A term the person removed is never proposed again (see "Keep the person's entries and removals when names are proposed"). A real book (Jonathan Stroud's "The Amulet of Samarkand") showed what a bare count gets wrong: `—Well,` and `"Don’t` open speech without a full stop, `Words`, `Seal` and `Shield` are common nouns given a capital, `Al-Arish` was cut into `Al` and `Arish`, and `Simon` was proposed beside `Simon Lovelace`. Every real name measured there is written in lower case less than 20% of the time, and every one of those common words at least 25% of the time, which is a test that works in any language with capitals; the stop-word list catches the function words and interjections a book rarely writes in lower case.

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

#### Scenario: A sentence starts after a stop mark, a dash or a quotation mark

- **WHEN** the scan reads where sentences start
- **THEN** a sentence starts after `.`, `!`, `?` or `…`, and also after a dash (`—`) or a quotation mark
- **AND** a word that follows `Mr.`, `Mrs.`, `Ms.`, `Dr.`, `St.` or `Prof.` does not count as the start of a sentence,
  and the title itself is not proposed

#### Scenario: A capitalised sentence start counts when the book capitalises it elsewhere

- **WHEN** a capitalised word starts a sentence and the book writes it with a capital mid-sentence at least 2 times
- **THEN** that start counts, and it starts a run

#### Scenario: An apostrophe or a hyphen between letters keeps one word

- **WHEN** the book writes `Don’t`, `O'Brien` or `Al-Arish`, or a name with a trailing possessive `’s` or `'s`
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

### Requirement: Triage the name candidates by code before any model reads them

The deterministic scan SHALL end with a triage of its candidates, at every minimum count (the glossary scan's 3 and
the model scan's 1 alike), measured over the whole book through the book index whatever slice was scanned, and its
result SHALL be what the glossary scan proposes and the model scan is shown. A joined name SHALL still be found as one
run by the book index's mentions, so later readings of the name can use it.

**Source**: 15i.N3 (`openspec/changes/complete-translation-workflow/tasks.md`), FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`). In plain words: a technical book repeats a section name in every heading and a converter credits itself on the title page; neither is a character, and the model should never be asked about them. A shop written `Orrin & Vale` is one name, and a camera printed `KESSEL OPTIK` on the label and `Kessel Optik` in the text is one name with two writings.

#### Scenario: A name that is mostly a heading is dropped

- **WHEN** `Quillon` is a heading 8 times and is written in the story's narration twice
- **THEN** it is not a candidate, at minimum count 1 or 3
- **AND** with 3 mentions in the story it is kept, however many headings repeat it; with 7 headings and 2 mentions in the
  story (78% outside) it is dropped; with 1 heading and 2 in the story (33% outside) it is kept

#### Scenario: Speech is narration

- **WHEN** `Quillon` is a heading 8 times and the story writes it 3 times, all inside quotation marks
- **THEN** it is kept

#### Scenario: A credit in the front and back matter is dropped

- **WHEN** `Jorun Falk` is written twice in the front matter and twice in the back matter and nowhere in the story
- **THEN** a scan over every segment of the book does not propose it

#### Scenario: A joined pair is one name at 80%

- **WHEN** `Orrin & Vale` is written 8 times and each of `Orrin` and `Vale` alone twice
- **THEN** `Orrin & Vale` is proposed with a count of 8 and its first joined sentence, and `Orrin` and `Vale` are not
- **AND** with 7 joined and 3 alone each (70%) `Orrin` and `Vale` are proposed and the pair is not; with `and` for `&` the
  same holds

#### Scenario: Both halves must be joined

- **WHEN** `Orrin & Vale` is written 9 times, `Orrin` alone once and `Vale` alone 11 times
- **THEN** `Orrin` and `Vale` are proposed and the pair is not

#### Scenario: Case variants are one name

- **WHEN** the book writes `KESSEL OPTIK` 5 times and `Kessel Optik` 3 times
- **THEN** `Kessel Optik` is proposed with a count of 8 and the alias `KESSEL OPTIK`
- **AND** with `McKay` 4 times and `Mckay` twice the base is `McKay`, and with `HALE` 4 times and `Hale` twice it is `Hale`

#### Scenario: A mention is in narration when it lies in the running text of a body unit

- **WHEN** a mention lies in a paragraph, a verse line, a list item, a table cell, a footnote or a caption, speech
  included, outside a title line or a printing note
- **THEN** it is in narration
- **AND** a mention in a heading, a title, a contents entry, a title-line paragraph, a printing note, front or back
  matter or the auxiliary unit is not

#### Scenario: A candidate that is mostly outside narration is dropped

- **WHEN** 70% or more of a candidate's mentions (its aliases' included, letter case kept) are not in narration and
  fewer than 3 are in narration
- **THEN** the candidate is dropped

#### Scenario: A joined pair keeps one writing, count and sentence

- **WHEN** `A & B` or `A and B` is one candidate because both halves are written joined in 80% or more of their own uses
- **THEN** its term is the first joined writing, its count the joined uses (it must itself reach the minimum count), its
  first sentence the first joined sentence, and a joined writing with the other connector its alias
- **AND** neither half is proposed on its own; below 80% the halves stay separate candidates

#### Scenario: Case variants are merged before the junk rules read them

- **WHEN** spellings differ only in letter case
- **THEN** they are one candidate: the base is the spelling not written in capitals, else the commoner one (the earlier
  on a tie), the others are its aliases, and the counts add

### Requirement: Run the model scan only when the person asks for it

WHEN the person presses the model scan button on Names & style, the application SHALL send the model the triaged
candidates, 40 per call, send every proposal the glossary does not hold and the person has not removed to the review's
verdict step, and write the kept proposals only after every call has answered. IF any call fails, THEN it SHALL write
nothing and show the provider's error in place. It SHALL NOT run on its own, when a run starts included.

**Source**: FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#name-term-pre-scan`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`), `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`. In plain words: the model finds names a frequency count misses, and it can guess type and gender, but it costs time and uses the network, so it only runs on request — the reference's automatic scan at run start is not built. Each name travels with one sentence of context, so a small model can tell a person from a place, and in batches small enough for a local model's context. The model's vocabulary is mapped onto the table's; a failure half-way must not leave half a merge or wipe what the person already has. On the fixture book a small model kept 17 of 28 candidates, among them chapter-title words (`Gravity Formula`, `Long Night`), spelled-out numbers (`Six`, `Seven`) and language names (`Latin`, `French`), and every one became a row: candidates now come from running text only, and every proposal goes through the same verdict the review gives, which sees all the book's uses of a term rather than one sentence, before anything is written.

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

#### Scenario: The model scan is a second opinion

- **WHEN** the dial is Balanced and the book has 12 names each written 3 times
- **THEN** pressing the scan makes no model call and changes nothing
- **AND** with 9 such names, or at Max with 30 or 9, it asks once (one batch of up to 40 candidates)

#### Scenario: Nothing is sent until the button is pressed

- **WHEN** Names & style opens and the person never presses the model scan button
- **THEN** no model call is made for the glossary

#### Scenario: A run never starts the model scan

- **WHEN** a run prepares a book whose glossary is empty
- **THEN** the deterministic scan proposes the names and no model scan call is made

#### Scenario: The candidates are every capitalised word and run read by the deterministic scan's rules

- **WHEN** the person presses the model scan button
- **THEN** the model is sent every capitalised word and every run of two or three capitalised words that occurs at least
  once not at the start of a sentence, read and filtered by the deterministic scan's rules (running text only, no title
  line, no foreign block or run, no stop word, no number or language name alone, no word mostly written in lower case),
  and they are the triaged ones of "Triage the name candidates by code before any model reads them"

#### Scenario: The verdict step keeps names and terms and gives them targets

- **WHEN** the proposals are sent to the verdict step, with the same prompt, evidence and batches as the review
- **THEN** only a proposal it calls a name or a term is kept, taking the verdict's type and gender where the proposal's
  were other and unknown
- **AND** each kept proposal gets a suggested target as "Suggest target renderings with the model" says

#### Scenario: The kept proposals are written by glossary key

- **WHEN** every call has answered
- **THEN** the kept proposals are written into the glossary, matching terms by their glossary key (see "Compare
  glossary terms by one key"), keeping any existing entry unchanged and adding each new term unlocked, with its
  suggested target where the model gave one

#### Scenario: Every proposed type has a mapping

- **WHEN** the model proposes a type and a gender
- **THEN** person reads as character, place as place, org as other, term as term and anything else as other
- **AND** each gender other than female, male or neuter reads as unknown

#### Scenario: The calls are announced, timed and stoppable

- **WHEN** the model scan runs
- **THEN** each call is announced, timed and attempt-counted as a run's model calls are, and the screen shows which
  request it is waiting on
- **AND** WHEN the person presses Stop, the request in flight is interrupted and nothing is written

#### Scenario: A scan that cannot add names says why

- **WHEN** the Book Brief's dial is below Max and the deterministic scan already proposes 10 names or more (the names
  written 3 times or more once the triage has dropped what is not a name)
- **THEN** the scan makes no call and answers an empty change, with one INFO line naming why
- **AND** at Max, or with fewer than 10, it runs as above

### Requirement: Suggest a character's gender from a short list of first names

WHEN a preparation or a model review finds an unlocked glossary entry with no gender, never tried and already typed as a
character, of at most three words, whose first word is a common given name that the source language's bundled list holds
for one gender, the application SHALL set that gender and mark it as a suggestion (`GlossaryEntry.genderOrigin` =
`SUGGESTED`), and SHALL NEVER change the entry's type.

**Source**: FR-GLOSS-02, FR-ALGO-D1; tasks 15e.14. In plain words: 176 of 277 glossary entries of a real run ended with gender unknown, so the gender sheet, the agreement fix and the consistency pass had nothing to act on; a short list of unambiguous first names fills most of them without a model call, marked so the person can see and change it.

#### Scenario: A listed name gets a suggested gender

- **WHEN** a held character `Hermione` has gender unknown in an English book and a run is prepared
- **THEN** the entry has gender female, marked as suggested and not locked

#### Scenario: Seeding never retypes

- **WHEN** a held entry `Victoria Station` of type other is prepared and the model's review calls it a place
- **THEN** the entry is a place with gender unknown

#### Scenario: A unisex name gets nothing

- **WHEN** the scan proposes `Alex`
- **THEN** the entry has gender unknown

#### Scenario: A person's choice is never overwritten

- **WHEN** the entry `John` already has gender female
- **THEN** preparation leaves it female and not marked

#### Scenario: Who types the entry

- **WHEN** an entry is typed as a character by the person, the scan's model typing or the review
- **THEN** it is seeded; a place or an untyped entry named like a person (`Victoria Station`, `Florence`) stays what it
  is, and the model's verdict types it

#### Scenario: An entry is seeded at most once

- **WHEN** the list proposed a gender, or the person set the gender in any way, even back to unknown
- **THEN** `GlossaryEntry.genderSeedTried` is set, so a gender the person reset to unknown stays unknown

#### Scenario: A review keeps the mark and the person confirms by choosing

- **WHEN** a model review re-applies an unchanged gender, or the person chooses the same value as a suggestion
- **THEN** a review keeps it marked as a suggestion, and the person's choice confirms it

#### Scenario: Seeding changes an entry atomically

- **WHEN** seeding or the term learning changes an entry while the person edits it
- **THEN** the change is made in one atomic step against the entry's current state
  (`GlossaryRepository.update(projectId, term, change)`), so an edit made meanwhile is never overwritten

#### Scenario: Unlisted names, display and confirmation

- **WHEN** a name is used by either gender or is also a common word, or the language has no list
- **THEN** it is not listed and nothing is suggested
- **AND** the table shows a suggested gender as `male (suggested)` and the column's hover explanation says so; a
  person's edit of the gender, or locking the entry, confirms it, and a gender already set is never overwritten

#### Scenario: The bundled list is per language

- **WHEN** a gender is looked up for a first word
- **THEN** the source language's bundled list `given-names-<language>.txt` is used ("seed, not exhaustive": English,
  Ukrainian and Russian)

### Requirement: One person, one gender

The application SHALL group the glossary entries that name one person into a cluster, computed from the entries alone
each time it is needed (no stored field, and no book index is needed), and SHALL treat the cluster as one person for
gender: a review SHALL end with one gender for the cluster, and a gender the person chooses for one member SHALL be
written to the others, a gender the person set never being overwritten.

**Source**: 15i.N2 (`openspec/changes/complete-translation-workflow/tasks.md`), FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`). In plain words: a book calls one woman `Zephra` and `Zephra Quillon`; three "she" after each name are too few to decide either, six together decide both, and the model is never left to give them two genders. A gender the person sets for one spelling is theirs for all of them, but `Mrs Venn` is not joined to `Arlo Venn`: she may be his wife.

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

#### Scenario: Three and two pronouns together decide

- **WHEN** a book shows three "she" after `Zephra Quillon` and two after `Zephra`
- **THEN** neither tally alone is strong, the pooled five are, and the review gives both entries the female gender with
  no model call

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

#### Scenario: The pronoun tally of every spelling is pooled

- **WHEN** a person is written under several spellings
- **THEN** the pooled tally decides a gender without the model (five or more pronouns, 90 % agreeing) and is the tally
  the review checks the model's gender against

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

### Requirement: Show the glossary as an editable table on Names & style

The Names & style screen SHALL show the glossary as an editable table with the columns Term, Type, Target (an empty one
shows the placeholder "No target"), Gender, a Locked switch and a last column that holds the row's remove cross, with an
info banner and a large primary Start translation button; WHILE no book is open, it SHALL instead report that no book is
open with a route to the import screen, and show no table, no scan and no Start translation.

**Source**: FR-GLOSS-02, FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `#empty-states`, `docs/specification/mockups/ui-mockup.html` (Names & style). In plain words: this is where the person settles spellings before the run starts — optional in automatic mode, which the banner says — and the run can be started from here without going back. The banner is true: the run adds the names it meets at the end of each chapter (the translation-pipeline capability's "Propose new names at the end of each body unit"). Like the brief, the structure and the export screens, this screen is built out of an open book, so with none open it says so instead of showing an empty table. When a start is offered is app-shell's "Move through the workflow by its available steps"; while a run can be resumed the button leads to it rather than beginning a second run, and it keeps its name so the screen does not change under the person's hand.

#### Scenario: A scanned entry appears unlocked

- **WHEN** the scan proposed `Hale`
- **THEN** its row shows Term `Hale`, Type `other`, an empty editable Target with the placeholder "No target", Gender
  "—" (it is not a character) and the Locked switch off

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

#### Scenario: The Gender column

- **WHEN** the table shows a row's gender
- **THEN** it reads female, male, neuter or unknown for a character and "—" for any other type, with "(suggested)" only
  on a gender that was suggested and not confirmed

#### Scenario: A changed row is marked

- **WHEN** the last model operation changed a row
- **THEN** the row wears a "Changed" chip in its Term cell
- **AND** the "Changed in last run" switch is offered only while some row is marked

#### Scenario: The glossary and the Recurring terms are two equal cards

- **WHEN** Names & style is shown at a content width of 1040 px or more, and then below it
- **THEN** the two cards, built from one widget (search, + Add, Scan, Review, Translate, Stop while one runs, Import,
  Export, the same table look), sit side by side at 1040 px or more and stacked below it
- **AND** a card too narrow for its toolbar folds Import and Export into a three-dot menu first, so the toolbar stays on
  one line at 960 px and at 1024 px

#### Scenario: The banner and the Start translation button

- **WHEN** a book is open
- **THEN** the screen shows an info banner reading `Skip this and the app builds names on the fly as it translates.`
  and a large primary Start translation button

#### Scenario: Start translation is pressed while no start is offered

- **WHEN** Start translation is pressed while the project's run is running, paused, stopped or in the provider-error
  state, or it completed with nothing pending
- **THEN** nothing starts and the translating screen is shown
- **AND** the button keeps its label `Start translation`

#### Scenario: The Type column and the remove column

- **WHEN** the table shows a row
- **THEN** its Type is one of character, place, term, title or other, and the Target is editable
- **AND** the last column has no header text and holds the row's remove cross

### Requirement: Review the glossary with the model when the person asks

WHEN the person presses Review with model on Names & style, the application SHALL decide by code each unlocked open entry
whose pronouns alone decide its gender, read the entries "Decide a read name from its readings by code" says, and send
the chosen model every other unlocked entry with no target or a suggested one for a verdict. It SHALL change the
glossary only after every call has answered; IF any call fails or the person presses Stop, THEN it SHALL change nothing.

**Source**: FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-review`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`). In plain words: a frequency count cannot tell a sentence-opening interjection from a name in every case, and it never knows a type or a gender, so the model is asked to clean and classify the list. It sees how often the book uses each word in lower case too, which is the clearest sign of a common word. A set type means someone — the person or an earlier model scan — already judged the entry a name, so only an untouched row is removed; and a row the person locked or gave a target is theirs and is never sent.

#### Scenario: The review removes a common word and classifies a name

- **WHEN** the glossary holds `Well` and `Hale` (other, unknown), `Moreau` (character, unknown), `Milton` → `Мілтон`
  locked and `Baker Street` → `Бейкер-стріт`, and the model answers `Well` not a name, `Hale` a name (person, male),
  `Moreau` a name (place, female), and `Milton` and `Baker Street` not a name
- **THEN** `Well` is removed and remembered as removed, `Hale` reads character, male (suggested), `Moreau` reads
  character, female (suggested) — no window shows either by a pronoun — and `Milton` and `Baker Street` are unchanged
- **AND** only `Well`, `Hale` and `Moreau` were sent, and the line reads `Model review done: 1 row removed, 2 rows
  updated, 0 targets suggested.` when the suggestion call suggests nothing

#### Scenario: A gender stands only on a window that shows it

- **WHEN** the book reads `Chrome nodded. "He never pays," said Tiger.` and the model answers `Chrome` a person, male,
  citing that window
- **THEN** `Chrome` reads male (suggested): the `he` is inside the speech, so no window shows the gender
- **AND** had the model answered female citing a window `Chrome stood. She left.`, `Chrome` would read female

#### Scenario: Five agreeing pronouns need no model

- **WHEN** an open entry `Wren` is followed by `she` in five narration sentences of the book and by no `he`
- **THEN** `Wren` is not sent and reads character, female

#### Scenario: A term locked while the model thinks is left alone

- **WHEN** the person locks `Well` with the target `Ну` while the review's call is out, and the model judges `Well`
  not a name
- **THEN** `Well` → `Ну` stays, locked

#### Scenario: A failed or stopped review changes nothing

- **WHEN** the glossary holds 41 open entries and the second call fails with `ErrorCode.unreachable`, or the person
  presses Stop while a call is out
- **THEN** the glossary holds the same 41 entries as before

#### Scenario: Pronouns alone decide a gender

- **WHEN** an unlocked open entry has five or more pronouns the code gives the name outside quoted speech (straight
  quotes included when they pair up) with no other name between, 90 % agreeing
- **THEN** it is decided without the model as a person of that gender

#### Scenario: The calls are cut by a budget and carry windows

- **WHEN** the other unlocked entries are sent
- **THEN** the calls are cut by a budget of about 5,000 input tokens and at most 40 entries, each entry with how many
  times the book uses it in any case, the code's pronoun tally and numbered windows
- **AND** the windows are four uses (six above 30 uses, two for a place), the first and one from each equal stretch of
  the rest, a narration window whose pronoun the code gives the name preferred, each the sentence with one before and
  one after

#### Scenario: The verdict is asked under a strict schema

- **WHEN** the model is asked about an entry
- **THEN** it answers a verdict — name, term or not a name — with a type (person, place, org, term, title or other), a
  gender (male, female, neuter or unknown) and the numbers of the windows it relied on

#### Scenario: Verdicts are written against each entry as it then stands

- **WHEN** every call has answered
- **THEN** the application removes through the glossary (so the removal is remembered) each entry judged not a name
  whose type is still other and gender still unknown
- **AND** it sets the type of an entry still of type other to the model's guess

#### Scenario: A gender is verified only by the windows it cites

- **WHEN** the model gives a gender for a person still of gender unknown
- **THEN** it is set as decided when most of the distinct windows it cites (strictly more than half, and at least two
  unless fewer were shown) hold a pronoun of that gender the code gives the name and the book-wide pronoun tally does
  not favour the other gender (three or more opposing pronouns making 60 % or more of the counted ones)
- **AND** otherwise it is set as a suggestion nobody has verified — against a favoured tally the suggestion is the
  tally's gender

#### Scenario: Suggested targets, locked entries and the summary line

- **WHEN** the verdicts are written
- **THEN** the suggested targets that "Suggest target renderings with the model" asks for are written after the
  verdicts, in the same action
- **AND** a locked entry and one whose target the person chose are not changed, and the glossary is shown with the line
  `Model review done: N rows removed, M rows updated, K targets suggested.`

#### Scenario: The calls are announced, stoppable and sent only on the button

- **WHEN** the review runs
- **THEN** its calls are announced, timed, attempt-counted and stoppable as the model scan's are
- **AND** they are not sent unless the person presses the button

### Requirement: Decide a read name from its readings by code

The review and the names pass SHALL share one path: of the open entries a strong pronoun case does not decide, those
the quality dial's names plan reads (Fast: a likely character written five times or more; Balanced and Max: any entry
written three times or more; at most 80) SHALL be read in context, one term per call (`term-reading`), and the code —
not the model — SHALL decide each from its readings, by the rules of the scenarios below.

**Source**: 15i.N5 (`openspec/changes/complete-translation-workflow/tasks.md`), FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#term-summary`. In plain words: the model reads, the code decides. A small model's pronoun answers were wrong one time in ten or more, so for now they only settle what the book's own pronouns leave open.

#### Scenario: Six windows read a character

- **WHEN** at Balanced the untyped `Brann` is written six times and its one reading call reads every window as a
  person's name, quoting `He nodded` from each, and the code reads no pronoun after `Brann`
- **THEN** `Brann` reads character, male (suggested), and no batched review call is sent for it

#### Scenario: A clear tally outweighs the readings

- **WHEN** the readings quote `he` for `Brann` in six windows and the code counts `she` four times after the name
- **THEN** `Brann` reads female (suggested, since no vote agrees) and no tiebreak call is made

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

#### Scenario: An honorific, a title or a common noun is not a character (15j.J10)

- **WHEN** the windows call a term a person but it is an honorific or a title of the source language (the bundled title
  list, or a run of capitalised words ending in a word of the language file's `officeTitles`: `Mrs`, `Duchess`, `Prime
  Minister`)
- **THEN** it is a title entry with no gender
- **AND** WHEN it is a common noun — a stop word or a never-alone word of the source language, or a word the book
  writes in lower case a fifth of the time or more with no clear pronoun tally — it stays unsure and carries no gender

#### Scenario: Only a person carries a gender

- **WHEN** an entry is a place, an organisation, a thing or a title
- **THEN** it carries no gender
- **AND** a gender the person set is never decided again

#### Scenario: A person with no evidence stays unspecified

- **WHEN** the book gives a person no evidence — a pronoun tally of 0/0 (forms of address included) and no verified vote
- **THEN** the gender stays `UNKNOWN` (not specified) and is marked as a gender the scan found no evidence for
  (`GlossaryEntry.noGenderEvidence`); any later gender and the person's own edit clear the mark
- **AND** the Names & style gender cell reads "not specified — no evidence in the book" (Ukrainian: "не вказано — у
  книзі немає свідчень") instead of "unknown", the column's tooltip explaining it, and the names suite reports gender
  over the persons with evidence beside the gender over all persons

#### Scenario: The model's votes only break ties

- **WHEN** the verified votes (a window's pronoun the reading quoted from that window's narration) of every spelling of
  the person are pooled and the model's votes were found wrong more than 5 % of the time by the coreference probe —
  every model today (26b 10 %, e4b 20 % on 2026-10-13)
- **THEN** the votes only break ties: a clear code tally (three or more pronouns making 60 % or more) decides, written
  verified when the verified votes agree with it and none opposes it, or when it is strong (five or more pronouns making
  90 % or more) and no verified vote opposes it (15j.J2), and as a suggestion otherwise
- **AND** with no clear tally two or more votes making 75 % (three at 80 % under Auto), or two or more pieces of
  evidence (votes and pronouns together) all of one gender, give a suggestion, never a verified gender
- **AND** a mode in which a passing model's votes decide, with a `term-tiebreak` call against a dominant tally, returns
  with the first model that passes the probe and is not built until then

#### Scenario: The tally counts a name's own uses once

- **WHEN** the tally counts a name's uses
- **THEN** a use that ends a longer name is the longer name's, a use after a gendered form of address counts that form
  unless the surname follows both genders' forms, and a name opening with such a form has its gender
- **AND** each window and quote counts once however many spellings' calls show it

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

### Requirement: Suggest target renderings with the model

WHEN the model review or the model scan has its verdicts, the application SHALL, in the same action, send the open
entries (unlocked, with no target or a suggested one) to a separate suggestion call, at most 20 per call, asking for each
a dictionary-form target under a strict schema, keep only suggestions that pass the checks below, and write them as
suggested targets after every call has answered; IF a call fails or the person presses Stop, THEN it SHALL write nothing.

**Source**: FR-GLOSS-01, FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`). In plain words: a glossary of names with no targets leaves every draft to spell each name afresh, so the model proposes one spelling per name, following the policy the person chose. It is a call of its own because a small model does one task per call far better than two. A suggestion is only a hint until the person confirms it — the draft may inflect it — and the model never undoes the person's choice. On gemma4:e4b every place and thing came back "neuter", masculine Ukrainian nouns included, and a person's gender judged from one sentence took a speaker's `he` (15h.A2), so the reply's gender is not written; and the same model wrote a Latin `e` inside `Венс`, which reads right and matches nothing.

#### Scenario: Transliterated names get Cyrillic targets

- **WHEN** the policy is Transliterate, the target is Ukrainian and the review keeps `Hale` (character, male) and
  `Moreau` (character, female), and the model suggests `Гейл` and `Моро`
- **THEN** `Hale` reads `Гейл` and `Moreau` reads `Моро`, each suggested and slanted with the `AI` badge, and the line
  reads `Model review done: 1 row removed, 2 rows updated, 2 targets suggested.`

#### Scenario: A copied name is no transliteration

- **WHEN** the policy is Transliterate and the model answers `Hale` for `Hale`, and `Hale` again to the second ask
- **THEN** `Hale` keeps no target, while for English to German the same answer is kept

#### Scenario: A rendering that does not open as the name sounds is asked again

- **WHEN** the policy is Transliterate, the target is Ukrainian and the model answers `Карло` for `Arlo` and `Венн`
  for `Venn`, and then `Арло` for `Arlo`
- **THEN** the second call carries only `Arlo`, with `Карло` named as refused, and `Arlo` reads `Арло`, `Venn` `Венн`

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

#### Scenario: A suggested name that loses a word or holds a foreign letter is asked again

- **WHEN** the model suggests `Варно` for `Varno City`, `Сарроу` for `Charrow`, `Валдором` for `Valdor` or `Сын` for
  `Son`
- **THEN** each is refused, named with its reason in one further call, and an entry refused twice has no target

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

- **WHEN** the verdicts answered and the suggestion call fails with `ErrorCode.timeout`
- **THEN** the glossary holds the same entries as before the review, verdicts included

#### Scenario: The call carries the entry's evidence and a strict schema

- **WHEN** the entries are sent to a suggestion call
- **THEN** each carries its type, its gender when known, `speaking name` and `brand` when the term reading says so, what
  the glossary already renders of its person (`Arlo Venn → Арло Венн, so Venn → Венн`, for a spelling of a person whose
  full name has a rendering) and up to two sentences of the book that hold it (each cut at 120 characters)
- **AND** the reply is `{"suggestions":[{"term","target","gender"}]}`, at most 20 items, a target at most 80 characters,
  `""` meaning no suggestion

#### Scenario: The system message states the name policy

- **WHEN** the suggestion call is built
- **THEN** its system message states the Book Brief's name policy: Translate asks for the natural target-language
  equivalent a published translation would print, transliterating only a name with none; Transliterate asks for the name
  spelled in the target language by its sound, by the target language's own convention for foreign names when the
  application bundles one (for Ukrainian, the orthography's practical transcription of foreign names) and a neutral
  sentence otherwise, a real place or person keeping an established target-language name
- **AND** both translate a name made of ordinary words and a domain term by meaning

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

#### Scenario: Under Transliterate each word must open as its source word sounds

- **WHEN** the policy is Transliterate and a rendering is checked
- **THEN** it is refused WHEN its words do not each open as a source word transliterates, by the `translit` letter table
  of the target's language file (the opening letter or digraph of the source word, the longest key first, against the
  opening letter of a word of the rendering, in any order)
- **AND** a word the table has no row for is not judged; an empty table judges no letters, while a lost word is still
  refused since it needs no table; a capitalised word of the rendering that stands for no source word is refused

#### Scenario: A last word with a non-nominative ending is refused

- **WHEN** the policy is Transliterate and a rendering's last word is the source's own transliteration plus an ending in
  the language file's `nonNominativeEndings` (endings no nominative has: `-ом`, `-ем`, `-ові`, `-ами` and the like; `-а`,
  `-у` and `-і` are left out, for `Анна`, `Павло` and `Джессі` are dictionary forms), the letter before the ending being
  one the `translit` table gives for the name's last letter and the stem no shorter than the source word, and the last
  letter of the name is not one the source language's `openNameFinals` lists
- **THEN** it is refused: `Valdor` → `Валдором` is refused for `Валдор`
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

#### Scenario: The renderings of one person and of one word agree

- **WHEN** one person is written under several spellings
- **THEN** a spelling takes the word the full name's rendering has in its place, the full name's rendering being the one
  the glossary holds or else the one asked in the same batch, and a full name asked about takes the words of a held
  rendering of one of its spellings
- **AND** beyond one person, a word of a character's name has one rendering in every character's name whose rendering has
  as many words: the glossary's rendering of the word, else the first one a call gives (a surname two full names share,
  which no cluster joins)

#### Scenario: The suggestions are written against each entry as it then stands

- **WHEN** every call has answered
- **THEN** a suggestion is written into an unlocked entry whose target is empty or was suggested, as a suggested target,
  and the reply's gender is not written (a person's gender is the review's)
- **AND** a target the person typed, imported, accepted or locked is not overwritten, the writes record no deferral, and
  while the calls run the screen shows `Suggesting renderings B/N` with the batch under way

### Requirement: Lock a suggested rendering only where names do not inflect

The application SHALL lock a rendering by rule only for an identifier, a kept original (a name, or a brand, under Keep
original), or a verified proper name that is not a common noun, with a checked rendering, not marked speaking, in a
target whose language file says `namesInflect=false`; otherwise it stays unlocked guidance held by the name checks. Every
writer of a suggestion SHALL apply the rule.

**Source**: FR-GLOSS-01, FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`). In plain words: a name locked behind a placeholder cannot inflect, so it is locked only where the target language does not decline names.

#### Scenario: Which targets decline names

- **WHEN** the target is English, German, French, Spanish or Portuguese (the language file says `namesInflect=false`)
- **THEN** a verified proper name that is not a common noun, with a checked rendering and not marked speaking, is locked
- **AND** for Ukrainian, Russian, Polish, Czech, Croatian, Slovak, Slovenian and any language with no such key it stays
  unlocked guidance held by the name checks

#### Scenario: The same name is locked after Review, Translate and the scan alike

- **WHEN** a rendering is written by the review's commit, the translate-only action or the name scan
- **THEN** the lock rule gives the same result for the same name

#### Scenario: A brand follows the name policy exactly as a person's name does (15j.J1)

- **WHEN** an entry any of whose readings says `brand` — the trade name of a company or a product line and never a
  material, a substance, a drug, a unit or any other common noun — is rendered
- **THEN** it is written in the target's alphabet under Transliterate, rendered by the model under Translate, and its
  source and locked under Keep original (this replaces 15i.T4's lock of every brand to its source)
- **AND** under Keep original every name-like entry gets its source as target and is locked; a single unverified title
  or other word (a demoted common noun) is asked about and translated

#### Scenario: "Verified" means decided by the in-context readings

- **WHEN** a name is decided as a name by the in-context readings
- **THEN** it is verified
- **AND** a decision by the batch review or the pronoun count alone is not

#### Scenario: Keep original locks every kept name

- **WHEN** the policy is Keep original and the open entries are a title `Weekly Courier Digest`, an organisation and a
  term `Amulet`
- **THEN** the title and the organisation read their sources, locked, after Review, Translate and the name scan alike,
  and `Amulet` is neither copied nor locked

#### Scenario: Names are locked only where they do not inflect

- **WHEN** `Venn` is a verified proper name, not speaking, rendered `Venn` and checked
- **THEN** it is locked when the target is English and stays unlocked guidance when the target is Ukrainian

### Requirement: Show a suggested target as a hint and let the person accept it

The Names & style table SHALL show a suggested target slanted with an `AI` badge and a tick that accepts it; Enter in the
field with the suggestion unchanged SHALL accept it too, and typing another target or turning the lock on SHALL make the
target the person's. An `Accept all` button beside Review with model SHALL accept every suggested target.

**Source**: FR-GLOSS-01, FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`). In plain words: a suggestion is only a hint until the person confirms it, and the draft may inflect it.

#### Scenario: Accept all is shown only while a suggestion is

- **WHEN** no suggested target is shown, then one is
- **THEN** the `Accept all` button is not shown, then shown, so the toolbar keeps to one line at 1000 px
- **AND** it is off while a model action runs

#### Scenario: A run started with suggested targets left notes them

- **WHEN** a run is started from Names & style while suggested targets remain
- **THEN** the application starts it and notes `K suggested targets are unreviewed and will be used as hints.`

#### Scenario: CSV treats a suggested target as plain

- **WHEN** the glossary is exported to CSV, and a CSV is imported
- **THEN** the export writes a suggested target as a plain target
- **AND** the import reads every target as the person's

#### Scenario: Accepting a suggestion

- **WHEN** the person presses the tick, or Enter in the unchanged field, on `Hale` → `Гейл` suggested
- **THEN** `Hale` reads `Гейл` as the person's target, with no badge

### Requirement: Compare glossary terms by one key

The application SHALL compare glossary terms — for a duplicate in the repository, the Add term dialog and a CSV import,
for a held or removed term in every scan, and for an entry's id — by one key: the term in Unicode NFC, with leading and
trailing punctuation and a trailing possessive `’s` or `'s` removed, inner spaces collapsed to one, and the case folded.

**Source**: FR-GLOSS-02, FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`). In plain words: before, each way in compared differently, so `Lovelace’s` could be held beside `Lovelace` and a removed name could come back in another spelling.

#### Scenario: Spellings of one term share a key

- **WHEN** the glossary holds `Hale` and `Hale's`, `Hale’s`, ` HALE ` or `"Hale,"` is added
- **THEN** the addition is refused as a duplicate, and a removed `Hale` is still removed when `Hale’s` is proposed

### Requirement: Sort and search the glossary table

The Names & style table SHALL sort by Term, Type, Gender or Locked when the person chooses that column's header,
breaking every tie by the source term, so that storing a change to any other column — a typed target above all — never
moves its row; and it SHALL offer a search field that shows only the rows whose source term or target contains the
typed text, ignoring case, and that the Escape key clears.

**Source**: FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`. In plain words: a real book yields a hundred rows or more, in the order the scan found them; a person settling them needs to find one name and group the rows by kind. The glossary keeps no count of mentions, so there is no count column.

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

**Source**: FR-GLOSS-03, FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `#dialog-add-glossary-term`. In plain words: a locked term is replaced by its target wherever it occurs; with no target there is nothing to put back, so the name would vanish from the book. Every way into the glossary refuses such an entry rather than one of them letting it through.

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

**Source**: FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-add-glossary-term`. In plain words: a name the scan missed can be added by hand; two entries for the same name would contradict each other, so the second is refused rather than silently merged. A locked term also needs a target (see "Lock only an entry that has a target").

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

**Source**: FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`. In plain words: a series keeps its names across books, so a glossary must travel as a plain file any spreadsheet can open; one bad line must not throw away a hundred good ones. A row that would lock a term with no target is refused like a malformed one (see "Lock only an entry that has a target").

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

**Source**: FR-GLOSS-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), FR-ALGO-C9 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, ADR-0034. In plain words: the glossary grows while the book translates — at the end of each chapter the run adds the names it has met (the translation-pipeline capability's "Propose new names at the end of each body unit") — and none of that growth may undo the person's work: a spelling they settled stays, and a false name they deleted does not come back at the next chapter. Adding a found name never costs the chapter's stored decisions either. The model scan, pressed after a removal, honours it the same way: a name the model proposes again is still a name the person deleted (see "Run the model scan only when the person asks for it").

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

#### Scenario: A removed recurring term is not scanned back

- **WHEN** the person removed the recurring term `beacon`, and the model scan reads it as a term used in one sense
- **THEN** the recurring terms do not hold `beacon`; had the person added it again first, it would stay

#### Scenario: The person can add a removed term back

- **WHEN** the person removed `Moreau` and then imports a CSV holding the line `Moreau,Моро,character,male,false`
- **THEN** the glossary holds `Moreau` → `Моро`, character, male, unlocked

#### Scenario: A removed recurring term is not added back by any scan (15i.N8)

- **WHEN** the person removed a recurring term, or a model dropped it in a review
- **THEN** the text scan, the model scan and the names pass do not add it back this session
- **AND** it is added only after the person adds it again

### Requirement: Render a locked term exactly as entered

WHILE a glossary entry is locked, the application SHALL hide each whole-word occurrence of its source term behind a
placeholder before the segment reaches the model and SHALL restore it with the entry's target rendering, never
matching the term inside a longer word or inside another placeholder.

**Source**: FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), EC-INLINE-3 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`. In plain words: the model never sees a locked name, so it cannot misspell it; matching whole words only keeps `Hale` from being substituted inside `Whale`. A model that drops the placeholder fails the protected-span hard gate (see the quality-gates capability's "Treat each protected span's placeholder as a hard gate").

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
a whole word in that chunk, each with its type and gender. An entry whose target the model suggested and nobody
confirmed SHALL be listed apart and SHALL never be hidden behind a placeholder.

**Source**: FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`. In plain words: a small model's context is precious, so a two-hundred-entry glossary is trimmed to the names in play; type and gender let the model agree verbs and adjectives with a character in languages that inflect for them. A locked name is hidden behind a placeholder, so the model is told what that placeholder will read as — otherwise it could not make `відчинив` or `відчинила` agree with it.

#### Scenario: Only present terms are given

- **WHEN** the glossary holds `Hale`, `Milton` and `Baker Street` and the chunk mentions only `Milton`
- **THEN** the prompt lists `Milton` → `Мілтон` (place) and neither `Hale` nor `Baker Street`

#### Scenario: A locked name is listed by its placeholder

- **WHEN** `Hale` → `Гейл` (character, male) is locked and the segment `Hale opened the door.` is sent as
  `⟦g0⟧ opened the door.`
- **THEN** the prompt lists `⟦g0⟧ → Гейл, character, male`

#### Scenario: An entry with no target gives type and gender only

- **WHEN** the glossary holds `Moreau` (character, male, no target) and the chunk mentions `Moreau`
- **THEN** the prompt lists `Moreau` with `character` and `male` and no rendering

#### Scenario: A suggested rendering is a hint

- **WHEN** `Hale` → `Гейл` (character, male) is suggested and `Milton` → `Мілтон` is the person's, and the chunk mentions
  both
- **THEN** the prompt lists `Milton → Мілтон (place)` under the glossary heading and `Hale → Гейл (character,
  male)` under the suggested-renderings heading, and `Hale` is sent as written

#### Scenario: An empty glossary adds nothing

- **WHEN** the glossary is empty
- **THEN** the prompt carries no glossary terms

#### Scenario: Each entry is listed by what it holds

- **WHEN** a chunk names an unlocked entry with the person's target, an entry with no target and a locked entry
- **THEN** the first is listed with its target rendering as guidance, the second by its type and gender alone, and the
  locked one — which the model sees only as a placeholder — as that placeholder with its rendering, type and gender

#### Scenario: The suggested renderings have their own heading

- **WHEN** a chunk names an entry whose target the model suggested and nobody confirmed
- **THEN** it is listed under `[Suggested renderings — not confirmed by the person]` with the line `Use each rendering
  unless it is clearly wrong; inflect it as the sentence needs.`
- **AND** it is never hidden behind a placeholder

### Requirement: Keep a lexicon of recurring terms and show it on Names & style

The application SHALL keep, for each project, a lexicon of recurring common terms and titles (`master`, `imp`, `Mr`)
that a model would otherwise render differently from one page to the next, SHALL propose them with a deterministic scan
and no model, and SHALL show them on Names & style in a "Recurring terms" card beside or under the glossary. A term the
glossary holds SHALL NOT be asked about or shown from the lexicon: the glossary is the person's word.

**Source**: `additional-work.md` 15d.9; `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`. In plain words: a book that calls a master "господар" in chapter one and "учитель" in chapter nine reads as two characters; the app finds the words a book keeps repeating, learns from the drafts which rendering the book is using, and keeps every later request to it, while leaving the person free to choose, or to make an entry a hard glossary rule.

#### Scenario: The scan proposes a title and a dual-use word

- **WHEN** a book's running text holds `Mr.` four times, and `Imp` capitalised mid-sentence three times and `imp` in lower
  case three times
- **THEN** the scan proposes `mr` and `imp` and nothing for a name written only with a capital or a word written only in
  lower case

#### Scenario: A held term is not proposed

- **WHEN** the glossary holds `Imp` and the lexicon holds `MR`
- **THEN** the scan proposes neither

#### Scenario: Editing a rendering

- **WHEN** the person types `пан` in the rendering field of `master` and presses Enter
- **THEN** the lexicon stores `пан` as the person's rendering of `master`, every later request is asked to keep it, and
  clearing the field returns to the most used verified rendering

#### Scenario: Promoting a term

- **WHEN** the person presses "To glossary" on `master`, whose established rendering is `господар`
- **THEN** the glossary holds `master` → `господар` (term, unlocked, the person's target) and the Recurring terms card no
  longer lists `master`

#### Scenario: A duplicate term is refused

- **WHEN** the person types `IMP` in the add field while `imp` is listed
- **THEN** the line above the glossary says `IMP is already in the glossary or the list` and no term is added

#### Scenario: What the deterministic scan proposes

- **WHEN** the scan reads the book's running text
- **THEN** it proposes a title of the source language's bundled list (English first) that the text uses at least three
  times, and a word the book writes both with a capital away from a sentence start and not beside another capitalised
  word (so it is no part of a name such as `Great Hall`), and in lower case (so it is neither a name nor a function word)
  at least five times, at most thirty
- **AND** never a term the glossary or the lexicon already holds

#### Scenario: When the scan runs

- **WHEN** a run is prepared and the lexicon is empty, or the person asks on Names & style ("Find recurring terms")
- **THEN** the scan runs
- **AND** a word that is only ever lower case, such as `pentacle`, is added by hand

#### Scenario: What the card shows

- **WHEN** the Recurring terms card lists a term
- **THEN** it shows the term and its Target, the rendering later requests are asked to keep (the person's if typed, else
  the learned or most used verified rendering, else the model's suggestion), and there is no separate column for the
  renderings the drafts used

#### Scenario: What the card lets the person do

- **WHEN** the person works in the card
- **THEN** they can edit the rendering (Enter or leaving the field stores it; clearing it returns to the most used),
  suggest renderings with the model (the glossary's suggestion call over terms of type term, one model action at a time
  with the name scan and review, needing a chosen model), add a term by hand (a small card asks for the word; one the
  glossary or the list already holds is refused in the screen's banner), and remove a term
- **AND** they can promote a term to the glossary, which stores it there as an unlocked entry of type term with its
  established rendering as the target and removes it from the lexicon

#### Scenario: The lexicon lives in memory and an edit changes the stored entry

- **WHEN** the application keeps the lexicon, and an edit of a rendering, a stored suggestion or a promotion is made
- **THEN** the lexicon lives in memory behind a storage port, like the other project data, and does not survive closing
  the application
- **AND** the edit changes the entry as it stands in the store, in one step, never from a copy read earlier: a count a
  running draft records meanwhile is kept, and a term removed or promoted meanwhile is not created again (the edit
  answers that the term is unknown)

### Requirement: Read recurring terms in context with the model

WHEN the person asks the model to find or review recurring terms, the application SHALL read each candidate term in its
own call at the Balanced and Max quality dials, in numbered windows of the book, and decide from the answers by code. A
window SHALL count only when the quote the model gave for it stands in that window. A failed call SHALL end the action
with its error and write nothing.

**Source**: `additional-work.md` 15i.N7; `docs/specification/01_Product/12_PROMPT_CATALOG.md#lexicon-reading`. In plain words: a word such as "bank" that means a river's edge in one chapter and a business in another cannot have one rendering, so the model is shown the word in its places and the code, not the model, decides; and a series can carry its recurring terms from one book to the next in a file.

#### Scenario: A word of one sense is kept with its rendering

- **WHEN** the model reads `beacon` at Balanced, every window quoted from the text uses it as a term, one sense, rendering
  `маяк`
- **THEN** the lexicon holds `beacon` with `маяк` as a model-written rendering

#### Scenario: An ordinary or two-sense word is dropped

- **WHEN** the model reads `ferry` and calls most windows "word", or reads `bank` and says the windows show two senses
- **THEN** the term is not added in a scan and is removed in a review

#### Scenario: A named thing moves to the glossary as a suggestion

- **WHEN** the model reads `signal fire` and calls most windows "name", rendering `Сигнальне Вогнище`
- **THEN** the glossary holds it as an unlocked term whose target is marked suggested and the lexicon does not

#### Scenario: A person's rendering outranks a model's and a file's

- **WHEN** the lexicon holds `master` with `пан` typed by the person and an import row says `master,володар`
- **THEN** `master` keeps `пан`; had a reading written `пан`, the import would have replaced it

#### Scenario: The windows are 3 of one paragraph at Balanced and 4 of two at Max

- **WHEN** a candidate term is read at Balanced, then at Max
- **THEN** it is shown in 3 windows of one paragraph either side at Balanced and 4 of two at Max

#### Scenario: The answers are decided by code

- **WHEN** the windows are counted
- **THEN** a term most counted windows use in one sense is kept, and an ordinary word (most counted windows) or a word
  with two senses or more is dropped
- **AND** a named thing (most counted windows) moves to the glossary unless the book writes the word in lower case in at
  least a fifth of its uses (the name scan's lower-case bar), in which case it stays a recurring term with the rendering
  read, and a term nothing readable decided is left as it is

#### Scenario: Fast reads the candidates in one batch choice

- **WHEN** the dial is Fast
- **THEN** the candidates go to one batch choice over one example sentence each, which keeps or drops and gives no
  rendering
- **AND** that call is labelled "Term choice", not "Name review"

#### Scenario: The model scan's candidates are the most frequent words

- **WHEN** the model scan chooses its candidates
- **THEN** they are the book's 300 most frequent words that are no stop word and no held term
- **AND** at Balanced and Max its 30 most frequent are read each in its own call, and the others only where that batch
  choice keeps them, a word it drops being dropped and one it leaves out left undecided

#### Scenario: A kept or moved term takes the one rendering the reading gave

- **WHEN** a term is kept or moved
- **THEN** it takes the one rendering the reading gave for every window, written as the model's rendering, and a review
  does not ask a term that already has a rendering
- **AND** a rendering the person typed or imported is not replaced by a reading or by a file import, while a rendering a
  reading wrote is replaced by a person's edit or an import

#### Scenario: A rendering that is the source spelling is refused

- **WHEN** a rendering is the source spelling, across scripts, and the term is not an identifier
- **THEN** the rendering is refused and the term is kept with none

#### Scenario: A term moved to the glossary enters as a suggestion

- **WHEN** a term is moved to the glossary
- **THEN** it enters as an unlocked term whose target is a suggestion nobody has confirmed

### Requirement: Exchange the recurring terms as a CSV file

The Recurring terms card SHALL search its terms and renderings and SHALL import and export its terms, through the same
file dialog seam as the glossary (suggested name `recurring-terms.csv`), as an RFC 4180 CSV file in UTF-8 with the header
`term,target`.

**Source**: `additional-work.md` 15i.N7; `docs/specification/01_Product/12_PROMPT_CATALOG.md#lexicon-reading`. In plain words: a series can carry its recurring terms from one book to the next in a file.

#### Scenario: The file format

- **WHEN** the card exports its terms
- **THEN** the file has CRLF line ends, a field quoted only when it holds a comma, a quote or a line break, the terms
  sorted, and the target the established rendering or empty

#### Scenario: An import adds what is missing and refuses what the glossary holds

- **WHEN** a file is imported
- **THEN** it adds the terms the lexicon lacks and gives a term without a person's rendering the row's rendering
- **AND** it refuses a row whose term the glossary holds, names every malformed line, and imports the good rows regardless

#### Scenario: An import names the lines it cannot use

- **WHEN** a file has the header, a good row, a row with three fields and a row for a term the glossary holds
- **THEN** one term is imported and the report names the malformed line and the refused line

### Requirement: Prepare names and recurring terms in one pass

WHEN the application prepares names and recurring terms in one pass, it SHALL run the steps the Names & style buttons
run, in this order, on a private copy of the glossary and of the recurring terms: scan the book for names when the
glossary is empty, read and decide the names, read the recurring terms, ask a rendering for every name and recurring term
that has none, and lock by rule. It SHALL write the stored glossary and recurring terms once, after its last step.

**Source**: `additional-work.md` 15i.N8; `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`. In plain words: one button should get from a book to a prepared glossary without five separate presses, and an answer that arrives half-way, or a Stop, must not leave half of a result behind; and what the person fixed by hand stays fixed.

#### Scenario: A pass fills both tables and writes them at the end

- **WHEN** the glossary holds the untyped names `Brann`, `Harrowgate` and `Overture`, the recurring terms hold `ferry`,
  and the model reads `Brann` as a man followed by "he", `Overture` as not a name, `ferry` as an ordinary word and
  `beacon` as a term
- **THEN** every model call goes out against the tables as they were, and afterwards `Brann` is a male character with a
  suggested rendering, `Overture` and `ferry` are gone, and `beacon` is a recurring term with its rendering

#### Scenario: A brand under Keep original is locked, a name in a language that declines names is not

- **WHEN** the policy is Keep original, the target is Ukrainian and the model reads `Zorbo` as a brand and `Brann` as a
  person, with the renderings `Zorbo` and `Бранн`
- **THEN** `Zorbo` is locked with the target `Zorbo` and `Brann` is not locked
- **AND** with a German target a name read as a name and not a speaking one is locked as well, but never a common noun

#### Scenario: A stopped pass changes nothing

- **WHEN** the person presses Stop during the fourth model call
- **THEN** no fifth call is sent, the pass answers `cancelled`, and neither table has changed

#### Scenario: A failed call changes nothing

- **WHEN** the call that reads the recurring terms fails with `unreachable`
- **THEN** the pass answers `unreachable` and the glossary and the recurring terms are as they were

#### Scenario: The names step asks for no rendering

- **WHEN** the names step runs
- **THEN** it asks for no rendering: both tables are read before either is translated

#### Scenario: A name is locked with the rendering it was given

- **WHEN** a name is an identifier or kept as written by the name policy (the rendering is its own spelling)
- **THEN** it is locked with the rendering it was given
- **AND** WHEN the pass read it, decided it is a name and not a speaking one, and the target language does not decline
  names and the rendering passed the checks, it is locked as well

#### Scenario: The estimate of calls and minutes

- **WHEN** the application estimates the names step before the first model call, from the dial's plan over the glossary
  as it will stand after its scan
- **THEN** a call takes 3.75–7.5 seconds at Fast, 11.25–18.75 at Balanced and 11.25–22.5 at Max
- **AND** the minutes are rounded up at both ends

#### Scenario: Nothing is written when the pass stops or fails

- **WHEN** the person stops the pass (the calling thread is interrupted, after which no further model call is sent), a
  call is cancelled, or a call fails
- **THEN** nothing is written and the pass answers with that error

#### Scenario: What the person fixed stays fixed

- **WHEN** the pass runs over a locked entry, a target the person typed, a gender the person chose, a recurring term
  whose rendering the person typed, or a name or term the person removed
- **THEN** it changes none of them and adds none back
- **AND** a recurring term the person removed, or a model dropped in the pass, is not added back by a later scan this
  session, until the person adds it again

#### Scenario: The model name scan stays behind its gate

- **WHEN** the pass runs over an empty glossary
- **THEN** the deterministic scan runs, and the model name scan stays behind its gate

### Requirement: Offer the one-pass preparation on Names & style

The Names & style screen SHALL offer "Prepare names automatically" as a secondary button above both cards, with a muted
line under it that states the pass's estimate for the brief's dial. A press SHALL run the names pass on the chosen model,
and WHEN it succeeds, both tables SHALL be read again and the results dialog "Automatic names — results" SHALL open with
a "Names (n)" section above a "Recurring terms (n)" section, a Revert on each row and one Undo all across both.

**Source**: `additional-work.md` 15i.U6. In plain words: one button replaces five presses, shows what it will cost before it starts, and gives every change back one by one or all together.

#### Scenario: The press fills both tables and Undo all gives both back

- **WHEN** the person presses "Prepare names automatically" and the pass adds `Mid`, drops `Hale` and adds `pentacle`
- **THEN** the results dialog lists `Mid` and `Hale` under "Names (2)" and `pentacle` under "Recurring terms (1)"
- **AND** Undo all removes `Mid` and `pentacle` and puts `Hale` back

#### Scenario: Cancel on the busy card changes nothing

- **WHEN** the person presses Cancel on the busy card while the pass waits for the model
- **THEN** the pass is interrupted, the dialog says "Stopped. Nothing was changed." and both tables are as they were

#### Scenario: The person's entries are left alone

- **WHEN** the glossary holds a locked `Gatewarden`, a `Marrow Lane` with the person's target and a `Wren` whose
  female gender the person chose, and the lexicon holds `anvil` with the person's rendering
- **THEN** after the pass those entries are unchanged, `Wren` is still female, and `anvil` was not asked about

#### Scenario: A removal is remembered

- **WHEN** the person removed the recurring term `gantry` and the model would keep it
- **THEN** the pass does not add `gantry` back

#### Scenario: The estimate comes before the first call

- **WHEN** five open names are each mentioned at least three times at the Balanced dial
- **THEN** the estimate is 5 calls and 1 to 2 minutes

#### Scenario: The estimate line

- **WHEN** the screen opens, or a change of the dial has been saved
- **THEN** the line under the button is asked for again and states the estimate ("about 15–25 min on Balanced")
- **AND** it is blank when the port cannot estimate

#### Scenario: The button is off while a model action runs

- **WHEN** any model action runs, or no model is chosen
- **THEN** the button is off while a model action runs, and with no model chosen a press reports that in the screen's
  banner and calls nothing

#### Scenario: The pass is a blocking activity

- **WHEN** the pass runs
- **THEN** it shows the shared busy card "Preparing names", whose Cancel is the pass's Stop, and the computer is kept awake
- **AND** a stopped or failed pass leaves both tables as they were

#### Scenario: Changed rows are marked on both cards

- **WHEN** the pass succeeds
- **THEN** every changed row wears the "Changed" chip on its card
- **AND** "Show changed rows" turns on both cards' changed filters

### Requirement: Record the renderings a draft used and keep them consistent

WHEN a batch is drafted, the application SHALL give the model the closed list of the lexicon's key terms the batch's items
name, and the model SHALL answer, for each item that holds one, the rendering it used in the entry's optional `terms`
object; the application SHALL count only verified pairs of an item adopted from the batch, per project, as source →
rendering, and SHALL show every later draft and retry the established rendering of each term the chunk names.

**Source**: `additional-work.md` 15d.9; `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`. In plain words: the model reports what it used, the app checks the claim against the text so a model cannot teach the book a word it did not write, and the majority wins the conflict so one early slip does not outvote a book's habit; the metric `distinctRenderingsPerTerm` reads 1.0 when every recurring term was written one way.

#### Scenario: An inflected rendering is accepted

- **WHEN** the model reports `master → господар` for an item whose source holds `master` and whose target holds `господаря`
- **THEN** the pair is verified and `господар` is counted once for `master`

#### Scenario: A hallucinated pair is dropped

- **WHEN** the model reports `master → учитель` and the target holds no word with that stem, or reports a term the item's
  source lacks, or a term off the list
- **THEN** nothing is counted for it

#### Scenario: Conflict policy

- **WHEN** `господар` was used twice and `учитель` once
- **THEN** `господар` is established; with the person's rendering `пан` it is `пан`; with one use each it is the one seen
  first; with none used and a suggestion `пан` it is `пан`

#### Scenario: The second batch keeps the first one's rendering

- **WHEN** the first batch of a book renders `master` as `господар`, and a model that would otherwise write `учитель` in the
  next call is shown the established block
- **THEN** the second call's prompt lists `master → господар`, the book's `distinctRenderingsPerTerm` is `1.0` and the
  draft's snapshot records `master → господар`

#### Scenario: The glossary wins

- **WHEN** the glossary holds `Master` and the lexicon holds `master`
- **THEN** the prompt lists the glossary entry and no recurring-term line for `master`, and the batch does not ask about it

#### Scenario: The model's answer is an object or a list of pairs

- **WHEN** the model answers for an item
- **THEN** `terms` is an object such as `{"master":"господар"}`
- **AND** a list of `{"source","target"}` pairs is read the same way

#### Scenario: A pair counts only when it is verified against the item

- **WHEN** a pair is checked before it counts
- **THEN** the term is on the list, it occurs in the item's source as a whole word (an English plural or possessive
  tolerated), and every significant word of the rendering has its stem in the target
- **AND** an inflected rendering passes, a multi-word rendering needs every word, a rendering the target does not hold is
  dropped, and only verified pairs of an item adopted from the batch are counted

#### Scenario: The established renderings are shown with their own share

- **WHEN** a later draft or retry names lexicon terms
- **THEN** it shows `term → rendering` lines under `[Established renderings of recurring terms — keep consistent]`, with
  its own share of the dynamic context — a fifth of what the glossary leaves, after the glossary and before the
  translation memory, the preceding text and the summary
- **AND** the lines are recorded in the draft's context snapshot so a retry shows the same

#### Scenario: One conflict policy decides which rendering is established

- **WHEN** a term has several renderings
- **THEN** the person's rendering wins; else the rendering learned by co-occurrence; else the most used verified
  rendering, the one first seen when two are used equally often; else the model's suggestion; else none

#### Scenario: The reviewer sees the established renderings

- **WHEN** the reviewer is given a chunk
- **THEN** it gets the established renderings of the terms in the chunk with the glossary's, so one thing named two ways
  is a terminology finding

#### Scenario: The run logs and reports its lexicon figures

- **WHEN** a run ends
- **THEN** it logs once `lexiconTerms`, `used`, `distinctRenderingsPerTerm`, `conflicted`, `learned` and
  `learnedCoverage`
- **AND** the command's JSON report carries them in a `lexicon` object

### Requirement: Learn a recurring term's rendering from the decided segments

The application SHALL learn a lexicon term's rendering from the decided segments with no model call, so a model that never
returns `terms` still keeps the book consistent: once a chunk's records are stored, each decided pair SHALL be counted by
word stems, and a term SHALL get a learned rendering only when at least three of its segments carry one stem, the stem's
Dice association with the term is at least 0.6 and at least twice that of any unrelated rival.

**Source**: `additional-work.md` 15d.9; `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`. In plain words: the model reports what it used, but a model that never reports still has its decisions read, and the majority wins so one early slip does not outvote a book's habit.

#### Scenario: Which pairs are counted and how

- **WHEN** a chunk's records are stored
- **THEN** each decided pair (an accepted draft; never a flagged, verbatim, memory-reused or person-edited segment) is
  counted by word stems (the first four letters of each target word of three letters or more, names skipped)
- **AND** the rendering is the stem's base form

#### Scenario: What is never learned

- **WHEN** a term or a word is held by the glossary, or the evidence is split (a title before changing surnames, a word
  with two meanings, a term rendered several ways), or a word is on the target language's bundled stop-word list
- **THEN** a term the glossary holds is not learned, a word the glossary holds is not a rendering, nothing is
  established for split evidence, and a stop word is not a rendering

#### Scenario: A stop or a continued run keeps the counts honest

- **WHEN** a run is stopped, or continues over stored decisions
- **THEN** a stop leaves no count for an undecided segment, and a run that continues replays the stored decisions first

#### Scenario: A rendering once established is kept while it stays supported

- **WHEN** a rendering is established
- **THEN** it is kept while its score stays at least 0.4 and at least three quarters of the best unrelated rival's, so the
  first decision wins

#### Scenario: A one-word character or place name is learned over capitalised target words

- **WHEN** a glossary name of type character or place has one word, no target and no lock
- **THEN** it is learned the same way over the capitalised target words, the spellings sharing a stem counting as one
  (the most used kept), and the stem needing at least three quarters of its segments to name the term
- **AND** the learned spelling is stored as the entry's suggested target, never replacing a target that is already there

#### Scenario: A rendering is learned with no `terms` reply

- **WHEN** a model that never returns `terms` renders `master` as `господар` in three stored chapters, and would write
  `учитель` in the fourth unless shown the block
- **THEN** the fourth chapter's prompt lists `master → господар`, the lexicon holds the learned rendering with its support,
  and `distinctRenderingsPerTerm` is `1.0`

#### Scenario: Split evidence establishes nothing

- **WHEN** a title is rendered `містер` half the time and `пан` the other half, or a word is rendered `посох` in one meaning
  and `персонал` in another
- **THEN** no learned rendering is stored and no line is shown for it

#### Scenario: A stop leaves no phantom count

- **WHEN** a run is cancelled after the fourth segment naming a term was decided inside a chunk that was not committed, and
  a new run is started
- **THEN** the learned support is three until the new run starts, and four after it replays the stored decisions

### Requirement: Apply glossary edits made during a pause from the next chunk

WHEN the person edits the glossary while a run is paused, the application SHALL apply the edit from the first chunk that
starts after the run resumes and SHALL NOT change segments already decided, except through backward revision on the Max
dial or the export's final consistency pass; the glossary SHALL NOT survive closing the application.

**Source**: FR-GLOSS-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), ADR-0034, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`. In plain words: a person who spots a wrong spelling mid-run can fix it and resume, and the rest of the book follows; earlier segments are corrected by the consistency pass or review, not silently. A chunk reads the glossary once, when it starts, so the rest of a chunk paused in the middle keeps the glossary it began with. Nothing is saved to disk yet.

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

#### Scenario: The words of a repeated place are not recurring terms

- **WHEN** a book holds `the Great Hall` 4 times, `Old Bailey` 3 times and `the great hall` and `the old courts` in lower case
- **THEN** the key-term scan proposes neither `great`, `hall` nor `old`, while `Master` written 3 times with a capital beside no other capital and `master` 3 times in lower case is still proposed
