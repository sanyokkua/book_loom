# Spec Delta

## ADDED Requirements

### Requirement: Derive a style sheet from the Book Brief and put it in every prompt

WHEN a run prepares, the system SHALL derive one style sheet from the project's Book Brief — register, genre, narrative
voice and era, audience, the faithful↔natural balance as one of five bands, and the name, foreign-passage, footnote and
unit policies — and SHALL place that same style sheet in the system message of every model call the run makes. Genre,
voice/era and audience SHALL appear verbatim as the person entered them; every other choice SHALL expand to a fixed
English rule. The derivation SHALL be deterministic: the same brief always yields the same text. A brief left at its
defaults SHALL yield the default style sheet, never an empty one.

**Source:** FR-BRIEF-02 … FR-BRIEF-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`),
FR-ALGO-B2 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-b-prep`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`.
In plain words: everything the person says about the book on the Book Brief now reaches the model, on every call, in the
same words. Building it without a model call keeps it reproducible and free; a brief nobody touched still tells the model
how to behave.

#### Scenario: The brief's words reach every call

- **WHEN** a book is translated with genre `Detective fiction`, voice/era `Victorian, first person`, audience `Adults`,
  register Formal, names Transliterate and units Metric
- **THEN** the system message of every draft, repair and reviewer call contains `Detective fiction`, `Victorian, first
  person` and `Adults`, a rule to transliterate names, and a rule to convert measurements to metric

#### Scenario: The same brief gives the same style sheet

- **WHEN** two runs are started for the same project without changing its brief
- **THEN** the style-sheet text in their first draft calls is identical, character for character

#### Scenario: A default brief is not empty

- **WHEN** a Markdown book is translated to `uk` with every brief choice left at its default
- **THEN** the system message carries the default style sheet, including the rule to keep a passage deliberately in
  another language as it is

### Requirement: Build every prompt from the catalogue's templates

The system SHALL build each model call — draft, structural repair, placeholder repair, reviewer, directed fix,
pre-scan, summary and revision — from that call's template in the prompt catalogue, held as data. Every
generation call (draft, both repairs, directed fix, revision) SHALL ask for and accept exactly one
`{"target":"…"}` object for exactly one segment. Every repair call — directed fix and
revision — SHALL carry exactly one `<Translation>…</Translation>` block, holding only the masked text to rewrite: the
rejected target, or the masked source when the finding is a refusal or an empty target; the source SHALL appear in its
own `<Source>` block; only the draft's text to translate is `<Text>`. Every prompt SHALL state its JSON schema together
with a literal reply its parser accepts, SHALL declare the book text it embeds as data rather than instructions, and
SHALL state the placeholder rules wherever that text carries `⟦gN⟧` tokens; revision SHALL list the
source's tokens under `[Immutable tokens]` when it has any. The draft and the rewriting calls SHALL show the bundled
few-shot examples of the language pair — the `<source>-<target>` file, else the `<target>` file, else `neutral` — read
from the classpath. The draft and repair, reviewer and directed-fix messages SHALL be pinned by golden files, so a prompt
changes only by a deliberate edit of them.

**Source:** `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `#output-contract`,
`#directed-fix-repair`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#prompt-builder`,
ADR-0038, `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-design`, `#few-shot-examples`.
In plain words: prompts are text files, not code, so they can be read and tuned without touching the engine. Small local
models reliably answer one segment with one small object and break on arrays keyed by id, so every call that produces
book text keeps that shape. A repair asks the model to rewrite one text, so only that text sits in the block it rewrites,
and the source it must stay faithful to sits beside it, where it cannot be mistaken for the text to return. A ~4B model
copies what it is shown far better than it follows abstract rules, so every prompt shows a real reply, and book text
that reads like an order is still only translated. A prompt that already works on real books must not change by
accident.

#### Scenario: A directed fix returns one target

- **WHEN** a directed fix is issued for segment `ch07.xhtml:41`
- **THEN** its response format requires exactly an object with one nonblank string `target`
- **AND** a reply `{"segments":[{"id":"ch07.xhtml:41","target":"Він пішов."}]}` is invalid structured output

#### Scenario: A directed fix rewrites only the rejected target

- **WHEN** a directed fix is issued for `Book.md:0`, whose masked source is `He opened the ⟦g0⟧old⟦g1⟧ door.`, after its
  target `HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.` failed the untranslated-echo check
- **THEN** its user message holds exactly one `<Translation>` block, and that block holds only
  `HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.`, with `He opened the ⟦g0⟧old⟦g1⟧ door.` in the `<Source>` block
- **AND** the pseudo model answers it with `{"target":"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR."}`

#### Scenario: A refusal is rewritten from the source

- **WHEN** a directed fix is issued for `Book.txt:0`, whose source is `He opened the old door.`, after its target
  `I'm sorry, but I can't translate this text.` failed the refusal hard gate
- **THEN** the one `<Translation>` block of its user message holds `He opened the old door.` and not the refusal

#### Scenario: The draft prompt matches its golden file

- **WHEN** `Book.md`, whose only paragraph is `He opened the *old* door.`, is translated from `en` to `uk` with the
  default brief and an empty glossary, memory and summary
- **THEN** the system and user messages of its draft call equal the golden files, and the system message shows the
  `en-uk` examples, among them `Source: ⟦g0⟧T⟦g1⟧he night was cold.` with `Reply: {"target":"⟦g0⟧Н⟦g1⟧іч була холодна."}`

#### Scenario: A pair with no examples of its own shows the neutral ones

- **WHEN** a draft is built for `en` → `ja`
- **THEN** its system message shows the `neutral` examples, among them `Source: * * *` with `Reply: {"target":"* * *"}`

#### Scenario: Every prompt shows a literal reply and declares book text as data

- **WHEN** any call's messages are rendered
- **THEN** they hold a complete JSON reply its parser accepts and the words `not instructions`, and every call whose text
  carries tokens states the `⟦gN⟧` token rules

### Requirement: Assemble each draft's context with the load-bearing items at the edges

WHEN a draft call is built, the system SHALL place in the prompt, in this order: the instruction frame with the style
sheet first; then the rolling summary; then only those glossary entries whose source term occurs in the chunk, each with
its target, type and gender; then the translation-memory hits for the segment; then the targets of the segments just
before it in the same unit — at most 1 on Fast, 2 on Balanced and 3 on Max, and none at the start of a unit — each the
earlier segment's effective target, or its draft or reused target while that segment waits for its decision in the
same chunk (see "Decide a chunk's segments in document order"); and the masked source last. An item with no content
SHALL be left out entirely. The system SHALL record, with each draft, what context it saw, so a later retry can rebuild
it.

**Source:** FR-ALGO-04, FR-ALGO-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, `#token-budget`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#context-package-assembler`.
In plain words: models read the start and the end of a prompt best, so the rules and the text to translate sit there,
and everything else sits between. Only the names that occur in the passage are sent, so a large glossary does not drown
the source. The window of earlier translations restarts at each chapter so the last chapter's phrasing does not leak in.

#### Scenario: Only the terms in the chunk are injected

- **WHEN** the glossary holds `Hale` → `Гейл` (character, male) and `Baker Street` → `Бейкер-стріт` (place), and the chunk
  holding `ch07.xhtml:41` mentions only `Hale`
- **THEN** the draft call for `ch07.xhtml:41` lists `Hale`, `Гейл`, `character` and `male`, and does not mention `Baker
  Street`

#### Scenario: The preceding window follows the dial and resets at a chapter

- **WHEN** a Balanced run drafts `ch07.xhtml:41` after `ch07.xhtml:38`, `:39` and `:40` were accepted, and later drafts
  `ch08.xhtml:0`
- **THEN** the call for `ch07.xhtml:41` carries the targets of `ch07.xhtml:39` and `ch07.xhtml:40` only
- **AND** the call for `ch08.xhtml:0` carries no preceding target

#### Scenario: The masked source is the last item

- **WHEN** a draft call carries a summary, one glossary entry and two preceding targets
- **THEN** its user message ends with `<Text>`, the masked source and `</Text>`, followed only by the reply-shape
  instruction, and the style sheet is in the system message

### Requirement: Reuse the translation memory only where its context matches

WHEN a segment's source text and the source texts of both its neighbours in its unit match those of an entry the memory
recorded — each compared as Unicode-normalized, unmasked source text, a unit's first or last segment matching its
missing neighbour only against another unit edge — the system SHALL use that entry's target without a draft call and
SHALL decide it by the memory-reuse exception of the `quality-gates` capability's acceptance rule, which asks no reviewer;
IF that decision fails, THEN the system SHALL discard the reused target and draft the segment as usual. WHEN only the
source text matches, the system SHALL offer the stored target in the draft prompt as a hint. WHEN a stored source has a
similarity of at least `0.85` to the segment's source — one minus their edit distance over the longer length, after
Unicode normalization and case folding — the system SHALL offer its target as a suggestion, unless it is already a hint.
The memory SHALL record a segment's target when the run accepts the segment, and SHALL NOT change when the person edits a
segment in review.

