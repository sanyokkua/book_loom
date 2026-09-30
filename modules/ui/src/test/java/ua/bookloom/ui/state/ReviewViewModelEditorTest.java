package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.i18n.MessageKey;

/** What the editor shows for the selected segment, when it is dirty, and what Accept is offered on. */
class ReviewViewModelEditorTest extends ReviewViewModelTestBase {

    private String editor() {
        return onFx(() -> review.editorText().get());
    }

    // IF the editor hid the tokens, THEN a person could not see where the bold and links sit.
    @Test
    void select_segmentWithMachineTarget_showsItWithItsTokens() {
        buildReview();

        select("ch05.xhtml:11");

        assertThat(editor()).isEqualTo("Він відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        assertThat(desk.calls()).contains("segment(" + projectId + ", ch05.xhtml:11)");
    }

    // IF a flagged segment with no target opened an empty editor, THEN saving would write nothing over the source.
    @Test
    void select_flaggedWithoutAnyTarget_showsTheMaskedSource() {
        buildReview();
        desk.willAnswerSegment(withEditor(lowScore(), null, null));

        select("ch05.xhtml:11");

        assertThat(editor()).isEqualTo("He opened the ⟦g0⟧old⟦g1⟧ door.");
    }

    @Test
    void accept_noMachineTarget_showsTheRefusalInPlaceAndTheRowStaysFlagged() {
        buildReview();
        desk.willAnswerSegment(withEditor(lowScore(), null, null));
        select("ch05.xhtml:11");
        desk.willAnswer(Result.err(AppError.of(
                ErrorCode.validation,
                "Cannot accept",
                "There is no machine translation to accept — edit it or retry.")));

        press(review::accept);

        assertThat(onFx(() -> review.problem().get()))
                .isEqualTo("There is no machine translation to accept — edit it or retry.");
        assertThat(onFx(() -> review.selected().get().status())).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(errors.presented()).isEmpty();
    }

    // IF an edited (REVISED) segment could be accepted, THEN the edit would be marked accepted unseen.
    @Test
    void select_revisedWithUserTarget_showsTheEditAndOffersNoAccept() {
        buildReview();
        final SegmentView revised = new SegmentView(
                "ch05.xhtml:11",
                "ch5 · p12",
                lowScore().kind(),
                SegmentStatus.REVISED,
                MASKED_SOURCE,
                "He opened the old door.",
                MASKED_MACHINE,
                "Він рвучко відчинив старі двері.",
                "Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері.",
                List.of(),
                null,
                lowScore().path(),
                true,
                null,
                null);
        desk.willAnswerSegment(revised);

        select("ch05.xhtml:11");

        assertThat(editor()).isEqualTo("Він рвучко відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        assertThat(onFx(() -> review.acceptAvailable().get())).isFalse();
    }

    @Test
    void select_flaggedSegmentAtRest_offersAccept() {
        buildReview();

        select("ch05.xhtml:11");

        assertThat(onFx(() -> review.acceptAvailable().get())).isTrue();
        assertThat(onFx(() -> review.dirty().get())).isFalse();
    }

    // IF typing left Accept on, THEN the unsaved edit would be silently dropped by accepting the machine text.
    @Test
    void editorText_typed_setsDirtyDisablesAcceptAndShowsTheHint() {
        buildReview();
        select("ch05.xhtml:11");

        onFx(() -> {
            review.editorText().set("Він відчинив ⟦g0⟧старі⟦g1⟧ двері!");
            return null;
        });

        assertThat(onFx(() -> review.dirty().get())).isTrue();
        assertThat(onFx(() -> review.acceptAvailable().get())).isFalse();
        assertThat(onFx(() -> review.hint().get())).isEqualTo(MessageKey.REVIEW_EDITING_HINT);
    }

    @Test
    void editorText_typedThenRestored_isNotDirty() {
        buildReview();
        select("ch05.xhtml:11");
        onFx(() -> {
            review.editorText().set("x");
            review.editorText().set(MASKED_MACHINE);
            return null;
        });

        assertThat(onFx(() -> review.dirty().get())).isFalse();
        assertThat(onFx(() -> review.hint().get())).isNull();
    }

    @Test
    void saveEdit_dirtyEditor_callsTheDeskWithTheSegmentIdAndTheText() {
        buildReview();
        select("ch05.xhtml:11");
        onFx(() -> {
            review.editorText().set("Чудовисько стріло мене опівночі.");
            return null;
        });

        press(review::saveEdit);

        assertThat(desk.calls())
                .contains("saveEdit(" + projectId + ", ch05.xhtml:11, Чудовисько стріло мене опівночі.)");
    }

    // IF the reason for a refused save were dropped, THEN the person would not know a token was deleted.
    @Test
    void saveEdit_tokensDeleted_showsTheReasonInPlaceAndKeepsTheSegmentFlagged() {
        buildReview();
        select("ch05.xhtml:11");
        onFx(() -> {
            review.editorText().set("Він відчинив старі двері.");
            return null;
        });
        desk.willAnswer(Result.err(
                AppError.of(ErrorCode.validation, "Edit refused", "The edit lost the placeholders ⟦g0⟧ and ⟦g1⟧.")));

        press(review::saveEdit);

        assertThat(onFx(() -> review.problem().get())).isEqualTo("The edit lost the placeholders ⟦g0⟧ and ⟦g1⟧.");
        assertThat(onFx(() -> review.selected().get().status())).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(onFx(() -> review.dirty().get())).isTrue();
    }

    @Test
    void revert_editedSegment_callsTheDeskAndShowsTheMachineTarget() {
        buildReview();
        final SegmentView edited = withEditor(lowScore(), "Чудовисько зустріло мене опівночі.", "Мій текст");
        desk.willAnswerSegment(edited);
        select("ch05.xhtml:11");
        desk.willAnswerSegment(withEditor(lowScore(), "Чудовисько зустріло мене опівночі.", null));

        press(review::revert);

        assertThat(desk.calls()).contains("revert(" + projectId + ", ch05.xhtml:11)");
        assertThat(editor()).isEqualTo("Чудовисько зустріло мене опівночі.");
        assertThat(onFx(() -> review.dirty().get())).isFalse();
    }

    // IF a segment were named by its locator, THEN two segments printed alike would be confused.
    @Test
    void accept_flaggedSegment_passesTheSegmentIdAndNeverTheLocator() {
        buildReview();
        select("ch05.xhtml:11");

        press(review::accept);

        assertThat(desk.calls())
                .contains("accept(" + projectId + ", ch05.xhtml:11)")
                .noneMatch(call -> call.contains("ch5 · p12"));
    }

    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"RUNNING", "PAUSING", "STOPPING"})
    void actionsAvailable_runTranslating_areUnavailable(final RunState state) {
        buildReview();
        select("ch05.xhtml:11");

        setRunState(state);

        assertThat(onFx(() -> review.actionsAvailable().get())).isFalse();
        assertThat(onFx(() -> review.acceptAvailable().get())).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"PAUSED", "STOPPED", "COMPLETED", "FAILED", "IDLE"})
    void actionsAvailable_runNotTranslating_areAvailable(final RunState state) {
        buildReview();
        select("ch05.xhtml:11");

        setRunState(state);

        assertThat(onFx(() -> review.actionsAvailable().get())).isTrue();
        assertThat(onFx(() -> review.acceptAvailable().get())).isTrue();
    }

    // IF a busy answer opened a dialog, THEN a run starting under the panel would interrupt the person.
    @Test
    void accept_deskAnswersBusy_raisesOneWarningToastAndNoDialog() {
        buildReview();
        select("ch05.xhtml:11");
        desk.willAnswer(Result.err(AppError.of(ErrorCode.busy, "Busy", "A run is going.")));

        press(review::accept);

        assertThat(toasts.raised())
                .extracting(raised -> raised.severity() + ":" + raised.key())
                .containsExactly("warning:" + MessageKey.REVIEW_BUSY);
        assertThat(errors.presented()).isEmpty();
    }

    // The count is what is left after this accept, read from the desk.
    @Test
    void accept_twoLeft_raisesTheSuccessToastNamingTheLocatorAndTheRemainder() {
        buildReview();
        select("ch05.xhtml:11");
        desk.willAnswerCounts(flaggedCount(2));

        press(review::accept);

        assertThat(toasts.raised())
                .extracting(raised -> raised.severity() + ":" + raised.key() + ":" + raised.args())
                .containsExactly("success:" + MessageKey.REVIEW_ACCEPTED + ":[ch5 · p12, 2]");
        assertThat(onFx(() -> review.flaggedCount().get())).isEqualTo(2);
    }

    // Skip moves on to the flagged segment the desk names next.
    @Test
    void skip_nextFlaggedNamed_selectsThatRow() {
        buildReview();
        select("ch05.xhtml:11");
        desk.willAnswer(Result.ok(ScriptedReviewDesk.recordOf(projectId, nameIssue())));

        press(review::skip);

        assertThat(desk.calls()).contains("skip(" + projectId + ", ch05.xhtml:11)");
        assertThat(onFx(() -> review.selected().get().locator())).isEqualTo("ch7 · p40");
    }
}
