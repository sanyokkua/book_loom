package ua.bookloom.llm;

import java.util.Objects;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.llm.profile.ObservedReasoning;

/**
 * What watches the calls of one model: the record of every attempt, and the once-per-model lines for a BookLoom fault
 * and for a server that ignored the answer cap.
 *
 * @param ledger keeps a record of every attempt that has a call kind
 * @param builtWrong writes a BookLoom fault's ERROR line once per model
 * @param capIgnored writes a server's ignored answer cap's WARN once per model
 * @param thinking remembers the models that spent their answer cap thinking
 */
record CallObservers(
        CallLedger ledger, BuiltWrongLog builtWrong, CapIgnoredLog capIgnored, ObservedReasoning thinking) {

    /** Observers whose thinking memory is their own, for a model built without the factory. */
    CallObservers(CallLedger ledger, BuiltWrongLog builtWrong, CapIgnoredLog capIgnored) {
        this(ledger, builtWrong, capIgnored, new ObservedReasoning());
    }

    CallObservers {
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(builtWrong, "builtWrong");
        Objects.requireNonNull(capIgnored, "capIgnored");
        Objects.requireNonNull(thinking, "thinking");
    }

    /** Observers that keep nothing. */
    static CallObservers none() {
        return new CallObservers(CallLedger.none(), new BuiltWrongLog(), new CapIgnoredLog(), new ObservedReasoning());
    }
}
