---
phase: PHASE_05_PROVIDERS
# Machine-readable phase→clause manifest read by `./gradlew traceCheck` for the
# phase-exit orphan-clause check (docs/implementation_plan/03_TRACEABILITY.md#orphan-clause-phase-exit).
# These clauses must each be cited by a non-superseded story before this phase is an "implemented phase".
phase_clauses:
  - docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour
  - docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#model-discovery-and-slots
  - docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets
  - docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#retry-and-error-mapping
  - docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification
  - docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling
  - docs/specification/02_Architecture/09_ERROR_HANDLING.md#result-envelope
  - FR-INFER-01
  - FR-INFER-02
  - FR-INFER-03
  - FR-INFER-04
  - FR-INFER-05
  - FR-INFER-06
  - FR-INFER-07
  - FR-INFER-08
  - FR-INFER-09
  - FR-INFER-10
  - FR-MODEL-01
  - FR-MODEL-02
  - FR-MODEL-03
  - FR-MODEL-04
  - FR-PROV-01
  - FR-PROV-02
  - FR-PROV-03
  - FR-PROV-04
  - FR-PROV-05
  - FR-PROV-06
  - FR-PROV-07
  - DD-10
  - DD-11
  - DD-12
  - DD-13
  - DD-14
  - DD-18
  - DD-31
  - DD-32
  - DD-33
  - DD-38
---

