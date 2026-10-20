## ADDED Requirements

### Requirement: Add the provider's headers and its key to every request

The system SHALL send a provider's custom headers with every request to it, SHALL replace each `${env:NAME}` in a header value with the value of the environment variable `NAME` when the request is made, and SHALL send the provider's key in the way its sign-in says (a Bearer token, or a named header).

**Source**: H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md` (Settings, Providers), ADR-0043. In plain words: a gateway that wants an extra header, or a server that wants a key, works without the key ever being saved: the value is read from the person's environment at the moment of the call.

#### Scenario: A bearer key is sent

- **WHEN** a provider signs in with a Bearer token from `HOME_API_KEY` whose value is `sk-test-123` and a chat call is made
- **THEN** the request carries the header `Authorization: Bearer sk-test-123`

#### Scenario: A named key header is sent

- **WHEN** a provider signs in with the API-key header `X-Api-Key` from `OFFICE_KEY` whose value is `k-777`
- **THEN** the request carries `X-Api-Key: k-777` and no `Authorization` header

#### Scenario: A custom header is resolved

- **WHEN** a provider has the header `X-Team` with the value `${env:TEAM_NAME}` and `TEAM_NAME` is `translation`
- **THEN** the request carries `X-Team: translation`

### Requirement: Refuse a call whose key is missing before sending anything

IF a provider names an environment variable for its key or for a header and that variable is not set, THEN the system SHALL answer `ErrorCode.missingCredential` naming the variable, before any request is sent, and SHALL NOT include any key value in the answer.

**Source**: H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`. In plain words: a call without its key would only come back as a confusing 401. Saying "the variable `HOME_API_KEY` is not set" lets the person fix the real cause.

#### Scenario: An unset key variable

- **WHEN** a provider signs in with a Bearer token from `HOME_API_KEY` and `HOME_API_KEY` is not set
- **THEN** the call answers `ErrorCode.missingCredential` mentioning `HOME_API_KEY`
- **AND** the server received 0 requests

#### Scenario: An unset header variable

- **WHEN** a header value is `${env:TEAM_NAME}` and `TEAM_NAME` is not set
- **THEN** the call answers `ErrorCode.missingCredential` mentioning `TEAM_NAME`

### Requirement: Never show the key in a log line or in the test dialog

The system SHALL NOT write a key or a resolved header value to a log line at any level, and SHALL show the key as hidden in a request it displays or copies for the test dialog.

**Source**: `docs/specification/mockups/README.md` (Settings, Test connection / models / inference), `.claude/rules/logging.md`. In plain words: logs and copied commands get shared with other people, so the secret is replaced by a mask wherever the request is displayed.

#### Scenario: The request line hides the key

- **WHEN** the test dialog shows a request to a provider whose Bearer key is `sk-test-123`
- **THEN** the displayed headers read `Authorization: Bearer ***` and the log contains no `sk-test-123`

### Requirement: Look once for a local AI program at first launch

WHEN the application starts for the first time, the system SHALL ask `http://127.0.0.1:11434` and `http://127.0.0.1:1234/v1` for their model lists with a 1 second connect timeout, SHALL send no prompt and no book text, SHALL store a provider for a program that answers with models, SHALL fail silently when none does, and SHALL NOT repeat the look by itself.

**Source**: ADR-0044, `docs/specification/mockups/README.md` (First launch and the AI model). In plain words: someone who just installed Ollama should find "AI model: Ollama — ready" on the first screen instead of an empty settings form. The look is local, listing only, happens once and says nothing if it finds nothing.

#### Scenario: Ollama is found

- **WHEN** the first launch asks `http://127.0.0.1:11434/api/tags` and it lists only `gemma4:e4b`
- **THEN** an Ollama provider is stored with `gemma4:e4b` selected
- **AND** the look is recorded as done

#### Scenario: Nothing answers

- **WHEN** neither address answers within 1 second
- **THEN** no provider is stored, no error is shown, and the look is recorded as done

#### Scenario: A second launch does not look again

- **WHEN** the application starts and the look is recorded as done
- **THEN** neither address is asked

#### Scenario: Only listing is requested

- **WHEN** the look runs against a server that answers
- **THEN** the server received only the model-list requests and no chat request

### Requirement: Describe how a provider signs in

The system SHALL describe a provider's sign-in as none, a Bearer token, or a named API-key header, SHALL hold for the last two only the name of the environment variable that holds the key, and SHALL NOT hold a key value.

