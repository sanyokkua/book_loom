package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookTitleViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.DestinationChooser;
import ua.bookloom.ui.state.ExportFigures;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.HiddenWork;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The export screen, laid out like the mockup: the banners, the tiles across the top, the title card and the card that
 * writes the book beside the side-file cards, and the step footer whose forward action is ⬇ Export book, in the
 * toolbar, with the reason beside it whenever it cannot be pressed.
 *
 * <p>Everything a person can set or read is the view model's; this class only arranges it and fills the result parts
 * when an export finishes. The view model outlives it, so it is observed through weak listeners held by the fields
 * below and the controller keeps the view alive with its nodes.
 */
@Slf4j
final class ExportView {

    private static final double SCREEN_SPACING = 14;
    private static final double LINE_SPACING = 4;

    private final ExportViewModel viewModel;
    private final CurrentProject project;
    private final Messages messages;
    private final Navigator navigator;
    private final ExportTitleCard titles;
    private final BookTitleViewModel titleModel;
    private final ExportDestinationCard destination;
    private final ExportSideFilesColumn sideFiles;
    private final ExportResult result;
    private final ExportBanners banners;
    private final HiddenWork hidden;
    private final VBox statement = new VBox(LINE_SPACING);
    private final ListChangeListener<String> onStatement = change -> showStatement();
    private final ChangeListener<@Nullable ExportFigures> onFigures = (observed, was, now) -> showFigures(now);
    private final ChangeListener<String> onReason = (observed, was, now) -> explain();
    private @Nullable StepFooter footer;
    // A stored edit of the title or author makes a file name the model suggested worth suggesting again, on request.
    private final ChangeListener<Number> onTitleEdited;
    private final WeakChangeListener<Number> weakTitleEdited;

    ExportView(
            final ExportViewModel viewModel,
            final BookTitleViewModel titleModel,
            final CurrentProject project,
            final Messages messages,
            final Navigator navigator,
            final DestinationChooser chooser,
            final FileRevealer revealer,
            final TranslatingViewModel translating,
            final HiddenWork hidden) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.titleModel = Objects.requireNonNull(titleModel, "titleModel");
        this.onTitleEdited = (observed, was, now) -> {
            log.debug("the title or author was edited; a suggested file name gets a notice");
            viewModel.nameSuggestion.afterTitleEdit();
        };
        this.weakTitleEdited = new WeakChangeListener<>(onTitleEdited);
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.titles = new ExportTitleCard(titleModel, messages);
        this.destination = new ExportDestinationCard(viewModel, messages, chooser);
        this.sideFiles = new ExportSideFilesColumn(viewModel, messages);
        this.result = new ExportResult(messages, revealer, navigator);
        this.hidden = Objects.requireNonNull(hidden, "hidden");
        this.banners = new ExportBanners(viewModel, translating, hidden, messages);
        viewModel.figures().addListener(new WeakChangeListener<>(onFigures));
        viewModel.blockReason().addListener(new WeakChangeListener<>(onReason));
        viewModel.runNote().addListener(new WeakChangeListener<>(onReason));
    }

    /** The content shown while no book is open: no separate empty state, only the disabled action and its footer. */
    Node empty() {
        log.debug("building the export screen with no book open");
        final Label subtitle = ImportViews.wrapped(messages.get(MessageKey.EXPORT_SUBTITLE), "muted");
        subtitle.setId("export-subtitle");
        return new VBox(SCREEN_SPACING, subtitle, result.tiles(), footer());
    }

    /** The screen's content for {@code book}, filled for the export the view model last finished, if any. */
    Node build(final OpenedBook book) {
        Objects.requireNonNull(book, "book");
        log.debug("building the export screen for project {}", book.projectId());
        final Label subtitle = ImportViews.wrapped(messages.get(MessageKey.EXPORT_SUBTITLE), "muted");
        subtitle.setId("export-subtitle");
        // build() runs on every book change; the one listener is moved, not added again.
        titleModel.edits().removeListener(weakTitleEdited);
        titleModel.edits().addListener(weakTitleEdited);
        final VBox left = new VBox(SCREEN_SPACING, titles.node(), writeCard(book));
        // The side files stack under the main cards in a window too narrow for both, rather than cutting either.
        final TwoColumns columns = TwoColumns.withSideColumn(left, sideFiles.build(), LayoutClass.Width.REGULAR);
        columns.setId("export-columns");
        // Work in the background writes this book: its choices wait until it ends.
        columns.disableProperty().bind(hidden.freezes(ViewNames.EXPORT));
        showOutcome(viewModel.outcome().get());
        showFigures(viewModel.figures().get());
        viewModel.refreshStatement();
        return new VBox(SCREEN_SPACING, subtitle, banners.build(), result.tiles(), columns, footer());
    }

    private void showFigures(final @Nullable ExportFigures figures) {
        result.showFigures(figures);
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

    // Back leads to Translating; the forward action is ⬇ Export book, which asks before replacing a file.
    private Node footer() {
        final StepFooter built = StepFooter.of(
                        new StepFooter.Action(
                                "export-back",
                                messages.get(MessageKey.EXPORT_BACK),
                                messages.get(MessageKey.EXPORT_BACK_TIP),
                                "btn-ghost",
                                () -> navigator.navigate(ViewNames.TRANSLATING)),
                        new StepFooter.Action(
                                "export-run",
                                messages.get(MessageKey.EXPORT_ACTION),
                                messages.get(MessageKey.EXPORT_ACTION_TIP),
                                "btn-primary",
                                this::exportPressed))
                .withCompactBack(messages.get(MessageKey.COMMON_BACK_COMPACT));
        built.forwardButton()
                .disableProperty()
                .bind(viewModel
                        .exportAvailable()
                        .not()
                        .or(viewModel.blockReason().isNotEmpty()));
        built.withRunGroup(result.toolbarActions());
        footer = built;
        explain();
        return built;
    }

    private void exportPressed() {
        log.debug(
                "export book pressed, available {}", viewModel.exportAvailable().get());
        viewModel.export();
    }

    private void explain() {
        final StepFooter shown = footer;
        if (shown == null) {
            return;
        }
        final String reason = viewModel.blockReason().get();
        shown.explainDisabledForward(reason.isEmpty() ? null : reason);
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
                failure());
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
