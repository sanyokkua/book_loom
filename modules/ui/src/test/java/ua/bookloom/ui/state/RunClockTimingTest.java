package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.project.SegmentPath;

/** Which decisions feed the time-left average: a segment kept as it is took no call and is left out. */
class RunClockTimingTest {

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "ACCEPTED, DRAFT, true",
        "ACCEPTED, REPAIRED, true",
        "FLAGGED, DRAFT, true",
        "ACCEPTED, TM_REUSE, true",
        "ACCEPTED, VERBATIM, false",
        "PENDING, DRAFT, false"
    })
    void isTimed_decision_countsOnlyWhenAModelWasAsked(
            final SegmentStatus status, final SegmentPath path, final boolean expected) {
        final SegmentDecided decided = new SegmentDecided(
                "ch1:0",
                status,
                null,
                new JobProgress(JobStage.TRANSLATE, 1, 1, 1, 0, 0),
                new SegmentDetail(null, path, List.of()));

        assertThat(RunClock.isTimed(decided)).isEqualTo(expected);
    }
}
