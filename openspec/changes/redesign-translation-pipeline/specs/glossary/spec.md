## ADDED Requirements

### Requirement: Group the inflected forms of a name into one lemma entry

WHEN the scan meets several written forms that the source language's name-ending table says can be forms of one name, the application SHALL hold them as one entry whose term is the dictionary (nominative) form and whose forms list the other attested forms, accepting the group when its nominative form is written in the book or at least two different forms point to the same one.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a Russian book writes `Велемир`, `Велемиром` and `Велемиру` for one man; without grouping each spelling became its own "name" and one run reported 381 false name swaps. The table of name endings is a mechanism any language may have; a language whose file sets none keeps one entry per written form.

#### Scenario: A nominative and an oblique form are one entry

- **WHEN** the source language is `ru` and the book writes `Велемир` 3 times and `Велемиром` 2 times mid-sentence
- **THEN** the glossary holds one entry `Велемир` with the form `Велемиром`, counted 5 times, and no entry `Велемиром`

#### Scenario: Two oblique forms that agree give the nominative

- **WHEN** the book writes `Градимиру` 2 times and `Градимиром` 2 times and never `Градимир`
- **THEN** the entry `Градимир` is held with those two forms and is marked as inferred

#### Scenario: One oblique form alone is not enough

- **WHEN** the book writes only `Градимиром`, 3 times
- **THEN** no lemma is inferred, and the form stays a candidate under its written spelling

#### Scenario: A language with no name-ending table keeps written forms

- **WHEN** the source language is `en` and the book writes `Wren` and `Wrens`
- **THEN** each stays its own entry

### Requirement: Resolve a form that fits two names by the clause around each mention

WHEN one written form can belong to two different names, the application SHALL decide each mention by the clause around it — a gendered verb ending after it, a preposition before it or a gendered form of address — and SHALL treat a mention with no such evidence as unsure: matched to both entries and giving no gender vote.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: `Велемира` is the genitive of the man `Велемир` and also the nominative of a woman `Велемира`; the clause usually settles which. A mention the clause cannot settle is not guessed: it counts for both names and votes for no gender, so one unclear sentence never decides who a character is. The Names & style table marks such an entry Unsure.

#### Scenario: A verb ending decides

- **WHEN** the source language is `ru`, the glossary holds `Велемир` (male) and `Велемира` (female), and the book writes `Велемира сказала тихо.`
- **THEN** the mention belongs to `Велемира` and counts as evidence that she is female

#### Scenario: A preposition decides

- **WHEN** the book writes `Мы пришли до Велемира.`
- **THEN** the mention belongs to `Велемир` as a masculine genitive

#### Scenario: A mention with no evidence is unsure

- **WHEN** the book writes `Это был портрет Велемира.`
- **THEN** the mention is matched to both entries and gives neither of them a gender vote
- **AND** each entry shows the Unsure mark

#### Scenario: A competition that changes nothing collapses

- **WHEN** the book writes only `Градовского`, 4 times, which could come from `Градовский` or `Градовской`, and both give the target `Градовський`
- **THEN** one entry is held, marked as inferred, under the more frequent class

### Requirement: Record where each glossary value came from

The application SHALL record the origin of an entry's gender, kind and target — person, book, rule or likely — SHALL let the person edit any value at any time, SHALL let an automatic value replace a stored one only when its origin is the same or higher in the order book, rule, likely and the stored one is not the person's, and SHALL let only the person's and the book's genders be enforced by checks.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: the old build used one word, "suggested", for a gender the code proved, a first-name guess and a model's guess, so a model's mistake was enforced and spread over dozens of paragraphs. Now a fact of the book is never confused with a hint: a rule or a likely value is shown to the person and may help, but no check holds the text to it.

#### Scenario: A higher origin replaces a lower one

- **WHEN** `Wren` is female by rule and later the book's pronouns decide female over the strong bar
- **THEN** `Wren` is female with the origin book

#### Scenario: A lower origin never replaces a higher one

- **WHEN** `Mara Quill` is female as the person set it and the model's answer is male
- **THEN** `Mara Quill` stays female with the origin person

#### Scenario: The person changes their own value

- **WHEN** `Mara Quill` is female as the person set it and the person changes her to male
- **THEN** `Mara Quill` is male with the origin person

#### Scenario: A value of the same origin is refreshed

- **WHEN** `Hale` → `Хейл` is a likely suggestion and the next review suggests `Гейл`
- **THEN** `Hale` reads `Гейл`, likely

#### Scenario: Only the person's and the book's genders are enforced

- **WHEN** `Ivar` is male by rule and `Mara Quill` is female from the book, and a translated sentence gives each of them a verb of the other gender
- **THEN** the gender check raises a finding for `Mara Quill` and none for `Ivar`

### Requirement: Give each target a state of suggested, pinned or locked

