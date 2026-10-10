package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.collections.SetChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.CallsSection;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeResults;

/**
 * The card shown after every Names &amp; style operation: what it added, removed and changed, each row with its term,
 * the row before and after and the model's reason when it gave one, a Revert on each row, Undo all, a button that
 * leaves only the changed rows in the table, and the operation's model calls folded away. An operation that changed
 * nothing, or that the person stopped, says so in one plain line. Shown in the shell's modal host, so the stylesheet and
 * the active theme block reach it. FX thread only.
 */
@Slf4j
@Singleton
public final class ChangeResultsDialog {

    static final String CARD_ID = "results-card";
    static final String LIST_ID = "results-list";
    static final String LINE_ID = "results-line";
    static final String SUMMARY_ID = "results-summary";
    static final String CALLS_ID = "results-calls";
    static final String CLOSE_ID = "results-close";
    static final String SHOW_ID = "results-show-changed";
    static final String UNDO_ID = "results-undo-all";

    private static final double MAX_LIST_HEIGHT = 420;
    private static final double LIST_WIDTH = ResultRows.WIDTH;

    private final ModalHost modalHost;
    private final Messages messages;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     */
    @Inject
    public ChangeResultsDialog(final ModalHost modalHost, final Messages messages) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Shows the results of an operation. FX thread only.
     *
     * @param results what the operation did and the means to put it back
     * @param showChanged run, after the card is closed, when the person asks to see only the changed rows
     */
    public void show(final ChangeResults results, final Runnable showChanged) {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(showChanged, "showChanged");
        log.debug("showing the results of {}: {}", results.operation(), results.outcome());
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        card.setHeader(header(results));
        card.setContent(body(results));
        buttons(card, results, showChanged);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        modalHost.show(card, false);
    }

    private Node header(final ChangeResults results) {
        final Label title = new Label(messages.get(
                MessageKey.RESULTS_TITLE, messages.get(results.operation().label())));
        title.setId("results-title");
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        if (results.outcome() == ChangeResults.Outcome.CHANGED) {
            final Label summary = new Label(messages.get(
                    MessageKey.RESULTS_SUMMARY,
                    results.count(ChangeKind.ADDED),
                    results.count(ChangeKind.REMOVED),
                    results.count(ChangeKind.CHANGED)));
            summary.setId(SUMMARY_ID);
            summary.getStyleClass().add("dialog-sub");
            header.getChildren().add(summary);
        }
        return header;
    }

    private Node body(final ChangeResults results) {
        final VBox body = new VBox();
        body.getStyleClass().add("busy-body");
        switch (results.outcome()) {
            case STOPPED -> body.getChildren().add(line(MessageKey.RESULTS_STOPPED));
            case NOTHING -> body.getChildren().add(line(MessageKey.RESULTS_NOTHING));
            case CHANGED -> body.getChildren().addAll(table(results));
        }
        final CallsSection calls = new CallsSection(CALLS_ID, "results-call", messages);
        calls.show(results.calls());
        body.getChildren().add(calls);
        return body;
    }

    private Label line(final MessageKey text) {
        final Label line = new Label(messages.get(text));
        line.setId(LINE_ID);
        line.setWrapText(true);
        line.getStyleClass().add("dialog-text");
        return line;
    }

    private List<Node> table(final ChangeResults results) {
        final ResultRows rows = new ResultRows(messages, results);
        final ListView<Object> list = new ListView<>(FXCollections.observableArrayList(rows.items()));
        list.setId(LIST_ID);
        list.getStyleClass().add("results-list");
        list.setFixedCellSize(ResultRows.ROW_HEIGHT);
        list.setCellFactory(_ -> rows.cell());
        list.setPrefWidth(LIST_WIDTH);
        list.setPrefHeight(Math.min(MAX_LIST_HEIGHT, rows.items().size() * ResultRows.ROW_HEIGHT + 2));
        final SetChangeListener<Long> onReverted = change -> list.refresh();
        results.reverted().addListener(onReverted);
        results.working().addListener((observed, was, now) -> list.refresh());
        final List<Node> parts = new ArrayList<>();
        parts.add(rows.headings());
        parts.add(list);
        return parts;
    }

    private void buttons(final DialogPane card, final ChangeResults results, final Runnable showChanged) {
        final ButtonType close =
                new ButtonType(messages.get(MessageKey.COMMON_CLOSE), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType show =
                new ButtonType(messages.get(MessageKey.RESULTS_SHOW_CHANGED), ButtonBar.ButtonData.OTHER);
        final ButtonType undo = new ButtonType(messages.get(MessageKey.RESULTS_UNDO_ALL), ButtonBar.ButtonData.OTHER);
        final boolean changed = results.outcome() == ChangeResults.Outcome.CHANGED;
        if (changed) {
            card.getButtonTypes().setAll(close, show, undo);
        } else {
            card.getButtonTypes().setAll(close);
        }
        final Button closeButton = button(card, close, CLOSE_ID, MessageKey.COMMON_CLOSE_TIP, "btn-primary");
        closeButton.setOnAction(event -> modalHost.hide());
        if (!changed) {
            return;
        }
        final Button showButton = button(card, show, SHOW_ID, MessageKey.RESULTS_SHOW_CHANGED_TIP, "btn-secondary");
        showButton.setOnAction(event -> {
            modalHost.hide();
            showChanged.run();
        });
        showButton.setDisable(
                results.count(ChangeKind.REMOVED) == results.rows().size());
        final Button undoButton = button(card, undo, UNDO_ID, MessageKey.RESULTS_UNDO_ALL_TIP, "btn-danger-outline");
        wireUndo(undoButton, results);
    }

    // Undo all is on only while some row can still be put back and no revert runs.
    private static void wireUndo(final Button undoButton, final ChangeResults results) {
        undoButton.setOnAction(event -> results.undoAll());
        final Runnable sync = () -> undoButton.setDisable(results.working().get() || !results.hasRowsToRevert());
        results.working().addListener((observed, was, now) -> sync.run());
        results.reverted().addListener((SetChangeListener<Long>) change -> sync.run());
        sync.run();
    }

    private Button button(
            final DialogPane card,
            final ButtonType type,
            final String id,
            final MessageKey tip,
            final String styleClass) {
        final Button button = (Button) card.lookupButton(type);
        button.setId(id);
        Tips.install(messages, button, tip);
        button.getStyleClass().add(styleClass);
        return button;
    }
}
