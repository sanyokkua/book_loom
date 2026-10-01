package ua.bookloom.ui.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/**
 * The model work under way, in the title bar, from any screen: "Model scan · 2 requests", and Stop when the work can be
 * stopped. The run itself is not named here — the run status beside it already is — so the chip shows only while some
 * other work runs, the oldest of them, with a count of the others. A redraw is not logged, since it follows every
 * request; a press of Stop is.
 */
@Slf4j
@Singleton
public final class ActivityChip {

    private static final double SPACING = 6;

    private final ActivityTracker activities;
    private final Messages messages;
    private final HBox view = new HBox(SPACING);
    private final Label text = new Label();
    private final Button stop = new Button();
    private ActivityTracker.@Nullable Activity shown;

    /**
     * Builds the chip and follows the tracker; it stays hidden while nothing but the run is under way.
     *
     * @param activities what runs
     * @param messages the catalogue the words come from
     */
    @Inject
    public ActivityChip(final ActivityTracker activities, final Messages messages) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.messages = Objects.requireNonNull(messages, "messages");
        text.setId("shell-activity-text");
        text.getStyleClass().add("activity-chip-text");
        stop.setText(messages.get(MessageKey.ACTIVITY_STOP));
        stop.setId("shell-activity-stop");
        stop.getStyleClass().add("shell-title-button");
        Tips.install(messages, stop, MessageKey.ACTIVITY_STOP_TIP);
        stop.setOnAction(event -> stopShown());
        view.getChildren().addAll(text, stop);
        view.setId("shell-activity");
        view.getStyleClass().add("activity-chip");
        view.setAlignment(Pos.CENTER_LEFT);
        Tips.install(messages, text, MessageKey.ACTIVITY_CHIP_TIP);
        activities.running().addListener((ListChangeListener<ActivityTracker.Activity>) change -> redraw());
        redraw();
    }

    /**
     * The chip's node, for the title bar.
     *
     * @return the same node every time
     */
    public Node view() {
        return view;
    }

    private void redraw() {
        final List<ActivityTracker.Activity> others = activities.running().stream()
                .filter(activity -> activity.kind() != ActivityKind.TRANSLATION)
                .toList();
        final ActivityTracker.Activity first = others.isEmpty() ? null : others.getFirst();
        shown = first;
        view.setVisible(first != null);
        view.setManaged(first != null);
        if (first == null) {
            text.setText("");
            return;
        }
        text.setText(messages.get(
                MessageKey.ACTIVITY_CHIP, messages.get(first.kind().label()), first.requests(), others.size() - 1));
        stop.setVisible(first.cancellable());
        stop.setManaged(first.cancellable());
    }

    private void stopShown() {
        final ActivityTracker.Activity activity = shown;
        if (activity != null) {
            log.info("stop pressed in the title bar for {} #{}", activity.kind(), activity.id());
            activities.stop(activity.id());
        }
    }
}
