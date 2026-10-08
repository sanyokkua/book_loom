package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConsistencyChecksTest {

    @Test
    void refusedTotal_twoRules_sumsTheirCounts() {
        final ConsistencyChecks checks = new ConsistencyChecks(1, 2, 3, Map.of("quotes", 2, "worse", 1), 4);

        assertThat(checks.refusedTotal()).isEqualTo(3);
    }

    @Test
    void none_countsNothing() {
        assertThat(ConsistencyChecks.NONE.refusedTotal()).isZero();
        assertThat(ConsistencyChecks.NONE.refused()).isEmpty();
    }

    @Test
    void constructor_refusalsChangedAfterwards_keepsTheCopy() {
        final Map<String, Integer> refused = new HashMap<>(Map.of("quotes", 1));
        final ConsistencyChecks checks = new ConsistencyChecks(0, 0, 0, refused, 0);

        refused.put("worse", 5);

        assertThat(checks.refused()).containsOnlyKeys("quotes");
    }

    // IF a negative count were accepted, THEN a report could say "-1 kept".
    @ParameterizedTest
    @CsvSource({"-1,0,0,0", "0,-1,0,0", "0,0,-1,0", "0,0,0,-1"})
    void constructor_negativeCount_isRejected(
            final int improved, final int kept, final int unchanged, final int skipped) {
        assertThatThrownBy(() -> new ConsistencyChecks(improved, kept, unchanged, Map.of(), skipped))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_zeroRefusalCount_isRejected() {
        assertThatThrownBy(() -> new ConsistencyChecks(0, 0, 0, Map.of("quotes", 0), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
