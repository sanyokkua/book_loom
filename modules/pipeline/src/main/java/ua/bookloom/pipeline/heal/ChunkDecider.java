package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Decides a chunk's segments one at a time, in document order — a {@link DraftOutcome.FlaggedAtOnce} outcome
 * becomes FLAGGED immediately, a {@link DraftOutcome.Drafted} one goes through {@link SegmentHealer}
 * ({@code specs/translation-pipeline/spec.md} "Decide a chunk's segments in document order"). Built only by
 * {@link QualityLoop#start}.
 */
@Slf4j
public final class ChunkDecider {

    private final List<DraftOutcome> outcomes;
    private final Map<Integer, QaResult> initialQa;
    private final @Nullable JudgeVerdict chunkVerdict;
    private final SegmentHealer healer;
    private int index;

    ChunkDecider(
            final List<DraftOutcome> outcomes,
            final Map<Integer, QaResult> initialQa,
            @Nullable final JudgeVerdict chunkVerdict,
            final SegmentHealer healer) {
        this.outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        this.initialQa = Map.copyOf(Objects.requireNonNull(initialQa, "initialQa"));
        this.chunkVerdict = chunkVerdict;
        this.healer = Objects.requireNonNull(healer, "healer");
    }

    /**
     * Whether an undecided segment remains.
     *
     * @return {@code true} until every segment of the chunk has been decided
     */
    public boolean hasNext() {
        return index < outcomes.size();
    }

    /**
     * Decides the next segment, in document order.
     *
     * <p>The current segment's index advances only when this call returns {@link Result#ok}. On
     * {@link Result#err}, the index is left where it was, so the segment that answered the error is not skipped:
     * the next {@code nextDecision()} call — after a pause-and-resume, a retried model call, or any other reason
     * the caller re-drives the step — redoes that same segment rather than moving on to the next one.
     *
     * @return the segment's decision, or the error a model call answered that ends this step
     * @throws NoSuchElementException when {@link #hasNext()} is {@code false}
     */
    public Result<SegmentOutcome> nextDecision() {
        if (!hasNext()) {
            throw new NoSuchElementException("every segment of this chunk is already decided");
        }
        final Result<SegmentOutcome> decision =
                switch (outcomes.get(index)) {
                    case DraftOutcome.FlaggedAtOnce flaggedAtOnce -> Result.ok(flaggedOutcome(flaggedAtOnce));
                    case DraftOutcome.Drafted drafted ->
                        healer.decide(drafted, Objects.requireNonNull(initialQa.get(index)), chunkVerdict);
                };
        if (decision.isOk()) {
            index++;
        }
        return decision;
    }

    private static SegmentOutcome flaggedOutcome(final DraftOutcome.FlaggedAtOnce flaggedAtOnce) {
        log.warn(
                "Segment {} flagged at once code={}",
                flaggedAtOnce.segment().id(),
                flaggedAtOnce.error().code());
        return new SegmentOutcome(
                flaggedAtOnce.segment().id(),
                SegmentStatus.FLAGGED,
                null,
                null,
                0.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                flaggedAtOnce.error());
    }
}
