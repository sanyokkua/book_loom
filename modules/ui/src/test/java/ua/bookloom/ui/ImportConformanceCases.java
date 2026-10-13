package ua.bookloom.ui;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.ConformanceCases.Kind;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Part;
import ua.bookloom.ui.ConformanceCases.Preparation;
import ua.bookloom.ui.ConformanceCases.Screen;

/** The conformance cases of the import screen once a book is open; they are listed in {@link ConformanceCases}. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ImportConformanceCases {

    static final List<Screen> SCREENS = List.of(
            new Screen(
                    "IMPORT_DETECTED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            // Once a book is open the drop zone is one line, still the sand box.
                            new Part("#import-dropzone", Kind.BACKGROUND, "sand-soft", "#f2e9db", "#3a3a37"),
                            new Part("#import-dropzone", Kind.BORDER, "sand-strong", "#dcc4a3", "#6d5f4a"),
                            new Part("#import-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#import-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#import-facts-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#import-facts-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-segments-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-segments-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // The book's checks answered: the count pills of the tree and the chunk warning in the facts card.
            new Screen(
                    "IMPORT_CHECKED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_CHECKED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-facts-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part(".count-pill", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part(".count-pill", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-chunk-warning", Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part("#structure-chunk-warning", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))));
}
