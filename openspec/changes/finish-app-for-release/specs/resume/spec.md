## ADDED Requirements

### Requirement: Recompute a reopened project's progress from its stored decisions

WHEN a project is opened after a restart, the system SHALL plan the run's steps again from the book, the brief and the stored decisions (a step per window planned from the stored proofread marks and, at Max, per window of the whole-book reading), count as done every decided draft and every window whose focus segments hold verdicts with its current fingerprint, and show no time left until the new run has timed its steps.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `research/review-digest.md#time-breakdown-of-the-traced-run`. In plain words: chapter summaries are no longer written (they cost 6.8 % of a run and no effect on the text could be measured), so a reopened project's steps are its drafts and its proofread windows. The bar still starts where the work stands, and no time left is shown until the new run has timed its own steps.

#### Scenario: The bar starts from the stored decisions

- **WHEN** a Balanced project of 40 segments in 4 chapters of 10 is opened after a restart with the 20 segments of its first 2 chapters decided
- **THEN** 20 of 40 steps count as done (40 drafts), and the title bar reads `Ready · 50 % done`

#### Scenario: Proofread windows already read count as done

- **WHEN** the same project is opened with all 40 segments decided and 6 Proofread windows planned from the stored proofread marks, the focus segments of 1 of which hold verdicts with its current fingerprint
- **THEN** 41 of 46 steps count as done and the title bar reads `Ready · 89 % done`

#### Scenario: Time left waits for the new run

- **WHEN** the same project is opened and Continue translation is chosen
- **THEN** no time left is shown on opening, and it appears only once the new run has timed its own steps

### Requirement: Repeat an unfinished Prepare step when a run continues

WHEN a run is continued, resumed or started again and a Prepare step of its project is not finished, the application SHALL run that step again from its start before any segment is drafted; a step's partial work SHALL never be kept, because a step writes its result once, at its end.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: the brief suggestion and the names pass each hold their answers in memory and write once, so a stop in the middle leaves the brief and the glossary exactly as they were. The continued run then asks again from the first call of that step. In the traced run the two steps took 48 seconds and about 6 minutes; repeating one of them is cheaper and safer than storing half-read names.

#### Scenario: A names step interrupted by a restart is repeated

- **WHEN** the application is closed during the names step's twentieth call, opened again, and the run is continued
- **THEN** the glossary holds what it held before the step, the names step starts again at its first call, and the brief step, already finished, makes no call

#### Scenario: A pause inside a Prepare step

- **WHEN** the person presses Pause during the names step
- **THEN** the call under way is allowed to answer, no further call of the step is sent, nothing of the step is written, and the run reads Paused
- **AND** Resume starts the names step again at its first call

### Requirement: Never repeat a finished Prepare step

WHEN a run is continued and a Prepare step of its project is finished, the application SHALL make no call of that step, whatever happened to the run afterwards.

**Source**: `research/review-digest.md#f11--the-windows-short-path-skips-the-models-brief-and-names-work`, `design.md#d20--prepare-steps-inside-the-run`. In plain words: a run that stopped at paragraph 900 must start at paragraph 901, not spend six minutes on the names again and risk changing renderings half the book already uses.

#### Scenario: A run continued in the Translate stage prepares nothing

- **WHEN** a run with both Prepare steps finished is stopped after 900 decided segments and continued
- **THEN** no brief call and no names call is made, and the first call is the draft of the first pending segment

#### Scenario: A step that failed is not tried again by a continue

- **WHEN** the names step ended with `ErrorCode.validation`, the run went on with the code scan's names, was stopped and is continued
- **THEN** no names call is made; the person can still run the names pass from Names & style

## MODIFIED Requirements

### Requirement: Make the aborted call again when a paused job resumes

WHILE a job is paused, the system SHALL make no model call; on resume it SHALL make the aborted call again from its first request, whether a segment's draft or repair or a Proofread window, and then continue where the run stood.

**Source**: `research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`, `research/review-digest.md#time-breakdown-of-the-traced-run`. In plain words: chapter summaries are no longer written (they cost 6.8 % of a run and no effect on the text could be measured), so the calls a pause can abort are a draft, a repair and a Proofread window. A resumed run still makes the aborted call again and goes on where it stood.

#### Scenario: A pause during a Proofread call

- **WHEN** a Balanced job (batch size 1) over three segments has drafted all three and repaired `Book.txt:2` once, so
  that segment carries a proofread mark, and a pause is requested while the Proofread call for the window around
  `Book.txt:2` is in flight
- **THEN** the job pauses with reason requested, with 3 accepted and 0 pending, and the provider has received `5`
  requests in total: 3 drafts, 1 repair and 1 Proofread call
- **AND** after resume the Proofread call is made again and no draft or repair is repeated, and the job ends Completed
  with the provider having received `6` requests in total

### Requirement: Continue the stage that has work

