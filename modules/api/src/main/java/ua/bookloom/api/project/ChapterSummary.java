package ua.bookloom.api.project;

import java.util.Objects;

/**
 * The kept summary of one finished chapter, written in the target language so later windows know the story so far
 * with the names the book settled ({@code specs/local-storage/spec.md} "Store each chapter summary as its own record").
 *
 * @param projectId the owning project's id
 * @param chapterKey the chapter's key: its unit id plus the position of its first segment; unique within a project
 * @param unitId the unit the chapter starts in
 * @param firstSegment the position of the chapter's first segment within that unit; zero or more
 * @param segmentCount how many segments the chapter holds; one or more
 * @param text the summary text in the target language; never blank
 * @param sourceHash the hash of the chapter's source text the summary was written from, so a changed chapter is
 *     summarised again; never blank
 */
public record ChapterSummary(
        String projectId,
        String chapterKey,
        String unitId,
        int firstSegment,
        int segmentCount,
        String text,
        String sourceHash) {

    /** Rejects a record no chapter could have. */
    public ChapterSummary {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(chapterKey, "chapterKey");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sourceHash, "sourceHash");
        if (firstSegment < 0) {
            throw new IllegalArgumentException("firstSegment must be zero or more, but was " + firstSegment);
        }
        if (segmentCount < 1) {
            throw new IllegalArgumentException("segmentCount must be one or more, but was " + segmentCount);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (sourceHash.isBlank()) {
            throw new IllegalArgumentException("sourceHash must not be blank");
        }
    }
}
