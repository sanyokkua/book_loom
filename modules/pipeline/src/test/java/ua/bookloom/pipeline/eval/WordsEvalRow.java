package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;

/**
 * What the garbled-word check said about one case.
 *
 * @param id the case id
 * @param defective whether the case holds a garbled word
 * @param expected the garbled words the case holds
 * @param flagged the words the check reported, as written in the text
 */
record WordsEvalRow(String id, boolean defective, List<String> expected, List<String> flagged) {

    /** Copies the lists. */
    WordsEvalRow {
        Objects.requireNonNull(id, "id");
        expected = List.copyOf(expected);
        flagged = List.copyOf(flagged);
    }

    /** Whether a defective case had every garbled word reported. */
    boolean caught() {
        return defective && flagged.containsAll(expected);
    }

    /** Whether a clean case had any word reported. */
    boolean falseAlarm() {
        return !defective && !flagged.isEmpty();
    }
}
