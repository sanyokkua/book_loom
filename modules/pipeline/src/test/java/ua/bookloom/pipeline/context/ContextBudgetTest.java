package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The allowance the memory lines and the character sheet are cut to; the numbers are worked by hand. */
class ContextBudgetTest {

    // free = window - 900 - 500; the allowance is 40% of it, never above 2000.
    @ParameterizedTest
    @CsvSource({"32768,2000", "8192,2000", "4096,1078", "2048,259", "1000,0"})
    void dynamicAllowance_window_isAShareOfWhatTheStaticPrefixLeaves(final int window, final int expected) {
        assertThat(ContextBudget.dynamicAllowance(window, 900)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2000,200", "1078,108", "0,0"})
    void characterAllowance_allowance_isATenthRoundedUp(final int allowance, final int expected) {
        assertThat(ContextBudget.characterAllowance(allowance)).isEqualTo(expected);
    }
}
