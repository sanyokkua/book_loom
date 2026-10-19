package ua.bookloom.ui.notify;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The shell's toast host and the {@link Toasts} that fill it.
 *
 * <p>Toasts are ordinary nodes in the shell's own scene rather than a popup window, because a second window would
 * not inherit the token-only stylesheet or the theme swap. They stack at the bottom centre of the area the shell puts
 * the host in, the newest lowest, and an identical message raised again folds into the one showing. Everything here is
 * FX-Application-Thread only, like the scene graph it edits; the timers are animations, which fire on that thread too.
 */
@Slf4j
@Singleton
public final class ToastStack implements Toasts {

    /** How long a success or information toast stays when nothing dismisses it. */
    private static final Duration GLANCE_LIFETIME = Duration.ofSeconds(5);

    /** How long a warning or error toast stays: long enough to be read and acted on. */
    private static final Duration READ_LIFETIME = Duration.ofSeconds(12);

    /** Beyond this many a burst of events would cover the content, so the oldest make way. */
    private static final int MOST_VISIBLE = 4;

    private final Messages messages;
    private final Duration glanceLifetime;
    private final Duration readLifetime;
    private final VBox host = new VBox();
    private final List<Toast> shown = new ArrayList<>();

    /**
     * Creates the stack with the standard toast lifetimes.
     *
     * @param messages the catalogue every toast's text comes from
     */
    @Inject
    public ToastStack(final Messages messages) {
        this(messages, GLANCE_LIFETIME, READ_LIFETIME);
    }

    /**
     * Creates the stack with chosen lifetimes, the seam for tests of the non-deterministic delay.
     *
     * @param messages the catalogue every toast's text comes from
     * @param glanceLifetime how long a success or information toast stays when nothing dismisses it
     * @param readLifetime how long a warning or error toast stays when nothing dismisses it
     */
    ToastStack(final Messages messages, final Duration glanceLifetime, final Duration readLifetime) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.glanceLifetime = Objects.requireNonNull(glanceLifetime, "glanceLifetime");
        this.readLifetime = Objects.requireNonNull(readLifetime, "readLifetime");
        host.setId("shell-toast-host");
        host.getStyleClass().add("shell-toast-host");
        host.setAlignment(Pos.BOTTOM_CENTER);
        host.setPickOnBounds(false);
        host.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(host, Pos.BOTTOM_CENTER);
    }

    /**
     * The node the shell places above the busy layer and over the content area's width, so an error raised while the
     * window waits is seen and a toast never covers the navigation.
     *
     * @return the host; empty while no toast is showing
     */
    public Node view() {
        return host;
    }

    @Override
    public void success(final MessageKey key, final Object... args) {
        show(Severity.SUCCESS, key, null, args);
    }

    @Override
    public void info(final MessageKey key, final Object... args) {
        show(Severity.INFO, key, null, args);
    }

    @Override
    public void warning(final MessageKey key, final Object... args) {
        show(Severity.WARNING, key, null, args);
    }

    @Override
    public void error(final MessageKey key, final Object... args) {
        show(Severity.ERROR, key, null, args);
    }

    @Override
    public void raise(final Severity severity, final MessageKey key, final ToastAction action, final Object... args) {
        Objects.requireNonNull(action, "action");
        show(severity, key, action, args);
    }

    private void show(
            final Severity severity, final MessageKey key, final @Nullable ToastAction action, final Object[] args) {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(args, "args");
        log.debug("raising a {} toast for {} with {} argument(s)", severity, key.key(), args.length);
        if (log.isTraceEnabled()) {
            log.trace("toast {} arguments: {}", key.key(), Arrays.toString(args));
        }
        final List<Object> argList = List.of(args);
        final Toast same = action == null ? existing(severity, key, argList) : null;
        if (same != null) {
            same.repeat();
            return;
        }
        final Duration lifetime = severity.isPersistent() ? readLifetime : glanceLifetime;
        final Toast toast = new Toast(messages, severity, key, args, action, lifetime, this::dismiss);
        shown.add(toast);
        host.getChildren().add(toast.node());
        toast.playIn();
        while (shown.size() > MOST_VISIBLE) {
            log.debug("dropping the oldest toast: more than {} would be shown", MOST_VISIBLE);
            drop(shown.getFirst(), false);
        }
    }

    private @Nullable Toast existing(final Severity severity, final MessageKey key, final List<Object> args) {
        return shown.stream()
                .filter(toast -> toast.isSameAs(severity, key, args))
                .findFirst()
                .orElse(null);
    }

    private void dismiss(final Toast toast) {
        drop(toast, true);
    }

    private void drop(final Toast toast, final boolean isAnimated) {
        if (toast.isDismissed()) {
            return;
        }
        shown.remove(toast);
        toast.dismiss(isAnimated, () -> host.getChildren().remove(toast.node()));
    }
}