WHEN the person chooses Continue translation, the system SHALL start a new run at the first stage that has work left: Prepare while a Prepare step of the project is not finished (the step runs again from its start, "Repeat an unfinished Prepare step when a run continues"), Translate while any segment is pending or, WHERE the last draft is built (`design.md#d4--the-last-draft-one-more-single-draft-at-the-end-of-translate`), any FLAGGED segment with no translation still waits for its one last draft, Proofread while an open deferral (a changed rendering or a fact that has arrived) exists or any planned window's focus segments do not all hold a proofread verdict with its current fingerprint.

**Source**: `design.md#d20--prepare-steps-inside-the-run`, `design.md#d4--the-last-draft-one-more-single-draft-at-the-end-of-translate`, `research/review-digest.md#f12--finished-is-shown-for-a-book-with-untranslated-paragraphs`. In plain words: a run has three stages and may be stopped in any of them. Prepare now makes model calls (the brief suggestion and the names pass), so a run stopped there continues with the step that was not finished. Continue goes to the stage where the work stands and does no model work when nothing is left. Where the last draft is built, a run stopped while it was making the last drafts of the paragraphs that had no translation continues there: those paragraphs count as Translate work until each has had its one last draft.

#### Scenario: A stopped translation continues at Translate

- **WHEN** a Balanced run is stopped with 412 of 1,240 segments decided and the person chooses Continue translation
- **THEN** the new run starts in the Translate stage at the 413th segment

#### Scenario: A finished translation continues at Proofread

- **WHEN** all 1,240 segments are decided, 120 Proofread windows are planned, the focus segments of 40 of them hold proofread verdicts with their current fingerprint, and the person chooses Continue translation
- **THEN** the new run starts in the Proofread stage and reads the other 80 windows

#### Scenario: A run stopped before any draft continues at Prepare

- **WHEN** a run was stopped during its names step, with the brief step finished and 0 of 1,240 segments decided
- **THEN** Continue translation starts in the Prepare stage, makes no brief call, runs the names step again from its first call, and goes on to Translate at the first segment

#### Scenario: A project with no work left makes no call

- **WHEN** all segments are decided, no deferral is open, and the focus segments of every planned window hold proofread verdicts with their current fingerprint
- **THEN** Continue translation sends no request to the provider and the run ends Completed

#### Scenario: A gender set after a finished Max run is offered and read

- **WHEN** a Max run has ended Completed, the person then sets the gender of `Hale` to female, which marks `Book.md:0` (`Хейл пішов.`) with `gender now known: Хейл — female`
- **THEN** the Translating screen offers Continue translation, and once the Continue has read that window it offers nothing more

#### Scenario: A rendering changed after a finished Fast run is offered

- **WHEN** a Fast run has ended Completed and the person then changes the locked target of `Hale` from `Хейл` to `Гейл`
- **THEN** the Translating screen offers Continue translation, the Continue sweeps `Гейл` into the decided text with no model call, and then nothing more is offered

#### Scenario: A gender set after a finished Fast run is settled by code

- **WHEN** a Fast run has ended Completed, the person then sets the gender of `Ірвін` to male in the Glossary, which records deferrals for 3 decided segments that name him, and chooses Continue translation
- **THEN** the new run starts in the Proofread stage, settles the 3 deferrals by code, sends no request to the provider, and ends Completed

#### Scenario: A run stopped during the last drafts continues there

- **WHEN** the last draft is built, all 1,240 segments are decided, 5 FLAGGED segments had no translation, the run was stopped after the last draft of 2 of them, and the person chooses Continue translation
- **THEN** the new run starts in the Translate stage, makes the last draft of the other 3 segments and no other draft call, and goes on to Proofread

#### Scenario: A segment that had its last draft is not work

- **WHEN** the last draft is built, all segments are decided, the only FLAGGED segment with no translation has already had its last draft, no deferral is open and every planned window is read
- **THEN** Continue translation is not offered for it and a Continue sends no request to the provider

## REMOVED Requirements

### Requirement: Write the missing chapter summaries first

**Reason**: Chapter summaries are no longer written. They took 6.8 % of the traced run (27 calls, 137 s, 13 of them re-asks), their block was 11.6 % of the draft prompt tokens, and the replies overlapped the block no more than a random other reply (7.2 % against 7.0 %); removing the context blocks other than name rows changed the blind score by at most 0.15, inside the 0.3 noise band (`research/review-digest.md#f8--context-blocks-other-than-name-rows-add-nothing-measurable`).

**Migration**: None for the person. A stored summary is not read; the database is set aside and started fresh by this change (ADR-0043). Continue makes no summary call; it starts at the first stage that has work ("Continue the stage that has work").

### Requirement: Recompute the progress of a reopened project from its stored decisions

**Reason**: Restated as "Recompute a reopened project's progress from its stored decisions": a scenario of the old requirement names a thing that no longer exists, and a MODIFIED block may not drop or rename a scenario.

**Migration**: None. See "Recompute a reopened project's progress from its stored decisions" under ADDED Requirements; the behaviour that stays is stated there in full.
