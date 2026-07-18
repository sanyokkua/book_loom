**Status:** Final **Owner:** architect **Audience:** product, architect, engineering, QA **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/00_Foundation/03_PERSONAS_AND_USECASES.md`,
`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md`,
`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md`

# Vision and Scope

## product-vision {#product-vision}

BookLoom is a local-first, offline desktop application that translates whole books — EPUB, FB2, Markdown, and plain
text — using a general-purpose Large Language Model (LLM) that the user runs on their own machine (Ollama or LM Studio)
or reaches through any OpenAI-compatible endpoint. The application parses each book into a structured document tree,
translates only the visible text while keeping structure, images, fonts, and identifiers intact through a
structure-and-text-preserving (canonical-equal) round trip — exact bytes may differ under re-serialization (DD-43) — and
runs automatically end to end: the target reader completes a full-length book with no human interaction for roughly 99%
of the content (a non-gating aspiration, not a guarantee). A tiered quality pipeline (draft → deterministic checks →
LLM-as-judge → self-heal repair, with an optional whole-book consistency pass) keeps names, tone, and formatting
consistent. Only the small minority of segments the machine cannot clear on its own are flagged for optional
side-by-side review. Every byte of book content and every model call stays on the user's machine or their chosen
endpoint.

## value-propositions {#value-propositions}

| #  | Value proposition          | What it means for the user                                                                                                                                                                                                                                                     |
|----|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| V1 | Private by construction    | Book content and translations never leave the user's control; the only outbound traffic is user-triggered provider communication (inference, model discovery, verification) with the provider they configured. See `docs/specification/03_NonFunctional` privacy requirements. |
| V2 | Automatic-first            | The user opens a book, sets a short brief, and walks away; the pipeline drives the whole book to completion unattended.                                                                                                                                                        |
| V3 | Structure-faithful         | The translated book is structure-and-text-preserving (canonical-equal) against the original everywhere except the translated text nodes — images, fonts, IDs, and layout survive the round trip; exact bytes may differ under re-serialization (DD-43).                        |
| V4 | Consistent voice and names | A name/term dictionary, context-aware translation memory, and a rolling bilingual summary keep terminology and register stable across an entire book.                                                                                                                          |
| V5 | Bring-your-own model       | Any local or OpenAI-compatible general instruction model works; no vendor lock-in and no per-word cloud cost.                                                                                                                                                                  |
| V6 | Review only what matters   | Deterministic and judge-based quality gates surface just the small minority (~1%, a non-gating aspiration) of segments that need a human, in a focused side-by-side queue.                                                                                                     |

## target-users {#target-users}

Primary audiences are individuals who want a full book in another language without a cloud service or a professional
translation budget. Detailed personas are in `docs/specification/00_Foundation/03_PERSONAS_AND_USECASES.md`:

- Hobbyist reader who wants to read a foreign book in their own language.
- Indie / volunteer translator who wants a strong first draft and control over final quality.
- Language learner who wants a faithful bilingual rendering to study alongside the source.
- Self-publisher who wants a consistent, structure-preserving translation of their own work.

## in-scope {#in-scope}

| ID      | In scope                                                                                                                                                                                                    |
|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SC-IN-1 | Input formats: EPUB (2 and 3), FB2 (including `.fb2.zip`), Markdown, and plain text (TXT).                                                                                                                  |
| SC-IN-2 | Fully offline operation: no account, no telemetry, no background network; only user-triggered provider communication (inference, model discovery, verification) with the configured provider.               |
| SC-IN-3 | Automatic-first translation of the entire book with an optional, opt-in review of flagged segments.                                                                                                         |
| SC-IN-4 | Structure-and-text-preserving (canonical-equal) round-trip repackaging of the source format — structure, images, fonts, IDs, and all text preserved; exact bytes may differ under re-serialization (DD-43). |
| SC-IN-5 | Local LLM providers (Ollama, LM Studio, llama.cpp) and any OpenAI-compatible endpoint, chosen and verified by the user.                                                                                     |
| SC-IN-6 | A consistency stack: name/term dictionary, context-aware translation memory, and rolling bilingual summary.                                                                                                 |
| SC-IN-7 | Cross-platform desktop delivery (macOS, Windows, Linux) via unsigned native packages: macOS `.app`/`.dmg`, Windows portable app-image zip (no installer), Linux tar.gz/`.deb` (DD-24).                      |
| SC-IN-8 | Export in the book's original format only (shown as a read-only format label — no format chooser), plus optional glossary, bilingual, and quality-report side artifacts.                                    |

## out-of-scope {#out-of-scope}

| ID       | Out of scope                                                                   | Rationale                                                                                                                                                             |
|----------|--------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SC-OUT-1 | PDF and DOCX input/output                                                      | These formats do not offer a clean text/structure separation for structure-and-text-preserving (canonical-equal) round-tripping; see `DD-08`.                         |
| SC-OUT-2 | Cloud translation services and hosted proprietary models as a built-in default | Conflicts with the offline, local-first invariant (`DD-01`); OpenAI-compatible endpoints are user-configured, not bundled.                                            |
| SC-OUT-3 | User accounts, sign-in, sync, or server-side storage                           | The application is single-user and local; there is nothing to authenticate to.                                                                                        |
| SC-OUT-4 | Telemetry, analytics, or crash reporting to any remote endpoint                | Privacy invariant; diagnostics stay in local logs.                                                                                                                    |
| SC-OUT-5 | Code signing and notarization of packages; Windows installers (`.msi`/WiX)     | Explicitly not done; the application ships unsigned with documented "open anyway" steps for Gatekeeper/SmartScreen (`DD-24`).                                         |
| SC-OUT-6 | Real-time / interactive chat translation or general-purpose LLM chat           | The product translates document files, not conversations.                                                                                                             |
| SC-OUT-7 | Converting a book between formats on export (e.g. EPUB→PDF, FB2→EPUB)          | Export is format-preserving only; cross-format conversion is lossy for structure/metadata (`DD-30`).                                                                  |
| SC-OUT-8 | Embedding models, vector stores, or RAG retrieval                              | Consistency uses a dictionary, string-similarity translation memory, and a rolling summary — no vectors (`DD-18`); the app assumes no future embedding functionality. |
| SC-OUT-9 | UI languages beyond English and Ukrainian                                      | This version ships exactly two UI languages (`DD-34`); further languages are future work, not assumed here.                                                           |

## success-criteria {#success-criteria}

| ID        | Criterion                                                                                                                                                                                                                                                 |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SC-CRIT-1 | A supported book translates end to end unattended, with roughly 99% of chunks accepted without human input (a non-gating aspiration, not a release gate).                                                                                                 |
| SC-CRIT-2 | The exported book passes the round-trip golden test: canonicalized output equals canonicalized source with only translated text nodes and language metadata differing (structure-and-text-preserving, canonical-equal — DD-43; TXT compares exact bytes). |
| SC-CRIT-3 | No network activity occurs other than user-triggered provider communication (inference, model discovery, verification) with the configured provider.                                                                                                      |
| SC-CRIT-4 | Flagged segments are reviewable side by side, and export is available at any time regardless of remaining flags.                                                                                                                                          |
