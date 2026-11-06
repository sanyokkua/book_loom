package ua.bookloom.api.pipeline;

/**
 * How a project's chapters stand for their summaries, so the Book Brief can say what turning chapter summaries on
 * costs before the next passage: one model call for each chapter in {@code toSummarise}.
 *
 * @param chapters how many chapters the book has by the one chapter rule; zero or more
 * @param finished how many of them have every segment decided, the book's last chapter included; from zero to
 *     {@code chapters}
 * @param toSummarise how many finished chapters the next Continue would summarise first: those with no stored summary
 *     or one written from other source text, never the book's last chapter, which no later passage reads; from zero to
 *     {@code finished}
 */
public record ChapterTally(int chapters, int finished, int toSummarise) {

    /** Rejects counts that cannot hold together. */
    public ChapterTally {
        if (chapters < 0 || finished < 0 || finished > chapters || toSummarise < 0 || toSummarise > finished) {
            throw new IllegalArgumentException(
                    "chapters " + chapters + " finished " + finished + " toSummarise " + toSummarise);
        }
    }
}
