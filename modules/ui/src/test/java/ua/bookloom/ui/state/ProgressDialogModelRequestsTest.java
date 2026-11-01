package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.i18n.MessageKey;

/** The requests of a stage: each call belongs to the request that sent it, and the one on show is held a moment. */
class ProgressDialogModelRequestsTest {

    private static final Instant START = Instant.parse("2026-03-01T09:00:00Z");
    private static final String STAGE = "STAGE:0";
    private static final String FIRST = "REQUEST:0";
    private static final String SECOND = "REQUEST:1";

    private Instant clock = START;
    private StepLog log = StepLog.EMPTY
            .apply(new StepsPlanned(StepKind.STAGE, 1, false))
            .apply(step(StepKind.STAGE, 0, StepState.STARTED, null));
    private List<CallSnapshot> recent = List.of();
    private Map<Long, String> requestOf = Map.of();

    private static StepChanged step(
            final StepKind kind, final int index, final StepState state, final @Nullable StepResult result) {
        return new StepChanged(kind, "names", index, state, null, result);
    }

    private static CallSnapshot waiting(final long id) {
        return CallSnapshot.waiting(
                id, CallKind.NAME_KNOWLEDGE, "name-knowledge", null, List.of(), List.of(), START, 1, 1, null);
    }

    private static CallSnapshot answered(final long id) {
        return waiting(id).answered("{}", null, Duration.ofSeconds(4));
    }

    private Activity activity() {
        final CallSnapshot newest = recent.isEmpty() ? null : recent.getLast();
        final LiveCalls calls = new LiveCalls(newest, null, Map.of(), null, List.of(), recent, 0, requestOf);
        return new Activity(
                1,
                ActivityKind.NAMES_PASS,
                0,
                true,
                MessageKey.ACTIVITY_SUGGEST_STYLE,
                START,
                new Activity.Progress(null, null, List.of(), null, calls, log),
                false,
                false);
    }

    private ProgressDialogModel model() {
        return new ProgressDialogModel(activity(), new LedgerFigures(), ProgressDialogModel.Mode.SIMPLE, () -> clock);
    }

    private void send(final int index) {
        log = log.apply(step(StepKind.REQUEST, index, StepState.STARTED, null));
    }

    private void done(final int index) {
        log = log.apply(step(StepKind.REQUEST, index, StepState.ENDED, StepResult.DONE));
    }

    private static StepEntry request(final ProgressDialogModel model, final String key) {
        return model.requestRows().stream()
                .filter(row -> row.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    // IF two calls fell in one flush and were pinned to the running step, THEN the first request would show the second
    // one's call; each call is read against the request that sent it.
    @Test
    void callOf_twoCallsInOneFlush_eachRequestShowsItsOwn() {
        final ProgressDialogModel model = model();
        send(0);
        done(0);
        send(1);
        recent = List.of(answered(1), waiting(2));
        requestOf = Map.of(1L, FIRST, 2L, SECOND);

        model.accept(activity());

        assertThat(model.callOf(request(model, FIRST)))
                .extracting(CallSnapshot::callId)
                .isEqualTo(1L);
        assertThat(model.callOf(request(model, SECOND)))
                .extracting(CallSnapshot::callId)
                .isEqualTo(2L);
    }

    @Test
    void requestRows_selectedStage_listsEveryRequestItSentInOrder() {
        final ProgressDialogModel model = model();
        send(0);
        done(0);
        send(1);

        model.accept(activity());

        assertThat(model.selected().get()).extracting(StepEntry::key).isEqualTo(STAGE);
        assertThat(model.requestRows()).extracting(StepEntry::key).containsExactly(FIRST, SECOND);
    }

    // IF the next request replaced the last at once, THEN its answer would flash by unread.
    @Test
    void focusedRequest_afterAReply_staysForTheHoldThenMovesOn() {
        final ProgressDialogModel model = model();
        send(0);
        recent = List.of(answered(1));
        requestOf = Map.of(1L, FIRST);
        model.accept(activity());
        done(0);
        send(1);
        recent = List.of(answered(1), waiting(2));
        requestOf = Map.of(1L, FIRST, 2L, SECOND);
        clock = START.plusMillis(ProgressDialogModel.HOLD_MILLIS - 1);
        model.accept(activity());
        final String during = model.focusedRequest().get().key();

        clock = START.plusMillis(ProgressDialogModel.HOLD_MILLIS);
        model.advance();

        assertThat(during).isEqualTo(FIRST);
        assertThat(model.focusedRequest().get().key()).isEqualTo(SECOND);
    }

    // IF a newer request pulled the view away from the one the person chose, THEN they could not read it.
    @Test
    void chooseRequest_thenANewerRequest_keepsTheChosenOneAndFollowStaysOff() {
        final ProgressDialogModel model = model();
        send(0);
        done(0);
        send(1);
        model.accept(activity());
        model.chooseRequest(request(model, FIRST));

        done(1);
        send(2);
        clock = START.plusSeconds(30);
        model.accept(activity());

        assertThat(model.focusedRequest().get().key()).isEqualTo(FIRST);
        assertThat(model.follow().get()).isFalse();
    }

    @Test
    void follow_turnedBackOn_returnsToTheNewestRequest() {
        final ProgressDialogModel model = model();
        send(0);
        done(0);
        send(1);
        model.accept(activity());
        model.chooseRequest(request(model, FIRST));

        model.follow().set(true);

        assertThat(model.focusedRequest().get().key()).isEqualTo(SECOND);
    }
}
