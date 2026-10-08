package ua.bookloom.ui.screen;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/** What the review panel tests share: scripting the desk, opening the panel on a state and selecting its first row. */
abstract class ReviewPanelScreenTestBase extends TranslatingScreenTestBase {

    static final String REVIEW = "translating-review-flagged";

    void script(final int flagged, final SegmentView... views) {
        desk.willAnswerQueue(List.of(views));
        for (final SegmentView view : views) {
            desk.willAnswerSegment(view);
        }
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, flagged, 0, 0, 0, 0, 0));
    }

    void publishFlagged(final int count) {
        mirror().live()
                .publishFlaggedQueue(java.util.stream.IntStream.range(0, count)
                        .mapToObj(i -> new FlaggedRow("s-" + i, "ch7 · p" + i, List.of(), null))
                        .toList());
        WaitForAsyncUtils.waitForFxEvents();
    }

    void openPanelWith(final RunState state, final int flagged, final SegmentView... views) throws Exception {
        readyToStart();
        script(flagged, views);
        showTranslating();
        publish(state);
        publishFlagged(flagged);
        awaitFx(() -> button(REVIEW).getText().equals("Review flagged (" + flagged + ")"));
        onFx(() -> button(REVIEW).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    ListView<ReviewRow> rowList() {
        return (ListView<ReviewRow>) required("review-list");
    }

    void selectFirstRow() throws TimeoutException {
        awaitFx(() -> !rowList().getItems().isEmpty());
        onFx(() -> rowList().getSelectionModel().select(0));
        awaitFx(() -> !((TextArea) required("review-target")).getText().isEmpty());
    }

    String projectIdOfOpenBook() {
        return ThemeTestSupport.onFx(() -> injector.getInstance(ua.bookloom.ui.state.CurrentProject.class)
                .book()
                .get()
                .projectId());
    }
}
