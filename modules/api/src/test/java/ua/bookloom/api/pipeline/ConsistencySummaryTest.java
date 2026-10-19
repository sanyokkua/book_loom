package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.DeferralReason;

/** The summary splits the segments that wait on a gender into suggested ones to confirm and unknown ones to set. */
class ConsistencySummaryTest {

    private static final Map<DeferralReason, Integer> SEVEN_WAIT = Map.of(DeferralReason.GENDER_UNKNOWN, 7);

    @Test
    void constructor_awaitingList_derivesTheSuggestedAndUnknownSegmentCounts() {
        final ConsistencySummary summary = new ConsistencySummary(
                ConsistencySummary.Status.RAN,
                0,
                0,
                SEVEN_WAIT,
                0,
                ConsistencyChecks.NONE,
                List.of(new GenderWait("Ember", 5, true), new GenderWait("Dane", 2)));

        assertThat(summary.suggestedGenderSegments()).isEqualTo(5);
        assertThat(summary.unknownGenderSegments()).isEqualTo(2);
    }

    @Test
    void constructor_moreSuggestedThanOpenDeferrals_leavesNoNegativeUnknownCount() {
        final ConsistencySummary summary = new ConsistencySummary(
                ConsistencySummary.Status.RAN,
                0,
                0,
                Map.of(),
                0,
                ConsistencyChecks.NONE,
                List.of(new GenderWait("Ember", 5, true)));

        assertThat(summary.unknownGenderSegments()).isZero();
    }

    @Test
    void constructor_negativeSplitCount_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConsistencySummary(
                        ConsistencySummary.Status.RAN, 0, 0, SEVEN_WAIT, 0, ConsistencyChecks.NONE, List.of(), -1, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConsistencySummary(
                        ConsistencySummary.Status.RAN, 0, 0, SEVEN_WAIT, 0, ConsistencyChecks.NONE, List.of(), 0, -1));
    }
}
