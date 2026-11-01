package ua.bookloom.ui;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.ConformanceCases.Kind;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Part;
import ua.bookloom.ui.ConformanceCases.Preparation;
import ua.bookloom.ui.ConformanceCases.Screen;

/** The conformance case of the results card a Names &amp; style operation opens over the names screen. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConformanceResultsCase {

    static final Screen RESULTS_DIALOG = new Screen(
            "RESULTS_DIALOG",
            ViewNames.NAMES_STYLE,
            Preparation.BOOK_OPENED,
            Overlay.RESULTS,
            List.of(
                    new Part("#results-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                    new Part("#results-card", Kind.BORDER, "border", "#ddd5c8", "#48585f")));
}