IF a Bearer or API-key sign-in has no variable name, or an API-key sign-in has no header name, THEN the system SHALL refuse the description with `ErrorCode.validation`.

**Source**: FR-PROV-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), ADR-0043. In plain words: a provider says where its key lives, never what the key is, so describing or saving a provider can never leak it.

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

**Source**: H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md` (Settings, Providers). In plain words: servers behind a gateway or with unusual routes need small adjustments, and a provider can name models its server does not list.

#### Scenario: A full description is accepted

- **WHEN** a provider is described with the models override `/catalog/models`, the header `X-Team` set to `${env:TEAM_NAME}` and the custom model `acme-translate-1`
- **THEN** the description is registered holding all three, with the header value kept as the text `${env:TEAM_NAME}`

### Requirement: Use a provider's endpoint overrides

WHERE a provider has a models endpoint override, the system SHALL use it for discovery and for the connection probe of an OpenAI-compatible provider; WHERE an OpenAI-compatible provider has an inference endpoint override, the system SHALL send its chat calls there.

**Source**: H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md` (Settings, Providers). In plain words: when a server keeps its routes elsewhere, every question to it goes to the route the person named.

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

### Requirement: Wait between attempts

WHEN a retryable call is tried again, the system SHALL wait the `Retry-After` delay when the response carries one, as seconds or as an HTTP date, and otherwise 500 ms doubled on each retry, capped at 8 s, with up to 25 % random jitter, and SHALL give each attempt its own full request timeout.

**Source**: FR-INFER-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`. In plain words: a loaded server needs a moment, and a slow first attempt must not eat the second attempt's time.

#### Scenario: The wait doubles

- **WHEN** a call fails with `503` three times with no `Retry-After`
- **THEN** the wait before the second request is about 500 ms and before the third about 1 s, each within 25 % jitter

#### Scenario: Retry-After as an HTTP date

- **WHEN** the server answers `429` with a `Retry-After` date 3 seconds ahead and then `200`
- **THEN** the second request is sent no sooner than that date

### Requirement: Do not count a refused-setting repeat as an attempt

WHEN a request is sent again only because the server refused an optional setting, the system SHALL NOT count that repeat among the attempts of the retry policy.

**Source**: FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`). In plain words: dropping a setting a model refuses corrects the request; it is not a failure and must not use up one of the three tries.

#### Scenario: A refused-setting repeat keeps all three attempts

- **WHEN** the server answers `400` naming the sent setting `temperature`, then `503` twice, then `200`
- **THEN** the call succeeds after 4 requests

### Requirement: Verify the connection and models stages

WHEN the connection stage runs, the system SHALL probe the provider. WHEN the models stage runs, it SHALL discover the models and report how many were listed, pass when the model id is in the list, fail with `modelUnavailable` when it is not, and pass softly with a note that the list could not be read when discovery answers `discoveryFailed` or an empty list.

**Source**: FR-PROV-06, FR-MODEL-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`. In plain words: first "does anything answer", then "does it hold my model", and a server that cannot list its models is not treated as broken.

#### Scenario: The model is listed

- **WHEN** `/api/tags` lists `llama3.2:3b` and `gemma4:e4b-mlx` and `gemma4:e4b-mlx` is verified
- **THEN** the connection stage passes and the models stage passes reporting 2 models

#### Scenario: An empty list passes softly

- **WHEN** discovery answers an empty list
- **THEN** the models stage passes with a note that the list could not be read

### Requirement: Verify the inference stage with the shape of a run's own calls

WHEN the inference stage runs, the system SHALL send a schema-constrained probe asking for exactly `{"status":"ok"}` with reasoning disabled and an output cap of `64`, and SHALL report `structured output: supported` only for that valid envelope.

**Source**: FR-INFER-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`. In plain words: the test asks the way a real run asks, so a thinking model that spends a capped reply on reasoning fails the test instead of flagging a book later.

#### Scenario: The envelope is supported

- **WHEN** `/api/chat` answers `{"status":"ok"}` with a normal finish
- **THEN** the stage passes and reports `structured output: supported`

#### Scenario: A reasoning model spends the cap

- **WHEN** the reply is empty because the 64-token cap was used on reasoning
- **THEN** the stage fails with `ErrorCode.emptyCompletion`

### Requirement: Fall back to a plain probe and name a missing model

