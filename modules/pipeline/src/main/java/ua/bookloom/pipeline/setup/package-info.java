/**
 * Suggests the Book Brief from the book itself: reads samples of the book, asks the model what kind of book it is and
 * who narrates, and keeps a suggested field only when the evidence the reply quotes is found in the book.
 *
 * <p>The package is not {@code @NullMarked} yet: NullAway rejects {@code BriefDecision.Decided}'s
 * {@code T extends @Nullable Object} value at its three null call sites, and marking it needs that record reworked,
 * which this change leaves to the next.
 */
package ua.bookloom.pipeline.setup;
