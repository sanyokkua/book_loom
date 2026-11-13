package ua.bookloom.llm;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.RejectedCapability;
import ua.bookloom.llm.provider.SettingDrops;
import ua.bookloom.llm.response.QuoteCut;

/**
 * Decides which setting one call's outcome makes the model drop, and for which scope (design D20). A structured answer
 * is asked on one of two rungs per model and call kind — with the answer schema, or with the contract in the prompt
 * only — and a failure that belongs to one schema moves only that call kind down: a schema the server cannot use, a
 * schema not applied twice in a row, or a reply whose translation the schema cut at a quote mark. A server with no
 * structured output at all moves every call kind down. Any other refused setting covers the whole model.
 */
@Slf4j
final class StructuredRungs {

    /** Schema failures of one kind in a row after which the kind steps down. */
    private static final int NOT_APPLIED_LIMIT = 2;

    // Per schema name: how many replies in a row the server could not fit to the schema.
    private final Map<String, AtomicInteger> notApplied = new ConcurrentHashMap<>();

    /**
     * A setting the model refused and what it covers.
     *
     * @param setting the setting to leave out; non-null
     * @param scope the answer schema's name it covers alone, or null for every call to the model
     * @param reason the word the step is logged with; non-null
     */
    record Step(RejectedCapability setting, @Nullable String scope, String reason) {}

    /**
     * The step one call's outcome earns.
     *
     * @param profile the model's profile; non-null
     * @param request the request that was sent; non-null
     * @param call its outcome; non-null
     * @param downgraded the settings this call already left out; non-null
     * @return the step, or null when the outcome makes no setting drop
     */
    @Nullable
    Step step(
            final ModelProfile profile,
            final ChatRequest request,
            final ProviderCallResult<ChatResponse> call,
            final Set<RejectedCapability> downgraded) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(downgraded, "downgraded");
        final RejectedCapability refused = call.rejectedCapability();
        final AppError error = call.result().error();
        if (refused != null) {
            return isNew(refused, request, downgraded) ? refusal(refused, request, error) : null;
        }
        final ResponseFormat format = request.responseFormat();
        if (format == null || downgraded.contains(RejectedCapability.STRUCTURED_OUTPUT)) {
            return null;
        }
        final ChatResponse response = call.result().data();
        return response != null ? answered(profile, request, response, format) : failed(error, format);
    }

    private static boolean isNew(
            final RejectedCapability refused, final ChatRequest request, final Set<RejectedCapability> downgraded) {
        return !downgraded.contains(refused) && SettingDrops.without(request, refused) != null;
    }

    // A schema refused for what it asks covers its own call kind; a server with no structured output covers them all.
    private static Step refusal(
            final RejectedCapability refused, final ChatRequest request, final @Nullable AppError error) {
        final String reason =
                error == null ? FailureReason.UNCLASSIFIED.token() : FailureReason.tokenIn(error.details());
        final ResponseFormat format = request.responseFormat();
        final @Nullable String scope = refused != RejectedCapability.STRUCTURED_OUTPUT
                        || format == null
                        || FailureReason.STRUCTURED_UNAVAILABLE.token().equals(reason)
                ? null
                : format.name();
        log.debug("Refusal scope setting={} reason={} scope={}", refused, reason, scope == null ? "model" : scope);
        return new Step(refused, scope, reason);
    }

    private @Nullable Step answered(
            final ModelProfile profile,
            final ChatRequest request,
            final ChatResponse response,
            final ResponseFormat format) {
        notApplied.remove(format.name());
        final boolean watched = profile.quirks().contains(ModelProfile.Quirk.QUOTE_CUTS_SCHEMA_STRING);
        final boolean cut = watched && QuoteCut.isCut(request, response.content());
        log.debug("Schema reply check format={} quoteCutWatched={} cut={}", format.name(), watched, cut);
        return cut
                ? new Step(RejectedCapability.STRUCTURED_OUTPUT, format.name(), FailureReason.SCHEMA_CUT_SHORT.token())
                : null;
    }

    private @Nullable Step failed(final @Nullable AppError error, final ResponseFormat format) {
        if (error == null || FailureReason.in(error.details()).orElse(null) != FailureReason.SCHEMA_NOT_APPLIED) {
            return null;
        }
        final int inARow = notApplied
                .computeIfAbsent(format.name(), name -> new AtomicInteger())
                .incrementAndGet();
        log.debug("Schema not applied format={} inARow={}", format.name(), inARow);
        if (inARow < NOT_APPLIED_LIMIT) {
            return null;
        }
        notApplied.remove(format.name());
        return new Step(RejectedCapability.STRUCTURED_OUTPUT, format.name(), FailureReason.SCHEMA_NOT_APPLIED.token());
    }
}
