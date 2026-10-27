package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ModelChoice;
import ua.bookloom.ui.state.RunRow;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.SettingsViewModel;

/**
 * The one source of the run-state row, driven the way the application drives it: a book opened through the import, a
 * model typed in settings, and a run published through the state mirror.
 */
class RunStateSourceTest extends TranslatingScreenTestBase {

    private RunStateSource source() {
        return injector.getInstance(RunStateSource.class);
    }

    private RunRow row() {
        return ThemeTestSupport.onFx(() -> source().view().get().row());
    }

    // IF a project counted as open with none open, THEN the title bar would describe a run that cannot exist.
    @Test
    void projectOpen_noProjectOpen_isFalseAndNamesNothing() {
        assertThat(ThemeTestSupport.onFx(() -> source().projectOpen().get())).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> source().projectName().get())).isEmpty();
    }

    @Test
    void view_bookOpenAndModelChosen_isReadyAndNamesTheProject() throws TimeoutException {
        readyToStart();

        assertThat(row()).isEqualTo(RunRow.READY);
        assertThat(ThemeTestSupport.onFx(() -> source().projectName().get())).isEqualTo("Frankenstein · EN → UK");
    }

    // IF the source missed a change of the mirror, THEN the title bar and the toolbar would lag the run.
    @Test
    void view_runPublishedThroughTheMirror_followsItsState() throws TimeoutException {
        readyToStart();

        publish(RunState.RUNNING);
        assertThat(row()).isEqualTo(RunRow.TRANSLATING);

        publish(RunState.PAUSED);
        assertThat(row()).isEqualTo(RunRow.PAUSED);

        mirror().review().publishProviderError(AppError.of(ErrorCode.unreachable, "Unreachable", "No answer."));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(row()).isEqualTo(RunRow.PAUSED_AFTER_ERROR);
    }

    // IF the chip and the footer each read the settings their own way, THEN they could name different models.
    @Test
    void model_typedInSettings_isTheOneChoiceTheChipAndTheFooterRead() throws TimeoutException {
        readyToStart();

        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("qwen3:8b"));

        assertThat(ThemeTestSupport.onFx(() -> source().model().get()))
                .isEqualTo(new ModelChoice("Ollama", "qwen3:8b", true));
    }

    @Test
    void view_noModelChosen_cannotStart() throws TimeoutException {
        readyToStart();

        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set(""));

        assertThat(row()).isEqualTo(RunRow.CANNOT_START);
    }
}
