package ua.bookloom.llm.profile;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * One spelling for each architecture a server reports under several: llama.cpp writes {@code qwen35} where Ollama's
 * MLX engine and Hugging Face write {@code qwen3_5}. The canonical spelling is the GGUF one (no separators), because
 * that is what a GGUF header, the common case here, carries.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ArchitectureAliases {

    private static final Map<String, String> CANONICAL = Map.of(
            "qwen3_5", "qwen35",
            "qwen3_5_moe", "qwen35moe",
            "gemma4_unified", "gemma4",
            "muse_glimmer", "muse-glimmer",
            "glm4_moe_lite", "glm4moelite");

    /**
     * Normalises one architecture name.
     *
     * @param architecture the name as a server or file reports it; case and surrounding spaces do not matter
     * @return the canonical lower-case spelling; a name with no alias comes back lower-cased and trimmed
     */
    public static String normalise(final String architecture) {
        Objects.requireNonNull(architecture, "architecture");
        final String key = architecture.strip().toLowerCase(Locale.ROOT);
        return CANONICAL.getOrDefault(key, key);
    }
}
