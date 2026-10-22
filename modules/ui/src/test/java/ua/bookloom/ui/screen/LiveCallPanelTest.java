package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.LiveRowFixtures.row;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.MemorySummaries;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.LiveRow.Stage;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.OpenBookForTest;
import ua.bookloom.ui.state.TechnicalDetails;

/** The live card as a whole: its two tabs, its Simple and Detailed choice, its scene summary and the log tab. */
class LiveCallPanelTest extends LiveCardTestBase {

    private static final String SUMMARY_NOTE = "Updated after every 20 accepted segments and at the end of each"
            + " chapter. On Max quality the model writes the one at the end of each chapter.";

    private void showCard() {
        showRows(List.of(row(1, Stage.ACCEPTED, "Перший."), row(2, Stage.IN_CALL, null)));
    }

    private TabPane tabs() {
        return (TabPane) required("translating-live-tabs");
    }

    // IF the card were not two tabs, THEN the log would sit below the list and push the summary off the screen.
    @Test
    void tabs_cardShown_areLiveThenActivityLogWithLiveSelected() {
        showCard();

        assertThat(ThemeTestSupport.onFx(
                        () -> tabs().getTabs().stream().map(Tab::getText).toList()))
                .containsExactly("Live", "Activity log");
        assertThat(ThemeTestSupport.onFx(() -> tabs().getSelectionModel().getSelectedIndex()))
                .isZero();
    }

    // IF the card did not exist before the run, THEN a book not yet started would show an empty box.
    @Test
    void card_noRunYet_isNotShown() {
        showTranslating();

        assertThat(isShown("translating-live-card")).isFalse();
    }

    // IF the list and detail stacked in a narrow window, THEN the detail would sit below the fold and the pair would
    // lose its point.
    @Test
    void listAndDetail_atMinimumWidth_stayNextToEachOther() {
        showCard();
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        awaitLayout();

        assertThat(boundsOf(DETAIL).getMinX())
                .isGreaterThanOrEqualTo(boundsOf(ROWS).getMaxX());
        assertThat(boundsOf(ROWS).getWidth()).isGreaterThanOrEqualTo(240);
    }

    // IF the list took the width of a window, THEN the detail would be squeezed to a sliver.
    @Test
    void list_atWideWindow_takesAtMostAThirdOfTheCard() {
        showCard();
        showAtSceneSize(1280, 800);

        assertThat(boundsOf(ROWS).getWidth())
                .isLessThanOrEqualTo(boundsOf("translating-live-card").getWidth() * 0.34 + 1);
    }

    // IF the card opened in Simple for someone who asked for technical details, THEN they would turn it on every run.
    @Test
    void mode_technicalDetailsOn_opensInDetailed() {
        onFx(() -> injector.getInstance(TechnicalDetails.class).property().set(true));

        showCard();

        assertThat(((ToggleButton) required("translating-live-mode-detailed")).isSelected())
                .isTrue();
    }

    @Test
    void mode_technicalDetailsOff_opensInSimple() {
        showCard();

        assertThat(((ToggleButton) required("translating-live-mode-simple")).isSelected())
                .isTrue();
    }

    // IF the choice were forgotten on a visit to another screen, THEN a person who wants Detailed would press it again.
    @Test
    void mode_detailedChosenThenTheScreenIsVisitedAgain_staysDetailed() {
        showCard();
        chooseMode(true);

        showTranslating();

        assertThat(((ToggleButton) required("translating-live-mode-detailed")).isSelected())
                .isTrue();
    }

    // IF the follow switch were forgotten on a visit, THEN a person who turned it off would be followed again.
    @Test
    void followSwitch_turnedOffThenTheScreenIsVisitedAgain_staysOff() {
        showCard();
        onFx(() -> ((ToggleSwitch) required("translating-live-follow")).setSelected(false));

        showTranslating();

        assertThat(((ToggleSwitch) required("translating-live-follow")).isSelected())
                .isFalse();
    }

    // IF a book with no summary left the line blank, THEN a person would wonder whether it broke.
    @Test
    void sceneSummary_noneYet_saysSoAndSaysHowItIsKept() {
        showCard();

        assertThat(labelText("translating-live-summary-text")).isEqualTo("No scene summary yet.");
        assertThat(labelText("translating-live-summary-source")).isEqualTo(SUMMARY_NOTE);
    }

    // IF the line did not read the summary the run keeps, THEN it would stay "none" while the model remembers a story.
    @Test
    void sceneSummary_runSavedOne_showsItsTargetText() {
        onFx(() -> OpenBookForTest.open(injector.getInstance(CurrentProject.class), "en", "uk"));
        showCard();
        final String id = ThemeTestSupport.onFx(
                () -> injector.getInstance(CurrentProject.class).book().get().projectId());
        ((MemorySummaries) injector.getInstance(SummaryRepository.class))
                .put(id, "Mira Holt goes back to Velk.", "Міра Холт повертається до Велка.", 1);

        publishCalls(new LiveCalls(null, null, Map.of(), null, LiveRowFixtures.rows(2, Stage.IN_CALL), List.of(), 1));

        assertThat(labelText("translating-live-summary-text")).isEqualTo("Міра Холт повертається до Велка.");
    }

    // IF the log tab listed every line with no way to see only the trouble, THEN one failure would hide among them.
    @Test
    void problemsOnly_chosen_listsOnlyFailedCallsRetriesAndSegmentsToReview() {
        showCard();
        final List<LogEntry> lines = IntStream.range(0, 40)
                .mapToObj(i -> i < 3
                        ? new LogEntry(LogKind.SEGMENT_ERROR, List.of("ch7 · p" + i))
                        : new LogEntry(LogKind.ACCEPTED, List.of("ch7 · p" + i)))
                .toList();
        mirror().publishLogEntries(lines);
        WaitForAsyncUtils.waitForFxEvents();
        openActivityLog();
        assertThat(logCells()).hasSizeGreaterThan(3);

        onFx(() -> ((ToggleButton) required("translating-log-problems")).fire());

        assertThat(logCells()).hasSize(3);
        assertThat(TooltipProbe.tipText(required("translating-log-problems")))
                .isEqualTo("Shows only failed calls, retries and segments to review.");
        onFx(() -> ((ToggleButton) required("translating-log-all")).fire());
        assertThat(logList().getItems()).hasSize(40);
    }
}
