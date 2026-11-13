**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-10-29
**Cross-references:** `docs/spec_baseline/specification/02_Architecture/09_ERROR_HANDLING.md`,
`docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/spec_baseline/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`, `docs/spec_baseline/specification/02_Architecture/05_PIPELINE_ENGINE.md`,
`docs/spec_baseline/adr/ADR-0047-server-engines-and-model-profiles.md`

# LLM Integration

`:llm` owns everything about talking to a model: the provider abstraction, server-engine detection, model discovery, inference, response
sanitization and tolerant parsing, the single-flight gate, retry, HTTP→typed-error mapping, credential resolution, and
provider verification. It is FX-free and reaches the network only through `java.net.http.HttpClient`. Signatures below
are Java-ish contracts in `ua.bookloom.api.llm` (ports) and `ua.bookloom.llm` (impl).

## provider-architecture {#provider-architecture}

The engine never talks to an HTTP dialect directly: it holds a `ChatModel` (`:api.llm`, ADR-0033) already bound to one
provider and model, obtained once from a `ChatModelFactory` —

```java
public interface ChatModel { Result<ChatResponse> chat(ChatRequest request); }
public interface ChatModelFactory { Result<ChatModel> create(ModelSelection selection); }
public record ModelSelection(String providerId, String modelId) {}
```

— the caller resolves the provider and model once and the job keeps one bound `ChatModel` for its whole run, rather
than re-reading the current provider and settings on every call. `pseudo` remains a deterministic, offline option;
the `ollama` and `lmstudio` presets, or a run-local `openai-compatible` registration, create a real gated and retried
model without sending a request until its first `chat` call.

The public provider-facing API has three ports. `ChatModelFactory` resolves a selected provider/model pair;
`ProviderConfigs` registers and finds provider descriptions; and `ProviderVerifier` runs the connection, models and
inference checks and detects the server engine. Callers outside `:llm` use these ports and never branch on a wire dialect.

```java
public interface ProviderConfigs {
    Result<ProviderConfig> register(ProviderConfig config);
    Optional<ProviderConfig> find(String id);
    List<ProviderConfig> all();
}
public interface ProviderVerifier {
    Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy);
    Result<EngineDetection> detectEngine(ProviderConfig config);   // no gate permit: a GET is not inference
}
public record ProviderConfig(
    String id, ProviderKind kind, URI baseUrl, Duration connectTimeout, Duration requestTimeout,
    String displayName, ProviderAuth auth, String apiKeyHeader, String credentialRef,
    String modelsPath, String chatPath, Map<String, String> headers, List<String> customModels,
    ServerEngine engine, @Nullable ServerEngine engineChoice, @Nullable String engineVersion,
    boolean nativeOfferDeclined
) {}
```

(shown without nullability; the five-argument form builds a provider with no sign-in and the engine `GENERIC`.)
`effectiveEngine()` is the person's `engineChoice`, else the detected `engine`. `HttpExchange` builds each request's headers from it through `RequestHeaders`: the JSON headers, the extra headers with `${env:NAME}` resolved, then the sign-in header last. Both clients choose their model-list and chat path through `EndpointPaths`.

The per-dialect `ProviderClient` is an internal `:llm` interface, not a public port. It supplies `probe()`,
`listModels()`, `chat(modelId, request)` and `kind()` to the factory and verifier. `probe()` is a lightweight
reachability check so the Connection and Models stages remain separable: Ollama uses `/api/version` and the
OpenAI-compatible dialect uses `/models`. Calls are synchronous and return the whole `ChatResponse`; the Ollama-native
client reads its reply as a stream internally, so a reply that stops sending is ended by an idle gap of 60 seconds,
while caller-visible streaming remains deferred.

### client-implementations {#client-implementations}

