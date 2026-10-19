package ua.bookloom.ui;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.ConformanceCases.Kind;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Part;
import ua.bookloom.ui.ConformanceCases.Preparation;
import ua.bookloom.ui.ConformanceCases.Screen;

/** The conformance cases of Names &amp; style and its Add term card; they are listed in {@link ConformanceCases}. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NamesStyleConformanceCases {

    static final List<Screen> SCREENS = List.of(
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
                            // The Recurring terms card is the same card as the Glossary's (15i.U5), so it wears the
                            // same surface.
                            new Part("#recurring-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#recurring-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            // The scanned term is readable, strong text in the glossary table.
                            new Part(".glossary-term", Kind.TEXT, "text-strong", "#22303a", "#f4f7f8"))),
            // The one-press preparation button (15i.U6) is the secondary button with the muted estimate under it.
            new Screen(
                    "NAMES_STYLE_AUTO",
                    ViewNames.NAMES_STYLE,
                    Preparation.GLOSSARY_LISTED,
                    Overlay.NONE,
                    List.of(
                            new Part("#names-style-auto", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#names-style-auto", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#names-style-auto-subtitle", Kind.TEXT, "muted", "#5d686d", "#bdc9cd"),
                            // "Confirm suggested genders" (15j.J3) is the ghost button beside it, shown while a
                            // character's gender is only suggested.
                            new Part("#names-style-confirm-genders", Kind.TEXT, "text", "#2c3941", "#e6ebed"))),
            // The Add term card, opened from the glossary card's header over the names screen.
            new Screen(
                    "ADD_TERM_DIALOG",
                    ViewNames.NAMES_STYLE,
                    Preparation.BOOK_OPENED,
                    Overlay.ADD_TERM,
                    List.of(
                            new Part("#add-term-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#add-term-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))));
}
