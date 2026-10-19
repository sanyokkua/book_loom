package ua.bookloom.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.ProviderProbe;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/**
 * The pseudo model behind a seeded fault injector, at the model port: each call either answers as the pseudo model
 * would, with its capitals written in Cyrillic so a clean reply passes the target-script checks, or fails the way a
 * real client or a real small model fails — a timeout, a call that never returns until the watchdog ends it, a 5xx
 * burst, an unreachable server, one long outage, an unloaded model, an empty reply, a reply that drops, duplicates or
 * garbles its placeholders (in one item of a batch reply, whose refusal is the whole reply), a refusal, a judge reply that is no JSON or that asks for a fix, a hang on the re-judge
 * that fix leads to, or a step that throws.
 *
 * <p>Used from the job thread only, apart from {@link #probe()}, which reads the outage's end.
 */
final class FaultyModel implements ChatModel {

    /** What one call did. */
    enum Fault {
        NONE,
        TIMEOUT,
        HANG,
        UPSTREAM_BURST,
        UNREACHABLE,
        OUTAGE,
        MODEL_UNLOADED,
        EMPTY_COMPLETION,
        EMPTY_TARGET,
        DROP_TOKEN,
        DUPLICATE_TOKEN,
        GARBLE_TOKEN,
        STRAY_BRACKET,
        REFUSAL,
        JUDGE_JUNK,
        JUDGE_REVISE,
        REJUDGE_HANG,
        THROW
    }

    /** The message of an injected throw, so the log check can tell it from a real one. */
    static final String INJECTED = "soak: injected fault";

    /** The timeout a hanging call announces; the watchdog ends it past one and a half times this. */
    static final Duration HANG_TIMEOUT = Duration.ofMinutes(2);

    private static final int PER_MILLE = 1_000;
    private static final int MAX_BURST = 4;
    private static final String DRAFT = "draft";
    private static final String BATCH = "draft-batch-json";
    private static final String REVIEW = "reviewer";
    private static final String DIRECTED_FIX = "directed-fix";
    // A reviewer that asks to rewrite the first pair with text the checks refuse, so the draft stays and is flagged.
    private static final String REVISE =
            "{\"results\":[{\"id\":\"s1\",\"status\":\"rewrite\",\"rewrite\":\"THE WRONG SENSE\"}]}";

    private final PseudoChatModel pseudo;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Random random;
    private final FaultPlan plan;
    private final Map<Fault, Integer> injected = new EnumMap<>(Fault.class);
    private final AtomicReference<@Nullable Instant> outageEnds = new AtomicReference<>();
    private Runnable stall = () -> {
        throw new IllegalStateException("no watchdog tick to end a hanging call");
    };
    private int calls;
    private int burstLeft;
    private @Nullable String previousFormat;

    FaultyModel(final ObjectMapper mapper, final Clock clock, final FaultPlan plan) {
        this.pseudo = new PseudoChatModel(mapper);
        this.mapper = mapper;
        this.clock = clock;
        this.plan = plan;
        this.random = new Random(plan.seed());
    }

    /** What ends a hanging call: moves the clock past its ceiling and runs one watchdog tick. */
    void stallWith(final Runnable stallNow) {
        this.stall = Objects.requireNonNull(stallNow, "stallNow");
    }

    /** The probe a recovering run checks the provider with: down while the outage lasts. */
    ProviderProbe probe() {
        return () -> isDown() ? Result.err(unreachable()) : Result.ok(Duration.ofMillis(5));
    }

    /** How many times each fault was injected. */
    Map<Fault, Integer> injected() {
        return Map.copyOf(injected);
    }

    int calls() {
        return calls;
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        return chat(request, CallAttemptListener.NONE);
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        calls++;
        final String format = request.responseFormat() == null
                ? "plain"
                : Objects.requireNonNull(request.responseFormat()).name();
        final boolean rejudge = REVIEW.equals(format) && DIRECTED_FIX.equals(previousFormat);
        previousFormat = format;
        final Fault fault = choose(format, rejudge);
        injected.merge(fault, 1, Integer::sum);
        final CallAttempt attempt =
                new CallAttempt(1, 1, fault == Fault.HANG || fault == Fault.REJUDGE_HANG ? HANG_TIMEOUT : null, null);
        attempts.started(attempt);
        final Result<ChatResponse> result = answer(fault, request);
        final AppError error = result.error();
        if (error != null) {
            attempts.failed(attempt, error.code());
        }
        return result;
    }

    private Fault choose(final String format, final boolean rejudge) {
        if (calls == plan.outageAtCall()) {
            outageEnds.set(clock.instant().plus(plan.outage()));
        }
        if (isDown()) {
            return Fault.OUTAGE;
        }
        if (burstLeft > 0) {
            burstLeft--;
            return Fault.UPSTREAM_BURST;
        }
        final int roll = random.nextInt(PER_MILLE);
        if (rejudge && roll < plan.rejudgeHangPerMille()) {
            return Fault.REJUDGE_HANG;
        }
        final Fault any = plan.anyCall(roll);
        if (any != Fault.NONE) {
            return startBurst(any);
        }
        return switch (format) {
            case DRAFT, BATCH -> plan.draftCall(roll);
            case REVIEW -> plan.judgeCall(roll);
            default -> Fault.NONE;
        };
    }

