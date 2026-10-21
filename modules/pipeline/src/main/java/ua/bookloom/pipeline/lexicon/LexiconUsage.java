package ua.bookloom.pipeline.lexicon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.Case;

/** How often the open book uses each recurring term, read from the book index. */
@Slf4j
// Checkstyle reads the source before Lombok adds the private constructor (see HashUtil).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LexiconUsage {

    // A one-word term is its word's postings (letter case ignored); a longer one is counted by its mentions.
    static Map<String, Integer> of(final List<LexiconEntry> entries, final BookIndex index) {
        final Map<String, Integer> usage = new LinkedHashMap<>();
        for (final LexiconEntry entry : entries) {
            final String term = entry.term();
            final boolean oneWord = term.strip().split("\\s+").length == 1;
            final int count = oneWord
                    ? index.postings(term).size()
                    : index.mentions(List.of(term), Case.INSENSITIVE).size();
            usage.put(LexiconEntry.keyOf(term), count);
        }
        log.debug("usage terms={}", usage.size());
        return usage;
    }
}
