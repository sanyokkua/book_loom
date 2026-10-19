package ua.bookloom.pipeline.eval;

import java.util.Objects;

/**
 * One answer of a {@link CorefReader}: a quoted stretch of the window and whether the model says its pronoun refers to
 * the term. A {@code false} claim, or no claim at all, is an abstention.
 *
 * @param quote the words the model quoted from the window
 * @param refersToTerm whether the model says the quoted pronoun refers to the term
 */
record CorefClaim(String quote, boolean refersToTerm) {

    /** Rejects a missing quote. */
    CorefClaim {
        Objects.requireNonNull(quote, "quote");
    }
}
