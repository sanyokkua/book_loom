package ua.bookloom.pipeline.eval;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * One glossary entry of a case, as the glossary table would hold it: the run turns it into the prompt's term line (or
 * hides a locked one behind a token) itself, so a case never writes the line by hand.
 *
 * @param term the source term as written in the text
 * @param target the chosen rendering, or null while unresolved
 * @param type the term's category; {@link TermType#OTHER} when a case file leaves it out
 * @param gender the term's grammatical gender; {@link Gender#UNKNOWN} when a case file leaves it out
 * @param locked whether the rendering is locked, so the model sees a token for the term
 */
public record EvalTerm(String term, @Nullable String target, TermType type, Gender gender, boolean locked) {

    /** Fills what a case file leaves out. */
    public EvalTerm {
        Objects.requireNonNull(term, "term");
        type = type == null ? TermType.OTHER : type;
        gender = gender == null ? Gender.UNKNOWN : gender;
    }

    public GlossaryEntry entry(final String projectId) {
        return new GlossaryEntry("eval:" + term, projectId, term, target, type, gender, locked);
    }
}
