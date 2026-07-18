**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/02_Architecture/09_ERROR_HANDLING.md`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`, `docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`

# LLM Integration

`:llm` owns everything about talking to a model: the provider abstraction, model discovery, inference, response
sanitization and tolerant parsing, the single-flight gate, retry, HTTP→typed-error mapping, credential resolution, and
provider verification. It is FX-free and reaches the network only through `java.net.http.HttpClient`. Signatures below
are Java-ish contracts in `ua.bookloom.api.llm` (ports) and `ua.bookloom.llm` (impl).

## provider-architecture {#provider-architecture}

A `Provider` **port** plus a `ProviderFactory` hide the concrete client from every caller (`:pipeline`, verification,
the UI). The factory maps a provider **kind** to the client that speaks that server's dialect. Callers depend only on
the port and never branch on kind, so adding a provider is adding an implementation behind the factory, not a change at
the call site.

```java
public interface Provider {
    Result<Void>              probe();                              // a.k.a. verifyConnection()
    Result<ChatResponse>      chat(ChatRequest req, RequestOptions opts);   // synchronous, whole-body
    Result<List<ModelInfo>>   listModels();
    Capabilities              capabilities();
    ProviderKind              kind();
}

public enum ProviderKind { OLLAMA, OPENAI_COMPATIBLE }   // extensible
```

`probe()` (equivalently `verifyConnection()`) is a **dedicated lightweight reachability check** so the Connection and
Models verification stages are **separable** — the Ollama-native client hits the root / `/api/version`, the
OpenAI-compatible client does a `/v1/models` HEAD or cheap GET. It proves reachability and the auth scheme without
listing models or running inference, so a server with no discovery endpoint can still pass Connection independently of
Models. `chat` is **synchronous** and returns the whole `ChatResponse` body (see #chat-contracts; streaming is deferred,
DD-33).

### client-implementations {#client-implementations}

Two concrete clients implement the port today, both returning the same `ChatResponse` / `ModelInfo` types so the
pipeline stays dialect-agnostic:

- **Ollama-native client** (`kind = OLLAMA`) — talks to Ollama's own API: `/api/chat` for inference, `/api/tags` for
  discovery, `/api/show` for model metadata, and the native `options` block (`num_ctx`, `keep_alive`, `format`,
  `think`). **Why native rather than Ollama's OpenAI-compatible shim:** the `/v1/*` surface does not fully honor the
  controls this app depends on — most importantly `num_ctx` (context-window sizing), and also `keep_alive`, `format`,
  and `think` — silently dropping or misapplying them. The native API is the only reliable way to size the context
  window and set reasoning/format controls for a local Ollama model, so Ollama is spoken to natively.
- **OpenAI-compatible client** (`kind = OPENAI_COMPATIBLE`) — talks to the OpenAI REST shape: `/v1/chat/completions`,
  `/v1/models`, and `response_format` for structured output. This one client covers **LM Studio and any other
  OpenAI-compatible server** (llama.cpp, vLLM, a remote gateway, …); those differ only in base URL and auth, which are
  profile/config data, not code.

### provider-factory {#provider-factory}

```java
public interface ProviderFactory {
    Provider create(ProviderConfig config);   // config.kind() -> concrete client
    ProviderProfile profileFor(ProviderKind kind);
}
```

`create` reads `config.kind()` and returns the matching client wired with the resolved `ProviderProfile`. The
`ProviderKind` enum is **extensible by design**: a future provider (for example a hosted Gemini or Claude endpoint)
would be a new enum constant plus a new `Provider` implementation registered in the factory, with **no change to any
caller**. Those providers are **not implemented and are out of scope for this version** — the seam is open, but the spec
neither requires nor assumes them; only `OLLAMA` and `OPENAI_COMPATIBLE` ship.

### provider-profile {#provider-profile}

`ProviderProfile` is immutable per-kind configuration data that parameterizes a client — auth defaults, discovery
capability, and quirks. Endpoint paths belong to each concrete client (Ollama-native uses `/api/*`, OpenAI-compatible
uses `/v1/*`), so the profile no longer templates them:

```java
public record ProviderProfile(
    ProviderKind kind,
    AuthScheme   defaultAuthScheme,     // NONE | BEARER | API_KEY_HEADER
    String       defaultBaseUrl,        // e.g. http://localhost:11434
    DiscoveryStrategy discoveryStrategy,// OLLAMA_TAGS | OPENAI_MODELS | NONE
    boolean      supportsModelDiscovery,// false -> manual model-ID entry only
    Capabilities capabilities           // supportsNumCtx, supportsReasoningControl, supportsStructuredOutput, effectiveContext…
) {}
```

