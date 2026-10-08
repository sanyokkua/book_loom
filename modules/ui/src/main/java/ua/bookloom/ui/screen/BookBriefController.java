package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.BookTitleViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The book-brief screen's frame, which shows the brief while a book is open and the no-book state while none is.
 *
 * <p>The open book is observed through a weak listener held by the field below, because the view model outlives this
 * controller. Nothing in the frame's own nodes captures the controller, so the host keeps a reference to it in its
 * properties: that is what lets the swap still happen when a book is opened under a screen that is on show, and lets
 * the controller go when the screen does.
 */
@Slf4j
public final class BookBriefController {

    private final BookBriefViewModel viewModel;
    private final BookTitleViewModel titles;
    private final CurrentProject project;
    private final Messages messages;
    private final Navigator navigator;
    private final LanguageNames names;
    private final SettingsViewModel settings;
    private final WorkflowProgress progress;
    private final ChangeListener<@Nullable OpenedBook> onBook = (observed, was, now) -> show(now != null);

    @FXML
    private Pane stateHost;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel the choices the brief assembles
     * @param titles the translated title and author the brief shows
     * @param project the holder of the open book they are about
     * @param messages the catalogue the built parts are worded from
     * @param navigator where Back, Continue and the route from the no-book state lead
     * @param names how the language boxes name and recognise languages
     * @param settings where the model row reads the chosen model from
     * @param progress where Continue records that the brief step is done
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public BookBriefController(
            final BookBriefViewModel viewModel,
            final BookTitleViewModel titles,
            final CurrentProject project,
            final Messages messages,
            final Navigator navigator,
            final LanguageNames names,
            final SettingsViewModel settings,
            final WorkflowProgress progress) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.titles = Objects.requireNonNull(titles, "titles");
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.names = Objects.requireNonNull(names, "names");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    @FXML
    void initialize() {
        final boolean bookOpen = project.book().get() != null;
        log.debug("building the book-brief screen, a book is open: {}", bookOpen);
        stateHost.getProperties().put(BookBriefController.class, this);
        project.book().addListener(new WeakChangeListener<>(onBook));
        show(bookOpen);
    }

    private void show(final boolean bookOpen) {
        log.debug("showing the {}", bookOpen ? "brief" : "no-book state");
        final Node content = bookOpen
                ? new BriefView(viewModel, titles, messages, navigator, names, settings, progress).root()
                : NoBookView.build(messages, navigator);
        stateHost.getChildren().setAll(content);
    }
}
