package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import java.util.Set;

/**
 * The slots one template file may use.
 *
 * @param required slots the file must contain as {@code {{slot}}}
 * @param optional slots the file may contain, alone or as a {@code {{#slot}}} block dropped when empty
 */
public record Slots(Set<String> required, Set<String> optional) {

    /** Copies both sets so a declaration can never change after construction. */
    public Slots {
        required = Set.copyOf(Objects.requireNonNull(required, "required"));
        optional = Set.copyOf(Objects.requireNonNull(optional, "optional"));
    }

    boolean declares(final String slot) {
        return required.contains(slot) || optional.contains(slot);
    }
}
