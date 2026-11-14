# SearchableCombo — research for a specification (2026-11-14)

Read-only research. Nothing in the repository was changed; this folder is git-ignored.

Subject: `modules/ui/src/main/java/ua/bookloom/ui/control/SearchableCombo.java` (JavaFX 26.0.2, ControlsFX 11.2.5).

How the facts below were established:

- **[source]** — read in the JavaFX 26.0.2 sources jars from the Gradle cache (`javafx-controls`, `javafx-base`,
  `javafx-graphics`), with file:line.
- **[run]** — observed in a stand-alone experiment: a copy of `SearchableCombo` + `TextMatch` (Lombok, JSpecify and
  log lines stripped, logic untouched) run against the same JavaFX 26.0.2 jars on the headless glass platform, driven
  with `javafx.scene.robot.Robot` and, for the real-mouse path, with non-synthesized `MouseEvent`s fired at the popup
  cell. Source and condensed logs: `combo-experiment/Exp.java`, `combo-experiment/run-*.txt`.
- **[inference]** — reasoned, not observed.

Not tested: a real macOS window with a physical mouse. Everything about native event delivery is inference.

---

## A. What the control does today, and the two defects

### A.0 The three modes (from the code)

All three share one constructor; the mode only changes `settle()`, which runs when the editor loses focus
(`SearchableCombo.java:87-91`, `193-209`).

| Mode | Factory | On focus loss, when the text differs from the committed value's name |
|---|---|---|
| STRICT | `strict(items, name)` `:96` | Text equal (case/accent-insensitive, stripped) to an entry's name commits that entry; anything else restores the previous value and puts the text in `invalidText` (`settleStrict` `:211-221`, `restore` `:232-244`). Used only by tests today. |
| FREE_TEXT | `freeText(items, name, fromText)` `:101` | Always commits `fromText(text)`, an empty text included (`:206`). Used by `BriefToneCard.genreBox` (`BriefToneCard.java:326`). |
| PARSED | `parsed(items, name[, searchText], parse)` `:108`, `:117` | `parse(text)` present → commits it, even a value the list does not hold; empty → restore + `invalidText` (`settleParsed` `:223-230`). Used by `BriefLanguagesCard.java:256` (source and target) and `TranslateIntoDialog.java:152`. The list is narrowed by `searchText` (shown name + English name + tag), not by the shown name. |

Shared mechanics: the items are a `FilteredList` (`:72`); the editor's text listener calls `narrow()` (`:86`,
`:155-184`), which hides the popup, sets the predicate, puts `value` back to the committed value, rewrites the editor
text and re-opens the popup when there is a non-empty needle and at least one row. A list choice arrives only through
the `valueProperty` listener → `chosenFromList` → `commit` (`:92`, `:186-191`, `:246-261`). The converter's
`fromString` ignores its argument and returns the committed value (`:82-84`).

One fact about the language boxes matters below: `LanguageNames.parse` falls back to `LanguageTags.normalize`, which
maps ISO 639-2 codes, so **"eng" parses to `en`** (`LanguageNames.java:122-123`, `LanguageTags.java:38,57`).

### A.1 Event sequences (JavaFX 26)

**Focus by mouse click in the text.** [source]
1. `TextFieldBehavior.mousePressed` (`TextFieldBehavior.java:224-235`): not focused → `focusGainedByMouseClick = true`,
   `textField.requestFocus()`.
2. The editor is a `FakeFocusTextField`; its `requestFocus()` focuses the parent combo (`FakeFocusTextField.java:32-36`).
   The scene's focus owner becomes the **ComboBox**; the skin gives the editor "fake focus"
   (`ComboBoxPopupControl.java:143-148`).
3. The editor's focus-owner listener (`TextFieldBehavior.java:81-92`) selects all only when the focus owner **is the
   text field** and focus was not gained by mouse; for any other focus owner it runs `selectRange(0, 0)`. In an
   editable combo the focus owner is never the text field, so this listener always collapses the selection to 0.
4. Back in `mousePressed`, `mouseSingleClick(hit)` (`:264`) puts the caret at the click position.
   Result: nothing is selected; the caret is where the click landed.

