package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.dialog.ConfirmPrompt;

/** The question asked before a translation stops: its two answers are Keep translating and Stop, never Cancel. */
class StopQuestionTest extends ShellTestBase {

    // IF the safe answer said Cancel, THEN a person could read it as cancelling the translation.
    @ParameterizedTest
    @CsvSource({"en, Keep translating, Stop", "uk, Перекладати далі, Зупинити"})
    void ask_stopRun_offersKeepTranslatingAndStop(final String language, final String keep, final String stop) {
        useLocale(Locale.of(language));

        ThemeTestSupport.onFx(() -> {
            injector.getInstance(ConfirmPrompt.class)
                    .ask(ConfirmPrompt.Question.STOP_RUN, List.of(), List.of(74, 142, 4), () -> {});
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(((Button) required("confirm-cancel")).getText()).isEqualTo(keep);
        assertThat(((Button) required("confirm-yes")).getText()).isEqualTo(stop);
    }
}
