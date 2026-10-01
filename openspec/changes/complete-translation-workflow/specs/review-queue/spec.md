# Spec Delta

## Purpose

How a person inspects and corrects segments inside the Translating screen: the flagged list and its filters, the
side-by-side compare, the six actions, the segment status machine they drive, and what the panel does when a review
mode pauses the run — so that the few segments the pipeline could not clear are fixed without leaving the run.

## ADDED Requirements

### Requirement: List flagged segments in a review panel inside the Translating screen

The Translating screen SHALL offer a `Review flagged (n)` control that opens a review panel listing every FLAGGED
segment by its locator with a badge for its main finding, and four filter chips — `all`, `names`, `omissions` and
`foreign · kept` — each narrowing that list: `names` to segments with a glossary finding, `omissions` to segments with
an omission finding, and `foreign · kept` to segments marked foreign or holding a kept foreign run; the navigation SHALL
have no separate review entry. The main finding SHALL be the finding of the highest severity, a tie going first to a
glossary finding (badge `name`), then a language finding (`wrong lang?`), then an omission finding (`omission`), and
any other finding, or a low judge score with no finding, showing `low score`.

A segment's locator SHALL read `ch<n> · p<mm>`, where `n` is its unit's 1-based position among the book's body units
and `mm` is the segment's 1-based position within its unit, zero-padded to two digits; a segment of the auxiliary unit
SHALL be located by its kind's label instead — `title`, or `author · <kk>`, `description · <kk>`, `nav · <kk>`,
`page title · <kk>`, `alt · <kk>` and `frontmatter · <kk>`, where `kk` is its 1-based position among the auxiliary
segments of that kind, zero-padded to two digits. The same locator SHALL name a segment everywhere the person reads
one: the review panel, messages and the export's report.

