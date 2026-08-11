## Why

`add-fb2-md-txt-roundtrip` shipped the four-format round trip and archived with a corpus run that
only ever called `DocumentPort.open`. It never wrote a single book back out, so the whole
reassembly half — jsoup re-serialization, JDOM2 fragment parsing, zip repackaging, byte-splicing,
the FB2 encoding re-decision — had been proven only against twelve hand-authored fixtures.

A verification sweep over 214 real books (`docs/implementation_plan/notes-corpus-verification.md`)
closed that gap. The headline is good: 207 of 208 writable books survived a full text-replacement
round trip with byte-exact equality across 626,276 segments. But it found eight defects and one
contradiction between two shipped requirements, and the worst of them is silent — **twelve EPUBs
import with zero segments and no error at all**, because one self-closed `<script/>` tag makes the
HTML parser swallow the entire book body. A user opens the book, sees nothing to translate, and
gets no diagnostic.

Fixing these on the branch before it merges is cheaper than fixing them after every downstream
stage has been built on top of them.

## What Changes

- **Parse XHTML that self-closes a raw-text element.** `<script/>`, `<style/>` and `<noscript/>`
  currently consume the rest of the document. This revises `design.md` D4's parser choice and
  therefore needs a new ADR.
- **Stop the writer changing structure on a zero-edit write.** Three separate ways it currently
  does: a self-closed `<a/>` is left open and duplicates its id across a section boundary; an empty
  non-void element round-trips to a different form so writing twice yields different bytes; and a
  `<pre>` whose content begins with two newlines silently loses one.
- **Classify a malformed translated fragment as `validation`, not `internal`.** A bare `&` or `<`
  in target text currently escapes the FB2 write path as an unclassified throwable.
- **Stop the charset ladder mis-resolving a Cyrillic single-byte encoding** as a Western European
  one, which decodes without error and produces mojibake.
- **Make the coverage metric able to measure Markdown and TXT.** It currently compares raw Markdown
  syntax against markup-stripped text, and decodes the TXT denominator as UTF-8 regardless of the
  resolved charset — so the assertion that is supposed to prove segmentation reach cannot
  distinguish real loss from measurement noise on two of the four formats.
- **Read book metadata from a legacy `<dc-metadata>` wrapper**, which currently drops title, author
  and language silently.
- **Resolve the contradiction between two shipped requirements**: one obliges the FB2 writer to add
  a `<lang>` element on export, the other obliges a zero-edit FB2 reassembly to be canonical-equal.
  For a source declaring no language, both cannot hold.
- **Make the corpus verification repeatable.** The sweep that found all of this was a throwaway.
  It becomes a committed, environment-gated test excluded from CI and `check`, so the same
  measurement can be re-run at the end of this change to prove every fix and to prove that no book
  which round-tripped before has regressed.
- Every task group adds a synthetic fixture carrying its shape to `FixtureCatalog`. Real books never
  enter the repo, so a defect that has no fixture is a defect the gate will not catch again.

Not in scope, recorded in `CHANGE_BACKLOG.md` instead: the reader accepting EPUBs the writer can
never export, the absence of a `close()`/eviction seam on the open-document registries, the writer
mutating the registry-held tree in place, and the 26,306-character single segment that the chunker
will have to sentence-split.

## Capabilities

### New Capabilities

None. Every requirement below constrains behaviour `document-round-trip` already owns.

### Modified Capabilities

- `document-round-trip`: parsing an XHTML content document whose markup self-closes a raw-text
  element; the skeleton-preservation guarantee tightened so a zero-edit write is a fixed point;
  the typed-envelope requirement extended to classify a malformed translated fragment; charset
  resolution required to prefer the encoding whose decoding is coherent rather than merely
  possible; the coverage proof required to measure all four formats on comparable terms; metadata
  population extended to the legacy wrapper; the FB2 golden's identity contract stated so it does
  not contradict the target-language obligation; and an on-demand corpus verification added.

## Impact

- `:document` — `document/epub/XhtmlParser.java` (the parser choice behind the twelve
  zero-segment books), `document/epub/EpubWriter.java`, `document/epub/OpfParser.java`,
  `document/detect/CharsetLadder.java`, `document/fb2/Fb2Writer.java`,
  `document/model/Jdom2TreeNode.java`, `document/DocumentService.java`.
- `:document` test sources — `golden/TextCoverage.java`, `golden/EpubCanonicalAssert.java`,
  `golden/GoldenComparisonMetaTest.java`, `fixture/FixtureCatalog.java` and the per-format fixture
  builders, plus the new corpus verification test.
- Build — a new tagged test task for the corpus set, wired the way `liveLocal`, `promptEval` and
  `visual` already are: excluded from `check` and CI, environment-gated so a checkout without a
  corpus stays green.
- Decisions — a new ADR revising `design.md` D4's "jsoup parses EPUB XHTML" choice. No existing ADR
  is superseded; ADR-0025, ADR-0026 and ADR-0027 all continue to hold and no fix here may regress
  them.
- No change to `:api`, no schema change, no new runtime dependency.
