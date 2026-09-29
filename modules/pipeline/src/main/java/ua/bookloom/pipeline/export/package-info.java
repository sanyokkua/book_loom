/**
 * Writes a stored project's decisions to a book file: the destination checks, the temporary-file chain (write,
 * re-open, count, atomic move) and the export service and job that drive it.
 */
@NullMarked
package ua.bookloom.pipeline.export;

import org.jspecify.annotations.NullMarked;
