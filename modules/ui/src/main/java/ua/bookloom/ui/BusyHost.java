package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.control.DurationText;
import ua.bookloom.ui.control.LiveCallView;
import ua.bookloom.ui.control.Motion;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.LiveCalls;

/**
 * The shell's layer for work the window waits for: a scrim that swallows clicks and a card that says what runs, how far
 * it has got, how long it has taken and what is left, with Cancel when the work can be stopped. It sits between the
 * frame and the {@link ModalHost}, so an error dialog arriving while it is up is shown above it and leaves it intact.
 *
 * <p>Work that makes model calls, such as the export's consistency pass, also shows its current call in a folded
 * "Model calls" section, the same view the Translating screen's live panel uses.
 *
 * <p>The card appears only after {@link #DELAY} so work that ends at once never flashes it, and fades in and out. It
 * cannot be dismissed from the keyboard or by a click: Escape is swallowed, Tab stays on the card, and the card goes
 * when the work ends. FX thread only.
 */
@Slf4j
@Singleton
public final class BusyHost {

    static final String HOST_ID = "shell-busy-host";
    static final String SCRIM_ID = "shell-busy-scrim";
    static final String CARD_ID = "busy-card";
    static final String TITLE_ID = "busy-title";
    static final String STEP_ID = "busy-step";
    static final String BAR_ID = "busy-bar";
    static final String ELAPSED_ID = "busy-elapsed";
    static final String ETA_ID = "busy-eta";
    static final String DETAILS_ID = "busy-details";
    static final String CANCEL_ID = "busy-cancel";
    static final String CALLS_ID = "busy-calls";
    static final String CALL_ID = "busy-call";

    /** How long work runs before the card appears, so a quick action does not flash it. */
    static final javafx.util.Duration DELAY = javafx.util.Duration.millis(400);

    private static final javafx.util.Duration FADE = Motion.STANDARD;
    private static final javafx.util.Duration TICK = javafx.util.Duration.seconds(1);

    private final ActivityTracker activities;
    private final Messages messages;
    private final ModalHost modalHost;
    private final StackPane host = new StackPane();
    private final Region scrim = new Region();
    private final DialogPane card = new ModalCard();
    private final Label title = new Label();
    private final Label step = new Label();
    private final ProgressBar bar = new ProgressBar();
    private final Label elapsed = new Label();
    private final Label eta = new Label();
    private final VBox details = new VBox();
    private final LiveCallView call;
    private final TitledPane calls;
    private final ButtonType cancelType;
    private final Button cancel;
    private final PauseTransition delay = new PauseTransition(DELAY);
    private final FadeTransition fade = new FadeTransition(FADE, host);
    private final Timeline clock = new Timeline(new KeyFrame(TICK, event -> updateClock()));
    private final EventHandler<KeyEvent> keyFilter = this::filterKey;
    private long shownId = -1;
    private boolean revealed;
    private @Nullable Scene filteredScene;
    private @Nullable Node previousFocus;

