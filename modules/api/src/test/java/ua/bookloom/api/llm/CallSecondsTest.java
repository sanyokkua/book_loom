package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.pipeline.CallKind;

/** The seconds per call of each kind a run measured, as the model's settings keep them. */
class CallSecondsTest {

    private static CallRecord answered(final CallKind kind, final long millis) {
        return new CallRecord(kind, null, Duration.ofMillis(millis), null, null);
    }

    private static CallRecord failed(final CallKind kind, final long millis) {
        return new CallRecord(kind, null, Duration.ofMillis(millis), null, FailureReason.UNCLASSIFIED);
    }

    @Test
    void of_twentyReviewCallsInTwoMinutes_givesSixSecondsAPerCall() {
        final List<CallRecord> records = new ArrayList<>(Collections.nCopies(20, answered(CallKind.REVIEW, 6_000)));

        assertThat(CallSeconds.of(records)).isEqualTo(Map.of(CallKind.REVIEW, 6.0));
    }

    @Test
    void of_failedCalls_areLeftOut() {
        final List<CallRecord> records = List.of(
                answered(CallKind.TERM_READING, 4_000),
                answered(CallKind.TERM_READING, 6_000),
                failed(CallKind.TERM_READING, 120_000),
                failed(CallKind.SUGGEST_TARGETS, 30_000));

        assertThat(CallSeconds.of(records)).isEqualTo(Map.of(CallKind.TERM_READING, 5.0));
    }

    @Test
    void of_draftCalls_areLeftOutAsTheirFigureIsPerSegment() {
        assertThat(CallSeconds.of(List.of(answered(CallKind.DRAFT, 9_000)))).isEmpty();
    }

    @Test
    void of_meanTime_isRoundedToATenthAndNeverZero() {
        assertThat(CallSeconds.of(List.of(answered(CallKind.SUMMARY, 2_345), answered(CallKind.FILE_NAME, 10))))
                .isEqualTo(Map.of(CallKind.SUMMARY, 2.3, CallKind.FILE_NAME, 0.1));
    }
}
