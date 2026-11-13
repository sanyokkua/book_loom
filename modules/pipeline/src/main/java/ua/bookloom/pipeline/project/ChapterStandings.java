package ua.bookloom.pipeline.project;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.pipeline.project.ChapterBoundaries.Chapter;
import ua.bookloom.util.hash.HashUtil;

/**
 * Where a chapter stands for its summary (design D10), by one rule that the run, which writes the missing summaries
 * before its first draft, and the Book Brief's count of them both follow, so the count says exactly what the next
 * Continue will do.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChapterStandings {

    // Masked sources are joined by a character no source holds, so two different chapters never hash the same.
    private static final String HASH_JOIN = "\u0000";

    /** Where a chapter stands, for its summary. */
    public enum Standing {
        /** Some of its segments are still pending; it is summarised when its last chunk commits. */
        OPEN,
        /** The book's last chapter, which no later window reads. */
        LAST,
        /** Its stored summary was written from the source it holds now. */
        STORED,
        /** Its stored summary was written from other source text. */
        STALE,
        /** It has no stored summary. */
        MISSING;

        /**
         * Whether a finished chapter in this standing needs its summary written.
         *
         * @return {@code true} for {@link #STALE} and {@link #MISSING}, {@code false} otherwise
         */
        public boolean needsSummary() {
            return this == STALE || this == MISSING;
        }
    }

    /**
     * Where one chapter stands.
     *
     * @param open whether any of its segments is still to be decided
     * @param chapters the non-null chapters of the book
     * @param chapter the non-null chapter, one of {@code chapters}
     * @param stored the summary stored for the chapter's key, or null when none is
     * @param held the non-null segments of the chapter, in reading order
     * @return never null
     */
    public static Standing standing(
            final boolean open,
            final ChapterBoundaries chapters,
            final Chapter chapter,
            @Nullable final ChapterSummary stored,
            final List<Segment> held) {
        Objects.requireNonNull(chapters, "chapters");
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(held, "held");
        if (open) {
            return Standing.OPEN;
        }
        if (isLast(chapters, chapter)) {
            return Standing.LAST;
        }
        if (stored == null) {
            return Standing.MISSING;
        }
        return stored.sourceHash().equals(sourceHash(held)) ? Standing.STORED : Standing.STALE;
    }

    /**
     * Whether a chapter is the book's last, which is never summarised since no later window reads it.
     *
     * @param chapters the non-null chapters of the book
     * @param chapter the non-null chapter, one of {@code chapters}
     * @return {@code true} if it is the last chapter, {@code false} otherwise
     */
    public static boolean isLast(final ChapterBoundaries chapters, final Chapter chapter) {
        final boolean last = chapter.number() == chapters.chapters().size();
        if (last) {
            log.debug(
                    "No chapter summary for chapter {}: it is the book's last, which no later window reads",
                    chapter.number());
        }
        return last;
    }

    /**
     * The hash of a chapter's source, which a stored summary keeps so a changed book is told from the one it was
     * written from.
     *
     * @param held the non-null segments of the chapter, in reading order
     * @return the hex SHA-256 of their masked texts joined by a character no source holds
     */
    public static String sourceHash(final List<Segment> held) {
        Objects.requireNonNull(held, "held");
        return HashUtil.sha256Hex(
                String.join(HASH_JOIN, held.stream().map(Segment::masked).toList())
                        .getBytes(StandardCharsets.UTF_8));
    }
}
