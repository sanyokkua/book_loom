# llm-provider Specification

## Purpose
How BookLoom describes a language-model provider and talks to it: the two server dialects (Ollama-native and
OpenAI-compatible), how a request is shaped and a reply is read, how every transport and HTTP outcome becomes one typed
error, retry, the single-flight gate, model discovery, and the three-stage verification that runs before a model is
trusted. Nothing here names a book; the translation engine only ever sees a chat model.

## Requirements

### Requirement: Describe a provider by kind, endpoint and timeouts

The system SHALL describe a provider by an id, a display name, a kind (Ollama-native or OpenAI-compatible), a base URL that is absolute and uses `http` or `https`, and a connect timeout and a request timeout, both positive, defaulting to 10 seconds and 180 seconds; IF a description breaks one of these rules, THEN the system SHALL refuse it with `ErrorCode.validation` and register nothing.

**Source**: FR-PROV-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-config`, ADR-0005, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a provider is a few settings, not code. The same OpenAI-compatible client serves LM Studio on this machine and any other OpenAI-shaped server. A key is only ever the name of an environment variable, read at call time.

#### Scenario: A remote OpenAI-compatible endpoint is described by its URL alone

- **WHEN** a provider `openai` named `OpenAI` is described as OpenAI-compatible at `https://api.openai.com/v1` with the default timeouts
- **THEN** the provider is registered under `openai`
- **AND** a request to it carries no `Authorization` header

#### Scenario: A relative base URL is refused

- **WHEN** a provider is described with the base URL `localhost:11434`
- **THEN** the result is `ErrorCode.validation` and nothing is registered

#### Scenario: A zero request timeout is refused

- **WHEN** a provider is described with a request timeout of 0 seconds
- **THEN** the result is `ErrorCode.validation`

### Requirement: Offer the two local presets and accept registrations

WHERE the application runs from the command line, the system SHALL offer without configuration the provider `ollama` (Ollama-native, `http://localhost:11434`) and the provider `lmstudio` (OpenAI-compatible, `http://localhost:1234/v1`) with the default timeouts; WHERE it runs in the window, the provider list SHALL start empty and hold only what the first-launch look found and what the person adds. A lookup SHALL answer the description or nothing, and a registration of a known id SHALL replace it.

**Source**: FR-PROV-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-factory`, `#provider-profile`, ADR-0043, ADR-0044, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13". In plain words: the command line still starts with the two usual servers and can point at another for one run. The window lists only real providers: the ones found on this computer and the ones the person saved. The presets keep the address `localhost`; the first-launch look deliberately asks `127.0.0.1`, so it never depends on how the name `localhost` resolves.

#### Scenario: The presets exist from the start

- **WHEN** nothing has been registered on the command line and the provider `lmstudio` is looked up
- **THEN** an OpenAI-compatible description at `http://localhost:1234/v1` with the default timeouts is returned

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

### Requirement: Shape an Ollama-native chat request

WHEN a chat request goes to an Ollama-native provider, the system SHALL `POST` to `<baseUrl>/api/chat` a JSON body with `model`, `messages` (roles `system`, `user`, `assistant`), `"stream":true`, `options` holding `temperature`, `num_ctx`, `num_predict` and `seed` each only when given (the object left out when none is), `format` only when a response format is given, and `think` for the request's reasoning level; an absent field SHALL be left out, never `null`, and no other option SHALL be sent.

**Source**: FR-INFER-01, FR-INFER-02, FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`, `#effective-context`, S4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: Ollama is spoken to natively because its OpenAI-shaped shim drops native controls and hides Ollama's own error bodies. Its default context is small and silently truncates a long prompt, so a run tells it the context size it needs. The reasoning level is sent as `"think":false` for Off, `"think":"low"` for Low and `"think":true` for On; no `keep_alive` is sent and `/api/show` is not read by a chat call.

#### Scenario: A full request

- **WHEN** a request with temperature `0.2`, the context size `8192` and a JSON-schema response format is sent to the provider `ollama` for the model `gemma4:e4b-mlx` with reasoning Off
- **THEN** the body posted to `/api/chat` has `"model":"gemma4:e4b-mlx"`, `"stream":true`, `"options":{"temperature":0.2,"num_ctx":8192}` and a `"format"` object holding that schema
- **AND** it has `"think":false`, no `keep_alive` key, and `/api/show` is never requested by the chat call

#### Scenario: An output cap joins the options

- **WHEN** a request with the context size `8192` and an output cap of `80` is sent
- **THEN** the body has `"options":{"num_ctx":8192,"num_predict":80}`

#### Scenario: A context size alone

- **WHEN** a request with the context size `8192` and no temperature is sent
- **THEN** the body has `"options":{"num_ctx":8192}`

#### Scenario: Absent settings are left out

- **WHEN** a request with no temperature, no context size and no response format is sent
- **THEN** the body has no `options` key and no `format` key

#### Scenario: Low and On reasoning are sent as think

- **WHEN** a request with reasoning Low is sent to the provider `ollama`, and then one with reasoning On
- **THEN** the first body has `"think":"low"` and the second `"think":true`

### Requirement: Shape an OpenAI-compatible chat request

WHEN a chat request goes to an OpenAI-compatible provider, the system SHALL `POST` to `<baseUrl>/chat/completions` (or the inference endpoint override) a JSON body with `model`, `messages` (roles `system`, `user`, `assistant`), `"stream":false`, `temperature` only when one is given, and `response_format` as `{"type":"json_schema","json_schema":{"name":<name>,"strict":true,"schema":<schema>}}` only when a response format is given; an absent field SHALL be left out, never `null`.

**Source**: FR-INFER-01, FR-INFER-03, FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`, ADR-0013. In plain words: one client covers LM Studio, llama.cpp, vLLM and any other OpenAI-shaped server. The reasoning parameter is sent only as "Turn reasoning off for an OpenAI-compatible call, and retry a reply that spent its cap reasoning" states: never guessed, only the level the person chose.

#### Scenario: A request with structured output and no reasoning parameter

- **WHEN** a request with temperature `0.2`, the response format named `draft` and reasoning left to the provider's default is sent to the provider `lmstudio` for the model `google/gemma-4-e4b`
- **THEN** the body posted to `/v1/chat/completions` has `"model":"google/gemma-4-e4b"`, `"stream":false`, `"temperature":0.2` and `"response_format":{"type":"json_schema","json_schema":{"name":"draft","strict":true,...}}`
- **AND** it has no `reasoning_effort` key

#### Scenario: Absent settings are left out

- **WHEN** a request with no temperature and no response format is sent
- **THEN** the body has neither a `temperature` nor a `response_format` key

### Requirement: Read a reply and report its finish

WHEN an Ollama-native provider answers `200` to `/api/chat`, the system SHALL take the reply text from
`message.content` and the finish from `done_reason`: `stop` is a normal finish, `length` is cut off by length, and
anything else is other. A `message.thinking` field SHALL be ignored. The system SHALL take the token usage from
`prompt_eval_count` (prompt tokens), `eval_count` (completion tokens) and `eval_duration` (generation time, in
nanoseconds), each only when present.

WHEN an OpenAI-compatible provider answers `200` to `/chat/completions`, the system SHALL take the reply text from
`choices[0].message.content` and the finish from `choices[0].finish_reason`: `stop` is a normal finish, `length` is cut
off by length, and anything else is other. A `reasoning` or `reasoning_content` field and `tool_calls` SHALL never
reach the reply text; the reasoning fields are read only to notice a reply that spent its cap reasoning (above). The system
SHALL take the token usage from `usage.prompt_tokens` and `usage.completion_tokens`, each only when present, and SHALL
use the call's measured wall-clock time as the generation time when either is present.

IF the body's `model` names a model other than the requested one, THEN the system SHALL still use the reply and SHALL
write one WARN line naming both ids.

IF the reply text is blank before any sanitising, THEN the system SHALL answer `ErrorCode.emptyCompletion`.

**Source**: FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `#empty-response-ordering`, ADR-0013. In plain words: both dialects collapse to text, a finish reason and, when the server says so, how many tokens it read and wrote and how long it took — which is where the Translating screen's tokens per second comes from. Ollama measures its own generation time; an OpenAI-compatible server does not, so the whole call's duration stands in for it. A reply carrying no usage field carries no usage at all (the `inference` capability). A separate reasoning channel is ignored. LM Studio answers a request for an unknown model id with whichever model is loaded, so the mismatch is logged rather than trusted silently.

