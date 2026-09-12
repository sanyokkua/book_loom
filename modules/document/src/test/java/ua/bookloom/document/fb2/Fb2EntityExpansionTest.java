package ua.bookloom.document.fb2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * An FB2 book's entity references are expanded the way every e-book reader expands them: from the book's own
 * header, or from the standard named-entity list bundled with the app — never from the network. Before this, the
 * parser kept the reference <em>and</em> its expansion, so `&nbsp;` came back doubled on every write (debt D6).
 */
class Fb2EntityExpansionTest {

    private static final char NBSP = ' ';
    private static final char EM_DASH = '—';

    @TempDir
    private Path tempDir;

    private final OpenFb2Registry registry = new OpenFb2Registry();

    private static String book(String doctype, String paragraph) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                %s
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description><title-info><book-title>T</book-title><lang>uk</lang></title-info></description>
                  <body><section><p>%s</p></section></body>
                </FictionBook>
                """.formatted(doctype, paragraph);
    }

    private String roundTrip(String xml) {
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), xml, StandardCharsets.UTF_8);
        final Document document = new Fb2Reader(registry).read(file);
        final Path out = new Fb2Writer(registry).write(document, tempDir.resolve("out.fb2"), "uk");
        return readUtf8(out);
    }

    private static String readUtf8(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long count(String text, char c) {
        return text.chars().filter(ch -> ch == c).count();
    }

    // WHEN the book declares an entity in its own header, THEN the reference is expanded once and the written book
    // carries exactly one character for it — never the reference plus its expansion.
    @Test
    void read_internalSubsetEntity_expandsToOneCharacter() {
        final String out = roundTrip(book("<!DOCTYPE FictionBook [<!ENTITY nbsp \"&#160;\">]>", "Київ&nbsp;1991"));

        assertThat(out).contains("Київ" + NBSP + "1991").doesNotContain("&nbsp;");
        assertThat(count(out, NBSP)).isEqualTo(1);
    }

    // WHEN the book declares its own custom entity, THEN its value is what the segment and the written book carry.
    @Test
    void read_customInternalEntity_expandsToItsValue() {
        final Path file = Fb2Fixtures.writeFb2(
                tempDir.resolve("book.fb2"),
                book("<!DOCTYPE FictionBook [<!ENTITY author \"Котляревський\">]>", "Автор: &author;"),
                StandardCharsets.UTF_8);
        final Document document = new Fb2Reader(registry).read(file);

        final String out = readUtf8(new Fb2Writer(registry).write(document, tempDir.resolve("out.fb2"), "uk"));

        assertThat(document.units().get(0).segments().get(0).sourceInner()).isEqualTo("Автор: Котляревський");
        assertThat(out).contains("Автор: Котляревський").doesNotContain("&author;");
    }

    // WHEN the book points at an external DTD by URL, THEN the standard entities resolve from the bundled list and
    // nothing is fetched from that URL.
    @Test
    void read_externalDoctype_resolvesFromBundledListWithoutNetwork() {
        final WireMockServer server =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        try {
            final String doctype =
                    "<!DOCTYPE FictionBook SYSTEM \"http://127.0.0.1:" + server.port() + "/FictionBook2.dtd\">";

            final String out = roundTrip(book(doctype, "Київ&nbsp;1991&mdash;2000"));

            assertThat(server.getAllServeEvents())
                    .as("no request reached the DTD URL")
                    .isEmpty();
            assertThat(out).contains("Київ" + NBSP + "1991" + EM_DASH + "2000");
        } finally {
            server.stop();
        }
    }

    // IF the book declares an entity whose value lives in an external file, THEN that file is never read: the
    // reference is refused or left empty, and its content appears nowhere in the written book.
    @Test
    void read_externalGeneralEntity_neverReadsTheFile() throws IOException {
        final Path secret = Files.writeString(tempDir.resolve("secret.txt"), "TOP-SECRET-CONTENT");
        final String doctype = "<!DOCTYPE FictionBook [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>";
        final String[] written = new String[1];

        final Throwable refused = catchThrowable(() -> written[0] = roundTrip(book(doctype, "X&xxe;Y")));

        final String out = refused == null ? written[0] : "";
        assertThat(out).doesNotContain("TOP-SECRET-CONTENT");
    }
}
