package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ModelProfileTest {

    @Test
    void defaults_noRow_isTheSafeProfile() {
        final ModelProfile profile = ModelProfile.defaults();

        assertThat(profile.reasoning().kind()).isEqualTo(ReasoningControl.Kind.NONE);
        assertThat(profile.quirks()).containsExactly(ModelProfile.Quirk.PROMPT_ONLY_START);
        assertThat(profile.sampling().mode()).isEqualTo(SamplingOverride.Mode.PROFILE_DEFAULT);
        assertThat(profile.thinking()).isEqualTo(ThinkingOverride.RECOMMENDED);
        assertThat(profile.batchSize()).isEqualTo(2);
        assertThat(profile.context()).isEqualTo(new ContextPolicy(16_384, 8_192, null, null));
    }

    @Test
    void chatModel_withoutAProfileOfItsOwn_answersTheDefault() {
        final ChatModel model = request -> {
            throw new AssertionError("no call expected");
        };

        assertThat(model.profile()).isEqualTo(ModelProfile.defaults());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void new_nonPositiveBatch_throws(final int batch) {
        final ModelProfile d = ModelProfile.defaults();
        assertThatThrownBy(() -> new ModelProfile(
                        d.reasoning(),
                        Set.of(),
                        d.systemRole(),
                        Set.of(),
                        d.sampling(),
                        d.thinking(),
                        d.context(),
                        0,
                        batch,
                        d.tier()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 1,,", "1, 0,,", "1, 1, 0,", "1, 1, 1, -4"})
    void contextPolicy_nonPositiveFigure_throws(
            final int cap, final int output, final Integer detected, final Integer loaded) {
        assertThatThrownBy(() -> new ContextPolicy(cap, output, detected, loaded))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void samplingOverride_custom_keepsTheTypedValues() {
        final SamplingOverride custom = SamplingOverride.custom(0.4, 20, 0.9, 0.05);

        assertThat(custom).isEqualTo(new SamplingOverride(SamplingOverride.Mode.CUSTOM, 0.4, 20, 0.9, 0.05));
    }

    @ParameterizedTest
    @CsvSource({"2.1,,,", "-0.1,,,", ",0,,", ",,0.0,", ",,1.1,", ",,,1.5", ",,,-0.1"})
    void samplingOverride_outOfRange_throws(
            final Double temperature, final Integer topK, final Double topP, final Double minP) {
        assertThatThrownBy(() -> SamplingOverride.custom(temperature, topK, topP, minP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void samplingOverride_profileDefaultWithAValue_throws() {
        assertThatThrownBy(() -> new SamplingOverride(SamplingOverride.Mode.PROFILE_DEFAULT, 0.3, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusedSetting_threeParts_coversTheWholeModel() {
        assertThat(new RefusedSetting("temperature", LocalDate.of(2026, 10, 30), "left it out").scope())
                .isNull();
    }

    @Test
    void withOverrides_keepsEverythingElse() {
        final ModelSettings base = ModelSettings.defaults("ollama", "qwen3.5:9b")
                .withRefused(List.of(new RefusedSetting("top_k", LocalDate.of(2026, 10, 30), "omitted", "DRAFT")));

        final ModelSettings changed =
                base.withOverrides(ThinkingOverride.ALWAYS, SamplingOverride.custom(0.4, null, null, null));

        assertThat(changed.thinkingOverride()).isEqualTo(ThinkingOverride.ALWAYS);
        assertThat(changed.samplingOverride().temperature()).isEqualTo(0.4);
        assertThat(changed.refused()).isEqualTo(base.refused());
        assertThat(changed.withOverrides(ThinkingOverride.RECOMMENDED, SamplingOverride.profileDefault()))
                .isEqualTo(base);
    }
}
