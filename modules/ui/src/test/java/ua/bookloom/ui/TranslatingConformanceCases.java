package ua.bookloom.ui;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.ConformanceCases.Kind;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Part;
import ua.bookloom.ui.ConformanceCases.Preparation;
import ua.bookloom.ui.ConformanceCases.Screen;

/** The conformance cases of the Translating screen's states; they are listed in {@link ConformanceCases}. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingConformanceCases {

    // The run's banner is pinned in the shell's slot, not drawn inside the screen.
    private static final String BANNER = "#shell-banners #translating-banner";

    private static final Part STRIP_FILL =
            new Part("#translating-strip", Kind.BACKGROUND, "surface", "#ffffff", "#33424a");
    private static final Part STRIP_EDGE = new Part("#translating-strip", Kind.BORDER, "border", "#ddd5c8", "#48585f");

    static final List<Screen> SCREENS = List.of(
            // Ready to translate: the strip over the ready card; Ready has no banner and no live card.
            new Screen(
                    "TRANSLATING",
                    ViewNames.TRANSLATING,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            STRIP_FILL,
                            STRIP_EDGE,
                            new Part("#translating-ready-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-ready-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // Running, with no banner: the strip's bar track surface-2 and its fill the primary, the full-height live
            // card, a segment being translated in the info-toned working marker, and the scene summary line.
            new Screen(
                    "TRANSLATING_RUNNING",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_STARTED,
                    Overlay.NONE,
                    List.of(
                            STRIP_FILL,
                            STRIP_EDGE,
                            new Part(
                                    "#translating-strip-bar .track",
                                    Kind.BACKGROUND,
                                    "surface-2",
                                    "#f6f1e8",
                                    "#2e3b41"),
                            new Part("#translating-strip-bar .bar", Kind.BACKGROUND, "primary", "#a58075", "#c4917e"),
                            new Part("#translating-live-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-live-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part(
                                    "#translating-live-rows .state-marker.state-working",
                                    Kind.BACKGROUND,
                                    "info-bg",
                                    "#e5edf0",
                                    "#293940"),
                            // The scene summary is the mockup's info banner (ui-mockup.html: .banner).
                            new Part("#translating-live-summary", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#translating-live-summary", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"))),
            // A finished run: the strip carries the outcome, so there is no outcome card any more.
            new Screen(
                    "TRANSLATING_COMPLETED",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_COMPLETED,
                    Overlay.NONE,
                    List.of(
                            STRIP_FILL,
                            STRIP_EDGE,
                            new Part("#translating-live-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part(BANNER, Kind.BACKGROUND, "ok-bg", "#e7efe8", "#2b3a33"),
                            new Part(BANNER, Kind.BORDER, "ok-bd", "#bcd4c1", "#3f5a49"))),
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
            // A pause on a provider error is the run's own state: the banner takes the err role, light and dark, and
            // so does the strip's bar.
            new Screen(
                    "TRANSLATING_PROVIDER_ERROR",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_PROVIDER_FAILED,
                    Overlay.NONE,
                    List.of(
                            new Part(BANNER, Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part(BANNER, Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"),
                            new Part("#translating-strip-bar > .bar", Kind.BACKGROUND, "err", "#b0574c", "#cc7a6f"))),
            // A run that waits for the provider by itself is a warning, not an error: it will go on without the person.
            new Screen(
                    "TRANSLATING_WAITING_FOR_PROVIDER",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_WAITING_FOR_PROVIDER,
                    Overlay.NONE,
                    List.of(
                            new Part(BANNER, Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part(BANNER, Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))));
}
