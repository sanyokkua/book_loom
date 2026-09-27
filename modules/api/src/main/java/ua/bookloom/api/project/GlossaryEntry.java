package ua.bookloom.api.project;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A glossary term for one project, shown as an editable table on Names &amp; style
 * ({@code specs/glossary/spec.md} "Show the glossary as an editable table on Names & style").
 *
 * @param id the entry's stable id
 * @param projectId the owning project's id
 * @param term the source term
 * @param target the term's chosen rendering, or null when unresolved
 * @param type the term's category
 * @param gender the term's grammatical gender
 * @param locked whether the term's rendering is locked against automatic revision
 */
public record GlossaryEntry(
        String id,
        String projectId,
        String term,
        @Nullable String target,
        TermType type,
        Gender gender,
        boolean locked) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public GlossaryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(gender, "gender");
    }
}
