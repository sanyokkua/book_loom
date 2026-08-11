package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonAnchor;
import ua.bookloom.api.document.Unit;

/**
 * Parse determinism, which is load-bearing precisely because it is invisible: anchors are never persisted —
 * {@code 06_DATA_MODEL_SQLITE.md#tables} has no anchor column — so resume recomputes them by parsing the file
 * again. A parse that numbered its units or blocks differently the second time would write every translation into
 * the wrong place after a restart, and would do so silently.
 */
class ParseDeterminismTest {

    @TempDir
    private Path tempDir;

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final String OPF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:language>en</dc:language></metadata>
              <manifest>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                <item id="c02" href="c02.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c01"/><itemref idref="c02"/></spine>
            </package>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>C</title></head>
            <body>
            <h1>Heading</h1>
            <div class="wrap"><div class="paragraph">Prose one.<br/>Prose two.</div></div>
            <p>Ordinary.</p>
            </body></html>
            """;

    // Covers: FR-IMPORT-08 — WHEN the same source bytes are parsed twice, THEN both parses produce the same unit
    // ids in the same order and every segment's id, order and anchor is identical between them.
    @Test
    void read_sameBytesParsedTwice_yieldIdenticalIdsOrderAndAnchors() {
        final Path epub = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", OPF)
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .entry("OEBPS/c02.xhtml", CHAPTER)
                .writeTo(epub);

        final Document first = new EpubReader(new OpenEpubRegistry()).read(epub);
        final Document second = new EpubReader(new OpenEpubRegistry()).read(epub);

        assertThat(unitIds(second)).isEqualTo(unitIds(first));
        assertThat(segmentIdentities(second)).isEqualTo(segmentIdentities(first));
        assertThat(segmentIdentities(first)).isNotEmpty();
    }

    private static List<String> unitIds(Document document) {
        final List<String> ids = new ArrayList<>();
        for (final Unit unit : document.units()) {
            ids.add(unit.id());
        }
        return ids;
    }

    /** Each segment reduced to exactly what resume depends on: its id, its order and its anchor. */
    private static List<String> segmentIdentities(Document document) {
        final List<String> identities = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                identities.add(identityOf(segment));
            }
        }
        return identities;
    }

    private static String identityOf(Segment segment) {
        final SkeletonAnchor anchor = segment.anchor();
        return segment.id() + "|" + segment.order() + "|" + anchor;
    }
}
