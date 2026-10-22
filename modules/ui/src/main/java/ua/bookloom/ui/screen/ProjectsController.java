package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LastOpened;
import ua.bookloom.ui.state.LocalModelProbe;
import ua.bookloom.ui.state.ProjectsState;
import ua.bookloom.ui.state.ProjectsViewModel;
import ua.bookloom.ui.state.RunStateSource;

/**
 * The Projects screen's frame: the list while projects are stored, the first-launch card while none are, and nothing
 * while they are being read or could not be. The toolbar's "New translation" belongs to the list only, because the
 * card has its own.
 *
 * <p>The view model's state is watched through a weak listener held by a field, and the host keeps this controller in
 * its properties, so the listener lives exactly as long as the screen does.
 */
@Slf4j
public final class ProjectsController {

    private final ProjectsViewModel viewModel;
    private final Messages messages;
    private final Navigator navigator;
    private final ProjectsListView list;
    private final ProjectsEmptyView empty;
    private final ChangeListener<ProjectsState> onState = (observed, was, now) -> show(now);

    @FXML
    private Pane host;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel the stored projects and what the controls do
     * @param messages the catalogue the built parts are worded from
     * @param navigator where New translation and Settings lead
     * @param lastOpened the words for when a project was last opened
     * @param runState the chosen AI model the first-launch card names
     * @param probe the look for a local AI program the card shows and can repeat
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public ProjectsController(
            final ProjectsViewModel viewModel,
            final Messages messages,
            final Navigator navigator,
            final LastOpened lastOpened,
            final RunStateSource runState,
            final LocalModelProbe probe) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.list = new ProjectsListView(messages, viewModel, lastOpened);
        this.empty = new ProjectsEmptyView(messages, navigator, runState, Objects.requireNonNull(probe, "probe"));
    }

    @FXML
    void initialize() {
        host.getProperties().put(ProjectsController.class, this);
        viewModel.state().addListener(new WeakChangeListener<>(onState));
        viewModel.refresh();
        show(viewModel.state().get());
    }

    private void show(final ProjectsState state) {
        log.debug("showing the projects screen in state {}", state);
        final Node content =
                switch (state) {
                    case LIST -> new VBox(14, list.build(), toolbar());
                    case EMPTY -> empty.build();
                    case LOADING, FAILED -> new VBox();
                };
        host.getChildren().setAll(content);
    }

    private StepFooter toolbar() {
        return StepFooter.of(
                null,
                new StepFooter.Action(
                        "projects-new",
                        messages.get(MessageKey.PROJECTS_NEW),
                        messages.get(MessageKey.PROJECTS_NEW_TIP),
                        "btn-primary",
                        () -> navigator.openImportEmpty()));
    }
}
