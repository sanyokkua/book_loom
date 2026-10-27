package ua.bookloom.ui.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/**
 * The model work under way, in the title bar, from any screen: "Model scan · 2 requests", and Stop when the work can be
 * stopped. Work put in the background reads "Preparing names · 53 % · open" instead, and "open" brings its progress
 * dialog back. The run itself is not named here — the run status beside it already is — so the chip shows only while
 * some other work runs: hidden work first, else the oldest, with a count of the others. A redraw is not logged, since
 * it follows every request; a press of Stop or open is.
 */
@Slf4j
@Singleton
public final class ActivityChip {

    private static final double SPACING = 6;
    private static final int PERCENT = 100;

    private final ActivityTracker activities;
    private final Messages messages;
    private final HBox view = new HBox(SPACING);
    private final Label text = new Label();
    private final Button stop = new Button();
    private final Hyperlink open = new Hyperlink();
    private @Nullable Activity shown;

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
        open.setText(messages.get(MessageKey.ACTIVITY_CHIP_OPEN));
        open.setId("shell-activity-open");
        open.getStyleClass().add("activity-chip-open");
        Tips.install(messages, open, MessageKey.ACTIVITY_CHIP_OPEN_TIP);
        open.setOnAction(event -> openShown());
        open.setMinWidth(Region.USE_PREF_SIZE);
        // The chip is held whole in a narrow title bar; the run status beside it gives way instead.
        text.setMinWidth(Region.USE_PREF_SIZE);
        stop.setMinWidth(Region.USE_PREF_SIZE);
        view.getChildren().addAll(text, open, stop);
        view.setId("shell-activity");
        view.getStyleClass().add("activity-chip");
        view.setAlignment(Pos.CENTER_LEFT);
        Tips.install(messages, text, MessageKey.ACTIVITY_CHIP_TIP);
        activities.running().addListener((ListChangeListener<Activity>) change -> redraw());
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
        final List<Activity> others = activities.running().stream()
                .filter(activity -> activity.kind() != ActivityKind.TRANSLATION)
                .toList();
        final Activity first = others.stream()
                .filter(Activity::isHidden)
                .findFirst()
                .orElse(others.isEmpty() ? null : others.getFirst());
        shown = first;
        view.setVisible(first != null);
        view.setManaged(first != null);
        if (first == null) {
            text.setText("");
            return;
        }
        final boolean hidden = first.isHidden();
        text.setText(
                hidden
                        ? hiddenText(first)
                        : messages.get(
                                MessageKey.ACTIVITY_CHIP,
                                messages.get(first.kind().label()),
                                first.requests(),
                                others.size() - 1));
        open.setVisible(hidden);
        open.setManaged(hidden);
        stop.setVisible(first.cancellable() && !hidden);
        stop.setManaged(first.cancellable() && !hidden);
    }

    private String hiddenText(final Activity hidden) {
        final Double fraction = hidden.fraction();
        final int percent = fraction == null ? 0 : (int) Math.floor(fraction * PERCENT);
        return messages.get(
                MessageKey.ACTIVITY_CHIP_HIDDEN, messages.get(hidden.kind().label()), percent);
    }

    private void openShown() {
        final Activity activity = shown;
        if (activity != null) {
            log.info("open pressed in the title bar for {} #{}", activity.kind(), activity.id());
            activities.reveal(activity.id());
        }
    }

    private void stopShown() {
        final Activity activity = shown;
        if (activity != null) {
            log.info("stop pressed in the title bar for {} #{}", activity.kind(), activity.id());
            activities.stop(activity.id());
        }
    }
}
