package ua.bookloom.llm.provider;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.llm.http.HttpReply;

/**
 * Detects a narrowly recognized unsupported request setting without retaining a server response body. A reply counts
 * only when its status is 400 or 422 and it names a setting the request really sent, so wording that merely mentions a
 * setting never drops one.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CapabilityRejections {

    private static final int BAD_REQUEST = 400;
    private static final int UNPROCESSABLE = 422;
    private static final int WINDOW = 160;
    private static final String VERB = "(?:unsupported|unknown|unrecognized|invalid|not supported|does not support"
            + "|not allowed|not permitted|only the default)";

    // The order is the precedence when one reply names several sent settings.
    private static final List<RejectedCapability> PRECEDENCE = List.of(
            RejectedCapability.REASONING_CONTROL,
            RejectedCapability.STRUCTURED_OUTPUT,
            RejectedCapability.OUTPUT_FIELD,
            RejectedCapability.TEMPERATURE,
            RejectedCapability.TOP_P,
            RejectedCapability.TOP_K,
            RejectedCapability.MIN_P,
            RejectedCapability.REPEAT_PENALTY,
            RejectedCapability.SEED,
            RejectedCapability.NUM_CTX);

    private static final Map<RejectedCapability, Pattern> PATTERNS = patterns();

    /**
     * Finds the sent setting a reply refuses.
     *
     * @param reply the provider's reply; non-null
     * @param request the request that was sent; non-null
     * @param kind the dialect the request was shaped for; non-null
     * @return the refused setting, or null when the reply is not a 400 or 422 naming a setting the request sent
     */
    public static @Nullable RejectedCapability from(HttpReply reply, ChatRequest request, ProviderKind kind) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(request, "request");
        if (reply.status() != BAD_REQUEST && reply.status() != UNPROCESSABLE) {
            return null;
        }
        final EnumSet<RejectedCapability> sent = sent(request, kind);
        RejectedCapability found = null;
        for (final RejectedCapability candidate : PRECEDENCE) {
            if (sent.contains(candidate)
                    && Objects.requireNonNull(PATTERNS.get(candidate), "pattern")
                            .matcher(reply.body())
                            .find()) {
                found = candidate;
                break;
            }
        }
        log.debug("Refusal check status={} kind={} sent={} matched={}", reply.status(), kind, sent, found);
        return found;
    }

    /**
     * As {@link #from}, but only for a reply that {@code HttpErrorMapper} classified as {@code validation}: a body
     * that means a missing model or an over-long prompt is never read as a refused setting.
     *
     * @param mapped the mapped outcome of the reply; non-null
     * @param reply the provider's reply; non-null
     * @param request the request that was sent; non-null
     * @param kind the dialect the request was shaped for; non-null
     * @return the refused setting, or null
     */
    public static @Nullable RejectedCapability fromMapped(
            Result<?> mapped, HttpReply reply, ChatRequest request, ProviderKind kind) {
        final AppError error = mapped.error();
        return error != null && error.code() == ErrorCode.validation ? from(reply, request, kind) : null;
    }

    private static EnumSet<RejectedCapability> sent(ChatRequest request, ProviderKind kind) {
        final EnumSet<RejectedCapability> sent = EnumSet.noneOf(RejectedCapability.class);
        addIf(sent, RejectedCapability.TEMPERATURE, request.temperature() != null);
        addIf(sent, RejectedCapability.SEED, request.seed() != null);
        addIf(sent, RejectedCapability.STRUCTURED_OUTPUT, request.responseFormat() != null);
        addIf(sent, RejectedCapability.REASONING_CONTROL, request.reasoningEnabled() != null);
        addIf(sent, RejectedCapability.NUM_CTX, kind == ProviderKind.OLLAMA && request.contextWindow() != null);
        addIf(
                sent,
                RejectedCapability.OUTPUT_FIELD,
                kind == ProviderKind.OPENAI_COMPATIBLE
                        && request.maxOutputTokens() != null
                        && request.outputField() != OutputLengthField.MAX_COMPLETION_TOKENS);
        final SamplingParams sampling = request.samplingOrNone();
        addIf(sent, RejectedCapability.TOP_P, sampling.topP() != null);
        addIf(sent, RejectedCapability.TOP_K, sampling.topK() != null);
        addIf(sent, RejectedCapability.MIN_P, sampling.minP() != null);
        addIf(sent, RejectedCapability.REPEAT_PENALTY, sampling.repeatPenalty() != null);
        return sent;
    }

    private static void addIf(EnumSet<RejectedCapability> sent, RejectedCapability setting, boolean carried) {
        if (carried) {
            sent.add(setting);
        }
    }

    private static Map<RejectedCapability, Pattern> patterns() {
        final Map<RejectedCapability, Pattern> byCapability = new EnumMap<>(RejectedCapability.class);
        byCapability.put(RejectedCapability.REASONING_CONTROL, pattern("think|reasoning_effort|reasoning"));
        byCapability.put(
                RejectedCapability.STRUCTURED_OUTPUT,
                pattern("format|response_format|json[ _-]?schema|structured[ _-]?output"));
        byCapability.put(RejectedCapability.OUTPUT_FIELD, pattern("max_tokens"));
        byCapability.put(RejectedCapability.TEMPERATURE, pattern("temperature"));
        byCapability.put(RejectedCapability.TOP_P, pattern("top_p"));
        byCapability.put(RejectedCapability.TOP_K, pattern("top_k"));
        byCapability.put(RejectedCapability.MIN_P, pattern("min_p"));
        byCapability.put(RejectedCapability.REPEAT_PENALTY, pattern("repeat_penalty"));
        byCapability.put(RejectedCapability.SEED, pattern("seed"));
        byCapability.put(RejectedCapability.NUM_CTX, pattern("num_ctx"));
        return byCapability;
    }

    private static Pattern pattern(String names) {
        final String name = "\\b(?:" + names + ")\\b";
        return Pattern.compile(
                name + ".{0," + WINDOW + "}" + VERB + "|" + VERB + ".{0," + WINDOW + "}" + name,
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }
}