#### Scenario: An Ollama reply that stopped normally

- **WHEN** Ollama answers `{"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":"{\"target\":\"Привіт\"}",
  "thinking":"Let me think"},"done":true,"done_reason":"stop"}`
- **THEN** the reply text is `{"target":"Привіт"}` with a normal finish, and the thinking text appears nowhere

#### Scenario: Ollama's usage is read

- **WHEN** Ollama answers with `"prompt_eval_count":812`, `"eval_count":96` and `"eval_duration":3200000000`
- **THEN** the reply carries 812 prompt tokens, 96 completion tokens and a generation time of 3.2 seconds

#### Scenario: OpenAI-compatible usage is read with the wall-clock time

- **WHEN** LM Studio answers after 2.5 seconds with `"usage":{"prompt_tokens":640,"completion_tokens":75,
  "total_tokens":715}`
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

- **WHEN** LM Studio answers `{"model":"google/gemma-4-e4b","choices":[{"message":{"role":"assistant","content":
  "{\"target\":\"Привіт\"}","reasoning_content":"thinking..."},"finish_reason":"stop"}]}`
- **THEN** the reply text is `{"target":"Привіт"}` with a normal finish

#### Scenario: The server answered with a different model

- **WHEN** the model `google/gemma-4-e4b-typo` was requested and LM Studio answers `200` with `"model":"google/gemma-4-e4b"`
- **THEN** the reply is used
- **AND** one WARN line names `google/gemma-4-e4b-typo` and `google/gemma-4-e4b`

#### Scenario: A blank reply

- **WHEN** the server answers `200` with `content` holding two spaces and a line feed
- **THEN** the result is `ErrorCode.emptyCompletion`

### Requirement: Map every transport and HTTP outcome to one error code

WHEN a call to either dialect fails, the system SHALL answer exactly one of these codes, decided in this order:

| Outcome | Code |
|---|---|
| the call is interrupted | `cancelled` |
| the request or connect timeout elapses | `timeout` |
| the connection is refused, the host does not resolve, or any other I/O failure | `unreachable` |
| HTTP `401` or `403` | `auth` |
| HTTP `404` on a chat call, or an Ollama error body whose message says the model was not found | `modelNotFound` |
| HTTP `429` | `rateLimited` |
| HTTP `500` to `599` | `upstream` |
| HTTP `400` whose body mentions `context_length_exceeded`, `n_ctx`, or the word `context` with `exceed`, `too long` or `greater than` | `contextWindow` |
| any other HTTP `400` | `validation` |
| any other HTTP status, or a `200` whose body cannot be read as the dialect's reply | `internal` |

The error's title and message SHALL be readable by a person, and its details SHALL hold only the HTTP status, the
endpoint host, the model id, the timeout and the attempt count. No header, no body and no secret SHALL ever appear in
an error or a log line.

**Source:** FR-INFER-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `02_Architecture/09_ERROR_HANDLING.md#error-code`,
`#safe-details-allowlist`.
In plain words: the engine, the screens and the retry policy branch on a code, never on a message. The order matters
because a body can match several rows; the first row wins. Ollama says `404 {"error":"model 'x' not found"}` for an
unknown model, and a context overflow arrives as a `400` whose only signal is its wording.

#### Scenario: Connection refused

- **WHEN** nothing listens on `http://localhost:11434` and a chat call is made to `ollama`
- **THEN** the result is `ErrorCode.unreachable`, marked retryable

#### Scenario: A request timeout

- **WHEN** the request timeout is 2 seconds and the server sends nothing for 3 seconds
- **THEN** the result is `ErrorCode.timeout`, with the details naming the 2-second timeout

#### Scenario: An unknown Ollama model

- **WHEN** Ollama answers `404` with the body `{"error":"model 'nope:latest' not found"}`
- **THEN** the result is `ErrorCode.modelNotFound`, not retryable

#### Scenario: A refused request

- **WHEN** the server answers `401`
- **THEN** the result is `ErrorCode.auth`, and the response body is not in the error

#### Scenario: A context overflow

- **WHEN** the server answers `400` with the body `{"error":{"message":"This model's maximum context length is 8192 tokens; your request exceeds it","code":"context_length_exceeded"}}`
- **THEN** the result is `ErrorCode.contextWindow`

#### Scenario: Any other 400

- **WHEN** the server answers `400` with the body `{"error":"invalid option: temperature"}`
- **THEN** the result is `ErrorCode.validation`

#### Scenario: A server fault

- **WHEN** the server answers `503`
- **THEN** the result is `ErrorCode.upstream`, marked retryable, with the details naming status `503`

#### Scenario: A body that is not the dialect's reply

- **WHEN** the server answers `200` with the body `<html>proxy error</html>`
- **THEN** the result is `ErrorCode.internal`

### Requirement: Retry a retryable failure a bounded number of times

WHEN a call answers a retryable code (`timeout`, `rateLimited`, `unreachable`, `upstream`, `discoveryFailed`), the system SHALL try again, up to 3 attempts in all, each with its own full request timeout, waiting the `Retry-After` delay when the response carries one (seconds or an HTTP date) and otherwise 500 ms doubled on each retry, capped at 8 s, with up to 25 % jitter; a non-retryable code SHALL NOT be repeated.

**Source**: FR-INFER-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`. In plain words: a local server that is still loading a model, or a rate-limited cloud endpoint, deserves a second try; a wrong key or an unknown model does not. A slow first attempt must not eat the second attempt's time, and the last failure is the answer when every attempt fails. A repeat after a refused setting is not an attempt; that rule belongs to the `inference` capability.

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

### Requirement: Serialize every model call through one gate

The system SHALL let at most one chat call, discovery call or verification call run at a time across the whole
process. A call that arrives while another runs SHALL wait its turn. While a call sleeps between retry attempts it
SHALL hold no place in the gate.

**Source:** FR-INFER-06 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/01_SYSTEM_ARCHITECTURE.md#single-flight-inference`, `02_Architecture/04_LLM_INTEGRATION.md#inference-gate`,
ADR-0008.
In plain words: a local model serves one request at a time; sending two only makes both slower. A sleeping retry must
not block another caller for nothing. Failing fast with `busy` is for interactive screens and comes with them.

#### Scenario: Two calls run one after the other

