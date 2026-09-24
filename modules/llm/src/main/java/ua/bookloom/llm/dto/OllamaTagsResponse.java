package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The model entries returned by Ollama's tags endpoint.
 *
 * @param models nullable because a malformed or partial listing may omit the collection
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaTagsResponse(
        @JsonProperty("models") @Nullable List<Model> models) {

    /** Copies a present model list to preserve its order and prevent mutation. */
    public OllamaTagsResponse {
        if (models != null) {
            models = List.copyOf(models);
        }
    }

    /**
     * One Ollama model entry.
     *
     * @param name nullable because a malformed listing may omit an entry's name
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Model(@JsonProperty("name") @Nullable String name) {}
}
