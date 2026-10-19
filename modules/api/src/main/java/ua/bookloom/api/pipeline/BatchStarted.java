package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that a helper action sending its work in batches, such as the glossary's target suggestions, starts one
 * batch, so a waiting line can say how far it has come ("Suggesting renderings 2/5").
 *
 * @param kind the call the batch is sent as
 * @param batch the batch, counted from one
 * @param batches how many batches the action sends, at least {@code batch}
 */
public record BatchStarted(CallKind kind, int batch, int batches) implements JobEvent {

    /** Rejects an event without a kind or with an impossible batch. */
    public BatchStarted {
        Objects.requireNonNull(kind, "kind");
        if (batch < 1 || batches < batch) {
            throw new IllegalArgumentException("batch " + batch + " of " + batches);
        }
    }
}
