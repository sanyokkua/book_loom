package ua.bookloom.pipeline.setup;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The verdict on what the model knows of the book: the accepted answer, or "unknown" with the reason it was refused.
 *
 * @param reply the accepted answer, or null when the book is unknown
 * @param reason why the answer was refused, or null when it was accepted
 */
public record Knowledge(
        @Nullable BookKnowledgeReply reply, @Nullable Reason reason) {

    /** Why an answer does not stand; {@link #label()} is the word written to the log. */
    public enum Reason {
        /** The book has no title to ask about. */
        NO_TITLE("no-title"),
        /** The title is only the file name's stem, so it names nothing. */
        TITLE_IS_FILE_STEM("title-is-file-stem"),
        /** The model says it does not know the book. */
        NOT_KNOWN("not-known"),
        /** The model's confidence is under the threshold. */
        LOW_CONFIDENCE("low-confidence"),
        /** The echoed title does not match the metadata. */
        TITLE_MISMATCH("title-mismatch"),
        /** The echoed author does not match the metadata. */
        AUTHOR_MISMATCH("author-mismatch"),
        /** The year is not within five of the metadata year. */
        YEAR_MISMATCH("year-mismatch"),
        /** The reply could not be read. */
        UNPARSABLE("unparsable"),
        /** The call itself failed. */
        CALL_FAILED("call-failed");

        private final String label;

        Reason(final String label) {
            this.label = label;
        }

        /** The reason's log word. */
        public String label() {
            return label;
        }
    }

    /** Accepted answer. */
    public static Knowledge known(final BookKnowledgeReply reply) {
        return new Knowledge(Objects.requireNonNull(reply, "reply"), null);
    }

    /** Refused answer. */
    public static Knowledge unknown(final Reason reason) {
        return new Knowledge(null, Objects.requireNonNull(reason, "reason"));
    }

    /** Whether the answer stands. */
    public boolean isKnown() {
        return reply != null;
    }
}
