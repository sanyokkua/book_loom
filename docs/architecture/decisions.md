# Architecture decisions index

This page groups the Architecture Decision Records (ADRs) that shape BookLoom's design by theme, with a one-line reason for each and, where the ADR names one, what would falsify it.

The ADRs live in `docs/spec_baseline/adr/` and are permanent: a number is never reused, and a superseded ADR is kept for history. The full list with status and the design-decision (DD) each ADR backs is in the [ADR README](../spec_baseline/adr/README.md). Status below is as of the current code; "falsifier" is quoted from the ADR in short form. To add an ADR, see the template in [template.md](../spec_baseline/adr/template.md).

## Platform, build and tooling

| ADR | Decision | Why |
|---|---|---|
| [0001](../spec_baseline/adr/ADR-0001-language-and-ui-toolkit.md) | Java and JavaFX | One desktop toolkit with native packaging. The JavaFX version pin is superseded by 0019. |
| [0002](../spec_baseline/adr/ADR-0002-build-tool-gradle.md) | Gradle with the Kotlin DSL | Multi-module build with dependency locking and shared conventions. |
| [0019](../spec_baseline/adr/ADR-0019-javafx-26-for-built-in-headless-testing.md) | JavaFX 26 | Its built-in headless platform lets UI tests and the packaging smoke run without a display on every OS. |
| [0014](../spec_baseline/adr/ADR-0014-lombok-hybrid.md), [0024](../spec_baseline/adr/ADR-0024-lombok-private-noargs-constructor.md) | Records for data, Lombok only on services | Immutable carriers stay records; boilerplate (constructors, loggers) is removed from services only. |
| [0021](../spec_baseline/adr/ADR-0021-modules-under-a-single-parent-directory.md) | Code directories under `modules/` | One parent directory; Gradle project names unchanged. |
| [0020](../spec_baseline/adr/ADR-0020-public-domain-license-exception-for-aopalliance.md) | Public Domain allowed on the license gate for one dependency | A Guice transitive dependency would otherwise fail the license check. |
| [0011](../spec_baseline/adr/ADR-0011-app-icon-and-branding.md) | One master icon, per-OS derivation | A single source for all platform icons. |

## Process

| ADR | Decision | Why |
|---|---|---|
| [0016](../spec_baseline/adr/ADR-0016-openspec-delivery-tracking.md) | OpenSpec changes replace stories | Delivery is tracked per change; generated traceability is never built. |
| [0017](../spec_baseline/adr/ADR-0017-infrastructure-first-delivery-order.md), [0023](../spec_baseline/adr/ADR-0023-backend-complete-milestone-and-backlog-interstitials.md) | Infrastructure-first order; backend-complete milestone | Prove the whole pipeline headlessly before the UI depends on it. |
| [0018](../spec_baseline/adr/ADR-0018-requirement-id-canonical-form.md) | Canonical requirement-id form | Stable identifiers across documents. |
| [0032](../spec_baseline/adr/ADR-0032-tests-are-the-evidence.md) | Tests are the evidence | A checkbox is a claim; a test is proof. No requirement-id tracking. |

## Architecture and runtime

| ADR | Decision | Why | Falsifier |
|---|---|---|---|
| [0033](../spec_baseline/adr/ADR-0033-bound-chat-model-and-pausable-translation-job.md) | The engine gets a chat model already bound to a provider and model, and runs as a pausable job | The engine never names a provider; pause, resume and cancel are methods of one handle. | Its first falsifier was met when per-model settings arrived; ADR-0047 resolves them inside `:llm`. |
| [0008](../spec_baseline/adr/ADR-0008-inference-concurrency-gate.md) | Single-flight inference gate with typed retry | A local backend should receive one request at a time, and every caller must contend on the same gate. | |
| [0034](../spec_baseline/adr/ADR-0034-storage-ports-with-in-memory-adapters.md) | Working state behind storage ports, with in-memory adapters | Ports first, so the pipeline and tests do not wait for SQLite. Still used by the command line and tests. | The SQLite adapter cannot implement a port without changing its signature, or one commit has to span two transactions. |
| [0022](../spec_baseline/adr/ADR-0022-busy-error-code-covers-the-single-instance-lock.md), [0039](../spec_baseline/adr/ADR-0039-import-refusal-kind-is-data.md) | `ErrorCode` stays at fifteen constants | New cases are data (a refusal kind), not new codes. | |
| [0015](../spec_baseline/adr/ADR-0015-app-environment-and-paths.md) | Per-OS paths resolved first, dev and prod kept apart | Data and logs follow platform conventions, and resolution runs before logging and SQLite. | |

