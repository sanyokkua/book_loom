package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** Proves the chat-call timeout scales with expected output, with the configured timeout always the floor. */
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
    void forChat_expectedOutputGiven_scalesBetweenConfiguredFloorAndCeiling(
            long configuredSeconds, int expectedOutputTokens, long effectiveSeconds) {
        final ProviderConfig scaled =
                RequestTimeouts.forChat(config(Duration.ofSeconds(configuredSeconds)), expectedOutputTokens);

        assertThat(scaled.requestTimeout()).isEqualTo(Duration.ofSeconds(effectiveSeconds));
    }

    @Test
    void forChat_noExpectedOutput_keepsConfiguredTimeoutUnchanged() {
        final ProviderConfig config = config(Duration.ofMinutes(3));

        final ProviderConfig scaled = RequestTimeouts.forChat(config, null);

        assertThat(scaled).isSameAs(config);
    }

    private static ProviderConfig config(Duration requestTimeout) {
        return new ProviderConfig(
                "test", ProviderKind.OLLAMA, URI.create("http://localhost"), Duration.ofSeconds(10), requestTimeout);
    }
}
