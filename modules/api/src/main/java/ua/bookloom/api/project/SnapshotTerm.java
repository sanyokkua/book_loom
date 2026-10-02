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
 * @param suggested whether the target was the model's unconfirmed suggestion, offered to the draft only as a hint
 */
public record SnapshotTerm(
        String term, @Nullable String target, TermType type, Gender gender, boolean locked, boolean suggested) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public SnapshotTerm {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(gender, "gender");
    }

    /** A term whose target, if any, is the person's — what a snapshot made before suggestions existed holds. */
    public SnapshotTerm(
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        this(term, target, type, gender, locked, false);
    }
}
