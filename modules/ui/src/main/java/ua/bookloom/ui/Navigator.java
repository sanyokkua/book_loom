package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.StepLocks;

/**
 * Swaps the content region by {@link ViewNames} constant and answers which step follows another.
 *
 * <p>{@link #navigate} must be called on the JavaFX Application Thread, because it builds nodes. Each view is loaded
 * with the active message catalogue and the {@link GuiceControllerFactory}, and a failed load leaves the previous
 * view in place: a screen that cannot be built must not blank the window. Both properties change only here, so the
 * shell can observe them without being able to fake a navigation. Leaving a screen whose model work would be left
 * behind asks first ({@link LeaveGuard}); the navigation then completes from the person's answer. While short work the
 * window waits for runs every navigation is refused (long work asks instead), and a screen whose step is still locked
 * ({@link StepLocks}) is refused silently: the reason is shown where the step is offered (a disabled button's hover
 * explanation), not as a transient message.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class Navigator {

    private final GuiceControllerFactory controllerFactory;
    private final Messages messages;
    private final LeaveGuard leaveGuard;
    private final ActivityTracker activities;
    private final StepLocks locks;
    private final ImportViewModel importer;
    private final ReadOnlyObjectWrapper<ViewNames> currentView = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Parent> content = new ReadOnlyObjectWrapper<>();

    /**
     * Observes the view on show.
     *
     * @return a read-only property holding {@code null} until the first accepted navigation; it fires once per
     *     accepted navigation and never for a refusal or a repeat
     */
    public ReadOnlyObjectProperty<ViewNames> currentView() {
        return currentView.getReadOnlyProperty();
    }

    /**
     * Observes the root node of the view on show, for the shell's content host to display.
     *
     * @return a read-only property holding {@code null} until the first accepted navigation
     */
    public ReadOnlyObjectProperty<Parent> content() {
        return content.getReadOnlyProperty();
    }

    /**
     * Shows a screen in place of the current one.
     *
     * @param target the screen to show
     * @return {@code true} if the screen is now current; {@code false} if the entry is inert, is already current, its
     *     view failed to load, or leaving the current screen first asks the person, in each of which nothing changes
     *     now, its step is locked, or blocking work is running
     */
    public boolean navigate(final ViewNames target) {
        Objects.requireNonNull(target, "target");
        return navigate(target, true, () -> {});
    }

    /**
     * Opens Import showing the drop zone although steps 1 to 5 are locked, which is how "New translation" reaches the
     * first step with no project open. An open project stays open; only the screen starts empty. The refusals of
     * {@link #navigate} other than the step lock apply.
     *
     * @return {@code true} if Import is now current; {@code false} if short work is running, Import is already
     *     current, its view failed to load, or leaving the current screen first asks the person
     */
    public boolean openImportEmpty() {
        log.debug("opening Import empty from {}", currentView.get());
        return navigate(ViewNames.IMPORT, false, importer::showEmpty);
    }

    /**
     * Shows a screen whatever its step lock says, for the packaged image's launch smoke, which loads every screen
     * with no project open.
     *
     * @param target the screen to show
     * @return as {@link #navigate}, except that a locked step is shown
     */
    public boolean showIgnoringLocks(final ViewNames target) {
        Objects.requireNonNull(target, "target");
        log.debug("showing {} ignoring the step locks", target);
        return navigate(target, false, () -> {});
    }

    private boolean navigate(final ViewNames target, final boolean honourLocks, final Runnable beforeShow) {
        final ViewNames source = currentView.get();
        log.debug("navigating {} -> {}", source, target);
        if (activities.locking().get()) {
            log.debug("refused {}: short work the window waits for is running", target);
            return false;
        }
        if (!target.isAvailable()) {
            log.debug("refused {}: it has no screen", target);
            return false;
        }
        if (honourLocks && isLocked(target)) {
            return false;
        }
        if (target == source) {
            log.debug("refused {}: it is already current", target);
            return false;
        }
        final Runnable shown = () -> {
            beforeShow.run();
            show(source, target);
        };
        if (!leaveGuard.admits(source, shown)) {
            log.debug("navigation {} -> {} waits for the person's answer", source, target);
            return false;
        }
        beforeShow.run();
        return show(source, target);
    }

    private boolean isLocked(final ViewNames target) {
        final Optional<MessageKey> reason = locks.lockReason(target);
        reason.ifPresent(key -> log.debug("refused {}: it is locked, {}", target, key));
        return reason.isPresent();
    }

    private boolean show(final ViewNames source, final ViewNames target) {
        final Optional<Parent> loaded = load(target);
        loaded.ifPresent(root -> {
            content.set(root);
            currentView.set(target);
            log.debug("navigated {} -> {}", source, target);
        });
        return loaded.isPresent();
    }

    /**
     * Finds the step a Continue control leads to.
     *
     * @param after the screen being left
     * @return the next workflow screen in reading order that has a view, skipping inert ones; empty if {@code after}
     *     is outside the workflow or no later step is available
     */
    public Optional<ViewNames> nextAvailableStep(final ViewNames after) {
        Objects.requireNonNull(after, "after");
        final Optional<ViewNames> next = after.group() == NavGroup.WORKFLOW
                ? Arrays.stream(ViewNames.values())
                        .dropWhile(view -> view != after)
                        .skip(1)
                        .filter(view -> view.group() == NavGroup.WORKFLOW && view.isAvailable())
                        .findFirst()
                : Optional.empty();
        log.debug("next available step after {} is {}", after, next);
        return next;
    }

    private Optional<Parent> load(final ViewNames target) {
        final String path = target.resource().orElseThrow();
        final URL url = ViewNames.class.getResource(path);
        if (url == null) {
            log.error("view resource {} of {} is missing from the module", path, target);
            return Optional.empty();
        }
        final FXMLLoader loader = new FXMLLoader(url, messages.bundle());
        loader.setControllerFactory(controllerFactory);
        try {
            final Parent root = loader.load();
            return Optional.of(root);
        } catch (IOException | RuntimeException e) {
            log.error("view {} failed to load from {}", target, path, e);
            return Optional.empty();
        }
    }
}
