package ua.bookloom.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Locale;
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
     * @param remoteHost the service a cloud model runs on, or null for a model on this machine
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Model(
            @JsonProperty("name") @Nullable String name,
            @JsonProperty("remote_host") @Nullable String remoteHost) {

        /**
         * Whether Ollama runs this model on its cloud service rather than on this machine.
         *
         * @return {@code true} if the entry names a remote host or its name is a cloud name, {@code false} otherwise
         */
        public boolean isCloud() {
            return (remoteHost != null && !remoteHost.isBlank()) || (name != null && isCloudName(name));
        }
    }

    /**
     * Whether a model name is one of Ollama's cloud names, which end {@code :cloud} or {@code -cloud}.
     *
     * @param name the model name; non-null
     * @return {@code true} if the name ends with a cloud suffix in any letter case, {@code false} otherwise
     */
    public static boolean isCloudName(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(":cloud") || lower.endsWith("-cloud");
    }
}
