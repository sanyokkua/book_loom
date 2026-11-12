# ADR-0047 — Name the server engine on the provider and build every request from it

**Status:** accepted, built across groups 5, 6, 8 and 9 of `redesign-translation-pipeline` **Date:** 2026-10-29
**Deciders:** owner (decisions D-5, D-6, D-2 of the 2026-10-27 pipeline research)
**Amends:** ADR-0043 (stored shapes reset once more), ADR-0044 (clarified, not changed)

## Context and problem statement

Every server speaks Chat Completions, but the same intent needs different fields on each: the answer-length cap,
the thinking switch, extra samplers, error wording, routes. `ProviderKind` has two values (Ollama-native,
OpenAI-compatible) and cannot tell LM Studio from llama-server or an Ollama behind its `/v1` shim. What is missing is
the engine, not more clients. Reading the engine on every request would need the network; detecting it by port is wrong
(LM Studio answers 200 to every path, and several servers copy Ollama's routes).

## Decision drivers

- A request must be built without a network round trip.
- Opening a book never needs the network; the only calls are user-triggered (ADR-0010) plus the first-launch look
  (ADR-0044).
- One wrong engine must not poison a run; the person can override.

## Considered options

- One client per engine.
- Detect by port or status code.
- Detect the engine from reply bodies when the person saves, tests or presses Detect again, and store it.

## Decision outcome

Chosen: detect from the shape of reply bodies only on Save, Test and Detect again, store the result on the provider.

- `ServerEngine {OLLAMA, LM_STUDIO, LLAMA_CPP, OPENROUTER, UNSLOTH, GENERIC}`; `ProviderConfig` holds `engine`
  (detected), `engineChoice` (the person's "Treat this server as...", never overwritten by detection),
  `engineVersion` and `nativeOfferDeclined`; the effective engine is the choice, else the detected one.
- The engine selects request fields, error rows and routes; `ProviderKind` stays the transport with two clients.
- Traits (`EngineTraits`) are computed from the engine and version and never stored.
- Check verdicts (answer schema, thinking switch, extra samplers, cap field) are stored on the model's settings as
  `ProbeVerdicts`, each with outcome, source (`test`) and time.
- Profile resolution order: engine traits, server facts, name reading, built-in row, the person's override.
- The first-launch look (ADR-0044) stays on the two default loopback addresses and stores the engine by which probe
  answered. Servers on other ports or on a LAN host are added by hand, and their engine is read from replies, never
  from the port.

### Consequences

- Positive: requests need no network; one place decides fields; the person can correct a wrong guess.
- Negative: stored shapes change, so the database is set aside once (ADR-0043); detection can be wrong for a new
  look-alike server.
- Neutral: no new client, no new network call.

## Pros and cons of the options

### One client per engine

- Good: isolated code.
- Bad: only fields and error rows differ; parsing and transport would be duplicated.

### Detect by port or status

- Good: no probes.
- Bad: wrong for LM Studio and every copy of Ollama's routes.

## Falsifiers

- More than one wrong engine in the live matrix of task 5.9.
- More than about 8 version branches in `EngineTraits` in one release.
- One engine needing its own response parser.

## Links

- Design decisions: DD-01, DD-20
- Spec clauses: `openspec/changes/archive/2026-11-12-redesign-translation-pipeline/design.md` D17, D26
- Stories: none yet