- **WHEN** two chat calls are started at the same time against a server that takes 1 second per reply
- **THEN** the server never has more than one request in flight
- **AND** both calls succeed

#### Scenario: A retry sleep lets another call through

- **WHEN** call A answers `429` with `Retry-After: 3` and call B is started during A's wait
- **THEN** B's request reaches the server before A's second attempt

### Requirement: Discover the models a provider offers

WHEN discovery is asked, the system SHALL `GET <baseUrl>/api/tags` of an Ollama-native provider and return each entry's `name`, or `GET <baseUrl>/models` (or the models endpoint override) of an OpenAI-compatible provider and return each entry's `id` from `data`, as model ids; an empty listing SHALL be a success with no models.

**Source**: FR-MODEL-01, FR-MODEL-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#model-discovery`, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a screen fills a list from this, and a server that keeps its model list on an unusual path can still be listed. An empty list is not an error: a server with nothing loaded is a valid state, and manual entry stays possible. A failure other than `auth`, `unreachable`, `timeout` or `cancelled`, or a body that is not a listing, is `discoveryFailed`.

#### Scenario: Ollama lists its models

- **WHEN** `/api/tags` answers `{"models":[{"name":"gemma4:e4b-mlx","model":"gemma4:e4b-mlx","size":1},{"name":"llama3.2:3b","model":"llama3.2:3b","size":2}]}`
- **THEN** discovery returns the ids `gemma4:e4b-mlx` and `llama3.2:3b` in that order

#### Scenario: LM Studio lists its models

- **WHEN** `/v1/models` answers `{"object":"list","data":[{"id":"google/gemma-4-e4b","object":"model"},{"id":"text-embedding-nomic","object":"model"}]}`
- **THEN** discovery returns `google/gemma-4-e4b` and `text-embedding-nomic`

#### Scenario: A listing that is not a listing

- **WHEN** `/v1/models` answers `200` with the body `{"data":"nope"}`
- **THEN** the result is `ErrorCode.discoveryFailed`

#### Scenario: An unreachable server is reported as such

- **WHEN** nothing listens at the base URL
- **THEN** discovery answers `ErrorCode.unreachable`, not `discoveryFailed`

### Requirement: Probe a provider's reachability without listing or inference

WHEN the connection of an Ollama-native provider is probed, the system SHALL `GET <baseUrl>/api/version`, and of an OpenAI-compatible provider `GET <baseUrl>/models` (or the models endpoint override); it SHALL count any HTTP answer as reachable except `401` and `403` (`ErrorCode.auth`) and `500` to `599` (`ErrorCode.upstream`), a refused connection being `ErrorCode.unreachable` and an elapsed timeout `ErrorCode.timeout`.

**Source**: FR-PROV-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#three-stage-verification`, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: "can I reach it, and does it accept my key" is a different question from "what models does it have", so a server without a listing endpoint can still pass the first. A `404` from a proxy that hides the listing route is still a server that answered.

#### Scenario: Ollama answers its version

- **WHEN** `/api/version` answers `200 {"version":"0.34.2"}`
- **THEN** the probe succeeds

#### Scenario: A 404 still counts as reachable

- **WHEN** `/v1/models` answers `404`
- **THEN** the probe succeeds

#### Scenario: A rejected key fails the probe

- **WHEN** `/v1/models` answers `401`
- **THEN** the probe answers `ErrorCode.auth`

### Requirement: Verify a provider and a model in three stages

WHEN verification of a provider and a model is asked for, the system SHALL run in order, up to the stage asked for and stopping at the first failure, the connection stage (the probe), the models stage (discovery: the model listed passes, unlisted fails `modelUnavailable`, `discoveryFailed` or an empty list passes softly with a note) and the inference stage (a probe for exactly `{"status":"ok"}` with reasoning Off and an output cap of `64`), reporting each stage run with its outcome and measured duration.

**Source**: FR-PROV-06, FR-PROV-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), FR-MODEL-05, FR-MODEL-06 (`#fr-model`), FR-INFER-08 (`#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`. In plain words: three short questions, each with its own answer, so a person sees which one failed: the server, the model name, or generation itself. The probe asks the way a run asks, so a thinking model that spends a capped reply on reasoning fails here instead of flagging a book later. An unknown provider id with no description given is `ErrorCode.validation` and runs no stage.

#### Scenario: All three stages pass

- **WHEN** `ollama` is verified with `gemma4:e4b-mlx`, `/api/version` answers `200` after 41 ms, `/api/tags` lists 3 models including `gemma4:e4b-mlx`, and `/api/chat` answers `{"status":"ok"}` with a normal finish after 1.2 s
- **THEN** the report holds three passed stages in the order connection, models, inference
- **AND** the connection stage reports about 41 ms, the models stage reports 3 models, and the inference stage reports about 1.2 s and `structured output: supported`

#### Scenario: The model is not in the list

- **WHEN** `/api/tags` lists only `llama3.2:3b` and `gemma4:e4b-mlx` is verified
- **THEN** the report holds a passed connection stage and a models stage failed with `ErrorCode.modelUnavailable`, reporting 1 model
- **AND** no chat call is made

#### Scenario: Discovery fails softly and inference decides

- **WHEN** `/v1/models` answers `200 {"data":"nope"}` and `/v1/chat/completions` answers `OK`
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

- **WHEN** the models check is asked for `lmstudio` with `google/gemma-4-e4b`, the connection probe answers `200` after 12 ms and `/v1/models` lists 2 models including `google/gemma-4-e4b`
- **THEN** the report holds a passed connection stage reporting about 12 ms and a passed models stage reporting 2 models, in that order
- **AND** no request reaches `/v1/chat/completions`

#### Scenario: A reasoning model spends the cap

- **WHEN** the inference probe's reply is empty because the 64-token cap was used on reasoning
- **THEN** the inference stage fails with `ErrorCode.emptyCompletion`

#### Scenario: An unknown provider runs no stage

- **WHEN** verification is asked for the provider id `gemini`, which is not registered, with no description given
- **THEN** the answer is `ErrorCode.validation` and no request is sent

### Requirement: Run a preflight before a command-line job

WHEN a translation job is about to start from the command line with a provider other than `pseudo`, the system SHALL
run connection, models and the schema-constrained inference stage. IF a stage fails, THEN no job starts and no model
call is made for a segment. A merely unconfirmed structured-output capability is informational and does not fail the
run.

**Source:** FR-INFER-08 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), FR-MODEL-06 (`#fr-model`),
`02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`.
In plain words: a typo in a model name must fail in a second, not after the first segment. A confirmed listing is proof
enough, so the extra generation runs only when the list could not confirm the model. Cold-loading a model costs
seconds, and the first real segment will pay it anyway.

#### Scenario: A listed model still probes structured output

- **WHEN** the command runs with `--provider ollama --model gemma4:e4b-mlx` and `/api/tags` lists `gemma4:e4b-mlx`
- **THEN** the job starts after a successful structured inference probe and the first later `/api/chat` request carries
  the book's first segment

#### Scenario: An unlisted model stops before the book

- **WHEN** the command runs with `--provider ollama --model gemma4:e4b` and `/api/tags` lists only `gemma4:e4b-mlx`
- **THEN** no job starts and `/api/chat` receives no request

#### Scenario: An unreadable listing falls back to one generation

- **WHEN** `/v1/models` answers `500` three times and `/v1/chat/completions` answers `OK`
- **THEN** the inference stage runs, passes, and the job starts

