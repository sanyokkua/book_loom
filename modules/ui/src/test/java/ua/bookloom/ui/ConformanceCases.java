package ua.bookloom.ui;

import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The data of the conformance test: one {@link Screen} per screen or dialog state, each naming the parts whose role and
 * published value are asserted. A later screen task adds exactly one entry to {@link #SCREENS}. The values are written
 * out by hand from {@code theme.css}'s published catalogue, never read back from the code under test.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConformanceCases {

    /** What a role is painted onto: the fill behind a part, or the line drawn around it. */
    enum Kind {
        BACKGROUND,
        BORDER,
        TEXT
    }

    /** A part of a screen: where to find it, what its role paints, and what the role must resolve to. */
    record Part(String selector, Kind kind, String role, String lightHex, String darkHex) {

        String expected(final ThemeMode block) {
            return block == ThemeMode.DARK ? darkHex : lightHex;
        }
    }

    /** The dialog a screen has open over the shell, if any. */
    enum Overlay {
        NONE,
        ABOUT,
        ERROR_DIALOG,
        REPLACE_RUN,
        RETRY,
        EXPORT_COMPLETE,
        ADD_TERM,
        RESULTS,
        REVIEW_PANEL
    }

    /** What must have happened before the view is read: nothing, or a state reached through the import view model. */
    enum Preparation {
        NONE,
        BOOK_OPENED,
        BOOK_CHECKED,
        BOOK_REFUSED,
        BOOK_DRM_BLOCKED,
        BOOK_UNSUPPORTED,
        BOOK_READING,
        BOOK_ALREADY_STORED,
        LANGUAGE_MISMATCH,
        RUN_COMPLETED,
        RUN_PROVIDER_FAILED,
        RUN_WAITING_FOR_PROVIDER,
        RUN_STARTED,
        REVIEW_SELECTED,
        BOOK_REPORTED,
        GLOSSARY_LISTED,
        GLOSSARY_WHILE_TRANSLATING,
        GLOSSARY_WHILE_HIDDEN,
        BOOK_WITHOUT_LANGUAGE,
        BRIEF_SUGGESTED,
        PROJECTS_LISTED,
        PROJECTS_EMPTY
    }

    /**
     * What is on screen: the view to show (none for the bare shell), what was done to it first, and which dialog, if
     * any, is open over it.
     */
    record Screen(String name, @Nullable ViewNames view, Preparation preparation, Overlay overlay, List<Part> parts) {

        Screen(final String name, final @Nullable ViewNames view, final Overlay overlay, final List<Part> parts) {
            this(name, view, Preparation.NONE, overlay, parts);
        }
    }

    private static final Part CARD_FILL =
            new Part("#export-screen .card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a");
    private static final Part CARD_EDGE = new Part("#export-screen .card", Kind.BORDER, "border", "#ddd5c8", "#48585f");
    private static final Part BOX_FILL =
            new Part("#export-aux-bilingual .box", Kind.BACKGROUND, "surface", "#ffffff", "#33424a");
    private static final Part BOX_EDGE =
            new Part("#export-aux-bilingual .box", Kind.BORDER, "input-border", "#988462", "#7d949e");
    private static final Part SWITCH_TRACK =
            new Part("#export-aux-consistency .thumb-area", Kind.BACKGROUND, "toggle-off", "#87939a", "#87939a");
    private static final Part SWITCH_THUMB =
            new Part("#export-aux-consistency .thumb", Kind.BACKGROUND, "toggle-thumb", "#ffffff", "#ffffff");

    /** One entry per screen; a later screen task adds exactly one line here. */
    private static final List<Screen> BASE = List.of(
            new Screen(
                    "SHELL",
                    null,
                    Overlay.NONE,
                    List.of(
                            new Part("#shell-title-bar", Kind.BACKGROUND, "title-bg", "#324148", "#1c2429"),
                            new Part("#shell-nav", Kind.BACKGROUND, "nav-bg", "#3a4a52", "#20292e"),
                            new Part(".shell-toolbar", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part(".shell-toolbar", Kind.BORDER, "divider", "#eae4d8", "#3c4a51"))),
            // The title bar's run status: its text is painted with the title bar's foreground role.
            new Screen(
                    "SHELL_RUN_STATUS",
                    null,
                    Preparation.RUN_STARTED,
                    Overlay.NONE,
                    List.of(new Part(".run-status-text", Kind.TEXT, "title-fg", "#dfe4e6", "#dfe4e6"))),
            new Screen(
                    "ABOUT_DIALOG",
                    null,
                    Overlay.ABOUT,
                    List.of(
                            new Part("#about-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#about-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "ERROR_DIALOG",
                    null,
                    Overlay.ERROR_DIALOG,
                    List.of(
                            new Part("#error-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#error-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "REPLACE_RUN_DIALOG",
                    null,
                    Overlay.REPLACE_RUN,
                    List.of(
                            new Part("#open-another-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#open-another-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "RETRY_DIALOG",
                    null,
                    Overlay.RETRY,
                    List.of(
                            new Part("#retry-note-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#retry-note-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "EXPORT_COMPLETE_DIALOG",
                    null,
                    Overlay.EXPORT_COMPLETE,
                    List.of(
                            new Part("#export-complete-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-complete-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "SETTINGS",
                    ViewNames.SETTINGS,
                    Overlay.NONE,
                    List.of(
                            new Part("#settings-provider-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#settings-provider-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#settings-model", Kind.BORDER, "input-border", "#988462", "#7d949e"),
                            // The selected tab is an underline of the primary role, not a filled tab.
                            new Part("#settings-tab-providers", Kind.BORDER, "primary", "#a58075", "#c4917e"))),
            // The drop zone is the mockup's dashed sand box: sand-soft fill, sand-strong line.
            new Screen(
                    "IMPORT",
                    ViewNames.IMPORT,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-dropzone", Kind.BACKGROUND, "sand-soft", "#f2e9db", "#3a3a37"),
                            new Part("#import-dropzone", Kind.BORDER, "sand-strong", "#dcc4a3", "#6d5f4a"))),
            new Screen(
                    "IMPORT_REFUSED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_REFUSED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-verdict", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#import-verdict", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            new Screen(
                    "IMPORT_DRM_BLOCKED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_DRM_BLOCKED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-verdict", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#import-verdict", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            new Screen(
                    "IMPORT_UNSUPPORTED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_UNSUPPORTED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-verdict", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#import-verdict", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            new Screen(
                    "IMPORT_LANGUAGE_MISMATCH",
                    ViewNames.IMPORT,
                    Preparation.LANGUAGE_MISMATCH,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-verdict", Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part("#import-verdict", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))),
            new Screen(
                    "BOOK_BRIEF",
                    ViewNames.BOOK_BRIEF,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part("#brief-languages-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#brief-languages-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#brief-quality-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#brief-aux-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#brief-policies-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            // The predicted Tone & style is the right column's one card, drawn like the decisions.
                            new Part("#brief-tone-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#brief-tone-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "BOOK_BRIEF_AUTO_SOURCE",
                    ViewNames.BOOK_BRIEF,
                    Preparation.BOOK_WITHOUT_LANGUAGE,
                    Overlay.NONE,
                    List.of(new Part("#brief-source-undeclared", Kind.TEXT, "muted", "#5d686d", "#bdc9cd"))),
            // After the model's suggestion: a field nothing supported wears the warning chip, in both themes; and the
            // model's language proposal under an Auto source reads as a hint beside its button.
            new Screen(
                    "BOOK_BRIEF_SUGGESTED",
                    ViewNames.BOOK_BRIEF,
                    Preparation.BRIEF_SUGGESTED,
                    Overlay.NONE,
                    List.of(
                            new Part(
                                    "#brief-evidence-genre-uncertain",
                                    Kind.BACKGROUND,
                                    "warn-bg",
                                    "#f6ecd8",
                                    "#3a3327"),
                            new Part("#brief-evidence-genre-uncertain", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"),
                            new Part("#brief-evidence-genre-uncertain", Kind.TEXT, "warn-fg", "#835d27", "#e6c086"),
                            new Part("#brief-evidence-genre", Kind.TEXT, "muted", "#5d686d", "#bdc9cd"),
                            new Part("#brief-source-proposal-text", Kind.TEXT, "muted", "#5d686d", "#bdc9cd"))),
            new Screen(
                    "BOOK_BRIEF_NO_BOOK",
                    ViewNames.BOOK_BRIEF,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#nobook-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#nobook-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            ConformanceResultsCase.RESULTS_DIALOG,
            // The screen that writes the book: cards and result tiles on the surface role, the side-file boxes and the
            // consistency track on the cool border role. It needs an open book to show them; the finished state
            // follows a real export.
            new Screen(
                    "EXPORT",
                    ViewNames.EXPORT,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(CARD_FILL, CARD_EDGE, BOX_FILL, BOX_EDGE, SWITCH_TRACK, SWITCH_THUMB)),
            new Screen(
                    "EXPORT_COMPLETED",
                    ViewNames.EXPORT,
                    Preparation.BOOK_REPORTED,
                    Overlay.NONE,
                    List.of(
                            CARD_FILL,
                            CARD_EDGE,
                            new Part("#export-written", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-written", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#export-auto", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-auto", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            BOX_FILL,
                            BOX_EDGE,
                            SWITCH_TRACK,
                            SWITCH_THUMB)));

    /** Every screen, the import screen's open-book cases and the names screen's included. */
    static final List<Screen> SCREENS = Stream.of(
                    BASE.stream(),
                    ImportConformanceCases.SCREENS.stream(),
                    NamesStyleConformanceCases.SCREENS.stream(),
                    ProjectsConformanceCases.SCREENS.stream(),
                    TranslatingConformanceCases.SCREENS.stream())
            .flatMap(cases -> cases)
            .toList();
}
