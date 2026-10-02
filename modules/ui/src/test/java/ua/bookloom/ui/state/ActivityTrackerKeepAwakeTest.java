package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.KeepAwake;

/**
 * The computer is kept awake exactly while the run is at work — translating, or waiting for the provider by itself —
 * and let sleep once the person pauses it or the run ends.
 */
class ActivityTrackerKeepAwakeTest extends FxTestBase {

    private final StateMirror mirror = new StateMirror();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final KeepAwake keepAwake = new KeepAwake() {
        @Override
        public void start() {
            calls.add("start");
        }

        @Override
        public void stop() {
            calls.add("stop");
        }
    };

    @Test
    void run_runningThenPausedByThePerson_holdsThenReleases() {
        final ActivityTracker tracker = onFx(() -> new ActivityTracker(mirror, keepAwake));

        publish(RunState.RUNNING, null);
        publish(RunState.PAUSED, null);

        assertThat(calls).containsExactly("start", "stop");
        assertThat(onFx(tracker::running)).isEmpty();
    }

    // IF the wait for the provider let the computer sleep, THEN the run could never resume by itself overnight.
    @Test
    void run_pausedButWaitingForTheProvider_staysAwakeAndCountsAsTranslating() {
        final ActivityTracker tracker = onFx(() -> new ActivityTracker(mirror, keepAwake));

        publish(RunState.RUNNING, null);
        publish(RunState.PAUSED, state(RecoveryWaiting.Status.WAITING, 9));
        publish(RunState.PAUSED, state(RecoveryWaiting.Status.WAITING, 8));

        // The pause and its recovery reach the mirror one after the other, so the hold may be let go for a moment.
        assertThat(calls).last().isEqualTo("start");
        assertThat(onFx(tracker::running))
                .extracting(ActivityTracker.Activity::kind)
                .containsExactly(ActivityKind.TRANSLATION);
    }

    @Test
    void run_waitingThenHeldByThePerson_releases() {
        onFx(() -> new ActivityTracker(mirror, keepAwake));

        publish(RunState.RUNNING, null);
        publish(RunState.PAUSED, state(RecoveryWaiting.Status.WAITING, 9));
        publish(RunState.PAUSED, state(RecoveryWaiting.Status.HELD, 0));

        assertThat(calls).last().isEqualTo("stop");
    }

    private void publish(final RunState state, final @Nullable RecoveryState recovery) {
        mirror.publishRunState(state);
        mirror.review().publishRecovery(recovery);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static RecoveryState state(final RecoveryWaiting.Status status, final long secondsLeft) {
        return new RecoveryState(status, 2, LocalTime.of(2, 14), null, null, secondsLeft);
    }
}
