package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
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

    /**
     * What a reviewer call over several pairs came to.
     *
     * @param items one outcome per pair, in order
     * @param truncated whether the reviewer's reply was cut by its cap (the model stopped with {@code finish=LENGTH})
     */
    record BatchReviewed(List<Reviewed> items, boolean truncated) {

        /** Copies the list. */
        BatchReviewed {
            items = List.copyOf(items);
        }
    }

    // The pair, the term pairs and the character sheet are what a run gives the reviewer for a chunk of this segment.
    Reviewed review(final EvalProject project, final String candidate) {
        return reviewAll(project, List.of(candidate)).items().getFirst();
    }

    /**
     * Sends the project's chunk to the reviewer in one call, as a run reviews a chunk: every segment of the project is a
     * pair with the candidate given at its index.
     */
    BatchReviewed reviewAll(final EvalProject project, final List<String> candidates) {
        final FinishWatch watch = new FinishWatch(calls);
        final List<ReviewedPair> pairs = IntStream.range(0, candidates.size())
                .mapToObj(i -> new ReviewedPair(
                        project.segment(i).id(), project.mask(i).maskedText(), candidates.get(i)))
                .toList();
        final Result<ReviewVerdict> result = loop.review(pairs, project.loop(), ReviewPass.FIRST, watch);
        final ReviewVerdict verdict =
                result.isOk() ? Objects.requireNonNull(result.data()) : ReviewVerdict.unreadable();
        final List<Reviewed> items = pairs.stream()
                .map(pair -> read(project.frame(), renderingsOf(project), verdict, pair))
                .toList();
        return new BatchReviewed(items, watch.truncated());
    }

    // As the run reads them (ReviewResolver): the target of every term pair is a rendering a terminology edit may use.
    private static List<String> renderingsOf(final EvalProject project) {
        return project.loop().glossaryPairs().stream()
                .map(line -> line.substring(line.indexOf('→') + 1).strip())
                .toList();
    }

    private Reviewed read(
            final CallFrame frame,
            final List<String> renderings,
            final ReviewVerdict verdict,
            final ReviewedPair pair) {
        if (!verdict.readable()) {
            return new Reviewed(false, !isClean(pair.maskedCandidate()), 0, "unreadable", "unreadable");
        }
        final ReviewItem item = verdict.itemFor(pair.segmentId()).orElseGet(() -> ReviewItem.ok(pair.segmentId()));
        return switch (item.status()) {
            case OK -> new Reviewed(true, false, 0, "ok", "ok");
            case EDITS -> editsOf(frame, renderings, pair.maskedSource(), pair.maskedCandidate(), item);
            case REWRITE -> rewriteOf(pair.maskedCandidate(), item);
        };
    }

    /** Passes every call through and notes whether any reply was cut by the model's output cap. */
    private static final class FinishWatch implements ModelCalls {

        private final ModelCalls delegate;
        private final AtomicBoolean truncated = new AtomicBoolean();

        FinishWatch(final ModelCalls delegate) {
            this.delegate = delegate;
        }

        boolean truncated() {
            return truncated.get();
        }

        @Override
        public Result<ChatResponse> call(
                final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
            return note(delegate.call(kind, segmentId, request));
        }

        @Override
        public Result<ChatResponse> callAbout(
                final CallKind kind, final List<String> segmentIds, final ChatRequest request) {
            return note(delegate.callAbout(kind, segmentIds, request));
        }

        @Override
        public void announce(final JobEvent event) {
            delegate.announce(event);
        }

        private Result<ChatResponse> note(final Result<ChatResponse> reply) {
            if (reply.isOk() && Objects.requireNonNull(reply.data()).finishReason() == FinishReason.LENGTH) {
                truncated.set(true);
            }
            return reply;
        }
    }

    private Reviewed editsOf(
            final CallFrame frame,
            final List<String> renderings,
            final String masked,
            final String candidate,
            final ReviewItem item) {
        final EditOutcome outcome = editApplier.apply(
                candidate,
                item.edits(),
                text -> blockersOf(frame, masked, text),
                blockersOf(frame, masked, candidate).orElseGet(java.util.Set::of),
                renderings);
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
