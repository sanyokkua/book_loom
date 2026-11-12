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
`markup` finding, and SHALL NOT accept the segment, whatever its confidence.

**Source**: FR-QA-04, FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, ADR-0040, ADR-0038. In plain words: the placeholder check the document round trip performs before restoring markup (see the document-round-trip capability, which also owns the two pair rules: a pair that held text must still hold text, and a line break stays inside the same pair) is the first quality gate; a pair that comes back crossed would restore broken markup and fail the whole export at the very end, so it is caught here for one segment instead. An atomic placeholder — an image, a line break, a locked term — may move, because word order legitimately changes. A draft that fails this gate first gets its marks put back by code (the translation-pipeline capability's "Put lost formatting marks back by code before any model call"); only a segment still failing after it earns the one repair at Balanced and Max, or is marked at Fast.

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

**Source**: FR-GLOSS-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), EC-FOREIGN-3 (`docs/spec_baseline/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `docs/spec_baseline/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, ADR-0037. In plain words: a locked name (see the glossary capability's "Render a locked term exactly as entered") and a foreign phrase the book keeps verbatim (see "Keep an inline foreign run verbatim under the Keep as-is policy") both reach the model as one placeholder, so the model can neither misspell the name nor translate the phrase. A reply that drops or duplicates such a placeholder would lose or double the name or the phrase, so it can never pass. Like any placeholder, a protected one may move.

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

**Source**: FR-GLOSS-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, ADR-0037. In plain words: when one locked term contains another, the longer wins, so `Baker Street` is never split into a placeholder for `Baker` followed by the word `Street`.

#### Scenario: The longest locked term is hidden first

- **WHEN** `Baker Street` and `Baker` are both locked and the segment is `Baker Street was quiet; Baker left.`
- **THEN** the model receives `⟦g0⟧ was quiet; ⟦g1⟧ left.`

### Requirement: Keep an inline foreign run verbatim under the Keep as-is policy

WHILE the Book Brief's foreign-passage policy is `Keep as-is`, the application SHALL hide each inline element whose own
`lang` or `xml:lang` differs from the brief's source language (both tags normalized), together with its text, behind one
placeholder before the segment reaches the model, SHALL restore it verbatim, and SHALL decide which runs are foreign
from those declarations alone, for any language the application recognizes.

**Source**: EC-FOREIGN-3 (`docs/spec_baseline/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), DD-26 (`docs/spec_baseline/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), FR-QA-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), ADR-0037. In plain words: a French farewell inside an English sentence must stay French when the person chose to keep foreign passages, and the surest way is to never show it to the model. Only a run the book itself marks with another language is kept: the application does not detect languages from text (ADR-0037), and a region variant of the source language is not foreign.

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

**Source**: EC-FOREIGN-3 (`docs/spec_baseline/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), DD-26 (`docs/spec_baseline/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), FR-QA-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), ADR-0037. In plain words: the fixture book's `<p xml:lang="la">Gravitas omnia trahit, sed nemo videt.</p>` was sent to the model under Keep as-is, translated and reviewed with nothing to edit, because only inline runs were hidden and the prompt's "keep it verbatim" line was not enough for a small model. A marked block is now never sent at all. Comparing with the book's own declared language too keeps a brief whose source was set to another language from leaving every paragraph under a `<body xml:lang="en">` untranslated; a block declaring the book's own language is never foreign.

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

**Source**: EC-FOREIGN-3 (`docs/spec_baseline/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection`), DD-26 (`docs/spec_baseline/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-26-foreign-passage-policy`), FR-QA-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), ADR-0037. In plain words: under the other two policies the foreign run is translated like the rest of the sentence; the second adds the original in parentheses so the reader can see what was there.

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`. In plain words: a model that declines, apologises or talks about the text instead of translating it has produced nothing a reader can use; letting it through would put the apology into the book. Only phrases about the task or the model count — the full list per language is in `05_PIPELINE_ENGINE.md#qa-thresholds` — so a character's own apology is translated, not refused, and a book whose own line begins `Translation:` keeps its translated `Переклад:`. A reply that holds only its placeholders would silently delete the paragraph's words. A reply with no text at all, or a blank `target`, is not this gate's case: the translation-pipeline capability flags or repairs it before any check. A segment with nothing to translate — no letter, a Roman numeral or a single character — never reaches this gate: it is kept as it is with no model call (`translation-pipeline` "Keep a segment with nothing to translate as it is"), so an empty reply to the chapter number `2` can no longer flag it.

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
- **AND** the segment earns the one repair at Balanced and Max and is FLAGGED at Fast

### Requirement: Match a refusal phrase only as whole words, in any of three languages

The application SHALL compare the target's start with the refusal phrases trimmed, without regard to case, with a
typographic apostrophe read as `'`, in English, the source language and the target language, and SHALL count a phrase
only when it is followed by the end of the text or by a character that is neither a letter nor a digit.

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`. In plain words: a phrase must end where a word ends, so `Як ШІ` never catches `Як шість`, and a refusal written with a curly apostrophe is still a refusal.

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

**Source**: FR-QA-01, FR-QA-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`, ADR-0037. In plain words: with no language detector, the only reliable signal is the alphabet — a Ukrainian translation written in Latin letters is plainly wrong. Serbian is catalogued as Cyrillic, so Latin-script Serbian output fails.

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

**Source**: FR-QA-01, FR-QA-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`, ADR-0037. In plain words: the check cannot tell Polish from Czech, so a same-script pair skips it and a wrong language in the same script is left to the source-only function-word check and to the Proofread stage; short fragments skip it so a name or an exclamation is not misjudged. Names the person chose to keep in their original spelling are Latin by design, so they are not held against a Cyrillic line.

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a model that copies the source back has translated nothing; comparing lower-cased text stops a copy in capitals from slipping past. Whether a failed echo blocks the segment depends on the source's length (see "Block acceptance when a soft check fails outright").

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a line made only of names the person chose to keep is meant to read like its source, so the names are taken out before the comparison. A short line that is only glossary names, such as `Bartimaeus!`, is explained by the glossary whatever the policy — how a name is rendered is the glossary check's question, not the echo's.

#### Scenario: A names-only line kept by policy is not an echo

- **WHEN** English → Ukrainian, the name policy is `Keep original`, the glossary holds `Margaret Hale` and
  `Milton Northern`, and `Margaret Hale, Milton Northern.` comes back unchanged
- **THEN** what remains after removing the names holds no letter, so the untranslated-echo check is skipped
- **AND** the segment is accepted in Assisted with confidence 1.0

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: small local models sometimes get stuck repeating a phrase until the output limit; this catches that decode loop without punishing ordinary repetition in prose, while a phrase said twice over lowers confidence a little.

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a translation far shorter than its source has usually dropped content, and one far longer has usually added some; the band differs by writing system because Chinese is naturally much shorter than English. A ratio just inside the band passes, but with less confidence than one comfortably inside it.

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: very short segments get a wider band because one word more or less changes their ratio a lot; a verse line Ukrainian says in half the words keeps its length (`it has been falling night and day` → `вона падає ніч і день`, 0.64, is no omission).

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a dropped name or phrase can leave the character count inside the band, so the space left before a full stop and a word count far below the source's are read as well; 0.55 sits well below any real pair's word ratio (English to Ukrainian runs about 0.8), and a verse line Ukrainian says in half the words keeps its length (`A stone let go will find the ground,` → `Камінь, відпущений, знайде землю,`), so a faithful translation is not caught by it.

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a compact line such as `Unfortunately, nothing happened.` → `На жаль, нічого.` (0.50) is complete in half the characters because Ukrainian needs fewer function words; one real run flagged 14 such lines as omissions, so below the lower bound a compact line is trusted as long as it keeps half its words.

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

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: below the lower bound a compact line is trusted as long as it keeps half its words (or, past 40 characters, three words and a ratio of 0.40). Known tradeoff (2026-10-07 review): the extended range also passes a real omission, and the six short-line cases of the real run (10 words to 3 or 4) were the reason for it; on the Fast dial no later call reads such a line.

#### Scenario: A longer line kept in three words is complete

- **WHEN** the source is `Then, without a word, the old man left the room.` and the target is `Старий мовчки вийшов.`
- **THEN** the length-ratio check does not fail it

#### Scenario: The extended range also passes a real omission

- **WHEN** the source is `He looked at her for a long time, then left.` and the target is `Він подивився на неї.`
  (44 characters of 10 words kept as 4, ratio 0.48)
- **THEN** the length-ratio check does not fail it, although part of the sentence is missing, and on the Fast dial no
  later call reads it

### Requirement: Measure glossary compliance over the locked terms in the segment

The application SHALL pass glossary compliance, with a margin of 1.0, when every locked glossary term occurring in the
source segment appears in the target as its entered rendering, SHALL fail it otherwise, and SHALL skip it, counting
1.0, when the segment contains no locked term.

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), FR-GLOSS-03 (`#fr-gloss`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds`. In plain words: a name the person locked must read exactly as they entered it; unlocked entries are only guidance for the model and do not lower the score. Because every locked term travels as a placeholder and comes back as its rendering, this check passes whenever the protected-span hard gate did; it stays in the blend because the reference weights include it.

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

**Source**: FR-QA-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds` (foreign-keep vs echo), ADR-0037. In plain words: a French line the author left untranslated in an English novel must stay French when the person chose to keep foreign passages, and that is not a failure; but only a passage the book itself marks, or one in another alphabet, earns that pass — a segment that merely echoes its source is still caught.

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

**Source**: FR-QA-03 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-thresholds` (foreign-keep vs echo), ADR-0037. In plain words: the comparison is with the source language the person confirmed on the brief, not with what the book's package claims, so a book whose package declares the wrong language does not have every paragraph marked foreign.

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
counting 0.0 and a skipped check 1.0, and SHALL NOT fold the hard gates into it; the confidence only orders segments for review and never decides acceptance.

