package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.MAPPER;
import static ua.bookloom.pipeline.export.ExportJobFixture.entries;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.read;
import static ua.bookloom.pipeline.export.ExportJobFixture.request;
import static ua.bookloom.pipeline.export.ExportJobFixture.zipEntry;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.TestBooks;

/**
 * What the export writes into the book, over generated books of every format: each segment's effective target laid
 * over a fresh read of the source, and the target language the brief names declared where the format has a place.
 */
class ExportJobImplTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String FRENCH = "Le café du coin était fermé ce matin-là, et personne ne savait pourquoi.\n"
            + "Les élèves attendaient devant la porte, sous la pluie fine et froide.";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
    }

    // A run with the pseudo model decides the paragraph; the export writes its markup back and declares uk.
    @Test
    void run_epubTranslatedWithPseudoModel_writesMarkupAndTargetLanguage() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("Tom &amp; <i>Jerry</i> ran.")), "en");
        final String id = fixture.importBook(source, "en");
        final TranslationJob job =
                ok(fixture.engine().newJob(new RunRequest(id, ReviewMode.UNATTENDED), new PseudoChatModel(MAPPER)));
        ok(job.run());
        final Path destination = tempDir.resolve("Book.uk.epub");

        ok(fixture.export(request(id, destination, false)));

        assertThat(zipEntry(destination, "OEBPS/ch0.xhtml")).contains("<p>TOM &amp; <i>JERRY</i> RAN.</p>");
        assertThat(zipEntry(destination, "OEBPS/content.opf")).contains("<dc:language>uk</dc:language>");
    }

    // The package declares no language, so only the brief's source language passed to the writer finds xml:lang="en".
    @Test
    void run_chapterDeclaringBriefSourceLanguage_writesTargetLanguageAttribute() {
        final Path source = TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One.")), null, "en");
        final String id = fixture.importBook(source, "en");
        final Path destination = tempDir.resolve("Book.uk.epub");

        ok(fixture.export(request(id, destination, false)));

        assertThat(zipEntry(destination, "OEBPS/ch0.xhtml"))
                .contains("xml:lang=\"uk\"")
                .doesNotContain("\"en\"");
    }

    // The FB2 title-info names the language the book is now in.
    @Test
    void run_fb2DeclaringSourceLanguage_writesTargetLanguage() {
        final Path source = TestBooks.fb2(tempDir.resolve("Book.fb2"), List.of("One."), "en");
        final String id = fixture.importBook(source, "en");
        final Path destination = tempDir.resolve("Book.uk.fb2");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).contains("<lang>uk</lang>").doesNotContain("<lang>en</lang>");
    }

    // A Markdown book that says lang: en is told it is now uk.
    @Test
    void run_markdownWithLanguageKey_writesTargetLanguage() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.", "en");
        final String id = fixture.importBook(source, "en");
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).contains("lang: uk").doesNotContain("lang: en");
    }

    // A Markdown book that says nothing about its language is not given a key it never had.
    @Test
    void run_markdownWithoutLanguageKey_writesNoLanguage() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "---\ntitle: The Lighthouse\n---\n\nOne.");
        final String id = fixture.importBook(source, "en");
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).isEqualTo("---\ntitle: The Lighthouse\n---\n\nOne.");
    }

    // windows-1252 cannot hold Cyrillic, so the whole file is written as UTF-8 and opens again intact.
    @Test
    void run_windows1252TxtWithCyrillicTarget_writesWholeFileAsUtf8() throws IOException {
        final Path source = tempDir.resolve("Book.txt");
        Files.write(source, (FRENCH + "\n\nHe opened the old door.\n").getBytes(WINDOWS_1252));
        final String id = fixture.importBook(source, "fr");
        fixture.accept(id, fixture.bodyRecords(id).get(1).segmentId(), "Він відчинив старі двері.");
        final Path destination = tempDir.resolve("Book.uk.txt");

        ok(fixture.export(request(id, destination, false)));

        assertThat(new String(Files.readAllBytes(destination), StandardCharsets.UTF_8))
                .isEqualTo(FRENCH + "\n\nВін відчинив старі двері.\n");
        final Document reopened = ok(fixture.documents().open(destination));
        assertThat(ExportJobFixture.bodySegments(reopened).get(1).sourceInner()).isEqualTo("Він відчинив старі двері.");
        ok(fixture.documents().close(reopened));
    }

    // A flagged segment is written with the machine translation it has.
    @Test
    void run_flaggedWithMachineTarget_writesIt() {
        final String id = markdown("He opened the *old* door.");
        fixture.flag(id, "Book.md:0", "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).isEqualTo("Він відчинив *старі* двері.");
    }

    // A flagged segment no draft ever passed for is written in its source.
    @Test
    void run_flaggedWithoutMachineTarget_writesSource() {
        final String id = markdown("He opened the *old* door.");
        fixture.flag(id, "Book.md:0", null);
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).isEqualTo("He opened the *old* door.");
    }

    // The person's saved edit wins over the machine translation.
    @Test
    void run_revisedSegment_writesTheEdit() {
        final String id = markdown("He went away.");
        fixture.decide(
                id,
                "Book.md:0",
                record -> record.withStatus(SegmentStatus.REVISED)
                        .withMachineTarget("Він пішов.", "Він пішов.")
                        .withUserTarget("Він пішов геть.", "Він пішов геть."));
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).isEqualTo("Він пішов геть.");
    }

    // A pending segment the run did not reach is written in its source, in the Markdown format.
    @Test
    void run_pendingSegment_writesSourceUnchanged() {
        final String id = markdown("He went away.\n\nThe end.");
        fixture.accept(id, "Book.md:0", "Він пішов геть.");
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).isEqualTo("Він пішов геть.\n\nThe end.");
    }

    // Reading the source afresh each time makes two exports of the same decisions the same book.
    @Test
    void run_sameDecisionsTwice_writesCanonicalEqualBooks() {
        final Path source = TestBooks.epub(
                tempDir.resolve("Book.epub"), List.of(List.of("One.", "Tom &amp; <i>Jerry</i> ran.")), "en");
        final String id = fixture.importBook(source, "en");
        fixture.accept(id, fixture.bodyRecords(id).get(1).segmentId(), "Том & ⟦g0⟧Джеррі⟦g1⟧ побігли.");

        ok(fixture.export(request(id, tempDir.resolve("a.epub"), false)));
        ok(fixture.export(request(id, tempDir.resolve("b.epub"), false)));

        assertThat(entries(tempDir.resolve("b.epub"))).isEqualTo(entries(tempDir.resolve("a.epub")));
    }

    private String markdown(final String content) {
        return fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), content), "en");
    }
}
