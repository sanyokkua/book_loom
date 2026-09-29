package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.WholeWord;

/**
 * The text of every source segment decided so far, each placeholder token read as one space so a footnote marker
 * glues no word, with a running count of how often each glossary term
 * occurs in it. A count remembers how far into the text it has scanned, so a refresh reads only what was decided
 * since the last one — while a term the person adds later is still counted from the first segment.
 */
final class DecidedSource {

    private final List<String> texts = new ArrayList<>();
    private final Map<String, TermCount> counts = new HashMap<>();

    void add(final String masked) {
        texts.add(Tokens.replace(masked, " "));
    }

    int occurrences(final String term) {
        if (term.isBlank()) {
            return 0;
        }
        final TermCount count = counts.computeIfAbsent(term, key -> new TermCount());
        for (; count.scanned < texts.size(); count.scanned++) {
            final var matcher = WholeWord.pattern(term).matcher(texts.get(count.scanned));
            while (matcher.find()) {
                count.total++;
            }
        }
        return count.total;
    }

    private static final class TermCount {
        private int total;
        private int scanned;
    }
}
