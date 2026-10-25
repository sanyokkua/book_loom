package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.collections.SetChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.ResultsView;
import ua.bookloom.ui.state.StageRows;
import ua.bookloom.ui.state.StepEntry;

/**
 * The Results view of the progress dialog, shown after every Names &amp; style operation: a header with the
 * operation's counts, steps and time, a Changes tab with each row's term, kind of change, before, after and reason and a
 * Revert on each, a Steps tab with the steps the operation took, and Undo all, Show changed rows and Done in the
 * footer. An operation that changed nothing, or that the person stopped, says so in one plain line. Escape is Done.
 * Shown in the shell's modal host, so the stylesheet and the active theme block reach it. FX thread only.
 */
@Slf4j
final class ProgressResultsCard extends ModalCard {

    static final String CARD_ID = "results-card";

    private static final int MAX_UNTARGETED = 20;
    private static final double MAX_LIST_HEIGHT = 420;
    private static final double STEP_ROW_HEIGHT = 44;

    private final ResultsView view;
    private final ChangeResults results;
    private final Messages messages;

    ProgressResultsCard(final ResultsView view, final Messages messages) {
        super(Size.XL);
        this.view = Objects.requireNonNull(view, "view");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.results = view.results();
        setId(CARD_ID);
        getStyleClass().addAll("dialog-card", "elevation-lg");
        setHeader(header());
        setContent(tabs());
        buttons();
        setOnEscape(view.onDone());
        log.info(
                "results of {} shown: {} rows, {} steps, {}",
                results.operation(),
                results.rows().size(),
                results.steps().done(),
                results.elapsed());
    }

    private Node header() {
        final Label title = new Label(messages.get(
                MessageKey.RESULTS_TITLE, messages.get(results.operation().label())));
        title.setId("progress-title");
        title.getStyleClass().add("dialog-title");
        final Label subtitle = new Label(subtitleText());
        subtitle.setId("progress-subtitle");
        subtitle.getStyleClass().add("dialog-sub");
        final VBox header = new VBox(title, subtitle);
        header.getStyleClass().addAll("dialog-h", "progress-header");
        return header;
    }

    private String subtitleText() {
        final int added = results.count(ChangeKind.ADDED);
        final int removed = results.count(ChangeKind.REMOVED);
        final int changed = results.count(ChangeKind.CHANGED);
        if (results.steps().isEmpty()) {
            return messages.get(MessageKey.PROGRESS_RESULTS_COUNTS, added, removed, changed);
        }
        return messages.get(
                MessageKey.PROGRESS_RESULTS_SUBTITLE,
                added,
                removed,
                changed,
                results.steps().done(),
                DurationText.spelled(messages, results.elapsed()));
    }

    private TabPane tabs() {
        final Tab changes = new Tab(messages.get(MessageKey.PROGRESS_RESULTS_TAB_CHANGES), changesBody());
        final Tab steps = new Tab(messages.get(MessageKey.PROGRESS_RESULTS_TAB_STEPS), stepsList());
        changes.setClosable(false);
        steps.setClosable(false);
        final TabPane tabs = new TabPane(changes, steps);
        tabs.setId("results-tabs");
        return tabs;
    }

    private Node changesBody() {
        final VBox body = new VBox();
        body.getStyleClass().add("busy-body");
        switch (results.outcome()) {
            case STOPPED -> body.getChildren().add(line(MessageKey.RESULTS_STOPPED));
            case NOTHING -> body.getChildren().add(line(MessageKey.RESULTS_NOTHING));
            case CHANGED -> body.getChildren().addAll(table());
        }
        if (!results.untargeted().isEmpty()) {
            body.getChildren().add(untargeted(results.untargeted()));
        }
        return body;
    }

    // The names the pass could not name in the target language are the person's to give, so they are listed, not kept
    // silently; a long list shows its first names and says how many more there are.
    private Label untargeted(final List<String> terms) {
        final String shown = String.join(", ", terms.subList(0, Math.min(terms.size(), MAX_UNTARGETED)));
        final Label line = new Label(messages.get(
                MessageKey.RESULTS_UNTARGETED,
                terms.size(),
                terms.size() > MAX_UNTARGETED ? shown + Messages.ELLIPSIS : shown));
        line.setId("results-untargeted");
        line.setWrapText(true);
        line.setMaxWidth(ResultRows.WIDTH);
        line.getStyleClass().add("dialog-text");
        return line;
    }

