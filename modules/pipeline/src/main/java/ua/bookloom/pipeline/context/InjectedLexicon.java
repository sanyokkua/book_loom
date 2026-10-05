package ua.bookloom.pipeline.context;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * Picks the recurring-term renderings a chunk's prompt lists: the established rendering of each term the chunk names,
 * never one the glossary already decides — a glossary entry is the person's word, the lexicon only keeps the book
 * consistent where nobody chose.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class InjectedLexicon {

    static List<SnapshotRendering> select(
            final Chunk chunk, final List<LexiconEntry> lexicon, final List<GlossaryEntry> glossary) {
        final List<String> texts = chunk.segments().stream()
                .map(Segment::masked)
                .map(masked -> Tokens.replace(masked, " "))
                .toList();
        final List<SnapshotRendering> selected = lexicon.stream()
                .filter(entry -> texts.stream().anyMatch(text -> TermMatch.occursIn(entry.term(), text)))
                .filter(entry -> !heldByGlossary(entry, glossary))
                .flatMap(entry ->
                        entry.established().map(rendering -> new SnapshotRendering(entry.term(), rendering)).stream())
                .toList();
        log.trace("Lexicon renderings for the chunk offered={} selected={}", lexicon.size(), selected.size());
        return selected;
    }

    static String line(final SnapshotRendering rendering) {
        return rendering.term() + " → " + rendering.rendering();
    }

    private static boolean heldByGlossary(final LexiconEntry entry, final List<GlossaryEntry> glossary) {
        final String key = LexiconEntry.keyOf(entry.term());
        return glossary.stream()
                .anyMatch(held -> LexiconEntry.keyOf(held.term()).equals(key));
    }
}
