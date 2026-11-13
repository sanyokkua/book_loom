package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A model that loops on one passage twice: the second request goes out with a new seed, and when it loops again the
 * segment is flagged with words a person reads and the book goes on, without a pause.
 */
class RepeatLimitWireMockTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String REPEAT_LIMIT = "{\"error\":\"token repeat limit reached\"}";
    private static final Pattern SEED = Pattern.compile("\"seed\"\\s*:\\s*(\\d+)");
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

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_twoLoops_flagsTheSegmentWithTheWordsAndDoesNotPause(final ProviderKind kind) {
        final WireMockProvider provider = new WireMockProvider(kind);
        started = provider;
        provider.stubSequence(List.of(
                provider.target("Абзац номер 0.", Duration.ZERO),
                provider.target("Абзац номер 1.", Duration.ZERO),
                aResponse().withStatus(500).withBody(REPEAT_LIMIT),
                aResponse().withStatus(500).withBody(REPEAT_LIMIT),
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
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.md:2", ErrorCode.upstream));
        assertThat(stored(project, "Book.md:2").findings())
                .filteredOn(finding -> finding.raisedBy().equals("reply"))
                .extracting(QaFinding::note)
                .containsExactly("The model kept repeating itself here.");
        assertThat(pauses).isEmpty();
        assertThat(provider.chatRequests()).isEqualTo(7);
        final List<String> bodies = provider.chatBodies();
        assertThat(seedOf(bodies.get(3))).isPresent().isNotEqualTo(seedOf(bodies.get(2)));
    }

    private static Optional<String> seedOf(final String body) {
        final Matcher matcher = SEED.matcher(body);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
