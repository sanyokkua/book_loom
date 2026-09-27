# Spec Delta

## ADDED Requirements

### Requirement: Never send a context size to an OpenAI-compatible server

WHEN a chat request that carries a context size goes to an OpenAI-compatible provider, the system SHALL leave the
context size out of the body entirely, under any field name.

**Source:** FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#effective-context`.
In plain words: the OpenAI chat format has no context-size field; LM Studio and similar servers set it when the model is
loaded, and a strict server may reject a field it does not know. Only Ollama is told the size.

#### Scenario: LM Studio receives no context field

- **WHEN** a request with temperature `0.2` and the context size `8192` is sent to the provider `lmstudio` for the model
  `google/gemma-4-e4b`
- **THEN** the body posted to `/v1/chat/completions` has `"temperature":0.2` and no `num_ctx`, `n_ctx`, `context_length`
  or `options` key

### Requirement: Scale a chat call's timeout with its expected output

The system SHALL wait for each attempt of a chat call for the longer of the provider's request timeout and 0.5 seconds
per output token the request expects, the latter at most 600 seconds — `max(request timeout, min(600 s, expected output
tokens × 0.5 s))`. WHEN a request states no expected output, the system SHALL wait for the provider's request timeout.
The request timeout is the provider description's own — 180 seconds by default, or the command line's `--timeout` — and
the system SHALL never wait less than it.

**Source:** FR-INFER-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `#provider-config`,
`docs/next_features.md` §15.
In plain words: a page from a slow model can need far longer than the three minutes a line needs, so a call that expects
a long answer waits longer, up to ten minutes. The configured timeout is the floor: a short call never gets less than the
default or the person's `--timeout`, because the first call after Ollama reloads a model for a larger context, or a
prompt read on a CPU, can take minutes before the first word, and cutting it off would pause the run for nothing. A
timeout set above ten minutes is kept as set. Which calls state an expected output is the `translation-pipeline`
capability's rule; the short verification calls state none and keep the request timeout.

#### Scenario: A medium segment gets a proportional wait

- **WHEN** a draft request expects 400 output tokens, the provider has the default request timeout, and the server sends
  nothing
- **THEN** the attempt ends with `ErrorCode.timeout` after 200 seconds, with the details naming the 200-second timeout

#### Scenario: A short segment keeps the request timeout

- **WHEN** a draft request expects 40 output tokens and the provider has the default request timeout
- **THEN** its attempt waits up to 180 seconds

#### Scenario: A huge segment gets the ceiling

- **WHEN** a draft request expects 2,000 output tokens and the provider has the default request timeout
- **THEN** its attempt waits up to 600 seconds

#### Scenario: A verification call keeps the provider timeout

- **WHEN** the inference stage of verification sends its probe to a provider with the default request timeout
- **THEN** its attempt waits up to 180 seconds
- **AND** a judge call, which states no expected output, also waits up to 180 seconds

#### Scenario: The command line's timeout is the floor

- **WHEN** the translate command runs with `--timeout 30`
- **THEN** a draft request expecting 40 output tokens waits up to 30 seconds
- **AND** a draft request expecting 400 output tokens waits up to 200 seconds

#### Scenario: A long configured timeout is never cut

- **WHEN** the provider's request timeout is 900 seconds and a draft request expects 2,000 output tokens
- **THEN** its attempt waits up to 900 seconds

## MODIFIED Requirements

### Requirement: Shape an Ollama-native chat request

WHEN a chat request goes to an Ollama-native provider, the system SHALL `POST` to `<baseUrl>/api/chat` a JSON body
with:

- `model`: the bound model id;
- `messages`: the conversation as `{"role","content"}` objects with the roles `system`, `user` and `assistant`;
- `stream`: `false`;
- `options`: an object holding `temperature`, the request's temperature, only when one is given, and `num_ctx`, the
  request's context size, only when one is given; the `options` object itself is left out when neither is given;
- `format`: the request's JSON schema, only when a response format is given.
- `think`: `false`, only when the request explicitly disables reasoning output.

A field whose value is absent SHALL be left out of the body, never sent as `null`. The system SHALL NOT send
`keep_alive` or any other option, and SHALL NOT read `<baseUrl>/api/show`.

**Source:** FR-INFER-01, FR-INFER-02, FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`,
`#effective-context`, `docs/next_features.md` §11.
In plain words: Ollama is spoken to natively because its OpenAI-shaped shim drops native controls and hides Ollama's
own error bodies. Its default context is small and silently truncates a long prompt, so a run now tells it the context
size it needs; absent fields remain omitted because a strict server rejects an explicit `null`.

