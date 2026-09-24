Every task is self-contained: its **Read** list names what it needs, so it can be planned and applied without any other
context. These rules apply to every task:

- **Git.** Work on `feature/add-real-llm-clients--<task>` (for example `--1.1-api-contracts`), commit there,
  squash-merge one commit into `feature/add-real-llm-clients`, and delete the task branch (`AGENTS.md#git`).
- **Tests first.** Write the task's tests, run them and watch them fail, then implement
  (`AGENTS.md#definition-of-done`). Name tests `method_state_expected`, put no `if`, `for` or `while` in a test body
  (use `@ParameterizedTest`), create files in `@TempDir`, and write expected values out instead of recomputing them
  (`.claude/rules/testing.md`). The LLM seam is tested with **WireMock at the HTTP wire for both dialects**, never by
  faking a client class.
- **Logging is part of done.** Write what the task's **Log** line lists, at the levels in design.md D10: INFO for
  lifecycle, DEBUG for every working method's parameters and every branch taken, TRACE for bodies and book text, WARN
  for retried, degraded or refused outcomes, and **never a credential or an auth header value**
  (`.claude/rules/logging.md`). Read the task's log output once at TRACE before calling it done.
- **Code rules.** `.claude/rules/java-coding-style.md`: records for data, `Objects.requireNonNull` at boundaries, a
  `@NullMarked` `package-info.java` with a purpose Javadoc for each new package, sealed types switched exhaustively,
  no `synchronized`, methods of at most 30 lines and files of at most 400. `.claude/rules/error-envelope.md`: every
  port method returns a `Result` behind a boundary `try/catch`; `details` only from `SafeDetails`.
  `.claude/rules/offline-and-privacy.md`: no call that is not user-triggered provider communication.
- **Finish** with `./gradlew spotlessApply`, then the task's **Done when** commands.
- **Sources.** Requirements are named in quotes and live in this change's `specs/<capability>/spec.md`. Decisions are
  in `design.md` (D1–D10); the governing records are ADR-0005, ADR-0008, ADR-0013 and ADR-0033.

## 1. Contracts

- [x] 1.1 Add the provider, verification and response-format contracts to `:api`, and the two nullable `ChatRequest` components with the old constructor kept. Every other module compiles against these types, so they come first, and no other module changes in this task. → `:api`
  - **Read:** design.md D1; `specs/llm-provider/spec.md` "Describe a provider by kind, endpoint and timeouts";
    `specs/inference/spec.md` "Carry a temperature and a response format per call"; ADR-0033;
    `modules/api/src/main/java/ua/bookloom/api/llm/{ChatRequest,ModelSelection,ChatModelFactory}.java`;
    `modules/api/src/test/java/ua/bookloom/api/llm/ChatContractsTest.java`.
  - **Change:** in `ua.bookloom.api.llm`: `ProviderKind`, `ProviderConfig(id, kind, baseUrl, connectTimeout,
    requestTimeout)` with its defaults and the copy-with methods `withBaseUrl` and `withRequestTimeout`, the
    `ProviderConfigs` port, `ModelInfo`, `ResponseFormat`, `VerificationStage`,
    `StageStatus`, `VerificationPolicy`, `StageOutcome`, `VerificationReport.isPassed()` and the `ProviderVerifier`
    port; `ChatRequest(messages, temperature, responseFormat)` plus `ChatRequest(messages)`.
  - **Test first:** `ChatContractsTest`: a request built from messages alone has a null temperature and format;
    `ProviderConfigTest`: `withRequestTimeout` changes only that component, `withBaseUrl` changes only that
    component, a null `id` is rejected; `VerificationReportTest`: a report with one `FAILED`
    stage is not passed, a report of `PASSED` and `SOFT_PASS` is.
  - **Log:** none; `:api` holds contracts only.
  - **Done when:** `./gradlew :api:build :app:archTest` is green (`api-is-framework-free`, `records-first`).

## 2. Jackson, DTOs and module wiring

- [x] 2.1 Bring `jackson-databind` into the version catalog and into `:llm` and `:pipeline`, add the Jackson record DTOs for both dialects in a non-exported `ua.bookloom.llm.dto`, and provide the tolerant `ObjectMapper` from `LlmModule`. Without a JSON library nothing below can shape a request or read a reply, and the DTO package is where `records-first` and `@JsonInclude(NON_NULL)` make "nullable params are omitted" a compile-time fact. → `:llm`, `:pipeline`, build
  - **Read:** design.md D2 (the `dto` row), D3, D9; `gradle/libs.versions.toml`; `modules/llm/build.gradle.kts`;
    `modules/llm/src/main/java/module-info.java`; `modules/pipeline/build.gradle.kts`; `AGENTS.md#what-will-bite-you`
    (a catalog pin nothing references does nothing; lockfiles).
  - **Change:** catalog `jackson` version and `jackson-databind` library; `implementation(libs.jackson.databind)` in
    both modules; `requires com.fasterxml.jackson.databind` in both `module-info` files and `opens ua.bookloom.llm.dto
    to com.fasterxml.jackson.databind` in `:llm`; the DTO records of D2 with `@JsonProperty` wire names; a
    `@Provides @Singleton ObjectMapper` with `FAIL_ON_UNKNOWN_PROPERTIES` off; `./gradlew resolveAndLockAll
    --write-locks`.
  - **Test first:** `ua.bookloom.llm.dto.DtoSerializationTest`: an `OllamaChatRequest` with a null temperature and
    null format serialises to a body with no `options`, no `format` and no `think` key; one with temperature `0.2`
    serialises to `"options":{"temperature":0.2}`; an
    `OpenAiChatResponse` body carrying `reasoning_content`, `tool_calls` and `usage` deserialises with the content
    and finish reason intact; `{"data":"nope"}` fails to read as `OpenAiModelsResponse`.
  - **Log:** none; DTOs carry data.
  - **Done when:** `./gradlew :llm:build :pipeline:build -PstrictLocks verifyLocks :app:archTest` is green and both
    lockfiles show the three Jackson artifacts.

