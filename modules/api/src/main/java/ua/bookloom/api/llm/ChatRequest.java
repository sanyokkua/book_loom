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
 * @param reasoningEnabled the reasoning level: before the model's profile is applied, the intent the call asks for;
 *     after it, the level the model is sent, or null when no reasoning control is sent
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
 * @param outputField the request field that carries the output cap, or null for {@code max_tokens}
 * @param source how much source text the call sends, which sizes a thinking model's reasoning reserve, or null when
 *     the call does not say
 * @param reasoningReserve the tokens added to the cap and the expected output for the model's reasoning, or null when
 *     the call is not sent to think; positive when set
 * @param keepLoaded whether the server is asked to keep the model loaded after the call: only a translation run's own
 *     calls ask it, so a long pause does not unload the model, and a test or a setup call leaves the server's own
 *     setting in force
 */
public record ChatRequest(
        List<ChatMessage> messages,
        @Nullable Double temperature,
        @Nullable ResponseFormat responseFormat,
        @Nullable ReasoningLevel reasoningEnabled,
        @Nullable Integer contextWindow,
        @Nullable Integer expectedOutputTokens,
        @Nullable Integer maxOutputTokens,
        @Nullable Integer seed,
        @Nullable CallKind callKind,
        @Nullable SamplingParams sampling,
        @Nullable OutputLengthField outputField,
        @Nullable SourceSize source,
        @Nullable Integer reasoningReserve,
        boolean keepLoaded) {

    /** Preserves the nine-argument construction form, with the server's own sampling. */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable ReasoningLevel reasoningEnabled,
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
                null,
                null,
                null,
                null,
                false);
    }

    /** Preserves the original message-only construction form, with no per-call settings and the reasoning intent Off. */
    public ChatRequest(List<ChatMessage> messages) {
        this(messages, null, null, ReasoningLevel.OFF, null, null, null);
    }

    /** Preserves the original per-call settings construction form without a reasoning control. */
    public ChatRequest(
            List<ChatMessage> messages, @Nullable Double temperature, @Nullable ResponseFormat responseFormat) {
        this(messages, temperature, responseFormat, null);
    }

    /** Preserves the original four-argument construction form, with no context window or expected output hint. */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable ReasoningLevel reasoningEnabled) {
        this(messages, temperature, responseFormat, reasoningEnabled, null, null, null);
    }

    /** Preserves the six-argument construction form, with no output cap. */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable ReasoningLevel reasoningEnabled,
            @Nullable Integer contextWindow,
            @Nullable Integer expectedOutputTokens) {
        this(messages, temperature, responseFormat, reasoningEnabled, contextWindow, expectedOutputTokens, null);
    }

    /** Preserves the seven-argument construction form, with no seed and no call kind. */
    public ChatRequest(
            List<ChatMessage> messages,
            @Nullable Double temperature,
            @Nullable ResponseFormat responseFormat,
            @Nullable ReasoningLevel reasoningEnabled,
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
        if (reasoningReserve != null && reasoningReserve <= 0) {
            throw new IllegalArgumentException("reasoningReserve must be positive: " + reasoningReserve);
        }
    }

    /**
     * Copies this request with the size of the source it sends, so a model that thinks gets room in proportion.
     *
     * @param size the source size, or null when the call does not say
     * @return a copy with {@code source} replaced and every other field unchanged
     */
    public ChatRequest withSource(@Nullable SourceSize size) {
        final Copy copy = new Copy(this);
        copy.source = size;
        return copy.build();
    }

    /**
     * Copies this request with the reasoning level the model is sent and the room its reasoning needs: the reserve is
     * added to the output cap (an uncapped request stays uncapped) and to the expected output, so the wait grows with
     * it.
     *
     * @param level the level sent, or null to send no reasoning control
     * @param reserve the tokens reserved for reasoning, or zero when the call does not think; zero or more
     * @return a copy with the level, the reserve, the cap and the expected output replaced
     */
    public ChatRequest withReasoning(@Nullable ReasoningLevel level, int reserve) {
        if (reserve < 0) {
            throw new IllegalArgumentException("reserve must not be negative: " + reserve);
        }
        final boolean reserved = reserve > 0;
        final Copy copy = new Copy(this);
        copy.reasoningEnabled = level;
        copy.reasoningReserve = reserved ? Integer.valueOf(reserve) : null;
        if (reserved) {
            copy.expectedOutputTokens = (expectedOutputTokens == null ? 0 : expectedOutputTokens) + reserve;
            copy.maxOutputTokens = maxOutputTokens == null ? null : maxOutputTokens + reserve;
        }
        return copy.build();
    }

    /**
     * Whether the model is expected to think on this call: room was reserved for its reasoning, or a thinking level is
     * sent.
     *
     * @return {@code true} if the call thinks, {@code false} otherwise
     */
    public boolean isThinking() {
        return reasoningReserve != null || (reasoningEnabled != null && reasoningEnabled.isThinking());
    }

    /**
     * Copies this request with its structured-response format cleared, for a provider that rejected it.
     *
     * @return a copy with {@code responseFormat} null and every other field unchanged
     */
    public ChatRequest withoutResponseFormat() {
        final Copy copy = new Copy(this);
        copy.responseFormat = null;
        return copy.build();
    }

    /**
     * Copies this request with its reasoning control cleared, for a provider that rejected it.
     *
     * @return a copy with {@code reasoningEnabled} null and every other field unchanged
     */
    public ChatRequest withoutReasoning() {
        final Copy copy = new Copy(this);
        copy.reasoningEnabled = null;
        return copy.build();
    }

    /**
     * Copies this request with a fixed sampling seed, so a call that must repeat its answer does.
     *
     * @param fixedSeed the seed to sample with
     * @return a copy with {@code seed} replaced and every other field unchanged
     */
    public ChatRequest withSeed(int fixedSeed) {
        final Copy copy = new Copy(this);
        copy.seed = fixedSeed;
        return copy.build();
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
        final Copy copy = new Copy(this);
        copy.contextWindow = window;
        copy.maxOutputTokens = maxOutputTokens == null ? null : Math.min(maxOutputTokens, outputCapLimit);
        return copy.build();
    }

    /**
     * Copies this request for a retry after a timeout, so the retry is not the identical request that stalled.
     *
     * @param retrySeed the seed the retry samples with
     * @param retryCap the retry's output cap, or null when the request had none; positive when set
     * @return a copy with {@code seed} and {@code maxOutputTokens} replaced and every other field unchanged
     */
    public ChatRequest forRetry(int retrySeed, @Nullable Integer retryCap) {
        final Copy copy = new Copy(this);
        copy.seed = retrySeed;
        copy.maxOutputTokens = retryCap;
        return copy.build();
    }

    /**
     * Copies this request with the given sampling controls.
     *
     * @param params the controls to send, or null to leave the server's defaults in force
     * @return a copy with {@code sampling} replaced and every other field unchanged
     */
    public ChatRequest withSampling(@Nullable SamplingParams params) {
        final Copy copy = new Copy(this);
        copy.sampling = params;
        return copy.build();
    }

    /**
     * Copies this request with the settings a model's own configuration decides.
     *
     * @param newTemperature the temperature to send, or null to send none
     * @param cap the output cap, or null for none; positive when set
     * @param field the field carrying the cap, or null for {@code max_tokens}
     * @return a copy with those three fields replaced and every other field unchanged
     */
    public ChatRequest withModelSettings(
            @Nullable Double newTemperature, @Nullable Integer cap, @Nullable OutputLengthField field) {
        final Copy copy = new Copy(this);
        copy.temperature = newTemperature;
        copy.maxOutputTokens = cap;
        copy.outputField = field;
        return copy.build();
    }

    /**
     * Copies this request as one of a translation run's own calls, so a server that unloads an idle model keeps it.
     *
     * @return a copy with {@code keepLoaded} set and every other field unchanged
     */
    public ChatRequest keptLoaded() {
        final Copy copy = new Copy(this);
        copy.keepLoaded = true;
        return copy.build();
    }

    /**
     * Returns the sampling controls with null standing for none, so a client reads each control without a guard.
     *
     * @return {@code sampling}, or {@link SamplingParams#NONE} when it is null
     */
    public SamplingParams samplingOrNone() {
        return sampling == null ? SamplingParams.NONE : sampling;
    }

    // One copy that changes only the named fields, so a copy method never repeats every component; it lives for one
    // copy method's call and is never shared.
    private static final class Copy {
        private List<ChatMessage> messages;
        private @Nullable Double temperature;
        private @Nullable ResponseFormat responseFormat;
        private @Nullable ReasoningLevel reasoningEnabled;
        private @Nullable Integer contextWindow;
        private @Nullable Integer expectedOutputTokens;
        private @Nullable Integer maxOutputTokens;
        private @Nullable Integer seed;
        private @Nullable CallKind callKind;
        private @Nullable SamplingParams sampling;
        private @Nullable OutputLengthField outputField;
        private @Nullable SourceSize source;
        private @Nullable Integer reasoningReserve;
        private boolean keepLoaded;

        Copy(final ChatRequest request) {
            messages = request.messages();
            temperature = request.temperature();
            responseFormat = request.responseFormat();
            reasoningEnabled = request.reasoningEnabled();
            contextWindow = request.contextWindow();
            expectedOutputTokens = request.expectedOutputTokens();
            maxOutputTokens = request.maxOutputTokens();
            seed = request.seed();
            callKind = request.callKind();
            sampling = request.sampling();
            outputField = request.outputField();
            source = request.source();
            reasoningReserve = request.reasoningReserve();
            keepLoaded = request.keepLoaded();
        }

        ChatRequest build() {
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
                    sampling,
                    outputField,
                    source,
                    reasoningReserve,
                    keepLoaded);
        }
    }
}
