package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    void request_messageOnlyConstructorDefaultsOptionalSettingsToNull() {
        final ChatRequest request = new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));

        assertThat(request.temperature()).isNull();
        assertThat(request.responseFormat()).isNull();
        assertThat(request.reasoningEnabled()).isNull();
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
}