## 3. HTTP exchange and the error matrix

- [x] 3.1 Add `HttpExchange`, `HttpClients` and `HttpErrorMapper` in `ua.bookloom.llm.http`, so one place sends a JSON request with a fresh per-request timeout and turns every transport and HTTP outcome into exactly one `ErrorCode`. Both clients depend on this; the matrix is the contract the engine and the retry policy branch on. → `:llm`
  - **Read:** `specs/llm-provider/spec.md` "Map every transport and HTTP outcome to one error code"; design.md D3,
    D4, D10; `modules/api/src/main/java/ua/bookloom/api/{SafeDetails,AppError,ErrorCode}.java`;
    `.claude/rules/error-envelope.md`.
  - **Change:** `HttpClients.forConnectTimeout(Duration)` caching one `HttpClient` per timeout; `HttpExchange.get`
    / `post(config, path, body)` returning `Result<HttpReply(status, headers, body)>` with `Content-Type`,
    `Accept`, no auth header, and `HttpRequest.timeout(requestTimeout)`; `HttpErrorMapper.map(...)` implementing
    D4 with `SafeDetails` only.
  - **Test first (WireMock):** `HttpExchangeTest`: a request carries `Content-Type: application/json` and
    `Accept: application/json` and no `Authorization` header; a 3-second delay against a 1-second timeout answers
    `timeout` with the details naming the timeout; a closed port answers `unreachable`. `HttpErrorMapperTest`: a
    `@ParameterizedTest` over (status, body, expected code) covering every row of the requirement, including the
    three context-window phrasings and `404 {"error":"model 'nope:latest' not found"}`; an error's `details` never
    contains the body.
  - **Log:** DEBUG on every exchange (method, host, path, header names, timeout, body length) and on the mapper's
    chosen row; TRACE the request and response bodies.
  - **Done when:** `./gradlew :llm:test --tests 'ua.bookloom.llm.http.*'` is green.

## 4. Ollama-native client

- [x] 4.1 Add `OllamaClient` in `ua.bookloom.llm.client.ollama` behind the internal `ProviderClient` interface (`ua.bookloom.llm.provider`): probe on `/api/version`, list from `/api/tags`, chat on `/api/chat` with `stream:false`, `options.temperature` and `format` only when given, and never `think`, `num_ctx`, `keep_alive` or a `/api/show` call. Ollama's OpenAI shim drops the native controls a later change needs and hides Ollama's own error bodies, so the native dialect is the reliable path to a local Ollama model. → `:llm`
  - **Read:** `specs/llm-provider/spec.md` "Shape an Ollama-native chat request", "Read a reply and report its
    finish", "Discover the models a provider offers", "Probe a provider's reachability…"; design.md D2, D3, D5 (the
    blank-before-sanitise rule belongs to the client), D10; `ua.bookloom.llm.dto` from task 2.1.
  - **Change:** `ProviderClient` (`probe()`, `listModels()`, `chat(modelId, ChatRequest)`, `kind()`);
    `OllamaClient` over `HttpExchange`, `HttpErrorMapper` and the `ObjectMapper`; `done_reason` mapping; the
    `model`-mismatch WARN; blank `message.content` → `emptyCompletion`.
  - **Test first:** first add and run an env-gated `liveLocal` acceptance test against the real Ollama endpoint before
    writing WireMock tests; prove probe, model discovery and one schema-constrained chat with the configured model.
    Then add `OllamaClientTest` against WireMock `/api/*`: the full-request scenario (`model`, `stream:false`,
    `"options":{"temperature":0.2}`, `format` object, no `think`, `num_ctx` or `keep_alive` key) asserted with
    WireMock's `equalToJson` on the recorded body; WireMock received no `/api/show` request across two chats; absent
    temperature and format leave no `options` and no `format` key; `done_reason` `stop`/`length`/`load` →
    STOP/LENGTH/`emptyCompletion` as a `@ParameterizedTest`; `message.thinking` ignored; `/api/tags` returns the two ids in order; `/api/version`
    200 passes the probe and 503 fails it as `upstream`; a `404 {"error":"model 'nope:latest' not found"}` chat answers
    `modelNotFound`; malformed and blank chat replies, an invalid local schema, model mismatch, and discovery error
    mapping are covered, including preservation of `auth`, `unreachable`, `timeout` and `cancelled`.
  - **Log:** DEBUG on every method's entry (provider id, host, model, message count, temperature, `formatSent`) and
    on every outcome (status, body length, finish, code); WARN on the model mismatch; TRACE bodies.
  - **Done when:** `./gradlew :llm:test --tests 'ua.bookloom.llm.client.ollama.*'` is green.

