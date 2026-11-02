package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * What the soft checks read besides the candidate: the languages and policies of the run, and the chunk's glossary and
 * character lines.
 *
 * @param frame the languages and the foreign-passage policy
 * @param namePolicy the Book Brief's name policy
 * @param glossaryTerms every glossary term, read by the name removal of the script and echo checks
 * @param glossaryPairs the unlocked glossary renderings of the chunk as {@code term → target} lines
 * @param characters the chunk's character gender sheet, one line each
 */
public record SoftContext(
        CallFrame frame,
        NamePolicy namePolicy,
        List<String> glossaryTerms,
        List<String> glossaryPairs,
        List<String> characters) {

    /** Rejects a missing part. */
    public SoftContext {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(glossaryPairs, "glossaryPairs");
        Objects.requireNonNull(characters, "characters");
    }
}
