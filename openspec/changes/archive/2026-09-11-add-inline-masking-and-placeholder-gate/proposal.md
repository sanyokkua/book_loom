## Why

Every segment BookLoom produces today ships with `masked` equal to `sourceInner` and an empty `placeholders` map —
`BlockSegmentWalker`, `MarkdownWalker` and `TxtReader` all construct it that way, and `BlockSegmentWalker`'s own
Javadoc concedes it. There is no `⟦gN⟧` code, no unmask, and no placeholder-multiset comparison anywhere in
`modules/`. That means the two fields the whole translation contract rests on are decorative, and **no translated
chunk can be validated at all**: `FR-DOC-04` (mask protected spans before translation, unmask after) and
`FR-DOC-05` (the placeholder multiset is a hard gate) are the only two `fr-doc` requirements with no covering test,
and `scripts/fr-coverage.sh` lists both as unclaimed.

Nothing downstream can start without them. The chunker must never cut a masked tag pair, the deterministic QA gate's
first row is tag integrity, and the accept rule is `hardGatesPass ∧ confidence ≥ τ ∧ …` — a boolean that has no
value to take until this change lands. Masking is also the last thing that can be built while `:document` still has
exactly one caller: ADR-0025 already reshaped reassembly around it ("Resolve it before change 5, not during it"),
and the port surface an unmask step needs is cheapest to add now, before `:pipeline` becomes real.

## What Changes

- **Inline masking, per format.** A segment's inline markup is replaced by opaque `⟦gN⟧` placeholder tokens before
  translation, numbered densely from `⟦g0⟧` in first-appearance order and unique within the segment; the original
  fragments are carried in `Segment.placeholders` as an ordered token→fragment map. `Segment.masked` and
  `Segment.placeholders` stop being pass-throughs and carry real values for EPUB, FB2, Markdown and TXT.
- **Two placeholder shapes, decided.** An element that encloses content becomes a **paired group** — an opening and
  a closing token with the words still translatable between them (`<em>old</em>` → `⟦g0⟧old⟦g1⟧`), which is what
  keeps an index-term anchor's ids and href intact while its visible link text still translates. A span that must
  never be entered — `<code>`, inline `<math>`, an inline `<svg>` — or that has no content — `<br/>`, `<img/>`, a
  comment, a CDATA section, an entity reference — becomes a **single atomic token**. The frozen spec names both
  shapes but never says which applies to an ordinary `<em>`; this change writes it down.
- **Which nodes are masked is decided structurally, not from a tag list.** Every non-character-data node inside a
  segment's run is masked, whatever it is called — extending ADR-0027's finding, where a whitelist of block tags
  reached 73.74% of a 194-book corpus and left 42 books importing essentially empty, to the inline layer. Five
  elements are named individually — `<math>`, `<svg>`, `<pre>` and `<listing>` in every markup format, and
  `<code>` in XHTML alone — because "never translate the interior" is a fact about them that no structural signal
  carries. They earn it two different ways: `math`/`svg` are foreign vocabulary to both host formats, while
  `pre`/`listing` are the elements whose leading line feed an HTML parser discards.
- **Unmask, and the placeholder-multiset hard gate.** A new operation restores a translated segment's markup. It
  first compares the multiset of `⟦gN⟧` tokens in the model's output against the multiset in `masked`: order is
  irrelevant, counts are not. A missing, duplicated or invented token fails with `ErrorCode.validation`, and the
  system does **not** unmask, does not attempt a repair, and does not silently reconcile — the caller is handed the
  complete expected token multiset, and the observed one within a stated bound, so the pipeline's later directed-fix tier can act on them. The asymmetry is deliberate: a repair tier cannot ask a model to restore tokens it invented, and the observed side is provider output with no bound of its own.
