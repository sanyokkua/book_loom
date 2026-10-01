package ua.bookloom.ui.screen;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.LiveChunkPanel;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.TaggedLog;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ControlState;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.RunInterventions;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * Builds the translating dashboard: the banner, the ready card before a run, the progress card and tiles while one
 * lives, the outcome once it ends, the live panel and log, and the run controls.
 *
 * <p>Everything that follows a property is bound to it, and the bindings hold their sources weakly, so a screen that
 * is replaced on the next visit is not kept alive by the mirror it observed. Which cards show is decided by the run
 * state alone: the ready card while idle, the progress card and tiles while a run is under way or stopped, the
 * outcome once it completed or failed. The log's cells log nothing, since they are refreshed on every scroll.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingView {

    private static final double SCREEN_SPACING = 14;
    private static final double CARD_SPACING = 8;
    private static final double TILE_SPACING = 12;
    private static final double ACTION_SPACING = 10;
    private static final double LOG_HEIGHT = 260;

    private static final Set<RunState> UNDER_WAY =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPING, RunState.STOPPED);
    private static final Set<RunState> ENDED = Set.of(RunState.COMPLETED, RunState.FAILED);
    private static final Set<RunState> REVIEWABLE =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPED, RunState.COMPLETED);
    private static final Set<RunState> WATCHED = EnumSet.complementOf(EnumSet.of(RunState.IDLE));

    /** One run control: its id suffix, its look, its label and which part of the view model's table decides it. */
    private record Control(
            String name,
            String styleClass,
            MessageKey label,
            MessageKey tip,
            Function<Controls, ControlState> availability) {}

    private static final Control START = new Control(
            "start", "btn-primary", MessageKey.TRANSLATING_START, MessageKey.TRANSLATING_START_TIP, Controls::start);
    private static final Control PAUSE = new Control(
            "pause", "btn-secondary", MessageKey.TRANSLATING_PAUSE, MessageKey.TRANSLATING_PAUSE_TIP, Controls::pause);
    private static final Control RESUME = new Control(
            "resume",
            "btn-primary",
            MessageKey.TRANSLATING_RESUME,
            MessageKey.TRANSLATING_RESUME_TIP,
            Controls::resume);
    private static final Control STOP = new Control(
            "stop", "btn-ghost", MessageKey.TRANSLATING_STOP, MessageKey.TRANSLATING_STOP_TIP, Controls::stop);

    /** One action the banner may offer: its node id, its label, its hover explanation and whether it is a ghost. */
    private record BannerAction(String id, MessageKey label, MessageKey tip, boolean ghost) {}

    private static final BannerAction RETRY_NOW = new BannerAction(
            "translating-retry-now", MessageKey.TRANSLATING_RETRY_NOW, MessageKey.TRANSLATING_RETRY_NOW_TIP, false);
    private static final BannerAction SKIP_SEGMENT = new BannerAction(
            "translating-skip-segment",
            MessageKey.TRANSLATING_SKIP_SEGMENT,
            MessageKey.TRANSLATING_SKIP_SEGMENT_TIP,
            false);
    private static final BannerAction OPEN_SETTINGS = new BannerAction(
            "translating-open-settings",
            MessageKey.TRANSLATING_OPEN_SETTINGS,
            MessageKey.TRANSLATING_OPEN_SETTINGS_TIP,
            false);
    private static final BannerAction STAY_PAUSED = new BannerAction(
            "translating-stay-paused",
            MessageKey.TRANSLATING_STAY_PAUSED,
            MessageKey.TRANSLATING_STAY_PAUSED_TIP,
            true);
    private static final BannerAction SEND_AGAIN = new BannerAction(
            "translating-send-again", MessageKey.TRANSLATING_RETRY_CALL, MessageKey.TRANSLATING_RETRY_CALL_TIP, false);
    private static final BannerAction PAUSE_STUCK = new BannerAction(
            "translating-pause-stuck",
            MessageKey.TRANSLATING_PAUSE_STUCK,
            MessageKey.TRANSLATING_PAUSE_STUCK_TIP,
            true);

    /**
     * What the screen's own buttons do that the view model does not: leave for another step, open the settings, or
     * open the review panel, whose view model counts the flagged segments and whose retry asks its note in a card.
     */
    record Exits(
            Navigator navigator,
            Runnable openSettings,
            ReviewViewModel review,
            RetryWithNoteDialog retryDialog,
            ReviewPauseFollower pauses,
            RunInterventions interventions) {}

    static TranslatingDashboard build(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final CurrentProject current,
            final LanguageNames names,
            final Messages messages,
            final Exits exits) {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(exits, "exits");
        log.debug("building the translating dashboard");
        final ReadOnlyObjectProperty<RunState> state = mirror.runState();
        final TranslatingDashboard.LiveBanner banner = banner(viewModel, messages, exits);
        final TaggedLog logList = new TaggedLog(mirror.activityLog(), messages);
        final ObservableValue<String> sourceName =
                languageName(current, BookBrief::sourceLanguage, MessageKey.LIVE_SOURCE_FALLBACK, names, messages);
        final ObservableValue<String> targetName =
                languageName(current, BookBrief::targetLanguage, MessageKey.LIVE_TARGET_FALLBACK, names, messages);
        final ReviewPanel review = reviewPanel(exits, sourceName, targetName, messages, state);
        final VBox screen = new VBox(
                SCREEN_SPACING,
                head(banner, actions(viewModel, mirror, exits.review(), review, messages)),
                StateVisibility.shownIn(
                        TranslatingReadyCard.build(viewModel, mirror, current, messages), state, Set.of(RunState.IDLE)),
                StateVisibility.shownIn(TranslatingFigures.progressCard(mirror, messages), state, UNDER_WAY),
                StateVisibility.shownIn(TranslatingFigures.runningTiles(mirror, messages), state, UNDER_WAY),
                StateVisibility.shownIn(TranslatingFigures.outcomeCard(mirror, messages), state, ENDED),
                StateVisibility.shownIn(watch(logList, mirror, sourceName, targetName, messages), state, WATCHED),
                review,
                footer(mirror, messages, exits.navigator()));
        return new TranslatingDashboard(screen, banner, messages, mirror);
    }

    // The run controls sit under the banner, so they stay in view above the live panel during a long stall.
    private static Node head(final TranslatingDashboard.LiveBanner banner, final Node actions) {
        final VBox head = new VBox(CARD_SPACING, banner.banner(), actions);
        head.setId("translating-head");
        return head;
    }

    private static TranslatingDashboard.LiveBanner banner(
            final TranslatingViewModel viewModel, final Messages messages, final Exits exits) {
        final Banner banner = new Banner("translating-banner", Banner.Role.INFO, "", "", "");
        final RunInterventions interventions = exits.interventions();
        final Button retry = action(RETRY_NOW, messages, () -> {
            log.debug("retry now pressed: resuming the paused run");
            viewModel.resume();
        });
        final Button skip = action(SKIP_SEGMENT, messages, interventions::skipSegment);
        final Button settings = action(OPEN_SETTINGS, messages, exits.openSettings());
        final Button stay = action(STAY_PAUSED, messages, () -> {});
        final Button again = action(SEND_AGAIN, messages, interventions::sendAgain);
        final Button pause = action(PAUSE_STUCK, messages, viewModel::pause);
        final HBox row = new HBox(ACTION_SPACING, retry, skip, again, settings, pause, stay);
        row.setId("translating-banner-actions");
        row.setAlignment(Pos.CENTER_LEFT);
        banner.addDetail(row);
        return new TranslatingDashboard.LiveBanner(banner, retry, skip, settings, stay, again, pause);
    }

    // Hidden until a look offers it; the ghost ones take only their own class, as the plain secondary look would
    // otherwise frame them.
    private static Button action(final BannerAction spec, final Messages messages, final Runnable run) {
        final Button button = Tips.install(messages, new Button(messages.get(spec.label())), spec.tip());
        button.setId(spec.id());
        if (spec.ghost()) {
            button.getStyleClass().setAll("btn-ghost");
        } else {
            button.getStyleClass().add("btn-secondary");
        }
        button.setOnAction(event -> run.run());
        Banner.setActionShown(button, false);
        return button;
    }

    private static Node logCard(final TaggedLog logList, final Messages messages) {
        final Label heading = new Label(messages.get(MessageKey.TRANSLATING_LOG_TITLE));
        heading.getStyleClass().add("card-title");
        final ToggleButton errorsOnly = new ToggleButton(messages.get(MessageKey.TRANSLATING_LOG_ERRORS_ONLY));
        errorsOnly.setId("translating-log-errors-only");
        errorsOnly.getStyleClass().add("review-chip");
        Tips.install(messages, errorsOnly, MessageKey.TRANSLATING_LOG_ERRORS_ONLY_TIP);
        errorsOnly.selectedProperty().addListener((observed, was, now) -> {
            log.debug("activity log errors-only {}", now);
            logList.errorsOnlyProperty().set(now);
        });
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(CARD_SPACING, heading, spacer, jumpToLatest(logList, messages), errorsOnly);
        header.setAlignment(Pos.CENTER_LEFT);
        logList.setId("translating-log");
        logList.setPrefHeight(LOG_HEIGHT);
        VBox.setVgrow(logList, Priority.ALWAYS);
        final VBox card = new VBox(CARD_SPACING, header, logList);
        card.setId("translating-log-card");
        card.getStyleClass().add("card");
        return card;
    }

    // Offered only while the person has scrolled the log up, which stops it following its newest line.
    private static Button jumpToLatest(final TaggedLog logList, final Messages messages) {
        final Button jump = Tips.install(
                messages,
                new Button(messages.get(MessageKey.TRANSLATING_LOG_JUMP)),
                MessageKey.TRANSLATING_LOG_JUMP_TIP);
        jump.setId("translating-log-jump");
        jump.getStyleClass().add("review-chip");
        jump.visibleProperty().bind(logList.followingProperty().not());
        jump.managedProperty().bind(jump.visibleProperty());
        jump.setOnAction(event -> logList.jumpToLatest());
        return jump;
    }

    private static Node watch(
            final TaggedLog logList,
            final StateMirror mirror,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        final LiveChunkPanel live =
                new LiveChunkPanel("translating-live-card", mirror.live().liveRows(), sourceName, targetName, messages);
        final Node logCard = logCard(logList, messages);
        final VBox row = new VBox(TILE_SPACING, live, logCard);
        row.setId("translating-watch");
        VBox.setVgrow(row, Priority.ALWAYS);
        return row;
    }

    private static ObservableValue<String> languageName(
            final CurrentProject current,
            final Function<BookBrief, @Nullable String> tagOf,
            final MessageKey fallback,
            final LanguageNames names,
            final Messages messages) {
        return Bindings.createStringBinding(
                () -> {
                    final BookBrief brief = current.brief().get();
                    final String tag = brief == null ? null : tagOf.apply(brief);
                    return tag == null ? messages.get(fallback) : names.nameOf(tag, messages.locale());
                },
                current.brief());
    }

    // An open panel is shown only while the run is in a state that offers Review flagged, so it never outlives its
    // button.
    private static ReviewPanel reviewPanel(
            final Exits exits,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages,
            final ReadOnlyObjectProperty<RunState> state) {
        final ReviewPanel panel =
                new ReviewPanel(exits.review(), sourceName, targetName, messages, exits.retryDialog(), exits.pauses());
        panel.visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> panel.openProperty().get() && REVIEWABLE.contains(state.get()),
                        panel.openProperty(),
                        state));
        panel.managedProperty().bind(panel.visibleProperty());
        return panel;
    }

    private static Node actions(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final ReviewViewModel review,
            final ReviewPanel panel,
            final Messages messages) {
        final ReadOnlyObjectProperty<Controls> controls = viewModel.controls();
        final HBox row = new HBox(
                ACTION_SPACING,
                control(START, viewModel::start, controls, messages),
                control(PAUSE, viewModel::pause, controls, messages),
                control(RESUME, viewModel::resume, controls, messages),
                control(STOP, viewModel::stop, controls, messages),
                reviewFlagged(mirror, review, panel, messages));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button reviewFlagged(
            final StateMirror mirror, final ReviewViewModel review, final ReviewPanel panel, final Messages messages) {
        final Button button = new Button();
        button.setId("translating-review-flagged");
        Tips.install(messages, button, MessageKey.TRANSLATING_REVIEW_FLAGGED_TIP);
        button.getStyleClass().add("btn-ghost");
        button.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                MessageKey.TRANSLATING_REVIEW_FLAGGED,
                                review.flaggedCount().get()),
                        review.flaggedCount()));
        button.setOnAction(event -> {
            log.debug(
                    "review flagged pressed with {} flagged segments: opening the panel",
                    review.flaggedCount().get());
            panel.setOpen(true);
        });
        return StateVisibility.shownIn(button, mirror.runState(), REVIEWABLE);
    }

    private static Node footer(final StateMirror mirror, final Messages messages, final Navigator navigator) {
        final StepFooter footer = StepFooter.of(
                new StepFooter.Action(
                        "translating-back",
                        messages.get(MessageKey.TRANSLATING_BACK),
                        messages.get(MessageKey.TRANSLATING_BACK_TIP),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.NAMES_STYLE)),
                new StepFooter.Action(
                        "translating-continue",
                        messages.get(MessageKey.TRANSLATING_CONTINUE),
                        messages.get(MessageKey.TRANSLATING_CONTINUE_TIP),
                        "btn-primary",
                        () -> navigator.navigate(ViewNames.EXPORT)));
        StateVisibility.shownIn(footer.forwardButton(), mirror.runState(), Set.of(RunState.COMPLETED));
        return footer;
    }

    private static Button control(
            final Control spec,
            final Runnable action,
            final ReadOnlyObjectProperty<Controls> controls,
            final Messages messages) {
        final Button button = Tips.install(messages, new Button(messages.get(spec.label())), spec.tip());
        button.setId("translating-" + spec.name());
        button.getStyleClass().add(spec.styleClass());
        button.setOnAction(event -> action.run());
        button.visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> spec.availability().apply(controls.get()).isShown(), controls));
        button.managedProperty().bind(button.visibleProperty());
        button.disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !spec.availability().apply(controls.get()).isEnabled(), controls));
        return button;
    }
}
