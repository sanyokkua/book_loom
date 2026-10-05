package ua.bookloom.pipeline.run;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.pipeline.batch.BatchReply;

/** How a batch step ended: what the reply says, and whether the model's own answer or an outside event made it. */
record BatchAttempt(BatchReply reply, Kind kind, @Nullable AppError skip) {

    enum Kind {
        /** The model answered; its reply, readable or not, says how well it kept the numbering. */
        ANSWERED,
        /** No answer came — an outage, a flagged step, an error that flags at once — which says nothing of the size. */
        UNAVAILABLE,
        /** The person skipped the step from a pause: its first segment is skipped. */
        SKIPPED
    }

    static BatchAttempt answered(final BatchReply reply) {
        return new BatchAttempt(reply, Kind.ANSWERED, null);
    }

    static BatchAttempt unavailable(final List<String> ids) {
        return new BatchAttempt(BatchReply.unreadable(ids), Kind.UNAVAILABLE, null);
    }

    static BatchAttempt skipped(final List<String> ids, final AppError error) {
        return new BatchAttempt(BatchReply.unreadable(ids), Kind.SKIPPED, error);
    }
}
