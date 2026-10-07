package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The row that closes a workflow step: a backward action on the left and a forward action on the right, so onward is
 * always bottom right on every screen.
 *
 * <p>The caller supplies each action's node id, label, hover explanation, button style class and effect, so the ids the
 * screens and their tests already address stay as they were. The forward action can be drawn unavailable, for the last step.
 */
@Slf4j
public final class StepFooter extends HBox {

    private static final double SPACING = 10;

    /**
     * One button of the footer.
     *
     * @param id the button's node id
     * @param label the visible, already translated text
     * @param tip the already translated hover explanation
     * @param styleClass the button style class, such as {@code btn-primary}
     * @param onAction what pressing the button does
     */
    public record Action(String id, String label, String tip, String styleClass, Runnable onAction) {

        /** Rejects a missing part. */
        public Action {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(tip, "tip");
            Objects.requireNonNull(styleClass, "styleClass");
            Objects.requireNonNull(onAction, "onAction");
        }
    }

    private static final String HOST_ID = "#shell-actions";

    private final Button forward;
    private final @Nullable Button back;
    private final Region spacer = new Region();
    private @Nullable Pane host;

    private StepFooter(final @Nullable Action backAction, final Action forward, final boolean forwardAvailable) {
        super(SPACING);
        Objects.requireNonNull(forward, "forward");
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("step-footer");
        this.back = backAction == null ? null : button(backAction);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        this.forward = button(forward);
        this.forward.setDisable(!forwardAvailable);
        restoreInPlace();
        sceneProperty().addListener((observed, was, scene) -> {
            if (scene == null) {
                release();
            } else {
                claim(scene);
            }
        });
    }

    // The window's toolbar is outside the scrolling content, so the step's actions stay reachable however long the
    // screen is. The same buttons move there while the footer is in a scene that has the toolbar's slot; a screen
    // shown on its own keeps them in its footer.
    private void claim(final Scene scene) {
        if (host != null || !(scene.getRoot().lookup(HOST_ID) instanceof Pane slot)) {
            return;
        }
        host = slot;
        getChildren().clear();
        slot.getChildren().addAll(buttons());
        setVisible(false);
        setManaged(false);
        log.debug("footer actions moved to the window toolbar");
    }

    private void release() {
        final Pane slot = host;
        if (slot == null) {
            return;
        }
        host = null;
        slot.getChildren().removeAll(buttons());
        restoreInPlace();
        setVisible(true);
        setManaged(true);
        log.debug("footer actions returned to the footer");
    }

    private void restoreInPlace() {
        getChildren().setAll(back == null ? List.of(spacer, forward) : List.of(back, spacer, forward));
    }

    private List<Button> buttons() {
        return back == null ? List.of(forward) : List.of(back, forward);
    }

    /**
     * Builds a footer whose forward action can be pressed.
     *
     * @param back the backward action, or {@code null} for a step with nothing before it
     * @param forward the forward action
     * @return the footer
     */
    public static StepFooter of(final @Nullable Action back, final Action forward) {
        return new StepFooter(back, forward, true);
    }

    /**
     * Builds a footer whose forward action is drawn but disabled, because no step follows.
     *
     * @param back the backward action, or {@code null}
     * @param forward the forward action, shown unavailable
     * @return the footer
     */
    public static StepFooter withUnavailableForward(final @Nullable Action back, final Action forward) {
        return new StepFooter(back, forward, false);
    }

    /**
     * The forward button, for a screen that enables it only while its input is usable.
     *
     * @return the button; never null
     */
    public Button forwardButton() {
        return forward;
    }

    private static Button button(final Action action) {
        final Button button = new Button(action.label());
        button.setId(action.id());
        button.getStyleClass().add(action.styleClass());
        Tips.install(button, action.tip());
        button.setOnAction(event -> {
            log.debug("footer action {} pressed", action.id());
            action.onAction().run();
        });
        return button;
    }
}
