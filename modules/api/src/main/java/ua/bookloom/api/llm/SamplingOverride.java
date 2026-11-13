package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the person chose on Settings › Models for one model's samplers; stored on its {@link ModelSettings}. There is no
 * "server defaults" choice: where an engine's own defaults are not neutral BookLoom states its samplers explicitly, and
 * such a choice would contradict that.
 *
 * @param mode whether the profile's samplers or the person's own are sent; non-null
 * @param temperature the person's temperature, 0.0 to 2.0, or null to keep the profile's; only set for
 *     {@link Mode#CUSTOM}
 * @param topK the person's {@code top_k}, positive, or null to keep the profile's; only set for {@link Mode#CUSTOM}
 * @param topP the person's {@code top_p} in (0, 1], or null to keep the profile's; only set for {@link Mode#CUSTOM}
 * @param minP the person's {@code min_p} in [0, 1], or null to keep the profile's; only set for {@link Mode#CUSTOM}
 */
public record SamplingOverride(
        Mode mode,
        @Nullable Double temperature,
        @Nullable Integer topK,
        @Nullable Double topP,
        @Nullable Double minP) {

    private static final double MAX_TEMPERATURE = 2.0;

    /** Where the samplers of a call come from. */
    public enum Mode {
        /** BookLoom's own temperature per call kind and the profile's samplers. */
        PROFILE_DEFAULT,
        /** The values the person typed; a value left empty keeps the profile's. */
        CUSTOM
    }

    public SamplingOverride {
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.PROFILE_DEFAULT && (temperature != null || topK != null || topP != null || minP != null)) {
            throw new IllegalArgumentException("the profile's samplers carry no values of the person's own");
        }
        requireIn(temperature, 0.0, MAX_TEMPERATURE, "temperature");
        requireIn(minP, 0.0, 1.0, "minP");
        requireIn(topP, Double.MIN_VALUE, 1.0, "topP");
        if (topK != null && topK <= 0) {
            throw new IllegalArgumentException("topK must be positive: " + topK);
        }
    }

    /**
     * The samplers nobody changed.
     *
     * @return the profile's samplers, with no value of the person's own
     */
    public static SamplingOverride profileDefault() {
        return new SamplingOverride(Mode.PROFILE_DEFAULT, null, null, null, null);
    }

    /**
     * The person's own samplers.
     *
     * @param temperature 0.0 to 2.0, or null to keep the profile's
     * @param topK positive, or null to keep the profile's
     * @param topP in (0, 1], or null to keep the profile's
     * @param minP in [0, 1], or null to keep the profile's
     * @return a {@link Mode#CUSTOM} choice holding them
     */
    public static SamplingOverride custom(
            final @Nullable Double temperature,
            final @Nullable Integer topK,
            final @Nullable Double topP,
            final @Nullable Double minP) {
        return new SamplingOverride(Mode.CUSTOM, temperature, topK, topP, minP);
    }

    private static void requireIn(
            final @Nullable Double value, final double low, final double high, final String name) {
        if (value != null && (value < low || value > high)) {
            throw new IllegalArgumentException(name + " must be " + low + " to " + high + ": " + value);
        }
    }
}
