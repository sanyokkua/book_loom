package ua.bookloom.ui.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ConnectionStatus;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.RunMode;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The run in the window's title bar: the book's file name, a state text, the time elapsed and left, a chip saying how
 * the model server has been answering (which opens the provider settings), and the one control that pauses or resumes
 * the run, so a person can act on it from any screen.
 *
 * <p>The bar holds no state of its own. It is redrawn from the mirror and from the view model's controls, and its button
 * calls the view model's {@code pause} and {@code resume}, the methods the translating screen uses, so both places
 * always agree. The file name is the only part that gives way when the window is narrow. No log line is written for a
 * redraw, because the elapsed time changes every second; only showing, hiding and a press are logged.
 */
@Slf4j
@Singleton
public final class RunStatusBar {

    private static final double PART_SPACING = 12;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final List<String> HEALTH_CLASSES = Arrays.stream(ConnectionStatus.Health.values())
            .map(ConnectionStatus.Health::styleClass)
            .toList();

    private final StateMirror mirror;
    private final TranslatingViewModel viewModel;
    private final Messages messages;
    private final HBox view = new HBox(PART_SPACING);
    private final Label fileName = new Label();
    private final Label stateText = new Label();
    private final Label modeText = new Label();
    private final Label elapsed = new Label();
    private final Label timeLeft = new Label();
    private final Label rate = new Label();
    private final Button control = new Button();
    private final Button connection = new Button();
    private final Tooltip connectionTip = new Tooltip();
    private final Navigator navigator;
    private boolean controlPauses;
    private boolean shown;

