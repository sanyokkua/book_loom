package ua.bookloom.ui.state;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The model advice for a computer's memory: a tag to pull and, from a larger memory, one more tag offered as an
 * option. Pure arithmetic over a figure the caller read; the pull command is only ever shown as text.
 *
 * @param gb the memory rounded to whole gigabytes
 * @param recommended the model tag recommended for this memory
 * @param optInTag the larger model offered as a slower, higher-quality option, or {@code null} below that memory
 */
public record ModelRecommendation(
        int gb, String recommended, @Nullable String optInTag) {

    static final int LARGE_FROM_GB = 24;
    static final int OPT_IN_FROM_GB = 36;
    static final String SMALL = "gemma4:e4b";
    static final String LARGE = "gemma4:26b-a4b";
    static final String OPT_IN = "gemma4:31b";
    private static final int GB_SHIFT = 30;
    private static final long HALF_GB = 1L << (GB_SHIFT - 1);

    /** The advice for this much memory; empty when the memory is unknown (zero or less). */
    public static Optional<ModelRecommendation> of(final long bytes) {
        if (bytes <= 0) {
            return Optional.empty();
        }
        final int gb = (int) ((bytes + HALF_GB) >> GB_SHIFT);
        final String tag = gb >= LARGE_FROM_GB ? LARGE : SMALL;
        return Optional.of(new ModelRecommendation(gb, tag, gb >= OPT_IN_FROM_GB ? OPT_IN : null));
    }

    /** The larger model offered as an option, if any. */
    public Optional<String> optIn() {
        return Optional.ofNullable(optInTag);
    }

    /** The command a person can run themselves to fetch this model; BookLoom never runs it. */
    public static String pullCommand(final String tag) {
        return "ollama pull " + tag;
    }
}