### Requirement: Log every call without its secret or its text

The system SHALL write, for every chat, discovery, probe and verification call: an INFO line when a verification starts
and ends with its stages and outcomes; a DEBUG line with the provider id, kind, endpoint host, model id, message count,
temperature, whether a response format was sent, attempt number, HTTP status and reply length; a WARN line for every
retry, every model mismatch and every failed stage; and the request and response bodies at TRACE only.

The system SHALL NOT write a credential value or an auth header value at any level; none exists in this change, and
the rule stands for the change that adds one.

**Source:** FR-INFER-10 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), NFR-PRIV-04, NFR-PRIV-06
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#no-telemetry`, `#secrets-never-stored`),
`02_Architecture/04_LLM_INTEGRATION.md#client-construction`.
In plain words: a wrong translation is explained from the log, so every call leaves a line saying what was asked and
what came back, in sizes and codes. The book's words and the model's words appear only when someone turns TRACE on.
A key, once a later change adds one, never appears, whatever the level.

#### Scenario: A TRACE run shows the bodies

- **WHEN** a chat call to the provider `lmstudio` runs at level TRACE
- **THEN** the log holds the request body and the response body

#### Scenario: A DEBUG run shows sizes and codes only

- **WHEN** the same call runs at level DEBUG and the server answers `503` then `200`
- **THEN** the log holds a DEBUG line with attempt `1` and status `503`, a WARN line for the retry, and a DEBUG line
  with attempt `2` and status `200`
- **AND** the log holds no request or response body

### Requirement: Answer a caller that asks which models a provider offers

The system SHALL let a caller ask, by provider id, for the models that provider offers, and SHALL answer with
the model ids discovery returns.

The answer SHALL be the outcome of the discovery this capability already contracts under "Discover the models a
provider offers" — the same two endpoints, the same reading of each dialect's listing shape, the same
empty-listing-is-success rule and the same failure classification. Asking through this route SHALL change no
discovery behaviour and SHALL add no obligation to it.

IF the provider id names no registered provider, THEN the system SHALL answer `ErrorCode.validation` and SHALL
send no request.

**Source:** FR-MODEL-01, FR-MODEL-02
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#model-discovery`,
`docs/specification/01_Product/07_SETTINGS.md#models-tab`.
In plain words: discovery is already built and already specified, and until now it has had exactly one caller —
the verification routine, which checks whether the chosen model is in the list and then throws the list away. A
screen that wants to offer that list has nothing to call. This adds only the way to ask, and the one behaviour
that is genuinely new because it is about the asking rather than the discovering: a provider id that names
nothing is refused before anything is sent, rather than producing a call to an endpoint assembled from a blank.
The existing discovery requirement's own note already anticipated this route — "a screen will fill a list from
this" — so the scenarios below prove it reaches that behaviour in both dialects rather than restating it.

#### Scenario: The route reaches Ollama-native discovery

- **GIVEN** a provider registered as `ollama` whose `/api/tags` answers
  `{"models":[{"name":"gemma3:12b","model":"gemma3:12b","size":1},{"name":"qwen3:8b","model":"qwen3:8b","size":2}]}`
- **WHEN** a caller asks `ollama` for its models
- **THEN** the answer carries `gemma3:12b` and `qwen3:8b` in that order

#### Scenario: The route reaches OpenAI-compatible discovery

- **GIVEN** a provider registered as `lmstudio` whose `/v1/models` answers
  `{"object":"list","data":[{"id":"google/gemma-3-12b","object":"model"}]}`
- **WHEN** a caller asks `lmstudio` for its models
- **THEN** the answer carries `google/gemma-3-12b`

#### Scenario: An unknown provider id is refused before anything is sent

- **WHEN** a caller asks `not-a-provider` for its models
- **THEN** the answer is `ErrorCode.validation`
- **AND** no HTTP request is made to any endpoint

### Requirement: Never send a context size to an OpenAI-compatible server

WHEN a chat request that carries a context size goes to an OpenAI-compatible provider, the system SHALL leave the
context size out of the body entirely, under any field name.

**Source**: FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#effective-context`. In plain words: the OpenAI chat format has no context-size field; LM Studio and similar servers set it when the model is loaded, and a strict server may reject a field it does not know. Only Ollama is told the size.

#### Scenario: LM Studio receives no context field

- **WHEN** a request with temperature `0.2` and the context size `8192` is sent to the provider `lmstudio` for the model
  `google/gemma-4-e4b`
- **THEN** the body posted to `/v1/chat/completions` has `"temperature":0.2` and no `num_ctx`, `n_ctx`, `context_length`
  or `options` key

### Requirement: Cap a chat call's output where the request states a cap

WHEN a chat request carries an output cap, the system SHALL send it to an Ollama-native provider as `options.num_predict` and to an OpenAI-compatible provider as `max_tokens`, or as `max_completion_tokens` where the model's settings choose it; WHEN it carries none and the model's settings limit the answer length, the limit is sent in its place; WHEN neither exists, neither field. WHILE an Ollama-native reply streams past the runaway bound, the system SHALL stop reading, close the connection and return what arrived as finished for length.

**Source**: FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `docs/specification/mockups/README.md#models-decision-6`. In plain words: a model that loops on one sentence would otherwise write until the server's own limit. The request states where the server must stop, and newer OpenAI-style models want that limit under another name, so the person's per-model choice decides which. A call that states no cap is left as unbounded as before unless the person limited that model's answer length. Ollama streams about one token per line, so a reply far past twice its cap is a model looping, and the client cuts it there. Which calls carry a cap, and how large it is, is the `translation-pipeline` capability's rule.

#### Scenario: LM Studio receives max_tokens

- **WHEN** a request with an output cap of `80` is sent to the provider `lmstudio` for the model `google/gemma-4-e4b` with no output-field choice
- **THEN** the body posted to `/v1/chat/completions` has `"max_tokens":80`

#### Scenario: The chosen output field is used

- **WHEN** the model's settings choose `max_completion_tokens` and a request with an output cap of `80` is sent to an OpenAI-compatible provider
- **THEN** the posted body has `"max_completion_tokens":80` and no `max_tokens` key

#### Scenario: No cap sends no max_tokens

- **WHEN** a request with no output cap is sent to the provider `lmstudio`
- **THEN** the posted body has neither `max_tokens` nor `max_completion_tokens`

#### Scenario: A runaway stream is cut as length

- **WHEN** a request with an output cap of `20` is sent to the provider `ollama` and the reply keeps streaming one-token lines with no final part
- **THEN** after 104 lines the client stops reading and answers a reply whose finish is cut off by length, well before the call's timeout

#### Scenario: Ollama receives num_predict beside num_ctx

- **WHEN** a request with the context size `8192` and an output cap of `80` is sent to the provider `ollama`
- **THEN** the body posted to `/api/chat` has `"options":{"num_ctx":8192,"num_predict":80}`

#### Scenario: The runaway bound follows the cap, else the context size

- **WHEN** an Ollama-native reply has sent more than `2 × cap + 64` lines, or, with no cap, more than `2 × context size + 64` lines
- **THEN** the system stops reading and returns what arrived as a reply that finished for length

### Requirement: Choose a chat call's timeout by its kind and its expected output

