package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.KeepAwake;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * Every model or provider activity under way, so that one that would compete with another for the model is not
 * offered, the title bar can name what runs and stop it, and leaving a screen whose work would be lost can ask first.
 *
 * <p>The tracker refuses nothing itself: an owner asks {@link #conflictFor} before it starts, registers with
 * {@link #begin} and ends its {@link Handle} however the work ends — ending twice is harmless. Which kinds conflict is
 * {@link ActivityKind#conflictsWith}. The translation run is registered here from the mirror's run state: it counts
 * while the run translates (running, pausing, stopping) or waits for the provider by itself, not while it waits
 * paused for the person, stopped or ended. FX thread only.
 */
@Slf4j
@Singleton
public final class ActivityTracker {

    private final ObservableList<Activity> running = FXCollections.observableArrayList();
    private final ObservableList<Activity> runningView = FXCollections.unmodifiableObservableList(running);
    private final Map<Long, Runnable> cancels = new HashMap<>();
    private long lastId;
    private @Nullable Handle translation;
    private final KeepAwake keepAwake;
    private final Clock clock;
    private final BooleanBinding blocking =
            Bindings.createBooleanBinding(() -> running.stream().anyMatch(Activity::isBlocking), running);
    private final ObjectBinding<@Nullable Activity> blockingActivity = Bindings.createObjectBinding(
            () -> running.stream().filter(Activity::isBlocking).findFirst().orElse(null), running);

    /**
     * Creates a tracker that follows the run and holds nothing awake.
     *
     * @param mirror where the run's state is read; a translating run is registered as {@link ActivityKind#TRANSLATION}
     */
    public ActivityTracker(final StateMirror mirror) {
        this(mirror, new KeepAwake.Off());
    }

    /**
     * Creates a tracker that follows the run and keeps the computer awake for as long as the run is registered.
     *
     * @param mirror where the run's state is read; a translating run is registered as {@link ActivityKind#TRANSLATION}
     * @param keepAwake what holds the computer awake while the translation is registered
     */
    @Inject
    public ActivityTracker(final StateMirror mirror, final KeepAwake keepAwake) {
        this(mirror, keepAwake, Clock.systemUTC());
    }

    /**
     * Creates a tracker on a given clock, so a test can move the time that elapsed time and the ETA are read from.
     *
     * @param mirror where the run's state is read
     * @param keepAwake what holds the computer awake while the translation is registered
     * @param clock where an activity's start and its elapsed time are read
     */
    public ActivityTracker(final StateMirror mirror, final KeepAwake keepAwake, final Clock clock) {
        Objects.requireNonNull(mirror, "mirror");
        this.keepAwake = Objects.requireNonNull(keepAwake, "keepAwake");
        this.clock = Objects.requireNonNull(clock, "clock");
        mirror.runState().addListener((observed, was, now) -> followRun(isTranslating(mirror)));
        // The countdown republishes the recovery every second; only a change of whether it waits matters here.
        mirror.review().recovery().addListener((observed, was, now) -> {
            if (RecoveryState.waits(was) != RecoveryState.waits(now)) {
                followRun(isTranslating(mirror));
            }
        });
        followRun(isTranslating(mirror));
        log.debug("activity tracker ready");
    }

    /**
     * Whether the run is at work: translating, or paused on a provider error that it recovers from by itself, which is
     * still the run's own activity and keeps the machine awake.
     *
     * @param mirror where the run's state and recovery are read; on the FX thread
     * @return {@code true} while the run translates or waits for the provider by itself, {@code false} otherwise
     */
    public static boolean isTranslating(final StateMirror mirror) {
        final RecoveryState recovery = mirror.review().recovery().get();
        return switch (mirror.runState().get()) {
            case RUNNING, PAUSING, STOPPING -> true;
            case PAUSED -> RecoveryState.waits(recovery);
            case IDLE, STOPPED, COMPLETED, FAILED -> false;
        };
    }

    private void followRun(final boolean translating) {
        final Handle registered = translation;
        if (translating && registered == null) {
            translation = begin(ActivityKind.TRANSLATION, null);
            keepAwake.start();
        } else if (!translating && registered != null) {
            registered.end();
            translation = null;
            keepAwake.stop();
        } else {
            log.debug("the translation activity stays {}", translating ? "registered" : "absent");
        }
    }

    /**
     * The activities under way, oldest first.
     *
     * @return a read-only list; never null
     */
    public ObservableList<Activity> running() {
        return runningView;
    }

    /**
     * Registers an activity that has started.
     *
     * @param kind what started
     * @param cancel what stops it, or {@code null} when it cannot be stopped from outside; it must end the handle (or
     *     let the work end it) once the work has stopped
     * @return the handle the owner ends when the work ends
     */
    public Handle begin(final ActivityKind kind, final @Nullable Runnable cancel) {
        Objects.requireNonNull(kind, "kind");
        final long id = ++lastId;
        if (cancel != null) {
            cancels.put(id, cancel);
        }
        running.add(new Activity(
                id, kind, 0, cancel != null, kind.label(), clock.instant(), Activity.Progress.NONE, false));
        log.info("activity {} #{} started; running now {}", kind, id, kinds());
        return new Handle(id, kind);
    }

    /**
     * Whether some running activity makes the window wait; navigation, the footer buttons and the navigation column
     * follow it.
     *
     * @return a binding that is {@code true} while a blocking activity runs
     */
    public BooleanBinding blocking() {
        return blocking;
    }

    /**
     * The oldest running activity that makes the window wait, which the busy card describes.
     *
     * @return a binding holding it, or {@code null} while nothing blocks
     */
    public ObjectBinding<@Nullable Activity> blockingActivity() {
        return blockingActivity;
    }

    /**
     * The tracker's own time, so a card that shows elapsed time counts from the same clock the start was taken from.
     *
     * @return the current instant
     */
    public Instant now() {
        return clock.instant();
    }

    /**
     * The running activity that rules out starting {@code kind} now.
     *
     * @param kind the kind about to start
     * @return the kind of the oldest running activity it conflicts with, or empty when it may start
     */
    public Optional<ActivityKind> conflictFor(final ActivityKind kind) {
        Objects.requireNonNull(kind, "kind");
        final Optional<ActivityKind> found =
                running.stream().map(Activity::kind).filter(kind::conflictsWith).findFirst();
        log.debug("conflict check for {}: {}", kind, found.map(Enum::name).orElse("none"));
        return found;
    }

    /**
     * Follows what rules out starting {@code kind}, for a control to disable itself and say why.
     *
     * @param kind the kind a control would start
     * @return a binding holding the conflicting running kind, or {@code null} while {@code kind} may start
     */
    public ObjectBinding<@Nullable ActivityKind> blocker(final ActivityKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Bindings.createObjectBinding(
                () -> running.stream()
                        .map(Activity::kind)
                        .filter(kind::conflictsWith)
                        .findFirst()
                        .orElse(null),
                running);
    }

    /**
     * Asks every running activity that matches to stop; each ends its own handle once it has.
     *
     * @param which the kinds to stop
     */
    public void stopWhere(final Predicate<ActivityKind> which) {
        Objects.requireNonNull(which, "which");
        running.stream()
                .filter(activity -> which.test(activity.kind()))
                .toList()
                .forEach(this::stop);
    }

    /**
     * Asks one activity to stop; ignored when it has ended or cannot be stopped.
     *
     * @param id the activity's id
     */
    public void stop(final long id) {
        running.stream().filter(activity -> activity.id() == id).findFirst().ifPresent(this::stop);
    }

    private void stop(final Activity activity) {
        final Runnable cancel = cancels.get(activity.id());
        if (cancel == null) {
            log.debug("activity {} #{} cannot be stopped from outside", activity.kind(), activity.id());
            return;
        }
        if (activity.cancelling()) {
            log.debug("activity {} #{} is already being stopped", activity.kind(), activity.id());
            return;
        }
        replace(activity.id(), Activity::asCancelling);
        log.info("activity {} #{} asked to stop", activity.kind(), activity.id());
        cancel.run();
    }

    private void replace(final long id, final UnaryOperator<Activity> change) {
        for (int i = 0; i < running.size(); i++) {
            final Activity shown = running.get(i);
            if (shown.id() == id) {
                final Activity changed = change.apply(shown);
                if (!changed.equals(shown)) {
                    running.set(i, changed);
                }
            }
        }
    }

    private String kinds() {
        return running.stream().map(activity -> activity.kind().name()).toList().toString();
    }

    /** One registration: counts its requests and ends it, once. FX thread only. */
    public final class Handle {

        private final long id;
        private final ActivityKind kind;
        private boolean ended;

        private Handle(final long id, final ActivityKind kind) {
            this.id = id;
            this.kind = kind;
        }

        /**
         * The activity's id, which {@link ActivityTracker#stop(long)} takes.
         *
         * @return the id
         */
        public long id() {
            return id;
        }

        /**
         * Records how many model requests the activity has sent; ignored once it has ended.
         *
         * @param count the requests so far
         */
        public void requests(final int count) {
            update(shown -> shown.requests() == count ? shown : shown.withRequests(count));
        }

        /**
         * Words the card's heading more closely than the kind's name; ignored once ended.
         *
         * @param title the heading's catalogue key; non-null
         */
        public void title(final MessageKey title) {
            Objects.requireNonNull(title, "title");
            update(shown -> shown.withTitle(title));
        }

        /**
         * Says how far a counted step has got; the time left is the elapsed time per finished unit times the units
         * left. Ignored once ended.
         *
         * @param done the units finished, from 0
         * @param total the units in the step, at least 1 and at least {@code done}
         * @param stepText what the step is, already worded
         */
        public void progress(final int done, final int total, final String stepText) {
            Objects.requireNonNull(stepText, "stepText");
            final double fraction = total <= 0 ? 0 : Math.min(1.0, (double) done / total);
            update(shown -> {
                final Duration elapsed = Duration.between(shown.startedAt(), clock.instant());
                final Duration eta = done <= 0 || total <= done
                        ? null
                        : elapsed.dividedBy(done).multipliedBy(total - done);
                return shown.withProgress(
                        new Activity.Progress(fraction, stepText, shown.details(), eta, shown.calls()));
            });
        }

        /**
         * Says what is being done when how far it has got is not known; clears any fraction and time left.
         *
         * @param stepText what is being done, already worded; {@code null} for none
         */
        public void indeterminate(final @Nullable String stepText) {
            update(shown ->
                    shown.withProgress(new Activity.Progress(null, stepText, shown.details(), null, shown.calls())));
        }

        /**
         * Replaces the card's detail lines.
         *
         * @param lines the lines in the order they are shown; non-null
         */
        public void details(final List<Activity.Detail> lines) {
            Objects.requireNonNull(lines, "lines");
            update(shown -> shown.withProgress(
                    new Activity.Progress(shown.fraction(), shown.stepText(), lines, shown.eta(), shown.calls())));
        }

        /**
         * Replaces the model calls the card shows; ignored once ended.
         *
         * @param calls the calls as the live call view shows them; non-null
         */
        public void calls(final LiveCalls calls) {
            Objects.requireNonNull(calls, "calls");
            update(shown -> shown.withProgress(
                    new Activity.Progress(shown.fraction(), shown.stepText(), shown.details(), shown.eta(), calls)));
        }

        private void update(final UnaryOperator<Activity> change) {
            if (!ended) {
                replace(id, change);
            }
        }

        /** Removes the activity; a second call does nothing. */
        public void end() {
            if (ended) {
                log.debug("activity {} #{} already ended", kind, id);
                return;
            }
            ended = true;
            cancels.remove(id);
            running.removeIf(activity -> activity.id() == id);
            log.info("activity {} #{} ended; running now {}", kind, id, kinds());
        }
    }
}
