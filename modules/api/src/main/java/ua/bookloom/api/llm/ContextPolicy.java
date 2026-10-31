package ua.bookloom.api.llm;

import org.jspecify.annotations.Nullable;

/**
 * The window facts a request is sized by. The server's figures reach {@code :api} only here; they are read for the
 * session and never stored.
 *
 * @param hardCap the most tokens the model handles well, whatever the server allows; positive
 * @param outputMax the longest answer BookLoom asks of the model, in tokens; positive
 * @param detected the window the server reports for the model, or null when it did not say; positive when set
 * @param loaded the window the model is loaded with now, or null when unknown or not loaded; positive when set
 */
public record ContextPolicy(
        int hardCap,
        int outputMax,
        @Nullable Integer detected,
        @Nullable Integer loaded) {

    public ContextPolicy {
        requirePositive(hardCap, "hardCap");
        requirePositive(outputMax, "outputMax");
        if (detected != null) {
            requirePositive(detected, "detected");
        }
        if (loaded != null) {
            requirePositive(loaded, "loaded");
        }
    }

    private static void requirePositive(final int value, final String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
