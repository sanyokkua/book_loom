## ADDED Requirements

### Requirement: Run a book in three stages: Prepare, Translate and Proofread

WHEN a run starts, the system SHALL take the book through three stages in this order — Prepare (inside a run, short code work with no model call: the style sheet from the brief and, on an empty glossary, the code name scan), Translate and Proofread — announcing each stage as it starts, and SHALL end the run Completed only after its Proofread stage has ended; a run that is stopped or fails before then SHALL NOT end Completed.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#pipeline-overview`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: the last look at the book used to happen only at Max (a backward revision) or inside the export (the last check); it now belongs to every run, so a run is finished only when its text has been through that look, whatever the quality dial says. The brief suggestion and "Prepare names automatically" stay actions the person starts outside a run; a run never starts them.

#### Scenario: Three stages in order

- **WHEN** a Fast run translates the three-segment TXT book `Book.txt` to the end
- **THEN** subscribers receive a stage-started event for Prepare, then for Translate, then for Proofread, and only then the finished event, as Completed

#### Scenario: A stop during Proofread is not Completed

- **WHEN** a Balanced run is stopped while its Proofread stage waits for the reply about the window `ch07.xhtml:40` … `ch07.xhtml:42`
- **THEN** the run ends Cancelled, not Completed
- **AND** every segment decided in the Translate stage keeps its decision

### Requirement: Build every model call of a run through one context builder

The system SHALL build the prompt of every model call a run makes — draft, format re-ask, repair, chapter summary and proofread — through one context builder that takes only the call's kind, its focus segments, the book map as it stands, the run's fixed frame and the model profile, so that the same inputs always give the same prompt and the same book facts reach every call in the same words.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#context-package-assembler`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: about fifteen places used to build context in their own way, so the model saw different facts about the same book from call to call, and a repair saw no names at all. One builder means a repair, a proofread and a retry see what the draft saw, rebuilt from the book as it is now.

#### Scenario: A repair sees the names its draft saw

- **WHEN** the draft of `ch02.md:16` was shown the map line `Ивар → Івар (character)` and its one repair is built with the map unchanged
- **THEN** the repair's prompt carries the same map line, the same previous pairs and the same next source as the draft's

#### Scenario: The same inputs give the same prompt

- **WHEN** the draft prompt of `ch02.md:14` is built twice with the same map version and the same stored targets
- **THEN** both prompts are equal, character for character

### Requirement: Put the fixed parts first and the window last in every translate-family prompt