**Source:** FR-REVIEW-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`),
`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`,
`docs/specification/mockups/ui-mockup.html` (Review queue), ADR-0036.
In plain words: review happens beside the run it belongs to, so a person never loses sight of progress; the badge and
filters let them take the name problems first, or only the omissions. The badge shows the worst problem, and among
equally bad ones the kind a reader notices first — a wrong name before a wrong language before a missing clause. A
locator like `ch5 · p12` is what a reader can find in the book — chapter five, twelfth paragraph — where an internal id
such as `ch05.xhtml:11` is not, and one rule keeps the panel, a toast and the report from naming the same paragraph
three ways. The mockup draws review as a separate step; keeping it inside Translating is the owner's approved departure.

#### Scenario: Three flagged segments are listed

- **WHEN** segments `ch5 · p12` (judge score 0.58, no finding), `ch7 · p40` (glossary finding) and `ch9 · p03` (script
  check failed) are FLAGGED
- **THEN** the control reads `Review flagged (3)`
- **AND** the list shows `ch5 · p12` with `low score`, `ch7 · p40` with `name` and `ch9 · p03` with `wrong lang?`

#### Scenario: The worst finding decides the badge

- **WHEN** FLAGGED `ch9 · p03` carries a medium `omission` finding from the length-ratio check and a medium `language`
  finding from the script check, and FLAGGED `ch4 · p02` carries a high `omission` finding from the judge and a medium
  `glossary` finding
- **THEN** `ch9 · p03` shows `wrong lang?` and `ch4 · p02` shows `omission`

#### Scenario: Locators count from one and pad the paragraph

- **WHEN** the twelfth segment of the fifth body unit, the third segment of the first body unit, and the seventh
  navigation label of the auxiliary unit are shown
- **THEN** they read `ch5 · p12`, `ch1 · p03` and `nav · 07`

#### Scenario: A filter narrows the list

- **WHEN** the person selects the `names` chip
- **THEN** only `ch7 · p40` is listed

#### Scenario: The foreign · kept chip lists segments with kept foreign text

- **WHEN** `ch11 · p02` is FLAGGED for its length ratio and holds the kept foreign run `au revoir`, and the person
  selects `foreign · kept`
- **THEN** only `ch11 · p02` is listed

#### Scenario: No review entry in the navigation

- **WHEN** the navigation is shown
- **THEN** it has no Review entry

### Requirement: Show an empty state when nothing is flagged, and browse every segment after an Unattended run

IF the review panel opens with no FLAGGED segment, THEN it SHALL show `Nothing flagged — every chunk cleared the
checks` with a Back to progress action; WHEN an Unattended run has finished, the panel SHALL also offer All segments,
listing in document order every decided segment and every segment kept as source by choice, the latter marked
`kept as source` — the only list that shows them — and the review counts SHALL report segments kept as source by
choice on their own, never as pending.

**Source:** FR-REVIEW-Q4 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#flagged-queue`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#empty-states`, ADR-0036.
In plain words: an empty list must say that is good news, not look broken; and in the default mode, where nobody
watched the run, the person may still want to read and correct segments that passed. Text the brief chose not to
translate — for example the table of contents with its switch off (the translation-pipeline capability defines "kept as
source by choice") — is neither a problem nor unfinished work, so it is out of the flagged list and out of the pending
count, and only All segments shows it.

#### Scenario: The empty state

- **WHEN** a run finished with 0 flagged segments and the panel is opened
- **THEN** it shows `Nothing flagged — every chunk cleared the checks` and Back to progress

#### Scenario: Browsing an accepted segment after an Unattended run

- **WHEN** an Unattended run has finished and the person chooses All segments
- **THEN** ACCEPTED segment `ch2 · p04` can be opened in the compare

#### Scenario: Segments kept as source appear only under All segments

- **WHEN** an Unattended run of an EPUB with 7 navigation labels has finished with the navigation-labels switch off
- **THEN** All segments lists `nav · 01` to `nav · 07`, each marked `kept as source`, and no chip lists any of them
- **AND** the review counts report 7 segments kept as source and do not count them as pending

### Requirement: Compare source and target side by side with the segment's findings and context

WHEN a segment is selected in the review panel, the application SHALL show its source read-only and its target —
the person's saved edit when there is one, else the machine target, else the model's refused reply (its rejected
target) under the note "No usable translation: the model's last reply is shown below, but it broke the formatting.",
else its source under the note "No translation kept — showing the source." — editable and marked EDITABLE, side by side
with no
inline diff, with each `⟦gN⟧` placeholder of the book's markup shown where it stands and each locked name shown as its
rendering; and it SHALL show the segment's findings, a badge with its chunk's judge score when a judge ran, and a context
line naming what the segment was translated with.

**Source:** FR-REVIEW-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`),
`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#side-by-side-compare`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`.
In plain words: two plain texts side by side are what a translator reads fastest; the findings say why the segment
is here, and the context line says what the model knew when it translated it. The placeholders stay visible so the
person keeps the bold, italics and links in place when they edit; a locked name reads as the name, because it is not
markup the person has to protect. A segment whose every reply broke the formatting used to open on its English source
in the pane labelled with the target language, which read as an untranslated segment although the model had
translated it; showing the refused reply, labelled, lets the person fix the one token instead of retyping the
paragraph.

#### Scenario: A segment with only a refused reply shows it, labelled

- **WHEN** `ch12 · p02` is FLAGGED with no machine target and the rejected target `Він відчинив ⟦g0⟧старі двері.`
- **THEN** the target pane shows `Він відчинив ⟦g0⟧старі двері.` under "No usable translation: …"
- **AND** Save edit is offered before any change, because saving repairs the reply if it can

#### Scenario: A flagged segment in the compare

- **WHEN** `ch5 · p12` is selected, flagged with a judge score of `0.58`, translated with the brief, two glossary
  terms, the previous paragraph and the summary
- **THEN** the source pane is read-only, the target pane is editable and marked EDITABLE
- **AND** a badge shows `judge 0.58` and the context line reads `brief · glossary(2) · previous paragraph · summary`

#### Scenario: The target shows its placeholders and names

- **WHEN** `ch3 · p02` of a Markdown book has the machine target `Гейл відчинив *старі* двері.`, where `Hale` is locked
  as `Гейл`, and is selected
- **THEN** the target pane shows `Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері.`

#### Scenario: No judge on Fast

- **WHEN** the selected segment was translated on the Fast dial
- **THEN** no judge score badge is shown and its findings are listed

### Requirement: Move segments only through the segment status machine

The application SHALL start every segment as PENDING and move it only through these transitions:

- PENDING → ACCEPTED or FLAGGED, by the pipeline;
- FLAGGED → ACCEPTED, by Accept or a passing retry; FLAGGED → REVISED, by a saved edit; FLAGGED → FLAGGED, by a failing
  retry, which records its new findings;
- ACCEPTED → ACCEPTED, by Accept used as a confirmation (Manual's Accept & continue), which changes nothing but marks the
  segment reviewed, or by a passing retry, which replaces the machine target; ACCEPTED → ACCEPTED unchanged, by a
  failing retry, which keeps the previous target and reports the new findings; ACCEPTED → REVISED, by a saved edit;
- REVISED → ACCEPTED, by revert; REVISED → REVISED, by another saved edit or an accepted backward-revision proposal;
- ACCEPTED → REVISED, by backward revision; FLAGGED → REVISED, by backward revision only when the segment carries at least one finding and
  every finding is one the revision fixes (a `glossary` finding when the revision swaps the name it names); a FLAGGED segment
  with any other finding takes the revised target and stays FLAGGED, keeping its findings.

No segment SHALL return to PENDING, a retry SHALL never lower a segment's status, and a FLAGGED segment SHALL stay
FLAGGED for the rest of its run unless the person acts on it. An action on a segment whose status it does not start
from — Accept on a PENDING or REVISED segment, Save edit on a PENDING one, Revert on one that is not REVISED, Retry on a
PENDING or REVISED one — SHALL be refused with `ErrorCode.validation` and SHALL change nothing.

**Source:** FR-REVIEW-06, FR-REVIEW-08 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`),
`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-status-state-machine`.
In plain words: every segment's state is decided by a small, fixed set of moves, so progress counts, resume and
export always agree about what is done; a flagged segment is not retried automatically, because it already used its
repair budget. A retry on an accepted segment is the person asking for a better wording, so a worse attempt must not cost
them the translation they already had. A segment a run has not decided yet is never accepted by a person: a run paused
for review has always decided the segment it names before it pauses.

#### Scenario: A flagged segment is accepted

- **WHEN** `ch5 · p12` is FLAGGED and the person presses Accept
- **THEN** it becomes ACCEPTED

#### Scenario: A flagged segment is not picked up again by the run

- **WHEN** a run stops with `ch5 · p12` FLAGGED and `ch6 · p01` PENDING, and a new run starts in the same session
- **THEN** the run begins at `ch6 · p01` and `ch5 · p12` stays FLAGGED

#### Scenario: No way back to pending

- **WHEN** an ACCEPTED segment is reverted, retried or edited
- **THEN** it is never PENDING

#### Scenario: A pending segment cannot be accepted

- **WHEN** Accept is requested for the PENDING segment `ch6 · p01`
- **THEN** it is refused with `ErrorCode.validation` and `ch6 · p01` stays PENDING with no target

#### Scenario: Confirming an accepted segment

- **WHEN** the mode is Manual, `ch1 · p03` is ACCEPTED with `Маяк стояв на прибережній скелі.` and the person presses
  Accept & continue
- **THEN** it stays ACCEPTED with `Маяк стояв на прибережній скелі.` and is marked reviewed

#### Scenario: Editing an accepted segment

- **WHEN** the person browses All segments, opens ACCEPTED `ch2 · p04` and saves `Дощ ущух лише надвечір.`
- **THEN** it becomes REVISED with that text, and saving `Дощ ущух аж надвечір.` afterwards keeps it REVISED with the new
  text

### Requirement: Accept, save an edit, revert, and never discard an edit silently

WHEN the person presses Accept on a FLAGGED or ACCEPTED segment, the application SHALL keep the machine target and mark
the segment ACCEPTED; WHEN they press Save edit, it SHALL store their text, placeholders included, as the target and
mark the segment REVISED while keeping the machine target, so the edit opens again exactly as it was saved; WHEN they
press Revert to machine target, it SHALL clear the edit and mark the segment ACCEPTED; WHILE the target pane holds
unsaved changes, Accept SHALL be disabled with the hint `Editing disables Accept until you Save or Revert.`; WHEN a
saved edit only lost or repeated placeholder tokens, the system SHALL put them back by the restore-missing repair
(document-round-trip, "Repair a refused target's placeholder tokens without a model") and save the repaired text; IF a
saved edit's placeholders still do not restore, THEN it SHALL refuse the edit in place with the reason, keep the typed
text, and show beside the actions a banner naming the tokens the edit lacks, as chips that insert their token at the
cursor, and those it holds too often; WHILE a run is translating or a retry is in flight, the target pane SHALL be
read-only and a note beside the actions SHALL say why — "Review is available when the run pauses." — instead of
leaving Save edit silently unavailable; re-reading the list or the segment SHALL NOT replace text the person has typed
and not saved; and IF Accept is
requested for a segment that holds no machine target, THEN it SHALL refuse it with `ErrorCode.validation` and the
message `There is no machine translation to accept — edit it or retry.` and SHALL change nothing. Accept, Save edit,
Revert, a passing retry and an accepted proposal SHALL mark the segment reviewed; Skip and a failing retry SHALL NOT.

**Source:** FR-REVIEW-05, FR-REVIEW-08, FR-REVIEW-09
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`),
`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`.
In plain words: the machine target is never thrown away, so an edit can always be undone; and because Accept means
"the machine was right", it cannot be pressed while the person's own typing is pending — they must keep or drop it
explicitly. An edit that deletes a placeholder would break the book's markup, so it is refused. A saved edit keeps its
placeholders, so the person can come back and edit it a second time without the formatting being lost. This replaces the
reference's rule that Accept on a dirty editor saves the edit first. A segment can be flagged with no machine target at
all — the model refused, answered nothing or overflowed its context — and Accept would then have nothing to keep, so
it says what to do instead; the editor opens on the source so the person can write the translation themselves.

#### Scenario: Saving an edit keeps the machine target

- **WHEN** `ch5 · p12`'s machine target is `Чудовисько зустріло мене опівночі.` and the person saves
  `Чудовисько стріло мене опівночі.`
- **THEN** the segment is REVISED with the edited target
- **AND** Revert to machine target restores `Чудовисько зустріло мене опівночі.` and marks it ACCEPTED

#### Scenario: A revised segment is edited again with its placeholders

- **WHEN** in a Markdown book `ch1 · p05` shows `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and the person saves
  `Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері.`
- **THEN** it is REVISED and its target reads `Він рвучко відчинив *старі* двері.`
- **AND** opened again, the target pane shows `Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері.`, and saving
  `Він рвучко відчинив ⟦g0⟧старезні⟦g1⟧ двері.` keeps it REVISED as `Він рвучко відчинив *старезні* двері.`

#### Scenario: A dirty editor disables Accept

- **WHEN** the person has typed into the target pane without saving
- **THEN** Accept is disabled and the hint `Editing disables Accept until you Save or Revert.` is shown

#### Scenario: An edit that drops its placeholders gets them back

- **WHEN** in a Markdown book the machine target is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and the person saves
  `Він відчинив старі двері.` with both tokens deleted
- **THEN** the segment is REVISED as `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.`, reading `Він відчинив *старі* двері.`

#### Scenario: An edit no repair can fix is refused with a token banner

- **WHEN** the machine target is `Він відчинив ⟦g0⟧старі⟦g1⟧ двері.` and the person saves `Двері`, one word that cannot
  hold the pair and keep text outside it
- **THEN** the edit is refused in place with `ErrorCode.validation`, the typed `Двері` stays in the pane, and the
  segment keeps its FLAGGED status and its machine target
- **AND** a banner beside the actions offers the chips `⟦g0⟧` and `⟦g1⟧`, and pressing `⟦g0⟧` with the cursor at the
  start makes the text `⟦g0⟧Двері`

#### Scenario: The target is read-only while a run translates

- **WHEN** a segment is selected and the run moves from PAUSED to RUNNING
- **THEN** the target pane is read-only, Save edit is unavailable, and the note "Review is available when the run
  pauses. …" is shown beside the actions

#### Scenario: Accept on a revised segment is refused

- **WHEN** `ch2 · p04` is REVISED with `Дощ ущух аж надвечір.` and Accept is requested for it
- **THEN** it is refused with `ErrorCode.validation` and `ch2 · p04` stays REVISED with its edit

#### Scenario: Accept without a machine target is refused

- **WHEN** `ch8 · p06`, whose source is `He went away.`, is FLAGGED after an empty completion with no machine target, is
  selected, and Accept is requested for it
- **THEN** it is refused with `ErrorCode.validation` and the message
  `There is no machine translation to accept — edit it or retry.`, and `ch8 · p06` stays FLAGGED with no target
- **AND** the target pane shows `He went away.` for the person to edit, and saving `Він пішов геть.` makes it REVISED

#### Scenario: Skip leaves the segment flagged

- **WHEN** the person presses Skip on `ch5 · p12`
- **THEN** it stays FLAGGED, is not marked reviewed, and `ch7 · p40` is selected

### Requirement: Retry a segment with the context it first saw

WHEN the person chooses Retry or Retry with note on a FLAGGED or ACCEPTED segment, the application SHALL draft that one
segment again with the same brief, glossary entries, preceding targets, memory and summary text it first saw — as they
read then, not as they read now — adding the note as an extra instruction and lowering the draft's temperature when
`Lower temperature for this retry` is checked; SHALL pass the result through the hard gates, the soft checks and, when
the brief's quality dial enables the judge, a judge call over that one pair; and SHALL decide it by the acceptance rule
with the review mode's τ, making no repair rounds. A passing result SHALL mark the segment ACCEPTED with the new machine
target, recorded as drafted. A failing result SHALL leave a FLAGGED segment FLAGGED with the new findings, and SHALL
leave an ACCEPTED segment ACCEPTED with its previous target, reporting the new findings.

**Source:** FR-REVIEW-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-A1,
FR-REVIEW-A2 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`),
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-retry-with-note`, ADR-0038.
In plain words: a retry must be a fair second attempt at the same problem, not a translation with different context;
the note lets the person steer it ("keep it more formal"). The same gates, checks and judge decide it as decided the
first draft (the quality-gates capability), but a retry is one attempt the person asked for, not a new round of
automatic repairs — if it fails, the person sees why and chooses again. The temperature values are the inference
capability's. When a retry may run at all is "Allow a retry whenever no run of the project is running".

#### Scenario: Retry with a note passes

- **WHEN** the run is paused and the person retries `ch5 · p12` with the note `keep it more formal` and
  `Lower temperature for this retry` checked
- **AND** the new target passes the hard gates, every soft check, the confidence threshold and the judge
- **THEN** `ch5 · p12` becomes ACCEPTED with the new target, recorded as drafted and marked reviewed
- **AND** exactly one draft call was made, carrying `keep it more formal` and the temperature `0.1`, plus the one-pair
  judge call when the dial enables the judge

#### Scenario: A failing retry stays flagged with new findings

- **WHEN** the retried target has a length ratio of `0.45` against the band `0.7–1.8`
- **THEN** the segment stays FLAGGED and shows the new length-ratio finding
- **AND** no directed fix or other repair call follows

#### Scenario: A failing retry never downgrades an accepted segment

- **WHEN** ACCEPTED `ch2 · p04` reads `Дощ ущух лише надвечір.` and a retry returns a target with a length ratio of
  `0.45` against the band `0.7–1.8`
- **THEN** `ch2 · p04` stays ACCEPTED with `Дощ ущух лише надвечір.` and the new length-ratio finding is reported

#### Scenario: A retry replays the glossary as it was

- **WHEN** `ch5 · p12` was drafted while the glossary held `Hale` → `Хейл`, the person has since changed it to `Гейл`,
  and `ch5 · p12` is retried
- **THEN** the retry's prompt carries `Hale` → `Хейл`, the entry the first draft saw

### Requirement: Allow a retry whenever no run of the project is running

WHILE no run of the project is running — its latest run is paused, stopped, completed or failed, or it has none — the
application SHALL allow Retry and Retry with note; IF a retry is requested while a run of the project is running, THEN
it SHALL refuse it with `ErrorCode.busy` and make no model call; and WHILE a retry is in flight, the Translating screen
SHALL make Resume and the other review actions unavailable.

**Source:** FR-REVIEW-05 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-INFER-06
(`#fr-infer`), `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#inference-gate`, ADR-0034, ADR-0036.
In plain words: only one model call runs at a time. A running book is using the model, so a retry would only queue
behind it; a paused run holds no place (the translation-pipeline capability's "Hold no model-call permit while
paused"), so a retry during a review pause reaches the model at once — which is exactly when a person wants it. The
run's recorded state (the resume capability's "Record whether a run is running, paused or ended") is what tells the
two apart. While the retry runs, resuming the book would put two calls in line again.

