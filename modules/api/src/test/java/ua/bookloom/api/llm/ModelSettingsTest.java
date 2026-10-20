package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelSettingsTest {

    private static ModelSettings with(
            final TemperatureChoice choice,
            final @Nullable Double temperature,
            final @Nullable Integer context,
            final @Nullable Integer maxOutput,
            final List<RefusedSetting> refused) {
        return new ModelSettings(
                "ollama",
                "gemma",
                choice,
                temperature,
                context,
                maxOutput,
                OutputLengthField.MAX_TOKENS,
                refused,
                null);
    }

    @Test
    void defaults_forAModel_chooseTemperatureAndSendMaxTokens() {
        final ModelSettings defaults = ModelSettings.defaults("ollama", "gemma");

        assertThat(defaults.temperatureChoice()).isEqualTo(TemperatureChoice.CHOOSE);
        assertThat(defaults.temperature()).isNull();
        assertThat(defaults.contextWindow()).isNull();
        assertThat(defaults.maxOutput()).isNull();
        assertThat(defaults.outputField()).isEqualTo(OutputLengthField.MAX_TOKENS);
        assertThat(defaults.refused()).isEmpty();
        assertThat(defaults.measuredSecondsPerSegment()).isNull();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.7, 2.0})
    void constructor_valueTemperatureInRange_isAccepted(final double value) {
        assertThat(with(TemperatureChoice.VALUE, value, null, null, List.of()).temperature())
                .isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 2.1})
    void constructor_valueTemperatureOutOfRange_isRejected(final double value) {
        assertThatThrownBy(() -> with(TemperatureChoice.VALUE, value, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_valueWithoutTemperature_isRejected() {
        assertThatThrownBy(() -> with(TemperatureChoice.VALUE, null, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_chooseWithTemperature_isRejected() {
        assertThatThrownBy(() -> with(TemperatureChoice.CHOOSE, 0.5, null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_nonPositiveContextOrAnswerCap_isRejected(final int value) {
        assertThatThrownBy(() -> with(TemperatureChoice.CHOOSE, null, value, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> with(TemperatureChoice.CHOOSE, null, null, value, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_refusedList_isCopiedDefensively() {
        final List<RefusedSetting> source = new ArrayList<>();
        source.add(new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "left it out"));
        final ModelSettings settings = with(TemperatureChoice.OMIT, null, null, null, source);

        source.clear();

        assertThat(settings.refused()).hasSize(1);
    }

    @Test
    void constructor_nullProviderId_isRejected() {
        assertThatThrownBy(() -> ModelSettings.defaults(nullString(), "m")).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("NullAway")
    private static String nullString() {
        return null;
    }

    @Test
    void outputLengthField_wireNames_areTheApiFieldNames() {
        assertThat(OutputLengthField.MAX_TOKENS.wireName()).isEqualTo("max_tokens");
        assertThat(OutputLengthField.MAX_COMPLETION_TOKENS.wireName()).isEqualTo("max_completion_tokens");
    }
}
