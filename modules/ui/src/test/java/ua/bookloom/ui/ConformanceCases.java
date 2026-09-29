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
        BORDER
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
        REPLACE_RUN
    }

    /** What must have happened before the view is read: nothing, or a state reached through the import view model. */
    enum Preparation {
        NONE,
        BOOK_OPENED,
        BOOK_REFUSED,
        LANGUAGE_MISMATCH,
        RUN_COMPLETED,
        RUN_PROVIDER_FAILED,
        BOOK_REPORTED
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
            new Part("#export-aux-glossary .box", Kind.BACKGROUND, "surface", "#ffffff", "#33424a");
    private static final Part BOX_EDGE =
            new Part("#export-aux-glossary .box", Kind.BORDER, "border-cool", "#cdd2d3", "#48585f");
    private static final Part SWITCH_TRACK =
            new Part("#export-aux-consistency .thumb-area", Kind.BACKGROUND, "border-cool", "#cdd2d3", "#48585f");

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
                    "SETTINGS",
                    ViewNames.SETTINGS,
                    Overlay.NONE,
                    List.of(
                            new Part("#settings-provider-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#settings-provider-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#settings-model", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
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
                            new Part("#brief-languages-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
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
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part("#structure-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "STRUCTURE_NO_BOOK",
                    ViewNames.STRUCTURE,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#nobook-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#nobook-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // Ready to translate: the banner is the info role, the bar's track surface-2 and its fill the primary.
            new Screen(
                    "TRANSLATING",
                    ViewNames.TRANSLATING,
                    Preparation.NONE,
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
                            new Part("#translating-banner", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#translating-banner", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"))),
            new Screen(
                    "TRANSLATING_COMPLETED",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_COMPLETED,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-banner", Kind.BACKGROUND, "ok-bg", "#e7efe8", "#2b3a33"),
                            new Part("#translating-banner", Kind.BORDER, "ok-bd", "#bcd4c1", "#3f5a49"))),
            // A pause on a provider error is the run's own state: the banner takes the err role, light and dark.
            new Screen(
                    "TRANSLATING_PROVIDER_ERROR",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_PROVIDER_FAILED,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-banner", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#translating-banner", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            // The report screen: cards and count tiles on the surface role, the unavailable controls' boxes and track
            // on
            // the cool border role. Both states keep the right-hand "also export" cards, so both have a card.
            new Screen(
                    "EXPORT",
                    ViewNames.EXPORT,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(CARD_FILL, CARD_EDGE, BOX_FILL, BOX_EDGE, SWITCH_TRACK)),
            new Screen(
                    "EXPORT_COMPLETED",
                    ViewNames.EXPORT,
                    Preparation.BOOK_REPORTED,
                    Overlay.NONE,
                    List.of(
                            CARD_FILL,
                            CARD_EDGE,
                            new Part("#export-accepted", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-accepted", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#export-flagged", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-flagged", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            BOX_FILL,
                            BOX_EDGE,
                            SWITCH_TRACK)));
}
