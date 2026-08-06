# Tasks — add-document-skeleton-and-epub-roundtrip

Establishes seam F1 — the immutable skeleton plus ordered segment list — and proves it on EPUB. The decisions are
`design.md` (D1–D7); the frozen sources are `02_Architecture/03_DOCUMENT_MODEL.md` (the model, reassembly,
repackaging, the golden test), `01_Product/03_DOCUMENT_FORMATS.md#epub` (per-format rules and edge cases), and
`09_ERROR_HANDLING.md` (the envelope, which already exists).

**Group order is a dependency chain.** The `:api` model lands first because `:document` implements against it; the
fixtures land before the round-trip test that consumes them; the golden test is last because it is the gate the rest
of the change is judged by.

**This is the first change in the project with a real capability**, so unlike changes 1–3 it ships a delta spec and
`openspec/specs/` stops being empty. Every requirement in `specs/document-round-trip/spec.md` needs a covering test.

**Test markers.** A test covering a business requirement carries `// Covers: FR-*` plus a one-line EARS restatement;
a mechanical or infrastructure test carries none. Do not invent an FR id — several requirements in the delta spec cite
`FR-DOC-EPUB-*` ids from `01_Product/03_DOCUMENT_FORMATS.md#epub`, which are real and citable.

## 1. Seam F1 — the document model in `:api`

- [ ] 1.1 Add the `BookFormat` enum (`EPUB`, `FB2`, `MARKDOWN`, `TXT`) and the `SegmentKind` enum with the body kinds
      `PARAGRAPH`, `HEADING`, `VERSE_LINE`, `LIST_ITEM`, `TABLE_CELL`, `FOOTNOTE`, `CAPTION`, `TITLE` plus the
      metadata-unit kinds `METADATA_TITLE`, `METADATA_AUTHOR`, `FRONTMATTER_VALUE`, `ALT`, `NAV_LABEL`. All of them
      ship now even though this change produces only a few: seam F1 is a fixed target for every later stage, and an
      enum that grows as producers appear is the moving target changes 1–3 were sequenced to avoid.
      → `:api/ua.bookloom.api` · `03_DOCUMENT_MODEL.md#data-model`, DD-07
- [ ] 1.2 Add the `SegmentStatus` enum (`PENDING`, `ACCEPTED`, `FLAGGED`, `REVISED`) with the transitions of the
      status machine stated in its Javadoc. Every segment this change produces is `PENDING` and stays there; the enum
      lands now because the pipeline and the persistence schema both key on it.
      → `:api/ua.bookloom.api` · `03_DOCUMENT_MODEL.md#segment-status-machine`
- [ ] 1.3 Add the `SkeletonAnchor(List<Integer> nodePath, int childIndex)` record locating a segment's text node
      within its unit's skeleton. It is an index path and **not** an element id, because EC-EPUB-4 requires duplicate
      ids to be preserved — ids are therefore not unique and cannot address a node (design.md D3).
      → `:api/ua.bookloom.api` · `03_DOCUMENT_MODEL.md#data-model`, EC-EPUB-4, design.md D3
- [ ] 1.4 Add the `Segment` record with `id`, `unit`, `order`, `kind`, `sourceInner`, `masked`, `placeholders`,
      `sourceHash`, `prevKey`, `nextKey`, `anchor`, `targetInner`, `status` and `confidence`. Ship `masked` and
      `placeholders` **in the shape but empty** — `masked` initialized equal to `sourceInner` and `placeholders` to an
      empty map — so change 5 fills fields rather than changing a record four changes already compile against
      (design.md D2). Say so in the field comments; a later reader must not mistake the initialization for real
      masking.
      → `:api/ua.bookloom.api` · `03_DOCUMENT_MODEL.md#data-model`, design.md D2
- [ ] 1.5 Add the `SkeletonHandle` and `Unit` records, where `Unit` carries `id`, `order`, `href`, `mediaType`, its
      skeleton handle and its ordered segments. The handle is **opaque**: the parsed tree never crosses the `:api`
      boundary, because `api-is-framework-free` bans parser libraries there and a jsoup type in a record component
      would put jsoup on all eight modules' compile classpath (design.md D1).
      → `:api/ua.bookloom.api` · `03_DOCUMENT_MODEL.md#data-model`, design.md D1
- [ ] 1.6 Add the `Document` record carrying `id`, `format`, `declaredLang`, `contentHash`, its metadata and its
      ordered units. `detectedSourceLang` is present in the shape but not populated here — Lingua detection is owned
      by `add-metadata-units-and-language-detection` (ADR-0023), and inventing a detector to fill a field would be
      scope this change cannot test.
      → `:api/ua.bookloom.api` · `03_DOCUMENT_MODEL.md#data-model`, FR-IMPORT-08
