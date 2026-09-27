package ua.bookloom.api.project;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A glossary term as a retry's context snapshot saw it — the text, not the id, so a retry replays exactly even
 * after the glossary changes.
 *
 * @param term the source term
 * @param target the term's rendering at the time of the snapshot, or null when unresolved
 * @param type the term's category
 * @param gender the term's grammatical gender
 * @param locked whether the term's rendering was locked at the time of the snapshot
 */
public record SnapshotTerm(String term, @Nullable String target, TermType type, Gender gender, boolean locked) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public SnapshotTerm {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(gender, "gender");
    }
}
