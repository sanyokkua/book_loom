package ua.bookloom.pipeline.prompt;

import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The user slots of the batch draft, kept out of {@link PromptName} so the enum body stays readable. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchSlots {

    private static final Set<String> CONTEXT = Set.of(
            "summary",
            "glossaryTerms",
            "lockedNames",
            "suggestedTerms",
            "memoryHint",
            "lexiconTerms",
            "characters",
            "relevantPairs",
            "precedingPairs",
            "nextSource",
            "itemTokens",
            "extraInstruction");

    /** The system message's slots, the same in every batch format. */
    static final Slots SYSTEM = new Slots(
            Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"),
            Set.of("examples", "languageRules", "tokenRules", "notesRule", "relatedPairsName"));

    static final Slots USER = new Slots(Set.of("items", "source", "target"), with(CONTEXT, "keyTerms"));

    /** The marked arm has no JSON entries to carry a key term's rendering, so it never asks for one. */
    static final Slots MARKERS_USER = new Slots(Set.of("items", "source", "target"), CONTEXT);

    /** The dual arm shows the passage the items make once as context before the numbered items. */
    static final Slots DUAL_USER = new Slots(Set.of("items", "passage", "source", "target"), with(CONTEXT, "keyTerms"));

    private static Set<String> with(final Set<String> base, final String extra) {
        final Set<String> all = new HashSet<>(base);
        all.add(extra);
        return all;
    }
}
