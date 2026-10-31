package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a model's server said about it when the person last acted. Never stored: a server can say something else
 * tomorrow (design D20), so it is read again at each check and kept only in session memory. Not the Models tab's
 * view-model of the same name in {@code :ui}.
 *
 * @param architecture the weights' architecture as the server spells it ("qwen35"), or null
 * @param family the family the server names ("gemma4"), or null
 * @param format the weights format ("gguf", "safetensors", "mlx"), or null
 * @param quant the quant as the server reports it, or null
 * @param parameterSize the size as the server writes it ("9.7B"), or null
 * @param capabilities what the server says the model can do ("completion", "vision", "thinking"); never null, may be
 *     empty
 * @param thinkingOptions the thinking values the server allows ("on", "off", "low"); never null, may be empty
 * @param thinkingDefault the thinking value the server uses when none is sent, or null
 * @param renderer Ollama's prompt-template family for the model, or null; absent on a plain GGUF download
 * @param parser Ollama's reply parser for the model, or null
 * @param runner the program running the weights ("llamacpp", "mlx"), or null
 * @param type the server's own kind of model ("llm", "vlm", "embeddings"), or null
 * @param state whether the server has the model in memory ("loaded", "not-loaded"), or null
 * @param compatibility the file format family LM Studio reports, or null
 * @param contextLength the window a run should size against: the loaded one where the server fixes it at load, else
 *     the most the model supports; null when the server did not say; positive when set
 * @param loadedContext the window the model is loaded with now, or null when unknown or not loaded; positive when set
 */
public record ModelFacts(
        @Nullable String architecture,
        @Nullable String family,
        @Nullable String format,
        @Nullable String quant,
        @Nullable String parameterSize,
        List<String> capabilities,
        List<String> thinkingOptions,
        @Nullable String thinkingDefault,
        @Nullable String renderer,
        @Nullable String parser,
        @Nullable String runner,
        @Nullable String type,
        @Nullable String state,
        @Nullable String compatibility,
        @Nullable Integer contextLength,
        @Nullable Integer loadedContext) {

    private static final String COMPLETION = "completion";
    private static final String EMBEDDINGS = "embeddings";
    private static final String EMBEDDING = "embedding";

    public ModelFacts {
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(thinkingOptions, "thinkingOptions");
        capabilities = List.copyOf(capabilities);
        thinkingOptions = List.copyOf(thinkingOptions);
        requirePositive(contextLength, "contextLength");
        requirePositive(loadedContext, "loadedContext");
    }

    /**
     * A model the server said nothing about; an answer, not an error.
     *
     * @return facts with every field empty
     */
    public static ModelFacts unknown() {
        return new ModelFacts(
                null, null, null, null, null, List.of(), List.of(), null, null, null, null, null, null, null, null,
                null);
    }

    /**
     * Copies these facts without the window the model is loaded with now, for a client that sets the window on every
     * request (Ollama's native API): what is loaded then says nothing about the window the next call gets.
     *
     * @return a copy with {@code loadedContext} null and every other fact unchanged
     */
    public ModelFacts withoutLoadedContext() {
        return new ModelFacts(
                architecture,
                family,
                format,
                quant,
                parameterSize,
                capabilities,
                thinkingOptions,
                thinkingDefault,
                renderer,
                parser,
                runner,
                type,
                state,
                compatibility,
                contextLength,
                null);
    }

    /**
     * Copies these facts with the thinking values the server's template is known to read.
     *
     * @param options the values; never null
     * @return a copy with {@code thinkingOptions} replaced and every other fact unchanged
     */
    public ModelFacts withThinkingOptions(final List<String> options) {
        return new ModelFacts(
                architecture,
                family,
                format,
                quant,
                parameterSize,
                capabilities,
                options,
                thinkingDefault,
                renderer,
                parser,
                runner,
                type,
                state,
                compatibility,
                contextLength,
                loadedContext);
    }

    /**
     * What kind of model the server's own words show it to be.
     *
     * @return {@link ModelKind#EMBEDDING} when its type is embeddings or its capabilities are given but lack
     *     completion, else {@link ModelKind#LLM} (a vision model is still a chat model)
     */
    public ModelKind kind() {
        if (EMBEDDINGS.equals(type) || EMBEDDING.equals(type)) {
            return ModelKind.EMBEDDING;
        }
        return !capabilities.isEmpty() && !capabilities.contains(COMPLETION) ? ModelKind.EMBEDDING : ModelKind.LLM;
    }

    private static void requirePositive(final @Nullable Integer value, final String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
