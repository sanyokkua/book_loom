package ua.bookloom.ui.control;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.Activity;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.ProgressFigures;
import ua.bookloom.ui.state.ResultOffer;
import ua.bookloom.ui.state.ResultsView;

/**
 * The one dialog of model work, on the work layer: a header with the title, a purpose line, a determinate bar and the
 * figures line; the steps beside the selected step's detail; a folded note on how progress is counted; and a footer
 * that says what Cancel does, with Continue in background for long work.
 *
 * <p>Escape hides long work and cancels short work. The dialog reads everything from its {@link ProgressDialogModel},
 * so a dialog put in the background reopens as it was. Redraws are not logged: they follow the work up to ten times a
 * second.
 */
@Slf4j
public final class ProgressDialog extends ModalCard {

    private static final double DIVIDER = 0.36;
    private static final String DETAIL_JOIN = " · ";
    private static final double FOOTNOTE_WIDTH = 380;

    private final ProgressDialogModel model;
    private final Messages messages;
    private final Label title = title("");
    private final Label purpose = new Label();
    private final ProgressBar bar = new ProgressBar();
    private final FigureLabel figures = new FigureLabel();
    private final Label details = new Label();
    private final HowCountedPane howCounted;
    private final ProgressStepDetail detail;
    private final DecisionPane decisions;
    private final BriefAnswerPane answers;
    private final ScrollPane detailScroll;
    private final Button cancel;
    private @Nullable Button apply;
    private @Nullable Boolean shownCancelling;
    private @Nullable Boolean shownOffered;

    /**
     * Builds the dialog over its model.
     *
     * @param model what the dialog shows, kept while the work runs
     * @param messages the catalogue every word comes from
     * @param onCancel what Cancel does: stop the work, or ask first
     * @param onHide what puts the work in the background, or {@code null} for short work, which cannot be hidden
     */
    public ProgressDialog(
            final ProgressDialogModel model,
            final Messages messages,
            final Runnable onCancel,
            final @Nullable Runnable onHide) {
        this(model, messages, onCancel, onHide, null);
    }

    /**
     * Builds the dialog over its model, with the export's "Write the file now".
     *
     * @param model what the dialog shows, kept while the work runs
     * @param messages the catalogue every word comes from
     * @param onCancel what Cancel does: stop the work, or ask first
     * @param onHide what puts the work in the background, or {@code null} for short work, which cannot be hidden
     * @param onWriteNow what "Write the file now" does, or {@code null} for work that writes no file
     */
    public ProgressDialog(
            final ProgressDialogModel model,
            final Messages messages,
            final Runnable onCancel,
            final @Nullable Runnable onHide,
            final @Nullable Runnable onWriteNow) {
        super(Size.XL);
        this.model = Objects.requireNonNull(model, "model");
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(onCancel, "onCancel");
        setId("busy-card");
        detail = new ProgressStepDetail(model, messages);
        howCounted = new HowCountedPane("progress-how", messages);
        decisions = new DecisionPane(model, messages);
        answers = new BriefAnswerPane(messages);
        detailScroll = new ScrollHost(detail);
        buildHeader();
        setContent(body());
        if (getContent() instanceof ScrollPane scroll) {
            scroll.setFitToHeight(true);
        }
        cancel = footer(onCancel, onHide, onWriteNow);
        setOnEscape(onHide != null ? onHide : onCancel);
        model.activity().addListener((observed, was, now) -> showActivity(now));
        model.figures().addListener((observed, was, now) -> showFigures());
        showActivity(model.activity().get());
        log.debug(
                "progress dialog built for {} #{}, hideable={}",
                model.activity().get().kind(),
                model.activity().get().id(),
                onHide != null);
    }

    /**
     * Builds the Results view: the operation's counts, steps and time, its rows and steps in tabs, and Undo all, Show
     * changed rows and Done.
     *
     * @param view the results and what the closing buttons do
     * @param messages the catalogue every word comes from
     * @return the card, for the modal host
     */
    public static ModalCard results(final ResultsView view, final Messages messages) {
        return new ProgressResultsCard(view, messages);
    }

    /**
     * Builds the Calls view: the model's calls, newest first, beside the selected call's detail, with Close.
     *
     * @param model one request step per call, each with its call attached
     * @param messages the catalogue every word comes from
     * @param onClose what Close and Escape do
     * @return the card, for the modal host
     */
    public static ModalCard calls(final ProgressDialogModel model, final Messages messages, final Runnable onClose) {
        return new ProgressCallsCard(model, messages, onClose);
    }

