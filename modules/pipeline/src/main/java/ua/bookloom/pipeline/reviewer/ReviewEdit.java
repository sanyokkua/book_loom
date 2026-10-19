package ua.bookloom.pipeline.reviewer;

import java.util.Objects;

/**
 * One find-and-replace edit the reviewer asks for, exactly as it wrote it; nothing here has been checked yet.
 *
 * @param criterion what the reviewer says was wrong
 * @param quote the text to find in the candidate, in masked form; never empty
 * @param replacement the text to put there; empty to delete the quote
 */
public record ReviewEdit(ReviewCriterion criterion, String quote, String replacement) {

    /** Rejects a missing component. */
    public ReviewEdit {
        Objects.requireNonNull(criterion, "criterion");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(replacement, "replacement");
    }
}
