package ua.bookloom.llm.engine;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the check of one model id against the server's own list found.
 *
 * @param sentId the id a request carries: the typed id with an LM Studio {@code @variant} removed
 * @param removedVariant the removed suffix without its {@code @}, or null when the typed id named none
 * @param verdict whether the id was checked and found
 * @param listedIds the chat models the server listed, in its order; empty when nothing was read
 * @param loaded whether LM Studio has the model in memory now (its list's {@code state}), or null when the list does
 *     not say; a model that is not loaded is loaded with LM Studio's own settings on the first call
 */
public record CheckedModelId(
        String sentId,
        @Nullable String removedVariant,
        Verdict verdict,
        List<String> listedIds,
        @Nullable Boolean loaded) {

    /** Copies the listed ids so a caller cannot change them later. */
    public CheckedModelId {
        Objects.requireNonNull(sentId, "sentId");
        Objects.requireNonNull(verdict, "verdict");
        listedIds = List.copyOf(Objects.requireNonNull(listedIds, "listedIds"));
    }

    /** What the check could say about the id. */
    public enum Verdict {
        /** The engine refuses an id it does not serve by itself, so no list was read. */
        NOT_CHECKED,
        /** The server's list holds the id. */
        LISTED,
        /** The server's list does not hold the id; no chat request may be sent. */
        NOT_LISTED,
        /** The list could not be read, so the id could not be checked; the call goes on and the server decides. */
        UNREADABLE
    }
}
