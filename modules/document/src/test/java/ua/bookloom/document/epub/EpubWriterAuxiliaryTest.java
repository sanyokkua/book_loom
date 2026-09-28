package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/** {@code EpubWriter} writing a targeted auxiliary slot back and leaving an untargeted one as source. */
class EpubWriterAuxiliaryTest {

    @TempDir
    private Path tempDir;

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    // A translated book's shelf title is the package's dc:title: a targeted auxiliary title lands there.
    @Test
    void write_auxiliaryTitleTargeted_opfDeclaresTheTranslatedTitle() {
        final Path output = writeWithAuxiliaryTarget("aux:title", "Франкенштейн");

        assertThat(rawOpfTextOf(output)).contains("<dc:title>Франкенштейн</dc:title>");
    }

    @Test
    void write_pageTitleTargetedWithAmpersand_headHoldsTheTitleEscapedOnce() {
        final Path output = writeWithAuxiliaryTarget("aux:head-title:OEBPS/c01.xhtml", "Розділ 1 & 2");

        final String chapter = new String(contentOf(output, "OEBPS/c01.xhtml"), StandardCharsets.UTF_8);
        assertThat(chapter).contains("<title>Розділ 1 &amp; 2</title>").doesNotContain("&amp;amp;");
    }

    @Test
    void write_noAuxiliaryTarget_opfTitleAndPageTitleStayAsSource() {
        final Path output = writeEdited(document -> document);

        assertThat(rawOpfTextOf(output)).contains("<dc:title>Test Book</dc:title>");
        assertThat(rawOpfTextOf(output)).contains("<dc:creator>A. Author</dc:creator>");
        assertThat(new String(contentOf(output, "OEBPS/c01.xhtml"), StandardCharsets.UTF_8))
                .contains("<title>Chapter</title>");
    }

    private Path writeWithAuxiliaryTarget(String segmentId, String target) {
        return writeEdited(document -> withAuxiliaryTarget(document, segmentId, target));
    }

    private Path writeEdited(UnaryOperator<Document> edit) {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf())
                .entry("OEBPS/c01.xhtml", chapter())
                .writeTo(epub);
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);
        return new EpubWriter(registry).write(edit.apply(document), tempDir.resolve("out.epub"), "uk");
    }

    private static Document withAuxiliaryTarget(Document document, String segmentId, String target) {
        final List<Unit> units = new ArrayList<>();
        for (final Unit unit : document.units()) {
            units.add(unit.isAuxiliary() ? unit.withSegments(retargeted(unit, segmentId, target)) : unit);
        }
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                document.metadata(),
                units);
    }

    private static List<Segment> retargeted(Unit unit, String segmentId, String target) {
        return unit.segments().stream()
                .map(segment -> segment.id().equals(segmentId) ? withTarget(segment, target) : segment)
                .toList();
    }

    private static Segment withTarget(Segment segment, String targetInner) {
        return segment.withDecision(ua.bookloom.api.document.SegmentStatus.ACCEPTED, targetInner);
    }

    private static String rawOpfTextOf(Path zip) {
        return new String(contentOf(zip, "OEBPS/content.opf"), StandardCharsets.UTF_8);
    }

    private static byte[] contentOf(Path zip, String entryName) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            final ZipEntry entry = file.getEntry(entryName);
            return file.getInputStream(entry).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String opf() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:creator>A. Author</dc:creator>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c01"/>
                  </spine>
                </package>
                """;
    }

    private static String chapter() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body><p>Paragraph 0.</p></body>
                </html>
                """;
    }
}
