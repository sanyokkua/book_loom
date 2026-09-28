/**
 * Divides a segment's masked text at the source language's sentence boundaries, so a paragraph longer than the
 * chunk budget can be translated in pieces that fit back together exactly — never cutting inside an inline pair
 * (ADR-0040).
 */
@NullMarked
package ua.bookloom.document.split;

import org.jspecify.annotations.NullMarked;
