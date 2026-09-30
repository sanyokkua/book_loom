package ua.bookloom.ui.screen;

import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/**
 * The cells of the glossary table, one editing control each.
 *
 * <p>A cell is reused for other rows as the table scrolls, so each control is filled from the row it now shows while a
 * flag holds back its own change handler, and a typed target is remembered with the row it was typed for: a focus loss
 * that arrives after the cell moved on must not write the old text into the new row. Cells log nothing, since they are
 * refreshed on every scroll.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryCells {

    private static final double ROW_SPACING = 8;

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
            final Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            final Button remove = new Button("✕");
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

    /** The target as a text field that writes on Enter and on losing focus. */
    static final class TargetCell extends EntryCell<TextField> {

        private final NamesStyleViewModel model;
        private @Nullable String syncedId;
        private String syncedText = "";

        TargetCell(final NamesStyleViewModel model) {
            super(new TextField());
            this.model = model;
            control().setOnAction(event -> commit());
            control().focusedProperty().addListener((observed, was, now) -> {
                if (!now) {
                    commit();
                }
            });
        }

        private void commit() {
            final GlossaryEntry shown = getItem();
            final String typed = control().getText();
            if (shown == null || !shown.id().equals(syncedId) || typed.equals(syncedText)) {
                return;
            }
            syncedText = typed;
            model.setTarget(shown.id(), typed);
        }

        @Override
        void show(final TextField shown, final GlossaryEntry entry) {
            syncedId = entry.id();
            syncedText = entry.target() == null ? "" : entry.target();
            shown.setText(syncedText);
        }
    }

    /** A choice among the values of an enum, written as soon as it is made. */
    static final class ChoiceCell<T> extends EntryCell<ComboBox<T>> {

        private final Function<GlossaryEntry, T> read;

        ChoiceCell(
                final T[] values,
                final Function<T, String> label,
                final Function<GlossaryEntry, T> read,
                final BiConsumer<String, T> write) {
            super(new ComboBox<>(FXCollections.observableArrayList(values)));
            this.read = read;
            control().setConverter(new StringConverter<>() {
                @Override
                public String toString(final @Nullable T value) {
                    return value == null ? "" : label.apply(value);
                }

                @Override
                public T fromString(final String text) {
                    return values[0];
                }
            });
            control().valueProperty().addListener((observed, was, now) -> {
                final GlossaryEntry shown = getItem();
                if (!isSyncing() && now != null && shown != null) {
                    write.accept(shown.id(), now);
                }
            });
        }

        @Override
        void show(final ComboBox<T> shown, final GlossaryEntry entry) {
            shown.setValue(read.apply(entry));
        }
    }

    /** The lock as a switch, written as soon as it is turned. */
    static final class LockCell extends EntryCell<ToggleSwitch> {

        LockCell(final NamesStyleViewModel model) {
            super(new ToggleSwitch());
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
