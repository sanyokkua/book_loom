package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.pipeline.run.PauseDecider.Recovery;
import ua.bookloom.pipeline.run.PauseDecider.Route;

class PauseDeciderTest {

    private static final String ALL = "AFTER_SEGMENT AFTER_SECTION BETWEEN_STAGES ON_ERROR ON_FLAGGED";

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
        "internal,PAUSE_OR_FAIL",
        "busy,FAIL",
        "discoveryFailed,FAIL"
    })
    void route_everyCode_matchesTheTable(final ErrorCode code, final Route expected) {
        assertThat(PauseDecider.route(code, null)).isEqualTo(expected);
    }

    // An outage must never spend a segment's budget, and a code only the person can fix must never wake by itself. An
    // unloaded model often loads again on the next request, so it wakes, but only six times: a model nobody loads
    // would otherwise be probed all night while the banner claims the run is on its way.
    @ParameterizedTest
    @CsvSource({
        "unreachable,OUTAGE,true,10,2147483647",
        "upstream,OUTAGE,true,10,2147483647",
        "rateLimited,OUTAGE,true,10,2147483647",
        "timeout,STALL,true,2,2147483647",
        "internal,FAULT,true,3,2147483647",
        "modelUnavailable,UNLOADED_MODEL,true,10,6",
        "auth,PERSON,false,2,0",
        "modelNotFound,PERSON,false,2,0",
        "missingCredential,PERSON,false,2,0",
        "validation,PERSON,false,2,0",
        "emptyCompletion,PERSON,false,2,0",
        "contextWindow,PERSON,false,2,0",
        "cancelled,PERSON,false,2,0",
        "busy,PERSON,false,2,0",
        "discoveryFailed,PERSON,false,2,0"
    })
    void recovery_everyCode_matchesTheTable(
            final ErrorCode code,
            final Recovery expected,
            final boolean automatic,
            final int budget,
            final int wakesPerOutage) {
        final Recovery recovery = PauseDecider.recovery(code, null);

        assertThat(recovery).isEqualTo(expected);
        assertThat(recovery.isAutomatic()).isEqualTo(automatic);
        assertThat(recovery.pausesBeforeFlagging()).isEqualTo(budget);
        assertThat(recovery.wakesPerOutage()).isEqualTo(wakesPerOutage);
    }

    // Waiting does not free memory, so only the person can act on it; a model that is merely not loaded (or still
    // loading) keeps the unloaded-model wait with its six wakes.
    @ParameterizedTest
    @CsvSource({
        "OUT_OF_MEMORY,PERSON,false,0",
        "NO_MODEL_LOADED,UNLOADED_MODEL,true,6",
        "MODEL_UNLOADED,UNLOADED_MODEL,true,6",
        "MODEL_LOADING,UNLOADED_MODEL,true,6"
    })
    void recovery_modelUnavailableWithOutOfMemory_isPersonAndNoModelLoadedIsUnloadedModel(
            final FailureReason reason, final Recovery expected, final boolean automatic, final int wakesPerOutage) {
        final Recovery recovery = PauseDecider.recovery(ErrorCode.modelUnavailable, reason);

        assertThat(recovery).isEqualTo(expected);
        assertThat(recovery.isAutomatic()).isEqualTo(automatic);
        assertThat(recovery.wakesPerOutage()).isEqualTo(wakesPerOutage);
    }

    // A model that looped twice on one passage would loop on it again after a pause: the passage is flagged. Every
    // other upstream failure is an outage the run waits through.
    @ParameterizedTest
    @CsvSource(
            nullValues = "-",
            value = {"REPEAT_LIMIT,FLAG_AT_ONCE", "TERMINATED,PAUSE_OR_FAIL", "-,PAUSE_OR_FAIL"})
    void route_upstreamRepeatLimit_flagsAtOnce(@Nullable final FailureReason reason, final Route expected) {
        assertThat(PauseDecider.route(ErrorCode.upstream, reason)).isEqualTo(expected);
    }

    // The forms that take the error read its reason from the details, so a caller never parses them itself.
    @ParameterizedTest
    @CsvSource({"upstream,REPEAT_LIMIT,FLAG_AT_ONCE,PERSON", "modelUnavailable,OUT_OF_MEMORY,PAUSE_OR_FAIL,PERSON"})
    void routeAndRecovery_error_readTheReasonFromItsDetails(
            final ErrorCode code, final FailureReason reason, final Route route, final Recovery recovery) {
        final AppError error = AppError.of(
                code,
                "Failed",
                "It failed.",
                SafeDetails.empty().withReason(reason).render(),
                null);

        assertThat(PauseDecider.route(error)).isEqualTo(route);
        assertThat(PauseDecider.recovery(error)).isEqualTo(recovery);
    }

    // The last segment of a book ends a segment, a section and the stage at once and must pause once; a flagged
    // segment is named as flagged, since that is what the person has to act on.
    @ParameterizedTest
    @CsvSource(
            nullValues = "NONE",
            value = {
                "true,  true,  true,  true,  " + ALL + ", REQUESTED",
                "true,  false, false, false, '', REQUESTED",
                "false, true,  true,  true,  " + ALL + ", ON_FLAGGED",
                "false, true,  true,  true,  AFTER_SEGMENT AFTER_SECTION BETWEEN_STAGES, BETWEEN_STAGES",
                "false, false, true,  true,  " + ALL + ", BETWEEN_STAGES",
                "false, false, false, true,  BETWEEN_STAGES, BETWEEN_STAGES",
                "false, false, true,  true,  AFTER_SEGMENT AFTER_SECTION, AFTER_SECTION",
                "false, false, true,  false, " + ALL + ", AFTER_SECTION",
                "false, false, false, false, " + ALL + ", AFTER_SEGMENT",
                "false, true,  false, false, AFTER_SEGMENT, AFTER_SEGMENT",
                "false, false, false, false, ON_FLAGGED ON_ERROR, NONE",
                "false, true,  true,  true,  '', NONE"
            })
    void boundary_precedence_isRequestedThenFlaggedThenStageThenSectionThenSegment(
            final boolean requested,
            final boolean flagged,
            final boolean endsSection,
            final boolean endsStage,
            final String points,
            @Nullable final PauseReason expected) {
        assertThat(PauseDecider.boundary(pointsOf(points), requested, flagged, endsSection, endsStage))
                .isEqualTo(Optional.ofNullable(expected));
    }

    private static Set<PausePoint> pointsOf(final String names) {
        return Arrays.stream(names.split(" "))
                .filter(name -> !name.isBlank())
                .map(PausePoint::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