#### Scenario: Retry during a review pause is allowed

- **WHEN** an Assisted run is paused on the flagged `ch5 · p12` and the person retries it
- **THEN** the retry's draft call reaches the provider at once

#### Scenario: Retry after a stop is allowed

- **WHEN** a run was stopped with `ch5 · p12` FLAGGED and the person retries it
- **THEN** the retry's draft call is made

#### Scenario: Retry while running is refused

- **WHEN** a run is running and a retry of `ch5 · p12` is requested
- **THEN** it is refused with `ErrorCode.busy` and no model call is made

#### Scenario: A retry in flight holds the run

- **WHEN** a run is paused and a retry of `ch5 · p12` is waiting for the model
- **THEN** Resume, Accept, Save edit, Revert, Retry and Skip are unavailable until the retry returns

### Requirement: Open the segment a review pause names

WHEN a run pauses for review naming a segment, the Translating screen SHALL open the review panel with that segment
selected in the compare; WHILE the review mode is Manual, the compare SHALL offer Accept & continue, which confirms the
segment as Accept does and then resumes the run.

**Source:** FR-REVIEW-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`),
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md#review-modes`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-review`, ADR-0036.
In plain words: when the run stops for the person, the thing to look at is already in front of them. When each review
mode pauses — Assisted on each flagged segment, Manual after every segment, Unattended never — and what an edit made
during the pause feeds after the resume are the resume capability's "Pause for review as the review mode says" and
"Continue after the person acts on the paused segment"; this requirement covers only what the panel does.

#### Scenario: Assisted opens the flagged segment

- **WHEN** an Assisted run pauses on the flagged `ch5 · p12`
- **THEN** the review panel opens with `ch5 · p12` selected in the compare

#### Scenario: Manual confirms every segment

- **WHEN** a Manual run pauses after the ACCEPTED `ch1 · p03`
- **THEN** the compare shows `ch1 · p03` with Accept & continue
- **AND** pressing it marks `ch1 · p03` reviewed and resumes the run, which next decides `ch1 · p04`

### Requirement: Allow export whatever remains flagged, stating the count

The application SHALL allow export whatever remains flagged — at any time except while a run is actively translating,
when the person pauses it first — and, WHEN segments are still FLAGGED or PENDING, SHALL export each FLAGGED segment's
machine target, or its source when it has none, and each PENDING segment's source, and SHALL state how many of each were
written.

**Source:** FR-REVIEW-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-REVIEW-X1,
FR-REVIEW-X2 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#export-any-time`), ADR-0035.
In plain words: review is optional, so it must never block getting the book out; the person is told how much of it is
unreviewed or untranslated. While a run is translating, a book written would disagree with what the run decides a
moment later, so export waits for a pause. A segment flagged because its markup never restored has no machine target,
so its source is written rather than broken markup. How the file is written belongs to the export capability.

