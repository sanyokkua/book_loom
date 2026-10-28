## ADDED Requirements

### Requirement: Detect the server engine only when the person saves, tests or asks

WHEN the person saves or tests a provider in Settings › Providers, or presses Detect again, the system SHALL decide which server engine answers at its address — Ollama, LM Studio, llama-server, Unsloth, OpenRouter or a generic OpenAI-compatible server — with at most 7 GET requests of 2 seconds each that carry no body, no book text and the key only when the provider has one, and SHALL store the engine and its version on the provider; it SHALL never detect at start-up or in the background.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`, `design.md#d17--server-engines-detection-and-traits`. In plain words: every local server speaks the same chat format but needs different fields for the same wish (turning thinking off, capping the answer), so BookLoom must know which program answers. It asks a few short questions only when the person acts, which keeps the offline rule; the one first-launch listing of ADR-0044 (the single allowed look at the two local addresses) is unchanged and llama-server's port is not added to it.

#### Scenario: A llama-server is recognised by its header and its properties

- **WHEN** the person tests a provider at `http://127.0.0.1:9931/v1`, `GET /api/version` answers `404`, `GET /health` answers `200 {"status":"ok"}` with the header `Server: llama.cpp`, and `GET /props` answers with `"build_info":"b11669-620d2225"`
- **THEN** the provider is stored with the engine llama-server and the version `b11669`

#### Scenario: Ollama is recognised by its version and its model list

- **WHEN** the person saves a provider at `http://127.0.0.1:11434`, `GET /api/version` answers `{"version":"0.40.11"}`, `GET /api/tags` answers `{"models":[{"name":"qwen2.5:1.5b"}]}` and `GET /v1/models` lists `qwen2.5:1.5b` with `"owned_by":"library"`
- **THEN** the provider is stored with the engine Ollama and the version `0.40.11`

#### Scenario: Unsloth is recognised by its health reply

- **WHEN** the person tests a provider at `http://127.0.0.1:8888/v1` and `GET /api/health` answers `{"status":"healthy","service":"Unsloth UI Backend"}`
- **THEN** the provider is stored with the engine Unsloth

#### Scenario: LM Studio is recognised by its native model list

- **WHEN** the person tests a provider at `http://127.0.0.1:1234/v1` and `GET /api/v0/models` answers `{"object":"list","data":[{"id":"google/gemma-4-e4b","type":"vlm","compatibility_type":"mlx"}]}`
- **THEN** the provider is stored with the engine LM Studio and no version

#### Scenario: A known host needs no probe

- **WHEN** the person saves a provider at `https://openrouter.ai/api/v1`
- **THEN** the provider is stored with the engine OpenRouter and only the model listing is requested

#### Scenario: Nothing recognisable answers

- **WHEN** no probe's reply has the expected shape and `GET /v1/models` answers rows with `"owned_by":"vllm"`
- **THEN** the provider is stored with the engine generic OpenAI-compatible

#### Scenario: A start-up sends nothing

- **WHEN** the window starts with a stored provider at `http://127.0.0.1:9931/v1` and the first-launch look is already recorded as done
- **THEN** no request reaches `http://127.0.0.1:9931` until the person saves, tests, presses Detect again or starts a run

### Requirement: Recognise a server only by the shape of its replies

The system SHALL count a detection probe as a hit only when the reply body has the shape that engine sends, never on the HTTP status or the port alone, and SHALL NOT take a server for Ollama when its model list carries another server's marker or its OpenAI-shaped model list holds a row without the `owned_by` field.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, `design.md#d17--server-engines-detection-and-traits`. In plain words: LM Studio answers every unknown path with HTTP 200 and an error body, and LocalAI and KoboldCpp imitate Ollama's own routes. A server that only pretends to be Ollama would be sent Ollama's native fields and fail, so it is treated as a generic OpenAI-compatible server instead.

#### Scenario: A KoboldCpp server that imitates Ollama is not Ollama

- **WHEN** `GET /api/version` answers `{"version":"0.9.1"}` and `GET /api/tags` answers `{"models":[{"name":"koboldcpp/Mara","details":{"family":"koboldcpp"}}]}`
- **THEN** the engine is not Ollama and detection goes on to the next probe

#### Scenario: A LocalAI server whose rows lack owned_by is not Ollama

- **WHEN** `/api/version` and `/api/tags` answer Ollama's shapes and `GET /v1/models` answers `{"data":[{"id":"mara-7b"}]}`
- **THEN** the engine is not Ollama

#### Scenario: An error body with status 200 is not a hit

- **WHEN** LM Studio answers `GET /api/version` with HTTP 200 and `{"error":"Unexpected endpoint or method. (GET /api/version)"}`
- **THEN** that probe is not counted as Ollama

#### Scenario: A proxy that forwards only Ollama's routes keeps the hit

- **WHEN** `/api/version` and `/api/tags` answer Ollama's shapes and `GET /v1/models` answers `404`
- **THEN** the engine is Ollama

### Requirement: Let the person choose the engine with Treat this server as…

WHEN the person chooses an engine under "Treat this server as…", the system SHALL store that engine on the provider, SHALL build every request from it until the person presses Detect again, and SHALL log the choice once at INFO with the source `override`.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: detection can be wrong for an unusual proxy or a new server, so the person can always say what the server is; detection never overrides that choice by itself.

#### Scenario: The person's choice wins over detection

- **WHEN** detection found a generic server at `http://10.0.0.7:8080/v1` and the person chooses llama-server under "Treat this server as…"
- **THEN** the next draft request to that provider carries `chat_template_kwargs`
- **AND** the log holds `engine detected provider=office engine=LLAMA_CPP probes=0 source=override`

#### Scenario: Detect again replaces the choice

- **WHEN** the person later presses Detect again and the probes find Ollama
- **THEN** the provider's engine becomes Ollama and the stored override is gone

### Requirement: Refuse an app that cannot translate a book at the connection test

WHEN detection finds GPT4All (its model rows carry `"owned_by":"humanity"`) or AnythingLLM (a base address whose path is `/api/v1/openai`), the system SHALL fail the connection stage with `ErrorCode.validation` and the reason `not-suitable-structured` or `not-suitable-documents`, store no engine for the provider, and send no chat request.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, `design.md#d17--server-engines-detection-and-traits`. In plain words: GPT4All's server refuses the answer schema every translation call needs and is no longer maintained; AnythingLLM's "model" is a workspace that chats over the person's documents, not a model. Both look like an OpenAI-compatible server, so they are recognised by what their replies and addresses show and stopped at the first test, before any book text is sent. The sentence the form shows is the `settings` capability's "Say in one sentence why an app cannot be used".

#### Scenario: GPT4All is refused by its model list

- **WHEN** the person tests a provider at `http://127.0.0.1:4891/v1` and `GET /v1/models` answers `{"data":[{"id":"Llama 3 8B Instruct","object":"model","owned_by":"humanity"}]}`
- **THEN** the connection stage fails with `validation`, reason `not-suitable-structured`, and no chat request is sent

#### Scenario: AnythingLLM is refused by its address

- **WHEN** the person tests a provider at `http://127.0.0.1:3001/api/v1/openai`
- **THEN** the connection stage fails with `validation`, reason `not-suitable-documents`, and no chat request is sent

#### Scenario: A usable server is not refused

- **WHEN** the person tests a provider at `http://127.0.0.1:9931/v1` and llama-server answers
- **THEN** the connection stage passes

### Requirement: Offer Ollama's own connection once when Ollama answers behind an OpenAI-compatible provider

WHEN detection finds Ollama at a provider saved as OpenAI-compatible, the system SHALL offer once to use Ollama's own connection, SHALL never switch the provider by itself, and, IF the person keeps it as it is, THEN SHALL keep it OpenAI-compatible with the engine Ollama and SHALL not offer again for that provider, also after Detect again.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Ollama's OpenAI-shaped path drops the context size, the answer cap field `max_completion_tokens`, most samplers and the thinking levels, so its native connection translates better; the person decides, once. Declining is remembered on its own, apart from the person's engine choice, so Detect again (which clears that choice) never brings the offer back and never shows Ollama as a choice the person made. The offer's words and where the form shows them are the `settings` capability's "Offer Ollama's own connection once when Ollama is behind an OpenAI-compatible provider".

#### Scenario: The offer appears once

- **WHEN** a provider at `http://127.0.0.1:11434/v1` saved as OpenAI-compatible is tested and Ollama answers
- **THEN** the form shows "This is Ollama. Use Ollama's own connection (recommended)? It keeps the window, overflow and sampling controls." with Use it and Keep as is

#### Scenario: Keeping it never asks again

- **WHEN** the person presses Keep as is and tests the same provider again
- **THEN** the provider stays OpenAI-compatible with the engine Ollama and no offer is shown

#### Scenario: Detecting again does not bring the offer back

- **WHEN** the person pressed Keep as is, then presses Detect again and Ollama answers again
- **THEN** no offer is shown, the engine is the detected Ollama, and "Treat this server as…" holds no choice of the person's

### Requirement: Fail the test of an Ollama provider whose server is another engine

IF a provider saved as Ollama-native is tested and detection finds another engine at its address, THEN the system SHALL fail the test with a message that names the engine found.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Ollama's native requests mean nothing to LM Studio or llama-server, so a mismatch is caught in Settings instead of on the first segment.

#### Scenario: LM Studio saved as Ollama

- **WHEN** a provider saved as Ollama-native at `http://127.0.0.1:1234` is tested and the probes find LM Studio
- **THEN** the test fails with the message "The server at this address is LM Studio, not Ollama."

#### Scenario: llama-server saved as Ollama

- **WHEN** a provider saved as Ollama-native at `http://127.0.0.1:9931` is tested and the probes find llama-server
- **THEN** the test fails with the message "The server at this address is llama-server, not Ollama."

### Requirement: Post LM Studio's chat to its own route and fall back to the OpenAI route

WHERE the engine is LM Studio, the system SHALL post each chat request, unstreamed, to `/api/v0/chat/completions` at the server's root with the same body it would post to `/v1`, and, IF that route answers with a body that is not JSON or with `{"error":"Unexpected endpoint…"}`, THEN SHALL use `<baseUrl>/chat/completions` for that provider for the rest of the session.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `design.md#d17--server-engines-detection-and-traits`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: LM Studio's own route takes the same body and adds the time to the first word and the loaded window to its reply; an older or changed LM Studio that lacks it still works through the usual route.

#### Scenario: A draft goes to the native route

- **WHEN** a draft for `google/gemma-4-e4b` is sent to a provider at `http://127.0.0.1:1234/v1` whose engine is LM Studio
- **THEN** the body is posted to `http://127.0.0.1:1234/api/v0/chat/completions` with `"stream":false`

#### Scenario: An unknown route falls back for the session

