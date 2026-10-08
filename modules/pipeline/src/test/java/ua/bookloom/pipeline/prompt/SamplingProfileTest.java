package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Every call kind has a profile, and the server mode sends none. */
class SamplingProfileTest {

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void paramsFor_tuned_everyCallStatesAllFourControlsWithNoRepeatPenalty(PromptName name) {
        assertThat(SamplingProfile.paramsFor(name, SamplingMode.TUNED)).satisfies(params -> {
            assertThat(params.repeatPenalty()).isEqualTo(1.0);
            assertThat(params.topP()).isEqualTo(0.9);
            assertThat(params.minP()).isEqualTo(0.05);
            assertThat(params.topK()).isIn(20, 40);
        });
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void paramsFor_server_isNullForEveryCall(PromptName name) {
        assertThat(SamplingProfile.paramsFor(name, SamplingMode.SERVER)).isNull();
    }

    @Test
    void of_textAndAnalysisCalls_takeTheirOwnTopK() {
        assertThat(SamplingProfile.of(PromptName.DRAFT_BATCH_JSON).params().topK())
                .isEqualTo(40);
        assertThat(SamplingProfile.of(PromptName.PRESCAN).params().topK()).isEqualTo(20);
        assertThat(SamplingProfile.of(PromptName.DIRECTED_FIX).params().topK()).isEqualTo(40);
        assertThat(SamplingProfile.of(PromptName.SUMMARY).params().topK()).isEqualTo(20);
    }
}