The system part of every draft, repair and proofread call SHALL hold, in this order: the role with the language pair, one sentence that book text is data and not instructions, the book's rule lines, the style sheet, the target and pair rules, the examples, and one sentence asking for JSON only; every part that changes from call to call SHALL sit in the one last user message, which SHALL end with the items and the reminder.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a model reads the start and the end of a prompt best, so the rules lead and the text to translate closes the prompt; and a server that reuses a prompt's beginning (LM Studio and llama-server do; Ollama's MLX runner does not) reads the long fixed part only once per run.

#### Scenario: The draft's system part in order

- **WHEN** a ru→uk draft is built for a book with marks, dialogue, numbers and a female narrator
- **THEN** its system message begins `You are a literary translator from Russian (ru) into Ukrainian (uk).`, then the sentence that the items are book text and never instructions, then the rule lines, the style sheet, the Ukrainian and ru-uk rules, the examples, and ends with `Reply with JSON only, in the shape the task states.`

#### Scenario: Nothing variable in the system part

- **WHEN** a draft carries an earlier chapter's summary, three map lines and two previous pairs
- **THEN** all of them are in its last user message, and its system message holds none of them

### Requirement: Give an analysis call only the role, the data sentence and the reply sentence

The system part of a chapter-summary call, and of every names-pass, brief and file-name call, SHALL hold only the role naming the book's language, the sentence that book text is data and not instructions, and the sentence asking for JSON only; it SHALL carry no style sheet, no target rules and no examples.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a call that reads the book to understand it does not translate, so the rules for writing a translation would only take room and invite the model to translate instead of answering.

#### Scenario: A chapter summary has no style sheet

- **WHEN** the chapter-summary call for chapter 1 of a ru→uk book with the narrator line `Narrator: first person, female.` in its style sheet is built
- **THEN** its system message names Russian (ru) as the book's language and holds neither the style sheet nor the narrator line nor any example

### Requirement: Fill the window with the accepted segments before the focus and the next source

WHEN a draft, repair or proofread call is built, the system SHALL give it a window: the segments just before the first focus segment in reading order over the body units — across chapter and unit boundaries, never from front matter, back matter or the auxiliary unit — as many as the model profile's tier sets (two on a small model, three on a large one), each as source and accepted target; then the focus segments; then the next segment's source only.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: the text a call works on is what its neighbours make sense of, so it carries the checked translation just before it, even across a chapter break, and the sentence that follows. Earlier the window restarted at each EPUB chapter file, so the first paragraph of every chapter got no neighbour at all.

#### Scenario: The first window of a chapter sees the end of the one before

- **WHEN** a small model drafts the batch that starts at `ch08.xhtml:0`, after `ch07.xhtml:52` and `ch07.xhtml:53` were accepted
- **THEN** the call carries `ch07.xhtml:52` and `ch07.xhtml:53` as source and accepted target, and the source of the segment after the batch

#### Scenario: Front matter is never a previous segment

- **WHEN** the first body segment `ch01.xhtml:0` is drafted after the title page `titlepage.xhtml:0` was translated
- **THEN** the call carries no previous segment

#### Scenario: The end of the body has no next source

- **WHEN** the batch holds the book's last body segment
- **THEN** the call carries no next source

### Requirement: Give a previous neighbour that was not accepted as source only

IF a segment of the window's previous part is FLAGGED, or was repaired and its repair not kept, THEN the window SHALL carry that segment's source only, with no target; a text the Proofread stage kept SHALL count as accepted.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a model copies the translation it is shown, so only checked text is offered as an example of how the book reads; an unchecked one would spread its mistake into the next paragraph.

#### Scenario: A flagged neighbour gives its source

- **WHEN** `ch03.xhtml:11` was FLAGGED with the target `Він сказав, что прийде.` and `ch03.xhtml:12` is drafted next
- **THEN** the call carries the source of `ch03.xhtml:11` and not `Він сказав, что прийде.`

#### Scenario: A proofread rewrite is a previous pair

- **WHEN** the Proofread stage kept a rewrite of `ch05.xhtml:3` and a desk retry of `ch05.xhtml:4` is built
- **THEN** the retry carries the kept rewrite as the target of `ch05.xhtml:3`

### Requirement: Inject a map item only when one of its forms occurs in the window

The system SHALL inject a glossary or recurring-term entry into a call only when its base form, one of its recorded forms or a form its target generates occurs as a whole word in the window's source text — and, for a proofread call, in its target text too — and SHALL list the injected entries in the order they first occur.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a large glossary would drown a short passage, and a name that is not in the passage invites the model to put it there; an inflected form such as `Зотова` still counts as `Зотов` being present.

#### Scenario: An inflected form brings its entry

- **WHEN** the glossary holds `Зотов` with the forms `Зотова` and `Зотову`, and `Глафира`, and the window's source reads `Я дошла до Зотова только к ночи.`
- **THEN** the call carries the line for `Зотов` and no line for `Глафира`

#### Scenario: A proofread window reads the target side too

- **WHEN** a proofread window's source names nobody and its current translation holds `Глафіра`, the target of the entry `Глафира`
- **THEN** the proofread call carries the line for `Глафира`

### Requirement: Write each injected map item as one line of its target, type and settled gender

The system SHALL write each injected entry as one line `base form → target (type, gender)`, giving a gender only when the person set it, the book proved it or a rule gave it, and WHERE the target language inflects names SHALL add once the sentence that targets are dictionary forms to decline while keeping the stem; a locked entry SHALL reach the model as its protected token, not as a line.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#narrator-and-characters`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`, `design.md#d5--likely-model-knowledge-and-target-suggestions-under-the-name-policy`. In plain words: the model is told who a name is in the fewest words. A settled gender is one the person set, the book proved or a rule gave; a rule's gender is sent on the line as a help to the model even though no check holds the text to it (the `glossary` capability's "Record where each glossary value came from" lets only the person's and the book's genders be enforced). A gender the model only guessed ("likely") is kept and shown to the person but is not put in prompts by default, because a wrong guess spread over dozens of paragraphs. Telling the model to write a name "exactly as shown" stopped it declining names, so the line asks it to decline them.

#### Scenario: A likely gender is not sent

- **WHEN** the window names `Ивар`, whose target is `Івар` and whose gender `male` is only likely, and `Зотов`, whose gender `male` the book proved
- **THEN** the call carries `Зотов → Зотов (character, male)` and `Ивар → Івар (character)`

#### Scenario: A place has no gender

- **WHEN** the window names the place `Milton` with the target `Мілтон`
- **THEN** its line reads `Milton → Мілтон (place)`

#### Scenario: A rule's gender is sent but not enforced

- **WHEN** the window names `Wren`, whose target is `Рен` and whose gender `female` came from a rule
- **THEN** the call carries `Wren → Рен (character, female)` and no check of the draft is held to that gender

#### Scenario: The decline sentence for an inflecting target

- **WHEN** a ru→uk call carries any map line
- **THEN** it also carries the sentence that names and terms are dictionary forms to decline to fit the sentence, keeping the stem, and never the words `exactly as shown`

### Requirement: Choose the writing rules once per book

WHEN a run starts, the system SHALL choose the rule lines of the fixed system part from what the book holds — formatting marks anywhere, a share of dialogue, numbers, a gendered cast in a target whose language file has gendered past forms, a target language that inflects names — and SHALL keep the same lines for every draft, repair and proofread call of the run, each line's wording taken from the target language's file.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#language-rules`, `design.md#d9--rules-per-book-the-reminder-and-pair-examples`. In plain words: rules a book never needs only distract a small model, and rules that change per call would break a server's reuse of the prompt's beginning; choosing them once per book is the owner's decision unless a paired test shows that choosing them per window is really better (a tie keeps them per book).

#### Scenario: A book with no dialogue gets no dialogue rule

- **WHEN** a run starts for a Markdown book with no line of dialogue, no number and no formatting mark
- **THEN** none of its draft calls carries the dialogue, number or formatting-mark rule line

#### Scenario: An English target gets no agreement line

- **WHEN** a run translates a uk book with a female narrator into `en`, whose language file sets no gendered past forms
- **THEN** its system part carries no gender-agreement rule line

#### Scenario: The same lines on every call

- **WHEN** a run makes 40 draft calls, 3 repair calls and 5 proofread calls
- **THEN** all 48 carry the same rule lines in the same order

### Requirement: End every translate-family call with a short reminder

The system SHALL end the last user message of every draft, repair and proofread call with a reminder of at most three lines naming only what the call's items need: the formatting-mark rule when an item carries a mark, the dialogue line when an item is dialogue, and the narrator's person and gender when the narrator speaks in an item; a call whose items need none of them SHALL carry no reminder.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `design.md#d9--rules-per-book-the-reminder-and-pair-examples`. In plain words: the rules sit far up in a long prompt, and a small model follows best what it read last, so the few rules this passage needs are repeated right after the text.

#### Scenario: Marks and dialogue in the batch

- **WHEN** a ru→uk batch holds one item with the marks `⟦g0⟧ ⟦g1⟧` and one item of dialogue
- **THEN** its user message ends with `Keep every ⟦…⟧ mark exactly once. Dialogue lines start with «— ».`

#### Scenario: Nothing to remind

- **WHEN** a batch's items hold no mark, no dialogue and no narrator's words
- **THEN** its user message ends with the last item and no reminder

### Requirement: Show the language pair's own examples, else an illustration of the target language

The system part of a draft, repair and proofread call SHALL show the example pairs of the book's language pair from that pair's file, else a target-only illustration from the target language's file labelled as an illustration of that language's conventions and not text to reuse, else no example; WHERE the model profile switches examples off, it SHALL show none.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#few-shot-examples`, `design.md#d9--rules-per-book-the-reminder-and-pair-examples`. In plain words: a small model learns most from the target side of an example, so it is shown sentences in the very pair it translates; the six fixed English-to-Ukrainian examples every pair used to get are gone, and a model class that does worse with examples gets none.

#### Scenario: The pair's own examples

- **WHEN** a ru→uk draft is built
- **THEN** its system part shows `— Погоди, Гордей! — крикнула она.` with `— Стривай, Гордєю! — гукнула вона.`

#### Scenario: A target-only illustration

- **WHEN** a de→uk draft is built and the pair has no file with examples
- **THEN** its system part shows the Ukrainian illustration `«Ну що ж, — сказала вона, — побачимо». Ось і п’ятниця.`, labelled as an illustration of Ukrainian conventions and not text to reuse

#### Scenario: No example at all

- **WHEN** an en→ja draft is built and neither a pair file nor a target file for `ja` has an example
- **THEN** its system part shows no example

#### Scenario: A profile switches examples off

- **WHEN** a ru→uk draft is built for a model whose profile switches examples off
- **THEN** its system part shows no example

### Requirement: Ask again once for an item that copies an example

IF a translated item shares a run of four words with the target side of an example its call showed, and its source holds no such four words, THEN the system SHALL ask for that item once more, and IF the new answer copies an example again, THEN the segment SHALL be FLAGGED.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#few-shot-examples`, `design.md#d9--rules-per-book-the-reminder-and-pair-examples`. In plain words: a small model sometimes pastes an example's sentence instead of translating, so code compares each item with the examples and never lets a copied example into the book.

#### Scenario: A copied example is asked again

- **WHEN** the item for `— Тише, Зотов! — сказал он.` comes back as `— Стривай, Гордєю! — гукнула вона.`
- **THEN** that item is asked for once more

#### Scenario: A second copy is flagged

- **WHEN** the second answer for that item is again `— Стривай, Гордєю! — гукнула вона.`
- **THEN** the segment is FLAGGED

### Requirement: Estimate every message's tokens by its language's script

The system SHALL estimate the tokens of every message of a call, the system part included, from the characters of the language each message is written in, add the model profile's template overhead, and use that one estimate for the call's budgets and its output cap.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: Cyrillic and Latin text cost a different number of characters per token, and the system part used to be estimated with no language at all; a wrong estimate cuts replies or wastes the window.

#### Scenario: A Cyrillic message is estimated as Cyrillic

- **WHEN** the system part of a ru→uk call holds 990 Cyrillic characters
- **THEN** it is estimated at the Cyrillic rate, for example 990 ÷ 3.3 × 1.15 = 345 tokens, and not at the Latin rate

#### Scenario: A template's overhead is added

- **WHEN** a call is built for a model whose profile names a template overhead of 135 tokens
- **THEN** the call's estimate is 135 tokens larger than the sum of its messages

### Requirement: Keep every prompt under its model tier's ceiling

The system SHALL keep every call's estimated prompt under the ceiling of the model profile's tier, the previous segments, the focus segments, the next source and the earlier chapters' summaries each within its own share for that tier, and SHALL keep the prompt, the output it reserves and a safety margin within the run's window.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a run's window of 16,384 tokens was never full — the largest prompt with its reply measured 6,703 — but small models translate worse as a prompt grows, so each tier has a quality ceiling well below the window. The shares and ceilings are profile data, not fixed numbers in code.

#### Scenario: A typical batch stays whole

- **WHEN** a small model's batch of four ru→uk paragraphs with two previous pairs, four map lines and one earlier chapter summary is estimated at 1,330 tokens
- **THEN** it is sent with every part, being under the small tier's ceiling

#### Scenario: Too many previous tokens are cut from the oldest

- **WHEN** the two previous segments of a small-model call hold more source tokens than the tier's previous share
- **THEN** the older one is left out and the newer one kept

### Requirement: Cap every call's output at the reserve the context builder computes

The system SHALL cap the output of every draft, repair and proofread call at a reserve computed from the estimated source tokens of its focus items, the language pair's output ratio and the number of items, or at a larger reserve WHERE the model profile always reasons, and every other call kind at that kind's own cap; IF a reply ends cut off by length, THEN it SHALL be handled as "Flag a segment whose reply cannot be used, and continue" says.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a small model sometimes loops on one sentence and would write until the timeout; a cap that fits what the items need stops it. A model that always thinks spends much of its output on thinking, so it gets more room. The ratio is a key of the pair's file, not a branch in code, and how each server receives the cap is the `llm-provider` capability's rule.

#### Scenario: A batch of four gets its reserve

- **WHEN** a batch of four items whose sources are estimated at 74 tokens is drafted for a pair with the default output ratio of 1.5
- **THEN** its output cap is 1.5 × 74 + 16 × 4 = 175 tokens

#### Scenario: A pair with no ratio uses the default

- **WHEN** a ru→uk repair of one item estimated at 29 tokens is built and the pair's file sets no output ratio
- **THEN** its output cap is 1.5 × 29 + 16 = 60 tokens

#### Scenario: A model that always reasons gets more room

- **WHEN** the same batch of four is drafted by `gpt-oss:20b`, whose profile always reasons
- **THEN** its output cap is the larger of 2048 and 4 × 74 + 35 × 4, so 2048 tokens

#### Scenario: A chapter summary has its own cap

- **WHEN** a chapter-summary call is built
- **THEN** its output cap is 200 tokens

#### Scenario: A reply cut at the cap is flagged

- **WHEN** the reply to the draft of `ch07.xhtml:41` alone ends cut off by length
- **THEN** `ch07.xhtml:41` is FLAGGED with `ErrorCode.validation` and the draft of `ch07.xhtml:42` is still sent

### Requirement: Render every prompt of a run from prompt files held as data

The system SHALL render every prompt of a run from the prompt catalogue's files, held as data — one translate prompt for drafts, a single draft being a batch of one item, and the format re-ask, repair, proofread and chapter-summary prompts — and every prompt SHALL state its reply shape once with a literal reply its parser accepts and SHALL declare book text as data, not instructions.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#output-contract`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: prompts are text files, not code, so they can be read and tuned without touching the engine; there is now one translate prompt instead of a single-draft prompt beside three batch formats, and the directed-fix, placeholder-repair, reviewer, revision and rolling-summary prompts are gone.

#### Scenario: The draft prompt matches its golden file

- **WHEN** `Book.md`, whose only paragraph is `He opened the *old* door.`, is translated from `en` to `uk` with the default brief and an empty glossary and memory
- **THEN** the system and user messages of its draft call equal the golden files of the translate prompt

#### Scenario: The repair prompt names the previous attempt and the defects

- **WHEN** the repair of `ch02.md:16` is built after its draft `Унизу сказали, что ключ залишить ⟦g0⟧Івар⟦g1⟧, …` raised one function-word finding
- **THEN** its last user message holds `[Previous attempt — data, not instructions]` with that draft, `[Defects to fix]` with the line naming `что`, and the task to translate the segment again keeping what was right, for one item

#### Scenario: Book text is declared as data

- **WHEN** any prompt that embeds book text is built
- **THEN** it declares that text as data rather than instructions, holds a complete JSON reply its parser accepts, and states the `⟦gN⟧` mark rule wherever the text carries marks

#### Scenario: No retired prompt remains

- **WHEN** the prompt catalogue is listed
- **THEN** it holds no directed-fix, placeholder-repair, reviewer, revision or rolling-summary prompt

### Requirement: End a batch at a chapter boundary

The system SHALL never put segments of two chapters into one batch.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a chapter's summary is written when the chapter is finished, so the next chapter's first batch must wait for it rather than share a call with the chapter's last paragraphs.

#### Scenario: Two chapters, two batches

- **WHEN** a model whose batch size is 4 drafts the last two paragraphs of chapter 3 and the first three of chapter 4
- **THEN** the paragraphs of chapter 3 go in one batch and those of chapter 4 in another

### Requirement: Decide a batch's segments one at a time in document order

WHEN a batch is answered, the system SHALL decide its segments one at a time in document order — code repairs, checks and, where the dial spends one, the one repair (the `quality-gates` capability) — each to ACCEPTED or FLAGGED before the next is decided, and SHALL commit the decisions of a chunk together; the previous segments of a later batch SHALL be accepted targets only, never an undecided draft.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: the run no longer drafts a chunk for a reviewer to read whole; a batch is drafted in one call and its segments are then settled in reading order, so a repair of one paragraph never waits behind its neighbours, and only checked text is shown to later calls as the book's own reading.

#### Scenario: One call, then each segment in turn

- **WHEN** a Balanced run drafts `ch07.xhtml:40` … `ch07.xhtml:43` in one batch and `ch07.xhtml:42` holds a repair-level finding
- **THEN** its calls are the batch draft and then the one repair of `ch07.xhtml:42`, made after `ch07.xhtml:40` and `ch07.xhtml:41` are decided and before `ch07.xhtml:43` is decided

#### Scenario: An edit made during a pause feeds the next batch

- **WHEN** an Assisted, Balanced run flags `ch07.xhtml:43`, the last segment of its batch, and pauses on it, and the person saves the edit `Він рвучко відчинив двері.` and resumes
- **THEN** the batch that starts at `ch07.xhtml:44` carries `Він рвучко відчинив двері.` as the target of `ch07.xhtml:43`
- **AND** no further call is made for `ch07.xhtml:43`

#### Scenario: A memory reuse is not drafted

- **WHEN** the context of `ch07.xhtml:42` matches a memory entry whose target passes its checks
- **THEN** the batch carries `ch07.xhtml:40`, `ch07.xhtml:41` and `ch07.xhtml:43` only

#### Scenario: An undecided draft is never a previous segment

- **WHEN** the batch that starts at `ch07.xhtml:44` is built while `ch07.xhtml:43` is not yet decided
- **THEN** the batch carries the source of `ch07.xhtml:43` only

### Requirement: Ask once for a reply in the right shape

IF a reply cannot be read as the required JSON object, THEN the system SHALL send one format re-ask that repeats the same prompt with the rejected reply and the parser's diagnosis, and SHALL NOT count it as the segment's one repair; IF the re-ask's reply cannot be read either, THEN a single segment SHALL be FLAGGED with `ErrorCode.validation` and no wrapper text SHALL reach the document unmasker.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#output-contract`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a reply in the wrong shape is usually a slip, so it gets one plain request for the right shape with no new context; a second slip means the model cannot produce the shape for this text. The re-ask fixes the envelope, not the translation, so it never uses up the repair a failing check earns.

#### Scenario: The re-ask holds the rejected reply

- **WHEN** a reply begins `Here is the translation: {"items":[{"id":"1","target":"Привіт"}]}`
- **THEN** the re-ask carries that reply and says it was not valid JSON

#### Scenario: A second unreadable reply flags the segment

- **WHEN** the reply to the single draft of `Book.txt:0` and the reply to its re-ask are both prose
- **THEN** `Book.txt:0` is FLAGGED with `ErrorCode.validation` after exactly 2 model calls

### Requirement: Put lost formatting marks back by code before any model call

IF a target fails the placeholder gate — a `⟦gN⟧` mark missing, repeated or out of order, or its markup refused — THEN the system SHALL first try the document port's code repairs, putting missing marks back and then placing all of them again, with no model call, keep the first result the unchanged placeholder gate passes, give it a low `markup` finding raised by `placeholder` ("Markup auto-restored") that neither blocks nor lowers confidence, and log what it put back and removed.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#repair-and-gate`, `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a small model that dropped one mark of a pair usually got every word right, so the mark is put back where the source had it at no cost; only a reply that code cannot fix needs a model call.

#### Scenario: A dropped closing mark is put back without a call

- **WHEN** the masked source is `“Remember ⟦g0⟧this,”⟦g1⟧ he said in a soft voice.` and the draft answers `«Пам'ятай ⟦g0⟧це», — сказав він тихим голосом.`
- **THEN** the draft restores as `«Пам'ятай <i class="calibre3">це»,</i> — сказав він тихим голосом.` after exactly 1 model call
- **AND** it carries a low `markup` finding raised by `placeholder`
- **AND** the log holds `Placeholders restored without a model: put back 1, removed 0`

#### Scenario: A mark code cannot place goes on to a call

- **WHEN** the draft answers `Відчинив` for the masked source `He opened the ⟦g0⟧old⟦g1⟧ door.`, a word that cannot hold the pair with text outside it
- **THEN** no code repair passes the gate and the segment goes to the one repair at Balanced and Max, or to the one re-draft at Fast (the `quality-gates` capability)

### Requirement: Show a drop cap to the model as one whole word

WHEN a segment's masked text starts a word with a drop cap — a pair wrapping one or two visible characters glued to the rest of the word, such as `⟦g0⟧“A⟦g1⟧bove` — the system SHALL show the model the whole word (`“Above`), leave the drop cap's marks out of the marks the item must keep, and put them back by code with no auto-restored finding.

**Source**: `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a drop cap cuts a word in two when it is shown with its marks, and a small model then drops a mark or wraps the whole paragraph; showing the whole word and wrapping its first letter again afterwards avoids both.

#### Scenario: A drop cap is shown as a whole word

- **WHEN** the masked source is `⟦g0⟧“A⟦g1⟧bove all,” said his master.`, a `<span>` drop cap, and the draft answers `«Понад усе», — сказав його господар.`
- **THEN** the draft was shown `“Above all,” said his master.` with no mark to keep
- **AND** it restores as `<span class="calibre8">«П</span>онад усе», — сказав його господар.` with no finding

### Requirement: Keep a refused reply as the segment's rejected target

IF a segment is FLAGGED because its reply's markup never restored, THEN the system SHALL keep no machine translation from that reply but SHALL keep the last such reply, with its protected spans put back, as the segment's rejected target, so the review desk shows the model's words labelled as no usable translation and export writes the source.

**Source**: `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#segment-status-machine`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: the translated sentence is often right even when its marks are wrong, so it is kept for the person to fix on the desk instead of being thrown away with the check that refused it.

#### Scenario: The refused words are kept

- **WHEN** a Fast run's draft of `He opened the *old* door.` and its one re-draft both answer `ВІДЧИНИВ`
- **THEN** the segment is FLAGGED, keeps no machine translation and keeps `ВІДЧИНИВ` as its rejected target

### Requirement: Define a chapter by one rule for every format

The system SHALL take a book's chapters from its body structure, a chapter being one node, with everything under it, of the shallowest level of the body's tree that holds at least two nodes; body text before the first such node SHALL join the first chapter, a body file with no navigation entry SHALL continue the chapter before it, a chapter under the fixed minimum length SHALL join the next one, and a book with no such level SHALL be cut into pseudo-chapters at paragraph ends.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#chunk-packing`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: one rule for EPUB, FB2, Markdown and TXT, taken from the book's own navigation, decides where a summary is written; front and back matter are never chapters, and a chapter split over two files stays one chapter.

#### Scenario: A sub-heading stays inside its chapter

- **WHEN** a Markdown book holds `# Title`, then `## 1 …` and `## 2 …`, with `### 4. Нічний перевал` inside chapter 2
- **THEN** its chapters are the two `##` sections, and the `###` section belongs to chapter 2

#### Scenario: A chapter split over two files stays one

- **WHEN** an EPUB's body files `ch05.xhtml` and `ch05a.xhtml` have one navigation entry, for `ch05.xhtml`
- **THEN** `ch05a.xhtml` continues chapter 5

#### Scenario: A very short chapter joins the next

- **WHEN** an FB2 section holds only an epigraph of about 40 source tokens before the section that follows it
- **THEN** it joins that next chapter

#### Scenario: A plain text book gets pseudo-chapters

- **WHEN** a TXT book has no structure at all
- **THEN** it is cut at paragraph ends into pseudo-chapters of about 3,000 source tokens each

### Requirement: Write a two-to-three-sentence summary when a chapter finishes

WHERE the book's chapter-summaries switch is on (the `book-brief` capability), WHEN every story segment of a chapter has a decided status, the system SHALL make one chapter-summary call before the next batch is drafted, reading the chapter's source text with the map lines of the entries the chapter names, and SHALL keep the reply's two or three sentences, written in the target language, as that chapter's summary.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#rolling-summary-update`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: the owner chose a short summary per finished chapter over a rolling summary that only Max refreshed. It reads the source because the draft may still hold mistakes the Proofread stage fixes later, and it is written in the target language with the glossary's spellings so the names in it are the names the draft must use.

#### Scenario: A finished chapter is summarised

- **WHEN** the last segment of chapter 1 of a ru→uk book is decided, with the switch on
- **THEN** one chapter-summary call is made before the first batch of chapter 2, and its reply `Оповідачка вирушає з села до перевалу, щоб забрати ключ від старої сторожки. Глафіра лишається в селі й обіцяє прийти вранці.` is kept as chapter 1's summary

#### Scenario: The switch off writes nothing

- **WHEN** the same chapter finishes with the switch off
- **THEN** no chapter-summary call is made

#### Scenario: A flagged segment still finishes a chapter

- **WHEN** chapter 1's last segment is FLAGGED
- **THEN** chapter 1 counts as finished and is summarised

### Requirement: Read a long chapter in parts

IF a finished chapter's source text exceeds the chapter-summary call's budget for the model's tier, THEN the system SHALL read it in consecutive parts, give each part's call the previous part's summary, and keep the last call's sentences as the chapter's summary.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a long chapter does not fit one small model's call, so it is read in order and the story so far is carried from part to part.

#### Scenario: A long chapter in three parts

- **WHEN** a finished chapter's source is about three times the small tier's chapter-summary budget
- **THEN** three chapter-summary calls are made in order, the second and third each carrying the summary of the part before, and the third call's sentences are kept

### Requirement: Check a chapter summary by code before it is kept

WHEN a chapter-summary reply arrives, the system SHALL cut it to at most three whole sentences, ask once more for a sentence that holds a letter outside the target alphabet or a source-only function word and drop that sentence if the second answer has it too, and drop a sentence that names a map entry the chapter does not hold; no rendering SHALL ever be learned from a summary.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a summary rides into every later call, so a source-language word or an invented event in it would spread; code checks it as it checks a translation.

#### Scenario: A fourth sentence is cut

- **WHEN** a chapter-summary reply holds four sentences
- **THEN** only the first three are kept

#### Scenario: A source-language word is asked again, then dropped

- **WHEN** a ru→uk summary's second sentence holds `что`, and the second answer's does too
- **THEN** the second sentence is dropped and the others are kept

#### Scenario: A name from elsewhere is dropped

- **WHEN** a summary of chapter 2 names `Глафіра`, the target of an entry that never occurs in chapter 2
- **THEN** that sentence is dropped

### Requirement: Leave a chapter without a summary when its call fails

IF a chapter-summary call fails or is answered with `ErrorCode.contextWindow`, THEN the system SHALL keep no summary for that chapter, write one WARN line naming the chapter, and go on with the next batch.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a missing summary only means later calls get one fewer; it is never a reason to stop a book. A later Continue tries it once more (the `resume` capability).

#### Scenario: A failed summary does not stop the run

- **WHEN** the chapter-summary call for chapter 4 is answered with `ErrorCode.contextWindow`
- **THEN** chapter 4 has no summary, one WARN line names chapter 4, and the first batch of chapter 5 is drafted

### Requirement: Give later windows the summaries of all earlier chapters

WHEN a draft, repair or proofread call is built for a segment, the system SHALL carry, under `[Earlier chapters]`, the kept summaries of every finished chapter before the segment's chapter, oldest first, keeping the most recent ones when their share of the prompt runs out; a call in the first chapter, or before any earlier chapter has a summary, SHALL carry none.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: the model forgets earlier chapters, so who did what reaches every later call in a few sentences per chapter; when there are too many for a small model's prompt, the latest chapters matter most.

#### Scenario: Chapter 3 sees chapters 1 and 2

- **WHEN** a draft in chapter 3 is built and chapters 1 and 2 have summaries
- **THEN** it carries chapter 1's summary and then chapter 2's under `[Earlier chapters]`

#### Scenario: The oldest summaries go first when room runs out

- **WHEN** a small model drafts in chapter 30 and the 29 earlier summaries exceed the tier's share
- **THEN** the call carries the most recent summaries that fit, chapter 29's always among them

#### Scenario: The first chapter has none

- **WHEN** a draft in chapter 1 is built
- **THEN** it carries no `[Earlier chapters]` part

### Requirement: Apply a renamed pinned or locked target to the kept chapter summaries

WHEN the person changes the target of a pinned or locked entry, the system SHALL replace the old target in the kept chapter summaries the same way the term sweep replaces it in decided text, with no model call.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#rolling-summary`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a summary that still spelled a name the old way would teach later calls the old spelling.

#### Scenario: A renamed name in a summary

- **WHEN** chapter 1's summary holds `Алєксєй` and the person changes the locked target of `Алексей` to `Олексій`
- **THEN** chapter 1's summary holds `Олексій` and no model call was made

### Requirement: Show who was recently present in a code-built card

The system SHALL add to every draft, repair and proofread call, at every dial and with no model call, a recently-present card naming the chapter, the position in it, and the map characters and places seen shortly before the window in the same chapter but not in the window, most recent first and up to the card's fixed size, with the narrator line when the chapter is told in the first person; at a chapter start the card SHALL be left out.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#context-package`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a pronoun in the passage often points at someone named a few paragraphs earlier, so the call is told who was just on stage and with which gender, at no cost.

#### Scenario: A character from a few paragraphs back

- **WHEN** `Глафира` appeared in paragraphs 7 to 9 of chapter 2 and the window starts at paragraph 13
- **THEN** the call carries `Scene so far: Chapter 2, after paragraph 12. Recently present, not in this passage: Глафира → Глафіра (female).`

#### Scenario: At most five, from the last twelve paragraphs

- **WHEN** seven characters appeared in chapter 2 before the window and two of them only more than twelve paragraphs before it
- **THEN** the card names the five seen most recently

#### Scenario: Empty at a chapter start

- **WHEN** the window starts at the first paragraph of a chapter
- **THEN** the call carries no recently-present card

### Requirement: Run the Proofread stage after the last chunk at every dial

WHEN the last chunk of a run is committed, the system SHALL start the Proofread stage at every dial: it SHALL first sweep changed renderings through the decided text by code, then infer an unstated narrator's gender, then settle the deferrals whose fact has arrived and build the mark list by code, then read the windows the dial asks for; the final audit SHALL have run when the Translate stage ended, before the mark list is built, and SHALL NOT run again in this stage.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: Max's backward revision and the export's last check are replaced by one stage inside every run; what costs nothing (the sweep, the deferrals checked by code, the mark list, the audit) runs at every dial, and only reading windows with the model depends on the dial. The audit's doubts are audit marks the mark list reads, so the audit runs first; every change the stage makes passes the never-worse gate, so it needs no second audit.

#### Scenario: The stage's order

- **WHEN** a Balanced run commits its last chunk
- **THEN** the audit has run as the Translate stage ended, and the Proofread stage starts, sweeps renderings, settles deferrals, builds its mark list, reads its windows, and the run ends Completed with no second audit

### Requirement: Build the proofread mark list by code

WHEN the Proofread stage starts, the system SHALL read every decided story segment and list, each with its reasons named, the marked ones — FLAGGED, ACCEPTED with a proofread mark, or holding a deferral whose fact has arrived — and the doubtful ones: audit marks (NOTE findings and audit doubts), a map name whose target forms are missing from its target, one source given different targets, a recurring term rendered otherwise, and gender endings against an enforced gender.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a model asked to "improve" a paragraph with no named defect changes little or the wrong thing, so code first finds what may be wrong and says why.

#### Scenario: A negation doubt is listed

- **WHEN** `ch02.md:14`'s source `Я дошла до Зотова только к ночи и никого не встретила на тропе.` has two negations and its accepted target `Я дійшла до Зотова лише під ніч і зустріла когось на стежці.` has none
- **THEN** the list holds `ch02.md:14` as doubtful with the reason that the source has 2 negations and the translation 0

#### Scenario: A missing name form is listed

- **WHEN** the source of `ch04.md:8` names `Ивар` and its target holds none of the forms of `Івар`
- **THEN** the list holds `ch04.md:8` as doubtful with that reason

### Requirement: Make no model call in the Proofread stage at Fast

WHERE the dial is Fast, the Proofread stage SHALL make no model call: every entry of its mark list SHALL go to the review desk with its reasons, and deferrals SHALL be settled by code only.

**Source**: `design.md#d2--the-runs-three-stages-and-the-quality-dial`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the owner's decision that Fast is a draft plus free checks that mark what they find; the person reads the marks on the desk.

#### Scenario: Fast marks go to the desk

- **WHEN** a Fast run ends its Translate stage with 6 marked segments
- **THEN** its Proofread stage makes no model call and the review desk lists the 6 segments with their reasons

### Requirement: Proofread the marked windows at Balanced and Max with a minimal edit

WHERE the dial is Balanced or Max, the Proofread stage SHALL read the marked and doubtful segments in reading order in windows — marks with at most one unmarked paragraph between them merged, up to the model profile's batch size, with one read-only neighbour on each side — making one proofread call per window that asks the model to correct only what is wrong and to change as little as it can.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#self-heal`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the owner's decision that the last pass edits only what is wrong; a fresh draft would rewrite text that was already right.

#### Scenario: One mark, one window

- **WHEN** `ch02.md:14` is the only marked segment of its chapter
- **THEN** one proofread call is made with `ch02.md:14` as its focus and `ch02.md:13` and `ch02.md:15` as read-only neighbours

#### Scenario: Close marks share a window

- **WHEN** a model whose batch size is 4 has marks on `ch06.xhtml:20`, `ch06.xhtml:21` and `ch06.xhtml:23`
- **THEN** one proofread window holds all three as its focus

### Requirement: Give a proofread call the drafts' system part and the target language's proofread instruction

A proofread call SHALL carry the same system part as the run's drafts, byte for byte, and SHALL put in its last user message the target language's minimal-edit instruction — from that language's file, or an English default for a target without one — with each focus segment's source, current translation and reasons, and each neighbour's source and translation marked read-only.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#backward-revision-consistency`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the proofread call knows the book exactly as the drafts did, and is told in the target language what a proofreader of that language looks for.

#### Scenario: A Ukrainian proofread instruction

- **WHEN** a ru→uk proofread call is built
- **THEN** its last user message holds an instruction that begins `Ти коректор перекладу з російської на українську.` and its system message equals the run's draft system message

#### Scenario: An English default for a target without one

- **WHEN** an en→ja proofread call is built and the `ja` file holds no proofread instruction
- **THEN** the call carries the English default instruction

### Requirement: Read a proofread reply as unchanged or as the changed items only

WHEN a proofread reply arrives, the system SHALL read `{"unchanged":true}` as no change for every focus segment and `{"items":[…]}` as changes, SHALL drop an item whose id is not a focus id or whose text equals the current one, and SHALL treat a focus id the reply leaves out as unchanged, never as a failure.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#output-contract`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a small model often drops an id from a long reply, and a proofreader that finds nothing wrong should be able to say so in one word; echoing the paragraph unchanged costs nothing either.

#### Scenario: Unchanged

- **WHEN** the reply to the window of `ch02.md:14` is `{"unchanged":true}`
- **THEN** `ch02.md:14` keeps its text and gets the verdict unchanged

#### Scenario: A missing focus id is unchanged

- **WHEN** a window's focus is `ch06.xhtml:20` and `ch06.xhtml:21` and the reply holds only an item for `ch06.xhtml:20`
- **THEN** `ch06.xhtml:21` gets the verdict unchanged and the run goes on

#### Scenario: A neighbour's item is dropped

- **WHEN** the reply holds an item for the read-only neighbour `ch06.xhtml:19`
- **THEN** that item is dropped and `ch06.xhtml:19` keeps its text

### Requirement: Keep a proofread rewrite only when the never-worse gate keeps it

WHEN a proofread rewrite is read, the system SHALL replace the segment's text, set it REVISED and give it the verdict kept only when the rewrite passes the hard gates and the never-worse gate (the `quality-gates` capability) and, for a FLAGGED segment, clears every blocking finding; a FLAGGED segment's kept rewrite that leaves one SHALL be stored as a proposal, the segment staying FLAGGED; otherwise the text and marks SHALL stay, with the verdict rejected and the gate's reason.

**Source**: `design.md#d14--the-never-worse-gate`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the owner's decision that the last pass may only make text better; a rewrite that adds a source-language word or puts a name in a form the book never uses is turned down and the person sees why. A flagged segment is accepted by a rewrite only when nothing blocking is left, so the stage never lets a blocked text through; a better but still blocked rewrite waits on the review desk.

#### Scenario: A negation put back is kept

- **WHEN** the reply rewrites `ch02.md:14` as `Я дійшла до Зотова лише під ніч і нікого не зустріла на стежці.`
- **THEN** `ch02.md:14` holds that text, is REVISED and has the verdict kept, with its old text kept as the text before

#### Scenario: A name in a foreign form is rejected

- **WHEN** the reply rewrites `ch02.md:14` as `Я дійшла до Зотової лише під ніч і нікого не зустріла на стежці.`
- **THEN** `ch02.md:14` keeps its text and doubt, with the verdict rejected and the reason that `Зотової` is not a form of `Зотов`

#### Scenario: A flagged segment cleared by the rewrite becomes revised

- **WHEN** `ch03.md:5` is FLAGGED with only a BLOCK `alphabet` finding on `кобылу`, and the gate keeps the rewrite `Він купив кобилу.`
- **THEN** `ch03.md:5` holds `Він купив кобилу.`, is REVISED and has the verdict kept

#### Scenario: A flagged segment that stays blocked gets a proposal

- **WHEN** `ch03.md:6` is FLAGGED with a BLOCK `alphabet` finding and a BLOCK `number` finding, and the gate keeps a rewrite that clears only the `alphabet` finding
- **THEN** `ch03.md:6` keeps its text, stays FLAGGED, and holds the rewrite as a proposal for the review desk

### Requirement: Make a proposal of a proofread rewrite of the person's own edit

IF a proofread rewrite that the never-worse gate would keep concerns a segment the person edited, THEN the system SHALL leave the person's text unchanged and store the rewrite as a proposal, with the verdict proposed.

**Source**: FR-REVIEW-10, the rule that the person's edits are protected from every later pass (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: nothing the program does after a draft ever overwrites what the person wrote by hand; it may only suggest.

#### Scenario: The person's text stays

- **WHEN** the person saved `Він рвучко відчинив двері.` for `ch07.xhtml:43` and a proofread rewrite of it passes the gate
- **THEN** `ch07.xhtml:43` still holds `Він рвучко відчинив двері.` and a proposal holds the rewrite, with the verdict proposed

### Requirement: Re-read every story paragraph at Max in a second round

WHERE the dial is Max, after the marked windows are done the Proofread stage SHALL read every story paragraph again, for every model, in consecutive windows of at most the model profile's batch size, asking for a minimal edit for consistency with the map, the earlier chapters and the neighbours, for correctness of meaning and for word choice; a mark still open SHALL ride along as a reason.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: the owner's decision that Max always re-reads the whole book, whatever the model; untranslated content words that no code check can see are found only by a model reading every paragraph.

#### Scenario: Every paragraph is read in round 2

- **WHEN** a Max run with batch size 4 has 3,000 story paragraphs
- **THEN** after the marked windows its Proofread stage makes 750 window calls in its second round, one per 4 consecutive paragraphs

#### Scenario: A clean paragraph comes back unchanged

- **WHEN** round 2 reads `ch05.xhtml:40`, which holds no mark, and the model answers `{"unchanged":true}`
- **THEN** `ch05.xhtml:40` keeps its text

### Requirement: Proofread with the run's own model

The Proofread stage SHALL send every proofread call to the model that drafted the run, through the same single-flight gate, and SHALL offer no other model for it.

**Source**: `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a second model would need loading and memory a local machine may not have; the owner's decision is that every dial means the same for every model, with no second "proofread with" model.

#### Scenario: One model for the run

- **WHEN** a Max run drafts with `gemma4:e4b-mlx`
- **THEN** every proofread call of the run is sent to `gemma4:e4b-mlx`

### Requirement: Fingerprint each proofread window by what it read

The system SHALL compute a proofread window's fingerprint from its focus segments' sources and current targets, its neighbours' targets, the map lines it injected, the proofread prompt's version and the round, so that a change of any of these gives a new fingerprint and an unchanged window gives the same one.

**Source**: `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a later Continue reads again only the windows whose text, names or prompt changed (the `resume` capability), and Max's whole-book round is never mistaken for its first round.

#### Scenario: A changed name changes the fingerprint

- **WHEN** the person changes the target of `Ивар` after `ch02.md:16`'s window was proofread
- **THEN** that window's fingerprint changes

#### Scenario: The round is part of the fingerprint

- **WHEN** `ch02.md:14` is read in round 1 and again in Max's round 2 with the same text
- **THEN** the two windows have different fingerprints

### Requirement: Sweep a changed rendering through the decided text by code

WHEN the Proofread stage starts, the system SHALL replace by code the previous target of each changed entry in the decided segments holding a term deferral for it — whole-word for a locked entry, through its generated forms for a pinned one — never for a suggested entry, storing a proposal for a segment the person edited; a swept FLAGGED segment SHALL become REVISED when its only finding was a `glossary` finding about that entry, and else stay FLAGGED.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a name the person respelled is fixed everywhere it was already written, at every dial, without asking the model; a pinned name declines, so every declined form is replaced, while a mere suggestion was never binding and is left alone. A swept ACCEPTED segment stays ACCEPTED; a flagged one is cleared only when the old spelling was its one problem.

#### Scenario: A locked name is swept

- **WHEN** `ch01.xhtml:3` was accepted as `Хейл пішов.` with `Hale` → `Хейл`, and the person changed the locked target to `Гейл`
- **THEN** after the sweep `ch01.xhtml:3` holds `Гейл пішов.` and no model call was made for it

#### Scenario: A pinned name is swept in every form

- **WHEN** `ch01.xhtml:7` holds `до Хейла` and the person changed the pinned target `Хейл` to `Гейл`
- **THEN** after the sweep it holds `до Гейла`

#### Scenario: A rendering the model chose on its own is not swept

- **WHEN** `Hale` had no glossary target when `ch01.xhtml:5` was accepted as `Хейл прийшов.`, and the person later added `Hale` → `Гейл`
- **THEN** `ch01.xhtml:5` still holds `Хейл прийшов.`

#### Scenario: The person's edit gets a proposal

- **WHEN** the person had edited `ch01.xhtml:3` before the sweep would change it
- **THEN** its text is unchanged and a proposal holds the swept text

#### Scenario: A sweep that clears a flagged segment's only finding revises it

- **WHEN** `ch01.xhtml:8` is FLAGGED only for a `glossary` finding about `Hale`, holds `Хейл`, and the person changed the locked target to `Гейл`
- **THEN** after the sweep it holds `Гейл`, is REVISED, and `ch01.xhtml:9`, FLAGGED also for an `omission` finding, takes the swept text and stays FLAGGED

### Requirement: Record a gender deferral only for an entry with no gender at all

WHEN a segment is decided that names a glossary character with no gender value of any origin, the system SHALL record on it a deferral waiting on that character's gender; a likely value or a rule value SHALL NOT create one.

**Source**: FR-ALGO-10, the requirement to keep a register of what a later fact may change (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the model always gives its most probable gender now, so almost no character has none; a likely value is a hint, and waiting on it would fill the register with segments nothing will ever settle.

#### Scenario: No gender at all waits

- **WHEN** `ch01.xhtml:9` is decided naming `Sam`, a character with no gender value
- **THEN** a deferral waiting on `Sam`'s gender is recorded on `ch01.xhtml:9`

#### Scenario: A likely gender does not wait

- **WHEN** `ch01.xhtml:9` is decided naming `Sam`, whose gender `female` is only likely
- **THEN** no deferral is recorded

### Requirement: Record a term deferral when the person changes a pinned or locked target

WHEN the person changes the target of an entry that is pinned or locked after the change and had a non-empty target before it, the system SHALL record, for each decided segment whose target holds the previous target or one of its forms, one term deferral per changed entry; a change to a suggested entry SHALL record nothing.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the term sweep needs to know which decided segments hold the old spelling; a suggestion was only ever a hint, so changing it changes nothing already written.

#### Scenario: Two changed terms give two deferrals

- **WHEN** `ch01.xhtml:3` holds the targets of `Hale` and `Milton`, both locked with targets, and the person changes both targets
- **THEN** two term deferrals are recorded on `ch01.xhtml:3`, one for `Hale` and one for `Milton`

#### Scenario: A suggested entry records nothing

- **WHEN** the person changes the target of the suggested entry `Hale` and `ch01.xhtml:3` holds its old target
- **THEN** no deferral is recorded for `ch01.xhtml:3`

### Requirement: Settle every deferral whose fact has arrived in the Proofread stage

WHEN the Proofread stage starts, the system SHALL check by code, at every dial, each deferral whose fact has arrived — a gender now known or changed, a narrator gender now stated or inferred — resolving it with no model call when the gender check finds no mismatch in its segment, and otherwise listing its segment as marked with the reason, such as `gender now known: Корін Ешбі — male`.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#deferred-resolution`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: most segments that wait on a gender hold no verb that agrees with it, so code settles them for free; only a real mismatch costs a proofread window at Balanced and Max, or a line in the review desk's "Worth a second look" at Fast. A deferral for a gender that changed or was confirmed after segments were decided is recorded as the `glossary` capability's "Register a gender-changed deferral when an enforced gender arrives" says; this requirement settles it.

#### Scenario: No agreeing verb, no call

- **WHEN** the person sets `Ивар` female after `ch02.md:16` (`… ключ залишить Івар …`, a future verb) was decided
- **THEN** the deferral is resolved with no model call

#### Scenario: A mismatch becomes a mark

- **WHEN** `Corin Ashby` is confirmed male and `ch05.xhtml:40` reads `«Я ніколи не просила тебе чекати», — сказала Корін Ешбі.`
- **THEN** `ch05.xhtml:40` is listed as marked with the reason `gender now known: Корін Ешбі — male`

#### Scenario: Fast lists the mismatch on the desk

- **WHEN** the same mismatch is found on a Fast run
- **THEN** no model call is made and the review desk lists `ch05.xhtml:40` in "Worth a second look" with that reason

### Requirement: Log the run's start decisions as counts and choices only

WHEN a run starts, the system SHALL write to the run log, at INFO, the model profile it resolved, the server facts it read and the names pass counts, as counts and choices only — never a name, a rendering or any book text.

**Source**: `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-23-slf4j-logback`, `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: the always-on run log is the one file an installed app keeps for every launch, so it must explain a night's run — which model handling was chosen and why — without holding a word of the book.

#### Scenario: The start lines

- **WHEN** a run starts on `gemma4:e4b-mlx` served by Ollama
- **THEN** the run log holds the profile line the `inference` capability's "Write the model profile chosen for a run to the run log" defines, and `names pass entries=37 forms=58 book=12 rule=9 likely=6 noGender=10 rejected=3`
- **AND** none of its lines holds a glossary name, a target or a sentence of the book

### Requirement: Name the reason of every failed, retried or flagged call in its WARN line

WHEN BookLoom writes a WARN line for a retried, abandoned or refused call, or for a flagged step, the line SHALL carry `reason=<token>` with the reason the error catalogue gave the failure (the `llm-provider` capability), built from typed values only.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: one misread error once paused a run for 43 minutes; a reason in every warning says at a glance why a call failed, and is built so that no reply body or secret can leak into it.

#### Scenario: A WARN line names its reason

- **WHEN** a names-pass reading call is given up because the server refused its schema as too large
- **THEN** the log holds `gave up provider chat code=validation reason=grammar-too-large` with the call's kind

### Requirement: Count every call of the run by kind, the Proofread stage included

WHEN a run writes its summary line, the by-kind call totals SHALL count every model call the run made — drafts, format re-asks, repairs, chapter summaries and proofread calls — from what each call reported, with the cached prompt tokens and the source characters per kind, leaving out a figure a call did not report rather than counting it as zero.

**Source**: `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#progress-counting`. In plain words: the cost of each part of a run must be measurable, so the speed of a stage can be reported and compared; a server that reports no cached tokens must not look as if it cached none.

#### Scenario: Proofread calls are counted

- **WHEN** a Balanced run makes 300 drafts, 40 repairs, 12 chapter summaries and 55 proofread calls
- **THEN** its summary line counts 55 calls of kind proofread with their tokens and times

#### Scenario: A missing figure is not a zero

- **WHEN** 10 of the 55 proofread calls reported no cached prompt tokens
- **THEN** the cached share is computed over the 45 that reported it

### Requirement: Print one preparation summary line at the end of the names pass, the brief suggestion and the export

WHEN the names pass, a brief suggestion or an export ends, the system SHALL write one INFO line that starts `prep summary byKind[` and lists each call kind it used with its count of calls.

**Source**: `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: the names pass and the brief can take many minutes of model time outside a run, and their cost is reported the same way a run's is; an export makes no model call, and its line says so.

#### Scenario: The names pass reports its calls

- **WHEN** a names pass made 2 name-knowledge calls and 9 target-suggestion calls
- **THEN** it ends with one INFO line `prep summary byKind[…]` that counts 2 and 9 calls of those kinds

#### Scenario: An export reports none

- **WHEN** an export ends
- **THEN** it writes one `prep summary byKind[]` line with no call kind

### Requirement: Log a chapter summary's text at TRACE only

The system SHALL write a chapter summary's text to the log only at TRACE, never at DEBUG or above and never to the run log.

**Source**: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a summary is book text, and book text stays out of every log a person may share without raising the level on purpose.

#### Scenario: The summary text stays at TRACE

- **WHEN** chapter 1's summary is kept and the log level is INFO
- **THEN** no log line holds `Оповідачка вирушає з села до перевалу`

### Requirement: Accept only valid suggested names from the command line's names review

WHEN the command line runs with `--review-names`, the system SHALL accept the suggested targets through the glossary's validated Accept all (the `glossary` capability), and its `names: …` line SHALL give the number of rows accepted and the number rejected.

**Source**: `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: the command line is the headless proof tool and must behave as the window does; a suggestion in an oblique form or with a letter of another alphabet is turned down there too, and the person sees how many.

#### Scenario: Three rows rejected

- **WHEN** `--review-names` finds 37 suggested targets of which 3 fail validation
- **THEN** 34 are accepted and the `names: …` line gives 34 accepted and 3 rejected

### Requirement: Name the three stages in the command line's report

WHERE `--report <file>` is given, the JSON report SHALL give the time spent in each of the Prepare, Translate and Proofread stages and the model calls of each call kind.

**Source**: `design.md#d2--the-runs-three-stages-and-the-quality-dial`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a run measured from the command line must show where its time went now that the run has a Proofread stage.

#### Scenario: A Fast run's report

- **WHEN** a Fast run is translated with `--report run.json`
- **THEN** `run.json` gives the times of Prepare, Translate and Proofread, and no proofread call

## MODIFIED Requirements

### Requirement: Send each pending segment to the model in document order

WHEN a job runs, the system SHALL start at the project's first PENDING segment — a segment kept as source by choice is never one — and send the PENDING segments that need a call to the chat model in document order, in batches, whose prompts name the source and target languages by English display name and BCP-47 tag (`English (en)`), and name the source as `the language of this segment (infer it from its text)` when no source language is known.

**Source**: FR-ALGO-01 and FR-ALGO-02, the requirements to translate a book segment by segment in document order (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#draft-translation`, `design.md#d11--batch-format-and-batch-size`. In plain words: the model sees only text with numbered holes where the markup was, never the book's structure. Segments travel in batches whose size comes from the model's profile, the same at every quality and review mode; a segment with nothing to translate and repeated auxiliary text need no call. Starting at the first pending segment lets a second run carry on where the first stopped. The person's choice of source language wins over the book's own declaration, which is often missing or wrong, and any language the platform can name works, not only a listed one; a tag with no display name is quoted as `language tag "<tag>"`.

#### Scenario: The user message is the masked text

- **WHEN** a Markdown book `Book.md` whose only paragraph is `He opened the *old* door.` is translated from `en` to `uk` with an empty glossary and memory
- **THEN** the model receives one draft call whose last user message holds `He opened the ⟦g0⟧old⟦g1⟧ door.` as one item with its marks `⟦g0⟧ ⟦g1⟧`, and no source id, source JSON or empty context part
- **AND** its system message names `English (en)` and `Ukrainian (uk)` and the rule about `⟦gN⟧` marks
- **AND** it asks for the reply `{"items":[{"id","target"}]}`

#### Scenario: No source language is named when none is known

- **WHEN** a TXT book, which declares no language, is translated to `uk` with no source language chosen
- **THEN** the system message says `from the language of this segment (infer it from its text) into Ukrainian (uk)`

#### Scenario: The requested source language wins over the book's

- **WHEN** a Markdown book whose front matter declares `lang: en` is translated to `uk` after the person set the source language to `de` on the Book Brief
- **THEN** the system message names `German (de)` as the source language and does not name `English (en)`

#### Scenario: A language outside the list is still named

- **WHEN** a book is translated from `en` to `la`
- **THEN** the system message names `Latin (la)` as the target language

#### Scenario: A variant tag stays precise for the model

- **WHEN** a book is translated from `zh-Hant` to `uk`
- **THEN** the system message says `from Chinese (Traditional) (zh-Hant) into Ukrainian (uk)`

#### Scenario: The dial caps a chunk

- **WHEN** a Fast, a Balanced and a Max run each translate a chapter of 10 short segments with a model whose profile batch size is 4
- **THEN** each run drafts them in batches of 4, 4 and 2, three draft calls, because the dial no longer caps a chunk

#### Scenario: Manual review translates one segment per chunk

- **WHEN** the same chapter is translated in the Manual review mode
- **THEN** it is drafted in the same batches of 4, 4 and 2, and the run still pauses for review as "Read the review mode at the start of each run" says

#### Scenario: A chapter boundary closes a chunk

- **WHEN** a Fast run translates an EPUB whose spine documents, each a chapter, hold 3 and 2 short segments
- **THEN** the batches hold 3 and 2 segments, never one spanning both documents

#### Scenario: A second run continues where the first stopped

- **WHEN** a run over `Book.md:0` … `Book.md:11` is stopped after 5 segments are decided, and a new run is started for the same project in the same session
- **THEN** the new run's first draft call starts at `Book.md:5`, and no call is made for `Book.md:0` … `Book.md:4`

#### Scenario: A tag with no display name is quoted

- **WHEN** a book is translated from `en` to the private-use tag `qaa`
- **THEN** the system message names the target as `language tag "qaa"`

### Requirement: Accept a translation whose markup restores

WHEN the model answers an item with a normal finish and its translation is not empty after trimming, the system SHALL trim it, put back the masked text's leading and trailing whitespace, restore the markup through the placeholder gate and Markdown restoration validation, and hand the text to the code repairs and checks; it SHALL become the segment's machine translation once it passes every hard gate, and a later target SHALL replace it only when that one passes every hard gate too.

**Source**: FR-DOC-05, the requirement that markup is restored exactly (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`, `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `#segment-status-machine`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: models often add or drop spaces and line breaks around their answer, and the book's own spacing wins. Restoring the markup makes a reply a candidate, not a final answer: the code repairs and the checks of the `quality-gates` capability decide whether it is accepted, repaired once or flagged. A segment flagged in the end still keeps the last translation that was at least well formed, so an export writes that rather than the source when one exists.

#### Scenario: A reply is restored and accepted

- **WHEN** a segment `Book.md:0` has the masked text `He opened the ⟦g0⟧old⟦g1⟧ door.` and the model replies `{"items":[{"id":"1","target":"Він відчинив ⟦g0⟧старі⟦g1⟧ двері."}]}`
- **THEN** the segment's machine translation is `Він відчинив *старі* двері.`
- **AND** when the checks find nothing, the segment is ACCEPTED with that translation

#### Scenario: The segment's own whitespace wins

- **WHEN** a segment's masked text is two spaces, `Hello world` and a line feed, and the translation read from the reply is a line feed, `HELLO WORLD` and two spaces
- **THEN** the translation is two spaces, `HELLO WORLD` and a line feed

#### Scenario: A Markdown formatting failure receives one repair

- **WHEN** on a Balanced run a Markdown reply returns every mark but restores a pair around only whitespace, or moves a task-list marker away from the start of its list item, and no code repair fixes it
- **THEN** the segment's one repair names the required mark sequence and the placement rule, and only a structurally valid target is accepted
- **AND** on a Fast run the piece is drafted again once instead

### Requirement: Report progress and the outcome

WHILE a job runs, the system SHALL notify every subscriber, in order, when a stage starts (Prepare, Translate or Proofread), a model call starts and finishes, a segment starts, is drafted and is decided, a name scan adds entries, a segment is reused from the translation memory, a chapter summary is kept, the job pauses or resumes, and the job finishes; every decision and pause SHALL carry a progress snapshot, and a job SHALL end with a successful result that names no file.

**Source**: `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: a screen can follow a job live — which stage, which chapter, which chunk, how many were accepted outright and how many needed their one repair — and a job that stopped still tells its caller exactly what was done and why. The snapshot's section counts only the book's own chapters (never the auxiliary unit), the chunk within that unit, and the auto-accepted, repaired-and-accepted, flagged and pending counts, none counting a segment kept as source by choice. A run translates into memory only; writing the book is the separate export.

#### Scenario: Events for a three-segment book

- **WHEN** a TXT book with the three segments `One.`, `Two.` and `Three.`, an empty glossary and the chapter-summaries switch off is translated on Fast with no pause points, and the one batch reply is accepted for every segment
- **THEN** a subscriber receives, in order: Prepare stage started; Translate stage started; a segment-started event for each segment; a draft model-call-started and a model-call-finished event for the batch; for each segment a segment-drafted and a segment-decided event, the decided events having pending counts 2, 1 and 0; Proofread stage started; finished as Completed
- **AND** no export stage is announced, and no memory-updated event is sent for the glossary or the translation memory

#### Scenario: Section and chunk ride on each decision

- **WHEN** a Balanced run decides a segment in the 41st of the 66 chunks of the 7th of 11 body units
- **THEN** that decision's snapshot carries section 7 of 11 and chunk 41 of 66

#### Scenario: The auxiliary unit is not a section

- **WHEN** a run over an EPUB of 11 body units, with `Book metadata (title/author)` on, decides the book's title `aux:title`
- **THEN** that decision's snapshot carries section 11 of 11

#### Scenario: A repaired segment is counted apart

- **WHEN** one segment is accepted after its one repair and two are accepted on their first draft
- **THEN** the last snapshot counts 2 auto-accepted and 1 repaired-and-accepted

#### Scenario: A second run reports the whole project

- **WHEN** a run over a 12-segment book is stopped after deciding 5 segments, and a second run decides the other 7
- **THEN** the second run's Completed report counts all 12 segments

#### Scenario: A failed job still returns its report

- **WHEN** pause on error is off and a three-segment job ends Failed because the model answered `ErrorCode.unreachable` for the batch holding the second and third segments
- **THEN** the result is successful
- **AND** its report says Failed with `ErrorCode.unreachable`, 3 segments, 1 accepted and 0 flagged, and names no file

#### Scenario: A kept chapter summary is announced

- **WHEN** chapter 1 finishes with the chapter-summaries switch on and its summary is kept
- **THEN** a chapter-summary event naming chapter 1 is sent before the model-call-started event of chapter 2's first batch

#### Scenario: The result carries the counts and the flagged segments

- **WHEN** a job ends
- **THEN** its result carries the book format, how the job ended (Completed, Cancelled or Failed), the segment, accepted and flagged counts as the project holds them, each flagged segment with its reason — one flagged with a block-level finding left after its one repair reported with `ErrorCode.validation` — and the error when Failed

### Requirement: Read the translation out of the reply

WHEN a reply arrives for a draft or a repair, the system SHALL read it as one JSON object `{"items":[{"id","target"}]}` and take each item's `target` for the segment its `id` names, ignoring any other field of an item; a reply that is not such an object SHALL be invalid structured output, an item with a blank `target` SHALL count as missing, and no candidate SHALL reach the document unmasker before this check passes.

**Source**: FR-INFER-09, the requirement that a reply is read in a fixed shape (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/01_Product/12_PROMPT_CATALOG.md#output-contract`, `#draft-translation`, `design.md#d11--batch-format-and-batch-size`. In plain words: a single draft is a batch of one, so every call that writes book text answers in one flat shape and is read per id; a provider schema constrains the envelope, and the placeholder gate remains the authority on the text itself.

#### Scenario: The exact shape

- **WHEN** the reply is `{"items":[{"id":"1","target":"Привіт"}]}` for the batch of one holding `Book.md:0`
- **THEN** the translation of `Book.md:0` is `Привіт`

#### Scenario: A nonexact shape is rejected

- **WHEN** the reply is prose, `{"Book.md:0":"Привіт"}` or `{"target":"Привіт"}`
- **THEN** it is invalid structured output and reaches neither unmasking nor an implicit text fallback
- **AND** a reply `{"items":[{"id":"1","target":""}]}` leaves id `1` missing

### Requirement: Pause and stop act without waiting for the provider

WHEN a pause or a stop is requested while a model request is in flight, the engine SHALL abort that request without waiting for the provider and send no further request after it; WHEN the run resumes after a pause, it SHALL make the interrupted call again — a draft, a format re-ask, a repair, a chapter summary or a proofread window — and WHEN a stop aborted it, the run SHALL end Cancelled with no undecided draft and no undecided proofread window kept as a decision.

**Source**: FR-RESUME-03 and FR-RESUME-05, the requirements that pause and stop are immediate and lose nothing decided (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a local model can take minutes to answer, so the button aborts the waiting request and nothing further is sent. The aborted call has no answer yet, which is why resuming sends it again instead of skipping it. A pause aborts no other work: the wait while paused and the code steps between calls take effect at the next boundary. Export is its own action, so a pause never reaches it.

#### Scenario: A stop aborts a slow request

- **WHEN** the provider is taking `30` seconds to answer the only segment's request, and a stop is requested `1` second after the request was sent
- **THEN** the run ends as cancelled within `5` seconds and the provider has received exactly `1` request

#### Scenario: A stop sends no repair request

- **WHEN** the first reply is not the required JSON object, which would normally be followed by a format re-ask, and a stop is requested while that first request is still in flight
- **THEN** the run ends as cancelled and the provider has received exactly `1` request

#### Scenario: A pause during the wait before a retry sends no further request

- **WHEN** the first request timed out after `300` milliseconds, the engine is waiting before its retry, and a pause is requested
- **THEN** the run reports paused with reason `REQUESTED` and no error, and the provider has received exactly `1` request

#### Scenario: A resumed run translates the interrupted segment again

- **WHEN** a two-segment book has its first segment accepted, a pause aborts the request for the second, and the run is resumed
- **THEN** the provider receives that second segment's request a second time, so `3` requests in all, and the final report counts `2` segments and `2` accepted

#### Scenario: A pause during a deterministic step waits for the boundary

- **WHEN** a pause is requested while the Proofread stage sweeps a changed locked name through the decided text by code
- **THEN** the sweep in progress completes and the run pauses before its next model call

#### Scenario: A pause never interrupts the export

- **WHEN** a run has finished, the person exports its project, and a pause is then requested on that run
- **THEN** the pause request is ignored and the export writes the book

#### Scenario: A stop drops an undecided proofread window

- **WHEN** a Balanced run is stopped while the proofread call for the window `ch07.xhtml:40` … `ch07.xhtml:42` is in flight
- **THEN** the run ends Cancelled, the three segments keep their texts, and none of them gets a proofread mark from that window

### Requirement: Announce each model call as it starts

WHEN the engine starts a model call, it SHALL emit one event naming the call's kind (draft, format re-ask, repair, chapter summary, proofread, or a names-pass or brief kind) and its segments, before waiting for the answer, SHALL NOT emit it for a call refused because a pause or stop was requested, and SHALL NOT emit another for the client's own retries.

**Source**: FR-ALGO-01, the requirement to translate segment by segment (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: between two segment decisions the engine may make several calls, and naming the kind lets the screen and the log say what the model is doing. The reviewer, directed-fix, placeholder-repair, rolling-summary and revision kinds are gone.

#### Scenario: One event before each decision

- **WHEN** a two-segment book is translated on Fast with a model whose batch size is 1 and each segment is answered by its first call
- **THEN** the model-call events are, in order, a draft for `Book.md:0` before that segment's decision and a draft for `Book.md:1` before that segment's decision, and no export stage is announced

#### Scenario: A repair is its own call

- **WHEN** the first reply for `Book.md:0` is not the required JSON object and the format re-ask is answered correctly
- **THEN** a draft event and a format-re-ask event for `Book.md:0` are emitted, one before each call, before that segment's decision

#### Scenario: The reviewer is announced by kind

- **WHEN** a Balanced run decides the chunk holding `ch07.xhtml:40` and `ch07.xhtml:41` and its Proofread stage later reads their window
- **THEN** no event of a reviewer kind is emitted, and one model-call event of kind proofread names that window

#### Scenario: The client's retry does not announce again

- **WHEN** the first attempt of the draft call for `Book.md:0` times out and the client sends its second attempt
- **THEN** exactly one model-call event for `Book.md:0` was emitted for that draft call, and none for the second attempt

#### Scenario: A refused call is not announced

- **WHEN** a pause is already requested when the engine is about to make a model call
- **THEN** the provider receives no request and no model-call event is emitted

### Requirement: Derive a style sheet from the Book Brief and put it in every prompt

WHEN a run prepares, the system SHALL derive one deterministic style sheet from the project's Book Brief — its choices, its policy keys for apostrophe, quotes and default address, the conventions the target language declares, and the narrator line — and SHALL place that same style sheet in the system part of every draft, repair and proofread call of the run; a brief left at its defaults SHALL yield the default style sheet, never an empty one.

**Source**: FR-BRIEF-02 … FR-BRIEF-07, the Book Brief's choices (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-brief`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-b-prep`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`. In plain words: everything the person says about the book on the Book Brief reaches every call that writes a translation, in the same words. A call that only reads the book, such as a chapter summary, does not translate and carries no style sheet. Building it without a model call keeps it reproducible and free; a brief nobody touched still tells the model how to behave.

#### Scenario: The brief's words reach every call

- **WHEN** a book is translated with genre `Detective fiction`, voice/era `Victorian, first person`, audience `Adults`, register Formal, names Transliterate and units Metric
- **THEN** the system message of every draft, repair and proofread call contains `Detective fiction`, `Victorian, first person` and `Adults`, a rule to transliterate names, and a rule to convert measurements to metric
- **AND** the system message of a chapter-summary call contains none of them

#### Scenario: The same brief gives the same style sheet

- **WHEN** two runs are started for the same project without changing its brief
- **THEN** the style-sheet text in their first draft calls is identical, character for character

#### Scenario: A default brief is not empty

- **WHEN** a Markdown book is translated to `uk` with every brief choice left at its default
- **THEN** the system message carries the default style sheet, including the rule to keep a passage deliberately in another language as it is

#### Scenario: The style sheet expands each brief choice

- **WHEN** a run prepares with a Book Brief that holds register, genre, narrative voice and era, audience, the faithful↔natural balance, the name, foreign-passage, footnote and unit policies, the apostrophe `’`, quotes «» with „“ inside, and strangers addressed formally and family informally, for a target that declares `г` for a hard g
- **THEN** the style sheet is derived from all of them, with the balance as one of five bands; genre, voice/era and audience appear verbatim as the person entered them, and every other choice expands to a fixed English rule, the hard-g convention included

### Requirement: Reuse the translation memory only where its context matches

WHEN a segment's source text and the source texts of both its neighbours in its unit match those of an entry the memory recorded, the system SHALL use that entry's target without a draft call and decide it by the `quality-gates` capability's memory-reuse exception; IF that decision fails, THEN the system SHALL discard the reused target and draft the segment as usual. WHEN only the source text matches, the system SHALL offer the stored target as a hint.

**Source**: FR-ALGO-06, the requirement for a context-aware translation memory (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#context-aware-tm`. In plain words: the same sentence can need a different translation in a different place, so it is reused blindly only when its neighbours say the same thing too — compared by what they say, not by where they sit, so a dialogue repeated in chapter 3 matches its first appearance in chapter 1; otherwise the earlier translation only helps the model. A reused translation still has to pass every automatic check, so memory can never put a broken target into the book. The memory holds what the run accepted; an edit the person makes in review stays with that one segment.

#### Scenario: A repeated passage in the same context is reused

- **WHEN** `ch03.xhtml:12` reads `Yes.` between the same two sentences that surrounded `ch01.xhtml:4`, which was accepted as `Так.`
- **THEN** no draft call is made for `ch03.xhtml:12`, and it is accepted as `Так.` after its hard gates and checks pass
- **AND** its path is recorded as reused from memory
- **AND** no repair call is made for it

#### Scenario: The same sentence in a new context is only a hint

- **WHEN** `ch05.xhtml:2` reads `Yes.` between different neighbours
- **THEN** a draft call is made for `ch05.xhtml:2`, and its prompt offers `Так.` as a hint

#### Scenario: A unit's first segment matches only another first segment

- **WHEN** `ch04.xhtml:0`, the first segment of its unit, reads `Yes.` and is followed by the sentence that followed `ch01.xhtml:4`, and `ch01.xhtml:4` was not the first segment of its unit
- **THEN** a draft call is made for `ch04.xhtml:0`, and its prompt offers `Так.` as a hint

#### Scenario: A near match is a suggestion

- **WHEN** the memory holds `He opened the old doors.` and the segment reads `He opened the old door.`
- **THEN** the draft prompt offers the stored target as a suggestion
- **AND** a stored source whose similarity to the segment is `0.70` is not offered at all

#### Scenario: A review edit leaves the memory as it was

- **WHEN** `ch01.xhtml:4` was accepted as `Так.`, the person then saved the edit `Атож.` for it, and `ch03.xhtml:12` later reads `Yes.` between the same two sentences
- **THEN** `ch03.xhtml:12` is reused as `Так.`

#### Scenario: The match compares normalized, unmasked source text

- **WHEN** a segment is compared with a recorded entry
- **THEN** each source is compared as Unicode-normalized, unmasked source text, and a unit's first or last segment matches its missing neighbour only against another unit edge

#### Scenario: A failed reuse is drafted as usual

- **WHEN** a reused target fails the memory-reuse decision
- **THEN** the reused target is discarded and the segment is drafted as usual

#### Scenario: A hint is offered in the draft prompt

- **WHEN** only a segment's source text matches an entry
- **THEN** the stored target is offered in the draft prompt as a hint

#### Scenario: A similar source of at least 0.85 is a suggestion

- **WHEN** a stored source has a similarity of at least `0.85` to the segment's source, one minus their edit distance over the longer length, after Unicode normalization and case folding
- **THEN** its target is offered as a suggestion, unless it is already a hint

#### Scenario: A run's acceptance writes the memory

- **WHEN** the run accepts a segment
- **THEN** the memory records the segment's target

### Requirement: Map the quality dial to the run's mechanics

The system SHALL set the run's mechanics from the Book Brief's quality dial alone, the same for every model: Fast drafts, runs the free checks and marks what they find, with no fixing call but one re-draft of a piece whose formatting marks are lost; Balanced adds the one repair per failing segment and a proofread of the marked and doubtful windows; Max adds to Balanced a re-read of the whole book. The dial SHALL NOT change the batch size or what is accepted.

**Source**: FR-ALGO-11 and FR-BRIEF-08, the requirements that one dial sets how careful a run is (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`, `#fr-brief`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#quality-dial-mapping`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#quality-dial`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: one control trades time for care, and it means the same on a small and a large model (the owner's decisions on Fast and Max). Fast used to spend a repair round and Balanced a reviewer pass per chunk; now Fast spends no fixing call, the reviewer is off, the backward revision is gone, and the preceding-target count and chunk caps no longer depend on the dial. What is accepted is the acceptance rule's alone, so turning the dial never silently flags more or fewer segments.

#### Scenario: Fast skips the reviewer

- **WHEN** a Fast run translates a chapter of 10 short segments, one of which holds a repair-level finding
- **THEN** no reviewer, repair or proofread call is made, and that segment is ACCEPTED with a proofread mark for the review desk

#### Scenario: Balanced packs up to eight segments

- **WHEN** a Balanced run on a model whose profile batch size is 8 enters a unit of 12 short segments
- **THEN** they are drafted in batches of 8 and 4, the profile's size and not the dial's, and no reviewer call is made

#### Scenario: Max revises after the last segment

- **WHEN** a Max run decides its last segment
- **THEN** its Proofread stage starts before the run ends, reads the marked and doubtful windows, and then re-reads every story paragraph

#### Scenario: Every model gets the same dial

- **WHEN** one Max run uses `qwen2.5:1.5b` and another `gemma4:26b`
- **THEN** both re-read the whole book in their Proofread stage

### Requirement: Split an oversized segment at sentence boundaries

IF a segment's masked text alone is estimated above the current-part budget of the model profile's tier, THEN the system SHALL split it at the source language's sentence boundaries without ever separating a paired placeholder, draft each piece with the segment's context, join the pieces in order, and gate and decide the joined target as that one segment.

**Source**: FR-ALGO-03 and FR-ALGO-12, the requirements to fit every call in the model's budget without breaking structure (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-algo`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, `#token-budget`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#chunk-packing`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: some books hold one paragraph of 26,000 characters, far more than a small model answers well in one go. Splitting only that paragraph, and only between sentences, keeps the book's structure intact because the joined translation still goes back into the one paragraph it came from. The limit is now the current-part budget of the model's tier rather than a fixed 1,200 tokens.

#### Scenario: A huge paragraph is drafted in pieces

- **WHEN** segment `ch02.xhtml:7` holds 26,306 Latin characters, estimated at 7,563 tokens
- **THEN** it is drafted in several sentence-aligned pieces, each with the same system part and map items
- **AND** exactly one decision is made for `ch02.xhtml:7`, over the joined target

#### Scenario: A pair is never separated

- **WHEN** a sentence boundary of an oversized segment falls between `⟦g3⟧` and its partner `⟦g4⟧`
- **THEN** that boundary is not used, so `⟦g3⟧`, the text it wraps and `⟦g4⟧` are in one piece

#### Scenario: An unsplittable segment goes alone

- **WHEN** segment `ch09.xhtml:3` is one 6,000-character sentence with no interior boundary
- **THEN** it is drafted whole with no previous segment and no memory hit
- **AND** the log holds one WARN line naming `ch09.xhtml:3`

#### Scenario: No split that keeps pairs together sends the segment whole

- **WHEN** no split of an oversized segment keeps every placeholder pair together
- **THEN** the segment is sent whole, alone, without previous segments and memory hits, and one WARN line names the segment

### Requirement: Give every call of a run its context size and expected output

The system SHALL plan every model call a run makes — drafts, format re-asks, repairs, chapter summaries and proofread calls — within the run's one window ("Size a run's calls from the detected window"), and every draft, repair and proofread call SHALL also state the output it expects: the output allowance of its items.

**Source**: `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#effective-context`, `#service-owned-retry`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: Ollama silently cuts off a prompt longer than its context, which would drop the rules at the top, and a context size that changes from call to call makes a server reload the model; one value for every call of the run avoids both. The window itself, and which server is sent it, are the `inference` capability's "Use one context size for every call to a model"; the timeout the expected output sets is the `llm-provider` capability's.

#### Scenario: A draft to Ollama carries the context size

- **WHEN** a run drafts `Book.md:0` through the provider `ollama` for a model that reports a context length of 32,768
- **THEN** the request carries `"num_ctx":16384`, the window the `inference` capability decides

#### Scenario: A draft states its output allowance

- **WHEN** a Balanced run drafts `Book.md:0`, whose source display text `He opened the old door.` has 23 characters, from `en` to `uk`
- **THEN** the draft request states 16 expected output tokens

#### Scenario: The allowance follows length, band and script

- **WHEN** a draft, repair or proofread call is built
- **THEN** its items' output allowance is estimated from the length of their source display text, the upper bound of the language pair's length band and the target language's script

#### Scenario: A reviewer states a limit and a pre-scan or summary states none

- **WHEN** a run builds a pre-scan call and a chapter-summary call, a run making no reviewer call any more
- **THEN** neither states an expected output, and each carries its own output cap ("Cap every call's output at the reserve the context builder computes")

### Requirement: Flag a segment whose reply cannot be used, and continue

IF a segment's draft gets an unusable reply — empty content, an abnormal finish, a reply that is not the required JSON object after its one format re-ask, or an `emptyCompletion` or `contextWindow` error — THEN the system SHALL mark the segment FLAGGED at once with that error as its reason, with no repair call, and continue with the next segment, after any pause the review mode causes; it SHALL examine a reply for these causes in a fixed order and act on the first that applies.

**Source**: FR-DOC-05, the requirement that markup is restored exactly (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`, `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#segment-status-machine`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#empty-response-ordering`, `#repair-and-gate`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md#partial-results`. In plain words: one bad reply must not stop a book. A reply that holds nothing, that the server cut off, or that twice fails to be the one small JSON object asked for gives a repair nothing to work on, so the segment is flagged at once and an export writes its source rather than broken markup. A reply of only spaces counts as empty: accepting it would silently delete the paragraph. A context overflow belongs to the one segment whose prompt is too long. A reply that lost a mark or refused the task is still a reply the model can fix with the problem named, so at Balanced and Max it gets the one repair first. A provider refusing every request alike is not a bad reply; it pauses the run instead.

#### Scenario: A missing token flags the segment and the job goes on

- **WHEN** a Fast run translates a Markdown book with the paragraphs `He opened the *old* door.` and `She left.`, and the model answers the first paragraph's item with `ВІДЧИНИВ` — no mark, and one word that cannot hold the pair — in the batch draft and in its one re-draft
- **THEN** the first segment is FLAGGED with a high `markup` finding after those two calls and keeps no machine translation
- **AND** it keeps `ВІДЧИНИВ` as its rejected target
- **AND** the second segment is still decided

#### Scenario: A flagged reply stores its reason as a finding

- **WHEN** the model replies `HE OPENED` to the single draft of a TXT paragraph and again `HE OPENED` to its format re-ask, neither being the required JSON object
- **THEN** the segment holds one finding of kind `validation`, severity high, raised by `reply`

#### Scenario: An empty reply stores an emptyCompletion finding

- **WHEN** the model replies to the single draft of a TXT paragraph with two spaces and a normal finish
- **THEN** the segment holds one finding of kind `emptyCompletion`, severity high, raised by `reply`

#### Scenario: A reply of only whitespace is flagged as empty

- **WHEN** a TXT book has the paragraphs `One.` and `Two.`, drafted by a model whose batch size is 1, and the model replies to `One.` with two spaces and a line feed and a normal finish
- **THEN** the first segment is FLAGGED with `ErrorCode.emptyCompletion` and keeps no translation, after exactly 1 model call
- **AND** `Two.` is still sent to the model

#### Scenario: Empty content wins over a cut-off finish

- **WHEN** the model replies to a draft with two spaces and a finish of cut off by length
- **THEN** the segment is FLAGGED with `ErrorCode.emptyCompletion`, not `ErrorCode.validation`

#### Scenario: A reply cut off by length is flagged

- **WHEN** the model replies `HE OPENED` with a finish of cut off by length
- **THEN** the segment is FLAGGED with `ErrorCode.validation`, no repair call is made for it, and the next segment is sent

#### Scenario: A context-window error flags the segment and the run goes on

- **WHEN** a TXT book has the paragraphs `One.` and `Two.`, drafted by a model whose batch size is 1, and the model answers `ErrorCode.contextWindow` for `One.`
- **THEN** the first segment is FLAGGED with `ErrorCode.contextWindow` and keeps no translation
- **AND** the run neither pauses nor ends, and `Two.` is still sent to the model

#### Scenario: A refusal is repaired rather than flagged

- **WHEN** a Balanced run translates the TXT paragraph `He opened the old door.` from `en` to `uk`, the model answers the draft with `I'm sorry, but I can't translate this text.` and the one repair with `Він відчинив старі двері.`
- **THEN** the segment is not flagged, and is ACCEPTED as repaired and accepted after exactly 2 model calls
- **AND** on a Fast run the same refusal is FLAGGED after exactly 1 model call

#### Scenario: A cut-off self-heal reply flags at once and keeps the earlier target

- **WHEN** a Balanced run translates the TXT paragraph `He opened the old door.` from `en` to `uk`, the draft `HE OPENED THE OLD DOOR.` fails the untranslated-echo check, and the model answers the one repair with a finish of cut off by length
- **THEN** the segment is FLAGGED with `ErrorCode.validation` and no second repair is made
- **AND** it keeps `HE OPENED THE OLD DOOR.` as its machine translation

#### Scenario: Each unusable reply names its error code

- **WHEN** a segment's draft gets one of these replies
- **THEN** the segment is FLAGGED at once, keeps no machine translation, and its reason is that error: content that is empty or only whitespace, whatever its finish (`ErrorCode.emptyCompletion`); a finish that is not normal, such as a reply cut off by length (`ErrorCode.validation`); a reply that is not the required JSON object after the one format re-ask was already used (`ErrorCode.validation`; see "Ask once for a reply in the right shape"); the model answering `ErrorCode.emptyCompletion`

#### Scenario: A context-window error is particular to its segment

- **WHEN** the model answers `ErrorCode.contextWindow` to a segment's draft
- **THEN** the segment's own prompt does not fit the model's context, so retrying or pausing cannot help, and the segment is FLAGGED at once

#### Scenario: A self-heal reply that is empty or an error flags at once

- **WHEN** the reply to a segment's one repair is empty or cut off, or the model answers `ErrorCode.emptyCompletion` or `ErrorCode.contextWindow` to it
- **THEN** the segment is likewise FLAGGED at once, keeping as its machine translation the last candidate that restored through the placeholder gate, if there is one (the `quality-gates` capability's "Keep a usable draft as the machine target when a text check still blocks")

#### Scenario: Causes are examined in a fixed order

- **WHEN** a reply has more than one cause
- **THEN** the system acts on the first that applies, in this order: an error the call answered (see also "End the run Failed on an error no pause can fix"); empty content; an abnormal finish; a reply that is not the required JSON object; markup that does not restore; a refusal; and only then the other checks

#### Scenario: Markup and refusals go through self-heal

- **WHEN** a reply's markup does not restore after the code repairs, or the reply reads as a refusal
- **THEN** the segment is not flagged at once: at Balanced and Max it gets its one repair and is FLAGGED only if a block-level finding is left; at Fast lost marks get the one re-draft and a refusal is FLAGGED with no call

#### Scenario: A reason is a high finding raised by reply

- **WHEN** a segment is flagged at once
- **THEN** it stores its reason as a high finding whose kind is the error code's name, raised by `reply`

### Requirement: Tag every activity-log entry with one of eight kinds

Every entry the activity log holds SHALL carry one kind from a fixed vocabulary of eight, each shown by its tag, with a status role from the token catalogue, a mark that is not its colour, and text from the message catalogue by key; an answered draft, repair, chapter-summary or proofread call and a call interrupted by a pause or stop SHALL be technical `info` lines shown only while Show technical details is on, and the log SHALL keep its newest entries up to a fixed bound.

**Source**: FR-NOTIF-6a, FR-NOTIF-6b and FR-NOTIF-6c, the activity-log requirements (`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md#activity-log`), FR-A11Y-6, the rule that colour never carries meaning alone (`docs/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md#accessibility`), FR-UI-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-ui`), `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the log is the only record of what a multi-hour run decided. A fixed vocabulary of short tags in a column makes it scannable; the run now also proofreads and summarises chapters, so the summary kind now means a chapter summary was kept and a proofread kind is added: eight kinds. Colour alone cannot carry the kind, and the text comes from the catalogue so a Ukrainian window never shows an English log. An entry names a segment by its locator, because a file name and an ordinal mean nothing to the person reading it. A failed attempt and each step of an automatic recovery are `retry` lines.

#### Scenario: An accepted segment logs its kind and role

- **WHEN** the segment `ch7 · p42` is accepted during a run
- **THEN** an entry of kind accepted with the tag `ok` is appended, referring to the success role, which resolves to `#5f8a6b` under the light values
- **AND** it carries a mark distinguishing it from a segment-error entry without relying on colour

#### Scenario: A repair round logs fix

- **WHEN** the segment `ch07.xhtml:41`, whose locator is `ch7 · p42`, gets its one repair
- **THEN** an entry tagged `fix` naming `ch7 · p42`, and not `ch07.xhtml:41`, is appended, and its tag is rendered before its text in a monospace face

#### Scenario: A format repair and a resume after a provider error log retry

- **WHEN** the segment `ch7 · p43` gets one format re-ask, and later the run pauses on `ErrorCode.unreachable` and the person presses Try again now
- **THEN** an entry tagged `retry` naming `ch7 · p43` is appended for the re-ask, the pause is logged as `info`, and the resume appends a second `retry` entry

#### Scenario: An answered draft is a technical line

- **WHEN** the draft call for `ch7 · p42` is answered while Show technical details is off, and later while it is on
- **THEN** the first answer adds no visible line, and the second adds one `info` line

#### Scenario: Entry text comes from the catalogue

- **WHEN** the accepted entry for the segment `ch7 · p42` is rendered while the interface is in Ukrainian
- **THEN** its text resolves from a catalogue key with `ch7 · p42` substituted into it, and is not an English sentence with the locator joined to it

#### Scenario: Each kind maps to its own status role

- **WHEN** the eight kinds are read
- **THEN** accepted refers to the success role; repaired, glossary-applied, chapter-summary, proofread and milestone refer to the information role; retried refers to the warning role; and segment-error refers to the danger role

#### Scenario: The log drops its oldest entries

- **WHEN** a run appends more entries than the log's bound
- **THEN** the log holds exactly the bound, and the entry appended first is no longer present

#### Scenario: The seven kinds and their tags

- **WHEN** the activity log holds entries of the seven kinds that came before the Proofread stage and chapter summaries
- **THEN** each carries one kind, shown by its tag: accepted `ok`, repaired `fix`, glossary or memory applied `mem`, chapter summary kept `sum` (it meant "summary updated" while the rolling summary existed), retried `retry`, segment error `err` and milestone `info`; the eighth kind, proofread, is tagged `proof`

#### Scenario: Entries are monospace with the tag first

- **WHEN** the activity log renders an entry
- **THEN** it is rendered in a monospace face with the tag first, and its kind is readable without seeing the colour

#### Scenario: Each event produces its kind

- **WHEN** a run lives through its events
- **THEN** `ok` is logged when a segment is accepted; `fix` for the one repair call; `retry` for a format re-ask and for the resume that follows a provider-error pause; `mem` when a name scan adds glossary entries or a segment is reused from the translation memory; `sum` when a chapter summary is kept; `proof` when a proofread window is decided; `err` when a segment is flagged; and `info` for a run milestone — a stage start, a pause (including a provider-error pause), a resume or the finish

#### Scenario: A provider client's own retry makes no entry

- **WHEN** the provider client retries inside one call
- **THEN** the run does not see it and logs no entry

#### Scenario: An entry names the segment by its locator

- **WHEN** an entry about a segment is written
- **THEN** its text comes from the catalogue with the segment's locator, such as `ch7 · p42`, never its id, and any other value substituted into it

#### Scenario: The proofread and chapter-summary kinds and their tags

- **WHEN** the Proofread stage keeps a rewrite of `ch7 · p42` and chapter 3's summary is kept
- **THEN** an entry of kind proofread tagged `proof` naming `ch7 · p42` and an entry of kind chapter summary tagged `sum` naming chapter 3 are appended

### Requirement: Keep a chunk's drafts across a pause and drop them on stop

WHEN a run pauses — on request, for review or on an error — the system SHALL first commit the segments decided since the last commit and keep the batch's undecided drafts in memory while it waits; WHEN the run resumes, it SHALL decide the remaining segments from the drafts already made rather than drafting them again; WHEN a run is stopped, it SHALL commit the segments already decided and drop the undecided drafts, whose segments stay PENDING for the next run to draft.

**Source**: FR-RESUME-03 and FR-RESUME-05, the requirements that pause and stop lose nothing decided (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-resume`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: a batch is drafted in one call, so a pause in the middle of it keeps the drafts already paid for; an edit made during the pause reaches the batches drafted after it. A decision is never lost to a pause or a stop; a draft that was not yet decided is dropped by a stop and drafted again by the next run.

#### Scenario: A resume reads the stored decision again

- **WHEN** a run resumes after a pause
- **THEN** the system reads the paused segment's stored decision again and gives a target the person edited during the pause to every call built after the resume; the model call a pause interrupted is made again as the `resume` capability's "Pause on request at the next boundary" states

#### Scenario: A commit never rewrites a committed record

- **WHEN** a run commits
- **THEN** the commit holds only the segments decided since the previous commit and never writes again a record the run already committed, so a target the person saved during a pause is never overwritten by the run

#### Scenario: A review pause in the middle of a Balanced chunk

- **WHEN** an Assisted, Balanced run drafts `ch07.xhtml:40` … `ch07.xhtml:43` in one batch, accepts `ch07.xhtml:40`, flags `ch07.xhtml:41` and pauses on it
- **THEN** the drafts of `ch07.xhtml:42` and `ch07.xhtml:43` were made by the batch call before the pause
- **AND** `ch07.xhtml:40` and `ch07.xhtml:41` were committed before the run began to wait
- **AND** after the person saves an edit of `ch07.xhtml:41` and resumes, `ch07.xhtml:42` and `ch07.xhtml:43` are decided from their existing drafts, with no new draft call for either

#### Scenario: A saved edit survives the rest of the chunk

- **WHEN** an Assisted, Balanced run flags `ch07.xhtml:41` and pauses on it, the person saves the edit `Він рвучко відчинив двері.`, and the run resumes and finishes the batch
- **THEN** `ch07.xhtml:41` is still REVISED with `Він рвучко відчинив двері.`

#### Scenario: A reviewer call interrupted by a pause is made again

- **WHEN** a Balanced run has drafted `ch07.xhtml:40` … `ch07.xhtml:43` in one batch, a pause is requested while the one repair of `ch07.xhtml:42` is in flight (a run makes no reviewer call any more), and the run is resumed
- **THEN** the repair of `ch07.xhtml:42` is sent again and no draft call of the batch is repeated
- **AND** each of the four segments is decided exactly once

#### Scenario: A stop drops the undecided drafts

- **WHEN** a Balanced run has drafted `ch07.xhtml:40` … `ch07.xhtml:43` and decided `ch07.xhtml:40` and `ch07.xhtml:41`, and is stopped while the one repair of `ch07.xhtml:42` is in flight
- **THEN** `ch07.xhtml:40` and `ch07.xhtml:41` keep their decisions, and `ch07.xhtml:42` and `ch07.xhtml:43` stay PENDING with no stored translation
- **AND** the next run's first draft call starts at `ch07.xhtml:42`

### Requirement: Propose new names at the end of each body unit

WHEN a run decides the last segment of a body unit, the system SHALL run the code name scan the `glossary` capability defines over the source text of every segment decided so far and add each proposed name the glossary does not hold as a new entry — its forms grouped under one base form, its target state suggested, with no target and no gender — with no model call; WHEN it added at least one entry, the system SHALL announce the glossary update with the number added.

**Source**: FR-ALGO-C9, the step that keeps the glossary growing during a run (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunk-loop`), FR-GLOSS-01, the requirement for a name scan (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-gloss`), `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#name-term-dictionary`, `design.md#d3--the-book-map-lemma-entries-origin-target-state`. In plain words: a person who started from their own list of names still gets the names the book introduces later, found the same free, offline way, with no model call; an inflected form joins its base form instead of becoming a new name. A proposal never replaces what the person entered, and a name the person deleted stays deleted. The new entries are only suggestions with no target, so they tell the model that the word is a name and nothing more.

#### Scenario: A recurring name is added at the end of a chapter

- **WHEN** the glossary holds only the person's `Hale` → `Гейл`, and `Moreau` occurs twice in `ch02.xhtml` and twice in `ch03.xhtml`, never at the start of a sentence
- **THEN** nothing is added at the end of `ch02.xhtml`, and when the last segment of `ch03.xhtml` is decided the glossary gains `Moreau`, suggested, with no target, of type other and with no gender
- **AND** the run announces a glossary update labelled `+1`, and `Hale` → `Гейл` is unchanged

#### Scenario: A term held in another letter case is not added

- **WHEN** the scan proposes a term the glossary already holds in another letter case
- **THEN** the term is not added, because terms are compared without regard to letter case

#### Scenario: The person's entries and removals are kept

- **WHEN** names are proposed at the end of a unit
- **THEN** the proposals are subject to the `glossary` capability's "Keep the person's entries and removals when names are proposed"
- **AND** a new form of a name the glossary holds, such as `Зотову` beside `Зотов`, joins that entry's forms instead of becoming an entry

### Requirement: Show the segment just decided and the one in progress in the live panel

WHILE a run translates, the live card SHALL list the run's segments in reading order, the chunk being translated last and earlier chunks above it (the latest 500 rows), one line each with formatting marks left out and the full text in the tooltip, each with a marker: waiting, translating…, drafted, checking…, translated, to review, translated again alone, or kept as is.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md#jobprogress`, `docs/specification/mockups/README.md#step-4--translating--t1t12-a-c1` (Live card), `docs/specification/mockups/README.md#g11--state-markers-and-one-colour-map`, `docs/proposals/2026-10-19-ui-finalization-and-persistence.md` (T1–T4). In plain words: a book takes hours, and watching sentences go by is how a person judges the model before the run ends; the card shows every segment of the call with its state, keeps what was translated earlier in the run to scroll back to, and shows a draft as soon as it arrives, so a batch whose checks and repairs take a while does not look as if nothing happened until then.

#### Scenario: The decided segment above the one in progress

- **WHEN** a Fast call covers `ch7 · p41` and `ch7 · p42`, `ch7 · p41` is decided as `Вона втратила матір.`, and the draft of `ch7 · p42` has not arrived
- **THEN** the rows read `ch7 · p41` translated and `ch7 · p42` translating…, and Simple detail for `ch7 · p41` shows its source and translation

#### Scenario: A draft waits for the reviewer

- **WHEN** on a Balanced run the batch draft of `ch7 · p42` has arrived and its checks and one repair have not finished (a run makes no reviewer call any more)
- **THEN** its row reads "drafted, checking…" and its detail shows the draft, and it reads translated only when it is decided

#### Scenario: A decision moves the segment up

- **WHEN** `ch7 · p42` is accepted
- **THEN** its row reads translated, and "Show each segment when translated" selects it unless the person clicked another row

#### Scenario: The second row waits for the draft

- **WHEN** a segment has started and its draft is not ready
- **THEN** its detail reads "Being translated now…" under the source, with "Not translated yet." in the translation column

#### Scenario: The second row is empty between segments

- **WHEN** the last segment of a call is decided and the next call has not started
- **THEN** the rows of the chunk stay, the decided ones marked translated

#### Scenario: Earlier chunks stay as history

- **WHEN** the chunk `ch7 · p41`–`p44` is decided and `ch7 · p45` of the next chunk starts
- **THEN** the rows `ch7 · p41` to `ch7 · p44` stay above `ch7 · p45` with their markers, and the person can scroll back and pick one

#### Scenario: The history keeps the latest 500 rows

- **WHEN** a run has started its 501st segment
- **THEN** the list holds 500 rows, the first segment's row dropped

#### Scenario: A new run starts with an empty card

- **WHEN** a translation is started again after the app was closed, or after the previous run ended
- **THEN** the live card lists no rows until the run's first segment starts; the history is not stored

#### Scenario: A directed fix keeps the chunk's rows

- **WHEN** the one repair call for `ch7 · p42` is made in the chunk `ch7 · p41`–`p44` (a run makes no directed fix any more)
- **THEN** the rows `ch7 · p41` to `ch7 · p44` stay, and `ch7 · p42` reads translating… while the repair waits for the model

#### Scenario: A verbatim segment reads kept as is

- **WHEN** `ch7 · p43` is a chapter number accepted as it is, with no model call
- **THEN** its row reads "kept as is"

#### Scenario: A run that stopped shows its undecided rows as waiting

- **WHEN** the run ends Stopped or Failed while `ch7 · p43` and `ch7 · p44` were in a call
- **THEN** both rows read "waiting", and no row reads translating…

#### Scenario: Rows show display text from the announcements

- **WHEN** a row is shown
- **THEN** it holds display text only, taken from the run's announcements, with formatting marks left out and the full text with marks `⟦1⟧` in the tooltip

#### Scenario: A paused run shows waiting rows

- **WHEN** the run is paused while segments 5–8 are in flight
- **THEN** their rows read waiting

### Requirement: Draft consecutive segments in token-budgeted batches

The system SHALL draft the segments that need a model call in batches: a segment no batch has dealt with yet and the next ones that need a call, up to the current batch size and within the current-part budget of the model's tier, SHALL go in one call whose reply is the JSON object `{"items":[{"id","target"}]}`. A segment kept as it is, an auxiliary text, a translation-memory reuse and a segment larger than the current-part budget SHALL never join a batch.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#batch-draft`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`, `design.md#d11--batch-format-and-batch-size`. In plain words: a book of short paragraphs does not pay the whole prompt once per paragraph; the model answers several at once, and if it loses one of them only that one is asked again. The reply has one flat shape with nothing but ids and targets: the optional `terms` field and the key-term list the prompt used to ask about are gone, and the lexicon learns renderings from the decided text alone. The size comes from the model's profile, never from the dial or the review mode.

#### Scenario: Four paragraphs go in one call

- **WHEN** a chunk of four paragraphs is drafted by a model whose batch size is 4 and the reply answers ids 1 to 4 with their marks
- **THEN** one draft call about the four segments is made and each segment is decided from its own entry

#### Scenario: A number-only paragraph is not in the batch

- **WHEN** a chunk holds a paragraph `1881` between two sentences
- **THEN** the batch carries the two sentences as ids 1 and 2 and `1881` is kept with no call

#### Scenario: Reported terms are verified before they count

- **WHEN** a batch answers the item that names `master` with a target holding `господар`
- **THEN** the prompt asked for no `terms` field and the reply holds none, and the lexicon counts `master → господар` only from the committed decision, as "A stored chunk teaches the lexicon" says

#### Scenario: A stored chunk teaches the lexicon

- **WHEN** a chunk's decided records are committed
- **THEN** its accepted drafts are counted by co-occurrence and the learned rendering of each term the chunk names is published to the lexicon before the next chunk's context is read; the decisions of a chunk that was not committed are not counted

#### Scenario: The first decision wins and a name gets its spelling

- **WHEN** `master` was learned as `господар` and the model then writes `учитель` as often
- **THEN** `господар` stays established while no unrelated word has clearly overtaken it, and a stop word such as `коли` is never learned as a rendering
- **AND** WHEN the glossary holds `Velemir` with no target and the decided pairs carry `Велемир` or `Велімир` in three of its segments **THEN** the entry's target becomes the suggestion `Велемир`, which the next call shows among its map items, and a target already there is never replaced

#### Scenario: A learned rendering belongs to one term

- **WHEN** the decided pairs name `Mr` with `пан` and `Mrs` with `пані`, or two other terms would both learn `господар`
- **THEN** `Mr` learns `пан` and `Mrs` learns `пані` (a match of `Mrs` counts for `Mr` only when `Mrs` is not tracked), and of two terms that would share a rendering stem the one with the stronger evidence keeps it, unless the terms are one word family (`master`, `masters`; an abbreviation is its own word)

#### Scenario: The dictionary form and the common word

- **WHEN** the pairs of `earth` carry `землі`, `земля` and `землю` and the target language's file lists its oblique endings
- **THEN** the learned rendering is `земля`; without that data the most extended form stays the base form
- **AND** a term the book writes with a capital only, in the segments that name it (`The Times`), learns nothing unless it is a bundled title

#### Scenario: Items are numbered within the batch

- **WHEN** a batch is built
- **THEN** its ids are `1…n` within the batch, and no item carries a `terms` object

#### Scenario: The batch prompt carries the shared context once

- **WHEN** a batch prompt is built
- **THEN** it carries the items and, once for all of them, the context builder's parts: the earlier chapters' summaries, the recently-present card, the map items the window names, the previous accepted pairs, the next source and the reminder

#### Scenario: A locked name is prefixed with its item's id

- **WHEN** an item holds a locked name
- **THEN** its line in the batch prompt is prefixed with the item's id (`3: ⟦g0⟧ → name`)

#### Scenario: Manual review drafts every segment alone

- **WHEN** the review mode is Manual review
- **THEN** the segments are drafted in batches of the model's batch size as in any other mode, and a pause after a decided segment keeps the drafts of the rest of its batch

### Requirement: Validate a batch reply per id and draft a failing id alone

The system SHALL validate a batch reply per id — the id exactly once with text, the source's marks in order, the length band and the document's placeholder gate — and SHALL give each segment its own verdict: an id the reply never answered SHALL get one first draft of its own, a batch of one, and an item that came back but failed a check SHALL go to its one repair at Balanced and Max, or, at Fast, be decided from its findings after the one re-draft an item with lost formatting marks gets.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#batch-draft`, `design.md#d11--batch-format-and-batch-size`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: if the model loses one item of a batch only that one is asked again, as a first draft; an item it answered badly already has a draft, so it gets the one repair with the defects named rather than a blind second draft.

#### Scenario: An unreadable reply sends every segment to its own draft

- **WHEN** a batch reply is unreadable and so is the reply to its one format re-ask, or the call is answered with an error the single draft would flag its segment for
- **THEN** every segment of the batch gets its own first draft

#### Scenario: The quality loop stays per segment

- **WHEN** a batch is answered
- **THEN** the checks, the one repair, the acceptance rule and the pause, resume and skip behaviour are per segment, so a pause after a segment of a batch keeps the answers of the rest and a resume makes no new batch call for them

#### Scenario: A failed batch call is one step

- **WHEN** a batch call fails
- **THEN** it is routed as one step named by its first segment and counts one toward the step's failure budget

#### Scenario: A missing id falls back alone

- **WHEN** the reply answers ids 1, 3 and 4 of four
- **THEN** a single-segment draft is made for the second segment only, and the other three are taken from the batch

#### Scenario: Two or more missing ids are asked again together

- **WHEN** the reply answers ids 1 and 4 of four
- **THEN** one format re-ask carries items 2 and 3 only with the same context, their entries are taken from it, no single-segment draft is made, and the batch size is judged by the first reply alone (it halves); if the re-ask fails or is unreadable the two are drafted alone

#### Scenario: A merged pair falls back for both

- **WHEN** the entry for id 2 holds the translation of items 2 and 3 and id 3 has no entry
- **THEN** items 2 and 3 are each drafted alone and the batch size halves

#### Scenario: A refusal sends every segment to its own draft

- **WHEN** the batch reply and the reply to its format re-ask are both an apology in prose
- **THEN** each of the four segments is drafted alone, one call each

#### Scenario: A pause keeps the answered batch

- **WHEN** the run pauses after the first decided segment of a batch of four and is resumed
- **THEN** no further model call is made for the other three, which are decided from the batch

#### Scenario: Skip during a batch call skips its first segment

- **WHEN** the run is paused on a provider error in the batch call about segments 1 to 4 and the person skips
- **THEN** segment 1 is flagged and segments 2 to 4 are drafted in a new batch call

#### Scenario: Skip in a paused batch step skips its first segment

- **WHEN** a batch step is paused on an error and the person chooses Skip
- **THEN** the step's first segment is skipped, flagged as a single draft's skip is, and the other segments batch or draft normally at their turn

#### Scenario: An item that failed a check is repaired, not drafted alone

- **WHEN** on a Balanced run the item for id 3 comes back as `Він сказав, что прийде.` and raises a function-word finding
- **THEN** id 3 gets its one repair with that defect named, and no first draft of its own

### Requirement: Adapt the batch size to the replies

The batch size SHALL start at the model profile's batch size, halve when a reply loses an id, repeats one, merges two items or names an id the batch never held, and grow by one after three clean batches, never above the profile's batch size and never below the smaller of 2 and the profile's size; a call that got no reply from the model SHALL leave the size as it was.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#batch-draft`, `design.md#d11--batch-format-and-batch-size`. In plain words: the owner's decision that small models start at 4 segments per batch and that a larger batch is used only where it is shown to be as safe; the size shrinks when a model starts losing items and grows back, but never past what its profile allows. The old start at 8 and the ceiling of 16 are gone.

#### Scenario: A run that batches never drafts every segment alone

- **WHEN** the batch size of a model whose profile size is 4 has halved repeatedly
- **THEN** it stays at 2 or more, so a run that batches never halves its way to drafting every segment alone

#### Scenario: A call with no reply says nothing about the size

- **WHEN** a provider outage, a step the run gave up on, or an error the single draft would flag its segment for answers a batch call
- **THEN** the batch size is left as it was

#### Scenario: A batch is never larger than its chunk

- **WHEN** a model whose profile batch size is 4 translates on Fast, Balanced and Max
- **THEN** no batch on any dial holds more than 4 segments, however many clean batches came before

#### Scenario: Outages never switch batching off

- **WHEN** three batch calls in a row are answered with `contextWindow` and the fourth with a readable reply
- **THEN** each of the first three is followed by its single drafts and the fourth is a batch call again

#### Scenario: The size adapts

- **WHEN** a model's profile batch size is 8 and the first batch of eight loses an id
- **THEN** the next batches hold at most four items

#### Scenario: The size grows back only to the profile's

- **WHEN** a model whose profile batch size is 4 has halved to 2 and then answers nine clean batches
- **THEN** the size grows to 3 and then to 4, and stays at 4

### Requirement: Fit every batch prompt in the window

The window SHALL hold every prompt: before a call is sent, its prompt, reserved output and a safety margin SHALL fit the run's window and its tier's ceiling; a prompt over it SHALL drop, in order, the next source, the oldest chapter summaries down to the newest, the previous segments from the oldest, the map items with the fewest occurrences (never a pinned or locked entry or a speaker's gender the window names), the last summary, and only then batch segments, down to one.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#batch-draft`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: what helps least goes first. The current part is never cut to make room for context, and some facts are never dropped: a pinned or locked name in the window, and the gender of a speaker in the window.

#### Scenario: The chunk budget reserves the dynamic context

- **WHEN** a batch is sized
- **THEN** its previous segments, its next source and the earlier chapters' summaries each stay within their own share of the tier's ceiling, so the focus segments keep their current-part budget

#### Scenario: Optional context goes first

- **WHEN** a batch prompt is over its tier's ceiling while carrying a next source and five earlier chapter summaries
- **THEN** the next source is dropped first, then the oldest summaries one by one, and the prompt is sent as soon as it fits

#### Scenario: A pinned name in the window is never dropped

- **WHEN** a prompt is cut down to its last droppable part and the window names the pinned `Зотов`
- **THEN** the line for `Зотов` is still sent

### Requirement: Size a run's calls from the detected window

The system SHALL read the model's context length when a run is prepared, through the provider's own detection, a failed detection never being an error, and SHALL plan every call of the run, its prompt and its output, to fit the one window the `inference` capability's "Use one context size for every call to a model" decides for the model, capping each reply at half of that window at most and sending the size only where that requirement says a server receives one.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#effective-context`, `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#token-budget`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: the owner's decision that 16k is the default window; a model loaded with a 4k context is never sent prompts for more, and one value for every call keeps a server from reloading the model between call kinds. How the window is chosen (the person's value, else the smallest of the detected length, the profile's cap and 16,384) and that only Ollama's native API is sent it as `num_ctx` are stated once, in the `inference` capability; this requirement is how the run budgets inside it.

#### Scenario: A 4096 window

- **WHEN** the provider reports 4096 for the run's model
- **THEN** every prompt of the run is planned for 4096, an Ollama request carries `"num_ctx":4096`, and every output cap is at most 2048

#### Scenario: Nothing detected

- **WHEN** the provider reports no context length
- **THEN** every prompt is planned for 16,384 and an Ollama request carries `"num_ctx":16384`

#### Scenario: A large detected window is held to 16,384

- **WHEN** the provider reports 131,072 for the run's model and no override is set
- **THEN** every prompt of the run is planned for 16,384 and an Ollama request carries `"num_ctx":16384`

#### Scenario: The person's override wins

- **WHEN** the person set a context window of 8,192 for the model and the provider reports 32,768
- **THEN** every prompt of the run is planned for 8,192 and an Ollama request carries `"num_ctx":8192`

### Requirement: Keep the prompt free of garbage and its prefix byte-identical

The system SHALL leave out of every prompt an empty value, an empty heading, a placeholder line such as `(none)`, `n/a` or a dash, a repeated line, and a map item none of whose forms occurs in the window; the system part of every draft, repair and proofread call SHALL be the same bytes for the whole run, and every part that varies SHALL sit in the one last user message.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#prompt-construction`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: a small model reads every token it is given and may echo it, so nothing is sent that carries no information; and a system part that never changes in a run lets a server that reuses prompt beginnings read it once.

#### Scenario: Filler never reaches the model

- **WHEN** a draft context holds the previous text `(none)`, a blank map line and the same memory line twice
- **THEN** the prompt has no previous part, no map part and the memory line once

#### Scenario: Two calls share their prefix

- **WHEN** a draft of `ch02.md:14` and the one repair of `ch02.md:16` are built in one run
- **THEN** their system messages are equal, byte for byte

#### Scenario: Each call logs its prompt sizes at DEBUG

- **WHEN** a model call is made
- **THEN** it logs, at DEBUG, the estimated tokens of the system message, of the rest of the prompt and the system message's share, and, when the server reports them, the prompt-evaluation time, the cached prompt tokens and the prompt tokens it counted beside the estimate; the estimate's rate is not changed by them

### Requirement: Tell the model about the language pair from one language-rules map

The system SHALL take every language rule a call carries from the bundled language files — the target language's rules, the source language's reading notes and the pair's own file — opening only the files of the run's target, source, pair and the generic file, so that no rule wording is written in code (which lines the fixed system part carries is "Choose the writing rules once per book") and a language with no file is translated with the generic rules.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#language-rules`, `design.md#d7--language-data-function-word-files-and-rule-keys-for-every-language`, `design.md#d9--rules-per-book-the-reminder-and-pair-examples`. In plain words: what to watch for in Ukrainian, or in going from Russian to Ukrainian, is written once as data, sent only for the pair in hand and only where the book needs it, and a language nobody has written rules for is translated with general advice instead of failing. Every language may have such files; none holds another language's words.

#### Scenario: English to Ukrainian loads only its own files

- **WHEN** the rules for English to Ukrainian are built
- **THEN** only `generic.properties`, `uk.properties`, `en.properties` and `pairs/en-uk.properties` are opened
- **AND** no rule of any other language appears in the call

#### Scenario: An unknown language gets the generic rules

- **WHEN** the target is a tag with no file, such as `ja`
- **THEN** the call carries the generic rules and the translation runs

#### Scenario: The reviewer also gets the reviewer checks

- **WHEN** the draft and proofread system parts are built for English to Ukrainian
- **THEN** neither carries `Check:` lines, a run making no reviewer call any more

#### Scenario: Rules forced to generic

- **WHEN** `BOOKLOOM_EVAL_RULES=generic` is set and the rules for English to Ukrainian are built
- **THEN** they are the Ukrainian, English and pair rules as usual, because the application no longer reads that variable

#### Scenario: A language is matched by its primary subtag

- **WHEN** a language tag with a region is looked up
- **THEN** the file of its primary subtag is used, and a tag with no file gets the rules of `generic.properties`, so that any language the platform can name keeps working

#### Scenario: The section is built once per pair

- **WHEN** a run makes many calls for one language pair
- **THEN** the system opens only the files of the target, the source, their pair and `generic.properties`, and builds the rule lines once per run, so every call of a run carries the same bytes

#### Scenario: Rules stay within their token limits

- **WHEN** the rule lines are built
- **THEN** a target's rules stay within 200 estimated tokens, a source's notes (at most three) within 80 and a pair's rules within 100

#### Scenario: Examples and name lines live in the language files

- **WHEN** a prompt shows example pairs, an illustration or name-rendering lines
- **THEN** they are keys of the language and pair files, and no per-language example file and no multi-language advice stays in a static prompt

#### Scenario: The English to Ukrainian draft system message is smaller

- **WHEN** the system part of an en→uk draft is built for a book with no dialogue and no numbers, and for a book with both
- **THEN** the first holds neither the dialogue nor the number rule line and is smaller than the second

### Requirement: Infer an unstated first-person narrator's gender from the decided translations

WHEN the Translate stage has ended, before the Proofread stage settles deferrals, and the brief's first-person narrator has no gender, or only a likely one (the source shows no gendered past-tense form), the application SHALL count the narrator's «I + past verb» forms in the decided targets by the target's `firstPersonPast` pattern and, WHEN at least 20 segments hold the source first-person pronoun in narration, there are at least 8 forms and one gender holds at least 80 % of them, SHALL store that gender as inferred by the run.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#narrator-and-characters`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a Russian or Ukrainian source usually says the narrator's gender in its own past-tense verbs, and the `book-brief` capability reads it there before Start; an English source does not, and then the translations themselves usually do: most «я пішов» against a few «я пішла» says the narrator is a man and the few are slips, while a book that is only 70 % feminine is left unknown rather than corrected into the wrong gender.

#### Scenario: A man's book with two feminine slips

- **WHEN** a first-person English book of 24 segments has the narrator unset, and 22 masculine and 2 feminine «я + verb» forms are in the decided targets
- **THEN** the narrator is stored as male, inferred by the run, and the Proofread stage settles the two segments as it settles any narrator deferral

#### Scenario: Seventy per cent is not enough

- **WHEN** 7 of 10 forms are feminine
- **THEN** the narrator stays unknown and nothing is re-rendered

#### Scenario: The count reads outside quotes and dialogue

- **WHEN** the count runs
- **THEN** the source narration (outside quotes and dialogue) is read for the first-person pronoun and the targets are read outside quotes and dialogue, with the pattern's named groups `female` and `male`; a language without the pattern infers nothing

#### Scenario: An unspecified person becomes first person only by the detector

- **WHEN** the brief's narrator person is unspecified and the gender is inferred
- **THEN** the narrator becomes first person only when the narrator detector reads the book as first person

#### Scenario: The inference is logged and its slips are deferred

- **WHEN** a gender is inferred
- **THEN** the forms, the agreeing forms and the gender are logged at INFO, and one deferral waiting on the narrator is left on each segment holding a form of the other gender, for the Proofread stage to settle like a character's

#### Scenario: A settled narrator is never replaced

- **WHEN** the narrator's gender was set by the person, verified by the brief, read from the source in Prepare or inferred by the run, or the narrator is third person
- **THEN** it is never replaced

#### Scenario: A merely suggested gender is replaced

- **WHEN** the narrator's gender is only likely and the inference holds
- **THEN** the likely value is replaced, because the decided targets are stronger evidence than a guess, and the replacement is logged at INFO

#### Scenario: The count asks no model

- **WHEN** the inference runs
- **THEN** no model call is made

#### Scenario: A source that shows the gender needs no count

- **WHEN** a Russian source's narration holds `я дошла` and `я нашла`, and Prepare stored the narrator as female from them
- **THEN** no count runs when translation has ended

### Requirement: Tell the model who narrates and who is present

The application SHALL add one narrator line to the run's style sheet when the brief names a narrator person, and no line, with the style sheet and its hash unchanged, while the narrator is unstated; a gender the person set, the book proved or the run inferred SHALL be stated and ordered, a likely gender SHALL read `likely male` or `likely female` and order no form, and who else is present SHALL reach a call only through its map items and its recently-present card.

**Source**: `docs/specification/01_Product/12_PROMPT_CATALOG.md#narrator-and-characters`, `design.md#d6--brief-policy-keys-declared-conventions-and-story-shape`, `design.md#d10--chapter-summaries-and-the-recently-present-card`. In plain words: a model that is told once that the narrator is a man does not need to guess a verb ending, and nothing is held to a guess. A separate sheet of the characters in the passage is no longer sent: the map items of the window and the recently-present card already say who is there.

#### Scenario: An unstated narrator changes nothing

- **WHEN** the brief's narrator is not stated
- **THEN** the style sheet is the default sheet and no narrator line is sent

#### Scenario: The narrator line reaches every call

- **WHEN** the brief holds a first-person male narrator and a book is run at Balanced
- **THEN** the system message of every draft, repair and proofread call carries `Narrator: first person, male.`

#### Scenario: Preparation reads the narrator without touching the brief

- **WHEN** a run prepares a first-person English book whose brief states no narrator
- **THEN** the preparation result holds a first-person narrator hint and the stored brief still has no narrator

#### Scenario: The narrator line orders the form

- **WHEN** the brief names a first-person female narrator
- **THEN** the line reads `Narrator: first person, female. Write the narrator's own "I" verbs, adjectives and participles in the feminine form ...`, with a masculine and a gender-less first-person line in the same way

#### Scenario: A third-person narrator is stated

- **WHEN** the brief names a third-person narrator
- **THEN** the line reads `Narrator: third person, outside the story ...`

#### Scenario: No separate character sheet

- **WHEN** a draft's window names `Lyra` (female) and `Hale` (male)
- **THEN** the call carries their map lines and no `[Characters in this text — keep their gender and agreement]` part

### Requirement: End a model call that outlives its ceiling

WHILE a run is running, the system SHALL watch its model calls on a daemon ticker of its own and SHALL end a call —
interrupting it through the job's control, as a pause does — when the attempt has been outstanding longer than 1.5
times its own timeout (15 min for a call whose model announces none), or when no segment has been decided for 20
minutes while a call is outstanding. The ended call SHALL answer `ErrorCode.timeout` and go through the run's ordinary
timeout path.

**Source**: the overnight plan, step 11, the decision that a stalled call must never hold a night's run (`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`). In plain words: a provider that accepts a request and never finishes it would otherwise keep the run waiting forever; this holds for every call kind, the proofread and chapter-summary calls included.

#### Scenario: A reviewer call that never returns is ended at 135 s

- **WHEN** a proofread call with a 90 s timeout has been outstanding for 136 s (a run makes no reviewer call any more)
- **THEN** the watchdog ends it, the gate is released, and the call answers `ErrorCode.timeout`

#### Scenario: A call that never returns is retried, then flagged

- **WHEN** the draft of `Book.txt:1` never returns three times in a row
- **THEN** the watchdog ends each attempt, the run pauses and recovers by itself twice, flags `Book.txt:1` with `ErrorCode.timeout` on the third, and ends Completed with two accepted

#### Scenario: A failing check is logged and swallowed

- **WHEN** the ticker's check fails
- **THEN** the failure is logged and swallowed, and the ticker goes on

#### Scenario: An ended call is given up and logged

- **WHEN** the watchdog ends a call
- **THEN** the provider client gives it up and the inference gate is released, and one WARN line names the call's kind, segment and elapsed time

#### Scenario: The ordinary timeout path

- **WHEN** a call has been ended with `ErrorCode.timeout`
- **THEN** the run pauses and recovers by itself, and after two such pauses the segment is flagged and the run goes on

#### Scenario: The watchdog is the last resort

- **WHEN** a reply stalls
- **THEN** both provider clients bound a whole reply by the request timeout (the OpenAI-compatible client's request timeout covers a body that stalls after its head), so the watchdog is the last resort behind them, not the first

#### Scenario: The ticker runs every 5 s with the run

- **WHEN** a run starts and ends
- **THEN** the daemon ticker checks every 5 s, is started with the run and is stopped with it

### Requirement: Run a whole book in bounded memory

The system SHALL keep nothing that grows with the number of model calls or log lines of a run, and SHALL keep per
segment only what the in-memory store holds for it (its record, targets, snapshot and memory entry). Logging SHALL
stay synchronous and size-capped, with no queue that a slow disk could fill.

**Source**: the overnight plan, step 13, the decision that a whole book left running all night must not run out of memory (`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`). In plain words: a 3,700-segment book under injected faults retains about 2.5 KB per decided segment and nothing else that grows; the soak harness (`./gradlew :pipeline:soak`) proves it. A snapshot now stores only what varied per call, so it is smaller.

#### Scenario: A night's calls do not grow the run summary

- **WHEN** a run makes 1,500 model calls, the first 500 of 10 s and the next 1,000 of 1 s
- **THEN** the final summary line reads `calls=1500 avgCallMs=4000 p95CallMs=1000` and holds 1,000 call times

#### Scenario: A whole book under faults finishes in bounded memory

- **WHEN** a generated book of 3,700 paragraphs runs on the Balanced dial over a model that times out, hangs, answers 5xx bursts, is unreachable for 25 minutes once, unloads its model, answers empty, damages placeholders, refuses, answers a proofread call with no JSON and throws
- **THEN** the run ends Completed with no segment pending, the export re-opens with every segment, the live threads are as many as before, and the retained heap grows by less than 16 KB per segment

#### Scenario: The run's own counters are bounded

- **WHEN** a run makes many calls
- **THEN** the run summary counts every call but takes its 95th percentile over the newest 1,000 call times only, a step's pause count is forgotten once the step answers, is flagged or ends the run, and the whole-word pattern cache shared by every run of a session holds at most 4,096 patterns

#### Scenario: The window's lists are bounded

- **WHEN** a run produces many log entries and rows
- **THEN** the window shows at most 500 activity-log entries and queues at most as many between two ticks, dropping the oldest; it remembers at most 256 segments' locators and repairs, keeps at most 256 recent failed attempts for the connection chip and at most 32 undecided live rows, and grows the flagged queue by appending the new rows rather than replacing the rows it already shows

### Requirement: Plan a run's steps and count the time spent

WHEN a run starts and as it goes, the system SHALL plan one draft step per segment to translate — a segment kept verbatim being a draft step done at once with no call — one chapter-summary step per chapter while the book's switch is on, and proofread steps: at Max one per window of the whole-book round from Start, and at Balanced and Max one per window marked so far; it SHALL add a step when a repair, a format re-ask or a resend starts, and count the time spent without the time paused.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#progress-counting`, `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the time left must count the Proofread stage and the chapter summaries from Start, not only once they begin, so a Max run does not seem nearly done when a whole re-read of the book is still ahead. Max's whole-book round is known at Start; the marked windows grow as the Translate stage marks segments. Until the model has a measured proofread time, a proofread step is estimated from its measured draft time per segment times the batch size and labelled an estimate. How the steps become the bar and the time left is the `app-shell` capability's.

#### Scenario: A twelve-segment unit on Balanced

- **WHEN** a Balanced run enters a unit of 12 segments, 2 of them kept verbatim
- **THEN** 12 draft steps are planned, the 2 verbatim ones done at once with no call, and no check step is planned for any chunk

#### Scenario: Steps are counted by quality dial

- **WHEN** a book of one chapter of 3 segments, with the chapter-summaries switch off, is started on Fast, Balanced and Max with a model whose batch size is 4
- **THEN** each run plans 3 draft steps; Max also plans 1 proofread step for its whole-book round at Start; Fast plans no proofread step; Balanced plans a proofread step only when a segment is marked

#### Scenario: Added work joins the total

- **WHEN** a Balanced run of 3 segments plans 3 steps and the one repair of one of them starts
- **THEN** the total becomes 4 steps when the repair starts

#### Scenario: A check with nothing to read is done at once

- **WHEN** a Balanced run's Proofread stage finds no marked or doubtful segment
- **THEN** no proofread step is left to do and no proofread call is made

#### Scenario: A pause does not count as elapsed

- **WHEN** a run translates for 10 minutes, is paused for 3 and translates for 5 more
- **THEN** the elapsed time is 15 minutes

#### Scenario: A proofread step is estimated until it is measured

- **WHEN** a model has a measured 3.0 s per drafted segment and no measured proofread time, and its batch size is 4
- **THEN** each planned proofread step counts 12 s, labelled an estimate

## REMOVED Requirements

### Requirement: Repair invalid structured draft replies once

**Reason**: The structural repair, the model placeholder repair and the self-heal rounds that followed are replaced by a format re-ask that is not counted as a repair, code repairs of the marks, and one repair call per failing segment at Balanced and Max (none at Fast but a lost-marks re-draft).
**Migration**: "Ask once for a reply in the right shape", "Put lost formatting marks back by code before any model call", "Show a drop cap to the model as one whole word" and "Keep a refused reply as the segment's rejected target" in this capability; the one repair itself is the `quality-gates` capability's "Give a failing segment one repair at Balanced and Max" and "Count only content-changing calls as the one repair".

### Requirement: Build every prompt from the catalogue's templates

**Reason**: The single-draft prompt, the three batch formats, the directed-fix, placeholder-repair, reviewer, revision and rolling-summary prompts are replaced by one translate prompt (a single draft is a batch of one), a format re-ask, a repair, a proofread and a chapter-summary prompt, all rendered through the one context builder.
**Migration**: "Render every prompt of a run from prompt files held as data" and "Build every model call of a run through one context builder".

### Requirement: Assemble each draft's context with the load-bearing items at the edges

**Reason**: The per-draft context package (rolling summary first, glossary lines for the chunk, preceding targets of the same unit only) is replaced by one context builder with a fixed system part, a window across chapter boundaries, map items chosen by their forms, chapter summaries and a reminder.
**Migration**: "Build every model call of a run through one context builder", "Put the fixed parts first and the window last in every translate-family prompt", "Fill the window with the accepted segments before the focus and the next source", "Inject a map item only when one of its forms occurs in the window"; the stored snapshot is the `local-storage` capability's "Store each distinct fixed part of a run's prompts once per project".

### Requirement: Keep a rolling summary of the book so far

**Reason**: The owner's decision that a two-to-three-sentence summary per finished chapter replaces the rolling summary refreshed every 20 accepted segments and written by the model only at Max.
**Migration**: "Write a two-to-three-sentence summary when a chapter finishes", "Give later windows the summaries of all earlier chapters" and "Show who was recently present in a code-built card".

### Requirement: Cap the output of every call that states an expected output

**Reason**: The cap formula and the reviewer's per-pair cap are replaced by the reserve the context builder computes from the focus items, the pair's output ratio and the model profile; the reviewer is gone.
**Migration**: "Cap every call's output at the reserve the context builder computes".

### Requirement: Record deferrals and revise backwards on Max

**Reason**: A likely gender no longer creates a deferral, a changed or confirmed gender gets its own deferral, a term deferral now also covers pinned entries, and deferrals are settled in the Proofread stage at every dial instead of by Max's backward revision.
**Migration**: "Record a gender deferral only for an entry with no gender at all", the `glossary` capability's "Register a gender-changed deferral when an enforced gender arrives", "Record a term deferral when the person changes a pinned or locked target" and "Settle every deferral whose fact has arrived in the Proofread stage".

### Requirement: Revise backwards after the last segment on Max

**Reason**: Max's backward revision is replaced by the Proofread stage inside every run: the term sweep and deferrals by code at every dial, minimal-edit window calls behind the never-worse gate at Balanced and Max, and a whole-book re-read at Max.
**Migration**: "Run the Proofread stage after the last chunk at every dial", "Sweep a changed rendering through the decided text by code", "Proofread the marked windows at Balanced and Max with a minimal edit", "Re-read every story paragraph at Max in a second round" and "Make a proposal of a proofread rewrite of the person's own edit".

### Requirement: Decide a chunk's segments in document order

**Reason**: The three-phase chunk loop (draft every segment, one reviewer call, then self-heal rounds) goes with the reviewer; a batch is drafted in one call and its segments are decided in order through the one repair, and previous segments are accepted targets only.
**Migration**: "Decide a batch's segments one at a time in document order".

### Requirement: Size every call from one context budget

**Reason**: The budget of a window of at most 8,192 with a dynamic context capped at 1,100 tokens and a chunk of at most 1,200 source tokens is replaced by per-tier shares and ceilings from the model profile inside one run window of up to 16,384.
**Migration**: "Keep every prompt under its model tier's ceiling", "Fit every batch prompt in the window" and "Size a run's calls from the detected window".

### Requirement: Give each draft the characters it names

**Reason**: The separate character gender sheet is replaced by the map items of the window, which carry a settled gender (the person's, the book's or a rule's), and the recently-present card; a likely gender is no longer sent by default.
**Migration**: "Write each injected map item as one line of its target, type and settled gender", "Inject a map item only when one of its forms occurs in the window" and "Show who was recently present in a code-built card".

### Requirement: Always show the scene summary under the live detail

**Reason**: The scene summary went with the rolling summary; the live card now lists each chapter summary as it is kept.
**Migration**: the `app-shell` capability's "Show a chapter summary as a live-card row"; the recently-present card a call carried is part of its prompt ("Show who was recently present in a code-built card").

## RENAMED Requirements

- FROM: `### Requirement: Tag every activity-log entry with one of seven kinds`
- TO: `### Requirement: Tag every activity-log entry with one of eight kinds`