- **A restored fragment is well-formed by construction** (**ADR-0031**, new). For EPUB and FB2 the masked text is
  entity-decoded character data — the model reads `Smith & Sons`, not `Smith &amp; Sons` — and unmask *composes* the
  fragment: the model's own text is escaped as character data, and only the mapped fragments are spliced back as
  markup. A stray `<` or `&` in translated prose can therefore no longer produce a fragment that fails to parse.
  This answers the question `CHANGE_BACKLOG.md#decision-debt` explicitly assigned to this change — "one fixes error
  classification at the write boundary, the other fixes what the model is allowed to hand back in the first place."
- **A literal `⟦` or `⟧` in prose is a protected span.** The frozen escape rule requires that a pre-existing bracket
  can never be read as a placeholder token and is restored exactly, but does not say how, and names only the opening
  bracket. Both are captured into the placeholder map like any other protected span, so the model never sees a bare
  bracket and the hard gate protects the restoration for free (ADR-0031).
- **Markdown gets the same composition rule, plus one check its punctuation needs.** Model-introduced Markdown
  delimiters are neutralised so a translation containing `5 * 3` cannot gain emphasis the author never wrote:
  backslash-escaped where CommonMark gives a backslash meaning, which is only before ASCII punctuation; deleted for
  a hard line break's trailing spaces, which no backslash form removes; and left exactly as written for a marker
  that is neither, so the structure check reports it rather than a stray backslash reaching the book —
  the exact counterpart of escaping a stray `<`, and it covers block constructs too, so a translation beginning
  `1985.` prints rather than becoming an ordered list. Escaping cannot cover the other direction, so after restoring,
  the multiset of construct types must still match the source's: `⟦g0⟧ старі ⟦g1⟧` passes the placeholder gate and
  restores to `* старі *`, which is not emphasis at all. Both sides are parsed the same way and standalone, so
  restoring a segment unchanged always passes; a mismatch is `ErrorCode.validation`. EPUB and FB2 need none of this,
  because a restored element splices back as a node.
- **A standalone inline code span is currently handed to the model, and this change stops it.** For
  `<p><code>List.of()</code></p>` the paragraph owns no text of its own, so the structural block rule descends into
  the code span and makes *that* the segment — masking cannot help, because by then the code span is the block.
  DD-49 says nothing inside `<code>` may reach the model at all, so this change's own atomic-code requirement would
  ship contradicted by the code. Fixed by not descending into a code span; the paragraph then yields no segment and
  the skeleton carries it through.
- **A restored FB2 entity reference could not be written back, and this change fixes that.** An entity reference is
  masked atomically, so its fragment is `&nbsp;` — and a fragment is re-parsed on its own, inside a synthetic
  wrapper that declares namespaces but no entities. Measured, that parse fails with "the entity was referenced, but
  not declared", which would make every FB2 segment containing a non-breaking space impossible to write back; the
  corpus carries 10,377 of them. Carrying the source's own internal declarations into that parse is verified to work
  under the existing hardened parser configuration and moves no security flag.
- **The mask-time invariant is asserted, not assumed.** Emitted tokens are unique within a segment and
  `placeholders` is a bijection over the tokens present in `masked` — checked over the masked form alone, so a code
  span whose own text is the literal `⟦g0⟧` is not mistaken for a duplicate.

### Non-goals, each with an owner

- **Translating text inside a Markdown raw-HTML block** (the second half of EC-MD-2).
  `docs/implementation_plan/CHANGE_BACKLOG.md`'s Stage B section assigns this here on the grounds that "change 5
  introduces exactly that mechanism".
  **That premise turned out to be false**: `BlockSegmentWalker` emits a `NodeAnchor` and a re-serialized
  `sourceInner`, and Markdown reassembles by splicing bytes at a `ByteSpanAnchor`, so nothing in the masking work is
  reusable — it needs a second walker, a character-to-byte offset bridge, pinned output settings for a nested jsoup
  parse, a rewrite of two shipped requirements, and a change to the text-coverage denominator with every Markdown
  fixture floor recalibrated. That is a change of its own, not a corner of this one. It moves to a new interstitial
  after `settle-writer-policy-and-document-lifetime`; task 1.2 records it in `CHANGE_BACKLOG.md` **with the falsified
  premise stated**, so it is not re-derived. Until then the shipped requirement *Preserve embedded raw HTML in Markdown
  without segmenting it* stays true and untouched.
