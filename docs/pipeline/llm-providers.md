# LLM providers

How a model call leaves BookLoom and comes back: the `ChatModel` port, the two HTTP clients, server engines, model
profiles, the reply pipeline, retries and timeouts, timings, discovery and verification, credentials and the one
first-launch listing.

Decisions: [ADR-0047](../spec_baseline/adr/ADR-0047-server-engines-and-model-profiles.md) (server engines and model
profiles), [ADR-0044](../spec_baseline/adr/ADR-0044-look-for-a-local-ai-program-at-first-launch.md) (first-launch
listing), [ADR-0005](../spec_baseline/adr/ADR-0005-provider-abstraction.md) and
[ADR-0013](../spec_baseline/adr/ADR-0013-response-handling-contract.md) as amended by ADR-0047. Design:
`openspec/changes/redesign-translation-pipeline/design.md` D17–D23. Classes are under
`modules/llm/src/main/java/ua/bookloom/llm/` unless stated. Only `:llm` holds HTTP code (ArchUnit
`no-http-in-core-except-llm`).

Privacy: BookLoom is **private when used with local models**. With a provider outside the computer, the text being
translated is sent to that provider's host, which is the person's choice.

## The port

| Type | Module | Role |
|---|---|---|
| `ChatModel` | `:api` `llm/ChatModel` | `Result<ChatResponse> chat(ChatRequest)`; also exposes the resolved `ModelProfile` and the person's window override |
| `ChatModelFactory` | `:api` `llm/ChatModelFactory` | `Result<ChatModel> create(ModelSelection)` |
| `ChatModelFactoryImpl` | `:llm` | resolves the provider id `pseudo` to `pseudo/PseudoChatModel`, any registered provider to a `GatedChatModel` over a `ProviderClient` |
| `ProviderClient` / `ProviderClientFactory` | `:llm` `provider/` | internal seam, never seen outside `:llm` |
| `GatedChatModel` | `:llm` | binds one model id to a client, applies the model's settings and profile on each call, retries typed failures outside the gate, records each call |
| `InferenceGate` | `:llm` `gate/` | one fair semaphore permit: every model call in the process is single-flight |

Every port method returns `Result<T>` with a typed `AppError`/`ErrorCode`; no exception crosses the module edge.

The **pseudo model** is deterministic and offline: it upper-cases the draft's text block (or the last user message) and
answers every structured call kind with a valid shape (for example `{"unchanged":true}` for Proofread). It is what the
command-line fixture run and many tests use.

## Clients and server engines

Two clients speak the two wire dialects:

- `client/ollama/OllamaClient` — Ollama's native `/api/chat`, reply read as a **stream** that must keep arriving
  (`OllamaReplyReader`; an idle gap of 60 s, or the shorter provider timeout, is a stall), `num_ctx` sent per request
  (`OllamaWindow`), reasoning switched through `think` (`OllamaReasoning`).
- `client/openai/OpenAiCompatibleClient` — `/v1/chat/completions` and `/v1/models` for every other server. It never
  sends a context size.

The **server engine** (`:api` `llm/ServerEngine`) names the program behind an address and selects request fields, error
rows and routes — not the client:

| Engine | Detected by (`engine/EngineDetector`) | Notes |
|---|---|---|
| `OLLAMA` | `/api/version`, `/api/tags`, `/v1/models` rows that all carry `owned_by` | native or via its OpenAI routes |
| `LLAMA_CPP` | `/health`, `/props` | waits while llama-server loads its model |
| `UNSLOTH` | `/api/health` | key variable for its server |
| `LM_STUDIO` | `/api/v0/models` | own chat route with fallback; never sends a quant variant id |
| `OPENROUTER` | known host | endpoint limits, no routing or data-policy fields, errors read inside a 200 reply |
| `GENERIC` | anything else | gets no control until a check proves it |