The application SHALL give every glossary target one of three states: suggested (a hint nobody accepted), pinned (accepted: the name may take any of its forms, its spelling checked through the forms the target's name table generates) or locked (the exact text, hidden behind a placeholder and restored). A target the person types or imports SHALL be pinned unless the person chooses locked.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: a name that inflects cannot be hidden behind a placeholder, or its endings would be wrong; so accepted names are pinned, which fixes the dictionary form and lets the draft decline it, and only an exact text such as an identifier is locked. Enforcement comes from the state; the origin only says who produced the spelling.

#### Scenario: A suggested target is a hint

- **WHEN** `Hale` → `Гейл` is suggested
- **THEN** the draft is told it as a hint and nothing checks the draft against it

#### Scenario: A pinned target may be declined

- **WHEN** `Зотов` → `Зотов` is pinned and a draft writes `Зотова`
- **THEN** `Зотова` is accepted as a form of the entry
- **AND** a draft that writes `Зотев` is refused as a different spelling

#### Scenario: A locked target is restored exactly

- **WHEN** `ZX-81` → `ZX-81` is locked
- **THEN** the source is sent with a placeholder for it and the exact text is put back

#### Scenario: The person's typed target is pinned

- **WHEN** the person types `Гейл` for `Hale`
- **THEN** the entry is pinned with the origin person, and locked only if the person sets the State to locked

### Requirement: Ask the model for the most probable gender and kind of each bare name

WHEN the names pass or a model review finds characters with no gender from the person or the book, or entries of type other, the application SHALL ask the model, in batches of up to 40 bare names with no book text, for the most probable gender and kind of each, and SHALL store every answer that repeats its name as a likely value with its confidence.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: a model knows that `Ivar` is almost always a man before it has read a page. Asking for the bare name costs about one call per 40 names, and the answer is always a value, never "unknown", because a hint beats a blank; it is marked likely so nobody takes it for a fact, and the book or the person overrides it.

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

- **WHEN** the person set `Wren` female, the book decided `Mara Quill` female, a rule gave `Ivar` male, and the model answers male for all three
- **THEN** `Wren`, `Mara Quill` and `Ivar` are unchanged

### Requirement: Ask at Start for the genders nobody has settled

WHEN Start translation is pressed and a character the book names at least 5 times has a gender that is only likely or by rule, the application SHALL ask once for those genders, the least confident first and each with its likely value already chosen, and SHALL make a value the person confirms or changes the person's.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: the model's guess is cheap but a wrong one spreads, so before the long run the person sees the characters whose gender nobody has checked and can fix the doubtful ones first. A row left as offered stays a hint.

#### Scenario: The least confident comes first

- **WHEN** `Mira Holt` (30 mentions, female, likely, 0.9), `Ivar` (12 mentions, male, likely, 0.55) and `Tobin` (3 mentions, likely) are open, and `Wren` (40 mentions) is female from the book
- **THEN** the question lists `Ivar` then `Mira Holt`, each with its value chosen, and neither `Tobin` nor `Wren`

#### Scenario: A rule and a model that disagree are both shown

- **WHEN** `Ivar` is male by rule and the model said female
- **THEN** the row reads `rule: male · model: female` with the rule's value chosen

#### Scenario: Confirming makes the person's value

- **WHEN** the person changes `Ivar` to female and confirms `Mira Holt` as offered
- **THEN** both are then the person's, and a row the person left alone stays likely

#### Scenario: Nothing to ask

- **WHEN** every character the book names 5 times or more has a gender from the person or the book
- **THEN** no question is shown

### Requirement: Check a suggested target against the target language's rules

WHEN a target is suggested or Accept all is pressed, the application SHALL reject the target, with a reason, if it holds a letter the target language's file forbids, has no capital letter where the term has one, has a different number of words than the term (a hyphenated word counting as its parts on both sides), or is spelled with the other alternative of a convention the Brief stores.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, `design.md#d4--name-forms-stress-marks-and-target-validation`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: a suggestion that a small model writes with a Russian letter in a Ukrainian name, a lost word or the wrong hard g reads right and matches nothing, so the code refuses it before it can be accepted and says why. A translated common word may stand in lower case (`місто` for `City`) and the later part of a hyphenated name may too (`Бейкер-стріт`), so only a target with no capital at all for a capitalised term is refused for its capitals. Every rule reads a key the target language's file may set; a language that sets none is not judged by it.

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

- **WHEN** the Brief stores the hard g as `г` and the suggestion for `Garcia` is `Ґарсія`
- **THEN** it is rejected with the reason "hard g is spelled ґ, the book uses г"

#### Scenario: Alternatives of a convention are one name when forms are compared

- **WHEN** `Гарсія` is pinned and a draft writes `Ґарсії`
- **THEN** the draft's word is read as a form of the same name, not as a different spelling

### Requirement: Remove stress marks from every target the application takes in

WHERE the target language's file asks for it, the application SHALL remove stress marks from every target it takes in — a model's suggestion, an imported row, a typed edit and a learned rendering — by decomposing the text, dropping the acute and grave combining marks and recomposing it, so that the marks of `й` and `ї` survive.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: a model writes `Зо́тов` with an invisible accent that matches no text in the book, and the name then fails every comparison. Removing the accents on the way in keeps every stored spelling plain; a language such as Bulgarian, which writes a grave on `ѝ`, does not ask for it.

#### Scenario: An acute accent is removed

- **WHEN** a suggestion arrives as `Зо́тов`
- **THEN** it is stored as `Зотов`

#### Scenario: The breve of й and the diaeresis of ї stay

- **WHEN** a typed target is `Йо́сип` or `Ї́жак`
- **THEN** it is stored as `Йосип` or `Їжак`, with the `й` and the `ї` unchanged

#### Scenario: A grave accent is removed

- **WHEN** an imported target holds `ѐ`
- **THEN** it is stored as `е`

#### Scenario: A language that does not ask keeps its marks

- **WHEN** the target language's file does not ask for stress marks to be removed and a typed target holds `ѝ`
- **THEN** the target is stored as typed

### Requirement: Generate the forms of a target from the target language's name table

WHERE the target language's file sets a name-ending table, the application SHALL generate from a pinned target and the entry's gender its case forms and its vocative, including the vowel changes the file lists, and SHALL accept any generated form as the entry's rendering; where the file sets none, the only form is the target itself.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: a pinned name has to be allowed to decline, but only in the ways the language really allows; a table of endings and vowel changes in the language's own file lets the code tell `Зотова` (a form of `Зотов`) from `Зотев` (another spelling). The table is a mechanism any language may have.

#### Scenario: Forms of a masculine name

- **WHEN** the target language is Ukrainian and `Зотов` (male) is pinned
- **THEN** its forms include `Зотова`, `Зотову` and `Зотовим`, and its vocative is `Зотове`

#### Scenario: A vowel change is listed

- **WHEN** `Лев` (male) is pinned and the file lists the change from `е` to a soft sign before the ending
- **THEN** `Льва` is one of its forms

#### Scenario: No table gives only the target

- **WHEN** the target language's file sets no name-ending table
- **THEN** the only form of a pinned name is the target itself

### Requirement: Flag a target that is not a dictionary form

WHERE the target language's file sets keys for oblique endings or counts words, the application SHALL flag a target that ends like an oblique or vocative form or has a different number of words than its term (a hyphenated word counting as its parts on both sides), SHALL compute the flag when the row is shown without storing it, and SHALL keep a flagged row out of Accept all.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: a person can type `Алєксєю` (a vocative) or paste a form into the Translation cell; the table says "not the dictionary form" instead of silently pinning it. The flag is computed, so changing the language file or the target changes it at once, and a language whose file sets no such key is never flagged.

#### Scenario: An oblique ending is flagged

- **WHEN** the target for `Valdor` is `Валдором` and the file lists `-ом` as non-nominative
- **THEN** the row shows "not the dictionary form" and Accept all leaves it out

#### Scenario: A vocative is flagged

- **WHEN** the target for `Alexei` is `Алєксєю` and the file lists `-ю` as a vocative ending
- **THEN** the row shows "not the dictionary form"

#### Scenario: A different word count is flagged, a compound is not

- **WHEN** the target for `Varno City` is `Варно`, the target for `Smith-Jones` is `Сміт-Джонс`, and the target for `Baker Street` is `Бейкер-стріт`
- **THEN** the first row is flagged "different number of words", and the second and third are not

#### Scenario: A language with no such key is never flagged

- **WHEN** the target language's file sets no oblique endings
- **THEN** no row is flagged for its ending

### Requirement: Read past-tense verbs after a name as gender evidence

WHERE the source language's file lists past-tense endings that show gender, the application SHALL count the first past-tense verb after a mention of a name, in the same clause, outside speech and with no other name between, as gender evidence for that name, and SHALL add it to the pronoun evidence under the same bars.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: in Russian and Ukrainian the verb itself says who acted (`сказала`, `сказал`), so a book that rarely writes `она` still shows a woman's gender. The endings come from the source language's file, as the pronouns do, so any language with gendered past forms gains by data alone.

#### Scenario: A feminine verb counts for the name before it

- **WHEN** the source language is `ru` and the book writes `Ольга пришла`, `Ольга сказала` and `Ольга ушла`
- **THEN** each verb is one vote for female, and with the pronouns they decide the gender by the usual bars

#### Scenario: Another name between stops the evidence

- **WHEN** the book writes `Ольга и Велемир ушёл`
- **THEN** `ушёл` is a vote for `Велемир` only

#### Scenario: Speech and plural forms do not count

- **WHEN** the book writes `«Ольга пришла», сказал он` and `Ольга и Велемир пришли`
- **THEN** neither verb is evidence for `Ольга`

#### Scenario: A language with no such data gives no verb evidence

- **WHEN** the source language is `en`
- **THEN** only the pronouns are counted

### Requirement: Keep a meaning-bearing name's meaning as a note

WHEN a name is made of ordinary words but is used as a proper name, the application SHALL sound it out by default under the Transliterate policy and keep its meaning as a note on the entry, and SHALL translate it only when the person answered so; a nickname that is a plain word is translated.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: a surname such as `Brightwater` is a name first; a translator sounds it out and remembers what it means. The meaning is kept so the person can decide to translate it, and the person is asked only when the book plays on the meaning (see the Book Brief).

#### Scenario: A surname is sounded out and its meaning kept

- **WHEN** the policy is Transliterate and `Brightwater` is a surname
- **THEN** its suggested target is the sound `Брайтвотер`, and the entry holds the note `bright water`

#### Scenario: The person chose to translate it

- **WHEN** the person answered "translate" for `Brightwater`
- **THEN** the suggestion call asks for a translation by meaning

#### Scenario: A plain-word nickname is translated

- **WHEN** the book calls a character `the Fox` as a nickname
- **THEN** the suggestion is a translation, and no meaning note is made

### Requirement: Group recurring terms by their stem

The application SHALL count and propose recurring terms by form group — the forms of a word that share a stem of at least 4 letters once the source language's noun endings are removed — and SHALL measure each scan's own use floor on the group's total instead of on one form.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a word that a Russian book writes `фонарь`, `фонаря` and `фонарём` is one recurring term; counting each form alone lost it. The floors stay those of each scan (the stored lexicon's scan keeps a word written in lower case at least five times, as "Keep a stored lexicon of recurring terms and show it on Names & style" says); only what is counted changes. The language's own noun-ending table is used, so any language that has one gains by data alone; a language without one counts exact forms.

#### Scenario: Three forms make one group

- **WHEN** the source language is `ru` and the book writes `фонарь` 3 times, `фонаря` 4 times and `фонарём` 2 times
- **THEN** one group of 9 uses is kept, its representative form is `фонарь`, and a rendering learned for it applies to all three forms

#### Scenario: A group under the floor is not kept

- **WHEN** the lexicon scan's floor is five lower-case uses and the book writes `фонарь` 2 times and `фонаря` 2 times
- **THEN** the group of 4 uses is not kept
- **AND** with `фонарь` 3 times and `фонаря` 2 times the group of 5 is kept, though no form alone reaches five

#### Scenario: A stem under 4 letters does not group

- **WHEN** the book writes `дом` and `дома`, whose shared stem `дом` has 3 letters
- **THEN** they are two groups

### Requirement: Register a gender-changed deferral when an enforced gender arrives

WHEN an entry's gender from the person or the book appears or changes after segments naming it were decided, the application SHALL register a gender-changed deferral for each of those segments, except for an entry that had no gender at all, whose waiting deferrals stay, and SHALL register none for a likely or rule value.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a likely gender was never checked, so when the person or the book later confirms it, even to the same value, the text decided meanwhile may disagree and must be looked at. This capability records the deferral; resolving it — by code when the gender check finds the segment clean, else by a proofread window — is the translation-pipeline capability's "Settle every deferral whose fact has arrived in the Proofread stage".

#### Scenario: A confirmed likely value defers the segments

- **WHEN** `Ivar` was male, likely, 4 segments naming him were decided, and the person sets male
- **THEN** 4 gender-changed deferrals are registered, one on each of those segments

#### Scenario: A likely gender confirmed by the book defers the segments too

- **WHEN** `Corin Ashby`'s gender was likely male, three decided segments name him, and the book's pronouns then prove male
- **THEN** one gender-changed deferral is registered on each of the three segments, although the value did not change

#### Scenario: A changed enforced value defers the segments

