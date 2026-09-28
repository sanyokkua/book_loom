package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.Fb2Fixtures;
import ua.bookloom.document.fixture.MarkdownFixtures;
import ua.bookloom.document.fixture.TxtFixtures;
import ua.bookloom.document.golden.XmlLeafDiff.Change;

/**
 * The auxiliary-text obligations of the FB2, Markdown and TXT golden gates (task 6.6): a zero-edit write stays
 * canonical-equal (byte-identical for TXT) now that auxiliary text is produced, and an edit to auxiliary slots
 * changes only their values and the language metadata.
 */
class FormatAuxiliaryGoldenTest {

    private static final String FB2_WITH_ANNOTATION = Fb2Fixtures.PRIMARY_XML.replace(
            "<src-lang>en</src-lang>",
            "<src-lang>en</src-lang>\n      <annotation><p>Бурлескна поема.</p></annotation>");

    private static final String MARKDOWN_WITH_FRONTMATTER_AND_IMAGE =
            "---\ntitle: The Book\nlang: en\n---\n\n# Chapter One\n\n![Figure 1](fig1.png)\n";

    @TempDir
    private Path tempDir;

    @Test
    void write_fb2WithTitleInfoAuxiliaryAndNoTargets_isCanonicalEqualToSource() {
        final Path source = fb2Fixture();
        final DocumentService service = DocumentServices.newService();
        final Document document = open(service, source);

        final Path output = write(service, document, source, "uk");

        Fb2CanonicalAssert.assertCanonicalEqual(source, output, "uk");
        assertThat(auxiliaryOf(document).segments())
                .extracting(Segment::id)
                .contains("aux:title", "aux:creator:0", "aux:description:0");
    }

    @Test
    void write_fb2OnlyAuxiliarySlotsEdited_changesExactlyThoseValuesAndTheLanguageMetadata() throws IOException {
        final Path source = fb2Fixture();
        final DocumentService service = DocumentServices.newService();
        final Document edited = AuxiliaryTargets.with(
                open(service, source),
                Map.of(
                        "aux:title",
                        "Енеїда (переклад)",
                        "aux:creator:0",
                        "<first-name>Ivan</first-name><last-name>Kotliarevskyi</last-name>",
                        "aux:description:0",
                        "Burlesque poem."));

        final Path output = write(service, edited, source, "en");

        final List<Change> changes = XmlLeafDiff.between(Files.readAllBytes(source), Files.readAllBytes(output));
        assertThat(changes)
                .filteredOn(change -> !change.isLanguage())
                .extracting(Change::oldValue, Change::newValue)
                .containsExactlyInAnyOrder(
                        tuple("Енеїда", "Енеїда (переклад)"),
                        tuple("Іван", "Ivan"),
                        tuple("Котляревський", "Kotliarevskyi"),
                        tuple("Бурлескна поема.", "Burlesque poem."));
        assertThat(changes)
                .filteredOn(Change::isLanguage)
                .isNotEmpty()
                .extracting(Change::newValue)
                .containsOnly("en");
    }

    // The frontmatter value, the alt text and the body are all read as segments, yet a zero-edit write with target
    // `uk` differs from the source in one place: the `lang` value.
    @Test
    void write_markdownFrontmatterAndImageWithNoTargets_differsOnlyInTheLangValue() throws IOException {
        final Path source = MarkdownFixtures.write(tempDir.resolve("book.md"), MARKDOWN_WITH_FRONTMATTER_AND_IMAGE);
        final DocumentService service = DocumentServices.newService();
        final Document document = open(service, source);

        final Path output = write(service, document, source, "uk");

        assertThat(Files.readString(output, StandardCharsets.UTF_8))
                .isEqualTo(MARKDOWN_WITH_FRONTMATTER_AND_IMAGE.replace("lang: en", "lang: uk"));
        assertThat(auxiliaryOf(document).segments())
                .extracting(Segment::id, Segment::sourceInner)
                .containsExactly(tuple("aux:fm:title", "The Book"), tuple("aux:alt:book.md:img0", "Figure 1"));
    }

    @Test
    void write_markdownOnlyAuxiliarySlotsEdited_changesExactlyTheTitleTheAltAndTheLang() throws IOException {
        final Path source = MarkdownFixtures.write(tempDir.resolve("book.md"), MARKDOWN_WITH_FRONTMATTER_AND_IMAGE);
        final DocumentService service = DocumentServices.newService();
        final Document edited = AuxiliaryTargets.with(
                open(service, source), Map.of("aux:fm:title", "Книга", "aux:alt:book.md:img0", "Рисунок 1"));

        final Path output = write(service, edited, source, "uk");

        assertThat(Files.readString(output, StandardCharsets.UTF_8))
                .isEqualTo("---\ntitle: Книга\nlang: uk\n---\n\n# Chapter One\n\n![Рисунок 1](fig1.png)\n");
    }

    @Test
    void write_txtWithNoTargets_isByteIdenticalAndItsAuxiliaryUnitIsEmpty() throws IOException {
        final Path source = TxtFixtures.primary(tempDir.resolve("notes.txt"));
        final DocumentService service = DocumentServices.newService();
        final Document document = open(service, source);

        final Path output = write(service, document, source, "uk");

        assertThat(Files.readAllBytes(output)).isEqualTo(Files.readAllBytes(source));
        assertThat(document.units())
                .filteredOn(Unit::isAuxiliary)
                .allSatisfy(unit -> assertThat(unit.segments()).isEmpty());
    }

    private Path fb2Fixture() {
        return Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), FB2_WITH_ANNOTATION, Fb2Fixtures.WINDOWS_1251);
    }

    private static Unit auxiliaryOf(Document document) {
        return document.units().stream().filter(Unit::isAuxiliary).findFirst().orElseThrow();
    }

    private static Document open(DocumentService service, Path source) {
        return Objects.requireNonNull(service.open(source).data(), "data");
    }

    private Path write(DocumentService service, Document document, Path source, String targetLanguage) {
        return Objects.requireNonNull(
                service.write(document, tempDir.resolve("out-" + source.getFileName()), targetLanguage)
                        .data(),
                "data");
    }
}