## 5. OpenAI-compatible client

- [x] 5.1 Add `OpenAiCompatibleClient` in `ua.bookloom.llm.client.openai`: probe and list on `/models`, chat on `/chat/completions` with `stream:false`, a temperature and a strict `json_schema` `response_format` only when given, and never a reasoning parameter. One client covers LM Studio, llama.cpp, vLLM and any other OpenAI-shaped server; a guessed reasoning parameter has been seen to empty a reply, so none is sent. → `:llm`
  - **Read:** `specs/llm-provider/spec.md` "Shape an OpenAI-compatible chat request", "Read a reply and report its
    finish", "Discover the models a provider offers", "Probe a provider's reachability…"; design.md D2, D3, D10;
    the Ollama client of task 4.1 as the pattern.
  - **Change:** `OpenAiCompatibleClient` over the same helpers; `finish_reason` mapping; `reasoning_content` and
    `tool_calls` ignored; the `model`-mismatch WARN; blank content → `emptyCompletion`; `ProviderClientFactory`
    switching exhaustively on `ProviderKind` and caching one client per config id.
  - **Test first (WireMock `/v1/*`):** `OpenAiCompatibleClientTest`: the structured-output scenario (`model`,
    `stream:false`, `temperature`, `response_format.json_schema.strict` true, no `reasoning_effort`) via
    `equalToJson`; absent settings leave no keys;
    `reasoning_content` ignored; the mismatch scenario (`google/gemma-4-e4b-typo` requested, `google/gemma-4-e4b`
    answered) still returns the reply and logs one WARN; `/v1/models` returns both ids; `{"data":"nope"}` answers
    `discoveryFailed`; a 404 on `/v1/models` passes the probe and a 401 fails it as `auth`; `<html>proxy error</html>`
    on chat answers `internal`. `ProviderClientFactoryTest`: each kind gets its client; the same config id gets the
    same instance.
  - **Log:** as task 4.1.
  - **Done when:** `./gradlew :llm:test --tests 'ua.bookloom.llm.client.openai.*' --tests 'ua.bookloom.llm.provider.*'` is green.

## 6. Sanitiser

- [x] 6.1 Add `ReplySanitizer` in `ua.bookloom.llm.response` and apply it in both clients after the blank check, so reasoning blocks and code fences never reach the engine. A model that narrates its thinking inline would otherwise put `<think>` text into a book. → `:llm`
  - **Read:** `specs/inference/spec.md` "Sanitise a reply before handing it back"; design.md D5;
    `04_LLM_INTEGRATION.md#empty-response-ordering`.
  - **Change:** `ReplySanitizer.clean(String)` per D5; both clients call it on a non-blank reply and return the
    result even when empty, finish unchanged.
  - **Test first:** `ReplySanitizerTest` as a `@ParameterizedTest`: each scenario of the requirement (inline block,
    `<THINKING>` unterminated, `<reasoning>` case-insensitive, fenced with `json`, fenced without a tag, only
    reasoning → empty), plus a reply with no wrapper returned trimmed; in `OllamaClientTest` and
    `OpenAiCompatibleClientTest`, one case each where a `<think>` reply comes back clean and one where an
    all-reasoning reply comes back empty with a normal finish, not `emptyCompletion`.
  - **Log:** DEBUG with the kind and count of removals and the lengths before and after; TRACE the cleaned text.
  - **Done when:** `./gradlew :llm:test --tests 'ua.bookloom.llm.response.*' --tests 'ua.bookloom.llm.client.*'` is green.

- [x] 6.2 Remove a bare case-insensitive `json` prefix immediately before a JSON object in `ReplySanitizer`, so a
  provider response such as `json{"segments":[...]}` reaches the draft parser as an object rather than leaking into
  Markdown through text fallback. → `:llm`
  - **Read:** `specs/inference/spec.md` "Sanitise a reply before handing it back"; design.md D5;
    `ReplySanitizer.java` and `ReplySanitizerTest.java`.
  - **Test first:** `ReplySanitizerTest` proves `json{"segments":[]}` becomes `{"segments":[]}`.
  - **Change:** apply the narrow prefix removal only when `json` is immediately followed by `{`; preserve ordinary
    text beginning with the word `json`.
  - **Log:** add the bare-label removal count to the existing DEBUG sanitizer summary; keep TRACE output credential-free.
  - **Done when:** `./gradlew :llm:test --tests 'ua.bookloom.llm.response.ReplySanitizerTest'` is green.

## 7. Retry, gate and the factory