- **WHEN** `/api/v0/chat/completions` answers HTTP 200 with `{"error":"Unexpected endpoint or method. (POST /api/v0/chat/completions)"}`
- **THEN** that request is posted again to `http://127.0.0.1:1234/v1/chat/completions` and every later request in the session goes there

### Requirement: Check the model id against the server's own list where any id is served

WHERE the engine answers any model id with whatever model it has loaded (LM Studio, Unsloth) or routes by id (a llama-server router), WHEN the person tests the provider or a run starts, the system SHALL check the model id against that server's own model list and, IF it is not there, THEN stop with a message naming the id before any chat request.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`, `design.md#d17--server-engines-detection-and-traits`. In plain words: LM Studio does not refuse a misspelt id — it answers with the loaded model — so a whole book could be translated by the wrong model without a word. Checking the list first stops that before it starts. The message's words are the `settings` capability's "Say that LM Studio has no model with the typed id".

#### Scenario: A misspelt LM Studio id stops the start

- **WHEN** a run is started with `google/gemma-4-26b-a4b-qatt` and `/api/v0/models` lists `google/gemma-4-26b-a4b-qat` and `google/gemma-4-e4b`
- **THEN** the run does not start, the message names `google/gemma-4-26b-a4b-qatt`, and no chat request is sent

#### Scenario: A listed id passes

- **WHEN** the test runs for `mistralai/ministral-3-14b-reasoning` and `/api/v0/models` lists it
- **THEN** the models stage passes

### Requirement: Never send an LM Studio quant variant

WHERE the engine is LM Studio, the system SHALL remove an `@variant` suffix from a model id before it is stored or sent, and SHALL say once that it was removed.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: an id like `model@4bit` makes LM Studio load a second copy of the model beside the first, which can exhaust memory; the loaded model is what translates. The words the form shows are the `settings` capability's "Say that a typed LM Studio variant was removed".

#### Scenario: A typed variant is stripped

- **WHEN** the person adds the model `google/gemma-4-26b-a4b-qat@4bit` to an LM Studio provider
- **THEN** the stored and sent id is `google/gemma-4-26b-a4b-qat` and one line says the variant was removed

### Requirement: Refuse a reply that another model wrote where any id is served

WHERE the engine answers any model id with its loaded model (LM Studio, Unsloth), IF a reply's `model` names a model other than the one requested, THEN the system SHALL refuse the reply with `ErrorCode.modelNotFound` and the reason `wrong-model-served`, and SHALL say "{engine} answered with {other model}, not {chosen model}. Check the model id."

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: the id check before a run is the first guard; this is the second, for a model swapped in LM Studio during a run. A translation written by a model the person did not choose is not kept.

#### Scenario: A swapped model is refused

- **WHEN** a draft for `google/gemma-4-26b-a4b-qat` is answered `200` with `"model":"google/gemma-4-e4b"` by LM Studio
- **THEN** the call fails with `modelNotFound`, reason `wrong-model-served`, and the message reads "LM Studio answered with google/gemma-4-e4b, not google/gemma-4-26b-a4b-qat. Check the model id."

### Requirement: Send max_tokens to Ollama's OpenAI-compatible path whatever the stored choice

WHERE the engine is Ollama and the provider is OpenAI-compatible, the system SHALL send an output cap only as `max_tokens`, even when the model's settings choose `max_completion_tokens`.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Ollama's OpenAI-shaped path silently ignores `max_completion_tokens`, so a call capped that way runs without a cap; only `max_tokens` stops it.

#### Scenario: The stored field choice is not used on Ollama's OpenAI path

- **WHEN** an Ollama provider kept as OpenAI-compatible has the model `qwen3.5:9b` with the stored output field `max_completion_tokens`, and a draft capped at `400` is sent
- **THEN** the body has `"max_tokens":400` and no `max_completion_tokens` key

### Requirement: Turn off a server's own tools on every call

WHERE the engine runs tools of its own by default (Unsloth), the system SHALL send `"enable_tools":false` in every chat request to it.

**Source**: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Unsloth Desktop runs web search, Python and terminal tools by default when it listens on this computer, so a translation request could start a web search with book text. BookLoom turns them off on every request; the key stays an environment-variable reference as for every provider.

#### Scenario: An Unsloth draft turns tools off and carries the key

- **WHEN** a draft is sent to an Unsloth provider whose Bearer key comes from `UNSLOTH_API_KEY` with the value `sk-unsloth-test`
- **THEN** the body has `"enable_tools":false` and the request carries `Authorization: Bearer sk-unsloth-test`

### Requirement: Check what a server honours only when the person asks

WHEN the person runs the Advanced features stage of a test for a model, the system SHALL send at most four short checks with invented text and at most 64 output tokens each — answer schema, turning reasoning off, the extra samplers `top_k` and `min_p`, and the answer-cap field — and SHALL store each verdict on the model with the source `test`.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`, `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: a server BookLoom does not know may ignore a schema or a thinking switch without saying so. Four tiny calls the person starts find out, and their answers decide what later calls send.

#### Scenario: A schema check that is honoured

- **WHEN** the schema check sends a `response_format` whose only allowed value for `k` is `ZX-91` and the reply is exactly `{"k":"ZX-91"}`
- **THEN** the model's stored verdict is "structured answers: honoured", source `test`

#### Scenario: A schema check answered with free text

- **WHEN** the schema check is answered `200` with `Sure, here it is: ZX-91`
- **THEN** the stored verdict is "structured answers: ignored" and no request is sent with `json_object`

#### Scenario: A cap-field check

- **WHEN** the cap check asks for the numbers from 1 to 100 with `"max_completion_tokens":16` and the reply reports 16 completion tokens and `"finish_reason":"length"`
- **THEN** the stored verdict is "answer cap: `max_completion_tokens`"

### Requirement: Send a generic server only what it has been shown to honour

WHERE the engine is a generic OpenAI-compatible server, the system SHALL send no answer schema and no reasoning control until a stored check verdict shows the server honours it.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `design.md#d17--server-engines-detection-and-traits`. In plain words: an unknown server gets the plain request every OpenAI-shaped server understands, and the JSON contract is stated in the prompt; a field is added only once the server has proved it works.

#### Scenario: No verdict, plain request

- **WHEN** a draft for `mara-7b` is sent to a generic server with no stored verdicts
- **THEN** the body has `model`, `messages`, `temperature`, `"stream":false` and the cap, and no `response_format` and no `reasoning_effort`

#### Scenario: An honoured schema is sent

- **WHEN** the model's stored verdict says structured answers are honoured
- **THEN** the next draft carries `response_format` with `"type":"json_schema"`

### Requirement: Warn once when a server ignores the answer cap

IF an OpenAI-compatible reply reports more completion tokens than the request's cap and a normal finish, THEN the system SHALL write one WARN line with the reason `cap-ignored` once per provider and model, and SHALL use the reply.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a server that ignores the cap lets a looping model write until its own limit; the person learns it from one line and the Models tab, without a pause.

#### Scenario: A cap ignored twice is said once

- **WHEN** two drafts capped at `80` to `mara-7b` are answered with `"completion_tokens":900` and `"finish_reason":"stop"`
- **THEN** the log holds exactly one WARN line with `reason=cap-ignored` for that model, and both replies are used

### Requirement: Name every classified failure with a reason token

WHEN a call fails, the system SHALL attach exactly one reason token chosen by the catalogue row that matched (for example `context-overflow` or `model-not-pulled`), carry it in the error's details and on every WARN line about that failure, keep the server's own sentence out of the details (TRACE only), and use `unclassified` when no row matches.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#safe-details-allowlist`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: the error code says what kind of failure it was; the reason says which one, so the run decides well (flag, wait, retry or ask) and the run log explains it. The token is chosen by BookLoom, never copied from the server, so no secret or book text can leak through it.

#### Scenario: An Ollama model that is not pulled

- **WHEN** Ollama answers `404 {"error":"model 'qwen3.5:4b' not found"}`
- **THEN** the error is `modelNotFound` with reason `model-not-pulled`, and the message says "This model is not installed in Ollama. Pull it (`ollama pull qwen3.5:4b`) or pick another."

#### Scenario: An Unsloth key that is missing or revoked

- **WHEN** Unsloth answers `401 {"error":{"type":"authentication_error"}}`
- **THEN** the error is `auth` with reason `auth`, and the message says to create a key in Unsloth › Settings › API and put its environment-variable name in the form

#### Scenario: A body nobody has seen before

- **WHEN** a server answers `400 {"error":"flux capacitor misaligned"}` to a request that sent no setting it names
- **THEN** the error is `validation` with reason `unclassified`, the WARN line holds `reason=unclassified`, and the body appears only at TRACE

### Requirement: Flag a passage that does not fit the model's window instead of pausing

IF a reply says the prompt did not fit the model's window — in any word order, as a `400`, as llama-server's `exceed_context_size_error`, or as an error event inside an LM Studio `200` — THEN the system SHALL answer `ErrorCode.contextWindow` with the reason `context-overflow` and no retry, and the run SHALL flag that segment and go on rather than pause for the person.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: today an overflow worded "exceeds the available context size" is missed because the word "context" comes after the verb, and a misread error once paused a run for 43 minutes. One passage too long for the window is that passage's problem; the rest of the book goes on.

#### Scenario: Ollama's overflow wording with the verb first

- **WHEN** Ollama answers a draft `400` with the body `request (696 tokens) exceeds the available context size (512 tokens)`
- **THEN** the error is `contextWindow` with reason `context-overflow`, the segment is flagged with "This passage did not fit the model's window (696 of 512 tokens). It was flagged.", and the run goes on with the next segment

#### Scenario: llama-server's typed overflow carries its counts

- **WHEN** llama-server answers `400` with `{"error":{"type":"exceed_context_size_error","n_prompt_tokens":9120,"n_ctx":8192}}`
- **THEN** the error is `contextWindow` with reason `context-overflow`, and its details hold the counts 9120 and 8192

#### Scenario: LM Studio's overflow inside a 200

- **WHEN** LM Studio answers HTTP 200 whose body carries `event: error` with `Context size has been exceeded`
- **THEN** the error is `contextWindow` with reason `context-overflow`, and the message adds "Reload the model with a larger context."

### Requirement: Retry a reply the server terminated once

IF a reply's body is `{"error":"terminated"}`, whatever its HTTP status, THEN the system SHALL answer `ErrorCode.upstream` with the reason `terminated` and send the request once more, and IF that is terminated too, THEN SHALL hand the failure to the run as an outage.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`, the owner's decision that this reply is mapped by its body text whatever its status. In plain words: LM Studio stops a reply with this body; sent as a `400` it used to read as a bad request and pause the run for the person, although nothing about the request was wrong.

#### Scenario: Terminated as a 400

