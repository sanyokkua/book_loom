package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TimedJobs.timedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.Paused;

/**
 * A model call whose provider never answers is ended by the watchdog at one and a half times its timeout: the gate is
 * released, the call is retried as a timeout, and after the budget the segment is flagged and the run goes on.
 */
class TranslationJobStallWatchdogTest {

    private static final Duration JUDGE_LIKE_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration PAST_THE_CEILING = Duration.ofSeconds(136);

    @TempDir
    private Path tempDir;

    private final ScriptedClock clock = new ScriptedClock();
    private final AtomicReference<@Nullable Runnable> tick = new AtomicReference<>();
    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_callThatNeverReturns_isEndedRetriedThenFlaggedAndTheRunGoesOn() {
        final HangingModel model = new HangingModel(replies("ONE.", "THREE."), 2, 3);
        final TranslationJobImpl job = timedJob(
                project(TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree."), brief("en", "uk")),
                model,
                clock,
                delay -> {
                    clock.advance(delay);
                    return 0;
                },
                (period, ticking) -> {
                    tick.set(ticking);
                    return () -> tick.set(null);
                });
        job.subscribe(events::add);

        final Future<Result<JobReport>> run = executor().submit(job::run);
        endHangingCalls(model, 3);
        final JobReport report = report(await(run));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:1", ErrorCode.timeout));
        assertThat(report.accepted()).isEqualTo(2);
        assertThat(events)
                .filteredOn(Paused.class::isInstance)
                .map(Paused.class::cast)
                .extracting(pause -> Objects.requireNonNull(pause.error()).code())
                .containsExactly(ErrorCode.timeout, ErrorCode.timeout);
        assertThat(model.gate.availablePermits()).isEqualTo(1);
        assertThat(tick.get()).isNull();
    }

    // Kept out of the test body: one stall per hanging call, each found by the watchdog past its ceiling.
    private void endHangingCalls(final HangingModel model, final int count) {
        for (int call = 1; call <= count; call++) {
            model.awaitHanging();
            clock.advance(PAST_THE_CEILING);
            Objects.requireNonNull(tick.get(), "the watchdog ticks while the run runs")
                    .run();
        }
    }

    /** Hangs the calls it is told to until interrupted, holding a gate-like permit the whole time. */
    private static final class HangingModel implements ChatModel {

        private final ChatModel answers;
        private final List<Integer> hanging;
        private final AtomicInteger calls = new AtomicInteger();
        private final LinkedBlockingQueue<Integer> hung = new LinkedBlockingQueue<>();
        private final Semaphore gate = new Semaphore(1);

        HangingModel(final ChatModel answers, final int firstHanging, final int hangingCount) {
            this.answers = answers;
            this.hanging = java.util.stream.IntStream.range(firstHanging, firstHanging + hangingCount)
                    .boxed()
                    .toList();
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            return chat(request, CallAttemptListener.NONE);
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
            gate.acquireUninterruptibly();
            try {
                final int call = calls.incrementAndGet();
                if (!hanging.contains(call)) {
                    return answers.chat(request, attempts);
                }
                final CallAttempt attempt = new CallAttempt(1, 1, JUDGE_LIKE_TIMEOUT, null);
                attempts.started(attempt);
                hung.add(call);
                return hangUntilInterrupted(attempts, attempt);
            } finally {
                gate.release();
            }
        }

        void awaitHanging() {
            try {
                Objects.requireNonNull(hung.poll(5, TimeUnit.SECONDS), "a hanging call");
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for a hanging call", cause);
            }
        }

        private static Result<ChatResponse> hangUntilInterrupted(
                final CallAttemptListener attempts, final CallAttempt attempt) {
            try {
                new CountDownLatch(1).await(5, TimeUnit.SECONDS);
                throw new AssertionError("the watchdog never ended the call");
            } catch (InterruptedException cause) {
                attempts.failed(attempt, ErrorCode.cancelled);
                return Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "The call was interrupted."));
            }
        }
    }
}