#### Scenario: Exporting with flags and pending segments

- **WHEN** 3 segments are FLAGGED and 120 are PENDING and the person exports
- **THEN** the flagged three carry their machine targets, the pending 120 carry their source
- **AND** the report states 3 flagged and 120 untranslated

#### Scenario: Export waits while the run translates

- **WHEN** a run is running with 3 segments FLAGGED
- **THEN** export is unavailable with the note `Pause the run to export`
- **AND** after the person pauses the run, export is available and writes the flagged three with their machine targets

### Requirement: Apply backward-revision proposals for edited segments only on acceptance

WHEN backward revision proposes a change to a segment the person edited, the application SHALL show the proposal in
the review panel beside the person's text and SHALL change that segment only when the person accepts the proposal,
keeping the accepted proposal, like a saved edit, placeholders included.

**Source:** FR-REVIEW-10 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-review`), FR-ALGO-D1,
FR-ALGO-D3 (`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#phase-d-backward-revision`).
In plain words: a whole-book consistency pass may fix a pronoun once a character's gender is known, but a person's own
edit is theirs — the pass may suggest, never overwrite. An accepted proposal becomes the person's text, so it can be
edited again like any other edit.

#### Scenario: A proposal waits for the person

- **WHEN** `ch2 · p07` is REVISED by the person as `Вона пішла.` and backward revision proposes `Він пішов.`
- **THEN** the panel shows the proposal and the segment keeps `Вона пішла.`
- **AND** when the person accepts the proposal the segment reads `Він пішов.`, stays REVISED, and opens in the editor as
  `Він пішов.`