    private Label line(final MessageKey text) {
        final Label line = new Label(messages.get(text));
        line.setId("results-line");
        line.setWrapText(true);
        line.getStyleClass().add("dialog-text");
        return line;
    }

    private List<Node> table() {
        final ResultRows rows = new ResultRows(messages, results);
        final ListView<Object> list = new ListView<>(FXCollections.observableArrayList(rows.items()));
        list.setId("results-list");
        list.getStyleClass().add("results-list");
        list.setFixedCellSize(ResultRows.ROW_HEIGHT);
        list.setCellFactory(_ -> rows.cell());
        list.setPrefWidth(ResultRows.WIDTH);
        list.setPrefHeight(Math.min(MAX_LIST_HEIGHT, rows.items().size() * ResultRows.ROW_HEIGHT + 2));
        final SetChangeListener<Long> onReverted = change -> list.refresh();
        results.reverted().addListener(onReverted);
        results.working().addListener((observed, was, now) -> list.refresh());
        final List<Node> parts = new ArrayList<>();
        parts.add(rows.headings());
        parts.add(list);
        return parts;
    }

    // The stages with their requests; work that planned no stage lists the steps as they were announced.
    private ListView<StepEntry> stepsList() {
        final List<StepEntry> stages = StageRows.of(results.steps()).rows();
        final ListView<StepEntry> list = new ListView<>(FXCollections.observableArrayList(
                stages.isEmpty() ? results.steps().entries() : stages));
        list.setId("results-steps");
        list.getStyleClass().add("progress-steps");
        list.setFixedCellSize(STEP_ROW_HEIGHT);
        list.setCellFactory(_ -> new StepCell(messages, null));
        return list;
    }

    private void buttons() {
        final ButtonType done =
                new ButtonType(messages.get(MessageKey.PROGRESS_RESULTS_DONE), ButtonBar.ButtonData.OTHER);
        if (results.outcome() == ChangeResults.Outcome.CHANGED) {
            final ButtonType undo =
                    new ButtonType(messages.get(MessageKey.RESULTS_UNDO_ALL), ButtonBar.ButtonData.LEFT);
            final ButtonType show =
                    new ButtonType(messages.get(MessageKey.RESULTS_SHOW_CHANGED), ButtonBar.ButtonData.OTHER);
            getButtonTypes().setAll(undo, show, done);
            wireUndo(button(undo, "results-undo-all", MessageKey.RESULTS_UNDO_ALL_TIP, "btn-danger-outline"));
            wireShow(button(show, "results-show-changed", MessageKey.RESULTS_SHOW_CHANGED_TIP, "btn-secondary"));
        } else {
            getButtonTypes().setAll(done);
        }
        button(done, "results-done", MessageKey.PROGRESS_RESULTS_DONE_TIP, "btn-primary")
                .setOnAction(event -> {
                    log.info("results: done pressed");
                    view.onDone().run();
                });
    }

    // Show changed rows filters the screen behind, then closes; it is off when only removed rows changed, which the
    // tables no longer hold.
    private void wireShow(final Button showButton) {
        showButton.setOnAction(event -> {
            log.info("results: show changed rows pressed");
            view.onShowChanged().run();
            view.onDone().run();
        });
        showButton.setDisable(
                results.count(ChangeKind.REMOVED) == results.rows().size());
    }

    // Undo all is on only while some row can still be put back and no revert runs.
    private void wireUndo(final Button undoButton) {
        undoButton.setOnAction(event -> results.undoAll());
        final Runnable sync = () -> undoButton.setDisable(results.working().get() || !results.hasRowsToRevert());
        results.working().addListener((observed, was, now) -> sync.run());
        results.reverted().addListener((SetChangeListener<Long>) change -> sync.run());
        sync.run();
    }

    private Button button(final ButtonType type, final String id, final MessageKey tip, final String styleClass) {
        final Button button = (Button) lookupButton(type);
        button.setId(id);
        Tips.install(messages, button, tip);
        button.getStyleClass().add(styleClass);
        return button;
    }
}
