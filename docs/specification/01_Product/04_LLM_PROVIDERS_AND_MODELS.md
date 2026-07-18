**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:llm`), QA **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/07_SETTINGS.md`, `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`,
`docs/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md`

# LLM Providers and Models

This document specifies provider configuration, model selection, and inference behaviour, realizing `FR-PROV-*`,
`FR-MODEL-*`, and `FR-INFER-*`. It is implemented in the `:llm` module. Model access goes through a provider abstraction
with two client implementations — an Ollama-native client and an OpenAI-compatible client; the only outbound network is
**user-triggered provider communication — inference, model discovery, and verification** (DD-01, DD-09).

## provider-model {#provider-model}

A `Provider` port plus a `ProviderFactory` hide the concrete client from callers (DD-10). Two client implementations
exist: an **Ollama-native** client (Ollama `/api/chat`, `/api/tags`, `/api/show`, native `options` including `num_ctx`,
`keep_alive`, `format`, `think`) used for Ollama, and an **OpenAI-compatible** client (`/v1/chat/completions`,
`/v1/models`, `response_format`) used for LM Studio and any other OpenAI-compatible server. Ollama is spoken to natively
because its OpenAI-compatible endpoint does not fully honor options such as `num_ctx`. A provider **kind**
(`OLLAMA | OPENAI_COMPATIBLE`, extensible) selects the client via the factory; the abstraction already admits future
implementations (e.g. Gemini, Claude) without changing callers, but those are not implemented and are out of scope here.

| ID           | Requirement                                                                                    |
|--------------|------------------------------------------------------------------------------------------------|
| FR-PROV-01a  | The user can add, edit, select (mark current), and delete providers from Settings → Providers. |
| FR-PROV-04a  | Multiple providers may be configured; exactly one is the current default at a time.            |
| FR-MODEL-04a | The selected translator and judge/helper models are remembered per provider.                   |

## provider-kinds-and-presets {#provider-kinds-and-presets}

| Preset                  | Client (kind)                           | Endpoint shape                                          | Auth scheme                   | Discovery                     |
|-------------------------|-----------------------------------------|---------------------------------------------------------|-------------------------------|-------------------------------|
| Ollama (local)          | Ollama-native (`OLLAMA`)                | `/api/chat`, `/api/tags`, `/api/show`, native `options` | None by default               | Live model list (`/api/tags`) |
| LM Studio (local)       | OpenAI-compatible (`OPENAI_COMPATIBLE`) | `/v1/chat/completions`, `/v1/models`                    | None by default               | Live model list or manual     |
| llama.cpp / vLLM        | OpenAI-compatible (`OPENAI_COMPATIBLE`) | `/v1/*`                                                 | None / optional               | Live model list or manual     |
| Other OpenAI-compatible | OpenAI-compatible (`OPENAI_COMPATIBLE`) | Configurable base URL, `/v1/*`                          | Bearer / API key by reference | Live model list or manual     |

Each preset seeds endpoint, auth scheme, discovery capability, and quirks from the profile; the user may override
fields. The one OpenAI-compatible client serves every OpenAI-shaped server, so pointing at a new such server is
configuration, not code.

## authentication-and-credentials {#authentication-and-credentials}

| ID          | Requirement                                                                                                                         |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------|
| FR-PROV-05a | Credentials are stored only as a reference — an environment-variable name or an OS keychain entry — never the secret value (DD-11). |
| FR-PROV-05b | The secret is resolved at call time from the referenced source; it is never written to the database, logs, or exports.              |
| FR-PROV-05c | Auth schemes supported: none, bearer token, and custom header, each resolving its value from the credential reference.              |

## model-discovery-and-slots {#model-discovery-and-slots}

