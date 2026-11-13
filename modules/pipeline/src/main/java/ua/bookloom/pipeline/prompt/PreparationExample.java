package ua.bookloom.pipeline.prompt;

import java.util.Objects;

/**
 * The worked examples a names-pass prompt shows, from one language file.
 *
 * @param examples the example lines, one per line, each from a different invented book
 * @param reply the valid reply the prompt ends with
 */
public record PreparationExample(String examples, String reply) {

    /** Rejects a missing part. */
    public PreparationExample {
        Objects.requireNonNull(examples, "examples");
        Objects.requireNonNull(reply, "reply");
    }
}
