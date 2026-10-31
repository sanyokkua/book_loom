package ua.bookloom.llm.profile;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelKind;

/**
 * What a Hugging Face {@code pipeline_tag} says a repository's model does. A repository with no tag, or one this table
 * does not know, says nothing, and the name decides.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PipelineTags {

    private static final Map<String, ModelKind> KINDS = Map.ofEntries(
            Map.entry("text-generation", ModelKind.LLM),
            Map.entry("text2text-generation", ModelKind.LLM),
            Map.entry("image-text-to-text", ModelKind.LLM),
            Map.entry("any-to-any", ModelKind.LLM),
            Map.entry("feature-extraction", ModelKind.EMBEDDING),
            Map.entry("sentence-similarity", ModelKind.EMBEDDING),
            Map.entry("text-ranking", ModelKind.RERANKER),
            Map.entry("text-to-image", ModelKind.IMAGE),
            Map.entry("image-to-image", ModelKind.IMAGE),
            Map.entry("text-to-video", ModelKind.VIDEO),
            Map.entry("image-to-video", ModelKind.VIDEO),
            Map.entry("text-to-speech", ModelKind.SPEECH),
            Map.entry("automatic-speech-recognition", ModelKind.SPEECH),
            Map.entry("text-to-audio", ModelKind.SPEECH));

    /**
     * Maps a pipeline tag to a kind.
     *
     * @param tag the repository's {@code pipeline_tag}, any letter case; null when it has none
     * @return the kind, or empty when the tag is missing or unknown
     */
    public static Optional<ModelKind> kind(final @Nullable String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(KINDS.get(tag.strip().toLowerCase(Locale.ROOT)));
    }
}
