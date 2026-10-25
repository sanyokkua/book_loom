package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.state.RunMode;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;

/**
 * The title bar's items give way by the window's width class (app-shell "Let title-bar items give way by priority",
 * {@code LayoutClass}): below the wide class (1440 px) the quality and review mode, the elapsed time and the speed go;
 * in a compact window (below 1040) the project's name goes too; the state text and the AI-model chip always stay.
 */
class TitleBarGiveWayTest extends ShellTestBase {

    private static final double HEIGHT = 700;
    private static final List<String> PARTS = List.of(
            "shell-run-project",
            "shell-run-state",
            "shell-run-mode",
            "shell-run-elapsed",
            "shell-run-rate",
            "shell-run-connection");

    private void translating() {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        onFx(() -> {
            mirror.publishRunStarted("Short.epub", new RunMode(QualityDial.BALANCED, ReviewMode.UNATTENDED));
            mirror.publishProgress(ProgressFixtures.progress(7, 11, 78, 0, 22));
            mirror.live()
                    .publishThroughput(new Throughput(9.4, false, Duration.ofMinutes(70), Duration.ofMinutes(62), 9.4));
            mirror.publishRunState(RunState.RUNNING);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String shownParts() {
        return String.join(
                " ", PARTS.stream().filter(id -> required(id).isVisible()).toList());
    }

    // IF the state or the chip gave way, THEN a narrow window would hide whether the AI model is answering.
    @ParameterizedTest
    @CsvSource({
        "1440, shell-run-project shell-run-state shell-run-mode shell-run-elapsed shell-run-rate shell-run-connection",
        "1439, shell-run-project shell-run-state shell-run-connection",
        "1280, shell-run-project shell-run-state shell-run-connection",
        "1040, shell-run-project shell-run-state shell-run-connection",
        "1023, shell-run-state shell-run-connection",
        "1000, shell-run-state shell-run-connection"
    })
    void titleBar_windowWidth_givesWayByPriority(final double width, final String expected) {
        translating();

        resizeScene(width, HEIGHT);

        assertThat(shownParts()).isEqualTo(expected);
    }

    // IF a part hidden for a narrow window never came back, THEN widening the window would not restore it.
    @ParameterizedTest
    @CsvSource({"1000", "1200"})
    void titleBar_wideAgainAfterNarrow_showsEveryPart(final double narrow) {
        translating();
        resizeScene(narrow, HEIGHT);

        resizeScene(1440, HEIGHT);

        assertThat(shownParts()).isEqualTo(String.join(" ", PARTS));
    }
}
