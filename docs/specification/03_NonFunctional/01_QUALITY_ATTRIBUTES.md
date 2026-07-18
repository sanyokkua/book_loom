**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, reviewer **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/03_NonFunctional/02_PERFORMANCE.md`,
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`,
`docs/specification/03_NonFunctional/05_RELIABILITY_AND_RESUME.md`,
`docs/specification/02_Architecture/01_SYSTEM_ARCHITECTURE.md`

# Quality Attributes

This document states the non-functional priorities for BookLoom and the tie-breaking order used when attributes
conflict. Each area is elaborated in its own file with `NFR-<AREA>-NN` requirements.

## priorities {#priorities}

Ranked; earlier wins a genuine conflict.

1. **Privacy & Offline** (`NFR-PRIV-*`, `NFR-OFFLINE-*`) — the product promise. Never traded away. See
   `03_PRIVACY_AND_OFFLINE.md`.
2. **Reliability & Resume** (`NFR-REL-*`) — a multi-hour local run is **process-crash-safe and forced-quit-safe**; on OS
   crash / power loss at most the last in-flight commit may be lost, never earlier accepted work, and relaunch resumes.
   See `05_RELIABILITY_AND_RESUME.md`.
3. **Correctness / Fidelity** — structure-and-text-preserving (canonical-equal) round-trip (DD-43) and tag-integrity
   gates (architectural, `02_Architecture/03_DOCUMENT_MODEL.md`); a translation must never corrupt the book.
4. **Performance** (`NFR-PERF-*`) — responsive UI and acceptable large-file handling within local-hardware limits. See
   `02_PERFORMANCE.md`.
5. **Usability** (`NFR-USAB-*`) — automatic-first, one-dial operation; review is opt-in.
6. **Accessibility** (`NFR-A11Y-*`) — best-effort, non-normative guidance (WCAG 2.1 AA as an aspiration, not a
   release/merge gate). See `04_ACCESSIBILITY.md` (Informative).
7. **Portability** (`NFR-PORT-*`) — Windows, macOS, Linux from one codebase.
8. **Maintainability** (`NFR-MAINT-*`) — enforced module boundaries, quality gates.

## conflict-rules {#conflict-rules}

- **Privacy beats convenience.** No feature may add a background network call, telemetry, or crash reporter to improve
  UX or diagnosability.
- **Reliability beats throughput.** Per-chunk atomic checkpoints stay even though they cost write amplification;
  correctness of resume outranks raw speed.
- **Fidelity beats "nice output."** The skeleton is never regenerated to prettify output; structure preservation is
  non-negotiable.
- **Responsiveness beats batch speed.** The FX thread is never blocked to finish a batch faster.

## quality-requirements {#quality-requirements}

| ID             | Attribute       | Requirement (summary)                                                                                                                                |
|----------------|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-PRIV-01    | Privacy         | Only outbound traffic is user-triggered provider communication (inference, model discovery, verification) with the configured provider.              |
| NFR-OFFLINE-01 | Offline         | All non-inference functions work with no network.                                                                                                    |
| NFR-REL-01     | Reliability     | Accepted work is process-crash-safe and forced-quit-safe (on OS crash / power loss at most the last in-flight commit may be lost); resume-on-launch. |
| NFR-PERF-01    | Performance     | UI stays responsive; long work off the FX thread.                                                                                                    |
| NFR-USAB-01    | Usability       | A book translates end-to-end with one dial and no manual steps for ~99% of chunks (non-gating aspiration).                                           |
| NFR-A11Y-01    | Accessibility   | Advisory (non-normative): WCAG 2.1 AA is best-effort guidance, not a release/merge gate (`04_ACCESSIBILITY.md`).                                     |
| NFR-PORT-01    | Portability     | Runs on Windows/macOS/Linux from one build.                                                                                                          |
| NFR-MAINT-01   | Maintainability | Module boundaries enforced by JPMS + ArchUnit; quality gates green.                                                                                  |

Detailed, testable requirements live in the per-area files; this table is the index and the priority contract.
