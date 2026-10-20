package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/** Behaviour every {@link ModelSettingsRepository} adapter must show. */
public abstract class ModelSettingsContractTest {

    protected abstract ModelSettingsRepository repository();

    private static ModelSettings sample(final String provider, final String model) {
        return new ModelSettings(
                provider,
                model,
                TemperatureChoice.VALUE,
                0.4,
                8192,
                1024,
                OutputLengthField.MAX_COMPLETION_TOKENS,
                List.of(new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "left it out")),
                2.5);
    }

    @Test
    void find_nothingSaved_returnsEmpty() {
        assertThat(repository().find("ollama", "gemma").data()).isEmpty();
    }

    @Test
    void save_thenFind_returnsTheSameSettings() {
        final ModelSettings saved = sample("ollama", "gemma");

        assertThat(repository().save(saved).isOk()).isTrue();

        assertThat(repository().find("ollama", "gemma").data()).hasValue(saved);
    }

    @Test
    void save_twice_keepsTheLast() {
        repository().save(sample("ollama", "gemma"));
        final ModelSettings cleared = ModelSettings.defaults("ollama", "gemma");
        repository().save(cleared);

        assertThat(repository().find("ollama", "gemma").data()).hasValue(cleared);
    }

    @Test
    void find_otherModelOrProvider_isKeptApart() {
        repository().save(sample("ollama", "gemma"));

        assertThat(repository().find("ollama", "other").data()).isEmpty();
        assertThat(repository().find("lmstudio", "gemma").data()).isEmpty();
    }

    @Test
    void all_listsEverySavedModel() {
        repository().save(sample("ollama", "gemma"));
        repository().save(sample("lmstudio", "qwen"));

        assertThat(repository().all().data())
                .extracting(ModelSettings::modelId)
                .containsExactlyInAnyOrder("gemma", "qwen");
    }

    @Test
    void all_nothingSaved_isEmpty() {
        assertThat(repository().all().data()).isEmpty();
    }

    @Test
    void delete_savedModel_removesItAndTheOthersStay() {
        repository().save(sample("ollama", "gemma"));
        repository().save(sample("ollama", "other"));

        assertThat(repository().delete("ollama", "gemma").isOk()).isTrue();

        assertThat(repository().find("ollama", "gemma").data()).isEmpty();
        assertThat(repository().find("ollama", "other").data()).isPresent();
    }

    @Test
    void delete_unknownModel_isStillOk() {
        assertThat(repository().delete("ollama", "none").isOk()).isTrue();
    }
}
