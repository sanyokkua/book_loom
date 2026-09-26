package ua.bookloom.ui.notify;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The shell's toast host and the {@link Toasts} that fill it.
 *
 * <p>Toasts are ordinary nodes in the shell's own scene rather than a popup window, because a second window would
 * not inherit the token-only stylesheet or the theme swap. Everything here is FX-Application-Thread only, like the
 * scene graph it edits; the timers are {@link PauseTransition}s, which fire on that thread too.
 */
@Slf4j
@Singleton
public final class ToastStack implements Toasts {

    /** How long a toast stays when nothing dismisses it. */
    private static final Duration DEFAULT_LIFETIME = Duration.ofSeconds(5);

    /** Beyond this many a burst of events would cover the window, so the oldest make way. */
    private static final int MOST_VISIBLE = 4;

    private final Messages messages;
    private final Duration lifetime;
    private final VBox host = new VBox();
    private final Map<Node, PauseTransition> timers = new HashMap<>();

    /**
     * Creates the stack with the standard toast lifetime.
     *
     * @param messages the catalogue every toast's text comes from
     */
    @Inject
    public ToastStack(final Messages messages) {
        this(messages, DEFAULT_LIFETIME);
    }

    /**
     * Creates the stack with a chosen lifetime, the seam for tests of the non-deterministic delay.
     *
     * @param messages the catalogue every toast's text comes from
     * @param lifetime how long a toast stays when nothing dismisses it
     */
    ToastStack(final Messages messages, final Duration lifetime) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        host.setId("shell-toast-host");
        host.getStyleClass().add("shell-toast-host");
        host.setAlignment(Pos.TOP_RIGHT);
        host.setPickOnBounds(false);
        host.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(host, Pos.TOP_RIGHT);
    }

    /**
     * The node the shell places above everything else.
     *
     * @return the host; empty while no toast is showing
     */
    public Node view() {
        return host;
    }

    @Override
    public void success(final MessageKey key, final Object... args) {
        raise(Severity.SUCCESS, key, args);
    }

    @Override
    public void info(final MessageKey key, final Object... args) {
        raise(Severity.INFO, key, args);
    }

    @Override
    public void warning(final MessageKey key, final Object... args) {
        raise(Severity.WARNING, key, args);
    }

    @Override
    public void error(final MessageKey key, final Object... args) {
        raise(Severity.ERROR, key, args);
    }

    private void raise(final Severity severity, final MessageKey key, final Object[] args) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(args, "args");
        log.debug("raising a {} toast for {} with {} argument(s)", severity, key.key(), args.length);
        if (log.isTraceEnabled()) {
            log.trace("toast {} arguments: {}", key.key(), Arrays.toString(args));
        }
        final Node toast = build(severity, messages.get(key, args));
        host.getChildren().add(toast);
        startTimer(toast);
        while (host.getChildren().size() > MOST_VISIBLE) {
            final Node oldest = host.getChildren().get(0);
            log.debug("dropping the oldest toast: more than {} would be shown", MOST_VISIBLE);
            remove(oldest);
        }
    }

    private Node build(final Severity severity, final String text) {
        final FontIcon icon = new FontIcon(severity.icon());
        icon.getStyleClass().add("toast-icon");
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("toast-text");
        HBox.setHgrow(label, Priority.ALWAYS);
        final HBox toast = new HBox(icon, label);
        toast.getStyleClass().addAll("toast", severity.styleClass(), "elevation");
        toast.setAccessibleText(text);
        toast.setOnMouseClicked(event -> {
            log.debug("toast dismissed by a click");
            remove(toast);
        });
        return toast;
    }

    private void startTimer(final Node toast) {
        final PauseTransition timer = new PauseTransition(javafx.util.Duration.millis((double) lifetime.toMillis()));
        timer.setOnFinished(event -> {
            log.debug("toast dismissed by its own timer");
            remove(toast);
        });
        timers.put(toast, timer);
        timer.play();
    }

    private void remove(final Node toast) {
        final PauseTransition timer = timers.remove(toast);
        if (timer != null) {
            timer.stop();
        }
        host.getChildren().remove(toast);
    }
}
