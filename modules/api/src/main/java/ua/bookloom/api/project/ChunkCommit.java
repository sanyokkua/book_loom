package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;

/**
 * A chunk's decided segments and everything they produced, applied to storage all together or not at all
 * (ADR-0034).
 *
 * @param projectId the owning project's id
 * @param segments the decided segment records to apply
 * @param tmEntries the translation-memory entries to apply
 * @param glossaryAdditions the glossary entries proposed by this chunk
 * @param deferrals the deferrals recorded by this chunk
 */
public record ChunkCommit(
        String projectId,
        List<SegmentRecord> segments,
        List<TmEntry> tmEntries,
        List<GlossaryEntry> glossaryAdditions,
        List<Deferral> deferrals) {

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies the lists, so a caller-held
     * mutable list cannot corrupt this record after construction.
     */
    public ChunkCommit {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(tmEntries, "tmEntries");
        Objects.requireNonNull(glossaryAdditions, "glossaryAdditions");
        Objects.requireNonNull(deferrals, "deferrals");
        segments = List.copyOf(segments);
        tmEntries = List.copyOf(tmEntries);
        glossaryAdditions = List.copyOf(glossaryAdditions);
        deferrals = List.copyOf(deferrals);
    }
}
