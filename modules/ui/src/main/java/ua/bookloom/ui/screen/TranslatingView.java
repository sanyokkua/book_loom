package ua.bookloom.ui.screen;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.LiveCallPanel;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.BlockedWords;
import ua.bookloom.ui.state.BriefEstimate;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.SceneSummary;
import ua.bookloom.ui.state.SectionMemory;
import ua.bookloom.ui.state.SleepHelp;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TechnicalDetails;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * Builds the Translating screen: why a start waits and the sleep help line, the summary strip in every state, the ready
 * card before a run and the full-height live card from its start on, and the footer whose run actions the toolbar
 * shows.
 *
 * <p>Everything that follows a property is bound to it, and the bindings hold their sources weakly, so a screen that
 * is replaced on the next visit is not kept alive by the mirror it observed. Which card shows under the strip is
 * decided by the run state: the ready card while idle, the live card otherwise. The run actions and the banner follow
 * the run-state row ({@link RunStateSource}) instead, so they read as the title bar does.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TranslatingView {

    private static final double SCREEN_SPACING = 14;
    private static final double CARD_SPACING = 8;

    private static final Set<RunState> RUNNING = Set.of(RunState.RUNNING, RunState.PAUSING);
    private static final Set<RunState> WATCHED = EnumSet.complementOf(EnumSet.of(RunState.IDLE));

    /**
     * What the screen's own buttons do that the view model does not: leave for another step, count the segments to
     * review, open the review desk, and read the run-state row; and the session's memory of which sections the person left open, which outlives the rebuilt screen.
     */
    record Exits(
            Navigator navigator,
            ReviewViewModel review,
            TranslatingDialogs dialogs,
            ReviewDeskLauncher desk,
            SectionMemory sections,
            RunStateSource runs) {}

    /**
     * What the strip and the live card read besides the mirror: the estimate before a start, the scene summary, the
     * sleep help line and whether technical details are on.
     */
    record Reads(BriefEstimate estimate, SceneSummary summary, SleepHelp sleep, TechnicalDetails technical) {}

    static Node build(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final CurrentProject current,
            final LanguageNames names,
            final Messages messages,
            final Exits exits,
            final Reads reads) {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(exits, "exits");
        Objects.requireNonNull(reads, "reads");
        log.debug("building the translating screen");
        return layout(viewModel, mirror, current, names, messages, exits, reads);
    }

    private static Node layout(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final CurrentProject current,
            final LanguageNames names,
            final Messages messages,
            final Exits exits,
            final Reads reads) {
        final ObservableValue<String> sourceName =
                languageName(current, BookBrief::sourceLanguage, MessageKey.LIVE_SOURCE_FALLBACK, names, messages);
        final ObservableValue<String> targetName =
                languageName(current, BookBrief::targetLanguage, MessageKey.LIVE_TARGET_FALLBACK, names, messages);
        final VBox screen = new VBox(SCREEN_SPACING, head(viewModel, mirror, reads.sleep(), messages));
        screen.getChildren()
                .addAll(
                        TranslatingSummaryStrip.build(mirror, stripInputs(mirror, exits, reads), messages),
                        StateVisibility.shownIn(
                                TranslatingReadyCard.build(
                                        viewModel, mirror, current, exits.runs().view(), messages),
                                mirror.runState(),
                                Set.of(RunState.IDLE)),
                        StateVisibility.shownIn(
                                liveCard(mirror, List.of(sourceName, targetName), messages, exits, reads),
                                mirror.runState(),
                                WATCHED),
                        footer(viewModel, mirror, messages, exits));
        return screen;
    }

    private static TranslatingSummaryStrip.Inputs stripInputs(
            final StateMirror mirror, final Exits exits, final Reads reads) {
        return new TranslatingSummaryStrip.Inputs(
                exits.runs().view(),
                exits.runs().decidedSegments(),
                exits.runs().totalSegments(),
                reads.estimate().estimate(),
                // The desk's count, or the run's own flagged rows while the desk has not counted them yet.
                Bindings.createIntegerBinding(
                        () -> Math.max(
                                exits.review().flaggedCount().get(),
                                mirror.live().flaggedQueue().size()),
                        exits.review().flaggedCount(),
                        mirror.live().flaggedQueue()),
                () -> exits.desk().open(ReviewFilter.ALL_FLAGGED));
    }

    // Why start or resume waits, and the help line about sleep while a translation runs. The banner is not part of the
    // page: the shell pins it above the scrolling content on every screen (RunBanner).
    private static Node head(
            final TranslatingViewModel viewModel,
            final StateMirror mirror,
            final SleepHelp sleep,
            final Messages messages) {
        final VBox head = new VBox(CARD_SPACING, heldNote(viewModel, messages), sleepLine(mirror, sleep, messages));
        head.setId("translating-head");
        return head;
    }

    private static Node sleepLine(final StateMirror mirror, final SleepHelp sleep, final Messages messages) {
        final HBox line = new HBox(CARD_SPACING, sleepText(sleep, messages), closeButton(sleep, messages));
        line.setId("translating-help-sleep");
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().addAll("banner", "banner-info");
        line.visibleProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> sleep.shown().get()
                                && RUNNING.contains(mirror.runState().get()),
                        sleep.shown(),
                        mirror.runState()));
        line.managedProperty().bind(line.visibleProperty());
        return line;
    }

    private static Label sleepText(final SleepHelp sleep, final Messages messages) {
        final Label text = new Label();
        text.setGraphic(new FontIcon(Feather.INFO));
        text.setWrapText(true);
        text.getStyleClass().add("banner-text");
        text.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> messages.get(
                                sleep.keepAwake().get()
                                        ? MessageKey.TRANSLATING_HELP_SLEEP
                                        : MessageKey.TRANSLATING_HELP_SLEEP_OFF),
                        sleep.keepAwake()));
        HBox.setHgrow(text, Priority.ALWAYS);
        return text;
    }

    private static Button closeButton(final SleepHelp sleep, final Messages messages) {
        final Button close = Tips.install(messages, new Button(), MessageKey.TRANSLATING_HELP_CLOSE_TIP);
        close.setGraphic(new FontIcon(Feather.X));
        close.setAccessibleText(messages.get(MessageKey.TRANSLATING_HELP_CLOSE));
        close.setId("translating-help-sleep-close");
        close.getStyleClass().add("btn-ghost");
        close.setOnAction(event -> {
            log.debug("sleep help line: close pressed");
            sleep.close();
        });
        return close;
    }

    private static Node liveCard(
            final StateMirror mirror,
            final List<ObservableValue<String>> columnNames,
            final Messages messages,
            final Exits exits,
            final Reads reads) {
        final LiveCallPanel.Sources sources = new LiveCallPanel.Sources(
                mirror.live().calls(),
                exits.runs().view(),
                mirror.live().waitingCall(),
                mirror.activityLog(),
                reads.technical().property(),
                reads.summary().text());
        return new LiveCallPanel(sources, columnNames, exits.sections(), exits.dialogs()::showCalls, messages);
    }

    /** The name of one of the open book's languages, in the display language, or the fallback while none is set. */
    static ObservableValue<String> languageName(
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
                                () -> exits.navigator().navigate(ViewNames.EXPORT)))
                .withCompactBack(messages.get(MessageKey.COMMON_BACK_COMPACT));
        final TranslatingRunActions actions = new TranslatingRunActions(
                exits.runs(), messages, runCommands(viewModel, mirror, exits), footer.forwardButton());
        return footer.withRunGroup(actions.buttons());
    }

    private static Map<String, Runnable> runCommands(
            final TranslatingViewModel viewModel, final StateMirror mirror, final Exits exits) {
        final TranslatingDialogs dialogs = exits.dialogs();
        return Map.of(
                RunStateView.START,
                () -> dialogs.narrator().startAfterAsking(viewModel::start),
                RunStateView.RESUME,
                viewModel::resume,
                RunStateView.PAUSE,
                viewModel::pause,
                RunStateView.STOP,
                () -> dialogs.askToStop(viewModel, exits.runs().stopFigures()),
                RunStateView.REVIEW,
                () -> openReview(mirror, exits));
    }

    // The suspicious segments are accepted ones the audit doubts: with nothing flagged, Review opens on them, so the
    // count a person sees on the button is what the desk shows.
    private static void openReview(final StateMirror mirror, final Exits exits) {
        final int flagged = exits.review().flaggedCount().get();
        final int suspicious = mirror.live().suspicious().get();
        log.debug("review pressed with {} flagged and {} suspicious segments: opening the desk", flagged, suspicious);
        exits.desk().open(flagged == 0 && suspicious > 0 ? ReviewFilter.SUSPICIOUS : ReviewFilter.ALL_FLAGGED);
    }
}
