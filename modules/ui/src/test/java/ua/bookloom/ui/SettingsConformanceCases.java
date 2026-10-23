package ua.bookloom.ui;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.ConformanceCases.Kind;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Part;
import ua.bookloom.ui.ConformanceCases.Preparation;
import ua.bookloom.ui.ConformanceCases.Screen;

/**
 * The conformance cases of the Providers form beyond the base Settings case: the summary card, the unsaved mark in the
 * pinned footer, the opened connection details and the two questions the form asks; listed in {@link ConformanceCases}.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SettingsConformanceCases {

    static final List<Screen> SCREENS = List.of(
            // A healthy provider is told apart by the success tint, not by words alone.
            new Screen(
                    "SETTINGS_SUMMARY",
                    ViewNames.SETTINGS,
                    Overlay.NONE,
                    List.of(
                            new Part("#settings-summary", Kind.BACKGROUND, "ok-bg", "#e7efe8", "#2b3a33"),
                            new Part("#settings-summary", Kind.BORDER, "ok-bd", "#bcd4c1", "#3f5a49"))),
            // Typing marks the footer in the warning text role, on the form card's surface.
            new Screen(
                    "SETTINGS_FORM_DIRTY",
                    ViewNames.SETTINGS,
                    Preparation.PROVIDER_FORM_DIRTY,
                    Overlay.NONE,
                    List.of(
                            new Part("#settings-form-dirty", Kind.TEXT, "warn-fg", "#835d27", "#e6c086"),
                            new Part("#settings-form-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"))),
            new Screen(
                    "SETTINGS_FOLD_OPEN",
                    ViewNames.SETTINGS,
                    Preparation.PROVIDER_FOLD_OPEN,
                    Overlay.NONE,
                    List.of(
                            new Part("#settings-connection", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#settings-connection", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#settings-form-address", Kind.BORDER, "input-border", "#988462", "#7d949e"))),
            new Screen(
                    "SETTINGS_DELETE_DIALOG",
                    ViewNames.SETTINGS,
                    Overlay.DELETE_PROVIDER,
                    List.of(
                            new Part("#confirm-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#confirm-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "SETTINGS_UNSAVED_DIALOG",
                    ViewNames.SETTINGS,
                    Preparation.PROVIDER_FORM_DIRTY,
                    Overlay.UNSAVED_PROVIDER,
                    List.of(
                            new Part("#unsaved-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#unsaved-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))));
}
