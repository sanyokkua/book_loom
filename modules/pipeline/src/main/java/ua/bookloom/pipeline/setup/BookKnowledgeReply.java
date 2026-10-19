package ua.bookloom.pipeline.setup;

import org.jspecify.annotations.Nullable;

/**
 * What the model answered when asked what it knows of the book.
 *
 * @param known whether the model says it knows this book
 * @param title the title it echoes, or null
 * @param author the author it echoes, or null
 * @param year the year it gives, or null
 * @param genre its genre, or null
 * @param audience its audience, or null
 * @param era its era or voice, or null
 * @param narration its narration (person and gender), or null
 * @param summary its summary, or null
 * @param confidence its confidence, 0.0 to 1.0
 */
public record BookKnowledgeReply(
        boolean known,
        @Nullable String title,
        @Nullable String author,
        @Nullable Integer year,
        @Nullable String genre,
        @Nullable String audience,
        @Nullable String era,
        @Nullable String narration,
        @Nullable String summary,
        double confidence) {}
