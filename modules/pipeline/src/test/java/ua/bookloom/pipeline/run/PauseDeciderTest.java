package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
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
        "internal,FAIL",
        "busy,FAIL",
        "discoveryFailed,FAIL"
    })
    void route_everyCode_matchesTheTable(final ErrorCode code, final Route expected) {
        assertThat(PauseDecider.route(code)).isEqualTo(expected);
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
