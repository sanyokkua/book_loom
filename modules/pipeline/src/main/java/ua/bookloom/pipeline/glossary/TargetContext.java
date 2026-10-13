package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.TermReading.Reading;

/**
 * What the suggestion call knows besides the entries it asks about (15i.N6).
 *
 * @param held every glossary entry of the project, the asked ones included: their clusters and the renderings already
 *     chosen come from it
 * @param readings the term reading of an entry by the entry's id, for its speaking and brand flags; an entry with none
 *     was not read
 * @param verified the ids of the entries the readings decided are proper names; an entry the batch review judged, or
 *     none read, is not in it
 */
public record TargetContext(List<GlossaryEntry> held, Map<String, Reading> readings, Set<String> verified) {

    /** Copies the collections. */
    public TargetContext {
        held = List.copyOf(Objects.requireNonNull(held, "held"));
        readings = Map.copyOf(Objects.requireNonNull(readings, "readings"));
        verified = Set.copyOf(Objects.requireNonNull(verified, "verified"));
    }

    /**
     * A context whose entries none of the readings verified as names.
     *
     * @param held every glossary entry of the project; never null
     * @param readings the term reading of an entry by id; never null
     */
    public TargetContext(final List<GlossaryEntry> held, final Map<String, Reading> readings) {
        this(held, readings, Set.of());
    }

    /**
     * A context that knows nothing beyond the asked entries.
     *
     * @return the empty context
     */
    public static TargetContext none() {
        return new TargetContext(List.of(), Map.of(), Set.of());
    }
}
