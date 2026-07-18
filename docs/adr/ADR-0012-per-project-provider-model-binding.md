# ADR-0012 — Per-project provider/model binding with change confirmation and preflight verification

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

A book translation is a long-lived job: a user may start it, pause, close the app, and resume days later. The output's
consistency — names, tone, register, terminology — depends heavily on *which model and provider* produced the earlier
chunks. If the provider or model silently changes between runs (because the user reconfigured settings, switched
machines, or the previously-used model is no longer loaded), the second half of the book can diverge in quality and
voice from the first half. That regression is exactly what a serious translation tool must avoid.

At the same time, settings must remain useful as defaults, providers can genuinely become unavailable, and the app must
never issue inference against a provider/model it has not confirmed is reachable. This ADR fixes how a project's
provider/model are bound, remembered, verified, and changed.

## Decision drivers

- **No mid-run quality regression.** A resumed project must continue on the same provider/model unless the user
  knowingly changes it.
- **Settings are defaults, not a live override.** Reconfiguring settings must not silently rewrite an in-flight
  project's model.
- **Availability is real.** The bound provider/model may be unreachable or unloaded on resume; the app must detect that
  and let the user decide.
- **No unverified inference.** Connection and model availability must be confirmed before any request (runs and
  diagnostics).
- **Explicit, minimal prompts.** Confirmations should appear only when something actually differs or is unavailable.

## Considered options

- **Option A — Always use the current settings provider/model.** Simplest; the project has no binding of its own.
- **Option B — Bind provider/model per project; settings are defaults for new projects only; verify on resume and
  confirm any change.**
- **Option C — Freeze the binding permanently at creation.** The project can never change provider/model.

## Decision outcome

Chosen: **Option B.** At creation a project **copies** the current settings-default provider + translator/judge models
into its own binding and records a **last-used snapshot** (provider id, kind, base URL, model ids). On every resume the
project uses **its own** binding, not the settings default. Specifically:

1. **Preflight verification.** Before continuing (and before any inference — real or diagnostic), the app verifies the
   bound provider is reachable and the bound model (s) are available; it fails fast with a typed error otherwise.
2. **Unavailable binding.** If the bound provider/model is unavailable, the app does **not** silently switch. It prompts
   the user and falls back to the current settings default **only on confirmation**, recording the change to the
   project.
3. **Settings drift.** If the settings default differs from the project's last-used binding, the app prompts *apply the
   new provider/model to this project, or continue with the previously-used one?* — defaulting to **continue**. If the
   user changed settings mid-project, the prompt appears on the next run.

Settings-configured provider/models thus act purely as **defaults for new projects**; existing projects keep their
binding until the user confirms a change.

### Consequences

Positive:

- The dominant failure mode — silent mid-book model/provider change — is designed out.
- Users keep a single settings surface for defaults without it clobbering running work.
- No inference is ever issued against an unverified or missing provider/model.
- The confirm dialogs make every change explicit and auditable in the project's last-used snapshot.

Negative:

- Extra persistence (per-project binding + snapshot) and extra resume-time UI (two confirm dialogs).
- A user who *wants* every project to follow settings must confirm the change per project.

Neutral:

- Verification reuses the existing three-stage provider verification machinery (connection → models → inference).
- The binding stores model **ids/references**, never secrets (credentials remain references per ADR-0006).

## Pros and cons of the options

### Option A — Always use current settings

Pros: no per-project state; trivial. Cons: the exact regression this ADR exists to prevent — changing settings silently
changes an in-flight book's model.

### Option B — Per-project binding + confirm (chosen)

Pros: prevents mid-run regression; settings stay useful as defaults; explicit, minimal prompts; no unverified inference.
Cons: more persistence and two resume dialogs.

### Option C — Freeze permanently

Pros: absolute consistency. Cons: too rigid — a genuinely unavailable provider or a deliberate user change becomes
impossible without editing data by hand.

## Links

- Design decisions: DD-31 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-31-per-project-provider-model`)
- Spec clauses: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`,
  `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`,
  `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md#projects`,
  `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`
- Related ADRs: ADR-0005 (provider abstraction), ADR-0006 (credentials as reference)
- Stories: none yet
