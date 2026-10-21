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
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ConnectionStatus;
import ua.bookloom.ui.state.RunMode;
import ua.bookloom.ui.state.RunRow;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.Throughput;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The open project in the window's title bar: its name, the state text of its run-state row, the quality and review
 * mode, the time elapsed and the speed while translating, the AI-model chip (which opens the settings) and the one run
 * control, so a person can follow and pause the run from any screen.
 *
 * <p>Everything shown is read from the {@link RunStateSource} row, the same row the toolbar and the banners read, and
 * the control calls the view model's {@code pause} and {@code resume}, as the toolbar's buttons do, so the two places
 * always agree. A window of 1280 pixels or less hides the mode, the time and the speed, one of 1060 or less the name
 * ({@link PriorityRow}). No line is logged for a redraw, because the elapsed time changes every second; only showing,
 * hiding and a press are logged.
 */
@Slf4j
@Singleton
public final class RunStatusBar {

    private static final double PART_SPACING = 12;
    // The project name shrinks to about twenty characters before any other part is hidden.
    private static final double NAME_COMFORT = 150;
    private static final double WITHOUT_DETAILS_AT = 1280;
    private static final double WITHOUT_NAME_AT = 1060;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final List<String> MEANING_CLASSES = Arrays.stream(StateMarker.Meaning.values())
            .map(StateMarker.Meaning::styleClass)
            .toList();

    private final RunStateSource runs;
    private final StateMirror mirror;
    private final TranslatingViewModel viewModel;
    private final Messages messages;
    private final Navigator navigator;
    private final PriorityRow view = new PriorityRow(PART_SPACING);
    private final Label projectName = new Label();
    private final FigureLabel stateText = new FigureLabel();
    private final Label modeText = new Label();
    // The state text (which holds the time left) and the two figures change every second; they are their own layout
    // roots, so a tick does not re-measure the bar.
    private final Label elapsed = new FigureLabel();
    private final Label rate = new FigureLabel();
    private final Button control = new Button();
    private final Button chip = new Button();
    private final Tooltip chipTip = new Tooltip();
    private RunStateView.@Nullable RunAction shownControl;
    private StateMarker.@Nullable Meaning shownMeaning;
    private @Nullable RunRow shownRow;
    private String shownTip = "";
    private boolean shown;

