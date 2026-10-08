package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.ProgressFixtures;

/** Which two calls the live panel holds, and what it knows of their segments. */
class LiveCallStateTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:10.400Z");

    private final LiveCallState state = new LiveCallState();

    private static CallSnapshot waiting(final long id) {
        return CallSnapshot.waiting(
                id,
                CallKind.DRAFT,
                "draft-batch-json",
                null,
                List.of(new CallSegment("s-" + id, "ch1 · p" + id, "Source " + id)),
                List.of(),
                START,
                1,
                2,
                Duration.ofSeconds(60));
    }

    // IF an answer opened a second entry, THEN the panel would show a finished call twice and never the previous one.
    @Test
    void snapshot_sameCallIdAgain_replacesTheCallInPlace() {
        state.snapshot(waiting(1));
        state.snapshot(waiting(1).answered("{}", null, Duration.ofSeconds(3)));

        final LiveCalls view = state.view(START);

        assertThat(Objects.requireNonNull(view.current()).state().name()).isEqualTo("ANSWERED");
        assertThat(view.previous()).isNull();
    }

    // IF a new call did not push the old one back, THEN what the person just read would vanish.
    @Test
    void snapshot_newCall_movesTheCurrentOneToPrevious() {
        state.snapshot(waiting(1).answered("one", null, Duration.ofSeconds(3)));
        state.snapshot(waiting(2));

        final LiveCalls view = state.view(START);

        assertThat(Objects.requireNonNull(view.current()).callId()).isEqualTo(2);
        assertThat(Objects.requireNonNull(view.previous()).callId()).isEqualTo(1);
    }

    // IF a late outcome note for the previous call were dropped, THEN its chips would stay at the state they had.
    @Test
    void snapshot_updateOfThePreviousCall_updatesThePreviousOne() {
        final CallSnapshot first = waiting(1).answered("one", null, Duration.ofSeconds(3));
        state.snapshot(first);
        state.snapshot(waiting(2));

        state.snapshot(first.withOutcome(new SegmentOutcomeNote("s-1", SegmentOutcomeNote.Kind.ACCEPTED, "")));

        final LiveCalls view = state.view(START);
        assertThat(Objects.requireNonNull(view.previous()).outcomes()).hasSize(1);
        assertThat(Objects.requireNonNull(view.current()).callId()).isEqualTo(2);
    }

    // IF a stale snapshot of a third, older call displaced a shown one, THEN the order of the two blocks would break.
    @Test
    void snapshot_callOlderThanBoth_isIgnored() {
        state.snapshot(waiting(5));
        state.snapshot(waiting(6));

        state.snapshot(waiting(3));

        final LiveCalls view = state.view(START);
        assertThat(Objects.requireNonNull(view.current()).callId()).isEqualTo(6);
        assertThat(Objects.requireNonNull(view.previous()).callId()).isEqualTo(5);
    }

    // IF the view carried a clock for an answered call, THEN every tick would publish a change nobody sees.
    @Test
    void view_waitingCall_carriesTheClockInWholeSeconds_andAnsweredCallDoesNot() {
        state.snapshot(waiting(1));

        assertThat(state.view(Instant.parse("2026-01-01T00:00:12.900Z")).asOf())
                .isEqualTo(Instant.parse("2026-01-01T00:00:12Z"));

        state.snapshot(waiting(1).answered("{}", null, Duration.ofSeconds(3)));

        assertThat(state.view(Instant.parse("2026-01-01T00:00:15Z")).asOf()).isNull();
    }

    // IF a draft did not reach its segment, THEN the target column would stay on the wait after the model answered.
    @Test
    void drafted_thenDecided_keepsTargetScoreAndPath() {
        state.drafted(new SegmentDrafted("s-1", "Чернетка", 0.9));
        state.decided(new SegmentDecided(
                "s-1",
                SegmentStatus.ACCEPTED,
                null,
                ProgressFixtures.progress(1, 1, 1, 0, 0),
                new SegmentDetail(0.93, SegmentPath.DRAFT, List.of(), "Готово")));

        final SegmentLive live = state.view(START).segments().get("s-1");

        assertThat(live).isEqualTo(new SegmentLive("Готово", 0.93, SegmentPath.DRAFT));
    }

    // IF a memory reuse left the segment without a target, THEN the panel would say there is no translation.
    @Test
    void decided_withoutDetailTarget_keepsTheDraftTarget() {
        state.drafted(new SegmentDrafted("s-1", "Чернетка", 0.9));
        state.decided(new SegmentDecided(
                "s-1",
                SegmentStatus.FLAGGED,
                null,
                ProgressFixtures.progress(1, 1, 1, 0, 0),
                new SegmentDetail(null, SegmentPath.REPAIRED, List.of())));

        assertThat(Objects.requireNonNull(state.view(START).segments().get("s-1"))
                        .target())
                .isEqualTo("Чернетка");
    }
}
