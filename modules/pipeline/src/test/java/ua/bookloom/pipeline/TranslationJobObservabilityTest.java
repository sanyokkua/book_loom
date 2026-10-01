package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.target;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.ContextAssembled;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * What a run tells the screen about itself beyond its decisions: the context each draft is sent with, the repair
 * rounds a segment enters, which step an error pause stopped and how often, and a skip from a pause taken while a
 * call hung.
 */
class TranslationJobObservabilityTest {

    private static final String MEANING_ON_S1 = "{\"score\":0.9,\"verdict\":\"revise\",\"findings\":[{\"segmentId\":"
            + "\"s1\",\"type\":\"meaning\",\"severity\":\"medium\",\"note\":\"drops a word\"}],\"deferrals\":[]}";
    private static final String FIXED_T0 = "Старий чоловік повільно пішов до гавані.";
    private static final long WAIT_SECONDS = 10;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // The second draft is sent with the first one's translation before it, in the text a person reads.
    @Test
    void run_twoSegments_secondContextHoldsTheFirstTranslation() {
        final TranslationJobImpl translation = job(project(threeLines(), brief("en", "uk")), replies("ONE.", "TWO."));
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);

        report(await(executor().submit(translation::run)));

        assertThat(events)
                .filteredOn(ContextAssembled.class::isInstance)
                .map(ContextAssembled.class::cast)
                .extracting(
                        ContextAssembled::segmentId,
                        assembled -> assembled.context().precedingTargets())
                .startsWith(tuple("Book.txt:0", List.of()), tuple("Book.txt:1", List.of("ONE.")));
    }

    // A segment the judge did not accept enters round 1 of the dial's budget, naming the finding it repairs.
    @Test
    void run_judgeFindsAMeaningError_announcesRoundOneWithTheScoreAndTheFinding() {
        final ScriptedChatModel model = replies(T0, T1, FIXED_T0)
                .answerTo(
                        ChunkRunFixtures.JUDGE,
                        Result.ok(new ChatResponse(MEANING_ON_S1, ua.bookloom.api.llm.FinishReason.STOP)))
                .answerTo(ChunkRunFixtures.JUDGE, ChunkRunFixtures.judged());
        final TranslationJobImpl translation =
                job(project(twoParagraphs(), brief("en", "uk", QualityDial.BALANCED)), model);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);

        report(await(executor().submit(translation::run)));

        assertThat(events)
                .filteredOn(RoundStarted.class::isInstance)
                .map(RoundStarted.class::cast)
                .extracting(
                        RoundStarted::segmentId,
                        RoundStarted::round,
                        RoundStarted::judgeScore,
                        RoundStarted::blockingFinding)
                .containsExactly(tuple("Book.md:0", 1, 0.9, "meaning"));
    }

    // A pause on an error names the segment whose step failed and how many of its pauses are spent.
    @Test
    void run_draftFailingTwice_eachPauseNamesTheSegmentAndCountsItsPauses() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(Result.err(error(ErrorCode.unreachable)))
                .answer(Result.err(error(ErrorCode.unreachable)))
                .answer(target("TWO."))
                .answer(target("THREE."));
        final TranslationJobImpl translation = job(project(threeLines(), brief("en", "uk")), model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused first = awaitPaused(pauses);
        translation.resume();
        final Paused second = awaitPaused(pauses);
        translation.resume();
        report(await(run));

        assertThat(List.of(first, second))
                .extracting(Paused::segmentId, Paused::pauses, Paused::pausesBeforeFlagging)
                .containsExactly(tuple("Book.txt:1", 1, 2), tuple("Book.txt:1", 2, 2));
    }

    // A call that hangs is paused by the person, who then skips it: the segment is flagged and the run goes on.
    @Test
    void skipSegment_afterPausingAHangingCall_flagsThatSegmentAndContinues() throws InterruptedException {
        final HangingSecondCall model = new HangingSecondCall(replies("ONE.", "THREE."));
        final TestProject project = project(threeLines(), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        assertThat(model.hanging.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        translation.pause();
        awaitPaused(pauses);
        translation.skipSegment();
        final JobReport report = report(await(run));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(stored(project, "Book.txt:1").status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(stored(project, "Book.txt:2").status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    private Path twoParagraphs() {
        return TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", S0, S1));
    }

    private Path threeLines() {
        return TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
    }

    private static LinkedBlockingQueue<Paused> pausesOf(final TranslationJobImpl translation) {
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        return pauses;
    }

    private static AppError error(final ErrorCode code) {
        return AppError.of(code, "Provider failure", "The scripted provider failed.");
    }

    /** Hangs on its second request until the job interrupts it, answering that as the provider client does. */
    private static final class HangingSecondCall implements ChatModel {

        private final ChatModel delegate;
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch hanging = new CountDownLatch(1);
        private final CountDownLatch never = new CountDownLatch(1);

        HangingSecondCall(final ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            if (calls.incrementAndGet() != 2) {
                return delegate.chat(request);
            }
            hanging.countDown();
            try {
                never.await();
                return Result.err(error(ErrorCode.internal));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "The call was interrupted."));
            }
        }
    }
}
