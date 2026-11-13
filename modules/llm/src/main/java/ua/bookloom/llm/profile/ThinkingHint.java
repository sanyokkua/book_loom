package ua.bookloom.llm.profile;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ServerEngine;

/**
 * Names the Ollama library model that fixes a download which cannot turn thinking off. Only a Hugging Face download
 * of a family whose library model has the switch gets one, and only when its reasoning is unswitchable here: a model
 * that always thinks by design has nothing to fix, so it never gets a hint.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ThinkingHint {

    private static final String HUGGING_FACE_PREFIX = "hf.co/";
    private static final Set<String> FAMILIES_WITH_LIBRARY_SWITCH = Set.of("qwen");

    /**
     * The library tag that has the switch this download lacks.
     *
     * @param engine the provider's effective engine; non-null
     * @param modelId the model id as the provider lists it; non-null
     * @param identity who the model is; non-null
     * @param reasoning the thinking control the profile resolved; non-null
     * @return the tag, such as {@code qwen3.5:4b}, or empty when no hint applies
     */
    public static Optional<String> libraryTag(
            final ServerEngine engine,
            final String modelId,
            final ModelIdentity identity,
            final ReasoningControl reasoning) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(reasoning, "reasoning");
        final boolean applies = engine == ServerEngine.OLLAMA
                && reasoning.kind() == ReasoningControl.Kind.UNSWITCHABLE
                && modelId.toLowerCase(Locale.ROOT).startsWith(HUGGING_FACE_PREFIX);
        if (!applies) {
            log.debug("thinking hint skipped model={} engine={} kind={}", modelId, engine, reasoning.kind());
            return Optional.empty();
        }
        final String family = identity.family();
        final @Nullable String version = identity.version();
        final @Nullable Double size = identity.sizeB();
        if (family == null || !FAMILIES_WITH_LIBRARY_SWITCH.contains(family) || version == null || size == null) {
            log.debug("thinking hint skipped model={} family={} version={} size={}", modelId, family, version, size);
            return Optional.empty();
        }
        final String whole = size == Math.rint(size) ? String.valueOf(size.longValue()) : String.valueOf(size);
        final String tag = family + version + ":" + whole + "b";
        log.debug("thinking hint model={} libraryTag={}", modelId, tag);
        return Optional.of(tag);
    }
}
