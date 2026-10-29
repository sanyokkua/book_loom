package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** A provider answers a verb-first "request exceeds the context size" 400: that segment is flagged and the run goes on. */
class ContextOverflowWireMockTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String OVERFLOW_BODY =
            "{\"error\":\"request (696 tokens) exceeds the available context size (512 tokens)\"}";
    private static final String BOOK = String.join(
            "\n\n",
            "Lantern one glowed.",
            "Lantern two glowed.",
            "Lantern three glowed.",
            "Lantern four glowed.",
            "Lantern five glowed.",
            "Lantern six glowed.");

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

    // A context overflow is a property of one segment's prompt, so pausing the whole run would stall the book.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_secondDraftOverflowWordedVerbFirst_flagsThatSegmentAndDoesNotPause(final ProviderKind kind) {
        final WireMockProvider provider = new WireMockProvider(kind);
        started = provider;
        provider.stubSequence(List.of(
                provider.target("Абзац номер 0.", Duration.ZERO),
                provider.target("Абзац номер 1.", Duration.ZERO),
                aResponse().withStatus(400).withBody(OVERFLOW_BODY),
                provider.target("Абзац номер 3.", Duration.ZERO),
                provider.target("Абзац номер 4.", Duration.ZERO),
                provider.target("Абзац номер 5.", Duration.ZERO)));
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), BOOK), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, provider.model(REQUEST_TIMEOUT, ignored -> {}));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final JobReport report = report(await(run));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.md:2", ErrorCode.contextWindow));
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 5, 0, 1, 0));
        assertThat(pauses).isEmpty();
        assertThat(provider.chatRequests()).isEqualTo(6);
    }

    // A flag that names both counts tells the person how far over the window the passage went.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_overflowWithCounts_flagNoteNamesBothCounts(final ProviderKind kind) {
        final WireMockProvider provider = new WireMockProvider(kind);
        started = provider;
        provider.stubSequence(List.of(
                provider.target("Абзац номер 0.", Duration.ZERO),
                provider.target("Абзац номер 1.", Duration.ZERO),
                aResponse().withStatus(400).withBody(OVERFLOW_BODY),
                provider.target("Абзац номер 3.", Duration.ZERO),
                provider.target("Абзац номер 4.", Duration.ZERO),
                provider.target("Абзац номер 5.", Duration.ZERO)));
        final TestProject project = project(TestBooks.markdown(tempDir.resolve("Book.md"), BOOK), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, provider.model(REQUEST_TIMEOUT, ignored -> {}));

        report(await(executor().submit(translation::run)));

        assertThat(stored(project, "Book.md:2").findings())
                .filteredOn(finding -> finding.raisedBy().equals("reply"))
                .extracting(QaFinding::note)
                .containsExactly("This passage did not fit the model's window (696 of 512 tokens). It was flagged.");
    }
}
