# ADR-0031 — Mask to character data and compose the restored fragment, rather than substituting into a markup string

**Status:** accepted **Date:** 2026-08-11
**Deciders:** architect
**Supersedes:** none

## Context and problem statement

`02_Architecture/03_DOCUMENT_MODEL.md#data-model` defines `Segment.masked` as "`sourceInner` with inline
descendants … replaced by `⟦gN⟧` placeholders", and `#unmask-and-validate` defines unmask as "substitute each
`⟦gN⟧` back to its original fragment". Read literally, both are **string operations over a markup string**:
`sourceInner` for EPUB and FB2 is serialized markup, so `masked` would be serialized markup with some spans
swapped for tokens, and unmask would be a token→fragment string substitution producing markup again.

That literal reading has two consequences the same chapter contradicts.

**One — the model does not see prose.** The chapter's own framing is that "the model sees clean block-level prose
interleaved with placeholders" (`#data-model`) and that masking exists so "the model sees clean prose and cannot
corrupt markup" (`#inline-masking`). A markup string is not clean prose: an EPUB paragraph reaches the model as
`Smith &amp; Sons &#8212; 1893`, and an FB2 one as `Smith &amp; Sons`. The model is asked to translate escaped
entity syntax, and its output is graded on preserving it.

**Two — a single stray character from the model corrupts the write-back.** Because unmask concatenates, everything
the model returned is re-parsed as markup by `TreeNode.replaceChildren` (ADR-0025). A model that writes `x < y`
or `Tom & Jerry` — both ordinary target prose — produces a fragment that does not parse. The shipped requirement
*Classify a malformed translated fragment as a validation failure*
(`openspec/specs/document-round-trip/spec.md`) already names this and classifies it correctly, but classifying it
is all anyone has done: the character itself still has no defined handling, and
`docs/implementation_plan/CHANGE_BACKLOG.md#decision-debt` assigns that question explicitly to change 5
(`add-inline-masking-and-placeholder-gate`) — "one fixes error classification at the write boundary, the other
fixes what the model is allowed to hand back in the first place."

The gap is genuine: the frozen spec states the *outcome* it wants (clean prose in, faithful markup out) and states
the *mechanism* in terms that cannot produce that outcome. Per `docs/implementation_plan/04_ADR_FORMAT.md`, a
deviation from a frozen clause is an ADR, never a spec edit.

## Decision drivers

- **The model must be given prose, not syntax.** Masking's entire purpose is that inline structure never reaches
  the model. Entity syntax is inline structure by another spelling.
- **A restored fragment must be well-formed by construction, not by hope.** After masking, every piece of markup
  in a translated segment came from the placeholder map, which `:document` built itself. Nothing the model returns
  needs to be markup, so nothing the model returns should be *parsed* as markup.
- **No failure mode should exist that the model cannot avoid.** FR-DOC-05 makes the placeholder multiset the
  un-overridable pre-condition, and a chunk failing it is a chunk the model can be told how to fix. "The model
  happened to type `<`" is not: the character is correct prose, the instruction to avoid it would be nonsense, and
  the repair round is spent on nothing. Every remaining structural check must name something the model can act on.
- **The round trip must not move.** The no-edit golden per format (DD-43) writes no target text at all, so whatever
  is chosen must be provably invisible to it.
- **`sourceHash` must not move.** `FR-ALGO-A3` keys the TM and change detection on the hash of the **unmasked**,
  NFC-normalized `sourceInner`. Whatever masking does, `sourceInner` and its hash are untouched.

## Considered options

- **Option A — Masked text is character data; unmask composes the fragment.** For markup-shaped formats, `masked`
  carries decoded character data plus tokens; unmask escapes the model-produced pieces as character data and
  splices the placeholder fragments as markup.
- **Option B — Keep `masked` as a markup string; unmask concatenates.** The literal reading. A stray `<` or `&`
  stays a `validation` failure, as the shipped requirement already specifies.
- **Option C — Keep `masked` as a markup string, but escape the whole unmasked result before writing.** Escape
  everything after substitution, then write.
- **Option D — Mask the entity references too.** Leave `masked` a markup string but give every `&amp;`, `&#8212;`
  and named entity its own `⟦gN⟧`.

## Decision outcome

