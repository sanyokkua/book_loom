package ua.bookloom.ui.screen;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.LiveCallPanel;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.TaggedLog;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.BlockedWords;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ReviewPauseFollower;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.SectionMemory;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * Builds the Translating screen: the live line of the run-state row, the ready card before a run, the progress card and
 * tiles while one lives, the outcome once it ends, the live panel and log, and the footer whose run actions the
 * toolbar shows.
 *
 * <p>Everything that follows a property is bound to it, and the bindings hold their sources weakly, so a screen that
 * is replaced on the next visit is not kept alive by the mirror it observed. Which cards show is decided by the run
 * state alone: the ready card while idle, the progress card and tiles while a run is under way or stopped, the
 * outcome once it completed or failed. The run actions and the banner follow the run-state row ({@link RunStateSource})
 * instead, so they read as the title bar does. The log's cells log nothing, since they are refreshed on every scroll.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingView {

    private static final double SCREEN_SPACING = 14;
    private static final double CARD_SPACING = 8;
    private static final double TILE_SPACING = 12;

    private static final Set<RunState> UNDER_WAY =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPING, RunState.STOPPED);
    private static final Set<RunState> ENDED = Set.of(RunState.COMPLETED, RunState.FAILED);
    private static final Set<RunState> REVIEWABLE =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPED, RunState.COMPLETED);
    private static final Set<RunState> WATCHED = EnumSet.complementOf(EnumSet.of(RunState.IDLE));

    /**
     * What the screen's own buttons do that the view model does not: leave for another step, open the review panel,
     * whose view model counts the flagged segments and whose retry asks its note in a card, and read the run-state
     * row; and the session's memory of which sections the person left open, which outlives the rebuilt screen.
     */
    record Exits(
            Navigator navigator,
            ReviewViewModel review,
            TranslatingDialogs dialogs,
            ReviewPauseFollower pauses,
            SectionMemory sections,
            RunStateSource runs) {}

    static Node build(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final CurrentProject current,
            final LanguageNames names,
            final Messages messages,
            final Exits exits) {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(exits, "exits");
        log.debug("building the translating screen");
        final ReadOnlyObjectProperty<RunState> state = mirror.runState();
        final TaggedLog logList = new TaggedLog(mirror.activityLog(), messages);
        final ObservableValue<String> sourceName =
                languageName(current, BookBrief::sourceLanguage, MessageKey.LIVE_SOURCE_FALLBACK, names, messages);
        final ObservableValue<String> targetName =
                languageName(current, BookBrief::targetLanguage, MessageKey.LIVE_TARGET_FALLBACK, names, messages);
        final ReviewPanel review = reviewPanel(exits, sourceName, targetName, messages, state);
        return new VBox(
                SCREEN_SPACING,
                head(exits.runs(), viewModel, messages),
                review,
                StateVisibility.shownIn(
                        TranslatingReadyCard.build(viewModel, mirror, current, messages), state, Set.of(RunState.IDLE)),
                StateVisibility.shownIn(TranslatingFigures.progressCard(mirror, messages), state, UNDER_WAY),
                StateVisibility.shownIn(TranslatingFigures.runningTiles(mirror, messages), state, UNDER_WAY),
                StateVisibility.shownIn(TranslatingFigures.outcomeCard(mirror, messages), state, ENDED),
                StateVisibility.shownIn(
                        watch(logList, mirror, sourceName, targetName, messages, exits), state, WATCHED),
                footer(viewModel, mirror, review, messages, exits));
    }

    // The live line of the run-state row (until the live card's own line, task 6.5) and why start or resume waits.
    // The banner is not part of the page: the shell pins it above the scrolling content on every screen (RunBanner).
    private static Node head(final RunStateSource runs, final TranslatingViewModel viewModel, final Messages messages) {
        final Label line = new Label();
        line.setId("translating-live-line");
        line.setWrapText(true);
        line.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> runs.view().get().liveLine().in(messages), runs.view()));
        final VBox head = new VBox(CARD_SPACING, line, heldNote(viewModel, messages));
        head.setId("translating-head");
        return head;
    }

    private static Node watch(
            final TaggedLog logList,
            final StateMirror mirror,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages,
            final Exits exits) {
        final LiveCallPanel live = new LiveCallPanel(
                "translating-live-card", mirror.live().calls(), mirror.runState(), sourceName, targetName, messages);
        live.rememberSectionsIn(exits.sections());
        final Node logCard = TranslatingLogCard.build(logList, messages);
        final VBox row = new VBox(TILE_SPACING, live, logCard);
        row.setId("translating-watch");
        VBox.setVgrow(row, Priority.ALWAYS);
        return row;
    }

    private static ObservableValue<String> languageName(
            final CurrentProject current,
            final Function<BookBrief, @Nullable String> tagOf,
            final MessageKey fallback,
            final LanguageNames names,
            final Messages messages) {
        return Bindings.createStringBinding(
                () -> {
                    final BookBrief brief = current.brief().get();
                    final String tag = brief == null ? null : tagOf.apply(brief);
                    return tag == null ? messages.get(fallback) : names.nameOf(tag, messages.locale());
                },
                current.brief());
    }

    // An open panel is shown only while the run is in a state that offers Review flagged, so it never outlives its
    // button. It opens right under the run controls, where Review flagged is, so pressing it always shows something —
    // with nothing flagged, the empty state that says so.
    private static ReviewPanel reviewPanel(
            final Exits exits,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages,
            final ReadOnlyObjectProperty<RunState> state) {
        final ReviewPanel panel =
                new ReviewPanel(exits.review(), sourceName, targetName, messages, exits.dialogs(), exits.pauses());
        panel.visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> panel.openProperty().get() && REVIEWABLE.contains(state.get()),
                        panel.openProperty(),
                        state));
        panel.managedProperty().bind(panel.visibleProperty());
        return panel;
    }

    // Says why start or resume is off while other model work (a scan, an export, a provider test, a retry) runs.
    private static Label heldNote(final TranslatingViewModel viewModel, final Messages messages) {
        final Label note = new Label();
        note.setId("translating-held");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            final ActivityKind kind = viewModel.otherWork().get();
                            return kind == null ? "" : BlockedWords.in(messages, kind, ActivityKind.TRANSLATION);
                        },
                        viewModel.otherWork()));
        note.visibleProperty().bind(note.textProperty().isNotEmpty());
        note.managedProperty().bind(note.visibleProperty());
        return note;
    }

    // Translating's toolbar: Back, then the run actions of the row; the forward action is Continue to Export.
    private static Node footer(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final ReviewPanel panel,
            final Messages messages,
            final Exits exits) {
        final StepFooter footer = StepFooter.of(
                new StepFooter.Action(
                        "translating-back",
                        messages.get(MessageKey.TRANSLATING_BACK),
                        messages.get(MessageKey.TRANSLATING_BACK_TIP),
                        "btn-ghost",
                        () -> exits.navigator().navigate(ViewNames.NAMES_STYLE)),
                new StepFooter.Action(
                        RunStateView.TO_EXPORT,
                        messages.get(MessageKey.TRANSLATING_CONTINUE),
                        messages.get(MessageKey.TRANSLATING_CONTINUE_TIP),
                        "btn-primary",
                        () -> exits.navigator().navigate(ViewNames.EXPORT)));
        final TranslatingDialogs dialogs = exits.dialogs();
        final Map<String, Runnable> commands = Map.of(
                RunStateView.START,
                () -> dialogs.narrator().startAfterAsking(viewModel::start),
                RunStateView.RESUME,
                viewModel::resume,
                RunStateView.PAUSE,
                viewModel::pause,
                RunStateView.STOP,
                () -> dialogs.askToStop(viewModel, exits.runs().stopFigures()),
                RunStateView.REVIEW,
                () -> openReview(mirror, exits.review(), panel));
        final TranslatingRunActions actions =
                new TranslatingRunActions(exits.runs(), messages, commands, footer.forwardButton());
        return footer.withRunGroup(actions.buttons());
    }

    // The suspicious segments are accepted ones the audit doubts: with nothing flagged, Review opens on them, so the
    // count a person sees on the button is what the panel shows.
    private static void openReview(final StateMirror mirror, final ReviewViewModel review, final ReviewPanel panel) {
        final int flagged = review.flaggedCount().get();
        final int suspicious = mirror.live().suspicious().get();
        log.debug("review pressed with {} flagged and {} suspicious segments: opening the panel", flagged, suspicious);
        review.selectFilter(flagged == 0 && suspicious > 0 ? ReviewFilter.SUSPICIOUS : ReviewFilter.ALL_FLAGGED);
        panel.setOpen(true);
    }
}
