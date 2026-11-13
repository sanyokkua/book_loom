package ua.bookloom.llm.profile;

import static ua.bookloom.api.llm.ModelProfile.Quirk.ESCAPE_THINK_TOGGLES;
import static ua.bookloom.api.llm.ModelProfile.Quirk.EXAMPLES_OFF;
import static ua.bookloom.api.llm.ModelProfile.Quirk.HARMONY_FORMAT;
import static ua.bookloom.api.llm.ModelProfile.Quirk.NO_REASONING_WORDS;
import static ua.bookloom.api.llm.ModelProfile.Quirk.PROMPT_ONLY_START;
import static ua.bookloom.api.llm.ModelProfile.Quirk.TEMPLATE_WRITES_REASONING;
import static ua.bookloom.llm.profile.IdentityPattern.family;

import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ModelProfile;

/** gpt-oss, Granite 4.1, the Nemotron 3 family, muse-glimmer, EuroLLM, DeepSeek-R1 7B, Phi-4 mini and GLM 4.7 Flash. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OtherRows {

    private static final int GPT_OSS_TEMPLATE_OVERHEAD = 135;
    private static final String GRANITE =
            "A plain instruct model with a native system role; it never thinks, so no" + " thinking control is sent. ";
    private static final String NEMOTRON_FAMILY = "nemotron";
    private static final String NEMOTRON = "A Mamba-transformer hybrid with a boolean thinking switch (Ollama think"
            + " false, an OpenAI-style server reasoning effort none); it thinks by default. ";
    private static final String OLD_RENDERER = "Its older renderer reads /think and /no_think written in book text as a"
            + " mode switch, so a word joiner (U+2060) is put after the slash. ";
    private static final String NOT_RECOMMENDED = "not recommended";
    private static final String EXPERIMENTAL = "experimental";

    static List<BuiltInRow> all() {
        return Stream.of(gptOssAndGranite(), nemotronAndOthers(), smallModels())
                .flatMap(List::stream)
                .toList();
    }

    private static List<BuiltInRow> gptOssAndGranite() {
        return List.of(
                gptOss(),
                granite("granite4.1-3b", 3.0, "Ukrainian and Russian are not declared by the maker.")
                        .batch(2),
                granite("granite4.1-8b", 8.0, "As the 3B; always send num_ctx, the 131,072 default used 55 GB."),
                granite("granite4.1-30b", 30.0, "As the 8B."));
    }

    private static List<BuiltInRow> nemotronAndOthers() {
        return List.of(
                nano("nemotron-3-nano-4b", 4.0, "nemotron-3-nano:4b", "English-centric.")
                        .batch(2),
                nano(
                                "nemotron-3-nano-30b",
                                30.0,
                                "nemotron-3-nano:30b",
                                "English-centric; Ukrainian and Russian are not declared; mixture of experts with 3B active.")
                        .batch(4),
                nemotronNewRenderer(),
                nemotronNamed(
                        "nemotron-cascade-2-30b",
                        family("nemotron-cascade").version("2").sizes(30.0),
                        NOT_RECOMMENDED,
                        "A reasoning model for English only.",
                        "nemotron-cascade-2:30b"),
                nemotronNamed(
                        "nemotron3-33b",
                        family(NEMOTRON_FAMILY).version("3").sizes(33.0),
                        "not offered for uk/ru",
                        "The Omni build takes English input only, so Start refuses a non-English source.",
                        "nemotron3:33b"),
                museGlimmer(),
                euroLlm());
    }

    private static List<BuiltInRow> smallModels() {
        return List.of(deepSeekR1Seven(), phi4Mini(), glmFlash());
    }

    private static BuiltInRow deepSeekR1Seven() {
        return BuiltInRow.of(
                        "deepseek-r1-7b",
                        family("deepseek-r1").sizes(7.0),
                        RowReasoning.ALWAYS_NO_SWITCH,
                        NOT_RECOMMENDED,
                        "Always reasons and has no switch; the system text is folded into the user turn. The pattern"
                                + " names only 7b: deepseek-r1:8b is a Qwen3 distill and 671b the full model, and a"
                                + " shared architecture name never picks this row.",
                        "deepseek-r1:7b")
                .folded();
    }

    private static BuiltInRow phi4Mini() {
        return BuiltInRow.of(
                        "phi4-mini",
                        family("phi").version("4").line("mini"),
                        RowReasoning.NONE,
                        EXPERIMENTAL,
                        "A plain instruct model that never thinks; its example pairs are left out of the prompt because"
                                + " it copies them into the answer, and a batch is two segments.",
                        "phi4-mini")
                .quirks(EXAMPLES_OFF)
                .batch(2, ModelProfile.Tier.SMALL);
    }

    private static BuiltInRow glmFlash() {
        return BuiltInRow.of(
                        "glm-4.7-flash",
                        family("glm").version("4.7").line("flash"),
                        RowReasoning.BOOLEAN,
                        EXPERIMENTAL,
                        "A mixture of experts with 3B active (read through the name grammar's active size); boolean"
                                + " thinking switch. Structured answers start on the prompt-only rung until the model"
                                + " is checked.",
                        "glm-4.7-flash",
                        "hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL")
                .quirks(PROMPT_ONLY_START)
                .batch(4, ModelProfile.Tier.LARGE);
    }

    private static BuiltInRow gptOss() {
        return BuiltInRow.of(
                        "gpt-oss-20b",
                        family("gpt-oss").sizes(20.0),
                        RowReasoning.HARMONY,
                        NOT_RECOMMENDED,
                        "Speaks the harmony message format and always reasons (about 60 percent of its output): the"
                                + " lowest level low is sent everywhere, never none, which means medium; the level"
                                + " goes only through the engine's reasoning field. Room for the reasoning is"
                                + " reserved; the channel markers are scrubbed; the caller's system text goes to a"
                                + " developer turn; the Ollama template puts the current date in the prompt, so the"
                                + " prompt prefix changes daily. Mixture of experts with 3.6B active.",
                        "gpt-oss:20b")
                .quirks(HARMONY_FORMAT)
                .templateOverhead(GPT_OSS_TEMPLATE_OVERHEAD)
                .batch(4, ModelProfile.Tier.LARGE);
    }

    private static BuiltInRow granite(final String name, final double size, final String note) {
        return BuiltInRow.of(
                name,
                family("granite").version("4.1").sizes(size),
                RowReasoning.NONE,
                NOT_RECOMMENDED,
                GRANITE + note,
                "granite4.1:" + (int) size + "b");
    }

    private static BuiltInRow nano(final String name, final double size, final String example, final String note) {
        return nemotronNamed(
                name, family(NEMOTRON_FAMILY).version("3").line("nano").sizes(size), EXPERIMENTAL, note, example);
    }

    private static BuiltInRow nemotronNewRenderer() {
        return BuiltInRow.of(
                        "nemotron-3.5-lightning-30b",
                        family(NEMOTRON_FAMILY).version("3.5").line("lightning").sizes(30.0),
                        RowReasoning.BOOLEAN,
                        EXPERIMENTAL,
                        NEMOTRON + "Its newer renderer treats /think as plain text, so nothing is escaped.",
                        "nemotron-3.5-lightning:30b")
                .batch(4, ModelProfile.Tier.LARGE);
    }

    private static BuiltInRow nemotronNamed(
            final String name,
            final IdentityPattern pattern,
            final String verdict,
            final String note,
            final String example) {
        return BuiltInRow.of(name, pattern, RowReasoning.BOOLEAN, verdict, NEMOTRON + OLD_RENDERER + note, example)
                .quirks(ESCAPE_THINK_TOGGLES)
                .batch(4, pattern.isLarge() ? ModelProfile.Tier.LARGE : ModelProfile.Tier.SMALL);
    }

    private static BuiltInRow museGlimmer() {
        return BuiltInRow.of(
                        "muse-glimmer-30b",
                        family("muse-glimmer").sizes(30.0),
                        RowReasoning.GLIMMER,
                        EXPERIMENTAL,
                        "Always reasons and cannot be switched off: its template writes the reasoning level itself"
                                + " and a hidden note-to-self message precedes the answer, which is scrubbed. Level low"
                                + " is sent whenever a schema is sent, never max; no system line names a reasoning"
                                + " level. Structured answers start on the prompt-only rung until the model is checked.",
                        "muse-glimmer:30b-nvfp4-dflash")
                .quirks(TEMPLATE_WRITES_REASONING, NO_REASONING_WORDS, PROMPT_ONLY_START)
                .filters(ModelProfile.MarkupFilter.TO_SELF)
                .batch(4, ModelProfile.Tier.LARGE);
    }

    private static BuiltInRow euroLlm() {
        return BuiltInRow.of(
                        "eurollm",
                        family("eurollm").sizes(1.7, 9.0, 22.0),
                        RowReasoning.NONE,
                        EXPERIMENTAL,
                        "EuroLLM (1.7B, 9B, 22B) speaks a native system role through its own ChatML template and"
                                + " never thinks, so no thinking control is sent.",
                        "hf.co/bartowski/utter-project_EuroLLM-22B-Instruct-2512-GGUF:IQ4_NL",
                        "EuroLLM-1.7B-Instruct")
                .batch(4, ModelProfile.Tier.SMALL);
    }
}
