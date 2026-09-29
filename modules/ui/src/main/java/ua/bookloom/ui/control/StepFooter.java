package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The row that closes a workflow step: a backward action on the left and a forward action on the right, so onward is
 * always bottom right on every screen.
 *
 * <p>The caller supplies each action's node id, label, button style class and effect, so the ids the screens and their
 * tests already address stay as they were. The forward action can be drawn unavailable, for the last step.
 */
@Slf4j
public final class StepFooter extends HBox {

    private static final double SPACING = 10;

    /**
     * One button of the footer.
     *
     * @param id the button's node id
     * @param label the visible, already translated text
     * @param styleClass the button style class, such as {@code btn-primary}
     * @param onAction what pressing the button does
     */
    public record Action(String id, String label, String styleClass, Runnable onAction) {

        /** Rejects a missing part. */
        public Action {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(styleClass, "styleClass");
            Objects.requireNonNull(onAction, "onAction");
        }
    }

    private final Button forward;

    private StepFooter(final @Nullable Action back, final Action forward, final boolean forwardAvailable) {
        super(SPACING);
        Objects.requireNonNull(forward, "forward");
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("step-footer");
        if (back != null) {
            getChildren().add(button(back));
        }
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        this.forward = button(forward);
        this.forward.setDisable(!forwardAvailable);
        getChildren().addAll(spacer, this.forward);
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
        button.setOnAction(event -> {
            log.debug("footer action {} pressed", action.id());
            action.onAction().run();
        });
        return button;
    }
}
