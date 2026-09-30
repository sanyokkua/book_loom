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
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ControlState;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.CurrentProject;
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
    private static final double LOG_HEIGHT = 220;
    private static final double WATCH_PANE_WIDTH = 300;

    private static final Set<RunState> UNDER_WAY =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPING, RunState.STOPPED);
    private static final Set<RunState> ENDED = Set.of(RunState.COMPLETED, RunState.FAILED);
    private static final Set<RunState> REVIEWABLE =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPED, RunState.COMPLETED);
    private static final Set<RunState> WATCHED = EnumSet.complementOf(EnumSet.of(RunState.IDLE));

    /** One run control: its id suffix, its look, its label and which part of the view model's table decides it. */
    private record Control(
            String name, String styleClass, MessageKey label, Function<Controls, ControlState> availability) {}

    private static final Control START =
            new Control("start", "btn-primary", MessageKey.TRANSLATING_START, Controls::start);
    private static final Control PAUSE =
            new Control("pause", "btn-secondary", MessageKey.TRANSLATING_PAUSE, Controls::pause);
    private static final Control RESUME =
            new Control("resume", "btn-primary", MessageKey.TRANSLATING_RESUME, Controls::resume);
    private static final Control STOP = new Control("stop", "btn-ghost", MessageKey.TRANSLATING_STOP, Controls::stop);

    /** What the screen's own buttons do that the view model does not: leave for another step, or open the settings. */
    record Exits(Navigator navigator, Runnable openSettings) {}

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
        final TranslatingDashboard.LiveBanner banner = banner(messages, exits.openSettings());
        final TaggedLog logList = new TaggedLog(mirror.activityLog(), messages);
        final VBox screen = new VBox(
                SCREEN_SPACING,
                banner.banner(),
                StateVisibility.shownIn(
                        TranslatingReadyCard.build(viewModel, mirror, current, messages), state, Set.of(RunState.IDLE)),
                StateVisibility.shownIn(TranslatingFigures.progressCard(mirror, messages), state, UNDER_WAY),
                StateVisibility.shownIn(TranslatingFigures.runningTiles(mirror, messages), state, UNDER_WAY),
                StateVisibility.shownIn(TranslatingFigures.outcomeCard(mirror, messages), state, ENDED),
                StateVisibility.shownIn(watch(logList, mirror, current, names, messages), state, WATCHED),
                actions(viewModel, mirror, messages),
                footer(mirror, messages, exits.navigator()));
        return new TranslatingDashboard(screen, banner, logList, messages, mirror);
    }

    private static TranslatingDashboard.LiveBanner banner(final Messages messages, final Runnable openSettings) {
        final Banner banner = new Banner("translating-banner", Banner.Role.INFO, "", "", "");
        final Button settings = banner.addAction(
                "translating-open-settings", messages.get(MessageKey.TRANSLATING_OPEN_SETTINGS), openSettings);
        Banner.setActionShown(settings, false);
        return new TranslatingDashboard.LiveBanner(banner, settings);
    }

    private static Node logCard(final TaggedLog logList, final Messages messages) {
        final Label heading = new Label(messages.get(MessageKey.TRANSLATING_LOG_TITLE));
        heading.getStyleClass().add("card-title");
        logList.setId("translating-log");
        logList.setPrefHeight(LOG_HEIGHT);
        VBox.setVgrow(logList, Priority.ALWAYS);
        final VBox card = new VBox(CARD_SPACING, heading, logList);
        card.setId("translating-log-card");
        card.getStyleClass().add("card");
        return card;
    }

    private static Node watch(
            final TaggedLog logList,
            final StateMirror mirror,
            final CurrentProject current,
            final LanguageNames names,
            final Messages messages) {
        final LiveChunkPanel live = new LiveChunkPanel(
                "translating-live-card",
                mirror.live().liveRows(),
                languageName(current, BookBrief::sourceLanguage, MessageKey.LIVE_SOURCE_FALLBACK, names, messages),
                languageName(current, BookBrief::targetLanguage, MessageKey.LIVE_TARGET_FALLBACK, names, messages),
                messages);
        final Node logCard = logCard(logList, messages);
        for (final Region side : new Region[] {live, (Region) logCard}) {
            side.setPrefWidth(WATCH_PANE_WIDTH);
            side.setMinWidth(0);
            HBox.setHgrow(side, Priority.ALWAYS);
        }
        final HBox row = new HBox(TILE_SPACING, live, logCard);
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

    private static Node actions(
            final TranslatingViewModel viewModel, final StateMirror mirror, final Messages messages) {
        final ReadOnlyObjectProperty<Controls> controls = viewModel.controls();
        final HBox row = new HBox(
                ACTION_SPACING,
                control(START, viewModel::start, controls, messages),
                control(PAUSE, viewModel::pause, controls, messages),
                control(RESUME, viewModel::resume, controls, messages),
                control(STOP, viewModel::stop, controls, messages),
                reviewFlagged(mirror, messages));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button reviewFlagged(final StateMirror mirror, final Messages messages) {
        final Button button = new Button();
        button.setId("translating-review-flagged");
        button.getStyleClass().add("btn-ghost");
        button.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                MessageKey.TRANSLATING_REVIEW_FLAGGED,
                                mirror.live().flaggedQueue().size()),
                        mirror.live().flaggedQueue()));
        button.disableProperty().bind(Bindings.isEmpty(mirror.live().flaggedQueue()));
        button.setOnAction(event -> log.debug(
                "review flagged pressed with {} flagged segments; the review panel is not built yet",
                mirror.live().flaggedQueue().size()));
        return StateVisibility.shownIn(button, mirror.runState(), REVIEWABLE);
    }

    private static Node footer(final StateMirror mirror, final Messages messages, final Navigator navigator) {
        final StepFooter footer = StepFooter.of(
                new StepFooter.Action(
                        "translating-back",
                        messages.get(MessageKey.TRANSLATING_BACK),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.NAMES_STYLE)),
                new StepFooter.Action(
                        "translating-continue",
                        messages.get(MessageKey.TRANSLATING_CONTINUE),
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
        final Button button = new Button(messages.get(spec.label()));
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