- **WHEN** LM Studio answers a draft `400` with `{"error":"terminated"}` and the repeat is answered normally
- **THEN** the first answer is `upstream` with reason `terminated`, not `validation`, and the call succeeds after 2 requests

#### Scenario: Terminated as a 200

- **WHEN** LM Studio answers HTTP 200 with only `{"error":"terminated"}`, twice
- **THEN** the call fails with `upstream`, reason `terminated`, after 2 requests, and the run treats it as an outage

### Requirement: Retry a model that repeats itself once with a new seed, then flag

IF a reply says the model hit its token repeat limit, THEN the system SHALL answer `ErrorCode.upstream` with the reason `repeat-limit`, send the request once more with a new seed, and, IF that fails the same way, THEN the run SHALL flag the segment with "The model kept repeating itself here." and go on.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a model caught in a loop on one passage needs a different random path, not three identical retries; if it still loops, that passage goes to review.

#### Scenario: A loop then an answer

- **WHEN** Ollama answers a draft `500 {"error":"token repeat limit reached"}` and the second request, which carries a new `seed`, is answered normally
- **THEN** the call succeeds after 2 requests

#### Scenario: Two loops flag the segment

- **WHEN** both requests answer `token repeat limit reached`
- **THEN** the segment is flagged with "The model kept repeating itself here." and the run is not paused

### Requirement: Ask the person when the model does not fit in memory

IF a reply says the model needs more memory than is free, THEN the system SHALL answer `ErrorCode.modelUnavailable` with the reason `out-of-memory`, SHALL not retry, and the run SHALL pause for the person rather than wait for the model to load.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: waiting does not free memory; the person has to close something or choose a smaller build, so BookLoom says so with the numbers instead of waiting forever.

#### Scenario: Not enough memory

- **WHEN** Ollama answers `500 {"error":"model requires 18.2 GiB but only 9.6 GiB are available"}`
- **THEN** the error is `modelUnavailable` with reason `out-of-memory`, the server received 1 request, and the message says "Not enough memory to load this model (needs 18.2 GiB, 9.6 GiB free). Close other apps or choose a smaller build."

### Requirement: Wait while llama-server is still loading its model

WHEN llama-server answers `503` with `Loading model`, the system SHALL answer `ErrorCode.modelUnavailable` with the reason `model-loading`, so the run waits for the model as it does for an unloaded LM Studio model, and SHALL send the request again once the server's `/health` no longer says it is loading.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a llama-server answers `503` on every route while it loads a model; that is a wait, not a server fault worth three quick retries.

#### Scenario: A loading server is waited for

- **WHEN** a draft is answered `503 {"error":{"message":"Loading model"}}` and `/health` then answers `200 {"status":"ok"}`
- **THEN** the first answer is `modelUnavailable` with reason `model-loading`, and the draft is sent again after the health check

### Requirement: Never repeat unchanged a request the server says BookLoom built wrong

IF a reply says a request BookLoom built is malformed — an Ollama option of the wrong type, a duration without a unit, an answer schema the server cannot use, or a grammar too large to build — THEN the system SHALL answer `ErrorCode.validation` with that row's reason, write one ERROR line for it once per model, and never send the same body again.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: these are BookLoom's own bugs. Today Ollama's `500` for a wrongly typed option is retried three times as a server fault; it will fail every time, so it is reported once, loudly, and the request changes or stops.

#### Scenario: A wrongly typed option is not retried

- **WHEN** Ollama answers `500 {"error":"option \"stop\" must be of type array"}`
- **THEN** the error is `validation` with reason `option-type`, the server received 1 request, and one ERROR line names `option-type`

#### Scenario: A grammar that cannot be built

- **WHEN** LM Studio answers `400 {"error":"Failed to initialize samplers: failed to parse grammar"}` to a draft
- **THEN** the error is `validation` with reason `grammar-too-large`, and no later request repeats that body

### Requirement: Say why Ollama refused an address or a cloud model

IF Ollama answers `403` with an empty body, THEN the system SHALL answer `ErrorCode.auth` with the reason `host-forbidden`; IF it says its cloud features are disabled or asks to sign in for a cloud model, THEN `auth` with the reason `cloud-disabled` or `cloud-signin`; each SHALL pause the run for the person with a message naming what to change.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`, the owner's decision that Ollama cloud models are listed like any model with a neutral label and no consent step. In plain words: Ollama refuses addresses it does not trust and cloud models when the person is not signed in; neither is a wrong key, so the message says what actually fixes it.

#### Scenario: An address Ollama does not accept

- **WHEN** Ollama at `http://192.168.1.20:11434` answers a chat `403` with an empty body
- **THEN** the error is `auth` with reason `host-forbidden` and the message says "Ollama refused this address. Use 127.0.0.1, or let Ollama listen on the network."

#### Scenario: A cloud model without a sign-in

- **WHEN** a chat to `gpt-oss:120b-cloud` is answered `401 {"error":"unauthorized","signin_url":"https://ollama.com/connect"}`
- **THEN** the error is `auth` with reason `cloud-signin` and the message says "Sign in to Ollama to use this cloud model"

### Requirement: Read an error that OpenRouter sends inside a 200 reply

WHERE the engine is OpenRouter, IF a `200` reply's body holds only an `error` object, or a choice finished with `error`, THEN the system SHALL read that error before any choice and classify it by its `error.metadata.error_type`, so that a provider outage is `ErrorCode.upstream` with the reason `provider-unavailable`.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `design.md#d21--openrouter-correctness-essentials`, the owner's decision that OpenRouter gets only what correctness needs. In plain words: OpenRouter reports some failures with HTTP 200; read as a normal reply, an outage would look like an empty or unreadable translation.

#### Scenario: An outage inside a 200

- **WHEN** OpenRouter answers HTTP 200 with `{"error":{"code":502,"message":"Provider returned error","metadata":{"error_type":"provider_unavailable"}}}`
- **THEN** the error is `upstream` with reason `provider-unavailable`, retried like any outage, not an unreadable reply

#### Scenario: A choice that finished with an error

- **WHEN** OpenRouter answers HTTP 200 with `{"choices":[{"message":{"content":""},"finish_reason":"error"}],"error":{"metadata":{"error_type":"provider_unavailable"}}}`
- **THEN** the error is `upstream` with reason `provider-unavailable`

### Requirement: Ask OpenRouter only for endpoints that honour every parameter sent

WHERE the engine is OpenRouter, the system SHALL send `"provider":{"require_parameters":true}` with every chat request, and, IF OpenRouter answers that no endpoint supports them, THEN SHALL answer `ErrorCode.validation` with the reason `no-endpoint-for-parameters` and pause the run for the person.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, `design.md#d21--openrouter-correctness-essentials`. In plain words: one OpenRouter model can be served by several hosting companies, and one that cannot honour the answer schema or the thinking switch would silently drop it. Asking for endpoints that honour everything turns that silent loss into a clear message.

#### Scenario: Parameters are required

- **WHEN** a draft with a JSON schema is sent to OpenRouter for `mistralai/mistral-small-3.2-24b-instruct`
- **THEN** the body has `"provider":{"require_parameters":true}`

#### Scenario: No endpoint can honour them

- **WHEN** OpenRouter answers `404` saying no endpoints support the requested parameters
- **THEN** the error is `validation` with reason `no-endpoint-for-parameters` and the message says "No provider of this model supports structured answers or turning reasoning off."

### Requirement: Turn off OpenRouter's context compression

WHERE the engine is OpenRouter, the system SHALL send `"plugins":[{"id":"context-compression","enabled":false}]` with every chat request.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#effective-context`, `design.md#d21--openrouter-correctness-essentials`. In plain words: OpenRouter shortens long prompts by default by cutting their middle, which would silently drop text BookLoom sent for translation.

#### Scenario: Compression is off

- **WHEN** a draft is sent to OpenRouter
- **THEN** the body has `"plugins":[{"id":"context-compression","enabled":false}]`

### Requirement: Keep an OpenRouter call within its endpoint's limits

WHERE the engine is OpenRouter, the system SHALL lower a call's output cap to the endpoint's `max_completion_tokens` when the cap is larger, and SHALL take the window from the endpoint's `context_length`, not the model's.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#effective-context`, `design.md#d21--openrouter-correctness-essentials`. In plain words: a hosting company may serve a model with a smaller window or answer limit than the model's own; planning for the larger figure would cut replies.

#### Scenario: The cap is clamped

- **WHEN** a draft capped at `6000` goes to an OpenRouter endpoint whose `max_completion_tokens` is `4096`
- **THEN** the body has `"max_tokens":4096`

### Requirement: Log OpenRouter's served provider, model and cost

WHERE the engine is OpenRouter, the system SHALL write on each call's DEBUG line the served provider's slug, the served model's slug and the reply's `usage.cost`, as slugs and numbers only, and the run summary SHALL total the cost.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-construction`, `design.md#d21--openrouter-correctness-essentials`. In plain words: the hosting company behind one model can change from call to call, and a cloud run costs money; the log says who answered and what it cost, without any book text.

#### Scenario: A call's cost is logged

- **WHEN** OpenRouter answers a draft with `"provider":"Mistral"`, `"model":"mistralai/mistral-small-3.2-24b-instruct"` and `"usage":{"cost":0.00042}`
- **THEN** the call's DEBUG line holds `Mistral`, `mistralai/mistral-small-3.2-24b-instruct` and `0.00042`, and the run summary adds `0.00042` to its cost total

### Requirement: Send OpenRouter no routing, data-policy or free-tier controls

WHERE the engine is OpenRouter, the system SHALL NOT send `data_collection`, a quantisation filter, provider pinning (`order`, `allow_fallbacks`) or `session_id`, and SHALL show no free-tier warning.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, `design.md#d21--openrouter-correctness-essentials`, the owner's decision that OpenRouter gets correctness essentials only and no routing controls. In plain words: the model the person picks is the model used; how OpenRouter routes among hosting companies stays OpenRouter's business.

#### Scenario: Only the essentials are sent

- **WHEN** a draft is sent to OpenRouter
- **THEN** the body has no `data_collection`, `quantizations`, `order`, `allow_fallbacks` or `session_id` key

### Requirement: Say why OpenRouter refused a call

IF OpenRouter answers `402`, THEN the system SHALL answer `ErrorCode.auth` with the reason `credits` and pause the run for the person; IF it answers `403` for a content-policy or moderation refusal, THEN SHALL answer `ErrorCode.validation` with the reason `blocked`, and the run SHALL flag that segment and go on.

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`, `design.md#d21--openrouter-correctness-essentials`. In plain words: no credits stops everything until the person acts; a refused passage concerns that passage only.

#### Scenario: No credits

- **WHEN** OpenRouter answers `402` with `{"error":{"metadata":{"limit_source":"credits"}}}`
- **THEN** the error is `auth` with reason `credits` and the message says "OpenRouter refused the call: no credits or the key's limit is reached."