Detection reads reply shapes, never a port or a status (LM Studio answers 200 to every path; KoboldCpp and LocalAI copy
Ollama's routes), with at most 7 GETs. It runs only on Save, Test and Detect again; the person's "Treat this server
as…" wins without a request. `engine/EngineTraits` turns the engine into request fields: where the answer cap goes, the
thinking-off switch, fields always sent, neutral sampler values, whether unknown ids are served.

## Model profiles

A `ModelProfile` (`:api`) is resolved afresh on every call and never stored (`profile/ModelProfiles`), later sources
winning:

1. the engine's traits (can any thinking control be sent at all);
2. a built-in row picked from the model's name read with the server's metadata (`profile/BuiltInRows`, per-family rows
   such as `GemmaRows`, `QwenRows`, `MistralRows`, each with its evidence; `profile/ModelIdentityParser` reads a name,
   link, pull command or file name);
3. the server's facts about the model (`capability/ModelFactsReader`) — read **only when the person acts** (selecting
   a model, the Models tab, Test, Start, Continue), kept for the session, never stored, every failure answered as
   "unknown";
4. the person's overrides on Settings › Models.

An unknown model gets `ModelProfile.defaults()`: no thinking control, answers asked through the prompt, BookLoom's own
temperatures, batch size 2. Fine-tunes take their base model's behaviour; a shared architecture name never picks a row.

What a profile decides per call:

| Field | Effect |
|---|---|
| `reasoning` | the thinking setting per call kind, sent as the engine's own off value; a model that always thinks gets reasoning room on top of the answer cap (`profile/ReasoningReserve`) |
| `systemRole` | a system message, or folded into the first user message (`client/MessageShaper`) |
| `quirks` | template work-arounds: escape `/think` in book text, no reasoning-level line, start on the prompt-only rung, no examples, … |
| `sampling` | the profile's samplers or the person's values |
| `context` | the one window every call to the model is sized by |
| `batchSize`, `tier` | draft batch size and the context budgets' size class (see [context-glossary-memory.md](context-glossary-memory.md)) |
| `markupFilters` | extra reply filters (`to=self` blocks, `\boxed{}`) for the models that write them |

**Structured rungs** (`StructuredRungs`): a structured answer is asked with a JSON schema or with the contract in the
prompt only. A schema the server cannot use, a schema not applied twice in a row, or a reply the schema cut at a quote
moves that call kind one rung down; a server with no structured output moves every kind down. A setting the server
refuses with 400/422 is dropped once and remembered per model and call kind (`provider/RefusalMemory`); no answer schema
is sent while a call thinks.

**Model advice** (`knowledge/QuantAdvisor`, `knowledge/LanguageAdvisor`, bundled `quant-tiers.csv` and
`declared-languages.csv`): a quant-quality chip and a line when the maker does not declare the book's languages.
Advice is shown and never blocks Start. A model recommendation from the computer's memory and the Ollama pull command
are text only; BookLoom never runs the command.

## Reply pipeline

1. **Read** (`OllamaReplyReader`, `OpenAiReplyReader`): text from `message.content` or `choices[0].message.content`, the
   finish reason (`stop`, `length`, other) and usage. A separate reasoning field is read only to detect a reply that
   spent its cap thinking and to time the first token; it never reaches the text. Blank content is
   `ErrorCode.emptyCompletion`.
