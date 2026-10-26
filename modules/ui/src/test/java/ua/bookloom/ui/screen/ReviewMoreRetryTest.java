package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewFixtures;

/** Try again in the desk's More menu is offered only for a segment the desk can retry, and says why when it is not. */
class ReviewMoreRetryTest extends ReviewDeskTestBase {

    private static SegmentView revisedByThePerson() {
        final SegmentView edited = ReviewFixtures.withUserEdit();
        return new SegmentView(
                edited.segmentId(),
                edited.locator(),
                edited.kind(),
                SegmentStatus.REVISED,
                edited.maskedSource(),
                edited.displaySource(),
                edited.maskedMachineTarget(),
                edited.userTarget(),
                edited.maskedUserTarget(),
                edited.findings(),
                edited.judgeScore(),
                edited.path(),
                true,
                null,
                null);
    }

    private String subLine(final String id) {
        return fx(() -> ((Label) ((VBox) ((CustomMenuItem) moreItem(id)).getContent())
                        .getChildren()
                        .get(1))
                .getText());
    }

    // IF Try again were offered on "your edit", THEN pressing it would only answer that the segment cannot be retried
    // (W6-5).
    @Test
    void tryAgain_segmentHoldsTheEdit_isOffAndSaysToUndoFirst() throws Exception {
        openDeskWith(List.of(revisedByThePerson()), List.of());

        assertThat(fx(() -> moreItem("review-more-retry").isDisable())).isTrue();
        assertThat(fx(() -> moreItem("review-more-note").isDisable())).isTrue();
        assertThat(subLine("review-more-retry")).isEqualTo("Undo my edit first; your edit stays until then");
    }

    @Test
    void tryAgain_flaggedSegment_isOfferedWithItsUsualLine() throws Exception {
        openDeskWith(List.of(ReviewFixtures.lowScore()), List.of());

        assertThat(fx(() -> moreItem("review-more-retry").isDisable())).isFalse();
        assertThat(subLine("review-more-retry")).isEqualTo("Ask the model again, same context");
    }
}
