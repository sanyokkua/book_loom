package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ReviewFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.RunState;

/** A retry from the review panel leaves the page where the person had it, with the same control focused. */
class ReviewRetryPageScreenTest extends ReviewPanelScreenTestBase {

    private static final double KEPT_OFFSET = 0.6;
    private static final double TOLERANCE = 0.001;

    // IF the retry's disabled buttons and the swapped compare moved the page or the focus, THEN the person would have
    // to find the segment and the control again after every retry.
    @Test
    void retry_answeredWithAShorterCompare_keepsTheScrollOffsetAndTheFocusedControl() throws Exception {
        openPanelWith(RunState.PAUSED, 1, ReviewFixtures.longSegment());
        selectFirstRow();
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        final ScrollPane page = (ScrollPane) required("shell-content-scroll");
        onFx(() -> page.setVvalue(KEPT_OFFSET));
        onFx(() -> required("review-retry").requestFocus());
        final double before = ThemeTestSupport.onFx(page::getVvalue);
        desk.holdRetry();

        onFx(() -> button("review-retry").fire());
        awaitFx(() -> button("review-retry").isDisabled());
        desk.willAnswerSegment(ReviewFixtures.longSegmentRetried());
        desk.releaseRetry();
        awaitFx(() -> !button("review-retry").isDisabled());
        awaitFx(() -> "Коротко.".equals(((javafx.scene.control.TextArea) required("review-target")).getText()));
        onFx(() -> {});

        assertThat(ThemeTestSupport.onFx(page::getVvalue))
                .isCloseTo(before, org.assertj.core.data.Offset.offset(TOLERANCE));
        assertThat(ThemeTestSupport.onFx(() -> {
                    final Node owner = scene.getFocusOwner();
                    return owner == null ? "none" : owner.getId();
                }))
                .isEqualTo("review-retry");
    }
}
