package ua.bookloom.ui;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.SegmentRecord;

/**
 * A hand-written {@link ReviewDesk} that records each call and answers with what a test queued, in order; a call with
 * nothing queued answers an {@code internal} error so an unexpected call is loud.
 */
public final class ScriptedReviewDesk implements ReviewDesk {

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final Queue<Result<?>> answers = new ConcurrentLinkedQueue<>();

    /** Queues the answer the next call gets, whatever the method; the caller states the matching result type. */
    public void willAnswer(final Result<?> answer) {
        answers.add(answer);
    }

    /** Every call as {@code method(arguments)}, in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public Result<SegmentRecord> accept(final String projectId, final String segmentId) {
        return answer("accept(" + projectId + ", " + segmentId + ")");
    }

    @Override
    public Result<SegmentRecord> saveEdit(final String projectId, final String segmentId, final String maskedText) {
        return answer("saveEdit(" + projectId + ", " + segmentId + ")");
    }

    @Override
    public Result<SegmentRecord> revert(final String projectId, final String segmentId) {
        return answer("revert(" + projectId + ", " + segmentId + ")");
    }

    @Override
    public Result<SegmentRecord> retry(
            final String projectId,
            final String segmentId,
            @Nullable final String note,
            final boolean lowerTemperature,
            final ChatModel model) {
        return answer("retry(" + projectId + ", " + segmentId + ", lowerTemperature=" + lowerTemperature + ")");
    }

    @Override
    public Result<SegmentRecord> skip(final String projectId, final String segmentId) {
        return answer("skip(" + projectId + ", " + segmentId + ")");
    }

    @Override
    public Result<SegmentRecord> acceptProposal(final String projectId, final String segmentId) {
        return answer("acceptProposal(" + projectId + ", " + segmentId + ")");
    }

    @Override
    public Result<List<SegmentView>> queue(final String projectId, final ReviewFilter filter) {
        return answer("queue(" + projectId + ", " + filter + ")");
    }

    @Override
    public Result<SegmentView> segment(final String projectId, final String segmentId) {
        return answer("segment(" + projectId + ", " + segmentId + ")");
    }

    @Override
    public Result<ReviewCounts> counts(final String projectId) {
        return answer("counts(" + projectId + ")");
    }

    @SuppressWarnings("unchecked")
    private <T> Result<T> answer(final String call) {
        calls.add(call);
        final Result<?> queued = answers.poll();
        if (queued != null) {
            return (Result<T>) queued;
        }
        return Result.err(AppError.of(ErrorCode.internal, "Not scripted", "The scripted review desk has no answer."));
    }
}
