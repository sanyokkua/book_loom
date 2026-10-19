package ua.bookloom.ui.i18n;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TermType;

/**
 * The words for a glossary term's type and gender, which the two enums in {@code :api} cannot carry because the
 * catalogue is a display concern of this module.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlossaryLabels {

    /**
     * The word for a type.
     *
     * @param messages the catalogue to read
     * @param type the type to name
     * @return the type in the display language
     */
    public static String type(final Messages messages, final TermType type) {
        Objects.requireNonNull(messages, "messages");
        return messages.get(
                switch (Objects.requireNonNull(type, "type")) {
                    case CHARACTER -> MessageKey.NAMES_STYLE_TYPE_CHARACTER;
                    case PLACE -> MessageKey.NAMES_STYLE_TYPE_PLACE;
                    case TERM -> MessageKey.NAMES_STYLE_TYPE_TERM;
                    case TITLE -> MessageKey.NAMES_STYLE_TYPE_TITLE;
                    case OTHER -> MessageKey.NAMES_STYLE_TYPE_OTHER;
                });
    }

    /**
     * The word for a gender.
     *
     * @param messages the catalogue to read
     * @param gender the gender to name
     * @return the gender in the display language
     */
    public static String gender(final Messages messages, final Gender gender) {
        Objects.requireNonNull(messages, "messages");
        return messages.get(
                switch (Objects.requireNonNull(gender, "gender")) {
                    case FEMALE -> MessageKey.NAMES_STYLE_GENDER_FEMALE;
                    case MALE -> MessageKey.NAMES_STYLE_GENDER_MALE;
                    case NEUTER -> MessageKey.NAMES_STYLE_GENDER_NEUTER;
                    case UNKNOWN -> MessageKey.NAMES_STYLE_GENDER_UNKNOWN;
                });
    }
}
