## ADDED Requirements

### Requirement: Show a segment's proofread verdict and its reason in the compare

WHEN a segment holds a proofread verdict, the compare SHALL show it (kept, proposed, rejected or unchanged) with the reason, and, for a proposal, the rewrite beside the person's text, which stays unchanged.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#side-by-side-compare`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the Proofread stage changes finished text, so the person must see what it did and why; a kept rewrite is also shown as a diff with the findings, a rejected one says that the never-worse gate refused it, and a rewrite of a text the person edited waits as a proposal. The words are English and Ukrainian.

#### Scenario: A kept rewrite is shown as a diff

- **WHEN** the Proofread stage kept `Ліна сказала` for `Ліна сказав` in `ch4 · p07` because the gender of Ліна is known
- **THEN** the compare shows the verdict `kept`, the reason "gender of Ліна is female" and the diff `− Ліна сказав` / `+ Ліна сказала`

#### Scenario: A proposal is shown beside the person's text

- **WHEN** `ch2 · p07` is REVISED by the person and the stage produced a passing rewrite
- **THEN** the compare shows the verdict `proposed` and the rewrite beside the person's text, which is unchanged

#### Scenario: A rejected rewrite says so

- **WHEN** the never-worse gate refused the rewrite of `ch5 · p12` because it added the word `что`
- **THEN** the compare shows the verdict `rejected` with that reason and no diff, and the segment's marks stay

#### Scenario: An unchanged answer is shown

- **WHEN** the model answered `unchanged` for `ch6 · p02`
- **THEN** the compare shows the verdict `unchanged` and no diff

#### Scenario: A segment without a proofread verdict shows no verdict

- **WHEN** `ch1 · p01` was never read by the Proofread stage
- **THEN** the compare shows no verdict line

### Requirement: Undo a kept proofread rewrite

WHEN the person chooses "Undo proofread" on a segment whose proofread verdict is kept, the application SHALL restore the stored before text as the target and the status the segment had before the rewrite, ACCEPTED or FLAGGED with its findings.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a rewrite the program kept is the program's, not the person's; one action puts the earlier wording back, from the text the stage stored for exactly this purpose, without touching an edit the person made themselves. A segment that was FLAGGED goes back to FLAGGED, so Undo never accepts a text a check blocked. A segment with no kept rewrite refuses the action ("Refuse a desk action on a segment whose status it does not start from").

#### Scenario: Undo proofread restores the earlier wording

- **WHEN** `ch4 · p07` is REVISED by a kept rewrite `Ліна сказала` whose before text is `Ліна сказав`, and the person chooses "Undo proofread"
- **THEN** its target is `Ліна сказав` and it is ACCEPTED

#### Scenario: A person's edit is not touched

- **WHEN** `ch2 · p04` is REVISED by the person's edit
- **THEN** "Undo proofread" is disabled for it and "Undo my edit" is enabled

#### Scenario: Undo proofread returns a flagged segment to flagged

- **WHEN** `ch5 · p14` was FLAGGED with a BLOCK `alphabet` finding on `кобылу`, the Proofread stage kept the rewrite `кобилу` that cleared it and made it REVISED, and the person chooses "Undo proofread"
- **THEN** its target is the text with `кобылу` again and it is FLAGGED with the `alphabet` finding

### Requirement: List in Changed by proofread the segments whose rewrite was kept or proposed

The review desk's "Changed by proofread" filter SHALL list, in document order, the segments whose proofread verdict is kept or proposed, each opened with its verdict line, and SHALL count them in its label.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: after the stage the person can read exactly what it touched, in order, and undo what they dislike.

#### Scenario: The filter lists the changed segments

- **WHEN** the Proofread stage kept a rewrite for `ch4 · p07` and proposed one for `ch2 · p07`, and answered `unchanged` for `ch6 · p02`
- **THEN** the filter reads "Changed by proofread (2)" and lists `ch2 · p07` then `ch4 · p07`

#### Scenario: Undoing a rewrite removes the segment from the list

- **WHEN** the person chooses "Undo proofread" on `ch4 · p07`
- **THEN** `ch4 · p07` leaves the list and the count falls to 1

#### Scenario: A run without proofread changes shows an empty list

- **WHEN** the stage changed nothing
- **THEN** the filter reads "Changed by proofread (0)" and lists nothing

