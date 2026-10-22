package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Motion;
import ua.bookloom.ui.control.ProgressDialog;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.DialogFeedback;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.ProgressDialogDefaults;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.ProgressFigures;
import ua.bookloom.ui.state.WorkVisibility;

/**
 * The work layer: a scrim that swallows clicks and the progress dialog of the blocking work that is shown. It sits
 * between the frame and the {@link ModalHost}, so a question or an error arriving while it is up is shown above it
 * and the dialog keeps its state underneath.
 *
 * <p>The dialog appears only after {@link #DELAY} so work that ends at once never flashes it, and fades in and out.
 * Each activity keeps its dialog and model until it ends, so long work put in the background ({@link
 * ActivityTracker#hide}) reopens as it was. The tracker's changes reach the models at most every {@link #COALESCE}.
 * Escape hides long work and cancels short work; the export's Cancel asks first. While a dialog shows, the layer says so
 * through {@link WorkVisibility}, so a dialog nobody asked for waits. FX thread only.
 */
@Slf4j
@Singleton
public final class BusyHost {

    static final String HOST_ID = "shell-busy-host";
    static final String SCRIM_ID = "shell-busy-scrim";

    /** How long work runs before its dialog appears, so a quick action does not flash it. */
    static final javafx.util.Duration DELAY = javafx.util.Duration.millis(400);

    /** The shortest gap between two redraws of the dialogs, about ten a second. */
    static final javafx.util.Duration COALESCE = javafx.util.Duration.millis(100);

    private static final javafx.util.Duration FADE = Motion.STANDARD;
    private static final javafx.util.Duration TICK = javafx.util.Duration.seconds(1);

    private final ActivityTracker activities;
    private final Messages messages;
    private final ProgressDialogDefaults defaults;
    private final ProgressFigures.Factory figures;
    private final WorkVisibility work;
    private final ConfirmDialog confirm;
    private final StackPane host = new StackPane();
    private final Region scrim = new Region();
    private final Map<Long, Shown> dialogs = new HashMap<>();
    private final PauseTransition delay = new PauseTransition(DELAY);
    private final PauseTransition coalesce = new PauseTransition(COALESCE);
    private final FadeTransition fade = new FadeTransition(FADE, host);
    private final Timeline clock = new Timeline(new KeyFrame(TICK, event -> tick()));
    private final BusyKeyboard keyboard;
    private final DialogFeedback feedback;
    private long shownId = -1;
    private boolean revealed;

    /** One activity's dialog and the model it reads. */
    private record Shown(ProgressDialogModel model, ProgressDialog dialog) {}

