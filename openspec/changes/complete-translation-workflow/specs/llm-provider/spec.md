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

### Requirement: Cap a chat call's output where the request states a cap

WHEN a chat request carries an output cap, the system SHALL send it to an Ollama-native provider as `options.num_predict`
and to an OpenAI-compatible provider as `max_tokens`. WHEN the request carries none, the system SHALL send neither field.
WHILE an Ollama-native reply streams, IF it has sent more than `2 × cap + 64` lines (with no cap, `2 × context size +
64`), THEN the system SHALL stop reading, close the connection, and return what arrived as a reply that finished for
length, instead of waiting for the call's timeout.

**Source:** FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`,
`openspec/changes/complete-translation-workflow/proposal.md#what-changes`.
In plain words: a model that loops on one sentence would otherwise write until the server's own limit, and the run would
wait for all of it. The request states where the server must stop, each dialect names that field its own way, and a call
that states no cap is left as unbounded as before. Ollama streams about one token per line, so a reply far past twice
its cap is a model looping on a server that did not stop it; the client cuts it there rather than let one call hold the
run for three minutes. Which calls carry a cap, and how large it is, is
the `translation-pipeline` capability's rule.

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

### Requirement: Choose a chat call's timeout by its kind and its expected output

The system SHALL wait for each attempt of a judge call for 90 seconds, and for each attempt of a glossary prescan or a
rolling-summary call for 120 seconds, whatever the provider's request timeout. For every other chat call it SHALL wait
for the longer of the provider's request timeout and 0.5 seconds per output token the request expects, the latter at
most 600 seconds — `max(request timeout, min(600 s, expected output tokens × 0.5 s))`. WHEN such a request states no
expected output, the system SHALL wait for the provider's request timeout. The request timeout is the provider
description's own — 180 seconds by default, or the command line's `--timeout` — and the system SHALL never wait less
than it for a call that is neither a judge, a prescan nor a summary call.

**Source:** FR-INFER-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `#provider-config`,
`docs/next_features.md` §15; the stalled re-judge of the Bartimaeus hand test (tasks 15b).
In plain words: a page from a slow model can need far longer than the three minutes a line needs, so a call that expects
a long answer waits longer, up to ten minutes. The configured timeout is the floor for those calls: a short call never
gets less than the default or the person's `--timeout`, because the first call after Ollama reloads a model for a larger
context, or a prompt read on a CPU, can take minutes before the first word, and cutting it off would pause the run for
nothing. A timeout set above ten minutes is kept as set. The judge and the helper calls are different: their replies are
short and capped, so one still running after a minute and a half (two minutes for a helper) is stuck rather than slow,
and the flat three minutes only delayed the retry. Which calls state an expected output is the `translation-pipeline`
capability's rule; the short verification calls state none and keep the request timeout.

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

### Requirement: Read an Ollama-native reply as a stream that must keep arriving

The system SHALL ask an Ollama-native provider for a streamed reply and SHALL read it line by line, each line one JSON
object. It SHALL join the `message.content` of every line in order and take the finish reason and the token counts from
the last line, so the caller receives the same single response a whole reply gives. WHEN no line arrives for the idle
gap — 60 seconds, or the provider's request timeout when that is shorter — before the first line or between two lines,
or the whole reply runs past the attempt's timeout, the system SHALL end the attempt with `ErrorCode.timeout` and close
the connection. WHEN a line carries an `error` field, the attempt SHALL end with `ErrorCode.upstream`. An interrupted
caller SHALL end the attempt at once with `ErrorCode.cancelled`. `ChatModel.chat` stays synchronous: no caller sees a
partial reply, and the OpenAI-compatible client keeps asking for a whole reply.

