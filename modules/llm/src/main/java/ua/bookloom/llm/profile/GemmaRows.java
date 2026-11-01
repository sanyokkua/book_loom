package ua.bookloom.llm.profile;

import static ua.bookloom.api.llm.ModelProfile.Quirk.PROMPT_ONLY_START;
import static ua.bookloom.api.llm.ModelProfile.Quirk.QUOTE_CUTS_SCHEMA_STRING;
import static ua.bookloom.api.llm.ModelProfile.Quirk.SHORT_STRING_CHECK;
import static ua.bookloom.llm.profile.IdentityPattern.family;

import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Gemma 3 and its tunes (no system role, never thinks) and Gemma 4 (a boolean thinking switch). The {@code -it} and
 * {@code -it-qat} builds of a Gemma 4 size behave the same, so one row covers both.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GemmaRows {

    private static final String GEMMA = "gemma";

    private static final String NO_SYSTEM_ROLE = "Gemma 3's template has no system role, so the system text is folded"
            + " into the first user message; it never thinks, so no thinking control is sent. ";
    private static final String GEMMA4 = "Gemma 4 thinks by default and has a boolean switch: Ollama takes think false,"
            + " an OpenAI-style server reasoning effort none; thinking markers arrive in channel markers that the reply"
            + " scrubber removes. ";
    private static final String LARGE_DEFAULT = "recommended";
    private static final String USABLE = "usable";
    private static final String EXPERIMENTAL = "experimental";
    private static final String NOT_RECOMMENDED = "not recommended";

    static List<BuiltInRow> all() {
        return Stream.of(gemma3Rows(), gemma4Rows(), gemma4Large())
                .flatMap(List::stream)
                .toList();
    }

    private static List<BuiltInRow> gemma3Rows() {
        return List.of(
                gemma3("gemma3-4b", 4.0, NOT_RECOMMENDED, "Too small: Ukrainian Belebele score 45.", "gemma3:4b")
                        .batch(2),
                gemma3(
                                "gemma3-12b",
                                12.0,
                                USABLE,
                                "On Ollama a structured reply can cut a string at a straight quote mark (ollama#18094),"
                                        + " so the reply is checked again; the QAT build leaks an end-of-turn marker.",
                                "gemma3:12b")
                        .quirks(QUOTE_CUTS_SCHEMA_STRING, SHORT_STRING_CHECK),
                gemma3("gemma3-27b", 27.0, USABLE, "About 18 GB of weights.", "gemma3:27b"),
                mamaylm3(),
                mamaylm2(),
                lapa());
    }

    private static List<BuiltInRow> gemma4Rows() {
        return List.of(
                gemma4Effective(
                        "gemma4-e2b",
                        2.0,
                        NOT_RECOMMENDED,
                        "43 to 70 percent of segments wrong locally; not offered for Ukrainian.",
                        "gemma4:e2b",
                        "gemma4:e2b-it-qat"),
                gemma4Effective(
                        "gemma4-e4b",
                        4.0,
                        USABLE,
                        "The default below 24 GB: batch 4 kept 96 percent of tokens against 83 percent at 8. Measured en to uk on nvfp4, mxfp8 and LM Studio MLX 4-bit.",
                        "gemma4:e4b-mlx",
                        "gemma4:e4b-it-qat",
                        "google/gemma-4-e4b"));
    }

    private static List<BuiltInRow> gemma4Large() {
        return List.of(
                gemma4(
                        "gemma4-12b",
                        12.0,
                        EXPERIMENTAL,
                        "About 10.5 s per segment locally; it may open an empty thought channel, which is scrubbed.",
                        "gemma4:12b",
                        "gemma4:12b-it-qat"),
                gemma4(
                        "gemma4-26b",
                        26.0,
                        LARGE_DEFAULT,
                        "The default from 24 GB: 6 percent wrong, 4.24 s per segment on Ollama; mixture of experts with 4B active; an empty thought channel is scrubbed. QAT loses more at this size.",
                        "gemma4:26b-mlx",
                        "gemma4:26b-a4b-it-qat",
                        "google/gemma-4-26b-a4b-qat"),
                gemma4(
                                "gemma4-31b",
                                31.0,
                                EXPERIMENTAL,
                                "Thinks by default; may loop inside long strings under a schema, so a runaway reply is retried on the prompt-only rung with a new seed. Opt in from 36 GB.",
                                "gemma4:31b",
                                "gemma4:31b-it-qat")
                        .batch(4));
    }

    private static BuiltInRow gemma3(
            final String name, final double size, final String verdict, final String note, final String example) {
        return BuiltInRow.of(
                        name,
                        family(GEMMA).version("3").sizes(size),
                        RowReasoning.NONE,
                        verdict,
                        NO_SYSTEM_ROLE + note,
                        example)
                .folded()
                .quirks(QUOTE_CUTS_SCHEMA_STRING);
    }

    private static BuiltInRow mamaylm3() {
        return BuiltInRow.of(
                        "mamaylm-gemma-3-12b",
                        family("mamaylm-gemma").version("3").sizes(12.0),
                        RowReasoning.NONE,
                        "usable (optional ru to uk drafter)",
                        NO_SYSTEM_ROLE + "A Gemma 3 tune for Ukrainian: about 15 s per segment locally; its maker"
                                + " advises temperature 0.2 and top-k 25 to 64.",
                        "mamaylm-gemma-3-12b-it-v2.0")
                .folded();
    }

    private static BuiltInRow mamaylm2() {
        return BuiltInRow.of(
                        "mamaylm-gemma-2-9b",
                        family("mamaylm-gemma").version("2").sizes(9.0),
                        RowReasoning.NONE,
                        NOT_RECOMMENDED,
                        "A Gemma 2 tune: its template refuses a system role (\"System role not supported\"), so the"
                                + " system text is folded; its window is held to 8,192 tokens; it never thinks.",
                        "mamaylm-gemma-2-9b-it-v0.1")
                .folded()
                .hardCap(8_192)
                .batch(2);
    }

    private static BuiltInRow lapa() {
        return BuiltInRow.of(
                        "lapa",
                        family("lapa"),
                        RowReasoning.NONE,
                        EXPERIMENTAL,
                        "A Ukrainian Gemma 3 tune (no system role, folded; never thinks). It follows rules weakly,"
                                + " so structured answers start on the prompt-only rung; it has the highest rate of"
                                + " Russian-isms.",
                        "lapa-v0.1.2-instruct")
                .folded()
                .quirks(PROMPT_ONLY_START)
                .batch(2);
    }

    private static BuiltInRow gemma4(
            final String name, final double size, final String verdict, final String note, final String... examples) {
        return BuiltInRow.of(
                name, family(GEMMA).version("4").sizes(size), RowReasoning.BOOLEAN, verdict, GEMMA4 + note, examples);
    }

    private static BuiltInRow gemma4Effective(
            final String name,
            final double effective,
            final String verdict,
            final String note,
            final String... examples) {
        return BuiltInRow.of(
                name,
                family(GEMMA).version("4").effective(effective),
                RowReasoning.BOOLEAN,
                verdict,
                GEMMA4 + note,
                examples);
    }
}
