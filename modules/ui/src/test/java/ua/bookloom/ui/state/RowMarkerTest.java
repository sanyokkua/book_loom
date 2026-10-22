package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.state.LiveRow.Stage;

/** The marker of a live row is a pure function of the row and of what the run is doing (which of the 16 rows). */
class RowMarkerTest {

    private static LiveRow row(final Stage stage, final @Nullable String fallbackCode) {
        return new LiveRow("s-1", "ch7 · p42", "Source.", null, stage, fallbackCode, List.of(), null);
    }

    // IF a paused run still read "translating…", THEN the live card would claim work that is not going on.
    @ParameterizedTest
    @CsvSource({
        "PAUSED, IN_CALL, WAITING",
        "WAITING, IN_CALL, WAITING",
        "PAUSED_AFTER_ERROR, IN_CALL, WAITING",
        "STOPPED_TRYING, IN_CALL, WAITING",
        "MODEL_MISSING, IN_CALL, WAITING",
        "STOPPED, IN_CALL, WAITING",
        "FAILED, IN_CALL, WAITING",
        "READY, IN_CALL, WAITING",
        "READY_TO_CONTINUE, DRAFTED, WAITING",
        "CANNOT_START, QUEUED, WAITING",
        "TRANSLATING, IN_CALL, TRANSLATING",
        "PAUSING, IN_CALL, TRANSLATING",
        "STOPPING, IN_CALL, TRANSLATING",
        "STUCK, IN_CALL, TRANSLATING",
        "TRANSLATING, DRAFTED, TRANSLATING",
        "TRANSLATING, QUEUED, WAITING",
        "STUCK, QUEUED, WAITING"
    })
    void of_inFlightRow_readsByWhatTheRunIsDoing(final RunRow run, final Stage stage, final RowMarker expected) {
        assertThat(RowMarker.of(row(stage, null), run)).isEqualTo(expected);
    }

    // IF a decided row changed with the run's state, THEN a pause would take back what was already translated.
    @ParameterizedTest
    @CsvSource({
        "TRANSLATING, ACCEPTED, TRANSLATED",
        "PAUSED, ACCEPTED, TRANSLATED",
        "FINISHED_TO_REVIEW, ACCEPTED, TRANSLATED",
        "TRANSLATING, FLAGGED, TO_REVIEW",
        "FAILED, FLAGGED, TO_REVIEW",
        "TRANSLATING, KEPT, KEPT",
        "STOPPED, KEPT, KEPT"
    })
    void of_decidedRow_keepsItsMarkerInEveryRun(final RunRow run, final Stage stage, final RowMarker expected) {
        assertThat(RowMarker.of(row(stage, null), run)).isEqualTo(expected);
    }

    // IF a segment drafted alone after a bad batch reply read as an ordinary one, THEN the fallback would be hidden.
    @ParameterizedTest
    @CsvSource({
        "TRANSLATING, DRAFTED, ALONE",
        "TRANSLATING, ACCEPTED, ALONE",
        "FINISHED_NONE, ACCEPTED, ALONE",
        "TRANSLATING, FLAGGED, TO_REVIEW",
        "TRANSLATING, IN_CALL, TRANSLATING"
    })
    void of_rowThatFellBack_isAloneOnlyOnceItHasADraft(final RunRow run, final Stage stage, final RowMarker expected) {
        assertThat(RowMarker.of(row(stage, "MISSING"), run)).isEqualTo(expected);
    }

    // IF a marker took another colour than the rest of the app gives its meaning, THEN a person would read it wrongly.
    @ParameterizedTest
    @CsvSource({"WAITING, WAITING", "TRANSLATING, WORKING", "TRANSLATED, DONE", "TO_REVIEW, ATTENTION", "KEPT, KEPT"})
    void meaning_eachMarker_isTheStateColourItIsKnownBy(final RowMarker marker, final StateMarker.Meaning expected) {
        assertThat(marker.meaning()).isEqualTo(expected);
    }
}
