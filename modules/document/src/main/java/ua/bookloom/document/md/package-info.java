/**
 * Markdown reading and writing ({@code 01_Product/03_DOCUMENT_FORMATS.md#markdown}).
 *
 * <p>The CommonMark syntax tree is used for <strong>analysis only</strong> — to locate each leaf block's byte span
 * in the original file. Its renderer is never invoked. Export copies the original bytes and substitutes only the
 * spans of segments that carry target text, which makes every untouched byte identical by construction: emphasis
 * markers, bullet characters, fence styles and whether the file ends with a newline all survive because nothing
 * re-renders them.
 */
@NullMarked
package ua.bookloom.document.md;

import org.jspecify.annotations.NullMarked;
