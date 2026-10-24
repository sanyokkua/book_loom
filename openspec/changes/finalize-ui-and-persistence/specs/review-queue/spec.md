## ADDED Requirements

### Requirement: Open the review desk as a large dialog

The application SHALL open the review desk as a large in-window dialog from the toolbar's "Review (n)" and from "n to review" on the Translating screen, and SHALL have no review panel on the Translating screen and no review step in the navigation.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#g1--dialogs-live-in-the-window-on-two-layers, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (decision 5, T6), ADR-0045. In plain words: reviewing is a task of its own that needs the whole window, so it is a dialog and not a panel squeezed beside the run.

#### Scenario: Review (n) opens the desk

- **WHEN** 6 segments need review and the person presses "Review (6)" in the toolbar
- **THEN** the review desk opens as a large dialog over the window, and the Translating screen holds no list of segments to review

#### Scenario: The strip's count opens the desk

- **WHEN** the person presses "6 to review" on the Translating screen's summary strip
- **THEN** the review desk opens with the filter "To review (6)" selected

#### Scenario: No review step in the navigation

- **WHEN** the navigation is shown
- **THEN** it lists Projects, the five workflow steps and Settings, and no Review entry

### Requirement: Open the review desk by itself when a translation finishes

WHEN a translation finishes with at least one segment to review and the setting "Open the review desk when a translation finishes" is on, the application SHALL open the review desk by itself as soon as no other dialog and no progress dialog is shown, with the first segment to review selected; it SHALL NOT open it by itself when 0 segments need review or the setting is off.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#run-states-a, docs/specification/mockups/README.md#generation--appearance--automation--storage--logs. In plain words: the desk appears at the end of a run so the person does not have to look for it, but it never covers something they are already doing, and it stays away when there is nothing to do or when they asked it to.

#### Scenario: The desk opens by itself at the end of a run

- **WHEN** a translation finishes with 6 segments to review, no dialog is open and the setting is on
- **THEN** the review desk opens by itself with the filter "To review (6)" and the first segment to review, `ch2 · p05`, selected

#### Scenario: The desk waits for an open dialog

- **WHEN** the translation finishes with 6 segments to review while the dialog "Stop translating?" is open
- **THEN** the desk does not open while that dialog is shown, and opens by itself once it is closed

#### Scenario: The desk waits for a progress dialog the person is watching

- **WHEN** the translation finishes with 6 segments to review while the "All calls…" progress dialog is shown
- **THEN** the desk opens by itself only after that dialog is closed or hidden

#### Scenario: The setting turns the automatic opening off

- **WHEN** the translation finishes with 6 segments to review and the setting "Open the review desk when a translation finishes" is off
- **THEN** the desk does not open by itself and "Review (6)" stays in the toolbar

#### Scenario: Nothing to review keeps the desk closed

- **WHEN** the translation finishes with 0 segments to review and the setting is on
- **THEN** the desk does not open by itself and the banner reads "Translation finished. Nothing needs your review."

### Requirement: Filter the desk by what needs attention

The review desk SHALL offer the filters "To review (n)", "Worth a second look (n)" and "All segments", and ‹ › controls that move to the previous and next segment of the filter shown; what "Worth a second look" lists is "List in Worth a second look the accepted segments the final audit doubts", and what "All segments" lists is "Browse every segment after an Unattended run".

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#words-w--user-facing-vocabulary, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1). In plain words: the person works through what needs them first, can look at what the last check doubted, and can still read everything else.

#### Scenario: Filters show their counts

- **WHEN** 4 segments are FLAGGED and 3 accepted segments are doubted by the final audit
- **THEN** the filters read "To review (4)", "Worth a second look (3)" and "All segments"

#### Scenario: A filter narrows the list and the arrows follow it

- **WHEN** `ch2 · p05`, `ch5 · p12`, `ch7 · p40` and `ch9 · p03` are FLAGGED, the person selects "To review" and `ch5 · p12` is selected
- **THEN** only those 4 segments are listed, › selects `ch7 · p40` and ‹ selects `ch2 · p05`

### Requirement: Mark every desk row with its state

Each row of the desk's list SHALL show the segment's location, a state marker — to review, accepted, your edit or later — and a one-line "why · text", with formatting marks left out of the line and the full text in the tooltip.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#g11--state-markers-and-one-colour-map. In plain words: each row says at a glance what has been done with it.

#### Scenario: Markers follow the person's actions

- **WHEN** the person accepts `ch5 · p12`, saves an edit of `ch7 · p40` and chooses "Leave for later" on `ch9 · p03`
- **THEN** their rows show the markers accepted, your edit and later

#### Scenario: The row leaves out the marks

- **WHEN** `ch3 · p02`'s translation is `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` and it is flagged for a name
- **THEN** its row reads `name · Гейл відчинив старі двері.` and its tooltip holds the full text

### Requirement: Keep "later" as a mark of this application run only

WHEN the person chooses "Leave for later", the desk SHALL write nothing, SHALL keep the segment FLAGGED and counted as to review, SHALL select the next segment of the filter, and SHALL show the marker later on its row until the application closes; after a restart the row SHALL show to review again.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: putting a segment aside is a bookmark for this sitting, not a decision, so nothing about the book changes and the bookmark is gone next time BookLoom opens.

#### Scenario: Leave for later changes nothing stored

- **WHEN** the person chooses "Leave for later" on FLAGGED `ch9 · p03` and closes the desk
- **THEN** `ch9 · p03` is still FLAGGED, it still counts in "Review (n)", and no change to it was stored

