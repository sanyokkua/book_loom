package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.MeaningQuestions;
import ua.bookloom.ui.state.OpenedBook;

/**
 * The once-per-name question about a name whose meaning the book explains or plays on (book-brief "Ask about a
 * meaning-bearing name in Prepare, once, and never during a run"; design D5): each name with its meaning and three
 * choices — sound it out, translate it, or sound it out and explain it at its first use — sound it out chosen. Save
 * stores the choices and goes on to start; Back, and Escape, which the host guarantees for every card, store the
 * default for every name and return without starting, so the question is never asked again. Asked at Start
 * translation, before anything else, and only for names the names pass marked; a run never marks one.
 */
@Slf4j
@Singleton
public final class MeaningNameDialog {

    static final String CARD_ID = "meaning-card";
    static final String CONFIRM_ID = "meaning-confirm";
    static final String BACK_ID = "meaning-back";
    static final String CHOICE_PREFIX = "meaning-choice-";

    private static final double ROW_SPACING = 6;
    private static final double CHOICE_GAP = 12;

    private final ModalHost modalHost;
    private final Messages messages;
    private final CurrentProject project;
    private final MeaningQuestions questions;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param project the holder of the open book
     * @param questions where the names are found and the answers stored
     */
    @Inject
    public MeaningNameDialog(
            final ModalHost modalHost,
            final Messages messages,
            final CurrentProject project,
            final MeaningQuestions questions) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.project = Objects.requireNonNull(project, "project");
        this.questions = Objects.requireNonNull(questions, "questions");
    }

    /**
     * Goes on through the question: when names wait the card is shown and {@code next} runs after Save; otherwise
     * {@code next} runs as soon as the glossary has been read. FX thread only.
     *
     * @param next what follows the question; not run when the person goes back
     */
    public void askThen(final Runnable next) {
        Objects.requireNonNull(next, "next");
        final OpenedBook book = project.book().get();
        if (book == null) {
            log.debug("no book is open: no meaning question");
            next.run();
            return;
        }
        final String projectId = book.projectId();
        questions.find(projectId, found -> {
            if (found.isEmpty()) {
                log.debug("no name waits for its meaning question: going on");
                next.run();
                return;
            }
            log.debug("asking about the meaning of {} names of project {}", found.size(), projectId);
            show(projectId, found, next);
        });
    }

    private void show(final String projectId, final List<GlossaryEntry> names, final Runnable next) {
        final FormCard card = new FormCard(
                modalHost, messages, CARD_ID, ModalCard.Size.WIDE, messages.get(MessageKey.DIALOG_MEANING_TITLE));
        final VBox body = new VBox(ROW_SPACING);
        body.getChildren()
                .add(Wrapped.label(messages.get(MessageKey.DIALOG_MEANING_TEXT, names.size()), "dialog-text"));
        final Map<String, ToggleGroup> choices = new LinkedHashMap<>();
        for (final GlossaryEntry name : names) {
            body.getChildren().add(Wrapped.label(rowText(name), "dialog-text"));
            final ToggleGroup group = new ToggleGroup();
            body.getChildren().add(choiceRow(choices.size(), group));
            choices.put(name.id(), group);
        }
        card.card().setContent(body);
        wire(card, projectId, choices, next);
        card.show();
    }

    private String rowText(final GlossaryEntry name) {
        final String meaning = name.meaning();
        return meaning == null
                ? messages.get(MessageKey.DIALOG_MEANING_ROW_PLAIN, name.term())
                : messages.get(MessageKey.DIALOG_MEANING_ROW, name.term(), meaning);
    }

    // The three choices give way by wrapping, so a long Ukrainian label never squeezes its neighbour.
    private FlowPane choiceRow(final int index, final ToggleGroup group) {
        final FlowPane row = new FlowPane(CHOICE_GAP, ROW_SPACING);
        row.getChildren()
                .addAll(
                        choice(
                                index,
                                group,
                                MeaningChoice.TRANSCRIBE,
                                MessageKey.DIALOG_MEANING_CHOICE_TRANSCRIBE,
                                MessageKey.DIALOG_MEANING_CHOICE_TRANSCRIBE_TIP),
                        choice(
                                index,
                                group,
                                MeaningChoice.TRANSLATE,
                                MessageKey.DIALOG_MEANING_CHOICE_TRANSLATE,
                                MessageKey.DIALOG_MEANING_CHOICE_TRANSLATE_TIP),
                        choice(
                                index,
                                group,
                                MeaningChoice.TRANSCRIBE_GLOSS,
                                MessageKey.DIALOG_MEANING_CHOICE_GLOSS,
                                MessageKey.DIALOG_MEANING_CHOICE_GLOSS_TIP));
        return row;
    }

    // A node id is a CSS selector, so the row's place stands in for the entry id, which holds a colon.
    private RadioButton choice(
            final int row,
            final ToggleGroup group,
            final MeaningChoice value,
            final MessageKey label,
            final MessageKey tip) {
        final RadioButton radio = Tips.install(messages, new RadioButton(messages.get(label)), tip);
        radio.setId(CHOICE_PREFIX + value.name().toLowerCase(Locale.ROOT) + "-" + row);
        radio.setUserData(value);
        radio.setToggleGroup(group);
        radio.setSelected(value == MeaningChoice.TRANSCRIBE);
        radio.setMinWidth(Region.USE_PREF_SIZE);
        return radio;
    }

    private void wire(
            final FormCard card, final String projectId, final Map<String, ToggleGroup> choices, final Runnable next) {
        final Runnable unanswered = () -> {
            log.debug("the meaning question of project {} was closed: every name is sounded out", projectId);
            questions.answer(projectId, defaults(choices), () -> {});
        };
        card.button(
                BACK_ID,
                messages.get(MessageKey.DIALOG_MEANING_BACK),
                MessageKey.DIALOG_MEANING_BACK_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                unanswered);
        card.onEscape(unanswered);
        card.button(
                CONFIRM_ID,
                messages.get(MessageKey.DIALOG_MEANING_CONFIRM),
                MessageKey.DIALOG_MEANING_CONFIRM_TIP,
                ButtonBar.ButtonData.OK_DONE,
                "btn-primary",
                () -> {
                    final Map<String, MeaningChoice> answers = chosen(choices);
                    log.debug(
                            "the meaning question of project {} was answered for {} names", projectId, answers.size());
                    questions.answer(projectId, answers, next);
                });
    }

    private static Map<String, MeaningChoice> chosen(final Map<String, ToggleGroup> choices) {
        final Map<String, MeaningChoice> answers = new LinkedHashMap<>();
        choices.forEach((id, group) -> {
            final Toggle selected = group.getSelectedToggle();
            answers.put(
                    id,
                    selected != null && selected.getUserData() instanceof MeaningChoice choice
                            ? choice
                            : MeaningChoice.TRANSCRIBE);
        });
        return answers;
    }

    private static Map<String, MeaningChoice> defaults(final Map<String, ToggleGroup> choices) {
        final Map<String, MeaningChoice> answers = new LinkedHashMap<>();
        choices.keySet().forEach(id -> answers.put(id, MeaningChoice.TRANSCRIBE));
        return answers;
    }
}
