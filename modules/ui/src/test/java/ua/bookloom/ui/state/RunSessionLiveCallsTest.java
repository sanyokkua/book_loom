package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.PromptSection;

/** The two calls of the live panel as the run session hands them to the mirror. */
class RunSessionLiveCallsTest extends LiveSessionTestBase {

    private CallSnapshot waiting(final long id) {
        return CallSnapshot.waiting(
                id,
                CallKind.DRAFT,
                "draft-batch-json",
                POSITION,
                List.of(new CallSegment("s-" + id, "ch1 · p" + id, "Source " + id)),
                List.of(new PromptSection("summary", "[Book so far]", PromptSection.Origin.USER, List.of("A story."))),
                clock.instant(),
                1,
                2,
                Duration.ofSeconds(60));
    }

    private LiveCalls calls() {
        return onFx(() -> mirror.live().calls().get());
    }

    private AtomicInteger countChanges() {
        final AtomicInteger changes = new AtomicInteger();
        onFx(() -> {
            mirror.live().calls().addListener((observed, was, now) -> changes.incrementAndGet());
            return null;
        });
        return changes;
    }

    // IF the snapshot were only traced, THEN the panel would never show a call.
    @Test
    void callSnapshotUpdated_thenTick_showsTheCallInTheMirror() {
        final RunSession session = session();

        session.onEvent(new CallSnapshotUpdated(waiting(1)));
        tick(session);

        assertThat(Objects.requireNonNull(calls().current()).callId()).isEqualTo(1);
        assertThat(calls().previous()).isNull();
    }

    // IF a second call did not reach the panel as current with the first behind it, THEN the person loses the reply.
    @Test
    void callSnapshotUpdated_secondCall_showsFirstAsPrevious() {
        final RunSession session = session();
        session.onEvent(new CallSnapshotUpdated(waiting(1).answered("one", null, Duration.ofSeconds(2))));
        session.onEvent(new CallSnapshotUpdated(waiting(2)));

        tick(session);

        assertThat(Objects.requireNonNull(calls().current()).callId()).isEqualTo(2);
        assertThat(Objects.requireNonNull(calls().previous()).reply()).isEqualTo("one");
    }

    // IF each event published by itself, THEN a thousand events would queue a thousand FX runnables.
    @Test
    void callSnapshotUpdated_manyEventsBeforeOneTick_publishOnce() {
        final RunSession session = session();
        final AtomicInteger changes = countChanges();

        IntStream.rangeClosed(1, 200).forEach(n -> session.onEvent(new CallSnapshotUpdated(waiting(n))));
        tick(session);
        tick(session);

        assertThat(changes.get()).isEqualTo(1);
        assertThat(Objects.requireNonNull(calls().current()).callId()).isEqualTo(200);
    }

    // IF a waiting call's clock did not advance with the session clock, THEN the elapsed timer would freeze.
    @Test
    void tick_waitingCallAfterOneSecond_publishesTheNewClock_butNotWithinTheSecond() {
        final RunSession session = session();
        session.onEvent(new CallSnapshotUpdated(waiting(1)));
        tick(session);
        final AtomicInteger changes = countChanges();

        clock.advance(Duration.ofMillis(300));
        tick(session);
        clock.advance(Duration.ofSeconds(1));
        tick(session);

        assertThat(changes.get()).isEqualTo(1);
    }
}
