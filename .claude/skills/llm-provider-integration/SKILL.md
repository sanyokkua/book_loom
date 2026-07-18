---
name: llm-provider-integration
description: >-
  Use when adding or using an LLM provider in the `:llm` module — the `Provider` port +
  `ProviderFactory` hiding two concrete clients (Ollama-native and OpenAI-compatible), the
  JSON-first tolerant response pipeline (structured output, sanitize, repair, text
  fallback), per-project provider/model binding with preflight verification, model
  discovery with first-class manual model-ID entry, the three-stage verification
  (connection/models/inference), credentials-as-reference, the single-flight
  `InferenceGate`, service-owned retry on typed retryable errors, nullable `ChatRequest`
  params, and HTTP -> typed `AppError` mapping.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# LLM Provider Integration

`:llm` owns everything about talking to a model: the provider abstraction, discovery,
inference, the single-flight gate, retry, HTTP->typed-error mapping, credential
resolution, and verification. It is FX-free and reaches the network only through
`java.net.http.HttpClient`. Ports live in `ua.bookloom.api.llm`; impl in `ua.bookloom.llm`.

## When to use

- Adding or wiring a provider (Ollama-native or OpenAI-compatible for LM Studio / any
  OpenAI-shaped server).
- Implementing or calling `chat`, `listModels`, or the three-stage verification.
- Handling model responses (structured output, sanitize, repair, text fallback).
- Wiring per-project provider/model binding and preflight verification.
- Wiring retry, the InferenceGate, or HTTP error mapping.
- Resolving a credential reference at request time.

## When NOT to use

- Do NOT let callers branch on provider kind or touch a concrete client. Everything goes
  through the `Provider` port + `ProviderFactory`. Two clients exist today
  (Ollama-native, OpenAI-compatible); a future kind (Gemini/Claude) is a new
  implementation registered in the factory — NOT required in this scope.
- Do NOT drive Ollama through the OpenAI-compatible `/v1/*` shim — use its native API so
  `num_ctx`, `keep_alive`, `format`, and `think` are honored.
- Do NOT introduce an embedding slot or vector store — only translator + judge/helper
  slots exist; consistency is dictionary + string-similarity TM + rolling summary.
- Do NOT put retry in `:pipeline` or `:ui` — retry is owned by `:llm`.
- Do NOT store, log, or echo a secret or full book text — store a `CredentialRef`, resolve
  at call time.
- Do NOT call `chat` outside the `InferenceGate` — a local model serves one request.
- Do NOT run inference without preflight-verifying the project's bound provider/model, and
  never silently switch provider/model — fall back only on user confirmation.
- Do NOT `requires java.net.http` from any module other than `:llm` (ArchUnit
  `no-http-in-core-except-llm`).

## Workflow

1. **Pick the client by kind, behind the factory.** `ProviderFactory.create(config)` maps
   `config.kind()` to a concrete client: `OLLAMA` → the Ollama-native client (`/api/chat`,
   `/api/tags`, `/api/show`, native `options`), `OPENAI_COMPATIBLE` → the OpenAI-compatible
   client (`/v1/chat/completions`, `/v1/models`, `response_format`). The `ProviderProfile`
   carries per-kind data: `kind`, `defaultAuthScheme` (NONE | BEARER | API_KEY_HEADER),
   `defaultBaseUrl`, `discoveryStrategy` (OLLAMA_TAGS | OPENAI_MODELS | NONE),
   `supportsModelDiscovery`, and `capabilities`. A new kind is a new implementation + enum
   constant + factory entry — callers never change and never branch on kind.
2. **Build the request.** `chat` is **synchronous**, returning `Result<ChatResponse>`
   (whole body) — streaming is deferred from v1, so there is NO `ChatRequest.stream`
   field or streaming path (cancellation = request timeout + cooperative interrupt at
   chunk boundary). `ChatRequest` fields `temperature`, `topP`, `maxTokens`,
   `numCtx`, `responseFormat` are NULLABLE and OMITTED from JSON when null
   (Jackson `@JsonInclude(NON_NULL)`) — a `null` temperature is absent, never sent as
   `null`, so strict endpoints do not reject it. Request structured output where supported
   (`responseFormat`/OpenAI `response_format`, Ollama `format`) and set reasoning low/off
   (Ollama `think`, OpenAI reasoning-effort) for translation/judge calls.
