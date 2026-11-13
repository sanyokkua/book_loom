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
            // The model row's quant chip: a good quant draws in the ok role, the same chip the pickers show.
            new Screen(
                    "TRANSLATING_QUANT_CHIP",
                    ViewNames.TRANSLATING,
                    Preparation.READY_QUANT_CHIP,
                    Overlay.NONE,
                    List.of(
                            new Part("#quant-chip", Kind.BACKGROUND, "ok-bg", "#e7efe8", "#2b3a33"),
                            new Part("#quant-chip", Kind.BORDER, "ok-bd", "#bcd4c1", "#3f5a49"),
                            new Part("#quant-chip", Kind.TEXT, "ok-fg", "#4a6c54", "#b0d0b7"))),
            // The ready card's language line under the model row: a model whose list leaves the target out draws its
            // warning in the warning text role.
            new Screen(
                    "TRANSLATING_LANGUAGE_WARNING",
                    ViewNames.TRANSLATING,
                    Preparation.READY_LANGUAGE_WARNING,
                    Overlay.NONE,
                    List.of(new Part("#translating-ready-language-line", Kind.TEXT, "warn-fg", "#835d27", "#e6c086"))),
            // Running, with no banner: the strip's bar track surface-2 and its fill the primary, the full-height live
            // card, and a segment being translated in the info-toned working marker.
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
                            // The stage in work on the strip in the info tone, the others neutral.
                            new Part("#translating-stage-proofread", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#translating-stage-prep", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part("#shell-run-stage", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"))),
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
            // The review desk open over a paused run: its card on the surface role with its border, the editor on
            // surface.
            new Screen(
                    "TRANSLATING_REVIEW",
                    ViewNames.TRANSLATING,
                    Preparation.REVIEW_SELECTED,
                    Overlay.REVIEW_DESK,
                    List.of(
                            new Part("#review-desk", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#review-desk", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#review-target", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"))),
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
