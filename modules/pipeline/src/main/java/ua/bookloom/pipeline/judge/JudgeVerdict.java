package ua.bookloom.pipeline.judge;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/**
 * One chunk's judge outcome: its score, its advisory verdict string, its findings and deferrals with labels already
 * resolved to segment ids, and whether the reply was readable at all. The acceptance rule (task 8.6) reads only
 * {@link #readable()} for an unreadable reply — its {@link #score()} carries no meaning then.
 *
 * @param score the chunk's overall score, between 0.0 and 1.0
 * @param verdict the model's advisory verdict string, or null when the reply carried none; never itself decisive
 * @param findings the findings whose label fell inside {@code s1}..{@code sk} and whose severity was recognised
 * @param deferrals the deferrals whose label fell inside {@code s1}..{@code sk}
 * @param readable false when the reply could not be parsed, or its {@code score} was missing or outside 0.0-1.0
 * @param unavailableBecause the error a judge call that never answered ended with — a timeout after the provider's
 *     own retries — or null when the judge answered
 */
public record JudgeVerdict(
        double score,
        @Nullable String verdict,
        List<JudgeFinding> findings,
        List<JudgeDeferral> deferrals,
        boolean readable,
        @Nullable AppError unavailableBecause) {

    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 1.0;

    /**
     * A verdict the judge answered.
     *
     * @param score the chunk's overall score, between 0.0 and 1.0
     * @param verdict the model's advisory verdict string, or null when the reply carried none
     * @param findings the findings resolved to segment ids
     * @param deferrals the deferrals resolved to segment ids
     * @param readable false when the reply could not be read
     */
    public JudgeVerdict(
            double score,
            @Nullable String verdict,
            List<JudgeFinding> findings,
            List<JudgeDeferral> deferrals,
            boolean readable) {
        this(score, verdict, findings, deferrals, readable, null);
    }

    /** Defensively copies the two list components and rejects a score outside 0.0-1.0. */
    public JudgeVerdict {
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(deferrals, "deferrals");
        findings = List.copyOf(findings);
        deferrals = List.copyOf(deferrals);
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("score must be within [0,1], but was " + score);
        }
    }

    /**
     * The verdict for a reply that could not be read at all.
     *
     * @return an unreadable verdict with score 0.0 and no findings or deferrals
     */
    public static JudgeVerdict unreadable() {
        return new JudgeVerdict(MIN_SCORE, null, List.of(), List.of(), false);
    }

    /**
     * The verdict of a judge call that never answered, so the segments it was for are decided by the quality checks
     * alone and flagged ({@code specs/quality-gates/spec.md} "Flag a segment the judge could not judge").
     *
     * @param cause the error the call ended with; never null
     * @return an unreadable verdict that names {@code cause}
     */
    public static JudgeVerdict unavailable(final AppError cause) {
        return new JudgeVerdict(MIN_SCORE, null, List.of(), List.of(), false, Objects.requireNonNull(cause, "cause"));
    }

    /**
     * Whether the judge never answered.
     *
     * @return {@code true} when this verdict stands for a judge call that timed out or could not reach the provider
     */
    public boolean isUnavailable() {
        return unavailableBecause != null;
    }
}
