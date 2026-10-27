package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.state.ProgressFigures.Figures;

/** A progress dialog's reader: it sees only the log of steps, and still measures the time between its reads. */
class LedgerFiguresTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");

    private StepLog log = StepLog.EMPTY.apply(new StepsPlanned(StepKind.REQUEST, 49, false));
    private final LedgerFigures figures = new LedgerFigures();

    private void finish(final int from, final int to) {
        for (int index = from; index < to; index++) {
            log = log.apply(new StepChanged(StepKind.REQUEST, "r", index, StepState.STARTED, null, null));
            log = log.apply(new StepChanged(StepKind.REQUEST, "r", index, StepState.ENDED, null, StepResult.DONE));
        }
    }

    // IF a dialog with no steps announced showed a share, THEN it would read 0 of 0 as a figure.
    @Test
    void of_noStepsAnnounced_isNone() {
        assertThat(figures.of(StepLog.EMPTY, T0)).isEqualTo(Figures.NONE);
    }

    // IF the dialog's time left did not come from the time between its reads, THEN names and brief work would show
    // none.
    @Test
    void of_twentySixOf49StepsEveryTwoSeconds_countsTheStepsAndEstimatesTheRest() {
        figures.of(log, T0);
        finish(0, 26);

        final Figures read = figures.of(log, T0.plusSeconds(52));

        assertThat(read.done()).isEqualTo(26);
        assertThat(read.total()).isEqualTo(49);
        assertThat(read.percent()).isEqualTo(53);
        // 23 left at 2 s each is 46 s, which reads as one minute.
        assertThat(read.left()).isEqualTo(Duration.ofMinutes(1));
        assertThat(read.line(new ua.bookloom.ui.i18n.Messages(() -> java.util.Locale.ENGLISH)))
                .isEqualTo("53 % · 26 of about 49 steps · Left about 1 min");
    }

    // IF the stage rows were timed, THEN a names pass would promise a time from stages that differ by hours.
    @Test
    void of_stageCountedWork_showsNoTimeLeft() {
        log = StepLog.EMPTY.apply(new StepsPlanned(StepKind.STAGE, 8, false));
        figures.of(log, T0);
        log = log.apply(new StepChanged(StepKind.STAGE, "s", 0, StepState.STARTED, null, null));
        log = log.apply(new StepChanged(StepKind.STAGE, "s", 0, StepState.ENDED, null, StepResult.DONE));

        final Figures read = figures.of(log, T0.plusSeconds(30));

        assertThat(read.done()).isEqualTo(1);
        assertThat(read.left()).isNull();
    }
}
