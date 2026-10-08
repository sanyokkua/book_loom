package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;

/**
 * An ordered conversation sent to a {@link ChatModel}.
 *
 * @param messages the conversation in wire order; never null and defensively copied
 * @param temperature the sampling temperature for this call, or null when provider defaults should apply
 * @param responseFormat the requested structured response format, or null when no format is requested
 * @param reasoningEnabled null when the provider should use its default, false when supported reasoning output
 *     should be disabled
 * @param contextWindow the provider context-window size to request, or null when the provider default applies
 * @param expectedOutputTokens the expected completion length used to scale the request timeout, or null when not
 *     known
 * @param maxOutputTokens the hard cap on the completion length, or null when the provider should not be told one; a
 *     positive count when set
 * @param seed the sampling seed, or null for the provider's own; a retry after a timeout sets one so it does not
 *     replay the reply that stalled
 * @param callKind what the call is for, which chooses its timeout, or null when it is not a run's call (a
 *     verification probe)
 * @param sampling the sampling controls beyond temperature, or null when the server's defaults should apply
 */
public record ChatRequest(
        List<ChatMessage> messages,
        @Nullable Double temperature,
        @Nullable ResponseFormat responseFormat,
        @Nullable Boolean reasoningEnabled,
        @Nullable Integer contextWindow,
        @Nullable Integer expectedOutputTokens,
        @Nullable Integer maxOutputTokens,
        @Nullable Integer seed,
        @Nullable CallKind callKind,
        @Nullable SamplingParams sampling) {

    /**
     * Preserves the nine-argument construction form, with the server's own sampling.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     * @param temperature the sampling temperature for this call, or null when provider defaults should apply
     * @param responseFormat the requested structured response format, or null when no format is requested
     * @param reasoningEnabled null when the provider should use its default
     * @param contextWindow the provider context-window size to request, or null when the provider default applies
     * @param expectedOutputTokens the expected completion length used to scale the request timeout, or null
     * @param maxOutputTokens the hard cap on the completion length, or null
     * @param seed the sampling seed, or null
     * @param callKind what the call is for, or null
     */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable Boolean reasoningEnabled,
            @Nullable Integer contextWindow,
            @Nullable Integer expectedOutputTokens,
            @Nullable Integer maxOutputTokens,
            @Nullable Integer seed,
            @Nullable CallKind callKind) {
        this(
                messages,
                temperature,
                responseFormat,
                reasoningEnabled,
                contextWindow,
                expectedOutputTokens,
                maxOutputTokens,
                seed,
                callKind,
                null);
    }

    /**
     * Preserves the original message-only construction form, with no per-call settings.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     */
    public ChatRequest(List<ChatMessage> messages) {
        this(messages, null, null, null, null, null, null);
    }

    /**
     * Preserves the original per-call settings construction form without a reasoning control.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     * @param temperature the sampling temperature for this call, or null when provider defaults should apply
     * @param responseFormat the requested structured response format, or null when no format is requested
     */
    public ChatRequest(
            List<ChatMessage> messages, @Nullable Double temperature, @Nullable ResponseFormat responseFormat) {
        this(messages, temperature, responseFormat, null, null, null, null);
    }

    /**
     * Preserves the original four-argument construction form, with no context window or expected output hint.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     * @param temperature the sampling temperature for this call, or null when provider defaults should apply
     * @param responseFormat the requested structured response format, or null when no format is requested
     * @param reasoningEnabled null when the provider should use its default, false when supported reasoning output
     *     should be disabled
     */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable Boolean reasoningEnabled) {
        this(messages, temperature, responseFormat, reasoningEnabled, null, null, null);
    }

    /**
     * Preserves the six-argument construction form, with no output cap.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     * @param temperature the sampling temperature for this call, or null when provider defaults should apply
     * @param responseFormat the requested structured response format, or null when no format is requested
     * @param reasoningEnabled null when the provider should use its default, false when supported reasoning output
     *     should be disabled
     * @param contextWindow the provider context-window size to request, or null when the provider default applies
     * @param expectedOutputTokens the expected completion length used to scale the request timeout, or null when not
     *     known
     */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable Boolean reasoningEnabled,
            @Nullable Integer contextWindow,
            @Nullable Integer expectedOutputTokens) {
        this(messages, temperature, responseFormat, reasoningEnabled, contextWindow, expectedOutputTokens, null);
    }

    /**
     * Preserves the seven-argument construction form, with no seed and no call kind.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     * @param temperature the sampling temperature for this call, or null when provider defaults should apply
     * @param responseFormat the requested structured response format, or null when no format is requested
     * @param reasoningEnabled null when the provider should use its default, false when supported reasoning output
     *     should be disabled
     * @param contextWindow the provider context-window size to request, or null when the provider default applies
     * @param expectedOutputTokens the expected completion length used to scale the request timeout, or null when not
     *     known
     * @param maxOutputTokens the hard cap on the completion length, or null when the provider should not be told one
     */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable Boolean reasoningEnabled,
            @Nullable Integer contextWindow,
            @Nullable Integer expectedOutputTokens,
            @Nullable Integer maxOutputTokens) {
        this(
                messages,
                temperature,
                responseFormat,
                reasoningEnabled,
                contextWindow,
                expectedOutputTokens,
                maxOutputTokens,
                null,
                null,
                null);
    }

    /**
     * Keeps a request independent from the mutable collection supplied by its caller, and rejects a non-positive
     * context window, expected-output-token count or output cap.
     */
    public ChatRequest {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
        if (contextWindow != null && contextWindow <= 0) {
            throw new IllegalArgumentException("contextWindow must be positive: " + contextWindow);
        }
        if (expectedOutputTokens != null && expectedOutputTokens <= 0) {
            throw new IllegalArgumentException("expectedOutputTokens must be positive: " + expectedOutputTokens);
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive: " + maxOutputTokens);
        }
    }

    /**
     * Copies this request with its structured-response format cleared, for a provider that rejected it.
     *
     * @return a copy with {@code responseFormat} null and every other field unchanged
     */
    public ChatRequest withoutResponseFormat() {
        return new ChatRequest(
                messages,
                temperature,
                null,
                reasoningEnabled,
                contextWindow,
                expectedOutputTokens,
                maxOutputTokens,
                seed,
                callKind,
                sampling);
    }

    /**
     * Copies this request with its reasoning control cleared, for a provider that rejected it.
     *
     * @return a copy with {@code reasoningEnabled} null and every other field unchanged
     */
    public ChatRequest withoutReasoning() {
        return new ChatRequest(
                messages,
                temperature,
                responseFormat,
                null,
                contextWindow,
                expectedOutputTokens,
                maxOutputTokens,
                seed,
                callKind,
                sampling);
    }

    /**
     * Copies this request with a fixed sampling seed, so a call that must repeat its answer does.
     *
     * @param fixedSeed the seed to sample with
     * @return a copy with {@code seed} replaced and every other field unchanged
     */
    public ChatRequest withSeed(int fixedSeed) {
        return new ChatRequest(
                messages,
                temperature,
                responseFormat,
                reasoningEnabled,
                contextWindow,
                expectedOutputTokens,
                maxOutputTokens,
                fixedSeed,
                callKind,
                sampling);
    }

    /**
     * Copies this request sized to a run's window: the window is requested as the context and the output cap is
     * limited to what the window leaves for a reply.
     *
     * @param window the context window to request; positive
     * @param outputCapLimit the most tokens a reply may be capped at; positive
     * @return a copy with {@code contextWindow} replaced and {@code maxOutputTokens} no larger than the limit
     */
    public ChatRequest sizedTo(int window, int outputCapLimit) {
        return new ChatRequest(
                messages,
                temperature,
                responseFormat,
                reasoningEnabled,
                window,
                expectedOutputTokens,
                maxOutputTokens == null ? null : Math.min(maxOutputTokens, outputCapLimit),
                seed,
                callKind,
                sampling);
    }

    /**
     * Copies this request for a retry after a timeout, so the retry is not the identical request that stalled.
     *
     * @param retrySeed the seed the retry samples with
     * @param retryCap the retry's output cap, or null when the request had none; positive when set
     * @return a copy with {@code seed} and {@code maxOutputTokens} replaced and every other field unchanged
     */
    public ChatRequest forRetry(int retrySeed, @Nullable Integer retryCap) {
        return new ChatRequest(
                messages,
                temperature,
                responseFormat,
                reasoningEnabled,
                contextWindow,
                expectedOutputTokens,
                retryCap,
                retrySeed,
                callKind,
                sampling);
    }

    /**
     * Copies this request with the given sampling controls.
     *
     * @param params the controls to send, or null to leave the server's defaults in force
     * @return a copy with {@code sampling} replaced and every other field unchanged
     */
    public ChatRequest withSampling(@Nullable SamplingParams params) {
        return new ChatRequest(
                messages,
                temperature,
                responseFormat,
                reasoningEnabled,
                contextWindow,
                expectedOutputTokens,
                maxOutputTokens,
                seed,
                callKind,
                params);
    }

    /**
     * Returns the sampling controls with null standing for none, so a client reads each control without a guard.
     *
     * @return {@code sampling}, or {@link SamplingParams#NONE} when it is null
     */
    public SamplingParams samplingOrNone() {
        return sampling == null ? SamplingParams.NONE : sampling;
    }
}
