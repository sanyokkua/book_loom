package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.dialog.NoTargetDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.FileChooserPort;
import ua.bookloom.ui.state.GlossaryNotice;
import ua.bookloom.ui.state.HiddenWork;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.RunRow;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The names and style screen's content for an open book: the line reported in place, the Glossary and the Recurring
 * terms card side by side or stacked, each a {@link TermTableCard}, the note that the step can be skipped, and the
 * footer that goes back to the brief or starts the run.
 *
 * <p>The forward action follows the run-state row: Start translation asks {@link TranslatingViewModel#start()} while
 * the project is Ready or Cannot start yet, and Go to Translating only shows the run in every other state, so this
 * screen never offers a second translation. Start then shows the translating screen whatever came of it, where a
 * missing input is named on the banner; while other model work holds a start (the names pass), Start is off and says
 * why. The row source outlives this view, so what listens to it does so weakly, with the strong reference held by the
 * node that reacts.
 */
@Slf4j
final class NamesStyleView {

    private static final double SCREEN_SPACING = 14;
    private static final String LISTENER_KEY = "names-style-listener";

    private final Messages messages;
    private final Navigator navigator;
    private final TranslatingViewModel translating;
    private final RunStateSource runs;
    private final NamesStyleViewModel glossary;
    private final BookBriefViewModel brief;
    private final ModalHost modalHost;
    private final NoTargetDialog noTargetDialog;
    private final NarratorDialog narratorDialog;
    private final HiddenWork hiddenWork;
    private final FileChooserPort csvFiles;

    NamesStyleView(
            final Messages messages,
            final Navigator navigator,
            final TranslatingViewModel translating,
            final RunStateSource runs,
            final NamesStyleViewModel glossary,
            final BookBriefViewModel brief,
            final ModalHost modalHost,
            final NoTargetDialog noTargetDialog,
            final NarratorDialog narratorDialog,
            final HiddenWork hiddenWork,
            final FileChooserPort csvFiles) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.noTargetDialog = Objects.requireNonNull(noTargetDialog, "noTargetDialog");
        this.narratorDialog = Objects.requireNonNull(narratorDialog, "narratorDialog");
        this.hiddenWork = Objects.requireNonNull(hiddenWork, "hiddenWork");
        this.csvFiles = Objects.requireNonNull(csvFiles, "csvFiles");
    }

    Node build(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("showing the glossary of project {}", projectId);
        glossary.show(projectId);
        final BooleanBinding frozen = hiddenWork.freezes(ViewNames.NAMES_STYLE);
        final NamesStyleCards parts = new NamesStyleCards(messages, glossary, csvFiles, modalHost, frozen);
        final TwoColumns cards = new TwoColumns(parts.glossaryCard(), parts.recurringCard(), LayoutClass.Width.WIDE);
        cards.equalHeightsProperty().set(true);
        final VBox screen = new VBox(
                SCREEN_SPACING,
                purpose(),
                runBanner(),
                hiddenBanner(frozen),
                noticeBanner(),
                AutoNamesBar.build(messages, glossary, brief, frozen),
                cards,
                footer());
        new ResultsOpener(glossary, modalHost, messages).attach(screen);
        return screen;
    }

    // What the screen is for, and that it may be skipped, above everything else on it.
    private Label purpose() {
        final Label purpose = new Label(messages.get(MessageKey.NAMES_STYLE_PURPOSE));
        purpose.setId("names-style-purpose");
        purpose.getStyleClass().add("muted");
        purpose.setWrapText(true);
        return purpose;
    }

    // Both tables stay editable while a translation is at work: the run reads the glossary as it is when it gets there.
    private Banner runBanner() {
        return BriefCards.shownWhile(
                new Banner(
                        "names-style-run-banner",
                        Banner.Role.INFO,
                        "ℹ",
                        "",
                        messages.get(MessageKey.NAMES_STYLE_RUN_BANNER)),
                Bindings.createBooleanBinding(() -> runs.view().get().isAtWork(), runs.view()));
    }

    // Work in the background writes to this screen once, at its end, so it may not be edited meanwhile.
    private Banner hiddenBanner(final BooleanBinding frozen) {
        final Banner banner = BriefCards.shownWhile(
                new Banner(
                        "names-style-hidden-banner",
                        Banner.Role.INFO,
                        "ℹ",
                        messages.get(MessageKey.NAMES_STYLE_HIDDEN_BANNER),
                        messages.get(MessageKey.NAMES_STYLE_HIDDEN_NOTE)),
                frozen);
        final Button show = banner.addTrailingAction(
                "names-style-hidden-show", messages.get(MessageKey.WORK_HIDDEN_SHOW), hiddenWork::showProgress);
        Tips.install(messages, show, MessageKey.WORK_HIDDEN_SHOW_TIP);
        return banner;
    }

    private Node noticeBanner() {
        final Banner banner = new Banner("names-style-notice", Banner.Role.ERR, "⚠", "", "");
        final ChangeListener<@Nullable GlossaryNotice> onNotice = (observed, was, now) -> showNotice(banner, now);
        banner.getProperties().put(LISTENER_KEY, onNotice);
        glossary.notice().addListener(new WeakChangeListener<>(onNotice));
        showNotice(banner, glossary.notice().get());
        return banner;
    }

    private static void showNotice(final Banner banner, final @Nullable GlossaryNotice notice) {
        banner.setVisible(notice != null);
        banner.setManaged(notice != null);
        if (notice != null) {
            final boolean failed = notice.level() == GlossaryNotice.Level.ERROR;
            banner.setRole(failed ? Banner.Role.ERR : Banner.Role.INFO);
            banner.setGlyph(failed ? "⚠" : "ℹ");
            banner.setText(notice.text());
        }
    }

    private Node footer() {
        final StepFooter footer = StepFooter.of(
                        new StepFooter.Action(
                                "names-style-back",
                                messages.get(MessageKey.NAMES_STYLE_BACK),
                                messages.get(MessageKey.NAMES_STYLE_BACK_TIP),
                                "btn-ghost",
                                () -> navigator.navigate(ViewNames.BOOK_BRIEF)),
                        new StepFooter.Action(
                                "names-style-start",
                                messages.get(MessageKey.NAMES_STYLE_START),
                                messages.get(MessageKey.NAMES_STYLE_START_TIP),
                                "btn-primary",
                                this::onForward))
                .withCompactBack(messages.get(MessageKey.COMMON_BACK_COMPACT));
        followTheRun(footer);
        return footer;
    }

    // The forward action names what it will do with the run as it stands, so it never offers a second translation.
    private void followTheRun(final StepFooter footer) {
        final ChangeListener<RunStateView> onRow = (observed, was, now) -> showForward(footer, now);
        footer.forwardButton().getProperties().put(LISTENER_KEY, onRow);
        runs.view().addListener(new WeakChangeListener<>(onRow));
        showForward(footer, runs.view().get());
    }

    // A start held by other work (the names pass) is off and says why; a disabled button shows no tooltip, so the
    // reason is also a visible line beside it. A missing model is not a hold: Start then shows Cannot start yet.
    private void showForward(final StepFooter footer, final RunStateView row) {
        final Forward action = Forward.of(row.row());
        final Button forward = footer.forwardButton();
        final RunStateView.@Nullable RunAction start = row.toolbar().stream()
                .filter(offered -> RunStateView.START.equals(offered.id()))
                .findFirst()
                .orElse(null);
        final @Nullable String heldBecause = action == Forward.START
                        && start != null
                        && !start.enabled()
                        && translating.otherWork().get() != null
                ? start.tip().in(messages)
                : null;
        final boolean held = heldBecause != null;
        final String tip = heldBecause == null ? messages.get(action.tip()) : heldBecause;
        if (!tip.equals(Tips.explanationOf(forward).orElse("")) || forward.isDisable() != held) {
            log.debug("names and style offers {} as its forward action, held {}", action, held);
            Tips.install(forward, tip);
        }
        forward.setText(messages.get(action.label()));
        forward.setDisable(held);
        footer.explainDisabledForward(held ? tip : null);
    }

    private void onForward() {
        final Forward action = Forward.of(runs.view().get().row());
        log.debug("forward pressed on names and style: {}", action);
        switch (action) {
            case START -> startUnlessTargetsAreMissing();
            case TO_RUN -> {
                log.debug("a run can be continued or is under way: showing it");
                navigator.navigate(ViewNames.TRANSLATING);
            }
        }
    }

    // An entry with no target leaves its spelling to the model, which may spell it differently each time; ask first.
    private void startUnlessTargetsAreMissing() {
        final List<String> missing = glossary.rows().stream()
                .filter(GlossaryFlags::hasNoTarget)
                .map(GlossaryEntry::term)
                .toList();
        log.debug("start pressed with {} entries that have no target", missing.size());
        if (missing.isEmpty()) {
            begin();
        } else {
            noTargetDialog.ask(missing, this::begin);
        }
    }

    private void begin() {
        narratorDialog.startAfterAsking(this::startRun);
    }

    private void startRun() {
        // First, because starting registers the preparation as blocking work, and a blocking activity refuses
        // navigation.
        navigator.navigate(ViewNames.TRANSLATING);
        translating.start();
    }

    /** What the forward button does, read from the run-state row. */
    private enum Forward {
        /** Ready or Cannot start yet: start a run, as Translating's Start would. */
        START(MessageKey.NAMES_STYLE_START, MessageKey.NAMES_STYLE_START_TIP),
        /** Any other row: a run can be continued, is under way or has ended; only show it. */
        TO_RUN(MessageKey.NAMES_STYLE_TO_RUN, MessageKey.NAMES_STYLE_TO_RUN_TIP);

        private final MessageKey label;
        private final MessageKey tip;

        Forward(final MessageKey label, final MessageKey tip) {
            this.label = label;
            this.tip = tip;
        }

        MessageKey label() {
            return label;
        }

        MessageKey tip() {
            return tip;
        }

        static Forward of(final RunRow row) {
            return row == RunRow.READY || row == RunRow.CANNOT_START ? START : TO_RUN;
        }
    }
}
