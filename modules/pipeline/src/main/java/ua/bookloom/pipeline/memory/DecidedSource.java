package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ua.bookloom.pipeline.WholeWord;

/**
 * The display text of every source segment decided so far, with a running count of how often each glossary term
 * occurs in it. A count remembers how far into the text it has scanned, so a refresh reads only what was decided
 * since the last one — while a term the person adds later is still counted from the first segment.
 */
final class DecidedSource {

    private final List<String> texts = new ArrayList<>();
    private final Map<String, TermCount> counts = new HashMap<>();

    void add(final String displayText) {
        texts.add(displayText);
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
