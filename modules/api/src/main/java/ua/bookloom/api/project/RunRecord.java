package ua.bookloom.api.project;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobState;

/**
 * A translation run's current state, upserted by run id so every state change of one run lands in one record — read
 * by the review desk to allow or refuse a retry while a run is still going, and by a resume to see how the last run
 * ended.
 *
 * @param runId the run's stable id
 * @param projectId the owning project's id
 * @param startedAt when this run started
 * @param endedAt when this run ended, present exactly when {@code state} is terminal
 * @param state the run's current state — never {@link JobState#NEW}
 * @param accepted the number of segments this run has accepted so far
 * @param flagged the number of segments this run has flagged so far
 */
public record RunRecord(
        String runId,
        String projectId,
        Instant startedAt,
        @Nullable Instant endedAt,
        JobState state,
        int accepted,
        int flagged) {

    /**
     * Validates the invariants a caller is entitled to assume: {@code state} is never {@link JobState#NEW}, and
     * {@code endedAt} is present exactly when {@code state} is terminal.
     */
    public RunRecord {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(state, "state");
        final boolean terminal = isTerminal(state);
        if (state == JobState.NEW) {
            throw new IllegalArgumentException("state must not be NEW");
        }
        if (terminal && endedAt == null) {
            throw new IllegalArgumentException("endedAt must be present for terminal state " + state);
        }
        if (!terminal && endedAt != null) {
            throw new IllegalArgumentException("endedAt must be null for non-terminal state " + state);
        }
    }

    private static boolean isTerminal(final JobState state) {
        return state == JobState.COMPLETED || state == JobState.CANCELLED || state == JobState.FAILED;
    }
}
