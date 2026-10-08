package ua.bookloom.pipeline.checks;

import java.util.List;
import ua.bookloom.api.project.Gender;

/**
 * A deterministic check of one target language that the narrator's own words agree with the narrator's gender. It is a
 * data-selected plug-in: a language file names its check with {@code genderCheck=<name>} and
 * {@link GenderChecks} maps the name to an implementation, so no other language is held to a rule it does not have.
 */
public interface GenderCheck {

    /**
     * Finds the narrator's words that carry the wrong gender.
     *
     * @param target the target's display text
     * @param narrator the narrator's gender, {@link Gender#MALE} or {@link Gender#FEMALE}
     * @return one soft finding per wrong word, in text order, each spanning that word; empty when the text agrees
     */
    List<CheckFinding> find(String target, Gender narrator);

    /**
     * Finds the words that disagree with a glossary character's gender: a masculine verb right after a woman's name,
     * or the name itself declined like a man's. Most languages have no such rule.
     *
     * @param target the target's display text
     * @param characters the glossary characters the segment names, each with the name as the target writes it
     * @return one soft finding per wrong word, in text order; empty when the text agrees or the language has no rule
     */
    default List<CheckFinding> findCharacters(final String target, final List<CharacterName> characters) {
        return List.of();
    }
}