### Requirement: List in Waiting on a gender the segments that hold an open gender deferral

The review desk's "Waiting on a gender" filter SHALL list, in document order, the segments that hold an open gender deferral — a character named in them had no gender when they were decided, or had it changed afterwards — and SHALL count them in its label.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a segment decided before the book said whether a character is a man or a woman may carry the wrong ending; the list shows which ones are waiting for the Proofread stage or for the person, and shrinks as the deferrals are resolved.

#### Scenario: Segments naming a character without a gender are listed

- **WHEN** the glossary entry Ірвін has no gender and `ch3 · p04` and `ch3 · p09` were decided naming Ірвін
- **THEN** the filter reads "Waiting on a gender (2)" and lists both

#### Scenario: A resolved deferral leaves the list

- **WHEN** the person sets Ірвін as male, the run continues and the stage resolves the deferral of `ch3 · p04`
- **THEN** `ch3 · p04` leaves the list and the count falls to 1

#### Scenario: A changed gender is listed

- **WHEN** Ірвін was female when `ch3 · p12` was decided and the person set male
- **THEN** `ch3 · p12` is listed

### Requirement: List at Fast the segments a gender now known may have left wrong

WHEN the Proofread stage of a Fast run finds by code a gender mismatch in a segment whose deferral's gender has arrived, the review desk SHALL list the segment in "Worth a second look" with the reason `gender now known: <target name> — <gender>`; a segment with no mismatch SHALL have its deferral resolved and not be listed; no model call SHALL be made.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: at Fast the Proofread stage makes no call, so a deferral cannot be resolved by a rewrite; code checks the segment, resolves the deferral when nothing is wrong, and otherwise leaves the segment on the desk for the person.

#### Scenario: A mismatch is listed with its reason

- **WHEN** Fast, the person sets `Irvin` (target `Ірвін`) as male and chooses Continue, and `ch3 · p04` says `Ірвін сказала`
- **THEN** the Proofread stage lists `ch3 · p04` in "Worth a second look" with the reason `gender now known: Ірвін — male`, and no model call is made

#### Scenario: A segment with no mismatch is resolved silently

- **WHEN** Fast, the person sets `Irvin` (target `Ірвін`) as male and chooses Continue, and `ch3 · p09` says `Ірвін сказав`
- **THEN** its deferral is resolved, `ch3 · p09` is not listed and no model call is made

#### Scenario: The person's edit resolves the item

- **WHEN** `ch3 · p04` is listed and the person accepts or edits it on the desk
- **THEN** the item leaves the desk

### Requirement: Label each Worth a second look row with the check that doubts it

Each row of the "Worth a second look" filter SHALL carry a label naming the audit check that doubts the segment, in the language of the interface.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: a list of doubts is only useful if each row says why at a glance: a possible lost negation, a changed last mark.

#### Scenario: A negation doubt is labelled

- **WHEN** the audit doubts `ch2 · p04` because it lost two negations
- **THEN** its row reads "negation" in English and the Ukrainian word in the Ukrainian bundle

#### Scenario: A terminal-mark doubt is labelled

- **WHEN** the audit doubts `ch2 · p05` because its last mark changed
- **THEN** its row reads "terminal mark"

### Requirement: Retry a segment with a context rebuilt from the current glossary and settings

WHEN the person chooses Try again or Try again with a note… under the desk's More menu on a FLAGGED or ACCEPTED segment, the application SHALL draft that one segment again with a context built at that moment from the current glossary, recurring-term renderings, brief settings, preceding accepted targets and chapter summaries, adding the note as an extra instruction and lowering the temperature when `Lower temperature for this retry` is checked.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, `design.md#d8--one-context-builder-block-plan-window-and-budgets`. In plain words: the owner's decision that Try again uses the current glossary and settings: a retry is the person's second attempt after they changed something, so it must see the change. The earlier rule replayed the context the first draft saw, which made a corrected name come back wrong. The temperature values are the inference capability's.

#### Scenario: Retry with a note passes

- **WHEN** the run is paused and the person chooses "Try again with a note…" on `ch5 · p12`, types `keep it more formal` and checks `Lower temperature for this retry`
- **AND** the new target passes the hard gates and every check
- **THEN** `ch5 · p12` becomes ACCEPTED with the new target, recorded as drafted and marked reviewed
- **AND** exactly one draft call was made, carrying `keep it more formal` and the temperature `0.1`