#### Scenario: A refused passage

- **WHEN** OpenRouter answers a draft `403` with `content_policy_violation`
- **THEN** the segment is flagged with "The provider refused this passage." and the run goes on

### Requirement: Read a model's facts only when the person acts

The system SHALL read a model's facts from its server (thinking control, template, quant, size, loaded window) only when the person selects the model, opens Settings › Models, tests a provider, presses Detect again, or starts or continues a run; SHALL keep them in memory for the session; and SHALL store only the check verdicts and the refused settings.

**Source**: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the server's own facts are the best guide to a model, but asking for them is network traffic, so it happens only on the person's action; a stored copy would go stale when the server is updated.

#### Scenario: Selecting a model reads its facts

- **WHEN** the person selects `qwen3.5:9b` on an Ollama provider
- **THEN** one `POST /api/show` for `qwen3.5:9b` is sent and nothing about its facts is written to the database

#### Scenario: No facts are read in the background

- **WHEN** the window has been open for an hour with an Ollama provider and the person has done nothing
- **THEN** no `/api/show`, `/api/ps` or `/props` request has been sent

### Requirement: Read a pasted model name, link, command or file name

WHEN the person types or pastes a model id, a Hugging Face or Ollama link, a command such as `ollama run`, `lms get` or `llama-server -hf`, or a file name, the system SHALL read from it the model id to use, the family, size and kind of model, and the quant token with its flags, without opening the link, running the command or sending any request.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#model-discovery-and-slots`, `design.md#d19--model-identity-reading-model-names`. In plain words: people copy whatever they have at hand — a link from a model page, a command from a guide, a downloaded file's name. BookLoom understands each form offline, so the model's handling and advice follow from what was pasted.

#### Scenario: A Hugging Face link with a file

- **WHEN** the person pastes `https://huggingface.co/unsloth/GLM-4.7-Flash-GGUF?show_file_info=GLM-4.7-Flash-UD-Q4_K_XL.gguf`
- **THEN** it is read as the repository `unsloth/GLM-4.7-Flash-GGUF`, the model GLM 4.7 Flash, the quant `UD-Q4_K_XL` with the dynamic flag, kind chat model
- **AND** no request is sent to `huggingface.co`

#### Scenario: An Ollama command

- **WHEN** the person pastes `ollama run hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL`
- **THEN** the model id is `hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL`, read as Mistral Small 3.2, 24B, instruct, quant `IQ4_NL`, and the command is not run

#### Scenario: A downloaded file name

- **WHEN** the person pastes `NousResearch_Hermes-4-14B-Q4_K_L.gguf`
- **THEN** it is read as Hermes 4, 14B, quant `Q4_K_L`

#### Scenario: Files that are not chat models

- **WHEN** the person pastes `mmproj-BF16.gguf`, and then `unsloth/LTX-2.3-GGUF`
- **THEN** the first is read as a sidecar file and the second as a video model, and neither can be chosen as a model

#### Scenario: An unknown name keeps its own words

- **WHEN** the person pastes `someuser/mymodel:7b-q4_K_M`
- **THEN** it is read as the family `mymodel`, 7B, quant `Q4_K_M`, and no known family is invented for it

#### Scenario: A reasoning variant keeps its variant

- **WHEN** the person pastes `mistralai/ministral-3-14b-reasoning`
- **THEN** it is read as the organisation `mistralai`, the family ministral, version 3, 14B, the variant reasoning

#### Scenario: An LM Studio id with and without an organisation

- **WHEN** the person pastes `google/gemma-4-e4b`, and then `lapa-v0.1.2-instruct`
- **THEN** the first is read as the organisation `google`, Gemma 4, effective size 4B, and the second as the family lapa, release 0.1.2, the variant instruct, with no organisation and no size

#### Scenario: An unknown word stays part of the family

- **WHEN** the person pastes `nvidia-nemotron-3.5-bolt-e4b`
- **THEN** it is read as the family `nemotron-bolt`, version 3.5, effective size 4B, and not as the Nemotron family

### Requirement: Offer the Ollama pull command as text

WHERE the provider is Ollama, WHEN a pasted model is not in the server's list, the system SHALL show the matching `ollama pull` command as text the person can copy, and SHALL never run it or download anything.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d19--model-identity-reading-model-names`. In plain words: a download of several gigabytes is the person's decision; BookLoom only writes the command out.

#### Scenario: A link becomes a pull command

- **WHEN** the person pastes `https://huggingface.co/unsloth/GLM-4.7-Flash-GGUF?show_file_info=GLM-4.7-Flash-UD-Q4_K_XL.gguf` for an Ollama provider that does not list it
- **THEN** the text `ollama pull hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL` is shown and no request reaches `/api/pull`

### Requirement: Resolve a model's profile in one fixed order

The system SHALL build each model's profile — its thinking switch, samplers, window, answer cap, message shaping, quirks and batch size — from the stored engine's traits, the server's facts about the model, the built-in row its name reading picks and the person's overrides on Settings › Models; the facts SHALL win over a row and the override over everything, and a row SHALL never match a name whose variant (such as `reasoning`) its pattern does not name.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-profile`, `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, the owner's decision that per-model handling comes from model profiles and the person's per-model overrides, with no free-text instruction field. In plain words: models differ in how thinking is switched off, in templates and in samplers; one order decides which source counts, so the same model gets the same handling every time and the person can always correct it.

#### Scenario: The server's facts beat the row the name picked

- **WHEN** the name `hf.co/someuser/Qwen3.5-9B-GGUF:Q4_K_M` picks the Qwen 3.5 9B row, which has a thinking switch, and Ollama's `/api/show` for it shows no `renderer` and capabilities without `thinking`
- **THEN** the profile says the model thinks on every call and cannot turn it off, and no `think` field is sent

#### Scenario: A reasoning variant takes its own row, never the instruct row

- **WHEN** `mistralai/ministral-3-14b-reasoning` and `ministral-3:14b` are resolved
- **THEN** the first takes the Ministral 3 Reasoning row (always reasons, no switch, the reasoning reserve) whatever its server reports, and the second takes the Ministral 3 14B row with no thinking control

#### Scenario: The person's override wins

- **WHEN** the person sets Thinking to Always for `qwen3.5:9b`, whose row default is off
- **THEN** the profile's thinking for every call kind of `qwen3.5:9b` is on

#### Scenario: Same weights, two providers, separate settings

- **WHEN** `gemma4:e4b-mlx` on Ollama and `google/gemma-4-e4b` on LM Studio are resolved
- **THEN** both take the Gemma 4 E4B row, and a refusal stored for one does not appear on the other

### Requirement: Let a fine-tune take its base model's behaviour but never its verdict

WHEN a model has no row of its own and its name or facts identify it as a fine-tune of a known base model, the system SHALL give it the base model's handling, SHALL say "recognised as a fine-tune of <base> (behaviour of <base>)", and SHALL give it the verdict "unknown fine-tune", never the base model's verdict.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a fine-tune keeps its base's chat template and thinking switch, so that handling is right; how well it translates is not inherited, so BookLoom does not vouch for it.

#### Scenario: A Hermes 4 70B build

- **WHEN** the model `hf.co/bartowski/NousResearch_Hermes-4-70B-GGUF:Q4_K_M` is resolved
- **THEN** it gets Llama 3.1's handling, the line "recognised as a fine-tune of Llama 3.1 (behaviour of Llama 3.1)", and the verdict "unknown fine-tune"

### Requirement: Never pick a model's row from a shared architecture name alone

The system SHALL NOT select a model family's built-in row only because the server reports an architecture name that unrelated families share, such as `llama` or `deepseek2`.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: Ollama reports the architecture `llama` for many unrelated GGUF models; treating each as Llama would give it Llama's handling and Llama's language warnings by mistake.

#### Scenario: A Mistral build reported as llama

- **WHEN** Ollama reports `general.architecture: llama` for `hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL`
- **THEN** the model takes the Mistral Small 3.2 row from its name, not a Llama row

#### Scenario: An unknown family reported as llama

- **WHEN** Ollama reports `general.architecture: llama` for `hf.co/someuser/Velemir-Writer-12B-GGUF:Q4_K_M`, whose name matches no row
- **THEN** the model gets the default profile, not a Llama row

### Requirement: Never give a model whose name reads a family of its own another family's row by its architecture

WHEN a model's name reads a family no built-in row knows, the system SHALL NOT give it another family's row or that family's declared languages because its server reports that family's architecture, and SHALL give it the default profile until a row of its own exists.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a model built on another family's architecture may still behave differently — its thinking switch, its template, its languages — so an architecture alone is not proof of handling. An unknown name with no family of its own may still be recognised by its architecture, as the Models tab's "recognised as …, not this exact model" says.

#### Scenario: A new family on a known architecture keeps the default profile

- **WHEN** LM Studio reports the architecture `gemma4` for `nvidia-nemotron-3.5-bolt-e4b`, whose name reads the family `nemotron-bolt`
- **THEN** the model gets the default profile, not the Gemma 4 E4B row, and its declared languages are unknown, not the Nemotron family's

### Requirement: Give an unknown model the safe default profile

WHEN no row matches a model, the system SHALL give it the default profile: no thinking control, answers asked through the prompt rather than a schema, BookLoom's own temperatures, a batch of 2 segments, and the verdict "unknown model".

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a model BookLoom knows nothing about still translates; it gets the handling least likely to fail, and its own facts, when the server reports them, still apply.

#### Scenario: An unknown model on a generic server

- **WHEN** a draft for `mara-7b` with no row and no server facts is sent
- **THEN** the batch holds at most 2 segments, the body has no `response_format` and no reasoning control, and the Models tab shows "unknown model"

### Requirement: Judge a model's quant from its name and its server's facts