**Focus by Tab.** [source + run] Same listener, no mouse step: caret 0, nothing selected (`run`: "after tab …
caret=0 anchor=0").

**Typing.** [source + run]
1. With the popup closed the key goes to the combo; the skin's key filter re-fires it at the editor
   (`ComboBoxPopupControl.java:150-177`). With the popup open the key reaches the popup's `ListView` first (it holds
   the popup scene's focus, `ComboBoxPopupControl.java:457`), then the combo, then the editor.
2. `TextInputControl.replaceText` (`TextInputControl.java:1284-1307`) inserts the character **at the caret**
   (`:1296`). The insert fires the text listeners, so `narrow()` runs *inside* `replaceText`.
3. `narrow()` → `FilteredList.setPredicate` → `refilter()` fires **one** change that removes every old row and adds
   every new one (`FilteredList.java:318-337`, the `GenericAddRemoveChange(0, size, removed, this)` at `:335`).
4. `narrow()`'s `positionCaret(typed.length())` (`SearchableCombo.java:175`) is then overwritten when `replaceText`
   continues with `doSelectRange(anchor, caretPosition)` (`TextInputControl.java:1301`): the caret ends right after
   the inserted character, not at the end.

**Enter.** [source + run]
- Popup open: `KEY_PRESSED` reaches the popup list. `ListViewBehavior` maps ENTER to `activate()`
  (`ListViewBehavior.java:105`), which calls `selectionModel.select(focusedIndex)` (`:795`); the list's
  `setOnKeyPressed` hides the popup (`ComboBoxListViewSkin.java:623-630`). The focused index is 0 after a refilter, so
  Enter selects **row 0** → skin listener (`ComboBoxListViewSkin.java:579-588`) → `comboBox.getSelectionModel().select(0)`
  → `ComboBox.java:562` → `setValue` → `SearchableCombo.chosenFromList` → `commit`. Stack trace captured in the run.
- Then `KEY_RELEASED`: `ComboBoxPopupControl.handleKeyEvent` (`:584-597`) →
  `setTextFromTextFieldIntoComboBoxValue` (`:323-350`) → `converter.fromString(text)` = the committed value → no
  value change → `updateDisplayNode()` (`:352-371`) rewrites the editor to the committed value's name (or `""` when
  the value is null).
- So the earlier reading is half right: the `fromString` path exists and returns the old committed value, but it is
  **not** what chooses a row on Enter. The row is chosen by `ListViewBehavior.activate()`. The `fromString` path only
  resets the editor text afterwards — which is what makes Enter with no match wipe the text (below).

**Mouse press / release on a popup row.** [source + run]
- `CellBehaviorBase.mousePressed` (`CellBehaviorBase.java:170-180`): `latePress = isSelected()`; a row that is **not**
  selected is selected on the press (`doSelect` → `simpleSelect` → `sm.clearAndSelect(index)`, `:287`); a row that
  **is** selected waits for the release (`:182-188`).
- `MultipleSelectionModelBase.clearAndSelect` (`MultipleSelectionModelBase.java:334-350`) is a **no-op** when that row
  is already the only selected row and its item is the model's item at that index (`:344-349`).
- Otherwise: list selection index changes → skin listener (`ComboBoxListViewSkin.java:579-588`) → combo selection
  model → `setValue` → `commit` → `clearFilter()` (the list grows back) and `hide()` — all inside the list's
  `clearAndSelect` call.
- On release the skin's filter hides the popup whatever happened (`ComboBoxListViewSkin.java:603-621`).

**Focus loss.** [source] `settle()` per mode (A.0); `ComboBox`'s own listener also calls `commitValue()`
(`ComboBox.java:292-296`, `457-465`), which is a no-op because `fromString` returns the committed value.

**Escape.** [source + run] Popup open: the popup hides (`ComboBoxPopupControl.java:505`,
`ComboBoxListViewSkin.java:623-630`); the typed text **stays**. Nothing is cancelled: the next focus loss settles the
text (a language box that holds "eng" then commits English through the parser — run P5). Popup closed: Escape is
allowed to bubble (`ComboBoxPopupControl.java:160-163`); in `TranslateIntoDialog` that would reach the dialog
[inference].

### A.2 Defect 1 — typed letters are inserted into the shown name

