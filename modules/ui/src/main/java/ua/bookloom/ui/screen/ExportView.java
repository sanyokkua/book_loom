package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.DestinationChooser;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.OpenedBook;

/**
 * The export screen for an open book, laid out like the mockup: the result tiles across the top, the card that writes
 * the book beside the side-file cards, and the step footer.
 *
 * <p>Everything a person can set or read is the view model's; this class only arranges it and fills the result parts
 * when an export finishes. The view model outlives it, so it is observed through weak listeners held by the fields
 * below and the controller keeps the view alive with its nodes.
 */
@Slf4j
final class ExportView {

    private static final double SCREEN_SPACING = 14;
    private static final double CARD_SPACING = 12;
    private static final double LINE_SPACING = 4;

    private final ExportViewModel viewModel;
    private final CurrentProject project;
    private final Messages messages;
    private final Navigator navigator;
    private final ExportDestinationCard destination;
    private final ExportSideFilesColumn sideFiles;
    private final ExportResult result;
    private final VBox statement = new VBox(LINE_SPACING);
    private final ListChangeListener<String> onStatement = change -> showStatement();

    ExportView(
            final ExportViewModel viewModel,
            final CurrentProject project,
            final Messages messages,
            final Navigator navigator,
            final DestinationChooser chooser,
            final FileRevealer revealer) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.destination = new ExportDestinationCard(viewModel, messages, chooser);
        this.sideFiles = new ExportSideFilesColumn(viewModel, messages);
        this.result = new ExportResult(messages, revealer);
    }

    /** The screen's content for {@code book}, filled for the export the view model last finished, if any. */
    Node build(final OpenedBook book) {
        Objects.requireNonNull(book, "book");
        log.debug("building the export screen for project {}", book.projectId());
        final Label subtitle = ImportViews.wrapped(messages.get(MessageKey.EXPORT_SUBTITLE), "muted");
        subtitle.setId("export-subtitle");
        final VBox left = new VBox(SCREEN_SPACING, writeCard(book));
        HBox.setHgrow(left, Priority.ALWAYS);
        final HBox columns = new HBox(SCREEN_SPACING, left, sideFiles.build());
        showOutcome(viewModel.outcome().get());
        viewModel.refreshStatement();
        return new VBox(SCREEN_SPACING, subtitle, result.tiles(), columns, footer(messages, navigator));
    }

    /** Fills or clears the result parts; called on the FX thread when the view model's outcome changes. */
    void showOutcome(final @Nullable ExportOutcome outcome) {
        final OpenedBook book = project.book().get();
        final BookBrief brief = project.brief().get();
        if (book == null || brief == null) {
            return;
        }
        result.show(outcome, book.inspection(), tag(brief.sourceLanguage()), tag(brief.targetLanguage()));
    }

    /** The step footer whose forward action is drawn unavailable, because no step follows the export. */
    static Node footer(final Messages messages, final Navigator navigator) {
        return StepFooter.withUnavailableForward(
                new StepFooter.Action(
                        "export-back",
                        messages.get(MessageKey.BRIEF_BACK),
                        messages.get(MessageKey.BRIEF_BACK_TIP),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.TRANSLATING)),
                new StepFooter.Action(
                        "export-next",
                        messages.get(MessageKey.EXPORT_NEXT),
                        messages.get(MessageKey.EXPORT_NEXT_TIP),
                        "btn-primary",
                        () -> {}));
    }

    private Node writeCard(final OpenedBook book) {
        final BookFormat format = Objects.requireNonNull(book.inspection().format(), "format");
        final Node formatRow = ImportViews.keyValue(
                "export-format",
                messages.get(MessageKey.EXPORT_FORMAT_LABEL),
                messages.get(MessageKey.EXPORT_FORMAT_SAME, messages.get(ImportViews.formatName(format))));
        return BriefCards.card(
                "export-card",
                messages,
                MessageKey.EXPORT_CARD_TITLE,
                formatRow,
                BriefCards.hint(messages, MessageKey.EXPORT_FORMAT_HINT),
                result.checks(),
                destination.build(),
                statementBox(),
                runRow(),
                failure(),
                result.actions());
    }

    private Node statementBox() {
        statement.setId("export-statement");
        viewModel.statement().addListener(new WeakListChangeListener<>(onStatement));
        showStatement();
        return statement;
    }

    private void showStatement() {
        statement.getChildren().clear();
        for (final String line : viewModel.statement()) {
            final Label label = new Label(line);
            label.setWrapText(true);
            label.getStyleClass().add("hint");
            statement.getChildren().add(label);
        }
    }

    private Node runRow() {
        final Button run = new Button(messages.get(MessageKey.EXPORT_ACTION));
        run.setId("export-run");
        Tips.install(messages, run, MessageKey.EXPORT_ACTION_TIP);
        run.getStyleClass().add("btn-primary");
        run.disableProperty().bind(viewModel.exportAvailable().not());
        run.setOnAction(event -> {
            log.debug(
                    "export book pressed, available {}",
                    viewModel.exportAvailable().get());
            viewModel.export();
        });
        final Label note = new Label();
        note.setId("export-note");
        note.getStyleClass().add("hint");
        note.textProperty().bind(viewModel.runNote());
        return new HBox(
                CARD_SPACING,
                run,
                BriefCards.shownWhile(note, viewModel.runNote().isNotEmpty()));
    }

    private Node failure() {
        final Label failure = new Label();
        failure.setId("export-failure");
        failure.setWrapText(true);
        failure.getStyleClass().add("status-err");
        failure.textProperty().bind(viewModel.failure());
        return BriefCards.shownWhile(failure, viewModel.failure().isNotEmpty());
    }

    private static String tag(final @Nullable String language) {
        return language == null ? "" : language;
    }
}
