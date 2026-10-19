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

/** The tree listing the structure screen shows, built from a book's profile with nothing invented. */
class StructureListingTest {

    private static BookProfile profileOf(final StructureNode... nodes) {
        return new BookProfile(
                "Title", "Author", null, List.of(nodes), new BookStats(0, 0, 0, 0, 0, 0, 0, 0, Set.of()), Set.of());
    }

    private static StructureNode node(final String title, final Integer segments) {
        return new StructureNode(title, null, segments, List.of());
    }

    // IF the roots were reordered, retitled or miscounted, THEN the screen would misreport how the book is structured.
    @Test
    void of_threeTopLevelNodes_keepsThemInOrderAndSumsTheirCounts() {
        final StructureListing listing =
                StructureListing.of(profileOf(node("Letter 1", 2), node("Chapter 1", 3), node("Chapter 2", 4)));

        assertThat(listing.roots())
                .extracting(StructureNode::title)
                .containsExactly("Letter 1", "Chapter 1", "Chapter 2");
        assertThat(listing.totalSegments()).isEqualTo(9);
    }

    // IF a child's count were added to the total besides its parent's, THEN the total would count it twice.
    @Test
    void totalSegments_nodeWithChildren_countsOnlyTheTopLevelNode() {
        final StructureNode part =
                new StructureNode("Part I", null, 8, List.of(node("Section 1", 2), node("Section 2", 3)));

        final StructureListing listing = StructureListing.of(profileOf(part));

        assertThat(listing.roots()).containsExactly(part);
        assertThat(listing.totalSegments()).isEqualTo(8);
        assertThat(listing.nodeCount()).isEqualTo(3);
    }

    // IF a node that only points into a unit counted elsewhere were given a count, THEN the total would count twice.
    @Test
    void totalSegments_nodeWithNoCountOfItsOwn_addsNothing() {
        final StructureNode entry = new StructureNode("Chapter 1", "unit-0", null, List.of());

        final StructureListing listing = StructureListing.of(profileOf(entry, node("Chapter 2", 2)));

        assertThat(listing.totalSegments()).isEqualTo(2);
    }

    // IF a book with nothing in it invented a node or a count, THEN the screen would show structure that is not there.
    @Test
    void of_profileWithNoStructure_hasNoRootsAndAZeroTotal() {
        final StructureListing listing = StructureListing.of(profileOf());

        assertThat(listing.roots()).isEmpty();
        assertThat(listing.totalSegments()).isZero();
        assertThat(listing.nodeCount()).isZero();
    }

    // IF the listing kept the caller's list, THEN a later change to it would silently alter what the screen shows.
    @Test
    void constructor_callersListChangedAfterwards_doesNotChangeTheListing() {
        final List<StructureNode> source = new ArrayList<>(List.of(node("a", 3)));

        final StructureListing listing = new StructureListing(source);
        source.add(node("b", 4));

        assertThat(listing.roots()).containsExactly(node("a", 3));
        assertThatThrownBy(() -> listing.roots().add(node("c", 1))).isInstanceOf(UnsupportedOperationException.class);
    }

    // IF a grouping node listed nothing, THEN picking a part would show an empty list although its chapters have text.
    @Test
    void unitsOf_groupingNodeWithoutAUnit_listsItsDescendantsUnitsOnceInOrder() {
        final StructureNode part = new StructureNode(
                "Part I",
                null,
                null,
                List.of(
                        new StructureNode("Chapter 1", "u1", 3, List.of()),
                        new StructureNode(
                                "Chapter 2", "u2", 4, List.of(new StructureNode("Scene", "u2", null, List.of()))),
                        new StructureNode("Notes", null, null, List.of())));

        assertThat(StructureListing.unitsOf(part)).containsExactly("u1", "u2");
    }

    // IF a chapter with a unit also listed its children's units, THEN a picked chapter would show other chapters' text.
    @Test
    void unitsOf_nodeWithItsOwnUnit_listsOnlyThatUnit() {
        final StructureNode chapter =
                new StructureNode("Chapter", "u1", 3, List.of(new StructureNode("Section", "u9", null, List.of())));

        assertThat(StructureListing.unitsOf(chapter)).containsExactly("u1");
    }
}
