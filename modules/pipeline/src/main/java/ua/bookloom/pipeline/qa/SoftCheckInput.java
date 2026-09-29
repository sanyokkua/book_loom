package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;

/**
 * Everything {@link SoftChecks#run(SoftCheckInput)} needs for one segment, already reduced to display texts
 * ({@link ua.bookloom.pipeline.DisplayText#of(String)}, so every document and protected token is already gone).
 *
 * @param sourceDisplayText the source's display text
 * @param targetDisplayText the candidate reply's display text, protected tokens stripped, so a locked name or a kept
 *     foreign phrase never counts toward the script, echo or length checks
 * @param targetWithRenderings the display text of the masked form — protected spans put back — where
 *     {@link GlossaryCheck} looks for each locked rendering
 * @param sourceLanguage the Book Brief's source language tag, or {@code null} when the book declares none
 * @param targetLanguage the target language tag
 * @param foreignPassagePolicy the Book Brief's foreign-passage policy
 * @param namePolicy the Book Brief's name policy
 * @param declaredLanguage the segment's declared block language ({@link ua.bookloom.api.document.Segment#declaredLanguage()}), or {@code null} when its block declares none
 * @param glossaryTerms every glossary term, read only for the {@code Keep original} name-removal the script and
 *     echo checks apply before comparing texts
 * @param lockedRenderings the locked glossary terms present in this segment, each with its entered rendering, read
 *     by {@link GlossaryCheck}; a kept foreign run is not listed, its own hard gate covers it
 */
public record SoftCheckInput(
        String sourceDisplayText,
        String targetDisplayText,
        String targetWithRenderings,
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
        Objects.requireNonNull(targetWithRenderings, "targetWithRenderings");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        Objects.requireNonNull(foreignPassagePolicy, "foreignPassagePolicy");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(lockedRenderings, "lockedRenderings");
        glossaryTerms = List.copyOf(glossaryTerms);
        lockedRenderings = List.copyOf(lockedRenderings);
    }
}
