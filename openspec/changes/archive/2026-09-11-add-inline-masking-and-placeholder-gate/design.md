## Context

See `proposal.md` — *Why*. The design-relevant state of the code is narrower than the motivation, and several of
the decisions below were settled by running probes against the exact library versions this repo pins rather than by
reading their documentation.

- **Seam F1 is built and ADR-0025 already prepared it for this change.** `SkeletonAnchor` is a sealed interface over
  `NodeAnchor(nodePath, runIndex)` for tree formats and `ByteSpanAnchor(start, end)` for buffer formats.
  `SkeletonAnchors.writeBack` resolves an anchor to a run and calls `TreeNode.replaceChildren(from, to, markup)`,
  which **parses the replacement as markup, not as text**. ADR-0025's own context says why: "Once masking (change 5)
  restores inline fragments on unmask, `targetInner` holds inner *content* … Resolve it before change 5, not during
  it." Nothing in reassembly needs redesigning.
- **One walker serves both tree formats.** `BlockSegmentWalker` runs over the `TreeNode` adapter, implemented by
  `JsoupTreeNode` (EPUB) and `Jdom2TreeNode` (FB2). `TreeNode` exposes `tagName()` (null for non-elements),
  `ownText()` (a node's own character data, `""` otherwise), `childNodes()`, `markup()` and `replaceChildren(...)`.
  ADR-0027's rationale for a single adapter — "a rule written twice drifts the same way, silently" — applies with
  equal force to masking.
- **Markdown already parses with inline source spans.** `MarkdownReader` builds its parser with
  `IncludeSourceSpans.BLOCKS_AND_INLINES`; `MarkdownWalker` takes a segment's `sourceInner` as a raw substring of
  the original text; `MarkdownSpans` computes a block's replacement range as the union of its **inline** spans and
  holds a character-to-byte offset map (`byteOffsets`) that the byte splice already depends on.
- **Fixtures are Java, not resources.** `:document` has no `src/test/resources` tree. `FixtureCatalog.all()` yields
  30 cases at the time of writing (32 once this change adds its two hazard fixtures), each with a hand-counted segment count, expected kinds and a coverage floor.
- **Two tests currently assert masking's absence** and must be replaced, not deleted: `SegmentTest`
  (`masked_shipsEqualToSourceInner_withNoPlaceholders`) and `FixtureSweepTest`'s per-fixture assertion.

## Goals / Non-Goals

**Goals**

- One masking implementation shared by EPUB and FB2, expressed once against `TreeNode`, as ADR-0027 required of
  block recognition.
- Masking, unmasking and the gate are **pure functions of a format, a segment and a string**: no I/O, no clock, no
  randomness, so every requirement is provable by a unit test and the corpus sweep needs no new machinery.
- The gate reports; it never repairs. A caller gets a typed failure carrying both multisets in full and nothing else.
- The four goldens are provably untouched.

**Non-Goals (design level, beyond `proposal.md`'s scope boundaries)**

- No persistence work. `segments.masked` / `segments.placeholders` columns are specified in
  `02_Architecture/06_DATA_MODEL_SQLITE.md` but `:persistence` is a stub; storing them is change 8's job. **One note
  for that change:** `masked` is no longer derivable from `sourceInner` by string substitution, so both columns are
  load-bearing, and the map's key spelling is settled here as the bare index form `g0`, matching that document's own
  `-- JSON map gN -> fragment` comment.
- No change to run splitting or anchor computation. Masking operates strictly inside a run the existing walker
  already chose, and emits no anchor of its own.
- No new `ErrorCode`. `validation` already documents "including a QA hard-gate failure"; ADR-0022 fixed the enum at
  fifteen constants.

## Decisions

### D1 — The masked form is produced by a walk, not by rewriting a string

A masked segment is produced by walking the run's nodes and appending either character data or a token; the
placeholder map is filled in the same pass. `masked` is therefore a *product* of the walk, not a transformation
applied to `sourceInner`.

*Why not a string rewrite over `sourceInner`?* Because finding an element's boundaries inside a serialized string
means re-implementing an HTML/XML tokenizer — the exact class of bug ADR-0028 spent a change fixing, where a
self-closed `<script/>` swallowed a whole document. The tree already has the boundaries; use them.

*Consequence recorded in ADR-0031:* `masked` is not a substring transformation of `sourceInner`, and for EPUB/FB2 it
is entity-decoded. `sourceInner` and `sourceHash` are untouched.

### D2 — Node shapes, decided by type and structure, with five named exceptions

The classifier tests **node type first**, then name, then child count — in that order. Both adapters return
`List.of()` from `childNodes()` for every non-element, so "element with no child nodes" is true of a comment, a
CDATA section and a text node as well; testing type last would route all of them to the wrong arm, and
`closeMarkup()` on a void element yields the illegal `</img>`, which is what a reordering bug would emit into a book.

| Node in the run, in classification order | Emits | Fragment recorded |
|---|---|---|
| CDATA section | one token | the node's complete `markup()` |
| Other character data | its **decoded** text (EPUB/FB2) or its raw source text (MD/TXT) | — |
| Comment, processing instruction, entity reference | one token | the node's complete `markup()` |
| Element named `math`, `svg`, `pre` or `listing` (either dialect), or `code` (XHTML only) | one token | the node's complete `markup()` — **for EPUB, composed so that any `pre`/`listing` in the captured subtree gets its leading line feed restored**, whether the element itself or a descendant of it |
| Element with no child nodes | one token | the node's complete `markup()` |
| Any other element | an opening token, then its masked children, then a closing token | the start tag / the end tag |

**CDATA is the trap in this table.** In JDOM2 `CDATA extends Text`, and `Jdom2TreeNode.ownText()` tests
`content instanceof Text` — so a CDATA section reports its decoded text and would be classified as character data,
decoded into `masked`, and re-escaped on the way back as `a &lt; b`. That silently breaks the shipped requirement
"a CDATA section stays CDATA". The classifier must test for CDATA **before** testing for text, and the test suite
must cover it, because nothing else will catch it: `Fb2CanonicalAssert` compares canonical XML, under which the
escaped form and the CDATA form differ, but no fixture currently carries a CDATA section inside a translatable
paragraph.

**Why a name list at all, when the rest is structural.** "Never translate the interior" is a semantic property of
these elements that no structural signal expresses — they look like any other element. ADR-0027 argued against a
whitelist for *recognition*, where the structural signal (does it own text?) carried the information. Here it does
not. DD-49 names `code` and `math`; `svg` is added because an inline SVG has children, so the structural rule would
pair it and walk into its `<text>` elements, making a figure's drawn caption translatable — measured, and contrary
to EC-IMG-1's "never translate image content". **Revised after this change's second audit: five names, not three,
and only one of them is dialect-scoped.** `math` and `svg` are atomic in *both* tree dialects, because a vocabulary
FictionBook does not declare is a reason to keep foreign content whole rather than to walk into it — measured, an
FB2 book handed a mathematical identifier and a figure's drawn caption to the model as prose. `pre` and `listing`
join them for an unrelated reason: they are the elements whose leading line feed an HTML parser discards, so
masking one by less than its whole serialized form loses a line per export. Only `code` stays scoped to XHTML,
because FictionBook uses it for an ordinary prose style whose text a reader reads.

**An `EntityRef` is neither text nor element** — `tagName()` is null and `ownText()` is `""` — so it falls off the
end of a classifier that only asks those two questions. It gets its own row, and its fragment is the reference
itself. That has a consequence at the other end: see D11.

**A captured `pre` or `listing` needs the line feed put back at capture, not at write — and only for EPUB.** This
was written as a rule about a *nested* `pre`, which is the case that motivated it; the shipped rule is broader,
because the second audit measured a `pre` inside a captured `<code>` losing both of its line feeds. Composition
therefore walks the captured subtree and restores the line feed for **every** preformatted element in it, whether
that is the captured element itself or a descendant. An HTML parser
discards one line feed after a `<pre>` start tag, and the document-wide restorer already in the writer puts exactly
one back at serialization. A nested listing goes through a *second* parse when its fragment is re-parsed, so it
loses a second one and the restorer cannot tell. Measured: source `<pre>\n\ncode();` ends up as `<pre>code();`, and
prepending one at capture makes the whole document a fixed point. The XML parser discards nothing — measured, JDOM2
keeps both line feeds — so applying the same prepend there would *add* a line on every cycle. The rule is therefore
conditioned on the adapter, not written once for both. Block-level `math` needs no row of its own: the name rule
above already routes it to one atomic token.

### D3 — `TreeNode` gains `openMarkup()` and `closeMarkup()`

**Superseded in part while building: it also gained `type()`.** D2's classification tests node type first, and that
is not expressible through the shipped adapter — `tagName()` is null for a text node, a comment, a processing
instruction, an entity reference *and* a CDATA section, and `ownText()` returns the decoded text for a CDATA section
as well as for a text node, because `CDATA extends Text` in JDOM2 and `CDataNode extends TextNode` in jsoup. A
classifier built on `tagName()`/`ownText()`/`childNodes()` alone therefore cannot tell a CDATA section from
character data, which is the exact trap D2 names. `TreeNode.type()` returns a four-constant enum — `ELEMENT`,
`TEXT`, `CDATA`, `OTHER` — and no more: a comment, a processing instruction and an entity reference all emit one
atomic token of their own markup, so the classifier never needs to tell those apart. The heading originally read
"and nothing else", which was true of the open/close split and false of the enum.

Splitting an element into a pair needs its start tag and end tag as text. Deriving them by subtracting inner markup
from outer markup is string surgery on serialized output and breaks on the self-closing forms ADR-0028 already had
to repair. Two adapter methods are a dozen lines each.

- jsoup: `element.tagName() + element.attributes().html()`. Measured — this preserves attribute *order* and yields
  `<a href="ch2.xhtml#top" id="x1">`, but it lower-cases attribute names and rewrites single quotes to double. That
  is why the requirement says "as the format's parser records it", not "as written": jsoup has already normalized
  before masking sees anything, and a promise of byte-identical attributes is one the system cannot keep.
- JDOM2: `"<" + getQualifiedName()`, then every namespace `getNamespacesIntroduced()` reports, then **every
  attribute** — each written with its own qualified name and an XML-escaped value — then `">"`; and
  `"</" + getQualifiedName() + ">"` for the close. All three parts are load-bearing and two are easy to omit.
  `getName()` returns the *local* name, so a prefixed `<x:mark>` composes as `<mark>` and the prefix is lost.
  Attributes are not part of any name-or-namespace call at all, so omitting them silently drops `l:href` from every
  FB2 footnote anchor — measured against the shipped primary fixture, which carries exactly that shape, and it is
  the one thing `proposal.md` promises masking protects. `getNamespacesIntroduced()` rather than
  `getNamespacesInScope()`: measured, the in-scope call stamps the document's whole namespace scope onto every
  opening token, while the introduced call is correctly empty for a child of a root that declares the prefix and
  correctly populated for one that redeclares it locally. The composed tag was verified to survive
  `Jdom2TreeNode.parseFragment`'s in-scope wrapper in both cases.

**No `escapeText` on the adapter.** An earlier draft put escaping there so the composed fragment would match the
writer's serializer exactly. It does not need to: the composed fragment is **re-parsed** by `replaceChildren`, and
only then serialized by the writer's own settings. Escaping `&`, `<` and `>` is therefore sufficient and correct for
both parsers, and it is a pure function of the format rather than of a node — which is what lets the unmask entry
point take a format instead of a live tree. Measured, the two serializers' escapers genuinely disagree beyond that
set — on a text node jsoup renders U+00A0 as `&#xa0;` or `&nbsp;` depending on its escape mode while JDOM2 writes
the character, and JDOM2 escapes `]]>` while jsoup does not — so trying to match them would have been both
unnecessary and wrong.

### D4 — A literal `⟦` or `⟧` is a protected span, captured during the same walk

Character data is scanned for the two bracket characters as it is appended; each occurrence becomes its own token.
This runs *after* atomic spans have been captured, which is why EC-CODE-2 needs nothing extra: the brackets inside a
`<code>` span are already inside a fragment and are never scanned. The same reasoning covers an attribute value —
`<span title="⟦x⟧">` rides inside an opening token, so its brackets are never scanned either.

The mask-time bijection check must scan the **masked form only**, never the mapped fragments, for exactly this
reason: a code span whose own text is the literal `⟦g0⟧` is a legitimate book, and a check that scanned fragments
would reject it.

Escaping only `⟦` would be sufficient (a token cannot form without an opening bracket) but both are protected,
because "which bracket needed protecting" is a distinction no caller can act on and an asymmetric rule invites a
future reader to remove the wrong half. Recorded in ADR-0031.

### D5 — Unmasking composes; it does not concatenate

```
target ──split on ⟦g\d+⟧──►  [text, token, text, token, …]
                              │            │
              escape(format)◄─┘            └──► placeholders.get(token)   (verbatim, once)
                              └──────────► concatenated → replaceChildren / byte splice
```

Token matching is by the grammar `⟦g\d+⟧` as a whole, so the "longest-first" caution in the frozen spec — that
`⟦g1⟧` must not be mis-matched as a prefix of `⟦g12⟧` — is satisfied by construction rather than by ordering a
substitution loop. This is worth stating because the obvious implementation (iterate map keys, `String.replace`
each) has exactly the bug the spec warns about, and additionally re-expands a token that a restored fragment
contains.

Escaping by format: EPUB and FB2 escape `&`, `<`, `>`; TXT escapes nothing; Markdown escapes model-introduced
delimiters per D6.

### D6 — Markdown needs escaping *and* a structure check, and both were narrowed by measurement

Markdown's markup is punctuation, so the composition rule applies in both directions:

- **Model adds structure.** `звичайні *слова*` would gain emphasis the author never wrote. Neutralised, in the
  spirit of escaping a stray `<` for EPUB. The step is minimal rather than blanket: parse the candidate restored
  text, find any construct not attributable to a restored fragment, and neutralise the characters that formed it.
  Blanket-escaping every ASCII punctuation character would fill a translated book with backslashes.
  **Neutralising is not always a backslash, which an earlier draft of this decision assumed.** CommonMark gives a
  backslash escaping meaning only before ASCII punctuation, so escaping the four spaces of an indented code block
  wrote a *visible* stray backslash into the book while the structure check happily passed — measured. A marker
  that is not ASCII punctuation is therefore left as written for the check to report. And a hard line break spelled
  as trailing spaces has no backslash form that removes it — a trailing backslash *is* the other spelling — so its
  spaces are deleted instead, leaving any space that came from a restored fragment alone. Without that arm a
  translation ending a line with two spaces, which models produce routinely, was a guaranteed failure against this
  requirement's own promise that the operation SHALL NOT fail.
  **This must cover block constructs too**, with one residual: measured, `- `, `# ` and `1985.` are all neutralised
  by escaping their marker and re-parsing confirms it, but a four-space indent has no clean CommonMark escape — a
  leading backslash renders literally. It is recorded as a residual rather than claimed.
  **The block half in full:** Measured, a translation beginning `1985.` parses as an ordered list,
  `- як казав автор` as a bullet list, `# Це не заголовок` as a heading, and a four-space indent as a code block.
  Escaping only inline constructs would leave every one of those to be rejected by the check below — a chunk failed
  over a character the frozen spec never protected, and the exact posture the escaping rule exists to avoid.
- **Model loses structure.** `⟦g0⟧ старі ⟦g1⟧` restores to `* старі *`, which is not emphasis. Escaping cannot help
  — nothing the model wrote is wrong; what changed is what the *source's own* delimiters now mean. So after
  restoring, re-parse and compare.

**The comparison is a multiset of construct types, disregarding text and soft line breaks, with both sides parsed
the same way and standalone.** Each qualifier was forced by a measurement:

- *Multiset, not sequence.* Source `the *old* door` yields `[Text, Emphasis, Text]`; the perfectly good translation
  `*старі* двері` yields `[Emphasis, Text]`. A sequence comparison rejects a correct translation.
- *Both sides parsed the same way, standalone.* The shipped Markdown fixture's `Some _emphasis_ here and a [ref][r].`
  resolves its reference from a definition twenty lines away. Parsed in document context it yields
  `[Emphasis, Link]`; parsed alone, `[Emphasis]`. Mixing the two would fail the **identity** restore of that very
  fixture. Parsing both sides alike makes identity pass by construction — at the cost of being blind to a reference
  link broken by translation, which is recorded as a limitation rather than hidden.
- *Soft line breaks disregarded.* Rendering two source lines as one is legal Markdown and routine in translation.
  Hard line breaks are counted, as a second guard behind the token they now carry.
- *Block constructs are in the same multiset* for a paragraph-like block — but **not** for a heading or a table cell, whose segment is inline content owned by a marker the skeleton holds, so a block construct found by parsing it alone is an artifact of parsing rather than a property of the document (see the requirement's own `WHERE` clause and ADR-0031). For a paragraph, so `* старі *` at a segment's start — measured, a `BulletList` — is
  caught without a separate "same block kind" clause that would have rejected a translation beginning `1985.` before
  the escaping rule above was widened to cover it.

*Why not normalize the model's whitespace inside a pair?* Trimming is a silent reconciliation, and the gate's whole
discipline is that it reports rather than guesses.

*Why does EPUB/FB2 need no equivalent?* A restored element is spliced back as a node by `replaceChildren`; its
identity cannot be changed by the characters around it.

### D7 — Markdown masking uses inline source spans, with three measured caveats

Verified against commonmark 0.24.0:

- **A paired construct's delimiters are derivable.** `Emphasis` spans `[14,19)` for `*old*` while its `Text` child
  spans `[15,18)`, so the opening delimiter is `[node.start, firstChild.start)` and the closing is
  `[lastChild.end, node.end)`. `Link` behaves identically, giving `[` and `](ch2.md)`.
- **A construct may have more than one span.** `*bcd\nefg*` reports `Emphasis spans=[2,6) [7,11)` — two disjoint
  ranges. Every "the source range" phrasing must be plural, and the delimiter derivation must take the first span's
  start and the last span's end.
- **A hard line break has no span at all, and is masked anyway.** Measured, the two-trailing-spaces form yields
  `HardLineBreak` with an *empty* span list; only the backslash form carries one, and the two spaces live inside the
  preceding `Text` node's span — whose literal is `line one` while its span covers `line one␠␠`. The range is
  therefore derived by taking that node's **last** source span, reading the raw substring it covers out of the
  source text, and stripping its trailing spaces — *not*, as an earlier draft of this decision stated, "span-end
  minus literal-length": measured, the literal occupies the span's head rather than its tail, its length diverges
  from the span's whenever the text holds a backslash escape or a character reference, and for the spacer node
  commonmark actually emits (span length 2, literal length 0) that formula yields an empty range. Masking it matters
  because the alternative is a failure the model cannot act on: left unmasked, the two spaces are invisible in the
  prompt, routinely stripped by model output, and their loss surfaces only as a structure-check failure whose
  report — placeholder multisets, which match — says nothing about what went wrong. A token makes it something the
  model is told to preserve and the repair tier can name.
- **An autolink must be atomic, and destination-equality alone does not detect one.** `<https://x.org/a>` parses as
  a `Link` whose only `Text` child's literal equals its destination — but `<me@example.com>` does not: measured, the
  destination is `mailto:me@example.com` while the text is `me@example.com`, so an equality test pairs it and hands
  the address to the model as prose. The rule is therefore: a `Link` is atomic when its **source substring** is
  delimited by `<` and `>`, or when its sole `Text` child's literal equals its destination. Measured to distinguish
  `[text](url)` (paired) from `<https://x.org/a>`, `<me@example.com>` and `[https://x.org](https://x.org)` (all
  atomic).
- **A reference link is paired, and one form of it is a known limitation.** `[t][r]` and `[t][]` mask to `[` and
  `][r]` / `][]`, which is correct. A *shortcut* reference `[t]` masks to `[` and `]`, and a translated label that
  matches no definition silently becomes literal text on the next parse — invisible to the structure check, because
  both sides are parsed standalone where neither resolves. Recorded as a limitation with an owner rather than
  half-solved.
- **`HtmlInline` is per node, not per pair.** `<span class="x">b</span>` yields two *separate* `HtmlInline` nodes
  with a `Text` between them, and an unclosed `<br>` yields one with no partner. Each is its own atomic token; there
  is no pairing to model and no balance to assume.
- **Never use `Text.getLiteral()`.** Measured, the literal for source `A \* B` is `A * B` and for `AT&amp;T` is
  `AT&T` — the parser has already un-escaped. Masking must take `source.substring(span)`, or the byte splice writes
  back a re-spelled document.

### D8 — The `:api` surface is one new port method, taking the format

```java
Result<String> unmask(BookFormat format, Segment segment, String translatedMasked);
```

on `DocumentPort`. Masking itself needs no port method: it happens during `open`, and its output is already on
`Segment`.

*Why does it take the format?* Because the escaping rule and the structure check both differ by format, and
`Segment` carries none — it has `id`, `unit`, `order`, `kind`, `sourceInner`, `masked`, `placeholders`,
`sourceHash`, `prevKey`, `nextKey`, `anchor`, `targetInner`, `status`, `confidence`, and no format. The anchor kind
distinguishes tree from buffer but not EPUB from FB2, nor Markdown from TXT. Passing `BookFormat` is one parameter
and no record change; adding a component to `Segment` would touch eleven reconstruction sites in tests for a value
every caller already holds on the `Document` it opened.

*Why a port method rather than a `:document` internal helper?* Because `:pipeline` is the caller and may not depend
on `:document` — the layering rule routes every cross-module call through `:api`. `DocumentPort` has two methods and
one caller today, which is the cheapest moment this seam will ever be added.

*Why `Result<String>`?* The success case is exactly one value; the failure case is the envelope. A new result record
would duplicate it.

### D9 — The gate's failure detail needs a `SafeDetails` component of its own

`SafeDetails` is the only supported producer of `AppError.details`, and its existing QA-findings component caps at
10 entries of 120 characters each. A forty-placeholder segment — routine in the technical books this design's own
risk list names — renders `⟦g0⟧ ⟦g1⟧ … ⟦g39⟧` at roughly 240 characters and would be truncated, so the directed-fix
tier at change 16 would re-prompt with *part* of the formatting and silently drop the rest. That is worse than
re-prompting with none.

So `SafeDetails` gains a placeholder-multiset component — shipped as **two** fields, `expectedPlaceholders` and
`observedPlaceholders`, not one: a single blended field cannot be scanned back into two separate lists, and the
repair tier at change 16 needs to know which tokens were expected and which were actually seen, not just their
union. Three mechanics matter: it must bypass the 120-character per-field sanitizer, which a forty-token list would
otherwise truncate; it must be appended in the render order rather than inserted, so the existing positional
constructor call sites keep their meaning; and the caller must be able to read the tokens **as values**, not by
string-parsing the rendered detail — the repair tier at change 16 needs a list, and a caller that re-parses prose
gets it wrong the day the rendering changes. That third mechanic ships in a weaker form than stated here: `AppError`
carries only the rendered `details` string, not a `SafeDetails` instance, so a caller downstream of the envelope
still recovers the tokens by scanning the rendered string with the gate's own `⟦g\d+⟧` grammar — a scan, not an
interpretation, which is the requirement's own gloss on "readable back token by token" and is exactly what the
scan-not-interpretation phrasing anticipates. "As values" holds only at the point `SafeDetails` itself is built,
before `render()` flattens it to a string.

This is an extension of a frozen closed enumeration: `02_Architecture/09_ERROR_HANDLING.md#safe-details-allowlist`
ends its list at "QA finding names", and `SafeDetailsTest` asserts that the record's component set *is* the
allowlist. ADR-0031 carries the deviation row, and the test's allowlist constant is updated with it — that test
going red is the intended signal, not an accident.

### D10 — Package and module wiring

New package `ua.bookloom.document.mask`, reserved by `01_MODULE_INVENTORY.md` and named in
`document/package-info.java` — though **not** in `module-info.java`, contrary to what an earlier draft of this
change claimed. It holds the unmasker, the token grammar, the mask accumulator and the multiset comparison. **It does not hold the maskers, contrary to this decision as first written**: `TreeMasker` lives in `.model` beside the `TreeNode` it walks and `MarkdownMasker` in `.md` beside commonmark's, because a masker in `.mask` walking `TreeNode` would make `.model` and `.mask` mutually dependent — `.model` already depends on `.mask` for the accumulator, as static-utility
classes with `@NoArgsConstructor(access = AccessLevel.PRIVATE)` per ADR-0024, matching `BlockSegmentWalker` and
`BlockRuns`. No `exports` and no `opens` are needed: `:document` exports only `ua.bookloom.document`, the masker is
consumed from `.model`/`.md`/`.txt` inside the same module, and static-utility classes need no Guice reflection.
`DocumentService` is the boundary that wraps its failures into the envelope.

### D11 — The pre-existing defects, one fixed and one deferred

**A standalone inline code span.** `BlockSegmentWalker.collect` skips its excluded set before descending, while
`emitRuns` includes every child of a text-owning block. Excluding `code` therefore does exactly what is wanted: a
`<p>` whose only child is `<code>` is never descended into and yields no segment, while a `<code>` inside a
text-owning paragraph is still in the run and still masked. Measured, no existing fixture's segment count or kinds
move, because every `<code>` in the repo's fixtures already sits inside a `<pre>`.

The exclusion is **per format**, unlike `pre` and `math`. FictionBook's `<code>` is an inline *style* element used
for ordinary prose, so an FB2 paragraph written entirely in it is text a reader reads; excluding it there would
silently drop real content. The walker's excluded set is consulted through the adapter, so this is where the two
formats stop sharing a rule. **The claim this paragraph originally made — that the corpus sweep counts blocks
skipped this way per book — is not what shipped.** The probe records `skippedCodeOnlyBlocks` as `notMeasured`
rather than a count, because reproducing the walker's predicate inside a harness excluded from CI would be a second
copy of intricate production logic that nothing would notice drifting; an honest absent value beats a zero that
reads as "none found". The number was obtained instead by diffing segment counts against the previous corpus run:
**44 blocks across 3 books**, every one an inline-code-dense technical book, with all 205 other common books
identical in segment count and kind histogram.

**A restored FB2 fragment carrying an entity reference — fixed here.** `Jdom2TreeNode.parseFragment` wraps a translated fragment
in a synthetic root declaring the namespaces in scope — but no DOCTYPE. Measured, a fragment containing `&nbsp;`
therefore fails with "The entity "nbsp" was referenced, but not declared", which would make every FB2 segment
containing a non-breaking space impossible to write back; the corpus carries 10,377 of them. The wrapper carries the
source document's internal subset as well, which is verified to work under the existing hardened parser
configuration, keeps the reference a reference, and still refuses an undeclared entity. The subset is inline text,
so nothing is fetched and no security flag moves.

**The entity-duplication defect — deferred.** The same measurement turned up a separate, pre-existing defect: with
entities left unexpanded the XML parser reports *both* the skipped reference and the characters it expands to, so
`<p>A&nbsp;B</p>` round-trips to `<p>A&nbsp;&#xa0;B</p>` — one non-breaking space in, two out, on every write.
Masking does not cause this, but — measured after the fact, correcting an earlier claim here that it "neither causes
nor worsens" it — masking **doubles its rate**. The plain write path adds one U+00A0 per cycle; the mask path adds
two, because the restored fragment carries both the `&nbsp;` reference and the already-expanded character, and
`Jdom2TreeNode.parseFragment` re-parses that fragment under the same non-expanding configuration, which reports the
reference and its expansion a second time. The rate is worse; the defect, its owner and its fix are unchanged. The
obvious one-line fix is a
**no-op**: `setExpandEntities(b)` writes the very `external-general-entities` feature key the next line of
`SecureXml` sets to false, verified in the bytecode and behaviourally. The configuration that actually expands
requires dropping that flag and substituting an `EntityResolver` — a change to how the offline invariant is enforced,
which additionally drops the document's internal subset. That belongs with `settle-writer-policy-and-document-lifetime`,
which already owns the writer-policy defect family, and is recorded there as D6.

## Risks / Trade-offs

- **The FB2 golden cannot currently catch a masking whitespace defect** → `Fb2CanonicalAssert.canonicalize`
  whitespace-collapses text before comparing. Mitigation: the mask-then-restore identity check compares segment
  content directly rather than through that comparator — and, as shipped, **without collapsing whitespace of its
  own**. That distinction is the whole mitigation and was nearly lost: the comparator was first written collapsing
  whitespace exactly as the golden does, which would have inherited the blind spot it exists to cover. Verified
  whitespace-exact against all 30 fixtures and 212 corpus books, so nothing on the restore path legitimately
  normalizes whitespace. One difference remains invisible whatever the comparator does: XML 1.0 §2.11 makes the
  *parser* normalize a carriage-return/line-feed pair to a bare line feed, so a `\r\n`-for-`\n` regression — the
  class of the `Jdom2TreeNode#markup` line-separator defect this change fixed — is caught only by a direct
  assertion on the captured fragment, never by this comparison. `GoldenComparisonMetaTest` pins all three: the
  namespace stamping it absorbs, the whitespace changes it catches, and the CRLF normalization it cannot see. **The identity check does not catch the deferred
  entity-duplication defect** — measured, masking is faithful to whatever the tree holds, so a doubled non-breaking
  space masks and restores identically and the probe passes. What *would* go red is the FB2 zero-edit golden, which
  is why the entity fixture stays out of `FixtureCatalog` and the entity path is exercised in unit tests of the
  fragment parse instead. Mitigation: task 7.13 says so explicitly.
- **Character-reference spelling is normalized** → `&#8212;` re-emits as `—`. Mitigation: DD-43 permits it
  explicitly and the canonical comparators treat the two forms as the same character. A declared entity reference is
  *not* normalized — it is masked atomically and restored as a reference, which is why the fragment's parse needs the
  declaration in scope.
- **The Markdown structure check costs an inline parse per translated segment** → measurable on a large book.
  Mitigation: it runs only on the unmask path, which by definition already involved a model call several orders of
  magnitude more expensive, and it parses one segment rather than the document.
- **A model that reorders a *pair* passes the multiset gate** → `⟦g1⟧old⟦g0⟧` has the same multiset as
  `⟦g0⟧old⟦g1⟧` and restores to `</em>old<em>`; so does concatenating `⟦g0⟧⟦g1⟧` where the source separated them,
  which restores to an empty `<em></em>` and deletes the words. Both are out of scope here and named as such: the
  multiset gate is what FR-DOC-05 specifies, and pair ordering needs pair membership, which the flat map does not
  expose. Recorded in `CHANGE_BACKLOG.md#decision-debt` with `add-chunking-and-context-assembly` as owner, together
  with the pairing-exposure gap that the chunker's "never split a pair" obligation also needs.
- **Restored fragments reach `replaceChildren` for real, for the first time** → the path exists and is exercised
  only with null targets and the corpus harness's marker text. `Jsoup.parseBodyFragment` also does *not* apply
  `XhtmlParser`'s self-closed-non-void repair (ADR-0028) or its output-settings pinning, so a fragment carrying a
  self-closed non-void element would re-trigger the defect ADR-0028 fixed. Mitigation: every mapped fragment is
  produced by serializing a node from an already-repaired tree, so the self-closing form cannot appear in one; the
  mask-then-restore identity requirement drives every fixture through the path, and the corpus sweep gains a probe
  that does the same across 213 real books.
- **`FixtureCatalog`'s hand-counted expectations grow a fourth column across 31 cases** → more hand-counting, and a
  wrong count is a test that proves nothing. Mitigation: the count is asserted alongside the existing segment count,
  which has caught exactly this class of error before; and the mask-then-restore identity check, which is the real
  proof, does not depend on the hand-counts at all.
- **The identity comparator is not string equality** → measured, `sourceInner` for FB2 is produced by a serializer
  that stamps in-scope namespaces inline (`<a xmlns=… xmlns:l=… l:href="#n1">`) while a composed opening tag carries
  only what the element introduces, so a restored fragment differs textually from `sourceInner` for *every* FB2
  segment holding an element — and re-parsing it recovers the source form exactly. The same holds for a nested
  `<pre>` whose capture prepends a line feed. Mitigation: the identity check compares by re-parsing the restored
  fragment in the same scope and comparing canonically, never by string equality; task 9.2 names the comparator.
- **A translated Markdown table cell containing `|` breaks its row** → the multiset gate passes and the structure
  check passes, because a cell's text parses the same way with or without the pipe when read standalone. Out of
  scope and recorded with the reference-link limitation; a cell-level escape belongs with whoever owns table
  fidelity.
- **A shipped test asserts the `SafeDetails` allowlist and will go red by design** → `SafeDetailsTest` compares the
  record's component set against a constant. The task list updates both together; the point of the test is that this
  cannot happen silently.
- **Coverage is a bundle-level 0.80 branch gate per module, and a masker is branch-dense** → a thin test set drags
  the whole `:document` bundle below the line. Mitigation: the task list names the branches that are easy to miss —
  CDATA, comment, processing-instruction and entity-reference arms; `openMarkup`/`closeMarkup` on a non-element and
  on an element with zero attributes; both `getNamespacesIntroduced()` arms and both `getQualifiedName()` arms; a
  lone `⟧`, `⟦g⟧` with no digits, and `⟦g007⟧` with leading zeros; the identity escape arm for TXT; the Markdown
  escape arm where a construct *is* attributable to a restored fragment and must not be escaped; the structure
  check's block-construct and hard-line-break arms; and the restore boundary's `validation`, `internal` and
  wrapped-`Throwable` arms.

## Open Questions

None that can be deferred. Every decision that could have changed the specs — the escape mechanism, whether model
text is character data, the masking scope within FR-DOC-04, whether Markdown gets a structure check, whether the
Markdown raw-HTML half stays in this change, and how the two pre-existing defects found while building it are
treated — was resolved before this document was written and is recorded in ADR-0031 and `proposal.md`.

Three limitations are recorded rather than solved, each with an owner in `CHANGE_BACKLOG.md`: pair membership and
pair ordering (change 12), a Markdown shortcut reference link whose translated label matches no definition, and a
`|` written into a Markdown table cell's target.
