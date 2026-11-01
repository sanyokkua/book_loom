package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * The target of a results row the person may type over, in the shape the table that holds it takes.
 *
 * @param key the glossary entry's id for a name, the source term for a recurring term
 * @param target what the row's target is now; empty when it has none
 * @param rest the row's other words (type, gender) shown beside the field; empty for a recurring term
 */
public record EditableTarget(String key, String target, String rest) {

    /** Rejects missing parts. */
    public EditableTarget {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rest, "rest");
    }
}
