package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the person set for one model of one provider; every field left off means the safe default.
 *
 * @param providerId the provider's id; non-null
 * @param modelId the model's id; non-null
 * @param temperatureChoice how the temperature is sent; non-null
 * @param temperature the fixed temperature, 0.0 to 2.0; required exactly when the choice is {@code VALUE}
 * @param contextWindow the context window in tokens, or null to use the detected one; positive when set
 * @param maxOutput the answer-length cap in tokens, or null for the computed one; positive when set
 * @param outputField the field that carries the cap; non-null
 * @param refused the settings the model refused; defensively copied
 * @param measuredSecondsPerSegment the speed the inference test measured, or null when it never ran
 */
public record ModelSettings(
        String providerId,
        String modelId,
        TemperatureChoice temperatureChoice,
        @Nullable Double temperature,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutput,
        OutputLengthField outputField,
        List<RefusedSetting> refused,
        @Nullable Double measuredSecondsPerSegment) {

    /** The lowest temperature a person may fix. */
    public static final double MIN_TEMPERATURE = 0.0;

    /** The highest temperature a person may fix. */
    public static final double MAX_TEMPERATURE = 2.0;

    public ModelSettings {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(temperatureChoice, "temperatureChoice");
        Objects.requireNonNull(outputField, "outputField");
        refused = List.copyOf(Objects.requireNonNull(refused, "refused"));
        requireTemperatureMatchesChoice(temperatureChoice, temperature);
        requirePositive(contextWindow, "contextWindow");
        requirePositive(maxOutput, "maxOutput");
    }

    /**
     * Builds the settings of a model nobody has changed.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model's id; non-null
     * @return the temperature chosen by BookLoom, no limits, {@code max_tokens}, nothing refused or measured
     */
    public static ModelSettings defaults(final String providerId, final String modelId) {
        return new ModelSettings(
                providerId,
                modelId,
                TemperatureChoice.CHOOSE,
                null,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(),
                null);
    }

    private static void requireTemperatureMatchesChoice(
            final TemperatureChoice choice, final @Nullable Double temperature) {
        if (choice == TemperatureChoice.VALUE) {
            if (temperature == null || temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
                throw new IllegalArgumentException("temperature must be 0.0 to 2.0 for VALUE: " + temperature);
            }
        } else if (temperature != null) {
            throw new IllegalArgumentException("temperature is only set for VALUE, not " + choice);
        }
    }

    private static void requirePositive(final @Nullable Integer value, final String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
