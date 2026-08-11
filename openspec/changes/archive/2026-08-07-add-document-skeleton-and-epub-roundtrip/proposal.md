# add-document-skeleton-and-epub-roundtrip

## Why

Stage A built an application that starts and does nothing. Three changes are archived, the gate is green, a window
opens — and `openspec/specs/` is **empty**, because none of them shipped behaviour a user could observe. This is the
change that ends that: the first with a real capability, and the first whose requirements fold into the ledger.

It exists to make one claim true and testable: **a book survives being taken apart and put back together.** Open an
EPUB, parse it, reassemble it with nothing translated, save it — and what comes out opens identically to what went in.
Every later change depends on that being provable, because every later change writes into the thing this one builds.

The claim is harder than it sounds, and its failure mode is the reason it goes first. A parser that loses a `<span>`,
rewrites an element id, re-compresses the `mimetype` entry, or normalizes an entity produces a file that still *looks*
fine — it opens, it renders, and nothing throws. The damage surfaces later as a reader that rejects the book, a
cross-reference that dead-ends, or an obfuscated font that renders as boxes. Discovered now, each is a one-line fix in
one module; discovered in Stage D, each is a bug in a corpus of already-translated books.

**Seam F1 is the other reason for the ordering** (`07_ROADMAP.md#forward-compatibility-seams`). The skeleton plus
ordered-segment model is consumed by masking, the pipeline, and export. A model that starts out mutable — or that
regenerates structure on write — cannot be converted later without touching every consumer, which is precisely the
"later stages reshape earlier code" the roadmap forbids.

## What Changes

- **`:api` gains the document contracts — seam F1.** `Document`, `Unit`, `Segment`, `SegmentKind`, `BookFormat`, and
  the `DocumentPort` interface, in a new `ua.bookloom.api.document` sub-package. Records throughout, per DD-05 and
  the `records-first` rule that governs `..api..`.
- **`Segment` carries only what this change can populate.** `id`, `unit`, `order`, `kind`, `sourceInner`, `sourceHash`,
  `prevKey`/`nextKey`, `status`. The `masked` and `placeholders` fields belong to the shape but stay empty here —
  inline masking is change 5, and populating them would mean inventing a placeholder scheme this change cannot
  validate.
- **`:document` gains EPUB read.** Unzip the container, read `META-INF/container.xml`, parse the OPF package document,
  walk the spine in reading order, and parse each XHTML content document with jsoup into a skeleton plus an ordered
  segment list.
- **The skeleton is immutable, and segments address it by anchor.** Each segment carries a **stable node path plus
  child index** into the skeleton tree — never a byte offset, never an inserted sentinel node. Anchors are computed at
  parse time and never mutate the tree.
- **Non-translatable blocks yield no segments (DD-49).** `<pre>`, `<pre><code>` listings and block-level MathML
  `<math>` are excluded from segmentation entirely and preserved through the skeleton alone. They are not empty
  segments; they are not segments.
- **`:document` gains EPUB write.** Target text is written back into the exact node it was parsed from; the container
  is repackaged with `mimetype` **first and STORED**, entry order preserved, and every other resource carried through
  byte-for-byte.
- **The target language is set on export by replacing the first `dc:language`**, adding one if absent, leaving any
  further entries untouched (FR-DOC-EPUB-6).
- **DRM is refused, not partially imported.** `META-INF/encryption.xml` is inspected: content encryption or any
  *unknown* algorithm refuses the book outright; the two known IDPF font-obfuscation URIs are **allowed** and the
  obfuscated font bytes carried through unchanged (EC-EPUB-1, EC-FONT-1).
- **A document-scoped content hash** (SHA-256 over the imported source) is computed and carried for later
  change-detection and resume (FR-IMPORT-08).
- **The golden round-trip test lands, and it is the gate.** Parse a fixture EPUB → reassemble with **zero** segment
  edits → assert **canonical equality**: text entries by decompressed canonical content, entry order preserved,
  `mimetype` first and STORED, unchanged binary entries by decompressed bytes.
- **A new dependency:** jsoup, for XHTML body parsing. The zip work uses `java.util.zip` from the JDK; the OPF is XML
  and uses the same parser stack the FB2 change will need.

**BREAKING:** nothing. No released artifact, no persisted data, and no consumer of `:document` exists yet.

## Capabilities

### New Capabilities

- `document-round-trip`: opening a book file, parsing it into an immutable skeleton plus an ordered list of
  translatable segments, and writing it back out in the same format such that a no-edit round trip is canonical-equal
  to the source. This change establishes the capability and covers **EPUB only**; FB2, Markdown and TXT extend it in
  the next change.

### Modified Capabilities

None. `openspec/specs/` is empty — this is the first change in the project to create a capability at all.

## Impact

- **`:api/ua.bookloom.api.document`** — `Document`, `Unit`, `Segment`, `SegmentKind`, `BookFormat` records/enums and
  the `DocumentPort` interface, in the sub-package `01_MODULE_INVENTORY.md#module-api` already plans for the
  document model — the module's existing `ua.bookloom.api` export stays the `Result`/`AppError`/`ErrorCode` floor
  only. Framework-free as always: no jsoup type may appear here, since `api-is-framework-free` bans parser libraries
  from `:api` and every consumer would otherwise inherit one.
- **`:document/ua.bookloom.document`** — `DocumentService` implementing the port, dispatching to an internal
  `document.model` package (seam F1's segment-emission and anchor write-back machinery, format-agnostic so change 4
  reuses it for FB2/Markdown/TXT) and a `document.epub` package (container/OPF/XHTML/DRM handling), per
  `01_MODULE_INVENTORY.md#module-document`. FX-free; depends only on `:api` and `:util`.
- **`:util/ua.bookloom.util.hash`** — the SHA-256 document-content and per-segment hashing helper, per
  `01_MODULE_INVENTORY.md#module-util` rather than duplicated inside `:document`.
- **Error handling** — every port method returns `Result<T>`. A corrupt container, a missing OPF, or a malformed spine
  is `ErrorCode.validation` (EC-EPUB-2); a DRM refusal is its own typed outcome, not a crash. Note that a **filesystem
  path is not on the safe-details allowlist**, so it belongs on `AppError.cause`, never in `details` — the allowlist is
  the record's component set and admits no path field.
- **Dependencies added:** jsoup (MIT, already on the allowed-license list's default family). Lockfiles regenerate for
  `:document`; note that unlike `:app`, the FX-free modules **do** lock normally, so a missing lockfile diff there is a
  real problem rather than the JavaFX carve-out.
- **Test fixtures** — a small, redistributable EPUB fixture enters the repository. It must be MIT-compatible or
  self-authored; a copyrighted book cannot be committed as a test resource.
- **Offline invariant** — unaffected and worth restating: parsing is local file I/O only. No module gains
  `java.net.http`, and the `no-http-in-core-except-llm` ArchUnit rule already enforces that structurally.
- **Frozen spec:** unedited.
- **ADRs consumed:** ADR-0003/DD-43 (canonical-equal round trip, not byte equality), ADR-0004 (export in the original
  format only), DD-07 (the skeleton/segment model), DD-49 (code and math as non-translatable blocks), ADR-0014
  (records for data), ADR-0017 (why this is Stage B's first entry).
