package ua.bookloom.pipeline.checks;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.UnitRole;

/**
 * Everything {@link TextChecks#run(TextCheckInput)} reads about one segment.
 *
 * @param source the source's display text
 * @param target the candidate's display text, with kept names and protected tokens already out
 * @param sourceLanguage the source language tag, or {@code null} when none is declared
 * @param targetLanguage the target language tag
 * @param glossaryPairs {@code term → target} lines of the glossary names the segment holds
 * @param keptTerms the terms Keep original keeps as written, which the Latin-run check leaves alone
 * @param role the role of the unit the segment belongs to; the Latin-run check applies to {@link UnitRole#BODY} only
 * @param pinnedTargets the renderings of the pinned glossary entries: a form the language's table lists for one is no
 *     spelling error, and a name-spelling finding on one blocks; on any other rendering it is a note
 */
public record TextCheckInput(
        String source,
        String target,
        @Nullable String sourceLanguage,
        String targetLanguage,
        List<String> glossaryPairs,
        List<String> keptTerms,
        UnitRole role,
        List<String> pinnedTargets) {

    public TextCheckInput {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(glossaryPairs, "glossaryPairs");
        Objects.requireNonNull(keptTerms, "keptTerms");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(pinnedTargets, "pinnedTargets");
    }
}
