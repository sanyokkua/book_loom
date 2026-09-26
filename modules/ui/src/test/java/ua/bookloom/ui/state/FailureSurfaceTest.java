package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;

/** The assignment of each of the fifteen typed error codes to the one surface the notifications spec gives it. */
class FailureSurfaceTest {

    // Written out code by code from the notifications spec, never derived from the mapping under test.
    private static final Map<ErrorCode, FailureSurface> EXPECTED = Map.ofEntries(
            Map.entry(ErrorCode.unreachable, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.timeout, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.auth, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.rateLimited, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.upstream, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.emptyCompletion, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.modelNotFound, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.modelUnavailable, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.missingCredential, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.contextWindow, FailureSurface.PROVIDER_ERROR),
            Map.entry(ErrorCode.cancelled, FailureSurface.STOPPED),
            Map.entry(ErrorCode.validation, FailureSurface.IN_PLACE),
            Map.entry(ErrorCode.internal, FailureSurface.DIALOG),
            Map.entry(ErrorCode.busy, FailureSurface.DIALOG),
            Map.entry(ErrorCode.discoveryFailed, FailureSurface.SETTINGS_ONLY));

    // IF a code were routed to another surface than the specification assigns it, THEN a destination that already
    // exists could be drawn as a model server that is not running.
    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void of_eachErrorCode_isTheSurfaceTheSpecificationAssigns(final ErrorCode code) {
        assertThat(FailureSurface.of(code)).isEqualTo(EXPECTED.get(code));
    }

    // IF a sixteenth code were added without a row in the expectations above, THEN "all fifteen" would stop being true.
    @Test
    void expectations_coverEveryErrorCode() {
        assertThat(EXPECTED.keySet()).containsExactlyInAnyOrderElementsOf(Arrays.asList(ErrorCode.values()));
        assertThat(EXPECTED).hasSize(15);
    }
}
