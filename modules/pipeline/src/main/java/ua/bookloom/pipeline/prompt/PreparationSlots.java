package ua.bookloom.pipeline.prompt;

import static ua.bookloom.pipeline.prompt.SlotNames.EXAMPLES;
import static ua.bookloom.pipeline.prompt.SlotNames.FOREIGN_PASSAGE_RULE;
import static ua.bookloom.pipeline.prompt.SlotNames.LANGUAGE_RULES;
import static ua.bookloom.pipeline.prompt.SlotNames.SOURCE_LANGUAGE;
import static ua.bookloom.pipeline.prompt.SlotNames.STYLE_SHEET;
import static ua.bookloom.pipeline.prompt.SlotNames.TARGET_LANGUAGE;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The system slots of the names-pass prompts, whose worked examples come from the book's language file
 * ({@link PreparationExamples}), kept out of {@link PromptName} so the enum body stays readable.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PreparationSlots {

    private static final Set<String> LANGUAGES = Set.of(SOURCE_LANGUAGE, TARGET_LANGUAGE);

    /** A reading of one term and the glossary review: the frame's slots and the examples. */
    static final Slots READING =
            new Slots(LANGUAGES, Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE, EXAMPLES, "exampleReply"));

    /** The summary of one term's readings at Max, which shows no examples. */
    static final Slots SUMMARY = new Slots(LANGUAGES, Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE));

    /** The name proposal: the frame's slots, the language rules and the examples. */
    static final Slots PRESCAN =
            new Slots(LANGUAGES, Set.of(STYLE_SHEET, FOREIGN_PASSAGE_RULE, LANGUAGE_RULES, EXAMPLES, "exampleReply"));

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
