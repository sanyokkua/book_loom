package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Contract tests for the model-facing records.
 */
class ChatContractsTest {

    @Test
    void messages_constructorCopiesCallerListAndBlocksMutation() {
        final ChatMessage message = new ChatMessage(ChatRole.USER, "hello");
        final List<ChatMessage> messages = new ArrayList<>(List.of(message));

        final ChatRequest request = new ChatRequest(messages);
        messages.add(new ChatMessage(ChatRole.ASSISTANT, "HELLO"));

        assertThat(request.messages()).containsExactly(message);
        assertThatThrownBy(() -> request.messages().add(message)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sizedTo_cappedRequest_takesTheWindowAndLimitsTheCap() {
        final ChatRequest asked = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")), 0.2, null, false, 8192, 300, 3900, 7, null);

        final ChatRequest sized = asked.sizedTo(4096, 2048);

        assertThat(sized.contextWindow()).isEqualTo(4096);
        assertThat(sized.maxOutputTokens()).isEqualTo(2048);
        assertThat(sized.temperature()).isEqualTo(0.2);
        assertThat(sized.expectedOutputTokens()).isEqualTo(300);
        assertThat(sized.seed()).isEqualTo(7);
    }

    @Test
    void sizedTo_smallerCapOrNone_isKept() {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));

        assertThat(new ChatRequest(messages, null, null, null, 8192, null, 100)
                        .sizedTo(4096, 2048)
                        .maxOutputTokens())
                .isEqualTo(100);
        assertThat(new ChatRequest(messages).sizedTo(4096, 2048).maxOutputTokens())
                .isNull();
    }

