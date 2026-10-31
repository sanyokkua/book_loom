package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The Sampling row of a model's draft: whether the person's own values are sent, and each value as typed, so a
 * half-typed figure is still a draft and only {@link #findings()} judges it. An empty text keeps the profile's value.
 * The typed texts are kept across a switch back to Profile default, and count for equality only while Custom is chosen.
 *
 * @param custom whether Custom is chosen
 * @param temperatureText the Custom temperature as typed; it is the tab's My value
 * @param topKText the Custom {@code top_k} as typed
 * @param topPText the Custom {@code top_p} as typed
 * @param minPText the Custom {@code min_p} as typed
 */
public record SamplingDraft(boolean custom, String temperatureText, String topKText, String topPText, String minPText) {

    /** Profile default with nothing typed. */
    public static final SamplingDraft PROFILE_DEFAULT = new SamplingDraft(false, "", "", "", "");

    public SamplingDraft {
        Objects.requireNonNull(temperatureText, "temperatureText");
        Objects.requireNonNull(topKText, "topKText");
        Objects.requireNonNull(topPText, "topPText");
        Objects.requireNonNull(minPText, "minPText");
    }

    /**
     * The row that shows a stored choice.
     *
     * @param settings what is stored for the model; its My value is the Custom temperature
     * @return the row that equals the stored choice
     */
    public static SamplingDraft of(final ModelSettings settings) {
        final SamplingOverride stored = settings.samplingOverride();
        if (stored.mode() == SamplingOverride.Mode.PROFILE_DEFAULT) {
            return PROFILE_DEFAULT;
        }
        final Double myValue = settings.temperatureChoice() == TemperatureChoice.VALUE ? settings.temperature() : null;
        return new SamplingDraft(true, text(myValue), text(stored.topK()), text(stored.topP()), text(stored.minP()));
    }

    /** The same row with Custom chosen or not; the typed values are kept. */
    public SamplingDraft withCustom(final boolean on) {
        return new SamplingDraft(on, temperatureText, topKText, topPText, minPText);
    }

    /** The same row with another typed temperature. */
    public SamplingDraft withTemperatureText(final String text) {
        return new SamplingDraft(custom, text, topKText, topPText, minPText);
    }

    /** The same row with another typed {@code top_k}. */
    public SamplingDraft withTopKText(final String text) {
        return new SamplingDraft(custom, temperatureText, text, topPText, minPText);
    }

    /** The same row with another typed {@code top_p}. */
    public SamplingDraft withTopPText(final String text) {
        return new SamplingDraft(custom, temperatureText, topKText, text, minPText);
    }

    /** The same row with another typed {@code min_p}. */
    public SamplingDraft withMinPText(final String text) {
        return new SamplingDraft(custom, temperatureText, topKText, topPText, text);
    }

    /**
     * What stops a save.
     *
     * @return one message per typed Custom value outside its range; empty under Profile default or when fine
     */
    public List<MessageKey> findings() {
        final List<MessageKey> found = new ArrayList<>();
        if (!custom) {
            return found;
        }
        if (isBad(temperatureText, typedTemperature())) {
            found.add(MessageKey.SETTINGS_MODELS_ERROR_SAMPLING_TEMPERATURE);
        }
        if (isBad(topKText, whole(topKText).filter(k -> k > 0))) {
            found.add(MessageKey.SETTINGS_MODELS_ERROR_SAMPLING_TOP_K);
        }
        if (isBad(topPText, number(topPText).filter(p -> p > 0.0 && p <= 1.0))) {
            found.add(MessageKey.SETTINGS_MODELS_ERROR_SAMPLING_TOP_P);
        }
        if (isBad(minPText, number(minPText).filter(p -> p >= 0.0 && p <= 1.0))) {
            found.add(MessageKey.SETTINGS_MODELS_ERROR_SAMPLING_MIN_P);
        }
        return List.copyOf(found);
    }

    /**
     * The typed temperature, when it can be the tab's My value.
     *
     * @return the temperature if one is typed and within 0 to 2, or empty if not
     */
    public Optional<Double> typedTemperature() {
        return number(temperatureText).filter(t -> t >= 0.0 && t <= ModelSettings.MAX_TEMPERATURE);
    }

    /**
     * The choice to store; call it only while {@link #findings()} is empty.
     *
     * @param temperature the tab's My value, or null when the temperature is not the person's own
     * @return Profile default, or Custom with each typed value and null for each empty one
     */
    public SamplingOverride override(final @Nullable Double temperature) {
        if (!custom) {
            return SamplingOverride.profileDefault();
        }
        return SamplingOverride.custom(
                temperature,
                whole(topKText).orElse(null),
                number(topPText).orElse(null),
                number(minPText).orElse(null));
    }

    // The typed texts only count while they are what is sent.
    @Override
    public boolean equals(final @Nullable Object other) {
        return other instanceof SamplingDraft that
                && custom == that.custom
                && (!custom
                        || (temperatureText.equals(that.temperatureText)
                                && topKText.equals(that.topKText)
                                && topPText.equals(that.topPText)
                                && minPText.equals(that.minPText)));
    }

    @Override
    public int hashCode() {
        return custom ? Objects.hash(true, temperatureText, topKText, topPText, minPText) : Boolean.hashCode(false);
    }

    private static boolean isBad(final String text, final Optional<?> valid) {
        return !text.isBlank() && valid.isEmpty();
    }

    private static Optional<Double> number(final String text) {
        try {
            return text.isBlank() ? Optional.empty() : Optional.of(Double.parseDouble(text.strip()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> whole(final String text) {
        try {
            return text.isBlank() ? Optional.empty() : Optional.of(Integer.parseInt(text.strip()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static String text(final @Nullable Number value) {
        return value == null ? "" : String.valueOf(value);
    }
}
