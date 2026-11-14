# BookLoom UI audit screenshots — 2026-11-14

- **Build:** development run (`./gradlew :app:run`), branch `feature/finish-app-for-release`, commit `8d507357`; About says "Version dev". macOS (Darwin 27.2), Retina 2× — every PNG is the window only at full resolution.
- **Data:** a fresh data folder (`BOOKLOOM_DATA_DIR` pointed into the session scratchpad), so the owner's `BookLoom-Dev` data was never opened. First launch found Ollama and LM Studio by itself; the model picked was `gemma4:e4b-mlx` (Ollama).
- **Books:** the repository fixture "Earth Gravity" (`modules/app/src/test/resources/fixtures/earth-gravity/`), copied to the scratchpad: the EPUB (164 segments, EN → UK, translated to the end and exported into the scratchpad) and the Markdown (143 segments; one project with no target language, one EN → DE stopped at 12 percent).
- **Files:** `shots/NN-screen-state-theme-lang.png`, 191 files. Numbers follow the order of capture; gaps are removed duplicates. Window 1440×900 pt unless the name says `narrow1000` (1000×900 pt).
- **Banner note:** from shot 140 on (dark, narrow and most Ukrainian shots) the project had been reopened from Projects, so the blue "Welcome back — 164 of 164 segments are done" banner sits at the top of every screen, Settings included, and takes about 120 pt of height. It stayed until a run was started (shots 204–209 are without it).

## Files

