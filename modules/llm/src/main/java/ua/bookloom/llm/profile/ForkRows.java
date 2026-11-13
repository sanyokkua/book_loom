package ua.bookloom.llm.profile;

import static ua.bookloom.llm.profile.IdentityPattern.family;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelReport;

/** Fine-tunes and distills of another maker's model that a person installs by name: MiMo, Thomson, Hermes 4, BigBang. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ForkRows {

    private static final String NOT_RECOMMENDED = "not recommended";
    private static final String EXPERIMENTAL = "experimental";

    static List<BuiltInRow> all() {
        return List.of(mimoDistill(), thomson(), hermes14b(), bigBang());
    }

    private static BuiltInRow mimoDistill() {
        return BuiltInRow.of(
                "mimo-v2.6-distill-qwen-9b",
                family("mimo-distill").sizes(9.0).allowing("distill"),
                RowReasoning.BOOLEAN,
                NOT_RECOMMENDED,
                "A distill of MiMo V2.6 into a Qwen student; it is given the Qwen boolean thinking switch (Ollama"
                        + " think false, an OpenAI-style server reasoning effort none) and the server's own"
                        + " capability list replaces it.",
                "mimo-v2.6-distill-qwen-9b",
                "hf.co/bartowski/XiaomiMiMo_MiMo-V2.6-Distill-Qwen-9B-GGUF:Q4_K_M");
    }

    private static BuiltInRow thomson() {
        return BuiltInRow.of(
                        "thomson-1.0-small",
                        family("thomson").version("1.0").line("small"),
                        RowReasoning.BOOLEAN,
                        EXPERIMENTAL,
                        "Thomson 1.0 Small is given its Qwen base's boolean thinking switch; the server's own capability"
                                + " list replaces it. Its licence, PolyForm Strict"
                                + " 1.0.0, allows noncommercial use only, so the Models tab shows a licence note"
                                + " (warning key "
                                + ModelReport.WARNING_LICENCE_NOTE
                                + ").",
                        "thomson-1.0-small",
                        "hf.co/bartowski/thomsonreuters_Thomson-1.0-Small-GGUF")
                .warning(ModelReport.WARNING_LICENCE_NOTE)
                .batch(4, ModelProfile.Tier.LARGE);
    }

    private static BuiltInRow hermes14b() {
        return BuiltInRow.of(
                "hermes-4-14b",
                family("hermes").version("4").sizes(14.0),
                RowReasoning.NONE,
                EXPERIMENTAL,
                "Hermes 4 14B is built on Qwen3-14B and is sent no thinking control unless its server declares"
                        + " one. The 70B is built on Llama 3.1 and takes the Llama 3.1 row as a fine-tune, so the"
                        + " size, not the family name, picks the row.",
                "hf.co/bartowski/NousResearch_Hermes-4-14B-GGUF:Q4_K_M");
    }

    private static BuiltInRow bigBang() {
        return BuiltInRow.of(
                        "bigbang-v1",
                        family("bigbang"),
                        RowReasoning.BOOLEAN,
                        NOT_RECOMMENDED,
                        "BigBang v1 is given its Qwen base's boolean thinking switch; the server's own capability list"
                                + " replaces it. Nothing measured vouches for its translation.",
                        "bigbang-v1",
                        "hf.co/someuser/BigBang-v1-GGUF:Q4_K_M")
                .batch(4, ModelProfile.Tier.LARGE);
    }
}
