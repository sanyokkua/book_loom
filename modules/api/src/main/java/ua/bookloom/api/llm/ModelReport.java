package ua.bookloom.api.llm;

import org.jspecify.annotations.Nullable;

/**
 * What a provider says about one model, for the Settings screen.
 *
 * @param contextLength the tokens the model can read at once, or null when the provider did not say; positive when set
 * @param loadedContext the tokens it is loaded with now, or null when unknown or not loaded; positive when set
 */
public record ModelReport(
        @Nullable Integer contextLength, @Nullable Integer loadedContext) {

    public ModelReport {
        requirePositive(contextLength, "contextLength");
        requirePositive(loadedContext, "loadedContext");
    }

    private static void requirePositive(final @Nullable Integer value, final String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