#### Scenario: The mark lasts while BookLoom stays open

- **WHEN** the person chooses "Leave for later" on `ch9 · p03`, closes the desk and opens it again
- **THEN** the row of `ch9 · p03` still shows later

#### Scenario: A restart clears the mark

- **WHEN** the person chooses "Leave for later" on `ch9 · p03`, quits BookLoom and opens the project again
- **THEN** the row of `ch9 · p03` shows to review

### Requirement: Keep formatting marks whole in the translation editor

The desk's translation editor SHALL treat each formatting mark such as `⟦0⟧` as one protected unit: a deletion that touches any part of a mark SHALL remove the whole mark, cutting a whole mark and pasting it elsewhere SHALL move it, and a paste SHALL insert only the marks the editor is missing.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (decision 12, F1). In plain words: a mark that is half deleted breaks the book's bold, links or italics, so the editor never allows half a mark, and the person can still move a mark to where the formatting belongs.

#### Scenario: Deleting part of a mark removes all of it

- **WHEN** the editor holds `Він відчинив ⟦0⟧старі⟦1⟧ двері.` and the person presses Backspace right after the `0`'s closing bracket
- **THEN** the whole mark `⟦0⟧` is removed and the text reads `Він відчинив старі⟦1⟧ двері.`

#### Scenario: Cut and paste moves a mark

- **WHEN** the editor holds `Він відчинив ⟦0⟧старі⟦1⟧ двері.` and the person cuts `⟦1⟧` and pastes it after `двері`
- **THEN** the text reads `Він відчинив ⟦0⟧старі двері⟦1⟧.`

#### Scenario: A paste keeps only the missing marks

- **WHEN** the editor lacks `⟦0⟧` and holds `⟦1⟧`, and the person pastes `⟦0⟧ ⟦1⟧ старі`
- **THEN** only `⟦0⟧` and the words are inserted, and `⟦1⟧` is not doubled

### Requirement: Offer the less common actions under More

The desk's footer SHALL offer a "More ▾" menu with Leave for later, Try again, Try again with a note… and Undo my edit, each with a line saying what it does; Try again with a note… SHALL open an inline panel inside the desk, not a second dialog, and Undo my edit SHALL be disabled while the segment holds no saved edit.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1). In plain words: the common path is one button, and the rarer actions are one click away without crowding the footer or stacking a second window on the desk.

#### Scenario: Try again with a note stays inside the desk

- **WHEN** the person chooses "Try again with a note…" and types `keep it more formal`
- **THEN** an inline panel appears inside the desk, and no second dialog is opened

#### Scenario: Undo my edit is disabled on an unedited segment

- **WHEN** ACCEPTED `ch2 · p04` has no saved edit and the person opens "More ▾"
- **THEN** "Undo my edit" is disabled, and Leave for later, Try again and Try again with a note… are enabled

### Requirement: Move through the desk with keyboard shortcuts

The desk SHALL run its main button on Ctrl+Enter (⌘+Enter on macOS) and SHALL move to the next or previous row on Ctrl+Alt+↓ and Ctrl+Alt+↑, without using Alt+↑ or Alt+↓, which belong to the editor on macOS.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1). In plain words: a person who reviews dozens of segments can stay on the keyboard, and the shortcuts do not collide with text editing.

#### Scenario: Ctrl+Enter runs Accept & next

- **WHEN** `ch5 · p12` is selected and unedited and the person presses Ctrl+Enter
- **THEN** it becomes ACCEPTED and `ch7 · p40` is selected

#### Scenario: Cmd+Enter runs Save my edit & next on macOS

- **WHEN** on macOS the person has typed `Він пішов геть.` into the translation of `ch5 · p12` and presses ⌘+Enter
- **THEN** the edit is stored, `ch5 · p12` becomes REVISED and `ch7 · p40` is selected

#### Scenario: Ctrl+Alt+Down goes to the next row

- **WHEN** `ch5 · p12` is selected and the person presses Ctrl+Alt+↓
- **THEN** `ch7 · p40` is selected and `ch5 · p12` keeps its status

#### Scenario: Ctrl+Alt+Up goes back

- **WHEN** `ch7 · p40` is selected and the person presses Ctrl+Alt+↑
- **THEN** `ch5 · p12` is selected

#### Scenario: Alt+Up stays with the editor

- **WHEN** the caret is in the translation editor of `ch7 · p40` and the person presses Alt+↑
- **THEN** `ch7 · p40` stays selected

### Requirement: Show on "What the model saw" the context the model was actually sent

The desk's "What the model saw" tab SHALL show the segment's stored context snapshot, parsed into its sections; for a segment that was drafted in a batch, it SHALL show the batch context that was actually sent to the model.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#g5--parsed-prompts-and-replies-in-detailed-views-only, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T8). In plain words: to judge a translation the person must see what the model was told, and for a batch that is the batch's package, not a reconstruction.

#### Scenario: A batch-drafted segment shows the batch's context

- **WHEN** `ch2 · p06` was drafted in a batch of 4 segments with the glossary entry `Velk` → `Велк` and the previous 2 pairs
- **THEN** the tab lists the glossary entry and the 2 pairs exactly as sent in that batch

#### Scenario: A segment drafted alone shows its own context

- **WHEN** `ch3 · p01` was drafted alone
- **THEN** the tab shows the context stored with that draft

### Requirement: Show stored formatting marks in the editor by their number

