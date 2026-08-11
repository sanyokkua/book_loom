# ADR-0030 — Synthesize the EPUB `mimetype` entry on write when the source container has none

**Status:** accepted **Date:** 2026-08-11
**Deciders:** architect, product owner
**Supersedes:** none

## Context and problem statement

`EpubReader` imposes no `mimetype` requirement — `grep -c -i mimetype` returns **0** for it and **9** for
`EpubWriter`, which requires one because DD-43 mandates mimetype-first-and-STORED on output
(`EpubWriter.requireMimetype`, throwing `CorruptContainerException`).

`aliceDynamic.epub` in the local corpus has 68 entries and none named `mimetype`. It **opens** with 13 units and 836
segments, and then **refuses every write** — including a zero-edit one — with `ErrorCode.validation`.

The refusal is not itself wrong; the writer genuinely cannot emit a container it has no `mimetype` for. The defect is
the **asymmetry**: the application accepts a book for translation that it can never deliver, and the user finds out
only after translating it. No requirement covers reader/writer acceptance parity, so neither behaviour can be called
the incorrect one from the frozen spec alone, and picking either is a product decision rather than a bug fix.

## Decision drivers

- **Acceptance must be honest.** Whatever is decided, the answer to "can this book be translated?" must be the same at
  import and at export. Two different answers is the defect.
- **The failure must arrive before the work, not after.** Hours of local inference must not be spent on a book that
  cannot be delivered.
- **A real, readable book should not be rejected.** `aliceDynamic.epub` opens in ordinary readers; refusing it shrinks
  what BookLoom accepts against no user benefit.
- **Nothing may be invented.** Anything the writer emits that the source did not contain has to be fully determined by
  a specification, not guessed from the book.

## Considered options

- **A — Synthesize the entry on write.** Keep the reader permissive; `EpubWriter` emits a conformant `mimetype` entry
  when the source had none.
- **B — Fail fast at open.** `EpubReader` refuses a container with no `mimetype`.
- **C — Warn at open, still refuse at write.** Surface the asymmetry rather than removing it.

## Decision outcome

Chosen: **A — synthesize the entry on write.**

The decisive point is that there is nothing to invent. OCF fixes the entry completely: the name `mimetype`, the exact
ASCII content `application/epub+zip`, first position in the archive, and STORED (uncompressed) with no extra field.
Every one of those is already what `EpubWriter` emits for a book that *has* the entry — the code path exists and is
exercised by every EPUB fixture. So this is not fabrication; it is emitting the one byte sequence the format
specification permits, for a container that was non-conformant on input.

Concretely:

- `EpubWriter.requireMimetype(parsed)` becomes a lookup that falls back to a synthesized `RawEntry` — name `mimetype`,
  content `application/epub+zip` in US-ASCII, STORED — when no entry of that name is present.
- When the source **does** carry the entry, its bytes are reused **verbatim**, unchanged from today. A book with an
  unusual-but-present `mimetype` is never rewritten.
- The synthesized entry is written first, exactly as the existing one is.
- `EpubReader` is untouched. It already accepts these books, and it keeps doing so.

### Consequences

- Positive: the asymmetry is gone in the direction that keeps books. `aliceDynamic.epub` imports with 836 segments
  **and** exports as a conformant OCF container.
- Positive: the export is strictly **more** conformant than the source. A book that some readers reject becomes one
  they accept.
- Positive: the smallest possible change — one method in one class, no reader change, no `:api` change, no new
  error path.
- Negative: **the output archive has an entry the source did not.** For a source with no `mimetype`, the export is not
  entry-for-entry identical to the input, and `#round-trip-golden-requirement` asserts "entry order preserved" and
  compares entry by entry. That comparison needs an explicit, narrow carve-out: **when the source carried no
  `mimetype`, the output's leading `mimetype` entry is excluded from the entry-set comparison and every other entry
  is compared exactly as before.** Without that, a golden over such a fixture fails on the very entry this decision
  adds on purpose.
- Negative: BookLoom silently repairs a container defect rather than reporting it. Accepted — the user cannot act on
  it, and a warning about a file property they did not create and cannot change is noise.
- Neutral: the no-`mimetype` shape is rare — one book in a 213-book corpus — so this is about correctness, not volume.
- Neutral: it says nothing about any other missing container part. A book with no `META-INF/container.xml` or no OPF
  still fails at open, because those carry book-specific content that genuinely cannot be derived.

### Deviations from the frozen specification

| Clause | As frozen | As decided here |
|---|---|---|
| `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement` | EPUB compared entry-by-entry with "entry order preserved" | unchanged, except that where the **source carried no `mimetype`**, the output's synthesized leading `mimetype` entry is excluded from the comparison |
| `02_Architecture/03_DOCUMENT_MODEL.md#repackaging` | "rewrite only the spine XHTML text nodes, keep all resources" | unchanged; the synthesized entry is a container-conformance entry, not a resource, and is only ever added when absent |

DD-43's mimetype-first-and-STORED rule is **not** deviated from — this is what makes it satisfiable for a container
that arrived without one.

## Pros and cons of the options

### Option A — Synthesize on write

- Good: no book is lost; the app delivers everything it accepts.
- Good: nothing is guessed — OCF fixes the entry's name, bytes, position and compression method completely.
- Good: one method, one class; the writing path itself is already there.
- Bad: needs the golden carve-out above, so "canonical-equal" gains a second qualifier.
- Bad: repairs a defect without telling anyone.

### Option B — Fail fast at open

- Good: strictly correct against OCF, which requires the entry; and the user learns immediately.
- Good: no golden carve-out; entry-for-entry comparison stays absolute.
- Bad: **rejects a book that works.** `aliceDynamic.epub` opens in ordinary readers, and BookLoom would be stricter
  than the ecosystem it exports into, for no gain the user can perceive.
- Bad: the strictness is unilateral — the reader would enforce a rule solely because the writer needs it, which is the
  asymmetry inverted rather than resolved.

### Option C — Warn at open, still refuse at write

- Good: cheapest; nothing about either path changes except a flag.
- Good: the user is told before spending inference time.
- Bad: **the user still cannot export the book.** A book that can be read but never delivered has no use in a
  translation tool, so this makes the dead end visible without removing it.
- Bad: costs a new field on the `Document` contract and a UI state, to communicate a limitation option A removes
  outright.

## Links

- Design decisions: DD-43 (canonical-equal round trip, mimetype-first-and-STORED)
- Spec clauses: `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#epub`,
  `#round-trip-golden-requirement`, `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#repackaging`
- Evidence: `docs/implementation_plan/notes-corpus-verification.md` finding F7;
  `docs/implementation_plan/CHANGE_BACKLOG.md` deferred finding "the reader accepts EPUBs the writer can never export"
- Implemented by: the `settle-writer-policy-and-document-lifetime` change (backlog item D2)
- Related: ADR-0029 (the other half of the writer's acceptance policy)
