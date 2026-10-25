package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.state.LiveRow.Stage;
import ua.bookloom.ui.state.RunState;

/** What is below the strip in each state of the run: the ready card before a run, the live card from its start on. */
class TranslatingRowLayoutTest extends LiveCardTestBase {

    // IF a state showed the wrong card, THEN a book not yet started would show an empty live card, or a run in progress
    // would offer to start.
    @ParameterizedTest
    @CsvSource({
        "IDLE, true",
        "RUNNING, false",
        "PAUSING, false",
        "PAUSED, false",
        "STOPPING, false",
        "STOPPED, false",
        "COMPLETED, false",
        "FAILED, false"
    })
    void belowTheStrip_eachRunState_isTheReadyCardOrTheLiveCard(final RunState state, final boolean ready) {
        chooseModel();
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);

        publish(state);

        assertThat(isShown("translating-strip")).isTrue();
        assertThat(isShown("translating-ready-card")).isEqualTo(ready);
        assertThat(isShown("translating-live-card")).isEqualTo(!ready);
    }

    // IF a paused or stopped run still read "translating..." on the segments that were in its last call, THEN the card
    // would claim work that is not going on.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"PAUSED", "STOPPED", "FAILED"})
    void rows_runNotWorking_readWaitingWhereTheyWereInACall(final RunState state) {
        showRows(LiveRowFixtures.rows(2, Stage.IN_CALL));
        assertThat(markerWords()).containsExactly("translating...", "translating...");

        publish(state);

        assertThat(markerWords()).containsExactly("waiting", "waiting");
    }

    // IF a pending pause already stopped the marker, THEN the segments still in the model would look idle.
    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"PAUSING", "STOPPING"})
    void rows_pauseOrStopPending_keepTranslating(final RunState state) {
        showRows(LiveRowFixtures.rows(2, Stage.IN_CALL));

        publish(state);

        assertThat(markerWords()).isEqualTo(List.of("translating...", "translating..."));
    }
}