- [x] 7.1 Add `RetryPolicy` (`ua.bookloom.llm.retry`), `InferenceGate` (`ua.bookloom.llm.gate`) and `GatedChatModel`, the in-memory `ProviderConfigs` seeded with the two presets, and make `ChatModelFactoryImpl` resolve a registered provider to a gated, retried client model while keeping `pseudo`. This is where a caller's `ChatModel` becomes real: one request at a time, three attempts on a retryable code, the gate free while a retry sleeps. → `:llm`
  - **Read:** `specs/llm-provider/spec.md` "Retry a retryable failure a bounded number of times", "Serialize every
    model call through one gate", "Offer the two local presets and accept registrations", "Describe a provider…";
    `specs/inference/spec.md` "Get a chat model by provider id and model id"; design.md D1 (validation split), D2
    (`GatedChatModel.chat`), D10; ADR-0008; `modules/llm/src/main/java/ua/bookloom/llm/{LlmModule,ChatModelFactoryImpl}.java`;
    `modules/llm/src/test/java/ua/bookloom/llm/ChatModelFactoryImplTest.java`.
  - **Change:** `RetryPolicy` (attempts 3, 500 ms base, 8 s cap, 25 % jitter, `Retry-After` as seconds or HTTP date;
    injected sleeper and random); `InferenceGate.run` on a fair `Semaphore(1)`; `GatedChatModel` per D2;
    `InMemoryProviderConfigs` (`ConcurrentHashMap`, the semantic checks of "Describe a provider…", the `ollama` and
    `lmstudio` presets); `LlmModule` binds `ProviderConfigs` and `InferenceGate` as singletons, `ProviderVerifier`
    (task 8.1 fills it) and `HttpClients`; `ChatModelFactoryImpl` takes `ProviderConfigs`, `ProviderClientFactory`,
    `InferenceGate` and `RetryPolicy` (`@RequiredArgsConstructor(onConstructor_ = {@Inject})`).
  - **Test first:** `RetryPolicyTest`: delays for attempts 1..3 with a fixed random are 500 ms, 1 s, 2 s (±25 %),
    the cap holds at 8 s, `Retry-After: 2` gives 2 s, an HTTP date gives the difference to the fake clock.
    `GatedChatModelTest` (WireMock): 503, 503, 200 → success with 3 requests; 503 ×3 → `upstream` with exactly 3
    requests; 401 → exactly 1 request; `429 Retry-After: 2` then 200 → the sleeper was asked for 2 s; two calls started
    together against a 1-second delayed stub never overlap (WireMock's request log, timestamps ordered
    end-before-start); call B's request arrives before A's second attempt while A sleeps. `InMemoryProviderConfigsTest`:
    the presets, the override, the unknown id, and each refusal of "Describe a provider…" (`localhost:11434`, a
    0-second timeout) as `validation`. `ChatModelFactoryImplTest`: `pseudo` still
    works; `ollama`/`gemma4:e4b-mlx` returns a model with no request sent and its first call posts to
    `/api/chat` with that model; `lmstudio` posts to `/v1/chat/completions`; `gemini` → `validation`; a blank model →
    `validation`.
  - **Log:** DEBUG on the gate's acquire/release with the queue length, on each attempt (number, code, delay) and on
    the factory's resolution branch; WARN on every retry and every refused registration; INFO none.
  - **Done when:** `./gradlew :llm:build` is green, including the 0.80 branch-coverage gate.

## 8. Discovery, verification and preflight

- [x] 8.1 Add `ProviderVerifierImpl` in `ua.bookloom.llm.verify` implementing the three stages with short-circuit, the models-stage membership check, the soft pass, and the `PREFLIGHT` policy that runs inference only after a soft pass. A typo in a model name must fail in a second, with the failing stage named, not after the first segment. → `:llm`
  - **Read:** `specs/llm-provider/spec.md` "Verify a provider and a model in three stages", "Run a preflight before a
    command-line job"; design.md D6, D10; `04_LLM_INTEGRATION.md#three-stage-verification`.
  - **Change:** `ProviderVerifierImpl.verify(selection, policy)` per D6, through the gate and retry; bound in
    `LlmModule`.
  - **Test first (WireMock, both dialects):** `ProviderVerifierImplTest`: all three pass for `ollama`; the model not
    in the list → `[PASSED, FAILED modelUnavailable]` and no `/api/chat` request; `{"data":"nope"}` on `/v1/models`
    then `OK` → `[PASSED, SOFT_PASS, PASSED]`; a probe answering `401` → `[FAILED auth]` and no further request; an
    empty list then `404 model not found` on chat → inference `FAILED modelUnavailable`; `PREFLIGHT` with
    a listed model → `[PASSED, PASSED, SKIPPED]`; `PREFLIGHT` with `/v1/models` 500 ×3 then `OK` → inference runs
    and passes; an unknown provider → `Result.err(validation)`.
  - **Log:** INFO at the verification's start and end with every stage and outcome; DEBUG each stage decision and
    its inputs; WARN each failed or soft-passed stage.
  - **Done when:** `./gradlew :llm:build` is green.

## 9. Draft prompt and reply parser

