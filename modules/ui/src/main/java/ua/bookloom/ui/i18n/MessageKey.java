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
    /** Why a workflow step cannot be opened yet: the languages are unset or identical. */
    NAV_LOCKED_NO_LANGUAGES("nav.locked.noLanguages"),
    /** The navigation footer naming the chosen provider and model: provider, model. */
    NAV_FOOTER_SELECTION("nav.footer.selection"),
    /** The navigation footer while no model is chosen. */
    NAV_FOOTER_NO_MODEL("nav.footer.noModel"),
    /** The navigation footer while the local AI programs are looked at, or their model question is open. */
    NAV_FOOTER_LOOKING("nav.footer.looking"),
    /** The navigation footer after a program answered with models and the person chose none. */
    NAV_FOOTER_NOT_CHOSEN("nav.footer.notChosen"),
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
    /** Name of the glossary's name scan as running model work. */
    ACTIVITY_GLOSSARY_SCAN("activity.glossaryScan"),
    /** Name of the glossary's name review as running model work. */
    ACTIVITY_GLOSSARY_REVIEW("activity.glossaryReview"),
    /** Name of the glossary's translate-only action as running model work. */
    ACTIVITY_GLOSSARY_TRANSLATE("activity.glossaryTranslate"),
    /** Name of the recurring terms' scan (text first, then the model) as running model work. */
    ACTIVITY_TERM_SCAN("activity.termScan"),
    /** Name of the recurring terms' review as running model work. */
    ACTIVITY_TERM_REVIEW("activity.termReview"),
    /** Name of the recurring terms' rendering suggestion as running model work. */
    ACTIVITY_TERM_TRANSLATE("activity.termTranslate"),
    /** Name of the automatic names pass as running model work, and the title of its progress dialog. */
    ACTIVITY_NAMES_PASS("activity.namesPass"),
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
    /** Progress dialog: label of the button that stops the work. */
    BUSY_CANCEL("busy.cancel"),
    /** Hover explanation of the progress dialog's Cancel button. */
    BUSY_CANCEL_TIP("busy.cancel.tip"),
    /** Progress dialog: what the purpose line says once a stop was asked for. */
    BUSY_CANCELLING("busy.cancelling"),
    /** Hover explanation of the disabled Cancel button while the work stops. */
    BUSY_CANCELLING_TIP("busy.cancelling.tip"),
    /** Progress dialog: shown instead of a Cancel button for work that cannot be stopped. */
    BUSY_NOT_CANCELLABLE("busy.notCancellable"),
    /** Busy card: heading of the folded section that shows the work's current model call. */
    BUSY_CALLS("busy.calls"),
    /** Progress dialog: the figures line; arguments: 0 percent, 1 steps done, 2 steps in all. */
    PROGRESS_FIGURES("progress.figures"),
    /** Progress dialog: the figures line with the time left; arguments: 0 the line, 1 "about 2 min" or the like. */
    PROGRESS_FIGURES_LEFT("progress.figures.left"),
    /** Tooltip of a progress bar that counts steps; arguments: 0 steps done, 1 steps in all. */
    PROGRESS_BAR_STEPS("progress.bar.steps"),
    /** The Translating strip bar's hover text: 0 the percent of all the work, 1 the steps done, 2 all the steps. */
    TRANSLATING_STRIP_BAR_STEPS("translating.strip.barSteps"),
    /** "How progress is counted": one kind of step; arguments: 0 its name, 1 done, 2 planned, 3 measured seconds. */
    PROGRESS_HOW_COUNTED_LINE("progress.howCounted.line"),
    /** "How progress is counted": one kind of step not timed yet; arguments: 0 its name, 1 done, 2 planned. */
    PROGRESS_HOW_COUNTED_UNTIMED("progress.howCounted.untimed"),
    /** Name of the draft steps in the note on how progress is counted. */
    PROGRESS_KIND_DRAFT("progress.kind.draft"),
    /** Name of the check steps in the note on how progress is counted. */
    PROGRESS_KIND_CHECK("progress.kind.check"),
    /** Name of the repair steps in the note on how progress is counted. */
    PROGRESS_KIND_REPAIR("progress.kind.repair"),
    /** Name of the re-ask steps in the note on how progress is counted. */
    PROGRESS_KIND_RE_ASK("progress.kind.reAsk"),
    /** Name of the resend steps in the note on how progress is counted. */
    PROGRESS_KIND_RESEND("progress.kind.resend"),
    /** Name of the chapter-summary steps in the note on how progress is counted. */
    PROGRESS_KIND_SUMMARY("progress.kind.summary"),
    /** Name of the backward-revision step in the note on how progress is counted. */
    PROGRESS_KIND_REVISION("progress.kind.revision"),
    /** Name of the stage rows in the note on how progress is counted. */
    PROGRESS_KIND_STAGE("progress.kind.stage"),
    /** Name of the model-request steps in the note on how progress is counted. */
    PROGRESS_KIND_REQUEST("progress.kind.request"),
    /** Before Start: the time of a book from a measured speed; argument 0 is "about 2 h" or the like. */
    ESTIMATE_MEASURED("estimate.measured"),
    /** Before Start: what Balanced costs against Fast when no speed was measured. */
    ESTIMATE_RELATIVE_BALANCED("estimate.relative.balanced"),
    /** Before Start: what Max costs against Fast when no speed was measured. */
    ESTIMATE_RELATIVE_MAX("estimate.relative.max"),
    /** Progress dialog: the switch that keeps the step the work is on selected. */
    PROGRESS_FOLLOW("progress.follow"),
    /** Hover explanation of the switch labelled by {@link #PROGRESS_FOLLOW}. */
    PROGRESS_FOLLOW_TIP("progress.follow.tip"),
    /** Progress dialog: the plain view of a step. */
    PROGRESS_VIEW_SIMPLE("progress.view.simple"),
    /** Hover explanation of the choice labelled by {@link #PROGRESS_VIEW_SIMPLE}. */
    PROGRESS_VIEW_SIMPLE_TIP("progress.view.simple.tip"),
    /** Progress dialog: the technical view of a step. */
    PROGRESS_VIEW_DETAILED("progress.view.detailed"),
    /** Hover explanation of the choice labelled by {@link #PROGRESS_VIEW_DETAILED}. */
    PROGRESS_VIEW_DETAILED_TIP("progress.view.detailed.tip"),
    /** Progress dialog: the name of a planned step that has not started; argument 0 is its number. */
    PROGRESS_STEP_PENDING("progress.step.pending"),
    /** Progress dialog: the result line of a step that has not started. */
    PROGRESS_STEP_NOT_STARTED("progress.step.notStarted"),
    /** Progress dialog: the result line of the running step. */
    PROGRESS_STEP_RUNNING("progress.step.running"),
    /** Progress dialog: the result line of a step that finished. */
    PROGRESS_STEP_DONE("progress.step.done"),
    /** Progress dialog: the result line of a step that failed. */
    PROGRESS_STEP_FAILED("progress.step.failed"),
    /** Progress dialog: the result line of a step done with no model call. */
    PROGRESS_STEP_NO_CALL("progress.step.noCall"),
    /** Progress dialog: the plain sentence about the selected step; arguments: 0 its state word, 1 its name. */
    PROGRESS_SAY("progress.say"),
    /** Progress dialog: heading of what the selected step read. */
    PROGRESS_INPUT("progress.input"),
    /** Progress dialog: heading of what the selected step gave back. */
    PROGRESS_OUTPUT("progress.output"),
    /** Progress dialog: what an input or output with nothing in it says. */
    PROGRESS_NOTHING("progress.nothing"),
    /** Progress dialog, Detailed: the call line; arguments: 0 number, 1 kind words, 2 segments, 3 attempt, 4 of. */
    PROGRESS_DETAIL_CALL("progress.detail.call"),
    /** Progress dialog, Detailed: the call's clock; arguments: 0 waiting or other, 1 the clock, 2 the limit or none. */
    PROGRESS_DETAIL_CLOCK("progress.detail.clock"),
    /** Progress dialog, Detailed: the call's tokens; arguments: 0 sent, 1 written, 2 tokens a second or none. */
    PROGRESS_DETAIL_TOKENS("progress.detail.tokens"),
    /** Progress dialog, Detailed: said of an older call whose full prompt is no longer kept. */
    PROGRESS_DETAIL_NOT_KEPT("progress.detail.notKept"),
    /** Progress dialog, Detailed: said of a step that made no model call. */
    PROGRESS_DETAIL_NO_CALL("progress.detail.noCall"),
    /** Progress dialog: the footer button that puts long work in the background. */
    PROGRESS_BACKGROUND("progress.background"),
    /** Hover explanation of the button labelled by {@link #PROGRESS_BACKGROUND}. */
    PROGRESS_BACKGROUND_TIP("progress.background.tip"),
    /** Progress dialog footer: what Cancel does for work that writes nothing until it ends. */
    PROGRESS_FOOTER_WRITES_NOTHING("progress.footer.writesNothing"),
    /** Progress dialog footer: what Cancel does for the export. */
    PROGRESS_FOOTER_EXPORT("progress.footer.export"),
    /** Progress dialog: heading of the folded note on how the steps are counted. */
    PROGRESS_HOW_COUNTED("progress.howCounted"),
    /** Progress dialog: the note on how the steps are counted. */
    PROGRESS_HOW_COUNTED_TEXT("progress.howCounted.text"),
    /** Title of the question asked before the export is cancelled. */
    EXPORT_CANCEL_TITLE("export.cancel.title"),
    /** Body of the cancel-the-export question. */
    EXPORT_CANCEL_TEXT("export.cancel.text"),
    /** Confirming button of the cancel-the-export question. */
    EXPORT_CANCEL_YES("export.cancel.yes"),
    /** Hover explanation of the button labelled by {@link #EXPORT_CANCEL_YES}. */
    EXPORT_CANCEL_YES_TIP("export.cancel.yes.tip"),
    /** The safe answer of the cancel-the-export question. */
    EXPORT_CANCEL_NO("export.cancel.no"),
    /** Title-bar chip of work in the background; arguments: 0 its name, 1 its percentage. */
    ACTIVITY_CHIP_HIDDEN("activity.chip.hidden"),
    /** Title-bar chip: the link that reopens hidden work's progress dialog. */
    ACTIVITY_CHIP_OPEN("activity.chip.open"),
    /** Hover explanation of the link labelled by {@link #ACTIVITY_CHIP_OPEN}. */
    ACTIVITY_CHIP_OPEN_TIP("activity.chip.open.tip"),
    /** Title of the question asked before leaving a screen whose long model work runs. */
    DIALOG_WORK_TITLE("dialog.work.title"),
    /** Body of that question; argument 0 is the work's name. */
    DIALOG_WORK_TEXT("dialog.work.text"),
    /** That question's answer that stays on the screen. */
    DIALOG_WORK_STAY("dialog.work.stay"),
    /** Hover explanation of the button labelled by {@link #DIALOG_WORK_STAY}. */
    DIALOG_WORK_STAY_TIP("dialog.work.stay.tip"),
    /** That question's answer that leaves and lets the work run on in the background. */
    DIALOG_WORK_BACKGROUND("dialog.work.background"),
    /** Hover explanation of the button labelled by {@link #DIALOG_WORK_BACKGROUND}. */
    DIALOG_WORK_BACKGROUND_TIP("dialog.work.background.tip"),
    /** Warning raised when model work is refused because long work runs in the background; argument 0 its name. */
    WORK_HIDDEN_REFUSED("work.hidden.refused"),
    /** Action of that warning, which brings the hidden work's progress back. */
    WORK_HIDDEN_SHOW("work.hidden.show"),
    /** Hover explanation of the action labelled by {@link #WORK_HIDDEN_SHOW}. */
    WORK_HIDDEN_SHOW_TIP("work.hidden.show.tip"),
    /** Action of the message raised when hidden work ends, which opens the screen that holds its results. */
    WORK_HIDDEN_RESULTS("work.hidden.results"),
    /** Hover explanation of the action labelled by {@link #WORK_HIDDEN_RESULTS}. */
    WORK_HIDDEN_RESULTS_TIP("work.hidden.results.tip"),
    /** Title of the question asked before the run is stopped. */
    CONFIRM_STOP_TITLE("confirm.stop.title"),
    /** Body of the stop-the-run question. */
    CONFIRM_STOP_TEXT("confirm.stop.text"),
    /** Confirming button of the stop-the-run question. */
    CONFIRM_STOP_YES("confirm.stop.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_STOP_YES}. */
    CONFIRM_STOP_YES_TIP("confirm.stop.yes.tip"),
    /** The safe answer of the stop question: the translation goes on. */
    CONFIRM_STOP_NO("confirm.stop.no"),
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
    /** Title-bar elapsed time; argument 0 is the formatted duration. */
    SHELL_RUN_ELAPSED("shell.run.elapsed"),
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
    /** A duration of an hour or more; argument 0 is the hours, argument 1 the two-digit minutes, both Strings. */
    DURATION_HM("duration.hm"),
    /** A duration of a minute or more, under an hour; argument 0 is the minutes, a String. */
    DURATION_M("duration.m"),
    /** A duration under a minute; argument 0 is the seconds, a String. */
    DURATION_S("duration.s"),
    /** A rounded time left under an hour; argument 0 is the minutes, a String; the text starts with "about". */
    DURATION_ABOUT_M("duration.about.m"),
    /** A rounded time left of whole hours; argument 0 is the hours, a String; the text starts with "about". */
    DURATION_ABOUT_H("duration.about.h"),
    /** A rounded time left of hours and minutes; arguments are the hours and the minutes, Strings. */
    DURATION_ABOUT_HM("duration.about.hm"),
    /** Label of a button that dismisses a dialog. */
    COMMON_CLOSE("common.close"),
    /** Hover explanation of the control labelled by {@link #COMMON_CLOSE}. */
    COMMON_CLOSE_TIP("common.close.tip"),
    /** The short label of a step's Back in a compact content area; the full label becomes its tooltip. */
    COMMON_BACK_COMPACT("common.backCompact"),
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
    /** A failure's title in the display language, selected on its lower-cased {@code ErrorCode} (argument 0). */
    ERROR_CODE_TITLE("error.code.title"),
    /** A failure's one-sentence message in the display language, selected like {@link #ERROR_CODE_TITLE}. */
    ERROR_CODE_MESSAGE("error.code.message"),
    /**
     * A failure's one-sentence advice in the display language when its details carry a
     * {@link ua.bookloom.api.FailureReason}, selected on the reason's token with hyphens as underscores (argument 0).
     */
    ERROR_REASON_MESSAGE("error.reason.message"),
    /** Label of the error dialog's control that copies the failure's safe details for support. */
    ERROR_DETAILS_COPY("error.details.copy"),
    /** Hover explanation of the control labelled by {@link #ERROR_DETAILS_COPY}. */
    ERROR_DETAILS_COPY_TIP("error.details.copy.tip"),
    /** Transient message raised when a book was opened; argument 0 is the file name, passed as a String. */
    TOAST_BOOK_OPENED("toast.bookOpened"),
    /** Transient message raised when a run finished with nothing flagged; argument 0 is the number translated. */
    TOAST_RUN_FINISHED("toast.runFinished"),
    /** Transient message raised when a run finished with flagged segments; argument 0 is accepted, argument 1 flagged. */
    TOAST_RUN_FINISHED_FLAGGED("toast.runFinishedFlagged"),
    /** Transient message raised when a run has begun: a title and a line saying progress is saved as it goes. */
    TOAST_RUN_STARTED("toast.runStarted"),
    /** Transient message raised when a chosen file could not be compared with the stored projects; argument 0 is its name. */
    TOAST_IMPORT_UNCHECKED("toast.importUnchecked"),
    /** Transient message raised when a folder is dropped on the import screen instead of a book file. */
    TOAST_IMPORT_FOLDER("toast.importFolder"),
    /** Transient message raised when the stored work was moved aside at launch; argument 0 is the new file name. */
    TOAST_STORAGE_SET_ASIDE("toast.storageSetAside"),
    /** Transient message raised when a provider's model list could not be read outside a dialog. */
    TOAST_MODEL_LIST_UNREADABLE("toast.modelListUnreadable"),
    /** Transient message raised when a project's automatic name changed; argument 0 is the new name. */
    TOAST_PROJECT_NAME_CHANGED("toast.projectNameChanged"),
    /** Transient message raised when work continued in the background ended; argument 0 is the work's name. */
    TOAST_HIDDEN_WORK_FINISHED("toast.hiddenWorkFinished"),
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
    /** Advice under the address of an OpenAI-compatible provider whose address does not end in /v1. */
    SETTINGS_FORM_ADDRESS_V1_WARNING("settings.form.address.v1"),
    /** Error under the name field: a provider needs a name. */
    SETTINGS_ERROR_NAME("settings.error.name"),
    /** Finding on the provider's name when another provider already shows the same name, ignoring case. */
    SETTINGS_ERROR_NAME_USED("settings.error.name.used"),
    /** Error under a timeout field: it must be a whole number of seconds above zero. */
    SETTINGS_ERROR_NUMBER("settings.error.number"),
    /** Error under a timeout field: it must be at most an hour. */
    SETTINGS_ERROR_TIMEOUT_TOO_LONG("settings.error.timeoutTooLong"),
    /** Error under a Models number field: it must be a whole number of tokens above zero. */
    SETTINGS_MODELS_ERROR_NUMBER("settings.models.error.number"),
    /** Models tab finding: the context window is outside the range a translation call can use. */
    SETTINGS_MODELS_ERROR_CONTEXT_RANGE("settings.models.error.contextRange"),
    /** Models tab finding: the answer cap is larger than the context window it has to fit in. */
    SETTINGS_MODELS_ERROR_LIMIT_OVER_WINDOW("settings.models.error.limitOverWindow"),
    /** Models tab: the Save button. */
    SETTINGS_MODELS_SAVE("settings.models.save"),
    /** Models tab: hover text of Save. */
    SETTINGS_MODELS_SAVE_TIP("settings.models.save.tip"),
    /** Models tab: Model */
    SETTINGS_MODELS_MODEL("settings.models.model"),
    /** Models tab: The model whose settings are shown; each model keeps its own. */
    SETTINGS_MODELS_MODEL_TIP("settings.models.model.tip"),
    /** Models tab: &#123;0&#125; · &#123;1&#125; */
    SETTINGS_MODELS_ENTRY("settings.models.entry"),
    /** Models tab: ↻ Refresh */
    SETTINGS_MODELS_REFRESH("settings.models.refresh"),
    /** Models tab: Reads the models of every provider again. */
    SETTINGS_MODELS_REFRESH_TIP("settings.models.refresh.tip"),
    /** Models tab: Each model keeps its own settings. All are optional — off means BookLoom picks the value per task. */
    SETTINGS_MODELS_EXPLAIN("settings.models.explain"),
    /** Models tab: Reading the models… */
    SETTINGS_MODELS_LISTING("settings.models.listing"),
    /** Models tab: No models yet. Add a provider on the Providers tab. */
    SETTINGS_MODELS_EMPTY("settings.models.empty"),
    /** Models tab: Found out from the program: &#123;0&#125; · structured answers: &#123;1&#125;. */
    SETTINGS_MODELS_FOUND("settings.models.found"),
    /** Models tab: it can read &#123;0, plural, one &#123;# token&#125; other &#123;# tokens&#125;&#125; at once */
    SETTINGS_MODELS_FOUND_READ("settings.models.found.read"),
    /** Models tab: &#123;0&#125; (&#123;1, number, integer&#125; loaded now) */
    SETTINGS_MODELS_FOUND_LOADED("settings.models.found.loaded"),
    /** Models tab: it did not say how much it can read at once */
    SETTINGS_MODELS_FOUND_UNKNOWN("settings.models.found.unknown"),
    /** Models tab: yes */
    SETTINGS_MODELS_FOUND_YES("settings.models.found.yes"),
    /** Models tab: no */
    SETTINGS_MODELS_FOUND_NO("settings.models.found.no"),
    /** Models tab: not checked yet — Test inference on the Providers tab checks it */
    SETTINGS_MODELS_FOUND_UNCHECKED("settings.models.found.unchecked"),
    /** Models tab: Asking the program about this model… */
    SETTINGS_MODELS_FOUND_LOADING("settings.models.found.loading"),
    /** Models tab: Temperature */
    SETTINGS_MODELS_TEMPERATURE("settings.models.temperature"),
    /** Models tab: — how varied the wording is */
    SETTINGS_MODELS_TEMPERATURE_SUB("settings.models.temperature.sub"),
    /** Models tab: Let BookLoom choose */
    SETTINGS_MODELS_TEMPERATURE_CHOOSE("settings.models.temperature.choose"),
    /** Models tab: Each task uses its own tuned value. */
    SETTINGS_MODELS_TEMPERATURE_CHOOSE_TIP("settings.models.temperature.choose.tip"),
    /** Models tab: My value */
    SETTINGS_MODELS_TEMPERATURE_VALUE("settings.models.temperature.value"),
    /** Models tab: One value for every request to this model. */
    SETTINGS_MODELS_TEMPERATURE_VALUE_TIP("settings.models.temperature.value.tip"),
    /** Models tab: Don't send */
    SETTINGS_MODELS_TEMPERATURE_OMIT("settings.models.temperature.omit"),
    /** Models tab: The temperature is left out of every request to this model. */
    SETTINGS_MODELS_TEMPERATURE_OMIT_TIP("settings.models.temperature.omit.tip"),
    /** Models tab: the slider's name. */
    SETTINGS_MODELS_TEMPERATURE_SLIDER("settings.models.temperature.slider"),
    /** Models tab: Drag to set one temperature, 0.00 to 2.00. */
    SETTINGS_MODELS_TEMPERATURE_SLIDER_TIP("settings.models.temperature.slider.tip"),
    /** Models tab: Each task uses its own tuned value: 0.0 for checks, 0.1–0.2 for translating. */
    SETTINGS_MODELS_TEMPERATURE_HINT_CHOOSE("settings.models.temperature.hint.choose"),
    /** Models tab: Every request uses this value. */
    SETTINGS_MODELS_TEMPERATURE_HINT_VALUE("settings.models.temperature.hint.value"),
    /** Models tab: Not sent at all — for models that refuse it, such as some newer OpenAI and Anthropic models. The model uses its own setting. */
    SETTINGS_MODELS_TEMPERATURE_HINT_OMIT("settings.models.temperature.hint.omit"),
    /** Models tab: Settings this model refused */
    SETTINGS_MODELS_REFUSED("settings.models.refused"),
    /** Models tab: — learned automatically */
    SETTINGS_MODELS_REFUSED_SUB("settings.models.refused.sub"),
    /** Models tab: Nothing refused */
    SETTINGS_MODELS_REFUSED_NONE("settings.models.refused.none"),
    /** Models tab: When a model refuses a setting, BookLoom stops sending it, repeats the request once without it, and lists it here. */
    SETTINGS_MODELS_REFUSED_NONE_HINT("settings.models.refused.noneHint"),
    /** Models tab: Refused on &#123;0, date, ::MMMd&#125;. BookLoom stopped sending it and repeated the request without it. */
    SETTINGS_MODELS_REFUSED_OMITTED("settings.models.refused.omitted"),
    /** Models tab: Refused on &#123;0, date, ::MMMd&#125;. BookLoom now sends &#123;1&#125; instead. */
    SETTINGS_MODELS_REFUSED_INSTEAD("settings.models.refused.instead"),
    /** Models tab: Send again */
    SETTINGS_MODELS_REFUSED_SEND("settings.models.refused.send"),
    /** Models tab: Forgets the refusal at once: the next request to this model carries the setting again. */
    SETTINGS_MODELS_REFUSED_SEND_TIP("settings.models.refused.send.tip"),
    /** Models tab: The refusal could not be forgotten. Try again. */
    SETTINGS_MODELS_REFUSED_SEND_FAILED("settings.models.refused.sendFailed"),
    /** Models tab: Use context window */
    SETTINGS_MODELS_CONTEXT("settings.models.context"),
    /** Models tab: Sets how many tokens one request may hold for this model. */
    SETTINGS_MODELS_CONTEXT_TIP("settings.models.context.tip"),
    /** Models tab: How much text one request may hold. Filled in from what the program loaded; larger is slower and uses more memory. */
    SETTINGS_MODELS_CONTEXT_HINT("settings.models.context.hint"),
    /** Models tab: num_ctx (Ollama) */
    SETTINGS_MODELS_CONTEXT_TECH("settings.models.context.tech"),
    /** Models tab: Used to size requests; this server is not sent a window. */
    SETTINGS_MODELS_CONTEXT_OPENAI("settings.models.context.openai"),
    /** Models tab: Limit answer length */
    SETTINGS_MODELS_LIMIT("settings.models.limit"),
    /** Models tab: Caps how many tokens one answer may have. */
    SETTINGS_MODELS_LIMIT_TIP("settings.models.limit.tip"),
    /** Models tab: Off (recommended): each request is limited by its expected length. */
    SETTINGS_MODELS_LIMIT_HINT("settings.models.limit.hint"),
    /** Models tab: max output tokens */
    SETTINGS_MODELS_LIMIT_TECH("settings.models.limit.tech"),
    /** Models tab: Advanced */
    SETTINGS_MODELS_ADVANCED("settings.models.advanced"),
    /** Models tab: Rarely needed options for this model. */
    SETTINGS_MODELS_ADVANCED_TIP("settings.models.advanced.tip"),
    /** Models tab: Field that carries the answer length */
    SETTINGS_MODELS_FIELD("settings.models.field"),
    /** Models tab: max_completion_tokens (standard) */
    SETTINGS_MODELS_FIELD_COMPLETION("settings.models.field.completion"),
    /** Models tab: The current standard field; newer models require it. */
    SETTINGS_MODELS_FIELD_COMPLETION_TIP("settings.models.field.completion.tip"),
    /** Models tab: max_tokens (older servers) */
    SETTINGS_MODELS_FIELD_TOKENS("settings.models.field.tokens"),
    /** Models tab: The classic field; older servers only know this one. */
    SETTINGS_MODELS_FIELD_TOKENS_TIP("settings.models.field.tokens.tip"),
    /** Models tab: Not used for Ollama, which has its own option. */
    SETTINGS_MODELS_FIELD_OLLAMA("settings.models.field.ollama"),
    /** Error under the address field: it must be an http or https address. */
    SETTINGS_ERROR_ADDRESS("settings.error.address"),
    /** Error under the chat endpoint: Ollama has its own chat protocol and takes no override. */
    SETTINGS_ERROR_OLLAMA_CHAT_PATH("settings.error.ollamaChatPath"),
    /** Error on the headers: argument 0 is the header whose value looks like a key. */
    SETTINGS_ERROR_HEADER_LITERAL("settings.error.headerLiteral"),
    /** Error under a header name: it holds a space, a colon or another character a header name may not; argument 0 is the name. */
    SETTINGS_ERROR_HEADER_NAME_INVALID("settings.error.headerNameInvalid"),
    /** Error under the key variable: a variable's name is wanted, not a key. */
    SETTINGS_ERROR_REF_NOT_NAME("settings.error.refNotName"),
    /** Error under the key variable: the sign-in needs a variable's name. */
    SETTINGS_ERROR_REF_MISSING("settings.error.refMissing"),
    /** Error under the key header: an API-key sign-in needs the header's name. */
    SETTINGS_ERROR_KEY_HEADER_MISSING("settings.error.keyHeaderMissing"),
    /** Shown in the provider list when the registry holds none. */
    SETTINGS_PROVIDERS_EMPTY("settings.providers.empty"),
    /** Heading of the Providers form while a new provider is typed. */
    SETTINGS_FORM_NEW_TITLE("settings.form.newTitle"),
    /** First line of the summary card for the provider in use; arguments are its name and model. */
    SETTINGS_SUMMARY_CURRENT("settings.summary.current"),
    /** First line of the summary card for a provider not in use; arguments are its name and model. */
    SETTINGS_SUMMARY_OTHER("settings.summary.other"),
    /** Stands in for the model on the summary card while none is chosen. */
    SETTINGS_SUMMARY_NO_MODEL("settings.summary.noModel"),
    /** Ends the summary line after a check that passed. */
    SETTINGS_SUMMARY_READY("settings.summary.ready"),
    /** Ends the summary line after a check that failed. */
    SETTINGS_SUMMARY_FAILED("settings.summary.failed"),
    /** The summary card’s speed figure; argument 0 is a measured duration. */
    SETTINGS_SUMMARY_SPEED("settings.summary.speed"),
    /** How many models the last listing offered; plural by count. */
    SETTINGS_SUMMARY_MODELS("settings.summary.models"),
    /** Label of the summary card’s Test button. */
    SETTINGS_SUMMARY_TEST("settings.summary.test"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_SUMMARY_TEST}. */
    SETTINGS_SUMMARY_TEST_TIP("settings.summary.test.tip"),
    /** Label of the summary card’s Change model button. */
    SETTINGS_SUMMARY_CHANGE_MODEL("settings.summary.changeModel"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_SUMMARY_CHANGE_MODEL}. */
    SETTINGS_SUMMARY_CHANGE_MODEL_TIP("settings.summary.changeModel.tip"),
    /** Title of the fold that holds the provider’s connection fields. */
    SETTINGS_CONNECTION("settings.connection"),
    /** Says whom the connection details are for, beside the fold’s title. */
    SETTINGS_CONNECTION_SUBTITLE("settings.connection.subtitle"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_CONNECTION}. */
    SETTINGS_CONNECTION_TIP("settings.connection.tip"),
    /** Label of the provider name field. */
    SETTINGS_FORM_NAME("settings.form.name"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_NAME}. */
    SETTINGS_FORM_NAME_TIP("settings.form.name.tip"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_KIND_LABEL}. */
    SETTINGS_KIND_LABEL_TIP("settings.kind.label.tip"),
    /** Label of the sign-in choice. */
    SETTINGS_FORM_AUTH("settings.form.auth"),
    /** Sign-in choice: no key. */
    SETTINGS_FORM_AUTH_NONE("settings.form.auth.none"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_AUTH_NONE}. */
    SETTINGS_FORM_AUTH_NONE_TIP("settings.form.auth.none.tip"),
    /** Sign-in choice: a bearer token. */
    SETTINGS_FORM_AUTH_BEARER("settings.form.auth.bearer"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_AUTH_BEARER}. */
    SETTINGS_FORM_AUTH_BEARER_TIP("settings.form.auth.bearer.tip"),
    /** Sign-in choice: a key in a named header. */
    SETTINGS_FORM_AUTH_APIKEY("settings.form.auth.apikey"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_AUTH_APIKEY}. */
    SETTINGS_FORM_AUTH_APIKEY_TIP("settings.form.auth.apikey.tip"),
    /** Label of the field naming the header that carries the key. */
    SETTINGS_FORM_KEY_HEADER("settings.form.keyHeader"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_KEY_HEADER}. */
    SETTINGS_FORM_KEY_HEADER_TIP("settings.form.keyHeader.tip"),
    /** Label of the field naming the environment variable that holds the key. */
    SETTINGS_FORM_KEY_VARIABLE("settings.form.keyVariable"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_KEY_VARIABLE}. */
    SETTINGS_FORM_KEY_VARIABLE_TIP("settings.form.keyVariable.tip"),
    /** Says that only the variable’s name is saved. */
    SETTINGS_FORM_KEY_VARIABLE_HINT("settings.form.keyVariable.hint"),
    /** Status of a key variable that has a value. */
    SETTINGS_FORM_KEY_SET("settings.form.key.set"),
    /** Status of a key variable that has no value. */
    SETTINGS_FORM_KEY_NOT_SET("settings.form.key.notSet"),
    /** Label of the base address field. */
    SETTINGS_FORM_ADDRESS("settings.form.address"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_ADDRESS}. */
    SETTINGS_FORM_ADDRESS_TIP("settings.form.address.tip"),
    /** Label of the models endpoint override field. */
    SETTINGS_FORM_MODELS_PATH("settings.form.modelsPath"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_MODELS_PATH}. */
    SETTINGS_FORM_MODELS_PATH_TIP("settings.form.modelsPath.tip"),
    /** Label of the inference endpoint override field. */
    SETTINGS_FORM_CHAT_PATH("settings.form.chatPath"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_CHAT_PATH}. */
    SETTINGS_FORM_CHAT_PATH_TIP("settings.form.chatPath.tip"),
    /** Why the inference endpoint cannot be changed for Ollama. */
    SETTINGS_FORM_CHAT_PATH_NOTE("settings.form.chatPath.note"),
    /** Label of the connect timeout field. */
    SETTINGS_FORM_CONNECT_TIMEOUT("settings.form.connectTimeout"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_CONNECT_TIMEOUT}. */
    SETTINGS_FORM_CONNECT_TIMEOUT_TIP("settings.form.connectTimeout.tip"),
    /** Label of the request timeout field. */
    SETTINGS_FORM_REQUEST_TIMEOUT("settings.form.requestTimeout"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_REQUEST_TIMEOUT}. */
    SETTINGS_FORM_REQUEST_TIMEOUT_TIP("settings.form.requestTimeout.tip"),
    /** Label of the button that lists the models again. */
    SETTINGS_FORM_MODEL_REFRESH("settings.form.model.refresh"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_MODEL_REFRESH}. */
    SETTINGS_FORM_MODEL_REFRESH_TIP("settings.form.model.refresh.tip"),
    /** How to fill the Model field. */
    SETTINGS_FORM_MODEL_HINT("settings.form.model.hint"),
    /** Label of the switch for custom headers. */
    SETTINGS_FORM_HEADERS_ON("settings.form.headers.on"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_HEADERS_ON}. */
    SETTINGS_FORM_HEADERS_ON_TIP("settings.form.headers.on.tip"),
    /** Prompt of the header name field. */
    SETTINGS_FORM_HEADERS_NAME("settings.form.headers.name"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_HEADERS_NAME}. */
    SETTINGS_FORM_HEADERS_NAME_TIP("settings.form.headers.name.tip"),
    /** Prompt of the header value field. */
    SETTINGS_FORM_HEADERS_VALUE("settings.form.headers.value"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_HEADERS_VALUE}. */
    SETTINGS_FORM_HEADERS_VALUE_TIP("settings.form.headers.value.tip"),
    /** Label of the button that adds a header row. */
    SETTINGS_FORM_HEADERS_ADD("settings.form.headers.add"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_HEADERS_ADD}. */
    SETTINGS_FORM_HEADERS_ADD_TIP("settings.form.headers.add.tip"),
    /** The glyph on the button that removes a header row. */
    SETTINGS_FORM_HEADERS_REMOVE("settings.form.headers.remove"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_HEADERS_REMOVE}. */
    SETTINGS_FORM_HEADERS_REMOVE_TIP("settings.form.headers.remove.tip"),
    /** Says a key in a header must be written as an environment reference. */
    SETTINGS_FORM_HEADERS_HINT("settings.form.headers.hint"),
    /** Label of the switch for custom models. */
    SETTINGS_FORM_MODELS_ON("settings.form.models.on"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_MODELS_ON}. */
    SETTINGS_FORM_MODELS_ON_TIP("settings.form.models.on.tip"),
    /** Prompt of the field that adds a model name. */
    SETTINGS_FORM_MODELS_ADD("settings.form.models.add"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_MODELS_ADD}. */
    SETTINGS_FORM_MODELS_ADD_TIP("settings.form.models.add.tip"),
    /** The glyph on a custom model’s chip, which removes it. */
    SETTINGS_FORM_MODELS_REMOVE("settings.form.models.remove"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_MODELS_REMOVE}; argument 0 is the name. */
    SETTINGS_FORM_MODELS_REMOVE_TIP("settings.form.models.remove.tip"),
    /** Heading of the checks block. */
    SETTINGS_CHECKS_TITLE("settings.checks.title"),
    /** Says the checks use the saved settings and change nothing. */
    SETTINGS_CHECKS_HINT("settings.checks.hint"),
    /** The mark in the footer while the form holds unsaved changes. */
    SETTINGS_FORM_DIRTY("settings.form.dirty"),
    /** Label of the footer’s Cancel button. */
    SETTINGS_FORM_CANCEL("settings.form.cancel"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_CANCEL}. */
    SETTINGS_FORM_CANCEL_TIP("settings.form.cancel.tip"),
    /** Label of the footer’s Save button. */
    SETTINGS_FORM_SAVE("settings.form.save"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_FORM_SAVE}. */
    SETTINGS_FORM_SAVE_TIP("settings.form.save.tip"),
    /** The footer’s note after a successful save. */
    SETTINGS_FORM_SAVED("settings.form.saved"),
    /** Error on the headers: argument 0 is a header written twice. */
    SETTINGS_ERROR_HEADER_DUPLICATE("settings.error.headerDuplicate"),
    /** Error on the headers: a value was typed with no header name. */
    SETTINGS_ERROR_HEADER_NAME_MISSING("settings.error.headerNameMissing"),
    /** Why Delete is off while model work runs on the provider in use. */
    SETTINGS_PROVIDER_DELETE_BUSY("settings.provider.delete.busy"),
    /** Error shown when the provider could not be saved; the form stays as typed. */
    SETTINGS_ERROR_SAVE_FAILED("settings.error.saveFailed"),
    /** Label before the selected provider's kind on its detail card. */
    SETTINGS_KIND_LABEL("settings.kind.label"),
    /** Name of the Ollama-native provider kind. */
    SETTINGS_KIND_OLLAMA("settings.kind.ollama"),
    /** Name of the OpenAI-compatible provider kind. */
    SETTINGS_KIND_OPENAI("settings.kind.openai"),
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
    /** Note under the model entry for the recommended model, Gemma 4 26B. */
    SETTINGS_ADVICE_GEMMA_26B("settings.advice.gemma26b"),
    /** Note under the model entry for Gemma 4 E4B, the smallest model that translates a book acceptably. */
    SETTINGS_ADVICE_GEMMA_E4B("settings.advice.gemmaE4b"),
    /** Warning under the model entry for Gemma 4 E2B, which is too small. */
    SETTINGS_ADVICE_GEMMA_E2B("settings.advice.gemmaE2b"),
    /** Warning under the model entry for the gpt-oss models. */
    SETTINGS_ADVICE_GPT_OSS("settings.advice.gptOss"),
    /** Note under the model entry on LM Studio: slower, no streaming, more control codes. */
    SETTINGS_ADVICE_LM_STUDIO("settings.advice.lmStudio"),
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
    /** Line under the Appearance tab saying a change takes effect without a Save button. */
    SETTINGS_INSTANT("settings.instant"),
    /** Line under the Generation tab saying when a change takes effect. */
    SETTINGS_GENERATION_INSTANT("settings.generation.instant"),
    /** Note shown on the Generation tab while a translation is under way. */
    SETTINGS_GENERATION_RUNNING("settings.generation.running"),
    /** Toast after a backup copy was written; argument 0 is the file's name. */
    SETTINGS_STORAGE_BACKUP_SAVED("settings.storage.backup.saved"),
    /** Line under the Automation tab saying when a change takes effect. */
    SETTINGS_AUTOMATION_INSTANT("settings.automation.instant"),
    /** Note shown on the Automation tab while a translation is under way. */
    SETTINGS_AUTOMATION_RUNNING("settings.automation.running"),
    /** Label of the choice of when a translation stops and asks. */
    SETTINGS_AUTOMATION_REVIEW("settings.automation.review"),
    /** Review mode choice: no stops until the end. */
    SETTINGS_AUTOMATION_REVIEW_UNATTENDED("settings.automation.review.unattended"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_REVIEW_UNATTENDED}. */
    SETTINGS_AUTOMATION_REVIEW_UNATTENDED_TIP("settings.automation.review.unattended.tip"),
    /** Review mode choice: stop at each segment that needs a look. */
    SETTINGS_AUTOMATION_REVIEW_ASSISTED("settings.automation.review.assisted"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_REVIEW_ASSISTED}. */
    SETTINGS_AUTOMATION_REVIEW_ASSISTED_TIP("settings.automation.review.assisted.tip"),
    /** Review mode choice: stop after every segment. */
    SETTINGS_AUTOMATION_REVIEW_MANUAL("settings.automation.review.manual"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_REVIEW_MANUAL}. */
    SETTINGS_AUTOMATION_REVIEW_MANUAL_TIP("settings.automation.review.manual.tip"),
    /** Line under the review mode choice. */
    SETTINGS_AUTOMATION_REVIEW_HINT("settings.automation.review.hint"),
    /** Note naming the launch variable that decides the review mode; argument 0 is its name. */
    SETTINGS_AUTOMATION_REVIEW_OVERRIDE("settings.automation.review.override"),
    /** Label of the default quality choice. */
    SETTINGS_AUTOMATION_QUALITY("settings.automation.quality"),
    /** Line under the default quality choice. */
    SETTINGS_AUTOMATION_QUALITY_HINT("settings.automation.quality.hint"),
    /** Default quality choice: Fast. */
    SETTINGS_AUTOMATION_QUALITY_FAST("settings.automation.quality.fast"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_QUALITY_FAST}. */
    SETTINGS_AUTOMATION_QUALITY_FAST_TIP("settings.automation.quality.fast.tip"),
    /** Default quality choice: Balanced. */
    SETTINGS_AUTOMATION_QUALITY_BALANCED("settings.automation.quality.balanced"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_QUALITY_BALANCED}. */
    SETTINGS_AUTOMATION_QUALITY_BALANCED_TIP("settings.automation.quality.balanced.tip"),
    /** Default quality choice: Max quality. */
    SETTINGS_AUTOMATION_QUALITY_MAX("settings.automation.quality.max"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_QUALITY_MAX}. */
    SETTINGS_AUTOMATION_QUALITY_MAX_TIP("settings.automation.quality.max.tip"),
    /** Switch: open the review desk by itself at the end of a run. */
    SETTINGS_AUTOMATION_OPEN_DESK("settings.automation.openDesk"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_OPEN_DESK}. */
    SETTINGS_AUTOMATION_OPEN_DESK_TIP("settings.automation.openDesk.tip"),
    /** Switch: the last-check default. */
    SETTINGS_AUTOMATION_LAST_CHECK("settings.automation.lastCheck"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_LAST_CHECK}. */
    SETTINGS_AUTOMATION_LAST_CHECK_TIP("settings.automation.lastCheck.tip"),
    /** Hint shown while the last-check default has not been written. */
    SETTINGS_AUTOMATION_LAST_CHECK_UNSET("settings.automation.lastCheck.unset"),
    /** Switch: keep the computer awake while a model works. */
    SETTINGS_AUTOMATION_KEEP_AWAKE("settings.automation.keepAwake"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_KEEP_AWAKE}. */
    SETTINGS_AUTOMATION_KEEP_AWAKE_TIP("settings.automation.keepAwake.tip"),
    /** Line under the keep-awake switch. */
    SETTINGS_AUTOMATION_KEEP_AWAKE_HINT("settings.automation.keepAwake.hint"),
    /** Switch: reopen the last project at launch. */
    SETTINGS_AUTOMATION_REOPEN("settings.automation.reopen"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_AUTOMATION_REOPEN}. */
    SETTINGS_AUTOMATION_REOPEN_TIP("settings.automation.reopen.tip"),
    /** Line under the reopen switch. */
    SETTINGS_AUTOMATION_REOPEN_HINT("settings.automation.reopen.hint"),
    /** Line shown when an Automation choice could not be stored. */
    SETTINGS_AUTOMATION_SAVE_FAILED("settings.automation.saveFailed"),
    /** Title of the card about the saved projects. */
    SETTINGS_STORAGE_PROJECTS("settings.storage.projects"),
    /** Row label: the file the work is saved in. */
    SETTINGS_STORAGE_SAVED_IN("settings.storage.savedIn"),
    /** Shown instead of a file when the work is kept in memory only. */
    SETTINGS_STORAGE_MEMORY_ONLY("settings.storage.memoryOnly"),
    /** Row label: the size of the saved work. */
    SETTINGS_STORAGE_SIZE("settings.storage.size"),
    /** Size and project count; argument 0 is the size, argument 1 the number of projects. */
    SETTINGS_STORAGE_SIZE_VALUE("settings.storage.sizeValue"),
    /** Row label: when the work is saved. */
    SETTINGS_STORAGE_SAVED("settings.storage.saved"),
    /** Row value: the work is saved as it goes. */
    SETTINGS_STORAGE_SAVED_VALUE("settings.storage.savedValue"),
    /** Action that opens the folder of the saved work. */
    SETTINGS_STORAGE_OPEN_FOLDER("settings.storage.openFolder"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_STORAGE_OPEN_FOLDER}. */
    SETTINGS_STORAGE_OPEN_FOLDER_TIP("settings.storage.openFolder.tip"),
    /** Action that writes a copy of the saved work. */
    SETTINGS_STORAGE_BACKUP("settings.storage.backup"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_STORAGE_BACKUP}. */
    SETTINGS_STORAGE_BACKUP_TIP("settings.storage.backup.tip"),
    /** Line saying no key is stored. */
    SETTINGS_STORAGE_KEYS("settings.storage.keys"),
    /** Title of the card about the logs. */
    SETTINGS_STORAGE_LOGS("settings.storage.logs"),
    /** Row label: the log folder. */
    SETTINGS_STORAGE_LOG_FOLDER("settings.storage.logFolder"),
    /** Row label: how many launches' logs are kept. */
    SETTINGS_STORAGE_KEPT("settings.storage.kept"),
    /** Row value; argument 0 is the number of launches kept. */
    SETTINGS_STORAGE_KEPT_VALUE("settings.storage.keptValue"),
    /** Switch: write the detailed diagnostic log. */
    SETTINGS_STORAGE_DETAILED("settings.storage.detailed"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_STORAGE_DETAILED}. */
    SETTINGS_STORAGE_DETAILED_TIP("settings.storage.detailed.tip"),
    /** Line under the detailed log switch. */
    SETTINGS_STORAGE_DETAILED_HINT("settings.storage.detailed.hint"),
    /** Line saying the switch applies at the next launch. */
    SETTINGS_STORAGE_DETAILED_RESTART("settings.storage.detailed.restart"),
    /** State line: this launch writes the detailed log. */
    SETTINGS_STORAGE_DETAILED_ON("settings.storage.detailed.on"),
    /** State line: this launch does not write the detailed log. */
    SETTINGS_STORAGE_DETAILED_OFF("settings.storage.detailed.off"),
    /** Note naming the launch variable that decides the detailed log; argument 0 is its name. */
    SETTINGS_STORAGE_DETAILED_OVERRIDE("settings.storage.detailed.override"),
    /** Note for a development run. */
    SETTINGS_STORAGE_DETAILED_DEV("settings.storage.detailed.dev"),
    /** Line shown when the detailed log switch could not be written. */
    SETTINGS_STORAGE_SAVE_FAILED("settings.storage.saveFailed"),
    /** Line shown when a Generation choice could not be stored. */
    SETTINGS_GENERATION_SAVE_FAILED("settings.generation.saveFailed"),
    /** Title of the card that lists what each quality setting does. */
    SETTINGS_GENERATION_QUALITY_TITLE("settings.generation.quality.title"),
    /** Where the quality is chosen, shown beside the title of the quality card. */
    SETTINGS_GENERATION_QUALITY_SUB("settings.generation.quality.sub"),
    /** Row of the quality table: the most segments in one request. */
    SETTINGS_GENERATION_QUALITY_BATCH("settings.generation.quality.batch"),
    /** Cell of the batch row; 0 is the segment cap. */
    SETTINGS_GENERATION_QUALITY_UP_TO("settings.generation.quality.upTo"),
    /** Row of the quality table: earlier translated segments sent with a draft. */
    SETTINGS_GENERATION_QUALITY_PRECEDING("settings.generation.quality.preceding"),
    /** Row of the quality table: directed-fix rounds. */
    SETTINGS_GENERATION_QUALITY_REPAIR("settings.generation.quality.repair"),
    /** Row of the quality table: reviewer passes. */
    SETTINGS_GENERATION_QUALITY_REVIEWS("settings.generation.quality.reviews"),
    /** Row of the quality table: the backward revision. */
    SETTINGS_GENERATION_QUALITY_BACKWARD("settings.generation.quality.backward"),
    /** Row of the quality table: the model-written summary. */
    SETTINGS_GENERATION_QUALITY_SUMMARY("settings.generation.quality.summary"),
    /** Cell of the quality table for a step that runs. */
    SETTINGS_GENERATION_QUALITY_YES("settings.generation.quality.yes"),
    /** Cell of the quality table for a step that does not run. */
    SETTINGS_GENERATION_QUALITY_NO("settings.generation.quality.no"),
    /** Label of the thinking-level choice. */
    SETTINGS_GENERATION_THINKING("settings.generation.thinking"),
    /** Thinking level: none. */
    SETTINGS_GENERATION_THINKING_OFF("settings.generation.thinking.off"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_GENERATION_THINKING_OFF}. */
    SETTINGS_GENERATION_THINKING_OFF_TIP("settings.generation.thinking.off.tip"),
    /** Thinking level: brief. */
    SETTINGS_GENERATION_THINKING_LOW("settings.generation.thinking.low"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_GENERATION_THINKING_LOW}. */
    SETTINGS_GENERATION_THINKING_LOW_TIP("settings.generation.thinking.low.tip"),
    /** Thinking level: full. */
    SETTINGS_GENERATION_THINKING_ON("settings.generation.thinking.on"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_GENERATION_THINKING_ON}. */
    SETTINGS_GENERATION_THINKING_ON_TIP("settings.generation.thinking.on.tip"),
    /** Explanation under the thinking-level choice. */
    SETTINGS_GENERATION_THINKING_HINT("settings.generation.thinking.hint"),
    /** Technical names of the thinking setting, shown with technical details on. */
    SETTINGS_GENERATION_THINKING_TECH("settings.generation.thinking.tech"),
    /** Note that a variable or property overrides a choice; 0 is its name. */
    SETTINGS_GENERATION_OVERRIDE("settings.generation.override"),
    /** Label of the sampling choice. */
    SETTINGS_GENERATION_SAMPLING("settings.generation.sampling"),
    /** Sampling mode: only the temperature is sent. */
    SETTINGS_GENERATION_SAMPLING_DEFAULTS("settings.generation.sampling.defaults"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_GENERATION_SAMPLING_DEFAULTS}. */
    SETTINGS_GENERATION_SAMPLING_DEFAULTS_TIP("settings.generation.sampling.defaults.tip"),
    /** Sampling mode: BookLoom sends its own sampling. */
    SETTINGS_GENERATION_SAMPLING_TUNED("settings.generation.sampling.tuned"),
    /** Hover explanation of the control labelled by {@link #SETTINGS_GENERATION_SAMPLING_TUNED}. */
    SETTINGS_GENERATION_SAMPLING_TUNED_TIP("settings.generation.sampling.tuned.tip"),
    /** Explanation under the sampling choice. */
    SETTINGS_GENERATION_SAMPLING_HINT("settings.generation.sampling.hint"),
    /** Title of the card listing the fixed time limits. */
    SETTINGS_GENERATION_LIMITS("settings.generation.limits"),
    /** Tag saying the limits cannot be changed. */
    SETTINGS_GENERATION_LIMITS_FIXED("settings.generation.limits.fixed"),
    /** Time-limit row: a reviewer call. */
    SETTINGS_GENERATION_LIMITS_CHECK("settings.generation.limits.check"),
    /** Time-limit row: the helper calls. */
    SETTINGS_GENERATION_LIMITS_HELPER("settings.generation.limits.helper"),
    /** Time-limit row: drafting calls. */
    SETTINGS_GENERATION_LIMITS_DRAFT("settings.generation.limits.draft"),
    /** Time-limit row: attempts per call. */
    SETTINGS_GENERATION_LIMITS_TRIES("settings.generation.limits.tries"),
    /** A limit in seconds; 0 is the number of seconds. */
    SETTINGS_GENERATION_LIMITS_SECONDS("settings.generation.limits.seconds"),
    /** The drafting limit; 0 is the longest allowed in minutes. */
    SETTINGS_GENERATION_LIMITS_DRAFT_VALUE("settings.generation.limits.draft.value"),
    /** The attempts limit; 0 is the most tries. */
    SETTINGS_GENERATION_LIMITS_TRIES_VALUE("settings.generation.limits.tries.value"),
    /** Label of the language choice. */
    APPEARANCE_LANGUAGE("appearance.language"),
    /** Hover explanation of the control labelled by {@link #APPEARANCE_LANGUAGE}. */
    APPEARANCE_LANGUAGE_TIP("appearance.language.tip"),
    /** Language option that follows the computer; 0 is that language. */
    APPEARANCE_LANGUAGE_SYSTEM("appearance.language.system"),
    /** Language option: English, written in English in both catalogues. */
    APPEARANCE_LANGUAGE_EN("appearance.language.en"),
    /** Language option: Ukrainian, written in Ukrainian in both catalogues. */
    APPEARANCE_LANGUAGE_UK("appearance.language.uk"),
    /** Note that a language choice applies after a restart. */
    APPEARANCE_LANGUAGE_NOTE("appearance.language.note"),
    /** Button that restarts BookLoom in the chosen language. */
    APPEARANCE_RESTART("appearance.restart"),
    /** Hover explanation of the control labelled by {@link #APPEARANCE_RESTART}. */
    APPEARANCE_RESTART_TIP("appearance.restart.tip"),
    /** Line shown when the restart could not start a new copy. */
    APPEARANCE_RESTART_FAILED("appearance.restart.failed"),
    /** Label of the technical-details switch. */
    APPEARANCE_TECHNICAL("appearance.technical"),
    /** Hover explanation of the control labelled by {@link #APPEARANCE_TECHNICAL}. */
    APPEARANCE_TECHNICAL_TIP("appearance.technical.tip"),
    /** Explanation under the technical-details switch. */
    APPEARANCE_TECHNICAL_HINT("appearance.technical.hint"),
    /** Line shown when an Appearance choice could not be stored. */
    APPEARANCE_SAVE_FAILED("appearance.saveFailed"),
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
    /** Label of the file-name row of the book card. */
    IMPORT_CARD_FILE("import.card.file"),
    /** Label of the format row of the book card. */
    IMPORT_CARD_FORMAT("import.card.format"),
    /** Label of the row holding the language the book declares. */
    IMPORT_CARD_LANGUAGE("import.card.language"),
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
    /** Button of a refusal that lets the person pick a different file. */
    IMPORT_CHOOSE_ANOTHER("import.refusal.chooseAnother"),
    /** Hover explanation of the control labelled by {@link #IMPORT_CHOOSE_ANOTHER}. */
    IMPORT_CHOOSE_ANOTHER_TIP("import.refusal.chooseAnother.tip"),
    /** Label of the row joining the title and the author. */
    IMPORT_CARD_TITLE_AUTHOR("import.card.titleAuthor"),
    /** Label of the row counting chapters and approximate words. */
    IMPORT_CARD_CHAPTERS("import.card.chapters"),
    /** Heading of the card that holds the book's counts and its checks. */
    IMPORT_FACTS_TITLE("import.facts.title"),
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
    /** Body of that warning; argument 0 is the code as the book wrote it. */
    IMPORT_UNRECOGNIZED_TEXT("import.unrecognized.text"),
    /** Button that moves on from an opened book to the brief. */
    IMPORT_CONTINUE("import.continue"),
    /** Hover explanation of the control labelled by {@link #IMPORT_CONTINUE}. */
    IMPORT_CONTINUE_TIP("import.continue.tip"),
    /** A word count below a thousand, exact; argument 0 is the count, a plural. */
    IMPORT_WORDS_EXACT("import.words.exact"),
    /** A word count of a thousand or more, rounded to the hundred; argument 0 is the rounded count. */
    IMPORT_WORDS_ABOUT("import.words.about"),
    /** Verdict line while a book is being read. */
    IMPORT_VERDICT_READING("import.verdict.reading"),
    /** Button beside the reading line that abandons the import. */
    IMPORT_VERDICT_CANCEL("import.verdict.cancel"),
    /** Hover explanation of the control labelled by {@link #IMPORT_VERDICT_CANCEL}. */
    IMPORT_VERDICT_CANCEL_TIP("import.verdict.cancel.tip"),
    /** Verdict line of a book that can be translated; argument 0 is the chapter count, argument 1 the worded word count. */
    IMPORT_VERDICT_READY("import.verdict.ready"),
    /** Verdict line of a book whose details and chapters name two languages; arguments are the two language names. */
    IMPORT_VERDICT_TWO_MARKS("import.verdict.twoMarks"),
    /** Verdict line of a book that is already stored; argument 0 is the project names, joined. */
    IMPORT_VERDICT_ALREADY("import.verdict.already"),
    /** Link that opens Projects on the book that is already stored. */
    IMPORT_VERDICT_ALREADY_SHOW("import.verdict.already.show"),
    /** Hover explanation of the control labelled by {@link #IMPORT_VERDICT_ALREADY_SHOW}. */
    IMPORT_VERDICT_ALREADY_SHOW_TIP("import.verdict.already.show.tip"),
    /** Verdict line of a book locked by DRM; argument 0 is the file name. */
    IMPORT_VERDICT_DRM("import.verdict.drm"),
    /** Sentence appended to the DRM line; argument 0 is the encryption scheme. */
    IMPORT_VERDICT_DRM_SCHEME("import.verdict.drm.scheme"),
    /** Verdict line of a file that could not be read; argument 0 is the file name. */
    IMPORT_VERDICT_DAMAGED("import.verdict.damaged"),
    /** Button that shows why a file could not be read. */
    IMPORT_VERDICT_DAMAGED_DETAILS("import.verdict.damaged.details"),
    /** Hover explanation of the control labelled by {@link #IMPORT_VERDICT_DAMAGED_DETAILS}. */
    IMPORT_VERDICT_DAMAGED_DETAILS_TIP("import.verdict.damaged.details.tip"),
    /** Verdict line of a file that is not a supported format; argument 0 is the file name, argument 1 what it is. */
    IMPORT_VERDICT_UNSUPPORTED("import.verdict.unsupported"),
    /** Heading of the closed fold holding the book's details. */
    IMPORT_ABOUT_TITLE("import.about"),
    /** Hover explanation of the fold labelled by {@link #IMPORT_ABOUT_TITLE}. */
    IMPORT_ABOUT_TIP("import.about.tip"),
    /** One-line summary of the closed fold: format, chapters, words, images and cover. */
    IMPORT_ABOUT_SUMMARY("import.about.summary"),
    /** Word in the fold summary for a book that has a cover. */
    IMPORT_ABOUT_COVER("import.about.cover.yes"),
    /** Text where a cover would be, and in the summary, for a book without one. */
    IMPORT_ABOUT_NO_COVER("import.about.cover.none"),
    /** Heading of the pane showing the picked segment in full. */
    IMPORT_BROWSER_TEXT("import.browser.text"),
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
    /** Banner over the brief while a translation is at work: why nothing can be changed. */
    BRIEF_READ_ONLY_BANNER("brief.readOnly.banner"),
    /** Second line of the read-only banner: when a change takes effect. */
    BRIEF_READ_ONLY_NOTE("brief.readOnly.note"),
    /** Button on the read-only banner that pauses the translation. */
    BRIEF_READ_ONLY_PAUSE("brief.readOnly.pause"),
    /** Hover explanation of the control labelled by {@link #BRIEF_READ_ONLY_PAUSE}. */
    BRIEF_READ_ONLY_PAUSE_TIP("brief.readOnly.pause.tip"),
    /** Hint under the language boxes once translating has started: the way to another language. */
    BRIEF_LANGUAGES_LOCKED("brief.languages.locked"),
    /** Label of the project name line in the languages card. */
    BRIEF_PROJECT_NAME_LABEL("brief.projectName.label"),
    /** Words after a project name that follows the languages. */
    BRIEF_PROJECT_NAME_AUTOMATIC("brief.projectName.automatic"),
    /** Words after a project name the person typed. */
    BRIEF_PROJECT_NAME_NAMED("brief.projectName.named"),
    /** Link beside the project name that opens the rename question. */
    BRIEF_PROJECT_NAME_RENAME("brief.projectName.rename"),
    /** Hover explanation of the control labelled by {@link #BRIEF_PROJECT_NAME_RENAME}. */
    BRIEF_PROJECT_NAME_RENAME_TIP("brief.projectName.rename.tip"),
    /** Button of the same-pair question that keeps the previous language: the language's name. */
    BRIEF_SAME_PAIR_KEEP("brief.samePair.keep"),
    /** Button of the same-pair question when no language was chosen before. */
    BRIEF_SAME_PAIR_KEEP_PREVIOUS("brief.samePair.keepPrevious"),
    /** The name of an automatic source language in the keep button. */
    BRIEF_SAME_PAIR_AUTO("brief.samePair.auto"),
    /** Hover explanation of the line labelled by {@link #ESTIMATE_MEASURED}. */
    ESTIMATE_MEASURED_TIP("estimate.measured.tip"),
    /** Heading of the dialog that fills in the brief from the book. */
    BRIEF_DIALOG_TITLE("brief.dialog.title"),
    /** Line under the heading of the fill-in dialog. */
    BRIEF_DIALOG_PURPOSE("brief.dialog.purpose"),
    /** Footer line of the fill-in dialog: nothing is written before Apply. */
    BRIEF_DIALOG_WRITES_NOTHING("brief.dialog.writesNothing"),
    /** Button of the fill-in dialog that writes the decided fields to the brief. */
    BRIEF_APPLY("brief.apply"),
    /** Hover explanation of the control labelled by {@link #BRIEF_APPLY}. */
    BRIEF_APPLY_TIP("brief.apply.tip"),
    /** Heading of the group of decided fields in the fill-in dialog. */
    BRIEF_DECIDES_GROUP("brief.decides.group"),
    /** Heading of the group of steps in the fill-in dialog. */
    BRIEF_STEPS_GROUP("brief.steps.group"),
    /** Row of the decided fields: the genre. */
    BRIEF_DECIDES_GENRE("brief.decides.genre"),
    /** Row of the decided fields: the register. */
    BRIEF_DECIDES_REGISTER("brief.decides.register"),
    /** Row of the decided fields: who tells the story. */
    BRIEF_DECIDES_NARRATOR("brief.decides.narrator"),
    /** Row of the decided fields: the readers and the voice. */
    BRIEF_DECIDES_READERS("brief.decides.readers"),
    /** How many of the samples read gave the decided value: agreeing, samples. */
    BRIEF_DECIDES_AGREE("brief.decides.agree"),
    /** Mark on a tally shown before the last step has decided. */
    BRIEF_DECIDES_SO_FAR("brief.decides.soFar"),
    /** Chip on a decided field the samples did not agree on. */
    BRIEF_DECIDES_UNCERTAIN("brief.decides.uncertain"),
    /** Line of a field no sample has answered yet. */
    BRIEF_DECIDES_PENDING("brief.decides.pending"),
    /** Mark on a sample's answer that quoted nothing from the book. */
    BRIEF_DECIDES_NO_QUOTE("brief.decides.noQuote"),
    /** Note under the per-sample answers. */
    BRIEF_DECIDES_NOTE("brief.decides.note"),
    /** Column heading of the per-sample answers: the sample. */
    BRIEF_DECIDES_COL_SAMPLE("brief.decides.col.sample"),
    /** Column heading of the per-sample answers: the answer. */
    BRIEF_DECIDES_COL_ANSWER("brief.decides.col.answer"),
    /** Column heading of the per-sample answers: the quote. */
    BRIEF_DECIDES_COL_QUOTE("brief.decides.col.quote"),
    /** Step of the fill-in dialog: what the model knows of the book. */
    BRIEF_STEP_KNOWLEDGE("brief.step.knowledge"),
    /** Step of the fill-in dialog: one sample read; the sample's number. */
    BRIEF_STEP_SAMPLE("brief.step.sample"),
    /** Step of the fill-in dialog: the decision over every sample. */
    BRIEF_STEP_SYNTHESIS("brief.step.synthesis"),
    /** Sentence about the deciding step. */
    BRIEF_STEP_SYNTHESIS_SUB("brief.step.synthesis.sub"),
    /** Line shown after the person stopped the suggestion. */
    BRIEF_SUGGEST_STOPPED("brief.suggest.stopped"),
    /** Line under a suggested field: the book's words the model quoted for it and how many samples agree. */
    BRIEF_EVIDENCE_QUOTED("brief.evidence.quoted"),
    /** Line under a suggested field that needed no quote: how many samples agree. */
    BRIEF_EVIDENCE_AGREED("brief.evidence.agreed"),
    /** Hover explanation of the line labelled by {@link #BRIEF_EVIDENCE_AGREED}. */
    BRIEF_EVIDENCE_AGREED_TIP("brief.evidence.agreed.tip"),
    /** Line under a suggested field that stands on what the model knows of the book. */
    BRIEF_EVIDENCE_KNOWLEDGE("brief.evidence.knowledge"),
    /** Line under a suggested field the code counted over the whole book, such as the narrator. */
    BRIEF_EVIDENCE_MEASURED("brief.evidence.measured"),
    /** Chip beside a field the samples of the book did not agree on. */
    BRIEF_EVIDENCE_UNCERTAIN("brief.evidence.uncertain"),
    /** Hover explanation of the chip labelled by {@link #BRIEF_EVIDENCE_UNCERTAIN}. */
    BRIEF_EVIDENCE_UNCERTAIN_TIP("brief.evidence.uncertain.tip"),
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
    /** The first entry of the source list: no language is named and the model identifies it from the text. */
    BRIEF_SOURCE_AUTO("brief.source.auto"),
    /** The model's read of an Auto source, with the language's name as its argument. */
    BRIEF_SOURCE_PROPOSAL("brief.source.proposal"),
    /** Button that takes the proposed source language. */
    BRIEF_SOURCE_USE("brief.source.use"),
    /** Hover explanation of the control labelled by {@link #BRIEF_SOURCE_USE}. */
    BRIEF_SOURCE_USE_TIP("brief.source.use.tip"),
    /** Note under the source box while the source is Auto. */
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
    /** The narrator gender the style suggestion proved from the book, marked so it is not taken for the person's. */
    BRIEF_NARRATOR_GENDER_SUGGESTED("brief.narrator.gender.suggested"),
    /** Hover explanation of the line labelled by {@link #BRIEF_NARRATOR_GENDER_SUGGESTED}. */
    BRIEF_NARRATOR_GENDER_SUGGESTED_TIP("brief.narrator.gender.suggested.tip"),
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
    /** Heading of the card holding the translated title and author. */
    EXPORT_BOOK_CARD("export.bookCard.title"),
    /** Label of the translated title field. */
    EXPORT_BOOK_TITLE("export.bookTitle"),
    /** Hover explanation of the control labelled by {@link #EXPORT_BOOK_TITLE}. */
    EXPORT_BOOK_TITLE_TIP("export.bookTitle.tip"),
    /** Label of the translated author field. */
    EXPORT_BOOK_AUTHOR("export.bookAuthor"),
    /** Hover explanation of the control labelled by {@link #EXPORT_BOOK_AUTHOR}. */
    EXPORT_BOOK_AUTHOR_TIP("export.bookAuthor.tip"),
    /** Reason line under the original title: it comes from the book's details. */
    EXPORT_BOOK_TITLE_FROM_DETAILS("export.bookTitle.fromDetails"),
    /** Reason line under the original title: it comes from a Markdown book's front matter. */
    EXPORT_BOOK_TITLE_FROM_FRONT_MATTER("export.bookTitle.fromFrontMatter"),
    /** Reason line under the original title: it comes from a Markdown book's first heading. */
    EXPORT_BOOK_TITLE_FROM_HEADING("export.bookTitle.fromHeading"),
    /** Reason line under the original title: the book names none and its file name stands in. */
    EXPORT_BOOK_TITLE_FROM_FILE_NAME("export.bookTitle.fromFileName"),
    /** Hint under the title and author fields saying when they fill and that an edit reaches every place. */
    EXPORT_BOOK_TITLE_HINT("export.bookTitle.hint"),
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
    /** Title shown for a structure node the book gives none. */
    STRUCTURE_UNTITLED("structure.untitled"),
    /**
     * The segments a run translates, beneath the total: argument 0 the book-text segments, 1 the other texts (titles,
     * image descriptions, contents, book details), 2 their sum, each an Integer.
     */
    STRUCTURE_RUN_TOTAL("structure.runTotal"),
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
    /** The reading pane's line without the technical figures: 0 is the locator, 1 the kind. */
    STRUCTURE_PREVIEW_META_PLAIN("structure.preview.metaPlain"),
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
    /** The facts card's one plain line for a book whose round trip and identifiers both passed. */
    STRUCTURE_CHECK_PLAIN("structure.check.plain"),
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
    /** Button that returns from names and style to the book brief. */
    NAMES_STYLE_BACK("namesStyle.back"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_BACK}. */
    NAMES_STYLE_BACK_TIP("namesStyle.back.tip"),
    /** Button that starts the run from names and style. */
    NAMES_STYLE_START("namesStyle.start"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_START}. */
    NAMES_STYLE_START_TIP("namesStyle.start.tip"),
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
    /** Button of the glossary card's model action bar: scan for more names. */
    NAMES_STYLE_MODEL_SCAN("namesStyle.modelScan"),
    /** Button of the glossary card's model action bar: review the rows. */
    NAMES_STYLE_REVIEW("namesStyle.review"),
    /** Button of the glossary card's model action bar: suggest a target for each row that has none. */
    NAMES_STYLE_TRANSLATE("namesStyle.translate"),
    /** Button of the shared model action bar of both cards: stop the action under way. */
    BAR_STOP("namesStyle.bar.stop"),
    /** Hover explanation of the control labelled by {@link #BAR_STOP}. */
    BAR_STOP_TIP("namesStyle.bar.stop.tip"),
    /** Hover explanation of the glossary card's {@link #NAMES_STYLE_MODEL_SCAN} button. */
    NAMES_STYLE_MODEL_SCAN_TIP("namesStyle.modelScan.tip"),
    /** Hover explanation of the glossary card's {@link #NAMES_STYLE_REVIEW} button. */
    NAMES_STYLE_REVIEW_TIP("namesStyle.review.tip"),
    /** Hover explanation of the glossary card's {@link #NAMES_STYLE_TRANSLATE} button. */
    NAMES_STYLE_TRANSLATE_TIP("namesStyle.translate.tip"),
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
    /** Accessible name of the three-dot menu that holds Import and Export while the card is narrow. */
    NAMES_STYLE_MORE("namesStyle.more"),
    /** Hover explanation of the control named by {@link #NAMES_STYLE_MORE}. */
    NAMES_STYLE_MORE_TIP("namesStyle.more.tip"),
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
    NAMES_STYLE_GENDER_NO_EVIDENCE("namesStyle.gender.noEvidence"),
    /** Glossary column of the lock switch. */
    NAMES_STYLE_COLUMN_LOCKED("namesStyle.column.locked"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_COLUMN_LOCKED}. */
    NAMES_STYLE_COLUMN_LOCKED_TIP("namesStyle.column.locked.tip"),
    /** Placeholder of a target field that is empty. */
    NAMES_STYLE_NO_TARGET("namesStyle.noTarget"),
    /** Accessible name of the row's remove action. */
    NAMES_STYLE_REMOVE("namesStyle.remove"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_REMOVE}. */
    NAMES_STYLE_REMOVE_TIP("namesStyle.remove.tip"),
    /** Accessible name of the last column of either table, which has no header text and holds each row's remove cross. */
    NAMES_STYLE_COLUMN_REMOVE("namesStyle.column.remove"),
    /** Hover explanation of the control named by {@link #NAMES_STYLE_COLUMN_REMOVE}. */
    NAMES_STYLE_COLUMN_REMOVE_TIP("namesStyle.column.remove.tip"),
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
    /** Line shown when a model scan or review of recurring terms is asked for and no model is chosen. */
    RECURRING_NO_MODEL_TERMS("namesStyle.recurring.noModelTerms"),
    /** Button of the recurring-terms card's model action bar: scan the text, then the model. */
    RECURRING_MODEL_SCAN("namesStyle.recurring.modelScan"),
    /** Hover explanation of the control labelled by {@link #RECURRING_MODEL_SCAN}. */
    RECURRING_MODEL_SCAN_TIP("namesStyle.recurring.modelScan.tip"),
    /** Button of the recurring-terms card's model action bar: review the terms. */
    RECURRING_MODEL_REVIEW("namesStyle.recurring.modelReview"),
    /** Hover explanation of the control labelled by {@link #RECURRING_MODEL_REVIEW}. */
    RECURRING_MODEL_REVIEW_TIP("namesStyle.recurring.modelReview.tip"),
    /** Button of the recurring-terms card's model action bar: suggest a rendering for each term that has none. */
    RECURRING_SUGGEST("namesStyle.recurring.suggest"),
    /** Hover explanation of the control labelled by {@link #RECURRING_SUGGEST}. */
    RECURRING_SUGGEST_TIP("namesStyle.recurring.suggest.tip"),
    /** Placeholder of the field where a word or title is typed. */
    RECURRING_ADD_PROMPT("namesStyle.recurring.add.prompt"),
    /** Hover explanation of the field whose placeholder is {@link #RECURRING_ADD_PROMPT}. */
    RECURRING_ADD_PROMPT_TIP("namesStyle.recurring.add.prompt.tip"),
    /** Button that opens the card asking for a recurring term. */
    RECURRING_ADD("namesStyle.recurring.add"),
    /** Hover explanation of the control labelled by {@link #RECURRING_ADD}. */
    RECURRING_ADD_TIP("namesStyle.recurring.add.tip"),
    /** Button that imports recurring terms from a CSV file. */
    RECURRING_IMPORT("namesStyle.recurring.import"),
    /** Hover explanation of the control labelled by {@link #RECURRING_IMPORT}. */
    RECURRING_IMPORT_TIP("namesStyle.recurring.import.tip"),
    /** Button that exports the recurring terms to a CSV file. */
    RECURRING_EXPORT("namesStyle.recurring.export"),
    /** Hover explanation of the control labelled by {@link #RECURRING_EXPORT}. */
    RECURRING_EXPORT_TIP("namesStyle.recurring.export.tip"),
    /** Line reported after the recurring terms were written to a CSV file; argument 0 is the file name. */
    RECURRING_EXPORT_DONE("namesStyle.recurring.export.done"),
    /** Title of the card that asks for a recurring term. */
    DIALOG_ADD_RECURRING_TITLE("dialog.addRecurring.title"),
    /** Line under the title of the card that asks for a recurring term. */
    DIALOG_ADD_RECURRING_SUBTITLE("dialog.addRecurring.subtitle"),
    /** Button that adds the typed recurring term. */
    DIALOG_ADD_RECURRING_CONFIRM("dialog.addRecurring.confirm"),
    /** Hover explanation of the control labelled by {@link #DIALOG_ADD_RECURRING_CONFIRM}. */
    DIALOG_ADD_RECURRING_CONFIRM_TIP("dialog.addRecurring.confirm.tip"),
    /** Header of the source-term column. */
    RECURRING_COLUMN_TERM("namesStyle.recurring.column.term"),
    /** Hover explanation of the column labelled by {@link #RECURRING_COLUMN_TERM}. */
    RECURRING_COLUMN_TERM_TIP("namesStyle.recurring.column.term.tip"),
    /** Header of the column holding the rendering kept for each term. */
    RECURRING_COLUMN_TARGET("namesStyle.recurring.column.target"),
    /** Hover explanation of the column labelled by {@link #RECURRING_COLUMN_TARGET}. */
    RECURRING_COLUMN_TARGET_TIP("namesStyle.recurring.column.target.tip"),
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
    /** Line after a recurring-terms scan that ran its text step only because no model is chosen. */
    RECURRING_MODEL_SKIPPED("namesStyle.recurring.modelSkipped"),
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
    /** Why the translate-only action did not start. */
    NAMES_STYLE_NO_MODEL_TRANSLATE("namesStyle.noModelTranslate"),
    /** Caption of the button that prepares the names and the recurring terms in one pass. */
    NAMES_STYLE_AUTO("namesStyle.auto"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_AUTO}. */
    NAMES_STYLE_AUTO_TIP("namesStyle.auto.tip"),
    /** The estimate under that button; arguments are the fewest and most minutes and the dial's name. */
    NAMES_STYLE_AUTO_ABOUT("namesStyle.auto.about"),
    PROGRESS_NAMES_PURPOSE("progress.namesPurpose"),
    /** Heading of the progress dialog of an export that runs the last check before saving. */
    PROGRESS_EXPORT_TITLE("progress.exportTitle"),
    /** Purpose line of the progress dialog of an export that runs the last check before saving. */
    PROGRESS_EXPORT_PURPOSE("progress.exportPurpose"),
    RECURRING_HELP_LINK("namesStyle.recurring.helpLink"),
    RECURRING_HELP_LINK_TIP("namesStyle.recurring.helpLink.tip"),
    RECURRING_HELP("namesStyle.recurring.help"),
    NAMES_STYLE_AUTO_ESTIMATE("namesStyle.auto.estimate"),
    /** The estimate when the fewest and most minutes are the same; arguments are the minutes and the dial's name. */
    NAMES_STYLE_AUTO_ESTIMATE_ONE("namesStyle.auto.estimateOne"),
    /** Why the automatic pass did not start. */
    NAMES_STYLE_AUTO_NO_MODEL("namesStyle.auto.noModel"),
    /** Line after the automatic pass; argument 0 is how many model calls it made. */
    NAMES_STYLE_AUTO_DONE("namesStyle.auto.done"),
    /** Line after the glossary's translate-only action; argument 0 is how many names took a suggested target. */
    NAMES_STYLE_TRANSLATED("namesStyle.translated"),
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
    /** Button that turns every suggested character gender into the person's. */
    NAMES_STYLE_CONFIRM_GENDERS("namesStyle.confirmGenders"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_CONFIRM_GENDERS}. */
    NAMES_STYLE_CONFIRM_GENDERS_TIP("namesStyle.confirmGenders.tip"),
    /** Accessible name of a row's action that confirms its suggested target. */
    NAMES_STYLE_ACCEPT("namesStyle.accept"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_ACCEPT}. */
    NAMES_STYLE_ACCEPT_TIP("namesStyle.accept.tip"),
    /** The short badge on a target the model suggested. */
    NAMES_STYLE_SUGGESTED_BADGE("namesStyle.suggestedBadge"),
    /** Hover explanation of the badge labelled by {@link #NAMES_STYLE_SUGGESTED_BADGE}. */
    NAMES_STYLE_SUGGESTED_BADGE_TIP("namesStyle.suggestedBadge.tip"),
    /** Result of an import; argument 0 is the row count, passed as an Integer. */
    NAMES_STYLE_IMPORT_DONE("namesStyle.import.done"),
    /** Lines an import could not read; argument 0 is the joined line numbers, argument 1 their count. */
    NAMES_STYLE_IMPORT_MALFORMED("namesStyle.import.malformed"),
    /** Lines an import refused as locked without a target; argument 0 is the joined line numbers, argument 1 their count. */
    NAMES_STYLE_IMPORT_REFUSED("namesStyle.import.refused"),
    /** How many rows of an imported file a later row of the same term replaced (argument 0). */
    NAMES_STYLE_IMPORT_DUPLICATES("namesStyle.import.duplicates"),
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

    /** Heading of the activity log card. */
    TRANSLATING_LOG_TITLE("translating.log.title"),
    /** Text shown in the activity log before the run has decided anything. */
    TRANSLATING_LOG_EMPTY("translating.log.empty"),

    /** Source pane of a segment that has no visible text. */
    LIVE_EMPTY_SOURCE("live.emptySource"),

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

    /** Badge with the judge's score; argument 0 is the formatted score. */
    LIVE_JUDGE("live.judge"),

    /** Text of the live panel before the run has made a model call. */
    LIVE_CALL_NONE("live.call.none"),

    /** Clock of a finished call; argument 0 is the {@code m:ss} time the answered attempt took. */
    LIVE_CALL_TOOK("live.call.took"),

    /** Token usage of an answered call; arguments: 0 prompt tokens or {@code none}, 1 completion tokens or {@code none}. */
    LIVE_CALL_TOKENS("live.call.tokens"),

    /** Heading of the section holding the user message a call about no segment sent. */
    LIVE_CALL_INPUT("live.call.input"),
    /** Hover explanation of the input section. */
    LIVE_CALL_INPUT_TIP("live.call.input.tip"),
    /** Button in the input section that copies the message. */
    LIVE_CALL_INPUT_COPY("live.call.input.copy"),
    /** Hover explanation of Copy in the input section. */
    LIVE_CALL_INPUT_COPY_TIP("live.call.input.copy.tip"),
    /** Line where the reply will stand while the call waits for it. */
    LIVE_CALL_REPLY_WAITING("live.call.replyWaiting"),
    /** Line where the reply would stand when the call ended without one. */
    LIVE_CALL_REPLY_NONE("live.call.replyNone"),
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
    /** Divider above a model-written message of the prompt, such as a worked example. */
    LIVE_CALL_PROMPT_ASSISTANT("live.call.prompt.assistant"),
    /** Button in the prompt-context section that copies the prompt's filled parts. */
    LIVE_CALL_PROMPT_COPY("live.call.prompt.copy"),
    /** Hover explanation of Copy in the prompt-context section. */
    LIVE_CALL_PROMPT_COPY_TIP("live.call.prompt.copy.tip"),
    /** Heading of the section while it shows the whole messages; argument 0 is their number. */
    LIVE_CALL_PROMPT_FULL_TITLE("live.call.prompt.fullTitle"),
    /** Hover explanation of the raw block that holds the messages as sent. */
    LIVE_CALL_PROMPT_FULL_TITLE_TIP("live.call.prompt.fullTitle.tip"),
    /** Button in the raw messages block that copies the messages exactly as sent. */
    LIVE_CALL_PROMPT_SENT_COPY("live.call.prompt.sentCopy"),
    /** Hover explanation of Copy in the raw messages block. */
    LIVE_CALL_PROMPT_SENT_COPY_TIP("live.call.prompt.sentCopy.tip"),
    /** Heading of the reply section when the reply was read as items; argument 0 is their number. */
    LIVE_CALL_REPLY_ITEMS("live.call.replyItems"),
    /** Heading of the collapsed block holding the reply exactly as received. */
    LIVE_CALL_REPLY_RAW("live.call.replyRaw"),
    /** Column heading: a batch item's id. */
    LIVE_CALL_COL_ID("live.call.col.id"),
    /** Column heading: the segment an item stands for. */
    LIVE_CALL_COL_SEGMENT("live.call.col.segment"),
    /** Column heading: the text of a batch item in a prompt. */
    LIVE_CALL_COL_TEXT("live.call.col.text"),
    /** Column heading: the text a reply returned for an item. */
    LIVE_CALL_COL_RETURNED("live.call.col.returned"),
    /** Column heading: what the reply did with an item. */
    LIVE_CALL_COL_RESULT("live.call.col.result"),
    /** Column heading: a glossary or recurring term. */
    LIVE_CALL_COL_TERM("live.call.col.term"),
    /** Column heading: the rendering of a term. */
    LIVE_CALL_COL_RENDERING("live.call.col.rendering"),
    /** Column heading: the note beside a term. */
    LIVE_CALL_COL_NOTE("live.call.col.note"),
    /** Column heading: the source text of an earlier pair. */
    LIVE_CALL_COL_SOURCE("live.call.col.source"),
    /** Column heading: the translation of an earlier pair. */
    LIVE_CALL_COL_TRANSLATION("live.call.col.translation"),
    /** Result word: the item came back once with text. */
    LIVE_CALL_ITEM_OK("live.call.item.ok"),
    /** Result word: the item is absent from the reply or blank. */
    LIVE_CALL_ITEM_MISSING("live.call.item.missing"),
    /** Result word: the item came back more than once. */
    LIVE_CALL_ITEM_DUPLICATE("live.call.item.duplicate"),
    /** Result word: the reply holds an id the batch never had. */
    LIVE_CALL_ITEM_EXTRA("live.call.item.extra"),
    /** Result word: the item's text seems to carry the next item as well. */
    LIVE_CALL_ITEM_MERGED("live.call.item.merged"),

    /** Words for one reason in an outcome chip, and for a rule a consistency answer broke; arguments: 0 the reason's code lower-cased with dashes as underscores, 1 the code as reported, shown when no words are known. */
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

    /** Heading of the source pane when no source language is set. */
    LIVE_SOURCE_FALLBACK("live.source.fallback"),
    /** Heading of the target pane when no target language is set. */
    LIVE_TARGET_FALLBACK("live.target.fallback"),
    /** Line under the Translating heading: progress of a stopped run lasts only until the application closes. */
    TRANSLATING_SUBTITLE("translating.subtitle"),

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

    /** The chapter part of the progress line; arguments are the section and the number of sections. */
    TRANSLATING_LINE_SECTION("translating.line.section"),

    /** The time-left part of the pace text; argument 0 is the rounded time, "about 20 min" or the like. */
    TRANSLATING_PACE_LEFT("translating.pace.left"),

    /** Button that leaves a completed run for the Export step. */
    TRANSLATING_CONTINUE("translating.continue"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_CONTINUE}. */
    TRANSLATING_CONTINUE_TIP("translating.continue.tip"),
    /** Button that goes back to the Names and style step. */
    TRANSLATING_BACK("translating.back"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_BACK}. */
    TRANSLATING_BACK_TIP("translating.back.tip"),
    /** Label of the Export screen's Back action. */
    EXPORT_BACK("export.back"),
    /** Name of the one step of the file name suggestion dialog. */
    EXPORT_SUGGEST_NAME_STEP("export.suggestName.step"),
    /** What the name suggestion sent; arguments: the title, the target language's name and the format. */
    EXPORT_SUGGEST_NAME_SENT("export.suggestName.sent"),
    /** What the name suggestion answered; argument: the suggested name. */
    EXPORT_SUGGEST_NAME_HOW("export.suggestName.how"),
    /** Purpose line of the file name suggestion dialog. */
    PROGRESS_SUGGEST_NAME_PURPOSE("progress.suggestNamePurpose"),
    /** Hover explanation of the control labelled by {@link #EXPORT_BACK}. */
    EXPORT_BACK_TIP("export.back.tip"),
    /** Banner title of a failure reported in place, such as a destination that may not be replaced. */
    TRANSLATING_REFUSED_TITLE("translating.refusedTitle"),
    /** Banner text naming the missing input; argument 0 is its token (book or model). */
    TRANSLATING_MISSING_TEXT("translating.missingText"),

    /** Chip over the activity log offered once the person scrolled up, which stops the log following its newest line. */
    TRANSLATING_LOG_JUMP("translating.log.jump"),
    /** Hover explanation of the control labelled by {@link #TRANSLATING_LOG_JUMP}. */
    TRANSLATING_LOG_JUMP_TIP("translating.log.jump.tip"),
    /** Hover explanation of a collapsed activity-log line; argument 0 is how many times it happened in a row. */
    TRANSLATING_LOG_REPEATS("translating.log.repeats"),
    /** Heading of the connection chip's hover explanation. */
    SHELL_CONNECTION("shell.connection"),
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
    /** Button of the export-complete dialog that puts the written book's full path on the clipboard. */
    EXPORT_COMPLETE_COPY("export.complete.copy"),
    /** Hover explanation of the control labelled by {@link #EXPORT_COMPLETE_COPY}. */
    EXPORT_COMPLETE_COPY_TIP("export.complete.copy.tip"),
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
    /** Caption of the first tile before any export: the segments done. */
    EXPORT_TILE_DONE("export.tile.done"),
    /** A tile figure read against its whole; argument 0 is the part, argument 1 the whole. */
    EXPORT_TILE_OF("export.tile.of"),
    /** Caption of the fourth tile once a written book was checked. */
    EXPORT_TILE_VALID_DONE("export.tile.validDone"),
    /** Banner title while a run translates. */
    EXPORT_BANNER_RUNNING("export.banner.running"),
    /** Banner text while a run translates. */
    EXPORT_BANNER_RUNNING_NOTE("export.banner.running.note"),
    /** The banner button that pauses the translating run. */
    EXPORT_BANNER_PAUSE("export.banner.pause"),
    /** Hover explanation of the control labelled by {@link #EXPORT_BANNER_PAUSE}. */
    EXPORT_BANNER_PAUSE_TIP("export.banner.pause.tip"),
    /** Banner of a partial book; argument 0 is the translated segments, argument 1 all that could be. */
    EXPORT_BANNER_PARTIAL("export.banner.partial"),
    /** Banner title and toolbar reason while nothing is translated. */
    EXPORT_BANNER_NOTHING("export.banner.nothing"),
    /** Banner text while nothing is translated. */
    EXPORT_BANNER_NOTHING_NOTE("export.banner.nothing.note"),
    /** Banner while the export runs in the background. */
    EXPORT_HIDDEN_BANNER("export.hidden.banner"),
    /** Label of the original title. */
    EXPORT_BOOK_TITLE_ORIGINAL("export.bookTitle.original"),
    /** Label of the translated title field. */
    EXPORT_BOOK_TITLE_TRANSLATED("export.bookTitle.translated"),
    /** Label of the original author. */
    EXPORT_BOOK_AUTHOR_ORIGINAL("export.bookAuthor.original"),
    /** Label of the translated author field. */
    EXPORT_BOOK_AUTHOR_TRANSLATED("export.bookAuthor.translated"),
    /** The original author of a book that names none. */
    EXPORT_BOOK_AUTHOR_NONE("export.bookAuthor.none"),
    /** A title edit that was not saved; argument 0 is the marks it lacks. */
    EXPORT_BOOK_TITLE_MARKS_MISSING("export.bookTitle.marksMissing"),
    /** A title or author edit the store refused; argument 0 is why, in words. */
    EXPORT_BOOK_TITLE_NOT_SAVED("export.bookTitle.notSaved"),
    /** The note after a short path inside the home folder. */
    EXPORT_SAVE_TO_HOME("export.saveTo.home"),
    /** The line under Save as about an existing file. */
    EXPORT_REPLACE_HINT("export.replace.hint"),
    /** Heading of the question before replacing; argument 0 is how many files exist. */
    EXPORT_REPLACE_TITLE("export.replace.title"),
    /** Text of the question before replacing; argument 0 is how many files exist, argument 1 their paths, one per line. */
    EXPORT_REPLACE_TEXT("export.replace.text"),
    /** The button that allows replacing the existing files. */
    EXPORT_REPLACE_YES("export.replace.yes"),
    /** Hover explanation of the control labelled by {@link #EXPORT_REPLACE_YES}. */
    EXPORT_REPLACE_YES_TIP("export.replace.yes.tip"),
    /** The progress dialog button that takes the suggested file name. */
    EXPORT_SUGGEST_NAME_USE("export.suggestName.use"),
    /** Hover explanation of the control labelled by {@link #EXPORT_SUGGEST_NAME_USE}. */
    EXPORT_SUGGEST_NAME_USE_TIP("export.suggestName.use.tip"),
    /** The file-name dialog's line saying what Cancel does. */
    EXPORT_SUGGEST_NAME_CANCEL_KEEPS("export.suggestName.cancelKeeps"),
    /** The notice after a title edit: the file name is suggested again only on request. */
    EXPORT_SUGGEST_NAME_TITLE_CHANGED("export.suggestName.titleChanged"),
    /** The last check's note while the book is partial. */
    EXPORT_CONSISTENCY_PARTIAL("export.consistency.partial"),
    /** Heading of the export dialog's steps. */
    EXPORT_GROUP_STEPS("export.group.steps"),
    /** Heading of the reread items; argument 0 is how many were reread, argument 1 how many there are. */
    EXPORT_GROUP_REREAD("export.group.reread"),
    /** Heading of the export dialog's last step. */
    EXPORT_GROUP_FINISH("export.group.finish"),
    /** Column heading of the reread grid: the text before. */
    EXPORT_REREAD_BEFORE("export.reread.before"),
    /** Column heading of the reread grid: the text after. */
    EXPORT_REREAD_AFTER("export.reread.after"),
    /** Row label of the reread grid: the paragraph before. */
    EXPORT_REREAD_PREVIOUS("export.reread.previous"),
    /** Row label of the reread grid: the reread paragraph. */
    EXPORT_REREAD_THIS("export.reread.this"),
    /** Row label of the reread grid: the paragraph after. */
    EXPORT_REREAD_NEXT("export.reread.next"),
    /** The after cell of an item the reread left alone. */
    EXPORT_REREAD_UNCHANGED("export.reread.unchanged"),
    /** The after cell of an item whose call is under way. */
    EXPORT_REREAD_READING("export.reread.reading"),
    /** The progress dialog button that ends the last check and writes the file. */
    EXPORT_WRITE_NOW("export.writeNow"),
    /** Hover explanation of the control labelled by {@link #EXPORT_WRITE_NOW}. */
    EXPORT_WRITE_NOW_TIP("export.writeNow.tip"),
    /** Heading of the question before writing early. */
    EXPORT_WRITE_NOW_TITLE("export.writeNow.title"),
    /** Text of the question before writing early; arguments are reread, all, fixed and the rest. */
    EXPORT_WRITE_NOW_TEXT("export.writeNow.text"),
    /** Text of the question before writing early when nothing was reread yet. */
    EXPORT_WRITE_NOW_TEXT_EARLY("export.writeNow.textEarly"),
    /** The button that writes the file early. */
    EXPORT_WRITE_NOW_YES("export.writeNow.yes"),
    /** Hover explanation of the control labelled by {@link #EXPORT_WRITE_NOW_YES}. */
    EXPORT_WRITE_NOW_YES_TIP("export.writeNow.yes.tip"),
    /** The safe answer of the write-early question. */
    EXPORT_WRITE_NOW_NO("export.writeNow.no"),
    /** Check line after an early write; argument 0 is how many paragraphs were not reread. */
    EXPORT_CHECK_CONSISTENCY_NOT_REREAD("export.check.consistency.notReread"),
    /** Row label of the export-complete card: the last check. */
    EXPORT_COMPLETE_LAST_CHECK("export.complete.lastCheck"),
    /** Row value of the last check; argument 0 is the segments reread, argument 1 those fixed. */
    EXPORT_COMPLETE_LAST_CHECK_VALUE("export.complete.lastCheckValue"),
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
    /**
     * Warning after an export: segments wait on characters whose gender is unknown; argument 0 is the segment count,
     * argument 1 the characters with their segment counts.
     */
    EXPORT_AWAITING_GENDER("export.awaitingGender"),
    /**
     * Warning after an export: segments wait on a gender the glossary only suggests; argument 0 is the segment count,
     * argument 1 the characters with their segment counts.
     */
    EXPORT_SUGGESTED_GENDER("export.suggestedGender"),
    /** The control that opens Names &amp; style from the warning labelled by {@link #EXPORT_AWAITING_GENDER}. */
    EXPORT_AWAITING_GENDER_OPEN("export.awaitingGender.open"),
    /** Hover explanation of the control labelled by {@link #EXPORT_AWAITING_GENDER_OPEN}. */
    EXPORT_AWAITING_GENDER_OPEN_TIP("export.awaitingGender.open.tip"),
    /** The tail of the warning's character list when more characters wait than are named; argument 0 is how many. */
    EXPORT_AWAITING_GENDER_MORE("export.awaitingGender.more"),
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
    /** Check line after an export: argument 0 is how many segments were skipped because their model call failed. */
    EXPORT_CHECK_CONSISTENCY_SKIPPED("export.check.consistency.skipped"),
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
    /** Title of the question asked when the model a translation continues with does not answer. */
    DIALOG_CONTINUE_NOT_ANSWERING_TITLE("dialog.continue.notAnswering.title"),
    /** Body of the not-answering question: provider, model. */
    DIALOG_CONTINUE_NOT_ANSWERING_BODY("dialog.continue.notAnswering.body"),
    /** Button that checks again whether the model answers. */
    DIALOG_CONTINUE_TRY_AGAIN("dialog.continue.tryAgain"),
    /** Hover explanation of the control labelled by {@link #DIALOG_CONTINUE_TRY_AGAIN}. */
    DIALOG_CONTINUE_TRY_AGAIN_TIP("dialog.continue.tryAgain.tip"),
    /** Button that opens Settings to choose another model. */
    DIALOG_CONTINUE_USE_ANOTHER("dialog.continue.useAnother"),
    /** Hover explanation of the control labelled by {@link #DIALOG_CONTINUE_USE_ANOTHER}. */
    DIALOG_CONTINUE_USE_ANOTHER_TIP("dialog.continue.useAnother.tip"),
    /** Button that leaves the translation as it is. */
    DIALOG_CONTINUE_NOT_NOW("dialog.continue.notNow"),
    /** Hover explanation of the control labelled by {@link #DIALOG_CONTINUE_NOT_NOW}. */
    DIALOG_CONTINUE_NOT_NOW_TIP("dialog.continue.notNow.tip"),
    /** Title of the question asked when the chosen model differs from the one the translation started with. */
    DIALOG_CONTINUE_DIFFERENT_TITLE("dialog.continue.different.title"),
    /** Body of the different-model question: earlier provider, earlier model, chosen provider, chosen model. */
    DIALOG_CONTINUE_DIFFERENT_BODY("dialog.continue.different.body"),
    /** Button that continues with the model the translation started with. */
    DIALOG_CONTINUE_EARLIER("dialog.continue.earlier"),
    /** Hover explanation of the control labelled by {@link #DIALOG_CONTINUE_EARLIER}. */
    DIALOG_CONTINUE_EARLIER_TIP("dialog.continue.earlier.tip"),
    /** Button that continues with the model chosen now. */
    DIALOG_CONTINUE_NEW("dialog.continue.new"),
    /** Hover explanation of the control labelled by {@link #DIALOG_CONTINUE_NEW}. */
    DIALOG_CONTINUE_NEW_TIP("dialog.continue.new.tip"),
    /** Title of the question asking which of several found models to use. */
    DIALOG_MODEL_PICK_TITLE("dialog.modelPick.title"),
    /** Body of the model-pick question: provider. */
    DIALOG_MODEL_PICK_BODY("dialog.modelPick.body"),
    /** Hover explanation of one model in the pick list: model. */
    DIALOG_MODEL_PICK_OPTION_HINT("dialog.modelPick.optionHint"),
    /** Button that uses the picked model. */
    DIALOG_MODEL_PICK_USE("dialog.modelPick.use"),
    /** Hover explanation of the control labelled by {@link #DIALOG_MODEL_PICK_USE}. */
    DIALOG_MODEL_PICK_USE_TIP("dialog.modelPick.use.tip"),
    /** Button that leaves the model unchosen. */
    DIALOG_MODEL_PICK_NOT_NOW("dialog.modelPick.notNow"),
    /** Hover explanation of the control labelled by {@link #DIALOG_MODEL_PICK_NOT_NOW}. */
    DIALOG_MODEL_PICK_NOT_NOW_TIP("dialog.modelPick.notNow.tip"),
    /** Title of the question asked when a book is chosen while a project is open. */
    DIALOG_OPEN_ANOTHER_TITLE("dialog.openAnother.title"),
    /** Body of that question; argument 0 is the open project's name, argument 1 the file chosen. */
    DIALOG_OPEN_ANOTHER_BODY("dialog.openAnother.body"),
    /** Note added while a run is going: it is stopped before either answer is carried out. */
    DIALOG_OPEN_ANOTHER_RUNNING("dialog.openAnother.running"),
    /** Default button: keep the open project and open the book as a new one. */
    DIALOG_OPEN_ANOTHER_NEW("dialog.openAnother.new"),
    /** Hover explanation of the control labelled by {@link #DIALOG_OPEN_ANOTHER_NEW}. */
    DIALOG_OPEN_ANOTHER_NEW_TIP("dialog.openAnother.new.tip"),
    /** Destructive button: delete the open project and open the book in its place. */
    DIALOG_OPEN_ANOTHER_REPLACE("dialog.openAnother.replace"),
    /** Hover explanation of the control labelled by {@link #DIALOG_OPEN_ANOTHER_REPLACE}. */
    DIALOG_OPEN_ANOTHER_REPLACE_TIP("dialog.openAnother.replace.tip"),
    /** Button that closes the question and changes nothing. */
    DIALOG_OPEN_ANOTHER_CANCEL("dialog.openAnother.cancel"),
    /** Hover explanation of the control labelled by {@link #DIALOG_OPEN_ANOTHER_CANCEL}. */
    DIALOG_OPEN_ANOTHER_CANCEL_TIP("dialog.openAnother.cancel.tip"),
    /** Line shown while the run is stopped for the answer. */
    DIALOG_OPEN_ANOTHER_STOPPING("dialog.openAnother.stopping"),
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
    /** Title of the question asked at Start about characters whose gender is unknown. */
    DIALOG_GENDERS_TITLE("dialog.genders.title"),
    /** Body of that question; argument 0 is the number of characters listed. */
    DIALOG_GENDERS_TEXT("dialog.genders.text"),
    /** One listed character; argument 0 is its name, argument 1 how often the book names it. */
    DIALOG_GENDERS_ROW("dialog.genders.row"),
    /** The control that starts without setting the genders. */
    DIALOG_GENDERS_START("dialog.genders.start"),
    /** Title of the question when every character it lists has a suggested gender to confirm. */
    DIALOG_GENDERS_SUGGESTED_TITLE("dialog.genders.suggested.title"),
    /** Intro to the characters whose gender is only suggested; argument 0 is how many. */
    DIALOG_GENDERS_SUGGESTED_TEXT("dialog.genders.suggested.text"),
    /** One character whose gender is only suggested; arguments: 0 the name, 1 its mentions. */
    DIALOG_GENDERS_SUGGESTED_ROW("dialog.genders.suggested.row"),
    /** Hover explanation of the control labelled by {@link #DIALOG_GENDERS_START}. */
    DIALOG_GENDERS_START_TIP("dialog.genders.start.tip"),
    /** The control that returns without starting. */
    DIALOG_GENDERS_BACK("dialog.genders.back"),
    /** Hover explanation of the control labelled by {@link #DIALOG_GENDERS_BACK}. */
    DIALOG_GENDERS_BACK_TIP("dialog.genders.back.tip"),
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
    /** Note beside the review actions while a run translates and the segment can only be read. */
    REVIEW_LOCKED_RUNNING("review.locked.running"),
    /** Note beside the review actions while a retry of a segment is in flight. */
    REVIEW_LOCKED_RETRY("review.locked.retry"),
    /** Note above the target of a segment that kept no translation but the model's refused reply, which it shows. */
    REVIEW_TARGET_REJECTED("review.target.rejected"),
    /** Note above the target of a segment that kept no translation at all, so its source is shown. */
    REVIEW_TARGET_SOURCE("review.target.source"),
    /** The review editor's line while the edit is blank: Save waits for the translation's words. */
    REVIEW_EDIT_BLANK("review.edit.blank"),
    /** Strip under the editor for a segment whose source holds no formatting marks. */
    REVIEW_MARKS_NONE("review.marks.none"),
    /** Strip under the editor while every mark is in place; argument 0 is the marks, space-separated. */
    REVIEW_MARKS_OK("review.marks.ok"),
    /** Strip text naming marks the edit lost; argument 0 is the marks, space-separated. */
    REVIEW_MARKS_MISSING("review.marks.missing"),
    /** Strip text naming marks the edit holds twice; argument 0 is the marks, space-separated. */
    REVIEW_MARKS_DOUBLED("review.marks.doubled"),
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
    /** Note shown when Accept is off for a segment whose editor holds the model's refused reply: Save edit keeps it. */
    REVIEW_ACCEPT_NEEDS_SAVE("review.accept.needsSave"),
    /** Caption of the readable view under the two panes: formatting tokens as chips, quoted words marked. */
    REVIEW_READABLE("review.readable"),
    /** Shown under the findings heading of a flagged segment whose record holds no finding. */
    REVIEW_FINDINGS_NONE("review.findings.none"),
    /** Heading of an edit the reviewer made and the app verified; argument 0 is the criterion's name. */
    REVIEW_EDIT_APPLIED("review.edit.applied"),
    /** The name of an edit criterion; argument 0 is its token with underscores for hyphens, such as {@code gender} or {@code invented_word}, because an ICU selector holds no hyphen. */
    REVIEW_EDIT_CRITERION("review.edit.criterion"),
    /** The replacement line of an edit that only deleted its quote. */
    REVIEW_EDIT_DELETED("review.edit.deleted"),
    /** Hover explanation of the diff of an applied edit: red is what the reviewer removed, green what it put there. */
    REVIEW_EDIT_TIP("review.edit.applied.tip"),
    /** Button that retries the selected segment's translation as it is. */
    REVIEW_RETRY("review.retry"),
    /** Shown in place when a retry is asked for and no model is chosen. */
    REVIEW_RETRY_NO_MODEL("review.retryNoModel"),
    /** Heading of the results dialog: the operation's name. */
    RESULTS_TITLE("results.title"),
    /** Name of the glossary's name scan in the results dialog. */
    RESULTS_OP_NAME_SCAN("results.op.nameScan"),
    /** Name of the glossary's review with the model in the results dialog. */
    RESULTS_OP_NAME_REVIEW("results.op.nameReview"),
    /** Name of the glossary's translate-only action in the results dialog. */
    RESULTS_OP_NAME_TRANSLATE("results.op.nameTranslate"),
    /** Name of the recurring terms' scan in the results dialog. */
    RESULTS_OP_TERM_SCAN("results.op.termScan"),
    /** Name of the recurring terms' review in the results dialog. */
    RESULTS_OP_TERM_REVIEW("results.op.termReview"),
    /** Name of the recurring terms' rendering suggestion in the results dialog. */
    RESULTS_OP_TERM_TRANSLATE("results.op.termTranslate"),
    /** Name of the automatic names pass in the results dialog. */
    RESULTS_OP_AUTO_NAMES("results.op.autoNames"),
    /** Name of "Confirm suggested genders" in the results dialog. */
    RESULTS_OP_CONFIRM_GENDERS("results.op.confirmGenders"),
    /** Heading of the glossary's rows in a sectioned results list; argument 0 is their count. */
    RESULTS_SECTION_NAMES("results.section.names"),
    /** Heading of the recurring terms' rows in a sectioned results list; argument 0 is their count. */
    RESULTS_SECTION_TERMS("results.section.terms"),
    /** Heading of the rows an operation added, with their count. */
    RESULTS_ADDED("results.added"),
    /** Heading of the added rows a scan found in the book's text without a model, with their count. */
    RESULTS_FOUND_IN_TEXT("results.foundInText"),
    /** Heading of the added rows the model chose when other added rows came from the text, with their count. */
    RESULTS_ADDED_BY_MODEL("results.addedByModel"),
    /** Heading of the rows an operation removed, with their count. */
    RESULTS_REMOVED("results.removed"),
    /** Heading of the rows an operation changed, with their count. */
    RESULTS_CHANGED("results.changed"),
    /** Line of the results dialog when an operation changed nothing. */
    RESULTS_NOTHING("results.nothing"),
    /** Line of the results dialog that lists the names still without a target, with their count and the names. */
    RESULTS_UNTARGETED("results.untargeted"),
    /** Line of the results dialog when the person stopped an operation. */
    RESULTS_STOPPED("results.stopped"),
    /** Column of the results rows: the term. */
    RESULTS_COLUMN_TERM("results.column.term"),
    /** Column of the results rows: the row before the operation. */
    RESULTS_COLUMN_BEFORE("results.column.before"),
    /** Column of the results rows: the row after the operation. */
    RESULTS_COLUMN_AFTER("results.column.after"),
    /** Column of the results rows: the model's reason, shown only when some row has one. */
    RESULTS_COLUMN_WHY("results.column.why"),
    /** Button of a results row that puts the row back as it was. */
    RESULTS_REVERT("results.revert"),
    /** Hover explanation of the control labelled by {@link #RESULTS_REVERT}. */
    RESULTS_REVERT_TIP("results.revert.tip"),
    /** Accessible name of a row's revert button: the term. */
    RESULTS_REVERT_FOR("results.revert.for"),
    /** Word shown in place of the revert button once a row was put back. */
    RESULTS_REVERTED("results.reverted"),
    /** Button of the results dialog that reverts every row. */
    RESULTS_UNDO_ALL("results.undoAll"),
    /** Hover explanation of the control labelled by {@link #RESULTS_UNDO_ALL}. */
    RESULTS_UNDO_ALL_TIP("results.undoAll.tip"),
    /** Button of the results dialog that closes it and shows only the changed rows. */
    RESULTS_SHOW_CHANGED("results.showChanged"),
    /** Hover explanation of the control labelled by {@link #RESULTS_SHOW_CHANGED}. */
    RESULTS_SHOW_CHANGED_TIP("results.showChanged.tip"),
    /** Word a screen reader gives for a row the last operation changed; the row itself shows a left edge. */
    CHANGED_MARKER("namesStyle.changed.marker"),
    /** Hover explanation of a row the last operation changed. */
    CHANGED_MARKER_TIP("namesStyle.changed.marker.tip"),
    /** Toggle that shows only the rows the last operation changed. */
    CHANGED_FILTER("namesStyle.changed.filter"),
    /** Hover explanation of the control labelled by {@link #CHANGED_FILTER}. */
    CHANGED_FILTER_TIP("namesStyle.changed.filter.tip"),
    /** Word of the state marker for something queued or idle. */
    STATE_WAITING("state.waiting"),
    /** Word of the state marker for something at work. */
    STATE_WORKING("state.working"),
    /** Word of the state marker for something finished well. */
    STATE_DONE("state.done"),
    /** Word of the state marker for something that needs the person or is slow. */
    STATE_ATTENTION("state.attention"),
    /** Word of the state marker for something that failed or cannot be reached. */
    STATE_FAILED("state.failed"),
    /** Word of the state marker for something left as it was on purpose. */
    STATE_KEPT("state.kept"),
    /** Line shown when a changed row was edited after the operation, so it is not put back: the term. */
    RESULTS_REVERT_EDITED("results.revert.edited"),
    /** Title-bar state text of a project with nothing translated yet. */
    RUN_TITLE_READY("run.title.ready"),
    /** Title-bar state text of a reopened project with stored progress: the percent done. */
    RUN_TITLE_READY_CONTINUE("run.title.readyContinue"),
    /** Title-bar state text while translating: the percent done and the worded time left, or {@code none}. */
    RUN_TITLE_RUNNING("run.title.running"),
    /** Title-bar state text while a pause waits for the run's next boundary. */
    RUN_TITLE_PAUSING("run.title.pausing"),
    /** Title-bar state text of a paused run: the percent done. */
    RUN_TITLE_PAUSED("run.title.paused"),
    /** Title-bar state text while the run waits for the AI program by itself: the program. */
    RUN_TITLE_WAITING("run.title.waiting"),
    /** Title-bar state text while a stop waits for the run to return. */
    RUN_TITLE_STOPPING("run.title.stopping"),
    /** Title-bar state text of a stopped run: the percent done. */
    RUN_TITLE_STOPPED("run.title.stopped"),
    /** Title-bar state text once every segment is decided. */
    RUN_TITLE_FINISHED("run.title.finished"),
    /** Title-bar state text of a run that ended on an error. */
    RUN_TITLE_FAILED("run.title.failed"),
    /** AI-model chip while a model is chosen and no run is at work. */
    RUN_CHIP_READY("run.chip.ready"),
    /** AI-model chip while no model is chosen. */
    RUN_CHIP_NO_MODEL("run.chip.noModel"),
    /** AI-model chip while the run sends requests. */
    RUN_CHIP_ANSWERING("run.chip.answering"),
    /** AI-model chip while the run is paused, stopped or finished. */
    RUN_CHIP_IDLE("run.chip.idle"),
    /** AI-model chip while the AI program cannot be reached: the program. */
    RUN_CHIP_UNREACHABLE("run.chip.unreachable"),
    /** AI-model chip once the run stopped trying by itself: the program and the hours. */
    RUN_CHIP_GAVE_UP("run.chip.gaveUp"),
    /** AI-model chip while the chosen model is not available. */
    RUN_CHIP_MISSING("run.chip.missing"),
    /** AI-model chip while an attempt has had no answer for a minute or more: its clock. */
    RUN_CHIP_STUCK("run.chip.stuck"),
    /** AI-model chip of a run that ended on an error. */
    RUN_CHIP_ERROR("run.chip.error"),
    /** Run action that continues a project at its first segment not done. */
    RUN_ACTION_CONTINUE("run.action.continue"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_CONTINUE}. */
    RUN_ACTION_CONTINUE_TIP("run.action.continue.tip"),
    /** Run action that sends the interrupted request to the AI model again now. */
    RUN_ACTION_TRY_AGAIN("run.action.tryAgain"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_TRY_AGAIN}. */
    RUN_ACTION_TRY_AGAIN_TIP("run.action.tryAgain.tip"),
    /** Run action that opens the review desk: the count of segments to review. */
    RUN_ACTION_REVIEW("run.action.review"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_REVIEW}. */
    RUN_ACTION_REVIEW_TIP("run.action.review.tip"),
    /** Pause offered while the run waits for the AI program by itself. */
    RUN_ACTION_PAUSE_WAIT("run.action.pauseWait"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_PAUSE_WAIT}. */
    RUN_ACTION_PAUSE_WAIT_TIP("run.action.pauseWait.tip"),
    /** Banner action on another screen that shows Translating. */
    RUN_ACTION_GO_TRANSLATING("run.action.goTranslating"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_GO_TRANSLATING}. */
    RUN_ACTION_GO_TRANSLATING_TIP("run.action.goTranslating.tip"),
    /** Banner action that opens the provider settings to choose another model. */
    RUN_ACTION_CHOOSE_MODEL("run.action.chooseModel"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_CHOOSE_MODEL}. */
    RUN_ACTION_CHOOSE_MODEL_TIP("run.action.chooseModel.tip"),
    /** Banner action that opens the failure of a failed run with its details. */
    RUN_ACTION_SHOW_DETAILS("run.action.showDetails"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_SHOW_DETAILS}. */
    RUN_ACTION_SHOW_DETAILS_TIP("run.action.showDetails.tip"),
    /** Banner action of a finished run that opens the review desk: the count. */
    RUN_ACTION_REVIEW_SEGMENTS("run.action.reviewSegments"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_REVIEW_SEGMENTS}. */
    RUN_ACTION_REVIEW_SEGMENTS_TIP("run.action.reviewSegments.tip"),
    /** Banner action that flags the segments in flight and goes on: their count. */
    RUN_ACTION_SKIP("run.action.skip"),
    /** Hover explanation of the control labelled by {@link #RUN_ACTION_SKIP}. */
    RUN_ACTION_SKIP_TIP("run.action.skip.tip"),
    /** Banner of a reopened project with stored progress: segments done and in all. */
    RUN_BANNER_WELCOME("run.banner.welcome"),
    /** Second line of the welcome banner: where it continues, or {@code none}. */
    RUN_BANNER_WELCOME_TEXT("run.banner.welcomeText"),
    /** Banner of a start that lacks an input: the input's token. */
    RUN_BANNER_CANNOT_START("run.banner.cannotStart"),
    /** Banner while the run waits for the AI program by itself: the program. */
    RUN_BANNER_WAITING("run.banner.waiting"),
    /** Second line of the waiting banner: the program and the time to the next try. */
    RUN_BANNER_WAITING_TEXT("run.banner.waitingText"),
    /** Banner of a run paused on an error: the program and {@code reach} or another token. */
    RUN_BANNER_PAUSED_ERROR("run.banner.pausedError"),
    /** Second line of the paused-on-error banner: the program and the same token. */
    RUN_BANNER_PAUSED_ERROR_TEXT("run.banner.pausedErrorText"),
    /** Banner once the run stopped trying by itself: the program and the hours. */
    RUN_BANNER_GAVE_UP("run.banner.gaveUp"),
    /** Second line of the gave-up banner: the program. */
    RUN_BANNER_GAVE_UP_TEXT("run.banner.gaveUpText"),
    /** Banner of a run paused because the model is not available: the model. */
    RUN_BANNER_MODEL_MISSING("run.banner.modelMissing"),
    /** Second line of the model-missing banner: the program. */
    RUN_BANNER_MODEL_MISSING_TEXT("run.banner.modelMissingText"),
    /** Banner of a call with no answer for a minute: its clock and its time limit, or {@code none}. */
    RUN_BANNER_STUCK("run.banner.stuck"),
    /** Banner of a finished run with segments to review: their count. */
    RUN_BANNER_FINISHED_REVIEW("run.banner.finishedReview"),
    /** Banner of a finished run with nothing to review. */
    RUN_BANNER_FINISHED_NONE("run.banner.finishedNone"),
    /** Banner of a run that ended on an error. */
    RUN_BANNER_FAILED("run.banner.failed"),
    /** A banner line that is a failure's own message, passed through as it is. */
    RUN_BANNER_PLAIN("run.banner.plain"),
    /** Live line of a project with nothing translated yet. */
    RUN_LIVE_READY("run.live.ready"),
    /** Live line of a reopened project: where it continues, or {@code none}. */
    RUN_LIVE_CONTINUES("run.live.continues"),
    /** Live line while a start lacks an input: nothing to say. */
    RUN_LIVE_CANNOT_START("run.live.cannotStart"),
    /** Live line while translating: the segments of the current call, or {@code none}. */
    RUN_LIVE_RUNNING("run.live.running"),
    /** Live line while pausing: the segments in flight, or {@code none}. */
    RUN_LIVE_PAUSING("run.live.pausing"),
    /** Live line of a paused run: the segments in flight, or {@code none}. */
    RUN_LIVE_PAUSED("run.live.paused"),
    /** Live line while the run waits for the AI program: the program and the segments, or {@code none}. */
    RUN_LIVE_WAITING("run.live.waiting"),
    /** Live line of a call with no answer for a minute: the segments, or {@code none}. */
    RUN_LIVE_STUCK("run.live.stuck"),
    /** Live line while stopping: the segments in flight, or {@code none}. */
    RUN_LIVE_STOPPING("run.live.stopping"),
    /** Live line of a stopped run: where it continues, or {@code none}. */
    RUN_LIVE_STOPPED("run.live.stopped"),
    /** Live line of a finished run. */
    RUN_LIVE_FINISHED("run.live.finished"),
    /** Live line of a run that ended on an error: where, or {@code none}. */
    RUN_LIVE_FAILED("run.live.failed"),
    /** Hover explanation of the navigation footer. */
    NAV_FOOTER_SELECTION_TIP("nav.footer.selection.tip"),
    /** Why a model action is off while a translation is at work. */
    MODEL_BLOCKED_PAUSE_FIRST("model.blocked.pauseFirst"),
    /** Why Start translation is off while the names pass runs. */
    START_BLOCKED_NAMES_PASS("start.blocked.namesPass"),
    /** Heading of the question asked before leaving a screen with unsaved changes. */
    DIALOG_UNSAVED_TITLE("dialog.unsaved.title"),
    /** Body of the question asked before leaving a screen with unsaved changes. */
    DIALOG_UNSAVED_TEXT("dialog.unsaved.text"),
    /** The answer that stays on the screen with the changes as they are. */
    DIALOG_UNSAVED_KEEP("dialog.unsaved.keep"),
    /** Hover explanation of the control labelled by {@link #DIALOG_UNSAVED_KEEP}. */
    DIALOG_UNSAVED_KEEP_TIP("dialog.unsaved.keep.tip"),
    /** The answer that drops the changes and leaves. */
    DIALOG_UNSAVED_DISCARD("dialog.unsaved.discard"),
    /** Hover explanation of the control labelled by {@link #DIALOG_UNSAVED_DISCARD}. */
    DIALOG_UNSAVED_DISCARD_TIP("dialog.unsaved.discard.tip"),
    /** The answer that saves the changes and leaves. */
    DIALOG_UNSAVED_SAVE("dialog.unsaved.save"),
    /** Hover explanation of the control labelled by {@link #DIALOG_UNSAVED_SAVE}. */
    DIALOG_UNSAVED_SAVE_TIP("dialog.unsaved.save.tip"),
    /** Heading of the unsaved-changes question on the Providers form; argument 0 is the provider's name. */
    DIALOG_UNSAVED_PROVIDER_TITLE("dialog.unsaved.provider.title"),
    /** Text of the unsaved-changes question on the Providers form. */
    DIALOG_UNSAVED_PROVIDER_TEXT("dialog.unsaved.provider.text"),
    /** Heading of the unsaved-changes question on the Models tab; argument 0 is the model and its provider. */
    DIALOG_UNSAVED_MODEL_TITLE("dialog.unsaved.model.title"),
    /** Text of the unsaved-changes question on the Models tab. */
    DIALOG_UNSAVED_MODEL_TEXT("dialog.unsaved.model.text"),
    /** Why a workflow step cannot be opened yet: no project is open. Also the hover text of the locked entry and Continue. */
    NAV_LOCKED_NO_PROJECT("nav.locked.noProject"),
    /** The purpose line under the Projects title. */
    PROJECTS_PURPOSE("projects.purpose"),
    /** Prompt text of the Projects search field. */
    PROJECTS_SEARCH("projects.search"),
    /** Hover explanation of the search field. */
    PROJECTS_SEARCH_TIP("projects.search.tip"),
    /** Filter segment showing every project. */
    PROJECTS_FILTER_ALL("projects.filter.all"),
    /** Hover explanation of the All segment. */
    PROJECTS_FILTER_ALL_TIP("projects.filter.all.tip"),
    /** Filter segment showing projects not yet finished. */
    PROJECTS_FILTER_IN_PROGRESS("projects.filter.inProgress"),
    /** Hover explanation of the In progress segment. */
    PROJECTS_FILTER_IN_PROGRESS_TIP("projects.filter.inProgress.tip"),
    /** Filter segment showing finished projects. */
    PROJECTS_FILTER_FINISHED("projects.filter.finished"),
    /** Hover explanation of the Finished segment. */
    PROJECTS_FILTER_FINISHED_TIP("projects.filter.finished.tip"),
    /** The line shown when a search keeps no project: the searched text. */
    PROJECTS_NO_MATCH("projects.noMatch"),
    /** The link that empties the search. */
    PROJECTS_CLEAR_SEARCH("projects.clearSearch"),
    /** Hover explanation of the clear-search link. */
    PROJECTS_CLEAR_SEARCH_TIP("projects.clearSearch.tip"),
    /** A book header's second line: file name, format, segment count. */
    PROJECTS_BOOK_META("projects.book.meta"),
    /** The number of translations of a book. */
    PROJECTS_BOOK_TRANSLATIONS("projects.book.translations"),
    /** Button on a book header that starts another translation of the same book. */
    PROJECTS_BOOK_TRANSLATE_INTO("projects.book.translateInto"),
    /** Hover explanation of the translate-into button. */
    PROJECTS_BOOK_TRANSLATE_INTO_TIP("projects.book.translateInto.tip"),
    /** A project row's second line: source, target, provider, model, quality. */
    PROJECTS_ROW_META("projects.row.meta"),
    /** A never-run project row's second line: source, target. */
    PROJECTS_ROW_NOT_STARTED("projects.row.notStarted"),
    /** Progress percent and when the project was last opened. */
    PROJECTS_ROW_PROGRESS("projects.row.progress"),
    /** Chip on a project whose name the person typed. */
    PROJECTS_ROW_NAMED("projects.row.named"),
    /** Hover explanation of the named chip. */
    PROJECTS_ROW_NAMED_TIP("projects.row.named.tip"),
    /** Chip on the project that is open in the window. */
    PROJECTS_ROW_OPEN("projects.row.open"),
    /** Hover explanation of the open chip. */
    PROJECTS_ROW_OPEN_TIP("projects.row.open.tip"),
    /** Row button that opens a project that has a run. */
    PROJECTS_ROW_CONTINUE("projects.row.continue"),
    /** Hover explanation of Continue. */
    PROJECTS_ROW_CONTINUE_TIP("projects.row.continue.tip"),
    /** Row button that opens a project with no run to continue, or the open one. */
    PROJECTS_ROW_OPEN_ACTION("projects.row.openAction"),
    /** Hover explanation of Open. */
    PROJECTS_ROW_OPEN_ACTION_TIP("projects.row.openAction.tip"),
    /** The row menu button's caption. */
    PROJECTS_ROW_MORE("projects.row.more"),
    /** Hover explanation of the row menu. */
    PROJECTS_ROW_MORE_TIP("projects.row.more.tip"),
    /** Row menu item. */
    PROJECTS_MENU_RENAME("projects.menu.rename"),
    /** Row menu item. */
    PROJECTS_MENU_SHOW_BOOK("projects.menu.showBook"),
    /** Row menu item. */
    PROJECTS_MENU_REMOVE("projects.menu.remove"),
    /** Status chip of a project never run. */
    PROJECTS_STATUS_NOT_STARTED("projects.status.notStarted"),
    /** Status chip of a project with an unfinished run. */
    PROJECTS_STATUS_TRANSLATING("projects.status.translating"),
    /** Status chip of a project whose latest run was paused. */
    PROJECTS_STATUS_PAUSED("projects.status.paused"),
    /** Status chip of a project whose latest run was stopped. */
    PROJECTS_STATUS_STOPPED("projects.status.stopped"),
    /** Status chip of a project whose run completed. */
    PROJECTS_STATUS_FINISHED("projects.status.finished"),
    /** Status chip of a project that was exported. */
    PROJECTS_STATUS_EXPORTED("projects.status.exported"),
    /** Status chip of a project whose run failed. */
    PROJECTS_STATUS_FAILED("projects.status.failed"),
    /** When a project was opened: today. */
    PROJECTS_WHEN_TODAY("projects.when.today"),
    /** When a project was opened: yesterday. */
    PROJECTS_WHEN_YESTERDAY("projects.when.yesterday"),
    /** When a project was opened: a date. */
    PROJECTS_WHEN_DATE("projects.when.date"),
    /** The line under the list about what removing does. */
    PROJECTS_REMOVE_HINT("projects.removeHint"),
    /** Toolbar and empty-state button that opens Import empty. */
    PROJECTS_NEW("projects.new"),
    /** Hover explanation of the new-translation buttons. */
    PROJECTS_NEW_TIP("projects.new.tip"),
    /** Title of the first-launch card. */
    PROJECTS_EMPTY_TITLE("projects.empty.title"),
    /** First step of how it works. */
    PROJECTS_EMPTY_STEP1("projects.empty.step1"),
    /** Second step of how it works. */
    PROJECTS_EMPTY_STEP2("projects.empty.step2"),
    /** Third step of how it works. */
    PROJECTS_EMPTY_STEP3("projects.empty.step3"),
    /** Fourth step of how it works. */
    PROJECTS_EMPTY_STEP4("projects.empty.step4"),
    /** The first-launch card's model line when a model is chosen: provider, model. */
    PROJECTS_MODEL_READY("projects.model.ready"),
    /** Button that opens Settings to change the AI model. */
    PROJECTS_MODEL_CHANGE("projects.model.change"),
    /** Hover explanation of Change…. */
    PROJECTS_MODEL_CHANGE_TIP("projects.model.change.tip"),
    /** Button that opens Settings to set up another AI program. */
    PROJECTS_MODEL_OTHER_PROGRAM("projects.model.otherProgram"),
    /** Hover explanation of the other-program button. */
    PROJECTS_MODEL_OTHER_PROGRAM_TIP("projects.model.otherProgram.tip"),
    /** The first-launch card's model line while the look for a local AI program runs. */
    PROJECTS_MODEL_LOOKING("projects.model.looking"),
    /** The model line when the first-launch look found the model: provider, model. */
    PROJECTS_MODEL_READY_FOUND("projects.model.readyFound"),
    /** Help under the model line when no AI program answered. */
    PROJECTS_MODEL_NONE_HELP("projects.model.noneHelp"),
    /** Heading of the model line when a program answered and the person chose no model yet. */
    PROJECTS_MODEL_DECLINED_TITLE("projects.model.declinedTitle"),
    /** Help under that heading: a program answered, no model is chosen. */
    PROJECTS_MODEL_DECLINED_HELP("projects.model.declinedHelp"),
    /** Button that looks again for a local AI program. */
    PROJECTS_MODEL_TRY_AGAIN("projects.model.tryAgain"),
    /** Hover explanation of the control labelled by {@link #PROJECTS_MODEL_TRY_AGAIN}. */
    PROJECTS_MODEL_TRY_AGAIN_TIP("projects.model.tryAgain.tip"),
    /** Caption of the project-name field on the project dialogs. */
    DIALOG_PROJECT_NAME_FIELD("dialog.projectName.field"),
    /** Hover explanation of the control labelled by {@link #DIALOG_PROJECT_NAME_FIELD}. */
    DIALOG_PROJECT_NAME_FIELD_TIP("dialog.projectName.field.tip"),
    /** The line shown when a project name is already used by another project of the same book. */
    DIALOG_PROJECT_NAME_ERROR_USED("dialog.projectName.error.used"),
    /** The line shown when a typed project name is too long; argument 0 is the longest length allowed. */
    DIALOG_PROJECT_NAME_ERROR_TOO_LONG("dialog.projectName.error.tooLong"),
    /** The line shown when a typed project name holds a slash, a backslash or a control character. */
    DIALOG_PROJECT_NAME_ERROR_CHARACTERS("dialog.projectName.error.characters"),
    /** Caption before the name chips. */
    DIALOG_PROJECT_NAME_CHIPS("dialog.projectName.chips"),
    /** Name chip that appends the model size. */
    DIALOG_PROJECT_NAME_CHIP_26B("dialog.projectName.chip.26b"),
    /** Name chip that appends the quality. */
    DIALOG_PROJECT_NAME_CHIP_MAX_QUALITY("dialog.projectName.chip.maxQuality"),
    /** Name chip that appends the thinking amount. */
    DIALOG_PROJECT_NAME_CHIP_THINKING_LOW("dialog.projectName.chip.thinkingLow"),
    /** Name chip that appends the tone. */
    DIALOG_PROJECT_NAME_CHIP_FORMAL("dialog.projectName.chip.formal"),
    /** Name chip that appends a version. */
    DIALOG_PROJECT_NAME_CHIP_V2("dialog.projectName.chip.v2"),
    /** Hover explanation of the control labelled by {@link #DIALOG_PROJECT_NAME_CHIPS}. */
    DIALOG_PROJECT_NAME_CHIPS_TIP("dialog.projectName.chips.tip"),
    /** Heading of the Rename card. */
    DIALOG_RENAME_TITLE("dialog.rename.title"),
    /** Rename card text for an automatic name. */
    DIALOG_RENAME_BODY_AUTO("dialog.rename.body.auto"),
    /** Rename card text for a name the person typed. */
    DIALOG_RENAME_BODY_NAMED("dialog.rename.body.named"),
    /** Button that gives the project its automatic name back. */
    DIALOG_RENAME_USE_AUTO("dialog.rename.useAuto"),
    /** Hover explanation of the control labelled by {@link #DIALOG_RENAME_USE_AUTO}. */
    DIALOG_RENAME_USE_AUTO_TIP("dialog.rename.useAuto.tip"),
    /** Note beside the use-automatic-name button. */
    DIALOG_RENAME_USE_AUTO_NOTE("dialog.rename.useAuto.note"),
    /** Confirming button of the Rename card. */
    DIALOG_RENAME_CONFIRM("dialog.rename.confirm"),
    /** Hover explanation of the control labelled by {@link #DIALOG_RENAME_CONFIRM}. */
    DIALOG_RENAME_CONFIRM_TIP("dialog.rename.confirm.tip"),
    /** Button on the Rename card that leads to removing the project. */
    DIALOG_RENAME_REMOVE("dialog.rename.remove"),
    /** Hover explanation of the control labelled by {@link #DIALOG_RENAME_REMOVE}. */
    DIALOG_RENAME_REMOVE_TIP("dialog.rename.remove.tip"),
    /** Heading of the Same language pair card. */
    DIALOG_SAME_PAIR_TITLE("dialog.samePair.title"),
    /** Same language pair card text. */
    DIALOG_SAME_PAIR_BODY("dialog.samePair.body"),
    /** Column heading: the existing project. */
    DIALOG_SAME_PAIR_COL_PROJECT("dialog.samePair.col.project"),
    /** Column heading: its model and quality. */
    DIALOG_SAME_PAIR_COL_MODEL("dialog.samePair.col.model"),
    /** Column heading: its progress. */
    DIALOG_SAME_PAIR_COL_PROGRESS("dialog.samePair.col.progress"),
    /** The existing project's progress. */
    DIALOG_SAME_PAIR_PROGRESS("dialog.samePair.progress"),
    /** The existing project's progress when it has not run. */
    DIALOG_SAME_PAIR_NOT_STARTED("dialog.samePair.notStarted"),
    /** Choice and button: open the existing project. */
    DIALOG_SAME_PAIR_OPEN("dialog.samePair.open"),
    /** Note under the open choice. */
    DIALOG_SAME_PAIR_OPEN_NOTE("dialog.samePair.open.note"),
    /** Hover explanation of the control labelled by {@link #DIALOG_SAME_PAIR_OPEN}. */
    DIALOG_SAME_PAIR_OPEN_TIP("dialog.samePair.open.tip"),
    /** Choice: a second project with the same languages. */
    DIALOG_SAME_PAIR_SECOND("dialog.samePair.second"),
    /** Note under the second-translation choice. */
    DIALOG_SAME_PAIR_SECOND_NOTE("dialog.samePair.second.note"),
    /** Hover explanation of the control labelled by {@link #DIALOG_SAME_PAIR_SECOND}. */
    DIALOG_SAME_PAIR_SECOND_TIP("dialog.samePair.second.tip"),
    /** Caption of the name field on the Same language pair card. */
    DIALOG_SAME_PAIR_NAME_LABEL("dialog.samePair.name.label"),
    /** Placeholder of that field. */
    DIALOG_SAME_PAIR_NAME_PLACEHOLDER("dialog.samePair.name.placeholder"),
    /** Hint under that field. */
    DIALOG_SAME_PAIR_NAME_HINT("dialog.samePair.name.hint"),
    /** Confirming button of the Same language pair card. */
    DIALOG_SAME_PAIR_USE("dialog.samePair.use"),
    /** Hover explanation of the control labelled by {@link #DIALOG_SAME_PAIR_USE}. */
    DIALOG_SAME_PAIR_USE_TIP("dialog.samePair.use.tip"),
    /** Button that keeps the language the person had; {0} names it. */
    DIALOG_SAME_PAIR_KEEP("dialog.samePair.keep"),
    /** Hover explanation of the control labelled by {@link #DIALOG_SAME_PAIR_KEEP}. */
    DIALOG_SAME_PAIR_KEEP_TIP("dialog.samePair.keep.tip"),
    /** Heading of the Translate into another language card. */
    DIALOG_TRANSLATE_INTO_TITLE("dialog.translateInto.title"),
    /** Text of that card; {0} names the book. */
    DIALOG_TRANSLATE_INTO_BODY("dialog.translateInto.body"),
    /** Caption of the target language list. */
    DIALOG_TRANSLATE_INTO_INTO("dialog.translateInto.into"),
    /** Prompt of the target language list. */
    DIALOG_TRANSLATE_INTO_INTO_PROMPT("dialog.translateInto.into.prompt"),
    /** Hover explanation of the control labelled by {@link #DIALOG_TRANSLATE_INTO_INTO}. */
    DIALOG_TRANSLATE_INTO_INTO_TIP("dialog.translateInto.into.tip"),
    /** Caption of the source language. */
    DIALOG_TRANSLATE_INTO_FROM("dialog.translateInto.from"),
    /** The source language when it was not chosen. */
    DIALOG_TRANSLATE_INTO_FROM_AUTO("dialog.translateInto.fromAuto"),
    /** A language the book is already translated into; {0} the language, {1} the number of projects. */
    DIALOG_TRANSLATE_INTO_USED("dialog.translateInto.used"),
    /** Caption of the name field. */
    DIALOG_TRANSLATE_INTO_NAME("dialog.translateInto.name"),
    /** Hint for an unused pair. */
    DIALOG_TRANSLATE_INTO_HINT_AUTO("dialog.translateInto.hint.auto"),
    /** Hint for a used pair. */
    DIALOG_TRANSLATE_INTO_HINT_USED("dialog.translateInto.hint.used"),
    /** Caption of what is copied. */
    DIALOG_TRANSLATE_INTO_START_FROM("dialog.translateInto.startFrom"),
    /** What is copied: the brief. */
    DIALOG_TRANSLATE_INTO_START_FROM_BRIEF("dialog.translateInto.startFrom.brief"),
    /** What is copied: glossary and recurring terms. */
    DIALOG_TRANSLATE_INTO_START_FROM_GLOSSARY("dialog.translateInto.startFrom.glossary"),
    /** Confirming button of that card. */
    DIALOG_TRANSLATE_INTO_CREATE("dialog.translateInto.create"),
    /** Hover explanation of the control labelled by {@link #DIALOG_TRANSLATE_INTO_CREATE}. */
    DIALOG_TRANSLATE_INTO_CREATE_TIP("dialog.translateInto.create.tip"),
    /** Heading of the Remove project question; {0} names the project. */
    CONFIRM_REMOVE_PROJECT_TITLE("confirm.removeProject.title"),
    /** Text of the Remove project question. */
    CONFIRM_REMOVE_PROJECT_TEXT("confirm.removeProject.text"),
    /** Confirming button of the Remove project question. */
    CONFIRM_REMOVE_PROJECT_YES("confirm.removeProject.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_REMOVE_PROJECT_YES}. */
    CONFIRM_REMOVE_PROJECT_YES_TIP("confirm.removeProject.yes.tip"),
    /** Heading of the delete-provider question; argument 0 is the provider's name. */
    CONFIRM_DELETE_PROVIDER_TITLE("confirm.deleteProvider.title"),
    /** What deleting a provider does; argument 0 is the provider's name. */
    CONFIRM_DELETE_PROVIDER_TEXT("confirm.deleteProvider.text"),
    /** The confirming button of the delete-provider question. */
    CONFIRM_DELETE_PROVIDER_YES("confirm.deleteProvider.yes"),
    /** Hover explanation of the control labelled by {@link #CONFIRM_DELETE_PROVIDER_YES}. */
    CONFIRM_DELETE_PROVIDER_YES_TIP("confirm.deleteProvider.yes.tip"),
    /** Heading of the Find the book card. */
    DIALOG_FIND_BOOK_TITLE("dialog.findBook.title"),
    /** Text when the stored file is gone; {0} names the project. */
    DIALOG_FIND_BOOK_MISSING("dialog.findBook.missing"),
    /** Text when the stored file's content changed. */
    DIALOG_FIND_BOOK_CHANGED("dialog.findBook.changed"),
    /** Button that opens the file dialog. */
    DIALOG_FIND_BOOK_CHOOSE("dialog.findBook.choose"),
    /** Hover explanation of the control labelled by {@link #DIALOG_FIND_BOOK_CHOOSE}. */
    DIALOG_FIND_BOOK_CHOOSE_TIP("dialog.findBook.choose.tip"),
    /** Error shown when the chosen file has other content. */
    DIALOG_FIND_BOOK_DIFFERENT("dialog.findBook.different"),
    /** Title of the system file dialog. */
    DIALOG_FIND_BOOK_CHOOSER_TITLE("dialog.findBook.chooserTitle"),
    /** Names and style: the line at the top that says what the screen is for and that it may be skipped. */
    NAMES_STYLE_PURPOSE("namesStyle.purpose"),
    /** Names and style: the note under the Glossary card's title. */
    NAMES_STYLE_GLOSSARY_NOTE("namesStyle.glossary.note"),
    /** Names and style: the empty glossary's button that scans the book for names. */
    NAMES_STYLE_EMPTY_SCAN("namesStyle.empty.scan"),
    /** Hover explanation of the control labelled by {@link #NAMES_STYLE_EMPTY_SCAN}. */
    NAMES_STYLE_EMPTY_SCAN_TIP("namesStyle.empty.scan.tip"),
    /** Recurring terms: header of the column that counts how often the book writes each term. */
    NAMES_STYLE_RECURRING_COLUMN_USED("namesStyle.recurring.column.used"),
    /** Hover explanation of the column labelled by {@link #NAMES_STYLE_RECURRING_COLUMN_USED}. */
    NAMES_STYLE_RECURRING_COLUMN_USED_TIP("namesStyle.recurring.column.used.tip"),
    /** Recurring terms: a cell of the Used column; argument 0 is the count. */
    NAMES_STYLE_RECURRING_USED("namesStyle.recurring.used"),
    /** Names and style: the note shown while a translation is at work. */
    NAMES_STYLE_RUN_BANNER("namesStyle.run.banner"),
    /** Names and style: the note shown while work in the background writes to the screen. */
    NAMES_STYLE_HIDDEN_BANNER("namesStyle.hidden.banner"),
    NAMES_STYLE_HIDDEN_NOTE("namesStyle.hidden.note"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_SCAN("progress.names.step.scan"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_NAMES("progress.names.step.names"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_TERMS_SCAN("progress.names.step.termsScan"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_TERMS_REVIEW("progress.names.step.termsReview"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_TERMS_READ("progress.names.step.termsRead"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_NAMES_TRANSLATE("progress.names.step.namesTranslate"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_TERMS_TRANSLATE("progress.names.step.termsTranslate"),
    /** Progress dialog: the name of this stage of the names pass. */
    PROGRESS_NAMES_STEP_WRITE("progress.names.step.write"),
    /** Progress dialog: a finished stage's count of requests; argument 0 is the count. */
    PROGRESS_STAGE_REQUESTS_DONE("progress.stage.requests.done"),
    /** Progress dialog: a running stage's count of requests so far; argument 0 is the count. */
    PROGRESS_STAGE_REQUESTS_RUNNING("progress.stage.requests.running"),
    /** Results view: the counts, the steps and the time of the operation; arguments 0 added, 1 removed, 2 changed, 3 steps, 4 time. */
    PROGRESS_RESULTS_SUBTITLE("progress.results.subtitle"),
    /** Results view: the counts alone, for an operation that announced no steps; arguments 0 added, 1 removed, 2 changed. */
    PROGRESS_RESULTS_COUNTS("progress.results.counts"),
    /** Results view: the tab that lists the rows the operation changed. */
    PROGRESS_RESULTS_TAB_CHANGES("progress.results.tab.changes"),
    /** Results view: the tab that lists the steps the operation took. */
    PROGRESS_RESULTS_TAB_STEPS("progress.results.tab.steps"),
    /** Results view: header of the column with each row's kind of change. */
    PROGRESS_RESULTS_COLUMN_CHANGE("progress.results.column.change"),
    /** Results view: the badge of an added row. */
    PROGRESS_RESULTS_BADGE_ADDED("progress.results.badge.added"),
    /** Results view: the badge of a removed row. */
    PROGRESS_RESULTS_BADGE_REMOVED("progress.results.badge.removed"),
    /** Results view: the badge of a changed row. */
    PROGRESS_RESULTS_BADGE_CHANGED("progress.results.badge.changed"),
    /** Results view: the button that closes it. */
    PROGRESS_RESULTS_DONE("progress.results.done"),
    /** Hover explanation of the button labelled by {@link #PROGRESS_RESULTS_DONE}. */
    PROGRESS_RESULTS_DONE_TIP("progress.results.done.tip"),
    /** Calls view: the line under its title. */
    PROGRESS_CALLS_PURPOSE("progress.calls.purpose"),
    /** A length of time in minutes and seconds, spelled out. */
    DURATION_SPELLED_MS("duration.spelled.ms"),
    /** A length of time under a minute, spelled out. */
    DURATION_SPELLED_S("duration.spelled.s"),
    /** A whole number of minutes, spelled out. */
    DURATION_SPELLED_M("duration.spelled.m"),
    /** A length of time in hours and minutes, spelled out. */
    DURATION_SPELLED_HM("duration.spelled.hm"),
    /** Translating strip: the big line of a finished run; its argument is how long it took. */
    TRANSLATING_STRIP_FINISHED_IN("translating.strip.finishedIn"),
    /** Translating strip: the segments decided of all. */
    TRANSLATING_STRIP_DONE("translating.strip.done"),
    /** Hover explanation of what a segment is, put on every count of segments (strip, Projects, Import, Export). */
    TRANSLATING_STRIP_DONE_TIP("translating.strip.done.tip"),
    /** Translating strip: segments translated with no repair. */
    TRANSLATING_STRIP_TRANSLATED("translating.strip.translated"),
    /** Translating strip: segments accepted after a repair. */
    TRANSLATING_STRIP_FIXED("translating.strip.fixed"),
    /** Translating strip: segments flagged for review. */
    TRANSLATING_STRIP_TO_REVIEW("translating.strip.toReview"),
    /** Translating strip: segments kept as they are. */
    TRANSLATING_STRIP_KEPT("translating.strip.kept"),
    /** Translating strip: accepted segments the final audit doubts. */
    TRANSLATING_STRIP_SECOND_LOOK("translating.strip.secondLook"),
    /** Hover explanation of the count labelled by {@link #TRANSLATING_STRIP_SECOND_LOOK}. */
    TRANSLATING_STRIP_SECOND_LOOK_TIP("translating.strip.secondLook.tip"),
    /** Translating strip: how long the run has been working. */
    TRANSLATING_STRIP_ELAPSED("translating.strip.elapsed"),
    /** Translating strip: the button that opens the segments to review. */
    TRANSLATING_STRIP_REVIEW("translating.strip.review"),
    /** Hover explanation of the button labelled by {@link #TRANSLATING_STRIP_REVIEW}. */
    TRANSLATING_STRIP_REVIEW_TIP("translating.strip.review.tip"),
    /** Translating: the help line while keep-awake is on. */
    TRANSLATING_HELP_SLEEP("translating.help.sleep"),
    /** Translating: the help line while keep-awake is off. */
    TRANSLATING_HELP_SLEEP_OFF("translating.help.sleepOff"),
    /** Translating: the name of the button that closes the help line. */
    TRANSLATING_HELP_CLOSE("translating.help.close"),
    /** Hover explanation of the button labelled by {@link #TRANSLATING_HELP_CLOSE}. */
    TRANSLATING_HELP_CLOSE_TIP("translating.help.close.tip"),
    /** Live card: the first tab. */
    LIVE_TAB_LIVE("live.tab.live"),
    /** Live card: the name of the Simple and Detailed choice. */
    LIVE_MODE("live.mode"),
    /** Hover explanation of the choice labelled by {@link #LIVE_MODE}. */
    LIVE_MODE_TIP("live.mode.tip"),
    /** Live card: the switch that follows each newly translated segment. */
    LIVE_FOLLOW("live.follow"),
    /** Hover explanation of the switch labelled by {@link #LIVE_FOLLOW}. */
    LIVE_FOLLOW_TIP("live.follow.tip"),
    /** Live card: the button that follows the translation again after a pick. */
    LIVE_FOLLOW_AGAIN("live.followAgain"),
    /** Hover explanation of the button labelled by {@link #LIVE_FOLLOW_AGAIN}. */
    LIVE_FOLLOW_AGAIN_TIP("live.followAgain.tip"),
    /** Live card: the button that lists every kept call. */
    LIVE_ALL_CALLS("live.allCalls"),
    /** Hover explanation of the button labelled by {@link #LIVE_ALL_CALLS}. */
    LIVE_ALL_CALLS_TIP("live.allCalls.tip"),
    /** Activity log tab: the choice that lists every line. */
    LIVE_LOG_ALL("live.log.all"),
    /** Hover explanation of the choice labelled by {@link #LIVE_LOG_ALL}. */
    LIVE_LOG_ALL_TIP("live.log.all.tip"),
    /** Activity log tab: the choice that lists only the problems. */
    LIVE_LOG_PROBLEMS("live.log.problems"),
    /** Hover explanation of the choice labelled by {@link #LIVE_LOG_PROBLEMS}. */
    LIVE_LOG_PROBLEMS_TIP("live.log.problems.tip"),
    /** Live row marker: not sent yet, or the run does not work. */
    LIVE_MARKER_WAITING("live.marker.waiting"),
    /** Live row marker: in a call the model works on. */
    LIVE_MARKER_TRANSLATING("live.marker.translating"),
    /** Live row marker: drafted, waiting for the checks that decide it. */
    LIVE_MARKER_DRAFTED("live.marker.drafted"),
    /** Live row marker: accepted. */
    LIVE_MARKER_TRANSLATED("live.marker.translated"),
    /** Live row marker: flagged for review. */
    LIVE_MARKER_TO_REVIEW("live.marker.toReview"),
    /** Live row marker: drafted again on its own after a bad batch reply. */
    LIVE_MARKER_ALONE("live.marker.alone"),
    /** Hover explanation of the marker labelled by {@link #LIVE_MARKER_ALONE}; its argument is the reply's fault in lower case. */
    LIVE_MARKER_ALONE_TIP("live.marker.alone.tip"),
    /** Live row marker: kept as it is. */
    LIVE_MARKER_KEPT("live.marker.kept"),
    /** Live detail: why a flagged segment waits; its argument names the findings. */
    LIVE_DETAIL_WHY("live.detail.why"),
    /** Live detail: the translation of a segment being translated. */
    LIVE_DETAIL_PENDING("live.detail.pending"),
    /** Live detail: the translation of a segment not sent yet. */
    LIVE_DETAIL_NOT_YET("live.detail.notYet"),
    /** Live detail: the heading of the box of what the model works on. */
    LIVE_DETAIL_IN_FLIGHT("live.detail.inFlight"),
    /** Live detail: the box of what the model works on, with nothing in flight. */
    LIVE_DETAIL_IN_FLIGHT_NONE("live.detail.inFlight.none"),
    /** Live detail, Detailed: a segment no call has carried yet. */
    LIVE_DETAIL_NOT_SENT("live.detail.notSent"),
    /** Live card, Detailed: the call line; id, kind, segment count, range or none, attempt, attempts, clock. */
    LIVE_CALL_LINE("live.callLine"),
    /** Live card, Detailed: the name of a draft call on the call line. */
    LIVE_CALL_DRAFT("live.call.draft"),
    /** Live card: a waiting clock against its limit. */
    LIVE_CLOCK_OF("live.clock.of"),
    /** Live card: the line while a call has waited ten seconds; its argument is the clock. */
    LIVE_WAITING_FOR_MODEL("live.waitingForModel"),
    /** Live card: the heading of the scene summary line. */
    LIVE_SUMMARY_TITLE("live.summary.title"),
    /** Live card: the scene summary line before there is one. */
    LIVE_SUMMARY_NONE("live.summary.none"),
    /** Live card: how the scene summary is kept; its argument is the refresh interval. */
    LIVE_SUMMARY_SOURCE("live.summary.source"),
    /** Title of the review desk dialog. */
    REVIEW_DESK_TITLE("review.desk.title"),
    /** The desk's header line; argument 0 is how many are left to review, argument 1 how many there were when the desk opened. */
    REVIEW_DESK_LEFT("review.desk.left"),
    /** The desk's header line while a run translates; arguments as {@link #REVIEW_DESK_LEFT}. */
    REVIEW_DESK_LEFT_READ_ONLY("review.desk.leftReadOnly"),
    /** Button in the read-only banner that pauses the run. */
    REVIEW_DESK_PAUSE("review.desk.pause"),
    /** Hover explanation of {@link #REVIEW_DESK_PAUSE}. */
    REVIEW_DESK_PAUSE_TIP("review.desk.pause.tip"),
    /** Footer button that closes the review desk. */
    REVIEW_CLOSE("review.close"),
    /** Hover explanation of {@link #REVIEW_CLOSE}. */
    REVIEW_CLOSE_TIP("review.close.tip"),
    /** Hover explanation of the desk's filter box. */
    REVIEW_FILTER_TIP("review.filter.tip"),
    /** Filter item; argument 0 is the count. */
    REVIEW_FILTER_TO_REVIEW("review.filter.toReview"),
    /** Hover explanation of {@link #REVIEW_FILTER_TO_REVIEW}. */
    REVIEW_FILTER_TO_REVIEW_TIP("review.filter.toReview.tip"),
    /** Filter item; argument 0 is the count. */
    REVIEW_FILTER_SECOND_LOOK("review.filter.secondLook"),
    /** Hover explanation of {@link #REVIEW_FILTER_SECOND_LOOK}. */
    REVIEW_FILTER_SECOND_LOOK_TIP("review.filter.secondLook.tip"),
    /** Filter item listing every segment. */
    REVIEW_FILTER_ALL("review.filter.all"),
    /** Hover explanation of {@link #REVIEW_FILTER_ALL}. */
    REVIEW_FILTER_ALL_TIP("review.filter.all.tip"),
    /** Hover explanation of the previous-segment button; argument 0 is its shortcut. */
    REVIEW_PREV_TIP("review.prev.tip"),
    /** Hover explanation of the next-segment button; argument 0 is its shortcut. */
    REVIEW_NEXT_TIP("review.next.tip"),
    /** The muted line under the list; arguments are the two shortcuts. */
    REVIEW_SHORTCUTS("review.shortcuts"),
    /** Row marker of a segment still to review. */
    REVIEW_MARKER_TO_REVIEW("review.marker.toReview"),
    /** Row marker of an accepted segment. */
    REVIEW_MARKER_ACCEPTED("review.marker.accepted"),
    /** Row marker of a segment holding the person's edit. */
    REVIEW_MARKER_YOUR_EDIT("review.marker.yourEdit"),
    /** Row marker of a segment left for later. */
    REVIEW_MARKER_LATER("review.marker.later"),
    /** A row's line; argument 0 is why it is listed, argument 1 its text. */
    REVIEW_ROW_LINE("review.row.line"),
    /** The main button on a stored translation. */
    REVIEW_MAIN_ACCEPT("review.main.accept"),
    /** The main button once the text was edited. */
    REVIEW_MAIN_SAVE("review.main.save"),
    /** The main button when accepting resumes a paused run. */
    REVIEW_MAIN_ACCEPT_CONTINUE("review.main.acceptContinue"),
    /** The main button when saving resumes a paused run. */
    REVIEW_MAIN_SAVE_CONTINUE("review.main.saveContinue"),
    /** Footer button that opens the less common actions. */
    REVIEW_MORE("review.more"),
    /** Hover explanation of {@link #REVIEW_MORE}. */
    REVIEW_MORE_TIP("review.more.tip"),
    /** More menu item. */
    REVIEW_MORE_LATER("review.more.later"),
    /** The line under {@link #REVIEW_MORE_LATER}. */
    REVIEW_MORE_LATER_SUB("review.more.later.sub"),
    /** More menu item. */
    REVIEW_MORE_RETRY("review.more.retry"),
    /** The line under {@link #REVIEW_MORE_RETRY}. */
    REVIEW_MORE_RETRY_SUB("review.more.retry.sub"),
    /** More menu item. */
    REVIEW_MORE_RETRY_NOTE("review.more.retryNote"),
    /** The line under {@link #REVIEW_MORE_RETRY_NOTE}. */
    REVIEW_MORE_RETRY_NOTE_SUB("review.more.retryNote.sub"),
    /** More menu item, and the button that confirms it. */
    REVIEW_MORE_UNDO("review.more.undo"),
    /** The line under {@link #REVIEW_MORE_UNDO}. */
    REVIEW_MORE_UNDO_SUB("review.more.undo.sub"),
    /** The line under {@link #REVIEW_MORE_UNDO} when there is no edit. */
    REVIEW_MORE_UNDO_NONE("review.more.undo.none"),
    /** Heading of the inline note panel. */
    REVIEW_NOTE_LABEL("review.note.label"),
    /** Placeholder of the note field. */
    REVIEW_NOTE_PROMPT("review.note.prompt"),
    /** Check box in the note panel. */
    REVIEW_NOTE_LOWER("review.note.lower"),
    /** Hover explanation of {@link #REVIEW_NOTE_LOWER}. */
    REVIEW_NOTE_LOWER_TIP("review.note.lower.tip"),
    /** Button that closes the note panel. */
    REVIEW_NOTE_CANCEL("review.note.cancel"),
    /** Hover explanation of {@link #REVIEW_NOTE_CANCEL}. */
    REVIEW_NOTE_CANCEL_TIP("review.note.cancel.tip"),
    /** Button that retries with the note. */
    REVIEW_NOTE_CONFIRM("review.note.confirm"),
    /** Hover explanation of {@link #REVIEW_NOTE_CONFIRM}. */
    REVIEW_NOTE_CONFIRM_TIP("review.note.confirm.tip"),
    /** Inline question before an edit is undone; argument 0 is the locator. */
    REVIEW_UNDO_QUESTION("review.undo.question"),
    /** Button that keeps the edit. */
    REVIEW_UNDO_KEEP("review.undo.keep"),
    /** Hover explanation of {@link #REVIEW_UNDO_KEEP}. */
    REVIEW_UNDO_KEEP_TIP("review.undo.keep.tip"),
    /** Inline question when typing is not saved; argument 0 is the locator. */
    REVIEW_UNSAVED_QUESTION("review.unsaved.question"),
    /** Button that keeps the typing. */
    REVIEW_UNSAVED_KEEP("review.unsaved.keep"),
    /** Hover explanation of {@link #REVIEW_UNSAVED_KEEP}. */
    REVIEW_UNSAVED_KEEP_TIP("review.unsaved.keep.tip"),
    /** Button that drops the typing. */
    REVIEW_UNSAVED_DISCARD("review.unsaved.discard"),
    /** Hover explanation of {@link #REVIEW_UNSAVED_DISCARD}. */
    REVIEW_UNSAVED_DISCARD_TIP("review.unsaved.discard.tip"),
    /** Detail tab. */
    REVIEW_TAB_WHY("review.tab.why"),
    /** Detail tab. */
    REVIEW_TAB_CONTEXT("review.tab.context"),
    /** Detail tab; argument 0 is the number of suggestions. */
    REVIEW_TAB_SUGGESTION("review.tab.suggestion"),
    /** Line over the context of a segment drafted alone. */
    REVIEW_CONTEXT_INTRO("review.context.intro"),
    /** Line over the context of a segment drafted in a batch. */
    REVIEW_CONTEXT_BATCH("review.context.batch"),
    /** What may be wrong with a name finding. */
    REVIEW_WHY_NAME("review.why.name"),
    /** What may be wrong with a language finding. */
    REVIEW_WHY_WRONG_LANGUAGE("review.why.wrongLanguage"),
    /** What may be wrong with an omission finding. */
    REVIEW_WHY_OMISSION("review.why.omission"),
    /** What may be wrong with any other finding. */
    REVIEW_WHY_LOW_SCORE("review.why.lowScore"),
    /** A finding's plain line; argument 0 is what may be wrong. */
    REVIEW_WHY_LINE("review.why.line"),
    /** A finding's one label; arguments are its kind word, severity and who raised it. */
    REVIEW_FINDING_LABEL("review.finding.label"),
    /** Who raised a finding, from its machine code. */
    REVIEW_RAISER("review.raiser"),
    /** Switch that shows rule codes and figures. */
    REVIEW_TECHNICAL("review.technical"),
    /** Hover explanation of {@link #REVIEW_TECHNICAL}. */
    REVIEW_TECHNICAL_TIP("review.technical.tip"),
    /** Heading of the current text on the Suggestion tab. */
    REVIEW_SUGGESTION_NOW("review.suggestion.now"),
    /** Heading of the proposed text on the Suggestion tab. */
    REVIEW_SUGGESTION_PROPOSED("review.suggestion.proposed"),
    /** Button that applies the suggestion. */
    REVIEW_SUGGESTION_USE("review.suggestion.use"),
    /** Hover explanation of {@link #REVIEW_SUGGESTION_USE}. */
    REVIEW_SUGGESTION_USE_TIP("review.suggestion.use.tip"),
    /** The Suggestion tab with nothing in it. */
    REVIEW_SUGGESTION_NONE("review.suggestion.none"),
    /** Hover explanation of the main button; argument 0 is its shortcut. */
    REVIEW_MAIN_ACCEPT_TIP("review.main.accept.tip"),
    /** Hover explanation of the note field. */
    REVIEW_NOTE_PROMPT_TIP("review.note.prompt.tip"),
    /** Hover explanation of the button that undoes the edit. */
    REVIEW_MORE_UNDO_TIP("review.more.undo.tip"),
    /** Accessible name of the desk's filter box. */
    REVIEW_FILTER("review.filter"),
    /** Accessible name of the previous-segment button. */
    REVIEW_PREV("review.prev"),
    /** Accessible name of the next-segment button. */
    REVIEW_NEXT("review.next"),
    /** Under the checks: why they are off while the form holds something the rules refuse. */
    SETTINGS_CHECKS_BLOCKED("settings.checks.blocked"),
    /** A check's result before it was run. */
    SETTINGS_CHECK_NOT_RUN("settings.check.notRun"),
    /** Link on a check's result that opens the test dialog. */
    SETTINGS_CHECK_DETAILS("settings.check.details"),
    /** Connection result; 0 is the provider's name, 1 the round trip as "41 ms". */
    SETTINGS_CHECK_CONNECTION_OK("settings.check.connection.ok"),
    /** Connection result when the server did not answer; 0 is the provider's name. */
    SETTINGS_CHECK_CONNECTION_FAILED("settings.check.connection.failed"),
    /** Models result when the chosen model is listed; 0 is the count. */
    SETTINGS_CHECK_MODELS_OK("settings.check.models.ok"),
    /** Models result for a server that answers but lists no models. */
    SETTINGS_CHECK_MODELS_SOFT("settings.check.models.soft"),
    /** Inference result; 0 is the time as "1.2 s". */
    SETTINGS_CHECK_INFERENCE_OK("settings.check.inference.ok"),
    /** Result of a check an earlier failure kept from running; 0 is the check's name. */
    SETTINGS_CHECK_SKIPPED("settings.check.skipped"),
    /** Line under the test dialog's title. */
    SETTINGS_TEST_SUBTITLE("settings.test.subtitle"),
    /** Verdict of a passed connection test; 0 is the provider's name. */
    SETTINGS_TEST_VERDICT_CONNECTION("settings.test.verdict.connection"),
    /** Verdict of a passed models test. */
    SETTINGS_TEST_VERDICT_MODELS("settings.test.verdict.models"),
    /** Verdict of a models test that connected and read no list. */
    SETTINGS_TEST_VERDICT_MODELS_SOFT("settings.test.verdict.modelsSoft"),
    /** Verdict of a passed inference test. */
    SETTINGS_TEST_VERDICT_INFERENCE("settings.test.verdict.inference"),
    /** Verdict of a failed test; 0 is the reason. */
    SETTINGS_TEST_VERDICT_FAILED("settings.test.verdict.failed"),
    /** Verdict of a test an earlier failure kept from running. */
    SETTINGS_TEST_VERDICT_SKIPPED("settings.test.verdict.skipped"),
    /** Verdict of a test that was not run. */
    SETTINGS_TEST_VERDICT_NOT_RUN("settings.test.verdict.notRun"),
    /** Verdict while a test is under way. */
    SETTINGS_TEST_VERDICT_RUNNING("settings.test.verdict.running"),
    /** Button that copies the request as a curl command. */
    SETTINGS_TEST_COPY_CURL("settings.test.copyCurl"),
    /** Hover explanation of the button labelled by {@link #SETTINGS_TEST_COPY_CURL}. */
    SETTINGS_TEST_COPY_CURL_TIP("settings.test.copyCurl.tip"),
    /** Heading of the request body column. */
    SETTINGS_TEST_BODY("settings.test.body"),
    /** Heading of the response column. */
    SETTINGS_TEST_RESPONSE("settings.test.response"),
    /** Button that copies a column's text. */
    SETTINGS_TEST_COPY("settings.test.copy"),
    /** Hover explanation of the button labelled by {@link #SETTINGS_TEST_COPY}. */
    SETTINGS_TEST_COPY_TIP("settings.test.copy.tip"),
    /** Shown where a request or reply had no body. */
    SETTINGS_TEST_NO_BODY("settings.test.noBody"),
    /** Button that closes the test dialog. */
    SETTINGS_TEST_CLOSE("settings.test.close"),
    /** Hover explanation of the button labelled by {@link #SETTINGS_TEST_CLOSE}. */
    SETTINGS_TEST_CLOSE_TIP("settings.test.close.tip"),
    /** Hover explanation of the Details link and of the result row beside it. */
    SETTINGS_CHECK_DETAILS_TIP("settings.check.details.tip"),
    /** Hover explanation of the connection test's switch in the test dialog's footer. */
    SETTINGS_STAGE_CONNECTION_TIP("settings.stage.connection.tip"),
    /** Hover explanation of the models test's switch in the test dialog's footer. */
    SETTINGS_STAGE_MODELS_TIP("settings.stage.models.tip"),
    /** Hover explanation of the inference test's switch in the test dialog's footer. */
    SETTINGS_STAGE_INFERENCE_TIP("settings.stage.inference.tip"),
    /** Label of the status metric. */
    SETTINGS_TEST_METRIC_STATUS("settings.test.metric.status"),
    /** Label of the time metric. */
    SETTINGS_TEST_METRIC_TIME("settings.test.metric.time"),
    /** Label of the time to the first token. */
    SETTINGS_TEST_METRIC_FIRST_TOKEN("settings.test.metric.firstToken"),
    /** Label of the generation speed. */
    SETTINGS_TEST_METRIC_SPEED("settings.test.metric.speed"),
    /** Generation speed; 0 is the number of tokens per second. */
    SETTINGS_TEST_METRIC_SPEED_VALUE("settings.test.metric.speedValue"),
    /** Label of the token counts. */
    SETTINGS_TEST_METRIC_TOKENS("settings.test.metric.tokens"),
    /** Token counts; 0 is the prompt's, 1 the reply's. */
    SETTINGS_TEST_METRIC_TOKENS_VALUE("settings.test.metric.tokensValue"),
    /** Label of whether the reply had the structure asked for. */
    SETTINGS_TEST_METRIC_STRUCTURED("settings.test.metric.structured"),
    /** Yes. */
    SETTINGS_TEST_METRIC_YES("settings.test.metric.yes"),
    /** No. */
    SETTINGS_TEST_METRIC_NO("settings.test.metric.no"),
    /** Label of the number of models listed. */
    SETTINGS_TEST_METRIC_MODELS("settings.test.metric.models"),
    /** Label of whether the chosen model was listed. */
    SETTINGS_TEST_METRIC_CHOSEN("settings.test.metric.chosen"),
    /** The chosen model is in the list. */
    SETTINGS_TEST_METRIC_LISTED("settings.test.metric.listed"),
    /** Label of what the server reports about itself. */
    SETTINGS_TEST_METRIC_SERVER("settings.test.metric.server"),
    /** A metric the server did not report. */
    SETTINGS_TEST_METRIC_NONE("settings.test.metric.none"),
    /** HTTP status of an answered request; 0 is the code. */
    SETTINGS_TEST_STATUS_OK("settings.test.status.ok"),
    /** No HTTP status because nothing answered. */
    SETTINGS_TEST_STATUS_NONE("settings.test.status.none"),
    /** Feedback in the test dialog's footer after Copy as curl. */
    SETTINGS_TEST_COPIED_CURL("settings.test.copied.curl"),
    /** Feedback in the test dialog's footer after Copy. */
    SETTINGS_TEST_COPIED("settings.test.copied"),
    /** Button that makes the open provider the current one. */
    SETTINGS_PROVIDER_USE("settings.provider.use"),
    /** Hover explanation of the button labelled by {@link #SETTINGS_PROVIDER_USE}. */
    SETTINGS_PROVIDER_USE_TIP("settings.provider.use.tip"),
    /** Why Use this one is off while the form holds changes. */
    SETTINGS_PROVIDER_USE_DIRTY("settings.provider.use.dirty"),
    /** Title of the question asked when the provider changes in an open, started book; 0 is the new provider's name. */
    DIALOG_SWITCH_PROVIDER_TITLE("dialog.switchProvider.title"),
    /** Body of that question; 0 is the earlier provider, 1 its model. */
    DIALOG_SWITCH_PROVIDER_TEXT("dialog.switchProvider.text"),
    /** Note under that question. */
    DIALOG_SWITCH_PROVIDER_NOTE("dialog.switchProvider.note"),
    /** Button that keeps the earlier provider; 0 is its name. */
    DIALOG_SWITCH_PROVIDER_KEEP("dialog.switchProvider.keep"),
    /** Hover explanation of the button labelled by {@link #DIALOG_SWITCH_PROVIDER_KEEP}. */
    DIALOG_SWITCH_PROVIDER_KEEP_TIP("dialog.switchProvider.keep.tip"),
    /** Button that switches to the new provider; 0 is its name. */
    DIALOG_SWITCH_PROVIDER_USE("dialog.switchProvider.use"),
    /** Hover explanation of the button labelled by {@link #DIALOG_SWITCH_PROVIDER_USE}. */
    DIALOG_SWITCH_PROVIDER_USE_TIP("dialog.switchProvider.use.tip"),
    /** Before Start: the time of a book from a speed an earlier translation measured; 0 is "about 2 h" or the like. */
    ESTIMATE_MEASURED_RUN("estimate.measured.run"),
    /** Hover explanation of the line labelled by {@link #ESTIMATE_MEASURED_RUN}. */
    ESTIMATE_MEASURED_RUN_TIP("estimate.measured.run.tip");

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