- [ ] 1.7 Add the `DocumentPort` interface with `open(Path)` returning `Result<Document>` and `write(Document, Path,
      String targetLanguage)` returning `Result<Path>`. Every method returns the envelope: the boundary discipline is
      already in force, and no exception may cross a module edge.
      → `:api/ua.bookloom.api` · `09_ERROR_HANDLING.md#boundary-discipline`,
      `02_MODULES_AND_LAYERING.md#dependency-direction`
- [ ] 1.8 Cover the model: a segment's neighbours are its document-order siblings with null at the ends, `masked`
      initializes equal to `sourceInner` with no placeholders, and an anchor rejects a negative index. Mark the
      neighbour test `// Covers: FR-DOC-01` with its EARS restatement; the rest are mechanical and carry no marker.
      → `:api/ua.bookloom.api` · `modules/api/src/test/java/ua/bookloom/api/`, `testing.md`

## 2. EPUB read in `:document`

- [ ] 2.1 Add `jsoup` and `jdom2` to `gradle/libs.versions.toml` and declare both on `:document` only. Two parsers
      rather than one because the specification divides them: XHTML in the wild is HTML-shaped and an XML parser
      rejects it, while the OPF is strict XML whose comments, CDATA and entity spelling must survive a canonical-equal
      round trip (design.md D4). Both are MIT/JDOM-licence and already covered by the allowed-licence policy.
      → repo root · `gradle/libs.versions.toml`, `modules/document/build.gradle.kts`,
      `05_Dependencies/03_LICENSING.md`
- [ ] 2.2 Add `requires org.jsoup` and `requires org.jdom2` to `:document`'s `module-info.java`, and keep both out of
      every other module. `api-is-framework-free` already bans a parser from `:api`; this task is what keeps it true
      of `:ui` and `:pipeline` as well, so a book's DOM cannot be reached from a screen.
      → `:document` · `modules/document/src/main/java/module-info.java`,
      `02_MODULES_AND_LAYERING.md#archunit-rules`
- [ ] 2.3 Read the EPUB container: open the archive with `java.util.zip`, parse `META-INF/container.xml` to find the
      OPF, and record every entry's name, order and compression method. The entry order is captured at read time
      because repackaging must reproduce it, and it is unrecoverable once the archive is closed.
      → `:document/ua.bookloom.document.epub` · `01_Product/03_DOCUMENT_FORMATS.md#epub`, FR-DOC-EPUB-1
- [ ] 2.4 Adjudicate `META-INF/encryption.xml` **before parsing anything else**, using an allowlist: permit only the
      two known IDPF font-obfuscation URIs and refuse the book for content encryption or any unrecognised algorithm.
      Deciding first is what makes "no partial import" achievable rather than aspirational, and an allowlist rather
      than a denylist is what stops the next encryption scheme importing silently (design.md D7).
      → `:document/ua.bookloom.document.epub` · EC-EPUB-1, EC-FONT-1, FR-DOC-EPUB-7, design.md D7
- [ ] 2.5 Parse the OPF with JDOM2: read the manifest and the spine, establish reading order, and read the
      `dc:language` entries and the title/author metadata. Spine order and zip order are different things, and
      translating in zip order would run the book — and its progress indicator — out of sequence.
      → `:document/ua.bookloom.document.epub` · FR-DOC-EPUB-1, FR-DOC-EPUB-2,
      `01_Product/03_DOCUMENT_FORMATS.md#epub`
- [ ] 2.6 Parse each spine XHTML document with jsoup, pinning output settings to `prettyPrint(false)` and the source
      charset. Pretty-printing normalizes whitespace, and whitespace between block elements is significant in verse —
      a normalizing writer would fail the golden round trip for a reason that looks like a test bug.
      → `:document/ua.bookloom.document.epub` · `03_DOCUMENT_MODEL.md#xml-round-trip-config`, FR-DOC-EPUB-5
- [ ] 2.7 Walk each parsed body in document order and emit one segment per translatable block element, capturing
      `sourceInner`, the block's `SegmentKind`, its anchor, and its neighbours. Emit **no segment** for `<pre>`,
      `<pre><code>` or block-level MathML `<math>` — they are excluded from segmentation entirely rather than marked
      untranslatable, because a segment that exists still costs token budget and still has to be explained to the
      model (DD-49).
      → `:document/ua.bookloom.document.epub` · DD-49, `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`
- [ ] 2.8 Compute the SHA-256 document content hash over the imported file and a SHA-256 `sourceHash` over each
      segment's NFC-normalized pre-mask inner content. The document hash is what makes resuming a run safe; the
      per-segment hash is what lets an edited book reuse the translations of the parts that did not change.
      → `:document/ua.bookloom.document.epub` · FR-IMPORT-08, `03_DOCUMENT_MODEL.md#data-model`
