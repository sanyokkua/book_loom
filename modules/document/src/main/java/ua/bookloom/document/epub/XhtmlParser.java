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

    /**
     * Matches any element written in XML self-closing form — {@code <name .../>} — for any tag name. Originally
     * (task 4.1) this pattern's tag-name group was the fixed alternation {@code (script|style|noscript)}; task
     * 5.1 generalizes it to any name and moves the decision of which matches actually get rewritten to {@link
     * #isKnownNonVoidTag(String)}, evaluated per match. That decision is jsoup's own public API, not a
     * hand-maintained list, and it happens to subsume the original three: {@code script}/{@code style}/
     * {@code noscript} are known and non-void, so they are still rewritten, for the reason task 4.1 measured —
     * their HTML content model is raw text, so only a literal {@code </script>} (etc.) ends one, not a
     * self-closing {@code />}, and jsoup's HTML tree builder swallows the rest of the document as that element's
     * text. Task 5.1 measured the same failure mode on ordinary non-void elements — {@code <a/>}, {@code <span/>}
     * — which stay open and pull a following sibling inside them instead of swallowing raw text, but the
     * mechanism jsoup applies is the same: a self-close is <em>ignored</em> on a known non-void tag. jsoup instead
     * <em>honours</em> a self-close on an unknown tag (an unrecognized or custom element closes cleanly and round
     * trips) and on a void tag (e.g. {@code <img/>}, {@code <br/>}, which is correctly always self-closing) — both
     * are left alone by {@link #isKnownNonVoidTag(String)}, so repairing them would be inert at best. Foreign
     * content (SVG, MathML) is not registered under jsoup's HTML namespace, so {@code Tag.isKnownTag} reports it
     * unknown and it too is left alone. The attribute-value alternation ({@code "[^"]*"} / {@code '[^']*'})
     * consumes a quoted value such as {@code src="js/book.js"} as one unit so an embedded {@code >} or {@code /}
     * cannot end the match early, and the reluctant quantifier then finds the first genuinely unquoted {@code />}
     * — an already-paired element such as {@code <script type="text/javascript">...</script>} never matches,
     * since it closes with a bare {@code >}. {@code \b} after the tag name rejects a different element name
     * sharing the prefix, e.g. {@code <scriptfoo/>}.
     *
     * <p>{@code EpubCanonicalAssert} in the test source set carries a byte-for-byte duplicate of this pattern and
     * of {@link #isKnownNonVoidTag(String)}, applied to both sides of the golden comparison (design.md D2) instead
     * of sharing it with this package-private class — the test harness must not reach across the module's
     * internal package boundary for production regex state. Keep the two definitions textually identical if
     * either ever changes.
     */
    private static final Pattern SELF_CLOSED_ELEMENT =
            Pattern.compile("<([a-zA-Z][a-zA-Z0-9-]*)\\b((?:\"[^\"]*\"|'[^']*'|[^\"'>])*?)/>");

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
        final String decoded = new String(content, charset);
        final Document doc = Jsoup.parse(expandSelfClosedNonVoidElement(decoded), baseUri);
        clearSeenSelfCloseOnNonVoidElements(doc);
        doc.outputSettings().prettyPrint(false).charset(charset).syntax(Document.OutputSettings.Syntax.xml);
        return doc;
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
        final Matcher matcher = SELF_CLOSED_ELEMENT.matcher(xhtml);
        final StringBuilder expanded = new StringBuilder();
        while (matcher.find()) {
            final String tagName = matcher.group(1);
            final String replacement = isKnownNonVoidTag(tagName)
                    ? "<" + tagName + matcher.group(2) + "></" + tagName + ">"
                    : matcher.group();
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(expanded);
        return expanded.toString();
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
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return DEFAULT_CHARSET;
        }
    }
}
