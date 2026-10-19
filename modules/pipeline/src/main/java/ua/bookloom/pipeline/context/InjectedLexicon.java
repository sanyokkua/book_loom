package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * Picks the recurring-term renderings a prompt lists: the established rendering of each term the text names,
 * never one the glossary already decides — a glossary entry is the person's word, the lexicon only keeps the book
 * consistent where nobody chose.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InjectedLexicon {

    /**
     * The established renderings of the recurring terms some segments name.
     *
     * @param segments the segments whose text decides which terms are present
     * @param lexicon every lexicon entry
     * @param glossary every glossary entry, whose terms the lexicon never overrides
     * @return one rendering per present term that has an established one and no glossary entry, in lexicon order;
     *     never null, empty when none is
     */
    public static List<SnapshotRendering> select(
            final List<Segment> segments, final List<LexiconEntry> lexicon, final List<GlossaryEntry> glossary) {
        return select(segments, lexicon, glossary, LexiconFilter.NONE);
    }

    /**
     * As {@link #select(List, List, List)}, leaving out the terms and renderings the filter refuses.
     *
     * @param segments the segments whose text decides which terms are present
     * @param lexicon every lexicon entry
     * @param glossary every glossary entry, whose terms the lexicon never overrides
     * @param filter what keeps function words and declined forms out
     * @return the renderings shown; never null, empty when none is
     */
    public static List<SnapshotRendering> select(
            final List<Segment> segments,
            final List<LexiconEntry> lexicon,
            final List<GlossaryEntry> glossary,
            final LexiconFilter filter) {
        final List<String> texts = Tokens.visibleTexts(segments);
        final List<SnapshotRendering> selected = lexicon.stream()
                .filter(entry -> texts.stream().anyMatch(text -> TermMatch.isNamedIn(entry.term(), text)))
                .filter(entry -> !heldByGlossary(entry, glossary))
                .flatMap(entry -> renderingOf(entry, filter).stream())
                .toList();
        log.trace("Lexicon scoped to the text offered={} selected={}", lexicon.size(), selected.size());
        return selected;
    }

    private static Optional<SnapshotRendering> renderingOf(final LexiconEntry entry, final LexiconFilter filter) {
        final Optional<String> rendering = entry.established();
        if (rendering.isPresent() && !filter.admits(entry.term(), rendering.get())) {
            log.trace(
                    "Lexicon entry {} -> {} is a function word or a declined form; left out",
                    entry.term(),
                    rendering.get());
            return Optional.empty();
        }
        return rendering.map(text -> new SnapshotRendering(entry.term(), text));
    }

    /**
     * How a prompt lists one rendering.
     *
     * @param rendering the non-null rendering
     * @return {@code term → rendering}
     */
    public static String line(final SnapshotRendering rendering) {
        return rendering.term() + " → " + rendering.rendering();
    }

    private static boolean heldByGlossary(final LexiconEntry entry, final List<GlossaryEntry> glossary) {
        final String key = LexiconEntry.keyOf(entry.term());
        return glossary.stream()
                .anyMatch(held -> LexiconEntry.keyOf(held.term()).equals(key));
    }
}
