package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.LexiconEntry;

/**
 * What the run learned of the whole book that a check of one paragraph is shown besides its names: the established
 * renderings of recurring terms.
 *
 * @param lexicon every lexicon entry as the pass starts
 */
record BookContext(List<LexiconEntry> lexicon) {

    /** Copies the lexicon. */
    BookContext {
        lexicon = List.copyOf(Objects.requireNonNull(lexicon, "lexicon"));
    }
}
