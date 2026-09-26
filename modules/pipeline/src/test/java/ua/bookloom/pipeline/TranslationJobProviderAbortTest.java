package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.documents;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;

/** Pause and Stop abort the request in flight at the HTTP seam, on both provider dialects. */
class TranslationJobProviderAbortTest {

    private static final Duration LONG_DELAY = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final long STOP_BUDGET_SECONDS = 5;

    @TempDir
    private Path tempDir;

    private @Nullable WireMockProvider started;

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
        if (started != null) {
            started.close();
        }
    }

    // A Stop that waited for the 30-second reply would blow the budget; the decided first segment must stay counted.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_stopDuringSecondSlowRequest_keepsTheFirstAcceptedAndLeavesTwoPending(final ProviderKind kind) {
        final WireMockProvider provider = start(kind);
        provider.stubSequence(List.of(
                provider.target("ONE.", Duration.ZERO),
                provider.target("NEVER SEEN.", LONG_DELAY),
                provider.target("NEVER SEEN.", LONG_DELAY)));
        final TranslationJobImpl translation =
                markdownJob(provider.model(REQUEST_TIMEOUT, ignored -> {}), "One.\n\nTwo.\n\nThree.");
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        provider.awaitChatRequests(2);
        sleepOneSecond();
        final long stoppedAt = System.nanoTime();
        translation.cancel();
        final JobReport result = report(await(run));

        assertThat(result)
                .extracting(JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.CANCELLED, 3, 1, 0);
        assertThat(Duration.ofNanos(System.nanoTime() - stoppedAt)).isLessThan(Duration.ofSeconds(STOP_BUDGET_SECONDS));
        assertThat(provider.chatRequests()).isEqualTo(2);
        assertThat(Files.exists(tempDir.resolve("Book.uk.md"))).isFalse();
    }

    // Without aborting the draft in flight, its invalid reply would trigger a structural repair request afterwards.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_stopDuringRepair_sendsNoRepairRequest(final ProviderKind kind) {
        final WireMockProvider provider = start(kind);
        provider.stubAlways(provider.reply("this is not the JSON object", Duration.ofSeconds(2)));
        final TranslationJobImpl translation = markdownJob(provider.model(REQUEST_TIMEOUT, ignored -> {}), "One.");
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        provider.awaitChatRequests(1);
        translation.cancel();
        final JobReport result = report(await(run));

        assertThat(result.end()).isEqualTo(JobState.CANCELLED);
        assertThat(provider.chatRequests()).isEqualTo(1);
    }

    // Letting the retry loop sleep out its backoff would send a second request before the pause took effect.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_pauseDuringTimeoutBackoff_sendsNoFurtherRequest(final ProviderKind kind) {
        final WireMockProvider provider = start(kind);
        provider.stubAlways(provider.target("ONE.", Duration.ofSeconds(10)));
        final CountDownLatch backoff = new CountDownLatch(1);
        final ChatModel model = provider.model(Duration.ofMillis(300), delay -> waitInBackoff(backoff));
        final TranslationJobImpl translation = markdownJob(model, "One.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitLatch(backoff);
        translation.pause();
        final Paused paused = awaitPaused(pauses);
        translation.cancel();

        assertThat(paused.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(paused.error()).isNull();
        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(provider.chatRequests()).isEqualTo(1);
    }

    // Applying the aborted segment, or dropping it, would make the provider see it once or the report count it wrong.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_pauseThenResume_redoesTheInterruptedSegmentOnce(final ProviderKind kind) {
        final WireMockProvider provider = start(kind);
        provider.stubSequence(List.of(
                provider.target("ONE.", Duration.ZERO),
                provider.target("NEVER SEEN.", LONG_DELAY),
                provider.target("TWO.", Duration.ZERO),
                provider.target("THREE.", Duration.ZERO)));
        final TranslationJobImpl translation =
                markdownJob(provider.model(REQUEST_TIMEOUT, ignored -> {}), "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        provider.awaitChatRequests(2);
        sleepOneSecond();
        translation.pause();
        final Paused paused = awaitPaused(pauses);
        final int sentWhilePaused = provider.chatRequests();
        translation.resume();
        final JobReport result = report(await(run));

        assertThat(paused.progress())
                .extracting(JobProgress::accepted, JobProgress::pending)
                .containsExactly(1, 2);
        assertThat(sentWhilePaused).isEqualTo(2);
        assertThat(provider.chatBodies()).hasSize(4);
        assertThat(provider.chatBodies().get(1)).isEqualTo(provider.chatBodies().get(2));
        assertThat(result)
                .extracting(JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 3, 0);
    }

    private WireMockProvider start(final ProviderKind kind) {
        started = new WireMockProvider(kind);
        return started;
    }

    private TranslationJobImpl markdownJob(final ChatModel model, final String content) {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), content);
        return job(documents(), source, tempDir.resolve("Book.uk.md"), model);
    }

    private static void sleepOneSecond() {
        try {
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while letting the request run", cause);
        }
    }

    private static void waitInBackoff(final CountDownLatch backoff) throws InterruptedException {
        backoff.countDown();
        Thread.sleep(Duration.ofSeconds(10));
    }

    private static void awaitLatch(final CountDownLatch latch) {
        try {
            if (!latch.await(STOP_BUDGET_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("the retry loop never reached its backoff");
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for the backoff", cause);
        }
    }
}
