package ua.bookloom.pipeline.glossary;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.index.WordCounts;

/**
 * The words of a place, an organisation or another name that the book also writes in lower case ({@code Houses} when
 * the book says {@code houses} too): they are ordinary words, so they are translated and never sounded out. The book
 * is the evidence, the same share ({@link FrequencyScan#LOWER_SHARE_LIMIT}) that keeps a word out of the name scan;
 * no word list is bundled. A person is never read this way, since a nickname is a name whatever it once meant.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CommonWords {

    private static final Set<TermType> TYPES = EnumSet.of(TermType.PLACE, TermType.ORGANISATION, TermType.OTHER);

    /**
     * The common words of each entry that holds some.
     *
     * @param entries the entries a call asks about
     * @param story the book's body, where the words are counted
     * @param language the language the book is read in, or null when it is not known
     * @return the words in the order the term writes them, by entry id; an entry with none is not in the map
     */
    static Map<String, List<String>> of(
            final List<GlossaryEntry> entries, final BookSlice story, @Nullable final String language) {
        final List<GlossaryEntry> candidates =
                entries.stream().filter(entry -> TYPES.contains(entry.type())).toList();
        if (candidates.isEmpty()) {
            return Map.of();
        }
        final WordCounts counts = WordCounts.of(story.index().scanReads(story.ordinals(), language));
        final Set<String> stop = StopWords.of(language);
        final Map<String, List<String>> found = new LinkedHashMap<>();
        for (final GlossaryEntry entry : candidates) {
            final List<String> common = Arrays.stream(entry.term().strip().split("\\s+"))
                    .filter(word -> !stop.contains(Occurrences.keyOf(word))
                            && counts.lowerShare(Occurrences.keyOf(word)) >= FrequencyScan.LOWER_SHARE_LIMIT)
                    .toList();
            if (!common.isEmpty()) {
                found.put(entry.id(), common);
            }
        }
        log.debug("Common words in {} of {} name entries", found.size(), candidates.size());
        log.trace("Common words by entry {}", found);
        return found;
    }
}
