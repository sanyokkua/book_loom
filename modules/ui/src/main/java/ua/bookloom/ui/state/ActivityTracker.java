package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Every model or provider activity under way, so that one that would compete with another for the model is not
 * offered, the title bar can name what runs and stop it, and leaving a screen whose work would be lost can ask first.
 *
 * <p>The tracker refuses nothing itself: an owner asks {@link #conflictFor} before it starts, registers with
 * {@link #begin} and ends its {@link Handle} however the work ends — ending twice is harmless. Which kinds conflict is
 * {@link ActivityKind#conflictsWith}. The translation run is registered here from the mirror's run state: it counts
 * while the run translates (running, pausing, stopping), not while it waits paused, stopped or ended. FX thread only.
 */
@Slf4j
@Singleton
public final class ActivityTracker {

    /**
     * One activity as the title bar shows it.
     *
     * @param id the registration's own number, unique for the application's life
     * @param kind what runs
     * @param requests how many model requests it has sent so far, zero when it does not count them
     * @param cancellable whether the title bar may offer to stop it
     */
    public record Activity(long id, ActivityKind kind, int requests, boolean cancellable) {

        /** Rejects a missing kind. */
        public Activity {
            Objects.requireNonNull(kind, "kind");
        }
    }

    private final ObservableList<Activity> running = FXCollections.observableArrayList();
    private final ObservableList<Activity> runningView = FXCollections.unmodifiableObservableList(running);
    private final Map<Long, Runnable> cancels = new HashMap<>();
    private long lastId;
    private @Nullable Handle translation;

    /**
     * Creates a tracker that follows the run.
     *
     * @param mirror where the run's state is read; a translating run is registered as {@link ActivityKind#TRANSLATION}
     */
    @Inject
    public ActivityTracker(final StateMirror mirror) {
        Objects.requireNonNull(mirror, "mirror");
        mirror.runState().addListener((observed, was, now) -> followRun(now));
        followRun(mirror.runState().get());
        log.debug("activity tracker ready");
    }

    private void followRun(final RunState state) {
        final boolean translating =
                switch (state) {
                    case RUNNING, PAUSING, STOPPING -> true;
                    case IDLE, PAUSED, STOPPED, COMPLETED, FAILED -> false;
                };
        final Handle registered = translation;
        if (translating && registered == null) {
            translation = begin(ActivityKind.TRANSLATION, null);
        } else if (!translating && registered != null) {
            registered.end();
            translation = null;
        } else {
            log.debug("run state {}: the translation activity stays {}", state, translating ? "registered" : "absent");
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
        running.add(new Activity(id, kind, 0, cancel != null));
        log.info("activity {} #{} started; running now {}", kind, id, kinds());
        return new Handle(id, kind);
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
        log.info("activity {} #{} asked to stop", activity.kind(), activity.id());
        cancel.run();
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
            if (ended) {
                return;
            }
            for (int i = 0; i < running.size(); i++) {
                final Activity shown = running.get(i);
                if (shown.id() == id && shown.requests() != count) {
                    running.set(i, new Activity(id, kind, count, shown.cancellable()));
                }
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