IF the structured probe is rejected or answered nonconformingly, THEN the system SHALL send one plain probe and, for a nonblank reply, report `structured output: not confirmed`; a `modelNotFound` answer SHALL be reported as `modelUnavailable` and any other error as itself.

**Source**: FR-INFER-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`). In plain words: a server that cannot do structured answers can still translate, so the test says "not confirmed" rather than "failed".

#### Scenario: A server without structured output

- **WHEN** the structured probe is rejected with `400` and the plain probe answers `OK`
- **THEN** the stage passes and reports `structured output: not confirmed`

#### Scenario: An unknown model

- **WHEN** `/api/chat` answers `404 {"error":"model 'nope' not found"}`
- **THEN** the stage reports `ErrorCode.modelUnavailable`

### Requirement: Run only the stages up to the one asked for

WHEN a shorter check is asked for, the system SHALL run the stages cumulatively up to the one asked for and no further, and SHALL NOT run or report a later stage.

**Source**: FR-PROV-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`). In plain words: each Settings button asks its own question and every cheaper one before it, and nothing after it.

#### Scenario: A connection check stops after one stage

- **WHEN** the connection check is asked for `lmstudio`
- **THEN** one stage is reported and no request reaches `/v1/chat/completions`

#### Scenario: A models check stops before inference

- **WHEN** the models check is asked for `lmstudio`
- **THEN** a connection and a models stage are reported and no chat request is made

### Requirement: Verify a provider description that is not saved

WHEN verification is asked for a provider description that has not been saved, the system SHALL verify it exactly as given, with its endpoints, headers and key, and SHALL NOT register or store it.

**Source**: H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a person can test the form before saving it, so a wrong address never reaches the stored settings.

#### Scenario: An unsaved provider is verified without being stored

- **WHEN** a description `office` at `http://10.0.0.5:1234/v1` that is not stored is verified
- **THEN** the server at that address is asked and the report is returned
- **AND** looking up `office` afterwards returns nothing

## MODIFIED Requirements

### Requirement: Describe a provider by kind, endpoint and timeouts

The system SHALL describe a provider by an id, a display name, a kind (Ollama-native or OpenAI-compatible), a base URL that is absolute and uses `http` or `https`, and a connect timeout and a request timeout, both positive, defaulting to 10 seconds and 180 seconds.

IF a description breaks one of these rules, THEN the system SHALL refuse it with `ErrorCode.validation` and register nothing.

**Source**: FR-PROV-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-config`, ADR-0005, H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a provider is a few settings, not code. The same OpenAI-compatible client serves LM Studio on this machine and any other OpenAI-shaped server. A key is only ever the name of an environment variable, read at call time.

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

The system SHALL offer, without configuration, the provider `ollama` (Ollama-native, `http://localhost:11434`) and the provider `lmstudio` (OpenAI-compatible, `http://localhost:1234/v1`), each with the default timeouts; SHALL answer a lookup by id with the description or with nothing, and list every description; and WHEN an id already known is registered again, SHALL replace its description.

**Source**: FR-PROV-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-factory`, `#provider-profile`, ADR-0043. In plain words: the command line still starts with the two usual servers and can point at another for one run. In the window, the providers a person saves are kept, so they do not have to enter them again.

#### Scenario: The presets exist from the start

- **WHEN** nothing has been registered and the provider `lmstudio` is looked up
- **THEN** an OpenAI-compatible description at `http://localhost:1234/v1` with the default timeouts is returned

#### Scenario: A registration overrides a preset for this run

- **WHEN** the command line registers a provider `ollama` at `http://10.0.0.5:11434`
- **THEN** looking up `ollama` in that run returns `http://10.0.0.5:11434`
- **AND** a fresh process again returns `http://localhost:11434`

#### Scenario: An unknown id returns nothing

- **WHEN** the provider `gemini` is looked up
- **THEN** no description is returned and no error is raised

### Requirement: Retry a retryable failure a bounded number of times

WHEN a call answers a retryable code (`timeout`, `rateLimited`, `unreachable`, `upstream`, `discoveryFailed`), the system SHALL try again, up to 3 attempts in all, and SHALL answer the last failure when every attempt fails. A call that answers a non-retryable code SHALL NOT be repeated.

