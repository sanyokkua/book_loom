package ua.bookloom.pipeline.prompt;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.prompt.PromptName.Slots;

/** The user slots of the batch draft, kept out of {@link PromptName} so the enum body stays readable. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchSlots {

    static final Slots USER = new Slots(
            Set.of("items", "source", "target"),
            Set.of(
                    "summary",
                    "glossaryTerms",
                    "lockedNames",
                    "suggestedTerms",
                    "memoryHint",
                    "lexiconTerms",
                    "keyTerms",
                    "characters",
                    "precedingPairs",
                    "nextSource",
                    "itemTokens",
                    "extraInstruction"));
}
