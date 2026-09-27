package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The response fields consumed from an OpenAI-compatible chat completion.
 *
 * @param model nullable because provider replies may omit metadata
 * @param choices nullable because malformed or partial replies may omit the collection
 * @param usage nullable because providers may omit reported token counts
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatResponse(
        @JsonProperty("model") @Nullable String model,
        @JsonProperty("choices") @Nullable List<Choice> choices,
        @JsonProperty("usage") @Nullable Usage usage) {

    /** Copies a present choices list to preserve its order and prevent mutation. */
    public OpenAiChatResponse {
        if (choices != null) {
            choices = List.copyOf(choices);
        }
    }

    /**
     * One generated choice and its finish reason.
     *
     * @param message nullable because a partial choice may omit its generated message
     * @param finishReason nullable because providers may omit a finish reason
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Choice(
            @JsonProperty("message") @Nullable Message message,
            @JsonProperty("finish_reason") @Nullable String finishReason) {}

    /**
     * The assistant message fields consumed from a choice.
     *
     * @param role nullable because a partial message may omit the speaker
     * @param content nullable because a partial message may omit generated text
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            @JsonProperty("role") @Nullable String role,
            @JsonProperty("content") @Nullable String content) {}

    /**
     * Token counts consumed from a chat completion's reported usage.
     *
     * @param promptTokens nullable because a provider may omit the prompt token count
     * @param completionTokens nullable because a provider may omit the completion token count
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
            @JsonProperty("prompt_tokens") @Nullable Integer promptTokens,
            @JsonProperty("completion_tokens") @Nullable Integer completionTokens) {}
}
