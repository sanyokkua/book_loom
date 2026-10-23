package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The desk's More ▾ button and its menu of the less common actions, each a word with a line under it: Leave for later,
 * Try again, Try again with a note…, and Undo my edit, which is off while the segment holds no edit of the person's.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewMoreMenu {

    /**
     * What the menu's items do besides the view model's own actions.
     *
     * @param askNote opens the inline note panel
     * @param askUndo asks inside the desk before the edit is undone
     * @param guard runs what would replace the editor's text, asking first about unsaved typing
     */
    record Asks(Runnable askNote, Runnable askUndo, ReviewListPane.Guard guard) {}

    /**
     * Turns a footer button into More ▾.
     *
     * @param more the footer button
     * @param viewModel the desk's view model
     * @param asks the inline questions the menu opens
     * @param messages the catalogue the words come from
     */
    static void install(final Button more, final ReviewViewModel viewModel, final Asks asks, final Messages messages) {
        Objects.requireNonNull(asks, "asks");
        more.setText(messages.get(MessageKey.REVIEW_MORE));
        more.setId("review-more");
        more.getStyleClass().add("btn-secondary");
        Tips.install(messages, more, MessageKey.REVIEW_MORE_TIP);
        final ContextMenu menu = menu(viewModel, asks, messages);
        more.setContextMenu(menu);
        // The menu hangs from the button's right edge, as the mockup's does, so a long Ukrainian line never leaves the
        // window.
        menu.setOnShown(event -> {
            final Bounds bounds = more.localToScreen(more.getBoundsInLocal());
            if (bounds != null) {
                menu.setX(bounds.getMaxX() - menu.getWidth());
            }
        });
        more.setOnAction(event -> {
            log.debug("more pressed: showing the less common actions");
            menu.show(more, Side.TOP, 0, 0);
        });
    }

    private static ContextMenu menu(final ReviewViewModel viewModel, final Asks asks, final Messages messages) {
        return new ContextMenu(
                simple(
                        messages,
                        "review-more-later",
                        MessageKey.REVIEW_MORE_LATER,
                        MessageKey.REVIEW_MORE_LATER_SUB,
                        () -> asks.guard().run(viewModel::leaveForLater),
                        viewModel.selected().isNull()),
                simple(
                        messages,
                        "review-more-retry",
                        MessageKey.REVIEW_MORE_RETRY,
                        MessageKey.REVIEW_MORE_RETRY_SUB,
                        () -> asks.guard().run(() -> viewModel.retry(null, false)),
                        locked(viewModel)),
                simple(
                        messages,
                        "review-more-note",
                        MessageKey.REVIEW_MORE_RETRY_NOTE,
                        MessageKey.REVIEW_MORE_RETRY_NOTE_SUB,
                        asks.askNote(),
                        locked(viewModel)),
                undo(messages, viewModel, asks.askUndo()));
    }

    // Off with no segment selected, or while the actions are not offered (a run translates, a retry is in flight).
    private static ObservableBooleanValue locked(final ReviewViewModel viewModel) {
        return Bindings.or(viewModel.selected().isNull(), Bindings.not(viewModel.actionsAvailable()));
    }

    private static CustomMenuItem simple(
            final Messages messages,
            final String id,
            final MessageKey word,
            final MessageKey sub,
            final Runnable action,
            final ObservableBooleanValue off) {
        final CustomMenuItem item = item(messages, id, word, sub, action);
        item.disableProperty().bind(off);
        return item;
    }

    // Undo is off with no edit to undo, and its line under the word says so.
    private static CustomMenuItem undo(final Messages messages, final ReviewViewModel viewModel, final Runnable ask) {
        final BooleanBinding noEdit = Bindings.createBooleanBinding(
                () -> viewModel.selected().get() == null
                        || viewModel.selected().get().userTarget() == null,
                viewModel.selected());
        final CustomMenuItem undo =
                item(messages, "review-more-undo", MessageKey.REVIEW_MORE_UNDO, MessageKey.REVIEW_MORE_UNDO_SUB, ask);
        undo.disableProperty().bind(noEdit.or(Bindings.not(viewModel.actionsAvailable())));
        final Label sub = (Label) ((VBox) undo.getContent()).getChildren().get(1);
        sub.textProperty()
                .bind(Bindings.when(noEdit)
                        .then(messages.get(MessageKey.REVIEW_MORE_UNDO_NONE))
                        .otherwise(messages.get(MessageKey.REVIEW_MORE_UNDO_SUB)));
        return undo;
    }

    private static CustomMenuItem item(
            final Messages messages,
            final String id,
            final MessageKey word,
            final MessageKey sub,
            final Runnable action) {
        final Label wordLabel = new Label(messages.get(word));
        wordLabel.getStyleClass().add("review-more-word");
        final Label subLabel = new Label(messages.get(sub));
        subLabel.getStyleClass().addAll("review-more-sub", "muted");
        final CustomMenuItem item = new CustomMenuItem(new VBox(wordLabel, subLabel));
        item.setId(id);
        item.setOnAction(event -> {
            log.debug("more item {} chosen", id);
            action.run();
        });
        return item;
    }
}