Two concrete clients implement the internal interface, both returning the same `ChatResponse` / `ModelInfo` types so
the pipeline stays dialect-agnostic. The provider **kind** is the transport and chooses the client; the **server
engine** (see #server-engines) selects the fields, error rows and routes inside it, through one traits record that the
OpenAI-compatible request mapper reads, so no per-engine client exists:

- **Ollama-native client** (`kind = OLLAMA`) — uses `/api/version` to probe, `/api/tags` to discover models and
  `/api/chat` for inference. It sends `stream:true` and reads the NDJSON lines into one reply (idle gap 60 s, the
  call's per-kind timeout as the ceiling), plus `options.temperature`, the samplers the model's profile or the
  person's Custom values set, and `format` only when the request provides them. It sends `think` (`false`, `"low"` or
  `true`) only when the request carries a reasoning level, `options.num_ctx` with the model's one window
  (`ModelWindow`, see #effective-context) together with `"truncate":false` so an over-long prompt is refused rather
  than cut, and `"keep_alive":"30m"` only on a translation run's calls (never on a test, a names pass, a brief or a
  file name).
- **OpenAI-compatible client** (`kind = OPENAI_COMPATIBLE`) — uses `/models` to probe and discover and
  `/chat/completions` for inference relative to the configured base URL. It covers LM Studio, llama-server, Unsloth and other
  OpenAI-compatible servers, sending `stream:false`, optional `temperature`, and strict `json_schema`
  `response_format` only when requested and, on a generic server, only after a stored check verdict shows it is honoured. The reasoning control is the engine's own off switch
  (`reasoning_effort:"none"`, `chat_template_kwargs.enable_thinking:false`, or top-level `enable_thinking:false`), a gpt-oss id always gets `reasoning_effort:"low"`, and a generic server gets none until a check has proved a switch. The cap
  goes out as `max_tokens` (and only that on Ollama's `/v1` path, whatever field the model's settings choose) or `max_completion_tokens`; LM Studio, llama-server and Unsloth carry
  their neutral samplers explicitly, and Unsloth carries `"enable_tools":false` on every call. A capped reply whose content is blank, whose `reasoning`/`reasoning_content`
  is not (or whose text is a reasoning opener never closed), and whose finish is `length` is asked for once more —
  with thinking off when it was on, else with four times the cap bounded by the context size; the Ollama-native client
  does the same with `message.thinking`, and a repeat that spends its cap again is `emptyCompletion` with the reason
  `reasoning-exhausted` (`response/ReasoningBudget`). Both clients read the reply through `response/ReasoningScrubber`.

### provider-factory {#provider-factory}

`ProviderClientFactory` is internal to `:llm`: it switches exhaustively on `ProviderConfig.kind()` and caches one
client per provider-config id. Only `OLLAMA` and `OPENAI_COMPATIBLE` ship; another dialect requires a new enum value and
internal client without changing callers of the three public ports. A client is built with the provider's effective
engine, so a changed engine (Detect again, "Treat this server as…") gives the next call a client built from the new one.

### provider-profile {#provider-profile}

There is no per-kind profile type or capability cache; `ProviderConfig` carries the endpoint, the timeouts, the sign-in and the engine fields. What an engine changes is computed on demand by `EngineTraits.of(engine, version)` (a Java switch, never stored): the cap field, the off switch, the extra samplers, the fields always sent and whether the server answers an unknown model id with whatever is loaded; traits join the record with the task that first reads them. The checks a server has passed are kept on the model's settings as `ProbeVerdicts`; the model-profile resolution (engine traits, server facts, name reading, built-in row, the person's override; ADR-0047) is built in later groups of the same change.

### provider-config {#provider-config}

`ProviderConfig` is the in-memory provider description that the factory turns into a client:

```java
public record ProviderConfig(
    String id, ProviderKind kind, URI baseUrl, Duration connectTimeout, Duration requestTimeout,
    ServerEngine engine, @Nullable ServerEngine engineChoice, @Nullable String engineVersion,
    boolean nativeOfferDeclined
    /* plus displayName, auth, apiKeyHeader, credentialRef, modelsPath, chatPath, headers, customModels */
) {}
```

## server-engines {#server-engines}

`ServerEngine` is `OLLAMA`, `LM_STUDIO`, `LLAMA_CPP`, `OPENROUTER`, `UNSLOTH` or `GENERIC` (`:api`, `llm/`). It is
decided by `llm/engine/EngineDetector` and only when the person saves or tests a provider or presses Detect again
(never at start-up or in the background): a saved provider under the `FULL` and `CONNECTION` policies, a draft also under
`CONNECTION_AND_MODELS`, `verifyConnection` always; the run's own recovery probe and `PREFLIGHT` never detect. A draft
whose id, kind and address match a saved provider stores the result on it. Detection also runs after a connection
failure unless it was `unreachable`, `timeout`, `missingCredential`, `cancelled` or `auth`.

