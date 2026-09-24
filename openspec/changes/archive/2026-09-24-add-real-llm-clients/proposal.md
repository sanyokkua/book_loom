## Why

A book goes through the whole pipeline from the command line today, but the model behind it is the offline `pseudo`
provider that upper-cases text. Nothing has ever been translated. This change puts real inference behind the same
`ChatModel` the engine already holds: a local Ollama server spoken to natively, and LM Studio or any other
OpenAI-compatible server (llama.cpp, vLLM, a local gateway) spoken to through the OpenAI REST shape and reached by
its URL alone. The spec asks for exactly these two clients behind one provider abstraction, with typed errors,
service-owned retry, a single-flight gate and structured output requested on every call (`FR-INFER-01`,
`FR-INFER-05..09`, `FR-PROV-02`,
`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`, `#fr-prov`; `02_Architecture/04_LLM_INTEGRATION.md`).

The owner wants the **basis only**: the provider and client design, both dialects, real handling of requests and
responses, timeouts, plain retries, connection and model verification, structured output, a minimal reasoning strip,
a real single-segment draft prompt from the catalog, and a command line that can pick the
provider and the model. Provider editing and switching, persisted providers, the repair and fallback loop, and the judge
belong to later changes. The change consumes backlog rows 10 (`add-llm-provider-abstraction`) and 11
(`add-inference-gate-and-response-contract`) in this reduced form; the backlog is a reference, not a contract
(`docs/implementation_plan/CHANGE_BACKLOG.md`, the Stage C table).

## What Changes

- **Provider settings.** A provider is described by a kind (Ollama-native or OpenAI-compatible), a base URL, a connect
  timeout and a request timeout; nothing else. Two presets exist from the start: `ollama` at `http://localhost:11434`
  and `lmstudio` at `http://localhost:1234/v1`. A caller can register a further provider or override a preset for the
  current run; nothing is persisted yet. No credential, auth scheme or auth header exists in this change: the targets
  are local servers, and `openai-compatible` with a base URL covers any other unauthenticated server.
- **Two clients, one contract.** An Ollama-native client (`/api/version`, `/api/tags`, `/api/chat`) and an
  OpenAI-compatible client (`/v1/models`, `/v1/chat/completions`), each mapping every transport and HTTP outcome to
  one typed error code, omitting unset request parameters and requesting structured output (a JSON schema). No
  reasoning parameter is sent (no `think`, no `reasoning_effort`): the live probes showed a guessed value turning
  thinking on and emptying the reply, so the reply is stripped instead. No context-window size is sent either; the
  server's own setting applies.
- **Reply sanitising.** Reasoning blocks (`<think>`, `<thinking>`, `<reasoning>`, case-insensitive, an unterminated
  opener stripping to the end) and code fences are removed before the reply reaches the caller. A reply that is blank
  before sanitising is `emptyCompletion`.
- **Retry and the gate.** Up to three attempts on retryable codes only (`timeout`, `rateLimited`, `unreachable`,
  `upstream`, `discoveryFailed`), backoff from 500 ms to a cap of 8 s with jitter, `Retry-After` honoured, a fresh
  timeout per attempt, and one single-flight gate around every model call that is released while a retry sleeps.
- **Verification.** Three independent stages on a provider config: connection (a cheap probe), models (live list; the
  chosen model must be in it), inference (one short call). Stages run in order and stop
  at the first hard failure. The command line runs connection and models before every job, and inference only when
  discovery failed or listed nothing.
- **A real draft prompt.** The engine builds the catalog's draft-translation prompt for exactly one source segment per
  inference, optionally supplying the last three accepted targets from that section as context. It asks for the strict
  `{"target":"…"}` reply shape at temperature 0.2; malformed structure and any failed formatting restoration receive
  one separate, diagnostic repair request. There is no text, map, id, or batch fallback.
- **Command line.** `translate` gains `--provider`, `--model`, `--base-url` and `--timeout`, prints one line per
  verification stage before the job, and keeps `pseudo` as the default so every existing invocation still works.