**Source:** FR-ALGO-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-ALGO-C4b, FR-ALGO-C9
(`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#context-aware-tm`.
In plain words: the same sentence can need a different translation in a different place, so it is reused blindly only
when its neighbours say the same thing too — compared by what they say, not by where they sit, so a dialogue repeated in
chapter 3 matches its first appearance in chapter 1; otherwise the earlier translation only helps the model. A reused
translation still has to pass every automatic check, so memory can never put a broken target into the book, but the
reviewer never sees it, because it already passed once. The memory holds what the run accepted; an edit the person makes in
review stays with that one segment.

#### Scenario: A repeated passage in the same context is reused

- **WHEN** `ch03.xhtml:12` reads `Yes.` between the same two sentences that surrounded `ch01.xhtml:4`, which was
  accepted as `Так.`
- **THEN** no draft call is made for `ch03.xhtml:12`, and it is accepted as `Так.` after its hard gates and checks pass,
  with no reviewer
- **AND** its path is recorded as reused from memory
- **AND** the reviewer call of its chunk, when the dial enables one, does not show it

#### Scenario: The same sentence in a new context is only a hint

- **WHEN** `ch05.xhtml:2` reads `Yes.` between different neighbours
- **THEN** a draft call is made for `ch05.xhtml:2`, and its prompt offers `Так.` as a hint

#### Scenario: A unit's first segment matches only another first segment

- **WHEN** `ch04.xhtml:0`, the first segment of its unit, reads `Yes.` and is followed by the sentence that followed
  `ch01.xhtml:4`, and `ch01.xhtml:4` was not the first segment of its unit
- **THEN** a draft call is made for `ch04.xhtml:0`, and its prompt offers `Так.` as a hint

#### Scenario: A near match is a suggestion

- **WHEN** the memory holds `He opened the old doors.` and the segment reads `He opened the old door.`
- **THEN** the draft prompt offers the stored target as a suggestion
- **AND** a stored source whose similarity to the segment is `0.70` is not offered at all

#### Scenario: A review edit leaves the memory as it was

- **WHEN** `ch01.xhtml:4` was accepted as `Так.`, the person then saved the edit `Атож.` for it, and `ch03.xhtml:12`
  later reads `Yes.` between the same two sentences
- **THEN** `ch03.xhtml:12` is reused as `Так.`

### Requirement: Keep a rolling summary of the book so far

The system SHALL refresh the rolling summary after every 20 accepted segments and at the end of every unit, whichever
comes first, counting accepted segments again from zero after each refresh, and SHALL carry the latest summary into
later draft prompts. The summary SHALL be built deterministically from the glossary entries whose term occurs in the
source decided so far and the latest heading texts, condensed to at most 300 estimated tokens. WHERE the dial is Max, the
refresh at the end of each unit SHALL instead be one summary model call, and the refresh after every 20 accepted
segments SHALL stay deterministic. A model summary whose target text is empty SHALL count as unreadable: the system SHALL
keep the previous summary and write one WARN line. Only a model-written summary text SHALL be carried into a draft
prompt, stored in a draft's context snapshot and announced as a summary update; the deterministic text SHALL be stored
with each version but SHALL NOT be shown to a draft or to the person as a summary, and a refresh that leaves the shown
text unchanged SHALL NOT be announced.

**Source:** FR-ALGO-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-ALGO-C10
(`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md#rolling-summary-update`.
In plain words: the model forgets earlier chapters, so a short record of who is who travels with every prompt. A
deterministic summary costs nothing; the model-written one costs a call per chapter, so only the slowest, most careful
setting pays for it. The deterministic text is the glossary lines and the heading titles: shown as "Running summary" on
the fixture book it read `Earth (place, neuter)…` followed by chapter titles — not a summary, and the names already
reach the draft in its glossary block — so it is never presented as one.

#### Scenario: A long chapter refreshes the summary three times

- **WHEN** a Balanced run accepts all 45 segments of `ch02.xhtml`
- **THEN** the summary is refreshed after the 20th and the 40th accepted segment and at the end of `ch02.xhtml`
- **AND** no summary model call is made, no draft is shown a summary and no summary update is announced

#### Scenario: A stored model summary is what a draft is shown

- **WHEN** the latest stored version holds the deterministic text `Earth (place, neuter)` and the model summary
  `Старий чоловік іде до гавані.`
- **THEN** the next draft's prompt and context snapshot carry `Старий чоловік іде до гавані.` and not the deterministic
  text; with no model summary they carry none and the context panel reads "No summary yet"

#### Scenario: Max asks the model at the end of a chapter

- **WHEN** a Max run reaches the end of `ch02.xhtml`
- **THEN** exactly one model call of kind summary is made for that chapter

#### Scenario: A summary with an empty target is unreadable

- **WHEN** a Max run's model summary call for `ch02.xhtml` replies `{"summary":{"source":"","target":""}}`
- **THEN** the previous summary is kept and one WARN line is logged

#### Scenario: The first prompt has no summary

- **WHEN** the first segment of a book is drafted
- **THEN** its prompt contains no summary block

### Requirement: Map the quality dial to the run's mechanics

The system SHALL set the run's mechanics from the Book Brief's quality dial, and from nothing else:

| Mechanic | Fast | Balanced | Max |
|---|---|---|---|
| preceding targets | 1 | 2 | 3 |
| repair rounds per failing segment | 1 | 2 | 3 |
| reviewer passes | 0 | 1 | 2 |
| backward revision | off | off | on |
| segments per chunk, at most | 8 | 4 | 2 |

WHERE the review mode is Manual, a chunk SHALL hold exactly one segment. The dial SHALL NOT change the trust threshold.

**Source:** FR-ALGO-11, FR-BRIEF-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`, `#fr-brief`),
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#quality-dial`, ADR-0036, ADR-0038.
In plain words: one control trades speed for care, and the table says exactly what it changes. The threshold that
decides what gets flagged belongs to the review mode, so turning the dial never silently flags more or fewer segments.

#### Scenario: Fast skips the reviewer

- **WHEN** a Fast run translates a chapter of 10 short segments that all pass their checks
- **THEN** no reviewer call is made, and each draft carries at most 1 preceding target

#### Scenario: Max revises after the last segment

- **WHEN** a Max run decides its last segment
- **THEN** the revision stage starts before the run ends

### Requirement: Split an oversized segment at sentence boundaries

IF a segment's masked text alone is estimated above the chunk token budget, at most 1200 tokens, THEN the system SHALL
split it at the source language's sentence boundaries without ever separating a paired placeholder, draft each piece with
the segment's context, join the pieces in order, and gate and decide the joined target as that one segment. IF no split
keeps every placeholder pair together, THEN the system SHALL send the segment whole, alone, without preceding targets and
memory hits, and SHALL write one WARN line naming the segment.

**Source:** FR-ALGO-03, FR-ALGO-12 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-ALGO-C2,
FR-ALGO-C2b (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, `#token-budget`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#chunk-packing`,
`docs/implementation_plan/CHANGE_BACKLOG.md` (D5).
In plain words: some books hold one paragraph of 26,000 characters, far more than a small model answers well in one go.
Splitting only that paragraph, and only between sentences, keeps the book's structure intact because the joined
translation still goes back into the one paragraph it came from.

#### Scenario: A huge paragraph is drafted in pieces

- **WHEN** segment `ch02.xhtml:7` holds 26,306 Latin characters, estimated at 7,563 tokens
- **THEN** it is drafted in several sentence-aligned pieces, each with the same style sheet and glossary entries
- **AND** exactly one decision is made for `ch02.xhtml:7`, over the joined target

#### Scenario: A pair is never separated

- **WHEN** a sentence boundary of an oversized segment falls between `⟦g3⟧` and its partner `⟦g4⟧`
- **THEN** that boundary is not used, so `⟦g3⟧`, the text it wraps and `⟦g4⟧` are in one piece

#### Scenario: An unsplittable segment goes alone

- **WHEN** segment `ch09.xhtml:3` is one 6,000-character sentence with no interior boundary
- **THEN** it is drafted whole with no preceding target and no memory hit
- **AND** the log holds one WARN line naming `ch09.xhtml:3`

### Requirement: Skip the auxiliary text the brief switches off

WHERE one of the Book Brief's four "Also translate" switches — `ToC / navigation labels`, `Image alt-text`,
`Book metadata (title/author)` and `Frontmatter values` — is off, the system SHALL treat every auxiliary segment of that
kind as kept as source by choice, decided by the brief at the time and whatever the segment's stored status: it SHALL make
no model call for it, SHALL keep its source text, SHALL count it neither as pending nor as accepted or flagged, and SHALL
leave it out of the progress, the time left and the place a new run starts. The navigation switch SHALL also govern the
page titles of an EPUB's content documents, and the metadata switch the book's descriptions. A body segment SHALL never
be kept as source by choice; a segment with nothing to translate is kept as it is by rule, not by choice, and is counted
apart ("Keep a segment with nothing to translate as it is").

**Source:** FR-BRIEF-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), FR-DOC-11 (`#fr-doc`),
DD-47 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041.
In plain words: a person who wants the table of contents left in the original language gets exactly that, and the
progress bar still reaches 100 % because those segments are not waiting to be translated. The switch is read whenever
the counts are taken, so turning it off after a run already translated those labels keeps them as source too, and
turning it on again makes the untranslated ones pending once more.

#### Scenario: Front matter is kept by default

- **WHEN** a Markdown book whose front matter holds `title: The Lighthouse` is translated with the default switches
- **THEN** no model call is made for the front-matter value, it keeps `The Lighthouse`, and the pending count does not
  include it

#### Scenario: Alt text switched off is kept

- **WHEN** image alt text is switched off and an EPUB image has the alt text `A lighthouse at dusk`
- **THEN** that alt text is not sent to the model and keeps `A lighthouse at dusk`

#### Scenario: Page titles follow the navigation switch

- **WHEN** `ToC / navigation labels` is off and a content document's page title is `Chapter One`
- **THEN** no model call is made for that page title and it keeps `Chapter One`

#### Scenario: A switch turned off after a run keeps its segments as source

- **WHEN** a run accepted the navigation label `Chapter One` as `Розділ перший`, and the person then switches
  `ToC / navigation labels` off
- **THEN** that label counts as kept as source, not as accepted, and the pending count does not include it
- **AND** a new run makes no model call for it

### Requirement: Keep a segment with nothing to translate as it is

WHEN a run reaches a PENDING segment whose visible text — its masked text with every `⟦gN⟧` token removed, normalized to
NFC, with every Unicode separator, control and format character dropped — is empty, holds no letter (only digits,
punctuation and symbols), is an upper-case Roman numeral with at most punctuation around it, is a single character, or
is an equation of one-letter symbols (an equals sign and no run of two or more letters, as `F = G × (m₁ × m₂) / r²`),
the system SHALL decide it ACCEPTED with its own text as the target, through the chunk's gate, with the path `verbatim`
and no model call, no reviewer, no quality check and no finding. A segment whose only visible letters belong to a locked
glossary name SHALL be decided the same way, written with the name's locked rendering. The system SHALL still announce
the segment as started and as decided, SHALL count it among the decided segments in the progress, but in neither the
auto-accepted nor the repaired count and never as kept as source by choice, SHALL write no translation-memory entry for
it, and SHALL leave it out of the time-left average. A segment holding a word — any run of two or more letters that is
not a Roman numeral — SHALL still be sent to the model.

**Source:** `.temporary_context/log.log` (the Bartimaeus run, 2026-10-01), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`.
In plain words: a chapter number, a scene break or a lone locked name reads the same in every language. The Bartimaeus
run sent the chapter number `2` to the model with a 740-token prompt, then held the echo against it; 69 segments of two
characters or less were each a full call. Keeping them as they are costs nothing and cannot go wrong, and they are
counted on their own line so nobody reads them as translations or as text someone chose to leave untranslated.

#### Scenario: A chapter number is kept with no call

- **WHEN** a run reaches `ch02.xhtml:0`, whose text is `2`
- **THEN** no model call is made for it, and it is ACCEPTED with the target `2` and the path `verbatim`
- **AND** its record holds no finding and no translation-memory entry is written for it

#### Scenario: A scene break and a Roman numeral are kept

- **WHEN** a run reaches a segment `***` and a segment `XIV`
- **THEN** each is ACCEPTED as it is with the path `verbatim` and no model call

#### Scenario: A lone locked name takes its rendering

- **WHEN** the glossary locks `Bartimaeus` → `Бартімеус` and a paragraph is only `Bartimaeus`
- **THEN** no model call is made for it, and it is ACCEPTED with the target `Бартімеус` and the path `verbatim`

#### Scenario: A word is still translated

- **WHEN** a paragraph is `Well` or `Chapter 2`
- **THEN** it is sent to the model as any other segment

#### Scenario: The progress counts it apart

- **WHEN** a run decides `2`, then `He left.` with no repair, then `***`
- **THEN** the last progress snapshot counts 3 accepted, of which 1 auto-accepted and 2 kept as is, and 0 pending

### Requirement: Send identical auxiliary text once

WHEN a run reaches a PENDING auxiliary segment whose masked source equals that of an auxiliary segment already drafted in
the same chunk or already ACCEPTED earlier in the run, the system SHALL make no model call for it: it SHALL take the
undecided draft, which is then reviewed and decided on its own, or the accepted target, which SHALL pass the same checks a
translation-memory reuse passes ("Reuse the translation memory only where its context matches") before it is accepted
with the path `tm-reuse`. A segment whose taken answer fails those checks SHALL be drafted as usual. Each segment SHALL
keep its own record and decision.

**Source:** `.temporary_context/log.log` (the Bartimaeus run, 2026-10-01), DD-47
(`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-47-metadata-nav-alt-translation`), ADR-0041.
In plain words: one book's alt texts read `image` 58 times and its page titles `The Bartimaeus Trilogy` 80 times, and
each was a separate call. A text that is identical gets an identical answer, so it is asked for once.

#### Scenario: A repeated alt text is sent once

- **WHEN** image alt text is switched on and two images of an EPUB have the alt text `image`
- **THEN** one model call translates `image`, and both alt texts are ACCEPTED with the same target

### Requirement: Announce each segment's text as it starts, is drafted and is decided

WHILE a run translates, the system SHALL announce, for each segment: when it starts, with its locator, its source
display text and its position (section k of n among the body units, chunk k of n within its unit); when its draft is
ready, with the draft display text; and when it is decided, with its status, and its
path (drafted, reused from memory, repaired, edited by the person, or source kept). Display text SHALL be the masked text
with its `⟦gN⟧` tokens removed and its whitespace collapsed. Book text SHALL travel only in these announcements in
memory and SHALL NOT be written to the log above TRACE.

**Source:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, NFR-PRIV-04
(`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#no-telemetry`).
In plain words: the Translating screen's two-row live panel shows the segment just decided and the one being translated
now, so the person can watch the translation happen, and the log names each segment the way a person reads it. The text
stays in memory; a log file written at the usual level never contains the book.

#### Scenario: A segment's three announcements

- **WHEN** segment `ch07.xhtml:41`, the 42nd segment of `ch07.xhtml`, the seventh of 11 body units, with the masked text
  `He opened the ⟦g0⟧old⟦g1⟧ door.` is drafted as `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and accepted
  `0.91`
- **THEN** its start announcement carries the locator `ch7 · p42`, `He opened the old door.` and section 7 of 11, its
  draft announcement carries `Він відчинив старі двері.`, and its decision carries ACCEPTED, `0.91` and the path drafted

#### Scenario: The log keeps the text out at DEBUG

- **WHEN** the same run is logged at DEBUG
- **THEN** the log holds lines naming `ch07.xhtml:41` and none holding `He opened` or `Він відчинив`

### Requirement: Report tokens per second from the recent drafts

WHILE a run translates, the system SHALL report tokens per second as the completion tokens over the generation time of
the last 20 finished draft calls, taking both from the provider's usage figures. IF a provider reports no usage for a
call, THEN the system SHALL estimate that call's completion tokens from the length of its reply in the target script and
SHALL use the call's wall-clock time.

**Source:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`.
In plain words: tokens per second tells a person how fast their model really is on this book, and it is taken from what
the server says it generated, so it is not a guess. Only drafts count, because a reviewer call's short answer would make a
slow model look fast.

#### Scenario: Usage figures give the rate

- **WHEN** each of the last 20 draft calls reported 90 completion tokens generated in 3 seconds
- **THEN** the reported rate is `30` tokens per second

#### Scenario: A provider without usage is estimated

- **WHEN** a provider reports no usage and every one of the last 20 draft replies is 300 Cyrillic characters answered in
  5 seconds
- **THEN** each call counts as 115 completion tokens and the reported rate is `23` tokens per second

### Requirement: Estimate the time left and the time spent

WHILE a run translates, the system SHALL estimate the time left as the average of wall-clock seconds per decided segment
over the last 20 such segments, times the pending segments remaining — a segment kept as source by
choice is not pending — and SHALL report no estimate until 5 segments of the run have been decided. A segment kept as it
is with no model call SHALL not enter the average. The elapsed time
SHALL count only the time the run was not paused.

**Source:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`.
In plain words: a book takes hours, so "how long is left" is the question a person asks most. The first few segments
include loading the model, so no estimate is shown until the figure means something, and a lunch-long pause does not
count as translation time.

#### Scenario: An estimate after five segments

- **WHEN** 5 segments were each decided in 12 seconds and 100 remain
- **THEN** the time left is 20 minutes

#### Scenario: Only the last twenty segments count

- **WHEN** 19 segments were each decided in 12 seconds, the next took 252 seconds, and 100 remain
- **THEN** the time left is 40 minutes

#### Scenario: No estimate too early

- **WHEN** 4 segments have been decided
- **THEN** no time left is reported

#### Scenario: A pause does not count as elapsed

- **WHEN** a run translates for 10 minutes, is paused for 3 and translates for 5 more
- **THEN** the elapsed time is 15 minutes

### Requirement: Give every call of a run its context size and expected output

The system SHALL give every model call a run makes a context size of 8192 tokens, and SHALL use 8192 as the effective
context when it sizes chunks. Every draft, directed fix and revision call SHALL also state the output
it expects: its segment's output allowance, estimated from the length of the source display text, the upper bound of
the language pair's length band and the target language's script. A reviewer call SHALL state the reviewer limit the next
requirement gives. A pre-scan or summary call SHALL state no expected output.

**Source:** `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#effective-context`, `#service-owned-retry`.
In plain words: Ollama's default context is small and it silently cuts off a longer prompt, which would drop the rules at
the top of the prompt. Asking for 8192 makes room for the whole context package. Where each server receives the size —
and that an OpenAI-compatible server never does — is the `llm-provider` capability's rule, as is the timeout the
expected output sets: a call that writes a long paragraph may wait longer than one that writes a line.

#### Scenario: A draft to Ollama carries the context size

- **WHEN** a run drafts `Book.md:0` through the provider `ollama`
- **THEN** the request carries a context size of 8192 tokens

#### Scenario: A draft states its output allowance

- **WHEN** a Balanced run drafts `Book.md:0`, whose source display text `He opened the old door.` has 23 characters,
  from `en` to `uk`
- **THEN** the draft request states 16 expected output tokens
- **AND** the reviewer call for its one-pair chunk states 85 expected output tokens

### Requirement: Cap the output of every call that states an expected output

The system SHALL give every call that states an expected output — draft, directed fix and revision —
also an output cap of `max(64, ⌈1.5 × allowance⌉ + 16 + 6 × placeholder tokens)`, and a draft, directed fix and revision call never less than `128`, where the allowance is the expected
output tokens the call states and the placeholder tokens are the `⟦gN⟧` tokens in the segment's masked text. A reviewer
call SHALL carry the cap `64 + Σ (96 + the candidate's estimated tokens)` over its pairs and state half of it as its
expected output, because a rewrite may repeat a whole candidate; the reviewer's response schema SHALL be flat — no
`maxItems`, `maxLength` or `additionalProperties` — since a bounded schema once stalled a structured call on one
provider. Every other call SHALL carry a cap too: a summary call `1024` (expecting `600`, its prompt asking for at most 150 words per language and five
facts), a pre-scan batch `64 + 48 × candidates` (expecting half), and a glossary review batch `2048` (expecting
`32 × terms`); no call kind goes out without a cap. IF a capped reply ends with a finish of cut off by length,
THEN the system SHALL treat it as any other cut-off reply ("Flag a segment whose reply cannot be used, and continue");
a cut-off summary keeps the previous summary.

**Source:** `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `#response-handling`,
`openspec/changes/complete-translation-workflow/proposal.md#what-changes`.
In plain words: a small model sometimes loops on one sentence and would otherwise write until the three-minute timeout,
three attempts in a row, for one paragraph. The cap is a generous multiple of the length the segment should need —
half as much again, a fixed margin for the `{"target":…}` wrapper, and room for every placeholder token — so a normal
reply never reaches it and a runaway one is cut off, flagged and left behind. A one-word source under a 64-token cap came
back as an empty target in the Bartimaeus run, so a translation call's cap never drops below 128. The reviewer was once left unbounded and a
looping re-judge then held a whole run for three minutes per attempt; the reviewer's reply is, per pair, an id, a status
and a few short edits, so its cap grows with the pairs and holds room for the one case that is long, a rewrite. A critique call (the retired reflect) was left unbounded until gemma4:e4b
on the fixture book streamed 9,664 lines into the three-minute timeout, and the run's ETA jumped from three to
nineteen minutes; every call kind now has a cap. How each server receives the cap is the `llm-provider` capability's
rule.

#### Scenario: A short draft gets the floor

- **WHEN** a Balanced run drafts `Book.md:0`, whose source display text `He opened the old door.` states 16 expected
  output tokens and holds no placeholder token
- **THEN** the draft request carries an output cap of `128`
- **AND** the reviewer call for its one-pair chunk carries an output cap of `170`

#### Scenario: A long paragraph with tokens gets a proportional cap

- **WHEN** a draft is sent for a 600-character paragraph that states 414 expected output tokens and holds 4 `⟦gN⟧`
  tokens
- **THEN** the draft request carries an output cap of `661`

#### Scenario: A reply cut at the cap is flagged and the run goes on

- **WHEN** the model's reply to the draft of `ch07.xhtml:41` ends with a finish of cut off by length
- **THEN** `ch07.xhtml:41` is FLAGGED with `ErrorCode.validation`
- **AND** the draft of `ch07.xhtml:42` is still sent

### Requirement: Record deferrals and revise backwards on Max

The system SHALL record a deferral for a segment when the segment contains a
glossary character whose gender is unknown. WHEN the person changes a glossary term's target, the system SHALL record,
for each decided segment whose target contains the previous target as a whole word, one TERM deferral per changed
term, only when the entry is locked after the change and had a non-empty target before it. WHERE the dial is Max, after the last segment the system SHALL run a backward
revision bounded to segments that contain a swept term: it SHALL substitute each locked term's rendering deterministically,
without a model call — replacing only the previous glossary target the person changed, never a rendering the model chose
on its own for a term that had no glossary target — and SHALL call the model with the revision call only for a gender deferral whose character now has a
gender. IF a segment to be changed carries the person's own edit, THEN the system SHALL record a proposal for it and leave
its target unchanged.

**Source:** FR-ALGO-10 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-REVIEW-10
(`#fr-review`), FR-ALGO-D1 … FR-ALGO-D3 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`.
In plain words: early in a book the model may not yet know that "Sam" is a woman, and Ukrainian verbs agree with gender.
The careful setting goes back and fixes those sentences once the facts are known, rewrites a locked name the same way
everywhere, and never overwrites what the person wrote by hand.

#### Scenario: A locked term is swept without a model call

- **WHEN** the glossary held `Hale` → `Хейл` when `ch01.xhtml:3` was accepted as `Хейл пішов.`, the person later changed
  the target to `Гейл` and locked it, and a Max run reaches its revision stage
- **THEN** `ch01.xhtml:3` holds `Гейл пішов.` and no model call was made for it

#### Scenario: A rendering the model chose on its own is not swept

- **WHEN** `Hale` had no glossary target when `ch01.xhtml:5` was accepted as `Хейл прийшов.`, the person later added
  `Hale` → `Гейл` locked, and a Max run reaches its revision stage
- **THEN** `ch01.xhtml:5` still holds `Хейл прийшов.` and no deferral was recorded for it

#### Scenario: A gender deferral is re-rendered

- **WHEN** `ch01.xhtml:9` mentions `Sam`, whose gender was `unknown` when it was drafted and is `female` at the revision
  stage
- **THEN** one revision call is made for `ch01.xhtml:9` and its target is replaced with the revised one

#### Scenario: Two changed terms give two deferrals

- **WHEN** `ch01.xhtml:3` contains `Hale` and `Milton`, both locked entries with targets, and the person changes both
  targets
- **THEN** two TERM deferrals are recorded on `ch01.xhtml:3`, one for `Hale` and one for `Milton`

#### Scenario: A change to an unlocked entry records nothing

- **WHEN** the person changes the target of the unlocked entry `Hale` and `ch01.xhtml:3` contains `Hale`
- **THEN** no deferral is recorded for `ch01.xhtml:3`

#### Scenario: A person's edit is protected

- **WHEN** the person had edited `ch01.xhtml:3` before the revision stage would change it
- **THEN** its target is unchanged and a proposal for it is recorded

### Requirement: Resolve the review mode once at launch

WHEN the desktop application starts, the system SHALL take the review mode from the environment variable
`BOOKLOOM_REVIEW_MODE`, or else from the system property `bookloom.review.mode`: `unattended` or `auto` is Unattended,
`assisted` or `semi-manual` is Assisted, and `manual` is Manual, in any letter case. WHEN neither is set, the mode SHALL be
Unattended. IF the value names no mode, THEN the system SHALL use Unattended and write one WARN line naming the rejected
value.

**Source:** FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`),
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, ADR-0036.
In plain words: Settings are not saved yet, so the careful modes are chosen by a launch flag, read the same way as the
log level. A typo must not silently change how the run behaves, and the default stays fully automatic.

#### Scenario: A spelled-out alias is accepted

- **WHEN** the application starts with `BOOKLOOM_REVIEW_MODE` set to `Semi-Manual`
- **THEN** runs use Assisted

#### Scenario: The environment wins over the property

- **WHEN** `BOOKLOOM_REVIEW_MODE` is `auto` and `bookloom.review.mode` is `manual`
- **THEN** runs use Unattended

#### Scenario: An unknown value falls back

- **WHEN** `BOOKLOOM_REVIEW_MODE` is `strict`
- **THEN** runs use Unattended and the log holds one WARN line holding `strict`

### Requirement: Hold no model-call permit while paused

WHILE a run is paused, the system SHALL hold no place in the single-flight gate, so another model call — a verification
or a review retry — can reach the provider.

**Source:** FR-INFER-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#inference-gate`, ADR-0036.
In plain words: a run paused for review waits for the person, who may want to retry the segment or test the provider; a
paused run that kept the gate would make both wait forever.

#### Scenario: A verification runs during a review pause

- **WHEN** an Assisted run is paused on the flagged segment `ch07.xhtml:41` and the person tests inference on the
  Providers tab
- **THEN** the test request reaches the provider at once

### Requirement: Flag a segment whose reply cannot be used, and continue

IF any of the following happens to a segment's draft, THEN the system SHALL mark the segment FLAGGED at once with that
error as its reason, with no self-heal round, keep no machine translation for it, and continue with the next segment,
after any pause the review mode causes:

- the reply's content is empty or holds only whitespace (`ErrorCode.emptyCompletion`), whatever its finish;
- the reply's finish is not normal, such as a reply cut off by length (`ErrorCode.validation`);
- a reply is not the required JSON object after the draft's one structural repair was already used — the reply to that
  repair, or to the placeholder repair that followed it (`ErrorCode.validation`; see "Repair invalid structured draft
  replies once");
- the model answers `ErrorCode.emptyCompletion`;
- the model answers `ErrorCode.contextWindow`: the segment's own prompt does not fit the model's context, which is
  particular to that segment, so retrying or pausing cannot help.

IF the reply to a self-heal call is empty or cut off, or the model answers `ErrorCode.emptyCompletion` or
`ErrorCode.contextWindow` to it, THEN the system SHALL likewise flag the segment at once, keeping as its machine
translation the last target that passed every hard gate, if there is one.

A segment flagged at once SHALL store its reason as a high finding whose kind is the error code's name, raised by
`reply`.

The system SHALL examine a reply for these causes in a fixed order and act on the first that applies: an error the call
answered (see also "Stop the job on any other failure"); empty content; an abnormal finish; a reply that is not the
required JSON object; markup that does not restore; a refusal; and only then the quality checks. A reply whose markup
does not restore after its placeholder repair, or that reads as a refusal, SHALL NOT be flagged at once: it goes through
self-heal and is FLAGGED only after the dial's repair rounds fail (the `quality-gates` capability).

**Source:** FR-DOC-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-ALGO-C8
(`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`),
`docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#segment-status-machine`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#empty-response-ordering`, `#repair-and-gate`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, `docs/implementation_plan/CHANGE_BACKLOG.md`
(D19).
In plain words: one bad reply must not stop a book. A reply that holds nothing, that the server cut off, or that twice
fails to be the one small JSON object asked for gives self-heal nothing to work on, so the segment is flagged at once and
an export writes its source text rather than broken markup, and the report says why. A reply of only spaces counts as
empty: accepting it would silently delete the paragraph. A context overflow belongs to the one segment whose prompt is
too long, so it is flagged and the run moves on. A reply that lost a token or refused the task is still a reply the model
can be asked to fix with the problem named, so it gets the dial's repair rounds first. A provider refusing the request
for every segment alike — an unloaded model, an unreachable server — is not a bad reply and no longer flags anything; it
pauses the run instead.

#### Scenario: A missing token flags the segment and the job goes on

- **WHEN** a Fast run (one repair round) translates a Markdown book with the paragraphs `He opened the *old* door.` and
  `She left.`, and the model replies `{"target":"ВІДЧИНИВ"}` — no token, and one word that cannot hold the pair — to the
  first paragraph's draft, to its placeholder repair and to its one directed fix
- **THEN** the first segment is FLAGGED with a high `markup` finding after exactly 3 model calls — the draft, the
  placeholder repair naming `⟦g0⟧ ⟦g1⟧`, and a directed fix stating `⟦g0⟧ ⟦g1⟧` — and keeps no machine translation
- **AND** it keeps `ВІДЧИНИВ` as its rejected target
- **AND** the second segment is still sent to the model

#### Scenario: A flagged reply stores its reason as a finding

- **WHEN** the model replies `HE OPENED` to the draft of a TXT paragraph and again `HE OPENED` to its structural repair,
  neither being the required JSON object
- **THEN** the segment holds one finding of kind `validation`, severity high, raised by `reply`

#### Scenario: An empty reply stores an emptyCompletion finding

- **WHEN** the model replies to the draft of a TXT paragraph with two spaces and a normal finish
- **THEN** the segment holds one finding of kind `emptyCompletion`, severity high, raised by `reply`

#### Scenario: A reply of only whitespace is flagged as empty

- **WHEN** a TXT book has the paragraphs `One.` and `Two.`, and the model replies to `One.` with two spaces and a line
  feed and a normal finish
- **THEN** the first segment is FLAGGED with `ErrorCode.emptyCompletion` and keeps no translation, after exactly 1 model
  call
- **AND** `Two.` is still sent to the model

#### Scenario: Empty content wins over a cut-off finish

- **WHEN** the model replies to a draft with two spaces and a finish of cut off by length
- **THEN** the segment is FLAGGED with `ErrorCode.emptyCompletion`, not `ErrorCode.validation`

#### Scenario: A reply cut off by length is flagged

- **WHEN** the model replies `HE OPENED` with a finish of cut off by length
- **THEN** the segment is FLAGGED with `ErrorCode.validation`, no repair call is made for it, and the next segment is
  sent

#### Scenario: A context-window error flags the segment and the run goes on

- **WHEN** a TXT book has the paragraphs `One.` and `Two.`, and the model answers `ErrorCode.contextWindow` for `One.`
- **THEN** the first segment is FLAGGED with `ErrorCode.contextWindow` and keeps no translation
- **AND** the run neither pauses nor ends, and `Two.` is still sent to the model

#### Scenario: A refusal is repaired rather than flagged

- **WHEN** a Fast run translates the TXT paragraph `He opened the old door.` from `en` to `uk`, the model replies
  `{"target":"I'm sorry, but I can't translate this text."}` to the draft, and `{"target":"Він відчинив старі двері."}`
  to the directed fix that follows
- **THEN** the segment is not flagged, and is ACCEPTED as repaired and accepted after exactly 2 model calls

#### Scenario: A cut-off self-heal reply flags at once and keeps the earlier target

- **WHEN** a Balanced run translates the TXT paragraph `He opened the old door.` from `en` to `uk`, the draft reply
  `{"target":"HE OPENED THE OLD DOOR."}` fails the untranslated-echo check, and the model answers the first directed fix
  with a finish of cut off by length
- **THEN** the segment is FLAGGED with `ErrorCode.validation` with no second directed fix
- **AND** it keeps `HE OPENED THE OLD DOOR.` as its machine translation


### Requirement: Refuse a run that cannot start

IF any of the following holds, THEN the system SHALL return that failure without calling the model. The failure is
`ErrorCode.validation`.

- the brief's target language, or its source language when one is set, is not a language code: two or three letters,
  optionally followed by subtags of two to eight letters or digits, each after a hyphen, such as `uk`, `en-US` or
  `zh-Hant`;
- the run names no known project;
- the project's book is no longer open;
- the job has already run.

**Source:** FR-EXPORT-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-export`),
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#boundary-discipline`, ADR-0035.
In plain words: a job that cannot finish should fail before it costs a single model call. The target language becomes
part of the output file name, so a value like `../x` must never get that far. The destination checks — the file type,
the source itself, an existing file — now belong to the export, which is where the destination is chosen.

#### Scenario: A path-like language code is refused

- **WHEN** a job is requested for a project whose brief has the target language `../x`
- **THEN** the result is `ErrorCode.validation` and the model is never called

#### Scenario: An unknown project is refused

- **WHEN** a job is requested for the project id `missing`
- **THEN** the result is `ErrorCode.validation` and the model is never called

#### Scenario: A job runs only once

- **WHEN** a job that has already finished is run again
- **THEN** the result is `ErrorCode.validation`


### Requirement: Tag every activity-log entry with one of seven kinds

Every entry the activity log holds SHALL carry one kind from a fixed vocabulary of seven, each shown by its tag —
accepted `ok`, repaired `fix`, glossary or memory applied `mem`, summary updated `sum`, retried `retry`, segment error
`err` and milestone `info` — and each kind SHALL carry both a status role from the token catalogue and a mark that is not
its colour, so the kind is readable without seeing the colour. Entries SHALL be rendered in a monospace face with the tag
first.

The run SHALL produce every kind: `ok` when a segment is accepted; `fix` for each repair call — a directed fix; `retry` for a structural or placeholder format repair and for the resume that follows a
provider-error pause; `mem` when a name scan adds glossary entries or a segment is reused from the translation memory;
`sum` when the rolling summary is refreshed; `err` when a segment is flagged; and `info` for a run milestone — a stage
start, a pause (including a provider-error pause), a resume or the finish. A retry the provider client makes inside one
call is not seen by the run and produces no entry.

The text of every entry SHALL come from the message catalogue by key, with the segment's locator — such as
`ch7 · p42`, never its id — and any other value substituted into it.

The log SHALL retain its most recent entries up to a fixed bound and SHALL drop the oldest first.

**Source:** FR-NOTIF-6a, FR-NOTIF-6b, FR-NOTIF-6c
(`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#activity-log`), FR-A11Y-6
(`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#accessibility`), FR-UI-08
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`).
In plain words: the log is the only record of what a multi-hour run decided. A fixed vocabulary of seven short tags,
lined up in a column, is what makes it scannable, and now that the engine repairs, remembers and summarises, every one of
the seven actually appears. Colour alone cannot carry the kind, and the text comes from the catalogue so a Ukrainian
window never shows an English log. An entry names a segment the way the review panel does, by its locator, because a
file name and an ordinal mean nothing to the person reading it.

#### Scenario: An accepted segment logs its kind and role

- **WHEN** the segment `ch7 · p42` is accepted during a run
- **THEN** an entry of kind accepted with the tag `ok` is appended, referring to the success role, which resolves to
  `#5f8a6b` under the light values
- **AND** it carries a mark distinguishing it from a segment-error entry without relying on colour

#### Scenario: A repair round logs fix

- **WHEN** the segment `ch07.xhtml:41`, whose locator is `ch7 · p42`, goes through one directed fix
- **THEN** an entry tagged `fix` naming `ch7 · p42`, and not `ch07.xhtml:41`, is appended, and its tag is rendered
  before its text in a monospace face

#### Scenario: A format repair and a resume after a provider error log retry

- **WHEN** the segment `ch7 · p43` gets one placeholder repair, and later the run pauses on `ErrorCode.unreachable` and
  the person presses Retry now
- **THEN** an entry tagged `retry` naming `ch7 · p43` is appended for the repair, the pause is logged as `info`, and the
  resume appends a second `retry` entry

#### Scenario: Entry text comes from the catalogue

- **WHEN** the accepted entry for the segment `ch7 · p42` is rendered while the interface is in Ukrainian
- **THEN** its text resolves from a catalogue key with `ch7 · p42` substituted into it, and is not an English sentence
  with the locator joined to it

#### Scenario: Each kind maps to its own status role

- **WHEN** the seven kinds are read
- **THEN** accepted refers to the success role; repaired, glossary-applied, summary-updated and milestone refer to
  the information role; retried refers to the warning role; and segment-error refers to the danger role

#### Scenario: The log drops its oldest entries

- **WHEN** a run appends more entries than the log's bound
- **THEN** the log holds exactly the bound, and the entry appended first is no longer present

### Requirement: Decide a chunk's segments in document order

WHERE the quality dial enables the reviewer, the system SHALL take each chunk through three phases, each in document order:
first it SHALL draft every segment of the chunk, checking a segment whose context matches the translation memory at once
instead of drafting it; then it SHALL make the chunk's one reviewer call; then it SHALL decide the segments one at a time,
each through its self-heal rounds to ACCEPTED or FLAGGED before the next is decided. The preceding targets of each draft
SHALL be the effective targets of the earlier segments, the chunk's own undecided drafts and reused targets included.
WHERE the reviewer is off, the system SHALL draft and decide each segment before it drafts the next, so the preceding
targets of each draft are decided targets.

WHEN a run pauses — on request, for review or on an error — the system SHALL first commit the segments decided since the
last commit, and SHALL keep the chunk's undecided drafts in memory while it waits. WHEN the run resumes, the system SHALL
read the paused segment's stored decision again, SHALL decide the chunk's remaining segments from the drafts already made
rather than drafting them again, and SHALL give a target the person edited during the pause to every draft made after
the resume; the model call a pause interrupted is made again as the `resume` capability's "Pause on request at the next
boundary" states. WHEN a run is
stopped, the system SHALL commit the segments already decided and SHALL drop the undecided drafts, whose segments stay
PENDING for the next run to draft. A commit SHALL hold only the segments decided since the previous commit and SHALL
never write again a record the run already committed, so a target the person saved during a pause is never overwritten
by the run.

**Source:** FR-ALGO-C4, FR-ALGO-C4b, FR-ALGO-C7, FR-ALGO-C8
(`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`), FR-ALGO-C13 (`#self-heal`), FR-RESUME-03,
FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, ADR-0033, ADR-0034, ADR-0038.
In plain words: the reviewer reads a whole chunk at once, so with the reviewer on (Balanced and Max) every segment of a chunk is
drafted before any is decided, and each draft still sees the translations just before it, even those not yet decided.
That fixes what a pause in the middle of a chunk can change: the rest of the chunk is already drafted and reviewed, so an
edit made during the pause reaches the drafts made after it — from the next chunk on — while the drafts already paid for
are kept. With the reviewer off (Fast) nothing needs the whole chunk, so each segment is drafted and decided in turn, and an
edit reaches the very next draft. A decision is never lost to a pause or a stop; a draft that was not yet decided is
dropped by a stop and drafted again by the next run.

#### Scenario: Balanced drafts the whole chunk before deciding any of it

- **WHEN** a Balanced run takes the chunk `ch07.xhtml:40` … `ch07.xhtml:43`
- **THEN** its model calls begin with the drafts of `ch07.xhtml:40`, `ch07.xhtml:41`, `ch07.xhtml:42` and
  `ch07.xhtml:43`, in that order, then one reviewer call for the four, and only then any self-heal call
- **AND** the draft call for `ch07.xhtml:42` carries the drafts of `ch07.xhtml:40` and `ch07.xhtml:41` as its preceding
  targets, although neither is decided yet

#### Scenario: A review pause in the middle of a Balanced chunk

- **WHEN** an Assisted, Balanced run takes the chunk `ch07.xhtml:40` … `ch07.xhtml:43`, accepts `ch07.xhtml:40`, flags
  `ch07.xhtml:41` and pauses on it
- **THEN** the drafts of `ch07.xhtml:42` and `ch07.xhtml:43` and the chunk's reviewer call were made before the pause
- **AND** `ch07.xhtml:40` and `ch07.xhtml:41` were committed before the run began to wait
- **AND** after the person saves an edit of `ch07.xhtml:41` and resumes, `ch07.xhtml:42` and `ch07.xhtml:43` are decided
  from their existing drafts, with no new draft call for either

#### Scenario: A saved edit survives the rest of the chunk

- **WHEN** an Assisted, Balanced run flags `ch07.xhtml:41` and pauses on it, the person saves the edit
  `Він рвучко відчинив двері.`, and the run resumes and finishes the chunk
- **THEN** `ch07.xhtml:41` is still REVISED with `Він рвучко відчинив двері.`

#### Scenario: An edit made during a Balanced pause feeds the next chunk

- **WHEN** an Assisted, Balanced run flags `ch07.xhtml:43`, the last segment of its chunk, and pauses on it, and the
  person saves the edit `Він рвучко відчинив двері.` and resumes
- **THEN** the draft call for `ch07.xhtml:44`, the first segment of the next chunk, carries `Він рвучко відчинив двері.`
  as a preceding target
- **AND** no further call is made for `ch07.xhtml:43`

#### Scenario: Fast drafts and decides one segment at a time

- **WHEN** an Assisted, Fast run flags `ch07.xhtml:41` and pauses on it, and the person saves the edit
  `Він рвучко відчинив двері.` and resumes
- **THEN** no model call for `ch07.xhtml:42` was made before the pause
- **AND** the draft call for `ch07.xhtml:42` carries `Він рвучко відчинив двері.` as a preceding target

#### Scenario: A memory reuse is neither drafted nor reviewed

- **WHEN** a Balanced run takes the chunk `ch07.xhtml:40` … `ch07.xhtml:43`, and the context of `ch07.xhtml:42` matches a
  memory entry whose target passes its checks
- **THEN** three draft calls are made for the chunk, none of them for `ch07.xhtml:42`
- **AND** the chunk's reviewer call shows three pairs, labelled `s1`, `s2` and `s3`, for `ch07.xhtml:40`, `ch07.xhtml:41`
  and `ch07.xhtml:43`

#### Scenario: A reviewer call interrupted by a pause is made again

- **WHEN** a Balanced run has drafted `ch07.xhtml:40` … `ch07.xhtml:43`, a pause is requested while the chunk's reviewer
  call is in flight, and the run is resumed
- **THEN** the reviewer call for the chunk is sent again and no draft call of the chunk is repeated
- **AND** each of the four segments is decided exactly once

#### Scenario: A stop drops the undecided drafts

- **WHEN** a Balanced run has drafted `ch07.xhtml:40` … `ch07.xhtml:43` and decided `ch07.xhtml:40` and `ch07.xhtml:41`,
  and is stopped while a directed fix for `ch07.xhtml:42` is in flight
- **THEN** `ch07.xhtml:40` and `ch07.xhtml:41` keep their decisions, and `ch07.xhtml:42` and `ch07.xhtml:43` stay PENDING
  with no stored translation
- **AND** the next run's first draft call is for `ch07.xhtml:42`

### Requirement: Propose new names at the end of each body unit

WHEN a run decides the last segment of a body unit, the system SHALL run the deterministic name scan the `glossary`
capability defines ("Propose names by a deterministic scan when the glossary is empty") over the source text of every
segment of the book decided so far, and SHALL add each proposed term the glossary does not hold — compared without
regard to letter case — as an unlocked entry with no target, of type other and gender unknown, subject to the
`glossary` capability's "Keep the person's entries and removals when names are proposed". WHEN it added at least one
term, the system SHALL announce the glossary update with the number added.

**Source:** FR-ALGO-C9 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`), FR-GLOSS-01
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`),
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`.
In plain words: a person who started from their own list of names still gets the names the book introduces later, found
the same free, offline way as the first proposals, with no model call. A proposal never replaces what the person
entered, and a name the person deleted stays deleted — otherwise the next chapter would bring it back. The new entries
are unlocked and have no target, so until the person fills them in on Names & style they only tell the model that the
word is a name.

#### Scenario: A recurring name is added at the end of a chapter

- **WHEN** the glossary holds only the person's `Hale` → `Гейл`, and `Moreau` occurs twice in `ch02.xhtml` and twice in
  `ch03.xhtml`, never at the start of a sentence
- **THEN** nothing is added at the end of `ch02.xhtml`, and when the last segment of `ch03.xhtml` is decided the glossary
  gains `Moreau`, unlocked, with no target, of type other and gender unknown
- **AND** the run announces a glossary update labelled `+1`, and `Hale` → `Гейл` is unchanged

### Requirement: Show the segment just decided and the one in progress in the live panel

WHILE a run translates, the Translating screen's `Current chunk (live)` panel SHALL show two rows: the first holds the
segment decided most recently — its source, its target, and its path —; the second
holds the segment started most recently and not yet decided — its source, with `waiting for the model…` until its draft
is ready and then its draft, marked `awaiting review` while the reviewer is on. WHEN the second row's segment is decided, the
screen SHALL move it to the first row and SHALL leave the second row empty until the next segment starts. Both rows SHALL
show display text only, taken from the run's announcements (see "Announce each segment's text as it starts, is drafted
and is decided").

**Source:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, the Translating screen's running state in
`docs/specification/mockups/ui-mockup.html`.
In plain words: a book takes hours, and watching sentences go by is how a person judges whether the model is doing well
before the run ends. The panel shows the last finished sentence beside the one the model is working on, so a slow model
is visibly busy rather than stuck, and a draft waiting for the chunk's reviewer is marked as not yet decided.

#### Scenario: The decided segment above the one in progress

- **WHEN** a Fast run has accepted `ch7 · p41`, `She had lost her mother, and the poor girl wept as she followed the
  coffin.`, on its first draft as `Вона втратила матір, і бідолашна дівчина плакала, ідучи за труною.`, and has started
  `ch7 · p42`, whose draft has not arrived
- **THEN** the first row shows that source and that target with the path `auto-accepted`
- **AND** the second row shows the source of `ch7 · p42` and `waiting for the model…`

#### Scenario: A draft waits for the reviewer

- **WHEN** on a Balanced run the draft of `ch7 · p42` has arrived and no later segment has started
- **THEN** the second row shows that draft marked `awaiting review`

#### Scenario: A decision moves the segment up

- **WHEN** `ch7 · p42`, shown in the second row, is accepted
- **THEN** the first row shows `ch7 · p42` with its path, and the second row stays empty until `ch7 · p43` starts

### Requirement: Show the Translating screen's idle, completed and failed states

WHILE no run has started for the open book, the Translating screen SHALL show a ready card — the book, the model, the
review mode, the quality dial and the pending count — and SHALL offer its own Start, naming in place a missing model or
brief field. WHEN a run completes, the screen SHALL show its outcome — the auto-accepted, repaired-and-accepted, flagged
and kept-as-source counts — and SHALL offer Continue to Export and Review flagged (n), and its Start only WHILE pending
segments remain, as they do after an "Also translate" switch is turned on once the run has completed. WHEN a run ends
Failed, the screen SHALL show the blocking error dialog, which says `Decided segments are kept until the application
closes.`, then the outcome so far, and SHALL offer its Start. The screen's Start SHALL begin a new run at the project's
first pending segment — a start, never a resume.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-RESUME-05
(`#fr-resume`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#error-dialog`, ADR-0035.
In plain words: the screen always says what can happen next. Before a run it shows what the run will use and how much is
left; after a run it points to Export, where the book is written, and offers a start again only when there is still
something pending to translate; after a failure it keeps what was decided and offers to start again from the first
undecided segment. When a start is offered at all is app-shell's "Move through the workflow by its available steps". The
Translating screen has seven states: idle, completed and failed are fixed here, running by "Start a run and report its
progress", paused and stopped by the `resume` capability, and the provider error by the `notifications` capability. The
mockup's dialog promised "Your progress is saved"; nothing is saved to disk yet, so the dialog says what is true.

#### Scenario: Idle shows the ready card

- **WHEN** `Frankenstein.epub` is open, briefed from `en` to `uk` on Balanced, the model `gemma4:26b` is chosen, the
  review mode is Unattended, 1,240 segments are pending and no run has started
- **THEN** the screen shows a ready card naming `Frankenstein.epub`, `gemma4:26b`, Unattended, Balanced and 1,240
  pending segments
- **AND** it offers Start and no Pause, Stop or Resume

#### Scenario: Completed points to Export

- **WHEN** a run completes with 1,180 auto-accepted, 45 repaired and accepted, 3 flagged and 12 kept as source, leaving
  nothing pending
- **THEN** the screen shows those four counts and offers Continue to Export and Review flagged (3)
- **AND** it offers no Start, Pause or Resume

#### Scenario: Completed with pending segments offers a start

- **WHEN** a run over a Markdown book completes with `Frontmatter values` switched off, and the person then switches
  `Frontmatter values` on, leaving the book's 3 frontmatter values pending
- **THEN** the translating screen offers Start beside Continue to Export
- **AND** Start begins a new run whose only model calls are for those 3 frontmatter values

#### Scenario: Failed shows the dialog, then offers a new start

- **WHEN** a run ends Failed with `ErrorCode.internal` after 412 of 1,240 segments were decided
- **THEN** a blocking error dialog says `Decided segments are kept until the application closes.` and does not say
  `Your progress is saved — nothing was lost.`
- **AND** once it is closed the screen shows the 412 decided segments and offers Start, which begins a new run whose
  first model call is for the 413th segment


### Requirement: Keep a detailed diagnostic log that one file explains

The system SHALL write, beside its ordinary log file and in the same local folder, a detailed diagnostic log
`bookloom-trace.log` holding every BookLoom line down to TRACE, one line per event with the job and segment ids, rotated
at 20 MB into at most four compressed archives. The system SHALL write it by default in a development run, and in an
installed application only WHEN `BOOKLOOM_TRACE_FILE=1` or `-Dbookloom.trace.file=true` is set; writing it SHALL NOT
change the level of the ordinary log or the console. The system SHALL begin each file of the detailed log, and each
file a rotation opens, with a session header naming the version, the operating system, the Java and JavaFX versions,
the locale, the log levels, and the session facts known at that moment: the provider, its kind and endpoint host, the
model, the quality dial, the review mode, the brief's choices, and the book's file name, format and size; each change
of those facts SHALL also be one INFO line. WHILE a job runs, the system SHALL write at most one INFO run-summary line a
minute, and one when the job ends, with the segments accepted, flagged, kept verbatim and pending, the model calls with
their average and 95th-percentile duration, tokens per second, timeouts and the segment being translated. Pause,
resume and stop requests, each pause with its reason and how it ended, and the start and end of each glossary scan
and review SHALL be INFO lines. The detailed log SHALL never contain a credential, SHALL hold book text only on TRACE
lines, and SHALL never leave the machine unless the person shares it.

**Source:** DD-23 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-23-slf4j-logback`), NFR-PRIV-04
(`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#no-telemetry`), NFR-PRIV-06 and NFR-PRIV-07
(`#secrets-never-stored`), `.claude/rules/logging.md`.
In plain words: until now the log a person could send was the terminal of a development run, cut where the terminal
buffer ended. The detailed log is a bounded file that explains itself: its header says which build, machine, provider,
model and book it is about, even in a file cut by rotation, and the run summary shows the pace of a run without reading
every line.

#### Scenario: A development run writes the detailed log with its header

- **WHEN** a development run is started with no switch set and opens `Kobzar.fb2`
- **THEN** `bookloom-trace.log` begins with a header naming the version, the operating system and `detailedLog=on`
- **AND** it holds an INFO line `session update book=Kobzar.fb2 format=FB2 …`
- **AND** `bookloom.log` holds no TRACE line

#### Scenario: An installed app writes it only when asked

- **WHEN** an installed application starts without `BOOKLOOM_TRACE_FILE`
- **THEN** no `bookloom-trace.log` is written
- **AND WHEN** it starts with `BOOKLOOM_TRACE_FILE=1`
- **THEN** `bookloom-trace.log` is written

#### Scenario: A rotated file names its session again

- **WHEN** the detailed log reaches 20 MB during a run with the model `gemma4:e4b`
- **THEN** the full file becomes `bookloom-trace.1.log.gz` and the new `bookloom-trace.log` begins with a header naming
  `model=gemma4:e4b`

#### Scenario: A run summary each minute

- **WHEN** a job has been running for two minutes, deciding segments all the time
- **THEN** the detailed log holds two `run summary periodic` lines, not one per segment

#### Scenario: No credential and book text only at TRACE

- **WHEN** `Book.md` with `He opened the *old* door.` is translated through the Ollama-native or the
  OpenAI-compatible client while the provider answers with `Authorization` and `X-Api-Key` headers
- **THEN** every line of `bookloom-trace.log` holding `He opened the` is a TRACE line
- **AND** no line holds the credential value

### Requirement: Size every call from one context budget

The system SHALL divide the usable window with the `ContextBudget`: the window `W` is the override when one is set, else
the detected context length limited to 8192, else 8192; the static prefix (the system message and the style sheet), the
dynamic context (glossary and lexicon lines for terms present in the chunk, memory, preceding translated text of the same
unit, summary) and a safety margin of 500 tokens are taken off `W`, and what remains is split between a chunk's source
tokens `T` and its reply `r·T`, where `r` is the length band's upper ratio for the language pair corrected for the
scripts' characters per token. A chunk SHALL hold at most `min(1200, T)` source tokens. The dynamic context SHALL take at
most 40% of what the prefix and margin leave, and never more than 1100 tokens, and SHALL be cut by priority — the
summary is dropped first, then preceding text (oldest first), then memory, and glossary lines last — before the context snapshot is
recorded, so a retry replays what the first draft saw.

**Source:** `tasks.md` 15d.5. In plain words: a model with a 4k window is given a smaller chunk and a smaller context
instead of a prompt it silently truncates.

#### Scenario: The 8k window

- **WHEN** the window is 8192, the prefix 900, the dynamic context 1100 and the ratio 1.5
- **THEN** a batch may hold 2276 source tokens and a chunk 1200

#### Scenario: A 4k window is never over budget

- **WHEN** a unit with 80 glossary terms, a long summary and three preceding texts is drafted at a window of 4096
- **THEN** every draft prompt plus the reply its chunk may need plus 500 is at most 4096 estimated tokens

#### Scenario: A short allowance cuts the summary first

- **WHEN** the dynamic allowance holds the glossary line and the two preceding texts but not the summary
- **THEN** the prompt carries the glossary line and both preceding texts and no summary block

### Requirement: Draft consecutive segments in token-budgeted batches

The system SHALL draft the segments of a chunk that need a model call in batches: when a chunk reaches a segment that
needs a call and no batch has dealt with it, that segment and the next ones that need a call, up to the batch size and
within the chunk's source-token budget (never a fixed segment count), SHALL go in one call whose reply is the JSON
object `{"items":[{"id","target","terms"?}]}` — ids `1…n` within the batch, answered by the flat batch schema, whose optional
`terms` object holds the renderings of the lexicon's key terms the item used ("Record the renderings a draft used and keep
them consistent", glossary). The prompt SHALL
carry the items, the summary, glossary, memory and suggestions of every item once, the last two or three decided
source and target pairs of the chapter, the source of the segment after the batch, the characters of the glossary the
batch names with their genders, the established renderings of the recurring terms the items name and the closed list of
key terms to report, and, for each locked name, a line prefixed with its item's id (`3: ⟦g0⟧ → name`). A
segment kept as it is, an auxiliary text, a translation-memory reuse and a segment larger than the chunk budget SHALL
never join a batch, and Manual review, whose chunks hold one segment, SHALL draft every segment alone.

The reply SHALL be validated per id: the id exactly once with text, the source's tokens in order, the length band and the
document's placeholder gate. Only an id that fails SHALL be drafted again on its own, through the ordinary single-segment
draft, with its own context package; a reply that is unreadable, or a call answered with an error the single draft
would flag its segment for, SHALL send every segment to its own draft. Every segment keeps its own verdict: the quality
loop, the repair path, the acceptance rule and the pause, resume and skip behaviour are per segment, so a pause after a
segment of a batch keeps the answers of the rest and a resume makes no new batch call for them. A batch call that fails
is routed as one step named by its first segment and counts one toward the step's failure budget.

The batch size SHALL start at 8, halve when a reply loses an id, repeats one, merges two items or names an id the batch
never held, and grow by one after three clean batches, never above 16 and never below 2: a run that batches never halves
its way to drafting every segment alone. A call that got no reply from the model — a provider outage, a step the run
gave up on, an error the single draft would flag its segment for — says nothing about the size and SHALL leave it as it
was. WHILE a batch step is paused on an error, Skip SHALL skip the step's first segment, flagged as a single draft's
skip is, and the other segments SHALL batch or draft normally at their turn. Dial budgets are the chunk caps: Fast 8,
Balanced 8, Max 2 segments per chunk, so a batch is never larger than its chunk.

A batch item whose target carries protocol text of the reply SHALL fail as an item problem (`LEAKED`) and be drafted alone,
never stripped and never stored: a `terms` or `items` key followed by a colon (straight or typographic quotes), a brace
holding an `id` key or a quoted key and colon, or a code fence. A paragraph that merely uses the word "terms" or a brace in
dialogue is not protocol.

The window SHALL hold every prompt: the chunk budget reserves the dynamic context's whole allowance, the previous pairs
of a batch take at most half of it and the next source at most a quarter, and before a batch is sent its assembled
prompt, the reply its items are expected to need and the 500-token safety margin SHALL fit the run's window. A batch
that does not fit is sent without the optional context (pairs, next source, memory, suggestions, recurring terms,
characters), then with fewer segments, and drafted alone when two segments do not fit either.

**Source:** `tasks.md` 15d.8; `docs/specification/01_Product/12_PROMPT_CATALOG.md#batch-draft`. In plain words: a book
of short paragraphs no longer pays the whole prompt once per paragraph; the model answers several at once, and if it
loses one of them only that one is asked again.

#### Scenario: Four paragraphs go in one call

- **WHEN** a Fast chunk of four paragraphs is drafted and the reply answers ids 1 to 4 with their tokens
- **THEN** one draft call about the four segments is made and each segment is decided from its own entry

#### Scenario: A missing id falls back alone

- **WHEN** the reply answers ids 1, 3 and 4 of four
- **THEN** a single-segment draft is made for the second segment only, and the other three are taken from the batch

#### Scenario: A merged pair falls back for both

- **WHEN** the entry for id 2 holds the translation of items 2 and 3 and id 3 has no entry
- **THEN** items 2 and 3 are each drafted alone and the batch size halves

#### Scenario: A leaked terms tail falls back alone

- **WHEN** the entry for id 2 ends `… сказав господар. «terms»: {«master»: «господар»}}, {`
- **THEN** a single-segment draft is made for the second segment only, and its stored target holds no `terms` text

#### Scenario: Outages never switch batching off

- **WHEN** three batch calls in a row are answered with `contextWindow` and the fourth with a readable reply
- **THEN** each of the first three is followed by its single drafts and the fourth is a batch call again

#### Scenario: Skip during a batch call skips its first segment

- **WHEN** the run is paused on a provider error in the batch call about segments 1 to 4 and the person skips
- **THEN** segment 1 is flagged and segments 2 to 4 are drafted in a new batch call

#### Scenario: A refusal sends every segment to its own draft

- **WHEN** the batch reply is an apology in prose
- **THEN** each of the four segments is drafted alone, one call each

#### Scenario: A number-only paragraph is not in the batch

- **WHEN** a chunk holds a paragraph `1881` between two sentences
- **THEN** the batch carries the two sentences as ids 1 and 2 and `1881` is kept with no call

#### Scenario: A pause keeps the answered batch

- **WHEN** the run pauses after the first decided segment of a batch of four and is resumed
- **THEN** no further model call is made for the other three, which are decided from the batch

#### Scenario: Reported terms are verified before they count

- **WHEN** an accepted batch item reports `master → господар` and its target holds `господар`
- **THEN** the lexicon counts the pair; a reported rendering the target does not hold counts nothing and the item is still
  accepted

#### Scenario: A stored chunk teaches the lexicon

- **WHEN** a chunk's decided records are committed
- **THEN** its accepted drafts are counted by co-occurrence and the learned rendering of each term the chunk names is
  published to the lexicon before the next chunk's context is read; the decisions of a chunk that was not committed are not
  counted

#### Scenario: The size adapts

- **WHEN** the first batch of eight loses an id
- **THEN** the next batches hold at most four items

### Requirement: Size a run's calls from the detected window

The system SHALL read the model's context length when a run is prepared, through the provider's own detection, and size
the run's chunks, context and every request from the window `min(detected, 8192)`, or 8192 when nothing is detected or
the detection fails; a failed detection SHALL never be an error. Every request of the run SHALL carry that window as
its context size and SHALL cap its reply at half of it at most.

**Source:** `tasks.md` 15d.5 (deferred to 15d.8). In plain words: a model loaded with 4k context is no longer sent
prompts and `num_ctx` for 8k.

#### Scenario: A 4096 window

- **WHEN** the provider reports 4096 for the run's model
- **THEN** every request of the run carries the context size 4096 and an output cap of at most 2048

#### Scenario: Nothing detected

- **WHEN** the provider reports no context length
- **THEN** every request carries the context size 8192

### Requirement: Keep the prompt free of garbage and its prefix byte-identical

The system SHALL leave out of every prompt an empty value, an empty heading, a placeholder line such as `(none)`, `n/a`
or a dash, a repeated line, a glossary or locked-name line whose term does not occur in the chunk, and any preceding
translated text that is not from the chunk's own unit. The system message and style sheet of a call kind SHALL be the same
bytes for every call of a run, so a server can reuse its prompt cache. Each model call SHALL log, at DEBUG, the estimated
tokens of the system message, of the rest of the prompt and the system message's share, and, when the server reports them,
the prompt-evaluation time and the cached prompt tokens.

**Source:** `tasks.md` 15d.5; `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`. In plain words: a
small model reads every token it is given and may echo it, so nothing is sent that carries no information.

#### Scenario: Filler never reaches the model

- **WHEN** a draft context holds the preceding text `(none)`, a blank glossary line and the same memory line twice
- **THEN** the prompt has no preceding block, no glossary block and the memory line once

#### Scenario: Two calls share their prefix

- **WHEN** two drafts of different segments with different context are built in one run
- **THEN** their system messages are equal

### Requirement: Tell the model about the language pair from one language-rules map

The system SHALL build, for each source and target language of a run, one section `[Language rules: <Source> -> <Target>]`
from bundled `prompt/languages/<tag>.properties` files — the target language's rules, at most three notes on reading the
source language, and the pair's own rules from `prompt/languages/pairs/<source>-<target>.properties` — and SHALL inject it
as `{{languageRules}}` into the draft, reviewer, summary, prescan and suggest-targets system messages, with the language's
`reviewerChecks` added only in the reviewer. A language is matched by its primary subtag; a tag with no file SHALL get the
rules of `generic.properties`, so that any language the platform can name keeps working. The system SHALL open only the
files of the target, the source, their pair and `generic.properties`, SHALL build the section once per pair so that every
call of a run carries the same bytes, and SHALL keep a target's rules within 200 estimated tokens, a source's notes within
80 and a pair's rules within 100. The few-shot examples and the name-rendering lines are keys of the same files
(`example.N`, `nameExample`, `names.*`); no per-language example file and no multi-language advice stays in a static
prompt. The draft system message of English to Ukrainian SHALL be smaller than it was before the map (698 estimated
tokens). Setting `BOOKLOOM_EVAL_RULES=generic` or the system property `bookloom.eval.rules=generic` SHALL build every
section from the generic rules alone, so a prompt eval can measure what the language files add.

**Source:** `tasks.md` 15d.5b; `docs/specification/01_Product/12_PROMPT_CATALOG.md#language-rules`. In plain words: what
to watch for in Ukrainian, or in going from English to Ukrainian, is written once as data, sent only for the pair in
hand, and a language nobody has written rules for is translated with general advice instead of failing.

#### Scenario: English to Ukrainian loads only its own files

- **WHEN** the section for English to Ukrainian is built
- **THEN** only `generic.properties`, `uk.properties`, `en.properties` and `pairs/en-uk.properties` are opened
- **AND** no rule of any other language appears in it

#### Scenario: An unknown language gets the generic rules

- **WHEN** the target is a tag with no file, such as `ja`
- **THEN** the section holds the generic rules and the translation runs

#### Scenario: The reviewer also gets the reviewer checks

- **WHEN** the draft and the reviewer system messages are built for English to Ukrainian
- **THEN** only the reviewer's carries the `Check:` lines

#### Scenario: Rules forced to generic

- **WHEN** `BOOKLOOM_EVAL_RULES=generic` is set and the section for English to Ukrainian is built
- **THEN** it holds the generic rules and no Ukrainian, English or pair rule

### Requirement: Tell the model who narrates and who is present

The application SHALL add one narrator line to the run's style sheet when the brief names a narrator person
(`Narrator: first person, female. Write the narrator's own "I" verbs, adjectives and participles in the feminine form
...`, the masculine and gender-less first-person lines, and `Narrator: third person, outside the story ...`), and no
line, with the style sheet and its hash unchanged, while the narrator is unstated. The application SHALL also give each
draft, in the single and the batch prompt, a character gender sheet: one `name — gender` line for each glossary
character of known gender that the segment (for a batch, any of its items) names as a whole word, under
`[Characters in this text — keep their gender and agreement]`. The sheet SHALL have a share of its own in the dynamic
context (`ContextSection.CHARACTERS`, a tenth of what the glossary leaves, taken before the lexicon, the memory, the
preceding text and the summary), and SHALL be recorded in `ContextSnapshot.characters` so a retry shows the same. The
reviewer's user message SHALL carry the chunk's sheet under `[Characters in these pairs — who they are]` when it is not
empty, and its system message carries the narrator line through the style sheet.

**Source:** FR-TRANS-05, ADR-0038; tasks 15d.10.
In plain words: a model that is told once that the narrator is a man and that Lyra is a woman does not need to guess a
verb ending from a name, and a character who is not in the scene costs no tokens.

#### Scenario: Only the characters in the scene are listed

- **WHEN** the glossary holds Lyra (female), Hale (male), Quill (gender unknown) and Oxford (a place) and the segment is
  `Lyra walked to Oxford with Quill.`
- **THEN** the draft prompt's sheet is exactly `Lyra — female`

#### Scenario: A short window cuts the sheet before the glossary

- **WHEN** the dynamic allowance is 12 tokens and two characters and two glossary lines are offered
- **THEN** one glossary line is shown, and the sheet is empty

#### Scenario: An unstated narrator changes nothing

- **WHEN** the brief's narrator is not stated
- **THEN** the style sheet is the default sheet and no narrator line is sent

## MODIFIED Requirements

### Requirement: Send each pending segment to the model in document order

WHEN a job runs, the system SHALL start at the project's first PENDING segment — a segment kept as source by choice is
never one — and send the PENDING segments to the chat model in document order — except a segment kept as it is because
it has nothing to translate ("Keep a segment with nothing to translate as it is") and an auxiliary segment identical to
one already drafted or accepted in the run ("Send identical auxiliary text once"), which are decided with no call — grouped into chunks and decided as
"Decide a chunk's segments in document order" says. A chunk SHALL hold consecutive PENDING segments of one unit — one
EPUB spine document, one FB2 body, or a whole Markdown or TXT file — up to the chunk token budget and at most 8 segments
on Fast, 8 on Balanced and 2 on Max, and exactly 1 in the Manual review mode; a unit boundary SHALL always close a
chunk. Every generation call SHALL still carry exactly one segment, as the catalog's draft-translation prompt:

- a system message that names the source and target languages as English display names plus their raw BCP-47 tags
  (for example, `English (en)` and `Ukrainian (uk)`), carries the style sheet derived from the Book Brief, and states the
  rules: keep every `⟦gN⟧` token exactly as written, same text, order and count; follow the brief's foreign-passage
  policy; output only the required JSON object, with no commentary, fences or reasoning;
- a user message that omits unavailable context entirely, carries the context package for the segment, repeats this
  source's exact ordered `⟦gN⟧` sequence immediately before the source, renders the masked source verbatim inside
  `<Text>…</Text>`, and requests only `{"target":"<translation>"}`.

Each draft call a run makes SHALL carry the temperature `0.2` and a response format whose schema requires exactly an
object with a nonblank string `target` and no additional properties. The system message SHALL contain language-neutral structural
few-shots for paired ranges, multiple paired ranges, standalone protected content, and the final JSON shape; it SHALL
say that the few-shots teach token placement only, never their literal text.

The source language SHALL be the Book Brief's source language — the book's declared language, normalized, unless the
person changed it — and on the command line the `--from` value when given. Each available BCP-47 tag SHALL be rendered
for the model as its English display name followed by the exact tag; a tag without a display name SHALL render as
`language tag "<tag>"`. This SHALL hold for any language the application recognizes, not only a listed one. When no source language is known, the prompt SHALL call it `the language of this segment (infer
it from its text)`.

**Source:** FR-ALGO-01, FR-ALGO-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), FR-DOC-03
(`#fr-doc`), FR-BRIEF-01 (`#fr-brief`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md#draft-translation`, `#output-contract`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#chunk-packing`, ADR-0037, ADR-0038.
In plain words: the model sees only text with numbered holes where the markup was, never the book's structure. Segments
now travel in small chunks so the reviewer can read neighbours together, but each translation is still asked for one
segment at a time, the shape small models get right. Starting at the first pending segment is what lets a second run in
the same session carry on where the first stopped. The person's choice of source language wins, because a book's own
declaration is often missing or wrong.

#### Scenario: The user message is the masked text

- **WHEN** a Markdown book `Book.md` whose only paragraph is `He opened the *old* door.` is translated from `en` to
  `uk` with an empty glossary, memory and summary
- **THEN** the model receives one draft call whose user message contains `<Text>` around
  `He opened the ⟦g0⟧old⟦g1⟧ door.`, repeats `⟦g0⟧ ⟦g1⟧` immediately before it, and contains no source id,
  source JSON, empty context block, or batch wording
- **AND** its system message names `English (en)` and `Ukrainian (uk)` and the rule about `⟦gN⟧` tokens
- **AND** the call carries the temperature `0.2` and a response format

#### Scenario: No source language is named when none is known

- **WHEN** a TXT book, which declares no language, is translated to `uk` with no source language chosen
- **THEN** the system message says `from the language of this segment (infer it from its text) into Ukrainian (uk)`

#### Scenario: The requested source language wins over the book's

- **WHEN** a Markdown book whose front matter declares `lang: en` is translated to `uk` after the person set the source
  language to `de` on the Book Brief
- **THEN** the system message names `German (de)` as the source language and does not name `English (en)`

#### Scenario: A language outside the list is still named

- **WHEN** a book is translated from `en` to `la`
- **THEN** the system message names `Latin (la)` as the target language

#### Scenario: A variant tag stays precise for the model

- **WHEN** a book is translated from `zh-Hant` to `uk`
- **THEN** the system message says `from Chinese (Traditional) (zh-Hant) into Ukrainian (uk)`

#### Scenario: The dial caps a chunk

- **WHEN** a Balanced run translates a chapter of 10 short segments that fit the token budget together
- **THEN** they are packed into chunks of 4, 4 and 2 segments, and 10 draft calls are made

#### Scenario: Manual review translates one segment per chunk

- **WHEN** the same chapter is translated in the Manual review mode
- **THEN** it is packed into 10 chunks of 1 segment

#### Scenario: A chapter boundary closes a chunk

- **WHEN** a Fast run translates an EPUB whose spine documents hold 3 and 2 short segments
- **THEN** the chunks hold 3 and 2 segments, never a chunk spanning both documents

#### Scenario: A second run continues where the first stopped

- **WHEN** a run over `Book.md:0` … `Book.md:11` is stopped after 5 segments are decided, and a new run is started for
  the same project in the same session
- **THEN** the new run's first draft call is for `Book.md:5`, and no call is made for `Book.md:0` … `Book.md:4`

### Requirement: Accept a translation whose markup restores

WHEN the model replies with a normal finish and the translation read from the reply is not empty after trimming, the
system SHALL trim it and put back the leading and trailing whitespace of the segment's masked text. It SHALL then
restore the segment's markup through the placeholder gate and Markdown restoration validation, and hand the restored
text to the quality checks, the reviewer and self-heal (the `quality-gates` capability), which decide whether the segment is
ACCEPTED, repaired or FLAGGED. The restored text SHALL become the segment's machine translation once it passes every hard
gate, and a later target SHALL replace it only when that target passes every hard gate too.

**Source:** FR-DOC-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), FR-ALGO-C5, FR-ALGO-C7
(`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`),
`docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `#segment-status-machine`.
In plain words: models often add or drop spaces and line breaks around their answer, and the book's own spacing wins. A
reply whose tokens all come back is no longer accepted on that alone: it now also has to pass the automatic checks, so
restoring the markup makes it a candidate, not a final answer. A segment that is flagged in the end still keeps the last
translation that was at least well formed, so an export writes that rather than the source when one exists.