| File | Screen | State | Theme | Language | Window |
|---|---|---|---|---|---|
| `01-first-launch-model-pick-dialog-light-en.png` | First launch | model pick dialog | light | en | 1440×900 pt (2880×1800 px) |
| `02-projects-empty-no-model-light-en.png` | Projects | empty no model | light | en | 1440×900 pt (2880×1800 px) |
| `03-projects-empty-model-ready-light-en.png` | Projects | empty model ready | light | en | 1440×900 pt (2880×1800 px) |
| `04-import-before-book-light-en.png` | Import book | before book | light | en | 1440×900 pt (2880×1800 px) |
| `05-import-book-open-top-light-en.png` | Import book | book open top | light | en | 1440×900 pt (2880×1800 px) |
| `06-import-book-chapter-segment-selected-light-en.png` | Import book | book chapter segment selected | light | en | 1440×900 pt (2880×1800 px) |
| `07-import-book-scrolled-down-light-en.png` | Import book | book scrolled down | light | en | 1440×900 pt (2880×1800 px) |
| `08-import-about-this-book-open-top-light-en.png` | Import book | about this book open top | light | en | 1440×900 pt (2880×1800 px) |
| `09-import-about-this-book-open-scrolled-light-en.png` | Import book | about this book open scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `10-import-about-this-book-open-middle-light-en.png` | Import book | about this book open middle | light | en | 1440×900 pt (2880×1800 px) |
| `11-brief-top-no-target-light-en.png` | Book Brief | top no target | light | en | 1440×900 pt (2880×1800 px) |
| `12-brief-target-language-dropdown-open-light-en.png` | Book Brief | target language dropdown open | light | en | 1440×900 pt (2880×1800 px) |
| `13-brief-top-target-uk-light-en.png` | Book Brief | top target uk | light | en | 1440×900 pt (2880×1800 px) |
| `14-brief-middle-target-uk-light-en.png` | Book Brief | middle target uk | light | en | 1440×900 pt (2880×1800 px) |
| `15-brief-bottom-target-uk-light-en.png` | Book Brief | bottom target uk | light | en | 1440×900 pt (2880×1800 px) |
| `16-brief-lower-middle-target-uk-light-en.png` | Book Brief | lower middle target uk | light | en | 1440×900 pt (2880×1800 px) |
| `17-brief-rename-dialog-light-en.png` | Book Brief | rename dialog | light | en | 1440×900 pt (2880×1800 px) |
| `18-brief-remove-project-confirm-light-en.png` | Book Brief | remove project confirm | light | en | 1440×900 pt (2880×1800 px) |
| `19-brief-fill-in-from-book-working-light-en.png` | Book Brief | fill in from book working | light | en | 1440×900 pt (2880×1800 px) |
| `20-brief-fill-in-dialog-done-simple-light-en.png` | Book Brief | fill in dialog done simple | light | en | 1440×900 pt (2880×1800 px) |
| `21-brief-fill-in-dialog-done-detailed-light-en.png` | Book Brief | fill in dialog done detailed | light | en | 1440×900 pt (2880×1800 px) |
| `22-brief-fill-in-dialog-progress-fold-open-light-en.png` | Book Brief | fill in dialog progress fold open | light | en | 1440×900 pt (2880×1800 px) |
| `23-brief-top-filled-from-book-light-en.png` | Book Brief | top filled from book | light | en | 1440×900 pt (2880×1800 px) |
| `24-brief-middle-filled-from-book-light-en.png` | Book Brief | middle filled from book | light | en | 1440×900 pt (2880×1800 px) |
| `25-names-scanned-no-targets-top-light-en.png` | Names & style | scanned no targets top | light | en | 1440×900 pt (2880×1800 px) |
| `26-names-scanned-recurring-terms-bottom-light-en.png` | Names & style | scanned recurring terms bottom | light | en | 1440×900 pt (2880×1800 px) |
| `27-names-recurring-how-it-works-light-en.png` | Names & style | recurring how it works | light | en | 1440×900 pt (2880×1800 px) |
| `28-names-add-recurring-term-dialog-light-en.png` | Names & style | add recurring term dialog | light | en | 1440×900 pt (2880×1800 px) |
| `29-names-add-term-dialog-light-en.png` | Names & style | add term dialog | light | en | 1440×900 pt (2880×1800 px) |
| `30-names-prepare-automatically-running-early-light-en.png` | Names & style | prepare automatically running early | light | en | 1440×900 pt (2880×1800 px) |
| `31-names-prepare-automatically-running-mid-light-en.png` | Names & style | prepare automatically running mid | light | en | 1440×900 pt (2880×1800 px) |
| `32-names-prepare-automatically-running-detailed-light-en.png` | Names & style | prepare automatically running detailed | light | en | 1440×900 pt (2880×1800 px) |
| `33-names-pass-in-background-readonly-light-en.png` | Names & style | pass in background readonly | light | en | 1440×900 pt (2880×1800 px) |
| `35-names-prepare-results-changes-light-en.png` | Names & style | prepare results changes | light | en | 1440×900 pt (2880×1800 px) |
| `36-names-prepare-results-steps-light-en.png` | Names & style | prepare results steps | light | en | 1440×900 pt (2880×1800 px) |
| `37-names-filled-top-light-en.png` | Names & style | filled top | light | en | 1440×900 pt (2880×1800 px) |
| `38-names-filled-middle-light-en.png` | Names & style | filled middle | light | en | 1440×900 pt (2880×1800 px) |
| `39-names-filled-bottom-light-en.png` | Names & style | filled bottom | light | en | 1440×900 pt (2880×1800 px) |
| `40-translating-idle-light-en.png` | Translating | idle | light | en | 1440×900 pt (2880×1800 px) |
| `41-translating-idle-progress-fold-open-light-en.png` | Translating | idle progress fold open | light | en | 1440×900 pt (2880×1800 px) |
| `42-export-nothing-translated-top-light-en.png` | Export | nothing translated top | light | en | 1440×900 pt (2880×1800 px) |
| `43-export-nothing-translated-tooltip-hover-light-en.png` | Export | nothing translated tooltip hover | light | en | 1440×900 pt (2880×1800 px) |
| `44-start-question-meaning-names-light-en.png` | Start question (dialog) | meaning names | light | en | 1440×900 pt (2880×1800 px) |
| `45-start-question-genders-to-confirm-light-en.png` | Start question (dialog) | genders to confirm | light | en | 1440×900 pt (2880×1800 px) |
| `46-translating-run-just-started-light-en.png` | Translating | run just started | light | en | 1440×900 pt (2880×1800 px) |
| `47-translating-running-simple-light-en.png` | Translating | running simple | light | en | 1440×900 pt (2880×1800 px) |
| `48-translating-running-detailed-top-light-en.png` | Translating | running detailed top | light | en | 1440×900 pt (2880×1800 px) |
| `49-translating-running-detailed-scrolled-light-en.png` | Translating | running detailed scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `50-translating-running-activity-log-light-en.png` | Translating | running activity log | light | en | 1440×900 pt (2880×1800 px) |
| `51-translating-paused-activity-log-all-light-en.png` | Translating | paused activity log all | light | en | 1440×900 pt (2880×1800 px) |
| `52-translating-paused-activity-log-problems-only-light-en.png` | Translating | paused activity log problems only | light | en | 1440×900 pt (2880×1800 px) |
| `53-translating-paused-live-detailed-light-en.png` | Translating | paused live detailed | light | en | 1440×900 pt (2880×1800 px) |
| `54-translating-paused-detailed-prompt-and-reply-open-light-en.png` | Translating | paused detailed prompt and reply open | light | en | 1440×900 pt (2880×1800 px) |
| `55-translating-paused-detailed-prompt-and-reply-scrolled-light-en.png` | Translating | paused detailed prompt and reply scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `56-translating-all-calls-dialog-light-en.png` | Translating | all calls dialog | light | en | 1440×900 pt (2880×1800 px) |
| `57-translating-all-calls-dialog-detailed-light-en.png` | Translating | all calls dialog detailed | light | en | 1440×900 pt (2880×1800 px) |
| `58-review-desk-open-light-en.png` | Review desk (dialog) | open | light | en | 1440×900 pt (2880×1800 px) |
| `59-review-desk-what-the-model-saw-light-en.png` | Review desk (dialog) | what the model saw | light | en | 1440×900 pt (2880×1800 px) |
| `60-review-desk-suggestion-tab-light-en.png` | Review desk (dialog) | suggestion tab | light | en | 1440×900 pt (2880×1800 px) |
| `61-review-desk-technical-details-on-light-en.png` | Review desk (dialog) | technical details on | light | en | 1440×900 pt (2880×1800 px) |
| `62-review-desk-more-menu-light-en.png` | Review desk (dialog) | more menu | light | en | 1440×900 pt (2880×1800 px) |
| `63-review-desk-try-again-with-note-light-en.png` | Review desk (dialog) | try again with note | light | en | 1440×900 pt (2880×1800 px) |
| `64-review-desk-filter-dropdown-light-en.png` | Review desk (dialog) | filter dropdown | light | en | 1440×900 pt (2880×1800 px) |
| `65-review-desk-edited-save-my-edit-light-en.png` | Review desk (dialog) | edited save my edit | light | en | 1440×900 pt (2880×1800 px) |
| `66-review-desk-unsaved-edit-guard-light-en.png` | Review desk (dialog) | unsaved edit guard | light | en | 1440×900 pt (2880×1800 px) |
| `67-export-partial-book-paused-light-en.png` | Export | partial book paused | light | en | 1440×900 pt (2880×1800 px) |
| `68-names-while-run-paused-light-en.png` | Names & style | while run paused | light | en | 1440×900 pt (2880×1800 px) |
| `69-brief-while-run-paused-light-en.png` | Book Brief | while run paused | light | en | 1440×900 pt (2880×1800 px) |
| `70-projects-one-project-paused-light-en.png` | Projects | one project paused | light | en | 1440×900 pt (2880×1800 px) |
| `71-projects-row-menu-pressed-no-popup-light-en.png` | Projects | row menu pressed no popup | light | en | 1440×900 pt (2880×1800 px) |
| `72-projects-translate-into-dialog-light-en.png` | Projects | translate into dialog | light | en | 1440×900 pt (2880×1800 px) |
| `73-open-another-book-question-light-en.png` | Import book (dialog) | question | light | en | 1440×900 pt (2880×1800 px) |
| `74-translating-stop-question-light-en.png` | Translating | stop question | light | en | 1440×900 pt (2880×1800 px) |
| `75-translating-running-simple-later-light-en.png` | Translating | running simple later | light | en | 1440×900 pt (2880×1800 px) |
| `76-review-desk-auto-opened-to-review-light-en.png` | Review desk (dialog) | auto opened to review | light | en | 1440×900 pt (2880×1800 px) |
| `77-review-desk-to-review-what-the-model-saw-light-en.png` | Review desk (dialog) | to review what the model saw | light | en | 1440×900 pt (2880×1800 px) |
| `78-review-desk-to-review-suggestion-light-en.png` | Review desk (dialog) | to review suggestion | light | en | 1440×900 pt (2880×1800 px) |
| `79-review-desk-to-review-technical-details-light-en.png` | Review desk (dialog) | to review technical details | light | en | 1440×900 pt (2880×1800 px) |
| `80-translating-finished-top-light-en.png` | Translating | finished top | light | en | 1440×900 pt (2880×1800 px) |
| `81-translating-finished-scrolled-light-en.png` | Translating | finished scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `82-export-finished-top-light-en.png` | Export | finished top | light | en | 1440×900 pt (2880×1800 px) |
| `83-export-finished-scrolled-light-en.png` | Export | finished scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `85-export-complete-dialog-light-en.png` | Export | complete dialog | light | en | 1440×900 pt (2880×1800 px) |
| `86-export-after-export-top-light-en.png` | Export | after export top | light | en | 1440×900 pt (2880×1800 px) |
| `87-export-after-export-scrolled-light-en.png` | Export | after export scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `88-export-replace-existing-question-light-en.png` | Export | replace existing question | light | en | 1440×900 pt (2880×1800 px) |
| `89-settings-providers-top-light-en.png` | Settings › Providers | top | light | en | 1440×900 pt (2880×1800 px) |
| `90-settings-providers-connection-details-open-light-en.png` | Settings › Providers | connection details open | light | en | 1440×900 pt (2880×1800 px) |
| `91-settings-providers-connection-details-scrolled-light-en.png` | Settings › Providers | connection details scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `92-settings-providers-checks-bottom-light-en.png` | Settings › Providers | checks bottom | light | en | 1440×900 pt (2880×1800 px) |
| `93-settings-providers-checks-tested-light-en.png` | Settings › Providers | checks tested | light | en | 1440×900 pt (2880×1800 px) |
| `94-settings-provider-test-details-dialog-light-en.png` | Settings › Providers (test dialog) | test details dialog | light | en | 1440×900 pt (2880×1800 px) |
| `95-settings-provider-test-dialog-connection-light-en.png` | Settings › Providers (test dialog) | test dialog connection | light | en | 1440×900 pt (2880×1800 px) |
| `96-settings-provider-test-dialog-models-light-en.png` | Settings › Providers (test dialog) | test dialog models | light | en | 1440×900 pt (2880×1800 px) |
| `97-settings-providers-tested-ready-fold-open-light-en.png` | Settings › Providers | tested ready fold open | light | en | 1440×900 pt (2880×1800 px) |
| `98-settings-providers-form-edited-unsaved-light-en.png` | Settings › Providers | form edited unsaved | light | en | 1440×900 pt (2880×1800 px) |
| `99-settings-providers-leave-guard-light-en.png` | Settings › Providers | leave guard | light | en | 1440×900 pt (2880×1800 px) |
| `100-settings-providers-lm-studio-idle-light-en.png` | Settings › Providers | lm studio idle | light | en | 1440×900 pt (2880×1800 px) |
| `101-settings-providers-new-provider-light-en.png` | Settings › Providers | new provider | light | en | 1440×900 pt (2880×1800 px) |
| `102-settings-providers-new-provider-bearer-token-light-en.png` | Settings › Providers | new provider bearer token | light | en | 1440×900 pt (2880×1800 px) |
| `103-settings-providers-kind-dropdown-light-en.png` | Settings › Providers | kind dropdown | light | en | 1440×900 pt (2880×1800 px) |
| `104-settings-providers-delete-question-light-en.png` | Settings › Providers | delete question | light | en | 1440×900 pt (2880×1800 px) |
| `105-settings-providers-change-model-focuses-field-light-en.png` | Settings › Providers | change model focuses field | light | en | 1440×900 pt (2880×1800 px) |
| `106-settings-providers-model-dropdown-light-en.png` | Settings › Providers | model dropdown | light | en | 1440×900 pt (2880×1800 px) |
| `107-settings-providers-custom-headers-models-on-light-en.png` | Settings › Providers | custom headers models on | light | en | 1440×900 pt (2880×1800 px) |
| `108-settings-providers-custom-headers-models-on-scrolled-light-en.png` | Settings › Providers | custom headers models on scrolled | light | en | 1440×900 pt (2880×1800 px) |
| `109-settings-models-top-light-en.png` | Settings › Models | top | light | en | 1440×900 pt (2880×1800 px) |
| `110-settings-models-middle-light-en.png` | Settings › Models | middle | light | en | 1440×900 pt (2880×1800 px) |
| `111-settings-models-middle2-light-en.png` | Settings › Models | middle2 | light | en | 1440×900 pt (2880×1800 px) |
| `112-settings-models-bottom-light-en.png` | Settings › Models | bottom | light | en | 1440×900 pt (2880×1800 px) |
| `113-settings-models-chooser-open-light-en.png` | Settings › Models | chooser open | light | en | 1440×900 pt (2880×1800 px) |
| `114-settings-generation-light-en.png` | Settings › Generation | default | light | en | 1440×900 pt (2880×1800 px) |
| `115-settings-models-advanced-open-bottom-light-en.png` | Settings › Models | advanced open bottom | light | en | 1440×900 pt (2880×1800 px) |
| `116-about-dialog-light-en.png` | About (dialog) | dialog | light | en | 1440×900 pt (2880×1800 px) |
| `117-settings-appearance-light-en.png` | Settings › Appearance | default | light | en | 1440×900 pt (2880×1800 px) |
| `117b-settings-storage-delete-all-data-question-light-en.png` | Settings › Storage & logs | delete all data question | light | en | 1440×900 pt (2880×1800 px) |
| `117c-settings-appearance-language-dropdown-light-en.png` | Settings › Appearance | language dropdown | light | en | 1440×900 pt (2880×1800 px) |
| `117d-settings-providers-use-this-one-question-light-en.png` | Settings › Providers | use this one question | light | en | 1440×900 pt (2880×1800 px) |
| `118-settings-automation-light-en.png` | Settings › Automation | default | light | en | 1440×900 pt (2880×1800 px) |
| `120-settings-storage-logs-light-en.png` | Settings › Storage & logs | logs | light | en | 1440×900 pt (2880×1800 px) |
| `122-projects-one-project-finished-light-en.png` | Projects | one project finished | light | en | 1440×900 pt (2880×1800 px) |
| `123-import-markdown-book-open-light-en.png` | Import book | markdown book open | light | en | 1440×900 pt (2880×1800 px) |
| `124-brief-second-project-languages-locked-top-light-en.png` | Book Brief | second project languages locked top | light | en | 1440×900 pt (2880×1800 px) |
| `125-brief-second-project-languages-locked-middle-light-en.png` | Book Brief | second project languages locked middle | light | en | 1440×900 pt (2880×1800 px) |
| `126-brief-second-project-languages-locked-bottom-light-en.png` | Book Brief | second project languages locked bottom | light | en | 1440×900 pt (2880×1800 px) |
| `127-projects-two-books-light-en.png` | Projects | two books | light | en | 1440×900 pt (2880×1800 px) |
| `128-projects-translate-into-dialog-filled-light-en.png` | Projects | translate into dialog filled | light | en | 1440×900 pt (2880×1800 px) |
| `129-brief-new-de-project-welcome-back-banner-light-en.png` | Book Brief | new de project welcome back banner | light | en | 1440×900 pt (2880×1800 px) |
| `130-translating-idle-de-project-light-en.png` | Translating | idle de project | light | en | 1440×900 pt (2880×1800 px) |
| `131-translating-stopped-de-project-light-en.png` | Translating | stopped de project | light | en | 1440×900 pt (2880×1800 px) |
| `132-projects-three-projects-light-en.png` | Projects | three projects | light | en | 1440×900 pt (2880×1800 px) |
| `133-projects-filter-in-progress-light-en.png` | Projects | filter in progress | light | en | 1440×900 pt (2880×1800 px) |
| `140-projects-dark-en.png` | Projects | default | dark | en | 1440×900 pt (2880×1800 px) |
| `141-import-book-open-top-dark-en.png` | Import book | book open top | dark | en | 1440×900 pt (2880×1800 px) |
| `142-import-book-open-scrolled-dark-en.png` | Import book | book open scrolled | dark | en | 1440×900 pt (2880×1800 px) |
| `143-brief-top-dark-en.png` | Book Brief | top | dark | en | 1440×900 pt (2880×1800 px) |
| `144-brief-bottom-dark-en.png` | Book Brief | bottom | dark | en | 1440×900 pt (2880×1800 px) |
| `145-names-top-dark-en.png` | Names & style | top | dark | en | 1440×900 pt (2880×1800 px) |
| `146-names-bottom-dark-en.png` | Names & style | bottom | dark | en | 1440×900 pt (2880×1800 px) |
| `147-translating-top-dark-en.png` | Translating | top | dark | en | 1440×900 pt (2880×1800 px) |
| `149-export-top-dark-en.png` | Export | top | dark | en | 1440×900 pt (2880×1800 px) |
| `150-export-bottom-dark-en.png` | Export | bottom | dark | en | 1440×900 pt (2880×1800 px) |
| `151-settings-providers-top-dark-en.png` | Settings › Providers | top | dark | en | 1440×900 pt (2880×1800 px) |
| `152-settings-providers-connection-details-open-dark-en.png` | Settings › Providers | connection details open | dark | en | 1440×900 pt (2880×1800 px) |
| `153-settings-providers-checks-bottom-dark-en.png` | Settings › Providers | checks bottom | dark | en | 1440×900 pt (2880×1800 px) |
| `154-settings-models-top-dark-en.png` | Settings › Models | top | dark | en | 1440×900 pt (2880×1800 px) |
| `155-settings-models-middle-dark-en.png` | Settings › Models | middle | dark | en | 1440×900 pt (2880×1800 px) |
| `156-settings-models-bottom-dark-en.png` | Settings › Models | bottom | dark | en | 1440×900 pt (2880×1800 px) |
| `157-settings-generation-dark-en.png` | Settings › Generation | default | dark | en | 1440×900 pt (2880×1800 px) |
| `158-settings-appearance-dark-en.png` | Settings › Appearance | default | dark | en | 1440×900 pt (2880×1800 px) |
| `159-settings-automation-dark-en.png` | Settings › Automation | default | dark | en | 1440×900 pt (2880×1800 px) |
| `160-settings-storage-logs-dark-en.png` | Settings › Storage & logs | logs | dark | en | 1440×900 pt (2880×1800 px) |
| `161-review-desk-dark-en.png` | Review desk (dialog) | default | dark | en | 1440×900 pt (2880×1800 px) |
| `162-review-desk-what-the-model-saw-dark-en.png` | Review desk (dialog) | what the model saw | dark | en | 1440×900 pt (2880×1800 px) |
| `163-about-dialog-dark-en.png` | About (dialog) | dialog | dark | en | 1440×900 pt (2880×1800 px) |
| `170-brief-top-narrow1000-light-en.png` | Book Brief | top | light | en | 1000×900 pt (2000×1800 px) |
| `171-brief-bottom-narrow1000-light-en.png` | Book Brief | bottom | light | en | 1000×900 pt (2000×1800 px) |
| `172-names-top-narrow1000-light-en.png` | Names & style | top | light | en | 1000×900 pt (2000×1800 px) |
| `173-names-bottom-narrow1000-light-en.png` | Names & style | bottom | light | en | 1000×900 pt (2000×1800 px) |
| `174-translating-narrow1000-light-en.png` | Translating | default | light | en | 1000×900 pt (2000×1800 px) |
| `175-settings-providers-narrow1000-light-en.png` | Settings › Providers | default | light | en | 1000×900 pt (2000×1800 px) |
| `176-settings-appearance-language-changed-restart-notice-light-en.png` | Settings › Appearance | language changed restart notice | light | en | 1440×900 pt (2880×1800 px) |
| `180-projects-light-uk.png` | Projects | default | light | uk | 1440×900 pt (2880×1800 px) |
| `181-import-book-open-top-light-uk.png` | Import book | book open top | light | uk | 1440×900 pt (2880×1800 px) |
| `182-import-book-open-scrolled-light-uk.png` | Import book | book open scrolled | light | uk | 1440×900 pt (2880×1800 px) |
| `183-brief-top-light-uk.png` | Book Brief | top | light | uk | 1440×900 pt (2880×1800 px) |
| `184-brief-bottom-light-uk.png` | Book Brief | bottom | light | uk | 1440×900 pt (2880×1800 px) |
| `185-names-top-light-uk.png` | Names & style | top | light | uk | 1440×900 pt (2880×1800 px) |
| `186-names-bottom-light-uk.png` | Names & style | bottom | light | uk | 1440×900 pt (2880×1800 px) |
| `187-translating-top-light-uk.png` | Translating | top | light | uk | 1440×900 pt (2880×1800 px) |
| `189-export-top-light-uk.png` | Export | top | light | uk | 1440×900 pt (2880×1800 px) |
| `190-export-bottom-light-uk.png` | Export | bottom | light | uk | 1440×900 pt (2880×1800 px) |
| `191-settings-providers-top-light-uk.png` | Settings › Providers | top | light | uk | 1440×900 pt (2880×1800 px) |
| `192-settings-providers-connection-details-open-light-uk.png` | Settings › Providers | connection details open | light | uk | 1440×900 pt (2880×1800 px) |
| `193-settings-providers-checks-bottom-light-uk.png` | Settings › Providers | checks bottom | light | uk | 1440×900 pt (2880×1800 px) |
| `194-settings-models-top-light-uk.png` | Settings › Models | top | light | uk | 1440×900 pt (2880×1800 px) |
| `195-settings-models-middle-light-uk.png` | Settings › Models | middle | light | uk | 1440×900 pt (2880×1800 px) |
| `196-settings-models-bottom-light-uk.png` | Settings › Models | bottom | light | uk | 1440×900 pt (2880×1800 px) |
| `197-settings-generation-light-uk.png` | Settings › Generation | default | light | uk | 1440×900 pt (2880×1800 px) |
| `198-settings-appearance-light-uk.png` | Settings › Appearance | default | light | uk | 1440×900 pt (2880×1800 px) |
| `199-settings-automation-light-uk.png` | Settings › Automation | default | light | uk | 1440×900 pt (2880×1800 px) |
| `200-settings-storage-logs-light-uk.png` | Settings › Storage & logs | logs | light | uk | 1440×900 pt (2880×1800 px) |
| `201-review-desk-light-uk.png` | Review desk (dialog) | default | light | uk | 1440×900 pt (2880×1800 px) |
| `202-review-desk-more-menu-light-uk.png` | Review desk (dialog) | more menu | light | uk | 1440×900 pt (2880×1800 px) |
| `203-about-dialog-light-uk.png` | About (dialog) | dialog | light | uk | 1440×900 pt (2880×1800 px) |
| `204-start-question-genders-to-confirm-light-uk.png` | Start question (dialog) | genders to confirm | light | uk | 1440×900 pt (2880×1800 px) |
| `205-translating-running-proofread-light-uk.png` | Translating | running proofread | light | uk | 1440×900 pt (2880×1800 px) |
| `206-translating-finished-live-light-uk.png` | Translating | finished live | light | uk | 1440×900 pt (2880×1800 px) |
| `207-translating-finished-activity-log-light-uk.png` | Translating | finished activity log | light | uk | 1440×900 pt (2880×1800 px) |
| `208-export-finished-all-done-light-uk.png` | Export | finished all done | light | uk | 1440×900 pt (2880×1800 px) |
| `209-quit-without-export-question-light-uk.png` | Quit question (dialog) | without export question | light | uk | 1440×900 pt (2880×1800 px) |