3. **Ollama options go native.** `num_ctx`, `keep_alive`, `format`, and `think` ride in the
   Ollama-native client's `options`; do not try to express them on an OpenAI `/v1/*`
   request (the shim does not fully honor them). The OpenAI-compatible client uses
   `response_format` and its reasoning params.
4. **Handle the response JSON-first but tolerant.** Sanitize before parsing — strip
   `<think>…</think>` blocks, chain-of-thought preambles, code-fence wrappers, and stray
   prose; locate the JSON object. Parse with a tolerant `ObjectMapper`
   (`FAIL_ON_UNKNOWN_PROPERTIES=false`, missing defaulted, trimmed). On malformed JSON do
   ONE repair retry ("return only valid JSON matching …"); if still unparseable, treat the
   sanitized text as the plain translation and flag the chunk only if it then fails QA.
5. **Discover models, with manual entry as a first-class override.** `listModels()` follows
   the profile's `discoveryStrategy` (Ollama `/api/tags`, OpenAI `/v1/models`). When
   `supportsModelDiscovery` is false, or discovery fails, is unauthorized, or returns empty,
   use a free-text model-ID field (pre-filled with any remembered model) — an offline or
   permission-limited endpoint never blocks configuration; a hard discovery failure maps to
   `discoveryFailed`. Persist translator + judge/helper slots (no embedding slot), the
   remembered model, and the per-provider **effective context (tokens)** value — resolved
   Ollama `num_ctx`/`/api/show` → discovery → manual field → conservative default; the
   chunk packer budgets `min(effectiveContext − reservedHeadroom, chunkBudgetSetting)`
   (DD-44).
6. **Bind provider/model per project and preflight before ANY inference.** Settings are
   defaults for new projects only; a project persists and reuses its own bound provider +
   models. Before real runs and diagnostics, verify connection + bound-model availability
   (reuse three-stage verification); on unavailability prompt and fall back to the current
   default only on confirmation; when settings differ from the project's last-used binding,
   prompt (default: continue with previous). Never switch silently.
7. **Resolve credentials as a reference.** `ProviderConfig` holds a `CredentialRef` — an
   env-var name or an OS-keychain entry id, never a secret. At request time resolve it,
   use it for that one call, never persist/log/echo it. A reference resolving to nothing ->
   `ErrorCode.missingCredential`. Local kinds default to `AuthScheme.NONE`.
8. **Wrap inference in the gate.** `InferenceGate.run(...)` (blocking, fair semaphore
   permit=1) acquires before every `chat` and releases in `finally`. Use `tryRun(call,
   wait)` for interactive actions so a user "retry now" fails fast with `ErrorCode.busy`
   instead of queueing behind a batch. The gate wraps inference only; parse/QA/persist run
   concurrently around it.
9. **Let the service own retry.** Retry only typed retryable errors — `timeout`,
   `rateLimited`, `unreachable`, `upstream` (5xx). Do NOT retry `auth`, `modelNotFound`,
   `contextWindow`, `validation`, `emptyCompletion`. Honor `Retry-After` when present,
   else exponential backoff with jitter. Give each attempt a FRESH per-attempt timeout.
   Bounded attempts; on exhaustion return the last typed `AppError` in the `Result`.
10. **Map every HTTP outcome to one typed `AppError`** (see table). Never leak a raw
   exception; return a `Result<T>` with the typed error.
11. **Implement three-stage verification on the DRAFT config** (not the saved one), each
   stage returning its own `Result` so the UI shows per-stage pass/fail:
   (1) Connection — reach `baseUrl` (probe / discovery endpoint); (2) Models —
   `listModels()` succeeds or degrades to free-text manual entry (soft pass); (3) Inference
   — a minimal `chat` round-trip returns non-empty content. Stages run in order and
   short-circuit on hard failure. This trio maps to the mockup add/edit-provider dialog
   buttons, and the same checks are the per-project preflight before any run/diagnostic.

