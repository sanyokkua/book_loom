package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.epub.EpubTestFactory;
import ua.bookloom.document.epub.EpubZipBuilder;
import ua.bookloom.document.fb2.Fb2Inspection;
import ua.bookloom.document.fb2.OpenFb2Registry;
import ua.bookloom.document.md.MarkdownInspection;
import ua.bookloom.document.md.OpenMarkdownRegistry;
import ua.bookloom.document.txt.OpenTxtRegistry;
import ua.bookloom.document.txt.TxtInspection;
import ua.bookloom.document.txt.TxtReader;

/**
 * {@link BookInspectorService#profile} (task 4.5): assembling title, author, cover, structure and statistics
 * through the façade, and the one refusal a profile can give — a document no longer open.
 */
class BookInspectorServiceProfileTest {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final int CHAPTER_COUNT = 11;

    @TempDir
    private Path tempDir;

    private static BookInspectorService serviceOf(EpubTestFactory.ReaderAndInspection pair) {
        return new BookInspectorService(
                pair.inspection(),
                new Fb2Inspection(new OpenFb2Registry()),
                new MarkdownInspection(new OpenMarkdownRegistry()),
                new TxtInspection(new OpenTxtRegistry()));
    }

    @Test
    void profile_frankensteinLikeEpub_reportsTitleAuthorCoverAndTopLevelNodes() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final BookInspectorService service = serviceOf(pair);
        final Path epub = buildBookWithChapters(tempDir.resolve("frankenstein.epub"));

        final Document document = pair.reader().read(epub);
        final Result<BookProfile> result = service.profile(document);

        assertThat(result.isOk()).isTrue();
        final BookProfile profile = Objects.requireNonNull(result.data(), "data");
        assertThat(profile.title()).isEqualTo("Frankenstein");
        assertThat(profile.author()).isEqualTo("Mary Shelley");
        assertThat(profile.cover()).isNotNull();
        assertThat(profile.structure()).hasSize(CHAPTER_COUNT);
    }

    private static Path buildBookWithChapters(Path destination) {
        final StringBuilder manifest = new StringBuilder(
                "<item id=\"cover-img\" href=\"cover.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>");
        final StringBuilder spine = new StringBuilder();
        final StringBuilder nav = new StringBuilder();
        for (int i = 1; i <= CHAPTER_COUNT; i++) {
            manifest.append(
                    "<item id=\"c%02d\" href=\"c%02d.xhtml\" media-type=\"application/xhtml+xml\"/>".formatted(i, i));
            spine.append("<itemref idref=\"c%02d\"/>".formatted(i));
            nav.append("<li><a href=\"c%02d.xhtml\">Chapter %d</a></li>".formatted(i, i));
        }
        manifest.append(
                "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>");
        final EpubZipBuilder builder = new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", opf(manifest.toString(), spine.toString()))
                .entry("OEBPS/nav.xhtml", navPage(nav.toString()))
                .binaryEntry("OEBPS/cover.jpg", "cover-bytes".getBytes(StandardCharsets.UTF_8));
        for (int i = 1; i <= CHAPTER_COUNT; i++) {
            builder.entry("OEBPS/c%02d.xhtml".formatted(i), xhtml("<p>Chapter " + i + " text.</p>"));
        }
        return builder.writeTo(destination);
    }

    private static String opf(String manifest, String spine) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Frankenstein</dc:title>
                    <dc:creator>Mary Shelley</dc:creator>
                  </metadata>
                  <manifest>%s</manifest>
                  <spine>%s</spine>
                </package>
                """.formatted(manifest, spine);
    }

    private static String navPage(String navBody) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
                + "<body><nav epub:type=\"toc\"><ol>" + navBody + "</ol></nav></body></html>";
    }

    private static String xhtml(String bodyInner) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body>%s</body>
                </html>
                """.formatted(bodyInner);
    }

    @Test
    void profile_txt_noTitleNoAuthorNoCoverOneNode() {
        final OpenTxtRegistry registry = new OpenTxtRegistry();
        final TxtReader reader = new TxtReader(registry);
        final BookInspectorService service = new BookInspectorService(
                EpubTestFactory.newInspection(),
                new Fb2Inspection(new OpenFb2Registry()),
                new MarkdownInspection(new OpenMarkdownRegistry()),
                new TxtInspection(registry));
        final Path txt = write(tempDir.resolve("notes.txt"), "Just plain notes.\n");

        final Document document = reader.read(txt);
        final Result<BookProfile> result = service.profile(document);

        assertThat(result.isOk()).isTrue();
        final BookProfile profile = Objects.requireNonNull(result.data(), "data");
        assertThat(profile.title()).isNull();
        assertThat(profile.author()).isNull();
        assertThat(profile.cover()).isNull();
        assertThat(profile.structure()).hasSize(1);
    }

    @Test
    void profile_closedDocument_answersInternalError() {
        final EpubTestFactory.ReaderAndInspection pair = EpubTestFactory.newSharedReaderAndInspection();
        final BookInspectorService service = serviceOf(pair);
        final Path epub = buildBookWithChapters(tempDir.resolve("closed.epub"));
        final Document document = pair.reader().read(epub);
        pair.reader().close(document.id());

        final Result<BookProfile> result = service.profile(document);

        assertThat(result.isOk()).isFalse();
        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.internal);
    }

    private static Path write(Path destination, String content) {
        try {
            Files.writeString(destination, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return destination;
    }
}
