package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.Subscription;

/** Proves event order, count snapshots, reports, subscribers, and atomic run claiming. */
class TranslationJobEventsTest {

    /** What one segment accepted on its first call announces, in order. */
    private static final List<String> ACCEPTED_ON_FIRST_CALL =
            List.of("SegmentStarted", "ModelCallStarted", "ModelCallFinished", "SegmentDrafted", "SegmentDecided");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Changing order or pending bookkeeping would break these hard-coded lifecycle snapshots, and a screen built from
    // the events alone would show a segment it never announced.
    @Test
    void events_threeAcceptedSegments_areExactAndOrdered() {
        final TranslationJobImpl translation = job(
                TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree."),
                replies("ONE.", "TWO.", "THREE."));
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(events)
                .extracting(TranslationJobEventsTest::label)
                .containsExactlyElementsOf(concat(List.of(
                        List.of("StageStarted PREP", "StageStarted TRANSLATE"),
                        acceptedOnFirstCall("Book.txt:0", 2),
                        acceptedOnFirstCall("Book.txt:1", 1),
                        acceptedOnFirstCall("Book.txt:2", 0),
                        List.of("MemoryUpdated SUMMARY", "Finished COMPLETED"))));
        assertThat(events)
                .filteredOn(StageStarted.class::isInstance)
                .extracting(
                        event -> ((StageStarted) event).stage(),
                        event -> ((StageStarted) event).progress().section(),
                        event -> ((StageStarted) event).progress().sections(),
                        event -> ((StageStarted) event).progress().accepted(),
                        event -> ((StageStarted) event).progress().flagged(),
                        event -> ((StageStarted) event).progress().pending())
                .containsExactly(tuple(JobStage.PREP, 1, 1, 0, 0, 3), tuple(JobStage.TRANSLATE, 1, 1, 0, 0, 3));
    }

    // Building the Finished event from other counts than the returned report would let a screen show one outcome while
    // the caller acts on another.
    @Test
    void finished_event_carriesTheReportRunReturns() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final Result<JobReport> result = translation.run();

