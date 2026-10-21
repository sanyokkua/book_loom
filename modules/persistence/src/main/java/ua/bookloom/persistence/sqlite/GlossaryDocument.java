package ua.bookloom.persistence.sqlite;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ua.bookloom.api.project.GlossaryEntry;
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

    Optional<GlossaryEntry> findByKey(final String key) {
        return entries.stream()
                .filter(entry -> GlossaryKeys.of(entry.term()).equals(key))
                .findFirst();
    }

    boolean wasRemoved(final String key) {
        return removed.contains(key);
    }

    /** Appends an entry and forgets that its term was removed. */
    GlossaryDocument added(final GlossaryEntry entry) {
        final List<GlossaryEntry> next = new ArrayList<>(entries);
        next.add(entry);
        final Set<String> stillRemoved = new LinkedHashSet<>(removed);
        stillRemoved.remove(GlossaryKeys.of(entry.term()));
        return new GlossaryDocument(next, List.copyOf(stillRemoved));
    }

    /** Replaces the entry with the same id in place, keeping its position. */
    GlossaryDocument replaced(final GlossaryEntry entry) {
        final List<GlossaryEntry> next = entries.stream()
                .map(held -> held.id().equals(entry.id()) ? entry : held)
                .toList();
        return new GlossaryDocument(next, removed);
    }

    /** Drops the entry with the id and remembers its term as removed. */
    GlossaryDocument without(final GlossaryEntry entry) {
        final List<GlossaryEntry> next =
                entries.stream().filter(held -> !held.id().equals(entry.id())).toList();
        final Set<String> nowRemoved = new LinkedHashSet<>(removed);
        nowRemoved.add(GlossaryKeys.of(entry.term()));
        return new GlossaryDocument(next, List.copyOf(nowRemoved));
    }

    Optional<GlossaryEntry> findById(final String entryId) {
        return entries.stream().filter(entry -> entry.id().equals(entryId)).findFirst();
    }
}
