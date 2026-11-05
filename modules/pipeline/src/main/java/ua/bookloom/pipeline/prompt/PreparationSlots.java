package ua.bookloom.pipeline.prompt;

import static ua.bookloom.pipeline.prompt.SlotNames.EXAMPLES;
import static ua.bookloom.pipeline.prompt.SlotNames.TARGET_LANGUAGE;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The task slots of the names-pass prompts, whose worked examples come from the book's language file
 * ({@link PreparationExamples}), kept out of {@link PromptName} so the enum body stays readable.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PreparationSlots {

    private static final Set<String> SHOWN = Set.of(EXAMPLES, "exampleReply");

    /** The recurring-term reading, which names the target its rendering is written in. */
    static final Slots LEXICON = new Slots(Set.of("term", "windows", TARGET_LANGUAGE), SHOWN);

    /** The name proposal. */
    static final Slots PRESCAN =
            new Slots(Set.of("candidates", TARGET_LANGUAGE), Set.of("existingTerms", EXAMPLES, "exampleReply"));

    /** The glossary review. */
    static final Slots REVIEW = new Slots(Set.of("terms", TARGET_LANGUAGE), SHOWN);

    /**
     * A reading of one glossary term: the term, its numbered text and the examples.
     *
     * @param textSlot the slot of the numbered text
     * @return the task template's slots
     */
    static Slots reading(final String textSlot) {
        return new Slots(Set.of("term", textSlot), SHOWN);
    }

    /**
     * The key of the worked examples a names-pass prompt takes from the book's language file.
     *
     * @param name the non-null prompt
     * @return the key, such as {@code termReading}, or null for a prompt whose examples are not the book's
     */
    static @Nullable String kindOf(final PromptName name) {
        return switch (name) {
            case TERM_READING -> "termReading";
            case LEXICON_READING -> "lexiconReading";
            case PRESCAN -> "prescan";
            case REVIEW_TERMS -> "reviewTerms";
            default -> null;
        };
    }
}
