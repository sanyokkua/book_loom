package ua.bookloom.llm.profile;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelKind;

/**
 * Decides from a listed id alone whether a model can translate. A listing costs one request and carries no
 * capabilities, so the name does the work here (a vision projector file, a video repository); the facts of a model
 * the person selects can still say embedding afterwards ({@code ModelFacts#kind}). A vision-language model stays: it
 * is a chat model.
 */
@Slf4j
public final class ListedModels {

    private final ModelIdentityParser parser = new ModelIdentityParser();

    /**
     * Whether the id names a chat model.
     *
     * @param id the id as the provider lists it; never null
     * @return {@code true} if the name reads as a chat model, or as nothing the grammar knows; {@code false} if it
     *     is a sidecar, an embedding, an image, video, speech or reranker model, or not a model
     */
    public boolean isChat(final String id) {
        Objects.requireNonNull(id, "id");
        final ModelKind kind = parser.parse(id).identity().kind();
        if (kind != ModelKind.LLM) {
            log.debug("Listed entry left out kind={}", kind);
        }
        return kind == ModelKind.LLM;
    }
}
