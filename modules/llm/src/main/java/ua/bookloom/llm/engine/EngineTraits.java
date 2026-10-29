package ua.bookloom.llm.engine;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ServerEngine;

/**
 * What one server engine reads on its OpenAI-compatible path: where the answer cap goes, how thinking is switched off,
 * the fields every call carries and whether any model id is answered. Computed from the stored engine on each call and
 * never stored, so a change of engine applies at once; OpenRouter speaks the generic row until it gets its own.
 *
 * @param capField which cap fields the engine honours
 * @param offSwitch the request field that switches the engine's thinking off
 * @param alwaysSend fields merged into every chat body; never null, empty when none
 * @param extraSamplers the neutral sampler values, by the engine's own field names, sent when the request carries no
 *     value of that sampler; never null, empty where the engine's defaults are neutral already
 * @param servesUnknownModel whether the engine answers an id it does not list with whatever model it has loaded
 */
public record EngineTraits(
        CapField capField,
        OffSwitch offSwitch,
        Map<String, Object> alwaysSend,
        Map<String, Object> extraSamplers,
        boolean servesUnknownModel) {

    private static final String GPT_OSS = "gpt-oss";
    // These engines' own defaults are not neutral (a repeat penalty above 1, a top_k of 40), so each value is sent.
    private static final Map<String, Object> NEUTRAL_REPEAT_PENALTY = neutral("repeat_penalty");
    private static final Map<String, Object> NEUTRAL_REPETITION_PENALTY = neutral("repetition_penalty");
    private static final Map<String, Object> TOOLS_OFF = Map.of("enable_tools", false);

    /** Copies the always-sent fields so a caller cannot change them later. */
    public EngineTraits {
        Objects.requireNonNull(capField, "capField");
        Objects.requireNonNull(offSwitch, "offSwitch");
        alwaysSend = Map.copyOf(Objects.requireNonNull(alwaysSend, "alwaysSend"));
        extraSamplers = Map.copyOf(Objects.requireNonNull(extraSamplers, "extraSamplers"));
    }

    private static Map<String, Object> neutral(final String penaltyField) {
        return Map.of("top_p", 1.0, "top_k", 0, "min_p", 0.0, penaltyField, 1.0);
    }

    /**
     * The traits of an engine.
     *
     * @param engine the provider's effective engine; non-null
     * @param version the engine's reported version, or null when unknown; no row depends on it yet
     * @return the engine's row; never null
     */
    public static EngineTraits of(final ServerEngine engine, final @Nullable String version) {
        Objects.requireNonNull(engine, "engine");
        return switch (engine) {
            // Ollama's OpenAI path ignores max_completion_tokens, so a call capped that way would run uncapped.
            case OLLAMA ->
                new EngineTraits(CapField.MAX_TOKENS_ONLY, OffSwitch.REASONING_EFFORT, Map.of(), Map.of(), false);
            case LM_STUDIO ->
                new EngineTraits(CapField.EITHER, OffSwitch.REASONING_EFFORT, Map.of(), NEUTRAL_REPEAT_PENALTY, true);
            case LLAMA_CPP ->
                new EngineTraits(CapField.EITHER, OffSwitch.TEMPLATE_KWARG, Map.of(), NEUTRAL_REPEAT_PENALTY, false);
            case UNSLOTH ->
                new EngineTraits(
                        CapField.EITHER, OffSwitch.ENABLE_THINKING, TOOLS_OFF, NEUTRAL_REPETITION_PENALTY, true);
            case OPENROUTER, GENERIC -> new EngineTraits(CapField.EITHER, OffSwitch.PROBED, Map.of(), Map.of(), false);
        };
    }

    /**
     * Whether a model reasons even when asked not to, so "off" is asked of it as its lowest level. A stopgap read from
     * the model's name until the model profile decides it.
     *
     * @param modelId the model id; non-null
     * @return {@code true} if the id names a gpt-oss model, {@code false} otherwise
     */
    public static boolean reasonsAtLowest(final String modelId) {
        return Objects.requireNonNull(modelId, "modelId")
                .toLowerCase(Locale.ROOT)
                .contains(GPT_OSS);
    }

    /** Which cap fields an engine honours. */
    public enum CapField {
        /** Only {@code max_tokens} limits the answer; the stored choice of {@code max_completion_tokens} is not used. */
        MAX_TOKENS_ONLY,
        /** Either field limits the answer, so the model's stored choice is sent. */
        EITHER
    }

    /** The request field that switches an engine's thinking. */
    public enum OffSwitch {
        /** {@code reasoning_effort}: {@code none}, {@code low} or {@code medium}. */
        REASONING_EFFORT("reasoning_effort"),
        /** {@code chat_template_kwargs.enable_thinking}, honoured only when the chat template reads that variable. */
        TEMPLATE_KWARG("chat_template_kwargs.enable_thinking"),
        /** Unsloth's top-level {@code enable_thinking}. */
        ENABLE_THINKING("enable_thinking"),
        /** OpenRouter's {@code reasoning} object. */
        REASONING_OBJECT("reasoning"),
        /** None is known; the field a check proved is used, and none before it. */
        PROBED("probed");

        private final String wireName;

        OffSwitch(final String wireName) {
            this.wireName = wireName;
        }

        /**
         * The name a check stores for the switch that worked.
         *
         * @return the field's name on the wire; never null
         */
        public String wireName() {
            return wireName;
        }

        /**
         * The switch a stored check verdict names.
         *
         * @param name the stored switch name; non-null
         * @return the switch if the name is one a request can carry, or empty otherwise
         */
        public static Optional<OffSwitch> named(final String name) {
            Objects.requireNonNull(name, "name");
            return Arrays.stream(values())
                    .filter(candidate -> candidate != PROBED && candidate.wireName.equals(name))
                    .findFirst();
        }
    }
}
