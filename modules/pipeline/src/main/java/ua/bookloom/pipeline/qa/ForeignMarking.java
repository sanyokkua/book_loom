package ua.bookloom.pipeline.qa;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.util.lang.Language;
import ua.bookloom.util.lang.LanguageTags;
import ua.bookloom.util.lang.Languages;

/**
 * Whether a segment counts as a kept foreign passage under {@link ForeignPassagePolicy#KEEP} — the segment's own
 * block declares a different language than the Book Brief's source, or its source display text's dominant script
 * differs from the source language's. A marked segment skips {@link ScriptCheck} and {@link EchoCheck}.
 *
 * <p>Tags are compared after {@link LanguageTags#normalize}, so any language the JDK can name takes part. Only a
 * catalogued source language has a script, so the dominant-script test is skipped for any other.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ForeignMarking {

    /**
     * Whether {@code input}'s segment is a kept foreign passage.
     *
     * @param input the check input
     * @return {@code false} under any policy other than {@link ForeignPassagePolicy#KEEP}, or when the Book
     *     Brief's source language is not a recognized language
     */
    static boolean isMarked(final SoftCheckInput input) {
        if (input.foreignPassagePolicy() != ForeignPassagePolicy.KEEP) {
            return false;
        }
        final Optional<String> source = LanguageTags.normalize(input.sourceLanguage());
        if (source.isEmpty()) {
            log.debug("foreign marking: source language {} not recognized, not marked", input.sourceLanguage());
            return false;
        }
        if (declaredLanguageDiffers(input.declaredLanguage(), source.get())) {
            return true;
        }
        return dominantScriptDiffers(input, source.get());
    }

    private static boolean declaredLanguageDiffers(@Nullable final String declared, final String source) {
        final Optional<String> declaredTag = LanguageTags.normalize(declared);
        final boolean differs = declaredTag.isPresent() && !declaredTag.get().equals(source);
        if (differs) {
            log.debug(
                    "foreign marking: declared language {} differs from source {}, marked", declaredTag.get(), source);
        }
        return differs;
    }

    private static boolean dominantScriptDiffers(final SoftCheckInput input, final String source) {
        final Optional<Language> catalogued = Languages.byTag(source);
        if (catalogued.isEmpty()) {
            log.debug("foreign marking: source {} has no known script, dominant-script test skipped", source);
            return false;
        }
        final boolean differs =
                DominantScript.of(input.sourceDisplayText(), catalogued.get().script())
                        != catalogued.get().script();
        log.debug("foreign marking: dominant script differs from source {}: {}", source, differs);
        return differs;
    }
}