2. **Sanitise** (`response/ReasoningScrubber`): remove thinking blocks and chat-template tokens (`<think>`, `[THINK]`,
   Gemma's thought channel, harmony channels, stray end-of-turn tokens), then the profile's filters, then one outer code
   fence and a leading `json` label. A reply that was only reasoning comes back empty and is asked once more.
3. **Parse** (in `:pipeline`): `batch/BatchReplyParser` is tolerant of wrapping and unknown fields but strict per id —
   every expected id exactly once with text. Missing ids get one format re-ask, then a batch of one. The single-item
   parser (`prompt/DraftReplyParser`) reads only the documented object, so wrapper prose never becomes book text;
   there is no plain-text acceptance of a draft.
4. **Validate**: the pipeline's gates and checks ([quality-gates.md](quality-gates.md)).

## Errors, retries and timeouts

- **Error catalogue** (`http/HttpErrorMapper`, `http/ErrorRows` with per-engine rows `OllamaRows`, `LmStudioRows`,
  `LlamaServerRows`, `UnslothRows`, `OpenRouterRows`): interruption, timeout and I/O first; then rows matched on the
  meaning of the body text; then the status (401/403 `auth`, 404 `modelNotFound`, 429 `rateLimited`, 5xx `upstream`,
  other 400 `validation`). Each classified failure names a reason token in the run log. `AppError.details` holds only
  allowlisted values.
- **Retry** (`retry/RetryPolicy`, `RetryChoice`, limits in `:api` `llm/CallLimits`): at most 3 requests per logical call,
  at most 2 of them timeouts; jittered exponential backoff (0.5 s base, 8 s max) or a valid `Retry-After`; a timed-out
  call is retried once with a new seed and three quarters of the cap; a repeat-limit reply is retried once with a new
  seed, then the segment is flagged. Backoff waits happen outside the gate.
- **Timeouts** (`http/RequestTimeouts`): helper calls (names, brief, file name, summary) 2 min, or 60 ms per expected
  token for a model that thinks first; every other call `max(provider timeout, min(600 s, 0.5 s × expected tokens))`;
  the default provider timeout is 180 s. The pipeline's stall watchdog sits above this
  ([translation-pipeline.md](translation-pipeline.md#bounded-calls-pauses-and-recovery)).
- A passage that does not fit the window flags the segment instead of pausing the run.

## Timings and the call ledger

Each call is recorded by `CallLedgerImpl` / `CallRecorder` with its kind, attempts, tokens and times. Time to first token
and prompt-reading time come from the engine where it reports them — Ollama's first streamed line and prompt duration,
LM Studio's `stats`, llama-server's `timings` — and are **missing, never 0**, where it does not (Ollama's OpenAI path,
OpenRouter, generic servers). The window shows a missing figure as an en dash (–), and a missing figure stays out of
averages. Per-kind seconds feed the next run's estimate (`:api` `llm/CallSeconds`, `MeasuredSpeed`).

## Discovery and verification

- **Discovery** (`ModelCatalogService`, `profile/ListedModels`): lists a provider's chat models (each listed model is
  classed by `:api` `llm/ModelKind`, so embedding and other non-chat kinds are left out; Ollama cloud models carry a
  neutral "cloud" label); manual model-id entry is always allowed. Every model picker has a name filter.
- **Verification** (`verify/ProviderVerifierImpl`): ordered stages — connection (with engine detection), models, model-id
  check against the server's own list, inference — plus optional advanced checks of what a server honours, only when
  the person asks. Settings › Providers tests the form as typed. A check is refused while a run translates.
- The command line runs a preflight before any job.

## Credentials

A credential is a **reference**: an environment-variable name, `${env:NAME}` in a header value. `http/RequestHeaders`
resolves it just before sending and refuses the call with `missingCredential` when the variable is unset, so nothing is
sent unsigned. Resolved values are never stored, logged or put in an `AppError`. `:api` `EnvironmentVariables` checks
presence without returning a value. An OS-keychain reference is allowed by the rules but not built.

## Network rules and the first-launch listing

All provider traffic is user-triggered: inference, discovery, verification, engine detection on Save/Test/Detect again.
The one exception (ADR-0044) is `modules/ui/src/main/java/ua/bookloom/ui/state/LocalModelProbe.java`: on the very first
launch only (a fresh database counts), after the window is shown, one silent model listing of exactly
`http://127.0.0.1:11434` (Ollama `/api/tags`) and `http://127.0.0.1:1234/v1` (LM Studio `/models`); no book text, no
inference, nothing answering is not an error, never repeated by itself (the person's "Try again" repeats it), desktop
window only. A program answering with at least one model is stored with the engine its listing shows.

## Testing

- The HTTP seam is tested with **WireMock**, stubbing both dialects (`/v1/chat/completions`, `/v1/models`, `/api/chat`,
  `/api/tags`, `/api/show`): request shaping, reply parsing, sanitising, retries, `Retry-After`, every HTTP-to-`ErrorCode`
  row. `ChatModel` and `ProviderClient` are never mocked.
- Engine detection is tested against recorded reply bodies in `modules/llm/src/test/resources/ua/bookloom/llm/engine/`,
  including look-alike servers that must not be taken for the engine they imitate.
- The real servers are checked by the `liveLocal` set (`EngineDetectionLiveTest`, `OllamaLiveTest`, `LmStudioLiveTest`,
  `LlamaCppLiveTest`, `UnslothLiveTest`), env-gated and never in `check`. See [../evaluation/README.md](../evaluation/README.md).

## See also

- [translation-pipeline.md](translation-pipeline.md)
- [context-glossary-memory.md](context-glossary-memory.md)
- [quality-gates.md](quality-gates.md)
- [../architecture/overview.md](../architecture/overview.md)
- [04_LLM_PROVIDERS_AND_MODELS.md](../spec_baseline/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md)