- **Spec repairs made in the same change:** `04_LLM_INTEGRATION.md#http-error-mapping` says `discoveryFailed` is
  not retryable while `09_ERROR_HANDLING.md` and the code say it is; the spec's `Provider.chat(ChatRequest,
  RequestOptions)` names a type that does not exist, and per-call settings are nullable `ChatRequest` components
  (ADR-0033); `CHANGE_BACKLOG.md#where-this-stands` still calls `add-translation-engine-and-cli` pending archive.

## Capabilities

### New Capabilities

- `llm-provider`: describing a provider (kind, endpoint, timeouts), the presets, how each client dialect shapes its
  requests and reads its replies, how transport and HTTP outcomes become typed error codes, retry, the single-flight
  gate, model discovery, and the three-stage verification with the command line's preflight policy.

### Modified Capabilities

- `inference`: getting a chat model by provider id and model id now resolves `ollama` and `lmstudio` (and any
  registered provider), keeps `pseudo`, and refuses an unknown id or a blank model id. New: per-call temperature and
  response format; replies are sanitised of reasoning blocks and code fences; a blank reply is `emptyCompletion`.
- `translation-pipeline`: each segment is sent with the catalog draft prompt and the single-segment JSON reply
  contract instead of two ad-hoc sentences; the translate command picks a provider and model and reports its
  preflight; the exit codes cover the new failures.

## Impact

- **Modules:**
  - `:api`: new records and ports in `ua.bookloom.api.llm` (provider kind, provider config and registry port, model
    info, response format, verifier port and report); two nullable components on `ChatRequest` with the old
    constructor kept.
  - `:llm`: the two clients, the HTTP exchange and error mapper, Jackson DTOs, the sanitiser, retry, the gate, the
    in-memory provider registry, discovery and verification; the factory resolves a registered provider to a gated
    client model.
  - `:pipeline`: a `prompt` package (draft prompt builder, reply parser, reply schema); `SegmentTranslator` uses it.
  - `:document`: Markdown protects task-list marker state and rejects restored paired markup whose visible range is
    empty, while retaining the order-insensitive placeholder hard gate.
  - `:app`: the new `translate` flags, preflight output and exit codes.
  - `:persistence`, `:ui`: unchanged. The desktop window still opens no network connection.
- **Dependencies:** `jackson-databind` 2.20.x (Apache-2.0) enters the version catalog and `:llm` only; the `:llm`
  lockfile is regenerated. Jackson stays out of `:api` (ArchUnit `api-is-framework-free`). WireMock is already on
  every test classpath and gains its first suites.
- **Network:** the only new traffic is user-triggered provider communication (inference, discovery, verification) to
  the configured endpoint, on the command line only, and only when a real provider is chosen
  (`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#outbound-scope`).
- **Decision records:** no new ADR. ADR-0005 (two clients behind one port), ADR-0008 (single-flight gate), ADR-0013
  (JSON-first tolerant response contract) and ADR-0033 (bound chat model, per-call hints as nullable request fields)
  already settle the shape; `design.md` records the choices inside it. ADR-0006 (credentials as a reference) is not
  exercised here, because no credential exists in this change.
- **Docs:** `02_Architecture/04_LLM_INTEGRATION.md` (the two repairs above and the ports paragraph),
  `docs/implementation_plan/01_MODULE_INVENTORY.md`, `docs/implementation_plan/CHANGE_BACKLOG.md`, `AGENTS.md`
  "Where it stands", `docs/DEVELOPMENT.md` (running the command line with a real model; the `liveLocal` variables).
- **Non-goals:** provider editing, switching and persistence; per-project binding (ADR-0012); the structured-output
  downgrade and the JSON repair call; the judge; streaming; any auth (a later change adds a Bearer path whose
  credential is an environment-variable *name* read at call time, go_text style; keychain references are never
  added, so they leave this list for good); reasoning control (`think`, `reasoning_effort`); context-window sizing
  (`num_ctx`, `/api/show`); `keep_alive`; effective-context budgeting and `maxTokens` (a cap starves reasoning
  models); chunking and context.
