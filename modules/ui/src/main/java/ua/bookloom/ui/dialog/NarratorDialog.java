package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.NarratorHints;
import ua.bookloom.ui.state.OpenedBook;

/**
 * The questions asked at Start translation: first the narrator, then the characters without a gender
 * ({@link CharacterGendersDialog}). The narrator question is asked once when the source was found to be told in the
 * first person and the brief does not say the narrator's gender: male, female, or not stated (start anyway). The
 * choice is written to the brief,
 * and any answer is remembered for the book, so the question is never asked twice in a session. Back, and Escape,
 * which the host guarantees for every card, return without starting and without remembering anything. Enter chooses
 * "not stated", never a gender, and the run starts only after the chosen gender has been stored in the project.
 */
@Slf4j
@Singleton
public final class NarratorDialog {

    static final String CARD_ID = "narrator-card";
    static final String MALE_ID = "narrator-male";
    static final String FEMALE_ID = "narrator-female";
    static final String UNKNOWN_ID = "narrator-unknown";
    static final String BACK_ID = "narrator-back";

    private final ModalHost modalHost;
    private final Messages messages;
    private final CurrentProject project;
    private final BookBriefViewModel brief;
    private final CharacterGendersDialog genders;
    // Touched on the FX thread only, like every dialog; holds the books the question has been answered for.
    private final Set<String> answered = new HashSet<>();

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param project the holder of the open book, whose narrator hint and brief decide whether to ask
     * @param brief the view model the chosen narrator is written through
     * @param genders the question about characters without a gender, asked after the narrator's
     */
    @Inject
    public NarratorDialog(
            final ModalHost modalHost,
            final Messages messages,
            final CurrentProject project,
            final BookBriefViewModel brief,
            final CharacterGendersDialog genders) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.project = Objects.requireNonNull(project, "project");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.genders = Objects.requireNonNull(genders, "genders");
    }

    /**
     * Starts through the question: when it must be asked the card is shown and {@code start} runs after an answer;
     * otherwise {@code start} runs at once. FX thread only.
     *
     * @param start what starts the translation; not run when the person goes back
     */
    public void startAfterAsking(final Runnable start) {
        Objects.requireNonNull(start, "start");
        askNarrator(() -> genders.startAfterAsking(start));
    }

    private void askNarrator(final Runnable start) {
        final OpenedBook book = project.book().get();
        if (book == null || !mustAsk(book, project.brief().get())) {
            log.debug("the narrator is not asked about: starting");
            start.run();
            return;
        }
        log.debug("asking who narrates before starting project {}", book.projectId());
        final Label text = new Label(messages.get(MessageKey.DIALOG_NARRATOR_TEXT));
        text.setWrapText(true);
        text.getStyleClass().add("dialog-text");
        final DialogPane card = card(new VBox(text));
        wire(card, book.projectId(), start);
        modalHost.show(card, false);
    }

    private boolean mustAsk(final OpenedBook book, final @Nullable BookBrief current) {
        return NarratorHints.needsGender(book, current) && !answered.contains(book.projectId());
    }

    private DialogPane card(final VBox body) {
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.DIALOG_NARRATOR_TITLE));
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
                new ButtonType(messages.get(MessageKey.DIALOG_NARRATOR_BACK), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType unknownType =
                new ButtonType(messages.get(MessageKey.DIALOG_NARRATOR_UNKNOWN), ButtonBar.ButtonData.OK_DONE);
        final ButtonType femaleType =
                new ButtonType(messages.get(MessageKey.DIALOG_NARRATOR_FEMALE), ButtonBar.ButtonData.OTHER);
        final ButtonType maleType =
                new ButtonType(messages.get(MessageKey.DIALOG_NARRATOR_MALE), ButtonBar.ButtonData.OTHER);
        card.getButtonTypes().setAll(backType, unknownType, femaleType, maleType);
        final Button back = button(card, backType, BACK_ID, MessageKey.DIALOG_NARRATOR_BACK_TIP, "btn-secondary");
        back.setOnAction(event -> {
            log.debug("going back without starting");
            modalHost.hide();
        });
        button(card, unknownType, UNKNOWN_ID, MessageKey.DIALOG_NARRATOR_UNKNOWN_TIP, "btn-primary")
                .setOnAction(event -> answer(projectId, Gender.UNKNOWN, start));
        button(card, femaleType, FEMALE_ID, MessageKey.DIALOG_NARRATOR_FEMALE_TIP, "btn-secondary")
                .setOnAction(event -> answer(projectId, Gender.FEMALE, start));
        button(card, maleType, MALE_ID, MessageKey.DIALOG_NARRATOR_MALE_TIP, "btn-secondary")
                .setOnAction(event -> answer(projectId, Gender.MALE, start));
    }

    private Button button(
            final DialogPane card, final ButtonType type, final String id, final MessageKey tip, final String style) {
        final Button button = (Button) card.lookupButton(type);
        button.setId(id);
        Tips.install(messages, button, tip);
        button.getStyleClass().add(style);
        return button;
    }

    private void answer(final String projectId, final Gender gender, final Runnable start) {
        log.debug("the narrator answered {} for project {}", gender, projectId);
        answered.add(projectId);
        if (gender != Gender.UNKNOWN) {
            brief.setFirstPersonNarrator(gender);
        }
        modalHost.hide();
        startWhenSaved(start);
    }

    // The run reads the stored brief, and the save is asynchronous: starting before it lands would draft with the old
    // narrator while the window shows the new one.
    private void startWhenSaved(final Runnable start) {
        final ReadOnlyBooleanProperty saving = brief.saving();
        if (!saving.get()) {
            start.run();
            return;
        }
        log.debug("the narrator is being saved; the run starts when the brief is stored");
        saving.addListener(new ChangeListener<>() {
            @Override
            public void changed(
                    final ObservableValue<? extends Boolean> property, final Boolean was, final Boolean now) {
                if (!now) {
                    property.removeListener(this);
                    start.run();
                }
            }
        });
    }
}
