package ua.bookloom.document.fb2;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import org.jdom2.Element;
import org.jspecify.annotations.Nullable;

/**
 * Everything {@code Fb2Writer} needs to find again while a book is open, keyed by the opaque ids that cross the
 * {@code :api} boundary as {@code SkeletonHandle}s — the parsed tree itself never leaves {@code :document}.
 *
 * @param document the book's parsed XML tree, mutated in place on write-back and then serialized
 * @param sourceName the source file's own name, used as each unit's href
 * @param zipMemberName the member name inside a {@code .fb2.zip}, or {@code null} when the source was a bare
 *     {@code .fb2} — export re-emits the container it was given, so a user who imported a zipped book expects one
 *     back, under the same member name
 * @param charset the encoding the book was read with
 * @param declaredEncodingName the encoding name exactly as the source's XML declaration spelled it, so a
 *     preserved encoding is echoed as written rather than as this JVM would normalise it; {@code null} when the
 *     source declared none
 * @param bodiesByHandleId each {@code <body>} element, keyed by its unit's {@code SkeletonHandle} id
 * @param crlfLineEndings whether the source bytes used {@code \r\n} line endings, so the writer emits the same
 *     style instead of JDOM's default — an LF book must not come back with every line ending doubled
 */
record ParsedFb2(
        org.jdom2.Document document,
        String sourceName,
        @Nullable String zipMemberName,
        Charset charset,
        @Nullable String declaredEncodingName,
        Map<String, Element> bodiesByHandleId,
        boolean crlfLineEndings) {

    ParsedFb2 {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(bodiesByHandleId, "bodiesByHandleId");
        bodiesByHandleId = Map.copyOf(bodiesByHandleId);
    }
}