**Source:** FR-INFER-04a, FR-INFER-05 (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`; tasks 15b.
In plain words: the JDK's request timeout stops counting once the reply begins, so a model that stalls halfway — or a
runner that stays busy after a cut connection — held the call for the whole three minutes. A stream shows whether the
model is still writing, so a stall is caught within a minute while a long, steady reply is never cut short.

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

### Requirement: Retry a timed-out call once, and never as the same request

WHEN an attempt of a chat call ends with `ErrorCode.timeout`, the system SHALL send at most one more attempt for that
call, and that attempt SHALL carry a sampling seed and, when the request states an output cap, three quarters of that cap.
A call SHALL make at most 2 attempts that time out; the other retryable codes keep the 3-attempt budget and the
`Retry-After` wait of the provider retry requirement. Each attempt SHALL be logged with its call kind and attempt
number, and a call that gives up SHALL be logged as a warning with its code.

**Source:** `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`; tasks 15b.
In plain words: a reply that ran until the timeout most likely looped, and the identical request at a low temperature
loops the same way, so three identical retries cost nine minutes for nothing. One varied retry gives the model a
different path and a shorter leash, and then the failure goes to the run, which decides what to do with it.

#### Scenario: A timeout then an answer

- **WHEN** the first attempt of a request capped at 400 tokens times out and the second is answered
- **THEN** the second request carries `"seed":2` and the cap 300, and the call succeeds after 2 requests

#### Scenario: Two timeouts end the call

- **WHEN** every attempt times out
- **THEN** the result is `ErrorCode.timeout` and the server received exactly 2 requests

## MODIFIED Requirements

### Requirement: Turn reasoning off for an OpenAI-compatible call, and retry a reply that spent its cap reasoning

WHEN a chat request that disables reasoning goes to an OpenAI-compatible provider, the system SHALL send
`"reasoning_effort":"none"`; a request that leaves reasoning to the provider SHALL carry no `reasoning_effort` key. IF
the server answers `400` naming the control as unsupported, THEN the request SHALL be sent once more without it, as for
the Ollama-native `think` (the `inference` capability).

IF a capped reply has blank `content`, non-blank `reasoning` or `reasoning_content` and the finish `length`, THEN the
system SHALL send the same request once more with its cap raised to four times, bounded by the request's context size
when one is given, before reading the reply; a second such reply SHALL answer `ErrorCode.emptyCompletion`. Neither
reasoning field SHALL ever reach the reply text.

**Source:** FR-INFER-02, FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`.
In plain words: through `/v1`, a thinking model (gemma4 on Ollama or LM Studio) writes every output token into
`message.reasoning` unless told not to, so a call capped at 128–190 tokens came back with no translation and 114 of 163
segments of the earth-gravity EPUB were flagged as empty. Both servers honour `reasoning_effort: none` (checked against
Ollama 0.x `/v1` and LM Studio with `google/gemma-4-e4b`); a server that does not still gets one chance with room to
reason and answer.

#### Scenario: LM Studio is told to skip reasoning

- **WHEN** a draft call with reasoning disabled and the cap `183` goes to LM Studio
- **THEN** the body posted to `/v1/chat/completions` has `"reasoning_effort":"none"` and `"max_tokens":183`

#### Scenario: A reply that only reasoned is asked for again with room

- **WHEN** the reply to a call capped at `128` with the context size `8192` is
  `{"choices":[{"message":{"content":"","reasoning_content":"Thinking Process: …"},"finish_reason":"length"}]}`
- **THEN** the same request is sent once more with `"max_tokens":512`, and its reply is the call's answer

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

**Source:** FR-INFER-01, FR-INFER-02, FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `#response-handling`,
`#effective-context`, `docs/next_features.md` §11.
In plain words: Ollama is spoken to natively because its OpenAI-shaped shim drops native controls and hides Ollama's
own error bodies. Its default context is small and silently truncates a long prompt, so a run now tells it the context
size it needs; absent fields remain omitted because a strict server rejects an explicit `null`.

#### Scenario: A full request

- **WHEN** a request with temperature `0.2`, the context size `8192` and a JSON-schema response format is sent to the
  provider `ollama` for the model `gemma4:e4b-mlx` with reasoning disabled
- **THEN** the body posted to `/api/chat` has `"model":"gemma4:e4b-mlx"`, `"stream":true`,
  `"options":{"temperature":0.2,"num_ctx":8192}` and a `"format"` object holding that schema
- **AND** it has `"think":false`, no `keep_alive` key, and `/api/show` is never requested

#### Scenario: An output cap joins the options

- **WHEN** a request with the context size `8192` and an output cap of `80` is sent
- **THEN** the body has `"options":{"num_ctx":8192,"num_predict":80}`

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
off by length, and anything else is other. A `reasoning` or `reasoning_content` field and `tool_calls` SHALL never
reach the reply text; the reasoning fields are read only to notice a reply that spent its cap reasoning (above). The system
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
