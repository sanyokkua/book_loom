package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.SnapshotTerm;

/**
 * A glossary entry chosen for one segment's prompt.
 *
 * @param term the entry as the snapshot records it
 * @param lines its prompt lines: one, or one per token when a locked entry is hidden behind several
 */
record InjectedTerm(SnapshotTerm term, List<String> lines) {

    InjectedTerm {
        Objects.requireNonNull(term, "term");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }
}
