# Offline & Privacy

Scope: all modules — a cross-cutting invariant. Spec: `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`. This is a Definition-of-Done gate for every change.

## MUST

- **MUST** make **no background, scheduled, or unsolicited network call** of any kind — no update checks, no analytics ping, no prefetch, no "phone home". — Rationale: the app is local-first by contract.
- **MUST** keep opening, parsing, masking, translating against a local model, and exporting a book working with **no network at all**; the expected outbound calls are **user-triggered provider communication (inference, model discovery, verification)** to the **configured** provider endpoint. Anything else that might use the network is **optional, safe (no fetch a document can steer — an XML DOCTYPE is answered from the bundled entity list), and degrades without an error** when the network or the resource is gone. — Rationale: the network is never required and never surprising.
- **MUST NOT** include telemetry, crash reporting to a remote service, usage metrics, or any third-party callback. — Rationale: no data leaves the machine.
- **MUST NOT** persist secrets anywhere — credentials are held as references (env-var name / OS keychain) and resolved at call time only; secrets never touch the DB, logs, `AppError`, or exported files. — Rationale: privacy invariant shared by `persistence-sqlite.md`, `llm-provider-integration.md`, `logging.md`, `error-envelope.md`.
- **MUST** bundle **all assets** (fonts, icons, stylesheets, i18n bundles, sample data) in the packaged app; nothing is fetched at runtime. — Rationale: the app works fully offline on first launch.

## SHOULD

- **SHOULD** make the configured endpoint explicit and visible to the user, so the one egress path is never hidden. — Rationale: transparency about the single network destination.
- **SHOULD** treat any newly added dependency's network behaviour as a review item — a library that phones home violates the invariant. — Rationale: transitive egress is still egress.
- **SHOULD** cover the invariant with a test/ArchUnit check that no non-`:llm` module opens `java.net.http`, and that `:llm` only calls the configured endpoint. — Rationale: the invariant is enforced, not trusted.

## Reject if

- Any code makes a network call that is not user-triggered provider communication (inference, model discovery, verification) to the configured provider.
- A background thread, timer, or startup path opens a socket for updates/telemetry/prefetch.
- Telemetry, remote crash reporting, or usage metrics are added.
- A secret is persisted, logged, embedded in an export, or held anywhere but as an env-var/keychain reference.
- The app fetches an asset at runtime instead of bundling it.
