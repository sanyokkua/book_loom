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
 * @param hosted whether the model runs on someone else's hardware, where a wide window costs the person no memory, so
 *     the window is the model's own and not the default window
 */
public record ContextPolicy(
        int hardCap,
        int outputMax,
        @Nullable Integer detected,
        @Nullable Integer loaded,
        boolean hosted) {

    /** A policy for a model on the person's own computer. */
    public ContextPolicy(
            final int hardCap, final int outputMax, final @Nullable Integer detected, final @Nullable Integer loaded) {
        this(hardCap, outputMax, detected, loaded, false);
    }

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

    /**
     * The same policy with the window the caller read from the provider.
     *
     * @param window the window the provider reported; positive
     * @return the policy with that detected window
     */
    public ContextPolicy withDetected(final int window) {
        return new ContextPolicy(hardCap, outputMax, window, loaded, hosted);
    }
}
