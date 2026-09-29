package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * A run paused by an error or for review holds no model-call permit, so another caller of the same gate is never
 * queued behind it.
 */
class PausedRunHoldsNoPermitTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

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

    // A permit kept across the pause would make a review retry wait for a run that is waiting for the person.
    @Test
    void run_pausedOnError_holdsNoPermit() {
        final WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE);
        started = provider;
        provider.stubSequence(List.of(
                aResponse().withStatus(400).withBody("{\"error\":\"Model unloaded\"}"),
                provider.reply("HELLO", Duration.ZERO)));
        final WireMockProvider.TwoModels models = provider.twoModels(REQUEST_TIMEOUT);
        final TranslationJobImpl translation =
                job(project(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), brief("en", "uk")), models.first());
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        final Result<ChatResponse> other = await(executor()
                .submit(() -> models.second().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))))));
        translation.cancel();

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(other.data()).isNotNull().extracting(ChatResponse::content).isEqualTo("HELLO");
        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
    }

    // A review pause waits for the person, who may retry the segment or test the provider meanwhile.
    @Test
    void run_pausedOnFlagged_holdsNoPermit() {
        final WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE);
        started = provider;
        provider.stubSequence(List.of(
                provider.target(ChunkRunFixtures.ECHO1, Duration.ZERO),
                provider.target(ChunkRunFixtures.ECHO1, Duration.ZERO),
                provider.reply("HELLO", Duration.ZERO)));
        final WireMockProvider.TwoModels models = provider.twoModels(REQUEST_TIMEOUT);
        final TranslationJobImpl translation = job(
                project(TestBooks.markdown(tempDir.resolve("Book.md"), ChunkRunFixtures.S1), brief("en", "uk")),
                models.first(),
                ReviewMode.ASSISTED);
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        final Result<ChatResponse> other = await(executor()
                .submit(() -> models.second().chat(new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))))));
        translation.cancel();

        assertThat(pause)
                .extracting(Paused::reason, Paused::segmentId)
                .containsExactly(PauseReason.ON_FLAGGED, "Book.md:0");
        assertThat(other.data()).isNotNull().extracting(ChatResponse::content).isEqualTo("HELLO");
        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
    }
}
