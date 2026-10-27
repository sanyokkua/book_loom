package ua.bookloom.ui.state;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.LexiconService;

/**
 * How often the book writes each recurring term, which the Used column shows. The counts come from the book's index
 * through the lexicon service, are read off the FX thread and published only while the opening of the screen that asked
 * is still current. A term with no count yet has none here, so the column shows nothing for it, never a zero. At most one
 * read runs; a change that arrives meanwhile is read once after it. FX thread only.
 */
@Slf4j
public final class RecurringUsage {

    private final LexiconService lexicon;
    private final GlossaryCalls calls;
    private final Supplier<String> project;
    private final Supplier<Consumer<Runnable>> current;
    private final ReadOnlyIntegerWrapper revision = new ReadOnlyIntegerWrapper();
    private Map<String, Integer> counts = Map.of();
    private boolean loading;
    private boolean again;

    RecurringUsage(
            final LexiconService lexicon,
            final GlossaryCalls calls,
            final Supplier<String> project,
            final Supplier<Consumer<Runnable>> current) {
        this.lexicon = Objects.requireNonNull(lexicon, "lexicon");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.project = Objects.requireNonNull(project, "project");
        this.current = Objects.requireNonNull(current, "current");
    }

    /** {@return the counts by {@code LexiconEntry.keyOf(term)} as last read; empty before the first read} */
    public Map<String, Integer> counts() {
        return counts;
    }

    /** {@return a number that changes each time new counts arrive, for the table to redraw on} */
    public ReadOnlyIntegerProperty revision() {
        return revision.getReadOnlyProperty();
    }

    /** Forgets the counts of the project that was shown, as another one opens. */
    void clear() {
        log.debug("recurring term counts dropped");
        counts = Map.of();
        revision.set(revision.get() + 1);
    }

    /** Reads the counts again, now or once the read under way is done. */
    void reload() {
        if (loading) {
            again = true;
            return;
        }
        final String id = project.get();
        if (id.isEmpty()) {
            log.debug("recurring term counts not read: no project is open");
            return;
        }
        loading = true;
        final long begun = System.nanoTime();
        log.debug("reading the recurring term counts of project {}", id);
        calls.run(
                "lexicon usage",
                () -> lexicon.usage(id),
                answer -> current.get().accept(() -> read(id, answer, begun)));
    }

    private void read(final String id, final Result<Map<String, Integer>> answer, final long begun) {
        loading = false;
        if (answer.isOk()) {
            counts = Map.copyOf(Objects.requireNonNull(answer.data(), "data"));
            revision.set(revision.get() + 1);
            log.debug(
                    "read {} recurring term counts of project {} in {} ms",
                    counts.size(),
                    id,
                    (System.nanoTime() - begun) / 1_000_000);
        } else {
            log.warn(
                    "the recurring term counts of project {} could not be read: {}",
                    id,
                    Objects.requireNonNull(answer.error()).code());
        }
        if (again) {
            again = false;
            reload();
        }
    }
}
