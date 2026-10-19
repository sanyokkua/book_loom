package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;

/**
 * Skip segment and Retry now act from a pause: pressed while the run waits on the model, they pause first and act once
 * the engine reports it paused.
 */
class RunInterventionsTest extends RunnerTestBase {

    // IF skip went to a running job, THEN the job would ignore it, since only a paused job can skip.
    @Test
    void skipSegment_whileRunning_pausesThenSkipsOnceThePauseIsReached() throws Exception {
        final RunInterventions interventions = interventions();
        startJob();

        onFx(() -> {
            interventions.skipSegment();
            return null;
        });
        reachPause();

        assertThat(job.calls()).containsSubsequence("pause", "skipSegment");
    }

    // IF Retry now on a stalled call did nothing, THEN the only way to send it again would be to wait out its timeout.
    @Test
    void sendAgain_whileRunning_pausesThenResumes() throws Exception {
        final RunInterventions interventions = interventions();
        startJob();

        onFx(() -> {
            interventions.sendAgain();
            return null;
        });
        reachPause();

        assertThat(job.calls()).containsSubsequence("pause", "resume").doesNotContain("skipSegment");
    }

    // IF a skip pressed while paused waited for another pause, THEN it would never happen.
    @Test
    void skipSegment_whilePaused_skipsAtOnce() throws Exception {
        final RunInterventions interventions = interventions();
        startJob();
        reachPause();

        onFx(() -> {
            interventions.skipSegment();
            return null;
        });

        assertThat(job.calls()).contains("skipSegment").doesNotContain("pause");
    }

    private RunInterventions interventions() {
        return onFx(() -> new RunInterventions(mirror, runner));
    }

    private void reachPause() throws InterruptedException, TimeoutException {
        job.emit(new Paused(PauseReason.ON_ERROR, error(), progress(0, 0, 1), "s-1", 1, 2));
        deliverAndTick();
        awaitState(RunState.PAUSED);
    }
}
