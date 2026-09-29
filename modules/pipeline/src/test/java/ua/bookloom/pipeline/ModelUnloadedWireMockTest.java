package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** LM Studio answers {@code 400} "Model unloaded" mid-book: the run pauses instead of flagging the rest away. */
class ModelUnloadedWireMockTest {

    private static final int PARAGRAPHS = 40;
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

    // Flagging the 400 would burn every remaining paragraph of the book on a model that is merely unloaded.
    @Test
    void run_secondRequestModelUnloaded_pausesWithValidationAndKeepsThirtyNinePending() {
        final WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE);
        started = provider;
        provider.stubSequence(List.of(
                provider.target("Абзац номер 0.", Duration.ZERO),
                aResponse().withStatus(400).withBody("{\"error\":\"Model unloaded\"}")));
        final String content = IntStream.range(0, PARAGRAPHS)
                .mapToObj(index -> "Paragraph " + index + ".")
                .collect(Collectors.joining("\n\n"));
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), content), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, provider.model(REQUEST_TIMEOUT, ignored -> {}));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.cancel();

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(1, 0, PARAGRAPHS - 1);
        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(PARAGRAPHS - 1, 1, 0, 0, 0));
        assertThat(provider.chatRequests()).isEqualTo(2);
    }
}