- [ ] 2.9 Cover the read path: spine order is followed rather than zip order, block kinds are distinguished, a code
      listing yields no segment, neighbours are wired, and the same file hashes identically twice. Mark each
      `// Covers: FR-DOC-01` / `FR-DOC-EPUB-1` / `FR-IMPORT-08` with its one-line EARS restatement.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/`, `testing.md`

## 3. EPUB write and repackaging

- [ ] 3.1 Write accepted target text back into the exact text node its segment was parsed from, resolving the anchor
      against the immutable skeleton. Nothing else in the tree may be touched: because only text nodes change,
      structure, ids, images and fonts are preserved by construction rather than by a preservation pass that could
      have bugs.
      → `:document/ua.bookloom.document.epub` · FR-DOC-03, `03_DOCUMENT_MODEL.md#reassembly`
- [ ] 3.2 Repackage the archive writing `mimetype` **first and STORED**, preserving the order and identity of every
      remaining entry and carrying untranslated resources through by their decompressed bytes. An EPUB whose
      `mimetype` entry is compressed or not first is rejected by readers, and the rejection never mentions
      compression — this is the format's most easily broken rule and a naive re-zip breaks it by default.
      → `:document/ua.bookloom.document.epub` · FR-DOC-EPUB-3, FR-DOC-06, `03_DOCUMENT_MODEL.md#repackaging`
- [ ] 3.3 Set the target language on export by replacing the **first** `dc:language` in the OPF, adding one when none
      is present, and leaving any further entries untouched. Later entries may legitimately record other languages
      present in the book, so overwriting all of them would discard real information.
      → `:document/ua.bookloom.document.epub` · FR-DOC-07, FR-DOC-EPUB-6
- [ ] 3.4 Carry out-of-spine resources verbatim and emit no segments for them, including the nav document and the
      NCX. Their ToC labels become segments in `add-metadata-units-and-language-detection`, the Stage B interstitial
      that runs after change 5 and carves nav/NCX out of this rule (ADR-0023) — so they are carried verbatim here and
      segmented there.
      → `:document/ua.bookloom.document.epub` · EC-EPUB-3,
      FR-DOC-EPUB-8 *(owned by `add-metadata-units-and-language-detection`)*
- [ ] 3.5 Cover the write path: `mimetype` is first and STORED, entry order is preserved, a duplicate id survives
      unrewritten, an embedded font round-trips byte-for-byte, and only the first `dc:language` is replaced. Mark each
      `// Covers: FR-DOC-EPUB-3` / `FR-DOC-EPUB-4` / `FR-DOC-EPUB-6` with its EARS restatement.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/`, EC-EPUB-4

## 4. Failure paths

- [ ] 4.1 Return `ErrorCode.validation` for a corrupt or non-zip file, a missing or malformed OPF, and a missing
      spine, producing **no** partially parsed document. Half a book is worse than no book, because the user cannot
      tell which half is missing and discovers it as absent chapters after a long run.
      → `:document/ua.bookloom.document` · EC-EPUB-2, `09_ERROR_HANDLING.md#boundary-discipline`
- [ ] 4.2 Return a DRM-blocked failure for content encryption or an unrecognised algorithm, distinct from a corrupt
      file so the UI can eventually say which happened. The user's next action differs: a corrupt file might be
      re-downloaded, a DRM-protected one cannot be used at all.
      → `:document/ua.bookloom.document` · FR-DOC-EPUB-7, EC-EPUB-1
- [ ] 4.3 Wrap every escaping throwable at the port boundary as `Result.err(AppError(internal, …, cause))`, and put
      **no filesystem path in `details`**. A path is not on the safe-details allowlist — which is the record's
      component set and admits no path field — so it belongs on `cause`, the channel meant for detail that reaches a
      log but never a screen.
      → `:document/ua.bookloom.document` · `09_ERROR_HANDLING.md#safe-details-allowlist`,
      `.claude/rules/error-envelope.md`
