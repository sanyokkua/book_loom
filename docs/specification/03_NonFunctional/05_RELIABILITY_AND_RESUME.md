**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/03_NonFunctional/01_QUALITY_ATTRIBUTES.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`, `docs/specification/02_Architecture/09_ERROR_HANDLING.md`,
`docs/specification/02_Architecture/10_DI_AND_LIFECYCLE.md`,
`docs/specification/02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md`, `docs/adr/ADR-0009-durability.md`

# Reliability and Resume

A book translation is a long-running local job. Reliability is the second-ranked attribute
(`01_QUALITY_ATTRIBUTES.md#priorities`): the durability guarantee is **process-crash-safe and forced-quit-safe** — on OS
crash / power loss **at most the last in-flight commit may be lost, never earlier accepted work** — and relaunch must
continue where it stopped (DD-20, ADR-0009).

## requirements {#requirements}

| ID         | Requirement                                                                                                                                                                                                                                                                           |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NFR-REL-01 | Every accepted/revised segment is a **crash-safe checkpoint** — durably committed before the run advances past it. The guarantee is **process-crash-safe and forced-quit-safe**; on OS crash / power loss at most the last in-flight commit may be lost, never earlier accepted work. |
| NFR-REL-02 | Checkpoint writes are **atomic** (single WAL transaction, `synchronous=NORMAL`); a crash mid-write leaves the DB consistent, losing at most the last in-flight commit.                                                                                                                |
| NFR-REL-03 | **Resume-on-launch** — reopening a project continues from the **first `PENDING`** segment; no accepted work is redone. `ACCEPTED`/`REVISED` are terminal; `FLAGGED` is **terminal-for-run** (in the review queue, excluded from auto-resume).                                         |
| NFR-REL-04 | **Pause/resume** within a session stops at a chunk boundary and continues without loss.                                                                                                                                                                                               |
| NFR-REL-05 | **Single instance** — one process per data dir (`FileLock` on `bookloom.lock`), so two instances never corrupt shared state.                                                                                                                                                          |
| NFR-REL-06 | The pipeline is **self-healing**: a chunk that fails QA repairs (directed-fix / reflect→improve) up to the dial budget, and only then is `FLAGGED` — a bad chunk never aborts the run.                                                                                                |
| NFR-REL-07 | Provider errors mid-run yield **partial results** (accepted segments retained) plus a typed `AppError`; the user can resume after fixing the provider.                                                                                                                                |

## crash-safe-checkpoints {#crash-safe-checkpoints}

Each chunk's outcome — accepted/revised segments, updated TM, name-dictionary entries, and (at chapter end) the rolling
summary — is committed in one transaction before the engine moves on
(`02_Architecture/05_PIPELINE_ENGINE.md#tiered-loop`). Because SQLite runs in WAL with `synchronous=NORMAL`, a committed
transaction is process-crash-safe and forced-quit-safe and a crash cannot tear it; on OS crash / power loss at most the
last in-flight commit may be lost, never earlier accepted work (`06_DATA_MODEL_SQLITE.md#storage-conventions`, DD-20,
ADR-0009).

## atomic-writes {#atomic-writes}

- All state that must move together (segment + its status + confidence) is written in a single transaction; there is no
  window where a segment is `ACCEPTED` but its target is absent.
- Export writes to a temporary file and atomically renames to the destination, so a partial file never overwrites a
  prior good output.
- Settings and provider config updates are transactional KV writes (`06_DATA_MODEL_SQLITE.md#settings`).

## resume-on-launch {#resume-on-launch}

On opening a project the engine reconstructs job state purely from `segments.status`:

- terminal segments (`ACCEPTED`/`REVISED`) are kept as-is,
- `FLAGGED` segments remain flagged (available for review),
- the first `PENDING` segment is where translation resumes. No in-memory-only progress is trusted; the DB is the single
  source of truth. A run interrupted at chunk *k* re-does only chunk *k*.

