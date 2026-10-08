package ua.bookloom.pipeline.checks;

import java.util.Objects;
import ua.bookloom.api.project.Gender;

/**
 * A glossary character as the target text must show it.
 *
 * @param rendering the target spelling of the name, as the glossary holds it
 * @param gender the character's gender, which the words around the name must agree with
 */
public record CharacterName(String rendering, Gender gender) {

    /** Rejects missing parts. */
    public CharacterName {
        Objects.requireNonNull(rendering, "rendering");
        Objects.requireNonNull(gender, "gender");
    }
}
