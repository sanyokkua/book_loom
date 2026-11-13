package ua.bookloom.api.llm;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Token counts and generation time a provider reported for one {@link ChatResponse}.
 *
 * @param prompt the number of prompt tokens the provider counted, or null when not reported
 * @param completion the number of completion tokens the provider counted, or null when not reported
 * @param generation the wall-clock time the provider spent generating the completion, or null when not reported
 * @param promptEval the time the provider spent evaluating the prompt before the first token, or null when not
 *     reported
 * @param cachedPrompt how many prompt tokens the provider says it served from its cache, or null when not reported
 * @param load the time the provider spent loading the model before it could answer, or null when not reported; only
 *     Ollama reports it, and a slow first call is explained by it
 */
public record TokenUsage(
        @Nullable Integer prompt,
        @Nullable Integer completion,
        @Nullable Duration generation,
        @Nullable Duration promptEval,
        @Nullable Integer cachedPrompt,
        @Nullable Duration load) {

    /**
     * Builds a usage with no load time, as every provider but Ollama reports.
     *
     * @param prompt the number of prompt tokens the provider counted, or null when not reported
     * @param completion the number of completion tokens the provider counted, or null when not reported
     * @param generation the time spent generating the completion, or null when not reported
     * @param promptEval the time spent evaluating the prompt, or null when not reported
     * @param cachedPrompt the prompt tokens served from the cache, or null when not reported
     */
    public TokenUsage(
            @Nullable Integer prompt,
            @Nullable Integer completion,
            @Nullable Duration generation,
            @Nullable Duration promptEval,
            @Nullable Integer cachedPrompt) {
        this(prompt, completion, generation, promptEval, cachedPrompt, null);
    }

    /**
     * Builds a usage with no prompt-evaluation time and no cache figure, as a provider that reports only counts does.
     *
     * @param prompt the number of prompt tokens the provider counted, or null when not reported
     * @param completion the number of completion tokens the provider counted, or null when not reported
     * @param generation the time spent generating the completion, or null when not reported
     */
    public TokenUsage(@Nullable Integer prompt, @Nullable Integer completion, @Nullable Duration generation) {
        this(prompt, completion, generation, null, null, null);
    }

    /**
     * Rejects a negative reported count and a reply that reports nothing at all: a response with no figures
     * carries no {@link TokenUsage}, never an empty one.
     */
    public TokenUsage {
        if (prompt != null && prompt < 0) {
            throw new IllegalArgumentException("prompt must not be negative: " + prompt);
        }
        if (completion != null && completion < 0) {
            throw new IllegalArgumentException("completion must not be negative: " + completion);
        }
        if (cachedPrompt != null && cachedPrompt < 0) {
            throw new IllegalArgumentException("cachedPrompt must not be negative: " + cachedPrompt);
        }
        if (prompt == null && completion == null && generation == null && promptEval == null) {
            throw new IllegalArgumentException(
                    "at least one of prompt, completion, generation or promptEval must be reported");
        }
    }
}
