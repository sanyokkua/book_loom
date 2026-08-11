/**
 * EPUB 2/3 read (task group 2) and, from a later change, write: container/OPF/spine parsing, DRM adjudication
 * before anything else is parsed (design.md D7), per-spine-document XHTML parsing, and the in-memory registry
 * that lets a future {@code write()} find the parsed trees a {@code SkeletonHandle} names (design.md D1).
 *
 * <p>Not exported: only {@code ua.bookloom.document} crosses the module boundary
 * (see {@code .claude/rules/architecture-layering.md}).
 */
@NullMarked
package ua.bookloom.document.epub;

import org.jspecify.annotations.NullMarked;
