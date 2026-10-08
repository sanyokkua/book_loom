package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.LexiconEntry;

/**
 * What the run learned of the whole book that a check of one paragraph is shown besides its names: the established
 * renderings of recurring terms and the rolling summary of the book.
 *
 * @param lexicon every lexicon entry as the pass starts
 * @param summary the rolling summary in the target language, or null when the run wrote none
 */
record BookContext(List<LexiconEntry> lexicon, @Nullable String summary) {

    /** Copies the lexicon. */
    BookContext {
        lexicon = List.copyOf(Objects.requireNonNull(lexicon, "lexicon"));
    }
}
