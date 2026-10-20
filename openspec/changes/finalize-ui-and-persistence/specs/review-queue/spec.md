## ADDED Requirements

### Requirement: Open the review desk as a large dialog and by itself when a translation finishes

The application SHALL open the review desk as a large in-window dialog from the toolbar's "Review (n)" and from "n to review" on the Translating screen, and SHALL have no review panel on the Translating screen and no review step in the navigation.

WHEN a translation finishes with at least one segment to review, the desk SHALL open by itself once no other dialog and no visible work is shown, unless the setting that opens the desk is off; it SHALL NOT open when nothing needs review.

**Source**: docs/specification/mockups/README.md (Dialog · Review; G1), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T6–T12), ADR-0045. In plain words: reviewing is a task of its own that needs the whole window, so it is a dialog; it appears by itself at the end of a run so the person does not have to find it, but it never covers something they are already doing.

#### Scenario: Review (n) opens the desk

- **WHEN** 6 segments need review and the person presses "Review (6)" in the toolbar
- **THEN** the review desk opens as a large dialog over the window, and the Translating screen holds no list of segments to review

#### Scenario: The desk opens by itself at the end of a run

- **WHEN** a translation finishes with 6 segments to review, no dialog is open and the "open the review desk" setting is on
- **THEN** the review desk opens by itself with the first segment to review selected

#### Scenario: The desk waits for a free moment

- **WHEN** the translation finishes with 6 segments to review while the dialog "Export complete" is open
- **THEN** the desk opens only after that dialog is closed

#### Scenario: The setting turns the automatic opening off

- **WHEN** the translation finishes with 6 segments to review and the "open the review desk" setting is off
- **THEN** the desk does not open by itself and "Review (6)" stays in the toolbar

#### Scenario: Nothing to review keeps the desk closed

- **WHEN** the translation finishes with 0 segments to review
- **THEN** the desk does not open by itself

### Requirement: Filter the desk by what needs attention

The review desk SHALL offer the filters "To review (n)", "Worth a second look (n)" and "All segments", and ‹ › controls that move to the previous and next segment of the filter shown. "Worth a second look" SHALL list the accepted segments the final audit doubts.

**Source**: docs/specification/mockups/README.md (Dialog · Review, Left pane; Words), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T6–T12). In plain words: the person works through what needs them first, can look at what the last check doubted, and can still read everything else.

#### Scenario: Filters show their counts

- **WHEN** 4 segments are FLAGGED and 3 accepted segments are doubted by the final audit
- **THEN** the filters read "To review (4)", "Worth a second look (3)" and "All segments"

#### Scenario: A filter narrows the list

- **WHEN** the person selects "Worth a second look"
- **THEN** only the 3 doubted segments are listed and the ‹ › controls move among them

### Requirement: Mark every desk row with its state

Each row of the desk's list SHALL show the segment's location, a state marker — to review, accepted, your edit or later — and a one-line "why · text", with formatting marks left out of the line and the full text in the tooltip.

The state "later" SHALL be the desk's own mark for the session: "Leave for later" SHALL write nothing, the segment SHALL stay FLAGGED, and it SHALL still count as to review.

**Source**: docs/specification/mockups/README.md (Dialog · Review, Left pane; G11), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T6–T12). In plain words: each row says at a glance what has been done with it, and putting a segment aside is a bookmark, not a decision.

#### Scenario: Markers follow the person's actions

- **WHEN** the person accepts `ch5 · p12`, saves an edit of `ch7 · p40` and presses "Leave for later" on `ch9 · p03`
- **THEN** their rows show the markers accepted, your edit and later

#### Scenario: Leave for later changes nothing stored

- **WHEN** the person presses "Leave for later" on FLAGGED `ch9 · p03` and the desk is closed
- **THEN** `ch9 · p03` is still FLAGGED, it still counts in "Review (n)", and no record of it was written

### Requirement: Keep formatting marks whole in the translation editor

The desk's translation editor SHALL treat each formatting mark such as `⟦0⟧` as one protected unit: deleting any part of it SHALL remove the whole mark, a cut mark MAY be pasted elsewhere, and a paste SHALL keep only the marks the editor is missing.

