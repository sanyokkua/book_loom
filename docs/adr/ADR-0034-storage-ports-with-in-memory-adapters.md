# ADR-0034 — Put the working state of a translation behind storage ports, with in-memory adapters until SQLite

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner (plan approval for `complete-translation-workflow`)

## Context and problem statement

Until now a run kept everything it knew — each segment's decision, the preceding-target window — inside the job
object, and threw it away when the job ended. The change `complete-translation-workflow` adds a glossary, a
translation memory, a rolling summary, deferred-resolution items, review edits and an export that runs after the
translation instead of as its last stage. Every one of those needs a place where one component writes and another
reads later: the review panel edits a segment the job decided, the export reads what the run and the review left, a
retry rebuilds the context a segment first saw.

The specification puts all of it in SQLite (`02_Architecture/06_DATA_MODEL_SQLITE.md#tables`, seams F6/F7 in
`00_Foundation/06_IMPLEMENTATION_STAGES.md#forward-compatibility-seams`), and ADR-0009 chose SQLite + Flyway + JDBI.
The owner decided that this change builds no database: "we define only in-memory storages for now", but "the
interfaces can be developed now" because the Projects feature will store intermediate results in the database.

## Decision

- **Ports in `:api`, adapters in `:persistence`.** The working state is reached only through repository ports in
  `ua.bookloom.api.persistence` — project, segment, glossary, translation memory, rolling summary, deferral and run
  repositories, plus one checkpoint port that commits a chunk's decisions, memory entries and deferrals together.
  The records they carry live in `ua.bookloom.api.project`. Every method returns `Result<T>`.
- **The shape follows the normative tables.** A port's records map column-for-column onto
  `06_DATA_MODEL_SQLITE.md#ddl-normative`, so the SQLite adapter is a second implementation, not a redesign. The
  additions are a **machine target stored apart from the user's edit**, each in plain and in masked form: "Revert to
  machine target" (FR-REVIEW-08) and "a flagged segment exports its machine target" both need it, and the review editor
  shows the masked form with its placeholders, so an edited segment can be edited again; a **reviewed** mark set by a
  person's action; a run record carrying the run's **current state** (running, paused, then its end), which review
  reads to refuse a retry only while a run is running; and a **context snapshot that
  holds the texts** a draft saw (preceding targets, injected glossary entries, memory hits, summary, style sheet), so a
  retry replays them without looking anything up. The DDL clause gains these columns in the same change (no migration
  has been written yet, so the baseline is still editable).
- **Commits hold decided segments.** The checkpoint port applies a set of a chunk's decided segments — with their
  memory entries, deferrals and glossary additions — all or nothing. The decided prefix of a chunk is committed before
  every pause and at a stop or the end, and the rest at the chunk's end; undecided drafts are never committed. In memory
  each commit is one atomic update; in SQLite it becomes the per-chunk transaction DD-20 requires, one per commit.
- **In-memory adapters now.** `ua.bookloom.persistence.memory` implements every port over concurrent maps with atomic
  per-key updates, bound as singletons. Nothing survives a restart, and the application says so where it matters.
- **One book per session, keyed as if there were many.** Every record carries a project id, so nothing in the ports
  assumes a single book, although the window only ever opens one.

## Considered options

- **Keep the state inside the job and pass it around.** Rejected: the review panel and the export outlive the job, and
  the next change would have to pull every structure back out.
- **Build the SQLite adapter now.** Rejected by the owner for this change; it doubles the scope and forces the
  settings and projects decisions that belong to their own changes.
- **A single "session" object in `:pipeline` with no ports.** Rejected: `:ui` and `:app` would bind to a concrete
  pipeline class, which ArchUnit `ports-not-concretes` forbids, and the database would have to be retrofitted behind it.

## Consequences

- **Positive:** review, export, retry and resume-after-stop all read one source of truth; the SQLite change becomes an
  adapter plus migrations.
- **Negative:** closing the application loses the work. Accepted for this change, and stated on the screens that could
  otherwise suggest otherwise (the stopped state says progress is kept until the application closes).
- **Neutral:** `:persistence` stops being empty but still holds no database code.

## What would falsify this decision

The SQLite adapter cannot implement one of these ports without changing its signature — for example because a query
the screens need cannot be expressed without a new method, or because one commit has to be split across two
transactions.

## Amended in this change (2026-09-27)

The readiness review of `complete-translation-workflow` found that "one commit per chunk" could not hold once a run
pauses between a chunk's decisions for review: the person edits a decided segment during the pause, so that decision
must already be stored. The commit is now defined by what it holds — decided segments only — rather than by the chunk
boundary. The run record's state replaces an end-only field, because an end-only record cannot tell a paused run from a
running one. The masked user target lets a second edit of a segment with inline markup restore.
