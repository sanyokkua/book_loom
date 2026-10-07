package ua.bookloom.pipeline.checks;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** The sentence-count test the batch item check shares with the text check: one rule, one place. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SentenceCount {

    /**
     * Whether a target has fewer sentences than the source's significant ones.
     *
     * @param source the non-null source display text
     * @param target the non-null target display text
     * @param sourceLanguage the source language tag, or null when unknown
     * @param targetLanguage the non-null target language tag; a script whose sentences cannot be counted is never judged
     * @return {@code true} when a sentence of the source has no counterpart; {@code false} otherwise
     */
    public static boolean dropsSentence(
            final String source,
            final String target,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        return SentenceCountCheck.find(source, target, sourceLanguage, targetLanguage)
                .isPresent();
    }
}
