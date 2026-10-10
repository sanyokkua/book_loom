package ua.bookloom.ui;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.SegmentRecord;

/**
 * A hand-written {@link ReviewDesk} that records each call and answers with what a test queued, in order; a call with
 * nothing queued answers an {@code internal} error so an unexpected call is loud.
 */
public final class ScriptedReviewDesk implements ReviewDesk {

    private static final long WAIT_SECONDS = 10;

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final Queue<Result<?>> answers = new ConcurrentLinkedQueue<>();
    private final Map<String, SegmentView> views = new ConcurrentHashMap<>();
    private volatile @Nullable Result<List<SegmentView>> queueView;
    private volatile @Nullable Result<ReviewCounts> countsView;
    private volatile @Nullable CountDownLatch acceptGate;
    private final CountDownLatch acceptEntered = new CountDownLatch(1);
    private volatile @Nullable CountDownLatch retryGate;

    /** Queues the answer the next call gets, whatever the method; the caller states the matching result type. */
    public void willAnswer(final Result<?> answer) {
        answers.add(answer);
    }

    /** Makes every {@code queue} call answer these views, whatever else was queued; the queue is not consumed. */
    public void willAnswerQueue(final List<SegmentView> views) {
        queueView = Result.ok(List.copyOf(views));
    }

    /**
     * Makes {@code segment} answer this view for its id, and every action on that id answer a record of it when
     * nothing was queued; a later call for the same id replaces the view.
     */
    public void willAnswerSegment(final SegmentView view) {
        views.put(view.segmentId(), view);
    }

    /** Makes every {@code counts} call answer these counts, whatever else was queued. */
    public void willAnswerCounts(final ReviewCounts counts) {
        countsView = Result.ok(counts);
    }

    /** From now on {@code accept} blocks before it answers until {@link #releaseAccept()}, as a slow desk would. */
    public void holdAccept() {
        acceptGate = new CountDownLatch(1);
    }

    /** From now on {@code retry} blocks before it answers until {@link #releaseRetry()}, as a slow model would. */
    public void holdRetry() {
        retryGate = new CountDownLatch(1);
    }

    /** Lets the held {@code retry} answer; a no-op when none is held. */
    public void releaseRetry() {
        final CountDownLatch held = retryGate;
        if (held != null) {
            held.countDown();
        }
    }

    /** Lets the held {@code accept} answer; a no-op when none is held. */
    public void releaseAccept() {
        final CountDownLatch held = acceptGate;
        if (held != null) {
            held.countDown();
        }
    }

    /** Blocks the caller until an {@code accept} call has been made and is being held or answered. */
    public void awaitAcceptCalled() throws InterruptedException {
        if (!acceptEntered.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("accept was never called");
        }
    }

    /** Every call as {@code method(arguments)}, in order. */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public Result<SegmentRecord> accept(final String projectId, final String segmentId) {
        acceptEntered.countDown();
        await(acceptGate);
        return action("accept(" + projectId + ", " + segmentId + ")", projectId, segmentId);
    }

    @Override
    public Result<SegmentRecord> saveEdit(final String projectId, final String segmentId, final String maskedText) {
        return action("saveEdit(" + projectId + ", " + segmentId + ", " + maskedText + ")", projectId, segmentId);
    }

    @Override
    public Result<SegmentRecord> revert(final String projectId, final String segmentId) {
        return action("revert(" + projectId + ", " + segmentId + ")", projectId, segmentId);
    }

    @Override
    public Result<SegmentRecord> retry(
            final String projectId,
            final String segmentId,
            @Nullable final String note,
            final boolean lowerTemperature,
            final ChatModel model) {
        await(retryGate);
        return action(
                "retry(" + projectId + ", " + segmentId + ", note=" + note + ", lowerTemperature=" + lowerTemperature
                        + ")",
                projectId,
                segmentId);
    }

    @Override
    public Result<SegmentRecord> skip(final String projectId, final String segmentId) {
        return action("skip(" + projectId + ", " + segmentId + ")", projectId, segmentId);
    }

    @Override
    public Result<SegmentRecord> acceptProposal(final String projectId, final String segmentId) {
        return action("acceptProposal(" + projectId + ", " + segmentId + ")", projectId, segmentId);
    }

    @Override
    public Result<List<SegmentView>> queue(final String projectId, final ReviewFilter filter) {
        final Result<List<SegmentView>> view = queueView;
        if (view != null) {
            calls.add("queue(" + projectId + ", " + filter + ")");
            return view;
        }
        return answer("queue(" + projectId + ", " + filter + ")");
    }

    @Override
    public Result<SegmentView> segment(final String projectId, final String segmentId) {
        final SegmentView view = views.get(segmentId);
        if (view != null) {
            calls.add("segment(" + projectId + ", " + segmentId + ")");
            return Result.ok(view);
        }
        return answer("segment(" + projectId + ", " + segmentId + ")");
    }

    @Override
    public Result<ReviewCounts> counts(final String projectId) {
        final Result<ReviewCounts> view = countsView;
        if (view != null) {
            calls.add("counts(" + projectId + ")");
            return view;
        }
        return answer("counts(" + projectId + ")");
    }

    @Override
    public Result<List<SuspiciousSegment>> audit(final String projectId) {
        return answer("audit(" + projectId + ")");
    }

    private static void await(final @Nullable CountDownLatch held) {
        try {
            if (held != null && !held.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the held call");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while a call was held", e);
        }
    }

    private Result<SegmentRecord> action(final String call, final String projectId, final String segmentId) {
        final SegmentView view = views.get(segmentId);
        if (view == null || !answers.isEmpty()) {
            return answer(call);
        }
        calls.add(call);
        return Result.ok(recordOf(projectId, view));
    }

    /** The stored record a desk would answer for this view. */
    public static SegmentRecord recordOf(final String projectId, final SegmentView view) {
        return new SegmentRecord(
                projectId,
                view.segmentId(),
                "unit",
                0,
                view.kind(),
                view.status(),
                view.maskedMachineTarget(),
                view.maskedMachineTarget(),
                view.userTarget(),
                view.maskedUserTarget(),
                0.0,
                view.judgeScore(),
                view.findings(),
                view.path(),
                0,
                view.reviewed(),
                view.context());
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