    private void buildHeader() {
        title.setId("progress-title");
        purpose.setId("progress-purpose");
        purpose.getStyleClass().add("dialog-sub");
        purpose.setWrapText(true);
        bar.setId("progress-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        Tips.installLive(
                bar,
                Bindings.createStringBinding(
                        () -> messages.get(
                                MessageKey.PROGRESS_BAR_STEPS,
                                model.figures().get().done(),
                                model.figures().get().total()),
                        model.figures()));
        figures.setId("progress-figures");
        figures.getStyleClass().add("dialog-sub");
        details.setId("progress-details");
        details.getStyleClass().add("dialog-sub");
        header(title, purpose, bar, figures, details).getStyleClass().add("progress-header");
    }

    private Node body() {
        detailScroll.getStyleClass().add("progress-detail-scroll");
        final ScrollPane answersScroll = new ScrollHost(answers);
        answersScroll.getStyleClass().add("progress-detail-scroll");
        showAnswers(answersScroll);
        final ScrollPane left = new ScrollHost(stepsColumn());
        left.setFitToHeight(true);
        left.getStyleClass().add("progress-detail-scroll");
        final SplitPane split = new SplitPane(left, new StackPane(detailScroll, answersScroll));
        split.setDividerPositions(DIVIDER);
        split.getStyleClass().add("progress-split");
        VBox.setVgrow(split, Priority.ALWAYS);
        final VBox body = new VBox(split, howCounted);
        body.getStyleClass().add("progress-body");
        return body;
    }

    // The export groups its steps into sections; every other work shows its decisions above one list.
    private Node stepsColumn() {
        if (model.activity().get().kind() == ActivityKind.EXPORT) {
            return new ExportStepSections(model, messages);
        }
        final ProgressStepList steps = new ProgressStepList(model, messages);
        VBox.setVgrow(steps, Priority.ALWAYS);
        decisions.managedProperty().addListener((observed, was, now) -> steps.sizeToRows(now));
        steps.sizeToRows(decisions.isManaged());
        return new VBox(decisions, decisions.stepsHeading(), steps);
    }

    // A selected decision row replaces the selected step's detail with what each sample answered for it.
    private void showAnswers(final ScrollPane answersScroll) {
        showFor(answersScroll, false);
        decisions.selected().addListener((observed, was, row) -> {
            if (row != null) {
                answers.show(row);
            }
            showFor(answersScroll, row != null);
            showFor(detailScroll, row == null);
        });
    }

