package ua.bookloom.api.llm;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.SafeDetails;

/** The program serving a provider's models; it selects request fields, error rows and routes, not the client. */
public enum ServerEngine {
    /** Ollama, native or behind its OpenAI-compatible routes. */
    OLLAMA("Ollama"),
    /** LM Studio. */
    LM_STUDIO("LM Studio"),
    /** llama.cpp's llama-server. */
    LLAMA_CPP("llama-server"),
    /** OpenRouter, hosted or on a custom domain. */
    OPENROUTER("OpenRouter"),
    /** Unsloth Studio. */
    UNSLOTH("Unsloth"),
    /** Any other server; it gets no control until a check proves it. */
    GENERIC("an OpenAI-compatible server");

    private static final Pattern IN_DETAILS = Pattern.compile("(?:\\A|, )engine=([A-Z_]+)(?:,|\\z)");

    private final String label;

    ServerEngine(final String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /**
     * The engine's name in an English sentence such as "The server at this address is LM Studio, not Ollama."; the
     * window words its own languages from the constant, not from this.
     *
     * @return the product's own spelling; never null
     */
    public String label() {
        return label;
    }

    /**
     * Reads back the engine {@link SafeDetails#withEngine(String)} recorded, so the window can name it in its own
     * language.
     *
     * @param details the rendered details, or {@code null}
     * @return the engine if the details name a known one, or empty if they do not
     */
    public static Optional<ServerEngine> in(final @Nullable String details) {
        if (details == null) {
            return Optional.empty();
        }
        final Matcher found = IN_DETAILS.matcher(details);
        if (!found.find()) {
            return Optional.empty();
        }
        final String name = found.group(1);
        return Arrays.stream(values())
                .filter(engine -> engine.name().equals(name))
                .findFirst();
    }
}
