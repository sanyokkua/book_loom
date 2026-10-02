package ua.bookloom.ui.screen;

import javafx.css.PseudoClass;
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
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The cells of the glossary table, one editing control each.
 *
 * <p>A cell is reused for other rows as the table scrolls, so each control is filled from the row it now shows while a
 * flag holds back its own change handler, and a typed target is remembered with the row it was typed for: a focus loss
 * that arrives after the cell moved on must not write the old text into the new row. Cells log nothing, since they are
 * refreshed on every scroll. A row's tooltips wait for the pointer's first visit, since most rows are never hovered.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryCells {

    private static final double ROW_SPACING = 8;
    private static final double BADGE_SPACING = 4;

    /** A cell holding one control that is filled from the row it shows. */
    abstract static class EntryCell<N extends Node> extends TableCell<GlossaryEntry, GlossaryEntry> {

        private final N control;
        private boolean syncing;

        EntryCell(final N control) {
            this.control = control;
            setText(null);
        }

        final N control() {
            return control;
        }

        final boolean isSyncing() {
            return syncing;
        }

        abstract void show(N shown, GlossaryEntry entry);

        @Override
        protected final void updateItem(final @Nullable GlossaryEntry entry, final boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            syncing = true;
            try {
                show(control, entry);
            } finally {
                syncing = false;
            }
            setGraphic(control);
        }
    }

    /** The source term with the action that removes its row. */
    static final class SourceCell extends EntryCell<HBox> {

        private final Label term = new Label();

        SourceCell(final Messages messages, final NamesStyleViewModel model) {
            super(new HBox(ROW_SPACING));
            term.getStyleClass().add("glossary-term");
            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            final Button remove = Tips.installOnHover(messages, new Button("✕"), MessageKey.NAMES_STYLE_REMOVE_TIP);
            remove.getStyleClass().addAll("btn-ghost", "glossary-remove");
            remove.setAccessibleText(messages.get(MessageKey.NAMES_STYLE_REMOVE));
            remove.setOnAction(event -> {
                final GlossaryEntry shown = getItem();
                if (shown != null) {
                    model.remove(shown.id());
                }
            });
            control().setAlignment(Pos.CENTER_LEFT);
            control().getChildren().addAll(term, spacer, remove);
        }

        @Override
        void show(final HBox shown, final GlossaryEntry entry) {
            term.setText(entry.term());
        }
    }

    /**
     * The target as a text field that writes on Enter and on losing focus. A target the model suggested is slanted and
     * badged, with a tick that accepts it; Enter on an untouched suggestion accepts it too.
     */
    static final class TargetCell extends EntryCell<HBox> {

        private static final PseudoClass SUGGESTED = PseudoClass.getPseudoClass("suggested");

        private final NamesStyleViewModel model;
        private final TextField field;
        private final Label badge;
        private final Button accept;
        private @Nullable String syncedId;
        private String syncedText = "";

        TargetCell(final Messages messages, final NamesStyleViewModel model) {
            super(new HBox(BADGE_SPACING));
            this.model = model;
            field = Tips.installOnHover(messages, new TextField(), MessageKey.NAMES_STYLE_COLUMN_TARGET_TIP);
            badge = Tips.installOnHover(
                    messages,
                    new Label(messages.get(MessageKey.NAMES_STYLE_SUGGESTED_BADGE)),
                    MessageKey.NAMES_STYLE_SUGGESTED_BADGE_TIP);
            badge.getStyleClass().addAll("chip", "glossary-suggested-badge");
            badge.setMinWidth(Region.USE_PREF_SIZE);
            accept = Tips.installOnHover(messages, new Button("✓"), MessageKey.NAMES_STYLE_ACCEPT_TIP);
            accept.getStyleClass().addAll("btn-ghost", "glossary-accept");
            accept.setAccessibleText(messages.get(MessageKey.NAMES_STYLE_ACCEPT));
            accept.setMinWidth(Region.USE_PREF_SIZE);
            accept.setOnAction(event -> acceptShown());
            HBox.setHgrow(field, Priority.ALWAYS);
            field.setOnAction(event -> onEnter());
            field.focusedProperty().addListener((observed, was, now) -> {
                if (!now) {
                    commit();
                }
            });
            control().setAlignment(Pos.CENTER_LEFT);
            control().getChildren().addAll(field, badge, accept);
        }

        private void onEnter() {
            final GlossaryEntry shown = getItem();
            if (shown != null && shown.isSuggested() && field.getText().equals(syncedText)) {
                acceptShown();
                return;
            }
            commit();
        }

        private void acceptShown() {
            final GlossaryEntry shown = getItem();
            if (shown != null && shown.id().equals(syncedId)) {
                model.accept(shown.id());
            }
        }

        private void commit() {
            final GlossaryEntry shown = getItem();
            final String typed = field.getText();
            if (shown == null || !shown.id().equals(syncedId) || typed.equals(syncedText)) {
                return;
            }
            syncedText = typed;
            model.setTarget(shown.id(), typed);
        }

        @Override
        void show(final HBox shown, final GlossaryEntry entry) {
            syncedId = entry.id();
            syncedText = entry.target() == null ? "" : entry.target();
            field.setText(syncedText);
            final boolean suggested = entry.isSuggested();
            field.pseudoClassStateChanged(SUGGESTED, suggested);
            badge.setVisible(suggested);
            badge.setManaged(suggested);
            accept.setVisible(suggested);
            accept.setManaged(suggested);
        }
    }

    /** The lock as a switch, written as soon as it is turned. */
    static final class LockCell extends EntryCell<ToggleSwitch> {

        LockCell(final Messages messages, final NamesStyleViewModel model) {
            super(Tips.installOnHover(messages, new ToggleSwitch(), MessageKey.NAMES_STYLE_COLUMN_LOCKED_TIP));
            control().selectedProperty().addListener((observed, was, now) -> {
                final GlossaryEntry shown = getItem();
                if (!isSyncing() && shown != null) {
                    model.setLocked(shown.id(), now);
                }
            });
        }

        @Override
        void show(final ToggleSwitch shown, final GlossaryEntry entry) {
            shown.setSelected(entry.locked());
        }
    }
}
