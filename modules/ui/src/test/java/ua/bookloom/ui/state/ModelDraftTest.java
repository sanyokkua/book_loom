package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.i18n.MessageKey;

/** The Models tab's draft: what it shows for stored settings, what stops a save, and what a save writes. */
class ModelDraftTest {

    private static final ModelSettings DEFAULTS = ModelSettings.defaults("ollama", "gemma4:e4b");

    private static ModelSettings stored(final int context, final int limit) {
        return DEFAULTS.withChoices(
                TemperatureChoice.VALUE, 0.5, context, limit, OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    private static ModelSettings saved(final Result<ModelSettings> result) {
        return Objects.requireNonNull(result.data());
    }

    @Test
    void of_defaults_chooseNoLimits() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS);

        assertThat(draft.choice()).isEqualTo(TemperatureChoice.CHOOSE);
        assertThat(draft.temperature()).isEqualTo(0.2);
        assertThat(draft.contextOn()).isFalse();
        assertThat(draft.contextText()).isEmpty();
        assertThat(draft.limitOn()).isFalse();
        assertThat(draft.limitText()).isEmpty();
        assertThat(draft.field()).isEqualTo(OutputLengthField.MAX_TOKENS);
    }

    @Test
    void of_storedValues_showThemWithTheSwitchesOn() {
        final ModelDraft draft = ModelDraft.of(stored(16384, 256));

        assertThat(draft.choice()).isEqualTo(TemperatureChoice.VALUE);
        assertThat(draft.temperature()).isEqualTo(0.5);
        assertThat(draft.contextOn()).isTrue();
        assertThat(draft.contextText()).isEqualTo("16384");
        assertThat(draft.limitOn()).isTrue();
        assertThat(draft.limitText()).isEqualTo("256");
        assertThat(draft.field()).isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void withContext_onWithSuggestion16384_holds16384() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS).withContext(true, "", 16384);

        assertThat(draft.contextOn()).isTrue();
        assertThat(draft.contextText()).isEqualTo("16384");
    }

    @Test
    void withContext_onWithoutSuggestion_isEmpty() {
        assertThat(ModelDraft.of(DEFAULTS).withContext(true, "", null).contextText())
                .isEmpty();
    }

    @Test
    void withContext_offAfterTyping_clearsTheText() {
        final ModelDraft draft = ModelDraft.of(stored(8192, 256)).withContext(false, "8192", 16384);

        assertThat(draft.contextOn()).isFalse();
        assertThat(draft.contextText()).isEmpty();
    }

    @Test
    void withLimit_onThenOff_clearsTheText() {
        final ModelDraft on = ModelDraft.of(DEFAULTS).withLimit(true, "300");

        assertThat(on.limitText()).isEqualTo("300");
        assertThat(on.withLimit(false, "300").limitText()).isEmpty();
    }

    @Test
    void withTemperature_roundsToAHundredthAndStaysInRange() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS);

        assertThat(draft.withTemperature(0.4549).temperature()).isEqualTo(0.45);
        assertThat(draft.withTemperature(3.0).temperature()).isEqualTo(2.0);
        assertThat(draft.withTemperature(-1.0).temperature()).isEqualTo(0.0);
    }

    @Test
    void withChoice_omitThenValue_keepsTheTemperature() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS)
                .withChoice(TemperatureChoice.VALUE)
                .withTemperature(0.7)
                .withChoice(TemperatureChoice.OMIT)
                .withChoice(TemperatureChoice.VALUE);

        assertThat(draft.temperature()).isEqualTo(0.7);
    }

    @Test
    void findings_contextZero_blocking() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS).withContext(true, "0", null);

        assertThat(draft.findings()).containsExactly(MessageKey.SETTINGS_MODELS_ERROR_NUMBER);
    }

    @Test
    void findings_contextText12k_blocking() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS).withContext(true, "12k", null);

        assertThat(draft.findings()).containsExactly(MessageKey.SETTINGS_MODELS_ERROR_NUMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-3", "1.5", "99999999999"})
    void findings_limitSwitchedOnWithNoUsableNumber_blocking(final String text) {
        final ModelDraft draft = ModelDraft.of(DEFAULTS).withLimit(true, text);

        assertThat(draft.findings()).containsExactly(MessageKey.SETTINGS_MODELS_ERROR_NUMBER);
    }

    @Test
    void findings_wholePositiveNumbersWithSpaces_none() {
        final ModelDraft draft =
                ModelDraft.of(DEFAULTS).withContext(true, " 16384 ", null).withLimit(true, "256");

        assertThat(draft.findings()).isEmpty();
    }

    @Test
    void apply_omit_temperatureNull() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS).withChoice(TemperatureChoice.OMIT);

        final ModelSettings written = saved(draft.apply(DEFAULTS));

        assertThat(written.temperatureChoice()).isEqualTo(TemperatureChoice.OMIT);
        assertThat(written.temperature()).isNull();
    }

    @Test
    void apply_myValue_writesThatValueAndTheNumbers() {
        final ModelDraft draft = ModelDraft.of(DEFAULTS)
                .withChoice(TemperatureChoice.VALUE)
                .withTemperature(0.5)
                .withContext(true, " 16384 ", null)
                .withLimit(true, "256")
                .withField(OutputLengthField.MAX_COMPLETION_TOKENS);

        final ModelSettings written = saved(draft.apply(DEFAULTS));

        assertThat(written.temperature()).isEqualTo(0.5);
        assertThat(written.contextWindow()).isEqualTo(16384);
        assertThat(written.maxOutput()).isEqualTo(256);
        assertThat(written.outputField()).isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void apply_switchesOff_writeNull() {
        final ModelSettings written = saved(ModelDraft.of(stored(16384, 256))
                .withContext(false, "", null)
                .withLimit(false, "")
                .apply(stored(16384, 256)));

        assertThat(written.contextWindow()).isNull();
        assertThat(written.maxOutput()).isNull();
    }

    @Test
    void apply_storedRefusalAndSpeed_stayAsStored() {
        final RefusedSetting refusal = new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "omitted");
        final ModelSettings current = new ModelSettings(
                "ollama",
                "gemma4:e4b",
                TemperatureChoice.CHOOSE,
                null,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(refusal),
                1.5,
                MeasuredFrom.RUN);

        final ModelSettings written =
                saved(ModelDraft.of(DEFAULTS).withChoice(TemperatureChoice.OMIT).apply(current));

        assertThat(written.refused()).containsExactly(refusal);
        assertThat(written.measuredSecondsPerSegment()).isEqualTo(1.5);
    }

    @Test
    void apply_blockingFinding_isAValidationError() {
        final Result<ModelSettings> result =
                ModelDraft.of(DEFAULTS).withContext(true, "0", null).apply(DEFAULTS);

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
    }

    // IF a temperature that does not apply made the form dirty, THEN toggling Choose after a slider move would ask to
    // save.
    @Test
    void equals_valueToggledBack_notDirty() {
        final ModelDraft base = ModelDraft.of(DEFAULTS);
        final ModelDraft toggled =
                base.withChoice(TemperatureChoice.VALUE).withTemperature(0.9).withChoice(TemperatureChoice.CHOOSE);

        assertThat(toggled).isEqualTo(base);
        assertThat(toggled).hasSameHashCodeAs(base);
    }

    @Test
    void equals_valueWithAnotherTemperature_isDifferent() {
        final ModelDraft one = ModelDraft.of(DEFAULTS).withChoice(TemperatureChoice.VALUE);

        assertThat(one.withTemperature(0.9)).isNotEqualTo(one.withTemperature(0.3));
    }
}