The system SHALL judge a model's quant as excellent, good, acceptable, poor, unusable, not measured or not supported from the bundled quant table, by the quant token and its flags from the model's name, the base type from the server's facts (the facts winning when the two disagree on the bit count) and the model's size class; the judgement SHALL never block a choice, change a request or be stored.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d22--quant-and-language-advice`. In plain words: the owner asked to be told how good or bad a model's compressed version is. Small models lose more from compression than large ones, so the size counts too. It is advice only; where it is shown is the `settings` capability's rule.

#### Scenario: A dynamic 4-bit build reported under its base name

- **WHEN** `hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL` is on Ollama, whose `/api/show` reports `Q4_K_M`, and it is a mixture of experts with about 3B active
- **THEN** the quant is shown as `UD-Q4_K_XL`, the size class is mixture of experts, and the judgement is good

#### Scenario: A small model at 4 bits

- **WHEN** `qwen2.5:1.5b` reports `Q4_K_M`
- **THEN** the size class is small and the judgement is acceptable

#### Scenario: A large model on MLX

- **WHEN** `muse-glimmer:30b-nvfp4-dflash` reports `nvfp4` in safetensors format and 32.3B parameters
- **THEN** the size class is large and the judgement is good

#### Scenario: A 2-bit file of a mid-size model

- **WHEN** the model file is `Qwen_Qwen3.5-9B-Q2_K_L.gguf`
- **THEN** the judgement is unusable, and the model can still be chosen

#### Scenario: gpt-oss as shipped

- **WHEN** `gpt-oss:20b` reports `MXFP4`
- **THEN** the size class is mixture of experts and the judgement is good

### Requirement: Say whether a model's maker declares a language

For a model and a language, the system SHALL decide declared, not declared or unknown from the bundled declared-languages table — the model's own row first, else its base model's row, named, else the language list in the server's facts, which may only add a language and is ignored when it lists English alone; nothing SHALL be fetched, and the state SHALL never block.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d22--quant-and-language-advice`. In plain words: the owner asked to be warned when a model's maker does not list the book's languages. "Declared" is what the maker says, not a measurement; only BookLoom's own tests count as tested. Which line is shown, and where, is the `settings` capability's rule.

#### Scenario: A model whose maker lists neither language

- **WHEN** a Russian-to-Ukrainian project uses `llama3.1:8b`
- **THEN** both Russian and Ukrainian are not declared

#### Scenario: A model whose maker lists both

- **WHEN** a Russian-to-Ukrainian project uses `hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL`
- **THEN** both languages are declared

#### Scenario: A list that names one of the two

- **WHEN** a Russian-to-Ukrainian project uses `qwen2.5:1.5b`, whose server lists only `en`
- **THEN** Russian is declared, Ukrainian is not declared, and the server's lone `en` is ignored

#### Scenario: A maker that says nothing

- **WHEN** an English-to-Ukrainian project uses `gemma4:12b-mlx`
- **THEN** both languages are unknown

#### Scenario: A fine-tune inherits its base's list, named

- **WHEN** a Russian-to-Ukrainian project uses `hf.co/bartowski/NousResearch_Hermes-4-14B-GGUF:Q6_K`
- **THEN** both languages are declared through its base model Qwen3, and the base is named

#### Scenario: A family's row is matched by the whole family name, never by its start

- **WHEN** a Russian-to-Ukrainian project uses `nvidia-nemotron-3.5-bolt-e4b`, whose family `nemotron-bolt` has no row, while the Nemotron family's row says neither language is declared
- **THEN** both languages are unknown, not "not declared"

### Requirement: Count a language pair as tested only by BookLoom's own runs

The system SHALL count a model as tested for a source and target language pair only when its bundled model row's evidence names a run BookLoom itself made on that model for that pair, and SHALL count no pair as tested from a maker's list, a base model's row or another model of the family.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d22--quant-and-language-advice`, the owner's decisions that only BookLoom's own tests count as tested and that a language line is not shown for a pair BookLoom tested. In plain words: "tested" means BookLoom measured this model translating this pair; English into Ukrainian says nothing about Russian into Ukrainian. Where the line is shown is the `settings` capability's rule.

#### Scenario: A measured pair is tested, another pair is not

- **WHEN** the bundled row of `gemma4:e4b-mlx` names BookLoom's English-to-Ukrainian runs and nothing else
- **THEN** the model is tested for English → Ukrainian and not for Russian → Ukrainian

#### Scenario: A family member is not tested by its sibling's runs

- **WHEN** `gemma4:12b-mlx` is chosen and only the Gemma 4 E4B and 26B rows name BookLoom's runs
- **THEN** `gemma4:12b-mlx` is tested for no pair

### Requirement: Take model knowledge only from the files bundled with the app

The system SHALL read its quant table and its declared-languages table only from files bundled with the application, each headed with a data version; SHALL never fetch or update them while running; and, IF a bundled file cannot be read, THEN SHALL fail the start-up with `ErrorCode.internal` rather than use an empty table.

**Source**: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#offline-invariant`, `design.md#d22--quant-and-language-advice`. In plain words: the advice must work offline and must not be silently empty; the tables are updated by a new release, and Settings › Models shows their date.

#### Scenario: The data version is known

- **WHEN** the bundled files are headed `# data-version: 2026-10-28`
- **THEN** the data version reported for Settings › Models is `2026-10-28`

#### Scenario: A broken file stops the start

- **WHEN** the bundled quant table has a malformed row
- **THEN** start-up fails with `ErrorCode.internal`, and no network request is made

### Requirement: Wait for a chat call by its kind, its expected output and its model

The system SHALL wait for each attempt of a names-pass prescan call 120 seconds, or for a model that thinks on every call the longer of 120 seconds and 60 ms per expected output token; for every other chat call, the longer of the provider's request timeout and 0.5 s per expected output token, at most 600 seconds, the expected output including any reasoning reserve, and, WHEN the request states none, the request timeout.

