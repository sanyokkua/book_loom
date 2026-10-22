package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.llm.ModelSelection;

/** Where the look at the two local AI programs stands, for the first-launch card to word. */
public sealed interface ProbeOutcome {

    /** No look has been made this session, or the first-launch look was not due. */
    record Idle() implements ProbeOutcome {}

    /** A look is under way. */
    record Looking() implements ProbeOutcome {}

    /**
     * A program answered with a model and it is now the chosen one.
     *
     * @param selection the program and model chosen
     * @param atFirstLaunch {@code true} when the first-launch look found it, {@code false} when the person's "Try
     *     again" did
     */
    record Found(ModelSelection selection, boolean atFirstLaunch) implements ProbeOutcome {

        /** Rejects a missing selection. */
        public Found {
            Objects.requireNonNull(selection, "selection");
        }
    }

    /** Several models were found and the person is being asked which to use. */
    record Asking() implements ProbeOutcome {}

    /** Nothing usable answered, or the person declined to pick. */
    record NotFound() implements ProbeOutcome {}
}
