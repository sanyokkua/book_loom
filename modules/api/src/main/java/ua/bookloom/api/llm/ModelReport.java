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
 * @param languages what the model's maker (or its base model's maker) declares, or null when no list is known or the
 *     advice was not added
 * @param quantAdvice how the model's quant rates at its size, or null when the advice was not added
 * @param baseModel the display name of the base model a fine-tune is built on, such as {@code Qwen3}, or null when the
 *     model is not a known fine-tune
 * @param notes the words the Models tab and Start need beyond the identity, or null when the advice was not added
 */
public record ModelReport(
        @Nullable Integer contextLength,
        @Nullable Integer loadedContext,
        @Nullable ModelIdentity identity,
        Match match,
        String verdict,
        List<String> warnings,
        ReasoningControl thinking,
        @Nullable LanguageDeclaration languages,
        @Nullable QuantAdvice quantAdvice,
        @Nullable String baseModel,
        @Nullable ModelNotes notes) {

    /** The verdict of a model no built-in row knows. */
    public static final String UNKNOWN_MODEL = "unknown model";

    /** The verdict of a fine-tune that takes its base model's handling but not its rating. */
    public static final String UNKNOWN_FINE_TUNE = "unknown fine-tune";

    /** The warning key for a model whose licence limits how it may be used. */
    public static final String WARNING_LICENCE_NOTE = "licenceNote";

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
        /** No row of its own: the row of the base model a known fine-tune is built on. */
        BASE_FAMILY,
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
                contextLength,
                loadedContext,
                null,
                Match.DEFAULT,
                UNKNOWN_MODEL,
                List.of(),
                ReasoningControl.UNKNOWN,
                null,
                null,
                null,
                null);
    }

    /**
     * The same report with the quant and language advice added.
     *
     * @param declared what the maker declares, or null when no list is known
     * @param quant how the quant rates, or null when it was not judged
     * @return the report with only those two parts changed
     */
    public ModelReport withAdvice(final @Nullable LanguageDeclaration declared, final @Nullable QuantAdvice quant) {
        return new ModelReport(
                contextLength,
                loadedContext,
                identity,
                match,
                verdict,
                warnings,
                thinking,
                declared,
                quant,
                baseModel,
                notes);
    }

    /**
     * The same report with the model's notes added.
     *
     * @param added the notes; non-null
     * @return the report with only the notes changed
     */
    public ModelReport withNotes(final ModelNotes added) {
        Objects.requireNonNull(added, "added");
        return new ModelReport(
                contextLength,
                loadedContext,
                identity,
                match,
                verdict,
                warnings,
                thinking,
                languages,
                quantAdvice,
                baseModel,
                added);
    }

    private static void requirePositive(final @Nullable Integer value, final String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
