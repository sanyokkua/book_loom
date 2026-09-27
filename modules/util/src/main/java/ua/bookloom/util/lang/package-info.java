/**
 * The 34-language catalogue the Book Brief offers and the one tag normalizer every caller shares.
 *
 * <p>Import, the brief, the token estimator and the script check all need the same answer to "which language is this
 * code" — a book spells one language as {@code EN}, {@code en-GB}, {@code en_GB} or the retired {@code ua} — so this
 * package is the single place that answer is computed (backlog decision debt D17).
 */
@NullMarked
package ua.bookloom.util.lang;

import org.jspecify.annotations.NullMarked;