| ID           | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-MODEL-01a | Models are discovered live from the current provider and presented for selection when the provider supports discovery (`ProviderProfile.supportsModelDiscovery`).                                                                                                                                                                                                                                                                                 |
| FR-MODEL-02a | Manual model-ID entry is first-class and always available as an override; when `supportsModelDiscovery` is false, or discovery fails, returns empty, or is unauthorized, the UI uses free-text model-ID entry (EC-MODEL-1).                                                                                                                                                                                                                       |
| FR-MODEL-03a | Two model slots exist: translator (required) and judge/helper (used when the judge or reflection runs). There is no embedding slot.                                                                                                                                                                                                                                                                                                               |
| FR-MODEL-05a | A model is **available** when: with discovery, its ID is a member of the live model list (`listModels()`); without discovery (a manually-entered model ID), the Inference verification round-trip against that model **is** the availability check — a non-empty completion proves availability, a failure marks it unavailable (`modelUnavailable`). Availability is confirmed before the provider is used for a run (see #per-project-binding). |
| FR-MODEL-06a | Each provider carries an **effective context (tokens)** value resolved in order: Ollama `num_ctx` / `/api/show` reported context → discovery-reported context → the provider profile's manual **"effective context (tokens)"** field → a conservative built-in default. The chunk packer budgets against this resolved value (see #effective-context).                                                                                            |
| FR-MODEL-07a | Each provider carries a `supportsStructuredOutput` capability, **populated during the Models/Inference verification stage** and thereafter cached on the provider. It records whether the server honoured a structured-output request; it never blocks a run, because structured output is always attempted and a rejection silently downgrades to text (see #response-handling).                                                                 |

Cross-chapter consistency is provided by the name/term dictionary, a context-aware translation memory (exact / context /
fuzzy via deterministic string similarity — not vectors), and a rolling bilingual summary; no embedding model or vector
store is used or reserved.

## candidate-models {#candidate-models}

The app is model-agnostic: any compatible chat model can be configured. The table below is **candidate guidance, not a
supported-model guarantee or a fixed list** (DD-42) — it records how representative current local models differ in the
two properties the pipeline cares about (structured-output support and reasoning behaviour) so defaults and evals stay
sane as the field moves. What the app must handle regardless is captured by the response-handling contract
(`#response-handling`, DD-33): request structured output where supported, strip reasoning, tolerate variance, fall back
to text.

| Model family (Ollama)        | Structured output / tools                    | Reasoning ("thinking")                                            | Context   | Notes for this app                                                                                      |
|------------------------------|----------------------------------------------|-------------------------------------------------------------------|-----------|---------------------------------------------------------------------------------------------------------|
| ministral-3 (3b/8b/14b)      | Native function-calling + JSON               | None (fast)                                                       | ~256K     | Structured-output-friendly default; multilingual; small                                                 |
| granite4 (micro … 32b-a9b-h) | Native tools + JSON                          | None                                                              | 128K      | Structured-output-friendly; fast; no reasoning to strip                                                 |
| gemma4 (e2b/e4b/12b/26b/31b) | Function-calling; JSON viable                | Configurable; **26b-a4b MoE reasoning may not disable cleanly**   | 128K–256K | 12b a good default; multimodal (image not used for text translation)                                    |
| qwen3.5 (4b/9b/27b/35b)      | Tools; JSON/response_format                  | Thinking mode (toggle exists; leakage risk)                       | ~256K     | Strongest multilingual coverage — use for Asian/long-tail languages; needs reasoning-strip              |
| qwen3.6 (27b/35b)            | Tools                                        | Thinking + preservation                                           | 256K      | Same lineage; needs reasoning-strip                                                                     |
| gpt-oss:20b                  | Native function-calling + structured outputs | Always-on chain-of-thought; **reasoning effort configurable low** | 128K      | Set reasoning effort low; English-centric — weaker for non-English translation                          |
| qwen3-vl (4b/8b)             | Tools / visual                               | Instruct + thinking variants                                      | 256K      | **Vision model — not used for text translation**; candidate for the visual-eval vision reviewer (DD-41) |

Guidance: prefer a **structured-output-friendly, non-reasoning** model as the translator default (ministral-3, granite4,
gemma4:12b) for clean JSON with nothing to sanitize; models with always-on or non-disableable reasoning (qwen3.5/3.6
thinking, gemma4:26b-a4b, gpt-oss) rely on the text-fallback + reasoning-strip path. "Smallest viable" defaults suit
low-VRAM machines. Note: some listed tags (e.g. `granite4.1:30b`) may not exist under that exact name in a given Ollama
version — the closest real tag is used, and prior-generation models (gemma3, qwen3, granite3, mistral/ministral
2024–2025) are valid fallbacks.

**Embedding models are used only by the local prompt-eval harness**
(`04_Build_and_Release/06_TESTING_STRATEGY.md#prompt-evals`), never by the app: a small multilingual embedder is the
default — `embeddinggemma:300m` (768-dim, 100+ languages, ~2K context), or `bge-m3:567m` (1024-dim, 8K context) for long
inputs. English-only embedders (`nomic-embed-text` v1.5, `bge-large`) are avoided for a multilingual harness.

## effective-context {#effective-context}

The token budget is driven by a per-provider **effective context (tokens)** value, not by a single hard-coded window
(DD-44). It is resolved deterministically, first hit wins:

1. **Ollama `num_ctx` / `/api/show`** — the Ollama-native client reads the model's reported context from `/api/show` (or
   an explicit `num_ctx`) as the authoritative value.
2. **Discovery** — a context length reported by the model-listing/metadata response, where a server exposes one.
3. **Manual "effective context (tokens)" field** — a user-editable field on the provider profile, used when neither of
   the above is available (e.g. an OpenAI-compatible server that reports no context).
4. **Conservative built-in default** — a safe floor (aligned with the settings default of `32768`) when the value is
   unknown.

The chunk packer budgets against the **resolved** value:
`effectiveBudget = min(effectiveContext − reservedHeadroom, chunkBudgetSetting)`, where reserved headroom accounts for
the system prompt, injected context, and the target-side output. When multiple `num_ctx`/context inputs collide,
precedence is **request-level > provider-level > setting default**, and the packer always budgets the single resolved
value. The token count itself is a deterministic heuristic (no shipped tokenizer); the formula lives in
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`.

## three-stage-verification {#three-stage-verification}

Verification runs against the **draft** provider configuration before it is saved (FR-PROV-06), so an unverified change
never affects the current working provider. The three stages are reported independently (FR-PROV-07):

| Stage      | Check                                                                                                                                                                                                                                                                                                                                              | Failure surfaced as                                                     |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| Connection | A dedicated **provider probe** (`probe()`/`verifyConnection()`) reaches the endpoint: Ollama hits the root / `/api/version`, an OpenAI-compatible server does a `/v1/models` HEAD or cheap GET. A **credential-resolution pre-check** runs first — a draft credential reference that does not yet resolve fails this stage as `missingCredential`. | Typed `AppError` (connection / `missingCredential`), banner/toast.      |
| Models     | Model listing (`listModels()`) succeeds (or manual fallback offered). Discovery failure is a typed `discoveryFailed`.                                                                                                                                                                                                                              | Typed `AppError` (`discoveryFailed`), fallback path.                    |
| Inference  | A minimal chat completion returns a valid response from the chosen model; this **also populates** `supportsStructuredOutput` (whether the structured-output request was honoured or silently downgraded). For a manually-entered model this round-trip **is** the availability check.                                                              | Typed `AppError` (inference / `modelUnavailable`), stage marked failed. |

The provider dialog exposes this as a Test connection / models / inference trio (see
`08_UI_SCREENS_AND_STATES.md#dialog-add-edit-provider`). Because Connection and Models are separated by the dedicated
probe, the two stages are reported independently even when discovery is unsupported. Provider-dialog verification
acquires the InferenceGate with a **bounded wait** (`tryRun`); if the gate is held, the stage surfaces `busy` as a
"model in use, try again" toast rather than blocking.

## inference-behaviour {#inference-behaviour}

| ID           | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-INFER-01a | Inference goes through the `Provider` port (`ChatRequest` → `ChatResponse`); the concrete client is the Ollama-native or OpenAI-compatible one per provider kind. No other outbound calls are made.                                                                                                                                                                                                                                                                  |
| FR-INFER-02a | The Ollama-native client sets `num_ctx` and related native `options` (`keep_alive`, `format`, `think`) that the OpenAI-compatible shape cannot express.                                                                                                                                                                                                                                                                                                              |
| FR-INFER-03a | Nullable/unset request parameters are omitted from the request body rather than sent as null.                                                                                                                                                                                                                                                                                                                                                                        |
| FR-INFER-04a | Streaming is **deferred (out of scope for v1)**. `Provider.chat` is synchronous and returns the whole `ChatResponse` body (`Result<ChatResponse>`); there is no streaming request flag. Cancellation is the HTTP request timeout (a hard upper bound) plus a cooperative interrupt at the next boundary; **worst-case cancel latency is the request read-timeout, not instant** (see `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`). |
| FR-INFER-06a | All inference passes through the single-flight InferenceGate so a local model serves one request at a time (DD-12).                                                                                                                                                                                                                                                                                                                                                  |

## response-handling {#response-handling}

Every model-facing call is JSON-first but tolerant, with a single repair retry and a deterministic text fallback:
| ID | Requirement | |---|---| | FR-INFER-09a | Structured output is **always attempted** (OpenAI `response_format`;
Ollama `format`). A structured-output rejection (HTTP 400 / "unsupported") is a **silent downgrade**: the call is
retried as a plain request and continues down the text-fallback path — never a hard failure. The outcome is recorded on
the provider's `supportsStructuredOutput` capability. | | FR-INFER-09d | Reasoning control is **best-effort**: the
reasoning/effort parameter is set low/off for translation and judge calls **where controllable** (Ollama `think`,
informed by `/api/show`; the OpenAI-compatible reasoning-effort field), and is **omitted when unsupported**. Whether or
not the parameter takes, output always falls through to the `<think>`-strip sanitize path, so an always-on or
non-disableable reasoning model still yields clean output. | | FR-INFER-09b | Responses are sanitized before parsing —
`<think>` reasoning blocks, chain-of-thought preambles, code-fence wrappers, and stray prose are stripped — then parsed
tolerantly (unknown fields ignored, missing defaulted, whitespace trimmed). | | FR-INFER-09c | Malformed JSON triggers
exactly one repair retry; if still unparseable the sanitized text is used as the plain translation, and the chunk is
flagged only if it then fails the QA gates. **Empty-response ordering:** a **raw-empty** response (blank *before*
sanitize) maps to `emptyCompletion`; a **non-empty response that sanitizes to empty** takes one repair retry
specifically for empty-after-sanitize, and if still empty yields an empty text-fallback result that QA then flags — it
is not an `emptyCompletion`. |

## per-project-binding {#per-project-binding}

Provider and model selection is bound per project; Settings values are only defaults for new projects. States and
dialogs are in `08_UI_SCREENS_AND_STATES.md`; storage is in
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`. | ID | Requirement | |---|---| | FR-PROV-08a | The
`current_provider` setting is the **new-project default only**; on creation the current defaults are copied into the
project and recorded as its last-used snapshot. `current_provider` is **never consulted for an existing project's
run** — a project always runs on its own bound provider/models. | | FR-PROV-08b | A project persists and reuses its own
bound provider and models on every resume, so it does not switch mid-run when settings later change. | | FR-PROV-09a |
Before any inference (real runs and diagnostics), the bound provider connection and model availability are verified
using the availability definition in FR-MODEL-05a. **Scope:** the **translator** model is always verified; the
**judge/helper** model is verified only when the quality dial will invoke the judge. On unavailability the app prompts
and falls back to the current default only on explicit confirmation, recording the change. | | FR-PROV-10a | On resume
the project's `last_used_json` snapshot is compared against the **live provider row**; on **any** drift (not only a
settings-default difference) the app raises the ADR-0012 "apply new selection vs continue with the previous one" prompt
(default: continue). |

## retry-and-error-mapping {#retry-and-error-mapping}

| ID           | Requirement                                                                                                                        |
|--------------|------------------------------------------------------------------------------------------------------------------------------------|
| FR-INFER-05a | Retry is service-owned and keyed on typed retryable errors; it honours `Retry-After` and uses a fresh timeout per attempt (DD-13). |
| FR-INFER-05b | Non-retryable errors fail fast without retry.                                                                                      |
| FR-INFER-07a | HTTP status and transport failures map to typed `AppError`/`ErrorCode` values with a safe-details allowlist (DD-14).               |

### provider-edge-cases {#provider-edge-cases}

| ID          | Edge case                                                | Expected behaviour                                                                                                                                                                                                                                                                        |
|-------------|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-MODEL-1  | Model discovery missing, empty, failing, or unauthorized | Offer manual model-ID entry (always available as an override); do not block provider setup.                                                                                                                                                                                               |
| EC-MODEL-2  | Selected model no longer available at run time           | Fail with `modelUnavailable`; prompt re-selection; do not silently substitute.                                                                                                                                                                                                            |
| EC-CTX-1    | Context-window sizing needed                             | Resolve the effective context per #effective-context (Ollama `num_ctx`/`/api/show` → discovery → manual field → conservative default); the packer budgets the resolved value. For an OpenAI-compatible server that cannot express `num_ctx`, use the manual field or default and proceed. |
| EC-CONCUR-2 | Provider-dialog verification while a run holds the gate  | Diagnostics acquire via `tryRun` with a bounded wait; on a held gate, surface `busy` as a "model in use, try again" toast rather than blocking.                                                                                                                                           |
| EC-NET-1    | Connection refused / DNS / TLS failure                   | Map to a typed connection error; surface via banner/toast; retry only if retryable.                                                                                                                                                                                                       |
| EC-NET-2    | HTTP 429 / rate limit with `Retry-After`                 | Honour `Retry-After`; retry within budget; then fail typed.                                                                                                                                                                                                                               |
| EC-CONCUR-1 | Concurrent inference attempts against one local model    | Serialize via InferenceGate; second caller waits, does not fail.                                                                                                                                                                                                                          |
