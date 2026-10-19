package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import lombok.extern.slf4j.Slf4j;

/**
 * The ways out of a model call that is stuck or failed: skip its segment, or send the call again.
 *
 * <p>A segment can only be skipped, and a call only sent again, from a pause, because only a pause interrupts the call
 * in flight. So while the run is still waiting on the model, either action first asks for a pause and remembers what to
 * do once the engine reports it paused. Any state other than pausing or paused forgets it. FX thread only.
 *
 * <p>A singleton that holds its state listener in a field, because the mirror would otherwise hold it only weakly.
 */
@Slf4j
@Singleton
public final class RunInterventions {

    /** What to do once the pause asked for is reached. */
    private enum FollowUp {
        NONE,
        SKIP,
        SEND_AGAIN
    }

    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final ChangeListener<RunState> onState = (observed, was, now) -> onRunState(now);
    private FollowUp followUp = FollowUp.NONE;

    /**
     * Starts following the run state.
     *
     * @param mirror where the run state is read from
     * @param runner the runner the actions are asked of
     */
    @Inject
    public RunInterventions(final StateMirror mirror, final TranslationRunner runner) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        mirror.runState().addListener(onState);
    }

    /** Flags the failing or stuck segment and lets the run go on: at once from a pause, else after pausing. */
    public void skipSegment() {
        final RunState state = mirror.runState().get();
        log.info("skip segment pressed in state {}", state);
        switch (state) {
            case PAUSED -> runner.skipSegment();
            case RUNNING -> pauseThen(FollowUp.SKIP);
            default -> log.debug("skip segment ignored: the run is {}", state);
        }
    }

    /** Cancels the request that is waiting and sends it again, by pausing and resuming at once. */
    public void sendAgain() {
        final RunState state = mirror.runState().get();
        log.info("send again pressed in state {}", state);
        if (state == RunState.RUNNING) {
            pauseThen(FollowUp.SEND_AGAIN);
        } else {
            log.debug("send again ignored: the run is {}", state);
        }
    }

    private void pauseThen(final FollowUp next) {
        followUp = next;
        runner.pause();
    }

    private void onRunState(final RunState now) {
        if (followUp == FollowUp.NONE) {
            return;
        }
        switch (now) {
            case PAUSED -> {
                final FollowUp due = followUp;
                followUp = FollowUp.NONE;
                log.debug("the pause asked for was reached: {}", due);
                if (due == FollowUp.SKIP) {
                    runner.skipSegment();
                } else {
                    runner.resume();
                }
            }
            case PAUSING, RUNNING -> log.trace("waiting for the pause to {}", followUp);
            default -> {
                log.debug("the run is {}; forgetting the {} that waited for a pause", now, followUp);
                followUp = FollowUp.NONE;
            }
        }
    }
}
