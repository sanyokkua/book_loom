package ua.bookloom.llm.profile;

import static ua.bookloom.llm.profile.IdentityPattern.family;

import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ModelProfile;

/** Magistral, Mistral Small, Ministral 3 (instruct and reasoning) and Devstral. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MistralRows {

    private static final String MINISTRAL = "ministral";

    private static final String NO_THINKING =
            "A plain instruct model with a native system role; it never thinks, so" + " no thinking control is sent. ";
    private static final String NOT_RECOMMENDED = "not recommended";
    private static final String EXPERIMENTAL = "experimental";
    private static final String MINISTRAL_UK = "Ukrainian is not in the maker's list. ";

    static List<BuiltInRow> all() {
        return Stream.of(magistralAndSmall(), ministralAndDevstral(), devstralAndReasoning())
                .flatMap(List::stream)
                .toList();
    }

    private static List<BuiltInRow> magistralAndSmall() {
        return List.of(
                magistral(),
                plain(
                        "mistral-small-3.2-24b",
                        family("mistral-small").version("3.2").sizes(24.0),
                        "usable",
                        "The maker lists Ukrainian and Russian; there is no Ukrainian translation measurement. BookLoom's"
                                + " own system message replaces the vendor's chat default.",
                        "hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL",
                        "mistral-small3.2:24b"));
    }

    private static List<BuiltInRow> ministralAndDevstral() {
        return List.of(
                plain(
                                "ministral-3-3b",
                                family(MINISTRAL).version("3").sizes(3.0),
                                NOT_RECOMMENDED,
                                "Ukrainian is not declared by the maker.",
                                "ministral-3:3b")
                        .batch(2),
                plain(
                        "ministral-3-8b",
                        family(MINISTRAL).version("3").sizes(8.0),
                        EXPERIMENTAL,
                        MINISTRAL_UK + "The maker's temperature is below 0.1 while Ollama defaults to 0.15.",
                        "ministral-3:8b"),
                plain(
                        "ministral-3-14b",
                        family(MINISTRAL).version("3").sizes(14.0),
                        EXPERIMENTAL,
                        MINISTRAL_UK
                                + "Its pattern takes no reasoning variant, so ministral-3-14b-reasoning never lands here.",
                        "ministral-3:14b"));
    }

    private static List<BuiltInRow> devstralAndReasoning() {
        return List.of(
                plain(
                                "devstral-small-2-24b",
                                family("devstral-small").version("2").sizes(24.0),
                                NOT_RECOMMENDED,
                                "A coding model.",
                                "devstral-small-2:24b")
                        .batch(4),
                ministralReasoning());
    }

    private static BuiltInRow magistral() {
        return BuiltInRow.of(
                        "magistral-24b",
                        family("magistral").sizes(24.0),
                        RowReasoning.BOOLEAN,
                        EXPERIMENTAL,
                        "A reasoning-trained model whose thinking is switched off by a prefill, so BookLoom runs it"
                                + " without reasoning; its [THINK] blocks and boxed answers are scrubbed. Ollama's"
                                + " default window is a memory tier capped near 39K tokens.",
                        "magistral:24b")
                .filters(ModelProfile.MarkupFilter.BOXED)
                .batch(4);
    }

    private static BuiltInRow plain(
            final String name,
            final IdentityPattern pattern,
            final String verdict,
            final String note,
            final String... examples) {
        return BuiltInRow.of(name, pattern, RowReasoning.NONE, verdict, NO_THINKING + note, examples);
    }

    private static BuiltInRow ministralReasoning() {
        return BuiltInRow.of(
                        "ministral-3-reasoning",
                        family(MINISTRAL).version("3").sizes(3.0, 8.0, 14.0).requiring("reasoning"),
                        RowReasoning.ALWAYS_NO_SWITCH,
                        EXPERIMENTAL,
                        "Ministral 3 Reasoning always reasons and has no switch, so no thinking control is sent and"
                                + " room for the reasoning is reserved on every call; its [THINK]...[/THINK] block is"
                                + " scrubbed from the reply. The instruct row takes no reasoning variant.",
                        "mistralai/ministral-3-14b-reasoning",
                        "ministral-3:8b-reasoning")
                .batch(4, ModelProfile.Tier.SMALL);
    }
}
