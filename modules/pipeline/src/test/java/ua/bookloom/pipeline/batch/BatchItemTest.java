package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BatchItemTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "a\"b", "a<b", "é"})
    void constructor_idThatNeedsEscaping_isRejected(final String id) {
        assertThatThrownBy(() -> new BatchItem(id, "text")).isInstanceOf(IllegalArgumentException.class);
    }
}
