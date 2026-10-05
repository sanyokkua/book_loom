package ua.bookloom.ui.screen;

import java.util.List;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.RecurringTerms;

/**
 * The Recurring terms card under the glossary: common words and titles the book repeats, with the rendering later
 * requests are asked to keep, what the drafts actually used, and the actions to edit a rendering, move a term to the
 * glossary or remove it. The scan needs no model; the suggestion of renderings is one of the screen's model actions,
 * so it is offered and blocked as they are.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecurringTermsCard {

    private static final double CARD_SPACING = 10;
    private static final double ACTION_SPACING = 8;
    private static final double ROW_HEIGHT = 46;
    private static final double VISIBLE_ROWS = 3;
    private static final double HEADER_HEIGHT = 32;
    private static final double TERM_WIDTH = 170;
    private static final double RENDERING_WIDTH = 260;
    private static final double SEEN_WIDTH = 300;
    private static final double FIELD_WIDTH = 220;

    static Node build(final Messages messages, final NamesStyleViewModel names) {
        final RecurringTerms recurring = names.recurring();
        final VBox card = new VBox(
                CARD_SPACING, title(messages), note(messages), toolbar(messages, names), table(messages, recurring));
        card.setId("recurring-card");
        card.getStyleClass().add("card");
        return card;
    }

    private static Label title(final Messages messages) {
        final Label title = new Label(messages.get(MessageKey.RECURRING_TITLE));
        title.setId("recurring-title");
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMinHeight(Region.USE_PREF_SIZE);
        return title;
    }

    private static Label note(final Messages messages) {
        final Label note = new Label(messages.get(MessageKey.RECURRING_NOTE));
        note.setId("recurring-note");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        return note;
    }

    private static FlowPane toolbar(final Messages messages, final NamesStyleViewModel names) {
        final RecurringTerms recurring = names.recurring();
        final Button find = action(
                messages, "recurring-find", MessageKey.RECURRING_FIND, MessageKey.RECURRING_FIND_TIP, recurring::find);
        final Button suggest = action(
                messages,
                "recurring-suggest",
                MessageKey.RECURRING_SUGGEST,
                MessageKey.RECURRING_SUGGEST_TIP,
                recurring::suggestRenderings);
        suggest.disableProperty()
                .bind(names.busy().or(names.modelBlockedReason().isNotEmpty()));
        final TextField typed = typedTerm(messages);
        final Runnable add = () -> {
            recurring.add(typed.getText());
            typed.clear();
        };
        typed.setOnAction(event -> add.run());
        final Button addButton =
                action(messages, "recurring-add", MessageKey.RECURRING_ADD, MessageKey.RECURRING_ADD_TIP, add);
        final FlowPane toolbar = new FlowPane(ACTION_SPACING, ACTION_SPACING, find, suggest, typed, addButton);
        toolbar.setId("recurring-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    // Escape empties the field, as it does the glossary's search.
    private static TextField typedTerm(final Messages messages) {
        final TextField typed = Tips.install(messages, new TextField(), MessageKey.RECURRING_ADD_PROMPT_TIP);
        typed.setId("recurring-add-field");
        typed.setPromptText(messages.get(MessageKey.RECURRING_ADD_PROMPT));
        typed.setPrefWidth(FIELD_WIDTH);
        typed.setMinWidth(Region.USE_PREF_SIZE);
        typed.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                typed.clear();
                event.consume();
            }
        });
        return typed;
    }

    private static Button action(
            final Messages messages,
            final String id,
            final MessageKey caption,
            final MessageKey tip,
            final Runnable onPress) {
        final Button button = Tips.install(messages, new Button(messages.get(caption)), tip);
        button.setId(id);
        button.getStyleClass().add("btn-ghost");
        // A caption is never cut to an ellipsis: the toolbar wraps the button onto the next line instead.
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> {
            log.debug("{} pressed", id);
            onPress.run();
        });
        return button;
    }

    private static TableView<LexiconEntry> table(final Messages messages, final RecurringTerms recurring) {
        final TableView<LexiconEntry> table = new TableView<>(recurring.rows());
        table.setId("recurring-table");
        table.getStyleClass().add("glossary-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(ROW_HEIGHT);
        table.setPlaceholder(new Label());
        table.setFocusTraversable(false);
        table.setPrefHeight(HEADER_HEIGHT + VISIBLE_ROWS * ROW_HEIGHT);
        table.setMinHeight(HEADER_HEIGHT + ROW_HEIGHT);
        table.getColumns().addAll(columns(messages, recurring));
        return table;
    }

    private static List<TableColumn<LexiconEntry, LexiconEntry>> columns(
            final Messages messages, final RecurringTerms recurring) {
        return List.of(
                column(
                        messages,
                        MessageKey.RECURRING_COLUMN_TERM,
                        MessageKey.RECURRING_COLUMN_TERM_TIP,
                        TERM_WIDTH,
                        RecurringTermsCells.TermCell::new),
                column(
                        messages,
                        MessageKey.RECURRING_COLUMN_RENDERING,
                        MessageKey.RECURRING_COLUMN_RENDERING_TIP,
                        RENDERING_WIDTH,
                        () -> new RecurringTermsCells.RenderingCell(messages, recurring)),
                column(
                        messages,
                        MessageKey.RECURRING_COLUMN_SEEN,
                        MessageKey.RECURRING_COLUMN_SEEN_TIP,
                        SEEN_WIDTH,
                        () -> new RecurringTermsCells.SeenCell(messages)),
                column(
                        messages,
                        MessageKey.RECURRING_COLUMN_ACTIONS,
                        MessageKey.RECURRING_COLUMN_ACTIONS_TIP,
                        0,
                        () -> new RecurringTermsCells.ActionsCell(messages, recurring)));
    }

    private static TableColumn<LexiconEntry, LexiconEntry> column(
            final Messages messages,
            final MessageKey caption,
            final MessageKey tip,
            final double width,
            final Supplier<RecurringTermsCells.RowCell<?>> cell) {
        final TableColumn<LexiconEntry, LexiconEntry> column = new TableColumn<>(messages.get(caption));
        Tips.installOnHeader(messages, column, tip);
        column.setSortable(false);
        column.setReorderable(false);
        if (width > 0) {
            column.setPrefWidth(width);
        }
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        column.setCellFactory(_ -> cell.get());
        return column;
    }
}
