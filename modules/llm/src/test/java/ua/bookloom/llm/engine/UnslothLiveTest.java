package ua.bookloom.llm.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.llm.testing.LiveServers;

/**
 * Proves the Unsloth rules against a real Unsloth Studio. The address is in BOOKLOOM_LIVE_UNSLOTH_URL (with its /v1)
 * and the API key in BOOKLOOM_LIVE_UNSLOTH_KEY.
 */
@Tag("liveLocal")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_UNSLOTH_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_UNSLOTH_KEY", matches = ".+")
class UnslothLiveTest {

    private static final String PROVIDER = "live-unsloth";
    private static final String KEY_VARIABLE = "BOOKLOOM_LIVE_UNSLOTH_KEY";
    private static final String MODEL_ENV = "BOOKLOOM_LIVE_UNSLOTH_MODEL";
    private static final String WRONG_KEY = "sk-unsloth-wrong-0000";

    // /api/health is Unsloth's own route and needs no key.
    @Test
    void detect_realUnsloth_isUnsloth() {
        final ProviderConfig config = config();
        final Result<EngineDetection> result = LiveServers.verifier(config).detectEngine(config);

        assertThat(result.isOk()).as("detection: " + result.error()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).engine()).isEqualTo(ServerEngine.UNSLOTH);
    }

    // The request carries enable_tools:false (proven on the wire by WireMock); the real server must accept it.
    @Test
    void draft_withKey_isAcceptedWithToolsOff() {
        final Injector injector = LiveServers.injector(config(), Map.of(KEY_VARIABLE, System.getenv(KEY_VARIABLE)));
        final String model = System.getenv().getOrDefault(MODEL_ENV, LiveServers.firstListedModel(injector, PROVIDER));
        injector.getInstance(ProviderVerifier.class)
                .verify(new ModelSelection(PROVIDER, model), VerificationPolicy.FULL);

        final Result<ChatResponse> result =
                LiveServers.chatModel(injector, PROVIDER, model).chat(LiveServers.draft(ReasoningLevel.OFF));

        assertThat(result.isOk()).as("Unsloth draft: " + result.error()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).content()).isNotBlank();
    }

    // A wrong key is answered 401 and reads as the sign-in sentence, not as a bare status.
    @Test
    void draft_wrongKey_saysUnslothNeedsAnApiKey() {
        final Injector injector = LiveServers.injector(config(), Map.of(KEY_VARIABLE, WRONG_KEY));
        final String model = System.getenv().getOrDefault(MODEL_ENV, "any-model");
        injector.getInstance(ProviderVerifier.class)
                .verify(new ModelSelection(PROVIDER, model), VerificationPolicy.FULL);

        final Result<ChatResponse> result =
                LiveServers.chatModel(injector, PROVIDER, model).chat(LiveServers.draft(ReasoningLevel.OFF));

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).message()).contains("Unsloth needs an API key");
    }

    private static ProviderConfig config() {
        return LiveServers.provider(
                PROVIDER,
                ProviderKind.OPENAI_COMPATIBLE,
                LiveServers.requireUrl("BOOKLOOM_LIVE_UNSLOTH_URL"),
                KEY_VARIABLE);
    }
}