**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/implementation_plan/07_ROADMAP.md`,
`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md`

# PHASE_05 — Providers

## Goal

Build the inference layer: the `Provider` abstraction with **two client implementations** (Ollama-native +
OpenAI-compatible) selected by a `ProviderFactory`, per-kind `ProviderProfile` data, the JSON-first tolerant
response-handling contract, live model discovery with first-class manual model-ID entry, three-stage verification on the
draft config, credential-as-reference storage, the single-flight `InferenceGate`, service-owned retry, and
HTTP→typed-error mapping. This establishes seams F3 (provider abstraction) and F4 (inference gate). Ollama traffic uses
the native API (native `options` for `num_ctx`); all other servers use the OpenAI-compatible client.

## In scope

- `Provider` port + two client implementations (Ollama-native, OpenAI-compatible) behind a `ProviderFactory` selected by
  kind (`OLLAMA | OPENAI_COMPATIBLE`); per-kind `ProviderProfile` records; presets Ollama, LM Studio, llama.cpp,
  OpenAI-compatible, custom; the abstraction admits future providers (Gemini/Claude) as new implementations without
  caller changes (not implemented here).
- Chat inference via JDK `HttpClient` for both clients (Ollama `/api/chat` with native `options` incl. `num_ctx`;
  OpenAI-compatible `/v1/chat/completions`); omit nullable params; optional streaming.
- Response-handling contract: request structured output where supported, strip reasoning/`<think>`/code-fence/prose,
  tolerant parse (ignore unknown, default missing), one repair retry, deterministic text fallback (DD-33).
- Live model discovery (`supportsModelDiscovery`) with **first-class manual model-ID entry** always available;
  translator + judge/helper slots (no embedding); remembered model per provider.
- Three-stage verification (connection → models → inference) run against the **draft** config, each stage reported
  independently with a typed error.
- Credential-as-reference storage (env-var name / keychain), never the secret.
- Single-flight `InferenceGate` (a local model serves one request at a time).
- Service-owned retry keyed on typed retryable errors, honouring `Retry-After`, fresh per-attempt timeout;
  HTTP/transport → typed `AppError`/`ErrorCode`.

## Out of scope

- The translation pipeline that consumes providers (PHASE_06).
- The provider dialog / Settings UI (PHASE_11) — verification and discovery logic exist here; the dialog wires to them
  later.
- The per-project provider/model **binding + resume prompts** (persistence in PHASE_04, resume flow in
  PHASE_06/PHASE_10) — this phase provides the preflight verification the binding calls.
- Neural QE (documented future sidecar). No embeddings or vector store anywhere (DD-18).

## Dependencies

PHASE_00 (modules, Result/AppError, offline invariant). PHASE_04 (secret-reference storage, settings KV for remembered
model/provider).

## Forward-compatibility

- **Establishes F3** — `Provider`/`ProviderProfile`/`ProviderFactory` so kinds are data (consumed by PHASE_06+,
  PHASE_11).
- **Establishes F4** — the single-flight `InferenceGate` present before any pipeline concurrency (consumed by PHASE_06,
  PHASE_08).
- Reinforces F9 — all LLM tests run against WireMock; only `:llm` touches `java.net.http`.

## Suggested stories / tasks

_Backlog, refined by `/plan-phase-stories-creation`, not the source of truth._

| Candidate task                                                                                                                                                                                                                          | Target modules                                                                             | Cited spec clauses                                                                                                                           |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `Provider` port + two client impls (Ollama-native, OpenAI-compatible) + `ProviderFactory` by kind; per-kind `ProviderProfile` records                                                                                                   | `:api/ua.bookloom.api.llm`, `:llm/ua.bookloom.llm.provider`, `:llm/ua.bookloom.llm.client` | FR-PROV-02, FR-PROV-03, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#provider-kinds-and-presets`, DD-10, DD-32                                 |
| Add/edit/select/delete providers; exactly one current                                                                                                                                                                                   | `:llm/ua.bookloom.llm.provider`, `:persistence/ua.bookloom.persistence.dao`                | FR-PROV-01, FR-PROV-04                                                                                                                       |
| Ollama-native chat (`/api/chat`, native `options` incl. `num_ctx`) + OpenAI-compatible chat (`/v1/chat/completions`); omit nullable params; optional streaming                                                                          | `:llm/ua.bookloom.llm.client`, `:llm/ua.bookloom.llm.dto`                                  | FR-INFER-01, FR-INFER-02, FR-INFER-03, FR-INFER-04, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#inference-behaviour`                          |
| Response handling: structured-output request, reasoning/`<think>`/fence strip, tolerant parse, one repair retry, text fallback; client construction (one injected `HttpClient`, record DTOs `@JsonInclude(NON_NULL)`, secret-free logs) | `:llm/ua.bookloom.llm.client`, `:llm/ua.bookloom.llm.dto`                                  | FR-INFER-09, FR-INFER-10, DD-33, `02_Architecture/04_LLM_INTEGRATION.md#response-handling`                                                   |
| Preflight connection + model-availability check before any inference (runs and diagnostics)                                                                                                                                             | `:llm/ua.bookloom.llm.verify`                                                              | FR-INFER-08, DD-31                                                                                                                           |
| Live model discovery (`supportsModelDiscovery`) + first-class manual model-ID entry; translator/judge slots; remembered model                                                                                                           | `:llm/ua.bookloom.llm.discovery`                                                           | FR-MODEL-01, FR-MODEL-02, FR-MODEL-03, FR-MODEL-04, DD-38, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#model-discovery-and-slots`, EC-MODEL-* |
| Three-stage verification (connection/models/inference) on the draft config, independent typed results                                                                                                                                   | `:llm/ua.bookloom.llm.verify`                                                              | FR-PROV-06, FR-PROV-07, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification`                                                 |
| Credential-as-reference storage (env-var name / keychain), never the secret                                                                                                                                                             | `:llm/ua.bookloom.llm.provider`, `:persistence/ua.bookloom.persistence.secret`             | FR-PROV-05, DD-11                                                                                                                            |
| Single-flight `InferenceGate`                                                                                                                                                                                                           | `:llm/ua.bookloom.llm.gate`                                                                | FR-INFER-06, DD-12 (seam F4)                                                                                                                 |
| Service-owned retry (typed retryable errors, `Retry-After`, fresh per-attempt timeout)                                                                                                                                                  | `:llm/ua.bookloom.llm.retry`                                                               | FR-INFER-05, DD-13, `01_Product/04_LLM_PROVIDERS_AND_MODELS.md#retry-and-error-mapping`                                                      |
| HTTP/transport → typed `AppError`/`ErrorCode` mapping                                                                                                                                                                                   | `:llm/ua.bookloom.llm.error`                                                               | FR-INFER-07, DD-14, `02_Architecture/09_ERROR_HANDLING.md#result-envelope`                                                                   |

## Phase exit checklist

- [ ] `Provider` port with two client impls behind `ProviderFactory`; adding a future provider is a new implementation,
  not a caller change (F3 established).
- [ ] Ollama-native and OpenAI-compatible chat both work against WireMock; nullable params omitted; Ollama `num_ctx` set
  via native `options`.
- [ ] Response handling verified: structured-output request, reasoning/fence strip, tolerant parse, one repair retry,
  text fallback (both dialects).
- [ ] Preflight connection + model-availability check runs before any inference; unverified/missing → typed error.
- [ ] Model discovery works and manual model-ID entry is always available; translator + judge/helper slots present (no
  embedding); model remembered per provider.
- [ ] Three-stage verification runs on the draft config and reports each stage independently with a typed error.
- [ ] Credentials stored only as a reference; no secret value persisted or logged.
- [ ] `InferenceGate` serializes concurrent inference to one in-flight request (F4 established).
- [ ] Retry honours `Retry-After` with a fresh per-attempt timeout; HTTP/transport errors map to typed `ErrorCode`s.
- [ ] Only `:llm` depends on `java.net.http`; all tests use WireMock (offline invariant intact).
- [ ] `./gradlew test` and `./gradlew traceCheck` green; module inventory updated.
