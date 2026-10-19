package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.RoundStarted;

/**
 * What a run session holds does not grow with the book: the lines waiting for a tick, the locators and repair rounds it
 * remembers, and the flagged queue, which grows by appending rather than by rebuilding every row it already shows.
 */
class RunSessionBoundsTest extends LiveSessionTestBase {

    // IF ticks stalled while the engine kept talking, THEN the queue would hold a night of lines nobody can see.
    @Test
    void add_moreLinesThanTheLogShowsBeforeATick_keepsOnlyTheNewest() {
        final PendingLines pending = new PendingLines();

        IntStream.range(0, 2_000).forEach(index -> pending.add(line(index)));

        assertThat(pending.size()).isEqualTo(StateMirror.MAX_LOG_ENTRIES);
        assertThat(pending.drain()).hasSize(StateMirror.MAX_LOG_ENTRIES).last().isEqualTo(line(1_999));
        assertThat(pending.size()).isZero();
    }

    @Test
    void tick_twoThousandCallLines_showsTheNewestFiveHundred() {
        final RunSession session = session();

        IntStream.range(0, 2_000).forEach(index -> session.onEvent(call("s-" + index, CallKind.DRAFT)));
        tick(session);

        assertThat(logEntries()).hasSize(StateMirror.MAX_LOG_ENTRIES);
    }

    // A segment that starts a repair round and is never decided (a stop, a skip) would otherwise stay remembered.
    @Test
    void segmentStarted_thousandsOfSegmentsWithRounds_holdsABoundedNumber() {
        final ActivityLogFeed feed = new ActivityLogFeed();

        IntStream.range(0, 3_700).forEach(index -> startWithRound(feed, "s-" + index));

        assertThat(feed.heldSegments()).isLessThanOrEqualTo(ActivityLogFeed.MAX_LOCATORS + 1);
    }

    // IF each flagged decision rebuilt the whole list, THEN the review list would lay out every row again each time.
    @Test
    void publishFlaggedQueue_growingQueue_appendsOnlyTheNewRows() {
        final List<String> changes = new ArrayList<>();
        onFx(() -> {
            mirror.live().flaggedQueue().addListener((ListChangeListener<FlaggedRow>)
                    change -> changes.add(kindOf(change)));
            return null;
        });

        mirror.live().publishFlaggedQueue(rows(3));
        mirror.live().publishFlaggedQueue(rows(5));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(changes).containsExactly("added 3", "added 2");
        assertThat(onFx(() -> mirror.live().flaggedQueue().size())).isEqualTo(5);
    }

    @Test
    void publishFlaggedQueue_rowLeavesTheQueue_replacesIt() {
        mirror.live().publishFlaggedQueue(rows(3));
        mirror.live().publishFlaggedQueue(rows(3).subList(1, 3));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> List.copyOf(mirror.live().flaggedQueue()))).isEqualTo(rows(3).subList(1, 3));
    }

    private static void startWithRound(final ActivityLogFeed feed, final String segmentId) {
        feed.segmentStarted(started(segmentId, "ch1 · " + segmentId, "Text."));
        feed.roundStarted(new RoundStarted(segmentId, 1, 3, 0.5, "meaning"));
    }

    private static LogEntry line(final int index) {
        return new LogEntry(LogKind.MODEL_CALL, List.of("draft", " · s-" + index, "0", "1", "0:01"));
    }

    private static List<FlaggedRow> rows(final int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new FlaggedRow("s-" + index, "ch1 · p" + index, List.of(), null))
                .toList();
    }

    private static String kindOf(final ListChangeListener.Change<? extends FlaggedRow> change) {
        change.next();
        return (change.wasReplaced() ? "replaced " : "added ") + change.getAddedSize();
    }
}
