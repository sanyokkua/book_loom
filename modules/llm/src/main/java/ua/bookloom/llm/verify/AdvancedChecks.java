package ua.bookloom.llm.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProbeVerdicts.Outcome;
import ua.bookloom.api.llm.ProbeVerdicts.ThinkingVerdict;
import ua.bookloom.api.llm.ProbeVerdicts.Verdict;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.llm.engine.EngineTraits;
import ua.bookloom.llm.engine.EngineTraits.OffSwitch;
import ua.bookloom.llm.provider.CheckReply;
import ua.bookloom.llm.provider.ProviderCallResult;

/**
 * The Advanced features checks of one model: short calls with invented text that find out what the server honours.
 * The answer schema and the thinking switch are checked on every engine; the extra samplers and the cap field only on
 * a server no engine row describes, since a recognised engine's row already says. One instance runs once.
 */
@Slf4j
final class AdvancedChecks {

    /** Where a verdict found by these checks comes from. */
    static final String SOURCE = "test";

    /** The cap of every check but the cap check's own. */
    static final int OUTPUT_CAP = 64;

    /** The cap the cap check asks for. */
    static final int CAP_PROBE = 16;

    // A server counts its stop token, and some round a limit up, so "about 16" allows a few more.
    private static final int CAP_SLACK = 4;
    private static final double HOT_TEMPERATURE = 1.5;
    private static final int GREEDY = 1;
    private static final String SCHEMA_KEY = "k";
    private static final String SCHEMA_VALUE = "ZX-91";
    private static final String NATIVE_SWITCH = "think";
    private static final Set<ServerEngine> UNDESCRIBED = EnumSet.of(ServerEngine.GENERIC, ServerEngine.OPENROUTER);

    private static final ResponseFormat SCHEMA = new ResponseFormat("advanced_check", """
            {"type":"object","properties":{"k":{"enum":["ZX-91"]}},"required":["k"],"additionalProperties":false}
            """);
    private static final String SCHEMA_PROMPT = "Return a JSON object whose field k holds any short product code.";
    private static final String THINK_PROMPT = "Think step by step: what is 17 plus 25? Answer with the number.";
    private static final String SAMPLE_PROMPT = "Write one short sentence about a lighthouse on a quiet coast.";
    private static final String COUNT_PROMPT = "Count from 1 to 100, separated by spaces.";

    private final ProviderConfig config;
    private final ObjectMapper mapper;
    private final Sender sender;
    private final Instant now;
    private @Nullable AppError stopped;

