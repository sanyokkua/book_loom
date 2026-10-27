package ua.bookloom.ui.control;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.PassEvents;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeOperation;
import ua.bookloom.ui.state.ChangeOrigin;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.ChangeRow;
import ua.bookloom.ui.state.ChangeSection;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.ResultsView;
import ua.bookloom.ui.state.StepLog;

/**
 * What the tests of the Results view of the progress dialog share: results built from rows, the dialog shown in the
 * shell's modal host, and the lookups. The reversals are recording fakes, since what they do to the glossary is proven
 * on the view model.
 */
abstract class ProgressResultsTestBase extends ShellTestBase {

    static final String CARD = "results-card";

    final List<Long> reverted = new ArrayList<>();
    final AtomicInteger shownChanged = new AtomicInteger();
    final AtomicInteger done = new AtomicInteger();

    List<ChangeResults.Item> items(final ChangeRow... rows) {
        final List<ChangeResults.Item> items = new ArrayList<>();
        for (final ChangeRow row : rows) {
            items.add(new ChangeResults.Item(row, finished -> {
                reverted.add(row.id());
                finished.accept(true);
            }));
        }
        return items;
    }

    ChangeResults results(final boolean stopped, final ChangeRow... rows) {
        return new ChangeResults(ChangeOperation.NAME_REVIEW, stopped, items(rows), LiveCalls.EMPTY);
    }

    /** Results of an operation that ran {@code steps} request steps in {@code seconds}, as a visible run. */
    ChangeResults timed(final int steps, final long seconds, final ChangeRow... rows) {
        return new ChangeResults(
                ChangeOperation.NAME_REVIEW,
                false,
                items(rows),
                LiveCalls.EMPTY,
                List.of(),
                requestsLog(steps),
                Duration.ofSeconds(seconds),
                false);
    }

    /** A pass's results with the stage log of a finished pass, for the Steps tab. */
    ChangeResults ofPass(final int[] requests, final ChangeRow... rows) {
        return new ChangeResults(
                ChangeOperation.NAME_REVIEW,
                false,
                items(rows),
                LiveCalls.EMPTY,
                List.of(),
                PassEvents.logUpTo(requests, PassEvents.CODES.size(), 49),
                Duration.ofSeconds(108),
                false);
    }

    static StepLog requestsLog(final int count) {
        StepLog log = StepLog.EMPTY.apply(new StepsPlanned(StepKind.REQUEST, count, false));
        for (int index = 0; index < count; index++) {
            log = log.apply(new StepChanged(StepKind.REQUEST, "names", index, StepState.STARTED, null, null));
            log = log.apply(new StepChanged(StepKind.REQUEST, "names", index, StepState.ENDED, null, StepResult.DONE));
        }
        return log;
    }

    static ChangeRow added(final long id, final String term) {
        return new ChangeRow(
                id, ChangeKind.ADDED, term, "—", "Терм · character", null, ChangeOrigin.MODEL, ChangeSection.NAMES);
    }

    static ChangeRow found(final long id, final String term) {
        return new ChangeRow(id, ChangeKind.ADDED, term, "—", "—", null, ChangeOrigin.TEXT, ChangeSection.NAMES);
    }

    static ChangeRow removed(final long id, final String term, final @Nullable String reason) {
        return new ChangeRow(
                id,
                ChangeKind.REMOVED,
                term,
                "No target · other",
                "—",
                reason,
                ChangeOrigin.MODEL,
                ChangeSection.NAMES);
    }

    /** Shows the Results view over the shell; Done and Show changed rows count themselves, Done closes it. */
    void show(final ChangeResults results) {
        onFx(() -> {
            final ModalHost host = injector.getInstance(ModalHost.class);
            final ResultsView view = new ResultsView(results, shownChanged::incrementAndGet, () -> {
                done.incrementAndGet();
                host.hide();
            });
            host.show(ProgressDialog.results(view, injector.getInstance(Messages.class)), false);
        });
    }

    Button press(final String id) {
        final Button button = (Button) required(id);
        onFx(button::fire);
        return button;
    }

    List<String> texts() {
        return textsUnder(required(CARD));
    }

    void selectTab(final int index) {
        onFx(() -> ((TabPane) required("results-tabs")).getSelectionModel().select(index));
    }

    boolean isShowing() {
        final Node card = scene.getRoot().lookup("#" + CARD);
        return ThemeTestSupport.onFx(() -> card != null && card.isVisible() && card.getScene() != null);
    }
}
