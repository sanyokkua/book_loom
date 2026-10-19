package ua.bookloom.api.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Exactly what one scan, review or translate action did to a list of entries, so a screen can show and revert each
 * change instead of guessing from a count.
 *
 * @param <T> the entry type, a glossary entry or a lexicon entry
 * @param added the entries the action created, in the order the list now holds them; never null
 * @param removed the entries the action deleted, as they were, each with the model's reason; never null
 * @param changed the entries the action rewrote, each with its state before and after; never null
 */
public record EntryChanges<T>(List<T> added, List<Removal<T>> removed, List<Change<T>> changed) {

    /**
     * One entry the action rewrote.
     *
     * @param <T> the entry type
     * @param before the entry as it was
     * @param after the entry as it is now
     * @param reason the model's own words for the change, or null when the action had none
     */
    public record Change<T>(T before, T after, @Nullable String reason) {

        /** Rejects a missing state. */
        public Change {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }

    /**
     * One entry the action deleted.
     *
     * @param <T> the entry type
     * @param entry the entry as it was
     * @param reason the model's own words for the removal, or null when the action had none
     */
    public record Removal<T>(T entry, @Nullable String reason) {

        /** Rejects a missing entry. */
        public Removal {
            Objects.requireNonNull(entry, "entry");
        }
    }

    /** Copies the lists. */
    public EntryChanges {
        added = List.copyOf(Objects.requireNonNull(added, "added"));
        removed = List.copyOf(Objects.requireNonNull(removed, "removed"));
        changed = List.copyOf(Objects.requireNonNull(changed, "changed"));
    }

    /**
     * The changes of an action that changed nothing.
     *
     * @param <T> the entry type
     * @return an empty set of changes
     */
    public static <T> EntryChanges<T> none() {
        return new EntryChanges<>(List.of(), List.of(), List.of());
    }

    /**
     * The changes of an action that only created entries.
     *
     * @param <T> the entry type
     * @param entries the non-null created entries
     * @return the changes
     */
    public static <T> EntryChanges<T> ofAdded(final List<T> entries) {
        return new EntryChanges<>(entries, List.of(), List.of());
    }

    /**
     * Compares a list before and after an action. An entry is the same entry when its key matches; it is changed when
     * it is not equal.
     *
     * @param <T> the entry type
     * @param before the non-null list as it was
     * @param after the non-null list as it is now
     * @param key the non-null identity of an entry across the two lists
     * @param reason the non-null function from an entry as it was to the model's reason for rewriting or removing it
     * @return what was added, removed and changed; an entry present in both and equal is not mentioned
     */
    public static <T> EntryChanges<T> between(
            final List<T> before,
            final List<T> after,
            final Function<T, String> key,
            final Function<T, @Nullable String> reason) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(reason, "reason");
        final Map<String, T> was = new HashMap<>();
        before.forEach(entry -> was.put(key.apply(entry), entry));
        final Map<String, T> now = new HashMap<>();
        after.forEach(entry -> now.put(key.apply(entry), entry));
        final List<T> added = after.stream()
                .filter(entry -> !was.containsKey(key.apply(entry)))
                .toList();
        final List<Removal<T>> removed = before.stream()
                .filter(entry -> !now.containsKey(key.apply(entry)))
                .map(entry -> new Removal<>(entry, reason.apply(entry)))
                .toList();
        final List<Change<T>> changed = new ArrayList<>();
        for (final T entry : before) {
            final T next = now.get(key.apply(entry));
            if (next != null && !next.equals(entry)) {
                changed.add(new Change<>(entry, next, reason.apply(entry)));
            }
        }
        return new EntryChanges<>(added, removed, changed);
    }

    /**
     * Whether the action changed nothing.
     *
     * @return {@code true} when nothing was added, removed or changed, {@code false} otherwise
     */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
    }

    /**
     * How many entries the action touched.
     *
     * @return the number added, removed and changed together
     */
    public int size() {
        return added.size() + removed.size() + changed.size();
    }
}