- **WHEN** `Mara Quill` was female from the book, 2 segments naming her were decided, and the person sets male
- **THEN** 2 gender-changed deferrals are registered

#### Scenario: An entry with no gender keeps its waiting deferrals

- **WHEN** `Tobin` had no gender, 3 decided segments naming him carry the deferrals that wait on his gender, and the book later decides male
- **THEN** the 3 waiting deferrals stay, now with their fact arrived, so each of the 3 segments is looked at, and no gender-changed deferral is added beside them

#### Scenario: A gender the person sets after the run reaches every segment that names the entry

- **WHEN** `Sam` had no gender during the run, two decided segments name `Sam` with waiting deferrals, and after the run the person sets female
- **THEN** the two waiting deferrals have their fact and are settled by the Proofread stage the next Continue runs, and no second deferral is recorded on either segment

#### Scenario: A likely or rule value registers nothing

- **WHEN** the model's likely value for `Ivar` arrives after segments naming him were decided
- **THEN** no deferral is registered

### Requirement: Log the names pass as counts and never as names

WHEN a names pass ends, the application SHALL write one INFO line to the run log with the numbers of entries, forms, and genders by origin (book, rule, likely), the characters without a gender and the rejected suggestions, and SHALL put no name, rendering or book text in it.

**Source**: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: the always-on run log must say what the pass did so a bad result can be traced, but it is a file the person may share, so it holds counts and choices only.

#### Scenario: The counts are written

- **WHEN** a pass ends with 37 entries, 58 forms, 12 genders from the book, 9 by rule, 6 likely, 10 entries with no gender (places and terms) and 3 rejected suggestions
- **THEN** the run log holds `names pass entries=37 forms=58 book=12 rule=9 likely=6 noGender=10 rejected=3`

#### Scenario: No name appears

- **WHEN** the pass handled `Ivar`, `Велемир` and `Mara Quill`
- **THEN** none of those names, no target and no sentence of the book is in the run log

### Requirement: Estimate the names pass from the seconds measured on this model

WHEN the names pass is about to start, the application SHALL estimate its time as the sum, over the kinds of call the dial's plan makes, of the number of calls times the seconds per call stored for the chosen model, and SHALL label it "measured on this model"; without stored seconds it SHALL scale the built-in constants by the model's measured speed, or else show them, labelled "estimate".

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a fixed guess of "about 1 min" was wrong by a factor of ten on a slow model; the app now stores the seconds each kind of call took on that model and multiplies. At the end of every pass and run the measured seconds per kind are stored again. While the pass runs, the figures line uses the steps left times the measured seconds per kind.

#### Scenario: Stored seconds give a measured estimate

- **WHEN** the plan is 5 reading calls, 1 name-knowledge call and 1 suggestion call, and the model has stored 15, 15 and 30 seconds for those kinds
- **THEN** the estimate is 120 seconds and the card reads "About 2 min, measured on this model."

#### Scenario: A measured speed scales the constants

- **WHEN** the model has no stored seconds, its measured speed is 6.88 seconds per segment, and the built-in reference speed is 3.44 seconds per segment
- **THEN** the built-in per-call figures are doubled and the card says "estimate"

#### Scenario: Nothing measured shows the built-in figures

- **WHEN** nothing is measured for the model and the plan is 5 Balanced calls
- **THEN** the card reads "About 1–2 min, estimate."

#### Scenario: The pass stores what it measured

- **WHEN** a pass ends
- **THEN** the seconds per call kind it measured are stored on the chosen model, replacing the earlier figures for those kinds

### Requirement: Keep one established rendering for each recurring term

The application SHALL establish one rendering for each recurring term by this order — the person's, else the one learned from the decided segments, else the model's suggestion, else none — and SHALL show the established rendering of each term a call's window names in every later draft and retry.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: once a book has called a warden `наглядач`, chapter nine must not call him `сторож`; the order says whose word wins. This replaces the old report-and-verify scheme: the model no longer reports its renderings, so the person's word, the decided text and the model's first suggestion are what remain.

#### Scenario: The person's rendering wins

- **WHEN** `master` has the person's `пан`, a learned `господар` and a suggestion `володар`
- **THEN** the established rendering is `пан`

#### Scenario: A learned rendering beats a suggestion

- **WHEN** `master` has a learned `господар` and a suggestion `володар`
- **THEN** the established rendering is `господар`

#### Scenario: Nothing to show

- **WHEN** `master` has no rendering of any kind
- **THEN** no line is shown for it

#### Scenario: A later retry shows the same line

- **WHEN** a segment naming `master` is drafted and later retried
- **THEN** both calls show `master → господар`, and the glossary entry wins if the glossary also holds `master`

### Requirement: List the steps of the names pass in its progress dialog

The progress dialog of the names pass SHALL list ten steps in order — Find candidates in the book, Decide the names, Ask what the model knows about the names, Find repeated words, Clean up repeated words, Read repeated words in context, Suggest translations for names, Suggest translations for repeated words, Check and accept the suggestions, Write the results — each with the number of requests it sent, and a figures line such as "53 % · 26 of about 49 steps · Left about 2 min".

**Source**: `docs/specification/mockups/README.md#dialog--prepare-names-automatically--results-g4-long--n4-n5`, N4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `design.md#d3--the-book-map-lemma-entries-origin-target-state`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: the person sees which part of the pass is running and how much is left; the new steps are the model's best guess about each name and the check that decides which suggestions are accepted. Grouping the forms of a name is part of finding candidates and sends no request. The dialog itself follows the progress-dialog pattern of the `app-shell` capability.

#### Scenario: The steps are listed with counts

- **WHEN** the pass is at its sixth step with 25 requests answered
- **THEN** the list shows ten rows, the sixth reads "Read repeated words in context" with "25 requests so far", and the steps after it say "Not started"

#### Scenario: The count reads by the step's state

- **WHEN** a step is running with 25 requests, has ended with 6, ended with none, or has not begun
- **THEN** its row reads "25 requests so far", "6 requests", "no request" or "Not started"
- **AND** no count reads "of N", because no step knows its total up front

## MODIFIED Requirements

### Requirement: Propose names by a deterministic scan when the glossary is empty

WHEN the Names & style screen opens for a book whose glossary is empty, or a run prepares such a book, the
application SHALL propose, without any network call, every capitalised word and every run of two or three capitalised
words that occurs at least 3 times not at the start of a sentence, counting the forms of one name together, ordered by
how often it occurs and then by where it first occurs, each with type other, gender unknown and no target, by the rules
of the scenarios below.

**Source**: FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#name-term-pre-scan` (offline fallback), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a name that recurs mid-sentence is almost certainly a proper noun, so a frequency count finds most of a book's names instantly and offline; words at the start of a sentence are ignored because every sentence starts with a capital, and the full stop after an abbreviated title does not end a sentence, so `Hale` in `Mr. Hale` still counts. A proposal has no target yet, so it is neither pinned nor locked, and the forms of one name are one proposal, not one per spelling. A term the person removed is never proposed again (see "Keep the person's entries and removals when names are proposed"). A real book of the owner's corpus showed what a bare count gets wrong: `—Well,` and `"Don’t` open speech without a full stop, common nouns are given a capital, a hyphenated place name was cut in two, and a first name was proposed beside the full name. Every real name measured there is written in lower case less than 20% of the time, and every one of those common words at least 25% of the time, which is a test that works in any language with capitals; the stop-word list catches the function words and interjections a book rarely writes in lower case.

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

### Requirement: Suggest a character's gender from a short list of first names

WHEN a preparation or a model review finds an unlocked glossary entry with no gender, never tried and already typed as a
character, of at most three words, whose first word is a common given name that the source language's bundled list holds
for one gender, the application SHALL set that gender with the origin rule (shown with the chip "rule", a hint that no
check enforces), and SHALL NEVER change the entry's type.

**Source**: FR-GLOSS-02, FR-ALGO-D1; tasks 15e.14; the owner's decision that a rule's guess is a hint, not a fact, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: 176 of 277 glossary entries of a real run ended with gender unknown, so the gender sheet, the agreement fix and the consistency pass had nothing to act on; a short list of unambiguous first names fills most of them without a model call, marked as a rule's guess so the person can see and change it, and no check treats it as a fact.

#### Scenario: A listed name gets a suggested gender

- **WHEN** a held character `Hermione` has gender unknown in an English book and a run is prepared
- **THEN** the entry has gender female with the origin rule

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
- **THEN** the entry is marked as tried, so a gender the person reset to unknown stays unknown

#### Scenario: A review keeps the mark and the person confirms by choosing

- **WHEN** a model review re-applies an unchanged gender, or the person chooses the same value
- **THEN** a review keeps the origin rule, and the person's choice makes it the person's

#### Scenario: Seeding changes an entry atomically

- **WHEN** seeding or the term learning changes an entry while the person edits it
- **THEN** the change is made in one atomic step against the entry's current state, so an edit made meanwhile is never overwritten

#### Scenario: Unlisted names, display and confirmation