#### Scenario: A reply is restored and accepted

- **WHEN** a segment `Book.md:0` has the masked text `He opened the ⟦g0⟧old⟦g1⟧ door.` and the model replies
  `{"target":"Він відчинив ⟦g0⟧старі⟦g1⟧ двері."}`
- **THEN** the segment's machine translation is `Він відчинив *старі* двері.`
- **AND** when the quality checks accept it, the segment is ACCEPTED with that translation

#### Scenario: The segment's own whitespace wins

- **WHEN** a segment's masked text is two spaces, `Hello world` and a line feed, and the translation read from the
  reply is a line feed, `HELLO WORLD` and two spaces
- **THEN** the translation is two spaces, `HELLO WORLD` and a line feed

#### Scenario: A Markdown formatting failure receives one repair

- **WHEN** a Markdown reply returns every placeholder token but restores a paired range around only whitespace or
  moves a task-list marker away from the start of its list item
- **THEN** the system makes one formatting repair that repeats the rejected target and exact token sequence, explains
  the placement rule, and accepts only a structurally valid corrected target

### Requirement: Stop the job on any other failure

IF one of the following happens, THEN the system SHALL NOT flag the segment, SHALL leave it and every later segment
PENDING, and, WHERE pause on error is enabled, SHALL pause with that error, and otherwise SHALL end the job Failed with
that error:

