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
                null,
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
        assertThat(defaults.measuredFrom()).isNull();
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
    void constructor_speedWithoutSource_isRejected() {
        assertThatThrownBy(() -> speed(1.2, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_sourceWithoutSpeed_isRejected() {
        assertThatThrownBy(() -> speed(null, MeasuredFrom.RUN)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.5})
    void constructor_nonPositiveSpeed_isRejected(final double value) {
        assertThatThrownBy(() -> speed(value, MeasuredFrom.TEST)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withMeasuredSpeed_modelWithRefusals_keepsRefusalsAndSetsSpeedAndSource() {
        final RefusedSetting refusal = new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "left it out");
        final ModelSettings base = with(TemperatureChoice.OMIT, null, 8192, 512, List.of(refusal));

        final ModelSettings measured = base.withMeasuredSpeed(1.2, MeasuredFrom.TEST);

        assertThat(measured.measuredSecondsPerSegment()).isEqualTo(1.2);
        assertThat(measured.measuredFrom()).isEqualTo(MeasuredFrom.TEST);
        assertThat(measured.refused()).containsExactly(refusal);
        assertThat(measured.contextWindow()).isEqualTo(8192);
    }

    @Test
    void withMeasuredSpeed_secondMeasurement_overwritesSourceToo() {
        final ModelSettings measured = ModelSettings.defaults("ollama", "gemma")
                .withMeasuredSpeed(1.2, MeasuredFrom.TEST)
                .withMeasuredSpeed(0.8, MeasuredFrom.RUN);

        assertThat(measured.measuredSecondsPerSegment()).isEqualTo(0.8);
        assertThat(measured.measuredFrom()).isEqualTo(MeasuredFrom.RUN);
    }

    private static ModelSettings speed(final @Nullable Double seconds, final @Nullable MeasuredFrom from) {
        return new ModelSettings(
                "ollama",
                "gemma",
                TemperatureChoice.CHOOSE,
                null,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(),
                seconds,
                from);
    }

    @Test
    void outputLengthField_wireNames_areTheApiFieldNames() {
        assertThat(OutputLengthField.MAX_TOKENS.wireName()).isEqualTo("max_tokens");
        assertThat(OutputLengthField.MAX_COMPLETION_TOKENS.wireName()).isEqualTo("max_completion_tokens");
    }

    @Test
    void withChoices_storedRefusalAndSpeed_keepsBoth() {
        final RefusedSetting refusal = new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "omitted");
        final ModelSettings base = with(TemperatureChoice.CHOOSE, null, null, null, List.of(refusal))
                .withMeasuredSpeed(1.5, MeasuredFrom.RUN);

        final ModelSettings changed =
                base.withChoices(TemperatureChoice.VALUE, 0.5, 16384, 256, OutputLengthField.MAX_COMPLETION_TOKENS);

        assertThat(changed.temperatureChoice()).isEqualTo(TemperatureChoice.VALUE);
        assertThat(changed.temperature()).isEqualTo(0.5);
        assertThat(changed.contextWindow()).isEqualTo(16384);
        assertThat(changed.maxOutput()).isEqualTo(256);
        assertThat(changed.outputField()).isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
        assertThat(changed.refused()).containsExactly(refusal);
        assertThat(changed.measuredSecondsPerSegment()).isEqualTo(1.5);
        assertThat(changed.measuredFrom()).isEqualTo(MeasuredFrom.RUN);
        assertThat(changed.providerId()).isEqualTo("ollama");
        assertThat(changed.modelId()).isEqualTo("gemma");
    }

    @Test
    void withChoices_valueWithoutTemperature_throws() {
        final ModelSettings base = ModelSettings.defaults("ollama", "gemma");

        assertThatThrownBy(
                        () -> base.withChoices(TemperatureChoice.VALUE, null, null, null, OutputLengthField.MAX_TOKENS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutRefusal_temperatureRow_removesOnlyIt() {
        final RefusedSetting temperature = new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "omitted");
        final RefusedSetting other = new RefusedSetting("top_p", LocalDate.of(2026, 10, 21), "omitted");
        final ModelSettings base = with(TemperatureChoice.OMIT, null, 8192, null, List.of(temperature, other));

        final ModelSettings changed = base.withoutRefusal("temperature");

        assertThat(changed.refused()).containsExactly(other);
        assertThat(changed.temperatureChoice()).isEqualTo(TemperatureChoice.OMIT);
        assertThat(changed.contextWindow()).isEqualTo(8192);
    }

    @Test
    void withoutRefusal_unknownName_returnsEqual() {
        final ModelSettings base = ModelSettings.defaults("ollama", "gemma");

        assertThat(base.withoutRefusal("temperature")).isEqualTo(base);
    }
}
