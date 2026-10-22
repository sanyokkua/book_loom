package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.control.ProgressDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.LedgerFigures;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.StepLog;

/**
 * The Calls view of the progress dialog: the list and detail split of model work, listing the model's calls newest
 * first, with no follow switch and a Close button only. The model is built by hand; the app fills it with one request
 * step per call.
 */
class ProgressCallsModeTest extends ProgressWorkTestBase {

    private static final Instant AT = Instant.parse("2026-03-01T09:00:00Z");
    private static final String PURPOSE =
            "Newest first. The last 50 are kept while BookLoom is open; each translated segment keeps its own record.";

    private final AtomicInteger closed = new AtomicInteger();

    private ProgressDialogModel modelOfCalls(final int count) {
        StepLog log = StepLog.EMPTY.apply(new StepsPlanned(StepKind.REQUEST, count, false));
        for (final int n : IntStream.rangeClosed(1, count).toArray()) {
            final String label = "Call " + n + " · Draft · 4 segments";
            log = log.apply(new StepChanged(StepKind.REQUEST, label, n - 1, StepState.STARTED, null, null));
            log = log.apply(new StepChanged(StepKind.REQUEST, label, n - 1, StepState.ENDED, null, StepResult.DONE));
        }
        final Activity activity = new Activity(
                1,
                ActivityKind.TRANSLATION,
                count,
                false,
                MessageKey.ACTIVITY_TRANSLATION,
                AT,
                new Activity.Progress(null, null, List.of(), null, LiveCalls.EMPTY, log),
                false,
                false);
        return new ProgressDialogModel(activity, new LedgerFigures(), ProgressDialogModel.Mode.SIMPLE, () -> AT);
    }

    private void showCalls(final int count) {
        onFx(() -> injector.getInstance(ModalHost.class)
                .show(
                        ProgressDialog.calls(
                                modelOfCalls(count), injector.getInstance(Messages.class), closed::incrementAndGet),
                        false));
    }

    // IF the oldest call came first, THEN the call a person is chasing would sit at the bottom of fifty.
    @Test
    void calls_modelOfThreeCalls_listsNewestFirstWithCloseOnly() {
        showCalls(3);

        assertThat(List.of(stepName(0), stepName(1), stepName(2)))
                .containsExactly(
                        "Call 3 · Draft · 4 segments", "Call 2 · Draft · 4 segments", "Call 1 · Draft · 4 segments");
        final ButtonBar footer = (ButtonBar) required("calls-card").lookup(".button-bar");
        assertThat(ThemeTestSupport.onFx(() -> footer.getButtons().stream()
                        .map(button -> ((Button) button).getText())
                        .toList()))
                .containsExactly("Close");
    }

    // IF the follow switch showed, THEN a list of finished calls would offer to follow work that is not running.
    @Test
    void calls_shown_hideTheFollowSwitchAndSayWhatIsKept() {
        showCalls(3);

        final Node follow = scene.getRoot().lookup("#progress-follow");
        assertThat(follow == null || !follow.isVisible()).isTrue();
        assertThat(((Label) required("progress-purpose")).getText()).isEqualTo(PURPOSE);
    }

    @Test
    void calls_sixtyCalls_listsTheNewestOfThemFirst() {
        showCalls(60);

        assertThat(stepName(0)).isEqualTo("Call 60 · Draft · 4 segments");
    }

    @Test
    void close_pressed_runsOnClose() {
        showCalls(3);

        onFx(() -> ((Button) required("calls-close")).fire());

        assertThat(closed.get()).isEqualTo(1);
    }
}
