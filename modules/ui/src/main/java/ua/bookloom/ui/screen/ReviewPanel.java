package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The review panel that expands under the Translating dashboard: the flagged list on the left, the compare with its
 * findings and actions on the right, or the empty state when nothing is listed.
 *
 * <p>Whether the panel is open is this screen's own state; the view model reads its list when the panel opens. The
 * dashboard decides whether an open panel is also shown, from the run state. A review pause opens the panel on its own;
 * the follower holds that pause, which outlives this node, so it is observed weakly through a listener kept in a field.
 */
@Slf4j
final class ReviewPanel extends VBox {

    private static final double SPACING = 12;
    private static final double LIST_WIDTH = 230;

    private final ReviewViewModel viewModel;
    private final ReviewPauseFollower pauses;
    private final BooleanProperty open = new SimpleBooleanProperty();
    private final ChangeListener<Boolean> onPause = (observed, was, now) -> pausedForReview(now);

    ReviewPanel(
            final ReviewViewModel viewModel,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages,
            final RetryWithNoteDialog retryDialog,
            final ReviewPauseFollower pauses) {
        super(SPACING);
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.pauses = Objects.requireNonNull(pauses, "pauses");
        Objects.requireNonNull(messages, "messages");
        final Label title = new Label(messages.get(MessageKey.REVIEW_TITLE));
        title.getStyleClass().add("card-title");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final Button back = new Button(messages.get(MessageKey.REVIEW_BACK));
        back.setId("review-back");
        Tips.install(messages, back, MessageKey.REVIEW_BACK_TIP);
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(event -> setOpen(false));
        final HBox header = new HBox(SPACING, title, spacer, back);
        header.setAlignment(Pos.CENTER_LEFT);
        final ReviewListPane list = new ReviewListPane(viewModel, messages);
        list.setPrefWidth(LIST_WIDTH);
        list.setMinWidth(LIST_WIDTH);
        list.setMaxWidth(LIST_WIDTH);
        final HBox body = new HBox(SPACING, list, right(sourceName, targetName, messages, retryDialog));
        setId("review-panel");
        getStyleClass().add("card");
        getChildren().addAll(header, body);
        open.addListener((observed, was, now) -> opened(now));
        pauses.pausedForReview().addListener(new WeakChangeListener<>(onPause));
        pausedForReview(pauses.pausedForReview().get());
    }

    BooleanProperty openProperty() {
        return open;
    }

    void setOpen(final boolean value) {
        open.set(value);
    }

    private StackPane right(
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages,
            final RetryWithNoteDialog retryDialog) {
        final ReviewComparePane compare = new ReviewComparePane(
                viewModel, sourceName, targetName, messages, retryDialog, pauses.acceptContinues());
        final Label empty = new Label(messages.get(MessageKey.REVIEW_EMPTY));
        empty.setId("review-empty-text");
        empty.setWrapText(true);
        final VBox emptyState = new VBox(empty);
        emptyState.setId("review-empty");
        emptyState.setAlignment(Pos.CENTER);
        // A pause names a segment the flagged list may not hold (Manual pauses after an accepted one), so the compare
        // shows it whatever the list says.
        final BooleanBinding shown = Bindings.createBooleanBinding(
                () -> viewModel.selected().get() != null
                        && (!viewModel.rows().isEmpty()
                                || pauses.pausedForReview().get()),
                viewModel.rows(),
                viewModel.selected(),
                pauses.pausedForReview());
        emptyState.visibleProperty().bind(Bindings.isEmpty(viewModel.rows()).and(shown.not()));
        emptyState.managedProperty().bind(emptyState.visibleProperty());
        compare.visibleProperty().bind(shown);
        compare.managedProperty().bind(compare.visibleProperty());
        final StackPane stack = new StackPane(compare, emptyState);
        stack.setPrefWidth(0);
        stack.setMinWidth(0);
        HBox.setHgrow(stack, Priority.ALWAYS);
        return stack;
    }

    private void pausedForReview(final boolean now) {
        if (now) {
            log.debug("a review pause is waiting: showing the panel on its segment");
            if (open.get()) {
                viewModel.open();
            } else {
                setOpen(true);
            }
        }
    }

    private void opened(final boolean now) {
        log.debug("review panel {}", now ? "opened" : "closed");
        if (now) {
            viewModel.open();
        }
    }
}