## Not captured, and why

- **Projects row menu (⋯: Rename, Show the book file, Remove):** the menu never opened — see "What broke" 1. So the Projects-side Rename… dialog and the Remove question from that menu are missing; the same Rename… and Remove dialogs are captured from the Book Brief (17, 18).
- **"Who narrates?" Start question:** did not appear; the fixture book is not a first-person narration and Fill in from the book had already set "He / she". The two other Start questions are captured (44 meaning names, 45 and 204 character genders).
- **Empty Names & style (no glossary rows):** not reachable — opening the screen scanned the book by itself, so the first sight already had rows (25, 26: scanned, no translations yet).
- **Provider-error / stuck-call banners, Skip segment, Retry now, error dialog:** none appeared naturally; the local model answered every call.
- **Export last-check dialog ("Write the file now", Cancel that asks):** the check of this 164-segment book finished before a capture was possible; only Export complete (85) and Replace 3 existing files? (88) are captured.
- **Native file choosers** (Browse files…, Change…, Import/Export CSV, Make a backup…, Save diagnostic bundle…): operating-system panels, not app UI; not captured. Make a backup and Save diagnostic bundle were not run.
- **Advanced features test, "Test with details"** on Settings › Providers: not run (extra model calls); the test dialog is captured from the inference test (94–96).
- **Destructive confirmations were opened and cancelled only:** Remove project (18), Delete provider (104), Delete all data (117b), Use LM Studio for the rest of this book (117d), Replace and delete this translation (button visible in 73).
- **Dark and Ukrainian sets** cover the main screens, the review desk and About only; the other dialogs exist in light English only. Ukrainian "Translating" during a draft run is not captured (205 is the short proofread continuation; 206–207 the finished state).
- **Narrow window:** 1000 pt wide, Book Brief, Names & style, Translating (finished-project view) and Settings › Providers; no dialogs.