**Source**: docs/specification/mockups/README.md (Dialog · Review, Editors; decision 12), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1, T7). In plain words: a mark that is half deleted breaks the book's bold, links or italics, so the editor never allows half a mark.

#### Scenario: Deleting part of a mark removes all of it

- **WHEN** the editor holds `Він відчинив ⟦0⟧старі⟦1⟧ двері.` and the person presses Backspace right after the `0`'s closing bracket
- **THEN** the whole mark `⟦0⟧` is removed and the text reads `Він відчинив старі⟦1⟧ двері.`

#### Scenario: A paste keeps only the missing marks

- **WHEN** the editor lacks `⟦0⟧` and holds `⟦1⟧`, and the person pastes `⟦0⟧ ⟦1⟧ старі`
- **THEN** only `⟦0⟧` and the words are inserted, and `⟦1⟧` is not doubled

### Requirement: Offer the less common actions under More

The desk's footer SHALL offer a "More ▾" menu with Leave for later, Try again, Try again with a note… and Undo my edit, each with a line saying what it does. Try again with a note… SHALL open an inline panel inside the desk, not a second dialog, and Undo my edit SHALL be disabled while the segment is not edited.

**Source**: docs/specification/mockups/README.md (Dialog · Review, Footer), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T6–T12, F1). In plain words: the common path is one button, and the rarer actions are one click away without crowding the footer or stacking a second window on the desk.

#### Scenario: Try again with a note stays inside the desk

- **WHEN** the person chooses "Try again with a note…" and types `keep it more formal`
- **THEN** an inline panel appears inside the desk, and no second dialog is opened

#### Scenario: Undo my edit is disabled on an untouched segment

- **WHEN** a segment has no edit
- **THEN** "Undo my edit" in the More menu is disabled

### Requirement: Move through the desk with keyboard shortcuts

The desk SHALL run its main button on Ctrl+Enter (⌘+Enter on macOS) and SHALL move to the next or previous segment on Ctrl+Alt+↓ and Ctrl+Alt+↑, without using Alt+↑ or Alt+↓, which belong to the editor on macOS.

**Source**: docs/specification/mockups/README.md (Dialog · Review, Footer), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T6–T12). In plain words: a person who reviews dozens of segments can stay on the keyboard, and the shortcuts do not collide with text editing.

#### Scenario: Ctrl+Enter accepts and moves on

- **WHEN** `ch5 · p12` is selected and unedited and the person presses Ctrl+Enter
- **THEN** it becomes ACCEPTED and `ch7 · p40` is selected

#### Scenario: Ctrl+Alt+Up goes back

- **WHEN** `ch7 · p40` is selected and the person presses Ctrl+Alt+↑
- **THEN** `ch5 · p12` is selected

### Requirement: Show on "What the model saw" the context the model was actually sent

The desk's "What the model saw" tab SHALL show the segment's stored context snapshot, parsed into its sections; for a segment that was drafted in a batch, it SHALL show the batch context that was actually sent to the model.

**Source**: docs/specification/mockups/README.md (Dialog · Review, Tabs), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T8). In plain words: to judge a translation the person must see what the model was told, and for a batch that is the batch's package, not a reconstruction.

#### Scenario: A batch-drafted segment shows the batch's context

- **WHEN** `ch2 · p06` was drafted in a batch of 4 segments with the glossary entry `Harbor` → `Гавань` and the previous 2 pairs
- **THEN** the tab lists the glossary entry and the 2 pairs exactly as sent in that batch

#### Scenario: A segment drafted alone shows its own context

- **WHEN** `ch3 · p01` was drafted alone
- **THEN** the tab shows the context stored with that draft

### Requirement: Show stored formatting marks in the editor by their number

The review editor SHALL show each stored formatting mark `⟦gN⟧` as `⟦N⟧` with the same number, and saving SHALL map each `⟦N⟧` back to `⟦gN⟧` before the placeholder check; detail panes SHALL show the marks as chips.

