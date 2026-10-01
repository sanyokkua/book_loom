package ua.bookloom.ui.state;

import org.jspecify.annotations.Nullable;

/**
 * Where a segment stands in its repair rounds, for the live row's tracker.
 *
 * @param round the round the segment is in, counted from one
 * @param rounds how many rounds the quality dial allows
 * @param judgeScore the score of the last judge verdict on the segment, or {@code null} when none judged it
 * @param blockingFinding the kind of the finding the round repairs, or {@code null} when it repairs no named finding
 */
public record RoundTrack(
        int round,
        int rounds,
        @Nullable Double judgeScore,
        @Nullable String blockingFinding) {}
