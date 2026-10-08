package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/** Closing the window: at once when idle, otherwise only after the person confirmed stopping the work. */
class ExitGuardTest extends ShellTestBase {

    private final AtomicInteger exits = new AtomicInteger();
    private final List<String> stopped = new CopyOnWriteArrayList<>();

    private boolean request() {
        final AtomicBoolean allowed = new AtomicBoolean();
        onFx(() -> allowed.set(injector.getInstance(ExitGuard.class).requestClose(exits::incrementAndGet)));
        return allowed.get();
    }

    private boolean isAsking() {
        return scene.getRoot().lookup("#confirm-card") != null;
    }

    // IF an idle window asked, THEN every quit would meet a pointless question.
    @Test
    void requestClose_nothingRuns_allowsTheCloseAtOnce() {
        assertThat(request()).isTrue();

        assertThat(isAsking()).isFalse();
        assertThat(exits).hasValue(0);
    }

    // IF a close went through unasked, THEN a stray click on the window's close box would end a long run.
    @Test
    void requestClose_workRunning_holdsTheCloseAndAsks() {
        onFx(() -> injector.getInstance(ActivityTracker.class)
                .begin(ActivityKind.GLOSSARY_SCAN, () -> stopped.add("scan")));

        assertThat(request()).isFalse();

        assertThat(isAsking()).isTrue();
        assertThat(exits).hasValue(0);
        assertThat(stopped).isEmpty();
    }

    // IF confirming only closed, THEN the model call would go on behind a closed window.
    @Test
    void confirm_workRunning_stopsTheWorkAndThenCloses() {
        onFx(() -> injector.getInstance(ActivityTracker.class)
                .begin(ActivityKind.GLOSSARY_SCAN, () -> stopped.add("scan")));
        request();

        onFx(() -> ((Button) required("confirm-yes")).fire());

        assertThat(stopped).containsExactly("scan");
        assertThat(exits).hasValue(1);
    }

    // IF Cancel closed the window anyway, THEN the question would be decoration.
    @Test
    void cancel_workRunning_keepsTheWindowAndTheWork() {
        onFx(() -> injector.getInstance(ActivityTracker.class)
                .begin(ActivityKind.GLOSSARY_SCAN, () -> stopped.add("scan")));
        request();

        onFx(() -> ((Button) required("confirm-cancel")).fire());

        assertThat(stopped).isEmpty();
        assertThat(exits).hasValue(0);
        assertThat(isAsking()).isFalse();
    }
}
