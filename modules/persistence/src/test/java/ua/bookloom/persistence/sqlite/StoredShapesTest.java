package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoredShapesTest {

    private enum Colour {
        RED,
        BLUE
    }

    private record Inner(Colour colour, int weight) {}

    private record Small(String name, List<Inner> parts, Map<String, Integer> counts) {}

    private record SmallRenamed(String name, List<Inner> parts, Map<String, Integer> counts) {}

    private record SmallGrown(String name, List<Inner> parts, Map<String, Integer> counts, boolean extra) {}

    private record InnerTyped(Colour colour, long weight) {}

    private record SmallRetyped(String name, List<InnerTyped> parts, Map<String, Integer> counts) {}

    @Test
    void describe_record_listsComponentsByNameWithNestedRecordsAndEnumsInline() {
        assertThat(StoredShapes.describe(List.of(Small.class)))
                .isEqualTo("{counts:java.util.Map<java.lang.String,java.lang.Integer>,name:java.lang.String,"
                        + "parts:java.util.List<{colour:enum[BLUE,RED],weight:int}>}");
    }

    @Test
    void fingerprint_tinyRecord_isThePinnedValue() {
        assertThat(StoredShapes.fingerprint(List.of(Small.class)))
                .isEqualTo("756bd83380ce887aff7e96fe169b61342aaa71e3ef0929cacfad16da8942754b");
    }

    @Test
    void fingerprint_sameShapeUnderAnotherName_isEqual() {
        assertThat(StoredShapes.fingerprint(List.of(SmallRenamed.class)))
                .isEqualTo(StoredShapes.fingerprint(List.of(Small.class)));
    }

    @Test
    void fingerprint_componentAdded_differs() {
        assertThat(StoredShapes.fingerprint(List.of(SmallGrown.class)))
                .isNotEqualTo(StoredShapes.fingerprint(List.of(Small.class)));
    }

    @Test
    void fingerprint_nestedComponentRetyped_differs() {
        assertThat(StoredShapes.fingerprint(List.of(SmallRetyped.class)))
                .isNotEqualTo(StoredShapes.fingerprint(List.of(Small.class)));
    }

    @Test
    void current_twoCalls_answerTheSameFingerprint() {
        assertThat(StoredShapes.current()).isEqualTo(StoredShapes.current()).hasSize(64);
    }
}
