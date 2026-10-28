## ADDED Requirements

### Requirement: Choose the reasoning setting per call kind from the model profile

The system SHALL decide each call's reasoning from the model's profile — off for every call kind, or the lowest level for a model that cannot turn thinking off — unless the person's thinking choice for that model on Settings › Models says to allow thinking on proofreading and names (then on for the proofread and names-pass calls only) or always (then on for every call); no environment variable and no app-wide setting SHALL change it.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, the owner's decision that thinking and sampling are a built-in default per model plus the person's per-model override, with Settings › Generation keeping only app-wide items. In plain words: thinking costs time and can leave the answer empty, so it stays off unless the person turns it on for a model that translates better with it. The evaluation scripts set it through their own harness; the `BOOKLOOM_REASONING` variable is no longer read by the app.

#### Scenario: The default is off

- **WHEN** nothing is chosen for `qwen3.5:9b` and a draft is sent to Ollama
- **THEN** the draft carries reasoning Off, sent as `"think":false`

#### Scenario: Thinking allowed on proofreading and names

- **WHEN** the person allows thinking on proofreading and names for `qwen3.5:9b`, and a draft and a proofread call are sent
- **THEN** the draft carries `"think":false` and the proofread call `"think":true`

#### Scenario: The environment variable is ignored

- **WHEN** `BOOKLOOM_REASONING` is set to turn reasoning on and nothing is chosen for `gemma4:e4b-mlx`
- **THEN** the draft carries `"think":false`

### Requirement: Send reasoning Off as the engine's own off value

WHERE a model declares a thinking control and a call's reasoning is Off, the system SHALL send the off value its profile names, on every engine, else the engine's own: Ollama native `"think":false` (else the lowest listed level); Ollama's OpenAI path, LM Studio and a generic server proved by its check `"reasoning_effort":"none"`; llama-server `chat_template_kwargs.enable_thinking` as JSON `false`; Unsloth `"enable_thinking":false`; OpenRouter its catalog's `reasoning` object.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the same wish is spelled differently by every engine, and a missing switch is not neutral — llama-server thinks by default, and Gemma 4 on LM Studio reasons when the field is left out. A profile's own off value comes first on every engine: gpt-oss always gets `low` (on llama-server `none` would erase the switch and fall back to medium), and muse-glimmer gets `low` whenever a schema is sent. A model with no thinking control gets nothing, by the next requirement.

#### Scenario: llama-server gets the template switch as a boolean

- **WHEN** a draft for `Qwen3.5-9B-Q4_K_M.gguf` goes to llama-server whose template reads `enable_thinking`
- **THEN** the body has `"chat_template_kwargs":{"enable_thinking":false}`, with `false` a JSON boolean, and no `reasoning_effort` inside `chat_template_kwargs`

#### Scenario: gpt-oss on llama-server

- **WHEN** a draft for `gpt-oss-20b` goes to llama-server
- **THEN** the body has `"reasoning_effort":"low"` and no `chat_template_kwargs`

#### Scenario: gpt-oss on Ollama

- **WHEN** a draft for `gpt-oss:20b`, whose levels are `low`, `medium` and `high`, goes to Ollama's native API
- **THEN** the body has `"think":"low"`

#### Scenario: muse-glimmer with a schema

- **WHEN** a draft with a JSON schema for `muse-glimmer:30b-nvfp4-dflash` goes to Ollama's native API
- **THEN** the body has `"think":"low"`, not `false`

#### Scenario: LM Studio and Ollama's OpenAI path

- **WHEN** a draft for `google/gemma-4-e4b` goes to LM Studio, and one for `qwen3.5:9b` to an Ollama provider kept as OpenAI-compatible
- **THEN** both bodies have `"reasoning_effort":"none"`

#### Scenario: Unsloth

- **WHEN** a draft goes to an Unsloth provider for `unsloth/Qwen3.5-9B-GGUF`
- **THEN** the body has a top-level `"enable_thinking":false`

#### Scenario: OpenRouter's catalog allows none

- **WHEN** OpenRouter's catalog lists the effort `none` for the model and a draft is sent
- **THEN** the body has `"reasoning":{"effort":"none"}`

### Requirement: Send no thinking control that the model or the engine does not declare

The system SHALL send no reasoning control — no `think`, `reasoning_effort`, `enable_thinking`, template variable or `reasoning` object — that the model's facts or the stored engine do not declare, including a llama-server template variable the model's template does not read.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a control a model does not have is refused with a `400` that used to pause runs, and a template variable the template ignores does nothing at all. Preventing it is better than repairing it.

#### Scenario: A model without thinking on Ollama

- **WHEN** a draft for `qwen2.5:1.5b`, whose capabilities lack `thinking`, is sent to Ollama's native API
- **THEN** the body has no `think` key

#### Scenario: A downloaded GGUF without thinking

- **WHEN** a draft for `hf.co/bartowski/utter-project_EuroLLM-22B-Instruct-2512-GGUF:IQ4_NL`, whose capabilities are `["completion"]`, is sent to Ollama
- **THEN** the body has no `think` key

#### Scenario: A template that reads another variable

