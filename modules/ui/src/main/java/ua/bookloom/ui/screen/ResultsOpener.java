package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.ProgressDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeMarks;
import ua.bookloom.ui.state.ChangeOperation;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.ResultsView;

/**
 * Opens the Results view of the progress dialog each time an operation of the names and style screen ends in the
 * foreground. An operation that ended in the background opens nothing by itself: the {@code HiddenResultsAnnouncer} offers
 * "Review the results", and pressing it takes the person here and opens them, once. The view models outlive the screen, so the listeners are
 * weak and their strong references are held by the screen's node.
 */
@Slf4j
final class ResultsOpener {

    private static final String KEY = "names-style-results-listener";

    private final NamesStyleViewModel names;
    private final ModalHost modalHost;
    private final Messages messages;

    ResultsOpener(final NamesStyleViewModel names, final ModalHost modalHost, final Messages messages) {
        this.names = names;
        this.modalHost = modalHost;
        this.messages = messages;
    }

    private static List<ChangeMarks.Side> sidesOf(final ChangeOperation operation, final ChangeMarks marks) {
        final List<ChangeMarks.Side> sides = new ArrayList<>();
        if (operation.isAboutNames()) {
            sides.add(marks.glossary());
        }
        if (operation.isAboutTerms()) {
            sides.add(marks.terms());
        }
        return sides;
    }

    /** Starts listening for the screen's lifetime and opens what was already asked for while it was away. */
    void attach(final Node screen) {
        final ChangeListener<@Nullable ChangeResults> onResults = (observed, was, now) -> ended(now);
        final ChangeListener<@Nullable ChangeResults> onRequest = (observed, was, now) -> opened(now);
        screen.getProperties().put(KEY, new Object[] {onResults, onRequest});
        names.results().addListener(new WeakChangeListener<>(onResults));
        names.resultsReview().requested().addListener(new WeakChangeListener<>(onRequest));
        opened(names.resultsReview().requested().get());
    }

    private void ended(final @Nullable ChangeResults results) {
        if (results == null) {
            return;
        }
        if (!results.endedHidden()) {
            log.debug("the results of {} open at once: the dialog was in the foreground", results.operation());
            open(results);
            return;
        }
        log.debug("the results of {} wait for the person: the pass ended in the background", results.operation());
    }

    private void opened(final @Nullable ChangeResults requested) {
        if (requested != null && names.resultsReview().take() != null) {
            open(requested);
        }
    }

    private void open(final ChangeResults results) {
        final List<ChangeMarks.Side> sides = sidesOf(results.operation(), names.marks());
        final ResultsView view = new ResultsView(
                results, () -> sides.forEach(side -> side.onlyChanged().set(side.size() > 0)), modalHost::hide);
        modalHost.show(ProgressDialog.results(view, messages), false);
    }
}