- the model call answers, after the retry policy is exhausted, `ErrorCode.unreachable`, `timeout`, `auth`,
  `rateLimited`, `upstream`, `modelNotFound`, `modelUnavailable` or `missingCredential`;
- the model call itself answers `ErrorCode.validation` — the provider refusing the request as invalid.

A chat reply of HTTP `400` or `404` whose body says the server has no model loaded — LM Studio's `Model unloaded`,
`Model is unloaded`, `No models loaded`, or a model "not loaded" — SHALL be classified `ErrorCode.modelUnavailable`, not
`validation`: loading the model fixes it, which the resume capability's "Recover from a provider error by itself" waits
for.

IF the model call throws or answers `ErrorCode.internal` or any code this requirement and "Flag a segment whose reply
cannot be used, and continue" do not name — `ErrorCode.busy` and `ErrorCode.discoveryFailed` included, which a run's
model call does not answer —, or restoring the markup fails with an error other than `ErrorCode.validation`, THEN the
system SHALL leave the segment and every later segment PENDING and SHALL end the job Failed with `ErrorCode.internal`,
whether or not pause on error is enabled; an internal error SHALL never pause the run.

IF the model answers `ErrorCode.cancelled`, THEN the system SHALL end the job Cancelled.

A `validation` refusal made before any model call — a run that cannot start — is not a model-call failure and is
answered as that refusal, not as a pause.