The review editor SHALL show each stored formatting mark `⟦gN⟧` as the text `⟦N⟧` with the same number, and saving SHALL map each `⟦N⟧` back to `⟦gN⟧` before the placeholder check.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (decision 12, F1). In plain words: the stored text keeps its internal mark names, while the person sees short numbered marks that are easy to move and recognise.

#### Scenario: The editor shows numbers and saving maps them back

- **WHEN** the stored target is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and the person opens it, types `рвучко ` after `Він ` and saves
- **THEN** the editor showed `Він відчинив ⟦0⟧старі⟦1⟧ двері.`, and the stored text is `Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері.`

### Requirement: Turn Accept into Save my edit while the translation holds unsaved changes

The desk SHALL offer one main button: "✓ Accept & next" keeps the machine text and moves to the next segment, and once the translation is edited the same button SHALL read "✓ Save my edit & next" and save the text instead; re-reading the list or the segment SHALL NOT replace text the person has typed and not saved.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T7). In plain words: Accept means "the machine was right", so while the person's own typing is pending the button changes its meaning to saving it, and a refresh must never eat what they typed.

#### Scenario: An unedited segment offers Accept

- **WHEN** `ch5 · p12` is selected and the person has not typed
- **THEN** the main button reads "✓ Accept & next"

#### Scenario: An edit turns the button into Save

- **WHEN** the person types `Він пішов геть.` into the translation of `ch5 · p12`
- **THEN** the main button reads "✓ Save my edit & next", nothing can be accepted without the edit, and pressing it stores the edit and marks the segment REVISED

#### Scenario: Typed text survives a re-read

- **WHEN** the person has typed `Він пішов геть.` into the translation of `ch5 · p12` without saving and the list is read again
- **THEN** the translation still holds `Він пішов геть.`

### Requirement: Refuse a saved edit whose formatting marks are missing or doubled

IF a saved edit reaches the placeholder check with a formatting mark missing or doubled, THEN the system SHALL refuse it with `ErrorCode.validation`, store nothing, and keep the segment's stored translation and status.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (decision 12). In plain words: the desk's editor cannot produce such an edit, because its main button stays off until every mark is present once; this check is the safety net behind it, so a broken text is never stored by any path.

#### Scenario: A save with a missing mark is refused

- **WHEN** FLAGGED `ch5 · p12` has the machine target `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and a save of `Він відчинив старі⟦g1⟧ двері.` reaches the placeholder check
- **THEN** it is refused with `ErrorCode.validation`, and `ch5 · p12` stays FLAGGED with `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` as its target

#### Scenario: A save with a doubled mark is refused

- **WHEN** REVISED `ch2 · p07` holds `Вона ⟦g0⟧пішла⟦g1⟧.` and a save of `Вона ⟦g0⟧пішла⟦g1⟧⟦g1⟧.` reaches the placeholder check
- **THEN** it is refused with `ErrorCode.validation`, and `ch2 · p07` stays REVISED with `Вона ⟦g0⟧пішла⟦g1⟧.`

### Requirement: Refuse and hold a blank edit

IF the editor's text is empty or only spaces, THEN the desk SHALL keep the main button disabled and show under the editor "The edit is empty. Type the translation to save it, or choose Leave for later.", and a blank save that reaches the review desk by any path SHALL be refused with `ErrorCode.validation` with nothing stored.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`. In plain words: a blank edit would export an empty paragraph where the book had text; leaving the segment for later is the way to put it off.

#### Scenario: The editor is cleared

- **WHEN** FLAGGED `ch5 · p12` holds `Гейл відчинив двері.` with no formatting marks and the person deletes all of it
- **THEN** the main button is disabled and the line under the editor reads `The edit is empty. Type the translation to save it, or choose Leave for later.`

#### Scenario: A blank save is refused

- **WHEN** a save of `   ` for ACCEPTED `ch2 · p03` reaches the review desk
- **THEN** it is refused with `ErrorCode.validation` and `ch2 · p03` keeps its stored translation

### Requirement: Name the formatting marks the editor lacks and hold the main button

WHILE the editor lacks a formatting mark of the source or holds one twice, the desk SHALL show under it a line naming each such mark and what to do — "⟦0⟧ is missing — paste it back where the formatting ends." — and SHALL keep the main button disabled until every mark is present exactly once.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (decision 12). In plain words: the line names the wrong mark and says where to put it back, and the button cannot save a broken text.

#### Scenario: A missing mark is named and blocks the button

- **WHEN** the translation is `Він відчинив ⟦0⟧старі⟦1⟧ двері.` and the person deletes `⟦0⟧` and pastes nothing back
- **THEN** the line reads "⟦0⟧ is missing — paste it back where the formatting ends." and the main button is disabled

#### Scenario: Restoring the mark re-enables the button

- **WHEN** the person then pastes `⟦0⟧` back before `старі`
- **THEN** the line is replaced by the note "Formatting marks ⟦0⟧ ⟦1⟧ keep bold, links and similar…" and the main button is enabled

#### Scenario: A doubled mark blocks the button too

- **WHEN** the editor holds `⟦1⟧` twice, because the person typed the characters `⟦1⟧` by hand
- **THEN** the line names `⟦1⟧` as doubled and the main button is disabled

### Requirement: Explain Worth a second look on hover where its count is shown

The Translating screen's outcome card SHALL show the count as "worth a second look" with a hover explanation, and the review desk SHALL show the "Worth a second look" filter with the same hover explanation, both in English and Ukrainian.

**Source**: FR-REVIEW-A1, docs/specification/mockups/README.md#words-w--user-facing-vocabulary. In plain words: the finished run says how many accepted segments a last check still doubts, in words a reader understands, and what the phrase means is one hover away.

