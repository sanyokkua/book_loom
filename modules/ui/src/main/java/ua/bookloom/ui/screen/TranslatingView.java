package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ControlState;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.StatusRole;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * Builds the translating dashboard: the banner, the progress card, the four count tiles, the activity log and the run
 * controls.
 *
 * <p>Everything that follows a property is bound to it, and the bindings hold their sources weakly, so a screen that
 * is replaced on the next visit is not kept alive by the mirror it observed. The log's cells log nothing, since they
 * are refreshed on every scroll, and only change their text and role class as they are reused. The screen shows the
 * segments decided out of the total and the four counts, and nothing about a rate or a finishing time, because the
 * engine reports neither.
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
    private static final double BANNER_SPACING = 2;
    private static final double LOG_HEIGHT = 220;
    private static final List<String> ROLE_CLASSES =
            Arrays.stream(StatusRole.values()).map(StatusRole::styleClass).toList();

    /** One run control: its id suffix, its look, its label and which part of the view model's table decides it. */
    private record Control(
            String name, String styleClass, MessageKey label, Function<Controls, ControlState> availability) {}

    private static final Control START =
            new Control("start", "btn-primary", MessageKey.TRANSLATING_START, Controls::start);
    private static final Control NEW_RUN =
            new Control("new-run", "btn-primary", MessageKey.TRANSLATING_NEW_RUN, Controls::newRun);
    private static final Control PAUSE =
            new Control("pause", "btn-secondary", MessageKey.TRANSLATING_PAUSE, Controls::pause);
    private static final Control RESUME =
            new Control("resume", "btn-primary", MessageKey.TRANSLATING_RESUME, Controls::resume);
    private static final Control STOP = new Control("stop", "btn-ghost", MessageKey.TRANSLATING_STOP, Controls::stop);

    static TranslatingDashboard build(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final Messages messages,
            final Runnable openSettings) {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(openSettings, "openSettings");
        log.debug("building the translating dashboard");
        final TranslatingDashboard.Banner banner = banner(messages, openSettings);
        final ListView<LogEntry> logList = logList(mirror, messages);
        final VBox screen = new VBox(
                SCREEN_SPACING,
                banner.box(),
                progressCard(mirror, messages),
                tiles(mirror, messages),
                logCard(logList, messages),
                actions(viewModel, messages));
        return new TranslatingDashboard(screen, banner, logList, messages);
    }

    private static TranslatingDashboard.Banner banner(final Messages messages, final Runnable openSettings) {
        final Label icon = new Label();
        icon.getStyleClass().add("banner-icon");
        final Label title = wrapped("banner-title");
        final Label text = wrapped("banner-text");
        final Button settings = new Button(messages.get(MessageKey.TRANSLATING_OPEN_SETTINGS));
        settings.setId("translating-open-settings");
        settings.getStyleClass().add("btn-secondary");
        settings.setOnAction(event -> openSettings.run());
        settings.setVisible(false);
        settings.setManaged(false);
        final HBox box = new HBox(icon, new VBox(BANNER_SPACING, title, text, settings));
        box.setId("translating-banner");
        title.setId("translating-banner-title");
        text.setId("translating-banner-text");
        box.setAlignment(Pos.TOP_LEFT);
        box.getStyleClass().add("banner");
        return new TranslatingDashboard.Banner(box, icon, title, text, settings);
    }

    private static Node progressCard(final StateMirror mirror, final Messages messages) {
        final ProgressBar bar = new ProgressBar();
        bar.setId("translating-progress");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(mirror.progressFraction());
        final Label processed = boundLabel(
                "translating-progress-text",
                "muted",
                () -> messages.get(
                        MessageKey.TRANSLATING_PROGRESS,
                        mirror.accepted().get() + mirror.flagged().get(),
                        mirror.total().get()),
                mirror.accepted(),
                mirror.flagged(),
                mirror.total());
        final Label remaining = boundLabel(
                "translating-remaining-text",
                "muted",
                () -> messages.get(
                        MessageKey.TRANSLATING_SEGMENTS_REMAINING,
                        mirror.remaining().get()),
                mirror.remaining());
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final VBox card = new VBox(CARD_SPACING, bar, new HBox(ACTION_SPACING, processed, spacer, remaining));
        card.setId("translating-progress-card");
        card.getStyleClass().add("card");
        return card;
    }

    private static Node tiles(final StateMirror mirror, final Messages messages) {
        final HBox row = new HBox(
                TILE_SPACING,
                tile("accepted", mirror.accepted(), MessageKey.TRANSLATING_COUNT_ACCEPTED, messages),
                tile("flagged", mirror.flagged(), MessageKey.TRANSLATING_COUNT_FLAGGED, messages),
                tile("remaining", mirror.remaining(), MessageKey.TRANSLATING_COUNT_REMAINING, messages),
                tile("total", mirror.total(), MessageKey.TRANSLATING_COUNT_TOTAL, messages));
        row.setId("translating-tiles");
        return row;
    }

    private static Node tile(
            final String name, final ReadOnlyIntegerProperty count, final MessageKey caption, final Messages messages) {
        final NumberFormat grouping = NumberFormat.getIntegerInstance(messages.locale());
        final Label number =
                boundLabel("translating-count-" + name, "stat-number", () -> grouping.format(count.get()), count);
        return tile("translating-tile-" + name, number, messages.get(caption));
    }

    /** A count tile: the number above its caption, on the stat surface, sharing its row equally. */
    static VBox tile(final String id, final Label number, final String caption) {
        final Label label = new Label(caption);
        label.getStyleClass().add("stat-caption");
        final VBox tile = new VBox(number, label);
        tile.setId(id);
        tile.getStyleClass().add("stat");
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    private static Node logCard(final ListView<LogEntry> logList, final Messages messages) {
        final Label heading = new Label(messages.get(MessageKey.TRANSLATING_LOG_TITLE));
        heading.getStyleClass().add("card-title");
        final VBox card = new VBox(CARD_SPACING, heading, logList);
        card.setId("translating-log-card");
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private static ListView<LogEntry> logList(final StateMirror mirror, final Messages messages) {
        final ListView<LogEntry> list = new ListView<>(mirror.activityLog());
        list.setId("translating-log");
        list.getStyleClass().add("activity-log");
        list.setPrefHeight(LOG_HEIGHT);
        list.setFocusTraversable(false);
        final Label empty = new Label(messages.get(MessageKey.TRANSLATING_LOG_EMPTY));
        empty.getStyleClass().add("muted");
        list.setPlaceholder(empty);
        list.setCellFactory(view -> new EntryCell(messages));
        VBox.setVgrow(list, Priority.ALWAYS);
        return list;
    }

    private static Node actions(final TranslatingViewModel viewModel, final Messages messages) {
        final ReadOnlyObjectProperty<Controls> controls = viewModel.controls();
        final HBox row = new HBox(
                ACTION_SPACING,
                control(START, viewModel::start, controls, messages),
                control(NEW_RUN, viewModel::start, controls, messages),
                control(PAUSE, viewModel::pause, controls, messages),
                control(RESUME, viewModel::resume, controls, messages),
                control(STOP, viewModel::stop, controls, messages));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
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

    private static Label boundLabel(
            final String id, final String styleClass, final Callable<String> text, final Observable... sources) {
        final Label label = new Label();
        label.setId(id);
        label.getStyleClass().add(styleClass);
        label.textProperty().bind(Bindings.createStringBinding(text, sources));
        return label;
    }

    private static Label wrapped(final String styleClass) {
        final Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** Draws one log entry as its mark and its catalogue message, in the role's colour, and does nothing else. */
    private static final class EntryCell extends ListCell<LogEntry> {

        private final Messages messages;

        EntryCell(final Messages messages) {
            this.messages = messages;
        }

        @Override
        protected void updateItem(final @Nullable LogEntry entry, final boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll(ROLE_CLASSES);
            if (empty || entry == null) {
                setText(null);
                return;
            }
            setText(entry.kind().mark() + " "
                    + messages.get(entry.messageKey(), entry.args().toArray()));
            getStyleClass().add(entry.role().styleClass());
        }
    }
}