**Source:** `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, `#boundary-discipline`,
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`,
`docs/implementation_plan/CHANGE_BACKLOG.md` (D19).
In plain words: an unreachable server, a missing key or an unloaded model would make every following call fail too.
Flagging them used to finish a run with every remaining segment flagged; now the segment waits, and the run pauses so
the person can fix the server and press Retry now. The command line pauses on an error like the window and recovers from
an outage by itself, but stops a pause only a person could end and writes what it translated. An
internal error is a bug in the application, which pressing Retry now cannot fix, so it ends the run with a dialog rather
than pausing it; a code a run's call should never answer means the same. Only a reply the model actually produced, or a prompt too long for the model, can be flagged.

#### Scenario: An unreachable model pauses the run

- **WHEN** pause on error is enabled, a book has three segments, and the model answers `ErrorCode.unreachable` for the
  second after three attempts
- **THEN** the job pauses with `ErrorCode.unreachable`, with 1 accepted, 0 flagged and 2 pending

#### Scenario: An unloaded model flags nothing

- **WHEN** pause on error is enabled and LM Studio answers `400` with the body `{"error":"Model unloaded"}` for the second
  of 40 segments
- **THEN** the job pauses with `ErrorCode.modelUnavailable`, with 0 flagged and 39 pending

#### Scenario: An unreachable model fails the job

- **WHEN** pause on error is off, a book has three segments, and the model answers `ErrorCode.unreachable` for the second
- **THEN** the job ends Failed with `ErrorCode.unreachable`, with 1 accepted and 2 pending

#### Scenario: A thrown exception fails the job as internal

- **WHEN** pause on error is enabled and the model call throws an exception for the first segment
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause, and that segment stays PENDING

#### Scenario: A restore that fails unexpectedly fails the job

- **WHEN** pause on error is enabled and restoring the markup of the first segment's reply fails with
  `ErrorCode.internal`
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause, and that segment stays PENDING

#### Scenario: An unexpected code fails the job as internal

- **WHEN** pause on error is enabled and the model call for the first segment answers `ErrorCode.discoveryFailed`
- **THEN** the job ends Failed with `ErrorCode.internal` and does not pause, and that segment stays PENDING

#### Scenario: A cancelled model call cancels the job

- **WHEN** the model answers `ErrorCode.cancelled`
- **THEN** the job ends Cancelled

### Requirement: Report progress and the outcome

WHILE a job runs, the system SHALL notify every subscriber, in order, when:

- a stage starts: preparation, translation or revision;
- a model call starts (see "Announce each model call as it starts"), and when it finishes, with its kind, its elapsed
  time, the token usage the provider reported or the estimate that stands in for it, and the length of its reply;
- a segment starts, is drafted and is decided (see "Announce each segment's text as it starts, is drafted and is
  decided");
- a name scan adds entries to the glossary, a segment is reused from the translation memory, or the rolling summary is
  refreshed;
- the job pauses, with its reason and, for a pause for review, the segment, or resumes;
- the job finishes.

Every decision and pause SHALL carry a progress snapshot: the section k of n — the current unit's position among the
book's body units, the auxiliary unit never counted and the section staying at n while the auxiliary unit is
translated —, the chunk k of n within that unit, and the auto-accepted, repaired-and-accepted, flagged and pending
counts, in none of which a segment kept as source by choice is counted. A segment accepted after at least one repair
round counts as repaired-and-accepted; any other accepted segment counts as auto-accepted.

WHEN a job ends, the system SHALL return a successful result carrying:

- the book format;
- how the job ended: Completed, Cancelled or Failed;
- the number of segments, and the accepted and flagged counts, as the project holds them when the run ends — the
  segments an earlier run decided included;
- each flagged segment with its reason, a segment flagged after its repair rounds reported with `ErrorCode.validation`;
- the error when Failed.

The job SHALL write no book and its result SHALL name no file; writing the book is the separate export (the `export`
capability).

**Source:** `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`,
`docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, ADR-0035, ADR-0038.
In plain words: a screen can follow a job live — which chapter, which chunk, how many were accepted outright and how many
needed repair — and a job that stopped still tells its caller exactly what was done and why. The section counts only the
book's own chapters, so the title, the table of contents and the alt texts never show up as a twelfth chapter of eleven.
A run now translates into memory only; the person decides when and where the book is written, after reviewing it.