#### Scenario: The outcome card counts them

- **WHEN** a run completes and its audit doubts 3 accepted segments
- **THEN** the outcome card shows "3" under "worth a second look"

#### Scenario: The filter explains itself

- **WHEN** the person hovers the "Worth a second look" filter
- **THEN** it explains that it shows accepted segments a last check still doubts

### Requirement: Refuse a desk action on a segment whose status it does not start from

The application SHALL refuse with `ErrorCode.validation`, changing nothing, an action on a segment whose status it does not start from: Accept on a PENDING or REVISED segment, Save my edit on a PENDING one, Undo my edit on one that is not REVISED, and Try again on a PENDING or REVISED one.

**Source**: FR-REVIEW-06, FR-REVIEW-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine`. In plain words: a segment a run has not decided yet is never accepted by a person, and undoing or trying again makes sense only on a segment that has something to undo or try again.

#### Scenario: A pending segment cannot be accepted

- **WHEN** Accept is requested for the PENDING segment `ch6 · p01`
- **THEN** it is refused with `ErrorCode.validation` and `ch6 · p01` stays PENDING with no target

#### Scenario: Accept on a revised segment is refused

- **WHEN** `ch2 · p04` is REVISED with `Дощ ущух аж надвечір.` and Accept is requested for it
- **THEN** it is refused with `ErrorCode.validation` and `ch2 · p04` stays REVISED with its edit

#### Scenario: Save my edit on a pending segment is refused

- **WHEN** Save my edit with `Він пішов геть.` is requested for the PENDING segment `ch6 · p01`
- **THEN** it is refused with `ErrorCode.validation` and `ch6 · p01` stays PENDING with no target

#### Scenario: Undo my edit on a segment that is not revised is refused

- **WHEN** Undo my edit is requested for the ACCEPTED segment `ch1 · p03`
- **THEN** it is refused with `ErrorCode.validation` and `ch1 · p03` stays ACCEPTED

#### Scenario: Try again on a pending or revised segment is refused

- **WHEN** Try again is requested for the PENDING segment `ch6 · p01` and for the REVISED segment `ch2 · p04`
- **THEN** both are refused with `ErrorCode.validation` and no model call is made

### Requirement: Mark a segment reviewed only by the desk actions that review it

Accept, Save my edit, Undo my edit, a passing Try again and an accepted proposal SHALL mark the segment reviewed; Leave for later and a failing Try again SHALL NOT.

**Source**: FR-REVIEW-05, FR-REVIEW-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: "reviewed" counts the segments a person actually dealt with, so putting one aside or an attempt that failed must not inflate it.

#### Scenario: Accept marks the segment reviewed

- **WHEN** the person presses "✓ Accept & next" on FLAGGED `ch5 · p12`
- **THEN** it becomes ACCEPTED and is marked reviewed

#### Scenario: A failing Try again does not mark the segment reviewed

- **WHEN** Try again on FLAGGED `ch5 · p12`, an English → Ukrainian source of 60 characters or more, returns a target with a length ratio of `0.45` against the band `0.7–1.8`
- **THEN** `ch5 · p12` stays FLAGGED and is not marked reviewed

#### Scenario: Leave for later leaves the segment flagged

- **WHEN** the person chooses "Leave for later" on `ch5 · p12`
- **THEN** it stays FLAGGED, is not marked reviewed, and `ch7 · p40` is selected

### Requirement: List in Worth a second look the accepted segments the final audit doubts

The review desk's "Worth a second look" filter SHALL list, in document order, the ACCEPTED segments no person has reviewed that hold an audit finding, each opened with its findings so the check that fired is named, and SHALL count them in its label; a listed segment SHALL leave the list once a person accepts or edits it.

**Source**: FR-REVIEW-A1, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#words-w--user-facing-vocabulary. In plain words: a segment that passed every gate can still hold a leak, so there is one more list to open after a run, and its label says how long it is.

#### Scenario: The filter lists the audited segments

- **WHEN** the person selects "Worth a second look" in the review desk after a run whose audit doubts `ch2 · p04`
- **THEN** `ch2 · p04` is listed and its "What may be wrong" tab names the audit check that fired

#### Scenario: Reviewing a listed segment removes it

- **WHEN** the person accepts `ch2 · p04` from the "Worth a second look" list
- **THEN** `ch2 · p04` leaves the list and the count in "Worth a second look (n)" falls by one

#### Scenario: Segments kept as source are left out and the To review filter is unchanged

- **WHEN** the navigation label `nav · 01` is kept as source by choice and `ch2 · p04` is the one doubted accepted segment
- **THEN** the "Worth a second look" list and count hold only `ch2 · p04`, and the "To review" filter lists the same segments as before

## RENAMED Requirements

- FROM: `### Requirement: Show an empty state when nothing is flagged, and browse every segment after an Unattended run`
- TO: `### Requirement: Show an empty state when nothing needs review`

- FROM: `### Requirement: Accept, save an edit, revert, and never discard an edit silently`
- TO: `### Requirement: Accept, save an edit, undo it, and never discard an edit silently`

## MODIFIED Requirements

### Requirement: Show an empty state when nothing needs review

IF the review desk opens with no segment to review, THEN it SHALL show "Nothing needs your review" and keep "All segments" available.

**Source**: FR-REVIEW-Q4 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#empty-states`, docs/specification/mockups/README.md#run-states-a. In plain words: an empty list must say that is good news, not look broken, and the person can still read what was translated.

