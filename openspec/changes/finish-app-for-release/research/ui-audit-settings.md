# Settings — polish audit (2026-11-14)

Scope: Settings, all six tabs, the provider test dialog, the leave guard and the Settings confirmations.
Shots viewed: 89–118, 117b–d, 120, 151–160, 175, 176, 191–200 (all in scope). Pixel positions below are in the
shot as shown at 2000 px width (×1.44 = file pixels, ÷1.39 = points). Paths are under `modules/ui/src/main/`.
No repository file was changed.

## Summary

1. Automation: neither segmented control shows which option is chosen (light, dark, Ukrainian) — it reads as "nothing set".
2. Generation: the quality table gives half its width to the row names and squeezes the three answer columns into 5–9-line stacks.
3. Test dialog: the figures column is too narrow ("uncache / d · 0 %", "25.4 / tok/s") while 170 px of the dialog stay empty.
4. Every tab has a different card width (640 / 760 / 860 / full), so the right edge jumps on each tab change.
5. Switches sit right after their text, so a column of switches is ragged on Providers, Models and Automation.
6. Generation "Time limits": a long key pushes its value sideways, and keys and values sit on different baselines.
7. Providers form: labels hang above the centre of their fields; the model picker and its filter have no label and ignore the field column.
8. The provider summary card is success-green even for "no model chosen yet" and before any test.
9. Confirmation dialogs disagree: main button present or not, its position, and a large empty band under the title.
10. Overall: the screens are calm, consistent in palette and hold up well in dark, Ukrainian and at 1000 pt. The defects are almost all alignment and width rules that differ from tab to tab, plus one real state bug (item 1).

Counts: High 3 · Medium 11 · Low 11 (25 items).

## Defects

