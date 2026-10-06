package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import ua.bookloom.api.Result;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.EditOutcome;
import ua.bookloom.pipeline.reviewer.EditVerifier;
import ua.bookloom.pipeline.reviewer.ReviewItem;
import ua.bookloom.pipeline.reviewer.ReviewPass;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewVerdict;
import ua.bookloom.pipeline.reviewer.ReviewedPair;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/**
 * Sends a candidate to the production reviewer with the inputs a run gives it for a chunk of that segment, and measures
 * the reply the way the run uses it: its edits go through the production {@link EditApplier}, so a reply counts as a
 * change only when the app would act on it.
 */
final class ReviewerEval {

    private final ModelCalls calls;
    private final EditApplier editApplier = new EditApplier(new EditVerifier());
    private final QualityLoop loop;

    ReviewerEval(final ModelCalls calls, final PromptTemplates templates, final DirectedFix directedFix) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.loop = new QualityLoop(
                new ReviewerCall(templates, new ReviewReplyParser(new ObjectMapper())), editApplier, directedFix);
    }

    /**
     * What one reviewer call came to once its edits went through the verifier.
     *
     * @param readable whether the reply parsed
     * @param flagged whether the reviewer asked for a change the app would act on: an applied edit, an edit whose quote
     *     is in the text but was refused, or a rewrite
     * @param tokenBreaks how many applied changes altered the candidate's placeholder tokens
     * @param signature the change asked for, so a repeated run can be compared with it
     * @param detail what the reviewer asked for, for the report
     */
    record Reviewed(boolean readable, boolean flagged, int tokenBreaks, String signature, String detail) {}

    // The pair, the term pairs and the character sheet are what a run gives the reviewer for a chunk of this segment.
    Reviewed review(final EvalProject project, final String candidate) {
        final String id = project.segment(0).id();
        final String masked = project.mask(0).maskedText();
        final Result<ReviewVerdict> result =
                loop.review(List.of(new ReviewedPair(id, masked, candidate)), project.loop(), ReviewPass.FIRST, calls);
        final ReviewVerdict verdict =
                result.isOk() ? Objects.requireNonNull(result.data()) : ReviewVerdict.unreadable();
        if (!verdict.readable()) {
            return new Reviewed(false, !isClean(candidate), 0, "unreadable", "unreadable");
        }
        final ReviewItem item = verdict.itemFor(id).orElseGet(() -> ReviewItem.ok(id));
        return switch (item.status()) {
            case OK -> new Reviewed(true, false, 0, "ok", "ok");
            case EDITS -> editsOf(project.frame(), masked, candidate, item);
            case REWRITE -> rewriteOf(candidate, item);
        };
    }

    private Reviewed editsOf(
            final CallFrame frame, final String masked, final String candidate, final ReviewItem item) {
        final EditOutcome outcome = editApplier.apply(
                candidate,
                item.edits(),
                text -> blockersOf(frame, masked, text),
                blockersOf(frame, masked, candidate).orElseGet(java.util.Set::of));
        final boolean flagged =
                !outcome.applied().isEmpty() || !outcome.failed().isEmpty();
        final int broken = Tokens.inOrder(outcome.text()).equals(Tokens.inOrder(candidate)) ? 0 : 1;
        final String detail = "edits applied=" + outcome.applied().size() + " refused="
                + outcome.failed().size() + " ignored=" + outcome.ignored() + " notes="
                + outcome.notes().size();
        return new Reviewed(
                true,
                flagged,
                broken,
                "edits:" + outcome.text() + outcome.failed().size(),
                detail);
    }

    private Reviewed rewriteOf(final String candidate, final ReviewItem item) {
        final String rewrite = Objects.requireNonNull(item.rewrite());
        final int broken = Tokens.inOrder(rewrite).equals(Tokens.inOrder(candidate)) ? 0 : 1;
        return new Reviewed(
                true, true, 0, signature(item), "rewrite" + (broken == 0 ? "" : " (breaks tokens, refused)"));
    }

    private static String signature(final ReviewItem item) {
        return item.status() + item.rewrite();
    }

    private static Optional<Set<String>> blockersOf(final CallFrame frame, final String masked, final String text) {
        return Optional.of(
                TextChecks.run(
                                DisplayText.of(masked),
                                DisplayText.of(text),
                                frame.sourceLanguage(),
                                frame.targetLanguage())
                        .stream()
                        .filter(CheckFinding::blocking)
                        .map(finding -> finding.kind().name())
                        .collect(Collectors.toUnmodifiableSet()));
    }

    /** Whether a candidate carries no blocking defect of its own, so an unreadable reply on it is not a miss. */
    private static boolean isClean(final String candidate) {
        return candidate.isBlank();
    }
}
