package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The limits the window shows are the numbers the HTTP client enforces. */
class CallLimitsTest {

    @Test
    void timeouts_fixed_areNinetySecondsTwoMinutesTenMinutes() {
        assertThat(CallLimits.REVIEW_TIMEOUT).isEqualTo(Duration.ofSeconds(90));
        assertThat(CallLimits.HELPER_TIMEOUT).isEqualTo(Duration.ofMinutes(2));
        assertThat(CallLimits.MAX_CHAT_TIMEOUT).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void attempts_fixed_areThreeTotalAndTwoTimeouts() {
        assertThat(CallLimits.MAX_ATTEMPTS).isEqualTo(3);
        assertThat(CallLimits.MAX_TIMEOUT_ATTEMPTS).isEqualTo(2);
    }
}
