package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.TermReading.Reading;

/**
 * What the suggestion call knows besides the entries it asks about (15i.N6).
 *
 * @param held every glossary entry of the project, the asked ones included: their clusters and the renderings already
 *     chosen come from it
 * @param readings the term reading of an entry by the entry's id, for its speaking and brand flags; an entry with none
 *     was not read
 */
public record TargetContext(List<GlossaryEntry> held, Map<String, Reading> readings) {

    /** Copies both collections. */
    public TargetContext {
        held = List.copyOf(Objects.requireNonNull(held, "held"));
        readings = Map.copyOf(Objects.requireNonNull(readings, "readings"));
    }

    /**
     * A context that knows nothing beyond the asked entries.
     *
     * @return the empty context
     */
    public static TargetContext none() {
        return new TargetContext(List.of(), Map.of());
    }
}
