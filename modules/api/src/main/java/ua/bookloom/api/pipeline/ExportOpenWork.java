package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * The open work the Export screen's banners name before a book is written, read from the stored records, verdicts and
 * deferrals with no model call.
 *
 * @param openMarks the segments that hold a proofread mark no window read, or whose rewrite was refused; never negative
 * @param waitingOnFact the segments that wait on a fact the person has since given, such as a character's gender;
 *     never negative
 * @param facts the names of the given facts those segments wait on, in the order the deferrals were recorded, each
 *     once; never null, empty when no segment waits on one
 */
public record ExportOpenWork(int openMarks, int waitingOnFact, List<String> facts) {

    /** Nothing open. */
    public static final ExportOpenWork NONE = new ExportOpenWork(0, 0, List.of());

    /** Rejects a negative count and copies the list. */
    public ExportOpenWork {
        facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
        if (openMarks < 0 || waitingOnFact < 0) {
            throw new IllegalArgumentException("no count may be negative: " + openMarks + ", " + waitingOnFact);
        }
    }
}
