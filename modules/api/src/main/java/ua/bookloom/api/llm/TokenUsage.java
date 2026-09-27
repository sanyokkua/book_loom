package ua.bookloom.api.llm;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Token counts and generation time a provider reported for one {@link ChatResponse}.
 *
 * @param prompt the number of prompt tokens the provider counted, or null when not reported
 * @param completion the number of completion tokens the provider counted, or null when not reported
 * @param generation the wall-clock time the provider spent generating the completion, or null when not reported
 */
public record TokenUsage(
        @Nullable Integer prompt,
        @Nullable Integer completion,
        @Nullable Duration generation) {

    /**
     * Rejects a negative reported count and a reply that reports nothing at all: a response with no figures
     * carries no {@link TokenUsage}, never an empty one.
     */
    public TokenUsage {
        if (prompt != null && prompt < 0) {
            throw new IllegalArgumentException("prompt must not be negative: " + prompt);
        }
        if (completion != null && completion < 0) {
            throw new IllegalArgumentException("completion must not be negative: " + completion);
        }
        if (prompt == null && completion == null && generation == null) {
            throw new IllegalArgumentException("at least one of prompt, completion or generation must be reported");
        }
    }
}
