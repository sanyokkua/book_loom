package ua.bookloom.llm.client.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OllamaThinkControlTest {

    @Test
    void thinkControl_gptOssWithReasoningOff_isLowLevel() {
        assertThat(OllamaClient.thinkControl("gpt-oss:20b", false)).isEqualTo("low");
    }

    @Test
    void thinkControl_otherModelWithReasoningOff_isFalse() {
        assertThat(OllamaClient.thinkControl("gemma4:e4b-mlx", false)).isEqualTo(false);
    }

    @Test
    void thinkControl_unspecified_isOmitted() {
        assertThat(OllamaClient.thinkControl("gpt-oss:20b", null)).isNull();
    }

    @Test
    void withReasoningHeadroom_gptOss_addsRoomForReasoning() {
        assertThat(OllamaClient.withReasoningHeadroom("gpt-oss:20b", 300)).isEqualTo(1324);
    }

    @Test
    void withReasoningHeadroom_otherModel_keepsCap() {
        assertThat(OllamaClient.withReasoningHeadroom("gemma4:e4b-mlx", 300)).isEqualTo(300);
    }
}
