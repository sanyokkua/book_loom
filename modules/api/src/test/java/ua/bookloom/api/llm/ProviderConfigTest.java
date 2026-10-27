package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

    @Test
    void fiveArgConstructor_leavesTheNewFieldsAtTheirDefaults() {
        final ProviderConfig config = providerConfig();

        assertThat(config.displayName()).isNull();
        assertThat(config.auth()).isEqualTo(ProviderAuth.NONE);
        assertThat(config.apiKeyHeader()).isNull();
        assertThat(config.credentialRef()).isNull();
        assertThat(config.modelsPath()).isNull();
        assertThat(config.chatPath()).isNull();
        assertThat(config.headers()).isEmpty();
        assertThat(config.customModels()).isEmpty();
    }

    @Test
    void withers_carryEveryOtherField() {
        final ProviderConfig full = providerConfig()
                .withDisplayName("Mine")
                .withAuth(ProviderAuth.API_KEY)
                .withApiKeyHeader("X-Api-Key")
                .withCredentialRef("MY_KEY")
                .withModelsPath("/m")
                .withChatPath("/c")
                .withHeaders(Map.of("Accept", "x"))
                .withCustomModels(List.of("a", "b"));

        final ProviderConfig moved =
                full.withBaseUrl(URI.create("http://other")).withRequestTimeout(Duration.ofSeconds(5));

        assertThat(moved.displayName()).isEqualTo("Mine");
        assertThat(moved.auth()).isEqualTo(ProviderAuth.API_KEY);
        assertThat(moved.apiKeyHeader()).isEqualTo("X-Api-Key");
        assertThat(moved.credentialRef()).isEqualTo("MY_KEY");
        assertThat(moved.modelsPath()).isEqualTo("/m");
        assertThat(moved.chatPath()).isEqualTo("/c");
        assertThat(moved.headers()).containsEntry("Accept", "x");
        assertThat(moved.customModels()).containsExactly("a", "b");
    }

    @Test
    void headersAndCustomModels_areDefensivelyCopied() {
        final java.util.ArrayList<String> models = new java.util.ArrayList<>(List.of("a"));
        final ProviderConfig config = providerConfig().withCustomModels(models);
        models.add("b");

        assertThat(config.customModels()).containsExactly("a");
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
