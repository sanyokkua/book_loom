package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import ua.bookloom.api.project.TargetState;

/**
 * One book-map name as the never-worse vetoes read it.
 *
 * @param sourceNames the entry's term and its other spellings in the source
 * @param target the entry's rendering
 * @param state how firmly the rendering holds; only a pinned or locked one restricts the forms a text may write
 * @param forms every form the map lists for the rendering, the rendering itself included
 */
public record NameFact(List<String> sourceNames, String target, TargetState state, Set<String> forms) {

    /** Rejects a missing component and copies the collections. */
    public NameFact {
        sourceNames = List.copyOf(Objects.requireNonNull(sourceNames, "sourceNames"));
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(state, "state");
        forms = Set.copyOf(Objects.requireNonNull(forms, "forms"));
    }
}