    /**
     * Builds the hidden layer and follows the tracker.
     *
     * @param activities where the blocking work is read, hidden, stopped and timed
     * @param messages the catalogue every word of the dialogs comes from
     * @param modalHost the dialog layer above this one, whose card takes the keyboard while it is shown
     * @param defaults the view a new dialog opens in
     * @param figures how each dialog gets its own reader of its figures
     * @param work where this layer says whether a dialog is showing
     * @param confirm the question asked before the export is cancelled, shown on the layer above
     * @param feedback told which dialog is shown, so a message raised meanwhile becomes its footer line
     */
    @Inject
    public BusyHost(
            final ActivityTracker activities,
            final Messages messages,
            final ModalHost modalHost,
            final ProgressDialogDefaults defaults,
            final ProgressFigures.Factory figures,
            final WorkVisibility work,
            final ConfirmDialog confirm,
            final DialogFeedback feedback) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.figures = Objects.requireNonNull(figures, "figures");
        this.work = Objects.requireNonNull(work, "work");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        keyboard = new BusyKeyboard(host, Objects.requireNonNull(modalHost, "modalHost"));
        host.setId(HOST_ID);
        host.getStyleClass().add("shell-modal-host");
        scrim.setId(SCRIM_ID);
        scrim.getStyleClass().add("shell-scrim");
        scrim.addEventHandler(MouseEvent.ANY, Event::consume);
        host.getChildren().add(scrim);
        host.setVisible(false);
        host.widthProperty().addListener((observed, was, now) -> fit());
        host.heightProperty().addListener((observed, was, now) -> fit());
        clock.setCycleCount(Timeline.INDEFINITE);
        delay.setOnFinished(event -> reveal());
        coalesce.setOnFinished(event -> flush());
        activities.blockingActivity().addListener((observed, was, now) -> follow(now));
        activities.running().addListener((ListChangeListener<Activity>) change -> changed());
        follow(activities.blockingActivity().get());
    }

    /**
     * The node to place in the scene, below the dialog layer and above the frame.
     *
     * @return the host region; invisible while no dialog shows
     */
    Node view() {
        return host;
    }

    // Many changes a second reach the tracker during a pass; the dialogs redraw at most every COALESCE.
    private void changed() {
        if (coalesce.getStatus() != Animation.Status.RUNNING) {
            coalesce.playFromStart();
        }
    }

    private void flush() {
        final Set<Long> alive = activities.running().stream().map(Activity::id).collect(Collectors.toSet());
        dialogs.keySet().removeIf(id -> !alive.contains(id));
        activities.running().forEach(activity -> {
            final Shown shown = dialogs.get(activity.id());
            if (shown != null) {
                shown.model().accept(activity);
            }
        });
    }

    private void follow(final @Nullable Activity now) {
        if (now == null) {
            log.debug("work layer: no shown work blocks any more");
            delay.stop();
            conceal();
        } else if (revealed && now.id() != shownId) {
            log.debug("work layer: the dialog of {} #{} replaces #{}", now.kind(), now.id(), shownId);
            show(now);
        } else if (!revealed && delay.getStatus() != Animation.Status.RUNNING) {
            log.debug("work layer: {} #{} blocks; its dialog follows after the delay", now.kind(), now.id());
            delay.playFromStart();
        }
    }

    private void reveal() {
        final Activity now = activities.blockingActivity().get();
        if (now == null) {
            return;
        }
        log.info("work layer: showing the progress of {} #{}", now.kind(), now.id());
        revealed = true;
        // Visible first: a node under an invisible parent cannot take the focus the dialog asks for.
        host.setVisible(true);
        show(now);
        work.visibleProperty().set(true);
        fade.stop();
        if (Motion.isReduced()) {
            host.setOpacity(1);
        } else {
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setOnFinished(null);
            fade.playFromStart();
        }
        clock.playFromStart();
    }

    private void show(final Activity now) {
        final Shown shown = dialogs.computeIfAbsent(now.id(), id -> build(now));
        shown.model().accept(now);
        shownId = now.id();
        host.getChildren().setAll(scrim, shown.dialog());
        feedback.open(DialogFeedback.Layer.WORK, shown.dialog());
        fit();
        keyboard.capture(shown.dialog());
    }

    private Shown build(final Activity first) {
        final ProgressDialogModel.Mode mode = defaults.initialMode().get();
        log.debug("work layer: a progress dialog for {} #{} opens in {}", first.kind(), first.id(), mode);
        final ProgressDialogModel model = new ProgressDialogModel(first, figures.create(), mode, activities::now);
        final long id = first.id();
        final Runnable hide = first.kind().isHideable() ? () -> activities.hide(id) : null;
        return new Shown(model, new ProgressDialog(model, messages, () -> cancel(first.kind(), id), hide));
    }

    private void cancel(final ActivityKind kind, final long id) {
        if (kind == ActivityKind.EXPORT) {
            log.debug("work layer: the export's Cancel asks first");
            confirm.ask(ConfirmDialog.Question.CANCEL_EXPORT, () -> activities.stop(id));
        } else {
            activities.stop(id);
        }
    }

    private void conceal() {
        if (!revealed) {
            return;
        }
        log.info("work layer: the progress dialog of #{} goes", shownId);
        revealed = false;
        shownId = -1;
        work.visibleProperty().set(false);
        feedback.close(DialogFeedback.Layer.WORK);
        clock.stop();
        keyboard.release();
        fade.stop();
        if (Motion.isReduced()) {
            host.setOpacity(0);
            hideHost();
            return;
        }
        fade.setFromValue(host.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(event -> {
            if (!revealed) {
                hideHost();
            }
        });
        fade.playFromStart();
    }

    // A dialog that has gone is not left in the scene behind the invisible layer; the next one is put there again.
    private void hideHost() {
        host.setVisible(false);
        host.getChildren().setAll(scrim);
    }

    private void fit() {
        final Shown shown = dialogs.get(shownId);
        final Scene scene = host.getScene();
        if (shown == null) {
            return;
        }
        final double width = host.getWidth() > 0 || scene == null ? host.getWidth() : scene.getWidth();
        final double height = host.getHeight() > 0 || scene == null ? host.getHeight() : scene.getHeight();
        if (width > 0 && height > 0) {
            shown.dialog().fit(width, height);
        }
    }

    private void tick() {
        final Shown shown = dialogs.get(shownId);
        if (shown != null) {
            shown.dialog().tick(activities.now());
        }
    }
}
