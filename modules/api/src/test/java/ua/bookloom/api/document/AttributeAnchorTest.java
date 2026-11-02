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
        final var arg1 = List.of(0, -1);
        assertThatThrownBy(() -> new AttributeAnchor(arg1, "alt")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void constructor_blankAttribute_isRejected(String attribute) {
        final var arg1 = List.of(0);
        assertThatThrownBy(() -> new AttributeAnchor(arg1, attribute)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_mutableNodePath_isDefensivelyCopied() {
        final List<Integer> mutable = new ArrayList<>(List.of(0, 1));
        final AttributeAnchor anchor = new AttributeAnchor(mutable, "alt");

        mutable.add(99);

        assertThat(anchor.nodePath()).containsExactly(0, 1);
    }
}