**Source**: FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#confidence`. In plain words: one number says how comfortably a segment clears the deterministic checks; hard gates stay pass/fail because no score can excuse broken markup; the number is a sort key for the review list, not a bar; the Proofread stage reads its windows in reading order. Each check's margin is stated in its own requirement above.

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

- **WHEN** the one repair is kept for the English → Polish segment above
- **THEN** its confidence is computed from the checks on the repaired text alone, and no model's opinion adds anything to it

### Requirement: Block acceptance when a soft check fails outright

IF one of the five confidence checks (glossary, length ratio, target script, untranslated echo, repetition) fails outright, THEN the application SHALL NOT accept the segment, whatever its confidence, SHALL record a medium finding of the check's kind at BLOCK level, and SHALL give it the one repair at Balanced and Max and none at Fast, a segment still blocked after that being FLAGGED, except as "Let a short echo lower confidence without blocking" says.

**Source**: FR-QA-07, FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `#qa-thresholds`, `design.md#d12--checks-levels-and-code-repairs-before-checks`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a check that fails is a concrete defect — a copied source, the wrong alphabet, half a sentence missing — and a high score on the other checks must not carry it into the book; confidence only orders segments for review. A blocked segment no longer goes through rounds of directed fixes: it gets one repair, and at Fast it is FLAGGED for the desk at once.

#### Scenario: A copied source is repaired, then flagged, whatever its confidence

- **WHEN** Balanced, English → Ukrainian, the draft for `He opened the old door.` is `He opened the old door.`, with confidence 0.65
- **THEN** the segment is not accepted and carries medium `language` findings for the echo and the script checks
- **AND** its one repair returns `He opened the old door.` again, and the segment is FLAGGED

#### Scenario: Fast makes no repair and flags at once

- **WHEN** Fast and the same draft
- **THEN** no call follows the draft and the segment is FLAGGED with the same findings

#### Scenario: A Latin-letter translation is flagged

- **WHEN** Balanced, and both the draft and the repair for `He opened the old door.` return `Vin vidchynyv stari dveri.`, with confidence 0.80
- **THEN** the segment is FLAGGED with a medium `language` finding from the script check

#### Scenario: A half-sentence omission is flagged

- **WHEN** Balanced, and both the draft and the repair for `He opened the old door and walked into the dark hall.` return `Він відчинив старі двері.` (ratio 0.47), with confidence 0.75
- **THEN** the segment is FLAGGED with a medium `omission` finding from the length-ratio check

#### Scenario: Each failed check records the finding of its kind

- **WHEN** the target-script, untranslated-echo, repetition, length-ratio and glossary-compliance checks each fail outright
- **THEN** the findings are medium `language`, medium `language`, medium `fluency`, medium `omission` and medium `glossary` respectively

#### Scenario: The pseudo model's echo is flagged in every mode

- **WHEN** the pseudo model translates `He opened the old door.` (23 code points) from English to Ukrainian on Balanced
- **THEN** its draft `HE OPENED THE OLD DOOR.` fails the echo and script checks, its one repair returns the same text, and the segment is FLAGGED
- **AND** this holds in Unattended, Assisted and Manual alike

### Requirement: Let a short echo lower confidence without blocking

IF the failed check is the untranslated echo and the source's display text, after any removal of names kept by policy,
has fewer than 20 code points, THEN the application SHALL NOT block the segment: the failure SHALL only add its 0.0
margin to confidence and record a low `language` finding.

**Source**: FR-QA-07, FR-QA-01 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `#qa-thresholds`, ADR-0038. In plain words: short lines are the exception for the echo check alone: a name, a Roman numeral or `OK` legitimately reads the same in both languages, so below 20 code points an echo lowers confidence instead of blocking.

#### Scenario: A short echo lowers confidence without blocking

- **WHEN** English → Ukrainian and the target for `Yes, sir.` (9 code points) is `YES, SIR.`
- **THEN** the echo check fails with a low `language` finding, the script check is skipped, and confidence is
  0.30 + 0.25 + 0.20 + 0.0 + 0.10 = `0.85`
- **AND** the segment is accepted in Unattended, Assisted and Manual

#### Scenario: The echo floor is 20 code points

- **WHEN** English → Ukrainian and the target for `It was a dark night.` (exactly 20 code points) is
  `IT WAS A DARK NIGHT.`
- **THEN** the failed echo blocks acceptance and records a medium `language` finding

### Requirement: Let the review mode decide when the run pauses, never what is accepted

The application SHALL accept or flag a segment by the acceptance rule alone — hard gates and the checks — and SHALL NOT let the review mode, the quality dial or any confidence threshold move that decision;
the review mode SHALL decide only when the run pauses for the person.

**Source**: FR-REVIEW-02, FR-QA-07 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`, `#fr-qa`), `docs/spec_baseline/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, ADR-0036. In plain words: the earlier rule asked a segment to reach a confidence of 0.60, 0.75 or 0.85 by review mode, and a judge score besides. A score cannot tell a coined word from a rare one, so on a real book it flagged 170 decisions that a person found correct. What a person wants to check is one choice — the review mode — and it now alone decides when the run stops; whether a segment is accepted depends on facts the code can verify, and there is no reviewer whose opinion could add a blocker.

#### Scenario: A low confidence alone does not flag a segment

- **WHEN** a segment passes its hard gates and every soft check with a script margin of 0.5, a length margin of 0.2 and
  a confidence of `0.70`
- **THEN** it is accepted in Unattended, Assisted and Manual alike

#### Scenario: The quality dial leaves acceptance alone

- **WHEN** the review mode is Unattended and the quality dial is Max
- **THEN** a draft that passes every check is accepted as it would be on Fast, and the dial decides only whether a failing segment earns the one repair

### Requirement: Fail a segment on a deterministic text defect before any repair or mark

The application SHALL run the deterministic text checks on every restored candidate, after the refusal gate and the code repairs and before the one repair or any mark, and SHALL treat a BLOCK-level finding as a failed hard gate.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: a mixed-alphabet word, a quote left open and a whole English paragraph accepted as Ukrainian are facts code can see, so the segment is repaired or flagged on those facts, and the repair is told exactly what they are.

#### Scenario: A text check runs before the reviewer

- **WHEN** a Balanced draft passes the refusal gate and holds the word `навчg3вся`
- **THEN** the script-purity check runs before the one repair, the segment fails a hard gate with a BLOCK finding, and the repair is told the word

### Requirement: Reject a word that mixes scripts

A word that holds a letter of the target's script beside a letter of another script or a digit (`навчg3вся`,
`імпoву`) SHALL raise a medium-or-higher `language` finding from `script-purity`.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: a word wholly in another script (a name) and a number's suffix (`90х`) are not mixed words, and a coined word in the right alphabet (`розлізяв`) cannot be seen by code and stays with the Proofread stage.

#### Scenario: A mixed-script word blocks acceptance

- **WHEN** English → Ukrainian and the draft for `He studied hard all year.` is `Він наполегливо навчg3вся цілий рік.`
- **THEN** the segment fails a hard gate with a `language` finding from `script-purity` whose note quotes `навчg3вся`
- **AND** the segment earns the one repair at Balanced and Max

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
share of source-only function words (the source language's function words minus the target language's) is at least 30%,
SHALL raise a BLOCK-level `language` finding from `language-identity`.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: a whole English paragraph accepted as Ukrainian is a fact code can see; a short English line is left to the echo check, because a name or an exclamation can read the same in both languages. A few source-only words in an otherwise translated paragraph are not this finding: they are the function-word residue finding, which earns a repair and keeps the draft.

#### Scenario: An English paragraph is a hard failure

- **WHEN** English → Ukrainian or English → Polish and the target of a 19-word English paragraph is that paragraph
- **THEN** the segment fails with a `language` finding from `language-identity`

#### Scenario: A Russian paragraph accepted as Ukrainian is a hard failure

- **WHEN** Russian → Ukrainian and the target is a 14-word Russian paragraph of which 6 words are in the pair's source-only set
- **THEN** the segment fails with a BLOCK `language` finding from `language-identity` and keeps no machine target

### Requirement: Note a doubled word and a spacing artefact the source lacks

A doubled word and a spacing artefact the source does not have SHALL only add a low `fluency` finding
(`duplicate-word`, `spacing`).

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: these are cosmetic, so they are notes for the person and never block a segment.

#### Scenario: A doubled word is only a note

- **WHEN** the target is `Мені одно одно таки.` and the source has no doubled word
- **THEN** the segment still passes its hard gates and carries a low `duplicate-word` finding

### Requirement: Name the exact span in each text finding and bound the text checks

Each finding SHALL carry the exact span and a plain explanation, and the repair SHALL repeat both in its defect
list. The checks SHALL be skipped for a kept foreign passage, SHALL read only quote marks (never dashes, commas or full
stops, which differ by language), and SHALL leave a text of only digits, URLs, e-mail addresses and ISBN labels alone, as
the script and echo checks do.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15d.2. In plain words: the one repair can only fix what it is told exactly, and a check must not fire on text that is not prose.

#### Scenario: The directed fix repeats the span

- **WHEN** the draft `Він наполегливо навчg3вся цілий рік.` gets a `script-purity` finding
- **THEN** the finding carries the span `навчg3вся` with a plain explanation, and the repair repeats both in its
  defect list

#### Scenario: A kept foreign passage is skipped

- **WHEN** the policy is `Keep as-is` and a segment marked foreign leaves the quote `«Je ne regrette rien` open
- **THEN** no `quote-balance` finding is raised for it

#### Scenario: A text of only a URL is left alone