- **WHEN** a name is used by either gender or is also a common word, or the language has no list
- **THEN** it is not listed and nothing is suggested
- **AND** the table shows the gender as `male · rule` and the column's hover explanation says it is a rule's guess; a
  person's edit of the gender replaces it, and a gender already set is never overwritten

#### Scenario: The bundled list is per language

- **WHEN** a gender is looked up for a first word
- **THEN** the source language's bundled first-name list is used (a seed, not exhaustive; one kind of file that any
  language may have), and a language with no list gives no rule gender

#### Scenario: A model that disagrees does not replace the rule

- **WHEN** `Ivar` has the gender male by rule and the model's answer for the bare name is female
- **THEN** `Ivar` stays male by rule and the table shows `rule: male · model: female`
- **AND** neither value is enforced, and no entry is ever turned to unknown by the disagreement

### Requirement: Show the glossary as an editable table on Names & style

The Names & style screen SHALL show the glossary as a table with the columns Term, Kind, Forms, Translation (an empty one shows the placeholder "No target"), State (suggested, pinned or locked), Gender and a remove cross, with an origin chip (person, book, rule or likely) on Kind, Translation and Gender, under the purpose line "Names of people and places — written the same way every time. Set the gender so names take the right grammatical form."

**Source**: FR-GLOSS-02, FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N1, N3 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: each value says where it came from, so the person can tell a fact of the book from a guess; this is where the person settles spellings before translating; the table explains its purpose, a changed row is marked on its edge so the term is never cut, an empty table says what to do, and the two cards sit side by side only when the content is at least 1440 px wide.

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

#### Scenario: The Gender column

- **WHEN** the table shows a row's gender
- **THEN** it reads female, male or neuter for a character, followed by its origin (`male · book`, `female · rule`, `female · person` or `male · likely`), "not specified — no evidence in the book" for a character with no value yet, and "—" for any other kind; it never reads "unknown"
- **AND** a likely value below a confidence of 0.7 reads `likely, low confidence`, and a rule value the model disagrees with reads `rule: male · model: female`

#### Scenario: A changed row is marked

- **WHEN** the last automatic run changed the row `Earth`
- **THEN** the row shows a mark on its left edge, its tooltip reads "Changed by the last automatic run", and the Term cell reads `Earth` in full with no chip

#### Scenario: The glossary and the Recurring terms are two equal cards

- **WHEN** the content is 1500 px wide, and then 1200 px wide, in the regular and in the compact width class
- **THEN** the two cards sit side by side at 1500 px and are stacked at 1200 px, with the tables filling the available height
- **AND** a card too narrow for its toolbar folds Import and Export into a three-dot menu

#### Scenario: Start translation during a paused run shows it

- **WHEN** the translation of `Earth Gravity · EN → UK` is paused at 55 % and the person looks at the toolbar of Names & style
- **THEN** it offers Go to Translating, pressing it shows the paused translation offering Resume, and no new translation begins

#### Scenario: The screen with no book open

- **WHEN** no project is open
- **THEN** the Names & style step is locked in the navigation with the hint "Steps unlock when a project is open", so the screen cannot be reached and no glossary table, scan button or Start translation is shown

#### Scenario: The banner and the Start translation button

- **WHEN** a project is open
- **THEN** the screen shows the purpose line "Decide how names and repeated words are translated. Optional — skip it and BookLoom builds both while it translates." and the toolbar's Start translation button

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
- **THEN** its Forms cell reads `Зотова, Зотову` with the tooltip "joins 2 forms", and the row shows an Unsure mark whose tooltip reads "Unsure: some mentions fit two names"

#### Scenario: A meaning chip

- **WHEN** the entry `Brightwater` was sounded out and its meaning `bright water` was kept as a note
- **THEN** its row shows a meaning chip, and hovering it shows `bright water`

#### Scenario: A target's state and origin are shown

- **WHEN** `Hale` → `Гейл` was suggested by the model and then accepted
- **THEN** the row shows State pinned and the origin chip `likely` beside the Translation, because the chip says who produced the spelling and the state says it is accepted

### Requirement: Review the glossary with the model when the person asks

WHEN the person presses Review with model, the application SHALL decide by code each open entry whose pronouns and
past-tense verbs alone decide its gender, send every other entry not pinned or locked for a verdict, then ask the model
for the most probable gender and kind of each entry still lacking a gender from the person or the book, storing them as
likely. It SHALL change the glossary only after every call has answered; IF any call fails or Stop is pressed, THEN it
SHALL change nothing.

**Source**: FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-review`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`), `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: a frequency count cannot tell a sentence-opening interjection from a name in every case, and it never knows a type or a gender, so the model is asked to clean and classify the list. It sees how often the book uses each word in lower case too, which is the clearest sign of a common word. A set type means someone — the person or an earlier model scan — already judged the entry a name, so only an untouched row is removed; and a row the person locked or gave a target is theirs and is never sent. The model is never allowed to answer "unknown": it gives its most probable value, and the table marks it likely so nobody mistakes it for a fact of the book.

#### Scenario: The review removes a common word and classifies a name

- **WHEN** the glossary holds `Well` and `Hale` (other, unknown), `Moreau` (character, unknown), `Milton` → `Мілтон`
  locked and `Baker Street` → `Бейкер-стріт`, and the model answers `Well` not a name, `Hale` a name (person, male),
  `Moreau` a name (place, female), and `Milton` and `Baker Street` not a name
- **THEN** `Well` is removed and remembered as removed, `Hale` reads character, male (likely), `Moreau` reads
  character, female (likely) — no window shows either by a pronoun — and `Milton` and `Baker Street` are unchanged
- **AND** only `Well`, `Hale` and `Moreau` were sent, and the line reads `Model review done: 1 row removed, 2 rows
  updated, 0 targets suggested.` when the suggestion call suggests nothing

#### Scenario: A gender stands only on a window that shows it

- **WHEN** the book reads `Kestrel nodded. "He never pays," said Pell.` and the model answers `Kestrel` a person, male,
  citing that window
- **THEN** `Kestrel` reads male (likely): the `he` is inside the speech, so no window shows the gender
- **AND** had the model answered female citing a window `Kestrel stood. She left.`, `Kestrel` would read female, from the book

#### Scenario: Five agreeing pronouns need no model

- **WHEN** an open entry `Wren` is followed by `she` in five narration sentences of the book and by no `he`
- **THEN** `Wren` is not sent and reads character, female, from the book

#### Scenario: A term locked while the model thinks is left alone

- **WHEN** the person locks `Well` with the target `Ну` while the review's call is out, and the model judges `Well`
  not a name
- **THEN** `Well` → `Ну` stays, locked

#### Scenario: A failed or stopped review changes nothing

- **WHEN** the glossary holds 41 open entries and the second call fails as unreachable, or the person
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
- **THEN** it is set as the book's gender when most of the distinct windows it cites (strictly more than half, and at least two
  unless fewer were shown) hold a pronoun of that gender the code gives the name and the book-wide pronoun tally does
  not favour the other gender (three or more opposing pronouns making 60 % or more of the counted ones)
- **AND** otherwise it is set as likely — against a favoured tally the value is the tally's gender, by rule

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

- **WHEN** the book gives the character `Ivar` no pronoun, no verb ending and no first-name match, and the model's answer for the bare name is male with confidence 0.8
- **THEN** `Ivar` reads male, likely, and the table does not read "unknown"
- **AND** an answer with confidence 0.55 reads `likely, low confidence`

#### Scenario: A value of higher origin is never replaced

- **WHEN** `Mara Quill` is female from the book, `Ivar` is male by rule, `Wren` is female as the person set it, and the model answers male for `Mara Quill` and female for `Ivar` and male for `Wren`
- **THEN** `Mara Quill` and `Wren` are unchanged and `Ivar` stays male by rule with the disagreement shown

### Requirement: Decide a read name from its readings by code

The review and the names pass SHALL share one path: of the open entries a strong pronoun case does not decide, those
the quality dial's names plan reads (Fast: a likely character written five times or more; Balanced and Max: any entry
written three times or more; at most 80) SHALL be read in context, one term per call (`term-reading`), and the code —
not the model — SHALL decide each from its readings and from the pronoun and past-tense verb evidence, by the rules of the scenarios below.

**Source**: 15i.N5 (`openspec/changes/archive/2026-10-19-complete-translation-workflow/tasks.md`), FR-GLOSS-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#term-summary`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: the model reads, the code decides. A small model's pronoun answers were wrong one time in ten or more, so for now they only settle what the book's own pronouns leave open. Where the code decides from the book, the value is the book's (a fact); where only the model's reading or a weak tally stands, it is a likely value or a rule's guess, and no check enforces it.

#### Scenario: Six windows read a character

- **WHEN** at Balanced the untyped `Brann` is written six times and its one reading call reads every window as a
  person's name, quoting `He nodded` from each, and the code reads no pronoun after `Brann`
- **THEN** `Brann` reads character, male, likely (the readings' votes alone are the model's judgement), and no batched review call is sent for it

#### Scenario: A clear tally outweighs the readings

- **WHEN** the readings quote `he` for `Brann` in six windows and the code counts `she` four times after the name
- **THEN** `Brann` reads female by rule (no vote agrees) and no tiebreak call is made

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

