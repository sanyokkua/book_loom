## ADDED Requirements

### Requirement: Build the same requests for every model

The system SHALL build the requests of a run — their instructions, their context and the form of the reply they ask for — the same way for every model, and SHALL hold no request format, instruction text or reply reader that is chosen for a translation-only model family; only the settings of the model's profile (reasoning, sampling, the system-role form, the batch size and the context size) SHALL differ between models.

**Source**: `research/review-digest.md#f1--the-model-sets-the-quality-ceiling`, `research/review-digest.md#model-table`, `research/review-digest.md#owner-decisions`, `design.md#d21--defaults-instead-of-questions`. In plain words: the review ran translation-only models through the app: one scored level with the recommended general model at five times the time and lost inline marks in its own documented format, another looped on 15 of 34 replies and scored 2.1. The app's own request was equal or better for eleven of twelve models. So no model family gets a request of its own; a model either works with the one request or is not the model to use.

#### Scenario: A translation-only model gets the app's request

- **WHEN** a draft call is built for the model id `translategemma:27b` and for `gemma4:26b` over the same paragraph of the same book
- **THEN** both requests carry the same system instructions, the same context and the same reply schema

#### Scenario: Only the profile's settings differ

- **WHEN** the two models' profiles differ in batch size and sampling
- **THEN** the two runs differ in how many paragraphs one call carries and in the sampling values sent, and in nothing else of the request

## MODIFIED Requirements

### Requirement: Send each call kind of the three stages at its own temperature

WHERE a model's temperature choice is Let BookLoom choose, the system SHALL send each call at the temperature of its kind: draft `0.2` (`0.1` for a desk retry that asks for a lower one), repair `0.1` with a seed, proofread `0.1`, WHERE the merge at Max is built the two extra drafts of a merge `0.2` and `0.4`, each with a new seed, and the merge call `0.2`, and every names-pass, brief and file-name call `0.2`. WHERE it is My value, every request SHALL carry that value; WHERE it is Don't send, none SHALL.

**Source**: `research/review-digest.md#merge-results-on-gemma426b`, `design.md#d22--the-merge-at-max-is-an-experiment-first`. In plain words: every call stays low and near-deterministic for fidelity; a repair and a proofread, which must change only what is wrong, are the steadiest, and a repair gets a new seed so it does not repeat the failed draft word for word. Where the merge at Max is built (it must first pass the gate stated in the design anchor), the two extra drafts of a merge get new seeds for the same reason: three copies of one text give the merge nothing to choose from, and the review found drafts written hot (0.8) merge worse than low ones. A person can instead fix one temperature for a model, or send none for models that refuse the setting.

#### Scenario: A repair is steadier than a draft

- **WHEN** a Balanced run drafts `ch07.xhtml:41` and then repairs it through the provider `lmstudio` with the choice Let BookLoom choose
- **THEN** the draft request body has `"temperature":0.2` and the repair request body has `"temperature":0.1` and a `seed`

#### Scenario: A lower-temperature retry

- **WHEN** the person retries the segment `ch05.xhtml:11` in review and asks for a lower temperature
- **THEN** the retry's draft request carries the temperature `0.1`
- **AND** a retry of the same segment without that request carries `0.2`

#### Scenario: A proofread call stays low and reasoning is off

- **WHEN** a proofread call for `gemma4:e4b-mlx` is sent to the provider `ollama` with the choice Let BookLoom choose and no thinking choice for the model
- **THEN** its body's `options` holds `"temperature":0.1` and the body has `"think":false`

#### Scenario: My value is sent on every request

- **WHEN** the model's choice is My value `0.5` and a draft and a repair are made
- **THEN** both request bodies carry `"temperature":0.5`

#### Scenario: Don't send leaves the temperature out

- **WHEN** the model's choice is Don't send and a draft call is made
- **THEN** the request body has no `temperature` key

#### Scenario: The extra drafts of a merge differ by seed and temperature

- **WHEN** the merge at Max is built and a Max run on `gemma4:26b` merges the marked paragraph `ch07.xhtml:41` with the choice Let BookLoom choose
- **THEN** the two extra draft requests carry `"temperature":0.2` and `"temperature":0.4`, each with a `seed` that differs from the first draft's and from the other's, and the merge request carries `"temperature":0.2`

