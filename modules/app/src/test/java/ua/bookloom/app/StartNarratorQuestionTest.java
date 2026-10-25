package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.RunState;

/**
 * The Start question about the narrator in the real window, for a book that declares its language (EPUB) and one
 * that does not (Markdown), whose narration can be judged only once the person has chosen the source.
 */
// `slow`: an import and a start in the real window; `test` and the gate run it, `fastTest` leaves it out.
@Tag("slow")
class StartNarratorQuestionTest extends WorkspaceTestBase {

    private static final String[] PARAGRAPHS = {
        "I walked down to the harbour before the sun was up.",
        "My boots were wet and Anna said I did not care at all.",
        "I saw Anna count the boats as they came in.",
        "Anna stood by the rail and she waved at me.",
        "I waved back and I laughed at her wet hat.",
        "My brother was late again, as Anna and I had expected.",
        "I bought bread and I shared it with Anna.",
        "She told me the fish were scarce this year.",
        "I listened to Anna and I watched the gulls circle above.",
        "My day had started well and I was glad of it."
    };

    // IF the narration of a book with no declared language were judged only by the run, THEN Start would never ask who
    // narrates it, and a woman's first-person story would be drafted with the default masculine forms.
    @ParameterizedTest
    @ValueSource(strings = {"Harbour.md", "Harbour.epub"})
    void start_firstPersonBook_asksWhoNarratesAndEnterStartsAnywayAfterTheCharacters(final String file)
            throws Exception {
        final Path book = file.endsWith(".md") ? markdown() : epub();
        startWorkspace(ReviewMode.UNATTENDED);
        onFx(() ->
                injector.getInstance(ua.bookloom.ui.state.ImportViewModel.class).open(book));
        waitUntil(() -> injector.getInstance(CurrentProject.class).book().get() != null);
        chooseLanguages();
        chooseModel();
        seedAnnaWithoutGender();
        onFx(() -> shell.activate(ViewNames.TRANSLATING));

        onFx(() -> ((Button) Objects.requireNonNull(shell.root().lookup("#translating-start"))).fire());
        waitUntil(() -> shell.root().lookup("#narrator-card") != null);
        assertThat(runState()).as("nothing starts before the answer").isEqualTo(RunState.IDLE);
        final Node unknown = onFx(() -> shell.root().lookup("#narrator-unknown"));
        assertThat(onFx(() -> ((Button) unknown).isDefaultButton())).isTrue();
        new FxRobot().type(KeyCode.ENTER);

        waitUntil(() -> shell.root().lookup("#genders-card") != null);
        assertThat(onFx(() -> shell.root().lookup("#narrator-card"))).isNull();
        assertThat(runState()).isEqualTo(RunState.IDLE);
    }

    private void chooseLanguages() throws Exception {
        final var brief = injector.getInstance(ua.bookloom.ui.state.BookBriefViewModel.class);
        onFx(() -> brief.setSourceLanguage("en"));
        onFx(() -> brief.setTargetLanguage("uk"));
        waitUntil(() -> !brief.saving().get());
    }

    private void seedAnnaWithoutGender() {
        final OpenedBook open = Objects.requireNonNull(
                onFx(() -> injector.getInstance(CurrentProject.class).book().get()));
        assertThat(injector.getInstance(GlossaryService.class)
                        .add(new GlossaryEntry(
                                "anna", open.projectId(), "Anna", null, TermType.CHARACTER, Gender.UNKNOWN, false))
                        .isOk())
                .isTrue();
    }

    private Path markdown() throws IOException {
        return Files.writeString(
                booksDir.resolve("Harbour.md"),
                "# Morning\n\n" + String.join("\n\n", PARAGRAPHS) + "\n",
                StandardCharsets.UTF_8);
    }

    // The smallest EPUB 3 the importer reads: the stored mimetype first, the container, the package with its declared
    // language, and one chapter.
    private Path epub() throws IOException {
        final Path written = booksDir.resolve("Harbour.epub");
        try (OutputStream out = Files.newOutputStream(written);
                ZipOutputStream zip = new ZipOutputStream(out)) {
            stored(zip, "mimetype", "application/epub+zip");
            deflated(zip, "META-INF/container.xml", CONTAINER);
            deflated(zip, "OEBPS/content.opf", PACKAGE);
            deflated(zip, "OEBPS/nav.xhtml", NAV);
            deflated(zip, "OEBPS/chapter.xhtml", chapter());
        }
        return written;
    }

    private static String chapter() {
        final StringBuilder body = new StringBuilder("<h1>Morning</h1>");
        for (final String paragraph : PARAGRAPHS) {
            body.append("<p>").append(paragraph).append("</p>");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en"><head><title>Morning</title></head>
                <body>%s</body></html>
                """.formatted(body);
    }

    private static void stored(final ZipOutputStream zip, final String name, final String text) throws IOException {
        final byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        final CRC32 crc = new CRC32();
        crc.update(bytes);
        final ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void deflated(final ZipOutputStream zip, final String name, final String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final String CONTAINER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
            """;

    private static final String PACKAGE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="id">harbour-test</dc:identifier>
                <dc:title>Harbour</dc:title>
                <dc:language>en</dc:language>
                <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="chapter"/></spine>
            </package>
            """;

    private static final String NAV = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head>
            <body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">Morning</a></li></ol></nav></body></html>
            """;
}
