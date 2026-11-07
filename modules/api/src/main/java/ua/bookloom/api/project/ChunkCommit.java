package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A chunk's decided segments and everything they produced, applied to storage all together or not at all
 * (ADR-0034).
 *
 * @param projectId the owning project's id
 * @param segments the decided segment records to apply
 * @param tmEntries the translation-memory entries to apply
 * @param glossaryAdditions the glossary entries proposed by this chunk
 * @param deferrals the deferrals recorded by this chunk
 * @param resolvedDeferralIds the ids of deferrals this commit settles, deleted in the same step as the texts and
 *     verdicts that settled them
 * @param run the run's record as it stands at this chunk, stored in the same step so the run's counts never disagree
 *     with its stored decisions, or null when the commit carries none
 */
public record ChunkCommit(
        String projectId,
        List<SegmentRecord> segments,
        List<TmEntry> tmEntries,
        List<GlossaryEntry> glossaryAdditions,
        List<Deferral> deferrals,
        List<String> resolvedDeferralIds,
        @Nullable RunRecord run) {

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
        Objects.requireNonNull(resolvedDeferralIds, "resolvedDeferralIds");
        segments = List.copyOf(segments);
        tmEntries = List.copyOf(tmEntries);
        glossaryAdditions = List.copyOf(glossaryAdditions);
        deferrals = List.copyOf(deferrals);
        resolvedDeferralIds = List.copyOf(resolvedDeferralIds);
    }

    /**
     * Creates a commit that carries no run record.
     *
     * @param projectId the owning project's id
     * @param segments the decided segment records to apply
     * @param tmEntries the translation-memory entries to apply
     * @param glossaryAdditions the glossary entries proposed by this chunk
     * @param deferrals the deferrals recorded by this chunk
     */
    public ChunkCommit(
            final String projectId,
            final List<SegmentRecord> segments,
            final List<TmEntry> tmEntries,
            final List<GlossaryEntry> glossaryAdditions,
            final List<Deferral> deferrals) {
        this(projectId, segments, tmEntries, glossaryAdditions, deferrals, List.of(), null);
    }

    /**
     * Creates a commit that settles no deferral.
     *
     * @param projectId the owning project's id
     * @param segments the decided segment records to apply
     * @param tmEntries the translation-memory entries to apply
     * @param glossaryAdditions the glossary entries proposed by this chunk
     * @param deferrals the deferrals recorded by this chunk
     * @param run the run's record at this chunk, or null
     */
    public ChunkCommit(
            final String projectId,
            final List<SegmentRecord> segments,
            final List<TmEntry> tmEntries,
            final List<GlossaryEntry> glossaryAdditions,
            final List<Deferral> deferrals,
            @Nullable final RunRecord run) {
        this(projectId, segments, tmEntries, glossaryAdditions, deferrals, List.of(), run);
    }

    /**
     * Counts the segments that carry a proofread verdict.
     *
     * @return how many of {@link #segments()} hold one
     */
    public int verdictCount() {
        return (int) segments.stream().filter(s -> s.proofread() != null).count();
    }
}
