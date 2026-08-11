# ADR-0026 — Adjudicate DRM by what is encrypted, not by the algorithm URI

**Status:** accepted **Date:** 2026-08-09
**Deciders:** architect
**Supersedes:** none

## Context and problem statement

EC-EPUB-1 (`01_Product/03_DOCUMENT_FORMATS.md#epub-edge-cases`) tells the importer to inspect
`META-INF/encryption.xml` and to refuse a book that declares content encryption or any unknown algorithm, while
allowing a book that declares "**only** the two known IDPF font-obfuscation algorithm URIs
(`http://www.idpf.org/2008/embedding` and `http://ns.adobe.com/pdf/enc#RC4`)".

`add-document-skeleton-and-epub-roundtrip` implemented that literally: `DrmAdjudicator` holds those two strings in
a `Set` and refuses the book unless every declared `EncryptionMethod/@Algorithm` is a member.

A survey of 194 real EPUBs found the rule misfires badly:

| Measurement | Value |
|---|---|
| Books carrying `META-INF/encryption.xml` | **30 of 194 (15.5%)** — not the rare case the rule assumes |
| Books containing actual DRM | **0** |
| Distinct algorithms declared | 2 — `http://www.idpf.org/2008/embedding` (14 books), `http://ns.adobe.com/pdf/enc#RC` (16 books) |
| `CipherReference` targets | **99 of 99 are font files** (`.ttf` 52, `.otf` 46, `.ttc` 1). Zero non-font resources are ever encrypted |

The Adobe algorithm appears in the wild as `http://ns.adobe.com/pdf/enc#RC`, without the trailing `4` the frozen
spec names. **16 of 194 books — 8% of an ordinary library — are refused today as DRM-protected when none of them
carries any content encryption at all.** Every one is a normal book whose only encrypted resources are four
obfuscated fonts.

The corpus also shows that the algorithm URI is the wrong discriminator in the other direction. The rule as written
inspects *only* the algorithm, so a book that encrypted its XHTML content documents using
`http://www.idpf.org/2008/embedding` would be **allowed** — the algorithm is on the list, and nothing checks what it
was applied to. That is the exact failure EC-EPUB-1 exists to prevent.

Two secondary facts, both measured, bear on how the check must be written:

- **Font media types are not one string.** The encrypted resources are declared in the OPF manifest as
  `application/vnd.ms-opentype` (57), `application/x-font-otf` (40) and `application/x-font-ttf` (2). Neither the
  file extension nor a single media type is sufficient; `.otf` appears under two media types and `.ttf` under two.
  A substring test for `font` matches none of the 57 `vnd.ms-opentype` cases.
- **`CipherReference/@URI` is relative to the container root, not to the OPF directory.** All 89 unambiguous cases
  resolve root-relative (`OPS/fonts/Charter-Roman.ttf` where the OPF itself lives in `OPS/`); zero resolve
  OPF-relative. Manifest `href`s, by contrast, *are* OPF-relative — and 102 of 194 books have a non-root OPF, so a
  single shared resolver silently mis-resolves one of the two.

## Decision drivers

- **Do not refuse a book that has no DRM.** A false refusal is total: the user cannot import the book at all, and
  the message tells them it is copy-protected, which is false.
- **Do not admit a book that does have DRM.** No partial import, per EC-EPUB-1's binding constraint.
- **Decide on evidence, not on spelling.** Vendors spell algorithm URIs inconsistently; what a book actually
  encrypted is a fact the archive states directly.
- **Fail closed on anything unrecognised**, so the next encryption scheme does not import silently.

## Considered options

- **Option A — Status quo.** Exact-match the two algorithm URIs from EC-EPUB-1.
- **Option B — Add the missing spelling** `http://ns.adobe.com/pdf/enc#RC` to the allowlist.
- **Option C — Adjudicate by the encrypted resource's kind:** allow only when every encrypted resource is a font.
- **Option D — Require both:** a known algorithm *and* a font target.

## Decision outcome

Chosen: **Option C.** The importer resolves every `CipherReference/@URI` **against the container root**, looks up
the matching OPF manifest item (whose `href` is resolved against the **OPF directory**), and allows the book only
when **every** encrypted resource is declared with a font media type. Any other outcome — a non-font resource, a
`CipherReference` with no matching manifest item, a malformed `encryption.xml` — refuses the book with the
DRM-blocked outcome and produces no partial import.

