/**
 * The document model — {@code Document}, {@code Unit}, {@code Segment} and the skeleton/segment contracts realizing
 * seam F1, plus the {@code DocumentPort} port {@code :document} implements.
 *
 * <p>Framework-free like the rest of {@code :api}: no parser type may appear here — the skeleton crosses this
 * boundary only as the opaque {@code SkeletonHandle}.
 */
@NullMarked
package ua.bookloom.api.document;

import org.jspecify.annotations.NullMarked;
