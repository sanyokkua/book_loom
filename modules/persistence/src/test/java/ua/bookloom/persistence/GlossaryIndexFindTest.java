package ua.bookloom.persistence;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** The lookup refuses a missing key at its boundary. */
class GlossaryIndexFindTest {

    @Test
    void find_nullKey_isRefused() {
        final Function<String, Object> lookup = GlossaryIndex.of(List.of())::find;

        assertThatNullPointerException().isThrownBy(() -> lookup.apply(null)).withMessageContaining("key");
    }
}
