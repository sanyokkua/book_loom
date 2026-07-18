**Status:** Final **Owner:** architect **Audience:** architect, coder, tester **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/03_NonFunctional/01_QUALITY_ATTRIBUTES.md`,
`docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md`

# Performance

Performance targets are set against typical local hardware and the reality that the throughput ceiling is the user's own
model, not the application. The app's job is to stay responsive and never be the bottleneck for anything except
generation itself.

## targets {#targets}

| ID          | Requirement                                                    | Target                                                                          |
|-------------|----------------------------------------------------------------|---------------------------------------------------------------------------------|
| NFR-PERF-01 | UI responsiveness — no FX-thread stall during any operation    | frame budget kept; no operation blocks the FXAT (`08_THREADING_CONCURRENCY.md`) |
| NFR-PERF-02 | Cold startup to first interactive screen                       | ≤ 3 s on a mid-range machine (excludes model warm-up)                           |
| NFR-PERF-03 | Large-file parse — a 10 MB FB2 into the skeleton+segment model | ≤ 5 s, streamed, off the FX thread                                              |
| NFR-PERF-04 | Import + language detection for a typical EPUB                 | ≤ 2 s                                                                           |
| NFR-PERF-05 | Steady-state heap for an open large project                    | ≤ ~512 MB app overhead excluding the OS/model process                           |
| NFR-PERF-06 | Per-segment checkpoint write                                   | ≤ ~5 ms amortized (WAL, batched fsync)                                          |
| NFR-PERF-07 | Review/glossary list rendering at 10k+ rows                    | virtualized; scroll stays smooth                                                |

## startup {#startup}

Startup is two-phase (`02_Architecture/10_DI_AND_LIFECYCLE.md#two-phase-init`): construct the object graph (fast, no
IO), then open the DB and run migrations. The first scene shows as soon as settings load; project data streams in
afterward. Migrations are incremental, so normal launches apply zero pending scripts.

## large-file-parsing {#large-file-parsing}

- Parsing runs as a `Task` on a worker thread; the UI shows progress.
- XML/EPUB entries are read via streaming/pull parsing where feasible; the skeleton holds structure once, segments
  reference it — the full source is not duplicated per segment.
- A 10 MB FB2 (`EC-FB2-*` scale) parses within `NFR-PERF-03`; memory stays bounded because the skeleton is a single tree
  and segments carry only their own inner text + placeholder map.

## memory {#memory}

- Segment text is the dominant footprint; targets/masks are stored, but large books stream accepted segments to SQLite
  and need not keep every `targetInner` resident.
- No whole-book byte buffer is held during translation — only the skeleton, the active chunk's segments, and the capped
  context window (preceding-target ~3 blocks, rolling summary).

## throughput {#throughput}

- Expected generation throughput comes from a local ~4B-active MoE model: end-to-end book time is dominated by
  tokens/second of that model, not by app overhead. The single-flight gate means one generation at a time by design
  (`02_Architecture/04_LLM_INTEGRATION.md#inference-gate`).
- The app overlaps non-generation work (context assembly for the next chunk, QA of the previous, checkpoint writes) with
  the in-flight call so the model is the only serialized stage.
- The quality dial trades throughput for quality explicitly (chunk size, repair budget, judge, backward revision —
  `05_PIPELINE_ENGINE.md#quality-dial`); FAST minimizes calls per chunk.

## virtualized-lists {#virtualized-lists}

`TableView`/`TreeView` for the glossary, flagged queue, and structure tree are virtualized (cell reuse) so a book with
thousands of segments/terms scrolls without materializing every row (`NFR-PERF-07`). No `ObservableList` is fully
rendered eagerly.