**Source**: docs/specification/mockups/README.md (Dialog · Review, Editors; G5), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1). In plain words: the stored text keeps its internal mark names, while the person sees short numbered marks that are easy to move and recognise.

#### Scenario: The editor shows numbers and saving maps them back

- **WHEN** the stored target is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and the person opens it and saves it unchanged
- **THEN** the editor showed `Він відчинив ⟦0⟧старі⟦1⟧ двері.`, and the saved text is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.`

#### Scenario: Detail panes show chips

- **WHEN** the same segment is shown in the readable view under the editors
- **THEN** the marks appear as chips `0` and `1`, not as raw `⟦` text

## MODIFIED Requirements

### Requirement: Show an empty state when nothing is flagged, and browse every segment after an Unattended run

IF the review desk opens with no segment to review, THEN it SHALL show "Nothing needs your review" and keep "All segments" available.

**Source**: FR-REVIEW-Q4 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#empty-states`, docs/specification/mockups/README.md (Dialog · Review; Run states), ADR-0036. In plain words: an empty list must say that is good news, not look broken; and in the default mode, where nobody watched the run, the person may still want to read and correct segments that passed. Text the brief chose not to translate is neither a problem nor unfinished work, so it is out of the review list and out of the pending count, and only All segments shows it.

#### Scenario: The empty state

- **WHEN** a run finished with 0 segments to review and the person opens the desk from the Finished state
- **THEN** it shows "Nothing needs your review"
- **AND** "All segments" lists every decided segment

### Requirement: Disable Accept while the target pane holds unsaved changes

The desk SHALL offer one main button: "✓ Accept & next" keeps the machine text and moves to the next segment, and once the translation is edited the same button SHALL read "✓ Save my edit & next" and save the text instead; re-reading the list or the segment SHALL NOT replace text the person has typed and not saved.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md (Dialog · Review, Footer), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T7). In plain words: Accept means "the machine was right", so while the person's own typing is pending the button changes its meaning to saving it, and a refresh must never eat what they typed.

#### Scenario: An untouched segment offers Accept

- **WHEN** `ch5 · p12` is selected and the person has not typed
- **THEN** the main button reads "✓ Accept & next"

#### Scenario: A dirty editor disables Accept

- **WHEN** the person has typed into the translation without saving
- **THEN** the "Accept" meaning is withdrawn: the button reads "✓ Save my edit & next" and nothing is accepted without the edit

#### Scenario: An edit turns the button into Save

- **WHEN** the person types `Він пішов геть.` into the translation of `ch5 · p12`
- **THEN** the main button reads "✓ Save my edit & next", and pressing it stores the edit and marks the segment REVISED

#### Scenario: Typed text survives a re-read

- **WHEN** the person has typed `Він пішов геть.` into the translation of `ch5 · p12` without saving and the list is read again
- **THEN** the translation still holds `Він пішов геть.`

### Requirement: Restore or refuse the placeholders of a saved edit

WHEN a saved edit only lost or repeated formatting marks, the system SHALL restore them by the restore-missing repair (document-round-trip, "Repair a refused target's placeholder tokens without a model") and save the repaired text; IF they still do not restore, THEN it SHALL refuse the edit in place with the reason and keep the typed text.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md (Dialog · Review, Editors). In plain words: an edit that deletes a mark would break the book's markup, so it is repaired when that is certain and refused when it is not, never saved broken.

#### Scenario: An edit that drops its placeholders gets them back

- **WHEN** in a Markdown book the machine target is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and the person saves
  `Він відчинив старі двері.` with both tokens deleted
- **THEN** the segment is REVISED as `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.`, reading `Він відчинив *старі* двері.`

#### Scenario: An edit no repair can fix is refused in place

- **WHEN** the machine target is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and the person saves `Двері`, one word that cannot
  hold the pair and keep text outside it
- **THEN** the edit is refused in place with `ErrorCode.validation`, the typed `Двері` stays in the pane, and the
  segment keeps its FLAGGED status and its machine target

### Requirement: Name the tokens a refused edit lacks

