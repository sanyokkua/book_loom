package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Label;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The line under the narrator's gender that says where a gender nobody chose came from: the book's own words prove it
 * ("Male, from the book"), or it is the model's guess ("Male (likely)") until the person chooses a gender, which makes
 * it theirs and hides the line.
 */
@Slf4j
final class NarratorGenderOrigin {

    private record Words(MessageKey text, MessageKey tip) {}

    private static final Words LIKELY =
            new Words(MessageKey.BRIEF_NARRATOR_GENDER_LIKELY, MessageKey.BRIEF_NARRATOR_GENDER_LIKELY_TIP);
    private static final Words FROM_BOOK =
            new Words(MessageKey.BRIEF_NARRATOR_GENDER_FROM_BOOK, MessageKey.BRIEF_NARRATOR_GENDER_FROM_BOOK_TIP);

    private final Messages messages;
    private final Label line = new Label();

    NarratorGenderOrigin(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        line.setId("brief-tone-narrator-gender-origin");
        line.getStyleClass().add("hint");
        Wrapped.wholeLines(line);
        Visibility.show(line, false);
    }

    Node node() {
        return line;
    }

    void show(final @Nullable BookBrief brief) {
        final Narrator narrator = brief == null ? Narrator.unspecified() : brief.narrator();
        final Words words =
                switch (narrator.genderOrigin()) {
                    case SUGGESTED -> LIKELY;
                    case VERIFIED -> FROM_BOOK;
                    case PERSON, INFERRED, NONE -> null;
                };
        if ((words != null) != line.isVisible()) {
            log.debug(
                    "narrator gender {} of origin {} is marked: {}",
                    narrator.gender(),
                    narrator.genderOrigin(),
                    words != null);
        }
        Visibility.show(line, words != null);
        if (words != null) {
            final MessageKey gender = narrator.gender() == Gender.MALE
                    ? MessageKey.BRIEF_NARRATOR_GENDER_MALE
                    : MessageKey.BRIEF_NARRATOR_GENDER_FEMALE;
            line.setText(messages.get(words.text(), messages.get(gender)));
            Tips.install(messages, line, words.tip());
        }
    }
}
