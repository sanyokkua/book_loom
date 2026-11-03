package ua.bookloom.llm.profile;

import static ua.bookloom.api.llm.ModelProfile.Quirk.EXAMPLES_OFF;
import static ua.bookloom.llm.profile.IdentityPattern.family;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Llama 3.1 and 3.2. A model is picked by its name; the server's {@code llama} architecture is shared by unrelated
 * models and never picks these rows.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LlamaRows {

    private static final int KNOWLEDGE_DATE_OVERHEAD = 30;
    private static final String NOT_RECOMMENDED = "not recommended";
    private static final String SMALL_LLAMA = "A plain instruct model with a native system role that never thinks, so"
            + " no thinking control is sent. Too small for rule-heavy work; its example pairs are left out of the"
            + " prompt because it copies them into the answer. ";

    static List<BuiltInRow> all() {
        return List.of(llama31(), small("llama3.2-1b", 1.0, "llama3.2:1b"), small("llama3.2-3b", 3.0, "llama3.2:3b"));
    }

    private static BuiltInRow llama31() {
        return BuiltInRow.of(
                        "llama3.1",
                        family("llama").version("3.1"),
                        RowReasoning.NONE,
                        NOT_RECOMMENDED,
                        "A plain instruct model with a native system role that never thinks, so no thinking control is"
                                + " sent. Its chat template puts a knowledge-date block in the system turn, about 30"
                                + " tokens. Ukrainian and Russian are not declared by the maker. Every size takes this"
                                + " row, and so does a fine-tune built on it that has no row of its own.",
                        "llama3.1:8b")
                .templateOverhead(KNOWLEDGE_DATE_OVERHEAD);
    }

    private static BuiltInRow small(final String name, final double size, final String example) {
        return BuiltInRow.of(
                        name,
                        family("llama").version("3.2").sizes(size),
                        RowReasoning.NONE,
                        NOT_RECOMMENDED,
                        SMALL_LLAMA + "Ukrainian and Russian are not declared by the maker.",
                        example)
                .quirks(EXAMPLES_OFF)
                .batch(2);
    }
}
