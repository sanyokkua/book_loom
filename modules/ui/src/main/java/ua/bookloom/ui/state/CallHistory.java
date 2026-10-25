package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The model of the "All calls…" dialog: one request step per call of the run's history, each holding its call, so the
 * progress dialog's Calls view lists them and opens one as it opens a step of model work.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallHistory {

    /**
     * Builds the dialog's model from the calls the live card kept.
     *
     * @param oldestFirst the calls, oldest first; never more than the history keeps
     * @param now the moment the dialog opens
     * @param messages the catalogue each row is worded from
     * @return a model with one step per call, named by its kind in words, in the order given
     */
    public static ProgressDialogModel modelOf(
            final List<CallSnapshot> oldestFirst, final Instant now, final Messages messages) {
        Objects.requireNonNull(oldestFirst, "oldestFirst");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(messages, "messages");
        log.debug("all calls: building the dialog's model over {} calls", oldestFirst.size());
        StepLog steps = StepLog.EMPTY.apply(new StepsPlanned(StepKind.REQUEST, oldestFirst.size(), false));
        final ProgressDialogModel model = new ProgressDialogModel(
                activity(steps, null, now), new LedgerFigures(), ProgressDialogModel.Mode.SIMPLE, () -> now);
        for (int index = 0; index < oldestFirst.size(); index++) {
            final CallSnapshot call = oldestFirst.get(index);
            final String name = rowName(call, messages);
            steps = steps.apply(new StepChanged(StepKind.REQUEST, name, index, StepState.STARTED, null, null));
            // Read while the call's step runs, so the model keeps the call with that step.
            model.accept(activity(steps, call, now));
            steps = steps.apply(new StepChanged(StepKind.REQUEST, name, index, StepState.ENDED, null, StepResult.DONE));
        }
        model.accept(activity(steps, null, now));
        return model;
    }

    // A call's own label is the pipeline's prompt name (draft-batch-json); the row says what the call did in words.
    private static String rowName(final CallSnapshot call, final Messages messages) {
        return messages.get(MessageKey.RUN_CALL_KIND, WaitingCall.tokenOf(call.kind()));
    }

    private static Activity activity(final StepLog steps, final @Nullable CallSnapshot call, final Instant now) {
        final LiveCalls calls = call == null ? LiveCalls.EMPTY : new LiveCalls(call, null, Map.of(), null);
        return new Activity(
                0,
                ActivityKind.TRANSLATION,
                0,
                false,
                MessageKey.ACTIVITY_TRANSLATION,
                now,
                new Activity.Progress(null, null, List.of(), null, calls, steps),
                false,
                false);
    }
}