## Persistence

| ADR | Decision | Why | Falsifier |
|---|---|---|---|
| [0009](../spec_baseline/adr/ADR-0009-persistence-sqlite.md) | SQLite with JDBI; Flyway from the first release | Embedded, explicit SQL, crash-safe in WAL mode. Amended by 0043. | |
| [0043](../spec_baseline/adr/ADR-0043-state-in-sqlite-as-json-documents.md) | State as JSON documents; one schema file; a changed database is set aside, no migrations before release | There is no installed base to migrate yet, and documents grow without schema changes. | A screen needs a query the layout cannot answer in under 200 ms with 200 projects, or checkpoint writes exceed 10 percent of run time. Then hot fields become columns. |
| [0006](../spec_baseline/adr/ADR-0006-credentials-as-reference.md) | Credentials stored as a reference | The database holds an environment-variable name, never a secret. | |

## Offline, privacy and providers

| ADR | Decision | Why | Falsifier |
|---|---|---|---|
| [0010](../spec_baseline/adr/ADR-0010-offline-network-policy.md) | Offline network policy | No telemetry, no background fetches; the only outbound calls are user-triggered provider calls. Privacy is "private when used with local models". | |
| [0044](../spec_baseline/adr/ADR-0044-look-for-a-local-ai-program-at-first-launch.md) | One silent local model listing on the very first launch | A new user with a local server running sees it without configuring anything; exactly two loopback addresses, once. | The look reaches another address, repeats without being asked, or delays the first window by more than 1 second. |
| [0005](../spec_baseline/adr/ADR-0005-provider-abstraction.md) | Two client implementations: Ollama-native and OpenAI-compatible | Ollama needs its native API for context and options; callers see one port. | |
| [0047](../spec_baseline/adr/ADR-0047-server-engines-and-model-profiles.md) | Server engines and per-model profiles; the engine is stored on the provider | What a call needs (reasoning field, system role, samplers, window) depends on the engine and model, not just the dialect. | More than one wrong engine in the live matrix; more than about 8 version branches in `EngineTraits` in one release; an engine needing its own response parser. |
| [0012](../spec_baseline/adr/ADR-0012-per-project-provider-model-binding.md) | Per-project provider and model binding, with preflight | A resumed project continues on the same model unless the person knowingly changes it. | |
| [0013](../spec_baseline/adr/ADR-0013-response-handling-contract.md) | JSON-first, tolerant response handling | Models ignore structure, emit reasoning or fence output; ask for structure but never depend on it. | |

## Translation pipeline

