package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.RunNotice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The translating screen's frame, which builds the dashboard once and keeps the parts that depend on the run state
 * and the log in step with the mirror.
 *
 * <p>The dashboard is built with no book open as well: the words for what a start is missing are shown on this same
 * screen, so the controls must exist to be pressed. The mirror, the view model's notice and the log outlive this
 * controller, so all three are observed through weak listeners held by the fields below, and the host keeps a reference to the controller in its
 * properties, which is what lets the listeners live exactly as long as the screen does. No line is logged when the log grows: that
 * listener runs for every batch of a whole book's decisions.
 */
@Slf4j
public final class TranslatingController {

    private final TranslatingViewModel viewModel;
    private final StateMirror mirror;
    private final Messages messages;
    private final Navigator navigator;
    private final ChangeListener<RunState> onState = (observed, was, now) -> renderState(now);
    private final ChangeListener<@Nullable RunNotice> onNotice = (observed, was, now) -> renderNotice(now);
    private final ChangeListener<Number> onWaiting = (observed, was, now) -> renderWaiting(now.intValue());
    private final ListChangeListener<LogEntry> onLog = change -> scrollLog();

    @FXML
    private Pane host;

    private TranslatingDashboard dashboard;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel what the controls press and the buttons' availability is read from
     * @param mirror the run's state, figures and log the dashboard shows
     * @param messages the catalogue the built parts are worded from
     * @param navigator where the provider-error banner's route to the settings leads
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public TranslatingController(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final Messages messages,
            final Navigator navigator) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    @FXML
    void initialize() {
        log.debug(
                "building the translating screen in state {}", mirror.runState().get());
        dashboard = TranslatingView.build(viewModel, mirror, messages, this::openSettings);
        host.getChildren().setAll(dashboard.root());
        host.getProperties().put(TranslatingController.class, this);
        mirror.runState().addListener(new WeakChangeListener<>(onState));
        viewModel.notice().addListener(new WeakChangeListener<>(onNotice));
        mirror.waitingSeconds().addListener(new WeakChangeListener<>(onWaiting));
        mirror.activityLog().addListener(new WeakListChangeListener<>(onLog));
        dashboard.render(
                mirror.runState().get(),
                viewModel.notice().get(),
                mirror.waitingSeconds().get());
    }

    private void scrollLog() {
        dashboard.scrollToNewest();
    }

    private void renderState(final RunState state) {
        render(state, viewModel.notice().get());
    }

    private void renderNotice(final @Nullable RunNotice notice) {
        render(mirror.runState().get(), notice);
    }

    // Not logged: the wait changes once a second for as long as a request is slow, and the session already logs when it
    // is first shown and when it is cleared.
    private void renderWaiting(final int seconds) {
        dashboard.render(mirror.runState().get(), viewModel.notice().get(), seconds);
    }

    private void render(final RunState state, final @Nullable RunNotice notice) {
        log.debug(
                "showing run state {}, notice {}",
                state,
                notice == null ? null : notice.getClass().getSimpleName());
        dashboard.render(state, notice, mirror.waitingSeconds().get());
    }

    private void openSettings() {
        log.debug("the provider-error banner offered the settings: going there");
        navigator.navigate(ViewNames.SETTINGS);
    }
}
