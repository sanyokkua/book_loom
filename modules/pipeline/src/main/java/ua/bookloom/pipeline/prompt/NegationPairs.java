package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** Which language pairs compare the number of negation words of a source and its target (design D12). */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NegationPairs {

    /**
     * Whether a pair counts negations: only a close pair, where a lost negation is a lost meaning and not another way
     * of saying the same, says so with {@code negationCounts=true} in its pair file.
     *
     * @param sourceTag the source language tag, or {@code null} when none is declared
     * @param targetTag the non-null target language tag
     * @return {@code true} if the pair file says so and both languages list negation words, {@code false} otherwise
     */
    public static boolean counts(@Nullable final String sourceTag, final String targetTag) {
        Objects.requireNonNull(targetTag, "targetTag");
        if (sourceTag == null || sourceTag.isBlank()) {
            return false;
        }
        final LanguageRules rules = LanguageRules.bundled();
        return "true"
                        .equalsIgnoreCase(String.valueOf(
                                rules.pairFile(sourceTag, targetTag).get("negationCounts")))
                && !rules.negationWords(sourceTag).isEmpty()
                && !rules.negationWords(targetTag).isEmpty();
    }
}