#### Scenario: A retry uses the current glossary

- **WHEN** `ch5 · p12` was drafted while the glossary held `Hale` → `Хейл`, the person has since changed it to `Гейл`, and chooses Try again on `ch5 · p12`
- **THEN** the prompt carries `Hale` → `Гейл`

#### Scenario: A retry uses the current recurring-term rendering

- **WHEN** `ch5 · p12` was drafted while the lexicon established `warden → вартовий`, later segments made `сторож` the rendering the book keeps, and the person chooses Try again
- **THEN** the prompt carries `warden → сторож`

#### Scenario: A retry with nothing changed sees the same context

- **WHEN** nothing in the glossary or the settings changed since `ch5 · p12` was drafted
- **THEN** the retry's context equals the one the first draft saw

### Requirement: Apply proofread proposals for edited segments only on acceptance

WHEN the Proofread stage produces a passing rewrite for a segment the person edited, the application SHALL show the rewrite in the review desk beside the person's text as a proposal and SHALL change that segment only when the person accepts the proposal, keeping the accepted proposal, like a saved edit, marks included.

**Source**: `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine`, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`, `design.md#d14--the-never-worse-gate`. In plain words: the Proofread stage may suggest a better wording once a character's gender is known, but a person's own edit is theirs — the stage may suggest, never overwrite.

#### Scenario: A proposal waits for the person

- **WHEN** `ch2 · p07` is REVISED by the person as `Вона пішла.` and the stage proposes `Він пішов.`
- **THEN** the desk shows the proposal and the segment keeps `Вона пішла.`
- **AND** when the person accepts the proposal the segment reads `Він пішов.`, stays REVISED, and opens in the editor as `Він пішов.`

#### Scenario: A saved edit or a revert withdraws the proposal built on the old wording

- **WHEN** `ch2 · p07` holds the proposal `Він пішов.` built on the person's `Вона пішла.`, and the person saves `Вона вийшла.`
- **THEN** the desk shows no proposal for `ch2 · p07`
- **AND** the deferral behind the proposal stays open, so the next Proofread builds a new proposal from `Вона вийшла.`
- **AND** choosing "Undo my edit" on a REVISED segment withdraws its waiting proposal the same way

#### Scenario: A flagged segment with a problem the name swap cannot fix stays flagged

- **WHEN** `ch5 · p12` is FLAGGED with an `omission` finding and the term sweep swaps `Хейл` for `Гейл` in it
- **THEN** the segment holds the swapped name, stays FLAGGED and stays in the "To review" filter with its `omission` finding
- **AND** a FLAGGED segment whose only finding is a `glossary` finding about that name becomes REVISED

## MODIFIED Requirements

### Requirement: Badge a flagged segment by its worst finding

Each row of the review desk SHALL carry its state marker and, as its one label, the segment's worst finding, the finding of the highest severity; a tie SHALL go first to a glossary finding (`name`), then a language finding (`wrong lang?`), then an omission finding (`omission`), and any other finding, or a stored score with no finding, SHALL show `low score`.