- **The FB2 entity-duplication defect this work uncovered.** With entity references left unexpanded the XML parser
  reports both the skipped reference and the characters it expands to, so `<p>A&nbsp;B</p>` round-trips to
  `<p>A&nbsp;&#xa0;B</p>` — one non-breaking space in, two out, on every write. Masking does not cause it, but it
  does **double the rate**: measured, the plain write path adds one non-breaking space per cycle and the mask path
  adds two, because the restored fragment carries both the reference and its expansion and the fragment re-parse
  reports each again. It is deferred because it is not the one-line change it looks like: measured in the bytecode and
  behaviourally, `setExpandEntities(b)` writes the very `external-general-entities` feature key the next line of the
  parser configuration sets to `false`, so the obvious fix is a **no-op**; the configuration that does expand
  requires dropping that flag for an `EntityResolver`, changing how the offline invariant is enforced, and it drops
  the document's internal subset as well. Task 1.3 records it against
  `settle-writer-policy-and-document-lifetime`, which already owns the writer-policy defect family D1–D4, as D6.
- **Locked glossary terms** — masking one requires a populated glossary, which arrives with `add-consistency-stack`
  (change 15). This change ships the masking primitive it will use. `EC-INLINE-3`'s partial-word rule belongs there
  too.
- **Standalone and typographic numerals.** Deferred because the frozen spec never defines the line between a
  "standalone/typographic" numeral and a "prose" numeral, and `EC-INLINE-4` settles only what happens either side of
  it. Inventing that boundary here would fix it by accident. It belongs with the unit policy in
  `add-consistency-stack` (change 15), which is the first change that has a Book Brief to read it from.
- **Bare URLs in running prose.** Deferred by choice, not by dependency: a bare URL is knowable from the document
  alone, but recognising one is lexical pattern-matching with its own edge cases (trailing punctuation, parenthesised
  URLs, internationalised domains) rather than the structural walk this change is. A *linked* URL is already
  protected — an `<a href>`'s attributes ride inside its opening token, and a Markdown autolink is masked atomically
  precisely so its URL is never translated. Owner: `add-consistency-stack` (change 15).
- **Detected foreign-language inline runs** (`EC-FOREIGN-3`) — masking one requires Lingua, which lands with
  `add-metadata-units-and-language-detection` at the end of Stage B.
- **Metadata-unit segments** (`METADATA_TITLE`, `ALT`, `NAV_LABEL`, `FRONTMATTER_VALUE`). They mask identically once
  they exist, and they do not exist yet; same owner as above.
- **Exposing which two tokens form a pair.** The chunker must never split between an opening and closing token
  (`DD-19`, `02_Architecture/05_PIPELINE_ENGINE.md`), and a flat token→fragment map does not say which tokens are
  partners — for Markdown both fragments of an emphasis pair are the literal `*`, so it is not even inferable.
  Task 1.4 records it in `CHANGE_BACKLOG.md#decision-debt` alongside the pair-*ordering* hole (a model returning
  `⟦g1⟧old⟦g0⟧` satisfies the multiset), both owned by `add-chunking-and-context-assembly` (change 12), which is the
  change that needs the information.
- **The self-heal routing a gate failure triggers.** `:pipeline` is a Guice-module stub. This change ships the
  deterministic mechanism and the typed failure; the tiered loop that calls it and re-prompts with "restore exactly:
  …" belongs to changes 12 and 16.

**Each of the deferrals above lands in `document-round-trip`,** not in the deferring change's own capability — a
follow-up that adds a masking category is a `document-round-trip` MOD however it is scheduled.

## Capabilities

### New Capabilities

