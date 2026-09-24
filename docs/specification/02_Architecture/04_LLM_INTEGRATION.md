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
    String id, ProviderKind kind, URI baseUrl, Duration connectTimeout, Duration requestTimeout
) {}
```

The per-dialect `ProviderClient` is an internal `:llm` interface, not a public port. It supplies `probe()`,
`listModels()`, `chat(modelId, request)` and `kind()` to the factory and verifier. `probe()` is a lightweight
reachability check so the Connection and Models stages remain separable: Ollama uses `/api/version` and the
OpenAI-compatible dialect uses `/models`. Calls are synchronous and return the whole `ChatResponse`; streaming remains
deferred.

### client-implementations {#client-implementations}

Two concrete clients implement the internal interface, both returning the same `ChatResponse` / `ModelInfo` types so
the pipeline stays dialect-agnostic:

- **Ollama-native client** (`kind = OLLAMA`) — uses `/api/version` to probe, `/api/tags` to discover models and
  `/api/chat` for inference. It sends `stream:false`, plus `options.temperature` and `format` only when the request
  provides them. It deliberately sends no `think`, `num_ctx`, `keep_alive`, or `/api/show` request in this change.
- **OpenAI-compatible client** (`kind = OPENAI_COMPATIBLE`) — uses `/models` to probe and discover and
  `/chat/completions` for inference relative to the configured base URL. It covers LM Studio and other
  OpenAI-compatible servers, sending `stream:false`, optional `temperature`, and strict `json_schema`
  `response_format` only when requested; it never sends a reasoning parameter.

### provider-factory {#provider-factory}

`ProviderClientFactory` is internal to `:llm`: it switches exhaustively on `ProviderConfig.kind()` and caches one
client per provider-config id. Only `OLLAMA` and `OPENAI_COMPATIBLE` ship; another dialect requires a new enum value and
internal client without changing callers of the three public ports.

### provider-profile {#provider-profile}

This change has no `ProviderProfile` or capability cache. Provider configuration is deliberately minimal while
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

- Only **typed retryable errors** trigger a retry — `ErrorCode.timeout`, `rateLimited`, `unreachable`, `upstream` (5xx)
  and `discoveryFailed`. `auth`, `modelNotFound`, `contextWindow`, `validation`, and `emptyCompletion` are not retried.
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

A local model serves one request at a time (`01_SYSTEM_ARCHITECTURE.md#single-flight-inference`). `run` acquires before
every provider call and releases in `finally`. The gate is released during backoff or `Retry-After` sleeps and
re-acquired per attempt, so a retrying call never holds the permit while it sleeps. Interactive `tryRun` and the
`ErrorCode.busy` UI behavior are deferred until an interactive screen owns them.

## http-error-mapping {#http-error-mapping}

Every transport/HTTP outcome maps to one typed `AppError` (`09_ERROR_HANDLING.md`):

| Condition                                              | ErrorCode                         | retryable |
|--------------------------------------------------------|-----------------------------------|-----------|
| connect refused / DNS / no route                       | `unreachable`                     | yes       |
| socket/read timeout                                    | `timeout`                         | yes       |
| 401 / 403                                              | `auth`                            | no        |
| 404 model / unknown model in body                      | `modelNotFound`                   | no        |
| 429                                                    | `rateLimited` (honor Retry-After) | yes       |
| 5xx                                                    | `upstream`                        | yes       |
| 400 context length exceeded                            | `contextWindow`                   | no        |
| 200 but raw-empty/blank content (pre-sanitize)         | `emptyCompletion`                 | no        |
| model listing/discovery failed                         | `discoveryFailed`                 | yes       |
| bound/selected model not available at run or preflight | `modelUnavailable`                | no        |
| missing credential at resolve or draft pre-check time  | `missingCredential`               | no        |
| unparseable body / other                               | `internal`                        | no        |

`discoveryFailed` and `modelUnavailable` are defined in the `ErrorCode` enum (`09_ERROR_HANDLING.md`). A
structured-output rejection is currently surfaced through the normal HTTP mapping; the downgrade path is deferred.

## credentials-as-reference {#credentials-as-reference}

Authentication is deferred. The future Bearer path will hold an environment-variable name as a credential reference,
resolve it immediately before a request and never persist or log the secret; keychain support is not part of that path.

## three-stage-verification {#three-stage-verification}

The built `ProviderVerifier` verifies a selected provider/model pair in three ordered stages, returning a
`VerificationReport` with each outcome. The add/edit-provider UI is future work.

1. **Connection** — the dedicated `probe()` reaches the configured base URL (`/api/version` for Ollama and `/models`
   for OpenAI-compatible providers). It proves reachability independently of listing, so Connection and Models are
   genuinely separable stages. Authentication is not yet part of this client configuration.
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
