package ua.bookloom.api.pipeline;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/**
 * What the model suggests for the tone and style of a Book Brief after reading the opening of the book. Every field is
 * a suggestion the person can accept or change; nothing is applied by itself.
 *
 * @param genre the genre in the model's words, or null when it named none
 * @param register the suggested register
 * @param voiceEra a short phrase on the narrator's voice and the era of the language, or null when nothing stands out
 * @param audience the likely readers, or null when none was named
 * @param narrator who tells the story
 * @param narratorGender the first-person narrator's gender, {@link Gender#UNKNOWN} when the text does not show it
 */
public record BriefSuggestion(
        @Nullable String genre,
        Register register,
        @Nullable String voiceEra,
        @Nullable String audience,
        NarratorPerson narrator,
        Gender narratorGender) {

    private static final Pattern THIRD_PERSON = Pattern.compile("(?iU)third[- ]person|\\bтрет\\p{L}*\\s+особ");
    private static final Pattern FIRST_PERSON = Pattern.compile("(?iU)first[- ]person|\\bперш\\p{L}*\\s+особ");

    /** Rejects a missing choice. */
    public BriefSuggestion {
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(narrator, "narrator");
        Objects.requireNonNull(narratorGender, "narratorGender");
    }

    /**
     * This suggestion held to a narrator that is already set: a person who chose the narrator, or the detector that
     * read it from the text, is never contradicted by a guess. The narrator and its gender are the set ones, and a
     * voice note that names the other grammatical person ("third-person limited" for a first-person book) is dropped.
     *
     * @param current the brief's narrator now; {@link Narrator#unspecified()} when nothing is set
     * @return this suggestion when the brief names no narrator person, else the aligned one
     */
    public BriefSuggestion alignedTo(final Narrator current) {
        Objects.requireNonNull(current, "current");
        if (current.person() == NarratorPerson.UNSPECIFIED) {
            return this;
        }
        final boolean contradicts = voiceEra != null && contradicts(current.person(), voiceEra);
        return new BriefSuggestion(
                genre, register, contradicts ? null : voiceEra, audience, current.person(), current.gender());
    }

    private static boolean contradicts(final NarratorPerson person, final String voice) {
        return switch (person) {
            case FIRST -> THIRD_PERSON.matcher(voice).find();
            case THIRD -> FIRST_PERSON.matcher(voice).find();
            case UNSPECIFIED -> false;
        };
    }
}