### Requirement: The pseudo model answers every call kind with the text in capitals

WHEN the pseudo model receives messages, the system SHALL upper-case the text inside the last user message's `<Text>…</Text>` block, or the whole last user message when it has none, and SHALL reply with that text, or, WHEN the request carries a response format, with a well-formed answer of that format's shape built from it; it SHALL keep every `⟦gN⟧` token and character reference, ignore the machine's locale, report a normal finish and no usage, and never fail or open a connection.

**Source**: `research/review-digest.md#f13--about-40--of-the-run-time-buys-nothing-in-the-exported-book`, `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d11--a-proofread-reply-is-a-list-of-edits-each-judged-alone`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: a book translated by the pseudo model is easy to recognise: it shows the original text in capitals, with its formatting intact. It answers every call kind of the three stages in its expected shape, so a whole run — Prepare, Translate, Proofread and export — works without any server. A draft or a repair answers `{"items":[{"id":"<id>","target":"<text>"}]}` with one item per `<s id="…">` of the last `<Items>` block (one item for a single segment); a format with a single `target` answers `{"target":"<text>"}`; a proofread call answers `{"edits":[]}`; the pre-scan answers the capitalised words as candidates; a name-knowledge call answers no items. Its drafts are the source in capitals, which the quality checks rightly call an untranslated echo for a long segment.

#### Scenario: Letters are capitalised, tokens and references are kept

- **WHEN** the last user message is `Tom ⟦g0⟧ran⟦g1⟧ &amp; hid&nbsp;&#160;&#xA0;⟦g12⟧`
- **THEN** the reply is `TOM ⟦g0⟧RAN⟦g1⟧ &amp; HID&nbsp;&#160;&#xA0;⟦g12⟧` with a normal finish

#### Scenario: The machine's locale does not change the result

- **WHEN** the default locale is Turkish (`tr`) and the last user message is `title`
- **THEN** the reply is `TITLE`

#### Scenario: Only the last user message is answered

- **WHEN** the messages are system `Translate into uk`, user `one`, assistant `ONE` and user `two`
- **THEN** the reply is `TWO`

#### Scenario: An empty message gives an empty reply

- **WHEN** the last user message is empty
- **THEN** the reply is empty, with a normal finish

#### Scenario: A batch draft gets one item per segment

- **WHEN** a request with the translate response format has a last `<Items>` block holding `<s id="ch01.xhtml:3">He opened the ⟦g0⟧old⟦g1⟧ door.</s>` and `<s id="ch01.xhtml:4">Mara Quill waited.</s>`
- **THEN** the reply is `{"items":[{"id":"ch01.xhtml:3","target":"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR."},{"id":"ch01.xhtml:4","target":"MARA QUILL WAITED."}]}` and carries no usage

#### Scenario: A proofread call changes nothing

- **WHEN** a request with the proofread response format is sent to the pseudo model
- **THEN** the reply is `{"edits":[]}`

#### Scenario: A chapter summary is the text in capitals

- **WHEN** a request with no response format has a `<Text>` block holding `Velemir crossed the pass.`
- **THEN** the reply is `VELEMIR CROSSED THE PASS.`, and the pseudo model knows no chapter-summary response format, because no chapter summary is written

### Requirement: Remember a refused setting for the provider and model

WHEN a setting is refused and dropped, the system SHALL remember the refusal for that provider and model across restarts — for the whole model, or for one call kind when it concerns that kind's answer schema — and SHALL leave the setting out of the first attempt of every later call it covers until the person chooses Send again; a refused system role SHALL be remembered as a fold.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: a model that rejects `temperature` once will reject it every time; remembering saves a failed request at the start of every later call, and the person can undo it from Settings › Models. A schema one call kind cannot use says nothing about the others, so that refusal keeps its scope. The example of another call kind is now a proofread call, because chapter-summary calls are gone.

#### Scenario: A later call leaves the setting out from the start

- **WHEN** `gpt-oss:20b` on the provider `office` refused `temperature` and the next draft call to `gpt-oss:20b` is made
- **THEN** its first request carries no `temperature` key and the server receives 1 request for it

#### Scenario: A refusal is remembered after a restart

