package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The model entries returned by an OpenAI-compatible models endpoint.
 *
 * @param data nullable because a malformed or partial listing may omit the collection
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiModelsResponse(
        @JsonProperty("data") @Nullable List<Model> data) {

    /** Copies a present model list to preserve its order and prevent mutation. */
    public OpenAiModelsResponse {
        if (data != null) {
            data = List.copyOf(data);
        }
    }

    /**
     * One OpenAI-compatible model entry.
     *
     * @param id nullable because a malformed listing may omit an entry's identifier
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Model(@JsonProperty("id") @Nullable String id) {}
}