Chosen: **Option A**, because it is the only option under which the model receives what the frozen spec says it
receives, and the only one under which a character the model typed cannot make a fragment malformed.

Concretely, and per skeleton kind:

**Tree skeletons (EPUB, FB2).**

- `masked` is built by walking the segment's run: a **character-data** node contributes its **decoded** text; every
  other node contributes a `⟦gN⟧` token and its exact serialized markup to the placeholder map. `masked` therefore
  contains no markup and no entity syntax — the `&` in `Smith &amp; Sons` reaches the model as `&`.
- **Unmask composes rather than concatenates.** The returned text is split on the placeholder-token grammar; each
  non-token piece has `&`, `<` and `>` escaped as character data; each token piece contributes its mapped fragment
  verbatim. The concatenation of those pieces is the fragment handed to `TreeNode.replaceChildren`, which re-parses
  it — which is why those three characters are sufficient and matching either serializer's full escaper is not
  required. Measured, the two serializers disagree beyond that set, so matching them would have been wrong as well
  as unnecessary.
- Consequence: a model that returns `Сміт & <Сини>` yields `Сміт &amp; &lt;Сини&gt;` — visible, correct, and
  well-formed. It is not a failure and costs no repair round.

**Buffer skeletons (Markdown, TXT).** Neither format has entity syntax to decode: a Markdown `sourceInner` is raw
source text (where `\*` and `&amp;` are literal source characters that the byte splice must not re-spell) and a TXT
one is decoded plain text. `masked` there is the same text with inline spans replaced by tokens.

The composition rule still applies to Markdown, because Markdown's markup **is** punctuation. Model-produced text
is character data there too, so on restore any run of model text that would parse as a construct the source did not
have is **neutralised** — backslash-escaped where CommonMark gives a backslash meaning, which is only before ASCII
punctuation; deleted where the construct is a hard line break's trailing spaces, which no backslash form removes;
and left as written where it is neither, for the structure check to report rather than a stray backslash reaching
the book — the exact counterpart of escaping a stray `<`
for EPUB. This covers block constructs as well as inline ones, and it has to: measured, a translated sentence
beginning `1985.` parses as an ordered list, `- як казав автор` as a bullet list, and `# Це не заголовок` as a
heading, and each is neutralised by escaping its marker — verified by re-parsing the escaped result. A four-space
indent is the one that is not cleanly escapable and is left as a known residual. Escaping the leading marker is the
difference between printing the translation and refusing the chunk over a character the frozen spec never protected. For TXT the escape step is
genuinely the identity: plain text has no markup to protect.

Escaping alone is not sufficient for Markdown, and this is the one place the two skeleton kinds genuinely differ.
A restored XML element is spliced back as a **node**, so its identity cannot be changed by the characters around
it. A restored Markdown delimiter is spliced back as a **character**, and whether it binds depends on its
neighbours: `⟦g0⟧ старі ⟦g1⟧` satisfies the placeholder multiset exactly and restores to `* старі *`, which is not
emphasis — the book loses its formatting with nothing failing. Escaping cannot prevent that, because nothing the
model wrote is wrong; what is wrong is what the source's own delimiters now mean.

So Markdown gets a second, narrow check: after restoring, the multiset of construct types the restored text parses
to must equal the source's. A difference is a `validation` failure with no repair attempted, exactly like the
multiset gate.

Three details make it work rather than misfire, each settled by measurement:

- **Both sides are parsed the same way, standalone, from the segment's own text.** Parsing the source side in
  document context and the restored side standalone would fail the *identity* restore of any segment holding a
  reference link, whose definition lives outside the segment — measured, `Some _emphasis_ here and a [ref][r].`
  yields `[Emphasis, Link]` in context and `[Emphasis]` alone. Same-way-both-sides makes identity pass by
  construction, at the cost of being blind to a reference link broken by translation. That blindness is recorded as
  a limitation, not papered over.
- **Soft line breaks are excluded from the comparison.** Rendering two source lines as one is legal Markdown and
  routine in translation. Hard line breaks are included, which is what catches the loss of a construct that has no
  source span of its own and therefore cannot be masked.
- **A multiset, not a sequence.** Translation reorders: `*старі* двері` against a source `the *old* door` yields
  `[Emphasis, Text]` against `[Text, Emphasis, Text]`, and a sequence comparison would reject a perfect
  translation.

