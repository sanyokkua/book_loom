package ua.bookloom.llm.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.testing.LiveServers;

/**
 * The detection matrix against real servers (ADR-0047's falsifier): each address is checked only when its variable is
 * set, and a server is never asked for more than the detector's probe budget.
 */
@Tag("liveLocal")
class EngineDetectionLiveTest {

    private static final String PROXY_ENGINE = "BOOKLOOM_LIVE_PROXY_ENGINE";

    // Ollama's own address, saved with the native kind: found by /api/version and /api/tags.
    @Test
    void detect_realOllamaNativeAddress_isOllamaWithItsVersion() {
        final EngineDetection found = detect(ProviderKind.OLLAMA, LiveServers.requireUrl("BOOKLOOM_LIVE_OLLAMA_URL"));

        assertOnly(found, ServerEngine.OLLAMA);
        assertThat(found.version()).as("Ollama version").isNotBlank();
    }

    // The same Ollama saved as OpenAI-compatible at /v1 is still Ollama, and the native connection is offered.
    @Test
    void detect_realOllamaThroughV1_isOllamaAndOffersTheNativeConnection() {
        final EngineDetection found =
                detect(ProviderKind.OPENAI_COMPATIBLE, LiveServers.requireUrl("BOOKLOOM_LIVE_OLLAMA_URL") + "/v1");

        assertOnly(found, ServerEngine.OLLAMA);
        assertThat(found.offerNativeConnection()).as("native offer").isTrue();
    }

    // LM Studio answers 200 to every path, so only the shape of its replies may name it.
    @Test
    void detect_realLmStudioV1Address_isLmStudio() {
        final EngineDetection found =
                detect(ProviderKind.OPENAI_COMPATIBLE, LiveServers.requireUrl("BOOKLOOM_LIVE_LMSTUDIO_URL") + "/v1");

        assertOnly(found, ServerEngine.LM_STUDIO);
    }

    // LM Studio at its bare origin (no /v1) must not turn into another engine.
    @Test
    void detect_realLmStudioBareOrigin_isLmStudio() {
        final EngineDetection found =
                detect(ProviderKind.OPENAI_COMPATIBLE, LiveServers.requireUrl("BOOKLOOM_LIVE_LMSTUDIO_URL"));

        assertOnly(found, ServerEngine.LM_STUDIO);
    }

    @Test
    void detect_realLlamaServer_isLlamaServerWithABuild() {
        final EngineDetection found =
                detect(ProviderKind.OPENAI_COMPATIBLE, LiveServers.requireUrl("BOOKLOOM_LIVE_LLAMACPP_URL"));

        assertOnly(found, ServerEngine.LLAMA_CPP);
        assertThat(found.version()).as("llama-server build").isNotBlank();
    }

    // A reverse proxy in front of any engine: the address and the expected engine constant come from the owner.
    @Test
    void detect_reverseProxiedAddress_isTheExpectedEngine() {
        final String address = LiveServers.requireUrl("BOOKLOOM_LIVE_PROXY_URL");
        final String expected = System.getenv(PROXY_ENGINE);
        Assumptions.assumeTrue(expected != null && !expected.isBlank(), PROXY_ENGINE + " is not set");

        final EngineDetection found = detect(ProviderKind.OPENAI_COMPATIBLE, address);

        assertOnly(found, ServerEngine.valueOf(Objects.requireNonNull(expected).trim()));
    }

    private static EngineDetection detect(final ProviderKind kind, final String address) {
        final ProviderConfig config = LiveServers.provider("live-detect", kind, address, null);
        final Result<EngineDetection> result = LiveServers.verifier(config).detectEngine(config);
        assertThat(result.isOk())
                .as("detection of " + address + ": " + result.error())
                .isTrue();
        return Objects.requireNonNull(result.data(), "detection");
    }

    private static void assertOnly(final EngineDetection found, final ServerEngine expected) {
        assertThat(found.engine()).as("engine detected").isEqualTo(expected);
        assertThat(found.source()).isEqualTo(Source.DETECTED);
        assertThat(found.suitability()).isEqualTo(Suitability.SUITABLE);
        assertThat(found.probes()).as("GETs sent").isPositive().isLessThanOrEqualTo(EngineDetector.MAX_PROBES);
    }
}
