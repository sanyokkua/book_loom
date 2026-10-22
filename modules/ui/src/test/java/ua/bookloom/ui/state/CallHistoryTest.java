package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;

/** The "All calls…" dialog's model: one step per call of the history, each holding its call. */
class CallHistoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:10:00Z");

    private static CallSnapshot call(final long id) {
        return ChunkFixtures.draft(id, CallKind.DRAFT, 1).answered("reply " + id, null, Duration.ofSeconds(3));
    }

    // IF the history were not one step per call, THEN the dialog could not list the calls or open one.
    @Test
    void modelOf_fiftyCalls_hasOneStepPerCallOldestFirstAndEachStepHoldsItsCall() {
        final List<CallSnapshot> calls =
                LongStream.rangeClosed(71, 120).mapToObj(CallHistoryTest::call).toList();

        final ProgressDialogModel model = CallHistory.modelOf(calls, NOW);

        assertThat(model.rows()).hasSize(50);
        assertThat(model.rows().get(0).label()).isEqualTo("Call 71 · DRAFT");
        assertThat(model.rows().get(49).label()).isEqualTo("Call 120 · DRAFT");
        assertThat(model.callOf(model.rows().get(49)))
                .isNotNull()
                .extracting(CallSnapshot::reply)
                .isEqualTo("reply 120");
    }

    // IF a run with no call opened a dialog with a phantom row, THEN the list would show a call that never happened.
    @Test
    void modelOf_noCalls_hasNoSteps() {
        assertThat(CallHistory.modelOf(List.of(), NOW).rows()).isEmpty();
    }
}