- **WHEN** the target is `https://example.org/1990`
- **THEN** the deterministic text checks raise no finding

### Requirement: Normalise a candidate's typography before any check counts

The application SHALL, with no setting, put every candidate target — a draft, a repair's rewrite, a retry, a reused memory entry, a proofread rewrite — through the deterministic code repairs of this capability before the placeholder gates restore it and before any check counts, so the checks, the edits applied to the candidate and the stored masked target all read the normalised text, and SHALL never run them on a source text.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: the model writes `п'ять...` and `"Іди геть"` about nine times in ten, so code puts `п’ять…` and `«Іди геть»` in, instead of asking a model to fix what code can see, and a person's later edit gets the same finish when the book is written. The code repairs now also strip stress marks, restore a lost dialogue dash and heading number, and write dashes in one form; each is its own requirement. A draft's masked reply is the normalised one.

#### Scenario: A memory reuse is normalised too

- **WHEN** `ch03.xhtml:12`, reused from memory, holds `Він сказав: "Так..."`
- **THEN** its stored target is `Він сказав: «Так…»`

#### Scenario: A proofread rewrite is normalised before it is judged

- **WHEN** the Proofread stage returns `Він сказав: "Не пам'ятаю..."` for `ch03.xhtml:14`
- **THEN** the never-worse gate reads `Він сказав: «Не пам’ятаю…»`

#### Scenario: A source text is never touched

- **WHEN** a source paragraph holds `"Yes..."`
- **THEN** it is unchanged

### Requirement: Normalise apostrophes, ellipses and spacing

The pass SHALL turn a straight apostrophe between two letters into the apostrophe the brief's apostrophe choice names (`’` unless the person chose `'`), three dots into `…`, and remove a space before
`,` `.` `;` `!` `?` or `…` (French keeps the space before `?` `!` `;`); a language with no line in the quote tables
gets these fixes only.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3. In plain words: code writes the apostrophe the person chose for the book, and the typographic ellipsis, that models almost never do.

#### Scenario: The person chose the straight apostrophe

- **WHEN** the brief's apostrophe is `'` and the Ukrainian draft is `Він сказав: "Не пам'ятаю..."`
- **THEN** the stored target is `Він сказав: «Не пам'ятаю…»`

#### Scenario: Apostrophe, ellipsis and quotes are normalised

- **WHEN** the brief's apostrophe is the default `’` and the Ukrainian draft is `Він сказав: "Не пам'ятаю..."`
- **THEN** the stored target is `Він сказав: «Не пам’ятаю…»`

### Requirement: Write quote marks in the target language's convention

For a target language that has a line in the quote table, the pass SHALL turn a straight `"` into the primary pair of the brief's quote-style choice (by default the language's own convention) at the top level and its nested pair inside it (`«…„…“…»` in Ukrainian by default), SHALL keep a dash dialogue a dash
dialogue, and SHALL leave a quote whose side cannot be told, a stray closing mark and an inch sign as they are.

**Source**: FR-QA-01, ADR-0038; tasks 15d.3. In plain words: the model writes `"Іди геть"`, and code writes `«Іди геть»` where the side of every mark is certain and touches nothing it would have to guess.

#### Scenario: Straight quotes become the language's pair

- **WHEN** the Ukrainian draft is `Він сказав: "Іди геть".`
- **THEN** the stored target is `Він сказав: «Іди геть».`

#### Scenario: The person chose another pair

- **WHEN** the brief's quote style for Ukrainian is `„…“` and the draft is `Він сказав: "Іди геть".`
- **THEN** the stored target is `Він сказав: „Іди геть“.`

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
model call before the one repair or any mark reads it, SHALL change quote marks only, never a word, a protected span or a
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

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: code mends only what it can decide with certainty, and otherwise leaves the text for the one repair.

#### Scenario: A source that is itself open is left alone

- **WHEN** the source is `He said, “I'll come` and the Ukrainian draft is `«Він сказав, я прийду`
- **THEN** the quote repair returns the draft unchanged

### Requirement: Keep a usable draft as the machine target when a text check still blocks

A segment that ends FLAGGED SHALL keep as its machine target the last candidate that restored through the placeholder
gate, except a paragraph in the wrong language, which keeps none; export SHALL write that machine target only when every
failed hard gate left on it is `quote-balance` or `script-purity`, and the source otherwise.

**Source**: FR-QA-01, FR-QA-07, ADR-0038; tasks 15e.5. In plain words: a usable Ukrainian paragraph with one stray quote mark is at worst exported as the model wrote it and flagged for a look. Every other flagged text is kept for the review desk to show, but the book gets the source there; a whole paragraph left in the wrong language keeps no machine text at all, so it can never be written as a translation. This is the one rule every other requirement about a flagged segment's text follows.

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

The application SHALL accept a drafted, edited or repaired segment only when its hard gates pass, no soft check failed outright and no BLOCK-level finding is left; REPAIR-level and NOTE-level findings left on it SHALL NOT stop acceptance and SHALL become its marks, and the confidence the checks derive SHALL NOT be compared with any threshold.

**Source**: FR-QA-07, FR-QA-02 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `design.md#d12--checks-levels-and-code-repairs-before-checks`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a segment is accepted unless the code can point at something blocking in it. A repaired target is decided by the hard gates and the never-worse gate ("Keep a repair only when it passes the hard gates and the never-worse gate keeps it"), then read by the same rule; what stays wrong at repair or note level is accepted with a mark for the Proofread stage. A soft check that fails outright is the one in "Block acceptance when a soft check fails outright". A reuse from memory follows the same rule with no model call (see "Accept a context-matched memory reuse when its checks pass").

#### Scenario: A low-confidence segment whose checks all pass is accepted

- **WHEN** Balanced and the segment passes every check with a length margin of 0.2 and confidence `0.70`
- **THEN** it is accepted in Unattended, Assisted and Manual alike

#### Scenario: A hard-gate failure is never accepted

- **WHEN** a segment's placeholder hard gate failed
- **THEN** the segment is not accepted and, at Balanced and Max, earns the one repair

#### Scenario: A failed soft check blocks a segment the reviewer likes

- **WHEN** a segment failed its length-ratio check outright, whatever any model thought of its wording
- **THEN** it is not accepted and, at Balanced and Max, earns the one repair

#### Scenario: A repaired target is accepted by the checks alone

- **WHEN** the one repair returns a target that passes the hard gates and every check, and the never-worse gate keeps it
- **THEN** the segment is accepted as repaired and no other call follows

#### Scenario: A verified blocker left unresolved flags the segment

- **WHEN** the one repair of a segment leaves a BLOCK `alphabet` finding on the text that stays
- **THEN** the segment is FLAGGED although its other checks pass

#### Scenario: A good but short translation is accepted

- **WHEN** a segment passes every check with a length margin of 0.5 and confidence `0.875`
- **THEN** it is accepted in Unattended, Assisted and Manual alike

#### Scenario: A repair-level leftover is accepted with a mark

- **WHEN** the text that stays holds a `terminal-mark` finding at REPAIR level and no BLOCK finding
- **THEN** the segment is accepted with a proofread mark

#### Scenario: The 15d.1 corpus replays through the rule

- **WHEN** the clean candidates of the defect corpus that the old judge refused are replayed
- **THEN** every clean candidate is accepted unchanged and every defect the checks decide at BLOCK level is refused

### Requirement: Accept a context-matched memory reuse when its checks pass

WHEN a segment's target is reused from memory because its source and both neighbours match, the application SHALL
accept that target only when its hard gates pass and no soft check failed outright, and SHALL make no model call for
it.

**Source**: FR-ALGO-06 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#context-aware-tm`, `#tiered-loop`, ADR-0038. In plain words: a sentence already accepted in the very same surroundings needs no new model call, but it must still clear every deterministic check, so a memory entry can never put a broken or since-forbidden target into the book. When the memory is looked up, and that a reuse which is not accepted is discarded and the segment drafted instead, is the translation-pipeline capability's "Reuse the translation memory only where its context matches"; a segment is recorded as reused from memory only when its reuse is accepted.

#### Scenario: A context match is accepted without the reviewer

- **WHEN** Balanced, `ch03.xhtml:12` reads `Yes.` between the same two sentences that surrounded `ch01.xhtml:4`, which
  was accepted as `Так.`
- **THEN** `ch03.xhtml:12` is accepted as `Так.` with confidence 1.0, no draft call is made for it, and it is
  recorded as reused from memory

#### Scenario: A reuse that fails a hard gate is drafted instead

- **WHEN** `ch01.xhtml:4` `Hale nodded.` was accepted as `Хейл кивнув.`, the person then locked `Hale` → `Гейл`, and
  `ch03.xhtml:12` reads `Hale nodded.` between the same neighbours
- **THEN** the reused target fails the protected-span hard gate and is not accepted
- **AND** `ch03.xhtml:12` is drafted instead, with `Hale` sent as a placeholder

### Requirement: Record each segment's findings for review and repair

The application SHALL record for every decided segment its confidence and each finding with the check that raised it, its type, its severity, its level (BLOCK, REPAIR or NOTE) and a short note, SHALL keep the findings of a FLAGGED segment for the review desk and for any later retry, and SHALL keep the REPAIR-level findings left on an ACCEPTED segment as its proofread mark and its NOTE-level findings as its audit mark.

**Source**: FR-QA-05 (`docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-qa`), `docs/spec_baseline/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, `design.md#d12--checks-levels-and-code-repairs-before-checks`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: the one repair needs to know exactly what was wrong, and so does the Proofread stage and the person reviewing a flagged segment; a flag with no reason would leave them guessing, and a mark with no named defect would give the proofread nothing to look at.

#### Scenario: A flagged segment keeps its reasons

