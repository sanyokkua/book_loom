package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.DraftJudge;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.heal.ReviewJudge;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.EditVerifier;
import ua.bookloom.pipeline.reviewer.ReviewItem;
import ua.bookloom.pipeline.reviewer.ReviewPass;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewStatus;
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
    private final DirectedFix directedFix;
    private final QualityLoop loop;

    ReviewerEval(final ModelCalls calls, final PromptTemplates templates, final DirectedFix directedFix) {
        this.calls = Objects.requireNonNull(calls, "calls");
        this.directedFix = Objects.requireNonNull(directedFix, "directedFix");
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
        final List<Reviewed> items = IntStream.range(0, pairs.size())
                .mapToObj(i -> read(project, i, verdict, pairs.get(i)))
                .toList();
        return new BatchReviewed(items, watch.truncated());
    }

    private Reviewed read(
            final EvalProject project, final int index, final ReviewVerdict verdict, final ReviewedPair pair) {
        if (!verdict.readable()) {
            return new Reviewed(false, !isClean(pair.maskedCandidate()), 0, "unreadable", "unreadable");
        }
        final ReviewItem item = verdict.itemFor(pair.segmentId()).orElseGet(() -> ReviewItem.ok(pair.segmentId()));
        if (item.status() == ReviewStatus.OK) {
            return new Reviewed(true, false, 0, "ok", "ok");
        }
        final Optional<DraftOutcome> adopted =
                project.translator().adopt(project.judged(index), project.mask(index), pair.maskedCandidate());
        if (adopted.isEmpty() || !(adopted.get() instanceof DraftOutcome.Drafted drafted)) {
            return new Reviewed(true, true, 1, "gate", "the placeholder gate refuses the candidate");
        }
        return resolved(project, drafted, item, pair.maskedCandidate());
    }

    // The run's own resolver: verified edits with the function words, the directed fix after a refused edit, and the
    // rewrite rule; the eval only reads what it came to.
    private Reviewed resolved(
            final EvalProject project,
            final DraftOutcome.Drafted drafted,
            final ReviewItem item,
            final String candidate) {
        final DraftJudge.Judged first = DraftJudge.judge(drafted, project.loop(), project.gate());
        final Result<ReviewJudge.Judged> result = ReviewJudge.resolve(
                first.outcome(), first.qa(), item, project.loop(), project.gate(), editApplier, directedFix, calls);
        if (result.isErr()) {
            return new Reviewed(true, true, 0, "unresolved", "the directed fix call failed");
        }
        final ReviewJudge.Judged judged = Objects.requireNonNull(result.data());
        final boolean changed = !judged.maskedText().equals(first.outcome().maskedReply());
        final int broken = Tokens.inOrder(judged.maskedText()).equals(Tokens.inOrder(candidate)) ? 0 : 1;
        final String detail = item.status().name().toLowerCase(Locale.ROOT) + " rounds=" + judged.rounds()
                + " blockersLeft=" + judged.verifiedBlockersLeft() + " accepted=" + judged.accepted();
        return new Reviewed(
                true,
                changed || judged.verifiedBlockersLeft() > 0,
                broken,
                item.status() + judged.maskedText() + judged.verifiedBlockersLeft(),
                detail);
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

    /** Whether a candidate carries no blocking defect of its own, so an unreadable reply on it is not a miss. */
    private static boolean isClean(final String candidate) {
        return candidate.isBlank();
    }
}
