package ua.bookloom.pipeline.eval;

import java.util.Locale;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;

/**
 * What the brief says about the narrator in a sequence run. The 6 h run had no narrator in its brief, so {@code UNSET}
 * is the default and reproduces its gender slips; {@code SET} is the same book with a first-person male narrator in the
 * brief, which switches the style sheet's rule and the Ukrainian gender check on. {@code DETECT} starts from the same
 * unset brief and does what the application does at Start translation: the detector reads the source, and when it finds
 * first-person narration the brief takes a first-person narrator of the gender the person's answer names
 * ({@code BOOKLOOM_EVAL_NARRATOR_GENDER}, male by default).
 */
enum SequenceNarratorMode {

    /** The brief names no narrator. */
    UNSET("unset", NarratorPerson.UNSPECIFIED, Gender.UNKNOWN),
    /** The brief names a first-person male narrator. */
    SET("set", NarratorPerson.FIRST, Gender.MALE),
    /** The brief starts unset; first-person narration found in the source and the Start answer then set it. */
    DETECT("detect", NarratorPerson.UNSPECIFIED, Gender.UNKNOWN);

    private final String label;
    private final NarratorPerson person;
    private final Gender gender;

    SequenceNarratorMode(final String label, final NarratorPerson person, final Gender gender) {
        this.label = label;
        this.person = person;
        this.gender = gender;
    }

    String label() {
        return label;
    }

    Narrator narrator() {
        return new Narrator(person, gender);
    }

    /** The mode a {@code BOOKLOOM_EVAL_NARRATOR} value names; blank or null is {@code UNSET}. */
    static SequenceNarratorMode parse(final String value) {
        if (value == null || value.isBlank()) {
            return UNSET;
        }
        for (final SequenceNarratorMode mode : values()) {
            if (mode.label.equals(value.strip().toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        throw new IllegalArgumentException("BOOKLOOM_EVAL_NARRATOR must be set, unset or detect, not " + value);
    }

    /** The gender a {@code BOOKLOOM_EVAL_NARRATOR_GENDER} value names ({@code male} or {@code female}); blank is male. */
    static Gender parseGender(final String value) {
        if (value == null || value.isBlank()) {
            return Gender.MALE;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "male" -> Gender.MALE;
            case "female" -> Gender.FEMALE;
            default ->
                throw new IllegalArgumentException(
                        "BOOKLOOM_EVAL_NARRATOR_GENDER must be male or female, not " + value);
        };
    }
}
