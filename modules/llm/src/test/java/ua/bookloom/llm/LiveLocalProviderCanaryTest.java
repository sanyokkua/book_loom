package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The {@code liveLocal} canary: proof that the tag partition is real in both directions.
 *
 * <p>It proves two things no other test in this change can. First, that {@code ./gradlew test} and
 * {@code ./gradlew check} never load this class — the tag is excluded by the default {@code Test} task, so a
 * merge gate on a runner with no Ollama can never block on it. Second, that {@code ./gradlew liveLocal} <em>does</em>
 * reach it, and reports green-with-skips rather than red when the endpoint is not configured
 * ({@code docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#live-local}, DD-35).
 *
 * <p>The real {@code liveLocal} suite — prompt shaping, the serialized request per dialect, structured-output
 * negotiation and {@code <think>}-stripping against what a local model actually emits — arrives with the provider
 * change. This class is the wiring, not the coverage.
 *
 * <p><strong>Required environment:</strong> {@code BOOKLOOM_LIVE_OLLAMA_URL} (the Ollama-native endpoint) and
 * {@code BOOKLOOM_LIVE_LMSTUDIO_URL} (the OpenAI-compatible endpoint). Each test is gated on the variable its own
 * dialect needs, so a machine running only one of the two servers still runs the half it can.
 */
@Tag("liveLocal")
class LiveLocalProviderCanaryTest {

    // Covers task 5.3/5.5: the `liveLocal` task reaches a `liveLocal`-tagged test when the Ollama endpoint is
    // configured, and JUnit skips it — rather than failing it — when the variable is absent.
    @Test
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_OLLAMA_URL", matches = "\\S+")
    void liveLocal_ollamaEndpointConfigured_readsAnAbsoluteEndpoint() {
        assertThat(URI.create(System.getenv("BOOKLOOM_LIVE_OLLAMA_URL")).isAbsolute())
                .as("BOOKLOOM_LIVE_OLLAMA_URL must be an absolute URL, e.g. http://localhost:11434")
                .isTrue();
    }

    // Covers task 5.3/5.5: the same gate for the OpenAI-compatible dialect, so a machine running only LM Studio
    // skips the Ollama case and runs this one.
    @Test
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_LMSTUDIO_URL", matches = "\\S+")
    void liveLocal_lmStudioEndpointConfigured_readsAnAbsoluteEndpoint() {
        assertThat(URI.create(System.getenv("BOOKLOOM_LIVE_LMSTUDIO_URL")).isAbsolute())
                .as("BOOKLOOM_LIVE_LMSTUDIO_URL must be an absolute URL, e.g. http://localhost:1234")
                .isTrue();
    }
}
