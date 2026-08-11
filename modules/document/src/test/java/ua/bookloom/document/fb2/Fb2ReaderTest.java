package ua.bookloom.document.fb2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.DrmRefusedException;

/**
 * {@code Fb2Reader}'s read path against the hand-authored {@code windows-1251} primary fixture.
 */
class Fb2ReaderTest {

    @TempDir
    private Path tempDir;

    private Document readPrimary() {
        final Path file =
                Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), Fb2Fixtures.PRIMARY_XML, Fb2Fixtures.WINDOWS_1251);
        return new Fb2Reader(new OpenFb2Registry()).read(file);
    }

    private static List<Segment> segmentsOf(Document document) {
        return document.units().stream().flatMap(u -> u.segments().stream()).toList();
    }

    private static List<String> sourceInnersOf(Document document) {
        return segmentsOf(document).stream().map(Segment::sourceInner).toList();
    }

    // Covers: FR-IMPORT-01 — WHEN a FictionBook file is opened, THEN the parsed document reports format FB2
    // rather than failing as an invalid EPUB container.
    @Test
    void read_fictionBookFile_reportsFormatFb2() {
        assertThat(readPrimary().format()).isEqualTo(BookFormat.FB2);
    }

    // Covers: FR-DOC-FB2-3 — WHEN an FB2 with no byte-order mark declares windows-1251, THEN that declaration
    // outranks detection and the document records that charset.
    @Test
    void read_windows1251Declaration_isRecordedOnTheDocument() {
        final Document document = readPrimary();

        assertThat(document.charset()).isEqualToIgnoringCase("windows-1251");
        assertThat(document.hasBom()).isFalse();
    }

    // Covers: FR-DOC-FB2-1 — an FB2 book's two bodies get distinct unit ids derived from the source name and the
    // body's position, and both report the source file as their href.
    @Test
    void read_mainAndNotesBodies_getDistinctUnitIdsSharingOneHref() {
        final Document document = readPrimary();

        assertThat(document.units()).extracting(Unit::id).containsExactly("book.fb2#0", "book.fb2#1");
        assertThat(document.units()).extracting(Unit::href).containsOnly("book.fb2");
        assertThat(document.units()).extracting(Unit::mediaType).containsOnly("application/x-fictionbook+xml");
    }

    // Covers: FR-DOC-FB2-1 — segment ids are built from the unit id, so a notes-body segment is namespaced by it.
    @Test
    void read_segmentIds_areBuiltFromTheirUnitId() {
        final Document document = readPrimary();

        assertThat(document.units().get(1).segments()).extracting(Segment::id).containsExactly("book.fb2#1:0");
    }

    // Covers: EC-VERSE-1 — each verse line of a poem is its own segment, so a stanza is never sent as one block.
    @Test
    void read_poemStanza_yieldsOneVerseLineSegmentPerLine() {
        assertThat(segmentsOf(readPrimary()))
                .filteredOn(s -> s.kind() == SegmentKind.VERSE_LINE)
                .extracting(Segment::sourceInner)
                .containsExactly("Рядок перший", "Рядок другий", "Рядок третій");
    }

    // Covers: EC-VERSE-2 — WHEN a poem contains verse lines with no stanza element around them, THEN one segment
    // per line is still produced, because the walker descends containers rather than requiring a known one.
    @Test
    void read_poemWithoutStanzaGrouping_stillYieldsOneSegmentPerVerseLine() {
        final String withoutStanza = Fb2Fixtures.PRIMARY_XML.replace(
                "<poem><stanza><v>Рядок перший</v><v>Рядок другий</v><v>Рядок третій</v></stanza></poem>",
                "<poem><v>Один</v><v>Два</v><v>Три</v><v>Чотири</v></poem>");
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), withoutStanza, Fb2Fixtures.WINDOWS_1251);

        final Document document = new Fb2Reader(new OpenFb2Registry()).read(file);

        assertThat(segmentsOf(document))
                .filteredOn(s -> s.kind() == SegmentKind.VERSE_LINE)
                .extracting(Segment::sourceInner)
                .containsExactly("Один", "Два", "Три", "Чотири");
    }

    // Covers: EC-FB2-4 — a table's cells are segments and the table itself is not.
    @Test
    void read_table_yieldsOneTableCellSegmentPerCell() {
        assertThat(segmentsOf(readPrimary()))
                .filteredOn(s -> s.kind() == SegmentKind.TABLE_CELL)
                .hasSize(6);
    }

    // Covers: FR-DOC-FB2-5 — a <cite> container is descended into to reach the paragraph inside it.
    @Test
    void read_citeContainer_isDescendedIntoToReachItsParagraph() {
        assertThat(sourceInnersOf(readPrimary())).contains("Цитоване речення.");
    }

    // Covers: FR-DOC-FB2-5 — an <empty-line/> carries no words and yields no segment.
    @Test
    void read_emptyLineElement_yieldsNoSegment() {
        assertThat(sourceInnersOf(readPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("empty-line"));
    }

    // Covers: EC-IMG-1 — a paragraph holding only an image yields no segment.
    @Test
    void read_imageOnlyParagraph_yieldsNoSegment() {
        assertThat(sourceInnersOf(readPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("<image"));
    }

    // Covers: FR-DOC-08 — text carried after a line break inside an FB2 paragraph becomes its own segment rather
    // than being lost.
    @Test
    void read_lineBreakRun_yieldsOneSegmentPerRun() {
        assertThat(sourceInnersOf(readPrimary())).contains("Пролог", "Хвіст комети");
    }

    // Covers: FR-DOC-FB2-6 — the notes body's prose is segmented like any other prose.
    @Test
    void read_notesBody_isSegmented() {
        assertThat(readPrimary().units().get(1).segments())
                .extracting(Segment::sourceInner)
                .containsExactly("Текст примітки.");
    }

    // Covers: FR-DOC-FB2-4 — a <binary> element yields no segment; its base64 payload is never translatable text.
    @Test
    void read_binarySection_yieldsNoSegment() {
        assertThat(sourceInnersOf(readPrimary()))
                .noneSatisfy(inner -> assertThat(inner).contains("iVBORw0"));
    }

    // Covers: FR-IMPORT-07 — the declared language and the title/author metadata are read from title-info.
    @Test
    void read_titleInfo_populatesDeclaredLangAndMetadata() {
        final Document document = readPrimary();

        assertThat(document.declaredLang()).isEqualTo("uk");
        assertThat(document.metadata()).containsEntry("title", "Енеїда");
        assertThat(document.metadata()).containsEntry("author", "Іван Котляревський");
    }

    // Covers: FR-IMPORT-07 — WHEN title-info declares no language, THEN the document records none rather than
    // inventing one, because absence is what a later detection step has to be able to see.
    @Test
    void read_titleInfoWithoutLang_recordsNoDeclaredLanguage() {
        final String withoutLang = Fb2Fixtures.PRIMARY_XML.replace("<lang>uk</lang>", "");
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), withoutLang, Fb2Fixtures.WINDOWS_1251);

        assertThat(new Fb2Reader(new OpenFb2Registry()).read(file).declaredLang())
                .isNull();
    }

    // Covers: EC-FB2-2 — IF the declared encoding disagrees with the encoding detected from the bytes with high
    // confidence, THEN the book is refused rather than being imported under a substituted encoding.
    @Test
    void read_declarationContradictingContent_isRefused() {
        final Path file = Fb2Fixtures.writeFb2(
                tempDir.resolve("book.fb2"), Fb2Fixtures.CONTRADICTED_DECLARATION_XML, Fb2Fixtures.WINDOWS_1251);

        assertThatThrownBy(() -> new Fb2Reader(new OpenFb2Registry()).read(file))
                .isInstanceOf(CorruptContainerException.class)
                .hasMessageContaining("contradicts");
    }

    // Covers: EC-FB2-2 — a single-byte declaration that decodes without error is still checked, because the code
    // pages real books misdeclare accept every byte and so never fail to decode.
    @Test
    void read_iso88591DeclarationOverCyrillicBytes_isStillRefused() {
        final String misdeclared = Fb2Fixtures.PRIMARY_XML.replace("windows-1251", "iso-8859-1");
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), misdeclared, Fb2Fixtures.WINDOWS_1251);

        assertThatThrownBy(() -> new Fb2Reader(new OpenFb2Registry()).read(file))
                .isInstanceOf(CorruptContainerException.class);
    }

    // Covers: FR-DOC-FB2-2 — WHERE the source is a .fb2.zip, the FB2 member inside it is read and the result is
    // treated as an ordinary FB2 document.
    @Test
    void read_zippedFictionBook_parsesTheMemberAsFb2() {
        final Path file = Fb2Fixtures.writeFb2Zip(
                tempDir.resolve("book.fb2.zip"), "book.fb2", Fb2Fixtures.PRIMARY_XML, Fb2Fixtures.WINDOWS_1251);

        final Document document = new Fb2Reader(new OpenFb2Registry()).read(file);

        assertThat(document.format()).isEqualTo(BookFormat.FB2);
        assertThat(document.units()).extracting(Unit::href).containsOnly("book.fb2");
    }

    // Covers: FR-IMPORT-05 — a zip carrying no FictionBook member is refused with no document returned.
    @Test
    void read_zipWithNoFictionBookMember_isRefused() {
        final Path file = Fb2Fixtures.writeFb2Zip(
                tempDir.resolve("book.fb2.zip"), "readme.txt", "just a readme", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new Fb2Reader(new OpenFb2Registry()).read(file))
                .isInstanceOf(CorruptContainerException.class)
                .hasMessageContaining("no FictionBook member");
    }

    // Covers: EC-DRM-1 — IF a .fb2.zip declares an encrypted entry, THEN the book is refused as protected rather
    // than reported as a corrupt archive, and no unit or segment is produced.
    @Test
    void read_encryptedZipMember_isRefusedAsProtectedNotCorrupt() {
        final Path file = Fb2Fixtures.writeEncryptedFb2Zip(tempDir.resolve("book.fb2.zip"));

        assertThatThrownBy(() -> new Fb2Reader(new OpenFb2Registry()).read(file))
                .isInstanceOf(DrmRefusedException.class)
                .hasMessageContaining("encrypted");
    }

    // Covers: FR-IMPORT-05 — an FB2 that is not well-formed XML is refused.
    @Test
    void read_malformedXml_isRefused() {
        final Path file = Fb2Fixtures.writeFb2(
                tempDir.resolve("book.fb2"), "<FictionBook><body><p>unclosed", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new Fb2Reader(new OpenFb2Registry()).read(file))
                .isInstanceOf(CorruptContainerException.class);
    }
}
