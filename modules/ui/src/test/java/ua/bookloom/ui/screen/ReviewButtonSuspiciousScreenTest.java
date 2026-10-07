package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/** The Review button opens a list that has entries and keeps its suspicious number current. */
class ReviewButtonSuspiciousScreenTest extends TranslatingScreenTestBase {

    private static final String REVIEW = "translating-review-flagged";

    // IF the button opened the flagged list while nothing is flagged, THEN a person told of 45 suspicious segments
    // would
    // meet an empty panel.
    @Test
    void reviewButton_noFlaggedButSuspicious_opensTheSuspiciousListAndRefreshesItsCountAfterAnAction()
            throws Exception {
        readyToStart();
        desk.willAnswerQueue(List.of(ReviewFixtures.accepted()));
        desk.willAnswerSegment(ReviewFixtures.accepted());
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 0, 0, 0, 0, 0, 0, 45));
        showTranslating();
        publish(RunState.COMPLETED);
        mirror().live().publishSuspicious(45);
        awaitFx(() -> button(REVIEW).getText().equals("Review (0 flagged · 45 suspicious)"));

        onFx(() -> button(REVIEW).fire());
        selectFirstRow();

        assertThat(desk.calls()).contains("queue(" + projectIdOfOpenBook() + ", SUSPICIOUS)");
        assertThat(rowTexts()).containsExactly("ch2 · p04");
        desk.willAnswerCounts(new ReviewCounts(100, 90, 0, 0, 0, 0, 0, 0, 0, 44));
        onFx(() -> button("review-accept").fire());
        awaitFx(() -> button(REVIEW).getText().equals("Review (0 flagged · 44 suspicious)"));
    }

    @SuppressWarnings("unchecked")
    private ListView<ReviewRow> rowList() {
        return (ListView<ReviewRow>) required("review-list");
    }

    private void selectFirstRow() throws Exception {
        awaitFx(() -> !rowList().getItems().isEmpty());
        onFx(() -> rowList().getSelectionModel().select(0));
        awaitFx(() -> !((TextArea) required("review-target")).getText().isEmpty());
    }

    private List<String> rowTexts() {
        return rowList().lookupAll(".list-cell").stream()
                .map(node -> (ListCell<?>) node)
                .filter(cell -> cell.getItem() != null)
                .map(cell -> String.join(
                        " ",
                        ((javafx.scene.Parent) cell.getGraphic())
                                .getChildrenUnmodifiable().stream()
                                        .filter(javafx.scene.control.Label.class::isInstance)
                                        .map(child -> ((javafx.scene.control.Label) child).getText())
                                        .filter(text -> !text.isEmpty())
                                        .toList()))
                .toList();
    }

    private String projectIdOfOpenBook() {
        return ThemeTestSupport.onFx(() -> injector.getInstance(ua.bookloom.ui.state.CurrentProject.class)
                .book()
                .get()
                .projectId());
    }
}