#### Scenario: A saved edit or a revert withdraws the proposal built on the old wording

- **WHEN** `ch2 · p07` holds the proposal `Він пішов.` built on the person's `Вона пішла.`, and the person saves
  `Вона вийшла.`
- **THEN** the panel shows no proposal for `ch2 · p07`
- **AND** the deferral behind the proposal stays open, so the next pass builds a new proposal from `Вона вийшла.`
- **AND** pressing Revert on a REVISED segment withdraws its waiting proposal the same way

#### Scenario: A flagged segment with a problem the name swap cannot fix stays flagged

- **WHEN** `ch5 · p12` is FLAGGED with an `omission` finding and backward revision swaps `Хейл` for `Гейл` in it
- **THEN** the segment holds the swapped name, stays FLAGGED and stays in the review list with its `omission` finding
- **AND** a FLAGGED segment whose only finding is a `glossary` finding about that name becomes REVISED

### Requirement: Keep review decisions for the session only

The application SHALL keep every review action's result for the rest of the session and SHALL NOT keep it after the
application closes.

**Source:** FR-REVIEW-A3 (`docs/specification/01_Product/06_REVIEW_AND_EDITING.md#segment-actions`), ADR-0034.
In plain words: storage is in memory until the database arrives, so a review done today is lost on quitting; this is
stated so nobody mistakes it for a bug, and it is what the persistence change will lift.

#### Scenario: A review survives navigation but not a restart

- **WHEN** the person accepts `ch5 · p12`, goes to Export and back to Translating
- **THEN** `ch5 · p12` is still ACCEPTED
- **AND** after closing and reopening the application the book's segments are not remembered