- [x] 9.1 Add `DraftPromptBuilder`, `DraftSchema` and `DraftReplyParser` in `ua.bookloom.pipeline.prompt`, and make `SegmentTranslator` build the catalog draft prompt for one segment at temperature 0.2 with the response format, then read the translation through the parser before its unchanged decision table. The two ad-hoc sentences become the real prompt, and a model's JSON, map or plain-text answer all yield the segment's translation. → `:pipeline`
  - **Read:** `specs/translation-pipeline/spec.md` "Send each pending segment to the model in document order",
    "Accept a translation whose markup restores", "Read the translation out of the reply"; design.md D7, D10;
    `01_Product/12_PROMPT_CATALOG.md#draft-translation`, `#output-contract`;
    `modules/pipeline/src/main/java/ua/bookloom/pipeline/SegmentTranslator.java`;
    `modules/pipeline/src/test/java/ua/bookloom/pipeline/{SegmentTranslatorTest,ScriptedChatModel,TranslationEngineEndToEndTest,DiagnosticsSegmentTranslatorTest}.java`.
  - **Change:** the three classes per D7 (`@NullMarked` package with a purpose Javadoc); `SegmentTranslator`
    takes a `DraftPromptBuilder` and a `DraftReplyParser` (constructed by `TranslationJobImpl` from the job's
    languages and the injected `ObjectMapper`), sets `temperature` 0.2 and `ResponseFormat("draft_translation",
    DraftSchema.SCHEMA)`, and feeds `translationFor(segment.id(), reply.content())` into the existing decision rows.
  - **Test first:** `DraftPromptBuilderTest`: the system message for `en`→`uk` contains `from English (en) into
    Ukrainian (uk)`, the `⟦gN⟧` rule and `Output ONLY the required JSON object`; with no source language it says
    `from the language of this segment (infer it from its text) into Ukrainian (uk)`; the user message contains
    `{"id":"Book.md:0","source":"He opened the ⟦g0⟧old⟦g1⟧ door."}` and `(none)`
    five times; a masked text with a quote and a line feed is JSON-escaped. `DraftReplyParserTest`: every scenario of
    "Read the translation out of the reply" as a `@ParameterizedTest`. `SegmentTranslatorTest`: the request carries
    `0.2` and the format; the JSON reply scenario is ACCEPTED with `Він відчинив *старі* двері.`; a map reply and a
    plain-text reply are ACCEPTED; `{"segments":[{"id":"Book.md:0"}]}` is FLAGGED `emptyCompletion`.
    `TranslationEngineEndToEndTest` and `DiagnosticsSegmentTranslatorTest`: `ScriptedChatModel` answers JSON and
    every existing assertion still holds.
  - **Log:** DEBUG on the builder's inputs (languages, segment id, masked length) and the parser's chosen shape
    (`segments`, `map`, `text`, `empty`); TRACE the rendered messages and the parsed translation.
  - **Done when:** `./gradlew :pipeline:build` is green, including the coverage gate.

- [x] 9.2 Render model-facing draft language directions as an English `Locale` display name plus the raw BCP-47 tag, so `en`→`uk` reaches the model as `English (en)`→`Ukrainian (uk)` while the job and CLI retain their raw tags. When no source language is available, tell the model to infer the language from the segment text; use a quoted-tag fallback for an unregistered tag. → `:pipeline`
  - **Read:** `specs/translation-pipeline/spec.md` "Send each pending segment to the model in document order";
    design.md D7; `01_Product/12_PROMPT_CATALOG.md#draft-translation`; `DraftPromptBuilder.java`.
  - **Test first:** `DraftPromptBuilderTest` proves known, missing, variant and unregistered language tags render
    deterministically in the system and user messages. Update `TranslateCommandLiveTest` so each env-gated real
    provider run requires Cyrillic Ukrainian output and rejects an unchanged known English source sentence while
    retaining its existing placeholder and reopen assertions.
  - **Change:** keep `TranslationJobRequest`, `TranslateArguments`, output suffixes and provider payloads on raw
    BCP-47 tags; change only `DraftPromptBuilder`'s model-facing descriptions using `Locale.ENGLISH`.
  - **Log:** DEBUG the rendered descriptions and TRACE the existing rendered messages; never log credentials.
  - **Done when:** `./gradlew :pipeline:test --tests 'ua.bookloom.pipeline.prompt.DraftPromptBuilderTest'` is green
    and each local provider's `liveLocal` run proves Ukrainian content.

- [x] 9.3 Carry nullable `reasoningEnabled` on `ChatRequest`, request top-level Ollama `"think":false` for the draft, repair and structured preflight calls, and retry once without it only when the native server explicitly rejects that capability. Keep the old one- and three-argument constructors, and leave OpenAI-compatible and pseudo requests unchanged. → `:api`, `:llm`, `:pipeline`
  - **Test first:** `ChatContractsTest`, DTO serialization and both client WireMock suites prove constructor compatibility, top-level `think:false` only for Ollama, omission when null, and no OpenAI-compatible reasoning field. `GatedChatModelTest` proves a recognized native thinking rejection sends exactly one downgraded request with no backoff, while an unrelated validation error is not downgraded.
  - **Change:** add the nullable generic hint and internal rejected-capability metadata; do not expose provider response bodies or add a public error-code branch.
  - **Done when:** `./gradlew :api:test :llm:test --tests 'ua.bookloom.llm.*' :pipeline:test --tests 'ua.bookloom.pipeline.SegmentTranslatorTest'` is green.

