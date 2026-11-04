package ua.bookloom.pipeline.context;

import java.util.Objects;

/**
 * One book rule as the fixed part states it.
 *
 * @param rule which rule
 * @param text its wording from the target language's file, else the generic file; never blank
 */
public record RuleLine(BookRule rule, String text) {

    /** Rejects a missing part. */
    public RuleLine {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(text, "text");
    }
}