- **WHEN** segment `ch5 · p12` is flagged after its length ratio of `0.41` fell outside `0.7–1.8`
- **THEN** its record carries confidence and a medium `omission` finding at BLOCK level from the length-ratio check

#### Scenario: An accepted segment keeps the edits made to it

- **WHEN** a segment is accepted after the stress strip removed 2 accent marks from its draft
- **THEN** its record carries a low `normalised` finding naming the 2 marks removed

#### Scenario: An accepted segment keeps a repair-level leftover as its proofread mark

- **WHEN** segment `ch5 · p13` is accepted with a `terminal-mark` finding at REPAIR level still on its text
- **THEN** its record carries that finding as its proofread mark

#### Scenario: An accepted segment keeps a low note

- **WHEN** segment `ch5 · p14` is accepted with a NOTE `number` finding naming an extra `5`
- **THEN** its record carries that finding as its audit mark and no proofread mark

### Requirement: Hold a first-person narrator's words to the narrator's gender

WHEN the brief's narrator is first person with a male or female gender the person set, the book's evidence verified or the run
inferred from the narrator's own forms, and the target language's file names a gender check with `genderCheck=<name>`,
the application SHALL run it on every candidate and raise a medium `gender` finding at REPAIR level (`raisedBy` `gender`) naming each
word that disagrees; a gender shown as "likely" SHALL be held against no text.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10. In plain words: when the person says the narrator is a man and the draft says «я зачинила», code can see the wrong ending, so one repair call is made for it; because a suffix rule is sometimes wrong, the check can only note and repair, never reject.

#### Scenario: A suggested narrator gender is not enforced

- **WHEN** the narrator is first-person with a male gender that is only likely (the model's guess) and the draft says
  `Я відчинила старі двері.`
- **THEN** there is no finding and no repair call, and the draft stays

### Requirement: Keep the gender check soft and per language

A gender check SHALL be a repair-level check: it never fails a hard gate, never blocks acceptance and never flags a segment by itself; its REPAIR-level finding earns the one repair at Balanced and Max, and a leftover SHALL be accepted with a proofread mark. A language whose file names no `genderCheck` SHALL have no gender check and raise no gender finding.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a suffix rule is sometimes wrong, so the check can only note and repair; a language whose file names no check is never checked.

#### Scenario: Third-person text is left alone

- **WHEN** the target is `Вона зачинила двері.` or the narrator is third-person
- **THEN** there is no finding

#### Scenario: A language with no gender check finds nothing

- **WHEN** the target language is Polish, whose file names no `genderCheck`, and the narrator is first-person male
- **THEN** no gender finding is raised

#### Scenario: A leftover is accepted with a mark

- **WHEN** Balanced, the narrator is first-person male, and the one repair of `Я зачинила двері.` still holds `зачинила`
- **THEN** the segment is ACCEPTED with a proofread mark naming `зачинила`, and is not flagged

### Requirement: Read the Ukrainian narrator's past-tense ending

The Ukrainian check (`genderCheck=uk`) SHALL read the first word after the narrator's `я`, past a short list of
particles and adverbs and never behind a comma, a full stop, a colon, a bracket or a dash, and SHALL take a past-tense
ending `-в`/`-вся` as masculine, `-ла`/`-лась`/`-лася` as feminine and `-ло`/`-лось` as neuter (which fits no narrator),
except for a list of words that end so and are not past-tense verbs.

**Source**: FR-QA-01, ADR-0038; tasks 15d.10. In plain words: the ending of the verb after «я» tells the narrator's gender, and a few words that merely end alike are excepted.

#### Scenario: A male narrator with a feminine verb is flagged

- **WHEN** the narrator is first-person male and the target is `Я зачинила двері й пішов.`
- **THEN** one finding spans `зачинила` at REPAIR level and the segment is accepted, with the one repair at Balanced and Max

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

### Requirement: Audit every accepted segment again when the Translate stage ends

WHEN the Translate stage of a run ends, before the Proofread stage builds its mark list, and again whenever the review desk is asked, the application SHALL check every ACCEPTED segment no person has reviewed (not kept as source, not kept verbatim) with the checks a run applies before acceptance — the deterministic text checks, the gender check, the number and terminal-mark checks, a placeholder token written into the stored plain target — and the book-wide `name-variants` check.

**Source**: FR-QA-01, task 15d.12, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. See also "Doubt a segment that lost two or more negations" and "Doubt the minority target of a repeated source paragraph". In plain words: the run's checks only see a candidate while it is being decided, so a leak that gets through (a paragraph left in English, a stray quote, a name spelled two ways) used to be found only by reading the book. The audit asks the same checks again over the finished book and lists what is still doubtful, with the check that fired, as advice that never blocks anything; its list is the audit marks the Proofread stage reads, so it runs once before that stage builds its list and not again after it (the never-worse gate guards every change the stage makes). There is no word validator any more. A lost glossary name is no audit doubt, it is a repair-level finding at draft time (see "Catch a lost glossary name"); spacing is not audited.

#### Scenario: A leftover and a stray quote are listed

- **WHEN** a finished run holds an accepted segment whose target is still English, one whose target leaves a quote open that the source closed, and the audit runs
- **THEN** the two segments are suspicious with the checks `language-identity` and `quote-balance`
- **AND** the segments the run translated cleanly are not listed

#### Scenario: The audit runs before the mark list

- **WHEN** a Balanced run's Translate stage ends with accepted `ch2 · p04` leaving a quote open that its source closed
- **THEN** the audit lists `ch2 · p04` with the check `quote-balance` before the Proofread stage builds its mark list, which holds `ch2 · p04` as an audit mark
- **AND** no audit runs after the Proofread stage has read its windows

#### Scenario: A declined name is no loss

- **WHEN** the glossary maps `Nell` to `Нелл` and the target holds `Нелла`
- **THEN** the run-time `name-missing` finding does not fire

### Requirement: Store each audit finding under the check's name

Each audit finding SHALL be stored on the record as an ordinary finding whose `raisedBy` is `audit:` and the check's
name (such as `audit:language-identity` or `audit:negation`; every audited check has one), replacing the findings of an earlier audit so that a
segment fixed since loses its mark.

**Source**: FR-QA-01, task 15d.12. In plain words: the person sees which check fired, and a segment fixed since the last audit is no longer listed.

#### Scenario: A fixed segment leaves the list

- **WHEN** a suspicious segment's target is clean at the next audit, or a person has reviewed the segment
- **THEN** it holds no audit finding and is not suspicious

#### Scenario: Every audited check has its name

- **WHEN** the audit runs on a Russian → Ukrainian book
- **THEN** the names it may store are `audit:language-identity`, `audit:function-word-residue`, `audit:alphabet`, `audit:address-parity`, `audit:quote-balance`, `audit:script-purity`, `audit:duplicate-word`, `audit:gender`, `audit:number`, `audit:terminal-mark`, `audit:token-leak`, `audit:name-variants`, `audit:negation` and `audit:source-variants`

### Requirement: Keep the final audit advisory

The audit SHALL change no status, target or count of accepted segments, SHALL NOT fail a completed run — a check that
throws is logged once and the run stays completed — and SHALL persist nothing beyond those findings. A segment is
"suspicious" while it is ACCEPTED, not reviewed, and holds an audit finding.

**Source**: FR-QA-01, task 15d.12. In plain words: the audit is advice that never blocks anything, so a clean run stays clean.

#### Scenario: The fixture book is not doubted

- **WHEN** the earth-gravity fixture goes through a run with a model that never fails, in each of the four formats
- **THEN** the audit lists no segment

### Requirement: Catch a lost glossary name

While a segment is drafted, the deterministic text checks SHALL raise a `name-missing` finding for an
unlocked glossary name (a capitalised term with a rendering) that the source names as a whole word and no word of the
target holds by the lexicon's stem rule, at REPAIR level, reporting its rendering; two entries one vowel or one doubled letter apart are
one name.

**Source**: task 15h.A5 of the archived translation-workflow change, which added the lost-name check. In plain words: the Oct 9 runs lost a place name; this check catches it. A lost name is a repair-level finding at draft time, fixable by the one repair. A copied Latin phrase is the separate requirement "Block a copied Latin phrase".

#### Scenario: A lost place name earns a fix

- **WHEN** the glossary maps `Zurich` to `Цюріх`, the source says "They reached Zurich at dawn." and the draft says
  "Вони дісталися до міста на світанку."
- **THEN** the draft carries a `name-missing` finding at REPAIR level and, at Balanced and Max, earns the one repair

### Requirement: Report a misspelt glossary name with its replacement

WHEN exactly one capitalised word of the target is a name's rendering written another way (equal once doubled letters are collapsed and the language's `voicingPairs` are folded, and not one of the forms the book map lists for the name) and is no pronoun, stop word or word the target also writes in lower case, the checks SHALL raise a `name-spelling` finding naming that word and its replacement instead of `name-missing`, at BLOCK level for a pinned name and at NOTE level for a suggested one.

**Source**: task 15h.A5 of the archived translation-workflow change (the lost-name check), `design.md#d4--name-forms-stress-marks-and-target-validation`. See also the glossary capability for pinned names and their forms. In plain words: a name spelled one letter differently is named with its exact replacement, so the one repair can put it right. A pinned name inflects freely, so a declined form the book map lists is correct; only a word that is none of its forms is a spelling error, and for a pinned name that is a defect the book may not carry. A suggested target is only a hint, so a different spelling of it earns no model call, only a note.

#### Scenario: A misspelt name names its replacement

- **WHEN** the glossary maps `Finn` to `Фінн` and the target says «Фін відчинив двері.»
- **THEN** a `name-spelling` finding names `Фін` and the replacement `Фінн`, instead of `name-missing`

