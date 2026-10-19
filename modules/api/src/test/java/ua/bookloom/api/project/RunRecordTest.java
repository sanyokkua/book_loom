package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.JobState;

/**
 * {@code RunRecord}'s state/end-time invariant.
 */
class RunRecordTest {

    private static final Instant STARTED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant ENDED = Instant.parse("2026-01-01T00:10:00Z");

    @Test
    void constructor_runningWithNoEndTime_isKept() {
        final RunRecord run = new RunRecord("r1", "p1", STARTED, null, JobState.RUNNING, 1, 0);

        assertThat(run.state()).isEqualTo(JobState.RUNNING);
        assertThat(run.endedAt()).isNull();
    }

    @Test
    void constructor_completedWithNoEndTime_throws() {
        assertThatThrownBy(() -> new RunRecord("r1", "p1", STARTED, null, JobState.COMPLETED, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_pausedWithEndTime_throws() {
        assertThatThrownBy(() -> new RunRecord("r1", "p1", STARTED, ENDED, JobState.PAUSED, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_new_throws() {
        assertThatThrownBy(() -> new RunRecord("r1", "p1", STARTED, null, JobState.NEW, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