None. `openspec/specs/document-round-trip/` already exists.

### Modified Capabilities

- `document-round-trip`: parsing a book into an immutable skeleton plus an ordered segment list, and writing
  translated text back into the same nodes so the file re-opens identically. This change adds the masking half of
  that contract — the `⟦gN⟧` token scheme and its per-format masking rules, the unmask operation, the
  placeholder-multiset hard gate, the mask-time invariant, and the Markdown escaping and structure checks — plus two
  corrections to existing behaviour: a block whose only content is an inline code span stops being a segment, and an
  FB2 entity reference stops being emitted twice. **Four** shipped requirements are **MODIFIED**: *Exclude
  non-translatable blocks from segmentation*, *Prove that segmentation covers the document's translatable text*,
  *Gate every fixture through the port on a real file*, and *Verify the round trip against a local real-book corpus
  on demand*. Everything else is **ADDED**. An earlier draft of this proposal named *Parse FB2 as one XML document
  preserving its lexical form* as the single modified requirement; it is in fact **left untouched**, because every
  one of its scenarios is a zero-edit round trip and all of them still hold. The consequence for a *translated*
  segment — that a character reference's lexical spelling is not preserved — is carried by ADR-0031 instead, which
  is where a deviation belongs rather than in an edit to a shipped requirement whose own promises did not change.

## Impact

- **`:api`** (`modules/api`) — `DocumentPort` gains an unmask-and-validate operation returning `Result<String>`,
  taking the book format alongside the segment because the escaping rule differs by format. `SafeDetails` gains a
  placeholder-multiset component so a gate failure can carry every expected token without truncation, and the model-derived observed tokens within a bound that states what it omitted. `Segment.masked` and
  `Segment.placeholders` begin carrying real values; the record's **shape is unchanged** — only its Javadoc, which
  documented the empty values as the shipped contract. No new
  `ErrorCode` — `validation` already documents "including a QA hard-gate failure", and ADR-0022 fixed the enum at
  fifteen constants.
- **`:document`** (`modules/document`) — a new `ua.bookloom.document.mask` package, already reserved in
  `docs/implementation_plan/01_MODULE_INVENTORY.md` and named in `document/package-info.java`, but never created and
  **not** in fact named in `module-info.java`. `TreeNode` gains the two accessors an open/close split needs;
  `BlockSegmentWalker`, `MarkdownWalker` and `TxtReader` populate `masked`/`placeholders`; `BlockSegmentWalker` stops
  descending into an inline code span; `Jdom2TreeNode` carries the source's entity declarations into a restored fragment's parse.
- **Tests** — `SegmentTest` and `FixtureSweepTest` currently assert the *absence* of masking and are replaced by
  assertions of the real contract. The four golden round-trip tests are unaffected by construction: every writer
  early-returns on a null target, so a no-edit round trip never enters the composition path — and this change states
  that as a requirement rather than relying on it. New fixtures are added to `FixtureCatalog` in code, following the
  existing pattern; `:document` has no `src/test/resources` tree.
- **No new dependency.** jsoup 1.23.1, JDOM2 2.0.6.1 and commonmark-java 0.24.0 are already on the classpath, and
  the Markdown reader already parses with `IncludeSourceSpans.BLOCKS_AND_INLINES`, which is what makes an inline
  node's exact source range addressable.
- **One new ADR** — `ADR-0031` (mask to character data and compose the restored fragment), recording eighteen narrow
  deviations across `02_Architecture/03_DOCUMENT_MODEL.md`, `01_Product/03_DOCUMENT_FORMATS.md`,
  `02_Architecture/05_PIPELINE_ENGINE.md` and `02_Architecture/09_ERROR_HANDLING.md`.
- **Offline invariant** — untouched, and re-verified. Masking is pure local computation, and the entity declarations
  carried into a restored fragment's parse are inline text: an entity pointing at a file or a URL is still never
  resolved, checked against an external `SYSTEM` declaration.