#### Scenario: Events for a three-segment book

- **WHEN** a TXT book with the three segments `One.`, `Two.` and `Three.` and an empty glossary is translated on Fast
  with no pause points, and every reply is accepted on the first call
- **THEN** a subscriber receives, in order: preparation stage started; translation stage started; for each segment a
  segment-started event, a draft model-call-started event, a draft model-call-finished event, a segment-drafted event
  and a segment-decided event, the decided events having pending counts 2, 1 and 0; one memory-updated event for the
  rolling summary at the end of the file; finished as Completed
- **AND** no export stage is announced, and no memory-updated event is sent for the glossary or the translation memory

#### Scenario: Section and chunk ride on each decision

- **WHEN** a Balanced run decides a segment in the 41st of the 66 chunks of the 7th of 11 body units
- **THEN** that decision's snapshot carries section 7 of 11 and chunk 41 of 66

#### Scenario: The auxiliary unit is not a section

- **WHEN** a run over an EPUB of 11 body units, with `Book metadata (title/author)` on, decides the book's title
  `aux:title`
- **THEN** that decision's snapshot carries section 11 of 11

#### Scenario: A repaired segment is counted apart

- **WHEN** one segment is accepted after one directed fix and two are accepted on their first draft
- **THEN** the last snapshot counts 2 auto-accepted and 1 repaired-and-accepted

#### Scenario: A second run reports the whole project

- **WHEN** a run over a 12-segment book is stopped after deciding 5 segments, and a second run decides the other 7
- **THEN** the second run's Completed report counts all 12 segments

#### Scenario: A failed job still returns its report

- **WHEN** pause on error is off and a three-segment job ends Failed because the model answered `ErrorCode.unreachable`
  for the second segment
- **THEN** the result is successful
- **AND** its report says Failed with `ErrorCode.unreachable`, 3 segments, 1 accepted and 0 flagged, and names no file

### Requirement: Translate one book from the command line

WHEN the translate command runs as `./gradlew :app:translate --args="<book> [--to <lang>] [--from <lang>]
[--overwrite] [--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] [--base-url <url>]
[--timeout <seconds>] [--quality fast|balanced|max] [--names translate|transliterate|keep] [--review-names]
[--max-outage <duration>] [--report <file>] [--no-partial]"` for a supported book, the system SHALL:

- take `--provider` as `pseudo` (the default), `ollama`, `lmstudio` or `openai-compatible`; `--model` is required
  for every provider but `pseudo`; `--base-url` is required for `openai-compatible` and overrides the preset for
  `ollama` and `lmstudio`;
- register, for this run only, a provider description built from the flags: `--base-url` sets the base URL and
  `--timeout` sets the request timeout in seconds; no other setting exists;
- check the destination `<name>.<to>.<ext>` beside the book, with `--to` defaulting to `uk`, before any model call;
- for a provider other than `pseudo`, run the preflight and print one line per stage that ran, in the form
  `<stage>: ok`, `<stage>: ok (<note>)`, `<stage>: skipped` or `<stage>: failed - <title>`;
- save `--quality` as the brief's quality dial and `--names` as its name policy (`keep` is Keep original); without them
  the brief keeps its defaults (Balanced, Transliterate);
- with `--review-names`, before translating, run the names scan, Review with model (verdicts and suggested targets) and
  accept every suggested target, printing one `names: …` line with the counts; a failed step is printed and the run goes
  on with the glossary as it stands;
- translate the book in the Unattended review mode, ignoring any review-mode launch flag and writing one line to the
  log that it did, with the window's recovery: pause on an error, wait through an outage by the recovery schedule and
  the connection-and-models probe (`resume` capability) for up to `--max-outage` (default `12h`; `90m`, `1h30m`,
  `45s` or ISO-8601), printing one line when an outage begins; and, since nobody can end a pause at a terminal, stop
  the run at a pause only a person ends (`auth`, `modelNotFound`, `missingCredential`, `validation`, or an outage past
  its limit), printing `Stopping the run: <reason>`;