- **WHEN** the book gives a person no evidence — a pronoun tally of 0/0 (forms of address included), no verb ending and no verified vote
- **THEN** the code decides nothing and the entry goes to the model for its most probable value, which is stored as likely
- **AND** until the model has answered (or if it never does) the Names & style gender cell reads "not specified — no evidence in the book" (Ukrainian: "не вказано — у книзі немає свідчень") instead of "unknown", the column's tooltip explaining it; any later gender and the person's own edit clear that wording

#### Scenario: The model's votes only break ties

- **WHEN** the verified votes (a window's pronoun the reading quoted from that window's narration) of every spelling of
  the person are pooled and the model's votes were found wrong more than 5 % of the time by the coreference probe —
  every model today (26b 10 %, e4b 20 % on 2026-10-13)
- **THEN** the votes only break ties: a clear code tally (three or more pronouns making 60 % or more) decides, written
  with the origin book when the verified votes agree with it and none opposes it, or when it is strong (five or more
  pronouns making 90 % or more) and no verified vote opposes it (15j.J2), and with the origin rule otherwise
- **AND** with no clear tally two or more votes making 75 % (three at 80 % under Auto), or two or more pieces of
  evidence (votes and pronouns together) all of one gender, give a likely gender, never a gender from the book
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

#### Scenario: A past-tense verb counts with the pronouns

- **WHEN** the source language is `ru` and an open entry `Ольга` is followed by `она` twice and by past forms ending in `-ла` three times in narration
- **THEN** the pronoun and verb evidence are summed to 5 votes of 5, over the strong bar, and `Ольга` reads character, female, from the book

### Requirement: Suggest target renderings with the model

WHEN the model review or the model scan has its verdicts, the application SHALL, in the same action, send the open
entries (not pinned or locked) to a separate suggestion call, at most 20 per call, asking under a strict schema and the
Brief's name policy for the dictionary form of each target, keep only suggestions that pass the checks below and write
them as suggested targets of origin likely after every call has answered; IF a call fails or Stop is pressed, THEN it
SHALL write nothing.

**Source**: FR-GLOSS-01, FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`), `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: a glossary of names with no targets leaves every draft to spell each name afresh, so the model proposes one spelling per name, following the policy the person chose. It is a call of its own because a small model does one task per call far better than two. A suggestion is only a hint until Accept all or the person accepts it — the draft may inflect it — and the model never undoes the person's choice. Names are sounded out by default for every language pair; the model adapts a name to the target language's own form only when the person chose that policy, and no list of equivalent names is bundled. On gemma4:e4b every place and thing came back "neuter", masculine Ukrainian nouns included, and a person's gender judged from one sentence took a speaker's `he` (15h.A2), so the reply's gender is not written; and the same model wrote a Latin `e` inside `Венс`, which reads right and matches nothing.

#### Scenario: Transliterated names get Cyrillic targets

- **WHEN** the policy is Transliterate, the target is Ukrainian and the review keeps `Hale` (character, male) and
  `Moreau` (character, female), and the model suggests `Гейл` and `Моро`
- **THEN** `Hale` reads `Гейл` and `Moreau` reads `Моро`, each suggested, with the origin likely and the label `likely`,
  and the line reads `Model review done: 1 row removed, 2 rows updated, 2 targets suggested.`

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

- **WHEN** the verdicts answered and the suggestion call fails with a timeout
- **THEN** the glossary holds the same entries as before the review, verdicts included

#### Scenario: The call carries the entry's evidence and a strict schema

- **WHEN** the entries are sent to a suggestion call
- **THEN** each carries its lemma (the nominative), its type, its gender when known, `speaking name` and `brand` when the term reading says so, what
  the glossary already renders of its person (`Arlo Venn → Арло Венн, so Venn → Венн`, for a spelling of a person whose
  full name has a rendering) and up to two sentences of the book that hold it (each cut at 120 characters)
- **AND** the reply is `{"suggestions":[{"term","target","gender"}]}`, at most 20 items, a target at most 80 characters,
  `""` meaning no suggestion

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
- **THEN** a suggestion is written into an entry that is neither pinned nor locked and whose target is empty or was suggested, as a suggested target of origin likely,
  and the reply's gender is not written (a person's gender is the review's)
- **AND** a target the person typed, imported, accepted or locked is not overwritten, the writes record no deferral, and
  while the calls run the screen shows `Suggesting renderings B/N` with the batch under way

#### Scenario: The lemma is asked for, not a form

- **WHEN** the entry `Велемир` joins the forms `Велемира` and `Велемиром`, two attested sentences use them, and the model answers `Велемиром`
- **THEN** the call carried the lemma `Велемир` and the sentences, the answer is refused for its non-nominative ending and asked once more

#### Scenario: A stress-marked reply is stored without the marks

- **WHEN** the model answers `Зо́тов` for `Zotov` and the target language's file asks for stress marks to be removed
- **THEN** the suggestion is stored as `Зотов`

#### Scenario: Translate lets the model adapt a name, Transliterate does not

- **WHEN** the source is Russian, the target Ukrainian, the entry is `Алексей` and the model answers `Олексій`
- **THEN** under Translate the answer is kept as a suggestion
- **AND** under Transliterate the same answer is refused as not opening as the name sounds, and the entry is asked once more

#### Scenario: A pair's invented example is sent when it has one

- **WHEN** the language pair's file sets an example for the chosen name policy, and then when it sets none
- **THEN** the call carries that example in the first case, and in the second case is zero-shot with the schema only

### Requirement: Lock a suggested rendering only where names do not inflect

WHEN a suggested target is accepted, the application SHALL make it locked only for an identifier, a kept original (a
name, or a brand, under Keep original), or a verified proper name that is not a common noun, with a checked rendering
and not marked speaking, in a target whose language file says `namesInflect=false`; every other accepted target SHALL be
pinned. Every path that accepts a suggestion SHALL apply the rule.

**Source**: FR-GLOSS-01, FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`), `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a name locked behind a placeholder cannot inflect, so it is locked only where the target language does not decline names; everywhere else an accepted name is pinned, which fixes its dictionary form and lets the draft use any form the target's name table generates.

#### Scenario: Which targets decline names

- **WHEN** the target is English, German, French, Spanish or Portuguese (the language file says `namesInflect=false`)
- **THEN** a verified proper name that is not a common noun, with a checked rendering and not marked speaking, is locked
- **AND** for Ukrainian, Russian, Polish, Czech, Croatian, Slovak, Slovenian and any language with no such key it is
  pinned

#### Scenario: The same name is locked after Review, Translate and the scan alike

- **WHEN** a suggestion is accepted by the tick, by Accept all or by the names pass
- **THEN** the lock rule gives the same state, locked or pinned, for the same name

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
- **THEN** the title and the organisation read their sources, locked, once accepted by any path,
  and `Amulet` is neither copied nor locked

#### Scenario: Names are locked only where they do not inflect

- **WHEN** `Venn` is a verified proper name, not speaking, rendered `Venn` and checked
- **THEN** it is locked when the target is English and is pinned when the target is Ukrainian

#### Scenario: An accepted name in a language that declines names is pinned

- **WHEN** the target is Ukrainian and `Hale` → `Гейл` is accepted
- **THEN** the entry is pinned, so `Гейла` and `Гейлу` in a draft are accepted as its forms and no placeholder hides `Hale`

### Requirement: Show a suggested target as a hint and let the person accept it

The Names & style table SHALL show a suggested target slanted with the label `likely` and a tick that accepts it; Enter
in the field with the suggestion unchanged SHALL accept it too, and typing another target or choosing a State SHALL make
the target the person's. An `Accept all` button beside Review with model SHALL check every suggested target against the
target language's rules, accept those that pass and list the rejected ones with their reasons.

**Source**: FR-GLOSS-01, FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#glossary-target-suggestions`, DD-46 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-46-glossary-llm-pre-scan`), `design.md#d3--the-book-map-lemma-entries-origin-target-state`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: a suggestion is only a hint until it is accepted, and the draft may inflect it; Accept all never lets a spelling through that the checks refuse, and it says which rows it left out and why.

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
- **THEN** `Hale` reads `Гейл` with the State pinned and no suggestion slant

#### Scenario: Accept all accepts the rows that pass and lists the rest

- **WHEN** the target is Ukrainian and the table holds the suggested targets `Hale` → `Гейл`, `Velk` → `Велк`, `ZX-81` → `ZX-81` and `Valdor` → `Валдором`
- **THEN** `Hale` and `Velk` become pinned, `ZX-81` becomes locked, and `Valdor` stays suggested
- **AND** the Results view lists `Valdor` with the reason "not the dictionary form"

#### Scenario: Undo all and revert per row

- **WHEN** the person presses Undo all in the Results view of that Accept all, or reverts only the row `Velk`
- **THEN** with Undo all `Hale`, `Velk` and `ZX-81` are suggested again, and with the revert only `Velk` is

#### Scenario: A flagged row is left out

- **WHEN** a suggested target carries the flag "not the dictionary form" or "different number of words"
- **THEN** Accept all does not accept it and lists it with that reason

