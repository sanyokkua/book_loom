package ua.bookloom.ui.screen;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RecurringTerms;

/**
 * The cells of the recurring-terms table, one control each, filled from the row they show. A cell is reused for other
 * rows as the table scrolls, so a typed rendering is remembered with the term it was typed for and a focus loss that
 * arrives after the cell moved on writes nothing. Cells log nothing, since they refresh on every scroll.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecurringTermsCells {

    private static final double ROW_SPACING = 8;
    private static final String COUNT_SEPARATOR = " · ";

    /** A cell holding one control that is filled from the row it shows. */
    abstract static class RowCell<N extends Node> extends TableCell<LexiconEntry, LexiconEntry> {

        private final N control;

        RowCell(final N control) {
            this.control = control;
            setText(null);
        }

        final N control() {
            return control;
        }

        abstract void show(N shown, LexiconEntry entry);

        @Override
        protected final void updateItem(final @Nullable LexiconEntry entry, final boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            show(control, entry);
            setGraphic(control);
        }
    }

    /** The source term. */
    static final class TermCell extends RowCell<Label> {

        TermCell() {
            super(new Label());
            control().getStyleClass().add("glossary-term");
        }

        @Override
        void show(final Label shown, final LexiconEntry entry) {
            shown.setText(entry.term());
        }
    }

    /** The rendering later requests keep, as a text field that writes on Enter and on losing focus. */
    static final class RenderingCell extends RowCell<TextField> {

        private final RecurringTerms recurring;
        private @Nullable String syncedTerm;
        private String syncedText = "";

        RenderingCell(final Messages messages, final RecurringTerms recurring) {
            super(Tips.installOnHover(messages, new TextField(), MessageKey.RECURRING_COLUMN_RENDERING_TIP));
            this.recurring = recurring;
            control().setOnAction(event -> commit());
            control().focusedProperty().addListener((observed, was, now) -> {
                if (!now) {
                    commit();
                }
            });
        }

        private void commit() {
            final String typed = control().getText();
            if (syncedTerm == null || typed.equals(syncedText)) {
                return;
            }
            syncedText = typed;
            recurring.setRendering(syncedTerm, typed);
        }

        @Override
        void show(final TextField shown, final LexiconEntry entry) {
            syncedTerm = entry.term();
            syncedText = entry.established().orElse("");
            shown.setText(syncedText);
        }
    }

    /** What the term was rendered as: the learned rendering with its support, then each reported one with its count. */
    static final class SeenCell extends RowCell<Label> {

        private final Messages messages;
        private final String none;

        SeenCell(final Messages messages) {
            super(new Label());
            this.messages = messages;
            none = messages.get(MessageKey.RECURRING_SEEN_NONE);
            control().getStyleClass().add("muted");
        }

        @Override
        void show(final Label shown, final LexiconEntry entry) {
            final Stream<String> learned = Stream.ofNullable(entry.learned())
                    .map(found -> messages.get(MessageKey.RECURRING_SEEN_LEARNED, found.text(), found.support()));
            final Stream<String> reported =
                    entry.renderings().stream().map(rendering -> rendering.text() + " ×" + rendering.count());
            final String text = Stream.concat(learned, reported).collect(Collectors.joining(COUNT_SEPARATOR));
            shown.setText(text.isEmpty() ? none : text);
        }
    }

    /** The row's actions: move it to the glossary, or remove it. */
    static final class ActionsCell extends RowCell<HBox> {

        ActionsCell(final Messages messages, final RecurringTerms recurring) {
            super(new HBox(ROW_SPACING));
            final Button promote = Tips.installOnHover(
                    messages, new Button(messages.get(MessageKey.RECURRING_PROMOTE)), MessageKey.RECURRING_PROMOTE_TIP);
            promote.getStyleClass().add("btn-ghost");
            promote.setMinWidth(Region.USE_PREF_SIZE);
            promote.setOnAction(event -> act(recurring, true));
            final Button remove = Tips.installOnHover(messages, new Button("✕"), MessageKey.RECURRING_REMOVE_TIP);
            remove.getStyleClass().addAll("btn-ghost", "glossary-remove");
            remove.setAccessibleText(messages.get(MessageKey.RECURRING_REMOVE));
            remove.setOnAction(event -> act(recurring, false));
            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            control().setAlignment(Pos.CENTER_LEFT);
            control().getChildren().addAll(promote, spacer, remove);
        }

        private void act(final RecurringTerms recurring, final boolean promote) {
            final LexiconEntry shown = getItem();
            if (shown == null) {
                return;
            }
            if (promote) {
                recurring.promote(shown.term());
            } else {
                recurring.remove(shown.term());
            }
        }

        @Override
        void show(final HBox shown, final LexiconEntry entry) {
            // The buttons are the same for every row.
        }
    }
}
