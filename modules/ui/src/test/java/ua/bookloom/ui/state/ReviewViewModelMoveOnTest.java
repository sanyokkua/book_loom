package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * After Accept or Save edit the panel moves on to the next flagged segment, or to its empty state when none is left,
 * and confirms the decision; it never keeps showing a segment that has left the list.
 */
class ReviewViewModelMoveOnTest extends ReviewViewModelTestBase {

    private @Nullable String selectedId() {
        return onFx(() -> {
            final SegmentView shown = review.selected().get();
            return shown == null ? null : shown.segmentId();
        });
    }

    private void listedAndSelected(final String segmentId) {
        buildReview();
        open();
        select(segmentId);
    }

    // IF an accepted segment stayed on show, THEN the person would keep reading a segment no longer in the list.
    @Test
    void accept_middleOfTheList_movesToTheNextFlaggedSegment() {
        listedAndSelected("ch05.xhtml:11");

        press(review::accept);

        assertThat(selectedId()).isEqualTo("ch07.xhtml:39");
    }

    // IF the last decision left the panel on the decided segment, THEN the empty state would never be reached.
    @Test
    void accept_nothingLeftListed_showsTheEmptyState() {
        listedAndSelected("ch09.xhtml:2");
        desk.willAnswerQueue(List.of());

        press(review::accept);

        assertThat(selectedId()).isNull();
        assertThat(onFx(() -> review.rows().isEmpty())).isTrue();
    }

    // IF a save were silent while the panel moved on, THEN the person could not tell the edit was kept.
    @Test
    void saveEdit_edited_confirmsTheSaveAndMovesOn() {
        listedAndSelected("ch05.xhtml:11");
        onFx(() -> {
            review.editorText().set("Гейл відчинив ⟦g0⟧старі⟦g1⟧ двері!");
            return null;
        });

        press(review::saveEdit);

        assertThat(toasts.raised())
                .extracting(raised -> raised.severity() + ":" + raised.key() + ":" + raised.args())
                .contains("success:" + MessageKey.REVIEW_SAVED + ":[ch5 · p12]");
        assertThat(selectedId()).isEqualTo("ch07.xhtml:39");
    }
}
