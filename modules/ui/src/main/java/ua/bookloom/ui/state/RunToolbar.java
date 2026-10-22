package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.RunStateView.RunAction;
import ua.bookloom.ui.state.RunStateView.Text;

/**
 * The toolbar's run actions and the title bar's one run control of each run-state row (README "Run states", L2).
 *
 * <p>Whether an action can be pressed comes from {@link Controls}, the one command gate the view model also refuses
 * by, so a button never offers what a press would refuse. The title bar's control is the toolbar's Pause or Resume of
 * the same row, so both read and act the same. Pure; logs nothing.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunToolbar {

    static List<RunAction> of(final RunRow row, final RunStateInputs in) {
        return switch (row) {
            case READY, CANNOT_START ->
                List.of(start(MessageKey.TRANSLATING_START, MessageKey.TRANSLATING_START_TIP, in));
            case READY_TO_CONTINUE -> List.of(continueAsNew(in), review(in));
            case TRANSLATING, STUCK -> List.of(pause(in), stop(in), review(in));
            case PAUSING, STOPPING -> List.of(pause(in), stop(in));
            case PAUSED ->
                List.of(
                        resume(MessageKey.TRANSLATING_RESUME, MessageKey.TRANSLATING_RESUME_TIP, in),
                        stop(in),
                        review(in));
            case WAITING -> List.of(pauseTheWait(in), stop(in));
            case PAUSED_AFTER_ERROR ->
                List.of(resume(MessageKey.TRANSLATING_RESUME, MessageKey.TRANSLATING_RESUME_TIP, in), stop(in));
            case STOPPED_TRYING, MODEL_MISSING ->
                List.of(resume(MessageKey.RUN_ACTION_TRY_AGAIN, MessageKey.RUN_ACTION_TRY_AGAIN_TIP, in), stop(in));
            case STOPPED ->
                List.of(resume(MessageKey.RUN_ACTION_CONTINUE, MessageKey.RUN_ACTION_CONTINUE_TIP, in), review(in));
            case FINISHED_TO_REVIEW, FINISHED_NONE -> finished(in);
            case FAILED -> List.of(continueAsNew(in), review(in));
        };
    }

    static @Nullable RunAction titleControl(final RunRow row, final RunStateInputs in) {
        return switch (row) {
            case TRANSLATING, STUCK, PAUSING -> pause(in);
            case WAITING -> pauseTheWait(in);
            case PAUSED, PAUSED_AFTER_ERROR ->
                resume(MessageKey.TRANSLATING_RESUME, MessageKey.TRANSLATING_RESUME_TIP, in);
            case STOPPED_TRYING, MODEL_MISSING ->
                resume(MessageKey.RUN_ACTION_TRY_AGAIN, MessageKey.RUN_ACTION_TRY_AGAIN_TIP, in);
            case STOPPING, STOPPED -> resume(MessageKey.RUN_ACTION_CONTINUE, MessageKey.RUN_ACTION_CONTINUE_TIP, in);
            case READY, READY_TO_CONTINUE, CANNOT_START, FINISHED_TO_REVIEW, FINISHED_NONE, FAILED -> null;
        };
    }

    // A completed run that left segments undecided still offers to continue them; a reopened finished one has none.
    private static List<RunAction> finished(final RunStateInputs in) {
        final List<RunAction> actions = new ArrayList<>();
        if (in.state() == RunState.COMPLETED && in.controls().start().isShown()) {
            actions.add(secondary(continueAsNew(in)));
        }
        // With nothing to review the desk does not open by itself, but Review stays, so a clean Unattended run can be
        // spot-checked through All segments.
        actions.add(review(in));
        actions.add(new RunAction(
                RunStateView.TO_EXPORT,
                Text.of(MessageKey.TRANSLATING_CONTINUE),
                true,
                true,
                Text.of(MessageKey.TRANSLATING_CONTINUE_TIP)));
        return List.copyOf(actions);
    }

    private static RunAction secondary(final RunAction action) {
        return new RunAction(action.id(), action.label(), false, action.enabled(), action.tip());
    }

    private static RunAction continueAsNew(final RunStateInputs in) {
        return start(MessageKey.RUN_ACTION_CONTINUE, MessageKey.RUN_ACTION_CONTINUE_TIP, in);
    }

    // No model chosen is the one input the row knows is missing before a press; the tooltip names it.
    private static RunAction start(final MessageKey label, final MessageKey tip, final RunStateInputs in) {
        final boolean offered = in.controls().start().isEnabled();
        final boolean noTarget = in.notice() instanceof RunNotice.MissingInput missing
                && missing.which() == RunNotice.Input.TARGET_LANGUAGE;
        final boolean enabled = offered && in.model().chosen() && !noTarget;
        final ActivityKind blocker = in.otherWork();
        final Text why;
        if (!offered && blocker != null) {
            why = BlockedWords.why(blocker, ActivityKind.TRANSLATION);
        } else if (noTarget) {
            why = Text.of(MessageKey.TRANSLATING_MISSING_TEXT, RunNotice.Input.TARGET_LANGUAGE.token());
        } else if (!in.model().chosen()) {
            why = Text.of(MessageKey.TRANSLATING_MISSING_TEXT, RunNotice.Input.MODEL.token());
        } else {
            why = Text.of(tip);
        }
        return new RunAction(RunStateView.START, Text.of(label), true, enabled, why);
    }

    private static RunAction resume(final MessageKey label, final MessageKey tip, final RunStateInputs in) {
        final boolean enabled = in.controls().resume().isEnabled();
        final ActivityKind blocker = in.otherWork();
        final Text why =
                !enabled && blocker != null ? BlockedWords.why(blocker, ActivityKind.TRANSLATION) : Text.of(tip);
        return new RunAction(RunStateView.RESUME, Text.of(label), true, enabled, why);
    }

    private static RunAction pause(final RunStateInputs in) {
        return new RunAction(
                RunStateView.PAUSE,
                Text.of(MessageKey.TRANSLATING_PAUSE),
                true,
                in.controls().pause().isEnabled(),
                Text.of(MessageKey.TRANSLATING_PAUSE_TIP));
    }

    private static RunAction pauseTheWait(final RunStateInputs in) {
        return new RunAction(
                RunStateView.PAUSE,
                Text.of(MessageKey.RUN_ACTION_PAUSE_WAIT),
                false,
                in.controls().pause().isEnabled(),
                Text.of(MessageKey.RUN_ACTION_PAUSE_WAIT_TIP));
    }

    private static RunAction stop(final RunStateInputs in) {
        return new RunAction(
                RunStateView.STOP,
                Text.of(MessageKey.TRANSLATING_STOP),
                false,
                in.controls().stop().isEnabled(),
                Text.of(MessageKey.TRANSLATING_STOP_TIP));
    }

    private static RunAction review(final RunStateInputs in) {
        return new RunAction(
                RunStateView.REVIEW,
                Text.of(MessageKey.RUN_ACTION_REVIEW, in.toReview()),
                false,
                true,
                Text.of(MessageKey.RUN_ACTION_REVIEW_TIP));
    }
}
