package ua.bookloom.llm.provider;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.llm.http.HttpReply;

/**
 * Internal provider outcome that keeps response retry advice alongside the unchanged public result envelope.
 *
 * @param result the typed outcome
 * @param retryAfter the server's {@code Retry-After} value, or null when it sent none
 * @param rejectedCapability the request setting the server refused, or null
 * @param sent the request the client actually sent when it changed the caller's (a cap raised after the reasoning
 *     spent it), or null when the caller's request was sent as it is; what the reply is measured against
 */
public record ProviderCallResult<T>(
        Result<T> result,
        @Nullable String retryAfter,
        @Nullable RejectedCapability rejectedCapability,
        @Nullable ChatRequest sent) {

    /** Enforces a present typed result while keeping an optional server retry hint internal to {@code :llm}. */
    public ProviderCallResult {
        Objects.requireNonNull(result, "result");
    }

    /** Keeps the callers whose request is sent as it is. */
    public ProviderCallResult(
            Result<T> result, @Nullable String retryAfter, @Nullable RejectedCapability rejectedCapability) {
        this(result, retryAfter, rejectedCapability, null);
    }

    /**
     * The same outcome, saying which request was actually sent.
     *
     * @param request the request the client sent in place of the caller's; non-null
     * @return a copy carrying {@code request} as {@link #sent()}
     */
    public ProviderCallResult<T> sentAs(ChatRequest request) {
        return new ProviderCallResult<>(
                result, retryAfter, rejectedCapability, Objects.requireNonNull(request, "request"));
    }

    /** Preserves existing callers that have no provider capability metadata. */
    public ProviderCallResult(Result<T> result, @Nullable String retryAfter) {
        this(result, retryAfter, null);
    }

    /** Carries response metadata without changing the public result envelope. */
    public static <T> ProviderCallResult<T> fromHttpReply(Result<T> result, HttpReply reply) {
        Objects.requireNonNull(reply, "reply");
        return new ProviderCallResult<>(result, retryAfterHeader(reply.headers()));
    }

    /** Carries response metadata and a narrowly recognized rejected request capability. */
    public static <T> ProviderCallResult<T> fromHttpReply(
            Result<T> result, HttpReply reply, @Nullable RejectedCapability rejectedCapability) {
        Objects.requireNonNull(reply, "reply");
        return new ProviderCallResult<>(result, retryAfterHeader(reply.headers()), rejectedCapability);
    }

    /** Wraps a result for a locally generated or transport failure with no response header. */
    public static <T> ProviderCallResult<T> withoutRetryAfter(Result<T> result) {
        return new ProviderCallResult<>(result, null);
    }

    private static @Nullable String retryAfterHeader(Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Retry-After")
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().getFirst();
            }
        }
        return null;
    }
}
