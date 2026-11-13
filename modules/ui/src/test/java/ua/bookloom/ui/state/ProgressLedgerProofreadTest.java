package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadForecast;
import ua.bookloom.api.pipeline.StepKind;

/**
 * The Proofread stage's forecast in the ledger (design D15 "Estimate"): replaced by each round's exact plan, and
 * labelled an estimate until one of its steps is timed.
 */
class ProgressLedgerProofreadTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");

    private final ProgressLedger ledger = new ProgressLedger(new RunClock(T0));
    private final Instant now = T0;

    // IF a round's exact plan were added to its forecast, THEN the windows would be counted twice.
    @Test
    void proofreadRound_planned_replacesItsForecast() {
        ledger.proofread()
                .forecast(new ProofreadForecast(ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH, 750));
        ledger.proofread().marked();
        ledger.proofread().marked();

        ledger.proofread().roundStarted(1);
        ledger.planned(StepKind.PROOFREAD, 1);
        final int afterRoundOne = ledger.read(now).total();
        ledger.proofread().roundStarted(2);
        ledger.planned(StepKind.PROOFREAD, 740);

        assertThat(afterRoundOne).isEqualTo(751);
        assertThat(ledger.read(now).total()).isEqualTo(741);
        assertThat(ProgressLedgerTest.proofreadOf(ledger.read(now)).forecast()).isFalse();
    }

    // IF a forecast proofread kind read like a measured one, THEN the note would promise a count the run has not made.
    @Test
    void proofreadForecast_untimed_isMarkedAnEstimate() {
        ledger.proofread()
                .forecast(new ProofreadForecast(ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH, 4));

        assertThat(ProgressLedgerTest.proofreadOf(ledger.read(now)).forecast()).isTrue();
        assertThat(ProgressLedgerTest.proofreadOf(ledger.read(now)).secondsPerStep())
                .isNull();
    }
}
