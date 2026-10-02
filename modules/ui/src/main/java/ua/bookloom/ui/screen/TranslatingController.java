package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.PauseNotice;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.RunInterventions;
import ua.bookloom.ui.state.RunNotice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SectionMemory;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The translating screen's frame, which builds the dashboard once and keeps the parts that depend on the run state
 * and the log in step with the mirror.
 *
 * <p>The dashboard is built with no book open as well: the words for what a start is missing are shown on this same
 * screen, so the controls must exist to be pressed. The mirror, the view model's notice and the log outlive this
 * controller, so all three are observed through weak listeners held by the fields below, and the host keeps a reference to the controller in its
 * properties, which is what lets the listeners live exactly as long as the screen does. The log follows its newest
 * line by itself ({@link ua.bookloom.ui.control.TaggedLog}).
 */
@Slf4j
public final class TranslatingController {

    private final TranslatingViewModel viewModel;
    private final StateMirror mirror;
    private final Messages messages;
    private final Navigator navigator;
    private final CurrentProject current;
    private final LanguageNames names;
    private final ReviewViewModel review;
    private final RetryWithNoteDialog retryDialog;
    private final ReviewPauseFollower pauses;
    private final RunInterventions interventions;
    private final SectionMemory sections;
    private final ChangeListener<RunState> onState = (observed, was, now) -> renderState(now);
    private final ChangeListener<@Nullable RunNotice> onNotice = (observed, was, now) -> renderNotice(now);
    private final ChangeListener<Number> onWaiting = (observed, was, now) -> renderWaiting(now.intValue());
    private final ChangeListener<Number> onChunk = (observed, was, now) -> renderChunk();
    private final ChangeListener<@Nullable PauseNotice> onPauseNotice = (observed, was, now) -> renderChunk();
    private final ChangeListener<@Nullable RecoveryState> onRecovery = (observed, was, now) -> renderChunk();

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
     * @param current the open book, whose languages head the live panel's panes
     * @param names the names of those languages
     * @param review the review panel's view model, which counts the flagged segments the panel's button names
     * @param retryDialog the card a retry with a note is asked in
     * @param pauses what opens the review panel on a review pause and continues the run after the person's decision
     * @param interventions what skips a stuck or failed segment and sends a stuck request again
     * @param sections the session's memory of which sections the person left open
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public TranslatingController(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final Messages messages,
            final Navigator navigator,
            final CurrentProject current,
            final LanguageNames names,
            final ReviewViewModel review,
            final RetryWithNoteDialog retryDialog,
            final ReviewPauseFollower pauses,
            final RunInterventions interventions,
            final SectionMemory sections) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.current = Objects.requireNonNull(current, "current");
        this.names = Objects.requireNonNull(names, "names");
        this.review = Objects.requireNonNull(review, "review");
        this.retryDialog = Objects.requireNonNull(retryDialog, "retryDialog");
        this.pauses = Objects.requireNonNull(pauses, "pauses");
        this.interventions = Objects.requireNonNull(interventions, "interventions");
        this.sections = Objects.requireNonNull(sections, "sections");
    }

    @FXML
    void initialize() {
        log.debug(
                "building the translating screen in state {}", mirror.runState().get());
        dashboard = TranslatingView.build(
                viewModel,
                mirror,
                current,
                names,
                messages,
                new TranslatingView.Exits(
                        navigator, this::openSettings, review, retryDialog, pauses, interventions, sections));
        viewModel.refreshPending();
        review.refreshCount();
        host.getChildren().setAll(dashboard.root());
        host.getProperties().put(TranslatingController.class, this);
        mirror.runState().addListener(new WeakChangeListener<>(onState));
        viewModel.notice().addListener(new WeakChangeListener<>(onNotice));
        mirror.waitingSeconds().addListener(new WeakChangeListener<>(onWaiting));
        mirror.chunk().addListener(new WeakChangeListener<>(onChunk));
        mirror.chunks().addListener(new WeakChangeListener<>(onChunk));
        mirror.review().pauseNotice().addListener(new WeakChangeListener<>(onPauseNotice));
        mirror.review().recovery().addListener(new WeakChangeListener<>(onRecovery));
        dashboard.render(
                mirror.runState().get(),
                viewModel.notice().get(),
                mirror.waitingSeconds().get());
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

    // Not logged: the chunk changes with every chunk of the run; it matters only to the paused banner's wording.
    private void renderChunk() {
        dashboard.render(
                mirror.runState().get(),
                viewModel.notice().get(),
                mirror.waitingSeconds().get());
    }

    private void render(final RunState state, final @Nullable RunNotice notice) {
        log.debug(
                "showing run state {}, notice {}",
                state,
                notice == null ? null : notice.getClass().getSimpleName());
        if (notice instanceof RunNotice.ProviderError provider) {
            log.debug("provider-error state shown for code {}", provider.error().code());
        }
        dashboard.render(state, notice, mirror.waitingSeconds().get());
    }

    private void openSettings() {
        log.debug("the provider-error banner offered the settings: going there");
        navigator.navigate(ViewNames.SETTINGS);
    }
}
