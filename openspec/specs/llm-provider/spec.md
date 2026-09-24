# llm-provider Specification

## Purpose
How BookLoom describes a language-model provider and talks to it: the two server dialects (Ollama-native and
OpenAI-compatible), how a request is shaped and a reply is read, how every transport and HTTP outcome becomes one typed
error, retry, the single-flight gate, model discovery, and the three-stage verification that runs before a model is
trusted. Nothing here names a book; the translation engine only ever sees a chat model.

## Requirements

### Requirement: Describe a provider by kind, endpoint and timeouts

The system SHALL describe a provider by:

- an id;
- a kind: Ollama-native or OpenAI-compatible;
- a base URL that is absolute and uses `http` or `https`;
- a connect timeout and a request timeout, both positive, defaulting to 10 seconds and 180 seconds.

Nothing else: no credential, no auth scheme, no context-window size, no reasoning setting.

IF a description breaks one of these rules, THEN the system SHALL refuse it with `ErrorCode.validation` and register
nothing.

**Source:** FR-PROV-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`),
`02_Architecture/04_LLM_INTEGRATION.md#provider-config`, ADR-0005.
In plain words: a provider is a few settings, not code. The same OpenAI-compatible client serves LM Studio on this
machine and any other OpenAI-shaped server, because they differ only in URL. Nothing credential-shaped exists in this
change: the targets are local servers. A later change adds an environment-variable name for a Bearer token, read at
call time and never stored.

#### Scenario: A remote OpenAI-compatible endpoint is described by its URL alone

- **WHEN** a provider `openai` is described as OpenAI-compatible at `https://api.openai.com/v1` with the default
  timeouts
- **THEN** the provider is registered under `openai`
- **AND** a request to it carries no `Authorization` header

#### Scenario: A relative base URL is refused

- **WHEN** a provider is described with the base URL `localhost:11434`
- **THEN** the result is `ErrorCode.validation` and nothing is registered

#### Scenario: A zero request timeout is refused

- **WHEN** a provider is described with a request timeout of 0 seconds
- **THEN** the result is `ErrorCode.validation`

### Requirement: Offer the two local presets and accept registrations

The system SHALL offer, without any configuration, the provider `ollama` (Ollama-native, `http://localhost:11434`) and
the provider `lmstudio` (OpenAI-compatible, `http://localhost:1234/v1`), each with the default timeouts.

WHEN a caller registers a provider description whose id is already known, the system SHALL replace the known
description for the rest of the run.

The system SHALL answer a lookup by id with the description, or with nothing for an unknown id, and SHALL list every
known description. Registrations SHALL NOT survive the process.

**Source:** FR-PROV-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`),
`02_Architecture/04_LLM_INTEGRATION.md#provider-factory`, `#provider-profile`.
In plain words: the two servers people run on their own machine work out of the box, and a command line can point at
another one for a single run. Saving providers is a later change; until then the registry lives in memory.

#### Scenario: The presets exist from the start

- **WHEN** nothing has been registered and the provider `lmstudio` is looked up
- **THEN** an OpenAI-compatible description at `http://localhost:1234/v1` with the default timeouts is returned

#### Scenario: A registration overrides a preset for this run

- **WHEN** a provider `ollama` is registered at `http://10.0.0.5:11434`
- **THEN** looking up `ollama` returns a description at `http://10.0.0.5:11434`
- **AND** a fresh process again returns `http://localhost:11434`

#### Scenario: An unknown id returns nothing

- **WHEN** the provider `gemini` is looked up
- **THEN** no description is returned and no error is raised

### Requirement: Shape an Ollama-native chat request

WHEN a chat request goes to an Ollama-native provider, the system SHALL `POST` to `<baseUrl>/api/chat` a JSON body
with:

- `model`: the bound model id;
- `messages`: the conversation as `{"role","content"}` objects with the roles `system`, `user` and `assistant`;
- `stream`: `false`;
- `options`: an object holding `temperature`, the request's temperature, only when one is given; the `options`
  object itself is left out otherwise;
- `format`: the request's JSON schema, only when a response format is given.
- `think`: `false`, only when the request explicitly disables reasoning output.

A field whose value is absent SHALL be left out of the body, never sent as `null`. The system SHALL NOT send `num_ctx`,
`keep_alive` or any other option, and SHALL NOT read `<baseUrl>/api/show`.