**Source**: FR-REVIEW-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`, ADR-0036, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12. In plain words: the label shows the worst problem, and among equally bad ones the kind a reader notices first — a wrong name before a wrong language before a missing clause.

#### Scenario: The worst finding decides the badge

- **WHEN** FLAGGED `ch9 · p03` carries a medium `omission` finding from the length-ratio check and a medium `language` finding from the script check, and FLAGGED `ch4 · p02` carries a high `omission` finding from the length-ratio check and a medium `glossary` finding
- **THEN** `ch9 · p03` shows `wrong lang?` and `ch4 · p02` shows `omission`

#### Scenario: A terminology finding counts as a glossary finding

- **WHEN** FLAGGED `ch6 · p08` carries a medium `terminology` finding and a medium `omission` finding from the length-ratio check
- **THEN** `ch6 · p08` shows `name`

#### Scenario: Any other finding shows low score

- **WHEN** FLAGGED `ch3 · p09` carries only a medium `meaning` finding raised by `number`
- **THEN** `ch3 · p09` shows `low score`

### Requirement: Show a segment's findings, score and context in the compare

The compare SHALL show the segment's findings — each rewrite the Proofread stage kept as a highlighted diff headed "Edit applied", the words it removed in the error colour and the words it put there in the success colour, and every other finding as its kind, severity, note and the check that raised it — a badge with a stored score when the record has one, and a context line naming what the segment was translated with.

**Source**: FR-REVIEW-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#side-by-side-compare`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`. In plain words: the findings say why the segment is here, and the context line says what the model knew when it translated it. The verdict of the Proofread stage is shown by "Show a segment's proofread verdict and its reason in the compare".

#### Scenario: A flagged segment shows its score and context

- **WHEN** `ch5 · p12` is selected, flagged with a stored score of `0.58`, translated with the brief, two glossary terms, the previous paragraph and the chapter summaries
- **THEN** a badge shows the score `0.58` and the context line reads `brief · glossary(2) · previous paragraph · summary`

#### Scenario: An applied edit is shown as a diff

- **WHEN** the selected segment's proofread verdict records a kept rewrite `Ліна сказала` that replaced `Ліна сказав`
- **THEN** the findings list shows `Edit applied`, then `− Ліна сказав` in the error colour, then `+ Ліна сказала` in the success colour
- **AND** a rewrite that only deleted words shows `+ (removed)`; the Ukrainian bundle shows `Правку застосовано`

#### Scenario: A finding is shown with the check that raised it

- **WHEN** the selected segment's record carries a REPAIR `terminal-mark` finding
- **THEN** the findings list shows its kind, its severity, its note and the check `terminal mark`

#### Scenario: No score badge without a stored score

- **WHEN** the selected segment was translated on the Fast dial, or its decision gave no score
- **THEN** no score badge is shown and its findings are listed

### Requirement: Move segments only through the segment status machine

The application SHALL start every segment as PENDING and move it only through the transitions in the scenarios below,
SHALL never return a segment to PENDING, SHALL never let a retry lower a segment's status, and SHALL keep a FLAGGED
segment FLAGGED unless the person acts on it, a kept proofread rewrite clears every blocking finding, or the term sweep
clears its only finding. REVISED SHALL mean the person's edit, a kept proofread rewrite, or such a swept segment.

**Source**: FR-REVIEW-06, FR-REVIEW-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), `docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine`. In plain words: every segment's state is decided by a small, fixed set of moves, so progress counts, resume and export always agree about what is done; a flagged segment is not retried automatically, because it already used its one repair; the Proofread stage may still fix it, but only a rewrite that clears every blocking finding moves it, and a rewrite that leaves one waits on the desk as a proposal with the segment still FLAGGED. A text the person edited is never rewritten by the stage; its rewrite is a proposal too. A retry on an accepted segment is the person asking for a better wording, so a worse attempt must not cost them the translation they already had. A segment a run has not decided yet is never accepted by a person: a run paused for review has always decided the segment it names before it pauses.

#### Scenario: The pipeline decides a pending segment

- **WHEN** the run decides PENDING `ch6 · p01`
- **THEN** it becomes ACCEPTED or FLAGGED, and only the pipeline moves a segment out of PENDING

#### Scenario: A flagged segment is accepted

- **WHEN** `ch5 · p12` is FLAGGED and the person presses Accept
- **THEN** it becomes ACCEPTED

#### Scenario: A flagged segment moves by retry or edit

- **WHEN** FLAGGED `ch5 · p12` is retried and passes, and FLAGGED `ch7 · p40` is retried and fails, and FLAGGED
  `ch9 · p03` has an edit saved
- **THEN** `ch5 · p12` becomes ACCEPTED, `ch7 · p40` stays FLAGGED with its new findings recorded, and `ch9 · p03`
  becomes REVISED

#### Scenario: A flagged segment is not picked up again by the run

- **WHEN** a run stops with `ch5 · p12` FLAGGED and `ch6 · p01` PENDING, and a new run starts in the same session
- **THEN** the run begins at `ch6 · p01` and `ch5 · p12` stays FLAGGED

#### Scenario: No way back to pending

- **WHEN** an ACCEPTED segment is reverted, retried or edited
- **THEN** it is never PENDING

#### Scenario: Confirming an accepted segment

- **WHEN** the mode is Manual, `ch1 · p03` is ACCEPTED with `Маяк стояв на прибережній скелі.` and the person presses
  Accept & continue
- **THEN** it stays ACCEPTED with `Маяк стояв на прибережній скелі.` and is marked reviewed

#### Scenario: An accepted segment moves by retry or edit

- **WHEN** ACCEPTED `ch2 · p04` is retried and passes, and ACCEPTED `ch2 · p05` is retried and fails, and ACCEPTED
  `ch2 · p06` has an edit saved
- **THEN** `ch2 · p04` stays ACCEPTED with the replaced machine target, `ch2 · p05` stays ACCEPTED unchanged with its
  previous target kept and the new findings reported, and `ch2 · p06` becomes REVISED

#### Scenario: Editing an accepted segment

- **WHEN** the person browses All segments, opens ACCEPTED `ch2 · p04` and saves `Дощ ущух лише надвечір.`
- **THEN** it becomes REVISED with that text, and saving `Дощ ущух аж надвечір.` afterwards keeps it REVISED with the new
  text

#### Scenario: A revised segment moves by revert or edit

- **WHEN** REVISED `ch2 · p07` is reverted, and REVISED `ch2 · p08` has another edit saved or an accepted
  proofread proposal applied
- **THEN** `ch2 · p07` becomes ACCEPTED and `ch2 · p08` stays REVISED

#### Scenario: Backward revision moves accepted and flagged segments

- **WHEN** the Proofread stage keeps a rewrite for ACCEPTED `ch2 · p09`, and its term sweep swaps a name in FLAGGED `ch5 · p12` whose only finding is a `glossary`
  finding about that name, and in FLAGGED `ch5 · p13` that also carries an `omission` finding
- **THEN** `ch2 · p09` becomes REVISED, `ch5 · p12` becomes REVISED, and `ch5 · p13` takes the swapped target and stays
  FLAGGED, keeping its findings

#### Scenario: Undo proofread returns a kept rewrite to accepted

- **WHEN** REVISED `ch2 · p09` holds a kept proofread rewrite made while it was ACCEPTED and the person chooses "Undo proofread"
- **THEN** `ch2 · p09` becomes ACCEPTED with the text the rewrite replaced

#### Scenario: A proofread rewrite that leaves a blocking finding is a proposal

- **WHEN** FLAGGED `ch5 · p15` holds a BLOCK `alphabet` finding and a BLOCK `number` finding, and the never-worse gate keeps a proofread rewrite that clears the `alphabet` finding only
- **THEN** `ch5 · p15` stays FLAGGED with its earlier target and the `number` finding, and the desk shows the rewrite as a proposal

### Requirement: Decide a retry by the checks and the acceptance rule without a repair

The application SHALL pass a retried draft through the hard gates and the checks and SHALL decide it by the acceptance rule, making no repair call.

**Source**: FR-REVIEW-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-A1, FR-REVIEW-A2 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`), ADR-0038. In plain words: the same gates and checks decide a retry as decided the first draft (the quality-gates capability), but a retry is one attempt the person asked for, not an automatic repair — if it fails, the person sees why and chooses again.