    /**
     * Builds the bar and starts following the run-state row; it stays hidden while no project is open.
     *
     * @param runs the row, the project's name and the chosen model
     * @param mirror the run's mode, times and connection figures
     * @param viewModel what the control calls
     * @param messages the catalogue every text comes from
     * @param navigator where the chip leads: the settings
     */
    @Inject
    public RunStatusBar(
            final RunStateSource runs,
            final StateMirror mirror,
            final TranslatingViewModel viewModel,
            final Messages messages,
            final Navigator navigator) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        build();
        final InvalidationListener redraw = observed -> refresh();
        runs.view().addListener(redraw);
        runs.projectOpen().addListener(redraw);
        runs.projectName().addListener(redraw);
        mirror.runMode().addListener(redraw);
        mirror.live().throughput().addListener(redraw);
        mirror.live().connection().addListener(redraw);
        // A figure is worded only while it is shown (a hidden label never laid out would re-measure the bar on every
        // tick), so it is worded again when a wider window brings it back.
        elapsed.visibleProperty().addListener(redraw);
        rate.visibleProperty().addListener(redraw);
        refresh();
    }

    /**
     * The node to place in the title bar.
     *
     * @return the bar; the same node every call, unmanaged while no project is open
     */
    public HBox view() {
        return view;
    }

    private void build() {
        view.setId("shell-run-status");
        view.getStyleClass().add("run-status");
        view.setAlignment(Pos.CENTER_LEFT);
        name(projectName, "shell-run-project", "run-status-text");
        name(stateText, "shell-run-state", "run-status-text");
        stateText.getStyleClass().add("run-status-state");
        name(modeText, "shell-run-mode", "run-status-detail");
        Tips.install(messages, modeText, MessageKey.SHELL_RUN_MODE_TIP);
        name(elapsed, "shell-run-elapsed", "run-status-detail");
        name(rate, "shell-run-rate", "run-status-detail");
        Tips.install(messages, rate, MessageKey.SHELL_RUN_RATE_TIP);
        projectName.setMinWidth(0);
        projectName.setTextOverrun(OverrunStyle.ELLIPSIS);
        Overrun.install(projectName);
        for (final Label part : new Label[] {stateText, modeText, elapsed, rate}) {
            part.setMinWidth(Region.USE_PREF_SIZE);
        }
        control.setId("shell-run-control");
        control.getStyleClass().add("shell-title-button");
        control.setContentDisplay(ContentDisplay.TEXT_ONLY);
        control.setMinWidth(Region.USE_PREF_SIZE);
        control.setOnAction(event -> press());
        Tips.install(messages, control, MessageKey.TRANSLATING_PAUSE_TIP);
        buildChip();
        view.getChildren().addAll(projectName, stateText, modeText, elapsed, rate, chip, control);
        giveWay();
    }

    // The window's own width hides the details first (README "Window chrome"); a title bar narrower still shortens the
    // name, then hides the rate, the mode and the elapsed time in that order. The state, the chip and the control stay.
    private void giveWay() {
        view.elastic(projectName, NAME_COMFORT);
        view.droppable(rate, modeText, elapsed);
        view.dropAtWindowWidth(WITHOUT_DETAILS_AT, modeText, elapsed, rate);
        view.dropAtWindowWidth(WITHOUT_NAME_AT, projectName);
    }

    private void buildChip() {
        chip.setId("shell-run-connection");
        chip.getStyleClass().addAll("chip", "state-marker", "connection-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chipTip.setText(messages.get(MessageKey.SHELL_CONNECTION));
        chip.setTooltip(chipTip);
        chip.setOnAction(event -> {
            log.debug("AI-model chip pressed: opening the settings");
            navigator.navigate(ViewNames.SETTINGS);
        });
    }

    private static void name(final Label label, final String id, final String styleClass) {
        label.setId(id);
        label.getStyleClass().add(styleClass);
    }

    private void press() {
        final RunStateView.RunAction action = shownControl;
        if (action == null) {
            return;
        }
        log.debug("title-bar run control pressed: {}", action.id());
        if (RunStateView.PAUSE.equals(action.id())) {
            viewModel.pause();
        } else {
            viewModel.resume();
        }
    }

    private void refresh() {
        setShown(runs.projectOpen().get());
        if (!shown) {
            return;
        }
        final RunStateView row = runs.view().get();
        projectName.setText(runs.projectName().get());
        if (row.row() != shownRow) {
            shownRow = row.row();
            stateText.release();
        }
        stateText.setText(row.title().in(messages));
        showMode(mirror.runMode().get());
        showTimes(mirror.live().throughput().get(), row.row());
        showChip(row.chip());
        showChipTip(mirror.live().connection().get());
        showControl(row.titleControl());
    }

    private void setShown(final boolean show) {
        if (show != shown) {
            log.debug("run status bar {}", show ? "shown" : "hidden");
            shown = show;
        }
        view.setVisible(show);
        view.setManaged(show);
    }

    private void showMode(final @Nullable RunMode mode) {
        view.want(modeText, mode != null);
        if (mode != null) {
            modeText.setText(messages.get(
                    MessageKey.SHELL_RUN_MODE,
                    messages.get(RunMode.dialKey(mode.dial())),
                    messages.get(
                            MessageKey.TRANSLATING_READY_REVIEW_MODE,
                            mode.reviewMode().name().toLowerCase(Locale.ROOT))));
        }
    }

    // The elapsed time and the speed belong to a run that is translating; a paused or ended one shows neither.
    private void showTimes(final Throughput figures, final RunRow row) {
        final boolean ticking = row == RunRow.TRANSLATING || row == RunRow.STUCK;
        view.want(elapsed, ticking);
        if (elapsed.isVisible()) {
            elapsed.setText(
                    messages.get(MessageKey.SHELL_RUN_ELAPSED, DurationText.format(messages, figures.elapsed())));
        }
        final Double average = figures.averageTokensPerSecond();
        view.want(rate, ticking && average != null);
        if (average != null && rate.isVisible()) {
            rate.setText(
                    messages.get(MessageKey.SHELL_RUN_RATE, (figures.estimated() ? "~" : "") + Math.round(average)));
        }
    }

    // The class list and the icon change only with the meaning: a changed class list restyles and re-measures the chip.
    private void showChip(final RunStateView.Chip shownChip) {
        chip.setText(shownChip.word().in(messages));
        final StateMarker.Meaning meaning = shownChip.meaning();
        if (meaning == shownMeaning) {
            return;
        }
        shownMeaning = meaning;
        chip.getStyleClass().removeAll(MEANING_CLASSES);
        chip.getStyleClass().add(meaning.styleClass());
        final FontIcon icon = new FontIcon(meaning.icon());
        icon.getStyleClass().add("state-icon");
        chip.setGraphic(icon);
    }

    private void showChipTip(final ConnectionStatus status) {
        final Duration since = status.sinceLastAnswer();
        final String model = runs.model().get().modelId();
        chipTip.setText(messages.get(MessageKey.SHELL_CONNECTION) + "\n"
                + messages.get(
                        MessageKey.SHELL_CONNECTION_TIP,
                        model.isBlank() ? none() : model,
                        since == null ? none() : clock(since),
                        String.valueOf(status.timeoutsRecently()),
                        String.valueOf(status.failuresRecently()),
                        rateOf(status.draftTokensPerSecond()),
                        rateOf(status.judgeTokensPerSecond())));
    }

    private String none() {
        return messages.get(MessageKey.SHELL_CONNECTION_NONE);
    }

    private String rateOf(final @Nullable Double tokensPerSecond) {
        return tokensPerSecond == null ? none() : String.valueOf(Math.round(tokensPerSecond));
    }

    private static String clock(final Duration duration) {
        final long seconds = duration.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
    }

    private void showControl(final RunStateView.@Nullable RunAction action) {
        shownControl = action;
        if (action == null) {
            control.setVisible(false);
            control.setManaged(false);
            return;
        }
        control.setText(action.label().in(messages));
        final String tip = action.tip().in(messages);
        // Installed only when the words change: the row is worked out again on every tick of the run's clocks.
        if (!tip.equals(shownTip)) {
            shownTip = tip;
            Tips.install(control, tip);
        }
        control.setDisable(!action.enabled());
        control.setVisible(true);
        control.setManaged(true);
    }
}
