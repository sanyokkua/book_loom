package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Proves a chat call's timeout is chosen by its kind: the judge and the helper calls have their own bound, every other
 * call scales with its expected output with the configured timeout as the floor.
 */
class RequestTimeoutsTest {

    @ParameterizedTest
    @CsvSource({
        "180,40,180",
        "180,400,200",
        "180,1200,600",
        "180,2000,600",
        "30,40,30",
        "30,400,200",
        "900,2000,900",
        "1,400,200",
        "1,1,1"
    })
    void forChat_draftWithExpectedOutput_scalesBetweenConfiguredFloorAndCeiling(
            long configuredSeconds, int expectedOutputTokens, long effectiveSeconds) {
        final ProviderConfig scaled = RequestTimeouts.forChat(
                config(Duration.ofSeconds(configuredSeconds)), request(CallKind.DRAFT, expectedOutputTokens));

        assertThat(scaled.requestTimeout()).isEqualTo(Duration.ofSeconds(effectiveSeconds));
    }

    @Test
    void forChat_noKindAndNoExpectedOutput_keepsConfiguredTimeoutUnchanged() {
        final ProviderConfig config = config(Duration.ofMinutes(3));

        final ProviderConfig scaled = RequestTimeouts.forChat(config, request(null, null));

        assertThat(scaled).isSameAs(config);
    }

    // The judge's reply is capped, so a judge still running after ninety seconds is stuck, not slow.
    @ParameterizedTest
    @CsvSource({"180,", "180,512", "600,1024", "30,"})
    void forChat_judge_isNinetySecondsWhateverTheConfigAndExpectedOutput(
            long configuredSeconds, @Nullable Integer expectedOutputTokens) {
        final ProviderConfig judged = RequestTimeouts.forChat(
                config(Duration.ofSeconds(configuredSeconds)), request(CallKind.REVIEW, expectedOutputTokens));

        assertThat(judged.requestTimeout()).isEqualTo(Duration.ofSeconds(90));
    }

    @ParameterizedTest
    @CsvSource({"PRESCAN", "REVIEW_TERMS", "SUGGEST_TARGETS", "SUMMARY"})
    void forChat_prescanOrSummary_isTwoMinutes(CallKind kind) {
        final ProviderConfig bounded = RequestTimeouts.forChat(config(Duration.ofMinutes(3)), request(kind, null));

        assertThat(bounded.requestTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @ParameterizedTest
    @CsvSource({"180,60", "90,60", "30,30"})
    void streamIdle_configuredTimeout_isSixtySecondsOrTheConfiguredTimeoutWhenShorter(
            long requestSeconds, long idleSeconds) {
        assertThat(RequestTimeouts.streamIdle(config(Duration.ofSeconds(requestSeconds))))
                .isEqualTo(Duration.ofSeconds(idleSeconds));
    }

    private static ChatRequest request(@Nullable CallKind kind, @Nullable Integer expectedOutputTokens) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                null,
                null,
                null,
                null,
                expectedOutputTokens,
                null,
                null,
                kind);
    }

    private static ProviderConfig config(Duration requestTimeout) {
        return new ProviderConfig(
                "test", ProviderKind.OLLAMA, URI.create("http://localhost"), Duration.ofSeconds(10), requestTimeout);
    }
}
