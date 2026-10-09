package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Why one suggested brief field holds its value: the book's own words the model quoted for it, each found in the sample
 * it read, and how many of the samples asked gave the same answer. A field is kept when every sample agrees, or when the
 * code verified its quote itself (the narrator's gender from a name or a word of address), which one sample is enough
 * for; any other field stays neutral or unknown and is shown as uncertain.
 *
 * @param quotes the quotes the agreeing samples gave, each checked to exist in its sample; never null, empty for a
 *     neutral value, which needs no quote, and for a field that was not kept
 * @param agreeing how many samples gave the kept answer, or the most that gave any one answer when they did not agree;
 *     from zero to {@code samples}
 * @param samples how many disjoint samples of the book were asked; at least one
 */
public record FieldEvidence(List<String> quotes, int agreeing, int samples) {

    /** Copies the quotes and rejects counts that cannot hold together. */
    public FieldEvidence {
        Objects.requireNonNull(quotes, "quotes");
        quotes = List.copyOf(quotes);
        if (samples < 1 || agreeing < 0 || agreeing > samples) {
            throw new IllegalArgumentException("agreeing " + agreeing + " of " + samples + " samples");
        }
    }

    /**
     * Whether the field was kept.
     *
     * @return {@code true} if every sample gave the same answer, {@code false} if the field is uncertain
     */
    public boolean isAgreed() {
        return agreeing == samples;
    }

    /**
     * Whether the field holds the suggested value rather than the neutral one it falls back to.
     *
     * @return {@code true} if every sample agreed or a verified quote stands behind the value, {@code false} if the
     *     field is uncertain
     */
    public boolean isKept() {
        return isAgreed() || !quotes.isEmpty();
    }
}
