package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.dialog.ChangeResultsDialog;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.dialog.NoTargetDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.NamesStyleViewModel;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The names and style screen's frame, which shows the glossary while a book is open and the no-book state while none
 * is.
 *
 * <p>The open book is observed through a weak listener held by the field below, because the state outlives this
 * controller. Nothing in the frame's own nodes captures the controller, so the host keeps a reference to it in its
 * properties: that is what lets the swap still happen when a book is opened under a screen that is on show, and lets
 * the controller go when the screen does.
 */
@Slf4j
public final class NamesStyleController {

    private final CurrentProject project;
    private final Messages messages;
    private final Navigator navigator;
    private final TranslatingViewModel translating;
    private final NamesStyleViewModel glossary;
    private final ModalHost modalHost;
    private final Toasts toasts;
    private final NoTargetDialog noTargetDialog;
    private final NarratorDialog narratorDialog;
    private final ChangeResultsDialog changeResultsDialog;
    private final ChangeListener<@Nullable OpenedBook> onBook = (observed, was, now) -> show(now);

    @FXML
    private Pane body;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param project the holder of the open book whose glossary is shown
     * @param messages the catalogue the built parts are worded from
     * @param navigator where Back, Start translation and the route from the no-book state lead
     * @param translating what Start translation asks to begin the run
     * @param glossary the state of the glossary table
     * @param modalHost where the Add term card is shown
     * @param toasts where starting with unconfirmed suggestions is noted
     * @param noTargetDialog the question asked before starting with entries that have no target
     * @param narratorDialog the question asked before starting when the book is told in the first person
     * @param changeResultsDialog the card that lists what each model operation changed
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public NamesStyleController(
            final CurrentProject project,
            final Messages messages,
            final Navigator navigator,
            final TranslatingViewModel translating,
            final NamesStyleViewModel glossary,
            final ModalHost modalHost,
            final Toasts toasts,
            final NoTargetDialog noTargetDialog,
            final NarratorDialog narratorDialog,
            final ChangeResultsDialog changeResultsDialog) {
        this.project = Objects.requireNonNull(project, "project");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.glossary = Objects.requireNonNull(glossary, "glossary");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.noTargetDialog = Objects.requireNonNull(noTargetDialog, "noTargetDialog");
        this.narratorDialog = Objects.requireNonNull(narratorDialog, "narratorDialog");
        this.changeResultsDialog = Objects.requireNonNull(changeResultsDialog, "changeResultsDialog");
    }

    @FXML
    void initialize() {
        final OpenedBook book = project.book().get();
        log.debug("building the names and style screen, a book is open: {}", book != null);
        body.getProperties().put(NamesStyleController.class, this);
        project.book().addListener(new WeakChangeListener<>(onBook));
        show(book);
    }

    private void show(final @Nullable OpenedBook book) {
        log.debug("showing the {}", book != null ? "glossary" : "no-book state");
        final Node content = book != null
                ? new NamesStyleView(
                                messages,
                                navigator,
                                translating,
                                glossary,
                                modalHost,
                                toasts,
                                noTargetDialog,
                                narratorDialog,
                                changeResultsDialog)
                        .build(book.projectId())
                : NoBookView.build(messages, navigator);
        body.getChildren().setAll(content);
    }
}
