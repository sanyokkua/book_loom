package ua.bookloom.document.epub;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

/**
 * Parses a spine XHTML content document with jsoup, pinning output settings so the round trip does not normalize
 * whitespace (task 2.6, FR-DOC-EPUB-5). jsoup is used deliberately over an XML parser here — see design.md D4 —
 * because real-world XHTML is HTML-shaped and an XML parser rejects it.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class XhtmlParser {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final int PROLOG_PROBE_BYTES = 200;
    private static final Pattern ENCODING_DECLARATION = Pattern.compile("encoding=[\"']([^\"']+)[\"']");

    private static final Pattern QUOTED_VALUE = Pattern.compile("\"[^\"]*\"|'[^']*'");

    /**
     * Parses a spine document's bytes, pinning output settings for a whitespace-faithful round trip.
     *
     * @param content the spine document's decompressed bytes
     * @param baseUri the entry's archive path, used as jsoup's base URI for this document
     * @return the parsed tree, with {@code prettyPrint(false)}, the detected source charset, and XML output
     *     syntax pinned ({@code 03_DOCUMENT_MODEL.md#xml-round-trip-config})
     */
    static Document parse(byte[] content, String baseUri) {
        final Charset charset = detectCharset(content);
        final String decoded = withoutByteOrderMark(new String(content, charset));
        final Document doc =
                Jsoup.parse(expandSelfClosedNonVoidElement(normalizeAttributeWhitespace(decoded)), baseUri);
        clearSeenSelfCloseOnNonVoidElements(doc);
        doc.outputSettings().prettyPrint(false).charset(charset).syntax(Document.OutputSettings.Syntax.xml);
        return doc;
    }

    /**
     * Turns a raw line feed, carriage return or tab inside a start tag's quoted attribute value into a space (a
     * carriage-return-line-feed pair counts once), as an XML reader does. jsoup keeps the raw character, and
     * {@link AttributeLineFeedEscaper} writes every line feed back as a character reference — which an XML reader
     * reads as a line feed, not the space the source meant. Doing this before jsoup is the only point where a raw
     * character can still be told apart from a reference such as a decimal line-feed reference, which is left for jsoup to decode.
     */
    private static String normalizeAttributeWhitespace(String xhtml) {
        final TagScanner scanner = new TagScanner(xhtml);
        final StringBuilder normalized = new StringBuilder();
        int copied = 0;
        int open = scanner.nextTag(0);
        while (open >= 0) {
            final int end = scanner.startTagEnd(open);
            if (end >= 0) {
                final String tag = QUOTED_VALUE
                        .matcher(xhtml.substring(open, end))
                        .replaceAll(value -> Matcher.quoteReplacement(
                                value.group().replace("\r\n", " ").replaceAll("[\\n\\r\\t]", " ")));
                normalized.append(xhtml, copied, open).append(tag);
                copied = end;
            }
            open = scanner.nextTag(end >= 0 ? end : open + 1);
        }
        return normalized.append(xhtml, copied, xhtml.length()).toString();
    }

    /**
     * A leading byte-order mark is not document text: left in, jsoup keeps it as a text node, opens an implicit
     * {@code body} around it and pushes the whole {@code head} into that body. {@link XhtmlProlog} already writes
     * the mark back as part of the source's own prolog, so dropping it here keeps it written exactly once.
     */
    private static String withoutByteOrderMark(String decoded) {
        return !decoded.isEmpty() && decoded.charAt(0) == '\uFEFF' ? decoded.substring(1) : decoded;
    }

    /**
     * Applied to the decoded document text before jsoup ever sees it (design.md D1/D2b), rather than as an
     * XML-parse fallback, so the parser taken is independent of document content for every book (design.md D1's
     * rejected alternative). This is the <strong>structural</strong> half of the self-closed-element fix: it
     * stops a self-closed known non-void element from staying open across the rest of the document and absorbing
     * what follows it (F12a). {@link #clearSeenSelfCloseOnNonVoidElements(Document)} below is the independent,
     * complementary half — it fixes the write not being a fixed point (F12b) — and both are needed: F12b's
     * contagion can be triggered by a self-closed element this method deliberately leaves alone (an unknown tag,
     * whose self-close jsoup honours rather than ignores), so fixing F12a here does not also fix F12b.
     *
     * @param xhtml the decoded content document
     * @return {@code xhtml} with every self-closed element whose tag is known to jsoup and not void expanded to
     *     an explicit open/close pair; unaffected otherwise
     */
    private static String expandSelfClosedNonVoidElement(String xhtml) {
        final TagScanner scanner = new TagScanner(xhtml);
        final StringBuilder expanded = new StringBuilder();
        int copied = 0;
        int open = scanner.nextTag(0);
        while (open >= 0) {
            final TagScanner.SelfClosed element = scanner.selfClosedAt(open);
            int resume = open + 1;
            if (element != null) {
                resume = element.end();
                if (isKnownNonVoidTag(element.name())) {
                    expanded.append(xhtml, copied, open)
                            .append('<')
                            .append(element.name())
                            .append(element.attributes())
                            .append("></")
                            .append(element.name())
                            .append('>');
                    copied = resume;
                }
            }
            open = scanner.nextTag(resume);
        }
        return expanded.append(xhtml, copied, xhtml.length()).toString();
    }

    /**
     * @param tagName a tag name captured from the raw document text, not yet case-normalized
     * @return {@code true} if jsoup knows this HTML tag and its content model is not empty — the set a self-close
     *     must be repaired on before parsing (task 5.1)
     */
    private static boolean isKnownNonVoidTag(String tagName) {
        final String normalized = tagName.toLowerCase(Locale.ROOT);
        return Tag.isKnownTag(normalized) && !Tag.valueOf(normalized).isEmpty();
    }

    /**
     * The <strong>serialization</strong> half of the self-closed-element fix (F12b, design.md D2b). {@code
     * Tag.SeenSelfClose} is a bit on the {@code Tag} object shared by every element of that name in the document,
     * set by jsoup's tokeniser the first time it sees ANY instance of that tag self-close — including a void or
     * unknown tag that {@link #expandSelfClosedNonVoidElement(String)} above deliberately never touches, because
     * expanding those would either be wrong (void) or pointless (unknown, already round-trips). Left set, jsoup
     * then self-closes EVERY empty element sharing that tag on output, even one the source never wrote
     * self-closed — which is not a fixed point, because the next parse does not treat a self-closed non-void
     * element as closed (measured: {@code o1 != o2}, then {@code o2 == o3} once the corruption has already
     * happened once). Clearing the bit on every non-void tag right after parse — and only non-void tags, so a
     * void element such as {@code <img/>}/{@code <br/>} keeps self-closing and the XHTML stays well-formed — makes
     * every empty non-void element serialize as an explicit open/close pair instead, which is stable under a
     * second write.
     *
     * @param doc the freshly parsed tree to normalize before this class pins the output syntax
     */
    private static void clearSeenSelfCloseOnNonVoidElements(Document doc) {
        for (final Element element : doc.getAllElements()) {
            final Tag tag = element.tag();
            if (!tag.isEmpty()) {
                tag.clear(Tag.SeenSelfClose);
            }
        }
    }

    private static Charset detectCharset(byte[] content) {
        final int probeLength = Math.min(content.length, PROLOG_PROBE_BYTES);
        final String prolog = new String(content, 0, probeLength, StandardCharsets.US_ASCII);
        final Matcher matcher = ENCODING_DECLARATION.matcher(prolog);
        if (!matcher.find()) {
            return DEFAULT_CHARSET;
        }
        try {
            return Charset.forName(matcher.group(1));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException _) {
            return DEFAULT_CHARSET;
        }
    }
}
