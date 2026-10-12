package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentModule;

/**
 * The index counts every word the way {@code WordCounts} does over the same raw masked texts, on the fixture book in
 * its four formats and on a small invented multi-unit book.
 */
class BookIndexWordCountsTest {

    private static final Path FIXTURES = Path.of("../app/src/test/resources/fixtures/earth-gravity");
    private static final double EXACT = 1e-9;

    @ParameterizedTest
    @ValueSource(strings = {"earth-gravity.epub", "earth-gravity.fb2", "earth-gravity.md", "earth-gravity.txt"})
    void stats_fixtureBook_equalWordCountsOverEverySegment(final String fixture) {
        assertSameCounts(open(fixture));
    }

    @Test
    void stats_inventedMultiUnitBook_equalWordCountsOverEverySegment() {
        assertSameCounts(IndexTestBooks.sample());
    }

    @Test
    void stats_aBookWithHonorificsQuotesTokensAndPossessives_equalWordCounts() {
        assertSameCounts(IndexTestBooks.oneUnit(
                "Mr. Hale nodded. “Hale’s coat is here,” said Dr. Vance.",
                "She asked Hale⟦g1⟧Street about the bridge. The Bridge fell.",
                "He paused—Well, perhaps not. El-Barun lay east; the road to El-Barun was long.",
                "Don’t go. I’m late, she said. Ann, Bea and Cy sang."));
    }

    // IF the index and WordCounts read the same masked texts, THEN every word's counts agree.
    private static void assertSameCounts(final Document book) {
        final BookIndex index = BookIndex.of(book);
        final List<Occurrences.Read> reads = book.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .map(Segment::masked)
                .map(Occurrences::read)
                .toList();
        final WordCounts counts = WordCounts.of(reads);
        final Set<String> keys = reads.stream()
                .flatMap(read -> read.words().stream())
                .map(Occurrences.Word::key)
                .collect(Collectors.toSet());

        assertThat(keys).isNotEmpty().allSatisfy(key -> {
            final WordStats stats = index.stats(key);
            assertThat(stats.lowerShare()).as("lowerShare of %s", key).isCloseTo(counts.lowerShare(key), within(EXACT));
            assertThat(stats.capitalisedMidSentence())
                    .as("midSentence of %s", key)
                    .isEqualTo(counts.midSentence(key));
            assertThat(stats.standalone()).as("standalone of %s", key).isEqualTo(counts.standaloneMidSentence(key));
            assertThat(stats.total()).as("total of %s", key).isEqualTo(occurrencesOf(reads, key));
        });
    }

    private static long occurrencesOf(final List<Occurrences.Read> reads, final String key) {
        return reads.stream()
                .flatMap(read -> read.words().stream())
                .filter(word -> word.key().equals(key))
                .count();
    }

    private static Document open(final String fixture) {
        return Objects.requireNonNull(
                Guice.createInjector(new DocumentModule())
                        .getInstance(DocumentPort.class)
                        .open(FIXTURES.resolve(fixture))
                        .data(),
                "fixture");
    }
}
