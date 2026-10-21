package ua.bookloom.ui.screen;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
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

    /** The source term; a changed row is marked by the table's row edge, so nothing else shares the cell. */
    static final class TermCell extends RowCell<Label> {

        TermCell() {
            super(withFullText(new Label()));
            control().getStyleClass().add("glossary-term");
        }

        // The cell may cut a long term; its whole text is the hover.
        private static Label withFullText(final Label term) {
            return Tips.installLive(term, term.textProperty());
        }

        @Override
        void show(final Label shown, final LexiconEntry entry) {
            shown.setText(entry.term());
        }
    }

    /** The target later requests keep, as a text field that writes on Enter and on losing focus. */
    static final class RenderingCell extends RowCell<TextField> {

        private final RecurringTerms recurring;
        private @Nullable String syncedTerm;
        private String syncedText = "";

        RenderingCell(final Messages messages, final RecurringTerms recurring) {
            super(Tips.installOnHover(messages, new TextField(), MessageKey.RECURRING_COLUMN_TARGET_TIP));
            this.recurring = recurring;
            control().setPromptText(messages.get(MessageKey.NAMES_STYLE_NO_TARGET));
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

    /** The row's action that moves it to the glossary. */
    static final class PromoteCell extends RowCell<Button> {

        PromoteCell(final Messages messages, final RecurringTerms recurring) {
            super(Tips.installOnHover(
                    messages,
                    new Button(messages.get(MessageKey.RECURRING_PROMOTE)),
                    MessageKey.RECURRING_PROMOTE_TIP));
            control().getStyleClass().add("btn-ghost");
            control().setMinWidth(Region.USE_PREF_SIZE);
            control().setOnAction(event -> {
                final LexiconEntry shown = getItem();
                if (shown != null) {
                    recurring.promote(shown.term());
                }
            });
        }

        @Override
        void show(final Button shown, final LexiconEntry entry) {
            // The button is the same for every row.
        }
    }

    /** The row's action that removes it, in the table's last column. */
    static final class RemoveCell extends RowCell<Button> {

        RemoveCell(final Messages messages, final RecurringTerms recurring) {
            super(Tips.installOnHover(messages, new Button("✕"), MessageKey.RECURRING_REMOVE_TIP));
            control().getStyleClass().addAll("btn-ghost", "glossary-remove");
            control().setAccessibleText(messages.get(MessageKey.RECURRING_REMOVE));
            control().setOnAction(event -> {
                final LexiconEntry shown = getItem();
                if (shown != null) {
                    recurring.remove(shown.term());
                }
            });
        }

        @Override
        void show(final Button shown, final LexiconEntry entry) {
            // The button is the same for every row.
        }
    }
}
