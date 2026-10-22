package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.SampleAnswer;

/** What each brief sample answered reaches the receiver the work gave the feed, which builds "What it decides". */
class CallFeedBriefReadTest {

    private static final Instant AT = Instant.parse("2026-01-01T00:00:10Z");

    private final ActivityTracker tracker = new ActivityTracker(new StateMirror());
    private final List<BriefSampleRead> seen = new ArrayList<>();

    private static BriefSampleRead read(final int sample) {
        return new BriefSampleRead(
                sample,
                4,
                Map.of(BriefField.GENRE, new SampleAnswer("crime", "He left.")),
                "ch1 · p02–p14",
                "Passage 1: He left.");
    }

    private CallFeed feed() {
        return new CallFeed(tracker.begin(ActivityKind.SUGGEST_STYLE, null), () -> AT, seen::add);
    }

    // IF the feed dropped the sample answers, THEN the dialog could not show a decision's evidence while it runs.
    @Test
    void accept_sampleRead_reachesTheReceiverInOrder() {
        final CallFeed feed = feed();

        feed.accept(read(1));
        feed.accept(read(2));

        assertThat(seen).extracting(BriefSampleRead::sample).containsExactly(1, 2);
        assertThat(seen.getFirst().location()).isEqualTo("ch1 · p02–p14");
    }

    @Test
    void accept_eventThatIsNoSampleRead_isNotHandedToTheReceiver() {
        feed().accept(new BatchStarted(CallKind.SUGGEST_TARGETS, 1, 2));

        assertThat(seen).isEmpty();
    }
}
