# ADR-0010 — Enforce an offline invariant: the only outbound call is user-triggered inference

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The product's premise is that a user can translate their own books on their own machine without their content ever
leaving it. There is no account, no backend, and no cloud service. Users bring potentially sensitive or unpublished
manuscripts, and the trust proposition is simple: everything stays local.

Modern desktop applications routinely make network calls the user never asked for — telemetry, analytics, crash
reporting, update checks, license validation, font or asset fetches. Any such call would silently break the product's
core promise and would be nearly impossible to reason about after the fact. The system therefore needs an explicit,
testable invariant about what network activity is permitted at all. This ADR fixes that policy.

## Decision drivers

- **Content stays on the user's machine.** No book text or translation may be transmitted anywhere except where the user
  explicitly directs it.
- **No unsolicited network activity.** No telemetry, analytics, crash reporting, update pings, or background fetches.
- **User-directed inference is the sole exception.** Translation inherently requires calling the model the user
  configured — but only when the user starts a job or a diagnostic.
- **Testable and enforceable.** The invariant must be checkable in CI, not just stated in prose.
- **Auditable dependency behaviour.** Third-party libraries must not open their own connections behind the app's back.

## Considered options

- **Option A — No explicit policy.** Rely on the fact that the app has no intentional telemetry and hope dependencies
  behave.
- **Option B — Best-effort minimization.** Disable known telemetry and update checks, but allow incidental convenience
  calls (e.g. optional online help or asset fetches) where handy.
- **Option C — Hard offline invariant.** Define exactly one permitted outbound destination class — a user-triggered
  inference request to the configured provider — and forbid all other outbound network activity, enforced by
  architecture rules and tests.

## Decision outcome

Chosen: **Option C — a hard offline invariant.** The application performs no background or unsolicited network activity
of any kind: no telemetry, no analytics, no remote crash reporting, no automatic update checks, no license calls, no
incidental asset fetches. The *only* permitted outbound call is a user-triggered inference request to the provider the
user configured — issued when the user starts a translation run or runs a provider diagnostic — plus that provider's
model-discovery/verification calls, which are themselves user-initiated against the same configured endpoint.

This is stated as an architectural invariant and treated as part of every story's Definition of Done: no change may
introduce a new background or unsolicited network call. The HTTP surface is confined to the `:llm` provider layer, so
"who is allowed to touch the network" is a small, reviewable, boundary-testable area rather than something that could
appear anywhere. Logging stays local (see `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`), and
credentials are stored as references, never transmitted (see `docs/adr/ADR-0006-credentials-as-reference.md`).

### Consequences

Positive:

- The product's core promise — content never leaves the machine except where the user directs it — is guaranteed, not
  merely intended.
- The network surface is tiny and confined to one module, making it auditable and boundary-testable.
- Users can run the app fully offline (against a local model) with no degraded behaviour.
- The invariant gives a clear, binary review criterion: any new outbound call that is not user-triggered inference is a
  defect.

Negative:

- Conveniences that assume connectivity — auto-update, online help, remote crash diagnostics, telemetry-driven product
  decisions — are off the table and must be handled by other means (manual updates, bundled help, local logs).
- The team gets no automatic usage or crash signal; quality feedback relies on user-supplied local logs and reports.

Neutral:

- Dependencies must be vetted so none opens its own connections; this is an ongoing review responsibility.
- "User-triggered inference to the configured provider" (including its discovery/verification calls) is the precise,
  whitelisted exception that stories and tests reference.

## Pros and cons of the options

### Option A — No explicit policy

Pros:

- Nothing to build or enforce; relies on current behaviour being clean.

Cons:

- Offers no guarantee; a future dependency or feature can silently add a call that breaks the core promise.
- Nothing to test against, so regressions are invisible until a user notices traffic.
- Undermines the trust proposition the product is sold on.

### Option B — Best-effort minimization

Pros:

- Blocks the obvious offenders (telemetry, update pings) while keeping some online conveniences.

Cons:

- "Incidental" calls are exactly the ones that leak content or metadata and erode trust.
- A fuzzy boundary is not testable; reviewers cannot apply a clear rule.
- Invites scope creep back toward normal always-online app behaviour.

### Option C — Hard offline invariant (chosen)

Pros:

- Delivers the core promise as a guarantee, with a single whitelisted exception.
- Small, confined, auditable network surface; enforceable in CI and in every story's DoD.
- A binary, unambiguous review rule.
- Full offline operation against a local model.

Cons:

- Sacrifices connected conveniences (auto-update, online help, telemetry, remote crash reports).
- No automatic usage/crash feedback loop for the team.

## Links

- Design decisions: DD-01 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-01-local-first-offline`)
- Spec clauses: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`,
  `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`,
  `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`
- Stories: none yet
