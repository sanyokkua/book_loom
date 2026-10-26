package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RunStateTest {

    @ParameterizedTest
    @CsvSource({
        "IDLE, true",
        "RUNNING, false",
        "PAUSING, false",
        "PAUSED, false",
        "STOPPING, false",
        "STOPPED, true",
        "COMPLETED, true",
        "FAILED, true"
    })
    void isIdleOrEnded_eachState_trueOnlyWhenNoRunIsAtWork(final RunState state, final boolean expected) {
        assertThat(state.isIdleOrEnded()).isEqualTo(expected);
    }
}
