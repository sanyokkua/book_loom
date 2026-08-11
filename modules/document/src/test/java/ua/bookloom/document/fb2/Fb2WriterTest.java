package ua.bookloom.document.fb2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * {@code Fb2Writer}'s write path: run-scoped write-back, the encoding decision taken over the real output, the
 * target-language update, and re-emitting the container the book arrived in.
 */
class Fb2WriterTest {

    @TempDir
    private Path tempDir;

    private final OpenFb2Registry registry = new OpenFb2Registry();

    private Document openPrimary() {
        final Path file =
                Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), Fb2Fixtures.PRIMARY_XML, Fb2Fixtures.WINDOWS_1251);
        return new Fb2Reader(registry).read(file);
    }

    private Path writeOut(Document document, String targetLanguage) {
        return new Fb2Writer(registry).write(document, tempDir.resolve("out.fb2"), targetLanguage);
    }

    private static byte[] bytesOf(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Covers: FR-DOC-FB2-3 — WHEN every character of the output is representable in the encoding the source
    // declared, THEN the output is written in that encoding and declares it.
    @Test
    void write_representableTranslation_keepsTheDeclaredEncoding() {
        final Document document = withTarget(openPrimary(), 0, 0, "Еней був козак.");

        final Path output = writeOut(document, "uk");

        final byte[] bytes = bytesOf(output);
        final String asDeclared = new String(bytes, Fb2Fixtures.WINDOWS_1251);
        assertThat(asDeclared).contains("encoding=\"windows-1251\"");
        assertThat(asDeclared).contains("Еней був козак.");
    }

    /**
     * The character that forces the switch is an em-dash or a curly quote nobody predicted, not the alphabet —
     * which is why the decision is taken over the real serialized output rather than guessed from the target
     * language.
     */
    // Covers: EC-FB2-1 — IF one segment's target text contains a character the declared encoding cannot
    // represent, THEN the whole document is written in UTF-8 with a rewritten declaration.
    @Test
    void write_oneUnrepresentableCharacter_switchesTheWholeDocumentToUtf8() {
        final Document document =
                withTarget(openPrimary(), 0, 0, "Еней " + Fb2Fixtures.UNREPRESENTABLE_IN_WINDOWS_1251 + " козак.");

        final Path output = writeOut(document, "uk");

        final String asUtf8 = new String(bytesOf(output), StandardCharsets.UTF_8);
        assertThat(asUtf8).contains("encoding=\"UTF-8\"");
        assertThat(asUtf8).contains(Fb2Fixtures.UNREPRESENTABLE_IN_WINDOWS_1251);
        assertThat(asUtf8).doesNotContain("windows-1251");
    }

    // Covers: FR-DOC-FB2-7 — WHEN a book is exported, THEN title-info's <lang> carries the target language and
    // <src-lang> is left exactly as it was, because it is the only remaining record of what the book was
    // translated from.
    @Test
    void write_targetLanguage_replacesLangAndLeavesSrcLangAlone() {
        final Path output = writeOut(openPrimary(), "de");

        final String xml = new String(bytesOf(output), Fb2Fixtures.WINDOWS_1251);
        assertThat(xml).contains("<lang>de</lang>");
        assertThat(xml).contains("<src-lang>en</src-lang>");
        assertThat(xml).doesNotContain("<lang>uk</lang>");
    }

    // Covers: FR-DOC-FB2-7 — WHEN title-info declares no language at all, THEN exactly one <lang> is added.
    @Test
    void write_missingLang_addsExactlyOne() {
        final String withoutLang = Fb2Fixtures.PRIMARY_XML.replace("<lang>uk</lang>", "");
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), withoutLang, Fb2Fixtures.WINDOWS_1251);
        final Document document = new Fb2Reader(registry).read(file);

        final Path output = writeOut(document, "uk");

        final String xml = new String(bytesOf(output), Fb2Fixtures.WINDOWS_1251);
        assertThat(xml.split("<lang>", -1)).hasSize(2);
        assertThat(xml).contains("<lang>uk</lang>");
    }

    // Covers: FR-DOC-FB2-2 — WHERE the source was a .fb2.zip, the output is a zip archive whose single entry
    // carries the member name the source used.
    @Test
    void write_bookImportedAsZip_reEmitsAZipWithTheSameMemberName() {
        final Path source = Fb2Fixtures.writeFb2Zip(
                tempDir.resolve("book.fb2.zip"), "roman.fb2", Fb2Fixtures.PRIMARY_XML, Fb2Fixtures.WINDOWS_1251);
        final Document document = new Fb2Reader(registry).read(source);

        final Path output = new Fb2Writer(registry).write(document, tempDir.resolve("out.fb2.zip"), "uk");

        assertThat(zipEntryNamesOf(output)).containsExactly("roman.fb2");
    }

    // Covers: FR-DOC-FB2-1 — a comment, a CDATA section and a namespace prefix all survive a zero-edit round
    // trip, because nothing here rebuilds the tree.
    @Test
    void write_zeroEditRoundTrip_preservesCommentCdataAndNamespacePrefix() {
        final Path output = writeOut(openPrimary(), "uk");

        final String xml = new String(bytesOf(output), Fb2Fixtures.WINDOWS_1251);
        assertThat(xml).contains("<!-- scene break -->");
        assertThat(xml).contains("<![CDATA[a < b]]>");
        assertThat(xml).contains("l:href=\"#n1\"");
    }

    // Covers: FR-DOC-FB2-4 — a <binary> element's base64 payload is character-for-character identical after a
    // round trip, never re-wrapped or re-encoded.
    @Test
    void write_zeroEditRoundTrip_preservesTheBinaryPayloadExactly() {
        final Path output = writeOut(openPrimary(), "uk");

        final String xml = new String(bytesOf(output), Fb2Fixtures.WINDOWS_1251);
        assertThat(xml).contains(payloadOf(Fb2Fixtures.PRIMARY_XML));
    }

    // Covers: EC-FB2-3 — a note's element id and the cross-reference pointing at it survive unchanged.
    @Test
    void write_zeroEditRoundTrip_preservesNoteIdAndItsCrossReference() {
        final Path output = writeOut(openPrimary(), "uk");

        final String xml = new String(bytesOf(output), Fb2Fixtures.WINDOWS_1251);
        assertThat(xml).contains("id=\"n1\"");
        assertThat(xml).contains("<a l:href=\"#n1\" type=\"note\">1</a>");
    }

    private static String payloadOf(String xml) {
        final int start = xml.indexOf("image/jpeg\">") + "image/jpeg\">".length();
        return xml.substring(start, xml.indexOf("</binary>", start));
    }

    private static List<String> zipEntryNamesOf(Path archive) {
        final List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                names.add(entry.getName());
                entry = zip.getNextEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }

    /** Rebuilds {@code document} with one segment carrying target text, leaving every other component alone. */
    private static Document withTarget(Document document, int unitIndex, int segmentOrder, String targetInner) {
        final List<Unit> units = new ArrayList<>(document.units());
        final Unit unit = units.get(unitIndex);
        final List<Segment> segments = new ArrayList<>(unit.segments());
        segments.set(segmentOrder, withTargetInner(segments.get(segmentOrder), targetInner));
        units.set(
                unitIndex, new Unit(unit.id(), unit.order(), unit.href(), unit.mediaType(), unit.skeleton(), segments));
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                Map.copyOf(document.metadata()),
                units);
    }

    private static Segment withTargetInner(Segment segment, String targetInner) {
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                segment.masked(),
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                targetInner,
                segment.status(),
                segment.confidence());
    }
}