- [x] 9.4 Make `PREFLIGHT` run a schema-constrained inference probe for every selected real model, report whether structured output was confirmed, and silently retry the probe as plain text only after an explicit structured-output rejection. A supported probe is evidence, not a guarantee, and an unconfirmed but otherwise working model may run. → `:llm`, `:app`
  - **Test first:** provider verification WireMock tests cover both dialects, a valid envelope (`supported`), malformed content or a recognized format rejection followed by a plain completion (`not confirmed`), and a real inference failure. CLI tests prove the note is printed and `inference: skipped` no longer appears for a listed model.
  - **Done when:** `./gradlew :llm:test --tests 'ua.bookloom.llm.verify.ProviderVerifierImplTest' :app:test --tests 'ua.bookloom.app.cli.TranslateCommandTest'` is green.

- [x] 9.5 Make draft inference permanently single-segment, require only the strict `{"target":"…"}` reply object, and add bounded structural and placeholder repairs without a text or batch fallback. → `:pipeline`
  - **Test first:** `DraftPromptBuilderTest`, `DraftReplyParserTest`, `StructuredReplySegmentTranslatorTest`, and `JobProgressTrackerTest` prove language-neutral few-shots, exact token repetition, strict shape rejection, diagnostic repairs, no unmask before validation, and the last-three section-local target window.
  - **Change:** `DraftContext` owns preceding accepted targets; source IDs, source JSON, batch wording, and empty context blocks are omitted. A malformed reply receives one repair with its delimited rejected text and diagnosis; a placeholder mismatch receives one repair with original source, rejected target, and required ordered tokens.
  - **Done when:** `./gradlew :pipeline:test --tests 'ua.bookloom.pipeline.prompt.DraftReplyParserTest' --tests 'ua.bookloom.pipeline.SegmentTranslatorTest'` is green.

## 10. Command line

- [x] 10.1 Extend the translate command with the provider flags, the run-scoped registration, the preflight lines and the new usage and exit codes, moving flag parsing into a package-private `TranslateArguments`. A person can now run a real model from the command line and see, before any segment is sent, whether the server and the model are there; every existing invocation still runs the pseudo model. → `:app`
  - **Read:** `specs/translation-pipeline/spec.md` "Translate one book from the command line", "Report command-line
    failures with exit codes"; `specs/llm-provider/spec.md` "Run a preflight before a command-line job"; design.md
    D6 (the stage lines), D8, D10; `modules/app/src/main/java/ua/bookloom/app/cli/TranslateCommand.java`;
    `modules/app/src/test/java/ua/bookloom/app/cli/TranslateCommandTest.java`.
  - **Change:** `TranslateArguments.parse` with every rule of "Report command-line failures…"; `TranslateCommand`
    gains `ProviderConfigs` and `ProviderVerifier`, builds and registers the config per D8, prints `<stage>: ok`,
    `<stage>: ok (<note>)`, `<stage>: skipped` or `<stage>: failed - <title>` followed by the message, and exits 1 on
    a failed stage; the usage string per D8.
  - **Test first:** `TranslateArgumentsTest` as a `@ParameterizedTest` over each invalid combination (`--provider
    gemini`, `--provider ollama` without `--model`, `openai-compatible` without `--base-url`, `--timeout 0`,
    `--timeout x`, an unknown option such as `--num-ctx 8192`) and the message `--model is required for provider
    ollama`; `TranslateCommandTest` with a recording `ProviderConfigs` and a scripted `ProviderVerifier` fake: the
    pseudo path prints one line and calls no verifier; `--provider ollama --model gemma4:e4b-mlx` registers the
    preset unchanged, prints `connection: ok`, `models: ok`, `inference: skipped` and the report; `--base-url
    http://10.0.0.5:11434` registers that URL; `--timeout 30` registers a 30-second request timeout; `--provider
    openai-compatible --base-url http://localhost:8080/v1 --model qwen3` registers an OpenAI-compatible description
    at that URL; a `FAILED` connection prints `connection: failed - <title>` then the message and exits 1 with no
    file; a `FAILED` models stage exits 1.
  - **Log:** INFO the chosen provider, model and every flag that was set; DEBUG each parse branch and each
    preflight line; WARN a rejected argument.
  - **Done when:** `./gradlew :app:build` is green, and `./gradlew -q :app:translate --args="'<a Markdown book>'
    --provider ollama"` prints the usage error and exits 2.

## 11. Live checks against real local servers

