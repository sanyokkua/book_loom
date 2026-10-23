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
 * The conformance cases of the finished Export screen beyond its base cases: the original title and author shown
 * read-only, and the screen with no book open, which still shows its tiles; listed in {@link ConformanceCases}.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportConformanceCases {

    static final List<Screen> SCREENS = List.of(
            // A value shown, never edited: the raised surface with the border line, as the mockup's read-only input.
            new Screen(
                    "EXPORT_ORIGINAL_TITLE",
                    ViewNames.EXPORT,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part(
                                    "#export-book-author-original", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part("#export-book-author-original", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // No separate empty state: the tiles stay, on the stat surface.
            new Screen(
                    "EXPORT_NO_BOOK",
                    ViewNames.EXPORT,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#export-written", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-written", Kind.BORDER, "border", "#ddd5c8", "#48585f"))));
}