    /**
     * Binds the checks to one model.
     *
     * @param sender sends one check through the gate
     * @param now the time every verdict of this run carries
     */
    AdvancedChecks(final ProviderConfig config, final ObjectMapper mapper, final Sender sender, final Instant now) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.now = Objects.requireNonNull(now, "now");
    }

    /**
     * Runs the checks in order, stopping at a failure that is not the server refusing a field.
     *
     * @return the verdicts found, null for a check that did not run or could not tell; the cap field the cap check
     *     chose; and the failure that stopped the checks, or null when they all ran
     */
    Findings run() {
        final boolean all = UNDESCRIBED.contains(config.effectiveEngine());
        log.debug(
                "Advanced checks started provider={} engine={} kind={} checks={}",
                config.id(),
                config.effectiveEngine(),
                config.kind(),
                all ? 4 : 2);
        final Verdict schema = schema();
        final ThinkingVerdict thinking = stopped == null ? thinking() : null;
        final Verdict samplers = all && stopped == null ? samplers() : null;
        final CapFinding cap = all && stopped == null ? cap() : new CapFinding(null, null);
        return new Findings(new ProbeVerdicts(schema, thinking, samplers, cap.verdict()), cap.field(), stopped);
    }

    // Honoured only when the reply is exactly the one value the schema allows; a free-text 200 is ignored and is never
    // asked again as json_object, since that would prove nothing about the schema.
    private @Nullable Verdict schema() {
        final ProviderCallResult<CheckReply> call = send(request(SCHEMA_PROMPT, SCHEMA, null, null, null), null);
        final CheckReply reply = call.result().data();
        if (reply == null) {
            return verdictOf(refusedOrStop("schema", call));
        }
        final boolean exact = isExactlyTheValue(reply.response().content());
        log.debug("Advanced schema check provider={} exact={}", config.id(), exact);
        return verdict(exact ? Outcome.HONOURED : Outcome.IGNORED);
    }

    private @Nullable ThinkingVerdict thinking() {
        if (config.kind() == ProviderKind.OLLAMA) {
            return nativeThinking();
        }
        boolean ignored = false;
        for (final OffSwitch offSwitch : switches()) {
            final ProviderCallResult<CheckReply> call =
                    send(request(THINK_PROMPT, null, ReasoningLevel.OFF, null, null), offSwitch);
            final CheckReply reply = call.result().data();
            final boolean works = reply != null && reply.reasoning() != CheckReply.Reasoning.SHOWN;
            log.debug("Advanced thinking check provider={} switch={} works={}", config.id(), offSwitch, works);
            if (works) {
                return new ThinkingVerdict(offSwitch.wireName(), verdict(Outcome.HONOURED));
            }
            if (reply != null) {
                ignored = true;
                continue;
            }
            // An empty answer under "off" is a reply the reasoning used up: the switch was accepted and did nothing.
            final Outcome failed = refusedOrStop("thinking", call);
            if (stopped != null) {
                return null;
            }
            ignored = ignored || failed == null;
        }
        return new ThinkingVerdict(null, verdict(ignored ? Outcome.IGNORED : Outcome.REFUSED));
    }

    // Ollama's own API has one switch, think, which its client always sends; the check says whether it was accepted.
    private @Nullable ThinkingVerdict nativeThinking() {
        final ProviderCallResult<CheckReply> call =
                send(request(THINK_PROMPT, null, ReasoningLevel.OFF, null, null), null);
        if (call.result().isOk()) {
            return new ThinkingVerdict(NATIVE_SWITCH, verdict(Outcome.HONOURED));
        }
        final Outcome outcome = refusedOrStop("thinking", call);
        return outcome == null ? null : new ThinkingVerdict(null, verdict(outcome));
    }

    // The engine's own switch first, then each switch a server of unknown make may read.
    private List<OffSwitch> switches() {
        final List<OffSwitch> order = new ArrayList<>();
        final OffSwitch own = EngineTraits.of(config.effectiveEngine(), config.engineVersion())
                .offSwitch();
        if (own != OffSwitch.PROBED) {
            order.add(own);
        }
        for (final OffSwitch next :
                List.of(OffSwitch.REASONING_EFFORT, OffSwitch.TEMPLATE_KWARG, OffSwitch.REASONING_OBJECT)) {
            if (!order.contains(next)) {
                order.add(next);
            }
        }
        return order;
    }

    // At a hot temperature two replies differ unless top_k 1 is honoured and the model picks its likeliest token.
    private @Nullable Verdict samplers() {
        final SamplingParams greedy = new SamplingParams(null, GREEDY, null, null);
        final ProviderCallResult<CheckReply> first = send(request(SAMPLE_PROMPT, null, null, greedy, null), null);
        if (first.result().isErr()) {
            return verdictOf(refusedOrStop("samplers", first));
        }
        final ProviderCallResult<CheckReply> second = send(request(SAMPLE_PROMPT, null, null, greedy, null), null);
        if (second.result().isErr()) {
            return verdictOf(refusedOrStop("samplers", second));
        }
        final boolean same = content(first).equals(content(second));
        log.debug("Advanced samplers check provider={} identical={}", config.id(), same);
        return verdict(same ? Outcome.HONOURED : Outcome.IGNORED);
    }

    // The field that stops the count near 16 tokens is the one later calls send; max_tokens is tried when the other
    // field does not limit the answer.
    private CapFinding cap() {
        final ProviderCallResult<CheckReply> completion = capCall(OutputLengthField.MAX_COMPLETION_TOKENS);
        final CheckReply reply = completion.result().data();
        if (reply != null && isLimited(reply.response())) {
            return new CapFinding(verdict(Outcome.HONOURED), OutputLengthField.MAX_COMPLETION_TOKENS);
        }
        final Outcome outcome = reply != null ? Outcome.IGNORED : refusedOrStop("cap", completion);
        if (outcome == null) {
            return new CapFinding(null, null);
        }
        final CheckReply tokens = capCall(OutputLengthField.MAX_TOKENS).result().data();
        final boolean limited = tokens != null && isLimited(tokens.response());
        log.debug(
                "Advanced cap check provider={} completionField={} maxTokensLimits={}", config.id(), outcome, limited);
        return new CapFinding(verdict(outcome), limited ? OutputLengthField.MAX_TOKENS : null);
    }

    private ProviderCallResult<CheckReply> capCall(final OutputLengthField field) {
        return send(request(COUNT_PROMPT, null, null, null, field), null);
    }

    private static boolean isLimited(final ChatResponse response) {
        final TokenUsage usage = response.usage();
        final Integer completion = usage == null ? null : usage.completion();
        return response.finishReason() == FinishReason.LENGTH
                && (completion == null || completion <= CAP_PROBE + CAP_SLACK);
    }

    private boolean isExactlyTheValue(final String content) {
        try {
            final JsonNode node = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(content.strip());
            return node != null
                    && node.isObject()
                    && node.size() == 1
                    && SCHEMA_VALUE.equals(node.path(SCHEMA_KEY).asText(null));
        } catch (JsonProcessingException notJson) {
            return false;
        }
    }

    // A refused field is a verdict; an empty answer says nothing; any other failure stops the checks.
    private @Nullable Outcome refusedOrStop(final String check, final ProviderCallResult<CheckReply> call) {
        final AppError failure = Objects.requireNonNull(call.result().error(), "error");
        final boolean refused = failure.code() == ErrorCode.validation || call.rejectedCapability() != null;
        log.debug(
                "Advanced {} check failed provider={} code={} reason={} refused={}",
                check,
                config.id(),
                failure.code(),
                FailureReason.tokenIn(failure.details()),
                refused);
        if (refused) {
            return Outcome.REFUSED;
        }
        if (failure.code() != ErrorCode.emptyCompletion) {
            stopped = failure;
        }
        return null;
    }

    private static String content(final ProviderCallResult<CheckReply> call) {
        return Objects.requireNonNull(call.result().data(), "reply").response().content();
    }

    private ProviderCallResult<CheckReply> send(final ChatRequest request, final @Nullable OffSwitch offSwitch) {
        return sender.send(request, offSwitch);
    }

    private @Nullable Verdict verdictOf(final @Nullable Outcome outcome) {
        return outcome == null ? null : verdict(outcome);
    }

    private Verdict verdict(final Outcome outcome) {
        return new Verdict(outcome, SOURCE, now);
    }

    private static ChatRequest request(
            final String prompt,
            final @Nullable ResponseFormat format,
            final @Nullable ReasoningLevel reasoning,
            final @Nullable SamplingParams sampling,
            final @Nullable OutputLengthField field) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, prompt)),
                sampling == null ? null : HOT_TEMPERATURE,
                format,
                reasoning,
                null,
                null,
                field == null ? OUTPUT_CAP : CAP_PROBE,
                null,
                null,
                sampling,
                field);
    }

    /** Sends one check through the gate. */
    @FunctionalInterface
    interface Sender {
        /**
         * Sends one check.
         *
         * @param offSwitch the thinking switch to send in place of the engine's, or null for the engine's own
         */
        ProviderCallResult<CheckReply> send(ChatRequest request, @Nullable OffSwitch offSwitch);
    }

    /**
     * What the checks found.
     *
     * @param verdicts the verdicts, null parts for checks that did not run or could not tell
     * @param capField the cap field later calls send, or null when the cap check did not find one
     * @param failure the failure that stopped the checks, or null when they all ran
     */
    record Findings(
            ProbeVerdicts verdicts,
            @Nullable OutputLengthField capField,
            @Nullable AppError failure) {}

    private record CapFinding(
            @Nullable Verdict verdict, @Nullable OutputLengthField field) {}
}
