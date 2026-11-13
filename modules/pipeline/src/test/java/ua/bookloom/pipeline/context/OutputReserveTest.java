package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ReasoningControl;

/** The output a translate-family call reserves for its reply (design D8, "Output reserve"). */
class OutputReserveTest {

    private static final int WINDOW = 16_384;

    @Test
    void reserve_batchOfFour_ratioTimesSourcePlusItems() {
        assertThat(OutputReserve.of(74, 4, 1.5, ReasoningControl.Kind.NONE, WINDOW))
                .isEqualTo(175);
    }

    @Test
    void reserve_pairWithoutRatio_default15() {
        assertThat(OutputReserve.of(29, 1, OutputReserve.DEFAULT_OUTPUT_RATIO, ReasoningControl.Kind.BOOLEAN, WINDOW))
                .isEqualTo(60);
    }

    @ParameterizedTest
    @EnumSource(
            value = ReasoningControl.Kind.class,
            names = {"ALWAYS", "UNSWITCHABLE"})
    void reserve_alwaysReasoning_atLeast2048(final ReasoningControl.Kind kind) {
        assertThat(OutputReserve.of(74, 4, 1.5, kind, WINDOW)).isEqualTo(2048);
    }

    // A long batch on a model that always reasons takes 4·S + 35·n once that is above the floor.
    @Test
    void reserve_alwaysReasoningLongBatch_fourTimesSourcePlus35PerItem() {
        assertThat(OutputReserve.of(1000, 4, 1.5, ReasoningControl.Kind.ALWAYS, WINDOW))
                .isEqualTo(4140);
    }

    // A 4096 window never gives a reply more than half of it.
    @Test
    void reserve_smallWindow_cappedAtHalf() {
        assertThat(OutputReserve.of(74, 4, 1.5, ReasoningControl.Kind.ALWAYS, 4096))
                .isEqualTo(2048);
        assertThat(OutputReserve.of(74, 4, 1.5, ReasoningControl.Kind.ALWAYS, 3000))
                .isEqualTo(1500);
    }
}
