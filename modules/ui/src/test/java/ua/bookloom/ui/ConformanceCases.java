package ua.bookloom.ui;

import java.util.List;
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
        LANGUAGE_MISMATCH,
        RUN_COMPLETED,
        RUN_PROVIDER_FAILED,
        RUN_WAITING_FOR_PROVIDER,
        RUN_STARTED,
        REVIEW_SELECTED,
        BOOK_REPORTED,
        GLOSSARY_LISTED
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
            new Part("#export-aux-bilingual .box", Kind.BORDER, "border-cool", "#cdd2d3", "#48585f");
    private static final Part SWITCH_TRACK =
            new Part("#export-aux-consistency .thumb-area", Kind.BACKGROUND, "toggle-off", "#b2babd", "#b2babd");
    private static final Part SWITCH_THUMB =
            new Part("#export-aux-consistency .thumb", Kind.BACKGROUND, "toggle-thumb", "#ffffff", "#ffffff");

    /** One entry per screen; a later screen task adds exactly one line here. */
    static final List<Screen> SCREENS = List.of(
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
                            new Part("#replace-run-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#replace-run-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
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
                            new Part("#settings-model", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
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
                    "IMPORT_DETECTED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#import-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "IMPORT_REFUSED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_REFUSED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-refusal", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#import-refusal", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            new Screen(
                    "IMPORT_DRM_BLOCKED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_DRM_BLOCKED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-drm", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#import-drm", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            new Screen(
                    "IMPORT_UNSUPPORTED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_UNSUPPORTED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-unsupported", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#import-unsupported", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            new Screen(
                    "IMPORT_LANGUAGE_MISMATCH",
                    ViewNames.IMPORT,
                    Preparation.LANGUAGE_MISMATCH,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-mismatch", Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part("#import-mismatch", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))),
            new Screen(
                    "BOOK_BRIEF",
                    ViewNames.BOOK_BRIEF,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part("#brief-languages-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#brief-languages-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#brief-quality-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#brief-aux-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"))),
            new Screen(
                    "BOOK_BRIEF_NO_BOOK",
                    ViewNames.BOOK_BRIEF,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#nobook-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#nobook-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "STRUCTURE",
                    ViewNames.STRUCTURE,
                    Preparation.BOOK_CHECKED,
                    Overlay.NONE,
                    List.of(
                            new Part("#structure-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-segments-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-segments-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-stats-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-checks-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part(".count-pill", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part(".count-pill", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-chunk-warning", Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part("#structure-chunk-warning", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))),
            new Screen(
                    "STRUCTURE_NO_BOOK",
                    ViewNames.STRUCTURE,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#nobook-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#nobook-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // The skip note is the info role, light and dark.
            new Screen(
                    "NAMES_STYLE",
                    ViewNames.NAMES_STYLE,
                    Preparation.GLOSSARY_LISTED,
                    Overlay.NONE,
                    List.of(
                            new Part("#names-style-banner", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#names-style-banner", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"),
                            new Part("#names-style-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#names-style-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            // The scanned term is readable, strong text in the glossary table.
                            new Part(".glossary-term", Kind.TEXT, "text-strong", "#22303a", "#f4f7f8"))),
            // The Add term card, opened from the glossary card's header over the names screen.
            new Screen(
                    "ADD_TERM_DIALOG",
                    ViewNames.NAMES_STYLE,
                    Preparation.BOOK_OPENED,
                    Overlay.ADD_TERM,
                    List.of(
                            new Part("#add-term-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#add-term-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // Ready to translate: the ready card and the banner in the info role.
            new Screen(
                    "TRANSLATING",
                    ViewNames.TRANSLATING,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-ready-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-ready-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#translating-banner", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#translating-banner", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"))),
            // Running: the bar's track surface-2 and its fill the primary, the tiles, the live panel and the log.
            new Screen(
                    "TRANSLATING_RUNNING",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_STARTED,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-progress-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-progress-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part(
                                    "#translating-progress .track", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part("#translating-progress .bar", Kind.BACKGROUND, "primary", "#a58075", "#c4917e"),
                            new Part("#translating-tile-accepted", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-tile-accepted", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#translating-log-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-log-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#translating-live-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-live-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            // The call's segments sit in a framed scroll area; its state chip takes the waiting role.
                            new Part(
                                    "#translating-live-card-current-segments",
                                    Kind.BACKGROUND,
                                    "surface",
                                    "#ffffff",
                                    "#33424a"),
                            new Part(
                                    "#translating-live-card-current-state",
                                    Kind.BACKGROUND,
                                    "warn-bg",
                                    "#f6ecd8",
                                    "#3a3327"),
                            // The prompt context is a tinted, framed body under the segments.
                            new Part(
                                    "#translating-live-card-current-prompt-body",
                                    Kind.BACKGROUND,
                                    "surface-alt",
                                    "#fbf9f4",
                                    "#38474f"),
                            new Part(
                                    "#translating-live-card-current-prompt-body",
                                    Kind.BORDER,
                                    "border",
                                    "#ddd5c8",
                                    "#48585f"),
                            new Part("#translating-banner", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#translating-banner", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"))),
            new Screen(
                    "TRANSLATING_COMPLETED",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_COMPLETED,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-outcome-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-outcome-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part(
                                    "#translating-outcome-tile-accepted",
                                    Kind.BACKGROUND,
                                    "surface",
                                    "#ffffff",
                                    "#33424a"),
                            new Part("#translating-banner", Kind.BACKGROUND, "ok-bg", "#e7efe8", "#2b3a33"),
                            new Part("#translating-banner", Kind.BORDER, "ok-bd", "#bcd4c1", "#3f5a49"))),
            // The review panel open beside a paused run: its card on the surface role, the judge badge on surface-2.
            new Screen(
                    "TRANSLATING_REVIEW",
                    ViewNames.TRANSLATING,
                    Preparation.REVIEW_SELECTED,
                    Overlay.REVIEW_PANEL,
                    List.of(
                            new Part("#review-panel", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#review-panel", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#review-judge", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part("#review-target", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            // A finding's kind is strong text and its note body text, readable on the card in both
                            // blocks.
                            new Part(".finding-kind", Kind.TEXT, "text-strong", "#22303a", "#f4f7f8"),
                            new Part(".finding-note", Kind.TEXT, "text", "#2c3941", "#e6ebed"))),
            // A pause on a provider error is the run's own state: the banner takes the err role, light and dark.
            new Screen(
                    "TRANSLATING_PROVIDER_ERROR",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_PROVIDER_FAILED,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-banner", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#translating-banner", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"),
                            new Part("#translating-progress > .bar", Kind.BACKGROUND, "err", "#b0574c", "#cc7a6f"))),
            // A run that waits for the provider by itself is a warning, not an error: it will go on without the person.
            new Screen(
                    "TRANSLATING_WAITING_FOR_PROVIDER",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_WAITING_FOR_PROVIDER,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-banner", Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part("#translating-banner", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))),
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
}
