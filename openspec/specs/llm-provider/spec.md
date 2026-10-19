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
- `stream`: `true` (the reply is read as the stream requirement above says);
- `options`: an object holding `temperature`, the request's temperature, only when one is given, and `num_ctx`, the
  request's context size, only when one is given, and `num_predict`, the request's output cap, only when one is given,
  and `seed`, the request's sampling seed, only when one is given;
  the `options` object itself is left out when none of them is given;
- `format`: the request's JSON schema, only when a response format is given.
- `think`: `false`, only when the request explicitly disables reasoning output.

A field whose value is absent SHALL be left out of the body, never sent as `null`. The system SHALL NOT send
`keep_alive` or any other option, and SHALL NOT read `<baseUrl>/api/show`.

**Source**: FR-INFER-01, FR-INFER-02, FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`, `#effective-context`, `docs/next_features.md` §11. In plain words: Ollama is spoken to natively because its OpenAI-shaped shim drops native controls and hides Ollama's own error bodies. Its default context is small and silently truncates a long prompt, so a run now tells it the context size it needs; absent fields remain omitted because a strict server rejects an explicit `null`.

#### Scenario: A full request

- **WHEN** a request with temperature `0.2`, the context size `8192` and a JSON-schema response format is sent to the
  provider `ollama` for the model `gemma4:e4b-mlx` with reasoning disabled
- **THEN** the body posted to `/api/chat` has `"model":"gemma4:e4b-mlx"`, `"stream":true`,
  `"options":{"temperature":0.2,"num_ctx":8192}` and a `"format"` object holding that schema
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

### Requirement: Shape an OpenAI-compatible chat request

WHEN a chat request goes to an OpenAI-compatible provider, the system SHALL `POST` to `<baseUrl>/chat/completions` a
JSON body with:

- `model`: the bound model id;
- `messages`: the conversation as `{"role","content"}` objects with the roles `system`, `user` and `assistant`;
- `stream`: `false`;
- `temperature`: the request's temperature, only when one is given;
- `response_format`: `{"type":"json_schema","json_schema":{"name":<name>,"strict":true,"schema":<schema>}}`, only
  when a response format is given.

A field whose value is absent SHALL be left out of the body, never sent as `null`. The system SHALL send a reasoning
parameter only as "Turn reasoning off for an OpenAI-compatible call, and retry a reply that spent its cap reasoning"
states, and no other.

**Source**: FR-INFER-01, FR-INFER-03, FR-INFER-09 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`, ADR-0013. In plain words: one client covers LM Studio, llama.cpp, vLLM and any other OpenAI-shaped server. The only reasoning parameter ever sent is `reasoning_effort: "none"`, on a request that disables reasoning: sending `reasoning_effort: "low"` to a model that does not reason has been observed to turn thinking on and leave the answer empty, so the parameter is never guessed, and the reply is stripped as well.

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
the first failure, reporting each stage that ran with its own outcome and its measured duration:

1. **Connection**: the provider is probed.
2. **Models**: discovery runs; the stage reports how many models were listed, passes when the model id is in the list,
   fails with `modelUnavailable` when the list does not contain it, and passes softly, with a note that the list could
   not be read, when discovery answers `discoveryFailed` or an empty list.
3. **Inference**: a schema-constrained probe asks for exactly `{"status":"ok"}` with reasoning disabled and an output
   cap of `64` — the shape of a run's own calls, so a server whose thinking model spends a capped reply on reasoning
   fails here (`emptyCompletion`) instead of passing an uncapped probe and then flagging a book. The stage
   reports `structured output: supported` only for that valid envelope. An explicit format rejection or nonconforming
   structured answer is followed by one plain probe; a nonblank plain reply reports `structured output: not confirmed`.
   A `modelNotFound` answer is reported as `modelUnavailable`; any other error is reported as itself.

WHEN a shorter check is asked for, the system SHALL run the stages cumulatively up to the one asked for and no further:
a connection check runs the connection stage only; a models check runs connection then models; an inference check runs
all three. Each stage that ran reports its outcome, duration and, for the models stage, count; a stage after the one
asked for is not run and not reported.

IF the provider id is unknown, THEN the system SHALL answer `ErrorCode.validation` and run no stage.

**Source**: FR-PROV-06, FR-PROV-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), FR-MODEL-05, FR-MODEL-06 (`#fr-model`), FR-INFER-08 (`#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`, `docs/specification/01_Product/07_SETTINGS.md#providers-tab`. In plain words: three short questions, each with its own answer, so a person sees which one failed: the server, the model name, or generation itself. The Providers tab has one button per question and shows what each measured — "reachable · 41 ms", "3 models", "inference · 1.2 s" — and each button asks its own question and every cheaper one before it, so a models check never reports a list from a server it has not first reached, and each stage reports its time and, for the listing, its count.

#### Scenario: All three stages pass

- **WHEN** `ollama` is verified with `gemma4:e4b-mlx`, `/api/version` answers `200` after 41 ms, `/api/tags` lists 3
  models including `gemma4:e4b-mlx`, and `/api/chat` answers `{"status":"ok"}` with a normal finish after 1.2 s
- **THEN** the report holds three passed stages in the order connection, models, inference
- **AND** the connection stage reports about 41 ms, the models stage reports 3 models, and the inference stage reports
  about 1.2 s

#### Scenario: The model is not in the list

- **WHEN** `/api/tags` lists only `llama3.2:3b` and `gemma4:e4b-mlx` is verified
- **THEN** the report holds a passed connection stage and a models stage failed with `ErrorCode.modelUnavailable`,
  reporting 1 model
