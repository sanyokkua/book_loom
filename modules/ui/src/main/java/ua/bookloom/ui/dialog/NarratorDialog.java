package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.scene.control.ButtonBar;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NarratorQuestion;

/**
 * The questions asked at Start translation: first the narrator, when {@link NarratorQuestion} says it must be asked
 * (male, female, or not stated, start anyway), then the characters without a gender ({@link CharacterGendersDialog}).
 * Back, and Escape, which the host guarantees for every card, return without starting and without remembering
 * anything. Enter chooses "not stated", never a gender.
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
    private final CharacterGendersDialog genders;
    private final NarratorQuestion question;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param genders the question about characters without a gender, asked after the narrator's
     * @param question what decides whether to ask and what an answer does
     */
    @Inject
    public NarratorDialog(
            final ModalHost modalHost,
            final Messages messages,
            final CharacterGendersDialog genders,
            final NarratorQuestion question) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.genders = Objects.requireNonNull(genders, "genders");
        this.question = Objects.requireNonNull(question, "question");
    }

    /**
     * Starts through the question: when it must be asked the card is shown and {@code start} runs after an answer;
     * otherwise {@code start} runs at once. The narrator's stored quotes are re-checked on the background pool first,
     * so the card, or {@code start}, follows a moment later on the FX thread. FX thread only.
     *
     * @param start what starts the translation; not run when the person goes back
     */
    public void startAfterAsking(final Runnable start) {
        Objects.requireNonNull(start, "start");
        final Runnable next = () -> genders.startAfterAsking(start);
        question.startOrAsk(next, projectId -> show(projectId, next));
    }

    private void show(final String projectId, final Runnable start) {
        final FormCard card = new FormCard(
                modalHost, messages, CARD_ID, ModalCard.Size.WIDE, messages.get(MessageKey.DIALOG_NARRATOR_TITLE));
        card.text(messages.get(MessageKey.DIALOG_NARRATOR_TEXT));
        wire(card, projectId, start);
        card.show();
    }

    private void wire(final FormCard card, final String projectId, final Runnable start) {
        card.button(
                BACK_ID,
                messages.get(MessageKey.DIALOG_NARRATOR_BACK),
                MessageKey.DIALOG_NARRATOR_BACK_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                () -> log.debug("going back without starting"));
        card.button(
                UNKNOWN_ID,
                messages.get(MessageKey.DIALOG_NARRATOR_UNKNOWN),
                MessageKey.DIALOG_NARRATOR_UNKNOWN_TIP,
                ButtonBar.ButtonData.OK_DONE,
                "btn-primary",
                () -> question.answer(projectId, Gender.UNKNOWN, start));
        card.button(
                FEMALE_ID,
                messages.get(MessageKey.DIALOG_NARRATOR_FEMALE),
                MessageKey.DIALOG_NARRATOR_FEMALE_TIP,
                ButtonBar.ButtonData.OTHER,
                "btn-secondary",
                () -> question.answer(projectId, Gender.FEMALE, start));
        card.button(
                MALE_ID,
                messages.get(MessageKey.DIALOG_NARRATOR_MALE),
                MessageKey.DIALOG_NARRATOR_MALE_TIP,
                ButtonBar.ButtonData.OTHER,
                "btn-secondary",
                () -> question.answer(projectId, Gender.MALE, start));
    }
}