*Alternatives considered.* **Normalizing the model's whitespace inside a pair** was rejected: trimming is a silent
reconciliation, and the gate's whole discipline is that it reports rather than guesses. **Comparing the sequence
rather than the multiset** was rejected on measurement — translation legitimately reorders, so `*старі* двері`
against a source `the *old* door` yields `[Emphasis, Text]` against `[Text, Emphasis, Text]` and a sequence
comparison would reject a perfect translation. **Doing nothing** was rejected because the failure is silent,
un-detectable downstream, and specific to the one format whose markup has no nodes.

This check is a **document-side precondition on a restore**, not a new entry in the deterministic QA gate's
enumerated check list (`02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`) — it fails a restore in `:document`
before any target text exists to score, in exactly the place the placeholder multiset is compared.

**A literal `⟦` or `⟧` in prose is a protected span, not an escape sequence.** `#inline-masking`'s escape rule
requires that a pre-existing bracket cannot be read as a placeholder token and is restored exactly. It does not say
how, and it names only the opening bracket. Both brackets are captured into the placeholder map like any other
protected span: the model never sees a bare bracket, the multiset hard gate protects the restoration for free, and
no second escape vocabulary exists to be mangled. This subsumes EC-INLINE-5 and EC-CODE-2 under one mechanism.

### Consequences

- Positive: the model is given prose. `Smith & Sons — 1893`, not `Smith &amp; Sons &#8212; 1893`. Prompt tokens
  drop and the untranslated-echo and length-ratio QA checks (change 14) measure text rather than syntax.
- Positive: a fragment cannot be malformed *because of a character the model typed*. The shipped
  malformed-fragment requirement stays reachable — a model that swaps a pair's tokens (`⟦g1⟧old⟦g0⟧`) satisfies the
  multiset and still restores to `</em>old<em>` — but the common case, a stray `<` or `&` in ordinary prose, is
  removed entirely.
- Neutral: this does **not** make the placeholder multiset the only structural check on a restored segment. Markdown
  gains a second one below, and pair ordering remains unchecked and is deferred with a named owner. The claim worth
  making is narrower: no *character-level* escaping failure survives.
- Negative: entity **lexical form** is not preserved across a *translated* segment. A source `&#8212;` re-emits as
  whatever the serializer writes for U+2014. DD-43 already permits this — "a faithful re-serializer may normalize
  entities" — and it cannot affect an untranslated segment, whose bytes are never rewritten.
- Negative: `masked` is no longer derivable from `sourceInner` by string substitution alone, so a reader comparing
  the two fields sees more than tokens differing. The `placeholders` map plus the composition rule is the whole
  contract; `sourceInner` remains the verbatim source and the hash input.
- Negative: an FB2 `CDATA` section subclasses `Text` in JDOM2, so it would arrive as character data and be
  re-escaped, losing its CDATA form and breaking a shipped round-trip obligation. It is therefore masked as an
  atomic protected span like a comment or a processing instruction — correct, but it means "character data" is a
  narrower category here than the parser's own type hierarchy suggests, and an implementation that tests
  `instanceof Text` gets it wrong.
- Negative: an FB2 entity reference is a node of its own in the XML tree, neither text nor element, so it is masked
  atomically like a comment. Its fragment is the reference itself (`&nbsp;`), and a fragment carrying one only
  re-parses if the entity is declared in scope — so the synthetic wrapper a translated fragment is parsed inside must
  carry the source document's internal subset. Measured: without it the parse fails with "The entity "nbsp" was
  referenced, but not declared", which would make every FB2 segment containing a non-breaking space impossible to
  write back; with it the reference survives as a reference. The subset is inline text, so no external resource is
  fetched and the hardened parser flags are untouched.
- Neutral: `sourceInner`, `sourceHash`, every anchor, and the four goldens are untouched. The no-edit round trip
  produces no target text, so no composition runs.
- Neutral: the `⟦`/`⟧` decision costs two placeholders in the rare segment that contains a mathematical white
  square bracket, and nothing anywhere else.

### Deviations from the frozen specification