#### Scenario: A pinned name's declined form is no error

- **WHEN** the book map lists `Фінн`, `Фінна` and `Фінну` for the pinned name Finn, and the target says «Я бачив Фінна.»
- **THEN** no `name-spelling` finding is raised

#### Scenario: A misspelt pinned name blocks, a misspelt suggested name asks for a repair

- **WHEN** the target says «Фін відчинив двері.» once for the pinned name Finn and once for a suggested name
- **THEN** the first finding is BLOCK level and the second is NOTE level, so only the first earns the one repair

### Requirement: Block a copied Latin phrase

The deterministic text checks SHALL raise a blocking `latin-run` finding for two or more Latin words in a row that the
source also holds, left in a target written in Cyrillic or Greek that has more letters of its own script than the run
has characters, except a locked term whose rendering is its own spelling and a glossary rendering the target holds,
which are kept originals and not a run (15i.T4), and SHALL raise none in a target the `language-identity` check has
already reported, nor for a segment of a front-matter or back-matter unit or of the auxiliary unit (a copyright page, a
title), where Latin is legitimate.

**Source**: task 15h.A5. In plain words: the Oct 9 runs left an English phrase in a Ukrainian sentence; this stops it. A mostly Latin target is the echo, script and language-identity checks' case, and a paragraph already reported as source language is not reported twice.

#### Scenario: A copied phrase blocks

- **WHEN** a Ukrainian draft keeps "Daily Courier" from the source
- **THEN** the draft has a blocking `latin-run` finding

#### Scenario: A kept original is no copied phrase

- **WHEN** a Ukrainian draft keeps "KESSEL OPTIK", a brand the glossary locks as its own spelling, between translated words,
  and the source holds the same two words
- **THEN** the draft has no `latin-run` finding, while "Daily Courier" beside it still blocks

#### Scenario: Front matter holds Latin

- **WHEN** a Ukrainian copyright line of a FRONT_MATTER unit keeps the publisher "Harbor Lane Press"
- **THEN** no `latin-run` finding is raised, while the same line in a BODY unit has one

#### Scenario: A mostly Latin target is not a latin-run

- **WHEN** a Ukrainian target is the English source copied back whole
- **THEN** no `latin-run` finding is raised, and the echo, script and language-identity checks decide

### Requirement: Count a name spelling only where the source names the term

The whole-book `name-variants` audit SHALL count a spelling only in a segment whose source names the term and never when
the word is a pronoun, a stop word or written in lower case in the targets. The final audit does not repeat
`name-missing` or `latin-run`.

**Source**: task 15h.A5. In plain words: the Oct 9 runs let "Він" count as a misspelling of "Фінн"; counting only where the source names the term stops that.

#### Scenario: A pronoun is no spelling of a name

- **WHEN** the targets write "Фінн" and "Він" and the glossary maps `Finn` to `Фінн`
- **THEN** no name variant is reported

### Requirement: Check each target word against the target language's alphabet

The deterministic text checks SHALL raise a BLOCK-level `alphabet` finding, naming the word and the letter, for every target word that holds a letter listed under `forbiddenLetters` in the target language's file (Ukrainian: `ыъэё`), except a word the source text holds as well when the source and target scripts differ, a glossary rendering and a kept foreign run; a language with no such key is never checked.

**Source**: task 15j.J5 of the archived translation-workflow change (the alphabet check), `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`, `design.md#d7--language-data-function-word-files-and-rule-keys-for-every-language`. In plain words: on Russian → Ukrainian the source and the target share an alphabet, so the old rule — forgive any word that also stands in the source — let every Russian word through; the exemption now applies only where the scripts differ (a Cyrillic title quoted in an English source). A finding blocks acceptance and earns the one repair.

#### Scenario: A Russian letter in a Ukrainian word is repaired or flagged

- **WHEN** Balanced and a Ukrainian draft holds `бэкона` or `кобыли`
- **THEN** the segment is not accepted as it is: the one repair names the word, and the segment is accepted when the repair removes the letter
- **AND** a draft that still holds the letter after the repair is flagged with the `alphabet` finding, its draft kept as the machine text for the desk and its source written by the export

#### Scenario: A Russian word copied from a Russian source is still found

- **WHEN** Russian → Ukrainian, the source is `Он купил кобылу.` and the draft is `Він купив кобылу.`
- **THEN** the `alphabet` finding names `кобылу` and the letter `ы`, because the scripts match and the source exemption does not apply

#### Scenario: A word that stands in the source is left alone

- **WHEN** English → Ukrainian, the source is `He read “Эхо” aloud.` and the Ukrainian draft is `Він прочитав «Эхо» вголос.`
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

### Requirement: Treat an address mix as a repair-level finding with one repair

The `address-mix` finding SHALL be REPAIR level: it SHALL NOT block acceptance or flag a segment, it earns the one repair at Balanced and Max, and a leftover SHALL be accepted with a proofread mark. A language whose file lists no `informalAddress` and `formalAddress` SHALL NOT be checked.

**Source**: task 15i.T6, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: the mix is advice, not a reason to reject; one repair asks for a single way of address and what stays is marked for the Proofread stage.

#### Scenario: The fix is asked once

- **WHEN** Balanced and a Ukrainian draft holds «Сядь, тобі треба відпочити, а я принесу Вам чаю.» as one speech
- **THEN** one repair is asked for the `address-mix` finding
- **AND** a repair that keeps the mix leaves the draft accepted with a proofread mark

#### Scenario: Fast only marks

- **WHEN** Fast and the same draft
- **THEN** no repair call is made and the segment is accepted with a proofread mark that the desk lists

### Requirement: Give every finding one of three levels: block, repair or note

The application SHALL give every finding one level: BLOCK (the segment cannot be accepted and is FLAGGED if the finding stays), REPAIR (the segment can be accepted, earns the one repair at Balanced and Max, and is accepted with a proofread mark if the finding stays) or NOTE (the segment is accepted with an audit mark and earns no call).

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: the same word, a copied source, a stray letter, a lost question mark, does not deserve the same treatment; the level says whether a segment may be accepted, whether it earns the one model call to fix it, and which mark it carries to the Proofread stage. A level is separate from a finding's severity, which only orders what the person sees.

#### Scenario: A blocking finding that survives the repair flags the segment

- **WHEN** Balanced, Russian → Ukrainian, the source is `Он купил кобылу.`, the draft is `Він купив кобылу.` (`ы` is a letter the Ukrainian file forbids) and the one repair returns `Він купив кобылу.` again
- **THEN** the `alphabet` finding is BLOCK, one repair call was made, and the segment is FLAGGED

#### Scenario: A repair-level finding that survives the repair is accepted with a proofread mark

- **WHEN** Balanced, English → Ukrainian, the source is `Where is the key?`, the draft is `Де ключ.` and the one repair returns `Де ключ.` again
- **THEN** the `terminal-mark` finding is REPAIR and the segment is ACCEPTED with a proofread mark carrying it

#### Scenario: A note-level finding earns no call

- **WHEN** English → Ukrainian, the source is `He waited.` and the draft is `Він чекав 5 хвилин.`
- **THEN** the extra number is a NOTE, no repair call is made, and the segment is ACCEPTED with an audit mark

#### Scenario: At Fast a repair-level finding becomes a mark for the desk

- **WHEN** Fast and the same draft `Де ключ.` for `Where is the key?`
- **THEN** no repair call is made and the segment is ACCEPTED with a proofread mark that the review desk lists

### Requirement: Strip stress marks from every candidate before any check counts

WHERE the target language sets `stripStressMarks`, the application SHALL, before any check counts, remove from every candidate target the acute and grave accent marks (decomposing the text first and recomposing it after, so that `й` and `ї` stay), the soft hyphen, and an acute sign right after a Cyrillic vowel, and SHALL log how many it removed.

**Source**: `design.md#d12--checks-levels-and-code-repairs-before-checks`, `design.md#d4--name-forms-stress-marks-and-target-validation`. In plain words: models sometimes write stress marks (`мі́сяць`), which no reader of a book wants and which break the exact-text checks; code takes them out of every candidate, and a language that writes accents as part of its letters (Bulgarian `ѝ`) simply does not set the key. Nothing in the code stripped stress marks before this change.

#### Scenario: Accents are removed and the letters stay

- **WHEN** the Ukrainian candidate is `Мі́сяць зійшо́в над ї́жаком.`
- **THEN** the stored candidate is `Місяць зійшов над їжаком.`, with `ї` intact, and a DEBUG log line gives the count 3

#### Scenario: A grave accent is removed too

- **WHEN** the Ukrainian candidate is `на́ша ма̀ма`
- **THEN** the stored candidate is `наша мама`

#### Scenario: A sign after a consonant is an apostrophe, not a stress mark

- **WHEN** the Ukrainian candidate is `п´ять` (an acute sign after the consonant `п`, before `я`)
- **THEN** the sign is not removed as a stress mark, and the apostrophe rule writes `п’ять`

#### Scenario: A language that does not set the key keeps its accents

- **WHEN** the target language is Bulgarian, whose file does not set `stripStressMarks`, and the candidate holds `ѝ`
- **THEN** the candidate is unchanged

### Requirement: Put back a leading dialogue dash that the source has and the target lost

WHERE the target language sets `dialogue-dash`, WHEN a source paragraph starts with a dash (`-`, `–`, `―`, `‒` or `—`) and its target starts with a letter, the application SHALL put `— ` before the target; WHEN the target starts with a quote mark instead, it SHALL record a NOTE and change nothing.

