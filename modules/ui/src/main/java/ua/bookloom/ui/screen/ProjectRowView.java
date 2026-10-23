package ua.bookloom.ui.screen;

import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.control.Overrun;
import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.QualityLabels;
import ua.bookloom.ui.state.LastOpened;
import ua.bookloom.ui.state.ProjectRow;
import ua.bookloom.ui.state.ProjectStatus;
import ua.bookloom.ui.state.ProjectsViewModel;

/** One translation's row on the Projects screen: what it is, how far it got and what can be done with it. */
final class ProjectRowView {

    private static final double ROW_SPACING = 12;
    private static final double INFO_SPACING = 4;
    private static final double CHIP_SPACING = 6;
    private static final double BAR_WIDTH = 120;
    private static final double PROGRESS_WIDTH = 170;
    private static final double PERCENT_SCALE = 100;
    private static final String SEPARATOR = " · ";

    private final Messages messages;
    private final ProjectsViewModel viewModel;
    private final LastOpened lastOpened;

    ProjectRowView(final Messages messages, final ProjectsViewModel viewModel, final LastOpened lastOpened) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.lastOpened = Objects.requireNonNull(lastOpened, "lastOpened");
    }

    Node build(final ProjectRow row) {
        final ProjectSummary summary = row.summary();
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox box = new HBox(
                ROW_SPACING,
                info(row),
                spacer,
                progress(row),
                fixed(StateMarker.of(
                        row.status().meaning(), messages.get(row.status().messageKey()))),
                action(row),
                menu(row));
        box.setId("projects-row-" + summary.project().id());
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Region fixed(final Region region) {
        region.setMinWidth(Region.USE_PREF_SIZE);
        return region;
    }

    private Node info(final ProjectRow row) {
        final ProjectSummary summary = row.summary();
        final Label name = new Label(summary.project().name());
        name.getStyleClass().add("provider-name");
        name.setMinWidth(0);
        Overrun.install(name);
        final HBox heading = new HBox(CHIP_SPACING, name);
        heading.setAlignment(Pos.CENTER_LEFT);
        if (summary.project().nameOrigin() == NameOrigin.USER) {
            heading.getChildren().add(chip("projects-named-" + summary.project().id(), MessageKey.PROJECTS_ROW_NAMED));
        }
        if (row.open()) {
            heading.getChildren().add(chip("projects-open-" + summary.project().id(), MessageKey.PROJECTS_ROW_OPEN));
        }
        final Label meta = new Label(metaOf(row));
        meta.getStyleClass().add("muted");
        Overrun.install(meta);
        final VBox info = new VBox(INFO_SPACING, heading, meta);
        info.setMinWidth(0);
        return info;
    }

    private Label chip(final String id, final MessageKey word) {
        final Label chip = new Label(messages.get(word));
        chip.setId(id);
        chip.getStyleClass().addAll("chip", "chip-neutral");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, chip, tipOf(word));
        return chip;
    }

    private static MessageKey tipOf(final MessageKey word) {
        return word == MessageKey.PROJECTS_ROW_NAMED
                ? MessageKey.PROJECTS_ROW_NAMED_TIP
                : MessageKey.PROJECTS_ROW_OPEN_TIP;
    }

    private String metaOf(final ProjectRow row) {
        final BookBrief brief = row.summary().project().brief();
        final String source = tag(brief.sourceLanguage());
        final String target = tag(brief.targetLanguage());
        final @Nullable RunRecord run = row.summary().latestRun();
        final @Nullable ModelSelection model = run == null ? null : run.model();
        if (run == null) {
            return messages.get(MessageKey.PROJECTS_ROW_NOT_STARTED, source, target);
        }
        final String quality = messages.get(QualityLabels.of(brief));
        if (model == null) {
            return String.join(SEPARATOR, source + " → " + target, quality);
        }
        return messages.get(MessageKey.PROJECTS_ROW_META, source, target, row.providerName(), model.modelId(), quality);
    }

    private static String tag(final @Nullable String language) {
        return language == null ? "?" : language.toUpperCase(Locale.ROOT);
    }

    private Node progress(final ProjectRow row) {
        final ProgressBar bar = new ProgressBar(row.percent() / PERCENT_SCALE);
        bar.setPrefWidth(BAR_WIDTH);
        final Label figure = new Label(messages.get(
                MessageKey.PROJECTS_ROW_PROGRESS,
                row.percent(),
                lastOpened.text(row.summary().project().lastOpenedAt())));
        figure.getStyleClass().add("muted");
        final VBox column = new VBox(INFO_SPACING, bar, figure);
        column.setAlignment(Pos.CENTER_RIGHT);
        column.setPrefWidth(PROGRESS_WIDTH);
        column.setMinWidth(PROGRESS_WIDTH);
        return column;
    }

    private Button action(final ProjectRow row) {
        final boolean continues =
                !row.open() && (row.status() == ProjectStatus.TRANSLATING || row.status() == ProjectStatus.FAILED);
        final Button button = new Button(
                messages.get(continues ? MessageKey.PROJECTS_ROW_CONTINUE : MessageKey.PROJECTS_ROW_OPEN_ACTION));
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setId("projects-continue-" + row.summary().project().id());
        button.getStyleClass().add(continues ? "btn-primary" : "btn-secondary");
        Tips.install(
                messages,
                button,
                continues ? MessageKey.PROJECTS_ROW_CONTINUE_TIP : MessageKey.PROJECTS_ROW_OPEN_ACTION_TIP);
        button.setOnAction(event -> viewModel.open(row));
        return button;
    }

    private MenuButton menu(final ProjectRow row) {
        final MenuButton more = new MenuButton(messages.get(MessageKey.PROJECTS_ROW_MORE));
        more.setId("projects-more-" + row.summary().project().id());
        more.setAccessibleText(messages.get(MessageKey.PROJECTS_ROW_MORE_TIP));
        more.getStyleClass().addAll("btn-ghost", "menu-more", "menu-more-bare");
        more.setMinWidth(Region.USE_PREF_SIZE);
        Tips.install(messages, more, MessageKey.PROJECTS_ROW_MORE_TIP);
        more.getItems()
                .addAll(
                        item(MessageKey.PROJECTS_MENU_RENAME, () -> viewModel.rename(row)),
                        item(MessageKey.PROJECTS_MENU_SHOW_BOOK, () -> viewModel.showBookFile(row)),
                        item(MessageKey.PROJECTS_MENU_REMOVE, () -> viewModel.remove(row)));
        return more;
    }

    private MenuItem item(final MessageKey caption, final Runnable action) {
        final MenuItem item = new MenuItem(messages.get(caption));
        item.setOnAction(event -> action.run());
        return item;
    }
}
