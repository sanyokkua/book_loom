package ua.bookloom.llm.engine;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.llm.ServerEngine;

/**
 * What one server engine reads on its OpenAI-compatible path: where the answer cap goes, how thinking is switched off,
 * the fields every call carries and whether any model id is answered. Computed from the stored engine on each call and
 * never stored, so a change of engine applies at once.
 *
 * @param capField which cap fields the engine honours
 * @param offSwitch the request field that switches the engine's thinking off
 * @param alwaysSend fields merged into every chat body; never null, empty when none
 * @param extraSamplers the neutral sampler values, by the engine's own field names, sent when the request carries no
 *     value of that sampler; never null, empty where the engine's defaults are neutral already
 * @param servesUnknownModel whether the engine answers an id it does not list with whatever model it has loaded
 * @param penaltyField the engine's name for the request's repeat penalty
 * @param endpointLimited whether a call carries only the parameters the model's endpoint lists, with its cap lowered
 *     to the endpoint's answer limit (a hosted gateway that routes a model to endpoints of differing abilities)
 */
public record EngineTraits(
        CapField capField,
        OffSwitch offSwitch,
        Map<String, Object> alwaysSend,
        Map<String, Object> extraSamplers,
        boolean servesUnknownModel,
        String penaltyField,
        boolean endpointLimited) {

    private static final String REPEAT_PENALTY = "repeat_penalty";
    private static final String REPETITION_PENALTY = "repetition_penalty";
    // These engines' own defaults are not neutral (a repeat penalty above 1, a top_k of 40), so each value is sent.
    private static final Map<String, Object> NEUTRAL_REPEAT_PENALTY = neutral(REPEAT_PENALTY);
    private static final Map<String, Object> NEUTRAL_REPETITION_PENALTY = neutral(REPETITION_PENALTY);
    private static final Map<String, Object> TOOLS_OFF = Map.of("enable_tools", false);
    // Only endpoints that honour every parameter sent are chosen, and no prompt is shortened by cutting its middle.
    private static final Map<String, Object> OPENROUTER_ALWAYS = Map.of(
            "provider",
            Map.of("require_parameters", true),
            "plugins",
            List.of(Map.of("id", "context-compression", "enabled", false)));

    /** Copies the always-sent fields so a caller cannot change them later. */
    public EngineTraits {
        Objects.requireNonNull(capField, "capField");
        Objects.requireNonNull(offSwitch, "offSwitch");
        Objects.requireNonNull(penaltyField, "penaltyField");
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
     * @return the engine's row; never null
     */
    public static EngineTraits of(final ServerEngine engine) {
        Objects.requireNonNull(engine, "engine");
        return switch (engine) {
            // Ollama's OpenAI path ignores max_completion_tokens, so a call capped that way would run uncapped.
            case OLLAMA -> local(CapField.MAX_TOKENS_ONLY, OffSwitch.REASONING_EFFORT, Map.of(), Map.of(), false);
            case LM_STUDIO ->
                local(CapField.EITHER, OffSwitch.REASONING_EFFORT, Map.of(), NEUTRAL_REPEAT_PENALTY, true);
            case LLAMA_CPP -> local(CapField.EITHER, OffSwitch.TEMPLATE_KWARG, Map.of(), NEUTRAL_REPEAT_PENALTY, false);
            case UNSLOTH ->
                local(CapField.EITHER, OffSwitch.ENABLE_THINKING, TOOLS_OFF, NEUTRAL_REPETITION_PENALTY, true);
            // An absent sampler is left out upstream, so the serving host's own default applies.
            case OPENROUTER ->
                new EngineTraits(
                        CapField.MAX_TOKENS_ONLY,
                        OffSwitch.REASONING_OBJECT,
                        OPENROUTER_ALWAYS,
                        Map.of(),
                        false,
                        REPETITION_PENALTY,
                        true);
            case GENERIC -> local(CapField.EITHER, OffSwitch.PROBED, Map.of(), Map.of(), false);
        };
    }

    // A server that runs the model itself: the penalty under its usual name, every parameter sent as asked.
    private static EngineTraits local(
            final CapField capField,
            final OffSwitch offSwitch,
            final Map<String, Object> alwaysSend,
            final Map<String, Object> extraSamplers,
            final boolean servesUnknownModel) {
        return new EngineTraits(
                capField, offSwitch, alwaysSend, extraSamplers, servesUnknownModel, REPEAT_PENALTY, false);
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
        /**
         * {@code reasoning_effort} held at {@code low}: a hosted model that refuses {@code none} and accepts the lowest
         * effort. Only a check stores it; no engine starts with it.
         */
        REASONING_EFFORT_LOW("reasoning_effort:low"),
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
