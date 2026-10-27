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
                            // The verdict of a book that can be translated, then the closed fold: a surface with a
                            // border.
                            new Part("#import-verdict", Kind.BACKGROUND, "ok-bg", "#e7efe8", "#2b3a33"),
                            new Part("#import-verdict", Kind.BORDER, "ok-bd", "#bcd4c1", "#3f5a49"),
                            new Part("#import-about", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#import-about", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-segments-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-segments-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-text-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-text-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // The two verdicts the information role paints: a book being read and a book already stored.
            new Screen(
                    "IMPORT_VERDICT_READING",
                    ViewNames.IMPORT,
                    Preparation.BOOK_READING,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-verdict", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#import-verdict", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"))),
            new Screen(
                    "IMPORT_VERDICT_ALREADY",
                    ViewNames.IMPORT,
                    Preparation.BOOK_ALREADY_STORED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-verdict", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#import-verdict", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"))),
            // The book's checks answered: the count pills of the tree and the chunk warning in the facts card.
            new Screen(
                    "IMPORT_CHECKED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_CHECKED,
                    Overlay.NONE,
                    List.of(
                            new Part(".count-pill", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part(".count-pill", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#structure-chunk-warning", Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part("#structure-chunk-warning", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))));
}
