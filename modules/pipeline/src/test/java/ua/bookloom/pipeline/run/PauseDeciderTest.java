package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.pipeline.run.PauseDecider.Route;

class PauseDeciderTest {

    // One row per ErrorCode constant: adding a sixteenth code must add a row here as well as a switch arm.
    @ParameterizedTest
    @CsvSource({
        "emptyCompletion,FLAG_AT_ONCE",
        "contextWindow,FLAG_AT_ONCE",
        "cancelled,CANCELLED",
        "unreachable,PAUSE_OR_FAIL",
        "timeout,PAUSE_OR_FAIL",
        "auth,PAUSE_OR_FAIL",
        "rateLimited,PAUSE_OR_FAIL",
        "upstream,PAUSE_OR_FAIL",
        "modelNotFound,PAUSE_OR_FAIL",
        "modelUnavailable,PAUSE_OR_FAIL",
        "missingCredential,PAUSE_OR_FAIL",
        "validation,PAUSE_OR_FAIL",
        "internal,FAIL",
        "busy,FAIL",
        "discoveryFailed,FAIL"
    })
    void route_everyCode_matchesTheTable(final ErrorCode code, final Route expected) {
        assertThat(PauseDecider.route(code)).isEqualTo(expected);
    }
}