### Requirement: Compare glossary terms by one key

The application SHALL compare glossary terms — for a duplicate in the repository, the Add term dialog and a CSV import,
for a held or removed term in every scan, and for an entry's id — by one key: the term in Unicode NFC, with leading and
trailing punctuation and a trailing possessive `’s` or `'s` removed, inner spaces collapsed and the case folded. A term
matches an entry when its key is that of the entry's lemma or of any attested form; the id stays the project id plus the
lemma's key.

**Source**: FR-GLOSS-02, FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a spelling the book uses for a known name (`Велемира` for `Велемир`) is that name, not a new one; before, each way in compared differently, so `Lovelace’s` could be held beside `Lovelace` and a removed name could come back in another spelling.

#### Scenario: Spellings of one term share a key

- **WHEN** the glossary holds `Hale` and `Hale's`, `Hale’s`, ` HALE ` or `"Hale,"` is added
- **THEN** the addition is refused as a duplicate, and a removed `Hale` is still removed when `Hale’s` is proposed

#### Scenario: A form matches the lemma's entry

- **WHEN** the glossary holds `Велемир` with the form `Велемира`, and a scan proposes `Велемира`, or a removed `Велемир` is proposed as `Велемиром`
- **THEN** the proposal is matched to the entry `Велемир` (or stays removed), and no second entry is created

#### Scenario: The id follows the lemma

- **WHEN** the entry `Велемир` gains the form `Велемиру`
- **THEN** its id is still the project id plus the key of `Велемир`

### Requirement: Sort and search the glossary table

The Names & style table SHALL sort by Term, Type, Gender or State when the person chooses that column's header, breaking
every tie by the source term, so that storing a change to any other column — a typed target above all — never moves its
row; and it SHALL offer a search field that shows only the rows whose source term, any of its forms or target contains
the typed text, ignoring case, and that the Escape key clears.

**Source**: FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a name is found by any spelling the book uses; a real book yields a hundred rows or more, in the order the scan found them; a person settling them needs to find one name and group the rows by kind. The glossary keeps no count of mentions, so there is no count column.

#### Scenario: Sorting by type keeps one type's rows in term order

- **WHEN** the table holds `Zeta` (place), `Alpha` (character), `Mid` and `Beta` (other) and the person sorts by Type
- **THEN** the rows read `Alpha`, `Zeta`, `Beta`, `Mid`, and storing a target for `Beta` leaves that order

#### Scenario: Searching by a target

- **WHEN** `Mid` has the target `Мід` and the person types `мід` into the search field
- **THEN** only `Mid` is shown, and pressing Escape clears the field and shows every row again

#### Scenario: Sorting by state

- **WHEN** the table holds `Zeta` (locked), `Alpha` (pinned), `Mid` (suggested) and `Beta` (suggested) and the person sorts by State
- **THEN** the rows read `Beta`, `Mid`, `Alpha`, `Zeta` (suggested, pinned, locked; ties by the source term)

#### Scenario: Searching by a form

- **WHEN** `Велемир` has the form `Велемиром` and the person types `велемиром` into the search field
- **THEN** only `Велемир` is shown

### Requirement: Lock only an entry that has a target

IF a change would leave a glossary entry pinned or locked with an empty target — setting the State to pinned or locked in
the table, emptying such an entry's target, confirming the Add term dialog with such a State and no target, or importing
a CSV row that is locked with no target — THEN the application SHALL refuse that change with the message
`A pinned or locked term needs a target.` and SHALL leave the glossary as it was.

**Source**: FR-GLOSS-03, FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `#dialog-add-glossary-term`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a pinned or locked term is checked or replaced by its target wherever it occurs; with no target there is nothing to put back, so the name would vanish from the book. Every way into the glossary refuses such an entry rather than one of them letting it through.

#### Scenario: Locking a term with no target is refused in the table

- **WHEN** the row `Hale` has an empty Target and the person sets its State to locked
- **THEN** the State stays empty, `A pinned or locked term needs a target.` is shown, and `Hale` stays as it was

#### Scenario: Emptying a locked term's target is refused

- **WHEN** `Hale` → `Гейл` is locked and the person clears its Target
- **THEN** the target stays `Гейл` and `A pinned or locked term needs a target.` is shown

#### Scenario: The Add term dialog refuses a locked term with no target

- **WHEN** the person confirms Add term with `Justine`, no target, character, female and the State locked
- **THEN** the dialog stays open with `A pinned or locked term needs a target.` and nothing is added

#### Scenario: A CSV row locked with no target is refused

- **WHEN** an imported file has the header and the line 2 `Hale,,character,male,true`
- **THEN** line 2 is reported as refused because a locked term needs a target, and no `Hale` entry is added

#### Scenario: A pinned term with no target is refused too

- **WHEN** the row `Hale` has an empty Target and the person sets its State to pinned
- **THEN** the same message is shown and `Hale` stays as it was

### Requirement: Add a term through a dialog that refuses duplicates

