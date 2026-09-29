package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Objects;

/**
 * What one backward-revision pass changed, for the run's log and the export report.
 *
 * @param termSubstitutions how many changed locked renderings were substituted, one per swept term deferral
 * @param genderReRenders how many segments the revision call re-rendered for a character's now-known gender
 * @param proposals how many segments the person edited got a proposal instead of a change
 * @param notes one line per change, naming the segment by its locator; never null, empty when nothing changed
 */
public record ConsistencyReport(int termSubstitutions, int genderReRenders, int proposals, List<String> notes) {

    /** Copies the notes so the report can never change after construction. */
    public ConsistencyReport {
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}