**Source**: FR-INFER-05, retry and timeouts (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `#provider-config`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a page from a slow model can need far longer than the three minutes a line needs, so a call that expects a long answer waits longer, up to ten minutes; the configured timeout is the floor, because the first call after a model reloads can take minutes before the first word. A prescan's reply is short and capped, so one still running after two minutes is stuck — unless the model always thinks first, which can take a minute more. The reviewer's fixed 90 seconds and the rolling summary's fixed 120 seconds end here: until those calls are removed, they take the proportional wait like any other call; a proofread call scales like a draft. A timeout set above ten minutes is kept as set. Which calls state an expected output is the `translation-pipeline` capability's rule; the verification calls state none.

#### Scenario: A medium segment gets a proportional wait

- **WHEN** a draft request expects 400 output tokens, the provider has the default request timeout, and the server sends nothing
- **THEN** the attempt ends with `ErrorCode.timeout` once the reply has sent nothing for its idle gap or 200 seconds have passed, whichever comes first

#### Scenario: A short segment keeps the request timeout

- **WHEN** a draft request expects 40 output tokens and the provider has the default request timeout
- **THEN** its attempt waits up to 180 seconds

#### Scenario: A huge segment gets the ceiling

- **WHEN** a draft request expects 2,000 output tokens and the provider has the default request timeout
- **THEN** its attempt waits up to 600 seconds

#### Scenario: A verification call keeps the provider timeout

- **WHEN** the inference stage of verification sends its probe to a provider with the default request timeout
- **THEN** its attempt waits up to 180 seconds

#### Scenario: A prescan call has its own bound

- **WHEN** a prescan call is sent for `gemma4:e4b-mlx` to a provider with the default request timeout, or with a request timeout of 600 seconds
- **THEN** its attempt waits up to 120 seconds

#### Scenario: A prescan for a model that always thinks waits for its reasoning

- **WHEN** a prescan call for `gpt-oss:20b` expects 2,600 output tokens including its reasoning reserve
- **THEN** its attempt waits up to 156 seconds

#### Scenario: A proofread call scales like a draft

- **WHEN** a proofread call expects 800 output tokens and the provider has the default request timeout
- **THEN** its attempt waits up to 400 seconds

#### Scenario: The command line's timeout is the floor

- **WHEN** the translate command runs with `--timeout 30`
- **THEN** a draft request expecting 40 output tokens waits up to 30 seconds
- **AND** a draft request expecting 400 output tokens waits up to 200 seconds

#### Scenario: A long configured timeout is never cut

- **WHEN** the provider's request timeout is 900 seconds and a draft request expects 2,000 output tokens
- **THEN** its attempt waits up to 900 seconds

#### Scenario: The wait is the longer of the request timeout and the proportional bound

- **WHEN** a call other than a prescan states an expected output
- **THEN** it waits `max(request timeout, min(600 s, expected output tokens × 0.5 s))`

#### Scenario: The request timeout is the provider's own

- **WHEN** the provider description sets no timeout and the command line gives no `--timeout`
- **THEN** the request timeout is 180 seconds, and a call other than a prescan never waits less than it

## MODIFIED Requirements

### Requirement: Describe a provider by kind, endpoint and timeouts

The system SHALL describe a provider by an id, a display name, a kind (Ollama-native or OpenAI-compatible), a base URL that is absolute and uses `http` or `https`, a connect and a request timeout, both positive, defaulting to 10 and 180 seconds, and its server engine with the engine's version when known; IF a description breaks one of these rules, THEN the system SHALL refuse it with `ErrorCode.validation` and register nothing.

**Source**: FR-PROV-03, the rule that a provider is configuration (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-config`, `design.md#d17--server-engines-detection-and-traits`. In plain words: a provider is a few settings, not code. The kind chooses how BookLoom talks (Ollama's native API or the OpenAI chat format); the engine says which program answers, so each request carries that program's fields. The engine is Ollama for the Ollama-native kind and generic for an OpenAI-compatible one until detection or the person names another. A key is only ever the name of an environment variable, read at call time, and a remote provider needs no consent step: BookLoom is private when used with local models, and the person decides what leaves the machine.

#### Scenario: A remote OpenAI-compatible endpoint is described by its URL alone

- **WHEN** a provider `openai` named `OpenAI` is described as OpenAI-compatible at `https://api.openai.com/v1` with the default timeouts
- **THEN** the provider is registered under `openai` with the engine generic
- **AND** a request to it carries no `Authorization` header

#### Scenario: A relative base URL is refused

- **WHEN** a provider is described with the base URL `localhost:11434`
- **THEN** the result is `ErrorCode.validation` and nothing is registered

#### Scenario: A zero request timeout is refused

- **WHEN** a provider is described with a request timeout of 0 seconds
- **THEN** the result is `ErrorCode.validation`

#### Scenario: A detected engine is part of the description

- **WHEN** a provider `office` at `http://10.0.0.7:9931/v1` was detected as llama-server `b11669`
- **THEN** looking up `office` returns the engine llama-server and the version `b11669`, and no consent or acknowledgement field

### Requirement: Offer the two local presets and accept registrations

WHERE the application runs from the command line, the system SHALL offer, unconfigured, the provider `ollama` (Ollama-native, engine Ollama, `http://localhost:11434`) and `lmstudio` (OpenAI-compatible, engine LM Studio, `http://localhost:1234/v1`) with the default timeouts; WHERE it runs in the window, the list SHALL start empty and hold only what the first-launch look found and the person adds. A lookup SHALL answer the description or nothing; a registration of a known id SHALL replace it.

**Source**: FR-PROV-02, the two local presets (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-factory`, `#provider-profile`, ADR-0043 (SQLite storage in the window), ADR-0044 (the one first-launch local listing), `design.md#d17--server-engines-detection-and-traits`. In plain words: the command line still starts with the two usual servers and can point at another for one run; it never detects, so its presets name their engines, and LM Studio is spoken to through its own route from the first call. The window lists only real providers: the ones found on this computer and the ones the person saved. The presets keep the address `localhost`; the first-launch look deliberately asks `127.0.0.1`, so it never depends on how the name `localhost` resolves.

#### Scenario: The presets exist from the start

- **WHEN** nothing has been registered on the command line and the provider `lmstudio` is looked up
- **THEN** an OpenAI-compatible description with the engine LM Studio at `http://localhost:1234/v1` with the default timeouts is returned

#### Scenario: A registration overrides a preset for this run

- **WHEN** the command line registers a provider `ollama` at `http://10.0.0.5:11434`
- **THEN** looking up `ollama` in that run returns `http://10.0.0.5:11434`
- **AND** a fresh process again returns `http://localhost:11434`

#### Scenario: An unknown id returns nothing

- **WHEN** the provider `gemini` is looked up
- **THEN** no description is returned and no error is raised

#### Scenario: The window starts with no presets

- **WHEN** the window starts on a first launch, the look finds nothing, and the person has added no provider
- **THEN** the provider list is empty and looking up `ollama` returns nothing

### Requirement: Store what the first-launch look found

WHEN a program asked by the first-launch look answers with at least one model, the system SHALL store it as a provider with the engine its listing shows — Ollama for the `/api/tags` reply, LM Studio for `/v1/models` rows owned by `organization_owner` — and, IF both programs answer, THEN Ollama SHALL become the current provider; it SHALL select the model when there is exactly one and ask the person when there are several. A program that answers with zero models SHALL count as not found.

**Source**: ADR-0044 (the one first-launch local listing), `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", `design.md#d17--server-engines-detection-and-traits`. In plain words: a program with nothing loaded cannot translate, so it is reported as not found rather than as "ready"; a person with several models chooses one instead of BookLoom guessing. The look labels what answered from the two listings it already asks for, with no extra request, so a provider found at first launch is spoken to correctly from its first call.

#### Scenario: Ollama with one model

- **WHEN** `http://127.0.0.1:11434/api/tags` lists only `gemma4:e4b`
- **THEN** an Ollama provider with the engine Ollama is stored with `gemma4:e4b` selected and no question is asked

#### Scenario: Both programs answer

- **WHEN** Ollama lists `gemma4:e4b` and LM Studio lists `google/gemma-4-e4b` with `"owned_by":"organization_owner"`
- **THEN** both providers are stored, the second with the engine LM Studio, and Ollama is the current one

#### Scenario: Several models

- **WHEN** Ollama lists `gemma4:e4b` and `gemma4:26b`
- **THEN** the Ollama provider is stored and the person is asked which model to use

#### Scenario: A program with no models counts as not found

- **WHEN** Ollama answers `/api/tags` with `{"models":[]}` and LM Studio does not answer
- **THEN** no provider is stored and Projects shows `No AI model found`

### Requirement: Shape an Ollama-native chat request

WHEN a chat request goes to an Ollama-native provider, the system SHALL `POST` to `<baseUrl>/api/chat` a streamed body with `model`, `messages`, `options` with `temperature`, the profile's samplers, `num_ctx`, `num_predict` and `seed` each only when given (the object left out when none is), `format` only when the call asks for a schema, `think` only when the reasoning rules give one, `keep_alive` only on a run's calls, and `stop` only as a list; an absent field SHALL be left out, never `null`.

**Source**: FR-INFER-01 to FR-INFER-03, chat calls with their settings (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#effective-context`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: Ollama is spoken to natively because its OpenAI-shaped path drops native controls and hides Ollama's own error bodies. Its default context is small and silently truncates a long prompt, so every call tells it the context size. Which `think` value is sent, which samplers and when the model is kept loaded are the `inference` capability's rules; a model that has no thinking switch gets no `think` field at all, which is what stops the `400` that used to pause runs.

#### Scenario: A full request

- **WHEN** a run's draft with temperature `0.2`, the context size `8192` and a JSON-schema response format is sent to the provider `ollama` for the model `gemma4:e4b-mlx` with reasoning Off
- **THEN** the body posted to `/api/chat` has `"model":"gemma4:e4b-mlx"`, `"stream":true`, an `options` object holding `"temperature":0.2` and `"num_ctx":8192`, and a `"format"` object holding that schema
- **AND** it has `"think":false` and `"keep_alive":"30m"`

#### Scenario: An output cap joins the options

- **WHEN** a request with the context size `8192`, an output cap of `80` and no temperature or samplers is sent
- **THEN** the body has `"options":{"num_ctx":8192,"num_predict":80}`

#### Scenario: A context size alone

- **WHEN** a request with the context size `8192`, no temperature and no samplers is sent
- **THEN** the body has `"options":{"num_ctx":8192}`

#### Scenario: Absent settings are left out

- **WHEN** a request with no temperature, no samplers, no context size and no response format is sent
- **THEN** the body has no `options` key and no `format` key

#### Scenario: Low and On reasoning are sent as think

- **WHEN** a request with reasoning On is sent for `qwen3.5:9b`, whose switch is true or false, and one with reasoning Low for `gpt-oss:20b`, whose levels are `low`, `medium` and `high`
- **THEN** the first body has `"think":true` and the second `"think":"low"`

#### Scenario: A model without a thinking switch gets no think field

- **WHEN** a draft for `qwen2.5:1.5b`, whose `/api/show` capabilities are `["completion","tools"]`, is sent with reasoning Off
- **THEN** the body has no `think` key

#### Scenario: A stop list is a list

- **WHEN** a request carries the stop text `</items>`
- **THEN** the body has `"stop":["</items>"]`, never a string

### Requirement: Shape an OpenAI-compatible chat request

WHEN a chat request goes to an OpenAI-compatible provider, the system SHALL post to the stored engine's chat route (`<baseUrl>/chat/completions` or the inference endpoint override, unless the engine has its own) an unstreamed JSON body with `model`, `messages`, `"stream":false`, `temperature` only when given, `response_format` as a strict `json_schema` only when the call asks for a schema, and every field the engine requires on every call; an absent field SHALL be left out, never `null`.

**Source**: FR-INFER-01, FR-INFER-03, FR-INFER-09, chat calls and reply handling (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`, `design.md#d17--server-engines-detection-and-traits`. In plain words: one client covers LM Studio, llama-server, Unsloth, OpenRouter and any other OpenAI-shaped server, but each engine wants its own fields for the same wish. The reasoning switch and the samplers are the `inference` capability's rules; LM Studio's own route, Unsloth's tool switch and OpenRouter's fields are separate requirements here. `json_object` is never sent.

#### Scenario: A request with structured output and no reasoning parameter

- **WHEN** a request with temperature `0.2` and the response format named `draft` is sent to the provider `lmstudio` for the model `mara-7b`, which has no thinking switch and whose stored verdict says structured answers are honoured
- **THEN** the posted body has `"model":"mara-7b"`, `"stream":false`, `"temperature":0.2` and `"response_format":{"type":"json_schema","json_schema":{"name":"draft","strict":true,...}}`
- **AND** it has no `reasoning_effort` key

#### Scenario: Absent settings are left out

- **WHEN** a request with no temperature and no response format is sent
- **THEN** the body has neither a `temperature` nor a `response_format` key

#### Scenario: A call on the prompt-only rung carries no schema

- **WHEN** a draft whose call kind has stepped down to answers asked through the prompt is sent to llama-server
- **THEN** the body has no `response_format` key

### Requirement: Read a reply and report its finish

WHEN a provider answers `200`, the system SHALL take the reply text from `message.content` (Ollama) or `choices[0].message.content` (OpenAI-compatible), the finish from `done_reason` or `finish_reason` (`stop` normal, `length` cut off by length, else other) and the reported usage; a reasoning field or `tool_calls` SHALL never reach the text; IF the text is blank before scrubbing, THEN the answer SHALL be `ErrorCode.emptyCompletion`.

**Source**: FR-INFER-09, reply handling (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `#empty-response-ordering`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: both dialects collapse to text, a finish reason and, when the server says so, how many tokens it read and wrote. The thinking a model writes in its own field (Ollama `message.thinking`, `message.reasoning` on Ollama's OpenAI path and OpenRouter, `reasoning_content` on LM Studio, llama-server and Unsloth) is now read, but only to notice a reply that spent its cap thinking and to time the first token; it is never part of the translation. On an engine that does not serve unknown ids, a reply naming another model is used and one WARN line names both ids; where any id is served, that reply is refused by a separate requirement.

#### Scenario: An Ollama reply that stopped normally

- **WHEN** Ollama answers `{"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":"{\"target\":\"Привіт\"}","thinking":"Let me think"},"done":true,"done_reason":"stop"}`
- **THEN** the reply text is `{"target":"Привіт"}` with a normal finish, and the thinking text is not in it

#### Scenario: Ollama's usage is read

- **WHEN** Ollama answers with `"prompt_eval_count":812`, `"eval_count":96` and `"eval_duration":3200000000`
- **THEN** the reply carries 812 prompt tokens, 96 completion tokens and a generation time of 3.2 seconds

#### Scenario: OpenAI-compatible usage is read with the wall-clock time

- **WHEN** LM Studio answers after 2.5 seconds with `"usage":{"prompt_tokens":640,"completion_tokens":75,"total_tokens":715}` and no `stats`
- **THEN** the reply carries 640 prompt tokens, 75 completion tokens and a generation time of 2.5 seconds

#### Scenario: A reply without usage

- **WHEN** an OpenAI-compatible server answers with no `usage` object
- **THEN** the reply carries no usage

#### Scenario: An Ollama reply cut off by length

- **WHEN** Ollama answers with `"done_reason":"length"`
- **THEN** the finish is cut off by length

#### Scenario: An Ollama reply that only loaded the model

- **WHEN** Ollama answers with `"done_reason":"load"` and an empty `message.content`
- **THEN** the result is `ErrorCode.emptyCompletion`

#### Scenario: An OpenAI-compatible reply with a separate reasoning channel

- **WHEN** llama-server answers `{"choices":[{"message":{"role":"assistant","content":"{\"target\":\"Привіт\"}","reasoning_content":"thinking..."},"finish_reason":"stop"}]}`
- **THEN** the reply text is `{"target":"Привіт"}` with a normal finish

#### Scenario: The server answered with a different model

- **WHEN** the model `qwen3.5:9b` was requested and Ollama's OpenAI path answers `200` with `"model":"qwen3.5:9b-q4"`
- **THEN** the reply is used
- **AND** one WARN line names `qwen3.5:9b` and `qwen3.5:9b-q4`

#### Scenario: A blank reply

- **WHEN** the server answers `200` with `content` holding two spaces and a line feed
- **THEN** the result is `ErrorCode.emptyCompletion`

### Requirement: Map every transport and HTTP outcome to one error code

WHEN a call fails, the system SHALL answer exactly one error code from the first matching row of one ordered catalogue: interruption, timeout and I/O failure first; then rows matched on the meaning of the body's text in any word order, whatever its status; then the status alone (`401`/`403` auth, `404` modelNotFound, `429` rateLimited, `5xx` upstream, another `400` validation, anything else internal). The details SHALL hold only allowlisted values.

**Source**: FR-INFER-07, typed failures (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`, `#safe-details-allowlist`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: the engine, the screens and the retry policy branch on a code, never on a message. A body says more than a status: LM Studio sends a stopped reply as a `400`, OpenRouter sends errors with `200`, and an overflow can name the verb before the word "context". The allowlisted details are the HTTP status, the endpoint host, the model id, the timeout, the attempt count, the reason token and the typed counts of an overflow; no header, body or secret. No new error code is added. A `200` whose body cannot be read is `upstream`, as "Classify a reply that answered but cannot be read as upstream" says.

#### Scenario: Connection refused

- **WHEN** nothing listens on `http://localhost:11434` and a chat call is made to `ollama`
- **THEN** the result is `ErrorCode.unreachable`, marked retryable

#### Scenario: A request timeout

- **WHEN** the request timeout is 2 seconds and the server sends nothing for 3 seconds
- **THEN** the result is `ErrorCode.timeout`, with the details naming the 2-second timeout

#### Scenario: An unknown Ollama model

- **WHEN** Ollama answers `404` with the body `{"error":"model 'nope:latest' not found"}`
- **THEN** the result is `ErrorCode.modelNotFound` with reason `model-not-pulled`, not retryable

#### Scenario: A refused request

- **WHEN** the server answers `401`
- **THEN** the result is `ErrorCode.auth`, and the response body is not in the error

#### Scenario: A context overflow

- **WHEN** the server answers `400` with the body `{"error":{"message":"This model's maximum context length is 8192 tokens; your request exceeds it","code":"context_length_exceeded"}}`
- **THEN** the result is `ErrorCode.contextWindow`

#### Scenario: A context overflow with the verb first

- **WHEN** the server answers `400` with the body `{"error":"input length 9120 is greater than the context length 8192"}`
- **THEN** the result is `ErrorCode.contextWindow`

#### Scenario: Any other 400

- **WHEN** the server answers `400` with the body `{"error":"invalid option: temperature"}` to a request that sent no temperature
- **THEN** the result is `ErrorCode.validation`

#### Scenario: A server fault

- **WHEN** the server answers `503` with an empty body
- **THEN** the result is `ErrorCode.upstream`, marked retryable, with the details naming status `503`

#### Scenario: A body that is not the dialect's reply

- **WHEN** the server answers `200` with the body `<html>proxy error</html>`
- **THEN** the result is `ErrorCode.upstream`, not `internal`

#### Scenario: An error envelope with a numeric code

- **WHEN** a generic server answers `400` with `{"object":"error","code":400,"message":"max_tokens is too large"}`
- **THEN** the envelope is read like its string form, and the reason is not `unclassified` for the number alone

### Requirement: Retry a retryable failure a bounded number of times

WHEN a call answers a retryable code, the system SHALL try again up to 3 attempts in all, each with its full request timeout, waiting the `Retry-After` delay when given (seconds or an HTTP date), else 500 ms doubled per retry, capped at 8 s, with up to 25 % jitter; WHERE the matched catalogue row has its own retry rule (once, once with a new seed, or never), that rule SHALL decide instead; a non-retryable code SHALL NOT be repeated.

**Source**: FR-INFER-05, retry (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a local server that is still loading a model, or a rate-limited cloud endpoint, deserves a second try; a wrong key or an unknown model does not. Some failures need a different retry: a stopped reply or a crashed runner gets one more try, a looping model one more try with a new seed, and a model that does not fit in memory none. The retryable codes are `timeout`, `rateLimited`, `unreachable`, `upstream` and `discoveryFailed`. A repeat after a refused setting is not an attempt; that rule belongs to the `inference` capability.

#### Scenario: Two faults then success

- **WHEN** the server answers `503` twice and then `200` with a normal reply
- **THEN** the call succeeds after 3 requests

#### Scenario: Three faults exhaust the attempts

- **WHEN** the server answers `503` three times
- **THEN** the result is `ErrorCode.upstream` and the server received exactly 3 requests

#### Scenario: Retry-After is honoured

- **WHEN** the server answers `429` with the header `Retry-After: 2` and then `200`
- **THEN** the second request is sent no sooner than 2 seconds after the first answer

#### Scenario: A non-retryable code is not repeated

- **WHEN** the server answers `401`
- **THEN** the server received exactly 1 request

#### Scenario: The wait doubles

- **WHEN** a call fails with `503` three times with no `Retry-After`
- **THEN** the wait before the second request is about 500 ms and before the third about 1 s, each within 25 % jitter

#### Scenario: Retry-After as an HTTP date

- **WHEN** the server answers `429` with a `Retry-After` date 3 seconds ahead and then `200`
- **THEN** the second request is sent no sooner than that date

#### Scenario: A crashed runner gets one more try

- **WHEN** Ollama answers `500 {"error":"model runner has unexpectedly stopped"}` twice
- **THEN** the result is `upstream` with reason `runner-crashed` and the server received exactly 2 requests

### Requirement: Discover the models a provider offers

WHEN discovery is asked, the system SHALL read the engine's model listing (Ollama `/api/tags`, LM Studio `/api/v0/models`, otherwise `<baseUrl>/models` or the override) and return only chat models, leaving out every entry that its listing, facts or name show is an embedding, image, video, speech or reranker model or a sidecar file such as `mmproj-*.gguf`, and labelling an Ollama cloud model `cloud`; an empty listing SHALL be a success with no models.

**Source**: FR-MODEL-01, FR-MODEL-02, model discovery (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#model-discovery`, `design.md#d19--model-identity-reading-model-names`, the owner's decision that Ollama cloud models are listed with a neutral "cloud" label, never hidden and never behind a consent step. In plain words: a picker should offer only models that can translate; an embedding model or a vision projector file chosen by mistake fails on the first segment. LM Studio's OpenAI listing says nothing about type, so its native listing is read; its `llm` and `vlm` entries are chat models. An empty list is not an error, and manual entry stays possible. A failure other than `auth`, `unreachable`, `timeout` or `cancelled`, or a body that is not a listing, is `discoveryFailed`.

#### Scenario: Ollama lists its models

- **WHEN** `/api/tags` answers `{"models":[{"name":"gemma4:e4b-mlx","model":"gemma4:e4b-mlx","size":1},{"name":"qwen2.5:1.5b","model":"qwen2.5:1.5b","size":2}]}`
- **THEN** discovery returns the ids `gemma4:e4b-mlx` and `qwen2.5:1.5b` in that order

#### Scenario: LM Studio lists its models

- **WHEN** `/api/v0/models` answers `{"object":"list","data":[{"id":"google/gemma-4-e4b","type":"vlm"},{"id":"lapa-v0.1.2-instruct","type":"llm"},{"id":"text-embedding-nomic-embed-text-v1.5","type":"embeddings"}]}`
- **THEN** discovery returns `google/gemma-4-e4b` and `lapa-v0.1.2-instruct` only

#### Scenario: A vision projector file is not a model

- **WHEN** a llama-server router's `/v1/models` lists `Qwen3.5-9B-Q4_K_M.gguf` and `mmproj-BF16.gguf`
- **THEN** discovery returns `Qwen3.5-9B-Q4_K_M.gguf` only

#### Scenario: An Ollama cloud model is labelled, not hidden

- **WHEN** `/api/tags` lists `gemma4:e4b-mlx` and `gpt-oss:120b-cloud`
- **THEN** discovery returns both, and `gpt-oss:120b-cloud` carries the label `cloud`

#### Scenario: A listing that is not a listing

- **WHEN** `/v1/models` answers `200` with the body `{"data":"nope"}`
- **THEN** the result is `ErrorCode.discoveryFailed`

#### Scenario: An unreachable server is reported as such

- **WHEN** nothing listens at the base URL
- **THEN** discovery answers `ErrorCode.unreachable`, not `discoveryFailed`

### Requirement: Verify a provider and a model in three stages

WHEN verification of a provider and a model is asked for, the system SHALL run in order, up to the stage asked for and stopping at the first failure, the connection stage (the probe), the models stage (listed passes, unlisted fails `modelUnavailable`, an unreadable or empty list passes softly with a note) and the inference stage (a probe for exactly `{"status":"ok"}` sent as a run sends a call to that model, capped at 64), reporting each stage with its outcome and duration.

**Source**: FR-PROV-06, FR-PROV-07, FR-MODEL-05, FR-MODEL-06, FR-INFER-08, the staged verification (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: three short questions, each with its own answer, so a person sees which one failed: the server, the model name, or generation itself. The probe uses the model's own profile — its reasoning-off value, BookLoom's own system message, its message shaping, and the reasoning reserve added to the cap for a model that always thinks — so a thinking model that spends a capped reply on reasoning fails here instead of flagging a book later. Where any id is served, the models stage is the id check of its own requirement. An unknown provider id with no description given is `ErrorCode.validation` and runs no stage.

#### Scenario: All three stages pass

- **WHEN** `ollama` is verified with `gemma4:e4b-mlx`, `/api/version` answers `200` after 41 ms, `/api/tags` lists 3 models including `gemma4:e4b-mlx`, and `/api/chat` answers `{"status":"ok"}` with a normal finish after 1.2 s
- **THEN** the report holds three passed stages in the order connection, models, inference
- **AND** the connection stage reports about 41 ms, the models stage reports 3 models, and the inference stage reports about 1.2 s and `structured output: supported`

#### Scenario: The probe uses the model's own off value

- **WHEN** `ollama` is verified with `gpt-oss:20b`
- **THEN** the inference probe's body has `"think":"low"`, never `false`, and its `num_predict` is at least 2,048, the cap of 64 raised by the reasoning reserve

#### Scenario: The model is not in the list

- **WHEN** `/api/tags` lists only `qwen2.5:1.5b` and `gemma4:e4b-mlx` is verified
- **THEN** the report holds a passed connection stage and a models stage failed with `ErrorCode.modelUnavailable`, reporting 1 model
- **AND** no chat call is made

#### Scenario: Discovery fails softly and inference decides

- **WHEN** a generic server's `/v1/models` answers `200 {"data":"nope"}` and `/v1/chat/completions` answers `{"status":"ok"}`
- **THEN** the models stage is reported as a soft pass noting `discoveryFailed`
- **AND** the inference stage passes

#### Scenario: An unknown model on a manually typed id

- **WHEN** discovery answered an empty list and `/api/chat` answers `404 {"error":"model 'nope' not found"}`
- **THEN** the models stage is a soft pass noting that the list could not be read, and the inference stage fails with `ErrorCode.modelUnavailable`

#### Scenario: One stage on its own

- **WHEN** only the connection check is asked for `lmstudio` and `/v1/models` answers `200` after 12 ms
- **THEN** the report holds one passed connection stage reporting about 12 ms
- **AND** no chat call is made

#### Scenario: A models check runs the connection stage first

- **WHEN** the models check is asked for `lmstudio` with `google/gemma-4-e4b`, the connection probe answers `200` after 12 ms and `/api/v0/models` lists 2 models including `google/gemma-4-e4b`
- **THEN** the report holds a passed connection stage reporting about 12 ms and a passed models stage reporting 2 models, in that order
- **AND** no chat request is sent

#### Scenario: A reasoning model spends the cap

- **WHEN** the inference probe's reply is empty twice because the cap was used on reasoning
- **THEN** the inference stage fails with `ErrorCode.emptyCompletion`

#### Scenario: An unknown provider runs no stage

- **WHEN** verification is asked for the provider id `gemini`, which is not registered, with no description given
- **THEN** the answer is `ErrorCode.validation` and no request is sent

### Requirement: Log every call without its secret or its text

The system SHALL write, for every chat, discovery, probe, detection and verification call: an INFO line when a verification or an engine detection ends, with its outcome; a DEBUG line with the provider id, engine, endpoint host, model id, call kind, attempt, HTTP status, reply length and the cached prompt tokens when reported; a WARN line with the reason token for every retry, refusal, model mismatch and failed stage; and the request and response bodies at TRACE only, never a credential.

**Source**: FR-INFER-10, call logging (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), NFR-PRIV-04 and NFR-PRIV-06, no book text or secret in logs (`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#no-telemetry`, `#secrets-never-stored`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-construction`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: a wrong translation is explained from the log, so every call leaves a line saying what was asked and what came back, in sizes, codes and reasons. The book's words and the model's words appear only when someone turns TRACE on. A key never appears, whatever the level; the detection line names the host only, never a path, a query or a key.

#### Scenario: A TRACE run shows the bodies

- **WHEN** a chat call to the provider `lmstudio` runs at level TRACE
- **THEN** the log holds the request body and the response body

#### Scenario: A DEBUG run shows sizes and codes only

- **WHEN** the same call runs at level DEBUG and the server answers `503` then `200`
- **THEN** the log holds a DEBUG line with attempt `1` and status `503`, a WARN line for the retry with `reason=unclassified`, and a DEBUG line with attempt `2` and status `200`
- **AND** the log holds no request or response body

#### Scenario: A detection is logged by host

- **WHEN** the person tests a provider `office` at `http://10.0.0.7:9931/v1?token=abc` and llama-server `b11669` is found after 3 probes
- **THEN** the log holds an INFO line `engine detected provider=office engine=LLAMA_CPP version=b11669 probes=3 source=detected` and no `token=abc`

### Requirement: Read an Ollama-native reply as a stream that must keep arriving

The system SHALL read an Ollama-native reply as a stream, joining every line's `message.content` and, apart, its `message.thinking`, and taking the finish and token counts from the last line; WHEN no line arrives for the idle gap, or the reply runs past the attempt's timeout, the attempt SHALL end with `timeout`; a line with an `error` field SHALL be classified by its text, and a last line with an empty `done_reason` SHALL be `upstream` with the reason `stream-truncated`.

**Source**: FR-INFER-04a and FR-INFER-05, streamed replies and timeouts (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `design.md#d18--error-catalogue-by-meaning-and-the-call-ledger`. In plain words: the JDK's request timeout stops counting once the reply begins, so a stream shows whether the model is still writing and a stall is caught within a minute. A thinking model's stream starts with thinking lines; they are kept apart from the text and still count as the model being alive. An error line can mean an overflow, a loop or a crash, so it is read like any other error body; a stream that ends without a reason was cut off and gets one more try. An interrupted caller ends the attempt at once with `cancelled`.

#### Scenario: Lines are joined in order

- **WHEN** an Ollama-native reply streams several lines
- **THEN** the `message.content` of the lines is joined in the order the lines arrived

#### Scenario: A streamed reply reads as one

- **WHEN** Ollama streams the lines `Hel`, `lo` and a last line with `done_reason` `stop`, 12 prompt tokens and 2 completion tokens
- **THEN** the reply is `Hello`, finished normally, with those token counts

#### Scenario: Thinking lines stay apart

- **WHEN** Ollama streams two lines with only `message.thinking` and then the lines `При` and `віт`
- **THEN** the reply text is `Привіт` and the thinking text is not in it

#### Scenario: A stream that stops sending

- **WHEN** the provider's request timeout is 1 second, a draft expecting 400 tokens is sent, and the stream sends its second line 8 seconds after its first
- **THEN** the attempt ends with `ErrorCode.timeout` after about 1 second of silence, not after 200 seconds

#### Scenario: A pause during a stalled stream

- **WHEN** the calling thread is interrupted while the stream sends nothing
- **THEN** the attempt ends at once with `ErrorCode.cancelled`

#### Scenario: The idle gap is 60 seconds or a shorter request timeout

- **WHEN** no line arrives before the first line or between two lines
- **THEN** the idle gap is 60 seconds, or the provider's request timeout when that is shorter
- **AND** a timed-out attempt closes the connection

#### Scenario: An error line ends the attempt

- **WHEN** a streamed line carries `{"error":"token repeat limit reached"}`
- **THEN** the attempt ends with `ErrorCode.upstream` and reason `repeat-limit`

#### Scenario: A stream cut off without a reason

- **WHEN** the last line has `"done":true` and `"done_reason":""`
- **THEN** the attempt ends with `ErrorCode.upstream` and reason `stream-truncated`, and the request is sent once more

#### Scenario: The caller still receives one whole reply

- **WHEN** a caller asks a model for an Ollama-native or an OpenAI-compatible reply
- **THEN** the call is synchronous and no caller sees a partial reply, and every OpenAI-compatible request asks for a whole reply

### Requirement: Detect a model's context length, optionally and without failing

WHEN asked for a model's context length, the system SHALL read it from the stored engine's facts — Ollama's `/api/show` and its loaded size in `/api/ps`, LM Studio's `/api/v0/models/<id>` preferring the loaded length, llama-server's per-slot `meta.n_ctx`, Unsloth's `/v1/models` row, OpenRouter's endpoint, or a generic listing's `max_model_len` or `context_length` — and answer unknown when the server cannot say; no outcome is an error, and no chat call triggers it.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#effective-context`, `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#effective-context`, `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the window a model really has decides how much of the prompt it may be given, and on LM Studio and llama-server it is fixed when the model is loaded or the server starts, so the loaded figure counts, not the model's maximum. A server that cannot say must never stop a run; the one context size then falls back to its default. Detection is user-triggered provider communication, never a background call.

#### Scenario: Ollama reports its context length

- **WHEN** `/api/show` answers `{"model_info":{"general.architecture":"gemma3","gemma3.context_length":131072}}`
- **THEN** the detected length is 131072

#### Scenario: LM Studio prefers the loaded length

- **WHEN** `/api/v0/models/google/gemma-4-e4b` answers `max_context_length` 131072 and `loaded_context_length` 4096
- **THEN** the detected length is 4096

#### Scenario: llama-server's window is per slot

- **WHEN** a llama-server started with a 32,768 window and 2 slots lists the model with `"meta":{"n_ctx":16384,"n_ctx_train":131072}`
- **THEN** the detected length is 16384

#### Scenario: A llama-server router's own zero is never a window

- **WHEN** a llama-server router's `/props` answers `{"role":"router","n_ctx":0}` and the model's own listing row has `"meta":{"n_ctx":8192}`
- **THEN** the detected length is 8192

#### Scenario: A server that cannot say

- **WHEN** the server answers 404 or 500, answers a body without the field, or refuses the connection
- **THEN** the length is unknown and the result is not an error

#### Scenario: Where the figure is read

- **WHEN** the figure is read from Ollama, from LM Studio or from Unsloth
- **THEN** it is the loaded size from `/api/ps` or else the `<architecture>.context_length` entry of `model_info` for Ollama; for LM Studio the native endpoint outside the configured `/v1` base, preferring `loaded_context_length` over `max_context_length`; for Unsloth the model's `/v1/models` row, preferring `context_length` over `max_context_length`

#### Scenario: A server that is not that server at all

- **WHEN** the server is not reachable, answers a non-200 status, answers a body without the field or is not that server at all
- **THEN** the length is unknown and no error is returned

#### Scenario: An unregistered provider id is refused without a request

- **WHEN** the provider id is not registered
- **THEN** the result is a `validation` error and no request is sent

### Requirement: Fall back to a plain probe when structured output is not confirmed

IF the inference stage's structured probe is rejected or answered nonconformingly, THEN the system SHALL send one plain probe and, for a nonblank reply, report `structured output: not confirmed` and store on the model the verdict refused or ignored with the source `test`; a `modelNotFound` answer SHALL be reported as `modelUnavailable` and any other error as itself.

**Source**: FR-INFER-08, the inference probe (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a server that cannot do structured answers can still translate, so the test says "not confirmed" rather than "failed". The verdict is kept on the model, so later calls ask through the prompt from the start instead of failing once per call kind.

#### Scenario: A server without structured output

- **WHEN** the structured probe is rejected with `400` and the plain probe answers `OK`
- **THEN** the stage passes, reports `structured output: not confirmed`, and the model's stored verdict is "structured answers: refused", source `test`

#### Scenario: A schema answered with something else

- **WHEN** the structured probe is answered `200` with `{"state":"fine"}` and the plain probe answers `OK`
- **THEN** the stage passes and the model's stored verdict is "structured answers: ignored", source `test`

## REMOVED Requirements

### Requirement: Turn reasoning off for an OpenAI-compatible call, and retry a reply that spent its cap reasoning

**Reason**: One `reasoning_effort` value for every OpenAI-compatible server is wrong for most engines: llama-server reads `chat_template_kwargs`, OpenRouter a `reasoning` object, Unsloth a top-level `enable_thinking`, gpt-oss must never get `none`, and a model without a thinking switch must get no control at all. The reasoning level also no longer comes from Settings › Generation, and the re-ask after a reply that spent its cap thinking now works the same on Ollama's native API.

**Migration**: Replaced by the `inference` requirements "Send reasoning Off as the engine's own off value", "Send no thinking control that the model or the engine does not declare", "Choose the reasoning setting per call kind from the model profile" and "Ask once more when a reply spent its whole answer thinking".

### Requirement: Choose a chat call's timeout by its kind and its expected output

**Reason**: Its fixed bounds belong to calls that this change removes — the reviewer's 90 seconds and the rolling summary's 120 seconds — and a model that thinks on every call needs a longer wait than any fixed bound gives.

**Migration**: Replaced by "Wait for a chat call by its kind, its expected output and its model", which keeps the proportional wait, the floor and the ceiling, and the prescan's 120 seconds.
