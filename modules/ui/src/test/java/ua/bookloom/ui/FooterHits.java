package ua.bookloom.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.testfx.api.FxRobot;

/**
 * Where a real mouse lands on a dialog's footer: the robot moves the pointer to each footer button's centre on the
 * screen and presses there, and the node the scene picked for that press must be the button or a part of it. The
 * presses are caught before any node sees them, so no button acts.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FooterHits {

    /** The enabled buttons of every shown dialog footer under {@code root}. */
    static List<Button> footerButtons(final Node root) {
        final List<Button> buttons = new ArrayList<>();
        for (final Node node : Contrast.shown(root)) {
            if (node instanceof Button button && !button.isDisabled() && inFooter(button)) {
                buttons.add(button);
            }
        }
        return buttons;
    }

    /**
     * Presses the mouse at each button's centre and names every button the press did not reach.
     *
     * @return never null; empty when every press landed on its button
     */
    static List<String> missed(final FxRobot robot, final Scene scene, final List<Button> buttons) {
        final AtomicReference<EventTarget> hit = new AtomicReference<>();
        final EventHandler<MouseEvent> swallow = event -> {
            if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
                hit.set(event.getTarget());
            }
            event.consume();
        };
        ThemeTestSupport.onFx(() -> {
            scene.addEventFilter(MouseEvent.ANY, swallow);
            return null;
        });
        final List<String> missed = new ArrayList<>();
        try {
            for (final Button button : buttons) {
                hit.set(null);
                robot.clickOn(button);
                final EventTarget target = hit.get();
                if (!(target instanceof Node node && isWithin(node, button))) {
                    missed.add("%s \"%s\" hit %s".formatted(button.getId(), button.getText(), describe(target)));
                }
            }
        } finally {
            ThemeTestSupport.onFx(() -> {
                scene.removeEventFilter(MouseEvent.ANY, swallow);
                return null;
            });
        }
        return missed;
    }

    private static boolean inFooter(final Node node) {
        for (Node walk = node.getParent(); walk != null; walk = walk.getParent()) {
            if (walk.getStyleClass().contains("button-bar")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithin(final Node node, final Node ancestor) {
        for (Node walk = node; walk != null; walk = walk.getParent()) {
            if (walk.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(final @Nullable EventTarget target) {
        if (target instanceof Node node) {
            return "%s#%s%s".formatted(node.getClass().getSimpleName(), node.getId(), node.getStyleClass());
        }
        return String.valueOf(target);
    }
}