| Clause | What it says | What this ADR does |
|---|---|---|
| `02_Architecture/03_DOCUMENT_MODEL.md#data-model`, `masked` row | "`sourceInner` with inline descendants … replaced by `⟦gN⟧` placeholders" | For EPUB/FB2, also entity-decoded, so `masked` is character data rather than a markup string. Markdown/TXT unchanged. |
| `#unmask-and-validate` step 1 | "**Unmask** — substitute each `⟦gN⟧` back to its original fragment." | Substitution of the tokens, plus escaping of the non-token pieces as character data, before the result is parsed back as markup. |
| `#inline-masking` escape rule | "each pre-existing occurrence is escaped before masking … and un-escaped on unmask" | The bracket is captured into the placeholder map instead. The observable obligation — it can never be read as a token, and the round trip restores it exactly — is met, and additionally protected by the hard gate. |
| `#inline-masking` escape rule | names `⟦` only | Both `⟦` and `⟧` are treated identically, because "which of the two needs protecting" is not a distinction any caller can act on. |
| `#inline-masking` | placeholders replace "inline descendant elements **and their tails**" | Only the markup is masked; the character data after an inline element's closing tag stays translatable. The worked example in `01_Product/05_TRANSLATION_ALGORITHM.md` reads the same way — `He opened the <em>old</em> door at 7 Baker Street.` masks to `He opened the ⟦g1⟧old⟦g2⟧ door at ⟦g3⟧.`, in which the tail ` door at ` is plainly not masked. That appendix is marked non-normative, so it corroborates rather than settles; what settles it is that masking tails would leave the model almost nothing to translate, which cannot be what a masking scheme is for. |
| `#inline-masking` | the **paired-markup group** shape is named only for "index-term/cross-reference anchors" | Every element enclosing translatable content is paired, not only anchors. The same non-normative worked examples do this for `<b>` and `<em>` (`⟦g1⟧Hale⟦g2⟧`, `⟦g4⟧…⟦g5⟧`); the load-bearing argument is that the alternative — one atomic token per inline element — hides the enclosed words from the model, which contradicts the chapter's own "the model sees clean block-level prose interleaved with placeholders". |
| `01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules`, and `02_Architecture/03_DOCUMENT_MODEL.md#inline-masking`'s "Only inline content is masked; block structure lives in the skeleton" | "Block code listings … are **not masked** — they never become segments at all" | True where such a block stands on its own, which is where the clause is aimed. A `<pre>` or block `<math>` nested **inside** a translatable block's run is masked atomically instead, because the alternative is handing raw `<pre>` markup to the model inside a paragraph. Note this is reachable only in a non-paragraph block: measured, an HTML parser closes an open `<p>` at a `<pre>` start tag, so the nesting survives in a text-owning `<div>`, `<li>`, `<td>` or `<blockquote>`, and for a `<math>` in a `<p>`. Its restored form must be identical to its source form, which the leading-line-break obligation already shipped makes checkable. |
| `01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts`, EC-IMG-1 | "Preserve bytes and references unchanged; **never translate image content**" | An inline `<svg>` in a prose block is masked **atomically**, so its `<text>` elements never reach the model. Left to the structural rule it would be paired and walked, and its text would become translatable — measured, and defensible on its own terms, but not what EC-IMG-1 says. The named-atomic set is therefore **five names**, against the two DD-49 names it started from, protected for two different reasons. `math` and `svg` are atomic in both tree dialects because the vocabulary that gives those names their meaning is foreign to both host formats rather than owned by either — a reason to keep them whole, not a reason to walk into them. `pre` and `listing` are atomic in both for an unrelated reason: they are the elements whose leading line feed the HTML parser discards, so masking one by anything other than its whole serialized form loses a line on every export. `code` is scoped to XHTML alone, because FictionBook uses it as an ordinary prose style. (Markdown has no named-element set at all; its masker reaches the same outcome through its non-paired catch-all.) An earlier edition of this row read "three elements" and was true when written; the second audit then measured an FB2 book handing a mathematical identifier and a figure's drawn caption to the model as prose, and the set grew. |
| `#unmask-and-validate` | orders it "1. Unmask … 2. Tag-multiset hard gate" | The gate runs **first**, and nothing is restored when it fails. Same outcome, and it removes the possibility of a half-restored fragment existing at all. |
| `02_Architecture/05_PIPELINE_ENGINE.md#qa-checks` | places the tag-integrity hard gate among the deterministic checks "in `:pipeline`" | The *mechanism* — unmask, compare, report — lives in `:document`, where `#unmask-and-validate` puts it and where `02_Architecture/02_MODULES_AND_LAYERING.md#module-document` assigns "inline masking, unmask+validate". `:pipeline` keeps the *routing*: it calls the mechanism and decides what a failure does. |
| `02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate` | enumerates the restore as exactly three numbered steps | Markdown adds two more, both before the fragment is written: model-introduced punctuation is escaped, and the restored text's construct multiset is compared against the source's. Neither is a new entry in the deterministic QA gate's enumerated check list — both are document-side preconditions on a restore, in the same place the multiset is compared. |
| `02_Architecture/03_DOCUMENT_MODEL.md#data-model` | writes the placeholder map as "`⟦gN⟧ → original fragment`", while `06_DATA_MODEL_SQLITE.md` comments the persisted column as "JSON map gN -> fragment" | The map is keyed by the bare index form `g0`. The two frozen documents disagree, and a persisted map cannot be read back under one spelling if it was written under the other, so one is chosen and stated normatively. |
| `02_Architecture/09_ERROR_HANDLING.md#safe-details-allowlist` | enumerates the safe-details allowlist as a closed list ending "QA finding names" | The list gains a placeholder-multiset entry, so a gate failure can carry every expected token, and the observed tokens within a stated bound (row below): the expected multiset is the system's own masking output and bounded by the segment, while the observed one is scanned out of the model's response through a grammar whose index is an unbounded digit run. The content is derived from the system's own tokens, never from book text, and the frozen list's own purpose — bounding what reaches a log file and a dialog — is unaffected. |
| `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, EC-CODE-3 | inline code "is masked as a single atomic protected `⟦gN⟧` placeholder" *within its segment* — so a code-only paragraph would be a segment whose masked form is `⟦g0⟧` | A block whose only translatable content is an inline code span yields **no segment**. Under the structural block rule such a paragraph owns no text of its own, so the walk descends into the code span and makes *that* the segment — handing the model an identifier with no masking possible. A segment whose entire content is one placeholder has nothing to translate and costs a chunk slot, so excluding it is both safer and cheaper. |
| `#inline-masking` | "tokens are matched **longest-first** by index width so that `⟦g1⟧` is never mis-matched as a prefix of `⟦g12⟧`" | Tokens are matched by the whole grammar `⟦g\d+⟧`, under which `⟦g1⟧` is not a prefix of `⟦g12⟧` at all. This is what the caution is asking for, obtained by construction rather than by ordering a substitution loop. |
| `01_Product/03_DOCUMENT_FORMATS.md#code-and-technical-content`, DD-49 | inline code "is masked as a single atomic protected placeholder" | Scoped to XHTML. FictionBook's `<code>` is a `styleType` prose style, not a code element — an FB2 paragraph written in it is text a reader reads, so it is masked as a paired group like any other inline element with children, not as an atomic span. |
| `02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`, as this ADR's own Markdown structure check states it | the restored text's construct multiset must equal the source's | For a heading or a table cell the comparison disregards **block** construct types on both sides. A heading's segment is the text after its `#` marker and a cell's is the text between its pipes — both markers live in the skeleton — so parsing the segment alone invents block structure the document never had: `1. Alpha beta` is an ordered list on its own. Counting it makes an ordinary numbered heading permanently untranslatable, because escaping can remove a construct the model added but never restore one the parse invented. Measured, 54 of 2,065 corpus Markdown segments across four books are in this position. A paragraph keeps the full comparison. **The carve-out holds in the loss direction only.** Because the multiset already disregards text nodes, disregarding block types as well empties *both* sides whenever the difference is purely block-level, making the comparison vacuous rather than relaxed: measured, a heading translation carrying a blank line and a second paragraph was accepted verbatim and written to disk as a heading *plus* a new block. So it is paired with a containment test on the characters that end the enclosing block — a line terminator for either kind, an unescaped `\|` for a cell — each compared against the source, so a segment already holding one is unaffected and a model that escapes its own pipe still succeeds. |
| This ADR's own Markdown escaping rule | model-introduced punctuation is **backslash-escaped** so it carries its characters literally | Two constructs cannot be neutralised that way and are handled differently. A hard line break spelled as trailing spaces has no backslash form that removes it — a trailing backslash *is* the other spelling — so its spaces are **deleted**, leaving any space that came from a restored fragment alone. And a marker that is not ASCII punctuation, such as the four spaces of an indented code block, is left as written for the structure comparison to report: CommonMark gives a backslash escaping meaning only before ASCII punctuation, so escaping there wrote a visible stray backslash into the book instead of refusing the case. **This applies to every construct the classifier reaches, with no per-construct exception** — it was first implemented in one of four branches, and the branch it was missing from turned a *setext* heading, whose marker is the underline on the following line rather than a character at the range's start, into twenty literal backslashes ahead of the translated text: measured in 2,330 of 35,290 accepted restores over the corpus. The guard now sits at the single point where a group becomes an insertion, so a branch cannot be added without it. |
| This ADR's own Markdown escaping rule, and the requirement's own "SHALL NOT fail" promise | model-introduced punctuation is neutralised so the operation never fails | Neutralisation is **bounded**. Each round re-parses the candidate and acts on one construct, so the round limit is in practice a cap on how many model-introduced constructs one segment may carry; measured, twenty-five independent emphasis pairs exhaust it with five still unescaped, and the structure comparison reports the mismatch as `ErrorCode.validation`. The bound is what stops a construct that re-parses to the same excess type after being acted on from looping without end — which a setext heading did, twenty times over, before the escapability guard was made universal. Recorded here because it was stated nowhere: it predates this change's second audit and neither audit swept it, and an unstated bound on a promise not to fail is the same defect shape that audit existed to close. |