- **WHEN** a draft for a Hermes 4 GGUF goes to llama-server whose template reads `thinking` and not `enable_thinking`
- **THEN** the body has no `chat_template_kwargs` key

### Requirement: Give a model that always thinks room to think

WHEN a model's facts or profile show it thinks on every call, the system SHALL raise each call's output cap by the profile's reasoning reserve and send its lowest offered level, or no reasoning control when it offers none, and SHALL remove the reasoning from the reply.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: some models cannot stop thinking — gpt-oss, a reasoning build, or a downloaded GGUF that Ollama serves without its own thinking switch. With the usual cap they would spend the whole answer thinking and return nothing, so they get extra room; the reply is slower but complete.

#### Scenario: A reasoning-only model on LM Studio

- **WHEN** a draft capped at `300` is sent for `mistralai/ministral-3-14b-reasoning`, whose LM Studio reasoning options are `["on"]`
- **THEN** the body has no `reasoning_effort` key and a `max_tokens` above `300` by the profile's reserve

#### Scenario: A downloaded build Ollama cannot switch off

- **WHEN** Ollama's `/api/show` for `hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M` shows no `renderer` and capabilities without `thinking`, and a draft capped at `300` is sent
- **THEN** the body has no `think` key and its `num_predict` is at least 2,048

### Requirement: Ask once more when a reply spent its whole answer thinking

IF a capped reply has blank text, reasoning present (in a reasoning field or as an opener with no closer) and the finish `length`, THEN the system SHALL send the request once more — with reasoning off when it was on and the model can turn it off, otherwise with its cap raised to four times, within the context size — and IF that reply is the same, THEN answer `emptyCompletion` with the reason `reasoning-exhausted`.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `#empty-response-ordering`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a model that used its whole answer budget thinking left no translation; one more try with no thinking, or with more room, usually gives one. Both of Ollama's dialects and every OpenAI-compatible engine follow the same rule (today Ollama flags at once because it ignores the thinking field). After the second empty reply the segment is flagged with "The model spent its whole answer budget thinking."

#### Scenario: Thinking that was on is turned off for the repeat

- **WHEN** the person chose Always for `qwen3.5:9b`, a draft capped at `128` is answered by Ollama with empty `message.content`, non-empty `message.thinking` and `"done_reason":"length"`
- **THEN** the same request is sent once more with `"think":false`, and its reply is the call's answer

#### Scenario: Thinking already off gets more room

- **WHEN** a draft to LM Studio with `"reasoning_effort":"none"`, capped at `128`, with the context size `8192`, is answered `{"choices":[{"message":{"content":"","reasoning_content":"Thinking Process: …"},"finish_reason":"length"}]}`
- **THEN** the same request is sent once more with `"max_tokens":512`

#### Scenario: An unclosed opener counts as reasoning

- **WHEN** a capped reply's text is `<think>first I will` with the finish `length`
- **THEN** the request is sent once more

#### Scenario: Twice empty

- **WHEN** the repeated request is also answered with blank text, reasoning and the finish `length`
- **THEN** the call answers `ErrorCode.emptyCompletion` with reason `reasoning-exhausted`

### Requirement: Choose sampling from the model profile

