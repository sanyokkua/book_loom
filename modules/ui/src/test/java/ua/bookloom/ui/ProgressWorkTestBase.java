package ua.bookloom.ui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/** What the tests of the work layer's progress dialog share: starting work, feeding its events, reading its rows. */
abstract class ProgressWorkTestBase extends ShellTestBase {

    private static final long WITHIN_SECONDS = 3;

    final List<String> cancelled = new CopyOnWriteArrayList<>();
    private final List<ActivityTracker.Handle> begun = new CopyOnWriteArrayList<>();

    // A registration left running keeps the work layer's clock ticking, which keeps this test's whole window alive
    // for the rest of the test JVM.
    @AfterEach
    void endTheWork() {
        onFx(() -> begun.forEach(ActivityTracker.Handle::end));
    }

    ActivityTracker.Handle begin(final ActivityKind kind) {
        final AtomicReference<ActivityTracker.Handle> handle = new AtomicReference<>();
        onFx(() ->
                handle.set(injector.getInstance(ActivityTracker.class).begin(kind, () -> cancelled.add(kind.name()))));
        final ActivityTracker.Handle started = Objects.requireNonNull(handle.get(), "handle");
        begun.add(started);
        return started;
    }

    void feed(final ActivityTracker.Handle handle, final List<JobEvent> events) {
        onFx(() -> events.forEach(handle::steps));
    }

    boolean isDialogShown() {
        final Node host = scene.getRoot().lookup("#" + BusyHost.HOST_ID);
        final Node card = scene.getRoot().lookup("#busy-card");
        return host != null && host.isVisible() && host.getOpacity() > 0 && card != null && card.isVisible();
    }

    void await(final BooleanSupplier condition) throws TimeoutException {
        WaitForAsyncUtils.waitFor(WITHIN_SECONDS, TimeUnit.SECONDS, condition::getAsBoolean);
        interact(() -> {});
    }

    void escape() {
        onFx(() -> Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    }

    /** The name line of the step list's row at {@code index}, once the list has drawn it. */
    String stepName(final int index) {
        return ((Label) required("progress-step-" + index).lookup(".progress-step-name")).getText();
    }

    /** The result line of the step list's row at {@code index}, such as "25 requests so far". */
    String stepResult(final int index) {
        return ((Label) required("progress-step-" + index).lookup(".progress-step-result")).getText();
    }

    /** How many rows the step list has drawn. */
    long drawnRows() {
        return ThemeTestSupport.onFx(() -> scene.getRoot().lookupAll(".progress-step").stream()
                .filter(Node::isVisible)
                .count());
    }
}
