package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code NodeAnchor}'s construction-time invariants.
 *
 * <p>No {@code // Covers: FR-*} marker: these are mechanical shape tests, not covering tests for a business
 * requirement — ADR-0025 is the rationale for the shape, and the obligations it carries are proven by the
 * write-back tests in {@code :document}.
 */
class NodeAnchorTest {

    @Test
    void constructor_negativeRunIndex_isRejected() {
        assertThatThrownBy(() -> new NodeAnchor(List.of(0, 1), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeNodePathEntry_isRejected() {
        assertThatThrownBy(() -> new NodeAnchor(List.of(0, -2, 1), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_mutableNodePath_isDefensivelyCopied() {
        final List<Integer> mutable = new ArrayList<>(List.of(0, 1));
        final NodeAnchor anchor = new NodeAnchor(mutable, 2);

        mutable.add(99);

        assertThat(anchor.nodePath()).containsExactly(0, 1);
    }

    @Test
    void constructor_emptyNodePathAndZeroRun_isAccepted() {
        assertThat(new NodeAnchor(List.of(), 0).runIndex()).isZero();
    }
}
