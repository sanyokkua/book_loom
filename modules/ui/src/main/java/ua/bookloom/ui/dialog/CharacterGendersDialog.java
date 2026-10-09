package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.UnknownGenders;

/**
 * The question asked at Start translation when characters the book names often still have no gender: it lists them and
 * offers Back (set the genders in Names &amp; style first) or Start anyway, which Enter chooses, so a run is never held
 * back by it. Once started anyway the question is not asked again for the book in this session. Back, and Escape,
 * which the host guarantees for every card, return without starting.
 */
@Slf4j
@Singleton
public final class CharacterGendersDialog {

    static final String CARD_ID = "genders-card";
    static final String START_ID = "genders-start";
    static final String BACK_ID = "genders-back";

    private final ModalHost modalHost;
    private final Messages messages;
    private final CurrentProject project;
    private final UnknownGenders unknown;
    // Touched on the FX thread only; holds the books the question was answered with "start anyway".
    private final Set<String> answered = new HashSet<>();

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param project the holder of the open book
     * @param unknown where the characters to ask about are found
     */
    @Inject
    public CharacterGendersDialog(
            final ModalHost modalHost,
            final Messages messages,
            final CurrentProject project,
            final UnknownGenders unknown) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.project = Objects.requireNonNull(project, "project");
        this.unknown = Objects.requireNonNull(unknown, "unknown");
    }

    /**
     * Starts through the question: when characters qualify the card is shown and {@code start} runs after "Start
     * anyway"; otherwise {@code start} runs as soon as the glossary has been read. FX thread only.
     *
     * @param start what starts the translation; not run when the person goes back
     */
    public void startAfterAsking(final Runnable start) {
        Objects.requireNonNull(start, "start");
        final OpenedBook book = project.book().get();
        if (book == null || answered.contains(book.projectId())) {
            log.debug("the character genders are not asked about: starting");
            start.run();
            return;
        }
        final String projectId = book.projectId();
        unknown.find(projectId, found -> {
            if (found.isEmpty()) {
                log.debug("no character needs a gender: starting");
                start.run();
                return;
            }
            log.debug("asking about {} characters with unknown gender for project {}", found.size(), projectId);
            final DialogPane card = card(found);
            wire(card, projectId, start);
            modalHost.show(card, false);
        });
    }

    private DialogPane card(final List<UnknownGender> found) {
        final Label text = new Label(messages.get(MessageKey.DIALOG_GENDERS_TEXT, found.size()));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final VBox body = new VBox(text);
        for (final UnknownGender character : found) {
            final Label row =
                    new Label(messages.get(MessageKey.DIALOG_GENDERS_ROW, character.term(), character.mentions()));
            row.getStyleClass().add("dialog-text");
            body.getChildren().add(row);
        }
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.DIALOG_GENDERS_TITLE));
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(body);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private void wire(final DialogPane card, final String projectId, final Runnable start) {
        final ButtonType backType =
                new ButtonType(messages.get(MessageKey.DIALOG_GENDERS_BACK), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType startType =
                new ButtonType(messages.get(MessageKey.DIALOG_GENDERS_START), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(backType, startType);
        final Button back = button(card, backType, BACK_ID, MessageKey.DIALOG_GENDERS_BACK_TIP, "btn-secondary");
        back.setOnAction(event -> {
            log.debug("going back without starting");
            modalHost.hide();
        });
        button(card, startType, START_ID, MessageKey.DIALOG_GENDERS_START_TIP, "btn-primary")
                .setOnAction(event -> {
                    log.debug("starting with the genders of project {} unknown", projectId);
                    answered.add(projectId);
                    modalHost.hide();
                    start.run();
                });
    }

    private Button button(
            final DialogPane card, final ButtonType type, final String id, final MessageKey tip, final String style) {
        final Button button = (Button) card.lookupButton(type);
        button.setId(id);
        Tips.install(messages, button, tip);
        button.getStyleClass().add(style);
        return button;
    }
}
