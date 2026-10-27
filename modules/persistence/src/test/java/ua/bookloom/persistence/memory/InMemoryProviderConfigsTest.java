package ua.bookloom.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;

/** Exercises what only the in-memory provider registry does: it starts with the two local server presets. */
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
    void save_presetId_replacesPresetForThisProcess() {
        final ProviderConfigs configs = new InMemoryProviderConfigs();
        final ProviderConfig replacement = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://10.0.0.5:11434"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);

        configs.save(replacement);

        assertThat(configs.find("ollama")).contains(replacement);
        assertThat(configs.all()).hasSize(2);
    }

    @Test
    void delete_preset_leavesTheOther() {
        final ProviderConfigs configs = new InMemoryProviderConfigs();

        assertThat(configs.delete("ollama").data()).isTrue();

        assertThat(configs.all()).hasSize(1);
    }
}
