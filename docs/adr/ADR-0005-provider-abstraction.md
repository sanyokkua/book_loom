# ADR-0005 — Provider abstraction with two client implementations (Ollama-native + OpenAI-compatible)

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The application talks to a locally hosted or self-hosted Large Language Model over HTTP. Users may run Ollama, LM
Studio, llama.cpp, or any OpenAI-compatible endpoint, and may add custom endpoints. Most of these runtimes expose an
OpenAI-compatible chat surface, and it is tempting to treat all of them as one. In practice, **Ollama's
OpenAI-compatible endpoint does not fully behave as expected** — notably it does not faithfully honour options such as
`num_ctx`, and its native surface (`/api/chat`, `/api/tags`, `/api/show`, with a real `options` object, `keep_alive`,
`format`, and `think`) is the reliable way to drive it. Forcing Ollama through the OpenAI shim produces subtle,
hard-to-diagnose quality and context-window problems.

The question is how to structure the provider layer so that Ollama is driven by its native API while every other runtime
is driven by the common OpenAI-compatible surface, the two are interchangeable to callers, and new providers (e.g.
Gemini, Claude) can be added later without touching call sites. This ADR fixes the provider abstraction. Only
general-purpose instruction models are targeted; no bespoke or fine-tuned translation model is assumed.

## Decision drivers

- **Correctness over uniformity.** Ollama needs its native API to honour `num_ctx` and other options; pretending it is
  "just OpenAI-compatible" is wrong.
- **One interface to callers.** The pipeline must not branch on vendor; it calls a `Provider` port.
- **Extensibility to future providers.** Adding Gemini/Claude later must be a new implementation, not a change to
  callers — and must be possible without over-building those providers now.
- **Testability against mocked HTTP seams.** Each client is tested against a mocked server for its own dialect.
- **No heavy vendor lock-in.** Avoid a large third-party client that dictates transport, JSON, or licensing; stay within
  the Apache-2.0/MIT/BSD gate.

## Considered options

- **Option A — One OpenAI-compatible implementation for everything** (drive Ollama through its OpenAI shim), differences
  captured as per-kind profile data.
- **Option B — A `Provider` port with two concrete client implementations** — an Ollama-native client and an
  OpenAI-compatible client — selected by provider kind through a `ProviderFactory`, with per-kind `ProviderProfile` data
  for endpoint/credential/model details and the abstraction defined so further providers are new implementations.
- **Option C — Adopt a heavy third-party LLM client library** and wrap it behind the `Provider` interface.

## Decision outcome

Chosen: **Option B.** A `Provider` port defines the inference/discovery/verification surface; a `ProviderFactory` maps a
provider **kind** (`OLLAMA | OPENAI_COMPATIBLE`) to one of two concrete client implementations:

- **Ollama-native client** — Ollama's own API (`/api/chat`, `/api/tags`, `/api/show`), setting `num_ctx` and other
  options through the native `options` object, using native `format` for structured output and `think` for reasoning
  control. Used for Ollama providers.
- **OpenAI-compatible client** — `/v1/chat/completions`, `/v1/models`, `response_format` for structured output. Used for
  LM Studio, llama.cpp, generic OpenAI-compatible servers, and custom endpoints.

Per-kind `ProviderProfile` data carries endpoint, auth scheme, credential reference, model slots, and a
`supportsModelDiscovery` capability. Callers depend only on the `Provider` port; adding a provider (Gemini, Claude, …)
is adding an implementation and a kind, with no change to call sites. Those future providers are **explicitly not
implemented and not required in this scope** — the abstraction merely leaves the seam open. Both clients use the JDK
`HttpClient` and Jackson, keeping transport and JSON under project control and within the license gate, and both obey
the shared response-handling contract (structured-output-first, tolerant parsing, reasoning-strip, repair retry, text
fallback — see ADR-0013).

### Consequences

Positive:

- Ollama is driven correctly through its native API; `num_ctx` and options work as intended.
- Callers never branch on vendor; the pipeline is provider-agnostic.
- A new provider is a new implementation behind the same port — the abstraction is ready for Gemini/Claude without
  pre-building them.
- Each client is tested against a mocked server for its own dialect (both dialects covered — see the testing strategy).
- No heavy client dependency; transport and JSON stay under project control and within the license gate.

Negative:

- Two client implementations to build and maintain instead of one, with two request/response mappings.
- Shared behaviour (retry, gate, response handling) must be factored so it is not duplicated across the two clients.

Neutral:

- Model discovery is per-client with first-class manual model-ID entry when a provider has no discovery endpoint
  (ADR-0012 / DD-38).
- The provider layer targets general instruction models only; specialized model handling is out of scope.

## Pros and cons of the options

### Option A — One OpenAI-compatible implementation for everything

Pros:

- One implementation to build and test; least code.
- Works acceptably for LM Studio and generic OpenAI-compatible servers.

Cons:

- **Wrong for Ollama:** its OpenAI shim does not faithfully honour `num_ctx` and other options, causing silent
  context-window and quality problems.
- Pushes vendor quirks into profile conditionals that cannot express a genuinely different API surface.

### Option B — `Provider` port with two client implementations (chosen)

Pros:

- Correct per-runtime behaviour (Ollama native; OpenAI-compatible for the rest).
- One port to callers; new providers are new implementations.
- Each dialect is tested against its own mocked seam.
- Transport and JSON stay under project control.

Cons:

- Two implementations and two wire mappings to maintain.
- Requires factoring shared behaviour so it is not duplicated.

### Option C — Heavy third-party LLM client library

Pros:

- Off-the-shelf coverage of many providers and conveniences.

Cons:

- Pulls a large dependency dictating transport, JSON, and abstractions the project would rather own.
- License and maintenance risk; may not fit the Apache-2.0/MIT/BSD gate.
- Local-runtime quirks (native `num_ctx`, custom base paths, offline behaviour) are exactly what such libraries handle
  least well.

## Links

- Design decisions: DD-09 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-09-general-instruction-models`),
  DD-10 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-10-single-provider-impl`), DD-32
  (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-32-two-clients-abstraction`), DD-38
  (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-38-manual-model-id`)
- Related ADRs: ADR-0012 (per-project provider/model binding), ADR-0013 (response-handling contract)
- Spec clauses: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
  `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`, `docs/specification/05_Dependencies/03_LICENSING.md`
- Stories: none yet
