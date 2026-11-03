package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ServerEngine;

/** A fine-tune takes its base model's behaviour and never its verdict (row 45 and the base-family match). */
class ModelProfilesBaseFamilyTest {

    private static final String HERMES_70B = "hf.co/bartowski/NousResearch_Hermes-4-70B-GGUF:Q4_K_M";
    private static final String HERMES_14B = "hf.co/bartowski/NousResearch_Hermes-4-14B-GGUF:Q4_K_M";

    private final ModelIdentityParser parser = new ModelIdentityParser();
    private final BuiltInRows builtIn = new BuiltInRows();

    private ResolvedProfile resolve(final ProfileRows rows, final String id) {
        return new ModelProfiles(rows).resolve(ServerEngine.OLLAMA, id, ModelFacts.unknown(), settings(id));
    }

    private static ModelSettings settings(final String id) {
        return ModelSettings.defaults("ollama", id);
    }

    @Test
    void resolve_hermes4_70b_takesLlama31BehaviourAndUnknownFineTuneVerdict() {
        final ResolvedProfile resolved = resolve(builtIn, HERMES_70B);

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.BASE_FAMILY);
        assertThat(resolved.verdict()).isEqualTo("unknown fine-tune");
        assertThat(resolved.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.NONE);
        assertThat(resolved.profile().templateOverhead()).isEqualTo(30);
        assertThat(resolved.baseModel()).isEqualTo("Llama 3.1");
        assertThat(resolved.profile().tier()).isEqualTo(ModelProfile.Tier.LARGE);
        assertThat(resolved.profile().batchSize()).isEqualTo(8);
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void resolve_hermes4_14b_hasItsOwnExactRowAndNamesItsBase() {
        final ResolvedProfile resolved = resolve(builtIn, HERMES_14B);

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.EXACT);
        assertThat(resolved.verdict()).isEqualTo("experimental");
        assertThat(resolved.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.NONE);
        assertThat(resolved.baseModel()).isEqualTo("Qwen3");
        assertThat(builtIn.rows().stream()
                        .filter(r -> "hermes-4-14b".equals(r.row().name()))
                        .findFirst()
                        .orElseThrow()
                        .evidence())
                .contains("Qwen3");
    }

    @Test
    void resolve_hermes4_14b_withoutItsOwnRow_takesQwen3Behaviour() {
        final ModelProfile.Tier fromBase = ModelProfile.Tier.SMALL;
        final ProfileRow qwen3 = new ProfileRow(
                "test qwen3",
                new ReasoningControl(ReasoningControl.Kind.BOOLEAN, List.of("true", "false"), "false", "true"),
                Set.of(),
                ModelProfile.SystemRole.NATIVE,
                Set.of(),
                32_768,
                4_096,
                0,
                2,
                fromBase,
                "recommended",
                List.of("fromBaseWarning"));
        final ProfileRows qwen3Only =
                (identity, facts) -> "qwen".equals(identity.family()) && "3".equals(identity.version())
                        ? Optional.of(new ProfileRows.Picked(qwen3, ModelReport.Match.FAMILY_VERSION))
                        : Optional.empty();

        final ResolvedProfile resolved = resolve(qwen3Only, HERMES_14B);

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.BASE_FAMILY);
        assertThat(resolved.verdict()).isEqualTo("unknown fine-tune");
        assertThat(resolved.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.BOOLEAN);
        assertThat(resolved.profile().tier()).isEqualTo(ModelProfile.Tier.LARGE);
        assertThat(resolved.profile().batchSize()).isEqualTo(8);
        assertThat(resolved.baseModel()).isEqualTo("Qwen3");
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void resolve_deepseekR1DistillOnQwen_keepsDefaultBehaviourButNamesItsBase() {
        final ResolvedProfile resolved = resolve(builtIn, "hf.co/bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF:Q4_K_M");

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.DEFAULT);
        assertThat(resolved.verdict()).isEqualTo("unknown model");
        assertThat(resolved.baseModel()).isEqualTo("Qwen 2.5");
    }

    @Test
    void resolve_qwen3vl4b_thinkingDigestDoesNotTakeInstructRow() {
        final ResolvedProfile thinking = resolve(builtIn, "qwen3-vl:4b");
        final ResolvedProfile instruct = resolve(builtIn, "qwen3-vl:4b-instruct");

        assertThat(thinking.match()).isEqualTo(ModelReport.Match.DEFAULT);
        assertThat(thinking.verdict()).isEqualTo("unknown model");
        assertThat(instruct.match()).isEqualTo(ModelReport.Match.EXACT);
        assertThat(instruct.verdict()).isEqualTo("experimental");
    }

    @Test
    void resolve_thomson_carriesTheLicenceNote() {
        final ResolvedProfile resolved = resolve(builtIn, "hf.co/bartowski/thomsonreuters_Thomson-1.0-Small-GGUF");

        assertThat(resolved.warnings()).containsExactly("licenceNote");
        assertThat(resolved.baseModel()).isEqualTo("Qwen 3.6");
    }

    @Test
    void rows_aliasGuard_hermes4Size70IsLlamaNotRow48() {
        final Optional<ProfileRows.Picked> row48 =
                builtIn.pick(parser.parse(HERMES_70B).identity(), ModelFacts.unknown());
        final Optional<BaseFamilyAliases.Base> base =
                new BaseFamilyAliases().baseOf(parser.parse(HERMES_70B).identity());
        final Optional<BaseFamilyAliases.Base> big =
                new BaseFamilyAliases().baseOf(parser.parse("hermes-4-405b").identity());

        assertThat(row48).isEmpty();
        assertThat(base).hasValueSatisfying(b -> assertThat(b.displayName()).isEqualTo("Llama 3.1"));
        assertThat(big).isEmpty();
    }
}
