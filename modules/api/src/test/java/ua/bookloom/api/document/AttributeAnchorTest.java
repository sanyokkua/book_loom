package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@code AttributeAnchor}'s construction-time invariants; the write-back behaviour is proven in {@code :document}. */
class AttributeAnchorTest {

    @Test
    void constructor_negativeNodePathEntry_isRejected() {
        assertThatThrownBy(() -> new AttributeAnchor(List.of(0, -1), "alt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void constructor_blankAttribute_isRejected(String attribute) {
        assertThatThrownBy(() -> new AttributeAnchor(List.of(0), attribute))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_mutableNodePath_isDefensivelyCopied() {
        final List<Integer> mutable = new ArrayList<>(List.of(0, 1));
        final AttributeAnchor anchor = new AttributeAnchor(mutable, "alt");

        mutable.add(99);

        assertThat(anchor.nodePath()).containsExactly(0, 1);
    }
}
