package ua.bookloom.llm.profile;

import static ua.bookloom.llm.profile.IdentityPattern.family;

import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Qwen 2.5 (never thinks), Qwen 3.5 and 3.6 (boolean switch) and Qwen 3.8 (level switch). */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QwenRows {

    private static final int QWEN25_WINDOW = 32_768;
    private static final String QWEN25 = "Qwen 2.5 is a plain instruct model with a native system role and a window of"
            + " 32,768 tokens; it never thinks, so no thinking control is sent. ";
    private static final String QWEN3 = "Thinks by default with a boolean switch (Ollama think false, an"
            + " OpenAI-style server reasoning effort none); on Ollama the default is thinking on at every size. ";
    private static final String NOT_RECOMMENDED = "not recommended";
    private static final String EXPERIMENTAL = "experimental";
    private static final String USABLE = "usable";
    private static final String MAKER_SAMPLER =
            "The maker's sampler is top-p 0.8, top-k 20; BookLoom sends its own (arm 3a of task 6.10 kept it).";

    static List<BuiltInRow> all() {
        return Stream.of(qwen25Rows(), qwen3Rows(), qwen3Large(), qwen3Vision())
                .flatMap(List::stream)
                .toList();
    }

    private static List<BuiltInRow> qwen25Rows() {
        return List.of(
                qwen25(
                                "qwen2.5-small",
                                new Double[] {0.5, 1.5},
                                NOT_RECOMMENDED,
                                "Too small for rule-heavy work.",
                                "qwen2.5:1.5b",
                                "qwen2.5:0.5b")
                        .batch(2),
                qwen25(
                        "qwen2.5-3b",
                        new Double[] {3.0},
                        NOT_RECOMMENDED,
                        "Chinese characters can leak into the reply (the foreign-script check blocks them);"
                                + " Ukrainian is not declared.",
                        "qwen2.5:3b"),
                qwen25(
                        "qwen2.5-7b",
                        new Double[] {7.0},
                        NOT_RECOMMENDED,
                        "Ukrainian is near chance on exam tests.",
                        "qwen2.5:7b-instruct-q4_K_M"),
                qwen25("qwen2.5-14b", new Double[] {14.0}, USABLE, MAKER_SAMPLER, "qwen2.5:14b"),
                qwen25("qwen2.5-32b", new Double[] {32.0}, USABLE, MAKER_SAMPLER, "qwen2.5:32b"));
    }

    private static List<BuiltInRow> qwen3Rows() {
        return List.of(
                qwen3(
                                "qwen3.5-2b",
                                "3.5",
                                2.0,
                                NOT_RECOMMENDED,
                                "Loops while thinking; warn, never block.",
                                "qwen3.5:2b")
                        .batch(2),
                qwen3(
                                "qwen3.5-4b",
                                "3.5",
                                4.0,
                                EXPERIMENTAL,
                                "Its maker advises presence penalty 0 and a temperature of at least 0.6; BookLoom"
                                        + " sends its own sampling (arm 3a of task 6.10 kept it).",
                                "qwen3.5:4b")
                        .batch(2),
                qwen3(
                        "qwen3.5-9b",
                        "3.5",
                        9.0,
                        EXPERIMENTAL,
                        "LM Studio's off control is unknown, so it is checked first.",
                        "qwen3.5:9b",
                        "hf.co/someuser/Qwen3.5-9B-GGUF:Q4_K_M"));
    }

    private static List<BuiltInRow> qwen3Large() {
        return List.of(
                qwen3(
                        "qwen3.5-27b",
                        "3.5",
                        27.0,
                        EXPERIMENTAL,
                        "Loops below temperature 0.5; one system message only.",
                        "qwen3.5:27b"),
                qwen3(
                        "qwen3.5-35b",
                        "3.5",
                        35.0,
                        EXPERIMENTAL,
                        "Mixture of experts with 3B active; hybrid attention, so no prefix-cache promise.",
                        "qwen3.5:35b"),
                qwen3("qwen3.6-35b", "3.6", 35.0, EXPERIMENTAL, "No multilingual measurement exists.", "qwen3.6:35b"),
                qwen38());
    }

    private static List<BuiltInRow> qwen3Vision() {
        return List.of(BuiltInRow.of(
                "qwen3-vl-4b-instruct",
                family("qwen").version("3").sizes(4.0).requiringAll("instruct", "vision"),
                RowReasoning.NONE,
                EXPERIMENTAL,
                "The instruct build of the Qwen3 vision-language model never thinks, so no thinking control is sent."
                        + " The plain qwen3-vl:4b is the thinking build and has no row of its own, so it gets the"
                        + " default profile; it never takes this row.",
                "qwen3-vl:4b-instruct"));
    }

    private static BuiltInRow qwen25(
            final String name,
            final Double[] sizes,
            final String verdict,
            final String note,
            final String... examples) {
        return BuiltInRow.of(
                        name,
                        family("qwen").version("2.5").sizes(sizes),
                        RowReasoning.NONE,
                        verdict,
                        QWEN25 + note,
                        examples)
                .hardCap(QWEN25_WINDOW);
    }

    private static BuiltInRow qwen3(
            final String name,
            final String version,
            final double size,
            final String verdict,
            final String note,
            final String... examples) {
        return BuiltInRow.of(
                name,
                family("qwen").version(version).sizes(size),
                RowReasoning.BOOLEAN,
                verdict,
                QWEN3 + note,
                examples);
    }

    private static BuiltInRow qwen38() {
        return BuiltInRow.of(
                "qwen3.8-27b",
                family("qwen").version("3.8").sizes(27.0),
                RowReasoning.LEVELS,
                EXPERIMENTAL,
                "Thinks by named levels and is switched off with false, never true: true or no value renders the"
                        + " medium level on Ollama. LM Studio cannot switch it off (reasoning effort does nothing on"
                        + " MLX, and the template default is the highest level), so use Ollama.",
                "qwen3.8:27b-mxfp8");
    }
}