        assertThat(events.getLast()).isEqualTo(new ua.bookloom.api.pipeline.Finished(report(result)));
    }

    // Installing the terminal state after Finished would make this callback observe RUNNING instead.
    @Test
    void finished_callback_observesTerminalState() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final AtomicReference<JobState> observed = new AtomicReference<>();
        translation.subscribe(event -> observeFinishedState(translation, observed, event));

        final Result<JobReport> result = translation.run();

        assertThat(observed.get()).isEqualTo(JobState.COMPLETED);
        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
    }

    // Losing a flag reason or its segment order would change this preserved report list.
    @Test
    void report_flaggedSegments_retainsOrderedIdsAndReasons() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(TranslationJobTestSupport.cutOffReply())
                .answer(Result.ok(new ChatResponse("", FinishReason.STOP)));

        final JobReport result =
                report(markdownJob(model, "One.\n\nTwo.\n\nThree.").run());

        assertThat(result)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 1, 2);
        assertThat(result.flaggedSegments())
                .extracting(FlaggedSegment::segmentId, FlaggedSegment::reason)
                .containsExactly(
                        tuple("Book.md:1", ErrorCode.validation), tuple("Book.md:2", ErrorCode.emptyCompletion));
    }

    // Removing a subscription during iteration would throw or deliver a second callback here.
    @Test
    void subscription_unsubscribeInsideCallback_isSafe() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final AtomicReference<Subscription> self = new AtomicReference<>();
        final AtomicInteger selfCalls = new AtomicInteger();
        final List<JobEvent> healthy = new ArrayList<>();
        self.set(translation.subscribe(event -> unsubscribeAfterFirst(self, selfCalls)));
        translation.subscribe(healthy::add);

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(selfCalls).hasValue(1);
        assertThat(healthy).hasSize(9);
    }

    // Equal listener instances still need independently removable subscription handles.
    @Test
    void subscription_duplicateListener_handlesRemainIndependent() {
        final JobSubscribers subscribers = new JobSubscribers();
        final AtomicInteger calls = new AtomicInteger();
        final JobListener listener = event -> calls.incrementAndGet();
        final Subscription first = subscribers.add(listener);
        final Subscription second = subscribers.add(listener);

        first.unsubscribe();
        first.unsubscribe();
        subscribers.deliver(stageEvent());

        assertThat(calls).hasValue(1);
        second.unsubscribe();
        subscribers.deliver(stageEvent());
        assertThat(calls).hasValue(1);
    }

    // Keeping a listener after it throws would invoke it again and starve neither healthy subscriber.
    @Test
    void subscriber_throwingOnce_isRemovedAndHealthySubscriberContinues() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final AtomicInteger throwerCalls = new AtomicInteger();
        final List<JobEvent> healthy = new ArrayList<>();
        translation.subscribe(event -> throwOnce(throwerCalls));
        translation.subscribe(healthy::add);

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(throwerCalls).hasValue(1);
        assertThat(healthy)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactlyElementsOf(concat(List.of(
                        List.of("StageStarted", "StageStarted"),
                        ACCEPTED_ON_FIRST_CALL,
                        List.of("MemoryUpdated", "Finished"))));
    }

    // Moving work across this pause boundary would insert another decision between Paused and Resumed.
    @Test
    void events_pauseAndResume_areOrderedAroundBoundary() {
        final TranslationJobImpl translation = markdownJob(replies("ONE.", "TWO."), "One.\n\nTwo.");
        final List<JobEvent> events = new ArrayList<>();
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> captureAndRecord(pauses, events, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        translation.pauseAt(Set.of());
        translation.resume();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactlyElementsOf(concat(List.of(
                        List.of("StageStarted", "StageStarted"),
                        ACCEPTED_ON_FIRST_CALL,
                        List.of("Paused", "Resumed"),
                        ACCEPTED_ON_FIRST_CALL,
                        List.of("MemoryUpdated", "Finished"))));
        shutdown(workers);
    }

    // Treating an empty book as having no section would reject this zero-count progress.
    @Test
    void progress_emptyBook_usesSectionZero() {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), "");
        final ScriptedChatModel model = replies();
        final TranslationJobImpl translation = job(source, model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final JobReport result = report(translation.run());

        assertThat(result)
                .extracting(JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 0, 0, 0);
        assertThat(events)
                .filteredOn(StageStarted.class::isInstance)
                .extracting(
                        event -> ((StageStarted) event).stage(),
                        event -> ((StageStarted) event).progress().section(),
                        event -> ((StageStarted) event).progress().sections(),
                        event -> ((StageStarted) event).progress().accepted(),
                        event -> ((StageStarted) event).progress().flagged(),
                        event -> ((StageStarted) event).progress().pending())
                .containsExactly(tuple(JobStage.PREP, 0, 1, 0, 0, 0), tuple(JobStage.TRANSLATE, 0, 1, 0, 0, 0));
        assertThat(events).noneMatch(SegmentDecided.class::isInstance);
        assertThat(model.requests()).isEmpty();
    }

    // Replacing the atomic claim with a state check would let both callers return successful reports.
    @Test
    void run_concurrentCallers_onlyOneClaimsExecution() {
        final ScriptedChatModel scripted = replies("ONE.");
        final BlockingModels.BlockingChatModel model = new BlockingModels.BlockingChatModel(scripted);
        final TranslationJobImpl translation = markdownJob(model, "One.");
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch loserFinished = new CountDownLatch(1);
        final ExecutorService firstWorker = executor();
        final ExecutorService secondWorker = executor();

        final Future<Result<JobReport>> first =
                firstWorker.submit(() -> awaitStartThenRecordError(start, loserFinished, translation));
        final Future<Result<JobReport>> second =
                secondWorker.submit(() -> awaitStartThenRecordError(start, loserFinished, translation));
        start.countDown();
        model.awaitEntered();
        awaitLatch(loserFinished);
        model.release();
        final List<Result<JobReport>> results = List.of(await(first), await(second));

        assertThat(results).filteredOn(Result::isOk).hasSize(1);
        assertThat(results)
                .filteredOn(Result::isErr)
                .extracting(Result::error)
                .extracting(AppError::code)
                .containsExactly(ErrorCode.validation);
        assertThat(scripted.requests()).hasSize(1);
        shutdown(firstWorker);
        shutdown(secondWorker);
    }

    private TranslationJobImpl markdownJob(final ua.bookloom.api.llm.ChatModel model, final String content) {
        return job(TestBooks.markdown(tempDir.resolve("Book.md"), content), model);
    }

    private static List<String> acceptedOnFirstCall(final String segmentId, final int pendingAfter) {
        return List.of(
                "SegmentStarted " + segmentId,
                "ModelCallStarted DRAFT " + segmentId,
                "ModelCallFinished DRAFT " + segmentId,
                "SegmentDrafted " + segmentId,
                "SegmentDecided " + segmentId + " ACCEPTED pending=" + pendingAfter);
    }

    private static List<String> concat(final List<List<String>> parts) {
        return parts.stream().flatMap(List::stream).toList();
    }

    private static String label(final JobEvent event) {
        return switch (event) {
            case StageStarted started -> "StageStarted " + started.stage();
            case SegmentStarted started -> "SegmentStarted " + started.segmentId();
            case ModelCallStarted started -> "ModelCallStarted " + started.kind() + " " + started.segmentId();
            case ModelCallFinished finished -> "ModelCallFinished " + finished.kind() + " " + finished.segmentId();
            case SegmentDrafted drafted -> "SegmentDrafted " + drafted.segmentId();
            case SegmentDecided decided ->
                "SegmentDecided " + decided.segmentId() + " " + decided.status() + " pending="
                        + decided.progress().pending();
            case MemoryUpdated updated -> "MemoryUpdated " + updated.kind();
            case Finished finished -> "Finished " + finished.report().end();
            default -> event.getClass().getSimpleName();
        };
    }

    private static void observeFinishedState(
            final TranslationJobImpl translation, final AtomicReference<JobState> observed, final JobEvent event) {
        if (event instanceof Finished) {
            observed.set(translation.state());
        }
    }

    private static void unsubscribeAfterFirst(final AtomicReference<Subscription> self, final AtomicInteger calls) {
        calls.incrementAndGet();
        java.util.Objects.requireNonNull(self.get(), "subscription").unsubscribe();
    }

    private static void throwOnce(final AtomicInteger calls) {
        calls.incrementAndGet();
        throw new IllegalStateException("test subscriber failure");
    }

    private static StageStarted stageEvent() {
        return new StageStarted(JobStage.TRANSLATE, new JobProgress(JobStage.TRANSLATE, 0, 1, 0, 0, 1));
    }

    private static void captureAndRecord(
            final LinkedBlockingQueue<Paused> pauses, final List<JobEvent> events, final JobEvent event) {
        events.add(event);
        capturePaused(pauses, event);
    }

    private static Result<JobReport> awaitStartThenRun(
            final CountDownLatch start, final TranslationJobImpl translation) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting to start concurrent run");
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting to start concurrent run", cause);
        }
        return translation.run();
    }

    private static Result<JobReport> awaitStartThenRecordError(
            final CountDownLatch start, final CountDownLatch loserFinished, final TranslationJobImpl translation) {
        final Result<JobReport> result = awaitStartThenRun(start, translation);
        if (result.isErr()) {
            loserFinished.countDown();
        }
        return result;
    }

    private static void awaitLatch(final CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for concurrent losing run");
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for concurrent losing run", cause);
        }
    }
}
