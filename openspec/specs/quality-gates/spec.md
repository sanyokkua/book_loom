# quality-gates Specification

## Purpose
The gate between a draft and an accepted translation: the hard gates a translated segment can never be accepted
without, the soft checks that blend into a deterministic confidence, the trust threshold each review mode sets, the
per-chunk model reviewer that fixes in place with edits the code verifies, and the self-heal rounds that repair a failing segment before it is flagged for review.

## Requirements

### Requirement: Treat placeholder integrity, including the order of paired placeholders, as a hard gate

IF a translated segment's markup does not restore — its placeholder tokens differ from its source's as a multiset, a
paired placeholder comes back closing-before-opening or improperly nested with another pair, or the target breaks a
pair rule the document round trip checks — THEN the application SHALL fail its placeholder hard gate, record a high
`markup` finding, and SHALL NOT accept the segment, whatever its confidence or the reviewer's answer.

**Source**: FR-QA-04, FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, ADR-0040, ADR-0038. In plain words: the placeholder check the document round trip performs before restoring markup (see the document-round-trip capability, which also owns the two pair rules: a pair that held text must still hold text, and a line break stays inside the same pair) is the first quality gate; a pair that comes back crossed would restore broken markup and fail the whole export at the very end, so it is caught here for one segment instead. An atomic placeholder — an image, a line break, a locked term — may move, because word order legitimately changes. A draft that fails this gate first gets its one placeholder repair (the translation-pipeline capability's "Repair invalid structured draft replies once"); only a segment still failing after it reaches self-heal.

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
SHALL fail that segment's protected-span hard gate, record a high finding, and SHALL NOT accept it.

**Source**: FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), EC-FOREIGN-3 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, ADR-0037. In plain words: a locked name (see the glossary capability's "Render a locked term exactly as entered") and a foreign phrase the book keeps verbatim (see "Keep an inline foreign run verbatim under the Keep as-is policy") both reach the model as one placeholder, so the model can neither misspell the name nor translate the phrase. A reply that drops or duplicates such a placeholder would lose or double the name or the phrase, so it can never pass. Like any placeholder, a protected one may move.

#### Scenario: A dropped locked term fails

- **WHEN** `Hale` is locked as `Гейл` and the segment `Hale opened the door.` is sent as `⟦g0⟧ opened the door.`
- **AND** the model returns `Він відчинив двері.`
- **THEN** the protected-span hard gate fails with a high `glossary` finding and the segment is not accepted

#### Scenario: A returned locked term passes and is restored

- **WHEN** the same segment's reply is `⟦g0⟧ відчинив двері.`
- **THEN** the protected-span hard gate passes and the target reads `Гейл відчинив двері.`

#### Scenario: A repeated kept foreign run fails

- **WHEN** the policy is `Keep as-is` and `She whispered <i xml:lang="fr">au revoir</i> and left.` is sent as
  `She whispered ⟦g2⟧ and left.`
- **AND** the model returns `Вона прошепотіла ⟦g2⟧ і пішла ⟦g2⟧.`
- **THEN** the protected-span hard gate fails with a high `markup` finding and the segment is not accepted

### Requirement: Hide the longest overlapping locked term first

WHERE locked terms overlap in a segment, the application SHALL hide the longest match first, so a shorter locked term
inside a longer one is not hidden separately.

**Source**: FR-GLOSS-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, ADR-0037. In plain words: when one locked term contains another, the longer wins, so `Baker Street` is never split into a placeholder for `Baker` followed by the word `Street`.

#### Scenario: The longest locked term is hidden first

- **WHEN** `Baker Street` and `Baker` are both locked and the segment is `Baker Street was quiet; Baker left.`
- **THEN** the model receives `⟦g0⟧ was quiet; ⟦g1⟧ left.`

### Requirement: Keep an inline foreign run verbatim under the Keep as-is policy

WHILE the Book Brief's foreign-passage policy is `Keep as-is`, the application SHALL hide each inline element whose own
`lang` or `xml:lang` differs from the brief's source language (both tags normalized), together with its text, behind one
placeholder before the segment reaches the model, SHALL restore it verbatim, and SHALL decide which runs are foreign
from those declarations alone, for any language the application recognizes.

**Source**: EC-FOREIGN-3 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), DD-26 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), ADR-0037. In plain words: a French farewell inside an English sentence must stay French when the person chose to keep foreign passages, and the surest way is to never show it to the model. Only a run the book itself marks with another language is kept: the application does not detect languages from text (ADR-0037), and a region variant of the source language is not foreign.

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

#### Scenario: A marked French run inside a paragraph is still one placeholder

- **WHEN** the policy is `Keep as-is` and the paragraph is `C'était <span lang="fr">très bien</span>, he said.` in an
  English book
- **THEN** the model receives the paragraph with one placeholder for the French run and translates the prose around it

#### Scenario: A run is foreign only by its declaration

- **WHEN** the policy is `Keep as-is`, the source language is `en`, and the paragraph is `She said bonjour and left.`
  with no language attribute on `bonjour`
- **THEN** the model receives `She said bonjour and left.` with no placeholder for `bonjour`

### Requirement: Keep a block in another declared language verbatim under the Keep as-is policy

WHILE the policy is `Keep as-is`, the application SHALL hide a whole block whose declared language — its own, or an
ancestor's below the document root — differs, after normalizing all three tags, from both the brief's source language
and the book's declared language, behind one placeholder, SHALL make no model call for it, and SHALL keep it as it is
(counted as kept as is, path verbatim).

**Source**: EC-FOREIGN-3 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), DD-26 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), ADR-0037. In plain words: the fixture book's `<p xml:lang="la">Gravitas omnia trahit, sed nemo videt.</p>` was sent to the model under Keep as-is, translated and reviewed with nothing to edit, because only inline runs were hidden and the prompt's "keep it verbatim" line was not enough for a small model. A marked block is now never sent at all. Comparing with the book's own declared language too keeps a brief whose source was set to another language from leaving every paragraph under a `<body xml:lang="en">` untranslated; a block declaring the book's own language is never foreign.

#### Scenario: A marked Latin paragraph is kept without a model call

- **WHEN** the policy is `Keep as-is`, the source language and the book's declared language are `en`, and the block is
  `<p xml:lang="la">Gravitas omnia trahit, sed nemo videt.</p>`
- **THEN** no draft call is sent for it and its record is accepted on the verbatim path with the target
  `Gravitas omnia trahit, sed nemo videt.`

#### Scenario: A block declaring the book's own language is not foreign

- **WHEN** the policy is `Keep as-is`, the brief's source is `de`, the book declares `en`, and a block declares `en`
- **THEN** the block is shown to the model as it is

### Requirement: Translate a marked foreign passage under the other two policies

WHERE the foreign-passage policy is `Translate` or `Translate with a note`, the application SHALL draft a marked
foreign run or block like any other text, and under `Translate with a note` SHALL tell the model to add the original
wording in parentheses after the translation.

