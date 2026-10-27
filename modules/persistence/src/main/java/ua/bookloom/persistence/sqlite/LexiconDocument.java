package ua.bookloom.persistence.sqlite;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import ua.bookloom.api.project.LexiconEntry;

/**
 * A project's recurring-term lexicon as one stored JSON document, so every change, a counted use included, is one
 * read-modify-write inside one write transaction ({@code design.md} D9). Public only because Jackson reads it
 * through its canonical constructor.
 *
 * @param entries the entries, one per {@link LexiconEntry#keyOf} key, in key order
 * @param removed the keys of the terms removed and not stored again since
 */
public record LexiconDocument(List<LexiconEntry> entries, List<String> removed) {

    /** Copies both lists, so a stored document never shares state with its caller. */
    public LexiconDocument {
        entries = List.copyOf(entries);
        removed = List.copyOf(removed);
    }

    static LexiconDocument empty() {
        return new LexiconDocument(List.of(), List.of());
    }

    Optional<LexiconEntry> find(final String key) {
        return entries.stream()
                .filter(entry -> LexiconEntry.keyOf(entry.term()).equals(key))
                .findFirst();
    }

    boolean wasRemoved(final String key) {
        return removed.contains(key);
    }

    /** Stores an entry in place of the one held for its key and forgets that the term was removed. */
    LexiconDocument stored(final LexiconEntry entry) {
        final String key = LexiconEntry.keyOf(entry.term());
        final List<LexiconEntry> next = Stream.concat(withoutKey(key), Stream.of(entry))
                .sorted(Comparator.comparing(held -> LexiconEntry.keyOf(held.term())))
                .toList();
        final Set<String> stillRemoved = new LinkedHashSet<>(removed);
        stillRemoved.remove(key);
        return new LexiconDocument(next, List.copyOf(stillRemoved));
    }

    /** Drops the entry held for a key and remembers the term as removed. */
    LexiconDocument without(final String key) {
        final Set<String> nowRemoved = new LinkedHashSet<>(removed);
        nowRemoved.add(key);
        return new LexiconDocument(withoutKey(key).toList(), List.copyOf(nowRemoved));
    }

    private Stream<LexiconEntry> withoutKey(final String key) {
        return entries.stream().filter(held -> !LexiconEntry.keyOf(held.term()).equals(key));
    }
}
