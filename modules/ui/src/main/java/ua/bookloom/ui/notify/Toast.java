package ua.bookloom.ui.notify;

import java.util.List;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * One message in the {@link ToastStack}: its node, its dismiss timer, and how many identical messages it stands for.
 *
 * <p>The timer pauses while the pointer is over the toast, so a message being read does not vanish under the cursor.
 * Everything is FX-Application-Thread only.
 */
@Slf4j
final class Toast {

    private static final Duration FADE = Duration.millis(160);
    private static final double SLIDE_PIXELS = 12;

    private final Severity severity;
    private final MessageKey key;
    private final List<Object> args;
    private final HBox node = new HBox();
    private final Label counter = new Label();
    private final PauseTransition timer;
    private boolean isHovered;
    private boolean isDismissed;
    private int count = 1;

    Toast(
            final Messages messages,
            final Severity severity,
            final MessageKey key,
            final Object[] args,
            final @Nullable ToastAction action,
            final java.time.Duration lifetime,
            final Consumer<Toast> dismisser) {
        this.severity = severity;
        this.key = key;
        this.args = List.of(args);
        this.timer = new PauseTransition(Duration.millis((double) lifetime.toMillis()));
        final String text = messages.get(key, args);
        node.getChildren().addAll(icon(severity), textLabel(text), counter());
        node.getStyleClass().addAll("toast", severity.styleClass(), "elevation");
        node.setAccessibleText(text);
        if (action != null) {
            node.getChildren().add(actionButton(messages, action, dismisser));
        } else {
            node.setOnMouseClicked(event -> {
                log.debug("toast dismissed by a click");
                dismisser.accept(this);
            });
        }
        node.getChildren().add(closeButton(messages, dismisser));
        node.setOnMouseEntered(event -> pause());
        node.setOnMouseExited(event -> resume());
        timer.setOnFinished(event -> {
            log.debug("toast dismissed by its own timer");
            dismisser.accept(this);
        });
    }

    private static FontIcon icon(final Severity severity) {
        final FontIcon icon = new FontIcon(severity.icon());
        icon.getStyleClass().add("toast-icon");
        return icon;
    }

    private static Label textLabel(final String text) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("toast-text");
        HBox.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    private Label counter() {
        counter.getStyleClass().add("toast-count");
        counter.setManaged(false);
        counter.setVisible(false);
        return counter;
    }

    private Button actionButton(final Messages messages, final ToastAction action, final Consumer<Toast> dismisser) {
        final Button button = new Button(messages.get(action.label()));
        button.getStyleClass().addAll("btn-ghost", "toast-action");
        button.setOnAction(event -> {
            log.debug("toast action {} pressed", action.label().key());
            action.run().run();
            dismisser.accept(this);
        });
        return Tips.install(messages, button, action.tip());
    }

    private Button closeButton(final Messages messages, final Consumer<Toast> dismisser) {
        final Button close = new Button(null, new FontIcon(Feather.X));
        close.getStyleClass().addAll("btn-ghost", "toast-close");
        close.setAccessibleText(messages.get(MessageKey.TOAST_DISMISS));
        close.setOnAction(event -> {
            log.debug("toast dismissed by its close button");
            dismisser.accept(this);
        });
        return Tips.install(messages, close, MessageKey.TOAST_DISMISS_TIP);
    }

    HBox node() {
        return node;
    }

    boolean isDismissed() {
        return isDismissed;
    }

    /** Whether a new message is the same one: the same severity, entry and arguments. */
    boolean isSameAs(final Severity otherSeverity, final MessageKey otherKey, final List<Object> otherArgs) {
        return !isDismissed && severity == otherSeverity && key == otherKey && args.equals(otherArgs);
    }

    /** Folds one more identical message in: the counter grows and the toast stays as long as a new one would. */
    void repeat() {
        count++;
        counter.setText("×" + count);
        counter.setManaged(true);
        counter.setVisible(true);
        log.debug("toast {} repeated, now {} times", key.key(), count);
        timer.playFromStart();
        if (isHovered) {
            timer.pause();
        }
    }

    void playIn() {
        node.setOpacity(0);
        node.setTranslateY(SLIDE_PIXELS);
        final FadeTransition fade = new FadeTransition(FADE, node);
        fade.setToValue(1);
        final TranslateTransition slide = new TranslateTransition(FADE, node);
        slide.setToY(0);
        fade.play();
        slide.play();
        timer.play();
    }

    /** Stops the timer and fades the toast away; {@code removal} runs when it has gone, or at once with no animation. */
    void dismiss(final boolean isAnimated, final Runnable removal) {
        isDismissed = true;
        timer.stop();
        if (!isAnimated) {
            removal.run();
            return;
        }
        final FadeTransition fade = new FadeTransition(FADE, node);
        fade.setToValue(0);
        fade.setOnFinished(event -> removal.run());
        final TranslateTransition slide = new TranslateTransition(FADE, node);
        slide.setToY(SLIDE_PIXELS);
        node.setMouseTransparent(true);
        fade.play();
        slide.play();
    }

    private void pause() {
        isHovered = true;
        timer.pause();
    }

    private void resume() {
        isHovered = false;
        if (!isDismissed) {
            timer.play();
        }
    }
}