The system SHALL wait for each attempt of a judge call for 90 seconds, and for each attempt of a glossary prescan or a rolling-summary call for 120 seconds, whatever the provider's request timeout. For every other chat call it SHALL wait for the longer of the provider's request timeout and 0.5 seconds per expected output token, at most 600 seconds, and, WHEN the request states no expected output, for the provider's request timeout.

**Source**: FR-INFER-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `#provider-config`, `docs/next_features.md` §15; the stalled re-judge of the Bartimaeus hand test (tasks 15b). In plain words: a page from a slow model can need far longer than the three minutes a line needs, so a call that expects a long answer waits longer, up to ten minutes. The configured timeout is the floor for those calls: a short call never gets less than the default or the person's `--timeout`, because the first call after Ollama reloads a model for a larger context, or a prompt read on a CPU, can take minutes before the first word, and cutting it off would pause the run for nothing. A timeout set above ten minutes is kept as set. The judge and the helper calls are different: their replies are short and capped, so one still running after a minute and a half (two minutes for a helper) is stuck rather than slow, and the flat three minutes only delayed the retry. Which calls state an expected output is the `translation-pipeline` capability's rule; the short verification calls state none and keep the request timeout.

#### Scenario: A medium segment gets a proportional wait

- **WHEN** a draft request expects 400 output tokens, the provider has the default request timeout, and the server sends
  nothing
- **THEN** the attempt ends with `ErrorCode.timeout` once the reply has sent nothing for its idle gap or 200 seconds have
  passed, whichever comes first

#### Scenario: A short segment keeps the request timeout

- **WHEN** a draft request expects 40 output tokens and the provider has the default request timeout
- **THEN** its attempt waits up to 180 seconds

#### Scenario: A huge segment gets the ceiling

- **WHEN** a draft request expects 2,000 output tokens and the provider has the default request timeout
- **THEN** its attempt waits up to 600 seconds

#### Scenario: A verification call keeps the provider timeout

- **WHEN** the inference stage of verification sends its probe to a provider with the default request timeout
- **THEN** its attempt waits up to 180 seconds

#### Scenario: A judge call has its own bound

- **WHEN** a judge call is sent to a provider with the default request timeout, or with a request timeout of 600 seconds
- **THEN** its attempt waits up to 90 seconds

#### Scenario: A prescan or summary call has its own bound

- **WHEN** a glossary prescan call or a rolling-summary call is sent to a provider with the default request timeout
- **THEN** its attempt waits up to 120 seconds

#### Scenario: The command line's timeout is the floor

- **WHEN** the translate command runs with `--timeout 30`
- **THEN** a draft request expecting 40 output tokens waits up to 30 seconds
- **AND** a draft request expecting 400 output tokens waits up to 200 seconds

#### Scenario: A long configured timeout is never cut

- **WHEN** the provider's request timeout is 900 seconds and a draft request expects 2,000 output tokens
- **THEN** its attempt waits up to 900 seconds

#### Scenario: The wait is the longer of the request timeout and the proportional bound

- **WHEN** a call that is neither a judge, a prescan nor a summary call states an expected output
- **THEN** it waits `max(request timeout, min(600 s, expected output tokens × 0.5 s))`

#### Scenario: The request timeout is the provider's own

- **WHEN** the provider description sets no timeout and the command line gives no `--timeout`
- **THEN** the request timeout is 180 seconds, and a call that is neither a judge, a prescan nor a summary call never waits less than it

### Requirement: Read an Ollama-native reply as a stream that must keep arriving

The system SHALL ask an Ollama-native provider for a streamed reply and read it line by line, joining the `message.content` of every line and taking the finish reason and the token counts from the last line. WHEN no line arrives for the idle gap, or the whole reply runs past the attempt's timeout, the system SHALL end the attempt with `ErrorCode.timeout`; a line carrying an `error` field SHALL end it with `ErrorCode.upstream`, and an interrupted caller at once with `ErrorCode.cancelled`.

