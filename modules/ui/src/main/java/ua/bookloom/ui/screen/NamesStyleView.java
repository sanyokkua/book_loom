package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.dialog.AddRecurringTermDialog;
import ua.bookloom.ui.dialog.AddTermDialog;
import ua.bookloom.ui.dialog.ChangeResultsDialog;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.dialog.NoTargetDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.CsvFileChooser;
import ua.bookloom.ui.state.GlossaryNotice;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.RecurringTerms;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The names and style screen's content for an open book: the line reported in place, the Glossary and the Recurring
 * terms card side by side or stacked, each a {@link TermTableCard}, the note that the step can be skipped, and the
 * footer that goes back to the brief or starts the run.
 *
 * <p>The forward action follows the run controls Translating offers: Start translation asks
 * {@link TranslatingViewModel#start()} when a start is offered, Resume the run continues a paused or stopped run, and
 * Back to the run only shows a run under way, so this screen never offers a second translation. Each then shows the
 * translating screen whatever came of it, where a missing input is named on the banner. The view model outlives this
 * view, so what listens to it does so weakly, with the strong reference held by the node that reacts.
 */
@Slf4j
final class NamesStyleView {

    private static final double SCREEN_SPACING = 14;
    private static final double STACK_BELOW = 1040;
    private static final String LISTENER_KEY = "names-style-listener";

    private final Messages messages;
    private final Navigator navigator;
    private final TranslatingViewModel translating;
    private final NamesStyleViewModel glossary;
    private final ModalHost modalHost;
    private final Toasts toasts;
    private final NoTargetDialog noTargetDialog;
    private final NarratorDialog narratorDialog;
    private final ChangeResultsDialog changeResultsDialog;
    private final CsvFileChooser csvFiles;

    NamesStyleView(
            final Messages messages,
            final Navigator navigator,
            final TranslatingViewModel translating,
            final NamesStyleViewModel glossary,
            final ModalHost modalHost,
            final Toasts toasts,
            final NoTargetDialog noTargetDialog,
            final NarratorDialog narratorDialog,
            final ChangeResultsDialog changeResultsDialog,
            final CsvFileChooser csvFiles) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.noTargetDialog = Objects.requireNonNull(noTargetDialog, "noTargetDialog");
        this.narratorDialog = Objects.requireNonNull(narratorDialog, "narratorDialog");
        this.changeResultsDialog = Objects.requireNonNull(changeResultsDialog, "changeResultsDialog");
        this.csvFiles = Objects.requireNonNull(csvFiles, "csvFiles");
    }

    Node build(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("showing the glossary of project {}", projectId);
        glossary.show(projectId);
        final Banner skip = Tips.install(
                messages,
                new Banner("names-style-banner", Banner.Role.INFO, "ℹ", "", messages.get(MessageKey.NAMES_STYLE_SKIP)),
                MessageKey.NAMES_STYLE_SKIP_TIP);
        final TwoColumns cards = new TwoColumns(glossaryCard(), recurringCard(), STACK_BELOW);
        cards.equalHeightsProperty().set(true);
        final VBox screen = new VBox(SCREEN_SPACING, noticeBanner(), cards, skip, footer());
        ResultsOpener.attach(screen, glossary, changeResultsDialog);
        return screen;
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

    private TermTableCard<GlossaryEntry> glossaryCard() {
        return new TermTableCard<>(
                messages,
                glossary,
                csvFiles,
                "names-style",
                glossaryTexts(),
                glossaryToolbar(),
                GlossaryTable.rows(messages, glossary));
    }

    private static TermTableCard.Texts glossaryTexts() {
        return new TermTableCard.Texts(
                "names-style-glossary-title",
                MessageKey.NAMES_STYLE_GLOSSARY_TITLE,
                null,
                new TermTableCard.Phrase(MessageKey.NAMES_STYLE_ADD, MessageKey.NAMES_STYLE_ADD_TIP),
                new TermTableCard.Phrase(MessageKey.NAMES_STYLE_IMPORT, MessageKey.NAMES_STYLE_IMPORT_TIP),
                new TermTableCard.Phrase(MessageKey.NAMES_STYLE_EXPORT, MessageKey.NAMES_STYLE_EXPORT_TIP),
                MessageKey.NAMES_STYLE_EMPTY,
                MessageKey.NAMES_STYLE_EMPTY_FILTERED);
    }

    private TermTableCard.Toolbar glossaryToolbar() {
        final ModelActionBar.Words words = new ModelActionBar.Words(
                MessageKey.NAMES_STYLE_MODEL_SCAN,
                MessageKey.NAMES_STYLE_MODEL_SCAN_TIP,
                MessageKey.NAMES_STYLE_REVIEW,
                MessageKey.NAMES_STYLE_REVIEW_TIP,
                MessageKey.NAMES_STYLE_TRANSLATE,
                MessageKey.NAMES_STYLE_TRANSLATE_TIP);
        return new TermTableCard.Toolbar(
                words,
                new ModelActionBar.Actions(glossary::modelScan, glossary::review, glossary::translate),
                this::openAddTerm,
                glossary::importCsv,
                glossary::exportCsv,
                "glossary.csv",
                acceptAll());
    }

    private TermTableCard<LexiconEntry> recurringCard() {
        return new TermTableCard<>(
                messages,
                glossary,
                csvFiles,
                "recurring",
                recurringTexts(),
                recurringToolbar(),
                RecurringTermsCard.rows(messages, glossary));
    }

    private static TermTableCard.Texts recurringTexts() {
        return new TermTableCard.Texts(
                "recurring-title",
                MessageKey.RECURRING_TITLE,
                MessageKey.RECURRING_NOTE,
                new TermTableCard.Phrase(MessageKey.RECURRING_ADD, MessageKey.RECURRING_ADD_TIP),
                new TermTableCard.Phrase(MessageKey.RECURRING_IMPORT, MessageKey.RECURRING_IMPORT_TIP),
                new TermTableCard.Phrase(MessageKey.RECURRING_EXPORT, MessageKey.RECURRING_EXPORT_TIP),
                MessageKey.RECURRING_EMPTY,
                MessageKey.NAMES_STYLE_EMPTY_FILTERED);
    }

    private TermTableCard.Toolbar recurringToolbar() {
        final RecurringTerms recurring = glossary.recurring();
        final ModelActionBar.Words words = new ModelActionBar.Words(
                MessageKey.RECURRING_MODEL_SCAN,
                MessageKey.RECURRING_MODEL_SCAN_TIP,
                MessageKey.RECURRING_MODEL_REVIEW,
                MessageKey.RECURRING_MODEL_REVIEW_TIP,
                MessageKey.RECURRING_SUGGEST,
                MessageKey.RECURRING_SUGGEST_TIP);
        return new TermTableCard.Toolbar(
                words,
                new ModelActionBar.Actions(recurring::scan, recurring::review, recurring::translate),
                this::openAddRecurring,
                recurring::importCsv,
                recurring::exportCsv,
                "recurring-terms.csv",
                null);
    }

    // Shown only while a row holds a suggestion, and off while a model action could replace the suggestions it would
    // accept.
    private Button acceptAll() {
        final Button acceptAll = Tips.install(
                messages,
                new Button(messages.get(MessageKey.NAMES_STYLE_ACCEPT_ALL)),
                MessageKey.NAMES_STYLE_ACCEPT_ALL_TIP);
        acceptAll.setId("names-style-accept-all");
        acceptAll.getStyleClass().add("btn-ghost");
        acceptAll.setMinWidth(Region.USE_PREF_SIZE);
        acceptAll.setMaxHeight(Double.MAX_VALUE);
        acceptAll.setOnAction(event -> {
            log.debug("names-style-accept-all pressed");
            glossary.acceptAll();
        });
        acceptAll.disableProperty().bind(glossary.busy());
        acceptAll.visibleProperty().bind(glossary.suggestedCount().greaterThan(0));
        acceptAll.managedProperty().bind(acceptAll.visibleProperty());
        return acceptAll;
    }

    private void openAddTerm() {
        modalHost.show(new AddTermDialog(messages, glossary, modalHost::hide).card(), false);
    }

    private void openAddRecurring() {
        modalHost.show(new AddRecurringTermDialog(messages, glossary.recurring(), modalHost::hide).card(), false);
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
                        this::onForward));
        followTheRun(footer.forwardButton());
        return footer;
    }

    // The forward action names what it will do with the run as it stands, so it never offers a second translation.
    private void followTheRun(final Button forward) {
        final ChangeListener<Controls> onControls = (observed, was, now) -> showForward(forward, Forward.of(now));
        forward.getProperties().put(LISTENER_KEY, onControls);
        translating.controls().addListener(new WeakChangeListener<>(onControls));
        showForward(forward, Forward.of(translating.controls().get()));
    }

    private void showForward(final Button forward, final Forward action) {
        log.debug("names and style offers {} as its forward action", action);
        forward.setText(messages.get(action.label()));
        Tips.install(messages, forward, action.tip());
    }

    private void onForward() {
        final Forward action = Forward.of(translating.controls().get());
        log.debug("forward pressed on names and style: {}", action);
        switch (action) {
            case START -> startUnlessTargetsAreMissing();
            case RESUME -> {
                translating.resume();
                navigator.navigate(ViewNames.TRANSLATING);
            }
            case TO_RUN -> {
                log.debug("a run is under way: showing it");
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
        noteUnconfirmedSuggestions();
        // First, because starting registers the preparation as blocking work, and a blocking activity refuses
        // navigation.
        navigator.navigate(ViewNames.TRANSLATING);
        translating.start();
    }

    // A run drafts with a suggested target as a hint, not as the person's choice; starting does not wait for a review.
    private void noteUnconfirmedSuggestions() {
        final int suggested = glossary.suggestedCount().get();
        log.debug("starting with {} suggested targets unconfirmed", suggested);
        if (suggested > 0) {
            toasts.info(MessageKey.NAMES_STYLE_SUGGESTIONS_UNREVIEWED, suggested);
        }
    }

    /** What the forward button does, read from the run controls Translating offers. */
    private enum Forward {
        /** No run can be continued: start one, as Translating's Start would. */
        START(MessageKey.NAMES_STYLE_START, MessageKey.NAMES_STYLE_START_TIP),
        /** A paused or stopped run can be continued: resume it rather than start another. */
        RESUME(MessageKey.NAMES_STYLE_RESUME, MessageKey.NAMES_STYLE_RESUME_TIP),
        /** A run is under way or has nothing left: only show it. */
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

        static Forward of(final Controls controls) {
            if (controls.start().isShown()) {
                return START;
            }
            return controls.resume().isShown() ? RESUME : TO_RUN;
        }
    }
}
