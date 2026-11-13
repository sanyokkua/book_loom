package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.document.UnitRole;
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
 * @param unitRole the role of the chunk's unit
 * @param pinnedTargets the renderings of the chunk's pinned glossary entries, whose declined forms are no spelling
 *     error; an empty list when none is known
 */
public record SoftContext(
        CallFrame frame,
        NamePolicy namePolicy,
        List<String> glossaryTerms,
        List<String> glossaryPairs,
        List<String> characters,
        UnitRole unitRole,
        List<String> pinnedTargets) {

    /** A context that knows no pinned entry. */
    public SoftContext(
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs,
            final List<String> characters,
            final UnitRole unitRole) {
        this(frame, namePolicy, glossaryTerms, glossaryPairs, characters, unitRole, List.of());
    }

    /** A context for a segment of a body unit. */
    public SoftContext(
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs,
            final List<String> characters) {
        this(frame, namePolicy, glossaryTerms, glossaryPairs, characters, UnitRole.BODY);
    }

    /** Rejects a missing part. */
    public SoftContext {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(glossaryPairs, "glossaryPairs");
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(unitRole, "unitRole");
        Objects.requireNonNull(pinnedTargets, "pinnedTargets");
    }
}
