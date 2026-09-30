package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import javafx.scene.control.ScrollPane;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;

/** The live panel on the running dashboard: what its two rows say as the mirror's rows change. */
class LiveChunkPanelTest extends TranslatingScreenTestBase {

    private static final String SOURCE = "She had lost her mother, and the poor girl wept as she followed the coffin.";
    private static final String DRAFT = "Вона втратила матір, і бідна дівчина плакала, йдучи за труною.";
    private static final String LOCATOR = "ch7 · p42";

    private static LiveRow started() {
        return new LiveRow("s-42", LOCATOR, SOURCE, null, null, null, true, false);
    }

    private static LiveRow drafted(final boolean judged) {
        return new LiveRow("s-42", LOCATOR, SOURCE, DRAFT, null, null, false, judged);
    }

    private static LiveRow decided(final @Nullable Double score, final SegmentPath path) {
        return new LiveRow("s-42", LOCATOR, SOURCE, DRAFT, score, path, false, false);
    }

    private void showRows(final @Nullable LiveRow lastDecided, final @Nullable LiveRow current)
            throws TimeoutException {
        readyToStart();
        showTranslating();
        onFx(() -> injector.getInstance(BookBriefViewModel.class).setSourceLanguage("en"));
        mirror().live().publishLiveRows(new LiveRows(lastDecided, current));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a started segment did not show at once, THEN a person could not tell the model had begun on it.
    @Test
    void rows_segmentStarted_secondRowShowsItsSourceAndTheWait() throws TimeoutException {
        showRows(null, started());

        assertThat(labelText("live-current-source")).isEqualTo(SOURCE);
        assertThat(labelText("live-current-target")).isEqualTo("waiting for the model…");
        assertThat(labelText("live-current-locator")).isEqualTo(LOCATOR);
        assertThat(labelText("live-last-source")).isEmpty();
    }

    // IF the draft did not replace the wait, or were not marked, THEN the person would not know it is unjudged.
    @Test
    void rows_draftOnAJudgedRun_replacesTheWaitAndIsMarkedAwaitingJudge() throws TimeoutException {
        showRows(null, drafted(true));

        assertThat(labelText("live-current-target")).isEqualTo(DRAFT);
        assertThat(labelText("live-current-awaiting")).isEqualTo("awaiting judge");
        assertThat(isShown("live-current-awaiting")).isTrue();
    }

    // IF a Fast draft were marked, THEN the panel would promise a judge that is not there.
    @Test
    void rows_draftOnAFastRun_isNotMarkedAwaitingJudge() throws TimeoutException {
        showRows(null, drafted(false));

        assertThat(isShown("live-current-awaiting")).isFalse();
    }

    // IF a decision left the second row full, THEN one segment would be shown twice.
    @Test
    void rows_decisionWithScore_movesToTheFirstRowWithItsJudgeScoreAndEmptiesTheSecond() throws TimeoutException {
        showRows(decided(0.93, SegmentPath.REPAIRED), null);

        assertThat(labelText("live-last-locator")).isEqualTo(LOCATOR);
        assertThat(labelText("live-last-source")).isEqualTo(SOURCE);
        assertThat(labelText("live-last-target")).isEqualTo(DRAFT);
        assertThat(labelText("live-last-judge")).isEqualTo("judge 0.93");
        assertThat(labelText("live-current-source")).isEmpty();
        assertThat(labelText("live-current-target")).isEmpty();
    }

    // IF a Fast decision showed a judge badge, THEN the panel would invent a score.
    @Test
    void rows_fastDecision_showsThePathAndNoJudgeBadge() throws TimeoutException {
        showRows(decided(null, SegmentPath.DRAFT), null);

        assertThat(labelText("live-last-path")).isEqualTo("auto-accepted");
        assertThat(isShown("live-last-judge")).isFalse();
    }

    // IF a path had no badge or the wrong words, THEN the person could not tell how a segment was settled.
    @ParameterizedTest
    @CsvSource({
        "DRAFT,auto-accepted",
        "REPAIRED,repaired",
        "TM_REUSE,from memory",
        "USER,edited",
        "SOURCE_KEPT,source kept"
    })
    void rows_eachPath_showsItsBadgeText(final SegmentPath path, final String badge) throws TimeoutException {
        showRows(decided(null, path), null);

        assertThat(labelText("live-last-path")).isEqualTo(badge);
    }

    // IF the panes were not headed by the two languages, THEN a person could not tell which side is the model's.
    @Test
    void panes_areHeadedByTheSourceAndTargetLanguageNames() throws TimeoutException {
        showRows(decided(0.9, SegmentPath.DRAFT), started());

        assertThat(labelText("live-last-source-head")).isEqualTo("English");
        assertThat(labelText("live-last-target-head")).isEqualTo("Ukrainian");
        assertThat(labelText("live-current-source-head")).isEqualTo("English");
        assertThat(labelText("live-current-target-head")).isEqualTo("Ukrainian");
    }

    // IF a stale vertical offset survived a row change, THEN the card would show an empty area instead of its rows.
    @Test
    void rows_change_scrollsTheCardBackToTheTop() throws TimeoutException {
        showRows(decided(0.93, SegmentPath.DRAFT), started());
        final ScrollPane scroll = (ScrollPane) required("live-current-source-scroll");
        onFx(() -> scroll.setVvalue(1));

        mirror().live().publishLiveRows(new LiveRows(decided(0.93, SegmentPath.DRAFT), drafted(true)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(scroll.getVvalue()).isZero();
    }
}