WHILE the editor lacks a formatting mark or holds one twice, the desk SHALL show under it a line naming each such mark and what to do — "⟦0⟧ is missing — paste it back where the formatting ends." — and SHALL keep the main button off until every mark is present once.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md (Dialog · Review, Editors), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1). In plain words: a refusal that does not say which mark is wrong leaves the person guessing; the line names it and says where to put it back, and the button cannot save a broken text.

#### Scenario: A missing mark is named and blocks the button

- **WHEN** the machine target is `Він відчинив ⟦0⟧старі⟦1⟧ двері.` and the person deletes `⟦0⟧` in the editor and pastes nothing back
- **THEN** the line reads "⟦0⟧ is missing — paste it back where the formatting ends." and the main button is disabled

#### Scenario: A refused edit offers the missing tokens as chips

- **WHEN** the machine target is `Він відчинив ⟦0⟧старі⟦1⟧ двері.` and a saved edit `Двері` is refused with `ErrorCode.validation` because its marks cannot be placed back
- **THEN** the line under the editor names `⟦0⟧` and `⟦1⟧` as missing, no chips are shown, and pasting `⟦0⟧` back at the start makes the text `⟦0⟧Двері`

#### Scenario: Restoring the mark re-enables the button

- **WHEN** the person pastes `⟦0⟧` back before `старі`
- **THEN** the line is replaced by the note about formatting marks and the main button is enabled

#### Scenario: A doubled mark blocks the button too

- **WHEN** the editor holds `⟦1⟧` twice
- **THEN** the line names `⟦1⟧` as doubled and the main button is disabled

### Requirement: Make the target read-only while a run translates

WHILE a translation is running, pausing or stopping, or a retry is in flight, the desk SHALL be read-only, its header SHALL read "read-only while translating", and a banner SHALL say "Pause the translation to edit, accept or try again." with a Pause button.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md (Dialog · Review, Header). In plain words: an edit made while the run decides the same segment would race it, so the desk waits for the pause and says so, with the button that gets there.

#### Scenario: The target is read-only while a run translates

- **WHEN** a segment is selected and the run moves from PAUSED to RUNNING
- **THEN** the translation editor is read-only, the main button is unavailable, and the note "Pause the translation to edit, accept or try again." is shown

#### Scenario: The desk is read-only during a translation

- **WHEN** the desk is open on `ch5 · p12` and the run moves from PAUSED to RUNNING
- **THEN** the translation editor is read-only, the main button is disabled, and the banner "Pause the translation to edit, accept or try again." with a Pause button is shown

#### Scenario: Pause makes the desk editable

- **WHEN** the person presses Pause in that banner and the run reaches Paused
- **THEN** the banner disappears and the editor accepts typing

### Requirement: Open the segment a review pause names

WHEN a run pauses for review naming a segment, the Translating screen SHALL open the review desk with that segment selected; WHILE the review mode is Manual, the desk SHALL offer Accept & continue, which confirms the segment as Accept does and then resumes the run.

**Source**: FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`, ADR-0036, ADR-0045. In plain words: when the run stops for the person, the thing to look at is already in front of them. When each review mode pauses is the resume capability's "Pause for review as the review mode says"; this requirement covers only what the desk does.

#### Scenario: Assisted opens the flagged segment

- **WHEN** an Assisted run pauses on the flagged `ch5 · p12`
- **THEN** the review desk opens with `ch5 · p12` selected

#### Scenario: Manual confirms every segment

- **WHEN** a Manual run pauses after the ACCEPTED `ch1 · p03`
- **THEN** the desk shows `ch1 · p03` with Accept & continue
- **AND** pressing it marks `ch1 · p03` reviewed and resumes the run, which next decides `ch1 · p04`

### Requirement: Show the suspicious count and chip with hover explanations

The Translating screen's outcome card SHALL show the count as "worth a second look" with a hover explanation, and the review desk SHALL show the "Worth a second look" filter with the same hover explanation, both in English and Ukrainian.

**Source**: FR-REVIEW-A1, task 15d.12, docs/specification/mockups/README.md (Words: suspicious becomes worth a second look). In plain words: the finished run says how many accepted segments a last check still doubts, in words a reader understands, and what the phrase means is one hover away.

#### Scenario: The outcome card counts them

- **WHEN** a run completes and its audit doubts 3 accepted segments
- **THEN** the outcome card shows "3" under "worth a second look"

#### Scenario: The chip explains itself

- **WHEN** the person hovers the "Worth a second look" filter
- **THEN** it explains that it shows accepted segments a last check still doubts

### Requirement: Show a finding's kind and the words it quotes where they stand

The review desk SHALL show each finding once, with one label made of its kind, its severity and what raised it, and SHALL NOT show the same finding again as a separate plain row or badge; the rule code and the ratio SHALL appear only with the "Show technical details" setting on.

**Source**: 08_UI_SCREENS_AND_STATES.md#screen-review, task 15d.13, docs/specification/mockups/README.md (Dialog · Review, Tabs and Code), docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T6–T12). In plain words: a person sees why a segment needs them in one clear line per finding, and never has to read the same fact twice or a rule code they did not ask for.

#### Scenario: A glossary finding shows the name badge

- **WHEN** the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` is open with the finding `"відчинив" — the name is not the locked rendering` of kind `glossary`
- **THEN** the finding shows one label naming the kind "name", and no second badge repeats it

