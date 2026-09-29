package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.StructureListing;

/**
 * The structure screen's frame, which lists the book's structure while a book is open and shows the no-book state while
 * none is.
 *
 * <p>The open book is observed through a weak listener held by the field below, because the view model outlives this
 * controller. Nothing in the frame's own nodes captures the controller, so the host keeps a reference to it in its
 * properties: that is what lets the swap still happen when a book is opened under a screen that is on show, and lets
 * the controller go when the screen does.
 */
@Slf4j
public final class StructureController {

    private final CurrentProject project;
    private final Messages messages;
    private final Navigator navigator;
    private final ChangeListener<@Nullable OpenedBook> onBook = (observed, was, now) -> show(now);

    @FXML
    private Pane stateHost;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param project the holder of the open book whose structure is listed
     * @param messages the catalogue the built parts are worded from
     * @param navigator where the route from the no-book state leads
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public StructureController(final CurrentProject project, final Messages messages, final Navigator navigator) {
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    @FXML
    void initialize() {
        final OpenedBook book = project.book().get();
        log.debug("building the structure screen, a book is open: {}", book != null);
        stateHost.getProperties().put(StructureController.class, this);
        project.book().addListener(new WeakChangeListener<>(onBook));
        show(book);
    }

    private void show(final @Nullable OpenedBook book) {
        log.debug("showing the {}", book != null ? "structure" : "no-book state");
        final Node content = book != null
                ? StructureView.build(listingOf(book), messages, navigator)
                : NoBookView.build(messages, navigator);
        stateHost.getChildren().setAll(content);
    }

    private static StructureListing listingOf(final OpenedBook book) {
        final var profile = book.profile();
        return profile == null ? new StructureListing(List.of()) : StructureListing.of(profile);
    }
}
