package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadForecast;

/**
 * The Proofread steps a run is expected to take before the stage plans them (design D15 "Estimate"): none at Fast, one
 * window per mark seen so far at Balanced and Max, and at Max the whole-book round's windows from Start. A round that
 * starts plans its own windows exactly, so its forecast is dropped then. Not thread-safe: the ledger holding it is used
 * under the run session's publish lock.
 */
@Slf4j
final class ProofreadPlan {

    private ProofreadCoverage coverage = ProofreadCoverage.NONE;
    private boolean marking = true;
    private int marks;
    private int wholeBook;

    void forecast(final ProofreadForecast forecast) {
        Objects.requireNonNull(forecast, "forecast");
        coverage = forecast.coverage();
        wholeBook = forecast.wholeBookWindows();
        log.debug("proofread forecast: coverage {}, {} whole-book windows", coverage, wholeBook);
    }

    // Marks come from decided segments; once round 1 has planned its windows they are counted exactly.
    void marked() {
        if (coverage != ProofreadCoverage.NONE && marking) {
            marks++;
        }
    }

    void roundStarted(final int round) {
        log.debug(
                "proofread round {} planned: its forecast of {} steps is dropped",
                round,
                round == 1 ? marks : wholeBook);
        if (round == 1) {
            marks = 0;
            marking = false;
        } else {
            wholeBook = 0;
        }
    }

    /** {@return the forecast steps not yet planned by a round; zero or more} */
    int steps() {
        return marks + wholeBook;
    }
}