#### Scenario: A glossary finding shows one label

- **WHEN** the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` is open with the finding `"відчинив" — the name is not the locked rendering` of kind `glossary`, severity medium, raised by the name check
- **THEN** the "What may be wrong" tab shows exactly one label for it, naming the kind, the severity and the name check

#### Scenario: A rule code appears only with technical details

- **WHEN** the omission finding `length ratio 0.29, expected 0.35–3.6` is shown, first with "Show technical details" off and then on
- **THEN** off, it reads "may be missing words — if it reads well, press Accept", and on, the line `omission · length ratio 0.29, expected 0.35–3.6` is added

### Requirement: Badge a flagged segment by its worst finding

Each row of the review dialog SHALL carry its state marker and, as its one label, the segment's worst finding, the finding of the highest severity; a tie SHALL go first to a glossary finding (`name`), then a language finding (`wrong lang?`), then an omission finding (`omission`), and any other finding, or a stored score with no finding, SHALL show `low score`.

**Source**: FR-REVIEW-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, ADR-0036, docs/specification/mockups/README.md (Dialog · Review, Left pane). In plain words: the label shows the worst problem, and among equally bad ones the kind a reader notices first — a wrong name before a wrong language before a missing clause; the marker beside it says whether the segment is to review, accepted, your edit or later.

#### Scenario: The worst finding decides the badge

- **WHEN** FLAGGED `ch9 · p03` carries a medium `omission` finding from the length-ratio check and a medium `language`
  finding from the script check, and FLAGGED `ch4 · p02` carries a high `omission` finding from the reviewer and a medium
  `glossary` finding
- **THEN** `ch9 · p03` shows `wrong lang?` and `ch4 · p02` shows `omission`

#### Scenario: A terminology finding counts as a glossary finding

- **WHEN** FLAGGED `ch6 · p08` carries a medium `terminology` finding from the reviewer and a medium `omission` finding
  from the length-ratio check
- **THEN** `ch6 · p08` shows `name`

#### Scenario: Any other finding shows low score

- **WHEN** FLAGGED `ch3 · p09` carries only a medium `meaning` finding raised by `number`
- **THEN** `ch3 · p09` shows `low score`

### Requirement: Compare source and target side by side with the segment's findings and context

WHEN a segment is selected in the review dialog, the application SHALL show its source read-only beside its target, editable and marked EDITABLE, with no inline diff, each formatting mark of the book shown where it stands as `⟦N⟧` and each locked name shown as its rendering.

**Source**: FR-REVIEW-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#side-by-side-compare`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`, docs/specification/mockups/README.md (Dialog · Review, Editors). In plain words: two plain texts side by side are what a translator reads fastest; the marks stay visible so the person keeps the bold, italics and links in place, and a locked name reads as the name.

#### Scenario: A flagged segment in the compare

- **WHEN** `ch5 · p12` is selected
- **THEN** the source pane is read-only, the target pane is editable and marked EDITABLE

#### Scenario: The target shows its placeholders and names

- **WHEN** `ch3 · p02` of a Markdown book has the machine target `Гейл відчинив *старі* двері.`, where `Hale` is locked as `Гейл`, and is selected
- **THEN** the target pane shows `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.`

### Requirement: Locate a segment by chapter and paragraph

A segment's locator SHALL read `ch<n> · p<mm>`, where `n` is its unit's 1-based position among the book's body units and `mm` is the segment's 1-based position within its unit, zero-padded to two digits, and SHALL name the segment the same way in the review dialog, in messages and in the export's report.

**Source**: FR-REVIEW-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, ADR-0036. In plain words: a locator like `ch5 · p12` is what a reader can find in the book — chapter five, twelfth paragraph — and one rule keeps the dialog, a toast and the report from naming the same paragraph three ways.

#### Scenario: Locators count from one and pad the paragraph

- **WHEN** the twelfth segment of the fifth body unit and the third segment of the first body unit are shown
- **THEN** they read `ch5 · p12` and `ch1 · p03`

### Requirement: Browse every segment after an Unattended run

WHEN an Unattended run has finished, the review dialog SHALL offer the filter All segments, listing in document order every decided segment and every segment kept as source by choice, the latter marked `kept as source` and shown by no other filter; the counts SHALL report segments kept as source by choice on their own, never as to review.

**Source**: FR-REVIEW-Q4 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#empty-states`, ADR-0036, docs/specification/mockups/README.md (Dialog · Review, Left pane). In plain words: in the default mode nobody watched the run, so the person may still want to read and correct segments that passed; text the brief chose not to translate is neither a problem nor unfinished work, so only All segments shows it.

