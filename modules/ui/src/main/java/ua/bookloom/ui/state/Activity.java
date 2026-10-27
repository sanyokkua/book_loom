package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * One activity as the title bar and its progress dialog show it.
 *
 * @param id the registration's own number, unique for the application's life
 * @param kind what runs
 * @param requests how many model requests it has sent so far, zero when it does not count them
 * @param cancellable whether the title bar may offer to stop it
 * @param title the card's heading; the kind's name unless the owner words it more closely
 * @param startedAt when it began, by the tracker's clock
 * @param progress how far it has got
 * @param cancelling whether it was asked to stop and has not ended yet
 * @param hidden whether its progress dialog was put in the background; only long work can be
 */
public record Activity(
        long id,
        ActivityKind kind,
        int requests,
        boolean cancellable,
        MessageKey title,
        Instant startedAt,
        Progress progress,
        boolean cancelling,
        boolean hidden) {

    /** Rejects a missing part. */
    public Activity {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(progress, "progress");
    }

    /**
     * Whether the window waits for it.
     *
     * @return {@code true} when its kind blocks
     */
    public boolean isBlocking() {
        return kind.isBlocking();
    }

    /**
     * Whether its progress dialog is in the background, behind the title bar's chip.
     *
     * @return {@code true} while hidden, {@code false} otherwise
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * How far it has got: the share of its announced steps when it announces any, else what its owner said.
     *
     * @return the fraction, or {@code null} when not known
     */
    public @Nullable Double fraction() {
        return progress.steps().isEmpty()
                ? progress.fraction()
                : Double.valueOf(progress.steps().reached());
    }

    /**
     * The steps it announced.
     *
     * @return the log; {@link StepLog#EMPTY} for work that announces none
     */
    public StepLog steps() {
        return progress.steps();
    }

    /**
     * What it is doing now.
     *
     * @return the worded step, or {@code null} for none
     */
    public @Nullable String stepText() {
        return progress.stepText();
    }

    /**
     * Its facts.
     *
     * @return the lines in the order shown; never null
     */
    public List<Detail> details() {
        return progress.details();
    }

    /**
     * The model calls it is making.
     *
     * @return the calls as the live call view shows them; {@link LiveCalls#EMPTY} when it shows none
     */
    public LiveCalls calls() {
        return progress.calls();
    }

    /**
     * The decided rows and the offer the work holds.
     *
     * @return the outcome; {@link ActivityOutcome#NONE} for work with none
     */
    public ActivityOutcome outcome() {
        return progress.outcome();
    }

    /**
     * The time left.
     *
     * @return the estimate, or {@code null} when not known
     */
    public @Nullable Duration eta() {
        return progress.eta();
    }

    Activity withRequests(final int count) {
        return new Activity(id, kind, count, cancellable, title, startedAt, progress, cancelling, hidden);
    }

    Activity withTitle(final MessageKey heading) {
        return new Activity(id, kind, requests, cancellable, heading, startedAt, progress, cancelling, hidden);
    }

    Activity withProgress(final Progress now) {
        return new Activity(id, kind, requests, cancellable, title, startedAt, now, cancelling, hidden);
    }

    Activity asCancelling() {
        return new Activity(id, kind, requests, cancellable, title, startedAt, progress, true, hidden);
    }

    Activity withHidden(final boolean now) {
        return new Activity(id, kind, requests, cancellable, title, startedAt, progress, cancelling, now);
    }

    /**
     * One line of an activity's detail block: "Requests 3", "Attempt 1 of 2".
     *
     * @param label what the value is, already worded for the person
     * @param value the value, already formatted
     */
    public record Detail(String label, String value) {

        /** Rejects a missing label or value. */
        public Detail {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * How far an activity has got, as its progress dialog shows it.
     *
     * @param fraction between 0 and 1, or {@code null} when that is not known
     * @param stepText what it is doing now, already worded, or {@code null} for none
     * @param details its facts in the order they are shown; never null
     * @param eta the time left by the average of the units finished so far, or {@code null} when not known
     * @param calls the model calls the work is making, as the live call view shows them; {@link LiveCalls#EMPTY}
     *     for work that shows none
     * @param steps the steps the work announced; {@link StepLog#EMPTY} for work that announces none
     * @param outcome the decided rows and the held offer; {@link ActivityOutcome#NONE} for work with none
     */
    public record Progress(
            @Nullable Double fraction,
            @Nullable String stepText,
            List<Detail> details,
            @Nullable Duration eta,
            LiveCalls calls,
            StepLog steps,
            ActivityOutcome outcome) {

        /** Freezes the details and rejects missing calls, steps or outcome. */
        public Progress {
            details = List.copyOf(details);
            Objects.requireNonNull(calls, "calls");
            Objects.requireNonNull(steps, "steps");
            Objects.requireNonNull(outcome, "outcome");
        }

        /** A progress that has no outcome yet. */
        public Progress(
                final @Nullable Double fraction,
                final @Nullable String stepText,
                final List<Detail> details,
                final @Nullable Duration eta,
                final LiveCalls calls,
                final StepLog steps) {
            this(fraction, stepText, details, eta, calls, steps, ActivityOutcome.NONE);
        }

        /** What an activity shows before it says anything: indeterminate, no step, no facts. */
        static final Progress NONE = new Progress(null, null, List.of(), null, LiveCalls.EMPTY, StepLog.EMPTY);

        Progress withFigures(final @Nullable Double now, final @Nullable String text, final @Nullable Duration left) {
            return new Progress(now, text, details, left, calls, steps, outcome);
        }

        Progress withDetails(final List<Detail> lines) {
            return new Progress(fraction, stepText, lines, eta, calls, steps, outcome);
        }

        Progress withCalls(final LiveCalls now) {
            return new Progress(fraction, stepText, details, eta, now, steps, outcome);
        }

        Progress withSteps(final StepLog now) {
            return new Progress(fraction, stepText, details, eta, calls, now, outcome);
        }

        Progress withOutcome(final ActivityOutcome now) {
            return new Progress(fraction, stepText, details, eta, calls, steps, now);
        }
    }
}
