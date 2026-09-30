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
    /** Navigation entry and breadcrumb of the import screen. */
    NAV_IMPORT("nav.import"),
    /** Navigation entry and breadcrumb of the book brief screen. */
    NAV_BRIEF("nav.brief"),
    /** Navigation entry and breadcrumb of the structure screen. */
    NAV_STRUCTURE("nav.structure"),
    /** Navigation entry and breadcrumb of the names-and-style screen. */
    NAV_NAMES_AND_STYLE("nav.namesAndStyle"),
    /** Navigation entry and breadcrumb of the translating dashboard. */
    NAV_TRANSLATING("nav.translating"),
    /** Navigation entry and breadcrumb of the export screen. */
    NAV_EXPORT("nav.export"),
    /** Navigation entry and breadcrumb of the settings screen. */
    NAV_SETTINGS("nav.settings"),
    /** The navigation footer naming the chosen provider and model: provider, model. */
    NAV_FOOTER_SELECTION("nav.footer.selection"),
    /** The navigation footer while no model is chosen. */
    NAV_FOOTER_NO_MODEL("nav.footer.noModel"),
    /** The product name in the title bar; the same in every language. */
    SHELL_TITLE("shell.title"),
    /** Title-bar control label offered while the light block is in force. */
    SHELL_THEME_TO_DARK("shell.themeToDark"),
    /** Title-bar control label offered while the dark block is in force. */
    SHELL_THEME_TO_LIGHT("shell.themeToLight"),
    /** Title-bar action that opens the About dialog. */
    SHELL_ABOUT("shell.about"),
    /** About dialog subtitle; argument 0 is the build version. */
    ABOUT_SUBTITLE("about.subtitle"),
    /** About dialog description of the product. */
    ABOUT_DESCRIPTION("about.description"),
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
    /** State text once every segment is decided. */
    SHELL_RUN_FINISHED("shell.run.finished"),
    /** State text once the run ended in failure; argument 0 is the whole-number percentage. */
    SHELL_RUN_FAILED("shell.run.failed"),
    /** Title-bar elapsed time; argument 0 is the formatted duration. */
    SHELL_RUN_ELAPSED("shell.run.elapsed"),
    /** Title-bar time left; argument 0 is the formatted duration. */
    SHELL_RUN_LEFT("shell.run.left"),
    /** Title-bar run control that pauses the run. */
    SHELL_RUN_PAUSE("shell.run.pause"),
    /** Title-bar run control that resumes the run. */
    SHELL_RUN_RESUME("shell.run.resume"),
    /** A duration of an hour or more; argument 0 is the hours, argument 1 the two-digit minutes, both Strings. */
    DURATION_HM("duration.hm"),
    /** A duration of a minute or more, under an hour; argument 0 is the minutes, a String. */
    DURATION_M("duration.m"),
    /** A duration under a minute; argument 0 is the seconds, a String. */
    DURATION_S("duration.s"),
    /** Label of a button that dismisses a dialog. */
    COMMON_CLOSE("common.close"),
    /** Activity-log entry for an accepted segment; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_ACCEPTED("log.accepted"),
    /** Activity-log entry for a repaired segment; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_REPAIRED("log.repaired"),
    /** Activity-log entry for an applied glossary; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_GLOSSARY_APPLIED("log.glossaryApplied"),
    /** Activity-log entry for an updated rolling summary; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_SUMMARY_UPDATED("log.summaryUpdated"),
    /** Activity-log entry for a retried segment; argument 0 is the segment id, passed as a String so it is never grouped like a number, argument 1 the attempt number. */
    LOG_RETRIED("log.retried"),
    /** Activity-log entry for a recoverable segment error; argument 0 is the segment id, passed as a String so it is never grouped like a number. */
    LOG_SEGMENT_ERROR("log.segmentError"),
    /** Activity-log entry for a run milestone; argument 0 is one of the tokens stageStarted, paused, resumed or finished, selecting the catalogue's own wording. */
    LOG_MILESTONE("log.milestone"),
    /** Counted sentence on the translating dashboard; argument 0 is the number of segments left. */
    TRANSLATING_SEGMENTS_REMAINING("translating.segmentsRemaining"),
    /** Label of the error dialog's button that runs the failed action again. */
    ERROR_RETRY("error.retry"),
    /** Label of the error dialog's control that reveals the failure's details while they are folded away. */
    ERROR_DETAILS_SHOW("error.details.show"),
    /** Label of the error dialog's control that folds the failure's details away while they are shown. */
    ERROR_DETAILS_HIDE("error.details.hide"),
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
    /** Label of the unavailable action that adds a provider. */
    SETTINGS_PROVIDER_ADD("settings.provider.add"),
    /** Label of the unavailable action that edits the selected provider. */
    SETTINGS_PROVIDER_EDIT("settings.provider.edit"),
    /** Label of the unavailable action that deletes the selected provider. */
    SETTINGS_PROVIDER_DELETE("settings.provider.delete"),
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
    /** Shown in place of the model while none is chosen. */
    SETTINGS_MODEL_NOT_CHOSEN("settings.model.notChosen"),
    /** Label of the action that checks the selected provider. */
    SETTINGS_CHECK_ACTION("settings.check.action"),
    /** Shown while a provider check is running. */
    SETTINGS_CHECK_PROGRESS("settings.check.progress"),
    /** Hint shown while no model is chosen, because the check needs one. */
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
    /** Option of the theme selector that always uses the dark block. */
    APPEARANCE_THEME_DARK("appearance.theme.dark"),
    /** Option of the theme selector that follows the operating system's colour scheme. */
    APPEARANCE_THEME_SYSTEM("appearance.theme.system"),
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
    /** Heading of the book-brief screen. */
    BRIEF_TITLE("brief.title"),
    /** Line under the heading saying what the brief is for and that most of it is not used yet. */
    BRIEF_SUBTITLE("brief.subtitle"),
    /** Heading of the card holding the source and target languages. */
    BRIEF_CARD_LANGUAGES("brief.card.languages"),
    /** Heading of the card holding the destination path and the overwrite choice. */
    BRIEF_CARD_DESTINATION("brief.card.destination"),
    /** Heading of the tone-and-style card. */
    BRIEF_CARD_TONE("brief.card.tone"),
    /** Heading of the translation-policies card. */
    BRIEF_CARD_POLICIES("brief.card.policies"),
    /** Heading of the card of auxiliary texts that can be translated as well. */
    BRIEF_CARD_ALSO("brief.card.also"),
    /** Heading of the quality-versus-speed card. */
    BRIEF_CARD_QUALITY("brief.card.quality"),
    /** Tag on a card whose choices nothing reads yet. */
    BRIEF_SOON("brief.soon"),
    /** Label of the source language, which the book declares and a person cannot change. */
    BRIEF_SOURCE_LABEL("brief.source.label"),
    /** Shown in place of the source language when the book declares none. */
    BRIEF_SOURCE_UNDECLARED("brief.source.undeclared"),
    /** Label of the target-language picker. */
    BRIEF_TARGET_LABEL("brief.target.label"),
    /** Label of the destination path field. */
    BRIEF_DESTINATION_LABEL("brief.destination.label"),
    /** Button that opens the file chooser for the destination. */
    BRIEF_DESTINATION_BROWSE("brief.destination.browse"),
    /** Title of the file chooser for the destination. */
    BRIEF_DESTINATION_CHOOSER("brief.destination.chooser"),
    /** Heading of the warning that a file already exists at the destination. */
    BRIEF_DESTINATION_EXISTS_TITLE("brief.destination.exists.title"),
    /** Body of the occupied-destination warning. */
    BRIEF_DESTINATION_EXISTS_TEXT("brief.destination.exists.text"),
    /** Label of the switch that allows replacing an existing destination file. */
    BRIEF_OVERWRITE_LABEL("brief.overwrite.label"),
    /** Label of the genre field. */
    BRIEF_TONE_GENRE("brief.tone.genre"),
    /** Prompt text of the genre field. */
    BRIEF_TONE_GENRE_PROMPT("brief.tone.genre.prompt"),
    /** Label of the register picker. */
    BRIEF_TONE_REGISTER("brief.tone.register"),
    /** Register option: formal and literary. */
    BRIEF_REGISTER_FORMAL("brief.register.formal"),
    /** Register option: neutral. */
    BRIEF_REGISTER_NEUTRAL("brief.register.neutral"),
    /** Register option: casual. */
    BRIEF_REGISTER_CASUAL("brief.register.casual"),
    /** Label of the narrative-voice and era notes field. */
    BRIEF_TONE_VOICE("brief.tone.voice"),
    /** Prompt text of the narrative-voice field. */
    BRIEF_TONE_VOICE_PROMPT("brief.tone.voice.prompt"),
    /** Label of the audience field. */
    BRIEF_TONE_AUDIENCE("brief.tone.audience"),
    /** Prompt text of the audience field. */
    BRIEF_TONE_AUDIENCE_PROMPT("brief.tone.audience.prompt"),
    /** Label of the character-and-place-names policy. */
    BRIEF_POLICY_NAMES("brief.policy.names"),
    /** Label of the foreign-language-passages policy. */
    BRIEF_POLICY_FOREIGN("brief.policy.foreign"),
    /** Hint under the foreign-passages policy. */
    BRIEF_POLICY_FOREIGN_HINT("brief.policy.foreign.hint"),
    /** Label of the footnotes policy. */
    BRIEF_POLICY_FOOTNOTES("brief.policy.footnotes"),
    /** Label of the units policy. */
    BRIEF_POLICY_UNITS("brief.policy.units"),
    /** Label of the faithful-to-natural balance slider. */
    BRIEF_POLICY_BALANCE("brief.policy.balance"),
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
    /** Switch for translating image alternative text. */
    BRIEF_AUX_ALT("brief.aux.alt"),
    /** Switch for translating the book's title and author metadata. */
    BRIEF_AUX_METADATA("brief.aux.metadata"),
    /** Switch for translating values in the front matter. */
    BRIEF_AUX_FRONTMATTER("brief.aux.frontmatter"),
    /** Hint under the front-matter switch. */
    BRIEF_AUX_FRONTMATTER_HINT("brief.aux.frontmatter.hint"),
    /** Quality option: fastest. */
    BRIEF_QUALITY_FAST("brief.quality.fast"),
    /** Quality option: balanced. */
    BRIEF_QUALITY_BALANCED("brief.quality.balanced"),
    /** Quality option: best quality. */
    BRIEF_QUALITY_MAX("brief.quality.max"),
    /** Hint describing what the balanced quality setting does. */
    BRIEF_QUALITY_HINT("brief.quality.hint"),
    /** Button that returns from the brief to the import screen. */
    BRIEF_BACK("brief.back"),
    /** Button that moves on from the brief to the next step. */
    BRIEF_CONTINUE("brief.continue"),
    /** Heading of the state shown by a screen that needs a book when none is open. */
    NOBOOK_TITLE("nobook.title"),
    /** Sentence reporting that no book has been opened yet. */
    NOBOOK_REPORT("nobook.report"),
    /** Button from the no-book state to the import screen. */
    NOBOOK_OPEN("nobook.open"),
    /** Heading of the structure screen. */
    STRUCTURE_TITLE("structure.title"),
    /** Position of a unit in reading order; argument 0 is the zero-based position, passed as an Integer. */
    STRUCTURE_UNTITLED("structure.untitled"),
    /** Total of translatable segments beneath the list; argument 0 is the count, passed as an Integer. */
    STRUCTURE_TOTAL("structure.total"),
    /** Button that moves on from the structure screen to the next available step. */
    STRUCTURE_CONTINUE("structure.continue"),
    /** Heading of the names and style screen. */
    NAMES_STYLE_TITLE("namesStyle.title"),
    /** One line under the heading saying what the screen is for. */
    NAMES_STYLE_SUBTITLE("namesStyle.subtitle"),
    /** Information banner saying the step can be skipped. */
    NAMES_STYLE_SKIP("namesStyle.skip"),
    /** Button that returns from names and style to the structure screen. */
    NAMES_STYLE_BACK("namesStyle.back"),
    /** Button that starts the run from names and style. */
    NAMES_STYLE_START("namesStyle.start"),
    /** Heading of the translating dashboard. */
    TRANSLATING_TITLE("translating.title"),
    /** Button that starts the first run from the dashboard. */
    TRANSLATING_START("translating.start"),
    /** Button that asks the run to pause at its next boundary. */
    TRANSLATING_PAUSE("translating.pause"),
    /** Button that asks a paused run to continue. */
    TRANSLATING_RESUME("translating.resume"),
    /** Button that asks the run to end. */
    TRANSLATING_STOP("translating.stop"),
    /** Segments decided over the total, under the progress bar; argument 0 is the accepted plus flagged count and argument 1 the total, both passed as Integers. */
    TRANSLATING_PROGRESS("translating.progress"),
    /** Caption of the accepted-segments tile. */
    TRANSLATING_COUNT_ACCEPTED("translating.count.accepted"),
    /** Caption of the flagged-segments tile. */
    TRANSLATING_COUNT_FLAGGED("translating.count.flagged"),
    /** Caption of the remaining-segments tile. */
    TRANSLATING_COUNT_REMAINING("translating.count.remaining"),
    /** Caption of the total-segments tile. */
    TRANSLATING_COUNT_TOTAL("translating.count.total"),
    /** Heading of the activity log card. */
    TRANSLATING_LOG_TITLE("translating.log.title"),
    /** Text shown in the activity log before the run has decided anything. */
    TRANSLATING_LOG_EMPTY("translating.log.empty"),
    /** Banner title of a run state; argument 0 is the state's token (idle to failed); stopped and failed are neutral. */
    TRANSLATING_STATE_TITLE("translating.stateTitle"),
    /** Banner text of a run state; argument 0 is the same token as for {@link #TRANSLATING_STATE_TITLE}. */
    TRANSLATING_STATE_TEXT("translating.stateText"),
    /** Banner title of a provider failure; argument 0 is the failure's error code name, passed as data. */
    TRANSLATING_PROVIDER_TITLE("translating.providerTitle"),
    /** Banner title of a failure reported in place, such as a destination that may not be replaced. */
    TRANSLATING_REFUSED_TITLE("translating.refusedTitle"),
    /** Banner title of a start refused because an input is missing. */
    TRANSLATING_MISSING_TITLE("translating.missingTitle"),
    /** Banner text naming the missing input; argument 0 is its token (book or model). */
    TRANSLATING_MISSING_TEXT("translating.missingText"),
    /** Banner text while the model has not answered for a while; argument 0 is the wait as {@code m:ss}, passed as a String. */
    TRANSLATING_WAITING_FOR_MODEL("translating.waitingForModel"),
    /** Button on the provider-error banner that opens the provider settings. */
    TRANSLATING_OPEN_SETTINGS("translating.openSettings"),
    /** Heading of the export screen. */
    EXPORT_TITLE("export.title"),
    /** Line under the export heading saying the run already wrote the file. */
    EXPORT_SUBTITLE("export.subtitle"),
    /** Heading of the export screen's empty state. */
    EXPORT_EMPTY_TITLE("export.empty.title"),
    /** Text of the export screen's empty state, naming what produces the book. */
    EXPORT_EMPTY_TEXT("export.empty.text"),
    /** Heading of the card reporting the written file. */
    EXPORT_CARD_TITLE("export.card.title"),
    /** Label of the row naming the written file's format. */
    EXPORT_FORMAT_LABEL("export.format.label"),
    /** Hint under the format row: the book is written in the format it was opened in. */
    EXPORT_FORMAT_HINT("export.format.hint"),
    /** Label of the row giving the path the book was written to. */
    EXPORT_PATH_LABEL("export.path.label"),
    /** Button that shows the written file in the system file manager. */
    EXPORT_REVEAL("export.reveal"),
    /** Heading of the card of additional outputs nothing produces yet. */
    EXPORT_ALSO_TITLE("export.also.title"),
    /** Label of the unavailable glossary output. */
    EXPORT_AUX_GLOSSARY("export.aux.glossary"),
    /** Label of the unavailable bilingual-copy output. */
    EXPORT_AUX_BILINGUAL("export.aux.bilingual"),
    /** Label of the unavailable quality-report output. */
    EXPORT_AUX_REPORT("export.aux.report"),
    /** Heading of the card holding the unavailable final consistency pass. */
    EXPORT_CONSISTENCY_TITLE("export.consistency.title"),
    /** Note under the consistency-pass switch saying what it would do. */
    EXPORT_CONSISTENCY_NOTE("export.consistency.note"),
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
    /** Button that discards the run and imports the other book. */
    DIALOG_REPLACE_RUN_CONFIRM("dialog.replaceRun.confirm"),
    /** Line shown while the discarded run is being stopped. */
    DIALOG_REPLACE_RUN_STOPPING("dialog.replaceRun.stopping");

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