**Source:** FR-INFER-01, FR-INFER-02, FR-INFER-03 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`.
In plain words: Ollama is spoken to natively because its OpenAI-shaped shim drops native controls and hides Ollama's
own error bodies. Translation may request `think:false`; an explicitly rejected native control is retried once with
the field omitted, while absent fields remain omitted because a strict server rejects an explicit `null`.

#### Scenario: A full request

- **WHEN** a request with temperature `0.2` and a JSON-schema response format is sent to the provider `ollama` for the
  model `gemma4:e4b-mlx` with reasoning disabled
- **THEN** the body posted to `/api/chat` has `"model":"gemma4:e4b-mlx"`, `"stream":false`,
  `"options":{"temperature":0.2}` and a `"format"` object holding that schema
- **AND** it has `"think":false`, no `num_ctx` or `keep_alive` key, and `/api/show` is never requested

#### Scenario: Absent settings are left out

- **WHEN** a request with no temperature and no response format is sent
- **THEN** the body has no `options` key and no `format` key

### Requirement: Shape an OpenAI-compatible chat request

WHEN a chat request goes to an OpenAI-compatible provider, the system SHALL `POST` to `<baseUrl>/chat/completions` a
JSON body with:

- `model`: the bound model id;
- `messages`: the conversation as `{"role","content"}` objects with the roles `system`, `user` and `assistant`;
- `stream`: `false`;
- `temperature`: the request's temperature, only when one is given;
- `response_format`: `{"type":"json_schema","json_schema":{"name":<name>,"strict":true,"schema":<schema>}}`, only
  when a response format is given.

A field whose value is absent SHALL be left out of the body, never sent as `null`. The system SHALL NOT send any
reasoning parameter.

**Source:** FR-INFER-01, FR-INFER-03, FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`, ADR-0013.
In plain words: one client covers LM Studio, llama.cpp, vLLM and any other OpenAI-shaped server. No reasoning
parameter is ever sent: sending `reasoning_effort: "low"` to a model that does not reason has been observed to turn
thinking on and leave the answer empty, so the parameter is never guessed, and the reply is stripped instead.

#### Scenario: A request with structured output and no reasoning parameter

- **WHEN** a request with temperature `0.2` and the response format named `draft` is sent to the provider `lmstudio`
  for the model `google/gemma-4-e4b`
- **THEN** the body posted to `/v1/chat/completions` has `"model":"google/gemma-4-e4b"`, `"stream":false`,
  `"temperature":0.2` and `"response_format":{"type":"json_schema","json_schema":{"name":"draft","strict":true,...}}`
- **AND** it has no `reasoning_effort` key

#### Scenario: Absent settings are left out

- **WHEN** a request with no temperature and no response format is sent
- **THEN** the body has neither a `temperature` nor a `response_format` key

### Requirement: Read a reply and report its finish

WHEN an Ollama-native provider answers `200` to `/api/chat`, the system SHALL take the reply text from
`message.content` and the finish from `done_reason`: `stop` is a normal finish, `length` is cut off by length, and
anything else is other. A `message.thinking` field SHALL be ignored.

WHEN an OpenAI-compatible provider answers `200` to `/chat/completions`, the system SHALL take the reply text from
`choices[0].message.content` and the finish from `choices[0].finish_reason`: `stop` is a normal finish, `length` is cut
off by length, and anything else is other. A `reasoning_content` field and `tool_calls` SHALL be ignored.

IF the body's `model` names a model other than the requested one, THEN the system SHALL still use the reply and SHALL
write one WARN line naming both ids.

IF the reply text is blank before any sanitising, THEN the system SHALL answer `ErrorCode.emptyCompletion`.

**Source:** FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `#empty-response-ordering`, ADR-0013.
In plain words: both dialects collapse to text plus a finish reason, which is all the engine needs. A separate
reasoning channel (`thinking`, `reasoning_content`) is ignored and inline reasoning is stripped by the sanitiser; with
no reasoning parameter ever sent, that is the whole of the app's reasoning handling in this change. LM Studio answers
a request for an unknown model id with whichever model is loaded, naming the real one in `model`, so the mismatch is
logged rather than trusted silently; the models stage of verification is what stops a typo before a run.

#### Scenario: An Ollama reply that stopped normally

- **WHEN** Ollama answers `{"model":"gemma4:e4b-mlx","message":{"role":"assistant","content":"{\"segments\":[]}",
  "thinking":"Let me think"},"done":true,"done_reason":"stop"}`
- **THEN** the reply text is `{"segments":[]}` with a normal finish, and the thinking text appears nowhere

#### Scenario: An Ollama reply cut off by length

- **WHEN** Ollama answers with `"done_reason":"length"`
- **THEN** the finish is cut off by length

#### Scenario: An Ollama reply that only loaded the model

- **WHEN** Ollama answers with `"done_reason":"load"` and an empty `message.content`
- **THEN** the result is `ErrorCode.emptyCompletion`

#### Scenario: An OpenAI-compatible reply with a separate reasoning channel

- **WHEN** LM Studio answers `{"model":"google/gemma-4-e4b","choices":[{"message":{"role":"assistant","content":
  "{\"segments\":[]}","reasoning_content":"thinking..."},"finish_reason":"stop"}]}`
- **THEN** the reply text is `{"segments":[]}` with a normal finish

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

WHEN a call answers a retryable code (`timeout`, `rateLimited`, `unreachable`, `upstream`, `discoveryFailed`), the
system SHALL try again, up to 3 attempts in all, and SHALL answer the last failure when every attempt fails. Between
attempts it SHALL wait:

