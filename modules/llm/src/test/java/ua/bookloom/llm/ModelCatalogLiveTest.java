package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;

/** Proves the model catalog against a configured, real local Ollama service. */
@Tag("liveLocal")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_OLLAMA_URL", matches = ".+")
class ModelCatalogLiveTest {

    // A real Ollama must report at least one installed model through the catalog.
    @Test
    void listModels_realOllama_returnsNonEmptyList() {
        final InMemoryProviderConfigs configs = new InMemoryProviderConfigs();
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(
                        Objects.requireNonNull(System.getenv("BOOKLOOM_LIVE_OLLAMA_URL"), "BOOKLOOM_LIVE_OLLAMA_URL")),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
        assertThat(configs.register(config).isOk()).isTrue();
        final ModelCatalogService catalog = new ModelCatalogService(
                configs,
                new ProviderClientFactory(new HttpExchange(new HttpClients()), new LlmModule().objectMapper()));

        final Result<List<ModelInfo>> result = catalog.listModels("ollama");

        assertThat(result.isOk()).as("Ollama catalog result: " + result.error()).isTrue();
        assertThat(Objects.requireNonNull(result.data(), "models")).isNotEmpty();
    }
}
