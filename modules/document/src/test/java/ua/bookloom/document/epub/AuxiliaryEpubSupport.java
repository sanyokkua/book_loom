package ua.bookloom.document.epub;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;

/**
 * Builds small EPUBs whose navigation, NCX and images the auxiliary tests read and write, and reads back what a
 * write produced. One instance shares one registry between its reader and its writer, as the document port does.
 */
final class AuxiliaryEpubSupport {

    static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private final Path directory;
    private final OpenEpubRegistry registry = new OpenEpubRegistry();

    AuxiliaryEpubSupport(Path directory) {
        this.directory = directory;
    }

    /** A package listing {@code manifestItems} and spining {@code spineIds}; its spine names the NCX item {@code ncx}. */
    static String opf(String manifestItems, String... spineIds) {
        final String spine = IntStream.range(0, spineIds.length)
                .mapToObj(i -> "<itemref idref=\"" + spineIds[i] + "\"/>")
                .collect(Collectors.joining());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Book</dc:title>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>%s</manifest>
                  <spine toc="ncx">%s</spine>
                </package>
                """.formatted(manifestItems, spine);
    }

    static String chapterItem(String id) {
        return "<item id=\"%s\" href=\"%s.xhtml\" media-type=\"application/xhtml+xml\"/>".formatted(id, id);
    }

    static final String NAV_ITEM =
            "<item id=\"nav\" href=\"toc01.html\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>";

    static final String NCX_ITEM = "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>";

    static String chapter(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter</title></head>
                <body>%s</body>
                </html>
                """.formatted(body);
    }

    /** A navigation document whose table of contents lists {@code labels}, linking entry i to {@code c01.xhtml#si}. */
    static String navDocument(String rootAttributes, List<String> labels) {
        final String items = IntStream.range(0, labels.size())
                .mapToObj(i -> "<li><a href=\"c01.xhtml#s%d\">%s</a></li>".formatted(i, labels.get(i)))
                .collect(Collectors.joining());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"%s>
                <head><title>Contents</title></head>
                <body><nav epub:type="toc"><ol>%s</ol></nav></body>
                </html>
                """.formatted(rootAttributes, items);
    }

    /** An NCX with one {@code navPoint id="npI"} per label, its {@code playOrder} I+1 and link {@code c01.xhtml#sI}. */
    static String ncx(String declaration, List<String> labels) {
        final String points = IntStream.range(0, labels.size())
                .mapToObj(i -> ("<navPoint id=\"np%d\" playOrder=\"%d\"><navLabel><text>%s</text></navLabel>"
                                + "<content src=\"c01.xhtml#s%d\"/></navPoint>")
                        .formatted(i, i + 1, labels.get(i), i))
                .collect(Collectors.joining("\n"));
        return declaration + "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">\n"
                + "<head/><docTitle><text>Book</text></docTitle>\n<navMap>\n" + points + "\n</navMap>\n</ncx>\n";
    }

    Path epub(String name, Map<String, String> entriesByName) {
        final EpubZipBuilder builder = new EpubZipBuilder().mimetype().entry("META-INF/container.xml", CONTAINER_XML);
        entriesByName.forEach(builder::entry);
        return builder.writeTo(directory.resolve(name));
    }

    Document open(Path epub) {
        return new EpubReader(registry).read(epub);
    }

    /** Writes {@code document} with {@code targetsBySegmentId} set as the effective targets of those segments. */
    Path write(Document document, Map<String, String> targetsBySegmentId, String sourceLanguage) {
        final Document edited = new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                document.metadata(),
                document.units().stream()
                        .map(unit -> retargeted(unit, targetsBySegmentId))
                        .toList());
        return new EpubWriter(registry).write(edited, directory.resolve("out.epub"), sourceLanguage, "uk");
    }

    private static Unit retargeted(Unit unit, Map<String, String> targetsBySegmentId) {
        return unit.withSegments(unit.segments().stream()
                .map(segment -> targetsBySegmentId.containsKey(segment.id())
                        ? withTarget(segment, targetsBySegmentId.get(segment.id()))
                        : segment)
                .toList());
    }

    private static Segment withTarget(Segment segment, String target) {
        return segment.withDecision(SegmentStatus.ACCEPTED, target);
    }

    static String entryText(Path zip, String entryName) {
        return new String(entryBytes(zip, entryName), StandardCharsets.UTF_8);
    }

    static byte[] entryBytes(Path zip, String entryName) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            final ZipEntry entry = file.getEntry(entryName);
            return file.getInputStream(entry).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Unit auxiliaryOf(Document document) {
        return document.units().get(document.units().size() - 1);
    }
}