## What visibly broke while driving the app (exact steps)

1. **Projects → the "⋯" button of a project row shows no menu.** Projects screen with one to three projects; click the "⋯" button at the right end of any project row (tried rows 1 and 3, a normal click, a slow press, and Space on the focused button). The button takes its hover/pressed look and nothing else appears (71). The clicks were synthetic (CGEvent), and every other popup opened the same way (combo boxes, the review desk's More ▾), so check this one by hand before calling it a defect.
2. **A second book imported as a new project cannot get a target language on the Book Brief.** With the EN → UK project open and finished: Projects → + New translation → Browse files… → `earth-gravity.md` → "Open another book?" → Open as a new project → Book Brief. "Translate into" is empty and disabled (greyed like "Translate from") while the line under it says "Required: choose the language to translate into", the toolbar shows "Choose the languages in Book Brief" with Continue disabled, and a "Cannot start yet — no target language is chosen" banner appears on every screen (123–127). Typing into the field does nothing. The only way forward was Projects → Translate into another language, which makes one more project (128, 129); the language-less project "Earth Gravity · EN" stays in the list (132). On the very first project of the session the same field was enabled (11–13).
3. **The new EN → DE project said "Welcome back — 3 of 143 segments are done" and "Ready · 2 % done" before any run** (129, 130) — right after "Create and open the brief".
4. **"Change model" on the provider summary card** opens the Connection details fold and scrolls to the model field, with every check reset to "Not tested yet" (105) — recorded as seen, not judged.
5. Nothing crashed; no error dialog or error toast appeared in about 50 minutes of driving. "Restart now" (Settings › Appearance) restarted the app by itself in the new language at the same window size.

## End state

- App quit (it asked "Quit without export?" — 209); no `bookloom`/`gradlew` process left. Theme and language in the scratch data were put back to "Match the computer". No tracked file of the repository changed.
- `ollama ps` at the end: `gemma4:e4b-mlx` loaded (15 GB, loaded by this session's runs through the app, unloads by itself after its keep-alive). `lms ps`: no models loaded.
- Scratch data, exported book and helper scripts: session scratchpad `uicap/` (temporary).
