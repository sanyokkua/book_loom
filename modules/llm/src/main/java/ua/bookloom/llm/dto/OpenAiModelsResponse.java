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
     * @param type the kind of model, which only LM Studio's own listing gives ({@code llm}, {@code vlm},
     *     {@code embeddings}); null elsewhere
     * @param state whether LM Studio has the model in memory ({@code loaded}, {@code not-loaded}); null elsewhere
     * @param ownedBy the row's {@code owned_by}, which names the serving engine on several servers; null when absent
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Model(
            @JsonProperty("id") @Nullable String id,
            @JsonProperty("type") @Nullable String type,
            @JsonProperty("state") @Nullable String state,
            @JsonProperty("owned_by") @Nullable String ownedBy) {

        /** The kind LM Studio gives a model that turns text into vectors and cannot chat. */
        public static final String EMBEDDINGS = "embeddings";

        /**
         * Whether the listing itself says this entry is an embedding model.
         *
         * @return {@code true} if the entry's type is {@code embeddings}, {@code false} otherwise
         */
        public boolean isEmbedding() {
            return EMBEDDINGS.equals(type);
        }
    }
}