#### Scenario: A retry makes no repair rounds

- **WHEN** the retried target of `ch5 · p12`, an English → Ukrainian source of 60 characters or more, has a length ratio
  of `0.45` against the band `0.7–1.8`
- **THEN** only the draft call was made
- **AND** no repair call follows

### Requirement: Filter the desk by what needs attention

The review desk SHALL offer the filters "To review (n)", "Worth a second look (n)", "Changed by proofread (n)", "Waiting on a gender (n)" and "All segments", each listing what its own requirement names, and ‹ › controls that move to the previous and next segment of the filter shown.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#words-w--user-facing-vocabulary, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1). In plain words: the person works through what needs them first, can look at what the audit doubted and what the Proofread stage changed or still waits on, and can still read everything else.

#### Scenario: Filters show their counts

- **WHEN** 4 segments are FLAGGED, 3 accepted segments are doubted by the final audit, the Proofread stage changed 2 segments and 1 segment waits on a gender
- **THEN** the filters read "To review (4)", "Worth a second look (3)", "Changed by proofread (2)", "Waiting on a gender (1)" and "All segments"

#### Scenario: A filter narrows the list and the arrows follow it

- **WHEN** `ch2 · p05`, `ch5 · p12`, `ch7 · p40` and `ch9 · p03` are FLAGGED, the person selects "To review" and `ch5 · p12` is selected
- **THEN** only those 4 segments are listed, › selects `ch7 · p40` and ‹ selects `ch2 · p05`

