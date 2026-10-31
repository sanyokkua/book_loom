package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.UnknownGenders;

/**
 * The question asked at Start translation when characters the book names often still have no gender, or only a
 * likely or rule one: it lists them in the order given (least confident first) with the offered gender chosen, and
 * offers Back (set or confirm the genders in Names &amp; style first) or Start anyway, which Enter chooses, so a run is
 * never held back by it. Start stores the rows the person ticked or changed as the person's own genders; an untouched
 * row stays a hint. Once started anyway the question is not asked again for the book in this session. Back, and Escape,
 * which the host guarantees for every card, return without starting.
 */
@Slf4j
@Singleton
public final class CharacterGendersDialog {

    static final String CARD_ID = "genders-card";
    static final String START_ID = "genders-start";
    static final String BACK_ID = "genders-back";

    private static final double ROW_SPACING = 10;

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
            final List<GenderRow> rows = new ArrayList<>();
            final FormCard card = card(found, rows);
            wire(card, projectId, rows, start);
            card.show();
        });
    }

    private FormCard card(final List<UnknownGender> found, final List<GenderRow> rows) {
        final boolean anyMissing = found.stream().anyMatch(character -> !character.suggested());
        final VBox body = new VBox(ROW_SPACING);
        intro(body, found, false, MessageKey.DIALOG_GENDERS_TEXT);
        intro(body, found, true, MessageKey.DIALOG_GENDERS_SUGGESTED_TEXT);
        for (int index = 0; index < found.size(); index++) {
            final UnknownGender character = found.get(index);
            final MessageKey key =
                    character.suggested() ? MessageKey.DIALOG_GENDERS_SUGGESTED_ROW : MessageKey.DIALOG_GENDERS_ROW;
            final GenderRow row = new GenderRow(
                    messages, character, messages.get(key, character.term(), character.mentions()), index);
            rows.add(row);
            body.getChildren().add(row.node());
        }
        final FormCard card = new FormCard(
                modalHost,
                messages,
                CARD_ID,
                ModalCard.Size.WIDE,
                messages.get(anyMissing ? MessageKey.DIALOG_GENDERS_TITLE : MessageKey.DIALOG_GENDERS_SUGGESTED_TITLE));
        card.card().setContent(body);
        return card;
    }

    // One intro per kind of character present; the rows themselves follow in the order given.
    private void intro(
            final VBox body, final List<UnknownGender> found, final boolean suggested, final MessageKey intro) {
        final long count = found.stream()
                .filter(character -> character.suggested() == suggested)
                .count();
        if (count > 0) {
            body.getChildren().add(Wrapped.label(messages.get(intro, count), "dialog-text"));
        }
    }

    private void wire(final FormCard card, final String projectId, final List<GenderRow> rows, final Runnable start) {
        card.button(
                BACK_ID,
                messages.get(MessageKey.DIALOG_GENDERS_BACK),
                MessageKey.DIALOG_GENDERS_BACK_TIP,
                ButtonBar.ButtonData.CANCEL_CLOSE,
                "btn-secondary",
                () -> log.debug("going back without starting"));
        card.button(
                START_ID,
                messages.get(MessageKey.DIALOG_GENDERS_START),
                MessageKey.DIALOG_GENDERS_START_TIP,
                ButtonBar.ButtonData.OK_DONE,
                "btn-primary",
                () -> {
                    answered.add(projectId);
                    final Map<String, Gender> answers = ticked(rows);
                    log.debug(
                            "starting project {} with {} of {} genders confirmed",
                            projectId,
                            answers.size(),
                            rows.size());
                    if (answers.isEmpty()) {
                        start.run();
                    } else {
                        unknown.answer(projectId, answers, start);
                    }
                });
    }

    // Only a ticked row is the person's answer; an untouched one stays a hint.
    private static Map<String, Gender> ticked(final List<GenderRow> rows) {
        final Map<String, Gender> answers = new LinkedHashMap<>();
        for (final GenderRow row : rows) {
            if (row.isTicked() && !row.entryId().isEmpty()) {
                answers.put(row.entryId(), row.chosen());
            }
        }
        return answers;
    }
}