**Source**: `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a speech line that lost its dash reads as narration; code restores it where the book's own source shows the dash, and only for a target language whose speech is written with dashes, so an English target never gets one.

#### Scenario: A lost dash is put back

- **WHEN** the target language sets `dialogue-dash`, the source is `— Where is the key?` and the target is `Де ключ?`
- **THEN** the stored target is `— Де ключ?`, with no model call

#### Scenario: A hyphen at the start of the source counts as a dash

- **WHEN** the source is `- Come in.` and the target is `Заходьте.`
- **THEN** the stored target is `— Заходьте.`

#### Scenario: A target that starts with a quote is only noted

- **WHEN** the source is `— Where is the key?` and the target is `«Де ключ?»`
- **THEN** the target is unchanged and a NOTE is recorded

#### Scenario: A target language without the key never gets a dash

- **WHEN** Russian → English, the source is `— Где ключ?` and the target is `Where is the key?`
- **THEN** the target is unchanged

### Requirement: Write dialogue dashes in one form

WHERE the target language sets `dialogue-dash`, the application SHALL write a dash at the start of a line as `— ` followed by the text, and a dash that follows `,`, `?`, `!`, `…` or `»` and precedes a letter as ` — `, and SHALL NOT change a hyphen inside a word or between digits.

**Source**: `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: models glue the dash to the next word or to the comma (`—Іди,-сказав він`); code writes it the way the book's dialogue is typeset, and never touches `будь-хто` or `10-20`.

#### Scenario: A glued dash at the start and after a comma are spaced

- **WHEN** the target language sets `dialogue-dash` and the target is `—Іди геть,-сказав він.`
- **THEN** the stored target is `— Іди геть, — сказав він.`

#### Scenario: A hyphen inside a word stays

- **WHEN** the target is `— Будь-хто міг це зробити.`
- **THEN** the target is unchanged

#### Scenario: A hyphen between digits stays

- **WHEN** the target is `У 1990-2000 роках.`
- **THEN** the target is unchanged

### Requirement: Put back a heading number that the target lost

WHEN a source paragraph starts with digits or a Roman numeral followed by `.`, `)` or `:` and its target starts with no number, the application SHALL put the source's number and its mark before the target; a bare `I` followed by a space SHALL NOT count as a numeral, and a Roman numeral SHALL count only when it has two letters or more and no sentence follows it in the source, so a person's initial (`M. Poirot smiled.`) is never put before a target.

**Source**: `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a chapter heading `4. Ночной перевал` must keep its `4.` whatever the model did with it; code puts it back, so the segment never needs a model call for it. A lost number is not a different number: a target that starts with another number is the separate blocking case.

#### Scenario: A lost digit number is restored with no model call

- **WHEN** the source is `4. Ночной перевал` and the draft is `Нічний перевал`
- **THEN** the stored target is `4. Нічний перевал` and no model call is made

#### Scenario: A Roman numeral is restored

- **WHEN** the source is `XII) Возвращение` and the draft is `Повернення`
- **THEN** the stored target is `XII) Повернення`

#### Scenario: A Latin initial is not a numeral

- **WHEN** the source is `M. Poirot smiled.` and the draft is `Пуаро усміхнувся.`
- **THEN** the draft is unchanged

#### Scenario: The pronoun I is not a numeral

- **WHEN** the source is `I opened it.` and the draft is `Я відчинив це.`
- **THEN** the draft is unchanged

#### Scenario: A target that already starts with the number is unchanged

- **WHEN** the source is `4. Ночной перевал` and the draft is `4. Нічний перевал`
- **THEN** the draft is unchanged

### Requirement: Block a word written wholly in a script the target does not use

The application SHALL raise a BLOCK-level `script-purity` finding for a target word written wholly in a script that is neither the target language's, nor Latin, nor common, and for CJK punctuation (U+3000–303F) or fullwidth forms (U+FF00–FFEF), when the source segment does not hold the same word or sign.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: small models sometimes drop a Chinese or Arabic word into a Ukrainian sentence; the mixed-script check sees only a word that mixes alphabets, so this check sees the word that is wholly foreign. A Latin name is fine, and a word the source itself holds is the book's own.

#### Scenario: A Chinese word in a Ukrainian sentence blocks

- **WHEN** English → Ukrainian and the draft is `Він сказав слово 门 і пішов.` for a source without that word
- **THEN** the segment carries a BLOCK `script-purity` finding naming `门`

#### Scenario: A word the source holds is left alone

- **WHEN** the source is `He wrote 门 on the wall.` and the draft is `Він написав 门 на стіні.`
- **THEN** no `script-purity` finding is raised

#### Scenario: A fullwidth sign the source lacks blocks

- **WHEN** the draft is `Він пішов。` and the source holds no such sign
- **THEN** the segment carries a BLOCK `script-purity` finding naming `。`

#### Scenario: A Latin name is not a foreign script

- **WHEN** the draft is `Він зустрів Mara Quill.`
- **THEN** no `script-purity` finding is raised

### Requirement: Count source-only function words in a target as untranslated residue

The application SHALL compute, for each language pair at the start of a run, the source-only set (the source language's function words minus the target language's), SHALL count the whole-word, lower-case hits of that set in each target, with no source exemption, and SHALL raise one `function-word-residue` finding naming the words, at REPAIR level, or at NOTE level for a pair whose language data marks residue as note-level (set when the language-data replay measured a precision below 0.9).

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d7--language-data-function-word-files-and-rule-keys-for-every-language`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: on Russian → Ukrainian a line left half in Russian shares its alphabet with the target, so no letter check sees it; the Russian words that Ukrainian does not also use (`что`, `это`) betray it. The set is computed from each language's own function-word file, never from a list of one pair, so a word both languages use (`просто`, `тебе`) never counts. A hit is a marked segment to repair, not a wrong-language paragraph, so the segment keeps its draft; it is its own finding, separate from the `language` finding of a paragraph in the wrong language. REPAIR is the shipped default, and a pair is made note-level only by its measured data, so a pair nobody measured stays REPAIR.

#### Scenario: A Russian word in a Ukrainian draft earns one repair

- **WHEN** Balanced, Russian → Ukrainian, the source is `Они сказали, что ключ в кармане.` and the draft is `Вони сказали, что ключ у кишені.`
- **THEN** a REPAIR `function-word-residue` finding names `что`, and one repair call follows

#### Scenario: A word both languages use is no residue

- **WHEN** the Ukrainian function-word file lists `просто` and the draft is `Він просто відчинив двері.`
- **THEN** no finding is raised

#### Scenario: A single hit does not make the paragraph a wrong-language paragraph

- **WHEN** the draft is a 14-word Ukrainian paragraph holding one source-only word
- **THEN** the segment keeps its draft as the machine text and is never treated as an English leftover

#### Scenario: A one-letter word counts only before a lower-case word

- **WHEN** `и` is in the source-only set, the draft is `Вона відчинила и пішла.` in one case and `Іван и Марта пішли.` in another
- **THEN** the first draft raises a finding naming `и` and the second does not

#### Scenario: A replay below 0.9 makes the hit a note

- **WHEN** the pair's language data marks residue as note-level, because its replay measured a precision of 0.82, and the draft holds `что`
- **THEN** the finding is a NOTE: no repair call is made and the segment is ACCEPTED with an audit mark

#### Scenario: A pair without function-word files raises nothing

- **WHEN** the target language has no function-word file
- **THEN** no residue finding is raised for any draft of that pair

### Requirement: Block a target that starts with a different number than its source

IF a target starts with a number that differs in value from the number its source starts with, THEN the application SHALL raise a BLOCK-level `number` finding naming both numbers.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a heading `4. Ночной перевал` that comes back as `5. Нічний перевал` has silently renumbered the book; a number that was merely lost is restored by code first, so only a replaced number reaches this check.

#### Scenario: A replaced heading number blocks

- **WHEN** the source is `4. Ночной перевал` and the draft is `5. Нічний перевал`
- **THEN** the segment carries a BLOCK `number` finding naming `4` and `5`

#### Scenario: The same number written another way does not block

- **WHEN** the source is `1,200 soldiers` and the draft is `1 200 солдатів`
- **THEN** no `number` finding is raised

### Requirement: Compare numbers by value and report a missing or extra number by its size

The application SHALL compare the numbers of a source and its target by value (`1,200` equals `1 200`, `3.5` equals `3,5`), SHALL raise a REPAIR-level `number` finding for a number of two or more digits that the target lacks with no replacement, SHALL raise a NOTE-level one for an extra number when none is missing, and SHALL NOT compare spelled-out numbers.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a lost `40` is a fact lost from the story and earns the one repair; an extra number may be a legitimate rewording, so it only marks the segment for the audit. Words such as `forty` are not compared, because their forms differ by language.

#### Scenario: A missing two-digit number earns the one repair

- **WHEN** Balanced, the source is `He paid 40 coins.` and the draft is `Він заплатив монетами.`
- **THEN** a REPAIR `number` finding names `40`, and one repair call follows

#### Scenario: An extra number is only a note

- **WHEN** the source is `He waited.` and the draft is `Він чекав 5 хвилин.`
- **THEN** a NOTE `number` finding names `5` and no repair call is made

#### Scenario: A decimal written the target's way matches

- **WHEN** the source is `It weighed 3.5 kilos.` and the draft is `Воно важило 3,5 кіло.`
- **THEN** no `number` finding is raised

#### Scenario: A spelled-out number is not compared

- **WHEN** the source is `He paid forty coins.` and the draft is `Він заплатив сорок монет.` or `Він заплатив монетами.`
- **THEN** no `number` finding is raised for either draft

### Requirement: Compare the last mark of a paragraph with its source's

WHEN the class of a target paragraph's last mark (`?`, `!`, `.` or `…`, with quotes and closing brackets ignored) differs from its source's, the application SHALL raise a REPAIR-level `terminal-mark` finding; an ellipsis against a full stop counts as a difference.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: most of the times a model drops the end of a paragraph, it also drops its question mark; comparing the last mark finds those omissions cheaply. Quotes are ignored so that a different quotation style is not a defect.

