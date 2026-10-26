package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.control.ScrollHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ReviewMainAction;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * The review desk: an extra-large dialog with the list on the left and the selected segment on the right, one main
 * button that accepts or saves, More ▾ for the less common actions, and Close. Every question it asks (a note for a
 * retry, Undo, typing that is not saved) is asked inside it, so no other card replaces it.
 *
 * <p>Plain Enter does nothing here: the main button runs on Ctrl/⌘+Enter only, so a stray Enter on a focused row never
 * decides a segment. Ctrl+Alt+↓/↑ move between rows; every other key is left to the control that has the focus.
 */
@Slf4j
final class ReviewDeskCard extends ModalCard {

    private static final double SPACING = 12;
    private static final double LIST_MIN = 220;
    private static final double LIST_PREF = 300;
    private static final double LIST_SHARE = 0.3;

    private final ReviewViewModel viewModel;
    private final Messages messages;
    private final ReviewInlinePanels inline;
    private final Runnable onClose;

    /**
     * What the desk shows and does besides its view model.
     *
     * @param sourceName the heading of the source pane
     * @param targetName the heading of the target pane
     * @param acceptContinues whether a decision resumes a paused run, which words the main button "& continue"
     * @param suspicious the count of Worth a second look
     * @param technical the shared technical-details switch
     * @param pause pauses the run, from the read-only banner
     */
    record Parts(
            ObservableValue<String> sourceName,
            ObservableValue<String> targetName,
            ObservableValue<Boolean> acceptContinues,
            javafx.beans.value.ObservableIntegerValue suspicious,
            javafx.beans.property.BooleanProperty technical,
            Runnable pause) {}

    ReviewDeskCard(
            final ReviewViewModel viewModel, final Parts parts, final Messages messages, final Runnable onClose) {
        super(Size.XL);
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        Objects.requireNonNull(parts, "parts");
        inline = new ReviewInlinePanels(messages, (note, lower) -> guard(() -> viewModel.retry(note, lower)));
        setId("review-desk");
        getStyleClass().add("review-desk");
        setEnterNeedsShortcut(true);
        setOnEscape(this::closeAsked);
        buildHeader();
        setContent(body(parts));
        if (getContent() instanceof ScrollPane scroll) {
            scroll.setFitToHeight(true);
        }
        footer(parts);
        addEventFilter(KeyEvent.KEY_PRESSED, this::moveKeys);
        log.debug("review desk built");
    }

