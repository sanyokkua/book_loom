package ua.bookloom.document.fixture;

import java.nio.file.Path;
import java.util.zip.ZipEntry;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.epub.EpubZipBuilder;

/**
 * The EPUB fixture family, each reproducing a shape the 194-book survey proved exists — and, in two cases, a
 * shape it proved has no natural example at all.
 *
 * <p>Written in Java rather than committed for the reason design.md D8 gives: a builder is reviewable where a
 * committed {@code .epub} is a binary blob nobody reads, and a diff shows which trap the fixture sets.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EpubFixtures {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    /**
     * The 42-book zero-segment case: every paragraph is a {@code <div class="paragraph">} and the book contains
     * no {@code <p>} at all. A tag whitelist covering {@code p}/{@code h1}–{@code h6}/{@code li} imports this
     * book completely empty, and every fidelity assertion still passes.
     */
    public static Path divParagraphs(Path destination) {
        return book(destination, """
                <h1>Chapter One</h1>
                <div class="paragraph">Prose one.</div>
                <div class="paragraph">Prose two.</div>
                <div class="paragraph">Prose three.</div>
                """);
    }

    /**
     * Defeats the obvious hardening. One surveyed book carries 1,393 {@code <p>} elements of which every single
     * one is an empty {@code <br/>} spacer, with all the prose in {@code div}s — so a sanity check of "does this
     * book have paragraphs?" passes it while the importer still finds nothing.
     */
    public static Path spacerParagraphs(Path destination) {
        return book(destination, """
                <p><br/></p>
                <div class="paragraph">Prose one.</div>
                <p><br/></p>
                <div class="paragraph">Prose two.</div>
                <p><br/></p>
                <div class="paragraph">Prose three.</div>
                """);
    }

    /**
     * Prose delimited by line breaks rather than by block elements. One surveyed book carries 465,500 characters
     * inside four elements separated by 9,290 {@code <br/>}; treating such a block as one segment produces a
     * segment larger than any model context.
     */
    public static Path brRuns(Path destination) {
        return book(destination, """
                <div>Run one<br/>Run two<br/>Run three</div>
                <p>An ordinary paragraph.</p>
                """);
    }

    /**
     * The inline-markup write-back case, which <strong>no fixture in the repository contained</strong> before
     * this change — which is why the defect shipped. One surveyed book wraps 7,159 of its 7,160 paragraphs
     * exactly like this.
     */
    public static Path inlineMarkup(Path destination) {
        return book(destination, """
                <p><span><i>Wrapped prose one.</i></span></p>
                <p><span><i>Wrapped prose two.</i></span></p>
                <p>Plain prose three.</p>
                """);
    }

    /**
     * One paragraph whose content is {@code <b>bold</b> text} — the exact shape the coverage requirement's
     * "inline markup is stripped for every format, not only some" scenario names.
     */
    public static Path boldInline(Path destination) {
        return book(destination, "<p><b>bold</b> text</p>\n");
    }

    /**
     * Deliberately STORED entries alongside DEFLATED ones. 26 surveyed books store 2,578 already-compressed
     * entries — one stores 1,950 — and the shipped writer re-DEFLATEd every one of them.
     */
    public static Path storedEntries(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(false))
                .entry("OEBPS/c01.xhtml", chapter("<p>Prose one.</p>\n<p>Prose two.</p>"))
                .entry("OEBPS/images/cover.jpg", "pretend-jpeg-bytes", ZipEntry.STORED)
                .entry("OEBPS/styles.css", "body { font-family: serif; }\n", ZipEntry.DEFLATED)
                .writeTo(destination);
    }

    /**
     * Font obfuscation under the Adobe algorithm spelled as real books spell it — without the trailing {@code 4}.
     * An exact-match algorithm allowlist refused 16 of 194 surveyed books over exactly that character, every one
     * of them a normal book (ADR-0026).
     */
    public static Path fontObfuscated(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("META-INF/encryption.xml", encryptionOf("OEBPS/fonts/serif.otf"))
                .entry("OEBPS/content.opf", opf(true))
                .entry("OEBPS/c01.xhtml", chapter("<p>Prose one.</p>\n<p>Prose two.</p>"))
                .binaryEntry("OEBPS/fonts/serif.otf", new byte[] {1, 2, 3, 4})
                .writeTo(destination);
    }

    /**
     * An {@code encryption.xml} naming a content document. The refusal path has <strong>no natural example</strong>
     * — a 194-book survey found zero genuinely protected books — so it is provable only here. The algorithm it
     * declares is the IDPF font-obfuscation URI, which is what makes this fixture also prove the hole an
     * algorithm-only check left open.
     */
    public static Path nonFontEncrypted(Path destination) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("META-INF/encryption.xml", encryptionOf("OEBPS/c01.xhtml"))
                .entry("OEBPS/content.opf", opf(true))
                .entry("OEBPS/c01.xhtml", chapter("<p>Prose one.</p>"))
                .writeTo(destination);
    }

    /**
     * The combination fixture. Every fixture above isolates one pathology, and each of those pathologies passed
     * in isolation while real books were corrupted — the survey's finding is that the failures are
     * <em>interactions</em>. Line-break splitting is unreachable until structural recognition lands (13,281 of
     * the corpus's 24,347 {@code <br/>} sit inside exactly these {@code div} wrappers), and write-back only
     * corrupts once a run and inline markup coincide.
     */
    public static Path combination(Path destination) {
        return book(destination, """
                <div class="paragraph">Plain prose one.</div>
                <div class="paragraph"><span><i>Wrapped prose two.</i></span></div>
                <div class="paragraph">Run A<br/>Run B<br/>Run C</div>
                <p><img src="fig1.png"/></p>
                <pre><code>int x = 1;</code></pre>
                <div class="paragraph">Plain prose four.</div>
                """);
    }

    /**
     * A self-closed {@code <script/>} in the head — XML-legal, unrepresentable in HTML raw text (design.md D1).
     * Twelve of 194 surveyed books do exactly this, all from one converter; without the pre-parse repair jsoup's
     * HTML tree builder swallows the rest of the document, including {@code <body>}, as script text.
     */
    public static Path headSelfClosedScript(Path destination) {
        return book(destination, "<script src=\"js/book.js\"/>", """
                <p>Prose one.</p>
                <p>Prose two.</p>
                """);
    }

    /** The same defect, {@code <style/>} rather than {@code <script/>} — design.md D1's measured repair set. */
    public static Path headSelfClosedStyle(Path destination) {
        return book(destination, "<style/>", """
                <p>Prose one.</p>
                """);
    }

    /** The same defect, {@code <noscript/>} rather than {@code <script/>} — design.md D1's measured repair set. */
    public static Path headSelfClosedNoscript(Path destination) {
        return book(destination, "<noscript/>", """
                <p>Prose one.</p>
                """);
    }

    /**
     * The already-paired form, {@code <script src="js/book.js"></script>} — proves the repair leaves a document
     * that never needed it byte-for-byte unaffected (design.md D1's risk entry).
     */
    public static Path headPairedScript(Path destination) {
        return book(destination, "<script src=\"js/book.js\"></script>", """
                <p>Prose one.</p>
                <p>Prose two.</p>
                """);
    }

    /**
     * A self-closed {@code <script/>} between two body paragraphs, rather than in {@code <head>} — proves the
     * repair works wherever the element sits, not only in the head.
     */
    public static Path bodySelfClosedScript(Path destination) {
        return book(destination, """
                <p>First paragraph.</p>
                <script src="js/book.js"/>
                <p>Second paragraph.</p>
                """);
    }

    /**
     * A {@code <pre><code>} listing whose text is the literal {@code <script src="x.js"/>} — proves the repair
     * does not overreach. Well-formed XHTML can only carry that literal text with its angle brackets entity-
     * escaped in the markup; that escaping is exactly why the byte-level regex in {@code XhtmlParser} never sees
     * a literal {@code <script} to match against, so the listing survives untouched while the trailing paragraph
     * still segments.
     */
    public static Path scriptLiteralInCodeListing(Path destination) {
        return book(destination, """
                <pre><code>&lt;script src="x.js"/&gt;</code></pre>
                <p>Prose.</p>
                """);
    }

    /**
     * A self-closed known non-void element wrapping a block-level child (design.md D2a). {@code <div>} cannot
     * nest inside {@code <p>} under the HTML content model, so this shape un-nests on the very first parse —
     * before this change's self-close repair is even reached — and the comparator cannot see whether writing the
     * un-nested tree changes anything further, since it re-parses source and output through the same un-nesting
     * rule alike (D2a's structural blind spot). Only the fixed-point write-twice check can prove this shape stays
     * stable rather than gaining a further phantom sibling on a second write.
     */
    public static Path paragraphWrappingDivision(Path destination) {
        return book(destination, """
                <p class="p1"><div class="image"><img src="i.jpg"/></div></p>
                """);
    }

    /**
     * A self-closed indexterm anchor immediately before a section boundary (design.md D2b, F12a measured in
     * docs/adr/ADR-0028-xhtml-self-closed-raw-text-repair.md). Without the pre-parse repair the anchor stays open, absorbing the following section's
     * {@code <h1>} as its own child and duplicating its {@code id} onto that heading's containing element.
     */
    public static Path selfClosedIndextermAnchor(Path destination) {
        return book(destination, """
                <section><div><p>text <a id="idm001" data-type="indexterm"/></p></div></section>
                <section><div><h1>Next Chapter</h1></div></section>
                """);
    }

    /**
     * A self-closed inline {@code <span>} between two words, followed by a second paragraph (design.md D2b,
     * F12a). Proves the repair reaches an ordinary inline element, not only the anchor shape above, and that the
     * paragraph following it is not absorbed as the span's content.
     */
    public static Path selfClosedInlineSpan(Path destination) {
        return book(destination, """
                <p>Before <span class="hl" id="s1"/> after</p>
                <p>Next paragraph.</p>
                """);
    }

    /**
     * The lossy shape (design.md D7, task 6.2; measured in {@code docs/implementation_plan/notes-corpus-verification.md}, finding F2): a
     * {@code <pre>} whose content begins with <strong>two</strong> line breaks, reproducing the poem
     * {@code Alices Adventures in Wonderland.epub}'s {@code chapter06.xhtml} carries verbatim. A fixture with
     * only one leading break would pass whether or not the restore exists — both sides of any comparison
     * discard a single leading break alike — so the second break is what actually proves the fix.
     */
    // Deliberately not a text block: Java's incidental-whitespace stripping would collapse the two blank lines
    // this fixture's whole point is to preserve exactly. A single literal keeps every byte exact and reviewable.
    @SuppressWarnings("StringConcatToTextBlock")
    public static Path preTwoLeadingLineFeeds(Path destination) {
        final String bodyContent = "<pre class=\"poem\">\n\n        \u201cSpeak roughly to your little boy,</pre>\n"
                + "<p>Prose after the poem.</p>\n";
        return book(destination, bodyContent);
    }

    /** A {@code <pre>} with no leading line break at all — gains none (spec scenario). */
    public static Path preNoLeadingLineFeed(Path destination) {
        return book(destination, """
                <pre>code here</pre>
                <p>Prose.</p>
                """);
    }

    /** A {@code <pre>} beginning with two carriage-return-line-feed pairs — unaffected (spec scenario). */
    public static Path preTwoLeadingCrLfPairs(Path destination) {
        return book(destination, "<pre>\r\n\r\nX</pre>\n<p>Prose.</p>\n");
    }

    /** An empty {@code <pre></pre>} — must reassemble without failing (spec scenario). */
    public static Path preEmpty(Path destination) {
        return book(destination, """
                <pre></pre>
                <p>Prose.</p>
                """);
    }

    /** A {@code <pre>} with exactly one leading line break — discarded identically on both sides (spec scenario). */
    public static Path preOneLeadingLineFeed(Path destination) {
        return book(destination, """
                <pre>
                code here</pre>
                <p>Prose.</p>
                """);
    }

    private static Path book(Path destination, String bodyContent) {
        return book(destination, "", bodyContent);
    }

    private static Path book(Path destination, String extraHeadContent, String bodyContent) {
        return new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(false))
                .entry("OEBPS/c01.xhtml", chapter(extraHeadContent, bodyContent))
                .writeTo(destination);
    }

    private static String chapter(String bodyContent) {
        return chapter("", bodyContent);
    }

    private static String chapter(String extraHeadContent, String bodyContent) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter One</title>%s</head>
                <body>
                %s</body>
                </html>
                """.formatted(extraHeadContent, bodyContent);
    }

    private static String opf(boolean withFont) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Fixture</dc:title>
                    <dc:creator>Fixture Author</dc:creator>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                    <item id="css" href="styles.css" media-type="text/css"/>
                    <item id="img" href="images/cover.jpg" media-type="image/jpeg"/>
                %s  </manifest>
                  <spine><itemref idref="c01"/></spine>
                </package>
                """.formatted(
                        withFont
                                ? "    <item id=\"font\" href=\"fonts/serif.otf\" "
                                        + "media-type=\"application/x-font-otf\"/>\n"
                                : "");
    }

    /** Names what was encrypted; ADR-0026 decides from that rather than from the algorithm. */
    private static String encryptionOf(String cipherUri) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                            xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                  <enc:EncryptedData>
                    <enc:EncryptionMethod Algorithm="http://ns.adobe.com/pdf/enc#RC"/>
                    <enc:CipherData><enc:CipherReference URI="%s"/></enc:CipherData>
                  </enc:EncryptedData>
                </encryption>
                """.formatted(cipherUri);
    }
}