| ADR | Decision | Why | Falsifier |
|---|---|---|---|
| [0046](../spec_baseline/adr/ADR-0046-three-stages-one-repair-never-worse.md) | Three stages, one repair, an edit stays only if no check got worse | Fewer calls per failing segment on a slow local model, and a repair can never make a segment worse. Supersedes 0007 and 0038. | Paired grading of changed paragraphs shows more worse than better; or more than 1 percent of segments need a second or third round. |
| [0035](../spec_baseline/adr/ADR-0035-export-is-a-separate-action.md) | Export is a separate action; a run ends when translation ends | A stopped or partial run can still be exported, and export makes no model call. | People finish a run and lose the translation, or an export during a run disagrees with later decisions. |
| [0036](../spec_baseline/adr/ADR-0036-review-modes-pause-the-run.md), [0045](../spec_baseline/adr/ADR-0045-review-is-a-dialog-and-the-review-mode-a-setting.md) | Review modes decide when the run pauses; review is an in-window dialog and the mode a setting | Unattended, Assisted and Manual differ only in pausing; review is a place, not a stage. | 0036: pausing loses work or blocks export. 0045: people regularly review while a run is translating, so the desk must sit beside the live card. |
| [0037](../spec_baseline/adr/ADR-0037-language-evidence-from-metadata-only.md), [0042](../spec_baseline/adr/ADR-0042-auto-source-language.md) | Source language from metadata only; the source may be Auto | The app never guesses a language from text; with Auto the model identifies it and the code uses generic rules. | 0037: wrong-language translations are common enough that people rely on review. 0042: the flagged share under Auto exceeds 1.5 times that of a chosen language, or the model misidentifies 2 or more of 20 books. |
| [0007](../spec_baseline/adr/ADR-0007-automatic-first-tiered-pipeline.md), [0038](../spec_baseline/adr/ADR-0038-judge-a-chunk-translate-a-segment.md) | Superseded by 0046 | Kept for history: the earlier tiered pipeline and per-chunk judging. | |

## Document model and round trip

| ADR | Decision | Why | Falsifier |
|---|---|---|---|
| [0003](../spec_baseline/adr/ADR-0003-document-skeleton-segment-model.md) | Document as skeleton plus ordered segments | Only text changes, so structure survives by construction. | |
| [0004](../spec_baseline/adr/ADR-0004-formats-scope-epub-fb2-md-txt.md) | Formats: EPUB, FB2, Markdown, TXT | The supported scope, same-format export. | |
| [0025](../spec_baseline/adr/ADR-0025-reassembly-replaces-run-inner-content.md) | Reassembly replaces a run's inner content | A segment spans inline markup, not a single text node. | |
| [0026](../spec_baseline/adr/ADR-0026-drm-adjudication-by-encrypted-resource.md) | DRM judged by what is encrypted | Font obfuscation must not be mistaken for DRM. | |
| [0027](../spec_baseline/adr/ADR-0027-structural-block-segmentation.md) | A translatable block is recognised structurally | A tag whitelist misses real books. | |
| [0028](../spec_baseline/adr/ADR-0028-xhtml-self-closed-raw-text-repair.md) | jsoup stays the EPUB XHTML parser, with one pre-parse normalisation | Fixes self-closed raw-text elements without changing parser. | |
| [0029](../spec_baseline/adr/ADR-0029-transcode-to-utf8-on-unrepresentable-target-text.md), [0030](../spec_baseline/adr/ADR-0030-synthesize-a-missing-epub-mimetype-entry-on-write.md) | Transcode to UTF-8 when the source charset cannot hold the target; synthesise a missing `mimetype` | Produce a valid book rather than refuse. | |
| [0031](../spec_baseline/adr/ADR-0031-masked-text-is-character-data.md) | Masked text is character data | Restored fragments are composed, not substituted into markup strings. | |
| [0040](../spec_baseline/adr/ADR-0040-placeholder-pairs-keep-their-order.md) | Paired placeholders keep order and nesting; atomic ones may move | Formatting must land on the right words. | Legitimate translations are flagged because a language must move a phrase across another pair. |
| [0041](../spec_baseline/adr/ADR-0041-translatable-attribute-values.md) | Image `alt` values and other listed attribute values are translatable | Accessibility text is book text. | A translated attribute value breaks a reading system. |

## See also

- [Overview](overview.md), [Document engine](document-engine.md), [UI](ui.md)
- [ADR README](../spec_baseline/adr/README.md), [ADR template](../spec_baseline/adr/template.md)
- [Design decisions (DD-NN)](../spec_baseline/specification/00_Foundation/04_DESIGN_DECISIONS.md)
- [Getting started](../development/getting-started.md)
