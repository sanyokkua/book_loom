package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingParams;
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
    private static final String REPEAT_PENALTY = "repeat_penalty";
    // The body itself, never a parameter an endpoint lists.
    private static final Set<String> BODY_FIELDS = Set.of("model", "messages", "stream");

    private final ProviderConfig config;
    private final EngineTraits traits;
    private final EngineFacts facts;
    private final Function<String, ProbeVerdicts> verdicts;
    private final boolean checking;
    private final @Nullable OffSwitch forced;

    /**
     * Binds the shaping to one provider.
     *
     * @param verdicts the stored check verdicts of a model id, read on each call that needs them
     * @param checking whether the calls are the Test's checks, which send what they ask for
     */
    EngineShaping(
            ProviderConfig config, EngineFacts facts, Function<String, ProbeVerdicts> verdicts, boolean checking) {
        this(config, facts, verdicts, checking, null);
    }

    private EngineShaping(
            ProviderConfig config,
            EngineFacts facts,
            Function<String, ProbeVerdicts> verdicts,
            boolean checking,
            @Nullable OffSwitch forced) {
        this.config = Objects.requireNonNull(config, "config");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.verdicts = Objects.requireNonNull(verdicts, "verdicts");
        this.checking = checking;
        this.forced = forced;
        this.traits = EngineTraits.of(config.effectiveEngine());
    }

    EngineShaping forChecks() {
        return new EngineShaping(config, facts, verdicts, true, null);
    }

    /**
     * The Advanced features check's shaping: it sends what it asks for and, when a switch is named, that switch in
     * place of the engine's, since trying each switch in turn is how the thinking check finds the one that works.
     *
     * @param offSwitch the switch to send, or null for the engine's own
     */
    EngineShaping forCheck(@Nullable OffSwitch offSwitch) {
        return new EngineShaping(config, facts, verdicts, true, offSwitch);
    }

    /** Whether only {@code max_tokens} limits the answer, whatever cap field the model's settings chose. */
    boolean isMaxTokensOnly() {
        return traits.capField() == EngineTraits.CapField.MAX_TOKENS_ONLY;
    }

    /**
     * The fields the engine needs on every call: its always-sent fields and the neutral value of each sampler the
     * request leaves unset.
     */
    Map<String, Object> everyCallFields(SamplingParams sampling) {
        final Map<String, Object> fields = new HashMap<>(traits.alwaysSend());
        traits.extraSamplers().forEach((name, value) -> {
            if (!isCarried(name, sampling)) {
                fields.put(name, value);
            }
        });
        log.debug("Fields sent on every call provider={} fields={}", config.id(), fields.keySet());
        return fields;
    }

    // The request's own value wins, and so does a refusal; the penalty counts under either engine's name.
    private static boolean isCarried(String field, SamplingParams sampling) {
        final boolean stated =
                switch (field) {
                    case "top_p" -> sampling.topP() != null;
                    case "top_k" -> sampling.topK() != null;
                    case "min_p" -> sampling.minP() != null;
                    default -> sampling.repeatPenalty() != null;
                };
        final String wireName = field.equals("repetition_penalty") ? "repeat_penalty" : field;
        return stated || sampling.withheld().contains(wireName);
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
        // The lowest level of a model that always reasons (gpt-oss, muse-glimmer) is a reasoning effort on every
        // engine's OpenAI layer; on llama-server "none" or a template switch would erase it and fall back to medium.
        if (level == ReasoningLevel.LOW && forced == null && traits.offSwitch() != OffSwitch.REASONING_OBJECT) {
            log.debug("Reasoning sent as the lowest effort provider={} model={}", config.id(), modelId);
            return ReasoningFields.effort(REASONING_LOW);
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
            case REASONING_OBJECT -> new ReasoningFields(null, null, null, reasoningObject(modelId, level, thinks));
            case PROBED -> ReasoningFields.NONE;
        };
    }

    // The engine's own object is chosen from the catalog read when the person acted; a check names the plain switch.
    private @Nullable Map<String, Object> reasoningObject(String modelId, ReasoningLevel level, boolean thinks) {
        if (forced != null || traits.offSwitch() != OffSwitch.REASONING_OBJECT) {
            return Map.of(ENABLED, thinks);
        }
        return OpenRouterReasoning.object(facts.lastRead(config.id(), modelId).orElse(null), level);
    }

    /**
     * The answer cap lowered to the endpoint's answer limit, on an engine whose endpoints set one.
     *
     * @param cap the cap the request asks for, or null for none
     * @return the cap sent, or null for none
     */
    @Nullable
    Integer endpointCap(String modelId, @Nullable Integer cap) {
        if (!traits.endpointLimited() || cap == null) {
            return cap;
        }
        final Integer limit = facts.lastRead(config.id(), modelId)
                .map(ModelFacts::maxCompletionTokens)
                .orElse(null);
        final boolean lowered = limit != null && cap > limit;
        log.debug(
                "Endpoint answer limit provider={} model={} cap={} limit={} lowered={}",
                config.id(),
                modelId,
                cap,
                limit,
                lowered);
        return lowered ? limit : cap;
    }

    /** Whether a written body is reshaped by {@link #toEndpoint} before it is sent. */
    boolean reshapesBody() {
        return traits.endpointLimited() || !REPEAT_PENALTY.equals(traits.penaltyField());
    }

    /**
     * Gives the body the engine's penalty name and, on an engine whose endpoints list what they honour, removes every
     * parameter the model's endpoint does not list; with nothing read, every parameter goes as asked.
     */
    void toEndpoint(String modelId, ObjectNode body) {
        final String penalty = traits.penaltyField();
        if (!REPEAT_PENALTY.equals(penalty) && body.has(REPEAT_PENALTY)) {
            body.set(penalty, body.remove(REPEAT_PENALTY));
        }
        if (!traits.endpointLimited()) {
            return;
        }
        final List<String> listed = facts.lastRead(config.id(), modelId)
                .map(ModelFacts::supportedParameters)
                .orElse(List.of());
        if (listed.isEmpty()) {
            log.debug("Endpoint parameters unknown, all sent provider={} model={}", config.id(), modelId);
            return;
        }
        final List<String> dropped = new ArrayList<>();
        body.fieldNames().forEachRemaining(name -> {
            if (!BODY_FIELDS.contains(name) && !traits.alwaysSend().containsKey(name) && !listed.contains(name)) {
                dropped.add(name);
            }
        });
        body.remove(dropped);
        log.debug("Endpoint parameters provider={} model={} dropped={}", config.id(), modelId, dropped);
    }

    // The field this engine switches thinking with for this model, or null when none can be sent.
    private @Nullable OffSwitch resolved(String modelId) {
        if (forced != null) {
            log.debug("Reasoning switch named by a check provider={} model={} field={}", config.id(), modelId, forced);
            return forced;
        }
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
