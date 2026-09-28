package ua.bookloom.pipeline.qa;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.util.lang.Language;
import ua.bookloom.util.lang.LanguageTags;
import ua.bookloom.util.lang.Languages;

/**
 * Whether a segment counts as a kept foreign passage under {@link ForeignPassagePolicy#KEEP} — the segment's own
 * block declares a different language than the Book Brief's source, or its source display text's dominant script
 * differs from the source language's. A marked segment skips {@link ScriptCheck} and {@link EchoCheck}.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ForeignMarking {

    /**
     * Whether {@code input}'s segment is a kept foreign passage.
     *
     * @param input the check input
     * @return {@code false} under any policy other than {@link ForeignPassagePolicy#KEEP}, or when the Book
     *     Brief's source language does not catalogue
     */
    static boolean isMarked(final SoftCheckInput input) {
        if (input.foreignPassagePolicy() != ForeignPassagePolicy.KEEP) {
            return false;
        }
        final Optional<Language> source =
                LanguageTags.normalize(input.sourceLanguage()).flatMap(Languages::byTag);
        if (source.isEmpty()) {
            return false;
        }
        if (declaredLanguageDiffers(input.declaredLanguage(), source.get())) {
            return true;
        }
        return DominantScript.of(input.sourceDisplayText(), source.get().script())
                != source.get().script();
    }

    private static boolean declaredLanguageDiffers(@Nullable final String declared, final Language source) {
        return LanguageTags.normalize(declared)
                .map(tag -> !tag.equals(source.tag()))
                .orElse(false);
    }
}
