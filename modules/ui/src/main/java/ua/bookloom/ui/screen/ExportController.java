package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookTitleViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.DestinationChooser;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.HiddenWork;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The export screen's frame: the destination, side files and result for an open book, and only the unavailable action
 * while none is. A finished export is announced by {@code ExportAnnouncer}, which outlives this screen.
 *
 * <p>The open book and the export's outcome are observed through weak listeners held by the fields below, because
 * both view models outlive this controller. The host keeps a reference to the controller in its properties, which is
 * what lets the listeners live exactly as long as the screen does. The screen is rebuilt on each visit, so an outcome
 * already there on arrival fills the result without opening the dialog again.
 */
@Slf4j
public final class ExportController {

    private final ExportViewModel viewModel;
    private final CurrentProject project;
    private final Messages messages;
    private final ExportView view;
    private final ChangeListener<@Nullable OpenedBook> onBook = (observed, was, now) -> show(now);
    private final ChangeListener<@Nullable ExportOutcome> onOutcome = (observed, was, now) -> onOutcome(now);

    @FXML
    private Label title;

    @FXML
    private Pane host;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel the destination, choices and export this screen presents
     * @param titles the translated title and author the Title and author card edits
     * @param project the holder of the open book
     * @param messages the catalogue the built parts are worded from
     * @param navigator where Back and the route from the no-book state lead
     * @param chooser the save dialog behind Browse
     * @param revealer what Open folder and Open book ask of the system
     * @param translating the run, which the banner's Pause pauses
     * @param hidden the work in the background, which makes the screen read-only
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public ExportController(
            final ExportViewModel viewModel,
            final BookTitleViewModel titles,
            final CurrentProject project,
            final Messages messages,
            final Navigator navigator,
            final DestinationChooser chooser,
            final FileRevealer revealer,
            final TranslatingViewModel translating,
            final HiddenWork hidden) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.view =
                new ExportView(viewModel, titles, project, messages, navigator, chooser, revealer, translating, hidden);
    }

    @FXML
    void initialize() {
        host.getProperties().put(ExportController.class, this);
        project.book().addListener(new WeakChangeListener<>(onBook));
        viewModel.outcome().addListener(new WeakChangeListener<>(onOutcome));
        show(project.book().get());
    }

    private void show(final @Nullable OpenedBook book) {
        log.debug("showing the export screen: a book is open {}", book != null);
        final Node content = book == null ? view.empty() : view.build(book);
        host.getChildren().setAll(content);
        showTitle(book == null ? null : viewModel.outcome().get());
    }

    private void onOutcome(final @Nullable ExportOutcome outcome) {
        log.debug("the export outcome changed: written {}", outcome != null);
        view.showOutcome(outcome);
        showTitle(outcome);
    }

    // A book written with segments still untranslated is not "ready": it is the book so far.
    private void showTitle(final @Nullable ExportOutcome outcome) {
        final MessageKey key = outcome == null
                ? MessageKey.NAV_EXPORT
                : outcome.report().pending() > 0 ? MessageKey.EXPORT_TITLE_PARTIAL : MessageKey.EXPORT_TITLE;
        log.debug("export title {}", key);
        title.setText(messages.get(key));
    }
}
