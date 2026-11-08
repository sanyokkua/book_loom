package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptVariants;
import ua.bookloom.pipeline.prompt.PromptVariants.ReviewerVariant;

/**
 * What the run's brief and review mode set for every chunk.
 *
 * @param projectId the project the run translates
 * @param mode the run's review mode, whose threshold decides acceptance and which caps Manual chunks at one
 * @param dial the quality dial's mechanics
 * @param frame the run's languages, style sheet and foreign-passage policy
 * @param names the brief's name policy
 * @param window the context window in tokens the run's prompts and chunks are sized against; positive
 */
public record RunSettings(
        String projectId, ReviewMode mode, DialParameters dial, CallFrame frame, NamePolicy names, int window) {

    /**
     * The settings of a requested run: the brief's dial and name policy.
     *
     * @param request the non-null run request
     * @param brief the non-null brief the run translates by
     * @param frame the non-null languages and style of the run
     * @param window the context window in tokens; positive
     * @param variants the non-null prompt variants; the reviewer OFF choice (arm 7a, until task 14.7) leaves the dial
     *     with no reviewer pass
     * @return the settings
     */
    public static RunSettings forRun(
            final RunRequest request,
            final BookBrief brief,
            final CallFrame frame,
            final int window,
            final PromptVariants variants) {
        return new RunSettings(
                request.projectId(), request.mode(), dialOf(brief, variants), frame, brief.names(), window);
    }

    /**
     * The brief's dial row, without its reviewer passes when arm 7a's OFF variant is bound.
     *
     * @param brief the non-null brief
     * @param variants the non-null prompt variants
     * @return the dial mechanics a run reads
     */
    public static DialParameters dialOf(final BookBrief brief, final PromptVariants variants) {
        final DialParameters row = DialParameters.of(brief.dial());
        return variants.reviewer() == ReviewerVariant.OFF ? row.withReviewerPasses(0) : row;
    }

    /**
     * The same settings sized for another window.
     *
     * @param newWindow the context window in tokens; positive
     * @return a copy whose {@code window} is replaced
     */
    public RunSettings withWindow(final int newWindow) {
        return new RunSettings(projectId, mode, dial, frame, names, newWindow);
    }

    /** Rejects a missing component. */
    public RunSettings {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(names, "names");
        if (window <= 0) {
            throw new IllegalArgumentException("window must be positive: " + window);
        }
    }
}
