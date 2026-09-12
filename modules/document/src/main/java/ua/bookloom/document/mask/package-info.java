/**
 * Format-agnostic masking machinery: the placeholder token grammar, the mutable accumulator that mints tokens and
 * checks the mask-time invariant, the plain-text masker, the placeholder-multiset gate, and the restore pass that
 * substitutes tokens back and escapes markup-shaped output. Imports nothing but {@code :api} and the JDK, so a
 * tree-shaped format's masker ({@code ua.bookloom.document.model.TreeMasker}) and a buffer-shaped format's masker
 * can both build on it without either depending on jsoup or JDOM2 (design.md D10, ADR-0031).
 *
 * <p>Not exported: only {@code ua.bookloom.document} crosses the module boundary
 * (see {@code .claude/rules/architecture-layering.md}).
 */
@NullMarked
package ua.bookloom.document.mask;

import org.jspecify.annotations.NullMarked;
