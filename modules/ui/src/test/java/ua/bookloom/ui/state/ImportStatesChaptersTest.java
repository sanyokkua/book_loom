package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.ui.BookFixtures;

/** A chapter is a top-level node of the structure, so the figure shown is the tree's own count. */
class ImportStatesChaptersTest {

    // IF nested nodes were counted, THEN the summary would say more chapters than the tree's top level shows.
    @Test
    void of_elevenTopLevelNodesTwoWithChildren_countsElevenChapters() {
        final List<StructureNode> nodes = IntStream.range(0, 11)
                .mapToObj(n -> new StructureNode(
                        "Chapter " + (n + 1),
                        "unit-" + n,
                        2,
                        n < 2
                                ? List.of(BookFixtures.leaf("Part " + n, 1), BookFixtures.leaf("Part " + n + "b", 1))
                                : List.of()))
                .toList();
        final ImportedBook book = BookFixtures.structured(
                "p1", BookFormat.EPUB, nodes, new BookStats(22, 1_200, 0, 0, 0, 0, 0, 0, Set.of()));

        final ImportState state = ImportStates.of("book.epub", book);

        assertThat(state)
                .isInstanceOfSatisfying(
                        ImportState.Detected.class,
                        detected -> assertThat(detected.card().chapters()).isEqualTo(11));
    }
}