**Source**: FR-INFER-04a, FR-INFER-05 (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`; tasks 15b. In plain words: the JDK's request timeout stops counting once the reply begins, so a model that stalls halfway — or a runner that stays busy after a cut connection — held the call for the whole three minutes. A stream shows whether the model is still writing, so a stall is caught within a minute while a long, steady reply is never cut short.

#### Scenario: Lines are joined in order

- **WHEN** an Ollama-native reply streams several lines
- **THEN** the `message.content` of the lines is joined in the order the lines arrived

#### Scenario: A streamed reply reads as one

- **WHEN** Ollama streams the lines `Hel`, `lo` and a last line with `done_reason` `stop`, 12 prompt tokens and 2
  completion tokens
- **THEN** the reply is `Hello`, finished `STOP`, with those token counts

#### Scenario: A stream that stops sending

- **WHEN** the provider's request timeout is 1 second, a draft expecting 400 tokens is sent, and the stream sends its
  second line 8 seconds after its first
- **THEN** the attempt ends with `ErrorCode.timeout` after about 1 second of silence, not after 200 seconds

#### Scenario: A pause during a stalled stream

- **WHEN** the calling thread is interrupted while the stream sends nothing
- **THEN** the attempt ends at once with `ErrorCode.cancelled`

#### Scenario: The idle gap is 60 seconds or a shorter request timeout

- **WHEN** no line arrives before the first line or between two lines
- **THEN** the idle gap is 60 seconds, or the provider's request timeout when that is shorter
- **AND** a timed-out attempt closes the connection

#### Scenario: An error line ends the attempt

- **WHEN** a streamed line carries an `error` field
- **THEN** the attempt ends with `ErrorCode.upstream`

#### Scenario: The caller still receives one whole reply

- **WHEN** a caller asks `ChatModel.chat` for an Ollama-native or an OpenAI-compatible reply
- **THEN** the call is synchronous and no caller sees a partial reply, and the OpenAI-compatible client keeps asking for a whole reply

### Requirement: Retry a timed-out call once, and never as the same request

WHEN an attempt of a chat call ends with `ErrorCode.timeout`, the system SHALL send at most one more attempt for that call, carrying a sampling seed and, when the request states an output cap, three quarters of that cap. A call SHALL make at most 2 attempts that time out; the other retryable codes keep the 3-attempt budget and the `Retry-After` wait of the provider retry requirement.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`; tasks 15b. In plain words: a reply that ran until the timeout most likely looped, and the identical request at a low temperature loops the same way, so three identical retries cost nine minutes for nothing. One varied retry gives the model a different path and a shorter leash, and then the failure goes to the run, which decides what to do with it.

#### Scenario: A timeout then an answer

- **WHEN** the first attempt of a request capped at 400 tokens times out and the second is answered
- **THEN** the second request carries `"seed":2` and the cap 300, and the call succeeds after 2 requests

#### Scenario: Two timeouts end the call

- **WHEN** every attempt times out
- **THEN** the result is `ErrorCode.timeout` and the server received exactly 2 requests

#### Scenario: Attempts are logged

- **WHEN** an attempt is made, or a call gives up
- **THEN** each attempt is logged with its call kind and attempt number, and a call that gives up is logged as a warning with its code

### Requirement: Detect a model's context length, optionally and without failing

WHEN asked for a model's context length, the system SHALL ask an Ollama-native provider with `POST /api/show` and an OpenAI-compatible provider with `GET /api/v0/models/<model id>` at the server's root, and answer `ContextLength` with the figure, or `ContextLength.unknown()` when the server cannot say; no such outcome is an error. Detection SHALL be user-triggered provider communication, never a background call, and a chat call never triggers it.

**Source**: `additional-work.md` 15d.5; `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md` (optional, safe, degrades without an error). In plain words: the window a model really has decides how much of the prompt it may be given, but a server that cannot say must never stop a run; it just gets the default 8k.

#### Scenario: Ollama reports its context length

- **WHEN** `/api/show` answers `{"model_info":{"general.architecture":"gemma3","gemma3.context_length":131072}}`
- **THEN** the detected length is 131072

#### Scenario: LM Studio prefers the loaded length

- **WHEN** `/api/v0/models/google/gemma-3` answers `max_context_length` 131072 and `loaded_context_length` 4096
- **THEN** the detected length is 4096

#### Scenario: A server that cannot say

- **WHEN** the server answers 404 or 500, answers a body without the field, or refuses the connection
- **THEN** the length is unknown and the result is not an error

#### Scenario: Where the figure is read

- **WHEN** the figure is read from Ollama or from LM Studio
- **THEN** it is the `<architecture>.context_length` entry of `model_info` for Ollama, and for LM Studio the native endpoint outside the configured `/v1` base, preferring `loaded_context_length` over `max_context_length`

#### Scenario: A server that is not that server at all

- **WHEN** the server is not reachable, answers a non-200 status, answers a body without the field or is not that server at all
- **THEN** the length is `ContextLength.unknown()` and no error is returned

#### Scenario: An unregistered provider id is refused without a request

- **WHEN** the provider id is not registered
- **THEN** the result is a `validation` error and no request is sent

### Requirement: Turn reasoning off for an OpenAI-compatible call, and retry a reply that spent its cap reasoning

WHEN a chat request goes to an OpenAI-compatible provider, the system SHALL send `"reasoning_effort":"none"` for reasoning Off, `"reasoning_effort":"low"` for Low, `"reasoning_effort":"medium"` for On, and no `reasoning_effort` when the request leaves reasoning to the provider's default. IF a capped reply has blank `content`, non-blank reasoning and the finish `length`, THEN the system SHALL send the same request once more with its cap raised to four times, bounded by the request's context size when one is given; a second such reply SHALL answer `ErrorCode.emptyCompletion`.

**Source**: FR-INFER-02, FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`, S4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: through `/v1`, a thinking model writes every output token into `message.reasoning` unless told not to, so a capped call came back with no translation. Off, the default, keeps `reasoning_effort: none`; Low and On exist because the person chose them in Settings › Generation (the `inference` capability), and a server that refuses the control is handled by the refused-setting rule there.

#### Scenario: LM Studio is told to skip reasoning

- **WHEN** a draft call with reasoning Off and the cap `183` goes to LM Studio
- **THEN** the body posted to `/v1/chat/completions` has `"reasoning_effort":"none"` and `"max_tokens":183`

#### Scenario: A reply that only reasoned is asked for again with room

- **WHEN** the reply to a call capped at `128` with the context size `8192` is `{"choices":[{"message":{"content":"","reasoning_content":"Thinking Process: …"},"finish_reason":"length"}]}`
- **THEN** the same request is sent once more with `"max_tokens":512`, and its reply is the call's answer

#### Scenario: A server that rejects the control gets the request once more without it

- **WHEN** the server answers `400` naming the `reasoning_effort` control as unsupported
- **THEN** the request is sent once more without it, as the `inference` capability's refused-setting rule says

#### Scenario: A request that leaves reasoning to the provider carries no control

- **WHEN** a request that leaves reasoning to the provider's default goes to an OpenAI-compatible provider
- **THEN** the body has no `reasoning_effort` key

#### Scenario: Low and On reasoning are sent as reasoning_effort

- **WHEN** a request with reasoning Low goes to an OpenAI-compatible provider, and then one with reasoning On
- **THEN** the first body has `"reasoning_effort":"low"` and the second `"reasoning_effort":"medium"`

#### Scenario: Either reasoning field marks a reply that spent its cap

- **WHEN** a capped reply has blank `content`, the finish `length`, and non-blank `reasoning` or `reasoning_content`
- **THEN** the same request is sent once more with its cap raised

#### Scenario: A second reply that only reasoned is empty

- **WHEN** the repeated request is also answered with blank `content`, reasoning and the finish `length`
- **THEN** the call answers `ErrorCode.emptyCompletion`

#### Scenario: Reasoning never reaches the reply text

- **WHEN** a reply carries `reasoning` or `reasoning_content`
- **THEN** neither field is part of the reply text

#### Scenario: Low reasoning is sent as chosen

- **WHEN** a draft call with reasoning Low goes to LM Studio
- **THEN** the body has `"reasoning_effort":"low"`

### Requirement: Add the provider's headers and its key to every request

The system SHALL send a provider's custom headers with every request to it, SHALL replace each `${env:NAME}` in a header value with the value of the environment variable `NAME` when the request is made (looked up as the "Look a variable up where the person set it" requirement says), and SHALL send the provider's key in the way its sign-in says (a Bearer token, or a named header).

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`, ADR-0043. In plain words: a gateway that wants an extra header, or a server that wants a key, works without the key ever being saved: the value is read from the person's environment at the moment of the call.

#### Scenario: A bearer key is sent

- **WHEN** a provider signs in with a Bearer token from `HOME_API_KEY` whose value is `sk-test-123` and a chat call is made
- **THEN** the request carries the header `Authorization: Bearer sk-test-123`

#### Scenario: A named key header is sent

- **WHEN** a provider signs in with the API-key header `X-Api-Key` from `OFFICE_KEY` whose value is `k-777`
- **THEN** the request carries `X-Api-Key: k-777` and no `Authorization` header

#### Scenario: A custom header is resolved

- **WHEN** a provider has the header `X-Team` with the value `${env:TEAM_NAME}` and `TEAM_NAME` is `translation`
- **THEN** the request carries `X-Team: translation`

### Requirement: Look a variable up where the person set it

WHEN a request needs a key or an `${env:NAME}` header value, and WHEN the provider form asks whether a key variable is set, the system SHALL use one lookup that reads BookLoom's own process environment first and then the user account's environment the operating system keeps — on macOS `launchctl getenv NAME`, on Windows the user then the machine environment in the registry, on Linux `environment.d/*.conf` (a later file wins) then `/etc/environment` — at the time of the call, reusing an answer for at most 2 seconds; it SHALL look up only a name of letters, digits and underscores not starting with a digit, run no shell, treat an empty value as not set, and treat a store it cannot read within 1.5 seconds as not set with one WARN line per launch; it SHALL NOT log, store or put in an error any value, only the variable's name and which source answered.

**Source**: `.temporary_context/ui-evidence/final/FINDINGS-OPEN.md` LAN-7 (owner requirement), ADR-0006. In plain words: an app opened from the Dock, a file manager or an IDE never sees a shell profile's `export` lines; a key set with `launchctl setenv`, `setx` or `environment.d` works at once, with no restart.

#### Scenario: A key set only for the user account is sent

- **WHEN** `BL_OS_ONLY_KEY` is absent from BookLoom's environment and the operating system's store holds `sk-from-os` for it, and a provider signs in with a Bearer token from `BL_OS_ONLY_KEY`
- **THEN** the form shows `BL_OS_ONLY_KEY` as "set" and the request carries `Authorization: Bearer sk-from-os`

#### Scenario: BookLoom's own environment wins

- **WHEN** `LMS_KEY` is `from-shell` in BookLoom's environment and `from-launchctl` in the operating system's store
- **THEN** the lookup answers `from-shell` and the store is not asked

#### Scenario: A store that cannot be read

- **WHEN** reading the operating system's store fails or takes longer than 1.5 seconds
- **THEN** the variable counts as not set, one WARN line is written, and no error dialog is shown

### Requirement: Say that a server answered when it refused or answered an error

IF a provider answers HTTP 401 or 403, THEN the system SHALL answer `ErrorCode.auth` with "The server answered 401 (not authorised)" or "403 (forbidden)" and that the sign-in is missing or wrong; IF a provider answers an HTTP success whose body is only an error object (except Ollama's own "model not found" and its streamed chat reply), THEN the connection, model-list and chat calls SHALL fail with the reason `wrong-path` (`ErrorCode.validation`, the model list keeping `discoveryFailed`) and say that the server does not serve this path and, for an OpenAI-compatible address without `/v1`, that it probably needs `/v1` at the end.

**Source**: `.temporary_context/ui-evidence/final/FINDINGS-OPEN.md` LAN-2, LAN-4. In plain words: LM Studio asked at its root answers HTTP 200 with `{"error":"Unexpected endpoint or method"}`, which used to pass the connection check; and a refused key used to read as a server that does not answer.

#### Scenario: A refused key in either dialect

- **WHEN** an Ollama or OpenAI-compatible server answers a probe, a model list or a chat with HTTP 401
- **THEN** the call fails with `auth` and "The server answered 401 (not authorised): the sign-in is missing or wrong. Check the sign-in type and the key variable."

#### Scenario: LM Studio asked without /v1

- **WHEN** an OpenAI-compatible provider at `http://127.0.0.1:1234` is probed and the server answers HTTP 200 with `{"error":"Unexpected endpoint or method. (GET /models)"}`
- **THEN** the connection check fails with reason `wrong-path` and "The server answered, but it does not serve this path: the address probably needs /v1 at the end."

### Requirement: Refuse a call whose key is missing before sending anything

IF a provider names an environment variable for its key or for a header and that variable is not set, THEN the system SHALL answer `ErrorCode.missingCredential` naming the variable and saying how to set it for the user account on macOS, Windows and Linux, before any request is sent, and SHALL NOT include any key value in the answer.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`. In plain words: a call without its key would only come back as a confusing 401. Saying "the variable `HOME_API_KEY` is not set" lets the person fix the real cause.

#### Scenario: An unset key variable

- **WHEN** a provider signs in with a Bearer token from `HOME_API_KEY` and `HOME_API_KEY` is not set
- **THEN** the call answers `ErrorCode.missingCredential` mentioning `HOME_API_KEY`, `launchctl setenv HOME_API_KEY`, `setx HOME_API_KEY` and `environment.d`
- **AND** the server received 0 requests

#### Scenario: An unset header variable

- **WHEN** a header value is `${env:TEAM_NAME}` and `TEAM_NAME` is not set
- **THEN** the call answers `ErrorCode.missingCredential` mentioning `TEAM_NAME`

### Requirement: Never show the key in a log line or in the test dialog

The system SHALL NOT write a key or a resolved header value to a log line at any level, and SHALL show the key as `••••` in a request it displays or copies for the test dialog.

**Source**: `docs/specification/mockups/README.md#dialog--test-connection--models--inference-s2`, `.claude/rules/logging.md`. In plain words: logs and copied commands get shared with other people, so the secret is replaced by the same mask, `••••`, wherever the request is displayed.

#### Scenario: The request line hides the key

- **WHEN** the test dialog shows a request to a provider whose Bearer key is `sk-test-123`
- **THEN** the displayed headers read `Authorization: Bearer ••••` and the log contains no `sk-test-123`

#### Scenario: Copy as curl carries the mask

- **WHEN** Copy as curl is used for that request
- **THEN** the copied command contains `-H 'Authorization: Bearer ••••'` and no key value

### Requirement: Look once for a local AI program at first launch

WHERE the application runs in the desktop window, WHEN it starts for the first time or for the first time after a fresh database, the system SHALL ask exactly `http://127.0.0.1:11434` (`/api/tags`) and `http://127.0.0.1:1234/v1` (`/models`) for their model lists with a 1 second connect timeout, SHALL send no other request and no inference, SHALL record that the look happened whatever it found, and SHALL NOT look again by itself.

**Source**: ADR-0044, owner decision 13 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D14". In plain words: someone who just installed Ollama should find "AI model: Ollama — ready" on the first screen instead of an empty settings form. The look is local, listing only, and happens once; a fresh database counts as a first launch because the record of the look lives in it. The command line and the tests never look.

#### Scenario: Only the model lists are requested

- **WHEN** the look runs on a first launch while servers listen at both addresses
- **THEN** the Ollama server receives exactly one request, `GET /api/tags`, and the LM Studio server exactly one, `GET /v1/models`
- **AND** neither receives a chat request

#### Scenario: Nothing answers

- **WHEN** neither address answers within 1 second
- **THEN** no provider is stored, no error is shown, and the look is recorded as done

#### Scenario: A second launch does not look again

- **WHEN** the application starts and the look is recorded as done
- **THEN** neither address is asked

#### Scenario: A fresh database looks once more

- **WHEN** the look was done, and at the next launch the database is set aside and a fresh one created
- **THEN** both addresses are asked once on that launch

#### Scenario: The command line never looks

- **WHEN** a book is translated from the command line on a machine where the look was never done
- **THEN** neither address is asked

### Requirement: Store what the first-launch look found

WHEN a program asked by the first-launch look answers with at least one model, the system SHALL store it as a provider and, IF both programs answer, THEN Ollama SHALL become the current provider; it SHALL select the model when there is exactly one and ask the person when there are several. A program that answers with zero models SHALL count as not found.

**Source**: ADR-0044, `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D14". In plain words: a program with nothing loaded cannot translate, so it is reported as not found rather than as "ready"; a person with several models chooses one instead of BookLoom guessing.

#### Scenario: Ollama with one model

- **WHEN** `http://127.0.0.1:11434/api/tags` lists only `gemma4:e4b`
- **THEN** an Ollama provider is stored with `gemma4:e4b` selected and no question is asked

#### Scenario: Both programs answer

- **WHEN** Ollama lists `gemma4:e4b` and LM Studio lists `google/gemma-4-e4b`
- **THEN** both providers are stored and Ollama is the current one

#### Scenario: Several models

- **WHEN** Ollama lists `gemma4:e4b` and `gemma4:26b`
- **THEN** the Ollama provider is stored and the person is asked which model to use

#### Scenario: A program with no models counts as not found

- **WHEN** Ollama answers `/api/tags` with `{"models":[]}` and LM Studio does not answer
- **THEN** no provider is stored and Projects shows `No AI model found`

### Requirement: Look again when the person presses Try again

WHEN the person presses Try again under "No AI model found" on Projects, the system SHALL run the same look once more — the same two addresses, the same listing requests and the same storing rule — because the person asked for it.

**Source**: ADR-0044, `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`. In plain words: "Open the Ollama app, then press Try again" has to work. A check the person starts is user-triggered provider communication, so it stays within the offline rule.

#### Scenario: Try again finds Ollama after it was opened

- **WHEN** the first launch found nothing, the person opens Ollama with `gemma4:e4b` loaded and presses Try again
- **THEN** `GET /api/tags` is sent to `http://127.0.0.1:11434`, an Ollama provider is stored with `gemma4:e4b` selected, and Projects shows the model as ready

### Requirement: Classify a reply that says no model is loaded as modelUnavailable

A chat reply of HTTP `400` or `404` whose body says the server has no model loaded — LM Studio's `Model unloaded`, `Model is unloaded`, `No models loaded`, or a model "not loaded" — SHALL be classified `ErrorCode.modelUnavailable`, not `validation` or `modelNotFound`.

**Source**: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#http-error-mapping`, `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#workflow-states-and-recovery`. In plain words: loading the model fixes it, which the resume capability's recovery waits for, so it must not look like a bad request. Classifying an HTTP reply is this capability's job.

#### Scenario: An unloaded model is recognised

- **WHEN** LM Studio answers `400` with the body `{"error":"Model unloaded"}` to a chat call
- **THEN** the call's error is `ErrorCode.modelUnavailable`

### Requirement: Classify a reply that answered but cannot be read as upstream

WHEN an AI program answers a chat request with HTTP 200 and a body that is empty, is not the dialect's JSON, or holds no message, the `:llm` client SHALL return `ErrorCode.upstream` with the message "The AI program answered, but its reply could not be read." and the endpoint host and model as its only details, logged once at WARN with no body, never `internal` and never at ERROR; and WHEN it answers with HTTP 5xx, the message SHALL name the status ("The AI program had an error (HTTP 500).").

**Source**: `docs/specification/02_Architecture/09_ERROR_HANDLING.md` (`upstream` — the endpoint failed), final QA W2-D2. In plain words: a server that answered is not one BookLoom cannot reach, and its broken reply is its fault, not an unexpected failure of BookLoom.

#### Scenario: An unreadable reply from either dialect

- **WHEN** Ollama's `/api/chat` or an OpenAI-compatible `/v1/chat/completions` answers 200 with an empty body, `not-json` or `{}`
- **THEN** the call fails with `upstream` and "The AI program answered, but its reply could not be read.", and no ERROR line is logged

#### Scenario: A server error names its status

- **WHEN** either dialect's chat endpoint answers HTTP 500
- **THEN** the call fails with `upstream` and "The AI program had an error (HTTP 500)."

### Requirement: Describe how a provider signs in

The system SHALL describe a provider's sign-in as none, a Bearer token, or a named API-key header, SHALL hold for the last two only the name of the environment variable that holds the key, and SHALL NOT hold a key value; IF a Bearer or API-key sign-in has no variable name, or an API-key sign-in has no header name, THEN the system SHALL refuse the description with `ErrorCode.validation`.

**Source**: FR-PROV-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", ADR-0043. In plain words: a provider says where its key lives, never what the key is, so describing or saving a provider can never leak it.

#### Scenario: A remote provider with a key reference

- **WHEN** a provider `openai` is described as OpenAI-compatible at `https://api.openai.com/v1` with a Bearer sign-in from `OPENAI_API_KEY`
- **THEN** the provider is registered under `openai` and holds the name `OPENAI_API_KEY` and no key value

#### Scenario: No sign-in sends no key

- **WHEN** a provider is described with sign-in none
- **THEN** a request to it carries no `Authorization` header

#### Scenario: A key sign-in without a variable name is refused

- **WHEN** a provider is described with a Bearer sign-in and no environment variable name
- **THEN** the result is `ErrorCode.validation`

#### Scenario: An API-key header without a header name is refused

- **WHEN** a provider is described with an API-key sign-in from `OFFICE_KEY` and no header name
- **THEN** the result is `ErrorCode.validation`

### Requirement: Describe a provider's overrides, headers and custom models

The system SHALL let a provider description carry an override of the models endpoint, an override of the inference endpoint, custom headers each with a name and a value that may be `${env:NAME}`, and custom model identifiers.

**Source**: decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", `docs/specification/mockups/README.md#providers`. In plain words: servers behind a gateway or with unusual routes need small adjustments, and a provider can name models its server does not list. Which header names must take an `${env:NAME}` value before a provider is saved is the `local-storage` capability's rule.

#### Scenario: A full description is accepted

- **WHEN** a provider is described with the models override `/catalog/models`, the header `X-Team` set to `${env:TEAM_NAME}` and the custom model `acme-translate-1`
- **THEN** the description is registered holding all three, with the header value kept as the text `${env:TEAM_NAME}`

### Requirement: Use a provider's endpoint overrides

WHERE a provider has a models endpoint override, the system SHALL use it for discovery and for the connection probe of an OpenAI-compatible provider; WHERE an OpenAI-compatible provider has an inference endpoint override, the system SHALL send its chat calls there.

**Source**: decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`. In plain words: when a server keeps its routes elsewhere, every question to it goes to the route the person named.

#### Scenario: Discovery follows the models override

- **WHEN** an OpenAI-compatible provider at `https://gw.example.net/v1` has the models endpoint override `/catalog/models`
- **THEN** discovery asks `https://gw.example.net/v1/catalog/models`

#### Scenario: The probe follows the models override

- **WHEN** the same provider is probed
- **THEN** the probe asks `https://gw.example.net/v1/catalog/models`

#### Scenario: Chat follows the inference override

- **WHEN** an OpenAI-compatible provider at `https://gw.example.net/v1` has the inference endpoint override `/gen/chat`
- **THEN** a chat call is posted to `https://gw.example.net/v1/gen/chat`

### Requirement: Keep registrations across a restart in the window only

WHERE the application runs in the window, the system SHALL keep registered providers across a restart; WHERE it runs from the command line, it SHALL NOT keep them beyond the process.

**Source**: ADR-0043. In plain words: a person's saved providers come back next time, while a scripted run never leaves anything behind.

#### Scenario: A window registration survives a restart

- **WHEN** the window saves a provider `office` at `http://10.0.0.5:1234/v1` and is restarted
- **THEN** looking up `office` returns that description

#### Scenario: A command-line registration is gone with the process

- **WHEN** the command line registers `office` and a new process starts
- **THEN** looking up `office` returns nothing

### Requirement: Fall back to a plain probe when structured output is not confirmed

IF the inference stage's structured probe is rejected or answered nonconformingly, THEN the system SHALL send one plain probe and, for a nonblank reply, report `structured output: not confirmed`; a `modelNotFound` answer SHALL be reported as `modelUnavailable` and any other error as itself.

**Source**: FR-INFER-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`. In plain words: a server that cannot do structured answers can still translate, so the test says "not confirmed" rather than "failed".

#### Scenario: A server without structured output

- **WHEN** the structured probe is rejected with `400` and the plain probe answers `OK`
- **THEN** the stage passes and reports `structured output: not confirmed`

### Requirement: Verify a provider description that is not saved

WHEN verification is asked for a provider description that has not been saved, the system SHALL verify it exactly as given, with its endpoints, headers and key, and SHALL NOT register or store it.

**Source**: `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", `docs/specification/mockups/README.md#providers`. In plain words: a person can test the form before saving it, so a wrong address never reaches the stored settings.

#### Scenario: An unsaved provider is verified without being stored

- **WHEN** a description `office` at `http://10.0.0.5:1234/v1` that is not stored is verified
- **THEN** the server at that address is asked and the report is returned
- **AND** looking up `office` afterwards returns nothing
