package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * One activity as the title bar and the busy card show it.
 *
 * @param id the registration's own number, unique for the application's life
 * @param kind what runs
 * @param requests how many model requests it has sent so far, zero when it does not count them
 * @param cancellable whether the title bar may offer to stop it
 * @param title the card's heading; the kind's name unless the owner words it more closely
 * @param startedAt when it began, by the tracker's clock
 * @param progress how far it has got
 * @param cancelling whether it was asked to stop and has not ended yet
 */
public record Activity(
        long id,
        ActivityKind kind,
        int requests,
        boolean cancellable,
        MessageKey title,
        Instant startedAt,
        Progress progress,
        boolean cancelling) {

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
     * How far it has got.
     *
     * @return the fraction, or {@code null} when not known
     */
    public @Nullable Double fraction() {
        return progress.fraction();
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
     * The time left.
     *
     * @return the estimate, or {@code null} when not known
     */
    public @Nullable Duration eta() {
        return progress.eta();
    }

    Activity withRequests(final int count) {
        return new Activity(id, kind, count, cancellable, title, startedAt, progress, cancelling);
    }

    Activity withTitle(final MessageKey heading) {
        return new Activity(id, kind, requests, cancellable, heading, startedAt, progress, cancelling);
    }

    Activity withProgress(final Progress now) {
        return new Activity(id, kind, requests, cancellable, title, startedAt, now, cancelling);
    }

    Activity asCancelling() {
        return new Activity(id, kind, requests, cancellable, title, startedAt, progress, true);
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
     * How far an activity has got, as the busy card shows it.
     *
     * @param fraction between 0 and 1, or {@code null} when that is not known
     * @param stepText what it is doing now, already worded, or {@code null} for none
     * @param details its facts in the order they are shown; never null
     * @param eta the time left by the average of the units finished so far, or {@code null} when not known
     */
    public record Progress(
            @Nullable Double fraction,
            @Nullable String stepText,
            List<Detail> details,
            @Nullable Duration eta) {

        /** Freezes the details. */
        public Progress {
            details = List.copyOf(details);
        }

        /** What an activity shows before it says anything: indeterminate, no step, no facts. */
        static final Progress NONE = new Progress(null, null, List.of(), null);
    }
}
