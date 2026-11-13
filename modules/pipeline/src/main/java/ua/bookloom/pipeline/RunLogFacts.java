package ua.bookloom.pipeline;

import java.util.Objects;
import ua.bookloom.api.llm.CallLedger;

/**
 * What one job's log lines are built from.
 *
 * @param ledger the ledger the run's summary counts its calls from
 * @param providerKind the kind of server the model runs on, or {@code unknown}; the start line names it
 */
record RunLogFacts(CallLedger ledger, String providerKind) {

    RunLogFacts {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(providerKind, "providerKind");
    }
}