Nothing in `docs/specification/**` is edited. Every requirement this ADR shapes is written in
`openspec/changes/add-inline-masking-and-placeholder-gate/specs/document-round-trip/spec.md`.

## Pros and cons of the options

### Option A — Masked text is character data; unmask composes

- Good: the model receives prose, which is what every clause about masking says it receives.
- Good: no character the model typed can make a fragment malformed, so the remaining structural checks all name
  something the model can act on.
- Good: one mechanism covers inline markup, atomic code/math, comments, entity references and literal brackets.
- Bad: entity lexical form is normalized in translated segments (permitted by DD-43, but real).
- Bad: `masked` is no longer a substring-level transformation of `sourceInner`, so the relationship between the two
  fields has to be stated rather than inferred.

### Option B — Keep `masked` as a markup string; unmask concatenates

- Good: the literal reading of the frozen text; no ADR needed.
- Good: entity lexical form survives a translated segment unchanged.
- Bad: the model is asked to translate `&amp;` and `&#8212;`, and is graded on reproducing them.
- Bad: a stray `<` or `&` in ordinary target prose fails the write, costing a repair round for a non-issue — and
  for FB2, whose parser is the stricter of the two, it fails far more often than for EPUB.
- Bad: leaves the backlog's assigned question ("what happens to the character itself") unanswered, which is the
  one thing change 5 was told to settle.

