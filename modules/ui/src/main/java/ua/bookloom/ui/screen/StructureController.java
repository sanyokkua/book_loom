package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.StructureChecksViewModel;
import ua.bookloom.ui.state.StructureListing;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The structure screen's frame, which shows the book's structure, statistics and checks while a book is open and shows the no-book state while
 * none is.
 *
 * <p>The open book is observed through a weak listener held by the field below, because the view model outlives this
 * controller. Nothing in the frame's own nodes captures the controller, so the host keeps a reference to it in its
 * properties: that is what lets the swap still happen when a book is opened under a screen that is on show, and lets
 * the controller go when the screen does.
 */
@Slf4j
public final class StructureController {

    private static final BookStats EMPTY_STATS = new BookStats(0, 0, 0, 0, 0, 0, 0, 0, Set.of());

    private final CurrentProject project;
    private final Messages messages;
    private final Navigator navigator;
    private final WorkflowProgress progress;
    private final StructureChecksViewModel checks;
    private final ChangeListener<@Nullable OpenedBook> onBook = (observed, was, now) -> show(now);

    @FXML
    private Pane stateHost;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param project the holder of the open book whose structure is listed
     * @param messages the catalogue the built parts are worded from
     * @param navigator where the route from the no-book state leads
     * @param progress where Continue records that the structure step is done
     * @param checks the background round-trip and chunk-budget checks the screen shows
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public StructureController(
            final CurrentProject project,
            final Messages messages,
            final Navigator navigator,
            final WorkflowProgress progress,
            final StructureChecksViewModel checks) {
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.checks = Objects.requireNonNull(checks, "checks");
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
        final Node content = book != null ? structureOf(book) : NoBookView.build(messages, navigator);
        stateHost.getChildren().setAll(content);
    }

    private Node structureOf(final OpenedBook book) {
        final BookProfile profile = book.profile();
        final StructureListing listing =
                profile == null ? new StructureListing(List.of()) : StructureListing.of(profile);
        final BookStats stats = profile == null ? EMPTY_STATS : profile.stats();
        return new StructureView(messages, navigator, progress, checks).build(listing, stats, book.projectId());
    }
}
