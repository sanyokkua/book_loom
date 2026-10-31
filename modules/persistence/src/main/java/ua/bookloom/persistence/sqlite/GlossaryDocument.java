package ua.bookloom.persistence.sqlite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.persistence.GlossaryIndex;
import ua.bookloom.util.text.GlossaryKeys;

/**
 * A project's glossary as one stored JSON document, so every change is one read-modify-write inside one write
 * transaction and a person's edit and a run's addition never overwrite each other ({@code design.md} D9). Public only
 * because Jackson reads it through its canonical constructor.
 *
 * @param entries the entries in the order they were added
 * @param removed the {@link GlossaryKeys} keys of the terms removed and not added again since
 */
public record GlossaryDocument(List<GlossaryEntry> entries, List<String> removed) {

    /** Copies both lists, so a stored document never shares state with its caller. */
    public GlossaryDocument {
        entries = List.copyOf(entries);
        removed = List.copyOf(removed);
    }

    static GlossaryDocument empty() {
        return new GlossaryDocument(List.of(), List.of());
    }

    /** The entry whose lemma has the key, else the first that lists it as a form ({@link GlossaryIndex}). */
    Optional<GlossaryEntry> findByKey(final String key) {
        return GlossaryIndex.of(entries).find(key);
    }

    /** The held entry an incoming entry's lemma or one of its forms names. */
    Optional<GlossaryEntry> holderOf(final GlossaryEntry entry) {
        return GlossaryIndex.of(entries).holderOf(entry);
    }

    boolean wasRemoved(final String key) {
        return removed.contains(key);
    }

    /** Appends an entry and forgets that its term and forms were removed. */
    GlossaryDocument added(final GlossaryEntry entry) {
        final List<GlossaryEntry> next = new ArrayList<>(entries);
        next.add(entry);
        final Set<String> stillRemoved = new LinkedHashSet<>(removed);
        GlossaryIndex.keysOf(entry).forEach(stillRemoved::remove);
        return new GlossaryDocument(next, List.copyOf(stillRemoved));
    }

    /**
     * Puts many entries at once: one whose id is held replaces it in place, any other is appended and its term and
     * forms are no longer remembered as removed. Indexed once, so thousands of entries cost one pass, not one pass
     * each.
     *
     * @return the new document, or empty when an entry's term or one of its forms is held under another id, as a lemma
     *     or a form
     */
    Optional<GlossaryDocument> putAll(final List<GlossaryEntry> puts) {
        final Map<String, Integer> positionById = new HashMap<>();
        final Map<String, String> idByKey = new HashMap<>();
        final List<GlossaryEntry> next = new ArrayList<>(entries);
        for (int at = 0; at < next.size(); at++) {
            final GlossaryEntry held = next.get(at);
            positionById.put(held.id(), at);
            GlossaryIndex.keysOf(held).forEach(key -> idByKey.putIfAbsent(key, held.id()));
        }
        final Set<String> stillRemoved = new LinkedHashSet<>(removed);
        for (final GlossaryEntry entry : puts) {
            final List<String> keys = GlossaryIndex.keysOf(entry);
            if (keys.stream()
                    .map(key -> idByKey.getOrDefault(key, entry.id()))
                    .anyMatch(id -> !id.equals(entry.id()))) {
                return Optional.empty();
            }
            keys.forEach(key -> idByKey.putIfAbsent(key, entry.id()));
            final Integer at = positionById.get(entry.id());
            if (at == null) {
                positionById.put(entry.id(), next.size());
                next.add(entry);
                keys.forEach(stillRemoved::remove);
            } else {
                next.set(at, entry);
            }
        }
        return Optional.of(new GlossaryDocument(next, List.copyOf(stillRemoved)));
    }

    /** Replaces the entry with the same id in place, keeping its position. */
    GlossaryDocument replaced(final GlossaryEntry entry) {
        final List<GlossaryEntry> next = entries.stream()
                .map(held -> held.id().equals(entry.id()) ? entry : held)
                .toList();
        return new GlossaryDocument(next, removed);
    }

    /** Drops the entry with the id and remembers its term and forms as removed. */
    GlossaryDocument without(final GlossaryEntry entry) {
        final List<GlossaryEntry> next =
                entries.stream().filter(held -> !held.id().equals(entry.id())).toList();
        final Set<String> nowRemoved = new LinkedHashSet<>(removed);
        nowRemoved.addAll(GlossaryIndex.keysOf(entry));
        return new GlossaryDocument(next, List.copyOf(nowRemoved));
    }

    Optional<GlossaryEntry> findById(final String entryId) {
        return entries.stream().filter(entry -> entry.id().equals(entryId)).findFirst();
    }
}
