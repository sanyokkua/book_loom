package ua.bookloom.document.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Parses an XHTML body fragment exactly the way production's {@code XhtmlParser} does, rather than the bare
 * {@link Jsoup#parse(String)}/{@link Jsoup#parseBodyFragment(String)} every other test in this package uses.
 *
 * <p><strong>Why this matters and must not be skipped.</strong> Production's {@code XhtmlParser.parse} pins
 * {@code prettyPrint(false)} and XML output syntax ({@code
 * modules/document/src/main/java/ua/bookloom/document/epub/XhtmlParser.java:78}) before any node is ever
 * serialized — but {@code XhtmlParser} is package-private in {@code ua.bookloom.document.epub} and unreachable
 * from this package. Measured on this repo's pinned jsoup 1.23.1, a bare {@code
 * Jsoup.parseBodyFragment("<p>See <svg><text>Fig 1</text></svg> above.</p>")} yields {@code
 * <svg> <text>Fig 1</text></svg>} for {@code outerHtml()} — jsoup's pretty-printer inserts whitespace into the
 * serialized markup whenever it does not recognize the enclosing tag's content model. Every existing test in this
 * package gets away with a bare parse only because none of them asserts serialized markup; the masking tests that
 * assert composed opening/closing tags must go through this helper instead, or they measure jsoup's
 * pretty-printer rather than the adapter under test.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class XhtmlTrees {

    /**
     * Parses {@code bodyHtml} as an XHTML body fragment with production's output settings applied.
     *
     * @param bodyHtml the fragment's inner body markup
     * @return the parsed {@code <body>} element, with {@code prettyPrint(false)} and XML output syntax already
     *     pinned so a caller's {@code outerHtml()} matches what production would serialize; never null
     */
    static Element body(String bodyHtml) {
        final Document doc = Jsoup.parseBodyFragment(bodyHtml);
        doc.outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.xml);
        return doc.body();
    }
}
