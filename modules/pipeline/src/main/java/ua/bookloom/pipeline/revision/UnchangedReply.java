package ua.bookloom.pipeline.revision;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;

/**
 * Reads the consistency check's short answer {@code {"unchanged":true}}, which the model gives instead of writing the
 * paragraph again. Only an object that holds nothing else counts: a reply that also carries a {@code target} is a
 * rewrite and is read as one.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UnchangedReply {

    // A code fence around the object is tolerated, as it is for every other reply.
    private static final Pattern UNCHANGED =
            Pattern.compile("(?:```(?:json)?\\s*)?\\{\\s*\"unchanged\"\\s*:\\s*true\\s*}\\s*(?:```)?");

    /**
     * Whether a finished reply says the paragraph needs no change.
     *
     * @param reply the non-null call result
     * @return {@code true} for a normally finished reply that is exactly the unchanged object, {@code false} for an
     *     error, a cut reply or anything else
     */
    static boolean isUnchanged(final Result<ChatResponse> reply) {
        Objects.requireNonNull(reply, "reply");
        final ChatResponse response = reply.data();
        return response != null
                && response.finishReason() == FinishReason.STOP
                && UNCHANGED.matcher(response.content().strip()).matches();
    }
}
