package ua.bookloom.ui.screen;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
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
import ua.bookloom.ui.dialog.AddTermDialog;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.GlossaryNotice;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The names and style screen's content for an open book: the line reported in place, the glossary card with the
 * table and the four actions of its header, the note that the step can be skipped, and the footer that goes back to
 * the structure or starts the run.
 *
 * <p>Start translation asks {@link TranslatingViewModel#start()} and then shows the translating screen whatever came
 * of it: the view model begins a run only when the controls offer a start, so with a run already under way the screen
 * just shows that run, and a missing input is named on the translating screen's banner. The view model outlives this
 * view, so what listens to it does so weakly, with the strong reference held by the node that reacts.
 */
@Slf4j
final class NamesStyleView {

    private static final double SCREEN_SPACING = 14;
    private static final double CARD_SPACING = 10;
    private static final double ACTION_SPACING = 8;
    private static final String LISTENER_KEY = "names-style-listener";

    private final Messages messages;
    private final Navigator navigator;
    private final TranslatingViewModel translating;
    private final NamesStyleViewModel glossary;
    private final ModalHost modalHost;

    NamesStyleView(
            final Messages messages,
            final Navigator navigator,
            final TranslatingViewModel translating,
            final NamesStyleViewModel glossary,
            final ModalHost modalHost) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
    }

    Node build(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("showing the glossary of project {}", projectId);
        glossary.show(projectId);
        final Banner skip =
                new Banner("names-style-banner", Banner.Role.INFO, "ℹ", "", messages.get(MessageKey.NAMES_STYLE_SKIP));
        return new VBox(SCREEN_SPACING, noticeBanner(), card(), skip, footer());
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
        final TableView<GlossaryEntry> table = GlossaryTable.build(messages, glossary);
        final ChangeListener<Number> onRestore = (observed, was, now) -> table.refresh();
        table.getProperties().put(LISTENER_KEY, onRestore);
        glossary.restorations().addListener(new WeakChangeListener<>(onRestore));
        final Label title = new Label(messages.get(MessageKey.NAMES_STYLE_GLOSSARY_TITLE));
        title.getStyleClass().add("card-title");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = new HBox(ACTION_SPACING, title, spacer, actions());
        final VBox card = new VBox(CARD_SPACING, header, table);
        card.setId("names-style-card");
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private Node actions() {
        final Button scan = action("names-style-model-scan", MessageKey.NAMES_STYLE_MODEL_SCAN, glossary::modelScan);
        scan.disableProperty().bind(glossary.busy());
        return new HBox(
                ACTION_SPACING,
                action("names-style-add", MessageKey.NAMES_STYLE_ADD, this::openAddTerm),
                scan,
                action("names-style-import", MessageKey.NAMES_STYLE_IMPORT, this::chooseImport),
                action("names-style-export", MessageKey.NAMES_STYLE_EXPORT, this::chooseExport));
    }

    private Button action(final String id, final MessageKey caption, final Runnable onPress) {
        final Button button = new Button(messages.get(caption));
        button.setId(id);
        button.getStyleClass().add("btn-ghost");
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
        return StepFooter.of(
                new StepFooter.Action(
                        "names-style-back",
                        messages.get(MessageKey.NAMES_STYLE_BACK),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.STRUCTURE)),
                new StepFooter.Action(
                        "names-style-start", messages.get(MessageKey.NAMES_STYLE_START), "btn-primary", this::onStart));
    }

    private void onStart() {
        log.debug(
                "start translation pressed, a start is offered: {}",
                translating.controls().get().start().isEnabled());
        translating.start();
        navigator.navigate(ViewNames.TRANSLATING);
    }
}
