/**
 * Plain-text reading and writing ({@code 01_Product/03_DOCUMENT_FORMATS.md#txt}).
 *
 * <p>There is no parser here, and that is the design rather than an omission: a parser would be the bug. Plain
 * text has no markup, so the blank line is the only structure there is, and segmentation is a scan over the byte
 * buffer recording spans. Export copies the original buffer and substitutes only translated spans, which makes an
 * untranslated round trip byte-identical by construction — the encoding, the line endings and the byte-order mark
 * survive because nothing ever re-encodes them. This is the one format where exact bytes are the correct
 * assertion rather than an over-strict one.
 */
@NullMarked
package ua.bookloom.document.txt;

import org.jspecify.annotations.NullMarked;
