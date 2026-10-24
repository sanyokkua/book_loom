package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
 * A step whose run actions belong in the toolbar too (Translating's Start, Pause, Resume, Stop and Review) adds them as a
 * run group, which sits just before the forward action wherever the buttons are.
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
    private static final String CONTENT_ID = "#shell-content";

    private final Button forward;
    private final @Nullable Button back;
    private final @Nullable Action backAction;
    private final SetChangeListener<PseudoClass> onWidthClass = change -> showBack();
    private @Nullable String compactBack;
    private @Nullable Region content;
    private boolean backShort;
    private final Region spacer = new Region();
    private final Label hint = new Label();
    private final List<Node> runGroup = new ArrayList<>();
    private boolean hinting;
    private @Nullable Pane host;

    private StepFooter(final @Nullable Action backAction, final Action forward, final boolean forwardAvailable) {
        super(SPACING);
        Objects.requireNonNull(forward, "forward");
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("step-footer");
        this.backAction = backAction;
        this.back = backAction == null ? null : button(backAction);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        this.forward = button(forward);
        this.forward.setDisable(!forwardAvailable);
        hint.setId(forward.id() + "-hint");
        hint.getStyleClass().add("muted");
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
        if (scene.getRoot().lookup(CONTENT_ID) instanceof Region area) {
            content = area;
            area.getPseudoClassStates().addListener(new WeakSetChangeListener<>(onWidthClass));
            showBack();
        }
        getChildren().clear();
        slot.getChildren().addAll(hostedNodes());
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
        content = null;
        showBack();
        slot.getChildren().removeAll(hostedNodes());
        restoreInPlace();
        setVisible(true);
        setManaged(true);
        log.debug("footer actions returned to the footer");
    }

    private void restoreInPlace() {
        final List<Node> nodes = new ArrayList<>();
        if (back != null) {
            nodes.add(back);
        }
        nodes.add(spacer);
        nodes.addAll(forwardNodes());
        getChildren().setAll(nodes);
    }

    private List<Node> forwardNodes() {
        final List<Node> nodes = new ArrayList<>(runGroup);
        if (hinting) {
            nodes.add(hint);
        }
        nodes.add(forward);
        return nodes;
    }

    /**
     * Adds the step's run actions, which sit just before the forward action, in this order.
     *
     * @param actions the buttons; each keeps the id, label and state its owner gives it
     * @return this footer
     */
    public StepFooter withRunGroup(final List<? extends Node> actions) {
        Objects.requireNonNull(actions, "actions");
        runGroup.addAll(actions);
        final Pane slot = host;
        if (slot == null) {
            restoreInPlace();
        } else {
            slot.getChildren().addAll(slot.getChildren().indexOf(forward), actions);
        }
        log.debug("footer {} holds {} run actions", forward.getId(), actions.size());
        return this;
    }

    private List<Node> hostedNodes() {
        final List<Node> nodes = new ArrayList<>();
        if (back != null) {
            nodes.add(back);
        }
        nodes.addAll(forwardNodes());
        return nodes;
    }

    /**
     * Gives Back a short label for a compact content area (README L2), where the full label becomes its tooltip.
     *
     * @param label the already translated short label, such as "‹ Back"
     * @return this footer
     */
    public StepFooter withCompactBack(final String label) {
        compactBack = Objects.requireNonNull(label, "label");
        showBack();
        return this;
    }

    // The content area's width class lives on its pseudo-classes (LayoutClass); a footer outside the shell stays full.
    private void showBack() {
        final Button button = back;
        final Action action = backAction;
        final String shortLabel = compactBack;
        if (button == null || action == null || shortLabel == null) {
            return;
        }
        final Region area = content;
        final boolean compact =
                area != null && area.getPseudoClassStates().contains(LayoutClass.Width.COMPACT.pseudoClass());
        if (compact == backShort) {
            return;
        }
        backShort = compact;
        log.debug("footer back {} reads {}", action.id(), compact ? "short" : "in full");
        button.setText(compact ? shortLabel : action.label());
        Tips.install(button, compact ? action.label() : action.tip());
    }

    /**
     * Says why the forward action cannot be pressed. A disabled button shows no tooltip, so the reason is a visible
     * line beside it, in the footer or in the toolbar wherever the buttons are.
     *
     * @param reason the already translated reason, or {@code null} to remove the line because the action can be pressed
     */
    public void explainDisabledForward(final @Nullable String reason) {
        final Pane parent = host != null ? host : this;
        final boolean shown = reason != null;
        log.debug("forward {} hint {}", forward.getId(), shown ? "shown" : "removed");
        if (shown) {
            hint.setText(reason);
        }
        if (shown == hinting) {
            return;
        }
        hinting = shown;
        if (shown) {
            parent.getChildren().add(parent.getChildren().indexOf(forward), hint);
        } else {
            parent.getChildren().remove(hint);
        }
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
