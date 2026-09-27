package ua.bookloom.api.persistence;

import ua.bookloom.api.Result;
import ua.bookloom.api.project.ChunkCommit;

/**
 * Applies one chunk's decided segments, translation-memory entries, glossary additions and deferrals together, so a
 * mid-chunk failure never leaves storage with a partial chunk (ADR-0034).
 */
public interface CheckpointPort {

    /**
     * Commits a chunk: its segment records, translation-memory entries and deferrals are all applied or none. A
     * glossary addition whose term already exists, ignoring case, or was removed by the person this session is
     * skipped and the existing entry is kept — it never fails the commit.
     *
     * @param commit the non-null chunk to commit
     * @return the number of items applied, or {@code validation} when a segment id does not belong to the project
     */
    Result<Integer> commit(ChunkCommit commit);
}
