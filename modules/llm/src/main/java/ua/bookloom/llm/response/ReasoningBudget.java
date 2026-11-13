package ua.bookloom.llm.response;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.llm.profile.OutputRoom;
import ua.bookloom.llm.provider.ProviderCallResult;

/**
 * Notices a reply that spent its whole answer thinking — blank text once scrubbed, reasoning shown in a field or as an
 * opener never closed, cut off at the cap — and gives the one request to send again: with thinking off when it was on
 * and the model can turn it off, else with four times the cap inside what the context window leaves after the prompt. Both dialects read their reply
 * through it, so a model that thinks gets the same one more try on every server (design D20).
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReasoningBudget {

    /** How many times the original cap the repeat allows when thinking cannot be turned off. */
    public static final int RAISE_FACTOR = 4;

    private static final String LENGTH = "length";

    /**
     * Whether a capped reply spent its whole answer thinking.
     *
     * @param request the request that got the reply; non-null
     * @param content the reply's text as received, or null when it had none
     * @param reasoningShown whether the reply carried reasoning text in a field of its own
     * @param finish the reply's finish word as the server wrote it, or null
     * @return {@code true} if the cap was spent on reasoning, {@code false} otherwise
     */
    public static boolean spent(
            final ChatRequest request,
            final @Nullable String content,
            final boolean reasoningShown,
            final @Nullable String finish) {
        Objects.requireNonNull(request, "request");
        final String text = content == null ? "" : content;
        final boolean opener = ReasoningScrubber.hasUnclosedOpener(text);
        final boolean blank = ReasoningScrubber.scrub(text).isBlank();
        final boolean spent =
                request.maxOutputTokens() != null && LENGTH.equals(finish) && blank && (reasoningShown || opener);
        log.debug(
                "Reasoning budget check cap={} finish={} blank={} reasoningField={} unclosedOpener={} spent={}",
                request.maxOutputTokens(),
                finish,
                blank,
                reasoningShown,
                opener,
                spent);
        return spent;
    }

    /**
     * The request to send once more after a reply that spent its cap thinking.
     *
     * @param request the request that got the reply; non-null
     * @return the request with thinking off when it was on, else with four times its cap bounded by what its context
     *     window leaves after the prompt, or empty when neither change is possible
     */
    public static Optional<ChatRequest> reask(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.reasoningEnabled() == ReasoningLevel.ON) {
            log.warn(
                    "The reply spent its whole answer thinking reason={} kind={}; asking once more with thinking off",
                    FailureReason.CAP_SPENT.token(),
                    request.callKind());
            return Optional.of(request.withReasoning(ReasoningLevel.OFF, 0));
        }
        final Integer cap = request.maxOutputTokens();
        if (cap == null) {
            return Optional.empty();
        }
        final int raised = raisedCap(request, cap);
        if (raised <= cap) {
            return Optional.empty();
        }
        log.warn(
                "The reply spent its whole answer cap of {} tokens thinking reason={} kind={}; asking once more with {}",
                cap,
                FailureReason.CAP_SPENT.token(),
                request.callKind(),
                raised);
        return Optional.of(request.withModelSettings(request.temperature(), raised, request.outputField()));
    }

    // Four times the cap, inside what the window leaves after the prompt.
    private static int raisedCap(final ChatRequest request, final int cap) {
        final Integer room = OutputRoom.room(request);
        final int raised = room == null ? cap * RAISE_FACTOR : Math.min(cap * RAISE_FACTOR, room);
        log.debug(
                "Reasoning cap raise cap={} contextWindow={} room={} raised={}",
                cap,
                request.contextWindow(),
                room,
                raised);
        return raised;
    }

    /**
     * What a client does with a reply: read it, or, when it spent its cap thinking, send the one repeat, or end with
     * {@code reasoning-exhausted} when it was that repeat.
     *
     * @param request the request that got the reply; non-null
     * @param spent whether the reply spent its cap thinking
     * @param mayReask whether this request may still be repeated (false for the repeat itself and for a check)
     * @param failed the failure of a repeat that spent its cap again; non-null
     * @param resend sends the repeat; non-null
     * @param read reads the reply as it is; non-null
     * @return the outcome; never null
     */
    public static ProviderCallResult<ChatResponse> settled(
            final ChatRequest request,
            final boolean spent,
            final boolean mayReask,
            final Supplier<AppError> failed,
            final Function<ChatRequest, ProviderCallResult<ChatResponse>> resend,
            final Supplier<ProviderCallResult<ChatResponse>> read) {
        Objects.requireNonNull(failed, "failed");
        Objects.requireNonNull(resend, "resend");
        Objects.requireNonNull(read, "read");
        if (!spent) {
            return read.get();
        }
        if (!mayReask) {
            return ProviderCallResult.withoutRetryAfter(Result.err(failed.get()));
        }
        return reask(request).map(again -> resend.apply(again).sentAs(again)).orElseGet(read);
    }

    /**
     * The failure of a call whose repeat spent its answer thinking too.
     *
     * @param config the provider; non-null
     * @param modelId the model asked; non-null
     * @return an {@code emptyCompletion} error with the reason {@code reasoning-exhausted}
     */
    public static AppError exhausted(final ProviderConfig config, final String modelId) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(modelId, "modelId");
        log.warn(
                "The repeat spent its whole answer thinking too reason={} host={} model={}",
                FailureReason.REASONING_EXHAUSTED.token(),
                config.baseUrl().getHost(),
                modelId);
        return AppError.of(
                ErrorCode.emptyCompletion,
                "Model returned an empty response",
                "The model spent its whole answer budget thinking.",
                SafeDetails.empty()
                        .withReason(FailureReason.REASONING_EXHAUSTED)
                        .withEndpoint(config.baseUrl())
                        .withModelName(modelId)
                        .render(),
                null);
    }
}
