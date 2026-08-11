/**
 * Format-agnostic seam F1 machinery: walking a parsed body into an ordered segment list and computing a
 * segment's skeleton anchor. Lands here rather than in {@code document.epub} so a later FB2/Markdown/TXT
 * importer (the next change) calls the same code instead of re-deriving it
 * ({@code 01_MODULE_INVENTORY.md#module-document}).
 *
 * <p>Not exported: only {@code ua.bookloom.document} crosses the module boundary
 * (see {@code .claude/rules/architecture-layering.md}).
 */
@NullMarked
package ua.bookloom.document.model;

import org.jspecify.annotations.NullMarked;
