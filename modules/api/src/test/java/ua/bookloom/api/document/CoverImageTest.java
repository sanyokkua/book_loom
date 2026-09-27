package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * {@code CoverImage}'s defensive-copy and content-equality invariants.
 */
class CoverImageTest {

    @Test
    void constructor_mutatingSourceArrayAfterConstruction_doesNotChangeStoredBytes() {
        final byte[] source = {1, 2, 3};
        final CoverImage cover = new CoverImage("OEBPS/cover.jpg", "image/jpeg", source);

        source[0] = 99;

        assertThat(cover.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void bytes_mutatingReturnedArray_doesNotChangeStoredBytes() {
        final CoverImage cover = new CoverImage("OEBPS/cover.jpg", "image/jpeg", new byte[] {1, 2, 3});

        final byte[] returned = cover.bytes();
        returned[0] = 99;

        assertThat(cover.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void equals_sameContentDifferentArrayInstances_areEqualWithSameHashCode() {
        final CoverImage first = new CoverImage("OEBPS/cover.jpg", "image/jpeg", new byte[] {1, 2, 3});
        final CoverImage second =
                new CoverImage("OEBPS/cover.jpg", "image/jpeg", Arrays.copyOf(new byte[] {1, 2, 3}, 3));

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
