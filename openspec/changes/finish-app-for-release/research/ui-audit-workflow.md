# UI polish audit — workflow screens and the review desk (2026-11-14)

Scope: Projects, Import book, Book Brief, Names & style, Translating, Export, the review desk, and the dialogs opened
from them. Shots: `shots/` as listed in `INDEX.md`. Coordinates are window points (1440 × 900 pt) unless stated.
Shots 207 and 206 are the same picture (207 does not show the Activity log).

## Summary

1. The "How progress is counted" fold is an unstyled JavaFX `TitledPane` (grey gradient header, hard border) on
   Translating and in the progress dialog; every other fold is flat. It is the most visible "not finished" spot.
2. The banner strip above a screen is 12–20 pt wider than the cards below it, and scrolled cards slide directly under
   it with no gap — seen on every screen that shows a banner.
3. Names & style tables: header words sit about 9 pt to the right of the cell text, the translation field changes
   width from row to row, and at 1000 pt cells wrap and a default black menu arrow appears.
4. Translating live card: the segment list truncates every text ("Chapter …") beside a half-empty detail pane; the
   English text is centred vertically while the Ukrainian text is top-aligned; in Detailed the prompt text is cut
   off at the right instead of wrapping.
5. Review desk: the English and Ukrainian boxes have different tops and heights; the "Readable view" shows two lines
   plus the clipped top of a third; up to three scrollbars stack on the right.
6. Export: the four tiles have four different widths that change with the state; read-only and editable fields in
   one row differ in height by 4 pt; the "Proofread: …" line is printed twice, once loose between cards.
7. Book Brief: switches sit at the end of each label (ragged column); the right column is empty for two screens.
8. Banners: the info glyph is a small serif "i" hanging above the title, another banner uses a circled "i"; a
   title-only banner sets a whole sentence in bold; title and body are about 34 pt apart.
9. Dark theme is clean; one real problem is the locked language fields on Book Brief, which are barely readable.
10. Overall: the screens are consistent in palette, type scale and card look, and Ukrainian fits almost everywhere.
    What is left is alignment and container-edge work, most of it in five shared places (banner slot, `TermTableCard`,
    `TitledPane` style, live card, review compare panes).

Counts: 4 High, 17 Medium, 16 Low (37 items).

## Defects

