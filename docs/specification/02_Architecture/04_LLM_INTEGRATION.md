**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-09-22
**Cross-references:** `docs/specification/02_Architecture/09_ERROR_HANDLING.md`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`

# LLM Integration

`:llm` owns everything about talking to a model: the provider abstraction, model discovery, inference, response
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
inference checks. Callers outside `:llm` use these ports and never branch on a wire dialect.

```java
public interface ProviderConfigs {
    Result<ProviderConfig> register(ProviderConfig config);
    Optional<ProviderConfig> find(String id);
    List<ProviderConfig> all();
}
public interface ProviderVerifier {
    Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy);
}
public record ProviderConfig(
    String id, ProviderKind kind, URI baseUrl, Duration connectTimeout, Duration requestTimeout,
    String displayName, ProviderAuth auth, String apiKeyHeader, String credentialRef,
    String modelsPath, String chatPath, Map<String, String> headers, List<String> customModels
) {}
```

(shown without nullability; the five-argument form builds a provider with no sign-in.) `HttpExchange` builds each request's headers from it through `RequestHeaders`: the JSON headers, the extra headers with `${env:NAME}` resolved, then the sign-in header last. Both clients choose their model-list and chat path through `EndpointPaths`.

The per-dialect `ProviderClient` is an internal `:llm` interface, not a public port. It supplies `probe()`,
`listModels()`, `chat(modelId, request)` and `kind()` to the factory and verifier. `probe()` is a lightweight
reachability check so the Connection and Models stages remain separable: Ollama uses `/api/version` and the
OpenAI-compatible dialect uses `/models`. Calls are synchronous and return the whole `ChatResponse`; the Ollama-native
client reads its reply as a stream internally, so a reply that stops sending is ended by an idle gap of 60 seconds,
while caller-visible streaming remains deferred.

### client-implementations {#client-implementations}

Two concrete clients implement the internal interface, both returning the same `ChatResponse` / `ModelInfo` types so
the pipeline stays dialect-agnostic:

- **Ollama-native client** (`kind = OLLAMA`) — uses `/api/version` to probe, `/api/tags` to discover models and
  `/api/chat` for inference. It sends `stream:true` and reads the NDJSON lines into one reply (idle gap 60 s, the
  call's per-kind timeout as the ceiling), plus `options.temperature` and `format` only when the request
  provides them. It sends `think` (`false`, `"low"` or `true`) only when the request carries a reasoning level, and deliberately sends no `num_ctx`, `keep_alive`, or `/api/show` request in this change.
- **OpenAI-compatible client** (`kind = OPENAI_COMPATIBLE`) — uses `/models` to probe and discover and
  `/chat/completions` for inference relative to the configured base URL. It covers LM Studio and other
  OpenAI-compatible servers, sending `stream:false`, optional `temperature`, and strict `json_schema`
  `response_format` only when requested, and `reasoning_effort` (`none`, `low` or `medium`) when the request carries a reasoning level (Ollama's
  and LM Studio's `/v1` both honour it). A capped reply whose content is blank, whose `reasoning`/`reasoning_content`
  is not, and whose finish is `length` is asked for once more with four times the cap, bounded by the context size.

### provider-factory {#provider-factory}

`ProviderClientFactory` is internal to `:llm`: it switches exhaustively on `ProviderConfig.kind()` and caches one
client per provider-config id. Only `OLLAMA` and `OPENAI_COMPATIBLE` ship; another dialect requires a new enum value and
internal client without changing callers of the three public ports.

### provider-profile {#provider-profile}

There is no per-kind profile type or capability cache; `ProviderConfig` carries only endpoint and timeouts. Provider configuration is deliberately minimal while
persistence, credentials, reasoning control, structured-output capability detection and context-window sizing remain
future work.

### provider-config {#provider-config}

`ProviderConfig` is the in-memory provider description that the factory turns into a client:

```java
public record ProviderConfig(
    String id, ProviderKind kind, URI baseUrl, Duration connectTimeout, Duration requestTimeout
) {}
```

## chat-contracts {#chat-contracts}

The built `ChatRequest`/`ChatResponse` (`ua.bookloom.api.llm`) are:

```java
public record ChatRequest(
    List<ChatMessage> messages,
    @Nullable Double temperature,
    @Nullable ResponseFormat responseFormat
) {
    public ChatRequest(List<ChatMessage> messages) { this(messages, null, null); }
}
public record ChatResponse(String content, FinishReason finishReason) {}   // FinishReason: STOP, LENGTH, OTHER
```

`ChatRequest` carries no model field: the model is bound on the `ChatModel` obtained from `ModelSelection`. Its
nullable `temperature` and `responseFormat` components are both built and omitted from serialized JSON when unset.
`topP`, `maxTokens`, `numCtx`, token usage and streaming are not part of the current contract.

## response-handling {#response-handling}

The current pipeline sends the minimal `{"target":"…"}` schema on every draft call and accepts only that exact
parsed object. Both clients reject raw blank content before sanitizing, ignore separate reasoning fields, strip
reasoning tags and an outer code fence, and return the cleaned content to the pipeline. A provider capability downgrade
may omit the native schema field, but the prompt still requires JSON only. Parsing and the two bounded repairs belong
to `:pipeline`, not to a client.

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

`listModels()` reads `/api/tags` for Ollama and `/models` for OpenAI-compatible providers. Discovery is a convenience:
the verifier records a discovery failure or empty list as a soft pass, then verifies a manually entered model through
an inference round trip. Provider editing, persisted model slots and capability profiles belong to later work.

## effective-context {#effective-context}

Effective-context discovery and chunk budgeting are deferred. This client intentionally does not send `num_ctx`, call
`/api/show`, or send `keep_alive`; a later context-window change owns those controls and their precedence.

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

A context-overflow failure carries the prompt and window token counts when the provider stated them (`(696 tokens)`
and `(512 tokens)` in its words, or the typed `n_prompt_tokens` and `n_ctx`), so the flag can say "696 of 512 tokens".
A row that names Ollama matches by text alone until group 5 adds the engine mask, so the same words from another
engine read the same way.

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
`VerificationReport` with each outcome. The add/edit-provider UI is future work.

1. **Connection** — the dedicated `probe()` reaches the configured base URL (`/api/version` for Ollama and `/models`
   for OpenAI-compatible providers). It proves reachability independently of listing, so Connection and Models are
   genuinely separable stages. Every stage sends the description's sign-in headers. An unsaved description is verified through `ProviderVerifier.verify(ProviderConfig, model, policy)` on an uncached client and listed through `ModelCatalog.listModels(ProviderConfig)`; neither registers anything.
2. **Models** — a non-empty list must contain the selected model; absence is `modelUnavailable`. A discovery failure
   or empty list is a soft pass, allowing inference to establish availability for a manually entered model.
3. **Inference** — a minimal `chat` round-trip against the chosen model returns non-empty content; proves the model
   actually generates. For a manually entered model this round trip is the availability check; `modelNotFound` maps to
   `modelUnavailable`. `FULL` performs this stage; `PREFLIGHT` performs it only after a Models soft pass.

Stages run in order and short-circuit on a hard failure, surfacing the typed `AppError` for that stage. Verification
uses the shared blocking gate and retry policy; interactive bounded-wait diagnostics belong to the future provider UI.

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
**translator** model is always verified; the **judge/helper** model is verified **only when the quality dial will invoke
the judge** — an unused judge slot is not preflighted. It never silently substitutes:

- If the bound provider or model is **unavailable**, the app prompts the user and falls back to the currently-configured
  default **only on explicit confirmation**, recording the change.
- On resume, `last_used_json` is compared against the **live provider row**; on **any drift** (not only a
  settings-default difference) the app raises the ADR-0012 "apply new selection vs continue with the previous one"
  prompt, defaulting to continue with the previous binding.

The prompts and their states are specified in `01_Product/08_UI_SCREENS_AND_STATES.md`.
