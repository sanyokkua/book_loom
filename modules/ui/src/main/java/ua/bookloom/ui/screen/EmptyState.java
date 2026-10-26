package ua.bookloom.ui.screen;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.Messages;

/**
 * What a term table shows while it holds no row: one line that says why, and, for the glossary, the two ways to fill
 * it, Scan the book and + Add, which do what the toolbar's do. A search that matches nothing gets its own line and no
 * buttons. Nothing here logs: the placeholder is redrawn with the table.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EmptyState {

    private static final double SPACING = 12;
    private static final double BUTTON_SPACING = 8;

    /**
     * The empty glossary's buttons.
     *
     * @param scan the words of Scan the book
     * @param add the words of + Add
     * @param onScan what Scan the book does
     * @param onAdd what + Add does
     * @param scanOff whether the toolbar's Scan is off, which the empty one follows
     * @param frozen whether the screen is read-only
     */
    record Actions(
            TermTableCard.Phrase scan,
            TermTableCard.Phrase add,
            Runnable onScan,
            Runnable onAdd,
            ObservableBooleanValue scanOff,
            ObservableBooleanValue frozen) {}

    static Node of(
            final Messages messages,
            final String card,
            final TermTableCard.Texts texts,
            final StringProperty query,
            final @Nullable Actions actions) {
        final Label empty = new Label();
        empty.getStyleClass().add("glossary-empty");
        empty.setWrapText(true);
        empty.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(isFiltering(query) ? texts.emptyFiltered() : texts.empty()), query));
        final VBox state = new VBox(SPACING, empty);
        state.setAlignment(Pos.CENTER);
        if (actions != null) {
            final HBox buttons = new HBox(
                    BUTTON_SPACING,
                    button(messages, card + "-empty-scan", actions.scan(), actions.onScan(), "btn-secondary"),
                    button(messages, card + "-empty-add", actions.add(), actions.onAdd(), "btn-ghost"));
            buttons.setAlignment(Pos.CENTER);
            buttons.getChildren().getFirst().disableProperty().bind(actions.scanOff());
            buttons.getChildren().getLast().disableProperty().bind(actions.frozen());
            Visibility.bind(buttons, Bindings.createBooleanBinding(() -> !isFiltering(query), query));
            state.getChildren().add(buttons);
        }
        return state;
    }

    private static boolean isFiltering(final StringProperty query) {
        return query.get() != null && !query.get().isBlank();
    }

    private static Button button(
            final Messages messages,
            final String id,
            final TermTableCard.Phrase words,
            final Runnable onPress,
            final String style) {
        final Button button = Tips.install(messages, new Button(messages.get(words.caption())), words.tip());
        button.setId(id);
        button.getStyleClass().add(style);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> onPress.run());
        return button;
    }
}
