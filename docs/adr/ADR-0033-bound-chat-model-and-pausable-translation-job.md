# ADR-0033 — The translation engine gets a bound chat model and runs as a pausable job

**Status:** accepted **Date:** 2026-09-13 **Deciders:** owner

## Context and problem statement

The first translation pipeline, the OpenSpec change `add-translation-engine-and-cli`, fixes two contracts that the
translation screen and the real LLM clients will build on. Changing either later means rewriting both sides.

- **How the engine reaches a model.**
  - ADR-0005 puts every model behind a `Provider` port with two clients, and ADR-0012 binds each project to its own
    provider and models.
  - The owner's go_text app shows the same split: a provider with a chat call, a factory keyed by provider kind, and a
    service that resolves the configured provider and the selected model.
  - What the engine itself should hold was still open. So was how a per-call setting reaches the model: the
    specification sets a temperature per prompt (`05_PIPELINE_ENGINE.md#generation-parameters`), and a screen will let
    the user change it while a job is paused.
- **How a run pauses.**
  - The specification gives a job handle with `pause()`, `resume()` and `cancel()`, and a `paused` run state
    (`02_Architecture/08_THREADING_CONCURRENCY.md#cancellation`). It does not say whether a paused engine waits or
    returns.
  - The owner wants pauses after a segment, after a section, between stages, on an error, and on request. The command
    line uses none of them.

## Decision

**The engine gets a chat model that is already bound to a provider and a model.**
- It sends messages and receives text plus a finish reason. It never names a provider or a model.
- A factory creates the model from a provider id and a model id. Behind the factory, `:llm` keeps ADR-0005's provider
  port and clients and ADR-0008's gate and retries.
- A setting the engine chooses for one call, and that does not depend on who answers, will be an optional field of the
  chat request, left out of the wire request when absent. The first is a temperature, later an output format. Such a
  field is added when the first prompt or screen needs it, without changing a signature. A provider or model identity
  is never such a field.
- The built-in provider `pseudo` is the only one until real clients exist.

**A translation is a job object that is also its own handle.**
- `run()` executes synchronously on the caller's thread.
- `pause()`, `resume()`, `cancel()` and the enabled pause points can be used from any thread.
- Progress reaches subscribers as ordered events.
- A paused job halts in place at a boundary and waits. `resume()` continues the same run; `cancel()` ends it.
- With no pause points and no subscribers, the job runs start to finish. That keeps ADR-0007's automatic-first
  behaviour the default.

## Considered options

- **The engine calls the provider port with a model name in each request**, as go_text's `Provider.Chat` does.
  Rejected: the engine would own model selection, which ADR-0012 gives to the project binding. go_text also re-reads
  the current provider and settings on every call, so a settings change in the middle of a run would silently switch
  the model a book is translated with.
- **No per-call settings; one bound model per prompt role** (draft, judge, repair), each created with its own
  temperature. Rejected: a temperature changed at a pause, or a lower one for a single repair, would need a new model
  object in the middle of a job, and the engine's signature would change with every new role.
- **The whole provider contract now** (configs, kinds, auth, discovery). Rejected: no client would prove it yet.
- **A pause ends `run()` with a Paused outcome, and resuming calls `run()` again.** Rejected: every caller would have to
  drive open, translate, export and close itself, and "paused" would stop being a state the engine owns.

## Consequences

- **Positive:**
  - Real clients, provider settings and model discovery plug in behind the factory without changing `:pipeline`.
  - A screen drives a job with four calls and a subscription.
  - The command line uses the same job with nothing enabled.
  - Changing the temperature at a pause needs one optional request field and one command on the job, not a new
    contract.
- **Negative:** a paused job holds its thread, and cancelling waits for the model call in progress. This is accepted:
  one job runs at a time, and real clients bound each call with their request timeout.
- **Neutral:** the specification's `JobHandle` is named `TranslationJob`, and the affected clauses are edited in the
  same change.

## What would falsify this decision

- A per-call setting the engine needs depends on which provider or model answers, such as a model-specific parameter
  the engine would have to know, so it cannot be an optional, provider-neutral request field.
- A screen needs something that a thread waiting in place cannot give, for example many books paused at once, each
  holding a thread.
