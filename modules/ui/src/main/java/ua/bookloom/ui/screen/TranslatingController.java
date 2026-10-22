package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.BriefEstimate;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.SceneSummary;
import ua.bookloom.ui.state.SectionMemory;
import ua.bookloom.ui.state.SleepHelp;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TechnicalDetails;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The translating screen's frame, which builds the screen once per visit.
 *
 * <p>The screen is built with no book open as well: the words for what a start is missing are shown on this same
 * screen. Every part that follows the run binds or listens weakly, so a screen replaced on the next visit is not kept
 * alive by the mirror, the view models or the run-state row it observed; the run's banner is the shell's
 * ({@link ua.bookloom.ui.control.RunBanner}), shown on every screen.
 */
@Slf4j
public final class TranslatingController {

    private final TranslatingViewModel viewModel;
    private final StateMirror mirror;
    private final Messages messages;
    private final CurrentProject current;
    private final LanguageNames names;
    private final ReviewViewModel review;
    private final TranslatingDialogs dialogs;
    private final ReviewPauseFollower pauses;
    private final SectionMemory sections;
    private final Navigator navigator;
    private final RunStateSource runs;
    private final TranslatingView.Reads reads;

    @FXML
    private Pane host;

    @FXML
    private Label title;

    @FXML
    private Label subtitle;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel what the run actions press and the ready card reads
     * @param mirror the run's state, figures and log the screen shows
     * @param messages the catalogue the built parts are worded from
     * @param navigator where Back and Continue to Export lead
     * @param current the open book, whose languages head the live panel's panes
     * @param names the names of those languages
     * @param review the review panel's view model, which counts the flagged segments
     * @param retryDialog the card a retry with a note is asked in
     * @param pauses what opens the review panel on a review pause and continues the run after the person's decision
     * @param sections the session's memory of which sections the person left open
     * @param narratorDialog the question asked before a start when the book is told in the first person
     * @param confirm the question asked before the run is stopped
     * @param runs the run-state row the run actions and the live line follow
     * @param parts what the strip, the help line and the live card read and open: the start estimate, the scene
     *     summary, the sleep help, the technical-details switch, the modal host and the activities' clock
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
            final SectionMemory sections,
            final NarratorDialog narratorDialog,
            final ConfirmDialog confirm,
            final RunStateSource runs,
            final Parts parts) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.current = Objects.requireNonNull(current, "current");
        this.names = Objects.requireNonNull(names, "names");
        this.review = Objects.requireNonNull(review, "review");
        this.dialogs = new TranslatingDialogs(
                Objects.requireNonNull(retryDialog, "retryDialog"),
                Objects.requireNonNull(narratorDialog, "narratorDialog"),
                Objects.requireNonNull(confirm, "confirm"),
                parts.modals(),
                messages,
                parts.activities());
        this.pauses = Objects.requireNonNull(pauses, "pauses");
        this.sections = Objects.requireNonNull(sections, "sections");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.reads = new TranslatingView.Reads(parts.estimate(), parts.summary(), parts.sleep(), parts.technical());
    }

    /**
     * The collaborators the strip, the help line and the live card need, injected as one so the controller's
     * constructor stays short.
     *
     * @param estimate the start estimate the strip shows before a run
     * @param summary the scene summary line's text
     * @param sleep the sleep help line
     * @param technical whether technical details are on
     * @param modals where "All calls…" opens
     * @param activities whose clock the calls dialog reads
     */
    public record Parts(
            BriefEstimate estimate,
            SceneSummary summary,
            SleepHelp sleep,
            TechnicalDetails technical,
            ModalHost modals,
            ActivityTracker activities) {

        /** Receives the parts from the injector. */
        @Inject
        public Parts {
            Objects.requireNonNull(estimate, "estimate");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(sleep, "sleep");
            Objects.requireNonNull(technical, "technical");
            Objects.requireNonNull(modals, "modals");
            Objects.requireNonNull(activities, "activities");
        }
    }

    @FXML
    void initialize() {
        log.debug(
                "building the translating screen in state {}", mirror.runState().get());
        final TranslatingView.Exits exits =
                new TranslatingView.Exits(navigator, review, dialogs, pauses, sections, runs);
        // Once a run has started the live card needs the height, as the mockup's running page has no heading either.
        final BooleanBinding beforeRun = mirror.runState().isEqualTo(RunState.IDLE);
        for (final Label heading : new Label[] {title, subtitle}) {
            heading.visibleProperty().bind(beforeRun);
            heading.managedProperty().bind(beforeRun);
        }
        host.getChildren().setAll(TranslatingView.build(viewModel, mirror, current, names, messages, exits, reads));
        viewModel.refreshPending();
        review.refreshCount();
    }
}
