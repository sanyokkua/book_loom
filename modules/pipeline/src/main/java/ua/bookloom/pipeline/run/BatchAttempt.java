package ua.bookloom.pipeline.run;

import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.pipeline.batch.BatchReply;

/**
 * How a batch step ended: what the reply says, and whether the model's own answer or an outside event made it.
 *
 * @param reply what each id is settled by, after any re-ask of the missing ids
 * @param first the first reply alone, which is all the batch size is judged by
 * @param kind how the step ended
 * @param skip the error a skipped step answered, else null
 */
record BatchAttempt(
        BatchReply reply,
        BatchReply first,
        Kind kind,
        @Nullable AppError skip) {

    enum Kind {
        /** The model answered; its reply, readable or not, says how well it kept the numbering. */
        ANSWERED,
        /** No answer came — an outage, a flagged step, an error that flags at once — which says nothing of the size. */
        UNAVAILABLE,
        /** The person skipped the step from a pause: its first segment is skipped. */
        SKIPPED
    }

    static BatchAttempt answered(final BatchReply reply, final BatchReply first) {
        return new BatchAttempt(reply, first, Kind.ANSWERED, null);
    }

    static BatchAttempt unavailable(final List<String> ids) {
        final BatchReply none = BatchReply.unreadable(ids);
        return new BatchAttempt(none, none, Kind.UNAVAILABLE, null);
    }

    static BatchAttempt skipped(final List<String> ids, final AppError error) {
        final BatchReply none = BatchReply.unreadable(ids);
        return new BatchAttempt(none, none, Kind.SKIPPED, error);
    }
}