- [ ] 4.4 Cover the failure paths with the fixtures from task 5.2: each returns the right code, none returns a
      partial document, and no error's `details` contains the fixture's path. Mark them `// Covers: FR-DOC-EPUB-7`
      and the EC ids they exercise.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/`, EC-EPUB-1, EC-EPUB-2

## 5. Fixtures and the golden round trip

- [ ] 5.1 Author the primary fixture EPUB **in this repository** rather than downloading one, and build it to trip
      every trap: `mimetype` first and stored, two `dc:language` entries, duplicate element ids, a `<pre><code>`
      listing, an out-of-spine stylesheet, an embedded font, an XML comment between block elements, a spine whose
      order differs from zip order, and an entity a re-serializer might respell. A downloaded book would put someone
      else's copyrighted text in git permanently; a happy-path fixture would pass against a parser that got all of
      the above wrong (design.md D6).
      → `:document` · `modules/document/src/test/resources/`, design.md D6
- [ ] 5.2 Author the four single-purpose refusal fixtures: a content-encrypted `encryption.xml`, a
      font-obfuscation-only one, an archive whose OPF is missing, and a file that is not a zip at all. Each exists to
      make one failure path provable rather than argued.
      → `:document` · `modules/document/src/test/resources/`, EC-EPUB-1, EC-EPUB-2
- [ ] 5.3 Implement the canonical comparison as defined in design.md D5 — `mimetype` first/STORED/exact, text entries
      re-parsed and serialized through a fixed canonical writer, binary entries by decompressed bytes, entry order as
      a list — and **do not** compare compression level. Re-parsing before comparing is what makes it canonical
      rather than textual: it absorbs the entity spelling and attribute quoting DD-43 permits to differ while still
      catching a lost element or a rewritten id. Write it to be reused, because change 4 needs the same shape for
      three more formats.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/`, DD-43,
      `03_DOCUMENT_MODEL.md#golden-round-trip-test`, design.md D5
- [ ] 5.4 Add the golden round-trip test: parse the primary fixture, reassemble with **zero** segment edits, and
      assert canonical equality. This is the gate the whole change is judged by — mark it
      `// Covers: FR-DOC-09` with its EARS restatement, and assert byte equality nowhere, since a re-zip at a
      different compression level changes every byte of a DEFLATED entry and harms no reader.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/`, FR-DOC-09,
      `01_Product/03_DOCUMENT_FORMATS.md#round-trip-golden-requirement`
- [ ] 5.5 Assert the individual traps as **named tests** as well as through the round trip, so a fixture that stops
      covering one of them fails a test that says which — rather than silently weakening the single gate everything
      else depends on.
      → `:document` · `modules/document/src/test/java/ua/bookloom/document/`, design.md "Risks"

## 6. Green gate

- [ ] 6.1 Regenerate and commit lock state — `./gradlew resolveAndLockAll --write-locks` — because jsoup and JDOM2
      are new coordinates. Unlike `:app`, whose compile and runtime classpaths are outside lock coverage by the
      JavaFX classifier carve-out, `:document` locks normally, so a missing lockfile diff here is a real problem
      rather than expected.
      → repo root · `01_BUILD_AND_TOOLING.md#dependency-locking`, task 2.1
- [ ] 6.2 Run `./gradlew :build-logic:clean` before the gate: `build-logic` is an included build, so the root `clean`
      does not reach into it and its functional suite can report `UP-TO-DATE` from a stale cache.
      → repo root · `01_MODULE_INVENTORY.md#as-built-baseline`
- [ ] 6.3 Run `./gradlew clean build check spotlessCheck` and confirm it is green across the whole project, including
      the eight ArchUnit rules — which now have a second module with real content to police, and a new parser
      dependency that must not have leaked past `:document`. No "pre-existing failure" exemption.
      → whole project · `06_DEFINITION_OF_DONE.md#per-change-checklist`,
      `.claude/rules/gradle-build-and-quality.md`
- [ ] 6.4 Run `./gradlew -PstrictLocks verifyLocks` and `./gradlew checkLicense`. The licence gate matters here
      because this change adds two dependencies, and JDOM2 ships under the **JDOM licence** — a recorded exception
      rather than one of the default Apache/MIT/BSD family, which is exactly the case the gate exists to adjudicate.
      → whole project · `01_BUILD_AND_TOOLING.md#dependency-locking`, `05_Dependencies/03_LICENSING.md`
- [ ] 6.5 Update `01_MODULE_INVENTORY.md#as-built-baseline` to record what `:api` and `:document` now genuinely hold,
      and add the `ua.bookloom.document.epub` package row. The inventory is the citation target every later change
      resolves against, so a stale row there is a broken citation in every proposal that follows.
      → `docs/implementation_plan/` · `01_MODULE_INVENTORY.md#as-built-baseline`
- [ ] 6.6 Confirm the offline invariant still holds: parsing is local file I/O only, no module gained
      `java.net.http`, and neither new parser fetches a DTD or schema over the network at parse time. The last of
      those is the one worth checking rather than assuming — an XML parser resolving an external DTD is a network
      call that no ArchUnit rule would catch.
      → whole project · `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md`, DD-01, NFR-PRIV-01
- [ ] 6.7 Run `openspec validate add-document-skeleton-and-epub-roundtrip --strict`, confirm it is clean, then
      archive. This change **does** fold requirements into `openspec/specs/` — it is the first to do so, and
      `openspec/specs/document-round-trip/` should exist afterwards where before the directory was empty.
      → `openspec/` · ADR-0016
