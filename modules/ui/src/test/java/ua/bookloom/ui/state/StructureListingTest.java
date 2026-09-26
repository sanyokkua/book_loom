package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.ui.BookFixtures;

/** The flat listing the structure screen shows, built from a parsed document with nothing invented. */
class StructureListingTest {

    private static final List<String> ELEVEN_HREFS = List.of(
            "OEBPS/chapter-01.xhtml",
            "OEBPS/chapter-02.xhtml",
            "OEBPS/chapter-03.xhtml",
            "OEBPS/chapter-04.xhtml",
            "OEBPS/chapter-05.xhtml",
            "OEBPS/chapter-06.xhtml",
            "OEBPS/chapter-07.xhtml",
            "OEBPS/chapter-08.xhtml",
            "OEBPS/chapter-09.xhtml",
            "OEBPS/chapter-10.xhtml",
            "OEBPS/chapter-11.xhtml");

    // IF the rows were reordered, renumbered or miscounted, THEN the screen would misreport how the book was parsed.
    @Test
    void of_elevenUnits_yieldsOneRowPerUnitInReadingOrderWithItsOwnCountAndTheSumAsTotal() {
        final Document document = BookFixtures.book(
                "eleven", BookFormat.EPUB, ELEVEN_HREFS, 120, 95, 130, 110, 100, 115, 105, 125, 135, 90, 115);

        final StructureListing listing = StructureListing.of(document);

        assertThat(listing.rows())
                .containsExactly(
                        new StructureRow("OEBPS/chapter-01.xhtml", 0, 120),
                        new StructureRow("OEBPS/chapter-02.xhtml", 1, 95),
                        new StructureRow("OEBPS/chapter-03.xhtml", 2, 130),
                        new StructureRow("OEBPS/chapter-04.xhtml", 3, 110),
                        new StructureRow("OEBPS/chapter-05.xhtml", 4, 100),
                        new StructureRow("OEBPS/chapter-06.xhtml", 5, 115),
                        new StructureRow("OEBPS/chapter-07.xhtml", 6, 105),
                        new StructureRow("OEBPS/chapter-08.xhtml", 7, 125),
                        new StructureRow("OEBPS/chapter-09.xhtml", 8, 135),
                        new StructureRow("OEBPS/chapter-10.xhtml", 9, 90),
                        new StructureRow("OEBPS/chapter-11.xhtml", 10, 115));
        assertThat(listing.totalSegments()).isEqualTo(1240);
    }

    // IF a book with nothing in it invented a row or a count, THEN the screen would show structure that is not there.
    @Test
    void of_documentWithNoUnits_hasNoRowsAndAZeroTotal() {
        final Document document = BookFixtures.book("empty", BookFormat.TXT, List.of());

        final StructureListing listing = StructureListing.of(document);

        assertThat(listing.rows()).isEmpty();
        assertThat(listing.totalSegments()).isZero();
    }

    // IF a unit without segments were dropped or counted as one, THEN the rows would not match the parse.
    @Test
    void of_unitWithNoSegments_keepsItsRowWithACountOfZero() {
        final Document document = BookFixtures.book("gap", BookFormat.EPUB, List.of("cover.xhtml", "ch1.xhtml"), 0, 2);

        final StructureListing listing = StructureListing.of(document);

        assertThat(listing.rows())
                .containsExactly(new StructureRow("cover.xhtml", 0, 0), new StructureRow("ch1.xhtml", 1, 2));
        assertThat(listing.totalSegments()).isEqualTo(2);
    }

    // IF the listing kept the caller's list, THEN a later change to it would silently alter what the screen shows.
    @Test
    void constructor_callersListChangedAfterwards_doesNotChangeTheListing() {
        final List<StructureRow> source = new ArrayList<>(List.of(new StructureRow("a.xhtml", 0, 3)));

        final StructureListing listing = new StructureListing(source);
        source.add(new StructureRow("b.xhtml", 1, 4));

        assertThat(listing.rows()).containsExactly(new StructureRow("a.xhtml", 0, 3));
        assertThatThrownBy(() -> listing.rows().add(new StructureRow("c.xhtml", 2, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // IF the total were stored beside the rows, THEN a caller could build a listing that contradicts itself.
    @Test
    void totalSegments_builtFromRowsAlone_isTheSumOfTheirCounts() {
        final StructureListing listing =
                new StructureListing(List.of(new StructureRow("a.xhtml", 0, 3), new StructureRow("b.xhtml", 1, 4)));

        assertThat(listing.totalSegments()).isEqualTo(7);
    }
}