WHEN the person confirms the Add term dialog with a source term, a target, a type, a gender and a State, the
application SHALL add that entry as the person's, without stress marks in the target where the target language asks for
that, and IF the source term has the glossary key of an existing entry or of one of its forms (see "Compare glossary
terms by one key"), THEN it SHALL refuse the addition and say so in the dialog.

**Source**: FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-add-glossary-term`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a name the scan missed can be added by hand; two entries for the same name would contradict each other, so the second is refused rather than silently merged. A pinned or locked term also needs a target (see "Lock only an entry that has a target").

#### Scenario: A new term is added

- **WHEN** the person adds `Justine` → `Жустіна`, character, female, locked
- **THEN** the table shows `Justine` with those values

#### Scenario: A duplicate differing only in case is refused

- **WHEN** `Justine` exists and the person adds `justine` → `Юстина`
- **THEN** the dialog stays open with a message that `justine` is already in the glossary and nothing is added

#### Scenario: A possessive of a held term is refused

- **WHEN** `Justine` exists and the person adds `Justine’s`
- **THEN** the dialog says `Justine’s` is already in the glossary and nothing is added

#### Scenario: A form of a held name is refused

- **WHEN** `Велемир` exists with the form `Велемира` and the person adds `Велемира`
- **THEN** the dialog says `Велемира` is already a form of `Велемир` and nothing is added

#### Scenario: Stress marks are removed from the typed target

- **WHEN** the person adds `Zotov` with the target `Зо́тов` and the target language's file asks for stress marks to be removed
- **THEN** the table shows `Зотов`

#### Scenario: A typed target is pinned unless the person locks it

- **WHEN** the person adds `Hale` → `Гейл` with the State left as offered
- **THEN** the entry is pinned with the origin person

### Requirement: Import and export the glossary as CSV

The application SHALL export the glossary as UTF-8 CSV with the header `term,target,type,gender,locked`, quoting as
RFC 4180 defines it, written whole through a new temporary file, with a single quote before a value that starts with
`=`, `+`, `-`, `@`, a tab or a carriage return; and WHEN a CSV is imported it SHALL update an entry matched by source
term or form, add the others as the person's entries, and report the malformed and refused rows by their lines while
importing the rest.

**Source**: FR-GLOSS-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-names-and-style`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a row that says locked is locked and any other row with a target is pinned; a series keeps its names across books, so a glossary must travel as a plain file any spreadsheet can open; one bad line must not throw away a hundred good ones, and a file whose every row has the same mistake gets one sentence naming it, not five thousand line numbers. A row that would lock a term with no target is refused like a malformed one (see "Lock only an entry that has a target").

#### Scenario: A term with a comma is quoted

- **WHEN** the glossary holds `Hale, Margaret` → `Гейл, Маргарет`, character, female, locked
- **THEN** the exported line is `"Hale, Margaret","Гейл, Маргарет",character,female,true`

#### Scenario: A malformed row is reported and skipped

- **WHEN** an imported file has the header, a valid line 2 `Hale,Гейл,character,male,true`, a line 3 `Milton,Мілтон`
  with two fields, and a valid line 4
- **THEN** lines 2 and 4 are imported and line 3 is reported as malformed

#### Scenario: Every row has a type the file may not use

- **WHEN** an imported file has the header and 5,000 rows, lines 2 to 5001, each with the type `person`
- **THEN** nothing is imported and the report reads "Imported 0 rows. 5,000 lines could not be read: 2–5001. The type must be one of: character, place, organisation, product, material, term, title, other." within seconds
- **AND** runs of lines are one span, at most ten spans are named, and each cause is said once

#### Scenario: Import updates an existing term

- **WHEN** the glossary holds `Hale` → `Хейл` and an imported line reads `Hale,Гейл,character,male,true`
- **THEN** the glossary holds one `Hale` entry with target `Гейл`, locked

#### Scenario: A locked row is locked and an unlocked row with a target is pinned

- **WHEN** an imported file has the lines `Hale,Гейл,character,male,true` and `Velk,Велк,character,male,false`
- **THEN** `Hale` is locked and `Velk` is pinned, both with the origin person

#### Scenario: A row that names a form updates the lemma's entry

- **WHEN** the glossary holds `Велемир` with the form `Велемира` and an imported line reads `Велемира,Велемир,character,male,false`
- **THEN** the glossary still holds one entry `Велемир`, with the target `Велемир`

#### Scenario: Stress marks are removed on import

- **WHEN** an imported line reads `Zotov,Зо́тов,character,male,false` and the target language's file asks for stress marks to be removed
- **THEN** the entry's target is `Зотов`

### Requirement: Give the model only the terms that occur in the chunk

WHEN a call is built, the application SHALL include only the glossary entries whose lemma or any attested form occurs
as a whole word in the window's source text, each with its type, its target and the gender when the person, the book or
a rule gave it, and never a likely gender. An entry whose target the model suggested and nobody accepted SHALL be listed
apart, and neither it nor a pinned entry SHALL be hidden behind a placeholder.

**Source**: FR-GLOSS-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a small model's context is precious, and a gender the model only guessed is stored and shown to the person but not put in a prompt (the default; a paired test may change it), so a two-hundred-entry glossary is trimmed to the names in play; type and gender let the model agree verbs and adjectives with a character in languages that inflect for them. A locked name is hidden behind a placeholder, so the model is told what that placeholder will read as — otherwise it could not make `відчинив` or `відчинила` agree with it.

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

#### Scenario: A likely gender is not sent

- **WHEN** `Ivar` → `Івар` is a character whose gender is male, likely, and the window mentions `Ivar`
- **THEN** the prompt lists `Ivar → Івар (character)` with no gender
- **AND** a gender from the person, the book or a rule is listed as before

#### Scenario: A pinned name is a line, not a placeholder

- **WHEN** `Зотов` → `Зотов` is pinned and the window holds `Зотова`
- **THEN** the prompt lists the entry as a line and the source text `Зотова` is sent as written, with no placeholder

### Requirement: Read recurring terms in context with the model

WHEN the person asks the model to find or review recurring terms, the application SHALL read each candidate term, the
forms of one word counted as one candidate, in its own call at the Balanced and Max quality dials, in numbered windows
of the book, and decide from the answers by code. A window SHALL count only when the quote the model gave for it stands
in that window. A failed call SHALL end the action with its error and write nothing.

**Source**: `additional-work.md` 15i.N7; `docs/specification/01_Product/12_PROMPT_CATALOG.md#lexicon-reading`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a word such as "bank" that means a river's edge in one chapter and a business in another cannot have one rendering, so the model is shown the word in its places and the code, not the model, decides; and a series can carry its recurring terms from one book to the next in a file.

#### Scenario: A word of one sense is kept with its rendering

- **WHEN** the model reads `beacon` at Balanced, every window quoted from the text uses it as a term, one sense, rendering
  `маяк`
- **THEN** the lexicon holds `beacon` with `маяк` as a model-written rendering

#### Scenario: An ordinary or two-sense word is dropped

- **WHEN** the model reads `ferry` and calls most windows "word", or reads `bank` and says the windows show two senses
- **THEN** the term is not added in a scan and is removed in a review

#### Scenario: A named thing moves to the glossary as a suggestion

- **WHEN** the model reads `signal fire` and calls most windows "name", rendering `Сигнальне Вогнище`
- **THEN** the glossary holds it as a term entry whose target is suggested (origin likely) and the lexicon does not

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
- **THEN** it enters as a term entry whose target is a suggestion nobody has accepted

#### Scenario: The forms of a word are read as one candidate

- **WHEN** the source language is `ru` and the candidates `фонарь`, `фонаря` and `фонарём` form one group
- **THEN** one call reads the group, and its windows may be drawn from any of the three forms

### Requirement: Prepare names and recurring terms in one pass

WHEN the application prepares names and recurring terms in one pass, it SHALL run the steps the Names & style buttons
run, in this order, on a private copy of both tables: scan the book for names when the glossary is empty, read and
decide the names, ask the model's most probable gender and kind for the rest, read the recurring terms, ask a rendering
for each name and term without one, and accept the suggestions that pass the checks. It SHALL write both stored tables
once, after its last step.

**Source**: `additional-work.md` 15i.N8; `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: the last step pins or locks only the suggestions the checks accept, and lists the others; one button should get from a book to a prepared glossary without five separate presses, and an answer that arrives half-way, or a Stop, must not leave half of a result behind; and what the person fixed by hand stays fixed.

#### Scenario: A pass fills both tables and writes them at the end

- **WHEN** the glossary holds the untyped names `Brann`, `Harrowgate` and `Overture`, the recurring terms hold `ferry`,
  and the model reads `Brann` as a man followed by "he", `Overture` as not a name, `ferry` as an ordinary word and
  `beacon` as a term
- **THEN** every model call goes out against the tables as they were, and afterwards `Brann` is a male character with a
  rendering, accepted and pinned, `Overture` and `ferry` are gone, and `beacon` is a recurring term with its rendering

#### Scenario: A brand under Keep original is locked, a name in a language that declines names is not

- **WHEN** the policy is Keep original, the target is Ukrainian and the model reads `Zorbo` as a brand and `Brann` as a
  person, with the renderings `Zorbo` and `Бранн`
- **THEN** `Zorbo` is locked with the target `Zorbo` and `Brann` is pinned with `Бранн`
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
  names and the rendering passed the checks, it is locked as well; any other rendering that passed the checks is pinned

#### Scenario: The estimate of calls and minutes

- **WHEN** the application estimates the names step before the first model call, from the dial's plan over the glossary as it will stand after its scan, and nothing is measured for the chosen model
- **THEN** a call takes 3.75–7.5 seconds at Fast, 11.25–18.75 at Balanced and 11.25–22.5 at Max, and the minutes are rounded up at both ends
- **AND** with seconds stored for the model, the estimate is the calls times those seconds, as "Estimate the names pass from the seconds measured on this model" says

#### Scenario: Nothing is written when the pass stops or fails

- **WHEN** the person stops the pass (the calling thread is interrupted, after which no further model call is sent), a
  call is cancelled, or a call fails
- **THEN** nothing is written and the pass answers with that error

#### Scenario: What the person fixed stays fixed

- **WHEN** the pass runs over a locked or pinned entry, a target the person typed, a gender the person chose, a recurring term
  whose rendering the person typed, or a name or term the person removed
- **THEN** it changes none of them and adds none back
- **AND** a recurring term the person removed, or a model dropped in the pass, is not added back by a later scan this
  session, until the person adds it again

#### Scenario: The model name scan stays behind its gate

- **WHEN** the pass runs over an empty glossary
- **THEN** the deterministic scan runs, and the model name scan stays behind its gate

#### Scenario: A suggestion the checks refuse is listed, not accepted

- **WHEN** the pass has the suggestion `Валдором` for `Valdor` and the target language's file lists `-ом` as a non-nominative ending
- **THEN** `Valdor` stays suggested, and the Results view lists it with the reason "not the dictionary form"

#### Scenario: Forms are grouped before the names are decided

- **WHEN** the pass scans a Russian book with `Велемир` and `Велемиром` and the glossary is empty
- **THEN** the names step decides one entry `Велемир`, not two

### Requirement: Let every glossary column be resized

The glossary table SHALL let every column be resized, the last filling the remaining width, each column with a minimum width that keeps its header and a typical value whole, with grid lines, a header that stays visible and fixed row heights.

**Source**: `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, `docs/specification/mockups/README.md#g9--two-kinds-of-table-jx-g4`, N2 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: the person can widen what is cut, and a column can never be dragged so narrow that its content disappears. The minimum width of each column, including the new Forms and State columns, is a value of the task that builds the table, not of this requirement.

#### Scenario: A column can be resized

- **WHEN** the person drags the border of the Term column to 220 px, and then tries to drag the State column to 30 px
- **THEN** Term is 220 px wide, and State stops at its minimum, wider than 30 px, with its header `State` and the value `pinned` shown whole

### Requirement: Show the recurring terms with their purpose, use count and empty state

The Recurring terms card SHALL show the purpose line "Words the book repeats — kept the same all through the book. Learned while translating, or set here." and the columns Term, Forms, Translation, Used and a link to the glossary, Used being the number of times the term or any form of its group occurs in the book's text, ignoring letter case; an empty card SHALL read "No recurring terms yet. They are found when you prepare names, or while translating."

**Source**: `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N1 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: the person sees how often each repeated word occurs and where such words come from. Used counts the whole group of forms and ignores letter case: `Warden` and `warden` count together, and for a language with an ending table the inflected forms of a word count too; a language with none counts the forms exactly as written.

#### Scenario: An empty lexicon says where terms come from

- **WHEN** no recurring term exists yet
- **THEN** the card reads "No recurring terms yet. They are found when you prepare names, or while translating."

#### Scenario: Used counts exact word forms, ignoring case

- **WHEN** the source language is `en`, which sets no ending table, and the book's text holds `warden` 9 times, `Warden` 4 times and `wardens` 3 times
- **THEN** the row of `warden` shows Used `13 times`

#### Scenario: Used counts the whole group of forms

- **WHEN** the source language is `ru` and the book's text holds `фонарь` 3 times, `фонаря` 4 times and `фонарём` 2 times
- **THEN** the row of `фонарь` shows Forms `фонаря, фонарём` and Used `9 times`

### Requirement: Apply glossary edits from the next chunk and keep the glossary across a restart

WHEN the person edits the glossary while a run is paused or translating, the application SHALL apply the edit from the first chunk that starts after it, SHALL NOT change segments already decided except through the Proofread stage, and SHALL keep the glossary stored with the project across a restart; a person's edit and a name the run adds SHALL never overwrite each other.

**Source**: FR-GLOSS-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), ADR-0043, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `docs/specification/mockups/README.md#changes-while-translating-i`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a person who spots a wrong spelling mid-run can fix it, and the rest of the book follows; earlier segments are corrected by the Proofread stage or on the review desk, not silently. A chunk reads the glossary once, when it starts. Each change is written to the stored entry in one step, so the run adding a name at the end of a chapter and the person typing a target at the same moment both survive.

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

#### Scenario: The words of a repeated place are not recurring terms

- **WHEN** a book holds `the Great Hall` 4 times, `Old Bailey` 3 times and `the great hall` and `the old courts` in lower case
- **THEN** the key-term scan proposes neither `great`, `hall` nor `old`, while `Master` written 3 times with a capital beside no other capital and `master` 3 times in lower case is still proposed

### Requirement: Keep a stored lexicon of recurring terms and show it on Names & style

The application SHALL keep, for each project, a stored lexicon of recurring common terms and titles that a model would otherwise render differently from one page to the next, each entry holding its representative form and the forms of its group, SHALL propose them with a deterministic scan and no model, and SHALL show them in a "Recurring terms" card; a term the glossary holds SHALL NOT be asked about or shown from the lexicon.

**Source**: `additional-work.md` 15d.9, `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`, `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, ADR-0043, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a book that calls a warden "наглядач" in chapter one and "сторож" in chapter nine reads as two characters; the app finds the words a book keeps repeating and keeps every later request to one rendering. The lexicon is stored with the project, so it is still there after a restart.

#### Scenario: The scan proposes a title and a dual-use word

- **WHEN** a book's running text holds `Mr.` four times, and `Warden` capitalised mid-sentence three times and `warden` in lower case three times
- **THEN** the scan proposes `mr` and `warden` and nothing for a name written only with a capital or a word written only in lower case

#### Scenario: A held term is not proposed

- **WHEN** the glossary holds `Warden` and the lexicon holds `MR`
- **THEN** the scan proposes neither

#### Scenario: Editing a rendering

- **WHEN** the person types `пан` in the rendering field of `master` and presses Enter
- **THEN** the lexicon stores `пан` as the person's rendering of `master`, every later request is asked to keep it, and clearing the field returns to the most used verified rendering

#### Scenario: Promoting a term

- **WHEN** the person presses "To glossary" on `master`, whose established rendering is `господар`
- **THEN** the glossary holds `master` → `господар` (term, pinned, the person's target) and the Recurring terms card no longer lists `master`

#### Scenario: A duplicate term is refused

- **WHEN** the person types `WARDEN` in the add field while `warden` is listed
- **THEN** the line above the glossary says `WARDEN is already in the glossary or the list` and no term is added

#### Scenario: What the deterministic scan proposes

- **WHEN** the scan reads the book's running text
- **THEN** it proposes a title of the source language's bundled list (English first) that the text uses at least three times, and a word the book writes both with a capital away from a sentence start and not beside another capitalised word (so it is no part of a name such as `Great Hall`), and in lower case (so it is neither a name nor a function word) at least five times, at most thirty
- **AND** never a term the glossary or the lexicon already holds

#### Scenario: When the scan runs

- **WHEN** a run is prepared and the lexicon is empty, or the person asks on Names & style ("Find recurring terms")
- **THEN** the scan runs
- **AND** a word that is only ever lower case, such as `lantern`, is added by hand

#### Scenario: What the card shows

- **WHEN** the Recurring terms card lists `master`, which the book's text uses 13 times
- **THEN** it shows the term, its Translation (the person's if typed, else the learned or most used verified rendering, else the model's suggestion) and Used `13 times`
- **AND** there is no separate column for the renderings the drafts used

#### Scenario: What the card lets the person do

- **WHEN** the person works in the card
- **THEN** they can edit the rendering (Enter or leaving the field stores it; clearing it returns to the most used), suggest renderings with the model (the glossary's suggestion call over terms of type term, needing a chosen model), add a term by hand (one the glossary or the list already holds is refused in the screen's banner), and remove a term
- **AND** they can promote a term to the glossary, which stores it there as a pinned entry of type term with its established rendering as the target and removes it from the lexicon

#### Scenario: The lexicon is stored and an edit changes the stored entry

- **WHEN** the person types `пан` as the rendering of `master`, the application is closed and opened again, and the project is opened
- **THEN** the card still lists `master` with `пан`
- **AND** the edit changed the entry as it stands in the store, in one step, never from a copy read earlier: a count a running draft records meanwhile is kept, and a term removed or promoted meanwhile is not created again

#### Scenario: The forms of a word are one entry

- **WHEN** the source language is `ru` and the scan meets `фонарь`, `фонаря` and `фонарём`
- **THEN** the lexicon holds one entry whose representative form is `фонарь` and whose forms are `фонаря` and `фонарём`, and a stored edit of its rendering applies to all three

### Requirement: Offer the one-pass preparation in a progress dialog

The Names & style screen SHALL offer "Prepare names automatically" in a card above both tables that says what it does and how long it takes for the brief's quality dial ("About 2 min, measured on this model. You can undo it all afterwards." or, with nothing measured, "About 1–2 min, estimate."), and a press SHALL run the names pass on the chosen model in the progress dialog, whose Cancel ("Cancel writes nothing.") stops the pass and leaves both tables as they were.

**Source**: `additional-work.md` 15i.U6, `docs/specification/mockups/README.md#step-3--names--style--n1n5-i`, N4, N5 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: one button replaces five presses and gives every change back one by one or all together. Hiding the dialog with Continue in background or Esc, and the activity chip, are the `app-shell` capability's progress-dialog rules.

#### Scenario: The press fills both tables and Undo all gives both back

- **WHEN** the person presses "Prepare names automatically" and the pass adds `Mira Holt`, drops `Hale` and adds `lantern`
- **THEN** the Results view shows "2 added · 1 removed" in its subtitle and lists the three changes in the Changes tab
- **AND** Undo all removes `Mira Holt` and `lantern` and puts `Hale` back

#### Scenario: Cancel in the progress dialog changes nothing

- **WHEN** the person presses Cancel in the progress dialog while the pass waits for the model
- **THEN** the dialog says "Stopped. Nothing was changed." and both tables are as they were

#### Scenario: The person's entries are left alone

- **WHEN** the glossary holds a locked `Gatewarden`, a `Marrow Lane` with the person's target and a `Wren` whose female gender the person chose, and the lexicon holds `anvil` with the person's rendering
- **THEN** after the pass those entries are unchanged, `Wren` is still female, and `anvil` was not asked about

#### Scenario: A removal is remembered

- **WHEN** the person removed the recurring term `gantry` and the model would keep it
- **THEN** the pass does not add `gantry` back

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

#### Scenario: The pass runs in the progress dialog

- **WHEN** the pass runs
- **THEN** the progress dialog is shown on the work layer, the computer is kept awake, and a stopped or failed pass leaves both tables as they were

#### Scenario: Changed rows are marked on both cards

- **WHEN** the pass succeeds and the person presses Show changed rows
- **THEN** every changed row on both cards shows the left-edge mark, and both cards' changed filters are on

### Requirement: Learn a recurring term's rendering from the decided segments

The application SHALL learn a lexicon term's rendering from the decided segments with no model call, so the book stays consistent whatever the model reports: once a chunk's records are stored, each decided pair SHALL be counted by word stems, and a term SHALL get a learned rendering only when at least three of its segments carry one stem, the stem's Dice association with the term is at least 0.6 and at least twice that of any unrelated rival.

**Source**: `additional-work.md` 15d.9; `docs/specification/01_Product/12_PROMPT_CATALOG.md#recurring-terms`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: the decided text is read, not the model's report of it, and the majority wins so one early slip does not outvote a book's habit.

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

- **WHEN** a model renders `master` as `господар` in three stored chapters, and would write `учитель` in the fourth unless shown the block
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

## REMOVED Requirements

### Requirement: List the eight steps of the names pass in its progress dialog

**Reason**: The pass has new steps (what the model knows about the names, and the check that accepts suggestions), so the fixed list of eight no longer describes it.

**Migration**: Replaced by "List the steps of the names pass in its progress dialog", which lists the ten steps with their request counts.

### Requirement: Record the renderings a draft used and keep them consistent

**Reason**: The batch reply no longer carries a `terms` object and the model is no longer asked to report the renderings it used, so there is nothing to verify or count; the decided segments are read instead (the default; a paired test named in design.md can bring the report back).

**Migration**: "Learn a recurring term's rendering from the decided segments" (modified: it stands alone) and "Keep one established rendering for each recurring term" (added: the order that picks the rendering and its display in later calls)