- [x] 11.1 Add the env-gated `liveLocal` suites: `OllamaLiveTest` and `LmStudioLiveTest` in `:llm` (probe, list, one JSON-schema chat that parses, one `⟦g0⟧…⟦g1⟧` round trip) and `TranslateCommandLiveTest` in `:app` (a three-paragraph Markdown book through the command with each provider, every `⟦gN⟧` surviving). A WireMock stub proves the code matches the recorded shapes; only a real server proves the shapes are still true. → `:llm`, `:app`
  - **Read:** design.md "Test strategy"; `.claude/rules/testing.md#local-only-livelocal`;
    `docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#live-local`;
    `modules/build-logic/src/main/kotlin/bookloom.test-conventions.gradle.kts` (the `liveLocal` task and tag).
  - **Change:** the three `@Tag("liveLocal")` classes with `@EnabledIfEnvironmentVariable(named =
    "BOOKLOOM_LIVE_OLLAMA_URL" | "BOOKLOOM_LIVE_LMSTUDIO_URL", matches = ".+")`, models from
    `BOOKLOOM_LIVE_OLLAMA_MODEL` (default `gemma4:e4b-mlx`) and `BOOKLOOM_LIVE_LMSTUDIO_MODEL` (default
    `google/gemma-4-e4b`); the `:app` test builds its book in `@TempDir` and re-opens the output through
    `DocumentPort`, asserting every segment ACCEPTED or FLAGGED with a reason other than a token loss.
  - **Test first:** the suites are the tests; without the variables `./gradlew liveLocal` is green by skipping.
  - **Log:** the production lines; read one TRACE run of the `:app` test and confirm the bodies appear and no key does.
  - **Done when:** `./gradlew liveLocal` skips cleanly with nothing set, and
    `BOOKLOOM_LIVE_OLLAMA_URL=http://localhost:11434 BOOKLOOM_LIVE_LMSTUDIO_URL=http://localhost:1234 ./gradlew :llm:liveLocal :app:liveLocal`
    is green on this machine with the two models installed; `./gradlew check` still runs none of them.

- [x] 11.2 Compare equivalent local serving routes without changing the production preference: add an env-gated
  `TranslateCommandLiveTest` case for the custom `openai-compatible` CLI route at
  `BOOKLOOM_LIVE_OLLAMA_OPENAI_URL`, then retain a timestamped local-only matrix for native Ollama,
  Ollama's OpenAI shim, and LM Studio. Exercise Q4/Q8 Gemma variants and the specified DeepSeek comparison with
  `--from en --to uk`, the shipped temperature, an explicit timeout, TRACE logging, copied outputs, model-identity
  checks, and exactly one loaded inference model at a time. → `:app`, local acceptance evidence
  - **Read:** design.md "Test strategy"; `specs/llm-provider/spec.md` "Shape an Ollama-native chat request",
    "Shape an OpenAI-compatible chat request", and "Run a preflight before a command-line job";
    `TranslateCommandLiveTest.java`; `.temporary_context/Books_Examples/Speckit_In_Simple_Words.md`.
  - **Test first:** add the third env-gated live case before running it, with
    `BOOKLOOM_LIVE_OLLAMA_OPENAI_URL=http://localhost:11434/v1`; it must require Ukrainian/Cyrillic output,
    unchanged-source rejection, placeholder restoration, and a reopened three-segment Markdown document.
  - **Do:** run the three italic Markdown segments, the 93-line Markdown fixture, and each relevant env-gated live
    suite for: native `gemma4:e4b-mlx`, native `gemma4:e4b-mxfp8`, OpenAI-compatible Ollama Q4, OpenAI-compatible
    Ollama Q8, LM Studio `google/gemma-4-e4b`, and LM Studio
    `deepseek/deepseek-r1-0528-qwen3-8b`. Then issue one controlled Q4 segment through Ollama `/api/chat` with and
    without `think:false` and `/v1/chat/completions`, holding prompt, schema intent, temperature, and model fixed.
    Rank configurations by placeholder preservation, zero flags, successful structural reopen, then elapsed time.
    Translate the listed EPUB and FB2 fixtures only through configurations that qualify on the small control; do not
    invent a second candidate when none qualifies.
  - **Evidence:** keep console output, TRACE logs, copied sources/outputs, safe request-shape notes, timings and the
    result matrix under one `/private/tmp/bookloom-route-matrix.<timestamp>/` root. For a shared italic segment trace
    masked input, raw target text, parser/recovery/repair class, unmasking, and final decision; confirm no credential
    is logged. Inspect Markdown, EPUB and FB2 structure as specified in the acceptance plan.
  - **Evidence (2026-09-24):** all six routes completed the small control. Ollama's OpenAI-compatible Q4 shim was
    recommended (3/3 control, then 46/46 Markdown, 140/145 EPUB and 158/163 FB2 accepted); its Q8 variant was
    caveated (2/3); native Q4 (1/3), native Q8 (0/3), LM Studio Gemma (1/3) and LM Studio DeepSeek (0/3) were not
    suitable. No second route qualified for long-format testing. Evidence remains outside Git at
    `/private/tmp/bookloom-single-segment-matrix.TCLnnn`.
  - **Done when:** the local-only matrix classifies each run as recommended, compatible with caveat, or not suitable;
    a product prompt/provider/parser correction is made only if that matrix identifies its layer and its captured
    case first becomes an offline test. This task does not by itself change the native-Ollama preference.

## 12. Spec and document repairs

