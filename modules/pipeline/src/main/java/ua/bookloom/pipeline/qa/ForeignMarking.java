package ua.bookloom.pipeline.qa;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.DisplayText;
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
public final class ForeignMarking {

    /**
     * Whether {@code input}'s segment is a kept foreign passage.
     *
     * @param input the check input
     * @return {@code false} under any policy other than {@link ForeignPassagePolicy#KEEP}, or when the Book
     *     Brief's source language is not a recognized language
     */
    static boolean isMarked(final SoftCheckInput input) {
        return isMarked(
                input.foreignPassagePolicy(),
                input.sourceLanguage(),
                input.declaredLanguage(),
                input.sourceDisplayText());
    }

    /**
     * Whether a segment counts as a kept foreign passage — the same rule the soft checks apply, asked of a stored
     * segment by the review queue's {@code foreign · kept} filter.
     *
     * @param segment the non-null segment; its declared language and its masked text are read
     * @param sourceLanguage the Book Brief's source language tag, or {@code null} when none is known
     * @param policy the non-null foreign-passage policy
     * @return {@code false} under any policy other than {@link ForeignPassagePolicy#KEEP}, or when the source
     *     language is not a recognized language
     */
    public static boolean isMarked(
            final Segment segment, @Nullable final String sourceLanguage, final ForeignPassagePolicy policy) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(policy, "policy");
        return isMarked(policy, sourceLanguage, segment.declaredLanguage(), DisplayText.of(segment.masked()));
    }

    private static boolean isMarked(
            final ForeignPassagePolicy policy,
            @Nullable final String sourceLanguage,
            @Nullable final String declaredLanguage,
            final String sourceDisplayText) {
        if (policy != ForeignPassagePolicy.KEEP) {
            return false;
        }
        final Optional<String> source = LanguageTags.normalize(sourceLanguage);
        if (source.isEmpty()) {
            log.debug("foreign marking: source language {} not recognized, not marked", sourceLanguage);
            return false;
        }
        if (declaredLanguageDiffers(declaredLanguage, source.get())) {
            return true;
        }
        return dominantScriptDiffers(sourceDisplayText, source.get());
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

    private static boolean dominantScriptDiffers(final String sourceDisplayText, final String source) {
        final Optional<Language> catalogued = Languages.byTag(source);
        if (catalogued.isEmpty()) {
            log.debug("foreign marking: source {} has no known script, dominant-script test skipped", source);
            return false;
        }
        final boolean differs =
                DominantScript.of(sourceDisplayText, catalogued.get().script())
                        != catalogued.get().script();
        log.debug("foreign marking: dominant script differs from source {}: {}", source, differs);
        return differs;
    }
}
