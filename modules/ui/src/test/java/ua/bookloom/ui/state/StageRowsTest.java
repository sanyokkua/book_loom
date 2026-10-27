package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.PassEvents;

/** How a log of a names pass folds into one row per stage with the requests each sent. */
class StageRowsTest {

    private static List<String> labels(final StageRows rows) {
        return rows.rows().stream().map(StepEntry::label).toList();
    }

    private static List<Integer> counts(final StageRows rows) {
        return rows.rows().stream().map(StepEntry::requests).toList();
    }

    // IF a request were counted under the wrong stage, THEN the dialog would blame the wrong step for the time.
    @Test
    void of_passAtFifthStage_countsEachStagesRequestsAndListsEightRows() {
        final StageRows rows = StageRows.of(PassEvents.logUpTo(new int[] {0, 6, 0, 3, 25}, 4, 49));

        assertThat(counts(rows)).containsExactly(0, 6, 0, 3, 25, 0, 0, 0);
        assertThat(labels(rows)).containsExactlyElementsOf(PassEvents.CODES);
        assertThat(rows.rows().get(5).isPending()).isTrue();
    }

    // IF the last request were not kept, THEN a stage's detail would show nothing of the call it made.
    @Test
    void of_stageWithRequests_namesItsLastRequest() {
        final StageRows rows = StageRows.of(PassEvents.logUpTo(new int[] {0, 6}, 1, 49));

        assertThat(rows.lastRequest()).containsEntry("STAGE:1", "REQUEST:5").doesNotContainKey("STAGE:0");
    }

    // IF a log with no stage produced rows, THEN the brief's request steps would be hidden behind an empty list.
    @Test
    void of_noStagePlanned_hasNoRows() {
        final StepLog log = StepLog.EMPTY
                .apply(new StepsPlanned(StepKind.REQUEST, 1, false))
                .apply(new StepChanged(StepKind.REQUEST, "knowledge", 0, StepState.STARTED, null, null));

        assertThat(StageRows.of(log).rows()).isEmpty();
    }

    // IF the planned second step had no name, THEN an action's dialog would list "Step 2" before the write.
    @Test
    void of_twoStagesPlannedOneStarted_namesThePendingOneAsTheWrite() {
        final StepLog log = StepLog.EMPTY
                .apply(new StepsPlanned(StepKind.STAGE, 2, false))
                .apply(new StepChanged(StepKind.STAGE, "Name scan", 0, StepState.STARTED, null, null));

        assertThat(labels(StageRows.of(log))).containsExactly("Name scan", "write");
    }

    // IF a request announced before any stage were dropped, THEN its count would vanish.
    @Test
    void of_requestBeforeAnyStage_belongsToTheFirstStage() {
        final StepLog log = StepLog.EMPTY
                .apply(new StepsPlanned(StepKind.STAGE, 2, false))
                .apply(new StepChanged(StepKind.REQUEST, "r", 0, StepState.STARTED, null, null))
                .apply(new StepChanged(StepKind.STAGE, "Name scan", 0, StepState.STARTED, null, null));

        assertThat(counts(StageRows.of(log))).containsExactly(1, 0);
    }
}
