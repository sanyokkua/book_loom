package ua.bookloom.pipeline.heal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.prompt.ChatRequests;

/**
 * The finding a wasted round leaves for the next one: the reply could not be read, and why. A round that repeated the
 * first request byte for byte got the same unreadable answer, so the cause goes into the next prompt and the call is
 * sent with another seed.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReplyFormatFinding {

    static final String RAISED_BY = "reply-format";

    /** The sampling seed of a call that follows a refused reply. */
    static final int RETRY_SEED = ChatRequests.RETRY_SEED;

    static QaFinding of(final String diagnostic) {
        return new QaFinding(
                "markup",
                Severity.MEDIUM,
                "Your previous reply " + diagnostic + ". Reply with the one JSON object only.",
                RAISED_BY);
    }

    static boolean isReplyFormat(final QaFinding finding) {
        return RAISED_BY.equals(finding.raisedBy());
    }
}