#### Scenario: Browsing an accepted segment after an Unattended run

- **WHEN** an Unattended run has finished and the person chooses All segments
- **THEN** ACCEPTED segment `ch2 · p04` can be opened in the compare

#### Scenario: Segments kept as source appear only under All segments

- **WHEN** an Unattended run of an EPUB with 7 navigation labels has finished with the navigation-labels switch off
- **THEN** All segments lists `nav · 01` to `nav · 07`, each marked `kept as source`, and no other filter lists any of them
- **AND** the counts report 7 segments kept as source and do not count them as to review

### Requirement: Apply backward-revision proposals for edited segments only on acceptance

WHEN backward revision proposes a change to a segment the person edited, the application SHALL show the proposal in the review dialog beside the person's text and SHALL change that segment only when the person accepts the proposal, keeping the accepted proposal, like a saved edit, placeholders included.

**Source**: FR-REVIEW-10 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-ALGO-D1, FR-ALGO-D3 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`). In plain words: a whole-book consistency pass may fix a pronoun once a character's gender is known, but a person's own edit is theirs — the pass may suggest, never overwrite.

#### Scenario: A proposal waits for the person

- **WHEN** `ch2 · p07` is REVISED by the person as `Вона пішла.` and backward revision proposes `Він пішов.`
- **THEN** the dialog shows the proposal and the segment keeps `Вона пішла.`
- **AND** when the person accepts the proposal the segment reads `Він пішов.`, stays REVISED, and opens in the editor as `Він пішов.`

#### Scenario: A saved edit or a revert withdraws the proposal built on the old wording

- **WHEN** `ch2 · p07` holds the proposal `Він пішов.` built on the person's `Вона пішла.`, and the person saves `Вона вийшла.`
- **THEN** the dialog shows no proposal for `ch2 · p07`
- **AND** the deferral behind the proposal stays open, so the next pass builds a new proposal from `Вона вийшла.`
- **AND** pressing Revert on a REVISED segment withdraws its waiting proposal the same way

#### Scenario: A flagged segment with a problem the name swap cannot fix stays flagged

- **WHEN** `ch5 · p12` is FLAGGED with an `omission` finding and backward revision swaps `Хейл` for `Гейл` in it
- **THEN** the segment holds the swapped name, stays FLAGGED and stays in the "To review" filter with its `omission` finding
- **AND** a FLAGGED segment whose only finding is a `glossary` finding about that name becomes REVISED

### Requirement: List the accepted segments the final audit doubts

The review dialog SHALL offer a "Worth a second look" filter that lists, in document order, the ACCEPTED segments no person has reviewed that hold an audit finding, each opened with its findings so the check that fired is named, and SHALL count them as "worth a second look" beside its other counts. A listed segment SHALL leave the list once a person accepts or edits it.

**Source**: FR-REVIEW-A1, task 15d.12, docs/specification/mockups/README.md (Words: suspicious becomes worth a second look). In plain words: a segment that passed every gate can still hold a leak, so there is one more list to open after a run, and the finished run says how long it is.

#### Scenario: The chip lists the audited segments

- **WHEN** the person selects "Worth a second look" in the review dialog after a run whose audit doubts `ch2 · p04`
- **THEN** `ch2 · p04` is listed

#### Scenario: Reviewing a listed segment removes it

- **WHEN** the person accepts `ch2 · p04` from the "Worth a second look" list
- **THEN** `ch2 · p04` leaves the list and the "worth a second look" count falls by one

#### Scenario: Segments kept as source are left out and flagged filters are unchanged

- **WHEN** `nav · 01` is kept as source by choice and `ch2 · p04` is the one doubted accepted segment
- **THEN** the "Worth a second look" list and count hold only `ch2 · p04`, and the "To review" filter lists the same segments as before

### Requirement: Show a readable view of the source and the target under the panes

The review dialog SHALL show under the two panes a readable view of the source and the target in which every formatting mark is a chip (`0`, `1`), not raw text, and the words a finding quotes — the quoted opening of its note, or the replacement of an applied edit — are marked in the target at their first occurrence; the target view SHALL follow what is typed in the editor.

**Source**: 08_UI_SCREENS_AND_STATES.md#screen-review, task 15d.13, docs/specification/mockups/README.md (G5). In plain words: a person sees why a segment was flagged on the words themselves, and never has to read a raw mark as if it were part of the book.

#### Scenario: A quoted word is marked and the tokens are chips

- **WHEN** the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` is open with the finding `"відчинив" — the name is not the locked rendering` of kind `glossary`
- **THEN** the target view marks `відчинив`, and shows `0` and `1` as chips with no `⟦` in its text

