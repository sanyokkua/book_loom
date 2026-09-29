package ua.bookloom.ui.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The run in the window's title bar: the book's file name, a state text, the time elapsed and left, and the one
 * control that pauses or resumes the run, so a person can act on it from any screen.
 *
 * <p>The bar holds no state of its own. It is redrawn from the mirror and from the view model's controls, and its button
 * calls the view model's {@code pause} and {@code resume}, the methods the translating screen uses, so both places
 * always agree. The file name is the only part that gives way when the window is narrow. No log line is written for a
 * redraw, because the elapsed time changes every second; only showing, hiding and a press are logged.
 */
@Slf4j
@Singleton
public final class RunStatusBar {

    private static final double PERCENT = 100.0;
    private static final double PART_SPACING = 12;

    private final StateMirror mirror;
    private final TranslatingViewModel viewModel;
    private final Messages messages;
    private final HBox view = new HBox(PART_SPACING);
    private final Label fileName = new Label();
    private final Label stateText = new Label();
    private final Label elapsed = new Label();
    private final Label timeLeft = new Label();
    private final Button control = new Button();
    private boolean controlPauses;
    private boolean shown;

    /**
     * Builds the bar and starts following the run; it stays hidden until a run exists.
     *
     * @param mirror where the run's state, figures and file name are read from
     * @param viewModel what the button calls and where the controls on offer are read from
     * @param messages the catalogue every text comes from
     */
    @Inject
    public RunStatusBar(final StateMirror mirror, final TranslatingViewModel viewModel, final Messages messages) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        build();
        final InvalidationListener redraw = observed -> refresh();
        mirror.runFileName().addListener(redraw);
        mirror.runState().addListener(redraw);
        mirror.progressFraction().addListener(redraw);
        mirror.review().providerError().addListener(redraw);
        mirror.live().throughput().addListener(redraw);
        viewModel.controls().addListener(redraw);
        refresh();
    }

    /**
     * The node to place in the title bar.
     *
     * @return the bar; the same node every call, unmanaged while no run exists
     */
    public HBox view() {
        return view;
    }

    private void build() {
        view.setId("shell-run-status");
        view.getStyleClass().add("run-status");
        view.setAlignment(Pos.CENTER_LEFT);
        name(fileName, "shell-run-file", "run-status-text");
        name(stateText, "shell-run-state", "run-status-text");
        name(elapsed, "shell-run-elapsed", "run-status-detail");
        name(timeLeft, "shell-run-left", "run-status-detail");
        fileName.setMinWidth(0);
        fileName.setTextOverrun(OverrunStyle.ELLIPSIS);
        for (final Label part : new Label[] {stateText, elapsed, timeLeft}) {
            part.setMinWidth(Region.USE_PREF_SIZE);
        }
        control.setId("shell-run-control");
        control.getStyleClass().add("shell-title-button");
        control.setContentDisplay(ContentDisplay.TEXT_ONLY);
        control.setMinWidth(Region.USE_PREF_SIZE);
        control.setOnAction(event -> press());
        view.getChildren().addAll(fileName, stateText, elapsed, timeLeft, control);
    }

    private static void name(final Label label, final String id, final String styleClass) {
        label.setId(id);
        label.getStyleClass().add(styleClass);
    }

    private void press() {
        log.debug("run control pressed: {}", controlPauses ? "pause" : "resume");
        if (controlPauses) {
            viewModel.pause();
        } else {
            viewModel.resume();
        }
    }

    private void refresh() {
        final String file = mirror.runFileName().get();
        setShown(file != null);
        if (file == null) {
            return;
        }
        fileName.setText(file);
        final RunState state = mirror.runState().get();
        final int percent = (int) Math.round(mirror.progressFraction().get() * PERCENT);
        stateText.setText(stateText(state, percent));
        showTimes(mirror.live().throughput().get());
        showControl(state, viewModel.controls().get());
    }

    private void setShown(final boolean show) {
        if (show != shown) {
            log.debug("run status bar {}", show ? "shown" : "hidden");
            shown = show;
        }
        view.setVisible(show);
        view.setManaged(show);
    }

    private String stateText(final RunState state, final int percent) {
        return switch (state) {
            case IDLE, RUNNING, PAUSING -> messages.get(MessageKey.SHELL_RUN_PROGRESS, percent);
            case PAUSED ->
                mirror.review().providerError().get() != null
                        ? messages.get(MessageKey.SHELL_RUN_PROVIDER_ERROR)
                        : messages.get(MessageKey.SHELL_RUN_PAUSED, percent);
            case STOPPING, STOPPED -> messages.get(MessageKey.SHELL_RUN_STOPPED, percent);
            case COMPLETED -> messages.get(MessageKey.SHELL_RUN_FINISHED);
            case FAILED -> messages.get(MessageKey.SHELL_RUN_FAILED, percent);
        };
    }

    private void showTimes(final Throughput figures) {
        elapsed.setText(messages.get(MessageKey.SHELL_RUN_ELAPSED, DurationText.format(messages, figures.elapsed())));
        final Duration left = figures.timeLeft();
        timeLeft.setVisible(left != null);
        timeLeft.setManaged(left != null);
        if (left != null) {
            timeLeft.setText(messages.get(MessageKey.SHELL_RUN_LEFT, DurationText.format(messages, left)));
        }
    }

    private void showControl(final RunState state, final Controls offered) {
        if (offered.pause().isShown()) {
            setControl(true, MessageKey.SHELL_RUN_PAUSE, offered.pause().isEnabled());
        } else if (offered.resume().isShown()) {
            setControl(false, MessageKey.SHELL_RUN_RESUME, offered.resume().isEnabled());
        } else if (state == RunState.STOPPING) {
            setControl(false, MessageKey.SHELL_RUN_RESUME, false);
        } else {
            control.setVisible(false);
            control.setManaged(false);
        }
    }

    private void setControl(final boolean pauses, final MessageKey label, final boolean enabled) {
        controlPauses = pauses;
        control.setText(messages.get(label));
        control.setDisable(!enabled);
        control.setVisible(true);
        control.setManaged(true);
    }
}
