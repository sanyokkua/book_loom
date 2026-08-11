/**
 * Character-encoding resolution on the import path: the fixed ladder that decides, once per book, which charset
 * a file was written in and whether it began with a byte-order mark
 * ({@code 01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom}).
 *
 * <p>Format-agnostic on purpose. Getting the encoding wrong corrupts every non-Latin character in a book and the
 * corruption is invisible until someone reads the translated text, so the decision is made in exactly one place
 * rather than once per reader.
 */
@NullMarked
package ua.bookloom.document.detect;

import org.jspecify.annotations.NullMarked;