`supportsModelDiscovery` records whether the server exposes a model-listing endpoint at all; see #model-discovery.
`Capabilities` no longer carries `supportsStreaming` — **streaming is deferred (out of scope for v1)**.
`supportsStructuredOutput` is **populated during the Models/Inference verification stage** (whether a structured-output
request is honoured or silently downgraded) and cached; it is a diagnostic hint, never a gate, because structured output
is always attempted (#response-handling). `effectiveContext` holds the resolved per-provider context window
(#effective-context).

### provider-config {#provider-config}

`ProviderConfig` is the saved, user-editable instance of a provider that the factory turns into a client:

```java
public record ProviderConfig(
    String id, String displayName,
    ProviderKind kind,
    String baseUrl,                 // overrides profile default
    AuthScheme authScheme,
    CredentialRef credentialRef,    // reference, never the secret (see below)
    Duration connectTimeout,
    Duration requestTimeout,
    Integer numCtx                  // nullable; context-window hint (Ollama options.num_ctx)
) {}
```

## chat-contracts {#chat-contracts}

```java
public record ChatRequest(
    String model,
    List<Message> messages,         // system + user turns
    Double temperature,             // nullable -> omitted from JSON
    Double topP,                    // nullable -> omitted
    Integer maxTokens,              // nullable -> omitted
    Integer numCtx,                 // nullable -> Ollama options.num_ctx only
    ResponseFormat responseFormat   // structured-output shape for the translation object; nullable
) {}

public record ChatResponse(
    String content,                 // assistant text (the JSON array of translations)
    String model,
    Usage usage,                    // prompt/completion tokens if reported; nullable
    FinishReason finishReason
) {}
```

**Nullable params are omitted** from the serialized JSON (Jackson `@JsonInclude(NON_NULL)`), so a `null` temperature is
absent rather than sent as `null` — avoiding rejections from strict endpoints. There is **no `stream` field** —
streaming is deferred (out of scope for v1), so `chat` always returns the whole `ChatResponse` body synchronously
(`Result<ChatResponse>`). **Cancellation** is bounded by the per-request HTTP timeout (a hard upper bound) plus a
cooperative interrupt at the next boundary; worst-case cancel latency is the request read-timeout, not instant
(`08_THREADING_CONCURRENCY.md#cancellation`). When several `numCtx` inputs collide, precedence is **request-level (
`ChatRequest.numCtx`) > provider-level (`ProviderConfig.numCtx`) > setting default**, and the packer budgets the single
resolved value (#effective-context).

## response-handling {#response-handling}

Model output is treated as **JSON-first but tolerant** — the pipeline asks for a defined shape, then defends against
models that wrap, narrate, or malform it. Every model-facing call runs the same sequence:

1. **Always request structured output** — the OpenAI-compatible client sets `response_format` (a JSON schema or
   `json_object`); the Ollama-native client sets `format` (`json` or a JSON schema). The request is attempted regardless
   of prior belief about support. A **structured-output rejection** (HTTP 400 / "unsupported") is a **silent
   downgrade**: the call is re-issued as a plain request (no `response_format`/`format`) and continues down the
   sanitize + text-fallback path — never a hard failure. The observed outcome updates the provider's
   `supportsStructuredOutput` capability.
2. **Best-effort reasoning control** — set reasoning low/off **where controllable and omit the parameter where
   unsupported**: the Ollama-native client sets `think` off/low (informed by `/api/show` where available), the
   OpenAI-compatible client sets its reasoning-effort parameter low. Whether or not the parameter takes, output **always
   falls through to the `<think>`-strip path** (step 3), so an always-on / non-disableable reasoning model is still
   handled.
3. **Sanitize before parsing** — strip `<think>…</think>` reasoning blocks and analogues, chain-of-thought preambles,
   markdown code-fence wrappers, and leading/trailing prose; ignore any separate reasoning channel; then locate the JSON
   object within the cleaned text.
4. **Parse tolerantly** — the `ObjectMapper` ignores unknown/unexpected fields (`FAIL_ON_UNKNOWN_PROPERTIES=false`),
   defaults missing optional fields, and trims whitespace, so a superset or reordered object still parses.
5. **One repair retry** — on malformed JSON, re-ask exactly once ("return only valid JSON matching this shape"). This is
   a single repair attempt, not a general retry loop. Its interaction with the gate and transport retry is specified in
   #repair-and-gate.
6. **Deterministic plain-text fallback** — if the output is still unparseable, treat the sanitized text as the plain
   translation. The chunk is **flagged only if it then fails the QA gates** (`05_PIPELINE_ENGINE.md`), so a model that
   simply refuses to emit JSON still yields usable output rather than a hard failure.

### empty-response ordering {#empty-response-ordering}

Empty responses are ordered distinctly from malformed ones:

- A **raw-empty** response — blank/whitespace-only *before* sanitize — maps straight to `ErrorCode.emptyCompletion` (the
  model produced nothing).
- A **non-empty response that sanitizes to empty** — content existed but was entirely reasoning/fences/prose — takes
  **one repair retry specific to empty-after-sanitize** before failing; if it is still empty after that retry, it yields
  an **empty text-fallback result** that the QA gate then flags. It is *not* reported as `emptyCompletion`, because the
  model did emit content.

### repair-and-gate {#repair-and-gate}

The repair retry (step 5) is a **fresh call, not a nested continuation**:

- It **re-acquires the InferenceGate** and gets its **own fresh per-attempt timeout**.
- It is subject to **transport retry independently** (the retryable-error path of #service-owned-retry applies to the
  repair call on its own).
- It **does not recurse** — a repair call never triggers a second repair.
- A **single global cap** bounds the total attempt count, so the product of (transport-retry × repair) can never run
  away.
- During any **backoff / `Retry-After` sleep the gate is released** and **re-acquired per attempt**, so a sleeping call
  never holds the single-flight permit and starves other work.

## client-construction {#client-construction}

Both clients share one construction contract:

- **One injected `java.net.http.HttpClient`** (Guice-provided, connection-pooled) is shared across requests; a **fresh
  timeout is set per request** via `HttpRequest.timeout(...)`, so a slow call cannot consume another call's budget.
- **Request/response DTOs are records** in an internal, non-exported `dto` package, annotated `@JsonInclude(NON_NULL)`
  so nullable/unset parameters are omitted from the body rather than serialized as `null`.
- **A single tolerant `ObjectMapper`** (unknown fields ignored, missing defaulted, whitespace trimmed) is reused for
  both dialects.
- **Never log secrets or full book text** — logs carry model id, endpoint, sizes, and typed error codes only;
  prompt/response bodies and resolved credentials never reach a log line or an `AppError`.

## model-discovery {#model-discovery}

`listModels()` follows the profile's `discoveryStrategy` — the Ollama-native client reads `/api/tags`, the
OpenAI-compatible client reads `/v1/models`. Discovery is a **convenience, not a requirement**:
`ProviderProfile.supportsModelDiscovery` marks whether the server offers a listing endpoint at all, and **manual
model-ID entry is a first-class, always-available override**. When `supportsModelDiscovery` is false, or discovery
fails, returns empty, or is unauthorized, the UI presents a free-text model-ID field (pre-filled with any previously
remembered model) so the user types the model IDs directly; an offline or permission-limited endpoint never blocks
configuration. Two model slots persist per provider — **translator** (required) and **judge/helper** (used when the
judge/reflection runs) — per `06_DATA_MODEL_SQLITE.md`. There is no embedding slot: cross-chapter consistency comes from
the name/term dictionary, a string-similarity translation memory, and a rolling bilingual summary
(`05_PIPELINE_ENGINE.md`), not from vectors.

## effective-context {#effective-context}

Each provider exposes a resolved **effective context (tokens)** value (`Capabilities.effectiveContext`) that the chunk
packer budgets against (DD-44). Resolution is deterministic, first hit wins:

1. **Ollama `num_ctx` / `/api/show`** — the Ollama-native client reads the model's reported context from `/api/show` (or
   an explicit `num_ctx`).
2. **Discovery** — a context length reported by model-listing/metadata, where a server exposes one.
3. **Manual "effective context (tokens)" field** — a user-editable field on the provider profile, persisted to the
   `providers` row.
4. **Conservative built-in default** — a safe floor (aligned with the settings default `32768`) when unknown.

The packer computes `effectiveBudget = min(effectiveContext − reservedHeadroom, chunkBudgetSetting)` (reserved
headroom = system prompt + injected context + target output). Colliding `numCtx` inputs resolve by **request >
provider > setting** precedence (#chat-contracts); the packer always budgets the single resolved value. Token counting
is a deterministic heuristic, not a shipped tokenizer (`01_Product/05_TRANSLATION_ALGORITHM.md#chunking`).

## service-owned-retry {#service-owned-retry}

Retry lives in `:llm`, never in `:pipeline` or the UI:

- Only **typed retryable errors** trigger a retry — `ErrorCode.timeout`, `rateLimited`, `unreachable`, `upstream` (5xx).
  `auth`, `modelNotFound`, `contextWindow`, `validation`, `emptyCompletion` are **not** retried.
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
    <T> Result<T> tryRun(Supplier<Result<T>> call, Duration wait); // tryAcquire -> ErrorCode.busy on failure
}
```

A local model serves one request at a time (`01_SYSTEM_ARCHITECTURE.md#single-flight-inference`). `run` acquires before
every `chat` call and releases in `finally`. `tryRun` uses `tryAcquire` so an interactive action (e.g. a user "retry
now") can fail fast with `ErrorCode.busy` rather than queue behind a long batch. **Provider-dialog verification (
diagnostics) uses `tryRun` with a bounded wait**, and surfaces a held gate as `busy` — a "model in use, try again"
toast — rather than blocking. The gate wraps inference only; parsing/QA/persistence run concurrently around it. The gate
is **released during backoff / `Retry-After` sleeps and re-acquired per attempt** (#service-owned-retry,
#repair-and-gate), so a retrying or repairing call never holds the permit while it sleeps.

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
| model listing/discovery failed                         | `discoveryFailed`                 | no        |
| bound/selected model not available at run or preflight | `modelUnavailable`                | no        |
| missing credential at resolve or draft pre-check time  | `missingCredential`               | no        |
| unparseable body / other                               | `internal`                        | no        |

`discoveryFailed` and `modelUnavailable` are defined in the `ErrorCode` enum (`09_ERROR_HANDLING.md`). A
structured-output rejection (400/unsupported) is **not** in this table: it is a silent downgrade handled inside
#response-handling, not a surfaced error. A non-empty response that sanitizes to empty is likewise not
`emptyCompletion` — see #empty-response-ordering.

## credentials-as-reference {#credentials-as-reference}

`ProviderConfig` stores a `CredentialRef`, never a secret. A reference is either an **environment-variable name** or an
**OS-keychain entry id**. At request time `:llm` resolves the reference to the live secret, uses it for that one call,
and never persists, logs, or echoes it. A reference that resolves to nothing yields `ErrorCode.missingCredential`.
During draft verification a **credential-resolution pre-check** runs before the Connection probe, so a draft whose
credential is not yet resolvable **fails the Connection stage as `missingCredential`** without a network round-trip.
Local providers (Ollama/LM Studio) default to `AuthScheme.NONE` and need no reference. Storage detail:
`06_DATA_MODEL_SQLITE.md#providers`.

## three-stage-verification {#three-stage-verification}

The add/edit-provider dialog verifies a **draft** config (not the saved one) in three independent stages, each returning
its own `Result` so the UI can show per-stage pass/fail:

1. **Connection** — the dedicated `probe()`/`verifyConnection()` reaches `baseUrl` (Ollama root/`/api/version`;
   OpenAI-compatible `/v1/models` HEAD or cheap GET); proves reachability + auth scheme. A **credential-resolution
   pre-check** runs first: a draft `CredentialRef` that does not yet resolve fails this stage as `missingCredential`
   (never a network round-trip against a missing secret). Because the probe is independent of listing, Connection and
   Models are genuinely separable stages.
2. **Models** — `listModels()` succeeds (or degrades to the free-text fallback, reported as a soft pass); a hard
   discovery failure is typed `discoveryFailed`.
3. **Inference** — a minimal `chat` round-trip against the chosen model returns non-empty content; proves the model
   actually generates. For a **manually-entered model** this round-trip **is** the availability check (a failure is
   `modelUnavailable`). This stage also **populates `supportsStructuredOutput`** (whether the structured-output request
   was honoured or silently downgraded).

Stages run in order and short-circuit on a hard failure, surfacing the typed `AppError` for that stage. Diagnostics
acquire the gate via `tryRun` (bounded wait) and report `busy` as a "model in use, try again" toast. The trio maps to
the mockup "Test connection / models / inference" buttons in the add/edit-provider dialog
(`docs/specification/mockups/ui-mockup.html`).

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
