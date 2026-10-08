package ua.bookloom.ui.i18n;

/**
 * Every key the interface displays, so a view names a constant instead of spelling a bundle key. A catalogue entry
 * with no constant is dead text and a constant with no entry would display as its raw key; the registry test fails on
 * both.
 *
 * <p>The file is longer than 400 lines because a registry has one entry per key, and splitting it would give
 * {@link Messages#get} several key types.
 */
public enum MessageKey {
    /** Heading of the navigation group holding the numbered workflow steps. */
    NAV_GROUP_WORKFLOW("nav.group.workflow"),
    /** Heading of the navigation group holding the application-level screens. */
    NAV_GROUP_APPLICATION("nav.group.application"),
    /** Navigation entry and breadcrumb of the projects screen. */
    NAV_PROJECTS("nav.projects"),
    /** Hover explanation of the control labelled by {@link #NAV_PROJECTS}. */
    NAV_PROJECTS_TIP("nav.projects.tip"),
    /** Navigation entry and breadcrumb of the import screen. */
    NAV_IMPORT("nav.import"),
    /** Hover explanation of the control labelled by {@link #NAV_IMPORT}. */
    NAV_IMPORT_TIP("nav.import.tip"),
    /** Navigation entry and breadcrumb of the book brief screen. */
    NAV_BRIEF("nav.brief"),
    /** Hover explanation of the control labelled by {@link #NAV_BRIEF}. */
    NAV_BRIEF_TIP("nav.brief.tip"),
    /** Navigation entry and breadcrumb of the structure screen. */
    NAV_STRUCTURE("nav.structure"),
    /** Hover explanation of the control labelled by {@link #NAV_STRUCTURE}. */
    NAV_STRUCTURE_TIP("nav.structure.tip"),
    /** Navigation entry and breadcrumb of the names-and-style screen. */
    NAV_NAMES_AND_STYLE("nav.namesAndStyle"),
    /** Hover explanation of the control labelled by {@link #NAV_NAMES_AND_STYLE}. */
    NAV_NAMES_AND_STYLE_TIP("nav.namesAndStyle.tip"),
    /** Navigation entry and breadcrumb of the translating dashboard. */
    NAV_TRANSLATING("nav.translating"),
    /** Hover explanation of the control labelled by {@link #NAV_TRANSLATING}. */
    NAV_TRANSLATING_TIP("nav.translating.tip"),
    /** Navigation entry and breadcrumb of the export screen. */
    NAV_EXPORT("nav.export"),
    /** Hover explanation of the control labelled by {@link #NAV_EXPORT}. */
    NAV_EXPORT_TIP("nav.export.tip"),
    /** Navigation entry and breadcrumb of the settings screen. */
    NAV_SETTINGS("nav.settings"),
    /** Hover explanation of the control labelled by {@link #NAV_SETTINGS}. */
    NAV_SETTINGS_TIP("nav.settings.tip"),
    /** Why a workflow step cannot be opened yet: no book is open. Also the hover text of the locked entry and Continue. */
    NAV_LOCKED_NO_BOOK("nav.locked.noBook"),
    /** Why a workflow step cannot be opened yet: the languages are unset or identical. */
    NAV_LOCKED_NO_LANGUAGES("nav.locked.noLanguages"),
    /** The navigation footer naming the chosen provider and model: provider, model. */
    NAV_FOOTER_SELECTION("nav.footer.selection"),
    /** The navigation footer while no model is chosen. */
    NAV_FOOTER_NO_MODEL("nav.footer.noModel"),
    /** The product name in the title bar; the same in every language. */
    SHELL_TITLE("shell.title"),
    /** Title-bar control label offered while the light block is in force. */
    SHELL_THEME_TO_DARK("shell.themeToDark"),
    /** Hover explanation of the control labelled by {@link #SHELL_THEME_TO_DARK}. */
    SHELL_THEME_TO_DARK_TIP("shell.themeToDark.tip"),
    /** Title-bar control label offered while the dark block is in force. */
    SHELL_THEME_TO_LIGHT("shell.themeToLight"),
    /** Hover explanation of the control labelled by {@link #SHELL_THEME_TO_LIGHT}. */
    SHELL_THEME_TO_LIGHT_TIP("shell.themeToLight.tip"),
    /** Title-bar action that opens the About dialog. */
    SHELL_ABOUT("shell.about"),
    /** Hover explanation of the control labelled by {@link #SHELL_ABOUT}. */
    SHELL_ABOUT_TIP("shell.about.tip"),
    /** Name of a translation run as model work that rules out other model work. */
    ACTIVITY_TRANSLATION("activity.translation"),
    /** Name of the glossary's model scan as running model work. */
    ACTIVITY_GLOSSARY_SCAN("activity.glossaryScan"),
    /** Name of the glossary's model review as running model work. */
    ACTIVITY_GLOSSARY_REVIEW("activity.glossaryReview"),
    /** Name of a review-panel segment retry as running model work. */
    ACTIVITY_REVIEW_RETRY("activity.reviewRetry"),
    /** Name of the file name proposal as running model work. */
    ACTIVITY_SUGGEST_NAME("activity.suggestName"),
    /** Name of the Book Brief style proposal as running model work. */
    ACTIVITY_SUGGEST_STYLE("activity.suggestStyle"),
    /** Name of the provider's inference test as running model work. */
    ACTIVITY_PROVIDER_INFERENCE_TEST("activity.providerInferenceTest"),
    /** Name of the provider's connection or model-list test as running work. */
    ACTIVITY_PROVIDER_CHECK("activity.providerCheck"),
    /** Name of reading the provider's model list as running work. */
    ACTIVITY_MODEL_LISTING("activity.modelListing"),
    /** Name of writing the translated book as running work. */
    ACTIVITY_EXPORT("activity.export"),
    /** Name of opening and inspecting a book as running work. */
    ACTIVITY_IMPORT("activity.import"),
    /** Name of preparing a run (style sheet, name scan, chunking) as running work. */
    ACTIVITY_RUN_PREPARATION("activity.runPreparation"),
    /** The title-bar chip naming running model work; arguments: 0 its name, 1 requests sent so far, 2 how many other works also run. */
    ACTIVITY_CHIP("activity.chip"),
    /** Hover explanation of the title-bar chip named by {@link #ACTIVITY_CHIP}. */
    ACTIVITY_CHIP_TIP("activity.chip.tip"),
    /** Title-bar control that stops the model work the chip names. */
    ACTIVITY_STOP("activity.stop"),
    /** Hover explanation of the control labelled by {@link #ACTIVITY_STOP}. */
    ACTIVITY_STOP_TIP("activity.stop.tip"),
    /** Why a control that would ask the model is unavailable; argument 0 is the name of the work running. */
    ACTIVITY_BLOCKED("activity.blocked"),
    /** Busy-card step: the export checks the destination and the project. */
    EXPORT_PROGRESS_VALIDATING("export.progress.validating"),
    /** Busy-card step: the consistency pass re-renders segments whose character's gender became known. */
    EXPORT_PROGRESS_RETRY("export.progress.retry"),
    /** Busy-card step: the consistency pass drafts flagged and doubted segments again, keeping only a better text. */
    EXPORT_PROGRESS_RETRY_DOUBTED("export.progress.retryDoubted"),
    /** Busy-card step: the consistency pass checks repaired paragraphs against their neighbours. */
    EXPORT_PROGRESS_NEIGHBOUR("export.progress.neighbour"),
    /** Busy-card step: the export writes the book and its side files. */
    EXPORT_PROGRESS_WRITING("export.progress.writing"),
    /** Busy-card detail label: the file being worked on. */
    ACTIVITY_DETAIL_FILE("activity.detail.file"),
    /** Busy-card detail label: the model being asked. */
    ACTIVITY_DETAIL_MODEL("activity.detail.model"),
    /** Busy-card detail label: how many model requests were sent. */
    ACTIVITY_DETAIL_REQUESTS("activity.detail.requests"),
    /** Busy-card detail label: how many segments of a step counted by segment are done. */
    ACTIVITY_DETAIL_SEGMENTS("activity.detail.segments"),
    /** Busy-card detail value: a count out of a total; arguments: 0 done, 1 total. */
    ACTIVITY_DETAIL_OF("activity.detail.of"),
    /** Busy card: time since the work began; argument 0 is a clock such as 1:15. */
    BUSY_ELAPSED("busy.elapsed"),
    /** Busy card: the estimated time left; argument 0 is a duration such as 2m. */
    BUSY_ETA("busy.eta"),
    /** Busy card: label of the button that stops the work. */
    BUSY_CANCEL("busy.cancel"),
    /** Hover explanation of the busy card's Cancel button. */
    BUSY_CANCEL_TIP("busy.cancel.tip"),
    /** Busy card: what the Cancel button and the step line say once a stop was asked for. */
    BUSY_CANCELLING("busy.cancelling"),
    /** Hover explanation of the disabled Cancel button while the work stops. */
    BUSY_CANCELLING_TIP("busy.cancelling.tip"),
    /** Busy card: shown instead of a Cancel button for work that cannot be stopped. */
    BUSY_NOT_CANCELLABLE("busy.notCancellable"),
    /** Busy card: heading of the folded section that shows the work's current model call. */
    BUSY_CALLS("busy.calls"),
    /** Hover explanation of the busy card's model-call section. */
    BUSY_CALLS_TIP("busy.calls.tip"),
    /** Title of the question asked before the run is stopped. */
    CONFIRM_STOP_TITLE("confirm.stop.title"),
    /** Body of the stop-the-run question. */
    CONFIRM_STOP_TEXT("confirm.stop.text"),
    /** Confirming button of the stop-the-run question. */
    CONFIRM_STOP_YES("confirm.stop.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_STOP_YES}. */
    CONFIRM_STOP_YES_TIP("confirm.stop.yes.tip"),
    /** Title of the question asked before the person's edit is discarded. */
    CONFIRM_REVERT_TITLE("confirm.revert.title"),
    /** Body of the discard-your-edit question. */
    CONFIRM_REVERT_TEXT("confirm.revert.text"),
    /** Confirming button of the discard-your-edit question. */
    CONFIRM_REVERT_YES("confirm.revert.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_REVERT_YES}. */
    CONFIRM_REVERT_YES_TIP("confirm.revert.yes.tip"),
    /** Title of the question asked before a project with progress is discarded. */
    CONFIRM_IMPORT_TITLE("confirm.import.title"),
    /** Body of the discard-this-project question. */
    CONFIRM_IMPORT_TEXT("confirm.import.text"),
    /** Confirming button of the discard-this-project question. */
    CONFIRM_IMPORT_YES("confirm.import.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_IMPORT_YES}. */
    CONFIRM_IMPORT_YES_TIP("confirm.import.yes.tip"),
    /** Title of the question asked before the window closes while work runs. */
    CONFIRM_QUIT_TITLE("confirm.quit.title"),
    /** Body of the quit-while-working question. */
    CONFIRM_QUIT_TEXT("confirm.quit.text"),
    /** Confirming button of the quit-while-working question. */
    CONFIRM_QUIT_YES("confirm.quit.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_QUIT_YES}. */
    CONFIRM_QUIT_YES_TIP("confirm.quit.yes.tip"),
    /** Title of the question asked before the window closes on a translation that was never exported. */
    CONFIRM_QUIT_UNEXPORTED_TITLE("confirm.quitUnexported.title"),
    /** Body of the quit-without-exporting question. */
    CONFIRM_QUIT_UNEXPORTED_TEXT("confirm.quitUnexported.text"),
    /** Confirming button of the quit-without-exporting question. */
    CONFIRM_QUIT_UNEXPORTED_YES("confirm.quitUnexported.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_QUIT_UNEXPORTED_YES}. */
    CONFIRM_QUIT_UNEXPORTED_YES_TIP("confirm.quitUnexported.yes.tip"),
    /** About dialog subtitle; argument 0 is the build version. */
    ABOUT_SUBTITLE("about.subtitle"),
    /** About dialog description of the product. */
    ABOUT_DESCRIPTION("about.description"),
    /** Heading of the About dialog's diagnostic-log section. */
    ABOUT_LOG_HEADING("about.log.heading"),
    /** The detailed diagnostic log is being written in this launch. */
    ABOUT_LOG_ON("about.log.on"),
    /** The detailed diagnostic log is not written in this launch, and how to switch it on. */
    ABOUT_LOG_OFF("about.log.off"),
    /** What the detailed log holds and that it stays on this computer. */
    ABOUT_LOG_PRIVACY("about.log.privacy"),
    /** The log folder; argument 0 is its absolute path. */
    ABOUT_LOG_FOLDER("about.log.folder"),
    /** About dialog action that opens the log folder in the file manager. */
    ABOUT_OPEN_LOG_FOLDER("about.log.openFolder"),
    /** Hover explanation of the control labelled by {@link #ABOUT_OPEN_LOG_FOLDER}. */
    ABOUT_OPEN_LOG_FOLDER_TIP("about.log.openFolder.tip"),
    /** About dialog action that copies the log folder's path. */
    ABOUT_COPY_LOG_PATH("about.log.copyPath"),
    /** Hover explanation of the control labelled by {@link #ABOUT_COPY_LOG_PATH}. */
    ABOUT_COPY_LOG_PATH_TIP("about.log.copyPath.tip"),
    /** About dialog action that saves the diagnostic bundle. */
    ABOUT_SAVE_BUNDLE("about.log.saveBundle"),
    /** Hover explanation of the control labelled by {@link #ABOUT_SAVE_BUNDLE}. */
    ABOUT_SAVE_BUNDLE_TIP("about.log.saveBundle.tip"),
    /** Toast after the log folder's path was copied. */
    ABOUT_LOG_PATH_COPIED("about.log.pathCopied"),
    /** Toast after the diagnostic bundle was written; argument 0 is the bundle's file name. */
    ABOUT_BUNDLE_SAVED("about.log.bundleSaved"),
    /** Tagline under the product name in the navigation column's brand block. */
    SHELL_TAGLINE("shell.tagline"),
    /** Tail of a workflow breadcrumb; argument 0 is the step number and argument 1 the number of workflow steps. */
    SHELL_BREADCRUMB_STEP("shell.breadcrumb.step"),
    /** State text while running; argument 0 is the whole-number percentage. */
    SHELL_RUN_PROGRESS("shell.run.progress"),
    /** State text while paused; argument 0 is the whole-number percentage. */
    SHELL_RUN_PAUSED("shell.run.paused"),
    /** State text once stopped; argument 0 is the whole-number percentage. */
    SHELL_RUN_STOPPED("shell.run.stopped"),
    /** State text while paused on an error, whatever its code. */
    SHELL_RUN_PROVIDER_ERROR("shell.run.providerError"),
    /** State text while the run waits for the provider by itself; argument 0 is the time to the next try as {@code m:ss}. */
    SHELL_RUN_WAITING_PROVIDER("shell.run.waitingProvider"),
    /** State text once every segment is decided. */
    SHELL_RUN_FINISHED("shell.run.finished"),
    /** State text once the run ended in failure; argument 0 is the whole-number percentage. */
    SHELL_RUN_FAILED("shell.run.failed"),
    /** Title-bar elapsed time; argument 0 is the formatted duration. */
    SHELL_RUN_ELAPSED("shell.run.elapsed"),
    /** Title-bar time left; argument 0 is the formatted duration. */
    SHELL_RUN_LEFT("shell.run.left"),
    /** What the names.translate option does, shown under its choice and on hover. */
    BRIEF_HELP_NAMES_TRANSLATE("brief.help.names.translate"),
    /** What the names.transliterate option does, shown under its choice and on hover. */
    BRIEF_HELP_NAMES_TRANSLITERATE("brief.help.names.transliterate"),
    /** What the names.keepOriginal option does, shown under its choice and on hover. */
    BRIEF_HELP_NAMES_KEEP_ORIGINAL("brief.help.names.keepOriginal"),
    /** What the foreign.keep option does, shown under its choice and on hover. */
    BRIEF_HELP_FOREIGN_KEEP("brief.help.foreign.keep"),
    /** What the foreign.translate option does, shown under its choice and on hover. */
    BRIEF_HELP_FOREIGN_TRANSLATE("brief.help.foreign.translate"),
    /** What the foreign.translateNote option does, shown under its choice and on hover. */
    BRIEF_HELP_FOREIGN_TRANSLATE_NOTE("brief.help.foreign.translateNote"),
    /** What the footnotes.translate option does, shown under its choice and on hover. */
    BRIEF_HELP_FOOTNOTES_TRANSLATE("brief.help.footnotes.translate"),
    /** What the footnotes.keep option does, shown under its choice and on hover. */
    BRIEF_HELP_FOOTNOTES_KEEP("brief.help.footnotes.keep"),
    /** What the units.keep option does, shown under its choice and on hover. */
    BRIEF_HELP_UNITS_KEEP("brief.help.units.keep"),
    /** What the units.metric option does, shown under its choice and on hover. */
    BRIEF_HELP_UNITS_METRIC("brief.help.units.metric"),
    /** What the register.formal option does, shown under its choice and on hover. */
    BRIEF_HELP_REGISTER_FORMAL("brief.help.register.formal"),
    /** What the register.neutral option does, shown under its choice and on hover. */
    BRIEF_HELP_REGISTER_NEUTRAL("brief.help.register.neutral"),
    /** What the register.casual option does, shown under its choice and on hover. */
    BRIEF_HELP_REGISTER_CASUAL("brief.help.register.casual"),
    /** What the narrator.unspecified option does, shown under its choice and on hover. */
    BRIEF_HELP_NARRATOR_UNSPECIFIED("brief.help.narrator.unspecified"),
    /** What the narrator.first option does, shown under its choice and on hover. */
    BRIEF_HELP_NARRATOR_FIRST("brief.help.narrator.first"),
    /** What the narrator.third option does, shown under its choice and on hover. */
    BRIEF_HELP_NARRATOR_THIRD("brief.help.narrator.third"),
    /** What the gender.unknown option does, shown under its choice and on hover. */
    BRIEF_HELP_GENDER_UNKNOWN("brief.help.gender.unknown"),
    /** What the gender.male option does, shown under its choice and on hover. */
    BRIEF_HELP_GENDER_MALE("brief.help.gender.male"),
    /** What the gender.female option does, shown under its choice and on hover. */
    BRIEF_HELP_GENDER_FEMALE("brief.help.gender.female"),
    /** Title-bar average generation speed; argument 0 is the rounded tokens per second, with a leading ~ if estimated. */
    SHELL_RUN_RATE("shell.run.rate"),
    /** Hover explanation of the text shown by {@link #SHELL_RUN_RATE}. */
    SHELL_RUN_RATE_TIP("shell.run.rate.tip"),
    /** Title-bar dial and review mode of the run; argument 0 is the dial's name, argument 1 the review mode's. */
    SHELL_RUN_MODE("shell.run.mode"),
    /** Hover explanation of the text shown by {@link #SHELL_RUN_MODE}. */
    SHELL_RUN_MODE_TIP("shell.run.mode.tip"),
    /** Title-bar run control that pauses the run. */
    SHELL_RUN_PAUSE("shell.run.pause"),
    /** Hover explanation of the control labelled by {@link #SHELL_RUN_PAUSE}. */
    SHELL_RUN_PAUSE_TIP("shell.run.pause.tip"),
    /** Title-bar run control that resumes the run. */
    SHELL_RUN_RESUME("shell.run.resume"),
    /** Hover explanation of the control labelled by {@link #SHELL_RUN_RESUME}. */
    SHELL_RUN_RESUME_TIP("shell.run.resume.tip"),
    /** A duration of an hour or more; argument 0 is the hours, argument 1 the two-digit minutes, both Strings. */
    DURATION_HM("duration.hm"),
    /** A duration of a minute or more, under an hour; argument 0 is the minutes, a String. */
    DURATION_M("duration.m"),
    /** A duration under a minute; argument 0 is the seconds, a String. */
    DURATION_S("duration.s"),
    /** Label of a button that dismisses a dialog. */
    COMMON_CLOSE("common.close"),
    /** Hover explanation of the control labelled by {@link #COMMON_CLOSE}. */
    COMMON_CLOSE_TIP("common.close.tip"),
    /** Label of a dialog button that abandons the question and changes nothing. */
    COMMON_CANCEL("common.cancel"),
    /** Hover explanation of the control labelled by {@link #COMMON_CANCEL}. */
    COMMON_CANCEL_TIP("common.cancel.tip"),
    /** Activity-log entry for an accepted segment; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_ACCEPTED("log.accepted"),
    /** Activity-log entry for an answered model-call attempt; arguments: 0 the call's name, 1 the locator part (empty or {@code " · ch7 · p42"}), 2 the repair round or {@code 0}, 3 the attempt, 4 the time it took as {@code m:ss}; all Strings. */
    LOG_MODEL_CALL("log.modelCall"),
    /** Activity-log entry for a failed model-call attempt; arguments as {@link #LOG_MODEL_CALL}, and 5 the error code; all Strings. */
    LOG_CALL_FAILED("log.callFailed"),
    /** Activity-log line for a model call the person's pause or stop interrupted; arguments as {@link #LOG_CALL_FAILED} without the error code. */
    LOG_CALL_PAUSED("log.callPaused"),
    /** Activity-log entry for a segment entering a repair round; arguments: 0 the locator, 1 the round, 2 the rounds allowed, 3 the finding it repairs or {@code none}; all Strings. */
    LOG_ROUND("log.round"),
    /** The name of a model call's kind; argument 0 is the kind's lower-case token, such as {@code review} or {@code directed_fix}. */
    RUN_CALL_KIND("run.callKind"),
    /** Activity-log entry for an applied glossary; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_GLOSSARY_APPLIED("log.glossaryApplied"),
    /** Activity-log entry for an updated rolling summary; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_SUMMARY_UPDATED("log.summaryUpdated"),
    /** Activity-log entry for a retried segment; argument 0 is the segment id, passed as a String so it is never grouped like a number, argument 1 the attempt number. */
    LOG_RETRIED("log.retried"),
    /** Activity-log entry for a recoverable segment error; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_SEGMENT_ERROR("log.segmentError"),
    /** Activity-log entry for a step of the automatic recovery; arguments: 0 the attempt, 1 the next try as {@code HH:mm:ss} or empty, 2 the last probe's code or {@code none}, 3 {@code waiting}, {@code gaveUp} or {@code held}; all Strings. */
    LOG_WAITING("log.waiting"),
    /** Activity-log entry for a run milestone; argument 0 is one of the tokens stageStarted, paused, resumed or finished, selecting the catalogue's own wording. */
    LOG_MILESTONE("log.milestone"),
    /** Counted sentence on the translating dashboard; argument 0 is the number of segments left. */
    TRANSLATING_SEGMENTS_REMAINING("translating.segmentsRemaining"),
    /** Label of the error dialog's button that runs the failed action again. */
    ERROR_RETRY("error.retry"),
    /** Hover explanation of the control labelled by {@link #ERROR_RETRY}. */
    ERROR_RETRY_TIP("error.retry.tip"),
    /** Label of the error dialog's control that reveals the failure's details while they are folded away. */
    ERROR_DETAILS_SHOW("error.details.show"),
    /** Hover explanation of the control labelled by {@link #ERROR_DETAILS_SHOW}. */
    ERROR_DETAILS_SHOW_TIP("error.details.show.tip"),
    /** Label of the error dialog's control that folds the failure's details away while they are shown. */
    ERROR_DETAILS_HIDE("error.details.hide"),
    /** Line under a run failure's message: decided segments survive, but only until the application closes. */
    ERROR_RUN_FAILURE_NOTE("error.runFailureNote"),
    /** Transient message raised when a book was opened; argument 0 is the file name, passed as a String. */
    TOAST_BOOK_OPENED("toast.bookOpened"),
    /** Transient message raised when a provider passed its check. */
    TOAST_PROVIDER_PASSED("toast.providerPassed"),
    /** Transient message raised when a run finished with nothing flagged; argument 0 is the number accepted. */
    TOAST_RUN_FINISHED("toast.runFinished"),
    /** Transient message raised when a run finished with flagged segments; argument 0 is accepted, argument 1 flagged. */
    TOAST_RUN_FINISHED_FLAGGED("toast.runFinishedFlagged"),
    /** Transient message raised when a run has begun, reminding that progress is kept only until the application closes. */
    TOAST_RUN_STARTED("toast.runStarted"),
    /** Transient message raised when a provider's model list could not be read. */
    TOAST_MODEL_LIST_UNREADABLE("toast.modelListUnreadable"),
    /** Accessible name of a message's dismiss button, which shows only a cross. */
    TOAST_DISMISS("toast.dismiss"),
    /** Hover explanation of the control named by {@link #TOAST_DISMISS}. */
    TOAST_DISMISS_TIP("toast.dismiss.tip"),
    /** Heading of the settings screen. */
    SETTINGS_TITLE("settings.title"),
    /** Tab of the providers area. */
    SETTINGS_TAB_PROVIDERS("settings.tab.providers"),
    /** Tab of the models area, not built yet. */
    SETTINGS_TAB_MODELS("settings.tab.models"),
    /** Tab of the generation area, not built yet. */
    SETTINGS_TAB_GENERATION("settings.tab.generation"),
    /** Tab of the appearance area. */
    SETTINGS_TAB_APPEARANCE("settings.tab.appearance"),
    /** Tab of the automation area, not built yet. */
    SETTINGS_TAB_AUTOMATION("settings.tab.automation"),
    /** Tab of the storage-and-logs area, not built yet. */
    SETTINGS_TAB_STORAGE("settings.tab.storage"),
    /** Title of the card that lists the providers. */
    SETTINGS_PROVIDERS_TITLE("settings.providers.title"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_PROVIDERS_TITLE}. */
    SETTINGS_PROVIDERS_TITLE_TIP("settings.providers.title.tip"),
    /** Label of the unavailable action that adds a provider. */
    SETTINGS_PROVIDER_ADD("settings.provider.add"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_PROVIDER_ADD}. */
    SETTINGS_PROVIDER_ADD_TIP("settings.provider.add.tip"),
    /** Label of the unavailable action that edits the selected provider. */
    SETTINGS_PROVIDER_EDIT("settings.provider.edit"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_PROVIDER_EDIT}. */
    SETTINGS_PROVIDER_EDIT_TIP("settings.provider.edit.tip"),
    /** Label of the unavailable action that deletes the selected provider. */
    SETTINGS_PROVIDER_DELETE("settings.provider.delete"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_PROVIDER_DELETE}. */
    SETTINGS_PROVIDER_DELETE_TIP("settings.provider.delete.tip"),
    /** Display name of the built-in Ollama provider. */
    SETTINGS_PROVIDER_OLLAMA("settings.provider.ollama"),
    /** Display name of the built-in LM Studio provider. */
    SETTINGS_PROVIDER_LMSTUDIO("settings.provider.lmstudio"),
    /** Badge on the provider row that is the selected one. */
    SETTINGS_PROVIDER_CURRENT("settings.provider.current"),
    /** Label before the selected provider's endpoint. */
    SETTINGS_ENDPOINT_LABEL("settings.endpoint.label"),
    /** Label before the model chosen for the selected provider. */
    SETTINGS_MODEL_LABEL("settings.model.label"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_MODEL_LABEL}. */
    SETTINGS_MODEL_LABEL_TIP("settings.model.label.tip"),
    /** Shown in place of the model while none is chosen. */
    SETTINGS_MODEL_NOT_CHOSEN("settings.model.notChosen"),
    /** Subtitle of the settings screen: the promise that nothing leaves the machine. */
    SETTINGS_SUBTITLE("settings.subtitle"),
    /** Badge on every provider row that is not the selected one. */
    SETTINGS_PROVIDER_IDLE("settings.provider.idle"),
    /** Label before the selected provider's kind on its detail card. */
    SETTINGS_KIND_LABEL("settings.kind.label"),
    /** Name of the Ollama-native provider kind. */
    SETTINGS_KIND_OLLAMA("settings.kind.ollama"),
    /** Name of the OpenAI-compatible provider kind. */
    SETTINGS_KIND_OPENAI("settings.kind.openai"),
    /** Label before the models the last listing found. */
    SETTINGS_MODELS_LABEL("settings.models.label"),
    /** The models found: argument 0 is their count, argument 1 the first names, joined. */
    SETTINGS_MODELS_FOUND("settings.models.found"),
    /** Said in place of the models found while no listing has been made. */
    SETTINGS_MODELS_NONE("settings.models.none"),
    /** Label of the action that asks whether the provider answers at all. */
    SETTINGS_TEST_CONNECTION("settings.test.connection"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_TEST_CONNECTION}. */
    SETTINGS_TEST_CONNECTION_TIP("settings.test.connection.tip"),
    /** Label of the action that also reads the provider's model list. */
    SETTINGS_TEST_MODELS("settings.test.models"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_TEST_MODELS}. */
    SETTINGS_TEST_MODELS_TIP("settings.test.models.tip"),
    /** Label of the action that also asks the chosen model for a short answer. */
    SETTINGS_TEST_INFERENCE("settings.test.inference"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_TEST_INFERENCE}. */
    SETTINGS_TEST_INFERENCE_TIP("settings.test.inference.tip"),
    /** Measured connection finding; argument 0 is the round-trip time already worded. */
    SETTINGS_BADGE_REACHABLE("settings.badge.reachable"),
    /** Measured models finding; argument 0 is the number of models listed. */
    SETTINGS_BADGE_MODELS("settings.badge.models"),
    /** Measured inference finding; argument 0 is the duration already worded. */
    SETTINGS_BADGE_INFERENCE("settings.badge.inference"),
    /** A duration under one second; argument 0 is the whole milliseconds. */
    SETTINGS_DURATION_MS("settings.duration.ms"),
    /** A duration of a second or more; argument 0 is the seconds, one decimal. */
    SETTINGS_DURATION_S("settings.duration.s"),
    /** Shown while a provider check is running. */
    SETTINGS_CHECK_PROGRESS("settings.check.progress"),
    /** Hint shown while no model is chosen, because two of the provider tests need one. */
    SETTINGS_CHECK_NEEDS_MODEL("settings.check.needsModel"),
    /** Name of the first check stage: does the server answer. */
    SETTINGS_STAGE_CONNECTION("settings.stage.connection"),
    /** Name of the second check stage: can the model list be read. */
    SETTINGS_STAGE_MODELS("settings.stage.models"),
    /** Name of the third check stage: does the model answer a short request. */
    SETTINGS_STAGE_INFERENCE("settings.stage.inference"),
    /** Outcome word of a stage that passed. */
    SETTINGS_STATUS_PASSED("settings.status.passed"),
    /** Outcome word of a stage that passed with a limitation. */
    SETTINGS_STATUS_SOFT_PASS("settings.status.softPass"),
    /** Outcome word of a stage that failed. */
    SETTINGS_STATUS_FAILED("settings.status.failed"),
    /** Outcome word of a stage that was not attempted. */
    SETTINGS_STATUS_SKIPPED("settings.status.skipped"),
    /** One finding of a check; argument 0 is the status glyph, argument 1 the stage name and argument 2 the outcome word. */
    SETTINGS_CHIP("settings.chip"),
    /** Note beside the model entry while the provider's model list is being fetched. */
    SETTINGS_MODEL_LISTING("settings.model.listing"),
    /** Note beside the model entry when no list was obtained; says the entry still accepts a typed name. */
    SETTINGS_MODEL_NO_LIST("settings.model.noList"),
    /** Label of the theme selector in the appearance tab. */
    APPEARANCE_THEME_LABEL("appearance.theme.label"),
    /** Option of the theme selector that always uses the light block. */
    APPEARANCE_THEME_LIGHT("appearance.theme.light"),
    /** Hover explanation of the control labelled by {@link #APPEARANCE_THEME_LIGHT}. */
    APPEARANCE_THEME_LIGHT_TIP("appearance.theme.light.tip"),
    /** Option of the theme selector that always uses the dark block. */
    APPEARANCE_THEME_DARK("appearance.theme.dark"),
    /** Hover explanation of the control labelled by {@link #APPEARANCE_THEME_DARK}. */
    APPEARANCE_THEME_DARK_TIP("appearance.theme.dark.tip"),
    /** Option of the theme selector that follows the operating system's colour scheme. */
    APPEARANCE_THEME_SYSTEM("appearance.theme.system"),
    /** Hover explanation of the control labelled by {@link #APPEARANCE_THEME_SYSTEM}. */
    APPEARANCE_THEME_SYSTEM_TIP("appearance.theme.system.tip"),
    /** Hint under the theme selector saying what the options mean. */
    APPEARANCE_THEME_HINT("appearance.theme.hint"),
    /** Label of the accent row in the appearance tab. */
    APPEARANCE_ACCENT_LABEL("appearance.accent.label"),
    /** Name and value of the accent colour, which is the same in both theme blocks. */
    APPEARANCE_ACCENT_VALUE("appearance.accent.value"),
    /** Tag saying the accent cannot be changed in this version. */
    APPEARANCE_ACCENT_FIXED("appearance.accent.fixed"),
    /** Heading of the import screen. */
    IMPORT_TITLE("import.title"),
    /** Line under the import heading naming the formats that can be opened. */
    IMPORT_SUBTITLE("import.subtitle"),
    /** Invitation shown in the drop zone. */
    IMPORT_DROPZONE_TITLE("import.dropzone.title"),
    /** Word between the drop invitation and the browse button. */
    IMPORT_DROPZONE_OR("import.dropzone.or"),
    /** Button that opens the system file picker. */
    IMPORT_BROWSE("import.browse"),
    /** Hover explanation of the control labelled by {@link #IMPORT_BROWSE}. */
    IMPORT_BROWSE_TIP("import.browse.tip"),
    /** Title of the system file picker. */
    IMPORT_CHOOSER_TITLE("import.chooser.title"),
    /** Name of the picker's filter that lists every openable format. */
    IMPORT_CHOOSER_FILTER("import.chooser.filter"),
    /** Line shown while a book is being opened; argument 0 is the file name, passed as a String. */
    IMPORT_PROGRESS("import.progress"),
    /** Heading of the card that reports an opened book. */
    IMPORT_CARD_TITLE("import.card.title"),
    /** Label of the file-name row of the book card. */
    IMPORT_CARD_FILE("import.card.file"),
    /** Label of the format row of the book card. */
    IMPORT_CARD_FORMAT("import.card.format"),
    /** Label of the row holding the language the book declares. */
    IMPORT_CARD_LANGUAGE("import.card.language"),
    /** Count of translatable segments; argument 0 is the count, passed as an Integer. */
    IMPORT_COUNT_SEGMENTS("import.count.segments"),
    /** Name of the EPUB format. */
    IMPORT_FORMAT_EPUB("import.format.epub"),
    /** Name of the FB2 format. */
    IMPORT_FORMAT_FB2("import.format.fb2"),
    /** Name of the Markdown format. */
    IMPORT_FORMAT_MARKDOWN("import.format.markdown"),
    /** Name of the plain-text format. */
    IMPORT_FORMAT_TXT("import.format.txt"),
    /** A language with its code; argument 0 is the language name and argument 1 its code, both Strings. */
    IMPORT_LANGUAGE("import.language"),
    /** Line of a refusal naming the file; argument 0 is the file name, passed as a String. */
    IMPORT_REFUSAL_FILE("import.refusal.file"),
    /** Button of a refusal that lets the person pick a different file. */
    IMPORT_CHOOSE_ANOTHER("import.refusal.chooseAnother"),
    /** Hover explanation of the control labelled by {@link #IMPORT_CHOOSE_ANOTHER}. */
    IMPORT_CHOOSE_ANOTHER_TIP("import.refusal.chooseAnother.tip"),
    /** Heading of the language-mismatch warning. */
    IMPORT_MISMATCH_TITLE("import.mismatch.title"),
    /** Body of the language-mismatch warning; argument 0 is the declared language and argument 1 the language of the text, both Strings. */
    IMPORT_MISMATCH_TEXT("import.mismatch.text"),
    /** Label of the row joining the title and the author. */
    IMPORT_CARD_TITLE_AUTHOR("import.card.titleAuthor"),
    /** Label of the row counting chapters and approximate words. */
    IMPORT_CARD_CHAPTERS("import.card.chapters"),
    /** Chapters and approximate words; argument 0 is the chapter count and argument 1 the already formatted word figure. */
    IMPORT_CARD_CHAPTERS_WORDS("import.card.chaptersWords"),
    /** Label of the row counting images and fonts. */
    IMPORT_CARD_MEDIA("import.card.media"),
    /** Images and fonts; argument 0 is the image count and argument 1 the font count. */
    IMPORT_CARD_MEDIA_COUNTS("import.card.mediaCounts"),
    /** Label of the row saying whether the book is DRM-protected. */
    IMPORT_CARD_DRM("import.card.drm"),
    /** Value of the DRM row of a book that is not encrypted. */
    IMPORT_DRM_NONE("import.drm.none"),
    /** Accessible name of the cover picture. */
    IMPORT_CARD_COVER("import.card.cover"),
    /** Button that discards the opened book and returns to the drop zone. */
    IMPORT_CANCEL("import.cancel"),
    /** Hover explanation of the control labelled by {@link #IMPORT_CANCEL}. */
    IMPORT_CANCEL_TIP("import.cancel.tip"),
    /** Heading of the warning about a declared language the application cannot name. */
    IMPORT_UNRECOGNIZED_TITLE("import.unrecognized.title"),
    /** Body of that warning; argument 0 is the code as the book wrote it. */
    IMPORT_UNRECOGNIZED_TEXT("import.unrecognized.text"),
    /** Heading of the DRM-blocked banner. */
    IMPORT_DRM_TITLE("import.drm.title"),
    /** Body of the DRM-blocked banner. */
    IMPORT_DRM_TEXT("import.drm.text"),
    /** Label of the file row of the DRM-blocked state. */
    IMPORT_DRM_FILE("import.drm.file"),
    /** Label of the encryption row of the DRM-blocked state. */
    IMPORT_DRM_SCHEME("import.drm.scheme"),
    /** Label of the status row of the DRM-blocked state. */
    IMPORT_DRM_STATUS("import.drm.status"),
    /** Value of that status row. */
    IMPORT_DRM_STATUS_VALUE("import.drm.statusValue"),
    /** Note that only DRM-free books are supported. */
    IMPORT_DRM_NOTE("import.drm.note"),
    /** Heading of the unsupported-file banner. */
    IMPORT_UNSUPPORTED_TITLE("import.unsupported.title"),
    /** Body of the unsupported-file banner. */
    IMPORT_UNSUPPORTED_TEXT("import.unsupported.text"),
    /** Label of the file row of the unsupported state. */
    IMPORT_UNSUPPORTED_FILE("import.unsupported.file"),
    /** Label of the detected-type row. */
    IMPORT_UNSUPPORTED_TYPE("import.unsupported.type"),
    /** Badge beside the detected type. */
    IMPORT_UNSUPPORTED_BADGE("import.unsupported.badge"),
    /** Hint listing the supported formats. */
    IMPORT_UNSUPPORTED_HINT("import.unsupported.hint"),
    /** Button that moves on from an opened book to the brief. */
    IMPORT_CONTINUE("import.continue"),
    /** Hover explanation of the control labelled by {@link #IMPORT_CONTINUE}. */
    IMPORT_CONTINUE_TIP("import.continue.tip"),
    /** Heading of the book-brief screen. */
    BRIEF_TITLE("brief.title"),
    /** Line under the heading saying what the brief is for. */
    BRIEF_SUBTITLE("brief.subtitle"),
    /** Heading of the card holding the source and target languages. */
    BRIEF_CARD_LANGUAGES("brief.card.languages"),
    /** Button on the tone card that asks the model to suggest the style fields. */
    BRIEF_SUGGEST("brief.suggest"),
    /** Hover explanation of the control labelled by {@link #BRIEF_SUGGEST}. */
    BRIEF_SUGGEST_TIP("brief.suggest.tip"),
    /** Line shown after the model filled in the style fields. */
    BRIEF_SUGGEST_DONE("brief.suggest.done"),
    /** Line shown when a style is suggested but no model is chosen. */
    BRIEF_SUGGEST_NO_MODEL("brief.suggest.noModel"),
    /** Heading of the tone-and-style card. */
    BRIEF_CARD_TONE("brief.card.tone"),
    /** Heading of the translation-policies card. */
    BRIEF_CARD_POLICIES("brief.card.policies"),
    /** Heading of the card of auxiliary texts that can be translated as well. */
    BRIEF_CARD_ALSO("brief.card.also"),
    /** Heading of the quality-versus-speed card. */
    BRIEF_CARD_QUALITY("brief.card.quality"),
    /** Hover explanation of the control labelled by {@link #BRIEF_CARD_QUALITY}. */
    BRIEF_CARD_QUALITY_TIP("brief.card.quality.tip"),
    /** Tag on a card whose choices nothing reads yet. */
    BRIEF_SOON("brief.soon"),
    /** Label of the source language box. */
    BRIEF_SOURCE_LABEL("brief.source.label"),
    /** Hover explanation of the control labelled by {@link #BRIEF_SOURCE_LABEL}. */
    BRIEF_SOURCE_LABEL_TIP("brief.source.label.tip"),
    /** Shown in place of the source language when the book declares none. */
    BRIEF_SOURCE_UNDECLARED("brief.source.undeclared"),
    /** Label of the target-language picker. */
    BRIEF_TARGET_LABEL("brief.target.label"),
    /** Hover explanation of the control labelled by {@link #BRIEF_TARGET_LABEL}. */
    BRIEF_TARGET_LABEL_TIP("brief.target.label.tip"),
    BRIEF_TARGET_REQUIRED("brief.target.required"),
    /** Note under the target box when the chosen language has no tested translation rules. */
    BRIEF_TARGET_UNTESTED("brief.target.untested"),
    /** Hover explanation of the note {@link #BRIEF_TARGET_UNTESTED}. */
    BRIEF_TARGET_UNTESTED_TIP("brief.target.untested.tip"),
    /** Label of the genre field. */
    BRIEF_TONE_GENRE("brief.tone.genre"),
    /** Hover explanation of the control labelled by {@link #BRIEF_TONE_GENRE}. */
    BRIEF_TONE_GENRE_TIP("brief.tone.genre.tip"),
    /** Prompt text of the genre field. */
    BRIEF_TONE_GENRE_PROMPT("brief.tone.genre.prompt"),
    /** Label of the register picker. */
    BRIEF_TONE_REGISTER("brief.tone.register"),
    /** Hover explanation of the control labelled by {@link #BRIEF_TONE_REGISTER}. */
    BRIEF_TONE_REGISTER_TIP("brief.tone.register.tip"),
    /** Register option: formal and literary. */
    BRIEF_REGISTER_FORMAL("brief.register.formal"),
    /** Register option: neutral. */
    BRIEF_REGISTER_NEUTRAL("brief.register.neutral"),
    /** Register option: casual. */
    BRIEF_REGISTER_CASUAL("brief.register.casual"),
    /** Label of the narrative-voice and era notes field. */
    BRIEF_TONE_VOICE("brief.tone.voice"),
    /** Hover explanation of the control labelled by {@link #BRIEF_TONE_VOICE}. */
    BRIEF_TONE_VOICE_TIP("brief.tone.voice.tip"),
    /** Prompt text of the narrative-voice field. */
    BRIEF_TONE_VOICE_PROMPT("brief.tone.voice.prompt"),
    /** Label of the audience field. */
    BRIEF_TONE_AUDIENCE("brief.tone.audience"),
    /** Hover explanation of the control labelled by {@link #BRIEF_TONE_AUDIENCE}. */
    BRIEF_TONE_AUDIENCE_TIP("brief.tone.audience.tip"),
    /** Prompt text of the audience field. */
    BRIEF_TONE_AUDIENCE_PROMPT("brief.tone.audience.prompt"),
    /** Label of the narrator-person choice on the tone card. */
    BRIEF_TONE_NARRATOR("brief.tone.narrator"),
    /** Hover explanation of the control labelled by {@link #BRIEF_TONE_NARRATOR}. */
    BRIEF_TONE_NARRATOR_TIP("brief.tone.narrator.tip"),
    /** Narrator option: not stated. */
    BRIEF_NARRATOR_UNSPECIFIED("brief.narrator.unspecified"),
    /** Narrator option: first person. */
    BRIEF_NARRATOR_FIRST("brief.narrator.first"),
    /** Narrator option: third person. */
    BRIEF_NARRATOR_THIRD("brief.narrator.third"),
    /** Label of the narrator-gender choice on the tone card. */
    BRIEF_TONE_NARRATOR_GENDER("brief.tone.narrator.gender"),
    /** Hover explanation of the control labelled by {@link #BRIEF_TONE_NARRATOR_GENDER}. */
    BRIEF_TONE_NARRATOR_GENDER_TIP("brief.tone.narrator.gender.tip"),
    /** Notice on the tone card: the source is told in the first person and the narrator's gender is not chosen. */
    BRIEF_NARRATOR_NOTICE("brief.narrator.notice"),
    /** Hover explanation of the notice labelled by {@link #BRIEF_NARRATOR_NOTICE}. */
    BRIEF_NARRATOR_NOTICE_TIP("brief.narrator.notice.tip"),
    /** Narrator gender option: not stated. */
    BRIEF_NARRATOR_GENDER_UNKNOWN("brief.narrator.gender.unknown"),
    /** Narrator gender option: male. */
    BRIEF_NARRATOR_GENDER_MALE("brief.narrator.gender.male"),
    /** Narrator gender option: female. */
    BRIEF_NARRATOR_GENDER_FEMALE("brief.narrator.gender.female"),
    /** Label of the character-and-place-names policy. */
    BRIEF_POLICY_NAMES("brief.policy.names"),
    /** Hover explanation of the control labelled by {@link #BRIEF_POLICY_NAMES}. */
    BRIEF_POLICY_NAMES_TIP("brief.policy.names.tip"),
    /** Label of the foreign-language-passages policy. */
    BRIEF_POLICY_FOREIGN("brief.policy.foreign"),
    /** Hover explanation of the control labelled by {@link #BRIEF_POLICY_FOREIGN}. */
    BRIEF_POLICY_FOREIGN_TIP("brief.policy.foreign.tip"),
    /** Hint under the foreign-passages policy. */
    BRIEF_POLICY_FOREIGN_HINT("brief.policy.foreign.hint"),
    /** Label of the footnotes policy. */
    BRIEF_POLICY_FOOTNOTES("brief.policy.footnotes"),
    /** Hover explanation of the control labelled by {@link #BRIEF_POLICY_FOOTNOTES}. */
    BRIEF_POLICY_FOOTNOTES_TIP("brief.policy.footnotes.tip"),
    /** Label of the units policy. */
    BRIEF_POLICY_UNITS("brief.policy.units"),
    /** Hover explanation of the control labelled by {@link #BRIEF_POLICY_UNITS}. */
    BRIEF_POLICY_UNITS_TIP("brief.policy.units.tip"),
    /** Label of the faithful-to-natural balance slider. */
    BRIEF_POLICY_BALANCE("brief.policy.balance"),
    /** Hover explanation of the control labelled by {@link #BRIEF_POLICY_BALANCE}. */
    BRIEF_POLICY_BALANCE_TIP("brief.policy.balance.tip"),
    /** Hint naming the three regions of the balance slider. */
    BRIEF_POLICY_BALANCE_HINT("brief.policy.balance.hint"),
    /** Policy option: translate. */
    BRIEF_OPTION_TRANSLATE("brief.option.translate"),
    /** Policy option: transliterate. */
    BRIEF_OPTION_TRANSLITERATE("brief.option.transliterate"),
    /** Policy option: keep the original spelling. */
    BRIEF_OPTION_KEEP_ORIGINAL("brief.option.keepOriginal"),
    /** Policy option: leave the passage as it is. */
    BRIEF_OPTION_KEEP_AS_IS("brief.option.keepAsIs"),
    /** Policy option: translate the passage and add a note. */
    BRIEF_OPTION_TRANSLATE_NOTE("brief.option.translateNote"),
    /** Policy option: keep. */
    BRIEF_OPTION_KEEP("brief.option.keep"),
    /** Units option: convert to metric. */
    BRIEF_OPTION_METRIC("brief.option.metric"),
    /** Switch for translating table-of-contents and navigation labels. */
    BRIEF_AUX_NAV("brief.aux.nav"),
    /** Hover explanation of the control labelled by {@link #BRIEF_AUX_NAV}. */
    BRIEF_AUX_NAV_TIP("brief.aux.nav.tip"),
    /** Switch for translating image alternative text. */
    BRIEF_AUX_ALT("brief.aux.alt"),
    /** Hover explanation of the control labelled by {@link #BRIEF_AUX_ALT}. */
    BRIEF_AUX_ALT_TIP("brief.aux.alt.tip"),
    /** Switch for translating the book's title and author metadata. */
    BRIEF_AUX_METADATA("brief.aux.metadata"),
    /** Hover explanation of the control labelled by {@link #BRIEF_AUX_METADATA}. */
    BRIEF_AUX_METADATA_TIP("brief.aux.metadata.tip"),
    /** Switch for translating values in the front matter. */
    BRIEF_AUX_FRONTMATTER("brief.aux.frontmatter"),
    /** Hover explanation of the control labelled by {@link #BRIEF_AUX_FRONTMATTER}. */
    BRIEF_AUX_FRONTMATTER_TIP("brief.aux.frontmatter.tip"),
    /** Hint under the front-matter switch. */
    BRIEF_AUX_FRONTMATTER_HINT("brief.aux.frontmatter.hint"),
    /** Quality option: fastest. */
    BRIEF_QUALITY_FAST("brief.quality.fast"),
    /** Quality option: balanced. */
    BRIEF_QUALITY_BALANCED("brief.quality.balanced"),
    /** Quality option: best quality. */
    BRIEF_QUALITY_MAX("brief.quality.max"),
    /** Hint describing what the fast quality setting turns on. */
    BRIEF_QUALITY_HINT_FAST("brief.quality.hint.fast"),
    /** Hint describing what the balanced quality setting turns on. */
    BRIEF_QUALITY_HINT_BALANCED("brief.quality.hint.balanced"),
    /** Hint describing what the max quality setting turns on. */
    BRIEF_QUALITY_HINT_MAX("brief.quality.hint.max"),
    /** Label of the row naming the model a run will use. */
    BRIEF_MODEL_LABEL("brief.model.label"),
    /** The model row's value; argument 0 is the model, argument 1 the provider's name. */
    BRIEF_MODEL_VALUE("brief.model.value"),
    /** The model row's value while no model is chosen. */
    BRIEF_MODEL_NONE("brief.model.none"),
    /** Link on the model row that opens the settings. */
    BRIEF_MODEL_CHANGE("brief.model.change"),
    /** Hover explanation of the control labelled by {@link #BRIEF_MODEL_CHANGE}. */
    BRIEF_MODEL_CHANGE_TIP("brief.model.change.tip"),
    /** Message beside the language boxes when source and target are the same language. */
    BRIEF_LANGUAGES_SAME("brief.languages.same"),
    /** Button that returns from the brief to the import screen. */
    BRIEF_BACK("brief.back"),
    /** Hover explanation of the control labelled by {@link #BRIEF_BACK}. */
    BRIEF_BACK_TIP("brief.back.tip"),
    /** Button that moves on from the brief to the next step. */
    BRIEF_CONTINUE("brief.continue"),
    /** Hover explanation of the control labelled by {@link #BRIEF_CONTINUE}. */
    BRIEF_CONTINUE_TIP("brief.continue.tip"),
    /** Heading of the state shown by a screen that needs a book when none is open. */
    NOBOOK_TITLE("nobook.title"),
    /** Sentence reporting that no book has been opened yet. */
    NOBOOK_REPORT("nobook.report"),
    /** Button from the no-book state to the import screen. */
    NOBOOK_OPEN("nobook.open"),
    /** Hover explanation of the control labelled by {@link #NOBOOK_OPEN}. */
    NOBOOK_OPEN_TIP("nobook.open.tip"),
    /** Heading of the structure screen. */
    STRUCTURE_TITLE("structure.title"),
    /** Title shown for a structure node the book gives none. */
    STRUCTURE_UNTITLED("structure.untitled"),
    /** Total of translatable segments beneath the list; argument 0 is the count, passed as an Integer. */
    STRUCTURE_TOTAL("structure.total"),
    /**
     * The segments a run translates, beneath the total: argument 0 the book-text segments, 1 the other texts (titles,
     * image descriptions, contents, book details), 2 their sum, each an Integer.
     */
    STRUCTURE_RUN_TOTAL("structure.runTotal"),
    /** Button that moves on from the structure screen to the next available step. */
    STRUCTURE_CONTINUE("structure.continue"),
    /** Hover explanation of the control labelled by {@link #STRUCTURE_CONTINUE}. */
    STRUCTURE_CONTINUE_TIP("structure.continue.tip"),
    /** Heading of the card holding the structure tree. */
    STRUCTURE_READING_ORDER("structure.readingOrder"),
    /** Hover explanation of the control labelled by {@link #STRUCTURE_READING_ORDER}. */
    STRUCTURE_READING_ORDER_TIP("structure.readingOrder.tip"),
    /** Heading of the card holding the segment browser. */
    STRUCTURE_SEGMENTS_TITLE("structure.segments.title"),
    /** Hover explanation of the control labelled by {@link #STRUCTURE_SEGMENTS_TITLE}. */
    STRUCTURE_SEGMENTS_TITLE_TIP("structure.segments.title.tip"),
    /** The list's summary; argument 0 is the segment count and 1 the planned chunk count, each an Integer. */
    STRUCTURE_SEGMENTS_HEADER("structure.segments.header"),
    /** Hover explanation of the segment list. */
    STRUCTURE_SEGMENTS_HINT("structure.segments.hint"),
    /** Shown in place of the list while no part with segments is picked. */
    STRUCTURE_SEGMENTS_PICK("structure.segments.pick"),
    /** Shown in place of the list while the segments are being listed. */
    STRUCTURE_SEGMENTS_LOADING("structure.segments.loading"),
    /** Shown in place of the list when the segments could not be listed. */
    STRUCTURE_SEGMENTS_FAILED("structure.segments.failed"),
    /** Shown in place of the list when the picked part has no segments. */
    STRUCTURE_SEGMENTS_NONE("structure.segments.none"),
    /** Shown in place of the list when the filters keep no segment. */
    STRUCTURE_SEGMENTS_NO_MATCH("structure.segments.noMatch"),
    /** Beside the filters while they hide segments; argument 0 is the number shown and 1 the number listed, each an Integer. */
    STRUCTURE_SEGMENTS_SHOWN("structure.segments.shown"),
    /** Prompt of the field that filters the segment list by text. */
    STRUCTURE_SEARCH_PROMPT("structure.search.prompt"),
    /** Hover explanation of the field labelled by {@link #STRUCTURE_SEARCH_PROMPT}. */
    STRUCTURE_SEARCH_HINT("structure.search.hint"),
    /** Choice of the kind filter that keeps every kind. */
    STRUCTURE_KIND_ALL("structure.kind.all"),
    /** Hover explanation of the kind filter. */
    STRUCTURE_KIND_HINT("structure.kind.hint"),
    /** Chip on a segment a run copies unchanged without asking the model. */
    STRUCTURE_KEPT_VERBATIM("structure.kept.verbatim"),
    /** Hover explanation of the chip labelled by {@link #STRUCTURE_KEPT_VERBATIM}. */
    STRUCTURE_KEPT_VERBATIM_TIP("structure.kept.verbatim.tip"),
    /** Chip on a segment of text the brief leaves untranslated. */
    STRUCTURE_KEPT_SOURCE("structure.kept.source"),
    /** Hover explanation of the chip labelled by {@link #STRUCTURE_KEPT_SOURCE}. */
    STRUCTURE_KEPT_SOURCE_TIP("structure.kept.source.tip"),
    /** Hover explanation of a segment's planned chunk badge. */
    STRUCTURE_CHUNK_HINT("structure.chunk.hint"),
    /** Hover explanation of a segment's token estimate. */
    STRUCTURE_TOKENS_HINT("structure.tokens.hint"),
    /** Shown in the reading pane while no segment is picked. */
    STRUCTURE_PREVIEW_EMPTY("structure.preview.empty"),
    /** Hover explanation of the reading pane. */
    STRUCTURE_PREVIEW_HINT("structure.preview.hint"),
    /** The reading pane's caption; arguments: 0 locator, 1 kind, 2 token count (an Integer), 3 the chunk phrase. */
    STRUCTURE_PREVIEW_META("structure.preview.meta"),
    /** The chunk phrase of the reading pane's caption; arguments: 0 the chunk, 1 the chunk count, each an Integer. */
    STRUCTURE_PREVIEW_CHUNK("structure.preview.chunk"),
    /** The chunk phrase for a segment no chunk holds. */
    STRUCTURE_PREVIEW_UNCHUNKED("structure.preview.unchunked"),
    /** Added to the caption for a segment too long for one chunk. */
    STRUCTURE_PREVIEW_OVERSIZED("structure.preview.oversized"),
    /** The name of the segment kind {@code PARAGRAPH} in the segment list. */
    STRUCTURE_KIND_PARAGRAPH("structure.kind.paragraph"),
    /** The name of the segment kind {@code HEADING} in the segment list. */
    STRUCTURE_KIND_HEADING("structure.kind.heading"),
    /** The name of the segment kind {@code VERSE_LINE} in the segment list. */
    STRUCTURE_KIND_VERSE_LINE("structure.kind.verse_line"),
    /** The name of the segment kind {@code LIST_ITEM} in the segment list. */
    STRUCTURE_KIND_LIST_ITEM("structure.kind.list_item"),
    /** The name of the segment kind {@code TABLE_CELL} in the segment list. */
    STRUCTURE_KIND_TABLE_CELL("structure.kind.table_cell"),
    /** The name of the segment kind {@code FOOTNOTE} in the segment list. */
    STRUCTURE_KIND_FOOTNOTE("structure.kind.footnote"),
    /** The name of the segment kind {@code CAPTION} in the segment list. */
    STRUCTURE_KIND_CAPTION("structure.kind.caption"),
    /** The name of the segment kind {@code TITLE} in the segment list. */
    STRUCTURE_KIND_TITLE("structure.kind.title"),
    /** The name of the segment kind {@code METADATA_TITLE} in the segment list. */
    STRUCTURE_KIND_METADATA_TITLE("structure.kind.metadata_title"),
    /** The name of the segment kind {@code METADATA_AUTHOR} in the segment list. */
    STRUCTURE_KIND_METADATA_AUTHOR("structure.kind.metadata_author"),
    /** The name of the segment kind {@code METADATA_DESCRIPTION} in the segment list. */
    STRUCTURE_KIND_METADATA_DESCRIPTION("structure.kind.metadata_description"),
    /** The name of the segment kind {@code FRONTMATTER_VALUE} in the segment list. */
    STRUCTURE_KIND_FRONTMATTER_VALUE("structure.kind.frontmatter_value"),
    /** The name of the segment kind {@code ALT} in the segment list. */
    STRUCTURE_KIND_ALT("structure.kind.alt"),
    /** The name of the segment kind {@code NAV_LABEL} in the segment list. */
    STRUCTURE_KIND_NAV_LABEL("structure.kind.nav_label"),
    /** Caption of the translatable-segment count in the statistics card. */
    STRUCTURE_STAT_SEGMENTS("structure.stat.segments"),
    /** Caption of the word count in the statistics card. */
    STRUCTURE_STAT_WORDS("structure.stat.words"),
    /** The estimated word count; argument 0 is the rounded count, passed as an Integer. */
    STRUCTURE_STAT_WORDS_VALUE("structure.stat.wordsValue"),
    /** Caption of the image count in the statistics card. */
    STRUCTURE_STAT_IMAGES("structure.stat.images"),
    /** Caption of the code-block count in the statistics card. */
    STRUCTURE_STAT_CODE("structure.stat.code"),
    /** Note under the code-block count saying code is carried through. */
    STRUCTURE_STAT_CODE_NOTE("structure.stat.codeNote"),
    /** Caption of the embedded-font count in the statistics card. */
    STRUCTURE_STAT_FONTS("structure.stat.fonts"),
    /** Caption of the verse-line count in the statistics card. */
    STRUCTURE_STAT_VERSE("structure.stat.verse"),
    /** Value shown in place of a zero verse count or an empty formatting list. */
    STRUCTURE_STAT_NONE("structure.stat.none"),
    /** Caption of the footnote and table counts in the statistics card. */
    STRUCTURE_STAT_NOTES("structure.stat.notes"),
    /** Caption of the inline formatting row in the statistics card. */
    STRUCTURE_STAT_FORMATTING("structure.stat.formatting"),
    /** The formatting kinds the book carries; argument 0 is their names, joined. */
    STRUCTURE_STAT_FORMATTING_VALUE("structure.stat.formattingValue"),
    /** Name of italic formatting in the statistics card. */
    STRUCTURE_FORMAT_ITALICS("structure.format.italics"),
    /** Name of bold formatting in the statistics card. */
    STRUCTURE_FORMAT_BOLD("structure.format.bold"),
    /** Name of hyperlinks in the statistics card. */
    STRUCTURE_FORMAT_LINKS("structure.format.links"),
    /** Name of quotations in the statistics card. */
    STRUCTURE_FORMAT_QUOTES("structure.format.quotes"),
    /** Name of inline code in the statistics card. */
    STRUCTURE_FORMAT_CODE("structure.format.code"),
    /** Name of explicit line breaks in the statistics card. */
    STRUCTURE_FORMAT_LINE_BREAKS("structure.format.lineBreaks"),
    /** Name of any other formatting in the statistics card. */
    STRUCTURE_FORMAT_OTHER("structure.format.other"),
    /** The round-trip check has been asked for and has not answered. */
    STRUCTURE_CHECK_RUNNING("structure.check.running"),
    /** The round-trip check found the structure and text preserved. */
    STRUCTURE_CHECK_PASSED("structure.check.passed"),
    /** The round-trip check found the structure changed. */
    STRUCTURE_CHECK_FAILED("structure.check.failed"),
    /** The round-trip check found fewer segments; argument 0 is the copy's count, already formatted, argument 1 the source's count, passed as an Integer. */
    STRUCTURE_CHECK_FAILED_COUNTS("structure.check.failedCounts"),
    /** The round-trip check could not be run. */
    STRUCTURE_CHECK_UNAVAILABLE("structure.check.unavailable"),
    /** Every image, font and link identifier survived the round trip. */
    STRUCTURE_CHECK_IDS_OK("structure.check.idsOk"),
    /** Identifiers lost in the round trip; argument 0 is the joined identifiers. */
    STRUCTURE_CHECK_IDS_MISSING("structure.check.idsMissing"),
    /** Warning that segments exceed the chunk budget; argument 0 is the count, passed as an Integer. */
    STRUCTURE_CHECK_OVERSIZED("structure.check.oversized"),
    /** Heading of the names and style screen. */
    NAMES_STYLE_TITLE("namesStyle.title"),
    /** One line under the heading saying what the screen is for. */
    NAMES_STYLE_SUBTITLE("namesStyle.subtitle"),
    /** Information banner saying the step can be skipped. */
    NAMES_STYLE_SKIP("namesStyle.skip"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_SKIP}. */
    NAMES_STYLE_SKIP_TIP("namesStyle.skip.tip"),
    /** Button that returns from names and style to the structure screen. */
    NAMES_STYLE_BACK("namesStyle.back"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_BACK}. */
    NAMES_STYLE_BACK_TIP("namesStyle.back.tip"),
    /** Button that starts the run from names and style. */
    NAMES_STYLE_START("namesStyle.start"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_START}. */
    NAMES_STYLE_START_TIP("namesStyle.start.tip"),
    /** Footer button of names and style that resumes the paused or stopped run and shows it. */
    NAMES_STYLE_RESUME("namesStyle.resume"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_RESUME}. */
    NAMES_STYLE_RESUME_TIP("namesStyle.resume.tip"),
    /** Footer button of names and style that shows the run under way instead of starting another. */
    NAMES_STYLE_TO_RUN("namesStyle.toRun"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_TO_RUN}. */
    NAMES_STYLE_TO_RUN_TIP("namesStyle.toRun.tip"),
    /** Heading of the glossary card. */
    NAMES_STYLE_GLOSSARY_TITLE("namesStyle.glossary.title"),
    /** Button that opens the Add term card. */
    NAMES_STYLE_ADD("namesStyle.add"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_ADD}. */
    NAMES_STYLE_ADD_TIP("namesStyle.add.tip"),
    /** Button that runs the model name scan. */
    NAMES_STYLE_MODEL_SCAN("namesStyle.modelScan"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_MODEL_SCAN}. */
    NAMES_STYLE_MODEL_SCAN_TIP("namesStyle.modelScan.tip"),
    /** Button that runs the model review of the glossary. */
    NAMES_STYLE_REVIEW("namesStyle.review"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_REVIEW}. */
    NAMES_STYLE_REVIEW_TIP("namesStyle.review.tip"),
    /** Button that stops the model scan or review under way. */
    NAMES_STYLE_MODEL_STOP("namesStyle.modelStop"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_MODEL_STOP}. */
    NAMES_STYLE_MODEL_STOP_TIP("namesStyle.modelStop.tip"),
    /** Prompt of the field that filters the glossary rows. */
    NAMES_STYLE_SEARCH("namesStyle.search"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_SEARCH}. */
    NAMES_STYLE_SEARCH_TIP("namesStyle.search.tip"),
    /** Button that imports a glossary CSV file. */
    NAMES_STYLE_IMPORT("namesStyle.import"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_IMPORT}. */
    NAMES_STYLE_IMPORT_TIP("namesStyle.import.tip"),
    /** Button that exports the glossary to a CSV file. */
    NAMES_STYLE_EXPORT("namesStyle.export"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_EXPORT}. */
    NAMES_STYLE_EXPORT_TIP("namesStyle.export.tip"),
    /** Glossary column of the source term. */
    NAMES_STYLE_COLUMN_SOURCE("namesStyle.column.source"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_COLUMN_SOURCE}. */
    NAMES_STYLE_COLUMN_SOURCE_TIP("namesStyle.column.source.tip"),
    /** Glossary column of the term's type. */
    NAMES_STYLE_COLUMN_TYPE("namesStyle.column.type"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_COLUMN_TYPE}. */
    NAMES_STYLE_COLUMN_TYPE_TIP("namesStyle.column.type.tip"),
    /** Glossary column of the chosen rendering. */
    NAMES_STYLE_COLUMN_TARGET("namesStyle.column.target"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_COLUMN_TARGET}. */
    NAMES_STYLE_COLUMN_TARGET_TIP("namesStyle.column.target.tip"),
    /** Glossary column of the grammatical gender. */
    NAMES_STYLE_COLUMN_GENDER("namesStyle.column.gender"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_COLUMN_GENDER}. */
    NAMES_STYLE_COLUMN_GENDER_TIP("namesStyle.column.gender.tip"),
    /** A glossary gender that came from the bundled first-name list and is not confirmed; argument 0 is the gender. */
    NAMES_STYLE_GENDER_SUGGESTED("namesStyle.gender.suggested"),
    /** Glossary column of the lock switch. */
    NAMES_STYLE_COLUMN_LOCKED("namesStyle.column.locked"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_COLUMN_LOCKED}. */
    NAMES_STYLE_COLUMN_LOCKED_TIP("namesStyle.column.locked.tip"),
    /** Glossary column that flags rows needing a look: no target, likely junk. */
    NAMES_STYLE_COLUMN_FLAGS("namesStyle.column.flags"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_COLUMN_FLAGS}. */
    NAMES_STYLE_COLUMN_FLAGS_TIP("namesStyle.column.flags.tip"),
    /** Chip on a row whose target is empty. */
    NAMES_STYLE_FLAG_NO_TARGET("namesStyle.flag.noTarget"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_FLAG_NO_TARGET}. */
    NAMES_STYLE_FLAG_NO_TARGET_TIP("namesStyle.flag.noTarget.tip"),
    /** Chip on a row whose term looks like printing text or an ordinary word rather than a name. */
    NAMES_STYLE_FLAG_JUNK("namesStyle.flag.junk"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_FLAG_JUNK}. */
    NAMES_STYLE_FLAG_JUNK_TIP("namesStyle.flag.junk.tip"),
    /** Accessible name of the row's remove action. */
    NAMES_STYLE_REMOVE("namesStyle.remove"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_REMOVE}. */
    NAMES_STYLE_REMOVE_TIP("namesStyle.remove.tip"),
    /** Text shown in place of the glossary rows when the book has no names yet. */
    NAMES_STYLE_EMPTY("namesStyle.empty"),
    /** Text shown in place of the glossary rows when the search matches none of them. */
    NAMES_STYLE_EMPTY_FILTERED("namesStyle.empty.filtered"),
    /** Text shown in place of the recurring-terms rows when the book has none yet. */
    RECURRING_EMPTY("namesStyle.recurring.empty"),
    /** Heading of the recurring-terms card. */
    RECURRING_TITLE("namesStyle.recurring.title"),
    /** Line under the heading that says what the card is for. */
    RECURRING_NOTE("namesStyle.recurring.note"),
    /** Button that runs the deterministic scan for recurring terms. */
    RECURRING_FIND("namesStyle.recurring.find"),
    /** Hover explanation of the control labelled by {@link #RECURRING_FIND}. */
    RECURRING_FIND_TIP("namesStyle.recurring.find.tip"),
    /** Button that asks the model to choose recurring terms among the book's frequent words. */
    RECURRING_MODEL_SCAN("namesStyle.recurring.modelScan"),
    /** Line shown when a model scan or review of recurring terms is asked for and no model is chosen. */
    RECURRING_NO_MODEL_TERMS("namesStyle.recurring.noModelTerms"),
    /** Hover explanation of the control labelled by {@link #RECURRING_MODEL_SCAN}. */
    RECURRING_MODEL_SCAN_TIP("namesStyle.recurring.modelScan.tip"),
    /** Button that asks the model which recurring terms to keep. */
    RECURRING_MODEL_REVIEW("namesStyle.recurring.modelReview"),
    /** Hover explanation of the control labelled by {@link #RECURRING_MODEL_REVIEW}. */
    RECURRING_MODEL_REVIEW_TIP("namesStyle.recurring.modelReview.tip"),
    /** Button that asks the model for a rendering of each term that has none. */
    RECURRING_SUGGEST("namesStyle.recurring.suggest"),
    /** Hover explanation of the control labelled by {@link #RECURRING_SUGGEST}. */
    RECURRING_SUGGEST_TIP("namesStyle.recurring.suggest.tip"),
    /** Placeholder of the field where a word or title is typed. */
    RECURRING_ADD_PROMPT("namesStyle.recurring.add.prompt"),
    /** Hover explanation of the field whose placeholder is {@link #RECURRING_ADD_PROMPT}. */
    RECURRING_ADD_PROMPT_TIP("namesStyle.recurring.add.prompt.tip"),
    /** Button that adds the typed word or title to the list. */
    RECURRING_ADD("namesStyle.recurring.add"),
    /** Hover explanation of the control labelled by {@link #RECURRING_ADD}. */
    RECURRING_ADD_TIP("namesStyle.recurring.add.tip"),
    /** Header of the source-term column. */
    RECURRING_COLUMN_TERM("namesStyle.recurring.column.term"),
    /** Hover explanation of the column labelled by {@link #RECURRING_COLUMN_TERM}. */
    RECURRING_COLUMN_TERM_TIP("namesStyle.recurring.column.term.tip"),
    /** Header of the rendering column. */
    RECURRING_COLUMN_RENDERING("namesStyle.recurring.column.rendering"),
    /** Hover explanation of the column labelled by {@link #RECURRING_COLUMN_RENDERING}. */
    RECURRING_COLUMN_RENDERING_TIP("namesStyle.recurring.column.rendering.tip"),
    /** Header of the column that lists the renderings the drafts used. */
    RECURRING_COLUMN_SEEN("namesStyle.recurring.column.seen"),
    /** Hover explanation of the column labelled by {@link #RECURRING_COLUMN_SEEN}. */
    RECURRING_COLUMN_SEEN_TIP("namesStyle.recurring.column.seen.tip"),
    /** Header of the column that holds a recurring term's actions. */
    RECURRING_COLUMN_ACTIONS("namesStyle.recurring.column.actions"),
    /** Hover explanation of the column labelled by {@link #RECURRING_COLUMN_ACTIONS}. */
    RECURRING_COLUMN_ACTIONS_TIP("namesStyle.recurring.column.actions.tip"),
    /** Shown in the used column of a term no draft has used yet. */
    RECURRING_SEEN_NONE("namesStyle.recurring.seen.none"),
    /** Shown in the used column for a rendering learned from the translated text: the rendering and its support. */
    RECURRING_SEEN_LEARNED("namesStyle.recurring.seen.learned"),
    /** Row button that moves a term to the glossary. */
    RECURRING_PROMOTE("namesStyle.recurring.promote"),
    /** Hover explanation of the control labelled by {@link #RECURRING_PROMOTE}. */
    RECURRING_PROMOTE_TIP("namesStyle.recurring.promote.tip"),
    /** Accessible name of a recurring term's remove action. */
    RECURRING_REMOVE("namesStyle.recurring.remove"),
    /** Hover explanation of the control named by {@link #RECURRING_REMOVE}. */
    RECURRING_REMOVE_TIP("namesStyle.recurring.remove.tip"),
    /** Why the suggestion of renderings did not start. */
    RECURRING_NO_MODEL("namesStyle.recurring.noModel"),
    /** Result of the suggestion of renderings; argument 0 is how many terms now have a rendering, as an Integer. */
    RECURRING_SUGGESTED("namesStyle.recurring.suggested"),
    /** Line after the model scan of recurring terms; argument 0 is how many terms were added. */
    RECURRING_MODEL_SCANNED("namesStyle.recurring.modelScanned"),
    /** Line after the model review of recurring terms; argument 0 is how many terms were removed. */
    RECURRING_MODEL_REVIEWED("namesStyle.recurring.modelReviewed"),
    /** Refusal of a term the glossary or the list already holds; argument 0 is the typed term. */
    RECURRING_DUPLICATE("namesStyle.recurring.duplicate"),
    /** Refusal of a lock on a term that has no target. */
    NAMES_STYLE_LOCK_NEEDS_TARGET("namesStyle.lockNeedsTarget"),
    /** Refusal of a duplicate term; argument 0 is the typed term. */
    NAMES_STYLE_DUPLICATE("namesStyle.duplicate"),
    /** Why the model scan did not start. */
    NAMES_STYLE_NO_MODEL("namesStyle.noModel"),
    /** Why the model review did not start. */
    NAMES_STYLE_NO_MODEL_REVIEW("namesStyle.noModelReview"),
    /**
     * The line shown while a model scan or review waits; argument 0 is the request number, 1 the attempt and 2 the
     * attempts allowed, each passed as an Integer.
     */
    NAMES_STYLE_MODEL_PROGRESS("namesStyle.modelProgress"),
    /** The line shown once a model scan or review was stopped. */
    NAMES_STYLE_MODEL_STOPPED("namesStyle.modelStopped"),
    /**
     * Result of a model review; argument 0 is the removed count, 1 the updated count and 2 the count of targets
     * suggested, each passed as an Integer.
     */
    NAMES_STYLE_REVIEWED("namesStyle.reviewed"),
    /** The line shown while targets are suggested; argument 0 is the batch and 1 the batches, each an Integer. */
    NAMES_STYLE_SUGGESTING("namesStyle.suggesting"),
    /** Button that confirms every suggested target. */
    NAMES_STYLE_ACCEPT_ALL("namesStyle.acceptAll"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_ACCEPT_ALL}. */
    NAMES_STYLE_ACCEPT_ALL_TIP("namesStyle.acceptAll.tip"),
    /** Accessible name of a row's action that confirms its suggested target. */
    NAMES_STYLE_ACCEPT("namesStyle.accept"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_ACCEPT}. */
    NAMES_STYLE_ACCEPT_TIP("namesStyle.accept.tip"),
    /** The short badge on a target the model suggested. */
    NAMES_STYLE_SUGGESTED_BADGE("namesStyle.suggestedBadge"),
    /** Hover explanation of the badge labelled by {@link #NAMES_STYLE_SUGGESTED_BADGE}. */
    NAMES_STYLE_SUGGESTED_BADGE_TIP("namesStyle.suggestedBadge.tip"),
    /** Notice on starting a run with suggestions unconfirmed; argument 0 is their count, passed as an Integer. */
    NAMES_STYLE_SUGGESTIONS_UNREVIEWED("namesStyle.suggestionsUnreviewed"),
    /** Result of an import; argument 0 is the row count, passed as an Integer. */
    NAMES_STYLE_IMPORT_DONE("namesStyle.import.done"),
    /** Lines an import could not read; argument 0 is the joined line numbers, argument 1 their count. */
    NAMES_STYLE_IMPORT_MALFORMED("namesStyle.import.malformed"),
    /** Lines an import refused as locked without a target; argument 0 is the joined line numbers, argument 1 their count. */
    NAMES_STYLE_IMPORT_REFUSED("namesStyle.import.refused"),
    /** Result of an export; argument 0 is the written file name. */
    NAMES_STYLE_EXPORT_DONE("namesStyle.export.done"),
    /** Term type: a character. */
    NAMES_STYLE_TYPE_CHARACTER("namesStyle.type.character"),
    /** Term type: a place. */
    NAMES_STYLE_TYPE_PLACE("namesStyle.type.place"),
    /** Term type: a recurring term. */
    NAMES_STYLE_TYPE_TERM("namesStyle.type.term"),
    /** Term type: a title or honorific. */
    NAMES_STYLE_TYPE_TITLE("namesStyle.type.title"),
    /** Term type: anything else. */
    NAMES_STYLE_TYPE_OTHER("namesStyle.type.other"),
    /** Grammatical gender: female. */
    NAMES_STYLE_GENDER_FEMALE("namesStyle.gender.female"),
    /** Grammatical gender: male. */
    NAMES_STYLE_GENDER_MALE("namesStyle.gender.male"),
    /** Grammatical gender: neuter. */
    NAMES_STYLE_GENDER_NEUTER("namesStyle.gender.neuter"),
    /** Grammatical gender: not known. */
    NAMES_STYLE_GENDER_UNKNOWN("namesStyle.gender.unknown"),
    /** Heading of the Add term card. */
    DIALOG_ADD_TERM_TITLE("dialog.addTerm.title"),
    /** Line under the Add term heading. */
    DIALOG_ADD_TERM_SUBTITLE("dialog.addTerm.subtitle"),
    /** Label of the source field. */
    DIALOG_ADD_TERM_SOURCE("dialog.addTerm.source"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_TERM_SOURCE}. */
    DIALOG_ADD_TERM_SOURCE_TIP("dialog.addTerm.source.tip"),
    /** Label of the target field. */
    DIALOG_ADD_TERM_TARGET("dialog.addTerm.target"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_TERM_TARGET}. */
    DIALOG_ADD_TERM_TARGET_TIP("dialog.addTerm.target.tip"),
    /** Label of the type choice. */
    DIALOG_ADD_TERM_TYPE("dialog.addTerm.type"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_TERM_TYPE}. */
    DIALOG_ADD_TERM_TYPE_TIP("dialog.addTerm.type.tip"),
    /** Label of the gender choice. */
    DIALOG_ADD_TERM_GENDER("dialog.addTerm.gender"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_TERM_GENDER}. */
    DIALOG_ADD_TERM_GENDER_TIP("dialog.addTerm.gender.tip"),
    /** Label of the lock switch. */
    DIALOG_ADD_TERM_LOCK("dialog.addTerm.lock"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_TERM_LOCK}. */
    DIALOG_ADD_TERM_LOCK_TIP("dialog.addTerm.lock.tip"),
    /** Button that closes the Add term card without adding. */
    DIALOG_ADD_TERM_CANCEL("dialog.addTerm.cancel"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_TERM_CANCEL}. */
    DIALOG_ADD_TERM_CANCEL_TIP("dialog.addTerm.cancel.tip"),
    /** Button that adds the term. */
    DIALOG_ADD_TERM_CONFIRM("dialog.addTerm.confirm"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_TERM_CONFIRM}. */
    DIALOG_ADD_TERM_CONFIRM_TIP("dialog.addTerm.confirm.tip"),
    /** Refusal of an Add term with no source text. */
    DIALOG_ADD_TERM_REQUIRED("dialog.addTerm.required"),
    /** Heading of the translating dashboard. */
    TRANSLATING_TITLE("translating.title"),
    /** Button that starts the first run from the dashboard. */
    TRANSLATING_START("translating.start"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_START}. */
    TRANSLATING_START_TIP("translating.start.tip"),
    /** Button that asks the run to pause at its next boundary. */
    TRANSLATING_PAUSE("translating.pause"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_PAUSE}. */
    TRANSLATING_PAUSE_TIP("translating.pause.tip"),
    /** Button that asks a paused run to continue. */
    TRANSLATING_RESUME("translating.resume"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_RESUME}. */
    TRANSLATING_RESUME_TIP("translating.resume.tip"),
    /** Button that asks the run to end. */
    TRANSLATING_STOP("translating.stop"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_STOP}. */
    TRANSLATING_STOP_TIP("translating.stop.tip"),
    /** Caption of the accepted-segments tile. */
    TRANSLATING_COUNT_ACCEPTED("translating.count.accepted"),
    /** Caption of the flagged-segments tile. */
    TRANSLATING_COUNT_FLAGGED("translating.count.flagged"),
    /** Caption of the remaining-segments tile. */
    TRANSLATING_COUNT_REMAINING("translating.count.remaining"),
    /** Hover explanation of the remaining-segments tile: what the count includes besides the book text. */
    TRANSLATING_COUNT_REMAINING_TIP("translating.count.remaining.tip"),
    /** Heading of the activity log card. */
    TRANSLATING_LOG_TITLE("translating.log.title"),
    /** Text shown in the activity log before the run has decided anything. */
    TRANSLATING_LOG_EMPTY("translating.log.empty"),
    /** Heading of the live chunk card. */
    LIVE_TITLE("live.title"),
    /** Target text of the segment in progress until its draft arrives. */
    LIVE_WAITING("live.waiting"),
    /** Live-row tracker; arguments: 0 the round, 1 the rounds allowed, 2 the judge's score or {@code none}, 3 the finding being repaired or {@code none}; all Strings. */
    LIVE_ROUND("live.round"),
    /** Source pane of a segment that has no visible text. */
    LIVE_EMPTY_SOURCE("live.emptySource"),
    /** Target pane of a decided segment whose target is empty. */
    LIVE_NO_TARGET("live.noTarget"),
    /** Heading of the collapsed section that shows what a draft was sent with. */
    LIVE_CONTEXT_TITLE("live.context.title"),
    /** Counts beside {@link #LIVE_CONTEXT_TITLE}; arguments: 0 preceding translations, 1 {@code yes} when a summary was sent else {@code no}, 2 names, 3 memory hits. */
    LIVE_CONTEXT_COUNTS("live.context.counts"),
    /** Hover explanation of the context section. */
    LIVE_CONTEXT_TIP("live.context.title.tip"),
    /** Context-section heading of the preceding translations. */
    LIVE_CONTEXT_PRECEDING("live.context.preceding"),
    /** Context-section heading of the rolling summary. */
    LIVE_CONTEXT_SUMMARY("live.context.summary"),
    /** Context-section line under {@link #LIVE_CONTEXT_SUMMARY} while the model has written no summary yet. */
    LIVE_CONTEXT_NO_SUMMARY("live.context.noSummary"),
    /** Context-section heading of the glossary names. */
    LIVE_CONTEXT_NAMES("live.context.names"),
    /** Context-section heading of the translation-memory hits. */
    LIVE_CONTEXT_MEMORY("live.context.memory"),
    /** Context-section line of one glossary name; arguments: 0 the term, 1 its rendering or {@code none}, 2 {@code locked} or {@code free}; all Strings. */
    LIVE_CONTEXT_TERM("live.context.term"),
    /** Context-section line of one memory hit; arguments: 0 the source, 1 the target; both Strings. */
    LIVE_CONTEXT_HIT("live.context.hit"),
    /** Context-section body when the draft was sent nothing besides its source and the style sheet. */
    LIVE_CONTEXT_EMPTY("live.context.empty"),
    /** Button in the context section that copies what the model was given. */
    LIVE_CONTEXT_COPY("live.context.copy"),
    /** Hover explanation of the control labelled by {@link #LIVE_CONTEXT_COPY}. */
    LIVE_CONTEXT_COPY_TIP("live.context.copy.tip"),
    /** The target column of a glossary name the model was given with no rendering. */
    LIVE_CONTEXT_NO_RENDERING("live.context.noRendering"),
    /** Hover explanation and spoken name of the lock mark beside a locked glossary name in the context section. */
    LIVE_CONTEXT_LOCKED("live.context.locked"),
    /** Badge on a draft the reviewer has not read yet. */
    LIVE_AWAITING_REVIEW("live.awaitingReview"),
    /** Badge with the judge's score; argument 0 is the formatted score. */
    LIVE_JUDGE("live.judge"),
    /** Heading of the live panel's block for the call in flight. */
    LIVE_CALL_CURRENT("live.call.current"),
    /** Heading of the live panel's first block once the run has ended. */
    LIVE_CALL_LAST("live.call.last"),
    /** Heading of the live panel's block for the call before the current one. */
    LIVE_CALL_PREVIOUS("live.call.previous"),
    /** Text of the live panel before the run has made a model call. */
    LIVE_CALL_NONE("live.call.none"),
    /** Attempt chip of a call; arguments: 0 the attempt, 1 the most attempts allowed; both Strings. */
    LIVE_CALL_ATTEMPT("live.call.attempt"),
    /** Clock of a call still waiting for the model; argument 0 is the {@code m:ss} time since it went out. */
    LIVE_CALL_WAITED("live.call.waited"),
    /** Clock of a finished call; argument 0 is the {@code m:ss} time the answered attempt took. */
    LIVE_CALL_TOOK("live.call.took"),
    /** Bound of a call's attempt; argument 0 is the {@code m:ss} time it may wait. */
    LIVE_CALL_TIMEOUT("live.call.timeout"),
    /** Token usage of an answered call; arguments: 0 prompt tokens or {@code none}, 1 completion tokens or {@code none}. */
    LIVE_CALL_TOKENS("live.call.tokens"),
    /** State chip of a call; argument 0 is {@code waiting}, {@code answered}, {@code failed} or {@code cancelled}. */
    LIVE_CALL_STATE("live.call.state"),
    /** Caption above the segments a call sent; argument 0 is their number. */
    LIVE_CALL_SEGMENTS("live.call.segments"),
    /** Text of a call that is about no particular segment. */
    LIVE_CALL_NO_SEGMENTS("live.call.noSegments"),
    /** Heading of the collapsed section holding the model's reply as received. */
    LIVE_CALL_REPLY("live.call.reply"),
    /** Hover explanation of the reply section. */
    LIVE_CALL_REPLY_TIP("live.call.reply.tip"),
    /** Button in the reply section that copies the model's reply. */
    LIVE_CALL_REPLY_COPY("live.call.reply.copy"),
    /** Hover explanation of Copy in the reply section. */
    LIVE_CALL_REPLY_COPY_TIP("live.call.reply.copy.tip"),
    /** Heading of the collapsed section holding the prompt's filled parts; argument 0 is their number. */
    LIVE_CALL_PROMPT("live.call.prompt"),
    /** Hover explanation of the prompt-context section. */
    LIVE_CALL_PROMPT_TIP("live.call.prompt.tip"),
    /** Divider above the parts of the system message. */
    LIVE_CALL_PROMPT_SYSTEM("live.call.prompt.system"),
    /** Divider above the parts of the user message. */
    LIVE_CALL_PROMPT_USER("live.call.prompt.user"),
    /** Button in the prompt-context section that copies the prompt's filled parts. */
    LIVE_CALL_PROMPT_COPY("live.call.prompt.copy"),
    /** Hover explanation of Copy in the prompt-context section. */
    LIVE_CALL_PROMPT_COPY_TIP("live.call.prompt.copy.tip"),
    /** Chip for what became of one segment of a call; arguments: 0 {@code adopted}, {@code fell_back}, {@code accepted} or {@code flagged}, 1 the detail or {@code none}. */
    LIVE_CALL_OUTCOME("live.call.outcome"),
    /** Words for one reason in an outcome chip; arguments: 0 the reason's code lower-cased with dashes as underscores, 1 the code as reported, shown when no words are known. */
    LIVE_CALL_DETAIL("live.call.detail"),
    /** Context-section heading of the style sheet. */
    LIVE_CONTEXT_STYLE("live.context.style"),
    /** Context-section heading of the locked glossary names. */
    LIVE_CONTEXT_LOCKED_NAMES("live.context.lockedNames"),
    /** Context-section heading of the glossary renderings the person has not confirmed. */
    LIVE_CONTEXT_SUGGESTED("live.context.suggested"),
    /** Context-section heading of the renderings the run keeps for recurring terms. */
    LIVE_CONTEXT_LEXICON("live.context.lexicon"),
    /** Context-section heading of the characters a segment names. */
    LIVE_CONTEXT_CHARACTERS("live.context.characters"),
    /** Badge of a segment accepted as drafted. */
    LIVE_PATH_DRAFT("live.path.draft"),
    /** Badge of a segment accepted after a repair. */
    LIVE_PATH_REPAIRED("live.path.repaired"),
    /** Badge of a segment reused from the translation memory. */
    LIVE_PATH_TM_REUSE("live.path.tmReuse"),
    /** Badge of a segment the person edited. */
    LIVE_PATH_USER("live.path.user"),
    /** Badge of a segment kept as source. */
    LIVE_PATH_SOURCE_KEPT("live.path.sourceKept"),
    /** Badge of a segment kept as it is because it had nothing to translate (a number, symbols). */
    LIVE_PATH_VERBATIM("live.path.verbatim"),
    /** Heading of the source pane when no source language is set. */
    LIVE_SOURCE_FALLBACK("live.source.fallback"),
    /** Heading of the target pane when no target language is set. */
    LIVE_TARGET_FALLBACK("live.target.fallback"),
    /** Line under the Translating heading: progress of a stopped run lasts only until the application closes. */
    TRANSLATING_SUBTITLE("translating.subtitle"),
    /** Caption of the tile counting segments accepted without repair. */
    TRANSLATING_COUNT_AUTO("translating.count.auto"),
    /** Caption of the tile counting segments accepted after repair. */
    TRANSLATING_COUNT_REPAIRED("translating.count.repaired"),
    /** Caption of the outcome tile counting auxiliary segments kept as source by choice. */
    TRANSLATING_COUNT_KEPT("translating.count.kept"),
    /** Caption of the outcome tile counting segments kept as they are: numbers, symbols, nothing to translate. */
    TRANSLATING_COUNT_VERBATIM("translating.count.verbatim"),
    /** Caption of the outcome tile counting accepted segments the final audit doubts. */
    TRANSLATING_COUNT_SUSPICIOUS("translating.count.suspicious"),
    /** Hover explanation of the tile labelled by {@link #TRANSLATING_COUNT_SUSPICIOUS}. */
    TRANSLATING_COUNT_SUSPICIOUS_TIP("translating.count.suspicious.tip"),
    /** Title of the ready card shown before a run exists. */
    TRANSLATING_READY_TITLE("translating.ready.title"),
    /** Label of the ready card's book row. */
    TRANSLATING_READY_BOOK("translating.ready.book"),
    /** Label of the ready card's model row. */
    TRANSLATING_READY_MODEL("translating.ready.model"),
    /** Hint on the ready card while no model is chosen, saying where to choose one. */
    TRANSLATING_READY_MODEL_HINT("translating.ready.modelHint"),
    /** Label of the ready card's review-mode row. */
    TRANSLATING_READY_REVIEW("translating.ready.review"),
    /** Label of the ready card's quality-dial row. */
    TRANSLATING_READY_DIAL("translating.ready.dial"),
    /** Label of the ready card's pending-segments row. */
    TRANSLATING_READY_PENDING("translating.ready.pending"),
    /** Name of a review mode; argument 0 is its lower-case token (unattended, assisted or manual). */
    TRANSLATING_READY_REVIEW_MODE("translating.ready.reviewMode"),
    /** Title of the card that reports what a finished or failed run decided. */
    TRANSLATING_OUTCOME_TITLE("translating.outcome.title"),
    /** The percentage of the progress line; argument 0 is the whole percent. */
    TRANSLATING_LINE_PERCENT("translating.line.percent"),
    /** The chapter part of the progress line; arguments are the section and the number of sections. */
    TRANSLATING_LINE_SECTION("translating.line.section"),
    /** The chunk part of the progress line; arguments are the chunk and the number of chunks. */
    TRANSLATING_LINE_CHUNK("translating.line.chunk"),
    /** The time-left part of the pace text; argument 0 is the duration, passed as a String. */
    TRANSLATING_PACE_LEFT("translating.pace.left"),
    /** The rate part of the pace text; argument 0 is the rate, passed as a String, with a leading ~ when estimated. */
    TRANSLATING_PACE_RATE("translating.pace.rate"),
    /** Paused banner text naming the chunk the run continues at; arguments are the chunk and the number of chunks. */
    TRANSLATING_PAUSED_TEXT("translating.pausedText"),
    /** Button that opens the flagged segments for review; argument 0 is the number of flagged segments. */
    TRANSLATING_REVIEW_FLAGGED("translating.reviewFlagged"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_REVIEW_FLAGGED}. */
    TRANSLATING_REVIEW_FLAGGED_TIP("translating.reviewFlagged.tip"),
    /**
     * The same button when the final audit doubts accepted segments too; argument 0 is the flagged count, argument 1
     * the suspicious count.
     */
    TRANSLATING_REVIEW_FLAGGED_SUSPICIOUS("translating.reviewFlaggedSuspicious"),
    /** Button that leaves a completed run for the Export step. */
    TRANSLATING_CONTINUE("translating.continue"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_CONTINUE}. */
    TRANSLATING_CONTINUE_TIP("translating.continue.tip"),
    /** Button that goes back to the Names and style step. */
    TRANSLATING_BACK("translating.back"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_BACK}. */
    TRANSLATING_BACK_TIP("translating.back.tip"),
    /** Banner title of a run state; argument 0 is the state's token (idle to failed); stopped and failed are neutral. */
    TRANSLATING_STATE_TITLE("translating.stateTitle"),
    /** Banner text of a run state; argument 0 is the same token as for {@link #TRANSLATING_STATE_TITLE}. */
    TRANSLATING_STATE_TEXT("translating.stateText"),
    /** Banner title of a provider failure; argument 0 is the failure's error code name, passed as data. */
    TRANSLATING_PROVIDER_TEXT("translating.providerText"),
    TRANSLATING_PROVIDER_TEXT_NO_HOST("translating.providerTextNoHost"),
    TRANSLATING_RETRY_NOW("translating.retryNow"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_RETRY_NOW}. */
    TRANSLATING_RETRY_NOW_TIP("translating.retryNow.tip"),
    TRANSLATING_STAY_PAUSED("translating.stayPaused"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_STAY_PAUSED}. */
    TRANSLATING_STAY_PAUSED_TIP("translating.stayPaused.tip"),
    /** Banner title of a failure reported in place, such as a destination that may not be replaced. */
    TRANSLATING_REFUSED_TITLE("translating.refusedTitle"),
    /** Banner title of a start refused because an input is missing. */
    TRANSLATING_MISSING_TITLE("translating.missingTitle"),
    /** Banner text naming the missing input; argument 0 is its token (book or model). */
    TRANSLATING_MISSING_TEXT("translating.missingText"),
    /** Banner text while the model has not answered for a while; argument 0 is the wait as {@code m:ss}, passed as a String. */
    TRANSLATING_WAITING_FOR_MODEL("translating.waitingForModel"),
    /** Banner text naming the request the run waits on; arguments: 0 the call's name, 1 the locator part, 2 the attempt, 3 the attempts allowed, 4 the attempt's wait as {@code m:ss}, 5 its timeout as {@code m:ss} or {@code none}, 6 the call's whole wait as {@code m:ss} or {@code none}; all Strings. */
    TRANSLATING_WAITING_CALL("translating.waitingCall"),
    /** Banner title once a request has waited long enough to offer a way out. */
    TRANSLATING_STUCK_TITLE("translating.stuckTitle"),
    /** Banner hint under a stuck request, naming the three ways out. */
    TRANSLATING_STUCK_HINT("translating.stuckHint"),
    /** Banner title while the run waits for the provider by itself. */
    TRANSLATING_RECOVERY_TITLE("translating.recoveryTitle"),
    /** Banner text while the run waits for the provider; arguments: 0 the time to the next try as {@code m:ss}, 1 the attempt (a number), 2 when the outage began as {@code HH:mm}, 3 the last probe's code or {@code none}. */
    TRANSLATING_RECOVERY_TEXT("translating.recoveryText"),
    /** Banner title once the provider has been down too long for the run to keep retrying by itself. */
    TRANSLATING_RECOVERY_GAVE_UP_TITLE("translating.recoveryGaveUpTitle"),
    /** Banner text once the run stopped retrying by itself; arguments: 0 when the outage began as {@code HH:mm}, 1 how many tries were made (a number). */
    TRANSLATING_RECOVERY_GAVE_UP_TEXT("translating.recoveryGaveUpText"),
    /** Banner title once a model the server never loaded again made the run stop retrying by itself. */
    TRANSLATING_MODEL_UNLOADED_TITLE("translating.modelUnloadedTitle"),
    /** Banner text for {@link #TRANSLATING_MODEL_UNLOADED_TITLE}; arguments: 0 the provider's endpoint host or {@code none}, 1 how many tries were made (a number), 2 when the outage began as {@code HH:mm}. */
    TRANSLATING_MODEL_UNLOADED_TEXT("translating.modelUnloadedText"),
    /** Button that flags the failing or stalled segment and lets the run go on. */
    TRANSLATING_SKIP_SEGMENT("translating.skipSegment"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_SKIP_SEGMENT}. */
    TRANSLATING_SKIP_SEGMENT_TIP("translating.skipSegment.tip"),
    /** Button on the stuck banner that cancels the stalled request and sends it again. */
    TRANSLATING_RETRY_CALL("translating.retryCall"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_RETRY_CALL}. */
    TRANSLATING_RETRY_CALL_TIP("translating.retryCall.tip"),
    /** Button on the stuck banner that pauses the run. */
    TRANSLATING_PAUSE_STUCK("translating.pauseStuck"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_PAUSE_STUCK}. */
    TRANSLATING_PAUSE_STUCK_TIP("translating.pauseStuck.tip"),
    /** Provider-error detail line; arguments: 0 the failed call's name, 1 the locator part, 2 the error code, 3 the pauses spent, 4 the pauses allowed; all Strings. */
    TRANSLATING_PROVIDER_DETAIL("translating.providerDetail"),
    /** Provider-error line saying what Retry now and Skip segment do; argument 0 is {@code last} when the next failure flags the segment, else {@code more}. */
    TRANSLATING_PROVIDER_RESUME("translating.providerResume"),
    /** Toggle over the activity log that shows only the lines about something that went wrong. */
    TRANSLATING_LOG_ERRORS_ONLY("translating.log.errorsOnly"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_LOG_ERRORS_ONLY}. */
    TRANSLATING_LOG_ERRORS_ONLY_TIP("translating.log.errorsOnly.tip"),
    /** Chip over the activity log offered once the person scrolled up, which stops the log following its newest line. */
    TRANSLATING_LOG_JUMP("translating.log.jump"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_LOG_JUMP}. */
    TRANSLATING_LOG_JUMP_TIP("translating.log.jump.tip"),
    /** Hover explanation of a collapsed activity-log line; argument 0 is how many times it happened in a row. */
    TRANSLATING_LOG_REPEATS("translating.log.repeats"),
    /** Heading of the connection chip's hover explanation. */
    SHELL_CONNECTION("shell.connection"),
    /** Status-bar chip before the server has answered anything. */
    SHELL_CONNECTION_UNKNOWN("shell.connection.unknown"),
    /** Status-bar chip while the server answers; argument 0 is how long ago it last answered, as {@code m:ss}, a String. */
    SHELL_CONNECTION_STEADY("shell.connection.steady"),
    /** Status-bar chip after recent failures; argument 0 is how many attempts failed in the last ten minutes. */
    SHELL_CONNECTION_UNSTEADY("shell.connection.unsteady"),
    /** Status-bar chip while the run waits for an unreachable provider and retries by itself. */
    SHELL_CONNECTION_RETRYING("shell.connection.retrying"),
    /** Hover explanation of the connection chip; arguments: 0 the model, 1 the last answer's age, 2 the timeouts, 3 the failures, 4 the drafting speed, 5 the judging speed; all Strings. */
    SHELL_CONNECTION_TIP("shell.connection.tip"),
    /** A figure the connection chip's explanation does not know yet. */
    SHELL_CONNECTION_NONE("shell.connection.none"),
    /** Button on the provider-error banner that opens the provider settings. */
    TRANSLATING_OPEN_SETTINGS("translating.openSettings"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_OPEN_SETTINGS}. */
    TRANSLATING_OPEN_SETTINGS_TIP("translating.openSettings.tip"),
    /** Heading of the export screen once a book was written. */
    EXPORT_TITLE("export.title"),
    /** Export screen heading after writing a book that still has untranslated segments. */
    EXPORT_TITLE_PARTIAL("export.title.partial"),
    /** Line under the export heading: the book is written when Export book is pressed, at any time. */
    EXPORT_SUBTITLE("export.subtitle"),
    /** Heading of the card that writes the book. */
    EXPORT_CARD_TITLE("export.card.title"),
    /** Label of the row naming the book's format. */
    EXPORT_FORMAT_LABEL("export.format.label"),
    /** Hint under the format row: the book is written in the format it was opened in. */
    EXPORT_FORMAT_HINT("export.format.hint"),
    /** Button that shows the written file in the system file manager. */
    EXPORT_REVEAL("export.reveal"),
    /** Hover explanation of the control labelled by {@link #EXPORT_REVEAL}. */
    EXPORT_REVEAL_TIP("export.reveal.tip"),
    /** Heading of the card of side files written beside the book. */
    EXPORT_ALSO_TITLE("export.also.title"),
    /** Label of the glossary side file. */
    EXPORT_AUX_GLOSSARY("export.aux.glossary"),
    /** Hover explanation of the control labelled by {@link #EXPORT_AUX_GLOSSARY}. */
    EXPORT_AUX_GLOSSARY_TIP("export.aux.glossary.tip"),
    /** Label of the bilingual-copy side file. */
    EXPORT_AUX_BILINGUAL("export.aux.bilingual"),
    /** Hover explanation of the control labelled by {@link #EXPORT_AUX_BILINGUAL}. */
    EXPORT_AUX_BILINGUAL_TIP("export.aux.bilingual.tip"),
    /** Label of the quality-report side file. */
    EXPORT_AUX_REPORT("export.aux.report"),
    /** Hover explanation of the control labelled by {@link #EXPORT_AUX_REPORT}. */
    EXPORT_AUX_REPORT_TIP("export.aux.report.tip"),
    /** Heading of the card holding the final consistency pass. */
    EXPORT_CONSISTENCY_TITLE("export.consistency.title"),
    /** Hover explanation of the control labelled by {@link #EXPORT_CONSISTENCY_TITLE}. */
    EXPORT_CONSISTENCY_TITLE_TIP("export.consistency.title.tip"),
    /** Label beside the switch that turns the final consistency pass on. */
    EXPORT_CONSISTENCY_SWITCH("export.consistency.switch"),
    /** Note under the consistency-pass switch saying what it does. */
    EXPORT_CONSISTENCY_NOTE("export.consistency.note"),
    /** Refusal beside Save to when the occupied book is the file the last export wrote. */
    EXPORT_NOTE_JUST_EXPORTED("export.note.justExported"),
    /** Note under Save to while it is empty, saying why Export book waits. */
    EXPORT_NOTE_NO_DESTINATION("export.note.noDestination"),
    /** Refusal beside Save to when the destination is the source file itself. */
    EXPORT_REFUSAL_SOURCE("export.refusal.source"),
    /** Refusal beside Save to when the destination's file type differs from the source's. */
    EXPORT_REFUSAL_TYPE("export.refusal.type"),
    /** Refusal naming an occupied book or side-file path; argument: the path. */
    EXPORT_REFUSAL_OCCUPIED("export.refusal.occupied"),
    /** Note shown while a run is translating, so the book cannot be exported yet. */
    EXPORT_NOTE_PAUSE("export.note.pause"),
    /** Counted line: segments written in the source language. */
    EXPORT_PARTIAL_SOURCE("export.partial.source"),
    /** Counted line: flagged segments written with their machine translation. */
    EXPORT_PARTIAL_FLAGGED("export.partial.flagged"),
    /** Counted line: segments kept as source by choice. */
    EXPORT_PARTIAL_KEPT("export.partial.kept"),
    /** Counted line: segments kept as they are because they had nothing to translate. */
    EXPORT_PARTIAL_VERBATIM("export.partial.verbatim"),
    /** Partial-export line counting flagged segments that kept no translation; argument 0 is the count. */
    EXPORT_PARTIAL_NO_TARGET("export.partial.noTarget"),
    /**
     * Counted line: segments written in the source language because their translation broke the formatting; argument
     * 0 is the count, argument 1 the segments' locators joined by commas.
     */
    EXPORT_SOURCE_FALLBACKS("export.sourceFallbacks"),
    /** Warning naming the flagged segments written in the source because no draft passed; arguments: count, locators. */
    EXPORT_NO_TARGET_FALLBACKS("export.noTargetFallbacks"),
    /** Note under the completion card when the report beside the book lists segments written as they are; argument 0 is the report's file name. */
    EXPORT_REPORT_FILE("export.reportFile"),
    /** Size of a written book under a kilobyte; argument 0 is the byte count. */
    EXPORT_SIZE_BYTES("export.size.bytes"),
    /** Size of a written book in kilobytes; argument 0 is the count. */
    EXPORT_SIZE_KB("export.size.kb"),
    /** Size of a written book in megabytes, one decimal at most; argument 0 is the count. */
    EXPORT_SIZE_MB("export.size.mb"),
    /** Title of the export-complete dialog. */
    EXPORT_COMPLETE_TITLE("export.complete.title"),
    /** Sentence of the export-complete dialog naming the written file; argument 0 is the file name. */
    EXPORT_COMPLETE_WRITTEN("export.complete.written"),
    /** Label of the folder row of the export-complete dialog. */
    EXPORT_COMPLETE_LOCATION("export.complete.location"),
    /** Label of the verification row of the export-complete dialog. */
    EXPORT_COMPLETE_VALIDATION("export.complete.validation"),
    /** Verification result of the export-complete dialog. */
    EXPORT_COMPLETE_VERIFIED("export.complete.verified"),
    /** Line of the export-complete dialog naming the untranslated segments written in the source; argument 0 the count. */
    EXPORT_COMPLETE_PENDING("export.complete.pending"),
    /** Label of the size row of the export-complete dialog. */
    EXPORT_COMPLETE_SIZE("export.complete.size"),
    /** Label of the row of the export-complete dialog listing the side files written beside the book. */
    EXPORT_COMPLETE_SIDE_FILES("export.complete.sideFiles"),
    /** Button that opens the written book in the program the system associates with it. */
    EXPORT_OPEN_BOOK("export.openBook"),
    /** Hover explanation of the control labelled by {@link #EXPORT_OPEN_BOOK}. */
    EXPORT_OPEN_BOOK_TIP("export.openBook.tip"),
    /** Label of the destination field beside Browse. */
    EXPORT_SAVE_TO("export.saveTo"),
    /** Hover explanation of the control labelled by {@link #EXPORT_SAVE_TO}. */
    EXPORT_SAVE_TO_TIP("export.saveTo.tip"),
    /** Export button that asks the model for the translated book's file name. */
    EXPORT_SUGGEST_NAME("export.suggestName"),
    /** Hover explanation of the control labelled by {@link #EXPORT_SUGGEST_NAME}. */
    EXPORT_SUGGEST_NAME_TIP("export.suggestName.tip"),
    /** Line shown when a name is suggested but no model is chosen. */
    EXPORT_SUGGEST_NAME_NO_MODEL("export.suggestName.noModel"),
    /** Line shown after the model's name was put in the destination. */
    EXPORT_SUGGEST_NAME_DONE("export.suggestName.done"),
    /** Hint under Save to when the suggested name kept the author's name in the source alphabet; it goes once the name is edited. */
    EXPORT_SUGGEST_NAME_AUTHOR_KEPT("export.suggestName.authorKept"),
    /** Button that opens the system save dialog for the destination. */
    EXPORT_BROWSE("export.browse"),
    /** Hover explanation of the control labelled by {@link #EXPORT_BROWSE}. */
    EXPORT_BROWSE_TIP("export.browse.tip"),
    /** Label of the switch that allows replacing an existing file at the destination. */
    EXPORT_REPLACE("export.replace"),
    /** Hover explanation of the control labelled by {@link #EXPORT_REPLACE}. */
    EXPORT_REPLACE_TIP("export.replace.tip"),
    /** The read-only format row; argument 0 is the format's display name. */
    EXPORT_FORMAT_SAME("export.format.same"),
    /** The button that writes the book. */
    EXPORT_ACTION("export.action"),
    /** Hover explanation of the control labelled by {@link #EXPORT_ACTION}. */
    EXPORT_ACTION_TIP("export.action.tip"),
    /** Caption of the tile counting the segments written translated. */
    EXPORT_TILE_WRITTEN("export.tile.written"),
    /** Caption of the tile giving the share of written segments accepted without repair. */
    EXPORT_TILE_AUTO("export.tile.auto"),
    /** Caption of the tile counting the segments a person acted on. */
    EXPORT_TILE_REVIEWED("export.tile.reviewed"),
    /** Caption of the tile that shows a passed mark once the written file was re-opened. */
    EXPORT_TILE_VALID("export.tile.valid"),
    /** Check line after an export: the file was re-opened and verified. */
    EXPORT_CHECK_REOPENED("export.check.reopened"),
    /** Check line after an export: the language metadata changed; arguments are the source and the target tags. */
    EXPORT_CHECK_LANGUAGE("export.check.language"),
    /** Check line after an export: one side file written beside the book; argument 0 is its file name. */
    EXPORT_CHECK_SIDE_FILE("export.check.sideFile"),
    /** Check line after an export: the consistency pass adjusted segments; argument 0 is the count. */
    EXPORT_CHECK_CONSISTENCY_ADJUSTED("export.check.consistency.adjusted"),
    /** Consistency pass result; argument 0 is how many paragraphs were corrected against their neighbours. */
    EXPORT_CHECK_CONSISTENCY_NEIGHBOURS("export.check.consistency.neighbours"),
    /** Check line after an export: the consistency pass ran and changed nothing. */
    EXPORT_CHECK_CONSISTENCY_NOTHING("export.check.consistency.nothing"),
    /** Check line after an export: segments still wait for a character's gender; argument 0 is their count. */
    EXPORT_CHECK_CONSISTENCY_AWAITING_GENDER("export.check.consistency.awaitingGender"),
    /** Check line after an export: the consistency pass skipped its gender step because no model was available. */
    EXPORT_CHECK_CONSISTENCY_NO_MODEL("export.check.consistency.noModel"),
    /**
     * Check line after an export: argument 0 is how many flagged or doubtful segments a fresh draft improved, argument
     * 1 how many were drafted again and kept because the new draft was no better.
     */
    EXPORT_CHECK_CONSISTENCY_RETRIED("export.check.consistency.retried"),
    /** Check line after an export: argument 0 is how many paragraphs the neighbour check found nothing to change in. */
    EXPORT_CHECK_CONSISTENCY_UNCHANGED("export.check.consistency.unchanged"),
    /**
     * Check line after an export: argument 0 is how many model answers the pass refused, argument 1 the rules they
     * broke with their counts ({@code quotes: 2, worse: 1}).
     */
    EXPORT_CHECK_CONSISTENCY_REFUSED("export.check.consistency.refused"),
    /** Words for the rule a consistency answer broke; arguments: 0 the rule's name lower-cased with dashes as underscores, 1 the name as reported, shown when no words are known. */
    EXPORT_CHECK_CONSISTENCY_RULE("export.check.consistency.rule"),
    /** Check line after an export: argument 0 is how many segments were skipped because their model call failed. */
    EXPORT_CHECK_CONSISTENCY_SKIPPED("export.check.consistency.skipped"),
    /** The forward action of the last step, always unavailable. */
    EXPORT_NEXT("export.next"),
    /** Hover explanation of the control labelled by {@link #EXPORT_NEXT}. */
    EXPORT_NEXT_TIP("export.next.tip"),
    /** Display name of the literary fiction genre offered on the Book Brief. */
    GENRE_LITERARY_FICTION("genre.literaryFiction"),
    /** Display name of the classic literature genre offered on the Book Brief. */
    GENRE_CLASSIC_LITERATURE("genre.classicLiterature"),
    /** Display name of the historical fiction genre offered on the Book Brief. */
    GENRE_HISTORICAL_FICTION("genre.historicalFiction"),
    /** Display name of the gothic novel genre offered on the Book Brief. */
    GENRE_GOTHIC_NOVEL("genre.gothicNovel"),
    /** Display name of the romance genre offered on the Book Brief. */
    GENRE_ROMANCE("genre.romance"),
    /** Display name of the historical romance genre offered on the Book Brief. */
    GENRE_HISTORICAL_ROMANCE("genre.historicalRomance"),
    /** Display name of the mystery genre offered on the Book Brief. */
    GENRE_MYSTERY("genre.mystery"),
    /** Display name of the detective fiction genre offered on the Book Brief. */
    GENRE_DETECTIVE_FICTION("genre.detectiveFiction"),
    /** Display name of the crime fiction genre offered on the Book Brief. */
    GENRE_CRIME_FICTION("genre.crimeFiction"),
    /** Display name of the thriller genre offered on the Book Brief. */
    GENRE_THRILLER("genre.thriller"),
    /** Display name of the psychological thriller genre offered on the Book Brief. */
    GENRE_PSYCHOLOGICAL_THRILLER("genre.psychologicalThriller"),
    /** Display name of the horror genre offered on the Book Brief. */
    GENRE_HORROR("genre.horror"),
    /** Display name of the science fiction genre offered on the Book Brief. */
    GENRE_SCIENCE_FICTION("genre.scienceFiction"),
    /** Display name of the fantasy genre offered on the Book Brief. */
    GENRE_FANTASY("genre.fantasy"),
    /** Display name of the epic fantasy genre offered on the Book Brief. */
    GENRE_EPIC_FANTASY("genre.epicFantasy"),
    /** Display name of the dystopian fiction genre offered on the Book Brief. */
    GENRE_DYSTOPIAN_FICTION("genre.dystopianFiction"),
    /** Display name of the adventure genre offered on the Book Brief. */
    GENRE_ADVENTURE("genre.adventure"),
    /** Display name of the western genre offered on the Book Brief. */
    GENRE_WESTERN("genre.western"),
    /** Display name of the war fiction genre offered on the Book Brief. */
    GENRE_WAR_FICTION("genre.warFiction"),
    /** Display name of the humor genre offered on the Book Brief. */
    GENRE_HUMOR("genre.humor"),
    /** Display name of the satire genre offered on the Book Brief. */
    GENRE_SATIRE("genre.satire"),
    /** Display name of the young adult genre offered on the Book Brief. */
    GENRE_YOUNG_ADULT("genre.youngAdult"),
    /** Display name of the children's literature genre offered on the Book Brief. */
    GENRE_CHILDRENS_LITERATURE("genre.childrensLiterature"),
    /** Display name of the fairy tale genre offered on the Book Brief. */
    GENRE_FAIRY_TALE("genre.fairyTale"),
    /** Display name of the short stories genre offered on the Book Brief. */
    GENRE_SHORT_STORIES("genre.shortStories"),
    /** Display name of the poetry genre offered on the Book Brief. */
    GENRE_POETRY("genre.poetry"),
    /** Display name of the drama genre offered on the Book Brief. */
    GENRE_DRAMA("genre.drama"),
    /** Display name of the memoir genre offered on the Book Brief. */
    GENRE_MEMOIR("genre.memoir"),
    /** Display name of the biography genre offered on the Book Brief. */
    GENRE_BIOGRAPHY("genre.biography"),
    /** Display name of the autobiography genre offered on the Book Brief. */
    GENRE_AUTOBIOGRAPHY("genre.autobiography"),
    /** Display name of the essay genre offered on the Book Brief. */
    GENRE_ESSAY("genre.essay"),
    /** Display name of the travel writing genre offered on the Book Brief. */
    GENRE_TRAVEL_WRITING("genre.travelWriting"),
    /** Display name of the history genre offered on the Book Brief. */
    GENRE_HISTORY("genre.history"),
    /** Display name of the philosophy genre offered on the Book Brief. */
    GENRE_PHILOSOPHY("genre.philosophy"),
    /** Display name of the religion and spirituality genre offered on the Book Brief. */
    GENRE_RELIGION_AND_SPIRITUALITY("genre.religionAndSpirituality"),
    /** Display name of the popular science genre offered on the Book Brief. */
    GENRE_POPULAR_SCIENCE("genre.popularScience"),
    /** Display name of the self-help genre offered on the Book Brief. */
    GENRE_SELF_HELP("genre.selfHelp"),
    /** Display name of the business genre offered on the Book Brief. */
    GENRE_BUSINESS("genre.business"),
    /** Display name of the true crime genre offered on the Book Brief. */
    GENRE_TRUE_CRIME("genre.trueCrime"),
    /** Display name of the graphic novel genre offered on the Book Brief. */
    GENRE_GRAPHIC_NOVEL("genre.graphicNovel"),
    /** Title of the question asked before an import discards the current translation. */
    DIALOG_REPLACE_RUN_TITLE("dialog.replaceRun.title"),
    /** Body when the run is still going: {0} is the run's file, {1} the file about to be imported. */
    DIALOG_REPLACE_RUN_ACTIVE("dialog.replaceRun.active"),
    /** Body when the run was stopped and could be resumed: {0} is the run's file, {1} the file to import. */
    DIALOG_REPLACE_RUN_STOPPED("dialog.replaceRun.stopped"),
    /** Button that leaves the run and the open book untouched. */
    DIALOG_REPLACE_RUN_KEEP("dialog.replaceRun.keep"),
    /** Hover explanation of the control labelled by {@link #DIALOG_REPLACE_RUN_KEEP}. */
    DIALOG_REPLACE_RUN_KEEP_TIP("dialog.replaceRun.keep.tip"),
    /** Button that discards the run and imports the other book. */
    DIALOG_REPLACE_RUN_CONFIRM("dialog.replaceRun.confirm"),
    /** Hover explanation of the control labelled by {@link #DIALOG_REPLACE_RUN_CONFIRM}. */
    DIALOG_REPLACE_RUN_CONFIRM_TIP("dialog.replaceRun.confirm.tip"),
    /** Line shown while the discarded run is being stopped. */
    DIALOG_REPLACE_RUN_STOPPING("dialog.replaceRun.stopping"),
    /** Title of the question asked before starting with glossary entries that have no target. */
    DIALOG_NO_TARGET_TITLE("dialog.noTarget.title"),
    /** Body of that question; argument 0 is the number of entries without a target. */
    DIALOG_NO_TARGET_TEXT("dialog.noTarget.text"),
    /** Button that closes that question and stays on the glossary. */
    DIALOG_NO_TARGET_REVIEW("dialog.noTarget.review"),
    /** Hover explanation of the control labelled by {@link #DIALOG_NO_TARGET_REVIEW}. */
    DIALOG_NO_TARGET_REVIEW_TIP("dialog.noTarget.review.tip"),
    /** Button that starts the translation without those targets. */
    DIALOG_NO_TARGET_START("dialog.noTarget.start"),
    /** Hover explanation of the control labelled by {@link #DIALOG_NO_TARGET_START}. */
    DIALOG_NO_TARGET_START_TIP("dialog.noTarget.start.tip"),
    /** Title of the question asked at Start translation when the book is told in the first person. */
    DIALOG_NARRATOR_TITLE("dialog.narrator.title"),
    /** Body of that question. */
    DIALOG_NARRATOR_TEXT("dialog.narrator.text"),
    /** Button that sets a male narrator and starts. */
    DIALOG_NARRATOR_MALE("dialog.narrator.male"),
    /** Hover explanation of the control labelled by {@link #DIALOG_NARRATOR_MALE}. */
    DIALOG_NARRATOR_MALE_TIP("dialog.narrator.male.tip"),
    /** Button that sets a female narrator and starts. */
    DIALOG_NARRATOR_FEMALE("dialog.narrator.female"),
    /** Hover explanation of the control labelled by {@link #DIALOG_NARRATOR_FEMALE}. */
    DIALOG_NARRATOR_FEMALE_TIP("dialog.narrator.female.tip"),
    /** Button that starts without stating the narrator's gender. */
    DIALOG_NARRATOR_UNKNOWN("dialog.narrator.unknown"),
    /** Hover explanation of the control labelled by {@link #DIALOG_NARRATOR_UNKNOWN}. */
    DIALOG_NARRATOR_UNKNOWN_TIP("dialog.narrator.unknown.tip"),
    /** Button that closes that question and stays on the screen. */
    DIALOG_NARRATOR_BACK("dialog.narrator.back"),
    /** Hover explanation of the control labelled by {@link #DIALOG_NARRATOR_BACK}. */
    DIALOG_NARRATOR_BACK_TIP("dialog.narrator.back.tip"),
    /** Title of the question asked before leaving a screen whose model work is still running. */
    DIALOG_LEAVE_TITLE("dialog.leave.title"),
    /** Body of the leave question; argument 0 is the name of the running work. */
    DIALOG_LEAVE_TEXT("dialog.leave.text"),
    /** Leave-question button that stops the running work and leaves. */
    DIALOG_LEAVE_STOP("dialog.leave.stop"),
    /** Hover explanation of the control labelled by {@link #DIALOG_LEAVE_STOP}. */
    DIALOG_LEAVE_STOP_TIP("dialog.leave.stop.tip"),
    /** Leave-question button that leaves and lets the work run on. */
    DIALOG_LEAVE_KEEP("dialog.leave.keep"),
    /** Hover explanation of the control labelled by {@link #DIALOG_LEAVE_KEEP}. */
    DIALOG_LEAVE_KEEP_TIP("dialog.leave.keep.tip"),
    /** Success toast after an accept; argument 0 is the segment's locator, argument 1 how many flagged segments remain. */
    REVIEW_ACCEPTED("review.accepted"),
    /** Confirmation after the person's edit was saved; argument 0 is the segment's locator. */
    REVIEW_SAVED("review.saved"),
    /** Warning toast when a review action is refused because a run is translating. */
    REVIEW_BUSY("review.busy"),
    /** Hint shown while the editor holds unsaved changes and Accept is off. */
    REVIEW_EDITING_HINT("review.editingHint"),
    /** Note beside the review actions while a run translates and the segment can only be read. */
    REVIEW_LOCKED_RUNNING("review.locked.running"),
    /** Note beside the review actions while a retry of a segment is in flight. */
    REVIEW_LOCKED_RETRY("review.locked.retry"),
    /** Note above the target of a segment that kept no translation but the model's refused reply, which it shows. */
    REVIEW_TARGET_REJECTED("review.target.rejected"),
    /** Note above the target of a segment that kept no translation at all, so its source is shown. */
    REVIEW_TARGET_SOURCE("review.target.source"),
    /** Banner text after a refused save naming the formatting tokens the edit lacks. */
    REVIEW_TOKENS_NEEDED("review.tokens.needed"),
    /** Banner text after a refused save naming formatting tokens the edit holds too often or that the source lacks. */
    REVIEW_TOKENS_EXTRA("review.tokens.extra"),
    /** Accessible name of a token chip; argument 0 is the token it inserts at the cursor. */
    REVIEW_TOKEN_INSERT("review.token.insert"),
    /** Hover explanation of the control labelled by {@link #REVIEW_TOKEN_INSERT}; argument 0 is the token. */
    REVIEW_TOKEN_INSERT_TIP("review.token.insert.tip"),
    /** Mark on a row of the All segments list whose record was kept as source by choice. */
    REVIEW_KEPT_AS_SOURCE("review.keptAsSource"),
    /** Badge of a flagged segment whose main finding concerns a name. */
    REVIEW_BADGE_NAME("review.badge.name"),
    /** Badge of a flagged segment whose main finding is a wrong language. */
    REVIEW_BADGE_WRONG_LANGUAGE("review.badge.wrongLanguage"),
    /** Badge of a flagged segment whose main finding is an omission. */
    REVIEW_BADGE_OMISSION("review.badge.omission"),
    /** Badge of a flagged segment whose main finding is any other kind or only a low judge score. */
    REVIEW_BADGE_LOW_SCORE("review.badge.lowScore"),
    /** Filter chip listing every flagged segment. */
    REVIEW_CHIP_ALL("review.chip.all"),
    /** Hover explanation of the control labelled by {@link #REVIEW_CHIP_ALL}. */
    REVIEW_CHIP_ALL_TIP("review.chip.all.tip"),
    /** Heading of the review panel. */
    REVIEW_TITLE("review.title"),
    /** Button that closes the review panel and returns to the run's progress. */
    REVIEW_BACK("review.back"),
    /** Hover explanation of the control labelled by {@link #REVIEW_BACK}. */
    REVIEW_BACK_TIP("review.back.tip"),
    /** Empty state of the review panel when no segment is listed. */
    REVIEW_EMPTY("review.empty"),
    /** Text in the review pane while the list holds segments and none is picked. */
    REVIEW_PICK("review.pick"),
    /** Mark on the target pane, which the person can edit. */
    REVIEW_EDITABLE("review.editable"),
    /** Hover explanation of the control labelled by {@link #REVIEW_EDITABLE}. */
    REVIEW_EDITABLE_TIP("review.editable.tip"),
    /** Mark beside the target pane's heading while the target cannot be edited (a run is translating or a retry is in flight). */
    REVIEW_READ_ONLY("review.readOnly"),
    /** Why Accept is off for a segment that kept no translation. */
    REVIEW_ACCEPT_NEEDS_TARGET("review.accept.needsTarget"),
    /** Caption of the line that names what the model knew when it drafted the segment. */
    REVIEW_CONTEXT("review.context"),
    /** Caption of the list of a segment's findings. */
    REVIEW_FINDINGS("review.findings"),
    /** Caption of the readable view under the two panes: formatting tokens as chips, quoted words marked. */
    REVIEW_READABLE("review.readable"),
    /** Shown under the findings heading of a flagged segment whose record holds no finding. */
    REVIEW_FINDINGS_NONE("review.findings.none"),
    /** Kind and severity of one finding; argument 0 is the kind, argument 1 the severity in lower case. */
    REVIEW_FINDING_KIND("review.finding.kind"),
    /** Muted label naming the check or the reviewer that raised a finding; argument 0 is its name. */
    REVIEW_RAISED_BY("review.raisedBy"),
    /** Heading of an edit the reviewer made and the app verified; argument 0 is the criterion's name. */
    REVIEW_EDIT_APPLIED("review.edit.applied"),
    /** The name of an edit criterion; argument 0 is its token with underscores for hyphens, such as {@code gender} or {@code invented_word}, because an ICU selector holds no hyphen. */
    REVIEW_EDIT_CRITERION("review.edit.criterion"),
    /** The replacement line of an edit that only deleted its quote. */
    REVIEW_EDIT_DELETED("review.edit.deleted"),
    /** Hover explanation of the diff of an applied edit: red is what the reviewer removed, green what it put there. */
    REVIEW_EDIT_TIP("review.edit.applied.tip"),
    /** Button that accepts the selected segment as it stands. */
    REVIEW_ACCEPT("review.accept"),
    /** Hover explanation of the control labelled by {@link #REVIEW_ACCEPT}. */
    REVIEW_ACCEPT_TIP("review.accept.tip"),
    /** Manual mode's Accept while the run waits on the segment: confirms it and lets the run go on. */
    REVIEW_ACCEPT_CONTINUE("review.acceptContinue"),
    /** Button that saves the person's edit of the target. */
    REVIEW_SAVE("review.save"),
    /** Hover explanation of the control labelled by {@link #REVIEW_SAVE}. */
    REVIEW_SAVE_TIP("review.save.tip"),
    /** Button that restores the machine target. */
    REVIEW_REVERT("review.revert"),
    /** Hover explanation of the control labelled by {@link #REVIEW_REVERT}. */
    REVIEW_REVERT_TIP("review.revert.tip"),
    /** Button that leaves the selected segment flagged and moves on to the next one. */
    REVIEW_SKIP("review.skip"),
    /** Hover explanation of the control labelled by {@link #REVIEW_SKIP}. */
    REVIEW_SKIP_TIP("review.skip.tip"),
    /** Filter chip listing flagged segments about names. */
    REVIEW_CHIP_NAMES("review.chip.names"),
    /** Hover explanation of the control labelled by {@link #REVIEW_CHIP_NAMES}. */
    REVIEW_CHIP_NAMES_TIP("review.chip.names.tip"),
    /** Filter chip listing flagged segments with omissions. */
    REVIEW_CHIP_OMISSIONS("review.chip.omissions"),
    /** Hover explanation of the control labelled by {@link #REVIEW_CHIP_OMISSIONS}. */
    REVIEW_CHIP_OMISSIONS_TIP("review.chip.omissions.tip"),
    /** Filter chip listing flagged segments with a foreign passage kept. */
    REVIEW_CHIP_FOREIGN_KEPT("review.chip.foreignKept"),
    /** Hover explanation of the control labelled by {@link #REVIEW_CHIP_FOREIGN_KEPT}. */
    REVIEW_CHIP_FOREIGN_KEPT_TIP("review.chip.foreignKept.tip"),
    /** Filter chip listing accepted segments the final audit doubts. */
    REVIEW_CHIP_SUSPICIOUS("review.chip.suspicious"),
    /** Hover explanation of the control labelled by {@link #REVIEW_CHIP_SUSPICIOUS}. */
    REVIEW_CHIP_SUSPICIOUS_TIP("review.chip.suspicious.tip"),
    /** Browse chip listing every segment, offered after an Unattended run. */
    REVIEW_CHIP_ALL_SEGMENTS("review.chip.allSegments"),
    /** Hover explanation of the control labelled by {@link #REVIEW_CHIP_ALL_SEGMENTS}. */
    REVIEW_CHIP_ALL_SEGMENTS_TIP("review.chip.allSegments.tip"),
    /** Button that retries the selected segment's translation as it is. */
    REVIEW_RETRY("review.retry"),
    /** Hover explanation of the control labelled by {@link #REVIEW_RETRY}. */
    REVIEW_RETRY_TIP("review.retry.tip"),
    /** Button that opens the card asking for a note and a lower temperature before a retry. */
    REVIEW_RETRY_NOTE("review.retryNote"),
    /** Hover explanation of the control labelled by {@link #REVIEW_RETRY_NOTE}. */
    REVIEW_RETRY_NOTE_TIP("review.retryNote.tip"),
    /** Caption of a backward-revision proposal shown beside the person's text. */
    REVIEW_PROPOSAL("review.proposal"),
    /** Button that applies the shown proposal as the person's own edit. */
    REVIEW_ACCEPT_PROPOSAL("review.acceptProposal"),
    /** Hover explanation of the control labelled by {@link #REVIEW_ACCEPT_PROPOSAL}. */
    REVIEW_ACCEPT_PROPOSAL_TIP("review.acceptProposal.tip"),
    /** Shown in place when a retry is asked for and no model is chosen. */
    REVIEW_RETRY_NO_MODEL("review.retryNoModel"),
    /** Heading of the retry-with-note card. */
    DIALOG_RETRY_TITLE("dialog.retry.title"),
    /** Caption of the note field of the retry card. */
    DIALOG_RETRY_NOTE("dialog.retry.note"),
    /** Hover explanation of the control labelled by {@link #DIALOG_RETRY_NOTE}. */
    DIALOG_RETRY_NOTE_TIP("dialog.retry.note.tip"),
    /** Check box of the retry card that lowers the sampling temperature. */
    DIALOG_RETRY_LOWER("dialog.retry.lower"),
    /** Hover explanation of the control labelled by {@link #DIALOG_RETRY_LOWER}. */
    DIALOG_RETRY_LOWER_TIP("dialog.retry.lower.tip"),
    /** Button that closes the retry card without retrying. */
    DIALOG_RETRY_CANCEL("dialog.retry.cancel"),
    /** Hover explanation of the control labelled by {@link #DIALOG_RETRY_CANCEL}. */
    DIALOG_RETRY_CANCEL_TIP("dialog.retry.cancel.tip"),
    /** Button that retries the segment with the note and the temperature chosen. */
    DIALOG_RETRY_CONFIRM("dialog.retry.confirm"),
    /** Hover explanation of the control labelled by {@link #DIALOG_RETRY_CONFIRM}. */
    DIALOG_RETRY_CONFIRM_TIP("dialog.retry.confirm.tip");

    private final String key;

    MessageKey(final String key) {
        this.key = key;
    }

    /**
     * Returns the name this constant has in the message bundles.
     *
     * @return the dotted bundle key
     */
    public String key() {
        return key;
    }
}
