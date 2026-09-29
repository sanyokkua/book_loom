package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * An editable choice box that narrows a fixed list to the entries whose displayed name contains the typed text
 * anywhere, ignoring case and accents, and settles the typed text when focus leaves it.
 *
 * <p>The mode is fixed by the factory that built the box, so no caller can pair a mode with the wrong function:
 * STRICT keeps the previous value when the text names no entry, FREE_TEXT keeps the typed text, PARSED hands the text
 * to a caller's parser that may recognise a value the list does not hold. The committed value is read-only; the text
 * the box last refused is {@link #invalidTextProperty()} until the next commit.
 *
 * @param <T> the entry type
 */
@Slf4j
public final class SearchableCombo<T> extends ComboBox<T> {

    private enum Mode {
        STRICT,
        FREE_TEXT,
        PARSED
    }

    private enum Source {
        LIST,
        FREE_TEXT,
        PARSER,
        RESTORED,
        PROGRAM
    }

    private final Mode mode;
    private final List<T> allItems;
    private final Function<T, String> name;
    private final @Nullable Function<String, T> fromText;
    private final @Nullable Function<String, Optional<T>> parse;
    private final FilteredList<T> filtered;
    private final ReadOnlyObjectWrapper<@Nullable T> committed = new ReadOnlyObjectWrapper<>(this, "committed");
    private final ReadOnlyStringWrapper invalidText = new ReadOnlyStringWrapper(this, "invalidText");
    private boolean updating;

    private SearchableCombo(
            final Mode mode,
            final List<T> items,
            final Function<T, String> name,
            final @Nullable Function<String, T> fromText,
            final @Nullable Function<String, Optional<T>> parse) {
        this.mode = mode;
        this.allItems = List.copyOf(items);
        this.name = Objects.requireNonNull(name, "name");
        this.fromText = fromText;
        this.parse = parse;
        this.filtered = new FilteredList<>(FXCollections.observableArrayList(allItems));
        setEditable(true);
        setItems(filtered);
        setConverter(new StringConverter<>() {
            @Override
            public String toString(final @Nullable T value) {
                return displayName(value);
            }

            @Override
            public @Nullable T fromString(final @Nullable String text) {
                return committed.get();
            }
        });
        getEditor().textProperty().addListener((obs, was, now) -> narrow(now));
        getEditor().focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                settle();
            }
        });
        valueProperty().addListener((obs, was, now) -> chosenFromList(now));
    }

    /** A box that keeps the previous value when the typed text names no entry. */
    public static <T> SearchableCombo<T> strict(final List<T> items, final Function<T, String> name) {
        return new SearchableCombo<>(Mode.STRICT, items, name, null, null);
    }

    /** A box that keeps whatever text was typed, turned into a value by {@code fromText}. */
    public static <T> SearchableCombo<T> freeText(
            final List<T> items, final Function<T, String> name, final Function<String, T> fromText) {
        return new SearchableCombo<>(Mode.FREE_TEXT, items, name, Objects.requireNonNull(fromText, "fromText"), null);
    }

    /** A box whose typed text goes to {@code parse}, which may recognise a value the list does not hold. */
    public static <T> SearchableCombo<T> parsed(
            final List<T> items, final Function<T, String> name, final Function<String, Optional<T>> parse) {
        return new SearchableCombo<>(Mode.PARSED, items, name, null, Objects.requireNonNull(parse, "parse"));
    }

    /** The committed value; null until something is chosen. */
    public ReadOnlyObjectProperty<@Nullable T> committedProperty() {
        return committed.getReadOnlyProperty();
    }

    /** The text the box last refused, or null once a commit cleared it. */
    public ReadOnlyStringProperty invalidTextProperty() {
        return invalidText.getReadOnlyProperty();
    }

    /** The committed value, or null. */
    public @Nullable T getCommitted() {
        return committed.get();
    }

    /** The refused text, or null. */
    public @Nullable String getInvalidText() {
        return invalidText.get();
    }

    /** Commits {@code value} from code (a preselection); a null value empties the box. */
    public void select(final @Nullable T value) {
        commit(value, Source.PROGRAM);
    }

    private String displayName(final @Nullable T value) {
        return value == null ? "" : name.apply(value);
    }

    private void narrow(final @Nullable String text) {
        if (updating) {
            return;
        }
        final String typed = text == null ? "" : text;
        final String needle = TextMatch.normalize(typed);
        // Changing the list makes the selection model pick another entry and rewrite the editor; neither is a choice.
        updating = true;
        try {
            filtered.setPredicate(item ->
                    needle.isEmpty() || TextMatch.normalize(displayName(item)).contains(needle));
            if (!Objects.equals(getValue(), committed.get())) {
                setValue(committed.get());
            }
            getEditor().setText(typed);
            getEditor().positionCaret(typed.length());
        } finally {
            updating = false;
        }
        if (isFocused() || getEditor().isFocused()) {
            if (!needle.isEmpty() && !filtered.isEmpty() && !isShowing()) {
                show();
            }
        }
    }

    private void chosenFromList(final @Nullable T chosen) {
        if (updating || chosen == null) {
            return;
        }
        commit(chosen, Source.LIST);
    }

    private void settle() {
        final String text = getEditor().getText() == null ? "" : getEditor().getText();
        if (text.equals(displayName(committed.get()))) {
            updating = true;
            try {
                clearFilter();
            } finally {
                updating = false;
            }
            return;
        }
        switch (mode) {
            case STRICT -> settleStrict(text);
            case FREE_TEXT -> commit(Objects.requireNonNull(fromText).apply(text), Source.FREE_TEXT);
            case PARSED -> settleParsed(text);
        }
    }

    private void settleStrict(final String text) {
        final String needle = TextMatch.normalize(text.strip());
        final Optional<T> match = allItems.stream()
                .filter(item -> TextMatch.normalize(displayName(item)).equals(needle))
                .findFirst();
        if (match.isPresent()) {
            commit(match.get(), Source.LIST);
        } else {
            restore(text);
        }
    }

    private void settleParsed(final String text) {
        final Optional<T> value = Objects.requireNonNull(parse).apply(text);
        if (value.isPresent()) {
            commit(value.get(), Source.PARSER);
        } else {
            restore(text);
        }
    }

    private void restore(final String refused) {
        log.debug("search combo {} restored the previous value, refusing the typed text", mode);
        final T previous = committed.get();
        updating = true;
        try {
            setValue(previous);
            getEditor().setText(displayName(previous));
            clearFilter();
        } finally {
            updating = false;
        }
        invalidText.set(refused.isBlank() ? null : refused);
    }

    private void commit(final @Nullable T value, final Source source) {
        log.debug("search combo {} committed a value from {}", mode, source);
        updating = true;
        try {
            committed.set(value);
            setValue(value);
            getEditor().setText(displayName(value));
            clearFilter();
        } finally {
            updating = false;
        }
        invalidText.set(null);
        if (isShowing()) {
            hide();
        }
    }

    private void clearFilter() {
        filtered.setPredicate(null);
    }
}
