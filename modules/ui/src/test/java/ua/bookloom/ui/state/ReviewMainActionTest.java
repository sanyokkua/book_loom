package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.ui.i18n.MessageKey;

/** What the desk's one main button says and whether it can be pressed. */
class ReviewMainActionTest extends ReviewViewModelTestBase {

    private ReviewMainAction main() {
        return onFx(() -> review.mainAction().get());
    }

    private void paused() {
        buildReview();
        setRunState(RunState.PAUSED);
        press(() -> review.openOn(ReviewFilter.ALL_FLAGGED, "ch07.xhtml:39"));
    }

    @Test
    void mainAction_cleanSegmentOnAPausedRun_isAcceptAndEnabled() {
        paused();

        assertThat(main().key()).isEqualTo(MessageKey.REVIEW_MAIN_ACCEPT);
        assertThat(main().enabled()).isTrue();
    }

    // IF the button kept saying Accept after typing, THEN pressing it would throw the typing away.
    @Test
    void mainAction_textTyped_isSaveMyEditAndEnabled() {
        paused();

        onFx(() -> {
            review.editorText().set("Він пішов ⟦0⟧геть⟦1⟧.");
            return null;
        });

        assertThat(main().key()).isEqualTo(MessageKey.REVIEW_MAIN_SAVE);
        assertThat(main().enabled()).isTrue();
    }

    // IF an edit with a missing mark fell back to Accept, THEN pressing it would keep the machine text unseen; it stays
    // Save my edit and is off until every mark is back.
    @ParameterizedTest
    @CsvSource({
        "false,true,true,true,REVIEW_MAIN_ACCEPT,true",
        "false,true,true,false,REVIEW_MAIN_ACCEPT,false",
        "false,true,false,true,REVIEW_MAIN_ACCEPT,true",
        "false,false,true,true,REVIEW_MAIN_ACCEPT,true",
        "true,true,true,true,REVIEW_MAIN_SAVE,true",
        "true,true,false,true,REVIEW_MAIN_SAVE,false",
        "true,false,true,true,REVIEW_MAIN_SAVE,false",
        "true,false,false,true,REVIEW_MAIN_SAVE,false"
    })
    void of_changedCompleteActionsAccept_picksTheWordAndWhetherItIsOn(
            final boolean changed,
            final boolean complete,
            final boolean actions,
            final boolean accept,
            final MessageKey key,
            final boolean enabled) {
        final ReviewMainAction action = ReviewMainAction.of(changed, complete, actions, accept);

        assertThat(action.key()).isEqualTo(key);
        assertThat(action.enabled()).isEqualTo(enabled);
    }

    // IF the mark were deleted and the button stayed on, THEN a broken edit would be sent to the desk.
    @Test
    void mainAction_aMarkDeletedFromTheEditor_isSaveMyEditAndDisabled() {
        paused();

        onFx(() -> {
            review.editorText().set("Він пішов геть.");
            return null;
        });

        assertThat(main().key()).isEqualTo(MessageKey.REVIEW_MAIN_SAVE);
        assertThat(main().enabled()).isFalse();
    }

    // IF the button stayed live while the model translated or a retry ran, THEN it would race for the same segment.
    @ParameterizedTest
    @CsvSource({"RUNNING,false", "PAUSING,false", "PAUSED,true"})
    void mainAction_runState_isEnabledOnlyWhenTheRunIsNotTranslating(final RunState state, final boolean enabled) {
        paused();

        setRunState(state);

        assertThat(main().enabled()).isEqualTo(enabled);
    }

    @Test
    void mainAction_retryInFlight_isDisabled() {
        paused();

        onFx(() -> {
            mirror.review().publishRetryInFlight(true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(main().enabled()).isFalse();
    }
}