#### Scenario: A full request

- **WHEN** a request with temperature `0.2`, the context size `8192` and a JSON-schema response format is sent to the
  provider `ollama` for the model `gemma4:e4b-mlx` with reasoning disabled
- **THEN** the body posted to `/api/chat` has `"model":"gemma4:e4b-mlx"`, `"stream":false`,
  `"options":{"temperature":0.2,"num_ctx":8192}` and a `"format"` object holding that schema
- **AND** it has `"think":false`, no `keep_alive` key, and `/api/show` is never requested

#### Scenario: A context size alone

- **WHEN** a request with the context size `8192` and no temperature is sent
- **THEN** the body has `"options":{"num_ctx":8192}`

#### Scenario: Absent settings are left out

- **WHEN** a request with no temperature, no context size and no response format is sent
- **THEN** the body has no `options` key and no `format` key

### Requirement: Read a reply and report its finish

WHEN an Ollama-native provider answers `200` to `/api/chat`, the system SHALL take the reply text from
`message.content` and the finish from `done_reason`: `stop` is a normal finish, `length` is cut off by length, and
anything else is other. A `message.thinking` field SHALL be ignored. The system SHALL take the token usage from
`prompt_eval_count` (prompt tokens), `eval_count` (completion tokens) and `eval_duration` (generation time, in
nanoseconds), each only when present.

WHEN an OpenAI-compatible provider answers `200` to `/chat/completions`, the system SHALL take the reply text from
`choices[0].message.content` and the finish from `choices[0].finish_reason`: `stop` is a normal finish, `length` is cut
off by length, and anything else is other. A `reasoning_content` field and `tool_calls` SHALL be ignored. The system
SHALL take the token usage from `usage.prompt_tokens` and `usage.completion_tokens`, each only when present, and SHALL
use the call's measured wall-clock time as the generation time when either is present.

IF the body's `model` names a model other than the requested one, THEN the system SHALL still use the reply and SHALL
write one WARN line naming both ids.

IF the reply text is blank before any sanitising, THEN the system SHALL answer `ErrorCode.emptyCompletion`.

**Source:** FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `#empty-response-ordering`, ADR-0013.
In plain words: both dialects collapse to text, a finish reason and, when the server says so, how many tokens it read
and wrote and how long it took — which is where the Translating screen's tokens per second comes from. Ollama measures
its own generation time; an OpenAI-compatible server does not, so the whole call's duration stands in for it. A reply
carrying no usage field carries no usage at all (the `inference` capability). A separate reasoning channel is ignored. LM Studio answers a request for an unknown model id with whichever model is
loaded, so the mismatch is logged rather than trusted silently.

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

### Requirement: Verify a provider and a model in three stages

WHEN verification of a provider id and a model id is asked for, the system SHALL run these stages in order and stop at
the first failure, reporting each stage that ran with its own outcome and its measured duration:

1. **Connection**: the provider is probed.
2. **Models**: discovery runs; the stage reports how many models were listed, passes when the model id is in the list,
   fails with `modelUnavailable` when the list does not contain it, and passes softly, with a note that the list could
   not be read, when discovery answers `discoveryFailed` or an empty list.
3. **Inference**: a schema-constrained probe asks for exactly `{"status":"ok"}` with reasoning disabled. The stage
   reports `structured output: supported` only for that valid envelope. An explicit format rejection or nonconforming
   structured answer is followed by one plain probe; a nonblank plain reply reports `structured output: not confirmed`.
   A `modelNotFound` answer is reported as `modelUnavailable`; any other error is reported as itself.

WHEN a shorter check is asked for, the system SHALL run the stages cumulatively up to the one asked for and no further:
a connection check runs the connection stage only; a models check runs connection then models; an inference check runs
all three. Each stage that ran reports its outcome, duration and, for the models stage, count; a stage after the one
asked for is not run and not reported.

IF the provider id is unknown, THEN the system SHALL answer `ErrorCode.validation` and run no stage.

**Source:** FR-PROV-06, FR-PROV-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`),
FR-MODEL-05, FR-MODEL-06 (`#fr-model`), FR-INFER-08 (`#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`,
`docs/specification/01_Product/07_SETTINGS.md#providers-tab`.
In plain words: three short questions, each with its own answer, so a person sees which one failed: the server, the
model name, or generation itself. The Providers tab has one button per question and shows what each measured — "reachable
· 41 ms", "3 models", "inference · 1.2 s" — and each button asks its own question and every cheaper one before it, so a
models check never reports a list from a server it has not first reached, and each stage reports its time and, for the
listing, its count.

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