- then export the translation to that destination — also when the run ended Failed or Cancelled or was interrupted,
  writing what it translated with the rest in the source, unless `--no-partial` is given;
- with `--report <file>`, write a JSON summary of the run: provider, model, dial, name policy, phase timings and model
  calls, how the run ended and why, every flagged segment with its reason and finding kinds, each outage with its start,
  wakes and how it ended, the names review counts, and what the export wrote, source fallbacks included;
- print one report line naming the output and the accepted and flagged counts, as
  `Completed: <output path> (accepted=<n>, flagged=<m>)`;
- send diagnostics only to the log file, and open a connection only to the chosen provider;
- exit with code 0 when the translation completed and the export wrote the book; 3 when the run did not complete and
  what it translated was written, after printing `Partial: <output path> (accepted=<n>, flagged=<m>, pending=<p>) — the
  run ended <state>: <reason>`; 1 when nothing was written; 2 for invalid arguments, as before.

WHEN the command is interrupted (Ctrl+C) while it translates, the system SHALL cancel the running job and then export
what it translated, waiting up to 5 seconds for the job and 60 seconds more for that export; WHEN it is interrupted
while it exports, it SHALL cancel the export, wait up to 5 seconds for it to end, and leave no hidden temporary file
beside the destination.

**Source:** FR-INFER-01, FR-INFER-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md#fx-free-core`,
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`, `docs/next_features.md` §13, ADR-0035,
ADR-0036.
In plain words: the command line is the headless proof tool, so it runs a book as a window run would — the brief's dial
and names, the names review, the recovery from an outage — and never throws a night's work away: a harness run that
hit a provider error ended Failed with 5 accepted and 27 flagged segments and wrote nothing. Partial exports get their
own exit code, 3, because 2 already means a usage mistake and a script must tell the three apart. It keeps its older
flags and its output name, and translates and then exports as two steps. It checks the destination first so an existing file stops it before an hour of translation. There
is nobody at a terminal to review, so it never pauses, and a Ctrl+C no longer leaves a half-written hidden file behind.
The pseudo model answers with the source in capitals, which the quality checks rightly call untranslated, so its
paragraphs of 20 or more characters are counted as flagged — and still written, with that capitalised text.

#### Scenario: A Markdown book is translated beside itself

- **WHEN** the command runs for `Book.md`, whose only paragraph is `He opened the *old* door.`, with no `--provider`
- **THEN** `Book.uk.md` appears beside it with the paragraph `HE OPENED THE *OLD* DOOR.`
- **AND** the command prints exactly one line, `Completed: ` followed by the path of `Book.uk.md` and
  ` (accepted=0, flagged=1)`, because the pseudo model's echo of a source of 23 characters is flagged
- **AND** it opens no connection and exits with code 0

#### Scenario: A Markdown book is translated by a local Ollama model

- **WHEN** the command runs with `Book.md --provider ollama --model gemma4:e4b-mlx`, the server lists
  `gemma4:e4b-mlx`, and it answers each segment with a JSON reply
- **THEN** the command prints `connection: ok`, `models: ok` and `inference: ok (structured output: supported)`, then
  the report line
- **AND** `Book.uk.md` holds the model's translation with `*old*` restored around the translated word
- **AND** it exits with code 0

#### Scenario: A custom OpenAI-compatible server by URL

- **WHEN** the command runs with `Book.md --provider openai-compatible --base-url http://localhost:8080/v1 --model qwen3`
- **THEN** every request goes to `http://localhost:8080/v1` and none carries an `Authorization` header

#### Scenario: A zipped FB2 book keeps its double extension

- **WHEN** the command runs for `Book.fb2.zip`
- **THEN** the output file is `Book.uk.fb2.zip`

#### Scenario: An existing output stops the command before translating

- **WHEN** `Book.uk.md` exists and the command runs for `Book.md --provider ollama --model gemma4:e4b-mlx` without
  `--overwrite`
- **THEN** it exits with code 1, `/api/chat` receives no request, and `Book.uk.md` is unchanged

#### Scenario: The command line ignores the review mode

- **WHEN** the command runs with `BOOKLOOM_REVIEW_MODE` set to `manual`
- **THEN** it never pauses and the log holds one line saying the review mode was ignored

#### Scenario: An outage in the middle of a book is waited through

- **WHEN** the command runs a three-paragraph book with Ollama, the second draft answers `ErrorCode.unreachable` and the
  first probe fails
- **THEN** it prints `Provider unreachable — waiting and retrying by itself, down since …`, waits, probes again,
  resumes, and ends with `Completed: …` and exit code 0

#### Scenario: A wrong key stops the run and keeps the first paragraph

- **WHEN** the second draft answers `ErrorCode.auth`
- **THEN** it prints `Stopping the run: auth …` and `Partial: … pending=2) …`, writes the first paragraph translated and
  the other two in the source, and exits with code 3
- **AND** with `--no-partial` it writes nothing and exits with code 1

#### Scenario: Ctrl+C during translation writes what was translated

- **WHEN** the command is interrupted while the second of three paragraphs is drafted
- **THEN** the destination holds the first paragraph translated and the rest in the source, and the exit code is 3

#### Scenario: Ctrl+C leaves no temporary file

- **WHEN** the command is interrupted while it exports `Book.uk.epub`
- **THEN** within 5 seconds the process ends and no `.Book.uk.epub` file remains beside the book

### Requirement: Write a diagnostic log of every run

The system SHALL write a diagnostic log of every command run and every job to its local log file, at these levels:

- INFO: the start and end of each command run and each job, with the book's format, the source file, the languages,
  the review mode, the quality dial, the context size, how the job ended and its counts;
- DEBUG: every check a request passes or fails, every stage change, pause, resume and cancellation, and every decision
  step with the segment id, the step's inputs and its outcome — one line per step;
- TRACE: for each segment, the messages sent to the model, the model's reply, the restored translation and the live
  display texts;
- WARN: every flagged segment, a job that ends Failed, and every degraded step, such as a pause on error or an oversized
  segment that could not be split;
- ERROR: an unexpected fault, once, with its cause.

A method that only reads the job's state or counts SHALL write no line. Every line written while a segment is being
decided SHALL show that segment's id through the log line pattern. The system SHALL write book text only in TRACE lines,
and SHALL never write a secret or a credential at any level.

**Source:** DD-23 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-23-slf4j-logback`), NFR-PRIV-04
(`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#no-telemetry`), NFR-PRIV-06 and NFR-PRIV-07
(`#secrets-never-stored`), `docs/next_features.md` §12.
In plain words: the log is how a wrong book gets explained: which segment, what the model was asked, what it answered,
and which rule decided. A development run of one book once wrote 53 MB, mostly lines repeated by state queries and
paired "building/built" lines; a screen that reads the state ten times a second would make that worse. One line per
decision, each tagged with its segment, keeps the log readable. Book text is written only at the most detailed level.

#### Scenario: A TRACE run follows a segment from prompt to decision

- **WHEN** the command runs for `Book.md`, whose only paragraph is `He opened the *old* door.`, at the log level
  `TRACE`
- **THEN** `bookloom.log` contains an INFO line for the job's start naming `MARKDOWN` and an INFO line for its end
  naming `COMPLETED`
- **AND** it contains a DEBUG line naming the segment `Book.md:0` and `FLAGGED`, and a WARN line naming `Book.md:0`,
  because the pseudo model's capitalised echo fails the untranslated-echo check
- **AND** it contains TRACE lines holding `He opened the ⟦g0⟧old⟦g1⟧ door.` and `HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.`

#### Scenario: An INFO run keeps book text out of the log

- **WHEN** the same command runs at the log level `INFO`
- **THEN** `bookloom.log` contains the job's start and end lines
- **AND** it contains no DEBUG or TRACE line and no `He opened`

#### Scenario: State queries write nothing

- **WHEN** the translating screen reads the job's state and counts 600 times during a run logged at DEBUG
- **THEN** the log holds no line for those reads

#### Scenario: The segment id is on every line of its decision

- **WHEN** segment `ch07.xhtml:41` is drafted, checked and accepted in a run logged at DEBUG
- **THEN** every line written between its start and its decision shows `ch07.xhtml:41`

### Requirement: Start a run and report its progress

The translating screen SHALL start a run for the open book, as briefed, with the chosen model, and WHILE the run is in
progress SHALL report the proportion complete, the section and chunk it is in, the time spent and the time left, tokens
per second, how many segments were auto-accepted, how many were repaired and accepted, how many were flagged, how many
remain, the live panel (see "Show the segment just decided and the one in progress in the live panel"), and a log of
what the run has decided.

WHEN a run starts, the screen SHALL raise the run-started transient message whose title and text the `notifications`
capability's "Carry four severities of transient message, coloured by the status roles" defines.

The screen SHALL refuse to start when no book is open, no source language is chosen, no target language is chosen, or
no model is chosen, and SHALL say which is missing.

Each count SHALL be derived from the progress snapshot the engine emits, and from nothing else: **remaining** is that
snapshot's pending count, which never includes a segment kept as source by choice; **auto-accepted**, **repaired and
accepted** and **flagged** are its counts of the same name; the **total** is the sum of those four; and the **proportion
complete** is the auto-accepted, repaired-and-accepted and flagged counts together, over that total. The section and
chunk indices the snapshot also carries SHALL be shown in the progress line, the section as the chapter, as
`<percent>% · Chapter <k> of <n> · chunk <k>/<n>`, and SHALL NOT be used for any count or for the proportion. The
flagged tile SHALL use the warning colour.

WHERE the total is zero, the screen SHALL report the proportion complete as zero rather than dividing by it.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `#toasts`,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, ADR-0034, ADR-0035, ADR-0038.
In plain words: the engine now reports throughput, repairs and its position in the book, so the dashboard shows them
instead of refusing them. The proportion is still counted in segments, because two chapters can differ tenfold in
length. The destination is no longer chosen before the run; it belongs to Export. The mockup's toast said the window
could be closed; progress lives only in memory until storage arrives, so the message says the opposite.

#### Scenario: Counts advance as segments are decided

- **WHEN** the engine reports `700` auto-accepted, `68` repaired and accepted, `3` flagged and `469` pending in the 41st
  of 66 chunks of section 7 of 11
- **THEN** the tiles show `700`, `68`, `3` and `469` remaining
- **AND** the progress line reads `62% · Chapter 7 of 11 · chunk 41/66`

#### Scenario: The proportion ignores the chapter indices

- **WHEN** the engine reports `700` auto-accepted, `68` repaired and accepted, `3` flagged and `469` pending while its
  section is `7` of `11`
- **THEN** the proportion complete is `771` of `1240`, and is not `7` of `11`

#### Scenario: An empty book does not divide by zero

- **WHEN** the engine reports every count as `0`
- **THEN** the screen reports the proportion complete as zero and does not fail

#### Scenario: A run cannot start without a model

- **WHEN** a book is open, the target language is `uk`, and no model has been chosen
- **THEN** starting is refused with a message naming the missing model, and no run begins

#### Scenario: A run cannot start without a source language

- **WHEN** the plain-text book `notes.txt`, which declares no language, is open, the target language is `uk`, a model is
  chosen and no source language has been chosen
- **THEN** starting is refused with a message naming the missing source language, and no run begins

#### Scenario: Starting a run says to keep the application open

- **WHEN** the person presses Start and the run begins
- **THEN** one transient message titled `Translation started` reads
  `Keep the application open — progress is kept only until it closes.`
- **AND** no message says the window can be closed

#### Scenario: The log records each decision

- **WHEN** the segment `ch7 · p42` is accepted during a run
- **THEN** an entry naming `ch7 · p42` and its outcome is appended to the log

### Requirement: Learn the run's outcome even when no event is sent

The application SHALL treat the result the engine returns when a run finishes as the authoritative outcome, and
SHALL treat the events emitted during the run as progress only.

IF a run is refused before it begins and therefore emits no finishing event, THEN the screen SHALL still leave
the running state and report the refusal.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/next_features.md` §14,
`docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#state-mirror`.
In plain words: a run refused at the start — the project's book is no longer open, or the brief's language is not a
language code — returns its error without ever emitting a finishing event. A screen that trusted only the event stream
would sit spinning forever.

#### Scenario: A refused start leaves the running state

- **WHEN** a run is started for a project whose brief's target language is `../x`, which the engine refuses with
  `ErrorCode.validation` before any model call
- **THEN** the screen leaves the running state and reports the refusal, although no finishing event was emitted

#### Scenario: Events alone do not decide the outcome

- **WHEN** a run emits progress events and then returns a failure
- **THEN** the screen reports the failure rather than the last progress it saw

### Requirement: Pause and stop act without waiting for the provider

WHEN a pause or a stop is requested while a model request is in flight, the engine SHALL abort that request without
waiting for the provider to answer, and SHALL NOT send any further request to the provider — not a retry, not a
repair and not a reviewer call — after the request was made.

WHEN a pause aborted a request, the engine SHALL report the run as paused with no error, and WHEN the run is
resumed the engine SHALL make the interrupted call again — a draft, a repair or the chunk's reviewer call (see "Decide a
chunk's segments in document order") — so every segment is decided once and the run continues from where it stood.

WHEN a stop aborted a request, the engine SHALL end the run as cancelled, and SHALL NOT keep the interrupted
segment, or any other undecided draft of its chunk, as a decision.

The engine SHALL NOT interrupt any work other than a model request: the wait while paused and the deterministic steps
between calls are never interrupted by a pause or a stop; they take effect at the next boundary.

**Source:** FR-RESUME-03, FR-RESUME-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`),
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, ADR-0035.
In plain words: a local model can take minutes to answer, and a request that times out is retried up to three
times. So the button aborts the request that is waiting, and nothing further is sent. The call that was aborted has no
answer yet, which is why resuming sends it again instead of skipping it — whether it was a segment's draft, one of its
repairs or the reviewer call for its chunk. The run no longer writes the book, so there is no longer an export for a pause
to stay out of; export is its own action.

#### Scenario: A stop aborts a slow request

- **WHEN** the provider is taking `30` seconds to answer the only segment's request, and a stop is requested `1`
  second after the request was sent
- **THEN** the run ends as cancelled within `5` seconds and the provider has received exactly `1` request

#### Scenario: A stop sends no repair request

- **WHEN** the first reply is not the required JSON object, which would normally be followed by a structural repair
  request, and a stop is requested while that first request is still in flight
- **THEN** the run ends as cancelled and the provider has received exactly `1` request

#### Scenario: A pause during the wait before a retry sends no further request

- **WHEN** the first request timed out after `300` milliseconds, the engine is waiting before its retry, and a pause
  is requested
- **THEN** the run reports paused with reason `REQUESTED` and no error, and the provider has received exactly `1`
  request

#### Scenario: A resumed run translates the interrupted segment again

- **WHEN** a two-segment book has its first segment accepted, a pause aborts the request for the second, and the run
  is resumed
- **THEN** the provider receives that second segment's request a second time, so `3` requests in all, and the final
  report counts `2` segments and `2` accepted

#### Scenario: A pause during a deterministic step waits for the boundary

- **WHEN** a pause is requested while a Max run substitutes locked terms in its revision stage
- **THEN** the substitution in progress completes and the run pauses before its next model call

#### Scenario: A pause never interrupts the export

- **WHEN** a run has finished, the person exports its project, and a pause is then requested on that run
- **THEN** the pause request is ignored and the export writes the book

### Requirement: Announce each model call as it starts

WHEN the engine starts a model call, it SHALL emit one event naming the call's kind — draft, structural repair,
placeholder repair, reviewer, directed fix, pre-scan, summary or revision — and the segment the
call belongs to when it belongs to one, before waiting for the answer, and SHALL NOT emit it for a call that was refused
because a pause or a stop was already requested. Each such call SHALL emit its own event. The client's own retries of
one call — a timeout, a `Retry-After` wait — belong to that same call and SHALL NOT emit another event.

**Source:** FR-ALGO-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`.
In plain words: between two segment decisions the engine may now make several calls — a draft, a reviewer call, a repair — and
a local model can take minutes for each. Naming the kind lets the screen and the log say what the model is doing, and
lets throughput count only drafts. A provider client that retries a timed-out request is still waiting on the same ask.

