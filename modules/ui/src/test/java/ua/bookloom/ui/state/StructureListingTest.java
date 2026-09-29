package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.StructureNode;

/** The flat listing the structure screen shows, built from a book's profile with nothing invented. */
class StructureListingTest {

    private static BookProfile profileOf(final StructureNode... nodes) {
        return new BookProfile(
                "Title", "Author", null, List.of(nodes), new BookStats(0, 0, 0, 0, 0, 0, 0, 0, Set.of()), Set.of());
    }

    private static StructureNode node(final String title, final Integer segments) {
        return new StructureNode(title, null, segments, List.of());
    }

    // IF the rows were reordered, retitled or miscounted, THEN the screen would misreport how the book is structured.
    @Test
    void of_threeTopLevelNodes_yieldsOneRowEachInOrderAndTheSumAsTotal() {
        final StructureListing listing =
                StructureListing.of(profileOf(node("Letter 1", 2), node("Chapter 1", 3), node("Chapter 2", 4)));

        assertThat(listing.rows())
                .containsExactly(
                        new StructureRow("Letter 1", 2),
                        new StructureRow("Chapter 1", 3),
                        new StructureRow("Chapter 2", 4));
        assertThat(listing.totalSegments()).isEqualTo(9);
    }

    // IF the nested children of a node became rows, THEN the flat screen would list a chapter's sections as chapters.
    @Test
    void of_nodeWithChildren_listsOnlyTheTopLevelNode() {
        final StructureNode part =
                new StructureNode("Part I", null, 5, List.of(node("Section 1", 2), node("Section 2", 3)));

        final StructureListing listing = StructureListing.of(profileOf(part));

        assertThat(listing.rows()).containsExactly(new StructureRow("Part I", 5));
        assertThat(listing.totalSegments()).isEqualTo(5);
    }

    // IF a node that only points into a unit counted elsewhere were given a count, THEN the total would count twice.
    @Test
    void of_nodeWithNoCountOfItsOwn_showsAZero() {
        final StructureNode entry = new StructureNode("Chapter 1", "unit-0", null, List.of());

        final StructureListing listing = StructureListing.of(profileOf(entry, node("Chapter 2", 2)));

        assertThat(listing.rows()).containsExactly(new StructureRow("Chapter 1", 0), new StructureRow("Chapter 2", 2));
        assertThat(listing.totalSegments()).isEqualTo(2);
    }

    // IF a book with nothing in it invented a row or a count, THEN the screen would show structure that is not there.
    @Test
    void of_profileWithNoStructure_hasNoRowsAndAZeroTotal() {
        final StructureListing listing = StructureListing.of(profileOf());

        assertThat(listing.rows()).isEmpty();
        assertThat(listing.totalSegments()).isZero();
    }

    // IF the listing kept the caller's list, THEN a later change to it would silently alter what the screen shows.
    @Test
    void constructor_callersListChangedAfterwards_doesNotChangeTheListing() {
        final List<StructureRow> source = new ArrayList<>(List.of(new StructureRow("a", 3)));

        final StructureListing listing = new StructureListing(source);
        source.add(new StructureRow("b", 4));

        assertThat(listing.rows()).containsExactly(new StructureRow("a", 3));
        assertThatThrownBy(() -> listing.rows().add(new StructureRow("c", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // IF the total were stored beside the rows, THEN a caller could build a listing that contradicts itself.
    @Test
    void totalSegments_builtFromRowsAlone_isTheSumOfTheirCounts() {
        final StructureListing listing =
                new StructureListing(List.of(new StructureRow("a", 3), new StructureRow("b", 4)));

        assertThat(listing.totalSegments()).isEqualTo(7);
    }
}