The system SHALL take each call's samplers from the model's profile, or from the person's Custom values for that model on Settings › Models, and WHERE an engine's own default for a sampler is not neutral (Ollama's OpenAI-compatible path, LM Studio, llama-server, Unsloth), SHALL send that sampler explicitly, at the profile's or the person's value or else a neutral one, under that engine's field name.

**Source**: `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#generation-parameters`, `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, the owner's decision that sampling has a built-in default per model and a per-model override. In plain words: llama-server's own defaults include a temperature of 0.8 and `min_p` 0.05, LM Studio adds a repeat penalty of 1.1, and Ollama's OpenAI path puts in its own `top_p`; a sampler left out silently takes those values. There is no "server's own sampling" choice, because on those engines it would bring those values back. Which numbers each model's profile holds is profile data measured by the project's paired tests, not a rule here; the scenarios say "the profile's value". The temperature per call kind is a separate requirement.

#### Scenario: llama-server gets every sampler

- **WHEN** a draft goes to llama-server for a model whose profile names `top_k`, `top_p` and no `min_p`
- **THEN** the body has `temperature`, the profile's `top_p` and `top_k`, `"min_p":0` and `"repeat_penalty":1.0`

#### Scenario: Unsloth's own field name

- **WHEN** a draft goes to an Unsloth provider for a model whose profile names `top_k` and `top_p`
- **THEN** the body has `temperature`, `top_p`, `top_k`, `min_p` and `repetition_penalty`, and no `repeat_penalty`

#### Scenario: Ollama's OpenAI path takes two samplers

- **WHEN** a draft for a model whose profile names `top_p` and `top_k` goes to an Ollama provider kept as OpenAI-compatible
- **THEN** the body has `temperature` and the profile's `top_p`, and no `top_k`, `min_p` or `repeat_penalty`

#### Scenario: A profile with no samplers on LM Studio gets neutral values

- **WHEN** a draft for `google/gemma-4-e4b`, whose profile names no sampler and whose sampling choice is Profile default, goes to LM Studio
- **THEN** the body has `temperature`, `"min_p":0` and `"repeat_penalty":1.0`

#### Scenario: The person's Custom values are sent

- **WHEN** the person chooses Custom for `gemma4:26b-mlx` with top_k `40`, top_p `0.9` and min_p left empty, and a draft is sent to Ollama's native API
- **THEN** the body's `options` holds the temperature, `"top_k":40` and `"top_p":0.9`, and no `min_p`

### Requirement: Use one context size for every call to a model

The system SHALL size every call to a model from that model's one context window — the person's window for that model, else the smallest of the detected or loaded window, the profile's hard cap and 16,384 — whatever the call kind, before, during or after a run; it SHALL send the size only to Ollama's native API, as `num_ctx`, never to an OpenAI-compatible server, and SHALL never stamp a fixed size on one kind of call.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#effective-context`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#effective-context`, `design.md#d8--one-context-builder-block-plan-window-and-budgets`, the owner's decision that the default window is 16k. In plain words: today the names pass, the brief and the file-name call always send 16,384, so a model loaded with 8k gets a prompt planned for twice its room, and on GGUF a size that changes between calls reloads the model. On LM Studio, llama-server and the other OpenAI-compatible servers the window is fixed when the model is loaded, so they are planned for but never sent a size (the llm-provider capability's "Never send a context size to an OpenAI-compatible server" says the same). How a run fits its prompts and replies inside this window is the translation-pipeline capability's "Size a run's calls from the detected window"; this is the one place the size itself is decided.

#### Scenario: A preparation call follows the detected window

- **WHEN** Ollama reports a context length of 8192 for `gemma4:e4b-mlx` and the names pass sends a prescan call
- **THEN** its body has `"num_ctx":8192`, not 16384

#### Scenario: The smallest limit wins

- **WHEN** `qwen2.5:14b` reports 131072, its profile's hard cap is 32768 and the person set no window
- **THEN** every call carries `"num_ctx":16384`

#### Scenario: The person's window is used everywhere

- **WHEN** the person sets the window 12288 for `gemma4:26b-mlx`
- **THEN** a draft, a proofread call, a chapter-summary call and a Suggest a name call all carry `"num_ctx":12288`

#### Scenario: An OpenAI-compatible server is planned for but sent no size

- **WHEN** LM Studio reports `google/gemma-4-e4b` loaded with 8192 and a draft is sent to it
- **THEN** the draft's prompt and cap are planned for 8192, and its body has no `num_ctx`, `n_ctx` or `context_length` key

### Requirement: Keep the model loaded while a run calls it

WHILE a translation run calls a model on Ollama's native API, the system SHALL send each of the run's calls with a keep-alive that holds the model loaded between calls, and SHALL send none on an interactive call or a call made before the run starts.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: Ollama unloads an idle model after a few minutes, and a run that pauses for a long proofread window would reload it for nothing; outside a run the person's own Ollama settings decide. LM Studio and llama-server keep a model loaded by their own settings, so they get no such field.

#### Scenario: A run's draft keeps the model loaded

- **WHEN** a run sends a draft for `gemma4:e4b-mlx` to Ollama
- **THEN** the body has `"keep_alive":"30m"`, a string with its unit

#### Scenario: An interactive call does not

- **WHEN** the person runs the inference test for `gemma4:e4b-mlx` in Settings › Providers
- **THEN** the body has no `keep_alive` key

### Requirement: Step a call kind's structured answers down one rung at a time

The system SHALL ask for structured answers per model and call kind on one of two rungs — with the answer schema, or with the answer contract stated only in the prompt — and SHALL move one call kind down when its schema fails (refused, unusable, or not applied twice in a row), every call kind of the model down when the failure is model-wide, and remember the step as a refused setting with that scope.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a server may fail one schema and handle the others; dropping the schema for every call because one failed throws away the help it gives. Every reply is checked by code the same way on both rungs, and `json_object` is never a rung.

#### Scenario: One call kind steps down

- **WHEN** LM Studio answers a draft `400 {"error":"Failed to initialize samplers: failed to parse grammar"}` for `google/gemma-4-e4b`
- **THEN** later drafts to that model carry no `response_format`, and its chapter-summary calls still carry theirs

#### Scenario: A model-wide failure steps every kind down

- **WHEN** a server answers `501` with `structured output is unavailable` for `google/gemma-4-26b-a4b-qat`
- **THEN** every later call to that model carries no `response_format`

#### Scenario: A model whose profile starts in the prompt

- **WHEN** the first draft for `lapa-v0.1.2-instruct`, whose profile marks a prompt-only start, is sent
- **THEN** it carries no `response_format`

### Requirement: Send no answer schema while a call thinks

WHILE a call has reasoning on, the system SHALL send it with the answer contract in the prompt only, with no answer schema.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: with a schema enforced from the first token, some models must write their thinking inside the answer's grammar and lose the answer; when thinking is on, the code checks the reply instead.

#### Scenario: A proofread call with thinking allowed

- **WHEN** the person allows thinking on proofreading for `gemma4:e4b-mlx` and a proofread call is sent to Ollama
- **THEN** the body has `"think":true` and no `format` key

### Requirement: Always send BookLoom's own system message

The system SHALL send its own system text on every chat call, including the verification probe, so that no default system prompt baked into the model or its template is used.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: some model downloads carry their maker's chat persona — a long "you are a chat assistant, ask the user to clarify" text — which a translation call must not inherit; BookLoom's own system text replaces it.

#### Scenario: A model with a baked chat persona

- **WHEN** Ollama's `/api/show` for `hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL` holds a 2 KB `system` text and the inference probe is sent
- **THEN** the probe's messages start with BookLoom's own system text

### Requirement: Fold the system text into the first user message for a model without a system role

WHERE a model's profile marks no system role (for example Gemma 2, Gemma 3 and their tunes), or the model refused one, the system SHALL send the system text as the start of the first user message, followed by a blank line and the user text, and SHALL never send two user messages in a row.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: these templates refuse a system message or render it as a second user turn, which confuses the model; one user message that starts with the instructions works on every server. A template that refuses the system role with "System role not supported" is resent folded at no attempt cost, and the fold is remembered for that model.

#### Scenario: Gemma 3 gets one user message

- **WHEN** a draft for `gemma3:12b` with a system text and a user text is sent to Ollama
- **THEN** the body's `messages` holds exactly one message, of role `user`, whose content starts with the system text

#### Scenario: A refused system role is remembered as a fold

- **WHEN** LM Studio answers a draft for `velka-chat-9b`, a model with no row whose profile gives it a system role, `400` with `System role not supported`
- **THEN** the draft is sent once more folded, the repeat is not counted as an attempt, and later calls to that model are folded from the start

### Requirement: Merge the turns once when a template wants alternating roles

IF a chat template refuses a request because its roles do not alternate, THEN the system SHALL send it once more with consecutive messages of the same role merged into one, and, IF it is refused again, THEN SHALL answer `ErrorCode.validation` with the reason `template-rejected`.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: some templates insist that user and assistant take turns; merging two neighbouring messages keeps their content and satisfies the template.

#### Scenario: Ministral's template wants alternation

- **WHEN** LM Studio answers a request for `mistralai/ministral-3-14b-reasoning` with `500` naming a Jinja exception that roles must alternate
- **THEN** the request is sent once more with no two messages of the same role in a row

### Requirement: Escape thinking switches in book text for a model whose template reads them

WHERE a model's profile marks that its template switches thinking on the words `/think` and `/no_think` in the text, the system SHALL insert a word joiner (U+2060) after the slash of each such word in the text it sends, and SHALL remove it from the reply.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: older Nemotron templates turn thinking on or off when these words appear anywhere in a message, so a book that mentions them would flip the model's mode; the invisible joiner keeps the words as text.

#### Scenario: A book line that names the switch

- **WHEN** a draft for `nemotron-3-nano:4b` carries the source `Type /no_think to stop.`
- **THEN** the sent text has a U+2060 between `/` and `no_think`, and the switch is not triggered

### Requirement: Measure time to first token and prompt reading for each engine

WHEN a model call ends, the system SHALL record its time to first token and its prompt-reading time where the engine reports them — Ollama from its first streamed line with text or thinking and its prompt duration, LM Studio from its reply's `stats`, llama-server from its reply's `timings` — and SHALL record them as missing, never as 0, where the engine reports neither.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: how long a model takes to start answering, and how long it spends reading the prompt, show whether a slow run is the model or the prompt. Ollama's OpenAI path, OpenRouter and generic servers do not say, and a 0 would read as instant.

#### Scenario: Ollama's first line is timed

- **WHEN** Ollama's first streamed line with text or thinking arrives 0.42 s after the request and its last line reports `"prompt_eval_duration":310000000`
- **THEN** the call's time to first token is 0.42 s and its prompt reading 0.31 s

#### Scenario: LM Studio's stats are read

- **WHEN** LM Studio's reply holds `"stats":{"time_to_first_token":0.169,"tokens_per_second":41.2,"generation_time":1.9}`
- **THEN** the call's time to first token is 0.169 s

#### Scenario: llama-server's timings are read

- **WHEN** llama-server's reply holds `"timings":{"prompt_ms":812.5,"predicted_per_second":38.4,"cache_n":1840}`
- **THEN** the call's prompt reading is 0.8125 s and its cached prompt tokens 1840

#### Scenario: A figure the engine does not give stays missing

- **WHEN** an OpenRouter call ends
- **THEN** its time to first token is missing and the run summary shows `–` for it, not `0`

### Requirement: The pseudo model answers every call kind with the text in capitals

WHEN the pseudo model receives messages, the system SHALL upper-case the text inside the last user message's `<Text>…</Text>` block, or the whole last user message when it has none, and SHALL reply with that text, or, WHEN the request carries a response format, with a well-formed answer of that format's shape built from it; it SHALL keep every `⟦gN⟧` token and character reference, ignore the machine's locale, report a normal finish and no usage, and never fail or open a connection.

**Source**: FR-INFER-01, the offline pseudo model (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#offline-invariant`, `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`. In plain words: a book translated by the pseudo model is easy to recognise: it shows the original text in capitals, with its formatting intact. It answers every call kind of the three stages in its expected shape, so a whole run — Prepare, Translate, Proofread and export — works without any server. A draft or a repair answers `{"items":[{"id":"<id>","target":"<text>"}]}` with one item per `<s id="…">` of the last `<Items>` block (one item for a single segment); a format with a single `target` answers `{"target":"<text>"}`; a proofread call answers `{"unchanged":true}`; a chapter summary answers `{"summary":"<text>"}`; the pre-scan answers the capitalised words as candidates; a name-knowledge call answers no items. Its drafts are the source in capitals, which the quality checks rightly call an untranslated echo for a long segment.

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
- **THEN** the reply is `{"unchanged":true}`

#### Scenario: A chapter summary is the text in capitals

- **WHEN** a request with the chapter-summary response format has a `<Text>` block holding `Velemir crossed the pass.`
- **THEN** the reply is `{"summary":"VELEMIR CROSSED THE PASS."}`

### Requirement: Send each call kind of the three stages at its own temperature

WHERE a model's temperature choice is Let BookLoom choose, the system SHALL send each call at the temperature of its kind: draft `0.2` (`0.1` for a desk retry that asks for a lower one), repair `0.1` with a seed, proofread `0.1`, and every names-pass, brief, chapter-summary and file-name call `0.2`. WHERE it is My value, every request SHALL carry that value; WHERE it is Don't send, none SHALL.

**Source**: FR-INFER-03, call settings (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#generation-parameters`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#generation-parameters`, `design.md#d13--one-repair-the-outcome-table-and-fasts-lost-marks-retry`. In plain words: every call stays low and near-deterministic for fidelity; a repair and a proofread, which must change only what is wrong, are the steadiest, and a repair gets a new seed so it does not repeat the failed draft word for word. A person can instead fix one temperature for a model, or send none for models that refuse the setting. The other samplers and the reasoning setting are separate requirements.

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

### Requirement: Step a call kind to the prompt-only rung when its schema cuts a string at a quote mark

WHERE a model's profile marks that its engine's answer schema can cut a string at a straight quote mark, IF a reply under the schema holds a target that stops where its source has a straight quote mark `"` while the source goes on, THEN the system SHALL send that call once more on the prompt-only rung and SHALL keep that call kind on it for the model as a refused setting.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: with Gemma 3 on Ollama, the schema's grammar can end a string at the first straight quote mark, so a sentence with dialogue comes back cut short and still reads as valid JSON. The profile says which models do this, and the call is asked again with the contract in the prompt, where the quote mark is just text.

#### Scenario: A cut target moves the draft to the prompt-only rung

- **WHEN** a draft for `gemma3:12b` on Ollama with a schema has the source `She said "go home" and left.` and the reply is `{"items":[{"id":"ch02.xhtml:7","target":"Вона сказала "}]}`
- **THEN** the draft is sent once more with no `format` key, and later drafts to `gemma3:12b` carry no `format` key either

#### Scenario: A whole target is kept

- **WHEN** the same draft is answered `{"items":[{"id":"ch02.xhtml:7","target":"Вона сказала «йди додому» і пішла."}]}`
- **THEN** the reply is used and the call kind stays on the schema rung

### Requirement: Keep reasoning-level wording out of the system text of a model whose template writes its own

WHERE a model's profile marks that its chat template writes the reasoning level into the system text itself, the system SHALL build that model's system text with no line that names a reasoning level, strength or effort.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: muse-glimmer's template adds a "Reasoning strength" line from the level the engine was sent, but only when the system text has none, and it rewrites other reasoning wording it finds there; a line of BookLoom's own would replace the `low` that was sent. Which models do this is profile data.

#### Scenario: muse-glimmer's system text names no level

- **WHEN** a draft with a schema for `muse-glimmer:30b-nvfp4-dflash` is sent to Ollama's native API
- **THEN** the body has `"think":"low"` and its system text holds no line with `Reasoning strength` or `Reasoning effort`

### Requirement: Never write a reasoning line into the instructions of a harmony-format model

WHERE a model's profile marks the harmony chat format (gpt-oss), the system SHALL send its reasoning level only through the engine's reasoning field and SHALL never write a line that starts with `Reasoning:` into any message.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: gpt-oss's template turns the system text into its developer instructions and reads a `Reasoning: high` line there as an order; a line written by BookLoom or copied from a guide would override the `low` the engine was sent.

#### Scenario: A gpt-oss draft carries its level only in the field

- **WHEN** a draft for `gpt-oss:20b` is sent to Ollama's native API
- **THEN** the body has `"think":"low"` and no message holds a line starting with `Reasoning:`

### Requirement: Write the model profile chosen for a run to the run log

WHEN a run starts or continues, the system SHALL write to the run log, at INFO, one line naming the model's resolved profile (match, quant, reasoning and its value, sampling source, window, batch size) and one line naming the server facts read (server and version, engine, runner, template family), as counts and choices only, never a character name or any book text.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-profile`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the run log is the one file kept in an installed app; with these two lines a wrong translation can be traced to how the model was handled. This is the one place the exact text of these lines is fixed; other capabilities that mention them say "such as" and do not repeat it. The model id is a choice the person made, not book text, so it is written.

#### Scenario: A run's start lines

- **WHEN** a run starts with `gemma4:e4b-mlx` on Ollama 0.40.11
- **THEN** the run log holds `profile resolved model=gemma4:e4b-mlx match=EXACT quant=nvfp4 reasoning=OFF(false) sampling=profile window=16384 batch=4` and `facts read server=ollama 0.40.11 engine=OLLAMA runner=mlx renderer=gemma4-small family=gemma4`

## MODIFIED Requirements

### Requirement: Get a chat model by provider id and model id

WHEN a caller asks for a chat model by provider id and model id, the system SHALL return a model that answers an ordered list of system, user and assistant messages with reply text and a finish (normal, cut off by length, or other), sending each call in its provider's dialect through the single-flight gate and the retry policy, resolving the model's profile at each call and scrubbing every reply; `pseudo` SHALL give the offline pseudo model, and creating a model SHALL open no connection.

**Source**: FR-INFER-01, one chat contract for every provider (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), FR-PROV-03 (`#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#chat-contracts`, ADR-0033 (provider-neutral call settings, amended by this change), `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the caller that starts a translation picks the provider and the model once, and the translation engine only talks to the model it is handed. The profile is looked up at every call, so a thinking or sampling choice the person saves between two calls applies to the next one; the model's facts are kept for the session and never stored. IF the provider id is unknown or the model id is blank, THEN the answer is `ErrorCode.validation` and no model.

#### Scenario: The pseudo provider gives a working model

- **WHEN** a chat model is requested for provider `pseudo` and model `uppercase`
- **THEN** a model is returned
- **AND** when sent the single user message `hello`, it replies `HELLO` with a normal finish

#### Scenario: The Ollama preset gives a model that speaks the native dialect

- **WHEN** a chat model is requested for provider `ollama` and model `gemma4:e4b-mlx`
- **THEN** a model is returned without any request being sent
- **AND** its first call posts to `http://localhost:11434/api/chat` with `"model":"gemma4:e4b-mlx"`

#### Scenario: The LM Studio preset gives a model that speaks the OpenAI dialect

- **WHEN** a chat model is requested for provider `lmstudio` and model `google/gemma-4-e4b`
- **THEN** its first call posts the OpenAI-shaped body with `"model":"google/gemma-4-e4b"` to LM Studio's chat route

#### Scenario: A choice saved between two calls applies to the next

- **WHEN** a model for `ollama` and `qwen3.5:9b` has sent one draft with `"think":false`, and the person then chooses Always for `qwen3.5:9b`
- **THEN** the next draft from the same model carries `"think":true`

#### Scenario: An unknown provider is refused

- **WHEN** a chat model is requested for provider `gemini` and model `gemini-2.5-flash`
- **THEN** the result is `ErrorCode.validation` and no model is returned

#### Scenario: A blank model id is refused

- **WHEN** a chat model is requested for provider `ollama` with an empty model id
- **THEN** the result is `ErrorCode.validation`

### Requirement: Carry a temperature and a response format per call

The system SHALL let a request carry, besides its messages, its call kind, a reasoning intent, an optional temperature, an optional response format (a name and a JSON schema), an optional context size, an optional expected output and an optional output cap; a request built from messages alone SHALL carry no call kind, the intent Off and none of the optional settings, an absent setting SHALL be left out of what is sent, and the pseudo model SHALL ignore everything but the format.

**Source**: FR-INFER-03, FR-INFER-09, call settings (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `#effective-context`, `#service-owned-retry`, ADR-0033 (provider-neutral call settings, amended by this change), `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the engine says what kind of call it is and what it wants (a shape, a size, a length, thinking or not) without knowing which server answers; the model's profile then decides the samplers, the context size and the engine's fields at send time. A setting that is not given is simply not sent, so a strict server never sees a `null`. The verification probe carries no call kind, so it is left out of the per-kind figures.

#### Scenario: A request built from messages alone carries no settings

- **WHEN** a request is built from the single user message `hello`
- **THEN** it has no call kind, the reasoning intent Off, no temperature, no response format, no context size, no expected output and no output cap

#### Scenario: A temperature and a format reach the server

- **WHEN** a request of the call kind draft with temperature `0.2` and the response format `translate` with the schema `{"type":"object","properties":{"items":{"type":"array"}},"required":["items"]}` is sent to the provider `lmstudio`
- **THEN** the posted body has `"temperature":0.2` and a `response_format` whose `json_schema.schema` is that object

#### Scenario: The pseudo model ignores both

- **WHEN** the single user message `hello` with temperature `0.2`, context size `8192`, 400 expected output tokens, an output cap of `661`, reasoning On and no response format is sent to the pseudo model
- **THEN** it replies `HELLO` with a normal finish

### Requirement: Sanitise a reply before handing it back

WHEN a real provider's reply text is not blank, the system SHALL, before returning it, remove thinking blocks and chat-template tokens that never occur in book prose, then the filters the model's profile switches on, then one Markdown code fence that wraps the text and a leading `json` label before a JSON object, and trim it; it SHALL return the result even when empty, with the finish unchanged, and write one DEBUG line with the counts removed.

**Source**: FR-INFER-09, reply handling (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `#empty-response-ordering`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: models narrate their thinking inline in many markups — `<think>`, `[THINK]`, Gemma 4's thought channel, gpt-oss's harmony channels — or leak end-of-turn tokens, and wrap JSON in a fence even when told not to. The engine should never have to know that. A `</think>` with no opener drops everything before it; harmony keeps the text after its last final channel. Two filters run only where the profile says: a hidden `to=self` message (muse-glimmer) and a whole item wrapped in `\boxed{…}` (Magistral). The removed text appears at TRACE only. A reply that was all thinking comes back empty and is handled by the rule for a reply that spent its answer thinking.

#### Scenario: An inline thinking block is removed

- **WHEN** the server's reply text is `<think>The word is greeting.</think>{"items":[{"id":"Book.md:0","target":"Привіт"}]}`
- **THEN** the returned text is `{"items":[{"id":"Book.md:0","target":"Привіт"}]}`

#### Scenario: An unterminated opener strips to the end

- **WHEN** the reply text is `{"items":[]}<THINKING>and then`
- **THEN** the returned text is `{"items":[]}`

#### Scenario: A closer with no opener drops what came before

- **WHEN** the reply text is `Okay, the user wants Ukrainian.</think>{"items":[]}`
- **THEN** the returned text is `{"items":[]}`

#### Scenario: Gemma 4's thought channel and stray turn tokens

- **WHEN** the reply text is `<|channel>thought…<channel|>{"items":[]}<end_of_turn>`
- **THEN** the returned text is `{"items":[]}`

#### Scenario: Harmony keeps the final channel

- **WHEN** the reply text is `<|channel|>analysis<|message|>Translate it.<|end|><|start|>assistant<|channel|>final<|message|>{"items":[]}<|return|>`
- **THEN** the returned text is `{"items":[]}`

#### Scenario: A boxed answer is unwrapped only where the profile says

- **WHEN** a reply for `magistral:24b`, whose profile switches the boxed filter on, is `\boxed{Привіт}`
- **THEN** the returned text is `Привіт`

#### Scenario: A fenced reply is unwrapped

- **WHEN** the reply text is three backticks, `json`, a line feed, `{"items":[]}`, a line feed and three backticks
- **THEN** the returned text is `{"items":[]}`

#### Scenario: A bare JSON label is removed

- **WHEN** the reply text is `json{"items":[]}`
- **THEN** the returned text is `{"items":[]}`

#### Scenario: A reply that was only reasoning comes back empty

- **WHEN** the reply text is `<think>nothing to say</think>`
- **THEN** the returned text is empty with a normal finish, and the result is not an error

### Requirement: Request provider controls without trusting them as guarantees

IF a server rejects a request with HTTP `400` or `422` whose body names an optional setting the request sent — the words `think` or `thinking` naming the reasoning control — THEN the system SHALL send it once more without that setting, with `max_completion_tokens` for a refused `max_tokens`, or one rung down for a refused answer schema, and SHALL record the step without exposing the response body.

**Source**: FR-INFER-09 and FR-INFER-09e, refused settings (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `#effective-context`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: servers differ in which optional knobs they accept. BookLoom first avoids sending a knob the model or the engine does not declare; a refusal that still comes is a hint, not a failure, so the request is sent once more without it. Today Ollama's "does not support thinking" is missed because only the bare word `think` is matched. Which settings count and what the repeat keeps are stated in this capability's refused-setting requirements.

#### Scenario: An unsupported native thinking control is omitted once

- **WHEN** Ollama rejects a request carrying `"think":false` and the context size `8192` with `400 {"error":"\"qwen2.5:1.5b\" does not support thinking"}`
- **THEN** the next request has the same messages, temperature, response format and `"num_ctx":8192` but no `think` field

#### Scenario: A structured-output rejection downgrades once

- **WHEN** a provider explicitly rejects the JSON schema of a draft
- **THEN** the next request has no structured-output field and no retry delay

#### Scenario: A request retried without structured output still carries its context size

- **WHEN** Ollama explicitly rejects the JSON-schema format of a draft request that carries the temperature `0.2`, the context size `8192` and 400 expected output tokens, from a provider with the default request timeout
- **THEN** the next request has no `format` field and its `options` hold `"temperature":0.2` and `"num_ctx":8192`
- **AND** its attempt waits up to 200 seconds, as its expected output gives

#### Scenario: A refused max_tokens becomes max_completion_tokens

- **WHEN** an OpenAI-compatible server answers `400` with the message `Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead.` to a request carrying `"max_tokens":400`
- **THEN** the next request carries `"max_completion_tokens":400` and no `max_tokens` key

### Requirement: Carry the provider's token usage on a reply

WHEN a provider reports figures for a call, the system SHALL return with the reply its prompt tokens, cached prompt tokens, completion tokens, generation time, time to first token and prompt-reading time, each only when reported; a figure not reported SHALL stay missing all the way to the run summary, which shows it as `–` and counts only the calls that reported it, never 0.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: the Translating screen's tokens per second and the run summary's figures come from what the server says. A missing figure must look missing, so an average is taken over the calls that reported it and the engine knows to estimate instead of showing a speed of zero. Which field of each server's reply holds which figure is the `llm-provider` capability's rule and this capability's timing requirement.

#### Scenario: Usage is carried when reported

- **WHEN** a provider reports 812 prompt tokens, 640 of them cached, 96 completion tokens and a generation time of 3.2 seconds
- **THEN** the reply carries 812, 640, 96 and 3.2 seconds

#### Scenario: No usage is carried when none is reported

- **WHEN** a provider's reply carries no usage figures
- **THEN** the reply carries no usage, and neither count reads 0

#### Scenario: A missing figure does not lower an average

- **WHEN** a run makes two drafts, one reporting a time to first token of 0.4 s and one reporting none
- **THEN** the run summary's time to first token for drafts is 0.4 s over 1 call, not 0.2 s

### Requirement: Remember a refused setting for the provider and model

WHEN a setting is refused and dropped, the system SHALL remember the refusal for that provider and model across restarts — for the whole model, or for one call kind when it concerns that kind's answer schema — and SHALL leave the setting out of the first attempt of every later call it covers until the person chooses Send again; a refused system role SHALL be remembered as a fold.

**Source**: FR-INFER-09e, refused settings (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), `docs/specification/mockups/README.md#refused-settings-s6`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a model that rejects `temperature` once will reject it every time; remembering saves a failed request at the start of every later call, and the person can undo it from Settings › Models. A schema one call kind cannot use says nothing about the others, so that refusal keeps its scope.

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

- **WHEN** `google/gemma-4-e4b` refused the draft's answer schema and a chapter-summary call is then made to it
- **THEN** the chapter-summary request still carries its `response_format`

#### Scenario: A save on the Models tab keeps a refusal recorded meanwhile

- **WHEN** the Models tab is open on `gpt-oss:20b`, the chat layer records a refusal of `temperature` and the person then saves a changed context window
- **THEN** the stored settings hold the new context window and still hold the refusal of `temperature`

### Requirement: Apply a model's own settings to the segments not translated yet

WHEN a person saves a change to a model's settings (temperature choice, thinking choice, sampling choice, context window, answer length and its field) while a run is in progress, the system SHALL apply the context window from the next chunk that starts and the other settings from the next request, and SHALL leave decided segments unchanged.

**Source**: decision 6 of the UI finalization proposal, per-model settings apply to what is not translated yet (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`, `docs/specification/01_Product/07_SETTINGS.md#models-tab`. In plain words: changing a setting mid-book steers the rest of the book without re-translating the part already done, and the screen says so ("to the segments not translated yet"). A chunk is packed for its context window when it starts, so the chunk in flight keeps that window; the thinking and sampling choices, now per model, change nothing about packing and apply from the next request.

#### Scenario: A change saved during a run reaches the next chunk

- **WHEN** segments 1 to 48 are decided, chunk 7 (segments 49 to 52) is being drafted with `"num_ctx":8192`, and the person saves the context window `16384` for `gemma4:e4b-mlx`
- **THEN** the remaining calls of chunk 7 carry `"num_ctx":8192`, the first draft call of chunk 8 carries `"num_ctx":16384`, and segments 1 to 48 are unchanged

#### Scenario: A temperature change saved during a run reaches the next request

- **WHEN** chunk 7 is being drafted with the temperature choice "Let BookLoom choose", and the person saves "My value" `0.7` for `gemma4:e4b-mlx` between two calls of chunk 7
- **THEN** the next call of chunk 7 carries `"temperature":0.7` and the calls before the save are unchanged

#### Scenario: A thinking choice saved during a run reaches the next request

- **WHEN** a run is drafting with `qwen3.5:9b` and the person chooses Always for it between two calls
- **THEN** the next call carries `"think":true` and the decided segments are unchanged

## REMOVED Requirements

### Requirement: Carry the reasoning level that the Generation setting chooses

**Reason**: Thinking is no longer one app-wide choice on Settings › Generation, and the `BOOKLOOM_REASONING` environment variable leaves the app. Each model has its own default from its profile and the person's override on Settings › Models, because models differ in whether and how thinking can be switched off.

**Migration**: Replaced by "Choose the reasoning setting per call kind from the model profile", with the engine's off values in "Send reasoning Off as the engine's own off value". A stored global Thinking choice is not carried over: the database is set aside under ADR-0043 (no migration before the first release), so every model starts at its profile's default.

### Requirement: The pseudo model answers with the last user message in capitals

**Reason**: Its answer shapes name call kinds this change removes — the directed fix, the reviewer and the rolling summary — and miss the new ones (the flat batch of items, the proofread reply and the chapter summary).

**Migration**: Replaced by "The pseudo model answers every call kind with the text in capitals", which keeps the capitals, the kept tokens and references, the locale rule, the normal finish, no usage and no network.

### Requirement: Send each call kind at its own temperature

**Reason**: Its temperatures name the reviewer and the directed fix, which this change removes, and miss the repair and the proofread call that replace them.

**Migration**: Replaced by "Send each call kind of the three stages at its own temperature", which keeps the person's three temperature choices and the lower desk retry.
