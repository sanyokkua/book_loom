package ua.bookloom.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Verifies exhaustive provider dispatch and reuse by provider configuration id. */
class ProviderClientFactoryTest {

    @Test
    void create_ollamaConfigReturnsNativeClient() {
        assertThat(factory().create(ollamaConfig())).isInstanceOf(OllamaClient.class);
    }

    @Test
    void create_openAiConfigReturnsCompatibleClient() {
        assertThat(factory().create(openAiConfig())).isInstanceOf(OpenAiCompatibleClient.class);
    }

    @Test
    void create_sameConfigIdReturnsCachedClient() {
        final ProviderClientFactory factory = factory();

        final ProviderClient first = factory.create(openAiConfig());
        final ProviderClient second = factory.create(openAiConfig());

        assertThat(second).isSameAs(first);
    }

    @Test
    void create_replacedConfigWithSameIdUsesNewClient() {
        final ProviderClientFactory factory = factory();

        final ProviderClient first = factory.create(openAiConfig());
        final ProviderConfig replacement = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://localhost:4321/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        final ProviderClient second = factory.create(replacement);

        assertThat(second).isNotSameAs(first);
    }

    private static ProviderClientFactory factory() {
        return new ProviderClientFactory(new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }

    private static ProviderConfig ollamaConfig() {
        return config("ollama", ProviderKind.OLLAMA, "http://localhost:11434");
    }

    private static ProviderConfig openAiConfig() {
        return config("lmstudio", ProviderKind.OPENAI_COMPATIBLE, "http://localhost:1234/v1");
    }

    private static ProviderConfig config(String id, ProviderKind kind, String endpoint) {
        return new ProviderConfig(id, kind, URI.create(endpoint), Duration.ofSeconds(2), Duration.ofSeconds(5));
    }
}
