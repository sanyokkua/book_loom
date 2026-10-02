# Spec Delta

## Purpose

The gate between a draft and an accepted translation: the hard gates a translated segment can never be accepted
without, the soft checks that blend into a deterministic confidence, the trust threshold each review mode sets, the
per-chunk model judge, and the self-heal rounds that repair a failing segment before it is flagged for review.

## ADDED Requirements

### Requirement: Treat placeholder integrity, including the order of paired placeholders, as a hard gate

IF a translated segment's markup does not restore — its placeholder tokens differ from its source's as a multiset, a
paired placeholder comes back closing-before-opening or improperly nested with another pair, or the target breaks one
of the pair rules the document round trip checks — THEN the application SHALL fail that segment's placeholder hard
gate, record a high `markup` finding, and SHALL NOT accept the segment, whatever its confidence or judge score.

**Source:** FR-QA-04, FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, ADR-0040, ADR-0038.
In plain words: the placeholder check the document round trip performs before restoring markup (see the
document-round-trip capability, which also owns the two pair rules: a pair that held text must still hold text, and a
line break stays inside the same pair) is the first quality gate; a pair that comes back crossed would restore broken
markup and fail the whole export at the very end, so it is caught here for one segment instead. An atomic placeholder
— an image, a line break, a locked term — may move, because word order legitimately changes. A draft that fails this
gate first gets its one placeholder repair (the translation-pipeline capability's "Repair invalid structured draft
replies once"); only a segment still failing after it reaches self-heal.

#### Scenario: A crossed pair fails the hard gate

- **WHEN** the masked source is `He opened the ⟦g0⟧old⟦g1⟧ door.` with `⟦g0⟧`/`⟦g1⟧` recorded as one pair
- **AND** the model returns `Він відчинив ⟦g1⟧старі⟦g0⟧ двері.`
- **THEN** the restore answers `ErrorCode.validation`, the placeholder hard gate fails and a high `markup` finding is
  recorded
- **AND** the segment is not accepted even though its token multiset `{⟦g0⟧, ⟦g1⟧}` matches

#### Scenario: A missing atomic token fails the hard gate

- **WHEN** the masked source is `See ⟦g0⟧ here.` and the model returns `Дивіться тут.`
- **THEN** the placeholder hard gate fails because `⟦g0⟧` is absent

#### Scenario: A moved atomic token passes

- **WHEN** the masked source is `⟦g0⟧ A drawing of the castle.` with `⟦g0⟧` an image
- **AND** the model returns `Малюнок замку. ⟦g0⟧`
- **THEN** the placeholder hard gate passes

### Requirement: Treat each protected span's placeholder as a hard gate

IF a translated segment does not return, exactly once each, the placeholders that stand for its protected spans — its
locked glossary terms and, under the `Keep as-is` foreign-passage policy, its kept foreign runs — THEN the application
SHALL fail that segment's protected-span hard gate, record a high finding — `glossary` for a locked term, `markup` for
a kept foreign run — and SHALL NOT accept it. WHERE locked terms overlap in a segment, the application SHALL hide the
longest match first, so a shorter locked term inside a longer one is not hidden separately.

**Source:** FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), EC-FOREIGN-3
(`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, ADR-0037.
In plain words: a locked name (see the glossary capability's "Render a locked term exactly as entered") and a foreign
phrase the book keeps verbatim (see "Keep an inline foreign run verbatim under the Keep as-is policy") both reach the
model as one placeholder, so the model can neither misspell the name nor translate the phrase. A reply that drops or
duplicates such a placeholder would lose or double the name or the phrase, so it can never pass. Like any placeholder,
a protected one may move. When one locked term contains another, the longer wins, so `Baker Street` is never split into
a placeholder for `Baker` followed by the word `Street`.

#### Scenario: A dropped locked term fails

- **WHEN** `Hale` is locked as `Гейл` and the segment `Hale opened the door.` is sent as `⟦g0⟧ opened the door.`
- **AND** the model returns `Він відчинив двері.`
- **THEN** the protected-span hard gate fails with a high `glossary` finding and the segment is not accepted

#### Scenario: A returned locked term passes and is restored

- **WHEN** the same segment's reply is `⟦g0⟧ відчинив двері.`
- **THEN** the protected-span hard gate passes and the target reads `Гейл відчинив двері.`

#### Scenario: The longest locked term is hidden first

- **WHEN** `Baker Street` and `Baker` are both locked and the segment is `Baker Street was quiet; Baker left.`
- **THEN** the model receives `⟦g0⟧ was quiet; ⟦g1⟧ left.`

#### Scenario: A repeated kept foreign run fails

- **WHEN** the policy is `Keep as-is` and `She whispered <i xml:lang="fr">au revoir</i> and left.` is sent as
  `She whispered ⟦g2⟧ and left.`
- **AND** the model returns `Вона прошепотіла ⟦g2⟧ і пішла ⟦g2⟧.`
- **THEN** the protected-span hard gate fails with a high `markup` finding and the segment is not accepted

### Requirement: Keep an inline foreign run verbatim under the Keep as-is policy

WHILE the Book Brief's foreign-passage policy is `Keep as-is`, the application SHALL hide each inline element whose own
`lang` or `xml:lang` differs from the brief's source language — compared after normalizing both tags — together with
its text behind one placeholder before the segment reaches the model, SHALL restore that element and its text
verbatim, and SHALL decide which runs are foreign from those declarations alone, never from the text. This SHALL hold for any
language the application recognizes, not only a listed one. WHILE the policy is `Keep as-is`, a whole block whose
declared language — its own, or an ancestor's below the document root — differs from both the brief's source language
and the language the book's metadata declares, after normalizing all three, SHALL be hidden whole behind one
placeholder, SHALL reach no model call, and SHALL be kept as it is (counted as kept as is, path verbatim); a block
declaring the book's own language is never foreign, even when the brief names another source. Under `Translate` and
`Translate with a note` such a block SHALL be drafted like any other, the second telling the model to add the original
wording in parentheses after the translation.

**Source:** EC-FOREIGN-3 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), DD-26
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), FR-QA-03
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), ADR-0037.
In plain words: a French farewell inside an English sentence must stay French when the person chose to keep foreign
passages, and the surest way is to never show it to the model. Only a run the book itself marks with another language
is kept: the application does not detect languages from text (ADR-0037), and a region variant of the source language
is not foreign. Under the other two policies the run is translated like the rest of the sentence. The same holds for a
whole paragraph the book marks: the fixture book's `<p xml:lang="la">Gravitas omnia trahit, sed nemo videt.</p>` was
sent to the model under Keep as-is, translated and judged 0.95, because only inline runs were hidden and the prompt's
"keep it verbatim" line was not enough for a small model. A marked block is now never sent at all. Comparing with the
book's own declared language too keeps a brief whose source was set to another language from leaving every paragraph
under a `<body xml:lang="en">` untranslated.

#### Scenario: A marked French run reaches the model as one placeholder

- **WHEN** the policy is `Keep as-is`, the source language is `en`, and the paragraph is
  `She whispered <i xml:lang="fr">au revoir</i> and left.`, whose `<i>` pair is `⟦g0⟧`/`⟦g1⟧`
- **THEN** the model receives `She whispered ⟦g2⟧ and left.`
- **AND** the reply `Вона прошепотіла ⟦g2⟧ і пішла.` is restored as `Вона прошепотіла <i xml:lang="fr">au revoir</i> і
  пішла.`

#### Scenario: A region variant of the source language is not foreign

- **WHEN** the policy is `Keep as-is`, the source language is `en`, and the run is `<i xml:lang="en-GB">colour</i>`
- **THEN** the run is sent as `⟦g0⟧colour⟦g1⟧` and translated with the sentence

#### Scenario: A marked Latin run is kept in an English book

- **WHEN** the policy is `Keep as-is`, the source language is `en`, and the paragraph is
  `He read <i xml:lang="la">memento mori</i> aloud.`, whose `<i>` pair is `⟦g0⟧`/`⟦g1⟧`
- **THEN** the model receives `He read ⟦g2⟧ aloud.`, one placeholder for the Latin run

#### Scenario: A marked Latin paragraph is kept without a model call

- **WHEN** the policy is `Keep as-is`, the source language and the book's declared language are `en`, and the block is
  `<p xml:lang="la">Gravitas omnia trahit, sed nemo videt.</p>`
- **THEN** no draft call is sent for it and its record is accepted on the verbatim path with the target
  `Gravitas omnia trahit, sed nemo videt.`

#### Scenario: A marked French run inside a paragraph is still one placeholder

- **WHEN** the policy is `Keep as-is` and the paragraph is `C'était <span lang="fr">très bien</span>, he said.` in an
  English book
- **THEN** the model receives the paragraph with one placeholder for the French run and translates the prose around it

#### Scenario: Translate drafts a marked paragraph

- **WHEN** the policy is `Translate` and the block is `<p xml:lang="la">Gravitas omnia trahit, sed nemo videt.</p>`
- **THEN** the block is drafted like any other paragraph

#### Scenario: Translate with a note asks for the original in parentheses

- **WHEN** the policy is `Translate with a note` and the same block is drafted
- **THEN** the draft's system message tells the model to add the original wording in parentheses right after the
  translation

#### Scenario: A block declaring the book's own language is not foreign

- **WHEN** the policy is `Keep as-is`, the brief's source is `de`, the book declares `en`, and a block declares `en`
- **THEN** the block is shown to the model as it is

#### Scenario: The Translate policy translates a marked run

- **WHEN** the policy is `Translate` and the paragraph is `She whispered <i xml:lang="fr">au revoir</i> and left.`
- **THEN** the model receives `She whispered ⟦g0⟧au revoir⟦g1⟧ and left.`

### Requirement: Treat a refusal or an empty target as a hard gate

IF a translated target's display text — its text with every placeholder removed and whitespace collapsed — is empty
while its source's is not, or the target, trimmed and compared without regard to case and with a typographic
apostrophe read as `'`, begins with a phrase that talks about the translation task or the model — such as `I cannot
translate`, `As an AI`, `Here is the translation` or `Ось переклад` — in English, the source language or the target
language, followed by the end of the text or by a character that is neither a letter nor a digit, and the source's own
display text does not begin with such a phrase, THEN the application SHALL fail that segment's refusal hard gate, record a high `meaning` finding, and SHALL
NOT accept it.

**Source:** FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`.
In plain words: a model that declines, apologises or talks about the text instead of translating it has produced
nothing a reader can use; letting it through would put the apology into the book. Only phrases about the task or the
model count — the full list per language is in `05_PIPELINE_ENGINE.md#qa-thresholds` — so a character's own apology
is translated, not refused; a phrase must end where a word ends, so `Як ШІ` never catches `Як шість`, and a book whose own
line begins `Translation:` keeps its translated `Переклад:`.
A reply that holds only its placeholders would silently delete the paragraph's words. A reply with no text at all, or
a blank `target`, is not this gate's case: the translation-pipeline capability flags or repairs it before any check.
A segment with nothing to translate — no letter, a Roman numeral or a single character — never reaches this gate: it is
kept as it is with no model call (`translation-pipeline` "Keep a segment with nothing to translate as it is"), so an
empty reply to the chapter number `2` can no longer flag it.

#### Scenario: An English refusal fails

- **WHEN** the source is `He opened the old door.` and the target is `I'm sorry, but I can't translate this text.`
- **THEN** the refusal hard gate fails with a high `meaning` finding

#### Scenario: A refusal in the target language fails

- **WHEN** the target language is Ukrainian and the target is `Вибачте, я не можу перекласти цей текст.`
- **THEN** the refusal hard gate fails

#### Scenario: A preamble about the translation fails

- **WHEN** the target is `Ось переклад: Він відчинив старі двері.`
- **THEN** the refusal hard gate fails

#### Scenario: A typographic apostrophe does not hide a refusal

- **WHEN** the target is `I’m sorry, but I can’t translate this.`, written with U+2019 apostrophes
- **THEN** the refusal hard gate fails

#### Scenario: A character's own apology passes

- **WHEN** the source is `Sorry, I can't go with you.` and the target is `Вибачте, я не можу піти з вами.`
- **THEN** the refusal hard gate passes

#### Scenario: A phrase is matched only as whole words

- **WHEN** the target is `Як шість років тому він пішов.` or `As an aide, she opened the door.`
- **THEN** the refusal hard gate passes

#### Scenario: The book's own heading is not a refusal

- **WHEN** the source is `Translation: the art of carrying meaning.` and the target is
  `Переклад: мистецтво перенесення змісту.`
- **THEN** the refusal hard gate passes

#### Scenario: A reply of placeholders only fails

- **WHEN** the masked source is `He opened the ⟦g0⟧old⟦g1⟧ door.` and the target is `⟦g0⟧⟦g1⟧`
- **THEN** the refusal hard gate fails, because the target's display text is empty while the source's is not
- **AND** the segment goes to self-heal

### Requirement: Check the target script for pairs whose languages use different scripts

WHEN the source's display text has at least 20 code points, the source and target languages use different scripts,
and the target has letters, the application SHALL fail the target-script check when fewer than 0.60 of the target's
letters are written in the target language's script, and otherwise SHALL pass it with a margin of
(share − 0.60) / 0.20, at most 1.0; a source language whose script the application does not know counts as using a
different script. In every other case it SHALL skip the check, which then counts as a margin of 1.0 — including when
the target language's script is not known to the application. Protected spans SHALL never count toward the share,
and under the `Keep original` name policy neither SHALL any whole-word occurrence of a glossary term.

**Source:** FR-QA-01, FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`, ADR-0037.
In plain words: with no language detector, the only reliable signal is the alphabet — a Ukrainian translation
written in Latin letters is plainly wrong. The check cannot tell Polish from Czech, so a same-script pair skips it and
a wrong language in the same script is left to the judge and to review; short fragments skip it so a name or an
exclamation is not misjudged. Names the person chose to keep in their original spelling are Latin by design, so they
are not held against a Cyrillic line. Serbian is catalogued as Cyrillic, so Latin-script Serbian output fails.

#### Scenario: A Cyrillic target for English source passes

- **WHEN** English → Ukrainian, the source is `He opened the old door.` (23 code points)
- **AND** the target is `Він відчинив старі двері.`
- **THEN** the target-script check passes with a margin of 1.0

#### Scenario: A Latin target for a Ukrainian book fails

- **WHEN** English → Ukrainian and the target for `He opened the old door.` is `Vin vidchynyv stari dveri.`
- **THEN** the target-script check fails, since 0.0 of its letters are Cyrillic

#### Scenario: A same-script pair skips the check

- **WHEN** English → Polish and the target is `Otworzył stare drzwi.`
- **THEN** the target-script check is skipped and contributes a margin of 1.0

#### Scenario: A same-script wrong language is not caught

- **WHEN** English → Polish and the model answers in Czech with `Otevřel staré dveře.`
- **THEN** the target-script check is skipped and does not fail the segment

#### Scenario: A target language whose script is not known skips the check

- **WHEN** English → Latin (`la`) and the source is `He opened the old door.` (23 code points)
- **THEN** the target-script check is skipped and contributes a margin of 1.0

#### Scenario: A short source skips the check

- **WHEN** English → Ukrainian and the source is `Yes, sir.` (9 code points)
- **THEN** the target-script check is skipped and contributes a margin of 1.0

#### Scenario: Names kept by policy do not count

- **WHEN** English → Ukrainian, the name policy is `Keep original`, the glossary holds `Hale`, `Margaret`, `Milton` and
  `London`, the source is `Hale and Margaret Hale left Milton for London early that cold morning.`
- **AND** the target is `Hale і Margaret Hale виїхали з Milton до London рано того холодного ранку.`
- **THEN** the share is 1.0 and the target-script check passes
- **AND** under the `Transliterate` name policy the same target's share is 0.54 and the check fails

### Requirement: Fail a target that echoes its source

The application SHALL measure a target's similarity to its source as 1 minus their edit distance divided by the
longer text's length, over both display texts after Unicode normalization and lower-casing, SHALL fail the
untranslated-echo check when the similarity is 0.90 or more, and SHALL otherwise pass it with a margin of
(0.90 − similarity) / 0.10, at most 1.0. Under the `Keep original` name policy, every whole-word occurrence of a
glossary term SHALL be removed from both texts before they are compared. IF the source's display text has fewer than 20
code points and, with every whole-word occurrence of a glossary term removed under any name policy, holds no letter,
THEN the application SHALL skip the check: the glossary explains the echo.

**Source:** FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`.
In plain words: a model that copies the source back has translated nothing; comparing lower-cased text stops a copy in
capitals from slipping past. A line made only of names the person chose to keep is meant to read like its source, so
the names are taken out before the comparison. A short line that is only glossary names, such as `Bartimaeus!`, is
explained by the glossary whatever the policy — how a name is rendered is the glossary check's question, not the echo's.
Whether a failed echo blocks the segment depends on the source's length (see "Block acceptance when a soft check fails
outright").

#### Scenario: A verbatim copy fails

- **WHEN** the source is `He opened the old door.` and the target is `He opened the old door.`
- **THEN** the untranslated-echo check fails (similarity 1.0)

#### Scenario: A copy in capitals still fails

- **WHEN** the target is `HE OPENED THE OLD DOOR.`
- **THEN** the untranslated-echo check fails (similarity 1.0)

#### Scenario: A real translation passes

- **WHEN** the target is `Він відчинив старі двері.`
- **THEN** the untranslated-echo check passes with a margin of 1.0 (similarity 0.12)

#### Scenario: A names-only line kept by policy is not an echo

- **WHEN** English → Ukrainian, the name policy is `Keep original`, the glossary holds `Margaret Hale` and
  `Milton Northern`, and `Margaret Hale, Milton Northern.` comes back unchanged
- **THEN** what remains after removing the names holds no letter, so the untranslated-echo check is skipped
- **AND** with the judge off the segment is accepted in Assisted with confidence 1.0

#### Scenario: A short glossary name is explained by the glossary

- **WHEN** the name policy is `Transliterate`, the glossary holds `Bartimaeus`, and `Bartimaeus!` comes back unchanged
- **THEN** the untranslated-echo check is skipped and counts 1.0

#### Scenario: A short line with words beside a name still counts

- **WHEN** the glossary holds `Bartimaeus` and `Bartimaeus said.` (16 code points) comes back unchanged
- **THEN** the untranslated-echo check fails and records a low `language` finding, without blocking

### Requirement: Fail a target caught in a repetition loop

IF any sequence of 3 consecutive words repeats 3 or more times in a row in a target, THEN the application SHALL fail
the repetition check for that segment; otherwise it SHALL pass it with a margin of 0.5 when a sequence of 3 words
repeats exactly twice in a row, and 1.0 when none does.

**Source:** FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`.
In plain words: small local models sometimes get stuck repeating a phrase until the output limit; this catches that
decode loop without punishing ordinary repetition in prose, while a phrase said twice over lowers confidence a little.

#### Scenario: Three repeats in a row fail

- **WHEN** the target is `двері відчинилися знову двері відчинилися знову двері відчинилися знову`
- **THEN** the repetition check fails

#### Scenario: Two repeats pass

- **WHEN** the target is `двері відчинилися знову двері відчинилися знову і він увійшов`
- **THEN** the repetition check passes with a margin of 0.5

### Requirement: Check the length ratio against the band of the language pair

The application SHALL fail the length-ratio check when target length divided by source length, in display-text
characters, falls outside the band of the pair's script class — Latin → Cyrillic 0.7–1.8, same script 0.6–1.7,
Latin → CJK 0.2–1.0, CJK → Latin 1.0–5.0, any other pair 0.5–2.5 — with the lower bound halved and the upper bound
doubled when the source is shorter than 25 characters, and SHALL otherwise pass it with a margin of the distance to the
nearer bound divided by one tenth of the band's width, at most 1.0. Inside the band the check SHALL still fail when
words look missing: when the target has more letter-word-followed-by-space-then-full-stop-or-comma spots (`помогою .`)
than the source, or — for a source of at least 8 words, neither text in a script written without spaces — when the
target has fewer than 0.55 words per source word. Its finding SHALL name which of the two it saw.

**Source:** FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`.
In plain words: a translation far shorter than its source has usually dropped content, and one far longer has usually
added some; the band differs by writing system because Chinese is naturally much shorter than English, and very
short segments get a wider band because one word more or less changes their ratio a lot. A ratio just inside the band
passes, but with less confidence than one comfortably inside it. A dropped name or phrase can leave the character count
inside the band, so the space left before a full stop and a word count far below the source's are read as well; 0.55
sits well below any real pair's word ratio (English to Ukrainian runs about 0.8), so a faithful translation is never
caught by it.

#### Scenario: A space left before a full stop fails

- **WHEN** English → Ukrainian, the source is `We measured it with the help of the old brass pendulum and Nell.` and the
  target `Ми виміряли це за допомогою старого латунного маятника та .`
- **THEN** the length-ratio check fails with a finding saying a word is missing before a full stop or comma

#### Scenario: A half-length Ukrainian target fails

- **WHEN** English → Ukrainian, the source has 100 characters and the target 50 (ratio 0.5)
- **THEN** the length-ratio check fails, since 0.5 is below 0.7

#### Scenario: A normal Ukrainian target passes

- **WHEN** English → Ukrainian, the source has 100 characters and the target 115 (ratio 1.15)
- **THEN** the length-ratio check passes with a margin of 1.0

#### Scenario: A target near the band's edge passes with half the margin

- **WHEN** English → Ukrainian, the source has 200 characters and the target 151 (ratio 0.755)
- **THEN** the length-ratio check passes with a margin of 0.5, since 0.755 lies 0.055 above 0.7 and one tenth of the
  band's width is 0.11

#### Scenario: A short source uses the widened band

- **WHEN** English → Ukrainian, the source is `Go.` (3 characters) and the target is `Іди-но звідси.` (14 characters, ratio 4.67)
- **THEN** the band is 0.35–3.6 and the length-ratio check fails
- **AND** the target `Йди.` (ratio 1.33) passes

#### Scenario: An unlisted pair uses the default band

- **WHEN** Ukrainian → Greek and the ratio is 2.3
- **THEN** the band 0.5–2.5 applies and the length-ratio check passes

### Requirement: Measure glossary compliance over the locked terms in the segment

The application SHALL pass glossary compliance, with a margin of 1.0, when every locked glossary term occurring in the
source segment appears in the target as its entered rendering, SHALL fail it otherwise, and SHALL skip it, counting
1.0, when the segment contains no locked term.

**Source:** FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), FR-GLOSS-03
(`#fr-gloss`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`.
In plain words: a name the person locked must read exactly as they entered it; unlocked entries are only guidance
for the model and do not lower the score. Because every locked term travels as a placeholder and comes back as its
rendering, this check passes whenever the protected-span hard gate did; it stays in the blend because the reference
weights include it.

#### Scenario: Every locked term rendered

- **WHEN** `Hale` → `Гейл` and `Milton` → `Мілтон` are locked and the target contains both renderings
- **THEN** glossary compliance is 1.0

#### Scenario: No locked term in the segment

- **WHEN** the segment `The rain did not stop.` contains no locked term
- **THEN** glossary compliance is skipped and counts 1.0

#### Scenario: An unlocked term does not count

- **WHEN** `Baker Street` → `Бейкер-стріт` is unlocked and the target renders it `вулиця Бейкер`
- **THEN** glossary compliance is 1.0

### Requirement: Skip the echo and script checks for a marked foreign passage kept by policy

WHILE the Book Brief's foreign-passage policy is `Keep as-is`, the application SHALL skip the untranslated-echo and
target-script checks, each counting 1.0, for a segment marked foreign — the block it belongs to declares its own
language and that language differs from the brief's source language after both tags are normalized, or its dominant
script differs from the source language's — and SHALL apply both checks to every unmarked segment. The comparison SHALL apply to any language the application
recognizes, not only a listed one.

**Source:** FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds` (foreign-keep vs echo), ADR-0037.
In plain words: a French line the author left untranslated in an English novel must stay French when the person
chose to keep foreign passages, and that is not a failure; but only a passage the book itself marks, or one in another
alphabet, earns that pass — a segment that merely echoes its source is still caught. The comparison is with the source
language the person confirmed on the brief, not with what the book's package claims, so a book whose package declares
the wrong language does not have every paragraph marked foreign.

#### Scenario: A marked French block is kept without failing

- **WHEN** the policy is `Keep as-is`, the brief's source language is `en`, and the block declares `lang="fr"`
- **AND** the target for `Je ne regrette rien.` is `Je ne regrette rien.`
- **THEN** the untranslated-echo and target-script checks are skipped and count 1.0

#### Scenario: A Latin block in an English book is marked foreign

- **WHEN** the policy is `Keep as-is`, the brief's source language is `en`, and the block declares `lang="la"`
- **AND** the target for `Memento mori.` is `Memento mori.`
- **THEN** the segment is marked foreign, and the untranslated-echo and target-script checks are skipped and count 1.0

#### Scenario: A Greek-script segment in an English book is kept

- **WHEN** the policy is `Keep as-is` and the segment `Γνῶθι σεαυτόν` in an English book returns unchanged
- **THEN** the untranslated-echo and target-script checks are skipped

#### Scenario: An unmarked echo still fails

- **WHEN** the policy is `Keep as-is` and the unmarked English segment `He opened the old door.` returns unchanged
- **THEN** the untranslated-echo check fails

#### Scenario: The Translate policy applies the checks to a marked block

- **WHEN** the policy is `Translate` and the `lang="fr"` block returns `Je ne regrette rien.` unchanged
- **THEN** the untranslated-echo check fails

#### Scenario: The brief's source language decides, not the package's

- **WHEN** the policy is `Keep as-is`, an EPUB's package and `<html>` elements declare `uk`, its text is English, and the
  person set the source language to `en` on the brief
- **THEN** a `<p xml:lang="fr">` block is marked foreign
- **AND** a `<p>` that declares no language of its own is not marked, and its echo is checked

### Requirement: Blend the soft-check margins into a documented confidence

The application SHALL compute each segment's confidence as 0.30 × glossary + 0.25 × length ratio + 0.20 × target
script + 0.15 × untranslated echo + 0.10 × repetition, each term the check's margin between 0 and 1, a failed check
counting 0.0 and a skipped check 1.0, and SHALL NOT fold the hard gates or the judge score into it.

**Source:** FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#confidence`.
In plain words: one number says how comfortably a segment clears the deterministic checks; hard gates stay
pass/fail because no score can excuse broken markup, and the judge stays separate so a strong judge cannot hide a
failed check, nor a weak one a clean segment. Each check's margin is stated in its own requirement above.

#### Scenario: A worked confidence

- **WHEN** English → Ukrainian, the segment has no locked term, its length margin is 0.5 (ratio 0.755), and its
  script, echo and repetition margins are 1.0
- **THEN** confidence is 0.30 × 1.0 + 0.25 × 0.5 + 0.20 × 1.0 + 0.15 × 1.0 + 0.10 × 1.0 = `0.875`

#### Scenario: Skipped checks count in full

- **WHEN** English → Polish (script check skipped), the segment has no locked term (glossary skipped), and the
  length, echo and repetition margins are 1.0
- **THEN** confidence is `1.0`

#### Scenario: Failed checks count as zero

- **WHEN** English → Ukrainian and the target for `He opened the old door.` is `He opened the old door.`, failing the
  echo and script checks, with no locked term and length and repetition margins of 1.0
- **THEN** confidence is 0.30 × 1.0 + 0.25 × 1.0 + 0.20 × 0.0 + 0.15 × 0.0 + 0.10 × 1.0 = `0.65`

#### Scenario: The judge score does not move confidence

- **WHEN** the English → Polish segment above receives a chunk judge score of `0.40`
- **THEN** its confidence stays `1.0`

### Requirement: Block acceptance when a soft check fails outright

IF a soft check fails outright, THEN the application SHALL NOT accept the segment, whatever its confidence, SHALL record
a medium finding — `language` for the target-script or untranslated-echo check, `fluency` for repetition, `omission`
for the length ratio, `glossary` for glossary compliance — and SHALL send the segment to a directed fix; except that
IF the failed check is the untranslated echo and the source's display text, after any removal of names kept by policy,
has fewer than 20 code points, THEN the failure SHALL only add its 0.0 margin to confidence and record a low `language`
finding.

**Source:** FR-QA-07, FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `#qa-thresholds`, ADR-0038.
In plain words: a check that fails is a concrete defect — a copied source, the wrong alphabet, half a sentence
missing — and a high score on the other checks must not carry it into the book; confidence decides only the close
calls, where every check passed. Short lines are the exception for the echo check alone: a name, a Roman numeral or
`OK` legitimately reads the same in both languages, so below 20 code points an echo lowers confidence instead of
blocking. The reference clauses that accepted on confidence alone are edited in this change.

#### Scenario: A copied source is repaired, then flagged, even above τ

- **WHEN** Unattended (τ = 0.60), Fast (N = 1), English → Ukrainian, and the draft for `He opened the old door.` is
  `He opened the old door.`, with confidence 0.65
- **THEN** the segment is not accepted and carries medium `language` findings for the echo and the script checks
- **AND** its one round is a directed fix, which returns `He opened the old door.` again, and the segment is FLAGGED

#### Scenario: A Latin-letter translation is flagged

- **WHEN** Assisted (τ = 0.75), Fast (N = 1), and both the draft and the directed fix for `He opened the old door.`
  return `Vin vidchynyv stari dveri.`, with confidence 0.80
- **THEN** the segment is FLAGGED with a medium `language` finding from the script check

#### Scenario: A half-sentence omission is flagged

- **WHEN** Assisted (τ = 0.75), Fast (N = 1), and both the draft and the directed fix for
  `He opened the old door and walked into the dark hall.` return `Він відчинив старі двері.` (ratio 0.47), with
  confidence 0.75
- **THEN** the segment is FLAGGED with a medium `omission` finding from the length-ratio check

#### Scenario: A short echo lowers confidence without blocking

- **WHEN** English → Ukrainian and the target for `Yes, sir.` (9 code points) is `YES, SIR.`
- **THEN** the echo check fails with a low `language` finding, the script check is skipped, and confidence is
  0.30 + 0.25 + 0.20 + 0.0 + 0.10 = `0.85`
- **AND** with the judge off the segment is accepted in Unattended, Assisted and Manual

#### Scenario: The echo floor is 20 code points

- **WHEN** English → Ukrainian and the target for `It was a dark night.` (exactly 20 code points) is
  `IT WAS A DARK NIGHT.`
- **THEN** the failed echo blocks acceptance and records a medium `language` finding

#### Scenario: The pseudo model's echo is flagged in every mode

- **WHEN** the pseudo model translates `He opened the old door.` (23 code points) from English to Ukrainian on Balanced
- **THEN** its draft `HE OPENED THE OLD DOOR.` fails the echo and script checks, each of its 2 directed fixes returns
  the same text, and the segment is FLAGGED
- **AND** this holds in Unattended, Assisted and Manual alike

### Requirement: Take the trust threshold from the review mode alone

The application SHALL set the trust threshold τ to 0.60 in Unattended, 0.75 in Assisted and 0.85 in Manual review
mode, SHALL use the same value as the judge threshold τ_judge, and SHALL NOT let the quality dial change either.

**Source:** FR-REVIEW-02, FR-QA-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`,
`#fr-qa`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`, ADR-0036.
In plain words: how much a person wants to check is a single choice — the review mode — and it alone decides how
confident a segment must be; the quality dial buys more careful mechanics but never lowers or raises the bar.

#### Scenario: One confidence, three modes

- **WHEN** a segment passes its hard gates and every soft check, with a script margin of 0.5, a length margin of 0.2
  and a confidence of `0.70`, and the judge is off
- **THEN** it is accepted in Unattended (τ = 0.60)
- **AND** it is not accepted in Assisted (τ = 0.75) or Manual (τ = 0.85)

#### Scenario: The quality dial leaves τ alone

- **WHEN** the review mode is Unattended and the quality dial is Max
- **THEN** τ is `0.60` and τ_judge is `0.60`

### Requirement: Judge each chunk once when the quality dial enables the judge

WHERE the quality dial is Balanced or Max, the application SHALL send the model, in one call per chunk, the source and
target of each segment of the chunk that passed its hard gates and was not reused from memory, labelled `s1` to `sk` in
document order, and SHALL read back an overall score between 0 and 1, an advisory verdict, findings naming a label
with a type of meaning, omission, fluency, glossary, language or tag and a severity of low, medium or high, and
deferrals; it SHALL make no judge call for a chunk with no such segment; WHERE the dial is Fast, it SHALL make no judge
call.

**Source:** FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#judge-quality-evaluation`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, ADR-0038.
In plain words: one judge call per chunk keeps the cost of Balanced tolerable and lets the judge see neighbouring
sentences; short local labels are easier for a small model to keep straight than segment ids. A segment whose markup
already failed is going to repair anyway, and a reuse from memory is decided without the judge (see "Accept a
context-matched memory reuse without the judge"), so neither is shown to it. Fast trades the judge away for speed.

#### Scenario: A Balanced chunk of three segments is judged once

- **WHEN** the dial is Balanced and a chunk holds three drafted segments that all passed their hard gates
- **THEN** one judge call is made, showing the pairs labelled `s1`, `s2`, `s3`
- **AND** a reply `{"score":0.82,"verdict":"accept","findings":[{"segmentId":"s2","type":"omission","severity":"medium","note":"drops the second clause"}],"deferrals":[]}`
  is read as a score of 0.82 with one medium omission finding on the second segment

#### Scenario: Only pairs that passed their hard gates and were not reused are judged

- **WHEN** the dial is Balanced and the chunk `ch03.xhtml:10`–`ch03.xhtml:13` holds `ch03.xhtml:10`, whose placeholder
  gate still failed after its placeholder repair, and `ch03.xhtml:12`, reused from memory
- **THEN** the judge call shows `ch03.xhtml:11` as `s1` and `ch03.xhtml:13` as `s2`, and nothing else

#### Scenario: No judge call when no pair qualifies

- **WHEN** the dial is Balanced and both segments of a chunk are reused from memory
- **THEN** no judge call is made for that chunk

#### Scenario: Fast makes no judge call

- **WHEN** the dial is Fast and a chunk of eight segments is drafted
- **THEN** no judge call is made

#### Scenario: A reply without findings or deferrals is read

- **WHEN** the judge replies `{"score":0.91,"verdict":"accept"}`
- **THEN** it is read as a score of 0.91 with no findings and no deferrals

### Requirement: Flag a segment the judge could not judge

IF a judge call — a chunk's, or a repaired segment's re-judge — ends with `ErrorCode.timeout` after the provider's
own retries, THEN the system SHALL NOT pause the run for it: it SHALL decide every segment the call was for by its
quality checks alone, keep the segment's latest target that passed every hard gate (the draft, or the last repair), add
the finding `judge-unavailable` (severity medium, raised by `judge`), and flag the segment with that error, never accept
it; it SHALL make no further repair round for it, and the run SHALL go on with the next segment. A judge call answered
with any other provider error — a provider outage (`unreachable`, `upstream`, `rateLimited`) included — still pauses
the run as the `resume` capability says, so an outage is waited out and the judge call made again, exactly as for a
draft call.

**Source:** FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`; tasks 15b.
In plain words: on the Bartimaeus hand test a re-judge after a directed fix hung seven times for the full timeout, and
each time the run paused, was resumed, and paid the same calls again. A judge that does not answer says nothing about
the translation, so the translation is kept and handed to the person for review instead of holding the whole book. A
provider that is down is another matter: overnight it would flag every segment judged while it was down, so the run
waits for it and judges again.

#### Scenario: A re-judge times out after a directed fix

- **WHEN** a Balanced run's chunk judge finds a medium `meaning` finding on `Book.md:0`, the directed fix answers
  `Старий чоловік повільно пішов до гавані.`, and the re-judge ends with `ErrorCode.timeout`, with pause on error
  enabled
- **THEN** the run does not pause, `Book.md:0` is FLAGGED with that fixed target and a `judge-unavailable` finding, and
  the run report lists it with `ErrorCode.timeout`
- **AND** `Book.md:1` is decided by the chunk's verdict and the run ends Completed

#### Scenario: A chunk's judge stalls

- **WHEN** a Balanced chunk of two drafted segments is judged and the judge call ends with `ErrorCode.timeout`
- **THEN** both segments are FLAGGED with their drafts as targets and no further call is made for them

#### Scenario: A provider outage during judging is waited out

- **WHEN** an Unattended Balanced run drafts `One.`, `Two.` and `Three.` and the chunk's judge call ends with
  `ErrorCode.unreachable` (or `upstream`, or `rateLimited`) while the provider is down for a minute
- **THEN** the run waits, probes at 0:15, 0:45 and 1:45, judges the chunk again once a probe passes, and ends Completed
  with all three ACCEPTED and none flagged

### Requirement: Stop repairing a segment that does not change

WHEN a self-heal round's rewrite is identical to the text it was asked to repair, or its re-judge repeats the
previous verdict's score and the same medium or high finding kinds for the segment, and the segment is not accepted,
THEN the system SHALL flag the segment at once with that round counted, and SHALL NOT spend the rest of the repair
budget on it.

**Source:** `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`; tasks 15b.
In plain words: on the hand test a directed fix returned the same 289-token rewrite three times and the judge repeated
the same `meaning` finding at 0.85 each time. A round that changes nothing will not be changed by the next one, so the
budget is not spent on it.

#### Scenario: Two fixes return the same text

- **WHEN** an echoed draft gets a first directed fix answering `HE OPENED THE OLD DOOR!`, a second answering the same,
  with three rounds allowed and the judge off
- **THEN** the segment is FLAGGED after 2 rounds and no third fix is sent

#### Scenario: The re-judge repeats the same finding at the same score

- **WHEN** the chunk judge scores 0.85 with a medium `meaning` finding, the directed fix answers a new target, and the
  re-judge again scores 0.85 with a medium `meaning` finding, with three rounds allowed
- **THEN** the segment is FLAGGED after 1 round with that fixed target, after 3 requests

### Requirement: Accept a segment only by the acceptance rule

The application SHALL accept a drafted segment only when its hard gates pass, no soft check failed outright (see
"Block acceptance when a soft check fails outright"), its confidence is at least τ, and either the judge is off or its
chunk's score is at least τ_judge with no medium or high finding against that segment; the verdict SHALL NOT decide,
and an unreadable judge reply SHALL leave every segment it judged unaccepted and routed to self-heal.

**Source:** FR-QA-07, FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md#judge-quality-evaluation`, ADR-0038.
In plain words: every condition must hold at once, so no single strong signal can carry a weak segment; a chunk's
score is shared, so a finding against one segment keeps that one out without dragging its clean neighbours with it. A
reuse from memory follows the same rule without the judge (see "Accept a context-matched memory reuse without the
judge").

#### Scenario: A finding on one segment keeps only that segment out

- **WHEN** Assisted (τ = 0.75), all three segments of a chunk pass their hard gates and every soft check with
  confidence `0.95`
- **AND** the chunk's judge score is `0.82` with a medium omission finding on `s2`
- **THEN** the first and third segments are accepted and the second goes to self-heal

#### Scenario: The score decides, not the verdict

- **WHEN** Assisted, a segment passes every check with confidence `0.95` and its chunk's reply is
  `{"score":0.70,"verdict":"accept"}`
- **THEN** the segment is not accepted
- **AND** a reply `{"score":0.80,"verdict":"revise"}` with no findings would accept it

#### Scenario: A low-severity finding does not block

- **WHEN** Assisted, the chunk score is `0.80` and the only finding on a segment is a low fluency finding
- **THEN** that segment is accepted

#### Scenario: An unreadable judge reply accepts nothing

- **WHEN** the dial is Balanced and the judge replies `Looks good to me!`
- **THEN** no judged segment of that chunk is accepted and each goes to self-heal

#### Scenario: A hard-gate failure is never accepted

- **WHEN** a segment's placeholder hard gate failed, so it was not shown to the judge
- **AND** the chunk's other segments were judged `0.95` with no finding
- **THEN** the segment is not accepted

#### Scenario: A failed soft check blocks a well-judged segment

- **WHEN** Assisted, Balanced, a chunk is judged `0.95` with no finding, and one of its segments failed its
  length-ratio check with confidence `0.75`
- **THEN** that segment is not accepted and goes to a directed fix

#### Scenario: A good but short translation is accepted

- **WHEN** the judge is off and a segment passes every check with a length margin of 0.5 and confidence `0.875`
- **THEN** it is accepted in Unattended, Assisted and Manual alike

### Requirement: Accept a context-matched memory reuse without the judge

WHEN a segment's target is reused from memory because its source and both neighbours match, the application SHALL
accept that target only when its hard gates pass, no soft check failed outright and its confidence is at least τ, and
SHALL make no judge call for it nor show it to its chunk's judge.

**Source:** FR-ALGO-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#context-aware-tm`, `#tiered-loop`, ADR-0038.
In plain words: a sentence already accepted in the very same surroundings needs no second opinion from the judge — it
is the one exception to the judge term of the acceptance rule — but it must still clear every deterministic check, so
a memory entry can never put a broken or since-forbidden target into the book. When the memory is looked up, and that a
reuse which is not accepted is discarded and the segment drafted instead, is the translation-pipeline capability's
"Reuse the translation memory only where its context matches"; a segment is recorded as reused from memory only when
its reuse is accepted.

#### Scenario: A context match is accepted without the judge

- **WHEN** Balanced, `ch03.xhtml:12` reads `Yes.` between the same two sentences that surrounded `ch01.xhtml:4`, which
  was accepted as `Так.`
- **THEN** `ch03.xhtml:12` is accepted as `Так.` with confidence 1.0, no draft or judge call is made for it, and it is
  recorded as reused from memory

#### Scenario: A reuse that fails a hard gate is drafted instead

- **WHEN** `ch01.xhtml:4` `Hale nodded.` was accepted as `Хейл кивнув.`, the person then locked `Hale` → `Гейл`, and
  `ch03.xhtml:12` reads `Hale nodded.` between the same neighbours
- **THEN** the reused target fails the protected-span hard gate and is not accepted
- **AND** `ch03.xhtml:12` is drafted instead, with `Hale` sent as a placeholder

### Requirement: Repair a failing segment within the dial's repair budget before flagging it

WHEN a drafted segment is not accepted, the application SHALL run up to N self-heal rounds — N being 1 on Fast, 2 on
Balanced and 3 on Max, counted only after the draft's one structural repair and one placeholder repair, which are not
rounds — each returning one target for that one segment: a directed fix when the segment has a concrete finding — a
failed hard gate, with the expected placeholder sequence stated when the placeholder or protected-span gate failed; a
soft check failed outright; or a medium or high judge finding — otherwise a reflect call then an improve call,
followed by a polish call only when the improved target passes its hard gates, fails no soft check and has a
confidence within 0.05 below τ; after N rounds without acceptance it SHALL mark the segment FLAGGED with every finding,
and a segment accepted after at least one round SHALL count as repaired and accepted.

WHERE the judge is on, WHEN a round's target passes its hard gates, fails no soft check and reaches τ, the application
SHALL judge that segment again on its own — one judge call holding only that source/target pair, labelled `s1` — and
SHALL decide it with that call's score and findings instead of its chunk's; a target short of any of these SHALL go to
the next round without a judge call.

**Source:** FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`),
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md#directed-fix-repair`, `#reflect-improve`, `#monolingual-polish`,
ADR-0038.
In plain words: most failures can be fixed by asking the model again with the problem named; a named problem gets a
targeted fix, a vague one gets a critique and a rewrite, and a near miss gets a final polish. The budget keeps a
stubborn segment from eating the run, and the repaired count shows how much the repairs are doing. The draft's own
two format repairs come first and never use up the budget — their order, and the replies that flag a segment at once,
are the translation-pipeline capability's "Repair invalid structured draft replies once" and "Flag a segment whose reply
cannot be used, and continue". A repaired target is new
text the chunk's judge never saw, so accepting it on the old score would skip the judge exactly where it matters;
judging it alone costs one short call and leaves the chunk's other decisions alone, and a target that already fails a
check is not worth that call.

#### Scenario: A placeholder failure gets a directed fix with the expected tokens

- **WHEN** Balanced, the source holds `⟦g0⟧`, `⟦g1⟧`, `⟦g2⟧`, and both the draft and its placeholder repair come back
  without `⟦g1⟧`
- **THEN** the first round is a directed fix that states the expected sequence `⟦g0⟧ ⟦g1⟧ ⟦g2⟧`
- **AND** its reply is one target for that segment

#### Scenario: The draft's format repairs are not rounds

- **WHEN** Fast (N = 1) and the draft, its placeholder repair and the directed fix all come back without `⟦g1⟧`
- **THEN** the segment is FLAGGED with a high `markup` finding and no machine translation, after exactly 3 model calls
- **AND** the directed fix was made although the placeholder repair had already been used

#### Scenario: A low score with no finding goes to reflect and improve

- **WHEN** Assisted, Balanced, a segment passed every check and its chunk scored `0.60` with no finding on it
- **THEN** the round is a reflect call followed by an improve call

#### Scenario: A near miss is polished

- **WHEN** Assisted (τ = 0.75) and after improve the segment passes its hard gates and every soft check with
  confidence `0.72`
- **THEN** a polish call is made
- **AND** with confidence `0.68`, or with confidence `0.72` and a failed length-ratio check, no polish call is made

#### Scenario: The budget runs out

- **WHEN** Fast (N = 1) and the segment still fails after its one round
- **THEN** it is marked FLAGGED

#### Scenario: A repaired segment is judged again on its own

- **WHEN** Assisted (τ = 0.75), Balanced, the chunk `Book.md:0`–`Book.md:3` scored `0.90` with a medium `omission`
  finding on `s2`, and the directed fix for `Book.md:1` returns `Вона пройшла з Гейлом до темної зали.`, passing every
  check at full margin (confidence `1.0`)
- **THEN** one judge call is made holding only that pair, labelled `s1`
- **AND** when it answers `{"score":0.9,"verdict":"accept"}` with no finding, `Book.md:1` is accepted as repaired

#### Scenario: A repair that still fails is not judged

- **WHEN** Unattended (τ = 0.60), Balanced, and the directed fix for `Book.md:3` returns its English source
  `He left the house at dawn.` (26 code points), which fails the untranslated-echo check outright with confidence 0.65
- **THEN** no judge call is made for it and the next round begins

#### Scenario: A repaired segment is counted

- **WHEN** Max and the segment is accepted in its second round
- **THEN** it is accepted and counted as repaired and accepted, not as auto-accepted

### Requirement: Record each segment's findings for review and repair

The application SHALL record for every decided segment its confidence, its chunk's judge score when a judge ran, and
each finding with the check or judge that raised it, its type, its severity and a short note, and SHALL keep the
findings of a FLAGGED segment for the review panel and for any later retry.

**Source:** FR-QA-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`),
`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`.
In plain words: a directed fix needs to know exactly what was wrong, and so does the person reviewing a flagged
segment; a flag with no reason would leave them guessing.

#### Scenario: A flagged segment keeps its reasons

- **WHEN** segment `ch5 · p12` is flagged after its length ratio of `0.41` fell outside `0.7–1.8` and the judge
  reported a high omission finding with score `0.58`
- **THEN** its record carries confidence, the judge score `0.58`, a medium `omission` finding from the length-ratio
  check and a high `omission` finding from the judge

#### Scenario: An accepted segment keeps a low finding

- **WHEN** a segment is accepted with a low fluency finding from the judge
- **THEN** its record carries that low fluency finding