### Requirement: Offer the less common actions under More

The desk's footer SHALL offer a "More ▾" menu with Leave for later, Try again, Try again with a note…, Undo my edit and Undo proofread, each with a line saying what it does; Try again with a note… SHALL open an inline panel inside the desk, not a second dialog, Undo my edit SHALL be disabled while the segment holds no saved edit, and Undo proofread SHALL be disabled while it holds no kept proofread rewrite.

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (F1). In plain words: the common path is one button, and the rarer actions are one click away without crowding the footer or stacking a second window on the desk.

#### Scenario: Try again with a note stays inside the desk

- **WHEN** the person chooses "Try again with a note…" and types `keep it more formal`
- **THEN** an inline panel appears inside the desk, and no second dialog is opened

#### Scenario: Undo my edit is disabled on an unedited segment

- **WHEN** ACCEPTED `ch2 · p04` has no saved edit and the person opens "More ▾"
- **THEN** "Undo my edit" and "Undo proofread" are disabled, and Leave for later, Try again and Try again with a note… are enabled

#### Scenario: Undo proofread is enabled on a kept rewrite

- **WHEN** REVISED `ch4 · p07` holds a kept proofread rewrite and the person opens "More ▾"
- **THEN** "Undo proofread" is enabled and "Undo my edit" is disabled

### Requirement: Show on "What the model saw" the context the model was actually sent

The desk's "What the model saw" tab SHALL show the segment's stored context snapshot, parsed into its sections, with the run's frame it was sent with; for a segment that was drafted in a batch, it SHALL show the batch context that was actually sent to the model, and for a retry the context rebuilt for that retry, labelled "as it was sent for this retry".

**Source**: docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#g5--parsed-prompts-and-replies-in-detailed-views-only, docs/proposals/2026-10-19-ui-finalization-and-persistence.md (T8). In plain words: to judge a translation the person must see what the model was told, and for a batch that is the batch's package, not a reconstruction.

#### Scenario: A batch-drafted segment shows the batch's context

- **WHEN** `ch2 · p06` was drafted in a batch of 4 segments with the glossary entry `Velk` → `Велк` and the previous 2 pairs
- **THEN** the tab lists the glossary entry and the 2 pairs exactly as sent in that batch

#### Scenario: A retry shows the rebuilt context

- **WHEN** the person changed the glossary entry `Hale` → `Гейл` and chose Try again on `ch5 · p12`
- **THEN** the tab lists `Hale` → `Гейл` and is labelled "as it was sent for this retry"

#### Scenario: A segment drafted alone shows its own context

- **WHEN** `ch3 · p01` was drafted alone
- **THEN** the tab shows the context stored with that draft

### Requirement: Refuse a desk action on a segment whose status it does not start from

The application SHALL refuse with `ErrorCode.validation`, changing nothing, an action on a segment whose status it does not start from: Accept on a PENDING or REVISED segment, Save my edit on a PENDING one, Undo my edit on one that holds no person's edit, Undo proofread on one that holds no kept proofread rewrite, and Try again on a PENDING or REVISED one.

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

- **WHEN** Undo my edit is requested for the ACCEPTED segment `ch1 · p03` and for the REVISED segment `ch4 · p07` that holds a kept proofread rewrite
- **THEN** both are refused with `ErrorCode.validation` and both keep their targets

#### Scenario: Undo proofread on a segment that holds no kept rewrite is refused

- **WHEN** Undo proofread is requested for the REVISED segment `ch2 · p04` that holds the person's edit
- **THEN** it is refused with `ErrorCode.validation` and `ch2 · p04` keeps its edit

#### Scenario: Undo proofread on an accepted segment is refused

- **WHEN** Undo proofread is requested for the ACCEPTED segment `ch1 · p03`
- **THEN** it is refused with `ErrorCode.validation` and `ch1 · p03` keeps its target

#### Scenario: Accept on a kept rewrite is refused

- **WHEN** Accept is requested for the REVISED segment `ch4 · p07` that holds a kept proofread rewrite
- **THEN** it is refused with `ErrorCode.validation`, and the person can choose Undo proofread first

