package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;

/** Exercises the in-memory provider registry's presets, replacement, lookup, and validation boundary. */
class InMemoryProviderConfigsTest {

    @Test
    void constructor_seedsBothLocalPresetsWithContractDefaults() {
        final ProviderConfigs configs = new InMemoryProviderConfigs();

        assertThat(configs.find("ollama"))
                .contains(new ProviderConfig(
                        "ollama",
                        ProviderKind.OLLAMA,
                        URI.create("http://localhost:11434"),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        assertThat(configs.find("lmstudio"))
                .contains(new ProviderConfig(
                        "lmstudio",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create("http://localhost:1234/v1"),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        assertThat(configs.all()).hasSize(2);
    }

    @Test
    void register_existingId_replacesPresetForThisProcess() {
        final ProviderConfigs configs = new InMemoryProviderConfigs();
        final ProviderConfig replacement = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://10.0.0.5:11434"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30));

        final Result<ProviderConfig> result = configs.register(replacement);

        assertThat(result.data()).isEqualTo(replacement);
        assertThat(configs.find("ollama")).contains(replacement);
        assertThat(configs.all()).hasSize(2);
    }

    @Test
    void find_unknownId_returnsEmpty() {
        assertThat(new InMemoryProviderConfigs().find("gemini")).isEmpty();
    }

    @Test
    void register_relativeBaseUrl_returnsValidationAndDoesNotRegister() {
        assertRejected(new ProviderConfig(
                "relative",
                ProviderKind.OLLAMA,
                URI.create("localhost:11434"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
    }

    @Test
    void register_nonHttpScheme_returnsValidationAndDoesNotRegister() {
        assertRejected(new ProviderConfig(
                "ftp",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("ftp://models.example.test/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
    }

    @Test
    void register_zeroTimeout_returnsValidationAndDoesNotRegister() {
        assertRejected(new ProviderConfig(
                "zero-timeout",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                Duration.ofSeconds(2),
                Duration.ZERO));
    }

    @Test
    void register_negativeConnectTimeout_returnsValidationAndDoesNotRegister() {
        assertRejected(new ProviderConfig(
                "negative-timeout",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                Duration.ofSeconds(-1),
                Duration.ofSeconds(10)));
    }

    private static void assertRejected(ProviderConfig invalid) {
        final ProviderConfigs configs = new InMemoryProviderConfigs();
        final Result<ProviderConfig> result = configs.register(invalid);

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
        assertThat(configs.find(invalid.id())).isEmpty();
        assertThat(configs.all()).hasSize(2);
    }
}