#### Scenario: The target view follows the editor

- **WHEN** the person appends `!` to the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` in the editor
- **THEN** the target view ends with `двері.!`

### Requirement: Refresh the flagged count when a run stops writing

The review view model SHALL read the to-review count from the stored segments again whenever the run state becomes `PAUSED`, `STOPPED`, `COMPLETED` or `FAILED`, because a run stores its decisions in its last flush, after its last event, and SHALL show only the newest read when two reads finish out of order.

**Source**: task 15d.13 (a real run showed 169 to review on the screen and 170 in the book), docs/specification/mockups/README.md (Words: flagged becomes to review). In plain words: the number on the Review (n) button at the end of a run is the number of segments to review that the book holds.

#### Scenario: The final flush stored one more flagged segment

- **WHEN** a run showing "Review (169)" completes and the stored segments now count 170 to review
- **THEN** the button reads "Review (170)"

## REMOVED Requirements

### Requirement: List flagged segments in a review panel inside the Translating screen

**Reason**: Review is now a large dialog, the review desk, opened from "Review (n)" and by itself at the end of a translation; the Translating screen no longer holds a panel.

**Migration**: Use "Open the review desk as a large dialog and by itself when a translation finishes" for opening and "Filter the desk by what needs attention" for the list.

### Requirement: Filter the flagged list with chips

**Reason**: The chips `all`, `names`, `omissions` and `foreign · kept` give way to the three desk filters "To review", "Worth a second look" and "All segments".

**Migration**: Use "Filter the desk by what needs attention".

### Requirement: Keep review decisions for the session only

**Reason**: Decisions, edits and the project's segments are now stored on disk and survive a restart (ADR-0043), so the session-only rule is false.

**Migration**: The `local-storage` capability's requirement that a project's work survives a restart replaces it; a review done today is still there tomorrow.
