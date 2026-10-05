package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;

/** The live panel's two rows, the flagged queue and the kept-as-source count, as the mirror shows them. */
class RunSessionLiveRowsTest extends LiveSessionTestBase {

    private static final String SOURCE = "She had lost her mother, and the poor girl wept as she followed the coffin.";
    private static final String DRAFT = "Вона втратила матір, і бідолашна дівчина плакала, ідучи за труною.";

    // IF a started segment did not appear in the second row, THEN a slow model would look stuck.
    @Test
    void segmentStarted_thenTick_showsItsSourceAwaitingTheModel() {
        final RunSession session = session();

        session.onEvent(started("s-42", "ch7 · p42", SOURCE));
        tick(session);

        final LiveRow current = rows().current();
        assertThat(current).isNotNull();
        assertThat(current.locator()).isEqualTo("ch7 · p42");
        assertThat(current.sourceText()).isEqualTo(SOURCE);
        assertThat(current.awaitingDraft()).isTrue();
        assertThat(current.targetText()).isNull();
        assertThat(rows().lastDecided()).isNull();
    }

    // IF a Balanced draft did not wait for the reviewer, THEN the row would show a draft as if it were final.
    @Test
    void segmentDrafted_balanced_isMarkedAwaitingReview() {
        final RunSession session = session(QualityDial.BALANCED);
        session.onEvent(started("s-42", "ch7 · p42", SOURCE));

        session.onEvent(new SegmentDrafted("s-42", DRAFT, 0.9));
        tick(session);

        final LiveRow current = rows().current();
        assertThat(current).isNotNull();
        assertThat(current.targetText()).isEqualTo(DRAFT);
        assertThat(current.awaitingDraft()).isFalse();
        assertThat(current.awaitingReview()).isTrue();
    }

    // IF Fast marked a draft as awaiting a reviewer that never runs, THEN the row would wait for nothing.
    @Test
    void segmentDrafted_fast_isNotMarkedAwaitingReview() {
        final RunSession session = session(QualityDial.FAST);
        session.onEvent(started("s-42", "ch7 · p42", SOURCE));

        session.onEvent(new SegmentDrafted("s-42", DRAFT, 0.9));
        tick(session);

        final LiveRow current = rows().current();
        assertThat(current).isNotNull();
        assertThat(current.awaitingReview()).isFalse();
    }

    // IF a decision left the segment in the second row, THEN the finished sentence would never reach the first.
    @Test
    void segmentDecided_currentRow_movesUpWithItsScoreAndPath() {
        final RunSession session = session();
        session.onEvent(started("s-42", "ch7 · p42", SOURCE));
        session.onEvent(new SegmentDrafted("s-42", DRAFT, 0.9));

        session.onEvent(decidedWith("s-42", SegmentStatus.ACCEPTED, detail(0.93, SegmentPath.DRAFT)));
        tick(session);

        final LiveRow last = rows().lastDecided();
        assertThat(last).isNotNull();
        assertThat(last.locator()).isEqualTo("ch7 · p42");
        assertThat(last.targetText()).isEqualTo(DRAFT);
        assertThat(last.judgeScore()).isEqualTo(0.93);
        assertThat(last.path()).isEqualTo(SegmentPath.DRAFT);
        assertThat(rows().current()).isNull();
    }

    // IF the next segment's start did not fill the second row again, THEN the panel would stay empty for the book.
    @Test
    void segmentStarted_afterADecision_fillsTheSecondRowAndKeepsTheFirst() {
        final RunSession session = session();
        session.onEvent(started("s-41", "ch7 · p41", SOURCE));
        session.onEvent(decidedWith("s-41", SegmentStatus.ACCEPTED, detail(null, SegmentPath.DRAFT)));

        session.onEvent(started("s-42", "ch7 · p42", "Next."));
        tick(session);

        assertThat(rows().lastDecided()).extracting(LiveRow::locator).isEqualTo("ch7 · p41");
        assertThat(rows().current()).extracting(LiveRow::locator).isEqualTo("ch7 · p42");
    }

