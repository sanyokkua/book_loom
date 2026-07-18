# ADR-0006 — Store credentials as a reference, never the secret value

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

Most providers are local and need no secret, but an OpenAI-compatible remote endpoint may require an API key. The
application persists provider configurations in a local SQLite database and can export or back up its data. It also
writes logs. A provider configuration therefore raises the question of where the secret lives, if anywhere, and how it
is kept out of the database, out of exports, and out of logs.

The product is local-first with no backend and no account; the user already trusts their own machine and their own
secret store. The design must avoid becoming a second, weaker place where plaintext secrets accumulate. This ADR fixes
how credentials are stored and resolved.

## Decision drivers

- **Never persist a plaintext secret** in the application database.
- **Never leak a secret** into exports, backups, or logs.
- **Resolve at call time**, so the live secret exists only transiently in memory when a request is made.
- **Lean on the OS/user's existing secret management** rather than reinventing key management.
- **Simplicity and auditability.** The storage model should be trivial to reason about and to prove safe in tests.
- **No custom crypto burden.** Avoid owning an encryption scheme and its key lifecycle if a better option exists.

## Considered options

- **Option A — Encrypt the secret in the database.** Store the ciphertext plus manage an encryption key.
- **Option B — Store the secret in the OS keychain** and keep an entry handle.
- **Option C — Store only an environment-variable name** and read the secret from the environment at call time.
- **Chosen shape — reference-only, unifying B and C:** persist only a *reference* (an OS-keychain entry name or an
  environment-variable name), never the secret itself, and resolve it at call time.

## Decision outcome

Chosen: **store credentials as a reference only** — either the name of an OS-keychain entry or the name of an
environment variable — and resolve the actual secret at the moment a request is made. The database, exports, and backups
contain a harmless reference string, never a secret. The resolved secret lives only transiently in memory for the
duration of the call and is never written to logs (logging redaction and the never-log-secrets rule apply).

This is deliberately the union of Options B and C rather than a single mechanism: the OS keychain suits users who want
the secret managed by the platform, and the environment-variable reference suits headless, containerized, or scripted
setups. Both share the same invariant — the application stores a pointer, not the payload. Option A (encrypt-in-DB) is
rejected because it would make the application the custodian of an encryption key with no meaningfully safer place to
keep it than the user's own OS secret store, adding custom-crypto risk for no real gain.

### Consequences

Positive:

- No plaintext secret is ever persisted; exports and backups are safe to share by construction.
- The live secret exists only transiently in memory at call time.
- The application owns no encryption key and no key-rotation lifecycle.
- The invariant ("only a reference is stored") is simple to state and to test.

Negative:

- The user must set up the referenced secret out-of-band (create a keychain entry or export an environment variable);
  the app cannot fully self-configure a remote key.
- A dangling reference (deleted keychain entry, unset variable) surfaces as a resolution error at call time rather than
  at configuration time, so the failure is later and must be reported clearly.

Neutral:

- Two reference kinds (keychain, env var) must both be supported and tested.
- Provider verification must account for a not-yet-resolvable reference when testing a draft configuration.

## Pros and cons of the options

### Option A — Encrypt the secret in the database

Pros:

- The secret is available entirely within the app with no external setup.
- Single storage location for all configuration.

Cons:

- The app must generate, store, and protect an encryption key — with no place to keep it that is safer than the OS
  secret store the reference approach already uses.
- Custom-crypto and key-lifecycle risk; a compromised key exposes every stored secret.
- Exports/backups now contain ciphertext that is only as safe as the key handling, a step backward from storing nothing
  sensitive at all.

### Option B — OS keychain entry

Pros:

- The platform-native secret store handles encryption and access control.
- Nothing sensitive in the app database.

Cons:

- Keychain APIs and availability differ per OS; headless/CI environments may lack one.

### Option C — Environment-variable name

Pros:

- Trivial, portable, and ideal for headless, containerized, or scripted use.
- Nothing sensitive in the app database.

Cons:

- The secret's at-rest safety depends on how the user manages their environment; a variable can end up in a shell
  history or a process listing if the user is careless.

### Chosen — reference-only (B ∪ C)

Pros:

- Combines the strengths of B and C; the user picks the mechanism that fits their setup.
- One clean invariant: store a pointer, never a payload; safe exports by construction; no app-owned crypto.

Cons:

- Requires out-of-band setup of the referenced secret and clear reporting of unresolved references.

## Links

- Design decisions: DD-11 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-11-credential-as-reference`)
- Spec clauses: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`,
  `docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`,
  `docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`
- Stories: none yet
