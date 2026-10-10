package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;

/**
 * Which rows the last operation added or changed, kept until the next operation starts, for the marker on the row and
 * the "Changed in last run" filter. The glossary and the recurring terms each have a side. FX thread only.
 */
public final class ChangeMarks {

    private final Side glossary = new Side();
    private final Side terms = new Side();

    /** One list's marks. */
    public static final class Side {

        private final ObservableSet<String> keys = FXCollections.observableSet();
        private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper();
        private final BooleanProperty onlyChanged = new SimpleBooleanProperty();

        private Side() {
            keys.addListener((SetChangeListener<String>) change -> {
                revision.set(revision.get() + 1);
                if (keys.isEmpty()) {
                    onlyChanged.set(false);
                }
            });
        }

        /**
         * Whether the last operation changed a row.
         *
         * @param key the row's key: its entry id, or a term's normalized key
         * @return {@code true} if the row is marked, {@code false} otherwise
         */
        public boolean isMarked(final String key) {
            return keys.contains(Objects.requireNonNull(key, "key"));
        }

        /**
         * How many rows are marked.
         *
         * @return the count
         */
        public int size() {
            return keys.size();
        }

        /**
         * A counter that grows whenever the marks change, for a view that redraws or refilters on it.
         *
         * @return a read-only counter
         */
        public ReadOnlyIntegerProperty revision() {
            return revision.getReadOnlyProperty();
        }

        /**
         * Whether the list shows only the marked rows.
         *
         * @return the switch, which falls back to {@code false} once no row is marked
         */
        public BooleanProperty onlyChanged() {
            return onlyChanged;
        }

        void mark(final String key) {
            keys.add(key);
        }

        void unmark(final String key) {
            keys.remove(key);
        }

        void clear() {
            keys.clear();
            onlyChanged.set(false);
        }
    }

    /**
     * The glossary's marks.
     *
     * @return the side keyed by entry id
     */
    public Side glossary() {
        return glossary;
    }

    /**
     * The recurring terms' marks.
     *
     * @return the side keyed by {@link ua.bookloom.api.project.LexiconEntry#keyOf(String)}
     */
    public Side terms() {
        return terms;
    }

    void clear() {
        glossary.clear();
        terms.clear();
    }
}
