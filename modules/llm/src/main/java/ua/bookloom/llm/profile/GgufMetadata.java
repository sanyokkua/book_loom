package ua.bookloom.llm.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelIdentity;

/**
 * Reads the identity a GGUF header states about itself. The architecture is the weights' own and so wins over a name;
 * the base model names are read with the same grammar as a typed name. Pure: the header arrives already read.
 */
@Slf4j
public final class GgufMetadata {

    private static final String ARCHITECTURE = "general.architecture";
    private static final String BASE_PREFIX = "general.base_model.";
    private static final String BASE_COUNT = BASE_PREFIX + "count";

    private final ModelIdentityParser parser = new ModelIdentityParser();

    /**
     * Reads the header's identity keys.
     *
     * @param metadata a flat JSON object keyed by the dotted GGUF key names (Ollama's {@code model_info}); keys
     *     other than {@code general.architecture} and {@code general.base_model.*} are ignored
     * @return the identity; an absent key leaves its part null or empty
     */
    public GgufIdentity read(final JsonNode metadata) {
        Objects.requireNonNull(metadata, "metadata");
        final String architecture = metadata.path(ARCHITECTURE).isTextual()
                        && !metadata.path(ARCHITECTURE).asText().isBlank()
                ? ArchitectureAliases.normalise(metadata.path(ARCHITECTURE).asText())
                : null;
        final List<ModelIdentity> bases = new ArrayList<>();
        final int count = metadata.path(BASE_COUNT).asInt(0);
        for (int index = 0; index < count; index++) {
            final JsonNode name = metadata.path(BASE_PREFIX + index + ".name");
            if (name.isTextual() && !name.asText().isBlank()) {
                bases.add(parser.parseBaseModelName(name.asText()).identity());
            }
        }
        log.debug("GGUF identity read architecture={} baseModels={}", architecture, bases.size());
        return new GgufIdentity(architecture, bases);
    }
}
