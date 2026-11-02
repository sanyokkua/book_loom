package ua.bookloom.llm;

import java.util.Objects;
import ua.bookloom.api.llm.CallLedger;

/**
 * What watches the calls of one model: the record of every attempt, and the once-per-model lines for a BookLoom fault
 * and for a server that ignored the answer cap.
 *
 * @param ledger keeps a record of every attempt that has a call kind
 * @param builtWrong writes a BookLoom fault's ERROR line once per model
 * @param capIgnored writes a server's ignored answer cap's WARN once per model
 */
record CallObservers(CallLedger ledger, BuiltWrongLog builtWrong, CapIgnoredLog capIgnored) {

    CallObservers {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(builtWrong, "builtWrong");
        Objects.requireNonNull(capIgnored, "capIgnored");
    }

    /** Observers that keep nothing. */
    static CallObservers none() {
        return new CallObservers(CallLedger.none(), new BuiltWrongLog(), new CapIgnoredLog());
    }
}
