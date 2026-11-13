# article-gravity — the reference article

One invented popular-science article, *The Quiet Pull: How Gravity Shapes Our World* (about 2,000 words), in English,
Ukrainian, Russian and French, each as Markdown, EPUB 3 and FictionBook 2.1. It is a short test book that holds
everything a translation must keep — code, formulas, diagram source, tables, links, DOIs, images and foreign-language
quotations — so the app can be checked on all four languages and all three formats, and a later run can be compared
with the reference translations. Added by redesign task 1.11 (`openspec/changes/archive/2026-11-12-redesign-translation-pipeline/tasks.md`,
design D24). All text is invented; no owner book text is used.

## Files

| Path | What |
|---|---|
| `en/gravity.md` | the source, GitHub-flavoured Markdown |
| `uk/gravity.md`, `ru/gravity.md`, `fr/gravity.md` | the reference translations |
| `<lang>/gravity.epub` | EPUB 3 built from that language's Markdown |
| `<lang>/gravity.fb2` | FictionBook 2.1 built from that language's Markdown |
| `<lang>/Picture_of_earth.jpg` | the one local image (four identical copies, so each folder builds on its own) |
| `manifest.json` | the hash manifest (`DatasetManifest`): the 12 books and the 4 image copies; text files hash with LF line ends, the EPUBs and the image by their raw bytes, as `sha256sum` prints them |

The structure is pinned by `ArticleGravityDatasetTest`; the manifest by `DatasetManifestCheckTest`; the eval that runs
today's pipeline on the English source is `ArticleEvalTest` (`--suite article`, see `scripts/eval-matrix.sh`).

## How the files were made

1. **English** was written by an Opus agent.
2. **Ukrainian, Russian, French** were translated from the English by Opus agents, then each translation was reviewed by
   three Codex models (`gpt-6-luna`, `gpt-6.1-terra`, `gpt-6.1-sol`) through the Codex CLI, by hand and on this invented
   text only. A change was applied when at least two reviewers agreed or it was clearly right. The review notes and the
   per-item decisions are in `.temporary_context/eval-artifacts/codex-review/article/` (git-ignored, local only). The
   fixed decisions are:
   - the HTML comments and the metadata footer (`<!-- metadata: … -->` and the `**Document version:** … **ID:**
     \`bl-test-gravity-en\`` line) stay verbatim English in every language;
   - the table-of-contents anchors follow each language's translated headings (GitHub-style slugs);
   - the foreign quotations stay in their original language with a gloss beside them: the Latin *Hypotheses non fingo*,
     the German sentence, the French sentence and the Ukrainian sentence (in the French text the French quotation is
     native and uses French spacing, with narrow no-break spaces inside « »);
   - code, URLs, DOIs and identifiers are untouched (comments and labels inside code blocks and diagrams may be
     translated; every snake_case identifier stays).
3. **EPUB and FB2** were built from each Markdown by `scripts/md-to-books.py` (standard library only, no network,
   byte-reproducible: running it again gives identical files). From inside a language folder:

   ```bash
   python3 ../../../../../../../../scripts/md-to-books.py gravity.md --lang xx --title "..." \
       --author "BookLoom Test Desk" --out-dir . --date 2026-10-28T00:00:00Z
   ```

   (from the repository root: `scripts/md-to-books.py <folder>/gravity.md --lang xx --title ... --author "BookLoom Test
   Desk" --out-dir <folder> --date 2026-10-28T00:00:00Z`). The titles are each Markdown's first heading:

   | `--lang` | `--title` |
   |---|---|
   | `en` | `The Quiet Pull: How Gravity Shapes Our World` |
   | `uk` | `Тихе тяжіння: як гравітація формує наш світ` |
   | `ru` | `Тихое притяжение: как гравитация формирует наш мир` |
   | `fr` | `L’attraction discrète : comment la gravitation façonne notre monde` |

   After regenerating, rewrite the hashes in `manifest.json` (its `files` sorted by path, `rootHash` = SHA-256 of the
   lines `path<TAB>sha256<LF>`); `DatasetManifestCheckTest` fails until they match.

## Image attribution

`Picture_of_earth.jpg` is the Wikimedia Commons file "Picture of earth.jpg", an open-licence picture as stated by the
owner. Recheck the exact licence text and author on its Commons page before redistributing it outside this repository.
The remote image in section 4 (`https://example.org/images/torsion-balance-schematic.svg`) is a placeholder address:
it is never fetched — the EPUB keeps it as an external `<img src>`, the FB2 as a link.

## What each element tests ("do not translate" items)

| Element | Where | Kept |
|---|---|---|
| HTML comments | first line, before the footer | verbatim English |
| Metadata footer | last line | verbatim English, including `bl-test-gravity-en` |
| Table of contents | `## Contents` | link targets follow the translated headings |
| Inline code | `F`, `G`, `` `9.81` ``, `` `gravity.py` ``, `` `R⊕` `` … | untouched |
| Fenced code (`text`, `json`, `python`, `bash`) | §3, §4, §8, §9 | info strings in the same order; identifiers (`EARTH_MASS`, `surface_gravity`, `station_id`, `g_mGal`, `st_001` …) untouched; comments may be translated |
| Diagram source (`mermaid` flowchart and sequence diagram, ASCII diagram) | §3, §6 | syntax and node ids untouched; labels may be translated |
| Formulas (`$$ … $$` LaTeX, Unicode superscripts, `t = √(2h / g)`) | §3, §9 | untouched |
| Tables with alignment and inline code | §3, §8 | column structure and alignment |
| URLs and DOIs | References, image, `[esa]` reference link | byte-identical (`ArticleGravity.protectedSpans`) |
| Local image `./Picture_of_earth.jpg` | §5 | same reference; embedded in EPUB and FB2 |
| Remote image | §4 | stays a reference, never fetched |
| Latin *Hypotheses non fingo* | §2 | Latin |
| German „Die Waage lügt nie; …“ | §4 | German |
| Ukrainian «Тут ніщо не падає — …» | §6 | Ukrainian (native in `uk`) |
| French « La pomme ne tombe pas : … » | §7 | French (native in `fr`) |
| GitHub alerts, `<details>`, `<kbd>`, `<sup>`, task list, nested lists, escapes `\*` `\_`, strikethrough, hard line break | throughout | Markdown structure |
| Numbers with units, ISBN, e-mail address | throughout | values unchanged (decimal separators follow the language) |