- **AND** no chat call is made

#### Scenario: Discovery fails softly and inference decides

- **WHEN** `/v1/models` answers `200 {"data":"nope"}` and `/v1/chat/completions` answers `OK`
- **THEN** the models stage is reported as a soft pass noting `discoveryFailed`
- **AND** the inference stage passes

#### Scenario: An unknown model on a manually typed id

- **WHEN** discovery answered an empty list and `/api/chat` answers `404 {"error":"model 'nope' not found"}`
- **THEN** the inference stage fails with `ErrorCode.modelUnavailable`

#### Scenario: One stage on its own

- **WHEN** only the connection check is asked for `lmstudio` and `/v1/models` answers `200` after 12 ms
- **THEN** the report holds one passed connection stage reporting about 12 ms
- **AND** no chat call is made

#### Scenario: A models check runs the connection stage first

- **WHEN** the models check is asked for `lmstudio` with `google/gemma-4-e4b`, the connection probe answers `200` after
  12 ms and `/v1/models` lists 2 models including `google/gemma-4-e4b`
- **THEN** the report holds a passed connection stage reporting about 12 ms and a passed models stage reporting 2 models,
  in that order
- **AND** no request reaches `/v1/chat/completions`

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

WHEN a chat request carries an output cap, the system SHALL send it to an Ollama-native provider as `options.num_predict` and to an OpenAI-compatible provider as `max_tokens`; WHEN it carries none, the system SHALL send neither field. WHILE an Ollama-native reply streams past the runaway bound, the system SHALL stop reading, close the connection and return what arrived as a reply that finished for length, instead of waiting for the call's timeout.

**Source**: FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `openspec/changes/archive/2026-10-19-complete-translation-workflow/proposal.md#what-changes`. In plain words: a model that loops on one sentence would otherwise write until the server's own limit, and the run would wait for all of it. The request states where the server must stop, each dialect names that field its own way, and a call that states no cap is left as unbounded as before. Ollama streams about one token per line, so a reply far past twice its cap is a model looping on a server that did not stop it; the client cuts it there rather than let one call hold the run for three minutes. Which calls carry a cap, and how large it is, is the `translation-pipeline` capability's rule.

#### Scenario: LM Studio receives max_tokens

- **WHEN** a request with an output cap of `80` is sent to the provider `lmstudio` for the model `google/gemma-4-e4b`
- **THEN** the body posted to `/v1/chat/completions` has `"max_tokens":80`

#### Scenario: No cap sends no max_tokens

- **WHEN** a request with no output cap is sent to the provider `lmstudio`
- **THEN** the posted body has no `max_tokens` key

#### Scenario: A runaway stream is cut as length

- **WHEN** a request with an output cap of `20` is sent to the provider `ollama` and the reply keeps streaming
  one-token lines with no final part
- **THEN** after 104 lines the client stops reading and answers a reply whose finish is cut off by length, well before
  the call's timeout

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

WHEN a chat request that disables reasoning goes to an OpenAI-compatible provider, the system SHALL send `"reasoning_effort":"none"`. IF a capped reply has blank `content`, non-blank reasoning and the finish `length`, THEN the system SHALL send the same request once more with its cap raised to four times, bounded by the request's context size when one is given; a second such reply SHALL answer `ErrorCode.emptyCompletion`.

**Source**: FR-INFER-02, FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`. In plain words: through `/v1`, a thinking model (gemma4 on Ollama or LM Studio) writes every output token into `message.reasoning` unless told not to, so a call capped at 128–190 tokens came back with no translation and 114 of 163 segments of the earth-gravity EPUB were flagged as empty. Both servers honour `reasoning_effort: none` (checked against Ollama 0.x `/v1` and LM Studio with `google/gemma-4-e4b`); a server that does not still gets one chance with room to reason and answer.

#### Scenario: LM Studio is told to skip reasoning

- **WHEN** a draft call with reasoning disabled and the cap `183` goes to LM Studio
- **THEN** the body posted to `/v1/chat/completions` has `"reasoning_effort":"none"` and `"max_tokens":183`

#### Scenario: A reply that only reasoned is asked for again with room

- **WHEN** the reply to a call capped at `128` with the context size `8192` is
  `{"choices":[{"message":{"content":"","reasoning_content":"Thinking Process: …"},"finish_reason":"length"}]}`
- **THEN** the same request is sent once more with `"max_tokens":512`, and its reply is the call's answer

#### Scenario: A server that rejects the control gets the request once more without it

- **WHEN** the server answers `400` naming the `reasoning_effort` control as unsupported
- **THEN** the request is sent once more without it, as for the Ollama-native `think` (the `inference` capability)

#### Scenario: A request that leaves reasoning to the provider carries no control

- **WHEN** a request that does not disable reasoning goes to an OpenAI-compatible provider
- **THEN** the body has no `reasoning_effort` key

#### Scenario: Either reasoning field marks a reply that spent its cap

- **WHEN** a capped reply has blank `content`, the finish `length`, and non-blank `reasoning` or `reasoning_content`
- **THEN** the same request is sent once more with its cap raised

#### Scenario: A second reply that only reasoned is empty

- **WHEN** the repeated request is also answered with blank `content`, reasoning and the finish `length`
- **THEN** the call answers `ErrorCode.emptyCompletion`

#### Scenario: Reasoning never reaches the reply text

- **WHEN** a reply carries `reasoning` or `reasoning_content`
- **THEN** neither field is part of the reply text
