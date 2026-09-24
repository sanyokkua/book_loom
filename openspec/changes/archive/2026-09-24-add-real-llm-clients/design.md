## Context

- **Code today.** `:api/ua.bookloom.api.llm` holds `ChatModel.chat(ChatRequest) -> Result<ChatResponse>`,
  `ChatModelFactory.create(ModelSelection(providerId, modelId))`, `ChatRequest(List<ChatMessage>)`,
  `ChatResponse(String content, FinishReason)`, `ChatRole` and `FinishReason {STOP, LENGTH, OTHER}`. `ErrorCode` already
  carries all fifteen codes, with `discoveryFailed` **retryable in code** (`ErrorCode.java:42`), and `SafeDetails`
  already has `withHttpStatus`, `withEndpoint`, `withModelName`, `withTimeout` and `withAttempt`. `:llm` is five files:
  `LlmModule`, `ChatModelFactoryImpl` (hard-coded `"pseudo"`) and `pseudo/PseudoChatModel`; its `module-info` already
  `requires java.net.http`. No Jackson on any main classpath. WireMock 3.13.2 sits on every module's test classpath
  (`bookloom.test-conventions.gradle.kts`) and is unused; the `liveLocal` and `promptEval` tasks exist and match no
  test. `:pipeline`'s `SegmentTranslator` (`SegmentTranslator.java:79-92`) is the only caller of `chat`: two string
  literals, one segment per call, a decision table (`validation`, `emptyCompletion`, `contextWindow` flag; other errors
  stop), no retry, no gate. `:app`'s `TranslateCommand` hard-codes `ModelSelection("pseudo", "uppercase")`.
- **Decisions already made.** ADR-0005 (two clients behind one provider port), ADR-0006 (credentials as a reference;
  nothing credential-shaped exists in this change, so it only fixes what a later Bearer path must look like),
  ADR-0008 (single-flight gate, service-owned retry), ADR-0013 (JSON-first tolerant response contract), ADR-0033 (the
  engine holds a bound `ChatModel`; a per-call setting is a nullable `ChatRequest` component). The sections below cite
  them instead of re-arguing them. No new ADR: nothing here is costly to reverse beyond what those already fix.
