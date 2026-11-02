package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The limits the window shows are the numbers the HTTP client enforces. */
class CallLimitsTest {

    @Test
    void timeouts_fixed_areNinetySecondsTwoMinutesTenMinutes() {
        assertThat(CallLimits.REVIEW_TIMEOUT.toSeconds()).isEqualTo(90);
        assertThat(CallLimits.HELPER_TIMEOUT.toMinutes()).isEqualTo(2);
        assertThat(CallLimits.MAX_CHAT_TIMEOUT.toMinutes()).isEqualTo(10);
    }

    @Test
    void attempts_fixed_areThreeTotalAndTwoTimeouts() {
        assertThat(CallLimits.MAX_ATTEMPTS).isEqualTo(3);
        assertThat(CallLimits.MAX_TIMEOUT_ATTEMPTS).isEqualTo(2);
    }
}
