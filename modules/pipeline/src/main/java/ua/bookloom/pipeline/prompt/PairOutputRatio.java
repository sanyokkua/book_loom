package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import java.util.OptionalDouble;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The output tokens per source token a language pair's file states under {@code outputRatio}, which sizes the output
 * reserve of a translate-family call (design D8): a key of the pair's file, never a branch in code.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PairOutputRatio {

    private static final String KEY = "outputRatio";

    /**
     * The pair's ratio.
     *
     * @param rules the non-null language rules the pair file is read from
     * @param sourceTag the source language tag, or null when it is inferred, which has no pair file
     * @param targetTag the non-null target language tag
     * @return the ratio if the pair's file states a positive number, or empty if it states none or no number
     */
    public static OptionalDouble of(
            final LanguageRules rules, @Nullable final String sourceTag, final String targetTag) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(targetTag, "targetTag");
        final @Nullable String value =
                sourceTag == null ? null : rules.pairFile(sourceTag, targetTag).get(KEY);
        if (value == null || value.isBlank()) {
            log.debug("Pair {}->{} states no output ratio", sourceTag, targetTag);
            return OptionalDouble.empty();
        }
        try {
            final double ratio = Double.parseDouble(value.strip());
            if (ratio > 0) {
                log.debug("Pair {}->{} output ratio {}", sourceTag, targetTag, ratio);
                return OptionalDouble.of(ratio);
            }
        } catch (NumberFormatException _) {
            // Named in the WARN below with the value.
        }
        log.warn("Pair {}->{} has an output ratio that is not a positive number: {}", sourceTag, targetTag, value);
        return OptionalDouble.empty();
    }
}
