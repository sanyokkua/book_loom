package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.pipeline.project.ChapterBoundaries;

/**
 * Where a work item stands among the book's chapters, which is what a run's progress counts in: a book can be one
 * unit holding many chapters, or many units holding one.
 */
final class ChapterNumbers {

    private final ChapterBoundaries chapters;
    private final int bodyUnits;

    ChapterNumbers(final ChapterBoundaries chapters, final int bodyUnits) {
        this.chapters = Objects.requireNonNull(chapters, "chapters");
        this.bodyUnits = bodyUnits;
    }

    /** {@return how many chapters the book has; its body units when it has no story text to cut into chapters} */
    int count() {
        return chapters.chapters().isEmpty() ? bodyUnits : chapters.chapters().size();
    }

    /**
     * The 1-based chapter of an item.
     *
     * @param item the non-null item
     * @param standing the chapter to report for an item outside the story (front matter, the auxiliary title): the run's
     *     last decided chapter, or the first before any decision
     * @return the item's chapter, else {@code standing}
     */
    int of(final WorkItem item, final int standing) {
        return chapters.chapterOf(item.segment().id())
                .map(ChapterBoundaries.Chapter::number)
                .orElse(standing);
    }
}
