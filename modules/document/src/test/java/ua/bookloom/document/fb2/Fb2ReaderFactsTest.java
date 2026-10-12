package ua.bookloom.document.fb2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.document.fixture.Fb2Fixtures;

/** The year and the publisher an FB2 description declares reach the metadata map, and nothing else about the book moves. */
class Fb2ReaderFactsTest {

    @TempDir
    private Path tempDir;

    /** A book whose description holds {@code titleInfoExtra} inside title-info and {@code afterTitleInfo} beside it. */
    private Document read(final String titleInfoExtra, final String afterTitleInfo) {
        final String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description>
                    <title-info>
                      <author><first-name>Ada</first-name><last-name>Quill</last-name></author>
                      <book-title>The Lamp</book-title>
                      %s
                      <lang>en</lang>
                    </title-info>
                    %s
                  </description>
                  <body><section><p>The lamp burned low.</p></section></body>
                </FictionBook>
                """.formatted(titleInfoExtra, afterTitleInfo);
        final Path file = Fb2Fixtures.writeFb2(tempDir.resolve("book.fb2"), xml, StandardCharsets.UTF_8);
        return new Fb2Reader(new OpenFb2Registry()).read(file);
    }

    private static List<String> segmentShape(final Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .map(segment -> segment.id() + "|" + segment.kind() + "|" + segment.sourceInner())
                .toList();
    }

    // IF title-info has a date and publish-info a publisher, THEN the map carries the year and the publisher.
    @Test
    void read_titleInfoDateAndPublishInfoPublisher_carriesTheYearAndThePublisher() {
        final Document document = read(
                "<date>1923</date>",
                "<publish-info><publisher>Larkspur Press</publisher><year>1950</year></publish-info>");

        assertThat(document.metadata())
                .containsEntry(MetadataKey.DATE.key(), "1923")
                .containsEntry(MetadataKey.PUBLISHER.key(), "Larkspur Press");
    }

    // IF the date carries a machine value and a human wording, THEN the year comes from the machine value.
    @Test
    void read_dateWithValueAttribute_takesTheYearFromTheValue() {
        final Document document = read("<date value=\"1901-05-05\">the spring of the year</date>", "");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), "1901");
    }

    // IF the date has no value attribute, THEN its text is read like any written date.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"May 1895|1895", "1850-1852|1850", "2001|2001"})
    void read_dateText_keepsTheFirstFourDigitYear(final String written, final String year) {
        final Document document = read("<date>" + written + "</date>", "");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), year);
    }

    // IF title-info has no date but publish-info has a year, THEN the publication year stands for the book's year.
    @Test
    void read_noTitleInfoDateButPublishInfoYear_usesThePublishInfoYear() {
        final Document document = read("", "<publish-info><year>1950</year></publish-info>");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), "1950");
    }

    // IF both name a year, THEN title-info/date wins over publish-info/year.
    @Test
    void read_bothTitleInfoDateAndPublishInfoYear_titleInfoDateWins() {
        final Document document = read("<date>1923</date>", "<publish-info><year>1950</year></publish-info>");

        assertThat(document.metadata()).containsEntry(MetadataKey.DATE.key(), "1923");
    }

    // IF a date holds no four-digit year, THEN the book has no year from it.
    @Test
    void read_dateWithNoYear_hasNoDateKey() {
        final Document document = read("<date>long ago</date>", "");

        assertThat(document.metadata()).doesNotContainKey(MetadataKey.DATE.key());
    }

    // IF there is no publish-info and no date, THEN the map has neither key.
    @Test
    void read_noDateAndNoPublishInfo_hasNeitherKey() {
        final Document document = read("", "");

        assertThat(document.metadata()).doesNotContainKeys(MetadataKey.DATE.key(), MetadataKey.PUBLISHER.key());
    }

    // IF publish-info has an empty publisher, THEN the book has no publisher.
    @Test
    void read_emptyPublisher_hasNoPublisherKey() {
        final Document document = read("", "<publish-info><publisher> </publisher></publish-info>");

        assertThat(document.metadata()).doesNotContainKey(MetadataKey.PUBLISHER.key());
    }

    // IF a book declares a date and a publisher, THEN it has exactly the segments of the same book without them.
    @Test
    void read_dateAndPublishInfoAdded_addNoSegment() {
        final Document plain = read("", "");
        final Document withFacts = read(
                "<date>1923</date>",
                "<publish-info><publisher>Larkspur Press</publisher><year>1950</year></publish-info>");

        assertThat(segmentShape(withFacts)).isNotEmpty().containsExactlyElementsOf(segmentShape(plain));
    }
}
