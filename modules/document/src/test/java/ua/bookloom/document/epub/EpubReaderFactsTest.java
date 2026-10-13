package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/** The year and the publisher an EPUB package declares reach the metadata map, and nothing else about the book moves. */
class EpubReaderFactsTest {

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    private static final String CHAPTER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>One</title></head>
            <body><h1>One</h1><p>The lamp burned low.</p></body>
            </html>
            """;

    @TempDir
    private Path tempDir;

    private Document read(final String dublinCore) {
        return read("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" + dublinCore + "</metadata>", "book");
    }

    private Document read(final String metadataElement, final String name) {
        final Path epub = tempDir.resolve(name + ".epub");
        new EpubZipBuilder()
                .mimetype()
                .entry("META-INF/container.xml", CONTAINER_XML)
                .entry("OEBPS/content.opf", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                          %s
                          <manifest><item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/></manifest>
                          <spine><itemref idref="c01"/></spine>
                        </package>
                        """.formatted(metadataElement))
                .entry("OEBPS/c01.xhtml", CHAPTER)
                .writeTo(epub);
        return new EpubReader(new OpenEpubRegistry()).read(epub);
    }

    private static List<String> segmentShape(final Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .map(segment -> segment.id() + "|" + segment.kind() + "|" + segment.sourceInner())
                .toList();
    }

    // IF the package declares a timestamp date and a publisher, THEN the map carries the bare year and the publisher.
    @Test
    void read_dateAndPublisher_carriesTheYearAndThePublisher() {
        final Document document = read("<dc:title>The Lamp</dc:title><dc:language>en</dc:language>"
                + "<dc:date>1923-04-01T00:00:00+00:00</dc:date><dc:publisher>Larkspur Press</dc:publisher>");

        assertThat(document.metadata())
                .containsEntry(MetadataKey.DATE.key(), "1923")
                .containsEntry(MetadataKey.PUBLISHER.key(), "Larkspur Press")
                .containsEntry(MetadataKey.TITLE.key(), "The Lamp");
    }

    // IF the date is written in any other way, THEN the first run of exactly four digits is the year.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "1895|1895",
                "2019-03-05T10:00:00Z|2019",
                "March 12, 1895|1895",
                "c. 1850-1852|1850",
                "(c) 2001 by the author|2001",
                "  1999  |1999"
            })
    void read_dateInSeveralWritings_keepsTheFirstFourDigitYear(final String written, final String year) {
        final Document document = read("<dc:date>" + written + "</dc:date>");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), year);
    }

    // IF the date holds no run of exactly four digits, THEN the book has no year (no guess).
    @ParameterizedTest
    @ValueSource(strings = {"someday", "19950", "20190305", "99", ""})
    void read_dateWithNoFourDigitYear_hasNoDateKey(final String written) {
        final Document document = read("<dc:date>" + written + "</dc:date>");

        assertThat(document.metadata()).doesNotContainKey(MetadataKey.DATE.key());
    }

    // IF a package lists a creation date and then a modification date, THEN the first one is the book's year.
    @Test
    void read_twoDates_keepsTheYearOfTheFirst() {
        final Document document = read("<dc:date>1901-05-05</dc:date><dc:date>2015-01-01</dc:date>");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), "1901");
    }

    // IF the package marks which date is the publication (EPUB 2), THEN that one is the year, not an edition date
    // listed before it.
    @Test
    void read_dateMarkedAsPublicationAfterAnEditionDate_keepsThePublicationYear() {
        final Document document = read("""
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                  <dc:date opf:event="modification">2015-01-01</dc:date>
                  <dc:date opf:event="publication">1901-05-05</dc:date>
                </metadata>""", "marked");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), "1901");
    }

    // IF no date is marked as the publication, THEN the first one stays the book's year.
    @Test
    void read_datesMarkedAsOtherEvents_keepsTheYearOfTheFirst() {
        final Document document = read("""
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                  <dc:date opf:event="creation">1899-02-02</dc:date>
                  <dc:date opf:event="modification">2015-01-01</dc:date>
                </metadata>""", "unmarked");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), "1899");
    }

    // IF the package declares neither, THEN the map has neither key.
    @Test
    void read_noDateAndNoPublisher_hasNeitherKey() {
        final Document document = read("<dc:title>The Lamp</dc:title>");

        assertThat(document.metadata()).doesNotContainKeys(MetadataKey.DATE.key(), MetadataKey.PUBLISHER.key());
    }

    // IF the publisher element is only white space, THEN the book has no publisher.
    @Test
    void read_blankPublisher_hasNoPublisherKey() {
        final Document document = read("<dc:publisher>   </dc:publisher>");

        assertThat(document.metadata()).doesNotContainKey(MetadataKey.PUBLISHER.key());
    }

    // IF the package nests its Dublin Core elements in the legacy wrapper, THEN date and publisher are read there too.
    @Test
    void read_dublinCoreInLegacyDcMetadataWrapper_carriesTheYearAndThePublisher() {
        final Document document = read("""
                <metadata>
                  <dc-metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>The Lamp</dc:title>
                    <dc:date>1888</dc:date>
                    <dc:publisher>Larkspur Press</dc:publisher>
                  </dc-metadata>
                </metadata>
                """, "legacy");

        assertThat(document.metadata())
                .containsEntry(MetadataKey.DATE.key(), "1888")
                .containsEntry(MetadataKey.PUBLISHER.key(), "Larkspur Press");
    }

    // IF a book declares a date and a publisher, THEN it has exactly the units and segments of the same book without
    // them: nothing new is translated, and the golden round trip has nothing new to carry.
    @Test
    void read_dateAndPublisherAdded_addNoUnitAndNoSegment() {
        final Document plain = read("<dc:title>The Lamp</dc:title><dc:creator>Ada Quill</dc:creator>");
        final Document withFacts = read("<dc:title>The Lamp</dc:title><dc:creator>Ada Quill</dc:creator>"
                + "<dc:date>1923</dc:date><dc:publisher>Larkspur Press</dc:publisher>");

        assertThat(withFacts.units())
                .extracting(Unit::id)
                .containsExactlyElementsOf(plain.units().stream().map(Unit::id).toList());
        assertThat(segmentShape(withFacts)).isNotEmpty().containsExactlyElementsOf(segmentShape(plain));
        assertThat(withFacts.units().stream()
                        .flatMap(unit -> unit.segments().stream())
                        .map(Segment::sourceInner))
                .doesNotContain("1923", "Larkspur Press");
    }
}
