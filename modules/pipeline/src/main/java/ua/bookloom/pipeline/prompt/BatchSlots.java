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
            "precedingPairs",
            "nextSource",
            "itemTokens",
            "extraInstruction");

    /** The system message's slots. */
    static final Slots SYSTEM = new Slots(
            Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"),
            Set.of("examples", "languageRules", "tokenRules", "noTokenRules"));

    static final Slots USER = new Slots(Set.of("items", "source", "target"), with(CONTEXT, "keyTerms"));

    private static Set<String> with(final Set<String> base, final String extra) {
        final Set<String> all = new HashSet<>(base);
        all.add(extra);
        return all;
    }
}