    // IF a flagged decision did not refresh the queue, THEN the review panel would list a stale set.
    @Test
    void segmentDecided_flagged_readsTheQueueFromTheDesk() {
        desk.willAnswerQueue(List.of(flaggedView("s-11", "ch5 · p12")));
        final RunSession session = session();
        session.onEvent(started("s-11", "ch5 · p12", SOURCE));

        session.onEvent(decidedWith("s-11", SegmentStatus.FLAGGED, detail(0.2, SegmentPath.REPAIRED)));
        tick(session);

        assertThat(desk.calls()).containsExactly("queue(project-1, ALL_FLAGGED)");
        final List<FlaggedRow> queue = onFx(() -> List.copyOf(mirror.live().flaggedQueue()));
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).locator()).isEqualTo("ch5 · p12");
        assertThat(queue.get(0).findings()).extracting(QaFinding::kind).containsExactly("language");
        assertThat(queue.get(0).judgeScore()).isEqualTo(0.2);
    }

    // IF an accepted decision read the desk, THEN every segment of a book would cost a query.
    @Test
    void segmentDecided_accepted_doesNotReadTheDesk() {
        final RunSession session = session();

        session.onEvent(decidedWith("s-1", SegmentStatus.ACCEPTED, detail(null, SegmentPath.DRAFT)));

        assertThat(desk.calls()).isEmpty();
    }

    // IF the queue could be edited from outside, THEN a screen could change what the run believes is flagged.
    @Test
    void flaggedQueue_fromOutside_isUnmodifiable() {
        assertThatThrownBy(() -> onFx(
                        () -> mirror.live().flaggedQueue().add(new FlaggedRow("s-1", "ch1 · p1", List.of(), null))))
                .hasRootCauseInstanceOf(UnsupportedOperationException.class);
    }

    // IF a finished run did not publish the kept-as-source count, THEN the completed card would show no such count.
    @Test
    void finish_completedRun_publishesTheKeptAsSourceCount() {
        desk.willAnswerCounts(new ReviewCounts(1240, 1180, 45, 3, 0, 0, 12, 0, 0));
        final RunSession session = session();

        session.finish(Result.ok(completedReport(1240)), () -> {});
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.live().sourceKept().get())).isEqualTo(12);
        assertThat(desk.calls()).containsExactly("counts(project-1)");
    }

    // IF a finished run did not publish the audit's count, THEN the outcome card could not say how many look doubtful.
    @Test
    void finish_completedRun_publishesTheSuspiciousCount() {
        desk.willAnswerCounts(new ReviewCounts(1240, 1180, 45, 3, 0, 0, 12, 0, 0, 7));
        final RunSession session = session();

        session.finish(Result.ok(completedReport(1240)), () -> {});
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.live().suspicious().get())).isEqualTo(7);
    }

    // IF a new run kept the last run's rows, THEN its panel would open showing another book's sentences.
    @Test
    void publishRunStarted_afterARun_clearsEveryLiveField() {
        desk.willAnswerQueue(List.of(flaggedView("s-11", "ch5 · p12")));
        desk.willAnswerCounts(new ReviewCounts(10, 5, 0, 1, 0, 0, 4, 0, 0));
        final RunSession session = session();
        session.onEvent(started("s-11", "ch5 · p12", SOURCE));
        session.onEvent(decidedWith("s-11", SegmentStatus.FLAGGED, detail(0.2, SegmentPath.REPAIRED)));
        session.finish(Result.ok(completedReport(10)), () -> {});
        tick(session);

        mirror.publishRunStarted("Dracula.epub", null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(rows()).isEqualTo(LiveRows.EMPTY);
        assertThat(throughput()).isEqualTo(Throughput.EMPTY);
        assertThat(onFx(() -> mirror.live().sourceKept().get())).isZero();
        assertThat(onFx(() -> mirror.live().flaggedQueue().size())).isZero();
    }

    private static SegmentView flaggedView(final String id, final String locator) {
        return new SegmentView(
                id,
                locator,
                SegmentKind.PARAGRAPH,
                SegmentStatus.FLAGGED,
                "masked",
                SOURCE,
                null,
                null,
                null,
                List.of(new QaFinding("language", Severity.HIGH, "not Ukrainian", "script")),
                0.2,
                SegmentPath.REPAIRED,
                false,
                null,
                null);
    }
}
