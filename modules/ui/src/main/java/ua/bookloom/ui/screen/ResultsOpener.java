package ua.bookloom.ui.screen;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.dialog.ChangeResultsDialog;
import ua.bookloom.ui.state.ChangeMarks;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * Opens the results card each time an operation of the names and style screen ends. The view model outlives the view,
 * so the listener is weak and its strong reference is held by the screen's node.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ResultsOpener {

    private static final String KEY = "names-style-results-listener";

    static void attach(final Node screen, final NamesStyleViewModel names, final ChangeResultsDialog dialog) {
        final ChangeListener<@Nullable ChangeResults> onResults = (observed, was, now) -> {
            if (now != null) {
                final ChangeMarks.Side side = now.operation().isAboutTerms()
                        ? names.marks().terms()
                        : names.marks().glossary();
                log.debug("opening the results of {}", now.operation());
                dialog.show(now, () -> side.onlyChanged().set(side.size() > 0));
            }
        };
        screen.getProperties().put(KEY, onResults);
        names.results().addListener(new WeakChangeListener<>(onResults));
    }
}
