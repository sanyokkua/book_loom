package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

/**
 * The provider-specific error value returned by an OpenAI-compatible endpoint.
 *
 * @param error nullable because an unsuccessful response may omit a structured error
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiErrorBody(
        @JsonProperty("error") @Nullable JsonNode error) {}
