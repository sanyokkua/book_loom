package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TimedJobs.timedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;

/**
 * A llama-server answers a draft {@code 503 Loading model}: the run waits as for an unloaded model, its wakes ask
 * {@code /health} first, and the draft is sent again only once the server no longer says it is loading.
 */
class ModelLoadingWireMockTest extends UnattendedRecoveryHarness {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String HEALTH = "/health";
    private static final String CHAT = "POST /v1/chat/completions";

    private @Nullable WireMockProvider started;

    @AfterEach
    void closeProvider() {
        if (started != null) {
            started.close();
        }
    }

    @Test
    void run_draftAnswered503Loading_waitsForHealthThenResends() {
        final WireMockProvider provider = loadingServer();
        final TranslationJobImpl job = timedJob(
                project(book(), brief("en", "uk")),
                provider.model(REQUEST_TIMEOUT, ignored -> {}),
                clock,
                instantTimer(),
                NO_TICKS);
        job.subscribe(events::add);
        job.recoverWith(provider.probe());

        final JobReport report = report(job.run());

        assertThat(pauses()).hasSize(1);
        final AppError pausedOn = pauses().getFirst().error();
        assertThat(pausedOn).extracting(AppError::code).isEqualTo(ErrorCode.modelUnavailable);
        assertThat(FailureReason.in(pausedOn == null ? null : pausedOn.details()))
                .contains(FailureReason.MODEL_LOADING);
        assertThat(report)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 3, 0);
        assertThat(provider.getRequests(HEALTH)).isEqualTo(2);
        assertThat(provider.chatRequests()).isEqualTo(4);
        final List<String> served = provider.servedInOrder();
        assertThat(nthIndexOf(served, CHAT, 3)).isGreaterThan(nthIndexOf(served, "GET " + HEALTH, 2));
    }

    // The second draft is answered "Loading model"; /health says loading once, then ok.
    private WireMockProvider loadingServer() {
        final WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE);
        started = provider;
        provider.stubSequence(List.of(
                provider.target("ОДИН.", Duration.ZERO),
                aResponse().withStatus(503).withBody("{\"error\":{\"message\":\"Loading model\"}}"),
                provider.target("ДВА.", Duration.ZERO),
                provider.target("ТРИ.", Duration.ZERO)));
        provider.stubGet(
                HEALTH,
                aResponse().withStatus(503).withBody("{\"status\":\"loading model\"}"),
                okJson("{\"status\":\"ok\"}"));
        provider.stubGet("/v1/models", okJson("{\"data\":[{\"id\":\"" + WireMockProvider.MODEL_ID + "\"}]}"));
        return provider;
    }

    private RecoveryTimer instantTimer() {
        return delay -> {
            clock.advance(delay);
            return 0;
        };
    }

    // The position of the n-th (from one) entry equal to {@code entry}, or -1 when there are fewer.
    private static int nthIndexOf(final List<String> served, final String entry, final int n) {
        final List<Integer> positions = IntStream.range(0, served.size())
                .filter(index -> served.get(index).equals(entry))
                .boxed()
                .toList();
        return positions.size() < n ? -1 : positions.get(n - 1);
    }
}