    /**
     * Builds the hidden layer and follows the tracker.
     *
     * @param activities where the blocking work is read, stopped and timed
     * @param messages the catalogue every word of the card comes from
     * @param modalHost the dialog layer above this one, whose card takes the keyboard while it is shown
     */
    @Inject
    public BusyHost(final ActivityTracker activities, final Messages messages, final ModalHost modalHost) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        cancelType = new ButtonType(messages.get(MessageKey.BUSY_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
        call = new LiveCallView(
                CALL_ID,
                new ReadOnlyStringWrapper(messages.get(MessageKey.LIVE_CALL_CURRENT)),
                new ReadOnlyStringWrapper(messages.get(MessageKey.LIVE_SOURCE_FALLBACK)),
                new ReadOnlyStringWrapper(messages.get(MessageKey.LIVE_TARGET_FALLBACK)),
                messages);
        calls = new TitledPane(messages.get(MessageKey.BUSY_CALLS), call);
        buildCard();
        cancel = (Button) card.lookupButton(cancelType);
        wireCancel();
        host.setId(HOST_ID);
        host.getStyleClass().add("shell-modal-host");
        scrim.setId(SCRIM_ID);
        scrim.getStyleClass().add("shell-scrim");
        scrim.addEventHandler(MouseEvent.ANY, Event::consume);
        host.getChildren().addAll(scrim, card);
        host.setVisible(false);
        clock.setCycleCount(Timeline.INDEFINITE);
        delay.setOnFinished(event -> reveal());
        activities.blockingActivity().addListener((observed, was, now) -> follow(now));
        follow(activities.blockingActivity().get());
    }

    /**
     * The node to place in the scene, below the dialog layer and above the frame.
     *
     * @return the host region; invisible while nothing blocks
     */
    Node view() {
        return host;
    }

    private void buildCard() {
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        title.setId(TITLE_ID);
        title.getStyleClass().add("dialog-title");
        step.setId(STEP_ID);
        step.getStyleClass().add("dialog-sub");
        step.setWrapText(true);
        final VBox header = new VBox(title, step);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        bar.setId(BAR_ID);
        bar.setMaxWidth(Double.MAX_VALUE);
        elapsed.setId(ELAPSED_ID);
        elapsed.getStyleClass().add("dialog-sub");
        eta.setId(ETA_ID);
        eta.getStyleClass().add("dialog-sub");
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox times = new HBox(elapsed, gap, eta);
        times.setAlignment(Pos.CENTER_LEFT);
        details.setId(DETAILS_ID);
        buildCalls();
        final VBox body = new VBox(bar, times, details, calls);
        body.getStyleClass().add("busy-body");
        card.setContent(body);
        card.getButtonTypes().setAll(cancelType);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    }

    private void buildCalls() {
        calls.setId(CALLS_ID);
        calls.setExpanded(false);
        Tips.install(messages, calls, MessageKey.BUSY_CALLS_TIP);
    }

    private void wireCancel() {
        cancel.setId(CANCEL_ID);
        Tips.install(messages, cancel, MessageKey.BUSY_CANCEL_TIP);
        cancel.getStyleClass().add("btn-danger-outline");
        cancel.setOnAction(event -> {
            log.info("busy card: cancel pressed for activity #{}", shownId);
            activities.stop(shownId);
        });
    }

    private void follow(final @Nullable Activity now) {
        if (now == null) {
            log.debug("busy host: nothing blocks any more");
            delay.stop();
            conceal();
        } else if (revealed) {
            refresh(now);
        } else if (delay.getStatus() != Animation.Status.RUNNING) {
            log.debug("busy host: {} #{} blocks; the card follows after the delay", now.kind(), now.id());
            delay.playFromStart();
        }
    }

    private void reveal() {
        final Activity now = activities.blockingActivity().get();
        if (now == null) {
            return;
        }
        log.debug("busy host: revealing the card for {} #{}", now.kind(), now.id());
        refresh(now);
        revealed = true;
        host.setVisible(true);
        fade.stop();
        if (Motion.isReduced()) {
            host.setOpacity(1);
        } else {
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setOnFinished(null);
            fade.playFromStart();
        }
        capture();
        clock.playFromStart();
        cancel.requestFocus();
    }

    private void conceal() {
        if (!revealed) {
            return;
        }
        revealed = false;
        clock.stop();
        release();
        fade.stop();
        if (Motion.isReduced()) {
            host.setOpacity(0);
            host.setVisible(false);
            return;
        }
        fade.setFromValue(host.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(event -> {
            if (!revealed) {
                host.setVisible(false);
            }
        });
        fade.playFromStart();
    }

    private void refresh(final Activity now) {
        shownId = now.id();
        title.setText(messages.get(now.title()));
        step.setText(stepText(now));
        step.setVisible(!step.getText().isEmpty());
        step.setManaged(step.isVisible());
        final @Nullable Double fraction = now.fraction();
        bar.setProgress(fraction == null ? ProgressBar.INDETERMINATE_PROGRESS : fraction);
        updateClock();
        final Duration left = now.eta();
        eta.setText(left == null ? "" : messages.get(MessageKey.BUSY_ETA, DurationText.format(messages, left)));
        eta.setVisible(left != null);
        showDetails(now);
        showCalls(now.calls());
        showCancel(now);
    }

    // The work's model calls, folded away until the person opens them; work that makes none shows no section.
    private void showCalls(final LiveCalls live) {
        final CallSnapshot current = live.current();
        calls.setVisible(current != null);
        calls.setManaged(current != null);
        call.show(current, live);
    }

    private String stepText(final Activity now) {
        if (now.cancelling()) {
            return messages.get(MessageKey.BUSY_CANCELLING);
        }
        if (!now.cancellable()) {
            return messages.get(MessageKey.BUSY_NOT_CANCELLABLE);
        }
        final String text = now.stepText();
        return text == null ? "" : text;
    }

    private void showDetails(final Activity now) {
        details.getChildren().clear();
        for (final Activity.Detail line : now.details()) {
            details.getChildren().add(row(line.label(), line.value()));
        }
        if (now.requests() > 0) {
            details.getChildren()
                    .add(row(messages.get(MessageKey.ACTIVITY_DETAIL_REQUESTS), Integer.toString(now.requests())));
        }
    }

    private static Node row(final String label, final String value) {
        final Label key = new Label(label);
        key.getStyleClass().add("kv-key");
        final Label shown = new Label(value);
        shown.getStyleClass().add("kv-value");
        final HBox row = new HBox(key, shown);
        row.getStyleClass().add("kv");
        return row;
    }

    private void showCancel(final Activity now) {
        final Node footer = card.lookup(".button-bar");
        if (footer != null) {
            footer.setVisible(now.cancellable());
            footer.setManaged(now.cancellable());
        }
        cancel.setVisible(now.cancellable());
        cancel.setDisable(now.cancelling());
        Tips.install(messages, cancel, now.cancelling() ? MessageKey.BUSY_CANCELLING_TIP : MessageKey.BUSY_CANCEL_TIP);
    }

    private void updateClock() {
        final Activity now = activities.blockingActivity().get();
        if (now == null) {
            return;
        }
        final Instant at = activities.now();
        final long seconds = Math.max(0, Duration.between(now.startedAt(), at).toSeconds());
        elapsed.setText(messages.get(MessageKey.BUSY_ELAPSED, DurationText.clock((int) seconds)));
        final LiveCalls live = now.calls();
        final CallSnapshot current = live.current();
        if (current != null && current.state() == CallState.WAITING) {
            call.show(
                    current,
                    new LiveCalls(current, live.previous(), live.segments(), at.truncatedTo(ChronoUnit.SECONDS)));
        }
    }

    private void capture() {
        final Scene scene = host.getScene();
        if (scene != null && filteredScene == null) {
            previousFocus = scene.getFocusOwner();
            scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            filteredScene = scene;
        }
    }

    private void release() {
        final Scene scene = filteredScene;
        if (scene != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
            filteredScene = null;
        }
        final Node restore = previousFocus;
        previousFocus = null;
        if (restore != null && restore.getScene() != null) {
            restore.requestFocus();
        }
    }

    // A dialog above the card owns the keyboard; otherwise Escape does nothing and Tab never leaves the card.
    private void filterKey(final KeyEvent event) {
        if (modalHost.isShowing()) {
            return;
        }
        final List<KeyCode> swallowed = List.of(KeyCode.ESCAPE, KeyCode.TAB);
        if (swallowed.contains(event.getCode())) {
            log.debug("busy card swallows {}", event.getCode());
            event.consume();
            if (event.getCode() == KeyCode.TAB && cancel.isVisible() && !cancel.isDisabled()) {
                cancel.requestFocus();
            }
        }
    }
}
