package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code StructureNode}'s defensive-copy and no-defaulting invariants.
 */
class StructureNodeTest {

    @Test
    void constructor_mutatingSourceChildrenAfterConstruction_doesNotChangeStoredChildren() {
        final StructureNode child = new StructureNode("Chapter 1", "ch1", 12, List.of());
        final List<StructureNode> children = new ArrayList<>(List.of(child));

        final StructureNode node = new StructureNode("Book", null, null, children);
        children.add(new StructureNode("Chapter 2", "ch2", 8, List.of()));

        assertThat(node.children()).containsExactly(child);
    }

    @Test
    void children_returned_isUnmodifiable() {
        final StructureNode node = new StructureNode("Book", null, null, List.of());

        assertThatThrownBy(() -> node.children().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_nullSegmentCount_isKeptAsNull() {
        final StructureNode node = new StructureNode("Part I", "part1", null, List.of());

        assertThat(node.segmentCount()).isNull();
    }

    @Test
    void constructor_emptyTitle_isKeptAsEmpty() {
        final StructureNode node = new StructureNode("", "ch1", 5, List.of());

        assertThat(node.title()).isEmpty();
    }
}
