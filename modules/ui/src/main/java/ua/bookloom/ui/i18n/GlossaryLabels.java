package ua.bookloom.ui.i18n;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;

/**
 * The words for a glossary term's type, gender, target state, the origin of a value and a target's issues, which the
 * enums in {@code :api} cannot carry because the catalogue is a display concern of this module.
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
                    case ORGANISATION -> MessageKey.NAMES_STYLE_TYPE_ORGANISATION;
                    case PRODUCT -> MessageKey.NAMES_STYLE_TYPE_PRODUCT;
                    case MATERIAL -> MessageKey.NAMES_STYLE_TYPE_MATERIAL;
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

    /**
     * The word for a target's state.
     *
     * @param messages the catalogue to read
     * @param state the state to name
     * @return the state in the display language
     */
    public static String state(final Messages messages, final TargetState state) {
        Objects.requireNonNull(messages, "messages");
        return messages.get(
                switch (Objects.requireNonNull(state, "state")) {
                    case SUGGESTED -> MessageKey.NAMES_STYLE_STATE_SUGGESTED;
                    case PINNED -> MessageKey.NAMES_STYLE_STATE_PINNED;
                    case LOCKED -> MessageKey.NAMES_STYLE_STATE_LOCKED;
                });
    }

    /**
     * The word for where a value came from, as its chip reads.
     *
     * @param messages the catalogue to read
     * @param origin the origin to name
     * @return the origin in the display language
     */
    public static String origin(final Messages messages, final Origin origin) {
        Objects.requireNonNull(messages, "messages");
        return messages.get(
                switch (Objects.requireNonNull(origin, "origin")) {
                    case USER -> MessageKey.NAMES_STYLE_ORIGIN_USER;
                    case BOOK -> MessageKey.NAMES_STYLE_ORIGIN_BOOK;
                    case RULE -> MessageKey.NAMES_STYLE_ORIGIN_RULE;
                    case LIKELY -> MessageKey.NAMES_STYLE_ORIGIN_LIKELY;
                });
    }

    /**
     * The words for why a target is refused or flagged; a different word count reads the same as a refusal and as a
     * flag.
     *
     * @param messages the catalogue to read
     * @param issue the issue to name
     * @return the reason in the display language
     */
    public static String issue(final Messages messages, final TargetIssue issue) {
        Objects.requireNonNull(messages, "messages");
        return messages.get(
                switch (Objects.requireNonNull(issue, "issue")) {
                    case FORBIDDEN_LETTER -> MessageKey.NAMES_STYLE_ISSUE_FORBIDDEN_LETTER;
                    case LATIN -> MessageKey.NAMES_STYLE_ISSUE_LATIN;
                    case STRESS_MARK -> MessageKey.NAMES_STYLE_ISSUE_STRESS_MARK;
                    case CAPITALISATION -> MessageKey.NAMES_STYLE_ISSUE_CAPITALISATION;
                    case WORD_COUNT, WORD_COUNT_DIFFERS -> MessageKey.NAMES_STYLE_ISSUE_WORD_COUNT;
                    case CONVENTION -> MessageKey.NAMES_STYLE_ISSUE_CONVENTION;
                    case RESPELLING -> MessageKey.NAMES_STYLE_ISSUE_RESPELLING;
                    case OBLIQUE_ENDING -> MessageKey.NAMES_STYLE_ISSUE_OBLIQUE_ENDING;
                });
    }
}
