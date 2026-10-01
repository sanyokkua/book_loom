package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Announces that a segment the checks did not accept enters one more repair round.
 *
 * @param segmentId the segment being repaired
 * @param round the round, counted from one
 * @param rounds how many rounds the quality dial allows, at least {@code round}
 * @param judgeScore the score of the last judge verdict on the segment, or null when none judged it
 * @param blockingFinding the kind of the finding the round repairs, such as {@code meaning}, or null when the round
 *     repairs no named finding
 */
public record RoundStarted(
        String segmentId,
        int round,
        int rounds,
        @Nullable Double judgeScore,
        @Nullable String blockingFinding) implements JobEvent {

    /** Rejects an event without a segment or with an impossible round. */
    public RoundStarted {
        Objects.requireNonNull(segmentId, "segmentId");
        if (round < 1 || rounds < round) {
            throw new IllegalArgumentException("round " + round + " of " + rounds);
        }
    }
}
