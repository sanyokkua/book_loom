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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.fixture.Fb2Fixtures;

/** The auxiliary slots {@code Fb2Reader} produces from {@code title-info} and {@code Fb2Writer} writes back. */
class Fb2AuxiliaryTest {

    private static final String TITLE_INFO_BOOK = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <book-title>Harry Potter et la Coupe de Feu</book-title>
                  <author><first-name>Leo</first-name> <last-name>Tolstoy</last-name><id>a1b2</id><email>leo@example.org</email></author>
                  <annotation><p>First paragraph.</p><p>Second <emphasis>paragraph</emphasis>.</p></annotation>
                  <lang>fr</lang>
                </title-info>
                <src-title-info><book-title>Война и мир</book-title></src-title-info>
                <document-info><history><p>v1.0 — scanned</p></history></document-info>
              </description>
              <body><section><p>Body text.</p></section></body>
            </FictionBook>
            """;

    @TempDir
    private Path tempDir;

    private final OpenFb2Registry registry = new OpenFb2Registry();

    private Document open(String xml) {
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), xml, StandardCharsets.UTF_8);
        return new Fb2Reader(registry).read(file);
    }

    private static Unit auxiliaryOf(Document document) {
        return document.units().get(document.units().size() - 1);
    }

    private static Segment segment(Document document, String id) {
        return auxiliaryOf(document).segments().stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private String writeWithTargets(Document document, Map<String, String> targets) {
        final List<Unit> units = new ArrayList<>(document.units());
        final Unit aux = auxiliaryOf(document);
        final List<Segment> segments = aux.segments().stream()
                .map(s -> targets.containsKey(s.id()) ? s.withDecision(SegmentStatus.ACCEPTED, targets.get(s.id())) : s)
                .toList();
        units.set(
                units.size() - 1,
                new Unit(aux.id(), aux.order(), aux.href(), aux.mediaType(), aux.skeleton(), segments));
        final Document targeted = new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                document.metadata(),
                units);
        final Path out = new Fb2Writer(registry).write(targeted, tempDir.resolve("out.fb2"), "uk");
        try {
            return Files.readString(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // A title, one author and a two-paragraph annotation are one segment each, in that order, with their kinds.
    @Test
    void read_titleAuthorAndAnnotation_yieldsOneTitleOneAuthorTwoDescriptions() {
        final Unit aux = auxiliaryOf(open(TITLE_INFO_BOOK));

        assertThat(aux.segments())
                .extracting(Segment::id, Segment::kind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("aux:title", SegmentKind.METADATA_TITLE),
                        org.assertj.core.groups.Tuple.tuple("aux:creator:0", SegmentKind.METADATA_AUTHOR),
                        org.assertj.core.groups.Tuple.tuple("aux:description:0", SegmentKind.METADATA_DESCRIPTION),
                        org.assertj.core.groups.Tuple.tuple("aux:description:1", SegmentKind.METADATA_DESCRIPTION));
        assertThat(aux.segments().get(0).sourceInner()).isEqualTo("Harry Potter et la Coupe de Feu");
        assertThat(aux.segments().get(3).masked()).isEqualTo("Second ⟦g0⟧paragraph⟦g1⟧.");
    }

    // An author is masked from its name parts only, one pair around each, whitespace between kept.
    @Test
    void read_authorWithIdAndEmail_masksOnlyTheNameParts() {
        final Segment author = segment(open(TITLE_INFO_BOOK), "aux:creator:0");

        assertThat(author.masked()).isEqualTo("⟦g0⟧Leo⟦g1⟧ ⟦g2⟧Tolstoy⟦g3⟧");
        assertThat(author.pairs()).hasSize(2);
        assertThat(author.placeholders().values()).doesNotContain("a1b2", "leo@example.org");
    }

    // Source-book information and the file history describe another book and file, so nothing is read from them.
    @Test
    void read_srcTitleInfoAndHistory_yieldNoSegment() {
        final Unit aux = auxiliaryOf(open(TITLE_INFO_BOOK));

        assertThat(aux.segments())
                .extracting(Segment::sourceInner)
                .noneMatch(text -> text.contains("Война") || text.contains("scanned"));
    }

    // A book with no title information has an empty auxiliary unit.
    @Test
    void read_noTitleInfo_yieldsAnEmptyAuxiliaryUnit() {
        final String xml = TITLE_INFO_BOOK.replaceAll("(?s)<title-info>.*</title-info>", "");

        assertThat(auxiliaryOf(open(xml)).segments()).isEmpty();
    }

    // A nickname-only author is masked from the nickname.
    @Test
    void read_nicknameOnlyAuthor_masksTheNickname() {
        final String xml = TITLE_INFO_BOOK.replace(
                "<first-name>Leo</first-name> <last-name>Tolstoy</last-name>", "<nickname>Ghost</nickname>");

        assertThat(segment(open(xml), "aux:creator:0").masked()).isEqualTo("⟦g0⟧Ghost⟦g1⟧");
    }

    // A target for the title alone changes the title and leaves author and annotation as they were.
    @Test
    void write_titleTargetOnly_translatesTheTitleAndLeavesTheRest() {
        final String output = writeWithTargets(open(TITLE_INFO_BOOK), Map.of("aux:title", "Гаррі Поттер"));

        assertThat(output)
                .contains("<book-title>Гаррі Поттер</book-title>")
                .contains("<first-name>Leo</first-name> <last-name>Tolstoy</last-name><id>a1b2</id>")
                .contains("<p>First paragraph.</p><p>Second <emphasis>paragraph</emphasis>.</p>");
    }

    // Each name part receives the text its own pair encloses; id and email stay.
    @Test
    void write_authorTarget_replacesEachNamePartInPlace() {
        final String output = writeWithTargets(
                open(TITLE_INFO_BOOK),
                Map.of("aux:creator:0", "<first-name>Лев</first-name> <last-name>Толстой</last-name>"));

        assertThat(output)
                .contains("<first-name>Лев</first-name> <last-name>Толстой</last-name><id>a1b2</id>")
                .contains("<email>leo@example.org</email>");
    }

    // An annotation paragraph is written back as markup, keeping its inline element.
    @Test
    void write_annotationTarget_keepsInlineElements() {
        final String output = writeWithTargets(
                open(TITLE_INFO_BOOK), Map.of("aux:description:1", "Другий <emphasis>абзац</emphasis> &amp; кінець."));

        assertThat(output).contains("<p>First paragraph.</p><p>Другий <emphasis>абзац</emphasis> &amp; кінець.</p>");
    }

    // A title target's ampersand is escaped once.
    @Test
    void write_titleTargetWithAmpersand_isEscapedOnce() {
        final String output = writeWithTargets(open(TITLE_INFO_BOOK), Map.of("aux:title", "Том &amp; Джеррі"));

        assertThat(output).contains("<book-title>Том &amp; Джеррі</book-title>").doesNotContain("&amp;amp;");
    }
}
