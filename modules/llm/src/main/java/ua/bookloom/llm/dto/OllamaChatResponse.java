package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The reply fields consumed from an Ollama-native chat response.
 *
 * @param model nullable because provider replies may omit metadata
 * @param message nullable because unsuccessful or partial replies may omit generated content
 * @param doneReason nullable because providers may omit a finish reason
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatResponse(
        @JsonProperty("model") @Nullable String model,
        @JsonProperty("message") @Nullable Message message,
        @JsonProperty("done_reason") @Nullable String doneReason) {

    /**
     * The generated content returned by Ollama.
     *
     * @param content nullable because malformed or partial replies may omit generated text
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(@JsonProperty("content") @Nullable String content) {}
}
