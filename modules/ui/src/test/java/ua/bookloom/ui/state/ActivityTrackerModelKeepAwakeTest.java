package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.KeepAwake;

/**
 * The computer is kept awake for every activity that calls the model, not only the run, and is let sleep only when
 * the last of them ends. The tracker, the mirror and the clock are real; the lock is a recording fake.
 */
class ActivityTrackerModelKeepAwakeTest extends FxTestBase {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    private final StateMirror mirror = new StateMirror();
    private final MutableClock clock = new MutableClock();
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

    private ActivityTracker tracker() {
        return onFx(() -> new ActivityTracker(mirror, keepAwake, clock));
    }

    // IF the lock were taken only for the run, THEN a ten-minute names pass would let the machine sleep mid-scan.
    @Test
    void begin_namesScanOfTenSimulatedMinutes_holdsTheLockUntilItEnds() {
        final ActivityTracker tracker = tracker();

        final ActivityTracker.Handle scan = onFx(() -> tracker.begin(ActivityKind.GLOSSARY_SCAN, null));
        clock.advance(TEN_MINUTES);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(calls).containsExactly("start");

        end(scan);

        assertThat(calls).containsExactly("start", "stop");
    }

    @ParameterizedTest
    @EnumSource(
            value = ActivityKind.class,
            mode = EnumSource.Mode.INCLUDE,
            names = {
                "SUGGEST_STYLE",
                "SUGGEST_NAME",
                "EXPORT",
                "REVIEW_RETRY",
                "TERM_REVIEW",
                "RUN_PREPARATION",
                "NAMES_PASS"
            })
    void begin_otherKindThatCallsTheModel_holdsThenReleases(final ActivityKind kind) {
        final ActivityTracker tracker = tracker();

        final ActivityTracker.Handle handle = onFx(() -> tracker.begin(kind, null));
        assertThat(calls).containsExactly("start");
        end(handle);

        assertThat(calls).containsExactly("start", "stop");
    }

    @ParameterizedTest
    @EnumSource(
            value = ActivityKind.class,
            mode = EnumSource.Mode.INCLUDE,
            names = {"PROVIDER_CHECK", "MODEL_LISTING", "IMPORT"})
    void begin_kindThatNeverCallsTheModel_holdsNothing(final ActivityKind kind) {
        final ActivityTracker tracker = tracker();

        final ActivityTracker.Handle handle = onFx(() -> tracker.begin(kind, null));
        end(handle);

        assertThat(calls).isEmpty();
    }

    // IF the first activity to end let the machine sleep, THEN a second one still calling the model would be frozen.
    @Test
    void end_firstOfTwoModelActivities_keepsTheLockForTheOther() {
        final ActivityTracker tracker = tracker();
        final ActivityTracker.Handle retry = onFx(() -> tracker.begin(ActivityKind.REVIEW_RETRY, null));
        final ActivityTracker.Handle suggestion = onFx(() -> tracker.begin(ActivityKind.SUGGEST_NAME, null));

        end(retry);

        assertThat(calls).doesNotContain("stop");

        end(suggestion);

        assertThat(calls).last().isEqualTo("stop");
    }

    // IF pausing the run released the lock while an export still asked the model, THEN the export would be frozen.
    @Test
    void run_pausedWhileAnExportCallsTheModel_staysAwakeUntilTheExportEnds() {
        final ActivityTracker tracker = tracker();
        publish(RunState.RUNNING);
        final ActivityTracker.Handle export = onFx(() -> tracker.begin(ActivityKind.EXPORT, null));

        publish(RunState.PAUSED);

        assertThat(calls).doesNotContain("stop");

        end(export);

        assertThat(calls).last().isEqualTo("stop");
    }

    @Test
    void end_calledTwice_releasesOnce() {
        final ActivityTracker tracker = tracker();
        final ActivityTracker.Handle scan = onFx(() -> tracker.begin(ActivityKind.TERM_SCAN, null));

        end(scan);
        end(scan);

        assertThat(calls.stream().filter("stop"::equals)).hasSize(1);
    }

    // IF the run's own hold were lost when a short activity ended inside it, THEN the run would sleep mid-book.
    @Test
    void end_shortActivityInsideARunningRun_neverReleasesTheRunsLock() {
        final ActivityTracker tracker = tracker();
        publish(RunState.RUNNING);
        final ActivityTracker.Handle retry = onFx(() -> tracker.begin(ActivityKind.REVIEW_RETRY, null));

        end(retry);

        assertThat(calls).doesNotContain("stop");
    }

    private static void end(final ActivityTracker.Handle handle) {
        onFx(() -> {
            handle.end();
            return null;
        });
    }

    private void publish(final RunState state) {
        mirror.publishRunState(state);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