| ID | Screen | Shot file(s) | What is wrong | Severity | Likely cause | Suggested fix | Confidence |
|---|---|---|---|---|---|---|---|
| W-01 | Translating, progress dialog | 40, 41, 47, 80, 31, 147, 174 | The "How progress is counted" fold has the default JavaFX look: a grey gradient header with a hard border, full card width. "About this book" and "Prompt context" folds are flat. | High | `control/HowCountedPane.java` extends `TitledPane` and adds no style class; `theme.css` has no `titled-pane` rule at all (0 matches) | Give it the same style class as the flat folds (the one `AboutBookFold` / `PromptView` use), or add one flat `.titled-pane > .title` rule | sure |
| W-02 | All screens with a banner | 80, 81, 140–149, 172, 173, 189 | The banner strip ends 12–20 pt further right than the cards below (banner right edge 1413 pt, cards 1401 pt; on Import and Brief 1393 pt). The cards leave room for the scrollbar, the banner does not. | Medium | `control/BannerSlot` sits outside the screen's scroll pane; the scroll pane reserves a gutter | Give the banner slot the same right inset as the scrolled content (or put the gutter outside both) | sure |
| W-03 | All screens with a banner | 81, 09, 173 | Scrolled content passes directly under the fixed banner: a card's text starts 5–8 pt below the banner edge and the card's side borders come out from under it. No gap, no line. | Medium | same slot as W-02; scroll pane starts at the banner's bottom edge with no top padding | Add the normal card gap (about 14 pt) as fixed space between the banner slot and the scroll pane | sure |
| W-04 | Translating | 80 vs 47, 51, 206 | The gap between the top banner and the first card is about 37 pt when finished or paused, 15 pt while running (the keep-awake banner). An invisible node still takes its height. | Medium | not located; probably the hidden heading/subtitle or an empty banner in `TranslatingView` left `managed` | Bind `managed` to `visible` for the hidden node | likely |
| W-05 | Names & style (both tables), results dialog | 68, 39, 33, 185, 35 | Header words are 8–9 pt to the right of the cell text in every column (Term 297 vs 288 pt, Kind, Translation, Used). In the results dialog the header "Term" is 8 pt to the left of the rows instead. | Medium | `screen/TermTableCard.java:65` (header cell padding) differs from the cell padding; `ProgressResultsCard` header row | Use one left inset for header and cell | sure |
| W-06 | Names & style, results dialog | 39 (row "dr"), 35 | The translation field changes width per row: 230 pt in a row without the "likely" chip, 187 pt with it; in the results dialog the "After" fields have five different widths (from 165 to 262 pt) depending on the text after them. | Medium | `GlossaryCells` / `ProgressResultsCard`: the field takes the space the trailing chip or text leaves | Fixed width for the field, trailing chip/text in its own fixed slot | sure |
| W-07 | Names & style, narrow | 172, 173 | At 1000 pt the toolbar overflow shows "⋯" and next to it a black default arrow "▶". | Medium | `TermTableCard` overflow `MenuButton`: the `.arrow` is not hidden | Hide the arrow of that menu button in CSS (as the Projects "⋯" does) | sure |
| W-08 | Names & style, narrow and Ukrainian | 172, 185 | Kind and Gender cells wrap to two lines ("character · / likely", "чоловічий · / ймовірно") while the neighbouring row of the same length does not, and the translation field cuts "Гарроу Вей" with no ellipsis. The empty Forms column keeps about 110 pt. | Medium | fixed column widths in `GlossaryTable`; Forms keeps its width when empty | Let Forms shrink when no row has forms; no wrap in Kind/Gender | likely |
| W-09 | Names & style | 68, 145, 172 | The glossary table has its own scrollbar inside the page scrollbar, 12 pt apart; the header row is wider than the rows by the bar's width. | Low | `GlossaryTable` fixed height with its own virtual scroll | Note only (re-layout is out of scope); at least align the header's right edge with the rows | sure |
| W-10 | Names & style | 39, 173 | About 65 pt of empty card under the last recurring term. | Low | `TermTableCard` minimum table height | Fit the table to its rows when they are few | likely |
| W-11 | Translating, running | 47, 48, 54 | The segment list is about 225 pt wide and cuts every text ("Chapter …", "Summar…", "Mass …") while the detail pane beside it is mostly empty. After the run the same list is 375 pt wide (80). | High | `control/LiveRowList` width (`.live-rows`, `theme.css:2203`) differs between the running and the finished layout | Use the finished-state width in both | sure |
| W-12 | Translating | 47 | In the selected row's detail the English text sits 9 pt lower than the Ukrainian text: the shorter side is centred vertically, the longer starts at the top. | Medium | `control/LiveRowDetail`: the two text cells have no common top alignment | Top-align both cells (`GridPane` valignment TOP) | sure |
| W-13 | Translating Detailed, review desk "What the model saw" | 54, 55, 59 | The prompt text is cut at the right edge under the box's scrollbar ("…from English ir", "…to you, eve") instead of wrapping. | High | `control/PromptView`: the text is laid out at the box width without the scrollbar width | Wrap to the viewport width (bind wrapping width to the scroll viewport) | sure |
| W-14 | Translating Detailed, review desk | 54, 59 | Each fold opens with a "Copy" button alone at the far right of an otherwise empty 38 pt row; "Model reply" is a bordered box inside a bordered box. | Low | `control/CopyablePane`, `ParsedGrid` each draw a border | Put Copy on the fold's title line; drop the inner border | sure |
| W-15 | Translating finished, Ukrainian | 206 | The live list holds one row and then about ten empty rows with separator lines. | Medium | `.live-rows .list-cell` (`theme.css:2212`) draws the bottom border on empty cells too | Border only on `:filled` cells | sure |
| W-16 | Translating idle | 40, 147, 174 | The chip "nvfp4 · acceptable" is pushed to the far right of the Model row, 700 pt from the model name it describes. | Low | "This run" card row with a right-aligned third column | Place the chip right after the value | sure |
| W-17 | Translating finished | 80 | Three entries to the review desk on one screen with two different numbers: banner "Review 3 segments", toolbar "Review (3)", summary card "1 to review". | Medium | counts differ: to review vs to review + worth a second look | One number, or the same wording for the same count | check by hand |
| W-18 | Review desk | 58, 59, 76, 161, 201 | The English box starts 6 pt higher than the Ukrainian box and is 40–65 pt taller: the "EDITABLE" chip makes the Ukrainian label row taller and the hint under the Ukrainian box takes the rest. In Ukrainian the hint has three lines, so the difference grows. | High | `control/ComparePanes.java:49` (the mark in the target's label row) and the hint only under the target | Same label-row height on both sides; hint under both boxes or below the pair | sure |
| W-19 | Review desk | 58, 76, 161, 201 | "Readable view" is two lines high, shows the cut-off top of a third line (grey stubs of chips) and has its own scrollbar. | Medium | the readable flow's fixed height (`theme.css:2777` block) does not fall on whole lines | Height in whole lines, or grow to the text up to 4 lines | sure |
| W-20 | Review desk | 59, 63 | Three scrollbars stack at the right edge (readable view, tab content, prompt box); with the note panel open a fourth one for the dialog. | Medium | each pane scrolls by itself | Note only; W-19 and W-13 remove two of them | sure |
| W-21 | Review desk | 58, 76 | "EDITABLE" / "РЕДАГУЄТЬСЯ" is the only upper-case chip in the app (all others: "accepted", "translated", "likely"). | Low | message text `REVIEW_EDITABLE` | Lower case | sure |
| W-22 | Export | 42, 67, 82, 86, 189 | The four tiles have four widths, and the widths change with the state: 257/307/257/281 pt before export, 261/287/238/315 after, 239/251/231/380 in Ukrainian. | Medium | `screen/ExportResult.java:97` — a plain `HBox`, each tile as wide as its text | Equal share for the four tiles (`HBox.setHgrow` + equal pref width, or a 4-column grid) | sure |
| W-23 | Export | 42, 82, 189 | "Title · original" (read-only) is 28 pt high, "Title · translated" 32 pt, with the same top — the bottoms and the hints under them do not line up. Same for Author. | Medium | read-only field style has smaller padding than the text field (`ExportTitleCard`) | Same height for both | sure |
| W-24 | Export, after export | 86, 87, 150, 189 | "Proofread: 18 segments re-read, 3 waiting for you" stands loose between the tiles and the cards, and is printed again in "Where to save" in amber, without a mark, 14 pt to the left of the text of the ✓ lines around it. | Medium | `ExportResult` writes the line in two places | Keep one; in the list give it the same indent as the ✓ lines | sure |
| W-25 | Export | 42, 83 | In "Where to save", Format is a label-left/value-right row with a line under it; "Save as" below is a stacked label. Two form styles in one card. | Low | `ExportDestinationCard` | Use the same row style for both | sure |
| W-26 | Export, Projects, Brief | 42, 132, 13 | Leading glyphs are much smaller than the button text: "⬇ Export book", "✦ Suggest a name", "＋ New translation", "✦ Fill in from the book". | Low | text glyphs in the label font instead of the icon font used in the sidebar | Same icon set as the sidebar, or raise the glyph size | likely |
| W-27 | Book Brief | 15, 14, 24 | Each switch sits right after its label, so the four "Also translate" switches are at 488, 404, 441 and 447 pt; "Chapter summaries" likewise. | Medium | `screen/BriefAlsoCard.java:77` — ControlsFX `ToggleSwitch` with its text, no fixed column | Put them in the shared `FormGrid` label column (as "This run" on Translating does) | sure |
| W-28 | Book Brief | 14, 15 | The right column is empty below "Tone & style" for two screens of scrolling. Removing the five language rows shortens the left column and helps; "Also translate" and "Quality and speed" still hang below. | Low | `TwoColumns` with unequal content | Move one short card to the right column (no re-layout beyond that) | sure |
| W-29 | Book Brief | 13, 14 | The legend "closer · balanced · freer" is one left-aligned string; the words are not under the left, middle and right of the slider. | Low | `BriefPoliciesCard` — one label | Three labels spread across the track width | sure |
| W-30 | Book Brief | 15 | "Quality and speed": about 45 pt of empty space above the "Model" line, and its link is "change" (lower case, no dots) while the same kind of link above is "Rename…". | Low | `BriefQualityCard` | Normal row gap; "Change…" | sure |
| W-31 | Import book | 06, 09, 141 | Chapter rows start with about 30 pt of empty indent (the place of a tree arrow that no row has), so names are cut ("Chapter One: What…"). | Medium | chapters shown in a `TreeView` whose disclosure space stays | Zero indent when no node has children | likely |
| W-32 | Import book, "About this book" | 09, 10 | Left column rows have a line under each and are 36 pt apart; right column rows have no lines and are 25 pt apart. | Medium | `screen/AboutBookFold.java` builds the two columns differently | Same row style in both | sure |
| W-33 | All banners | 42, 67, 33, 82, 47 | The info glyph is a small serif "i" placed about 6 pt above the middle of the title line; the keep-awake banner uses a circled "i" centred on its line. A title-only banner sets a whole sentence in bold. Title and body are about 34 pt apart although the code asks for 3. | Medium | `control/Banner.java:24,51` (`TOP_LEFT`, `TEXT_SPACING = 3`); the title label made by `Wrapped.label` seems to reserve a second line; glyph chosen per caller | One info glyph; centre it on the first line; check the title's height | likely (gap cause: check by hand) |
| W-34 | Folds | 41, 08, 54 | The arrow of an opened, focused fold is larger and has a blurred shadow; the neighbour fold's arrow is crisp. | Low | focus effect applied to the arrow of `.titled-pane:focused` | Show focus as the standard ring on the title, no effect on the arrow | likely |
| W-35 | Projects | 132, 140, 180 | Progress bars start at different x in each row (1073, 1061, 1072 pt) because bar and percent hug the status chip, whose width varies. | Low | project row `HBox` in `ProjectsView` | Fixed-width slot for the status chip | sure |
| W-36 | Dialogs | 29, 31, 35 | Add glossary term: the second column starts at 726 pt for the field and 723 pt for the combo box under it, and the labels are bold while form labels elsewhere are regular and muted. Progress dialog: "Follow the current step" touches the pane's left edge and the split has a double line. Results dialog: the chips "added"/"changed" sit about 6 pt above the middle of their row. | Low | dialog-local layouts, not `FormGrid`; `SplitPane` default divider | Reuse `FormGrid` and the form label style; 1 px divider; centre the chips | sure |
| W-37 | Projects (empty) | 03 | The hint shows raw back-quotes: "Pull it with \`ollama pull gemma4:26b-a4b\`." | Low | message text | Remove the back-quotes or set the command in the monospace style | sure |

## Cross-screen inconsistencies

- **Folds:** flat ("About this book", "Prompt context", "Model reply", "Raw reply") vs default grey gradient ("How
  progress is counted") — W-01.
- **Info glyph in banners:** serif "i" (Export, Names) vs circled "i" (keep-awake) vs a circular arrow (Welcome back).
- **Form label style:** regular muted label above the field (Brief, Export) vs bold label (Add glossary term) vs
  label-left rows with a line under each ("This run", Export Format row).
- **Switch placement:** in a value column ("This run" on Translating) vs right after the label (Book Brief).
- **Right edge of content:** banner strip, cards with a page scrollbar, cards inside an inner scroll pane (Import,
  Brief) — three different right margins (W-02).
- **Text links:** "Rename…", "Change…" vs "change"; the title-bar link "open" in lower case.
- **Names:** heading "Book brief" vs sidebar and breadcrumb "Book Brief"; button "Continue to names & style" vs
  "Back to Names & style".
- **Chip case:** "EDITABLE" vs every other chip in lower case.
- **Wording notes only:** Activity log with "Problems only" and no problems says "Nothing has happened yet…" (52);
  Add glossary term subtitle reads "Source → target + type + state"; the review desk heading "low score · low · the
  typography check" reads like raw codes; one Export banner sentence has no full stop, its neighbour has; two hint
  lines under "Text in other languages" say nearly the same.

## Dark theme

- **Book Brief, locked languages (143):** "English" and "Ukrainian" in the disabled combo boxes are barely readable
  (dim grey on the dark field). These are the two facts the card exists to show. Medium, likely — measure with
  `ContrastTest`; the light theme has the same dimming but stays readable (170).
- **Scrollbar thumbs** are light grey in dark (141, 145) and dark grey in light; on Import the bright thumb of the
  inner scroll pane is the brightest thing on the screen. Low.
- **Tooltip** at the bottom of the window is cut by the window edge (145, also 172 in light). Low, check by hand.
- Everything else in scope holds: banners, chips, tiles, read-only fields, the review desk (161, 162) have visible
  edges and the same state colours as in light. No hard-coded light leftovers seen.

## Ukrainian

- Review desk (201): the hint under the Ukrainian box takes three lines, so the two boxes differ in height by about
  65 pt (W-18).
- Names & style (185): "чоловічий · ймовірно" wraps inside the Gender cell while "жіночий · ймовірно" does not (W-08).
- Export (189): tile widths 239/251/231/380 pt — the last tile is half as wide again as the others (W-22).
- Translating finished (206): empty list rows with lines (W-15); row text cut to "Нел…".
- No overflow or clipped button text found on Projects, Import, Brief, Export or the toolbars; the longest toolbar
  ("Назад до імен та стилю · Продовжити переклад · Перегляд (3) · Перейти до експорту") fits at 1440 pt.

## Narrow window (1000 pt)

- Names & style (172, 173): default black arrow beside "⋯" (W-07); wrapped Kind/Gender cells and a cut translation
  field (W-08); rows scroll right under the banner (W-03).
- Banner wider than the cards below it on all four shots (W-02).
- Book Brief (170) and Translating (174) collapse to one column cleanly; "Back to …" shortens to "‹ Back"; the title
  bar drops the book name by priority. No breakage. Narrow Import, Export, Projects and dialogs were not captured.

## Looks right — keep

- The card system: one radius, one hairline border, one inner padding, 15 pt between cards — the same on all six
  screens and in both themes.
- Type scale: screen title, card heading, muted label and hint are the same sizes and weights everywhere.
- Segmented choices (Brief, Simple/Detailed, All/Problems only, Projects filter): same height, same selected fill.
- Status chips with a glyph and a word ("translated", "accepted", "to review", "Stopped", "Exported") — the same
  role colours on Projects, Translating and the review desk, readable in dark.
- The toolbar: breadcrumb left, "Back to …" as a link, one filled primary button at the far right, outlined
  secondary buttons — consistent on every step, and it shortens well at 1000 pt.
- The sidebar step marks (number, tick, filled current step) and the provider footer.
- The Translating summary strip: big time-left line, phase chips, bar, counts — clear hierarchy.
- Ukrainian fits without clipped buttons on every captured screen.
- The review desk footer (Close · More ▾ · ✓ Accept & next) and the dialog footers in general: quiet left, primary
  right, destructive outlined in red.
- Dark theme banners and tiles: every surface keeps a visible edge.
