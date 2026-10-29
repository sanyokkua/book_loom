package ua.bookloom.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The {@code reason=} field of a WARN line is read from typed details only, and says {@code none} when they hold none. */
class FailureReasonTest {

    @Test
    void tokenIn_detailsWithAReason_isTheReasonsToken() {
        final String details = SafeDetails.empty()
                .withReason(FailureReason.GRAMMAR_TOO_LARGE)
                .withHttpStatus(400)
                .render();

        assertThat(FailureReason.tokenIn(details)).isEqualTo("grammar-too-large");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"httpStatus=500", "reason=unheard-of", "connection reset by peer"})
    void tokenIn_detailsWithoutAKnownReason_isNone(final String details) {
        assertThat(FailureReason.tokenIn(details)).isEqualTo("none");
    }
}