    private Fault startBurst(final Fault fault) {
        if (fault == Fault.UPSTREAM_BURST) {
            burstLeft = random.nextInt(MAX_BURST);
        }
        return fault;
    }

    private boolean isDown() {
        final Instant ends = outageEnds.get();
        return ends != null && clock.instant().isBefore(ends);
    }

    private Result<ChatResponse> answer(final Fault fault, final ChatRequest request) {
        return switch (fault) {
            case TIMEOUT, UPSTREAM_BURST, UNREACHABLE, OUTAGE, MODEL_UNLOADED, EMPTY_COMPLETION ->
                Result.err(failure(fault));
            case HANG, REJUDGE_HANG -> hang();
            case JUDGE_JUNK ->
                Result.ok(new ChatResponse("The translation reads well {score: maybe", FinishReason.STOP));
            case JUDGE_REVISE -> Result.ok(new ChatResponse(REVISE, FinishReason.STOP));
            case THROW -> throw new IllegalStateException(INJECTED);
            case NONE, EMPTY_TARGET, DROP_TOKEN, DUPLICATE_TOKEN, GARBLE_TOKEN, STRAY_BRACKET, REFUSAL ->
                reply(fault, request);
        };
    }

    // The errors the real clients answer, with their own titles and safe details.
    private static AppError failure(final Fault fault) {
        return switch (fault) {
            case TIMEOUT ->
                AppError.of(
                        ErrorCode.timeout,
                        "Provider request timed out",
                        "The provider did not respond before the timeout.",
                        "endpointHost=localhost, timeout=120s",
                        null);
            case UPSTREAM_BURST ->
                AppError.of(
                        ErrorCode.upstream,
                        "Provider server error",
                        "The provider could not complete the request.",
                        "httpStatus=503, endpointHost=localhost",
                        null);
            case MODEL_UNLOADED ->
                AppError.of(
                        ErrorCode.modelUnavailable,
                        "Provider model is not loaded",
                        "The provider has no model loaded to answer the request.",
                        "httpStatus=400, endpointHost=localhost",
                        null);
            case EMPTY_COMPLETION ->
                AppError.of(ErrorCode.emptyCompletion, "Empty completion", "The provider returned no content.");
            default -> unreachable();
        };
    }

    // A client ends a call it is interrupted in and answers cancelled; the watchdog's tick interrupts this thread.
    private Result<ChatResponse> hang() {
        stall.run();
        try {
            Thread.sleep(Duration.ofSeconds(10));
            throw new AssertionError("the stall watchdog never ended the hanging call");
        } catch (InterruptedException interrupted) {
            return Result.err(AppError.of(ErrorCode.cancelled, "Provider request cancelled", "Interrupted."));
        }
    }

    private Result<ChatResponse> reply(final Fault fault, final ChatRequest request) {
        final Result<ChatResponse> clean = pseudo.chat(request);
        final ChatResponse response = Objects.requireNonNull(clean.data(), "the pseudo model always answers");
        return Result.ok(new ChatResponse(rewrite(fault, response.content()), FinishReason.STOP));
    }

    // Only a reply with a target, or a batch of them, is rewritten; the judge's and the scans' JSON pass through.
    private String rewrite(final Fault fault, final String content) {
        try {
            final JsonNode node = mapper.readTree(content);
            final JsonNode items = node == null ? null : node.get("items");
            if (items != null && items.isArray()) {
                return rewriteBatch(fault, items);
            }
            final JsonNode target = node == null ? null : node.get("target");
            if (target == null || !target.isTextual()) {
                return content;
            }
            final String damaged = FaultyText.damage(fault, Cyrillic.of(target.asText()), random);
            return mapper.writeValueAsString(Map.of("target", damaged));
        } catch (JsonProcessingException notJson) {
            return content;
        }
    }

    // A batch reply is Cyrillic throughout; a content fault damages one random item, and a refusal is the whole reply.
    private String rewriteBatch(final Fault fault, final JsonNode items) throws JsonProcessingException {
        if (fault == Fault.REFUSAL) {
            return FaultyText.damage(fault, "", random);
        }
        final int damaged = random.nextInt(items.size());
        final List<Map<String, String>> entries = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            final String target = Cyrillic.of(items.get(index).path("target").asText());
            entries.add(Map.of(
                    "id",
                    items.get(index).path("id").asText(),
                    "target",
                    index == damaged ? FaultyText.damage(fault, target, random) : target));
        }
        return mapper.writeValueAsString(Map.of("items", entries));
    }

    private static AppError unreachable() {
        return AppError.of(
                ErrorCode.unreachable,
                "Provider is unreachable",
                "The provider could not be reached.",
                "endpointHost=localhost",
                null);
    }
}
