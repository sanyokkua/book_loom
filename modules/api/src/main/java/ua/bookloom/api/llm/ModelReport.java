package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What BookLoom knows about one model, for the person to read: the server's windows and the half of the model's
 * handling a request never needs. A request reads the {@link ModelProfile} instead.
 *
 * @param contextLength the tokens the model can read at once, or null when the provider did not say; positive when set
 * @param loadedContext the tokens it is loaded with now, or null when unknown or not loaded; positive when set
 * @param identity who the model is as its name and its server's metadata say, or null when the name was not read
 * @param match how the model's handling was found; non-null
 * @param verdict how BookLoom rates the model for translation, such as {@value #UNKNOWN_MODEL}; non-null
 * @param warnings what the person should know before choosing the model; never null, may be empty
 * @param thinking the thinking control the model offers, as its profile resolved it, so the Models tab can say what
 *     the model offers and whether the person's thinking choice can reach it; non-null
 */
public record ModelReport(
        @Nullable Integer contextLength,
        @Nullable Integer loadedContext,
        @Nullable ModelIdentity identity,
        Match match,
        String verdict,
        List<String> warnings,
        ReasoningControl thinking) {

    /** The verdict of a model no built-in row knows. */
    public static final String UNKNOWN_MODEL = "unknown model";

    /** How a model's handling was found, from the most specific to none at all. */
    public enum Match {
        /** The person's own choice on Settings › Models decides it. */
        OVERRIDE,
        /** A built-in row names this exact model. */
        EXACT,
        /** A row of the model's family names its size. */
        SIZE,
        /** A row names the model's family and version. */
        FAMILY_VERSION,
        /** A row names the model's family. */
        FAMILY,
        /** A row was found by the architecture the server reports. */
        ARCHITECTURE,
        /** No row: the default profile. */
        DEFAULT
    }

    public ModelReport {
        requirePositive(contextLength, "contextLength");
        requirePositive(loadedContext, "loadedContext");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(verdict, "verdict");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        Objects.requireNonNull(thinking, "thinking");
    }

    /**
     * A report that holds only the server's windows, for a capability that does not read model names.
     *
     * @param contextLength the readable window, or null; positive when set
     * @param loadedContext the loaded window, or null; positive when set
     * @return the report with no identity, the default match, the verdict {@value #UNKNOWN_MODEL} and an unknown
     *     thinking control
     */
    public static ModelReport windowsOnly(
            final @Nullable Integer contextLength, final @Nullable Integer loadedContext) {
        return new ModelReport(
                contextLength, loadedContext, null, Match.DEFAULT, UNKNOWN_MODEL, List.of(), ReasoningControl.UNKNOWN);
    }

    private static void requirePositive(final @Nullable Integer value, final String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
