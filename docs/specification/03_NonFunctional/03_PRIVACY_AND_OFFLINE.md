**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/03_NonFunctional/01_QUALITY_ATTRIBUTES.md`,
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`

# Privacy and Offline

Privacy is the product promise and the top-ranked quality attribute (`01_QUALITY_ATTRIBUTES.md#priorities`). Everything
stays on the user's machine; the only outbound traffic is the user's own **user-triggered provider communication** —
inference, model discovery, and verification — with the provider they configured.

## offline-invariant {#offline-invariant}

| ID             | Requirement                                                                                                                                                                                                                                          |
|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-OFFLINE-01 | Import, parsing, masking, QA, glossary/TM/summary, persistence, review, and export function with **no network connection**.                                                                                                                          |
| NFR-OFFLINE-02 | The **only** outbound network traffic the application makes is **user-triggered provider communication** — an inference, model-discovery, or verification request — to the configured provider (local Ollama/LM Studio or an OpenAI-compatible URL). |
| NFR-OFFLINE-03 | No background, scheduled, or startup network activity: no update check, no license check, no telemetry beacon, no crash upload, no font/asset CDN fetch.                                                                                             |
| NFR-OFFLINE-04 | Provider verification (connection/models/inference) counts as user-triggered — it runs only when the user clicks Test or starts a run.                                                                                                               |

The offline invariant is enforced in review and by an ArchUnit rule limiting `java.net.http` usage to `:llm`, plus the
Definition-of-Done gate "no new background/unsolicited network calls."

## no-telemetry {#no-telemetry}

| ID          | Requirement                                                                                                              |
|-------------|--------------------------------------------------------------------------------------------------------------------------|
| NFR-PRIV-02 | No analytics, usage metrics, or telemetry of any kind are collected or transmitted.                                      |
| NFR-PRIV-03 | No third-party SDKs that phone home are bundled (dependency license + behavior gate, `05_Dependencies/03_LICENSING.md`). |
| NFR-PRIV-04 | Logs stay local (per-OS log dir); they are never uploaded.                                                               |

## secrets-never-stored {#secrets-never-stored}

| ID          | Requirement                                                                                                                                                                                                                                                    |
|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-PRIV-05 | API credentials are stored **only as a reference** (env-var name or OS-keychain id), never as plaintext, in any table, config file, or export (`02_Architecture/04_LLM_INTEGRATION.md#credentials-as-reference`, `06_DATA_MODEL_SQLITE.md#secret-references`). |
| NFR-PRIV-06 | Secrets are resolved at request time, used for that call, and never logged. MDC is cleared in `finally`; parameterized logging never interpolates a secret.                                                                                                    |
| NFR-PRIV-07 | `AppError.details` is built from a **safe-details allowlist** (`09_ERROR_HANDLING.md#safe-details-allowlist`); no secret, `Authorization` header, or raw body ever reaches the UI or logs.                                                                     |

## outbound-scope {#outbound-scope}

| ID          | Requirement                                                                                                                                                                                   |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-PRIV-08 | Book content leaves the machine only as the body of a user-triggered inference request to the user's chosen endpoint — nowhere else. For a local provider it never leaves the machine at all. |
| NFR-PRIV-09 | The user always sees which provider/endpoint is current before a run (Settings + the Translating screen), so any outbound destination is explicit and chosen.                                 |

## verification {#verification}

These requirements are testable: a network-egress test (or WireMock with a deny-all default) asserts that no request is
issued during import, parse, QA, persistence, or export, and that the sole request during a run targets the configured
provider URL. A secret-leak test asserts no credential value appears in logs or `AppError.details`.