#### Scenario: Try again on a pending or revised segment is refused

- **WHEN** Try again is requested for the PENDING segment `ch6 · p01` and for the REVISED segment `ch2 · p04`
- **THEN** both are refused with `ErrorCode.validation` and no model call is made

### Requirement: List in Worth a second look the accepted segments the final audit doubts

The review desk's "Worth a second look" filter SHALL list, in document order, the ACCEPTED segments no person has reviewed that hold an audit mark or a proofread mark still open (at Fast, each doubt the Proofread stage's mark list named), each opened with its findings so the check that fired is named, and SHALL count them in its label; a listed segment SHALL leave the list once a person accepts or edits it.

**Source**: FR-REVIEW-A1, docs/specification/mockups/README.md#dialog--review-decision-5-f1--t6t12, docs/specification/mockups/README.md#words-w--user-facing-vocabulary. In plain words: a segment that passed every gate can still hold a leak or a defect the repair could not clear, so there is one more list to open after a run, and its label says how long it is. At Fast, where the Proofread stage makes no call, this list is where the marks the draft left are found.

#### Scenario: The filter lists the audited segments

- **WHEN** the person selects "Worth a second look" in the review desk after a run whose audit doubts `ch2 · p04`
- **THEN** `ch2 · p04` is listed and its "What may be wrong" tab names the audit check that fired

#### Scenario: A proofread mark still open is listed

- **WHEN** Balanced and `ch2 · p08` was accepted with a `terminal-mark` finding that the one repair and the Proofread stage did not clear
- **THEN** `ch2 · p08` is listed and its "What may be wrong" tab names `terminal-mark`

#### Scenario: At Fast the marks of the draft are listed

- **WHEN** Fast and `ch2 · p09` was accepted with a repair-level `number` finding
- **THEN** `ch2 · p09` is listed in "Worth a second look"

#### Scenario: Reviewing a listed segment removes it

- **WHEN** the person accepts `ch2 · p04` from the "Worth a second look" list
- **THEN** `ch2 · p04` leaves the list and the count in "Worth a second look (n)" falls by one

#### Scenario: Segments kept as source are left out and the To review filter is unchanged

- **WHEN** the navigation label `nav · 01` is kept as source by choice and `ch2 · p04` is the one doubted accepted segment
- **THEN** the "Worth a second look" list and count hold only `ch2 · p04`, and the "To review" filter lists the same segments as before

### Requirement: Explain Worth a second look on hover where its count is shown

The Translating screen's outcome card SHALL show the count as "worth a second look" with a hover explanation, and the review desk SHALL show the "Worth a second look" filter with the same hover explanation, both in English and Ukrainian.

**Source**: FR-REVIEW-A1, docs/specification/mockups/README.md#words-w--user-facing-vocabulary, `design.md#d15--the-proofread-stage-deferrals-and-proofread-marks`. In plain words: the finished run says how many accepted segments the audit or the Proofread stage still doubts, in words a reader understands, and what the phrase means is one hover away. The export's last check is gone, so the explanation no longer names it.

#### Scenario: The outcome card counts them

- **WHEN** a run completes and its audit doubts 3 accepted segments
- **THEN** the outcome card shows "3" under "worth a second look"

#### Scenario: The filter explains itself

- **WHEN** the person hovers the "Worth a second look" filter
- **THEN** it explains that it shows accepted segments the audit or the Proofread stage still doubts

## REMOVED Requirements

### Requirement: Retry a segment with the context it first saw

**Reason**: Try again no longer replays the context the first draft saw; it rebuilds the context from the current glossary and settings (the owner's decision that Try again uses the current glossary and settings).

**Migration**: "Retry a segment with a context rebuilt from the current glossary and settings".

### Requirement: Apply backward-revision proposals for edited segments only on acceptance

**Reason**: Max's backward revision and the export consistency pass are removed; the Proofread stage replaces them and proposes through the same rule for edited segments.

**Migration**: "Apply proofread proposals for edited segments only on acceptance".

## RENAMED Requirements

- FROM: `### Requirement: Decide a retry by the acceptance rule without repair rounds`
- TO: `### Requirement: Decide a retry by the checks and the acceptance rule without a repair`
