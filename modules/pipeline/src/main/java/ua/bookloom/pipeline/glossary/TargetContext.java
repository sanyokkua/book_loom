package ua.bookloom.pipeline.glossary;

import java.util.LinkedHashMap;
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
 * @param commonWords by entry id, the words of a name that the book also writes in lower case, so they are common words
 *     to translate and not letters to sound out; an entry without any is not in it
 */
public record TargetContext(
        List<GlossaryEntry> held,
        Map<String, Reading> readings,
        Set<String> verified,
        Map<String, List<String>> commonWords) {

    /** Copies the collections. */
    public TargetContext {
        held = List.copyOf(Objects.requireNonNull(held, "held"));
        readings = Map.copyOf(Objects.requireNonNull(readings, "readings"));
        verified = Set.copyOf(Objects.requireNonNull(verified, "verified"));
        commonWords = Map.copyOf(Objects.requireNonNull(commonWords, "commonWords"));
    }

    /**
     * A context whose entries hold no common words.
     *
     * @param held every glossary entry of the project; never null
     * @param readings the term reading of an entry by id; never null
     * @param verified the ids the readings decided are proper names; never null
     */
    public TargetContext(
            final List<GlossaryEntry> held, final Map<String, Reading> readings, final Set<String> verified) {
        this(held, readings, verified, Map.of());
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
     * The held entries with each asked one standing in for its held version, so a cluster is read from what the call
     * knows.
     *
     * @param asked the entries a call asks about; never null
     * @return the held entries, then the asked ones not held, each id once; never null
     */
    public List<GlossaryEntry> heldWith(final List<GlossaryEntry> asked) {
        final Map<String, GlossaryEntry> byId = new LinkedHashMap<>();
        held.forEach(entry -> byId.put(entry.id(), entry));
        asked.forEach(entry -> byId.put(entry.id(), entry));
        return List.copyOf(byId.values());
    }

    /**
     * A context that knows nothing beyond the asked entries.
     *
     * @return the empty context
     */
    public static TargetContext none() {
        return new TargetContext(List.of(), Map.of(), Set.of(), Map.of());
    }

    /**
     * This context with the common words of the entries a call asks about.
     *
     * @param words the common words by entry id; never null
     * @return the same facts with those words
     */
    public TargetContext withCommonWords(final Map<String, List<String>> words) {
        return new TargetContext(held, readings, verified, words);
    }

    /** The common words of the entry's term, in the order it writes them; empty when it holds none. */
    List<String> commonWordsOf(final GlossaryEntry entry) {
        return commonWords.getOrDefault(entry.id(), List.of());
    }
}
