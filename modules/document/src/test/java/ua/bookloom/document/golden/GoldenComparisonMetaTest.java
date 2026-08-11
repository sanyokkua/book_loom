package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.document.epub.EpubZipBuilder;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * Meta-tests on the comparisons themselves: they must be able to <strong>fail</strong>.
 *
 * <p>A comparison that cannot fail is worse than no comparison, because it reports green over any output
 * whatsoever — and both of these are easy to write in exactly that form. The Markdown one especially: syntax-tree
 * nodes inherit identity equality, so the obvious spelling compares two references and passes only when they are
 * the same object.
 *
 * <p>Each comparison is therefore driven twice — once over a difference it must absorb, and once over a difference
 * it must catch.
 */
class GoldenComparisonMetaTest {

    @TempDir
    private Path tempDir;

    private static final String MARKDOWN_WITH_REFERENCE = """
            # Chapter

            Prose with a [ref][r] in it.

            [r]: https://example.com
            """;

    /**
     * A content document written the way the twelve corpus books that motivate design.md D1 write it: a
     * self-closed {@code <script/>} in the head, XML-legal but unrepresentable in HTML.
     */
    private static final String SELF_CLOSED_SCRIPT_CONTENT_DOCUMENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><script src="js/book.js"/></head>
            <body>
            <p>First paragraph.</p>
            <p>Second paragraph.</p>
            </body>
            </html>
            """;

    /**
     * An FB2 whose declaration quoting, ampersand spelling and element count are all under the caller's control —
     * the three axes these meta-tests need to vary independently.
     */
    private Path writeFb2(String name, String quote, String ampersand, String extraElement) {
        return write(name, """
                <?xml version=%1$s1.0%1$s encoding=%1$sutf-8%1$s?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><lang>en</lang></title-info></description>
                  <body><section><p>Fish %2$s chips.</p>%3$s</section></body>
                </FictionBook>
                """.formatted(quote, ampersand, extraElement));
    }

    private Path write(String name, String content) {
        final Path file = tempDir.resolve(name);
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /** A minimal EPUB — {@code mimetype} (forced {@link ZipEntry#STORED}, since {@link
     * EpubCanonicalAssert#assertCanonicalEqual} requires the compared archive's first entry to be stored) plus
     * one XHTML content document — built directly rather than through {@code DocumentPort}, since these two
     * cases exist to drive the comparator itself. */
    private Path epubWithContentDocument(String name, String contentDocument) {
        return new EpubZipBuilder()
                .entry("mimetype", "application/epub+zip", ZipEntry.STORED)
                .entry("OEBPS/chapter.xhtml", contentDocument)
                .writeTo(tempDir.resolve(name));
    }

    // Covers: FR-DOC-09 — WHEN a reassembled FB2 differs from its source only in attribute quoting or the spelling
    // of a character reference, THEN the golden comparison still passes.
    @Test
    void fb2Comparison_quotingAndEntitySpellingOnly_isAbsorbed() {
        final Path source = writeFb2("source.fb2", "'", "&amp;", "");
        final Path reserialized = writeFb2("output.fb2", "\"", "&#38;", "");

        assertThatCode(() -> Fb2CanonicalAssert.assertCanonicalEqual(source, reserialized, "en"))
                .doesNotThrowAnyException();
    }

    // Covers: FR-DOC-09 — the FB2 comparison catches a lost element, so its tolerance of quoting is a deliberate
    // narrowing rather than an inability to fail.
    @Test
    void fb2Comparison_lostElement_isCaught() {
        final Path source = writeFb2("source.fb2", "\"", "&amp;", "<p>Second paragraph.</p>");
        final Path damaged = writeFb2("damaged.fb2", "\"", "&amp;", "");

        assertThatThrownBy(() -> Fb2CanonicalAssert.assertCanonicalEqual(source, damaged, "en"))
                .isInstanceOf(AssertionError.class);
    }

    // Covers: FR-DOC-09 — the FB2 comparison catches a rewritten binary payload, which a canonical writer free to
    // re-wrap long text could otherwise change without the structural comparison noticing.
    @Test
    void fb2Comparison_reWrappedBinaryPayload_isCaught() {
        final String withBinary = """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <body><section><p>Prose.</p></section></body>
                  <binary id="c.jpg" content-type="image/jpeg">%s</binary>
                </FictionBook>
                """;
        final Path source = write("source.fb2", withBinary.formatted("AAAABBBBCCCC"));
        final Path rewrapped = write("rewrapped.fb2", withBinary.formatted("AAAABBBB\n  CCCC"));

        assertThatThrownBy(() -> Fb2CanonicalAssert.assertCanonicalEqual(source, rewrapped, "en"))
                .isInstanceOf(AssertionError.class);
    }

    /**
     * A source whose {@code <title-info>} declares no {@code <lang>} at all — the shape {@link
     * ua.bookloom.document.fb2.Fb2Writer#write} always adds one for, and {@link Fb2Fixtures#PRIMARY_XML} never
     * exercises because it declares both {@code <lang>} and {@code <src-lang>}.
     */
    private Path writeFb2WithNoLanguage(String name, String... paragraphs) {
        final StringBuilder body = new StringBuilder();
        for (final String paragraph : paragraphs) {
            body.append("<p>").append(paragraph).append("</p>");
        }
        return write(name, """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><genre>prose</genre><book-title>Sample</book-title></title-info></description>
                  <body><section>%s</section></body>
                </FictionBook>
                """.formatted(body));
    }

    // Covers: FR-DOC-FB2-7 — WHERE the source's title information declares no language element, THEN the golden
    // comparison treats the single added target-language element as expected rather than as a difference.
    @Test
    void fb2Comparison_addedLanguageOnly_isAbsorbedWhenSourceDeclaredNone() {
        final Path source = writeFb2WithNoLanguage("no-lang-source.fb2", "First paragraph.", "Second paragraph.");
        final Path output = write("no-lang-output.fb2", """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><genre>prose</genre><book-title>Sample</book-title>\
                <lang>uk</lang></title-info></description>
                  <body><section><p>First paragraph.</p><p>Second paragraph.</p></section></body>
                </FictionBook>
                """);

        assertThatCode(() -> Fb2CanonicalAssert.assertCanonicalEqual(source, output, "uk"))
                .doesNotThrowAnyException();
    }

    // Covers: FR-DOC-FB2-7 — WHEN a fixture FB2 book whose <title-info> contains no <lang> element is reassembled
    // with no segment receiving target text, and the output both gains <lang>uk</lang> and drops one <p> element
    // present in the source, THEN the golden test fails.
    @Test
    void fb2Comparison_addedLanguageButLostParagraph_isCaught() {
        final Path source = writeFb2WithNoLanguage("lossy-source.fb2", "First paragraph.", "Second paragraph.");
        final Path damaged = write("lossy-output.fb2", """
                <?xml version="1.0" encoding="utf-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><genre>prose</genre><book-title>Sample</book-title>\
                <lang>uk</lang></title-info></description>
                  <body><section><p>First paragraph.</p></section></body>
                </FictionBook>
                """);

        assertThatThrownBy(() -> Fb2CanonicalAssert.assertCanonicalEqual(source, damaged, "uk"))
                .isInstanceOf(AssertionError.class);
    }

    // Covers: FR-DOC-09 — WHEN a reassembled Markdown drops a reference-link definition present in the source,
    // THEN the golden comparison fails.
    @Test
    void markdownComparison_lostReferenceLinkDefinition_isCaught() {
        final String damaged = MARKDOWN_WITH_REFERENCE.replace("[r]: https://example.com\n", "");

        assertThat(MarkdownAstAssert.structureOf(damaged))
                .isNotEqualTo(MarkdownAstAssert.structureOf(MARKDOWN_WITH_REFERENCE));
    }

    // Covers: FR-DOC-09 — the Markdown comparison distinguishes trees by structure rather than by object identity,
    // so it can fail at all; an equality check on two syntax trees never would.
    @Test
    void markdownComparison_changedTableAlignment_isCaught() {
        final String leftAligned = "| A | B |\n|:--|:--|\n| 1 | 2 |\n";
        final String rightAligned = "| A | B |\n|--:|--:|\n| 1 | 2 |\n";

        assertThat(MarkdownAstAssert.structureOf(rightAligned))
                .isNotEqualTo(MarkdownAstAssert.structureOf(leftAligned));
    }

    // Covers: FR-DOC-09 — a fenced block's info string is part of the compared structure, so losing it fails.
    @Test
    void markdownComparison_lostFenceInfoString_isCaught() {
        assertThat(MarkdownAstAssert.structureOf("```\nint x = 1;\n```\n"))
                .isNotEqualTo(MarkdownAstAssert.structureOf("```java\nint x = 1;\n```\n"));
    }

    // Covers: FR-DOC-09 — two identical documents do compare equal, so the assertions above are not passing
    // merely because the serialization is unstable.
    @Test
    void markdownComparison_identicalDocuments_compareEqual() {
        assertThat(MarkdownAstAssert.structureOf(MARKDOWN_WITH_REFERENCE))
                .isEqualTo(MarkdownAstAssert.structureOf(MARKDOWN_WITH_REFERENCE));
    }

    // Covers: FR-DOC-01 — WHEN a source content document holding <script src="js/book.js"/> is reassembled with
    // zero segment edits and the output holds <script src="js/book.js"></script>, THEN the EPUB golden round-trip
    // comparison passes, because both sides are normalized identically before comparison.
    @Test
    void epubComparison_selfClosedScriptExpandedToPairedForm_isAbsorbed() {
        final Path source = epubWithContentDocument("source.epub", SELF_CLOSED_SCRIPT_CONTENT_DOCUMENT);
        final Path output = epubWithContentDocument("output.epub", """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><script src="js/book.js"></script></head>
                <body>
                <p>First paragraph.</p>
                <p>Second paragraph.</p>
                </body>
                </html>
                """);

        assertThatCode(() -> EpubCanonicalAssert.assertCanonicalEqual(source, output))
                .doesNotThrowAnyException();
    }

    // Covers: FR-DOC-01 — WHEN a source content document holding <script src="js/book.js"/> and two <p> elements
    // is compared against an output holding <script src="js/book.js"></script> and only one of those <p>
    // elements, THEN the EPUB golden round-trip comparison fails.
    @Test
    void epubComparison_selfClosedScriptExpandedButParagraphLost_isCaught() {
        final Path source = epubWithContentDocument("lossy-source.epub", SELF_CLOSED_SCRIPT_CONTENT_DOCUMENT);
        final Path damaged = epubWithContentDocument("lossy-output.epub", """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><script src="js/book.js"></script></head>
                <body>
                <p>First paragraph.</p>
                </body>
                </html>
                """);

        assertThatThrownBy(() -> EpubCanonicalAssert.assertCanonicalEqual(source, damaged))
                .isInstanceOf(AssertionError.class);
    }
}