#### Scenario: A lost question mark is a finding

- **WHEN** the source is `Where is the key?` and the draft is `Де ключ.`
- **THEN** a REPAIR `terminal-mark` finding is raised

#### Scenario: Quotes around the mark are ignored

- **WHEN** the source is `“Where is the key?”` and the draft is `«Де ключ?»`
- **THEN** no `terminal-mark` finding is raised

#### Scenario: An ellipsis against a full stop is a difference

- **WHEN** the source is `He waited…` and the draft is `Він чекав.`
- **THEN** a REPAIR `terminal-mark` finding is raised

### Requirement: Keep a speech's form of address from changing between informal and formal

WHERE the source and the target language files both list informal and formal address forms, WHEN a source speech uses only informal forms (or only formal ones) and its target speech uses forms of the other kind, the application SHALL raise a REPAIR-level `address-parity` finding; a source speech that mixes both kinds is not judged.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a Russian `— Ты придёшь?` that becomes a polite `— Ви прийдете?` changes how two characters stand to each other; the check compares with the source, which the address-mix check (target only) cannot. It is off for any pair where either language lists no address forms.

#### Scenario: A switch from informal to formal is a finding

- **WHEN** Russian → Ukrainian, the source speech is `— Ты придёшь?` and the target speech is `— Ви прийдете?`
- **THEN** a REPAIR `address-parity` finding is raised

#### Scenario: A matching form is fine

- **WHEN** the source speech is `— Ты придёшь?` and the target speech is `— Ти прийдеш?`
- **THEN** no finding is raised

#### Scenario: A mixed source speech is not judged

- **WHEN** the source speech uses both a `ты` form and a `вы` form
- **THEN** no `address-parity` finding is raised

#### Scenario: A pair where one language lists no address forms is not checked

- **WHEN** English → Ukrainian and the English file lists no address forms
- **THEN** no `address-parity` finding is raised

### Requirement: Doubt a segment that lost two or more negations

WHERE the pair declares that negations are counted and both language files list negation words, WHEN a source paragraph holds at least two more negation words than its target, the audit SHALL doubt the segment with a NOTE-level `negation` finding, and the draft stage SHALL make no call for it.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a lost `не` turns a sentence into its opposite, but counting negations across two languages is right only for a close pair and wrong as a finding on its own (about one hit in six was a real loss), so it is advice for the Proofread stage, never a repair. It is off for English → Ukrainian, where double negation differs.

#### Scenario: Two lost negations are doubted

- **WHEN** Russian → Ukrainian, the pair counts negations, the source holds 5 negation words and the target holds 3
- **THEN** the audit doubts the segment with a `negation` finding, and no call was made for it while drafting

#### Scenario: One lost negation is not doubted

- **WHEN** the source holds 5 negation words and the target holds 4
- **THEN** no `negation` finding is raised

#### Scenario: A pair that does not count negations is never doubted

- **WHEN** English → Ukrainian, the source holds 4 negation words and the target holds 1
- **THEN** no `negation` finding is raised

### Requirement: Doubt the minority target of a repeated source paragraph

WHEN two or more paragraphs have the same normalised source text of at least six words but different normalised targets, the audit SHALL doubt each paragraph that holds the less used target.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a refrain, a sign or a repeated line should read the same each time; when it was translated two ways, the odd one out is the one worth a second look.

#### Scenario: The odd translation of a repeated line is doubted

- **WHEN** `The gate stayed shut until the morning came.` occurs three times, translated `Брама лишалася зачиненою до ранку.` twice and `Брама була замкнена до світанку.` once
- **THEN** the audit doubts the paragraph that holds `Брама була замкнена до світанку.`

#### Scenario: A short line is not compared

- **WHEN** `The gate stayed shut.` (4 words) occurs twice with two different targets
- **THEN** no doubt is raised

#### Scenario: Differences in spacing or case alone are no difference

- **WHEN** the same six-word source occurs twice and the targets differ only in a space and a capital letter
- **THEN** no doubt is raised

### Requirement: Give a failing segment one repair at Balanced and Max

WHEN a drafted segment holds a BLOCK or REPAIR finding, the application SHALL at Balanced and Max send one repair call, the segment's own draft prompt and window plus its previous attempt and its defects, asking for it to be translated again at a low temperature, and SHALL make no further content-changing call for it; at Fast it SHALL make none.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: the earlier flow could spend up to eight calls on one stubborn segment; the research found that a second and third round rarely bring a better text, so a segment gets one well-informed second try with the same context as its draft, and what stays wrong is marked for the Proofread stage. The dial never depends on the model: Max repairs exactly as Balanced does.

#### Scenario: One repair fixes a draft

- **WHEN** Balanced, Russian → Ukrainian, the draft `Вони сказали, что ключ у кишені.` carries a REPAIR finding naming `что`
- **THEN** one repair call is sent with the window, the previous attempt and the defect `что`, it returns `Вони сказали, що ключ у кишені.`, and the segment is ACCEPTED

#### Scenario: A repair that fails gets no second repair

- **WHEN** Balanced and the repair still holds `что`
- **THEN** no further call is made for the segment and its outcome is read from the findings left

#### Scenario: Max repairs exactly as Balanced does

- **WHEN** Max and the same draft
- **THEN** one repair call is made, no more

#### Scenario: Fast makes no repair call

- **WHEN** Fast and the same draft
- **THEN** no repair call is made

#### Scenario: A segment with only notes gets no repair

- **WHEN** Balanced and the draft carries only a NOTE-level `number` finding
- **THEN** no repair call is made

#### Scenario: A verified reviewer blocker left gets no repair

- **WHEN** Balanced, the draft carries a REPAIR finding naming `что`, and the reviewer's quoted issue `дверзі` is still in the text after its directed fix
- **THEN** no repair call is made and the segment is FLAGGED, because no repair the code can check clears the reviewer's evidence

### Requirement: Count only content-changing calls as the one repair

The application SHALL NOT count as the one repair a code repair, the one format re-ask for an unreadable reply or for ids a batch reply missed, a transport retry, or the first draft of an id a batch reply missed; it SHALL count a placeholder repair and the re-draft of a batch item that came back but failed its gate as the one repair.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: the repair budget is about changing the text because a check failed; fixing the envelope around a reply, or asking again after a network hiccup, does not use it up. A placeholder repair is a content change, so it is the one repair rather than an extra call beside it.

#### Scenario: A format re-ask does not use the repair

- **WHEN** Balanced and a draft reply is not valid JSON, the re-ask returns a valid reply, and a check then fails
- **THEN** the segment still gets its one repair, so up to three calls were made: draft, format re-ask and repair

#### Scenario: A missing id is drafted again without using the repair

- **WHEN** a batch of 4 comes back without the id of segment 3
- **THEN** segment 3 is drafted alone and its repair is still available

#### Scenario: A timeout retry is not a repair

- **WHEN** a draft call times out and is sent again by the transport
- **THEN** the segment's one repair is still available

#### Scenario: A lost placeholder is repaired by the one repair

- **WHEN** Balanced and the draft loses `⟦g0⟧`
- **THEN** the one repair names `⟦g0⟧` as the expected token, and no separate placeholder repair is made

### Requirement: Name every defect with its quoted span in the repair

The repair call SHALL list every BLOCK and REPAIR finding of the segment with its plain explanation and the exact span it quotes, and, when formatting marks failed, the marks the segment must hold.

**Source**: `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a model told only that its text was wrong improves little; told which word and why, and which marks are missing, it can fix just that.

#### Scenario: Two defects are listed with their spans

- **WHEN** the draft `Вони сказали, что ключ у кишені, та пішли.` carries a REPAIR `function-word-residue` finding naming `что` and a REPAIR `terminal-mark` finding
- **THEN** the repair request lists both with their explanations and the span `что`

#### Scenario: Missing marks are named

- **WHEN** the draft lost `⟦g1⟧`
- **THEN** the repair request names the tokens `⟦g0⟧` and `⟦g1⟧` as the marks the target must hold

### Requirement: Keep a repair only when it passes the hard gates and the never-worse gate keeps it

The application SHALL replace a segment's draft by its repair only when the repair passes the hard gates (placeholder, protected span, typography) and the never-worse gate keeps it, SHALL otherwise keep the draft, and SHALL NOT let the acceptance rule alone decide it.

**Source**: `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`, `design.md#d14--the-never-worse-gate`. In plain words: a draft with only repair-level findings already passes the acceptance rule, so 'accepted' would let a worse repair in; one rule for every repair keeps the best text the segment has had, and there are no further rounds to try.

#### Scenario: A repair that clears the defect replaces the draft

- **WHEN** Balanced and the repair of `Вони сказали, что ключ у кишені.` is `Вони сказали, що ключ у кишені.`
- **THEN** the repair becomes the target

#### Scenario: A repair that adds a defect is not kept

- **WHEN** Russian → Ukrainian, the source is `Где ключ и письмо?`, the draft `Де ключ і лист.` carries a `terminal-mark` finding, and the repair is `Де ключ и лист?`, which fixes the mark but adds the source-only word `и`
- **THEN** the draft stays and its marks stay

#### Scenario: A repair that breaks a placeholder is not kept

- **WHEN** the repair drops `⟦g0⟧`
- **THEN** the draft stays

#### Scenario: A repair that changes nothing is not kept

- **WHEN** the repair equals the draft
- **THEN** the draft stays and no further call is made

### Requirement: Keep the repair of a text that is no translation when it has fewer blockers

WHEN the text a repair would replace stands on no machine translation — a refusal, an echo of the source, a reply in the wrong language or one whose formatting marks were lost — the application SHALL keep a repair that passes the hard gates, differs from that text and holds no more blocking checks, without the never-worse gate's text vetoes.

