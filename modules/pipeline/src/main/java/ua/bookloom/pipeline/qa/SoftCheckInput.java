package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;

/**
 * Everything {@link SoftChecks#run(SoftCheckInput)} needs for one segment, already reduced to display texts
 * ({@link ua.bookloom.pipeline.DisplayText#of(String)} over the masked source and candidate target, so every
 * document and protected token is already gone).
 *
 * @param sourceDisplayText the source's display text
 * @param targetDisplayText the candidate target's display text
 * @param sourceLanguage the Book Brief's source language tag, or {@code null} when the book declares none
 * @param targetLanguage the target language tag
 * @param foreignPassagePolicy the Book Brief's foreign-passage policy
 * @param namePolicy the Book Brief's name policy
 * @param declaredLanguage the segment's declared block language ({@link ua.bookloom.api.document.Segment#declaredLanguage()}), or {@code null} when its block declares none
 * @param glossaryTerms every glossary term, read only for the {@code Keep original} name-removal the script and
 *     echo checks apply before comparing texts
 * @param lockedRenderings the locked glossary terms present in this segment, each with its entered rendering, read
 *     by {@link GlossaryCheck}
 */
public record SoftCheckInput(
        String sourceDisplayText,
        String targetDisplayText,
        @Nullable String sourceLanguage,
        String targetLanguage,
        ForeignPassagePolicy foreignPassagePolicy,
        NamePolicy namePolicy,
        @Nullable String declaredLanguage,
        List<String> glossaryTerms,
        List<LockedRendering> lockedRenderings) {

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies the two list components.
     */
    public SoftCheckInput {
        Objects.requireNonNull(sourceDisplayText, "sourceDisplayText");
        Objects.requireNonNull(targetDisplayText, "targetDisplayText");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(foreignPassagePolicy, "foreignPassagePolicy");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(lockedRenderings, "lockedRenderings");
        glossaryTerms = List.copyOf(glossaryTerms);
        lockedRenderings = List.copyOf(lockedRenderings);
    }
}
