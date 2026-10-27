package ua.bookloom.pipeline.glossary;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * One entry as a many-entry store found it and as it leaves it, so what follows a change (a deferral, a shared gender)
 * can be worked out after all of them are written.
 *
 * @param before the entry as held, or null for a new one
 * @param after the entry as stored
 */
record EntryChange(@Nullable GlossaryEntry before, GlossaryEntry after) {

    EntryChange {
        Objects.requireNonNull(after, "after");
    }
}