    private void buildHeader() {
        final Label left = Wrapped.label("", "dialog-sub");
        left.setId("review-desk-left");
        left.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                viewModel.translating().get()
                                        ? MessageKey.REVIEW_DESK_LEFT_READ_ONLY
                                        : MessageKey.REVIEW_DESK_LEFT,
                                viewModel.flaggedCount().get(),
                                Math.max(
                                        viewModel.deskTotal().get(),
                                        viewModel.flaggedCount().get())),
                        viewModel.translating(),
                        viewModel.flaggedCount(),
                        viewModel.deskTotal()));
        header(title(messages.get(MessageKey.REVIEW_DESK_TITLE)), left);
    }

    private Node body(final Parts parts) {
        final ReviewListPane list = new ReviewListPane(viewModel, parts.suspicious(), this::guard, messages);
        list.setMinWidth(LIST_MIN);
        list.setPrefWidth(LIST_PREF);
        list.maxWidthProperty().bind(widthProperty().multiply(LIST_SHARE));
        final ReviewDeskDetail detail = new ReviewDeskDetail(
                viewModel, parts.sourceName(), parts.targetName(), parts.technical(), messages, inline);
        final ScrollPane detailScroll = new ScrollHost(detail);
        detailScroll.setFitToHeight(true);
        detailScroll.getStyleClass().add("review-detail-scroll");
        keepInView(detailScroll);
        final VBox empty = new VBox(emptyText());
        empty.setId("review-empty");
        empty.setAlignment(Pos.CENTER);
        empty.visibleProperty().bind(viewModel.selected().isNull());
        detailScroll.visibleProperty().bind(viewModel.selected().isNotNull());
        final StackPane right = new StackPane(detailScroll, empty);
        HBox.setHgrow(right, Priority.ALWAYS);
        final HBox split = new HBox(SPACING, list, right);
        VBox.setVgrow(split, Priority.ALWAYS);
        final VBox body = new VBox(SPACING, readOnlyBanner(parts.pause()), inline.question(), split);
        body.getStyleClass().add("review-desk-body");
        return body;
    }

    // The note panel sits below the tabs, so at a small window it opens out of sight: bring it into view. Another
    // segment starts at the top, where its editors are.
    private void keepInView(final ScrollPane detailScroll) {
        inline.noteOpen().addListener((observed, was, open) -> {
            Platform.runLater(() -> detailScroll.setVvalue(open ? detailScroll.getVmax() : 0));
        });
        viewModel.selected().addListener((observed, was, now) -> {
            if (was == null || now == null || !was.segmentId().equals(now.segmentId())) {
                detailScroll.setVvalue(0);
                // The desk confirms a decision after it moved on, so a note naming one segment stays only until the
                // person picks another; a question about the segment left behind no longer applies either.
                clearFeedback();
                inline.closeQuestion();
            }
        });
    }

    private Label emptyText() {
        final Label text = new Label();
        text.setId("review-empty-text");
        text.setWrapText(true);
        text.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                viewModel.rows().isEmpty() ? MessageKey.REVIEW_EMPTY : MessageKey.REVIEW_PICK),
                        viewModel.rows()));
        return text;
    }

    // Read-only while a run translates (with Pause) or a retry is in flight (with no Pause: the run is not running).
    private Node readOnlyBanner(final Runnable pause) {
        final Label text = new Label();
        text.getStyleClass().add("banner-text");
        text.setWrapText(true);
        text.textProperty().bind(viewModel.lockReason().map(messages::get).orElse(""));
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);
        final Button pauseButton = Tips.install(
                messages, new Button(messages.get(MessageKey.REVIEW_DESK_PAUSE)), MessageKey.REVIEW_DESK_PAUSE_TIP);
        pauseButton.setId("review-pause");
        pauseButton.getStyleClass().add("btn-secondary");
        Visibility.bind(pauseButton, viewModel.translating());
        pauseButton.setOnAction(event -> {
            log.info("review desk: pause pressed");
            pause.run();
        });
        final HBox banner = new HBox(SPACING, text, pauseButton);
        banner.setId("review-readonly");
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().addAll("banner", "banner-info");
        Visibility.bind(banner, viewModel.lockReason().isNotNull());
        return banner;
    }

    private void footer(final Parts parts) {
        final Button close = button(
                messages.get(MessageKey.REVIEW_CLOSE), ButtonBar.ButtonData.CANCEL_CLOSE, "review-close", "btn-ghost");
        Tips.install(messages, close, MessageKey.REVIEW_CLOSE_TIP);
        close.setOnAction(event -> closeAsked());
        final ButtonType moreType = new ButtonType(messages.get(MessageKey.REVIEW_MORE), ButtonBar.ButtonData.OTHER);
        final ButtonType mainType = new ButtonType("", ButtonBar.ButtonData.OK_DONE);
        getButtonTypes().addAll(moreType, mainType);
        ReviewMoreMenu.install(
                (Button) lookupButton(moreType),
                viewModel,
                new ReviewMoreMenu.Asks(inline::openNote, this::askUndo, this::guard),
                messages);
        main((Button) lookupButton(mainType), parts.acceptContinues());
    }

    private void main(final Button main, final ObservableValue<Boolean> continues) {
        main.setId("review-main");
        main.getStyleClass().add("btn-primary");
        Tips.install(main, messages.get(MessageKey.REVIEW_MAIN_ACCEPT_TIP, ReviewListPane.MAIN.getDisplayText()));
        main.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                viewModel.mainAction().get().wording(Boolean.TRUE.equals(continues.getValue()))),
                        viewModel.mainAction(),
                        continues));
        main.disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !viewModel.mainAction().get().enabled(), viewModel.mainAction()));
        main.setOnAction(event -> runMain());
        // While the note panel is open, Ctrl/⌘+Enter sends the note instead.
        inline.noteOpen().addListener((observed, was, open) -> {
            main.setDefaultButton(!open);
            inline.noteConfirm().setDefaultButton(open);
        });
    }

    private void runMain() {
        final ReviewMainAction action = viewModel.mainAction().get();
        log.debug("review desk main button pressed: {}", action.key());
        if (action.isSave()) {
            viewModel.saveEdit();
        } else {
            viewModel.accept();
        }
    }

    // Ctrl+Alt+↓/↑ move between rows; every other key is left alone and not logged.
    private void moveKeys(final KeyEvent event) {
        final int delta = ReviewListPane.NEXT.match(event) ? 1 : ReviewListPane.PREVIOUS.match(event) ? -1 : 0;
        if (delta != 0) {
            event.consume();
            log.debug("review desk shortcut: move {}", delta);
            guard(() -> viewModel.move(delta));
        }
    }

    // Runs what would replace the editor's text, asking first when the person's typing is not saved.
    private boolean guard(final Runnable move) {
        final SegmentView shown = viewModel.selected().get();
        if (shown == null || !viewModel.editor().dirty().get()) {
            move.run();
            return true;
        }
        log.debug("unsaved typing in segment {}: asking before moving", shown.segmentId());
        inline.askWhile(
                new ReviewInlinePanels.Question(
                        messages.get(MessageKey.REVIEW_UNSAVED_QUESTION, shown.locator()),
                        MessageKey.REVIEW_UNSAVED_DISCARD,
                        MessageKey.REVIEW_UNSAVED_DISCARD_TIP,
                        MessageKey.REVIEW_UNSAVED_KEEP,
                        MessageKey.REVIEW_UNSAVED_KEEP_TIP),
                () -> {
                    viewModel.editor().discard();
                    move.run();
                },
                viewModel.editor().dirty());
        return false;
    }

    private void askUndo() {
        final SegmentView shown = viewModel.selected().get();
        if (shown != null) {
            inline.ask(
                    new ReviewInlinePanels.Question(
                            messages.get(MessageKey.REVIEW_UNDO_QUESTION, shown.locator()),
                            MessageKey.REVIEW_MORE_UNDO,
                            MessageKey.REVIEW_MORE_UNDO_TIP,
                            MessageKey.REVIEW_UNDO_KEEP,
                            MessageKey.REVIEW_UNDO_KEEP_TIP),
                    viewModel::revert);
        }
    }

    private void closeAsked() {
        guard(() -> {
            inline.closeNote();
            inline.closeQuestion();
            onClose.run();
        });
    }
}
