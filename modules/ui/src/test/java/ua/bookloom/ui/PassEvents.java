package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.state.StepLog;

/**
 * The events a names pass announces, built by hand: eight stage rows and one request step per call labelled with the
 * stage that sent it, request indexes counted across the whole pass.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PassEvents {

    /** The stage codes of a names pass, in order. */
    public static final List<String> CODES = List.of(
            "scan", "names", "terms-scan", "terms-review", "terms-read", "names-translate", "terms-translate", "write");

    /**
     * The events of a pass that has finished the stages before {@code running} and sits in stage {@code running}.
     *
     * @param requests the requests each stage sent and had answered, one number per stage up to and including the
     *     running one; a finished stage with none ends with no call
     * @param running the index of the running stage, or the number of stages when every one has ended
     * @param planned the pass-wide request estimate announced up front
     * @return the events in the order the pass announced them
     */
    public static List<JobEvent> upTo(final int[] requests, final int running, final int planned) {
        final List<JobEvent> events = new ArrayList<>();
        events.add(new StepsPlanned(StepKind.STAGE, CODES.size(), false));
        events.add(new StepsPlanned(StepKind.REQUEST, planned, true));
        int request = 0;
        for (int stage = 0; stage < Math.min(running + 1, CODES.size()); stage++) {
            final String code = CODES.get(stage);
            events.add(change(StepKind.STAGE, code, stage, StepState.STARTED, null));
            for (int n = 0; n < requests[stage]; n++) {
                events.add(change(StepKind.REQUEST, code, request, StepState.STARTED, null));
                events.add(change(StepKind.REQUEST, code, request, StepState.ENDED, StepResult.DONE));
                request++;
            }
            if (stage < running) {
                final StepResult result = requests[stage] == 0 ? StepResult.NO_CALL : StepResult.DONE;
                events.add(change(StepKind.STAGE, code, stage, StepState.ENDED, result));
            }
        }
        return events;
    }

    /**
     * The same pass folded into the log a dialog reads.
     *
     * @param requests see {@link #upTo}
     * @param running see {@link #upTo}
     * @param planned see {@link #upTo}
     * @return the log
     */
    public static StepLog logUpTo(final int[] requests, final int running, final int planned) {
        StepLog log = StepLog.EMPTY;
        for (final JobEvent event : upTo(requests, running, planned)) {
            log = log.apply(event);
        }
        return log;
    }

    private static StepChanged change(
            final StepKind kind,
            final String label,
            final int index,
            final StepState state,
            final @Nullable StepResult result) {
        return new StepChanged(kind, label, index, state, null, result);
    }
}
