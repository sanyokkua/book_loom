package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * A choice among the values of an enum (a term's type or gender), shown as plain text until it is used.
 *
 * <p>A combo box in every row is the heaviest thing a scrolling glossary repaints, and almost every row is only read.
 * So the cell shows a label, reachable with Tab, and puts a combo box in its place when the label is clicked, focused,
 * or Enter or Space is pressed on it; the choice is written as soon as it is made, and the label comes back once the
 * combo box loses focus or the cell moves to another row. The combo box is made once per cell, on first use.
 *
 * @param <T> the enum the column chooses from
 */
final class GlossaryChoiceCell<T> extends GlossaryCells.EntryCell<Label> {

    private final Messages messages;
    private final MessageKey tip;
    private final T[] values;
    private final Function<T, String> label;
    private final Function<GlossaryEntry, T> read;
    private final BiConsumer<String, T> write;
    private @Nullable ComboBox<T> combo;
    private boolean filling;

    GlossaryChoiceCell(
            final Messages messages,
            final MessageKey tip,
            final T[] values,
            final Function<T, String> label,
            final Function<GlossaryEntry, T> read,
            final BiConsumer<String, T> write) {
        super(Tips.installOnHover(messages, new Label(), tip));
        this.messages = messages;
        this.tip = tip;
        this.values = values.clone();
        this.label = label;
        this.read = read;
        this.write = write;
        final Label shown = control();
        shown.getStyleClass().add("glossary-choice");
        shown.setFocusTraversable(true);
        shown.setMaxWidth(Double.MAX_VALUE);
        shown.setOnMouseClicked(event -> open(true));
        shown.focusedProperty().addListener((observed, was, now) -> {
            if (now) {
                open(false);
            }
        });
    }

    @Override
    void show(final Label shown, final GlossaryEntry entry) {
        shown.setText(label.apply(read.apply(entry)));
    }

    private void open(final boolean showList) {
        final GlossaryEntry entry = getItem();
        if (entry == null) {
            return;
        }
        final ComboBox<T> editor = editor();
        filling = true;
        try {
            editor.setValue(read.apply(entry));
        } finally {
            filling = false;
        }
        setGraphic(editor);
        editor.requestFocus();
        if (showList) {
            editor.show();
        }
    }

    private ComboBox<T> editor() {
        final ComboBox<T> existing = combo;
        if (existing != null) {
            return existing;
        }
        final ComboBox<T> made =
                Tips.installOnHover(messages, new ComboBox<>(FXCollections.observableArrayList(values)), tip);
        made.setMaxWidth(Double.MAX_VALUE);
        made.setConverter(new LabelConverter());
        made.valueProperty().addListener((observed, was, now) -> chosen(now));
        made.focusedProperty().addListener((observed, was, now) -> {
            if (!now && !made.isShowing()) {
                close();
            }
        });
        made.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !made.isShowing()) {
                made.show();
                event.consume();
            }
        });
        combo = made;
        return made;
    }

    private void chosen(final @Nullable T value) {
        final GlossaryEntry entry = getItem();
        if (filling || value == null || entry == null || Objects.equals(value, read.apply(entry))) {
            return;
        }
        write.accept(entry.id(), value);
    }

    private void close() {
        final GlossaryEntry entry = getItem();
        if (entry == null) {
            return;
        }
        show(control(), entry);
        setGraphic(control());
    }

    /** Shows each value by its catalogue label; the combo box is never editable, so text is never parsed back. */
    private final class LabelConverter extends StringConverter<T> {

        @Override
        public String toString(final @Nullable T value) {
            return value == null ? "" : label.apply(value);
        }

        @Override
        public T fromString(final String text) {
            return values[0];
        }
    }
}
