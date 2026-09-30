package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewCounts;

/** The pending count that decides whether a completed run offers a start, read from the desk off the FX thread. */
class TranslatingViewModelPendingTest extends TranslatingViewModelTestBase {

    private void refresh() {
        press(viewModel::refreshPending);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a completed run offered no Start while segments remain, THEN they could never be translated.
    @Test
    void refreshPending_deskAnswers3_publishesTheCountAndPendingRemains() {
        openBookAndChooseModel();
        desk.willAnswerCounts(new ReviewCounts(1240, 0, 0, 0, 0, 3, 0, 0));
        buildViewModel();

        refresh();

        assertThat(onFx(() -> viewModel.pendingCount().get())).isEqualTo(3);
        assertThat(onFx(() -> viewModel.pendingRemain().get())).isTrue();
        assertThat(desk.calls())
                .contains("counts(" + onFx(() -> current.book().get().projectId()) + ")");
    }

    // IF a failed read showed a stale or invented count, THEN the ready card would state something the desk never said.
    @Test
    void refreshPending_deskFails_reportsNoPendingRemaining() {
        openBookAndChooseModel();
        desk.willAnswer(Result.err(AppError.of(ErrorCode.internal, "No", "No")));
        buildViewModel();

        refresh();

        assertThat(onFx(() -> viewModel.pendingRemain().get())).isFalse();
    }

    // IF no book is open, THEN there is no project whose desk could be asked.
    @Test
    void refreshPending_noBookOpen_asksNothing() {
        buildViewModel();

        refresh();

        assertThat(desk.calls()).isEmpty();
        assertThat(onFx(() -> viewModel.pendingRemain().get())).isFalse();
    }
}