| ID | Tab / dialog | Shot(s) | What is wrong | Severity | Likely cause | Suggested fix | Confidence |
|---|---|---|---|---|---|---|---|
| S-01 | Automation | 118, 159, 199 | "When to stop and ask you" and "Default quality for new books" show no selected segment, although the title bar says "Balanced · Unattended". The Theme control on Appearance, same widget, shows its selection. | High | `java/ua/bookloom/ui/control/EnumSegments.java:79` selects with `group.selectToggle(toggle)`; ControlsFX puts the buttons into the group only when the skin is built, so the selection made in `initialize()` is lost. `AppearanceController.java:108,143` uses `toggle.setSelected(true)` and works. Defaults exist (`state/AutomationSettings.java:47-48`). | In `EnumSegments.show` call `toggle.setSelected(true)` instead of `group.selectToggle(toggle)`. | likely (cause read from code, not run) |
| S-02 | Generation | 114, 157, 197 | Quality table: the row-name column takes about 45 % of the width (325 px for "Fixing calls"), the three answer columns get about 135 px each and wrap to 5–6 lines in English, 9 in Ukrainian. Rows have no separators, so the tall cells are hard to follow across. | High | `java/ua/bookloom/ui/screen/GenerationQualityTable.java:28` `WIDTHS = {5, 2, 2, 2}`. | Turn the shares round, e.g. `{2, 3, 3, 3}`; add the `-color-divider` row line the neighbouring card uses. | sure |
| S-03 | Test dialog | 94 | Figures column: "119 ms · uncache / d · 0 %" breaks inside a word, "25.4 / tok/s" breaks between number and unit. The values start at x=1088 in a column that ends at 1160. | High | `java/ua/bookloom/ui/dialog/ProviderTestView.java:56` `METRICS_WIDTH = 230`, fixed min/pref/max (lines 205–207). | Widen to about 300, or let the value label keep its preferred width (`setMinWidth(USE_PREF_SIZE)`) and shorten the key column. | sure |
| S-04 | Test dialog | 94, 95, 96 | Body and Response boxes are a fixed 360 pt high; about 170 px below them is empty in a dialog that fills the window, and the Body already scrolls both ways. | Medium | `ProviderTestView.java:55,138-139` `RAW_HEIGHT = 360` as pref and min height. | Let the three panes grow (`VBox.vgrow ALWAYS`), keep 360 as the minimum. | sure |
| S-05 | All tabs | 89, 109, 114, 117, 118, 120 | Card right edge differs on every tab: Providers 1910, Models 1558, Appearance 1252, Automation 1419, Generation and Storage 1963 (same as the banner). Switching tabs makes the content jump between four widths. | Medium | `screen/appearance.fxml:18` maxWidth 640; `automation.fxml:15` 760; `models.fxml:17` 860; `settings.fxml:35` 860 (+ list); Generation/Storage use `TwoColumns` with no limit. | One width for the single-card tabs (860, as Models), and the same outer limit for the two-card tabs. | sure |
| S-06 | Providers, Models, Automation | 91, 111, 118, 199 | Switches are placed right after their text, so they do not line up: Providers 982 vs 977; Models 566 vs 559 (and the number fields after them 636 vs 629); Automation 990 / 762 / 789. | Medium | ControlsFX `ToggleSwitch` with its own text (`automation.fxml:49-58`, `ProviderExtrasSection.java:41,45`, `ModelLimitsSection.java:36,42,189`). | Give the switch text one minimum width per group (the widest), or put the switch first and the text after it. | sure |
| S-07 | Generation › Time limits | 114, 157, 197 | The value column is ragged: row 1 starts at 1449, rows 2–4 at 1446 (Ukrainian: 1473 / 1438 / 1449 / 1438). Keys are top-aligned at 12.5 px, values 13 px lower in rows 3–4, level in row 1. | Medium | `theme.css:1884` `.kv-key { -fx-min-width: 170px }` — a longer key pushes its value; `GenerationController.java:124-133` builds a plain `HBox` (top-left). | Fixed key width for this card (min = pref = max about 240) and `Pos.BASELINE_LEFT` on the row. | sure |
| S-08 | Providers › Connection details | 90, 97, 101, 192 | Labels hang high: "Name" text centre at y=736, its field centre at 748; same for Kind, Sign-in, Address, both timeouts. | Medium | `screen/FormGrid.java` lays the label at the top of the row (no vertical centring or baseline in the file). | Centre the label on the first control of the row (or baseline-align). | likely |
| S-09 | Providers › Connection details | 91, 92 | "Filter models by name" and the model picker have no label and start at the label column (x=773), not the field column (x=1065); the filter is full width while every field above is 500 px. They read as a different form. | Medium | Built outside `FormGrid` (`ModelField` / `ModelFilterField` rows in the provider form). | Put the picker in the grid as a "Model" row with the filter above it in the same field column. This is also where the model facts will move, so fix it with that work. | sure |
| S-10 | Providers | 100, 117d, 89 | The summary card is success-green (`-color-ok-bg`) for LM Studio, idle, "no model chosen yet", and for a provider that has not been tested. Green means "works" everywhere else (chips, test banner). | Medium | `theme.css:2965` `.summary-card` is always ok-coloured; only `.summary-card-bad` exists (`ProviderSummaryCard.java:179-181`). | Neutral card (`-color-surface-2`/divider) until a test passed; green only for "ready ✓". | sure |
| S-11 | Providers | 90, 97, 100, 152 | When the fold opens the scroll bar appears and the summary card and fold shrink to x=1873, while the "Delete…" button above and Cancel/Save below stay at 1890 — the right edges no longer line up. | Low | Scroll pane inside the form card takes its bar width from the content. | Reserve the bar's width always (padding on the content when no bar), or overlay the bar. | sure |
| S-12 | Providers › Checks | 92, 93, 193 | "Details" of the two-button rows sits between the buttons (y=934, 1031) while the result text sits beside the first button (y=908, 1006); in the one-button rows both are on one line. Results change from plain text ("Not tested yet") to a chip with a different left edge (1005 → 991). | Low | `ProviderChecks.java` grid: the link is centred on the row, the status top-aligned. | Top-align "Details" with the first button; give the idle text the chip's left inset. | sure |
| S-13 | Providers › Checks | 92, 153 | Four filled main buttons stacked (Test connection / models / inference / advanced features) plus the filled "Test" in the summary card: five main actions on one screen, none stands out. | Low | `ProviderChecks.java:232` area, all `btn-primary`. | Keep "Test" in the summary card as the main one; make the stage buttons the outlined kind. | check by hand (taste, owner's call) |
| S-14 | Providers form | 102 | A field in error gets a pale red border that is weaker than the normal border; the red message below carries the whole signal. | Low | error border uses `-color-err-bd` (`theme.css:109`, #e4b6ad). | Use `-color-err` for the field edge. | likely |
| S-15 | Providers | 107, 108 | "+ Add header" is indented 13 px against the switch labels above and the hint below (786 vs 773); the same 13 px shift is on "Open data folder" / "Open log folder" in Storage (398 vs 385) and "+ New provider" in the list. | Low | Ghost button padding (`btn-ghost`). | Negative left margin of the padding for a ghost button that starts a row, or use the outlined button there. | sure |
| S-16 | Models | 109–112, 195 | Text of the same role is styled three ways: "This model can turn thinking on and off." and the Sampling paragraph are dark 14 px, the Temperature and limits explanations are muted 13 px. Gaps are uneven: fact lines 47 / 47 / 61 px apart, "Translate: schema / Name reading: schema" set tight, radio rows 53 px apart, heading-to-text 31–43 px. | Medium | `ModelThinkingSection`, `ModelSamplingSection` use a body label where the others use `hint`; section spacing set per section. | One class (`hint`) for every explanation, one spacing for the section list. | sure |
| S-17 | Banners (Models info, test result, Welcome back) | 109, 94–96, 176 | The banner icon sits about 7–11 px above the first text line (i at 777 vs text 784; ✓ at 269 vs 276; ↺ at 204 vs 215). | Low | `control/Banner.java:51` `Pos.TOP_LEFT`, icon 15 px (`theme.css:1924-1931`) beside a 14 px bold title with padding. | Baseline-align the icon with the title, or give the icon the title's top inset. | sure |
| S-18 | Leave guard, Delete provider, Delete all data, Use this one | 99, 104, 117b, 117d | (a) Leave guard and both delete questions have no main button — three equal outlined buttons; "Use LM Studio" has a filled one. (b) The filled one is the left button there, while the confirming button is rightmost in the others. (c) 70–82 px of empty space between title and text in a 250-px dialog. (d) Only 117d has an icon. | Medium | Dialog builders pick button kinds per dialog; header panel height (`theme.css:757` `.dialog-card > .header-panel`). | Leave guard: "Save" filled, rightmost. One order everywhere (confirm on the right). Halve the header's bottom padding. | sure |
| S-19 | Test dialog | 94, 95 | Stage switch at the bottom left looks like a row of loose words; the chosen one is a white outlined box in 94 and a beige one in 95 (one of them is hover or focus). "Advanced features" is grey with no reason shown. | Low | Stage buttons styled as ghost toggles. | Use the same segmented control as Sign-in / Theme. | check by hand |
| S-20 | Storage & logs | 120, 160, 200 | The two cards end at different heights (764 vs 776; Ukrainian 973 vs 1007). The folder path breaks inside words at the column edge ("Development-Git / Hub-book-loom", "…2f3b1bb0dce / d/scratchpad"). | Low | `TwoColumns` does not equalise heights; monospace label wraps by character. | Fill both cards to the row height; break the path only after "/" (insert zero-width spaces) or shorten the middle. | sure |
| S-21 | Storage, Providers, Appearance | 120, 89, 91, 92, 176 | Three looks for a secondary action on one screen family: bare text ("Open data folder", "Make a backup…", "Change model", "Use this one", "Refresh", "Detect again"), underlined link ("Details", "Restart now") and outlined ("Delete…", "Test with details"). The bare-text ones do not look clickable next to plain hint text. | Medium | `btn-ghost` vs `btn-link` vs outlined, chosen per place. | Ghost → outlined for row actions (Open folder, Make a backup, Change model, Use this one); keep the link for in-line "Details". | sure |
| S-22 | Tabs | 109, 114, 117, 118, 120 | After a click the chosen tab carries the keyboard focus ring (a dark rounded box) on top of its underline, and keeps it. | Low | Focus ring drawn on `:focused`, not only on keyboard focus (`:focus-visible`). The clicks were synthetic. | Draw the ring on `:focus-visible` only for tabs. | check by hand |
| S-23 | Settings header | 191–200 | The Ukrainian subtitle wraps to two lines at about x=1260 with 700 px free to its right; it pushes every tab 21 px down compared with English. | Low | `settings.fxml:16` `styleClass="muted, prose"`; `theme.css:625` `.prose { -fx-max-width: 680px }`. | Drop `prose` from this one line (or shorten the Ukrainian text). | sure |
| S-24 | Providers, Models with the Welcome banner | 152, 153, 192, 194 | With the banner on top the pinned-footer card is squeezed: only three form rows (152, 192) or four fact lines (154) fit, in a scroll area inside the page. | Medium | Form card height is bound to the viewport; the banner takes about 190 px. | Give the form card a minimum height (about 420 pt) and let the page scroll below it. | sure |
| S-25 | Generation | 197 | When the page scrolls, the scroll bar (1948–1963) touches the right card's edge (1947) with no gap, and both cards narrow by the bar's width. | Low | Page scroll pane without a reserved gutter. | 8 px gap between content and bar. | sure |

## Cross-tab inconsistencies

- **Card width**: four different widths (S-05).
- **Form layout**: Providers uses label-left / field-right (label column about 210 pt); Models and the Providers picker put the label inline before the control ("Model [combo]"); Appearance and Automation stack a muted label above the control; switches carry their text themselves. Three patterns for the same job.
- **Label colour for the same level**: field labels are muted on Providers, Appearance, Automation; switch labels are dark; section headings on Models are bold with a muted "— explanation" tail, on Providers "Checks" is bold alone.
- **Field widths**: Name/Kind 500 px, Address full, timeouts 165 px, Models number fields 165 px, language 500 px — reasonable, but the filter fields are full width on both tabs.
- **Save model**: Providers and Models have Cancel / Save pinned; Appearance and Automation say "✓ Changes apply at once."; Generation and Storage say nothing. Fine as behaviour, but the "apply at once" line is styled as a hint with a ✓ glyph, not as the banner used elsewhere.
- **Read-only rows**: Storage and Time limits use divider lines between rows; the quality table beside Time limits uses a bordered box and no lines.
- **Secondary actions**: three looks (S-21).
- **Wording, note only**: "Refresh" (Providers, Models) vs "Detect again"; "Test" vs "Test connection / models / inference"; "Delete…" opens a question whose button is "Delete", but "Delete all data…" → "Delete all data"; title-bar chip "Model idle" (89) vs "AI model ready" (151) for the same idle state; "Kind" on the form vs "Treat this server as…".
- **Makes the owner's decisions harder**: (a) the model picker in the provider form is the least tidy part of that form (S-09), and it is where the model facts are to move; (b) Models facts use loose, unevenly spaced lines (S-16) — move them as one block with one style; (c) the open model list in the provider form showed only the chosen model although "7 models available" (106) — check by hand before it becomes the only chooser.

## Dark theme

- "Delete…" (and "Delete all data…") border is almost invisible: `-color-err-bd` #5e3f39 on the card (`theme.css:185`, `.btn-danger-outline` 1352) — 151, 153, 160. Medium, sure. Use `-color-err` for the edge in dark.
- Unselected radio buttons are a dark ring on the dark card and nearly disappear (155: "Allow on proofreading and names", "Always"). Medium, likely — check the radio edge against the 3:1 rule the inputs follow.
- Off switches: white thumb on a light grey track, the brightest thing on the screen while meaning "off" (153, 156, 158, 160). Low, check by hand.
- The Models info banner loses its blue tint and reads as a plain darker box (154). Low.
- No light-theme leftovers found; chips, summary card, segmented controls and scroll bars are correct.

## Ukrainian

- Quality table: first answer column runs to 9 lines (197) — same cause as S-02, worse.
- Time limits value column is ragged by 35 px (197) — S-07.
- Subtitle wraps early (S-23).
- Models facts: the line "Трохи знижена точність…" runs to the scroll bar and touches it before wrapping (194, x≈1520). Low; goes away with the facts move, keep a right inset there.
- Wording, note only: "структуровані відповіді: ще не перевірено…" starts lower-case (194); "для мови «українська»", "для англійська → українська" are not declined (194); "Ваша модель ШІ" (card) vs "AI-модель готова" (title bar) mix ШІ and AI (191).
- Nothing is clipped or cut with "…"; the Checks buttons, tabs, list chips ("неактивний") and the footer buttons all fit.

## Narrow window

- 175 (Providers, 1000 pt): holds. List and form keep their proportions, nothing clips, the banner and the form card share the right edge (which they do not at 1440 — S-05).
- Only the closed-fold state was captured. With the fold open at this width the label column (about 210 pt) plus the 500-px fields and the Welcome banner leave very little; check by hand, together with S-24.
- Generation's quality table at this width was not captured; with `{5,2,2,2}` the answer columns would be about 60 pt wide — fix S-02 first, then look.

## Looks right — keep

- The provider list: name, address, state chip and the selected-row tint are clear and identical in light, dark and Ukrainian.
- The pinned Cancel / Save footer with the "● Unsaved changes" mark (98) — unmistakable, and disabled states are honest.
- Segmented controls (Sign-in, Theme, Temperature): one look, good selected contrast in both themes.
- The test dialog's structure: result banner, request line with "Copy as curl", Body / figures / Response side by side.
- Result chips in Checks (93) and the "ready ✓ · Answers in about 332 ms" line in the summary card (97).
- Hints under controls: consistent muted size and tight 5 px attachment to their control on Appearance and Automation.
- Storage's key–value rows with divider lines; the monospace path with "▸ file" is easy to read.
- Dark theme overall: token use is clean, no stray light surfaces, text contrast is comfortable.
- Ukrainian fits everywhere without truncation; buttons grow instead of clipping.
- Field sizes follow content (short number fields, long address field).
