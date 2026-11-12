**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:llm`), QA **Last Updated:** 2026-10-29
**Cross-references:** `docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/spec_baseline/specification/01_Product/07_SETTINGS.md`, `docs/spec_baseline/specification/00_Foundation/04_DESIGN_DECISIONS.md`,
`docs/spec_baseline/specification/01_Product/11_NOTIFICATIONS_AND_ERRORS.md`, `docs/spec_baseline/adr/ADR-0047-server-engines-and-model-profiles.md`

# LLM Providers and Models

This document specifies provider configuration, model selection, and inference behaviour, realizing `FR-PROV-*`,
`FR-MODEL-*`, and `FR-INFER-*`. It is implemented in the `:llm` module. Model access goes through a provider abstraction
with two client implementations — an Ollama-native client and an OpenAI-compatible client — and a detected **server
engine** on each provider that selects the request fields, error rows and routes inside those two clients (ADR-0047). The only outbound network is
**user-triggered provider communication — inference, model discovery, verification and server-engine detection** (DD-01, DD-09), plus one exception (ADR-0044): on the very first launch only (a fresh database counts), after the window is shown, one silent model listing of exactly http://127.0.0.1:11434 (Ollama /api/tags) and http://127.0.0.1:1234/v1 (LM Studio /models); no other address, no prompt or book text, nothing answering is not an error, never repeated by itself, desktop window only, only :llm holds HTTP code; every later check is user-triggered. BookLoom is private when used with local models: a provider outside this computer receives the text being translated, which is the user's choice.

## provider-model {#provider-model}

A `ChatModel` port plus a `ChatModelFactory` (over the internal `ProviderClient` and `ProviderClientFactory`) hide the concrete client from callers (DD-10). Two client implementations
exist: an **Ollama-native** client (Ollama `/api/chat`, `/api/tags`, `/api/show`, native `options` including `num_ctx`,
`keep_alive`, `format`, `think`) used for Ollama, and an **OpenAI-compatible** client (`/v1/chat/completions`,
`/v1/models`, `response_format`) used for LM Studio and any other OpenAI-compatible server. Ollama is spoken to natively
because its OpenAI-compatible endpoint does not fully honor options such as `num_ctx`. A provider **kind**
(`OLLAMA | OPENAI_COMPATIBLE`, extensible) is the **transport**: it selects the client via the factory. A provider also
holds a **server engine** (`ServerEngine`: Ollama, LM Studio, llama-server, OpenRouter, Unsloth, generic
OpenAI-compatible), its version when known, the person's optional "Treat this server as…" choice, and whether the
one-time Ollama offer was declined; the effective engine is the person's choice, else the detected one. The engine
selects fields, error rows and routes; traits derived from it are computed, never stored (see #server-engines). The
abstraction already admits future implementations (e.g. Gemini, Claude) without changing callers, but those are not
implemented and are out of scope here.

| ID           | Requirement                                                                                    |
|--------------|------------------------------------------------------------------------------------------------|
| FR-PROV-01a  | The user can add, edit, select (mark current), and delete providers from Settings → Providers. |
| FR-PROV-04a  | Multiple providers may be configured; exactly one is the current default at a time.            |
| FR-MODEL-04a | The selected translator model is remembered per provider.                   |

## provider-kinds-and-presets {#provider-kinds-and-presets}

| Preset                  | Client (kind)                           | Engine (detected)  | Endpoint shape                                          | Auth scheme                   | Discovery                     |
|-------------------------|-----------------------------------------|--------------------|---------------------------------------------------------|-------------------------------|-------------------------------|
| Ollama (local)          | Ollama-native (`OLLAMA`)                | Ollama             | `/api/chat`, `/api/tags`, `/api/show`, native `options` | None by default               | Live model list (`/api/tags`) |
| LM Studio (local)       | OpenAI-compatible (`OPENAI_COMPATIBLE`) | LM Studio          | `/api/v0/chat/completions` (falls back to `/v1/chat/completions`), `/v1/models` | None by default | Live model list (`/api/v0/models`) or manual |
| llama-server (llama.cpp) | OpenAI-compatible (`OPENAI_COMPATIBLE`) | llama-server       | `/v1/*` (address hint `http://127.0.0.1:9931/v1`)       | None / optional               | Live model list or manual     |
| Unsloth (local)         | OpenAI-compatible (`OPENAI_COMPATIBLE`) | Unsloth            | `/v1/*`                                                 | Bearer key by reference (required) | Live model list or manual |
| Other OpenAI-compatible (vLLM, hosted APIs, LocalAI, …) | OpenAI-compatible (`OPENAI_COMPATIBLE`) | Generic (or OpenRouter by host) | Configurable base URL, `/v1/*`        | Bearer / API key by reference | Live model list or manual     |

Each preset seeds endpoint, auth scheme, discovery capability, and quirks from the profile; the user may override
fields, including the model-list path (both kinds) and the chat path (OpenAI-compatible only; an Ollama description that sets it is refused). An override replaces the path only — the base URL, sign-in and request shape stay the dialect's. The one OpenAI-compatible client serves every OpenAI-shaped server, so pointing at a new such server is
configuration, not code. The command line's presets are `ollama` (engine Ollama) and `lmstudio` (engine LM Studio); the first-launch look stores the engine its listing shows (`/api/tags` is Ollama; `/v1/models` rows owned by `organization_owner` are LM Studio). Servers on other ports or on a LAN host are added by hand and their engine is read from their replies, never from the port.

## server-engines {#server-engines}

The engine is detected only when the person saves or tests a provider or presses **Detect again** (a saved provider
under the full and connection policies, a draft also under connection-and-models; the run's own recovery probe never
detects), with at most 7 GET requests of 2 seconds each, no body, no book text and the key only when the provider has
one. A reply counts as a hit only by the shape of its body, never by a status or a port: LM Studio answers 200 to every
path, and KoboldCpp and LocalAI imitate Ollama's routes, so a server whose model list carries another server's marker or
whose OpenAI-shaped rows lack `owned_by` is not Ollama. Detection also runs after a connection failure, unless the
failure was unreachable, timeout, missing credential, cancelled or auth. Known hosts need no probe (`openrouter.ai` is
OpenRouter; `ollama.com` is Ollama; Anthropic and Gemini hosts are reported "Not supported yet; reachable through
OpenRouter" and not refused). The result is stored on the provider (a draft whose id, kind and address match a saved
provider stores it on that provider). GPT4All (rows with `owned_by:"humanity"`, no structured replies) and AnythingLLM
(a base path ending `/api/v1/openai`, a chat over documents) are refused at the connection stage with `validation`,
no engine stored and no chat request sent.

| Engine | Detected by | What the engine changes in a request |
|--------|-------------|---------------------------------------|
| Ollama | `/api/version` `{"version":…}` + `/api/tags` `{"models":…}`, no impostor marker, `/v1/models` rows all carry `owned_by` | Native kind: `num_ctx`, `think`, `format`. Behind an OpenAI-compatible provider: the cap goes out only as `max_tokens` (never `max_completion_tokens`), `reasoning_effort:"none"` for Off, and the one-time offer to use Ollama's own connection (never switched by itself; Keep as is is remembered apart from the person's engine choice). |
| LM Studio | `/api/v0/models` shaped as a list with `compatibility_type`, or `organization_owner` rows | Chat is posted unstreamed to `/api/v0/chat/completions` (time to first token and speed come from its `stats`), falling back to `<base>/chat/completions` for the session when that route answers "Unexpected endpoint" or a non-JSON body with status 2xx, 404 or 405. The model id is checked against `/api/v0/models` at the first chat call of a session (and by the Test), because LM Studio answers any id with the loaded model; an `@variant` suffix is removed (after a passing models check or Save, not while typing); a reply naming another model is refused (`wrong-model-served`). `reasoning_effort:"none"` for Off; neutral samplers (`top_p` 1.0, `top_k` 0, `min_p` 0.0, `repeat_penalty` 1.0) sent explicitly. At Start a model that is not loaded gets a toast saying LM Studio will load it with its own default settings and the window may differ. |
| llama-server | `Server: llama.cpp` header or `/health` `{"status":"ok"}` with `build_info` `b<digits>-…`, then `/props` | Off is `chat_template_kwargs.enable_thinking:false` (a JSON boolean) only when the template reads that variable (read lazily by one GET of `/props` on the first call that needs it, per model id for a router; an unreadable `/props` means no kwarg); a gpt-oss id gets `reasoning_effort:"low"` and no kwarg; neutral samplers explicit (`repeat_penalty`). A router's `n_ctx:0` is never a window; a router's model id is checked against its list. |
| Unsloth | `/api/health` `{"status":"healthy","service":"Unsloth UI Backend"}` | Supported but not recommended over Ollama and LM Studio. `"enable_tools":false` on every call (its own web search, code and terminal tools are off), a Bearer key from an environment-variable reference, top-level `enable_thinking:false` for Off, `repetition_penalty` 1.0 with the other neutral samplers, the model id checked against its list. A `401` is `auth` with a sentence telling the person to create a key in Unsloth › Settings › API; a `400` for a `response_format` it cannot enforce is a refused setting (the schema is dropped for that model). |
| OpenRouter | Host rules (`openrouter.ai`), or `canonical_slug`/`supported_parameters`/`pricing` in `/models` | Every call carries `provider.require_parameters:true` (a provider that cannot honour a parameter is never silently substituted) and `plugins:[{"id":"context-compression","enabled":false}]` (a prompt is never trimmed behind the person's back); it is posted unstreamed. Only the parameters the chosen endpoint lists are sent (`repetition_penalty` is the penalty's name), the answer cap is clamped to the endpoint's `max_completion_tokens` and the window comes from its `context_length` (both read from `/models/<id>/endpoints` when the person acts). Reasoning comes from the model catalog: `none` among the listed efforts sends `reasoning:{effort:"none"}`, a reasoning object without efforts `{enabled:false}`, a mandatory one the lowest effort, a model with no reasoning field nothing. The Test's models stage lists the catalog's chat models only (those whose output modality is text). Each call line logs the served provider and model slugs and the cost, and the run summary totals the cost (numbers and slugs only, never text). Its refusals have their own words: no credits (402), a blocked passage (403 content policy), no provider that supports the request's parameters, and a hosting company's outage (waited out like any upstream outage). There are no routing, data-collection, quantisation or pinning controls: BookLoom never chooses which host serves a call. |
| Generic OpenAI-compatible | Nothing above matched | Only what a check has shown the server honours: no `response_format` and no reasoning control until a stored verdict says so (the prompt states the answer contract instead); the Test's own checks send what they ask for, because a check is how a verdict is earned. |

**Reasoning that cannot be turned off, and hosted windows.** Many hosted models always reason, and the setting may disappear from more of them, so BookLoom lives with it: a model whose catalog says reasoning is mandatory and offers no level, and a model whose reply spent its answer cap thinking (remembered for the session, per provider and model), is treated as one that thinks on every call. Every call to it carries room for the thinking on top of the answer's cap, so the answer is not starved and the single repeat is not what saves each call. A model that runs on the provider's hardware (any host outside the person's own network, and any `-cloud` model a local Ollama relays) is hosted: its reserve is four times the local one (at least 8,192 tokens), and its window is the window it reports (OpenRouter 200,000 for `openrouter/free`) instead of the 16,384 default that protects the person's own memory; a hosted provider that reports no window keeps the default, and the person's own window for the model still wins. Seen live: with `openrouter/free` the first batch call ended `reasoning-exhausted`, then every draft answered and a three-segment run finished with nothing flagged (before: all three segments were flagged).

**The Test finds the reasoning value (2026-11-03).** A hosted server of unknown make (Generic engine) gets no reasoning control until a check shows what it takes, and the person is not asked to run the Advanced checks for that. The Test's inference probe asks for reasoning off (`reasoning_effort:"none"`); when the server refuses it, the probe asks once for the lowest effort (`"low"`), and only when that is refused too is the control left out. The field that was answered is stored on the model as its thinking switch (`reasoning_effort`, or `reasoning_effort:low` for a model that refuses `none`), and every later call of that model sends it: a model the built-in rows do not know gets a control of its own, and one whose server refused `none` is held at its lowest effort for every call kind. A probe that passes inside its 64-token cap is the evidence that the value works. The Advanced thinking check still runs on request and replaces the stored switch. OpenRouter keeps its catalog-driven object, with two additions: a router (`openrouter/...`) is held at `{"effort":"low"}`, never `none`, and a model that always reasons and lists no effort is asked for `minimal`. A `410 Gone` (a hosted model taken out of service) reads as a model the provider lacks, not as an unexpected reply.

**Hosted free tiers seen in hand checks (2026-11-03).** They are measurements, not promises.

- **NVIDIA** (`https://integrate.api.nvidia.com/v1`, OpenAI-compatible, Bearer from `NVIDIA_API_KEY`; the host is not a known rule, so it is Generic). The model list carries only `id`/`object`/`created`/`owned_by`. A hosted model that reasons returns `reasoning_content`; `reasoning_effort:"none"` is refused with a bare `400 Invalid request` that names no field, so the Test's inference check, on a refusal that names no field, asks for the lowest effort and then, if that is refused, goes on without the control (see the paragraph above). Under load a model may accept the request and never answer (no 429): the call then ends at its timeout, and the run's outage recovery waits and retries. Mistral Large 4 reasons on every call (127-200 s per call, then `reasoning-exhausted`), so a book run on it is impractical; Llama 3.2 90B, DeepSeek V4.1 Flash, GLM 5.3 Flash and Gemma 4 31B answered the Test (2.6 s to 49 s) but stalled during a translation run. An end-of-life model answers `410 Gone` with the date.
- **Ollama cloud** through the local Ollama (Provider kind Ollama, `127.0.0.1:11434`): a `-cloud` model must be registered first (`ollama pull gpt-oss:120b-cloud` fetches only a manifest), otherwise the Test's models stage says the model is not available. `gpt-oss:120b-cloud`, `gemma4:31b-cloud`, `nemotron-3-nano:30b-cloud` and `deepseek-v4.1-flash:cloud` answered in 0.4-2.2 s per call; a whole three-segment run took 1-3 s. The server answers with the model's cloud name without the suffix; the client logs that as a different model and carries on. Some cloud models answer 402 (paid plan).


Request rules common to the OpenAI-compatible engines: a reply with more completion tokens than the cap and a normal
finish writes one `cap-ignored` WARN per provider and model (for a thinking call the limit is the cap plus the 1,024
reasoning-headroom tokens) and the reply is used; an error envelope whose `code` is an integer is read like the string
form; a `2xx` whose body is only `{"error":…}` is `validation` with reason `wrong-path`. The engine is chosen, shown and
corrected on Settings › Providers (`07_SETTINGS.md#providers-tab`); the optional **Advanced features** stage of a Test
stores what a server honours (see #three-stage-verification).

## authentication-and-credentials {#authentication-and-credentials}

| ID          | Requirement                                                                                                                         |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------|
| FR-PROV-05a | Credentials are stored only as a reference — an environment-variable name (an OS keychain entry is allowed later, not built) — never the secret value (DD-11). |
| FR-PROV-05b | The secret is resolved at call time immediately before each request by one lookup that the request and the form's "set / not set" share: the process environment first, then the user account's environment the operating system keeps (macOS `launchctl getenv`, Windows user then machine registry environment, Linux `environment.d/*.conf` then `/etc/environment`), an answer reused for at most 2 s, so a key set after BookLoom started needs no restart. A null or blank value counts as unset, an unreadable store counts as unset with one WARN, and a variable that is unset fails the call as `missingCredential`, naming the variable and how to set it per OS and never a value, before any request is sent; it is never written to the database, logs, or exports. |
| FR-PROV-05c | Auth schemes supported: none, bearer token, and custom header, each resolving its value from the credential reference: bearer sends `Authorization: Bearer <value>`, a custom header sends the value under the configured name. Extra headers may hold `${env:NAME}`, resolved the same way; a value with a line break or control character is refused as `validation` without being shown, and the sign-in header wins over an extra header of the same name.              |

## model-discovery-and-slots {#model-discovery-and-slots}

| ID           | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-MODEL-01a | Models are discovered live from the current provider and presented for selection when the provider supports discovery (a per-provider capability, planned). The listing is the engine's own (Ollama `/api/tags`, LM Studio `/api/v0/models`, otherwise `<baseUrl>/models`) and offers chat models only: a listing entry that shows it is an embedding model (LM Studio `type` `embeddings`) is left out, and an Ollama entry whose name ends `:cloud` or `-cloud`, or that carries `remote_host`, is listed with a neutral "cloud" label — never hidden and never behind a consent step. |
| FR-MODEL-02a | Manual model-ID entry is first-class and always available as an override; when `supportsModelDiscovery` is false, or discovery fails, returns empty, or is unauthorized, the UI uses free-text model-ID entry (EC-MODEL-1).                                                                                                                                                                                                                       |
| FR-MODEL-03a | One model slot exists: the translator (required). There is no judge or helper model and no embedding slot.                                                                                                                                                                                                                                                                                                               |
| FR-MODEL-05a | A model is **available** when: with discovery, its ID is a member of the live model list (`listModels()`); without discovery (a manually-entered model ID), the Inference verification round-trip against that model **is** the availability check — a non-empty completion proves availability, a failure marks it unavailable (`modelUnavailable`). Where the engine answers any id with the loaded model (LM Studio, Unsloth) or routes by id (a llama-server router), the id is also checked against the server's own list at the first chat call of a session per provider and model: an id that is not listed ends that call with `modelNotFound` (reason `model-not-listed`) before any chat request, and the run pauses on it; a list that cannot be read lets the call go ahead. Availability is confirmed before the provider is used for a run (see #per-project-binding). |
| FR-MODEL-06a | Each provider carries an **effective context (tokens)** value resolved in order: Ollama `num_ctx` / `/api/show` reported context → discovery-reported context → the provider profile's manual **"effective context (tokens)"** field → a conservative built-in default. The chunk packer budgets against this resolved value (see #effective-context).                                                                                            |
| FR-MODEL-07a | Each provider carries a `supportsStructuredOutput` capability, **populated during the Models/Inference verification stage** and thereafter cached on the provider. It records whether the server honoured a structured-output request; it never blocks a run, because structured output is always attempted and a rejection moves that call kind (or, for a model-wide failure, every kind) of that model to the prompt-only rung, stored as a refused setting with its scope (see #response-handling). On a generic server the stored verdict decides whether a schema is sent at all (see #server-engines).                                                                 |

Cross-chapter consistency is provided by the name/term dictionary, a context-aware translation memory (exact / context /
fuzzy via deterministic string similarity — not vectors), and a rolling bilingual summary; no embedding model or vector
store is used or reserved.

## candidate-models {#candidate-models}

The app is model-agnostic: any compatible chat model can be configured. What it knows about particular models is the
**model profile**, resolved at every call and never stored (design D20): the half a request needs (`ModelProfile` in
`:api`: how thinking is switched, the system role, template quirks, the window facts, template overhead, batch size and
size tier) and the half a person reads (`ModelReport`: who the model is, how the profile was found, a verdict).

Four sources are read in a fixed order, the later winning: the stored engine's traits (whether any thinking control can
be sent at all); the model's **name**, parsed into a family, version, line, size and variants (the metadata a server
reports wins over the name on size), which picks a **built-in row**; the **server's own facts** (a listed capability set
without `thinking` removes a switch; a model that thinks by default with no renderer cannot be switched off); and the
person's per-model overrides. A model no row knows gets the default profile (no thinking control, answers asked through
the prompt, a batch of 2) and the verdict "unknown model".

**Built-in rows** (`llm: profile/BuiltInRows`, grouped by family in `GemmaRows`, `MistralRows`, `QwenRows` and
`OtherRows`) are data with the rule in words: an identity pattern, the reasoning switch and its off value, the system
role, quirks, the hard cap, the batch (4 for a small tier, 8 for a large one, 2 for the smallest models) and a verdict. A row
holds no maker's sampler values: arm 3a of the redesign kept BookLoom's own temperature per call kind, with neutral
values where a server's own samplers are not neutral (`docs/DEVELOPMENT.md#redesign-arms`). The classes they restate: models that never think (with a native
system role, or with none so the system text is folded: Gemma 2 and 3); a boolean thinking switch (Gemma 4, Qwen 3.5 and
3.6, Nemotron 3, Magistral); a level switch (Qwen 3.8); models that always reason (gpt-oss in the harmony format;
muse-glimmer, whose template writes the reasoning level; Ministral 3 Reasoning, which has no switch at all). Gemma 4's
`-it` and `-it-qat` builds share one row per size. Template quirks a row sets: `QUOTE_CUTS_SCHEMA_STRING` (Gemma 3),
`TEMPLATE_WRITES_REASONING` (muse-glimmer), `HARMONY_FORMAT` (gpt-oss), `ESCAPE_THINK_TOGGLES` (the Nemotron 3 builds
with the older renderer).

A row is picked by the parsed name alone, by the whole family word and never by its start. **The architecture a server
reports never picks a row**: `llama` and `deepseek2` are shared by unrelated models, and a family with no row of its
own (Nemotron 3.5 Bolt) keeps the default profile and its own unknown languages whatever architecture its server
reports. A name that reads a reasoning, thinking or vision variant never falls to the plain instruct row of its family.
The rows for Ministral 3 Reasoning, EuroLLM and Qwen 2.5's smallest sizes cover models the owner runs; a fine-tune takes
its base family's handling and languages but never its verdict (`llm: profile`, `knowledge`). The rows are guidance for handling,
not a supported-model guarantee or a fixed list (DD-42). What the app must handle regardless is captured by the
response-handling contract (`#response-handling`, DD-33): request structured output where supported, strip reasoning,
tolerate variance, fall back to text.

**Quant and declared-languages advice (built, redesign group 9).** Two bundled tables, `quant-tiers.csv` and
`declared-languages.csv` in `llm: knowledge`, each with a `# data-version:` line (`scripts/model-knowledge-check.py`
compares them with the research data), feed advice that is shown and never enforced: no line blocks Start, changes a
setting or sends a request.

- *Quant.* `QuantAdvisor` takes the quant token from the name (`Q4_K_M`, `IQ4_NL`, `UD-Q4_K_XL`, `nvfp4`), or from the
  server's facts when it reports one (the facts win on which table row applies), and judges it against the model's size
  class (`SizeClassifier`: small up to 5B, mid up to 16B, large above, MoE by active parameters). The tier is one of
  excellent, good, acceptable, poor, unusable, removed (an old format) or not measured (no row); a quantisation-aware
  Q4_0 build reads the `Q4_0_QAT` row. The chip `token · tier` appears beside each model in every picker; a model whose
  name has no quant token shows no chip until the server's facts are read (select, Test or Detect again). The sentence
  under the tier is quiet for good and excellent and names the target language for the others.
- *Declared languages.* `LanguageAdvisor` says whether the maker lists a language: a **complete** list can say "not
  declared"; a list that only names languages (positive-only) can say "declared" but never "missing"; a model whose
  maker says nothing is unknown; a fine-tune reads its base model's list and says so. The server's own language list
  never overrides the table. A language counts as tested only where BookLoom's own run exists for that pair (the row's
  evidence); a maker's claim does not.
- *Where it is shown.* Settings › Models (`07_SETTINGS.md#models-tab`, where BookLoom's verdict line comes first and these lines follow) and, as one line, at Start (`StartAdviceLines`):
  a warning where a complete list leaves out the source or target, a quieter information line where the maker says
  nothing or only a base model lists it, nothing where the model declares the language or BookLoom tested the pair.

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

How a window is set differs by engine: only the Ollama-native connection sends it per request (`num_ctx`); an
OpenAI-compatible request cannot express one, so LM Studio loads a model with its own default settings (the loaded
figure is the better guess), llama-server fixes it at server start and shares it between slots (a router's own `n_ctx:0`
is never a window), and a hosted API decides it itself.

The chunk packer budgets against the **resolved** value:
`effectiveBudget = min(effectiveContext − reservedHeadroom, chunkBudgetSetting)`, where reserved headroom accounts for
the system prompt, injected context, and the target-side output. When multiple `num_ctx`/context inputs collide,
precedence is **request-level > provider-level > setting default**, and the packer always budgets the single resolved
value. The token count itself is a deterministic heuristic (no shipped tokenizer); the formula lives in
`docs/spec_baseline/specification/01_Product/05_TRANSLATION_ALGORITHM.md#chunking`.

## three-stage-verification {#three-stage-verification}

Verification runs against the **draft** provider configuration before it is saved (FR-PROV-06), so an unverified change
never affects the current working provider. The three stages are reported independently (FR-PROV-07):

| Stage      | Check                                                                                                                                                                                                                                                                                                                                              | Failure surfaced as                                                     |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| Connection | A dedicated **provider probe** (`probe()`/`verifyConnection()`) reaches the endpoint: Ollama hits the root / `/api/version`, an OpenAI-compatible server does a `/v1/models` HEAD or cheap GET. A **credential-resolution pre-check** runs first — a draft credential reference that does not yet resolve fails this stage as `missingCredential`. The stage also **detects the server engine** (see #server-engines): an Ollama-native provider whose address is answered by another recognised engine fails with a message naming it ("The server at this address is LM Studio, not Ollama."; a generic server does not count), and GPT4All or AnythingLLM are refused with one plain sentence each. The draft is the unsaved form: it is checked by the provider rules, run on an uncached client, never registered, and each stage's exchange shows its request headers with every secret masked (`Authorization: Bearer ••••`). When the structured probe is rejected and the plain probe answers, inference passes with the note "structured output: not confirmed". | Typed `AppError` (connection / `missingCredential` / `validation`), banner/toast. |
| Models     | Model listing (`listModels()`) succeeds (or manual fallback offered). Discovery failure is a typed `discoveryFailed`. Where any id is served (LM Studio, Unsloth, a llama-server router) the typed id is checked against the server's own list; an unlisted id fails as `modelNotFound` (reason `model-not-listed`) naming the id. | Typed `AppError` (`discoveryFailed` / `modelNotFound`), fallback path. |
| Inference  | A minimal chat completion returns a valid response from the chosen model; this **also populates** `supportsStructuredOutput` (whether the structured-output request was honoured or silently downgraded), stored as a verdict on the model's settings. For a manually-entered model this round-trip **is** the availability check.                                                              | Typed `AppError` (inference / `modelUnavailable`), stage marked failed. |

An optional fourth stage, **Advanced features**, runs only when the person asks (it is refused while a run translates
and allowed while the run is paused): at most four short checks with invented text and at most 64 output tokens each —
the answer schema, turning thinking off (the engine's own switch first, then `reasoning_effort:"none"`, then
`chat_template_kwargs.enable_thinking:false`, then `reasoning:{enabled:false}`; the first that works is stored), the
extra samplers `top_k` and `min_p`, and the answer-cap field (the winning field is also stored as the model's output
field). A recognised engine runs the first two; a generic server runs all four; the Ollama-native kind checks the schema
through `format` and thinking as one `think:false` call. Each verdict (honoured, ignored or refused, source `test`, with
its time) is stored on the model's settings; the verdicts of an unsaved draft apply once a provider with that id is
saved.

The provider dialog exposes this as a Test connection / models / inference trio (see
`08_UI_SCREENS_AND_STATES.md#dialog-add-edit-provider`). Because Connection and Models are separated by the dedicated
probe, the two stages are reported independently even when discovery is unsupported. Provider-dialog verification goes
through the same single blocking `InferenceGate` as every other call: when the gate is held by a run in progress, the
verification call simply waits for that call to finish rather than being refused — there is no early `busy` here. The
one `busy` outcome in this system belongs to a review retry asked while the project's own run is RUNNING, refused from
the run record before it reaches the gate at all.

## inference-behaviour {#inference-behaviour}

| ID           | Requirement                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-INFER-01a | Inference goes through the `ChatModel` port (`ChatRequest` → `ChatResponse`); the concrete client is the Ollama-native or OpenAI-compatible one per provider kind, and the request fields come from the provider's effective server engine. No other outbound calls are made, except the first-launch local listing (ADR-0044; on the very first launch only (a fresh database counts), after the window is shown, one silent model listing of exactly http://127.0.0.1:11434 (Ollama /api/tags) and http://127.0.0.1:1234/v1 (LM Studio /models); no other address, no prompt or book text, nothing answering is not an error, never repeated by itself, desktop window only, only :llm holds HTTP code; every later check is user-triggered).                                                                                                                                                                                                                                                                  |
| FR-INFER-02a | The Ollama-native client sets `num_ctx` and related native `options` (`keep_alive`, `format`, `think`) that the OpenAI-compatible shape cannot express.                                                                                                                                                                                                                                                                                                              |
| FR-INFER-03a | Nullable/unset request parameters are omitted from the request body rather than sent as null.                                                                                                                                                                                                                                                                                                                                                                        |
| FR-INFER-04a | Caller-visible streaming is **deferred (out of scope for v1)**. `ChatModel.chat` is synchronous and returns the whole `ChatResponse` body (`Result<ChatResponse>`); there is no streaming request flag. The Ollama-native client reads its reply as a stream internally only to end a stalled reply after a 60-second idle gap. Cancellation aborts the request in flight and no further request is sent; the HTTP request timeout stays the hard upper bound only for a call nobody cancels (see `docs/spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`). |
| FR-INFER-06a | All inference passes through the single-flight InferenceGate so a local model serves one request at a time (DD-12).                                                                                                                                                                                                                                                                                                                                                  |

## response-handling {#response-handling}

Every model-facing call is JSON-first but tolerant, with a single repair retry and a deterministic text fallback:
| ID | Requirement | |---|---| | FR-INFER-09a | Structured output is **always attempted** (OpenAI `response_format`;
Ollama `format`) — on a generic OpenAI-compatible server only once a stored check verdict shows it is honoured; until then the prompt states the answer contract. A structured-output rejection (HTTP 400 / "unsupported") is a **silent downgrade**: the call is
retried as a plain request and continues down the text-fallback path — never a hard failure. The outcome is recorded on
the provider's `supportsStructuredOutput` capability. | | FR-INFER-09d | Reasoning control is **best-effort**: every call asks for thinking off, and the model's profile decides what is sent (design D20): no control the model or its engine does not declare; then the person's Thinking choice on Settings › Models (Recommended = off on every call, Allow on proofreading and names, Always); then the profile's own off value. Ollama `think` is `false` (Off), the model's lowest level when it lists no `false` (`"low"` for gpt-oss), or `true` (On); on an OpenAI-compatible server Off is the engine's own switch (`reasoning_effort:"none"` for Ollama `/v1`, LM Studio and a generic server whose check proved it, `chat_template_kwargs.enable_thinking:false` as a JSON boolean for a llama-server whose template reads it, top-level `enable_thinking:false` for Unsloth), and a model that always reasons at a level (gpt-oss, muse-glimmer) gets `reasoning_effort:"low"`. A call that thinks, and every call of a model that cannot stop thinking (no control sent), gets a reasoning reserve of `max(2048, 4·S + 35·n)` tokens on its output cap for a source of S tokens in n items; no environment variable changes any of this. The parameter is **omitted when unsupported** (a model that refuses it is repeated without it). Whether or
not the parameter takes, output always falls through to the `<think>`-strip sanitize path, so an always-on or
non-disableable reasoning model still yields clean output. | | FR-INFER-09e | **Any optional setting a model refuses is dropped, not fatal** (mockups/README.md "Refused settings (S6)"). When a reply is HTTP 400 or 422, is classified `validation`, and its body names a setting the request **sent** (`temperature`, `top_p`, `top_k`, `min_p`, repeat penalty, `seed`, `max_tokens`, `num_ctx`, besides the reasoning and structured-output cases above) with words like unsupported / unknown / invalid / only the default, the request is repeated without that setting alone (`max_tokens` becomes `max_completion_tokens`); the repeat is not an attempt, and each distinct setting earns one repeat per call. The refusal is remembered per provider + model in `ModelSettings.refused` (written by `RefusalMemory`) until the person sends the setting again in Settings › Models, and one warn line says the request was repeated without it. A remembered refusal beats the person's own choice (My value 0.7 with `temperature` refused sends none); a refused sampler's neutral value does not return on the resend. A 400 or 422 that names nothing the request sent stays an ordinary `validation` error. `CapabilityRejections` is the recogniser. | | FR-INFER-09b | Responses are sanitized before parsing —
`<think>` reasoning blocks, chain-of-thought preambles, code-fence wrappers, and stray prose are stripped — then parsed
tolerantly (unknown fields ignored, missing defaulted, whitespace trimmed). | | FR-INFER-09c | Malformed JSON triggers
exactly one repair retry; if still unparseable the sanitized text is used as the plain translation, and the chunk is
flagged only if it then fails the QA gates. **Empty-response ordering:** a **raw-empty** response (blank *before*
sanitize) maps to `emptyCompletion`; a **non-empty response that sanitizes to empty** takes one repair retry
specifically for empty-after-sanitize, and if still empty yields an empty text-fallback result that QA then flags — it
is not an `emptyCompletion`. |

A provider whose credential variable is unset fails every call, discovery included, with `missingCredential` before any request is made; discovery preserves this code instead of degrading it to `discoveryFailed`.

## per-project-binding {#per-project-binding}

Provider and model selection is bound per project; Settings values are only defaults for new projects. States and
dialogs are in `08_UI_SCREENS_AND_STATES.md`; storage is in
`docs/spec_baseline/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`. | ID | Requirement | |---|---| | FR-PROV-08a | The
`current_provider` setting is the **new-project default only**; on creation the current defaults are copied into the
project and recorded as its last-used snapshot. `current_provider` is **never consulted for an existing project's
run** — a project always runs on its own bound provider/models. | | FR-PROV-08b | A project persists and reuses its own
bound provider and models on every resume, so it does not switch mid-run when settings later change. | | FR-PROV-09a |
Before any inference (real runs and diagnostics), the bound provider connection and model availability are verified
using the availability definition in FR-MODEL-05a. **Scope:** the **translator** model is the only model and is always verified (there is no judge or helper model). On unavailability the app prompts
and falls back to the current default only on explicit confirmation, recording the change. | | FR-PROV-10a | On resume
the project's `last_used_json` snapshot is compared against the **live provider row**; on **any** drift (not only a
settings-default difference) the app raises the ADR-0012 "apply new selection vs continue with the previous one" prompt
(default: continue). |

## retry-and-error-mapping {#retry-and-error-mapping}

| ID           | Requirement                                                                                                                        |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------|
| FR-INFER-05a | Retry is service-owned and keyed on typed retryable errors; it honours `Retry-After` and uses a fresh timeout per attempt (DD-13). |
| FR-INFER-05b | Non-retryable errors fail fast without retry.                                                                                      |
| FR-INFER-07a | HTTP status and transport failures map to typed `AppError`/`ErrorCode` values with a safe-details allowlist (DD-14).               |

### provider-edge-cases {#provider-edge-cases}

| ID          | Edge case                                                | Expected behaviour                                                                                                                                                                                                                                                                        |
|-------------|-----------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| EC-MODEL-1  | Model discovery missing, empty, failing, or unauthorized | Offer manual model-ID entry (always available as an override); do not block provider setup.                                                                                                                                                                                               |
| EC-MODEL-2  | Selected model no longer available at run time           | Fail with `modelUnavailable`; prompt re-selection; do not silently substitute.                                                                                                                                                                                                            |
| EC-CTX-1    | Context-window sizing needed                             | Resolve the effective context per #effective-context (Ollama `num_ctx`/`/api/show` → discovery → manual field → conservative default); the packer budgets the resolved value. For an OpenAI-compatible server that cannot express `num_ctx`, use the manual field or default and proceed. |
| EC-CONCUR-2 | Provider-dialog verification while a run holds the gate  | The verification call goes through the same single blocking gate as the run and simply waits for the call in flight; a review retry against the project's own RUNNING run is refused with `busy` from the run record instead, before any gate wait.                                       |
| EC-NET-1    | Connection refused / DNS / TLS failure                   | Map to a typed connection error; surface via banner/toast; retry only if retryable.                                                                                                                                                                                                       |
| EC-NET-2    | HTTP 429 / rate limit with `Retry-After`                 | Honour `Retry-After`; retry within budget; then fail typed.                                                                                                                                                                                                                               |
| EC-CONCUR-1 | Concurrent inference attempts against one local model    | Serialize via InferenceGate; second caller waits, does not fail.                                                                                                                                                                                                                          |