#### Scenario: The empty state

- **WHEN** a run finished with 0 segments to review and the person opens the desk from the Finished state
- **THEN** it shows "Nothing needs your review"
- **AND** "All segments" lists every decided segment

### Requirement: Make the target read-only while a run translates

WHILE the run state is Translating, Stuck call, Pausing or Stopping, or a retry is in flight, the desk SHALL be read-only. WHILE the run state is Translating, Stuck call, Pausing or Stopping, its header SHALL read "read-only while translating", and a banner SHALL say "Pause the translation to edit, accept or try again." with a Pause button; WHILE a retry is in flight on a run that is not translating, the banner SHALL say "Trying again… the desk is available when the model answers." with no Pause button.

**Source**: FR-REVIEW-05, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md#run-states-a, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: an edit made while the run decides the same segment would race it, so the desk waits for the pause and says so, with the button that gets there.

#### Scenario: The target is read-only while a run translates

- **WHEN** the desk is open on `ch5 · p12` and the run moves from Paused to Translating
- **THEN** the translation editor is read-only, the main button is disabled, the header reads "read-only while translating", and the banner "Pause the translation to edit, accept or try again." with a Pause button is shown

#### Scenario: Pause makes the desk editable

- **WHEN** the person presses Pause in that banner and the run reaches Paused
- **THEN** the banner disappears and the editor accepts typing

#### Scenario: A retry on a paused run is waited for without Pause

- **WHEN** the run is Paused and a Try again on `ch5 · p12` is in flight
- **THEN** the editor is read-only, the banner reads "Trying again… the desk is available when the model answers.", no Pause button is shown, and the header does not say "read-only while translating"

### Requirement: Open the segment a review pause names

WHEN a run pauses for review naming a segment, the application SHALL open the review desk with that segment selected; WHILE the review mode is Manual, the desk SHALL offer Accept & continue, which confirms the segment as Accept does and then resumes the run.

**Source**: FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`, ADR-0036, ADR-0045. In plain words: when the run stops for the person, the thing to look at is already in front of them. When each review mode pauses is the resume capability's "Pause for review as the review mode says"; this requirement covers only what the desk does.

#### Scenario: Assisted opens the flagged segment

- **WHEN** an Assisted run pauses on the flagged `ch5 · p12`
- **THEN** the review desk opens with `ch5 · p12` selected

#### Scenario: Manual confirms every segment

- **WHEN** a Manual run pauses after the ACCEPTED `ch1 · p03`
- **THEN** the desk shows `ch1 · p03` with Accept & continue
- **AND** pressing it marks `ch1 · p03` reviewed and resumes the run, which next decides `ch1 · p04`

### Requirement: Show a finding's kind and the words it quotes where they stand

The review desk SHALL show each finding once, with one label made of its kind, its severity and what raised it, and SHALL NOT show the same finding again as a separate plain row or badge; the rule code and the ratio SHALL appear only with the "Show technical details" setting on.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#g12--simple-first-technical-details-on-request. In plain words: a person sees why a segment needs them in one clear line per finding, and never has to read the same fact twice or a rule code they did not ask for.

#### Scenario: A glossary finding shows the name badge

- **WHEN** the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` is open with the finding `"відчинив" — the name is not the locked rendering` of kind `glossary`
- **THEN** the finding's label names the kind "name"

#### Scenario: A glossary finding shows one label

- **WHEN** the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` is open with the finding `"відчинив" — the name is not the locked rendering` of kind `glossary`, severity medium, raised by the name check
- **THEN** the "What may be wrong" tab shows exactly one label for it, naming the kind "name", the severity and the name check, and no second badge repeats it

#### Scenario: A rule code appears only with technical details

- **WHEN** the omission finding `length ratio 0.29, expected 0.35–3.6` is shown, first with "Show technical details" off and then on
- **THEN** off, it reads "may be missing words — if it reads well, press Accept", and on, the line `omission · length ratio 0.29, expected 0.35–3.6` is added

### Requirement: Badge a flagged segment by its worst finding

Each row of the review desk SHALL carry its state marker and, as its one label, the segment's worst finding, the finding of the highest severity; a tie SHALL go first to a glossary finding (`name`), then a language finding (`wrong lang?`), then an omission finding (`omission`), and any other finding, or a stored score with no finding, SHALL show `low score`.

**Source**: FR-REVIEW-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, ADR-0036, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: the label shows the worst problem, and among equally bad ones the kind a reader notices first — a wrong name before a wrong language before a missing clause.

#### Scenario: The worst finding decides the badge

- **WHEN** FLAGGED `ch9 · p03` carries a medium `omission` finding from the length-ratio check and a medium `language` finding from the script check, and FLAGGED `ch4 · p02` carries a high `omission` finding from the reviewer and a medium `glossary` finding
- **THEN** `ch9 · p03` shows `wrong lang?` and `ch4 · p02` shows `omission`

#### Scenario: A terminology finding counts as a glossary finding

- **WHEN** FLAGGED `ch6 · p08` carries a medium `terminology` finding from the reviewer and a medium `omission` finding from the length-ratio check
- **THEN** `ch6 · p08` shows `name`

#### Scenario: Any other finding shows low score

- **WHEN** FLAGGED `ch3 · p09` carries only a medium `meaning` finding raised by `number`
- **THEN** `ch3 · p09` shows `low score`

### Requirement: Compare source and target side by side with the segment's findings and context

WHEN a segment is selected in the review desk, the application SHALL show its source read-only in the Original pane, with each formatting mark as a chip, beside its translation in the editor, marked EDITABLE, with no inline diff, each formatting mark shown where it stands as `⟦N⟧` and each locked name shown as its rendering.

**Source**: FR-REVIEW-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#side-by-side-compare`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: two plain texts side by side are what a translator reads fastest; the marks stay visible so the person keeps the bold, italics and links in place, and a locked name reads as the name.

