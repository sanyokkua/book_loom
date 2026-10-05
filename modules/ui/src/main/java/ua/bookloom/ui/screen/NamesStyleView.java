package ua.bookloom.ui.screen;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.AddTermDialog;
import ua.bookloom.ui.dialog.NoTargetDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.GlossaryNotice;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The names and style screen's content for an open book: the line reported in place, the glossary card with the
 * table and the four actions of its header, the note that the step can be skipped, and the footer that goes back to
 * the structure or starts the run.
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
    private static final double CARD_SPACING = 10;
    private static final double ACTION_SPACING = 8;
    private static final double SEARCH_WIDTH = 220;
    private static final String LISTENER_KEY = "names-style-listener";

    private final Messages messages;
    private final Navigator navigator;
    private final TranslatingViewModel translating;
    private final NamesStyleViewModel glossary;
    private final ModalHost modalHost;
    private final Toasts toasts;
    private final NoTargetDialog noTargetDialog;

    NamesStyleView(
            final Messages messages,
            final Navigator navigator,
            final TranslatingViewModel translating,
            final NamesStyleViewModel glossary,
            final ModalHost modalHost,
            final Toasts toasts,
            final NoTargetDialog noTargetDialog) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.noTargetDialog = Objects.requireNonNull(noTargetDialog, "noTargetDialog");
    }

    Node build(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("showing the glossary of project {}", projectId);
        glossary.show(projectId);
        final Banner skip = Tips.install(
                messages,
                new Banner("names-style-banner", Banner.Role.INFO, "ℹ", "", messages.get(MessageKey.NAMES_STYLE_SKIP)),
                MessageKey.NAMES_STYLE_SKIP_TIP);
        return new VBox(
                SCREEN_SPACING, noticeBanner(), card(), RecurringTermsCard.build(messages, glossary), skip, footer());
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

    private Node card() {
        final TextField search = GlossaryTable.search(messages);
        final TableView<GlossaryEntry> table = GlossaryTable.build(messages, glossary, search.textProperty());
        final ChangeListener<Number> onRestore = (observed, was, now) -> table.refresh();
        table.getProperties().put(LISTENER_KEY, onRestore);
        glossary.restorations().addListener(new WeakChangeListener<>(onRestore));
        final VBox header = new VBox(CARD_SPACING, title(), toolbar(search));
        final VBox card = new VBox(CARD_SPACING, header, blockedNote(), table);
        card.setId("names-style-card");
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    // The title sits above the toolbar, which wraps onto a further line rather than cutting a caption short.
    private Label title() {
        final Label title = new Label(messages.get(MessageKey.NAMES_STYLE_GLOSSARY_TITLE));
        title.setId("names-style-glossary-title");
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMinHeight(Region.USE_PREF_SIZE);
        return title;
    }

    private FlowPane toolbar(final TextField search) {
        search.setPrefWidth(SEARCH_WIDTH);
        search.setMinWidth(Region.USE_PREF_SIZE);
        final FlowPane toolbar = new FlowPane(ACTION_SPACING, ACTION_SPACING, search);
        toolbar.setId("names-style-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren()
                .add(action(
                        "names-style-add",
                        MessageKey.NAMES_STYLE_ADD,
                        MessageKey.NAMES_STYLE_ADD_TIP,
                        this::openAddTerm));
        toolbar.getChildren().addAll(modelActions());
        toolbar.getChildren()
                .addAll(
                        action(
                                "names-style-import",
                                MessageKey.NAMES_STYLE_IMPORT,
                                MessageKey.NAMES_STYLE_IMPORT_TIP,
                                this::chooseImport),
                        action(
                                "names-style-export",
                                MessageKey.NAMES_STYLE_EXPORT,
                                MessageKey.NAMES_STYLE_EXPORT_TIP,
                                this::chooseExport));
        return toolbar;
    }

    // Says why the model actions are off while other model work (a run, an export, a provider test) is under way.
    private Label blockedNote() {
        final Label note = new Label();
        note.setId("names-style-model-blocked");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.textProperty().bind(glossary.modelBlockedReason());
        note.visibleProperty().bind(glossary.modelBlockedReason().isNotEmpty());
        note.managedProperty().bind(note.visibleProperty());
        return note;
    }

    // The two model actions are not offered while one runs or other model work does; Stop is shown only while one runs.
    // Accept all is shown only while a row holds a suggestion — the toolbar must still fit one line at 1000 px — and is
    // off while a model action could replace the suggestions it would accept.
    private List<Button> modelActions() {
        final Button scan = action(
                "names-style-model-scan",
                MessageKey.NAMES_STYLE_MODEL_SCAN,
                MessageKey.NAMES_STYLE_MODEL_SCAN_TIP,
                glossary::modelScan);
        final Button review = action(
                "names-style-review",
                MessageKey.NAMES_STYLE_REVIEW,
                MessageKey.NAMES_STYLE_REVIEW_TIP,
                glossary::review);
        final Button stop = action(
                "names-style-model-stop",
                MessageKey.NAMES_STYLE_MODEL_STOP,
                MessageKey.NAMES_STYLE_MODEL_STOP_TIP,
                glossary::stopModel);
        scan.disableProperty()
                .bind(glossary.busy().or(glossary.modelBlockedReason().isNotEmpty()));
        review.disableProperty()
                .bind(glossary.busy().or(glossary.modelBlockedReason().isNotEmpty()));
        stop.visibleProperty().bind(glossary.busy());
        stop.managedProperty().bind(glossary.busy());
        return List.of(scan, review, acceptAll(), stop);
    }

    private Button acceptAll() {
        final Button acceptAll = action(
                "names-style-accept-all",
                MessageKey.NAMES_STYLE_ACCEPT_ALL,
                MessageKey.NAMES_STYLE_ACCEPT_ALL_TIP,
                glossary::acceptAll);
        acceptAll.disableProperty().bind(glossary.busy());
        acceptAll.visibleProperty().bind(glossary.suggestedCount().greaterThan(0));
        acceptAll.managedProperty().bind(acceptAll.visibleProperty());
        return acceptAll;
    }

    private Button action(final String id, final MessageKey caption, final MessageKey tip, final Runnable onPress) {
        final Button button = Tips.install(messages, new Button(messages.get(caption)), tip);
        button.setId(id);
        button.getStyleClass().add("btn-ghost");
        // A caption is never cut to an ellipsis: the toolbar wraps the button onto the next line instead.
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(event -> {
            log.debug("{} pressed", id);
            onPress.run();
        });
        return button;
    }

    private void openAddTerm() {
        modalHost.show(new AddTermDialog(messages, glossary, modalHost::hide).card(), false);
    }

    private void chooseImport() {
        final File chosen = chooser().showOpenDialog(null);
        if (chosen != null) {
            glossary.importCsv(chosen.toPath());
        }
    }

    private void chooseExport() {
        final FileChooser chooser = chooser();
        chooser.setInitialFileName("glossary.csv");
        final File chosen = chooser.showSaveDialog(null);
        if (chosen != null) {
            final Path destination = chosen.toPath();
            glossary.exportCsv(destination);
        }
    }

    private static FileChooser chooser() {
        final FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        return chooser;
    }

    private Node footer() {
        final StepFooter footer = StepFooter.of(
                new StepFooter.Action(
                        "names-style-back",
                        messages.get(MessageKey.NAMES_STYLE_BACK),
                        messages.get(MessageKey.NAMES_STYLE_BACK_TIP),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.STRUCTURE)),
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
        noteUnconfirmedSuggestions();
        translating.start();
        navigator.navigate(ViewNames.TRANSLATING);
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
