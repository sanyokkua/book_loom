# ADR-0013 — Response-handling contract: JSON-first, tolerant, with repair retry and text fallback

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

BookLoom drives general-purpose instruction models running locally (via Ollama or an OpenAI-compatible server). These
models are wildly inconsistent in how they respond. Some honour a structured-output request and return clean JSON; some
ignore it and answer in prose. Reasoning models emit `<think>…</think>` blocks or chain-of-thought preambles. Many wrap
JSON in markdown code fences, add commentary before or after, or include extra fields the app never asked for. A naïve
"parse the body as JSON" approach would fail constantly on exactly the local models the product targets.

The pipeline needs a single, defined, testable contract for turning any of these responses into usable data — one that
both client implementations (Ollama-native and OpenAI-compatible) obey — so that model variability is handled in one
place rather than smeared through the pipeline.

## Decision drivers

- **Robustness to model variety.** The runtime must cope with models that ignore structured output, emit reasoning,
  fence their JSON, or add fields.
- **Prefer structure, tolerate its absence.** Ask for JSON where supported, but never depend on getting it.
- **Deterministic degradation.** When structured parsing fails, degrade predictably rather than erroring out.
- **One contract, both clients.** Ollama-native and OpenAI-compatible responses are normalized the same way.
- **Privacy and safety.** Never log secrets or full book text; ignore unexpected fields rather than trusting them.

## Considered options

- **Option A — Strict structured output only.** Require conforming JSON; if the model does not comply after a retry,
  flag the chunk.
- **Option B — Plain-text delimited protocol.** Never rely on JSON; wrap the translation in sentinel delimiters and
  parse deterministically.
- **Option C — JSON-first, tolerant, with repair retry and a text fallback.** Request structure, sanitize, parse
  leniently, retry once, then fall back to treating the cleaned text as the translation.

## Decision outcome

Chosen: **Option C.** Every model-facing call follows one pipeline:

1. **Request structured output** where the client supports it — OpenAI `response_format` (json_object / json_schema);
   Ollama native `format` (json or a JSON schema) — asking for a defined shape.
2. **Sanitize before parsing:** strip reasoning/thinking blocks (`<think>…</think>` and analogues), chain-of-thought
   preambles, markdown code-fence wrappers, and leading/trailing prose; ignore any separate reasoning channel. Where
   reasoning level is controllable (Ollama `think`, OpenAI reasoning params), set it low/off for translation and judging
   to cut latency and noise.
3. **Parse tolerantly:** locate the JSON object in the cleaned text and deserialize with unknown fields ignored
   (`FAIL_ON_UNKNOWN_PROPERTIES=false`), missing optional fields defaulted, and whitespace trimmed.
4. **Repair once:** on malformed output, issue exactly **one** repair retry ("return only valid JSON matching …").
5. **Text fallback:** if it still will not parse, deterministically treat the cleaned response as the plain translation.
   Only if that then fails the QA gates is the chunk flagged.

Clients are constructed from **one injected `java.net.http.HttpClient`** with a fresh per-request timeout;
request/response DTOs are **records** in an internal `dto` package with `@JsonInclude(NON_NULL)` (null params omitted,
not sent as `null`); a tolerant shared `ObjectMapper` is used; and neither secrets nor full book text are ever logged.
Both client implementations normalize their raw responses through this same contract.

### Consequences

Positive:

- Weak and inconsistent local models are handled gracefully; the pipeline gets usable output from prose-only,
  reasoning-heavy, or fence-wrapping models.
- Model variability is confined to one tested contract, not scattered across the pipeline.
- Degradation is deterministic (structured → repaired → plain text → flag), so behaviour is predictable and testable.
- Unknown fields never break parsing; missing fields are defaulted.

Negative:

- More moving parts than strict parsing (sanitize, repair, fallback), each of which must be tested (including against
  both dialects).
- The text fallback yields less structured metadata (e.g. no self-reported confidence) than a clean JSON response.

Neutral:

- Structured output is an optimization, not a guarantee; correctness never depends on the model complying.
- The one-shot repair keeps latency bounded (it does not loop).

## Pros and cons of the options

### Option A — Strict structured output only

Pros: cleanest data. Cons: flags far too many chunks on weaker local models; brittle against the exact runtimes the
product targets.

### Option B — Plain-text delimited protocol

Pros: simplest, most model-agnostic. Cons: carries little structured metadata; delimiter collisions and reasoning bleed
still need handling.

### Option C — JSON-first, tolerant, repair + fallback (chosen)

Pros: robust across mixed models; structured when possible, degrades predictably; one contract for both clients. Cons:
more logic to build and test; fallback loses some metadata.

## Links

- Design decisions: DD-33 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-33-response-handling`)
- Spec clauses: `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
  `docs/specification/01_Product/12_PROMPT_CATALOG.md`, `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`
- Related ADRs: ADR-0005 (provider abstraction), ADR-0008 (inference concurrency gate)
- Stories: none yet
