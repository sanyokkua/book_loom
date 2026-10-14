package ua.bookloom.pipeline.chunk;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.checks.Words;

/**
 * The 15i.X3 experiment arm: a paragraph of more than {@value #LONG_ABOVE_WORDS} words is drafted as sentence groups of
 * at most {@value #GROUP_WORDS} words, each call showing the whole paragraph as context, instead of as one call. It is
 * off until a measurement says it earns its calls. {@code BOOKLOOM_LONG_PARAGRAPHS} (else the
 * {@code bookloom.long.paragraphs} property) names {@code groups} or {@code off}; the eval sets the property from
 * {@code BOOKLOOM_EVAL_LONG_PARAGRAPHS}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LongParagraphs {

    /** What a run uses when nothing is set: off, as the arm is unmeasured. */
    public static final boolean DEFAULT = false;

    /** The environment variable that names the setting; it wins over the property. */
    public static final String ENV_NAME = "BOOKLOOM_LONG_PARAGRAPHS";

    /** The system property that names the setting. */
    public static final String PROPERTY_NAME = "bookloom.long.paragraphs";

    /** A paragraph with more words than this is drafted as groups. */
    public static final int LONG_ABOVE_WORDS = 180;

    /** The most words one group holds, unless one sentence alone has more. */
    public static final int GROUP_WORDS = 90;

    private static final String GROUPS = "groups";
    private static final String OFF = "off";

    /**
     * Whether this process drafts long paragraphs as groups, read from the real environment and system properties.
     *
     * @return {@code true} if it does, {@code false} otherwise
     */
    public static boolean isOn() {
        return resolve(System.getenv(), System.getProperty(PROPERTY_NAME));
    }

    /**
     * Resolves the switch from an environment and a property value.
     *
     * @param env the environment variables, whose value wins over the property; never null
     * @param property the {@code bookloom.long.paragraphs} value, or null when unset
     * @return the named setting; {@link #DEFAULT} when neither names one or the name is neither {@code groups} nor
     *     {@code off}
     */
    static boolean resolve(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        final String asked = env.getOrDefault(ENV_NAME, property == null ? "" : property)
                .strip()
                .toLowerCase(Locale.ROOT);
        return switch (asked) {
            case "" -> DEFAULT;
            case GROUPS -> true;
            case OFF -> false;
            default -> {
                log.warn("Unknown long-paragraphs setting '{}'; using {}", asked, DEFAULT ? GROUPS : OFF);
                yield DEFAULT;
            }
        };
    }

    /**
     * How many words a text writes, leaving out the placeholder tokens, whose letter is no word.
     *
     * @param text the text, masked or not; never null
     * @return the word count, zero for none
     */
    public static int words(final String text) {
        return Words.count(Tokens.replace(Objects.requireNonNull(text, "text"), ""));
    }

    /**
     * Whether a shown text is a paragraph the arm drafts as groups.
     *
     * @param shownText the text the model is shown for a segment; never null
     * @return {@code true} if it has more than {@value #LONG_ABOVE_WORDS} words, {@code false} otherwise
     */
    public static boolean isLong(final String shownText) {
        return words(shownText) > LONG_ABOVE_WORDS;
    }

    /**
     * Whether a segment must stay out of a batch and be drafted on its own: the arm is on and the text is long.
     *
     * @param shownText the text the model would be shown for the segment; never null
     * @return {@code true} if the arm is on and the text is long, {@code false} otherwise
     */
    public static boolean isDraftedAlone(final String shownText) {
        return isOn() && isLong(shownText);
    }
}
