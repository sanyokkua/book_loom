package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * The native error field Ollama may return with a non-success status.
 *
 * @param error nullable because the server may return a non-JSON or empty error body
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaErrorBody(
        @JsonProperty("error") @Nullable String error) {}