## single-instance {#single-instance}

An exclusive `FileLock` on **`bookloom.lock`** in the data dir guarantees one writer. A second launch detects the held
lock, **shows a "BookLoom is already running" dialog, and exits** — it does not focus, signal, or raise the running
instance, and there is no IPC (`02_Architecture/10_DI_AND_LIFECYCLE.md#single-instance-lock`,
`02_Architecture/11_APP_ENVIRONMENT_AND_PATHS.md#edge-cases`). This prevents two engines racing on the same SQLite file.

## self-healing-pipeline {#self-healing-pipeline}

The tiered loop treats failure as normal flow, not an exception: hard tag-gate failures and QA findings drive targeted
repairs; vague quality drives reflect→improve; exhaustion flags (~1%). Cancellation is cooperative and safe
(`08_THREADING_CONCURRENCY.md#cancellation`). The result is that a full-book run reliably reaches a terminal state for
every segment — accepted, revised, or flagged — under normal and adverse conditions (`EC-RESUME-*`, `EC-CONCUR-*`,
`EC-NET-*`).

## resume-edge-cases {#resume-edge-cases}

Resume is defined not just for the clean case but for the ways a project's on-disk state can diverge from what the DB
expects. Each row is a typed, testable outcome (`ErrorCode` per `02_Architecture/09_ERROR_HANDLING.md`).

| ID              | Situation                                                                                                                                                                            | Detection                                                                                             | Behaviour                                                                                                                                                                                                                      |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **EC-RESUME-1** | **Changed source hash** — the imported file was edited since the skeleton was built (the document-level `projects.content_hash`, or a per-segment `source_hash`, no longer matches). | Recompute the document `content_hash` on open; compare.                                               | Refuse silent resume: warn that the source changed and offer re-import (rebuild skeleton, losing prior translation) vs. continue against the stored skeleton. Never re-derive the skeleton semantically under an existing run. |
| **EC-RESUME-2** | **Missing skeleton blob** — the on-disk/blob skeleton for a unit is absent or unreadable while its segment rows exist.                                                               | Skeleton load fails at resume.                                                                        | Typed startup/resume error (`validation`); the project cannot resume until re-imported. Accepted segment text in the DB is preserved so nothing already translated is lost.                                                    |
| **EC-RESUME-3** | **Partial chunk** — the process died mid-chunk, so some segments in the chunk are `PENDING` while earlier ones committed.                                                            | `segments.status` scan finds the first `PENDING`.                                                     | Resume re-does **only** that chunk from its first `PENDING` segment; already-`ACCEPTED`/`REVISED` segments are untouched (per-chunk atomicity, NFR-REL-02).                                                                    |
| **EC-RESUME-4** | **Moved data dir** — the DB/skeleton was moved to a new path, or `BOOKLOOM_DATA_DIR` now points elsewhere.                                                                           | Path resolution opens whatever DB lives at the resolved data dir (`11_APP_ENVIRONMENT_AND_PATHS.md`). | Resume is keyed to the DB, not an absolute path: a project resumes normally as long as its rows and skeleton blobs travel **together** with the data dir; a DB present without its skeleton blobs degrades to EC-RESUME-2.     |
| **EC-RESUME-5** | **Provider unavailable / unbound** — the project's `provider_id` is null (`ON DELETE SET NULL`) or the bound model is not offered.                                                   | Preflight verify at resume (`ErrorCode.modelUnavailable`, `06_DATA_MODEL_SQLITE.md#resume-support`).  | Take the **"provider unavailable → prompt"** path: prompt the user to pick/repair a provider before continuing; `current_provider` is never silently substituted.                                                              |

## verification {#verification}

Reliability is validated by kill-and-resume tests (interrupt mid-chunk, relaunch, assert no accepted segment lost and
translation resumes at the right segment), an atomicity test (simulated crash between segment write and status write is
impossible by construction), and a single-instance test (second launch does not open a second writer).