    /**
     * Builds the bar and starts following the run; it stays hidden until a run exists.
     *
     * @param mirror where the run's state, figures and file name are read from
     * @param viewModel what the button calls and where the controls on offer are read from
     * @param messages the catalogue every text comes from
     * @param navigator where the connection chip leads: the provider settings
     */
    @Inject
    public RunStatusBar(
            final StateMirror mirror,
            final TranslatingViewModel viewModel,
            final Messages messages,
            final Navigator navigator) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        build();
        final InvalidationListener redraw = observed -> refresh();
        mirror.runFileName().addListener(redraw);
        mirror.runMode().addListener(redraw);
        mirror.runState().addListener(redraw);
        mirror.figures().addListener(redraw);
        mirror.review().providerError().addListener(redraw);
        mirror.review().recovery().addListener(redraw);
        mirror.live().throughput().addListener(redraw);
        mirror.live().connection().addListener(redraw);
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
        name(modeText, "shell-run-mode", "run-status-detail");
        Tips.install(messages, modeText, MessageKey.SHELL_RUN_MODE_TIP);
        name(elapsed, "shell-run-elapsed", "run-status-detail");
        name(timeLeft, "shell-run-left", "run-status-detail");
        name(rate, "shell-run-rate", "run-status-detail");
        Tips.install(messages, rate, MessageKey.SHELL_RUN_RATE_TIP);
        fileName.setMinWidth(0);
        fileName.setTextOverrun(OverrunStyle.ELLIPSIS);
        for (final Label part : new Label[] {stateText, modeText, elapsed, timeLeft, rate}) {
            part.setMinWidth(Region.USE_PREF_SIZE);
        }
        control.setId("shell-run-control");
        control.getStyleClass().add("shell-title-button");
        control.setContentDisplay(ContentDisplay.TEXT_ONLY);
        control.setMinWidth(Region.USE_PREF_SIZE);
        control.setOnAction(event -> press());
        Tips.install(messages, control, MessageKey.SHELL_RUN_PAUSE_TIP);
        buildConnection();
        view.getChildren().addAll(fileName, stateText, modeText, elapsed, timeLeft, rate, connection, control);
    }

    private void buildConnection() {
        connection.setId("shell-run-connection");
        connection.getStyleClass().addAll("shell-title-button", "connection-chip");
        connection.setMinWidth(Region.USE_PREF_SIZE);
        connectionTip.setText(messages.get(MessageKey.SHELL_CONNECTION));
        connection.setTooltip(connectionTip);
        connection.setOnAction(event -> {
            log.debug("connection chip pressed: opening the provider settings");
            navigator.navigate(ViewNames.SETTINGS);
        });
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
        final int percent = mirror.figures().get().percent();
        stateText.setText(stateText(state, percent));
        showMode(mirror.runMode().get());
        showTimes(mirror.live().throughput().get());
        showConnection(mirror.live().connection().get());
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
            case PAUSED -> pausedText(percent);
            case STOPPING, STOPPED -> messages.get(MessageKey.SHELL_RUN_STOPPED, percent);
            case COMPLETED -> messages.get(MessageKey.SHELL_RUN_FINISHED);
            case FAILED -> messages.get(MessageKey.SHELL_RUN_FAILED, percent);
        };
    }

    private String pausedText(final int percent) {
        final RecoveryState recovery = mirror.review().recovery().get();
        if (recovery != null && recovery.isWaiting()) {
            return messages.get(
                    MessageKey.SHELL_RUN_WAITING_PROVIDER, clock(Duration.ofSeconds(recovery.secondsLeft())));
        }
        return mirror.review().providerError().get() != null
                ? messages.get(MessageKey.SHELL_RUN_PROVIDER_ERROR)
                : messages.get(MessageKey.SHELL_RUN_PAUSED, percent);
    }

    private void showMode(final @Nullable RunMode mode) {
        modeText.setVisible(mode != null);
        modeText.setManaged(mode != null);
        if (mode != null) {
            modeText.setText(messages.get(
                    MessageKey.SHELL_RUN_MODE,
                    messages.get(RunMode.dialKey(mode.dial())),
                    messages.get(
                            MessageKey.TRANSLATING_READY_REVIEW_MODE,
                            mode.reviewMode().name().toLowerCase(Locale.ROOT))));
        }
    }

    private void showTimes(final Throughput figures) {
        elapsed.setText(messages.get(MessageKey.SHELL_RUN_ELAPSED, DurationText.format(messages, figures.elapsed())));
        final Duration left = figures.timeLeft();
        timeLeft.setVisible(left != null);
        timeLeft.setManaged(left != null);
        if (left != null) {
            timeLeft.setText(messages.get(MessageKey.SHELL_RUN_LEFT, DurationText.format(messages, left)));
        }
        final Double average = figures.averageTokensPerSecond();
        rate.setVisible(average != null);
        rate.setManaged(average != null);
        if (average != null) {
            rate.setText(
                    messages.get(MessageKey.SHELL_RUN_RATE, (figures.estimated() ? "~" : "") + Math.round(average)));
        }
    }

    private void showConnection(final ConnectionStatus status) {
        // While the run waits for the provider by itself, the chip says so whatever the recent calls were.
        final boolean retrying = RecoveryState.waits(mirror.review().recovery().get());
        final ConnectionStatus.Health health = retrying ? ConnectionStatus.Health.UNSTEADY : status.health();
        final Duration since = status.sinceLastAnswer();
        connection.setText(
                retrying
                        ? messages.get(MessageKey.SHELL_CONNECTION_RETRYING)
                        : switch (health) {
                            case UNKNOWN -> messages.get(MessageKey.SHELL_CONNECTION_UNKNOWN);
                            case STEADY -> messages.get(MessageKey.SHELL_CONNECTION_STEADY, clock(since));
                            case UNSTEADY ->
                                messages.get(MessageKey.SHELL_CONNECTION_UNSTEADY, status.failuresRecently());
                        });
        connection.getStyleClass().removeAll(HEALTH_CLASSES);
        connection.getStyleClass().add(health.styleClass());
        final String model = viewModel.modelText().get();
        connectionTip.setText(messages.get(MessageKey.SHELL_CONNECTION) + "\n"
                + messages.get(
                        MessageKey.SHELL_CONNECTION_TIP,
                        model == null || model.isBlank() ? none() : model,
                        since == null ? none() : clock(since),
                        String.valueOf(status.timeoutsRecently()),
                        String.valueOf(status.failuresRecently()),
                        rate(status.draftTokensPerSecond()),
                        rate(status.judgeTokensPerSecond())));
    }

    private String none() {
        return messages.get(MessageKey.SHELL_CONNECTION_NONE);
    }

    private String rate(final @Nullable Double tokensPerSecond) {
        return tokensPerSecond == null ? none() : String.valueOf(Math.round(tokensPerSecond));
    }

    private static String clock(final @Nullable Duration duration) {
        final long seconds = duration == null ? 0 : duration.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
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
        Tips.install(messages, control, pauses ? MessageKey.SHELL_RUN_PAUSE_TIP : MessageKey.SHELL_RUN_RESUME_TIP);
        control.setDisable(!enabled);
        control.setVisible(true);
        control.setManaged(true);
    }
}
