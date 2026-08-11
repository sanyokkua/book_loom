/**
 * FictionBook 2 reading and writing: one XML document per book, optionally wrapped in a zip container
 * ({@code 01_Product/03_DOCUMENT_FORMATS.md#fb2}).
 *
 * <p>FB2 keeps everything in one file — prose, verse, footnotes, and the cover as base64 text — so everything the
 * file carries is at risk from one careless re-serializer. That is why this package parses and writes through the
 * same strict JDOM2 configuration the OPF uses, and why the round-trip comparison checks the declared encoding
 * and each binary payload separately from the canonical tree.
 */
@NullMarked
package ua.bookloom.document.fb2;

import org.jspecify.annotations.NullMarked;
