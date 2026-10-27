package ua.bookloom.ui.control;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.DecisionRow;

/**
 * Words the values of a decided row in the interface language: the register and the narrator come from a closed list the
 * brief itself words; a genre, an audience or a voice is the model's own text and is shown as it is.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DecisionWords {

    private static final String JOIN = " · ";

    static String nameOf(final Messages messages, final BriefField group) {
        return messages.get(
                switch (group) {
                    case GENRE -> MessageKey.BRIEF_DECIDES_GENRE;
                    case REGISTER -> MessageKey.BRIEF_DECIDES_REGISTER;
                    case NARRATOR, NARRATOR_GENDER -> MessageKey.BRIEF_DECIDES_NARRATOR;
                    case AUDIENCE, VOICE -> MessageKey.BRIEF_DECIDES_READERS;
                });
    }

    static String value(final Messages messages, final DecisionRow row) {
        return switch (row.group()) {
            case REGISTER -> register(messages, row.value());
            case NARRATOR -> narrator(messages, row.value());
            default -> row.value();
        };
    }

    private static String register(final Messages messages, final String value) {
        return switch (value) {
            case "Formal" -> messages.get(MessageKey.BRIEF_REGISTER_FORMAL);
            case "Neutral" -> messages.get(MessageKey.BRIEF_REGISTER_NEUTRAL);
            case "Casual" -> messages.get(MessageKey.BRIEF_REGISTER_CASUAL);
            default -> value;
        };
    }

    private static String narrator(final Messages messages, final String value) {
        final String[] parts = value.split(JOIN, 2);
        final String person =
                switch (parts[0]) {
                    case "First person" -> messages.get(MessageKey.BRIEF_NARRATOR_FIRST);
                    case "Third person" -> messages.get(MessageKey.BRIEF_NARRATOR_THIRD);
                    default -> parts[0];
                };
        if (parts.length == 1) {
            return person;
        }
        final String gender =
                switch (parts[1]) {
                    case "Male" -> messages.get(MessageKey.BRIEF_NARRATOR_GENDER_MALE);
                    case "Female" -> messages.get(MessageKey.BRIEF_NARRATOR_GENDER_FEMALE);
                    default -> parts[1];
                };
        return person + JOIN + gender;
    }
}
