package ua.bookloom.llm.client.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ReasoningLevel;

class OllamaThinkControlTest {

    @Test
    void thinkControl_gptOssWithReasoningOff_isLowLevel() {
        assertThat(OllamaClient.thinkControl("gpt-oss:20b", ReasoningLevel.OFF)).isEqualTo("low");
    }

    @Test
    void thinkControl_otherModelWithReasoningOff_isFalse() {
        assertThat(OllamaClient.thinkControl("gemma4:e4b-mlx", ReasoningLevel.OFF))
                .isEqualTo(false);
    }

    @Test
    void thinkControl_unspecified_isOmitted() {
        assertThat(OllamaClient.thinkControl("gpt-oss:20b", null)).isNull();
    }

    @Test
    void withReasoningHeadroom_gptOss_addsRoomForReasoning() {
        assertThat(OllamaClient.withReasoningHeadroom("gpt-oss:20b", 300, ReasoningLevel.OFF))
                .isEqualTo(1324);
    }

    // A model with thinking asked for spends part of the cap on it, whichever model it is.
    @Test
    void withReasoningHeadroom_otherModelWithThinkingOn_addsRoomForReasoning() {
        assertThat(OllamaClient.withReasoningHeadroom("gemma4:e4b-mlx", 300, ReasoningLevel.ON))
                .isEqualTo(1324);
    }

    @Test
    void withReasoningHeadroom_gptOssWithThinkingOn_addsTheRoomOnce() {
        assertThat(OllamaClient.withReasoningHeadroom("gpt-oss:20b", 300, ReasoningLevel.ON))
                .isEqualTo(1324);
    }

    @Test
    void withReasoningHeadroom_thinkingOnWithoutACap_staysUncapped() {
        assertThat(OllamaClient.withReasoningHeadroom("gemma4:e4b-mlx", null, ReasoningLevel.ON))
                .isNull();
    }

    @Test
    void withReasoningHeadroom_otherModelUnspecified_keepsCap() {
        assertThat(OllamaClient.withReasoningHeadroom("gemma4:e4b-mlx", 300, null))
                .isEqualTo(300);
    }

    @Test
    void withReasoningHeadroom_otherModel_keepsCap() {
        assertThat(OllamaClient.withReasoningHeadroom("gemma4:e4b-mlx", 300, ReasoningLevel.OFF))
                .isEqualTo(300);
    }

    // Low is Ollama's own "low" effort for any model, not the boolean.
    @Test
    void thinkControl_lowOnAnyModel_isTheLowEffortString() {
        assertThat(OllamaClient.thinkControl("gemma4:e4b-mlx", ReasoningLevel.LOW))
                .isEqualTo("low");
        assertThat(OllamaClient.thinkControl("gpt-oss:20b", ReasoningLevel.LOW)).isEqualTo("low");
    }

    @Test
    void thinkControl_on_isTrue() {
        assertThat(OllamaClient.thinkControl("gemma4:e4b-mlx", ReasoningLevel.ON))
                .isEqualTo(true);
    }

    // A call at Low spends part of its cap on reasoning, so it gets the same room as On.
    @Test
    void withReasoningHeadroom_otherModelWithLow_addsRoomForReasoning() {
        assertThat(OllamaClient.withReasoningHeadroom("gemma4:e4b-mlx", 300, ReasoningLevel.LOW))
                .isEqualTo(1324);
    }
}