#### Scenario: One event before each decision

- **WHEN** a two-segment book is translated on Fast and each segment is answered by its first call
- **THEN** the model-call events are, in order, a draft for `Book.md:0` before that segment's decision and a draft for
  `Book.md:1` before that segment's decision, and no export stage is announced

#### Scenario: A repair is its own call

- **WHEN** the first reply for `Book.md:0` is not the required JSON object and the structural repair call is answered
  correctly
- **THEN** a draft event and a structural-repair event for `Book.md:0` are emitted, one before each call, before that
  segment's decision

#### Scenario: The reviewer is announced by kind

- **WHEN** a Balanced run reviews the chunk holding `ch07.xhtml:40` and `ch07.xhtml:41`
- **THEN** one model-call event of kind review is emitted for that chunk

#### Scenario: The client's retry does not announce again

- **WHEN** the first attempt of the draft call for `Book.md:0` times out and the client sends its second attempt
- **THEN** exactly one model-call event for `Book.md:0` was emitted for that draft call, and none for the second
  attempt

#### Scenario: A refused call is not announced

- **WHEN** a pause is already requested when the engine is about to make a model call
- **THEN** the provider receives no request and no model-call event is emitted

### Requirement: End a model call that outlives its ceiling

WHILE a run is running, the system SHALL watch its model calls on a daemon ticker of its own (every 5 s, started and
stopped with the run, every failure of a check logged and swallowed) and SHALL end a call — interrupting it through the
job's control, as a pause does, so the provider client gives it up and the inference gate is released — when the attempt
has been outstanding longer than 1.5 times its own timeout (15 min for a call whose model announces none), or when no
segment has been decided for 20 minutes while a call is outstanding. The ended call SHALL answer `ErrorCode.timeout`,
log one WARN line with the call's kind, segment and elapsed time, and go through the run's ordinary timeout path: it
pauses, recovers by itself, and after two such pauses the segment is flagged and the run goes on. Both provider
clients bound a whole reply by the request timeout (the OpenAI-compatible client's JDK request timeout covers a body
that stalls after its head), so the watchdog is the last resort behind them, not the first.

**Source:** the overnight plan, step 11 (a stalled call must never hold a night's run).
In plain words: a provider that accepts a request and never finishes it would otherwise keep the run waiting forever.

#### Scenario: A reviewer call that never returns is ended at 135 s

- **WHEN** a reviewer call with a 90 s timeout has been outstanding for 136 s
- **THEN** the watchdog ends it, the gate is released, and the call answers `ErrorCode.timeout`

#### Scenario: A call that never returns is retried, then flagged

- **WHEN** the draft of `Book.txt:1` never returns three times in a row
- **THEN** the watchdog ends each attempt, the run pauses and recovers by itself twice, flags `Book.txt:1` with
  `ErrorCode.timeout` on the third, and ends Completed with two accepted

### Requirement: Run a whole book in bounded memory

The system SHALL keep nothing that grows with the number of model calls or log lines of a run, and SHALL keep per
segment only what the in-memory store holds for it (its record, targets, snapshot and memory entry). In particular:
the run summary SHALL count every call but take its 95th percentile over the newest 1,000 call times only
(`RunSummaryLogger.PERCENTILE_WINDOW`); a step's pause count SHALL be forgotten once the step answers, is flagged or
ends the run; the whole-word pattern cache shared by every run of a session SHALL hold at most 4,096 patterns
(`WholeWord.MAX_PATTERNS`). The window SHALL show at most 500 activity-log entries (`StateMirror.MAX_LOG_ENTRIES`) and
queue at most as many between two ticks, dropping the oldest; remember at most 256 segments' locators and repair rounds
(`ActivityLogFeed.MAX_LOCATORS`); keep at most 256 recent failed attempts for the connection chip
(`ConnectionHealth.MAX_FAILURES`); keep at most 32 undecided live rows; and grow the flagged queue by appending the new
rows rather than replacing the rows it already shows. Logging SHALL stay synchronous and size-capped, with no queue
that a slow disk could fill.

**Source:** the overnight plan, step 13 (a whole book left running all night must not run out of memory).
In plain words: a 3,700-segment book under injected faults retains about 2.5 KB per decided segment and nothing else
that grows; the soak harness (`./gradlew :pipeline:soak`) proves it.

#### Scenario: A night's calls do not grow the run summary

- **WHEN** a run makes 1,500 model calls, the first 500 of 10 s and the next 1,000 of 1 s
- **THEN** the final summary line reads `calls=1500 avgCallMs=4000 p95CallMs=1000` and holds 1,000 call times

#### Scenario: A whole book under faults finishes in bounded memory

- **WHEN** a generated book of 3,700 paragraphs runs on the Balanced dial over a model that times out, hangs, answers
  5xx bursts, is unreachable for 25 minutes once, unloads its model, answers empty, damages placeholders, refuses,
  answers the reviewer with no JSON and throws
- **THEN** the run ends Completed with no segment pending, the export re-opens with every segment, the live threads
  are as many as before, and the retained heap grows by less than 16 KB per segment

### Requirement: Repair invalid structured draft replies once

IF a draft reply is malformed or wrong-shaped — anything but one complete JSON object with exactly one nonblank string
`target`, a blank `target` included — THEN the system SHALL issue exactly one structural repair for the same segment.
That repair SHALL use the same response format, include the delimited rejected reply and a parsing diagnosis, and ask
for only the exact JSON object. IF it is again invalid, THEN the segment SHALL be FLAGGED at once with
`ErrorCode.validation`, with no self-heal round, and no wrapper text reaches the document unmasker.

IF a strictly valid `target` fails placeholder validation — a `⟦gN⟧` token of the document or of a protected span
missing, repeated or out of order, or the restored markup refused — THEN the system SHALL try, in this order, stopping
at the first that passes the unchanged placeholder hard gate:

1. the document port's deterministic repair in restore-missing mode (document-round-trip, "Repair a refused target's
   placeholder tokens without a model"), with no model call;
2. exactly one model repair containing the original source, the rejected target, the exact required ordered token
   sequence, and a note naming what is wrong — the gate's reason, each missing token, each extra token, each token the
   source never had (`⟦g1⟧ is not a placeholder of this text; write names as plain text.`), each pair out of order, and
   the text each affected pair wraps in the source (for example `In <Text>, ⟦g0⟧…⟦g1⟧ wraps "“A"`) — with one worked
   example in the prompt;
3. on that repair's reply, the deterministic repair in restore-missing mode, then in re-place-all mode; and when that
   repair's reply is unusable (empty, cut off, not the JSON object), the re-place-all repair of the draft's reply.

A piece of a segment drafted in pieces SHALL count as keeping its tokens when its own tokens are in order and every
other token is glued to a word, which the whole segment's restore-missing repair then drops; a piece with a token it
invented in place of a word SHALL get its one placeholder repair with the same note. The repair SHALL log
`Placeholders restored without a model: put back N, removed M`.

A target that passes only through a deterministic repair SHALL carry a low `markup` finding raised by `placeholder`
("Markup auto-restored"), which neither blocks acceptance nor lowers confidence and is shown in review. IF every step
fails, THEN the segment SHALL NOT be flagged at once: it SHALL go to self-heal with a `markup` finding, whose directed
fix states the expected token sequence and whose reply gets the restore-missing repair, and SHALL be FLAGGED only after
the dial's repair rounds fail (the `quality-gates` capability). Such a segment keeps no machine translation, but SHALL
keep the last refused reply, its protected spans put back, as its rejected target, so review shows the model's words
labelled as no usable translation instead of the source; export writes its source. A draft that the placeholder gates
restored but a blocking text check refused (`script-purity`, `quote-balance`, `language-identity`) keeps no machine
translation either, and SHALL likewise keep its restored text as its rejected target when it is FLAGGED, so the
translated sentence is never lost with the check that refused it.

A drop cap — a pair wrapping one or two visible characters at the start of a word, glued to its rest, such as
`⟦g0⟧“A⟦g1⟧bove` — SHALL be folded out of the text the model is shown (`“Above`), with its tokens left out of the
required token sequence, and SHALL be put back by the restore-missing repair, with no auto-restored finding.

Each draft SHALL get at most one structural repair and one placeholder repair, the reply of either repair being read by
the same rules, and neither repair SHALL count toward the dial's repair rounds. A self-heal call SHALL get neither
repair: a self-heal reply that is not the required JSON object or fails the placeholder hard gate fails that round.

**Source:** FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
FR-ALGO-C11, FR-ALGO-C12 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`),
`docs/specification/01_Product/12_PROMPT_CATALOG.md#output-contract`, `#directed-fix-repair`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#repair-and-gate`, ADR-0013.
In plain words: a reply in the wrong shape is usually a formatting slip, so it gets one plain request for the right
shape; a second slip means the model cannot produce the shape for this segment, and asking again would only burn time.
A reply that lost or moved a token is a different problem: a small model that dropped one token of a pair usually got
every word right, so the token is first put back where the source had it, with no call; only a reply that cannot be
fixed that way gets one model repair told exactly which tokens are wrong and what they wrapped, and if that still fails,
the dial's repair rounds try a directed fix before the segment is flagged. A drop cap cuts a word in two when it is
shown with its tokens — `⟦g0⟧“A⟦g1⟧bove` — and a small model then drops a token or wraps the whole paragraph, so it is
shown as the whole word and wrapped again on its first letter afterwards. The two format repairs are free, so a slip
never eats the rounds meant for the translation itself.

#### Scenario: A structural repair receives diagnostic data

- **WHEN** a reply begins `Here is the translation: {"target":"Привіт"}`
- **THEN** the repair includes that rejected reply and reports that the reply was not valid JSON

#### Scenario: A second invalid structured reply is flagged

- **WHEN** the original reply and its one repair reply are invalid structured output
- **THEN** the segment is FLAGGED with `ErrorCode.validation` and the model is called exactly twice
- **AND** no self-heal call is made for it

#### Scenario: A blank target is a structural failure

- **WHEN** the draft reply is `{"target":""}` and its structural repair reply is `{"target":"  "}`
- **THEN** the segment is FLAGGED with `ErrorCode.validation` after exactly 2 model calls

#### Scenario: A dropped closing token is put back without a call

- **WHEN** the masked source is `“Remember ⟦g0⟧this,”⟦g1⟧ he said in a soft voice.` and the draft answers
  `{"target":"«Пам'ятай ⟦g0⟧це», — сказав він тихим голосом."}`
- **THEN** the draft restores as `«Пам'ятай <i class="calibre3">це»,</i> — сказав він тихим голосом.` after exactly
  1 model call
- **AND** it carries a low `markup` finding raised by `placeholder`

#### Scenario: A placeholder repair names the required sequence

- **WHEN** `{"target":"⟦g1⟧Привіт⟦g0⟧"}` is valid JSON but a segment requires `⟦g0⟧ ⟦g1⟧` in that order, so no
  restore-missing repair can fix it
- **THEN** the one repair contains the original source, rejected target, and `⟦g0⟧ ⟦g1⟧`; no unmask succeeds until a
  target with that sequence passes the gate

#### Scenario: A placeholder repair names what is wrong

- **WHEN** the masked source is `“Remember ⟦g0⟧this,”⟦g1⟧ he said in a soft voice.`, the draft answers with the pair
  swapped, `«Пам'ятай ⟦g1⟧це»,⟦g0⟧ — сказав він тихим голосом.`, and the repair answers
  `«Пам'ятай ⟦g0⟧це», — сказав він тихим голосом.`
- **THEN** the one repair contains `⟦g0⟧ ⟦g1⟧`, `Out of order: ⟦g1⟧ comes before ⟦g0⟧.` and
  `In <Text>, ⟦g0⟧…⟦g1⟧ wraps "this,”"`
- **AND** the repair's reply restores with `⟦g1⟧` put back after `це»,`, after exactly 2 model calls

#### Scenario: A drop cap is shown as a whole word

- **WHEN** the masked source is `⟦g0⟧“A⟦g1⟧bove all,” said his master.`, a `<span>` drop cap, and the draft answers
  `{"target":"«Понад усе», — сказав його господар."}`
- **THEN** the draft was shown `“Above all,” said his master.` with no required token
- **AND** it restores as `<span class="calibre8">«П</span>онад усе», — сказав його господар.` with no finding

#### Scenario: The placeholder repair names the rule the reply broke

- **WHEN** the masked source is `The ⟦g0⟧second⟦g1⟧ marked paragraph.` and the draft answers
  `{"target":"Другий ⟦g0⟧⟦g1⟧ позначений абзац."}`
- **THEN** the gate refuses the restore because the pair `⟦g0⟧⟦g1⟧` encloses no text
- **AND** the one placeholder repair states that reason beside the required sequence `⟦g0⟧ ⟦g1⟧`

#### Scenario: A placeholder failure after its repair goes to self-heal

- **WHEN** a Balanced run (two repair rounds) drafts `Book.md:0`, whose masked source is
  `He opened the ⟦g0⟧old⟦g1⟧ door.`, and the model replies `{"target":"Відчинив"}` — one word, which cannot hold the
  pair and keep text outside it — to the draft, to its placeholder repair and to every directed fix
- **THEN** the segment is not flagged after the placeholder repair; two directed fixes follow, each stating
  `⟦g0⟧ ⟦g1⟧`
- **AND** the segment is FLAGGED with a high `markup` finding after exactly 4 model calls for it, and no reviewer call shows
  it

## REMOVED Requirements

### Requirement: Flag a segment the model could not translate, and continue

**Reason**: It flagged a segment when the model call itself answered `ErrorCode.validation`. That is the provider
refusing the request, which repeats for every later segment; LM Studio's `400` "Model unloaded" flagged every remaining
segment of a real run (backlog D19). The requirement is renamed for what it now flags — a reply that cannot be used,
and a context overflow particular to one segment — so it is replaced rather than modified in place.

**Migration**: Replaced by "Flag a segment whose reply cannot be used, and continue", which flags at once only a reply
the model produced that is empty, cut off, or twice not the required JSON object, and a context overflow — a reply whose
markup does not restore now goes through self-heal first (the modified "Repair invalid structured draft replies once")
— and by the modified "Stop the job on any other failure", which pauses the run (or fails it where pause on error is
off) on every provider refusal and ends it Failed on an internal error.

### Requirement: Refuse a job that cannot start

**Reason**: The run no longer writes the book (ADR-0035), so it has no destination to check; three of its scenarios
test destination checks that now belong to the export.

**Migration**: Replaced by "Refuse a run that cannot start" for the language, project, book and run-once checks. The
destination checks — the file type, the source itself, an existing file without overwrite — move to the export's
refusals in the `export` capability; the command line still checks the destination before translating.

### Requirement: Give every activity-log entry a kind, a status role and a catalogue message

**Reason**: Its scenario "No rate and no estimate are shown" is now deliberately false: the Translating screen shows
tokens per second and the time left.

**Migration**: Replaced by "Tag every activity-log entry with one of seven kinds", which keeps the seven kinds, their
status roles, the catalogue text and the bound, and adds the `ok/fix/mem/sum/retry/err/info` tags, the monospace
rendering and which event produces each kind.