    @Test
    void request_messageOnlyConstructorDefaultsOptionalSettingsToNull() {
        final ChatRequest request = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));

        assertThat(request.temperature()).isNull();
        assertThat(request.responseFormat()).isNull();
        assertThat(request.reasoningEnabled()).isNull();
        assertThat(request.contextWindow()).isNull();
        assertThat(request.expectedOutputTokens()).isNull();
        assertThat(request.maxOutputTokens()).isNull();
    }

    @Test
    void response_messageOnlyConstructorDefaultsUsageToNull() {
        final ChatResponse response = new ChatResponse("x", FinishReason.STOP);

        assertThat(response.usage()).isNull();
    }

    @Test
    void tokenUsage_allFieldsProvided_arePreserved() {
        final TokenUsage usage = new TokenUsage(812, 96, Duration.ofMillis(3200));

        assertThat(usage.prompt()).isEqualTo(812);
        assertThat(usage.completion()).isEqualTo(96);
        assertThat(usage.generation()).isEqualTo(Duration.ofMillis(3200));
    }

    @Test
    void tokenUsage_allFieldsNull_isRejected() {
        assertThatThrownBy(() -> new TokenUsage(null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenUsage_negativePromptCount_isRejected() {
        assertThatThrownBy(() -> new TokenUsage(-1, 5, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void request_nonPositiveContextWindow_isRejected(int contextWindow) {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));

        assertThatThrownBy(() -> new ChatRequest(messages, null, null, null, contextWindow, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void request_nonPositiveExpectedOutputTokens_isRejected(int expectedOutputTokens) {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));

        assertThatThrownBy(() -> new ChatRequest(messages, null, null, null, null, expectedOutputTokens))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void request_nonPositiveMaxOutputTokens_isRejected(int maxOutputTokens) {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));

        assertThatThrownBy(() -> new ChatRequest(messages, null, null, null, null, null, maxOutputTokens))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void request_sixArgumentConstructor_leavesTheCapUnset() {
        final ChatRequest request =
                new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), 0.2, null, false, 8192, 400);

        assertThat(request.maxOutputTokens()).isNull();
    }

    @Test
    void withoutResponseFormatAndWithoutReasoning_keepTheOutputCap() {
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hello")),
                0.2,
                new ResponseFormat("draft", "{\"type\":\"object\"}"),
                false,
                8192,
                400,
                661);

        assertThat(request.withoutResponseFormat().maxOutputTokens()).isEqualTo(661);
        assertThat(request.withoutReasoning().maxOutputTokens()).isEqualTo(661);
    }

    @Test
    void withoutResponseFormat_clearsOnlyResponseFormat() {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));
        final ResponseFormat responseFormat = new ResponseFormat("draft", "{\"type\":\"object\"}");
        final ChatRequest request = new ChatRequest(messages, 0.2, responseFormat, false, 8192, 400);

        final ChatRequest stripped = request.withoutResponseFormat();

        assertThat(stripped.messages()).isEqualTo(messages);
        assertThat(stripped.temperature()).isEqualTo(0.2);
        assertThat(stripped.responseFormat()).isNull();
        assertThat(stripped.reasoningEnabled()).isFalse();
        assertThat(stripped.contextWindow()).isEqualTo(8192);
        assertThat(stripped.expectedOutputTokens()).isEqualTo(400);
    }

    @Test
    void withoutReasoning_clearsOnlyReasoningControl() {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));
        final ResponseFormat responseFormat = new ResponseFormat("draft", "{\"type\":\"object\"}");
        final ChatRequest request = new ChatRequest(messages, 0.2, responseFormat, false, 8192, 400);

        final ChatRequest stripped = request.withoutReasoning();

        assertThat(stripped.messages()).isEqualTo(messages);
        assertThat(stripped.temperature()).isEqualTo(0.2);
        assertThat(stripped.responseFormat()).isEqualTo(responseFormat);
        assertThat(stripped.reasoningEnabled()).isNull();
        assertThat(stripped.contextWindow()).isEqualTo(8192);
        assertThat(stripped.expectedOutputTokens()).isEqualTo(400);
    }

    @Test
    void request_explicitSettingsArePreserved() {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));
        final ResponseFormat responseFormat = new ResponseFormat("draft", "{\"type\":\"object\"}");
        final ChatRequest request = new ChatRequest(messages, 0.2, responseFormat);

        assertThat(request.messages()).isEqualTo(messages);
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.responseFormat()).isEqualTo(responseFormat);
        assertThat(request.reasoningEnabled()).isNull();
    }

    // A caller may disable reasoning without changing the existing three-argument request construction.
    @Test
    void request_reasoningDisabled_preservesAllHints() {
        final List<ChatMessage> messages = List.of(new ChatMessage(ChatRole.USER, "hello"));
        final ResponseFormat responseFormat = new ResponseFormat("draft", "{\"type\":\"object\"}");

        final ChatRequest request = new ChatRequest(messages, 0.2, responseFormat, false);

        assertThat(request.messages()).isEqualTo(messages);
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.responseFormat()).isEqualTo(responseFormat);
        assertThat(request.reasoningEnabled()).isFalse();
    }

    @SuppressWarnings("NullAway")
    @Test
    void request_nullMessages_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ChatRequest(null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void message_nullRole_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ChatMessage(null, "hello"));
    }

    @SuppressWarnings("NullAway")
    @Test
    void message_nullContent_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ChatMessage(ChatRole.USER, null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void response_nullFinishReason_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ChatResponse("HELLO", null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void response_nullContent_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ChatResponse(null, FinishReason.STOP));
    }

    @SuppressWarnings("NullAway")
    @Test
    void modelSelection_nullProviderId_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ModelSelection(null, "model"));
    }

    @SuppressWarnings("NullAway")
    @Test
    void modelSelection_nullModelId_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ModelSelection("provider", null));
    }

    @Test
    void withSampling_everyCopyingMethod_keepsTheControls() {
        final SamplingParams sampling = new SamplingParams(0.9, 40, 0.05, 1.0);
        final ChatRequest request =
                new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello"))).withSampling(sampling);

        assertThat(request.sampling()).isEqualTo(sampling);
        assertThat(request.withSeed(3).sampling()).isEqualTo(sampling);
        assertThat(request.sizedTo(4096, 512).sampling()).isEqualTo(sampling);
        assertThat(request.forRetry(5, null).sampling()).isEqualTo(sampling);
        assertThat(request.withoutReasoning().sampling()).isEqualTo(sampling);
        assertThat(request.withoutResponseFormat().sampling()).isEqualTo(sampling);
    }

    @Test
    void samplingOrNone_unset_isEmptyControls() {
        assertThat(new ChatRequest(List.of()).samplingOrNone()).isSameAs(SamplingParams.NONE);
    }

    @Test
    void samplingParams_nonPositiveTopK_isRejected() {
        assertThatThrownBy(() -> new SamplingParams(null, 0, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
