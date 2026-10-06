package ua.bookloom.pipeline.heal;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.ReviewPass;
import ua.bookloom.pipeline.reviewer.ReviewVerdict;
import ua.bookloom.pipeline.reviewer.ReviewedPair;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/**
 * Starts one chunk's quality loop: evaluates every drafted outcome, makes the chunk's reviewer pass or passes when the
 * dial enables them — over the drafted pairs the checks do not already refuse, never a memory reuse — and hands both
 * to a {@link ChunkDecider} that decides one segment per call ({@code specs/quality-gates/spec.md} "Review each chunk
 * once per pass when the quality dial enables the reviewer").
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class QualityLoop {

    private final ReviewerCall reviewerCall;
    private final EditApplier editApplier;
    private final DirectedFix directedFix;

    /**
     * Starts the chunk's quality loop.
     *
     * @param outcomes the chunk's draft outcomes, in document order
     * @param settings the chunk's review mode, dial, call frame, name policy and glossary terms
     * @param gate the placeholder-gate function every self-heal round's candidate goes through
     * @param calls the seam every model call of this loop is sent through
     * @return a decider that yields one {@link SegmentOutcome} per {@code nextDecision()} call, or the error the
     *     chunk's reviewer call answered
     */
    public Result<ChunkDecider> start(
            final List<DraftOutcome> outcomes,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(calls, "calls");
        log.debug(
                "Starting quality loop chunkSize={} reviewPasses={}",
                outcomes.size(),
                settings.dial().reviewPasses());
        final Map<Integer, QaResult> initialQa = evaluateDrafts(outcomes, settings);
        final ReviewOutcome reviewed = reviewChunk(outcomes, initialQa, settings, calls);
        if (reviewed.error() != null) {
            return Result.err(reviewed.error());
        }
        return Result.ok(new ChunkDecider(outcomes, initialQa, reviewed.verdict(), healer(settings, gate, calls)));
    }

    /**
     * Starts the chunk's quality loop after its reviewer call kept failing and the run gave up on it: every reviewed
     * pair is decided as if the reviewer were unavailable — by the quality checks alone, and flagged.
     *
     * @param outcomes the chunk's draft outcomes, in document order
     * @param settings the chunk's review mode, dial, call frame, name policy and glossary terms
     * @param gate the placeholder-gate function every self-heal round's candidate goes through
     * @param calls the seam every model call of this loop is sent through
     * @param reason the error the chunk's reviewer call last answered
     * @return a decider over the chunk, with no reviewer call made
     */
    public ChunkDecider startWithoutReviewer(
            final List<DraftOutcome> outcomes,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls,
            final AppError reason) {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(reason, "reason");
        log.warn("Deciding a chunk without its reviewer chunkSize={} code={}", outcomes.size(), reason.code());
        return new ChunkDecider(
                outcomes,
                evaluateDrafts(outcomes, settings),
                ReviewVerdict.unavailable(reason),
                healer(settings, Objects.requireNonNull(gate, "gate"), calls));
    }

    /**
     * One reviewer pass over a chunk's pairs, with the term pairs and the character sheet the chunk's loop settings
     * carry — the call a run makes, which a prompt eval makes the same way.
     *
     * @param pairs the chunk's pairs that qualify for review, in document order
     * @param settings the chunk's loop settings
     * @param pass which pass this is
     * @param calls the seam the call is sent through
     * @return the verdict, or the error the call answered
     */
    public Result<ReviewVerdict> review(
            final List<ReviewedPair> pairs,
            final LoopSettings settings,
            final ReviewPass pass,
            final ModelCalls calls) {
        return reviewerCall.review(
                pairs, settings.frame(), settings.glossaryPairs(), settings.characters(), pass, calls);
    }

    private SegmentHealer healer(final LoopSettings settings, final GateFunction gate, final ModelCalls calls) {
        return new SegmentHealer(editApplier, directedFix, settings, gate, calls);
    }

    private Map<Integer, QaResult> evaluateDrafts(final List<DraftOutcome> outcomes, final LoopSettings settings) {
        final Map<Integer, QaResult> results = new LinkedHashMap<>();
        for (int index = 0; index < outcomes.size(); index++) {
            if (outcomes.get(index) instanceof DraftOutcome.Drafted drafted) {
                results.put(index, DraftEvaluation.evaluate(drafted, settings));
            }
        }
        return results;
    }

    /**
     * Makes the chunk's reviewer passes when the dial enables them and at least one pair qualifies. Both passes read
     * the drafts; the second pass's edits are applied after the first's, each verified against the text the earlier
     * ones left.
     */
    private ReviewOutcome reviewChunk(
            final List<DraftOutcome> outcomes,
            final Map<Integer, QaResult> initialQa,
            final LoopSettings settings,
            final ModelCalls calls) {
        if (!settings.dial().hasReviewer()) {
            return new ReviewOutcome(null, null);
        }
        final List<ReviewedPair> pairs = qualifyingPairs(outcomes, initialQa);
        logReviewInput(outcomes, initialQa, pairs);
        if (pairs.isEmpty()) {
            return new ReviewOutcome(null, null);
        }
        final Result<ReviewVerdict> first = review(pairs, settings, ReviewPass.FIRST, calls);
        if (first.isErr() || settings.dial().reviewPasses() < 2) {
            return outcomeOf(first);
        }
        final ReviewVerdict firstVerdict = Objects.requireNonNull(first.data());
        if (!firstVerdict.readable()) {
            return outcomeOf(first);
        }
        final Result<ReviewVerdict> second = review(pairs, settings, ReviewPass.SECOND, calls);
        return second.isErr()
                ? outcomeOf(second)
                : new ReviewOutcome(firstVerdict.followedBy(Objects.requireNonNull(second.data())), null);
    }

    private static ReviewOutcome outcomeOf(final Result<ReviewVerdict> reviewed) {
        return reviewed.isErr() ? new ReviewOutcome(null, reviewed.error()) : new ReviewOutcome(reviewed.data(), null);
    }

    private static List<ReviewedPair> qualifyingPairs(
            final List<DraftOutcome> outcomes, final Map<Integer, QaResult> initialQa) {
        final List<ReviewedPair> pairs = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            addQualifyingPair(pairs, outcomes.get(index), initialQa.get(index));
        }
        return pairs;
    }

    private static void addQualifyingPair(
            final List<ReviewedPair> pairs, final DraftOutcome outcome, @Nullable final QaResult qa) {
        switch (outcome) {
            case DraftOutcome.Drafted drafted -> {
                if (AcceptanceRule.readyForReview(Objects.requireNonNull(qa))) {
                    // The reviewer reads and quotes the text as the model wrote it, protected tokens in place, because
                    // that is the text its edits are applied to and checked on.
                    pairs.add(new ReviewedPair(drafted.segment().id(), drafted.maskedSource(), drafted.maskedReply()));
                }
            }
            case DraftOutcome.FlaggedAtOnce ignored -> {}
            // Already accepted once between the same neighbours; the reviewer never sees a reuse.
            case DraftOutcome.Reused ignored -> {}
            // Nothing was translated, so there is nothing to review.
            case DraftOutcome.Verbatim ignored -> {}
        }
    }

    private static void logReviewInput(
            final List<DraftOutcome> outcomes, final Map<Integer, QaResult> initialQa, final List<ReviewedPair> pairs) {
        final List<String> included =
                pairs.stream().map(ReviewedPair::segmentId).toList();
        final List<String> excluded = excludedFromReview(outcomes, initialQa);
        log.debug("Review input included={} excluded={}", included, excluded);
    }

    private static List<String> excludedFromReview(
            final List<DraftOutcome> outcomes, final Map<Integer, QaResult> initialQa) {
        final List<String> excluded = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            addExcluded(excluded, outcomes.get(index), initialQa.get(index));
        }
        return excluded;
    }

    private static void addExcluded(
            final List<String> excluded, final DraftOutcome outcome, @Nullable final QaResult qa) {
        if (outcome instanceof DraftOutcome.FlaggedAtOnce flaggedAtOnce) {
            excluded.add(flaggedAtOnce.segment().id() + ":flagged-at-once");
        } else if (outcome instanceof DraftOutcome.Reused reused) {
            excluded.add(reused.segment().id() + ":reused");
        } else if (outcome instanceof DraftOutcome.Verbatim verbatim) {
            excluded.add(verbatim.segment().id() + ":verbatim");
        } else if (qa != null && !AcceptanceRule.readyForReview(qa)) {
            excluded.add(outcome.segment().id() + ":refused-by-checks");
        }
    }

    /**
     * The chunk's reviewer outcome: a verdict (possibly {@code null} when the reviewer did not run), or the error that
     * ends {@link #start}. A plain local holder rather than {@link Result}, which never tolerates a {@code null}
     * success value.
     */
    private record ReviewOutcome(
            @Nullable ReviewVerdict verdict, @Nullable AppError error) {}
}