- **Probes.** At most 7 body-checked GETs, each with a 2 s connect and read timeout, no body, no book text, and the key
  only when the provider has one. The root is found by stripping a trailing `/`, `/v1`, `/api` or `/api/v1`. Step 0 is
  the host rules (`openrouter.ai` is OpenRouter; `ollama.com` is Ollama; `api.openai.com` and other hosted OpenAI-shaped APIs are
  generic; Anthropic and Gemini hosts are reported as not supported yet, reachable through OpenRouter); step 1
  `root/api/version`, `root/api/tags`, `root/v1/models` (Ollama only when the version body is `{"version":"x.y.z"}`, the tags body is
  `{"models":[…]}`, no row carries a fake marker — `details.family` `koboldcpp`, `details.format` `sglang`, a digest
  starting `sha256:sglang` — and every `/v1/models` row that is listed has `owned_by`; an unanswered or 404 `/v1/models`
  keeps the hit); step 2 `root/health` and `root/props` (llama-server by `Server: llama.cpp`, or `{"status":"ok"}` with
  a `build_info` of the form `b<digits>-…`; `"role":"router"` sets the router flag; a `503 Loading model` is a loading
  llama-server); step 2b `root/api/health` (Unsloth); step 3 `root/api/v0/models` shaped as a list with
  `compatibility_type` (LM Studio); step 4 `base/models` signals (OpenRouter fields, `owned_by` `library` for Ollama's
  `/v1`, `organization_owner` for LM Studio's, `unsloth-studio`), reusing the step-1 reply when the base path is
  `root/v1`. A hit needs the expected body, never a status or a port. Nothing recognised is `GENERIC`, the family named
  at DEBUG only.
- **Refused servers.** GPT4All (`/v1/models` rows with `"owned_by":"humanity"`, reason `not-suitable-structured`) and
  AnythingLLM (a base path ending `/api/v1/openai`, read from the address before any GET, reason
  `not-suitable-documents`) fail the connection stage with `validation`, no engine stored and no chat request sent.
  `EngineDetection.suitability` is `OK`, `NOT_SUITABLE` or `NOT_SUPPORTED_YET` (Anthropic and Gemini hosts: a hint, not a refusal).
- **Mismatch and the offer.** An Ollama-native provider whose address a recognised non-Ollama engine answers fails the
  connection stage naming the engine (a generic answer does not count). When Ollama answers behind an
  `OPENAI_COMPATIBLE` provider the detection carries `offerNativeConnection`; Keep as is sets `nativeOfferDeclined` and
  nothing else, a detection never offers while that flag is set, and nothing is ever switched by itself. Detect again
  clears `engineChoice`, never `nativeOfferDeclined`.
- **Log.** INFO `engine detected provider=<id> engine=LLAMA_CPP version=b11669 probes=3 source=detected|override`
  (host only, never a path, query or key); DEBUG per probe with status and whether the body shape matched.
- **Facts.** `EngineFacts` is session memory read lazily by one GET on the first call that needs it (so an engine named
  by "Treat this server as…" works without a Test), forgotten by a Test or Detect again: today only llama-server's
  template variable and a router's per-id `/props?model=<id>`; an unreadable `/props` means no kwarg, and a router's
  `n_ctx:0` is never a window. Other facts (Ollama's runner, LM Studio's allowed reasoning options) are read by the
  first task whose request field depends on them.
- **LM Studio route.** The body the `/v1` path would carry is posted, unstreamed, to `<root>/api/v0/chat/completions`;
  the reply's `stats.time_to_first_token`, `tokens_per_second` and `generation_time` fill the call's timings
  (`TokenUsage.promptEval` holds the time to first token). A reply whose body is not JSON or is `{"error":"Unexpected
  endpoint…"}`, with status 2xx, 404 or 405, makes `<baseUrl>/chat/completions` the route for that provider for the
  session (INFO once); a plain-text 401, 403, 429 or 5xx does not switch.
- **Model id safety.** For engines whose traits say the server answers an unknown id with whatever is loaded (LM Studio,
  Unsloth) and for a llama-server router, `engine/ModelIdCheck` reads the server's own list once on the first chat call
  per provider and model (cached) and ends an unlisted id with `modelNotFound` and reason `model-not-listed` before any
  chat request (the run pauses on it); the Test's models stage uses the same class; a list that cannot be read lets the
  call go ahead. A typed LM Studio `@variant` suffix is removed (`ModelVariant`; the Providers form strips the stored id
  after a passing models check or Save, not while typing). A reply whose `model` names another model than the one
  requested on such an engine ends with `modelNotFound` and reason `wrong-model-served` (the comparison ignores case, a
  variant and a publisher prefix).
- **Check verdicts.** `ModelSettings.probeVerdicts` holds one nullable verdict each for the answer schema, the thinking
  switch, the extra samplers and the cap field (outcome honoured, ignored or refused; source `test`; time), written
  through `ModelSettingsRepository.update` so a refusal recorded meanwhile survives. They are earned by the optional
  Advanced features stage (see #three-stage-verification).

## chat-contracts {#chat-contracts}

The built `ChatRequest`/`ChatResponse` (`ua.bookloom.api.llm`) are:

```java
public record ChatRequest(
    List<ChatMessage> messages,
    @Nullable Double temperature, @Nullable ResponseFormat responseFormat,
    @Nullable ReasoningLevel reasoningEnabled,      // the intent before the profile, the level sent after it
    @Nullable Integer contextWindow,                // the model's one window; sent only as Ollama's num_ctx
    @Nullable Integer expectedOutputTokens, @Nullable Integer maxOutputTokens, @Nullable Integer seed,
    @Nullable CallKind callKind, @Nullable SamplingParams sampling, @Nullable OutputLengthField outputField,
    @Nullable SourceSize source, @Nullable Integer reasoningReserve,
    boolean keepLoaded                              // a run's call: Ollama keeps the model loaded
) {}
public record ChatResponse(String content, FinishReason finishReason, @Nullable TokenUsage usage) {}
```

`ChatRequest` carries no model field: the model is bound on the `ChatModel` obtained from `ModelSelection`. Every
nullable component is omitted from the serialized JSON when unset. There is no streaming field: the Ollama-native client
streams internally and both clients hand back one whole reply (the OpenAI-compatible client posts `stream:false`). `GatedChatModel` applies the model's stored settings and
profile to each call (`BoundSettings.applied`): the temperature choice, the Custom samplers, the cap, the reasoning
decision and the structured rung.

Before a chat call leaves `GatedChatModel`, `client/MessageShaper` lays its messages out for the model's profile
(design D20): a call with no system message gets BookLoom's own (`MessageShaper.DEFAULT_SYSTEM`; the verification
probe carries it too), so no persona baked into a download is used; a profile with `systemRole` `FOLD` (Gemma 2, Gemma
3 and their tunes) gets the system text, a blank line and the user text as one user message, never two user messages
in a row; `TEMPLATE_WRITES_REASONING` (muse-glimmer) drops any system line naming a reasoning level, strength or
effort, and `HARMONY_FORMAT` (gpt-oss) drops every line starting `Reasoning:`; `ESCAPE_THINK_TOGGLES` puts a word
joiner (U+2060) after the slash of `/think` and `/no_think` in book text and takes it out of the reply. A reply whose
body says `System role not supported` (reason `system-role-unsupported`) stores the refused setting `system_role`
(done instead: `folded`) and is resent folded at no attempt cost; a template that refuses because roles must
alternate (`template-rejected`) is resent once with neighbouring messages of one role merged (not remembered), and a
second refusal stays `validation` `template-rejected`.

## response-handling {#response-handling}

Each call kind asks for its own answer schema (a draft batch for `{"items":[…]}`, a single draft for
`{"target":"…"}`), and the prompt states the same contract, so a call on the prompt-only rung asks for the same JSON.
Both clients reject raw blank content before scrubbing, read a separate reasoning field only to decide the once-only
reasoning re-ask (`response/ReasoningBudget`), scrub every known reasoning and turn marker and one outer code fence
through `response/ReasoningScrubber`, and return the cleaned content to the pipeline. Parsing and the repair belong to
`:pipeline`, not to a client.

**Structured rungs (design D20).** A structured answer is asked on one of two rungs per model and answer schema (the
schema's name is its prompt's name): `SCHEMA`, or `PROMPT_ONLY` with the contract stated in the prompt alone.
`BoundSettings` sends no schema when the model's row marks `PROMPT_ONLY_START` (the default profile included) or when
the call thinks (a reasoning level is sent or room is reserved for reasoning). `GatedChatModel` reads each outcome
through `StructuredRungs`: a schema the server cannot use (`grammar-too-large`, `schema-invalid`, a `400`/`422` naming
the schema), a schema not applied twice in a row (`schema-not-applied`), or, for a row with `QUOTE_CUTS_SCHEMA_STRING`
(Gemma 3 on Ollama), a reply whose `target` stops on a space, colon, comma or dash while the source opens a straight
quotation (`schema-cut-short`, `response/QuoteCut`) moves that schema alone down; a server with no structured output
(`structured-unavailable`) moves every schema of the model. Each step resends the call at no attempt cost and is stored
as `RefusedSetting("response_format", day, "omitted", scope)` — `scope` null for the whole model — which the Models
tab lists with its scope and forgets per scope on Try again now.

### empty-response ordering {#empty-response-ordering}

Empty responses are ordered distinctly from malformed ones:

- A **raw-empty** response — blank/whitespace-only *before* sanitize — maps straight to `ErrorCode.emptyCompletion` (the
  model produced nothing).
- A **non-empty response that sanitizes to empty** is returned as a normal, empty reply. The pipeline's existing
  empty-content decision flags the segment; this change performs no repair retry.

### repair-and-gate {#repair-and-gate}

The pipeline may issue one fresh schema-constrained structural repair for a malformed or wrong-shape reply, including
the rejected reply and a parsing diagnosis. A valid `target` that fails placeholder validation instead receives one
fresh repair with the source, rejected target, and exact ordered token sequence. Each repair re-acquires the
`InferenceGate`, receives a fresh timeout, passes strict parsing and the unchanged unmask gate, and never recurses.

## client-construction {#client-construction}

Both clients share one construction contract:

- **One `java.net.http.HttpClient` per connect timeout** is cached by `HttpClients`; each request still receives a
  fresh `HttpRequest.timeout(...)`, so a slow call cannot consume another call's budget.
- **Request/response DTOs are records** in an internal, non-exported `dto` package, annotated `@JsonInclude(NON_NULL)`
  so nullable/unset parameters are omitted from the body rather than serialized as `null`.
- **A single tolerant `ObjectMapper`** (unknown fields ignored, missing defaulted, whitespace trimmed) is reused for
  both dialects.
- **Never log secrets** — DEBUG logs carry model id, endpoint, sizes and typed error codes; TRACE diagnostics may carry
  request and response bodies, but never a resolved credential or an `AppError` detail outside the safe allowlist.

## model-discovery {#model-discovery}

`listModels()` reads `/api/tags` for Ollama, `/api/v0/models` for LM Studio and `<baseUrl>/models` otherwise. Discovery is a convenience:
the verifier records a discovery failure or empty list as a soft pass, then verifies a manually entered model through
an inference round trip. Lists are chat-only: an entry whose own listing shows an embedding kind (LM Studio `type`
`embeddings`) is dropped, and an Ollama entry whose name ends `:cloud` or `-cloud`, or that carries `remote_host` in
`/api/tags`, gets `ModelInfo.cloud = true` (a neutral label, nothing hidden, no consent; an Ollama behind an
OpenAI-compatible provider is labelled by name suffix only). Dropping sidecar files and other kinds that only the name
or `/api/show` reveals belongs to the model-profile work of a later group. OpenRouter's catalog lists every kind of model,
so for an effective engine `OPENROUTER` only entries whose `architecture.output_modalities` contains `text` are kept (an
image-only or embeddings-only entry is left out); the Test's models stage and the model pickers use this same list.

### openrouter-call-line {#openrouter-call-line}

When a reply names its host or its price, the call's DEBUG line gains `servedProvider=<slug> servedModel=<slug>
cost=<number>`; a field the reply did not carry is `-`, never 0, and nothing but slugs and a number is logged. `CallRecord`
carries a nullable `cost`; `CallKindTotals` sums it, and the run summary line prints `cost=<total>` when any call reported
one and `cost=-` otherwise (a free model's reported 0 is a 0).

## effective-context {#effective-context}

Every call to a model is sized from one window, `ModelWindow.of(ContextPolicy, override)` (`:api`, `llm/`): the
person's window for that model, else the smallest of the loaded or detected window, the profile's hard cap and
`ModelWindow.DEFAULT_WINDOW` (16,384). The loaded window counts only on an engine that fixes it at load or server
start (LM Studio, llama-server and the other OpenAI-compatible servers); the Ollama-native client sets the window on
each request, so Ollama's currently loaded figure does not bind it. The server's facts are read at Start (and on
the Models tab, Test and Detect again), never in the background. `:pipeline` plans every call of a model (the run,
preparation, the names pass, the brief, the file name, export, a review retry) for that window through `JobModelCalls`,
which caps each reply at half of it; only the Ollama-native client sends it, as `num_ctx` with `"truncate":false`, and an
OpenAI-compatible request never carries a context size. A run's calls also carry `keep_alive` `"30m"` on Ollama's
native API so a long pause does not unload the model.

## service-owned-retry {#service-owned-retry}

Retry lives in `:llm`, never in `:pipeline` or the UI:

- The **matched catalogue row's retry rule** decides (`#http-error-mapping`): `NONE`, `SAME` once, `NEW_SEED` once,
  `BACKOFF`, or `AUTO_WAIT` (the run waits instead). A failure no row explains is retried only when its code is
  retryable — `ErrorCode.timeout`, `rateLimited`, `unreachable`, `upstream` (5xx) and `discoveryFailed`; `auth`,
  `modelNotFound`, `contextWindow`, `validation`, and `emptyCompletion` are not retried.
- **Retry-After** is honored when present (header or body); otherwise exponential backoff with jitter.
- The **InferenceGate is released during backoff / `Retry-After` sleeps** and **re-acquired per attempt**, so a sleeping
  call never holds the single-flight permit (see #repair-and-gate).
- Each attempt gets a **fresh per-attempt timeout** (a slow first attempt does not consume the second attempt's budget).
- A bounded attempt count; a **single global cap** bounds the combined (transport-retry × repair) attempts. On
  exhaustion the last typed `AppError` is returned in the `Result`.

## inference-gate {#inference-gate}

```java
public final class InferenceGate {
    private final Semaphore permit = new Semaphore(1, true);   // single-flight
    <T> Result<T> run(Supplier<Result<T>> call);              // blocking acquire
}
```

A local model serves one request at a time (`01_SYSTEM_ARCHITECTURE.md#single-flight-inference`). `run` is the gate's
only entry point: it acquires before every provider call, blocking until the permit is free, and releases in
`finally`. The gate is released during backoff or `Retry-After` sleeps and re-acquired per attempt, so a retrying call
never holds the permit while it sleeps. No call is ever refused by the gate itself; the one `busy` outcome belongs to
the review-retry case, which is refused with `ErrorCode.busy` read from the run record — not from the gate — while
the project's own run is RUNNING, and its UI behavior is owned by the interactive review screen.

## http-error-mapping {#http-error-mapping}

Every transport and HTTP outcome maps to one typed `AppError` (`09_ERROR_HANDLING.md`) through **one error catalogue
read by meaning** (`llm/http/ErrorRows`, design D18 of `redesign-translation-pipeline`). The same words mean the same
thing on every engine, whatever the HTTP status or the word order, so the status alone never decides when the body
says more.

The order is fixed:

1. **Transport first** — an interrupted call is `cancelled`, a socket or read timeout `timeout`, a refused connection,
   an unknown host or another I/O failure `unreachable`.
2. **Rows by body text** — the rows of Table A are tried in order; the **first row whose words the body holds** (and
   whose statuses, when it names any, include the reply's) decides the code, the reason token and the retry rule. A
   streamed error line (Ollama's `{"error":…}` inside the stream, an SSE `event: error`) is read through the same rows
   that name no status; one no row explains is `upstream` with `unclassified`. A success whose body is only an error
   object keeps the row it matches; with no row it is `validation` with `wrong-path`, or `upstream` with
   `unclassified` for an SSE error event.
3. **Status alone** (Table B) — for a reply no row explains, with the reason `unclassified`.

The provider's own sentence reaches the log at TRACE only; the details carry the reason token and typed values
(`09_ERROR_HANDLING.md#safe-details-allowlist`, `#failure-reasons`). The 15 `ErrorCode` constants are unchanged.

**Table A — the rows, in the order they are tried.** "Any" status means the row matches by its words alone. Who is
asked is what the run does with the failure after the client's own retries (`pipeline/run/PauseDecider`).

| # | Row id | Status | Body words | ErrorCode | Reason | Retry | Who is asked |
|---|---|---|---|---|---|---|---|
| 1 | `ollama-host-forbidden` | 403 | empty body | `auth` | `host-forbidden` | NONE | the person |
| 2 | `ollama-cloud-signin` | any | `"signin_url"` | `auth` | `cloud-signin` | NONE | the person |
| 3 | `ollama-cloud-disabled` | any | `ollama cloud is disabled` | `auth` | `cloud-disabled` | NONE | the person |
| 4 | `ollama-cloud-unreachable` | 502 | `cloud`, `remote model`, `ollama.com` or `bad gateway` | `upstream` | `cloud-unreachable` | BACKOFF | nobody: the run waits as for an outage |
| 5 | `ollama-model-not-pulled` | any | `model … not found` in an `error` or `message` field | `modelNotFound` | `model-not-pulled` | NONE | the person |
| 6 | `ollama-thinking-unsupported` | 400 | `does not support think(ing)` | `validation` | `thinking-unsupported` | NONE | nobody when the refused control can be dropped (the request is sent again without it, not as an attempt); else the person |
| 7 | `ollama-think-level-invalid` | 400 | `invalid think value`, `think must be a boolean` | `validation` | `think-level-invalid` | NONE | as row 6 |
| 8 | `ollama-option-type` | any | `option "…" must be of type`, `missing unit in duration` | `validation` | `option-type` | NONE | the person; a BookLoom fault, logged at ERROR once per model |
| 9 | `ollama-server-busy` | any | `server busy` | `upstream` | `server-busy` | BACKOFF | nobody: the run waits as for an outage |
| 10 | `ollama-out-of-memory` | any | `model requires … GiB but only … GiB are available` | `modelUnavailable` | `out-of-memory` | NONE | the person (never woken by itself) |
| 11 | `ollama-repeat-limit` | any | `token repeat limit` | `upstream` | `repeat-limit` | NEW_SEED | nobody: after its one new-seed resend the segment is flagged |
| 12 | `ollama-runner-crashed` | any | `model runner has unexpectedly stopped` | `upstream` | `runner-crashed` | SAME | nobody: the run waits as for an outage |
| 13 | `lmstudio-terminated` | any | `terminated` as the `error` or `message` field | `upstream` | `terminated` | SAME | nobody: a second one waits as an outage |
| 14 | `lmstudio-no-model-loaded` | any | `no model(s) (are/is) loaded` | `modelUnavailable` | `no-model-loaded` | AUTO_WAIT | nobody for six wakes, then the person |
| 15 | `lmstudio-model-unloaded` | any | `model … unloaded`, `model … not loaded` | `modelUnavailable` | `model-unloaded` | AUTO_WAIT | as row 14 |
| 16 | `lmstudio-context-exceeded` | any | `context size has been exceeded` | `contextWindow` | `context-overflow` | NONE | nobody: the segment is flagged |
| 17 | `lmstudio-tokens-to-keep` | any | `tokens to keep … greater than the context length` | `contextWindow` | `context-overflow` | NONE | as row 16 |
| 18 | `lmstudio-grammar-too-large` | any | `failed to parse grammar` | `validation` | `grammar-too-large` | NONE | the person |
| 19 | `lmstudio-structured-unavailable` | 501 | `structured output is unavailable` | `validation` | `structured-unavailable` | NONE | the person |
| 20 | `lmstudio-template-rejected` | any | `Jinja Exception` | `validation` | `template-rejected` | NONE | the person |
| 21 | `lmstudio-engine-unreachable` | any | `Engine protocol predict request failed: fetch failed` | `upstream` | `engine-unreachable` | SAME | nobody: the run waits as for an outage |
| 22 | `lmstudio-schema-invalid` | any | `JSON schema error`, `Invalid structured output configuration`, `Pointer '…' does not exist`, `Unable to generate parser`, `cannot resolve $ref` | `validation` | `schema-invalid` | NONE | the person |
| 23 | `llama-exceed-context` | any | `exceed_context_size_error` (its typed `n_prompt_tokens` and `n_ctx` become the token counts) | `contextWindow` | `context-overflow` | NONE | nobody: the segment is flagged |
| 24 | `llama-model-loading` | 503 | `Loading model` | `modelUnavailable` | `model-loading` | AUTO_WAIT | nobody: the run waits until `/health` no longer says loading |
| 25 | `llama-schema-not-applied` | any | `unparsed peg-…`, `does not match the expected peg-native format` | `validation` | `schema-not-applied` | NONE | the person |
| 26 | `context-words` | any | `context` and one of `exceed`, `greater than`, `too long`, `longer than`, in any order | `contextWindow` | `context-overflow` | NONE | nobody: the segment is flagged |
| 27 | `context-code` | any | `context_length_exceeded` | `contextWindow` | `context-overflow` | NONE | as row 26 |
| 28 | `context-n-ctx` | any | `n_ctx` | `contextWindow` | `context-overflow` | NONE | as row 26 |

Engine-limited rows (group 5): each `ErrorRow` carries a set of engines (empty means every engine) and the mapper
passes the provider's effective engine. Two rows are limited to Unsloth and tried before the status rows: a `401`
(also with an empty body) with `authentication_error` is `auth` with reason `auth` and the sentence "Unsloth needs an
API key. Create one in Unsloth › Settings › API and put its environment-variable name here."; a `400` for a
`response_format` it cannot enforce is `validation` with reason `structured-unavailable`, handled as a refused setting
(the schema is dropped for that model by `RefusalMemory`; there is no separate rung). An error envelope whose `code` is
an integer (`{"error":{"code":400,…}}`, `{"object":"error","code":400,…}`) is read like the string form. A cap the
server ignores is not an error: an OpenAI-compatible reply with more completion tokens than the request's cap (for a
thinking call, the cap plus the 1,024-token reasoning headroom) and a normal finish writes one WARN with reason
`cap-ignored` per provider and model and the reply is used. The id and swapped-model guards add two `modelNotFound`
outcomes that come from BookLoom's own checks, not from a server body: `model-not-listed` (the typed id is not in the
server's own list) and `wrong-model-served` (the reply names another model).

A context-overflow failure carries the prompt and window token counts when the provider stated them (`(696 tokens)`
and `(512 tokens)` in its words, or the typed `n_prompt_tokens` and `n_ctx`), so the flag can say "696 of 512 tokens".
The rows that name an engine in their id match by text alone unless an engine set limits them, so the same words from
another engine read the same way.

**OpenRouter rows (design D21).** Four rows are limited to the OpenRouter engine (`llm/http/OpenRouterRows`), tried
before the generic rows, so another server's 402 or 403 keeps its own meaning: an `error_type` of `provider_unavailable`
(any status, also inside a 200) is `upstream` with reason `provider-unavailable`, retried with backoff and waited out
like any outage; a `no endpoints` body that names parameters is `validation` with `no-endpoint-for-parameters` (no
provider supports the structured answer or the reasoning switch; the person is asked); a `402` is `auth` with `credits`;
a `403` whose body says content policy, moderation or flagged is `validation` with `blocked` (the segment is flagged, the
run goes on). A `200` whose body holds an `error` object, or a choice that carries one, is parsed before `choices` and
goes through the same rows. A choice that finished with `finish_reason` `error` carries the failure of that body, not an empty answer. Statuses 408, 504 and 524 are `timeout` on OpenRouter only (`StatusMappings`; elsewhere a 504 is a proxy's
fault and stays `upstream`); 502, 503 and 529 and a 429 follow Table B.

**Table B — the status alone, for a reply no row explains.** Every failure here carries the reason `unclassified`.

| Condition | ErrorCode | Retried by the client |
|---|---|---|
| connect refused, DNS failure, no route, other I/O failure | `unreachable` | yes |
| socket or read timeout | `timeout` | yes, with a new seed and a lower cap (the timeout ladder) |
| the call was interrupted | `cancelled` | no |
| 401 or 403 | `auth` | no |
| 404 on a chat call | `modelNotFound` | no |
| 429 | `rateLimited` (Retry-After honoured) | yes |
| 5xx | `upstream` | yes |
| 400 or 422 | `validation` | no |
| any other status | `internal` | no |
| 200 whose reply cannot be read | `upstream` | yes |
| 200 with raw-empty or blank content (before sanitising) | `emptyCompletion` | no |
| a model listing that cannot be read | `discoveryFailed` | yes |
| a bound or chosen model not offered at run or preflight | `modelUnavailable` | no |
| a credential reference that resolves to nothing | `missingCredential` | no |

**Retry rules.** The matched row's `RetryRule` decides whether `GatedChatModel` sends a failed request again, not the
code's `isRetryable()`; a failure no row explains keeps the code's own rule, and `RetryPolicy` keeps the attempt
counts, the backoff and the timeout ladder:

- `NONE` — never sent again unchanged: the request must change or the person must act.
- `SAME` — the same request once more, after a state check that the server answers at all; a server that does not
  answer is handed to the run as an outage instead.
- `NEW_SEED` — the same request once more with another seed (a seed the model refused stays dropped).
- `BACKOFF` — the same request after growing pauses, within the attempt budget, honouring `Retry-After`.
- `AUTO_WAIT` — not sent again by the client; the run waits for the server by itself (an unloaded or loading model).

A request repeated without a setting the model refused is not an attempt. Every WARN line about a retried, given-up or
refused call, or a flagged step, carries `reason=<token>` built from the typed reason (`09_ERROR_HANDLING.md#failure-reasons`),
for example `gave up provider chat code=validation reason=grammar-too-large kind=TERM_READING`.

`discoveryFailed` and `modelUnavailable` are defined in the `ErrorCode` enum (`09_ERROR_HANDLING.md`).

## credentials-as-reference {#credentials-as-reference}

A credential is held as an environment-variable name. `RequestHeaders` resolves it, and every `${env:NAME}` in an extra header, immediately before each request, in `HttpExchange` for plain and streamed calls alike, through the lookup function `LlmModule` is given: `:app`'s `SystemEnvironmentVariables` (process environment, then the OS store of the user's variables — `launchctl getenv`, the registry user and machine environment, `environment.d` and `/etc/environment` — read at call time with a 2-second cache), the same instance that answers the Providers form's "set / not set". `:llm` holds no operating-system code; installed alone it reads the process environment. A variable that is unset or blank refuses the call as `missingCredential` naming the variable, before any request is sent; a resolved value is never logged, never placed in an `AppError`, and shown only masked in a verification exchange. Keychain support is not built.

## three-stage-verification {#three-stage-verification}

The built `ProviderVerifier` verifies a selected provider/model pair in three ordered stages, returning a
`VerificationReport` with each outcome, and an optional fourth stage the person asks for.

1. **Connection** — the dedicated `probe()` reaches the configured base URL (`/api/version` for Ollama and `/models`
   for OpenAI-compatible providers). It proves reachability independently of listing, so Connection and Models are
   genuinely separable stages. Every stage sends the description's sign-in headers. The stage also runs engine
   detection (#server-engines): it fails an Ollama-native provider whose address a recognised non-Ollama engine
   answers, and refuses GPT4All and AnythingLLM with `validation` before any chat request. An unsaved description is verified through `ProviderVerifier.verify(ProviderConfig, model, policy)` on an uncached client and listed through `ModelCatalog.listModels(ProviderConfig)`; neither registers anything.
2. **Models** — a non-empty list must contain the selected model; absence is `modelUnavailable`. Where any id is served
   (LM Studio, Unsloth, a llama-server router) the id is checked against the server's own list through
   `engine/ModelIdCheck` and an unlisted one fails with `modelNotFound` (reason `model-not-listed`) naming the id. A
   discovery failure or empty list is a soft pass, allowing inference to establish availability for a manually entered model.
3. **Inference** — a minimal `chat` round-trip against the chosen model returns non-empty content; proves the model
   actually generates. For a manually entered model this round trip is the availability check; `modelNotFound` maps to
   `modelUnavailable`. `FULL` performs this stage; `PREFLIGHT` performs it only after a Models soft pass. When the
   plain-probe fallback runs, the schema verdict (refused or ignored, source `test`) is stored on the model.
4. **Advanced** (`VerificationStage.ADVANCED`, policy `ADVANCED` = `FULL` then this stage; never part of another
   policy) — `llm/verify/AdvancedChecks` runs up to four short checks through the same gate and the request mapper, each
   with invented text and at most 64 output tokens: C1 answer schema (honoured only when the reply is exactly the one
   allowed value; free text is ignored, a 400 is refused; never retried as `json_object`), C2 thinking off (the
   engine's switch, then `reasoning_effort:"none"`, then `chat_template_kwargs.enable_thinking:false`, then
   `reasoning:{enabled:false}`; honoured when the reasoning field is empty and `reasoning_tokens` is 0; the first
   switch that works is stored), C3 extra samplers (two calls at temperature 1.5 with `top_k:1`, honoured when the
   replies match), C4 the cap field (a count to 100 with `max_completion_tokens:16`; the field that limits
   `completion_tokens` to about 16 with finish `length`, also stored as the model's output field). A recognised engine
   runs C1 and C2; a generic server runs all four; the Ollama-native kind runs C1 through `format` and C2 as one
   `think:false` call. The checks use an uncached client built with the engine this Test just detected, and send what
   they ask for even on a generic server (a check is how a verdict is earned). Verdicts are saved through
   `ModelSettingsRepository.update` with source `test`; those of an unsaved draft are keyed by its ids and apply once a
   provider with that id is saved. In the window the stage is registered as a provider inference test, so it is refused
   while a run translates and runs while the run is paused.

Stages run in order and short-circuit on a hard failure, surfacing the typed `AppError` for that stage. Verification
uses the shared blocking gate and retry policy; engine detection takes no gate permit (a GET is not inference).

## per-project-binding {#per-project-binding}

Provider and model selection is **bound per project**, not global. The `current_provider` setting is the **new-project
default only**: on project creation the current defaults are copied into the project and recorded as its **last-used**
provider/model snapshot (`last_used_json`). `current_provider` is **never consulted for an existing project's run**.
From then on the project **persists and reuses its own bound provider and models** on every resume, so a paused or
closed book continues on the same provider and models and does not regress mid-run when settings later change. The
binding is stored with the project (`06_DATA_MODEL_SQLITE.md#projects`, `06_DATA_MODEL_SQLITE.md#resume-support`).

**Before any inference** — real runs and diagnostics alike — `:llm` **preflight-verifies** the connection and that the
project's bound model (s) are actually available, failing fast with a typed error if unreachable or missing (this reuses
#three-stage-verification). "Available" means: with discovery, membership in `listModels()`; without discovery (a manual
model), the inference round-trip **is** the availability check (`modelUnavailable` on failure). **Scope:** the
**translator** model is the only model and is always verified (there is no judge or helper slot). It never silently substitutes:

- If the bound provider or model is **unavailable**, the app prompts the user and falls back to the currently-configured
  default **only on explicit confirmation**, recording the change.
- On resume, `last_used_json` is compared against the **live provider row**; on **any drift** (not only a
  settings-default difference) the app raises the ADR-0012 "apply new selection vs continue with the previous one"
  prompt, defaulting to continue with the previous binding.

The prompts and their states are specified in `01_Product/08_UI_SCREENS_AND_STATES.md`.