**Cause, verified [source + run].** Nothing ever selects the editor's text when the box gains focus:
`TextFieldBehavior.java:81-92` selects all only when the scene's focus owner is the text field itself, and in an
editable `ComboBox` the focus owner is the combo (`FakeFocusTextField.java:32-36`). A click leaves the caret at the
click position (`TextFieldBehavior.java:264`); Tab leaves it at 0. `SearchableCombo` adds no selection of its own
(its only focus handling is `settle()` on loss, `SearchableCombo.java:87-91`), and its `positionCaret` in `narrow()`
(`:175`) is overwritten by `TextInputControl.java:1301`.

Run: value "Find out from the text", click in the text, type `en` → `Find out fromen the text`; Tab in, type `en` →
`enFind out from the text` (the owner's screenshot; a click at the left edge of the text gives the same). The list
then has 0 rows and no popup, so the box looks dead.

This is stock JavaFX behaviour for every editable `ComboBox`; it is a defect here because the text is the name of a
chosen value, not prose being edited.

### A.3 Defect 2 — a click on the single row is ignored

**Cause, verified [source + run]: a stale selection index, not the `clearFilter()` inside the value listener.**

After a refilter both selection models keep their old **index** while the row under that index changes:

- `ComboBox.ComboBoxSelectionModel.itemsContentObserver` (`ComboBox.java:590-643`): the single refilter change is
  "replaced", so `index = -1`, `shift = 0` (`:614-615`); the last branch (`:631-640`) looks for the previously
  selected item in the new rows and, when it is not there, **leaves `selectedIndex` as it was**.
- `ListView`'s selection model (`ListView.java:1396-1417`): for a replaced change with the index still in range it
  clears and re-selects **the same index** (`:1406-1412`). [The branch is inferred from the observed result: no index
  change event fired and the list's selected item became the new row.]

So with a committed value at index *k* of the full list, after typing, **row *k* of the narrowed list is already
"selected"** in both models. Clicking that row: the cell is selected → the press is deferred
(`CellBehaviorBase.java:174`) → on release `clearAndSelect(k)` is a no-op (`MultipleSelectionModelBase.java:344-349`)
→ no index change → the skin listener never runs → no value → no commit. The release filter hides the popup
(`ComboBoxListViewSkin.java:603-621`). The click is "ignored".

The source-language box is the worst case: its committed value is the Auto entry, which is **index 0**
(`BriefLanguagesCard.java:226-231`), so the **first row of every narrowed list** is unclickable unless Auto itself
matches. One match → the only row is dead.

Run (`run-v.txt`, `run-w.txt`, `run-p.txt`):

| Committed before typing | Typed | Click on "English" |
|---|---|---|
| Auto entry (index 0), STRICT and PARSED | `eng` (1 row, English at row 0) | **ignored** — committed stays Auto, text stays `eng`; state before the click: combo index 0 / item Auto, list index 0 / item English |
| Auto entry | `e` (7 rows, English at row 3) | chosen |
| nothing (null) | `eng`, `e` | chosen |
| Belarusian, Czech, German, Ukrainian | `eng`, `e` | chosen |
| English | `e` | chosen, **plus an uncaught `IndexOutOfBoundsException` on the FX thread** (below) |

Same result whether the text was replaced by select-all + typing or cleared with Backspace first ("the box is empty").

**Corrections to the earlier reading.**
- "`clearFilter()` inside the value listener changes the items during the selection callback, so the selection
  model/ListView push another index back" — **not the cause of the ignored click.** In every case where the commit
  path was entered, it ended on the right value (the list index bounces 0 → 7 → −1 → 7 and settles). It *is* a real
  hazard though: changing the items inside `clearAndSelect` makes JavaFX's own listener fail —
  `IndexOutOfBoundsException` at `ListViewBehavior.java:313` (`c.getAddedSubList()` on a change built before the list
  changed), reached from `MultipleSelectionModelBase.clearAndSelect:394`. Observed for committed English, typed `e`,
  click English. `SettingsModelScreenTest.java:72-88` already guards the same failure for `ModelField`.
- "Enter goes through `converter.fromString`" — see A.1: Enter chooses through `ListViewBehavior.activate()`.

**Where the run disagrees with the report, stated plainly.** In the state that reproduces the ignored click (Auto
committed, `eng` typed) **Enter does not choose either**: `activate()` selects index 0, which is already selected, so
nothing changes; then the key release rewrites the editor to "Find out from the text" and `narrow()` re-opens the
popup (run W1, P2). Enter chooses English only when nothing, or a value at another index, is committed. And with
nothing committed the click worked in every run. So either the owner pressed Enter in a different box/state than the
click, or a real macOS mouse adds something the headless run cannot show. The stale index is a certain defect; whether
it is the *only* cause on macOS is not proven. The fix proposed in C does not depend on the answer: it stops using
selection-change events for mouse choices altogether.

### A.4 Other defects found on the way (all [run])

1. **Down commits instead of moving.** `e` typed (7 rows), Down → the *second* row is committed and the popup closes;
   the list cannot be browsed with the keyboard (run D1). `TranslateIntoDialogTest.java:122-133` passes only because
   "Pol" leaves one row, where Down does nothing and Enter picks row 0.
2. **Enter with several matches commits the first row** (`e` + Enter → the Auto entry, run D3).
3. **Enter with no match wipes the text silently** (`zz` + Enter → editor `""`, no `invalidText`, run D4).
4. **Down on a closed box does nothing** (run D2) — the list cannot be opened from the keyboard.
5. **Escape cancels nothing** (A.1).
6. After a parser commit the combo's selection model can be left at index −1/item null while the value is set
   (run P1) — invisible, but it feeds the next stale state.
7. `ListViewBehavior` also maps SPACE to `activate()` (`ListViewBehavior.java:106`). Whether a space typed while the
   list is open ("chinese s…") reaches the editor or activates a row was **not tested** — cover it with a test.

`ModelField` (Settings › Models) is a second hand-written filtering combo of the same shape; it was not analysed.

---

## B. What an editable "type to filter" box is expected to do

| Topic | macOS (AppKit) | Windows | GNOME / GTK 4 | WAI-ARIA APG |
|---|---|---|---|---|
| Model | `NSComboBox`: text field + list; custom values allowed; optional inline completion (`completes`): the completion is appended and **selected** so typing replaces it | WinUI `ComboBox IsEditable`: custom text allowed, `TextSubmitted` on Enter or focus loss when the text matches no item; Win32 guidance: a combo box is for *unconstrained* input, a constrained list should be a plain drop-down | No editable combo in the HIG. `GtkDropDown` with `enable-search`: a **search entry inside the popup**, the button shows only the chosen value | Combobox pattern; "list autocomplete with manual selection" / "…with automatic selection" |
| Click into a box with a value | Edits the current value (caret); Tab into a text field selects its content [platform convention, not in the cited pages] | Not specified in the cited pages; Tab into an edit selects all [convention] | n/a — the search entry starts empty | Not specified |
| Typing | Text; with `completes`, inline completion | Text; non-editable combo type-ahead brings matches into view | Filters the list in the popup | Types the character; the popup may appear and disappear as the user types; options are filtered |
| Popup opens | On the arrow button | On the button / F4 / Alt+Down | On activation | Implementation's choice: Down, the button, focus, or typing matching characters are all allowed |
| Down / Up | Move through the list | Navigate; **selection does not change** while navigating an open list (default `SelectionChangedTrigger`) | Navigate | Down moves visual focus to the first/next option, Up to the last/previous; Alt+Down opens without moving |
| Enter | Accepts | Commits the highlighted item, or submits custom text | Chooses the highlighted row | Accepts the focused option, closes the popup, caret at the end; with no option focused it only closes the popup |
| Escape | Closes the list | Closes the list | Closes the popover | Closes the popup; optionally, when already closed, clears the box |
| Focus loss, text matches nothing | The text is the value (custom) | `TextSubmitted`; the app validates and may revert the text to the last good value (Microsoft's own sample does this) | n/a | Manual selection: the typed string becomes the value. Automatic selection: the highlighted suggestion becomes the value |
| Mouse click on an option | Chooses it, closes | Chooses it, closes (`SelectionChanged` on click/tap/Enter) | Chooses it, closes | Not described in the keyboard tables; AutoSuggestBox: click = chosen suggestion |

Sources:
- Apple HIG, Combo boxes — https://developer.apple.com/design/human-interface-guidelines/combo-boxes ("People can enter a custom value into the field or click the button to choose from a list of predefined values.")
- AppKit `NSComboBox` — https://developer.apple.com/documentation/appkit/nscombobox ("Clicking in the text portion of the control allows the user to edit the current value.") and `completes` — https://developer.apple.com/documentation/appkit/nscombobox/completes
- WinUI combo box — https://learn.microsoft.com/en-us/windows/apps/design/controls/combo-box (sections "Make a combo box editable", "Text submitted", "SelectionChanged and keyboard navigation")
- WinUI AutoSuggestBox — https://learn.microsoft.com/en-us/windows/apps/design/controls/auto-suggest-box
- Win32 UX guide, drop-down lists and combo boxes — https://learn.microsoft.com/en-us/windows/win32/uxguide/ctrl-drop
- GNOME HIG, Drop-downs — https://developer.gnome.org/hig/patterns/controls/drop-downs.html ; `GtkDropDown` — https://docs.gtk.org/gtk4/class.DropDown.html ("can optionally allow search in the popup")
- WAI-ARIA APG, Combobox pattern — https://www.w3.org/WAI/ARIA/apg/patterns/combobox/ ; example "Editable Combobox With List Autocomplete" — https://www.w3.org/WAI/ARIA/apg/patterns/combobox/examples/combobox-autocomplete-list/

What the sources agree on, and what they leave open:
- Agreed: typing filters or completes; arrows move a highlight **without choosing**; Enter accepts the highlighted
  option; Escape closes the list; a mouse click on an option chooses it and closes the list; the popup need not open
  on focus.
- Open: none of the cited pages says "select all on click". Native text fields put the caret at a click and select
  all on Tab. For a picker whose text is the *name of a chosen value*, selecting all on any focus gain is the common
  answer (browser address bars; GTK avoids the question by starting an empty search entry; `NSComboBox` reaches the
  same effect by selecting the completion). It is a deliberate choice, not a quoted rule.
- Open: what focus loss does with unmatched text depends on whether the list is closed. A strict list reverts (the
  Microsoft sample); a free-text box keeps the text (APG manual selection). The three modes already encode this.

---

## C. Proposed contract and the smallest way to build it

### C.1 Behaviour contract (examples use a list Auto "Find out from the text", Belarusian, Czech, English, Estonian, French, German, Ukrainian)

1. **Focus selects the text.** When the box gains focus by Tab or by a click, and it shows a value, the whole text is
   selected. Example: value "Find out from the text", click, type `en` → the editor holds `en`. A click in a box that
   already has focus places the caret as in any text field.
2. **Typing narrows.** The list holds the entries whose search text contains the typed text, ignoring case and
   accents, in list order. `eng` → [English]; `e` → 7 rows; `BOKMAL` → [Norwegian Bokmål].
3. **The popup follows the matches.** It opens when the text is not empty and at least one entry matches, is sized to
   the rows (at most 10), and closes when nothing matches (`zz` → no popup). Focus alone does not open it.
4. **Opening without typing.** The arrow button, Down, or Alt+Down on a closed box opens the list. When the text is
   empty or is the committed value's name, the whole list is shown with the committed row marked and scrolled into
   view.
5. **Arrows move a highlight and choose nothing.** Down highlights the next row (the first when none is highlighted),
   Up the previous; the editor text, the committed value and the list do not change. `e`, Down, Down → row 2
   (Belarusian) highlighted, committed unchanged, popup open.
6. **Typing again drops the highlight.** After any change of the text no row is highlighted.
7. **A click on a row chooses that row** — whatever was committed before and whatever its index. Auto committed, type
   `eng`, click "English" → committed English, editor "English", popup closed, full list restored, `invalidText` null.
8. **Enter** decides in this order: (a) a highlighted row → that row; (b) the text names an entry exactly (strict),
   is recognised by the parser (parsed), or the mode is free text → that value, as on focus loss; (c) exactly one row
   is listed → that row; (d) otherwise nothing is committed and the text and popup stay. `eng` + Enter → English (c);
   `en` + Enter in a language box → English by its tag (b) although several rows contain "en"; `e` + Enter → nothing
   (d); `Pol`, Down, Enter → Polish (a); genre `solarpunk novella` + Enter → that text (b). Enter never clears the
   text.
9. **Escape cancels the edit.** With the popup open or the text changed: the popup closes, the editor shows the
   committed value's name again (empty when none), the list is whole, nothing is committed, and the key is consumed
   (a dialog does not close). With nothing to cancel the key is not consumed.
10. **Focus loss settles by mode** (unchanged): text equal to the committed name → nothing; STRICT — exact name
    commits, anything else restores the previous value and reports the text in `invalidText`; PARSED — the parser's
    value commits (it may be outside the list: `Latin` → `la`), an unrecognised text restores and reports; FREE_TEXT —
    the text commits as typed, an empty text included. A single partial match is **not** committed on focus loss
    (`eng` in a strict box → restored, `invalidText` = `eng`).
11. **After any commit** the editor shows the value's name, the list is whole, the popup is closed, `invalidText` is
    null, and the value, the committed value and both selection models name the same entry (or none, for a value
    outside the list).
12. **A commit from code** (`select(value)`) behaves like 11 and never opens the popup or moves focus.
13. **No exception reaches the FX thread's uncaught handler** from any of the gestures above.
14. **Committing fires `committedProperty` once per choice**, and never for a highlight, a narrowing or a cancelled
    edit.

Rules 5, 8 and 9 follow WinUI and the APG; 3 and 4 are within what the APG allows; 1 is the deliberate choice named
in B; 10 keeps today's modes.

### C.2 Smallest implementation that reaches it (still `SearchableCombo extends ComboBox<T>`, one class + one CSS rule)

The principle: **stop treating a change of the popup list's selection as "the person chose this"**. Commit from the
gesture and from the **item**, never from an index.

1. *Select on focus.* Listen to the combo's `focusedProperty`; on gain run `Platform.runLater(getEditor()::selectAll)`.
   The deferral is needed because `TextFieldBehavior.java:81-92` and `:264` both run during the same focus change /
   press and would undo an immediate `selectAll()`.
2. *Own the popup's input.* Set a cell factory (public API). The factory's `ListView` argument is the popup list —
   install once on it: a `KEY_PRESSED` filter handling Down / Up / Enter / Escape and consuming them, so
   `ListViewBehavior` (`activate`, select-next) never runs. Each cell gets a `MOUSE_PRESSED` filter that consumes the
   press (so `CellBehaviorBase` never touches the selection model) and a `MOUSE_RELEASED` filter that calls
   `commit(cell.getItem(), LIST)` for a filled cell.
3. *Highlight with the list's focus model, not its selection:* `listView.getFocusModel().focus(i)` + `scrollTo(i)`,
   shown through a `.combo-box-popup .list-cell:focused` rule in `theme.css` (tokens only). Reset to −1 in `narrow()`.
4. *Keys on a closed popup.* A `KEY_PRESSED`/`KEY_RELEASED` filter on the combo, registered in the constructor (so
   before the skin's filter at `ComboBoxPopupControl.java:150`), handles Enter (rule 8), Escape (rule 9) and
   Down/Alt+Down (rule 4) and consumes them, so `setTextFromTextFieldIntoComboBoxValue` never rewrites the editor.
5. *Keep the models honest after a refilter.* In `narrow()`, still under `updating`, select the committed value in
   the popup list when it is among the rows and clear the list's selection otherwise. This removes the stale index
   even for callers that still go through the selection model.
6. *`commit()` stays synchronous.* It is no longer called from inside a selection callback, so `clearFilter()` there
   is safe and the `IndexOutOfBoundsException` path disappears. The `valueProperty` listener stays only for
   `getSelectionModel().select(x)` from code.

A narrower patch — keep selection-driven commits, add step 5, defer `clearFilter()` with `Platform.runLater` behind a
guard, add an Enter handler — would fix the reported click and the exception in fewer lines, but leaves Down
committing, keeps the index-based coupling, and adds an asynchronous step that tests must wait for. Not recommended.

Risks:
- Filter order is assumed (popup list sees keys before the combo; a filter added in the constructor runs before the
  skin's). Both are consistent with what was observed, neither is documented API — pin them with tests.
- The real-mouse path cannot be exercised by the robot on the headless platform (D). The fix removes the
  press-versus-release difference, which is the reason to prefer it, but one hand check on macOS is still needed.
- `Platform.runLater` select-all: a drag that starts with the focusing press will be replaced by select-all.
- Input-method composition (`ComboBoxPopupControl.java:181-192`), the space key with the list open (A.4 item 7),
  `TextLimit` on the genre editor, and the tooltip bound to the editor (`ComboBoxPopupControl.java:308`) are untouched
  but untested here.
- The hide-then-show resize in `narrow()` stays; a highlight must survive it or be reset by rule 6 (it is reset).
- Screen readers: the list's focus item is what `ComboBoxListViewSkin.queryAccessibleAttribute` reports
  (`:682-691`), so a focus-model highlight is announced; not verified with VoiceOver.

### C.3 ControlsFX instead?

ControlsFX 11.2.5 is already a dependency (`gradle/libs.versions.toml:100`, `modules/ui/build.gradle.kts:26`).

- **`SearchableComboBox`** (`impl/org/controlsfx/skin/SearchableComboBoxSkin.java`): a *non-editable* combo with a
  separate search field laid over it while the popup is open. It filters by "contains all words", rebuilds the items
  list on every keystroke (its own comment cites JDK-8174176), Enter/Tab pick the first row, Escape restores. It has
  no notion of free text or a parser, shows a bundled bitmap filter icon and ControlsFX's own "search" prompt, and adds
  the style classes `combo-box-search` / `combo-box-filtered`. FREE_TEXT and PARSED cannot be expressed; the box would
  stop showing typed text in place. **Does not fit.**
- **`TextFields.bindAutoCompletion` / `AutoCompletionBinding`**: suggestions under a plain `TextField`; default match
  is case-insensitive "contains" (`SuggestionProvider.java:235-239`); a row is taken on mouse click or Enter
  (`AutoCompletePopupSkin.java:98`). It has no arrow button and no "show the whole list", no accent folding, and it
  **adds its own stylesheet** to the suggestion list (`AutoCompletePopupSkin.java:77-78`), which the token-only theme
  would have to override. The three modes, `committed`/`invalidText`, the search-text-versus-name split and select-on-
  focus would all still be written by hand on top of it, and every existing `SearchableCombo` test and the theme rules
  for `.combo-box` would be redone. How it schedules suggestion lookups was not read.

Honest verdict: neither is simpler. `SearchableComboBox` is a different control; the auto-completion binding trades
the `ComboBox` quirks for a missing arrow, foreign styling and a rewrite of the tests, and still needs the same custom
logic. Fixing the existing class is the smaller and safer change.

---

## D. Tests

### D.1 What the headless platform can do (checked)

- Popups and mouse/key robots work on `glass.platform=Headless`
  (`modules/build-logic/src/main/kotlin/bookloom.test-conventions.gradle.kts:110`):
  `TermTableCardTest.java:116-141` clicks a `MenuButton`, finds the `ContextMenu` window and does
  `clickOn("Export...")` on an item **inside the popup**; `ProjectsMoreMenuFitTest.java:23-28` shows and inspects a
  popup. No existing test clicks a `ComboBox` popup row.
- Real typing works: `TranslateIntoDialogTest.java:122-133` does `clickOn(languages()); write("Pol");
  type(KeyCode.DOWN); type(KeyCode.ENTER)`. `SettingsScreenTestBase.java:143` uses `type(KeyCode.ENTER)`.
- `SettingsModelScreenTest.java:72-88` shows the pattern for "no failure on the FX thread": set an uncaught-exception
  handler on the FX thread, act, assert the list of failures is empty.
- **Caveat [source + run]:** the headless robot marks its mouse press and release as *synthesized*
  (`com/sun/glass/ui/headless/HeadlessRobot.java:152,170`). `CellBehaviorBase.java:171` then defers every row
  selection to the release, whereas a real mouse selects an unselected row on the press. A `clickOn(cell)` test
  therefore reproduces the ignored click (it is a release-path no-op either way) but does **not** exercise the
  real-mouse press path. To cover that path, fire a non-synthesized pair at the cell with
  `Event.fireEvent(cell, new MouseEvent(MOUSE_PRESSED, …, synthesized = false, …))` and the matching
  `MOUSE_RELEASED` — done in `combo-experiment/Exp.java` (`realClick`). Parameterise the row-click tests over both
  ways (`@ParameterizedTest`, no branch in the test body).
- The current helper `typeInto` (`SearchableComboTest.java:75-78`) sets the text with `setText`, so it can never see
  defect 1; and `choosingFromNarrowedList_commitsItAndEmptiesFilter` (`:183-192`) selects through the selection
  model, so it never sees defect 2. Both stay valid as they are; the new tests must type and click for real.
- Finding the row: `lookup(".combo-box-popup .list-cell")` filtered by text (TestFX looks through every window, as
  the `clickOn("Export...")` precedent shows), or walk `Window.getWindows()` for the showing `PopupWindow`.

### D.2 Tests to add to `SearchableComboTest` (red today unless marked)

| # | Name (`method_state_expected`) | Steps | Expect | Today [run] |
|---|---|---|---|---|
| 1 | `typing_boxShowingAValueClickedInto_replacesTheShownText` | select Auto; `clickOn(editor)`; `write("en")` | editor `en` | `Find out fromen the text` |
| 2 | `typing_boxShowingAValueTabbedInto_replacesTheShownText` | focus the button; `type(TAB)`; `write("en")` | editor `en` | `enFind out from the text` |
| 3 | `rowClick_committedEntryAtTheSameRow_choosesTheClickedRow` (robot + real press) | select Auto; click; `write("eng")`; click "English" | committed English, editor "English", popup closed, all items | ignored |
| 4 | `rowClick_severalRows_choosesTheClickedRow` (robot + real press) | empty; `write("e")`; click "English" | committed English | passes |
| 5 | `rowClick_anyCommittedEntry_raisesNothingOnTheFxThread` | select English; `write("e")`; real-press click "English"; uncaught handler | no failure | `IndexOutOfBoundsException` |
| 6 | `enter_singleMatch_choosesIt` | select Auto; `write("eng")`; `type(ENTER)` | committed English | text reverts to Auto, popup re-opens |
| 7 | `enter_severalMatchesNoneHighlighted_choosesNothing` | empty; `write("e")`; `type(ENTER)` | committed null, editor `e` | commits the first row |
| 8 | `enter_noMatch_keepsTheTypedText` | empty; `write("zz")`; `type(ENTER)` | editor `zz` | editor emptied |
| 9 | `down_severalMatches_highlightsWithoutChoosing` | empty; `write("e")`; `type(DOWN)` twice | committed null, popup open, row 2 highlighted | commits row 2 at the first Down |
| 10 | `downThenEnter_highlightedRow_choosesIt` | empty; `write("e")`; `type(DOWN, DOWN, ENTER)` | committed Belarusian | — |
| 11 | `down_closedBox_opensTheWholeList` | select Ukrainian; focus; `type(DOWN)` | popup open, all items, Ukrainian marked | nothing happens |
| 12 | `escape_typedText_restoresTheCommittedName` | select Ukrainian; `write("eng")`; `type(ESCAPE)`; move focus away | editor "Ukrainian", committed Ukrainian, `invalidText` null | text stays `eng`; a parsed box then commits English |
| 13 | `parsed_exactTagAmongSeveralMatches_enterChoosesTheParsedValue` | parsed list; `write("en")`; `type(ENTER)` | committed `en` | commits the first row containing "en" |
| 14 | `freeText_enter_commitsTheTypedText` | free text; `write("solarpunk")`; `type(ENTER)` | committed "solarpunk" | — |
| 15 | `typing_spaceWhileTheListIsOpen_goesIntoTheText` | empty; `write("chinese s")` | editor `chinese s`, 1 row, nothing committed | not tested |
| 16 | `commit_anyGesture_firesCommittedOnce` | recording listener; tests 3, 6, 10 | one event each | — |

And at screen level, one each, so the wiring is proven where the owner saw it:
- `BookBrief…`: source box showing Auto, click, `write("eng")`, click "English" → the view model's source language
  is `en` (the exact reported path; red today).
- `TranslateIntoDialogTest`: keep `languages_typedPolThenDownAndReturn_choosesPolish`; add `write("Pol")` + click the
  row.

Before pushing: `scripts/ci-linux-ui.sh` (popup sizes depend on the platform font) and one hand check on macOS of
tests 1, 3 and 9 with a real mouse.