**Source**: `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`, `design.md#d14--the-never-worse-gate`. In plain words: the never-worse vetoes protect a translation's quotes, clauses, numbers and names; a refusal or an untranslated echo has none worth protecting, so comparing them would throw away a good repair because the refusal held a comma.

#### Scenario: A refusal is replaced by its repair

- **WHEN** Balanced, the draft of `He opened the old door.` is `I'm sorry, but I can't translate this text.` and the repair is `Він відчинив старі двері.`
- **THEN** the repair becomes the target and the segment is ACCEPTED

#### Scenario: A repair that echoes the source again is not kept

- **WHEN** the draft is the echo `HE OPENED THE OLD DOOR.` and the repair is the same echo
- **THEN** the draft stays and the segment is FLAGGED

### Requirement: Read a segment's outcome from the findings left after the one repair

After the one repair, or on the draft at Fast, the application SHALL read the findings left on the text that stays: a BLOCK finding means FLAGGED; only REPAIR-level findings mean ACCEPTED with a proofread mark; only NOTE-level findings mean ACCEPTED with an audit mark; none means ACCEPTED with no mark.

**Source**: `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the table is the one place that says what a leftover finding does, so the review desk, the Proofread stage and the export all agree on what is marked.

#### Scenario: A blocking finding left flags the segment

- **WHEN** the text that stays holds a BLOCK `alphabet` finding
- **THEN** the segment is FLAGGED and the review desk and the Proofread stage both list it

#### Scenario: Repair-level leftovers are accepted with a proofread mark

- **WHEN** the text that stays holds only a REPAIR `terminal-mark` finding
- **THEN** the segment is ACCEPTED with a proofread mark naming that finding

#### Scenario: Note-level leftovers are accepted with an audit mark

- **WHEN** the text that stays holds only a NOTE `number` finding
- **THEN** the segment is ACCEPTED with an audit mark

#### Scenario: No finding means no mark

- **WHEN** the text that stays holds no finding
- **THEN** the segment is ACCEPTED with no mark

### Requirement: Make no fixing call at Fast and mark the segment instead

At the Fast dial the application SHALL make no repair call, SHALL read the outcome table on the draft and leave its marks for the review desk, and SHALL send one re-draft only for a piece whose formatting marks are still lost after the code restore, keeping that re-draft only by the same keep rule.

**Source**: `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: Fast is a draft plus free checks: no hidden extra calls. Today Fast spends a repair round; this change removes it. A lost formatting mark would break the export, so it alone earns one retry.

#### Scenario: A failing draft is marked, not repaired

- **WHEN** Fast and the draft of `He opened the old door.` is `He opened the old door.`
- **THEN** no call follows the draft, the segment is FLAGGED and the review desk lists it

#### Scenario: Lost formatting marks earn one re-draft

- **WHEN** Fast and the draft for `See ⟦g0⟧ here.` is `Дивіться тут.` and the code restore cannot put `⟦g0⟧` back
- **THEN** one re-draft is sent, and it is kept when it holds `⟦g0⟧` and passes the keep rule

### Requirement: Flag a segment whose formatting marks stay lost after the one re-draft

IF the re-draft of a segment still loses formatting marks, or the keep rule rejects it, THEN the application SHALL, at every dial, keep no machine target for it, store the last reply as its rejected target, FLAG the segment with its placeholder BLOCK for the review desk, and try nothing else.

**Source**: `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a book can still be written: export fails the segment's own validation and writes its source text there, says so in a banner, and the person fixes it on the desk.

#### Scenario: Fast gives up after one re-draft

- **WHEN** Fast and the re-draft of `See ⟦g0⟧ here.` is `Дивіться тут.` again
- **THEN** the segment is FLAGGED with a `markup` finding and no further call is made

#### Scenario: Balanced gives up when the one repair fails on marks

- **WHEN** Balanced and the one repair also lacks `⟦g0⟧`
- **THEN** the segment is FLAGGED and no further call is made

### Requirement: Reject a candidate text that breaks a never-worse veto

The never-worse gate SHALL reject a candidate text, keeping the current one, when the candidate fails a hard gate, adds a BLOCK finding, adds a source-language word, writes a name in a form the book map does not list for it, drops a map name, adds a Latin run or a stress mark, or has fewer sentences, negation words, numbers, quotes, dashes or clauses than both the current text and the source.

**Source**: `design.md#d14--the-never-worse-gate`. In plain words: every edit the program makes after a draft (the one repair, a proofread rewrite) must pass this one test, so a fix can never cost the book something it had. The earlier reviewer edits were checked by three different 'no new blocker' rules; this is the single replacement. A count is compared with the current text and the source, so a veto fires only when the candidate loses what the source holds: a repair that replaces a wrong number with the source's, removes a stray quote or drops a doubled sentence is kept.

#### Scenario: A hard-gate failure is rejected

- **WHEN** a candidate drops `⟦g0⟧` that the current text holds
- **THEN** the gate rejects it

#### Scenario: A new blocking finding is rejected

- **WHEN** the current text has no BLOCK finding and the candidate holds a letter `ы` in a Ukrainian word
- **THEN** the gate rejects it

#### Scenario: A source-language word added is rejected

- **WHEN** the current text is `Вони сказали, що ключ у кишені.` and the candidate is `Вони сказали, что ключ у кишені.`
- **THEN** the gate rejects it

#### Scenario: A name form outside the map is rejected

- **WHEN** the map lists `Велемир`, `Велемира` and `Велемирові` for the pinned name Велемир, and the candidate writes `Велемер`
- **THEN** the gate rejects it

#### Scenario: A dropped map name is rejected

- **WHEN** the source names Велемир, the current text has `Велемир` and the candidate has no form of the name
- **THEN** the gate rejects it

#### Scenario: A lower sentence count is rejected

- **WHEN** the source and the current text have 3 sentences and the candidate has 2
- **THEN** the gate rejects it

#### Scenario: A lost negation is rejected

- **WHEN** the current text holds `не` twice, the candidate holds it once, and the source holds two negation words or its language has no negation list
- **THEN** the gate rejects it

#### Scenario: A lost number is rejected

- **WHEN** the source and the current text hold `40` and the candidate does not
- **THEN** the gate rejects it

#### Scenario: A wrong number replaced by the source's is kept

- **WHEN** the source is `3. Night pass`, the current text `5. Нічний перевал` and the candidate `3. Нічний перевал`
- **THEN** no veto rejects it, because `5` is a number the source does not hold

#### Scenario: A stray quote or a doubled sentence removed is kept

- **WHEN** the source holds no quote mark and one sentence, and the candidate removes a stray `«` or a doubled sentence of the current text
- **THEN** no veto rejects it, because the candidate does not fall below the source

#### Scenario: A new stress mark is rejected

- **WHEN** the candidate holds `ма́ма` where the current text holds `мама`
- **THEN** the gate rejects it

### Requirement: Keep a candidate text only when its score is not worse and its mark is cleared

WHEN no veto applies, the application SHALL keep a candidate only when its score (BLOCK findings weigh most, then REPAIR findings, then notes and audit doubts) is not higher than the current text's and either the marking finding is cleared or the score is strictly lower; in the whole-book re-read it SHALL also keep, at an equal score, a candidate for an unmarked paragraph that changes no more words than the rewrite cap allows; otherwise the current text and its marks stay.

**Source**: `design.md#d14--the-never-worse-gate`. In plain words: a change that fixes the flagged problem without making anything else worse is kept, and a change that only moves words around is not; the score is a code count, because no judge model is available at run time.

#### Scenario: A candidate that clears the marked finding is kept

- **WHEN** the current text holds one REPAIR finding and the candidate holds none and passes every veto
- **THEN** the candidate is kept

#### Scenario: A candidate that swaps a blocking finding for a soft one is kept

- **WHEN** the current text holds a BLOCK `alphabet` finding on `кобылу`, the candidate writes `кобилу`, clearing it, but holds a REPAIR `gender` finding, and no counted check's finding count rises
- **THEN** the candidate is kept because its score is lower

#### Scenario: A candidate with an equal score that clears nothing is not kept

- **WHEN** the current text and the candidate both hold the marked REPAIR finding
- **THEN** the current text stays with its mark

#### Scenario: A small rewrite of an unmarked paragraph in the whole-book re-read is kept at an equal score

- **WHEN** the paragraph holds no mark, the candidate changes 2 of its 20 words and the score is equal
- **THEN** the candidate is kept

#### Scenario: A large rewrite of an unmarked paragraph is not kept at an equal score

- **WHEN** the paragraph holds no mark, the candidate changes 14 of its 20 words and the score is equal
- **THEN** the current text stays

### Requirement: Let no per-check finding count rise in a kept candidate

The never-worse gate SHALL reject a candidate when the number of findings of any one of the checks alphabet, language-identity, function-word residue, script-purity, number, terminal-mark, address-parity and sentence-count is higher than in the current text, even when the candidate clears the finding that marked the segment.

**Source**: `design.md#d14--the-never-worse-gate`, `design.md#d12--checks-levels-and-code-repairs-before-checks`. In plain words: a repair that cures one defect by adding another of a kind the checks count, a new number finding for a fixed Russian word, is no improvement, so it is not kept.

#### Scenario: A repair that cures one finding and adds a number finding is rejected

- **WHEN** the repair clears the `function-word-residue` finding on `что` but loses the number `40`, raising a `number` finding
- **THEN** the gate rejects the repair and the draft stays

#### Scenario: A repair that lowers one count and raises none is kept

- **WHEN** the repair clears the `function-word-residue` finding and every other count stays equal
- **THEN** the gate does not reject it
