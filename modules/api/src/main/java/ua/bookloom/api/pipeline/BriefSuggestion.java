package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
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

    /** Rejects a missing choice. */
    public BriefSuggestion {
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(narrator, "narrator");
        Objects.requireNonNull(narratorGender, "narratorGender");
    }
}
