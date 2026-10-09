package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;

class UnchangedReplyTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"unchanged\":true}",
                "  { \"unchanged\" : true }\n",
                "```json\n{\"unchanged\":true}\n```",
            })
    void isUnchanged_onlyTheUnchangedObject_isTrue(final String content) {
        assertThat(UnchangedReply.isUnchanged(Result.ok(new ChatResponse(content, FinishReason.STOP))))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"unchanged\":false}",
                "{\"unchanged\":true,\"target\":\"Інакше.\"}",
                "{\"target\":\"Так само.\"}",
                "unchanged",
                ""
            })
    void isUnchanged_anythingElse_isFalse(final String content) {
        assertThat(UnchangedReply.isUnchanged(Result.ok(new ChatResponse(content, FinishReason.STOP))))
                .isFalse();
    }

    @Test
    void isUnchanged_replyCutOff_isFalse() {
        assertThat(UnchangedReply.isUnchanged(Result.ok(new ChatResponse("{\"unchanged\":true}", FinishReason.LENGTH))))
                .isFalse();
    }

    @Test
    void isUnchanged_callError_isFalse() {
        assertThat(UnchangedReply.isUnchanged(
                        Result.err(AppError.of(ErrorCode.unreachable, "Provider unreachable", "No answer."))))
                .isFalse();
    }
}
