package ua.bookloom.llm.client.openai;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.engine.EngineTraits;
import ua.bookloom.llm.engine.EngineTraits.OffSwitch;

/**
 * Chooses the request fields of one provider's engine: which cap field is honoured, which field switches thinking, and
 * — on a server no engine row describes — only what a stored check verdict proved. The Test's own checks send what
 * they ask for, since a check is how a verdict is earned.
 */
@Slf4j
final class EngineShaping {

    // The reasoning_effort values: off, a call that thinks (the server's usual middle level), one that thinks a little.
    private static final String REASONING_OFF = "none";
    private static final String REASONING_ON = "medium";
    private static final String REASONING_LOW = "low";

    private static final String ENABLE_THINKING = "enable_thinking";
    private static final String ENABLED = "enabled";

    private final ProviderConfig config;
    private final EngineTraits traits;
    private final EngineFacts facts;
    private final Function<String, ProbeVerdicts> verdicts;
    private final boolean checking;

    /**
     * Binds the shaping to one provider.
     *
     * @param verdicts the stored check verdicts of a model id, read on each call that needs them
     * @param checking whether the calls are the Test's checks, which send what they ask for
     */
    EngineShaping(
            ProviderConfig config, EngineFacts facts, Function<String, ProbeVerdicts> verdicts, boolean checking) {
        this.config = Objects.requireNonNull(config, "config");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.verdicts = Objects.requireNonNull(verdicts, "verdicts");
        this.checking = checking;
        this.traits = EngineTraits.of(config.effectiveEngine(), config.engineVersion());
    }

    EngineShaping forChecks() {
        return new EngineShaping(config, facts, verdicts, true);
    }

    /** Whether only {@code max_tokens} limits the answer, whatever cap field the model's settings chose. */
    boolean isMaxTokensOnly() {
        return traits.capField() == EngineTraits.CapField.MAX_TOKENS_ONLY;
    }

    Map<String, Object> alwaysSend() {
        return traits.alwaysSend();
    }

    /**
     * The request with what an unknown server has not been shown to honour taken out: the answer schema until its
     * verdict is honoured, the reasoning control until a thinking switch is. The prompt's answer contract stands in.
     */
    ChatRequest proved(String modelId, ChatRequest request) {
        final boolean carries = request.responseFormat() != null || request.reasoningEnabled() != null;
        if (traits.offSwitch() != OffSwitch.PROBED || checking || !carries) {
            return request;
        }
        final ProbeVerdicts found = verdicts.apply(modelId);
        final boolean schema = request.responseFormat() != null && !isHonoured(found.answerSchema());
        final boolean reasoning =
                request.reasoningEnabled() != null && provedSwitch(found).isEmpty();
        log.debug(
                "Unproved fields left out provider={} model={} engine={} schema={} reasoning={}",
                config.id(),
                modelId,
                config.effectiveEngine(),
                schema,
                reasoning);
        final ChatRequest withoutSchema = schema ? request.withoutResponseFormat() : request;
        return reasoning ? withoutSchema.withoutReasoning() : withoutSchema;
    }

    ReasoningFields reasoning(String modelId, @Nullable ReasoningLevel level) {
        if (level == null) {
            return ReasoningFields.NONE;
        }
        // A gpt-oss model ignores "off" and spends the whole cap reasoning, so off is asked as its lowest level.
        if (EngineTraits.reasonsAtLowest(modelId)) {
            log.debug("Reasoning asked by effort for a gpt-oss id model={} level={}", modelId, level);
            return ReasoningFields.effort(level == ReasoningLevel.OFF ? REASONING_LOW : effort(level));
        }
        final @Nullable OffSwitch field = resolved(modelId);
        log.debug(
                "Reasoning switch provider={} model={} engine={} level={} field={}",
                config.id(),
                modelId,
                config.effectiveEngine(),
                level,
                field == null ? "none" : field.wireName());
        if (field == null) {
            return ReasoningFields.NONE;
        }
        final boolean thinks = level.isThinking();
        return switch (field) {
            case REASONING_EFFORT -> ReasoningFields.effort(effort(level));
            case TEMPLATE_KWARG -> new ReasoningFields(null, Map.of(ENABLE_THINKING, thinks), null, null);
            case ENABLE_THINKING -> new ReasoningFields(null, null, thinks, null);
            case REASONING_OBJECT -> new ReasoningFields(null, null, null, Map.of(ENABLED, thinks));
            case PROBED -> ReasoningFields.NONE;
        };
    }

    // The field this engine switches thinking with for this model, or null when none can be sent.
    private @Nullable OffSwitch resolved(String modelId) {
        return switch (traits.offSwitch()) {
            case TEMPLATE_KWARG -> facts.readsEnableThinking(config, modelId) ? OffSwitch.TEMPLATE_KWARG : null;
            case PROBED ->
                checking
                        ? OffSwitch.REASONING_EFFORT
                        : provedSwitch(verdicts.apply(modelId)).orElse(null);
            case REASONING_EFFORT, ENABLE_THINKING, REASONING_OBJECT -> traits.offSwitch();
        };
    }

    private static Optional<OffSwitch> provedSwitch(ProbeVerdicts found) {
        final ProbeVerdicts.ThinkingVerdict thinking = found.thinkingSwitch();
        if (thinking == null || thinking.switchName() == null || !isHonoured(thinking.verdict())) {
            return Optional.empty();
        }
        return OffSwitch.named(Objects.requireNonNull(thinking.switchName(), "switchName"));
    }

    private static boolean isHonoured(ProbeVerdicts.@Nullable Verdict verdict) {
        return verdict != null && verdict.outcome() == ProbeVerdicts.Outcome.HONOURED;
    }

    private static String effort(ReasoningLevel level) {
        return switch (level) {
            case ON -> REASONING_ON;
            case LOW -> REASONING_LOW;
            case OFF -> REASONING_OFF;
        };
    }

    /**
     * The reasoning fields of one body; each is left out when null.
     *
     * @param effort {@code reasoning_effort}
     * @param kwargs {@code chat_template_kwargs}
     * @param enableThinking the top-level {@code enable_thinking}
     * @param reasoning the {@code reasoning} object
     */
    record ReasoningFields(
            @Nullable String effort,
            @Nullable Map<String, Object> kwargs,
            @Nullable Boolean enableThinking,
            @Nullable Map<String, Object> reasoning) {

        static final ReasoningFields NONE = new ReasoningFields(null, null, null, null);

        static ReasoningFields effort(String value) {
            return new ReasoningFields(value, null, null, null);
        }
    }
}
