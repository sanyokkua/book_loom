package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;

/** The model calls of a glossary scan or a setup proposal reach the busy card of that work. */
class CallFeedTest {

    private static final Instant AT = Instant.parse("2026-01-01T00:00:10Z");

    private final ActivityTracker tracker = new ActivityTracker(new StateMirror());

    private static CallSnapshot call(final long id, final CallKind kind) {
        return CallSnapshot.waiting(id, kind, "prescan", null, List.of(), List.of(), AT, 1, 1, Duration.ofMinutes(1));
    }

    // IF a segmentless call never reached the card, THEN the person could not see what a scan sent the model.
    @Test
    void accept_callSnapshot_becomesTheCurrentCallOfTheActivity() {
        final ActivityTracker.Handle handle = tracker.begin(ActivityKind.GLOSSARY_SCAN, null);
        final CallFeed feed = new CallFeed(handle, () -> AT);

        feed.accept(new CallSnapshotUpdated(call(1, CallKind.PRESCAN)));

        assertThat(tracker.running().getFirst().calls().current())
                .extracting(CallSnapshot::kind)
                .isEqualTo(CallKind.PRESCAN);
    }

    @Test
    void accept_secondCall_pushesTheFirstBackAsThePreviousOne() {
        final ActivityTracker.Handle handle = tracker.begin(ActivityKind.GLOSSARY_REVIEW, null);
        final CallFeed feed = new CallFeed(handle, () -> AT);

        feed.accept(new CallSnapshotUpdated(call(1, CallKind.REVIEW_TERMS)));
        feed.accept(new CallSnapshotUpdated(call(2, CallKind.SUGGEST_TARGETS)));

        final LiveCalls shown = tracker.running().getFirst().calls();
        assertThat(shown.current()).extracting(CallSnapshot::callId).isEqualTo(2L);
        assertThat(shown.previous()).extracting(CallSnapshot::callId).isEqualTo(1L);
    }

    @Test
    void accept_eventThatIsNoCall_changesNothing() {
        final ActivityTracker.Handle handle = tracker.begin(ActivityKind.GLOSSARY_SCAN, null);

        new CallFeed(handle, () -> AT).accept(new BatchStarted(CallKind.SUGGEST_TARGETS, 1, 2));

        assertThat(tracker.running().getFirst().calls()).isEqualTo(LiveCalls.EMPTY);
    }
}
