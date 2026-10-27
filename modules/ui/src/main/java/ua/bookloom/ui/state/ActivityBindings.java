package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.collections.ObservableList;
import org.jspecify.annotations.Nullable;

/**
 * What the window reads off the running activities: whether shown work makes it wait, which work that is, whether the
 * navigation is locked, and what rules a kind out. Split from {@link ActivityTracker}, which owns the list. FX thread
 * only.
 */
final class ActivityBindings {

    private final ObservableList<Activity> running;
    private final BooleanBinding blocking;
    private final ObjectBinding<@Nullable Activity> blockingActivity;
    private final BooleanBinding locking;

    ActivityBindings(final ObservableList<Activity> running) {
        this.running = Objects.requireNonNull(running, "running");
        blocking = Bindings.createBooleanBinding(
                () -> running.stream().anyMatch(ActivityBindings::isShownBlocking), running);
        blockingActivity = Bindings.createObjectBinding(
                () -> running.stream()
                        .filter(ActivityBindings::isShownBlocking)
                        .findFirst()
                        .orElse(null),
                running);
        locking = Bindings.createBooleanBinding(
                () -> running.stream()
                        .anyMatch(activity ->
                                isShownBlocking(activity) && !activity.kind().isHideable()),
                running);
    }

    private static boolean isShownBlocking(final Activity activity) {
        return activity.isBlocking() && !activity.isHidden();
    }

    BooleanBinding blocking() {
        return blocking;
    }

    ObjectBinding<@Nullable Activity> blockingActivity() {
        return blockingActivity;
    }

    BooleanBinding locking() {
        return locking;
    }

    ObjectBinding<@Nullable ActivityKind> blocker(final ActivityKind kind) {
        return Bindings.createObjectBinding(
                () -> running.stream()
                        .map(Activity::kind)
                        .filter(kind::conflictsWith)
                        .findFirst()
                        .orElse(null),
                running);
    }
}