- **WHEN** `gpt-oss:20b` on the provider `office` refused `temperature` and the application is restarted
- **THEN** the first request to `gpt-oss:20b` after the restart carries no temperature and is not rejected

#### Scenario: A refusal belongs to one model

- **WHEN** `gpt-oss:20b` refused `temperature` and a request goes to `gemma4:e4b-mlx` on the same provider
- **THEN** the request to `gemma4:e4b-mlx` still carries its temperature

#### Scenario: A schema refusal belongs to one call kind

- **WHEN** `google/gemma-4-e4b` refused the draft's answer schema and a proofread call is then made to it
- **THEN** the proofread request still carries its `response_format`

#### Scenario: A save on the Models tab keeps a refusal recorded meanwhile

- **WHEN** the Models tab is open on `gpt-oss:20b`, the chat layer records a refusal of `temperature` and the person then saves a changed context window
- **THEN** the stored settings hold the new context window and still hold the refusal of `temperature`

### Requirement: Use one context size for every call to a model

The system SHALL size every call to a model from that model's one context window — the person's window for that model, else the smallest of the detected or loaded window, the profile's hard cap and 16,384 — whatever the call kind, before, during or after a run; it SHALL send the size only to Ollama's native API, as `num_ctx`, never to an OpenAI-compatible server, and SHALL never stamp a fixed size on one kind of call.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: today the names pass, the brief and the file-name call always send 16,384, so a model loaded with 8k gets a prompt planned for twice its room, and on GGUF a size that changes between calls reloads the model. On LM Studio, llama-server and the other OpenAI-compatible servers the window is fixed when the model is loaded, so they are planned for but never sent a size (the llm-provider capability's "Never send a context size to an OpenAI-compatible server" says the same). How a run fits its prompts and replies inside this window is the translation-pipeline capability's "Size a run's calls from the detected window"; this is the one place the size itself is decided. Chapter-summary calls are gone, so the list of calls names none.

#### Scenario: A preparation call follows the detected window

- **WHEN** Ollama reports a context length of 8192 for `gemma4:e4b-mlx` and the names pass sends a prescan call
- **THEN** its body has `"num_ctx":8192`, not 16384

#### Scenario: The smallest limit wins

- **WHEN** `qwen2.5:14b` reports 131072, its profile's hard cap is 32768 and the person set no window
- **THEN** every call carries `"num_ctx":16384`

#### Scenario: The person's window is used everywhere

- **WHEN** the person sets the window 12288 for `gemma4:26b-mlx`
- **THEN** a draft, a proofread call and a Suggest a name call all carry `"num_ctx":12288`

#### Scenario: An OpenAI-compatible server is planned for but sent no size

- **WHEN** LM Studio reports `google/gemma-4-e4b` loaded with 8192 and a draft is sent to it
- **THEN** the draft's prompt and cap are planned for 8192, and its body has no `num_ctx`, `n_ctx` or `context_length` key

### Requirement: Step a call kind's structured answers down one rung at a time

The system SHALL ask for structured answers per model and call kind on one of two rungs — with the answer schema, or with the answer contract stated only in the prompt — and SHALL move one call kind down when its schema fails (refused, unusable, or not applied twice in a row), every call kind of the model down when the failure is model-wide, and remember the step as a refused setting with that scope.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `design.md#d13--chapter-summaries-are-removed-with-their-switch-and-the-story-shape`. In plain words: a server may fail one schema and handle the others; dropping the schema for every call because one failed throws away the help it gives. Every reply is checked by code the same way on both rungs, and `json_object` is never a rung. The example of a call kind that keeps its rung is now a proofread call, because chapter-summary calls are gone.

#### Scenario: One call kind steps down

- **WHEN** LM Studio answers a draft `400 {"error":"Failed to initialize samplers: failed to parse grammar"}` for `google/gemma-4-e4b`
- **THEN** later drafts to that model carry no `response_format`, and its proofread calls still carry theirs

#### Scenario: A model-wide failure steps every kind down

- **WHEN** a server answers `501` with `structured output is unavailable` for `google/gemma-4-26b-a4b-qat`
- **THEN** every later call to that model carries no `response_format`

#### Scenario: A model whose profile starts in the prompt

- **WHEN** the first draft for `lapa-v0.1.2-instruct`, whose profile marks a prompt-only start, is sent
- **THEN** it carries no `response_format`