#### Scenario: A flagged segment in the compare

- **WHEN** `ch5 · p12` is selected
- **THEN** the Original pane is read-only and the translation editor is editable and marked EDITABLE

#### Scenario: The target shows its placeholders and names

- **WHEN** `ch3 · p02` of a Markdown book has the machine target `Гейл відчинив *старі* двері.`, where `Hale` is locked as `Гейл`, and is selected
- **THEN** the editor shows `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` and the Original pane shows the marks as the chips `0` and `1`

### Requirement: Open the compare on the best text the segment has

The desk's translation editor SHALL hold the person's saved edit when there is one, else the machine target, else the model's refused reply under the note "No usable translation: the model's last reply is shown below, but it broke the formatting.", else the segment's source under the note "No translation kept — showing the source."

**Source**: FR-REVIEW-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#side-by-side-compare`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: the editor opens on the most useful text and says so when it is not a translation; a refused reply, labelled, lets the person put back the one mark instead of retyping the paragraph.

#### Scenario: A segment with only a refused reply shows it, labelled

- **WHEN** `ch12 · p02` is FLAGGED with no machine target and the refused reply `Він відчинив ⟦g0⟧старі двері.`, whose source holds `⟦g0⟧` and `⟦g1⟧`
- **THEN** the editor shows `Він відчинив ⟦0⟧старі двері.` under "No usable translation: …", the line reads "⟦1⟧ is missing — paste it back where the formatting ends." and the main button is disabled
- **AND** once the person pastes `⟦1⟧` after `старі` and presses the main button, `ch12 · p02` becomes REVISED

#### Scenario: A saved edit is shown ahead of the machine target

- **WHEN** `ch2 · p04` has the machine target `Дощ ущух лише надвечір.` and the saved edit `Дощ ущух аж надвечір.`
- **THEN** the editor shows `Дощ ущух аж надвечір.`

#### Scenario: A segment with nothing shows its source, labelled

- **WHEN** `ch8 · p06`, whose source is `He went away.`, is FLAGGED with no machine target and no refused reply
- **THEN** the editor shows `He went away.` under the note that begins `No translation kept — showing the source.`

### Requirement: Accept, save an edit, undo it, and never discard an edit silently

WHEN the person presses "✓ Accept & next" on a FLAGGED or ACCEPTED segment, the application SHALL keep the machine target and mark it ACCEPTED; WHEN they press "✓ Save my edit & next", it SHALL store their text, marks included, as the target and mark it REVISED, keeping the machine target; WHEN they choose "Undo my edit" under More, it SHALL clear the edit and mark the segment ACCEPTED.

**Source**: FR-REVIEW-05, FR-REVIEW-08, FR-REVIEW-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: the machine target is never thrown away, so an edit can always be undone, and a saved edit keeps its marks, so the person can come back and edit it again without losing the formatting.

#### Scenario: Saving an edit keeps the machine target

- **WHEN** `ch5 · p12`'s machine target is `Вартовий зустрів мене опівночі.` and the person saves `Вартовий стрів мене опівночі.`
- **THEN** the segment is REVISED with the edited target
- **AND** "Undo my edit" restores `Вартовий зустрів мене опівночі.` and marks it ACCEPTED

#### Scenario: A revised segment is edited again with its placeholders

- **WHEN** in a Markdown book `ch1 · p05` shows `Він відчинив ⟦0⟧старі⟦1⟧ двері.` and the person saves `Він рвучко відчинив ⟦0⟧старі⟦1⟧ двері.`
- **THEN** it is REVISED and its target reads `Він рвучко відчинив *старі* двері.`
- **AND** opened again, the editor shows `Він рвучко відчинив ⟦0⟧старі⟦1⟧ двері.`, and saving `Він рвучко відчинив ⟦0⟧старезні⟦1⟧ двері.` keeps it REVISED as `Він рвучко відчинив *старезні* двері.`

### Requirement: Retry a segment with the context it first saw

WHEN the person chooses Try again or Try again with a note… under the desk's More menu on a FLAGGED or ACCEPTED segment, the application SHALL draft that one segment again with the brief, glossary entries, recurring-term renderings, preceding targets, memory and summary it first saw, as they read then, adding the note as an extra instruction and lowering the temperature when `Lower temperature for this retry` is checked in the note panel.

