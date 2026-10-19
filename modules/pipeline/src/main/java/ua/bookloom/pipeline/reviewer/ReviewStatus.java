package ua.bookloom.pipeline.reviewer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What the reviewer says about one candidate. */
public enum ReviewStatus {

    /** The candidate needs nothing. */
    OK("ok"),

    /** The candidate needs the listed edits, the default for a defect. */
    EDITS("edits"),

    /** More than about 40% of the text must change, or the structure or meaning is wrong throughout. */
    REWRITE("rewrite");

    private final String wire;

    ReviewStatus(final String wire) {
        this.wire = wire;
    }

    /**
     * The word the reviewer writes for this status.
     *
     * @return a lower-case token
     */
    public String wire() {
        return wire;
    }

    /**
     * Reads the word a reviewer wrote.
     *
     * @param text the status as written, in any letter case
     * @return the status, or empty when the word names none
     */
    public static Optional<ReviewStatus> of(final String text) {
        final String key = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(status -> status.wire.equals(key)).findFirst();
    }
}