- [x] 12.1 Fix the clauses this change outgrows and record the new state where readers look for it. A spec that disagrees with the code, and a backlog that calls a shipped change pending, cost every later reader a wrong assumption. → docs
  - **Read:** `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#chat-contracts`,
    `#http-error-mapping`, `#client-construction`; `docs/specification/02_Architecture/09_ERROR_HANDLING.md#error-code`;
    `docs/implementation_plan/CHANGE_BACKLOG.md#where-this-stands` and the Stage C table;
    `docs/implementation_plan/01_MODULE_INVENTORY.md#module-api`, `#module-llm`, `#module-pipeline`, `#module-app`;
    `AGENTS.md` "Where it stands"; `docs/DEVELOPMENT.md#running`, `#testing`; `docs/next_features.md`.
  - **Change:** `04_LLM_INTEGRATION.md`: `discoveryFailed` retryable **yes** in the mapping table (matching
    `09_ERROR_HANDLING.md` and the code); the `Provider.chat(ChatRequest, RequestOptions)` block replaced by the built
    `ChatRequest(messages, temperature, responseFormat)` and the three `:api` ports, with the per-dialect client
    interface named as internal to `:llm`; `#chat-contracts` marks `temperature` and `responseFormat` as built.
    `CHANGE_BACKLOG.md`: `#where-this-stands` says `add-translation-engine-and-cli` is archived and this change is
    in progress; rows 10 and 11 marked consumed by `add-real-llm-clients` in reduced form. `AGENTS.md` "Where it
    stands": `:llm` real with both clients, next the UI. `docs/DEVELOPMENT.md`: running the command with `--provider
    ollama|lmstudio|openai-compatible`, the LM Studio unknown-model behaviour, and the four `BOOKLOOM_LIVE_*`
    variables in `#testing`. `docs/next_features.md`: the structured-output downgrade and JSON repair, `tryRun`/`busy`,
    auth (a Bearer path with an environment-variable name read at call time; keychain never), reasoning control
    (`think`, `reasoning_effort`), context-window sizing (`num_ctx`, `/api/show`, `keep_alive`), and that
    D7/D9/D15 are now reachable with a real model.
  - **Done when:** every cited anchor exists, and `grep -n "RequestOptions" docs/specification/` finds nothing.

## 13. Gate

- [x] 13.1 Update `docs/implementation_plan/01_MODULE_INVENTORY.md` with the new `:api` types, the `:llm` packages (`provider`, `client.ollama`, `client.openai`, `http`, `dto`, `response`, `retry`, `gate`, `verify`), `ua.bookloom.pipeline.prompt` and `TranslateArguments`, and the as-built status, so the module map matches the code. → docs
  - **Done when:** every row names a package or class that exists.
- [x] 13.2 Re-run `./gradlew clean build check spotlessCheck` and `./gradlew -PstrictLocks verifyLocks` after tasks 6.2, 9.2, 9.3, 9.4 and 9.5, getting both green across the whole project with no pre-existing-failure exemption, including the eight ArchUnit rules and the 0.80 branch-coverage gate on `:llm` and `:pipeline`. A change is not done while any check anywhere is red. → all modules
  - **Evidence (2026-09-24):** `./gradlew clean build check spotlessCheck` — `BUILD SUCCESSFUL in 1m 13s`, 111
    actionable tasks; `./gradlew -PstrictLocks verifyLocks` — `BUILD SUCCESSFUL in 986ms`, 17 actionable tasks.
  - **Done when:** both commands pass and the gate's tail is pasted as evidence.
- [x] 13.3 Repair the real Markdown acceptance defects, then translate `.temporary_context/Books_Examples/ExampleOfMDWithImagesInside/earth-gravity.md` through native Ollama, the LM Studio preset, and the URL-only OpenAI-compatible route. Markdown task-list markers (`[ ]`, `[x]`, `[X]` plus their separator) are atomic placeholders; a restored paired emphasis, strong emphasis, or link must retain nonblank visible text; and the one formatting repair tells the model those rules. A result that reads as a translation with its formatting intact and a log that shows the preflight lines and every decision are the evidence; a ticked checkbox is not. → `:document`, `:pipeline`, `:app`
  - **Do:** cover the marker, empty-pair, and formatting-repair paths with offline tests; run native Ollama with
    `BOOKLOOM_LOG_LEVEL=TRACE`, then LM Studio and `openai-compatible --base-url http://localhost:1234/v1`, one at a
    time. Preserve each output, inspect every source/output link and all five task markers, verify both image
    destinations and the local JPEG hash, then inspect the new `bookloom.log` slice for verification, prompt, reply,
    restore/unmask, repair/decision and no DEBUG `Authorization` header.
  - **Expect:** three successful preflight lines then one report line for every route; no empty or misplaced link,
    every task marker has its original state, and emphasis, tables, fences, Mermaid, HTML details and images remain
    intact. Every remaining flagged segment has a WARN explanation and preserves source formatting; malformed accepted
    Markdown is never acceptable.
  - **Evidence (2026-09-24):** offline document and pipeline repair tests passed. The renewed sequential live runs
    under `/private/tmp/bookloom-13.3.ybKIq0/` all passed connection/models/inference preflight: native Ollama
    `gemma4:e4b-mlx` completed 135 accepted / 5 flagged; LM Studio `google/gemma-4-e4b` completed 132 / 8; and the
    URL-only OpenAI-compatible route to `http://localhost:1234/v1` completed 133 / 7. Each flag is a logged
    `validation` rejection after the one repair (structure or placeholder mismatch), so the output uses the safe
    source fallback. The preserved outputs have nonempty correctly placed links, all five original task-marker states,
    both image destinations and the unchanged local JPEG SHA-256, plus intact emphasis, tables, fences, Mermaid and
    details structure. The new TRACE slice records verification start/finish, prompt, request/reply sanitisation,
    restore/unmask, repair and final decisions; its DEBUG lines contain no `Authorization` header.