**Source**: FR-REVIEW-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-A1, FR-REVIEW-A2 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`), docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, ADR-0038. In plain words: trying again must be a fair second attempt at the same problem, not a translation with different context; the note lets the person steer it ("keep it more formal"). The temperature values are the inference capability's.

#### Scenario: Retry with a note passes

- **WHEN** the run is paused and the person chooses "Try again with a note…" on `ch5 · p12`, types `keep it more formal` and checks `Lower temperature for this retry`
- **AND** the new target passes the hard gates and every soft check, and the reviewer leaves no verified blocker
- **THEN** `ch5 · p12` becomes ACCEPTED with the new target, recorded as drafted and marked reviewed
- **AND** exactly one draft call was made, carrying `keep it more formal` and the temperature `0.1`, plus the one-pair reviewer call when the dial enables the reviewer

#### Scenario: A retry replays the recurring-term renderings as they were

- **WHEN** `ch5 · p12` was drafted while the lexicon established `warden → вартовий`, a later draft made `сторож` the most used rendering, and the person chooses Try again on `ch5 · p12`
- **THEN** the prompt carries `warden → вартовий`, the rendering the first draft saw, and the context line of the segment names `recurring(1)`

#### Scenario: A retry replays the glossary as it was

- **WHEN** `ch5 · p12` was drafted while the glossary held `Hale` → `Хейл`, the person has since changed it to `Гейл`, and chooses Try again on `ch5 · p12`
- **THEN** the prompt carries `Hale` → `Хейл`, the entry the first draft saw

### Requirement: Allow a retry whenever no run of the project is running

WHILE no run of the project is running — its latest run is paused, stopped, completed or failed, or it has none — the application SHALL allow Try again and Try again with a note…; IF one is requested while a run of the project is running, THEN it SHALL refuse it with `ErrorCode.busy` and make no model call; WHILE a retry is in flight, the desk's actions and the toolbar's Resume SHALL be unavailable.

**Source**: FR-REVIEW-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-INFER-06 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#inference-gate`, ADR-0036. In plain words: only one model call runs at a time; a paused run holds no place, so a retry reaches the model at once, and resuming during it would queue two calls. The busy refusal guards the desk's read-only state.

#### Scenario: Retry during a review pause is allowed

- **WHEN** an Assisted run is paused on the flagged `ch5 · p12` and the person chooses Try again
- **THEN** the draft call reaches the provider at once

#### Scenario: Retry after a stop is allowed

- **WHEN** a run was stopped with `ch5 · p12` FLAGGED and the person chooses Try again
- **THEN** the draft call is made

#### Scenario: Retry while running is refused

- **WHEN** a run is running and a retry of `ch5 · p12` is requested
- **THEN** it is refused with `ErrorCode.busy` and no model call is made

#### Scenario: A retry in flight holds the run

- **WHEN** a run is paused and a Try again of `ch5 · p12` is waiting for the model
- **THEN** the toolbar's Resume, the desk's main button and every item under "More ▾" are unavailable until the retry returns

### Requirement: Locate a segment by chapter and paragraph

A segment of a body unit SHALL be located as `ch<n> · p<mm>`, where `n` is its unit's 1-based position among the book's body units and `mm` is the segment's 1-based position within its unit, zero-padded to two digits, and SHALL be named the same way in the review desk, in messages and in the export's report; an auxiliary segment is located by "Locate an auxiliary segment by its kind".

**Source**: FR-REVIEW-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, ADR-0036. In plain words: a locator like `ch5 · p12` is what a reader can find in the book — chapter five, twelfth paragraph — and one rule keeps the desk, a toast and the report from naming the same paragraph three ways.

#### Scenario: Locators count from one and pad the paragraph

- **WHEN** the twelfth segment of the fifth body unit and the third segment of the first body unit are shown
- **THEN** they read `ch5 · p12` and `ch1 · p03`

#### Scenario: An auxiliary segment keeps its kind locator

- **WHEN** the seventh navigation label is shown in the review desk
- **THEN** it reads `nav · 07`, not a `ch<n> · p<mm>` locator

### Requirement: Browse every segment after an Unattended run

WHEN an Unattended run has finished, the review desk's "All segments" filter SHALL list in document order every decided segment and every segment kept as source by choice, the latter marked `kept as source` and shown by no other filter; the counts SHALL report segments kept as source by choice on their own, never as to review.

**Source**: FR-REVIEW-Q4 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`), `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#empty-states`, ADR-0036, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: in the default mode nobody watched the run, so the person may still want to read and correct segments that passed; text the brief chose not to translate is neither a problem nor unfinished work, so only All segments shows it.

#### Scenario: Browsing an accepted segment after an Unattended run

- **WHEN** an Unattended run has finished and the person chooses "All segments"
- **THEN** ACCEPTED segment `ch2 · p04` is listed and opens in the compare

#### Scenario: Segments kept as source appear only under All segments

- **WHEN** an Unattended run of an EPUB with 7 navigation labels has finished with the "Table of contents and chapter titles" switch off
- **THEN** "All segments" lists `nav · 01` to `nav · 07`, each marked `kept as source`, and no other filter lists any of them
- **AND** the counts report 7 segments kept as source and do not count them as to review

### Requirement: Apply backward-revision proposals for edited segments only on acceptance

WHEN backward revision proposes a change to a segment the person edited, the application SHALL show the proposal in the review desk beside the person's text and SHALL change that segment only when the person accepts the proposal, keeping the accepted proposal, like a saved edit, marks included.

