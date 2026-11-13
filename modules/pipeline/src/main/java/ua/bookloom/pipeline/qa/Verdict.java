package ua.bookloom.pipeline.qa;

import java.util.Objects;

/** What {@link NeverWorse} decides about a candidate text: keep it, or keep the current one for a named reason. */
public sealed interface Verdict {

    /** The candidate replaces the current text. */
    record Keep() implements Verdict {}

    /**
     * The current text stays.
     *
     * @param reason the veto or score rule that refused the candidate, a stable name for logs and tests
     */
    record Reject(String reason) implements Verdict {

        /** Rejects a missing reason. */
        public Reject {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