- the `Retry-After` delay when the response carries one, as seconds or as an HTTP date;
- otherwise 500 ms doubled on each retry, capped at 8 s, with up to 25 % random jitter.

Each attempt SHALL get its own full request timeout. A call that answers a non-retryable code SHALL NOT be repeated.

**Source:** FR-INFER-05 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `02_Architecture/09_ERROR_HANDLING.md#error-code`.
In plain words: a local server that is still loading a model, or a rate-limited cloud endpoint, deserves a second try;
a wrong key or an unknown model does not. Retry lives with the client so neither the engine nor a screen re-implements
it. A slow first attempt must not eat the second attempt's time.

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

WHEN discovery is asked of an Ollama-native provider, the system SHALL `GET <baseUrl>/api/tags` and return each entry's
`name` as a model id.

WHEN discovery is asked of an OpenAI-compatible provider, the system SHALL `GET <baseUrl>/models` and return each
entry's `id` from `data`.

IF the listing call fails for a reason other than `auth`, `unreachable`, `timeout` or `cancelled`, or the body cannot
be read as a listing, THEN the system SHALL answer `ErrorCode.discoveryFailed`. An empty listing is a success with no
models.

**Source:** FR-MODEL-01, FR-MODEL-02 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`),
`02_Architecture/04_LLM_INTEGRATION.md#model-discovery`.
In plain words: a screen will fill a list from this; a command line checks that a typed model id is really there. An
empty list is not an error: a server with nothing loaded is a valid state, and manual entry stays possible.

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

WHEN the connection of an Ollama-native provider is probed, the system SHALL `GET <baseUrl>/api/version`. WHEN the
connection of an OpenAI-compatible provider is probed, the system SHALL `GET <baseUrl>/models`.

The probe SHALL count any HTTP answer as reachable except `401` and `403` (`ErrorCode.auth`) and `500` to `599`
(`ErrorCode.upstream`); a refused connection is `ErrorCode.unreachable` and an elapsed timeout is `ErrorCode.timeout`.

**Source:** FR-PROV-06 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`),
`02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#three-stage-verification`.
In plain words: "can I reach it, and does it accept my key" is a different question from "what models does it have",
so a server without a listing endpoint can still pass the first. A `404` from a proxy that hides the listing route is
still a server that answered.

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

WHEN verification of a provider id and a model id is asked for, the system SHALL run these stages in order and stop at
the first failure, reporting each stage that ran with its own outcome:

1. **Connection**: the provider is probed.
2. **Models**: discovery runs; the stage passes when the model id is in the list, fails with `modelUnavailable` when
   the list does not contain it, and passes softly, with a note that the list could not be read, when discovery
   answers `discoveryFailed` or an empty list.
3. **Inference**: a schema-constrained probe asks for exactly `{"status":"ok"}` with reasoning disabled. The stage
   reports `structured output: supported` only for that valid envelope. An explicit format rejection or nonconforming
   structured answer is followed by one plain probe; a nonblank plain reply reports `structured output: not confirmed`.
   A `modelNotFound` answer is reported as `modelUnavailable`; any other error is reported as itself.

IF the provider id is unknown, THEN the system SHALL answer `ErrorCode.validation` and run no stage.

**Source:** FR-PROV-06, FR-PROV-07 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), FR-MODEL-05,
FR-MODEL-06 (`#fr-model`), FR-INFER-08 (`#fr-infer`), `02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`.
In plain words: three short questions, each with its own answer, so a person sees which one failed: the server, the
model name, or generation itself. The order saves time: an unreachable server never gets a model list asked of it. For
a model the list cannot confirm, one short generation is the availability check.

#### Scenario: All three stages pass

- **WHEN** `ollama` is verified with `gemma4:e4b-mlx`, `/api/version` answers `200`, `/api/tags` lists
  `gemma4:e4b-mlx`, and `/api/chat` answers `OK` with a normal finish
- **THEN** the report holds three passed stages in the order connection, models, inference

#### Scenario: The model is not in the list

- **WHEN** `/api/tags` lists only `llama3.2:3b` and `gemma4:e4b-mlx` is verified
- **THEN** the report holds a passed connection stage and a models stage failed with `ErrorCode.modelUnavailable`
- **AND** no chat call is made

#### Scenario: Discovery fails softly and inference decides

- **WHEN** `/v1/models` answers `200 {"data":"nope"}` and `/v1/chat/completions` answers `OK`
- **THEN** the models stage is reported as a soft pass noting `discoveryFailed`
- **AND** the inference stage passes

#### Scenario: An unknown model on a manually typed id

- **WHEN** discovery answered an empty list and `/api/chat` answers `404 {"error":"model 'nope' not found"}`
- **THEN** the inference stage fails with `ErrorCode.modelUnavailable`

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
