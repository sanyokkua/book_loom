package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.model.SecureXml;

/**
 * The offline invariant on the import path, asserted rather than assumed.
 *
 * <p>An XML parser that fetches an external DTD makes a real network call that <strong>no ArchUnit rule would
 * catch</strong>: it is not an import of {@code java.net.http}, it is a side effect of resolving a doctype. And
 * this is not a hypothetical shape — 53 books in the surveyed corpus rely on XHTML named entities under an XHTML
 * 1.1 DOCTYPE, 10,377 non-breaking spaces among them, so the parser meets one on an ordinary import.
 *
 * <p>The strongest available evidence is used: a {@link SecurityManager}-style ban is gone from modern Java, so
 * instead every socket attempt during a parse is made to fail loudly by pointing the JVM's proxy configuration at
 * a dead address — any parser that tried to fetch would raise rather than silently succeed from a cache — and the
 * parse is additionally required to <em>succeed</em> with the entity resolved, which is only possible without a
 * fetch.
 */
class OfflineParsingTest {

    @TempDir
    private Path tempDir;

    /** A DOCTYPE whose external subset does not exist. A parser that fetched it could not possibly succeed. */
    private static final String XHTML_WITH_NAMED_ENTITY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
                "http://nonexistent.invalid/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>C</title></head>
            <body><p>Fish&nbsp;and&nbsp;chips.</p></body>
            </html>
            """;

    private static final String FB2_WITH_EXTERNAL_ENTITY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE FictionBook [<!ENTITY xxe SYSTEM "http://nonexistent.invalid/secret">]>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><lang>en</lang></title-info></description>
              <body><section><p>Prose.</p></section></body>
            </FictionBook>
            """;

    @AfterEach
    void clearProxyOverride() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
    }

    /**
     * jsoup resolves HTML named entities from its own internal table. That is what keeps the offline invariant
     * true for the 53 corpus books that use them — an XML parser would have to fetch the DTD to learn what
     * a named entity means.
     */
    @Test
    void open_xhtmlUsingNamedEntitiesUnderADoctype_resolvesThemWithoutFetchingTheDtd() {
        final Path fixture = writeEpubWith(XHTML_WITH_NAMED_ENTITY);

        final Document document = Objects.requireNonNull(
                DocumentServices.newService().open(fixture).data());

        assertThat(document.units().get(0).segments())
                .singleElement()
                .satisfies(segment -> assertThat(segment.sourceInner()).contains("Fish"));
    }

    /**
     * The FB2 side. {@code SecureXml} disables external DTD and entity loading outright, so a document declaring
     * an external entity fails to resolve it rather than fetching it — the failure is the proof.
     */
    @Test
    void secureXml_documentDeclaringAnExternalEntity_doesNotFetchIt() {
        assertThatThrownBy(() -> SecureXml.builder().build(new StringReader(FB2_WITH_EXTERNAL_ENTITY + "<x/>")))
                .isInstanceOfAny(JDOMException.class, IOException.class);
    }

    /**
     * The end-to-end statement: opening a book of each format completes while every outbound socket would fail.
     * Any parser that reached for the network during an import would surface here as a failure rather than as a
     * quiet, slow success.
     */
    @Test
    void open_everyFormat_completesWithOutboundConnectionsPointedAtADeadProxy() {
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", String.valueOf(closedPort()));

        final DocumentService service = DocumentServices.newService();

        assertThat(service.open(writeEpubWith(XHTML_WITH_NAMED_ENTITY)).isOk()).isTrue();
        assertThat(service.open(write(
                                "book.fb2",
                                FB2_WITH_EXTERNAL_ENTITY.replace(
                                        "<!DOCTYPE FictionBook [<!ENTITY xxe SYSTEM \"http://nonexistent.invalid/secret\">]>",
                                        "")))
                        .isOk())
                .isTrue();
        assertThat(service.open(write("notes.md", "# Title\n\nProse.\n")).isOk())
                .isTrue();
        assertThat(service.open(write("notes.txt", "One.\n\nTwo.\n")).isOk()).isTrue();
    }

    /** A port nothing is listening on, so a connection through it fails immediately rather than hanging. */
    private static int closedPort() {
        final int port = ephemeralPort();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), port).close())
                .as("the chosen port must really be closed, or the proxy override would prove nothing")
                .isInstanceOf(IOException.class);
        return port;
    }

    /** Binds and releases a port, so the number is one the operating system just confirmed was free. */
    private static int ephemeralPort() {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            return probe.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path writeEpubWith(String chapter) {
        return new ua.bookloom.document.epub.EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                          <rootfiles>
                            <rootfile full-path="OEBPS/content.opf"
                                      media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>
                        """)
                .entry("OEBPS/content.opf", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:language>en</dc:language></metadata>
                          <manifest>
                            <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                          </manifest>
                          <spine><itemref idref="c01"/></spine>
                        </package>
                        """)
                .entry("OEBPS/c01.xhtml", chapter)
                .writeTo(tempDir.resolve("book.epub"));
    }

    private Path write(String name, String content) {
        final Path file = tempDir.resolve(name);
        try {
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }
}