- **Live probes on this machine (2026-09-20, Ollama 0.34.2 at `:11434`, LM Studio at `:1234`)** shaped the wire
  section: Ollama honours `format:<schema>`, answers an unknown model with `404 {"error":"model 'x' not found"}`,
  rejects `think` for a model without the capability, and does **not** forward `think` through its `/v1` shim
  (ollama#15293), which is why the native client is mandatory. LM Studio honours `response_format: json_schema`,
  returns `reasoning_content` as a separate field, answers an **unknown model id with 200 from whichever model is
  loaded** (naming the real one in `model`), and **turns thinking on and empties `content` when sent
  `reasoning_effort:"low"`** to gemma4. Those two reasoning findings are the reason this change sends no reasoning
  parameter at all and strips the reply instead.
- **Reference.** The owner's go_text (`internal/llms`): one OpenAI-compatible client parameterised by a per-kind
  profile, an Ollama branch for `num_ctx`, nullable pointer params omitted from JSON, service-owned retry (500 ms to
  8 s, `Retry-After`, fresh per-attempt timeout), env-var credentials resolved at call time, three-stage verification,
  context-overflow sniffed from a 400 body. Pitfalls carried over: a low max-tokens cap starves reasoning models; a
  context window is not an output cap; a 404 on the models endpoint carries no model name.

## Goals / Non-Goals

**Goals:** real inference against Ollama and any OpenAI-compatible server behind the unchanged `ChatModel`; typed
errors, timeouts, plain retry, the gate; connection and model verification before a run; structured output
requested on every draft call; a minimal reasoning strip; the catalog draft prompt for one segment;
a command line that picks provider and model.

**Non-Goals:** provider editing, switching and persistence (`:persistence` backs the same port later); per-project
binding (ADR-0012); the structured-output downgrade and the JSON repair call (`04_LLM_INTEGRATION.md#response-handling`
steps 1 and 5); the judge; streaming; any auth: a later change adds a Bearer path whose credential is an
environment-variable *name* read with `getenv` at call time and never stored (go_text `service.go:319-341` is the
reference), and keychain references are never added; reasoning control (`think`, `reasoning_effort`); context-window
sizing (`num_ctx`, `/api/show`); `keep_alive`; effective-context budgeting, `maxTokens`, `topP`; `tryRun`/`busy` on
the gate (arrives with the first interactive screen); token usage.

## Decisions

Packages follow `docs/implementation_plan/01_MODULE_INVENTORY.md#module-api`, `#module-llm`, `#module-pipeline` and
`#module-app`.

### D1: contracts in `:api` (ADR-0033, ADR-0005)

```java
// ua.bookloom.api.llm — all records/interfaces/enums, framework-free (ArchUnit api-is-framework-free, records-first)
enum ProviderKind { OLLAMA, OPENAI_COMPATIBLE }
record ProviderConfig(String id, ProviderKind kind, URI baseUrl, Duration connectTimeout, Duration requestTimeout) {
    // copy-with methods the CLI uses: withBaseUrl, withRequestTimeout.
    // Constants DEFAULT_CONNECT_TIMEOUT = 10 s, DEFAULT_REQUEST_TIMEOUT = 180 s.
    // No credential, auth scheme, context-window or reasoning component: see Non-Goals.
}
interface ProviderConfigs {                       // port; :llm holds it in memory, :persistence backs it later
    Result<ProviderConfig> register(ProviderConfig config);   // ErrorCode.validation on a bad description
    Optional<ProviderConfig> find(String id);
    List<ProviderConfig> all();
}
record ModelInfo(String id) {}                    // fields such as a context length append later, never replace
record ResponseFormat(String name, String jsonSchema) {}
record ChatRequest(List<ChatMessage> messages, @Nullable Double temperature, @Nullable ResponseFormat responseFormat) {
    public ChatRequest(List<ChatMessage> messages) { this(messages, null, null); }   // every call site keeps compiling
}
enum VerificationStage { CONNECTION, MODELS, INFERENCE }
enum StageStatus { PASSED, SOFT_PASS, FAILED, SKIPPED }
enum VerificationPolicy { FULL, PREFLIGHT }       // PREFLIGHT: connection, models; inference only after a SOFT_PASS
record StageOutcome(VerificationStage stage, StageStatus status, @Nullable AppError error, @Nullable String note) {}
record VerificationReport(List<StageOutcome> stages) { boolean isPassed() { /* no FAILED */ } }
interface ProviderVerifier { Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy); }
```

- **Validation split.** Compact constructors reject `null` and copy lists; the semantic rules of `llm-provider`
  "Describe a provider…" (absolute `http`/`https` URL, positive timeouts) are checked by `ProviderConfigs.register`
  and answered as `ErrorCode.validation`, so a record can still be built in a test with a deliberately bad value.
- **`ModelSelection` stays** `(providerId, modelId)`. The provider id is the registry key; `pseudo` never enters the
  registry and is checked first by the factory.
### D2: `:llm` layout (ADR-0005)

| Package (all non-exported except `ua.bookloom.llm`) | Holds |
|---|---|
| `ua.bookloom.llm` | `LlmModule`, `ChatModelFactoryImpl`, `InMemoryProviderConfigs`, `GatedChatModel` |
| `ua.bookloom.llm.provider` | `ProviderClient` (internal per-dialect interface: `probe()`, `listModels()`, `chat(modelId, ChatRequest)`, `kind()`), `ProviderClientFactory` (switch on `ProviderConfig.kind()`) |
| `ua.bookloom.llm.client.ollama` | `OllamaClient` |
| `ua.bookloom.llm.client.openai` | `OpenAiCompatibleClient` |
| `ua.bookloom.llm.http` | `HttpExchange` (one JSON `GET`/`POST`, per-request timeout, no auth header, returns `HttpReply(status, headers, body)` or a mapped transport error), `HttpErrorMapper` (the matrix below), `HttpClients` (one `java.net.http.HttpClient` per connect timeout, cached) |
| `ua.bookloom.llm.dto` | Jackson records, `@JsonInclude(NON_NULL)`, `opens … to com.fasterxml.jackson.databind`: `OllamaChatRequest`/`Options` (temperature only), `OllamaChatResponse`/`Message`, `OllamaTagsResponse`, `OllamaErrorBody`, `OpenAiChatRequest`/`ResponseFormatDto`, `OpenAiChatResponse`/`Choice`/`Message`, `OpenAiModelsResponse`, `OpenAiErrorBody` |
| `ua.bookloom.llm.response` | `ReplySanitizer` |
| `ua.bookloom.llm.retry` | `RetryPolicy` (attempts 3, base 500 ms, cap 8 s, jitter 25 %, `Retry-After` seconds or HTTP-date; sleeper and random injected) |
| `ua.bookloom.llm.gate` | `InferenceGate` (`Semaphore(1, true)`, blocking `<T> Result<T> run(Supplier<Result<T>>)`, released in `finally`) |
| `ua.bookloom.llm.verify` | `ProviderVerifierImpl` |
| `ua.bookloom.llm.pseudo` | unchanged |

- `LlmModule` binds `ChatModelFactory`, `ProviderConfigs` (singleton, seeded with the two presets), `ProviderVerifier`,
  `InferenceGate` (singleton), and `@Provides` a tolerant `ObjectMapper` (`FAIL_ON_UNKNOWN_PROPERTIES=false`) and the
  `HttpClients` holder. `ProviderClientFactory` caches one client per config id, so a run reuses one client per
  provider; the clients hold no per-model state.
- **`ChatModelFactoryImpl.create`:** `pseudo` → `PseudoChatModel`; blank model id → `validation`; unknown id →
  `validation`; else `GatedChatModel(client, modelId, gate, retryPolicy)`. Creating a model sends nothing.
- **`GatedChatModel.chat`:** for attempt 1..3: `gate.run(() -> client.chat(modelId, request))`; on a retryable error
  with attempts left, sleep the policy's delay **outside** the gate, then retry; else return. This is the whole
  retry-and-gate interplay of `04_LLM_INTEGRATION.md#service-owned-retry`; there is no repair call to interleave.
- **Why an internal `ProviderClient` and not the spec's public `Provider` port:** the callers (`:pipeline`, `:app`)
  already have their ports (`ChatModelFactory`, `ProviderConfigs`, `ProviderVerifier`); a public per-dialect port would
  be a second way to reach a model, which ADR-0033 rules out. The spec's `Provider` paragraph is reworded accordingly
  (task 12).

### D3: wire shapes, from the live probes

**Ollama-native** (`baseUrl` = `http://localhost:11434`):

| Call | Request | Reply fields used |
|---|---|---|
| probe | `GET /api/version` | status only |
| list | `GET /api/tags` | `models[].name` |
| chat | `POST /api/chat` with model, messages, stream false, optional temperature/schema, and optional `think:false`; nullable fields omitted; never `num_ctx` or `keep_alive` | `message.content`, `done_reason` (`stop`→STOP, `length`→LENGTH, `load`/`unload`/other→OTHER), `model` |

- **Later:** change 12 budgets the context window; it can then send `options.num_ctx` and read
  `model_info["<arch>.context_length"]` from `/api/show` (`04_LLM_INTEGRATION.md#effective-context`), both a few
  lines in this client. Nothing here prepares for it beyond the `Options` record.

**OpenAI-compatible** (`baseUrl` = `http://localhost:1234/v1`, or any other OpenAI-shaped URL such as
`http://localhost:8080/v1`):

| Call | Request | Reply fields used |
|---|---|---|
| probe | `GET /models` | status only |
| list | `GET /models` | `data[].id` |
| chat | `POST /chat/completions` `{"model","messages","stream":false,"temperature":0.2,"response_format":{"type":"json_schema","json_schema":{"name","strict":true,"schema"}}}`; never `reasoning_effort` | `choices[0].message.content`, `choices[0].finish_reason` (`stop`/`length`/other), `model` |

No auth header is sent in this change. Every request sends `Content-Type: application/json` and
`Accept: application/json`, and sets `HttpRequest.timeout(requestTimeout)` afresh per attempt.

### D4: HTTP and transport → `ErrorCode` (`llm-provider` "Map every transport and HTTP outcome…")

Decided in `HttpErrorMapper`, first match wins: `cancelled` (`InterruptedException`, interrupt flag restored) →
`timeout` (`HttpTimeoutException`, `HttpConnectTimeoutException`)
→ `unreachable` (`ConnectException`, `UnresolvedAddressException`, any other `IOException`) → `auth` (401, 403) →
`modelNotFound` (404 on chat; any status whose Ollama error body matches `model '.*' not found`) → `rateLimited`
(429, `Retry-After` parsed into the policy) → `upstream` (500–599) → `contextWindow` (400 whose body matches
`context_length_exceeded|n_ctx|context\b.*\b(exceed|too long|greater than)`, case-insensitive) → `validation` (other
400) → `internal` (other status, or a 200 body Jackson cannot read). `details` is `SafeDetails` only:
`withHttpStatus`, `withEndpoint(baseUrl)` (host only), `withModelName`, `withTimeout`, `withAttempt`. The
probe treats 401/403 and 5xx as failures and every other status as reachable.

### D5: sanitiser (ADR-0013)

`ReplySanitizer.clean(String)`: (1) if blank → the client returns `emptyCompletion` **before** calling the sanitiser;
(2) remove `(?is)<(think|thinking|reasoning)>.*?</\1>` and then an unterminated `(?is)<(think|thinking|reasoning)>.*$`;
(3) if the trimmed text starts with three backticks, drop the first line and a trailing fence line; (4) remove a
case-insensitive leading `json` label only when it is immediately followed by an object opener; (5) trim. A
`thinking` / `reasoning_content` field is never read. The result may be empty and is returned as a normal reply: the
engine's own "empty or whitespace" rule then flags the segment `emptyCompletion`
(`04_LLM_INTEGRATION.md#empty-response-ordering`); invalid structured output is handled by D7's one repair call.

### D6: verification and preflight

**Corrective structured-output behavior.** Preflight now always runs inference, including for a listed model. Its
small schema probe asks for one status object while reasoning is disabled. A valid probe reports structured output
supported. An explicit format rejection or a nonconforming structured answer is followed by one plain probe; a
nonblank plain answer reports structured output not confirmed and does not block the job. Capability downgrade retries
are immediate, do not consume the transport retry budget, and never log a server response body.

`ProviderVerifierImpl.verify(selection, policy)`: unknown provider → `Result.err(validation)`; else stages in order,
each appended to the report, stop at the first `FAILED`:

1. `CONNECTION`: `client.probe()`.
2. `MODELS`: `client.listModels()` (retried by policy); `discoveryFailed` or an empty list → `SOFT_PASS` with the note
   `model list unavailable`; id absent from a non-empty list → `FAILED` with `modelUnavailable`; present → `PASSED`.
3. `INFERENCE`: under both `FULL` and `PREFLIGHT`, send a schema-constrained {"status":"ok"} probe with
   reasoning disabled. A valid envelope passes with structured output supported; an explicit format rejection or
   nonconforming structured reply receives one plain probe and, if nonblank, passes with
   structured output not confirmed. `modelNotFound` → `FAILED` `modelUnavailable`; other error → `FAILED`.

Verification calls go through the same gate and retry as inference. The command line prints one line per outcome:
`connection: ok`, `models: ok (model list unavailable)`, `inference: ok (structured output: supported)`,
`models: failed - <title>`.

### D7: prompt, schema and parser in `:pipeline/ua.bookloom.pipeline.prompt`

**Corrective reply classification.** The parser reads the entire reply strictly as exactly one JSON object with one
nonblank string field, `target`; no embedded objects, maps, arrays, extra fields, or text fallback are accepted. The
translator makes one fresh schema-constrained structural repair containing the delimited rejected reply and parsing
diagnosis. A target that fails the document placeholder gate receives one distinct repair containing the original
source, rejected target, and exact ordered token sequence. Every repair is strictly parsed and re-gated; neither repair
recurses. Draft and repair requests disable reasoning where the provider supports it.

`DraftPromptBuilder(sourceLang, targetLang)` renders `01_Product/12_PROMPT_CATALOG.md#draft-translation` for one
segment. The raw BCP-47 language tags remain the request and job values, but model-facing `{{sourceLang}}` and
`{{targetLang}}` use the English `Locale` display name followed by the exact tag (for example, `English (en)` and
`Ukrainian (uk)`). An unregistered tag renders as `language tag "<tag>"`. `{{sourceLang}}` comes from the request,
else the book, else `the language of this segment (infer it from its text)`;
`{{styleSheet}}` = `Neutral, faithful literary prose: keep the author's register, sentence rhythm and paragraph breaks;
use the standard modern orthography of the target language.`; `{{foreignPassageRule}}` = the catalog default;
`DraftContext` currently owns only an optional, section-local window of the last three accepted targets; absent context
blocks are omitted. Summary, glossary, TM and retry-note fields remain absent until producers exist. The raw masked
source appears verbatim inside `<Text>…</Text>` and the user message repeats its exact ordered placeholder sequence.
The system has language-neutral structural few-shots; it explicitly says these demonstrate token placement only.
The closing instruction is `Return exactly one JSON object: {"target":"<translation>"}`.

`DraftSchema.SCHEMA` is the text block
`{"type":"object","properties":{"target":{"type":"string"}},"required":["target"],"additionalProperties":false}`,
sent as `ResponseFormat("draft_translation", SCHEMA)`; temperature `0.2` (`DraftPromptBuilder.TEMPERATURE`).

`DraftReplyParser.parse(replyText)` accepts only that exact strict object shape. `SegmentTranslator` repairs malformed
or wrong-shape output once with the raw rejected reply and parsing diagnosis, then flags a second failure before
unmasking. A valid target reaches unmasking only after strict parsing; a failed placeholder or Markdown-restoration
validation receives one formatting-repair request containing the original source, rejected target, exact ordered token
sequence, and the rule that paired tokens wrap nonblank translated text and task-list tokens remain at the item start.
The repair is re-gated once and then flagged if it remains invalid.

**Jackson in `:pipeline` too.** The plan brought Jackson into `:llm` only, but the reply parser is pipeline-owned
(`CHANGE_BACKLOG.md`, `add-prompt-catalog-and-output-contract` covers `ua.bookloom.pipeline.prompt` and the output
contract) and a hand-written JSON reader would be worse than the dependency. `:pipeline` therefore also declares
`implementation(libs.jackson.databind)` and `requires com.fasterxml.jackson.databind`, used by the parser alone;
`:api` stays free of it (`api-is-framework-free`), and `dependency-direction` is unaffected (a library, not a module).

### D8: command line (`01_MODULE_INVENTORY.md#module-app`)

`TranslateCommand` is at 329 lines; the flag parsing moves to a package-private `TranslateArguments` record with a
static `parse(List<String>) -> Result<TranslateArguments>` (usage errors as `validation`), keeping both files under
400 lines. `run`:

1. parse; exit 2 with the reason and usage on `validation`;
2. `pseudo` → `ModelSelection("pseudo","uppercase")` and no preflight (unchanged path);
3. else build the `ProviderConfig`: preset from `ProviderConfigs.find` for `ollama`/`lmstudio`, or
   `ProviderConfig("openai-compatible", OPENAI_COMPATIBLE, --base-url, …)`; apply `--base-url` and `--timeout`;
   `ProviderConfigs.register` (a `validation` answer is exit 2);
4. `ProviderVerifier.verify(selection, PREFLIGHT)`; print one line per stage; a `FAILED` stage prints the error's
   title on the stage line and its message on the next, exit 1;
5. `ChatModelFactory.create`, then the job as today.

Usage: `translate <book> [--to <lang>] [--from <lang>] [--overwrite] [--provider pseudo|ollama|lmstudio|openai-compatible]
[--model <id>] [--base-url <url>] [--timeout <seconds>]`. `TranslateArguments` holds exactly `--provider`, `--model`,
`--base-url` and `--timeout` besides the existing flags. `TranslateCommand` gains `ProviderConfigs` and
`ProviderVerifier` as constructor dependencies; `TranslateLauncher` and the Gradle `translate` task do not change.

### D9: dependencies and build

- `gradle/libs.versions.toml`: `jackson = "2.20.x"` (the latest 2.20 patch at implementation time) and
  `jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }`
  (Apache-2.0; brings `jackson-core` and `jackson-annotations`). `:llm` and `:pipeline` add
  `implementation(libs.jackson.databind)`; both lockfiles regenerated with `./gradlew resolveAndLockAll --write-locks`
  and checked with `./gradlew -PstrictLocks verifyLocks`.
- `:llm` `module-info`: `requires com.fasterxml.jackson.databind;` and `opens ua.bookloom.llm.dto to
  com.fasterxml.jackson.databind;`. `:pipeline` `module-info`: `requires com.fasterxml.jackson.databind;` (the parser
  reads a `JsonNode`; no DTO to open).
- The `records-first` rule covers `..dto..` automatically: every DTO is a record. Jackson 2.12+ maps records by
  their canonical constructor; `@JsonProperty("response_format")` and friends name the wire fields.

### D10: logging (`.claude/rules/logging.md`)

| Level | Lines |
|---|---|
| INFO | a verification's start (provider id, kind, host, model, policy) and end (each stage and outcome); the command's chosen provider and model |
| WARN | every retry (attempt, code, delay), a `model` mismatch (requested vs answered), a failed or soft-passed stage, a refused registration |
| DEBUG | every client method's entry: provider id, kind, endpoint host, model id, message count, temperature, `responseFormatPresent`, attempt; every branch: the mapper's chosen code and the row that chose it, the sanitiser's removals (kind and count), the gate's acquire/release, the parser's chosen shape, each preflight decision; every response: status, body length, finish |
| TRACE | request and response bodies, the rendered system and user messages, the sanitised text, the parsed translation |

Never, at any level: a credential or an auth header value, once a later change adds one. `HttpExchange` logs headers
as names only. `SafeDetails` is the only source of `AppError.details`.

## Risks / Trade-offs

- **LM Studio answers an unknown model with 200.** → The preflight's models stage is the guard; the WARN on a `model`
  mismatch is the second line of defence. Documented in `docs/DEVELOPMENT.md`.
- **Strict `json_schema` on servers that do not support it** (older llama.cpp builds) answers 400 → `validation`,
  which stops the job. → The silent downgrade is a later change; the workaround today is `--provider ollama` or a
  server that supports it. Named in `docs/next_features.md` by task 12.
- **A cold model load takes seconds and may exceed a short `--timeout`.** → The default is 180 s and the preflight
  skips inference for a listed model; `timeout` is retryable, so a load that finishes during the retry succeeds.
- **Ollama's default context window (4,096 tokens) may silently truncate a long paragraph plus the ~400-token prompt**,
  giving a wrong translation with no error, because this change sends no `num_ctx`. → Accepted by the owner: a
  segment is one paragraph and normally fits; change 12 budgets the window and can send `num_ctx` then.
- **A thinking model reasons at length on every paragraph**, because `think` is never sent. → The separate reasoning
  channel is ignored and inline reasoning text is stripped, so the translation is still read; the cost is time, not
  correctness. Reasoning control is a later change.
- **The gate is process-wide, so verification waits behind a running job.** → Only the command line exists, and it
  runs one job; `tryRun`/`busy` come with the first screen.
- **Jitter and sleeps in tests.** → `RetryPolicy` takes an injected sleeper and random; WireMock suites assert request
  counts and order, never wall-clock delays, except one bounded `Retry-After` test with a fake clock.

## Test strategy

- **WireMock at the HTTP seam, both dialects, in `check`** (`.claude/rules/testing.md`): `OllamaClientTest`
  (`/api/version`, `/api/tags`, `/api/chat`: body shape with and without a temperature and `format`, no `think`,
  `num_ctx` or `/api/show` ever, omitted nulls, reply parsing, `done_reason` mapping), `OpenAiCompatibleClientTest`
  (`/v1/models`, `/v1/chat/completions`: `response_format`, never `reasoning_effort`, `reasoning_content` ignored,
  model mismatch WARN), `HttpErrorMapperTest` (the whole matrix as a `@ParameterizedTest` over status and body),
  `ReplySanitizerTest`, `RetryPolicyTest` and `GatedChatModelTest` (attempt counts,
  `Retry-After`, gate released while sleeping, two concurrent calls never overlap at the server), `InferenceGateTest`,
  `ProviderVerifierImplTest` (all report shapes), `InMemoryProviderConfigsTest`, `ChatModelFactoryImplTest` (extended).
- **`:pipeline`:** `DraftPromptBuilderTest`, `DraftReplyParserTest` (every shape in the requirement),
  `SegmentTranslatorTest` and `TranslationEngineEndToEndTest` updated so `ScriptedChatModel` answers JSON, plus one
  plain-text and one map-shaped reply.
- **`:app`:** `TranslateCommandTest` with a recording `ProviderConfigs`/`ProviderVerifier` fake for the flag matrix and
  the stage lines; `TranslateLauncherTest` unchanged.
- **`liveLocal` (env-gated, never in `check`):** `OllamaLiveTest` and `LmStudioLiveTest` in `:llm`
  (`@EnabledIfEnvironmentVariable` on `BOOKLOOM_LIVE_OLLAMA_URL` / `BOOKLOOM_LIVE_LMSTUDIO_URL`, models from
  `BOOKLOOM_LIVE_OLLAMA_MODEL`, default `gemma4:e4b-mlx`, and `BOOKLOOM_LIVE_LMSTUDIO_MODEL`, default
  `google/gemma-4-e4b`): probe, list, one JSON-schema chat whose reply parses, one call with `⟦g0⟧…⟦g1⟧` whose tokens
  survive; and `TranslateCommandLiveTest` in `:app`: a three-paragraph Markdown book through the command with each
  provider, asserting every `⟦gN⟧` survived (the output re-opens and every segment is ACCEPTED or FLAGGED with a
  named reason, none `validation` for a token loss).
- **Equivalent-route comparison (local evidence only):** `TranslateCommandLiveTest` also accepts
  `BOOKLOOM_LIVE_OLLAMA_OPENAI_URL` and `BOOKLOOM_LIVE_OLLAMA_OPENAI_MODEL`, exercising the custom
  `openai-compatible` CLI path against Ollama's `/v1` shim. It uses the same explicit `--from en --to uk`,
  temperature, timeout, small Markdown source, and output/reopen assertions as the native and LM Studio cases.
  A live evaluation loads one serving model at a time, retains its logs and copied output outside the repository,
  and treats native Ollama, the Ollama shim, and LM Studio as separate configurations even when the model artifact
  is nominally the same. The comparison is evidence for compatibility, not a product routing change.
