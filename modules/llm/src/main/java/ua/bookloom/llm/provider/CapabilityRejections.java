package ua.bookloom.llm.provider;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.llm.http.HttpReply;

/** Detects a narrowly recognized unsupported request control without retaining a server response body. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public final class CapabilityRejections {

    private CapabilityRejections() {}

    private static final int BAD_REQUEST = 400;
    private static final Pattern REASONING_REJECTION = Pattern.compile(
            "\\bthink\\b.{0,256}\\b(unsupported|unknown|unrecognized|invalid|not supported)\\b"
                    + "|\\b(unsupported|unknown|unrecognized|invalid|not supported)\\b.{0,256}\\bthink\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STRUCTURED_OUTPUT_REJECTION = Pattern.compile(
            "\\b(format|response_format|json[ _-]?schema|structured[ _-]?output)\\b.{0,256}"
                    + "\\b(unsupported|unknown|unrecognized|invalid|not supported)\\b"
                    + "|\\b(unsupported|unknown|unrecognized|invalid|not supported)\\b.{0,256}"
                    + "\\b(format|response_format|json[ _-]?schema|structured[ _-]?output)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Returns only a capability that was both sent and explicitly rejected by a 400 response. */
    public static @Nullable RejectedCapability from(HttpReply reply, ChatRequest request) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(request, "request");
        if (reply.status() != BAD_REQUEST) {
            return null;
        }
        final String body = reply.body();
        if (request.reasoningEnabled() != null
                && REASONING_REJECTION.matcher(body).find()) {
            return RejectedCapability.REASONING_CONTROL;
        }
        if (request.responseFormat() != null
                && STRUCTURED_OUTPUT_REJECTION.matcher(body).find()) {
            return RejectedCapability.STRUCTURED_OUTPUT;
        }
        return null;
    }
}
