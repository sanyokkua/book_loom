/**
 * Writes a stored project's decisions to a book file: the destination checks, the temporary-file chain (write, re-open,
 * per-segment verification, atomic move), the side files written beside the book, and the export service and job that
 * drive it.
 */
@NullMarked
package ua.bookloom.pipeline.export;

import org.jspecify.annotations.NullMarked;