### Option C — Escape the whole unmasked result

- Good: trivially well-formed output.
- Bad: destroys the restored markup as well — `<em>` becomes `&lt;em&gt;`. This is precisely the defect ADR-0025
  exists to remove, reintroduced one layer up.

### Option D — Mask the entity references too

- Good: preserves entity lexical form exactly, and keeps `masked` a markup-shaped string.
- Bad: the model still sees markup, just less of it; a bare `<` from the model still breaks the write.
- Bad: DD-49 warns that "naive masking would explode the placeholder multiset". A corpus paragraph with a dozen
  `&nbsp;` and `&#8212;` would carry a dozen extra placeholders whose only job is to survive a round trip that
  DD-43 already declares free to normalize them.

## Links

- Design decisions: DD-07, DD-19, DD-43, DD-45, DD-49
- Spec clauses: `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#data-model`, `#inline-masking`,
  `#unmask-and-validate`, `#xml-round-trip-config`;
  `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-doc` (FR-DOC-04, FR-DOC-05);
  `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#inline-masking-rules` (EC-INLINE-5),
  `#code-edge-cases` (EC-CODE-2)
- Related ADRs: ADR-0003 (skeleton + segment model), ADR-0025 (reassembly replaces a run's inner content),
  ADR-0027 (structural block recognition), ADR-0029 (transcode to UTF-8 on unrepresentable target text)
- Changes: `openspec/changes/add-inline-masking-and-placeholder-gate/`