**Source**: FR-REVIEW-10 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-ALGO-D1, FR-ALGO-D3 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`). In plain words: a whole-book consistency pass may fix a pronoun once a character's gender is known, but a person's own edit is theirs — the pass may suggest, never overwrite.

#### Scenario: A proposal waits for the person

- **WHEN** `ch2 · p07` is REVISED by the person as `Вона пішла.` and backward revision proposes `Він пішов.`
- **THEN** the desk shows the proposal and the segment keeps `Вона пішла.`
- **AND** when the person accepts the proposal the segment reads `Він пішов.`, stays REVISED, and opens in the editor as `Він пішов.`

#### Scenario: A saved edit or a revert withdraws the proposal built on the old wording

- **WHEN** `ch2 · p07` holds the proposal `Він пішов.` built on the person's `Вона пішла.`, and the person saves `Вона вийшла.`
- **THEN** the desk shows no proposal for `ch2 · p07`
- **AND** the deferral behind the proposal stays open, so the next pass builds a new proposal from `Вона вийшла.`
- **AND** choosing "Undo my edit" on a REVISED segment withdraws its waiting proposal the same way

#### Scenario: A flagged segment with a problem the name swap cannot fix stays flagged

- **WHEN** `ch5 · p12` is FLAGGED with an `omission` finding and backward revision swaps `Хейл` for `Гейл` in it
- **THEN** the segment holds the swapped name, stays FLAGGED and stays in the "To review" filter with its `omission` finding
- **AND** a FLAGGED segment whose only finding is a `glossary` finding about that name becomes REVISED

### Requirement: Show a readable view of the source and the target under the panes

The review desk SHALL show under the two panes a readable view of the source and the target in which every formatting mark is a chip (`0`, `1`), not raw text, and the words a finding quotes — the quoted opening of its note, or the replacement of an applied edit — are marked in the target at their first occurrence; the target view SHALL follow what is typed in the editor.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`, docs/specification/mockups/README.md#g5--parsed-prompts-and-replies-in-detailed-views-only. In plain words: a person sees why a segment was flagged on the words themselves, and never has to read a raw mark as if it were part of the book.

#### Scenario: A quoted word is marked and the tokens are chips

- **WHEN** the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` is open with the finding `"відчинив" — the name is not the locked rendering` of kind `glossary`
- **THEN** the target view marks `відчинив`, and shows `0` and `1` as chips with no `⟦` in its text

#### Scenario: The target view follows the editor

- **WHEN** the person appends `!` to the target `Гейл відчинив ⟦0⟧старі⟦1⟧ двері.` in the editor
- **THEN** the target view ends with `двері.!`

### Requirement: Refresh the flagged count when a run stops writing

The application SHALL read the to-review count from the stored segments again whenever the run state becomes Paused, Stopped, Finished or Did not finish, because a run stores its decisions in its last flush, after its last event, and SHALL show only the newest read when two reads finish out of order.

**Source**: task 15d.13 (`openspec/changes/archive/2026-10-19-complete-translation-workflow/additional-work.md`), docs/specification/mockups/README.md#words-w--user-facing-vocabulary. In plain words: the number on the Review (n) button at the end of a run is the number of segments to review that the book holds; a real run once showed 169 on the screen and 170 in the book.

#### Scenario: The final flush stored one more flagged segment

- **WHEN** a run showing "Review (169)" finishes and the stored segments now count 170 to review
- **THEN** the button reads "Review (170)"

#### Scenario: An older read does not overwrite a newer one

- **WHEN** a read started at Paused returns 12 after a read started at Stopped has already shown 13
- **THEN** the button keeps "Review (13)"

## REMOVED Requirements

### Requirement: List flagged segments in a review panel inside the Translating screen

**Reason**: Review is now a large dialog, the review desk, opened from "Review (n)" and by itself at the end of a translation; the Translating screen no longer holds a panel.

**Migration**: Use "Open the review desk as a large dialog" and "Open the review desk by itself when a translation finishes" for opening, and "Filter the desk by what needs attention" for the list.

### Requirement: Filter the flagged list with chips

**Reason**: The chips `all`, `names`, `omissions` and `foreign · kept` give way to the three desk filters "To review", "Worth a second look" and "All segments".

**Migration**: Use "Filter the desk by what needs attention".

### Requirement: Keep review decisions for the session only

**Reason**: Decisions, edits and the project's segments are now stored on disk and survive a restart (ADR-0043), so the session-only rule is false.

**Migration**: The `local-storage` capability's requirement that a project's work survives a restart replaces it; a review done today is still there tomorrow.

### Requirement: Disable Accept while the target pane holds unsaved changes

**Reason**: The desk has one main button that turns from "✓ Accept & next" into "✓ Save my edit & next" once the text is edited, instead of a disabled Accept with a hint.

**Migration**: Use "Turn Accept into Save my edit while the translation holds unsaved changes".

### Requirement: Restore or refuse the placeholders of a saved edit

**Reason**: The protected editor (decision 12) keeps the main button off until every mark is present once, so no saved edit reaches the store with a mark missing; the automatic restore is no longer needed and a safety-net refusal remains.

**Migration**: Use "Refuse a saved edit whose formatting marks are missing or doubled".

### Requirement: Name the tokens a refused edit lacks

**Reason**: The desk names a missing or doubled mark while the person edits, before any save, and offers no chips.

**Migration**: Use "Name the formatting marks the editor lacks and hold the main button".

### Requirement: Show the suspicious count and chip with hover explanations

**Reason**: "suspicious" becomes "worth a second look", and the chip becomes a desk filter.

**Migration**: Use "Explain Worth a second look on hover where its count is shown".

### Requirement: Refuse an action on a segment whose status it does not start from

**Reason**: The actions are renamed: Revert becomes "Undo my edit" and Retry becomes "Try again"; the rule is unchanged.

**Migration**: Use "Refuse a desk action on a segment whose status it does not start from".

### Requirement: Mark a segment reviewed only by the actions that review it

**Reason**: Skip becomes "Leave for later" and Revert becomes "Undo my edit" in the desk; the rule is unchanged.

**Migration**: Use "Mark a segment reviewed only by the desk actions that review it".

### Requirement: List the accepted segments the final audit doubts

**Reason**: The "suspicious" chip becomes the desk filter "Worth a second look (n)"; the rule is unchanged.

**Migration**: Use "List in Worth a second look the accepted segments the final audit doubts".

