package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.fixture.EpubFixtures;

/**
 * FR-DOC-01's scenarios: a self-closed raw-text element (design.md D1's measured set — {@code script}, {@code
 * style}, {@code noscript}) must not swallow the rest of the content document it appears in. {@code
 * FixtureCatalog} registers the same fixtures for round-trip fidelity (segment count, canonical comparison,
 * coverage floor); these tests prove the parse-time behaviour the requirement itself states, independently of
 * that sweep.
 */
class SelfClosedRawTextElementTest {

    @TempDir
    private Path tempDir;

    // WHEN a content document whose <head> contains <script src="js/book.js"/> and whose
    // <body> contains two <p> elements of prose is parsed, THEN both paragraphs become segments.
    @Test
    void read_selfClosedScriptInHead_bothParagraphsBecomeSegments() {
        final Path epub = EpubFixtures.headSelfClosedScript(tempDir.resolve("book.epub"));

        final Document document = read(epub);

        assertThat(sourceTextsOf(document)).containsExactly("Prose one.", "Prose two.");
    }

    // WHEN a content document whose <head> contains <style/> and whose <body> contains one
    // <p> of prose is parsed, THEN that paragraph becomes a segment.
    @Test
    void read_selfClosedStyleInHead_theParagraphBecomesASegment() {
        final Path epub = EpubFixtures.headSelfClosedStyle(tempDir.resolve("book.epub"));

        final Document document = read(epub);

        assertThat(sourceTextsOf(document)).containsExactly("Prose one.");
    }

    // WHEN a content document whose <head> contains <script src="js/book.js"></script> and
    // whose <body> contains two <p> elements is parsed, THEN both paragraphs become segments.
    @Test
    void read_pairedScriptInHead_bothParagraphsBecomeSegments() {
        final Path epub = EpubFixtures.headPairedScript(tempDir.resolve("book.epub"));

        final Document document = read(epub);

        assertThat(sourceTextsOf(document)).containsExactly("Prose one.", "Prose two.");
    }

    // WHEN a content document whose <body> contains one <p> of prose, then <script
    // src="js/book.js"/>, then a second <p> of prose is parsed, THEN both paragraphs become segments.
    @Test
    void read_selfClosedScriptInBody_bothSiblingParagraphsBecomeSegments() {
        final Path epub = EpubFixtures.bodySelfClosedScript(tempDir.resolve("book.epub"));

        final Document document = read(epub);

        assertThat(sourceTextsOf(document)).containsExactly("First paragraph.", "Second paragraph.");
    }

    // WHEN a content document whose <body> contains a <pre><code> listing whose text is the
    // literal <script src="x.js"/>, followed by one <p> of prose, is parsed and reassembled with zero segment
    // edits, THEN the listing's text in the output is still the literal <script src="x.js"/>, AND the paragraph
    // becomes a segment.
    @Test
    void write_scriptLiteralInCodeListing_listingTextSurvivesUnrewritten_andParagraphIsASegment() {
        final Path source = EpubFixtures.scriptLiteralInCodeListing(tempDir.resolve("book.epub"));
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(source);
        assertThat(sourceTextsOf(document)).containsExactly("Prose.");

        final Path output = new EpubWriter(registry).write(document, tempDir.resolve("out.epub"), "en");

        assertThat(chapterTextOf(output)).contains("&lt;script src=\"x.js\"/&gt;");
    }

    private static Document read(Path epub) {
        return new EpubReader(new OpenEpubRegistry()).read(epub);
    }

    private static List<String> sourceTextsOf(Document document) {
        final List<String> texts = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                texts.add(segment.sourceInner());
            }
        }
        return texts;
    }

    private static String chapterTextOf(Path zip) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                if (entry.getName().endsWith(".xhtml")) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = in.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new AssertionError("no .xhtml entry in " + zip);
    }
}
