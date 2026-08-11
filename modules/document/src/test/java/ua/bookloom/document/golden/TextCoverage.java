package ua.bookloom.document.golden;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/**
 * Measures what proportion of a fixture's visible text the emitted segments actually cover.
 *
 * <p><strong>Why this assertion exists at all.</strong> Every other document assertion in the suite checks that
 * nothing was <em>damaged</em> — and a book that yields no segments satisfies every one of them perfectly. The
 * skeleton round-trips, the bytes match, the canonical forms are equal, the gate is green, and the product does
 * nothing. A survey of 194 real books found 42 that would have imported empty with no test noticing. This is the
 * only assertion in the suite that checks something <em>happened</em>.
 *
 * <p><strong>How it stays independent of the walker.</strong> The denominator is read straight from the source
 * file — all character data outside deliberately excluded blocks — by machinery that shares no code with
 * segmentation. The numerator is the segments' own text with markup stripped. If the measurement asked the walker
 * what it had covered, it would agree with the walker whatever the walker did, which is the failure that let a
 * 73.74%-coverage implementation look correct.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TextCoverage {

    /**
     * The proportion of {@code source}'s translatable visible text covered by {@code document}'s segments.
     *
     * @param source the fixture file as written
     * @param format the format it was parsed as
     * @param document the parsed document
     * @return a value in {@code [0,1]}; {@code 1} when the source carries no translatable text at all, since a
     *     document with nothing to translate is fully covered by producing nothing
     */
    static double of(Path source, BookFormat format, Document document) {
        final List<String> translatable = words(translatableTextOf(source, format, document.charset()));
        if (translatable.isEmpty()) {
            return 1.0;
        }
        return (double) reachedCount(translatable, words(segmentTextOf(document, format))) / translatable.size();
    }

    /**
     * How many of the source's words the segments actually reached, counted as a multiset so a word occurring
     * three times in the source needs three occurrences among the segments.
     *
     * <p>Words rather than characters, because a ratio of lengths would pass on entirely <em>different</em> text
     * of the same length — which is not a measure of coverage at all.
     */
    private static int reachedCount(List<String> translatable, List<String> covered) {
        final java.util.Map<String, Integer> available = new java.util.HashMap<>();
        for (final String word : covered) {
            available.merge(word, 1, Integer::sum);
        }
        int reached = 0;
        for (final String word : translatable) {
            final Integer remaining = available.get(word);
            if (remaining != null && remaining > 0) {
                available.put(word, remaining - 1);
                reached++;
            }
        }
        return reached;
    }

    private static final java.util.regex.Pattern WORD_SEPARATOR = java.util.regex.Pattern.compile(" ");

    private static List<String> words(String text) {
        final List<String> words = new java.util.ArrayList<>();
        WORD_SEPARATOR.splitAsStream(normalize(text)).filter(w -> !w.isEmpty()).forEach(words::add);
        return words;
    }

    /** Every segment's visible text, with any inline markup stripped so the two sides are comparable. */
    private static String segmentTextOf(Document document, BookFormat format) {
        final StringBuilder text = new StringBuilder();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                text.append(visibleTextOf(segment.sourceInner(), format)).append(' ');
            }
        }
        return text.toString();
    }

    /**
     * Strips inline markup so both sides of the ratio are plain text. FB2 fragments go through the XML parser
     * rather than the HTML one: an HTML parse silently discards a CDATA section, which would report a paragraph
     * the walker did reach as text it missed. Markdown goes through {@link #markdownPlainText(String)}, the same
     * stripping the denominator uses, rather than a second, independently invented rule — a segment carrying
     * {@code `code`} must match the denominator's clean {@code code}. TXT segments already are plain text: the
     * reader emits raw paragraph bytes with nothing to strip.
     */
    private static String visibleTextOf(String sourceInner, BookFormat format) {
        return switch (format) {
            case EPUB -> Jsoup.parse(sourceInner).text();
            case FB2 ->
                Jsoup.parse("<fragment>" + sourceInner + "</fragment>", "", org.jsoup.parser.Parser.xmlParser())
                        .text();
            case MARKDOWN -> markdownPlainText(sourceInner);
            case TXT -> sourceInner;
        };
    }

    /**
     * The source's translatable text, decoded and stripped per format.
     *
     * @param charset the charset {@link Document#charset()} resolved for this document, or {@code null} for a
     *     container format that records none; only the TXT branch consults it — every other format resolves its
     *     own encoding independently (EPUB per content document, FB2 from its declaration)
     */
    private static String translatableTextOf(Path source, BookFormat format, @Nullable String charset) {
        return switch (format) {
            case EPUB -> epubText(source);
            case FB2 -> fb2Text(source);
            case MARKDOWN -> markdownText(source);
            case TXT -> readText(source, charset == null ? StandardCharsets.UTF_8 : Charset.forName(charset));
        };
    }

    /**
     * Every spine document's body text with {@code <pre>} and block {@code <math>} removed — the exclusions
     * DD-49 names, and the only text a correct importer is allowed not to reach.
     */
    private static String epubText(Path source) {
        final StringBuilder text = new StringBuilder();
        for (final ZipText entry : ZipText.contentDocumentsOf(source)) {
            final org.jsoup.nodes.Document parsed = Jsoup.parse(entry.text());
            parsed.select("pre, math").remove();
            text.append(parsed.body().text()).append(' ');
        }
        return text.toString();
    }

    /**
     * The FB2 bodies' text. {@code <description>} is excluded because its title and author are metadata rather
     * than body prose — they become segments only in a later change — and {@code <binary>} because a base64
     * cover image is not text anyone translates.
     */
    private static String fb2Text(Path source) {
        final String xml = fb2Xml(source);
        final org.jsoup.nodes.Document parsed = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser());
        parsed.select("description, binary").remove();
        final StringBuilder text = new StringBuilder();
        for (final org.jsoup.nodes.Element body : parsed.select("body")) {
            text.append(xmlText(body)).append(' ');
        }
        return text.toString();
    }

    private static String fb2Xml(Path source) {
        if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return ZipText.firstMemberEndingWith(source, ".fb2");
        }
        return readText(source, Charset.forName(declaredEncodingOf(source)));
    }

    private static String declaredEncodingOf(Path source) {
        final String prolog = readText(source, StandardCharsets.ISO_8859_1);
        final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']")
                .matcher(prolog);
        return matcher.find() ? matcher.group(1) : "UTF-8";
    }

    /**
     * The Markdown body's text with every excluded construct removed — frontmatter, code blocks, raw HTML and
     * link-reference definitions. Inline code is counted as text, because an inline {@code Code} span sits inside
     * a paragraph that is one segment and its characters are part of that segment's extent.
     */
    private static String markdownText(Path source) {
        final String whole = readText(source, StandardCharsets.UTF_8);
        final String body = whole.startsWith("---\n") ? whole.substring(afterFrontmatter(whole)) : whole;
        return markdownPlainText(body);
    }

    private static int afterFrontmatter(String whole) {
        final int closing = whole.indexOf("\n---", 3);
        return closing < 0 ? 0 : Math.min(whole.length(), whole.indexOf('\n', closing + 1) + 1);
    }

    /**
     * Parses {@code markdown} as a standalone CommonMark document and collects its {@link Text} and inline
     * {@link Code} literals — the one stripping rule both the denominator ({@link #markdownText(Path)}) and the
     * numerator ({@link #visibleTextOf}) use, so a segment's raw {@code **bold**} and the source's clean {@code
     * bold} are measured on the same terms.
     */
    private static String markdownPlainText(String markdown) {
        final Node parsed = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build()
                .parse(markdown);
        final StringBuilder text = new StringBuilder();
        appendMarkdownText(parsed, text);
        return text.toString();
    }

    private static void appendMarkdownText(Node parent, StringBuilder text) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            appendMarkdownNode(child, text);
        }
    }

    private static void appendMarkdownNode(Node node, StringBuilder text) {
        if (node instanceof FencedCodeBlock
                || node instanceof IndentedCodeBlock
                || node instanceof HtmlBlock
                || node instanceof LinkReferenceDefinition) {
            return;
        }
        if (node instanceof Text literal) {
            text.append(literal.getLiteral()).append(' ');
        } else if (node instanceof Code inlineCode) {
            text.append(inlineCode.getLiteral()).append(' ');
        }
        appendMarkdownText(node, text);
    }

    /**
     * Visible text from an XML tree, with a separator between sibling elements.
     *
     * <p>jsoup's own {@code text()} is not usable here: in XML mode it has no notion of block elements, so
     * {@code <v>Рядок перший</v><v>Рядок другий</v>} comes back as {@code першийРядок} welded into one word — and
     * a word-based coverage measure would then report text the walker <em>did</em> reach as text it missed.
     */
    private static String xmlText(org.jsoup.nodes.Element root) {
        final StringBuilder text = new StringBuilder();
        appendXmlText(root, text);
        return text.toString();
    }

    private static void appendXmlText(org.jsoup.nodes.Node node, StringBuilder text) {
        for (final org.jsoup.nodes.Node child : node.childNodes()) {
            appendXmlChild(child, text);
        }
    }

    private static void appendXmlChild(org.jsoup.nodes.Node child, StringBuilder text) {
        if (child instanceof org.jsoup.nodes.TextNode textNode) {
            text.append(textNode.getWholeText());
            return;
        }
        appendXmlText(child, text);
        text.append(' ');
    }

    private static String readText(Path source, Charset charset) {
        try {
            return new String(Files.readAllBytes(source), charset);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * Whitespace-collapsed and lower-cased, so the comparison is about how much text was reached rather than
     * about how a serializer spelled the space between two words.
     */
    private static String normalize(String text) {
        // A byte-order mark is a fact about the file's encoding, not a character of its prose. Left in, it welds
        // itself to the first word and reports a paragraph that was reached as one that was missed.
        return text.replace("\uFEFF", "").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
}
