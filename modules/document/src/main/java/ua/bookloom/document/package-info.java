/**
 * Document service facade and format dispatch — the implementation of {@code DocumentPort}.
 *
 * <p>Format-specific packages ({@code epub}, {@code fb2}, {@code md}, {@code txt}, {@code model}) and {@code mask}
 * (inline masking, unmask, and the placeholder-multiset gate) have arrived with the round-trip changes.
 * {@code detect} has arrived too, but so far only for character-encoding resolution ({@code CharsetLadder}) and a
 * script-coherence check ({@code ForeignWordCoherence}); source-language detection proper has not yet.
 */
@NullMarked
package ua.bookloom.document;

import org.jspecify.annotations.NullMarked;