    private Button footer(
            final Runnable onCancel, final @Nullable Runnable onHide, final @Nullable Runnable onWriteNow) {
        final ButtonType cancelType =
                new ButtonType(messages.get(MessageKey.BUSY_CANCEL), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType hideType =
                new ButtonType(messages.get(MessageKey.PROGRESS_BACKGROUND), ButtonBar.ButtonData.LEFT);
        if (onHide == null) {
            getButtonTypes().setAll(cancelType);
            addApply();
        } else {
            getButtonTypes().setAll(hideType, cancelType);
            wireHide((Button) lookupButton(hideType), onHide);
        }
        if (onWriteNow != null) {
            ProgressFooterActions.addWriteNow(this, messages, onWriteNow);
        }
        final Button stop = (Button) lookupButton(cancelType);
        stop.setId("progress-cancel");
        stop.getStyleClass().add("btn-danger-outline");
        stop.setOnAction(event -> {
            log.info("progress dialog: cancel pressed");
            onCancel.run();
        });
        footnote();
        return stop;
    }

    private void wireHide(final Button hide, final Runnable onHide) {
        hide.setId("progress-background");
        hide.getStyleClass().add("btn-secondary");
        Tips.install(messages, hide, MessageKey.PROGRESS_BACKGROUND_TIP);
        hide.setOnAction(event -> {
            log.info("progress dialog: continue in background pressed");
            onHide.run();
        });
    }

    // Work whose answer needs a decision gets a button that takes it, named for what it takes, disabled until the
    // answer
    // is held.
    private void addApply() {
        final MessageKey label =
                ProgressFooterActions.applyLabel(model.activity().get().kind());
        if (label == null) {
            return;
        }
        final ButtonType applyType = new ButtonType(messages.get(label), ButtonBar.ButtonData.OK_DONE);
        getButtonTypes().add(applyType);
        final Button button = (Button) lookupButton(applyType);
        button.setId("progress-apply");
        button.getStyleClass().add("btn-primary");
        button.setOnAction(event -> {
            final ResultOffer offer = model.activity().get().outcome().offer();
            log.info("progress dialog: apply pressed, an answer is held: {}", offer != null);
            if (offer != null) {
                offer.apply().run();
            }
        });
        apply = button;
    }

    private void showOffer(final Activity now) {
        final Button button = apply;
        if (button == null) {
            return;
        }
        final ResultOffer offer = now.outcome().offer();
        final boolean offered = offer != null && offer.enabled();
        button.setDisable(!offered);
        if (shownOffered == null) {
            shownOffered = offered;
            Tips.install(
                    messages,
                    button,
                    ProgressFooterActions.applyTip(model.activity().get().kind()));
        }
    }

    // The line saying what Cancel does sits in the footer's left group, after Continue in background.
    private void footnote() {
        final ActivityKind kind = model.activity().get().kind();
        final Label footnote = new Label(messages.get(
                switch (kind) {
                    case EXPORT -> MessageKey.PROGRESS_FOOTER_EXPORT;
                    case SUGGEST_STYLE -> MessageKey.BRIEF_DIALOG_WRITES_NOTHING;
                    case SUGGEST_NAME -> MessageKey.EXPORT_SUGGEST_NAME_CANCEL_KEEPS;
                    default -> MessageKey.PROGRESS_FOOTER_WRITES_NOTHING;
                }));
        footnote.setId("progress-footnote");
        footnote.getStyleClass().add("progress-footnote");
        if (kind == ActivityKind.SUGGEST_STYLE) {
            // Its Ukrainian sentence is long: held to a width the buttons beside it leave, at the smallest window.
            footnote.setMinWidth(FOOTNOTE_WIDTH);
            footnote.setPrefWidth(FOOTNOTE_WIDTH);
            footnote.setMaxWidth(FOOTNOTE_WIDTH);
        }
        ButtonBar.setButtonData(footnote, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonUniformSize(footnote, false);
        if (lookup(".button-bar") instanceof ButtonBar buttons) {
            buttons.getButtons().add(footnote);
        }
    }

    private void showActivity(final Activity now) {
        title.setText(messages.get(titleOf(now)));
        showOffer(now);
        purpose.setText(purposeOf(now));
        final Double fraction = now.fraction();
        bar.setProgress(fraction == null ? ProgressBar.INDETERMINATE_PROGRESS : fraction);
        details.setText(now.details().stream()
                .map(line -> line.label() + " " + line.value())
                .collect(Collectors.joining(DETAIL_JOIN)));
        showFor(details, !now.details().isEmpty());
        showFigures();
        showFor(cancel, now.cancellable());
        cancel.setDisable(now.cancelling());
        if (!Boolean.valueOf(now.cancelling()).equals(shownCancelling)) {
            shownCancelling = now.cancelling();
            Tips.install(
                    messages, cancel, now.cancelling() ? MessageKey.BUSY_CANCELLING_TIP : MessageKey.BUSY_CANCEL_TIP);
        }
    }

    // The work that has its own action name (the brief's fill-in, the names pass) shows it, not the title bar's
    // wording.
    private static MessageKey titleOf(final Activity now) {
        return switch (now.kind()) {
            case SUGGEST_STYLE -> MessageKey.BRIEF_DIALOG_TITLE;
            case NAMES_PASS -> MessageKey.NAMES_STYLE_AUTO;
            default -> now.title();
        };
    }

    private String purposeOf(final Activity now) {
        if (now.cancelling()) {
            return messages.get(MessageKey.BUSY_CANCELLING);
        }
        if (now.kind() == ActivityKind.SUGGEST_STYLE) {
            return messages.get(MessageKey.BRIEF_DIALOG_PURPOSE);
        }
        if (now.kind() == ActivityKind.NAMES_PASS) {
            return messages.get(MessageKey.PROGRESS_NAMES_PURPOSE);
        }
        if (now.kind() == ActivityKind.SUGGEST_NAME) {
            return messages.get(MessageKey.PROGRESS_SUGGEST_NAME_PURPOSE);
        }
        if (now.title() == MessageKey.PROGRESS_EXPORT_TITLE) {
            return messages.get(MessageKey.PROGRESS_EXPORT_PURPOSE);
        }
        if (!now.cancellable()) {
            return messages.get(MessageKey.BUSY_NOT_CANCELLABLE);
        }
        final String step = now.stepText();
        return step == null ? "" : step;
    }

    private void showFigures() {
        final boolean counted = !model.activity().get().steps().isEmpty();
        final ProgressFigures.Figures read = model.figures().get();
        figures.setText(counted ? read.line(messages) : "");
        showFor(figures, counted);
        howCounted.show(read.kinds());
    }

    /**
     * Moves the dialog's clocks, once a second while it is shown.
     *
     * @param now the moment the clocks read
     */
    public void tick(final Instant now) {
        detail.tick(now);
    }

    private static void showFor(final Node node, final boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