**Source**: EC-FOREIGN-3 (`docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), DD-26 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), ADR-0037. In plain words: under the other two policies the foreign run is translated like the rest of the sentence; the second adds the original in parentheses so the reader can see what was there.

#### Scenario: Translate drafts a marked paragraph

- **WHEN** the policy is `Translate` and the block is `<p xml:lang="la">Gravitas omnia trahit, sed nemo videt.</p>`
- **THEN** the block is drafted like any other paragraph

#### Scenario: Translate with a note asks for the original in parentheses

- **WHEN** the policy is `Translate with a note` and the same block is drafted
- **THEN** the draft's system message tells the model to add the original wording in parentheses right after the
  translation

#### Scenario: The Translate policy translates a marked run

- **WHEN** the policy is `Translate` and the paragraph is `She whispered <i xml:lang="fr">au revoir</i> and left.`
- **THEN** the model receives `She whispered ⟦g0⟧au revoir⟦g1⟧ and left.`

### Requirement: Treat a refusal or an empty target as a hard gate

IF a translated target's display text — its text with every placeholder removed and whitespace collapsed — is empty
while its source's is not, or the target begins with a phrase that talks about the translation task or the model while
the source's display text does not begin with such a phrase, THEN the application SHALL fail that segment's refusal
hard gate, record a high `meaning` finding, and SHALL NOT accept it.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`. In plain words: a model that declines, apologises or talks about the text instead of translating it has produced nothing a reader can use; letting it through would put the apology into the book. Only phrases about the task or the model count — the full list per language is in `05_PIPELINE_ENGINE.md#qa-thresholds` — so a character's own apology is translated, not refused, and a book whose own line begins `Translation:` keeps its translated `Переклад:`. A reply that holds only its placeholders would silently delete the paragraph's words. A reply with no text at all, or a blank `target`, is not this gate's case: the translation-pipeline capability flags or repairs it before any check. A segment with nothing to translate — no letter, a Roman numeral or a single character — never reaches this gate: it is kept as it is with no model call (`translation-pipeline` "Keep a segment with nothing to translate as it is"), so an empty reply to the chapter number `2` can no longer flag it.

#### Scenario: An English refusal fails

- **WHEN** the source is `He opened the old door.` and the target is `I'm sorry, but I can't translate this text.`
- **THEN** the refusal hard gate fails with a high `meaning` finding

#### Scenario: A refusal in the target language fails

- **WHEN** the target language is Ukrainian and the target is `Вибачте, я не можу перекласти цей текст.`
- **THEN** the refusal hard gate fails

#### Scenario: A preamble about the translation fails

- **WHEN** the target is `Ось переклад: Він відчинив старі двері.`
- **THEN** the refusal hard gate fails

#### Scenario: A phrase about the model or the task fails

- **WHEN** the target begins `I cannot translate this passage`, `As an AI I would say` or
  `Here is the translation: Він відчинив старі двері.`
- **THEN** the refusal hard gate fails

#### Scenario: A character's own apology passes

- **WHEN** the source is `Sorry, I can't go with you.` and the target is `Вибачте, я не можу піти з вами.`
- **THEN** the refusal hard gate passes

#### Scenario: The book's own heading is not a refusal

- **WHEN** the source is `Translation: the art of carrying meaning.` and the target is
  `Переклад: мистецтво перенесення змісту.`
- **THEN** the refusal hard gate passes

#### Scenario: A reply of placeholders only fails

- **WHEN** the masked source is `He opened the ⟦g0⟧old⟦g1⟧ door.` and the target is `⟦g0⟧⟦g1⟧`
- **THEN** the refusal hard gate fails, because the target's display text is empty while the source's is not
- **AND** the segment goes to self-heal

### Requirement: Match a refusal phrase only as whole words, in any of three languages

The application SHALL compare the target's start with the refusal phrases trimmed, without regard to case, with a
typographic apostrophe read as `'`, in English, the source language and the target language, and SHALL count a phrase
only when it is followed by the end of the text or by a character that is neither a letter nor a digit.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`. In plain words: a phrase must end where a word ends, so `Як ШІ` never catches `Як шість`, and a refusal written with a curly apostrophe is still a refusal.

#### Scenario: A typographic apostrophe does not hide a refusal

- **WHEN** the target is `I’m sorry, but I can’t translate this.`, written with U+2019 apostrophes
- **THEN** the refusal hard gate fails

#### Scenario: A phrase is matched only as whole words

- **WHEN** the target is `Як шість років тому він пішов.` or `As an aide, she opened the door.`
- **THEN** the refusal hard gate passes

### Requirement: Check the target script for pairs whose languages use different scripts

WHEN the source's display text has at least 20 code points, the source and target languages use different scripts,
and the target has letters, the application SHALL fail the target-script check when fewer than 0.60 of the target's
letters are written in the target language's script, and otherwise SHALL pass it with a margin of
(share − 0.60) / 0.20, at most 1.0.

**Source**: FR-QA-01, FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`, ADR-0037. In plain words: with no language detector, the only reliable signal is the alphabet — a Ukrainian translation written in Latin letters is plainly wrong. Serbian is catalogued as Cyrillic, so Latin-script Serbian output fails.

#### Scenario: A Cyrillic target for English source passes

- **WHEN** English → Ukrainian, the source is `He opened the old door.` (23 code points)
- **AND** the target is `Він відчинив старі двері.`
- **THEN** the target-script check passes with a margin of 1.0

#### Scenario: A Latin target for a Ukrainian book fails

- **WHEN** English → Ukrainian and the target for `He opened the old door.` is `Vin vidchynyv stari dveri.`
- **THEN** the target-script check fails, since 0.0 of its letters are Cyrillic

### Requirement: Bound what the target-script check reads and skips

The application SHALL count a source language whose script it does not know as using a different script, SHALL skip
the target-script check, counting a margin of 1.0, when the source's display text has fewer than 20 code points, when
the two languages share a script, when the target has no letter, or when the target language's script is not known, and
SHALL never count protected spans toward the share, nor, under the `Keep original` name policy, any
whole-word occurrence of a glossary term.

**Source**: FR-QA-01, FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`, ADR-0037. In plain words: the check cannot tell Polish from Czech, so a same-script pair skips it and a wrong language in the same script is left to the reviewer and to review; short fragments skip it so a name or an exclamation is not misjudged. Names the person chose to keep in their original spelling are Latin by design, so they are not held against a Cyrillic line.

#### Scenario: A same-script pair skips the check

- **WHEN** English → Polish and the target is `Otworzył stare drzwi.`
- **THEN** the target-script check is skipped and contributes a margin of 1.0

#### Scenario: A same-script wrong language is not caught

- **WHEN** English → Polish and the model answers in Czech with `Otevřel staré dveře.`
- **THEN** the target-script check is skipped and does not fail the segment

#### Scenario: A target language whose script is not known skips the check

- **WHEN** English → Latin (`la`) and the source is `He opened the old door.` (23 code points)
- **THEN** the target-script check is skipped and contributes a margin of 1.0

#### Scenario: A source language whose script is not known counts as a different script

- **WHEN** Latin (`la`) → Ukrainian, the source is `Aperuit ianuam veterem.` (23 code points) and the target is
  `Vin vidchynyv stari dveri.`
- **THEN** the target-script check applies and fails

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
(0.90 − similarity) / 0.10, at most 1.0.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a model that copies the source back has translated nothing; comparing lower-cased text stops a copy in capitals from slipping past. Whether a failed echo blocks the segment depends on the source's length (see "Block acceptance when a soft check fails outright").

#### Scenario: A verbatim copy fails

- **WHEN** the source is `He opened the old door.` and the target is `He opened the old door.`
- **THEN** the untranslated-echo check fails (similarity 1.0)

#### Scenario: A copy in capitals still fails

- **WHEN** the target is `HE OPENED THE OLD DOOR.`
- **THEN** the untranslated-echo check fails (similarity 1.0)

#### Scenario: A real translation passes

- **WHEN** the target is `Він відчинив старі двері.`
- **THEN** the untranslated-echo check passes with a margin of 1.0 (similarity 0.12)

### Requirement: Leave glossary names out of the echo check

Under the `Keep original` name policy, the application SHALL remove every whole-word occurrence of a glossary term from
both texts before comparing them. IF the source's display text has fewer than 20 code points and, with every
whole-word occurrence of a glossary term removed under any name policy, holds no letter, THEN the application SHALL
skip the echo check: the glossary explains the echo.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a line made only of names the person chose to keep is meant to read like its source, so the names are taken out before the comparison. A short line that is only glossary names, such as `Bartimaeus!`, is explained by the glossary whatever the policy — how a name is rendered is the glossary check's question, not the echo's.

#### Scenario: A names-only line kept by policy is not an echo

- **WHEN** English → Ukrainian, the name policy is `Keep original`, the glossary holds `Margaret Hale` and
  `Milton Northern`, and `Margaret Hale, Milton Northern.` comes back unchanged
- **THEN** what remains after removing the names holds no letter, so the untranslated-echo check is skipped
- **AND** with the reviewer off the segment is accepted in Assisted with confidence 1.0

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

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: small local models sometimes get stuck repeating a phrase until the output limit; this catches that decode loop without punishing ordinary repetition in prose, while a phrase said twice over lowers confidence a little.

#### Scenario: Three repeats in a row fail

- **WHEN** the target is `двері відчинилися знову двері відчинилися знову двері відчинилися знову`
- **THEN** the repetition check fails

#### Scenario: Two repeats pass

- **WHEN** the target is `двері відчинилися знову двері відчинилися знову і він увійшов`
- **THEN** the repetition check passes with a margin of 0.5

### Requirement: Check the length ratio against the band of the language pair

The application SHALL fail the length-ratio check when target length divided by source length, in display-text
characters, falls outside the band of the pair's script class, and otherwise SHALL pass it with a margin of the
distance to the nearer bound divided by one tenth of the band's width, at most 1.0.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a translation far shorter than its source has usually dropped content, and one far longer has usually added some; the band differs by writing system because Chinese is naturally much shorter than English. A ratio just inside the band passes, but with less confidence than one comfortably inside it.

#### Scenario: Each script class has its band

- **WHEN** the source has 60 characters or more and the ratio is 1.5
- **THEN** the check passes for Latin → Cyrillic (band 0.7–1.8), for the same script (band 0.6–1.7) and for CJK → Latin
  (band 1.0–5.0)
- **AND** it fails for Latin → CJK (band 0.2–1.0)

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

#### Scenario: An unlisted pair uses the default band

- **WHEN** Ukrainian → Greek and the ratio is 2.3
- **THEN** the band 0.5–2.5 applies and the length-ratio check passes

### Requirement: Widen the length-ratio band for a short source

WHEN the source is shorter than 25 characters, the application SHALL halve the band's lower bound and double its upper
bound; otherwise, WHEN it is shorter than 60 characters, it SHALL set the lower bound at 0.85 times and the upper
bound at 1.25 times the band's.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: very short segments get a wider band because one word more or less changes their ratio a lot; a verse line Ukrainian says in half the words keeps its length (`it has been falling night and day` → `вона падає ніч і день`, 0.64, is no omission).

#### Scenario: A short source uses the widened band

- **WHEN** English → Ukrainian, the source is `Go.` (3 characters) and the target is `Іди-но звідси.` (14 characters, ratio 4.67)
- **THEN** the band is 0.35–3.6 and the length-ratio check fails
- **AND** the target `Йди.` (ratio 1.33) passes

#### Scenario: A source under 60 characters uses the narrower widening

- **WHEN** English → Ukrainian and the source has 40 characters
- **THEN** the band is 0.595–2.25, which is 0.7 × 0.85 and 1.8 × 1.25

### Requirement: Fail the length-ratio check when words look missing inside the band

WHILE the ratio is inside the band, the application SHALL still fail the check when the target has more
letter-word-followed-by-space-then-full-stop-or-comma spots (`помогою .`) than the source, or — for a source of at
least 8 words, neither text in a script written without spaces — when the target has fewer than 0.55 words per source
word while its character ratio is under 0.85, and its finding SHALL name which of the two it saw.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a dropped name or phrase can leave the character count inside the band, so the space left before a full stop and a word count far below the source's are read as well; 0.55 sits well below any real pair's word ratio (English to Ukrainian runs about 0.8), and a verse line Ukrainian says in half the words keeps its length (`A stone let go will find the ground,` → `Камінь, відпущений, знайде землю,`), so a faithful translation is not caught by it.

#### Scenario: A space left before a full stop fails

- **WHEN** English → Ukrainian, the source is `We measured it with the help of the old brass pendulum and Nell.` and the
  target `Ми виміряли це за допомогою старого латунного маятника та .`
- **THEN** the length-ratio check fails with a finding saying a word is missing before a full stop or comma

### Requirement: Trust a compact short line below the length-ratio band

WHEN a source line is under 60 characters and at most 10 words and its target keeps the words *Bound the compact
reading of a longer short line* asks (at least half of them for a line under 40 characters and of at most 8 words), the
application SHALL NOT fail the length-ratio check for a ratio below the band's lower bound, nor, when the source is past
40 characters or 8 words, for the word-count omission rule; a ratio above the upper bound, and a space left before a
full stop, SHALL still fail.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a compact line such as `Unfortunately, nothing happened.` → `На жаль, нічого.` (0.50) is complete in half the characters because Ukrainian needs fewer function words; one real run flagged 14 such lines as omissions, so below the lower bound a compact line is trusted as long as it keeps half its words.

#### Scenario: A compact line is complete in half the characters

- **WHEN** English → Ukrainian, the source is `Unfortunately, nothing happened.` and the target is `На жаль, нічого.` (ratio 0.50)
- **THEN** the length-ratio check does not fail it

#### Scenario: A line that lost most of its words is still an omission

- **WHEN** the source is `The monster met me at midnight.` and the target is `Чудовисько тут`
- **THEN** the length-ratio check fails, since the target keeps 2 of 6 words

#### Scenario: A compact line with a space before a full stop still fails

- **WHEN** the source is `Unfortunately, nothing happened.` and the target is `На жаль, нічого .`
- **THEN** the length-ratio check fails with a finding saying a word is missing before a full stop or comma

### Requirement: Bound the compact reading of a longer short line

WHEN a source line has 40 characters or more, or more than 8 words, the application SHALL treat it as compact only
while its target keeps at least 0.40 of the source's characters and either half of its words or three words and 0.30
of its words.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: below the lower bound a compact line is trusted as long as it keeps half its words (or, past 40 characters, three words and a ratio of 0.40). Known tradeoff (2026-10-07 review): the extended range also passes a real omission, and the six short-line cases of the real run (10 words to 3 or 4) were the reason for it; on the Fast dial no reviewer is there to catch such a line.

#### Scenario: A longer line kept in three words is complete

- **WHEN** the source is `Then, without a word, the old man left the room.` and the target is `Старий мовчки вийшов.`
- **THEN** the length-ratio check does not fail it

#### Scenario: The extended range also passes a real omission

- **WHEN** the source is `He looked at her for a long time, then left.` and the target is `Він подивився на неї.`
  (44 characters of 10 words kept as 4, ratio 0.48)
- **THEN** the length-ratio check does not fail it, although part of the sentence is missing, and on the Fast dial no
  reviewer is there to catch it

### Requirement: Measure glossary compliance over the locked terms in the segment

The application SHALL pass glossary compliance, with a margin of 1.0, when every locked glossary term occurring in the
source segment appears in the target as its entered rendering, SHALL fail it otherwise, and SHALL skip it, counting
1.0, when the segment contains no locked term.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), FR-GLOSS-03 (`#fr-gloss`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a name the person locked must read exactly as they entered it; unlocked entries are only guidance for the model and do not lower the score. Because every locked term travels as a placeholder and comes back as its rendering, this check passes whenever the protected-span hard gate did; it stays in the blend because the reference weights include it.

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
target-script checks, each counting 1.0, for a segment marked foreign, and SHALL apply both checks to every unmarked
segment.

**Source**: FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds` (foreign-keep vs echo), ADR-0037. In plain words: a French line the author left untranslated in an English novel must stay French when the person chose to keep foreign passages, and that is not a failure; but only a passage the book itself marks, or one in another alphabet, earns that pass — a segment that merely echoes its source is still caught.

#### Scenario: A marked French block is kept without failing

- **WHEN** the policy is `Keep as-is`, the brief's source language is `en`, and the block declares `lang="fr"`
- **AND** the target for `Je ne regrette rien.` is `Je ne regrette rien.`
- **THEN** the untranslated-echo and target-script checks are skipped and count 1.0

#### Scenario: An unmarked echo still fails

- **WHEN** the policy is `Keep as-is` and the unmarked English segment `He opened the old door.` returns unchanged
- **THEN** the untranslated-echo check fails

#### Scenario: The Translate policy applies the checks to a marked block

- **WHEN** the policy is `Translate` and the `lang="fr"` block returns `Je ne regrette rien.` unchanged
- **THEN** the untranslated-echo check fails

### Requirement: Mark a segment foreign by its declared language or its script

The application SHALL mark a segment foreign when the block it belongs to declares its own language and that language differs
from the brief's source language after both tags are normalized, or when its dominant script differs from the source
language's; the comparison SHALL apply to any language the application recognizes, not only a listed one.

**Source**: FR-QA-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds` (foreign-keep vs echo), ADR-0037. In plain words: the comparison is with the source language the person confirmed on the brief, not with what the book's package claims, so a book whose package declares the wrong language does not have every paragraph marked foreign.

#### Scenario: A Latin block in an English book is marked foreign

- **WHEN** the policy is `Keep as-is`, the brief's source language is `en`, and the block declares `lang="la"`
- **AND** the target for `Memento mori.` is `Memento mori.`
- **THEN** the segment is marked foreign, and the untranslated-echo and target-script checks are skipped and count 1.0

#### Scenario: A Greek-script segment in an English book is kept

- **WHEN** the policy is `Keep as-is` and the segment `Γνῶθι σεαυτόν` in an English book returns unchanged
- **THEN** the untranslated-echo and target-script checks are skipped

#### Scenario: The brief's source language decides, not the package's

- **WHEN** the policy is `Keep as-is`, an EPUB's package and `<html>` elements declare `uk`, its text is English, and the
  person set the source language to `en` on the brief
- **THEN** a `<p xml:lang="fr">` block is marked foreign
- **AND** a `<p>` that declares no language of its own is not marked, and its echo is checked

### Requirement: Blend the soft-check margins into a documented confidence

The application SHALL compute each segment's confidence as 0.30 × glossary + 0.25 × length ratio + 0.20 × target
script + 0.15 × untranslated echo + 0.10 × repetition, each term the check's margin between 0 and 1, a failed check
counting 0.0 and a skipped check 1.0, and SHALL NOT fold the hard gates or the reviewer's answer into it; the confidence only orders segments for review and never decides acceptance.

**Source**: FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#confidence`. In plain words: one number says how comfortably a segment clears the deterministic checks; hard gates stay pass/fail because no score can excuse broken markup, and the reviewer stays separate so a confident reviewer cannot hide a failed check; the number is a sort key for the review list, not a bar. Each check's margin is stated in its own requirement above.

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

#### Scenario: The reviewer's answer does not move confidence

- **WHEN** the English → Polish segment above receives a reviewer edit that is applied
- **THEN** its confidence is computed from the checks on the edited text alone, and the answer adds nothing to it

### Requirement: Block acceptance when a soft check fails outright

IF a soft check fails outright, THEN the application SHALL NOT accept the segment, whatever its confidence, SHALL record
a medium finding of the check's kind, and SHALL send the segment to a directed fix, except as "Let a short echo lower
confidence without blocking" says.

**Source**: FR-QA-07, FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `#qa-thresholds`, ADR-0038. In plain words: a check that fails is a concrete defect — a copied source, the wrong alphabet, half a sentence missing — and a high score on the other checks must not carry it into the book; confidence decides only the close calls no more: confidence orders segments for review. The reference clauses that accepted on confidence or on a judge score are edited in this change.

#### Scenario: A copied source is repaired, then flagged, whatever its confidence

- **WHEN** Fast (N = 1), English → Ukrainian, and the draft for `He opened the old door.` is
  `He opened the old door.`, with confidence 0.65
- **THEN** the segment is not accepted and carries medium `language` findings for the echo and the script checks
- **AND** its one round is a directed fix, which returns `He opened the old door.` again, and the segment is FLAGGED

#### Scenario: A Latin-letter translation is flagged

- **WHEN** Fast (N = 1), and both the draft and the directed fix for `He opened the old door.`
  return `Vin vidchynyv stari dveri.`, with confidence 0.80
- **THEN** the segment is FLAGGED with a medium `language` finding from the script check

#### Scenario: A half-sentence omission is flagged

- **WHEN** Fast (N = 1), and both the draft and the directed fix for
  `He opened the old door and walked into the dark hall.` return `Він відчинив старі двері.` (ratio 0.47), with
  confidence 0.75
- **THEN** the segment is FLAGGED with a medium `omission` finding from the length-ratio check

#### Scenario: Each failed check records the finding of its kind

- **WHEN** the target-script, untranslated-echo, repetition, length-ratio and glossary-compliance checks each fail
  outright
- **THEN** the findings are medium `language`, medium `language`, medium `fluency`, medium `omission` and medium
  `glossary` respectively

#### Scenario: The pseudo model's echo is flagged in every mode

- **WHEN** the pseudo model translates `He opened the old door.` (23 code points) from English to Ukrainian on Balanced
- **THEN** its draft `HE OPENED THE OLD DOOR.` fails the echo and script checks, each of its 2 directed fixes returns
  the same text, and the segment is FLAGGED
- **AND** this holds in Unattended, Assisted and Manual alike

### Requirement: Let a short echo lower confidence without blocking

IF the failed check is the untranslated echo and the source's display text, after any removal of names kept by policy,
has fewer than 20 code points, THEN the application SHALL NOT block the segment: the failure SHALL only add its 0.0
margin to confidence and record a low `language` finding.

**Source**: FR-QA-07, FR-QA-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `#qa-thresholds`, ADR-0038. In plain words: short lines are the exception for the echo check alone: a name, a Roman numeral or `OK` legitimately reads the same in both languages, so below 20 code points an echo lowers confidence instead of blocking.

#### Scenario: A short echo lowers confidence without blocking

- **WHEN** English → Ukrainian and the target for `Yes, sir.` (9 code points) is `YES, SIR.`
- **THEN** the echo check fails with a low `language` finding, the script check is skipped, and confidence is
  0.30 + 0.25 + 0.20 + 0.0 + 0.10 = `0.85`
- **AND** with the reviewer off the segment is accepted in Unattended, Assisted and Manual

#### Scenario: The echo floor is 20 code points

- **WHEN** English → Ukrainian and the target for `It was a dark night.` (exactly 20 code points) is
  `IT WAS A DARK NIGHT.`
- **THEN** the failed echo blocks acceptance and records a medium `language` finding

### Requirement: Let the review mode decide when the run pauses, never what is accepted

The application SHALL accept or flag a segment by the acceptance rule alone — hard gates, deterministic checks and
verified blockers — and SHALL NOT let the review mode, the quality dial or any confidence threshold move that decision;
the review mode SHALL decide only when the run pauses for the person.

**Source**: FR-REVIEW-02, FR-QA-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`, `#fr-qa`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, ADR-0036. In plain words: the earlier rule asked a segment to reach a confidence of 0.60, 0.75 or 0.85 by review mode, and a judge score besides. A score cannot tell a coined word from a rare one, so on a real book it flagged 170 decisions that a person found correct. What a person wants to check is one choice — the review mode — and it now alone decides when the run stops; whether a segment is accepted depends on facts the code can verify.

#### Scenario: A low confidence alone does not flag a segment

- **WHEN** a segment passes its hard gates and every soft check with a script margin of 0.5, a length margin of 0.2 and
  a confidence of `0.70`, and the reviewer answers `ok`
- **THEN** it is accepted in Unattended, Assisted and Manual alike

#### Scenario: The quality dial leaves acceptance alone

- **WHEN** the review mode is Unattended and the quality dial is Max
- **THEN** the same segment is accepted or flagged as it would be on Fast, by the checks and the verified edits

### Requirement: Review each chunk once per pass when the quality dial enables the reviewer

WHERE the quality dial is Balanced or Max, the application SHALL send the model, in one call per chunk and pass, the
source and the candidate of each segment of the chunk that passed its hard gates, failed no soft check outright and was
not reused from memory, labelled `s1` to `sk` in document order, together with the glossary renderings of the names in
the chunk and the language's reviewer checks, at temperature 0 with a fixed seed.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#reviewer-in-place-fixes`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, ADR-0038; tasks 15d.6. In plain words: a judge that scores a chunk cannot say which word is wrong, so a good sentence and a bad one fell below the same bar and the book filled with false alarms. A reviewer that quotes the word and offers the replacement can be checked by the code, which is the only reason to trust it. One call per chunk keeps the cost tolerable; short local labels are easier for a small model to keep straight than segment ids; each glossary rendering goes in as one `source → target` line.

#### Scenario: A Balanced chunk of three segments is reviewed once

- **WHEN** the dial is Balanced and a chunk holds three drafted segments that all passed their checks
- **THEN** one reviewer call is made at temperature 0, showing the pairs labelled `s1`, `s2`, `s3` and carrying the
  seed `15`
- **AND** a reply `{"results":[{"id":"s2","status":"edits","edits":[{"criterion":"omission","quote":"Він відчинив двері.","replacement":"Він відчинив двері й пішов."}]}]}`
  is read as one edit on the second segment and `ok` for the first and third

#### Scenario: Only pairs the checks do not already refuse are reviewed

- **WHEN** the dial is Balanced and the chunk `ch03.xhtml:10`–`ch03.xhtml:13` holds `ch03.xhtml:10`, whose placeholder
  gate still failed after its placeholder repair, and `ch03.xhtml:12`, reused from memory
- **THEN** the reviewer call shows `ch03.xhtml:11` as `s1` and `ch03.xhtml:13` as `s2`, and nothing else

### Requirement: Make no reviewer call when no pair qualifies or the dial is Fast

The application SHALL make no reviewer call for a chunk with no segment that qualifies for review, and WHERE the dial
is Fast it SHALL make none and decide by the deterministic checks alone.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, ADR-0038; tasks 15d.6. In plain words: a chunk where nothing is left to review costs no call, and Fast trades the reviewer away for speed.

#### Scenario: No reviewer call when no pair qualifies

- **WHEN** the dial is Balanced and both segments of a chunk are reused from memory
- **THEN** no reviewer call is made for that chunk

#### Scenario: Fast makes no reviewer call

- **WHEN** the dial is Fast and a chunk of eight segments is drafted
- **THEN** no reviewer call is made and each segment is decided by the checks alone

### Requirement: Read the reviewer's reply per label

The application SHALL read back per label `{"id","status","edits","rewrite"}` where `status` is `ok`, `edits` — a list
of `{"criterion","quote","replacement"}` find-and-replace edits, the default for a defect — or `rewrite`, allowed when
more than about 40% of the text must change or the structure or meaning is wrong throughout.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#reviewer-in-place-fixes`, ADR-0038; tasks 15d.6. In plain words: a reviewer that quotes the word and offers the replacement can be checked by the code; a rewrite is the exception for a segment wrong throughout.

#### Scenario: One edit is read for the second segment

- **WHEN** the reviewer replies `{"results":[{"id":"s2","status":"edits","edits":[{"criterion":"omission","quote":"Він відчинив двері.","replacement":"Він відчинив двері й пішов."}]}]}`
  for three pairs
- **THEN** it is read as one `omission` edit on `s2` and `ok` for `s1` and `s3`

### Requirement: Tolerate an incomplete reviewer reply

The application SHALL read a label the reply leaves out as `ok`, SHALL drop an answer whose status names nothing and an
edit with no quote or whose replacement equals its quote, and SHALL read a criterion that names nothing as `style`; the
criterion SHALL otherwise be one of meaning, omission, addition, terminology, gender, agreement, invented-word, quotes,
language, fluency or style.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, ADR-0038; tasks 15d.6. In plain words: short local labels are easier for a small model to keep straight than segment ids, and a small model often answers loosely, so a gap in the reply reads as "nothing to fix" and a half-formed edit is dropped rather than guessed at.

#### Scenario: A reply that names no segment reads as ok

- **WHEN** the reviewer replies `{"results":[]}`
- **THEN** every segment of the chunk is read as `ok`

#### Scenario: An unreadable reply is read as unreadable

- **WHEN** the reviewer replies `Looks good to me!`
- **THEN** the reply is unreadable and each segment it was for is handled as in "Flag a segment the reviewer could not
  review"

#### Scenario: An edit with no quote or no change is dropped

- **WHEN** the reviewer answers an edit with an empty quote and another whose replacement `двері` equals its quote
  `двері`
- **THEN** neither edit is kept and no edit is applied

#### Scenario: A criterion that names nothing reads as style

- **WHEN** the reviewer answers an edit whose criterion is empty
- **THEN** the edit reads as `style`

### Requirement: Send the reviewer a flat schema and resend a timed-out call without it

The schema sent SHALL be flat — no `maxItems`, `maxLength` or `additionalProperties` — and a structured call that ends
with `ErrorCode.timeout` SHALL be sent once more without its response format.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, ADR-0038; tasks 15d.6. In plain words: a small local model stalls on a constrained schema, so the schema stays plain and a stalled structured call is asked once more without it.

#### Scenario: The schema is flat

- **WHEN** a reviewer call is built
- **THEN** its schema holds no `maxItems`, no `maxLength` and no `additionalProperties`

#### Scenario: A structured call that times out is sent again without its schema

- **WHEN** a reviewer call ends with `ErrorCode.timeout`
- **THEN** the same request is sent once more with no `response_format` (OpenAI-compatible) or `format` (Ollama) and the
  same temperature and seed, and a readable reply to it is used

### Requirement: Review a Max chunk twice with a narrower second pass

WHERE the dial is Max, the application SHALL make a second pass over the same drafts with a narrower checklist (gender,
terminology, agreement) and SHALL apply that pass's edits after the first pass's.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#reviewer-in-place-fixes`, ADR-0038; tasks 15d.6. In plain words: Max spends a second, narrower look on the defects a small model most often misses.

#### Scenario: Max reviews the chunk twice

- **WHEN** the dial is Max and a chunk holds a draft whose first pass edit fixes a coined word and whose second pass
  edit fixes an agreement
- **THEN** two reviewer calls are made, the second saying it is a second pass, and both edits are applied in that order

### Requirement: Verify every reviewer edit in code before applying it

The application SHALL apply a reviewer edit only when its quote is a substring of the candidate that occurs exactly once
after normalisation (white space runs and typographic apostrophes), the replacement keeps the `⟦gN⟧` tokens of the whole
candidate in the same multiset and order, the deterministic checks and the placeholder gates pass on the edited text,
and the set of blocking checks does not grow.

**Source**: FR-QA-02, FR-QA-07, ADR-0038; tasks 15d.6. In plain words: a model may quote words that are not there, offer a replacement that breaks a placeholder, or "fix" one thing and break another. Because the code checks all of that, a wrong edit costs nothing and a right one is applied without asking the model again.

#### Scenario: A coined word with a quote is fixed

- **WHEN** the draft is `Він відчинив старі дверзі.` and the reviewer answers an `invented-word` edit with quote
  `дверзі` and replacement `двері`
- **THEN** the segment is ACCEPTED as `Він відчинив старі двері.` with one repair round and the edit kept on the record
  as a low finding raised by `reviewer-edit`

#### Scenario: A quote that occurs twice is refused

- **WHEN** an edit's quote occurs twice in the candidate
- **THEN** it is not applied and counts as refused

#### Scenario: A replacement that changes a token is refused

- **WHEN** an edit's quote holds `⟦g0⟧він⟦g1⟧` and its replacement is `вона`
- **THEN** it is not applied and counts as refused

#### Scenario: An edit that makes a check block is refused

- **WHEN** an edit's replacement mixes Latin and Cyrillic letters inside one word
- **THEN** the text fails `script-purity` and the edit counts as refused

### Requirement: Ignore a reviewer edit whose quote is missing or whose change does not fit its criterion

The application SHALL ignore an edit whose quote the candidate does not hold, and an edit whose change does not fit its
criterion, testing the fit before the quote's count so that an unfit edit is never an ambiguity.

**Source**: FR-QA-02, FR-QA-07, ADR-0038; tasks 15d.6. In plain words: a small model that finds nothing still fills a slot with a paraphrase under one of the criterion names, so an edit that does not do what its criterion says is dropped like a quote that is not there.

#### Scenario: A hallucinated quote is ignored

- **WHEN** the reviewer answers an edit whose quote `відімкнув ключем` is not in the candidate
- **THEN** the candidate is unchanged, no edit is recorded, no further call is made and the segment is accepted

#### Scenario: An edit that does not fit its criterion is ignored

- **WHEN** the reviewer answers one of: an `omission` edit `Він пішов.` → `Він ушів.` that adds no words, an `addition`
  edit `Він пішов додому.` → `Він пішов додому знову.` that removes none, a `quotes` edit `двері` → `дверцята` that
  touches no quote mark or bracket, a `language` edit `двері` → `дверка` that touches no letter of another script, a
  `terminology` edit `двері` → `портал` that uses no word the candidate or the glossary has, or a `meaning`, `terminology`, `gender` or
  `agreement` edit that deletes more than half of its quote, such as the `gender` edit `Вона втомилася і пішла` →
  `Вона`
- **THEN** the edit is ignored, even when its quote occurs twice in the candidate

#### Scenario: A reviewer's gender edit toward a suggested gender is a low note

- **WHEN** the glossary suggests `Chisel` as female, the sheet reads `Chisel — likely female`, the draft is
  `Чизел відчинив старі двері.` and the reviewer answers a `gender` edit changing `відчинив` to `відчинила`
- **THEN** the edit is not applied, one low `ignored-edit` finding is recorded, no directed fix is called and the draft
  stays; with the gender set by the person the same edit is applied

### Requirement: Apply reviewer edits in order and keep a refused one as evidence

The application SHALL apply edits one after another, each verified against the text the earlier ones left, dropping an
edit identical to an earlier one (the same criterion, quote and replacement, as the two Max passes can both write it),
and SHALL treat an edit whose quote was found but whose application was refused as evidence, never as an applied change.

**Source**: FR-QA-02, FR-QA-07, ADR-0038; tasks 15d.6. In plain words: later edits are checked against what the earlier ones left; fluency and style remarks are notes for the person and are never applied.

#### Scenario: A gender pair of edits is applied in order

- **WHEN** the reviewer answers two `gender` edits, `Вона втомився` → `Вона втомилася` and `йшов далі` → `йшла далі`
- **THEN** both are applied and the target is `Вона втомилася, але йшла далі.`

#### Scenario: An edit written by both Max passes is applied once

- **WHEN** both Max passes answer the same `gender` edit `Вона втомився` → `Вона втомилася`
- **THEN** it is applied once and the second copy is dropped

#### Scenario: A style remark is a note

- **WHEN** the reviewer answers an edit with the criterion `style` for an idiom
- **THEN** it is not applied, the segment is accepted with the draft's text and a low finding raised by `reviewer` keeps
  the remark

### Requirement: Send one directed fix for a refused reviewer edit

WHEN, after the applied edits, an edit remains whose quote was found but whose application was refused, the
application SHALL send one directed fix for that segment naming the criterion, the quote and the reviewer's
replacement.

**Source**: FR-QA-02, FR-QA-07, ADR-0038; tasks 15d.6. In plain words: when the reviewer is right that something is wrong but the code cannot apply its words, the model that wrote the draft gets one precise chance, with the exact quote. A call that fails during that one fix pauses and is sent again like any other call; the reviewer is not asked again.

#### Scenario: A refused edit gets one directed fix that names its quote

- **WHEN** the reviewer's `invented-word` edit for the quote `дверзі` mixes two alphabets and the directed fix answers
  `Він відчинив старі двері.`
- **THEN** exactly one directed fix is made, whose request names `дверзі` and `invented-word`
- **AND** the segment is ACCEPTED as `Він відчинив старі двері.`

#### Scenario: The directed fix fails with an error

- **WHEN** the directed fix call answers `ErrorCode.auth`
- **THEN** the step ends with that error, and the next decision for the segment repeats the fix without a new reviewer
  call

### Requirement: Count a refused edit's issue as resolved only when its defect is gone

The application SHALL count a refused edit's issue as resolved only when the fixed text passes the hard gates and checks, adds no
blocking check, and the edit's defect is gone from it — its quote is no longer there or, for an edit whose replacement
adds words around its quote (`двері` → `старі двері`), its replacement stands there.

**Source**: FR-QA-02, FR-QA-07, ADR-0038; tasks 15d.6. In plain words: a fix counts only when the thing the reviewer pointed at is really gone and nothing new is broken.

#### Scenario: A fix that removes the quote resolves the issue

- **WHEN** the `invented-word` edit for the quote `дверзі` is refused and the directed fix answers
  `Він відчинив старі двері.`
- **THEN** the issue is resolved, no verified blocker remains and the segment is ACCEPTED

### Requirement: Flag the segment when a refused edit's issue is not resolved

IF a refused edit's issue is not resolved, THEN the application SHALL treat it as a verified blocker and SHALL FLAG the
segment with it recorded as a medium finding raised by `reviewer`, keeping the text the applied edits left.

**Source**: FR-QA-02, FR-QA-07, ADR-0038; tasks 15d.6. In plain words: an issue the reviewer evidenced with a quote the code found, and that no edit or fix cleared, is the one thing besides the checks that keeps a segment out of the book.

#### Scenario: A directed fix that leaves the quote flags the segment

- **WHEN** the directed fix answers a text that still holds `дверзі`
- **THEN** the segment is FLAGGED with the draft as its target and a medium `invented-word` finding raised by
  `reviewer` that quotes `дверзі`

### Requirement: Take a reviewer's rewrite only when nothing breaks

A `rewrite` SHALL replace the draft only when the whole text passes the placeholder gates and every deterministic
check; otherwise the draft SHALL stay, the segment SHALL be FLAGGED and a medium `rewrite` finding SHALL say the draft
was kept.

**Source**: FR-QA-02, FR-QA-07, ADR-0038; tasks 15d.6. In plain words: a model that rewrites a whole sentence may break more than it fixes, so a rewrite is taken only when nothing breaks.

#### Scenario: A rewrite that passes every check is taken

- **WHEN** the reviewer answers `rewrite` with `Він відчинив старі дубові двері.`
- **THEN** the segment is ACCEPTED with that target

#### Scenario: A rewrite that breaks a check leaves the draft

- **WHEN** the reviewer answers `rewrite` with a text that mixes Latin and Cyrillic letters inside one word
- **THEN** the draft stays as the target, the segment is FLAGGED, and a medium `rewrite` finding says the draft was kept

### Requirement: Flag a segment the reviewer could not review

IF a reviewer call ends with `ErrorCode.timeout` after its retries and its resend, or its reply cannot be read, THEN
the system SHALL NOT pause the run for it: it SHALL keep each segment's draft, flag the segment with a medium
`reviewer-unavailable` finding raised by `reviewer` (with the timeout as its reason when the call timed out), accept
none of them, make no repair round for them, and go on with the next segment.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`; tasks 15b, 15d.6, 15e.7. In plain words: a reviewer that does not answer says nothing about the translation, so the translation is kept and handed to the person for review instead of holding the whole book.

#### Scenario: A chunk's reviewer stalls

- **WHEN** a Balanced chunk of two drafted segments is reviewed and the call and its resend both end with
  `ErrorCode.timeout`, with pause on error enabled
- **THEN** the run does not pause, both segments are FLAGGED with their drafts as targets and a `reviewer-unavailable`
  finding, and the run report lists them with `ErrorCode.timeout`

#### Scenario: A reply that cannot be read flags the segment

- **WHEN** the reviewer replies `Looks good to me!`
- **THEN** the segment is FLAGGED with a `reviewer-unavailable` finding and no flag reason

### Requirement: Wait out a provider outage during review

IF a reviewer call ends with a provider error other than `ErrorCode.timeout` — a provider outage (`unreachable`,
`upstream`, `rateLimited`) included — THEN the application SHALL pause the run as the `resume` capability says, so the
outage is waited out and the reviewer call made again, exactly as for a draft call.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`; tasks 15b, 15d.6, 15e.7. In plain words: a provider that is down is another matter than a reviewer that does not answer: overnight it would flag every segment reviewed while it was down, so the run waits for it and reviews again.

#### Scenario: A provider outage during review is waited out

- **WHEN** an Unattended Balanced run drafts `One.`, `Two.` and `Three.` and the chunk's reviewer call ends with
  `ErrorCode.unreachable` (or `upstream`, or `rateLimited`) while the provider is down for a minute
- **THEN** the run waits, probes at 0:15, 0:45 and 1:45, reviews the chunk again once a probe passes, and ends Completed
  with all three ACCEPTED and none flagged

### Requirement: Keep the first pass's answers when a second reviewer pass cannot be read

On Max, a second reviewer pass that cannot be read SHALL leave the first pass's answers in force.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`; tasks 15b, 15d.6, 15e.7. In plain words: what the first pass already found is not thrown away because the narrower second look failed to answer.

#### Scenario: An unreadable second pass keeps the first pass's edit

- **WHEN** on Max the first reviewer pass answers an `omission` edit for `s1` and the second pass replies
  `Looks good to me!`
- **THEN** the first pass's answers stay in force and its edit stays applied

### Requirement: Never flag a segment for a reviewer reply cut by the output cap

A reviewer reply that stopped with the finish reason `length` SHALL NEVER flag a segment: the system SHALL keep the
complete entries of the cut reply, ask about the pairs left unread once more in one smaller call of the same pass, and
take a pair still unread after that as `ok` with a warning in the log.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`; tasks 15b, 15d.6, 15e.7. In plain words: the deterministic checks have passed such a pair, and a flag would hand the person a segment nothing is known to be wrong with (the draft stays unreviewed but accepted by the checks).

#### Scenario: A reply cut by the cap keeps what it answered

- **WHEN** the reviewer's reply for eight pairs stops with `length` after the fifth entry
- **THEN** the five answers are used, one more reviewer call is made for the three unread pairs only, and no segment is
  flagged `reviewer-unavailable`

#### Scenario: Pairs unread after the re-ask are taken as ok

- **WHEN** the re-ask is cut too and answers none of its pairs
- **THEN** those pairs are decided by the quality checks alone, with no further reviewer call and no flag

### Requirement: Size the reviewer's output cap from the batch

The system SHALL size the reviewer's output cap from the batch as 128 tokens plus, for each pair, 70, the estimated
tokens of the pair's candidate (a rewrite may repeat it) and 120 for a possible edit, and SHALL expect a reply of half
the cap.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`; tasks 15b, 15d.6, 15e.7. In plain words: a cap sized from the pairs bounds every model call, so a small model cannot run on without limit.

#### Scenario: The cap grows with the pairs

- **WHEN** a reviewer call is built for two pairs
- **THEN** its output cap is 128 tokens plus, for each pair, 70, the estimated tokens of the pair's candidate and 120,
  and its expected length is half the cap

### Requirement: Settle a failed re-ask of a cut reviewer reply

IF a re-ask is answered with an unreadable reply (an empty completion, a context-window refusal, a stalled call), THEN
the system SHALL take its pairs as `ok` with a warning in the log; IF it fails with a cancellation (Pause or Stop) or a
provider outage, THEN it SHALL fail the whole review as the first call does, so the step is redone on resume and no unreviewed pair is stored as accepted.

**Source**: FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`; tasks 15b, 15d.6, 15e.7. In plain words: a re-ask that cannot answer is no reason to flag, but a Pause, a Stop or an outage must not leave an unreviewed pair stored as accepted.

#### Scenario: An unreadable re-ask takes its pairs as ok

- **WHEN** the re-ask for three unread pairs is answered with `ErrorCode.emptyCompletion`
- **THEN** those pairs are taken as `ok` with a warning in the log and no segment is flagged

#### Scenario: A re-ask that meets an outage fails the whole review

- **WHEN** the re-ask for three unread pairs is answered with `ErrorCode.unreachable`
- **THEN** the whole review fails like the first call, the step is redone on resume and no unreviewed pair is stored as
  accepted

### Requirement: Keep the best candidate through the repair path

The application SHALL carry a best candidate through a segment's repair path — the target with the fewest failed hard
gates and, among those, the fewest blocking checks, the checks counting before any reviewer finding — and SHALL replace
it by a step's result only when that result has fewer; progress SHALL be judged by the sets of blocking checks, never by
a score.

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), ADR-0038; tasks 15b, 15d.7. In plain words: on the hand test a repair that "improved" a segment sometimes left it with more defects than the draft had. A repair is a bet, so the best text seen so far is kept.

#### Scenario: A repair that breaks a hard gate is discarded

- **WHEN** the draft `Він.` fails only the length check and the directed fix answers `Він відчиниw.`, which fails
  `script-purity`
- **THEN** the segment is FLAGGED with `Він.` as its target and the `length` finding, and no second fix is sent

#### Scenario: No defect ends worse than its first candidate

- **WHEN** every fix answers a text with a Latin letter in a Cyrillic word and an unclosed guillemet, over each 15d.1
  defect the checks decide
- **THEN** each segment is FLAGGED with exactly its first candidate's findings

### Requirement: Stop the repair path at a step that does not improve

WHEN a step's result does not have fewer failed hard gates or blocking checks than the best candidate, whether it
regresses, swaps one blocker for another or repeats the text, THEN the application SHALL discard it, stop the path
there and keep the best candidate, which is always the final target, and SHALL write each round's blocker set before and after
to the log at DEBUG.

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), ADR-0038; tasks 15b, 15d.7. In plain words: a directed fix once returned the same text three times; a step that does not beat the best candidate ends the attempt.

#### Scenario: A second repair that makes it worse leaves the first

- **WHEN** Max, the draft `Він відчиниw «старі двері.` fails `script-purity` and `quote-balance`, the first fix answers
  `Він відчиниw старі двері.` (one blocker) and the second answers `HE OPENED THE «OLD DOOR.` (three)
- **THEN** the segment is FLAGGED with the `script-purity` finding of the first fix and no `echo` or `quote-balance`
  finding

#### Scenario: Swapping one blocker for another stops the path

- **WHEN** Max, a draft fails `script-purity` and `quote-balance`, the first fix leaves only `quote-balance`, and the
  second leaves only `script-purity`
- **THEN** the segment is FLAGGED with the `quote-balance` finding after 2 requests and no third fix is sent

#### Scenario: Each round's blocker set is logged

- **WHEN** a draft fails `script-purity` and `quote-balance` and the first repair round's fix leaves only `quote-balance`
- **THEN** the DEBUG log holds the blocker set before (`script-purity`, `quote-balance`) and after (`quote-balance`)

#### Scenario: A shrinking set continues up to the dial's rounds

- **WHEN** Max, a draft fails `script-purity` and `quote-balance`, the first fix leaves only `script-purity` and the
  second passes every check
- **THEN** the segment is ACCEPTED after 2 rounds as repaired
- **AND** with a dial of 1 round and the same first fix the segment is FLAGGED after 1 round

#### Scenario: A fix that leaves the same blockers stops the path

- **WHEN** an echoed draft gets a first directed fix answering `HE OPENED THE OLD DOOR!` with three rounds allowed
- **THEN** the segment is FLAGGED after 1 round and no second fix is sent

### Requirement: Send a directed fix only for a blocker with evidence

The application SHALL send a directed fix only for a blocker with evidence it holds — a failed check, or the quote of a
reviewer edit it could not verify — never for a concern with no finding, so a segment with nothing to name is decided
without a repair call, and at most as many fix rounds as the dial allows, none on a review retry.

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), ADR-0038; tasks 15b, 15d.7. In plain words: the reflect, improve and polish chain could no longer run once acceptance stopped depending on a score, and no measurement showed it helped, so it is gone rather than hidden behind a switch; a fix needs something concrete to name.

#### Scenario: A reviewer finding without a quote is never fixed

- **WHEN** the reviewer asks for a rewrite that mixes alphabets in one word
- **THEN** the draft is kept, the segment is FLAGGED and no directed fix is sent

#### Scenario: A review retry makes no fix

- **WHEN** a retry's reviewer edit is refused and the dial has no repair round
- **THEN** no directed fix is sent and the segment is FLAGGED with the edited text

#### Scenario: No reflect, improve or polish call exists

- **WHEN** a Max segment fails a check and its repair rounds run
- **THEN** only directed fixes are sent, with no reflect, improve or polish call and no near-miss polish window

### Requirement: Replace the edited text by a fix only when it removes a quote without a new blocker

A fix for refused reviewer edits SHALL replace the edited text only when it brings no new blocker and removes at least
one of the quotes.

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), ADR-0038; tasks 15b, 15d.7. In plain words: a fix that makes things worse is thrown away.

#### Scenario: A fix that adds a blocker is discarded

- **WHEN** a reviewer edit is refused, and the directed fix answers a text that still holds the quote and an unclosed
  guillemet
- **THEN** the edited text stays as the target and the segment is FLAGGED

#### Scenario: A fix that supplies the words of an omission edit is kept

- **WHEN** an `omission` edit `двері` → `старі двері` is refused because `двері` occurs twice, and the directed fix
  answers a text that holds `старі двері`
- **THEN** the fixed text is the target and the edit counts as resolved although the quote is still in it

#### Scenario: A fix that clears one of two quotes is kept

- **WHEN** two reviewer edits are refused and the directed fix removes the first quote but not the second
- **THEN** the fixed text is the target, the segment is FLAGGED, and the second quote stays on the record as evidence

### Requirement: Fail a segment on a deterministic text defect before the reviewer reads it

The application SHALL run the deterministic text checks on every restored candidate, after the refusal gate and before
any reviewer call, and SHALL treat a blocking finding as a failed hard gate.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: a mixed-alphabet word, a quote left open and a whole English paragraph accepted as Ukrainian are facts code can see, so the segment is repaired or flagged before a model is asked to opine on it.

#### Scenario: A text check runs before the reviewer

- **WHEN** a Balanced draft passes the refusal gate and leaves a quote open that its source closes
- **THEN** the quote check runs before any reviewer call, the segment fails a hard gate and the reviewer is not asked
  about it

### Requirement: Reject a word that mixes scripts

A word that holds a letter of the target's script beside a letter of another script or a digit (`навчg3вся`,
`імпoву`) SHALL raise a medium-or-higher `language` finding from `script-purity`.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: a word wholly in another script (a name) and a number's suffix (`90х`) are not mixed words, and a coined word in the right alphabet (`розлізяв`) cannot be seen by code and stays with the reviewer.

#### Scenario: A mixed-script word blocks acceptance

- **WHEN** English → Ukrainian and the draft for `He studied hard all year.` is `Він наполегливо навчg3вся цілий рік.`
- **THEN** the segment fails a hard gate with a `language` finding from `script-purity` whose note quotes `навчg3вся`
- **AND** the reviewer is not asked about that segment

### Requirement: Hold a target's quote pairs balanced

A quote pair of the target language's convention table that the target leaves open, closes without opening or closes
with the wrong mark, while the source's own pairs balance, SHALL raise a `fluency` finding from `quote-balance`.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: a target balanced with English curly quotes (`“…”`) only adds a low `quote-balance` note, because the typography pass leaves typographic marks alone; a mark that is also an apostrophe (`’`) is never a quote mark of any language's table.

#### Scenario: A quote left open blocks acceptance

- **WHEN** the source `“Yes, sir,” said the boy.` balances and the Ukrainian target is `«Ні, сер, — відповів хлопчик.`
- **THEN** the segment fails with a `fluency` finding from `quote-balance`
- **AND** a source that is itself open lets the target be open too

#### Scenario: English curly quotes only add a note

- **WHEN** the source `“Yes,” said the boy.` balances and the Ukrainian target is `“Так”, — сказав хлопчик.`, balanced
  with English curly quotes
- **THEN** only a low `quote-balance` note is added and the segment is not failed by it

#### Scenario: An apostrophe is no quote mark

- **WHEN** the Ukrainian target is `Він сказав п’ять слів.`
- **THEN** no `quote-balance` finding is raised

### Requirement: Identify a paragraph written in the wrong language

A paragraph of at least 12 words whose letters are under 60% in the target script, or, when the scripts match, whose
share of source-language function words (those the target language does not also use) is at least 30%, SHALL raise a
`language` finding from `language-identity`.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: a whole English paragraph accepted as Ukrainian is a fact code can see; a short English line is left to the echo check, because a name or an exclamation can read the same in both languages.

#### Scenario: An English paragraph is a hard failure

- **WHEN** English → Ukrainian or English → Polish and the target of a 19-word English paragraph is that paragraph
- **THEN** the segment fails with a `language` finding from `language-identity`

### Requirement: Note a doubled word and a spacing artefact the source lacks

A doubled word and a spacing artefact the source does not have SHALL only add a low `fluency` finding
(`duplicate-word`, `spacing`).

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: these are cosmetic, so they are notes for the person and never block a segment.

#### Scenario: A doubled word is only a note

- **WHEN** the target is `Мені одно одно таки.` and the source has no doubled word
- **THEN** the segment still passes its hard gates and carries a low `duplicate-word` finding

### Requirement: Name the exact span in each text finding and bound the text checks

Each finding SHALL carry the exact span and a plain explanation, and the directed fix SHALL repeat both in its findings
list. The checks SHALL be skipped for a kept foreign passage, SHALL read only quote marks (never dashes, commas or full
stops, which differ by language), and SHALL leave a text of only digits, URLs, e-mail addresses and ISBN labels alone, as
the script and echo checks do.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: the directed fix can only repair what it is told exactly, and a check must not fire on text that is not prose.

#### Scenario: The directed fix repeats the span

- **WHEN** the draft `Він наполегливо навчg3вся цілий рік.` gets a `script-purity` finding
- **THEN** the finding carries the span `навчg3вся` with a plain explanation, and the directed fix repeats both in its
  findings list

#### Scenario: A kept foreign passage is skipped

- **WHEN** the policy is `Keep as-is` and a segment marked foreign leaves the quote `«Je ne regrette rien` open
- **THEN** no `quote-balance` finding is raised for it

#### Scenario: A text of only a URL is left alone

- **WHEN** the target is `https://example.org/1990`
- **THEN** the deterministic text checks raise no finding

### Requirement: Normalise a candidate's typography before it is reviewed

The application SHALL, with no setting, put every candidate target — a draft, a repair round's rewrite, a review retry,
a reused memory entry, a consistency-pass revision — through a deterministic typography pass before the placeholder
gates restore it, so that the checks, the reviewer, the edits applied to the candidate and the stored masked target all
read the normalised text, and SHALL never run the pass on a source text.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3. In plain words: the model writes `п'ять...` and `"Іди геть"` about nine times in ten, so code puts `п’ять…` and `«Іди геть»` in, instead of asking a model to fix what code can see, and a person's later edit gets the same finish when the book is written. A draft's masked reply is the normalised one.

#### Scenario: A memory reuse is normalised too

- **WHEN** `ch03.xhtml:12`, reused from memory, holds `Він сказав: "Так..."`
- **THEN** its stored target is `Він сказав: «Так…»`

### Requirement: Normalise apostrophes, ellipses and spacing

The pass SHALL turn a straight apostrophe between two letters into `’`, three dots into `…`, and remove a space before
`,` `.` `;` `!` `?` or `…` (French keeps the space before `?` `!` `;`); a language with no line in the quote tables
gets these fixes only.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3. In plain words: code writes the typographic apostrophe and ellipsis that models almost never do.

#### Scenario: Apostrophe, ellipsis and quotes are normalised

- **WHEN** the Ukrainian draft is `Він сказав: "Не пам'ятаю..."`
- **THEN** the stored target is `Він сказав: «Не пам’ятаю…»`

### Requirement: Write quote marks in the target language's convention

For a target language that has a line in the quote table, the pass SHALL turn a straight `"` into that language's
primary pair at the top level and its nested pair inside it (`«…„…“…»` in Ukrainian), SHALL keep a dash dialogue a dash
dialogue, and SHALL leave a quote whose side cannot be told, a stray closing mark and an inch sign as they are.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3. In plain words: the model writes `"Іди геть"`, and code writes `«Іди геть»` where the side of every mark is certain and touches nothing it would have to guess.

#### Scenario: Straight quotes become the language's pair

- **WHEN** the Ukrainian draft is `Він сказав: "Іди геть".`
- **THEN** the stored target is `Він сказав: «Іди геть».`

#### Scenario: A dash dialogue stays a dash dialogue

- **WHEN** the Ukrainian draft is `— Іди геть, — сказав він.`
- **THEN** the stored target is unchanged

### Requirement: Leave tokens untouched and make the typography pass idempotent

The pass SHALL read only the text between `⟦gN⟧` tokens, so markup, code spans, kept foreign runs and locked names (all
tokens at that point) and the characters touching a token are never changed, and SHALL give the same text when run
twice.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3. In plain words: the pass can never damage markup or a locked name, and running it again changes nothing. The space before a footnote marker is not handled, because a marker is a token like any other and the text cannot say which one it is.

#### Scenario: Tokens are never touched

- **WHEN** the draft is `Він ⟦g1⟧сказав...⟦g2⟧ "так" ⟦g3⟧ , ⟦g4⟧.`
- **THEN** every token is still in its place, and the text between them is `сказав…`, `«так»`, and nothing next to a
  token loses a space

#### Scenario: A space before a footnote marker stays

- **WHEN** the draft is `Іди геть ⟦g5⟧.` with `⟦g5⟧` a footnote marker
- **THEN** the space before `⟦g5⟧` stays

#### Scenario: A second pass changes nothing

- **WHEN** an already normalised text is normalised again
- **THEN** it is the same text and no finding is added

### Requirement: Record a typography change as a low finding

WHEN the typography normaliser changes a candidate, the application SHALL add a low `fluency` finding from `normalised`
naming how many of each kind were changed, and SHALL never change the verdict of a gate.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3. In plain words: the person can see what code changed, and a typography fix never decides whether a segment is accepted.

#### Scenario: A change is recorded as a low finding

- **WHEN** the Ukrainian draft `Він сказав: "Не пам'ятаю..."` is normalised
- **THEN** the segment carries a low `normalised` finding and its gates give the same verdicts as before

### Requirement: Repair a candidate's quote marks deterministically, and keep a usable draft when a text check still blocks

When the only blocker of a restored candidate is `quote-balance`, the application SHALL repair its quote marks without a
model call before any round or reviewer reads it, SHALL change quote marks only, never a word, a protected span or a
`⟦gN⟧` token, and SHALL repeat to the same text.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: a usable Ukrainian paragraph with one stray quote mark is mended by code, or at worst exported as the model wrote it and flagged for a look; an English leftover is never exported as if it were a translation.

#### Scenario: An unclosed quote is repaired and the segment accepted

- **WHEN** a draft's only blocker is an unclosed «
- **THEN** the mark is closed without a model call, the segment is ACCEPTED with a low `normalised` finding

### Requirement: Record a quote-mark repair only when its blocker is gone

The repaired candidate SHALL go through the gate and the checks again and replace the original only when the quote
blocker is gone, and the change SHALL be recorded as a low `normalised` finding.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: a repair that did not clear the blocker is dropped and the original candidate stays.

#### Scenario: A repair that leaves the blocker is dropped

- **WHEN** the quote repair of a draft leaves its `quote-balance` blocker
- **THEN** the original candidate stays and no `normalised` finding is recorded

### Requirement: Collapse and re-pair stray quote marks

The quote repair SHALL collapse a doubled mark (`»»`), write straight marks as the language's pairs, replace a closer
that crosses the open mark with the open mark's own closer, and open a paragraph whose first mark is a stray closer when
the source opens with a quote (also when the closer ends the paragraph; otherwise it SHALL drop a stray closer at a
paragraph edge).

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: these are the repairs where the side of every mark is certain.

#### Scenario: A doubled closer is collapsed

- **WHEN** the Ukrainian draft is `«Він пішов»»` and its source `“He left”` balances
- **THEN** the repaired text is `«Він пішов»`

### Requirement: Close one unclosed opener at the dash or the paragraph's end

The quote repair SHALL close one unclosed opener before the dash clause that follows it, only when the source's own
quote closes before its dash, or else at the paragraph's end, before a single final full stop.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: the one place a closing mark can be put with certainty is where the source puts it.

#### Scenario: An opener is closed before a final full stop

- **WHEN** the source is `He said, "I'll come — maybe tomorrow."` and the Ukrainian draft is `«Я прийду — можливо, завтра.`
- **THEN** the repaired text is `«Я прийду — можливо, завтра».`

### Requirement: Treat English curly quotes as a pair of their own

English curly quotes `“…”` inside a target whose language writes `“` as a closer (uk, ru, be, de, cs, sk, sl, hr, bg)
SHALL be a pair of their own, never a closer of the open mark, and a text balanced with English quotes alone SHALL be
left as it is.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: in those languages `“` closes a quotation, so an English curly pair inside a Ukrainian quotation must not be mistaken for the end of the outer one.

#### Scenario: An English curly pair inside an open quotation is its own pair

- **WHEN** the Ukrainian draft is `«Він сказав “так” і пішов.`
- **THEN** the repaired text is `«Він сказав “так” і пішов».`

#### Scenario: A text balanced with English quotes alone is left as it is

- **WHEN** the Ukrainian draft is `Він сказав “так” і пішов.`
- **THEN** the quote repair returns it unchanged

### Requirement: Leave the quote marks unchanged when a repair would be a guess

The quote repair SHALL return the text unchanged when the source's own quotes are open, a placeholder token sits inside
a word, a language has no quote line of its own, or any step would be a guess.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: code mends only what it can decide with certainty, and otherwise leaves the text for the repair rounds.

#### Scenario: A source that is itself open is left alone

- **WHEN** the source is `He said, “I'll come` and the Ukrainian draft is `«Він сказав, я прийду`
- **THEN** the quote repair returns the draft unchanged

### Requirement: Keep a usable draft as the machine target when a text check still blocks

A segment that still ends FLAGGED, whose draft restored through the placeholder gate and whose every failed hard gate is
`quote-balance` or `script-purity`, SHALL keep that draft (the best candidate of its repair path) as its machine target,
so review shows it with its finding and export writes it instead of the source.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: a usable Ukrainian paragraph with one stray quote mark is at worst exported as the model wrote it and flagged for a look.

#### Scenario: A quote the repair cannot clear keeps the draft

- **WHEN** a draft with two unclosed « ends FLAGGED
- **THEN** its machine target is the draft, and the export writes it, not the source

### Requirement: Keep no machine target for an English leftover or a broken placeholder

A segment whose failed gate is `language-identity`, `refusal`, `placeholder`, `locked-term` or `kept-run` SHALL keep no
machine target.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: an English leftover is never exported as if it were a translation.

#### Scenario: An English leftover keeps no draft

- **WHEN** a draft fails `language-identity` and ends FLAGGED
- **THEN** it has no machine target and the export writes the source and lists it

### Requirement: Accept a segment only by the acceptance rule

The application SHALL accept a drafted, edited or repaired segment only when its hard gates pass, no soft check failed
outright, no deterministic check blocks, and no verified blocker is left — an issue the reviewer evidenced with a quote
the code found and that no edit or fix resolved; the confidence the checks derive SHALL NOT be compared with any
threshold and the reviewer's fluency and style remarks SHALL NOT block.

**Source**: FR-QA-07, FR-QA-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#reviewer-in-place-fixes`, ADR-0038; tasks 15d.6. In plain words: a segment is accepted unless the code can point at something wrong with it. A repaired target is decided by the checks alone: the reviewer read the draft, not the repair. A soft check that fails outright is the one in "Block acceptance when a soft check fails outright". A reuse from memory follows the same rule with no reviewer (see "Accept a context-matched memory reuse without the reviewer").

#### Scenario: A low-confidence segment whose checks all pass is accepted

- **WHEN** Balanced and the segment passes every check with a length margin of 0.2 and confidence `0.70`, and the
  reviewer answers `ok`
- **THEN** it is accepted in Unattended, Assisted and Manual alike

#### Scenario: A hard-gate failure is never accepted

- **WHEN** a segment's placeholder hard gate failed, so it was not shown to the reviewer
- **THEN** the segment is not accepted and goes to a directed fix

#### Scenario: A failed soft check blocks a segment the reviewer likes

- **WHEN** a chunk is reviewed and one of its segments failed its length-ratio check outright
- **THEN** that segment is not reviewed, is not accepted and goes to a directed fix

#### Scenario: A repaired target is accepted by the checks alone

- **WHEN** a directed fix returns a target that passes every check
- **THEN** it is accepted as repaired and no reviewer call follows

#### Scenario: A good but short translation is accepted

- **WHEN** the reviewer is off and a segment passes every check with a length margin of 0.5 and confidence `0.875`
- **THEN** it is accepted in Unattended, Assisted and Manual alike

#### Scenario: A verified blocker left unresolved flags the segment

- **WHEN** a refused edit's quote is still in the target after its one directed fix
- **THEN** the segment is FLAGGED although every check passes

#### Scenario: The 15d.1 corpus replays through the rule

- **WHEN** the clean candidates of the defect corpus that the old judge refused are replayed with a reviewer that finds
  nothing, and the defects only a model can see are replayed with the edits a reviewer would ask for
- **THEN** every clean candidate is accepted unchanged, every defect the checks decide is refused, and every other
  defect ends as the corrected text

### Requirement: Accept a context-matched memory reuse without the reviewer

WHEN a segment's target is reused from memory because its source and both neighbours match, the application SHALL
accept that target only when its hard gates pass and no soft check failed outright, and SHALL make no reviewer call for
it nor show it to its chunk's reviewer.

**Source**: FR-ALGO-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#context-aware-tm`, `#tiered-loop`, ADR-0038. In plain words: a sentence already accepted in the very same surroundings needs no second opinion from the reviewer, but it must still clear every deterministic check, so a memory entry can never put a broken or since-forbidden target into the book. When the memory is looked up, and that a reuse which is not accepted is discarded and the segment drafted instead, is the translation-pipeline capability's "Reuse the translation memory only where its context matches"; a segment is recorded as reused from memory only when its reuse is accepted.

#### Scenario: A context match is accepted without the reviewer

- **WHEN** Balanced, `ch03.xhtml:12` reads `Yes.` between the same two sentences that surrounded `ch01.xhtml:4`, which
  was accepted as `Так.`
- **THEN** `ch03.xhtml:12` is accepted as `Так.` with confidence 1.0, no draft or reviewer call is made for it, and it is
  recorded as reused from memory

#### Scenario: A reuse that fails a hard gate is drafted instead

- **WHEN** `ch01.xhtml:4` `Hale nodded.` was accepted as `Хейл кивнув.`, the person then locked `Hale` → `Гейл`, and
  `ch03.xhtml:12` reads `Hale nodded.` between the same neighbours
- **THEN** the reused target fails the protected-span hard gate and is not accepted
- **AND** `ch03.xhtml:12` is drafted instead, with `Hale` sent as a placeholder

### Requirement: Repair a failing segment within the dial's repair budget before flagging it

WHEN a drafted segment is not accepted because a check refuses it, the application SHALL run up to N self-heal rounds —
N being 1 on Fast, 2 on Balanced and 3 on Max — each returning one target for that one segment; after N rounds without
acceptance it SHALL mark the segment FLAGGED with every finding, and a segment accepted after at least one round SHALL
count as repaired and accepted.

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#directed-fix-repair`, ADR-0038. In plain words: most failures can be fixed by asking the model again with the problem named. The budget keeps a stubborn segment from eating the run, and the repaired count shows how much the repairs are doing. A segment the checks accept goes to the reviewer instead (see "Verify every reviewer edit in code before applying it").

#### Scenario: The budget runs out

- **WHEN** Fast (N = 1) and the segment still fails after its one round
- **THEN** it is marked FLAGGED

#### Scenario: A repaired segment is counted

- **WHEN** Max and the segment is accepted in its second round
- **THEN** it is accepted and counted as repaired and accepted, not as auto-accepted

### Requirement: Do not count the draft's format repairs as repair rounds

The draft's one structural repair and one placeholder repair SHALL NOT count as self-heal rounds.

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#directed-fix-repair`, ADR-0038. In plain words: the draft's own two format repairs come first and never use up the budget — their order, and the replies that flag a segment at once, are the translation-pipeline capability's "Repair invalid structured draft replies once" and "Flag a segment whose reply cannot be used, and continue".

#### Scenario: The draft's format repairs are not rounds

- **WHEN** Fast (N = 1) and the draft, its placeholder repair and the directed fix all come back without `⟦g1⟧`
- **THEN** the segment is FLAGGED with a high `markup` finding and no machine translation, after exactly 3 model calls
- **AND** the directed fix was made although the placeholder repair had already been used

### Requirement: Name a concrete finding in each round's directed fix

Each self-heal round SHALL be a directed fix naming the concrete finding — a failed hard gate, with the expected
placeholder sequence stated when the placeholder or protected-span gate failed, or a soft check failed outright — and
SHALL have no call when there is nothing concrete to name.

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#directed-fix-repair`, ADR-0038. In plain words: a model repairs what it is told exactly, and a round with nothing to name would only ask it to guess.

#### Scenario: A placeholder failure gets a directed fix with the expected tokens

- **WHEN** Balanced, the source holds `⟦g0⟧`, `⟦g1⟧`, `⟦g2⟧`, and both the draft and its placeholder repair come back
  without `⟦g1⟧`
- **THEN** the first round is a directed fix that states the expected sequence `⟦g0⟧ ⟦g1⟧ ⟦g2⟧`
- **AND** its reply is one target for that segment

### Requirement: Decide a repaired target by the checks alone

A target a round returns SHALL be decided by the checks alone, with no reviewer call, and a round that does not lower
the blocker set ends the repair there ("Keep the best candidate through the repair path").

**Source**: FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`), ADR-0038. In plain words: the reviewer read the draft, not the repair, so a repaired target is judged by the code alone.

#### Scenario: A repaired target gets no reviewer call

- **WHEN** Balanced and a directed fix returns a target that passes every check
- **THEN** the segment is accepted as repaired and no reviewer call follows

### Requirement: Record each segment's findings for review and repair

The application SHALL record for every decided segment its confidence and each finding with the check or the reviewer
that raised it, its type, its severity and a short note, SHALL record every edit it applied as a low finding raised by
`reviewer-edit` that carries the quote and its replacement, and SHALL keep the findings of a FLAGGED segment for the
review desk and for any later retry.

**Source**: FR-QA-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`. In plain words: a directed fix needs to know exactly what was wrong, and so does the person reviewing a flagged segment; a flag with no reason would leave them guessing, and an edit made for them should be shown as what it removed and what it put there.

#### Scenario: A flagged segment keeps its reasons

- **WHEN** segment `ch5 · p12` is flagged after its length ratio of `0.41` fell outside `0.7–1.8`
- **THEN** its record carries confidence and a medium `omission` finding from the length-ratio check

#### Scenario: An accepted segment keeps the edits made to it

- **WHEN** a segment is accepted after the reviewer's `gender` edit `Вона втомився` → `Вона втомилася` was applied
- **THEN** its record carries a low `gender` finding raised by `reviewer-edit` holding both texts

#### Scenario: An accepted segment keeps a low note

- **WHEN** a segment is accepted with a `style` remark from the reviewer
- **THEN** its record carries that remark as a low finding raised by `reviewer`

### Requirement: Hold a first-person narrator's words to the narrator's gender

WHEN the brief's narrator is first person with a male or female gender the person set, the brief verified or the run
inferred from the narrator's own forms, and the target language's file names a gender check with `genderCheck=<name>`,
the application SHALL run it on every candidate and raise a low `gender` finding (`raisedBy` `gender`) naming each word
that disagrees; a gender shown as "(suggested)" SHALL be held against no text.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10. In plain words: when the person says the narrator is a man and the draft says «я зачинила», code can see the wrong ending, so one repair call is made for it; because a suffix rule is sometimes wrong, the check can only note and repair, never reject.

#### Scenario: A suggested narrator gender is not enforced

- **WHEN** the narrator is first-person with a male gender that is only suggested and the draft says
  `Я відчинила старі двері.`
- **THEN** there is no finding and no directed fix, and a reviewer edit that changes `відчинила` to `відчинив` is
  ignored with a low `ignored-edit` note and the draft stays

### Requirement: Keep the gender check soft and per language

A gender check SHALL be a soft check: it never fails a hard gate, never blocks acceptance and never flags a segment by
itself. A language whose file names no `genderCheck` SHALL have no gender check and raise no gender finding.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10. In plain words: a suffix rule is sometimes wrong, so the check can only note and repair; a language whose file names no check is never checked.

#### Scenario: Third-person text is left alone

- **WHEN** the target is `Вона зачинила двері.` or the narrator is third-person
- **THEN** there is no finding

#### Scenario: A language with no gender check finds nothing

- **WHEN** the target language is Polish, whose file names no `genderCheck`, and the narrator is first-person male
- **THEN** no gender finding is raised

### Requirement: Read the Ukrainian narrator's past-tense ending

The Ukrainian check (`genderCheck=uk`) SHALL read the first word after the narrator's `я`, past a short list of
particles and adverbs and never behind a comma, a full stop, a colon, a bracket or a dash, and SHALL take a past-tense
ending `-в`/`-вся` as masculine, `-ла`/`-лась`/`-лася` as feminine and `-ло`/`-лось` as neuter (which fits no narrator),
except for a list of words that end so and are not past-tense verbs.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10. In plain words: the ending of the verb after «я» tells the narrator's gender, and a few words that merely end alike are excepted.

#### Scenario: A male narrator with a feminine verb is flagged

- **WHEN** the narrator is first-person male and the target is `Я зачинила двері й пішов.`
- **THEN** one finding spans `зачинила` and the segment is accepted and sent to one directed fix

#### Scenario: The corpus is caught without a model

- **WHEN** the check runs over the gender corpus
- **THEN** every defective case is flagged and at most 5% of the faithful cases are

### Requirement: Hold only the narrator's own words to the gender check

The Ukrainian check SHALL read only the narrator's own words, so a passage inside quote marks of the language's
convention table (nested pairs and an unclosed one included) and the speech lines of a dash dialogue are left out, as
are third-person sentences.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10. In plain words: a woman speaking inside quotes is not the narrator, so her verbs are never held against a male narrator.

#### Scenario: A woman speaking in quotes is not flagged

- **WHEN** the narrator is first-person male and the target is `«Я зачинила двері», — сказала вона.`
- **THEN** there is no finding, and the same holds for `— Я зачинила двері, — сказала вона.`

### Requirement: Spend one directed fix on an accepted segment's gender finding

WHEN an accepted segment carries a `gender` finding and the dial has a repair round and the segment was not drafted in
pieces, the application SHALL spend exactly one directed fix on it, naming the finding, and SHALL take the fix only when
its text passes every check and no longer carries the finding; otherwise the segment stays accepted as it was with the
finding kept as a note. A fix call that fails SHALL leave the accepted segment as it was.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10. In plain words: one repair call is made for a wrong ending, and a fix that does not help is not repeated.

#### Scenario: The fix repairs the segment

- **WHEN** the scripted model first answers `Я відчинила старі двері.` for a male narrator and the fix answers
  `Я відчинив старі двері.`
- **THEN** the segment is accepted on the repaired path with the fixed text and no `gender` finding

#### Scenario: A fix that does not help is not repeated

- **WHEN** the fix answers the same wrong text
- **THEN** the segment stays accepted with its draft, carries the `gender` finding and no second fix is called

### Requirement: Ask a word validator about doubtful words, as a note only

The application SHALL run every candidate's display text through the run's `WordValidator` after the other text checks
and raise one low `unknown-word` finding (`raisedBy` `unknown-word`, kind `fluency`) naming each word the validator
doubts with its quoted span, as a soft note that never fails a hard gate, never blocks acceptance and never flags a
segment by itself.

**Source**: FR-QA-01; task 15d.11. In plain words: a made-up word such as `кафедрахрі` cannot be caught by script, so a validator may point at it, but only as a note, because a real rare, dialect or archaic word can be missing from any source of words.

#### Scenario: A doubted word becomes a low note

- **WHEN** the validator doubts `кафедрахрі` in `Він сидів на кафедрахрі й мовчки дивився у вікно.`
- **THEN** one low `unknown-word` finding quotes `кафедрахрі`, the hard gates pass and the segment is not failed outright

### Requirement: Bind a word validator with no opinion by default

The validator bound by default SHALL have no opinion, so a run with it is exactly a run without the check, and a
validator SHALL be a pure function of the text and the target language tag. No dictionary of words is bundled or
planned.

**Source**: FR-QA-01; task 15d.11. In plain words: today nothing is bound, so the check changes nothing until a validator is chosen.

#### Scenario: The default validator changes nothing

- **WHEN** a segment is evaluated with the default validator
- **THEN** the result equals the one produced without a validator

### Requirement: Run the model-based word validator as one structured call per batch

The optional model-based validator SHALL cost one structured call per batch of texts at temperature zero with a flat
schema, SHALL keep a reported word only when it is a whole word of the text it names and its quoted phrase, when given,
is words of that text that include it, SHALL say nothing when its call fails, and SHALL NOT be enabled by default, in
the window or by the command line.

**Source**: FR-QA-01; task 15d.11. In plain words: a model-based pointer exists only to be measured, so it is optional, bounded to one call per batch, and anything it reports that the text does not hold is dropped.

#### Scenario: A word the text does not hold is dropped

- **WHEN** the model-based validator reports a word that is not a whole word of the text, or a quote the text does not hold
- **THEN** no finding is raised for it

#### Scenario: A failed call says nothing

- **WHEN** the model-based validator's call fails
- **THEN** it reports no words and the segment is judged as it would be without the check

#### Scenario: The corpus is measured, not asserted

- **WHEN** `scripts/eval-matrix.sh --suite words` runs the ten garbled and ten clean sentences of `eval/words.json`
- **THEN** a report holds the recall over the garbled cases and the false-positive rate over the clean ones

### Requirement: Audit every accepted segment again when a run completes

WHEN a run ends COMPLETED, and again whenever the review desk is asked, the application SHALL check every ACCEPTED
segment no person has reviewed (not kept as source, not kept verbatim) with the checks a run applies before
acceptance — the deterministic text checks, the gender check, the run's word validator, a placeholder token written
into the stored plain target — and the book-wide `name-variants` check; spacing and a lost glossary name are not
audited.

**Source**: FR-QA-01, task 15d.12. In plain words: the run's checks only see a candidate while it is being decided, so a leak that gets through (a paragraph left in English, a stray quote, a name spelled two ways) used to be found only by reading the book. The audit asks the same checks again over the finished book and lists what is still doubtful, with the check that fired, as advice that never blocks anything. A lost glossary name is no audit doubt, it is a soft finding at draft time (15h.A5, see "Catch a lost or misspelt name, a copied Latin phrase and a listed foreign word").

#### Scenario: A leftover and a stray quote are listed

- **WHEN** a finished run holds an accepted segment whose target is still English, one whose target leaves a quote
  open that the source closed, and the audit runs
- **THEN** the two segments are suspicious with the checks `language-identity` and `quote-balance`
- **AND** the segments the run translated cleanly are not listed

#### Scenario: A declined name is no loss

- **WHEN** the glossary maps `Nell` to `Нелл` and the target holds `Нелла`
- **THEN** the run-time `name-missing` finding does not fire

### Requirement: Store each audit finding under the check's name

Each audit finding SHALL be stored on the record as an ordinary finding whose `raisedBy` is `audit:` and the check's
name (`audit:language-identity`, `audit:quote-balance`, `audit:script-purity`, `audit:duplicate-word`, `audit:gender`,
`audit:unknown-word`, `audit:token-leak`, `audit:name-variants`), replacing the findings of an earlier audit so that a
segment fixed since loses its mark.

**Source**: FR-QA-01, task 15d.12. In plain words: the person sees which check fired, and a segment fixed since the last audit is no longer listed.

#### Scenario: A fixed segment leaves the list

- **WHEN** a suspicious segment's target is clean at the next audit, or a person has reviewed the segment
- **THEN** it holds no audit finding and is not suspicious

### Requirement: Keep the final audit advisory

The audit SHALL change no status, target or count of accepted segments, SHALL NOT fail a completed run — a check that
throws is logged once and the run stays completed — and SHALL persist nothing beyond those findings. A segment is
"suspicious" while it is ACCEPTED, not reviewed, and holds an audit finding.

**Source**: FR-QA-01, task 15d.12. In plain words: the audit is advice that never blocks anything, so a clean run stays clean.

#### Scenario: The fixture book is not doubted

- **WHEN** the earth-gravity fixture goes through a run with a model that never fails, in each of the four formats
- **THEN** the audit lists no segment

### Requirement: Catch a lost or misspelt name, a copied Latin phrase and a listed foreign word

While a segment is drafted, the deterministic text checks SHALL raise a soft `name-missing` finding (15h.A5) for an
unlocked glossary name (a capitalised term with a rendering) that the source names as a whole word and no word of the
target holds by the lexicon's stem rule, reporting its rendering; two entries one vowel or one doubled letter apart are
one name.

**Source**: task 15h.A5. In plain words: the Oct 9 runs lost a place name; this check catches it. A lost name is a soft finding at draft time, fixable by the one directed fix.

#### Scenario: A lost place name earns a fix

- **WHEN** the glossary maps `Zurich` to `Цюріх`, the source says "They reached Zurich at dawn." and the draft says
  "Вони дісталися до міста на світанку."
- **THEN** the draft carries a soft `name-missing` finding and is not kept as drafted

### Requirement: Report a misspelt glossary name with its replacement

WHEN exactly one capitalised word of the target is the rendering written another way (equal once doubled letters are
collapsed and the language's `voicingPairs` are folded) and is no pronoun, stop word or word the target also writes in
lower case, the deterministic text checks SHALL raise a soft `name-spelling` finding naming that word and the exact
replacement instead of `name-missing`.

**Source**: task 15h.A5. In plain words: a name spelled one letter differently is named with its exact replacement, so the one directed fix can put it right.

#### Scenario: A misspelt name names its replacement

- **WHEN** the glossary maps `Finn` to `Фінн` and the target says «Фін відчинив двері.»
- **THEN** a soft `name-spelling` finding names `Фін` and the replacement `Фінн`, instead of `name-missing`

### Requirement: Block a copied Latin phrase

The deterministic text checks SHALL raise a blocking `latin-run` finding for two or more Latin words in a row that the
source also holds, left in a target written in Cyrillic or Greek that has more letters of its own script than the run
has characters, except a locked term whose rendering is its own spelling and a glossary rendering the target holds,
which are kept originals and not a run (15i.T4), and SHALL raise none in a target the `language-identity` check has
already reported.

**Source**: task 15h.A5. In plain words: the Oct 9 runs left an English phrase in a Ukrainian sentence; this stops it. A mostly Latin target is the echo, script and language-identity checks' case, and a paragraph already reported as source language is not reported twice.

#### Scenario: A copied phrase blocks

- **WHEN** a Ukrainian draft keeps "Daily Courier" from the source
- **THEN** the draft has a blocking `latin-run` finding

#### Scenario: A kept original is no copied phrase

- **WHEN** a Ukrainian draft keeps "KESSEL OPTIK", a brand the glossary locks as its own spelling, between translated words,
  and the source holds the same two words
- **THEN** the draft has no `latin-run` finding, while "Daily Courier" beside it still blocks

#### Scenario: A mostly Latin target is not a latin-run

- **WHEN** a Ukrainian target is the English source copied back whole
- **THEN** no `latin-run` finding is raised, and the echo, script and language-identity checks decide

### Requirement: Report a listed foreign word

A whole word of the target that the target language's file lists under `foreignWords` (`тоже>теж`) and the source does
not hold SHALL raise a soft `foreign-word` finding with the exact replacement, in the word's capitalisation; the soft
`name-missing`, `name-spelling` and `foreign-word` findings SHALL all be fixable by the one directed fix.

**Source**: task 15h.A5. In plain words: a short list names the Russian words that slip into Ukrainian text, so they can be replaced exactly.

#### Scenario: A listed foreign word names its replacement

- **WHEN** a Ukrainian draft holds `тоже` and the source does not hold it
- **THEN** a soft `foreign-word` finding names `тоже` and the replacement `теж`

### Requirement: Count a name spelling only where the source names the term

The whole-book `name-variants` audit SHALL count a spelling only in a segment whose source names the term and never when
the word is a pronoun, a stop word or written in lower case in the targets. The final audit does not repeat
`name-missing` or `latin-run`.

**Source**: task 15h.A5. In plain words: the Oct 9 runs let "Він" count as a misspelling of "Фінн"; counting only where the source names the term stops that.

#### Scenario: A pronoun is no spelling of a name

- **WHEN** the targets write "Фінн" and "Він" and the glossary maps `Finn` to `Фінн`
- **THEN** no name variant is reported

### Requirement: Check each target word against the target language's alphabet

The deterministic text checks SHALL include a target-alphabet check: for every target word that holds a letter listed
under `forbiddenLetters` in the target language's file (Ukrainian: `ыъэё`) and does not stand in the source text as
well, it SHALL raise a blocking `alphabet` finding (15j.J5) naming the word and the letter, which blocks acceptance and
earns the directed fix; a language with no such key is never checked.

#### Scenario: A Russian letter in a Ukrainian word is repaired or flagged

- **WHEN** a Ukrainian draft holds `бэкона` or `кобыли`
- **THEN** the segment is not accepted as it is: the directed fix names the word, and the segment is accepted when the
  fix removes the letter
- **AND** a draft that still holds the letter after the repair round is flagged with the `alphabet` finding, its draft
  kept as the machine text

#### Scenario: A word that stands in the source is left alone

- **WHEN** the source is `He read “Эхо” aloud.` and the Ukrainian draft is `Він прочитав «Эхо» вголос.`
- **THEN** the alphabet check raises no finding for `Эхо`

#### Scenario: A clean Ukrainian text is not touched

- **WHEN** a Ukrainian text holds `бекону`, `кобилу`, `очерету` and `ґанок`
- **THEN** the alphabet check says nothing

### Requirement: Hold one quoted speech to one form of address

The deterministic text checks SHALL include an address-mix check (15i.T6): a target language whose file lists
`informalAddress` and `formalAddress` (Ukrainian: the ти-forms, and the Ви-forms written capitalised) gets one soft
`address-mix` finding, naming both forms, for each quoted speech (a quoted passage, or dash dialogue up to the dash that
opens the author's remark) that holds a form of each list; a language whose file lists no such keys is never checked.

**Source**: task 15i.T6. In plain words: a model sometimes writes "ти" and "Ви" to the same listener in one line; the check names both forms so the one fix can pick a single way of address.

#### Scenario: A speech that mixes the forms is named

- **WHEN** a Ukrainian draft holds «Сядь, тобі треба відпочити, а я принесу Вам чаю.» as one speech
- **THEN** the segment is accepted with a soft `address-mix` finding naming "тобі" and "Вам"

### Requirement: Read address forms by capitalisation, one speech at a time

The informal forms SHALL count in any case; a formal form SHALL count only when it is capitalised and not the first
word of a sentence, because a sentence start is capitalised whatever the word is. Only the speech SHALL be read, each
speech on its own, never the narrator's words.

**Source**: task 15i.T6. In plain words: the check must not mistake a capitalised sentence start for the polite "Ви", and must not mix the forms of two different listeners.

#### Scenario: Two speeches addressed differently are left alone

- **WHEN** a paragraph holds one ти speech to a child and, after a remark, one Ви speech to a stranger
- **THEN** the address-mix check says nothing

#### Scenario: A capitalised sentence start is no formal form

- **WHEN** a Ukrainian speech reads «Вам тут не місце, ти знаєш?»
- **THEN** no `address-mix` finding is raised, because «Вам» opens the sentence and only the informal «ти» counts

### Requirement: Treat an address mix as a note with one fix

The `address-mix` finding SHALL NOT block acceptance or flag a segment; with a repair round available it earns the same
single directed fix as a narrator-gender finding, otherwise it stays a note for review. A language whose file lists no
`informalAddress` and `formalAddress` SHALL NOT be checked.

**Source**: task 15i.T6. In plain words: the mix is advice, not a reason to reject.

#### Scenario: The fix is asked once

- **WHEN** a Ukrainian draft holds «Сядь, тобі треба відпочити, а я принесу Вам чаю.» as one speech and the dial has a
  repair round
- **THEN** one directed fix is asked for the `address-mix` finding
- **AND** a fix that keeps the mix leaves the draft accepted with the finding as a note
