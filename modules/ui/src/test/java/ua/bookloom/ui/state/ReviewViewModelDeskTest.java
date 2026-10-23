package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.ReviewFixtures;

/** What the desk view model adds to the old panel's: opening on a segment, moving, leaving for later, "x of y". */
class ReviewViewModelDeskTest extends ReviewViewModelTestBase {

    private static final String FIRST = "ch05.xhtml:11";
    private static final String SECOND = "ch07.xhtml:39";

    private @Nullable String selectedId() {
        return onFx(() ->
                review.selected().get() == null ? null : review.selected().get().segmentId());
    }

    private void builtAndSelected(final String segmentId) {
        buildReview();
        setRunState(RunState.PAUSED);
        press(() -> review.openOn(ReviewFilter.ALL_FLAGGED, segmentId));
    }

    private DeskMarker markerOf(final String segmentId) {
        return onFx(() -> review.rows().stream()
                .filter(row -> row.segmentId().equals(segmentId))
                .map(ReviewRow::marker)
                .findFirst()
                .orElse(null));
    }

    @Test
    void filter_beforeAnythingIsChosen_isAllFlagged() {
        buildReview();

        assertThat(onFx(() -> review.filter().get())).isEqualTo(ReviewFilter.ALL_FLAGGED);
    }

    // IF the desk opened on nothing, THEN a person would face a blank detail pane; no id picks the first row.
    @Test
    void openOn_noSegmentId_selectsTheFirstRow() {
        buildReview();

        press(() -> review.openOn(ReviewFilter.ALL_FLAGGED, null));

        assertThat(selectedId()).isEqualTo(FIRST);
        assertThat(locators()).containsExactly("ch5 · p12", "ch7 · p40", "ch9 · p03");
    }

    // IF a review pause opened the desk on the first row, THEN the person would not see the segment it names.
    @Test
    void openOn_namedSegment_selectsThatSegment() {
        builtAndSelected("ch09.xhtml:2");

        assertThat(selectedId()).isEqualTo("ch09.xhtml:2");
    }

    @Test
    void move_plusOne_selectsTheNextRowOfTheFilter() {
        builtAndSelected(FIRST);

        press(() -> review.move(1));

        assertThat(selectedId()).isEqualTo(SECOND);
    }

    @Test
    void move_minusOne_selectsThePreviousRow() {
        builtAndSelected(SECOND);

        press(() -> review.move(-1));

        assertThat(selectedId()).isEqualTo(FIRST);
    }

    // IF a move past the ends wrapped or threw, THEN a held key would jump around; it stays on the end row.
    @Test
    void move_pastTheFirstRow_staysOnIt() {
        builtAndSelected(FIRST);

        press(() -> review.move(-1));

        assertThat(selectedId()).isEqualTo(FIRST);
    }

    // IF leaving for later wrote to the project, THEN "later" would outlive the app run; it is a mark only.
    @Test
    void leaveForLater_selectedSegment_makesNoActionCallAndTheCountStays() {
        builtAndSelected(FIRST);

        press(review::leaveForLater);

        assertThat(desk.calls())
                .noneMatch(call -> call.startsWith("accept(")
                        || call.startsWith("saveEdit(")
                        || call.startsWith("revert(")
                        || call.startsWith("retry(")
                        || call.startsWith("skip("));
        assertThat(onFx(() -> review.rows().size())).isEqualTo(3);
        assertThat(onFx(() -> review.flaggedCount().get())).isEqualTo(3);
    }

    @Test
    void leaveForLater_selectedSegment_marksItLaterAndSelectsTheNextOne() {
        builtAndSelected(FIRST);

        press(review::leaveForLater);

        assertThat(markerOf(FIRST)).isEqualTo(DeskMarker.LATER);
        assertThat(markerOf(SECOND)).isEqualTo(DeskMarker.TO_REVIEW);
        assertThat(selectedId()).isEqualTo(SECOND);
    }

    // IF a list re-read forgot the mark, THEN the person's "later" would vanish at the next count refresh.
    @Test
    void leaveForLater_thenTheListIsReadAgain_theMarkSurvives() {
        builtAndSelected(FIRST);
        press(review::leaveForLater);

        open();

        assertThat(markerOf(FIRST)).isEqualTo(DeskMarker.LATER);
    }

    // IF a re-read replaced what the person was typing, THEN a count refresh mid-edit would lose their work.
    @Test
    void open_whileTextIsTyped_keepsTheTyping() {
        builtAndSelected(FIRST);
        onFx(() -> {
            review.editorText().set("Він пішов геть.");
            return null;
        });

        open();

        assertThat(onFx(() -> review.editorText().get())).isEqualTo("Він пішов геть.");
    }

    // IF All segments were offered only after an Unattended run, THEN a person who chose Assisted could never browse.
    @Test
    void selectFilter_allSegmentsAfterAnAssistedRun_listsEveryDecidedSegment() {
        buildReview();
        reviewMode = ReviewMode.ASSISTED;
        desk.willAnswerQueue(ReviewFilter.ALL_SEGMENTS, List.of(ReviewFixtures.accepted(), lowScore()));
        setRunState(RunState.PAUSED);

        press(() -> review.selectFilter(ReviewFilter.ALL_SEGMENTS));

        assertThat(locators()).containsExactly("ch2 · p04", "ch5 · p12");
    }

    @Test
    void deskTotal_sixToReviewWhenItOpened_staysSixWhileFewerAreLeft() {
        buildReview();
        desk.willAnswerCounts(flaggedCount(6));
        press(() -> review.openOn(ReviewFilter.ALL_FLAGGED, null));
        desk.willAnswerCounts(flaggedCount(5));

        press(review::refreshCount);

        assertThat(onFx(() -> review.deskTotal().get())).isEqualTo(6);
        assertThat(onFx(() -> review.flaggedCount().get())).isEqualTo(5);
    }
}