The font media-type allowlist is explicit:

```
application/vnd.ms-opentype      application/font-sfnt      font/otf     font/woff
application/x-font-otf           application/font-woff      font/ttf     font/woff2
application/x-font-ttf           application/x-font-truetype             font/collection
```

The first three are the values measured in the corpus; the remainder are the historical and RFC 8081 font types,
included so the next book does not require a code change to open. The declared algorithm URI is **recorded for
diagnostics and no longer gates the decision.**

This is stricter than the status quo where it matters and looser only where the evidence says the status quo is
wrong: a book encrypting a content document is now refused whatever algorithm it names, and a book encrypting only
fonts is now allowed however its vendor spelled the algorithm.

### Consequences

Positive:

- 16 measured books stop being falsely refused; the DRM-blocked outcome regains its meaning.
- A book that encrypts content documents under a font-obfuscation algorithm URI is now correctly refused — a hole
  the algorithm-only check left open.
- The check no longer depends on vendor URI spelling, which is the part that has demonstrably drifted.

Negative:

- **This is a documented deviation from EC-EPUB-1's stated mechanism.** The frozen specification is not edited; the
  requirement in `openspec/specs/document-round-trip/` cites this ADR.
- Adjudication now needs the OPF manifest, so it can no longer run before the OPF is parsed. `design.md` D7 of
  `add-document-skeleton-and-epub-roundtrip` deliberately adjudicated **first**, before anything else, to guarantee
  "no partial import". That guarantee is preserved differently: the OPF is parsed into memory, adjudication runs
  before any content document is read or any `Document` is constructed, and a refusal returns before anything is
  handed to a caller. Parsing an OPF is not a partial import — no unit and no segment exists yet.
- Two path bases now coexist in one code path (container-root for cipher references, OPF-relative for manifest
  hrefs). Getting them the wrong way round silently mis-resolves on the 102 books with a non-root OPF, so the two
  resolutions must be separate, named functions rather than one shared helper.

Neutral:

- The corpus contains **no genuinely DRM-protected book**, so the refusal path has no natural example and must be
  proven by a hand-authored fixture. Font obfuscation itself remains fully preserved — obfuscated bytes and
  `encryption.xml` are carried through unchanged, and the OPF `unique-identifier` (the obfuscation key for both
  algorithms) is never rewritten.

## Pros and cons of the options

### Option A — Exact algorithm allowlist (status quo)

Good: literal reading of EC-EPUB-1; no manifest lookup needed; runs before any parsing. Bad: measurably wrong on
8% of a real library, and blind to *what* was encrypted, so it would admit a content-encrypted book that reused a
font-obfuscation URI.

### Option B — Add the missing spelling

Good: one line; fixes the 16 measured books. Bad: fixes this spelling and no other — the next vendor variant fails
identically, and we only found this one because a book that happened to be on disk was inspected. Leaves the
"algorithm says font, target is content" hole entirely open.

### Option C — Judge by the encrypted resource (chosen)

Good: decides on what the archive states rather than on a string; closes the content-encrypted hole; robust to URI
drift. Bad: requires the manifest, so adjudication moves after OPF parsing; needs an explicit media-type list that
must stay current; a book with a font missing from its own manifest is refused (correctly — an unresolvable cipher
reference is not something to guess about).

### Option D — Require a known algorithm *and* a font target

Good: strictly the safest; both signals must agree. Bad: retains exactly the false-refusal behaviour that motivated
this ADR — the 16 `#RC` books have font targets but an unlisted algorithm, so they would still be refused. Rejected
because it fixes nothing measured while adding a second failure mode.

## Links

- Spec clauses (deviated from, unedited): `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#epub-edge-cases`
  (EC-EPUB-1), `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#images-and-fonts` (EC-FONT-1),
  `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#drm-and-language-detection` (EC-DRM-1),
  `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-import` (FR-IMPORT-04)
- Prior art: `openspec/changes/archive/2026-08-07-add-document-skeleton-and-epub-roundtrip/design.md` D7
- Consumed by: `openspec/changes/add-fb2-md-txt-roundtrip/`
- Rules: `.claude/rules/document-roundtrip.md`
