package ua.bookloom.api.llm;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;

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
 * @param secondsPerCall the measured speed of each call kind that was measured; defensively copied, empty when never
 *     measured
 * @param probeVerdicts what the advanced check found; non-null, with null parts for what was never checked
 * @param thinkingOverride the person's thinking choice for the model; non-null
 * @param samplingOverride the person's sampler choice for the model; non-null
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
        Map<CallKind, MeasuredSpeed> secondsPerCall,
        ProbeVerdicts probeVerdicts,
        ThinkingOverride thinkingOverride,
        SamplingOverride samplingOverride) {

    /** The lowest temperature a person may fix. */
    public static final double MIN_TEMPERATURE = 0.0;

    /** The highest temperature a person may fix. */
    public static final double MAX_TEMPERATURE = 2.0;

    public ModelSettings {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(temperatureChoice, "temperatureChoice");
        Objects.requireNonNull(outputField, "outputField");
        Objects.requireNonNull(probeVerdicts, "probeVerdicts");
        Objects.requireNonNull(thinkingOverride, "thinkingOverride");
        Objects.requireNonNull(samplingOverride, "samplingOverride");
        refused = List.copyOf(Objects.requireNonNull(refused, "refused"));
        requireTemperatureMatchesChoice(temperatureChoice, temperature);
        requirePositive(contextWindow, "contextWindow");
        requirePositive(maxOutput, "maxOutput");
        secondsPerCall = Map.copyOf(Objects.requireNonNull(secondsPerCall, "secondsPerCall"));
    }

    /**
     * Builds the settings of a model nobody has changed.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model's id; non-null
     * @return the temperature chosen by BookLoom, no limits, {@code max_tokens}, nothing refused or measured, and the
     *     profile's thinking and samplers
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
                Map.of(),
                ProbeVerdicts.none(),
                ThinkingOverride.RECOMMENDED,
                SamplingOverride.profileDefault());
    }

    /**
     * Keeps everything but one call kind's measured speed.
     *
     * @param kind the call kind measured; non-null
     * @param seconds the new speed, positive
     * @param from where it was measured; non-null
     * @return a copy holding the new speed for that kind, the other kinds' speeds kept
     */
    public ModelSettings withMeasuredSpeed(final CallKind kind, final double seconds, final MeasuredFrom from) {
        Objects.requireNonNull(kind, "kind");
        final Map<CallKind, MeasuredSpeed> next = new EnumMap<>(CallKind.class);
        next.putAll(secondsPerCall);
        next.put(kind, new MeasuredSpeed(seconds, from));
        return new ModelSettings(
                providerId,
                modelId,
                temperatureChoice,
                temperature,
                contextWindow,
                maxOutput,
                outputField,
                refused,
                next,
                probeVerdicts,
                thinkingOverride,
                samplingOverride);
    }

    /**
     * Reads one call kind's measured speed.
     *
     * @param kind the call kind; non-null
     * @return the speed if it was measured, or empty if not
     */
    public Optional<MeasuredSpeed> speedOf(final CallKind kind) {
        return Optional.ofNullable(secondsPerCall.get(Objects.requireNonNull(kind, "kind")));
    }

    /**
     * Replaces what the person edits and keeps everything else.
     *
     * @param choice how the temperature is sent; non-null
     * @param value the fixed temperature, required exactly for {@code VALUE}
     * @param context the context window, or null; positive when set
     * @param output the answer-length cap, or null; positive when set
     * @param field the field that carries the cap; non-null
     * @return a copy with the new choices and the same ids, refusals and measured speed
     */
    public ModelSettings withChoices(
            final TemperatureChoice choice,
            final @Nullable Double value,
            final @Nullable Integer context,
            final @Nullable Integer output,
            final OutputLengthField field) {
        return new ModelSettings(
                providerId,
                modelId,
                choice,
                value,
                context,
                output,
                field,
                refused,
                secondsPerCall,
                probeVerdicts,
                thinkingOverride,
                samplingOverride);
    }

    /**
     * Keeps everything but the check verdicts.
     *
     * @param verdicts the verdicts to keep; non-null
     * @return a copy holding them
     */
    public ModelSettings withProbeVerdicts(final ProbeVerdicts verdicts) {
        Objects.requireNonNull(verdicts, "verdicts");
        return new ModelSettings(
                providerId,
                modelId,
                temperatureChoice,
                temperature,
                contextWindow,
                maxOutput,
                outputField,
                refused,
                secondsPerCall,
                verdicts,
                thinkingOverride,
                samplingOverride);
    }

    /**
     * Forgets one refusal.
     *
     * @param name the refused setting's name; non-null
     * @return a copy without it, or an equal record when it was not listed
     */
    public ModelSettings withoutRefusal(final String name) {
        Objects.requireNonNull(name, "name");
        return withRefused(refused.stream().filter(r -> !r.name().equals(name)).toList());
    }

    /**
     * Forgets one refusal of one scope, leaving the same setting's refusals of other scopes listed.
     *
     * @param name the refused setting's name; non-null
     * @param scope the call kind the refusal covers, or null for the one that covers the whole model
     * @return a copy without it, or an equal record when it was not listed
     */
    public ModelSettings withoutRefusal(final String name, final @Nullable String scope) {
        Objects.requireNonNull(name, "name");
        return withRefused(refused.stream()
                .filter(r -> !(r.name().equals(name) && Objects.equals(r.scope(), scope)))
                .toList());
    }

    /**
     * Keeps everything but the refused settings.
     *
     * @param rows the refusals to keep; non-null
     * @return a copy holding them
     */
    public ModelSettings withRefused(final List<RefusedSetting> rows) {
        Objects.requireNonNull(rows, "rows");
        return new ModelSettings(
                providerId,
                modelId,
                temperatureChoice,
                temperature,
                contextWindow,
                maxOutput,
                outputField,
                rows,
                secondsPerCall,
                probeVerdicts,
                thinkingOverride,
                samplingOverride);
    }

    /**
     * Keeps everything but the person's thinking and sampler choices.
     *
     * @param thinking the thinking choice; non-null
     * @param sampling the sampler choice; non-null
     * @return a copy holding them
     */
    public ModelSettings withOverrides(final ThinkingOverride thinking, final SamplingOverride sampling) {
        Objects.requireNonNull(thinking, "thinking");
        Objects.requireNonNull(sampling, "sampling");
        return new ModelSettings(
                providerId,
                modelId,
                temperatureChoice,
                temperature,
                contextWindow,
                maxOutput,
                outputField,
                refused,
                secondsPerCall,
                probeVerdicts,
                thinking,
                sampling);
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