## HTTP -> ErrorCode mapping

| Condition | ErrorCode | Retryable |
|---|---|---|
| connect refused / DNS / no route | `unreachable` | yes |
| socket/read timeout | `timeout` | yes |
| 401 / 403 | `auth` | no |
| 404 model / unknown model in body | `modelNotFound` | no |
| 429 | `rateLimited` (honor Retry-After) | yes |
| 5xx | `upstream` | yes |
| 400 context length exceeded | `contextWindow` | no |
| 200 but raw-empty (blank pre-sanitize) content | `emptyCompletion` | no |
| model-list/capability discovery call failed (not auth/unreachable) | `discoveryFailed` | yes |
| bound model absent at run/resume preflight | `modelUnavailable` | no |
| missing credential at resolve time | `missingCredential` | no |
| unparseable body / other | `internal` | no |

A non-empty response that sanitizes to empty is NOT `emptyCompletion` — it gets one
empty-after-sanitize repair retry, then an empty text-fallback result that QA flags.

## Reference index

- In-repo authorities: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
  `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`,
  `docs/specification/02_Architecture/09_ERROR_HANDLING.md`,
  `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`.

## Mandatory validation checklist

- [ ] Callers use only the `Provider` port + `ProviderFactory`; no caller branches on kind
      or touches a concrete client.
- [ ] Ollama is driven natively (`options.num_ctx`, `keep_alive`, `format`, `think`), never
      through the `/v1/*` shim.
- [ ] Structured output requested where supported; reasoning set low/off for translate/judge.
- [ ] Responses sanitized (strip `<think>`, CoT, code fences, prose) and parsed tolerantly.
- [ ] One repair retry on malformed JSON, then deterministic plain-text fallback; flag only
      if QA then fails.
- [ ] Only translator + judge/helper slots exist — no embedding slot or vector store.
- [ ] Discovery degrades to first-class manual model-ID entry on
      no-discovery/failure/empty/unauthorized (`supportsModelDiscovery`).
- [ ] Provider/model bound per project; connection + model availability preflighted before
      any inference; no silent switch (fallback only on user confirmation).
- [ ] One injected `HttpClient`, fresh per-request timeout, record DTOs in internal `dto`
      package (`@JsonInclude(NON_NULL)`), tolerant `ObjectMapper`; secrets/book text never logged.
- [ ] Nullable `ChatRequest` params are omitted from JSON when null; there is no
      `ChatRequest.stream` field or streaming path (`chat` is synchronous; streaming
      FR-INFER-04 is deferred from v1).
- [ ] Credentials are `CredentialRef` only; secrets never stored/logged/echoed.
- [ ] Every `chat` call goes through the `InferenceGate` (`run`/`tryRun`).
- [ ] Retry only on the typed retryable set; Retry-After honored; fresh per-attempt timeout.
- [ ] Every HTTP/transport outcome maps to exactly one typed `AppError`/`ErrorCode`.
- [ ] Three-stage verification runs on the DRAFT config with per-stage results.
- [ ] `:llm` is FX-free; only `:llm` requires `java.net.http` (ArchUnit green).

## Gotchas

- Ollama's OpenAI-compatible `/v1/*` shim silently drops/misapplies `num_ctx` (and
  `keep_alive`/`format`/`think`) — that is why Ollama uses the native client; don't "fix" it
  by routing Ollama through `/v1/*`.
- Be strict in the ask (request JSON) but tolerant in the parse: unknown fields ignored,
  missing defaulted. Exactly ONE repair retry, then plain-text fallback — not a repair loop.
- `emptyCompletion` (200 with blank body) is NOT retryable — retrying wastes the gate.
- `tryRun` prevents an interactive "retry now" from deadlocking behind a long batch; use
  it for user-triggered single calls, `run` for the batch loop.
- Verification tests the draft config so a broken save is caught before it is persisted; the
  same connection+model checks run as the per-project preflight before every run.
- Do not retry `auth`/`contextWindow` — they will never succeed on retry and burn budget.
- Settings are defaults for NEW projects only; a resumed project keeps its own bound
  provider/model to avoid mid-run quality regression.
