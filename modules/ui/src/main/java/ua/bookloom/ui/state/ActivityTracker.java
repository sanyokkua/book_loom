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
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepsPlanned;
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
    private final ReadOnlyObjectWrapper<LiveCalls> lastCalls = new ReadOnlyObjectWrapper<>(LiveCalls.EMPTY);
    private long lastId;
    private final AwakeHolders awake;
    private final Clock clock;
    private final ActivityBindings bindings = new ActivityBindings(running);

    /**
     * Creates a tracker that follows the run and holds nothing awake.
     *
     * @param mirror where the run's state is read; a translating run is registered as {@link ActivityKind#TRANSLATION}
     */
    public ActivityTracker(final StateMirror mirror) {
        this(mirror, new KeepAwake.Off());
    }

    /**
     * Creates a tracker that follows the run and keeps the computer awake while a model activity is registered.
     *
     * @param mirror where the run's state is read; a translating run is registered as {@link ActivityKind#TRANSLATION}
     * @param keepAwake what holds the computer awake while an activity that asks the model is registered
     */
    @Inject
    public ActivityTracker(final StateMirror mirror, final KeepAwake keepAwake) {
        this(mirror, keepAwake, Clock.systemUTC());
    }

    /**
     * Creates a tracker on a given clock, so a test can move the time that elapsed time and the ETA are read from.
     *
     * @param mirror where the run's state is read
     * @param keepAwake what holds the computer awake while an activity that asks the model is registered
     * @param clock where an activity's start and its elapsed time are read
     */
    public ActivityTracker(final StateMirror mirror, final KeepAwake keepAwake, final Clock clock) {
        Objects.requireNonNull(mirror, "mirror");
        this.awake = new AwakeHolders(keepAwake);
        this.clock = Objects.requireNonNull(clock, "clock");
        new RunRegistration(this).follow(mirror);
        log.debug("activity tracker ready");
    }

    /** The calls of the last work that made any, kept for the results dialog once its card is gone. */
    public ReadOnlyObjectProperty<LiveCalls> lastCalls() {
        return lastCalls.getReadOnlyProperty();
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
                id, kind, 0, cancel != null, kind.label(), clock.instant(), Activity.Progress.NONE, false, false));
        log.info("activity {} #{} started; running now {}", kind, id, kinds());
        awake.hold(kind);
        return new Handle(id, kind);
    }

    /** {@return a binding that is {@code true} while blocking work runs with its dialog shown, not in the background} */
    public BooleanBinding blocking() {
        return bindings.blocking();
    }

    /** {@return a binding holding the oldest blocking activity whose dialog is shown, or {@code null} for none} */
    public ObjectBinding<@Nullable Activity> blockingActivity() {
        return bindings.blockingActivity();
    }

    /**
     * {@return a binding that is {@code true} while shown, non-hideable blocking work runs} The navigation and the
     * toolbar follow it; long work does not lock them, so leaving its screen can ask whether to let it run on.
     */
    public BooleanBinding locking() {
        return bindings.locking();
    }

    /**
     * Puts long work's progress dialog in the background; ignored for work that cannot be hidden or has ended.
     *
     * @param id the activity's id
     */
    public void hide(final long id) {
        running.stream().filter(activity -> activity.id() == id).findFirst().ifPresent(activity -> {
            if (!activity.kind().isHideable()) {
                log.debug("activity {} #{} cannot be hidden: it is short work", activity.kind(), id);
                return;
            }
            log.info("activity {} #{} continues in the background", activity.kind(), id);
            replace(id, shown -> shown.withHidden(true));
        });
    }

    /**
     * Brings hidden work's progress dialog back; ignored when it is not hidden or has ended.
     *
     * @param id the activity's id
     */
    public void reveal(final long id) {
        log.debug("activity #{} asked to show its progress again", id);
        replace(id, shown -> shown.withHidden(false));
    }

    /** {@return the tracker's own time, so a dialog's clocks count from the clock the start was taken from} */
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
        return bindings.blocker(Objects.requireNonNull(kind, "kind"));
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
        private final StepTimer timer = new StepTimer();
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
         * Says how far a counted step has got; the time left is the step's elapsed time per unit finished in it times
         * the units left. A step begins when it announces nothing done or its text changes. Ignored once ended.
         *
         * @param done the units finished, from 0
         * @param total the units in the step, at least 1 and at least {@code done}
         * @param stepText what the step is, already worded
         */
        public void progress(final int done, final int total, final String stepText) {
            Objects.requireNonNull(stepText, "stepText");
            final double fraction = total <= 0 ? 0 : Math.min(1.0, (double) done / total);
            final Instant now = clock.instant();
            log.debug("activity {} #{} step '{}' at {} of {}", kind, id, stepText, done, total);
            update(shown -> {
                final Duration eta = timer.eta(shown.startedAt(), done, total, stepText, now);
                return shown.withProgress(
                        new Activity.Progress(fraction, stepText, shown.details(), eta, shown.calls(), shown.steps()));
            });
        }

        /**
         * Says what is being done when how far it has got is not known; clears any fraction and time left.
         *
         * @param stepText what is being done, already worded; {@code null} for none
         */
        public void indeterminate(final @Nullable String stepText) {
            update(shown -> shown.withProgress(
                    new Activity.Progress(null, stepText, shown.details(), null, shown.calls(), shown.steps())));
        }

        /**
         * Replaces the card's detail lines.
         *
         * @param lines the lines in the order they are shown; non-null
         */
        public void details(final List<Activity.Detail> lines) {
            Objects.requireNonNull(lines, "lines");
            update(shown -> shown.withProgress(shown.progress().withDetails(lines)));
        }

        /**
         * Folds a step event into the activity's steps; any other event is ignored, as is any event once ended.
         *
         * @param event the job event the work announced; non-null
         */
        public void steps(final JobEvent event) {
            Objects.requireNonNull(event, "event");
            if (event instanceof StepChanged changed) {
                step(changed, null);
            } else if (event instanceof StepsPlanned planned) {
                log.debug("activity {} #{} plans {} {} steps", kind, id, planned.added(), planned.kind());
                update(shown -> shown.withProgress(
                        shown.progress().withSteps(shown.steps().apply(planned))));
            }
        }

        /**
         * Folds one step change in with what the step read and wrote; ignored once ended.
         *
         * @param changed the step's new state; non-null
         * @param text what it read and wrote, or {@code null}
         */
        public void step(final StepChanged changed, final StepEntry.@Nullable StepText text) {
            Objects.requireNonNull(changed, "changed");
            log.debug("activity {} #{} step {} {} is {}", kind, id, changed.kind(), changed.index(), changed.state());
            update(shown ->
                    shown.withProgress(shown.progress().withSteps(shown.steps().apply(changed, text))));
        }

        /**
         * Replaces the model calls the card shows; ignored once ended.
         *
         * @param calls the calls as the live call view shows them; non-null
         */
        public void calls(final LiveCalls calls) {
            Objects.requireNonNull(calls, "calls");
            if (!ended && calls.current() != null) {
                lastCalls.set(calls);
            }
            update(shown -> shown.withProgress(shown.progress().withCalls(calls)));
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
            awake.release(kind);
        }
    }
}
