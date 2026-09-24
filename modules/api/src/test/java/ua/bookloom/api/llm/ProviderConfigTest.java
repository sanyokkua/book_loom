package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Contract tests for provider descriptions and their copy-with methods. */
class ProviderConfigTest {

    @Test
    void defaultTimeouts_areTenAndOneHundredEightySeconds() {
        assertThat(ProviderConfig.DEFAULT_CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
        assertThat(ProviderConfig.DEFAULT_REQUEST_TIMEOUT).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void withBaseUrl_changesOnlyTheBaseUrl() {
        final ProviderConfig config = providerConfig();
        final URI replacement = URI.create("http://127.0.0.1:11434");

        assertThat(config.withBaseUrl(replacement))
                .isEqualTo(new ProviderConfig(
                        "local", ProviderKind.OLLAMA, replacement, Duration.ofSeconds(3), Duration.ofSeconds(40)));
    }

    @Test
    void withRequestTimeout_changesOnlyTheRequestTimeout() {
        final ProviderConfig config = providerConfig();
        final Duration replacement = Duration.ofSeconds(90);

        assertThat(config.withRequestTimeout(replacement))
                .isEqualTo(new ProviderConfig(
                        "local",
                        ProviderKind.OLLAMA,
                        URI.create("http://localhost:11434"),
                        Duration.ofSeconds(3),
                        replacement));
    }

    @SuppressWarnings("NullAway")
    @Test
    void nullId_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ProviderConfig(
                        null,
                        ProviderKind.OLLAMA,
                        URI.create("http://localhost"),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)));
    }

    @SuppressWarnings("NullAway")
    @Test
    void nullKind_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ProviderConfig(
                        "local", null, URI.create("http://localhost"), Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @SuppressWarnings("NullAway")
    @Test
    void nullBaseUrl_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ProviderConfig(
                        "local", ProviderKind.OLLAMA, null, Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @SuppressWarnings("NullAway")
    @Test
    void nullConnectTimeout_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ProviderConfig(
                        "local", ProviderKind.OLLAMA, URI.create("http://localhost"), null, Duration.ofSeconds(1)));
    }

    @SuppressWarnings("NullAway")
    @Test
    void nullRequestTimeout_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ProviderConfig(
                        "local", ProviderKind.OLLAMA, URI.create("http://localhost"), Duration.ofSeconds(1), null));
    }

    private static ProviderConfig providerConfig() {
        return new ProviderConfig(
                "local",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                Duration.ofSeconds(3),
                Duration.ofSeconds(40));
    }
}
