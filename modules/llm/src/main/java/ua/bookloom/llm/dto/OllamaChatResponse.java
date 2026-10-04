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
 * @param promptEvalCount nullable because providers may omit the prompt token count
 * @param evalCount nullable because providers may omit the completion token count
 * @param evalDuration nullable generation time in nanoseconds, because providers may omit it
 * @param promptEvalDuration nullable time spent evaluating the prompt in nanoseconds, because providers may omit it
 * @param error nullable; the failure a provider reports in place of a reply, which a stream may send after it began
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatResponse(
        @JsonProperty("model") @Nullable String model,
        @JsonProperty("message") @Nullable Message message,
        @JsonProperty("done_reason") @Nullable String doneReason,
        @JsonProperty("prompt_eval_count") @Nullable Integer promptEvalCount,
        @JsonProperty("eval_count") @Nullable Integer evalCount,
        @JsonProperty("eval_duration") @Nullable Long evalDuration,
        @JsonProperty("prompt_eval_duration") @Nullable Long promptEvalDuration,
        @JsonProperty("error") @Nullable String error) {

    /**
     * The generated content returned by Ollama.
     *
     * @param content nullable because malformed or partial replies may omit generated text
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(@JsonProperty("content") @Nullable String content) {}
}