**Source**: FR-INFER-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#service-owned-retry`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`, FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`). In plain words: a local server that is still loading a model, or a rate-limited cloud endpoint, deserves a second try; a wrong key or an unknown model does not. Dropping a setting a model refuses is a correction of the request, not a failure, so it must not use up one of the three attempts.

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

### Requirement: Discover the models a provider offers

WHEN discovery is asked, the system SHALL `GET <baseUrl>/api/tags` of an Ollama-native provider and return each entry's `name`, or `GET <baseUrl>/models` of an OpenAI-compatible provider and return each entry's `id` from `data`, as model ids.

An empty listing SHALL be a success with no models.

**Source**: FR-MODEL-01, FR-MODEL-02 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#model-discovery`, H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: a screen fills a list from this, and a server that keeps its model list on an unusual path can still be listed. An empty list is not an error: a server with nothing loaded is a valid state, and manual entry stays possible.

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

WHEN the connection of an Ollama-native provider is probed, the system SHALL `GET <baseUrl>/api/version`. WHEN the connection of an OpenAI-compatible provider is probed, the system SHALL `GET <baseUrl>/models`.

The probe SHALL count any HTTP answer as reachable except `401` and `403` (`ErrorCode.auth`) and `500` to `599` (`ErrorCode.upstream`); a refused connection is `ErrorCode.unreachable` and an elapsed timeout is `ErrorCode.timeout`.

**Source**: FR-PROV-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#three-stage-verification`, H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: "can I reach it, and does it accept my key" is a different question from "what models does it have", so a server without a listing endpoint can still pass the first. A `404` from a proxy that hides the listing route is still a server that answered.

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

WHEN verification of a provider id and a model id is asked for, the system SHALL run the connection, models and inference stages in that order, stop at the first failure, and report each stage that ran with its outcome and measured duration.

IF the provider id is unknown and no description is given, THEN the system SHALL answer `ErrorCode.validation` and run no stage.

**Source**: FR-PROV-06, FR-PROV-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), FR-MODEL-05, FR-MODEL-06 (`#fr-model`), FR-INFER-08 (`#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#three-stage-verification`, H4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: three short questions, each with its own answer, so a person sees which one failed: the server, the model name, or generation itself. A provider that is still being typed into the form can be tested before it is saved, so a wrong address never reaches the stored settings.

#### Scenario: All three stages pass

- **WHEN** `ollama` is verified with `gemma4:e4b-mlx`, `/api/version` answers `200` after 41 ms, `/api/tags` lists 3 models including `gemma4:e4b-mlx`, and `/api/chat` answers `{"status":"ok"}` with a normal finish after 1.2 s
- **THEN** the report holds three passed stages in the order connection, models, inference
- **AND** the connection stage reports about 41 ms, the models stage reports 3 models, and the inference stage reports about 1.2 s

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
- **THEN** the inference stage fails with `ErrorCode.modelUnavailable`

#### Scenario: One stage on its own

- **WHEN** only the connection check is asked for `lmstudio` and `/v1/models` answers `200` after 12 ms
- **THEN** the report holds one passed connection stage reporting about 12 ms
- **AND** no chat call is made

#### Scenario: A models check runs the connection stage first

- **WHEN** the models check is asked for `lmstudio` with `google/gemma-4-e4b`, the connection probe answers `200` after 12 ms and `/v1/models` lists 2 models including `google/gemma-4-e4b`
- **THEN** the report holds a passed connection stage reporting about 12 ms and a passed models stage reporting 2 models, in that order
- **AND** no request reaches `/v1/chat/completions`

### Requirement: Cap a chat call's output where the request states a cap

WHEN a chat request carries an output cap, the system SHALL send it to an Ollama-native provider as `options.num_predict` and to an OpenAI-compatible provider as `max_tokens`, or as `max_completion_tokens` where the model's settings choose it; WHEN it carries none, neither field. WHILE an Ollama-native reply streams past the runaway bound, the system SHALL stop reading, close the connection and return what arrived as finished for length.

**Source**: FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#client-implementations`, `docs/specification/mockups/README.md` (Settings, Models). In plain words: a model that loops on one sentence would otherwise write until the server's own limit. The request states where the server must stop, and newer OpenAI-style models want that limit under another name, so the person's per-model choice decides which. A call that states no cap is left as unbounded as before. Ollama streams about one token per line, so a reply far past twice its cap is a model looping, and the client cuts it there. Which calls carry a cap, and how large it is, is the `translation-pipeline` capability's rule.

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
