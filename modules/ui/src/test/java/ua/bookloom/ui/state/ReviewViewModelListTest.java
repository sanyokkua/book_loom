package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.SegmentPath;

/** The flagged list, its badges, the four chips and the All segments browse. */
class ReviewViewModelListTest extends ReviewViewModelTestBase {

    // IF the badge were not on the row, THEN the person could not tell why a segment was flagged without opening it.
    @Test
    void open_threeFlagged_listsEachWithItsMainFindingBadge() {
        buildReview();

        open();

        assertThat(onFx(() -> review.rows().stream()
                        .map(row -> row.locator() + "|" + row.badge())
                        .toList()))
                .containsExactly("ch5 · p12|LOW_SCORE", "ch7 · p40|NAME", "ch9 · p03|WRONG_LANGUAGE");
        assertThat(desk.calls()).contains("queue(" + projectId + ", ALL_FLAGGED)");
    }

    // IF the chip filtered here instead of asking the desk, THEN the panel and the desk could disagree on "names".
    @Test
    void selectFilter_names_asksTheDeskAndShowsItsAnswer() {
        buildReview();
        open();
        desk.willAnswerQueue(List.of(nameIssue()));

        press(() -> review.selectFilter(ReviewFilter.NAMES));

        assertThat(desk.calls()).contains("queue(" + projectId + ", NAMES)");
        assertThat(locators()).containsExactly("ch7 · p40");
    }

    @Test
    void selectFilter_foreignKept_asksForForeignKept() {
        buildReview();
        open();

        press(() -> review.selectFilter(ReviewFilter.FOREIGN_KEPT));

        assertThat(desk.calls()).contains("queue(" + projectId + ", FOREIGN_KEPT)");
        assertThat(onFx(() -> review.filter().get())).isEqualTo(ReviewFilter.FOREIGN_KEPT);
    }

    private static SegmentView navLabel(final int number) {
        return new SegmentView(
                "nav." + number,
                "nav · 0" + number,
                SegmentKind.NAV_LABEL,
                SegmentStatus.ACCEPTED,
                "Chapter",
                "Chapter",
                "Chapter",
                null,
                null,
                List.of(),
                null,
                SegmentPath.SOURCE_KEPT,
                false,
                null,
                null);
    }

    // IF a kept-as-source record were listed by a chip, THEN the flagged list would count what nobody has to review.
    @Test
    void selectFilter_allSegmentsAfterUnattendedRun_listsKeptAsSourceMarked() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();
        setRunState(RunState.COMPLETED);
        desk.willAnswerQueue(List.of(navLabel(1), navLabel(2), navLabel(7)));

        press(() -> review.selectFilter(ReviewFilter.ALL_SEGMENTS));

        assertThat(locators()).containsExactly("nav · 01", "nav · 02", "nav · 07");
        assertThat(onFx(() -> review.rows().stream().allMatch(ReviewRow::keptAsSource)))
                .isTrue();
        assertThat(onFx(() -> review.rows().get(0).badge())).isNull();
    }

    @Test
    void allSegmentsOffered_unattendedRunCompleted_isOffered() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();

        setRunState(RunState.COMPLETED);

        assertThat(onFx(() -> review.allSegmentsOffered().get())).isTrue();
    }

    @Test
    void allSegmentsOffered_assistedRunCompleted_isNotOffered() {
        reviewMode = ReviewMode.ASSISTED;
        buildReview();

        setRunState(RunState.COMPLETED);

        assertThat(onFx(() -> review.allSegmentsOffered().get())).isFalse();
    }

    @Test
    void selectFilter_allSegmentsWhileRunning_isRefused() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();
        setRunState(RunState.RUNNING);

        press(() -> review.selectFilter(ReviewFilter.ALL_SEGMENTS));

        assertThat(onFx(() -> review.filter().get())).isEqualTo(ReviewFilter.ALL_FLAGGED);
        assertThat(desk.calls()).doesNotContain("queue(" + projectId + ", ALL_SEGMENTS)");
    }

    // The mirror clears its queue when a run starts, so the count must come from the desk, not from the mirror.
    @Test
    void open_desk_readsFlaggedCountFromTheDesk() {
        buildReview();

        open();

        assertThat(onFx(() -> review.flaggedCount().get())).isEqualTo(3);
        assertThat(desk.calls()).contains("counts(" + projectId + ")");
    }

    @Test
    void flaggedQueueChange_mirrorQueueChanged_refreshesFlaggedCountFromTheDesk() {
        buildReview();
        desk.willAnswerCounts(flaggedCount(5));

        mirror.live().publishFlaggedQueue(List.of(FlaggedRow.of(lowScore())));
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> review.flaggedCount().get())).isEqualTo(5);
    }

    @Test
    void open_noBookOpen_asksNothing() {
        review = onFx(
                () -> new ReviewViewModel(desk, mirror, current, reviewMode, toasts, errors, new DirectExecutor()));

        open();

        assertThat(desk.calls()).isEmpty();
        assertThat(locators()).isEmpty();
    }
}
